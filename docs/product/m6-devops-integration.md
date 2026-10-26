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
or configuration defaults. TV2 has approved a 5,000 ms demo interval (D02), and
TV6 has selected D04/D11/D12 proposals as M6 implementation baselines. D04
charger-failure behavior, D11 retention scope, and D12 cross-module contract
still need the owner/consumer confirmations recorded in the decision log before
they are described as whole-system agreements.

The selected M6 implementation baselines, remaining cross-module questions,
required approvers, and copy-ready messages for each teammate are in
`docs/proposals/m6-open-decisions.md`. Proposed values there are not product
authority for other modules until the named approvers/consumers accept them.
Before Incident creation from Operator or failure readings is enabled, TV2 must
select severity values, automatic severity mapping, and the BR-M6-06 active
duplicate behavior. The generic latest-state table is a D11 schema addendum
beyond D10; TV4 must approve it and provide the Flyway migration before
persistent latest-state behavior is enabled.

TV6 recorded these TV3 decisions on 2026-10-20: M6-D03 uses one shared service
with manual and automatic adapters; M6-D05 keeps M6 on the event-publisher side
of module boundaries; M6-D06 separates M6-owned device health from M5-owned
charging allocation/session state; and M6-D09 uses system-wide 400/404/409
errors plus consumer-key/event-ID deduplication and isolated consumer error
handling. On 2026-10-21, TV2 accepted M6-D01 (Operator reports manually, internal
Sensor/Simulator adapter, no Student incident creation), D02 (5,000 ms demo
interval), and D07 (Incident lifecycle with reopen only from Resolved to
InProgress and terminal Closed). TV2 and TV3 accepted D08: all `/api/m6/**`
routes require `OPERATOR`, except a specifically justified read-only route
needed by Students. No such Student route is currently identified. On
2026-10-21, TV6 selected the M6 proposals for D04 (failure reason set), D11
(latest state plus Incident, no raw tick history), and D12 (uppercase String
reason codes and current event payloads) as M6 implementation baselines. TV3/M5
confirmation for ChargerFailed semantics, TV3/TV4 confirmation for D11, and TV3
plus consumer payload acknowledgements for D12 remain outstanding; these choices
are not recorded as whole-system approvals.
For low battery, M6 publishes its resource-state update; M3 owns
`VehicleLowBattery` on its vehicle-return flow below the configured threshold
(currently 20%).
TV6 selected the M6-D10 entity proposal as its baseline, and TV4 accepted that
proposal on 2026-10-21. The Flyway migration is still a delivery task and is not
present in the current code. The decision proposal records the accepted scope
and remaining follow-up.

## Current Repository State

| Capability | Evidence | Status |
| --- | --- | --- |
| M6 package structure | `backend/.../m6simulator/` | In-memory simulator walking skeleton; `NORMAL_TICK` uses ParkingSpace `FREE`; selected uppercase failure codes are validated by resource type; no Incident JPA workflow or persistent latest-state table |
| M6 event contracts | `shared/events/*FailedEvent`, `ResourceStateUpdatedEvent` | Present |
| In-process event publisher | `shared/events/EventPublisher.java` | Present |
| M6 inbound duplicate aid | `m6simulator/service/InboundDeviceReadingStore.java` | Separate producer-side bounded process-memory store; not durable |
| Consumer duplicate aid | `shared/events/ProcessedEventStore.java` | Integration branch implements bounded in-memory `(consumerKey, eventId)` dedupe; M3 handlers use stable keys and separate transactions, catching/logging failures. Focused 14-test run and full 37-test verify passed. The feature branch does not yet contain these shared/M3 changes |
| M6 incident domain/service/API | `m6simulator` | Not implemented. D01/D07/D10 are accepted; TV2 severity/duplicate-policy gate and TV4 migration remain. D11 latest-state schema addendum also awaits TV4 approval |
| Simulator API | `m6simulator/controller/SimulatorController.java`, `shared/config/SecurityConfig.java` | Scenario/status endpoints require Operator. Catch-all `/api/m6/**` OPERATOR rule is implemented on integration branch; feature branch sync is pending. No Student read-only exception is identified |
| Simulator and scheduler | `DeviceSimulatorService`, `SimulatorScheduler`, `application.yml` | Deterministic scenarios implemented; D02 interval configured to 5,000 ms; scheduler remains disabled by default and still requires an explicit seed when enabled |
| M6 consumer integration | M3 `VehicleEventHandler` | Integration branch H2 proof covers `VehicleFailed` → `MAINTENANCE`, duplicate delivery, and handler isolation; feature branch retains its prior M3 handler. M2/M4/M5/M8 runtime reaction is not proven here |
| M6 operator UI | No implementation found | Not implemented |
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
