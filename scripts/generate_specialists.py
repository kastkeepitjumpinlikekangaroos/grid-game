#!/usr/bin/env python3
"""Specialist character sprite generators (IDs 102-111).

10 characters with unique draw functions for profession-defining visuals.
This file contains the first 5: Alchemist, Puppeteer, Gambler, Blacksmith, Pirate.
"""

import sys
import os
sys.path.insert(0, os.path.dirname(__file__))
from sprite_base import (
    generate_character, ellipse, pill, _darken, _brighten,
    draw_fur_texture, draw_scale_texture,
    OUTLINE, BLACK, DOWN, UP, LEFT, RIGHT,
)

# ---------------------------------------------------------------------------
# Palettes
# ---------------------------------------------------------------------------

# Alchemist palette
ALC_COAT = (70, 110, 55)
ALC_COAT_LIGHT = (95, 140, 75)
ALC_COAT_DARK = (48, 78, 38)
ALC_APRON = (180, 170, 150)
ALC_APRON_DARK = (150, 140, 120)
ALC_APRON_STAIN = (120, 100, 60)
ALC_SKIN = (220, 200, 175)
ALC_SKIN_DARK = (190, 170, 145)
ALC_GOGGLES = (160, 140, 80)
ALC_GOGGLES_LENS = (140, 200, 220)
ALC_GOGGLES_LENS_BRIGHT = (180, 230, 245)
ALC_FLASK = (100, 180, 110)
ALC_FLASK_BRIGHT = (140, 220, 150)
ALC_FLASK_LIQUID = (80, 200, 60)
ALC_FLASK_BUBBLE = (160, 240, 140)
ALC_VIAL_RED = (200, 60, 60)
ALC_VIAL_BLUE = (60, 100, 200)
ALC_VIAL_YELLOW = (220, 200, 60)
ALC_BELT = (100, 75, 50)
ALC_BELT_DARK = (70, 52, 35)
ALC_HAIR = (140, 100, 60)
ALC_BOOT = (80, 60, 40)
ALC_RESIDUE = (160, 140, 50)
ALC_RESIDUE2 = (100, 80, 40)
ALC_LEG = (90, 80, 65)

# Puppeteer palette
PUP_COAT = (80, 35, 90)
PUP_COAT_LIGHT = (110, 55, 120)
PUP_COAT_DARK = (55, 22, 62)
PUP_SKIN = (230, 215, 220)
PUP_SKIN_DARK = (200, 185, 190)
PUP_STRING = (200, 200, 210)
PUP_STRING_DIM = (160, 160, 170)
PUP_PUPPET_BODY = (200, 120, 80)
PUP_PUPPET_HEAD = (220, 180, 150)
PUP_PUPPET_EYE = (30, 30, 30)
PUP_MASK = (230, 220, 200)
PUP_MASK_DARK = (190, 180, 160)
PUP_MASK_ACCENT = (200, 50, 50)
PUP_BAR = (140, 110, 70)
PUP_BAR_DARK = (100, 78, 48)
PUP_HAIR = (50, 30, 55)
PUP_LEG = (60, 30, 65)
PUP_BOOT = (45, 25, 50)
PUP_GLOVE = (90, 40, 100)

# Gambler palette
GAM_SUIT = (40, 40, 45)
GAM_SUIT_LIGHT = (65, 65, 72)
GAM_SUIT_DARK = (25, 25, 30)
GAM_VEST = (160, 40, 40)
GAM_VEST_LIGHT = (190, 60, 60)
GAM_VEST_DARK = (120, 28, 28)
GAM_SKIN = (230, 215, 195)
GAM_SKIN_DARK = (200, 185, 165)
GAM_HAT = (30, 30, 35)
GAM_HAT_BAND = (200, 170, 50)
GAM_HAT_DARK = (20, 20, 22)
GAM_HAT_LIGHT = (50, 50, 55)
GAM_CARD_WHITE = (245, 245, 240)
GAM_CARD_RED = (200, 40, 40)
GAM_CARD_BLACK = (30, 30, 30)
GAM_CHAIN = (220, 190, 60)
GAM_CHAIN_BRIGHT = (250, 220, 80)
GAM_DICE_WHITE = (240, 240, 235)
GAM_DICE_DOT = (30, 30, 30)
GAM_HAIR = (50, 40, 35)
GAM_LEG = (35, 35, 40)
GAM_BOOT = (25, 25, 28)

# Blacksmith palette
BKS_APRON = (120, 80, 45)
BKS_APRON_DARK = (85, 55, 30)
BKS_APRON_LIGHT = (150, 105, 60)
BKS_SKIN = (200, 160, 130)
BKS_SKIN_DARK = (170, 130, 100)
BKS_SKIN_SOOT = (140, 110, 85)
BKS_GAUNTLET = (140, 145, 155)
BKS_GAUNTLET_DARK = (100, 105, 115)
BKS_GAUNTLET_LIGHT = (175, 180, 190)
BKS_HAMMER_HEAD = (160, 165, 175)
BKS_HAMMER_DARK = (120, 125, 135)
BKS_HAMMER_HANDLE = (100, 70, 40)
BKS_GOGGLES = (130, 135, 145)
BKS_GOGGLES_LENS = (200, 140, 60)
BKS_GOGGLES_DARK = (90, 95, 105)
BKS_HAIR = (60, 45, 35)
BKS_FORGE_GLOW = (255, 140, 40)
BKS_FORGE_DIM = (200, 100, 30)
BKS_BELT = (90, 65, 40)
BKS_BELT_BUCKLE = (180, 170, 50)
BKS_SOOT = (60, 55, 50)
BKS_LEG = (100, 80, 60)
BKS_BOOT = (70, 55, 40)
BKS_BODY = (160, 130, 100)
BKS_BODY_DARK = (130, 100, 75)

# Pirate palette
PIR_COAT = (100, 40, 35)
PIR_COAT_LIGHT = (135, 60, 50)
PIR_COAT_DARK = (70, 28, 24)
PIR_BANDANA = (180, 40, 40)
PIR_BANDANA_DARK = (140, 30, 30)
PIR_BANDANA_LIGHT = (210, 60, 55)
PIR_SKIN = (210, 180, 150)
PIR_SKIN_DARK = (180, 150, 120)
PIR_EYEPATCH = (30, 25, 25)
PIR_EYEPATCH_STRAP = (50, 40, 35)
PIR_EARRING = (220, 190, 50)
PIR_CUTLASS_BLADE = (180, 185, 195)
PIR_CUTLASS_LIGHT = (210, 215, 225)
PIR_CUTLASS_GUARD = (200, 170, 50)
PIR_CUTLASS_HANDLE = (100, 70, 40)
PIR_SKULL_BUCKLE = (220, 215, 200)
PIR_SKULL_EYE = (30, 25, 25)
PIR_PEG = (180, 150, 100)
PIR_PEG_DARK = (140, 115, 75)
PIR_BELT = (80, 60, 35)
PIR_BELT_DARK = (55, 42, 25)
PIR_LEG = (70, 55, 45)
PIR_BOOT = (50, 38, 30)
PIR_HAIR = (40, 30, 25)
PIR_COAT_TAIL = (90, 35, 30)


# ===================================================================
# ALCHEMIST (ID 102)
# Green/brown lab coat, bubbling potion flask, goggles, belt of vials,
# stained apron, explosion residue marks.
# ===================================================================

def draw_alchemist(draw, ox, oy, direction, frame):
    bob = [0, -2, 0, -1][frame]
    leg_spread = [-4, 0, 4, 0][frame]
    bubble_offset = [0, -2, -1, -3][frame]

    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 20
    head_cy = body_cy - 20

    if direction == DOWN:
        # Legs
        draw.rectangle([cx - 10 + leg_spread, body_cy + 10,
                        cx - 4 + leg_spread, base_y], fill=ALC_LEG, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, body_cy + 10,
                        cx + 10 - leg_spread, base_y], fill=ALC_LEG, outline=OUTLINE)
        # Boots
        draw.rectangle([cx - 10 + leg_spread, base_y - 5,
                        cx - 4 + leg_spread, base_y], fill=ALC_BOOT, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, base_y - 5,
                        cx + 10 - leg_spread, base_y], fill=ALC_BOOT, outline=OUTLINE)

        # Lab coat body
        ellipse(draw, cx, body_cy, 14, 12, ALC_COAT)
        ellipse(draw, cx - 3, body_cy - 2, 8, 7, ALC_COAT_LIGHT, outline=None)
        # V-neck seam
        draw.line([(cx, body_cy - 8), (cx - 4, body_cy + 4)],
                  fill=ALC_COAT_DARK, width=1)
        draw.line([(cx, body_cy - 8), (cx + 4, body_cy + 4)],
                  fill=ALC_COAT_DARK, width=1)

        # Apron over coat
        draw.rectangle([cx - 10, body_cy + 2, cx + 10, body_cy + 12],
                       fill=ALC_APRON, outline=OUTLINE)
        # Apron stains
        draw.point((cx - 4, body_cy + 5), fill=ALC_APRON_STAIN)
        draw.point((cx + 3, body_cy + 7), fill=ALC_APRON_STAIN)
        draw.point((cx - 6, body_cy + 9), fill=ALC_RESIDUE2)

        # Belt with vials
        draw.rectangle([cx - 14, body_cy + 10, cx + 14, body_cy + 14],
                       fill=ALC_BELT, outline=OUTLINE)
        # Vials on belt
        draw.rectangle([cx - 10, body_cy + 6, cx - 8, body_cy + 10],
                       fill=ALC_VIAL_RED, outline=OUTLINE)
        draw.rectangle([cx - 6, body_cy + 6, cx - 4, body_cy + 10],
                       fill=ALC_VIAL_BLUE, outline=OUTLINE)
        draw.rectangle([cx + 4, body_cy + 6, cx + 6, body_cy + 10],
                       fill=ALC_VIAL_YELLOW, outline=OUTLINE)
        draw.rectangle([cx + 8, body_cy + 6, cx + 10, body_cy + 10],
                       fill=ALC_FLASK, outline=OUTLINE)

        # Arms
        draw.rectangle([cx - 18, body_cy - 6, cx - 12, body_cy + 6],
                       fill=ALC_COAT, outline=OUTLINE)
        draw.rectangle([cx - 18, body_cy + 2, cx - 12, body_cy + 6],
                       fill=ALC_SKIN, outline=OUTLINE)
        draw.rectangle([cx + 12, body_cy - 6, cx + 18, body_cy + 6],
                       fill=ALC_COAT, outline=OUTLINE)
        draw.rectangle([cx + 12, body_cy + 2, cx + 18, body_cy + 6],
                       fill=ALC_SKIN, outline=OUTLINE)
        # Shoulder pads
        ellipse(draw, cx - 14, body_cy - 6, 5, 3, ALC_COAT_LIGHT)
        ellipse(draw, cx + 14, body_cy - 6, 5, 3, ALC_COAT_LIGHT)

        # Flask in right hand
        flask_x = cx + 20
        flask_y = body_cy - 2
        # Flask body (round bottom)
        ellipse(draw, flask_x, flask_y + 4, 4, 5, ALC_FLASK)
        ellipse(draw, flask_x - 1, flask_y + 3, 2, 3, ALC_FLASK_BRIGHT, outline=None)
        # Flask neck
        draw.rectangle([flask_x - 1, flask_y - 4, flask_x + 1, flask_y],
                       fill=ALC_FLASK, outline=OUTLINE)
        # Liquid inside
        ellipse(draw, flask_x, flask_y + 5, 3, 3, ALC_FLASK_LIQUID, outline=None)
        # Bubbles
        draw.point((flask_x - 1, flask_y + 2 + bubble_offset), fill=ALC_FLASK_BUBBLE)
        draw.point((flask_x + 1, flask_y + bubble_offset), fill=ALC_FLASK_BUBBLE)

        # Head
        ellipse(draw, cx, head_cy, 14, 13, ALC_HAIR)
        ellipse(draw, cx, head_cy + 2, 12, 10, ALC_SKIN)
        ellipse(draw, cx + 2, head_cy + 4, 8, 6, ALC_SKIN_DARK, outline=None)
        # Eyes
        draw.rectangle([cx - 6, head_cy + 1, cx - 3, head_cy + 4], fill=(255, 255, 255))
        draw.rectangle([cx - 5, head_cy + 2, cx - 4, head_cy + 3], fill=BLACK)
        draw.rectangle([cx + 3, head_cy + 1, cx + 6, head_cy + 4], fill=(255, 255, 255))
        draw.rectangle([cx + 4, head_cy + 2, cx + 5, head_cy + 3], fill=BLACK)
        # Nose and mouth
        draw.point((cx, head_cy + 6), fill=ALC_SKIN_DARK)
        draw.line([(cx - 2, head_cy + 8), (cx + 2, head_cy + 8)],
                  fill=ALC_SKIN_DARK, width=1)
        # Goggles on forehead
        draw.rectangle([cx - 8, head_cy - 6, cx + 8, head_cy - 2],
                       fill=ALC_GOGGLES, outline=OUTLINE)
        ellipse(draw, cx - 4, head_cy - 4, 3, 2, ALC_GOGGLES_LENS)
        draw.point((cx - 5, head_cy - 5), fill=ALC_GOGGLES_LENS_BRIGHT)
        ellipse(draw, cx + 4, head_cy - 4, 3, 2, ALC_GOGGLES_LENS)
        draw.point((cx + 3, head_cy - 5), fill=ALC_GOGGLES_LENS_BRIGHT)
        # Explosion residue on coat
        draw.point((cx - 12, body_cy - 2), fill=ALC_RESIDUE)
        draw.point((cx + 10, body_cy + 1), fill=ALC_RESIDUE)
        draw.point((cx - 8, body_cy - 6), fill=ALC_RESIDUE2)

    elif direction == UP:
        # Legs
        draw.rectangle([cx - 10 + leg_spread, body_cy + 10,
                        cx - 4 + leg_spread, base_y], fill=ALC_LEG, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, body_cy + 10,
                        cx + 10 - leg_spread, base_y], fill=ALC_LEG, outline=OUTLINE)
        draw.rectangle([cx - 10 + leg_spread, base_y - 5,
                        cx - 4 + leg_spread, base_y], fill=ALC_BOOT, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, base_y - 5,
                        cx + 10 - leg_spread, base_y], fill=ALC_BOOT, outline=OUTLINE)

        # Lab coat body (back)
        ellipse(draw, cx, body_cy, 14, 12, ALC_COAT)
        ellipse(draw, cx, body_cy, 10, 9, ALC_COAT_DARK, outline=None)
        # Back seam
        draw.line([(cx, body_cy - 6), (cx, body_cy + 6)],
                  fill=_darken(ALC_COAT, 0.7), width=1)
        # Apron ties visible from back
        draw.line([(cx - 6, body_cy + 4), (cx - 10, body_cy + 8)],
                  fill=ALC_APRON_DARK, width=2)
        draw.line([(cx + 6, body_cy + 4), (cx + 10, body_cy + 8)],
                  fill=ALC_APRON_DARK, width=2)
        # Belt
        draw.rectangle([cx - 14, body_cy + 10, cx + 14, body_cy + 14],
                       fill=ALC_BELT, outline=OUTLINE)

        # Arms
        draw.rectangle([cx - 18, body_cy - 6, cx - 12, body_cy + 6],
                       fill=ALC_COAT, outline=OUTLINE)
        draw.rectangle([cx + 12, body_cy - 6, cx + 18, body_cy + 6],
                       fill=ALC_COAT, outline=OUTLINE)
        ellipse(draw, cx - 14, body_cy - 6, 5, 3, ALC_COAT_LIGHT)
        ellipse(draw, cx + 14, body_cy - 6, 5, 3, ALC_COAT_LIGHT)

        # Head (back)
        ellipse(draw, cx, head_cy, 14, 13, ALC_HAIR)
        ellipse(draw, cx, head_cy - 2, 10, 8, _darken(ALC_HAIR, 0.85), outline=None)
        # Goggles strap visible from back
        draw.line([(cx - 10, head_cy - 4), (cx + 10, head_cy - 4)],
                  fill=ALC_GOGGLES, width=2)
        # Residue
        draw.point((cx + 8, body_cy - 4), fill=ALC_RESIDUE)

    elif direction == LEFT:
        # Legs
        draw.rectangle([cx - 2 - leg_spread, body_cy + 10,
                        cx + 4 - leg_spread, base_y],
                       fill=_darken(ALC_LEG, 0.85), outline=OUTLINE)
        draw.rectangle([cx - 2 - leg_spread, base_y - 5,
                        cx + 4 - leg_spread, base_y], fill=ALC_BOOT, outline=OUTLINE)
        draw.rectangle([cx - 8 + leg_spread, body_cy + 10,
                        cx - 2 + leg_spread, base_y], fill=ALC_LEG, outline=OUTLINE)
        draw.rectangle([cx - 8 + leg_spread, base_y - 5,
                        cx - 2 + leg_spread, base_y], fill=ALC_BOOT, outline=OUTLINE)

        # Lab coat body
        ellipse(draw, cx - 2, body_cy, 12, 12, ALC_COAT)
        ellipse(draw, cx - 4, body_cy - 2, 7, 7, ALC_COAT_LIGHT, outline=None)
        # Apron (side view)
        draw.rectangle([cx - 8, body_cy + 2, cx + 4, body_cy + 12],
                       fill=ALC_APRON, outline=OUTLINE)
        draw.point((cx - 4, body_cy + 6), fill=ALC_APRON_STAIN)
        # Belt
        draw.rectangle([cx - 14, body_cy + 10, cx + 10, body_cy + 14],
                       fill=ALC_BELT, outline=OUTLINE)
        # Vial on belt
        draw.rectangle([cx - 8, body_cy + 6, cx - 6, body_cy + 10],
                       fill=ALC_VIAL_RED, outline=OUTLINE)

        # Front arm with flask
        draw.rectangle([cx - 14, body_cy - 4, cx - 8, body_cy + 6],
                       fill=ALC_COAT, outline=OUTLINE)
        draw.rectangle([cx - 14, body_cy + 2, cx - 8, body_cy + 6],
                       fill=ALC_SKIN, outline=OUTLINE)
        ellipse(draw, cx - 10, body_cy - 6, 5, 3, ALC_COAT_LIGHT)
        # Flask held in front
        flask_x = cx - 16
        flask_y = body_cy
        ellipse(draw, flask_x, flask_y + 4, 3, 4, ALC_FLASK)
        ellipse(draw, flask_x - 1, flask_y + 3, 2, 2, ALC_FLASK_BRIGHT, outline=None)
        draw.rectangle([flask_x - 1, flask_y - 2, flask_x + 1, flask_y + 1],
                       fill=ALC_FLASK, outline=OUTLINE)
        ellipse(draw, flask_x, flask_y + 5, 2, 2, ALC_FLASK_LIQUID, outline=None)
        draw.point((flask_x, flask_y + 1 + bubble_offset), fill=ALC_FLASK_BUBBLE)

        # Head
        ellipse(draw, cx - 2, head_cy, 13, 13, ALC_HAIR)
        ellipse(draw, cx - 4, head_cy + 2, 10, 9, ALC_SKIN)
        ellipse(draw, cx - 2, head_cy + 4, 6, 5, ALC_SKIN_DARK, outline=None)
        # Eye
        draw.rectangle([cx - 10, head_cy + 1, cx - 7, head_cy + 4], fill=(255, 255, 255))
        draw.rectangle([cx - 9, head_cy + 2, cx - 8, head_cy + 3], fill=BLACK)
        # Nose and mouth
        draw.point((cx - 9, head_cy + 6), fill=ALC_SKIN_DARK)
        draw.line([(cx - 10, head_cy + 8), (cx - 7, head_cy + 8)],
                  fill=ALC_SKIN_DARK, width=1)
        # Goggles on forehead
        draw.rectangle([cx - 8, head_cy - 6, cx + 4, head_cy - 2],
                       fill=ALC_GOGGLES, outline=OUTLINE)
        ellipse(draw, cx - 4, head_cy - 4, 3, 2, ALC_GOGGLES_LENS)
        draw.point((cx - 5, head_cy - 5), fill=ALC_GOGGLES_LENS_BRIGHT)
        # Residue
        draw.point((cx - 10, body_cy - 3), fill=ALC_RESIDUE)

    else:  # RIGHT
        # Legs
        draw.rectangle([cx - 2 + leg_spread, body_cy + 10,
                        cx + 4 + leg_spread, base_y],
                       fill=_darken(ALC_LEG, 0.85), outline=OUTLINE)
        draw.rectangle([cx - 2 + leg_spread, base_y - 5,
                        cx + 4 + leg_spread, base_y], fill=ALC_BOOT, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, body_cy + 10,
                        cx + 10 - leg_spread, base_y], fill=ALC_LEG, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, base_y - 5,
                        cx + 10 - leg_spread, base_y], fill=ALC_BOOT, outline=OUTLINE)

        # Lab coat body
        ellipse(draw, cx + 2, body_cy, 12, 12, ALC_COAT)
        ellipse(draw, cx, body_cy - 2, 7, 7, ALC_COAT_LIGHT, outline=None)
        # Apron
        draw.rectangle([cx - 4, body_cy + 2, cx + 8, body_cy + 12],
                       fill=ALC_APRON, outline=OUTLINE)
        draw.point((cx + 4, body_cy + 6), fill=ALC_APRON_STAIN)
        # Belt
        draw.rectangle([cx - 10, body_cy + 10, cx + 14, body_cy + 14],
                       fill=ALC_BELT, outline=OUTLINE)
        draw.rectangle([cx + 6, body_cy + 6, cx + 8, body_cy + 10],
                       fill=ALC_VIAL_BLUE, outline=OUTLINE)

        # Front arm with flask
        draw.rectangle([cx + 8, body_cy - 4, cx + 14, body_cy + 6],
                       fill=ALC_COAT, outline=OUTLINE)
        draw.rectangle([cx + 8, body_cy + 2, cx + 14, body_cy + 6],
                       fill=ALC_SKIN, outline=OUTLINE)
        ellipse(draw, cx + 10, body_cy - 6, 5, 3, ALC_COAT_LIGHT)
        # Flask
        flask_x = cx + 16
        flask_y = body_cy
        ellipse(draw, flask_x, flask_y + 4, 3, 4, ALC_FLASK)
        ellipse(draw, flask_x + 1, flask_y + 3, 2, 2, ALC_FLASK_BRIGHT, outline=None)
        draw.rectangle([flask_x - 1, flask_y - 2, flask_x + 1, flask_y + 1],
                       fill=ALC_FLASK, outline=OUTLINE)
        ellipse(draw, flask_x, flask_y + 5, 2, 2, ALC_FLASK_LIQUID, outline=None)
        draw.point((flask_x, flask_y + 1 + bubble_offset), fill=ALC_FLASK_BUBBLE)

        # Head
        ellipse(draw, cx + 2, head_cy, 13, 13, ALC_HAIR)
        ellipse(draw, cx + 4, head_cy + 2, 10, 9, ALC_SKIN)
        ellipse(draw, cx + 6, head_cy + 4, 6, 5, ALC_SKIN_DARK, outline=None)
        # Eye
        draw.rectangle([cx + 7, head_cy + 1, cx + 10, head_cy + 4], fill=(255, 255, 255))
        draw.rectangle([cx + 8, head_cy + 2, cx + 9, head_cy + 3], fill=BLACK)
        # Nose and mouth
        draw.point((cx + 9, head_cy + 6), fill=ALC_SKIN_DARK)
        draw.line([(cx + 7, head_cy + 8), (cx + 10, head_cy + 8)],
                  fill=ALC_SKIN_DARK, width=1)
        # Goggles
        draw.rectangle([cx - 4, head_cy - 6, cx + 8, head_cy - 2],
                       fill=ALC_GOGGLES, outline=OUTLINE)
        ellipse(draw, cx + 4, head_cy - 4, 3, 2, ALC_GOGGLES_LENS)
        draw.point((cx + 3, head_cy - 5), fill=ALC_GOGGLES_LENS_BRIGHT)
        # Residue
        draw.point((cx + 10, body_cy - 3), fill=ALC_RESIDUE)


