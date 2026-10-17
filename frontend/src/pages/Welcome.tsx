import { useEffect } from 'react';
import { Link, Navigate } from 'react-router-dom';
import { ArrowRight, CalendarDays, Columns3, ShieldCheck, Users } from 'lucide-react';
import { useAuth } from '../contexts/AuthContext';
import { BrandMark } from '../components/layout/BrandMark';
import { WorkspaceIllustration } from '../components/illustrations/Illustrations';
import { markWelcomeSeen } from './welcomeState';

const FEATURES = [
  {
    icon: Columns3,
    title: 'Organize projects',
    text: 'Every initiative gets an overview, a task list, a board and a calendar.',
  },
  {
    icon: CalendarDays,
    title: 'Coordinate tasks',
    text: 'Owners, assignees, priorities and due dates keep work moving and visible.',
  },
  {
    icon: Users,
    title: 'Collaborate as a team',
    text: 'Invite members, share activity and keep each workspace separate.',
  },
];

/**
 * A short introduction for first-time, signed-out visitors. It never delays anyone:
 * signed-in users are sent straight to their workspace and it is shown only once.
 */
const Welcome = () => {
  const { user, loading } = useAuth();

  useEffect(() => {
    markWelcomeSeen();
  }, []);

  if (!loading && user) return <Navigate to="/" replace />;

  return (
    <div className="welcome">
      <header className="welcome-nav">
        <span className="auth-logo">
          <BrandMark size={30} />
          <span>TenantFlow</span>
        </span>
        <nav className="flex items-center gap-2" aria-label="Account">
          <Link to="/login" className="btn btn-ghost">Sign in</Link>
          <Link to="/register" className="btn btn-primary">Get started</Link>
        </nav>
      </header>

      <main className="welcome-main">
        <section className="welcome-hero">
          <div className="welcome-copy">
            <span className="welcome-kicker">
              <ShieldCheck size={14} aria-hidden="true" /> Workspaces for focused teams
            </span>
            <h1 className="welcome-title">Plan the work. See the progress. Ship together.</h1>
            <p className="welcome-lead">
              TenantFlow brings your projects, tasks and team into one calm workspace, so everyone knows what
              matters today and what comes next.
            </p>
            <div className="welcome-actions">
              <Link to="/register" className="btn btn-primary btn-lg">
                Get started <ArrowRight size={16} aria-hidden="true" />
              </Link>
              <Link to="/login" className="btn btn-secondary btn-lg">I already have an account</Link>
            </div>
          </div>
          <div className="welcome-visual" aria-hidden="true">
            <WorkspaceIllustration className="welcome-illustration" />
          </div>
        </section>

        <section className="welcome-features" aria-label="What you can do">
          {FEATURES.map(({ icon: Icon, title, text }) => (
            <div key={title} className="welcome-feature">
              <span className="welcome-feature-icon"><Icon size={18} aria-hidden="true" /></span>
              <h2 className="welcome-feature-title">{title}</h2>
              <p className="welcome-feature-text">{text}</p>
            </div>
          ))}
        </section>
      </main>
    </div>
  );
};

export default Welcome;
