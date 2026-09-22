import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { clearSession, loadSession, saveSession } from "./sessionStore";

const refreshSession = vi.fn();
vi.mock("./supabaseAuth", () => ({ refreshSession: (token: string) => refreshSession(token) }));

// Imported after the mock so backendApi picks up the mocked supabaseAuth module.
const { backendApi, ApiError, setActiveAccountId } = await import("./backendApi");

function mockFetchOnce(body: unknown, status = 200, headers: Record<string, string> = {}) {
  return vi.fn().mockResolvedValue({
    ok: status >= 200 && status < 300,
    status,
    statusText: "",
    // Real responses always carry headers, and the client reads the echoed request id off
    // them - a bare object here would only be testing a mock the browser never produces.
    headers: new Headers(headers),
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

  it("surfaces a non-ok response as an ApiError carrying the backend's message", async () => {
    saveSession({ accessToken: "tok-1", refreshToken: null, expiresAtMillis: Date.now() + 3_600_000, email: "a@b.com", accountId: "acc1", activeAccountId: null });
    vi.stubGlobal("fetch", mockFetchOnce({ error: "nope", requestId: "req-1" }, 403));

    // The message is the body's `error`, not the raw JSON: useCrud shows it to the user.
    await expect(backendApi.listClients()).rejects.toMatchObject({ status: 403, message: "nope" });
  });
});

describe("backendApi request ids", () => {
  beforeEach(() => {
    saveSession({ accessToken: "tok-1", refreshToken: null, expiresAtMillis: Date.now() + 3_600_000, email: "a@b.com", accountId: "acc1", activeAccountId: null });
  });

  it("sends an X-Request-Id on every call", async () => {
    const fetchMock = mockFetchOnce([]);
    vi.stubGlobal("fetch", fetchMock);

    await backendApi.listClients();

    expect(fetchMock.mock.calls[0][1].headers["X-Request-Id"]).toMatch(/\S/);
  });

  it("prefers the id the backend echoed back, which is the one it logged", async () => {
    vi.stubGlobal("fetch", mockFetchOnce({ error: "nope" }, 500, { "X-Request-Id": "server-side-id" }));

    await expect(backendApi.listClients()).rejects.toMatchObject({ requestId: "server-side-id" });
  });

  it("falls back to the id it generated when the response echoes none", async () => {
    const fetchMock = mockFetchOnce({ error: "nope" }, 500);
    vi.stubGlobal("fetch", fetchMock);

    const error = await backendApi.listClients().catch((e: unknown) => e);

    expect((error as InstanceType<typeof ApiError>).requestId).toBe(fetchMock.mock.calls[0][1].headers["X-Request-Id"]);
  });
});
