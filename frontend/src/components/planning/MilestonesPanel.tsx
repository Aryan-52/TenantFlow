import { useState, type FormEvent } from 'react';
import { CheckCircle, Flag, Pencil, Plus, RotateCcw, Trash2 } from 'lucide-react';
import { milestoneApi, type Milestone } from '../../api/planning';
import { apiError } from '../../api/work';
import { useToast } from '../../contexts/ToastContext';
import { Button } from '../ui/Button';
import { Input } from '../ui/Input';
import { Modal } from '../ui/Modal';
import { ConfirmDialog } from '../ui/ConfirmDialog';
import { EmptyState } from '../ui/EmptyState';
import { formatDueDate, todayKey } from '../tasks/taskMeta';

interface MilestonesPanelProps {
  tenantId: string;
  projectId: string;
  milestones: Milestone[];
  onChange: (milestones: Milestone[]) => void;
  /** Deleting is for workspace admins; everyone else can plan and complete. */
  canDelete: boolean;
}

const sortMilestones = (list: Milestone[]) => [...list].sort((a, b) => a.targetDate.localeCompare(b.targetDate) || a.name.localeCompare(b.name));

/** A project's milestones: create, edit, complete or reopen, and delete. */
export const MilestonesPanel = ({ tenantId, projectId, milestones, onChange, canDelete }: MilestonesPanelProps) => {
  const toast = useToast();
  const [editing, setEditing] = useState<Milestone | 'new' | null>(null);
  const [deleting, setDeleting] = useState<Milestone | null>(null);
  const [form, setForm] = useState({ name: '', description: '', targetDate: '' });
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const today = todayKey();

  const open = (m: Milestone | 'new') => {
    setEditing(m);
    setError('');
    setForm(m === 'new' ? { name: '', description: '', targetDate: '' } : { name: m.name, description: m.description ?? '', targetDate: m.targetDate });
  };

  const upsert = (saved: Milestone) => onChange(sortMilestones([...milestones.filter((m) => m.id !== saved.id), saved]));

  const save = async (e: FormEvent) => {
    e.preventDefault();
    if (!form.name.trim() || !form.targetDate) {
      setError('Name and target date are required');
      return;
    }
    setBusy(true);
    try {
      const saved = editing === 'new'
        ? await milestoneApi.create(tenantId, projectId, form)
        : await milestoneApi.update(tenantId, projectId, (editing as Milestone).id, { ...form, status: (editing as Milestone).status, version: (editing as Milestone).version });
      upsert(saved);
      toast.success(editing === 'new' ? `Milestone "${saved.name}" added` : 'Milestone saved');
      setEditing(null);
    } catch (err) {
      setError(apiError(err, 'Could not save the milestone').message);
    } finally {
      setBusy(false);
    }
  };

  const setStatus = async (m: Milestone, status: Milestone['status']) => {
    try {
      upsert(await milestoneApi.update(tenantId, projectId, m.id, { name: m.name, description: m.description ?? '', targetDate: m.targetDate, status, version: m.version }));
      toast.success(status === 'DONE' ? `Completed "${m.name}"` : `Reopened "${m.name}"`);
    } catch (err) {
      toast.error(apiError(err, 'Could not update the milestone').message);
    }
  };

  const remove = async () => {
    if (!deleting) return;
    setBusy(true);
    try {
      await milestoneApi.remove(tenantId, projectId, deleting.id);
      onChange(milestones.filter((m) => m.id !== deleting.id));
      toast.success(`Deleted "${deleting.name}"`);
      setDeleting(null);
    } catch (err) {
      toast.error(apiError(err, 'Could not delete the milestone').message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <section aria-labelledby="milestones-title">
      <div className="panel-head">
        <h2 id="milestones-title" className="section-heading">Milestones</h2>
        <Button onClick={() => open('new')}><Plus size={15} /> Add milestone</Button>
      </div>

      {milestones.length === 0 ? (
        <EmptyState icon={<Flag size={22} />} title="No milestones yet" description="Milestones mark the dates that matter. Link tasks to them from the task details." />
      ) : (
        <ul className="milestone-list">
          {milestones.map((m) => {
            const overdue = m.status === 'OPEN' && m.targetDate < today;
            return (
              <li key={m.id} className={`milestone-item ${m.status === 'DONE' ? 'is-done' : ''} ${overdue ? 'is-overdue' : ''}`.trim()}>
                <span className="milestone-diamond" aria-hidden="true" />
                <div className="milestone-main">
                  <div className="milestone-name">{m.name}</div>
                  <div className="milestone-meta">
                    <span className={overdue ? 'text-danger' : ''}>{overdue ? 'Overdue · ' : ''}{formatDueDate(m.targetDate)}</span>
                    <span>{m.doneTasks}/{m.totalTasks} linked tasks done</span>
                    {m.completedAt && <span>Completed {new Date(m.completedAt).toLocaleDateString()}</span>}
                  </div>
                  {m.description && <p className="text-xs text-muted mt-1">{m.description}</p>}
                </div>
                {m.status === 'OPEN' ? (
                  <Button variant="secondary" size="sm" onClick={() => setStatus(m, 'DONE')}><CheckCircle size={14} /> Complete</Button>
                ) : (
                  <Button variant="ghost" size="sm" onClick={() => setStatus(m, 'OPEN')}><RotateCcw size={14} /> Reopen</Button>
                )}
                <Button variant="ghost" size="sm" className="btn-icon" onClick={() => open(m)} aria-label={`Edit ${m.name}`}><Pencil size={14} /></Button>
                {canDelete && (
                  <Button variant="ghost" size="sm" className="btn-icon text-danger" onClick={() => setDeleting(m)} aria-label={`Delete ${m.name}`}><Trash2 size={14} /></Button>
                )}
              </li>
            );
          })}
        </ul>
      )}

      <Modal isOpen={editing !== null} onClose={() => setEditing(null)} title={editing === 'new' ? 'Add milestone' : 'Edit milestone'}>
        <form id="milestone-form" onSubmit={save} noValidate>
          {error && <p className="text-sm text-danger mb-3" role="alert">{error}</p>}
          <Input label="Name" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} maxLength={160} required autoFocus />
          <Input label="Target date" type="date" value={form.targetDate} onChange={(e) => setForm({ ...form, targetDate: e.target.value })} required />
          <div className="form-group">
            <label htmlFor="milestone-desc">Description</label>
            <textarea id="milestone-desc" rows={3} maxLength={2000} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
          </div>
        </form>
        <div className="modal-actions">
          <Button variant="secondary" onClick={() => setEditing(null)} disabled={busy}>Cancel</Button>
          <Button type="submit" form="milestone-form" isLoading={busy}>Save</Button>
        </div>
      </Modal>

      <ConfirmDialog
        isOpen={!!deleting}
        title="Delete milestone"
        message={`Delete "${deleting?.name ?? ''}"? Its linked tasks stay; they only lose the link.`}
        confirmText="Delete milestone"
        isDestructive
        isLoading={busy}
        onConfirm={remove}
        onCancel={() => setDeleting(null)}
      />
    </section>
  );
};
