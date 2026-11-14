import os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
from lib import *
O=png_dir('m3')
usecase(O+'m3_usecase.png','«subsystem» M3 — Rental Vehicle Lifecycle',
 [('UC-M3-01','Pick up a vehicle'),('UC-M3-04','Validate vehicle state|and reservation'),('UC-M3-02','Return a vehicle'),
  ('UC-M3-05','Handle a full hub'),('UC-M3-03','View current trip'),('UC-M3-06','Mark low battery|from a reading')],
 left=[('stu','Student',False)], right=[('iot','IoT simulator (M6)',True)],
 assoc=[('stu','UC-M3-01'),('stu','UC-M3-02'),('stu','UC-M3-03'),('iot','UC-M3-06')],
 rels=[('UC-M3-01','UC-M3-04','include'),('UC-M3-05','UC-M3-02','extend')], amber=('UC-M3-06',))
statemachine(O+'m3_state.png',
 ['AVAILABLE','RESERVED','IN_USE','LOW_BATTERY','CHARGING','MAINTENANCE','IN_TRANSIT'],
 [('AVAILABLE','RESERVED','ReservationConfirmed (M2)'),
  ('RESERVED','AVAILABLE','ReservationCancelled /\\nReservationExpired (M2)'),
  ('RESERVED','IN_USE','pickUp [owner, battery ≥ 20%]\\n/ VehiclePickedUp'),
  ('IN_USE','AVAILABLE','returnVehicle [space docked,\\nbattery ≥ 20%] / VehicleReturned'),
  ('IN_USE','LOW_BATTERY','returnVehicle [battery < 20%]\\n/ VehicleReturned, VehicleLowBattery'),
  ('AVAILABLE','LOW_BATTERY','battery reading < 20% at a hub\\n/ VehicleLowBattery'),
  ('LOW_BATTERY','CHARGING','ChargingStarted (M5)'),
  ('CHARGING','AVAILABLE','ChargingCompleted (M5)'),
  ('AVAILABLE','IN_TRANSIT','DispatchStarted (M7)'),
  ('IN_TRANSIT','AVAILABLE','DispatchCompleted /\\nDispatchFailed (M7)'),
  ('AVAILABLE','MAINTENANCE','VehicleFailed (M6)', 'color="#c0504d", fontcolor="#c0504d"'),
  ('RESERVED','MAINTENANCE','VehicleFailed', 'color="#c0504d", fontcolor="#c0504d"'),
  ('IN_USE','MAINTENANCE','VehicleFailed', 'color="#c0504d", fontcolor="#c0504d"'),
  ('LOW_BATTERY','MAINTENANCE','VehicleFailed', 'color="#c0504d", fontcolor="#c0504d"'),
  ('CHARGING','MAINTENANCE','VehicleFailed', 'color="#c0504d", fontcolor="#c0504d"'),
  ('MAINTENANCE','AVAILABLE','repaired', 'style=dashed')],
 initial='AVAILABLE', rankdir='TB', extra='ranksep=0.8; nodesep=0.9;')
N={}
N['VehicleController']=klass('VehicleController','REST, /api/m3/vehicles',attrs=None,ops=[
 '+list(): List<VehicleResponse>','+currentTrip(p: Principal): CurrentTripResponse',
 '+pickUp(id: Long, p: Principal): void','+returnVehicle(id: Long, req: ReturnVehicleRequest, p: Principal): void'])
N['VehicleService']=klass('VehicleService','service',attrs=['-lowBatteryThreshold: int = 20'],ops=[
 '+pickUp(vehicleId: Long, userId: Long): void','+returnVehicle(vehicleId: Long, hubId: Long, batteryLevel: int, userId: Long): void',
 '+getCurrentTrip(userId: Long): CurrentTripResponse','-alternativeHubs(fullHubId: Long): String'])