# ===================================================================
# PUPPETEER (ID 103)
# Dark purple outfit, puppet strings from fingers upward, marionette,
# theatrical mask on hip, control bar held overhead.
# ===================================================================

def draw_puppeteer(draw, ox, oy, direction, frame):
    bob = [0, -2, 0, -1][frame]
    leg_spread = [-4, 0, 4, 0][frame]
    puppet_sway = [-2, 0, 2, 0][frame]

    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 20
    head_cy = body_cy - 20

    if direction == DOWN:
        # Legs
        draw.rectangle([cx - 10 + leg_spread, body_cy + 10,
                        cx - 4 + leg_spread, base_y], fill=PUP_LEG, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, body_cy + 10,
                        cx + 10 - leg_spread, base_y], fill=PUP_LEG, outline=OUTLINE)
        draw.rectangle([cx - 10 + leg_spread, base_y - 5,
                        cx - 4 + leg_spread, base_y], fill=PUP_BOOT, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, base_y - 5,
                        cx + 10 - leg_spread, base_y], fill=PUP_BOOT, outline=OUTLINE)

        # Body
        ellipse(draw, cx, body_cy, 14, 12, PUP_COAT)
        ellipse(draw, cx - 3, body_cy - 2, 8, 7, PUP_COAT_LIGHT, outline=None)
        # Decorative seam
        draw.line([(cx, body_cy - 8), (cx, body_cy + 8)],
                  fill=PUP_COAT_DARK, width=1)
        # Belt
        draw.rectangle([cx - 14, body_cy + 8, cx + 14, body_cy + 12],
                       fill=PUP_COAT_DARK, outline=OUTLINE)
        # Theatrical mask on belt (right hip)
        ellipse(draw, cx + 10, body_cy + 6, 4, 5, PUP_MASK)
        draw.point((cx + 9, body_cy + 5), fill=PUP_MASK_DARK)
        draw.point((cx + 11, body_cy + 5), fill=PUP_MASK_DARK)
        draw.arc([cx + 8, body_cy + 5, cx + 12, body_cy + 9],
                 start=0, end=180, fill=PUP_MASK_ACCENT, width=1)

        # Arms (raised, holding control bar)
        # Left arm raised
        draw.rectangle([cx - 18, body_cy - 14, cx - 12, body_cy - 4],
                       fill=PUP_COAT, outline=OUTLINE)
        draw.rectangle([cx - 18, body_cy - 14, cx - 12, body_cy - 10],
                       fill=PUP_GLOVE, outline=OUTLINE)
        # Right arm raised
        draw.rectangle([cx + 12, body_cy - 14, cx + 18, body_cy - 4],
                       fill=PUP_COAT, outline=OUTLINE)
        draw.rectangle([cx + 12, body_cy - 14, cx + 18, body_cy - 10],
                       fill=PUP_GLOVE, outline=OUTLINE)
        # Control bar (horizontal, above hands)
        draw.rectangle([cx - 16, body_cy - 17, cx + 16, body_cy - 14],
                       fill=PUP_BAR, outline=OUTLINE)
        draw.line([(cx - 14, body_cy - 16), (cx + 14, body_cy - 16)],
                  fill=_brighten(PUP_BAR, 1.2), width=1)

        # Puppet strings (from bar downward to puppet)
        puppet_cx = cx + puppet_sway
        puppet_y = body_cy + 20
        draw.line([(cx - 8, body_cy - 14), (puppet_cx - 4, puppet_y - 6)],
                  fill=PUP_STRING, width=1)
        draw.line([(cx, body_cy - 14), (puppet_cx, puppet_y - 8)],
                  fill=PUP_STRING, width=1)
        draw.line([(cx + 8, body_cy - 14), (puppet_cx + 4, puppet_y - 6)],
                  fill=PUP_STRING, width=1)
        draw.line([(cx - 4, body_cy - 14), (puppet_cx - 2, puppet_y + 2)],
                  fill=PUP_STRING_DIM, width=1)
        draw.line([(cx + 4, body_cy - 14), (puppet_cx + 2, puppet_y + 2)],
                  fill=PUP_STRING_DIM, width=1)

        # Small marionette puppet
        ellipse(draw, puppet_cx, puppet_y - 6, 3, 3, PUP_PUPPET_HEAD)
        draw.point((puppet_cx - 1, puppet_y - 7), fill=PUP_PUPPET_EYE)
        draw.point((puppet_cx + 1, puppet_y - 7), fill=PUP_PUPPET_EYE)
        draw.rectangle([puppet_cx - 3, puppet_y - 3, puppet_cx + 3, puppet_y + 3],
                       fill=PUP_PUPPET_BODY, outline=OUTLINE)
        # Puppet limbs
        draw.line([(puppet_cx - 3, puppet_y - 1), (puppet_cx - 6, puppet_y + 3)],
                  fill=PUP_PUPPET_BODY, width=1)
        draw.line([(puppet_cx + 3, puppet_y - 1), (puppet_cx + 6, puppet_y + 3)],
                  fill=PUP_PUPPET_BODY, width=1)

        # Head
        ellipse(draw, cx, head_cy, 14, 13, PUP_HAIR)
        ellipse(draw, cx, head_cy + 2, 11, 9, PUP_SKIN)
        ellipse(draw, cx + 2, head_cy + 4, 7, 5, PUP_SKIN_DARK, outline=None)
        # Eyes (dramatic, theatrical)
        draw.rectangle([cx - 6, head_cy + 1, cx - 3, head_cy + 4], fill=(255, 255, 255))
        draw.rectangle([cx - 5, head_cy + 2, cx - 4, head_cy + 3], fill=(60, 30, 80))
        draw.rectangle([cx + 3, head_cy + 1, cx + 6, head_cy + 4], fill=(255, 255, 255))
        draw.rectangle([cx + 4, head_cy + 2, cx + 5, head_cy + 3], fill=(60, 30, 80))
        # Smirk
        draw.arc([cx - 3, head_cy + 6, cx + 3, head_cy + 10],
                 start=0, end=180, fill=PUP_SKIN_DARK, width=1)

    elif direction == UP:
        # Legs
        draw.rectangle([cx - 10 + leg_spread, body_cy + 10,
                        cx - 4 + leg_spread, base_y], fill=PUP_LEG, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, body_cy + 10,
                        cx + 10 - leg_spread, base_y], fill=PUP_LEG, outline=OUTLINE)
        draw.rectangle([cx - 10 + leg_spread, base_y - 5,
                        cx - 4 + leg_spread, base_y], fill=PUP_BOOT, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, base_y - 5,
                        cx + 10 - leg_spread, base_y], fill=PUP_BOOT, outline=OUTLINE)

        # Body (back)
        ellipse(draw, cx, body_cy, 14, 12, PUP_COAT)
        ellipse(draw, cx, body_cy, 10, 9, PUP_COAT_DARK, outline=None)
        draw.line([(cx, body_cy - 6), (cx, body_cy + 6)],
                  fill=_darken(PUP_COAT, 0.65), width=1)
        draw.rectangle([cx - 14, body_cy + 8, cx + 14, body_cy + 12],
                       fill=PUP_COAT_DARK, outline=OUTLINE)

        # Arms raised
        draw.rectangle([cx - 18, body_cy - 14, cx - 12, body_cy - 4],
                       fill=PUP_COAT, outline=OUTLINE)
        draw.rectangle([cx + 12, body_cy - 14, cx + 18, body_cy - 4],
                       fill=PUP_COAT, outline=OUTLINE)
        # Control bar
        draw.rectangle([cx - 16, body_cy - 17, cx + 16, body_cy - 14],
                       fill=PUP_BAR, outline=OUTLINE)
        # Strings downward (behind body in UP view)
        puppet_cx = cx + puppet_sway
        puppet_y = body_cy + 22
        draw.line([(cx - 6, body_cy - 14), (puppet_cx - 3, puppet_y - 4)],
                  fill=PUP_STRING_DIM, width=1)
        draw.line([(cx + 6, body_cy - 14), (puppet_cx + 3, puppet_y - 4)],
                  fill=PUP_STRING_DIM, width=1)

        # Head (back)
        ellipse(draw, cx, head_cy, 14, 13, PUP_HAIR)
        ellipse(draw, cx, head_cy - 2, 10, 8, _darken(PUP_HAIR, 0.8), outline=None)

    elif direction == LEFT:
        # Legs
        draw.rectangle([cx - 2 - leg_spread, body_cy + 10,
                        cx + 4 - leg_spread, base_y],
                       fill=_darken(PUP_LEG, 0.85), outline=OUTLINE)
        draw.rectangle([cx - 2 - leg_spread, base_y - 5,
                        cx + 4 - leg_spread, base_y], fill=PUP_BOOT, outline=OUTLINE)
        draw.rectangle([cx - 8 + leg_spread, body_cy + 10,
                        cx - 2 + leg_spread, base_y], fill=PUP_LEG, outline=OUTLINE)
        draw.rectangle([cx - 8 + leg_spread, base_y - 5,
                        cx - 2 + leg_spread, base_y], fill=PUP_BOOT, outline=OUTLINE)

        # Body
        ellipse(draw, cx - 2, body_cy, 12, 12, PUP_COAT)
        ellipse(draw, cx - 4, body_cy - 2, 7, 7, PUP_COAT_LIGHT, outline=None)
        draw.rectangle([cx - 14, body_cy + 8, cx + 10, body_cy + 12],
                       fill=PUP_COAT_DARK, outline=OUTLINE)
        # Mask on hip (back side)
        ellipse(draw, cx + 6, body_cy + 6, 3, 4, PUP_MASK)
        draw.point((cx + 5, body_cy + 5), fill=PUP_MASK_DARK)

        # Front arm raised with bar
        draw.rectangle([cx - 14, body_cy - 14, cx - 8, body_cy - 4],
                       fill=PUP_COAT, outline=OUTLINE)
        draw.rectangle([cx - 14, body_cy - 14, cx - 8, body_cy - 10],
                       fill=PUP_GLOVE, outline=OUTLINE)
        # Control bar
        draw.rectangle([cx - 16, body_cy - 17, cx + 2, body_cy - 14],
                       fill=PUP_BAR, outline=OUTLINE)
        # Strings and puppet
        puppet_cx = cx - 10 + puppet_sway
        puppet_y = body_cy + 16
        draw.line([(cx - 12, body_cy - 14), (puppet_cx, puppet_y - 6)],
                  fill=PUP_STRING, width=1)
        draw.line([(cx - 6, body_cy - 14), (puppet_cx, puppet_y - 2)],
                  fill=PUP_STRING, width=1)
        # Puppet
        ellipse(draw, puppet_cx, puppet_y - 5, 2, 3, PUP_PUPPET_HEAD)
        draw.point((puppet_cx - 1, puppet_y - 6), fill=PUP_PUPPET_EYE)
        draw.rectangle([puppet_cx - 2, puppet_y - 2, puppet_cx + 2, puppet_y + 2],
                       fill=PUP_PUPPET_BODY, outline=OUTLINE)

        # Head
        ellipse(draw, cx - 2, head_cy, 13, 13, PUP_HAIR)
        ellipse(draw, cx - 4, head_cy + 2, 10, 9, PUP_SKIN)
        ellipse(draw, cx - 2, head_cy + 4, 6, 5, PUP_SKIN_DARK, outline=None)
        # Eye
        draw.rectangle([cx - 10, head_cy + 1, cx - 7, head_cy + 4], fill=(255, 255, 255))
        draw.rectangle([cx - 9, head_cy + 2, cx - 8, head_cy + 3], fill=(60, 30, 80))
        draw.arc([cx - 8, head_cy + 6, cx - 4, head_cy + 10],
                 start=0, end=180, fill=PUP_SKIN_DARK, width=1)

    else:  # RIGHT
        # Legs
        draw.rectangle([cx - 2 + leg_spread, body_cy + 10,
                        cx + 4 + leg_spread, base_y],
                       fill=_darken(PUP_LEG, 0.85), outline=OUTLINE)
        draw.rectangle([cx - 2 + leg_spread, base_y - 5,
                        cx + 4 + leg_spread, base_y], fill=PUP_BOOT, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, body_cy + 10,
                        cx + 10 - leg_spread, base_y], fill=PUP_LEG, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, base_y - 5,
                        cx + 10 - leg_spread, base_y], fill=PUP_BOOT, outline=OUTLINE)

        # Body
        ellipse(draw, cx + 2, body_cy, 12, 12, PUP_COAT)
        ellipse(draw, cx, body_cy - 2, 7, 7, PUP_COAT_LIGHT, outline=None)
        draw.rectangle([cx - 10, body_cy + 8, cx + 14, body_cy + 12],
                       fill=PUP_COAT_DARK, outline=OUTLINE)
        # Mask on hip
        ellipse(draw, cx - 6, body_cy + 6, 3, 4, PUP_MASK)
        draw.point((cx - 5, body_cy + 5), fill=PUP_MASK_DARK)

        # Front arm raised with bar
        draw.rectangle([cx + 8, body_cy - 14, cx + 14, body_cy - 4],
                       fill=PUP_COAT, outline=OUTLINE)
        draw.rectangle([cx + 8, body_cy - 14, cx + 14, body_cy - 10],
                       fill=PUP_GLOVE, outline=OUTLINE)
        # Control bar
        draw.rectangle([cx - 2, body_cy - 17, cx + 16, body_cy - 14],
                       fill=PUP_BAR, outline=OUTLINE)
        # Strings and puppet
        puppet_cx = cx + 10 + puppet_sway
        puppet_y = body_cy + 16
        draw.line([(cx + 12, body_cy - 14), (puppet_cx, puppet_y - 6)],
                  fill=PUP_STRING, width=1)
        draw.line([(cx + 6, body_cy - 14), (puppet_cx, puppet_y - 2)],
                  fill=PUP_STRING, width=1)
        # Puppet
        ellipse(draw, puppet_cx, puppet_y - 5, 2, 3, PUP_PUPPET_HEAD)
        draw.point((puppet_cx + 1, puppet_y - 6), fill=PUP_PUPPET_EYE)
        draw.rectangle([puppet_cx - 2, puppet_y - 2, puppet_cx + 2, puppet_y + 2],
                       fill=PUP_PUPPET_BODY, outline=OUTLINE)

        # Head
        ellipse(draw, cx + 2, head_cy, 13, 13, PUP_HAIR)
        ellipse(draw, cx + 4, head_cy + 2, 10, 9, PUP_SKIN)
        ellipse(draw, cx + 6, head_cy + 4, 6, 5, PUP_SKIN_DARK, outline=None)
        # Eye
        draw.rectangle([cx + 7, head_cy + 1, cx + 10, head_cy + 4], fill=(255, 255, 255))
        draw.rectangle([cx + 8, head_cy + 2, cx + 9, head_cy + 3], fill=(60, 30, 80))
        draw.arc([cx + 4, head_cy + 6, cx + 8, head_cy + 10],
                 start=0, end=180, fill=PUP_SKIN_DARK, width=1)


# ===================================================================
# GAMBLER (ID 104)
# Black formal suit, top hat, playing cards fanned in hand, gold pocket
# watch chain, red vest, dice on belt.
# ===================================================================

def draw_gambler(draw, ox, oy, direction, frame):
    bob = [0, -2, 0, -1][frame]
    leg_spread = [-4, 0, 4, 0][frame]
    card_fan = [-1, 0, 1, 0][frame]

    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 20
    head_cy = body_cy - 20

    if direction == DOWN:
        # Legs
        draw.rectangle([cx - 10 + leg_spread, body_cy + 10,
                        cx - 4 + leg_spread, base_y], fill=GAM_LEG, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, body_cy + 10,
                        cx + 10 - leg_spread, base_y], fill=GAM_LEG, outline=OUTLINE)
        draw.rectangle([cx - 10 + leg_spread, base_y - 5,
                        cx - 4 + leg_spread, base_y], fill=GAM_BOOT, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, base_y - 5,
                        cx + 10 - leg_spread, base_y], fill=GAM_BOOT, outline=OUTLINE)

        # Suit body
        ellipse(draw, cx, body_cy, 14, 12, GAM_SUIT)
        ellipse(draw, cx - 3, body_cy - 2, 8, 7, GAM_SUIT_LIGHT, outline=None)
        # Red vest visible under jacket
        draw.polygon([(cx - 6, body_cy - 6), (cx + 6, body_cy - 6),
                      (cx + 4, body_cy + 8), (cx - 4, body_cy + 8)],
                     fill=GAM_VEST, outline=None)
        draw.line([(cx, body_cy - 6), (cx, body_cy + 8)],
                  fill=GAM_VEST_DARK, width=1)
        # Vest buttons
        draw.point((cx, body_cy - 2), fill=GAM_CHAIN)
        draw.point((cx, body_cy + 2), fill=GAM_CHAIN)
        # Jacket lapels
        draw.line([(cx - 2, body_cy - 8), (cx - 6, body_cy + 2)],
                  fill=GAM_SUIT_DARK, width=2)
        draw.line([(cx + 2, body_cy - 8), (cx + 6, body_cy + 2)],
                  fill=GAM_SUIT_DARK, width=2)
        # Belt with dice
        draw.rectangle([cx - 14, body_cy + 8, cx + 14, body_cy + 12],
                       fill=GAM_SUIT_DARK, outline=OUTLINE)
        # Dice on belt
        draw.rectangle([cx + 6, body_cy + 5, cx + 10, body_cy + 9],
                       fill=GAM_DICE_WHITE, outline=OUTLINE)
        draw.point((cx + 7, body_cy + 6), fill=GAM_DICE_DOT)
        draw.point((cx + 9, body_cy + 8), fill=GAM_DICE_DOT)
        # Pocket watch chain (curved line from vest to pocket)
        draw.arc([cx - 8, body_cy + 2, cx + 2, body_cy + 10],
                 start=180, end=360, fill=GAM_CHAIN, width=1)
        draw.point((cx - 8, body_cy + 6), fill=GAM_CHAIN_BRIGHT)

        # Arms
        draw.rectangle([cx - 18, body_cy - 6, cx - 12, body_cy + 6],
                       fill=GAM_SUIT, outline=OUTLINE)
        draw.rectangle([cx - 18, body_cy + 2, cx - 12, body_cy + 6],
                       fill=GAM_SKIN, outline=OUTLINE)
        draw.rectangle([cx + 12, body_cy - 6, cx + 18, body_cy + 6],
                       fill=GAM_SUIT, outline=OUTLINE)
        draw.rectangle([cx + 12, body_cy + 2, cx + 18, body_cy + 6],
                       fill=GAM_SKIN, outline=OUTLINE)
        ellipse(draw, cx - 14, body_cy - 6, 5, 3, GAM_SUIT_LIGHT)
        ellipse(draw, cx + 14, body_cy - 6, 5, 3, GAM_SUIT_LIGHT)

        # Playing cards fanned in right hand
        card_x = cx + 19
        card_y = body_cy + 2
        # Three cards fanned
        for i, angle_off in enumerate([-4 + card_fan, 0, 4 - card_fan]):
            cx_c = card_x + angle_off
            draw.rectangle([cx_c - 2, card_y - 6, cx_c + 2, card_y + 2],
                           fill=GAM_CARD_WHITE, outline=OUTLINE)
        # Card suit symbols
        draw.point((card_x - 4 + card_fan, card_y - 3), fill=GAM_CARD_RED)
        draw.point((card_x, card_y - 4), fill=GAM_CARD_BLACK)
        draw.point((card_x + 4 - card_fan, card_y - 3), fill=GAM_CARD_RED)

        # Head
        ellipse(draw, cx, head_cy, 14, 13, GAM_HAIR)
        ellipse(draw, cx, head_cy + 2, 12, 10, GAM_SKIN)
        ellipse(draw, cx + 2, head_cy + 4, 8, 6, GAM_SKIN_DARK, outline=None)
        # Eyes
        draw.rectangle([cx - 6, head_cy + 1, cx - 3, head_cy + 4], fill=(255, 255, 255))
        draw.rectangle([cx - 5, head_cy + 2, cx - 4, head_cy + 3], fill=BLACK)
        draw.rectangle([cx + 3, head_cy + 1, cx + 6, head_cy + 4], fill=(255, 255, 255))
        draw.rectangle([cx + 4, head_cy + 2, cx + 5, head_cy + 3], fill=BLACK)
        # Sly grin
        draw.arc([cx - 4, head_cy + 6, cx + 4, head_cy + 10],
                 start=10, end=170, fill=GAM_SKIN_DARK, width=1)

        # Top hat
        draw.rectangle([cx - 16, head_cy - 10, cx + 16, head_cy - 6],
                       fill=GAM_HAT, outline=OUTLINE)
        draw.rectangle([cx - 10, head_cy - 28, cx + 10, head_cy - 10],
                       fill=GAM_HAT, outline=OUTLINE)
        draw.rectangle([cx + 4, head_cy - 27, cx + 9, head_cy - 11],
                       fill=GAM_HAT_DARK, outline=None)
        draw.rectangle([cx - 10, head_cy - 14, cx + 10, head_cy - 10],
                       fill=GAM_HAT_BAND, outline=None)
        draw.line([(cx - 6, head_cy - 24), (cx - 6, head_cy - 16)],
                  fill=GAM_HAT_LIGHT, width=1)

    elif direction == UP:
        # Legs
        draw.rectangle([cx - 10 + leg_spread, body_cy + 10,
                        cx - 4 + leg_spread, base_y], fill=GAM_LEG, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, body_cy + 10,
                        cx + 10 - leg_spread, base_y], fill=GAM_LEG, outline=OUTLINE)
        draw.rectangle([cx - 10 + leg_spread, base_y - 5,
                        cx - 4 + leg_spread, base_y], fill=GAM_BOOT, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, base_y - 5,
                        cx + 10 - leg_spread, base_y], fill=GAM_BOOT, outline=OUTLINE)

        # Suit body (back)
        ellipse(draw, cx, body_cy, 14, 12, GAM_SUIT)
        ellipse(draw, cx, body_cy, 10, 9, GAM_SUIT_DARK, outline=None)
        draw.line([(cx, body_cy - 6), (cx, body_cy + 6)],
                  fill=_darken(GAM_SUIT, 0.6), width=1)
        # Coattails
        draw.rectangle([cx - 14, body_cy + 8, cx + 14, body_cy + 12],
                       fill=GAM_SUIT_DARK, outline=OUTLINE)
        # Arms
        draw.rectangle([cx - 18, body_cy - 6, cx - 12, body_cy + 6],
                       fill=GAM_SUIT, outline=OUTLINE)
        draw.rectangle([cx + 12, body_cy - 6, cx + 18, body_cy + 6],
                       fill=GAM_SUIT, outline=OUTLINE)
        ellipse(draw, cx - 14, body_cy - 6, 5, 3, GAM_SUIT_LIGHT)
        ellipse(draw, cx + 14, body_cy - 6, 5, 3, GAM_SUIT_LIGHT)

        # Head (back)
        ellipse(draw, cx, head_cy, 14, 13, GAM_HAIR)
        ellipse(draw, cx, head_cy - 2, 10, 8, _darken(GAM_HAIR, 0.8), outline=None)
        # Top hat
        draw.rectangle([cx - 16, head_cy - 10, cx + 16, head_cy - 6],
                       fill=GAM_HAT, outline=OUTLINE)
        draw.rectangle([cx - 10, head_cy - 28, cx + 10, head_cy - 10],
                       fill=GAM_HAT, outline=OUTLINE)
        draw.rectangle([cx + 4, head_cy - 27, cx + 9, head_cy - 11],
                       fill=GAM_HAT_DARK, outline=None)
        draw.rectangle([cx - 10, head_cy - 14, cx + 10, head_cy - 10],
                       fill=GAM_HAT_BAND, outline=None)

    elif direction == LEFT:
        # Legs
        draw.rectangle([cx - 2 - leg_spread, body_cy + 10,
                        cx + 4 - leg_spread, base_y],
                       fill=_darken(GAM_LEG, 0.85), outline=OUTLINE)
        draw.rectangle([cx - 2 - leg_spread, base_y - 5,
                        cx + 4 - leg_spread, base_y], fill=GAM_BOOT, outline=OUTLINE)
        draw.rectangle([cx - 8 + leg_spread, body_cy + 10,
                        cx - 2 + leg_spread, base_y], fill=GAM_LEG, outline=OUTLINE)
        draw.rectangle([cx - 8 + leg_spread, base_y - 5,
                        cx - 2 + leg_spread, base_y], fill=GAM_BOOT, outline=OUTLINE)

        # Body
        ellipse(draw, cx - 2, body_cy, 12, 12, GAM_SUIT)
        ellipse(draw, cx - 4, body_cy - 2, 7, 7, GAM_SUIT_LIGHT, outline=None)
        # Vest visible
        draw.polygon([(cx - 6, body_cy - 4), (cx + 2, body_cy - 4),
                      (cx + 1, body_cy + 6), (cx - 5, body_cy + 6)],
                     fill=GAM_VEST, outline=None)
        draw.line([(cx - 2, body_cy - 4), (cx - 2, body_cy + 6)],
                  fill=GAM_VEST_DARK, width=1)
        # Watch chain
        draw.arc([cx - 8, body_cy + 2, cx, body_cy + 8],
                 start=180, end=360, fill=GAM_CHAIN, width=1)
        # Belt
        draw.rectangle([cx - 14, body_cy + 8, cx + 10, body_cy + 12],
                       fill=GAM_SUIT_DARK, outline=OUTLINE)

        # Front arm with cards
        draw.rectangle([cx - 14, body_cy - 4, cx - 8, body_cy + 6],
                       fill=GAM_SUIT, outline=OUTLINE)
        draw.rectangle([cx - 14, body_cy + 2, cx - 8, body_cy + 6],
                       fill=GAM_SKIN, outline=OUTLINE)
        ellipse(draw, cx - 10, body_cy - 6, 5, 3, GAM_SUIT_LIGHT)
        # Cards
        card_x = cx - 16
        card_y = body_cy
        for i, dy in enumerate([-3 + card_fan, 0, 3 - card_fan]):
            draw.rectangle([card_x - 3, card_y + dy - 3, card_x + 1, card_y + dy + 1],
                           fill=GAM_CARD_WHITE, outline=OUTLINE)
        draw.point((card_x - 1, card_y - 4), fill=GAM_CARD_RED)

        # Head
        ellipse(draw, cx - 2, head_cy, 13, 13, GAM_HAIR)
        ellipse(draw, cx - 4, head_cy + 2, 10, 9, GAM_SKIN)
        ellipse(draw, cx - 2, head_cy + 4, 6, 5, GAM_SKIN_DARK, outline=None)
        # Eye
        draw.rectangle([cx - 10, head_cy + 1, cx - 7, head_cy + 4], fill=(255, 255, 255))
        draw.rectangle([cx - 9, head_cy + 2, cx - 8, head_cy + 3], fill=BLACK)
        draw.arc([cx - 8, head_cy + 6, cx - 4, head_cy + 10],
                 start=10, end=170, fill=GAM_SKIN_DARK, width=1)
        # Top hat
        draw.rectangle([cx - 16, head_cy - 10, cx + 8, head_cy - 6],
                       fill=GAM_HAT, outline=OUTLINE)
        draw.rectangle([cx - 10, head_cy - 28, cx + 4, head_cy - 10],
                       fill=GAM_HAT, outline=OUTLINE)
        draw.rectangle([cx, head_cy - 27, cx + 3, head_cy - 11],
                       fill=GAM_HAT_DARK, outline=None)
        draw.rectangle([cx - 10, head_cy - 14, cx + 4, head_cy - 10],
                       fill=GAM_HAT_BAND, outline=None)
        draw.line([(cx - 6, head_cy - 24), (cx - 6, head_cy - 16)],
                  fill=GAM_HAT_LIGHT, width=1)

    else:  # RIGHT
        # Legs
        draw.rectangle([cx - 2 + leg_spread, body_cy + 10,
                        cx + 4 + leg_spread, base_y],
                       fill=_darken(GAM_LEG, 0.85), outline=OUTLINE)
        draw.rectangle([cx - 2 + leg_spread, base_y - 5,
                        cx + 4 + leg_spread, base_y], fill=GAM_BOOT, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, body_cy + 10,
                        cx + 10 - leg_spread, base_y], fill=GAM_LEG, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, base_y - 5,
                        cx + 10 - leg_spread, base_y], fill=GAM_BOOT, outline=OUTLINE)

        # Body
        ellipse(draw, cx + 2, body_cy, 12, 12, GAM_SUIT)
        ellipse(draw, cx, body_cy - 2, 7, 7, GAM_SUIT_LIGHT, outline=None)
        # Vest visible
        draw.polygon([(cx - 2, body_cy - 4), (cx + 6, body_cy - 4),
                      (cx + 5, body_cy + 6), (cx - 1, body_cy + 6)],
                     fill=GAM_VEST, outline=None)
        draw.line([(cx + 2, body_cy - 4), (cx + 2, body_cy + 6)],
                  fill=GAM_VEST_DARK, width=1)
        # Watch chain
        draw.arc([cx, body_cy + 2, cx + 8, body_cy + 8],
                 start=180, end=360, fill=GAM_CHAIN, width=1)
        # Belt
        draw.rectangle([cx - 10, body_cy + 8, cx + 14, body_cy + 12],
                       fill=GAM_SUIT_DARK, outline=OUTLINE)

        # Front arm with cards
        draw.rectangle([cx + 8, body_cy - 4, cx + 14, body_cy + 6],
                       fill=GAM_SUIT, outline=OUTLINE)
        draw.rectangle([cx + 8, body_cy + 2, cx + 14, body_cy + 6],
                       fill=GAM_SKIN, outline=OUTLINE)
        ellipse(draw, cx + 10, body_cy - 6, 5, 3, GAM_SUIT_LIGHT)
        # Cards
        card_x = cx + 16
        card_y = body_cy
        for i, dy in enumerate([-3 + card_fan, 0, 3 - card_fan]):
            draw.rectangle([card_x - 1, card_y + dy - 3, card_x + 3, card_y + dy + 1],
                           fill=GAM_CARD_WHITE, outline=OUTLINE)
        draw.point((card_x + 1, card_y - 4), fill=GAM_CARD_BLACK)

        # Head
        ellipse(draw, cx + 2, head_cy, 13, 13, GAM_HAIR)
        ellipse(draw, cx + 4, head_cy + 2, 10, 9, GAM_SKIN)
        ellipse(draw, cx + 6, head_cy + 4, 6, 5, GAM_SKIN_DARK, outline=None)
        # Eye
        draw.rectangle([cx + 7, head_cy + 1, cx + 10, head_cy + 4], fill=(255, 255, 255))
        draw.rectangle([cx + 8, head_cy + 2, cx + 9, head_cy + 3], fill=BLACK)
        draw.arc([cx + 4, head_cy + 6, cx + 8, head_cy + 10],
                 start=10, end=170, fill=GAM_SKIN_DARK, width=1)
        # Top hat
        draw.rectangle([cx - 8, head_cy - 10, cx + 16, head_cy - 6],
                       fill=GAM_HAT, outline=OUTLINE)
        draw.rectangle([cx - 4, head_cy - 28, cx + 10, head_cy - 10],
                       fill=GAM_HAT, outline=OUTLINE)
        draw.rectangle([cx + 4, head_cy - 27, cx + 9, head_cy - 11],
                       fill=GAM_HAT_DARK, outline=None)
        draw.rectangle([cx - 4, head_cy - 14, cx + 10, head_cy - 10],
                       fill=GAM_HAT_BAND, outline=None)
        draw.line([(cx, head_cy - 24), (cx, head_cy - 16)],
                  fill=GAM_HAT_LIGHT, width=1)


