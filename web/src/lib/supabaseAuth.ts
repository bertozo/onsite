// Talks directly to Supabase Auth's REST API (GoTrue) for email+password sign-up/sign-in
// and password recovery, exactly like the Android client
// (android/app/src/main/java/com/xbertz/onsite/backend/SupabaseAuth.kt). No Supabase SDK
// dependency: this app only ever needs the resulting access token, which it hands to the
// backend exactly like a dev-login token would be. Same project as the Android app so an
// account created on one client works on the other.
const SUPABASE_URL = "https://bbxciadhznsdpdnwdynw.supabase.co";
const SUPABASE_PUBLISHABLE_KEY = "sb_publishable_gNu70Q44fANX4LmU-1gThQ_a1u4-pAg";

export interface SupabaseSession {
  accessToken: string;
  refreshToken: string | null;
  /** Epoch millis when the access token expires; used to refresh proactively. */
  expiresAtMillis: number;
}

export type SignUpResult =
  | { kind: "signedIn"; session: SupabaseSession }
  | { kind: "confirmationEmailSent" };

export class SupabaseAuthError extends Error {
  code: string | null;
  constructor(code: string | null) {
    super(code ?? "unknown_error");
    this.code = code;
  }
}

interface SupabaseUserDto {
  id?: string;
  // Supabase's anti-enumeration behaviour: signing up with an email that's already
  // registered returns HTTP 200 with a look-alike user instead of an error, but its
  // "identities" come back empty (a real new user's does not).
  identities?: unknown[];
}

interface SupabaseSessionDto {
  access_token?: string;
  refresh_token?: string;
  expires_in?: number;
  user?: SupabaseUserDto;
}

interface SupabaseErrorDto {
  error_code?: string;
  error?: string;
  msg?: string;
  error_description?: string;
}

async function toAuthError(response: Response): Promise<SupabaseAuthError> {
  const body = (await response.json().catch(() => null)) as SupabaseErrorDto | null;
  return new SupabaseAuthError(body?.error_code ?? body?.error ?? null);
}

function toSession(dto: SupabaseSessionDto): SupabaseSession {
  if (!dto.access_token) throw new SupabaseAuthError(null);
  return {
    accessToken: dto.access_token,
    refreshToken: dto.refresh_token ?? null,
    expiresAtMillis: Date.now() + (dto.expires_in ?? 3600) * 1000,
  };
}

function headers(extra?: Record<string, string>): HeadersInit {
  return {
    "Content-Type": "application/json",
    apikey: SUPABASE_PUBLISHABLE_KEY,
    ...extra,
  };
}

export async function signUp(email: string, password: string): Promise<SignUpResult> {
  const response = await fetch(`${SUPABASE_URL}/auth/v1/signup`, {
    method: "POST",
    headers: headers(),
    body: JSON.stringify({ email, password }),
  });
  if (!response.ok) throw await toAuthError(response);
  const dto = (await response.json()) as SupabaseSessionDto;
  if (dto.access_token) return { kind: "signedIn", session: toSession(dto) };
  if (dto.user?.identities?.length === 0) throw new SupabaseAuthError("user_already_exists");
  return { kind: "confirmationEmailSent" };
}

export async function signIn(email: string, password: string): Promise<SupabaseSession> {
  const response = await fetch(`${SUPABASE_URL}/auth/v1/token?grant_type=password`, {
    method: "POST",
    headers: headers(),
    body: JSON.stringify({ email, password }),
  });
  if (!response.ok) throw await toAuthError(response);
  return toSession(await response.json());
}

export async function refreshSession(refreshToken: string): Promise<SupabaseSession> {
  const response = await fetch(`${SUPABASE_URL}/auth/v1/token?grant_type=refresh_token`, {
    method: "POST",
    headers: headers(),
    body: JSON.stringify({ refresh_token: refreshToken }),
  });
  if (!response.ok) throw await toAuthError(response);
  return toSession(await response.json());
}

/**
 * Sends the recovery email. The project's "Reset password" template shows the raw
 * {{ .Token }} as a 6-digit code instead of a link, so the rest of the flow
 * (verifyRecovery + updatePassword) never needs a redirect URL - the user types the code
 * back into the app.
 */
export async function recover(email: string): Promise<void> {
  const response = await fetch(`${SUPABASE_URL}/auth/v1/recover`, {
    method: "POST",
    headers: headers(),
    body: JSON.stringify({ email }),
  });
  if (!response.ok) throw await toAuthError(response);
}

/** Exchanges a recovery code for a session, proving the user controls that inbox. */
export async function verifyRecovery(email: string, code: string): Promise<SupabaseSession> {
  const response = await fetch(`${SUPABASE_URL}/auth/v1/verify`, {
    method: "POST",
    headers: headers(),
    body: JSON.stringify({ type: "recovery", email, token: code }),
  });
  if (!response.ok) throw await toAuthError(response);
  return toSession(await response.json());
}

/** Sets a new password on the session obtained from verifyRecovery. */
export async function updatePassword(accessToken: string, newPassword: string): Promise<void> {
  const response = await fetch(`${SUPABASE_URL}/auth/v1/user`, {
    method: "PUT",
    headers: headers({ Authorization: `Bearer ${accessToken}` }),
    body: JSON.stringify({ password: newPassword }),
  });
  if (!response.ok) throw await toAuthError(response);
}
