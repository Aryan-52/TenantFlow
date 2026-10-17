// @vitest-environment node
import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import { PLACEHOLDER, checkVercelConfig, sourceToRegExp } from './check-vercel-config.js';

const committed = JSON.parse(readFileSync(new URL('../vercel.json', import.meta.url), 'utf8'));
const withHost = (host) => JSON.parse(JSON.stringify(committed).replaceAll(`${PLACEHOLDER}.onrender.com`, host));
const fallback = sourceToRegExp(committed.rewrites.at(-1).source);

describe('vercel.json routing', () => {
  it('sends React Router deep links to index.html', () => {
    for (const path of ['/', '/login', '/welcome', '/oauth-callback', '/tenants/6180ea63/dashboard', '/tenants/t/projects/p/kanban']) {
      expect(fallback.test(path), path).toBe(true);
    }
  });

  it('never rewrites backend, OAuth or asset requests to index.html', () => {
    for (const path of ['/api/auth/refresh', '/api/tenants/t/projects', '/oauth2/authorization/google', '/login/oauth2/code/google', '/assets/index-abc.js']) {
      expect(fallback.test(path), path).toBe(false);
    }
  });

  it('proxies exactly the backend route families, keeping their paths', () => {
    const proxied = committed.rewrites.filter((r) => r.destination !== '/index.html');
    expect(proxied.map((r) => r.source)).toEqual(['/api/:path*', '/oauth2/:path*', '/login/oauth2/:path*']);
    expect(sourceToRegExp('/api/:path*').test('/api/auth/login')).toBe(true);
    expect(sourceToRegExp('/api/:path*').test('/apix')).toBe(false);
  });

  it('refuses to deploy the proxy while the backend host is still the placeholder', () => {
    const problems = checkVercelConfig(committed, {});
    expect(problems).toHaveLength(1);
    expect(problems[0]).toContain(PLACEHOLDER);
  });

  it('accepts the proxy once a real https backend host is filled in', () => {
    expect(checkVercelConfig(withHost('tenantflow-api.onrender.com'), {})).toEqual([]);
  });

  it('accepts an own API domain without touching the proxy rules', () => {
    expect(checkVercelConfig(committed, { VITE_API_URL: 'https://api.example.com' })).toEqual([]);
    expect(checkVercelConfig(committed, { VITE_API_URL: 'http://api.example.com' })).toContain('VITE_API_URL must be an https:// address in production.');
    expect(checkVercelConfig(committed, { VITE_API_URL: 'https://localhost:8095' })).toContain('VITE_API_URL points at a local machine.');
  });

  it('catches the mistakes that would break routing', () => {
    const config = withHost('tenantflow-api.onrender.com');
    const fallbackFirst = { ...config, rewrites: [config.rewrites.at(-1), ...config.rewrites.slice(0, -1)] };
    expect(checkVercelConfig(fallbackFirst, {}).join(' ')).toContain('must be the last rewrite');

    const greedy = { ...config, rewrites: [...config.rewrites.slice(0, -1), { source: '/(.*)', destination: '/index.html' }] };
    expect(checkVercelConfig(greedy, {}).join(' ')).toContain('also matches /api/auth/refresh');

    const missingOAuth = { ...config, rewrites: config.rewrites.filter((r) => r.source !== '/login/oauth2/:path*') };
    expect(checkVercelConfig(missingOAuth, {}).join(' ')).toContain('Missing proxy rewrite for /login/oauth2/');

    const insecure = withHost('tenantflow-api.onrender.com');
    insecure.rewrites[0] = { ...insecure.rewrites[0], destination: 'http://tenantflow-api.onrender.com/api/:path*' };
    expect(checkVercelConfig(insecure, {}).join(' ')).toContain('must use https');
  });
});
