#!/usr/bin/env python3
"""Prints what every character actually hears, resolved the way the game does.

`AbilitySounds.scala` keys on projectile type first and on (character,
projectile) second, so reading either table alone does not tell you whether a
character sounds like themselves. This resolves both against CharacterDef and
prints the roster, which is the only practical way to audit the thing the sound
set is FOR: that a Gorilla, a Golem and a Beetle do not all sound like a rock
rolling downhill just because the data says all three "throw a boulder".

    python3 scripts/sound_roster.py            # the whole roster
    python3 scripts/sound_roster.py --shared   # only sounds >1 character shares
"""
import collections
import os
import re
import sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
SOUNDS = os.path.join(ROOT, "src/main/scala/com/gridgame/client/audio/AbilitySounds.scala")
CHARS = os.path.join(ROOT, "src/main/scala/com/gridgame/common/model/CharacterDef.scala")


def load_tables():
    src = open(SOUNDS).read()
    by_type, overrides = {}, {}
    for m in re.finditer(r'set\("([a-z0-9_]+)",\s*((?:[^()]|\([^()]*\))*?)\)\n', src):
        for pt in re.findall(r"ProjectileType\.([A-Z_0-9]+)", m.group(2)):
            by_type[pt] = m.group(1)
    for m in re.finditer(r'only\("([a-z0-9_]+)",\s*CharacterId\.(\w+),\s*ProjectileType\.([A-Z_0-9]+)\)', src):
        overrides[(m.group(2), m.group(3))] = m.group(1)
    return by_type, overrides


def load_characters():
    src = open(CHARS).read()
    out = []
    for _, body in re.findall(r"val (\w+): CharacterDef = CharacterDef\((.*?)\n  \)\n", src, re.S):
        cid = re.search(r"id = CharacterId\.(\w+)", body).group(1)
        name = re.search(r'displayName = "([^"]+)"', body).group(1)
        primary = re.search(r"primaryProjectileType = ProjectileType\.(\w+)", body).group(1)
        abilities = []
        for kind in ("qAbility", "eAbility"):
            one_line = re.search(kind + r" = AbilityDef\((.*?)\n", body, re.S)
            if one_line and "name =" in one_line.group(1):
                seg = one_line.group(1)
            else:
                block = re.search(kind + r" = AbilityDef\((.*?)\n    \)", body, re.S)
                seg = block.group(1) if block else ""
            label = re.search(r'name = "([^"]+)"', seg)
            proj = re.search(r"projectileType = ProjectileType\.(\w+)", seg)
            abilities.append((label.group(1) if label else "?", proj.group(1) if proj else None))
        out.append((name, cid, primary, abilities))
    return out


def main():
    by_type, overrides = load_tables()
    shared_only = "--shared" in sys.argv

    def sound_for(cid, proj):
        if proj is None:
            return "—"                       # a dash/teleport/buff, no projectile
        return overrides.get((cid, proj)) or by_type.get(proj, "?MISSING")

    rows = [(name, sound_for(cid, primary),
             [(label, sound_for(cid, proj)) for label, proj in abilities])
            for name, cid, primary, abilities in load_characters()]

    if shared_only:
        by_sound = collections.defaultdict(list)
        for name, primary, _ in rows:
            by_sound[primary].append(name)
        for sound, names in sorted(by_sound.items(), key=lambda kv: -len(kv[1])):
            if len(names) > 1:
                print("%-24s %d  %s" % (sound, len(names), ", ".join(names)))
        return

    for name, primary, abilities in rows:
        extra = "  ".join("%s:%s=%s" % (k, label[:14], snd)
                          for k, (label, snd) in zip("QE", abilities))
        print("%-15s %-24s %s" % (name, primary, extra))


if __name__ == "__main__":
    main()
