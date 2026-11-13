import axios from "axios";
import { api, type ApiError } from "@/shared/api/client";

export type TaskStatus = "CREATED" | "IN_PROGRESS" | "DONE" | "FAILED";
export type RecommendationStatus = "PENDING" | "APPROVED" | "PARTIAL" | "REJECTED";

export interface RedistributionTask {
  taskId: number;
  vehicleId: number;
  sourceHubId: number;
  destinationHubId: number;
  status: TaskStatus;
  recommendationId: string | null;
  createdAt: string;
  updatedAt: string | null;
}

export interface Recommendation {
  id: number;
  source: "M1_ALERT" | "M8_SIMULATION" | string;
  externalRef: string | null;
  sourceHubId: number;
  sourceHubName: string | null;
  destinationHubId: number;
  destinationHubName: string | null;
  amount: number;
  taskCount: number;
  reason: string | null;
  status: RecommendationStatus;
  note: string | null;
  createdAt: string;
  resolvedAt: string | null;
  resolvedBy: string | null;
}

export interface ApprovalResult {
  recommendationId: string;
  status: RecommendationStatus;
  taskIds: number[];
  message: string | null;
}

/** BR-M7 task lifecycle (section 4.1 of Template_M7): the buttons offered for each status. */
export const nextStatuses: Record<TaskStatus, TaskStatus[]> = {
  CREATED: ["IN_PROGRESS", "FAILED"],
  IN_PROGRESS: ["DONE", "FAILED"],
  DONE: [],
  FAILED: [],
};

export const redistributionApi = {
  tasks: (status?: TaskStatus) =>
    api.get<RedistributionTask[]>("/m7/redistributions", { params: status ? { status } : {} }).then(r => r.data),
  createTask: (vehicleId: number, sourceHubId: number, destinationHubId: number) =>
    api.post<RedistributionTask>("/m7/redistributions", { vehicleId, sourceHubId, destinationHubId }).then(r => r.data),
  moveTask: (taskId: number, status: TaskStatus) =>
    api.put<RedistributionTask>(`/m7/redistributions/${taskId}/status`, null, { params: { status } }).then(r => r.data),
  recommendations: (status?: RecommendationStatus) =>
    api.get<Recommendation[]>("/m7/recommendations", { params: status ? { status } : {} }).then(r => r.data),
  approve: (id: number) =>
    api.post<ApprovalResult>(`/m7/recommendations/${id}/approve`).then(r => r.data),
  reject: (id: number, reason: string) =>
    api.post<Recommendation>(`/m7/recommendations/${id}/reject`, { reason }).then(r => r.data),
};

const friendly: Record<string, string> = {
  VEHICLE_RESERVED: "The vehicle is reserved or in use, so it cannot be moved (BR-M7-01).",
  TASK_ALREADY_ACTIVE: "This vehicle already has an active redistribution task (BR-M7-02).",
  DESTINATION_FULL: "The destination hub has no free parking space left (BR-M7-03).",
  VEHICLE_NOT_AT_SOURCE: "The vehicle is not at the selected source hub.",
  SAME_HUB: "Source and destination must be different hubs.",
  INVALID_STATE_TRANSITION: "That status change is not allowed for this task.",
  RECOMMENDATION_ALREADY_RESOLVED: "This recommendation has already been decided. Refresh the list.",
};

export function redistributionError(error: unknown): string {
  if (axios.isAxiosError<ApiError>(error)) {
    const data = error.response?.data;
    if (data?.code && friendly[data.code]) return friendly[data.code];
    if (data?.message) return data.message;
    if (error.response?.status === 403) return "Only operators can manage redistribution.";
  }
  return "The request could not be completed. Check the connection and try again.";
}
