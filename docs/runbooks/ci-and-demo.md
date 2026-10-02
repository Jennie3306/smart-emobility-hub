# CI, Integration, And Demo Runbook

## Clean Validation

Run from the repository root on the revision being handed off:

```bash
cd backend
mvn -B verify
```

```bash
cd frontend
npm ci
npm run test -- --run --passWithNoTests
npm run build
```

```bash
docker compose -f docker-compose.yml -f docker-compose.demo.yml config
```

These mirror the checked-in GitHub Actions workflow plus Compose configuration
validation. Record the revision and actual exit status. A workflow file in the
repository is not evidence of a green remote run.

## Weekly Integration Record

For each integrated revision, record:

| Field | Value |
| --- | --- |
| Commit SHA | |
| Backend verify | command, result, test count |
| Frontend test/build | commands and result |
| Compose config/build | command and result |
| Integrated flows exercised | actor action, event, consumer result |
| Defects | severity, owner, linked test/FR, status |
| GitHub CI URL | if actually observed |
| Branch protection | verified setting or `unverified` |

## Demo Environment

Render the topology before creating resources:

```bash
docker compose -f docker-compose.yml -f docker-compose.demo.yml config
```

Start the demo:

```bash
docker compose -f docker-compose.yml -f docker-compose.demo.yml up -d --build
docker compose -f docker-compose.yml -f docker-compose.demo.yml ps
```

Verify:

- PostgreSQL is healthy.
- Backend logs contain `Started EmobilityApplication` and no Flyway validation
  failure.
- `http://localhost:8080/swagger-ui.html` loads.
- `http://localhost` loads and login works.
- API calls through nginx retain the session cookie.
- A STOMP client can connect through `/ws` when testing realtime behavior.

## M6 Simulator Walking Skeleton

The simulator routes require an `OPERATOR` session. For the local demo account
created by `DemoUserInitializer`, log in and save the session cookie:

```bash
curl -i -c /tmp/m6-operator.cookies \
  -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data 'username=operator1&password=123456'
```

Check the current scheduler/run summary:

```bash
curl -i -b /tmp/m6-operator.cookies \
  http://localhost:8080/api/m6/simulator/status
```

Run deterministic scenarios. Each call returns a fresh `runId`, inbound reading
IDs are generated for each reading, and the response lists outbound `eventId`,
type, and resource. The same seed reproduces business content; repeating a
scenario is not a duplicate-delivery test because it creates new inbound IDs.
`observedAt` records when the factory creates readings, but two close calls may
receive equal timestamps; timestamps are not used as duplicate keys. The
`NORMAL_TICK` state contract is Vehicle `AVAILABLE`, ParkingSpace `FREE`, and
ChargingPoint `AVAILABLE`.

```bash
curl -i -b /tmp/m6-operator.cookies \
  -H 'Content-Type: application/json' \
  -d '{"seed":42}' \
  http://localhost:8080/api/m6/simulator/scenarios/NORMAL_TICK
```

```bash
curl -i -b /tmp/m6-operator.cookies \
  -H 'Content-Type: application/json' \
  -d '{"seed":42}' \
  http://localhost:8080/api/m6/simulator/scenarios/VEHICLE_FAILURE
```

```bash
curl -i -b /tmp/m6-operator.cookies \
  -H 'Content-Type: application/json' \
  -d '{"seed":42}' \
  http://localhost:8080/api/m6/simulator/scenarios/CHARGER_FAILURE
```

`VEHICLE_FAILURE` emits `VehicleFailedEvent` for demo vehicle ID 1; the current
M3 listener can move that row to `MAINTENANCE`. Run it only against disposable
demo data if that state change should not remain. Check the M3 view with:

```bash
curl -i -b /tmp/m6-operator.cookies http://localhost:8080/api/m3/vehicles
```

The scheduler is off by default. If a group-approved interval is available,
enable it explicitly with `app.simulator.scheduler-enabled=true`,
`app.simulator.interval-ms=<approved milliseconds>`, and
`app.simulator.seed=<demo seed>`. It only runs `NORMAL_TICK`. Do not set a
cadence until M6-D02 is approved. State/dedupe are process-local and are cleared
by a backend restart. Current M4/M5/M8 runtime reactions are not implied by an
event appearing in M6's response/log.

Look for `M6_SIMULATION_STARTED`, `M6_DEVICE_EVENT_PUBLISHED`,
`M6_DEVICE_READING_DUPLICATE`, `M6_DEVICE_READING_REJECTED`, and
`M6_SIMULATION_SUCCEEDED`/`M6_SIMULATION_FAILED` log lines to correlate a run.

Check the authorization boundary: an unauthenticated status request should
return 401; log in as `student1` and the same request should return 403. As
`operator1`, status and valid scenarios should return 200. Invalid input should
return 400 with code `VALIDATION_FAILED`; the scenario details list allowed
scenario names, while body details identify the invalid request or missing seed.

```bash
curl -i -b /tmp/m6-operator.cookies \
  -H 'Content-Type: application/json' \
  -d '{"seed":42}' \
  http://localhost:8080/api/m6/simulator/scenarios/UNKNOWN
```

```bash
curl -i -b /tmp/m6-operator.cookies \
  -H 'Content-Type: application/json' \
  -d '{' \
  http://localhost:8080/api/m6/simulator/scenarios/NORMAL_TICK
```

```bash
curl -i -b /tmp/m6-operator.cookies \
  -H 'Content-Type: application/json' \
  -d '{}' \
  http://localhost:8080/api/m6/simulator/scenarios/NORMAL_TICK
```

If a run fails, status should report `FAILED`, include `completedAt` and the
completed counters, and a later run should be accepted once the guard is
released. A simultaneous second run receives 409 `SIMULATOR_ALREADY_RUNNING`.

Exercise the video-critical cross-module path once the relevant modules exist:
M6 reports a charging-point failure, publishes `ChargerFailed`, M5 reallocates
the charging session, and M4 handles any related parking state. Correlate the
single `eventId` in logs and verify duplicate delivery is harmless.

## Stop And Recovery

Stop only this Compose project:

```bash
docker compose -f docker-compose.yml -f docker-compose.demo.yml down
```

Preserve the database volume by default. If a disposable demo database is
explicitly intended, use `down -v` and record that its data cannot be recovered.

When startup fails, collect `docker compose ... ps` and `docker compose ... logs`
before rebuilding or deleting data. Do not treat repeated rebuilds as diagnosis.

## Enforcement Levels

- Local: commands above are available; each run must report its observed result.
- Hook: no repository-managed required hook is installed.
- CI: `.github/workflows/ci.yml` invokes backend and frontend validation for
  pushes and pull requests targeting `main`.
- Branch protection: external and unverified until inspected in GitHub settings.
