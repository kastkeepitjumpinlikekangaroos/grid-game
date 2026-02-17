#!/usr/bin/env python3
"""Generate the Spaceman character sprite sheet (sprites/character.png) at 64x64."""

import sys, os
sys.path.insert(0, os.path.dirname(__file__))
from sprite_base import generate_character

def main():
    generate_character(
        "character",
        body_color=(80, 120, 180),
        body_highlight=(120, 160, 220),
        head_color=(200, 210, 220),
        head_highlight=(230, 240, 250),
        leg_color=(60, 80, 120),
        accent_color=(100, 200, 200),
        hat_style='helm',
        hat_color=(200, 210, 220),
        skin_color=(230, 220, 210),
        eye_color=(50, 200, 200),
    )

if __name__ == "__main__":
    main()
