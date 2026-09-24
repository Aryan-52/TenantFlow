import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { Mail, ArrowLeft } from 'lucide-react';
import client from '../api/client';
import { Button } from '../components/ui/Button';
import { Input } from '../components/ui/Input';
import { ErrorState } from '../components/ui/ErrorState';
import { Alert } from '../components/ui/Alert';

const GENERIC_MESSAGE = 'If an account exists for this email, you will receive password reset instructions.';

const ForgotPassword = () => {
  const [email, setEmail] = useState('');
  const [submitted, setSubmitted] = useState(false);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      await client.post('/api/auth/forgot-password', { email });
      // Always show the same generic outcome, whether or not the email exists.
      setSubmitted(true);
    } catch (err: any) {
      // Only network/validation failures land here (e.g. malformed email) - the backend
      // itself never distinguishes "unknown email" as an error.
      setError(err.response?.data?.message || 'Something went wrong. Please try again.');
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
          <h2 className="m-0">Forgot your password?</h2>
          <p className="text-muted text-sm m-0 text-center">
            Enter your email and we'll send you instructions to reset it.
          </p>
        </div>

        {submitted ? (
          <>
            <Alert variant="success">{GENERIC_MESSAGE}</Alert>
            <Link to="/login" className="btn btn-secondary w-full mt-4 justify-center">
              <ArrowLeft size={16} /> Back to sign in
            </Link>
          </>
        ) : (
          <>
            <ErrorState message={error} />
            <form onSubmit={handleSubmit} noValidate>
              <Input
                label="Email"
                type="email"
                icon={<Mail size={15} />}
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
                autoComplete="email"
                autoFocus
              />
              <Button type="submit" fullWidth isLoading={loading} disabled={loading}>
                {loading ? 'Sending...' : 'Send reset instructions'}
              </Button>
            </form>
            <p className="text-center text-muted mt-6 text-sm">
              Remembered your password?{' '}
              <Link to="/login" style={{ color: 'var(--primary-color)', fontWeight: 500 }}>
                Sign in
              </Link>
            </p>
          </>
        )}
      </div>
    </div>
  );
};

export default ForgotPassword;
