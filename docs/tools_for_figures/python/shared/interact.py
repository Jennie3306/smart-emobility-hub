import os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
from lib import *
O=png_dir('shared')
O=O+'module_interaction.png'
F='fontname="DejaVu Sans"'
P={'m6':(4.5,8),'m2':(1,6),'m4':(8,6),'m3':(1,3),'m5':(8,3),'m7':(4.5,1),'m1':(0.6,0),'m8':(8.4,0)}
T={'m6':'M6 IoT and incidents','m2':'M2 Reservation','m4':'M4 Parking','m3':'M3 Rental vehicles','m5':'M5 Smart charging','m7':'M7 Redistribution','m1':'M1 Monitoring','m8':'M8 What-if'}
src=f'digraph I {{ graph [{F}, splines=line, outputorder=edgesfirst]; node [{F}]; edge [{F}, fontsize=12, penwidth=1.6, color="#4a6fa5", fontcolor="#ffffff"];\n'
for k,(x,y) in P.items():
    fill,col=('#fff4dc','#c9a227') if k=='m8' else ('#eef2fb','#4a6fa5')
    src+=f'{k} [label=<<b>{T[k]}</b>>, pos="{x*72},{y*72}!", shape=box, style="rounded,filled", fillcolor="{fill}", color="{col}", penwidth=1.8, fontsize=13, width=2.1, height=0.62];\n'
# edges: (a,b,num,kind,both)
E=[('m6','m2','1'),('m6','m3','2'),('m6','m4','3'),('m6','m5','4'),('m2','m3','5'),('m2','m4','6'),('m2','m5','7'),('m3','m5','8'),('m4','m5','9'),
   ('m1','m7','10'),('m7','m3','11'),('m7','m5','12')]
def lab(n,c): return f'label=<<table border="0" cellpadding="1" bgcolor="{c}" style="rounded"><tr><td><font color="#ffffff"><b>{n}</b></font></td></tr></table>>'
for a,b,n in E: src+=f'{a} -> {b} [{lab(n,"#4a6fa5")}];\n'
for a,b,n in [('m4','m2','A'),('m3','m4','B'),('m7','m4','C')]:
    src+=f'{a} -> {b} [{lab(n,"#2f7d32")}, color="#2f7d32", penwidth=2.6, arrowhead=empty];\n'
src+=f'm8 -> m7 [{lab("D","#2f7d32")}, color="#2f7d32", penwidth=2.6, style=dashed, arrowhead=empty];\n'
# back directions drawn as separate offset? use dir=both on pairs where reverse exists
src=src.replace('m2 -> m3 [','m2 -> m3 [dir=both, ').replace('m3 -> m5 [','m3 -> m5 [dir=both, ').replace('m4 -> m5 [','m4 -> m5 [dir=both, ')
src+='}'
run_dot(src,O,dpi=160,engine='neato',args=['-n2']); print('ok')
