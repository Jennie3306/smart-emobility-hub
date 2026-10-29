// @vitest-environment jsdom
import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, it, vi } from "vitest";
import { api } from "../../../shared/api/client";
import { IncidentFixturePanel } from "./IncidentFixturePanel";
import { incidentSeverities } from "../fixtures/incidents";

afterEach(() => { cleanup(); vi.restoreAllMocks(); });

it("filters status/resource/query and shows an empty match", async () => {
  render(<IncidentFixturePanel />);
  const user = userEvent.setup();
  await user.selectOptions(screen.getByLabelText("Trạng thái"), "Closed");
  expect(screen.getAllByRole("row")).toHaveLength(2);
  expect(screen.getByRole("button", { name: "Xem FIX-005" })).toBeTruthy();
  await user.selectOptions(screen.getByLabelText("Tài nguyên"), "CHARGING_POINT");
  expect(screen.getByText("Không có fixture phù hợp với bộ lọc.")).toBeTruthy();
  await user.selectOptions(screen.getByLabelText("Trạng thái"), "");
  await user.selectOptions(screen.getByLabelText("Tài nguyên"), "");
  await user.type(screen.getByLabelText("Tìm trong fixture"), "demo-3");
  expect(screen.getAllByRole("row")).toHaveLength(2);
  expect(screen.getByRole("button", { name: "Xem FIX-003" })).toBeTruthy();
  await user.selectOptions(screen.getByLabelText("Severity", { exact: true }), "LOW");
  expect(screen.getByText("Không có fixture phù hợp với bộ lọc.")).toBeTruthy();
  await user.selectOptions(screen.getByLabelText("Severity", { exact: true }), "HIGH");
  expect(screen.getByRole("button", { name: "Xem FIX-003" })).toBeTruthy();
});

it("walks D07 including reopen, then blocks Closed and resets fixtures", async () => {
  render(<IncidentFixturePanel />);
  const user = userEvent.setup();
  const detail = within(screen.getByRole("region", { name: "Chi tiết mẫu · FIX-001" }));
  expect((detail.getByRole("button", { name: "Gán người mẫu" }) as HTMLButtonElement).disabled).toBe(true);
  await user.selectOptions(detail.getByLabelText("Người xử lý mẫu"), "Operator mẫu A");
  for (const name of ["Gán người mẫu", "Bắt đầu mẫu", "Resolve mẫu", "Reopen mẫu", "Resolve mẫu", "Close mẫu"]) {
    await user.click(detail.getByRole("button", { name }));
  }
  expect(detail.getByText("Closed là terminal theo D07; không có thao tác chuyển trạng thái.")).toBeTruthy();
  expect(detail.queryByRole("button", { name: "Reopen mẫu" })).toBeNull();
  expect(detail.getAllByRole("listitem")).toHaveLength(7);
  expect(detail.getByText("LOW")).toBeTruthy();
  await user.click(screen.getByRole("button", { name: "Đặt lại fixture" }));
  expect(detail.getByRole("button", { name: "Gán người mẫu" })).toBeTruthy();
});

it.each(incidentSeverities)("previews explicit %s severity without creating a row or sending requests", async severity => {
  const post = vi.spyOn(api, "post");
  const get = vi.spyOn(api, "get");
  render(<IncidentFixturePanel />);
  const user = userEvent.setup();
  await user.click(screen.getByRole("button", { name: "Mở bản mẫu báo cáo" }));
  await user.type(screen.getByLabelText("Mã tài nguyên mẫu"), "DRAFT-99");
  await user.type(screen.getByLabelText("Mô tả"), "Bản nháp thử UI");
  const severitySelect = screen.getByLabelText("Severity báo cáo") as HTMLSelectElement;
  expect(severitySelect.value).toBe("");
  await user.selectOptions(severitySelect, severity);
  await user.click(screen.getByRole("button", { name: "Xem bản nháp" }));
  expect(screen.getByRole("heading", { name: "Bản nháp chưa gửi" })).toBeTruthy();
  expect(screen.getAllByRole("row")).toHaveLength(6);
  expect(screen.getByText(`Severity: ${severity}`)).toBeTruthy();
  expect(screen.getByText("Quy tắc Incident trùng đã chốt; bản nháp này chưa kiểm tra trùng.")).toBeTruthy();
  expect(screen.queryByText(/chờ TV2/i)).toBeNull();
  expect(post).not.toHaveBeenCalled(); expect(get).not.toHaveBeenCalled();
});

it("requires an explicit severity before preview and offers no default", async () => {
  render(<IncidentFixturePanel />);
  const user = userEvent.setup();
  await user.click(screen.getByRole("button", { name: "Mở bản mẫu báo cáo" }));
  await user.type(screen.getByLabelText("Mã tài nguyên mẫu"), "DRAFT-100");
  await user.type(screen.getByLabelText("Mô tả"), "Severity chưa chọn");
  const select = screen.getByLabelText("Severity báo cáo") as HTMLSelectElement;
  expect(select.required).toBe(true);
  expect(select.value).toBe("");
  expect(Array.from(select.options).map(option => option.value)).toEqual(["", "LOW", "MEDIUM", "HIGH", "CRITICAL"]);
  await user.click(screen.getByRole("button", { name: "Xem bản nháp" }));
  expect(select.validity.valueMissing).toBe(true);
  expect(screen.queryByRole("heading", { name: "Bản nháp chưa gửi" })).toBeNull();
});
