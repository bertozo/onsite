import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { backendApi, setActiveAccountId as setApiActiveAccountId } from "../lib/backendApi";
import { mapAuthError } from "../lib/authError";
import { log } from "../lib/log";
import * as sessionStore from "../lib/sessionStore";
import * as supabaseAuth from "../lib/supabaseAuth";
import { SupabaseAuthError } from "../lib/supabaseAuth";
import type { AccountMembershipDto, MeResponse } from "../lib/types";

export type AuthState =
  | { status: "checking" }
  | { status: "loggedOut"; error?: string; info?: string }
  | { status: "loggingIn" }
  | {
      status: "loggedIn";
      email: string;
      activeAccountId: string;
      activeAccountName: string;
      activeRole: "OWNER" | "WORKER";
      memberships: AccountMembershipDto[];
    };

export type PasswordResetState =
  | { step: "hidden" }
  | { step: "email"; loading?: boolean; error?: string }
  | { step: "code"; email: string; loading?: boolean; error?: string };

interface AuthContextValue {
  state: AuthState;
  passwordReset: PasswordResetState;
  signIn: (email: string, password: string) => Promise<void>;
  signUp: (email: string, password: string) => Promise<void>;
  devSignIn: (email: string) => Promise<void>;
  openPasswordReset: () => void;
  dismissPasswordReset: () => void;
  sendPasswordResetCode: (email: string) => Promise<void>;
  confirmPasswordReset: (code: string, newPassword: string) => Promise<void>;
  switchAccount: (accountId: string) => void;
  refreshMe: () => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

/**
 * The account created for this user at signup, as opposed to one they later joined by
 * invite. Every account has the same "PERSONAL" accountKind, so the one reliable signal is
 * that an account's name is set to its creator's email at creation time.
 */
function mine(memberships: AccountMembershipDto[], email: string): AccountMembershipDto {
  return memberships.find((m) => m.accountName === email) ?? memberships[0];
}

function loggedInFrom(me: MeResponse, activeAccountId: string | null): AuthState {
  const active = (activeAccountId && me.memberships.find((m) => m.accountId === activeAccountId)) || mine(me.memberships, me.email);
  return {
    status: "loggedIn",
    email: me.email,
    activeAccountId: active.accountId,
    activeAccountName: active.accountName,
    activeRole: active.role,
    memberships: me.memberships,
  };
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({ status: "checking" });
  const [passwordReset, setPasswordReset] = useState<PasswordResetState>({ step: "hidden" });

  useEffect(() => {
    const stored = sessionStore.loadSession();
    if (!stored) {
      setState({ status: "loggedOut" });
      return;
    }
    setApiActiveAccountId(stored.activeAccountId);
    backendApi
      .me()
      .then((me) => setState(loggedInFrom(me, stored.activeAccountId)))
      .catch((e) => {
        // The one branch behind "it logged me out on refresh": an expired token and a
        // backend that is simply down look identical to the user, and used to look
        // identical in the console too.
        log.warn("session could not be restored, signing out", e);
        setState({ status: "loggedOut" });
      });
  }, []);

  const completeLogin = useCallback(async (session: supabaseAuth.SupabaseSession) => {
    const me = await backendApi.bootstrap(session.accessToken);
    const personal = mine(me.memberships, me.email);
    sessionStore.saveSession({
      accessToken: session.accessToken,
      refreshToken: session.refreshToken,
      expiresAtMillis: session.expiresAtMillis,
      email: me.email,
      accountId: personal.accountId,
      activeAccountId: null,
    });
    setApiActiveAccountId(null);
    setState(loggedInFrom(me, null));
  }, []);

  const signIn = useCallback(
    async (email: string, password: string) => {
      const trimmed = email.trim();
      if (!trimmed.includes("@")) {
        setState({ status: "loggedOut", error: "Digite um e-mail válido." });
        return;
      }
      if (!password) {
        setState({ status: "loggedOut", error: "Digite sua senha." });
        return;
      }
      setState({ status: "loggingIn" });
      try {
        const session = await supabaseAuth.signIn(trimmed, password);
        await completeLogin(session);
      } catch (e) {
        // Never the email or the password - only why it was refused.
        log.warn("sign-in failed", e);
        setState({ status: "loggedOut", error: mapAuthError(e) });
      }
    },
    [completeLogin],
  );

  const signUp = useCallback(
    async (email: string, password: string) => {
      const trimmed = email.trim();
      if (!trimmed.includes("@")) {
        setState({ status: "loggedOut", error: "Digite um e-mail válido." });
        return;
      }
      if (password.length < 6) {
        setState({ status: "loggedOut", error: "A senha precisa ter pelo menos 6 caracteres." });
        return;
      }
      setState({ status: "loggingIn" });
      try {
        const result = await supabaseAuth.signUp(trimmed, password);
        if (result.kind === "signedIn") {
          await completeLogin(result.session);
        } else {
          setState({ status: "loggedOut", info: "Enviamos um e-mail de confirmação. Confirme para entrar." });
        }
      } catch (e) {
        log.warn("sign-up failed", e);
        setState({ status: "loggedOut", error: mapAuthError(e) });
      }
    },
    [completeLogin],
  );

  /** Convenience for local development against the backend's DEV_AUTH_ENABLED endpoint. */
  const devSignIn = useCallback(
    async (email: string) => {
      const trimmed = email.trim();
      if (!trimmed.includes("@")) {
        setState({ status: "loggedOut", error: "Digite um e-mail válido." });
        return;
      }
      setState({ status: "loggingIn" });
      try {
        const base = import.meta.env.VITE_BACKEND_URL ?? "http://localhost:8080";
        const response = await fetch(`${base}/v1/dev/auth/login`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ email: trimmed }),
        });
        if (!response.ok) throw new Error(`dev login failed with HTTP ${response.status}`);
        const { token } = (await response.json()) as { token: string };
        await completeLogin({ accessToken: token, refreshToken: null, expiresAtMillis: Date.now() + 23 * 3600 * 1000 });
      } catch (e) {
        log.warn("dev login failed", e);
        setState({ status: "loggedOut", error: "Login de teste indisponível (backend local rodando com DEV_AUTH_ENABLED?)." });
      }
    },
    [completeLogin],
  );

  const openPasswordReset = useCallback(() => setPasswordReset({ step: "email" }), []);
  const dismissPasswordReset = useCallback(() => setPasswordReset({ step: "hidden" }), []);

  // Moves on to the code step regardless of whether the email is registered, so this can't be
  // used to probe accounts - except when Supabase rejects the request for being rate limited,
  // which reveals nothing about the account and, left silent, sends the user to type in a code
  // from an email that was never actually sent (requesting a new code invalidates whatever
  // token they were already holding).
  const sendPasswordResetCode = useCallback(async (email: string) => {
    const trimmed = email.trim();
    if (!trimmed.includes("@")) {
      setPasswordReset({ step: "email", error: "Digite um e-mail válido." });
      return;
    }
    setPasswordReset({ step: "email", loading: true });
    try {
      await supabaseAuth.recover(trimmed);
    } catch (e) {
      log.warn("password recovery request failed", e);
      if (e instanceof SupabaseAuthError && e.code === "over_email_send_rate_limit") {
        setPasswordReset({ step: "email", error: mapAuthError(e) });
        return;
      }
    }
    setPasswordReset({ step: "code", email: trimmed });
  }, []);

  const confirmPasswordReset = useCallback(
    async (code: string, newPassword: string) => {
      const current = passwordReset;
      if (current.step !== "code") return;
      if (!code.trim()) {
        setPasswordReset({ ...current, error: "Digite o código recebido por e-mail." });
        return;
      }
      if (newPassword.length < 6) {
        setPasswordReset({ ...current, error: "A senha precisa ter pelo menos 6 caracteres." });
        return;
      }
      setPasswordReset({ ...current, loading: true, error: undefined });
      try {
        const session = await supabaseAuth.verifyRecovery(current.email, code.trim());
        await supabaseAuth.updatePassword(session.accessToken, newPassword);
        setPasswordReset({ step: "hidden" });
        await completeLogin(session);
      } catch (e) {
        setPasswordReset({ ...current, loading: false, error: mapAuthError(e, "Código inválido ou expirado.") });
      }
    },
    [passwordReset, completeLogin],
  );

  const switchAccount = useCallback(
    (accountId: string) => {
      if (state.status !== "loggedIn") return;
      const target = state.memberships.find((m) => m.accountId === accountId);
      if (!target) return;
      sessionStore.setActiveAccount(accountId);
      setApiActiveAccountId(accountId);
      setState({
        status: "loggedIn",
        email: state.email,
        activeAccountId: target.accountId,
        activeAccountName: target.accountName,
        activeRole: target.role,
        memberships: state.memberships,
      });
    },
    [state],
  );

  const refreshMe = useCallback(async () => {
    const stored = sessionStore.loadSession();
    if (!stored) return;
    const me = await backendApi.me();
    setState(loggedInFrom(me, stored.activeAccountId));
  }, []);

  const logout = useCallback(() => {
    sessionStore.clearSession();
    setApiActiveAccountId(null);
    setState({ status: "loggedOut" });
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      state,
      passwordReset,
      signIn,
      signUp,
      devSignIn,
      openPasswordReset,
      dismissPasswordReset,
      sendPasswordResetCode,
      confirmPasswordReset,
      switchAccount,
      refreshMe,
      logout,
    }),
    [state, passwordReset, signIn, signUp, devSignIn, openPasswordReset, dismissPasswordReset, sendPasswordResetCode, confirmPasswordReset, switchAccount, refreshMe, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}
