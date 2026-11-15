"""solve.py <out levers.json> <round>[:weight] [<round>...]   (the last round is the current roster)

The next step of a balance pass. A round is a candidate's name: cand/<name>/roster.tsv and
levers.json, runs/<name>_A.csv (the bots as they are) and runs/<name>_B.csv (the way a player
plays). Every round's scores are carried to the current roster by the model (what a character's
score gains per log of its damage, weighted by each attack's share of its kills, and per log of
its health), pooled by how many matches each played and its weight, and a step is solved from the
current levers toward the targets by ridge least squares inside the roster's rules (no hit over
55, the burst fighters' primaries at 49 or more, health inside its role's band).

env: DAMP (0.7: how much of the solved step is taken), STEP (0.22: the most a lever moves, in log),
     HPSTEP (10), LAM (30: the pull on damage levers), MU (400: on health; 60 once two who share an
     attack need telling apart), TM and TS (the melee characters' and the skirmishers' targets),
     GAPMIN (leave alone whoever is within this of its target), NOHP=1,
     S_DMG and S_HP (scale the measured slopes: calib.py measures them)
"""
import collections, json, math, os, sys
import numpy as np
from lib import *

S_DMG = float(os.environ.get('S_DMG', 24)); S_HP = float(os.environ.get('S_HP', 20))
DAMP = float(os.environ.get('DAMP', 0.7)); STEP = float(os.environ.get('STEP', 0.22))
HPSTEP = int(os.environ.get('HPSTEP', 10))
LAM = float(os.environ.get('LAM', 30)); MU = float(os.environ.get('MU', 400))
CAP = 55
BANDS = {'Ranged': (60, 80), 'Melee': (105, 150), 'Skirmisher': (105, 150)}
# the fighters who burst instead of holding: their primaries stay at 49 or more (MeleeKitTest)
BURST_FLOOR = {'Raptor': 49, 'Assassin': 49, 'Berserker': 49, 'Fenrir': 49, 'Shapeshifter': 49}
# left a little under the middle on purpose: what they are good at, bots don't do
SPECIAL = {'Ember': -2.0, 'Spaceman': -1.0}
# how much of a character's score is the bots' pace (A), the rest the way a player plays (B): the
# melee characters lean on the bots as they are, whose swings a player is a little worse at landing
WA = {'Melee': 2.0 / 3, 'Skirmisher': 0.5, 'Ranged': 0.5}
TARGET = {'Melee': float(os.environ.get('TM', 1.6)), 'Skirmisher': float(os.environ.get('TS', 0.3)), 'Ranged': 0.0}

def eff(p):
    """one number for how hard a projectile hits, for ratios between rosters"""
    xs = [p['damage']]
    if p['dist'] != '-':
        a, b = p['dist'].split('-'); xs = [(int(a) + int(b)) / 2]
    for k in ('aoehit', 'aoemax'):
        if p[k] != '-': xs.append(int(p[k]))
    if p['expl'] != '-':
        a, b = p['expl'].split('-'); xs.append((int(a) + int(b)) / 2)
    v = max(xs)
    if p['dots'] != '-':
        for d in p['dots'].split('+'): v += int(''.join(ch for ch in d if ch.isdigit()))
    return max(v, 0.5)

def maxhit(p):
    xs = [p['damage']]
    if p['dist'] != '-': xs.append(int(p['dist'].split('-')[1]))
    for k in ('aoehit', 'aoemax'):
        if p[k] != '-': xs.append(int(p[k]))
    if p['expl'] != '-': xs.append(int(p['expl'].split('-')[0]))
    return max(xs)

def trap_eff(t):
    v = t['damage']
    if t['expl'] != '-':
        a, b = t['expl'].split('-'); v += (int(a) + int(b)) / 2
    for d in t['fx'].split('+'):
        if d.startswith('Burn(') or d.startswith('Poison('): v += int(d[d.index('(') + 1:d.index(',')])
    return max(v, 0.5)

def lever_eff(ros, key):
    chars, projs, traps = ros
    if key.startswith('trap'): return trap_eff(traps[int(key[4:])])
    return eff(projs[int(key)])

def shares(chars, projs, traps, stats):
    """char -> {lever key: share of its kills}"""
    out = {}
    for c, ch in chars.items():
        slots = [ch['p'], ch['q'], ch['e']]
        keys = [s for s in slots if s != '-']
        names = {}
        for s in keys:
            names[traps[int(s[4:])]['name'] if s.startswith('trap') else projs[int(s)]['name']] = s
        w = {k: 0.0 for k in keys}
        how = stats[c]['how'] if c in stats else {}
        for name, n in how.items():
            if name in names: w[names[name]] += n; continue
            if name in ('a burn', 'poison'):
                tag = 'burn' if name == 'a burn' else 'poison'
                cands = [s for s in keys if (not s.startswith('trap') and tag in projs[int(s)]['dots']) or
                         (s.startswith('trap') and tag in traps[int(s[4:])]['fx'].lower())]
                if cands:
                    for s in cands: w[s] += n / len(cands)
                    continue
            if name == 'a trap':
                cands = [s for s in keys if s.startswith('trap')]
                if cands: w[cands[0]] += n; continue
            pre = [s for nm, s in names.items() if name.startswith(nm)]
            w[pre[0] if pre else ch['p']] += n
        tot = sum(w.values())
        out[c] = {k: (v / tot if tot > 0 else (1.0 if k == ch['p'] else 0.0)) for k, v in w.items()}
    return out

