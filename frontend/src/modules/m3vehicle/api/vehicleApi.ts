import { api } from "@/shared/api/client";

export interface Vehicle {
  id: number;
  code: string;
  type: "E_BIKE" | "E_SCOOTER";
  batteryLevel: number;
  state: string;
  hubId: number | null;
}

export const vehicleApi = {
  list: () => api.get<{ data: Vehicle[] }>("/m3/vehicles").then((r) => r.data.data),
  pickUp: (id: number) => api.post(`/m3/vehicles/${id}/pick-up`),
  returnVehicle: (id: number, hubId: number, batteryLevel: number) =>
    api.post(`/m3/vehicles/${id}/return`, { hubId, batteryLevel }),
};
