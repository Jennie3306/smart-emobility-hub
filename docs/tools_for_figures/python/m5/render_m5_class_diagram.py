from PIL import Image, ImageDraw, ImageFont
from pathlib import Path
import textwrap
OUT=Path(__file__).resolve().parents[2]/'png'/'m5'/'m5_class_diagram.png'
W,H=1600,1380
im=Image.new('RGB',(W,H),'white'); d=ImageDraw.Draw(im)
base='/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'; bold='/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
ft=ImageFont.truetype(bold,38); fh=ImageFont.truetype(bold,27); fm=ImageFont.truetype(base,22); fb=ImageFont.truetype(base,22); small=ImageFont.truetype(base,21)
ink='#18364F'; muted='#536A7D'; border='#315B7D'; green='#E5F4EC'; blue='#EAF2FB'; amber='#FFF3D9'; purple='#F1EDFA'; gray='#F7F9FB'; green_border='#23845B'; amber_border='#B7791F'; purple_border='#8063AA'

def box(x,y,w,h,title,meta,lines,fill=gray,stroke=border):
    d.rounded_rectangle((x,y,x+w,y+h),radius=16,fill=fill,outline=stroke,width=3)
    d.rectangle((x+1,y+1,x+w-1,y+58),fill=fill)
    d.line((x,y+60,x+w,y+60),fill=stroke,width=2)
    d.text((x+18,y+12),title,font=fh,fill=ink)
    d.text((x+18,y+68),meta,font=fm,fill=muted)
    yy=y+104; max_chars=max(18,int((w-38)/12.5))
    for line in lines:
        for fragment in textwrap.wrap(line,width=max_chars,break_long_words=True):
            d.text((x+18,yy),fragment,font=small,fill='#243746'); yy+=31

def down_arrow(x1,y1,x2,y2,label=''):
    mid=(y1+y2)//2
    d.line((x1,y1,x1,mid,x2,mid,x2,y2-12),fill=muted,width=3)
    d.polygon([(x2,y2),(x2-10,y2-18),(x2+10,y2-18)],fill=muted)
    if label: d.text((min(x1,x2)+14,mid-28),label,font=small,fill=muted)

d.text((45,30),'M5 · Smart charging class design',font=ft,fill=ink)
d.text((48,82),'Core classes and important public operations · com.hcmut.emobility.m5charging',font=fm,fill=muted)
# Top: application boundary
box(45,150,455,290,'ChargingController','controller · REST /api/m5',[
    '+ request(ChargingRequest, auth): ResponseEntity',
    '+ schedule(ChargingScheduleRequest, auth): ResponseEntity',
    '+ moveSchedule(id, body, auth): ChargingSessionResponse',
    '+ priority(id, body, auth): ChargingSessionResponse',
],blue)
box(565,150,470,290,'ChargingService','service · transactional business logic',[
    '+ request(...) / schedule(...): ChargingSessionResponse',
    '+ changePriority(...) / moveSchedule(...): ChargingSessionResponse',
    '+ start(...) / cancel(...): ChargingSessionResponse',
    '+ handleChargerFailed(pointId, hubId): void',
    '+ runScheduledTasks(): void',
],green,green_border)
box(1100,150,455,290,'ChargingEventHandler','event handler · @Order(0) · consumer M5',[
    '+ onChargingRequested(event): void',
    '+ onChargerFailed(event): void',
    '+ onResourceStateUpdated(event): void',
    '+ onVehicleReturned / onVehicleLowBattery(event): void',
    '+ onReservationCancelled / onExpired(event): void',
    '+ onRecommendationApproved(event): void',
],amber,amber_border)
down_arrow(270,440,770,565,'REST'); down_arrow(1330,440,900,565,'shared events')
# second tier: policies and dependencies
box(45,590,455,245,'ChargingPriorityPolicy','policy · deterministic score & comparator',[
    '+ calculateScore(SoC, useAt, waitingSince, bonus, priority, now): double',
    '+ comparator(): Comparator[ChargingSession]',
],blue)
box(565,590,470,245,'Repositories','persistence · row locking',[
    'ChargingSessionRepository: lockById(), status queries',
    'ChargingPointRepository: lockByHubAndState(s)()',
    'ChargingPriorityAuditRepository: findBySessionId...()',
],gray)
box(1100,590,455,245,'ChargingScheduler / EventPublisher','non-interactive + shared event boundary',[
    'ChargingScheduler.run(): void · every 60 seconds',
    'EventPublisher.publish(DomainEvent): void',
    'Queue update: /topic/m5/hubs/{hubId}/queue',
],purple,purple_border)
down_arrow(800,440,270,590,'score'); down_arrow(800,440,800,590,'repository'); down_arrow(800,440,1320,590,'timer / events')
# third tier: entities
box(45,985,455,320,'ChargingSession','entity · aggregate root',[
    'Fields: id, hubId, vehicleKind, vehicleId, batteryLevel, status, priorityScore',
    'Times: scheduledStartAt, scheduledEndAt, waitingSince, holdExpiresAt',
    '+ enqueue(), assign(), start(), complete(), interrupt(), cancel()',
    '+ changePriority(), moveSchedule(), requeueAfterFailure()',
],blue)
box(565,985,470,320,'ChargingPoint','entity · M5 owns allocation; M6 owns health',[
    'Fields: id, hubId, state (AVAILABLE / RESERVED / IN_USE / FAULTY / UNDER_REPAIR)',
    '+ reserve(): void · + startCharging(): void',
    '+ release(): void · + markFaulty(): void',
    '+ applyExternalState(state): void',
],blue)
box(1100,985,455,320,'ChargingPriorityAudit','entity · BR-M5-09 audit trail',[
    'Fields: sessionId, operatorUserId, oldPriority, newPriority',
    'reason: String · changedAt: OffsetDateTime',
    'Records the operator, timestamp and reason for each change',
],blue)
# persistence relations
for x1,x2,label in [(800,270,'persists sessions'),(800,800,'persists ports'),(800,1320,'persists audit')]:
    d.line((800,835,800,900),fill=muted,width=3)
    d.line((x1,900,x2,900),fill=muted,width=3)
    d.line((x2,900,x2,973),fill=muted,width=3)
    d.polygon([(x2,985),(x2-10,967),(x2+10,967)],fill=muted)
    d.text((x2-80,910),label,font=small,fill=muted)
im.save(OUT)
print(OUT)
