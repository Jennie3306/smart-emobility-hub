import { api } from "@/shared/api/client";
export interface Hub { id: number; name: string; capacity: number; chargingPoints: number }
export interface Vehicle { id: number; hubId: number | null; battery: number; state: string }
export interface Snapshot { id: string; capturedAt: string; hubs: Hub[]; vehicles: Vehicle[]; deviceStates: {hubId:number;type:string;resourceId:number;state:string;observedAt:string}[]; demandSource: string }
export interface Scenario { name:string; hubId:number; disabled:boolean; injectedRequests:number; durationMins:number; arrivalWindowMins:number; tripMins:number; reroute:boolean; seed:number }
export interface Kpi { requests:number; served:number; failed:number; failurePercent:number; meanWaitMins:number; peakQueue:number; hubs:{hubId:number;name:string;requests:number;served:number;failed:number;meanWaitMins:number;availableVehicles:number;peakParkingPercent:number}[] }
export interface Recommendation { id:string;action:string;fromHubId:number;toHubId:number;amount:number;reason:string;status:string;projectedFailurePercent:number }
export interface Run { id:string;createdAt:string;scenario:Scenario;snapshot:Snapshot;kpi:Kpi;baseline:Kpi;timeline:{minute:number;served:number;waiting:number;failed:number}[];recommendations:Recommendation[];recommendationSource:string;warnings:string[] }
export interface Monitor {lastChecked:string|null;warnings:string[];intervalMins:number;demandConnected:boolean;applyEnabled:boolean;aiConfigured:boolean}
export const simulationApi = {
  snapshot: () => api.get<Snapshot>("/m8/snapshot").then(r=>r.data),
  status: () => api.get<Monitor>("/m8/status").then(r=>r.data),
  list: () => api.get<Run[]>("/m8/runs").then(r=>r.data),
  parse: (text:string) => api.post<{scenario:Scenario;source:string;warnings:string[]}>("/m8/parse",{text}).then(r=>r.data),
  run: (scenario:Scenario,snapshotRunId?:string) => api.post<Run>("/m8/runs",scenario,{params:{snapshotRunId}}).then(r=>r.data),
  discard: (runId:string,id:string) => api.post<Run>(`/m8/runs/${runId}/recommendations/${id}/discard`).then(r=>r.data),
  apply: (runId:string,id:string) => api.post(`/m8/runs/${runId}/recommendations/${id}/apply`),
  iot: () => api.post("/m6/simulator/scenarios/NORMAL_TICK",{seed:42}),
};
