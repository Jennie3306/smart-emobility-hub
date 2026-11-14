"""Render every diagram: python render_all.py   (or one module: python render_all.py m3)"""
import os, subprocess, sys
HERE = os.path.dirname(os.path.abspath(__file__))
JOBS = {f'm{i}': [f'm{i}/m{i}.py'] for i in range(1, 9)}
JOBS['m5'] += ['m5/render_m5_class_diagram.py', 'm5/render_m5_sequence.py']
JOBS['shared'] = ['shared/erd.py', 'shared/interact.py', 'shared/deploy.py', 'shared/nav.py']
for group in (sys.argv[1:] or JOBS):
    for script in JOBS[group]:
        print(f'{group}: {script}', flush=True)
        subprocess.run([sys.executable, os.path.basename(script)], cwd=os.path.join(HERE, os.path.dirname(script)), check=True)
