import os, sys
HERE_M1=os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE_M1, '..')); sys.path.insert(0, HERE_M1)
from lib import *
O=png_dir('m1')

# ---------------------------------------------------------------- use case
usecase(O+'m1_usecase.png','«subsystem» M1 — Hub Monitoring and Operations Dashboard',
 [('UC-M1-01','View hub list'),('UC-M1-03','Filter hub list'),('UC-M1-02','View hub detail'),('UC-M1-05','Compute hub utilisation'),
  ('UC-M1-04','View operations dashboard'),('UC-M1-07','Evaluate thresholds,|raise and clear alerts'),
  ('UC-M1-08','View alert history'),('UC-M1-06','Mirror owner-announced|resource state')],
 left=[('stu','Student',False),('op','Operator',False)],
 right=[('tm','Timer',True),('own','Owner modules|(M2, M3, M4, M5, M7)',True)],
 assoc=[('stu','UC-M1-01'),('stu','UC-M1-03'),('stu','UC-M1-02'),
        ('op','UC-M1-01'),('op','UC-M1-02'),('op','UC-M1-04'),('op','UC-M1-08'),
        ('tm','UC-M1-07'),('own','UC-M1-06')],
 rels=[('UC-M1-03','UC-M1-01','extend'),('UC-M1-01','UC-M1-05','include'),('UC-M1-02','UC-M1-05','include'),
       ('UC-M1-04','UC-M1-05','include'),('UC-M1-07','UC-M1-05','include')],
 amber=('UC-M1-06','UC-M1-07'))

# ---------------------------------------------------------------- class (built in m1_class_def.py)
from m1_class_def import DOT
run_dot(DOT, O+'m1_class.png', dpi=150)

# ---------------------------------------------------------------- state: Alert (AlertState.canMoveTo)
statemachine(O+'m1_state.png',
 ['OPEN','ACKNOWLEDGED',('RESOLVED','fillcolor="#e8e8e8", color="#999999"'),('DISMISSED','fillcolor="#e8e8e8", color="#999999"')],
 [('OPEN','OPEN','threshold still breached\\n/ refresh figures only [BR-M1-06]'),
  ('OPEN','ACKNOWLEDGED','operator acknowledges\\n/ record user and timestamp'),
  ('OPEN','RESOLVED','condition cleared [BR-M1-07]\\n/ close alert, push to dashboard'),
  ('OPEN','DISMISSED','operator dismisses\\n/ record reason'),
  ('ACKNOWLEDGED','RESOLVED','condition cleared [BR-M1-07]\\n/ close alert'),
  ('ACKNOWLEDGED','DISMISSED','operator dismisses\\n/ record reason')],
 final=('RESOLVED','DISMISSED'),
 extra='label=<<b>Alert lifecycle — Module M1</b>>; labelloc=t; fontsize=18; ranksep=1.6; nodesep=0.9;',
 notes=['__init [shape=circle, style=filled, fillcolor=black, label="", width=0.22];',
        '__init -> OPEN [label="threshold breached\\n[BR-M1-03 / BR-M1-04]\\n/ create alert, publish\\nHubNearCapacityEvent"];',
        'note [shape=note, style=filled, fillcolor="#fff6d5", color="#c9a227", fontname="DejaVu Sans", fontsize=12, '
        'label="BR-M1-06: a hub holds at most one alert\\nin OPEN or ACKNOWLEDGED state\\nfor each alert type"];',
        'note -> OPEN [style=dashed, arrowhead=none, color="#c9a227"];'])

