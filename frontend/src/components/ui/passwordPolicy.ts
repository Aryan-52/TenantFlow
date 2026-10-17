/** The backend's password policy, checked live while typing. */
export const passwordChecks = (password: string) => ({
  length: password.length >= 8,
  upper: /[A-Z]/.test(password),
  lower: /[a-z]/.test(password),
  number: /[0-9]/.test(password),
  special: /[^A-Za-z0-9]/.test(password),
});

export const meetsPasswordPolicy = (password: string) => Object.values(passwordChecks(password)).every(Boolean);
