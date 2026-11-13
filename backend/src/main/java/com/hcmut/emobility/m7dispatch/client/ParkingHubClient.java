package com.hcmut.emobility.m7dispatch.client;

import com.hcmut.emobility.m7dispatch.domain.RedistributionTaskStatus;
import com.hcmut.emobility.m7dispatch.repository.RedistributionTaskRepository;
import com.hcmut.emobility.shared.common.BusinessException;
import com.hcmut.emobility.shared.contracts.ParkingDock;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Optional;

/**
 * BR-M7-03 (Appendix A v2): the destination Hub's capacity is its free spaces in the shared parking pool owned
 * by M4, minus the vehicles M7 has already sent there (active tasks), so two tasks cannot both count on the
 * last space.
 */
@Component
public class ParkingHubClient implements HubClient {

    private final ParkingDock parkingDock;
    private final RedistributionTaskRepository tasks;

    public ParkingHubClient(ParkingDock parkingDock, RedistributionTaskRepository tasks) {
        this.parkingDock = parkingDock;
        this.tasks = tasks;
    }

    @Override
    public Optional<ExternalDtos.HubInfo> checkDestinationCapacity(Long hubId) {
        int free;
        try {
            free = parkingDock.freeSpacesForRental(hubId);
        } catch (BusinessException ex) {
            if ("HUB_NOT_FOUND".equals(ex.getCode())) return Optional.empty();
            throw ex;
        }
        long incoming = tasks.countByDestinationHubIdAndStatusIn(hubId,
                EnumSet.of(RedistributionTaskStatus.CREATED, RedistributionTaskStatus.IN_PROGRESS));
        return Optional.of(new ExternalDtos.HubInfo(hubId, free, (int) incoming));
    }
}
