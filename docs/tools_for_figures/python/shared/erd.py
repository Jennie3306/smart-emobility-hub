import os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
from lib import *
O=png_dir('shared')
O=O+'erd.png'
F='fontname="DejaVu Sans"'
T={ # table: (owner, color, [(col, key)])
'app_user':('shared','#e8e8e8',[('id','PK'),('username','UQ'),('full_name',''),('role','')]),
'hub':('shared','#e8e8e8',[('id','PK'),('name',''),('location',''),('parking_slots',''),('charging_points','')]),
'alert':('M1','#dbe7f6',[('id','PK'),('hub_id','FK'),('type, state',''),('trigger_value',''),('raised_at, closed_at','')]),
'm1_resource_state':('M1','#dbe7f6',[('resource_type','PK'),('resource_id','PK'),('hub_id','FK'),('state','')]),
'reservation':('M2','#e6f2df',[('id','PK'),('user_id','FK'),('hub_id','FK'),('resource_type, resource_id',''),('state',''),('expires_at, start_at, end_at',''),('active_resource_key','UQ'),('active_user_key','UQ')]),
'rental_vehicle':('M3','#fdecd8',[('id','PK'),('code','UQ'),('current_hub_id','FK'),('state, battery_level',''),('reserved_by_user_id',''),('version','')]),
'rental_session':('M3','#fdecd8',[('id','PK'),('vehicle_id','LK'),('user_id','LK'),('start_time, end_time',''),('status','')]),
'parking_space':('M4','#efe3f7',[('id','PK'),('hub_id','FK'),('space_code','UQ*'),('status, blocked_reason',''),('current_reservation_id','FK'),('docked_vehicle_id','FK UQ'),('charger_point_id','UQ LK')]),
'parking_booking_log':('M4','#efe3f7',[('id','PK'),('reservation_id','FK UQ'),('space_id','FK'),('hub_id, student_id','FK'),('personal_vehicle_id','FK'),('slot_start, slot_end',''),('status','')]),
'personal_vehicle':('M4','#efe3f7',[('id','PK'),('student_id','FK'),('license_plate','UQ'),('vehicle_type, status','')]),
'charging_point':('M5','#fff1c9',[('id','PK'),('hub_id','FK'),('state',''),('version','')]),
'charging_session':('M5','#fff1c9',[('id','PK'),('user_id','FK'),('hub_id','FK'),('charging_point_id','FK'),('vehicle_kind, vehicle_id','LK'),('status, priority_level',''),('priority_score, failure_bonus',''),('active_vehicle_key','UQ')]),
'charging_priority_audit':('M5','#fff1c9',[('id','PK'),('session_id','FK'),('operator_user_id','FK'),('old_priority, new_priority',''),('reason, changed_at','')]),
'incident':('M6','#f9dcdc',[('id','PK'),('hub_id','FK'),('resource_type, resource_id','LK'),('status, severity',''),('assignee',''),('version','')]),
'redistribution_tasks':('M7','#dff3f1',[('task_id','PK'),('vehicle_id','FK'),('source_hub_id','FK'),('destination_hub_id','FK'),('status',''),('recommendation_id','LK')]),
'redistribution_recommendation':('M7','#dff3f1',[('id','PK'),('source_hub_id','FK'),('destination_hub_id','FK'),('source, external_ref','UQ*'),('amount, task_count',''),('status','')]),
}
def node(n):
    own,c,cols=T[n]
    rows=''.join(f'<tr><td align="left" port="{cname.split(",")[0]}">{cname}</td><td align="right">{'<font color="#555555" point-size="9">'+k+'</font>' if k else ' '}</td></tr>' for cname,k in cols)
    return f'"{n}" [shape=plaintext, label=<<table border="1" cellborder="0" cellspacing="0" cellpadding="3" color="#555555"><tr><td colspan="2" bgcolor="{c}"><b>{n}</b>  <font point-size="9">({own})</font></td></tr>{rows}</table>>];'
R=[('alert','hub'),('m1_resource_state','hub'),('reservation','hub'),('reservation','app_user'),('rental_vehicle','hub'),('parking_space','hub'),('parking_space','reservation','0..1'),('parking_space','rental_vehicle','0..1'),
('parking_booking_log','reservation','1'),('parking_booking_log','parking_space'),('parking_booking_log','personal_vehicle','0..1'),('personal_vehicle','app_user'),
('charging_point','hub'),('charging_session','hub'),('charging_session','charging_point','0..1'),('charging_session','app_user','0..1'),('charging_priority_audit','charging_session'),
('incident','hub'),('redistribution_tasks','rental_vehicle'),('redistribution_tasks','hub'),('redistribution_recommendation','hub')]
L=[('rental_session','rental_vehicle'),('rental_session','app_user'),('parking_space','charging_point','0..1'),('redistribution_tasks','redistribution_recommendation','0..1')]
src=f'digraph E {{ graph [rankdir=LR, {F}, nodesep=0.25, ranksep=0.9, splines=true, concentrate=false]; node [{F}, fontsize=10]; edge [{F}, fontsize=9, color="#555555", arrowhead=teetee, arrowtail=crowodot, dir=both];\n'
src+='\n'.join(node(n) for n in T)+'\n'
for r in R:
    a,b=r[0],r[1]; one=len(r)>2 and r[2]
    src+=f'"{a}" -> "{b}" [arrowhead={"teeodot" if one=="0..1" else "teetee"}{", arrowtail=teeodot" if one=="1" else ""}];\n'
for r in L:
    a,b=r[0],r[1]
    src+=f'"{a}" -> "{b}" [style=dashed, color="#999999", arrowhead={"teeodot" if len(r)>2 else "teetee"}];\n'
src+='legend [shape=plaintext, fontsize=9, label=<<table border="0" cellspacing="1"><tr><td align="left">PK primary key · FK foreign key · UQ unique (UQ* composite)</td></tr><tr><td align="left">LK logical reference, no DB constraint (dashed)</td></tr><tr><td align="left">crow\'s foot: many side · bar: exactly one · circle: optional</td></tr></table>>];\n'
src+='{rank=same; hub; app_user}\n}'
run_dot(src,O,dpi=150); print('ok')
