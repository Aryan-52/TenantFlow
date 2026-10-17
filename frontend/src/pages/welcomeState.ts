const KEY = 'tf.welcomeSeen';

/** The introduction is shown once; afterwards signed-out visitors go straight to sign-in. */
export const hasSeenWelcome = (): boolean => {
  try {
    return localStorage.getItem(KEY) === '1';
  } catch {
    return true; // No storage: never trap anyone on the intro.
  }
};

export const markWelcomeSeen = () => {
  try {
    localStorage.setItem(KEY, '1');
  } catch {
    // Ignore: the intro may show again next time.
  }
};
