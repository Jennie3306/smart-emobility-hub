package com.hcmut.emobility.m7dispatch.client;

import java.util.Optional;

/** M7 hoi suc chua Hub dich. Thay bang API cua M4 khi M4 san sang. */
public interface HubClient {
    Optional<ExternalDtos.HubInfo> checkDestinationCapacity(Long hubId);
}
