import os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
from lib import *
O=png_dir('m8')
usecase(O+'m8_usecase.png','«subsystem» M8 — What-if Simulation',
 [('UC-M8-01','Prepare a scenario|(describe or edit)'),('UC-M8-06','Validate the|scenario'),('UC-M8-02','Run a simulation|on a snapshot'),
  ('UC-M8-03','Review and compare|saved runs'),('UC-M8-04','Review recommendations|(discard / send to M7)'),('UC-M8-05','Check recent booking|volume every 30 min')],
 left=[('op','Operator',False),('tm','Timer',True)], right=[('ai','AI provider (optional)',True),('m7','M7 Redistribution',True),('own','M1–M4 data',True)],
 assoc=[('op','UC-M8-01'),('op','UC-M8-02'),('op','UC-M8-03'),('op','UC-M8-04'),('tm','UC-M8-05'),('ai','UC-M8-01'),('ai','UC-M8-02'),('m7','UC-M8-04'),('own','UC-M8-02'),('own','UC-M8-05')],
 rels=[('UC-M8-01','UC-M8-06','include'),('UC-M8-02','UC-M8-06','include'),('UC-M8-04','UC-M8-02','extend')], amber=('UC-M8-05',))
N={}
N['SimulationController']=klass('SimulationController','REST, /api/m8 (OPERATOR)',attrs=None,ops=['+snapshot() / status() / runs()','+parse(text): ParseResult','+run(scenario, snapshotRunId, repeatRecentBookings,','    startingVehicles): Run','+discard(runId, recId) / apply(runId, recId): Run','+retry(runId, expectedRounds): Run','+check()'])
N['SimulationService']=klass('SimulationService','service',attrs=None,ops=['+run(scenario, snapshotRunId, repeat, startingVehicles): Run','+retry(runId, expectedRounds): Run','+parse(text): ParseResult','+list(): List<Run>'])
N['DispatchApplyService']=klass('DispatchApplyService','service, talks to M7',attrs=None,ops=['+apply(runId, recId, operator): Run','+discard(runId, recId, operator): Run','+sync(runs): List<Run>'])
N['AgentPlanner']=klass('AgentPlanner','service, optional LLM tool loop',attrs=None,ops=['+extend(snapshot, scenario, previous): AgentState'])
N['SimulationPlanTool']=klass('SimulationPlanTool','component',attrs=None,ops=['+evaluate(snapshot, scenario, actions): PlanEvaluation'])
N['ScenarioAssistant']=klass('ScenarioAssistant','service, optional LLM (M8_LLM_*)',attrs=['-url, key, model: String'],ops=['+parse(text, snapshot): ParseResult','+validate(scenario, snapshot)','+recommend(snapshot, scenario, kpi, engine): Proposals','+toolChat(messages, tool): JsonNode'])
N['StartingFleet']=klass('StartingFleet','utility',attrs=None,ops=['~override(snapshot, startingVehicles): Snapshot'])
N['SimulationEngine']=klass('SimulationEngine','component, pure function',attrs=None,ops=['+simulate(snapshot, scenario): EngineResult'])
N['SnapshotProvider']=klass('SnapshotProvider','interface',attrs=None,ops=['+capture(): Snapshot'],**IFACE)
N['JdbcSnapshotProvider']=klass('JdbcSnapshotProvider','read-only, REPEATABLE_READ',attrs=['-devices: Map<String, DeviceState>'],ops=['+capture(): Snapshot','+onResourceState(e: ResourceStateUpdatedEvent)'])
N['RedistributionRecommendations']=klass('RedistributionRecommendations','interface, shared.contracts (M7)',attrs=None,ops=['+propose(source, ref, from, to, amount, reason): String','+approve(id, operator): ApprovalResult','+decision(id): Decision','+withdraw(id, operator, reason): Decision'],**IFACE)
N['RunStore']=klass('RunStore','JSON file, latest 200 runs',attrs=['-file: Path'],ops=['+save(run) / get(id) / list()'])
N['NetworkMonitor']=klass('NetworkMonitor','@Scheduled every 30 min',attrs=['-state: State (volatile)'],ops=['+check()','+status(aiConfigured): Map'],**SCHED)
N['SimulationModels']=klass('SimulationModels','immutable records',attrs=['Scenario(name, durationMins, seed, affectedHubs: HubSettings[])','HubSettings(hubId, disabled, closeFromMins, reopenAtMins,','  injectedRequests, arrivalWindowMins, tripMins, reroute, repeatBookings)','Snapshot(hubs, vehicles, deviceStates, bookingWindow, capacities, hypotheticalFleet)','Kpi(requests, served, failed, failurePercent, meanWaitMins, peakQueue, hubs)','Run(scenario, snapshot, kpi, baseline, timeline, recommendations, agent)','Recommendation(action, from, to, amount, status, projectedFailure%, dispatch)','DispatchProgress(tasks, message, m7RecommendationId)','AgentState(totalRounds, status, rounds, best)'],ops=None)
T='hub, rental_vehicle, reservation, parking_space, redistribution_tasks, m1_resource_state'
N['Tables']=klass(T,'tables of M1–M4, M7 (read only)',attrs=None,ops=None,**EXT)
classdiagram(O+'m8_class.png',N,[('SimulationController','SimulationService','assoc'),('SimulationController','DispatchApplyService','assoc'),('SimulationController','NetworkMonitor','assoc'),
 ('SimulationService','SnapshotProvider','assoc'),('SimulationService','SimulationEngine','assoc'),('SimulationService','RunStore','assoc'),('SimulationService','ScenarioAssistant','assoc'),
 ('SimulationService','AgentPlanner','assoc'),('SimulationService','StartingFleet','dep','edited fleet'),('AgentPlanner','SimulationPlanTool','assoc'),('AgentPlanner','ScenarioAssistant','dep','LLM call'),
 ('SimulationPlanTool','SimulationEngine','assoc'),('ScenarioAssistant','SimulationEngine','dep','re-simulates proposals'),
 ('DispatchApplyService','RunStore','assoc'),('DispatchApplyService','RedistributionRecommendations','assoc'),
 ('JdbcSnapshotProvider','SnapshotProvider','impl'),('JdbcSnapshotProvider',T,'dep','SELECT'),
 ('NetworkMonitor','SnapshotProvider','assoc'),('NetworkMonitor','SimulationEngine','assoc'),('RunStore','SimulationModels','dep')],extra='splines=true;')
