package com.hcmut.emobility.shared.contracts;

import java.util.List;

/**
 * Shared parking pool of a Hub (Appendix A v2, decided 12/11/2026).
 *
 * A Hub has one pool of parking spaces, owned by M4. Rental vehicles standing at the Hub occupy a space in
 * that pool just like personal vehicles, so "the Hub is full" means the same thing for a student returning a
 * rental vehicle (M3), an Operator moving vehicles (M7) and a student booking a space (M4), and the dashboard
 * (M1) and What-if snapshot (M8) count the same spaces.
 *
 * Implemented by M4. M3 and M7 call it synchronously because they need an immediate yes / no that cannot be
 * overtaken by a concurrent request (the last free space): the implementation locks the space row.
 * This is the same pattern as M2's ReservationCore used by M4.
 */
public interface ParkingDock {

    /**
     * Holds a free space at the Hub for a rental vehicle that is arriving (returned by a student or
     * delivered by a redistribution task). Prefers spaces without a charging point so those stay available
     * for vehicles that need charging. Idempotent: if the vehicle already holds a space at this Hub, that
     * space is returned; a space it still holds at another Hub is released first.
     *
     * @return id of the parking space now OCCUPIED by the vehicle
     * @throws com.hcmut.emobility.shared.common.BusinessException HUB_FULL (409) when no free space is left,
     *         HUB_NOT_FOUND (404)
     */
    Long dockRentalVehicle(Long vehicleId, Long hubId);

    /**
     * Releases the space a rental vehicle occupies because it leaves the Hub (picked up by a student or
     * taken away by a redistribution task). Does nothing when the vehicle holds no space.
     */
    void undockRentalVehicle(Long vehicleId);

    /** Free spaces a rental vehicle could use at the Hub right now. HUB_NOT_FOUND (404). */
    int freeSpacesForRental(Long hubId);

    /** Free spaces per Hub, used to suggest an alternative Hub when the chosen one is full. */
    List<HubVacancy> vacancies();

    record HubVacancy(Long hubId, String hubName, int freeSpaces) { }
}
