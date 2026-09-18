-- V2: du lieu mau cho demo va test (TV4 quan ly).
-- Tai khoan demo KHONG dat o day: mat khau phai duoc ma hoa bang BCrypt luc chay,
-- nen do DemoUserInitializer (shared/auth) tao khi khoi dong neu bang app_user con rong.

INSERT INTO hub (name, location, parking_slots, charging_points) VALUES
  ('Hub Ga Metro',   'Ga Metro so 1 - DHQG',        40, 8),
  ('Hub KTX Khu A',  'Ky tuc xa Khu A',             30, 6),
  ('Hub KTX Khu B',  'Ky tuc xa Khu B',             30, 6),
  ('Hub BK',         'Truong Dai hoc Bach Khoa',    25, 4),
  ('Hub KHTN',       'Truong DH Khoa hoc Tu nhien', 20, 4);

INSERT INTO rental_vehicle (code, type, battery_level, state, current_hub_id) VALUES
  ('EB-001', 'E_BIKE',    92, 'AVAILABLE',   1),
  ('EB-002', 'E_BIKE',    18, 'LOW_BATTERY', 1),
  ('ES-001', 'E_SCOOTER', 75, 'AVAILABLE',   2),
  ('ES-002', 'E_SCOOTER', 60, 'AVAILABLE',   3),
  ('EB-003', 'E_BIKE',    45, 'AVAILABLE',   4);
