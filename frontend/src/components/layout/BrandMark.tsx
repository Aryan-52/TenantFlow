/** The TenantFlow mark (same artwork as public/favicon.svg). */
export const BrandMark = ({ className = '', size = 28 }: { className?: string; size?: number }) => (
  <svg viewBox="0 0 32 32" width={size} height={size} className={className} aria-hidden="true" focusable="false">
    <rect width="32" height="32" rx="8" fill="#3352e1" />
    <path d="M9.5 10.5h13M16 10.5v11" stroke="#fff" strokeWidth="3" strokeLinecap="round" />
    <circle cx="22.5" cy="21.5" r="2.5" fill="#6ee7b7" />
  </svg>
);
