#!/usr/bin/env python3
"""Procedurally synthesizes all game audio (sfx + music) into sounds/*.wav.

Everything is generated from numpy oscillators/noise — no samples, no external audio libraries,
zero licensing concerns. This file is the driver; the sounds are in the modules beside it:

    sound_kit.py       what a sound is built from, and the chain that finishes it
    sound_engines.py   the family engines: a bolt, a swing, a throw, a slam, a gun, a blast...
    sound_attacks.py   every character's attacks that throw something
    sound_casts.py     every character's abilities that throw nothing: dashes, blinks, phases,
                       barriers, traps
    sound_events.py    hits, spawn and death, detonations, traps going off
    sound_music.py     the two music loops

How a sound is designed and judged is scripts/SOUNDS.md; which sound the game plays for what is
`AbilitySounds.scala` (src/main/scala/com/gridgame/client/audio), which this cross-checks itself
against, both ways, every time it runs.

    python3 scripts/generate_sounds.py                  # everything -> sounds/ (about a minute)
    python3 scripts/generate_sounds.py wizard arcane    # only sounds whose names match
    python3 scripts/generate_sounds.py --out /tmp/snd   # somewhere else (to look before replacing)
    python3 scripts/generate_sounds.py --list           # the names, and nothing made
    python3 scripts/generate_sounds.py --jobs 1         # in this process, one at a time

Needs numpy. Output is deterministic (every random source is seeded by name), so a rerun changes
only the sounds whose code changed, whatever `--jobs` is.
"""
import os
import re
import sys
import time

import sound_kit as kit
import sound_attacks  # noqa: F401  (each registers its sounds with the kit as it is imported)
import sound_casts    # noqa: F401
import sound_events   # noqa: F401

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
SCALA = os.path.join(ROOT, "src", "main", "scala", "com", "gridgame", "client", "audio")
MUSIC = ("music_menu", "music_battle")


def scala_names():
    """Every sound `AbilitySounds.scala` and `AudioManager.scala` name, or None outside the repo."""
    names = set()
    for f, pattern in (("AbilitySounds.scala", r'"((?:atk|blast|cast)_[a-z0-9_]+)"'),
                       ("AudioManager.scala", r'"([a-z][a-z0-9_]+)"')):
        path = os.path.join(SCALA, f)
        if not os.path.exists(path):
            return None
        src = open(path).read()
        if f == "AudioManager.scala":
            # only its list of event sounds, not every string in the file
            m = re.search(r"EVENT_SOUNDS\s*=\s*Seq\((.*?)\)", src, re.S)
            src = m.group(1) if m else ""
        names |= set(re.findall(pattern, src))
    return names


def check_scala_mapping(generated):
    """The game and this script have to agree. A sound the game names that has no file plays
    nothing, silently; a sound made that nothing plays is a megabyte nobody hears. Either is
    caught here, the moment the sounds are rebuilt."""
    named = scala_names()
    if named is None:
        return
    missing = sorted(named - generated)
    unused = sorted(generated - named)
    if missing:
        raise SystemExit("the game names sounds that are not generated: " + ", ".join(missing))
    if unused:
        raise SystemExit("generated, but nothing in the game plays them: " + ", ".join(unused))
    # A primary fires twice a second: it is short, and it does not hold. (Twenty-three were of a
    # long class or held: a boulder thrown twice a second lasted a second and a quarter.)
    table = open(os.path.join(SCALA, "AbilitySounds.scala")).read()
    primaries = set(re.findall(r'hears\(CharacterId\.\w+,\s*"([a-z0-9_]+)"', table))
    long_ = sorted(n for n in primaries if n in kit.REGISTRY and
                   (kit.REGISTRY[n][2] not in ("bolt", "melee", "tiny") or kit.REGISTRY[n][4] is not None))
    if long_:
        raise SystemExit("primaries registered as a long class or as held (they fire twice a second): " + ", ".join(long_))


def _one(job):
    name, out_dir = job
    try:
        kit.render(name, out_dir)
        return name, None
    except Exception:
        import traceback
        return name, traceback.format_exc()


def _music(name, out_dir):
    import sound_music
    kit.OUT_DIR = out_dir
    if name == "music_menu":
        kit.save_wav("music_menu", sound_music.gen_menu_theme(), level=0.74, trim=False, shape=False, by="peak",
                     tail=None, dip=None, air=None, glue=(0.30, 2.2))
    else:
        kit.save_wav("music_battle", sound_music.gen_battle_theme(), level=0.94, trim=False, shape=False, by="peak",
                     tail=None, dip=None, air=None, glue=(0.26, 2.8))


def main():
    args = sys.argv[1:]
    out_dir = kit.OUT_DIR
    jobs = max(1, (os.cpu_count() or 2) - 1)
    only = []
    i = 0
    while i < len(args):
        a = args[i]
        if a == "--out":
            out_dir = args[i + 1]
            i += 1
        elif a == "--jobs":
            jobs = int(args[i + 1])
            i += 1
        elif a == "--list":
            print("\n".join(sorted(list(kit.REGISTRY) + list(MUSIC))))
            return
        elif a.startswith("--"):
            raise SystemExit("unknown option " + a)
        else:
            only.append(a)
        i += 1

    names = [n for n in kit.REGISTRY if not only or any(o in n for o in only)]
    music = [n for n in MUSIC if not only or any(o in n for o in only)]
    if not only:
        check_scala_mapping(set(kit.REGISTRY) | set(MUSIC))
    os.makedirs(out_dir, exist_ok=True)
    start = time.time()
    work = [(n, out_dir) for n in names]
    if jobs > 1 and len(work) > 4:
        from multiprocessing import Pool
        with Pool(jobs) as pool:
            done = pool.map(_one, work, chunksize=2)
    else:
        done = [_one(w) for w in work]
    failed = [(n, err) for n, err in done if err]
    for n, err in failed:
        print("FAILED %s\n%s" % (n, err))
    for n in music:
        _music(n, out_dir)
    if failed:
        raise SystemExit("%d sound(s) failed" % len(failed))
    print("generated %d sounds in %s (%.0fs)" % (len(names) + len(music), os.path.normpath(out_dir), time.time() - start))


if __name__ == "__main__":
    main()
