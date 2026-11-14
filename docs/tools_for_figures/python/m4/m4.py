import os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
from lib import *
O=png_dir('m4')
usecase(O+'m4_usecase.png','«subsystem» M4 — Parking and Personal Vehicles',
 [('UC-M4-01','Register a personal vehicle'),('UC-M4-02','Book a parking space'),('UC-M4-06','Request charging|with the booking'),
  ('UC-M4-03','Check in / check out'),('UC-M4-05','Block / unblock a space'),('UC-M4-04','Release no-show|and overstay'),
  ('UC-M4-07','Dock a rental vehicle|in the shared pool')],
 left=[('stu','Student',False),('op','Operator',False)], right=[('tim','Timer',True),('m3','M3 (return / pick-up)',True),('m6','M5 / M6 (charger state)',True)],
 assoc=[('stu','UC-M4-01'),('stu','UC-M4-02'),('stu','UC-M4-03'),('op','UC-M4-05'),('tim','UC-M4-04'),('m3','UC-M4-07'),('m6','UC-M4-05')],
 rels=[('UC-M4-06','UC-M4-02','extend')], amber=('UC-M4-04','UC-M4-07'))
statemachine(O+'m4_state.png',['FREE','RESERVED','OCCUPIED','BLOCKED'],
 [('FREE','RESERVED','book(reservationId)\\n[capacity, type, charger]'),
  ('RESERVED','OCCUPIED','checkIn(reservationId)\\n[15 min before ≤ now < start + 15 min]'),
  ('RESERVED','FREE','cancel / ReservationExpired (no-show)\\n/ booking CANCELLED or NO_SHOW'),
  ('OCCUPIED','FREE','checkOut / auto check-out\\n[end + 15 min] / COMPLETED'),
  ('FREE','OCCUPIED','dock(rental vehicle)\\n[BR-M4-08]','style=dashed'),
  ('OCCUPIED','FREE','undock (pick-up,\\nredistribution)','style=dashed'),
  ('FREE','BLOCKED','ChargerFailed / Operator block','color="#c0504d", fontcolor="#c0504d"'),
  ('RESERVED','BLOCKED','ChargerFailed\\n/ cancel the reservation (M2)','color="#c0504d", fontcolor="#c0504d"'),
  ('OCCUPIED','BLOCKED','ChargerFailed\\n/ keep the stay','color="#c0504d", fontcolor="#c0504d"'),
  ('BLOCKED','FREE','charger back in service (M5) or Operator unblock\\n[no reservation, no vehicle]'),
  ('BLOCKED','OCCUPIED','unblock [a vehicle is still parked]')],
 initial='FREE', rankdir='TB', extra='nodesep=1.6; ranksep=1.3;')
N={}
N['ParkingController']=klass('ParkingController','REST, /api/m4',attrs=None,ops=['+registerVehicle / updateVehicle / deactivate / activate','+hubs(), spaces(hubId), availability(hubId, start, end)',
 '+book(req), checkIn(id), checkOut(id), cancel(id), myBookings()','+hubBookings(hubId), block(id), unblock(id)  «Operator»'])
N['ParkingService']=klass('ParkingService','service',attrs=['MIN_BOOKING_MINUTES = 30, MAX_BOOKING_HOURS = 12','BOOK_AHEAD_HOURS = 24, EARLY_CHECK_IN_MINUTES = 15','CHECK_OUT_GRACE_MINUTES = 15'],ops=[
 '+book(studentId, req: BookParkingRequest): ParkingBookingResponse','+checkIn(studentId, bookingId) / checkOut(studentId, bookingId)','+cancel(studentId, bookingId)',
 '+applyRelease(reservationId, outcome): void','+handleChargerFailed(pointId) / handleChargerRecovered(pointId)','+autoCheckOut(bookingId): boolean','+releaseIfReservationEnded(reservationId): boolean'])
