import { afterEach, describe, expect, it } from "vitest";
import { clearSession, loadSession, saveSession, setActiveAccount, updateTokens } from "./sessionStore";

afterEach(() => clearSession());

describe("sessionStore", () => {
  it("returns null when nothing is stored", () => {
    expect(loadSession()).toBeNull();
  });

  it("round-trips a saved session", () => {
    saveSession({ accessToken: "t1", refreshToken: "r1", expiresAtMillis: 123, email: "a@b.com", accountId: "acc1", activeAccountId: null });
    expect(loadSession()).toEqual({ accessToken: "t1", refreshToken: "r1", expiresAtMillis: 123, email: "a@b.com", accountId: "acc1", activeAccountId: null });
  });

  it("setActiveAccount updates only that field", () => {
    saveSession({ accessToken: "t1", refreshToken: "r1", expiresAtMillis: 123, email: "a@b.com", accountId: "acc1", activeAccountId: null });
    setActiveAccount("acc2");
    expect(loadSession()?.activeAccountId).toBe("acc2");
    expect(loadSession()?.accountId).toBe("acc1");
  });

  it("updateTokens refreshes the token fields without touching identity", () => {
    saveSession({ accessToken: "t1", refreshToken: "r1", expiresAtMillis: 123, email: "a@b.com", accountId: "acc1", activeAccountId: "acc2" });
    updateTokens("t2", "r2", 456);
    expect(loadSession()).toEqual({ accessToken: "t2", refreshToken: "r2", expiresAtMillis: 456, email: "a@b.com", accountId: "acc1", activeAccountId: "acc2" });
  });

  it("is a no-op when there is no session to update", () => {
    setActiveAccount("acc2");
    updateTokens("t2", "r2", 456);
    expect(loadSession()).toBeNull();
  });
});
