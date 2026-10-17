package io.github.aryan52.tenantflow.task;

import io.github.aryan52.tenantflow.activity.ActivityService;
import io.github.aryan52.tenantflow.activity.ActivityService.Notify;
import io.github.aryan52.tenantflow.common.PageResponse;
import io.github.aryan52.tenantflow.common.Pagination;
import io.github.aryan52.tenantflow.entity.Membership;
import io.github.aryan52.tenantflow.entity.Role;
import io.github.aryan52.tenantflow.entity.Task;
import io.github.aryan52.tenantflow.entity.User;
import io.github.aryan52.tenantflow.error.ForbiddenOperationException;
import io.github.aryan52.tenantflow.error.ResourceNotFoundException;
import io.github.aryan52.tenantflow.repository.MembershipRepository;
import io.github.aryan52.tenantflow.repository.ProjectRepository;
import io.github.aryan52.tenantflow.repository.TaskRepository;
import io.github.aryan52.tenantflow.repository.UserRepository;
import io.github.aryan52.tenantflow.task.dto.CommentResponse;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Task discussion. Every operation first proves the task belongs to the project and the
 * project to the tenant (the controller has already proved membership of the tenant).
 * Anyone in the workspace can read and post; only the author edits; the author or a
 * workspace admin deletes.
 */
@Service
@RequiredArgsConstructor
public class TaskCommentService {

	private static final int EXCERPT = 140;

	private final TaskCommentRepository commentRepository;
	private final ProjectRepository projectRepository;
	private final TaskRepository taskRepository;
	private final MembershipRepository membershipRepository;
	private final UserRepository userRepository;
	private final ActivityService activityService;
	private final io.github.aryan52.tenantflow.notification.Notifier notifier;
	private final io.github.aryan52.tenantflow.preferences.PreferenceService preferenceService;