N['RentalDockService']=klass('RentalDockService','service, implements ParkingDock',attrs=None,ops=['+dockRentalVehicle(vehicleId, hubId): Long','+undockRentalVehicle(vehicleId): void','+freeSpacesForRental(hubId): int','+vacancies(): List<HubVacancy>'])
N['ParkingNoShowJob']=klass('ParkingNoShowJob','scheduled, every 60 s',attrs=None,ops=['+run(): void'],**SCHED)
N['ParkingEventHandler']=klass('ParkingEventHandler','event listener, @Order(0)',attrs=None,ops=['+onReservationExpired / onReservationCancelled','+onReservationCheckedIn','+onChargerFailed(e)','+onResourceStateUpdated(e)  [charger back]'],**SCHED)
N['ParkingSpace']=klass('ParkingSpace','entity, parking_space',attrs=['-id, hubId: Long','-spaceCode: String','-type: Type {CAR, BIKE}','-status: Status','-currentReservationId: Long','-chargerPointId: Long','-blockedReason: BlockReason','-dockedVehicleId: Long','-version: Long'],
 ops=['+book(reservationId) / checkIn(reservationId)','+release(reservationId) / detachReservation(reservationId)','+dock(vehicleId) / undock(vehicleId)','+block(reason) / unblock()'])
N['ParkingBookingLog']=klass('ParkingBookingLog','entity, parking_booking_log',attrs=['-reservationId, spaceId, hubId, studentId','-vehicleKind, personalVehicleId','-slotStart, slotEnd, checkInDeadline','-checkInTime, checkOutTime','-chargingRequested: boolean','-status: ACTIVE | COMPLETED | NO_SHOW | CANCELLED'],ops=['+open(...) {static}','+markCheckedIn(now) / close(outcome, now)'])
N['PrivateVehicle']=klass('PrivateVehicle','entity, private_vehicle',attrs=['-studentId: Long','-licensePlate: String','-type, model, batteryLevel','-active: boolean'],ops=['+register(...) {static}','+update(...) / deactivate() / activate()'])
N['Status']=klass('ParkingSpace.Status','enumeration',attrs=['FREE','RESERVED','OCCUPIED','BLOCKED'],ops=['+canMoveTo(target): boolean'],**ENUM)
N['ReservationCore']=klass('ReservationCore','interface (owner M2)',attrs=None,ops=['+reserveWindow(...)','+checkIn(...) / cancel(...) / completeByResource(...)','+cancelForBlockedResource(...)'],**IFACE)
N['ParkingDock']=klass('ParkingDock','interface, shared contract',attrs=None,ops=['used by M3 and M7'],**IFACE)
N['EventPublisher']=klass('EventPublisher','shared',attrs=None,ops=['ResourceStateUpdated (PARKING_SPACE)','ChargingRequested (M5)'],**EXT)
classdiagram(O+'m4_class.png',N,[
 ('ParkingController','ParkingService','assoc'),('ParkingNoShowJob','ParkingService','assoc'),('ParkingEventHandler','ParkingService','assoc'),
 ('ParkingService','ReservationCore','assoc','reserve / check in / cancel'),('ParkingService','ParkingSpace','assoc','locks'),('ParkingService','ParkingBookingLog','assoc'),
 ('ParkingService','PrivateVehicle','assoc'),('RentalDockService','ParkingDock','impl'),('RentalDockService','ParkingSpace','assoc','dock'),
 ('ParkingService','EventPublisher','dep','publishes'),('RentalDockService','EventPublisher','dep'),
 ('ParkingBookingLog','ParkingSpace','assoc','0..* → 1'),('ParkingSpace','ParkingSpace.Status','assoc','1'),('ParkingBookingLog','PrivateVehicle','assoc','0..* → 0..1')],extra='splines=true;')