N['VehicleEventHandler']=klass('VehicleEventHandler','event listener, @Order(0)',attrs=None,ops=[
 '+onDeviceReadingReported(e): void','+onVehicleFailed(e): void','+onChargingStarted(e) / onChargingCompleted(e): void',
 '+onDispatchStarted(e) / onDispatchCompleted(e) / onDispatchFailed(e): void','+onReservationExpired(e): void'],**SCHED)
N['ReservationVehicleHandler']=klass('ReservationVehicleHandler','event listener',attrs=None,ops=[
 '+confirmed(e: ReservationConfirmedEvent): void','+checkedIn(e: ReservationCheckedInEvent): void','+cancelled(e: ReservationCancelledEvent): void'],**SCHED)
N['RentalVehicle']=klass('RentalVehicle','entity, rental_vehicle',attrs=['-id: Long','-code: String','-type: VehicleType','-batteryLevel: int',
 '-state: VehicleState','-currentHubId: Long','-reservedByUserId: Long','-version: Long'],ops=[
 '+changeState(target: VehicleState): void','+updateBattery(level: int): void','+reserve(userId: Long): void','+clearReservation(): void'])
N['RentalSession']=klass('RentalSession','entity, rental_session',attrs=['-id: Long','-vehicleId: Long','-userId: Long',
 '-startTime: LocalDateTime','-endTime: LocalDateTime','-status: SessionStatus'],ops=['+start(vehicleId, userId): RentalSession {static}','+complete(): void'])
N['VehicleState']=klass('VehicleState','enumeration',attrs=['AVAILABLE','RESERVED','IN_USE','LOW_BATTERY','CHARGING','MAINTENANCE','IN_TRANSIT'],ops=['+canMoveTo(target): boolean'],**ENUM)
N['SessionStatus']=klass('SessionStatus','enumeration',attrs=['ACTIVE','COMPLETED'],ops=None,**ENUM)
N['RentalVehicleRepository']=klass('RentalVehicleRepository','JpaRepository',attrs=None,ops=['+findById(id): Optional<RentalVehicle>'],**IFACE)
N['RentalSessionRepository']=klass('RentalSessionRepository','JpaRepository',attrs=None,ops=['+findByVehicleIdAndStatus(id, s)','+findByUserIdAndStatus(id, s)'],**IFACE)
N['ParkingDock']=klass('ParkingDock','interface, shared contract (owner M4)',attrs=None,ops=['+dockRentalVehicle(vehicleId, hubId): Long','+undockRentalVehicle(vehicleId): void','+vacancies(): List<HubVacancy>'],**IFACE)
N['EventPublisher']=klass('EventPublisher','shared',attrs=None,ops=['+publish(e: DomainEvent): void'],**EXT)
N['CurrentTripResponse']=klass('CurrentTripResponse','record, DTO',attrs=['sessionId, vehicleId, vehicleCode,','vehicleState, batteryLevel, hubId,','startTime'],ops=None,**EXT)
classdiagram(O+'m3_class.png',N,[
 ('VehicleController','VehicleService','assoc'),('ReservationVehicleHandler','VehicleService','assoc','pickUp'),
 ('VehicleService','RentalVehicleRepository','assoc'),('VehicleService','RentalSessionRepository','assoc'),
 ('VehicleService','ParkingDock','assoc','dock / undock'),('VehicleService','EventPublisher','dep','publishes'),
 ('VehicleEventHandler','RentalVehicleRepository','assoc'),('VehicleEventHandler','EventPublisher','dep'),
 ('RentalVehicleRepository','RentalVehicle','dep'),('RentalSessionRepository','RentalSession','dep'),
 ('RentalVehicle','VehicleState','assoc','1'),('RentalSession','SessionStatus','assoc','1'),
 ('RentalSession','RentalVehicle','assoc','0..* → 1'),('VehicleService','CurrentTripResponse','dep','creates')],
 extra='splines=true;')
