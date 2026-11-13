// @vitest-environment jsdom
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, it, vi } from "vitest";
import { api } from "../../../shared/api/client";
import { IncidentPanel } from "./IncidentPanel";
import type { Incident } from "../api/incidentApi";

afterEach(() => { cleanup(); vi.restoreAllMocks(); });

const reported: Incident = {
  id: 7, resourceType: "CHARGING_POINT", resourceId: 1, hubId: 1, reason: "OVERHEAT", severity: "CRITICAL",
  description: "Device reported FAULTY (OVERHEAT)", status: "REPORTED", source: "DEVICE", reportedBy: "DEVICE",
  assignee: null, note: null, reportedAt: "2026-11-13T08:00:00Z", assignedAt: null, startedAt: null,
  resolvedAt: null, closedAt: null, version: 0, allowedActions: ["ASSIGN"],
};

it("lists incidents and assigns one with the version that was read", async () => {
  vi.spyOn(api, "get").mockResolvedValue({ data: [reported] });
  const patch = vi.spyOn(api, "patch").mockResolvedValue({ data: { ...reported, status: "ASSIGNED", version: 1 } });
  render(<IncidentPanel />);
  const user = userEvent.setup();

  await user.click(await screen.findByRole("button", { name: "Xem sự cố 7" }));
  const assign = screen.getByRole("button", { name: "Phân công" }) as HTMLButtonElement;
  expect(assign.disabled).toBe(true);
  await user.type(screen.getByPlaceholderText("vd. operator1"), "operator1");
  await user.click(assign);

  expect(patch).toHaveBeenCalledWith("/m6/incidents/7",
    { action: "ASSIGN", expectedVersion: 0, assigneeUserId: "operator1", note: undefined });
  expect(await screen.findByText("Sự cố #7: Phân công → ASSIGNED")).toBeTruthy();
});

it("shows the existing incident when a duplicate report is refused", async () => {
  vi.spyOn(api, "get").mockResolvedValue({ data: [] });
  vi.spyOn(api, "post").mockRejectedValue(Object.assign(new Error("409"), {
    isAxiosError: true,
    response: { status: 409, data: { code: "ACTIVE_INCIDENT_EXISTS", message: "", details: { existingIncidentId: 7, existingStatus: "REPORTED" } } },
  }));
  render(<IncidentPanel />);
  const user = userEvent.setup();

  await user.click(screen.getByRole("button", { name: "Báo cáo sự cố" }));
  await user.type(screen.getByLabelText("Mã tài nguyên"), "1");
  await user.selectOptions(screen.getByLabelText("Mức độ sự cố"), "HIGH");
  await user.click(screen.getByRole("button", { name: "Gửi báo cáo" }));

  expect(await screen.findByText(/đang có sự cố #7 \(REPORTED\)/)).toBeTruthy();
});
