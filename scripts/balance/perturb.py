"""perturb.py <out levers.json> [seed=5]: every attack's damage moved at random by up to a fifth and
every character's health by up to 10, for calib.py to measure what each is worth."""
import json, math, random, sys
from lib import *
chars, projs, traps = roster(S + '/roster0.tsv')
random.seed(int(sys.argv[2]) if len(sys.argv) > 2 else 5)
bands = {'Ranged': (60, 80), 'Melee': (105, 150), 'Skirmisher': (105, 150)}
lev = dict(proj={str(t): math.exp(random.uniform(-0.2, 0.2)) for t in projs}, hp={})
for c, ch in chars.items():
    lo, hi = bands[ch['role']]
    lev['hp'][str(c)] = min(max(ch['hp'] + random.choice([-10, -5, 0, 5, 10]), lo), hi)
json.dump(lev, open(sys.argv[1], 'w'))
