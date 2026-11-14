import html, subprocess, os
# python/ holds this file; the type folders (png/, graphviz_dot/, mermaid_mmd/) sit next to it.
HERE=os.path.dirname(os.path.abspath(__file__))
ROOT=os.path.dirname(HERE)
E=html.escape

def png_dir(module):
    """Folder for one module's images: <root>/png/<module>/ (ends with a separator, so O+'name.png' works)."""
    d=os.path.join(ROOT,'png',module); os.makedirs(d,exist_ok=True); return d+os.sep

def _source(out, folder, ext):
    """Where the source of an image goes: png/m3/m3_state.png -> graphviz_dot/m3/m3_state.dot."""
    module=os.path.basename(os.path.dirname(os.path.abspath(out)))
    name=os.path.splitext(os.path.basename(out))[0]
    d=os.path.join(ROOT,folder,module); os.makedirs(d,exist_ok=True)
    return os.path.join(d,name+ext)

def run_dot(src, out, dpi=150, engine='dot', args=()):
    """Write the .dot into graphviz_dot/<module>/ and render it from that folder (use cases find stick.png via imagepath)."""
    p=_source(out,'graphviz_dot','.dot')
    open(p,'w',encoding='utf-8').write(src)
    subprocess.run([engine,*args,'-Tpng',f'-Gdpi={dpi}','-o',os.path.abspath(out),os.path.basename(p)],cwd=os.path.dirname(p),check=True)
def usecase(out, title, ucs, left=(), right=(), assoc=(), rels=(), amber=(), dpi=150):
    """ucs: [(id,label)], left/right: [(key,label,system)], assoc: [(actorKey, ucId)], rels: [(from,to,'include'|'extend')]"""
    L=['digraph G {','imagepath="../../python"; rankdir=LR; splines=true; nodesep=0.35; ranksep=1.3; fontname="DejaVu Sans";',
       'node [fontname="DejaVu Sans"]; edge [fontname="DejaVu Sans", fontsize=11, arrowsize=0.8];']
    def actor(k,lab,sysa):
        t=('<i>«system»</i><br/>' if sysa else '')+'<br/>'.join(f'<b>{E(x)}</b>' for x in lab.split('|'))
        L.append(f'"{k}" [shape=plain, label=<<table border="0" cellspacing="0" cellpadding="1"><tr><td fixedsize="true" width="40" height="56"><img src="stick.png" scale="true"/></td></tr><tr><td><font point-size="12">{t}</font></td></tr></table>>];')
    for k,lab,s in left: actor(k,lab,s)
    L.append(f'subgraph cluster_sys {{ label=<<b>{E(title)}</b>>; fontsize=14; style="rounded"; color="#1f4e79"; penwidth=2; bgcolor="#f9fbfd";')
    for i,lab in ucs:
        f,st=('#fff1c9','#d4b04a') if i in amber else ('#dae7f8','#6f8fc4')
        L.append(f'"{i}" [shape=ellipse, style=filled, fillcolor="{f}", color="{st}", penwidth=1.6, fontsize=11, label=<<b>{E(i)}</b><br/>{E(lab).replace("|","<br/>")}>];')
    L.append('}')
    for k,lab,s in right: actor(k,lab,s)
    for a,u in assoc:
        if any(a==k for k,_,_ in right): L.append(f'"{u}" -> "{a}" [dir=none, color="#444444"];')
        else: L.append(f'"{a}" -> "{u}" [dir=none, color="#444444"];')
    for f,t,kind in rels:
        col='#2e6fb0' if kind=='include' else '#c0571b'
        L.append(f'"{f}" -> "{t}" [style=dashed, color="{col}", fontcolor="{col}", label="«{kind}»", constraint=false];')
    L.append('}')
    run_dot('\n'.join(L), out, dpi)

def klass(name, stereo=None, attrs=(), ops=(), fill="#eef2fb", border="#4a6fa5", kind=None):
    head=(f'<font point-size="10"><i>&laquo;{E(stereo)}&raquo;</i></font><br/>' if stereo else '')+f'<b>{E(name)}</b>'
    rows=[f'<tr><td bgcolor="{fill}" align="center">{head}</td></tr>']
    if attrs is not None:
        a='<br align="left"/>'.join(E(x) for x in attrs)
        rows.append(f'<tr><td align="left" balign="left">{a+"<br align=\"left\"/>" if attrs else " "}</td></tr>')
    if ops is not None:
        o='<br align="left"/>'.join(E(x) for x in ops)
        rows.append(f'<tr><td align="left" balign="left">{o+"<br align=\"left\"/>" if ops else " "}</td></tr>')
    return f'"{name}" [label=<<table border="1" cellborder="1" cellspacing="0" cellpadding="4" color="{border}">{"".join(rows)}</table>>];'

