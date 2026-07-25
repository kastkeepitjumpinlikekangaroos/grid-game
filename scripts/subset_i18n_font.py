#!/usr/bin/env python3
"""
Subset & slim the bundled CJK fonts used by the in-game OpenGL renderer and the
JavaFX UI for internationalization.

The upstream Noto Sans SC / KR variable fonts are ~18MB / ~10MB — far too large to
commit and bundle in every client build. This script instances them to a single
weight (removing variable-font overhead) and subsets them to a bounded glyph set:

  * Basic Latin + Latin-1 + common punctuation (so digits/labels render from these
    fonts too when a locale forces the CJK family)
  * CJK symbols & punctuation and fullwidth forms
  * Simplified Chinese: the GB2312 character set (~6763 chars ~= all modern
    everyday Simplified Chinese, incl. names/chat)
  * Korean: the full modern Hangul syllables block (U+AC00-U+D7A3, 11172) + Jamo
  * Every codepoint that actually appears in the i18n JSON catalogs (i18n/*.json),
    so translated UI text is always covered even if it uses a rarer glyph

Result: ~a few MB per font instead of ~18MB, while still rendering virtually all
real Chinese/Korean UI text, player names, and chat.

Requires: fonttools  (pip3 install fonttools)

Usage:
    python3 scripts/subset_i18n_font.py \
        --sc /path/to/NotoSansSC.ttf \
        --kr /path/to/NotoSansKR.ttf \
        --outdir fonts
"""
import argparse
import glob
import json
import os
import sys

from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer


def latin_and_punct():
    cps = set()
    cps |= set(range(0x0020, 0x007F))   # Basic Latin
    cps |= set(range(0x00A0, 0x0100))   # Latin-1 Supplement
    cps |= set(range(0x2000, 0x2070))   # General Punctuation
    cps |= set(range(0x3000, 0x3040))   # CJK Symbols and Punctuation
    cps |= set(range(0xFF00, 0xFFF0))   # Halfwidth and Fullwidth Forms
    return cps


def gb2312_codepoints():
    """Every character encodable in GB2312 == modern everyday Simplified Chinese."""
    cps = set()
    for hi in range(0xA1, 0xF8):
        for lo in range(0xA1, 0xFF):
            try:
                cps.add(ord(bytes([hi, lo]).decode("gb2312")))
            except (UnicodeDecodeError, ValueError):
                pass
    return cps


def hangul_codepoints():
    cps = set()
    cps |= set(range(0xAC00, 0xD7A4))   # Hangul Syllables
    cps |= set(range(0x1100, 0x1200))   # Hangul Jamo
    cps |= set(range(0x3130, 0x3190))   # Hangul Compatibility Jamo
    return cps


def catalog_codepoints(repo_root):
    """Union of every codepoint used in any i18n/*.json catalog value (and key)."""
    cps = set()
    for path in glob.glob(os.path.join(repo_root, "i18n", "*.json")):
        try:
            with open(path, encoding="utf-8") as f:
                data = json.load(f)
        except (OSError, json.JSONDecodeError):
            continue
        for k, v in data.items():
            for s in (k, v):
                if isinstance(s, str):
                    cps |= {ord(c) for c in s}
    return cps


def set_family_name(font, family):
    """Give the (now static) font a clean, predictable family name so JavaFX and
    AWT resolve it consistently. Variable-font instances otherwise keep a
    misleading default subfamily (e.g. 'Thin')."""
    name = font["name"]
    for nid in (1, 16):          # Family, Typographic Family
        name.setName(family, nid, 3, 1, 0x409)
        name.setName(family, nid, 1, 0, 0)
    for nid in (2, 17):          # Subfamily, Typographic Subfamily
        name.setName("Regular", nid, 3, 1, 0x409)
        name.setName("Regular", nid, 1, 0, 0)
    name.setName(family, 4, 3, 1, 0x409)         # Full name
    name.setName(family, 4, 1, 0, 0)
    name.setName(family.replace(" ", "") + "-Regular", 6, 3, 1, 0x409)  # PostScript
    name.setName(family.replace(" ", "") + "-Regular", 6, 1, 0, 0)


def subset_font(src, dst, unicodes, family=None):
    font = TTFont(src)
    # Instance the weight axis to Regular (400) if this is a variable font,
    # dropping gvar/HVAR/etc. overhead.
    if "fvar" in font:
        try:
            instancer.instantiateVariableFont(
                font, {"wght": 400}, inplace=True, updateFontNames=False
            )
        except Exception as e:  # noqa: BLE001
            print(f"  (instancing skipped: {e})")
    if family:
        set_family_name(font, family)

    options = subset.Options()
    options.glyph_names = False
    options.hinting = False
    options.desubroutinize = True
    options.name_IDs = ["*"]
    options.name_legacy = True
    options.name_languages = ["*"]
    options.recalc_bounds = True
    options.drop_tables = ["BASE", "GSUB", "GPOS", "GDEF", "vhea", "vmtx"]
    options.layout_features = []

    subsetter = subset.Subsetter(options=options)
    subsetter.populate(unicodes=sorted(unicodes))
    subsetter.subset(font)
    font.save(dst)
    return os.path.getsize(dst)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--sc", required=True, help="source Noto Sans SC ttf")
    ap.add_argument("--kr", required=True, help="source Noto Sans KR ttf")
    ap.add_argument("--outdir", default="fonts")
    args = ap.parse_args()

    repo_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    os.makedirs(args.outdir, exist_ok=True)

    base = latin_and_punct()
    cat = catalog_codepoints(repo_root)
    print(f"catalog codepoints found: {len(cat)}")

    sc_unicodes = base | gb2312_codepoints() | cat
    kr_unicodes = base | hangul_codepoints() | cat

    sc_out = os.path.join(args.outdir, "NotoSansSC-i18n.ttf")
    kr_out = os.path.join(args.outdir, "NotoSansKR-i18n.ttf")

    sc_size = subset_font(args.sc, sc_out, sc_unicodes, family="Noto Sans SC")
    print(f"SC: {len(sc_unicodes)} codepoints -> {sc_out} ({sc_size/1e6:.2f} MB)")
    kr_size = subset_font(args.kr, kr_out, kr_unicodes, family="Noto Sans KR")
    print(f"KR: {len(kr_unicodes)} codepoints -> {kr_out} ({kr_size/1e6:.2f} MB)")


if __name__ == "__main__":
    main()