# ===================================================================
# BLACKSMITH (ID 105)
# Heavy leather apron over muscular frame, hammer, soot marks,
# protective goggles, metal gauntlets, forge glow accents.
# ===================================================================

def draw_blacksmith(draw, ox, oy, direction, frame):
    bob = [0, -2, 0, -1][frame]
    leg_spread = [-4, 0, 4, 0][frame]
    hammer_swing = [0, -2, 0, 2][frame]

    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 20
    head_cy = body_cy - 20

    if direction == DOWN:
        # Legs (stocky)
        draw.rectangle([cx - 10 + leg_spread, body_cy + 10,
                        cx - 4 + leg_spread, base_y], fill=BKS_LEG, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, body_cy + 10,
                        cx + 10 - leg_spread, base_y], fill=BKS_LEG, outline=OUTLINE)
        draw.rectangle([cx - 10 + leg_spread, base_y - 5,
                        cx - 4 + leg_spread, base_y], fill=BKS_BOOT, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, base_y - 5,
                        cx + 10 - leg_spread, base_y], fill=BKS_BOOT, outline=OUTLINE)

        # Broad body (muscular)
        ellipse(draw, cx, body_cy, 15, 13, BKS_BODY)
        ellipse(draw, cx - 3, body_cy - 2, 9, 8, _brighten(BKS_BODY, 1.15), outline=None)
        # Leather apron over chest
        draw.polygon([(cx - 10, body_cy - 6), (cx + 10, body_cy - 6),
                      (cx + 12, body_cy + 12), (cx - 12, body_cy + 12)],
                     fill=BKS_APRON, outline=OUTLINE)
        draw.polygon([(cx - 6, body_cy - 4), (cx + 6, body_cy - 4),
                      (cx + 8, body_cy + 8), (cx - 8, body_cy + 8)],
                     fill=BKS_APRON_LIGHT, outline=None)
        # Soot marks on apron
        draw.point((cx - 4, body_cy + 2), fill=BKS_SOOT)
        draw.point((cx + 3, body_cy + 4), fill=BKS_SOOT)
        draw.point((cx - 2, body_cy + 8), fill=BKS_SOOT)
        draw.point((cx + 6, body_cy), fill=BKS_SOOT)
        # Belt with buckle
        draw.rectangle([cx - 15, body_cy + 10, cx + 15, body_cy + 14],
                       fill=BKS_BELT, outline=OUTLINE)
        draw.rectangle([cx - 3, body_cy + 10, cx + 3, body_cy + 14],
                       fill=BKS_BELT_BUCKLE, outline=OUTLINE)
        draw.point((cx, body_cy + 12), fill=_brighten(BKS_BELT_BUCKLE, 1.3))

        # Arms (thick, muscular with gauntlets)
        draw.rectangle([cx - 20, body_cy - 6, cx - 12, body_cy + 6],
                       fill=BKS_SKIN, outline=OUTLINE)
        # Muscle definition
        draw.line([(cx - 16, body_cy - 4), (cx - 16, body_cy + 4)],
                  fill=BKS_SKIN_DARK, width=1)
        # Left gauntlet
        draw.rectangle([cx - 20, body_cy + 2, cx - 12, body_cy + 8],
                       fill=BKS_GAUNTLET, outline=OUTLINE)
        draw.line([(cx - 18, body_cy + 4), (cx - 14, body_cy + 4)],
                  fill=BKS_GAUNTLET_LIGHT, width=1)
        # Right arm
        draw.rectangle([cx + 12, body_cy - 6, cx + 20, body_cy + 6],
                       fill=BKS_SKIN, outline=OUTLINE)
        draw.line([(cx + 16, body_cy - 4), (cx + 16, body_cy + 4)],
                  fill=BKS_SKIN_DARK, width=1)
        # Right gauntlet
        draw.rectangle([cx + 12, body_cy + 2, cx + 20, body_cy + 8],
                       fill=BKS_GAUNTLET, outline=OUTLINE)
        draw.line([(cx + 14, body_cy + 4), (cx + 18, body_cy + 4)],
                  fill=BKS_GAUNTLET_LIGHT, width=1)
        # Shoulder pads
        ellipse(draw, cx - 15, body_cy - 6, 6, 4, BKS_APRON)
        ellipse(draw, cx + 15, body_cy - 6, 6, 4, BKS_APRON)

        # Hammer (held in right hand)
        hammer_x = cx + 22
        hammer_y = body_cy - 4 + hammer_swing
        # Handle
        draw.rectangle([hammer_x - 1, hammer_y, hammer_x + 1, hammer_y + 16],
                       fill=BKS_HAMMER_HANDLE, outline=OUTLINE)
        # Head
        draw.rectangle([hammer_x - 4, hammer_y - 4, hammer_x + 4, hammer_y + 2],
                       fill=BKS_HAMMER_HEAD, outline=OUTLINE)
        draw.rectangle([hammer_x - 3, hammer_y - 3, hammer_x + 1, hammer_y + 1],
                       fill=BKS_GAUNTLET_LIGHT, outline=None)
        # Forge glow on hammer
        draw.point((hammer_x - 2, hammer_y - 2), fill=BKS_FORGE_GLOW)
        draw.point((hammer_x + 2, hammer_y), fill=BKS_FORGE_DIM)

        # Head (broad)
        ellipse(draw, cx, head_cy, 15, 13, BKS_HAIR)
        ellipse(draw, cx, head_cy + 2, 12, 10, BKS_SKIN)
        ellipse(draw, cx + 2, head_cy + 4, 8, 6, BKS_SKIN_DARK, outline=None)
        # Soot on face
        draw.point((cx + 6, head_cy + 4), fill=BKS_SKIN_SOOT)
        draw.point((cx - 4, head_cy + 8), fill=BKS_SKIN_SOOT)
        # Eyes
        draw.rectangle([cx - 6, head_cy + 1, cx - 3, head_cy + 4], fill=(255, 255, 255))
        draw.rectangle([cx - 5, head_cy + 2, cx - 4, head_cy + 3], fill=BLACK)
        draw.rectangle([cx + 3, head_cy + 1, cx + 6, head_cy + 4], fill=(255, 255, 255))
        draw.rectangle([cx + 4, head_cy + 2, cx + 5, head_cy + 3], fill=BLACK)
        # Eyebrows (thick)
        draw.line([(cx - 7, head_cy), (cx - 2, head_cy)],
                  fill=_darken(BKS_SKIN, 0.5), width=2)
        draw.line([(cx + 2, head_cy), (cx + 7, head_cy)],
                  fill=_darken(BKS_SKIN, 0.5), width=2)
        # Nose and mouth
        draw.point((cx, head_cy + 6), fill=BKS_SKIN_DARK)
        draw.line([(cx - 3, head_cy + 8), (cx + 3, head_cy + 8)],
                  fill=BKS_SKIN_DARK, width=1)
        # Goggles pushed up on forehead
        draw.rectangle([cx - 8, head_cy - 6, cx + 8, head_cy - 2],
                       fill=BKS_GOGGLES, outline=OUTLINE)
        ellipse(draw, cx - 4, head_cy - 4, 3, 2, BKS_GOGGLES_LENS)
        ellipse(draw, cx + 4, head_cy - 4, 3, 2, BKS_GOGGLES_LENS)
        draw.point((cx - 5, head_cy - 5), fill=_brighten(BKS_GOGGLES_LENS, 1.3))

    elif direction == UP:
        # Legs
        draw.rectangle([cx - 10 + leg_spread, body_cy + 10,
                        cx - 4 + leg_spread, base_y], fill=BKS_LEG, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, body_cy + 10,
                        cx + 10 - leg_spread, base_y], fill=BKS_LEG, outline=OUTLINE)
        draw.rectangle([cx - 10 + leg_spread, base_y - 5,
                        cx - 4 + leg_spread, base_y], fill=BKS_BOOT, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, base_y - 5,
                        cx + 10 - leg_spread, base_y], fill=BKS_BOOT, outline=OUTLINE)

        # Body (back)
        ellipse(draw, cx, body_cy, 15, 13, BKS_BODY)
        ellipse(draw, cx, body_cy, 11, 10, BKS_BODY_DARK, outline=None)
        # Apron ties from back
        draw.line([(cx - 8, body_cy + 4), (cx - 12, body_cy + 8)],
                  fill=BKS_APRON_DARK, width=2)
        draw.line([(cx + 8, body_cy + 4), (cx + 12, body_cy + 8)],
                  fill=BKS_APRON_DARK, width=2)
        draw.rectangle([cx - 15, body_cy + 10, cx + 15, body_cy + 14],
                       fill=BKS_BELT, outline=OUTLINE)
        # Arms
        draw.rectangle([cx - 20, body_cy - 6, cx - 12, body_cy + 6],
                       fill=BKS_SKIN, outline=OUTLINE)
        draw.rectangle([cx - 20, body_cy + 2, cx - 12, body_cy + 8],
                       fill=BKS_GAUNTLET, outline=OUTLINE)
        draw.rectangle([cx + 12, body_cy - 6, cx + 20, body_cy + 6],
                       fill=BKS_SKIN, outline=OUTLINE)
        draw.rectangle([cx + 12, body_cy + 2, cx + 20, body_cy + 8],
                       fill=BKS_GAUNTLET, outline=OUTLINE)
        ellipse(draw, cx - 15, body_cy - 6, 6, 4, BKS_APRON)
        ellipse(draw, cx + 15, body_cy - 6, 6, 4, BKS_APRON)
        # Head (back)
        ellipse(draw, cx, head_cy, 15, 13, BKS_HAIR)
        ellipse(draw, cx, head_cy - 2, 11, 8, _darken(BKS_HAIR, 0.8), outline=None)
        # Goggle strap
        draw.line([(cx - 10, head_cy - 4), (cx + 10, head_cy - 4)],
                  fill=BKS_GOGGLES, width=2)

    elif direction == LEFT:
        # Legs
        draw.rectangle([cx - 2 - leg_spread, body_cy + 10,
                        cx + 4 - leg_spread, base_y],
                       fill=_darken(BKS_LEG, 0.85), outline=OUTLINE)
        draw.rectangle([cx - 2 - leg_spread, base_y - 5,
                        cx + 4 - leg_spread, base_y], fill=BKS_BOOT, outline=OUTLINE)
        draw.rectangle([cx - 8 + leg_spread, body_cy + 10,
                        cx - 2 + leg_spread, base_y], fill=BKS_LEG, outline=OUTLINE)
        draw.rectangle([cx - 8 + leg_spread, base_y - 5,
                        cx - 2 + leg_spread, base_y], fill=BKS_BOOT, outline=OUTLINE)

        # Hammer behind body
        hammer_x = cx + 10
        hammer_y = body_cy - 4 + hammer_swing
        draw.rectangle([hammer_x - 1, hammer_y, hammer_x + 1, hammer_y + 14],
                       fill=BKS_HAMMER_HANDLE, outline=OUTLINE)
        draw.rectangle([hammer_x - 3, hammer_y - 3, hammer_x + 3, hammer_y + 1],
                       fill=BKS_HAMMER_HEAD, outline=OUTLINE)
        draw.point((hammer_x - 1, hammer_y - 1), fill=BKS_FORGE_GLOW)

        # Body
        ellipse(draw, cx - 2, body_cy, 13, 13, BKS_BODY)
        ellipse(draw, cx - 4, body_cy - 2, 8, 8, _brighten(BKS_BODY, 1.15), outline=None)
        # Apron
        draw.polygon([(cx - 8, body_cy - 4), (cx + 4, body_cy - 4),
                      (cx + 6, body_cy + 10), (cx - 10, body_cy + 10)],
                     fill=BKS_APRON, outline=OUTLINE)
        draw.point((cx - 2, body_cy + 2), fill=BKS_SOOT)
        draw.point((cx + 2, body_cy + 6), fill=BKS_SOOT)
        # Belt
        draw.rectangle([cx - 15, body_cy + 10, cx + 10, body_cy + 14],
                       fill=BKS_BELT, outline=OUTLINE)
        draw.rectangle([cx - 5, body_cy + 10, cx - 1, body_cy + 14],
                       fill=BKS_BELT_BUCKLE, outline=OUTLINE)

        # Arm with gauntlet
        draw.rectangle([cx - 16, body_cy - 4, cx - 10, body_cy + 6],
                       fill=BKS_SKIN, outline=OUTLINE)
        draw.line([(cx - 13, body_cy - 2), (cx - 13, body_cy + 4)],
                  fill=BKS_SKIN_DARK, width=1)
        draw.rectangle([cx - 16, body_cy + 2, cx - 10, body_cy + 8],
                       fill=BKS_GAUNTLET, outline=OUTLINE)
        ellipse(draw, cx - 12, body_cy - 6, 5, 4, BKS_APRON)

        # Head
        ellipse(draw, cx - 2, head_cy, 14, 13, BKS_HAIR)
        ellipse(draw, cx - 4, head_cy + 2, 11, 9, BKS_SKIN)
        ellipse(draw, cx - 2, head_cy + 4, 7, 5, BKS_SKIN_DARK, outline=None)
        draw.point((cx - 8, head_cy + 5), fill=BKS_SKIN_SOOT)
        # Eye
        draw.rectangle([cx - 10, head_cy + 1, cx - 7, head_cy + 4], fill=(255, 255, 255))
        draw.rectangle([cx - 9, head_cy + 2, cx - 8, head_cy + 3], fill=BLACK)
        draw.line([(cx - 11, head_cy), (cx - 6, head_cy)],
                  fill=_darken(BKS_SKIN, 0.5), width=2)
        draw.point((cx - 9, head_cy + 6), fill=BKS_SKIN_DARK)
        # Goggles
        draw.rectangle([cx - 8, head_cy - 6, cx + 4, head_cy - 2],
                       fill=BKS_GOGGLES, outline=OUTLINE)
        ellipse(draw, cx - 4, head_cy - 4, 3, 2, BKS_GOGGLES_LENS)

    else:  # RIGHT
        # Legs
        draw.rectangle([cx - 2 + leg_spread, body_cy + 10,
                        cx + 4 + leg_spread, base_y],
                       fill=_darken(BKS_LEG, 0.85), outline=OUTLINE)
        draw.rectangle([cx - 2 + leg_spread, base_y - 5,
                        cx + 4 + leg_spread, base_y], fill=BKS_BOOT, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, body_cy + 10,
                        cx + 10 - leg_spread, base_y], fill=BKS_LEG, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, base_y - 5,
                        cx + 10 - leg_spread, base_y], fill=BKS_BOOT, outline=OUTLINE)

        # Hammer behind body
        hammer_x = cx - 10
        hammer_y = body_cy - 4 + hammer_swing
        draw.rectangle([hammer_x - 1, hammer_y, hammer_x + 1, hammer_y + 14],
                       fill=BKS_HAMMER_HANDLE, outline=OUTLINE)
        draw.rectangle([hammer_x - 3, hammer_y - 3, hammer_x + 3, hammer_y + 1],
                       fill=BKS_HAMMER_HEAD, outline=OUTLINE)
        draw.point((hammer_x + 1, hammer_y - 1), fill=BKS_FORGE_GLOW)

        # Body
        ellipse(draw, cx + 2, body_cy, 13, 13, BKS_BODY)
        ellipse(draw, cx, body_cy - 2, 8, 8, _brighten(BKS_BODY, 1.15), outline=None)
        # Apron
        draw.polygon([(cx - 4, body_cy - 4), (cx + 8, body_cy - 4),
                      (cx + 10, body_cy + 10), (cx - 6, body_cy + 10)],
                     fill=BKS_APRON, outline=OUTLINE)
        draw.point((cx + 2, body_cy + 2), fill=BKS_SOOT)
        draw.point((cx - 2, body_cy + 6), fill=BKS_SOOT)
        # Belt
        draw.rectangle([cx - 10, body_cy + 10, cx + 15, body_cy + 14],
                       fill=BKS_BELT, outline=OUTLINE)
        draw.rectangle([cx + 1, body_cy + 10, cx + 5, body_cy + 14],
                       fill=BKS_BELT_BUCKLE, outline=OUTLINE)

        # Arm with gauntlet
        draw.rectangle([cx + 10, body_cy - 4, cx + 16, body_cy + 6],
                       fill=BKS_SKIN, outline=OUTLINE)
        draw.line([(cx + 13, body_cy - 2), (cx + 13, body_cy + 4)],
                  fill=BKS_SKIN_DARK, width=1)
        draw.rectangle([cx + 10, body_cy + 2, cx + 16, body_cy + 8],
                       fill=BKS_GAUNTLET, outline=OUTLINE)
        ellipse(draw, cx + 12, body_cy - 6, 5, 4, BKS_APRON)

        # Head
        ellipse(draw, cx + 2, head_cy, 14, 13, BKS_HAIR)
        ellipse(draw, cx + 4, head_cy + 2, 11, 9, BKS_SKIN)
        ellipse(draw, cx + 6, head_cy + 4, 7, 5, BKS_SKIN_DARK, outline=None)
        draw.point((cx + 8, head_cy + 5), fill=BKS_SKIN_SOOT)
        # Eye
        draw.rectangle([cx + 7, head_cy + 1, cx + 10, head_cy + 4], fill=(255, 255, 255))
        draw.rectangle([cx + 8, head_cy + 2, cx + 9, head_cy + 3], fill=BLACK)
        draw.line([(cx + 6, head_cy), (cx + 11, head_cy)],
                  fill=_darken(BKS_SKIN, 0.5), width=2)
        draw.point((cx + 9, head_cy + 6), fill=BKS_SKIN_DARK)
        # Goggles
        draw.rectangle([cx - 4, head_cy - 6, cx + 8, head_cy - 2],
                       fill=BKS_GOGGLES, outline=OUTLINE)
        ellipse(draw, cx + 4, head_cy - 4, 3, 2, BKS_GOGGLES_LENS)


