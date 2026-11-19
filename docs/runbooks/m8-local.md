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
Requests use JSON mode and bounded output. The timeout is 90 seconds for the responses endpoint
(scenario extraction) and 35 seconds for chat completions (tool calls). Parameters are validated.
Recommendations must pass resource/capacity checks and improve deterministic simulation results.
Only dispatch proposals are recognized; rerouting stays an operator setting. AI cannot execute
operational actions. When AI is configured, each run lets it test up to three dispatch plans with the
simulate_plan tool on the saved snapshot; Retry adds three more rounds. Only plans that reduce
unserved requests (rental requests not served + parking turned away + unserved charging) are kept.
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
- Independently verified proposals and persistent Discard. A proposal is kept only if it lowers the total of
  unserved rental requests, parking turned away and unserved charging requests.
- Per-hub parking and charging scenarios (section "Parking and charging at this hub"):
  `parkingArrivals` and `parkingStayMins` (private vehicles arriving to park), `chargingRequests` and `chargeMins`
  (charging sessions), `failedChargers` (chargers out of order for the whole horizon).
  Parking, charging and failed chargers are hypothetical demand; leave the inputs at 0 to leave them out of a run.
- Parking: each arrival takes a free space of the captured pool (used/free/total from the snapshot) and releases it after
  the stay; with no free space it is turned away. Charging: requests queue first-come-first-served on chargers that are
  operational, not busy and not failed.
- KPIs added: parking requests, served and turned away; peak parking occupancy per hub; charging requests, served and
  unserved; mean charging wait (served requests only); peak charging queue; usable chargers and charger utilization per hub.
- The local rule can propose dispatching rental vehicles out of a parking-saturated hub. When charging requests go
  unserved and another open hub has free chargers, the run also shows a `charging_redirect` recommendation:
  advice to redirect charging requests to that hub. It is display only: Discard is available, Apply is refused
  with NOT_APPLICABLE, and M5 is never changed.
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
- Each trip holds a vehicle for tripMins, then returns to its origin; no battery depletion.
- A hub can be closed from a given minute and optionally reopened. Rerouting (only during a
  closure) picks available hubs by ID without travel time.
- Requests still waiting at the horizon count as failed. Mean wait includes served requests only.
- Parking reservations, travel time, dispatch travel and ongoing-trip arrivals are not modeled.
- Rental trips do not change the parking pool, and charging does not change vehicle batteries.
- Validation limits per hub: injected requests 0-10000; arrival window 1-1440 min; trip 1-240 min;
  parking arrivals 0-10000; parking stay 0-1440 min; charging requests 0-10000; charge duration 0-480 min;
  failed chargers 0-1000. A stay (or charge duration) of at least 1 minute is required when parking arrivals
  (or charging requests) are set, parking arrivals need parking data for the hub in the snapshot, and failed chargers
  cannot exceed the hub's operational chargers.
- The baseline holds demand, seed and rerouting fixed and reopens the affected hub.
- Dispatch projections assume vehicles moved before the run. Separate proposals are not additive.
- Dispatch is limited by the captured free rental-bike spaces at the destination minus incoming M7 tasks.
  Without that observation the dispatch proposal cannot be applied (RENTAL_PARKING_UNKNOWN); rerouting is unaffected.

History is atomically saved in backend/.local/m8/runs.json, retaining the latest 200 runs.
This single-process file store is temporary, pending TV4's approved M8 database schema.
English display labels for seeded hubs do not change live Hub rows.

## Applying a dispatch proposal

Buttons: Review dispatch (opens a confirmation), Create M7 tasks (sends the proposal to M7),
View M7 recommendation (opens it in M7) and Discard. Charging redirect advice shows Discard only.
Recommendation states: PENDING (history label "Not applied"), NEEDS_REVIEW ("Needs review": the M7 approval was not
confirmed; check M7), PARTIAL ("Partly dispatched": only some tasks were created), APPROVED ("Approved in M7"),
APPLIED ("Completed in M7") and DISCARDED ("Discarded").
Apply returns HTTP 409 with these codes: HYPOTHETICAL_FLEET (run used edited vehicle counts),
DISPATCH_REVIEW_REQUIRED (another proposal of the run is NEEDS_REVIEW), NOT_APPLICABLE (not a pending dispatch)
and RENTAL_PARKING_UNKNOWN (no rental-bike parking observation for the destination).
Discard returns ALREADY_RESOLVED when the proposal is already resolved.

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
restart persistence, bounds, M8 roles, recommendation feasibility, parking and charging scenarios,
dispatch apply states and M3 user-ID spoof prevention.

## Latest verification (16 November 2026)

- Backend `mvn test`: 330 tests, 0 failures (92 in the M8 package, 13 test classes).
- Frontend Vitest: 47 tests passed. `npm run build` passes.

## Local verification — 27 October 2026

- Backend Maven verify passed. V10, V11 and V20 are applied to the existing
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
- Dispatch projections respect the captured free rental-bike spaces at the destination minus
  incoming M7 tasks. Historical snapshots without that observation cannot produce new dispatch
  proposals or be applied. Rerouting remains available.
  This is a conservative simulation guard, not authoritative M4 parking approval.
  Previously saved recommendations retain their historical results and limitations.

Regression checks: frontend scenario-draft tests; backend RecommendationTest covers a
40-space destination with zero/one observed spaces and no assigned rentals, plus missing
observations. M8 database persistence remains pending; M7 execution is integrated (see Applying a dispatch proposal).

After fixes: backend packaging succeeded after releasing the running Windows JAR lock, and the
frontend regression tests and production build passed.
Browser checks confirmed field errors and disabled Run for invalid horizon/negative requests,
restoration from zero-booking mode to manual 100 requests via Try this plan, and a saved
reroute run with 100 requests / 12 served / 88 failed on the same snapshot. Editing to 500
showed Draft changed and an explicit previous-results notice. At 320px, the comparison
region scrolls internally (243px client / 270px content) with no document overflow.
The two QA-fix runs remain in history; no booking, Apply or live-resource mutation occurred.
