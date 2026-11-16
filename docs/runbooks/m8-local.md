# M8 — local setup and integration

M8 is merged into main and runs against the shared PostgreSQL database with the other modules.

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
Default model: gpt-6.1-sol through https://api.openai.com/v1/responses; other models use
https://api.openai.com/v1/chat/completions.
Override using M8_LLM_URL, M8_LLM_KEY and M8_LLM_MODEL. Never use VITE_* for keys.

The provider receives descriptions and, for recommendations, the snapshot and KPIs.
Requests use JSON mode, bounded output and a 35-second timeout. Parameters are validated.
Recommendations must pass resource/capacity checks and improve deterministic simulation results.
Only dispatch proposals are recognized; rerouting stays an operator setting. AI cannot execute
operational actions. When AI is configured, each run lets it test up to three dispatch plans with the
simulate_plan tool on the saved snapshot; Retry adds three more rounds. Only plans that reduce
unserved requests are kept.
Provider failures show sanitized status/error codes and fall back to labeled local rules.
AI configured means credentials are loaded; it does not prove a call succeeded.
A parser result marked AI confirms an actual successful provider response.

Reference: [OpenAI Structured Outputs](https://developers.openai.com/api/docs/guides/structured-outputs?api-mode=chat).

## Implemented behavior

- Description to editable form through LLM or local rules.
- Immutable Hub/vehicle snapshot from PostgreSQL in a REPEATABLE_READ transaction.
- One-minute deterministic engine with seeded arrivals; no LLM or live writes.
- KPIs, open-hub baseline on the same snapshot, chart, history, editing and comparison.
- ResourceStateUpdated events from M4 (parking spaces) and M5 (chargers) displayed separately
  as "Device state updates" with receive timestamps.
  These RAM observations are not transactionally synchronized with the database snapshot
  and do not overwrite authoritative M3 vehicle state in the engine.
- Independently verified proposals and persistent Discard.
- Apply sends a dispatch proposal to M7 through `RedistributionRecommendations` (propose + approve).
  M7 picks the vehicles, checks parking and creates the tasks. Discard withdraws a pending M7 proposal.
  When runs are listed, M8 follows M7: a rejected proposal becomes DISCARDED, and an approved one
  becomes APPLIED once all its tasks have finished with at least one vehicle delivered.
- Runs on an edited (hypothetical) fleet can be simulated but not applied.
- Accepted M2 bookings from the captured 30-minute window can supply the scenario volume.
  The saved snapshot retains that window, so editing an old run repeats its original volume.
- A 30-minute network check repeats recent accepted bookings against current resources,
  without rerouting, and reports projected unserved requests on M8 and the M1 dashboard.
  Run check now refreshes this projection immediately. The dashboard polls status every
  30 seconds; this does not change the backend check interval.
- M1 parking/charger observations appear separately from assigned PostgreSQL vehicles.
  M6 AVAILABLE charger events are normalized to M1 FREE; they do not change vehicle state.
- Student search loads Hub choices from M1. Reservation pickup creates an M3 trip;
  return completes the reservation, and cancellation releases a reserved vehicle.
- Compact M8 navigation keeps simulation, history/comparison and snapshot accessible
  when the sidebar is hidden. SockJS uses the browser global alias in Vite.

## Modeling assumptions

- Requests arrive within arrivalWindowMins using a seeded random schedule.
- Only AVAILABLE vehicles with battery >=20% serve requests; full M2 reservation behavior is not modeled.
- Accepted bookings are not total demand: rejected attempts and searches are excluded.
  Cancelled or completed bookings still count as historical accepted arrivals.
- Each trip holds a vehicle for tripMins, then returns to its origin; no charging or battery depletion.
- A hub can be closed from a given minute and optionally reopened. Rerouting (only during a
  closure) picks available hubs by ID without travel time.
- Requests still waiting at the horizon count as failed. Mean wait includes served requests only.
- Parking reservations, charging schedules, dispatch travel and ongoing-trip arrivals are not modeled.
- The baseline holds demand, seed and rerouting fixed and reopens the affected hub.
- Dispatch projections assume vehicles moved before the run. Separate proposals are not additive.

History is atomically saved in backend/.local/m8/runs.json, retaining the latest 200 runs.
This single-process file store is temporary, pending TV4's approved M8 database schema.
English display labels for seeded hubs do not change live Hub rows.

## Handoff

- TV2: booking persistence and the accepted-booking read contract are integrated;
  total attempted demand remains unavailable.
- TV3: review the local rental lifecycle/event and snapshot contract changes.
- TV4: review V20 M2 persistence migration already applied locally; approve the separate
  M8 schema and operational hub state.
- TV7: Apply uses `RedistributionRecommendations` (propose, approve, decision, withdraw).
  The donor keep-back differs: M8 keeps 1 vehicle, M7 keeps 4, M1 warns at 2. Needs one agreed value.
- M1: booking-risk warnings are integrated.

## Verify

From backend, invoke Maven with -B verify. From frontend, run npm.cmd run build.
Tests cover repeatability/conservation, closure, rerouting, battery eligibility, zero demand,
restart persistence, bounds, M8 roles, recommendation feasibility and M3 user-ID spoof prevention.

## Local verification — 27 October 2026

- Backend Maven verify passed 88 tests. V10, V11 and V20 are applied to the existing
  PostgreSQL volume. No database volume was reset.
- Frontend TypeScript and production build passed after the compact navigation and
  SockJS fixes. Browser verification covered M1 live connection, M8 booking volume,
  saved-run loading, KPI comparison, history and snapshot navigation at an 800px viewport. Student login reached search with live Hub choices and vehicle results; no active reservation remained.
- Real HTTP/PostgreSQL smoke: reservation -> RESERVED -> pickup/IN_USE -> return/COMPLETED;
  a second booking -> cancellation/AVAILABLE. Wrong-owner pickup and return both returned
  403, as did student access to M8. The demo vehicle's Hub and battery were restored.
- M6 NORMAL_TICK reached M1 with a FREE charger. M8 repeated the two accepted demo
  bookings, ignored a conflicting manual volume, saved KPIs and received an LLM proposal.
  Simulation left the live vehicle records unchanged. The monitor projected one unserved
  request if those two bookings repeated; the M1 dashboard displayed the warning.
- Smoke evidence is ignored local data: .local/integration-smoke-results.json and
  .local/integration-verify.log. Two retained demo booking records contribute to the
  rolling 30-minute window until they age out; this is test traffic, not real demand.

For a demo, open History & comparison and View & edit on
Integration demo - repeat recent bookings. Compare it with another saved run, or use
Review dispatch to send a proposal to M7. Saving a new run can call the configured LLM.
M8 history is file-backed.

## Exploratory QA fixes — 27 October 2026

- Trying a rerouting plan restores the saved run's demand mode and snapshot together.
  The displayed effective request count is also the submitted count. A manual 100-request
  plan stays manual even after the operator temporarily selects a zero-booking window.
- Numeric bounds, empty numbers, scenario names and arrival-window/horizon relationships
  are validated before running, with field feedback and a reason when Run is disabled.
- Draft changes mark the previous KPIs/recommendations as results from the previous run.
  Editing or changing context clears obsolete success/error notices.
- KPI comparison scrolls within its own region; long scenario headings can wrap.
- Dispatch projections respect the smaller of assigned-vehicle headroom and captured M1
  free parking (also bounded by total minus used). Historical snapshots without occupancy
  observations cannot produce new dispatch proposals. Rerouting remains available.
  This is a conservative simulation guard, not authoritative M4 parking approval.
  Previously saved recommendations retain their historical results and limitations.

Regression checks: frontend scenario-draft tests; backend RecommendationTest covers a
40-space destination with zero/one observed spaces and no assigned rentals, plus missing
observations. M7 execution and M8 database persistence remain pending.

After fixes: 89 backend tests passed; backend packaging succeeded after releasing the
running Windows JAR lock. Five frontend regression tests and the production build passed.
Browser checks confirmed field errors and disabled Run for invalid horizon/negative requests,
restoration from zero-booking mode to manual 100 requests via Try this plan, and a saved
reroute run with 100 requests / 12 served / 88 failed on the same snapshot. Editing to 500
showed Draft changed and an explicit previous-results notice. At 320px, the comparison
region scrolls internally (243px client / 270px content) with no document overflow.
The two QA-fix runs remain in history; no booking, Apply or live-resource mutation occurred.
