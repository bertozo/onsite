// The invoice-header details (business name/ABN/contact/bank account). Mirrors the Android
// app's Profile: the backend keeps one row per user (/v1/me/profile) and both apps reconcile
// against it by last-write-wins on updatedAtMillis, the client's own edit time. localStorage
// is only a per-browser cache so the invoice header renders instantly and offline.
import { backendApi } from "./backendApi";
import type { ProfileDto } from "./types";

const KEY = "onsite_profile";

export interface BusinessProfile {
  name: string;
  role: string;
  phone: string;
  email: string;
  abn: string;
  bankBsb: string;
  bankAccount: string;
  updatedAtMillis: number;
}

export const EMPTY_PROFILE: BusinessProfile = {
  name: "",
  role: "",
  phone: "",
  email: "",
  abn: "",
  bankBsb: "",
  bankAccount: "",
  updatedAtMillis: 0,
};

export function loadProfile(): BusinessProfile {
  let raw: string | null = null;
  try {
    raw = localStorage.getItem(KEY);
  } catch {
    return EMPTY_PROFILE;
  }
  if (!raw) return EMPTY_PROFILE;
  try {
    const parsed = JSON.parse(raw) as Partial<BusinessProfile> & { bankDetails?: string };
    // Before sync existed the bank details were one free-text field; keep that text visible.
    const bankAccount = parsed.bankAccount ?? parsed.bankDetails ?? "";
    return { ...EMPTY_PROFILE, ...parsed, bankAccount };
  } catch {
    return EMPTY_PROFILE;
  }
}

function cache(profile: BusinessProfile): void {
  try {
    localStorage.setItem(KEY, JSON.stringify(profile));
  } catch {
    // Private window or blocked storage: the server copy is the source of truth anyway.
  }
}

function toDto(p: BusinessProfile): ProfileDto {
  const orNull = (v: string) => (v.trim() ? v.trim() : null);
  return {
    name: p.name.trim(),
    role: orNull(p.role),
    phone: orNull(p.phone),
    email: orNull(p.email),
    abn: orNull(p.abn),
    bankBsb: orNull(p.bankBsb),
    bankAccount: orNull(p.bankAccount),
    updatedAtMillis: p.updatedAtMillis,
  };
}

function fromDto(d: ProfileDto): BusinessProfile {
  return {
    name: d.name,
    role: d.role ?? "",
    phone: d.phone ?? "",
    email: d.email ?? "",
    abn: d.abn ?? "",
    bankBsb: d.bankBsb ?? "",
    bankAccount: d.bankAccount ?? "",
    updatedAtMillis: d.updatedAtMillis,
  };
}

/**
 * Reconciles the cached copy with the server's and returns the winner. A cache saved before
 * sync existed (updatedAtMillis 0) only gets uploaded if the server has nothing yet, so a
 * fresh profile from the phone isn't clobbered by an old browser one. Offline, or when the
 * request fails for any reason, the cache is returned as is.
 */
export async function syncProfile(): Promise<BusinessProfile> {
  const local = loadProfile();
  let remote: ProfileDto | null;
  try {
    remote = await backendApi.getProfile();
  } catch {
    return local;
  }
  const hasLocal = local.name.trim() !== "";
  if (!hasLocal && !remote) return local;
  if (!remote || (hasLocal && local.updatedAtMillis > remote.updatedAtMillis)) {
    const upload = { ...local, updatedAtMillis: local.updatedAtMillis || Date.now() };
    try {
      const winner = fromDto(await backendApi.putProfile(toDto(upload)));
      cache(winner);
      return winner;
    } catch {
      return local;
    }
  }
  if (!hasLocal || remote.updatedAtMillis > local.updatedAtMillis) {
    const winner = fromDto(remote);
    cache(winner);
    return winner;
  }
  return local;
}

/** Caches immediately, then pushes; resolves to whichever version the server kept. */
export async function saveProfile(profile: BusinessProfile): Promise<BusinessProfile> {
  const stamped = { ...profile, updatedAtMillis: Date.now() };
  cache(stamped);
  try {
    const winner = fromDto(await backendApi.putProfile(toDto(stamped)));
    cache(winner);
    return winner;
  } catch {
    return stamped;
  }
}
