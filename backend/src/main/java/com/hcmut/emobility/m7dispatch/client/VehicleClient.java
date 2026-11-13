package com.hcmut.emobility.m7dispatch.client;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** M7 hoi trang thai xe (du lieu thuoc M3). Chi doc. */
public interface VehicleClient {
    Optional<ExternalDtos.VehicleInfo> checkVehicle(Long vehicleId);

    /** AVAILABLE vehicles standing at the Hub, highest battery first (then by id). */
    List<Long> availableVehiclesAt(Long hubId);

    /** Number of AVAILABLE vehicles per Hub; Hubs without any are absent. */
    Map<Long, Integer> availableCountPerHub();
}
