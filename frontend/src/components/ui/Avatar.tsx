import { initials, tintStyle } from './visual';

export interface AvatarProps {
  name: string;
  size?: 'xs' | 'sm' | 'md' | 'lg';
  className?: string;
  /** Show the full name on hover (avatars without visible names next to them). */
  withTitle?: boolean;
}

/** Initials on a stable per-person tint. */
export const Avatar = ({ name, size = 'md', className = '', withTitle = false }: AvatarProps) => (
  <span
    className={`avatar avatar-${size} ${className}`.trim()}
    style={tintStyle(name, 'avatar')}
    title={withTitle ? name : undefined}
    aria-hidden={withTitle ? undefined : true}
  >
    {initials(name)}
  </span>
);
