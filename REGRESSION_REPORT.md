# M8 What-if fixes: regression report

- Date: 2026-11-15
- Branch: `feature/m8-UI_whatif`
- HEAD: `242857f68721ad6075170f323fefa5e49c55c0b2` plus **uncommitted fixes** (13 modified tracked files, 12 untracked; the four new/changed test files plus `AgentFallbackTest.java` and `simulationApi.test.ts` are part of the fix set; PDFs, `AGENTS.md`, `backend/package-lock.json` are unrelated untracked files)
- Run by: independent Sonnet agent (read-only on product code; only this file was written)

## Verdict: PASS (no confirmed regression found; 3 risks and 2 notes below)

## Commands run

| Command (cwd) | Exit | Result |
|---|---|---|
| `git rev-parse HEAD`, `git status --short`, `git diff --stat` | 0 | 13 files changed, +269/-17 (tracked) |
| `mvn test` (backend, full) | 0 | Tests run: **302**, Failures 0, Errors 0, Skipped 0; BUILD SUCCESS. 45 test classes reported by this run's log |
| `npx vitest run` (frontend) | 0 | 8 files, **39 passed**, 0 failed |
| `npx tsc --noEmit -p .` (frontend) | 0 | no errors |
| `npm run build` (frontend; `tsc --noEmit && vite build`) | 0 | built OK (output in git-ignored `frontend/dist`) |
| `npm run lint` | not run | no `lint` script in `frontend/package.json` |

The ERROR lines in the Maven log (M2/M3/M6 "simulated failure", unique-index violation) come from tests that deliberately force failures; the suite reports no failures.

## Per-fix verification

| Fix | Covering test(s) | Result | Not covered |
|---|---|---|---|
| ISSUE-001 (demo IoT seed 42 to 100) | `simulationApi.test.ts` "uses the seed that matches the seeded fleet" | pass | No browser replay and no live M6/M3 check that EB-001 stays AVAILABLE. Code evidence only: `application.yml` comment says seed 100 reports 92% for EB-001 and seed 42 reports 15%. Backend `app.simulator.seed` is 100. |
| ISSUE-002a (discard withdraws M7 proposal) | `DispatchApplyTest.discardWithdrawsRegisteredM7Proposal`, `discardAfterM7ApprovalFollowsM7`, `discardWithoutM7ProposalDoesNotCallM7`; `RecommendationServiceTest.withdrawRejectsPendingProposal`, `withdrawKeepsApprovedProposal` | pass (302-test run) | M7 is a Mockito mock in `DispatchApplyTest`. The real `RecommendationService` is tested separately with mocked repositories. No integration test that M8 discard makes M7 status REJECTED in a real DB. No controller-level test of the changed discard route (`SimulationController.java:30`, now needs a `Principal`). |
| ISSUE-002b (apply reconciles with M7, NEEDS_REVIEW recoverable) | `DispatchApplyTest.resolvedExternallyFollowsM7DecisionWithoutCreatingAnotherProposal`, `missingM7RecommendationStillRequiresReview`, `applyAfterM7RejectClosesProposalInsteadOfSticking`, `stuckReviewIsResolvedFromM7Decision`, `unconfirmedReviewRetriesApprovalWhenM7IsStillPending` | pass | Same mock limit. `PARTIAL` from M7 via reconcile has no dedicated test. Frontend Discard-enabled-for-NEEDS_REVIEW (`SimulationPage.tsx:246`) has no UI test; only `tsc` and build cover it. |
| ISSUE-003 (local fallback on PROVIDER_ERROR / INVALID_RESPONSE) | `AgentFallbackTest.providerErrorFallsBackToLabeledLocalRules`, `modelThatStopsKeepsToolAgentWithoutLocalRules` | pass | Uses mocked `AgentPlanner` and `ScenarioAssistant`. No retry-after-fallback test (see Risk 1). No real provider call. |
| ISSUE-006 (unreadable body to 400) | `SimulationSecurityTest.malformedScenarioBodyIsBadRequest` | pass | Only the M8 `/api/m8/runs` route is tested. The handler is global, so other modules' malformed bodies now also return 400 (intended, not tested elsewhere). |

## Regressions and risks

No Confirmed regression.

1. **Risk: stale fallback label after a successful agent retry.** `SimulationService.java:55-63` stores local-rule recommendations and the warning "AI planning failed (...); showing local-rule recommendations instead." in the run. `saveAgentLocked` (`SimulationService.java:98-104`) then keeps `r.warnings()` and `r.recommendations()` but sets the source to `"TOOL_AGENT"`. If a retry later produces a valid best plan, the `Run` constructor (`SimulationModels.java:117-133`) adds `agent-` dispatch recommendations next to the old local ones. The UI would then show "AI tool exploration" and the "AI planning failed" warning together with both sets of plans. Not reproduced (no test or run exercises retry after fallback). The retry endpoint itself still works: `ensurePlanningEditable` only blocks on PARTIAL/APPLIED/APPROVED/NEEDS_REVIEW, and local recs are PENDING.
2. **Risk: M7 call is made while holding the RunStore lock.** `DispatchApplyService.discard` (`:78-97`) and `apply` call M7 inside `synchronized (store)`. This matches the existing `apply` pattern, so it is not new in kind. A slow M7 transaction now also blocks Discard and other run reads and writes. Not reproduced.
3. **Risk: `RedistributionRecommendations` gained two abstract methods.** The only implementer is `RecommendationService`. The only other users are `DispatchApplyService` and Mockito mocks in `DispatchApplyTest`; no other module or hand-written test double implements it (grep over `backend/src`). So nothing breaks today, but any out-of-tree implementer would. `RecommendationController` is unchanged and still uses `reject` and `ApprovalResult` directly. The existing M7 tests all pass.
4. Note: `SimulationService.discard`/`discardLocked` (`SimulationService.java:72-82`) is now unused by any caller (the controller moved to `DispatchApplyService.discard`). It is dead code. It also keeps the old status-PENDING-only rule without M7 sync, so it should not be reused.
5. Note: Discard on a recommendation with no M7 id (reroute, never-applied dispatch) still works. `DispatchApplyService.discard` takes the `m7Id == null` path, saves DISCARDED and does not call M7 (`discardWithoutM7ProposalDoesNotCallM7`). One difference: the saved recommendation now carries a non-null empty `dispatch` object (`tasks=[]`) where the old path left it null. The frontend renders `r.dispatch` guarded, showing "0/N tasks created" on a discarded card. Cosmetic, not confirmed in a browser.

