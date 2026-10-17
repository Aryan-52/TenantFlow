import { useId, useMemo, useState, type ReactNode } from 'react';
import { Link } from 'react-router-dom';

/**
 * Small, dependency-free chart primitives drawn with the --chart-* tokens (validated
 * categorical slots, light and dark). Every chart carries visible values or a legend plus
 * a "View data" table, so nothing depends on colour alone.
 */

export interface Series {
  key: string;
  label: string;
  /** CSS colour, normally var(--chart-1..3). */
  color: string;
}

const fmt = (n: number) => n.toLocaleString();

/** Legend: swatch + text in text tokens (never the series colour as text). */
export const Legend = ({ series }: { series: Series[] }) => (
  <ul className="chart-legend" aria-hidden="true">
    {series.map((s) => (
      <li key={s.key}>
        <span className="chart-swatch" style={{ background: s.color }} />
        {s.label}
      </li>
    ))}
  </ul>
);

/** The accessible alternative for any chart. */
export const DataTable = ({ caption, columns, rows }: { caption: string; columns: string[]; rows: (string | number)[][] }) => (
  <details className="chart-data">
    <summary>View data</summary>
    <table className="chart-table">
      <caption className="sr-only">{caption}</caption>
      <thead>
        <tr>{columns.map((c) => <th key={c} scope="col">{c}</th>)}</tr>
      </thead>
      <tbody>
        {rows.map((r, i) => (
          <tr key={i}>{r.map((v, j) => (j === 0 ? <th key={j} scope="row">{v}</th> : <td key={j}>{typeof v === 'number' ? fmt(v) : v}</td>))}</tr>
        ))}
      </tbody>
    </table>
  </details>
);

export interface BarItem {
  key: string;
  label: string;
  value: number;
  /** Optional destination (e.g. a filtered list). */
  href?: string;
  hint?: string;
}

/**
 * Horizontal bars, one hue, value at the tip. For magnitudes per category (priority,
 * status, buckets). Rows with an href are links.
 */
export const BarList = ({ items, color = 'var(--chart-1)', label }: { items: BarItem[]; color?: string; label: string }) => {
  const max = Math.max(1, ...items.map((i) => i.value));
  return (
    <ul className="bar-list" aria-label={label}>
      {items.map((item) => {
        const body = (
          <>
            <span className="bar-list-label">{item.label}</span>
            <span className="bar-list-track" aria-hidden="true">
              <span className="bar-list-bar" style={{ width: `${(item.value / max) * 100}%`, background: color, minWidth: item.value > 0 ? 4 : 0 }} />
            </span>
            <span className="bar-list-value">{fmt(item.value)}</span>
          </>
        );
        return (
          <li key={item.key} title={item.hint}>
            {item.href ? (
              <Link to={item.href} className="bar-list-row is-link" aria-label={`${item.label}: ${item.value}${item.hint ? `. ${item.hint}` : ''}`}>
                {body}
              </Link>
            ) : (
              <div className="bar-list-row" aria-label={`${item.label}: ${item.value}`}>{body}</div>
            )}
          </li>
        );
      })}
    </ul>
  );
};

export interface StackedRow {
  key: string;
  label: string;
  values: Record<string, number>;
  href?: string;
  /** Extra text after the total, e.g. "2 overdue". */
  note?: ReactNode;
}

/** One horizontal stacked bar per row (e.g. open work per assignee by status). */
export const StackedBars = ({ rows, series, label }: { rows: StackedRow[]; series: Series[]; label: string }) => {
  const totals = rows.map((r) => series.reduce((s, x) => s + (r.values[x.key] ?? 0), 0));
  const max = Math.max(1, ...totals);
  const [hover, setHover] = useState<string | null>(null);
  return (
    <div>
      <Legend series={series} />
      <ul className="stacked-list" aria-label={label}>
        {rows.map((r, i) => {
          const total = totals[i];
          const description = series.map((s) => `${s.label} ${r.values[s.key] ?? 0}`).join(', ');
          const body = (
            <>
              <span className="bar-list-label">{r.label}</span>
              <span className="stacked-track" aria-hidden="true">
                <span className="stacked-bar" style={{ width: `${(total / max) * 100}%` }}>
                  {series.map((s) =>
                    (r.values[s.key] ?? 0) > 0 ? (
                      <span key={s.key} className="stacked-seg" style={{ flexGrow: r.values[s.key], background: s.color }} />
                    ) : null,
                  )}
                </span>
              </span>
              <span className="bar-list-value">
                {fmt(total)}
                {r.note && <span className="stacked-note">{r.note}</span>}
              </span>
              {hover === r.key && (
                <span className="chart-tooltip" role="tooltip">
                  <strong>{r.label}</strong>
                  {series.map((s) => (
                    <span key={s.key} className="chart-tooltip-row">
                      <span className="chart-swatch" style={{ background: s.color }} />
                      {s.label}<b>{fmt(r.values[s.key] ?? 0)}</b>
                    </span>
                  ))}
                </span>
              )}
            </>
          );
          const common = {
            onMouseEnter: () => setHover(r.key),
            onMouseLeave: () => setHover(null),
            onFocus: () => setHover(r.key),
            onBlur: () => setHover(null),
          };
          return (
            <li key={r.key}>
              {r.href ? (
                <Link to={r.href} className="bar-list-row stacked-row is-link" aria-label={`${r.label}: ${total} (${description})`} {...common}>
                  {body}
                </Link>
              ) : (
                <div className="bar-list-row stacked-row" tabIndex={0} aria-label={`${r.label}: ${total} (${description})`} {...common}>
                  {body}
                </div>
              )}
            </li>
          );
        })}
      </ul>
    </div>
  );
};

