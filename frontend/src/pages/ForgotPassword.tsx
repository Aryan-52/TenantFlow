import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { Mail, ArrowLeft } from 'lucide-react';
import client from '../api/client';
import { Button } from '../components/ui/Button';
import { Input } from '../components/ui/Input';
import { ErrorState } from '../components/ui/ErrorState';
import { Alert } from '../components/ui/Alert';
import { AuthLayout } from '../components/layout/AuthLayout';

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
    <AuthLayout
      title={submitted ? 'Check your email' : 'Reset your password'}
      subtitle={submitted ? undefined : "Enter the email you signed up with and we'll send you a reset link."}
      footer={
        <>
          Remembered it? <Link to="/login">Back to sign in</Link>
        </>
      }
    >
      {submitted ? (
        <>
          <Alert variant="success">{GENERIC_MESSAGE}</Alert>
          <Link to="/login" className="btn btn-secondary btn-lg w-full mt-4">
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
              placeholder="you@company.com"
            />
            <Button type="submit" fullWidth className="btn-lg" isLoading={loading} disabled={loading}>
              {loading ? 'Sending…' : 'Send reset link'}
            </Button>
          </form>
        </>
      )}
    </AuthLayout>
  );
};

export default ForgotPassword;
