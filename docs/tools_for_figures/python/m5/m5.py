import os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
from lib import *
O=png_dir('m5')
usecase(O+'m5_usecase.png','«subsystem» M5 — Charging and Priority',
 [('UC-M5-01','Request charging'),('UC-M5-02','Schedule charging'),('UC-M5-04','Cancel a request|or schedule'),('UC-M5-05','View queue|and schedules'),
  ('UC-M5-03','Adjust priority|or schedule'),('UC-M5-06','Rank the queue|and assign ports'),('UC-M5-07','Reassign after|a charger failure'),('UC-M5-08','Finish or expire|a session')],
 left=[('stu','Student',False),('op','Operator',False)], right=[('tim','Timer (60 s)',True),('iot','M6 / owner events',True)],
 assoc=[('stu','UC-M5-01'),('stu','UC-M5-02'),('stu','UC-M5-04'),('stu','UC-M5-05'),('op','UC-M5-05'),('op','UC-M5-03'),('op','UC-M5-01'),
        ('tim','UC-M5-06'),('tim','UC-M5-08'),('iot','UC-M5-07'),('iot','UC-M5-06')],
 rels=[('UC-M5-01','UC-M5-06','include'),('UC-M5-03','UC-M5-06','include'),('UC-M5-07','UC-M5-06','include'),('UC-M5-04','UC-M5-05','extend'),('UC-M5-03','UC-M5-05','extend')],
 amber=('UC-M5-06','UC-M5-07','UC-M5-08'))
N={}
N['ChargingController']=klass('ChargingController','REST, /api/m5',attrs=None,ops=['+request(req) / schedule(req)','+start(id) / cancel(id)','+mine() / hubs() / queue(hubId)  «Operator»','+changePriority(id, req) / moveSchedule(id, req) / audit(id)  «Operator»'])
N['ChargingService']=klass('ChargingService','service',attrs=['-lowBatteryThreshold: int = 20'],ops=['+request(userId, req): ChargingSessionResponse','+schedule(userId, req): ChargingSessionResponse','+start / cancel(sessionId, userId, operator)',
 '+changePriority(sessionId, operatorId, level, reason)','+runScheduledTasks(): void','+handleChargerFailed(pointId, hubId): void','+handleVehicleReturned / handleVehicleLowBattery','+handleM4ChargingRequest(...): boolean','+applyRecommendation(id, actionType): void','-recalculateHub(hubId, now): void','-expirePortHolds(now) / progressCharging(now)'])
N['ChargingPriorityPolicy']=klass('ChargingPriorityPolicy','utility',attrs=None,ops=['+calculateScore(battery, expectedUse, waitingSince, bonus, level, now): double','+comparator(): Comparator<ChargingSession>'],**EXT)
N['ChargingScheduler']=klass('ChargingScheduler','scheduled, every 60 s',attrs=None,ops=['+run(): void'],**SCHED)
N['ChargingEventHandler']=klass('ChargingEventHandler','event listener',attrs=None,ops=['+onChargerFailed / onDeviceReadingReported','+onVehicleReturned / onVehicleLowBattery','+onChargingRequested (M4)','+onReservationCancelled / onReservationExpired','+onRecommendationApproved'],**SCHED)
N['ChargingSession']=klass('ChargingSession','entity, charging_session',attrs=['-userId, hubId, parkingSpaceId: Long','-vehicleKind: VehicleKind, vehicleId: Long','-batteryLevel, startBatteryLevel: int','-status: ChargingSessionStatus','-priorityLevel: PriorityLevel','-priorityScore: double, failureBonus: int','-chargingPointId: Long','-scheduledStartAt, scheduledEndAt, holdExpiresAt','-waitingSince, chargingStartedAt, completedAt','-activeVehicleKey: String (UNIQUE)'],
 ops=['+request(...) / schedule(...) {static}','+enqueue / assign / start / complete','+interrupt / requeueAfterFailure / applyFailureBonus','+cancel / returnToQueue / changePriority'])
