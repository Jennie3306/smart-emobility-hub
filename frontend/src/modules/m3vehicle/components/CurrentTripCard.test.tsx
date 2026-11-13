// @vitest-environment jsdom
import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { api } from "../../../shared/api/client";
import { CurrentTripCard, tripMinutes } from "./CurrentTripCard";

afterEach(() => { cleanup(); vi.restoreAllMocks(); });

const hubLabel = (id: number) => (id === 1 ? "Metro Hub" : `Hub #${id}`);

it("shows the vehicle, the pick-up Hub while on the road, and the battery (FR-M3-03)", async () => {
  const get = vi.spyOn(api, "get").mockResolvedValue({
    data: { data: { sessionId: 4, vehicleId: 1, vehicleCode: "EB-001", vehicleState: "IN_USE",
      batteryLevel: 82, hubId: null, startTime: "2026-11-13T17:50:18" } },
  });
  render(<CurrentTripCard hubLabel={hubLabel} pickupHubId={1} />);

  expect(await screen.findByText("EB-001")).toBeTruthy();
  expect(screen.getByText("On the road (picked up at Metro Hub)")).toBeTruthy();
  expect(screen.getByText("82%")).toBeTruthy();
  expect(get).toHaveBeenCalledWith("/m3/vehicles/current-trip");
});

it("renders nothing when the student has no active trip", async () => {
  const get = vi.spyOn(api, "get").mockRejectedValue(Object.assign(new Error("404"), {
    isAxiosError: true, response: { status: 404, data: { code: "NO_ACTIVE_TRIP" } },
  }));
  const { container } = render(<CurrentTripCard hubLabel={hubLabel} />);
  await vi.waitFor(() => expect(get).toHaveBeenCalled());
  await Promise.resolve();
  expect(container.innerHTML).toBe("");
  expect(screen.queryByText("Unable to load the current trip.")).toBeNull();
});

it("counts whole minutes since the start and never goes negative", () => {
  expect(tripMinutes("2026-11-13T17:00:00", new Date("2026-11-13T18:25:40"))).toBe(85);
  expect(tripMinutes("2026-11-13T18:00:00", new Date("2026-11-13T17:59:00"))).toBe(0);
});
