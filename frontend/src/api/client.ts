import axios from 'axios';

// In production (Docker), requests go to a same-origin relative URL and nginx
// reverse-proxies /api/* to the backend container (see frontend/nginx.conf).
// For local `npm run dev` without that proxy, set VITE_API_URL (see .env.development)
// to point straight at the backend, e.g. http://localhost:8081.
const baseURL = import.meta.env.VITE_API_URL || '';

const client = axios.create({
  baseURL,
});

client.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

client.interceptors.response.use(
  (response) => response,
  (error) => {
    // Only redirect to login for unauthorized errors when NOT already on an auth page,
    // and never for the login/register calls themselves (a failed login attempt should
    // show an inline error, not bounce the user back to /login).
    const requestUrl: string = error.config?.url || '';
    const isAuthRequest = requestUrl.includes('/api/auth/');

    if (error.response && error.response.status === 401 && !isAuthRequest) {
      localStorage.removeItem('token');
      if (window.location.pathname !== '/login') {
        window.location.href = '/login';
      }
    }
    return Promise.reject(error);
  }
);

export default client;
