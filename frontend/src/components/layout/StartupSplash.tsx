import { useEffect, useLayoutEffect, useState, useSyncExternalStore, type CSSProperties } from 'react';
import { BrandMark } from './BrandMark';

/**
 * The start-up splash.
 *
 * index.html paints the first frame before any JavaScript arrives. <StartupSplash /> then takes
 * over with the same markup (styled in index.html) and stays until both
 *   - the app is ready: the session is restored and nothing is holding the splash
 *     (AppLoading holds it while the workspace loads), and
 *   - a minimum time has passed since the page started loading: about 2.6 s for the first
 *     entry in this browser tab, about 1.2 s for a reload of the same tab.
 * It never appears for navigation inside the app. After 10 s it says that loading is slow and
 * offers a reload; request timeouts (api/client.ts) guarantee that loading always finishes.
 */

/** First entry in this tab vs. a reload. Chosen in index.html (same rule) so CSS and JS agree. */
function splashMinimumMs(): number {
  const fromDocument = typeof document === 'undefined'
    ? NaN
    : parseInt(getComputedStyle(document.documentElement).getPropertyValue('--splash-ms'), 10);
  return Number.isFinite(fromDocument) && fromDocument > 0 ? fromDocument : FIRST_ENTRY_MS;
}
const FIRST_ENTRY_MS = 2600;
const SLOW_AFTER_MS = 10_000;
const FADE_MS = 280;
/** The tile animation's period (index.html, .boot-tile / .boot-mark). */
const ANIMATION_PERIOD_MS = 1800;

// ---- Holds: components that need the splash to stay (e.g. while the workspace loads) -------
let holds = 0;
const listeners = new Set<() => void>();
const notify = () => listeners.forEach((l) => l());
const subscribe = (l: () => void) => {
  listeners.add(l);
  return () => listeners.delete(l);
};
const getHolds = () => holds;

/** Keeps the start-up splash up while the calling component is mounted. */
export function useHoldSplash() {
  // Layout effect: registered before the browser paints, so the splash never lifts in between.
  useLayoutEffect(() => {
    holds += 1;
    notify();
    return () => {
      holds -= 1;
      notify();
    };
  }, []);
}

// ---- The splash itself ---------------------------------------------------------------------
interface SplashViewProps {
  label: string;
  /** The minimum time has passed and the app is ready: the bar completes. */
  done?: boolean;
  leaving?: boolean;
  slow?: boolean;
}

/** Splash markup shared by the overlay and AppLoading; animation phase follows page time. */
export const SplashView = ({ label, done = false, leaving = false, slow = false }: SplashViewProps) => {
  // Read once: the motion continues from where index.html's splash was instead of restarting.
  const [style] = useState(() => {
    const now = typeof performance === 'undefined' ? 0 : performance.now();
    return {
      '--phase': `${-Math.round(now % ANIMATION_PERIOD_MS)}ms`,
      '--progress-phase': `${-Math.round(now)}ms`,
    } as CSSProperties;
  });
  return (
    <div
      className={`boot-splash ${done ? 'is-done' : ''} ${leaving ? 'is-leaving' : ''}`.replace(/\s+/g, ' ').trim()}
      role="status"
      aria-live="polite"
      aria-busy={!done}
      style={style}
    >
      <div className="boot-stack" aria-hidden="true">
        <span className="boot-tile t1" />
        <span className="boot-tile t2" />
        <span className="boot-tile t3" />
        <BrandMark size={48} className="boot-mark" />
      </div>
      <div className="boot-name">TenantFlow</div>
      <div className="boot-progress" aria-hidden="true"><span /></div>
      <div className="boot-label">{label}</div>
      {slow && (
        <p className="boot-slow-react">
          This is taking longer than usual.{' '}
          <button type="button" onClick={() => window.location.reload()}>Reload</button>
        </p>
      )}
    </div>
  );
};

interface StartupSplashProps {
  /** Session restore still running (AuthContext). */
  authLoading: boolean;
  /** Labels the stage the app is in. */
  signedIn: boolean;
}

export const StartupSplash = ({ authLoading, signedIn }: StartupSplashProps) => {
  const holdCount = useSyncExternalStore(subscribe, getHolds, getHolds);
  const [minimumMs] = useState(splashMinimumMs);
  const [minimumPassed, setMinimumPassed] = useState(() => performance.now() >= minimumMs);
  const [slow, setSlow] = useState(false);
  const [phase, setPhase] = useState<'showing' | 'leaving' | 'gone'>('showing');
  const ready = !authLoading && holdCount === 0;

  useEffect(() => {
    const now = performance.now();
    const timers = [
      window.setTimeout(() => setMinimumPassed(true), Math.max(0, minimumMs - now)),
      window.setTimeout(() => setSlow(true), Math.max(0, SLOW_AFTER_MS - now)),
    ];
    return () => timers.forEach(clearTimeout);
  }, [minimumMs]);

  useEffect(() => {
    if (phase !== 'showing' || !ready || !minimumPassed) return;
    // A moment later, so the page underneath has rendered and the splash lifts onto a complete
    // screen. A timer, not requestAnimationFrame: frames are paused in background tabs, which
    // would keep the splash up there indefinitely.
    const timer = window.setTimeout(() => {
      setPhase('leaving');
      try {
        sessionStorage.setItem('tf.entered', '1');
      } catch {
        // Storage unavailable (private mode): every load counts as a first entry.
      }
    }, 50);
    return () => clearTimeout(timer);
  }, [phase, ready, minimumPassed]);

  useEffect(() => {
    if (phase !== 'leaving') return;
    const reduced = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
    const timer = window.setTimeout(() => setPhase('gone'), reduced ? 0 : FADE_MS);
    return () => clearTimeout(timer);
  }, [phase]);

  if (phase === 'gone') return null;
  const label = authLoading ? 'Restoring your session…' : holdCount > 0 ? 'Loading your workspace…' : signedIn ? 'Ready' : 'Welcome';
  return (
    <div className="startup-splash">
      <SplashView label={label} done={ready && minimumPassed} leaving={phase === 'leaving'} slow={slow && !ready} />
    </div>
  );
};