# ===================================================================
# PIRATE (ID 106)
# Red bandana, eyepatch, cutlass at hip, captain's long coat,
# peg leg (one wooden leg), gold earring, skull belt buckle.
# ===================================================================

def draw_pirate(draw, ox, oy, direction, frame):
    bob = [0, -2, 0, -1][frame]
    leg_spread = [-4, 0, 4, 0][frame]
    coat_sway = [-2, 0, 2, 0][frame]

    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 20
    head_cy = body_cy - 20

    if direction == DOWN:
        # Coat tails (behind body, flowing)
        draw.polygon([
            (cx - 16, body_cy + 6),
            (cx + 16, body_cy + 6),
            (cx + 18 + coat_sway, base_y + 2),
            (cx - 18 + coat_sway, base_y + 2),
        ], fill=PIR_COAT_DARK, outline=OUTLINE)
        draw.line([(cx - 6, body_cy + 8), (cx - 8 + coat_sway, base_y)],
                  fill=_darken(PIR_COAT_DARK, 0.8), width=1)
        draw.line([(cx + 6, body_cy + 8), (cx + 8 + coat_sway, base_y)],
                  fill=_darken(PIR_COAT_DARK, 0.8), width=1)

        # Legs -- left normal, right is peg leg
        draw.rectangle([cx - 10 + leg_spread, body_cy + 10,
                        cx - 4 + leg_spread, base_y], fill=PIR_LEG, outline=OUTLINE)
        draw.rectangle([cx - 10 + leg_spread, base_y - 5,
                        cx - 4 + leg_spread, base_y], fill=PIR_BOOT, outline=OUTLINE)
        # Peg leg (right)
        draw.rectangle([cx + 5 - leg_spread, body_cy + 10,
                        cx + 9 - leg_spread, body_cy + 16], fill=PIR_LEG, outline=OUTLINE)
        draw.rectangle([cx + 6 - leg_spread, body_cy + 16,
                        cx + 8 - leg_spread, base_y], fill=PIR_PEG, outline=OUTLINE)
        draw.line([(cx + 6 - leg_spread, body_cy + 18),
                   (cx + 8 - leg_spread, body_cy + 18)],
                  fill=PIR_PEG_DARK, width=1)
        draw.line([(cx + 6 - leg_spread, body_cy + 22),
                   (cx + 8 - leg_spread, body_cy + 22)],
                  fill=PIR_PEG_DARK, width=1)

        # Body (captain's coat)
        ellipse(draw, cx, body_cy, 14, 12, PIR_COAT)
        ellipse(draw, cx - 3, body_cy - 2, 8, 7, PIR_COAT_LIGHT, outline=None)
        # Open coat showing shirt
        draw.polygon([(cx - 4, body_cy - 6), (cx + 4, body_cy - 6),
                      (cx + 3, body_cy + 6), (cx - 3, body_cy + 6)],
                     fill=(200, 190, 170), outline=None)
        # Coat lapels
        draw.line([(cx - 2, body_cy - 8), (cx - 6, body_cy + 4)],
                  fill=PIR_COAT_DARK, width=2)
        draw.line([(cx + 2, body_cy - 8), (cx + 6, body_cy + 4)],
                  fill=PIR_COAT_DARK, width=2)
        # Belt with skull buckle
        draw.rectangle([cx - 14, body_cy + 8, cx + 14, body_cy + 12],
                       fill=PIR_BELT, outline=OUTLINE)
        # Skull buckle
        ellipse(draw, cx, body_cy + 10, 3, 3, PIR_SKULL_BUCKLE)
        draw.point((cx - 1, body_cy + 9), fill=PIR_SKULL_EYE)
        draw.point((cx + 1, body_cy + 9), fill=PIR_SKULL_EYE)
        draw.point((cx, body_cy + 11), fill=PIR_SKULL_EYE)

        # Cutlass at left hip
        cutlass_x = cx - 18
        draw.line([(cutlass_x, body_cy + 6), (cutlass_x - 2, body_cy + 22)],
                  fill=PIR_CUTLASS_BLADE, width=2)
        draw.line([(cutlass_x + 1, body_cy + 8), (cutlass_x - 1, body_cy + 20)],
                  fill=PIR_CUTLASS_LIGHT, width=1)
        # Guard (curved)
        draw.arc([cutlass_x - 4, body_cy + 4, cutlass_x + 4, body_cy + 10],
                 start=0, end=180, fill=PIR_CUTLASS_GUARD, width=2)
        # Handle
        draw.rectangle([cutlass_x - 1, body_cy + 2, cutlass_x + 1, body_cy + 6],
                       fill=PIR_CUTLASS_HANDLE, outline=OUTLINE)

        # Arms
        draw.rectangle([cx - 18, body_cy - 6, cx - 12, body_cy + 6],
                       fill=PIR_COAT, outline=OUTLINE)
        draw.rectangle([cx - 18, body_cy + 2, cx - 12, body_cy + 6],
                       fill=PIR_SKIN, outline=OUTLINE)
        draw.rectangle([cx + 12, body_cy - 6, cx + 18, body_cy + 6],
                       fill=PIR_COAT, outline=OUTLINE)
        draw.rectangle([cx + 12, body_cy + 2, cx + 18, body_cy + 6],
                       fill=PIR_SKIN, outline=OUTLINE)
        # Gold cuff trim
        draw.line([(cx - 18, body_cy + 1), (cx - 12, body_cy + 1)],
                  fill=PIR_EARRING, width=1)
        draw.line([(cx + 12, body_cy + 1), (cx + 18, body_cy + 1)],
                  fill=PIR_EARRING, width=1)
        ellipse(draw, cx - 14, body_cy - 6, 5, 3, PIR_COAT_LIGHT)
        ellipse(draw, cx + 14, body_cy - 6, 5, 3, PIR_COAT_LIGHT)

        # Head
        ellipse(draw, cx, head_cy, 14, 13, PIR_HAIR)
        ellipse(draw, cx, head_cy + 2, 12, 10, PIR_SKIN)
        ellipse(draw, cx + 2, head_cy + 4, 8, 6, PIR_SKIN_DARK, outline=None)
        # Eyes -- left eye has eyepatch
        # Right eye (visible)
        draw.rectangle([cx + 3, head_cy + 1, cx + 6, head_cy + 4], fill=(255, 255, 255))
        draw.rectangle([cx + 4, head_cy + 2, cx + 5, head_cy + 3], fill=BLACK)
        # Eyepatch on left eye
        draw.rectangle([cx - 7, head_cy + 1, cx - 3, head_cy + 5],
                       fill=PIR_EYEPATCH, outline=OUTLINE)
        # Eyepatch strap
        draw.line([(cx - 7, head_cy + 2), (cx - 14, head_cy - 6)],
                  fill=PIR_EYEPATCH_STRAP, width=1)
        draw.line([(cx - 3, head_cy + 2), (cx + 14, head_cy - 6)],
                  fill=PIR_EYEPATCH_STRAP, width=1)
        # Nose and mouth
        draw.point((cx, head_cy + 6), fill=PIR_SKIN_DARK)
        draw.line([(cx - 3, head_cy + 8), (cx + 3, head_cy + 8)],
                  fill=PIR_SKIN_DARK, width=1)
        # Gold earring (right ear)
        draw.point((cx + 12, head_cy + 4), fill=PIR_EARRING)
        draw.point((cx + 12, head_cy + 6), fill=PIR_EARRING)
        draw.point((cx + 11, head_cy + 7), fill=PIR_EARRING)

        # Red bandana
        draw.rectangle([cx - 14, head_cy - 7, cx + 14, head_cy - 2],
                       fill=PIR_BANDANA, outline=OUTLINE)
        draw.rectangle([cx + 6, head_cy - 6, cx + 13, head_cy - 3],
                       fill=PIR_BANDANA_DARK, outline=None)
        draw.line([(cx - 10, head_cy - 5), (cx + 10, head_cy - 5)],
                  fill=PIR_BANDANA_LIGHT, width=1)
        # Bandana knot tails (left side)
        draw.line([(cx - 12, head_cy - 4), (cx - 16, head_cy + 4)],
                  fill=PIR_BANDANA, width=2)
        draw.line([(cx - 14, head_cy - 4), (cx - 18, head_cy + 2)],
                  fill=PIR_BANDANA_DARK, width=1)

    elif direction == UP:
        # Coat tails (visible from behind, dramatic)
        draw.polygon([
            (cx - 18, body_cy + 4),
            (cx + 18, body_cy + 4),
            (cx + 22 + coat_sway, base_y + 2),
            (cx - 22 + coat_sway, base_y + 2),
        ], fill=PIR_COAT, outline=OUTLINE)
        # Inner coat highlight
        draw.polygon([
            (cx - 12, body_cy + 6),
            (cx + 12, body_cy + 6),
            (cx + 16 + coat_sway, base_y),
            (cx - 16 + coat_sway, base_y),
        ], fill=PIR_COAT_DARK, outline=None)
        draw.line([(cx, body_cy + 8), (cx + coat_sway, base_y - 2)],
                  fill=_darken(PIR_COAT, 0.7), width=1)

        # Legs
        draw.rectangle([cx - 10 + leg_spread, body_cy + 10,
                        cx - 4 + leg_spread, base_y], fill=PIR_LEG, outline=OUTLINE)
        draw.rectangle([cx - 10 + leg_spread, base_y - 5,
                        cx - 4 + leg_spread, base_y], fill=PIR_BOOT, outline=OUTLINE)
        # Peg leg
        draw.rectangle([cx + 5 - leg_spread, body_cy + 10,
                        cx + 9 - leg_spread, body_cy + 16], fill=PIR_LEG, outline=OUTLINE)
        draw.rectangle([cx + 6 - leg_spread, body_cy + 16,
                        cx + 8 - leg_spread, base_y], fill=PIR_PEG, outline=OUTLINE)

        # Body
        ellipse(draw, cx, body_cy, 14, 12, PIR_COAT)
        ellipse(draw, cx, body_cy, 10, 9, PIR_COAT_DARK, outline=None)
        draw.line([(cx, body_cy - 6), (cx, body_cy + 6)],
                  fill=_darken(PIR_COAT, 0.65), width=1)
        draw.rectangle([cx - 14, body_cy + 8, cx + 14, body_cy + 12],
                       fill=PIR_BELT, outline=OUTLINE)
        # Arms
        draw.rectangle([cx - 18, body_cy - 6, cx - 12, body_cy + 6],
                       fill=PIR_COAT, outline=OUTLINE)
        draw.rectangle([cx + 12, body_cy - 6, cx + 18, body_cy + 6],
                       fill=PIR_COAT, outline=OUTLINE)
        draw.line([(cx - 18, body_cy + 1), (cx - 12, body_cy + 1)],
                  fill=PIR_EARRING, width=1)
        draw.line([(cx + 12, body_cy + 1), (cx + 18, body_cy + 1)],
                  fill=PIR_EARRING, width=1)
        ellipse(draw, cx - 14, body_cy - 6, 5, 3, PIR_COAT_LIGHT)
        ellipse(draw, cx + 14, body_cy - 6, 5, 3, PIR_COAT_LIGHT)

        # Head (back)
        ellipse(draw, cx, head_cy, 14, 13, PIR_HAIR)
        ellipse(draw, cx, head_cy - 2, 10, 8, _darken(PIR_HAIR, 0.75), outline=None)
        # Bandana from back with knot tails
        draw.rectangle([cx - 14, head_cy - 7, cx + 14, head_cy - 2],
                       fill=PIR_BANDANA, outline=OUTLINE)
        draw.line([(cx + 6, head_cy - 3), (cx + 12, head_cy + 6)],
                  fill=PIR_BANDANA, width=3)
        draw.line([(cx + 10, head_cy - 3), (cx + 16, head_cy + 4)],
                  fill=PIR_BANDANA_DARK, width=2)

    elif direction == LEFT:
        # Coat tail (flowing behind to right)
        draw.polygon([
            (cx + 4, body_cy + 4),
            (cx + 16, body_cy + 2),
            (cx + 20 + coat_sway, base_y + 2),
            (cx + 4, base_y + 2),
        ], fill=PIR_COAT, outline=OUTLINE)
        draw.polygon([
            (cx + 6, body_cy + 6),
            (cx + 14, body_cy + 4),
            (cx + 16 + coat_sway, base_y),
            (cx + 6, base_y),
        ], fill=PIR_COAT_DARK, outline=None)

        # Legs
        draw.rectangle([cx - 2 - leg_spread, body_cy + 10,
                        cx + 4 - leg_spread, base_y],
                       fill=_darken(PIR_LEG, 0.85), outline=OUTLINE)
        draw.rectangle([cx - 2 - leg_spread, base_y - 5,
                        cx + 4 - leg_spread, base_y], fill=PIR_BOOT, outline=OUTLINE)
        # Front leg is peg leg
        draw.rectangle([cx - 8 + leg_spread, body_cy + 10,
                        cx - 4 + leg_spread, body_cy + 16], fill=PIR_LEG, outline=OUTLINE)
        draw.rectangle([cx - 7 + leg_spread, body_cy + 16,
                        cx - 5 + leg_spread, base_y], fill=PIR_PEG, outline=OUTLINE)

        # Body
        ellipse(draw, cx - 2, body_cy, 12, 12, PIR_COAT)
        ellipse(draw, cx - 4, body_cy - 2, 7, 7, PIR_COAT_LIGHT, outline=None)
        # Shirt visible
        draw.polygon([(cx - 4, body_cy - 4), (cx + 2, body_cy - 4),
                      (cx + 1, body_cy + 4), (cx - 3, body_cy + 4)],
                     fill=(200, 190, 170), outline=None)
        # Belt
        draw.rectangle([cx - 14, body_cy + 8, cx + 10, body_cy + 12],
                       fill=PIR_BELT, outline=OUTLINE)
        ellipse(draw, cx - 4, body_cy + 10, 2, 2, PIR_SKULL_BUCKLE)

        # Cutlass at hip
        draw.line([(cx + 6, body_cy + 6), (cx + 8, body_cy + 20)],
                  fill=PIR_CUTLASS_BLADE, width=2)
        draw.arc([cx + 3, body_cy + 3, cx + 9, body_cy + 9],
                 start=0, end=180, fill=PIR_CUTLASS_GUARD, width=1)

        # Front arm
        draw.rectangle([cx - 14, body_cy - 4, cx - 8, body_cy + 6],
                       fill=PIR_COAT, outline=OUTLINE)
        draw.rectangle([cx - 14, body_cy + 2, cx - 8, body_cy + 6],
                       fill=PIR_SKIN, outline=OUTLINE)
        draw.line([(cx - 14, body_cy + 1), (cx - 8, body_cy + 1)],
                  fill=PIR_EARRING, width=1)
        ellipse(draw, cx - 10, body_cy - 6, 5, 3, PIR_COAT_LIGHT)

        # Head
        ellipse(draw, cx - 2, head_cy, 13, 13, PIR_HAIR)
        ellipse(draw, cx - 4, head_cy + 2, 10, 9, PIR_SKIN)
        ellipse(draw, cx - 2, head_cy + 4, 6, 5, PIR_SKIN_DARK, outline=None)
        # Eye (with eyepatch)
        draw.rectangle([cx - 10, head_cy + 1, cx - 6, head_cy + 5],
                       fill=PIR_EYEPATCH, outline=OUTLINE)
        draw.line([(cx - 10, head_cy + 2), (cx - 14, head_cy - 6)],
                  fill=PIR_EYEPATCH_STRAP, width=1)
        draw.line([(cx - 6, head_cy + 2), (cx + 6, head_cy - 6)],
                  fill=PIR_EYEPATCH_STRAP, width=1)
        draw.point((cx - 9, head_cy + 6), fill=PIR_SKIN_DARK)
        # Bandana
        draw.rectangle([cx - 14, head_cy - 7, cx + 8, head_cy - 2],
                       fill=PIR_BANDANA, outline=OUTLINE)
        draw.rectangle([cx + 2, head_cy - 6, cx + 7, head_cy - 3],
                       fill=PIR_BANDANA_DARK, outline=None)
        draw.line([(cx - 10, head_cy - 5), (cx + 4, head_cy - 5)],
                  fill=PIR_BANDANA_LIGHT, width=1)
        # Tails trailing right
        draw.line([(cx + 6, head_cy - 4), (cx + 14, head_cy + 2)],
                  fill=PIR_BANDANA, width=2)

    else:  # RIGHT
        # Coat tail (flowing behind to left)
        coat_sway_r = [-coat_sway for coat_sway in [coat_sway]][0]
        draw.polygon([
            (cx - 4, body_cy + 4),
            (cx - 16, body_cy + 2),
            (cx - 20 + coat_sway_r, base_y + 2),
            (cx - 4, base_y + 2),
        ], fill=PIR_COAT, outline=OUTLINE)
        draw.polygon([
            (cx - 6, body_cy + 6),
            (cx - 14, body_cy + 4),
            (cx - 16 + coat_sway_r, base_y),
            (cx - 6, base_y),
        ], fill=PIR_COAT_DARK, outline=None)

        # Legs
        draw.rectangle([cx - 2 + leg_spread, body_cy + 10,
                        cx + 4 + leg_spread, base_y],
                       fill=_darken(PIR_LEG, 0.85), outline=OUTLINE)
        draw.rectangle([cx - 2 + leg_spread, base_y - 5,
                        cx + 4 + leg_spread, base_y], fill=PIR_BOOT, outline=OUTLINE)
        # Front leg is peg leg
        draw.rectangle([cx + 4 - leg_spread, body_cy + 10,
                        cx + 8 - leg_spread, body_cy + 16], fill=PIR_LEG, outline=OUTLINE)
        draw.rectangle([cx + 5 - leg_spread, body_cy + 16,
                        cx + 7 - leg_spread, base_y], fill=PIR_PEG, outline=OUTLINE)

        # Body
        ellipse(draw, cx + 2, body_cy, 12, 12, PIR_COAT)
        ellipse(draw, cx, body_cy - 2, 7, 7, PIR_COAT_LIGHT, outline=None)
        draw.polygon([(cx - 2, body_cy - 4), (cx + 4, body_cy - 4),
                      (cx + 3, body_cy + 4), (cx - 1, body_cy + 4)],
                     fill=(200, 190, 170), outline=None)
        # Belt
        draw.rectangle([cx - 10, body_cy + 8, cx + 14, body_cy + 12],
                       fill=PIR_BELT, outline=OUTLINE)
        ellipse(draw, cx + 4, body_cy + 10, 2, 2, PIR_SKULL_BUCKLE)

        # Cutlass at hip
        draw.line([(cx - 6, body_cy + 6), (cx - 8, body_cy + 20)],
                  fill=PIR_CUTLASS_BLADE, width=2)
        draw.arc([cx - 9, body_cy + 3, cx - 3, body_cy + 9],
                 start=0, end=180, fill=PIR_CUTLASS_GUARD, width=1)

        # Front arm
        draw.rectangle([cx + 8, body_cy - 4, cx + 14, body_cy + 6],
                       fill=PIR_COAT, outline=OUTLINE)
        draw.rectangle([cx + 8, body_cy + 2, cx + 14, body_cy + 6],
                       fill=PIR_SKIN, outline=OUTLINE)
        draw.line([(cx + 8, body_cy + 1), (cx + 14, body_cy + 1)],
                  fill=PIR_EARRING, width=1)
        ellipse(draw, cx + 10, body_cy - 6, 5, 3, PIR_COAT_LIGHT)

        # Head
        ellipse(draw, cx + 2, head_cy, 13, 13, PIR_HAIR)
        ellipse(draw, cx + 4, head_cy + 2, 10, 9, PIR_SKIN)
        ellipse(draw, cx + 6, head_cy + 4, 6, 5, PIR_SKIN_DARK, outline=None)
        # Eye (visible, no patch on this side)
        draw.rectangle([cx + 7, head_cy + 1, cx + 10, head_cy + 4], fill=(255, 255, 255))
        draw.rectangle([cx + 8, head_cy + 2, cx + 9, head_cy + 3], fill=BLACK)
        # Eyepatch strap crosses forehead
        draw.line([(cx + 4, head_cy - 2), (cx - 6, head_cy - 6)],
                  fill=PIR_EYEPATCH_STRAP, width=1)
        draw.point((cx + 9, head_cy + 6), fill=PIR_SKIN_DARK)
        # Gold earring (left ear visible)
        draw.point((cx - 8, head_cy + 4), fill=PIR_EARRING)
        draw.point((cx - 8, head_cy + 6), fill=PIR_EARRING)
        draw.point((cx - 7, head_cy + 7), fill=PIR_EARRING)
        # Bandana
        draw.rectangle([cx - 8, head_cy - 7, cx + 14, head_cy - 2],
                       fill=PIR_BANDANA, outline=OUTLINE)
        draw.rectangle([cx + 6, head_cy - 6, cx + 13, head_cy - 3],
                       fill=PIR_BANDANA_DARK, outline=None)
        draw.line([(cx - 4, head_cy - 5), (cx + 10, head_cy - 5)],
                  fill=PIR_BANDANA_LIGHT, width=1)
        # Tails trailing left
        draw.line([(cx - 6, head_cy - 4), (cx - 14, head_cy + 2)],
                  fill=PIR_BANDANA, width=2)


# ===================================================================
# CHEF (ID 107)
# White chef uniform, tall white toque hat, red neckerchief, meat
# cleaver/spatula in hand, stained apron, kitchen details.
# ===================================================================