N['ChargingPoint']=klass('ChargingPoint','entity, charging_point',attrs=['-id, hubId: Long','-state: ChargingPointState','-version: Long'],ops=['+reserve / startCharging / release','+markFaulty / recover()','+applyExternalState(value): boolean'])
N['ChargingPriorityAudit']=klass('ChargingPriorityAudit','entity',attrs=['-sessionId, operatorUserId','-oldPriority, newPriority, reason','-changedAt, changeType'],ops=None)
N['Status']=klass('ChargingSessionStatus','enumeration',attrs=['REQUESTED, QUEUED, SCHEDULED,','CHARGING, INTERRUPTED,','COMPLETED, CANCELLED'],ops=None,**ENUM)
N['PState']=klass('ChargingPointState','enumeration',attrs=['AVAILABLE, RESERVED, IN_USE,','FAULTY, UNDER_REPAIR'],ops=None,**ENUM)
N['EventPublisher']=klass('EventPublisher','shared',attrs=None,ops=['ResourceStateUpdated (CHARGING_POINT)','ChargingStarted / ChargingCompleted'],**EXT)
classdiagram(O+'m5_class.png',N,[('ChargingController','ChargingService','assoc'),('ChargingScheduler','ChargingService','assoc'),('ChargingEventHandler','ChargingService','assoc'),
 ('ChargingService','ChargingPriorityPolicy','dep','ranks with'),('ChargingService','ChargingSession','assoc','manages'),('ChargingService','ChargingPoint','assoc','assigns'),
 ('ChargingService','ChargingPriorityAudit','assoc','records'),('ChargingService','EventPublisher','dep','publishes'),
 ('ChargingSession','ChargingPoint','assoc','0..* → 0..1'),('ChargingSession','ChargingSessionStatus','assoc'),('ChargingPoint','ChargingPointState','assoc'),
 ('ChargingPriorityAudit','ChargingSession','assoc','0..* → 1')],extra='splines=true;')
mermaid(O+'m5_sequence.png','''sequenceDiagram
autonumber
participant M6 as DeviceEventService (M6)
participant EP as EventPublisher
participant H as ChargingEventHandler (M5)
participant CS as ChargingService (M5)
participant M4 as ParkingEventHandler (M4)
participant M1 as MonitoringEventHandler (M1)
M6->>M6: record Incident (one per resource)
M6->>EP: ChargerFailedEvent(point, hub, reason)
EP->>H: onChargerFailed (markIfNew M5, eventId)
H->>CS: handleChargerFailed(pointId, hubId)
CS->>CS: point.markFaulty()
CS->>EP: ResourceStateUpdated(CHARGING_POINT, FAULTY)
EP->>M1: mirror FAULTY on the dashboard
loop each session on the point
  alt booked window not started yet
    CS->>CS: move the booking to another free point
  else charging or held
    CS->>CS: save battery, interrupt(), requeueAfterFailure()
  end
end
CS->>CS: recalculateHub — rank by P, assign free points
alt still waiting
  CS->>CS: applyFailureBonus (+20), recalculate again
end
EP->>M4: ChargerFailedEvent → block the space of the charger
''', width=1500)


# ---------------------------------------------------------------- state
# State: ChargingSession, following ChargingSession.java (assign / enqueue / start / interrupt / returnToQueue / cancel).
statemachine(O+'m5_state.png',
 ['REQUESTED','QUEUED','SCHEDULED',('CHARGING','fillcolor="#e1f3e8", color="#3a7d44"'),('INTERRUPTED','fillcolor="#fff1c9", color="#c9a227"'),
  ('COMPLETED','fillcolor="#e8e8e8", color="#999999"'),('CANCELLED','fillcolor="#e8e8e8", color="#999999"')],
 [('REQUESTED','QUEUED','enqueue [no free point]'),
  ('REQUESTED','SCHEDULED','assign [point free] or\\nbooked window inside a parking booking'),
  ('QUEUED','SCHEDULED','allocation [highest P,\\nURGENT first] / hold 10 min'),
  ('SCHEDULED','CHARGING','start [within the hold\\nor the booked window]'),
  ('SCHEDULED','QUEUED','hold expires\\n[automatic low-battery session]','style=dashed'),
  ('CHARGING','COMPLETED','Timer [SoC ≥ 90% or 90 min\\nor the window ends]'),
  ('SCHEDULED','INTERRUPTED','ChargerFailed\\n/ release the point'),
  ('CHARGING','INTERRUPTED','ChargerFailed\\n/ save SoC'),
  ('INTERRUPTED','SCHEDULED','another point free\\nin the same hub'),
  ('INTERRUPTED','QUEUED','no replacement / keep waitingSince,\\n+20 priority'),
  ('REQUESTED','CANCELLED','cancel'),('QUEUED','CANCELLED','cancel'),
  ('SCHEDULED','CANCELLED','cancel, or hold expires\\n[student session]'),('INTERRUPTED','CANCELLED','cancel')],
 initial='REQUESTED', final=('COMPLETED','CANCELLED'), rankdir='TB',
 extra='label=<<b>ChargingSession lifecycle — Module M5</b>>; labelloc=t; fontsize=18; ranksep=0.9; nodesep=1.0;',
 notes=['note [shape=note, style=filled, fillcolor="#fff6d5", color="#c9a227", fontname="DejaVu Sans", fontsize=11, '
        'label="CHARGING cannot be cancelled: the Timer ends it.\\nQUEUED never jumps to CHARGING: a vehicle charges\\nonly on the point assigned to it."];',
        'note -> CHARGING [style=dashed, arrowhead=none, color="#c9a227"];'])
