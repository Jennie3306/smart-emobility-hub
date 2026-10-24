// Kieu du lieu tra ve tu /api/m1 — phai trung voi Dtos.java ben backend.

export type AlertType = 'CAPACITY' | 'LOW_VEHICLE';
export type AlertState = 'OPEN' | 'ACKNOWLEDGED' | 'RESOLVED' | 'DISMISSED';

export interface Utilisation {
  parkingPercent: number | null;      // null = hub khong co cho do
  chargingPercent: number | null;
  freeSlots: number;
  totalSlots: number;
  freePoints: number;
  operationalPoints: number;
  dataAnomaly: boolean;
}

export interface HubSummary {
  id: number;
  name: string;
  location: string;
  availableVehicles: number;
  utilisation: Utilisation;
  hasOpenAlert: boolean;
}

export interface Vehicle {
  id: number; code: string; type: string; batteryLevel: number; state: string;
}

export interface HubDetail extends HubSummary {
  vehicles: Vehicle[];
  parkingSlots: { id: number; slotNo: number; state: string }[];
  chargingPoints: { id: number; pointNo: number; state: string }[];
}

export interface Alert {
  id: number;
  hubId: number;
  hubName: string;
  type: AlertType;
  state: AlertState;
  triggerValue: number;
  raisedAt: string;
  acknowledgedBy: string | null;
  acknowledgedAt: string | null;
  closedAt: string | null;
  dismissReason: string | null;
}

export interface Dashboard {
  vehicleCountByState: Record<string, number>;
  totalHubs: number;
  openAlertCount: number;
  hubs: HubSummary[];
  openAlerts: Alert[];
  generatedAt: string;
}

export interface HubStatus {
  hubId: number;
  availableVehicles: number;
  utilisation: Utilisation;
}
