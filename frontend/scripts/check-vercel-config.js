// Checks frontend/vercel.json before every Vercel build ("vercel-build" in package.json), so a
// deployment fails loudly instead of shipping broken routing. Two supported setups:
//
//  1. Same-origin proxy (VITE_API_URL empty): Vercel forwards /api, /oauth2 and /login/oauth2 to
//     the Render backend, so cookies and Google sign-in stay on the frontend's domain.
//  2. Own API domain (VITE_API_URL=https://api.example.com): the app calls the backend directly;
//     the proxy rewrites are then unused.
//
// In both, every other path falls back to index.html (React Router deep links), and that
// fallback must never swallow backend or asset paths.
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

export const PLACEHOLDER = 'REPLACE-WITH-YOUR-RENDER-SERVICE';
/** Backend route families the browser reaches (see SecurityConfig and Spring's OAuth2 login). */
export const BACKEND_PREFIXES = ['/api/', '/oauth2/', '/login/oauth2/'];

/** Vercel source pattern -> RegExp, for the two forms used in vercel.json. */
export function sourceToRegExp(source) {
  if (source.endsWith('/:path*')) {
    const prefix = source.slice(0, -'/:path*'.length).replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    return new RegExp(`^${prefix}(?:/.*)?$`);
  }
  return new RegExp(`^${source}$`);
}

/** Returns a list of problems (empty = fine). Never reads or prints secret values. */
export function checkVercelConfig(config, env = {}) {
  const problems = [];
  const rewrites = Array.isArray(config.rewrites) ? config.rewrites : [];
  if (config.outputDirectory !== 'dist') problems.push('outputDirectory must be "dist" (Vite build output).');

  const fallbackIndex = rewrites.findIndex((r) => r.destination === '/index.html');
  if (fallbackIndex === -1) {
    problems.push('Missing the index.html fallback rewrite: deep links such as /tenants/<id>/dashboard would 404.');
  } else {
    if (fallbackIndex !== rewrites.length - 1) problems.push('The index.html fallback must be the last rewrite.');
    const fallback = sourceToRegExp(rewrites[fallbackIndex].source);
    for (const path of ['/api/auth/refresh', '/oauth2/authorization/google', '/login/oauth2/code/google', '/assets/index.js']) {
      if (fallback.test(path)) problems.push(`The index.html fallback also matches ${path}; it must exclude backend and asset paths.`);
    }
    for (const path of ['/', '/login', '/tenants/abc/dashboard', '/oauth-callback']) {
      if (!fallback.test(path)) problems.push(`The index.html fallback does not cover the app route ${path}.`);
    }
  }

  const apiUrl = (env.VITE_API_URL ?? '').trim();
  if (apiUrl) {
    // Own API domain: the browser talks to it directly.
    if (!/^https:\/\//.test(apiUrl)) problems.push('VITE_API_URL must be an https:// address in production.');
    if (/localhost|127\.0\.0\.1/.test(apiUrl)) problems.push('VITE_API_URL points at a local machine.');
    return problems;
  }

  // Same-origin proxy: every backend prefix must be forwarded, all to one https backend.
  const hosts = new Set();
  for (const prefix of BACKEND_PREFIXES) {
    const rule = rewrites.find((r) => r.source === `${prefix}:path*`);
    if (!rule) {
      problems.push(`Missing proxy rewrite for ${prefix}:path* (VITE_API_URL is empty, so the app calls this domain).`);
      continue;
    }
    let url;
    try {
      url = new URL(rule.destination.replace(':path*', 'x'));
    } catch {
      problems.push(`Proxy destination for ${prefix} is not a URL.`);
      continue;
    }
    if (url.protocol !== 'https:') problems.push(`Proxy destination for ${prefix} must use https.`);
    if (url.pathname !== `${prefix}x`) problems.push(`Proxy destination for ${prefix} must keep the same path (${prefix}:path*).`);
    if (rewrites.indexOf(rule) > fallbackIndex && fallbackIndex !== -1) problems.push(`Proxy rewrite for ${prefix} must come before the index.html fallback.`);
    hosts.add(url.host);
  }
  if (hosts.size > 1) problems.push('All proxy rewrites must point at the same backend host.');
  // URL hosts are lower-cased, so compare case-insensitively.
  if ([...hosts].some((h) => h.includes(PLACEHOLDER.toLowerCase()))) {
    problems.push(`Replace ${PLACEHOLDER}.onrender.com in frontend/vercel.json with your Render backend's host (or set VITE_API_URL for an own API domain).`);
  }
  return problems;
}

// CLI: node scripts/check-vercel-config.js
if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  const config = JSON.parse(readFileSync(new URL('../vercel.json', import.meta.url), 'utf8'));
  const problems = checkVercelConfig(config, process.env);
  if (problems.length) {
    console.error('vercel.json is not ready to deploy:');
    for (const p of problems) console.error(`  - ${p}`);
    process.exit(1);
  }
  console.log(`vercel.json OK (${process.env.VITE_API_URL ? 'own API domain' : 'same-origin proxy'}).`);
}
