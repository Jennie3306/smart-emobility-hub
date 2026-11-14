import html
C={}
def wrap(lines, n=56):
    out=[]
    for l in lines:
        if len(l)<=n or l.startswith(' '): out.append(l); continue
        cur=''
        for part in l.split(', '):
            piece=(cur+', ' if cur else '')+part
            if len(piece)>n and cur:
                out.append(cur+','); cur='      '+part
            else: cur=piece
        out.append(cur)
    return out
def cls(name, stereo=None, attrs=(), ops=(), fill="#eef2fb", border="#4a6fa5"):
    e=html.escape
    ops=wrap(ops); attrs=wrap(attrs)
    rows=[]
    head=(f'<font point-size="11"><i>&laquo;{e(stereo)}&raquo;</i></font><br/>' if stereo else '')+f'<b>{e(name)}</b>'
    rows.append(f'<tr><td bgcolor="{fill}" align="center">{head}</td></tr>')
    a='<br align="left"/>'.join(e(x) for x in attrs)
    rows.append(f'<tr><td align="left" balign="left">{a+"<br align=\"left\"/>" if attrs else " "}</td></tr>')
    o='<br align="left"/>'.join(e(x) for x in ops)
    rows.append(f'<tr><td align="left" balign="left">{o+"<br align=\"left\"/>" if ops else " "}</td></tr>')
    C[name]=f'"{name}" [label=<<table border="1" cellborder="1" cellspacing="0" cellpadding="5" color="{border}">{"".join(rows)}</table>>];'