mermaid(O+'m8_sequence.png','''sequenceDiagram
autonumber
actor OP as Operator
participant C as SimulationController
participant S as SimulationService
participant P as JdbcSnapshotProvider
participant G as AgentPlanner
participant A as ScenarioAssistant
participant E as SimulationEngine
participant R as RunStore
participant D as DispatchApplyService
participant M7 as M7 RecommendationService
OP->>C: POST /api/m8/runs (scenario)
C->>S: run(scenario, snapshotRunId, repeatRecentBookings, startingVehicles)
S->>P: capture() (or the snapshot of a saved run)
P-->>S: Snapshot (hubs, vehicles, M2 bookings of last 30 min, M1/M4 capacities, incoming M7 tasks)
S->>A: validate(scenario, snapshot)
S->>E: simulate(snapshot, scenario) and the baseline with hubs open
E-->>S: KPI, timeline, baseline KPI
alt AI provider configured
  S->>G: extend(snapshot, scenario)
  loop at most 3 rounds
    G->>E: simulate_plan(actions) on the same snapshot
    E-->>G: KPI or validation feedback
  end
  G-->>S: AgentState (best tested plan)
  opt provider failed and no tested plan
    S->>A: recommend(...) with local rules
  end
else no AI provider
  S->>A: recommend(snapshot, scenario, kpi, engine) with local rules
end
S->>R: save(run)
S-->>OP: Run (KPI, baseline, proposals, warnings)
OP->>C: POST /runs/:id/recommendations/:recId/apply
C->>D: apply(runId, recId, operator)
D->>M7: propose("M8_SIMULATION", recId, from, to, amount)
D->>M7: approve(m7Id, operator)
M7-->>D: ApprovalResult (status, task ids)
D->>R: save(run) as PENDING, PARTIAL or APPROVED
OP->>C: GET /api/m8/runs (later)
C->>D: sync(runs)
D->>M7: decision(m7Id)
M7-->>D: status, done and failed tasks
D->>R: save(run) only if changed (APPLIED or DISCARDED)
Note over S,D: M8 never writes operational tables. M7 creates and runs the tasks
''', width=1900)


