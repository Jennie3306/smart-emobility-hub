# M8 — local setup and integration

Branch: feature/m8-UI_whatif. Integrates M3 6ed46b4 and M6 2e74eb8.

## Start and sign in

From the repository root in PowerShell, run ./scripts/start-m8.ps1.
Open http://localhost:5173/m8/simulation and sign in with operator1 / 123456.
M8 requires OPERATOR. Student accounts receive HTTP 403 for M8.
The login defaults to the Operator demo account and routes by authenticated role.
Use Sign out to switch accounts. Swagger: http://localhost:8080/swagger-ui.html.
Stop with ./scripts/stop-m8.ps1; PostgreSQL and its data remain intact.

## OpenAI configuration

The startup script reads .local/m8-openai-key.dpapi, encrypted with Windows DPAPI
for the current Windows user. Git ignores this file. It decrypts the key only for
the backend launch; credentials never become Java command-line arguments or frontend configuration.
Default endpoint: https://api.openai.com/v1/chat/completions. Model: gpt-4.1-mini.
Override using M8_LLM_URL, M8_LLM_KEY and M8_LLM_MODEL. Never use VITE_* for keys.

The provider receives descriptions and, for recommendations, the snapshot and KPIs.
Requests use JSON mode, bounded output and a 35-second timeout. Parameters are validated.
Recommendations must pass resource/capacity checks and improve deterministic simulation results.
Only dispatch and rerouting proposals are recognized. AI cannot execute operational actions.
Provider failures show sanitized status/error codes and fall back to labeled local rules.
AI configured means credentials are loaded; it does not prove a call succeeded.
A parser result marked AI confirms an actual successful provider response.

Reference: [OpenAI Structured Outputs](https://developers.openai.com/api/docs/guides/structured-outputs?api-mode=chat).

## Implemented behavior

- Description to editable form through LLM or local rules.
- Immutable Hub/vehicle snapshot from PostgreSQL in a REPEATABLE_READ transaction.
- One-minute deterministic engine with seeded arrivals; no LLM or live writes.
- KPIs, open-hub baseline on the same snapshot, chart, history, editing and comparison.
- M6 ResourceStateUpdatedEvent observations displayed separately with receive timestamps.
  These RAM observations are not transactionally synchronized with the database snapshot
  and do not overwrite authoritative M3 vehicle state in the engine.
- Independently verified proposals and persistent Discard.
- Apply returns 409 DISPATCH_NOT_CONNECTED until M7 provides execution and revalidation.
- A 30-minute network check warns when assigned vehicles exceed parking capacity.
  Request-based overload forecasting awaits the M2 demand feed.

## Modeling assumptions

- Requests arrive within arrivalWindowMins using a seeded random schedule.
- Only AVAILABLE vehicles with battery >=20% serve requests; full M2 reservation behavior is not modeled.
- Each trip holds a vehicle for tripMins, then returns to its origin; no charging or battery depletion.
- Hub closure lasts the horizon. Rerouting picks available hubs by ID without travel time.
- Requests still waiting at the horizon count as failed. Mean wait includes served requests only.
- Parking reservations, charging schedules, dispatch travel and ongoing-trip arrivals are not modeled.
- The baseline holds demand, seed and rerouting fixed and reopens the affected hub.
- Dispatch projections assume vehicles moved before the run. Separate proposals are not additive.

History is atomically saved in backend/.local/m8/runs.json, retaining the latest 200 runs.
This single-process file store is temporary, pending TV4's approved M8 database schema.
English display labels for seeded hubs do not change live Hub rows.

## Handoff

- TV2: live demand by Hub/time window; request and reservation semantics.
- TV3: snapshot contract and review of M3 identity/response fixes in this branch.
- TV4: M8 migrations, operational hub state/capacity and parking integration.
- TV7: Apply contract with live revalidation, deduplication and execution status.
  RecommendationApprovedEvent currently lacks dispatch details; M8 does not alter it.
- M1: dashboard monitoring warnings and execution status.

## Verify

From backend, invoke Maven with -B verify. From frontend, run npm.cmd run build.
Tests cover repeatability/conservation, closure, rerouting, battery eligibility, zero demand,
restart persistence, bounds, M8 roles, recommendation feasibility and M3 user-ID spoof prevention.
