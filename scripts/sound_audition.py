#!/usr/bin/env python3
"""Writes a page to LISTEN to the roster on: every character, a button for each of its three
attacks, playing what the game plays for it.

The gallery and the audit judge a sound by looking at it and measuring it; this is for the
judgement that counts, by ear, and for the one question a spectrogram cannot answer — does this
sound like THAT attack, in that character's hands? A row a character, as `AbilitySounds.scala`
resolves it; then what happens to a player, the blasts and the traps.

    python3 scripts/sound_audition.py /tmp/audition                 # -> /tmp/audition/index.html
    python3 scripts/sound_audition.py /tmp/audition --before HEAD   # with what each sounded like
                                                                    # at that commit beside it
    open /tmp/audition/index.html

`--before <rev>` copies that revision's sounds into the folder (git show) and resolves the table
as it stood there, in this form or the older `set`/`only` one, so every button gets a second,
"before" button: the way to hear a change to the set. `--variants <dir>` adds a section at the
top with a button for every .wav in that folder (and what `notes.json` there says of each, name
to a line of text): for a choice between ways of making one sound, which is a question only an
ear can answer. SND_DIR overrides the directory of the sounds played, to audition a build before
it replaces sounds/. Needs nothing but Python and git.
"""
import html
import json
import os
import re
import subprocess
import sys

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import sound_roster  # noqa: E402

TABLE = "src/main/scala/com/gridgame/client/audio/AbilitySounds.scala"
PLAIN = {"dash": "dash", "phase": "phase_shift", "barrier": "barrier_up", "blink": "teleport", "trap": "trap_place"}
OTHERS = [
    ("What happens to a player", ["spawn", "death", "hit_taken", "hit_dealt", "hit_other", "cooldown_reset", "barrier_block"]),
    ("Blasts", ["explosion", "blast_rocket", "blast_cannonball", "blast_bomb", "blast_fire", "blast_magma", "blast_toxic",
                "blast_acid", "blast_mud", "blast_cluster"]),
    ("Traps going off, and the plain casts", ["trap_snap", "trap_poison", "trap_ignite", "trap_place", "dash", "teleport",
                                               "phase_shift", "barrier_up"]),
]


def git_show(rev, path):
    return subprocess.run(["git", "-C", ROOT, "show", "%s:%s" % (rev, path)], capture_output=True).stdout