ENUM=dict(fill="#fde9d9", border="#c0504d")
IFACE=dict(fill="#e6f4ea", border="#3a7d44")
EXT=dict(fill="#f2f2f2", border="#888888")
SCHED=dict(fill="#fff4dc", border="#c9a227")

def classdiagram(out, nodes, edges, clusters=None, rankdir='TB', dpi=150, extra=''):
    """edges: (a,b,kind,label) kind: assoc|dep|impl|inherit|comp|agg"""
    L=['digraph G {',f'rankdir={rankdir}; splines=ortho; nodesep=0.5; ranksep=0.7; {extra}',
       'node [shape=plain, fontname="DejaVu Sans", fontsize=10]; edge [fontname="DejaVu Sans", fontsize=9, color="#555555"];']
    used=set()
    if clusters:
        for cname,members in clusters:
            L.append(f'subgraph "cluster_{cname}" {{ label="{cname}"; fontsize=11; style=dashed; color="#999999";')
            for m in members: L.append(nodes[m]); used.add(m)
            L.append('}')
    for k,v in nodes.items():
        if k not in used: L.append(v)
    sty={'assoc':'arrowhead=vee','dep':'style=dashed, arrowhead=vee','impl':'style=dashed, arrowhead=onormal',
         'inherit':'arrowhead=onormal','comp':'arrowtail=diamond, dir=back','agg':'arrowtail=odiamond, dir=back',
         'line':'arrowhead=none'}
    for e in edges:
        a,b,kind=e[0],e[1],e[2]; lab=e[3] if len(e)>3 else ''
        extra2=e[4] if len(e)>4 else ''
        L.append(f'"{a}" -> "{b}" [{sty[kind]}, xlabel="{lab}" {extra2}];')
    L.append('}')
    run_dot('\n'.join(L), out, dpi)

def statemachine(out, states, trans, initial=None, final=(), dpi=150, rankdir='LR', extra='', notes=()):
    L=['digraph G {',f'rankdir={rankdir}; nodesep=0.6; ranksep=0.9; fontname="DejaVu Sans Bold";',
       'node [shape=box, style="rounded,filled", fillcolor="#dae7f8", color="#4a6fa5", fontname="DejaVu Sans Bold", fontsize=15, margin="0.2,0.1"];',
       'edge [fontname="DejaVu Sans", fontsize=13, color="#444444"];', extra]
    if initial:
        L.append('__init [shape=circle, style=filled, fillcolor=black, label="", width=0.22];')
        L.append(f'__init -> "{initial}";')
    for s in states:
        name, kw = (s, '') if isinstance(s,str) else s
        L.append(f'"{name}" [{kw}];')
    for i,f in enumerate(final):
        L.append(f'__fin{i} [shape=doublecircle, style=filled, fillcolor=black, label="", width=0.15];')
        L.append(f'"{f}" -> __fin{i};')
    for t in trans:
        a,b,lab=t[0],t[1],t[2]; kw=t[3] if len(t)>3 else ''
        L.append(f'"{a}" -> "{b}" [label="{lab}" {kw}];')
    for n in notes: L.append(n)
    L.append('}')
    run_dot('\n'.join(L), out, dpi)

def mermaid(out, src, width=1600, config=None):
    """Write the .mmd into mermaid_mmd/<module>/ and render it with the Mermaid CLI."""
    p=_source(out,'mermaid_mmd','.mmd'); open(p,'w',encoding='utf-8').write(src)
    cfg=config or os.path.join(HERE,'mcfg.json')
    subprocess.run(['mmdc','-p',os.path.join(HERE,'pp.json'),'-i',p,'-o',os.path.abspath(out),'-c',cfg,'-w',str(width),'-b','white','-s','2'],check=True,capture_output=True)

# Shared look of activity diagrams (graphviz), used by every module.
F='fontname="DejaVu Sans"'
ACT='shape=box, style="rounded,filled", fillcolor="#eef2fb", color="#4a6fa5", penwidth=1.4'
DEC='shape=diamond, style=filled, fillcolor="#fff1c9", color="#c9a227", fontsize=10, width=1.6, height=0.8'
ERR='shape=box, style="rounded,filled", fillcolor="#f9dcdc", color="#c0504d"'
OK='shape=box, style="rounded,filled", fillcolor="#e1f3e8", color="#3a7d44"'
def activity_header(title):
    return (f'digraph G {{ graph [rankdir=TB, {F}, nodesep=0.35, ranksep=0.35, label=<<b>{E(title)}</b>>, labelloc=t, fontsize=14]; '
            f'node [{F}, fontsize=11]; edge [{F}, fontsize=9.5, color="#555555"];\n'
            's [shape=circle, style=filled, fillcolor=black, label="", width=0.25];\n')
END='[shape=doublecircle, style=filled, fillcolor=black, label="", width=0.18]'
