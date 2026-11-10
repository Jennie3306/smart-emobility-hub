# M8 — local setup and integration

Branch: feature/m8-UI_whatif. Includes M1 1656510, M2 5e59464, M3 6ed46b4
and M6 5583c1c, with integration fixes. Integration/QA through ade654b was pushed as WIP;
newer tool-planner work remains on this feature branch until explicitly published.

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
Default endpoint: https://api.openai.com/v1/chat/completions. Model: gpt-5.6-sol.
The GPT-5 request omits temperature because this model rejects temperature=0.
Override using M8_LLM_URL, M8_LLM_KEY and M8_LLM_MODEL. Never use VITE_* for keys.

The provider receives descriptions and, for recommendations, the snapshot and KPIs.
Parser requests use JSON mode; planning uses native function tools. Both have bounded
output and a 35-second timeout per model call. Parameters are validated.
Recommendations must pass resource/capacity checks and improve deterministic simulation results.
Only dispatch and rerouting proposals are recognized. AI cannot execute operational actions.
Parser failures fall back to labeled local rules. Agent failures retain verified plans
and require explicit Retry; no hidden fallback is presented as an AI tool result.
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
- Apply requires an M4 parking adapter. Without one, the UI disables Apply and the API
  returns `409 PARKING_NOT_CONNECTED` for a pending dispatch recommendation.
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
- Only the affected hub generates requests. Donor and alternate hubs have no independent
  demand in a scenario, so moving vehicles or rerouting can overstate the real benefit.
- Only AVAILABLE vehicles with battery >=20% serve requests; full M2 reservation behavior is not modeled.
- Accepted bookings are not total demand: rejected attempts and searches are excluded.
  Cancelled or completed bookings still count as historical accepted arrivals.
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

- TV2: booking persistence and the accepted-booking read contract are integrated;
  total attempted demand remains unavailable.
- TV3: review the local rental lifecycle/event and snapshot contract changes.
- TV4 / M4: the parking integration is the remaining dependency for Apply. When M4
  publishes the code, map its existing API to `DispatchParking` in M8. A capacity read
  alone does not reserve a space. M8 scenario history is still stored locally.
- TV7 / M7: M8 already calls the existing per-vehicle task API. Keep that API unless
  verification uncovers a contract issue. M8 owns the recommendation and its task IDs.
- TV1 / M1 and TV3 / M3: verify dashboard counts and vehicle location after M7 emits
  `DispatchCompletedEvent`. Showing newly created tasks on the dashboard is separate.

### Apply dispatch contract

- M8 reads fresh inventory and active M7 tasks. It selects available source vehicles
  with at least 20% battery in ID order, leaving one eligible vehicle at the source.
- `DispatchParking` is the M8 port for the M4 integration. Its adapter must hold a
  destination space per attempt, link it to the created M7 task and release it after a
  definitive rejection. M4 must handle completion, cancellation and failed tasks.
  An adapter may return a business rejection from `hold` only if it created no hold.
- After a hold, M8 calls `POST /api/m7/redistributions` once per vehicle with the
  operator session. Each returned task ID is recorded under the saved recommendation.
  A definitive rejection can leave a partial result. Retry skips confirmed task IDs.
- An intent is saved before calling M4 or M7. If a response is lost, that recommendation
  requires manual review in M7 and M4. There is no automatic retry for an uncertain
  outcome because the per-vehicle M7 endpoint accepts no idempotency key. The local
  run file serializes requests in one process. Shared deployment needs durable storage.
- Apply creates tasks in `CREATED`. M7 publishes the shared `DispatchCompletedEvent`
  after each task reaches `DONE`; M3 moves the vehicle and M1 updates the dashboard.
  AI plan cards apply individual dispatch actions. Their displayed KPI is for the full
  tested plan. Rerouting remains a simulation setting.

### Final integration checklist

1. Review the latest M4 and M7 commits. Identify the actual M4 operations for holding
   a destination space, associating the hold with an M7 task, and releasing or completing
   it. Check operator authorization, concurrent holds, expiry and retry behavior.
2. Add the adapter in M8 against those operations. Reuse M7 `GET /api/m7/redistributions`
   for active tasks and `POST /api/m7/redistributions` with `vehicleId`, `sourceHubId` and
   `destinationHubId` to create each task. Enable Apply only with the real parking adapter.
3. In an isolated environment, verify successful Apply, a full destination, partial
   creation, duplicate Apply, a lost response, and parking cleanup after failure. Follow
   a created task through `IN_PROGRESS` and `DONE`, then check M3 location and M1 counts.
4. Keep final formatting and integration edits in M8 and its documentation. If a teammate's
   module needs a change for a verified integration issue, keep that diff limited to the
   affected API or event and avoid reformatting adjacent code. Follow repository ownership
   rules for shared events and migrations.

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
Try this plan to preview rerouting. Saving a new run can call the configured LLM.
M7 Apply is unavailable, M4 parking validation is pending, and M8 history is file-backed.

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

## Tool-calling AI planner — 1 November 2026

When AI is configured, Save & run starts a bounded planning session instead of the old
single-pass recommendation request. The model calls `simulate_plan`, receives deterministic
KPIs or validation feedback, then can revise the complete candidate on the same snapshot.
Each batch allows at most **three model turns**, with one tool call per turn and at most
four actions per plan. A final text response or provider error can end a batch early.
The last tool result at the limit is included in the next Retry context.

The AI planning panel shows each round, tested actions, validation errors and KPIs. It
retains the best improving plan (fewer failed requests, then lower mean wait); a later bad
plan or provider failure cannot replace it. Invalid proposals never receive invented KPIs.
The original run's KPIs remain visible separately from the best plan's projected KPIs.

`Retry · +3 rounds` explicitly permits up to three more model turns on the **saved**
scenario/snapshot, including its previous attempts and best plan. Edited draft fields are
not used by Retry; Save & run creates a new run instead. Older saved runs can start a
session with Start AI planning. Simultaneous retries on one run and stale round versions
return HTTP 409; Refresh loads the latest saved planning state without changing the draft.

Only `reroute` and hypothetical `dispatch` actions exist. Demand, seed, closure and time
parameters cannot be changed through the tool. Every candidate starts from a fresh copy,
combined actions share conservative parking/donor limits, and a vehicle cannot be moved
twice within a plan. No live execution tool is exposed. M4/M7 limitations still apply.

Sessions are saved with the existing file-backed history. The latest 60 trace entries and
best plan are retained; the provider receives at most nine recent paired tool exchanges
plus the frozen scenario/snapshot and previous best. Provider errors do not trigger hidden
retries. Each request times out after 35 seconds; a three-turn batch can take about two
minutes. The current GPT-5.6 Sol chat provider requires `reasoning_effort: none` with
function tools; parser behavior and the configured model/key are preserved.

Endpoint: `POST /api/m8/runs/{id}/agent/retry?expectedRounds={totalRounds}` (Operator only).
Protocol reference: [OpenAI function calling](https://developers.openai.com/api/docs/guides/function-calling).

Live evidence: Agent loop demo - closed Metro Hub (`bc47d41c-9a78-4ad2-a512-bd08b7c3bf6d`).
After one provider-configuration failure, explicit Retry made three actual tool calls:
rerouting served 12/100, then two dispatch candidates were rejected (donor retention and
closed source). Best failure count stayed 88. A UI Retry consumed one further turn and
the model stopped early; saved history reached five total rounds. This is one live
scenario, not a broad model-quality benchmark.
