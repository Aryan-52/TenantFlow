import { AlertCircle } from 'lucide-react';

export const ErrorState = ({ message }: { message: string }) => {
  if (!message) return null;
  return (
    <div className="alert alert-danger mb-4" role="alert">
      <AlertCircle size={18} className="alert-icon" />
      <span>{message}</span>
    </div>
  );
};
