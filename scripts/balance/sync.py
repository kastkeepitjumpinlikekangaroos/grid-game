"""sync.py <cand name>: each AbilityDef's `damage` (what the ability is listed as dealing) moved with
its projectile's, where it matched one of the projectile's numbers before."""
import re, sys
from lib import *
from apply import PTYPE, close_of, statements
name = sys.argv[1]
c0, p0, t0 = roster(S + '/roster0.tsv'); c1, p1, t1 = roster(f'{S}/cand/{name}/roster.tsv')
def nums(p):
    out = [('damage', p['damage'])]
    if p['dist'] != '-': out += [('d0', int(p['dist'].split('-')[0])), ('d1', int(p['dist'].split('-')[1]))]
    for k in ('aoemax', 'aoehit'):
        if p[k] != '-': out.append((k, int(p[k])))
    if p['expl'] != '-': out += [('x0', int(p['expl'].split('-')[0]))]
    return out
import os
n = 0
for f in sorted(os.listdir(f'{S}/cand/{name}/roster')):
    path = f'{S}/cand/{name}/roster/{f}'
    src = open(path).read(); out = []; last = 0
    for (a, b, text) in statements(src, r'AbilityDef\('):
        m = re.search(r'\bdamage = (\d+), projectileType = ProjectileType\.(\w+)', text)
        if m and PTYPE[m.group(2)] in p0:
            t = PTYPE[m.group(2)]; old = dict(nums(p0[t])); new = dict(nums(p1[t])); d = int(m.group(1))
            hit = [k for k, v in nums(p0[t]) if v == d and v > 0]
            if hit and new[hit[0]] != d:
                text = text[:m.start(1)] + str(new[hit[0]]) + text[m.end(1):]; n += 1
        out.append(src[last:a]); out.append(text); last = b
    out.append(src[last:])
    open(path, 'w').write(''.join(out))
print(n, 'ability damage fields moved')