cls("MonitoringController","REST, /api/m1",ops=[
"+getHubs(hasVehicle: Boolean, hasFreeSlot: Boolean): List<HubSummaryDto>",
"+getHub(hubId: Long): HubDetailDto",
"+getDashboard(): DashboardDto",
"+getAlertHistory(from: OffsetDateTime, to: OffsetDateTime, hubId: Long): List<AlertDto>",
"+acknowledge(alertId: Long, principal: Principal): AlertDto",
"+dismiss(alertId: Long, request: DismissRequest): AlertDto"])
cls("ThresholdEvaluationTask","scheduled, Timer",ops=[
"+evaluateAll(): void",
"-evaluateHub(hub: Hub, counts: HubCounts, availableVehicles: int): void",
"-evaluate(hubId: Long, type: AlertType, value: int,",
"          breached: boolean, cleared: boolean): void"],fill="#fff4dc",border="#c9a227")
cls("MonitoringEventHandler","event listener, @Order(100)",ops=[
"+onResourceStateUpdated(e: ResourceStateUpdatedEvent): void",
"+onReservationConfirmed(e: ReservationConfirmedEvent): void",
"+onVehiclePickedUp(e: VehiclePickedUpEvent): void",
"+onVehicleReturned(e: VehicleReturnedEvent): void",
"+onVehicleFailed(e: VehicleFailedEvent): void",
"+onVehicleLowBattery(e: VehicleLowBatteryEvent): void",
"+onChargingStarted(e: ChargingStartedEvent): void",
"+onChargingCompleted(e: ChargingCompletedEvent): void",
"+onDispatchStarted(e: DispatchStartedEvent): void",
"+onDispatchCompleted(e: DispatchCompletedEvent): void",
"+onDispatchFailed(e: DispatchFailedEvent): void",
"+normalise(type: String, state: String): String {static}"],fill="#fff4dc",border="#c9a227")
cls("MonitoringService","service",ops=[
"+computeUtilisation(c: HubCounts): UtilisationDto",
"+countsPerHub(): Map<Long, HubCounts>",
"+availableVehiclesPerHub(): Map<Long, Integer>",
"+getHubSummaries(): List<HubSummaryDto>",
"+getHubDetail(hubId: Long): HubDetailDto",
"+getDashboard(openAlerts: List<AlertDto>): DashboardDto",
"+buildHubStatus(hubId: Long): Optional<HubStatusDto>"])
cls("AlertService","service",ops=[
"+raiseOrRefresh(hubId: Long, type: AlertType, value: int): Alert",
"+clear(alert: Alert): void",
"+acknowledge(alertId: Long, username: String): AlertDto",
"+dismiss(alertId: Long, reason: String): AlertDto",
"+findOpen(): List<AlertDto>",
"+findHistory(from: OffsetDateTime, to: OffsetDateTime, hubId: Long): List<AlertDto>",
"+toDto(a: Alert): AlertDto"])
cls("DashboardPublisher","STOMP wrapper",ops=[
"+publishHubStatus(dto: HubStatusDto): void",
"+publishAlert(dto: AlertDto): void"])
cls("MonitoringProperties","config app.monitoring",attrs=[
"-capacityRaiseThreshold: int = 80",
"-capacityClearThreshold: int = 75",
"-lowVehicleThreshold: int = 2",
"-lowVehicleClearThreshold: int = 4"])
cls("AlertRepository","interface",ops=[
"+findOpenByHubAndType(hubId: Long, type: AlertType): Optional<Alert>",
"+findOpen(): List<Alert>",
"+findByRaisedAtBetweenOrderByRaisedAtDesc(",
"     from: OffsetDateTime, to: OffsetDateTime): List<Alert>",
"+findByHubIdAndRaisedAtBetweenOrderByRaisedAtDesc(",
"     hubId: Long, from: OffsetDateTime, to: OffsetDateTime): List<Alert>"])
cls("ResourceStateRepository","interface",ops=[
"+countByHub(): List<Object[]>",
"+findByResourceTypeAndResourceId(",
"     type: String, id: Long): Optional<ResourceState>",
"+findOfHub(hubId: Long, type: String): List<ResourceState>"])
cls("HubRepository","interface",ops=[
"+findAllByOrderByIdAsc(): List<Hub>",
"+countAvailableVehiclesPerHub(): List<Object[]>",
"+findVehiclesOfHub(hubId: Long): List<Object[]>",
"+findVehicleCountByState(): List<Object[]>"])
cls("ProcessedEventStore","shared",ops=["+markIfNew(consumer: String, eventId: UUID): boolean"],fill="#f0f0f0",border="#888888")
cls("EventPublisher","shared",ops=["+publish(event: DomainEvent): void"],fill="#f0f0f0",border="#888888")
cls("Alert","entity, owned by M1",attrs=[
"-id: Long","-hubId: Long","-type: AlertType","-state: AlertState","-triggerValue: int",
"-raisedAt: OffsetDateTime","-acknowledgedBy: String","-acknowledgedAt: OffsetDateTime",
"-closedAt: OffsetDateTime","-dismissReason: String"],ops=[
"+raise(hubId: Long, type: AlertType, value: int): Alert {static}",
"+acknowledge(username: String): void","+dismiss(reason: String): void",
"+resolve(): void","+refresh(latestValue: int): void"],fill="#e6f2e6",border="#3c8a3c")
cls("ResourceState","entity, read model owned by M1",attrs=[
"-resourceType: String {PK}","-resourceId: Long {PK}","-hubId: Long","-state: String","-updatedAt: OffsetDateTime"],
ops=["+applyState(newState: String): void"],fill="#e6f2e6",border="#3c8a3c")
cls("Hub","entity, shared, read only",attrs=["-id: Long","-name: String","-location: String"],fill="#f0f0f0",border="#888888")
cls("HubCounts","record",attrs=["-hubId: long","-totalSlots, usedSlots, freeSlots: long","-operationalPoints, pointsInUse, freePoints: long"])
cls("AlertState","enumeration",attrs=["OPEN","ACKNOWLEDGED","RESOLVED","DISMISSED"],ops=["+isOpen(): boolean","+canMoveTo(target: AlertState): boolean"])
cls("AlertType","enumeration",attrs=["CAPACITY","LOW_VEHICLE"])
edges='''
MonitoringController -> MonitoringService
MonitoringController -> AlertService
ThresholdEvaluationTask -> MonitoringService
ThresholdEvaluationTask -> AlertService
ThresholdEvaluationTask -> MonitoringProperties
ThresholdEvaluationTask -> DashboardPublisher
MonitoringEventHandler -> MonitoringService
MonitoringEventHandler -> ResourceStateRepository
MonitoringEventHandler -> ProcessedEventStore
MonitoringEventHandler -> DashboardPublisher
AlertService -> AlertRepository
AlertService -> EventPublisher [label=" publishes\\n HubNearCapacityEvent"]
AlertService -> DashboardPublisher
MonitoringService -> HubRepository
MonitoringService -> ResourceStateRepository
MonitoringService -> HubCounts [style=dashed, label=" «use»"]
AlertRepository -> Alert [style=dashed]
ResourceStateRepository -> ResourceState [style=dashed]
HubRepository -> Hub [style=dashed]
Alert -> AlertState
Alert -> AlertType
'''
out=['digraph G {','graph [rankdir=TB, splines=spline, nodesep=0.35, ranksep=0.6, fontname="Arial", pad=0.2, dpi=200, label=<<b>Module M1 — Hub Monitoring and Operations Dashboard: class diagram</b>>, labelloc=t, fontsize=18];',
'node [shape=plaintext, fontname="Arial", fontsize=14];','edge [fontname="Arial", fontsize=11, arrowhead=vee, color="#444444"];']
out+=C.values()
for r in [["MonitoringController","ThresholdEvaluationTask","MonitoringEventHandler"],
          ["MonitoringService","AlertService","DashboardPublisher","MonitoringProperties"],
          ["HubRepository","ResourceStateRepository","AlertRepository","ProcessedEventStore","EventPublisher"],
          ["Hub","ResourceState","Alert","HubCounts"]]:
    out.append('{rank=same; '+'; '.join(f'"{x}"' for x in r)+';}')
for l in edges.strip().splitlines():
    a,rest=l.split(' -> ')
    b,_,attr=rest.partition(' ')
    out.append(f'"{a}" -> "{b}" {attr}')
out.append('"Hub" -> "Alert" [dir=none, style=solid, headlabel="0..*", taillabel="1", label=" raised for", constraint=false];')
out.append('"Hub" -> "ResourceState" [dir=none, headlabel="0..*", taillabel="1", label=" located at", constraint=false];')
out.append('}')
DOT='\n'.join(out)
