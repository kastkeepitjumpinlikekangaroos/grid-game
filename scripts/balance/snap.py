"""snap.py <candidate built from the levers> <out levers.json> <threshold> <run>...: drop the levers
that do next to nothing. One whose move is worth under the threshold (kills less deaths a match)
to every one of its throwers goes back to what the attack was: a point on an ability is churn. Not
by the size of the move: a point on a 22-damage primary is worth more than one a match."""
import json, sys, collections, math
from lib import *
import solve as sv
name = sys.argv[1]; th = float(sys.argv[3])
c0, p0, t0 = roster(S + '/roster0.tsv'); c1, p1, t1 = roster(f'{S}/cand/{name}/roster.tsv')
l = json.load(open(f'{S}/cand/{name}/levers.json'))
pooled = {}
for run in sys.argv[4:]:
    for reg in 'AB':
        for c, st in per_char(load(f'{S}/runs/{run}_{reg}.csv')).items():
            pooled.setdefault(c, dict(how=collections.Counter()))['how'].update(st['how'])
W = sv.shares(c0, p0, t0, pooled)
worth = collections.defaultdict(float)
for c, w in W.items():
    s = 28.5 if c0[c]['role'] == 'Ranged' else 25.0
    for k, v in w.items():
        if k.startswith('trap'): continue
        d = abs(math.log(sv.eff(p1[int(k)]) / sv.eff(p0[int(k)])))
        worth[k] = max(worth[k], s * v * d)
n = 0
for k in list(l.get('proj', {})):
    if worth[k] < th: del l['proj'][k]; n += 1
json.dump(l, open(sys.argv[2], 'w'), indent=1, sort_keys=True)
print(n, 'levers dropped;', len(l['proj']), 'attack levers left')