## Known open items (out of scope, not failures)

ISSUE-002c (M8 never reaches APPLIED when M7 tasks finish), ISSUE-004, ISSUE-005, ISSUE-007, and `injectedRequests: 1.5` still being coerced to 1.

## Limits

- No live API or browser replay was done. No database or backend instance was started; the user's instance on 8080/5173, their DB and `backend/.local/m8/runs.json` were not touched.
- Backend results come from one full `mvn test` run, with H2/in-memory test config as configured by the repo. Nothing was run against PostgreSQL.
- Seed-100 behavior (ISSUE-001) is verified by the unit test on the request payload and by reading `application.yml`, not by observing M3 vehicle state.
- No lint run (no script). `npm run build` wrote to the git-ignored `frontend/dist`.
- Review of the diff was by reading code. Risks 1 to 3 and note 5 are unreproduced.

---

## Addendum: ISSUE-002c and ISSUE-007 (2026-11-15, self-run by the implementing Opus session, not independent)

Changes: M7 `Decision` now carries done/failed task counts; `GET /api/m8/runs` follows M7 (`DispatchApplyService.sync`) and marks a plan `APPLIED` once all its M7 tasks finished with at least one DONE (all FAILED stays `APPROVED` with a message). UI: plurals, "Device state updates" wording, AI panel shows "Exploring…" only during an AI retry, login returns to the requested page (`?next=`, same-site paths only), M8 link opens and highlights the M7 recommendation, History shows a Dispatch column.

| Command | Result |
|---|---|
| `mvn test` (backend, full) | exit 0 · 307 tests, 0 failures, 0 errors |
| `npx vitest run` (frontend, full) | 9 files, 42 tests passed |
| `npx tsc --noEmit -p .` | exit 0 |
| `npm run build` | exit 0 |

New tests: `DispatchApplyTest` (4 sync cases), `RecommendationServiceTest.decisionCountsDoneAndFailedTasks`, `text.test.ts` (plural, dispatch summary, safe login redirect).
Not covered: no browser replay of the ISSUE-007 UI changes or the APPLIED transition; M7 is a mock in `DispatchApplyTest`.

## Browser verification (2026-11-15, implementing Opus session, built-in browser)

Isolated stack: Postgres container `emobility-verify-db` (127.0.0.1:5544, Flyway seed), backend from current sources via `spring-boot:run` on :8090 with a scratch `app.m8.store-file`, Vite on 127.0.0.1:5174 (temporary config, deleted). The user's app on 8080/5173 was not touched. Demo account `operator1`. All torn down afterwards.

| Item | Observation | Result |
|---|---|---|
| ISSUE-001 | "Read Demo IoT Observations" → backend log `NORMAL_TICK seed=100`; EB-001 stayed 92% AVAILABLE in DB | pass |
| ISSUE-007 login | `/m8/simulation` signed out → `/login?next=%2Fm8%2Fsimulation` → after sign-in back on `/m8/simulation` | pass |
| ISSUE-007 AI panel | Page busy during Apply; "Exploring… / Testing plans" never rendered (MutationObserver) | pass |
| ISSUE-007 text | "Dispatch 8 vehicles", Device state updates heading/notice, History Dispatch column | pass |
| ISSUE-007 M7 link | `?recommendation=2` opened "Recently decided", item highlighted and in view; open row #5 highlighted | pass |
| ISSUE-002c | Apply → 8/8 tasks; 7 DONE → M8 APPROVED "7 of 8 tasks finished"; 8th FAILED → M8 APPLIED "7 of 8 tasks completed in M7; 1 failed.", History "Completed in M7", buttons locked | pass |
| ISSUE-002a | M7 held #4 PENDING (0/7, donor vehicles busy); Discard in M8 → M7 #4 REJECTED "Discarded in M8 What-if simulation" | pass |
| ISSUE-002b | #5 PENDING; Reject in M7 UI → reopening M8 shows DISCARDED "M7 recommendation #5 was rejected", no NEEDS_REVIEW | pass |
| Mobile 375 px | scrollWidth = 375, no horizontal overflow | pass |
| Console | only 401 before login and a 404 from a verifier probe of a non-existent GET endpoint | pass |

Limits: runs and M7 task status changes were created through the app's own API from the page (`fetch`) to save clicks; Apply, Discard, Reject and navigation used the UI. `window.prompt` was stubbed for the M7 reject reason. Mouse clicks after reloads timed out in the pane, so some buttons were clicked via DOM `click()` (same React handlers). No real LLM provider, no keyboard/focus pass.
