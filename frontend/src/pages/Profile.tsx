import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import client from '../api/client';
import { Button } from '../components/ui/Button';
import { Input } from '../components/ui/Input';
import { PasswordInput } from '../components/ui/PasswordInput';
import { Avatar } from '../components/ui/Avatar';
import { ErrorState } from '../components/ui/ErrorState';
import { PasswordRequirements } from '../components/ui/PasswordRequirements';
import { useToast } from '../contexts/ToastContext';
import { PageHeader } from '../components/ui/PageHeader';
import { Mail, Calendar, Lock } from 'lucide-react';

const Profile = () => {
  const { user, refreshUser, login } = useAuth();
  const toast = useToast();

  const [name, setName] = useState(user?.name || '');
  const [nameError, setNameError] = useState('');
  const [nameLoading, setNameLoading] = useState(false);

  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [passwordError, setPasswordError] = useState('');
  const [currentPasswordError, setCurrentPasswordError] = useState('');
  const [newPasswordError, setNewPasswordError] = useState('');
  const [passwordLoading, setPasswordLoading] = useState(false);

  if (!user) return null;

  const handleNameSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setNameError('');
    setNameLoading(true);
    try {
      await client.put('/api/users/me', { name });
      await refreshUser();
      toast.success('Name updated');
    } catch (err: any) {
      setNameError(err.response?.data?.message || 'Failed to update profile');
    } finally {
      setNameLoading(false);
    }
  };

  const clearPasswordErrors = () => {
    setPasswordError('');
    setCurrentPasswordError('');
    setNewPasswordError('');
  };

  const handlePasswordSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    clearPasswordErrors();

    if (newPassword !== confirmPassword) {
      setPasswordError('New passwords do not match.');
      return;
    }

    setPasswordLoading(true);
    try {
      // The backend returns a freshly-issued access token here (it bumps the account's
      // token_version to sign out every other session), so this tab must swap it in
      // immediately via login() rather than just showing a success message - otherwise
      // this session would itself go stale on its very next request.
      const res = await client.put('/api/users/me/password', { currentPassword, newPassword });
      await login(res.data.token);

      toast.success('Password changed. Other sessions were signed out; you stay signed in.');
      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
    } catch (err: any) {
      const fieldErrors = err.response?.data?.fieldErrors;
      if (fieldErrors?.currentPassword) {
        setCurrentPasswordError(fieldErrors.currentPassword);
      } else if (fieldErrors?.newPassword) {
        setNewPasswordError(fieldErrors.newPassword);
      } else {
        setPasswordError(err.response?.data?.message || 'Failed to change password');
      }
    } finally {
      setPasswordLoading(false);
    }
  };

  return (
    <div className="max-w-4xl">
      <PageHeader title="Profile" subtitle="Your personal details and sign-in security. These apply in every workspace." />

      <div className="card card-body mb-8">
        <div className="profile-hero">
          <Avatar name={user.name} size="lg" />
          <div style={{ minWidth: 0 }}>
            <div className="profile-hero-name truncate">{user.name}</div>
            <div className="text-sm text-muted flex items-center gap-4 flex-wrap mt-1">
              <span className="flex items-center gap-1">
                <Mail size={14} aria-hidden="true" /> {user.email}
              </span>
              {user.createdAt && (
                <span className="flex items-center gap-1">
                  <Calendar size={14} aria-hidden="true" /> Member since{' '}
                  {new Date(user.createdAt).toLocaleDateString(undefined, { year: 'numeric', month: 'long' })}
                </span>
              )}
            </div>
          </div>
        </div>
      </div>

      <section className="settings-section" aria-labelledby="profile-name">
        <div>
          <h2 id="profile-name" className="settings-section-title">Name</h2>
          <p className="settings-section-desc">How teammates see you on tasks, projects and activity.</p>
        </div>
        <div className="card card-body">
          <ErrorState message={nameError} />
          <form onSubmit={handleNameSubmit} noValidate>
            <Input label="Full name" value={name} onChange={(e) => setName(e.target.value)} required autoComplete="name" />
            <div className="form-group">
              <label htmlFor="profile-email" className="flex items-center gap-2">
                Email <span className="readonly-hint"><Lock size={11} aria-hidden="true" /> Read-only</span>
              </label>
              <input id="profile-email" value={user.email} readOnly disabled />
            </div>
            <div className="flex justify-end">
              <Button type="submit" isLoading={nameLoading} disabled={nameLoading || !name.trim() || name.trim() === user.name}>
                Save name
              </Button>
            </div>
          </form>
        </div>
      </section>

      <section className="settings-section" aria-labelledby="profile-password">
        <div>
          <h2 id="profile-password" className="settings-section-title">Password</h2>
          <p className="settings-section-desc">Changing it signs you out everywhere else. This browser stays signed in.</p>
        </div>
        <div className="card card-body">
          <ErrorState message={passwordError} />
          <form onSubmit={handlePasswordSubmit} noValidate>
            <PasswordInput
              label="Current password"
              value={currentPassword}
              onChange={(e) => {
                setCurrentPassword(e.target.value);
                if (currentPasswordError) setCurrentPasswordError('');
              }}
              error={currentPasswordError}
              required
              autoComplete="current-password"
            />
            {currentPasswordError && (
              <p className="text-sm mb-4" style={{ marginTop: '-0.5rem' }}>
                <Link to="/forgot-password" className="auth-inline-link">
                  Forgot password?
                </Link>
              </p>
            )}
            <div className="form-row">
              <PasswordInput
                label="New password"
                value={newPassword}
                onChange={(e) => {
                  setNewPassword(e.target.value);
                  if (newPasswordError) setNewPasswordError('');
                }}
                error={newPasswordError}
                required
                autoComplete="new-password"
              />
              <PasswordInput
                label="Confirm new password"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                required
                autoComplete="new-password"
                error={confirmPassword && newPassword !== confirmPassword ? 'Passwords do not match' : undefined}
              />
            </div>
            <PasswordRequirements password={newPassword} />
            <div className="flex justify-end">
              <Button
                type="submit"
                isLoading={passwordLoading}
                disabled={passwordLoading || !currentPassword || !newPassword || newPassword !== confirmPassword}
              >
                Update password
              </Button>
            </div>
          </form>
        </div>
      </section>
    </div>
  );
};

export default Profile;
