-- V30 (M3, demo data): rental fleet large enough for redistribution (M7) and What-if dispatch (M8).
-- V2 seeded only 5 vehicles, so every Hub had 0-1 available vehicles: M1 raised LOW_VEHICLE everywhere,
-- M7 had nothing to move and M8 could never apply a dispatch (the source must keep at least one vehicle).
--
-- Deliberate imbalance for the demo story "students pour into the Metro station; move vehicles from the
-- dormitories": Hub Ga Metro (1) stays short (EB-001 available, EB-002 low battery) so M1 keeps one
-- LOW_VEHICLE alert there; the other Hubs get 4-10 available vehicles (M1 clears LOW_VEHICLE at >= 4).
-- Hub Ga Metro gets no new vehicle, so its parking stays at 31/40 (77%) for the M4/M1 capacity scenario.
-- Each vehicle takes a parking space at startup (ParkingStartup -> RentalDockService.dockParkedFleet, BR-M4-08).
INSERT INTO rental_vehicle (code, type, battery_level, state, current_hub_id) VALUES
  -- Hub KTX Khu A (2): 9 new + ES-001 = 10 available
  ('EB-101', 'E_BIKE',    88, 'AVAILABLE', 2),
  ('EB-102', 'E_BIKE',    76, 'AVAILABLE', 2),
  ('EB-103', 'E_BIKE',    64, 'AVAILABLE', 2),
  ('EB-104', 'E_BIKE',    95, 'AVAILABLE', 2),
  ('EB-105', 'E_BIKE',    52, 'AVAILABLE', 2),
  ('ES-101', 'E_SCOOTER', 83, 'AVAILABLE', 2),
  ('ES-102', 'E_SCOOTER', 71, 'AVAILABLE', 2),
  ('ES-103', 'E_SCOOTER', 90, 'AVAILABLE', 2),
  ('ES-104', 'E_SCOOTER', 58, 'AVAILABLE', 2),
  -- Hub KTX Khu B (3): 4 new + ES-002 = 5 available
  ('EB-201', 'E_BIKE',    81, 'AVAILABLE', 3),
  ('EB-202', 'E_BIKE',    67, 'AVAILABLE', 3),
  ('ES-201', 'E_SCOOTER', 74, 'AVAILABLE', 3),
  ('ES-202', 'E_SCOOTER', 49, 'AVAILABLE', 3),
  -- Hub BK (4): 4 new + EB-003 = 5 available
  ('EB-301', 'E_BIKE',    86, 'AVAILABLE', 4),
  ('EB-302', 'E_BIKE',    62, 'AVAILABLE', 4),
  ('ES-301', 'E_SCOOTER', 79, 'AVAILABLE', 4),
  ('ES-302', 'E_SCOOTER', 55, 'AVAILABLE', 4),
  -- Hub KHTN (5): 4 new = 4 available
  ('EB-401', 'E_BIKE',    84, 'AVAILABLE', 5),
  ('EB-402', 'E_BIKE',    69, 'AVAILABLE', 5),
  ('ES-401', 'E_SCOOTER', 77, 'AVAILABLE', 5),
  ('ES-402', 'E_SCOOTER', 60, 'AVAILABLE', 5);
