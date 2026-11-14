import os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
from lib import *
O=png_dir('m8')
usecase(O+'m8_usecase.png','«subsystem» M8 — What-if Simulation',
 [('UC-M8-01','Prepare a scenario|(describe or edit)'),('UC-M8-06','Validate the|scenario'),('UC-M8-02','Run a simulation|on a snapshot'),
  ('UC-M8-03','Review and compare|saved runs'),('UC-M8-04','Review recommendations|(discard / apply)'),('UC-M8-05','Check recent booking|volume every 30 min')],
 left=[('op','Operator',False),('tm','Timer',True)], right=[('ai','AI provider (optional)',True),('own','M2, M3, M1, M6 data',True)],
 assoc=[('op','UC-M8-01'),('op','UC-M8-02'),('op','UC-M8-03'),('op','UC-M8-04'),('tm','UC-M8-05'),('ai','UC-M8-01'),('ai','UC-M8-04'),('own','UC-M8-02'),('own','UC-M8-05')],
 rels=[('UC-M8-01','UC-M8-06','include'),('UC-M8-02','UC-M8-06','include'),('UC-M8-04','UC-M8-02','extend')], amber=('UC-M8-05',))
N={}
N['SimulationController']=klass('SimulationController','REST, /api/m8 (OPERATOR)',attrs=None,ops=['+snapshot() / status() / runs()','+parse(text): ParseResult','+run(scenario, snapshotRunId, repeatRecentBookings): Run','+discard(runId, recId) / apply(runId, recId)','+check()'])
N['SimulationService']=klass('SimulationService','service',attrs=None,ops=['+run(scenario, snapshotRunId, repeat): Run','+discard(runId, recId): Run','+apply(runId, recId)','+parse(text): ParseResult'])
N['ScenarioAssistant']=klass('ScenarioAssistant','service, optional LLM (M8_LLM_*)',attrs=['-url, key, model: String'],ops=['+parse(text, snapshot): ParseResult','+validate(scenario, snapshot)','+recommend(snapshot, scenario, kpi, engine): Proposals','-parkingRoom(snapshot, hub): int'])
N['SimulationEngine']=klass('SimulationEngine','component, pure function',attrs=None,ops=['+simulate(snapshot, scenario): EngineResult'])
N['SnapshotProvider']=klass('SnapshotProvider','interface',attrs=None,ops=['+capture(): Snapshot'],**IFACE)
N['JdbcSnapshotProvider']=klass('JdbcSnapshotProvider','read-only, REPEATABLE_READ',attrs=['-devices: Map<String, DeviceState>'],ops=['+capture(): Snapshot','+onResourceState(e: ResourceStateUpdatedEvent)'])
N['RunStore']=klass('RunStore','JSON file, latest 200 runs',attrs=['-file: Path'],ops=['+save(run) / get(id) / list()'])
N['NetworkMonitor']=klass('NetworkMonitor','@Scheduled every 30 min',attrs=['-state: State (volatile)'],ops=['+check()','+status(aiConfigured): Map'],**SCHED)
N['SimulationModels']=klass('SimulationModels','immutable records',attrs=['Scenario(name, hubId, disabled, injectedRequests,','  durationMins, arrivalWindowMins, tripMins, reroute, seed)','Snapshot(hubs, vehicles, deviceStates, bookingWindow, capacities)','Kpi(requests, served, failed, failurePercent, meanWaitMins, peakQueue, hubs)','Run(scenario, snapshot, kpi, baseline, timeline, recommendations)','Recommendation(action, from, to, amount, status, projectedFailure%)'],ops=None)
N['Tables']=klass('hub, rental_vehicle, reservation, m1_resource_state','tables of M1–M4 (read only)',attrs=None,ops=None,**EXT)
classdiagram(O+'m8_class.png',N,[('SimulationController','SimulationService','assoc'),('SimulationController','NetworkMonitor','assoc'),
 ('SimulationService','SnapshotProvider','assoc'),('SimulationService','SimulationEngine','assoc'),('SimulationService','RunStore','assoc'),('SimulationService','ScenarioAssistant','assoc'),
 ('ScenarioAssistant','SimulationEngine','dep','re-simulates proposals'),('JdbcSnapshotProvider','SnapshotProvider','impl'),('JdbcSnapshotProvider','hub, rental_vehicle, reservation, m1_resource_state','dep','SELECT'),
 ('NetworkMonitor','SnapshotProvider','assoc'),('NetworkMonitor','SimulationEngine','assoc'),('RunStore','SimulationModels','dep')],extra='splines=true;')
