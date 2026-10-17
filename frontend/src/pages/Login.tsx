import React, { useState, useEffect } from 'react';
import { useNavigate, Link, useSearchParams } from 'react-router-dom';
import { Mail } from 'lucide-react';
import client from '../api/client';
import { useAuth } from '../contexts/AuthContext';
import { Button } from '../components/ui/Button';
import { Input } from '../components/ui/Input';
import { PasswordInput } from '../components/ui/PasswordInput';
import { ErrorState } from '../components/ui/ErrorState';
import { AuthLayout } from '../components/layout/AuthLayout';

// Vite dev server has no reverse proxy, so hitting the OAuth entry point needs the
// absolute backend URL there; in Docker, nginx proxies /oauth2/* same-origin (see
// nginx.conf), so a relative path is correct and VITE_API_URL is unset/empty.
const API_BASE = import.meta.env.VITE_API_URL || '';

const GoogleIcon = () => (
  <svg width="18" height="18" viewBox="0 0 48 48" aria-hidden="true">
    <path fill="#FFC107" d="M43.6 20.5H42V20H24v8h11.3c-1.6 4.7-6.1 8-11.3 8-6.6 0-12-5.4-12-12s5.4-12 12-12c3.1 0 5.8 1.1 8 3l5.7-5.7C34.6 6.1 29.6 4 24 4 12.9 4 4 12.9 4 24s8.9 20 20 20 20-8.9 20-20c0-1.3-.1-2.7-.4-3.5z" />
    <path fill="#FF3D00" d="M6.3 14.7l6.6 4.8C14.6 15.9 18.9 13 24 13c3.1 0 5.8 1.1 8 3l5.7-5.7C34.6 6.1 29.6 4 24 4 16.3 4 9.7 8.3 6.3 14.7z" />
    <path fill="#4CAF50" d="M24 44c5.5 0 10.4-1.9 14.3-5.1l-6.6-5.6C29.6 35.1 26.9 36 24 36c-5.2 0-9.6-3.3-11.2-7.9l-6.6 5.1C9.6 39.6 16.3 44 24 44z" />
    <path fill="#1976D2" d="M43.6 20.5H42V20H24v8h11.3c-.8 2.3-2.2 4.2-4.1 5.6l6.6 5.6C41.7 36 44 30.5 44 24c0-1.3-.1-2.7-.4-3.5z" />
  </svg>
);

const Login = () => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [rememberMe, setRememberMe] = useState(false);
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(false);

  const navigate = useNavigate();
  const { login } = useAuth();
  const [searchParams] = useSearchParams();
  const oauthFailed = searchParams.get('error') === 'oauth_failed';

  // Never link straight to /oauth2/authorization/google without first checking it's
  // actually usable - hitting it with no Google credentials configured is exactly the
  // broken experience this avoids. null = "still checking" (button shown disabled).
  const [googleAvailable, setGoogleAvailable] = useState<boolean | null>(null);
  useEffect(() => {
    let cancelled = false;
    client
      .get('/api/auth/oauth-providers')
      .then((res) => {
        if (!cancelled) setGoogleAvailable(!!res.data.google);
      })
      .catch(() => {
        if (!cancelled) setGoogleAvailable(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setFieldErrors({});
    setLoading(true);

    try {
      const res = await client.post('/api/auth/login', { email, password, rememberMe });
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
    <AuthLayout
      title="Welcome back"
      subtitle="Sign in to continue to your workspace."
      footer={
        <>
          New to TenantFlow? <Link to="/register">Create an account</Link>
        </>
      }
    >
      <ErrorState message={error} />
      {oauthFailed && !error && (
        <ErrorState message="We couldn't sign you in with Google. Please try again or use your email and password." />
      )}

      <form onSubmit={handleSubmit} noValidate>
        <Input
          id="login-email"
          label="Email"
          type="email"
          icon={<Mail size={15} />}
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          error={fieldErrors.email}
          required
          autoComplete="email"
          autoFocus
          placeholder="you@company.com"
        />
        <PasswordInput
          label="Password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          error={fieldErrors.password}
          required
          autoComplete="current-password"
        />
        <div className="flex justify-between items-center mb-6">
          <label className="checkbox-label">
            <input type="checkbox" checked={rememberMe} onChange={(e) => setRememberMe(e.target.checked)} />
            Keep me signed in
          </label>
          <Link to="/forgot-password" className="auth-inline-link">
            Forgot password?
          </Link>
        </div>
        <Button type="submit" fullWidth isLoading={loading} disabled={loading} className="btn-lg">
          {loading ? 'Signing in…' : 'Sign in'}
        </Button>
      </form>

      <div className="divider-with-text">or</div>

      {googleAvailable ? (
        <a href={`${API_BASE}/oauth2/authorization/google`} className="btn btn-secondary btn-lg w-full">
          <GoogleIcon /> Continue with Google
        </a>
      ) : (
        <>
          <button type="button" className="btn btn-secondary btn-lg w-full" disabled title="Google sign-in is not configured.">
            <GoogleIcon /> Continue with Google
          </button>
          {googleAvailable === false && <p className="text-center text-muted text-xs mt-2">Google sign-in is not configured.</p>}
        </>
      )}
    </AuthLayout>
  );
};

export default Login;
