import { Link } from 'react-router-dom';
import { MoreHorizontal, Pencil, Trash2 } from 'lucide-react';
import type { Project } from '../../api/work';
import { timeAgo } from '../../api/activity';
import { ProjectStatusBadge } from '../ui/Badge';
import { Avatar } from '../ui/Avatar';
import { Dropdown, DropdownItem, DropdownDivider } from '../ui/Dropdown';
import { initials, tintStyle } from '../ui/visual';

interface ProjectCardProps {
  project: Project;
  href: string;
  canManage: boolean;
  onEdit: (project: Project) => void;
  onDelete: (project: Project) => void;
}

/** Cover motif: a few quiet lines in the project's tint (same pattern for every project). */
const CoverPattern = () => (
  <svg className="project-cover-pattern" viewBox="0 0 240 72" preserveAspectRatio="none" aria-hidden="true" focusable="false">
    <path d="M150 72 C 170 40, 200 30, 240 26" />
    <path d="M128 72 C 152 34, 196 18, 240 12" />
    <path d="M106 72 C 136 28, 190 6, 240 -2" />
  </svg>
);

/**
 * A project at a glance. The project name is the primary link and covers the whole card
 * (stretched link); the actions menu sits above it, so the two never conflict.
 */
export const ProjectCard = ({ project: p, href, canManage, onEdit, onDelete }: ProjectCardProps) => {
  const percent = p.taskCount ? Math.round((p.completedTaskCount / p.taskCount) * 100) : 0;
  return (
    <article className="project-card">
      <div className="project-cover" style={tintStyle(p.id, 'cover')}>
        <CoverPattern />
        <span className="project-mark project-mark-lg" style={tintStyle(p.id)} aria-hidden="true">
          {initials(p.name)}
        </span>
        {canManage && (
          <div className="project-card-menu above-stretch">
            <Dropdown
              align="right"
              trigger={({ open, toggle }) => (
                <button
                  type="button"
                  className="icon-button project-menu-trigger"
                  onClick={toggle}
                  aria-haspopup="menu"
                  aria-expanded={open}
                  aria-label={`Actions for ${p.name}`}
                >
                  <MoreHorizontal size={16} />
                </button>
              )}
            >
              <DropdownItem icon={<Pencil size={15} />} onClick={() => onEdit(p)}>Edit project</DropdownItem>
              <DropdownDivider />
              <DropdownItem icon={<Trash2 size={15} />} danger onClick={() => onDelete(p)}>Delete project</DropdownItem>
            </Dropdown>
          </div>
        )}
      </div>

      <div className="project-card-body">
        <div className="flex items-start justify-between gap-2">
          <h3 className="project-card-title">
            <Link to={href} className="stretched-link">{p.name}</Link>
          </h3>
          <ProjectStatusBadge status={p.status} />
        </div>
        <p className={`project-card-desc line-clamp-2 ${p.description ? '' : 'is-empty'}`.trim()}>
          {p.description || 'No description yet.'}
        </p>

        <div className="project-card-progress">
          <div className="flex justify-between items-center text-xs mb-2">
            <span className="text-muted">{p.taskCount === 0 ? 'No tasks yet' : `${p.completedTaskCount} of ${p.taskCount} tasks done`}</span>
            <span className="font-semibold tabular">{percent}%</span>
          </div>
          <div className={`progress ${percent === 100 ? 'is-complete' : ''}`.trim()} role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={percent} aria-label={`${p.name}: ${percent}% complete`}>
            <div className="progress-bar" style={{ width: `${percent}%` }} />
          </div>
        </div>

        <div className="project-card-footer">
          <span className="flex items-center gap-2" style={{ minWidth: 0 }}>
            {p.ownerName ? (
              <>
                <Avatar name={p.ownerName} size="xs" />
                <span className="truncate">{p.ownerName}</span>
              </>
            ) : (
              <span className="text-faint">No owner</span>
            )}
          </span>
          <span className="text-faint flex-shrink-0" title={new Date(p.updatedAt).toLocaleString()}>Updated {timeAgo(p.updatedAt)}</span>
        </div>
      </div>
    </article>
  );
};
