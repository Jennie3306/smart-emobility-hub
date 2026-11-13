package com.hcmut.emobility.m4parking.dto;

import com.hcmut.emobility.m4parking.domain.PrivateVehicle;

/** Sua xe ca nhan. Bien so khong sua duoc (khoa duy nhat); muon doi bien so thi dang ky xe moi. */
public record UpdateVehicleRequest(PrivateVehicle.VehicleType vehicleType, String model, Integer batteryLevel) { }
