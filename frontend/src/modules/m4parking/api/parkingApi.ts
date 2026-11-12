import { api, type ApiError } from "@/shared/api/client";
import { englishHubLabels } from "@/shared/api/hubLabels";
import type { AxiosError } from "axios";

export type SpaceStatus = "FREE" | "RESERVED" | "OCCUPIED" | "BLOCKED";
export type SpaceType = "CAR" | "BIKE";
export type BookingStatus = "ACTIVE" | "COMPLETED" | "NO_SHOW" | "CANCELLED";
export type VehicleKind = "PERSONAL" | "RENTAL";
export type PersonalVehicleType = "E_MOTORBIKE" | "E_BIKE" | "E_SCOOTER" | "E_CAR";

export interface ParkingHub {
  hubId: number;
  hubName: string;
  capacity: number;
  free: number;
  reserved: number;
  occupied: number;
  blocked: number;
  freeWithCharger: number;
}

export interface ParkingSpace {
  id: number;
  hubId: number;
  spaceCode: string;
  type: SpaceType;
  status: SpaceStatus;
  chargerPointId: number | null;
  blockedReason: "CHARGER_FAILURE" | "OPERATOR" | null;
}

export interface Availability {
  hubId: number;
  startAt: string;
  endAt: string;
  capacity: number;
  activeBookings: number;
  remaining: number;
  freeSpaces: number;
}

export interface PersonalVehicle {
  id: number;
  licensePlate: string;
  vehicleType: PersonalVehicleType;
  model: string;
  batteryLevel: number | null;
  status: "ACTIVE" | "INACTIVE";
  createdAt: string;
}

export interface ParkingBooking {
  id: number;
  reservationId: number;
  hubId: number;
  spaceId: number;
  spaceCode: string | null;
  spaceType: SpaceType | null;
  spaceStatus: SpaceStatus | null;
  hasCharger: boolean;
  vehicleKind: VehicleKind;
  personalVehicleId: number | null;
  slotStart: string;
  slotEnd: string;
  checkInDeadline: string;
  checkInTime: string | null;
  checkOutTime: string | null;
  status: BookingStatus;
  chargingRequested: boolean;
}

export interface BookingRequest {
  hubId: number;
  startAt: string;
  endAt: string;
  vehicleKind: VehicleKind;
  personalVehicleId?: number;
  rentalVehicleId?: number;
  spaceId?: number;
  requestCharging: boolean;
  batteryLevel?: number;
}

export const parkingApi = {
  hubs: () => api.get<ParkingHub[]>("/m4/hubs").then(r => englishHubLabels(r.data)),
  spaces: (hubId: number) => api.get<ParkingSpace[]>(`/m4/hubs/${hubId}/spaces`).then(r => r.data),
  availability: (hubId: number, startAt: string, endAt: string) =>
    api.get<Availability>(`/m4/hubs/${hubId}/availability`, { params: { startAt, endAt } }).then(r => r.data),

  myVehicles: () => api.get<PersonalVehicle[]>("/m4/vehicles/mine").then(r => r.data),
  registerVehicle: (body: { licensePlate: string; vehicleType: PersonalVehicleType; model: string; batteryLevel?: number }) =>
    api.post<PersonalVehicle>("/m4/vehicles", body).then(r => r.data),
  updateVehicle: (id: number, body: { vehicleType: PersonalVehicleType; model: string; batteryLevel?: number }) =>
    api.put<PersonalVehicle>(`/m4/vehicles/${id}`, body).then(r => r.data),
  deactivateVehicle: (id: number) => api.post<PersonalVehicle>(`/m4/vehicles/${id}/deactivate`).then(r => r.data),
  activateVehicle: (id: number) => api.post<PersonalVehicle>(`/m4/vehicles/${id}/activate`).then(r => r.data),

  myBookings: () => api.get<ParkingBooking[]>("/m4/bookings/mine").then(r => r.data),
  book: (body: BookingRequest) => api.post<ParkingBooking>("/m4/bookings", body).then(r => r.data),
  checkIn: (id: number) => api.post<ParkingBooking>(`/m4/bookings/${id}/check-in`).then(r => r.data),
  checkOut: (id: number) => api.post<ParkingBooking>(`/m4/bookings/${id}/check-out`).then(r => r.data),
  cancel: (id: number) => api.post<ParkingBooking>(`/m4/bookings/${id}/cancel`).then(r => r.data),

  hubBookings: (hubId: number) => api.get<ParkingBooking[]>(`/m4/hubs/${hubId}/bookings`).then(r => r.data),
  block: (spaceId: number) => api.post<ParkingSpace>(`/m4/spaces/${spaceId}/block`).then(r => r.data),
  unblock: (spaceId: number) => api.post<ParkingSpace>(`/m4/spaces/${spaceId}/unblock`).then(r => r.data),
};

