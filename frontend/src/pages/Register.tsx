import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { User as UserIcon, Mail } from 'lucide-react';
import client from '../api/client';
import { useAuth } from '../contexts/AuthContext';
import { Button } from '../components/ui/Button';
import { Input } from '../components/ui/Input';
import { PasswordInput } from '../components/ui/PasswordInput';
import { ErrorState } from '../components/ui/ErrorState';
import { AuthLayout } from '../components/layout/AuthLayout';
import { PasswordRequirements } from '../components/ui/PasswordRequirements';
import { meetsPasswordPolicy } from '../components/ui/passwordPolicy';

const Register = () => {
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');

  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const { login } = useAuth();


  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setFieldErrors({});

    if (password !== confirmPassword) {
      setFieldErrors({ confirmPassword: 'Passwords do not match' });
      return;
    }

    setLoading(true);

    try {
      const res = await client.post('/api/auth/register', { name, email, password });
      await login(res.data.token);
      navigate('/');
    } catch (err: any) {
      if (err.response?.data?.fieldErrors && Object.keys(err.response.data.fieldErrors).length > 0) {
        setFieldErrors(err.response.data.fieldErrors);
      } else if (err.response?.data?.message) {
        setError(err.response.data.message);
      } else {
        setError('Unable to connect to the server. Please try again.');
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <AuthLayout
      title="Create your account"
      subtitle="Start organizing projects with your team in minutes."
      footer={
        <>
          Already have an account? <Link to="/login">Sign in</Link>
        </>
      }
    >
      <ErrorState message={error} />

      <form onSubmit={handleSubmit} noValidate>
        <Input
          label="Full name"
          type="text"
          icon={<UserIcon size={15} />}
          value={name}
          onChange={(e) => setName(e.target.value)}
          error={fieldErrors.name}
          required
          autoComplete="name"
          autoFocus
          placeholder="Ada Lovelace"
        />
        <Input
          label="Work email"
          type="email"
          icon={<Mail size={15} />}
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          error={fieldErrors.email}
          required
          autoComplete="email"
          placeholder="you@company.com"
        />
        <PasswordInput
          label="Password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          error={fieldErrors.password}
          required
          autoComplete="new-password"
        />
        <PasswordRequirements password={password} />
        <PasswordInput
          label="Confirm password"
          value={confirmPassword}
          onChange={(e) => setConfirmPassword(e.target.value)}
          error={confirmPassword && password !== confirmPassword ? 'Passwords do not match' : fieldErrors.confirmPassword}
          required
          autoComplete="new-password"
        />
        <Button
          type="submit"
          fullWidth
          className="btn-lg mt-2"
          isLoading={loading}
          disabled={loading || password !== confirmPassword || !meetsPasswordPolicy(password)}
        >
          {loading ? 'Creating account…' : 'Create account'}
        </Button>
      </form>
    </AuthLayout>
  );
};

export default Register;
