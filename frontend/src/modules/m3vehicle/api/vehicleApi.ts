import axios from "axios";
import { api } from "@/shared/api/client";

export interface Vehicle {
  id: number;
  code: string;
  type: "E_BIKE" | "E_SCOOTER";
  batteryLevel: number;
  state: string;
  hubId: number | null;
}

/** FR-M3-03: the student's trip in progress (GET /api/m3/vehicles/current-trip). */
export interface CurrentTrip {
  sessionId: number;
  vehicleId: number;
  vehicleCode: string;
  vehicleState: string;
  batteryLevel: number;
  /** Hub where the vehicle stands; null while it is out on the trip (M3 clears it at pick-up). */
  hubId: number | null;
  /** Server local time without an offset, e.g. "2026-11-13T17:50:18.123". */
  startTime: string;
}

export const vehicleApi = {
  list: () => api.get<{ data: Vehicle[] }>("/m3/vehicles").then((r) => r.data.data),
  pickUp: (id: number) => api.post(`/m3/vehicles/${id}/pick-up`),
  returnVehicle: (id: number, hubId: number, batteryLevel: number) =>
    api.post(`/m3/vehicles/${id}/return`, { hubId, batteryLevel }),
  /** Resolves to null when the student has no active trip (404 NO_ACTIVE_TRIP). */
  currentTrip: () =>
    api.get<{ data: CurrentTrip }>("/m3/vehicles/current-trip")
      .then((r) => r.data.data)
      .catch((e: unknown) => {
        if (axios.isAxiosError(e) && e.response?.status === 404) return null;
        throw e;
      }),
};
