import os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
from lib import *
O=png_dir('m7')
usecase(O+'m7_usecase.png','«subsystem» M7 — Vehicle Redistribution',
 [('UC-M7-01','Create a redistribution|task'),('UC-M7-05','Check vehicle, duplicate|and destination capacity'),('UC-M7-02','Approve or reject|a recommendation'),
  ('UC-M7-03','Track a task|(start, done, failed)'),('UC-M7-04','Propose redistribution|from an alert')],
 left=[('op','Operator',False)], right=[('m1','M1 (HubNearCapacity)',True),('m8','M8 (What-if)',True)],
 assoc=[('op','UC-M7-01'),('op','UC-M7-02'),('op','UC-M7-03'),('m1','UC-M7-04'),('m8','UC-M7-02')],
 rels=[('UC-M7-01','UC-M7-05','include'),('UC-M7-02','UC-M7-05','include')], amber=('UC-M7-04',))
statemachine(O+'m7_state.png',['CREATED','IN_PROGRESS','DONE','FAILED',('PENDING','fillcolor="#fff1c9", color="#c9a227"'),('APPROVED','fillcolor="#fff1c9", color="#c9a227"'),('PARTIAL','fillcolor="#fff1c9", color="#c9a227"'),('REJECTED','fillcolor="#fff1c9", color="#c9a227"')],
 [('CREATED','IN_PROGRESS','start [vehicle still AVAILABLE]\\n/ undock at source, DispatchStarted'),
  ('IN_PROGRESS','DONE','done [space at destination]\\n/ dock, DispatchCompleted'),
  ('IN_PROGRESS','FAILED','fail / dock back at source,\\nDispatchFailed','color="#c0504d", fontcolor="#c0504d"'),
  ('CREATED','FAILED','fail (not started)','color="#c0504d", fontcolor="#c0504d"'),
  ('PENDING','APPROVED','approve [all tasks created]'),('PENDING','PARTIAL','approve [some tasks created]'),
  ('PARTIAL','APPROVED','approve again [rest created]'),('PENDING','REJECTED','reject')],
 rankdir='TB', extra='nodesep=0.9; ranksep=0.9; subgraph cluster_t {label="RedistributionTask"; fontname="DejaVu Sans Bold"; CREATED; IN_PROGRESS; DONE; FAILED;} subgraph cluster_r {label="RedistributionRecommendation"; fontname="DejaVu Sans Bold"; PENDING; APPROVED; PARTIAL; REJECTED;}')
N={}
N['RedistributionController']=klass('RedistributionController','REST, /api/m7/redistributions',attrs=None,ops=['+create(req) / list(status) / get(id)','+updateStatus(id, status)'])
N['RecommendationController']=klass('RecommendationController','REST, /api/m7/recommendations',attrs=None,ops=['+list(status) / approve(id) / reject(id, reason)'])
N['RedistributionEventHandler']=klass('RedistributionEventHandler','@TransactionalEventListener',attrs=None,ops=['+onHubNearCapacity(e: HubNearCapacityEvent)'],**SCHED)
N['RecommendationService']=klass('RecommendationService','service, implements RedistributionRecommendations',attrs=['-keepAtDonor = 4, targetAvailable = 4','-parkingTargetPercent = 75'],ops=['+proposeForAlert(hubId, type, ref): Optional<...>','+propose(source, ref, from, to, amount, reason): String','+approve(id, by): ApprovalResult','+reject(id, by, reason)','-forShortage / forCrowding'])
N['RedistributionService']=klass('RedistributionService','service',attrs=None,ops=['+createRedistributionTask(vehicle, from, to): RedistributionTask','+updateTaskStatus(taskId, status): RedistributionTask','+getTaskDetail(id) / getTasks(status)'])
N['RedistributionRecommendation']=klass('RedistributionRecommendation','entity',attrs=['-source: M1_ALERT | M8_SIMULATION','-externalRef, sourceHubId, destinationHubId','-amount, taskCount: int','-status: RecommendationStatus','-reason, note, resolvedBy, createdAt, resolvedAt'],ops=None)
N['RedistributionTask']=klass('RedistributionTask','entity, redistribution_task',attrs=['-vehicleId, sourceHubId, destinationHubId','-status: RedistributionTaskStatus','-recommendationId: String','-createdAt, updatedAt'],ops=None)
N['RedistributionRecommendations']=klass('RedistributionRecommendations','interface, shared contract (for M8)',attrs=None,ops=['+propose(...): String','+approve(id, by): ApprovalResult'],**IFACE)
N['Clients']=klass('VehicleClient / HubClient','interfaces (JdbcFleetClient, ParkingHubClient)',attrs=None,ops=['+checkVehicle(id) / availableVehiclesAt(hub)','+availableCountPerHub()','+checkDestinationCapacity(hub)'],**IFACE)
N['ParkingDock']=klass('ParkingDock','shared contract (owner M4)',attrs=None,ops=['+dock / undock'],**IFACE)
N['EventPublisher']=klass('EventPublisher','shared',attrs=None,ops=['DispatchStarted / Completed / Failed','RecommendationApproved'],**EXT)
classdiagram(O+'m7_class.png',N,[('RedistributionController','RedistributionService','assoc'),('RecommendationController','RecommendationService','assoc'),
 ('RedistributionEventHandler','RecommendationService','assoc','proposeForAlert'),('RecommendationService','RedistributionRecommendations','impl'),
 ('RecommendationService','RedistributionService','assoc','one task per vehicle'),('RecommendationService','RedistributionRecommendation','assoc'),
 ('RecommendationService','VehicleClient / HubClient','assoc'),('RedistributionService','VehicleClient / HubClient','assoc'),
 ('RedistributionService','RedistributionTask','assoc'),('RedistributionService','ParkingDock','assoc'),('RedistributionService','EventPublisher','dep'),('RecommendationService','EventPublisher','dep'),
 ('RedistributionTask','RedistributionRecommendation','assoc','0..* → 0..1')],extra='splines=true;')
