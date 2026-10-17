import { act, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { StartupSplash, useHoldSplash } from './StartupSplash';

const Holder = () => {
  useHoldSplash();
  return null;
};
const splash = () => document.querySelector('.startup-splash');
/** Small steps, so timers that React effects schedule along the way also run. */
const advance = async (ms: number) => {
  for (let t = 0; t < ms; t += 50) {
    await act(async () => {
      vi.advanceTimersByTime(50);
    });
  }
};

describe('StartupSplash', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'requestAnimationFrame', 'cancelAnimationFrame', 'performance'] });
    sessionStorage.clear();
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it('stays for the minimum time on a first entry even when the app is ready at once', async () => {
    render(<StartupSplash authLoading={false} signedIn />);
    await advance(2000);
    expect(splash()).toBeTruthy();
    await advance(1000); // past 2.6 s, then one frame and the fade
    await advance(400);
    expect(splash()).toBeNull();
    expect(sessionStorage.getItem('tf.entered')).toBe('1');
  });

  it('waits for the session and for anything holding it, then completes its progress', async () => {
    const { rerender } = render(<><StartupSplash authLoading signedIn={false} /><Holder /></>);
    expect(screen.getByText('Restoring your session…')).toBeTruthy();
    await advance(3000);
    expect(splash()).toBeTruthy();

    rerender(<><StartupSplash authLoading={false} signedIn /><Holder /></>);
    expect(screen.getByText('Loading your workspace…')).toBeTruthy();
    await advance(1000);
    expect(splash()).toBeTruthy();

    rerender(<StartupSplash authLoading={false} signedIn />);
    expect(document.querySelector('.boot-splash.is-done')).toBeTruthy();
    await advance(400);
    expect(splash()).toBeNull();
  });

  it('says loading is slow (with a reload) instead of spinning forever', async () => {
    render(<StartupSplash authLoading signedIn={false} />);
    await advance(9000);
    expect(screen.queryByText(/taking longer than usual/)).toBeNull();
    await advance(1500);
    expect(screen.getByText(/taking longer than usual/)).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Reload' })).toBeTruthy();
  });
});
