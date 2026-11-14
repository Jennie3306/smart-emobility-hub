import os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
from lib import *
O=png_dir('shared')
O=O+'deployment.png'
F='fontname="DejaVu Sans"'
def art(k,t,sub,fill='#eef2fb',col='#4a6fa5'): return f'{k} [label=<<font point-size="9">«container»</font><br/><b>{t}</b><br/><font point-size="9">{sub}</font>>, shape=component, style="filled", fillcolor="{fill}", color="{col}", penwidth=1.5];'
src=f'''digraph D {{ graph [rankdir=TB, {F}, nodesep=0.4, ranksep=0.8]; node [{F}, fontsize=11]; edge [{F}, fontsize=9, color="#555555"];
subgraph cluster_c {{ label=<«device» Student or operator PC / phone>; style="filled"; fillcolor="#fafafa"; color="#888888";
 br [label=<<b>Web browser</b><br/><font point-size="9">React SPA, STOMP client</font>>, shape=box, style="rounded,filled", fillcolor="#ffffff"]; }}
subgraph cluster_h {{ label=<«execution environment» Docker Compose on the demo host<br/><font point-size="9">docker compose -f docker-compose.yml -f docker-compose.demo.yml up -d --build</font>>; style="filled"; fillcolor="#f3f6fb"; color="#1f4e79"; penwidth=1.8;
 {art('fe','frontend','nginx:alpine, port 80<br/>built SPA (npm run build)')}
 {art('be','backend','eclipse-temurin:21-jre, port 8080<br/>app.jar (Spring Boot), Swagger UI')}
 {art('db','db','postgres:16, port 5432<br/>healthcheck pg_isready')}
 vol [label="volume pgdata", shape=cylinder, style=filled, fillcolor="#ffffff", color="#4a6fa5", fontsize=10];
}}
ai [label=<<b>AI provider</b><br/><font point-size="9">optional, HTTPS<br/>M8_LLM_URL / KEY / MODEL</font>>, shape=box, style="rounded,dashed", color="#777777"];
br -> fe [label="HTTP :80\\npages, /api, /ws"];
fe -> be [label="proxy /api (cookie)\\n/ws (upgrade)"];
be -> db [label="JDBC :5432\\nFlyway on start-up"];
db -> vol [arrowhead=none, style=dashed];
be -> ai [style=dashed, label="HTTPS (M8 only)"];
}}'''
run_dot(src,O,dpi=160); print('ok')
