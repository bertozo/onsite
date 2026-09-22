// Console logging with levels, mirroring the Android app's log/AppLog.kt in spirit: the
// point is that a failure leaves a trace instead of a blank screen.
//
// warn/error stay on in production builds - they are what a user can screenshot and what
// the next person to open devtools needs - while debug is dev-only noise. Never log a
// token, a password or a bank detail (see docs/logging.md); the request id is what makes a
// line traceable, and it is not sensitive.
const prefix = "[onsite]";

const debugEnabled = import.meta.env.DEV;

export const log = {
  debug(message: string, ...rest: unknown[]): void {
    if (debugEnabled) console.debug(`${prefix} ${message}`, ...rest);
  },
  info(message: string, ...rest: unknown[]): void {
    console.info(`${prefix} ${message}`, ...rest);
  },
  /** Something failed and the app carried on - most of this app's failures are these. */
  warn(message: string, ...rest: unknown[]): void {
    console.warn(`${prefix} ${message}`, ...rest);
  },
  error(message: string, ...rest: unknown[]): void {
    console.error(`${prefix} ${message}`, ...rest);
  },
};

/**
 * For the `.catch(() => undefined)` shape that litters the screens: keeps the "load it if
 * you can, carry on if you can't" behaviour, but says so. Returns the fallback value.
 */
export function logAndFallback<T>(what: string, fallback: T): (error: unknown) => T {
  return (error: unknown) => {
    log.warn(`${what} failed`, error);
    return fallback;
  };
}
