#!/usr/bin/env python3
"""Prints what every character's three attacks sound like, as the game resolves it.

`AbilitySounds.scala` has a row a character (`hears(character, primary, Q, E)`); this reads it
against the roster and prints each attack by name beside its sound, which is the practical way to
audit the thing the sound set is FOR: that every attack sounds like what it is, in the hands of
whoever is doing it.

    python3 scripts/sound_roster.py              # the whole roster
    python3 scripts/sound_roster.py wizard       # characters whose name matches
    python3 scripts/sound_roster.py --shared     # sounds more than one attack in the roster makes
    python3 scripts/sound_roster.py --missing    # rows naming a sound with no file in sounds/
"""
import collections
import os
import re
import sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
SOUNDS = os.path.join(ROOT, "src/main/scala/com/gridgame/client/audio/AbilitySounds.scala")
# The roster, a file per category, in id order (Roster.scala)
ROSTER = os.path.join(ROOT, "src/main/scala/com/gridgame/common/model/roster")
CATEGORIES = ("Originals", "Elementals", "Undead", "Medieval", "SciFi", "Beasts", "Mythological", "Specialists")
KINDS = {"DashBuff": "dash", "PhaseShiftBuff": "phase", "BarrierCast": "barrier", "TeleportCast": "blink",
         "TrapCast": "trap", "FanProjectile": "fan", "GroundSlam": "slam"}


def load_rows():
    src = open(SOUNDS).read()
    return {m.group(1): (m.group(2), m.group(3), m.group(4)) for m in re.finditer(
        r'hears\(CharacterId\.(\w+),\s*"([a-z0-9_]+)",\s*"([a-z0-9_]+)",\s*"([a-z0-9_]+)"\)', src)}


def _ability(body, kind):
    """The text of one AbilityDef(...), by matching its parentheses."""
    start = body.index(kind + " = AbilityDef(") + len(kind + " = AbilityDef(")
    depth, i, quoted = 1, start, False
    while depth:
        ch = body[i]
        if ch == '"' and body[i - 1] != "\\":
            quoted = not quoted
        elif not quoted and ch == "(":
            depth += 1
        elif not quoted and ch == ")":
            depth -= 1
        i += 1
    return body[start:i - 1]


def load_characters():
    src = "".join(open(os.path.join(ROSTER, c + ".scala")).read() for c in CATEGORIES)
    out = []
    for _, body in re.findall(r"val (\w+): CharacterDef = CharacterDef\((.*?)\n  \)\n", src, re.S):
        cid = re.search(r"id = CharacterId\.(\w+)", body).group(1)
        name = re.search(r'displayName = "([^"]+)"', body).group(1)
        abilities = []
        for kind in ("qAbility", "eAbility"):
            seg = _ability(body, kind)
            label = re.search(r'name = "([^"]+)"', seg).group(1)
            cast = re.search(r"castBehavior = (\w+)", seg)
            abilities.append((label, KINDS.get(cast.group(1), "") if cast else ""))
        out.append((name, cid, abilities))
    return out


def main():
    flags = [a for a in sys.argv[1:] if a.startswith("--")]
    match = [a.lower() for a in sys.argv[1:] if not a.startswith("--")]
    rows = load_rows()
    characters = load_characters()

    if "--shared" in flags:
        by_sound = collections.defaultdict(list)
        for name, cid, abilities in characters:
            p, q, e = rows.get(cid, ("?", "?", "?"))
            for label, snd in (("primary", p), (abilities[0][0], q), (abilities[1][0], e)):
                by_sound[snd].append("%s's %s" % (name, label))
        for snd, who in sorted(by_sound.items(), key=lambda kv: -len(kv[1])):
            if len(set(w.split("'s ")[0] for w in who)) > 1:
                print("%-24s %s" % (snd, ", ".join(who)))
        return

    if "--missing" in flags:
        bad = 0
        for name, cid, _ in characters:
            for snd in rows.get(cid, ("?MISSING",) * 3):
                if not os.path.isfile(os.path.join(ROOT, "sounds", snd + ".wav")):
                    print("%s: %s" % (name, snd))
                    bad += 1
        sys.exit(1 if bad else 0)

    for name, cid, abilities in characters:
        if match and not any(m in name.lower() for m in match):
            continue
        p, q, e = rows.get(cid, ("?MISSING",) * 3)
        def cell(key, ab, snd):
            kind = " (%s)" % ab[1] if ab[1] else ""
            return "%s %s%s = %s" % (key, ab[0], kind, snd)
        print("%-14s %-22s %-46s %s" % (name, p, cell("Q", abilities[0], q), cell("E", abilities[1], e)))


if __name__ == "__main__":
    main()
