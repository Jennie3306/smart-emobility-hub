# M6 And TV6 Working Contract

## Outcome

TV6 owns two connected workstreams:

1. M6 product work: simulate device data, update resource state, manage device
   incidents, and publish facts for other modules.
2. DevOps/integration work: maintain repository collaboration, CI, packaging,
   a stable demo environment, weekly integrated builds, and the deployment
   diagram supplied to TV3.

## M6 Boundary

M6 must provide:

- periodic simulated state for vehicles, parking spaces, and charging points;
- operator workflows for reporting and progressing incidents;
- device health state for `ChargingPoint`; M5 owns charging allocation/session
  state. TV3 approved this split under M6-D06; detailed transitions need the
  M5 handoff contract;
- `Incident` states `Reported`, `Assigned`, `InProgress`, `Resolved`, `Closed`;
- publication of `ResourceStateUpdated`, `VehicleFailed`, and `ChargerFailed`;
- unit/integration tests for device failure, incident transitions, event payload
  and routing, and repeated `eventId` handling.

M6 must not cancel reservations, reassign charging sessions, alter parking
records, or move vehicles to maintenance inside another module. It publishes the
fact; M2, M3, M4, M5, M1, and M8 own their reactions.

Do not infer thresholds, retry policy, or failure probabilities from examples
or configuration defaults. The decision log separates accepted business rules,
implementation proposals and physical-schema delivery.

## Decisions Received 29 October 2026

Sources: [TV2 dated 28 Oct](../../DESCRIPTION/Template_report/Evidence/M6_IoT/decisions/tv2-20261028.md),
[TV3 received 29 Oct, source undated](../../DESCRIPTION/Template_report/Evidence/M6_IoT/decisions/tv3-received-20261029.md).
These are source decisions, not executable proof that the new rules exist.

- Severity is LOW/MEDIUM/HIGH/CRITICAL, uppercase, mandatory for Operator report
  with no default. Missing/null/empty/unknown/lowercase returns 400
  VALIDATION_FAILED with severity details. It affects display/filter/priority,
  not workflow or auto-assignment; no edit-severity endpoint in this version.
- Automatic mapping is VEHICLE: MECHANICAL→HIGH, SENSOR_OFFLINE→MEDIUM;
  CHARGING_POINT: OFFLINE→MEDIUM, POWER_FAULT→HIGH, OVERHEAT→CRITICAL. Adapter
  payload does not gain severity. MANUAL_REPORT is Operator-only; adapter use
  or unknown reason must be INVALID_DEVICE_PAYLOAD, no write/publication/fallback.
- Active statuses are REPORTED/ASSIGNED/IN_PROGRESS, keyed by resource type+ID
  regardless of reason. Manual duplicate is 409 ACTIVE_INCIDENT_EXISTS with
  existingIncidentId/existingStatus and no mutation/event. A new reading ID on
  an active resource yields non-error ACTIVE_INCIDENT_EXISTS, marks the ID
  processed and does not update Incident/re-emit failure; unchanged state emits
  no ResourceStateUpdated. Re-delivery of the same processed ID is DUPLICATE first.
- Concurrent creation must leave one active and one post-commit failure-event
  set; loser follows its manual/reading outcome. Reports after RESOLVED/CLOSED
  create a new Incident, leaving the old one unchanged and adding no linkage
  column. Reopen validates source RESOLVED first, then active conflict; conflict
  returns 409 with existingIncidentId, leaving the old Incident RESOLVED.
- TV3 accepts D11's latest-state + Incident / no raw-history architecture and
  uppercase String reasons. Current payload is sufficient for M3; other
  consumer acknowledgements, M5 usage/recovery contract and TV4 D11 schema remain.
- TV3 requires compatibility with markIfNew(eventId) and new M3 handlers;
  shared/events/store/security changes need owner review, preferably a separate
  PR. The TV2-proposed active/transition ordering and active_resource_key unique
  mechanism are **not** approved by this TV3 reply; coordinate TV3+TV4 first.

D01/D02/D07/D08 remain accepted (Operator, 5,000 ms demo, lifecycle and namespace
security). D10 schema was accepted on 21 Oct; migration still awaits TV4. New
severity/state columns/constraints and the D11 generic snapshot table require
physical-schema confirmation. Reopen timestamp semantics remain unanswered.
M6 publishes resource-state updates for low battery; M3 owns VehicleLowBattery.

**Gap fixes implemented 29 Oct:** feature commit `7edb551` separates
allowsDeviceReading from allowsOperatorReport. Adapter MANUAL_REPORT is rejected
as INVALID_DEVICE_PAYLOAD before state write/dedupe completion/publication, while
Operator reason validation still permits it. Commit `b156344` updates Incident
fixtures to LOW/MEDIUM/HIGH/CRITICAL in list/detail/filter and mandatory draft
selection with no default. Fixture data is not automatic severity mapping and
the draft does not create an Incident or apply duplicate admission.

Fresh proof: 47 focused backend tests (35 D07 + 12 DeviceEventService), full
backend verify 71 tests and frontend 32 tests passed; TypeScript/Vite build and
Compose config passed. Browser proof isolates fixture UI with a stubbed GET
status and is not new live simulator/API/DB proof. See report §8.10 and curated
policy-20261029 records. Historical 27/28 Oct evidence remains unchanged.

