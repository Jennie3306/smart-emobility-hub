package com.hcmut.emobility.m7dispatch.client;

import java.util.Optional;

/** M7 hoi suc chua Hub dich (BR-M7-03). Cai dat: ParkingHubClient, dung quy cho do chung cua M4. */
public interface HubClient {
    Optional<ExternalDtos.HubInfo> checkDestinationCapacity(Long hubId);
}
