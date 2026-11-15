"""show.py <solve.py's .state.json> <candidate> <run> [threshold=1.3]: who is still further than the
threshold from its target, with what its numbers were and are."""
import sys, json
from lib import *
st=json.load(open(sys.argv[1]))
c0,p0,t0=roster(S+'/roster0.tsv'); c3,p3,t3=roster(f'{S}/cand/{sys.argv[2]}/roster.tsv')
A=per_char(load(f'{S}/runs/{sys.argv[3]}_A.csv')); B=per_char(load(f'{S}/runs/{sys.argv[3]}_B.csv'))
th=float(sys.argv[4]) if len(sys.argv)>4 else 1.3
gap=dict(zip(st['ids'],st['gap']))
def nums(p): return '/'.join(x for x in [str(p['damage']) if p['damage'] else '', p['dist'] if p['dist']!='-' else '', 'aoe'+p['aoehit'] if p['aoehit']!='-' else '', 'aoe'+p['aoemax'] if p['aoemax']!='-' else '', 'x'+p['expl'] if p['expl']!='-' else '', p['dots'] if p['dots']!='-' else ''] if x)
def desc(c):
    ch0=c0[c]; ch1=c3[c]; out=[f"hp {ch0['hp']}->{ch1['hp']}"]
    for s in ('p','q','e'):
        t=ch0[s]
        if t=='-': out.append(f"{s}:{ch0.get(s+'cast','')}/{ch0.get(s+'cd','')}"); continue
        if t.startswith('trap'): out.append(f"{s}:trap"); continue
        a=p0[int(t)]; b=p3[int(t)]
        out.append(f"{s}:{a['name']} {nums(a)}->{nums(b)} cd{ch0.get(s+'cd','')}")
    return '  '.join(out)
for c,g in sorted(gap.items(), key=lambda kv: kv[1]):
    c=int(c)
    if abs(g)>th: print(f"{c0[c]['name']:14s} {-g:+.1f} ({sys.argv[3]} A {A[c]['kd']:+.1f} B {B[c]['kd']:+.1f})  {desc(c)}")
