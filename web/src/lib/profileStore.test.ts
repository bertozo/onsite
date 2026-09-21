import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { ProfileDto } from "./types";

const getProfile = vi.fn<() => Promise<ProfileDto | null>>();
const putProfile = vi.fn<(p: ProfileDto) => Promise<ProfileDto>>();
vi.mock("./backendApi", () => ({
  backendApi: { getProfile: () => getProfile(), putProfile: (p: ProfileDto) => putProfile(p) },
}));

// Imported after the mock so profileStore picks up the mocked backendApi module.
const { EMPTY_PROFILE, loadProfile, saveProfile, syncProfile } = await import("./profileStore");

const remoteFromPhone: ProfileDto = {
  name: "From phone",
  role: null,
  phone: null,
  email: null,
  abn: "11111111111",
  bankBsb: "062-000",
  bankAccount: "12345678",
  updatedAtMillis: 2_000,
};

beforeEach(() => {
  localStorage.clear();
  putProfile.mockImplementation(async (p) => p);
});

afterEach(() => {
  vi.restoreAllMocks();
  getProfile.mockReset();
  putProfile.mockReset();
});

describe("syncProfile", () => {
  it("adopts the server's profile when this browser has none", async () => {
    getProfile.mockResolvedValue(remoteFromPhone);

    const result = await syncProfile();

    expect(result.name).toBe("From phone");
    expect(result.bankBsb).toBe("062-000");
    expect(putProfile).not.toHaveBeenCalled();
    expect(loadProfile().name).toBe("From phone");
  });

  it("uploads the local profile when the server has none, stamping a pre-sync cache with a real edit time", async () => {
    localStorage.setItem("onsite_profile", JSON.stringify({ name: "Old browser", abn: "", bankDetails: "BSB 000-000" }));
    getProfile.mockResolvedValue(null);

    const result = await syncProfile();

    expect(putProfile).toHaveBeenCalledTimes(1);
    const sent = putProfile.mock.calls[0][0];
    expect(sent.name).toBe("Old browser");
    expect(sent.bankAccount).toBe("BSB 000-000");
    expect(sent.updatedAtMillis).toBeGreaterThan(0);
    expect(result.updatedAtMillis).toBe(sent.updatedAtMillis);
  });

  it("lets the server win when the browser copy predates sync", async () => {
    localStorage.setItem("onsite_profile", JSON.stringify({ name: "Old browser" }));
    getProfile.mockResolvedValue(remoteFromPhone);

    const result = await syncProfile();

    expect(result.name).toBe("From phone");
    expect(putProfile).not.toHaveBeenCalled();
  });

  it("pushes a newer local edit over an older server copy", async () => {
    localStorage.setItem("onsite_profile", JSON.stringify({ ...EMPTY_PROFILE, name: "Newer here", updatedAtMillis: 5_000 }));
    getProfile.mockResolvedValue(remoteFromPhone);

    const result = await syncProfile();

    expect(putProfile).toHaveBeenCalledTimes(1);
    expect(putProfile.mock.calls[0][0].updatedAtMillis).toBe(5_000);
    expect(result.name).toBe("Newer here");
  });

  it("keeps the cache as is when the server can't be reached", async () => {
    localStorage.setItem("onsite_profile", JSON.stringify({ ...EMPTY_PROFILE, name: "Offline", updatedAtMillis: 1 }));
    getProfile.mockRejectedValue(new Error("network"));

    expect((await syncProfile()).name).toBe("Offline");
    expect(putProfile).not.toHaveBeenCalled();
  });
});

describe("saveProfile", () => {
  it("stamps the edit time, caches, and keeps whatever the server answered", async () => {
    putProfile.mockResolvedValue({ ...remoteFromPhone, updatedAtMillis: 9_000 });

    const result = await saveProfile({ ...EMPTY_PROFILE, name: "Typed here" });

    expect(putProfile.mock.calls[0][0].name).toBe("Typed here");
    expect(putProfile.mock.calls[0][0].updatedAtMillis).toBeGreaterThan(0);
    expect(result.name).toBe("From phone");
    expect(loadProfile().updatedAtMillis).toBe(9_000);
  });

  it("still caches locally when the push fails", async () => {
    putProfile.mockRejectedValue(new Error("offline"));

    const result = await saveProfile({ ...EMPTY_PROFILE, name: "Kept locally" });

    expect(result.name).toBe("Kept locally");
    expect(loadProfile().name).toBe("Kept locally");
  });
});