# Chef palette
CHF_UNIFORM = (240, 238, 232)
CHF_UNIFORM_LIGHT = (250, 248, 245)
CHF_UNIFORM_DARK = (210, 205, 195)
CHF_UNIFORM_SHADOW = (185, 180, 172)
CHF_APRON = (230, 225, 215)
CHF_APRON_DARK = (200, 195, 185)
CHF_APRON_STAIN_RED = (180, 60, 50)
CHF_APRON_STAIN_BROWN = (140, 100, 60)
CHF_APRON_STAIN_YELLOW = (190, 170, 60)
CHF_NECKERCHIEF = (200, 40, 40)
CHF_NECKERCHIEF_DARK = (160, 30, 30)
CHF_NECKERCHIEF_LIGHT = (230, 60, 55)
CHF_SKIN = (215, 185, 155)
CHF_SKIN_DARK = (185, 155, 125)
CHF_TOQUE = (245, 242, 235)
CHF_TOQUE_DARK = (215, 210, 200)
CHF_TOQUE_LIGHT = (255, 255, 252)
CHF_TOQUE_BAND = (220, 215, 205)
CHF_CLEAVER_BLADE = (185, 190, 200)
CHF_CLEAVER_LIGHT = (215, 220, 230)
CHF_CLEAVER_HANDLE = (100, 65, 35)
CHF_CLEAVER_RIVET = (180, 175, 165)
CHF_BUTTONS = (190, 185, 175)
CHF_LEG = (60, 60, 65)
CHF_BOOT = (45, 42, 40)
CHF_HAIR = (80, 55, 35)
CHF_BELT = (90, 70, 45)


def draw_chef(draw, ox, oy, direction, frame):
    bob = [0, -2, 0, -1][frame]
    leg_spread = [-4, 0, 4, 0][frame]
    chop_offset = [-1, 2, -1, 0][frame]

    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 20
    head_cy = body_cy - 20

    if direction == DOWN:
        # Legs
        draw.rectangle([cx - 10 + leg_spread, body_cy + 10,
                        cx - 4 + leg_spread, base_y], fill=CHF_LEG, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, body_cy + 10,
                        cx + 10 - leg_spread, base_y], fill=CHF_LEG, outline=OUTLINE)
        # Boots
        draw.rectangle([cx - 10 + leg_spread, base_y - 5,
                        cx - 4 + leg_spread, base_y], fill=CHF_BOOT, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, base_y - 5,
                        cx + 10 - leg_spread, base_y], fill=CHF_BOOT, outline=OUTLINE)

        # Chef jacket body
        ellipse(draw, cx, body_cy, 14, 12, CHF_UNIFORM)
        ellipse(draw, cx - 3, body_cy - 2, 8, 7, CHF_UNIFORM_LIGHT, outline=None)
        # Double-breasted buttons
        draw.point((cx - 3, body_cy - 4), fill=CHF_BUTTONS)
        draw.point((cx - 3, body_cy), fill=CHF_BUTTONS)
        draw.point((cx - 3, body_cy + 4), fill=CHF_BUTTONS)
        draw.point((cx + 3, body_cy - 4), fill=CHF_BUTTONS)
        draw.point((cx + 3, body_cy), fill=CHF_BUTTONS)
        draw.point((cx + 3, body_cy + 4), fill=CHF_BUTTONS)

        # Apron over jacket
        draw.rectangle([cx - 10, body_cy + 2, cx + 10, body_cy + 14],
                       fill=CHF_APRON, outline=OUTLINE)
        # Apron stains
        draw.point((cx - 5, body_cy + 5), fill=CHF_APRON_STAIN_RED)
        draw.point((cx + 3, body_cy + 8), fill=CHF_APRON_STAIN_BROWN)
        draw.point((cx - 2, body_cy + 10), fill=CHF_APRON_STAIN_YELLOW)
        draw.point((cx + 6, body_cy + 6), fill=CHF_APRON_STAIN_RED)

        # Belt / apron tie
        draw.rectangle([cx - 12, body_cy + 12, cx + 12, body_cy + 14],
                       fill=CHF_BELT, outline=OUTLINE)

        # Arms
        draw.rectangle([cx - 18, body_cy - 6, cx - 12, body_cy + 6],
                       fill=CHF_UNIFORM, outline=OUTLINE)
        draw.rectangle([cx - 18, body_cy + 2, cx - 12, body_cy + 6],
                       fill=CHF_SKIN, outline=OUTLINE)
        draw.rectangle([cx + 12, body_cy - 6, cx + 18, body_cy + 6],
                       fill=CHF_UNIFORM, outline=OUTLINE)
        draw.rectangle([cx + 12, body_cy + 2, cx + 18, body_cy + 6],
                       fill=CHF_SKIN, outline=OUTLINE)
        # Shoulder puffs
        ellipse(draw, cx - 14, body_cy - 6, 5, 3, CHF_UNIFORM_LIGHT)
        ellipse(draw, cx + 14, body_cy - 6, 5, 3, CHF_UNIFORM_LIGHT)

        # Cleaver in right hand
        clv_x = cx + 20
        clv_y = body_cy - 2 + chop_offset
        draw.rectangle([clv_x - 1, clv_y - 8, clv_x + 1, clv_y - 2],
                       fill=CHF_CLEAVER_HANDLE, outline=OUTLINE)
        draw.rectangle([clv_x - 4, clv_y - 14, clv_x + 4, clv_y - 8],
                       fill=CHF_CLEAVER_BLADE, outline=OUTLINE)
        draw.line([(clv_x - 3, clv_y - 12), (clv_x + 3, clv_y - 12)],
                  fill=CHF_CLEAVER_LIGHT, width=1)
        draw.point((clv_x, clv_y - 4), fill=CHF_CLEAVER_RIVET)

        # Neckerchief
        draw.polygon([(cx - 4, body_cy - 10), (cx + 4, body_cy - 10),
                      (cx, body_cy - 4)], fill=CHF_NECKERCHIEF, outline=OUTLINE)
        draw.line([(cx - 2, body_cy - 9), (cx, body_cy - 6)],
                  fill=CHF_NECKERCHIEF_LIGHT, width=1)

        # Head
        ellipse(draw, cx, head_cy, 14, 13, CHF_HAIR)
        ellipse(draw, cx, head_cy + 2, 12, 10, CHF_SKIN)
        ellipse(draw, cx + 2, head_cy + 4, 8, 6, CHF_SKIN_DARK, outline=None)
        # Eyes
        draw.rectangle([cx - 6, head_cy + 1, cx - 3, head_cy + 4], fill=(255, 255, 255))
        draw.rectangle([cx - 5, head_cy + 2, cx - 4, head_cy + 3], fill=BLACK)
        draw.rectangle([cx + 3, head_cy + 1, cx + 6, head_cy + 4], fill=(255, 255, 255))
        draw.rectangle([cx + 4, head_cy + 2, cx + 5, head_cy + 3], fill=BLACK)
        # Nose and mouth
        draw.point((cx, head_cy + 6), fill=CHF_SKIN_DARK)
        draw.line([(cx - 2, head_cy + 8), (cx + 2, head_cy + 8)],
                  fill=CHF_SKIN_DARK, width=1)
        # Mustache
        draw.line([(cx - 5, head_cy + 7), (cx - 1, head_cy + 6)],
                  fill=CHF_HAIR, width=1)
        draw.line([(cx + 1, head_cy + 6), (cx + 5, head_cy + 7)],
                  fill=CHF_HAIR, width=1)

        # Toque (tall chef hat)
        draw.rectangle([cx - 8, head_cy - 8, cx + 8, head_cy - 6],
                       fill=CHF_TOQUE_BAND, outline=OUTLINE)
        draw.rectangle([cx - 7, head_cy - 24, cx + 7, head_cy - 8],
                       fill=CHF_TOQUE, outline=OUTLINE)
        ellipse(draw, cx, head_cy - 24, 7, 3, CHF_TOQUE)
        draw.line([(cx - 4, head_cy - 20), (cx - 4, head_cy - 12)],
                  fill=CHF_TOQUE_DARK, width=1)
        draw.line([(cx + 2, head_cy - 18), (cx + 2, head_cy - 10)],
                  fill=CHF_TOQUE_LIGHT, width=1)

    elif direction == UP:
        # Legs
        draw.rectangle([cx - 10 + leg_spread, body_cy + 10,
                        cx - 4 + leg_spread, base_y], fill=CHF_LEG, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, body_cy + 10,
                        cx + 10 - leg_spread, base_y], fill=CHF_LEG, outline=OUTLINE)
        draw.rectangle([cx - 10 + leg_spread, base_y - 5,
                        cx - 4 + leg_spread, base_y], fill=CHF_BOOT, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, base_y - 5,
                        cx + 10 - leg_spread, base_y], fill=CHF_BOOT, outline=OUTLINE)

        # Chef jacket (back)
        ellipse(draw, cx, body_cy, 14, 12, CHF_UNIFORM)
        ellipse(draw, cx, body_cy, 10, 9, CHF_UNIFORM_DARK, outline=None)
        # Back seam
        draw.line([(cx, body_cy - 6), (cx, body_cy + 6)],
                  fill=CHF_UNIFORM_SHADOW, width=1)
        # Apron ties visible from back
        draw.line([(cx - 6, body_cy + 4), (cx - 10, body_cy + 8)],
                  fill=CHF_APRON_DARK, width=2)
        draw.line([(cx + 6, body_cy + 4), (cx + 10, body_cy + 8)],
                  fill=CHF_APRON_DARK, width=2)
        # Belt
        draw.rectangle([cx - 12, body_cy + 12, cx + 12, body_cy + 14],
                       fill=CHF_BELT, outline=OUTLINE)

        # Arms
        draw.rectangle([cx - 18, body_cy - 6, cx - 12, body_cy + 6],
                       fill=CHF_UNIFORM, outline=OUTLINE)
        draw.rectangle([cx + 12, body_cy - 6, cx + 18, body_cy + 6],
                       fill=CHF_UNIFORM, outline=OUTLINE)
        ellipse(draw, cx - 14, body_cy - 6, 5, 3, CHF_UNIFORM_LIGHT)
        ellipse(draw, cx + 14, body_cy - 6, 5, 3, CHF_UNIFORM_LIGHT)

        # Head (back)
        ellipse(draw, cx, head_cy, 14, 13, CHF_HAIR)
        ellipse(draw, cx, head_cy - 2, 10, 8, _darken(CHF_HAIR, 0.85), outline=None)
        # Neckerchief knot visible from back
        draw.rectangle([cx - 2, body_cy - 10, cx + 2, body_cy - 7],
                       fill=CHF_NECKERCHIEF, outline=OUTLINE)

        # Toque (back view)
        draw.rectangle([cx - 8, head_cy - 8, cx + 8, head_cy - 6],
                       fill=CHF_TOQUE_BAND, outline=OUTLINE)
        draw.rectangle([cx - 7, head_cy - 24, cx + 7, head_cy - 8],
                       fill=CHF_TOQUE, outline=OUTLINE)
        ellipse(draw, cx, head_cy - 24, 7, 3, CHF_TOQUE)
        draw.line([(cx - 3, head_cy - 20), (cx - 3, head_cy - 10)],
                  fill=CHF_TOQUE_DARK, width=1)
        draw.line([(cx + 3, head_cy - 18), (cx + 3, head_cy - 10)],
                  fill=CHF_TOQUE_DARK, width=1)

    elif direction == LEFT:
        # Legs
        draw.rectangle([cx - 2 - leg_spread, body_cy + 10,
                        cx + 4 - leg_spread, base_y],
                       fill=_darken(CHF_LEG, 0.85), outline=OUTLINE)
        draw.rectangle([cx - 2 - leg_spread, base_y - 5,
                        cx + 4 - leg_spread, base_y], fill=CHF_BOOT, outline=OUTLINE)
        draw.rectangle([cx - 8 + leg_spread, body_cy + 10,
                        cx - 2 + leg_spread, base_y], fill=CHF_LEG, outline=OUTLINE)
        draw.rectangle([cx - 8 + leg_spread, base_y - 5,
                        cx - 2 + leg_spread, base_y], fill=CHF_BOOT, outline=OUTLINE)

        # Chef jacket body (side)
        ellipse(draw, cx - 2, body_cy, 12, 12, CHF_UNIFORM)
        ellipse(draw, cx - 4, body_cy - 2, 7, 7, CHF_UNIFORM_LIGHT, outline=None)
        # Apron (side view)
        draw.rectangle([cx - 8, body_cy + 2, cx + 4, body_cy + 14],
                       fill=CHF_APRON, outline=OUTLINE)
        draw.point((cx - 4, body_cy + 6), fill=CHF_APRON_STAIN_RED)
        draw.point((cx - 2, body_cy + 10), fill=CHF_APRON_STAIN_BROWN)
        # Belt
        draw.rectangle([cx - 14, body_cy + 12, cx + 10, body_cy + 14],
                       fill=CHF_BELT, outline=OUTLINE)

        # Front arm with cleaver
        draw.rectangle([cx - 14, body_cy - 4, cx - 8, body_cy + 6],
                       fill=CHF_UNIFORM, outline=OUTLINE)
        draw.rectangle([cx - 14, body_cy + 2, cx - 8, body_cy + 6],
                       fill=CHF_SKIN, outline=OUTLINE)
        ellipse(draw, cx - 10, body_cy - 6, 5, 3, CHF_UNIFORM_LIGHT)
        # Cleaver
        clv_x = cx - 16
        clv_y = body_cy + chop_offset
        draw.rectangle([clv_x - 1, clv_y - 6, clv_x + 1, clv_y],
                       fill=CHF_CLEAVER_HANDLE, outline=OUTLINE)
        draw.rectangle([clv_x - 3, clv_y - 12, clv_x + 3, clv_y - 6],
                       fill=CHF_CLEAVER_BLADE, outline=OUTLINE)
        draw.line([(clv_x - 2, clv_y - 10), (clv_x + 2, clv_y - 10)],
                  fill=CHF_CLEAVER_LIGHT, width=1)

        # Neckerchief (side)
        draw.polygon([(cx - 6, body_cy - 10), (cx + 2, body_cy - 10),
                      (cx - 2, body_cy - 4)], fill=CHF_NECKERCHIEF, outline=OUTLINE)

        # Head
        ellipse(draw, cx - 2, head_cy, 13, 13, CHF_HAIR)
        ellipse(draw, cx - 4, head_cy + 2, 10, 9, CHF_SKIN)
        ellipse(draw, cx - 2, head_cy + 4, 6, 5, CHF_SKIN_DARK, outline=None)
        # Eye
        draw.rectangle([cx - 10, head_cy + 1, cx - 7, head_cy + 4], fill=(255, 255, 255))
        draw.rectangle([cx - 9, head_cy + 2, cx - 8, head_cy + 3], fill=BLACK)
        # Nose and mouth
        draw.point((cx - 9, head_cy + 6), fill=CHF_SKIN_DARK)
        draw.line([(cx - 10, head_cy + 8), (cx - 7, head_cy + 8)],
                  fill=CHF_SKIN_DARK, width=1)
        # Mustache
        draw.line([(cx - 12, head_cy + 7), (cx - 8, head_cy + 6)],
                  fill=CHF_HAIR, width=1)

        # Toque (side)
        draw.rectangle([cx - 8, head_cy - 8, cx + 4, head_cy - 6],
                       fill=CHF_TOQUE_BAND, outline=OUTLINE)
        draw.rectangle([cx - 7, head_cy - 24, cx + 3, head_cy - 8],
                       fill=CHF_TOQUE, outline=OUTLINE)
        ellipse(draw, cx - 2, head_cy - 24, 5, 3, CHF_TOQUE)
        draw.line([(cx - 4, head_cy - 20), (cx - 4, head_cy - 10)],
                  fill=CHF_TOQUE_DARK, width=1)

    else:  # RIGHT
        # Legs
        draw.rectangle([cx - 2 + leg_spread, body_cy + 10,
                        cx + 4 + leg_spread, base_y],
                       fill=_darken(CHF_LEG, 0.85), outline=OUTLINE)
        draw.rectangle([cx - 2 + leg_spread, base_y - 5,
                        cx + 4 + leg_spread, base_y], fill=CHF_BOOT, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, body_cy + 10,
                        cx + 10 - leg_spread, base_y], fill=CHF_LEG, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, base_y - 5,
                        cx + 10 - leg_spread, base_y], fill=CHF_BOOT, outline=OUTLINE)

        # Chef jacket body (side)
        ellipse(draw, cx + 2, body_cy, 12, 12, CHF_UNIFORM)
        ellipse(draw, cx, body_cy - 2, 7, 7, CHF_UNIFORM_LIGHT, outline=None)
        # Apron
        draw.rectangle([cx - 4, body_cy + 2, cx + 8, body_cy + 14],
                       fill=CHF_APRON, outline=OUTLINE)
        draw.point((cx + 4, body_cy + 6), fill=CHF_APRON_STAIN_RED)
        draw.point((cx + 2, body_cy + 10), fill=CHF_APRON_STAIN_BROWN)
        # Belt
        draw.rectangle([cx - 10, body_cy + 12, cx + 14, body_cy + 14],
                       fill=CHF_BELT, outline=OUTLINE)

        # Front arm with cleaver
        draw.rectangle([cx + 8, body_cy - 4, cx + 14, body_cy + 6],
                       fill=CHF_UNIFORM, outline=OUTLINE)
        draw.rectangle([cx + 8, body_cy + 2, cx + 14, body_cy + 6],
                       fill=CHF_SKIN, outline=OUTLINE)
        ellipse(draw, cx + 10, body_cy - 6, 5, 3, CHF_UNIFORM_LIGHT)
        # Cleaver
        clv_x = cx + 16
        clv_y = body_cy + chop_offset
        draw.rectangle([clv_x - 1, clv_y - 6, clv_x + 1, clv_y],
                       fill=CHF_CLEAVER_HANDLE, outline=OUTLINE)
        draw.rectangle([clv_x - 3, clv_y - 12, clv_x + 3, clv_y - 6],
                       fill=CHF_CLEAVER_BLADE, outline=OUTLINE)
        draw.line([(clv_x - 2, clv_y - 10), (clv_x + 2, clv_y - 10)],
                  fill=CHF_CLEAVER_LIGHT, width=1)

        # Neckerchief (side)
        draw.polygon([(cx - 2, body_cy - 10), (cx + 6, body_cy - 10),
                      (cx + 2, body_cy - 4)], fill=CHF_NECKERCHIEF, outline=OUTLINE)

        # Head
        ellipse(draw, cx + 2, head_cy, 13, 13, CHF_HAIR)
        ellipse(draw, cx + 4, head_cy + 2, 10, 9, CHF_SKIN)
        ellipse(draw, cx + 6, head_cy + 4, 6, 5, CHF_SKIN_DARK, outline=None)
        # Eye
        draw.rectangle([cx + 7, head_cy + 1, cx + 10, head_cy + 4], fill=(255, 255, 255))
        draw.rectangle([cx + 8, head_cy + 2, cx + 9, head_cy + 3], fill=BLACK)
        # Nose and mouth
        draw.point((cx + 9, head_cy + 6), fill=CHF_SKIN_DARK)
        draw.line([(cx + 7, head_cy + 8), (cx + 10, head_cy + 8)],
                  fill=CHF_SKIN_DARK, width=1)
        # Mustache
        draw.line([(cx + 8, head_cy + 6), (cx + 12, head_cy + 7)],
                  fill=CHF_HAIR, width=1)

        # Toque (side)
        draw.rectangle([cx - 4, head_cy - 8, cx + 8, head_cy - 6],
                       fill=CHF_TOQUE_BAND, outline=OUTLINE)
        draw.rectangle([cx - 3, head_cy - 24, cx + 7, head_cy - 8],
                       fill=CHF_TOQUE, outline=OUTLINE)
        ellipse(draw, cx + 2, head_cy - 24, 5, 3, CHF_TOQUE)
        draw.line([(cx + 4, head_cy - 20), (cx + 4, head_cy - 10)],
                  fill=CHF_TOQUE_DARK, width=1)


# ===================================================================
# MUSICIAN (ID 108)
# Purple/dark outfit with feathered cap, lute on back, flowing cape,
# music note particles floating nearby, elegant pose.
# ===================================================================

# Musician palette
MUS_OUTFIT = (80, 40, 100)
MUS_OUTFIT_LIGHT = (110, 60, 135)
MUS_OUTFIT_DARK = (55, 25, 70)
MUS_OUTFIT_TRIM = (180, 150, 60)
MUS_CAPE = (100, 50, 130)
MUS_CAPE_DARK = (70, 35, 90)
MUS_CAPE_LIGHT = (130, 70, 160)
MUS_SKIN = (225, 200, 175)
MUS_SKIN_DARK = (195, 170, 145)
MUS_FEATHER_CAP = (70, 35, 90)
MUS_FEATHER_CAP_DARK = (50, 25, 65)
MUS_FEATHER = (200, 50, 50)
MUS_FEATHER_TIP = (240, 80, 70)
MUS_LUTE_BODY = (160, 110, 60)
MUS_LUTE_DARK = (120, 80, 40)
MUS_LUTE_NECK = (140, 95, 50)
MUS_LUTE_STRING = (200, 195, 180)
MUS_NOTE = (255, 220, 80)
MUS_NOTE_DIM = (200, 170, 60)
MUS_BELT = (90, 60, 40)
MUS_BUCKLE = (200, 180, 60)
MUS_LEG = (70, 45, 55)
MUS_BOOT = (55, 35, 42)
MUS_HAIR = (50, 35, 25)


