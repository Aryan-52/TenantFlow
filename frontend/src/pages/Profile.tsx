import { useAuth } from '../contexts/AuthContext';
import { User, Mail, Hash } from 'lucide-react';

const Profile = () => {
  const { user } = useAuth();

  if (!user) return null;

  return (
    <div className="max-w-2xl mx-auto">
      <h1 className="mb-6">Your Profile</h1>
      
      <div className="card">
        <div className="flex items-center gap-6 mb-8 pb-8 border-b" style={{ borderBottom: '1px solid var(--border-color)' }}>
          <div style={{ width: 80, height: 80, borderRadius: '50%', backgroundColor: 'var(--primary-light)', color: 'var(--primary-color)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 'bold', fontSize: '2.5rem' }}>
            {user.name.charAt(0).toUpperCase()}
          </div>
          <div>
            <h2 className="mb-1">{user.name}</h2>
            <p className="text-muted">{user.email}</p>
          </div>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <div>
            <p className="text-muted text-sm font-medium mb-2 flex items-center gap-2"><User size={16} /> Full Name</p>
            <p className="font-medium">{user.name}</p>
          </div>
          <div>
            <p className="text-muted text-sm font-medium mb-2 flex items-center gap-2"><Mail size={16} /> Email Address</p>
            <p className="font-medium">{user.email}</p>
          </div>
          <div className="md:col-span-2">
            <p className="text-muted text-sm font-medium mb-2 flex items-center gap-2"><Hash size={16} /> Account ID</p>
            <p className="font-medium font-mono text-sm" style={{ backgroundColor: 'var(--bg-subtle)', padding: '0.5rem', borderRadius: 'var(--radius-md)', display: 'inline-block' }}>
              {user.id}
            </p>
          </div>
        </div>
      </div>
    </div>
  );
};

export default Profile;
