"""web.py <roster.tsv> <index.html in> <index.html out>: the damage numbers on the website's
character cards, written from the roster (what each line says besides its numbers is left as it is)."""
import re, sys
from lib import *
chars, projs, traps = roster(sys.argv[1])
byname = {c['name']: c for c in chars.values()}
html = open(sys.argv[2]).read()

def burn_dealt(total, ms, tick):
    each = total // (ms // tick) if ms // tick > 0 else total
    bites = 0; last = 0; t = 200
    while t < ms:
        if t >= last + tick: bites += 1; last = t
        t += 200
    return bites * each

def dots(fx):
    out = {}
    for m in re.finditer(r'(Burn|Poison)\((\d+),(\d+),(\d+)\)', fx):
        total, ms, tick = int(m.group(2)), int(m.group(3)), int(m.group(4))
        out[m.group(1).lower()] = (burn_dealt(total, ms, tick) if m.group(1) == 'Burn' else total, ms)
    return out

def secs(ms): return ('%g' % (ms / 1000.0))

def rewrite(text, slot, c):
    key = c[slot]
    if key == '-': return text
    if key.startswith('trap'):
        t = traps[int(key[4:])]
        if t['damage'] > 0: text = re.sub(r'\b\d+ dmg', f"{t['damage']} dmg", text)
        if t['expl'] != '-':
            a, b = t['expl'].split('-'); text = re.sub(r'explodes \d+/\d+', f'explodes {a}/{b}', text)
            text = re.sub(r'^\d+ dmg', f'{a} dmg', text)
        d = dots(t['fx'])
        for kind, word in (('burn', r'(burn|burns|alight for)'), ('poison', r'(poison|poisons)')):
            if kind in d: text = re.sub(word + r' \d+ over [\d.]+s', lambda m: f"{m.group(1)} {d[kind][0]} over {secs(d[kind][1])}s", text)
        return text
    p = projs[int(key)]
    aoe = p['aoehit'] if p['aoehit'] != '-' else p['aoemax']
    aoemax = p['aoemax'] if p['aoemax'] != '-' else p['aoehit']
    centre = p['expl'].split('-')[0] if p['expl'] != '-' else None
    if p['dist'] != '-':
        a, b = p['dist'].split('-'); head = f'{a}&ndash;{b}'
    elif p['charge'] != '-':
        a, b = [int(float(v)) for v in p['charge'].split('-')]; head = f'{a}&ndash;{b}'
    elif p['damage'] > 0: head = str(p['damage'])
    elif aoemax != '-' and int(aoemax) > 0: head = aoemax
    elif centre and int(centre) > 0: head = centre
    else: head = None
    if p['charge'] != '-' and re.match(r'^\d+ dmg, \d+&ndash;\d+ charged', text):
        # "39 dmg, 25-82 charged": the blow as it is, then what a charge makes of it
        text = re.sub(r'^\d+ dmg, \d+&ndash;\d+ charged', f"{p['damage']} dmg, {head} charged", text)
    elif head: text = re.sub(r'^\d+(&ndash;\d+)? dmg', f'{head} dmg', text)
    if aoemax != '-':
        text = re.sub(r'AoE \d+ dmg', f'AoE {aoemax} dmg', text)
    if aoe != '-':
        text = re.sub(r'AoE splash \d+', f'AoE splash {aoe}', text)
        text = re.sub(r'AoE \d+(?! dmg)\b', f'AoE {aoe}', text)
    if p['expl'] != '-' and int(centre) > 0:
        a, b = p['expl'].split('-'); text = re.sub(r'explodes \d+/\d+', f'explodes {a}/{b}', text)
    d = dots(p['fx'])
    for kind, word in (('burn', r'(burn|burns)'), ('poison', r'(poison|poisons)')):
        if kind in d: text = re.sub(word + r' \d+ over [\d.]+s', lambda m: f"{m.group(1)} {d[kind][0]} over {secs(d[kind][1])}s", text)
    return text

out = []; last = 0; cur = None; changed = 0
for m in re.finditer(r'<h3>([^<]+)</h3>|(<span class="ability-key">)(LMB|Q|E)(</span> )([^<]*?)( &mdash; )([^<]*)', html):
    if m.group(1):
        cur = byname.get(m.group(1).replace('&#39;', "'")); continue
    if cur is None: continue
    slot = {'LMB': 'p', 'Q': 'q', 'E': 'e'}[m.group(3)]
    new = rewrite(m.group(7), slot, cur)
    if new != m.group(7):
        changed += 1
        if len(sys.argv) > 4: print(f"{cur['name']} {m.group(3)}: {m.group(7)}  ->  {new}")
    out.append(html[last:m.start(7)]); out.append(new); last = m.end(7)
out.append(html[last:])
open(sys.argv[3], 'w').write(''.join(out))
print(changed, 'lines changed')