def draw_musician(draw, ox, oy, direction, frame):
    bob = [0, -2, 0, -1][frame]
    leg_spread = [-4, 0, 4, 0][frame]
    note_float = [-3, -6, -4, -7][frame]
    note_drift = [0, 2, 4, 1][frame]

    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 20
    head_cy = body_cy - 20

    if direction == DOWN:
        # Cape behind body
        draw.polygon([(cx - 12, body_cy - 8), (cx + 12, body_cy - 8),
                      (cx + 14, body_cy + 14), (cx - 14, body_cy + 14)],
                     fill=MUS_CAPE, outline=OUTLINE)
        draw.polygon([(cx - 8, body_cy - 4), (cx + 8, body_cy - 4),
                      (cx + 10, body_cy + 10), (cx - 10, body_cy + 10)],
                     fill=MUS_CAPE_DARK, outline=None)

        # Legs
        draw.rectangle([cx - 10 + leg_spread, body_cy + 10,
                        cx - 4 + leg_spread, base_y], fill=MUS_LEG, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, body_cy + 10,
                        cx + 10 - leg_spread, base_y], fill=MUS_LEG, outline=OUTLINE)
        draw.rectangle([cx - 10 + leg_spread, base_y - 5,
                        cx - 4 + leg_spread, base_y], fill=MUS_BOOT, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, base_y - 5,
                        cx + 10 - leg_spread, base_y], fill=MUS_BOOT, outline=OUTLINE)

        # Body (tunic)
        ellipse(draw, cx, body_cy, 14, 12, MUS_OUTFIT)
        ellipse(draw, cx - 3, body_cy - 2, 8, 7, MUS_OUTFIT_LIGHT, outline=None)
        # V-neck trim
        draw.line([(cx, body_cy - 8), (cx - 4, body_cy + 2)],
                  fill=MUS_OUTFIT_TRIM, width=1)
        draw.line([(cx, body_cy - 8), (cx + 4, body_cy + 2)],
                  fill=MUS_OUTFIT_TRIM, width=1)
        # Belt
        draw.rectangle([cx - 14, body_cy + 10, cx + 14, body_cy + 14],
                       fill=MUS_BELT, outline=OUTLINE)
        draw.rectangle([cx - 2, body_cy + 10, cx + 2, body_cy + 14],
                       fill=MUS_BUCKLE, outline=OUTLINE)

        # Arms
        draw.rectangle([cx - 18, body_cy - 6, cx - 12, body_cy + 6],
                       fill=MUS_OUTFIT, outline=OUTLINE)
        draw.rectangle([cx - 18, body_cy + 2, cx - 12, body_cy + 6],
                       fill=MUS_SKIN, outline=OUTLINE)
        draw.rectangle([cx + 12, body_cy - 6, cx + 18, body_cy + 6],
                       fill=MUS_OUTFIT, outline=OUTLINE)
        draw.rectangle([cx + 12, body_cy + 2, cx + 18, body_cy + 6],
                       fill=MUS_SKIN, outline=OUTLINE)
        # Puffy shoulders
        ellipse(draw, cx - 14, body_cy - 6, 5, 4, MUS_OUTFIT_LIGHT)
        ellipse(draw, cx + 14, body_cy - 6, 5, 4, MUS_OUTFIT_LIGHT)

        # Head
        ellipse(draw, cx, head_cy, 14, 13, MUS_HAIR)
        ellipse(draw, cx, head_cy + 2, 12, 10, MUS_SKIN)
        ellipse(draw, cx + 2, head_cy + 4, 8, 6, MUS_SKIN_DARK, outline=None)
        # Eyes
        draw.rectangle([cx - 6, head_cy + 1, cx - 3, head_cy + 4], fill=(255, 255, 255))
        draw.rectangle([cx - 5, head_cy + 2, cx - 4, head_cy + 3], fill=BLACK)
        draw.rectangle([cx + 3, head_cy + 1, cx + 6, head_cy + 4], fill=(255, 255, 255))
        draw.rectangle([cx + 4, head_cy + 2, cx + 5, head_cy + 3], fill=BLACK)
        # Nose and smile
        draw.point((cx, head_cy + 6), fill=MUS_SKIN_DARK)
        draw.line([(cx - 3, head_cy + 8), (cx + 3, head_cy + 8)],
                  fill=MUS_SKIN_DARK, width=1)

        # Feathered cap
        draw.rectangle([cx - 10, head_cy - 8, cx + 10, head_cy - 4],
                       fill=MUS_FEATHER_CAP, outline=OUTLINE)
        ellipse(draw, cx, head_cy - 8, 10, 3, MUS_FEATHER_CAP)
        draw.rectangle([cx - 8, head_cy - 7, cx + 8, head_cy - 5],
                       fill=MUS_FEATHER_CAP_DARK, outline=None)
        # Feather plume
        draw.line([(cx + 6, head_cy - 8), (cx + 14, head_cy - 18)],
                  fill=MUS_FEATHER, width=2)
        draw.line([(cx + 14, head_cy - 18), (cx + 16, head_cy - 22)],
                  fill=MUS_FEATHER_TIP, width=1)

        # Music notes floating
        nt_x = cx - 14 + note_drift
        nt_y = head_cy - 10 + note_float
        draw.ellipse([nt_x - 2, nt_y - 1, nt_x + 2, nt_y + 1], fill=MUS_NOTE)
        draw.line([(nt_x + 2, nt_y - 1), (nt_x + 2, nt_y - 6)],
                  fill=MUS_NOTE, width=1)
        nt_x2 = cx + 16 - note_drift
        nt_y2 = head_cy - 4 + note_float
        draw.ellipse([nt_x2 - 2, nt_y2 - 1, nt_x2 + 2, nt_y2 + 1],
                     fill=MUS_NOTE_DIM)
        draw.line([(nt_x2 + 2, nt_y2 - 1), (nt_x2 + 2, nt_y2 - 5)],
                  fill=MUS_NOTE_DIM, width=1)

    elif direction == UP:
        # Lute on back (visible from behind)
        lute_x = cx
        lute_y = body_cy - 2
        ellipse(draw, lute_x, lute_y + 2, 8, 10, MUS_LUTE_BODY)
        ellipse(draw, lute_x, lute_y + 2, 5, 7, MUS_LUTE_DARK, outline=None)
        draw.rectangle([lute_x - 2, lute_y - 14, lute_x + 2, lute_y - 4],
                       fill=MUS_LUTE_NECK, outline=OUTLINE)
        # Strings
        draw.line([(lute_x - 1, lute_y - 12), (lute_x - 1, lute_y + 6)],
                  fill=MUS_LUTE_STRING, width=1)
        draw.line([(lute_x + 1, lute_y - 12), (lute_x + 1, lute_y + 6)],
                  fill=MUS_LUTE_STRING, width=1)

        # Cape behind
        draw.polygon([(cx - 12, body_cy - 8), (cx + 12, body_cy - 8),
                      (cx + 16, body_cy + 16), (cx - 16, body_cy + 16)],
                     fill=MUS_CAPE, outline=OUTLINE)
        draw.polygon([(cx - 8, body_cy - 4), (cx + 8, body_cy - 4),
                      (cx + 12, body_cy + 12), (cx - 12, body_cy + 12)],
                     fill=MUS_CAPE_DARK, outline=None)

        # Legs
        draw.rectangle([cx - 10 + leg_spread, body_cy + 10,
                        cx - 4 + leg_spread, base_y], fill=MUS_LEG, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, body_cy + 10,
                        cx + 10 - leg_spread, base_y], fill=MUS_LEG, outline=OUTLINE)
        draw.rectangle([cx - 10 + leg_spread, base_y - 5,
                        cx - 4 + leg_spread, base_y], fill=MUS_BOOT, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, base_y - 5,
                        cx + 10 - leg_spread, base_y], fill=MUS_BOOT, outline=OUTLINE)

        # Body (back)
        ellipse(draw, cx, body_cy, 14, 12, MUS_OUTFIT)
        ellipse(draw, cx, body_cy, 10, 9, MUS_OUTFIT_DARK, outline=None)
        draw.line([(cx, body_cy - 6), (cx, body_cy + 6)],
                  fill=_darken(MUS_OUTFIT, 0.7), width=1)
        # Belt
        draw.rectangle([cx - 14, body_cy + 10, cx + 14, body_cy + 14],
                       fill=MUS_BELT, outline=OUTLINE)

        # Arms
        draw.rectangle([cx - 18, body_cy - 6, cx - 12, body_cy + 6],
                       fill=MUS_OUTFIT, outline=OUTLINE)
        draw.rectangle([cx + 12, body_cy - 6, cx + 18, body_cy + 6],
                       fill=MUS_OUTFIT, outline=OUTLINE)
        ellipse(draw, cx - 14, body_cy - 6, 5, 4, MUS_OUTFIT_LIGHT)
        ellipse(draw, cx + 14, body_cy - 6, 5, 4, MUS_OUTFIT_LIGHT)

        # Head (back)
        ellipse(draw, cx, head_cy, 14, 13, MUS_HAIR)
        ellipse(draw, cx, head_cy - 2, 10, 8, _darken(MUS_HAIR, 0.85), outline=None)
        # Feathered cap from back
        draw.rectangle([cx - 10, head_cy - 8, cx + 10, head_cy - 4],
                       fill=MUS_FEATHER_CAP, outline=OUTLINE)
        ellipse(draw, cx, head_cy - 8, 10, 3, MUS_FEATHER_CAP)
        draw.line([(cx + 6, head_cy - 8), (cx + 14, head_cy - 18)],
                  fill=MUS_FEATHER, width=2)
        draw.line([(cx + 14, head_cy - 18), (cx + 16, head_cy - 22)],
                  fill=MUS_FEATHER_TIP, width=1)

    elif direction == LEFT:
        # Cape trailing right
        draw.polygon([(cx + 4, body_cy - 8), (cx + 16, body_cy + 14),
                      (cx + 6, body_cy + 14), (cx + 2, body_cy - 4)],
                     fill=MUS_CAPE, outline=OUTLINE)
        draw.polygon([(cx + 5, body_cy - 4), (cx + 12, body_cy + 10),
                      (cx + 6, body_cy + 10), (cx + 4, body_cy - 2)],
                     fill=MUS_CAPE_DARK, outline=None)

        # Legs
        draw.rectangle([cx - 2 - leg_spread, body_cy + 10,
                        cx + 4 - leg_spread, base_y],
                       fill=_darken(MUS_LEG, 0.85), outline=OUTLINE)
        draw.rectangle([cx - 2 - leg_spread, base_y - 5,
                        cx + 4 - leg_spread, base_y], fill=MUS_BOOT, outline=OUTLINE)
        draw.rectangle([cx - 8 + leg_spread, body_cy + 10,
                        cx - 2 + leg_spread, base_y], fill=MUS_LEG, outline=OUTLINE)
        draw.rectangle([cx - 8 + leg_spread, base_y - 5,
                        cx - 2 + leg_spread, base_y], fill=MUS_BOOT, outline=OUTLINE)

        # Body (side)
        ellipse(draw, cx - 2, body_cy, 12, 12, MUS_OUTFIT)
        ellipse(draw, cx - 4, body_cy - 2, 7, 7, MUS_OUTFIT_LIGHT, outline=None)
        # Trim line
        draw.line([(cx - 4, body_cy - 8), (cx - 6, body_cy + 2)],
                  fill=MUS_OUTFIT_TRIM, width=1)
        # Belt
        draw.rectangle([cx - 14, body_cy + 10, cx + 10, body_cy + 14],
                       fill=MUS_BELT, outline=OUTLINE)
        draw.rectangle([cx - 4, body_cy + 10, cx, body_cy + 14],
                       fill=MUS_BUCKLE, outline=OUTLINE)

        # Lute peeking from behind (strap visible)
        draw.line([(cx + 2, body_cy - 8), (cx + 6, body_cy + 4)],
                  fill=MUS_LUTE_DARK, width=2)
        ellipse(draw, cx + 6, body_cy + 6, 5, 6, MUS_LUTE_BODY)

        # Front arm
        draw.rectangle([cx - 14, body_cy - 4, cx - 8, body_cy + 6],
                       fill=MUS_OUTFIT, outline=OUTLINE)
        draw.rectangle([cx - 14, body_cy + 2, cx - 8, body_cy + 6],
                       fill=MUS_SKIN, outline=OUTLINE)
        ellipse(draw, cx - 10, body_cy - 6, 5, 4, MUS_OUTFIT_LIGHT)

        # Head
        ellipse(draw, cx - 2, head_cy, 13, 13, MUS_HAIR)
        ellipse(draw, cx - 4, head_cy + 2, 10, 9, MUS_SKIN)
        ellipse(draw, cx - 2, head_cy + 4, 6, 5, MUS_SKIN_DARK, outline=None)
        # Eye
        draw.rectangle([cx - 10, head_cy + 1, cx - 7, head_cy + 4], fill=(255, 255, 255))
        draw.rectangle([cx - 9, head_cy + 2, cx - 8, head_cy + 3], fill=BLACK)
        # Nose and smile
        draw.point((cx - 9, head_cy + 6), fill=MUS_SKIN_DARK)
        draw.line([(cx - 10, head_cy + 8), (cx - 7, head_cy + 8)],
                  fill=MUS_SKIN_DARK, width=1)

        # Feathered cap
        draw.rectangle([cx - 10, head_cy - 8, cx + 2, head_cy - 4],
                       fill=MUS_FEATHER_CAP, outline=OUTLINE)
        ellipse(draw, cx - 4, head_cy - 8, 7, 3, MUS_FEATHER_CAP)
        # Feather pointing back-right
        draw.line([(cx, head_cy - 8), (cx + 10, head_cy - 16)],
                  fill=MUS_FEATHER, width=2)
        draw.line([(cx + 10, head_cy - 16), (cx + 12, head_cy - 20)],
                  fill=MUS_FEATHER_TIP, width=1)

        # Music notes
        nt_x = cx - 18 + note_drift
        nt_y = head_cy - 8 + note_float
        draw.ellipse([nt_x - 2, nt_y - 1, nt_x + 2, nt_y + 1], fill=MUS_NOTE)
        draw.line([(nt_x + 2, nt_y - 1), (nt_x + 2, nt_y - 6)],
                  fill=MUS_NOTE, width=1)

    else:  # RIGHT
        # Cape trailing left
        draw.polygon([(cx - 4, body_cy - 8), (cx - 16, body_cy + 14),
                      (cx - 6, body_cy + 14), (cx - 2, body_cy - 4)],
                     fill=MUS_CAPE, outline=OUTLINE)
        draw.polygon([(cx - 5, body_cy - 4), (cx - 12, body_cy + 10),
                      (cx - 6, body_cy + 10), (cx - 4, body_cy - 2)],
                     fill=MUS_CAPE_DARK, outline=None)

        # Legs
        draw.rectangle([cx - 2 + leg_spread, body_cy + 10,
                        cx + 4 + leg_spread, base_y],
                       fill=_darken(MUS_LEG, 0.85), outline=OUTLINE)
        draw.rectangle([cx - 2 + leg_spread, base_y - 5,
                        cx + 4 + leg_spread, base_y], fill=MUS_BOOT, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, body_cy + 10,
                        cx + 10 - leg_spread, base_y], fill=MUS_LEG, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, base_y - 5,
                        cx + 10 - leg_spread, base_y], fill=MUS_BOOT, outline=OUTLINE)

        # Body (side)
        ellipse(draw, cx + 2, body_cy, 12, 12, MUS_OUTFIT)
        ellipse(draw, cx, body_cy - 2, 7, 7, MUS_OUTFIT_LIGHT, outline=None)
        # Trim line
        draw.line([(cx + 4, body_cy - 8), (cx + 6, body_cy + 2)],
                  fill=MUS_OUTFIT_TRIM, width=1)
        # Belt
        draw.rectangle([cx - 10, body_cy + 10, cx + 14, body_cy + 14],
                       fill=MUS_BELT, outline=OUTLINE)
        draw.rectangle([cx, body_cy + 10, cx + 4, body_cy + 14],
                       fill=MUS_BUCKLE, outline=OUTLINE)

        # Lute peeking from behind (strap visible)
        draw.line([(cx - 2, body_cy - 8), (cx - 6, body_cy + 4)],
                  fill=MUS_LUTE_DARK, width=2)
        ellipse(draw, cx - 6, body_cy + 6, 5, 6, MUS_LUTE_BODY)

        # Front arm
        draw.rectangle([cx + 8, body_cy - 4, cx + 14, body_cy + 6],
                       fill=MUS_OUTFIT, outline=OUTLINE)
        draw.rectangle([cx + 8, body_cy + 2, cx + 14, body_cy + 6],
                       fill=MUS_SKIN, outline=OUTLINE)
        ellipse(draw, cx + 10, body_cy - 6, 5, 4, MUS_OUTFIT_LIGHT)

        # Head
        ellipse(draw, cx + 2, head_cy, 13, 13, MUS_HAIR)
        ellipse(draw, cx + 4, head_cy + 2, 10, 9, MUS_SKIN)
        ellipse(draw, cx + 6, head_cy + 4, 6, 5, MUS_SKIN_DARK, outline=None)
        # Eye
        draw.rectangle([cx + 7, head_cy + 1, cx + 10, head_cy + 4], fill=(255, 255, 255))
        draw.rectangle([cx + 8, head_cy + 2, cx + 9, head_cy + 3], fill=BLACK)
        # Nose and smile
        draw.point((cx + 9, head_cy + 6), fill=MUS_SKIN_DARK)
        draw.line([(cx + 7, head_cy + 8), (cx + 10, head_cy + 8)],
                  fill=MUS_SKIN_DARK, width=1)

        # Feathered cap
        draw.rectangle([cx - 2, head_cy - 8, cx + 10, head_cy - 4],
                       fill=MUS_FEATHER_CAP, outline=OUTLINE)
        ellipse(draw, cx + 4, head_cy - 8, 7, 3, MUS_FEATHER_CAP)
        # Feather pointing back-left
        draw.line([(cx, head_cy - 8), (cx - 10, head_cy - 16)],
                  fill=MUS_FEATHER, width=2)
        draw.line([(cx - 10, head_cy - 16), (cx - 12, head_cy - 20)],
                  fill=MUS_FEATHER_TIP, width=1)

        # Music notes
        nt_x = cx + 18 - note_drift
        nt_y = head_cy - 8 + note_float
        draw.ellipse([nt_x - 2, nt_y - 1, nt_x + 2, nt_y + 1], fill=MUS_NOTE)
        draw.line([(nt_x + 2, nt_y - 1), (nt_x + 2, nt_y - 6)],
                  fill=MUS_NOTE, width=1)


# ===================================================================
# ASTRONOMER (ID 109)
# Dark blue/navy robes with star patterns, pointed hat with moon/star
# decal, telescope prop, constellation dots, night sky themed.
# ===================================================================

# Astronomer palette
AST_ROBE = (30, 40, 80)
AST_ROBE_LIGHT = (50, 65, 115)
AST_ROBE_DARK = (20, 28, 55)
AST_STAR_BRIGHT = (255, 240, 160)
AST_STAR_DIM = (200, 190, 120)
AST_MOON = (240, 235, 180)
AST_MOON_DARK = (200, 195, 145)
AST_SKIN = (220, 200, 175)
AST_SKIN_DARK = (190, 170, 145)
AST_HAT = (25, 35, 70)
AST_HAT_DARK = (18, 25, 50)
AST_HAT_BAND = (60, 50, 100)
AST_TELESCOPE = (120, 100, 70)
AST_TELESCOPE_DARK = (85, 70, 50)
AST_TELESCOPE_LENS = (140, 180, 220)
AST_TELESCOPE_RIM = (160, 140, 90)
AST_BELT = (60, 50, 90)
AST_BELT_BUCKLE = (180, 170, 120)
AST_LEG = (40, 45, 70)
AST_BOOT = (30, 32, 50)
AST_HAIR = (160, 155, 145)
AST_CONSTELLATION = (180, 200, 255)


