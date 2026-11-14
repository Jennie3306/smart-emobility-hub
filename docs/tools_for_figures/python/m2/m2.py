import os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
from lib import *
O=png_dir('m2')
# English versions of the M2 diagrams; the original PlantUML / draw.io sources (Vietnamese) are kept in
# plantuml_puml/m2/ and drawio/m2/. Content follows package com.hcmut.emobility.m2reservation.

# ---------------------------------------------------------------- use case
usecase(O+'m2_usecase.png','«subsystem» M2 — Find and Reserve a Vehicle',
 [('UC-M2-01','Search vehicles'),('FR-M2-10','Suggest other hubs'),('UC-M2-02','View vehicle|and battery'),
  ('UC-M2-03','Reserve a vehicle'),('FR-M2-06','Check conflicts|and limits'),('FR-M2-07','View current|reservation'),
  ('UC-M2-04','Cancel a reservation'),('UC-M2-05','Expire an|unclaimed hold'),('FR-M2-08','Handle a|failed vehicle'),
  ('FR-M2-09','Close on return')],
 left=[('stu','Student',False)],
 right=[('tm','Timer',True),('iot','IoT simulator (M6)',True),('m3','M3 — Rental vehicle|lifecycle',True)],
 assoc=[('stu','UC-M2-01'),('stu','UC-M2-02'),('stu','UC-M2-03'),('stu','FR-M2-07'),('stu','UC-M2-04'),
        ('tm','UC-M2-05'),('iot','FR-M2-08'),('m3','FR-M2-09')],
 rels=[('FR-M2-10','UC-M2-01','extend'),('UC-M2-03','FR-M2-06','include')],
 amber=('UC-M2-05','FR-M2-08','FR-M2-09'))

# ---------------------------------------------------------------- class
N={}
N['VehicleSearchController']=klass('VehicleSearchController','RestController /api/m2',attrs=None,
    ops=['+search(query): List<VehicleSummary>','+detail(id): VehicleDetail','+suggestHubs(query): List<HubSuggestion>'])
N['ReservationController']=klass('ReservationController','RestController /api/m2/reservations',attrs=None,
    ops=['+reserve(request, auth): 201 ReservationResponse','+current(auth): 200 | 204','+get(id, auth)','+cancel(id, auth)','+checkIn(id, auth)'])
N['ReservationCore']=klass('ReservationCore','interface — the only entry point for M4',attrs=None,
    ops=['+reserve(userId, type, resourceId, hubId): Reservation','+reserveWindow(userId, type, resourceId, hubId,','      startAt, endAt, checkInDeadline): Reservation',
         '+cancelForBlockedResource(reservationId): Optional','+cancel(reservationId, requesterId): Reservation','+checkIn(reservationId, requesterId): Reservation',
         '+completeByResource(type, resourceId): Optional','+findActive(userId, type): Optional'],**IFACE)
N['ReservationService']=klass('ReservationService','Service',
    attrs=['-repository: ReservationRepository','-vehicles: VehicleSearchRepository','-publisher: EventPublisher','-clock: Clock','-holdMinutes: int'],
    ops=['+reserveVehicle(userId, vehicleId): Reservation','+getOwned(reservationId, requesterId)','+findOverdueIds(): List<Long>','+expire(reservationId): boolean',
         '+handleVehicleFailed(vehicleId): Optional','+pickedUp(vehicleId, userId): void','-checkCanReserve(userId, type, resourceId)','-saveActive(r): Reservation  // UNIQUE decides'])
N['VehicleSearchService']=klass('VehicleSearchService','Service',attrs=['-MAX_SUGGESTIONS = 3'],
    ops=['+search(query): List<VehicleSummary>','+getDetail(vehicleId): VehicleDetail','+suggestHubs(query): List<HubSuggestion>'])
N['ReservationExpiryJob']=klass('ReservationExpiryJob','Component, @Scheduled 30 s',attrs=None,ops=['+run(): void'],**SCHED)
N['ReservationEventHandler']=klass('ReservationEventHandler','Component',attrs=None,
    ops=['+onVehicleFailed(VehicleFailedEvent)  // after commit','+onVehicleReturned(VehicleReturnedEvent)','+onVehiclePickedUp(VehiclePickedUpEvent)'])
N['Reservation']=klass('Reservation','entity, reservation',
    attrs=['-id, userId, hubId: Long','-resourceType: ResourceType','-resourceId: Long','-state: ReservationState','-startAt, endAt, expiresAt: OffsetDateTime',
           '-checkedInAt, closedAt: OffsetDateTime','-cancelReason: CancelReason','-activeResourceKey: String  {UNIQUE}','-activeUserKey: String  {UNIQUE}','-version: Long  {@Version}'],
    ops=['+create(...) {static}','+confirm(now, holdMinutes)','+confirmWindow(now, start, end, deadline)','+checkIn(now) / cancel(reason, now)','+expire(now) / complete(now)','+reassignResource(newResourceId)','+isOverdue(now) / remainingSeconds(now)'])
