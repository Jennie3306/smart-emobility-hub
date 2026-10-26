import type { AxiosError } from "axios";
import { api, type ApiError } from "@/shared/api/client";
import { englishHubLabels } from "@/shared/api/hubLabels";

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
  { id: 1, name: "Metro Hub" },
  { id: 2, name: "Dormitory A Hub" },
  { id: 3, name: "Dormitory B Hub" },
  { id: 4, name: "Bach Khoa Hub" },
  { id: 5, name: "Science Hub" },
];

const hubNames=new Map(HUBS.map(h=>[h.id,h.name]));
export const hubName = (id: number | null) => id===null?"-":hubNames.get(id) ?? `Hub #${id}`;

export const VEHICLE_TYPE_LABEL: Record<VehicleType, string> = {
  E_BIKE: "Electric bicycle",
  E_SCOOTER: "Electric scooter",
};

export const reservationApi = {
  hubs: () => api.get<{id:number;name:string}[]>("/m1/hubs").then(r=>{const hubs=englishHubLabels(r.data);hubs.forEach(h=>hubNames.set(h.id,h.name));return hubs;}),
  returnVehicle: (vehicleId:number,hubId:number,batteryLevel:number) => api.post(`/m3/vehicles/${vehicleId}/return`,{hubId,batteryLevel}),
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
    case "RESOURCE_CONFLICT": return "This vehicle was just reserved. Choose another vehicle.";
    case "RESERVATION_LIMIT_EXCEEDED": return "You already have an active vehicle reservation";
    case "VEHICLE_NOT_AVAILABLE": return "The vehicle is no longer available";
    case "RESERVATION_EXPIRED": return "Reservation expired";
    case "ACCESS_DENIED": return "You do not own this reservation";
    case "INVALID_STATE_TRANSITION": return "This action is not allowed in the current reservation state";
    case "VALIDATION_FAILED": return "Minimum battery must be between 0 and 100";
    default: return data?.message ?? "An error occurred. Please try again.";
  }
}
