// DTOs mirror onsite-backend's routes exactly, same shapes the Android client
// (android/app/src/main/java/com/xbertz/onsite/backend/BackendApi.kt) sends/receives.

export type Role = "OWNER" | "WORKER";
export type InviteStatus = "PENDING" | "ACCEPTED";
export type ConnectionStatus = "PENDING" | "ACTIVE" | "REVOKED";
export type InvoiceStatus = "DRAFT" | "SENT" | "PAID" | "VOID";

export interface AccountMembershipDto {
  accountId: string;
  accountName: string;
  accountKind: string;
  role: Role;
}

export interface MeResponse {
  userId: string;
  email: string;
  displayName: string | null;
  memberships: AccountMembershipDto[];
}

export interface InviteDto {
  id: string;
  accountId: string;
  accountName: string;
  email: string;
  role: Role;
  status: InviteStatus;
}

export interface ConnectionInviteDto {
  id: string;
  employerAccountId: string;
  employerAccountName: string;
  email: string;
  status: InviteStatus;
}

export interface ConnectionDto {
  id: string;
  employerAccountId: string;
  employerAccountName: string;
  workerAccountId: string;
  workerClientId: string;
  workerClientName: string;
  status: ConnectionStatus;
}

export interface MemberDto {
  userId: string;
  email: string;
  displayName: string | null;
  role: Role;
}

export interface ConnectionPlannedJobRequest {
  id: string;
  dateEpochDay: number;
  startMinute: number;
  endMinute?: number | null;
  siteLabel?: string | null;
  jobTypeLabel?: string | null;
  notes?: string | null;
}

export interface ConnectionSessionDto {
  id: string;
  siteLabel: string | null;
  jobTypeLabel: string | null;
  startTimestampMillis: number;
  stopTimestampMillis: number | null;
}

export interface ClientRequest {
  id: string;
  name: string;
  abn?: string | null;
  phone?: string | null;
  email?: string | null;
  createdAtMillis: number;
  hourlyRate?: number | null;
}

export interface SiteRequest {
  id: string;
  label: string;
  address?: string | null;
  latitude: number;
  longitude: number;
  createdAtMillis: number;
}

export interface JobTypeRequest {
  id: string;
  name: string;
  createdAtMillis: number;
}

export interface InvoiceRequest {
  id: string;
  number: string;
  clientName: string;
  periodStartEpochDay: number;
  periodEndEpochDay: number;
  issueDateEpochDay: number;
  totalHours: number;
  totalAmount?: number | null;
  hourlyRate?: number | null;
  status: InvoiceStatus;
  sentAtMillis?: number | null;
  paidAtMillis?: number | null;
  pdfPath?: string | null;
  notes?: string | null;
  createdAtMillis: number;
}

export interface TrackingSessionRequest {
  id: string;
  clientName?: string | null;
  siteLabel?: string | null;
  jobTypeLabel?: string | null;
  startTimestampMillis: number;
  startLatitude: number;
  startLongitude: number;
  stopTimestampMillis?: number | null;
  stopLatitude?: number | null;
  stopLongitude?: number | null;
  hourlyRate?: number | null;
  invoiceId?: string | null;
}

export interface PlannedJobRequest {
  id: string;
  dateEpochDay: number;
  startMinute: number;
  endMinute?: number | null;
  clientName?: string | null;
  siteLabel?: string | null;
  jobTypeLabel?: string | null;
  notes?: string | null;
  assignedUserId?: string | null;
}
