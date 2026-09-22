import React, { useState } from 'react';
import { useAuth } from '../contexts/AuthContext';
import client from '../api/client';
import { Button } from '../components/ui/Button';
import { Input } from '../components/ui/Input';
import { PasswordInput } from '../components/ui/PasswordInput';
import { Avatar } from '../components/ui/Avatar';
import { ErrorState } from '../components/ui/ErrorState';
import { Alert } from '../components/ui/Alert';
import { PageHeader } from '../components/ui/PageHeader';
import { Mail, Hash, Calendar } from 'lucide-react';

const Profile = () => {
  const { user, refreshUser } = useAuth();

  const [name, setName] = useState(user?.name || '');
  const [nameError, setNameError] = useState('');
  const [nameSuccess, setNameSuccess] = useState('');
  const [nameLoading, setNameLoading] = useState(false);

  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [passwordError, setPasswordError] = useState('');
  const [passwordSuccess, setPasswordSuccess] = useState('');
  const [passwordLoading, setPasswordLoading] = useState(false);

  if (!user) return null;

  const handleNameSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setNameError('');
    setNameSuccess('');
    setNameLoading(true);
    try {
      await client.put('/api/users/me', { name });
      await refreshUser();
      setNameSuccess('Profile updated successfully.');
      setTimeout(() => setNameSuccess(''), 3000);
    } catch (err: any) {
      setNameError(err.response?.data?.message || 'Failed to update profile');
    } finally {
      setNameLoading(false);
    }
  };

  const handlePasswordSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setPasswordError('');
    setPasswordSuccess('');

    if (newPassword !== confirmPassword) {
      setPasswordError('New passwords do not match.');
      return;
    }

    setPasswordLoading(true);
    try {
      await client.put('/api/users/me/password', { currentPassword, newPassword });
      setPasswordSuccess('Password changed successfully.');
      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
      setTimeout(() => setPasswordSuccess(''), 3000);
    } catch (err: any) {
      setPasswordError(err.response?.data?.message || 'Failed to change password');
    } finally {
      setPasswordLoading(false);
    }
  };

  return (
    <div className="max-w-2xl mx-auto">
      <PageHeader title="Your profile" subtitle="Manage your personal account information." />

      <div className="card mb-6">
        <div className="card-body">
          <div className="flex items-center gap-4 mb-6 pb-6 border-b">
            <Avatar name={user.name} size="lg" />
            <div style={{ minWidth: 0 }}>
              <h2 className="mb-1 truncate">{user.name}</h2>
              <p className="text-muted truncate">{user.email}</p>
            </div>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-6 mb-2">
            <div>
              <p className="text-muted text-sm font-medium mb-2 flex items-center gap-2">
                <Mail size={15} /> Email address
              </p>
              <p className="font-medium">{user.email}</p>
            </div>
            {user.createdAt && (
              <div>
                <p className="text-muted text-sm font-medium mb-2 flex items-center gap-2">
                  <Calendar size={15} /> Member since
                </p>
                <p className="font-medium">{new Date(user.createdAt).toLocaleDateString()}</p>
              </div>
            )}
            <div className="md:col-span-2">
              <p className="text-muted text-sm font-medium mb-2 flex items-center gap-2">
                <Hash size={15} /> Account ID
              </p>
              <p className="font-mono text-sm p-2 rounded-md" style={{ backgroundColor: 'var(--bg-subtle)', display: 'inline-block' }}>
                {user.id}
              </p>
            </div>
          </div>
        </div>
      </div>

      <div className="card mb-6">
        <div className="card-header">
          <h3 className="mb-0">Edit name</h3>
        </div>
        <div className="card-body">
          <ErrorState message={nameError} />
          {nameSuccess && <Alert variant="success" className="mb-4">{nameSuccess}</Alert>}
          <form onSubmit={handleNameSubmit} noValidate>
            <Input label="Full name" value={name} onChange={(e) => setName(e.target.value)} required />
            <Button type="submit" isLoading={nameLoading} disabled={nameLoading || name.trim() === user.name}>
              Save name
            </Button>
          </form>
        </div>
      </div>

      <div className="card">
        <div className="card-header">
          <h3 className="mb-0">Change password</h3>
        </div>
        <div className="card-body">
          <ErrorState message={passwordError} />
          {passwordSuccess && <Alert variant="success" className="mb-4">{passwordSuccess}</Alert>}
          <form onSubmit={handlePasswordSubmit} noValidate>
            <PasswordInput
              label="Current password"
              value={currentPassword}
              onChange={(e) => setCurrentPassword(e.target.value)}
              required
              autoComplete="current-password"
            />
            <PasswordInput
              label="New password"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              required
              autoComplete="new-password"
              hint="At least 8 characters, with an uppercase letter, lowercase letter, number, and special character."
            />
            <PasswordInput
              label="Confirm new password"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              required
              autoComplete="new-password"
              error={confirmPassword && newPassword !== confirmPassword ? 'Passwords do not match' : undefined}
            />
            <Button type="submit" isLoading={passwordLoading} disabled={passwordLoading || !currentPassword || !newPassword || newPassword !== confirmPassword}>
              Update password
            </Button>
          </form>
        </div>
      </div>
    </div>
  );
};

export default Profile;
