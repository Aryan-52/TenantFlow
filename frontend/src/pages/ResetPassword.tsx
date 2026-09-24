import React, { useState, useEffect } from 'react';
import { useSearchParams, Link, useNavigate } from 'react-router-dom';
import { Check, X, CheckCircle2 } from 'lucide-react';
import client from '../api/client';
import { Button } from '../components/ui/Button';
import { PasswordInput } from '../components/ui/PasswordInput';
import { ErrorState } from '../components/ui/ErrorState';

const ResetPassword = () => {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token') || '';
  const navigate = useNavigate();

  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [success, setSuccess] = useState(false);

  const [pwdReqs, setPwdReqs] = useState({
    length: false,
    upper: false,
    lower: false,
    number: false,
    special: false,
  });

  useEffect(() => {
    setPwdReqs({
      length: newPassword.length >= 8,
      upper: /[A-Z]/.test(newPassword),
      lower: /[a-z]/.test(newPassword),
      number: /[0-9]/.test(newPassword),
      special: /[^A-Za-z0-9]/.test(newPassword),
    });
  }, [newPassword]);

  const reqItem = (met: boolean, text: string) => (
    <div className={`flex items-center gap-1 text-sm mt-1 ${met ? 'text-success' : 'text-muted'}`}>
      {met ? <Check size={14} /> : <X size={14} />} {text}
    </div>
  );

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    if (newPassword !== confirmPassword) {
      setError('Passwords do not match.');
      return;
    }

    setLoading(true);
    try {
      await client.post('/api/auth/reset-password', { token, newPassword });
      setSuccess(true);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Something went wrong. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  const brand = (
    <div className="auth-brand" style={{ flexDirection: 'column' }}>
      <span className="sidebar-brand-mark" style={{ width: 40, height: 40, fontSize: '1.25rem', marginBottom: 'var(--space-2)' }}>
        T
      </span>
      <h2 className="m-0">Reset your password</h2>
    </div>
  );

  if (!token) {
    return (
      <div className="auth-container">
        <div className="card auth-card card-padded">
          {brand}
          <ErrorState message="This link is invalid. Please request a new password reset." />
          <Link to="/forgot-password" className="btn btn-primary w-full justify-center">
            Request a new link
          </Link>
        </div>
      </div>
    );
  }

  if (success) {
    return (
      <div className="auth-container">
        <div className="card auth-card card-padded text-center">
          <div className="empty-state-icon mx-auto mb-4" style={{ backgroundColor: 'var(--success-bg)', color: 'var(--success-color)' }}>
            <CheckCircle2 size={24} />
          </div>
          <h2 className="mb-2">Password reset</h2>
          <p className="text-muted mb-6">Your password has been changed successfully. You can now sign in with your new password.</p>
          <Button fullWidth onClick={() => navigate('/login')}>
            Return to sign in
          </Button>
        </div>
      </div>
    );
  }

  return (
    <div className="auth-container">
      <div className="card auth-card card-padded">
        {brand}
        <ErrorState message={error} />
        <form onSubmit={handleSubmit} noValidate>
          <PasswordInput
            label="New password"
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
            required
            autoComplete="new-password"
            autoFocus
          />
          {newPassword && (
            <div className="mb-4 p-3 rounded-md" style={{ background: 'var(--bg-subtle)' }}>
              {reqItem(pwdReqs.length, 'At least 8 characters')}
              {reqItem(pwdReqs.upper, 'One uppercase letter')}
              {reqItem(pwdReqs.lower, 'One lowercase letter')}
              {reqItem(pwdReqs.number, 'One number')}
              {reqItem(pwdReqs.special, 'One special character')}
            </div>
          )}
          <PasswordInput
            label="Confirm new password"
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
            required
            autoComplete="new-password"
            error={confirmPassword && newPassword !== confirmPassword ? 'Passwords do not match' : undefined}
          />
          <Button
            type="submit"
            fullWidth
            isLoading={loading}
            disabled={loading || newPassword !== confirmPassword || !Object.values(pwdReqs).every(Boolean)}
          >
            {loading ? 'Resetting...' : 'Reset password'}
          </Button>
        </form>
      </div>
    </div>
  );
};

export default ResetPassword;
