"""changes.py <candidate>: every number the candidate moves from the roster setup.sh started from."""
import sys
from lib import *
c0,p0,t0=roster(S+'/roster0.tsv'); c1,p1,t1=roster(f'{S}/cand/{sys.argv[1]}/roster.tsv')
def nums(p): return '/'.join(x for x in [str(p['damage']) if p['damage'] else '', p['dist'] if p['dist']!='-' else '', ('charged '+p['charge'].replace('.0','')) if p['charge']!='-' else '', 'splash '+p['aoehit'] if p['aoehit']!='-' else '', 'splash '+p['aoemax'] if p['aoemax']!='-' and p['aoemax']!=p['aoehit'] else '', 'blast '+p['expl'] if p['expl'] not in ('-','0-0') else '', p['dots'] if p['dots']!='-' else ''] if x)
users={}
for c in c0.values():
    for s in ('p','q','e'):
        if c[s]!='-': users.setdefault(c[s],[]).append(c['name'])
pc=[k for k in p0 if nums(p0[k])!=nums(p1[k])]
print(len(pc),'attacks changed of',len(p0))
import math
rows=[]
for k in pc:
    a,b=p0[k],p1[k]
    rows.append((b['name'],nums(a),nums(b),', '.join(users.get(str(k),[]))))
for r in sorted(rows): print(f"  {r[0]:22s} {r[1]:28s} -> {r[2]:28s} {r[3]}")
hp=[(c0[c]['name'],c0[c]['role'],c0[c]['hp'],c1[c]['hp']) for c in c0 if c0[c]['hp']!=c1[c]['hp']]
print(len(hp),'health changes:'); 
for r in sorted(hp,key=lambda r:(r[1],r[3]-r[2])): print(f"  {r[0]:14s} {r[1]:10s} {r[2]} -> {r[3]}")
cd=[(c0[c]['name'],s,c0[c][s+'name'],c0[c][s+'cd'],c1[c][s+'cd']) for c in c0 for s in 'qe' if c0[c][s+'cd']!=c1[c][s+'cd']]
print('cooldowns:',cd)
print('traps:',[(t0[k]['name'],t0[k]['damage'],t0[k]['expl'],t0[k]['fx'],'->',t1[k]['damage'],t1[k]['expl'],t1[k]['fx']) for k in t0 if t0[k]!=t1[k]])
