# M6 Walking Skeleton Hardening

Date: 2026-10-02

## Status

Completed — implementation and local validation

## Outcome

Fix the errors recorded in `M6-error.md` and satisfy the updated `PLAN (15).md`:
correct the ParkingSpace fixture, make simulator runs recover after exceptions,
return stable client errors, remove clock-resolution assumptions, and align the
class diagram, report, entity proposal, and validation evidence with executable
behavior. Deliver scoped commits on `codex/m6-simulator-hardening` and open a PR
if the configured GitHub tooling is available.

## Context

- Current worktree is based on `main`; `git fetch --prune origin` reports
  `HEAD...origin/main` as `0 0`.
- User-provided `PLAN (15).md` and `M6-error.md` define the requested errors and
  delivery scope.
- `DESCRIPTION/Template_report/Template_M4.md` and
  `DESCRIPTION/Tong_quan_de_tai_va_Quy_dinh_chung.md` establish ParkingSpace
  states `Free`, `Reserved`, `Occupied`, and `Blocked`.
- Existing M6 code/tests, `docs/WORKFLOW.md`, `docs/ARCHITECTURE.md`,
  `docs/product/m6-devops-integration.md`, and open M6 decisions are the local
  implementation and ownership authorities.
- Draw.io skill governs native diagram editing/export.

## Scope

In scope:

- Correct simulator state and lifecycle error handling, controller input error
  mapping, and relevant deterministic/concurrency/security tests.
- Synchronize the referenced class diagram and its embedded-XML PNG with actual
  code and visibly separate implemented behavior from proposals.
- Correct report signatures, state distinctions, fixture timestamps, test
  evidence, and entity handoff wording; preserve unapproved decisions as open.
- Re-run focused/full backend checks, frontend checks, Compose config, isolated
  runtime smoke, and `git diff --check`; stage explicit M6 paths only.
- Use two scoped commits and push/open a feature-branch PR if credentials and
  repository configuration permit.

Out of scope:

- Changes to shared consumer dedupe, M3/M4/M5/M8 production handlers, global
  `GlobalExceptionHandler`, Incident persistence, JPA entities, or migrations.
- New product policy, scheduler cadence, reason-code taxonomy, durable event
  broker, or atomic after-commit behavior.
- Staging unrelated Harness, template, legacy Excalidraw, or duplicate PNG files.
- Merging the PR or changing branch protection.

## Approach

1. Preserve all existing worktree changes; work on the requested feature branch.
2. Fix simulator guard/failure state and the ParkingSpace fixture; add focused
   service, pipeline, controller, and security regressions.
3. Update only the M6 class diagram source and its referenced `*.drawio.png`;
   export with Draw.io Desktop, inspect the rendered result, and validate XML.
4. Update Template_M6, the open-decision entity/review package, and execution
   evidence. Keep proposals separate from implemented behavior.
5. Run the exact focused and repository validation from PLAN (15), then isolated
   HTTP smoke for success, authorization, and bad-input responses.
6. Stage explicit M6 implementation/documentation paths, inspect staged names
   and diffs, create the two prescribed commits, push the feature branch, and
   create/attach a PR only if GitHub access is available.

## Risks And Recovery

- Runtime or Compose smoke can mutate fixture data; use a unique disposable
  Compose project and remove only resources created for this run.
- Diagram edits may be unreadable if layout changes too broadly; preserve the
  current design language and inspect the actual export before staging.
- Existing worktree contains unrelated Harness, templates, diagrams, and
  reports. Never use `git add .`, reset, clean, or discard another change.
- Shared authorization, event, schema, and ChargingPoint ownership decisions
  remain open; do not implement those policies here.
- If remote state changes during work, rebase/update only when safe and rerun
  all required checks; preserve local dirty files.
- If GitHub access is unavailable, leave local commits/branch intact and report
  that push/PR could not be completed.

## Progress

- [x] Read updated plan, error log, workflow, and repository state.
- [x] Fetch origin and confirm base equals `origin/main` (`0 0`).
- [x] Create `codex/m6-simulator-hardening` without disturbing dirty files.
- [x] Fix ParkingSpace state and simulator failure/concurrency recovery.
- [x] Return HTTP 400 for invalid scenario/body; retain 401/403/409/200 behavior.
- [x] Remove timestamp-resolution assertion and update timestamp wording.
- [x] Correct class diagram source and Draw.io export; inspect visual/XML output.
- [x] Update Template_M6 and entity proposal with implemented/proposed/pending
  boundaries and real verification counts.
- [x] Run focused/full backend, frontend, Compose, isolated runtime smoke, and
  whitespace checks.
- [x] Stage only listed M6 files and create the two scoped commits. Push the
  feature branch and open the review PR as the delivery step.

## Decisions

- 2026-10-02: Use `FREE` for the simulated ParkingSpace state, following M4's
  state vocabulary; do not add general state-transition validation while
  ChargingPoint ownership remains open.
- 2026-10-02: Keep the existing event bus, security boundary, and persistence
  ownership; scope fixes to M6 controller/service and documentation.
- 2026-10-02: Do not require timestamps from adjacent factory calls to differ;
  fresh inbound UUIDs identify readings, and timestamps record observation time.

## Validation

- Focused proof: service recovery, concurrent-run conflict, state/event payload,
  malformed/unknown/missing request inputs, and 401/403/409/200 API boundaries.
- Repository proof: Maven verify including module boundary; frontend test/build;
  Compose config and diff checks.
- Runtime proof: disposable PostgreSQL Compose app exercises simulator routes
  and M3 VehicleFailed reaction; invalid inputs and authorization checked over
  HTTP, with correlated logs captured.

## Result

Implementation and local validation passed on 2026-10-02. Focused backend tests:
21/21; full `mvn -B verify`: 30/30 (0 failures, errors, or skips); frontend
`npm ci`, test command (no test files, exit 0), and production build passed;
Compose config passed. Isolated runtime smoke confirmed 401/403/200 authorization,
three successful scenarios, M3 VehicleFailed reaction, 400 validation responses,
frontend 200, and correlated run/inbound/outbound IDs. Only the disposable smoke
Compose project, its volume, network, and images were removed. Changes are split
into the two scoped commits described by the plan; pushing the branch and opening
the review PR are delivery steps.
