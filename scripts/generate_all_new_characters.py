#!/usr/bin/env python3
"""Generate all 100 new character sprite sheets using category-specific custom draw functions."""

import sys
import os
sys.path.insert(0, os.path.dirname(__file__))
from sprite_base import generate_character
from generate_elementals import ELEMENTAL_DRAW_FUNCTIONS
from generate_undead import UNDEAD_DRAW_FUNCTIONS
from generate_medieval import MEDIEVAL_DRAW_FUNCTIONS
from generate_scifi import SCIFI_DRAW_FUNCTIONS
from generate_beasts import BEAST_DRAW_FUNCTIONS
from generate_mythological import MYTHOLOGICAL_DRAW_FUNCTIONS
from generate_specialists import SPECIALIST_DRAW_FUNCTIONS

# All 7 category modules with their draw function registries
CATEGORY_MODULES = [
    ("Elemental", ELEMENTAL_DRAW_FUNCTIONS),       # IDs 12-26, 15 chars
    ("Undead/Dark", UNDEAD_DRAW_FUNCTIONS),          # IDs 27-41, 15 chars
    ("Medieval/Fantasy", MEDIEVAL_DRAW_FUNCTIONS),   # IDs 42-56, 15 chars
    ("Sci-Fi/Tech", SCIFI_DRAW_FUNCTIONS),           # IDs 57-71, 15 chars
    ("Nature/Beast", BEAST_DRAW_FUNCTIONS),           # IDs 72-86, 15 chars
    ("Mythological", MYTHOLOGICAL_DRAW_FUNCTIONS),    # IDs 87-101, 15 chars
    ("Specialist", SPECIALIST_DRAW_FUNCTIONS),        # IDs 102-111, 10 chars
]

def main():
    total = 0
    for category_name, draw_functions in CATEGORY_MODULES:
        count = len(draw_functions)
        print(f"\n--- {category_name} ({count} characters) ---")
        for name, draw_func in draw_functions.items():
            generate_character(name, draw_func=draw_func)
        total += count
    print(f"\nGenerated {total} character sprites across {len(CATEGORY_MODULES)} categories.")

if __name__ == "__main__":
    main()
