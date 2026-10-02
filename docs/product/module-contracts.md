# Module And Integration Contracts

## Ownership

| Module | Owner | Responsibility |
| --- | --- | --- |
| M1 | TV1 | Hub monitoring and operator dashboard |
| M2 | TV2 | Vehicle search/reservation and reservation core |
| M3 | TV3 | Rental vehicle pickup, return, and lifecycle |
| M4 | TV4 | Parking reservation for rental and private vehicles |
| M5 | TV5 | Charging queue, priority, and scheduling |
| M6 | TV6 | IoT simulation, device events, and incident lifecycle |
| M7 | TV7 | Vehicle redistribution |
| M8 | TV8 | What-if simulation and recommendations |

TV3 owns backend `shared/`; TV4 owns Flyway migrations; TV5 owns the frontend
design system. CODEOWNERS currently assigns `frontend/src/shared/` to TV6.
Placeholder GitHub handles must be replaced before CODEOWNERS can be relied on.

## Java Dependency Rule

Feature modules may use shared contracts and infrastructure. They must not import
another feature module's service package. Cross-module reactions use events in
`shared/events`. The only accepted direct exception is M4 calling the public
`ReservationCore` interface owned by M2.

`ModuleBoundaryTest` mechanically checks this narrow rule during `mvn verify`.
It does not enforce file ownership, Git review, event semantics, database access,
or runtime isolation.

## Event Contract

Every event is a past-tense fact, has `eventId` and `occurredAt`, and carries
only identifiers plus the minimum data consumers need. Producers publish;
consumers implement reactions in their own package.

| Event | Producer | Consumers |
| --- | --- | --- |
| `ResourceStateUpdated` | M6 | M1 dashboard; M8 snapshot input |
| `HubNearCapacity` | M1 | M7 redistribution proposal |
| `VehicleFailed` | M6 | M3 maintenance; M2 reservation response |
| `ChargerFailed` | M6 | M5 charging recovery; M4 parking response |
| `VehicleReturned` | M3 | M5 charging queue; M2 reservation completion |
| `VehicleLowBattery` | M3 | M5 charging queue |
| `ReservationExpired` / `ReservationCancelled` | M2 | M3 vehicle release; M4 parking release |
| `DispatchCompleted` | M7 | M3 vehicle location; M1 dashboard |
| `RecommendationApproved` | M7 after approval | M7 dispatch task; M5 schedule change |

Consumers must make repeated processing of the same `eventId` harmless and must
log failures without moving producer-owned logic into the consumer. The current
in-memory `ProcessedEventStore` is demo-scale and is lost on restart.

## Naming And Traceability

- Requirement: `FR-M<module>-<number>`.
- Business rule: `BR-M<module>-<number>`.
- Use case: `UC-M<module>-<number>`.
- Test case: `TC-M<module>-<number>`.
- API: `/api/m<module>/<resource>`.
- Feature branch: `feature/m<module>-<description>`.

Names for states, events, classes, methods, and IDs must remain consistent across
specification, diagrams, code, and tests.