# ---------------------------------------------------------------- activity: TV5's Graphviz source, unchanged
# (originally docs/figures/m5_activity_allocation.dot)
run_dot(r'''digraph M5Activity {
  graph [rankdir=TB, bgcolor="white", pad="0.25", nodesep="0.3", ranksep="0.42", splines=ortho, label="M5 · Priority queue dispatch and charger-failure handling", labelloc=t, fontsize=20, fontname="Arial"];
  node [shape=box, style="rounded,filled", fillcolor="#f4f7fb", color="#315b7d", penwidth=1.3, fontname="Arial", fontsize=11, margin="0.14,0.10"];
  edge [color="#38556e", penwidth=1.1, arrowsize=0.75, fontname="Arial", fontsize=10];
  start [shape=circle, label="", width=0.18, height=0.18, fillcolor="#263746", color="#263746"];
  incoming [label="New request / Timer tick /\nVehicleReturned or VehicleLowBattery", fillcolor="#eaf2fb"];
  validate [label="Validate SoC < 90%, Hub and\nno active session for this vehicle"];
  valid [shape=diamond, label="Valid?", width=1.15, height=0.7, fillcolor="#fff2d8"];
  reject [label="Reject request and return\nBusinessException", fillcolor="#fde8e7", color="#bd4a45"];
  score [label="Calculate P = 0.5B + 0.3U + 0.2W\n(HIGH +30; URGENT first)"];
  sort [label="Sort by URGENT → P descending → FIFO"];
  port [shape=diamond, label="Available port\nwithout overlap?", width=1.55, height=0.9, fillcolor="#fff2d8"];
  assign [label="Lock port + assign session\nSCHEDULED · hold 10 minutes", fillcolor="#e1f3ee", color="#23845b"];
  queue [label="Keep session QUEUED\nShow position and wait estimate", fillcolor="#eaf2fb"];
  timer [label="Every 60 seconds: expire holds,\nadvance charging SoC and recalculate"];
  done [shape=doublecircle, label="", width=0.2, height=0.2, fillcolor="#263746", color="#263746"];
  fail [label="ChargerFailed from M6", fillcolor="#fff2d8", color="#b7791f"];
  save [label="Persist current SoC; set INTERRUPTED;\nmark failed port FAULTY", fillcolor="#fff2d8", color="#b7791f"];
  replacement [shape=diamond, label="Another port\navailable in same Hub?", width=1.55, height=0.9, fillcolor="#fff2d8"];
  reassign [label="Assign replacement port;\nkeep wait history", fillcolor="#e1f3ee", color="#23845b"];
  requeue [label="Requeue; preserve waitingSince;\nadd +20 only if still queued", fillcolor="#eaf2fb"];
  start -> incoming -> validate -> valid;
  valid -> reject [label="No"];
  valid -> score [label="Yes"];
  score -> sort -> port;
  port -> assign [label="Yes"];
  port -> queue [label="No"];
  assign -> timer;
  queue -> timer;
  timer -> done;
  fail -> save -> replacement;
  replacement -> reassign [label="Yes"];
  replacement -> requeue [label="No"];
  reassign -> done;
  requeue -> done;
  reject -> done;
}''', O+'m5_activity.png', dpi=150)
print('ok')
