/**
 * Product illustrations drawn from the design tokens (see .ill-* in pages.css), so they
 * follow the light/dark theme. Decorative only: always aria-hidden.
 */

/** A calm, abstract TenantFlow workspace: a board, a calendar tile and a progress card. */
export const WorkspaceIllustration = ({ className = '' }: { className?: string }) => (
  <svg viewBox="0 0 440 340" className={`ill ${className}`.trim()} aria-hidden="true" focusable="false">
    <defs>
      <filter id="ill-shadow" x="-20%" y="-20%" width="140%" height="150%">
        <feDropShadow dx="0" dy="10" stdDeviation="14" floodOpacity="0.10" />
      </filter>
      <linearGradient id="ill-ring" x1="0" y1="0" x2="1" y2="1">
        <stop offset="0" className="ill-accent-stop-a" />
        <stop offset="1" className="ill-accent-stop-b" />
      </linearGradient>
    </defs>

    {/* Board */}
    <g filter="url(#ill-shadow)">
      <rect x="40" y="58" width="300" height="214" rx="18" className="ill-surface" />
    </g>
    <rect x="58" y="76" width="56" height="8" rx="4" className="ill-line-strong" />
    <circle cx="312" cy="80" r="7" className="ill-avatar-a" />
    <circle cx="298" cy="80" r="7" className="ill-avatar-b" />
    <circle cx="284" cy="80" r="7" className="ill-avatar-c" />

    {/* Three columns */}
    {[58, 152, 246].map((x, col) => (
      <g key={x}>
        <rect x={x} y="100" width="78" height="156" rx="10" className="ill-column" />
        <circle cx={x + 12} cy="114" r="3.5" className={col === 0 ? 'ill-dot-muted' : col === 1 ? 'ill-dot-accent' : 'ill-dot-mint'} />
        <rect x={x + 21} y="111" width="30" height="6" rx="3" className="ill-line" />
        {Array.from({ length: 3 - col }, (_, n) => n).map((n) => (
          <g key={n}>
            <rect x={x + 7} y={126 + n * 40} width="64" height="32" rx="7" className="ill-card" />
            <rect x={x + 14} y={134 + n * 40} width={n === 1 ? 36 : 46} height="5" rx="2.5" className="ill-line-strong" />
            <rect x={x + 14} y={145 + n * 40} width="20" height="4" rx="2" className={col === 2 ? 'ill-pill-mint' : n === 0 ? 'ill-pill-accent' : 'ill-line'} />
          </g>
        ))}
      </g>
    ))}

    {/* Calendar tile */}
    <g filter="url(#ill-shadow)">
      <rect x="300" y="18" width="112" height="104" rx="14" className="ill-surface" />
    </g>
    <rect x="314" y="32" width="40" height="6" rx="3" className="ill-line-strong" />
    {Array.from({ length: 15 }, (_, i) => {
      const cx = 320 + (i % 5) * 18;
      const cy = 56 + Math.floor(i / 5) * 18;
      return i === 7 ? (
        <g key={i}>
          <circle cx={cx} cy={cy} r="7.5" className="ill-accent" />
        </g>
      ) : (
        <circle key={i} cx={cx} cy={cy} r="2.5" className={i === 11 ? 'ill-dot-mint' : 'ill-dot-muted'} />
      );
    })}

    {/* Progress card */}
    <g filter="url(#ill-shadow)">
      <rect x="12" y="222" width="150" height="86" rx="14" className="ill-surface" />
    </g>
    <circle cx="52" cy="265" r="22" className="ill-ring-track" strokeWidth="7" fill="none" />
    <circle
      cx="52"
      cy="265"
      r="22"
      stroke="url(#ill-ring)"
      strokeWidth="7"
      fill="none"
      strokeLinecap="round"
      strokeDasharray="138.2"
      strokeDashoffset="38"
      transform="rotate(-90 52 265)"
    />
    <rect x="86" y="252" width="56" height="6" rx="3" className="ill-line-strong" />
    <rect x="86" y="266" width="38" height="5" rx="2.5" className="ill-line" />
  </svg>
);

/** Empty projects: a stack of project cards. */
export const ProjectsIllustration = () => (
  <svg viewBox="0 0 160 112" width="160" height="112" className="ill" aria-hidden="true" focusable="false">
    <rect x="34" y="12" width="92" height="64" rx="10" className="ill-column" />
    <rect x="24" y="24" width="112" height="72" rx="12" className="ill-surface" />
    <rect x="24" y="24" width="112" height="24" rx="12" className="ill-cover" />
    <rect x="24" y="36" width="112" height="12" className="ill-cover" />
    <rect x="36" y="40" width="18" height="18" rx="5" className="ill-accent" />
    <rect x="36" y="66" width="56" height="6" rx="3" className="ill-line-strong" />
    <rect x="36" y="80" width="88" height="4" rx="2" className="ill-column" />
    <rect x="36" y="80" width="52" height="4" rx="2" className="ill-accent" />
  </svg>
);

/** Empty tasks: a short checklist. */
export const TasksIllustration = () => (
  <svg viewBox="0 0 160 112" width="160" height="112" className="ill" aria-hidden="true" focusable="false">
    <rect x="26" y="14" width="108" height="84" rx="12" className="ill-surface" />
    {[0, 1, 2].map((i) => (
      <g key={i}>
        <rect x="40" y={30 + i * 22} width="12" height="12" rx="4" className={i === 0 ? 'ill-mint' : 'ill-check-empty'} />
        {i === 0 && <path d="M43 36.5l2.2 2.2 4-4.4" className="ill-check-mark" fill="none" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />}
        <rect x="60" y={33 + i * 22} width={i === 1 ? 44 : 60} height="6" rx="3" className={i === 0 ? 'ill-line' : 'ill-line-strong'} />
      </g>
    ))}
  </svg>
);