mermaid(O+'m3_sequence.png','''sequenceDiagram
autonumber
actor S as Student
participant RC as ReservationController (M2)
participant RS as ReservationService (M2)
participant EP as EventPublisher
participant H as ReservationVehicleHandler (M3)
participant VS as VehicleService (M3)
participant PD as ParkingDock (M4)
S->>RC: POST /api/m2/reservations/{id}/check-in
RC->>RS: checkIn(id, userId)
RS->>RS: reservation.checkIn(now) [now < expiresAt]
RS->>EP: publish(ReservationCheckedInEvent)
EP->>H: checkedIn(event) (same transaction)
H->>VS: pickUp(vehicleId, userId)
alt state ≠ RESERVED
  VS-->>H: VEHICLE_NOT_AVAIL
else battery < 20%
  VS-->>H: BATTERY_TOO_LOW
else reservedByUserId ≠ userId
  VS-->>H: RESERVATION_MISMATCH
else valid
  VS->>PD: undockRentalVehicle(vehicleId)
  VS->>VS: changeState(IN_USE), clearReservation()
  VS->>VS: RentalSession.start(vehicleId, userId)
  VS->>EP: publish(VehiclePickedUpEvent)
end
Note over RS,VS: an exception rolls back the whole transaction:<br/>the reservation stays CONFIRMED and the vehicle RESERVED
RC-->>S: 200 CHECKED_IN, or 4xx with the error code
''', width=1500)

# ---------------------------------------------------------------- activity: UC-M3-02 return a vehicle
# VehicleService.returnVehicle: one transaction; M4 docks the vehicle, M2 closes the reservation (FR-M2-09).
src=activity_header('UC-M3-02 — Return a vehicle at a hub (M3)')+f'''
a [label="Student chooses the hub\\nand presses Return", {ACT}];
d1 [label="Active trip of\\nthis student?", {DEC}];
x1 [label="SESSION_NOT_FOUND (404) /\\nTRIP_OWNER_MISMATCH (403)", {ERR}];
d2 [label="Vehicle IN_USE?", {DEC}];
x2 [label="INVALID_STATE (400)", {ERR}];
dock [label="M4 ParkingDock.dockRentalVehicle:\\nlock and take a free space\\nin the shared pool (BR-M3-03)", {ACT}];
d3 [label="Free space\\nat the hub?", {DEC}];
x3 [label="HUB_FULL (409)\\nwith hubs that still have room;\\nnothing changes", {ERR}];
upd [label="Record battery level,\\nset current hub", {ACT}];
d4 [label="Battery below\\nthreshold (20 %)?", {DEC}];
low [label="State LOW_BATTERY", {ACT}];
av [label="State AVAILABLE", {ACT}];
m [shape=diamond, label="", width=0.3, height=0.3, style=filled, fillcolor="#ffffff"];
done [label="Complete the RentalSession", {ACT}];
ev [label="Publish VehicleReturnedEvent\\n(M2 completes the reservation,\\nM1 recounts, M5 may queue charging)", {ACT}];
ev2 [label="Also publish\\nVehicleLowBatteryEvent", {ACT}];
ok [label="Commit; trip summary\\nshown to the student", {OK}];
e {END}; e2 {END};
s -> a -> d1; d1 -> x1 [label="no"]; d1 -> d2 [label="yes"];
d2 -> x2 [label="no"]; d2 -> dock [label="yes"]; dock -> d3;
d3 -> x3 [label="no"]; d3 -> upd [label="yes"]; upd -> d4;
d4 -> low [label="yes"]; d4 -> av [label="no"]; low -> m; av -> m; m -> done -> ev;
ev -> ev2 [label="[low battery]"]; ev -> ok [label="[otherwise]"]; ev2 -> ok; ok -> e;
x1 -> e2; x2 -> e2; x3 -> e2;
{{rank=same; low; av}}
}}'''
run_dot(src,O+'m3_activity.png',dpi=150)
print('ok')