mermaid(O+'m8_sequence.png','''sequenceDiagram
autonumber
actor OP as Operator
participant C as SimulationController
participant S as SimulationService
participant P as JdbcSnapshotProvider
participant A as ScenarioAssistant
participant E as SimulationEngine
participant R as RunStore
OP->>C: POST /api/m8/runs (scenario, seed 42)
C->>S: run(scenario, null, false)
S->>P: capture()
P-->>S: Snapshot (hubs, vehicles, bookings of last 30 min, M1 capacities)
S->>A: validate(scenario, snapshot)
S->>E: simulate(snapshot, scenario)
E-->>S: KPI and timeline
S->>E: simulate(snapshot, same scenario with hub open)
E-->>S: baseline KPI
S->>A: recommend(snapshot, scenario, kpi, engine)
A->>E: simulate each candidate on a copy
A-->>S: proposals that reduce failed requests
S->>R: save(run)
S-->>OP: Run (KPI, baseline, proposals, warnings)
Note over S,R: no INSERT or UPDATE on any operational table
''', width=1400)


# ---------------------------------------------------------------- state: a proposal inside a saved run
# A run is created complete and never changes; only the status of each proposal moves (SimulationService).
statemachine(O+'m8_state.png',
 ['PENDING','DISCARDED',('APPLIED','style="rounded,dashed,filled", fillcolor="#f2f2f2", color="#888888", fontcolor="#777777"')],
 [('PENDING','DISCARDED','discard(runId, id)\\n/ save run with the new status'),
  ('PENDING','PENDING','apply(runId, id)\\n/ refused: DISPATCH_NOT_CONNECTED,\\nnothing executed'),
  ('PENDING','APPLIED','apply after M7 integration\\n(planned)','style=dashed, color="#888888", fontcolor="#777777"')],
 initial='PENDING', final=('DISCARDED',),
 extra='label=<<b>Proposal of a what-if run (M8)</b>>; labelloc=t; fontsize=16;',
 notes=['note [shape=note, style=filled, fillcolor="#fff6d5", color="#c9a227", fontname="DejaVu Sans", fontsize=11, '
        'label="Created with the run, always PENDING.\\nDiscarding a non-PENDING proposal\\nfails with ALREADY_RESOLVED.\\nThe run itself is never modified."];',
        'note -> PENDING [style=dashed, arrowhead=none, color="#c9a227"];'])

# ---------------------------------------------------------------- activity: from a description to a decision
src=activity_header('What-if run: from a description to a decision (M8)')+f'''
d1 [label="Describe\\nin words?", {DEC}];
p [label="Parse: LLM if configured,\\notherwise local rules", {ACT}];
f [label="Pre-filled editable form", {ACT}];
d2 [label="Fields valid?", {DEC}];
d3 [label="Snapshot\\nsource?", {DEC}];
c [label="Capture snapshot in one\\nREPEATABLE_READ read", {ACT}];
r [label="Reuse the snapshot\\nof a saved run", {ACT}];
m [shape=diamond, label="", width=0.3, height=0.3, style=filled, fillcolor="#ffffff"];
sim [label="Simulate scenario and baseline\\nminute by minute", {ACT}];
pr [label="Propose dispatch or reroute;\\nkeep only proposals that\\nreduce failed requests", {ACT}];
sv [label="Save the run (RunStore)", {ACT}];
sh [label="Show KPIs, timeline, per-hub table,\\nproposals and assumptions", {ACT}];
d4 [label="Next step?", {DEC}];
cmp [label="History: select two runs,\\ncompare KPIs", {ACT}];
dis [label="Proposal DISCARDED;\\nlive state unchanged", {ACT}];
ap [label="Refused: DISPATCH_NOT_CONNECTED;\\nnothing executed", {ERR}];
e {END};
s -> d1; d1 -> p [label="yes"]; d1 -> f [label="no"]; p -> f; f -> d2; d2 -> f [label="no: field errors", constraint=false];
d2 -> d3 [label="yes"]; d3 -> c [label="live"]; d3 -> r [label="saved run"]; c -> m; r -> m; m -> sim -> pr -> sv -> sh -> d4;
d4 -> f [label="edit and run again", constraint=false, style=dashed];
d4 -> cmp [label="compare"]; d4 -> dis [label="discard"]; d4 -> ap [label="apply"];
cmp -> e; dis -> e; ap -> e;
{{rank=same; c; r}} {{rank=same; cmp; dis; ap}}
}}'''
run_dot(src,O+'m8_activity.png',dpi=150)
print('ok')