# ---------------------------------------------------------------- state: a proposal inside a saved run
# A run is saved once; only the status of each proposal moves (DispatchApplyService follows M7's decision).
statemachine(O+'m8_state.png',
 ['PENDING','NEEDS_REVIEW','PARTIAL','APPROVED','APPLIED','DISCARDED'],
 [('PENDING','DISCARDED','discard / withdraw the M7 proposal,\\nor M7 rejected it'),
  ('PENDING','PENDING','apply / M7 could not\\ncreate a task yet'),
  ('PENDING','PARTIAL','apply / M7 created\\nsome tasks'),
  ('PENDING','APPROVED','apply / M7 created\\nevery task'),
  ('PENDING','NEEDS_REVIEW','apply / M7 approval\\nnot confirmed'),
  ('NEEDS_REVIEW','PENDING','apply / M7 still pending:\\nretry the approval'),
  ('NEEDS_REVIEW','APPROVED','apply or list runs /\\nM7 already approved'),
  ('NEEDS_REVIEW','DISCARDED','discard, or M7 rejected'),
  ('PARTIAL','PARTIAL','apply again / more tasks'),
  ('PARTIAL','APPROVED','apply again /\\nremaining tasks created'),
  ('APPROVED','APPLIED','list runs / every M7 task finished,\\nat least one vehicle arrived')],
 initial='PENDING', final=('APPLIED','DISCARDED'),
 extra='label=<<b>Proposal of a what-if run (M8)</b>>; labelloc=t; fontsize=16;',
 notes=['note [shape=note, style=filled, fillcolor="#fff6d5", color="#c9a227", fontname="DejaVu Sans", fontsize=11, '
        'label="Created with the run, always PENDING.\\nApply registers it with M7 (propose + approve);\\nM8 then follows M7 when runs are listed.\\nOnly dispatch proposals can be applied,\\nand not on an edited (hypothetical) fleet."];',
        'note -> PENDING [style=dashed, arrowhead=none, color="#c9a227"];'])

# ---------------------------------------------------------------- activity: from a description to a decision
src=activity_header('What-if run: from a description to a decision (M8)')+f'''
d1 [label="Describe\\nin words?", {DEC}];
p [label="Parse: LLM if configured,\\notherwise local rules", {ACT}];
f [label="Editable form: affected hubs,\\nclosing time, demand, rerouting", {ACT}];
d2 [label="Fields valid?", {DEC}];
d3 [label="Snapshot\\nsource?", {DEC}];
c [label="Capture snapshot in one\\nREPEATABLE_READ read", {ACT}];
r [label="Reuse the snapshot\\nof a saved run", {ACT}];
m [shape=diamond, label="", width=0.3, height=0.3, style=filled, fillcolor="#ffffff"];
fl [label="Apply edited vehicle counts\\n(optional, hypothetical fleet)", {ACT}];
sim [label="Simulate scenario and baseline\\nminute by minute", {ACT}];
d5 [label="AI provider\\nconfigured?", {DEC}];
ai [label="AI tests up to 3 dispatch plans\\nwith simulate_plan; keeps only\\nplans that reduce unserved requests", {ACT}];
pr [label="Local rules: dispatch from a donor hub\\nthat keeps one vehicle and fits\\nrental-bike parking", {ACT}];
sv [label="Save the run (RunStore)", {ACT}];
sh [label="Show KPIs, timeline, per-hub table,\\nproposals and assumptions", {ACT}];
d4 [label="Next step?", {DEC}];
cmp [label="History: select two runs,\\ncompare KPIs", {ACT}];
dis [label="Proposal DISCARDED;\\nM7 proposal withdrawn", {ACT}];
d6 [label="Live fleet?", {DEC}];
ap [label="Send to M7: propose + approve;\\nM7 picks vehicles, creates tasks", {OK}];
no [label="Refused: run again\\nwith the live fleet", {ERR}];
fw [label="M8 follows M7:\\nAPPLIED when tasks finish,\\nDISCARDED if rejected", {ACT}];
e {END};
s -> d1; d1 -> p [label="yes"]; d1 -> f [label="no"]; p -> f; f -> d2; d2 -> f [label="no: field errors", constraint=false];
d2 -> d3 [label="yes"]; d3 -> c [label="live"]; d3 -> r [label="saved run"]; c -> m; r -> m; m -> fl -> sim -> d5;
d5 -> ai [label="yes"]; d5 -> pr [label="no"]; ai -> pr [label="provider failed,\\nno tested plan", style=dashed];
ai -> sv; pr -> sv; sv -> sh -> d4;
d4 -> f [label="edit and run again", constraint=false, style=dashed];
d4 -> ai [label="retry AI: +3 rounds", constraint=false, style=dashed];
d4 -> cmp [label="compare"]; d4 -> dis [label="discard"]; d4 -> d6 [label="apply"];
d6 -> ap [label="yes"]; d6 -> no [label="no"]; ap -> fw;
cmp -> e; dis -> e; fw -> e; no -> e;
{{rank=same; c; r}} {{rank=same; ai; pr}} {{rank=same; cmp; dis; d6}}
}}'''
run_dot(src,O+'m8_activity.png',dpi=150)
print('ok')
