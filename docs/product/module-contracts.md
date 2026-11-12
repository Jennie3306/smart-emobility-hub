# Appendix A — Module contracts (v2, 12 Nov 2026)

This file is the event and interface contract between the eight modules. It replaces Appendix A of
"Project overview & common rules" (v1). The report's §3.1 (architecture) and §4.2 (implementation structure)
refer to it, and `ModuleBoundaryTest` enforces the "no cross-module service import" rule it describes.

## A.1 Principles

1. **One owner per resource.** Only the owning module changes a resource's state and announces it. Other
   modules mirror the announced state or ask the owner through a published interface.
2. **Observations are not states.** The IoT simulator (M6) reports what a device observed. The owner decides
   what the observation means for the business state.
3. **Events are facts in the past tense** (`ChargerFailed`, not `FailCharger`). Every event carries `eventId`
   (UUID) and `occurredAt`; consumers ignore an `eventId` they have already processed (`ProcessedEventStore`).
4. **Synchronous calls only where an immediate yes / no is needed** and the answer must not be overtaken by a
   concurrent request (the last free space). These calls go through an interface in a shared or public package,
   never through another module's service class.
5. **Read-only cross-module reads** (JDBC `SELECT`) are allowed for lookups; writes to another module's tables
   are not.

Mechanism: level 2 of the original Appendix A.3 — Spring's in-process event bus (`EventPublisher` →
`ApplicationEventPublisher`, `@EventListener`). The owner of a state listens at `@Order(0)`; M1 recounts at
`@Order(100)`, so the dashboard never shows a value older than the owner's.

## A.2 Resource ownership

| Resource | Owner | States | Mirrored by |
|---|---|---|---|
| Reservation | M2 | PENDING, CONFIRMED, CHECKED_IN, COMPLETED, CANCELLED, EXPIRED | M3, M4, M5 |
| RentalVehicle | M3 | AVAILABLE, RESERVED, IN_USE, LOW_BATTERY, CHARGING, MAINTENANCE, IN_TRANSIT | M1, M7, M8 (read-only) |
| ParkingSpace (shared pool per Hub) | M4 | FREE, RESERVED, OCCUPIED, BLOCKED | M1, M8 |
| ChargingPoint | M5 | AVAILABLE (published as FREE), RESERVED, IN_USE, FAULTY, UNDER_REPAIR | M1, M8, M4 |
| ChargingSession | M5 | REQUESTED, QUEUED, SCHEDULED, CHARGING, COMPLETED, INTERRUPTED, CANCELLED | — |
| Device reading, Incident | M6 | Incident: REPORTED, ASSIGNED, IN_PROGRESS, RESOLVED, CLOSED | — |
| RedistributionTask | M7 | CREATED, IN_PROGRESS, DONE, FAILED | — |
| SimulationRun | M8 | DRAFT, RUNNING, COMPLETED, APPLIED, DISCARDED | — |

**Shared parking pool (BR-M4-08).** A Hub has one pool of parking spaces. A rental vehicle standing at a Hub
occupies a space in that pool, just like a personal vehicle. "Hub full" therefore means the same thing when a
student returns a rental vehicle (M3), when an Operator moves vehicles (M7), when a student books a space (M4),
on the dashboard (M1) and in the What-if snapshot (M8).

## A.3 Events

