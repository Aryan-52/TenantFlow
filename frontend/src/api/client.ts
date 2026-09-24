import axios from 'axios';

// In production (Docker), requests go to a same-origin relative URL and nginx
// reverse-proxies /api/* to the backend container (see frontend/nginx.conf).
// For local `npm run dev` without that proxy, set VITE_API_URL (see .env.development)
// to point straight at the backend, e.g. http://localhost:8081.
const baseURL = import.meta.env.VITE_API_URL || '';

// Needed so the browser sends/receives the HttpOnly "remember me" refresh cookie
// (see RefreshCookieUtil on the backend). The cookie itself is never readable from JS -
// this only controls whether axios participates in the browser's normal cookie handling.
const client = axios.create({
  baseURL,
  withCredentials: true,
});

client.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// A separate, bare instance for the refresh call itself - it must never go through the
// interceptors above (no Authorization header to attach, and a 401 here must not trigger
// another refresh attempt).
const refreshClient = axios.create({ baseURL, withCredentials: true });

let refreshInFlight: Promise<string | null> | null = null;

/** Calls POST /api/auth/refresh using the HttpOnly refresh cookie (if any). Concurrent
 * callers share a single in-flight request rather than each triggering their own
 * rotation. Resolves to the new access token, or null if there is no valid persistent
 * session to refresh from. */
function refreshAccessToken(): Promise<string | null> {
  if (!refreshInFlight) {
    refreshInFlight = refreshClient
      .post('/api/auth/refresh')
      .then((res) => {
        const newToken = res.data.token as string;
        localStorage.setItem('token', newToken);
        return newToken;
      })
      .catch(() => null)
      .finally(() => {
        refreshInFlight = null;
      });
  }
  return refreshInFlight;
}

client.interceptors.response.use(
  (response) => response,
  async (error) => {
    const requestUrl: string = error.config?.url || '';
    const isAuthRequest = requestUrl.includes('/api/auth/');
    const alreadyRetried = !!error.config?._retriedAfterRefresh;

    if (error.response && error.response.status === 401 && !isAuthRequest && !alreadyRetried) {
      const newToken = await refreshAccessToken();
      if (newToken) {
        // A "remember me" session was still valid - retry the original request once
        // with the new token instead of bouncing the user to the login page.
        error.config._retriedAfterRefresh = true;
        error.config.headers.Authorization = `Bearer ${newToken}`;
        return client(error.config);
      }

      // No persistent session (or it's also invalid/expired) - this really is the end
      // of the session.
      localStorage.removeItem('token');
      if (window.location.pathname !== '/login') {
        window.location.href = '/login';
      }
    }
    return Promise.reject(error);
  }
);

export default client;
export { refreshAccessToken };
