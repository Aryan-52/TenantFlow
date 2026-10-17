import React, { useState } from 'react';
import { useSearchParams, Link, useNavigate } from 'react-router-dom';
import { CheckCircle2 } from 'lucide-react';
import client from '../api/client';
import { Button } from '../components/ui/Button';
import { PasswordInput } from '../components/ui/PasswordInput';
import { ErrorState } from '../components/ui/ErrorState';
import { AuthLayout } from '../components/layout/AuthLayout';
import { PasswordRequirements } from '../components/ui/PasswordRequirements';
import { meetsPasswordPolicy } from '../components/ui/passwordPolicy';

const ResetPassword = () => {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token') || '';
  const navigate = useNavigate();

  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [success, setSuccess] = useState(false);

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

  if (!token) {
    return (
      <AuthLayout title="Reset your password" footer={<Link to="/login">Back to sign in</Link>}>
        <ErrorState message="This link is invalid or incomplete. Please request a new password reset." />
        <Link to="/forgot-password" className="btn btn-primary btn-lg w-full">
          Request a new link
        </Link>
      </AuthLayout>
    );
  }

  if (success) {
    return (
      <AuthLayout title="Password updated" subtitle="Your password has been changed. You can now sign in with your new password.">
        <div className="auth-success-icon" aria-hidden="true">
          <CheckCircle2 size={22} />
        </div>
        <Button fullWidth className="btn-lg" onClick={() => navigate('/login')}>
          Continue to sign in
        </Button>
      </AuthLayout>
    );
  }

  return (
    <AuthLayout title="Choose a new password" subtitle="Pick something strong that you don't use anywhere else.">
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
        <PasswordRequirements password={newPassword} />
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
          className="btn-lg mt-2"
          isLoading={loading}
          disabled={loading || newPassword !== confirmPassword || !meetsPasswordPolicy(newPassword)}
        >
          {loading ? 'Saving…' : 'Update password'}
        </Button>
      </form>
    </AuthLayout>
  );
};

export default ResetPassword;
