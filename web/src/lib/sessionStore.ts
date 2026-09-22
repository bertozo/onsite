// localStorage equivalent of the Android client's SessionStore (EncryptedSharedPreferences
// on that platform; the browser has no equivalent secure-storage primitive available to a
// plain SPA, so this is the standard tradeoff for a token kept client-side).
import { log } from "./log";

const KEY = "onsite_session";

export interface StoredSession {
  accessToken: string;
  refreshToken: string | null;
  expiresAtMillis: number;
  email: string;
  /** The personal account created for this user at signup. */
  accountId: string;
  /** Which account the app is currently acting as; null means "my own account". */
  activeAccountId: string | null;
}

export function loadSession(): StoredSession | null {
  const raw = localStorage.getItem(KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as StoredSession;
  } catch (e) {
    // A corrupted session reads as "never signed in", which is the one silent logout a
    // user cannot explain - so it says so here.
    log.warn("stored session could not be parsed, treating it as signed out", e);
    return null;
  }
}

export function saveSession(session: StoredSession): void {
  localStorage.setItem(KEY, JSON.stringify(session));
}

export function setActiveAccount(accountId: string | null): void {
  const current = loadSession();
  if (!current) return;
  saveSession({ ...current, activeAccountId: accountId });
}

export function updateTokens(accessToken: string, refreshToken: string | null, expiresAtMillis: number): void {
  const current = loadSession();
  if (!current) return;
  saveSession({ ...current, accessToken, refreshToken, expiresAtMillis });
}

export function clearSession(): void {
  localStorage.removeItem(KEY);
}
