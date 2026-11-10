from PIL import Image, ImageDraw, ImageFont
from pathlib import Path
import math, textwrap
OUT=Path(__file__).with_name('m5_sequence_charger_failed.png')
W,H=2200,1480
im=Image.new('RGB',(W,H),'white'); d=ImageDraw.Draw(im)
base='/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'; bold='/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
ft=ImageFont.truetype(bold,40); fh=ImageFont.truetype(bold,22); fb=ImageFont.truetype(base,19); fs=ImageFont.truetype(base,17)
ink='#18364F'; line='#476176'; blue='#EAF2FB'; amber='#FFF3D9'; green='#E5F4EC'; purple='#F1EDFA'; red='#FCE8E7'; border='#315B7D'; muted='#536A7D'
d.text((55,34),'M5 · Sequence: ChargerFailedEvent and port reassignment',font=ft,fill=ink)
d.text((58,88),'M6 is authoritative for device health · M5 owns the charging session and charging-point assignment',font=fb,fill=muted)
actors=[('M6 Device',150,blue),('Spring Event Bus',450,purple),('M5 Event Handler',760,amber),('ProcessedEventStore',1070,purple),('ChargingService',1390,green),('M5 Repositories',1710,blue),('M1 Projection',2010,blue)]
box_w=210; box_h=78; top=145; bottom=1370
centers=[]
for name,x,fill in actors:
    centers.append(x+box_w//2)
    d.rounded_rectangle((x,top,x+box_w,top+box_h),radius=14,fill=fill,outline=border,width=3)
    bb=d.textbbox((0,0),name,font=fh); tw=bb[2]-bb[0]
    d.text((x+(box_w-tw)/2,top+23),name,font=fh,fill=ink)
    d.line((x+box_w//2,top+box_h,x+box_w//2,bottom),fill='#9aa9b5',width=2)

def arrow(i,j,y,label, color=line, dashed=False, width=3):
    x1=centers[i]; x2=centers[j]
    # horizontal message arrow
    dash=12
    if dashed:
        xx=x1
        while xx<x2-15:
            d.line((xx,y,min(xx+dash,x2-15),y),fill=color,width=width); xx+=dash*2
    else:
        d.line((x1,y,x2-15,y),fill=color,width=width)
    d.polygon([(x2-15,y-9),(x2,y),(x2-15,y+9)],fill=color)
    # wrapped label on a white background centered on line
    lines=textwrap.wrap(label,width=48)
    boxh=len(lines)*23+8; boxw=max(d.textbbox((0,0),s,font=fs)[2] for s in lines)+18
    cx=(x1+x2)//2
    d.rounded_rectangle((cx-boxw//2,y-23-boxh,cx+boxw//2,y-12),radius=7,fill='white',outline='#d5dde4',width=1)
    yy=y-23-boxh+4
    for s in lines:
        bb=d.textbbox((0,0),s,font=fs); d.text((cx-(bb[2]-bb[0])/2,yy),s,font=fs,fill=ink); yy+=23

def return_arrow(i,j,y,label):
    x1=centers[i]; x2=centers[j]
    d.line((x1,y,x2+15,y),fill=muted,width=2)
    d.polygon([(x2+15,y-8),(x2,y),(x2+15,y+8)],fill=muted)
    bb=d.textbbox((0,0),label,font=fs); d.text(((x1+x2)/2-(bb[2]-bb[0])/2,y+7),label,font=fs,fill=muted)

# Messages
arrow(0,1,310,'publish ChargerFailedEvent(pointId, hubId, reason)')
arrow(1,2,440,'onChargerFailed(event) · @Order(0)')
arrow(2,3,570,'markIfNew("M5", eventId)')
return_arrow(3,2,615,'true · first delivery')
arrow(2,4,700,'handleChargerFailed(pointId, hubId)')
arrow(4,5,820,'load charging point and sessions in SCHEDULED / CHARGING')
return_arrow(5,4,870,'port + affected sessions')
arrow(4,5,965,'sync failed port FAULTY; persist latest SoC; INTERRUPTED → QUEUED')
# Alternative note box
alt=(470,1010,1730,1160)
d.rounded_rectangle(alt,radius=15,fill='#fffaf0',outline='#c28a2b',width=3)
d.text((495,1025),'alt · another operational port exists in the same Hub',font=fh,fill='#885a12')
d.text((520,1070),'Yes: lock the replacement port and assign the session (SCHEDULED).',font=fb,fill=ink)
d.text((520,1108),'No: remain QUEUED; preserve waitingSince; +20 only while queued.',font=fb,fill=ink)
arrow(4,1,1210,'publish ResourceStateUpdatedEvent(CHARGING_POINT, state)')
arrow(1,2,1290,'onResourceStateUpdated(event) · M5 sync @Order(0)')
arrow(1,6,1370,'M1 projection handles the same event @Order(100)',color='#23845b')
# explanatory margin note
note='M5 deduplicates with its own consumer key ("M5"), so it does not suppress M1. The queue is then broadcast to /topic/m5/hubs/{hubId}/queue.'
d.rounded_rectangle((70,1390,2130,1450),radius=12,fill=blue,outline=border,width=2)
d.text((92,1407),note,font=fs,fill=ink)
im.save(OUT)
print(OUT)
