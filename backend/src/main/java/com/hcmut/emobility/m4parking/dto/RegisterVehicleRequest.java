package com.hcmut.emobility.m4parking.dto;

import com.hcmut.emobility.m4parking.domain.PrivateVehicle;

/** FR-M4-01: bien so, loai xe, mau xe; muc pin khong bat buoc. */
public record RegisterVehicleRequest(String licensePlate, PrivateVehicle.VehicleType vehicleType,
                                     String model, Integer batteryLevel) { }