	/** Oldest first: a discussion reads top to bottom. */
	@Transactional(readOnly = true)
	public PageResponse<CommentResponse> list(UUID tenantId, UUID projectId, UUID taskId, Integer page, Integer size) {
		findTask(tenantId, projectId, taskId);
		Page<TaskComment> comments = commentRepository.findByTenantIdAndTaskId(tenantId, taskId,
				Pagination.of(page, size == null ? 50 : size, Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"))));
		Set<UUID> authorIds = comments.getContent().stream().map(TaskComment::getAuthorId).filter(Objects::nonNull)
				.collect(Collectors.toSet());
		Map<UUID, String> names = userRepository.findAllById(authorIds).stream()
				.collect(Collectors.toMap(User::getId, User::getName, (a, b) -> a));
		Function<TaskComment, CommentResponse> mapper = c -> CommentResponse.from(c, names.get(c.getAuthorId()));
		return PageResponse.of(comments, mapper);
	}

	@Transactional
	public CommentResponse create(UUID tenantId, UUID projectId, UUID taskId, UUID authorId, String body) {
		return create(tenantId, projectId, taskId, authorId, body, null);
	}

	@Transactional
	public CommentResponse create(UUID tenantId, UUID projectId, UUID taskId, UUID authorId, String body,
			List<UUID> mentionedUserIds) {
		Task task = findTask(tenantId, projectId, taskId);
		User author = userRepository.findById(authorId).orElseThrow(() -> new ResourceNotFoundException("User"));
		TaskComment comment = commentRepository.saveAndFlush(TaskComment.builder()
				.tenantId(tenantId)
				.taskId(task.getId())
				.authorId(authorId)
				.body(body.strip())
				.build());

		Set<UUID> mentioned = notifyMentions(tenantId, task, author, comment, mentionedUserIds);
		List<Notify> notify = notifyFor(tenantId, task, author, comment).stream()
				.filter(n -> !mentioned.contains(n.recipientId())) // the mention already told them
				.toList();
		activityService.record(tenantId, task.getProject().getId(), task.getId(), "TASK_COMMENTED",
				"commented on \"" + task.getTitle() + "\"", notify);
		return CommentResponse.from(comment, author.getName());
	}

	@Transactional
	public CommentResponse update(UUID tenantId, UUID projectId, UUID taskId, UUID commentId, UUID userId, String body) {
		return update(tenantId, projectId, taskId, commentId, userId, body, null);
	}

	/** Editing can add mentions; people already mentioned in this comment are not notified again. */
	@Transactional
	public CommentResponse update(UUID tenantId, UUID projectId, UUID taskId, UUID commentId, UUID userId, String body,
			List<UUID> mentionedUserIds) {
		Task task = findTask(tenantId, projectId, taskId);
		TaskComment comment = findComment(tenantId, taskId, commentId);
		if (!userId.equals(comment.getAuthorId())) {
			throw new ForbiddenOperationException("Only the author can edit this comment");
		}
		comment.setBody(body.strip());
		TaskComment saved = commentRepository.saveAndFlush(comment);
		User author = userRepository.findById(userId).orElse(null);
		if (author != null) {
			notifyMentions(tenantId, task, author, saved, mentionedUserIds);
		}
		return CommentResponse.from(saved, author == null ? null : author.getName());
	}

	@Transactional
	public void delete(UUID tenantId, UUID projectId, UUID taskId, UUID commentId, UUID userId) {
		findTask(tenantId, projectId, taskId);
		TaskComment comment = findComment(tenantId, taskId, commentId);
		Role role = membershipRepository.findByUser_IdAndTenant_Id(userId, tenantId).map(Membership::getRole)
				.orElseThrow(() -> new ForbiddenOperationException("Not a member of this workspace"));
		if (!userId.equals(comment.getAuthorId()) && !role.atLeast(Role.ADMIN)) {
			throw new ForbiddenOperationException("Only the author or a workspace admin can delete this comment");
		}
		commentRepository.delete(comment);
	}

	/**
	 * Notifies mentioned members. A mention counts only for a current member of this workspace
	 * whose "@Name" actually appears in the text (no hidden pings), never the author, and once
	 * per comment and person (dedupe key), so retries and edits do not repeat it.
	 * @return the people who were (or had already been) notified
	 */
	private Set<UUID> notifyMentions(UUID tenantId, Task task, User author, TaskComment comment, List<UUID> ids) {
		Set<UUID> result = new LinkedHashSet<>();
		if (ids == null || ids.isEmpty()) {
			return result;
		}
		String text = comment.getBody().toLowerCase(java.util.Locale.ROOT);
		String excerpt = comment.getBody().length() <= EXCERPT ? comment.getBody() : comment.getBody().substring(0, EXCERPT - 1) + "…";
		for (UUID id : new LinkedHashSet<>(ids)) {
			if (id == null || id.equals(author.getId()) || !membershipRepository.existsByUser_IdAndTenant_Id(id, tenantId)) {
				continue;
			}
			User user = userRepository.findById(id).orElse(null);
			if (user == null || !text.contains("@" + user.getName().toLowerCase(java.util.Locale.ROOT))) {
				continue;
			}
			if (!preferenceService.load(id).isMentionsEnabled()) {
				continue;
			}
			notifier.notifyOnce(id, tenantId, "MENTION", author.getName() + " mentioned you on " + task.getTitle(), excerpt,
					TaskActivity.link(tenantId, task), "mention:" + comment.getId() + ":" + id);
			result.add(id);
		}
		return result;
	}

	/** The assignee and the creator hear about new comments (never the author; ActivityService skips that). */
	private List<Notify> notifyFor(UUID tenantId, Task task, User author, TaskComment comment) {
		Set<UUID> recipients = new LinkedHashSet<>();
		if (task.getAssignee() != null) {
			recipients.add(task.getAssignee().getId());
		}
		if (task.getCreatedBy() != null) {
			recipients.add(task.getCreatedBy().getId());
		}
		String excerpt = comment.getBody().length() <= EXCERPT ? comment.getBody() : comment.getBody().substring(0, EXCERPT - 1) + "…";
		List<Notify> notify = new ArrayList<>();
		for (UUID recipient : recipients) {
			// Only people still in the workspace can open the task.
			if (membershipRepository.findByUser_IdAndTenant_Id(recipient, tenantId).isEmpty()) {
				continue;
			}
			notify.add(Notify.of(recipient, "TASK_COMMENTED", author.getName() + " commented on " + task.getTitle(),
					excerpt, TaskActivity.link(tenantId, task), "comment:" + comment.getId()));
		}
		return notify;
	}

	private Task findTask(UUID tenantId, UUID projectId, UUID taskId) {
		projectRepository.findByIdAndTenant_Id(projectId, tenantId).orElseThrow(() -> new ResourceNotFoundException("Project"));
		return taskRepository.findByIdAndProject_Id(taskId, projectId).orElseThrow(() -> new ResourceNotFoundException("Task"));
	}

	private TaskComment findComment(UUID tenantId, UUID taskId, UUID commentId) {
		return commentRepository.findByIdAndTenantIdAndTaskId(commentId, tenantId, taskId)
				.orElseThrow(() -> new ResourceNotFoundException("Comment"));
	}
}
