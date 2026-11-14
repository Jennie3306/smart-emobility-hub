import os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
from lib import *
O=png_dir('m6')
usecase(O+'m6_usecase.png','«subsystem» M6 — Incidents and Device Events',
 [('UC-M6-01','Report a device incident'),('UC-M6-02','Manage the incident|lifecycle'),('UC-M6-06','Run and monitor|the simulator'),
  ('UC-M6-07','Validate payload|and resource'),('UC-M6-03','Receive and process|device readings'),('UC-M6-04','Run the periodic tick'),('UC-M6-05','Record a failure|and announce it')],
 left=[('op','Operator',False)], right=[('tim','Scheduler',True),('dev','Simulator /|device sensors',True)],
 assoc=[('op','UC-M6-01'),('op','UC-M6-02'),('op','UC-M6-06'),('tim','UC-M6-04'),('dev','UC-M6-03')],
 rels=[('UC-M6-01','UC-M6-07','include'),('UC-M6-03','UC-M6-07','include'),('UC-M6-04','UC-M6-03','include'),('UC-M6-06','UC-M6-03','include'),('UC-M6-05','UC-M6-03','extend')],
 amber=('UC-M6-03','UC-M6-04','UC-M6-05'))
statemachine(O+'m6_state.png',['REPORTED','ASSIGNED','IN_PROGRESS','RESOLVED','CLOSED'],
 [('REPORTED','ASSIGNED','ASSIGN [assignee is an Operator]'),('ASSIGNED','IN_PROGRESS','START'),('IN_PROGRESS','RESOLVED','RESOLVE'),
  ('RESOLVED','CLOSED','CLOSE'),('RESOLVED','IN_PROGRESS','REOPEN [no other active incident]\\n/ resolvedAt cleared','style=dashed')],
 initial='REPORTED', final=('CLOSED',), rankdir='TB', extra='nodesep=0.8; ranksep=0.6;',
 notes=['note [shape=note, style=filled, fillcolor="#fff8e1", color="#c9a227", fontname="DejaVu Sans", fontsize=12, label="Every action carries expectedVersion (STALE_VERSION)\\nREPORTED, ASSIGNED, IN_PROGRESS are active:\\nat most one active incident per resource (BR-M6-06)"];'])
N={}
N['SimulatorController']=klass('SimulatorController','REST, /api/m6/simulator',attrs=None,ops=['+status(): SimulatorStatusView','+run(scenario, req: RunScenarioRequest): SimulationRunView'])
N['IncidentController']=klass('IncidentController','REST, /api/m6/incidents',attrs=None,ops=['+report(req) / list(status, type, severity)','+get(id) / transition(id, req)'])
N['SimulatorScheduler']=klass('SimulatorScheduler','scheduled, if enabled',attrs=['-seed: long'],ops=['+scheduledTick(): void'],**SCHED)
N['DeviceSimulatorService']=klass('DeviceSimulatorService','service',attrs=['-running: AtomicBoolean'],ops=['+runScenario(scenario, seed): SimulationRunView','+status(): SimulatorStatusView'])
N['SyntheticReadingFactory']=klass('SyntheticReadingFactory','component',attrs=None,ops=['+createReadings(scenario, seed, runId): List<DeviceReading>'])
N['DeviceEventService']=klass('DeviceEventService','service',attrs=None,ops=['+processReading(reading, runId): ProcessingResult','-validate(reading)','-buildEvents(reading): List<DomainEvent>'])
N['IncidentService']=klass('IncidentService','service',attrs=['ACTIVE = {REPORTED, ASSIGNED, IN_PROGRESS}'],ops=['+report(req, reporter): Incident','+openForDeviceFailure(reading): boolean','+transition(id, req, actor): Incident','+get(id) / list(...)'])
N['Incident']=klass('Incident','entity, incident',attrs=['-resourceType, resourceId, hubId','-reason: String, severity: IncidentSeverity','-status: IncidentStatus, source: IncidentSource','-reportedBy, assignee, note','-reportedAt, assignedAt, startedAt,','  resolvedAt, closedAt','-version: Long (@Version)'],ops=['+apply(action, assignee, note, now): void'])
N['IncidentStatus']=klass('IncidentStatus','enumeration',attrs=['REPORTED, ASSIGNED, IN_PROGRESS,','RESOLVED, CLOSED'],ops=['+transition(action): IncidentStatus'],**ENUM)
N['IncidentSeverity']=klass('IncidentSeverity','enumeration',attrs=['LOW, MEDIUM, HIGH, CRITICAL'],ops=['+forDeviceFailure(type, reason) {static}'],**ENUM)
N['DemoDeviceCatalog']=klass('DemoDeviceCatalog','component',attrs=None,ops=['+hubFor(type, id): Optional<Long>'],**EXT)
N['Stores']=klass('InboundDeviceReadingStore / InMemoryDeviceStateStore','in-memory',attrs=None,ops=['+find(eventId) / markCompleted(result)','+save(snapshot)'],**EXT)
N['EventPublisher']=klass('EventPublisher','shared',attrs=None,ops=['DeviceReadingReported','VehicleFailed / ChargerFailed'],**EXT)
classdiagram(O+'m6_class.png',N,[('SimulatorController','DeviceSimulatorService','assoc'),('SimulatorScheduler','DeviceSimulatorService','assoc'),
 ('DeviceSimulatorService','SyntheticReadingFactory','assoc'),('DeviceSimulatorService','DeviceEventService','assoc','each reading'),
 ('DeviceEventService','DemoDeviceCatalog','assoc','validates'),('DeviceEventService','InboundDeviceReadingStore / InMemoryDeviceStateStore','assoc'),
 ('DeviceEventService','IncidentService','assoc','failure'),('DeviceEventService','EventPublisher','dep','publishes'),
 ('IncidentController','IncidentService','assoc'),('IncidentService','Incident','assoc','manages'),('IncidentService','EventPublisher','dep','after commit'),
 ('Incident','IncidentStatus','assoc'),('Incident','IncidentSeverity','assoc')],extra='splines=true;')
