import type { CSSProperties } from 'react';

/**
 * A consistent, restrained visual identity for workspaces, projects and people: one of six
 * tints chosen by a stable hash of the id, so the same project always looks the same and no
 * random imagery is involved. Colours come from the --tint-* tokens (light and dark aware).
 */
const TINTS = ['iris', 'sky', 'mint', 'amber', 'rose', 'slate'] as const;
export type Tint = (typeof TINTS)[number];

function hash(value: string): number {
  let h = 0;
  for (let i = 0; i < value.length; i++) h = (Math.imul(31, h) + value.charCodeAt(i)) | 0;
  return Math.abs(h);
}

export const tintFor = (key: string): Tint => TINTS[hash(key) % TINTS.length];

/** CSS custom properties consumed by .workspace-mark, .project-cover and .avatar. */
export const tintStyle = (key: string, prefix: 'mark' | 'cover' | 'avatar' = 'mark'): CSSProperties => {
  const tint = tintFor(key);
  return {
    [`--${prefix}-bg`]: `var(--tint-${tint}-bg)`,
    [`--${prefix}-fg`]: `var(--tint-${tint}-fg)`,
  } as CSSProperties;
};

/** Up to two initials: "Apollo launch" -> "AL", "acme" -> "AC". */
export function initials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return '?';
  if (parts.length === 1) return parts[0]!.slice(0, 2).toUpperCase();
  return (parts[0]![0] + parts[parts.length - 1]![0]).toUpperCase();
}