N['ReservationState']=klass('ReservationState','enumeration',attrs=['PENDING, CONFIRMED, CHECKED_IN','COMPLETED, CANCELLED, EXPIRED'],ops=['+canMoveTo(target): boolean','+isActive(): boolean'],**ENUM)
N['ResourceType']=klass('ResourceType','enumeration',attrs=['VEHICLE, PARKING_SPACE'],ops=None,**ENUM)
N['CancelReason']=klass('CancelReason','enumeration',attrs=['USER_REQUEST, VEHICLE_FAILED','RESOURCE_BLOCKED, REJECTED_CONFLICT'],ops=None,**ENUM)
N['ReservationRepository']=klass('ReservationRepository','JpaRepository',attrs=None,
    ops=['+findFirstByUserIdAndResourceTypeAndStateIn(...)','+findFirstByResourceTypeAndResourceIdAndStateIn(...)','+findOverdueIds(now): List<Long>'],**IFACE)
N['VehicleSearchRepository']=klass('VehicleSearchRepository','Repository, read-only view of M3 data',attrs=None,
    ops=['+findById(id)','+searchAvailable(hubId, type, minBattery)','+countAvailableByHub(type, minBattery)','+findReplacements(hubId, type, excludeId)'],**IFACE)
N['EventPublisher']=klass('EventPublisher','shared.events',attrs=None,ops=['+publish(event)'],**EXT)
N['M4 ParkingService']=klass('M4 ParkingService','module m4parking',attrs=None,ops=None,**EXT)
classdiagram(O+'m2_class.png',N,[
 ('VehicleSearchController','VehicleSearchService','assoc'),('ReservationController','ReservationService','assoc'),
 ('ReservationController','VehicleSearchService','assoc','findCode()'),
 ('ReservationService','ReservationCore','impl'),('M4 ParkingService','ReservationCore','dep','«use»'),
 ('ReservationExpiryJob','ReservationService','assoc'),('ReservationEventHandler','ReservationService','assoc'),
 ('VehicleSearchService','VehicleSearchRepository','assoc'),('ReservationService','VehicleSearchRepository','assoc'),
 ('ReservationService','ReservationRepository','assoc'),('ReservationService','EventPublisher','dep','«publish» Confirmed, CheckedIn,\\nCancelled, Expired'),
 ('ReservationRepository','Reservation','dep'),('Reservation','ReservationState','assoc'),('Reservation','ResourceType','assoc'),
 ('Reservation','CancelReason','assoc','0..1')],
 clusters=[('controller',['VehicleSearchController','ReservationController']),
           ('service',['ReservationCore','ReservationService','VehicleSearchService','ReservationExpiryJob']),
           ('domain',['Reservation','ReservationState','ResourceType','CancelReason'])],
 extra='label=<<b>Module M2 — Find and Reserve a Vehicle: class diagram</b>>; labelloc=t; fontsize=16;')

# ---------------------------------------------------------------- state: Reservation (ReservationState.canMoveTo)
statemachine(O+'m2_state.png',
 ['PENDING','CONFIRMED','CHECKED_IN',('COMPLETED','fillcolor="#e1f3e8", color="#3a7d44"'),
  ('EXPIRED','fillcolor="#e8e8e8", color="#999999"'),('CANCELLED','fillcolor="#e8e8e8", color="#999999"')],
 [('PENDING','CONFIRMED','checks pass [vehicle AVAILABLE ∧\\nno active reservation]\\n/ expiresAt = now + 15 min\\n(or the M4 check-in deadline),\\npublish ReservationConfirmedEvent'),
  ('PENDING','CANCELLED','checks fail or UNIQUE violated\\n/ 409, transaction rolled back\\n(never stored)','style=dashed'),
  ('CONFIRMED','CHECKED_IN','checkIn() or pick-up via M3\\n[owner ∧ now < expiresAt]\\n/ checkedInAt = now'),
  ('CONFIRMED','CONFIRMED','VehicleFailedEvent [replacement of the\\nsame type at the same hub]\\n/ move to the new vehicle, keep expiresAt'),
  ('CONFIRMED','EXPIRED','Timer (30 s) [now ≥ expiresAt]\\n/ release keys,\\npublish ReservationExpiredEvent'),
  ('CONFIRMED','CANCELLED','cancel() [owner] / USER_REQUEST;\\nVehicleFailedEvent [no replacement] / VEHICLE_FAILED;\\nM4 space blocked / RESOURCE_BLOCKED\\n— publish ReservationCancelledEvent'),
  ('CHECKED_IN','COMPLETED','VehicleReturnedEvent (M3) or\\nM4 check-out / completeByResource()')],
 initial='PENDING', final=('COMPLETED','EXPIRED','CANCELLED'),
 rankdir='TB', extra='label=<<b>Reservation lifecycle — Module M2</b>>; labelloc=t; fontsize=18; ranksep=1.1; nodesep=1.2;',
 notes=['note [shape=note, style=filled, fillcolor="#fff6d5", color="#c9a227", fontname="DejaVu Sans", fontsize=11, '
        'label="Release keys = activeResourceKey and activeUserKey set to NULL, closedAt = now.\\nPENDING, CONFIRMED and CHECKED_IN hold the keys (UNIQUE, BR-M2-01 / BR-M2-03).\\nCOMPLETED, CANCELLED and EXPIRED are final: canMoveTo() returns false."];',
        'note -> CONFIRMED [style=dashed, arrowhead=none, color="#c9a227"];'])

