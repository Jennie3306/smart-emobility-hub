import axios from "axios";
import { api, type ApiError } from "../../../shared/api/client";

// FR-M6-01/02. Tu vung khop backend (IncidentStatus, IncidentAction, IncidentSeverity).
export const incidentSeverities = ["LOW", "MEDIUM", "HIGH", "CRITICAL"] as const;
export type IncidentSeverity = typeof incidentSeverities[number];
export const incidentStatuses = ["REPORTED", "ASSIGNED", "IN_PROGRESS", "RESOLVED", "CLOSED"] as const;
export type IncidentStatus = typeof incidentStatuses[number];
export type IncidentAction = "ASSIGN" | "START" | "RESOLVE" | "REOPEN" | "CLOSE";
export type IncidentResource = "VEHICLE" | "CHARGING_POINT";

/** BR-M6-03: ly do Operator duoc chon theo loai tai nguyen. */
export const reasonsFor: Record<IncidentResource, string[]> = {
  VEHICLE: ["MECHANICAL", "SENSOR_OFFLINE", "MANUAL_REPORT"],
  CHARGING_POINT: ["OFFLINE", "OVERHEAT", "POWER_FAULT", "MANUAL_REPORT"],
};

export interface Incident {
  id: number;
  resourceType: IncidentResource;
  resourceId: number;
  hubId: number;
  reason: string;
  severity: IncidentSeverity;
  description: string | null;
  status: IncidentStatus;
  source: "OPERATOR" | "DEVICE";
  reportedBy: string;
  assignee: string | null;
  note: string | null;
  reportedAt: string;
  assignedAt: string | null;
  startedAt: string | null;
  resolvedAt: string | null;
  closedAt: string | null;
  version: number;
  allowedActions: IncidentAction[];
}

export interface ReportIncident {
  hubId: number;
  resourceType: IncidentResource;
  resourceId: number;
  reason: string;
  severity: IncidentSeverity;
  description?: string;
}

export interface IncidentFilter { status?: string; resourceType?: string; severity?: string }

export const incidentApi = {
  async list(filter: IncidentFilter = {}): Promise<Incident[]> {
    const params = Object.fromEntries(Object.entries(filter).filter(([, v]) => v));
    return (await api.get<Incident[]>("/m6/incidents", { params })).data;
  },
  async report(body: ReportIncident): Promise<Incident> {
    return (await api.post<Incident>("/m6/incidents", body)).data;
  },
  async transition(incident: Incident, action: IncidentAction, assigneeUserId?: string, note?: string): Promise<Incident> {
    return (await api.patch<Incident>(`/m6/incidents/${incident.id}`,
      { action, expectedVersion: incident.version, assigneeUserId, note })).data;
  },
};

export function incidentError(error: unknown): string {
  const response = axios.isAxiosError<ApiError>(error) ? error.response : undefined;
  const data = response?.data;
  switch (data?.code) {
    case "ACTIVE_INCIDENT_EXISTS":
      return `This resource already has incident #${data.details?.existingIncidentId} (${data.details?.existingStatus}). Handle that incident first.`;
    case "STALE_VERSION":
      return "Someone else has just updated this incident. The list was reloaded; please try again.";
    case "INVALID_STATE_TRANSITION":
      return "This action is not allowed in the current state.";
    case "RESOURCE_NOT_FOUND":
      return "No vehicle or charging point has this ID.";
    case "RESOURCE_HUB_MISMATCH":
      return "The resource does not belong to the selected hub.";
    case "ASSIGNEE_NOT_FOUND":
      return "There is no Operator account with this username.";
  }
  if (data?.message) return data.message;
  if (response?.status === 403) return "Only Operator accounts can manage incidents.";
  return "The request could not be completed. Check the connection and try again.";
}