def rows_at(rev):
    """What each character heard at `rev`: {character id: (primary, q, e)} with None for a cast
    the table had no sound of its own for."""
    src = git_show(rev, TABLE).decode("utf-8", "replace")
    rows = {m.group(1): (m.group(2), m.group(3), m.group(4)) for m in re.finditer(
        r'hears\(CharacterId\.(\w+),\s*"([a-z0-9_]+)",\s*"([a-z0-9_]+)",\s*"([a-z0-9_]+)"\)', src)}
    if rows:
        return rows
    # the older form: a sound a projectile type, and a few (character, type) overrides
    by_type, only = {}, {}
    for m in re.finditer(r'\bset\("([a-z0-9_]+)",\s*([^)]*)\)', src):
        for pt in re.findall(r"ProjectileType\.([A-Z_0-9]+)", m.group(2)):
            by_type[pt] = m.group(1)
    for m in re.finditer(r'only\("([a-z0-9_]+)",\s*CharacterId\.(\w+),\s*ProjectileType\.([A-Z_0-9]+)\)', src):
        only[(m.group(2), m.group(3))] = m.group(1)
    roster = "".join(git_show(rev, "src/main/scala/com/gridgame/common/model/roster/%s.scala" % c).decode("utf-8", "replace")
                     for c in sound_roster.CATEGORIES)
    for _, body in re.findall(r"val (\w+): CharacterDef = CharacterDef\((.*?)\n  \)\n", roster, re.S):
        cid = re.search(r"id = CharacterId\.(\w+)", body).group(1)
        types = [re.search(r"primaryProjectileType = ProjectileType\.(\w+)", body).group(1)]
        for kind in ("qAbility", "eAbility"):
            t = re.search(r"projectileType = ProjectileType\.(\w+)", sound_roster._ability(body, kind))
            types.append(t.group(1) if t else None)
        rows[cid] = tuple((only.get((cid, t)) or by_type.get(t)) if t else None for t in types)
    return rows


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    out = args[0] if args else "out/audition"
    before = sys.argv[sys.argv.index("--before") + 1] if "--before" in sys.argv else None
    variants_dir = sys.argv[sys.argv.index("--variants") + 1] if "--variants" in sys.argv else None
    for given in (before, variants_dir):
        if given in args:
            args.remove(given)
    out = args[0] if args else "out/audition"
    os.makedirs(out, exist_ok=True)
    now_dir = os.path.abspath(os.environ.get("SND_DIR", os.path.join(ROOT, "sounds")))
    now_rows = sound_roster.load_rows()
    old_rows = rows_at(before) if before else {}
    wanted_old = set()

    def old_of(cid, slot, kind):
        row = old_rows.get(cid)
        if row is None:
            return None
        return row[slot] or PLAIN.get(kind)

    data = []
    for name, cid, abilities in sound_roster.load_characters():
        row = now_rows.get(cid, (None, None, None))
        cells = []
        for slot, label, kind in ((0, "Primary", ""), (1, abilities[0][0], abilities[0][1]), (2, abilities[1][0], abilities[1][1])):
            was = old_of(cid, slot, kind) if before else None
            if was:
                wanted_old.add(was)
            cells.append(dict(label=label, kind=kind, now=row[slot], was=was))
        data.append(dict(name=name, cells=cells))
    others = [dict(title=t, sounds=[s for s in names if os.path.isfile(os.path.join(now_dir, s + ".wav"))]) for t, names in OTHERS]
    if before:
        os.makedirs(os.path.join(out, "before"), exist_ok=True)
        for t, names in OTHERS:
            wanted_old.update(names)
        have = set()
        for s in sorted(wanted_old):
            blob = git_show(before, "sounds/%s.wav" % s)
            if blob:
                open(os.path.join(out, "before", s + ".wav"), "wb").write(blob)
                have.add(s)
        for d in data:
            for c in d["cells"]:
                if c["was"] not in have:
                    c["was"] = None
        for o in others:
            o["was"] = [s for s in o["sounds"] if s in have]

    variants = []
    if variants_dir:
        notes_path = os.path.join(variants_dir, "notes.json")
        notes = json.load(open(notes_path)) if os.path.isfile(notes_path) else {}
        os.makedirs(os.path.join(out, "variants"), exist_ok=True)
        for f in sorted(os.listdir(variants_dir)):
            if f.endswith(".wav"):
                open(os.path.join(out, "variants", f), "wb").write(open(os.path.join(variants_dir, f), "rb").read())
                variants.append(dict(file=f, note=notes.get(f[:-4], "")))
    page = PAGE.replace("__DATA__", json.dumps(dict(chars=data, others=others, now="file://" + now_dir + "/",
                                                    before=bool(before), rev=before or "", variants=variants)))
    path = os.path.join(out, "index.html")
    open(path, "w").write(page)
    print(path)


