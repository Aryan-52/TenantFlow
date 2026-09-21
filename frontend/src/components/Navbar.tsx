import { Link } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';

const Navbar = () => {
  const { user, logout } = useAuth();

  return (
    <nav className="navbar">
      <Link to="/" className="nav-brand">TenantFlow</Link>
      <div className="nav-links">
        <span className="text-muted">Hello, {user?.name}</span>
        <button className="btn" onClick={logout}>Sign Out</button>
      </div>
    </nav>
  );
};

export default Navbar;