mermaid(O+'m6_sequence.png','''sequenceDiagram
autonumber
actor OP as Operator / Scheduler
participant DS as DeviceSimulatorService
participant F as SyntheticReadingFactory
participant DE as DeviceEventService
participant IS as IncidentService
participant EP as EventPublisher
participant OW as Owner modules (M3, M4, M5)
OP->>DS: runScenario(CHARGER_FAILURE, seed)
DS->>DS: running.compareAndSet(false, true) or 409
DS->>F: createReadings(scenario, seed, runId)
F-->>DS: readings (fresh eventIds)
loop each reading
  DS->>DE: processReading(reading, runId)
  DE->>DE: duplicate eventId? catalog, hub, battery, reason valid?
  alt failure reading
    DE->>IS: openForDeviceFailure(reading)
    alt an incident is already active
      IS-->>DE: false
      DE-->>DS: PROCESSED, no event (ACTIVE_INCIDENT_EXISTS)
    else none active
      IS->>IS: save Incident REPORTED, severity by type and reason
      DE->>EP: ChargerFailedEvent / VehicleFailedEvent
      EP->>OW: owners lock the resource and handle bookings
    end
  else normal reading
    DE->>EP: DeviceReadingReportedEvent
    EP->>OW: owners decide the business state
  end
end
DS-->>OP: run summary (processed, duplicates, rejected, events)
''', width=1500)
# ---------------------------------------------------------------- activity: reporting an incident (operator or device)
src=activity_header('Reporting an incident — operator report or device failure (M6)')+f'''
A [label="Operator submits a report", {ACT}];
B [label="Device reading with a failure", {ACT}];
V [label="Operator role, payload,\\nreason and resource valid?", {DEC}];
D [label="eventId already\\nprocessed?", {DEC}];
D1 [label="Return DUPLICATE,\\nnothing changes", {ACT}];
V2 [label="Catalog, hub and\\nreason code valid?", {DEC}];
R1 [label="REJECTED, no event", {ERR}];
R2 [label="400 / 404, nothing stored", {ERR}];
ACT1 [label="Active incident\\nfor this resource?", {DEC}];
ACT2 [label="Active incident\\nfor this resource?", {DEC}];
C1 [label="409 ACTIVE_INCIDENT_EXISTS\\nwith the existing id and status", {ERR}];
C2 [label="Mark reading processed,\\nannounce nothing", {ACT}];
S1 [label="Save Incident REPORTED,\\nseverity chosen by the operator", {ACT}];
S2 [label="Save Incident REPORTED,\\nseverity by type and reason", {ACT}];
T [label="Commit\\nsucceeded?", {DEC}];
R3 [label="Rollback, no event", {ERR}];
P [label="Publish VehicleFailed /\\nChargerFailed", {ACT}];
OW [label="M3 / M5 / M4 lock the resource and\\nhandle reservations and sessions", {ACT}];
L [label="Operator: ASSIGN, START, RESOLVE,\\nREOPEN or CLOSE with expectedVersion", {OK}];
s2 [shape=circle, style=filled, fillcolor=black, label="", width=0.25];
e {END}; e2 {END};
s -> A; s2 -> B;
A -> V; B -> D; D -> D1 [label="yes"]; D -> V2 [label="no"];
V2 -> R1 [label="no"]; V -> R2 [label="no"];
V -> ACT1 [label="yes"]; V2 -> ACT2 [label="yes"];
ACT1 -> C1 [label="yes"]; ACT2 -> C2 [label="yes"];
ACT1 -> S1 [label="no"]; ACT2 -> S2 [label="no"];
S1 -> T; T -> R3 [label="no"]; T -> P [label="yes"]; S2 -> P;
P -> OW -> L -> e;
D1 -> e2; R1 -> e2; R2 -> e2; C1 -> e2; C2 -> e2; R3 -> e2;
{{rank=same; s; s2}}
}}'''
run_dot(src,O+'m6_activity.png',dpi=150)
print('ok')
