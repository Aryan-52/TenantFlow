import { Check, X } from 'lucide-react';
import { passwordChecks } from './passwordPolicy';

const LABELS: [keyof ReturnType<typeof passwordChecks>, string][] = [
  ['length', '8+ characters'],
  ['upper', 'Uppercase letter'],
  ['lower', 'Lowercase letter'],
  ['number', 'Number'],
  ['special', 'Special character'],
];

export const PasswordRequirements = ({ password }: { password: string }) => {
  if (!password) return null;
  const checks = passwordChecks(password);
  return (
    <ul className="password-reqs" aria-label="Password requirements">
      {LABELS.map(([key, text]) => (
        <li key={key} className={`password-req ${checks[key] ? 'is-met' : ''}`.trim()}>
          {checks[key] ? <Check size={13} aria-hidden="true" /> : <X size={13} aria-hidden="true" />}
          <span>{text}</span>
          <span className="sr-only">{checks[key] ? '(met)' : '(not met yet)'}</span>
        </li>
      ))}
    </ul>
  );
};