| Event | Publisher | Payload (besides eventId, occurredAt) | Consumers and what each does |
|---|---|---|---|
| DeviceReadingReported | M6 | hubId, resourceType, resourceId, observedState, batteryLevel | M3: record battery; AVAILABLE below threshold → LOW_BATTERY, publish VehicleLowBattery. M5: a FAULTY port reported online → FREE |
| ResourceStateUpdated | M4 (PARKING_SPACE), M5 (CHARGING_POINT) | hubId, resourceType, resourceId, newState | M1: update dashboard. M8: keep for snapshot. M4: charger operational again → unblock its space |
| VehicleFailed | M6 | vehicleId, hubId, reason | M3: → MAINTENANCE. M2: cancel the vehicle's reservation. M1: recount |
| ChargerFailed | M6 | chargingPointId, hubId, reason | M5: port FAULTY, interrupt / move sessions. M4: block the space wired to that port, cancel its unchecked-in booking |
| ReservationConfirmed | M2 | reservationId, userId, resourceType, resourceId, hubId | M3: vehicle → RESERVED (fails fast so M2 rolls back). M1: recount |
| ReservationCheckedIn | M2 | reservationId, userId, resourceType, resourceId, hubId | M4: space RESERVED → OCCUPIED. M3: vehicle check-in |
| ReservationExpired | M2 | reservationId, resourceType, resourceId | M3: vehicle → AVAILABLE. M4: release space (no-show). M5: cancel linked charging session |
| ReservationCancelled | M2 | reservationId, resourceType, resourceId | M3: vehicle → AVAILABLE. M4: release space. M5: cancel linked charging session |
| VehiclePickedUp | M3 | vehicleId, userId, hubId | M2: check the reservation in. M1: recount |
| VehicleReturned | M3 | vehicleId, hubId, batteryLevel | M2: complete reservation. M5: queue a low-battery vehicle. M1: recount |
| VehicleLowBattery | M3 | vehicleId, batteryLevel, hubId | M5: automatic charging request. M1: recount |
| ChargingRequested | M4 | userId, hubId, parkingSpaceId, vehicleKind, vehicleId, batteryLevel, arriveAt, leaveAt | M5: queue a session that waits for the vehicle's arrival, preferring the port wired to the booked space |
| ChargingStarted | M5 | sessionId, hubId, chargingPointId, vehicleKind, vehicleId | M3: rental vehicle LOW_BATTERY → CHARGING. M1: recount |
| ChargingCompleted | M5 | sessionId, hubId, chargingPointId, vehicleKind, vehicleId, finalBatteryLevel | M3: rental vehicle CHARGING → AVAILABLE with final level. M1: recount |
| HubNearCapacity | M1 | hubId, resourceType, usagePercent | M7: propose redistribution |
| DispatchStarted | M7 | taskId, vehicleId, fromHubId, toHubId | M3: vehicle AVAILABLE → IN_TRANSIT. M1: recount source Hub |
| DispatchCompleted | M7 | taskId, vehicleId, fromHubId, toHubId | M3: vehicle at destination, IN_TRANSIT → AVAILABLE. M1: recount both Hubs |
| DispatchFailed | M7 | taskId, vehicleId, fromHubId | M3: vehicle back at source, IN_TRANSIT → AVAILABLE. M1: recount |
| RecommendationApproved | M7 (after Operator approval) | recommendationId, actionType | M5: change charging schedule when the action concerns charging |
| SimulationCompleted | M8 | runId, recommendationIds | M7: list the run's recommendations for approval |

## A.4 Synchronous interfaces

| Interface | Package | Provider | Callers | Why synchronous |
|---|---|---|---|---|
| `ReservationCore` | `m2reservation.service` (only allowed cross-module service import) | M2 | M4 | Booking a parking space must hold the reservation in the same transaction |
| `ParkingDock` | `shared.contracts` | M4 (`RentalDockService`) | M3 (return, pick-up), M7 (task start / done / failed) | Return or delivery needs an immediate "is there a space"; the space row is locked so two vehicles cannot take the last space |
| Snapshot reads | read-only JDBC | each owner's tables | M8 (`JdbcSnapshotProvider`), M1, M5, M7 lookups | Consistent read of current state; never written back |

`ParkingDock` errors: `HUB_FULL` (409) when no free space is left — M3 returns it with the Hubs that still have
room, M7 turns it into `DESTINATION_FULL`; `HUB_NOT_FOUND` (404).

## A.5 Changes from v1

| v1 | v2 | Reason |
|---|---|---|
| M6 publishes ResourceStateUpdated for every resource | M6 publishes DeviceReadingReported; owners publish ResourceStateUpdated | M6 cannot know booking-driven states (RESERVED, IN_USE) without reading other modules' tables, which v1 itself calls the wrong way |
| ChargingPoint state machine drawn by M6 | Drawn by M5, the owner (code: `m5charging.domain.ChargingPointState`, includes RESERVED) | Names on diagrams must match the code |
| M6 "locks resources with an incident" | M6 reports the failure; M5 marks the port FAULTY, M4 blocks the space, M3 moves the vehicle to MAINTENANCE | Each owner changes its own state |
| ParkingReleased (M4 → M1) | ResourceStateUpdated with PARKING_SPACE = FREE | One event for every owner-state change |
| Rental capacity: `hub.parking_slots` vs rental vehicles (M7), no check (M3) | Shared parking pool through `ParkingDock` | One meaning of "Hub full" for M1, M3, M4, M7, M8 |
| — | ChargingRequested, ChargingStarted, DispatchStarted, DispatchFailed, ReservationConfirmed, ReservationCheckedIn, VehiclePickedUp added | Events already used in code, or needed so every state on the RentalVehicle state machine is reachable |

## A.6 Not implemented yet (tracked in the review)

- SimulationCompleted (M8) and M7 reading M8's recommendations: `NotConnectedRecommendationClient`.
- M5's handling of RecommendationApproved only logs the event.
- M7's reaction to HubNearCapacity only logs the event.
- M8 reads parking occupancy per Hub from the snapshot's `capacities`; its engine still estimates occupancy from
  rental vehicles only.
- M8's runs have no state field yet (SimulationRun states above are the design) and are stored in a JSON file,
  not in the database.
- M6 incidents: `IncidentStatus` / `IncidentLifecycle` exist, but no repository, API or screen.
