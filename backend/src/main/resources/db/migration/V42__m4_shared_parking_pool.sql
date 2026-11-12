-- V42 (M4, Appendix A v2 decided 12/11/2026): one parking pool per Hub, shared by rental and personal vehicles.
-- A rental vehicle standing at a Hub occupies a parking space (BR-M4-08), so "Hub full" means the same thing
-- for returning a rental vehicle (M3), redistribution (M7), booking a space (M4), the dashboard (M1) and the
-- What-if snapshot (M8). M4 changes these rows only through ParkingDock (shared contract).

ALTER TABLE parking_space
    ADD COLUMN docked_vehicle_id BIGINT UNIQUE REFERENCES rental_vehicle(id);

-- OCCUPIED now means: a checked-in booking (reservation) OR a docked rental vehicle - exactly one of them.
ALTER TABLE parking_space DROP CONSTRAINT ck_parking_space_reservation;
ALTER TABLE parking_space ADD CONSTRAINT ck_parking_space_reservation CHECK (
    (status = 'FREE'     AND current_reservation_id IS NULL     AND docked_vehicle_id IS NULL)
 OR (status = 'RESERVED' AND current_reservation_id IS NOT NULL AND docked_vehicle_id IS NULL)
 OR (status = 'OCCUPIED' AND ((current_reservation_id IS NULL) <> (docked_vehicle_id IS NULL)))
 OR (status = 'BLOCKED'  AND NOT (current_reservation_id IS NOT NULL AND docked_vehicle_id IS NOT NULL)));

CREATE INDEX idx_parking_space_docked ON parking_space(hub_id) WHERE docked_vehicle_id IS NOT NULL;

-- Rental vehicles already at a Hub are docked at startup by ParkingStartup (RentalDockService.dockParkedFleet),
-- not here: the choice of space follows the same rule as a live return (spaces without a charger first).