**Remaining work:** Incident severity mapping/application, service/API/JPA,
version/concurrency/after-commit and TV4 migrations; TV3 ordering/race review and
M5 health/recovery contract. Root's ProcessedEventStore prototype removes the
one-argument markIfNew(UUID) and must not be copied into the feature/shared PR
as-is. Preserve compatibility and newer M3 handlers under TV3 review.

## Current Repository State

| Capability | Evidence | Status |
| --- | --- | --- |
| M6 package structure | `backend/.../m6simulator/` | In-memory simulator walking skeleton; `NORMAL_TICK` uses ParkingSpace `FREE`; source-specific uppercase reasons reject adapter MANUAL_REPORT; Operator reason helper remains separate; no Incident JPA workflow or persistent latest-state table |
| M6 event contracts | `shared/events/*FailedEvent`, `ResourceStateUpdatedEvent` | Present |
| In-process event publisher | `shared/events/EventPublisher.java` | Present |
| M6 inbound duplicate aid | `m6simulator/service/InboundDeviceReadingStore.java` | Separate producer-side bounded process-memory store; not durable |
| Consumer duplicate aid | `shared/events/ProcessedEventStore.java` | Integration branch implements bounded in-memory `(consumerKey, eventId)` dedupe; M3 handlers use stable keys and separate transactions, catching/logging failures. Focused 14-test run and full 37-test verify passed. The feature branch does not yet contain these shared/M3 changes |
| M6 incident domain/service/API | `m6simulator/domain/Incident{Action,Status,Lifecycle}` | Feature branch has immutable, unpersisted D07 transition graph: 35 domain tests; historical backend verify 69 tests passed 27 Oct; current verify 71 passed 29 Oct (Java 21), code committed as 7edb551/b156344. No Incident service/API/JPA or pipeline wiring. Assignee/version/timestamps and policy application remain outside the domain slice. TV2 severity/duplicate rules are now accepted; implementation and TV3 race/ordering review + TV4 migrations/D11 schema remain |
| Simulator API | `m6simulator/controller/SimulatorController.java`, `shared/config/SecurityConfig.java` | Scenario/status endpoints require Operator. Catch-all `/api/m6/**` OPERATOR rule is implemented on integration branch; feature branch sync is pending. No Student read-only exception is identified |
| Simulator and scheduler | `DeviceSimulatorService`, `SimulatorScheduler`, `application.yml` | Deterministic scenarios implemented; D02 interval configured to 5,000 ms; scheduler remains disabled by default and still requires an explicit seed when enabled |
| M6 consumer integration | M3 `VehicleEventHandler` | Integration branch H2 proof covers `VehicleFailed` → `MAINTENANCE`, duplicate delivery, and handler isolation; feature branch retains its prior M3 handler. M2/M4/M5/M8 runtime reaction is not proven here |
| M6 operator UI | `/m6/operations`, `frontend/src/modules/m6simulator/`; [UI proof](../reviews/m6-ui-validation-20261028.md) | Live simulator status/three scenarios/Long seed/run output and 400/401/403/409 handling implemented on feature; 28 frontend tests and production Compose browser smoke passed 28 Oct. Incident UI is explicitly local fixtures, with D07/filter/detail/draft only; fixtures now show four approved severities with filtering and required draft choice (32 frontend tests on 29 Oct); no real Incident policy/application/health/API/DB completion inferred |
| Backend CI | `.github/workflows/ci.yml` | Configured |
| Frontend CI | `.github/workflows/ci.yml` | Configured |
| Demo containers | Dockerfiles and Compose overlay | Configured; verify per runbook |
| Branch protection | External GitHub setting | Unverified |

## Recommended Delivery Order

This is sequencing, not invented product policy:

1. Implement the accepted D01/D02/D07/D08 decisions and TV6-selected D04/D11/D12
   baselines; keep unresolved cross-module decisions explicitly marked.
2. Coordinate entity/table needs with TV4 and shared event changes with TV3.
3. Implement and test the two state machines and invalid transitions.
4. Implement incident service/API and publish the three accepted events only
   after the owned state change succeeds.
5. Add the scheduled simulator behind configuration suitable for deterministic
   tests; do not make tests depend on randomness or wall-clock waiting.
6. Give M1/M2/M3/M4/M5/M8 stable sample event fixtures early so their handlers
   can be developed independently.
7. Add the M6 frontend route only after TV5 returns the approved mockup/design
   contract.
8. Run the integrated event flows and record report/test evidence.

## DevOps And Integration Definition Of Done

- CI uses the accepted Java 21 and Node 20 toolchains and runs backend verify,
  frontend tests, and frontend build from a clean checkout.
- An integrated branch passes the local equivalents before PR handoff.
- The demo Compose configuration renders, images build, database becomes
  healthy, backend starts, frontend/API/WebSocket routes are reachable, and
  shutdown removes only resources created for the demo.
- The deployment diagram matches the actual Compose topology.
- Weekly integration evidence records revision, commands, results, failures,
  owner, and follow-up; “CI exists” is not recorded as “CI passed.”
- GitHub ruleset status is reported separately from workflow configuration.

Use `docs/runbooks/ci-and-demo.md` for the executable steps and
`DESCRIPTION/Template_report/Template_M6.md` for report artifacts.