# ---------------------------------------------------------------- activity: UC-M1-07 (ThresholdEvaluationTask, every 30 s)
src=activity_header('UC-M1-07 — Evaluate thresholds, raise and clear alerts (M1)')+f'''
t [label="Timer fires every 30 s\\nBR-M1-05", {ACT}];
d0 [label="Previous cycle\\nfinished?", {DEC}];
sk [label="Skip this run, write log", {ERR}];
load [label="Load all hubs", {ACT}];
h [label="Any hub left?", {DEC}];
rd [label="Read hub resource state", {ACT}];
da [label="Data available?", {DEC}];
sk2 [label="Write log, skip this hub", {ERR}];
cmp [label="Compute parking utilisation (BR-M1-01)\\ncount available vehicles (BR-M1-08)", {ACT}];
ty [label="Any alert type left?\\ncapacity / low vehicle", {DEC}];
br [label="Raise threshold breached?\\n80 % / ≤ 2 vehicles", {DEC}];
ex [label="Open alert of\\nthis type exists?", {DEC}];
rf [label="Refresh latest figures,\\nno duplicate alert (BR-M1-06)", {ACT}];
cr [label="Create alert in state OPEN", {ACT}];
pub [label="Publish HubNearCapacityEvent\\n(hubId, resourceType, percentage)", {ACT}];
cl [label="Open alert exists and\\nbelow clear threshold?\\n< 75 % / ≥ 4 vehicles", {DEC}];
rs [label="Close alert:\\ntransition to RESOLVED", {ACT}];
push [label="Push changes to the\\ndashboard over WebSocket", {OK}];
e {END};
s -> t -> d0; d0 -> sk [label="no"]; d0 -> load [label="yes"]; sk -> e;
load -> h; h -> rd [label="yes"]; h -> push [label="no"]; push -> e;
rd -> da; da -> sk2 [label="no"]; da -> cmp [label="yes"]; sk2 -> h [constraint=false];
cmp -> ty; ty -> h [label="no", constraint=false]; ty -> br [label="yes"];
br -> ex [label="yes"]; br -> cl [label="no"];
ex -> rf [label="yes"]; ex -> cr [label="no"]; cr -> pub; pub -> ty [constraint=false]; rf -> ty [constraint=false];
cl -> rs [label="yes"]; cl -> ty [label="no", constraint=false]; rs -> ty [constraint=false];
{{rank=same; rf; cr; rs}}
}}'''
run_dot(src, O+'m1_activity.png', dpi=150)

# ---------------------------------------------------------------- sequence: dashboard load and live push
mermaid(O+'m1_sequence.png', r'''
%%{init: {'theme':'base','themeVariables':{'fontFamily':'Arial','fontSize':'17px','actorBkg':'#eef2fb','actorBorder':'#4a6fa5','noteBkgColor':'#fff4c2','noteBorderColor':'#c9a227','activationBkgColor':'#dfe8f7'}}}%%
sequenceDiagram
    autonumber
    actor Op as Operator
    participant Page as DashboardPage
    participant Broker as STOMP broker
    participant Ctrl as MonitoringController
    participant Alerts as AlertService
    participant Svc as MonitoringService
    participant Repo as Repositories
    participant Handler as MonitoringEventHandler
    participant Store as ProcessedEventStore
    participant Owner as M4 / M5 (owner)

    rect rgb(232, 240, 252)
    Note over Op,Repo: A — initial load (one aggregated call)
    Op->>Page: open dashboard
    Page->>Ctrl: GET /api/m1/dashboard
    Ctrl->>Alerts: findOpen()
    Alerts-->>Ctrl: List<AlertDto>
    Ctrl->>Svc: getDashboard(openAlerts)
    Svc->>Repo: countByHub()<br/>countAvailableVehiclesPerHub()
    Repo-->>Svc: per-hub counts
    Svc->>Svc: computeUtilisation(HubCounts)<br/>BR-M1-01, 02, 08, 09
    Svc-->>Ctrl: DashboardDto
    Ctrl-->>Page: 200 OK + JSON
    Page-->>Op: render three regions
    end

    rect rgb(230, 245, 230)
    Note over Page,Broker: B — subscribe once
    Page->>Broker: CONNECT /ws<br/>SUBSCRIBE /topic/hubs, /topic/alerts
    Broker-->>Page: connected
    end

    rect rgb(255, 246, 220)
    Note over Broker,Owner: C — push when an owner announces a state change
    Owner-)Handler: ResourceStateUpdatedEvent<br/>(eventId, hubId, type, id, newState)
    Handler->>Store: markIfNew("M1", eventId)
    alt eventId already processed
        Store-->>Handler: false
        Handler->>Handler: skip duplicate
    else new eventId
        Store-->>Handler: true
        Handler->>Repo: applyState(newState)<br/>m1_resource_state
        Handler->>Svc: buildHubStatus(hubId)
        Svc-->>Handler: HubStatusDto
        Handler->>Broker: publishHubStatus()<br/>/topic/hubs
        Broker-->>Page: HubStatusDto
        Page->>Page: patch row, flash once<br/>(FR-M1-06)
    end
    end

    rect rgb(252, 232, 232)
    Note over Op,Ctrl: D — exceptions 6a / 7a: channel lost
    Broker--xPage: connection closed
    Page-->>Op: "Data may be stale —<br/>last updated HH:MM:SS"
    loop every 15 s until reconnected
        Page->>Ctrl: GET /api/m1/dashboard
    end
    end
''', width=1800, config=os.path.join(HERE_M1,'m1_mermaid_config.json'))
print('ok')
