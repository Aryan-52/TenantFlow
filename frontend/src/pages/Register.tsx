import React, { useState, useEffect } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { Check, X, User as UserIcon, Mail } from 'lucide-react';
import client from '../api/client';
import { useAuth } from '../contexts/AuthContext';
import { Button } from '../components/ui/Button';
import { Input } from '../components/ui/Input';
import { PasswordInput } from '../components/ui/PasswordInput';
import { ErrorState } from '../components/ui/ErrorState';

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

  const [pwdReqs, setPwdReqs] = useState({
    length: false,
    upper: false,
    lower: false,
    number: false,
    special: false,
  });

  useEffect(() => {
    setPwdReqs({
      length: password.length >= 8,
      upper: /[A-Z]/.test(password),
      lower: /[a-z]/.test(password),
      number: /[0-9]/.test(password),
      special: /[^A-Za-z0-9]/.test(password),
    });
  }, [password]);

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

  const reqItem = (met: boolean, text: string) => (
    <div className={`flex items-center gap-1 text-sm mt-1 ${met ? 'text-success' : 'text-muted'}`}>
      {met ? <Check size={14} /> : <X size={14} />} {text}
    </div>
  );

  return (
    <div className="auth-container">
      <div className="card auth-card card-padded">
        <div className="auth-brand" style={{ flexDirection: 'column' }}>
          <span className="sidebar-brand-mark" style={{ width: 40, height: 40, fontSize: '1.25rem', marginBottom: 'var(--space-2)' }}>
            T
          </span>
          <h2 className="m-0">Create account</h2>
          <p className="text-muted text-sm m-0">Join TenantFlow today</p>
        </div>

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
          />
          <Input
            label="Email"
            type="email"
            icon={<Mail size={15} />}
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            error={fieldErrors.email}
            required
            autoComplete="email"
          />
          <PasswordInput
            label="Password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            error={fieldErrors.password}
            required
            autoComplete="new-password"
          />
          {password && (
            <div className="mb-4 p-3 rounded-md" style={{ background: 'var(--bg-subtle)' }}>
              {reqItem(pwdReqs.length, 'At least 8 characters')}
              {reqItem(pwdReqs.upper, 'One uppercase letter')}
              {reqItem(pwdReqs.lower, 'One lowercase letter')}
              {reqItem(pwdReqs.number, 'One number')}
              {reqItem(pwdReqs.special, 'One special character')}
            </div>
          )}
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
            isLoading={loading}
            disabled={loading || password !== confirmPassword || !Object.values(pwdReqs).every(Boolean)}
          >
            {loading ? 'Creating account...' : 'Create account'}
          </Button>
        </form>
        <p className="text-center text-muted mt-6 text-sm">
          Already have an account?{' '}
          <Link to="/login" style={{ color: 'var(--primary-color)', fontWeight: 500 }}>
            Sign in
          </Link>
        </p>
      </div>
    </div>
  );
};

export default Register;
