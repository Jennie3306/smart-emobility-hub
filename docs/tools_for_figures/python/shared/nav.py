import os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
from lib import *
O=png_dir('shared')
O=O+'navigation.png'
F='fontname="DejaVu Sans"'
def p(k,t,sub,fill='#eef2fb',col='#4a6fa5'): return f'{k} [label=<<b>{t}</b><br/><font point-size="9">{sub}</font>>, shape=box, style="rounded,filled", fillcolor="{fill}", color="{col}", penwidth=1.5];'
OP=('#fdecd8','#b4690e')
src=f'''digraph N {{ graph [rankdir=LR, {F}, nodesep=0.22, ranksep=0.7]; node [{F}, fontsize=11]; edge [{F}, fontsize=9, color="#666666"];
login [label=<<b>Sign in</b><br/><font point-size="9">/login</font>>, shape=box, style="rounded,filled", fillcolor="#1f4e79", fontcolor="#ffffff", color="#1f4e79"];
dash [label=<<b>Top navigation bar</b><br/><font point-size="9">on every page; the server<br/>enforces the role</font>>, shape=box, style="rounded,filled", fillcolor="#e8e8e8", color="#777777"];
subgraph cluster_s {{ label="Student"; style="rounded"; color="#4a6fa5"; fontcolor="#1f4e79";
{p('hubs','Hubs','list, filter, hub detail')}
{p('find','Find a vehicle (M2)','search, reserve')}
{p('mine','My reservation','hold timer, pick up, return,<br/>Current trip card (M3)')}
{p('park','Parking (M4)','student view: book a space,<br/>charging on arrival')}
{p('chg','Smart charging (M5)','request, my sessions, start, cancel')}
}}
subgraph cluster_o {{ label="Operator"; style="rounded"; color="#b4690e"; fontcolor="#b4690e";
{p('odash','Dashboard (M1)','live figures, open alerts',*OP)}
{p('alerts','Alerts','history',*OP)}
{p('veh','Rental vehicles (M3)','fleet list',*OP)}
{p('opark','Parking (M4)','operator view: block, unblock,<br/>bookings, spaces',*OP)}
{p('ochg','Smart charging (M5)','queue, schedule, priority, audit',*OP)}
{p('inc','Incidents and IoT (M6)','incidents, simulator',*OP)}
{p('red','Redistribution (M7)','recommendations, tasks',*OP)}
{p('wif','What-if Studio (M8)','simulation, history, network',*OP)}
}}
login -> dash; dash -> hubs [label="Student"]; dash -> find; dash -> mine; dash -> park; dash -> chg;
dash -> odash [label="Operator"]; dash -> alerts; dash -> veh; dash -> opark; dash -> ochg; dash -> inc; dash -> red; dash -> wif;
find -> mine [label="Reserve", style=dashed, constraint=false]; hubs -> find [label="hub chosen", style=dashed, constraint=false];
odash -> red [label="alert → proposal", style=dashed, constraint=false];
}}'''
run_dot(src,O,dpi=150); print('ok')