def rate(ch): return 9 / 75 if ch['role'] == 'Ranged' else 5 / 37

def main():
    out = sys.argv[1]
    rounds = []
    for a in sys.argv[2:]:
        name, _, wt = a.partition(':')
        ros = roster(f'{S}/cand/{name}/roster.tsv')
        A = per_char(load(f'{S}/runs/{name}_A.csv')); B = per_char(load(f'{S}/runs/{name}_B.csv'))
        rounds.append(dict(name=name, ros=ros, A=A, B=B, wt=float(wt) if wt else 1.0))
    cur = rounds[-1]
    chars, projs, traps = cur['ros']
    levers = json.load(open(f"{S}/cand/{cur['name']}/levers.json"))
    lp = {k: float(v) for k, v in levers.get('proj', {}).items()}
    lt = {k: float(v) for k, v in levers.get('trap', {}).items()}
    ids = sorted(chars)

    # kill shares: pooled over every round and both regimes
    pooled = {}
    for r in rounds:
        for D in (r['A'], r['B']):
            for c, st in D.items():
                p = pooled.setdefault(c, dict(how=collections.Counter()))
                p['how'].update(st['how'])
    W = shares(chars, projs, traps, pooled)
    keys = sorted({k for c in ids for k in W[c]})
    kidx = {k: i for i, k in enumerate(keys)}
    M = np.zeros((len(ids), len(keys)))
    for i, c in enumerate(ids):
        for k, v in W[c].items(): M[i, kidx[k]] = v
    rates = np.array([rate(chars[c]) for c in ids])
    # what a character's score gains per log of damage and of health, measured by its role
    # (calib.py on a roster with every lever moved at random)
    Sd = np.array([28.5 if chars[c]['role'] == 'Ranged' else 21.5 for c in ids]) * S_DMG / 24.0
    Sh = np.array([14.0 if chars[c]['role'] == 'Ranged' else 23.0 for c in ids]) * S_HP / 20.0

    # every round's blend, carried to the current roster
    num = np.zeros(len(ids)); den = np.zeros(len(ids))
    cd_now = levers.get('cd', {})
    for r in rounds:
        y = np.array([WA[chars[c]['role']] * r['A'][c]['kd'] + (1 - WA[chars[c]['role']]) * r['B'][c]['kd'] for c in ids])
        n = np.array([(r['A'][c]['n'] + r['B'][c]['n']) / 2 for c in ids])
        dl = np.array([math.log(lever_eff(cur['ros'], k) / lever_eff(r['ros'], k)) for k in keys])
        dh = np.array([math.log(chars[c]['hp'] / r['ros'][0][c]['hp']) for c in ids])
        pred = Sd * M.dot(dl) + Sh * dh
        pred -= (pred * rates).sum() / rates.sum()  # a match's scores sum to nothing, whatever moved
        yc = y + pred
        # a round played before one of a character's cooldowns moved says nothing of them now
        cd_r = json.load(open(f"{S}/cand/{r['name']}/levers.json")).get('cd', {})
        same = np.array([all(cd_r.get(f'{c}:{sl}') == cd_now.get(f'{c}:{sl}') for sl in 'qe') for c in ids])
        if r is cur: same[:] = True
        num += yc * n * r['wt'] * same; den += n * r['wt'] * same
    yhat = num / den

    # targets
    # the fighters' are given; the ranged characters' is whatever is left of a match's scores
    t = np.array([TARGET[chars[c]['role']] + SPECIAL.get(chars[c]['name'], 0.0) for c in ids])
    isr = np.array([chars[c]['role'] == 'Ranged' for c in ids])
    t[isr] += ((yhat - t) * rates).sum() / rates[isr].sum()
    g = t - yhat
    gmin = float(os.environ.get('GAPMIN', 0))
    full_g = g.copy()
    g = np.where(np.abs(g) < gmin, 0.0, g)  # the last steps leave alone whoever is within the noise

    # unknowns: x (per lever, log damage), h (per character, log health)
    nk = len(keys); nc = len(ids)
    lo = np.full(nk + nc, -STEP); hi = np.full(nk + nc, STEP)
    for k, j in kidx.items():
        if k.startswith('trap'):
            tr = traps[int(k[4:])]
            mh = max([tr['damage']] + ([int(tr['expl'].split('-')[0])] if tr['expl'] != '-' else []))
            if mh > 0: hi[j] = min(hi[j], math.log(CAP / mh))
            # the bear trap is pinned at 10 (TrapTest), and a trap that deals nothing has no lever
            if tr['name'] == 'Bear Trap' or trap_eff(tr) <= 0.5: lo[j] = hi[j] = 0
        else:
            p = projs[int(k)]
            mh = maxhit(p)
            if mh > 0: hi[j] = min(hi[j], max(0.0, math.log(CAP / mh)))
            # a blast whose centre is at the cap may still rise at its edge, to three quarters of it
            if p['expl'] != '-':
                c0_, e0_ = [int(v) for v in p['expl'].split('-')]
                if c0_ >= CAP and 0 < e0_ < round(0.75 * CAP):
                    hi[j] = max(hi[j], min(STEP, math.log(round(0.75 * CAP) / e0_)))
            if eff(p) <= 0.5: lo[j] = hi[j] = 0
            # nothing under a third of what it was, or over three times
    for i, c in enumerate(ids):
        ch = chars[c]
        if ch['name'] in BURST_FLOOR:
            j = kidx[ch['p']]
            lo[j] = max(lo[j], min(0.0, math.log(BURST_FLOOR[ch['name']] / projs[int(ch['p'])]['damage'])))
        b = BANDS[ch['role']]
        lo[nk + i] = max(math.log(max(b[0], ch['hp'] - HPSTEP) / ch['hp']), -1)
        hi[nk + i] = math.log(min(b[1], ch['hp'] + HPSTEP) / ch['hp'])
        if os.environ.get('NOHP'): lo[nk + i] = hi[nk + i] = 0
    Afull = np.hstack([Sd[:, None] * M, np.diag(Sh)])
    reg = np.concatenate([np.full(nk, LAM), np.full(nc, MU)])
    fixed = {}
    for _ in range(60):
        free = [j for j in range(nk + nc) if j not in fixed]
        rhs = g - Afull[:, list(fixed)].dot(np.array([fixed[j] for j in fixed])) if fixed else g
        Af = Afull[:, free]
        sol = np.linalg.solve(Af.T.dot(Af) + np.diag(reg[free]), Af.T.dot(rhs))
        bad = [(j, v) for j, v in zip(free, sol) if v < lo[j] - 1e-9 or v > hi[j] + 1e-9]
        if not bad: break
        for j, v in bad: fixed[j] = min(max(v, lo[j]), hi[j])
    z = np.zeros(nk + nc)
    for j, v in zip(free, sol): z[j] = v
    for j, v in fixed.items(): z[j] = v
    z *= DAMP
    z = np.minimum(np.maximum(z, lo), hi)

    # the new levers
    new = dict(proj=dict(lp), trap=dict(lt), hp=dict(levers.get('hp', {})), cd=dict(levers.get('cd', {})))
    for k, j in kidx.items():
        if abs(z[j]) < 1e-6: continue
        if k.startswith('trap'):
            nm = traps[int(k[4:])]['name']; new['trap'][nm] = new['trap'].get(nm, 1.0) * math.exp(z[j])
        else:
            new['proj'][k] = new['proj'].get(k, 1.0) * math.exp(z[j])
    for i, c in enumerate(ids):
        want = chars[c]['hp'] * math.exp(z[nk + i])
        hp = int(round(want / 5.0)) * 5
        b = BANDS[chars[c]['role']]
        hp = min(max(hp, b[0]), b[1])
        if hp != chars[c]['hp']: new['hp'][str(c)] = hp
    json.dump(new, open(out, 'w'), indent=1, sort_keys=True)

    pred = Afull.dot(z)
    resid = g - pred
    print(f"rounds {[r['name'] for r in rounds]}  gap: sd {g.std():.2f} max {abs(g).max():.2f}; after this step (model) sd {resid.std():.2f}")
    for role in ('Melee', 'Skirmisher', 'Ranged'):
        m = np.array([chars[c]['role'] == role for c in ids])
        print(f"  {role:10s} pooled {np.average(yhat[m]):+.2f} target {np.average(t[m]):+.2f}  spread sd {yhat[m].std():.2f}")
    g = full_g
    order = np.argsort(g)
    print("furthest above target:", ', '.join(f"{chars[ids[i]]['name']} {-g[i]:+.1f}" for i in order[:14]))
    print("furthest below target:", ', '.join(f"{chars[ids[i]]['name']} {-g[i]:+.1f}" for i in order[::-1][:14]))
    nhp = sum(1 for c in ids if str(c) in new['hp'] and new['hp'][str(c)] != chars[c]['hp'])
    print(f"levers moved: {sum(1 for j in range(nk) if abs(z[j]) > 1e-6)} damage, {nhp} health")
    json.dump(dict(ids=ids, yhat=yhat.tolist(), target=t.tolist(), gap=g.tolist()), open(out.replace('.json', '.state.json'), 'w'))

if __name__ == "__main__": main()