def draw_astronomer(draw, ox, oy, direction, frame):
    bob = [0, -2, 0, -1][frame]
    leg_spread = [-4, 0, 4, 0][frame]
    twinkle = [0, 1, 0, -1][frame]

    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 20
    head_cy = body_cy - 20

    if direction == DOWN:
        # Robe skirt (long flowing robe)
        draw.polygon([(cx - 14, body_cy + 6), (cx + 14, body_cy + 6),
                      (cx + 16, base_y), (cx - 16, base_y)],
                     fill=AST_ROBE, outline=OUTLINE)
        draw.polygon([(cx - 10, body_cy + 8), (cx + 10, body_cy + 8),
                      (cx + 12, base_y - 2), (cx - 12, base_y - 2)],
                     fill=AST_ROBE_DARK, outline=None)
        # Stars on robe
        draw.point((cx - 6, body_cy + 12), fill=AST_STAR_BRIGHT)
        draw.point((cx + 8, body_cy + 14), fill=AST_STAR_DIM)
        draw.point((cx - 2, base_y - 4), fill=AST_STAR_BRIGHT)
        draw.point((cx + 4, body_cy + 10), fill=AST_STAR_DIM)

        # Boots peeking under robe
        draw.rectangle([cx - 8 + leg_spread, base_y - 4,
                        cx - 4 + leg_spread, base_y], fill=AST_BOOT, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, base_y - 4,
                        cx + 8 - leg_spread, base_y], fill=AST_BOOT, outline=OUTLINE)

        # Upper body
        ellipse(draw, cx, body_cy, 14, 12, AST_ROBE)
        ellipse(draw, cx - 3, body_cy - 2, 8, 7, AST_ROBE_LIGHT, outline=None)
        # Stars on chest
        draw.point((cx + 5, body_cy - 2), fill=AST_STAR_BRIGHT)
        draw.point((cx - 7, body_cy + 3), fill=AST_STAR_DIM)
        # Belt / sash
        draw.rectangle([cx - 14, body_cy + 8, cx + 14, body_cy + 12],
                       fill=AST_BELT, outline=OUTLINE)
        draw.rectangle([cx - 2, body_cy + 8, cx + 2, body_cy + 12],
                       fill=AST_BELT_BUCKLE, outline=OUTLINE)

        # Arms (long sleeves)
        draw.rectangle([cx - 18, body_cy - 6, cx - 12, body_cy + 8],
                       fill=AST_ROBE, outline=OUTLINE)
        draw.rectangle([cx - 18, body_cy + 4, cx - 12, body_cy + 8],
                       fill=AST_SKIN, outline=OUTLINE)
        draw.rectangle([cx + 12, body_cy - 6, cx + 18, body_cy + 8],
                       fill=AST_ROBE, outline=OUTLINE)
        draw.rectangle([cx + 12, body_cy + 4, cx + 18, body_cy + 8],
                       fill=AST_SKIN, outline=OUTLINE)
        # Star on sleeve
        draw.point((cx - 15, body_cy - 2), fill=AST_STAR_BRIGHT)
        draw.point((cx + 15, body_cy), fill=AST_STAR_DIM)

        # Telescope in right hand
        tl_x = cx + 20
        tl_y = body_cy - 4
        draw.rectangle([tl_x - 1, tl_y - 12, tl_x + 1, tl_y + 2],
                       fill=AST_TELESCOPE, outline=OUTLINE)
        draw.rectangle([tl_x - 1, tl_y - 10, tl_x + 1, tl_y - 8],
                       fill=AST_TELESCOPE_DARK, outline=None)
        # Lens end
        draw.rectangle([tl_x - 2, tl_y - 14, tl_x + 2, tl_y - 12],
                       fill=AST_TELESCOPE_RIM, outline=OUTLINE)
        draw.point((tl_x, tl_y - 13), fill=AST_TELESCOPE_LENS)

        # Head
        ellipse(draw, cx, head_cy, 14, 13, AST_HAIR)
        ellipse(draw, cx, head_cy + 2, 12, 10, AST_SKIN)
        ellipse(draw, cx + 2, head_cy + 4, 8, 6, AST_SKIN_DARK, outline=None)
        # Eyes
        draw.rectangle([cx - 6, head_cy + 1, cx - 3, head_cy + 4], fill=(255, 255, 255))
        draw.rectangle([cx - 5, head_cy + 2, cx - 4, head_cy + 3], fill=BLACK)
        draw.rectangle([cx + 3, head_cy + 1, cx + 6, head_cy + 4], fill=(255, 255, 255))
        draw.rectangle([cx + 4, head_cy + 2, cx + 5, head_cy + 3], fill=BLACK)
        # Nose and mouth
        draw.point((cx, head_cy + 6), fill=AST_SKIN_DARK)
        draw.line([(cx - 2, head_cy + 8), (cx + 2, head_cy + 8)],
                  fill=AST_SKIN_DARK, width=1)
        # Beard (wise astronomer)
        draw.line([(cx - 4, head_cy + 9), (cx + 4, head_cy + 9)],
                  fill=AST_HAIR, width=1)
        draw.line([(cx - 3, head_cy + 10), (cx + 3, head_cy + 10)],
                  fill=AST_HAIR, width=1)

        # Pointed wizard-style hat with moon/star
        draw.polygon([(cx - 10, head_cy - 6), (cx + 10, head_cy - 6),
                      (cx + 4, head_cy - 28)], fill=AST_HAT, outline=OUTLINE)
        draw.rectangle([cx - 12, head_cy - 8, cx + 12, head_cy - 6],
                       fill=AST_HAT_BAND, outline=OUTLINE)
        # Moon decal on hat
        ellipse(draw, cx - 2, head_cy - 16, 3, 3, AST_MOON)
        ellipse(draw, cx - 1, head_cy - 16, 2, 2, AST_HAT, outline=None)
        # Star decal near tip
        draw.point((cx + 2, head_cy - 22 + twinkle), fill=AST_STAR_BRIGHT)

        # Constellation dots floating nearby
        draw.point((cx - 18, head_cy - 14 + twinkle), fill=AST_CONSTELLATION)
        draw.point((cx - 16, head_cy - 18), fill=AST_CONSTELLATION)
        draw.line([(cx - 18, head_cy - 14 + twinkle),
                   (cx - 16, head_cy - 18)], fill=AST_CONSTELLATION, width=1)
        draw.point((cx + 18, head_cy - 10 - twinkle), fill=AST_CONSTELLATION)

    elif direction == UP:
        # Robe skirt (back)
        draw.polygon([(cx - 14, body_cy + 6), (cx + 14, body_cy + 6),
                      (cx + 16, base_y), (cx - 16, base_y)],
                     fill=AST_ROBE, outline=OUTLINE)
        draw.polygon([(cx - 10, body_cy + 8), (cx + 10, body_cy + 8),
                      (cx + 12, base_y - 2), (cx - 12, base_y - 2)],
                     fill=AST_ROBE_DARK, outline=None)
        draw.point((cx - 4, body_cy + 14), fill=AST_STAR_DIM)
        draw.point((cx + 6, base_y - 6), fill=AST_STAR_BRIGHT)
        # Boots
        draw.rectangle([cx - 8 + leg_spread, base_y - 4,
                        cx - 4 + leg_spread, base_y], fill=AST_BOOT, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, base_y - 4,
                        cx + 8 - leg_spread, base_y], fill=AST_BOOT, outline=OUTLINE)

        # Upper body (back)
        ellipse(draw, cx, body_cy, 14, 12, AST_ROBE)
        ellipse(draw, cx, body_cy, 10, 9, AST_ROBE_DARK, outline=None)
        draw.line([(cx, body_cy - 6), (cx, body_cy + 6)],
                  fill=_darken(AST_ROBE, 0.7), width=1)
        draw.point((cx + 6, body_cy - 4), fill=AST_STAR_DIM)
        draw.point((cx - 8, body_cy + 2), fill=AST_STAR_BRIGHT)
        # Belt
        draw.rectangle([cx - 14, body_cy + 8, cx + 14, body_cy + 12],
                       fill=AST_BELT, outline=OUTLINE)

        # Arms
        draw.rectangle([cx - 18, body_cy - 6, cx - 12, body_cy + 8],
                       fill=AST_ROBE, outline=OUTLINE)
        draw.rectangle([cx + 12, body_cy - 6, cx + 18, body_cy + 8],
                       fill=AST_ROBE, outline=OUTLINE)

        # Head (back)
        ellipse(draw, cx, head_cy, 14, 13, AST_HAIR)
        ellipse(draw, cx, head_cy - 2, 10, 8, _darken(AST_HAIR, 0.85), outline=None)

        # Pointed hat (back)
        draw.polygon([(cx - 10, head_cy - 6), (cx + 10, head_cy - 6),
                      (cx + 4, head_cy - 28)], fill=AST_HAT, outline=OUTLINE)
        draw.rectangle([cx - 12, head_cy - 8, cx + 12, head_cy - 6],
                       fill=AST_HAT_BAND, outline=OUTLINE)
        draw.point((cx, head_cy - 20), fill=AST_STAR_DIM)

    elif direction == LEFT:
        # Robe skirt (side)
        draw.polygon([(cx - 10, body_cy + 6), (cx + 8, body_cy + 6),
                      (cx + 10, base_y), (cx - 14, base_y)],
                     fill=AST_ROBE, outline=OUTLINE)
        draw.polygon([(cx - 6, body_cy + 8), (cx + 4, body_cy + 8),
                      (cx + 6, base_y - 2), (cx - 10, base_y - 2)],
                     fill=AST_ROBE_DARK, outline=None)
        draw.point((cx - 6, body_cy + 14), fill=AST_STAR_BRIGHT)
        draw.point((cx + 2, base_y - 4), fill=AST_STAR_DIM)
        # Boot
        draw.rectangle([cx - 6 + leg_spread, base_y - 4,
                        cx - 2 + leg_spread, base_y], fill=AST_BOOT, outline=OUTLINE)

        # Upper body (side)
        ellipse(draw, cx - 2, body_cy, 12, 12, AST_ROBE)
        ellipse(draw, cx - 4, body_cy - 2, 7, 7, AST_ROBE_LIGHT, outline=None)
        draw.point((cx - 8, body_cy + 2), fill=AST_STAR_BRIGHT)
        # Belt
        draw.rectangle([cx - 14, body_cy + 8, cx + 10, body_cy + 12],
                       fill=AST_BELT, outline=OUTLINE)
        draw.rectangle([cx - 4, body_cy + 8, cx, body_cy + 12],
                       fill=AST_BELT_BUCKLE, outline=OUTLINE)

        # Telescope held out front
        tl_x = cx - 18
        tl_y = body_cy - 6
        draw.line([(tl_x, tl_y), (tl_x - 10, tl_y - 6)],
                  fill=AST_TELESCOPE, width=2)
        draw.rectangle([tl_x - 12, tl_y - 8, tl_x - 9, tl_y - 5],
                       fill=AST_TELESCOPE_RIM, outline=OUTLINE)
        draw.point((tl_x - 10, tl_y - 6), fill=AST_TELESCOPE_LENS)

        # Front arm
        draw.rectangle([cx - 14, body_cy - 4, cx - 8, body_cy + 8],
                       fill=AST_ROBE, outline=OUTLINE)
        draw.rectangle([cx - 14, body_cy + 4, cx - 8, body_cy + 8],
                       fill=AST_SKIN, outline=OUTLINE)

        # Head
        ellipse(draw, cx - 2, head_cy, 13, 13, AST_HAIR)
        ellipse(draw, cx - 4, head_cy + 2, 10, 9, AST_SKIN)
        ellipse(draw, cx - 2, head_cy + 4, 6, 5, AST_SKIN_DARK, outline=None)
        # Eye
        draw.rectangle([cx - 10, head_cy + 1, cx - 7, head_cy + 4], fill=(255, 255, 255))
        draw.rectangle([cx - 9, head_cy + 2, cx - 8, head_cy + 3], fill=BLACK)
        # Nose and mouth
        draw.point((cx - 9, head_cy + 6), fill=AST_SKIN_DARK)
        draw.line([(cx - 10, head_cy + 8), (cx - 7, head_cy + 8)],
                  fill=AST_SKIN_DARK, width=1)
        # Beard
        draw.line([(cx - 10, head_cy + 9), (cx - 6, head_cy + 9)],
                  fill=AST_HAIR, width=1)
        draw.line([(cx - 9, head_cy + 10), (cx - 6, head_cy + 10)],
                  fill=AST_HAIR, width=1)

        # Pointed hat (side)
        draw.polygon([(cx - 10, head_cy - 6), (cx + 4, head_cy - 6),
                      (cx, head_cy - 26)], fill=AST_HAT, outline=OUTLINE)
        draw.rectangle([cx - 12, head_cy - 8, cx + 6, head_cy - 6],
                       fill=AST_HAT_BAND, outline=OUTLINE)
        # Moon decal
        ellipse(draw, cx - 4, head_cy - 14, 2, 2, AST_MOON)
        ellipse(draw, cx - 3, head_cy - 14, 1, 1, AST_HAT, outline=None)
        # Star
        draw.point((cx - 1, head_cy - 22 + twinkle), fill=AST_STAR_BRIGHT)

        # Constellation dots
        draw.point((cx - 20, head_cy - 12 + twinkle), fill=AST_CONSTELLATION)
        draw.point((cx - 18, head_cy - 16), fill=AST_CONSTELLATION)

    else:  # RIGHT
        # Robe skirt (side)
        draw.polygon([(cx - 8, body_cy + 6), (cx + 10, body_cy + 6),
                      (cx + 14, base_y), (cx - 10, base_y)],
                     fill=AST_ROBE, outline=OUTLINE)
        draw.polygon([(cx - 4, body_cy + 8), (cx + 6, body_cy + 8),
                      (cx + 10, base_y - 2), (cx - 6, base_y - 2)],
                     fill=AST_ROBE_DARK, outline=None)
        draw.point((cx + 6, body_cy + 14), fill=AST_STAR_BRIGHT)
        draw.point((cx - 2, base_y - 4), fill=AST_STAR_DIM)
        # Boot
        draw.rectangle([cx + 2 - leg_spread, base_y - 4,
                        cx + 6 - leg_spread, base_y], fill=AST_BOOT, outline=OUTLINE)

        # Upper body (side)
        ellipse(draw, cx + 2, body_cy, 12, 12, AST_ROBE)
        ellipse(draw, cx, body_cy - 2, 7, 7, AST_ROBE_LIGHT, outline=None)
        draw.point((cx + 8, body_cy + 2), fill=AST_STAR_BRIGHT)
        # Belt
        draw.rectangle([cx - 10, body_cy + 8, cx + 14, body_cy + 12],
                       fill=AST_BELT, outline=OUTLINE)
        draw.rectangle([cx, body_cy + 8, cx + 4, body_cy + 12],
                       fill=AST_BELT_BUCKLE, outline=OUTLINE)

        # Telescope held out front
        tl_x = cx + 18
        tl_y = body_cy - 6
        draw.line([(tl_x, tl_y), (tl_x + 10, tl_y - 6)],
                  fill=AST_TELESCOPE, width=2)
        draw.rectangle([tl_x + 9, tl_y - 8, tl_x + 12, tl_y - 5],
                       fill=AST_TELESCOPE_RIM, outline=OUTLINE)
        draw.point((tl_x + 10, tl_y - 6), fill=AST_TELESCOPE_LENS)

        # Front arm
        draw.rectangle([cx + 8, body_cy - 4, cx + 14, body_cy + 8],
                       fill=AST_ROBE, outline=OUTLINE)
        draw.rectangle([cx + 8, body_cy + 4, cx + 14, body_cy + 8],
                       fill=AST_SKIN, outline=OUTLINE)

        # Head
        ellipse(draw, cx + 2, head_cy, 13, 13, AST_HAIR)
        ellipse(draw, cx + 4, head_cy + 2, 10, 9, AST_SKIN)
        ellipse(draw, cx + 6, head_cy + 4, 6, 5, AST_SKIN_DARK, outline=None)
        # Eye
        draw.rectangle([cx + 7, head_cy + 1, cx + 10, head_cy + 4], fill=(255, 255, 255))
        draw.rectangle([cx + 8, head_cy + 2, cx + 9, head_cy + 3], fill=BLACK)
        # Nose and mouth
        draw.point((cx + 9, head_cy + 6), fill=AST_SKIN_DARK)
        draw.line([(cx + 7, head_cy + 8), (cx + 10, head_cy + 8)],
                  fill=AST_SKIN_DARK, width=1)
        # Beard
        draw.line([(cx + 6, head_cy + 9), (cx + 10, head_cy + 9)],
                  fill=AST_HAIR, width=1)
        draw.line([(cx + 6, head_cy + 10), (cx + 9, head_cy + 10)],
                  fill=AST_HAIR, width=1)

        # Pointed hat (side)
        draw.polygon([(cx - 4, head_cy - 6), (cx + 10, head_cy - 6),
                      (cx, head_cy - 26)], fill=AST_HAT, outline=OUTLINE)
        draw.rectangle([cx - 6, head_cy - 8, cx + 12, head_cy - 6],
                       fill=AST_HAT_BAND, outline=OUTLINE)
        # Moon decal
        ellipse(draw, cx + 4, head_cy - 14, 2, 2, AST_MOON)
        ellipse(draw, cx + 5, head_cy - 14, 1, 1, AST_HAT, outline=None)
        # Star
        draw.point((cx + 1, head_cy - 22 + twinkle), fill=AST_STAR_BRIGHT)

        # Constellation dots
        draw.point((cx + 20, head_cy - 12 + twinkle), fill=AST_CONSTELLATION)
        draw.point((cx + 18, head_cy - 16), fill=AST_CONSTELLATION)


# ===================================================================
# RUNESMITH (ID 110)
# Brown/tan leather apron over sturdy frame, glowing blue rune
# inscriptions on arms/chest, helm, forging hammer, runic stones.
# ===================================================================

# Runesmith palette
RNS_APRON = (140, 110, 70)
RNS_APRON_DARK = (110, 85, 55)
RNS_APRON_LIGHT = (170, 140, 95)
RNS_BODY = (160, 130, 100)
RNS_BODY_DARK = (130, 100, 75)
RNS_SKIN = (210, 180, 150)
RNS_SKIN_DARK = (180, 150, 120)
RNS_RUNE_BLUE = (80, 160, 255)
RNS_RUNE_BRIGHT = (140, 200, 255)
RNS_RUNE_DIM = (50, 110, 200)
RNS_HELM = (140, 145, 155)
RNS_HELM_DARK = (100, 105, 115)
RNS_HELM_LIGHT = (175, 180, 190)
RNS_HAMMER_HEAD = (150, 155, 165)
RNS_HAMMER_DARK = (110, 115, 125)
RNS_HAMMER_HANDLE = (100, 70, 40)
RNS_HAMMER_GLOW = (100, 180, 255)
RNS_BELT = (90, 65, 40)
RNS_BELT_BUCKLE = (120, 140, 180)
RNS_STONE = (100, 95, 85)
RNS_STONE_RUNE = (60, 140, 230)
RNS_LEG = (100, 80, 60)
RNS_BOOT = (70, 55, 40)
RNS_HAIR = (60, 45, 35)
RNS_GAUNTLET = (130, 135, 145)


def draw_runesmith(draw, ox, oy, direction, frame):
    bob = [0, -2, 0, -1][frame]
    leg_spread = [-4, 0, 4, 0][frame]
    rune_pulse = [0, 1, 0, -1][frame]

    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 20
    head_cy = body_cy - 20

    # Choose rune color based on frame for pulsing effect
    rune_color = RNS_RUNE_BLUE if frame % 2 == 0 else RNS_RUNE_BRIGHT

    if direction == DOWN:
        # Legs (sturdy)
        draw.rectangle([cx - 10 + leg_spread, body_cy + 10,
                        cx - 4 + leg_spread, base_y], fill=RNS_LEG, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, body_cy + 10,
                        cx + 10 - leg_spread, base_y], fill=RNS_LEG, outline=OUTLINE)
        # Boots
        draw.rectangle([cx - 11 + leg_spread, base_y - 6,
                        cx - 3 + leg_spread, base_y], fill=RNS_BOOT, outline=OUTLINE)
        draw.rectangle([cx + 3 - leg_spread, base_y - 6,
                        cx + 11 - leg_spread, base_y], fill=RNS_BOOT, outline=OUTLINE)

        # Sturdy body
        ellipse(draw, cx, body_cy, 15, 13, RNS_BODY)
        ellipse(draw, cx - 3, body_cy - 2, 9, 8, RNS_BODY_DARK, outline=None)
        # Leather apron over body
        draw.rectangle([cx - 10, body_cy - 2, cx + 10, body_cy + 14],
                       fill=RNS_APRON, outline=OUTLINE)
        draw.rectangle([cx - 8, body_cy, cx + 8, body_cy + 12],
                       fill=RNS_APRON_DARK, outline=None)
        # Apron stitching
        draw.line([(cx, body_cy - 2), (cx, body_cy + 14)],
                  fill=RNS_APRON_LIGHT, width=1)
        # Glowing rune on chest
        draw.point((cx - 4, body_cy + 2 + rune_pulse), fill=rune_color)
        draw.point((cx + 4, body_cy + 2 - rune_pulse), fill=rune_color)
        draw.point((cx, body_cy + 6), fill=rune_color)
        draw.line([(cx - 4, body_cy + 2 + rune_pulse), (cx, body_cy + 6)],
                  fill=RNS_RUNE_DIM, width=1)
        draw.line([(cx + 4, body_cy + 2 - rune_pulse), (cx, body_cy + 6)],
                  fill=RNS_RUNE_DIM, width=1)

        # Belt with runic stones
        draw.rectangle([cx - 15, body_cy + 10, cx + 15, body_cy + 14],
                       fill=RNS_BELT, outline=OUTLINE)
        draw.rectangle([cx - 2, body_cy + 10, cx + 2, body_cy + 14],
                       fill=RNS_BELT_BUCKLE, outline=OUTLINE)
        # Runic stones on belt
        draw.rectangle([cx - 10, body_cy + 10, cx - 7, body_cy + 13],
                       fill=RNS_STONE, outline=OUTLINE)
        draw.point((cx - 8, body_cy + 11), fill=RNS_STONE_RUNE)
        draw.rectangle([cx + 7, body_cy + 10, cx + 10, body_cy + 13],
                       fill=RNS_STONE, outline=OUTLINE)
        draw.point((cx + 8, body_cy + 11), fill=RNS_STONE_RUNE)

        # Arms with rune inscriptions
        draw.rectangle([cx - 19, body_cy - 6, cx - 12, body_cy + 6],
                       fill=RNS_BODY, outline=OUTLINE)
        draw.rectangle([cx - 19, body_cy + 2, cx - 12, body_cy + 6],
                       fill=RNS_GAUNTLET, outline=OUTLINE)
        draw.rectangle([cx + 12, body_cy - 6, cx + 19, body_cy + 6],
                       fill=RNS_BODY, outline=OUTLINE)
        draw.rectangle([cx + 12, body_cy + 2, cx + 19, body_cy + 6],
                       fill=RNS_GAUNTLET, outline=OUTLINE)
        # Rune marks on arms
        draw.point((cx - 16, body_cy - 2), fill=rune_color)
        draw.point((cx - 15, body_cy), fill=rune_color)
        draw.point((cx + 16, body_cy - 2), fill=rune_color)
        draw.point((cx + 15, body_cy), fill=rune_color)

        # Forging hammer in right hand
        hm_x = cx + 22
        hm_y = body_cy - 4
        draw.rectangle([hm_x - 1, hm_y - 6, hm_x + 1, hm_y + 4],
                       fill=RNS_HAMMER_HANDLE, outline=OUTLINE)
        draw.rectangle([hm_x - 4, hm_y - 12, hm_x + 4, hm_y - 6],
                       fill=RNS_HAMMER_HEAD, outline=OUTLINE)
        draw.rectangle([hm_x - 3, hm_y - 11, hm_x + 3, hm_y - 8],
                       fill=RNS_HAMMER_DARK, outline=None)
        # Rune glow on hammer head
        draw.point((hm_x, hm_y - 9), fill=RNS_HAMMER_GLOW)

        # Head
        ellipse(draw, cx, head_cy + 2, 12, 10, RNS_SKIN)
        ellipse(draw, cx + 2, head_cy + 4, 8, 6, RNS_SKIN_DARK, outline=None)
        # Eyes
        draw.rectangle([cx - 6, head_cy + 1, cx - 3, head_cy + 4], fill=(255, 255, 255))
        draw.rectangle([cx - 5, head_cy + 2, cx - 4, head_cy + 3], fill=BLACK)
        draw.rectangle([cx + 3, head_cy + 1, cx + 6, head_cy + 4], fill=(255, 255, 255))
        draw.rectangle([cx + 4, head_cy + 2, cx + 5, head_cy + 3], fill=BLACK)
        # Nose and mouth
        draw.point((cx, head_cy + 6), fill=RNS_SKIN_DARK)
        draw.line([(cx - 2, head_cy + 8), (cx + 2, head_cy + 8)],
                  fill=RNS_SKIN_DARK, width=1)
        # Thick brows
        draw.line([(cx - 7, head_cy), (cx - 3, head_cy - 1)],
                  fill=RNS_HAIR, width=1)
        draw.line([(cx + 3, head_cy - 1), (cx + 7, head_cy)],
                  fill=RNS_HAIR, width=1)

        # Helm
        draw.rectangle([cx - 12, head_cy - 10, cx + 12, head_cy - 2],
                       fill=RNS_HELM, outline=OUTLINE)
        draw.rectangle([cx - 10, head_cy - 9, cx + 10, head_cy - 4],
                       fill=RNS_HELM_DARK, outline=None)
        draw.line([(cx - 10, head_cy - 7), (cx + 10, head_cy - 7)],
                  fill=RNS_HELM_LIGHT, width=1)
        # Helm nose guard
        draw.rectangle([cx - 1, head_cy - 6, cx + 1, head_cy],
                       fill=RNS_HELM, outline=OUTLINE)
        # Rune on helm
        draw.point((cx - 6, head_cy - 6), fill=rune_color)
        draw.point((cx + 6, head_cy - 6), fill=rune_color)

    elif direction == UP:
        # Legs
        draw.rectangle([cx - 10 + leg_spread, body_cy + 10,
                        cx - 4 + leg_spread, base_y], fill=RNS_LEG, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, body_cy + 10,
                        cx + 10 - leg_spread, base_y], fill=RNS_LEG, outline=OUTLINE)
        draw.rectangle([cx - 11 + leg_spread, base_y - 6,
                        cx - 3 + leg_spread, base_y], fill=RNS_BOOT, outline=OUTLINE)
        draw.rectangle([cx + 3 - leg_spread, base_y - 6,
                        cx + 11 - leg_spread, base_y], fill=RNS_BOOT, outline=OUTLINE)

        # Body (back)
        ellipse(draw, cx, body_cy, 15, 13, RNS_BODY)
        ellipse(draw, cx, body_cy, 11, 10, RNS_BODY_DARK, outline=None)
        draw.line([(cx, body_cy - 6), (cx, body_cy + 6)],
                  fill=_darken(RNS_BODY, 0.7), width=1)
        # Apron ties from back
        draw.line([(cx - 6, body_cy + 4), (cx - 12, body_cy + 8)],
                  fill=RNS_APRON_DARK, width=2)
        draw.line([(cx + 6, body_cy + 4), (cx + 12, body_cy + 8)],
                  fill=RNS_APRON_DARK, width=2)
        # Belt
        draw.rectangle([cx - 15, body_cy + 10, cx + 15, body_cy + 14],
                       fill=RNS_BELT, outline=OUTLINE)

        # Arms
        draw.rectangle([cx - 19, body_cy - 6, cx - 12, body_cy + 6],
                       fill=RNS_BODY, outline=OUTLINE)
        draw.rectangle([cx - 19, body_cy + 2, cx - 12, body_cy + 6],
                       fill=RNS_GAUNTLET, outline=OUTLINE)
        draw.rectangle([cx + 12, body_cy - 6, cx + 19, body_cy + 6],
                       fill=RNS_BODY, outline=OUTLINE)
        draw.rectangle([cx + 12, body_cy + 2, cx + 19, body_cy + 6],
                       fill=RNS_GAUNTLET, outline=OUTLINE)
        # Rune marks visible from back
        draw.point((cx - 16, body_cy - 2), fill=rune_color)
        draw.point((cx + 16, body_cy - 2), fill=rune_color)

        # Head (back)
        ellipse(draw, cx, head_cy + 2, 12, 10, RNS_SKIN)
        ellipse(draw, cx, head_cy, 10, 8, _darken(RNS_HAIR, 0.85), outline=None)

        # Helm (back)
        draw.rectangle([cx - 12, head_cy - 10, cx + 12, head_cy - 2],
                       fill=RNS_HELM, outline=OUTLINE)
        draw.rectangle([cx - 10, head_cy - 9, cx + 10, head_cy - 4],
                       fill=RNS_HELM_DARK, outline=None)
        draw.line([(cx - 10, head_cy - 7), (cx + 10, head_cy - 7)],
                  fill=RNS_HELM_LIGHT, width=1)

    elif direction == LEFT:
        # Legs
        draw.rectangle([cx - 2 - leg_spread, body_cy + 10,
                        cx + 4 - leg_spread, base_y],
                       fill=_darken(RNS_LEG, 0.85), outline=OUTLINE)
        draw.rectangle([cx - 3 - leg_spread, base_y - 6,
                        cx + 5 - leg_spread, base_y], fill=RNS_BOOT, outline=OUTLINE)
        draw.rectangle([cx - 8 + leg_spread, body_cy + 10,
                        cx - 2 + leg_spread, base_y], fill=RNS_LEG, outline=OUTLINE)
        draw.rectangle([cx - 9 + leg_spread, base_y - 6,
                        cx - 1 + leg_spread, base_y], fill=RNS_BOOT, outline=OUTLINE)

        # Body (side)
        ellipse(draw, cx - 2, body_cy, 13, 13, RNS_BODY)
        # Apron (side view)
        draw.rectangle([cx - 10, body_cy - 2, cx + 2, body_cy + 14],
                       fill=RNS_APRON, outline=OUTLINE)
        draw.rectangle([cx - 8, body_cy, cx, body_cy + 12],
                       fill=RNS_APRON_DARK, outline=None)
        # Rune on apron
        draw.point((cx - 5, body_cy + 4 + rune_pulse), fill=rune_color)
        draw.point((cx - 3, body_cy + 6), fill=rune_color)
        # Belt
        draw.rectangle([cx - 15, body_cy + 10, cx + 10, body_cy + 14],
                       fill=RNS_BELT, outline=OUTLINE)
        draw.rectangle([cx - 8, body_cy + 10, cx - 5, body_cy + 13],
                       fill=RNS_STONE, outline=OUTLINE)
        draw.point((cx - 6, body_cy + 11), fill=RNS_STONE_RUNE)

        # Front arm with hammer
        draw.rectangle([cx - 15, body_cy - 4, cx - 9, body_cy + 6],
                       fill=RNS_BODY, outline=OUTLINE)
        draw.rectangle([cx - 15, body_cy + 2, cx - 9, body_cy + 6],
                       fill=RNS_GAUNTLET, outline=OUTLINE)
        # Rune on arm
        draw.point((cx - 12, body_cy - 1), fill=rune_color)
        # Hammer
        hm_x = cx - 18
        hm_y = body_cy - 2
        draw.rectangle([hm_x - 1, hm_y - 4, hm_x + 1, hm_y + 4],
                       fill=RNS_HAMMER_HANDLE, outline=OUTLINE)
        draw.rectangle([hm_x - 3, hm_y - 10, hm_x + 3, hm_y - 4],
                       fill=RNS_HAMMER_HEAD, outline=OUTLINE)
        draw.point((hm_x, hm_y - 7), fill=RNS_HAMMER_GLOW)

        # Head
        ellipse(draw, cx - 2, head_cy + 2, 11, 10, RNS_SKIN)
        ellipse(draw, cx - 2, head_cy + 4, 6, 5, RNS_SKIN_DARK, outline=None)
        # Eye
        draw.rectangle([cx - 10, head_cy + 1, cx - 7, head_cy + 4], fill=(255, 255, 255))
        draw.rectangle([cx - 9, head_cy + 2, cx - 8, head_cy + 3], fill=BLACK)
        # Nose and mouth
        draw.point((cx - 9, head_cy + 6), fill=RNS_SKIN_DARK)
        draw.line([(cx - 10, head_cy + 8), (cx - 7, head_cy + 8)],
                  fill=RNS_SKIN_DARK, width=1)
        # Thick brow
        draw.line([(cx - 11, head_cy), (cx - 7, head_cy - 1)],
                  fill=RNS_HAIR, width=1)

        # Helm (side)
        draw.rectangle([cx - 10, head_cy - 10, cx + 6, head_cy - 2],
                       fill=RNS_HELM, outline=OUTLINE)
        draw.rectangle([cx - 8, head_cy - 9, cx + 4, head_cy - 4],
                       fill=RNS_HELM_DARK, outline=None)
        draw.line([(cx - 8, head_cy - 7), (cx + 4, head_cy - 7)],
                  fill=RNS_HELM_LIGHT, width=1)
        # Nose guard
        draw.rectangle([cx - 8, head_cy - 4, cx - 6, head_cy],
                       fill=RNS_HELM, outline=OUTLINE)
        draw.point((cx - 4, head_cy - 6), fill=rune_color)

    else:  # RIGHT
        # Legs
        draw.rectangle([cx - 2 + leg_spread, body_cy + 10,
                        cx + 4 + leg_spread, base_y],
                       fill=_darken(RNS_LEG, 0.85), outline=OUTLINE)
        draw.rectangle([cx - 3 + leg_spread, base_y - 6,
                        cx + 5 + leg_spread, base_y], fill=RNS_BOOT, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, body_cy + 10,
                        cx + 10 - leg_spread, base_y], fill=RNS_LEG, outline=OUTLINE)
        draw.rectangle([cx + 3 - leg_spread, base_y - 6,
                        cx + 11 - leg_spread, base_y], fill=RNS_BOOT, outline=OUTLINE)

        # Body (side)
        ellipse(draw, cx + 2, body_cy, 13, 13, RNS_BODY)
        # Apron (side view)
        draw.rectangle([cx - 2, body_cy - 2, cx + 10, body_cy + 14],
                       fill=RNS_APRON, outline=OUTLINE)
        draw.rectangle([cx, body_cy, cx + 8, body_cy + 12],
                       fill=RNS_APRON_DARK, outline=None)
        # Rune on apron
        draw.point((cx + 5, body_cy + 4 + rune_pulse), fill=rune_color)
        draw.point((cx + 3, body_cy + 6), fill=rune_color)
        # Belt
        draw.rectangle([cx - 10, body_cy + 10, cx + 15, body_cy + 14],
                       fill=RNS_BELT, outline=OUTLINE)
        draw.rectangle([cx + 5, body_cy + 10, cx + 8, body_cy + 13],
                       fill=RNS_STONE, outline=OUTLINE)
        draw.point((cx + 6, body_cy + 11), fill=RNS_STONE_RUNE)

        # Front arm with hammer
        draw.rectangle([cx + 9, body_cy - 4, cx + 15, body_cy + 6],
                       fill=RNS_BODY, outline=OUTLINE)
        draw.rectangle([cx + 9, body_cy + 2, cx + 15, body_cy + 6],
                       fill=RNS_GAUNTLET, outline=OUTLINE)
        # Rune on arm
        draw.point((cx + 12, body_cy - 1), fill=rune_color)
        # Hammer
        hm_x = cx + 18
        hm_y = body_cy - 2
        draw.rectangle([hm_x - 1, hm_y - 4, hm_x + 1, hm_y + 4],
                       fill=RNS_HAMMER_HANDLE, outline=OUTLINE)
        draw.rectangle([hm_x - 3, hm_y - 10, hm_x + 3, hm_y - 4],
                       fill=RNS_HAMMER_HEAD, outline=OUTLINE)
        draw.point((hm_x, hm_y - 7), fill=RNS_HAMMER_GLOW)

        # Head
        ellipse(draw, cx + 2, head_cy + 2, 11, 10, RNS_SKIN)
        ellipse(draw, cx + 2, head_cy + 4, 6, 5, RNS_SKIN_DARK, outline=None)
        # Eye
        draw.rectangle([cx + 7, head_cy + 1, cx + 10, head_cy + 4], fill=(255, 255, 255))
        draw.rectangle([cx + 8, head_cy + 2, cx + 9, head_cy + 3], fill=BLACK)
        # Nose and mouth
        draw.point((cx + 9, head_cy + 6), fill=RNS_SKIN_DARK)
        draw.line([(cx + 7, head_cy + 8), (cx + 10, head_cy + 8)],
                  fill=RNS_SKIN_DARK, width=1)
        # Thick brow
        draw.line([(cx + 7, head_cy - 1), (cx + 11, head_cy)],
                  fill=RNS_HAIR, width=1)

        # Helm (side)
        draw.rectangle([cx - 6, head_cy - 10, cx + 10, head_cy - 2],
                       fill=RNS_HELM, outline=OUTLINE)
        draw.rectangle([cx - 4, head_cy - 9, cx + 8, head_cy - 4],
                       fill=RNS_HELM_DARK, outline=None)
        draw.line([(cx - 4, head_cy - 7), (cx + 8, head_cy - 7)],
                  fill=RNS_HELM_LIGHT, width=1)
        # Nose guard
        draw.rectangle([cx + 6, head_cy - 4, cx + 8, head_cy],
                       fill=RNS_HELM, outline=OUTLINE)
        draw.point((cx + 4, head_cy - 6), fill=rune_color)