# ---------------------------------------------------------------- activity: UC-M2-03 reserve a vehicle
src=activity_header('UC-M2-03 — Reserve a vehicle, with conflict checks (M2)')+f'''
a [label="Student presses Reserve\\nPOST /api/m2/reservations", {ACT}];
r0 [label="Student role and\\nvehicleId present?", {DEC}];
x0 [label="400 VALIDATION_FAILED /\\n403 ACCESS_DENIED", {ERR}];
d1 [label="Vehicle exists?", {DEC}];
x1 [label="VEHICLE_NOT_FOUND (404)", {ERR}];
d2 [label="Vehicle AVAILABLE?", {DEC}];
x2 [label="VEHICLE_NOT_AVAILABLE (409)\\nchoose another vehicle", {ERR}];
d3 [label="Student already has an\\nactive vehicle reservation?", {DEC}];
x3 [label="RESERVATION_LIMIT_EXCEEDED (409)\\nlink to the current reservation", {ERR}];
d4 [label="Vehicle already has an\\nactive reservation?", {DEC}];
x4 [label="RESOURCE_CONFLICT (409)\\nreload search results", {ERR}];
c [label="Create PENDING, confirm → CONFIRMED\\nexpiresAt = now + 15 min,\\nset activeResourceKey / activeUserKey", {ACT}];
sv [label="saveAndFlush → INSERT", {ACT}];
d5 [label="UNIQUE key\\nviolated?", {DEC}];
x5 [label="RESOURCE_CONFLICT (409),\\nrollback, no event", {ERR}];
p [label="Publish ReservationConfirmedEvent\\n(M3: vehicle → RESERVED, same transaction)", {ACT}];
ok [label="Commit; 201 Created\\ncountdown 15:00 on the screen", {OK}];
e {END}; e2 {END};
s -> a -> r0; r0 -> x0 [label="no"]; r0 -> d1 [label="yes"];
d1 -> x1 [label="no"]; d1 -> d2 [label="yes"]; d2 -> x2 [label="no"]; d2 -> d3 [label="yes"];
d3 -> x3 [label="yes"]; d3 -> d4 [label="no"]; d4 -> x4 [label="yes"]; d4 -> c [label="no"];
c -> sv -> d5; d5 -> x5 [label="yes: a parallel request won"]; d5 -> p [label="no"]; p -> ok -> e;
x0 -> e2; x1 -> e2; x2 -> e2; x3 -> e2; x4 -> e2; x5 -> e2;
}}'''
run_dot(src,O+'m2_activity.png',dpi=150)

# ---------------------------------------------------------------- sequence: two students reserve the last vehicle
mermaid(O+'m2_sequence.png','''sequenceDiagram
  autonumber
  actor A as Student A
  actor B as Student B
  participant C as ReservationController
  participant S as ReservationService
  participant R as ReservationRepository
  participant DB as PostgreSQL
  participant E as EventPublisher
  Note over A,B: EB-001 is the last AVAILABLE vehicle at the hub, both press Reserve at almost the same time
  par Request of Student A
    A->>C: POST /api/m2/reservations {vehicleId: 1}
    C->>S: reserveVehicle(userA, 1)
    S->>R: pre-checks: user limit, vehicle conflict
    R-->>S: none found
    S->>R: saveAndFlush(reservation A)
    R->>DB: INSERT (active_resource_key = 'VEHICLE:1')
    DB-->>R: 1 row
    S->>E: publish(ReservationConfirmedEvent)
    Note right of E: M3 marks the vehicle RESERVED in the same transaction
    S->>DB: COMMIT
    C-->>A: 201 Created {state: CONFIRMED, expiresAt}
  and Request of Student B
    B->>C: POST /api/m2/reservations {vehicleId: 1}
    C->>S: reserveVehicle(userB, 1)
    S->>R: pre-checks: user limit, vehicle conflict
    R-->>S: none found (A has not committed yet)
    S->>R: saveAndFlush(reservation B)
    R->>DB: INSERT (active_resource_key = 'VEHICLE:1')
    Note right of DB: waits on A's uncommitted key, then fails after A commits
    DB-->>R: unique_violation
    R-->>S: DataIntegrityViolationException
    S->>DB: ROLLBACK
    C-->>B: 409 {code: RESOURCE_CONFLICT}
  end
  Note over A,E: exactly one 201 and one 409, one CONFIRMED row and one ReservationConfirmedEvent
''', width=1500)
print('ok')
