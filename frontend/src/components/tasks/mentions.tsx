import { Fragment, type ReactNode } from 'react';

export interface Mentionable {
  userId: string;
  name: string;
}

/** Members whose "@Name" appears in the text (what the server will treat as mentions). */
export function mentionedIds(text: string, members: Mentionable[]): string[] {
  const lower = text.toLowerCase();
  return members.filter((m) => lower.includes(`@${m.name.toLowerCase()}`)).map((m) => m.userId);
}

/** Renders a comment with "@Name" of known members emphasised. Plain text only. */
export function renderMentions(text: string, members: Mentionable[]): ReactNode {
  const names = members.map((m) => m.name).filter(Boolean).sort((a, b) => b.length - a.length);
  if (names.length === 0) return text;
  const escaped = names.map((n) => n.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'));
  const parts = text.split(new RegExp(`(@(?:${escaped.join('|')}))`, 'gi'));
  return parts.map((p, i) => (i % 2 === 1 ? <span key={i} className="mention">{p}</span> : <Fragment key={i}>{p}</Fragment>));
}