export interface TrendDatum {
  date: string; // ISO date
  [key: string]: number | string;
}

const niceMax = (v: number) => {
  if (v <= 4) return 4;
  const pow = 10 ** Math.floor(Math.log10(v));
  const step = [1, 2, 2.5, 5, 10].map((m) => m * pow).find((s) => v / s <= 4) ?? 10 * pow;
  return Math.ceil(v / step) * step;
};

/**
 * Lines over time with one shared axis, hairline grid, a crosshair + tooltip on hover or
 * keyboard (arrow keys), a legend, and a data table.
 */
export const TrendChart = ({
  data,
  series,
  label,
  formatDate,
  height = 220,
}: {
  data: TrendDatum[];
  series: Series[];
  label: string;
  formatDate: (iso: string) => string;
  height?: number;
}) => {
  const id = useId();
  const [active, setActive] = useState<number | null>(null);
  const width = 640;
  const pad = { top: 12, right: 12, bottom: 26, left: 34 };
  const innerW = width - pad.left - pad.right;
  const innerH = height - pad.top - pad.bottom;
  const max = useMemo(() => niceMax(Math.max(0, ...data.flatMap((d) => series.map((s) => Number(d[s.key]) || 0)))), [data, series]);
  const x = (i: number) => pad.left + (data.length <= 1 ? innerW / 2 : (i / (data.length - 1)) * innerW);
  const y = (v: number) => pad.top + innerH - (v / max) * innerH;
  const ticks = [0, max / 4, max / 2, (3 * max) / 4, max];
  const labelEvery = Math.max(1, Math.ceil(data.length / 7));

  const onMove = (clientX: number, rect: DOMRect) => {
    const px = ((clientX - rect.left) / rect.width) * width;
    const i = Math.round(((px - pad.left) / innerW) * (data.length - 1));
    setActive(Math.max(0, Math.min(data.length - 1, i)));
  };

  const activeDatum = active == null ? null : data[active];

  return (
    <div className="trend">
      <Legend series={series} />
      <div className="trend-plot">
        <svg
          viewBox={`0 0 ${width} ${height}`}
          className="trend-svg"
          role="img"
          aria-labelledby={`${id}-desc`}
          tabIndex={0}
          onMouseMove={(e) => onMove(e.clientX, e.currentTarget.getBoundingClientRect())}
          onMouseLeave={() => setActive(null)}
          onBlur={() => setActive(null)}
          onKeyDown={(e) => {
            if (e.key === 'ArrowRight') setActive((a) => Math.min(data.length - 1, (a ?? -1) + 1));
            else if (e.key === 'ArrowLeft') setActive((a) => Math.max(0, (a ?? data.length) - 1));
            else if (e.key === 'Escape') setActive(null);
          }}
        >
          <desc id={`${id}-desc`}>{label}. Use the arrow keys to read values, or open “View data” below.</desc>
          {ticks.map((t) => (
            <g key={t}>
              <line x1={pad.left} x2={width - pad.right} y1={y(t)} y2={y(t)} className="chart-grid" />
              <text x={pad.left - 8} y={y(t)} className="chart-axis" textAnchor="end" dominantBaseline="middle">
                {Number.isInteger(t) ? fmt(t) : t.toFixed(1)}
              </text>
            </g>
          ))}
          {data.map((d, i) =>
            i % labelEvery === 0 || i === data.length - 1 ? (
              <text key={d.date} x={x(i)} y={height - 6} className="chart-axis" textAnchor="middle">
                {formatDate(d.date)}
              </text>
            ) : null,
          )}
          {series.map((s) => (
            <path
              key={s.key}
              d={data.map((d, i) => `${i === 0 ? 'M' : 'L'}${x(i).toFixed(1)},${y(Number(d[s.key]) || 0).toFixed(1)}`).join(' ')}
              fill="none"
              stroke={s.color}
              strokeWidth={2}
              strokeLinejoin="round"
              strokeLinecap="round"
            />
          ))}
          {activeDatum && active != null && (
            <g>
              <line x1={x(active)} x2={x(active)} y1={pad.top} y2={pad.top + innerH} className="chart-crosshair" />
              {series.map((s) => (
                <circle key={s.key} cx={x(active)} cy={y(Number(activeDatum[s.key]) || 0)} r={4.5} fill={s.color} className="chart-dot" />
              ))}
            </g>
          )}
        </svg>
        {activeDatum && active != null && (
          <div
            className="chart-tooltip trend-tooltip"
            role="status"
            style={{ left: `${(x(active) / width) * 100}%` }}
          >
            <strong>{formatDate(activeDatum.date)}</strong>
            {series.map((s) => (
              <span key={s.key} className="chart-tooltip-row">
                <span className="chart-swatch" style={{ background: s.color }} />
                {s.label}<b>{fmt(Number(activeDatum[s.key]) || 0)}</b>
              </span>
            ))}
          </div>
        )}
      </div>
      <DataTable
        caption={label}
        columns={['Period', ...series.map((s) => s.label)]}
        rows={data.map((d) => [formatDate(d.date), ...series.map((s) => Number(d[s.key]) || 0)])}
      />
    </div>
  );
};
