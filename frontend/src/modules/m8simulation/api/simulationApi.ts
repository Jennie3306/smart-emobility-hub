import { api } from "@/shared/api/client";
export interface Hub { id: number; name: string; capacity: number; chargingPoints: number }
export interface Vehicle { id: number; hubId: number | null; battery: number; state: string }
export interface BookingWindow { from:string; to:string; windowMins:number; hubs:{hubId:number;bookings:number;activeHolds:number}[] }
export interface CapacityState {hubId:number;totalSlots:number;usedSlots:number;freeSlots:number;operationalChargers:number;busyChargers:number}
export interface Snapshot { id: string; capturedAt: string; hubs: Hub[]; vehicles: Vehicle[]; deviceStates: {hubId:number;type:string;resourceId:number;state:string;observedAt:string}[]; demandSource: string; bookingWindow?:BookingWindow|null; capacities?:CapacityState[]; hypotheticalFleet?:boolean }
export interface HubSettings { hubId:number; disabled:boolean; injectedRequests:number; arrivalWindowMins:number; tripMins:number; reroute:boolean; repeatBookings:boolean; closeFromMins:number; reopenAtMins:number|null }
export interface Scenario { name:string; hubId:number; disabled:boolean; injectedRequests:number; durationMins:number; arrivalWindowMins:number; tripMins:number; reroute:boolean; seed:number; affectedHubs?:HubSettings[]|null }
export interface Kpi { requests:number; served:number; failed:number; failurePercent:number; meanWaitMins:number; peakQueue:number; hubs:{hubId:number;name:string;requests:number;served:number;failed:number;meanWaitMins:number;availableVehicles:number;peakParkingPercent:number}[] }
export interface Recommendation {
  id: string; action: string; fromHubId: number; toHubId: number; amount: number;
  reason: string; status: string; projectedFailurePercent: number;
  dispatch?: { tasks: { vehicleId: number | null; taskId: number }[]; unconfirmedVehicleId: number | null; message: string | null; m7RecommendationId?: string | null } | null;
}
export interface PlanAction {action:"reroute"|"dispatch";fromHubId:number;toHubId:number;amount:number;reason:string}
export interface PlanEvaluation {valid:boolean;actions:PlanAction[];kpi:Kpi|null;feedback:string}
export interface AgentRound {number:number;tool:string;valid:boolean;actions:PlanAction[];kpi:Kpi|null;feedback:string}
export interface AgentState {totalRounds:number;status:string;message:string;rounds:AgentRound[];best:PlanEvaluation|null}
export interface Run { id:string;createdAt:string;scenario:Scenario;snapshot:Snapshot;kpi:Kpi;baseline:Kpi;timeline:{minute:number;served:number;waiting:number;failed:number}[];recommendations:Recommendation[];recommendationSource:string;warnings:string[];agent?:AgentState|null }
export interface Monitor {lastChecked:string|null;warnings:string[];intervalMins:number;demandConnected:boolean;applyEnabled:boolean;aiConfigured:boolean;demandDescription?:string;forecasts?:{hubId:number;hubName:string;recentBookings:number;activeHolds:number;projectedServed:number;projectedFailed:number}[]}
export const simulationApi = {
  snapshot: () => api.get<Snapshot>("/m8/snapshot").then(r=>r.data),
  status: () => api.get<Monitor>("/m8/status").then(r=>r.data),
  list: () => api.get<Run[]>("/m8/runs").then(r=>r.data),
  parse: (text:string) => api.post<{scenario:Scenario;source:string;warnings:string[]}>("/m8/parse",{text}).then(r=>r.data),
  run: (scenario:Scenario,snapshotRunId?:string,repeatRecentBookings=false, startingVehicles?:string) => api.post<Run>("/m8/runs",scenario,{params:{snapshotRunId,repeatRecentBookings,startingVehicles}}).then(r=>r.data),
  check: () => api.post<Monitor>("/m8/monitor/check").then(r=>r.data),
  retry: (runId:string,expectedRounds:number) => api.post<Run>(`/m8/runs/${runId}/agent/retry`,null,{params:{expectedRounds}}).then(r=>r.data),
  discard: (runId:string,id:string) => api.post<Run>(`/m8/runs/${runId}/recommendations/${id}/discard`).then(r=>r.data),
  apply: (runId:string,id:string) => api.post<Run>(`/m8/runs/${runId}/recommendations/${id}/apply`).then(r=>r.data),
  iot: () => api.post("/m6/simulator/scenarios/NORMAL_TICK",{seed:42}),
};