PAGE = """<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Grid Game sounds</title>
<style>
  :root { --bg:#12131a; --card:#1b1d27; --ink:#e8e9f1; --dim:#8b8fa6; --line:#2a2d3c; --go:#ffcf5a; --was:#7fb3ff; }
  * { box-sizing: border-box; }
  body { margin:0; background:var(--bg); color:var(--ink); font:14px/1.4 system-ui, -apple-system, "Segoe UI", sans-serif; }
  header { position:sticky; top:0; z-index:2; background:rgba(18,19,26,.94); backdrop-filter:blur(6px); border-bottom:1px solid var(--line);
           padding:12px 16px; display:flex; gap:12px; align-items:center; flex-wrap:wrap; }
  h1 { font-size:16px; margin:0 8px 0 0; }
  input[type=search] { background:var(--card); border:1px solid var(--line); color:var(--ink); border-radius:8px; padding:7px 10px; width:220px; font:inherit; }
  label { color:var(--dim); display:flex; gap:6px; align-items:center; }
  main { padding:12px 16px 60px; max-width:1180px; margin:0 auto; }
  h2 { font-size:13px; text-transform:uppercase; letter-spacing:.08em; color:var(--dim); margin:22px 0 8px; }
  .row { display:grid; grid-template-columns:150px repeat(3, 1fr); gap:8px; padding:6px 0; border-bottom:1px solid var(--line); align-items:stretch; }
  .who { font-weight:600; align-self:center; }
  .cell { background:var(--card); border:1px solid var(--line); border-radius:10px; padding:7px 9px; min-width:0; }
  .cell .lab { white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
  .cell .kind { color:var(--dim); font-size:12px; }
  .cell .file { color:var(--dim); font:11px ui-monospace, SFMono-Regular, Menlo, monospace; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
  .btns { display:flex; gap:6px; margin-top:6px; }
  button { font:inherit; border:1px solid var(--line); background:#242736; color:var(--ink); border-radius:8px; padding:5px 10px; cursor:pointer; }
  button:hover { border-color:#454a63; }
  button.now { border-color:#5c4d1c; color:var(--go); }
  button.was { border-color:#27405f; color:var(--was); }
  button.playing { background:#34384d; }
  .grid { display:grid; grid-template-columns:repeat(auto-fill, minmax(230px, 1fr)); gap:8px; }
  .hint { color:var(--dim); margin:8px 0 0; }
  @media (max-width:760px) { .row { grid-template-columns:1fr; } .who { margin-top:6px; } }
</style></head><body>
<header><h1>Grid Game sounds</h1>
  <input id="q" type="search" placeholder="Find a character or a sound" autofocus>
  <label><input id="twice" type="checkbox"> play twice, half a second apart (as a held primary)</label>
  <span class="hint" id="legend"></span>
</header>
<main id="main"></main>
<script>
const D = __DATA__;
const main = document.getElementById('main');
let playing = [];
function play(src, btn) {
  playing.forEach(a => { a.pause(); }); playing = [];
  document.querySelectorAll('button.playing').forEach(b => b.classList.remove('playing'));
  const twice = document.getElementById('twice').checked;
  const a = new Audio(src); playing.push(a); btn.classList.add('playing');
  a.onended = () => btn.classList.remove('playing');
  a.play();
  if (twice) setTimeout(() => { const b = new Audio(src); playing.push(b); b.play(); }, 500);
}
function buttons(now, was) {
  let h = '<div class="btns">';
  if (now) h += `<button class="now" data-src="${D.now}${now}.wav">&#9654; now</button>`;
  if (was) h += `<button class="was" data-src="before/${was}.wav" title="${was}">&#9654; before</button>`;
  return h + '</div>';
}
function render(filter) {
  const f = (filter || '').toLowerCase();
  let h = '';
  if (D.variants && D.variants.length && !f) {
    h += '<h2>Which of these? (alternatives, not in the game)</h2><div class="grid">';
    for (const v of D.variants)
      h += `<div class="cell"><div class="lab">${v.file.replace('.wav', '')}</div><div class="kind">${v.note}</div>` +
           `<div class="btns"><button class="now" data-src="variants/${v.file}">&#9654; play</button></div></div>`;
    h += '</div>';
  }
  h += '<h2>The roster</h2>';
  for (const c of D.chars) {
    const hay = (c.name + ' ' + c.cells.map(x => x.label + ' ' + x.now).join(' ')).toLowerCase();
    if (f && !hay.includes(f)) continue;
    h += `<div class="row"><div class="who">${c.name}</div>`;
    for (const x of c.cells) {
      h += `<div class="cell"><div class="lab">${x.label}${x.kind ? ` <span class="kind">${x.kind}</span>` : ''}</div>` +
           `<div class="file">${x.now || ''}</div>${buttons(x.now, x.was)}</div>`;
    }
    h += '</div>';
  }
  for (const o of D.others) {
    const list = o.sounds.filter(s => !f || s.includes(f));
    if (!list.length) continue;
    h += `<h2>${o.title}</h2><div class="grid">`;
    for (const s of list) h += `<div class="cell"><div class="file">${s}</div>${buttons(s, (o.was || []).includes(s) ? s : null)}</div>`;
    h += '</div>';
  }
  main.innerHTML = h;
}
main.addEventListener('click', e => { const b = e.target.closest('button[data-src]'); if (b) play(b.dataset.src, b); });
document.getElementById('q').addEventListener('input', e => render(e.target.value));
document.getElementById('legend').textContent = D.before ? `gold: as it is now · blue: as it was at ${D.rev}` : '';
render('');
</script></body></html>
"""

if __name__ == "__main__":
    main()
