import { api, type ApiError } from "@/shared/api/client";
import type { AxiosError } from "axios";

export type VehicleKind = "PERSONAL" | "RENTAL";
export type ChargingStatus = "REQUESTED" | "QUEUED" | "SCHEDULED" | "CHARGING" | "COMPLETED" | "INTERRUPTED" | "CANCELLED";
export type PriorityLevel = "NORMAL" | "HIGH" | "URGENT";

export interface ChargingHub {
  hubId: number;
  hubName: string;
  totalPoints: number;
  availablePoints: number;
  chargingPoints: number;
  faultyPoints: number;
  queuedSessions: number;
}

export interface ChargingSession {
  id: number;
  userId: number | null;
  hubId: number;
  parkingSpaceId: number | null;
  vehicleKind: VehicleKind;
  vehicleId: number;
  batteryLevel: number;
  status: ChargingStatus;
  priorityLevel: PriorityLevel;
  priorityScore: number;
  chargingPointId: number | null;
  expectedUseAt: string | null;
  scheduledStartAt: string | null;
  scheduledEndAt: string | null;
  holdExpiresAt: string | null;
  createdAt: string;
  queuePosition: number | null;
  waitingMinutes: number;
  estimatedWaitMinutes: number | null;
}

export const chargingApi = {
  hubs: () => api.get<ChargingHub[]>("/m5/hubs").then(r => r.data),
  mine: () => api.get<ChargingSession[]>("/m5/sessions/mine").then(r => r.data),
  queue: (hubId: number) => api.get<ChargingSession[]>(`/m5/hubs/${hubId}/queue`).then(r => r.data),
  request: (body: {
    hubId: number; vehicleKind: VehicleKind; vehicleId: number; batteryLevel: number;
    parkingSpaceId?: number; expectedUseAt?: string;
  }) => api.post<ChargingSession>("/m5/sessions", body).then(r => r.data),
  schedule: (body: {
    hubId: number; vehicleId: number; batteryLevel: number; parkingSpaceId: number;
    startAt: string; durationMinutes: number;
  }) => api.post<ChargingSession>("/m5/sessions/schedule", body).then(r => r.data),
  start: (id: number) => api.post<ChargingSession>(`/m5/sessions/${id}/start`).then(r => r.data),
  cancel: (id: number) => api.post<ChargingSession>(`/m5/sessions/${id}/cancel`).then(r => r.data),
  changePriority: (id: number, priority: PriorityLevel, reason: string) =>
    api.patch<ChargingSession>(`/m5/sessions/${id}/priority`, { priority, reason }).then(r => r.data),
  moveSchedule: (id: number, startAt: string, durationMinutes: number, reason: string) =>
    api.put<ChargingSession>(`/m5/sessions/${id}/schedule`, { startAt, durationMinutes, reason }).then(r => r.data),
};

export function chargingErrorMessage(error: unknown): string {
  const data = (error as AxiosError<ApiError>).response?.data;
  switch (data?.code) {
    case "BATTERY_ALREADY_FULL": return "Charging is not accepted when the battery is already at 90% or higher.";
    case "ACTIVE_SESSION_EXISTS": return "This vehicle already has an unfinished charging session.";
    case "NO_OPERATIONAL_CHARGER": return "This Hub has no operational charging point.";
    case "CHARGING_CAPACITY_FULL": return "No charging point is free for the selected time window.";
    case "PARKING_RESERVATION_REQUIRED": return "A valid parking reservation at the same Hub must cover the entire charging window.";
    case "SCHEDULE_LIMIT_EXCEEDED": return "You can have at most two active charging schedules.";
    case "CHARGING_HOLD_EXPIRED": return "The 10-minute port hold expired. Please create a new charging request.";
    case "CHARGING_POINT_BUSY": return "The assigned charging point is no longer available.";
    case "INVALID_STATE_TRANSITION": return "This action is not allowed in the current session state.";
    case "ACCESS_DENIED": return "You do not have permission to change this charging session.";
    case "VALIDATION_FAILED": return "Check the entered values and try again.";
    default: return data?.message ?? "Unable to complete the charging request. Please try again.";
  }
}

export async function currentRole(): Promise<"STUDENT" | "OPERATOR"> {
  const response = await api.get<{ role: "STUDENT" | "OPERATOR" }>("/auth/me");
  return response.data.role;
}
