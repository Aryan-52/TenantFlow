import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { Mail } from 'lucide-react';
import client from '../api/client';
import { useAuth } from '../contexts/AuthContext';
import { Button } from '../components/ui/Button';
import { Input } from '../components/ui/Input';
import { PasswordInput } from '../components/ui/PasswordInput';
import { ErrorState } from '../components/ui/ErrorState';

const Login = () => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(false);

  const navigate = useNavigate();
  const { login } = useAuth();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setFieldErrors({});
    setLoading(true);

    try {
      const res = await client.post('/api/auth/login', { email, password });
      await login(res.data.token);
      navigate('/');
    } catch (err: any) {
      if (err.response?.data?.fieldErrors && Object.keys(err.response.data.fieldErrors).length > 0) {
        setFieldErrors(err.response.data.fieldErrors);
      } else if (err.response?.status === 401) {
        setError('Invalid email or password.');
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
    <div className="auth-container">
      <div className="card auth-card card-padded">
        <div className="auth-brand" style={{ flexDirection: 'column' }}>
          <span className="sidebar-brand-mark" style={{ width: 40, height: 40, fontSize: '1.25rem', marginBottom: 'var(--space-2)' }}>
            T
          </span>
          <h2 className="m-0">Welcome back</h2>
          <p className="text-muted text-sm m-0">Sign in to your TenantFlow account</p>
        </div>

        <ErrorState message={error} />

        <form onSubmit={handleSubmit} noValidate>
          <Input
            label="Email"
            type="email"
            icon={<Mail size={15} />}
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            error={fieldErrors.email}
            required
            autoComplete="email"
            autoFocus
          />
          <PasswordInput
            label="Password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            error={fieldErrors.password}
            required
            autoComplete="current-password"
          />
          <Button type="submit" fullWidth isLoading={loading} disabled={loading}>
            {loading ? 'Signing in...' : 'Sign In'}
          </Button>
        </form>
        <p className="text-center text-muted mt-6 text-sm">
          Don't have an account?{' '}
          <Link to="/register" style={{ color: 'var(--primary-color)', fontWeight: 500 }}>
            Sign up
          </Link>
        </p>
      </div>
    </div>
  );
};

export default Login;
