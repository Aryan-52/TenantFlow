package io.github.aryan52.tenantflow.user;

import io.github.aryan52.tenantflow.entity.User;
import io.github.aryan52.tenantflow.error.ResourceNotFoundException;
import io.github.aryan52.tenantflow.repository.UserRepository;
import io.github.aryan52.tenantflow.user.dto.ChangePasswordRequest;
import io.github.aryan52.tenantflow.user.dto.UpdateProfileRequest;
import io.github.aryan52.tenantflow.user.dto.UserProfileResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	@Transactional(readOnly = true)
	public UserProfileResponse getProfile(UUID userId) {
		return UserProfileResponse.from(findUser(userId));
	}

	@Transactional
	public UserProfileResponse updateProfile(UUID userId, UpdateProfileRequest request) {
		User user = findUser(userId);
		user.setName(request.name().trim());
		return UserProfileResponse.from(userRepository.save(user));
	}

	@Transactional
	public void changePassword(UUID userId, ChangePasswordRequest request) {
		User user = findUser(userId);

		if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
			throw new InvalidCurrentPasswordException();
		}

		user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
		userRepository.save(user);
	}

	private User findUser(UUID userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> new ResourceNotFoundException("User"));
	}
}
