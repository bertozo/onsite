import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { clearSession, loadSession, saveSession } from "./sessionStore";

const refreshSession = vi.fn();
vi.mock("./supabaseAuth", () => ({ refreshSession: (token: string) => refreshSession(token) }));

// Imported after the mock so backendApi picks up the mocked supabaseAuth module.
const { backendApi, ApiError, setActiveAccountId } = await import("./backendApi");

function mockFetchOnce(body: unknown, status = 200) {
  return vi.fn().mockResolvedValue({
    ok: status >= 200 && status < 300,
    status,
    statusText: "",
    text: async () => JSON.stringify(body),
  });
}

beforeEach(() => {
  setActiveAccountId(null);
});

afterEach(() => {
  clearSession();
  vi.restoreAllMocks();
  refreshSession.mockReset();
});

describe("backendApi request auth", () => {
  it("throws without hitting the network when there is no session", async () => {
    await expect(backendApi.listClients()).rejects.toBeInstanceOf(ApiError);
  });

  it("sends the stored token as a bearer header", async () => {
    saveSession({ accessToken: "tok-1", refreshToken: null, expiresAtMillis: Date.now() + 3_600_000, email: "a@b.com", accountId: "acc1", activeAccountId: null });
    const fetchMock = mockFetchOnce([]);
    vi.stubGlobal("fetch", fetchMock);

    await backendApi.listClients();

    const [, init] = fetchMock.mock.calls[0];
    expect(init.headers.Authorization).toBe("Bearer tok-1");
    expect(init.headers["X-Account-Id"]).toBeUndefined();
  });

  it("sends X-Account-Id once an active account is set", async () => {
    saveSession({ accessToken: "tok-1", refreshToken: null, expiresAtMillis: Date.now() + 3_600_000, email: "a@b.com", accountId: "acc1", activeAccountId: "acc2" });
    setActiveAccountId("acc2");
    const fetchMock = mockFetchOnce([]);
    vi.stubGlobal("fetch", fetchMock);

    await backendApi.listClients();

    expect(fetchMock.mock.calls[0][1].headers["X-Account-Id"]).toBe("acc2");
  });

  it("proactively refreshes a token that's about to expire, and persists the new one", async () => {
    saveSession({ accessToken: "old", refreshToken: "refresh-1", expiresAtMillis: Date.now() + 10_000, email: "a@b.com", accountId: "acc1", activeAccountId: null });
    refreshSession.mockResolvedValue({ accessToken: "new", refreshToken: "refresh-2", expiresAtMillis: Date.now() + 3_600_000 });
    const fetchMock = mockFetchOnce([]);
    vi.stubGlobal("fetch", fetchMock);

    await backendApi.listClients();

    expect(refreshSession).toHaveBeenCalledWith("refresh-1");
    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBe("Bearer new");
    expect(loadSession()?.accessToken).toBe("new");
    expect(loadSession()?.refreshToken).toBe("refresh-2");
  });

  it("surfaces a non-ok response as an ApiError", async () => {
    saveSession({ accessToken: "tok-1", refreshToken: null, expiresAtMillis: Date.now() + 3_600_000, email: "a@b.com", accountId: "acc1", activeAccountId: null });
    vi.stubGlobal("fetch", mockFetchOnce({ error: "nope" }, 403));

    await expect(backendApi.listClients()).rejects.toMatchObject({ status: 403 });
  });
});
