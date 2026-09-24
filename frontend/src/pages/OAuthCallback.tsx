import { useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import { refreshAccessToken } from '../api/client';

/**
 * Landing page after a successful Google login. The backend has already set the
 * HttpOnly "remember me" refresh cookie (OAuth2LoginSuccessHandler) and redirected
 * here with no token in the URL by design. This page's only job is to exchange that
 * cookie for an access token via the same /api/auth/refresh path a returning "remember
 * me" user goes through, then continue into the app.
 */
const OAuthCallback = () => {
  const navigate = useNavigate();
  const { login } = useAuth();
  const hasRun = useRef(false);

  useEffect(() => {
    if (hasRun.current) return;
    hasRun.current = true;

    (async () => {
      const token = await refreshAccessToken();
      if (token) {
        await login(token);
        navigate('/', { replace: true });
      } else {
        navigate('/login?error=oauth_failed', { replace: true });
      }
    })();
  }, [login, navigate]);

  return (
    <div className="auth-container">
      <div className="card auth-card card-padded text-center">
        <p className="text-muted">Signing you in...</p>
      </div>
    </div>
  );
};

export default OAuthCallback;
