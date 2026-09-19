// The Android app's Profile (business name/ABN/contact, used only as the invoice PDF header)
// lives purely in local Room storage and is never synced to onsite-backend - there simply is
// no /v1/me/profile route. The web app mirrors that: kept in the browser's localStorage only,
// per browser/device, used only to fill the printable invoice header.
const KEY = "onsite_profile";

export interface BusinessProfile {
  name: string;
  abn: string;
  phone: string;
  email: string;
  bankDetails: string;
}

const EMPTY: BusinessProfile = { name: "", abn: "", phone: "", email: "", bankDetails: "" };

export function loadProfile(): BusinessProfile {
  const raw = localStorage.getItem(KEY);
  if (!raw) return EMPTY;
  try {
    return { ...EMPTY, ...JSON.parse(raw) };
  } catch {
    return EMPTY;
  }
}

export function saveProfile(profile: BusinessProfile): void {
  localStorage.setItem(KEY, JSON.stringify(profile));
}
