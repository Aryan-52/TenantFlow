import React, { useState, useEffect } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { Eye, EyeOff, Check, X } from 'lucide-react';
import client from '../api/client';
import { useAuth } from '../contexts/AuthContext';
import { Button } from '../components/ui/Button';
import { ErrorState } from '../components/ui/ErrorState';

const Register = () => {
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);

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
    special: false
  });

  useEffect(() => {
    setPwdReqs({
      length: password.length >= 8,
      upper: /[A-Z]/.test(password),
      lower: /[a-z]/.test(password),
      number: /[0-9]/.test(password),
      special: /[^A-Za-z0-9]/.test(password)
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
    <div className="flex items-center gap-1 text-sm mt-1" style={{ color: met ? 'var(--success-color)' : 'var(--text-muted)' }}>
      {met ? <Check size={14} /> : <X size={14} />} {text}
    </div>
  );

  return (
    <div className="auth-container">
      <div className="card auth-card">
        <div className="text-center mb-6">
          <div style={{ width: 40, height: 40, backgroundColor: 'var(--primary-color)', borderRadius: '8px', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#fff', fontSize: '1.25rem', fontWeight: 'bold', margin: '0 auto 1rem' }}>T</div>
          <h2>Create Account</h2>
          <p className="text-muted">Join TenantFlow today</p>
        </div>
        
        <ErrorState message={error} />
        
        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label>Full Name</label>
            <input
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
              className="w-full"
            />
            {fieldErrors.name && <div className="text-danger text-sm mt-1">{fieldErrors.name}</div>}
          </div>
          <div className="form-group">
            <label>Email</label>
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
              className="w-full"
            />
            {fieldErrors.email && <div className="text-danger text-sm mt-1">{fieldErrors.email}</div>}
          </div>
          <div className="form-group">
            <label>Password</label>
            <div style={{ position: 'relative' }}>
              <input
                type={showPassword ? 'text' : 'password'}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
                className="w-full"
                style={{ paddingRight: '2.5rem' }}
              />
              <button
                type="button"
                onClick={() => setShowPassword(!showPassword)}
                style={{ position: 'absolute', right: '0.5rem', top: '50%', transform: 'translateY(-50%)', background: 'none', border: 'none', cursor: 'pointer', color: 'var(--text-muted)' }}
              >
                {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
              </button>
            </div>
            {password && (
              <div className="mt-2" style={{ background: 'var(--bg-subtle)', padding: '0.75rem', borderRadius: 'var(--radius-md)' }}>
                {reqItem(pwdReqs.length, 'At least 8 characters')}
                {reqItem(pwdReqs.upper, 'One uppercase letter')}
                {reqItem(pwdReqs.lower, 'One lowercase letter')}
                {reqItem(pwdReqs.number, 'One number')}
                {reqItem(pwdReqs.special, 'One special character')}
              </div>
            )}
            {fieldErrors.password && <div className="text-danger text-sm mt-1">{fieldErrors.password}</div>}
          </div>
          <div className="form-group mb-6">
            <label>Confirm Password</label>
            <div style={{ position: 'relative' }}>
              <input
                type={showConfirmPassword ? 'text' : 'password'}
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                required
                className="w-full"
                style={{ paddingRight: '2.5rem' }}
              />
              <button
                type="button"
                onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                style={{ position: 'absolute', right: '0.5rem', top: '50%', transform: 'translateY(-50%)', background: 'none', border: 'none', cursor: 'pointer', color: 'var(--text-muted)' }}
              >
                {showConfirmPassword ? <EyeOff size={18} /> : <Eye size={18} />}
              </button>
            </div>
            {confirmPassword && password !== confirmPassword && <div className="text-danger text-sm mt-1">Passwords do not match</div>}
            {fieldErrors.confirmPassword && <div className="text-danger text-sm mt-1">{fieldErrors.confirmPassword}</div>}
          </div>
          <Button type="submit" className="w-full" isLoading={loading} disabled={loading || password !== confirmPassword || !Object.values(pwdReqs).every(Boolean)}>
            {loading ? 'Creating account...' : 'Create Account'}
          </Button>
        </form>
        <p className="text-center text-muted mt-6 text-sm">
          Already have an account? <Link to="/login" style={{ color: 'var(--primary-color)', textDecoration: 'none', fontWeight: 500 }}>Sign in</Link>
        </p>
      </div>
    </div>
  );
};

export default Register;
