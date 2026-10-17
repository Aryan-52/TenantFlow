import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { CalendarDays, Columns3, Users } from 'lucide-react';
import { BrandMark } from './BrandMark';
import { AuthScene } from './AuthScene';

interface AuthLayoutProps {
  title: string;
  subtitle?: ReactNode;
  children: ReactNode;
  /** Shown under the form (e.g. "Don't have an account? Sign up"). */
  footer?: ReactNode;
}

const POINTS = [
  { icon: Columns3, text: 'Plan projects on boards, lists and calendars' },
  { icon: CalendarDays, text: 'See what is due, overdue and coming up' },
  { icon: Users, text: 'Separate workspaces for every team' },
];

/**
 * Sign-in, registration and password recovery share one calm layout: the form on the left,
 * an isometric product scene on the right (a short banner above the form on small screens).
 * The form never waits for the decoration.
 */
export const AuthLayout = ({ title, subtitle, children, footer }: AuthLayoutProps) => (
  <div className="auth-split">
    <main className="auth-form-side">
      <Link to="/welcome" className="auth-logo" aria-label="About TenantFlow">
        <BrandMark size={30} />
        <span>TenantFlow</span>
      </Link>
      <div className="auth-form-wrap">
        <h1 className="auth-title">{title}</h1>
        {subtitle && <p className="auth-subtitle">{subtitle}</p>}
        <div className="auth-form-body">{children}</div>
        {footer && <div className="auth-footer">{footer}</div>}
      </div>
      <p className="auth-legal">© {new Date().getFullYear()} TenantFlow</p>
    </main>
    <aside className="auth-showcase" aria-hidden="true">
      <div className="auth-showcase-inner">
        <AuthScene />
        <div className="auth-showcase-copy">
          <p className="auth-showcase-title">Everything your team is working on, in one calm place.</p>
          <ul className="auth-points">
            {POINTS.map(({ icon: Icon, text }) => (
              <li key={text}>
                <span className="auth-point-icon"><Icon size={14} /></span>
                {text}
              </li>
            ))}
          </ul>
        </div>
      </div>
    </aside>
  </div>
);
