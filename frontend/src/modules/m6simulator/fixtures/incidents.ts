// UI fixtures only. D07 owns transitions; TV2's 2026-10-28 reply owns severity
// vocabulary. No Incident API, duplicate admission or device-health effects.
export const incidentSeverities = ["LOW", "MEDIUM", "HIGH", "CRITICAL"] as const;
export type IncidentSeverity = typeof incidentSeverities[number];
export const incidentStatuses = ["Reported", "Assigned", "InProgress", "Resolved", "Closed"] as const;
export type IncidentStatus = typeof incidentStatuses[number];
export type IncidentAction = "ASSIGN" | "START" | "RESOLVE" | "REOPEN" | "CLOSE";
export type FixtureResource = "VEHICLE" | "CHARGING_POINT";
export interface FixtureIncident {
  id: string;
  resourceType: FixtureResource;
  resourceId: string;
  hub: string;
  reasonCode: string;
  description: string;
  status: IncidentStatus;
  severity: IncidentSeverity;
  assignee: string | null;
  history: string[];
}

const transitions: Record<IncidentStatus, Partial<Record<IncidentAction, IncidentStatus>>> = {
  Reported: { ASSIGN: "Assigned" },
  Assigned: { START: "InProgress" },
  InProgress: { RESOLVE: "Resolved" },
  Resolved: { REOPEN: "InProgress", CLOSE: "Closed" },
  Closed: {},
};
export function allowedFixtureActions(status: IncidentStatus): IncidentAction[] {
  return Object.keys(transitions[status]) as IncidentAction[];
}
export function transitionFixture(incident: FixtureIncident, action: IncidentAction, assignee?: string): FixtureIncident {
  const status = transitions[incident.status][action];
  if (!status) throw new Error("Transition ngoài D07.");
  return { ...incident, status, assignee: action === "ASSIGN" ? assignee || null : incident.assignee,
    history: [...incident.history, `Thao tác mẫu ${action}: ${incident.status} → ${status}`] };
}

export function createIncidentFixtures(): FixtureIncident[] {
  return incidentStatuses.map((status, index) => ({
    id: `FIX-${String(index + 1).padStart(3, "0")}`,
    resourceType: index % 2 === 0 ? "VEHICLE" : "CHARGING_POINT",
    resourceId: `DEMO-${index + 1}`,
    hub: index % 2 === 0 ? "Hub Metro (mẫu)" : "Hub KTX (mẫu)",
    reasonCode: "MANUAL_REPORT",
    description: "Dữ liệu mẫu để review giao diện; không phải sự cố đã lưu.",
    status,
    // Deliberately varied sample data; never a default for the report form.
    severity: incidentSeverities[index % incidentSeverities.length],
    assignee: status === "Reported" ? null : "Operator mẫu A",
    history: [`Fixture bắt đầu tại ${status}; không có lịch sử backend.`],
  }));
}
