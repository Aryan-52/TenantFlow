import type { ReactNode } from 'react';

export const Table = ({ children }: { children: ReactNode }) => {
  return (
    <div className="table-container">
      <table className="table">
        {children}
      </table>
    </div>
  );
};