mermaid(O+'m4_sequence.png','''sequenceDiagram
autonumber
actor S as Student
participant PC as ParkingController
participant PS as ParkingService
participant DB as parking_space / parking_booking_log
participant RC as ReservationCore (M2)
participant EP as EventPublisher
participant M5 as ChargingEventHandler (M5)
S->>PC: POST /api/m4/bookings {hub, window, vehicle, requestCharging}
PC->>PS: book(studentId, request)
PS->>PS: validateWindow, resolveVehicle (BR-M4-02), battery < 90%
PS->>DB: count overlapping bookings + docked rental vehicles
alt count ≥ capacity (BR-M4-01, BR-M4-08)
  PS-->>PC: 409 HUB_FULL
else room left
  PS->>DB: pickSpace — FREE, right type, with charger (row lock)
  PS->>RC: reserveWindow(student, PARKING_SPACE, space, hub, window, start + 15 min)
  alt same space or second booking
    RC-->>PS: RESOURCE_CONFLICT / RESERVATION_LIMIT_EXCEEDED (rollback)
  else confirmed
    RC-->>PS: Reservation CONFIRMED
    PS->>DB: space.book(id) → RESERVED, booking log ACTIVE
    PS->>EP: ResourceStateUpdated(PARKING_SPACE, RESERVED) → M1
    PS->>EP: ChargingRequested(student, hub, space, PERSONAL, battery, window)
    EP->>M5: onChargingRequested → session queued, prefers the charger of the space
    PC-->>S: 201 booking with check-in window
  end
end
''', width=1500)

# ---------------------------------------------------------------- activity: a parking stay after booking (UC-M4-03, UC-M4-04)
# ParkingService.checkIn / checkOut / cancel / autoCheckOut and ParkingNoShowJob (Timer, every 60 s).
src=activity_header('A parking stay: from booking to release (M4)')+f'''
b [label="Booking ACTIVE,\\nspace RESERVED", {ACT}];
w [label="Next event?", {DEC}];
cn [label="M2 cancel(): reservation CANCELLED", {ACT}];
ci [label="Student presses Check in", {ACT}];
d1 [label="now ≥ start − 15 min\\nand before the\\ncheck-in deadline?", {DEC}];
x1 [label="CHECK_IN_TOO_EARLY /\\nCHECK_IN_EXPIRED", {ERR}];
d2 [label="Space BLOCKED?", {DEC}];
x2 [label="SPACE_BLOCKED (409)", {ERR}];
in [label="M2 checkIn(): CHECKED_IN;\\nspace OCCUPIED, booking checked in", {ACT}];
ns [label="Timer: deadline passed,\\nno check-in", {ACT}];
d3 [label="M2 still holds\\nthe reservation?", {DEC}];
wait [label="Wait: M2 Timer marks it\\nEXPIRED, the event releases the space", {ACT}];
rel [label="Release the space,\\nbooking NO_SHOW", {ACT}];
w2 [label="Next event?", {DEC}];
co [label="Student presses Check out", {ACT}];
ao [label="Timer: window end + 15 min\\npassed, auto check-out", {ACT}];
fin [label="M2 completeByResource():\\nreservation COMPLETED", {ACT}];
d4 [label="Space BLOCKED?", {DEC}];
fr [label="Space FREE,\\npublish the new state to M1", {OK}];
det [label="Detach the reservation only;\\nunblock if the charger is back", {ACT}];
close [label="Booking closed\\n(COMPLETED / CANCELLED / NO_SHOW)", {ACT}];
e {END}; e2 {END};
s -> b -> w;
w -> cn [label="student cancels"]; w -> ci [label="check-in"]; w -> ns [label="deadline passes"];
ci -> d1; d1 -> x1 [label="no"]; d1 -> d2 [label="yes"]; d2 -> x2 [label="yes"]; d2 -> in [label="no"];
x1 -> w [style=dashed, constraint=false]; x2 -> e2;
ns -> d3; d3 -> wait [label="yes"]; d3 -> rel [label="no (event missed)"]; wait -> rel;
in -> w2; w2 -> co [label="student"]; w2 -> ao [label="Timer"]; co -> fin; ao -> fin;
cn -> d4; fin -> d4; rel -> d4;
d4 -> fr [label="no"]; d4 -> det [label="yes"]; fr -> close; det -> close; close -> e;
{{rank=same; cn; ci; ns}} {{rank=same; co; ao}}
}}'''
run_dot(src,O+'m4_activity.png',dpi=150)
print('ok')
