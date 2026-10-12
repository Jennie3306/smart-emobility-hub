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
- `ChargingPoint` states `Available`, `InUse`, `Faulty`, `UnderRepair`;
- `Incident` states `Reported`, `Assigned`, `InProgress`, `Resolved`, `Closed`;
- publication of `ResourceStateUpdated`, `VehicleFailed`, and `ChargerFailed`;
- unit/integration tests for device failure, incident transitions, event payload
  and routing, and repeated `eventId` handling.

M6 must not cancel reservations, reassign charging sessions, alter parking
records, or move vehicles to maintenance inside another module. It publishes the
fact; M2, M3, M4, M5, M1, and M8 own their reactions.

Do not choose incident thresholds, simulator frequency, retry policy, retention,
or failure probabilities from examples or configuration defaults. Those are BR
and NFR decisions that need explicit group authority before implementation.

The open questions, proposed defaults, required approvers, and copy-ready
messages for each teammate are in
`docs/proposals/m6-open-decisions.md`. Proposed values there are not product
authority until the named approvers accept them.

## Current Repository State

| Capability | Evidence | Status |
| --- | --- | --- |
| M6 package structure | `backend/.../m6simulator/` | In-memory simulator walking skeleton; `NORMAL_TICK` uses ParkingSpace `FREE`; no Incident JPA workflow |
| M6 event contracts | `shared/events/*FailedEvent`, `ResourceStateUpdatedEvent` | Present |
| In-process event publisher | `shared/events/EventPublisher.java` | Present |
| M6 inbound duplicate aid | `m6simulator/service/InboundDeviceReadingStore.java` | Separate producer-side bounded process-memory store; not durable |
| Consumer duplicate aid | `shared/events/ProcessedEventStore.java` | Present, memory-only; still globally keyed pending TV3 decision |
| M6 incident domain/service/API | `m6simulator` | Not implemented; Incident entities and workflow remain proposals |
| Simulator API | `m6simulator/controller/SimulatorController.java` | Operator-only scenario/status endpoints; shared security rule needs TV3 review |
| Simulator and scheduler | `DeviceSimulatorService`, `SimulatorScheduler` | Deterministic scenarios implemented; scheduler off by default and has no default interval |
| M6 consumer integration | M3 `VehicleEventHandler` | H2 integration test and isolated PostgreSQL Compose smoke prove `VehicleFailed` moves vehicle ID 1 to `MAINTENANCE`; M4/M5/M8 runtime reaction not proven here |
| M6 operator UI | No implementation found | Not implemented |
| Backend CI | `.github/workflows/ci.yml` | Configured |
| Frontend CI | `.github/workflows/ci.yml` | Configured |
| Demo containers | Dockerfiles and Compose overlay | Configured; verify per runbook |
| Branch protection | External GitHub setting | Unverified |

## Recommended Delivery Order

This is sequencing, not invented product policy:

1. Agree and record FR-M6/BR-M6 values, incident permissions, simulator cadence,
   and failure inputs with the group.
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
