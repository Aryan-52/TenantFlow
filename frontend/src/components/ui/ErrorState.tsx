import { AlertCircle } from 'lucide-react';

export const ErrorState = ({ message }: { message: string }) => {
  if (!message) return null;
  return (
    <div style={{
      backgroundColor: '#f8d7da',
      border: '1px solid #f5c6cb',
      color: '#721c24',
      padding: '0.75rem 1rem',
      borderRadius: '0.25rem',
      display: 'flex',
      alignItems: 'center',
      gap: '0.5rem',
      marginBottom: '1rem'
    }}>
      <AlertCircle size={20} />
      <span>{message}</span>
    </div>
  );
};