mermaid(O+'m7_sequence.png','''sequenceDiagram
autonumber
participant M1 as AlertService (M1)
participant H as RedistributionEventHandler
participant RS as RecommendationService
actor OP as Operator
participant DS as RedistributionService
participant PD as ParkingDock (M4)
participant EP as EventPublisher
M1->>H: HubNearCapacityEvent(hub 1, VEHICLE, 1) after commit
H->>RS: proposeForAlert(1, VEHICLE, alert id)
RS->>RS: no open proposal, donor keeps 4, amount = min(need, spare, room)
RS-->>OP: proposal PENDING, move 3 from hub 2 to hub 1
OP->>RS: approve(id)
loop vehicles at hub 2, highest battery first, keep one at the source
  RS->>DS: createRedistributionTask(vehicle, 2, 1)
  DS->>DS: not reserved, no active task, destination has room
end
RS->>EP: RecommendationApproved(id, REDISTRIBUTE)
OP->>DS: updateTaskStatus(task, IN_PROGRESS)
DS->>PD: undockRentalVehicle (space freed at hub 2)
DS->>EP: DispatchStarted, M3 sets IN_TRANSIT
OP->>DS: updateTaskStatus(task, DONE)
DS->>PD: dockRentalVehicle(vehicle, hub 1)
DS->>EP: DispatchCompleted, M3 AVAILABLE at hub 1, M1 recounts
''', width=1500)

# ---------------------------------------------------------------- activity: from an alert to redistribution tasks
# RecommendationService.proposeForAlert (after M1 commits an alert) and approve / reject by the operator.
src=activity_header('From a hub alert to redistribution tasks (M7)')+f'''
a [label="M1 commits a LOW_VEHICLE or\\nnear-capacity alert for a hub", {ACT}];
d0 [label="Open proposal (PENDING /\\nPARTIAL) for this hub?", {DEC}];
n0 [label="No new proposal", {ACT}];
d1 [label="Alert type?", {DEC}];
sh [label="Shortage: donor = hub with the most\\nspare vehicles (keeps a minimum);\\namount ≤ free room at the hub", {ACT}];
cr [label="Crowding: target = hub with the most\\nfree room; move only vehicles the\\nhub can spare", {ACT}];
d2 [label="Donor / target found\\nand amount > 0?", {DEC}];
n1 [label="Log the reason, no proposal", {ACT}];
p [label="Save recommendation PENDING\\n(from, to, amount, reason)", {ACT}];
op [label="Operator decides", {DEC}];
rj [label="REJECTED with reason", {ACT}];
c [label="Candidates: available vehicles at the source\\nnot already in a task; limit = min(remaining,\\ncandidates − 1) so the source keeps one", {ACT}];
l [label="Limit reached or\\nno candidate left?", {DEC}];
t [label="Create a task (M4 checks the\\ndestination has room; vehicle → IN_TRANSIT\\nwhen it leaves)", {ACT}];
d3 [label="Result?", {DEC}];
sk [label="Skip this vehicle", {ACT}];
st [label="Stop: DESTINATION_FULL\\nor HUB_NOT_FOUND", {ERR}];
d4 [label="All tasks\\ncreated?", {DEC}];
ap [label="APPROVED", {OK}];
pa [label="PARTIAL (some tasks) or still\\nPENDING (none), with a note;\\nthe operator can approve again", {ACT}];
ev [label="First approval with tasks:\\npublish RecommendationApprovedEvent", {ACT}];
e {END}; e2 {END};
s -> a -> d0; d0 -> n0 [label="yes"]; d0 -> d1 [label="no"];
d1 -> sh [label="vehicle shortage"]; d1 -> cr [label="parking nearly full"]; sh -> d2; cr -> d2;
d2 -> n1 [label="no"]; d2 -> p [label="yes"]; n0 -> e2; n1 -> e2;
p -> op; op -> rj [label="reject"]; op -> c [label="approve"]; rj -> e;
c -> l; l -> t [label="no"]; t -> d3;
d3 -> l [label="created", constraint=false]; d3 -> sk [label="other error"]; sk -> l [constraint=false];
d3 -> st [label="destination full"]; l -> d4 [label="yes"]; st -> d4;
d4 -> ap [label="yes"]; d4 -> pa [label="no"]; ap -> ev; pa -> ev [label="[tasks > 0]"]; pa -> e [label="[no task]"]; ev -> e;
pa -> op [style=dashed, constraint=false, label="approve again"];
{{rank=same; sh; cr}} {{rank=same; n0; n1}}
}}'''
run_dot(src,O+'m7_activity.png',dpi=150)
print('ok')
