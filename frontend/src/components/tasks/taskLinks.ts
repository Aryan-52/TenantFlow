import type { KeyboardEvent } from 'react';
import type { Task } from '../../api/work';

export const taskHref = (tenantId: string, t: Pick<Task, 'id' | 'projectId'>) =>
  `/tenants/${tenantId}/projects/${t.projectId}/tasks?task=${t.id}`;

/** Moves focus to the previous/next row (arrow keys), like a list box. */
export function rowKeyNav(e: KeyboardEvent<HTMLElement>) {
  if (e.key !== 'ArrowDown' && e.key !== 'ArrowUp') return;
  const rows = Array.from(document.querySelectorAll<HTMLElement>('[data-task-row-link]'));
  const i = rows.indexOf(e.currentTarget);
  const next = rows[e.key === 'ArrowDown' ? i + 1 : i - 1];
  if (next) {
    e.preventDefault();
    next.focus();
  }
}
