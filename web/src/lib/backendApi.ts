// REST client for onsite-backend, mirroring the Android client's BackendApi.kt endpoint for
// endpoint. Every call needs a valid Supabase access token (refreshed here if it's close to
// expiring) and, once an active account is chosen, an X-Account-Id header telling the
// backend which account (personal or a joined team account) to act against.
import { loadSession, updateTokens } from "./sessionStore";
import { refreshSession } from "./supabaseAuth";
import type {
  AccountMembershipDto,
  ClientRequest,
  ConnectionDto,
  ConnectionInviteDto,
  ConnectionPlannedJobRequest,
  ConnectionSessionDto,
  InviteDto,
  InvoiceRequest,
  JobTypeRequest,
  MeResponse,
  MemberDto,
  PlannedJobRequest,
  SiteRequest,
  TrackingSessionRequest,
} from "./types";

const BASE_URL = import.meta.env.VITE_BACKEND_URL ?? "http://localhost:8080";

export class ApiError extends Error {
  status: number;
  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

/** Which account the caller is acting as; kept in sync with the stored session by AuthContext. */
let activeAccountId: string | null = null;
export function setActiveAccountId(accountId: string | null): void {
  activeAccountId = accountId;
}

/** Returns a token guaranteed to be valid for at least a few more seconds, refreshing if needed. */
async function validToken(): Promise<string> {
  const session = loadSession();
  if (!session) throw new ApiError(401, "not_signed_in");
  const expiringSoon = session.expiresAtMillis - Date.now() < 30_000;
  if (expiringSoon && session.refreshToken) {
    const refreshed = await refreshSession(session.refreshToken);
    updateTokens(refreshed.accessToken, refreshed.refreshToken, refreshed.expiresAtMillis);
    return refreshed.accessToken;
  }
  return session.accessToken;
}

/** Signs a request with a caller-supplied token, used only during login before a session exists. */
async function rawRequest<T>(token: string, method: string, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = { Authorization: `Bearer ${token}` };
  if (activeAccountId) headers["X-Account-Id"] = activeAccountId;
  if (body !== undefined) headers["Content-Type"] = "application/json";
  const response = await fetch(`${BASE_URL}${path}`, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  if (!response.ok) {
    const text = await response.text().catch(() => "");
    throw new ApiError(response.status, text || response.statusText);
  }
  if (response.status === 204) return undefined as T;
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const token = await validToken();
  return rawRequest<T>(token, method, path, body);
}

export const backendApi = {
  // -- identity ---------------------------------------------------------
  bootstrap: (token: string) => rawRequest<MeResponse>(token, "POST", "/v1/me/bootstrap"),
  me: () => request<MeResponse>("GET", "/v1/me"),

  // -- invites (team accounts) -------------------------------------------
  createInvite: (accountId: string, email: string) =>
    request<InviteDto>("POST", `/v1/accounts/${accountId}/invites`, { email }),
  listMyInvites: () => request<InviteDto[]>("GET", "/v1/me/invites"),
  acceptInvite: (inviteId: string) => request<InviteDto>("POST", `/v1/me/invites/${inviteId}/accept`),

  // -- connections (subcontractor <-> client bridge) ----------------------
  createConnectionInvite: (accountId: string, email: string) =>
    request<ConnectionInviteDto>("POST", `/v1/accounts/${accountId}/connection-invites`, { email }),
  listMyConnectionInvites: () => request<ConnectionInviteDto[]>("GET", "/v1/me/connection-invites"),
  acceptConnectionInvite: (inviteId: string, clientId: string) =>
    request<ConnectionDto>("POST", `/v1/me/connection-invites/${inviteId}/accept`, { clientId }),
  listMyConnections: () => request<ConnectionDto[]>("GET", "/v1/me/connections"),
  listEmployerConnections: () => request<ConnectionDto[]>("GET", "/v1/connections"),
  revokeConnection: (connectionId: string) => request<void>("DELETE", `/v1/connections/${connectionId}`),
  listAccountMembers: () => request<MemberDto[]>("GET", "/v1/me/account-members"),
  createConnectionPlannedJob: (connectionId: string, req: ConnectionPlannedJobRequest) =>
    request<void>("POST", `/v1/connections/${connectionId}/planned-jobs`, req),
  listConnectionSessions: (connectionId: string) =>
    request<ConnectionSessionDto[]>("GET", `/v1/connections/${connectionId}/sessions`),

  // -- clients ----------------------------------------------------------
  listClients: () => request<ClientRequest[]>("GET", "/v1/clients"),
  createClient: (req: ClientRequest) => request<void>("POST", "/v1/clients", req),
  updateClient: (id: string, req: ClientRequest) => request<void>("PUT", `/v1/clients/${id}`, req),
  deleteClient: (id: string) => request<void>("DELETE", `/v1/clients/${id}`),

  // -- sites ----------------------------------------------------------------
  listSites: () => request<SiteRequest[]>("GET", "/v1/sites"),
  createSite: (req: SiteRequest) => request<void>("POST", "/v1/sites", req),
  updateSite: (id: string, req: SiteRequest) => request<void>("PUT", `/v1/sites/${id}`, req),
  deleteSite: (id: string) => request<void>("DELETE", `/v1/sites/${id}`),

  // -- job types --------------------------------------------------------------
  listJobTypes: () => request<JobTypeRequest[]>("GET", "/v1/job-types"),
  createJobType: (req: JobTypeRequest) => request<void>("POST", "/v1/job-types", req),
  updateJobType: (id: string, req: JobTypeRequest) => request<void>("PUT", `/v1/job-types/${id}`, req),
  deleteJobType: (id: string) => request<void>("DELETE", `/v1/job-types/${id}`),

  // -- invoices -----------------------------------------------------------------
  listInvoices: () => request<InvoiceRequest[]>("GET", "/v1/invoices"),
  createInvoice: (req: InvoiceRequest) => request<void>("POST", "/v1/invoices", req),
  updateInvoice: (id: string, req: InvoiceRequest) => request<void>("PUT", `/v1/invoices/${id}`, req),

  // -- tracking sessions -----------------------------------------------------
  listTrackingSessions: () => request<TrackingSessionRequest[]>("GET", "/v1/sessions"),
  createTrackingSession: (req: TrackingSessionRequest) => request<void>("POST", "/v1/sessions", req),
  updateTrackingSession: (id: string, req: TrackingSessionRequest) => request<void>("PUT", `/v1/sessions/${id}`, req),
  deleteTrackingSession: (id: string) => request<void>("DELETE", `/v1/sessions/${id}`),

  // -- planned jobs -----------------------------------------------------------
  listPlannedJobs: () => request<PlannedJobRequest[]>("GET", "/v1/planned-jobs"),
  createPlannedJob: (req: PlannedJobRequest) => request<void>("POST", "/v1/planned-jobs", req),
  updatePlannedJob: (id: string, req: PlannedJobRequest) => request<void>("PUT", `/v1/planned-jobs/${id}`, req),
  deletePlannedJob: (id: string) => request<void>("DELETE", `/v1/planned-jobs/${id}`),
};

export type { AccountMembershipDto };
