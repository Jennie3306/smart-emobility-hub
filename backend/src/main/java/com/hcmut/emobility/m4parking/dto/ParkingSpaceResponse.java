package com.hcmut.emobility.m4parking.dto;

import com.hcmut.emobility.m4parking.domain.ParkingSpace;

public record ParkingSpaceResponse(Long id, Long hubId, String spaceCode, ParkingSpace.Type type,
                                   ParkingSpace.Status status, Long chargerPointId,
                                   ParkingSpace.BlockReason blockedReason, Long dockedVehicleId) {

    public static ParkingSpaceResponse from(ParkingSpace s) {
        return new ParkingSpaceResponse(s.getId(), s.getHubId(), s.getSpaceCode(), s.getType(), s.getStatus(),
                s.getChargerPointId(), s.getBlockedReason(), s.getDockedVehicleId());
    }
}
