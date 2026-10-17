interface ProgressRingProps {
  /** 0-100 */
  percent: number;
  size?: number;
  stroke?: number;
  label: string;
}

/** Circular completion indicator with the percentage in the middle. */
export const ProgressRing = ({ percent, size = 88, stroke = 8, label }: ProgressRingProps) => {
  const value = Math.max(0, Math.min(100, Math.round(percent)));
  const radius = (size - stroke) / 2;
  const circumference = 2 * Math.PI * radius;
  return (
    <div
      className={`progress-ring ${value === 100 ? 'is-complete' : ''}`.trim()}
      role="progressbar"
      aria-valuemin={0}
      aria-valuemax={100}
      aria-valuenow={value}
      aria-label={label}
      style={{ width: size, height: size }}
    >
      <svg width={size} height={size} aria-hidden="true">
        <circle className="progress-ring-track" cx={size / 2} cy={size / 2} r={radius} fill="none" strokeWidth={stroke} />
        <circle
          className="progress-ring-value"
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          strokeWidth={stroke}
          strokeLinecap="round"
          strokeDasharray={circumference}
          strokeDashoffset={circumference * (1 - value / 100)}
        />
      </svg>
      <span className="progress-ring-label" aria-hidden="true">{value}%</span>
    </div>
  );
};

/** Stacked To do / In progress / Done bar with a legend. */
export const StatusBreakdown = ({ todo, inProgress, done }: { todo: number; inProgress: number; done: number }) => {
  const total = todo + inProgress + done;
  return (
    <div>
      <div className="status-bar" role="img" aria-label={`${todo} to do, ${inProgress} in progress, ${done} done`}>
        {total > 0 && (
          <>
            {todo > 0 && <span className="is-todo" style={{ flexGrow: todo }} />}
            {inProgress > 0 && <span className="is-progress" style={{ flexGrow: inProgress }} />}
            {done > 0 && <span className="is-done" style={{ flexGrow: done }} />}
          </>
        )}
      </div>
      <div className="legend mt-3" aria-hidden="true">
        <span className="legend-item"><span className="legend-swatch is-todo" />To do <strong>{todo}</strong></span>
        <span className="legend-item"><span className="legend-swatch is-progress" />In progress <strong>{inProgress}</strong></span>
        <span className="legend-item"><span className="legend-swatch is-done" />Done <strong>{done}</strong></span>
      </div>
    </div>
  );
};
