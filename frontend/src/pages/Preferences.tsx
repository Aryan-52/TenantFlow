import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { Monitor, Moon, Sun } from 'lucide-react';
import { preferenceApi, type Preferences as Prefs } from '../api/workspace';
import { apiError } from '../api/work';
import { useTheme, type ThemePreference } from '../contexts/ThemeContext';
import { useToast } from '../contexts/ToastContext';
import { PageHeader } from '../components/ui/PageHeader';
import { Button } from '../components/ui/Button';
import { ErrorState } from '../components/ui/ErrorState';
import { Skeleton } from '../components/ui/Skeleton';

const THEMES: { value: ThemePreference; label: string; icon: typeof Sun }[] = [
  { value: 'light', label: 'Light', icon: Sun },
  { value: 'dark', label: 'Dark', icon: Moon },
  { value: 'system', label: 'Match system', icon: Monitor },
];

/** Personal settings: appearance, time zone and in-app reminders. */
const Preferences = () => {
  const { preference, setPreference } = useTheme();
  const toast = useToast();
  const [prefs, setPrefs] = useState<Prefs | null>(null);
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    preferenceApi.get().then(setPrefs).catch((err) => setError(apiError(err, 'Could not load your preferences.').message));
  }, []);

  const zones = useMemo(() => {
    const supported = (Intl as unknown as { supportedValuesOf?: (k: string) => string[] }).supportedValuesOf;
    return supported ? supported('timeZone') : [];
  }, []);
  const browserZone = Intl.DateTimeFormat().resolvedOptions().timeZone;

  const save = async (e: FormEvent) => {
    e.preventDefault();
    if (!prefs) return;
    setSaving(true);
    setError('');
    try {
      setPrefs(await preferenceApi.update(prefs));
      toast.success('Preferences saved');
    } catch (err) {
      setError(apiError(err, 'Could not save your preferences.').message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="max-w-3xl">
      <PageHeader title="Preferences" subtitle="Settings that apply to you in every workspace" />

      <section className="panel mb-6" aria-labelledby="pref-appearance">
        <h2 id="pref-appearance" className="section-heading mb-3">Appearance</h2>
        <div className="theme-picker" role="radiogroup" aria-label="Theme">
          {THEMES.map(({ value, label, icon: Icon }) => (
            <button key={value} type="button" role="radio" aria-checked={preference === value} className={preference === value ? 'active' : ''} onClick={() => setPreference(value)}>
              <Icon size={16} aria-hidden="true" /> {label}
            </button>
          ))}
        </div>
        <p className="text-xs text-muted mt-2">Saved in this browser.</p>
      </section>

      <ErrorState message={error} />
      {!prefs ? (
        !error && <Skeleton style={{ height: '14rem' }} />
      ) : (
        <form className="panel" onSubmit={save} aria-labelledby="pref-reminders">
          <h2 id="pref-reminders" className="section-heading mb-1">Reminders and notifications</h2>
          <p className="text-sm text-muted mb-4">In-app only. Each reminder is sent once per task and due date.</p>

          <div className="form-group">
            <label htmlFor="pref-zone">Time zone</label>
            <select
              id="pref-zone"
              value={prefs.timeZone ?? ''}
              onChange={(e) => setPrefs({ ...prefs, timeZone: e.target.value || null })}
            >
              <option value="">Server default ({prefs.effectiveTimeZone})</option>
              {zones.map((z) => <option key={z} value={z}>{z}</option>)}
            </select>
            <p className="field-hint">
              Decides when "today", "due soon" and "overdue" begin for you.
              {browserZone && prefs.timeZone !== browserZone && (
                <> <button type="button" className="link-button" onClick={() => setPrefs({ ...prefs, timeZone: browserZone })}>Use this device's zone ({browserZone})</button></>
              )}
            </p>
          </div>

          <fieldset className="pref-fieldset">
            <legend className="sr-only">Reminder types</legend>
            <label className="check-row">
              <input type="checkbox" checked={prefs.dueSoonEnabled} onChange={(e) => setPrefs({ ...prefs, dueSoonEnabled: e.target.checked })} />
              <span>
                Remind me before tasks are due
                <span className="check-row-hint">For tasks assigned to me.</span>
              </span>
            </label>
            {prefs.dueSoonEnabled && (
              <div className="form-group indent">
                <label htmlFor="pref-days">How early</label>
                <select id="pref-days" value={prefs.dueSoonDays} onChange={(e) => setPrefs({ ...prefs, dueSoonDays: Number(e.target.value) })}>
                  <option value={0}>On the due date</option>
                  <option value={1}>1 day before</option>
                  <option value={2}>2 days before</option>
                  <option value={3}>3 days before</option>
                  <option value={7}>1 week before</option>
                </select>
              </div>
            )}
            <label className="check-row">
              <input type="checkbox" checked={prefs.overdueEnabled} onChange={(e) => setPrefs({ ...prefs, overdueEnabled: e.target.checked })} />
              <span>
                Tell me when my tasks become overdue
                <span className="check-row-hint">Once per task and due date.</span>
              </span>
            </label>
            <label className="check-row">
              <input type="checkbox" checked={prefs.mentionsEnabled} onChange={(e) => setPrefs({ ...prefs, mentionsEnabled: e.target.checked })} />
              <span>
                Notify me when someone mentions me in a comment
                <span className="check-row-hint">Comments on tasks you are assigned to or created still notify you.</span>
              </span>
            </label>
          </fieldset>

          <div className="flex justify-end mt-4">
            <Button type="submit" isLoading={saving}>Save preferences</Button>
          </div>
        </form>
      )}
    </div>
  );
};

export default Preferences;
