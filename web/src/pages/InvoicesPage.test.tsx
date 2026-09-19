import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { CompanyRequest, InvoiceRequest, SiteRequest, TrackingSessionRequest } from "../lib/types";
import { InvoicesPage } from "./InvoicesPage";

const listInvoices = vi.fn<() => Promise<InvoiceRequest[]>>();
const listCompanies = vi.fn<() => Promise<CompanyRequest[]>>();
const listSites = vi.fn<() => Promise<SiteRequest[]>>();
const listTrackingSessions = vi.fn<() => Promise<TrackingSessionRequest[]>>();
const createInvoice = vi.fn().mockResolvedValue(undefined);
const updateTrackingSession = vi.fn().mockResolvedValue(undefined);
const updateInvoice = vi.fn().mockResolvedValue(undefined);

vi.mock("../lib/backendApi", () => ({
  backendApi: {
    listInvoices: () => listInvoices(),
    listCompanies: () => listCompanies(),
    listSites: () => listSites(),
    listTrackingSessions: () => listTrackingSessions(),
    createInvoice: (req: unknown) => createInvoice(req),
    updateTrackingSession: (id: string, req: unknown) => updateTrackingSession(id, req),
    updateInvoice: (id: string, req: unknown) => updateInvoice(id, req),
  },
  ApiError: class ApiError extends Error {},
}));

const COMPANY: CompanyRequest = { id: "c1", name: "Acme", createdAtMillis: 0, hourlyRate: 50 };
const SITE: SiteRequest = { id: "s1", label: "HQ", latitude: 0, longitude: 0, createdAtMillis: 0 };
const START = Date.UTC(2026, 2, 16, 8, 0);
const SESSION: TrackingSessionRequest = {
  id: "sess1",
  companyName: "Acme",
  siteLabel: "HQ",
  jobTypeLabel: "Install",
  startTimestampMillis: START,
  startLatitude: 0,
  startLongitude: 0,
  stopTimestampMillis: START + 2 * 3_600_000,
  stopLatitude: 0,
  stopLongitude: 0,
  hourlyRate: null,
  invoiceId: null,
};

beforeEach(() => {
  listInvoices.mockResolvedValue([]);
  listCompanies.mockResolvedValue([COMPANY]);
  listSites.mockResolvedValue([SITE]);
  listTrackingSessions.mockResolvedValue([SESSION]);
});

afterEach(() => {
  vi.clearAllMocks();
  localStorage.clear();
});

describe("InvoicesPage - new invoice review", () => {
  it("generates an invoice from unbilled sessions using the company's default rate", async () => {
    const user = userEvent.setup();
    render(<InvoicesPage />);

    await user.click(await screen.findByRole("button", { name: "+ Nova fatura" }));
    const dialog = await screen.findByRole("heading", { name: "Nova fatura" });
    const modal = dialog.closest("div")!.parentElement!;

    // Base rate comes from the company (50/h) and the 2h session should form one line.
    await waitFor(() => expect(within(modal).getByText(/1 dia pendente/)).toBeInTheDocument());
    expect(within(modal).getByText("AU$ 100,00")).toBeInTheDocument();

    await user.click(within(modal).getByRole("button", { name: "Gerar fatura" }));

    await waitFor(() => expect(createInvoice).toHaveBeenCalledTimes(1));
    const invoiceReq = createInvoice.mock.calls[0][0] as InvoiceRequest;
    expect(invoiceReq.companyName).toBe("Acme");
    expect(invoiceReq.totalHours).toBe(2);
    expect(invoiceReq.totalAmount).toBe(100);

    // The session gets tagged with the new invoice's id.
    expect(updateTrackingSession).toHaveBeenCalledWith("sess1", expect.objectContaining({ invoiceId: invoiceReq.id }));
  });

  it("lets the reviewer override a line's rate before generating, and persists it to the session", async () => {
    const user = userEvent.setup();
    render(<InvoicesPage />);

    await user.click(await screen.findByRole("button", { name: "+ Nova fatura" }));
    const dialog = await screen.findByRole("heading", { name: "Nova fatura" });
    const modal = dialog.closest("div")!.parentElement!;

    await waitFor(() => expect(within(modal).getByText(/1 dia pendente/)).toBeInTheDocument());

    const rateInput = within(modal).getByPlaceholderText("50");
    await user.clear(rateInput);
    await user.type(rateInput, "80");

    await waitFor(() => expect(within(modal).getAllByText("AU$ 160,00").length).toBeGreaterThan(0));

    await user.click(within(modal).getByRole("button", { name: "Gerar fatura" }));

    await waitFor(() => expect(createInvoice).toHaveBeenCalledTimes(1));
    expect((createInvoice.mock.calls[0][0] as InvoiceRequest).totalAmount).toBe(160);
    expect(updateTrackingSession).toHaveBeenCalledWith("sess1", expect.objectContaining({ hourlyRate: 80 }));
  });
});
