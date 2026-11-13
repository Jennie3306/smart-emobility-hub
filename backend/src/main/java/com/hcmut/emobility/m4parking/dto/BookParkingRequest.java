package com.hcmut.emobility.m4parking.dto;

import com.hcmut.emobility.m4parking.domain.ParkingBookingLog;

import java.time.OffsetDateTime;

/**
 * FR-M4-02. vehicleKind PERSONAL can personalVehicleId (xe da dang ky, BR-M4-02);
 * RENTAL can rentalVehicleId (xe thue sinh vien dang giu qua M2).
 * spaceId de trong thi he thong tu chon cho (UC-M4-02 luong thay the 3a).
 * requestCharging = true: chon cho co tru sac va phat ChargingRequestedEvent sang M5.
 */
public record BookParkingRequest(Long hubId, OffsetDateTime startAt, OffsetDateTime endAt,
                                 ParkingBookingLog.VehicleKind vehicleKind, Long personalVehicleId,
                                 Long rentalVehicleId, Long spaceId, Boolean requestCharging,
                                 Integer batteryLevel) { }
