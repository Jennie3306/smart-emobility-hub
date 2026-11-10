package com.hcmut.emobility.m7dispatch.client;

import java.util.Optional;

/** M7 hoi trang thai xe (du lieu thuoc M3). Chi doc. */
public interface VehicleClient {
    Optional<ExternalDtos.VehicleInfo> checkVehicle(Long vehicleId);
}