export function parkingErrorMessage(error: unknown): string {
  const data = (error as AxiosError<ApiError>).response?.data;
  switch (data?.code) {
    case "HUB_FULL": return "This Hub is fully booked for that time. Try another time or another Hub.";
    case "NO_SPACE_AVAILABLE": return data?.message ?? "No suitable space is free at this Hub. Try another Hub.";
    case "SPACE_NOT_AVAILABLE": return "Someone just booked that space. Choose another one.";
    case "RESOURCE_CONFLICT": return "Someone booked that space at the same moment. Please try again.";
    case "SPACE_BLOCKED": return "This space is blocked because of an incident. Choose another one.";
    case "SPACE_HAS_NO_CHARGER": return "To charge, choose a space with a charging point (⚡).";
    case "SPACE_TYPE_MISMATCH": return data?.message ?? "This space does not fit your vehicle type.";
    case "DUPLICATE_BOOKING": return "You already have a booking at this Hub in the same time window.";
    case "RESERVATION_LIMIT_EXCEEDED": return "You already have an active parking booking. Finish or cancel it first.";
    case "VEHICLE_NOT_REGISTERED": return "Register this vehicle under My vehicles before booking.";
    case "VEHICLE_INACTIVE": return "This vehicle is inactive. Reactivate it under My vehicles.";
    case "RENTAL_VEHICLE_NOT_YOURS": return "You can only book parking for a rental vehicle you have reserved or picked up (M2).";
    case "BATTERY_LEVEL_REQUIRED": return "Enter the battery level to request charging.";
    case "BATTERY_ALREADY_FULL": return "Charging is not accepted at 90% battery or higher.";
    case "INVALID_TIME_SLOT":
    case "BOOKING_TOO_FAR_AHEAD":
    case "INVALID_BOOKING_DURATION": return data?.message ?? "Check the arrival and leaving time.";
    case "CHECK_IN_TOO_EARLY": return "Check-in opens 15 minutes before your booked time.";
    case "CHECK_IN_EXPIRED":
    case "RESERVATION_EXPIRED": return "The 15-minute check-in window has passed. The space has been released.";
    case "ALREADY_CHECKED_IN": return data?.message ?? "You have already checked in.";
    case "NOT_CHECKED_IN": return "Check in before checking out.";
    case "BOOKING_NOT_ACTIVE": return "This booking is no longer active. Refresh the list.";
    case "LICENSE_PLATE_EXISTS": return "This license plate is already registered.";
    case "INVALID_LICENSE_PLATE": return "License plate must have 5–20 letters or digits (dots and dashes allowed).";
    case "VEHICLE_IN_USE": return "This vehicle has an active booking. Finish it before removing the vehicle.";
    case "SPACE_STILL_IN_USE": return "A vehicle is still parked in this space. Unblock it after check-out.";
    case "SPACE_ALREADY_BLOCKED": return "This space is already blocked.";
    case "SPACE_NOT_BLOCKED": return "This space is not blocked.";
    case "ACCESS_DENIED": return "You do not have permission for this action.";
    case "VALIDATION_FAILED": return data?.message ?? "Check the entered values and try again.";
    default: return data?.message ?? "Unable to complete the parking request. Please try again.";
  }
}

export async function currentRole(): Promise<"STUDENT" | "OPERATOR"> {
  const response = await api.get<{ role: "STUDENT" | "OPERATOR" }>("/auth/me");
  return response.data.role;
}
