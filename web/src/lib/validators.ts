// Port of the Android app's Validators.kt so both clients accept and reject exactly the same
// input for the profile and client forms. Every rule treats a blank value as valid - the
// fields are optional; only non-empty input is checked. Bank/ABN rules follow the Australian
// formats the app is built around.

const ABN_WEIGHTS = [10, 1, 3, 5, 7, 9, 11, 13, 15, 17, 19];

const digitsOf = (value: string) => value.replace(/\D/g, "");

/** 11 digits (spaces allowed) passing the ATO modulus-89 check. */
export function isValidAbn(value: string): boolean {
  if (/[^\d\s]/.test(value)) return false;
  const digits = digitsOf(value);
  if (digits.length !== 11) return false;
  const sum = [...digits].reduce((acc, ch, index) => {
    const digit = Number(ch) - (index === 0 ? 1 : 0);
    return acc + digit * ABN_WEIGHTS[index];
  }, 0);
  return sum % 89 === 0;
}

/** 6 digits, optionally written as 123-456 or 123 456. */
export function isValidBsb(value: string): boolean {
  if (/[^\d\s-]/.test(value)) return false;
  return digitsOf(value).length === 6;
}

/** Australian account numbers are 6 to 10 digits. */
export function isValidAccountNumber(value: string): boolean {
  if (/[^\d\s]/.test(value)) return false;
  const length = digitsOf(value).length;
  return length >= 6 && length <= 10;
}

/** Optional leading "+", then 8-15 digits; spaces, dashes and parentheses are ignored. */
export function isValidPhone(value: string): boolean {
  const stripped = value.replace(/[\s\-()]/g, "");
  return /^\+?\d{8,15}$/.test(stripped);
}

export function isValidEmail(value: string): boolean {
  return /^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)+$/.test(value.trim());
}

/** Money typed by the user: digits with an optional "." or "," decimal part, never negative. */
export function parseAmount(value: string): number | null {
  const normalised = value.trim().replace(",", ".");
  if (!normalised || !/^\d+(\.\d{0,2})?$/.test(normalised)) return null;
  const parsed = Number(normalised);
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : null;
}

export function isValidAmount(value: string): boolean {
  return parseAmount(value) !== null;
}

// "Optional" variants: blank is fine, otherwise the rule applies.
export const abnOk = (value: string) => value.trim() === "" || isValidAbn(value);
export const bsbOk = (value: string) => value.trim() === "" || isValidBsb(value);
export const accountNumberOk = (value: string) => value.trim() === "" || isValidAccountNumber(value);
export const phoneOk = (value: string) => value.trim() === "" || isValidPhone(value);
export const emailOk = (value: string) => value.trim() === "" || isValidEmail(value);
export const amountOk = (value: string) => value.trim() === "" || isValidAmount(value);

/** Same wording as the Android app's error strings (values-pt). */
export const VALIDATION_MESSAGES = {
  abn: "Informe um ABN válido com 11 dígitos",
  bsb: "O BSB deve ter 6 dígitos (ex.: 062-000)",
  accountNumber: "O número da conta deve ter de 6 a 10 dígitos",
  phone: "Informe um telefone válido",
  email: "Informe um e-mail válido",
  amount: "Informe um valor válido",
} as const;

export interface ProfileFieldErrors {
  abn?: string;
  bsb?: string;
  accountNumber?: string;
  phone?: string;
  email?: string;
}

export function profileErrors(p: { abn: string; bankBsb: string; bankAccount: string; phone: string; email: string }): ProfileFieldErrors {
  const errors: ProfileFieldErrors = {};
  if (!abnOk(p.abn)) errors.abn = VALIDATION_MESSAGES.abn;
  if (!bsbOk(p.bankBsb)) errors.bsb = VALIDATION_MESSAGES.bsb;
  if (!accountNumberOk(p.bankAccount)) errors.accountNumber = VALIDATION_MESSAGES.accountNumber;
  if (!phoneOk(p.phone)) errors.phone = VALIDATION_MESSAGES.phone;
  if (!emailOk(p.email)) errors.email = VALIDATION_MESSAGES.email;
  return errors;
}

export interface ClientFieldErrors {
  abn?: string;
  phone?: string;
  email?: string;
  hourlyRate?: string;
}

export function clientErrors(c: { abn: string; phone: string; email: string; hourlyRate: string }): ClientFieldErrors {
  const errors: ClientFieldErrors = {};
  if (!abnOk(c.abn)) errors.abn = VALIDATION_MESSAGES.abn;
  if (!phoneOk(c.phone)) errors.phone = VALIDATION_MESSAGES.phone;
  if (!emailOk(c.email)) errors.email = VALIDATION_MESSAGES.email;
  if (!amountOk(c.hourlyRate)) errors.hourlyRate = VALIDATION_MESSAGES.amount;
  return errors;
}

export const hasErrors = (errors: object) => Object.keys(errors).length > 0;
