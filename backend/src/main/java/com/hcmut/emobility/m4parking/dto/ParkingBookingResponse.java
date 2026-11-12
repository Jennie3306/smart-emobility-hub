package com.hcmut.emobility.m4parking.dto;

import com.hcmut.emobility.m4parking.domain.ParkingBookingLog;
import com.hcmut.emobility.m4parking.domain.ParkingSpace;

import java.time.OffsetDateTime;

public record ParkingBookingResponse(Long id, Long reservationId, Long hubId, Long spaceId, String spaceCode,
                                     ParkingSpace.Type spaceType, ParkingSpace.Status spaceStatus,
                                     boolean hasCharger, ParkingBookingLog.VehicleKind vehicleKind,
                                     Long personalVehicleId, OffsetDateTime slotStart, OffsetDateTime slotEnd,
                                     OffsetDateTime checkInDeadline, OffsetDateTime checkInTime,
                                     OffsetDateTime checkOutTime, ParkingBookingLog.Status status,
                                     boolean chargingRequested) {

    public static ParkingBookingResponse of(ParkingBookingLog b, ParkingSpace space) {
        return new ParkingBookingResponse(b.getId(), b.getReservationId(), b.getHubId(), b.getSpaceId(),
                space == null ? null : space.getSpaceCode(),
                space == null ? null : space.getType(),
                space == null ? null : space.getStatus(),
                space != null && space.getChargerPointId() != null,
                b.getVehicleKind(), b.getPersonalVehicleId(), b.getSlotStart(), b.getSlotEnd(),
                b.getCheckInDeadline(), b.getCheckInTime(), b.getCheckOutTime(), b.getStatus(),
                b.isChargingRequested());
    }
}
