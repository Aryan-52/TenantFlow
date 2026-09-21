import React from 'react';

export const Skeleton = ({ className = '', style = {} }: { className?: string, style?: React.CSSProperties }) => {
  return (
    <div className={`skeleton ${className}`} style={{ minHeight: '1.5rem', ...style }} />
  );
};

export const TableSkeleton = ({ rows = 3, cols = 4 }: { rows?: number, cols?: number }) => (
  <div className="table-container">
    <table className="table">
      <thead>
        <tr>
          {Array.from({ length: cols }).map((_, i) => (
            <th key={i}><Skeleton style={{ height: '1rem', width: '60%' }} /></th>
          ))}
        </tr>
      </thead>
      <tbody>
        {Array.from({ length: rows }).map((_, i) => (
          <tr key={i}>
            {Array.from({ length: cols }).map((_, j) => (
              <td key={j}><Skeleton style={{ height: '1rem', width: '80%' }} /></td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  </div>
);
