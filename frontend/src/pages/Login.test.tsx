import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import Login from './Login';

vi.mock('../contexts/AuthContext', () => ({ useAuth: () => ({ login: vi.fn(), user: null }) }));
vi.mock('../api/client', () => ({
  default: { get: vi.fn(() => Promise.resolve({ data: { google: false } })), post: vi.fn() },
}));

describe('Sign in', () => {
  it('shows a usable form next to a purely decorative 3D scene', async () => {
    const user = userEvent.setup();
    const { container } = render(<MemoryRouter><Login /></MemoryRouter>);
    const scene = container.querySelector('.auth-scene');
    expect(scene).toBeTruthy();
    // The scene is decoration only: hidden from assistive technology, with nothing focusable inside.
    expect(scene!.closest('[aria-hidden="true"]')).toBeTruthy();
    expect(scene!.querySelectorAll('a, button, input, [tabindex]')).toHaveLength(0);

    // The form is ready immediately.
    const email = screen.getByRole('textbox', { name: /email/i });
    await user.type(email, 'ada@example.test');
    expect((email as HTMLInputElement).value).toBe('ada@example.test');
    expect(screen.getByRole('button', { name: 'Sign in' })).toBeTruthy();
  });
});
