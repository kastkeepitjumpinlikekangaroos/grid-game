"""compare.py <candidate> <run> [<candidate> <run> ...]: the roles' means, the spread within each and
the most-winning characters of each run, for a pass's before and after."""
import sys, math
from lib import *
def stats(cand, run):
    chars, projs, traps = roster(f'{S}/cand/{cand}/roster.tsv')
    A = per_char(load(f'{S}/runs/{run}_A.csv')); B = per_char(load(f'{S}/runs/{run}_B.csv'))
    out = {}
    for role in ('Melee', 'Skirmisher', 'Ranged'):
        ids = [c for c in chars if chars[c]['role'] == role]
        wa = 2 / 3 if role == 'Melee' else 0.5
        bl = [wa * A[c]['kd'] + (1 - wa) * B[c]['kd'] for c in ids]
        m = sum(bl) / len(bl); sd = math.sqrt(sum((x - m) ** 2 for x in bl) / (len(bl) - 1))
        se = math.sqrt(sum((wa * A[c]['se']) ** 2 + ((1 - wa) * B[c]['se']) ** 2 for c in ids) / len(ids))
        def mean(D, k): return sum(D[c][k] for c in ids) / len(ids)
        sdA = math.sqrt(sum((A[c]['kd'] - mean(A, 'kd')) ** 2 for c in ids) / (len(ids) - 1))
        sdB = math.sqrt(sum((B[c]['kd'] - mean(B, 'kd')) ** 2 for c in ids) / (len(ids) - 1))
        topA = max(ids, key=lambda c: A[c]['win']); topB = max(ids, key=lambda c: B[c]['win'])
        lo = min(ids, key=lambda c: wa * A[c]['kd'] + (1 - wa) * B[c]['kd']); hi = max(ids, key=lambda c: wa * A[c]['kd'] + (1 - wa) * B[c]['kd'])
        f = lambda c: wa * A[c]['kd'] + (1 - wa) * B[c]['kd']
        out[role] = dict(A=mean(A, 'kd'), B=mean(B, 'kd'), bl=m, sd=sd, se=se, sdA=sdA, sdB=sdB, winA=100 * mean(A, 'win'), winB=100 * mean(B, 'win'),
                         topA=f"{chars[topA]['name']} {100 * A[topA]['win']:.0f}%", topB=f"{chars[topB]['name']} {100 * B[topB]['win']:.0f}%",
                         lo=f"{chars[lo]['name']} {f(lo):+.1f}", hi=f"{chars[hi]['name']} {f(hi):+.1f}",
                         nowinA=sum(1 for c in ids if A[c]['win'] < 0.02), nowinB=sum(1 for c in ids if B[c]['win'] < 0.02))
    return out
args = sys.argv[1:]
for i in range(0, len(args), 2):
    st = stats(args[i], args[i + 1])
    print(f"== {args[i]} / {args[i+1]}")
    for role, s in st.items():
        print(f"  {role:10s} A {s['A']:+.2f} B {s['B']:+.2f} mix {s['bl']:+.2f} | spread mix {s['sd']:.2f} (luck {s['se']:.2f}) A {s['sdA']:.2f} B {s['sdB']:.2f} | wins A {s['winA']:.1f}% B {s['winB']:.1f}% | most wins A {s['topA']}, B {s['topB']} | under 2% of wins: A {s['nowinA']} B {s['nowinB']} | mix from {s['lo']} to {s['hi']}")
