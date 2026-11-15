"""apply.py <levers.json> <outdir>: the original roster sources with a set of levers applied.

levers.json: {"proj": {"<type id>": multiplier}, "hp": {"<char id>": health},
              "cd": {"<char id>:q|e": ms}, "trap": {"<trap name>": multiplier}}
Every damage number of a projectile type moves by its multiplier from the ORIGINAL source's number
(so rounding never compounds), kept to the roster's rules: no uncharged hit over 55, a blast's edge
at most three quarters of its centre.
"""
import json, os, re, sys
from lib import S, roster

CAP = 55
ORIG = S + '/orig'

def ids(path, pat):
    out = {}
    for m in re.finditer(pat, open(path).read()):
        out[m.group(1)] = int(m.group(2))
    return out

PTYPE = ids(ORIG + '/ProjectileType.scala', r'val (\w+): Byte = (-?\d+)')
CID = ids(ORIG + '/CharacterId.scala', r'val (\w+): CharacterId = CharacterId\((\d+)')

def close_of(s, i):
    """index of the paren closing the one at s[i]"""
    depth = 0; j = i; instr = False
    while j < len(s):
        ch = s[j]
        if instr:
            if ch == '\\': j += 1
            elif ch == '"': instr = False
        elif ch == '"': instr = True
        elif ch == '(': depth += 1
        elif ch == ')':
            depth -= 1
            if depth == 0: return j
        j += 1
    raise ValueError('unclosed')

def depth_at(text, pos):
    depth = 0; instr = False; j = 0
    while j < pos:
        ch = text[j]
        if instr:
            if ch == '\\': j += 1
            elif ch == '"': instr = False
        elif ch == '"': instr = True
        elif ch == '(': depth += 1
        elif ch == ')': depth -= 1
        j += 1
    return depth

def scale(n, m, cap=True):
    if n <= 0: return n
    v = max(1, int(round(n * m)))
    return min(CAP, v) if cap else v

def patch_projectile(text, m, inherited_damage=None):
    """text: 'ProjectileDef( ... )' or '.copy( ... )', scaled by m"""
    # nested configs first
    def aoe(mm): return f"AoESplashConfig({mm.group(1)}, {scale(int(mm.group(2)), m)}"
    text = re.sub(r'AoESplashConfig\(([\d.]+f?), (\d+)', aoe, text)
    def expl(mm):
        c = scale(int(mm.group(1)), m); e = scale(int(mm.group(2)), m)
        e = min(e, int(round(c * 0.75) if c * 0.75 % 1 != 0.5 else int(c * 0.75 + 0.5)))
        return f"ExplosionConfig({c}, {e},"
    text = re.sub(r'ExplosionConfig\((\d+), (\d+),', expl, text)
    def dds(mm): return f"DistanceDamageScaling({scale(int(mm.group(1)), m)}, {scale(int(mm.group(2)), m)},"
    text = re.sub(r'DistanceDamageScaling\((\d+), (\d+),', dds, text)
    def charge(mm): return f"chargeDamageScaling = Some(ChargeScaling({scale(int(float(mm.group(1))), m, False)}f, {scale(int(float(mm.group(2))), m, False)}f))"
    text = re.sub(r'chargeDamageScaling = Some\(ChargeScaling\(([\d.]+)f, ([\d.]+)f\)\)', charge, text)
    def dot(mm): return f"{mm.group(1)}({scale(int(mm.group(2)), m, False)},"
    text = re.sub(r'\b(Burn|Poison)\((\d+),', dot, text)
    # the hit itself: the `damage =` among the statement's own arguments
    done = False
    out = []; last = 0
    for mm in re.finditer(r'\bdamage = (\d+)', text):
        if depth_at(text, mm.start()) == 1:
            out.append(text[last:mm.start()]); out.append(f"damage = {scale(int(mm.group(1)), m)}"); last = mm.end(); done = True
    out.append(text[last:]); text = ''.join(out)
    if not done and inherited_damage is not None and inherited_damage > 0 and scale(inherited_damage, m) != inherited_damage:
        # a variant that took its base's damage, given one of its own
        mm = re.search(r'name = "[^"]*"', text)
        text = text[:mm.end()] + f", damage = {scale(inherited_damage, m)}" + text[mm.end():]
    return text

def statements(src, opener):
    """(start, end, text) of every `opener(...)` call"""
    out = []
    for mm in re.finditer(opener, src):
        i = mm.end() - 1
        j = close_of(src, i)
        out.append((mm.start(), j + 1, src[mm.start():j + 1]))
    return out

def rewrite(src, opener, fn):
    out = []; last = 0
    for (a, b, text) in statements(src, opener):
        if a < last: continue
        out.append(src[last:a]); out.append(fn(text)); last = b
    out.append(src[last:])
    return ''.join(out)

def main():
    levers = json.load(open(sys.argv[1])); outdir = sys.argv[2]
    proj = {int(k): float(v) for k, v in levers.get('proj', {}).items()}
    hp = {int(k): int(v) for k, v in levers.get('hp', {}).items()}
    cd = levers.get('cd', {})
    trap = levers.get('trap', {})
    chars0, projs0, traps0 = roster(S + '/roster0.tsv')
    os.makedirs(outdir + '/roster', exist_ok=True)
    seen = set()

    def on_proj(text):
        mm = re.search(r'\bid = ProjectileType\.(\w+)', text)
        if not mm or depth_at(text, mm.start()) != 1: return text
        t = PTYPE[mm.group(1)]
        seen.add(t)
        if t not in proj or abs(proj[t] - 1.0) < 1e-9: return text
        inherited = projs0[t]['damage'] if (t in projs0 and text.startswith('.copy')) else None
        return patch_projectile(text, proj[t], inherited)

    def on_char(text):
        mm = re.search(r'\bid = CharacterId\.(\w+)', text)
        c = CID[mm.group(1)]
        if c in hp:
            text = re.sub(r'maxHealth = \d+', f'maxHealth = {hp[c]}', text)
        for slot in ('q', 'e'):
            key = f'{c}:{slot}'
            if key in cd:
                a = text.index(f'{slot}Ability = AbilityDef(')
                b = close_of(text, text.index('(', a))
                sub = re.sub(r'cooldownMs = \d+', f'cooldownMs = {int(cd[key])}', text[a:b + 1])
                text = text[:a] + sub + text[b + 1:]
        return text

    files = ['roster/' + f for f in sorted(os.listdir(ORIG + '/roster'))] + ['ProjectileDef.scala', 'Trap.scala']
    for f in files:
        src = open(ORIG + '/' + f).read()
        src = rewrite(src, r'(?<== )ProjectileDef\(', on_proj)
        src = rewrite(src, r'\.copy\(', on_proj)
        if f.startswith('roster/'):
            src = rewrite(src, r'(?<== )CharacterDef\(', on_char)
        if f == 'Trap.scala' and trap:
            def on_trap(text):
                mm = re.search(r'name = "([^"]*)"', text)
                if not mm or mm.group(1) not in trap: return text
                m = float(trap[mm.group(1)])
                text = patch_projectile(text, m)
                return text
            src = rewrite(src, r'(?<== )TrapDef\(', on_trap)
        open(outdir + '/' + f, 'w').write(src)
    missing = [t for t in proj if t not in seen and abs(proj[t] - 1) > 1e-9]
    if missing: print('levers for types with no definition found:', missing)

if __name__ == "__main__": main()