# ===================================================================
# SHAPESHIFTER (ID 111)
# Purple/shifting-color body that changes hue per frame, cape, glowing
# eyes, unstable edges/outline that shifts between frames, morphing
# silhouette hints.
# ===================================================================

# Shapeshifter palettes — one per frame to simulate shifting colors
SHP_BODY_FRAMES = [
    (120, 50, 160),   # frame 0: purple
    (50, 120, 160),   # frame 1: teal
    (160, 50, 80),    # frame 2: crimson
    (80, 160, 50),    # frame 3: green
]
SHP_BODY_LIGHT_FRAMES = [
    (155, 75, 200),
    (75, 155, 200),
    (200, 75, 110),
    (110, 200, 75),
]
SHP_BODY_DARK_FRAMES = [
    (80, 30, 110),
    (30, 80, 110),
    (110, 30, 55),
    (55, 110, 30),
]
SHP_CAPE = (90, 40, 120)
SHP_CAPE_DARK = (60, 25, 80)
SHP_CAPE_LIGHT = (120, 60, 155)
SHP_EYE_GLOW = (200, 255, 200)
SHP_EYE_CORE = (255, 255, 220)
SHP_EDGE_FRAMES = [
    (140, 60, 180),   # unstable edge colors per frame
    (60, 140, 180),
    (180, 60, 100),
    (100, 180, 60),
]
SHP_SKIN = (180, 160, 190)
SHP_SKIN_DARK = (150, 130, 160)
SHP_LEG_FRAMES = [
    (90, 40, 120),
    (40, 90, 120),
    (120, 40, 60),
    (60, 120, 40),
]
SHP_BOOT = (50, 40, 55)
SHP_BELT = (70, 50, 80)
SHP_MORPH = (200, 180, 220)


def draw_shapeshifter(draw, ox, oy, direction, frame):
    bob = [0, -2, 0, -1][frame]
    leg_spread = [-4, 0, 4, 0][frame]
    edge_jitter = [-1, 1, -1, 0][frame]

    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 20
    head_cy = body_cy - 20

    # Per-frame shifting colors
    body_col = SHP_BODY_FRAMES[frame]
    body_light = SHP_BODY_LIGHT_FRAMES[frame]
    body_dark = SHP_BODY_DARK_FRAMES[frame]
    edge_col = SHP_EDGE_FRAMES[frame]
    leg_col = SHP_LEG_FRAMES[frame]

    if direction == DOWN:
        # Cape behind (shifting edges)
        draw.polygon([(cx - 12 + edge_jitter, body_cy - 8),
                      (cx + 12 - edge_jitter, body_cy - 8),
                      (cx + 15 + edge_jitter, body_cy + 16),
                      (cx - 15 - edge_jitter, body_cy + 16)],
                     fill=SHP_CAPE, outline=edge_col)
        draw.polygon([(cx - 8, body_cy - 4), (cx + 8, body_cy - 4),
                      (cx + 10, body_cy + 12), (cx - 10, body_cy + 12)],
                     fill=SHP_CAPE_DARK, outline=None)

        # Legs (shifting color)
        draw.rectangle([cx - 10 + leg_spread, body_cy + 10,
                        cx - 4 + leg_spread, base_y], fill=leg_col, outline=edge_col)
        draw.rectangle([cx + 4 - leg_spread, body_cy + 10,
                        cx + 10 - leg_spread, base_y], fill=leg_col, outline=edge_col)
        # Boots
        draw.rectangle([cx - 10 + leg_spread, base_y - 5,
                        cx - 4 + leg_spread, base_y], fill=SHP_BOOT, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, base_y - 5,
                        cx + 10 - leg_spread, base_y], fill=SHP_BOOT, outline=OUTLINE)

        # Body (shifting color with unstable outline)
        ellipse(draw, cx, body_cy, 14 + edge_jitter, 12, body_col, outline=edge_col)
        ellipse(draw, cx - 3, body_cy - 2, 8, 7, body_light, outline=None)
        # Morphing detail — shifting inner pattern
        draw.point((cx - 5 + edge_jitter, body_cy - 3), fill=body_dark)
        draw.point((cx + 6 - edge_jitter, body_cy + 2), fill=body_dark)
        draw.point((cx + 2, body_cy + 5 + edge_jitter), fill=body_dark)
        # Belt
        draw.rectangle([cx - 14, body_cy + 10, cx + 14, body_cy + 14],
                       fill=SHP_BELT, outline=edge_col)

        # Arms (shifting)
        draw.rectangle([cx - 18 + edge_jitter, body_cy - 6,
                        cx - 12, body_cy + 6],
                       fill=body_col, outline=edge_col)
        draw.rectangle([cx - 18 + edge_jitter, body_cy + 2,
                        cx - 12, body_cy + 6],
                       fill=SHP_SKIN, outline=OUTLINE)
        draw.rectangle([cx + 12, body_cy - 6,
                        cx + 18 - edge_jitter, body_cy + 6],
                       fill=body_col, outline=edge_col)
        draw.rectangle([cx + 12, body_cy + 2,
                        cx + 18 - edge_jitter, body_cy + 6],
                       fill=SHP_SKIN, outline=OUTLINE)

        # Morph hint particles (silhouette fragments)
        draw.point((cx - 20, body_cy - 4), fill=SHP_MORPH)
        draw.point((cx + 20, body_cy + 2), fill=SHP_MORPH)
        draw.point((cx - 16, body_cy + 8), fill=edge_col)

        # Head (shifting outline)
        ellipse(draw, cx, head_cy, 14 + edge_jitter, 13, body_col, outline=edge_col)
        ellipse(draw, cx, head_cy + 2, 12, 10, SHP_SKIN)
        ellipse(draw, cx + 2, head_cy + 4, 8, 6, SHP_SKIN_DARK, outline=None)
        # Glowing eyes
        draw.rectangle([cx - 6, head_cy + 1, cx - 3, head_cy + 4], fill=SHP_EYE_GLOW)
        draw.rectangle([cx - 5, head_cy + 2, cx - 4, head_cy + 3], fill=SHP_EYE_CORE)
        draw.rectangle([cx + 3, head_cy + 1, cx + 6, head_cy + 4], fill=SHP_EYE_GLOW)
        draw.rectangle([cx + 4, head_cy + 2, cx + 5, head_cy + 3], fill=SHP_EYE_CORE)
        # No visible mouth — mysterious
        draw.point((cx, head_cy + 7), fill=SHP_SKIN_DARK)

        # Unstable crown / shifting head top
        draw.point((cx - 6, head_cy - 10 + edge_jitter), fill=edge_col)
        draw.point((cx, head_cy - 12), fill=body_light)
        draw.point((cx + 5, head_cy - 9 - edge_jitter), fill=edge_col)

    elif direction == UP:
        # Cape (back, prominent)
        draw.polygon([(cx - 12 + edge_jitter, body_cy - 8),
                      (cx + 12 - edge_jitter, body_cy - 8),
                      (cx + 17 + edge_jitter, body_cy + 18),
                      (cx - 17 - edge_jitter, body_cy + 18)],
                     fill=SHP_CAPE, outline=edge_col)
        draw.polygon([(cx - 8, body_cy - 4), (cx + 8, body_cy - 4),
                      (cx + 13, body_cy + 14), (cx - 13, body_cy + 14)],
                     fill=SHP_CAPE_DARK, outline=None)
        # Shifting cape edge details
        draw.point((cx - 14, body_cy + 14), fill=SHP_CAPE_LIGHT)
        draw.point((cx + 14, body_cy + 12), fill=SHP_CAPE_LIGHT)

        # Legs
        draw.rectangle([cx - 10 + leg_spread, body_cy + 10,
                        cx - 4 + leg_spread, base_y], fill=leg_col, outline=edge_col)
        draw.rectangle([cx + 4 - leg_spread, body_cy + 10,
                        cx + 10 - leg_spread, base_y], fill=leg_col, outline=edge_col)
        draw.rectangle([cx - 10 + leg_spread, base_y - 5,
                        cx - 4 + leg_spread, base_y], fill=SHP_BOOT, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, base_y - 5,
                        cx + 10 - leg_spread, base_y], fill=SHP_BOOT, outline=OUTLINE)

        # Body (back)
        ellipse(draw, cx, body_cy, 14 + edge_jitter, 12, body_col, outline=edge_col)
        ellipse(draw, cx, body_cy, 10, 9, body_dark, outline=None)
        draw.line([(cx, body_cy - 6), (cx, body_cy + 6)],
                  fill=_darken(body_col, 0.7), width=1)
        # Belt
        draw.rectangle([cx - 14, body_cy + 10, cx + 14, body_cy + 14],
                       fill=SHP_BELT, outline=edge_col)

        # Arms
        draw.rectangle([cx - 18 + edge_jitter, body_cy - 6,
                        cx - 12, body_cy + 6],
                       fill=body_col, outline=edge_col)
        draw.rectangle([cx + 12, body_cy - 6,
                        cx + 18 - edge_jitter, body_cy + 6],
                       fill=body_col, outline=edge_col)

        # Head (back)
        ellipse(draw, cx, head_cy, 14 + edge_jitter, 13, body_col, outline=edge_col)
        ellipse(draw, cx, head_cy - 2, 10, 8, body_dark, outline=None)

        # Unstable crown
        draw.point((cx - 5, head_cy - 10 + edge_jitter), fill=edge_col)
        draw.point((cx + 1, head_cy - 12), fill=body_light)
        draw.point((cx + 6, head_cy - 9 - edge_jitter), fill=edge_col)

    elif direction == LEFT:
        # Cape trailing right
        draw.polygon([(cx + 4 + edge_jitter, body_cy - 8),
                      (cx + 16 - edge_jitter, body_cy + 16),
                      (cx + 6, body_cy + 16),
                      (cx + 2, body_cy - 4)],
                     fill=SHP_CAPE, outline=edge_col)
        draw.polygon([(cx + 5, body_cy - 2), (cx + 12, body_cy + 12),
                      (cx + 6, body_cy + 12), (cx + 4, body_cy)],
                     fill=SHP_CAPE_DARK, outline=None)

        # Legs
        draw.rectangle([cx - 2 - leg_spread, body_cy + 10,
                        cx + 4 - leg_spread, base_y],
                       fill=_darken(leg_col, 0.85), outline=edge_col)
        draw.rectangle([cx - 2 - leg_spread, base_y - 5,
                        cx + 4 - leg_spread, base_y], fill=SHP_BOOT, outline=OUTLINE)
        draw.rectangle([cx - 8 + leg_spread, body_cy + 10,
                        cx - 2 + leg_spread, base_y], fill=leg_col, outline=edge_col)
        draw.rectangle([cx - 8 + leg_spread, base_y - 5,
                        cx - 2 + leg_spread, base_y], fill=SHP_BOOT, outline=OUTLINE)

        # Body (side, shifting)
        ellipse(draw, cx - 2, body_cy, 12 + edge_jitter, 12, body_col, outline=edge_col)
        ellipse(draw, cx - 4, body_cy - 2, 7, 7, body_light, outline=None)
        draw.point((cx - 8 + edge_jitter, body_cy + 3), fill=body_dark)
        # Belt
        draw.rectangle([cx - 14, body_cy + 10, cx + 10, body_cy + 14],
                       fill=SHP_BELT, outline=edge_col)

        # Front arm
        draw.rectangle([cx - 14 + edge_jitter, body_cy - 4,
                        cx - 8, body_cy + 6],
                       fill=body_col, outline=edge_col)
        draw.rectangle([cx - 14 + edge_jitter, body_cy + 2,
                        cx - 8, body_cy + 6],
                       fill=SHP_SKIN, outline=OUTLINE)

        # Morph particles
        draw.point((cx - 18, body_cy - 2), fill=SHP_MORPH)
        draw.point((cx + 10, body_cy + 6), fill=edge_col)

        # Head (shifting outline)
        ellipse(draw, cx - 2, head_cy, 13 + edge_jitter, 13, body_col, outline=edge_col)
        ellipse(draw, cx - 4, head_cy + 2, 10, 9, SHP_SKIN)
        ellipse(draw, cx - 2, head_cy + 4, 6, 5, SHP_SKIN_DARK, outline=None)
        # Glowing eye
        draw.rectangle([cx - 10, head_cy + 1, cx - 7, head_cy + 4], fill=SHP_EYE_GLOW)
        draw.rectangle([cx - 9, head_cy + 2, cx - 8, head_cy + 3], fill=SHP_EYE_CORE)
        draw.point((cx - 9, head_cy + 7), fill=SHP_SKIN_DARK)

        # Unstable head top
        draw.point((cx - 8, head_cy - 10 + edge_jitter), fill=edge_col)
        draw.point((cx - 2, head_cy - 12), fill=body_light)
        draw.point((cx + 4, head_cy - 9 - edge_jitter), fill=edge_col)

    else:  # RIGHT
        # Cape trailing left
        draw.polygon([(cx - 4 - edge_jitter, body_cy - 8),
                      (cx - 16 + edge_jitter, body_cy + 16),
                      (cx - 6, body_cy + 16),
                      (cx - 2, body_cy - 4)],
                     fill=SHP_CAPE, outline=edge_col)
        draw.polygon([(cx - 5, body_cy - 2), (cx - 12, body_cy + 12),
                      (cx - 6, body_cy + 12), (cx - 4, body_cy)],
                     fill=SHP_CAPE_DARK, outline=None)

        # Legs
        draw.rectangle([cx - 2 + leg_spread, body_cy + 10,
                        cx + 4 + leg_spread, base_y],
                       fill=_darken(leg_col, 0.85), outline=edge_col)
        draw.rectangle([cx - 2 + leg_spread, base_y - 5,
                        cx + 4 + leg_spread, base_y], fill=SHP_BOOT, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, body_cy + 10,
                        cx + 10 - leg_spread, base_y], fill=leg_col, outline=edge_col)
        draw.rectangle([cx + 4 - leg_spread, base_y - 5,
                        cx + 10 - leg_spread, base_y], fill=SHP_BOOT, outline=OUTLINE)

        # Body (side, shifting)
        ellipse(draw, cx + 2, body_cy, 12 + edge_jitter, 12, body_col, outline=edge_col)
        ellipse(draw, cx, body_cy - 2, 7, 7, body_light, outline=None)
        draw.point((cx + 8 - edge_jitter, body_cy + 3), fill=body_dark)
        # Belt
        draw.rectangle([cx - 10, body_cy + 10, cx + 14, body_cy + 14],
                       fill=SHP_BELT, outline=edge_col)

        # Front arm
        draw.rectangle([cx + 8, body_cy - 4,
                        cx + 14 - edge_jitter, body_cy + 6],
                       fill=body_col, outline=edge_col)
        draw.rectangle([cx + 8, body_cy + 2,
                        cx + 14 - edge_jitter, body_cy + 6],
                       fill=SHP_SKIN, outline=OUTLINE)

        # Morph particles
        draw.point((cx + 18, body_cy - 2), fill=SHP_MORPH)
        draw.point((cx - 10, body_cy + 6), fill=edge_col)

        # Head (shifting outline)
        ellipse(draw, cx + 2, head_cy, 13 + edge_jitter, 13, body_col, outline=edge_col)
        ellipse(draw, cx + 4, head_cy + 2, 10, 9, SHP_SKIN)
        ellipse(draw, cx + 6, head_cy + 4, 6, 5, SHP_SKIN_DARK, outline=None)
        # Glowing eye
        draw.rectangle([cx + 7, head_cy + 1, cx + 10, head_cy + 4], fill=SHP_EYE_GLOW)
        draw.rectangle([cx + 8, head_cy + 2, cx + 9, head_cy + 3], fill=SHP_EYE_CORE)
        draw.point((cx + 9, head_cy + 7), fill=SHP_SKIN_DARK)

        # Unstable head top
        draw.point((cx - 4, head_cy - 9 + edge_jitter), fill=edge_col)
        draw.point((cx + 2, head_cy - 12), fill=body_light)
        draw.point((cx + 8, head_cy - 10 - edge_jitter), fill=edge_col)


# ─── REGISTRY ─────────────────────────────────────────────────────
SPECIALIST_DRAW_FUNCTIONS = {
    'alchemist': draw_alchemist,
    'puppeteer': draw_puppeteer,
    'gambler': draw_gambler,
    'blacksmith': draw_blacksmith,
    'pirate': draw_pirate,
    'chef': draw_chef,
    'musician': draw_musician,
    'astronomer': draw_astronomer,
    'runesmith': draw_runesmith,
    'shapeshifter': draw_shapeshifter,
}


def main():
    for name, draw_func in SPECIALIST_DRAW_FUNCTIONS.items():
        generate_character(name, draw_func=draw_func)
    print(f"\nGenerated {len(SPECIALIST_DRAW_FUNCTIONS)} specialist character sprites.")


if __name__ == "__main__":
    main()
