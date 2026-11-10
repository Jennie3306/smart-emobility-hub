// @vitest-environment jsdom
import { act, cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { simulatorApi, type SimulationRun, type SimulatorStatus } from "../api/simulatorApi";
import { SimulatorPanel } from "./SimulatorPanel";

vi.mock("../api/simulatorApi", async importOriginal => ({
  ...await importOriginal<typeof import("../api/simulatorApi")>(),
  simulatorApi: { status: vi.fn(), run: vi.fn() },
}));
const idle: SimulatorStatus = { schedulerEnabled: false, intervalMs: null, state: "IDLE", latestRun: null };
const result: SimulationRun = { runId: "e4c0178f-0674-4db1-a542-77a74892c346", scenario: "NORMAL_TICK", seed: "42",
  startedAt: "2026-10-28T09:00:00+07:00", completedAt: "2026-10-28T09:00:01+07:00", readingCount: 3,
  processedCount: 3, duplicateCount: 0, rejectedCount: 0, error: null,
  outboundEvents: [{ eventId: "d6daa174-94ce-4098-a85f-83326b9d8cdd", eventType: "ResourceStateUpdated", resourceType: "VEHICLE", resourceId: 1 }] };
const failure = (status: number) => ({ isAxiosError: true, response: { status, data: { code: `TEST_${status}`, details: { seed: "Check input" } } } });
beforeEach(() => { vi.resetAllMocks(); vi.mocked(simulatorApi.status).mockResolvedValue(idle); });
afterEach(cleanup);

async function ready() {
  render(<SimulatorPanel />);
  await screen.findByText("Chưa có run nào trong phiên backend này.");
  return userEvent.setup();
}

it("shows real disabled scheduler, null interval and empty latest run", async () => {
  await ready();
  expect(screen.getByText("Đang tắt")).toBeTruthy();
  expect(screen.getByText("—")).toBeTruthy();
  expect(screen.getAllByRole("option")).toHaveLength(3);
});

it.each(["NORMAL_TICK", "VEHICLE_FAILURE", "CHARGER_FAILURE"] as const)("sends %s and renders the returned receipt", async scenario => {
  const user = await ready();
  await user.selectOptions(screen.getByLabelText("Scenario"), scenario);
  vi.mocked(simulatorApi.run).mockResolvedValue({ ...result, scenario });
  vi.mocked(simulatorApi.status).mockResolvedValue({ ...idle, state: "SUCCEEDED", latestRun: { ...result, scenario } });
  await user.click(screen.getByRole("button", { name: "Chạy scenario" }));
  expect(simulatorApi.run).toHaveBeenCalledWith(scenario, "42");
  expect(await screen.findByText(result.runId)).toBeTruthy();
  expect(screen.getByText("ResourceStateUpdated")).toBeTruthy();
  expect(screen.getByText(result.outboundEvents[0].eventId)).toBeTruthy();
  expect(screen.getByText("Trùng inbound ID")).toBeTruthy();
});

it("rejects fractional seed locally without a POST", async () => {
  const user = await ready();
  await user.clear(screen.getByLabelText("Seed"));
  await user.type(screen.getByLabelText("Seed"), "1.5");
  await user.click(screen.getByRole("button", { name: "Chạy scenario" }));
  expect(screen.getByRole("alert").textContent).toContain("số nguyên");
  expect(simulatorApi.run).not.toHaveBeenCalled();
});

it.each([400, 401, 403])("shows HTTP %s and keeps the entered seed on run failure", async status => {
  const user = await ready();
  vi.mocked(simulatorApi.run).mockRejectedValue(failure(status));
  await user.click(screen.getByRole("button", { name: "Chạy scenario" }));
  expect((await screen.findByRole("alert")).textContent).toContain(`HTTP ${status}`);
  expect((screen.getByLabelText("Seed") as HTMLInputElement).value).toBe("42");
  expect(screen.queryByText(result.runId)).toBeNull();
});

it("refreshes on 409 without retrying the POST and blocks while backend is running", async () => {
  const user = await ready();
  vi.mocked(simulatorApi.run).mockRejectedValue(failure(409));
  vi.mocked(simulatorApi.status).mockResolvedValue({ ...idle, state: "RUNNING", schedulerEnabled: true, intervalMs: 5000 });
  await user.click(screen.getByRole("button", { name: "Chạy scenario" }));
  expect((await screen.findByRole("alert")).textContent).toContain("HTTP 409");
  await screen.findByText("Đang bật");
  expect((screen.getByRole("button", { name: "Chạy scenario" }) as HTMLButtonElement).disabled).toBe(true);
  expect(simulatorApi.run).toHaveBeenCalledTimes(1);
  vi.mocked(simulatorApi.status).mockResolvedValue(idle);
  await user.click(screen.getByRole("button", { name: "Làm mới trạng thái" }));
  await waitFor(() => expect((screen.getByRole("button", { name: "Chạy scenario" }) as HTMLButtonElement).disabled).toBe(false));
});

it("prevents duplicate clicks during a pending POST", async () => {
  const user = await ready();
  let finish!: (run: SimulationRun) => void;
  vi.mocked(simulatorApi.run).mockImplementation(() => new Promise(resolve => { finish = resolve; }));
  await user.dblClick(screen.getByRole("button", { name: "Chạy scenario" }));
  expect(simulatorApi.run).toHaveBeenCalledTimes(1);
  expect((screen.getByRole("button", { name: "Đang chạy…" }) as HTMLButtonElement).disabled).toBe(true);
  vi.mocked(simulatorApi.status).mockResolvedValue({ ...idle, state: "SUCCEEDED", latestRun: result });
  await act(async () => finish(result));
  await screen.findByText(result.runId);
});

it("shows status errors, disables run, and can retry a failed GET", async () => {
  vi.mocked(simulatorApi.status).mockRejectedValueOnce(failure(403));
  render(<SimulatorPanel />);
  expect((await screen.findByRole("alert")).textContent).toContain("HTTP 403");
  expect((screen.getByRole("button", { name: "Chạy scenario" }) as HTMLButtonElement).disabled).toBe(true);
  await userEvent.click(screen.getByRole("button", { name: "Làm mới trạng thái" }));
  await screen.findByText("Chưa có run nào trong phiên backend này.");
});

it("refreshes a failed run and displays server error and partial counts", async () => {
  const user = await ready();
  vi.mocked(simulatorApi.run).mockRejectedValue(failure(500));
  vi.mocked(simulatorApi.status).mockResolvedValue({ ...idle, state: "FAILED", latestRun: { ...result, processedCount: 1, error: "Consumer test failure" } });
  await user.click(screen.getByRole("button", { name: "Chạy scenario" }));
  expect(await screen.findByText("Run thất bại: Consumer test failure")).toBeTruthy();
  expect(screen.getByText(result.runId)).toBeTruthy();
});

it("keeps an unreachable backend visibly distinct from an empty run", async () => {
  vi.mocked(simulatorApi.status).mockRejectedValue(new Error("offline"));
  render(<SimulatorPanel />);
  expect((await screen.findByRole("alert")).textContent).toContain("Kiểm tra kết nối");
  expect(screen.getByText("Kết quả chưa tải được.")).toBeTruthy();
});
