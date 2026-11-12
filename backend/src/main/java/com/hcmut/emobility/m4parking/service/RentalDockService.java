package com.hcmut.emobility.m4parking.service;

import com.hcmut.emobility.m4parking.domain.ParkingSpace;
import com.hcmut.emobility.m4parking.repository.ParkingSpaceRepository;
import com.hcmut.emobility.shared.common.BusinessException;
import com.hcmut.emobility.shared.contracts.ParkingDock;
import com.hcmut.emobility.shared.events.EventPublisher;
import com.hcmut.emobility.shared.events.ResourceStateUpdatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * BR-M4-08 (Appendix A v2): one parking pool per Hub, shared by rental and personal vehicles.
 * M4 implements the shared {@link ParkingDock} contract; M3 calls it when a student returns or picks up a
 * rental vehicle, M7 when a redistribution task starts or ends. Every change locks the space row
 * (PESSIMISTIC_WRITE), so two vehicles arriving for the last free space cannot both get it.
 */
@Service
public class RentalDockService implements ParkingDock {

    private static final Logger log = LoggerFactory.getLogger(RentalDockService.class);

    /** Rental vehicles are E_BIKE / E_SCOOTER, which use BIKE spaces. */
    static final ParkingSpace.Type RENTAL_SPACE_TYPE = ParkingSpace.Type.BIKE;

    private final ParkingSpaceRepository spaces;
    private final EventPublisher events;
    private final JdbcTemplate jdbc;

    public RentalDockService(ParkingSpaceRepository spaces, EventPublisher events, JdbcTemplate jdbc) {
        this.spaces = spaces;
        this.events = events;
        this.jdbc = jdbc;
    }

    /**
     * Pre: the Hub exists. Post: one space at the Hub is OCCUPIED with dockedVehicleId = vehicleId and
     * ResourceStateUpdatedEvent is published. Order of choice: spaces without a charging point first (kept
     * for vehicles that need charging), then charger spaces from the highest code down, so P-01, which the
     * demo charger-failure scenario uses, is taken last.
     * Errors: HUB_FULL (409), HUB_NOT_FOUND (404). A refusal changes nothing, so it does not mark the caller's
     * transaction rollback-only (noRollbackFor): M7 may catch HUB_FULL and carry on.
     */
    @Override
    @Transactional(noRollbackFor = BusinessException.class)
    public Long dockRentalVehicle(Long vehicleId, Long hubId) {
        requireHub(hubId);
        Optional<ParkingSpace> current = spaces.findByDockedVehicleId(vehicleId);
        if (current.isPresent() && current.get().getHubId().equals(hubId)) return current.get().getId();
        List<ParkingSpace> candidates = new ArrayList<>(
                spaces.findByHubIdAndTypeAndStatusOrderById(hubId, RENTAL_SPACE_TYPE, ParkingSpace.Status.FREE));
        candidates.sort(Comparator
                .comparing((ParkingSpace s) -> s.getChargerPointId() == null ? 0 : 1)
                .thenComparing(ParkingSpace::getId, Comparator.reverseOrder()));
        for (ParkingSpace candidate : candidates) {
            ParkingSpace space = spaces.lockById(candidate.getId()).orElse(null);
            if (space == null || space.getStatus() != ParkingSpace.Status.FREE) continue;
            // Only now, with a space secured, leave the space still held at another Hub.
            if (current.isPresent()) undockRentalVehicle(vehicleId);
            space.dock(vehicleId);
            spaces.save(space);
            publish(space);
            log.info("[M4] Rental vehicle {} parked on space {} at hub {}", vehicleId, space.getSpaceCode(), hubId);
            return space.getId();
        }
        throw new BusinessException("HUB_FULL", "No free parking space is left at this Hub");
    }

    /** Post: the vehicle's space (if any) is FREE again, or stays BLOCKED without the vehicle. */
    @Override
    @Transactional
    public void undockRentalVehicle(Long vehicleId) {
        spaces.findByDockedVehicleId(vehicleId).ifPresent(found -> {
            ParkingSpace space = spaces.lockById(found.getId()).orElse(null);
            if (space == null || !vehicleId.equals(space.getDockedVehicleId())) return;
            space.undock(vehicleId);
            spaces.save(space);
            publish(space);
            log.info("[M4] Rental vehicle {} left space {} at hub {}", vehicleId, space.getSpaceCode(), space.getHubId());
        });
    }

    @Override
    @Transactional(readOnly = true)
    public int freeSpacesForRental(Long hubId) {
        requireHub(hubId);
        return spaces.findByHubIdAndTypeAndStatusOrderById(hubId, RENTAL_SPACE_TYPE, ParkingSpace.Status.FREE).size();
    }

    @Override
    @Transactional(readOnly = true)
    public List<HubVacancy> vacancies() {
        return jdbc.query("""
                select h.id, h.name, count(ps.id) as free_spaces
                from hub h
                left join parking_space ps on ps.hub_id = h.id and ps.type = ? and ps.status = 'FREE'
                group by h.id, h.name
                order by h.id
                """, (rs, row) -> new HubVacancy(rs.getLong(1), rs.getString(2), rs.getInt(3)),
                RENTAL_SPACE_TYPE.name());
    }

    /**
     * Startup reconciliation: every rental vehicle that stands at a Hub (not IN_USE / IN_TRANSIT) holds a space.
     * Covers databases created before V42 and the V2 seed. A vehicle that finds its Hub full is logged and left
     * without a space; the Hub then shows as full anyway. Returns the number of vehicles docked.
     */
    @Transactional
    public int dockParkedFleet() {
        List<long[]> parked = jdbc.query("""
                select v.id, v.current_hub_id from rental_vehicle v
                where v.current_hub_id is not null and v.state not in ('IN_USE', 'IN_TRANSIT')
                  and not exists (select 1 from parking_space ps where ps.docked_vehicle_id = v.id)
                order by v.id
                """, (rs, row) -> new long[] { rs.getLong(1), rs.getLong(2) });
        int docked = 0;
        for (long[] vehicle : parked) {
            try {
                dockRentalVehicle(vehicle[0], vehicle[1]);
                docked++;
            } catch (BusinessException ex) {
                log.warn("[M4] Rental vehicle {} at hub {} has no parking space: {}", vehicle[0], vehicle[1],
                        ex.getMessage());
            }
        }
        return docked;
    }

    private void requireHub(Long hubId) {
        Integer found = hubId == null ? 0
                : jdbc.queryForObject("select count(*) from hub where id = ?", Integer.class, hubId);
        if (found == null || found == 0) {
            throw new BusinessException("HUB_NOT_FOUND", "Hub not found: " + hubId, HttpStatus.NOT_FOUND);
        }
    }

    private void publish(ParkingSpace space) {
        events.publish(new ResourceStateUpdatedEvent(space.getHubId(), "PARKING_SPACE", space.getId(),
                space.getStatus().name()));
    }
}
