import type { AxiosError } from "axios";
import { api, type ApiError } from "@/shared/api/client";

export type VehicleType = "E_BIKE" | "E_SCOOTER";
export type ReservationState =
  | "PENDING" | "CONFIRMED" | "CHECKED_IN" | "COMPLETED" | "CANCELLED" | "EXPIRED";

export interface VehicleSummary {
  id: number;
  code: string;
  type: VehicleType;
  batteryLevel: number;
  hubId: number | null;
}

export interface VehicleDetail extends VehicleSummary {
  state: string;
  holdMinutes: number;
}

export interface HubSuggestion {
  hubId: number;
  availableCount: number;
}

export interface Reservation {
  id: number;
  resourceType: string;
  resourceId: number;
  vehicleCode: string | null;
  hubId: number | null;
  state: ReservationState;
  cancelReason: string | null;
  expiresAt: string | null;
  remainingSeconds: number;   // computed by the server - count down from this, independent of the device clock
}

export interface SearchParams {
  hubId?: number;
  type?: VehicleType;
  minBattery?: number;
}

// Hard-coded from the V2 seed until M1 provides GET /api/m1/hubs (recorded in section 4.4)
export const HUBS = [
  { id: 1, name: "Hub Ga Metro" },
  { id: 2, name: "Hub KTX Khu A" },
  { id: 3, name: "Hub KTX Khu B" },
  { id: 4, name: "Hub BK" },
  { id: 5, name: "Hub KHTN" },
];

export const hubName = (id: number | null) => HUBS.find((h) => h.id === id)?.name ?? "-";

export const VEHICLE_TYPE_LABEL: Record<VehicleType, string> = {
  E_BIKE: "Xe đạp điện",
  E_SCOOTER: "Xe máy điện",
};

export const reservationApi = {
  search: (params: SearchParams) =>
    api.get<VehicleSummary[]>("/m2/vehicles/search", { params }).then((r) => r.data),
  detail: (id: number) =>
    api.get<VehicleDetail>(`/m2/vehicles/${id}`).then((r) => r.data),
  alternatives: (params: SearchParams) =>
    api.get<HubSuggestion[]>("/m2/hubs/alternatives", { params }).then((r) => r.data),
  reserve: (vehicleId: number) =>
    api.post<Reservation>("/m2/reservations", { vehicleId }).then((r) => r.data),
  /** null when there is no active reservation (backend returns 204). */
  current: () =>
    api.get<Reservation>("/m2/reservations/current").then((r) => (r.status === 204 ? null : r.data)),
  get: (id: number) =>
    api.get<Reservation>(`/m2/reservations/${id}`).then((r) => r.data),
  cancel: (id: number) =>
    api.post<Reservation>(`/m2/reservations/${id}/cancel`).then((r) => r.data),
  checkIn: (id: number) =>
    api.post<Reservation>(`/m2/reservations/${id}/check-in`).then((r) => r.data),
};

/** Backend error code of a failed request. */
export function errorCode(err: unknown): string | undefined {
  return (err as AxiosError<ApiError>).response?.data?.code;
}

/** Backend error code -> message shown to the user (screen spec S-M2-02, S-M2-03). */
export function errorMessage(err: unknown): string {
  const data = (err as AxiosError<ApiError>).response?.data;
  switch (data?.code) {
    case "RESOURCE_CONFLICT": return "Xe vừa được người khác đặt, vui lòng chọn xe khác";
    case "RESERVATION_LIMIT_EXCEEDED": return "Bạn đã có một đặt xe đang hiệu lực";
    case "VEHICLE_NOT_AVAILABLE": return "Xe vừa chuyển trạng thái, không còn đặt được";
    case "RESERVATION_EXPIRED": return "Đặt chỗ đã hết hạn";
    case "ACCESS_DENIED": return "Bạn không có quyền thao tác đặt chỗ này";
    case "INVALID_STATE_TRANSITION": return "Đặt chỗ không còn ở trạng thái cho phép thao tác này";
    case "VALIDATION_FAILED": return "Pin tối thiểu phải từ 0 đến 100";
    default: return data?.message ?? "Có lỗi xảy ra, vui lòng thử lại";
  }
}
