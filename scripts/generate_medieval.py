#!/usr/bin/env python3
"""Medieval/Fantasy character sprite generators (IDs 42-56).

15 characters with class-defining equipment and unique visual features.
"""

import sys
import os
import math
sys.path.insert(0, os.path.dirname(__file__))
from sprite_base import (
    generate_character, ellipse, pill, _darken, _brighten,
    OUTLINE, BLACK, DOWN, UP, LEFT, RIGHT,
)

# ---------------------------------------------------------------------------
# Palettes
# ---------------------------------------------------------------------------

# Paladin palette
PAL_ARMOR = (210, 190, 80)
PAL_ARMOR_LIGHT = (240, 220, 110)
PAL_ARMOR_DARK = (170, 150, 55)
PAL_SKIN = (225, 200, 170)
PAL_SKIN_DARK = (195, 170, 140)
PAL_CAPE = (240, 240, 235)
PAL_CAPE_DARK = (200, 200, 195)
PAL_CROSS = (210, 190, 80)
PAL_HELM = (220, 200, 90)
PAL_HELM_DARK = (180, 160, 60)
PAL_HELM_LIGHT = (245, 230, 130)
PAL_WING = (230, 230, 225)
PAL_WING_DARK = (190, 190, 185)
PAL_SHIELD = (200, 185, 75)
PAL_SHIELD_DARK = (160, 145, 50)
PAL_LEG = (180, 165, 70)
PAL_BOOT = (160, 145, 55)

# Ranger palette
RNG_LEATHER = (90, 110, 65)
RNG_LEATHER_LIGHT = (115, 140, 85)
RNG_LEATHER_DARK = (62, 78, 44)
RNG_HOOD = (70, 90, 50)
RNG_HOOD_DARK = (48, 62, 34)
RNG_HOOD_LIGHT = (90, 115, 65)
RNG_SKIN = (215, 190, 155)
RNG_SKIN_DARK = (185, 160, 125)
RNG_BOW = (120, 85, 50)
RNG_BOW_DARK = (85, 60, 35)
RNG_STRING = (200, 200, 190)
RNG_QUIVER = (100, 75, 45)
RNG_QUIVER_DARK = (70, 52, 30)
RNG_ARROW = (160, 150, 130)
RNG_ARROW_TIP = (180, 185, 195)
RNG_FEATHER = (180, 50, 50)
RNG_BELT = (80, 65, 40)
RNG_LEG = (75, 95, 55)
RNG_BOOT = (70, 55, 38)

# Berserker palette
BER_SKIN = (200, 165, 130)
BER_SKIN_DARK = (170, 135, 100)
BER_SKIN_LIGHT = (220, 190, 160)
BER_FUR = (120, 90, 55)
BER_FUR_DARK = (85, 62, 38)
BER_FUR_LIGHT = (150, 115, 72)
BER_WARPAINT = (180, 40, 40)
BER_WARPAINT_DARK = (140, 30, 30)
BER_HAIR = (160, 100, 50)
BER_HAIR_DARK = (120, 70, 35)
BER_AXE_HEAD = (170, 175, 185)
BER_AXE_DARK = (130, 135, 145)
BER_AXE_HANDLE = (100, 70, 40)
BER_BELT = (90, 65, 40)
BER_BELT_BUCKLE = (180, 170, 50)
BER_LEG = (140, 110, 75)
BER_BOOT = (100, 75, 48)

# Crusader palette
CRU_ARMOR = (180, 185, 195)
CRU_ARMOR_LIGHT = (210, 215, 225)
CRU_ARMOR_DARK = (140, 145, 155)
CRU_TABARD = (170, 40, 40)
CRU_TABARD_DARK = (130, 28, 28)
CRU_TABARD_LIGHT = (200, 60, 55)
CRU_CROSS = (220, 220, 215)
CRU_HELM = (190, 195, 205)
CRU_HELM_DARK = (150, 155, 165)
CRU_HELM_LIGHT = (220, 225, 235)
CRU_CAPE = (170, 40, 40)
CRU_CAPE_DARK = (130, 28, 28)
CRU_SKIN = (225, 200, 170)
CRU_SKIN_DARK = (195, 170, 140)
CRU_LEG = (160, 165, 175)
CRU_BOOT = (140, 145, 155)

# Druid palette
DRU_ROBE = (80, 110, 60)
DRU_ROBE_LIGHT = (105, 140, 80)
DRU_ROBE_DARK = (55, 78, 40)
DRU_STAFF = (120, 90, 55)
DRU_STAFF_DARK = (85, 62, 38)
DRU_VINE = (60, 130, 50)
DRU_VINE_DARK = (40, 95, 35)
DRU_LEAF = (80, 160, 50)
DRU_LEAF_DARK = (55, 120, 35)
DRU_ANTLER = (160, 130, 80)
DRU_ANTLER_DARK = (120, 95, 55)
DRU_ANTLER_LIGHT = (190, 160, 110)
DRU_SKIN = (215, 190, 155)
DRU_SKIN_DARK = (185, 160, 125)
DRU_HAIR = (100, 70, 45)
DRU_LEG = (70, 95, 50)
DRU_BOOT = (90, 70, 45)

# Bard palette
BRD_OUTFIT = (140, 50, 120)
BRD_OUTFIT_LIGHT = (170, 75, 150)
BRD_OUTFIT_DARK = (100, 35, 85)
BRD_ACCENT = (220, 180, 50)
BRD_ACCENT_DARK = (180, 140, 35)
BRD_ACCENT_LIGHT = (245, 210, 80)
BRD_CAPE = (50, 100, 150)
BRD_CAPE_DARK = (35, 70, 110)
BRD_SKIN = (225, 200, 170)
BRD_SKIN_DARK = (195, 170, 140)
BRD_HAT = (140, 50, 120)
BRD_HAT_DARK = (100, 35, 85)
BRD_FEATHER = (220, 180, 50)
BRD_LUTE = (160, 120, 70)
BRD_LUTE_DARK = (120, 85, 48)
BRD_LUTE_STRING = (200, 195, 180)
BRD_NOTE = (220, 180, 50)
BRD_LEG = (120, 40, 100)
BRD_BOOT = (90, 30, 75)

# Monk palette
MNK_ROBE = (210, 140, 50)
MNK_ROBE_LIGHT = (235, 170, 75)
MNK_ROBE_DARK = (170, 110, 35)
MNK_SASH = (180, 60, 40)
MNK_SASH_DARK = (140, 42, 28)
MNK_SKIN = (200, 170, 130)
MNK_SKIN_DARK = (170, 140, 100)
MNK_SKIN_LIGHT = (220, 195, 160)
MNK_BEADS = (120, 70, 40)
MNK_BEAD_HIGHLIGHT = (160, 100, 60)
MNK_WRAP = (200, 190, 170)
MNK_WRAP_DARK = (170, 160, 140)
MNK_HEAD = (200, 170, 130)

# Cleric palette
CLR_ROBE = (235, 230, 220)
CLR_ROBE_LIGHT = (248, 245, 240)
CLR_ROBE_DARK = (200, 195, 185)
CLR_GOLD = (220, 190, 60)
CLR_GOLD_DARK = (180, 150, 40)
CLR_GOLD_LIGHT = (245, 220, 90)
CLR_SKIN = (225, 200, 170)
CLR_SKIN_DARK = (195, 170, 140)
CLR_HALO = (255, 230, 100)
CLR_HALO_LIGHT = (255, 245, 160)
CLR_STAFF = (180, 150, 90)
CLR_STAFF_DARK = (140, 115, 65)
CLR_HAIR = (180, 150, 100)
CLR_LEG = (210, 205, 195)
CLR_BOOT = (180, 170, 155)
CLR_SYMBOL = (220, 190, 60)

# Rogue palette
ROG_OUTFIT = (55, 55, 60)
ROG_OUTFIT_LIGHT = (80, 80, 88)
ROG_OUTFIT_DARK = (35, 35, 40)
ROG_HOOD = (45, 45, 50)
ROG_HOOD_DARK = (30, 30, 35)
ROG_HOOD_LIGHT = (65, 65, 72)
ROG_SKIN = (215, 190, 155)
ROG_SKIN_DARK = (185, 160, 125)
ROG_MASK = (40, 40, 45)
ROG_DAGGER = (180, 185, 195)
ROG_DAGGER_DARK = (140, 145, 155)
ROG_DAGGER_HANDLE = (100, 70, 40)
ROG_BELT = (70, 55, 38)
ROG_BELT_BUCKLE = (150, 150, 160)
ROG_LEG = (50, 50, 55)
ROG_BOOT = (40, 40, 44)

# Barbarian palette
BAR_SKIN = (190, 155, 115)
BAR_SKIN_DARK = (160, 125, 85)
BAR_SKIN_LIGHT = (210, 180, 145)
BAR_FUR = (130, 100, 65)
BAR_FUR_DARK = (95, 70, 42)
BAR_FUR_LIGHT = (160, 130, 85)
BAR_HELM = (140, 110, 70)
BAR_HELM_DARK = (100, 78, 48)
BAR_HELM_LIGHT = (170, 140, 95)
BAR_HORN = (200, 185, 150)
BAR_HORN_DARK = (160, 145, 110)
BAR_HORN_LIGHT = (230, 220, 195)
BAR_AXE_HEAD = (170, 175, 185)
BAR_AXE_DARK = (130, 135, 145)
BAR_AXE_HANDLE = (100, 70, 40)
BAR_TATTOO = (60, 80, 110)
BAR_BELT = (90, 65, 40)
BAR_BELT_BUCKLE = (180, 170, 50)
BAR_LEG = (150, 120, 80)
BAR_BOOT = (110, 82, 52)

# Enchantress palette
ENC_ROBE = (120, 60, 160)
ENC_ROBE_LIGHT = (150, 85, 195)
ENC_ROBE_DARK = (85, 40, 115)
ENC_SPARKLE = (220, 200, 255)
ENC_SPARKLE_BRIGHT = (240, 230, 255)
ENC_ORB = (180, 140, 240)
ENC_ORB_BRIGHT = (210, 180, 255)
ENC_STAFF = (140, 100, 60)
ENC_STAFF_GEM = (200, 140, 255)
ENC_SKIN = (230, 210, 195)
ENC_SKIN_DARK = (200, 180, 165)
ENC_HAIR = (60, 40, 80)
ENC_LEG = (100, 50, 135)
ENC_BOOT = (80, 38, 108)

# Jester palette
JST_RED = (200, 50, 50)
JST_RED_DARK = (155, 35, 35)
JST_RED_LIGHT = (230, 75, 70)
JST_GREEN = (50, 160, 60)
JST_GREEN_DARK = (35, 120, 42)
JST_GREEN_LIGHT = (75, 195, 85)
JST_BELL = (220, 200, 60)
JST_BELL_DARK = (180, 160, 40)
JST_SKIN = (230, 215, 190)
JST_SKIN_DARK = (200, 185, 160)
JST_BALL_1 = (200, 50, 50)
JST_BALL_2 = (50, 120, 200)
JST_BALL_3 = (220, 180, 50)
JST_LEG_R = (180, 40, 40)
JST_LEG_G = (40, 140, 50)
JST_BOOT_R = (140, 30, 30)
JST_BOOT_G = (30, 105, 38)

# Valkyrie palette
VAL_ARMOR = (180, 185, 195)
VAL_ARMOR_LIGHT = (210, 215, 225)
VAL_ARMOR_DARK = (140, 145, 155)
VAL_WING = (230, 230, 225)
VAL_WING_DARK = (190, 190, 185)
VAL_WING_LIGHT = (245, 245, 240)
VAL_CAPE = (50, 80, 130)
VAL_CAPE_DARK = (35, 55, 95)
VAL_SKIN = (225, 205, 180)
VAL_SKIN_DARK = (195, 175, 150)
VAL_HAIR = (220, 200, 140)
VAL_HAIR_DARK = (180, 160, 100)
VAL_SPEAR = (160, 165, 175)
VAL_SPEAR_DARK = (120, 125, 135)
VAL_SHIELD = (170, 175, 185)
VAL_SHIELD_ACCENT = (50, 80, 130)
VAL_LEG = (160, 165, 175)
VAL_BOOT = (140, 145, 155)

# Warlock palette
WLK_ROBE = (50, 30, 60)
WLK_ROBE_LIGHT = (70, 45, 85)
WLK_ROBE_DARK = (35, 20, 42)
WLK_HOOD = (40, 25, 50)
WLK_HOOD_DARK = (28, 18, 35)
WLK_HOOD_LIGHT = (55, 35, 68)
WLK_EYE = (160, 80, 220)
WLK_EYE_BRIGHT = (200, 120, 255)
WLK_CAPE = (40, 25, 50)
WLK_CAPE_DARK = (28, 18, 35)
WLK_VOID = (80, 50, 120)
WLK_VOID_BRIGHT = (120, 80, 180)
WLK_SKIN = (200, 185, 170)
WLK_SKIN_DARK = (170, 155, 140)
WLK_LEG = (45, 28, 55)
WLK_BOOT = (35, 22, 42)

# Inquisitor palette
INQ_ARMOR = (150, 140, 125)
INQ_ARMOR_LIGHT = (180, 170, 155)
INQ_ARMOR_DARK = (115, 105, 90)
INQ_ACCENT = (160, 45, 40)
INQ_ACCENT_DARK = (125, 32, 28)
INQ_HAT = (100, 90, 75)
INQ_HAT_DARK = (70, 62, 50)
INQ_HAT_LIGHT = (130, 120, 105)
INQ_SKIN = (220, 200, 175)
INQ_SKIN_DARK = (190, 170, 145)
INQ_CROSS = (200, 190, 170)
INQ_BELT = (90, 70, 50)
INQ_BELT_BUCKLE = (180, 170, 140)
INQ_LEG = (130, 120, 105)
INQ_BOOT = (100, 90, 75)
INQ_CAPE = (120, 110, 95)
INQ_CAPE_DARK = (85, 78, 62)


# ===================================================================
# PALADIN (ID 42) -- Golden plate armor, holy cross on chest,
#                     winged helm, white cape, shield on arm
# ===================================================================

def draw_paladin(draw, ox, oy, direction, frame):
    bob = [0, -2, 0, -1][frame]
    leg_spread = [-4, 0, 4, 0][frame]

    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 20
    head_cy = body_cy - 20

    if direction == DOWN:
        # Cape behind body
        cape_sway = [0, 2, 0, -2][frame]
        draw.polygon([
            (cx - 14, body_cy - 6),
            (cx + 14, body_cy - 6),
            (cx + 16 + cape_sway, base_y - 2),
            (cx - 16 + cape_sway, base_y - 2),
        ], fill=PAL_CAPE, outline=OUTLINE)
        draw.line([(cx - 4, body_cy), (cx - 6 + cape_sway, base_y - 4)],
                  fill=PAL_CAPE_DARK, width=1)
        draw.line([(cx + 4, body_cy), (cx + 6 + cape_sway, base_y - 4)],
                  fill=PAL_CAPE_DARK, width=1)

        # Legs
        draw.rectangle([cx - 10 + leg_spread, body_cy + 10,
                        cx - 4 + leg_spread, base_y], fill=PAL_LEG, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, body_cy + 10,
                        cx + 10 - leg_spread, base_y], fill=PAL_LEG, outline=OUTLINE)
        # Boots
        draw.rectangle([cx - 10 + leg_spread, base_y - 5,
                        cx - 4 + leg_spread, base_y], fill=PAL_BOOT, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, base_y - 5,
                        cx + 10 - leg_spread, base_y], fill=PAL_BOOT, outline=OUTLINE)

        # Armor body
        ellipse(draw, cx, body_cy, 14, 12, PAL_ARMOR)
        ellipse(draw, cx - 3, body_cy - 2, 8, 7, PAL_ARMOR_LIGHT, outline=None)
        # Holy cross on chest
        draw.rectangle([cx - 1, body_cy - 6, cx + 1, body_cy + 4],
                       fill=PAL_CROSS, outline=None)
        draw.rectangle([cx - 4, body_cy - 3, cx + 4, body_cy - 1],
                       fill=PAL_CROSS, outline=None)
        # Belt
        draw.rectangle([cx - 14, body_cy + 8, cx + 14, body_cy + 14],
                       fill=PAL_ARMOR_DARK, outline=OUTLINE)
        draw.rectangle([cx - 3, body_cy + 9, cx + 3, body_cy + 13],
                       fill=PAL_ARMOR_LIGHT, outline=OUTLINE)

        # Arms
        draw.rectangle([cx - 18, body_cy - 6, cx - 12, body_cy + 6],
                       fill=PAL_ARMOR, outline=OUTLINE)
        draw.rectangle([cx - 18, body_cy + 2, cx - 12, body_cy + 6],
                       fill=PAL_SKIN, outline=OUTLINE)
        draw.rectangle([cx + 12, body_cy - 6, cx + 18, body_cy + 6],
                       fill=PAL_ARMOR, outline=OUTLINE)
        draw.rectangle([cx + 12, body_cy + 2, cx + 18, body_cy + 6],
                       fill=PAL_SKIN, outline=OUTLINE)
        # Shoulder pads
        ellipse(draw, cx - 14, body_cy - 6, 6, 4, PAL_ARMOR_LIGHT)
        ellipse(draw, cx + 14, body_cy - 6, 6, 4, PAL_ARMOR_LIGHT)

        # Shield on left arm
        draw.rectangle([cx - 24, body_cy - 8, cx - 16, body_cy + 8],
                       fill=PAL_SHIELD, outline=OUTLINE)
        draw.rectangle([cx - 22, body_cy - 6, cx - 18, body_cy + 6],
                       fill=PAL_SHIELD_DARK, outline=None)
        # Shield cross
        draw.line([(cx - 20, body_cy - 4), (cx - 20, body_cy + 4)],
                  fill=PAL_ARMOR_LIGHT, width=1)
        draw.line([(cx - 22, body_cy), (cx - 18, body_cy)],
                  fill=PAL_ARMOR_LIGHT, width=1)

        # Head
        ellipse(draw, cx, head_cy, 14, 13, PAL_SKIN)
        ellipse(draw, cx + 2, head_cy + 4, 8, 6, PAL_SKIN_DARK, outline=None)
        # Eyes
        draw.rectangle([cx - 6, head_cy + 1, cx - 3, head_cy + 5], fill=(255, 255, 255))
        draw.rectangle([cx - 5, head_cy + 2, cx - 4, head_cy + 4], fill=BLACK)
        draw.rectangle([cx + 3, head_cy + 1, cx + 6, head_cy + 5], fill=(255, 255, 255))
        draw.rectangle([cx + 4, head_cy + 2, cx + 5, head_cy + 4], fill=BLACK)
        draw.point((cx, head_cy + 6), fill=PAL_SKIN_DARK)
        draw.line([(cx - 2, head_cy + 8), (cx + 2, head_cy + 8)],
                  fill=PAL_SKIN_DARK, width=1)
        # Winged helm
        draw.rectangle([cx - 16, head_cy - 6, cx + 16, head_cy],
                       fill=PAL_HELM, outline=OUTLINE)
        draw.rectangle([cx + 6, head_cy - 5, cx + 15, head_cy - 1],
                       fill=PAL_HELM_DARK, outline=None)
        # Crest
        draw.rectangle([cx - 2, head_cy - 14, cx + 2, head_cy - 4],
                       fill=PAL_HELM_LIGHT, outline=OUTLINE)
        # Wing decorations on helm
        draw.polygon([(cx - 14, head_cy - 4), (cx - 20, head_cy - 16),
                      (cx - 16, head_cy - 10)], fill=PAL_WING, outline=OUTLINE)
        draw.polygon([(cx + 14, head_cy - 4), (cx + 20, head_cy - 16),
                      (cx + 16, head_cy - 10)], fill=PAL_WING, outline=OUTLINE)
        draw.line([(cx - 18, head_cy - 12), (cx - 16, head_cy - 8)],
                  fill=PAL_WING_DARK, width=1)
        draw.line([(cx + 18, head_cy - 12), (cx + 16, head_cy - 8)],
                  fill=PAL_WING_DARK, width=1)

    elif direction == UP:
        # Cape visible from back
        cape_sway = [0, 2, 0, -2][frame]
        draw.polygon([
            (cx - 14, body_cy - 8),
            (cx + 14, body_cy - 8),
            (cx + 18 + cape_sway, base_y),
            (cx - 18 + cape_sway, base_y),
        ], fill=PAL_CAPE, outline=OUTLINE)
        draw.line([(cx - 4, body_cy - 2), (cx - 6 + cape_sway, base_y - 4)],
                  fill=PAL_CAPE_DARK, width=1)
        draw.line([(cx + 4, body_cy - 2), (cx + 6 + cape_sway, base_y - 4)],
                  fill=PAL_CAPE_DARK, width=1)
        draw.line([(cx, body_cy), (cx + cape_sway, base_y - 4)],
                  fill=PAL_CAPE_DARK, width=1)

        # Legs
        draw.rectangle([cx - 10 + leg_spread, body_cy + 10,
                        cx - 4 + leg_spread, base_y], fill=PAL_LEG, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, body_cy + 10,
                        cx + 10 - leg_spread, base_y], fill=PAL_LEG, outline=OUTLINE)
        draw.rectangle([cx - 10 + leg_spread, base_y - 5,
                        cx - 4 + leg_spread, base_y], fill=PAL_BOOT, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, base_y - 5,
                        cx + 10 - leg_spread, base_y], fill=PAL_BOOT, outline=OUTLINE)

        # Armor body (back)
        ellipse(draw, cx, body_cy, 14, 12, PAL_ARMOR)
        ellipse(draw, cx, body_cy, 10, 9, PAL_ARMOR_DARK, outline=None)
        draw.line([(cx, body_cy - 6), (cx, body_cy + 6)],
                  fill=_darken(PAL_ARMOR, 0.7), width=1)
        draw.rectangle([cx - 14, body_cy + 8, cx + 14, body_cy + 14],
                       fill=PAL_ARMOR_DARK, outline=OUTLINE)

        # Arms
        draw.rectangle([cx - 18, body_cy - 6, cx - 12, body_cy + 6],
                       fill=PAL_ARMOR, outline=OUTLINE)
        draw.rectangle([cx + 12, body_cy - 6, cx + 18, body_cy + 6],
                       fill=PAL_ARMOR, outline=OUTLINE)
        ellipse(draw, cx - 14, body_cy - 6, 6, 4, PAL_ARMOR_LIGHT)
        ellipse(draw, cx + 14, body_cy - 6, 6, 4, PAL_ARMOR_LIGHT)
        # Shield on left arm (back view)
        draw.rectangle([cx - 24, body_cy - 8, cx - 16, body_cy + 8],
                       fill=PAL_SHIELD, outline=OUTLINE)

        # Head (back)
        ellipse(draw, cx, head_cy, 14, 13, PAL_SKIN)
        ellipse(draw, cx, head_cy - 2, 10, 8, PAL_SKIN_DARK, outline=None)
        # Winged helm
        draw.rectangle([cx - 16, head_cy - 6, cx + 16, head_cy],
                       fill=PAL_HELM, outline=OUTLINE)
        draw.rectangle([cx - 2, head_cy - 14, cx + 2, head_cy - 4],
                       fill=PAL_HELM_LIGHT, outline=OUTLINE)
        draw.polygon([(cx - 14, head_cy - 4), (cx - 20, head_cy - 16),
                      (cx - 16, head_cy - 10)], fill=PAL_WING, outline=OUTLINE)
        draw.polygon([(cx + 14, head_cy - 4), (cx + 20, head_cy - 16),
                      (cx + 16, head_cy - 10)], fill=PAL_WING, outline=OUTLINE)

    elif direction == LEFT:
        # Cape trailing right
        cape_sway = [0, 2, 0, -2][frame]
        draw.polygon([
            (cx + 6, body_cy - 10),
            (cx + 16, body_cy - 6),
            (cx + 18 + cape_sway, base_y),
            (cx + 6, base_y),
        ], fill=PAL_CAPE, outline=OUTLINE)
        draw.line([(cx + 10, body_cy - 4), (cx + 12 + cape_sway, base_y - 4)],
                  fill=PAL_CAPE_DARK, width=1)

        # Legs
        draw.rectangle([cx - 2 - leg_spread, body_cy + 10,
                        cx + 4 - leg_spread, base_y],
                       fill=_darken(PAL_LEG, 0.85), outline=OUTLINE)
        draw.rectangle([cx - 2 - leg_spread, base_y - 5,
                        cx + 4 - leg_spread, base_y], fill=PAL_BOOT, outline=OUTLINE)
        draw.rectangle([cx - 8 + leg_spread, body_cy + 10,
                        cx - 2 + leg_spread, base_y], fill=PAL_LEG, outline=OUTLINE)
        draw.rectangle([cx - 8 + leg_spread, base_y - 5,
                        cx - 2 + leg_spread, base_y], fill=PAL_BOOT, outline=OUTLINE)

        # Armor body
        ellipse(draw, cx - 2, body_cy, 12, 12, PAL_ARMOR)
        ellipse(draw, cx - 4, body_cy - 2, 7, 7, PAL_ARMOR_LIGHT, outline=None)
        draw.rectangle([cx - 14, body_cy + 8, cx + 10, body_cy + 14],
                       fill=PAL_ARMOR_DARK, outline=OUTLINE)

        # Shield on front arm
        draw.rectangle([cx - 18, body_cy - 10, cx - 10, body_cy + 6],
                       fill=PAL_SHIELD, outline=OUTLINE)
        draw.line([(cx - 14, body_cy - 6), (cx - 14, body_cy + 2)],
                  fill=PAL_ARMOR_LIGHT, width=1)
        draw.line([(cx - 16, body_cy - 2), (cx - 12, body_cy - 2)],
                  fill=PAL_ARMOR_LIGHT, width=1)

        # Front arm
        draw.rectangle([cx - 14, body_cy - 4, cx - 8, body_cy + 6],
                       fill=PAL_ARMOR, outline=OUTLINE)
        ellipse(draw, cx - 10, body_cy - 6, 5, 3, PAL_ARMOR_LIGHT)

        # Head
        ellipse(draw, cx - 2, head_cy, 13, 13, PAL_SKIN)
        ellipse(draw, cx - 4, head_cy + 2, 9, 8, PAL_SKIN)
        ellipse(draw, cx - 2, head_cy + 4, 6, 5, PAL_SKIN_DARK, outline=None)
        draw.rectangle([cx - 10, head_cy + 1, cx - 7, head_cy + 5], fill=(255, 255, 255))
        draw.rectangle([cx - 9, head_cy + 2, cx - 8, head_cy + 4], fill=BLACK)
        draw.point((cx - 9, head_cy + 6), fill=PAL_SKIN_DARK)
        draw.line([(cx - 10, head_cy + 8), (cx - 7, head_cy + 8)],
                  fill=PAL_SKIN_DARK, width=1)
        # Helm
        draw.rectangle([cx - 16, head_cy - 6, cx + 8, head_cy],
                       fill=PAL_HELM, outline=OUTLINE)
        draw.rectangle([cx - 4, head_cy - 14, cx, head_cy - 4],
                       fill=PAL_HELM_LIGHT, outline=OUTLINE)
        draw.polygon([(cx - 12, head_cy - 4), (cx - 18, head_cy - 16),
                      (cx - 14, head_cy - 10)], fill=PAL_WING, outline=OUTLINE)

    else:  # RIGHT
        # Cape trailing left
        cape_sway = [0, -2, 0, 2][frame]
        draw.polygon([
            (cx - 6, body_cy - 10),
            (cx - 16, body_cy - 6),
            (cx - 18 + cape_sway, base_y),
            (cx - 6, base_y),
        ], fill=PAL_CAPE, outline=OUTLINE)
        draw.line([(cx - 10, body_cy - 4), (cx - 12 + cape_sway, base_y - 4)],
                  fill=PAL_CAPE_DARK, width=1)

        # Legs
        draw.rectangle([cx - 2 + leg_spread, body_cy + 10,
                        cx + 4 + leg_spread, base_y],
                       fill=_darken(PAL_LEG, 0.85), outline=OUTLINE)
        draw.rectangle([cx - 2 + leg_spread, base_y - 5,
                        cx + 4 + leg_spread, base_y], fill=PAL_BOOT, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, body_cy + 10,
                        cx + 10 - leg_spread, base_y], fill=PAL_LEG, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, base_y - 5,
                        cx + 10 - leg_spread, base_y], fill=PAL_BOOT, outline=OUTLINE)

        # Armor body
        ellipse(draw, cx + 2, body_cy, 12, 12, PAL_ARMOR)
        ellipse(draw, cx, body_cy - 2, 7, 7, PAL_ARMOR_LIGHT, outline=None)
        draw.rectangle([cx - 10, body_cy + 8, cx + 14, body_cy + 14],
                       fill=PAL_ARMOR_DARK, outline=OUTLINE)

        # Shield on front arm
        draw.rectangle([cx + 10, body_cy - 10, cx + 18, body_cy + 6],
                       fill=PAL_SHIELD, outline=OUTLINE)
        draw.line([(cx + 14, body_cy - 6), (cx + 14, body_cy + 2)],
                  fill=PAL_ARMOR_LIGHT, width=1)
        draw.line([(cx + 12, body_cy - 2), (cx + 16, body_cy - 2)],
                  fill=PAL_ARMOR_LIGHT, width=1)

        # Front arm
        draw.rectangle([cx + 8, body_cy - 4, cx + 14, body_cy + 6],
                       fill=PAL_ARMOR, outline=OUTLINE)
        ellipse(draw, cx + 10, body_cy - 6, 5, 3, PAL_ARMOR_LIGHT)

        # Head
        ellipse(draw, cx + 2, head_cy, 13, 13, PAL_SKIN)
        ellipse(draw, cx + 4, head_cy + 2, 9, 8, PAL_SKIN)
        ellipse(draw, cx + 6, head_cy + 4, 6, 5, PAL_SKIN_DARK, outline=None)
        draw.rectangle([cx + 7, head_cy + 1, cx + 10, head_cy + 5], fill=(255, 255, 255))
        draw.rectangle([cx + 8, head_cy + 2, cx + 9, head_cy + 4], fill=BLACK)
        draw.point((cx + 9, head_cy + 6), fill=PAL_SKIN_DARK)
        draw.line([(cx + 7, head_cy + 8), (cx + 10, head_cy + 8)],
                  fill=PAL_SKIN_DARK, width=1)
        # Helm
        draw.rectangle([cx - 8, head_cy - 6, cx + 16, head_cy],
                       fill=PAL_HELM, outline=OUTLINE)
        draw.rectangle([cx, head_cy - 14, cx + 4, head_cy - 4],
                       fill=PAL_HELM_LIGHT, outline=OUTLINE)
        draw.polygon([(cx + 12, head_cy - 4), (cx + 18, head_cy - 16),
                      (cx + 14, head_cy - 10)], fill=PAL_WING, outline=OUTLINE)


# ===================================================================
# RANGER (ID 43) -- Green/brown leather, hood, bow on back,
#                    quiver with arrows, agile build
# ===================================================================

def draw_ranger(draw, ox, oy, direction, frame):
    bob = [0, -2, 0, -1][frame]
    leg_spread = [-4, 0, 4, 0][frame]

    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 20
    head_cy = body_cy - 20

    if direction == DOWN:
        # Legs (slender)
        draw.rectangle([cx - 9 + leg_spread, body_cy + 10,
                        cx - 4 + leg_spread, base_y], fill=RNG_LEG, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, body_cy + 10,
                        cx + 9 - leg_spread, base_y], fill=RNG_LEG, outline=OUTLINE)
        draw.rectangle([cx - 9 + leg_spread, base_y - 5,
                        cx - 4 + leg_spread, base_y], fill=RNG_BOOT, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, base_y - 5,
                        cx + 10 - leg_spread, base_y], fill=RNG_BOOT, outline=OUTLINE)

        # Quiver on back (visible behind right shoulder)
        draw.rectangle([cx + 8, body_cy - 14, cx + 12, body_cy + 6],
                       fill=RNG_QUIVER, outline=OUTLINE)
        draw.rectangle([cx + 8, body_cy - 14, cx + 12, body_cy - 12],
                       fill=RNG_QUIVER_DARK, outline=None)
        # Arrow tips poking out
        draw.line([(cx + 9, body_cy - 18), (cx + 9, body_cy - 14)],
                  fill=RNG_ARROW_TIP, width=1)
        draw.line([(cx + 11, body_cy - 16), (cx + 11, body_cy - 14)],
                  fill=RNG_ARROW_TIP, width=1)
        draw.point((cx + 9, body_cy - 18), fill=RNG_ARROW_TIP)
        # Arrow feathers
        draw.point((cx + 9, body_cy - 19), fill=RNG_FEATHER)
        draw.point((cx + 11, body_cy - 17), fill=RNG_FEATHER)

        # Leather body
        ellipse(draw, cx, body_cy, 13, 11, RNG_LEATHER)
        ellipse(draw, cx - 3, body_cy - 2, 7, 6, RNG_LEATHER_LIGHT, outline=None)
        # Diagonal strap across chest
        draw.line([(cx - 8, body_cy - 8), (cx + 8, body_cy + 4)],
                  fill=RNG_QUIVER_DARK, width=2)
        # Belt
        draw.rectangle([cx - 13, body_cy + 8, cx + 13, body_cy + 13],
                       fill=RNG_BELT, outline=OUTLINE)
        draw.rectangle([cx - 2, body_cy + 9, cx + 2, body_cy + 12],
                       fill=_brighten(RNG_BELT, 1.4), outline=OUTLINE)

        # Arms
        draw.rectangle([cx - 17, body_cy - 5, cx - 12, body_cy + 5],
                       fill=RNG_LEATHER, outline=OUTLINE)
        draw.rectangle([cx - 17, body_cy + 1, cx - 12, body_cy + 5],
                       fill=RNG_SKIN, outline=OUTLINE)
        draw.rectangle([cx + 12, body_cy - 5, cx + 17, body_cy + 5],
                       fill=RNG_LEATHER, outline=OUTLINE)
        draw.rectangle([cx + 12, body_cy + 1, cx + 17, body_cy + 5],
                       fill=RNG_SKIN, outline=OUTLINE)
        ellipse(draw, cx - 14, body_cy - 5, 5, 3, RNG_LEATHER_LIGHT)
        ellipse(draw, cx + 14, body_cy - 5, 5, 3, RNG_LEATHER_LIGHT)

        # Head
        ellipse(draw, cx, head_cy, 13, 12, RNG_SKIN)
        ellipse(draw, cx + 2, head_cy + 3, 8, 6, RNG_SKIN_DARK, outline=None)
        # Eyes
        draw.rectangle([cx - 6, head_cy + 1, cx - 3, head_cy + 4], fill=(255, 255, 255))
        draw.rectangle([cx - 5, head_cy + 2, cx - 4, head_cy + 3], fill=BLACK)
        draw.rectangle([cx + 3, head_cy + 1, cx + 6, head_cy + 4], fill=(255, 255, 255))
        draw.rectangle([cx + 4, head_cy + 2, cx + 5, head_cy + 3], fill=BLACK)
        draw.point((cx, head_cy + 5), fill=RNG_SKIN_DARK)
        draw.line([(cx - 2, head_cy + 7), (cx + 2, head_cy + 7)],
                  fill=RNG_SKIN_DARK, width=1)
        # Hood
        ellipse(draw, cx, head_cy - 4, 16, 10, RNG_HOOD)
        ellipse(draw, cx + 4, head_cy - 2, 10, 7, RNG_HOOD_DARK, outline=None)
        ellipse(draw, cx - 2, head_cy - 6, 8, 5, RNG_HOOD_LIGHT, outline=None)
        draw.polygon([(cx - 4, head_cy - 14), (cx, head_cy - 20),
                      (cx + 4, head_cy - 14)], fill=RNG_HOOD, outline=OUTLINE)

    elif direction == UP:
        # Bow on back (visible from behind)
        draw.arc([cx - 8, body_cy - 16, cx + 4, body_cy + 8],
                 start=260, end=80, fill=RNG_BOW, width=2)
        draw.line([(cx - 2, body_cy - 16), (cx - 2, body_cy + 8)],
                  fill=RNG_STRING, width=1)
        # Quiver
        draw.rectangle([cx + 6, body_cy - 14, cx + 10, body_cy + 6],
                       fill=RNG_QUIVER, outline=OUTLINE)
        draw.line([(cx + 7, body_cy - 18), (cx + 7, body_cy - 14)],
                  fill=RNG_ARROW_TIP, width=1)
        draw.line([(cx + 9, body_cy - 16), (cx + 9, body_cy - 14)],
                  fill=RNG_ARROW_TIP, width=1)

        # Legs
        draw.rectangle([cx - 9 + leg_spread, body_cy + 10,
                        cx - 4 + leg_spread, base_y], fill=RNG_LEG, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, body_cy + 10,
                        cx + 9 - leg_spread, base_y], fill=RNG_LEG, outline=OUTLINE)
        draw.rectangle([cx - 9 + leg_spread, base_y - 5,
                        cx - 4 + leg_spread, base_y], fill=RNG_BOOT, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, base_y - 5,
                        cx + 9 - leg_spread, base_y], fill=RNG_BOOT, outline=OUTLINE)

        # Body (back)
        ellipse(draw, cx, body_cy, 13, 11, RNG_LEATHER)
        ellipse(draw, cx, body_cy, 9, 8, RNG_LEATHER_DARK, outline=None)
        draw.line([(cx, body_cy - 5), (cx, body_cy + 5)],
                  fill=_darken(RNG_LEATHER, 0.7), width=1)
        draw.rectangle([cx - 13, body_cy + 8, cx + 13, body_cy + 13],
                       fill=RNG_BELT, outline=OUTLINE)

        # Arms
        draw.rectangle([cx - 17, body_cy - 5, cx - 12, body_cy + 5],
                       fill=RNG_LEATHER, outline=OUTLINE)
        draw.rectangle([cx + 12, body_cy - 5, cx + 17, body_cy + 5],
                       fill=RNG_LEATHER, outline=OUTLINE)
        ellipse(draw, cx - 14, body_cy - 5, 5, 3, RNG_LEATHER_LIGHT)
        ellipse(draw, cx + 14, body_cy - 5, 5, 3, RNG_LEATHER_LIGHT)

        # Head (back)
        ellipse(draw, cx, head_cy, 13, 12, RNG_SKIN)
        ellipse(draw, cx, head_cy - 2, 9, 8, RNG_SKIN_DARK, outline=None)
        # Hood
        ellipse(draw, cx, head_cy - 2, 16, 12, RNG_HOOD)
        ellipse(draw, cx, head_cy, 12, 10, RNG_HOOD_DARK, outline=None)
        draw.polygon([(cx - 4, head_cy - 14), (cx, head_cy - 20),
                      (cx + 4, head_cy - 14)], fill=RNG_HOOD, outline=OUTLINE)
        draw.line([(cx, head_cy - 8), (cx, head_cy + 4)],
                  fill=RNG_HOOD_DARK, width=1)

    elif direction == LEFT:
        # Quiver trailing behind
        draw.rectangle([cx + 4, body_cy - 12, cx + 8, body_cy + 4],
                       fill=RNG_QUIVER, outline=OUTLINE)
        draw.line([(cx + 5, body_cy - 16), (cx + 5, body_cy - 12)],
                  fill=RNG_ARROW_TIP, width=1)
        draw.line([(cx + 7, body_cy - 14), (cx + 7, body_cy - 12)],
                  fill=RNG_ARROW_TIP, width=1)
        draw.point((cx + 5, body_cy - 17), fill=RNG_FEATHER)

        # Legs
        draw.rectangle([cx - 2 - leg_spread, body_cy + 10,
                        cx + 4 - leg_spread, base_y],
                       fill=_darken(RNG_LEG, 0.85), outline=OUTLINE)
        draw.rectangle([cx - 2 - leg_spread, base_y - 5,
                        cx + 4 - leg_spread, base_y], fill=RNG_BOOT, outline=OUTLINE)
        draw.rectangle([cx - 8 + leg_spread, body_cy + 10,
                        cx - 2 + leg_spread, base_y], fill=RNG_LEG, outline=OUTLINE)
        draw.rectangle([cx - 8 + leg_spread, base_y - 5,
                        cx - 2 + leg_spread, base_y], fill=RNG_BOOT, outline=OUTLINE)

        # Body
        ellipse(draw, cx - 2, body_cy, 11, 11, RNG_LEATHER)
        ellipse(draw, cx - 4, body_cy - 2, 6, 6, RNG_LEATHER_LIGHT, outline=None)
        # Strap
        draw.line([(cx - 6, body_cy - 8), (cx + 4, body_cy + 4)],
                  fill=RNG_QUIVER_DARK, width=2)
        draw.rectangle([cx - 13, body_cy + 8, cx + 9, body_cy + 13],
                       fill=RNG_BELT, outline=OUTLINE)

        # Front arm
        draw.rectangle([cx - 14, body_cy - 4, cx - 8, body_cy + 5],
                       fill=RNG_LEATHER, outline=OUTLINE)
        draw.rectangle([cx - 14, body_cy + 1, cx - 8, body_cy + 5],
                       fill=RNG_SKIN, outline=OUTLINE)
        ellipse(draw, cx - 10, body_cy - 5, 5, 3, RNG_LEATHER_LIGHT)

        # Head
        ellipse(draw, cx - 2, head_cy, 12, 12, RNG_SKIN)
        ellipse(draw, cx - 4, head_cy + 2, 8, 7, RNG_SKIN)
        ellipse(draw, cx - 2, head_cy + 4, 5, 4, RNG_SKIN_DARK, outline=None)
        draw.rectangle([cx - 10, head_cy + 1, cx - 7, head_cy + 4], fill=(255, 255, 255))
        draw.rectangle([cx - 9, head_cy + 2, cx - 8, head_cy + 3], fill=BLACK)
        draw.point((cx - 9, head_cy + 5), fill=RNG_SKIN_DARK)
        # Hood
        ellipse(draw, cx - 2, head_cy - 4, 14, 10, RNG_HOOD)
        ellipse(draw, cx + 2, head_cy - 2, 8, 7, RNG_HOOD_DARK, outline=None)
        draw.polygon([(cx - 6, head_cy - 14), (cx - 2, head_cy - 20),
                      (cx + 2, head_cy - 14)], fill=RNG_HOOD, outline=OUTLINE)

    else:  # RIGHT
        # Quiver trailing behind
        draw.rectangle([cx - 8, body_cy - 12, cx - 4, body_cy + 4],
                       fill=RNG_QUIVER, outline=OUTLINE)
        draw.line([(cx - 7, body_cy - 16), (cx - 7, body_cy - 12)],
                  fill=RNG_ARROW_TIP, width=1)
        draw.line([(cx - 5, body_cy - 14), (cx - 5, body_cy - 12)],
                  fill=RNG_ARROW_TIP, width=1)
        draw.point((cx - 7, body_cy - 17), fill=RNG_FEATHER)

        # Legs
        draw.rectangle([cx - 2 + leg_spread, body_cy + 10,
                        cx + 4 + leg_spread, base_y],
                       fill=_darken(RNG_LEG, 0.85), outline=OUTLINE)
        draw.rectangle([cx - 2 + leg_spread, base_y - 5,
                        cx + 4 + leg_spread, base_y], fill=RNG_BOOT, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, body_cy + 10,
                        cx + 10 - leg_spread, base_y], fill=RNG_LEG, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, base_y - 5,
                        cx + 10 - leg_spread, base_y], fill=RNG_BOOT, outline=OUTLINE)

        # Body
        ellipse(draw, cx + 2, body_cy, 11, 11, RNG_LEATHER)
        ellipse(draw, cx, body_cy - 2, 6, 6, RNG_LEATHER_LIGHT, outline=None)
        draw.line([(cx + 6, body_cy - 8), (cx - 4, body_cy + 4)],
                  fill=RNG_QUIVER_DARK, width=2)
        draw.rectangle([cx - 9, body_cy + 8, cx + 13, body_cy + 13],
                       fill=RNG_BELT, outline=OUTLINE)

        # Front arm
        draw.rectangle([cx + 8, body_cy - 4, cx + 14, body_cy + 5],
                       fill=RNG_LEATHER, outline=OUTLINE)
        draw.rectangle([cx + 8, body_cy + 1, cx + 14, body_cy + 5],
                       fill=RNG_SKIN, outline=OUTLINE)
        ellipse(draw, cx + 10, body_cy - 5, 5, 3, RNG_LEATHER_LIGHT)

        # Head
        ellipse(draw, cx + 2, head_cy, 12, 12, RNG_SKIN)
        ellipse(draw, cx + 4, head_cy + 2, 8, 7, RNG_SKIN)
        ellipse(draw, cx + 6, head_cy + 4, 5, 4, RNG_SKIN_DARK, outline=None)
        draw.rectangle([cx + 7, head_cy + 1, cx + 10, head_cy + 4], fill=(255, 255, 255))
        draw.rectangle([cx + 8, head_cy + 2, cx + 9, head_cy + 3], fill=BLACK)
        draw.point((cx + 9, head_cy + 5), fill=RNG_SKIN_DARK)
        # Hood
        ellipse(draw, cx + 2, head_cy - 4, 14, 10, RNG_HOOD)
        ellipse(draw, cx - 2, head_cy - 2, 8, 7, RNG_HOOD_DARK, outline=None)
        draw.polygon([(cx - 2, head_cy - 14), (cx + 2, head_cy - 20),
                      (cx + 6, head_cy - 14)], fill=RNG_HOOD, outline=OUTLINE)


# ===================================================================
# BERSERKER (ID 44) -- Minimal armor (fur straps), warpaint,
#                       twin axes on back, wild hair, muscular
# ===================================================================

def draw_berserker(draw, ox, oy, direction, frame):
    bob = [0, -2, 0, -1][frame]
    leg_spread = [-4, 0, 4, 0][frame]

    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 20
    head_cy = body_cy - 20

    if direction == DOWN:
        # Twin axes on back
        draw.line([(cx - 6, body_cy - 14), (cx - 6, body_cy + 6)],
                  fill=BER_AXE_HANDLE, width=2)
        draw.line([(cx + 6, body_cy - 14), (cx + 6, body_cy + 6)],
                  fill=BER_AXE_HANDLE, width=2)
        # Axe heads poking above shoulders
        draw.polygon([(cx - 10, body_cy - 18), (cx - 6, body_cy - 14),
                      (cx - 2, body_cy - 18)], fill=BER_AXE_HEAD, outline=OUTLINE)
        draw.polygon([(cx + 2, body_cy - 18), (cx + 6, body_cy - 14),
                      (cx + 10, body_cy - 18)], fill=BER_AXE_HEAD, outline=OUTLINE)
        draw.line([(cx - 8, body_cy - 16), (cx - 4, body_cy - 16)],
                  fill=BER_AXE_DARK, width=1)
        draw.line([(cx + 4, body_cy - 16), (cx + 8, body_cy - 16)],
                  fill=BER_AXE_DARK, width=1)

        # Legs (thick, muscular)
        draw.rectangle([cx - 10 + leg_spread, body_cy + 10,
                        cx - 4 + leg_spread, base_y], fill=BER_LEG, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, body_cy + 10,
                        cx + 10 - leg_spread, base_y], fill=BER_LEG, outline=OUTLINE)
        draw.rectangle([cx - 10 + leg_spread, base_y - 5,
                        cx - 4 + leg_spread, base_y], fill=BER_BOOT, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, base_y - 5,
                        cx + 10 - leg_spread, base_y], fill=BER_BOOT, outline=OUTLINE)

        # Muscular torso (exposed skin with fur straps)
        ellipse(draw, cx, body_cy, 15, 13, BER_SKIN)
        ellipse(draw, cx - 3, body_cy - 2, 9, 7, BER_SKIN_LIGHT, outline=None)
        # Chest muscles
        draw.line([(cx, body_cy - 6), (cx, body_cy + 2)],
                  fill=BER_SKIN_DARK, width=1)
        # Fur straps across chest
        draw.line([(cx - 12, body_cy - 8), (cx + 8, body_cy + 4)],
                  fill=BER_FUR, width=3)
        draw.line([(cx + 12, body_cy - 8), (cx - 8, body_cy + 4)],
                  fill=BER_FUR, width=3)
        # Fur texture dots on straps
        draw.point((cx - 4, body_cy - 4), fill=BER_FUR_LIGHT)
        draw.point((cx + 4, body_cy - 4), fill=BER_FUR_LIGHT)
        draw.point((cx, body_cy), fill=BER_FUR_DARK)
        # Belt
        draw.rectangle([cx - 15, body_cy + 8, cx + 15, body_cy + 14],
                       fill=BER_BELT, outline=OUTLINE)
        draw.rectangle([cx - 3, body_cy + 9, cx + 3, body_cy + 13],
                       fill=BER_BELT_BUCKLE, outline=OUTLINE)

        # Muscular arms (wider)
        draw.rectangle([cx - 20, body_cy - 6, cx - 13, body_cy + 6],
                       fill=BER_SKIN, outline=OUTLINE)
        draw.rectangle([cx - 20, body_cy - 6, cx - 18, body_cy + 4],
                       fill=BER_SKIN_LIGHT, outline=None)
        draw.rectangle([cx + 13, body_cy - 6, cx + 20, body_cy + 6],
                       fill=BER_SKIN, outline=OUTLINE)
        draw.rectangle([cx + 13, body_cy - 6, cx + 15, body_cy + 4],
                       fill=BER_SKIN_LIGHT, outline=None)
        # Warpaint on arms
        draw.line([(cx - 18, body_cy - 2), (cx - 14, body_cy - 2)],
                  fill=BER_WARPAINT, width=1)
        draw.line([(cx + 14, body_cy - 2), (cx + 18, body_cy - 2)],
                  fill=BER_WARPAINT, width=1)
        # Fur shoulder pads
        ellipse(draw, cx - 15, body_cy - 7, 6, 4, BER_FUR)
        draw.point((cx - 17, body_cy - 9), fill=BER_FUR_LIGHT)
        ellipse(draw, cx + 15, body_cy - 7, 6, 4, BER_FUR)
        draw.point((cx + 13, body_cy - 9), fill=BER_FUR_LIGHT)

        # Head
        ellipse(draw, cx, head_cy, 14, 13, BER_SKIN)
        ellipse(draw, cx + 2, head_cy + 4, 8, 6, BER_SKIN_DARK, outline=None)
        # Eyes
        draw.rectangle([cx - 6, head_cy + 1, cx - 3, head_cy + 5], fill=(255, 255, 255))
        draw.rectangle([cx - 5, head_cy + 2, cx - 4, head_cy + 4], fill=BLACK)
        draw.rectangle([cx + 3, head_cy + 1, cx + 6, head_cy + 5], fill=(255, 255, 255))
        draw.rectangle([cx + 4, head_cy + 2, cx + 5, head_cy + 4], fill=BLACK)
        draw.point((cx, head_cy + 6), fill=BER_SKIN_DARK)
        draw.line([(cx - 2, head_cy + 8), (cx + 2, head_cy + 8)],
                  fill=BER_SKIN_DARK, width=1)
        # Warpaint on face
        draw.line([(cx - 8, head_cy + 2), (cx - 3, head_cy + 2)],
                  fill=BER_WARPAINT, width=2)
        draw.line([(cx + 3, head_cy + 2), (cx + 8, head_cy + 2)],
                  fill=BER_WARPAINT, width=2)
        # Wild hair
        for hx in range(-12, 14, 3):
            hy_off = abs(hx) // 3
            draw.line([(cx + hx, head_cy - 10), (cx + hx + 1, head_cy - 18 + hy_off)],
                      fill=BER_HAIR, width=2)
        draw.point((cx - 8, head_cy - 16), fill=BER_HAIR_DARK)
        draw.point((cx + 6, head_cy - 14), fill=BER_HAIR_DARK)

    elif direction == UP:
        # Axes on back visible
        draw.line([(cx - 6, body_cy - 14), (cx - 6, body_cy + 6)],
                  fill=BER_AXE_HANDLE, width=2)
        draw.line([(cx + 6, body_cy - 14), (cx + 6, body_cy + 6)],
                  fill=BER_AXE_HANDLE, width=2)
        draw.polygon([(cx - 10, body_cy - 18), (cx - 6, body_cy - 14),
                      (cx - 2, body_cy - 18)], fill=BER_AXE_HEAD, outline=OUTLINE)
        draw.polygon([(cx + 2, body_cy - 18), (cx + 6, body_cy - 14),
                      (cx + 10, body_cy - 18)], fill=BER_AXE_HEAD, outline=OUTLINE)

        # Legs
        draw.rectangle([cx - 10 + leg_spread, body_cy + 10,
                        cx - 4 + leg_spread, base_y], fill=BER_LEG, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, body_cy + 10,
                        cx + 10 - leg_spread, base_y], fill=BER_LEG, outline=OUTLINE)
        draw.rectangle([cx - 10 + leg_spread, base_y - 5,
                        cx - 4 + leg_spread, base_y], fill=BER_BOOT, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, base_y - 5,
                        cx + 10 - leg_spread, base_y], fill=BER_BOOT, outline=OUTLINE)

        # Body (back)
        ellipse(draw, cx, body_cy, 15, 13, BER_SKIN)
        ellipse(draw, cx, body_cy, 11, 10, BER_SKIN_DARK, outline=None)
        # Fur X straps from back
        draw.line([(cx - 10, body_cy - 8), (cx + 10, body_cy + 4)],
                  fill=BER_FUR, width=3)
        draw.line([(cx + 10, body_cy - 8), (cx - 10, body_cy + 4)],
                  fill=BER_FUR, width=3)
        draw.rectangle([cx - 15, body_cy + 8, cx + 15, body_cy + 14],
                       fill=BER_BELT, outline=OUTLINE)

        # Arms
        draw.rectangle([cx - 20, body_cy - 6, cx - 13, body_cy + 6],
                       fill=BER_SKIN, outline=OUTLINE)
        draw.rectangle([cx + 13, body_cy - 6, cx + 20, body_cy + 6],
                       fill=BER_SKIN, outline=OUTLINE)
        ellipse(draw, cx - 15, body_cy - 7, 6, 4, BER_FUR)
        ellipse(draw, cx + 15, body_cy - 7, 6, 4, BER_FUR)

        # Head (back)
        ellipse(draw, cx, head_cy, 14, 13, BER_SKIN)
        ellipse(draw, cx, head_cy - 2, 10, 8, BER_SKIN_DARK, outline=None)
        # Wild hair from back
        for hx in range(-12, 14, 3):
            hy_off = abs(hx) // 3
            draw.line([(cx + hx, head_cy - 10), (cx + hx + 1, head_cy - 18 + hy_off)],
                      fill=BER_HAIR, width=2)

    elif direction == LEFT:
        # Axe on back
        draw.line([(cx + 6, body_cy - 14), (cx + 6, body_cy + 6)],
                  fill=BER_AXE_HANDLE, width=2)
        draw.polygon([(cx + 2, body_cy - 18), (cx + 6, body_cy - 14),
                      (cx + 10, body_cy - 18)], fill=BER_AXE_HEAD, outline=OUTLINE)

        # Legs
        draw.rectangle([cx - 2 - leg_spread, body_cy + 10,
                        cx + 4 - leg_spread, base_y],
                       fill=_darken(BER_LEG, 0.85), outline=OUTLINE)
        draw.rectangle([cx - 2 - leg_spread, base_y - 5,
                        cx + 4 - leg_spread, base_y], fill=BER_BOOT, outline=OUTLINE)
        draw.rectangle([cx - 8 + leg_spread, body_cy + 10,
                        cx - 2 + leg_spread, base_y], fill=BER_LEG, outline=OUTLINE)
        draw.rectangle([cx - 8 + leg_spread, base_y - 5,
                        cx - 2 + leg_spread, base_y], fill=BER_BOOT, outline=OUTLINE)

        # Body
        ellipse(draw, cx - 2, body_cy, 13, 13, BER_SKIN)
        ellipse(draw, cx - 4, body_cy - 2, 7, 7, BER_SKIN_LIGHT, outline=None)
        # Fur strap
        draw.line([(cx - 10, body_cy - 8), (cx + 6, body_cy + 4)],
                  fill=BER_FUR, width=3)
        draw.rectangle([cx - 15, body_cy + 8, cx + 11, body_cy + 14],
                       fill=BER_BELT, outline=OUTLINE)

        # Arm
        draw.rectangle([cx - 16, body_cy - 5, cx - 10, body_cy + 6],
                       fill=BER_SKIN, outline=OUTLINE)
        draw.rectangle([cx - 16, body_cy - 5, cx - 14, body_cy + 4],
                       fill=BER_SKIN_LIGHT, outline=None)
        draw.line([(cx - 14, body_cy - 1), (cx - 11, body_cy - 1)],
                  fill=BER_WARPAINT, width=1)
        ellipse(draw, cx - 12, body_cy - 7, 5, 4, BER_FUR)

        # Head
        ellipse(draw, cx - 2, head_cy, 13, 13, BER_SKIN)
        ellipse(draw, cx - 4, head_cy + 2, 8, 7, BER_SKIN)
        ellipse(draw, cx - 2, head_cy + 4, 5, 4, BER_SKIN_DARK, outline=None)
        draw.rectangle([cx - 10, head_cy + 1, cx - 7, head_cy + 5], fill=(255, 255, 255))
        draw.rectangle([cx - 9, head_cy + 2, cx - 8, head_cy + 4], fill=BLACK)
        draw.point((cx - 9, head_cy + 6), fill=BER_SKIN_DARK)
        # Warpaint
        draw.line([(cx - 12, head_cy + 2), (cx - 7, head_cy + 2)],
                  fill=BER_WARPAINT, width=2)
        # Wild hair
        for hx in range(-10, 6, 3):
            hy_off = abs(hx) // 3
            draw.line([(cx + hx, head_cy - 10), (cx + hx + 1, head_cy - 18 + hy_off)],
                      fill=BER_HAIR, width=2)

    else:  # RIGHT
        # Axe on back
        draw.line([(cx - 6, body_cy - 14), (cx - 6, body_cy + 6)],
                  fill=BER_AXE_HANDLE, width=2)
        draw.polygon([(cx - 10, body_cy - 18), (cx - 6, body_cy - 14),
                      (cx - 2, body_cy - 18)], fill=BER_AXE_HEAD, outline=OUTLINE)

        # Legs
        draw.rectangle([cx - 2 + leg_spread, body_cy + 10,
                        cx + 4 + leg_spread, base_y],
                       fill=_darken(BER_LEG, 0.85), outline=OUTLINE)
        draw.rectangle([cx - 2 + leg_spread, base_y - 5,
                        cx + 4 + leg_spread, base_y], fill=BER_BOOT, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, body_cy + 10,
                        cx + 10 - leg_spread, base_y], fill=BER_LEG, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, base_y - 5,
                        cx + 10 - leg_spread, base_y], fill=BER_BOOT, outline=OUTLINE)

        # Body
        ellipse(draw, cx + 2, body_cy, 13, 13, BER_SKIN)
        ellipse(draw, cx, body_cy - 2, 7, 7, BER_SKIN_LIGHT, outline=None)
        draw.line([(cx + 10, body_cy - 8), (cx - 6, body_cy + 4)],
                  fill=BER_FUR, width=3)
        draw.rectangle([cx - 11, body_cy + 8, cx + 15, body_cy + 14],
                       fill=BER_BELT, outline=OUTLINE)

        # Arm
        draw.rectangle([cx + 10, body_cy - 5, cx + 16, body_cy + 6],
                       fill=BER_SKIN, outline=OUTLINE)
        draw.rectangle([cx + 14, body_cy - 5, cx + 16, body_cy + 4],
                       fill=BER_SKIN_LIGHT, outline=None)
        draw.line([(cx + 11, body_cy - 1), (cx + 14, body_cy - 1)],
                  fill=BER_WARPAINT, width=1)
        ellipse(draw, cx + 12, body_cy - 7, 5, 4, BER_FUR)

        # Head
        ellipse(draw, cx + 2, head_cy, 13, 13, BER_SKIN)
        ellipse(draw, cx + 4, head_cy + 2, 8, 7, BER_SKIN)
        ellipse(draw, cx + 6, head_cy + 4, 5, 4, BER_SKIN_DARK, outline=None)
        draw.rectangle([cx + 7, head_cy + 1, cx + 10, head_cy + 5], fill=(255, 255, 255))
        draw.rectangle([cx + 8, head_cy + 2, cx + 9, head_cy + 4], fill=BLACK)
        draw.point((cx + 9, head_cy + 6), fill=BER_SKIN_DARK)
        # Warpaint
        draw.line([(cx + 7, head_cy + 2), (cx + 12, head_cy + 2)],
                  fill=BER_WARPAINT, width=2)
        # Wild hair
        for hx in range(-4, 12, 3):
            hy_off = abs(hx) // 3
            draw.line([(cx + hx, head_cy - 10), (cx + hx + 1, head_cy - 18 + hy_off)],
                      fill=BER_HAIR, width=2)


# ===================================================================
# CRUSADER (ID 45) -- Heavy silver plate, red tabard with cross,
#                      great helm, red cape
# ===================================================================

def draw_crusader(draw, ox, oy, direction, frame):
    bob = [0, -2, 0, -1][frame]
    leg_spread = [-4, 0, 4, 0][frame]

    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 20
    head_cy = body_cy - 20

    if direction == DOWN:
        # Red cape behind
        cape_sway = [0, 2, 0, -2][frame]
        draw.polygon([
            (cx - 16, body_cy - 8),
            (cx + 16, body_cy - 8),
            (cx + 18 + cape_sway, base_y),
            (cx - 18 + cape_sway, base_y),
        ], fill=CRU_CAPE, outline=OUTLINE)
        draw.line([(cx - 6, body_cy - 2), (cx - 8 + cape_sway, base_y - 4)],
                  fill=CRU_CAPE_DARK, width=1)
        draw.line([(cx + 6, body_cy - 2), (cx + 8 + cape_sway, base_y - 4)],
                  fill=CRU_CAPE_DARK, width=1)

        # Legs
        draw.rectangle([cx - 10 + leg_spread, body_cy + 10,
                        cx - 4 + leg_spread, base_y], fill=CRU_LEG, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, body_cy + 10,
                        cx + 10 - leg_spread, base_y], fill=CRU_LEG, outline=OUTLINE)
        draw.rectangle([cx - 10 + leg_spread, base_y - 5,
                        cx - 4 + leg_spread, base_y], fill=CRU_BOOT, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, base_y - 5,
                        cx + 10 - leg_spread, base_y], fill=CRU_BOOT, outline=OUTLINE)

        # Heavy plate body
        ellipse(draw, cx, body_cy, 15, 13, CRU_ARMOR)
        ellipse(draw, cx - 3, body_cy - 2, 9, 7, CRU_ARMOR_LIGHT, outline=None)
        # Red tabard over armor
        draw.rectangle([cx - 8, body_cy - 8, cx + 8, body_cy + 8],
                       fill=CRU_TABARD, outline=None)
        # White cross on tabard
        draw.rectangle([cx - 1, body_cy - 6, cx + 1, body_cy + 6],
                       fill=CRU_CROSS, outline=None)
        draw.rectangle([cx - 5, body_cy - 2, cx + 5, body_cy],
                       fill=CRU_CROSS, outline=None)
        # Belt
        draw.rectangle([cx - 15, body_cy + 8, cx + 15, body_cy + 14],
                       fill=CRU_ARMOR_DARK, outline=OUTLINE)
        draw.rectangle([cx - 3, body_cy + 9, cx + 3, body_cy + 13],
                       fill=CRU_ARMOR_LIGHT, outline=OUTLINE)

        # Arms
        draw.rectangle([cx - 19, body_cy - 6, cx - 13, body_cy + 6],
                       fill=CRU_ARMOR, outline=OUTLINE)
        draw.rectangle([cx - 19, body_cy + 2, cx - 13, body_cy + 6],
                       fill=CRU_SKIN, outline=OUTLINE)
        draw.rectangle([cx + 13, body_cy - 6, cx + 19, body_cy + 6],
                       fill=CRU_ARMOR, outline=OUTLINE)
        draw.rectangle([cx + 13, body_cy + 2, cx + 19, body_cy + 6],
                       fill=CRU_SKIN, outline=OUTLINE)
        # Heavy shoulder pads
        ellipse(draw, cx - 15, body_cy - 7, 7, 5, CRU_ARMOR_LIGHT)
        ellipse(draw, cx - 15, body_cy - 9, 5, 3, _brighten(CRU_ARMOR_LIGHT, 1.1), outline=None)
        ellipse(draw, cx + 15, body_cy - 7, 7, 5, CRU_ARMOR_LIGHT)
        ellipse(draw, cx + 15, body_cy - 9, 5, 3, _brighten(CRU_ARMOR_LIGHT, 1.1), outline=None)

        # Great helm (covers entire head)
        ellipse(draw, cx, head_cy, 16, 14, CRU_HELM)
        ellipse(draw, cx + 4, head_cy + 2, 10, 8, CRU_HELM_DARK, outline=None)
        ellipse(draw, cx - 2, head_cy - 2, 10, 8, CRU_HELM, outline=None)
        ellipse(draw, cx - 4, head_cy - 4, 8, 6, CRU_HELM_LIGHT, outline=None)
        # Visor slit
        draw.rectangle([cx - 8, head_cy + 1, cx + 8, head_cy + 3],
                       fill=BLACK, outline=None)
        # Ventilation holes
        draw.point((cx - 4, head_cy + 6), fill=BLACK)
        draw.point((cx, head_cy + 6), fill=BLACK)
        draw.point((cx + 4, head_cy + 6), fill=BLACK)
        # Crest
        draw.rectangle([cx - 2, head_cy - 16, cx + 2, head_cy - 6],
                       fill=CRU_TABARD, outline=OUTLINE)
        # Cross on helm forehead
        draw.line([(cx, head_cy - 4), (cx, head_cy + 1)],
                  fill=CRU_CROSS, width=1)
        draw.line([(cx - 2, head_cy - 2), (cx + 2, head_cy - 2)],
                  fill=CRU_CROSS, width=1)

    elif direction == UP:
        cape_sway = [0, 2, 0, -2][frame]
        draw.polygon([
            (cx - 16, body_cy - 8),
            (cx + 16, body_cy - 8),
            (cx + 20 + cape_sway, base_y),
            (cx - 20 + cape_sway, base_y),
        ], fill=CRU_CAPE, outline=OUTLINE)
        draw.line([(cx - 4, body_cy - 2), (cx - 6 + cape_sway, base_y - 4)],
                  fill=CRU_CAPE_DARK, width=1)
        draw.line([(cx + 4, body_cy - 2), (cx + 6 + cape_sway, base_y - 4)],
                  fill=CRU_CAPE_DARK, width=1)
        draw.line([(cx, body_cy), (cx + cape_sway, base_y - 4)],
                  fill=CRU_CAPE_DARK, width=1)

        # Legs
        draw.rectangle([cx - 10 + leg_spread, body_cy + 10,
                        cx - 4 + leg_spread, base_y], fill=CRU_LEG, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, body_cy + 10,
                        cx + 10 - leg_spread, base_y], fill=CRU_LEG, outline=OUTLINE)
        draw.rectangle([cx - 10 + leg_spread, base_y - 5,
                        cx - 4 + leg_spread, base_y], fill=CRU_BOOT, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, base_y - 5,
                        cx + 10 - leg_spread, base_y], fill=CRU_BOOT, outline=OUTLINE)

        # Body (back)
        ellipse(draw, cx, body_cy, 15, 13, CRU_ARMOR)
        ellipse(draw, cx, body_cy, 11, 10, CRU_ARMOR_DARK, outline=None)
        draw.line([(cx, body_cy - 6), (cx, body_cy + 6)],
                  fill=_darken(CRU_ARMOR, 0.7), width=1)
        draw.rectangle([cx - 15, body_cy + 8, cx + 15, body_cy + 14],
                       fill=CRU_ARMOR_DARK, outline=OUTLINE)

        # Arms
        draw.rectangle([cx - 19, body_cy - 6, cx - 13, body_cy + 6],
                       fill=CRU_ARMOR, outline=OUTLINE)
        draw.rectangle([cx + 13, body_cy - 6, cx + 19, body_cy + 6],
                       fill=CRU_ARMOR, outline=OUTLINE)
        ellipse(draw, cx - 15, body_cy - 7, 7, 5, CRU_ARMOR_LIGHT)
        ellipse(draw, cx + 15, body_cy - 7, 7, 5, CRU_ARMOR_LIGHT)

        # Great helm (back)
        ellipse(draw, cx, head_cy, 16, 14, CRU_HELM)
        ellipse(draw, cx, head_cy, 12, 10, CRU_HELM_DARK, outline=None)
        draw.rectangle([cx - 2, head_cy - 16, cx + 2, head_cy - 6],
                       fill=CRU_TABARD, outline=OUTLINE)

    elif direction == LEFT:
        # Cape
        cape_sway = [0, 2, 0, -2][frame]
        draw.polygon([
            (cx + 6, body_cy - 10),
            (cx + 16, body_cy - 6),
            (cx + 18 + cape_sway, base_y),
            (cx + 6, base_y),
        ], fill=CRU_CAPE, outline=OUTLINE)
        draw.line([(cx + 10, body_cy - 4), (cx + 12 + cape_sway, base_y - 4)],
                  fill=CRU_CAPE_DARK, width=1)

        # Legs
        draw.rectangle([cx - 2 - leg_spread, body_cy + 10,
                        cx + 4 - leg_spread, base_y],
                       fill=_darken(CRU_LEG, 0.85), outline=OUTLINE)
        draw.rectangle([cx - 2 - leg_spread, base_y - 5,
                        cx + 4 - leg_spread, base_y], fill=CRU_BOOT, outline=OUTLINE)
        draw.rectangle([cx - 8 + leg_spread, body_cy + 10,
                        cx - 2 + leg_spread, base_y], fill=CRU_LEG, outline=OUTLINE)
        draw.rectangle([cx - 8 + leg_spread, base_y - 5,
                        cx - 2 + leg_spread, base_y], fill=CRU_BOOT, outline=OUTLINE)

        # Body
        ellipse(draw, cx - 2, body_cy, 13, 13, CRU_ARMOR)
        ellipse(draw, cx - 4, body_cy - 2, 7, 7, CRU_ARMOR_LIGHT, outline=None)
        # Tabard visible from side
        draw.rectangle([cx - 8, body_cy - 6, cx + 2, body_cy + 6],
                       fill=CRU_TABARD, outline=None)
        # Cross on side
        draw.line([(cx - 3, body_cy - 4), (cx - 3, body_cy + 4)],
                  fill=CRU_CROSS, width=1)
        draw.line([(cx - 6, body_cy), (cx, body_cy)],
                  fill=CRU_CROSS, width=1)
        draw.rectangle([cx - 15, body_cy + 8, cx + 11, body_cy + 14],
                       fill=CRU_ARMOR_DARK, outline=OUTLINE)

        # Arm
        draw.rectangle([cx - 16, body_cy - 4, cx - 10, body_cy + 6],
                       fill=CRU_ARMOR, outline=OUTLINE)
        draw.rectangle([cx - 16, body_cy + 2, cx - 10, body_cy + 6],
                       fill=CRU_SKIN, outline=OUTLINE)
        ellipse(draw, cx - 12, body_cy - 7, 6, 4, CRU_ARMOR_LIGHT)

        # Great helm
        ellipse(draw, cx - 2, head_cy, 14, 14, CRU_HELM)
        ellipse(draw, cx + 2, head_cy + 2, 8, 8, CRU_HELM_DARK, outline=None)
        ellipse(draw, cx - 4, head_cy - 2, 8, 8, CRU_HELM_LIGHT, outline=None)
        # Visor slit
        draw.rectangle([cx - 12, head_cy + 1, cx - 2, head_cy + 3],
                       fill=BLACK, outline=None)
        # Crest
        draw.rectangle([cx - 4, head_cy - 16, cx, head_cy - 6],
                       fill=CRU_TABARD, outline=OUTLINE)

    else:  # RIGHT
        # Cape
        cape_sway = [0, -2, 0, 2][frame]
        draw.polygon([
            (cx - 6, body_cy - 10),
            (cx - 16, body_cy - 6),
            (cx - 18 + cape_sway, base_y),
            (cx - 6, base_y),
        ], fill=CRU_CAPE, outline=OUTLINE)
        draw.line([(cx - 10, body_cy - 4), (cx - 12 + cape_sway, base_y - 4)],
                  fill=CRU_CAPE_DARK, width=1)

        # Legs
        draw.rectangle([cx - 2 + leg_spread, body_cy + 10,
                        cx + 4 + leg_spread, base_y],
                       fill=_darken(CRU_LEG, 0.85), outline=OUTLINE)
        draw.rectangle([cx - 2 + leg_spread, base_y - 5,
                        cx + 4 + leg_spread, base_y], fill=CRU_BOOT, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, body_cy + 10,
                        cx + 10 - leg_spread, base_y], fill=CRU_LEG, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, base_y - 5,
                        cx + 10 - leg_spread, base_y], fill=CRU_BOOT, outline=OUTLINE)

        # Body
        ellipse(draw, cx + 2, body_cy, 13, 13, CRU_ARMOR)
        ellipse(draw, cx, body_cy - 2, 7, 7, CRU_ARMOR_LIGHT, outline=None)
        draw.rectangle([cx - 2, body_cy - 6, cx + 8, body_cy + 6],
                       fill=CRU_TABARD, outline=None)
        draw.line([(cx + 3, body_cy - 4), (cx + 3, body_cy + 4)],
                  fill=CRU_CROSS, width=1)
        draw.line([(cx, body_cy), (cx + 6, body_cy)],
                  fill=CRU_CROSS, width=1)
        draw.rectangle([cx - 11, body_cy + 8, cx + 15, body_cy + 14],
                       fill=CRU_ARMOR_DARK, outline=OUTLINE)

        # Arm
        draw.rectangle([cx + 10, body_cy - 4, cx + 16, body_cy + 6],
                       fill=CRU_ARMOR, outline=OUTLINE)
        draw.rectangle([cx + 10, body_cy + 2, cx + 16, body_cy + 6],
                       fill=CRU_SKIN, outline=OUTLINE)
        ellipse(draw, cx + 12, body_cy - 7, 6, 4, CRU_ARMOR_LIGHT)

        # Great helm
        ellipse(draw, cx + 2, head_cy, 14, 14, CRU_HELM)
        ellipse(draw, cx + 6, head_cy + 2, 8, 8, CRU_HELM_DARK, outline=None)
        ellipse(draw, cx, head_cy - 2, 8, 8, CRU_HELM_LIGHT, outline=None)
        draw.rectangle([cx + 2, head_cy + 1, cx + 12, head_cy + 3],
                       fill=BLACK, outline=None)
        draw.rectangle([cx, head_cy - 16, cx + 4, head_cy - 6],
                       fill=CRU_TABARD, outline=OUTLINE)


# ===================================================================
# DRUID (ID 46) -- Nature robes with leaf patterns, wooden staff
#                   with vines, antler crown
# ===================================================================

def draw_druid(draw, ox, oy, direction, frame):
    bob = [0, -2, 0, -1][frame]
    leg_spread = [-4, 0, 4, 0][frame]
    robe_sway = [-2, 0, 2, 0][frame]

    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 20
    head_cy = body_cy - 20

    if direction == DOWN:
        # Staff behind
        staff_x = cx + 16
        draw.line([(staff_x, head_cy - 8), (staff_x, base_y + 2)],
                  fill=DRU_STAFF, width=2)
        # Vine wrapping staff
        for vy in range(head_cy - 6, base_y, 6):
            draw.point((staff_x - 1, vy), fill=DRU_VINE)
            draw.point((staff_x + 1, vy + 3), fill=DRU_VINE)
        # Leaf at top
        draw.polygon([(staff_x - 3, head_cy - 12), (staff_x, head_cy - 18),
                      (staff_x + 3, head_cy - 12)], fill=DRU_LEAF, outline=OUTLINE)
        draw.line([(staff_x, head_cy - 16), (staff_x, head_cy - 12)],
                  fill=DRU_LEAF_DARK, width=1)

        # Robe body (flowing)
        draw.polygon([
            (cx - 14, body_cy - 10),
            (cx + 14, body_cy - 10),
            (cx + 16 + robe_sway, base_y + 2),
            (cx - 16 + robe_sway, base_y + 2),
        ], fill=DRU_ROBE, outline=OUTLINE)
        draw.line([(cx - 4, body_cy - 6), (cx - 6 + robe_sway, base_y)],
                  fill=DRU_ROBE_DARK, width=1)
        draw.line([(cx + 4, body_cy - 6), (cx + 6 + robe_sway, base_y)],
                  fill=DRU_ROBE_DARK, width=1)
        draw.line([(cx - 10, body_cy - 4), (cx - 12 + robe_sway, base_y)],
                  fill=DRU_ROBE_LIGHT, width=1)
        # Leaf embroidery on robe
        draw.point((cx - 4, body_cy + 2), fill=DRU_LEAF)
        draw.point((cx + 3, body_cy - 2), fill=DRU_LEAF)
        draw.point((cx - 6, body_cy + 6), fill=DRU_LEAF_DARK)
        draw.point((cx + 5, body_cy + 4), fill=DRU_LEAF)
        # Vine belt
        draw.rectangle([cx - 14, body_cy + 8, cx + 14, body_cy + 12],
                       fill=DRU_VINE, outline=OUTLINE)
        draw.point((cx - 6, body_cy + 9), fill=DRU_VINE_DARK)
        draw.point((cx + 4, body_cy + 10), fill=DRU_VINE_DARK)
        draw.point((cx, body_cy + 9), fill=DRU_LEAF)

        # Arms
        draw.rectangle([cx - 18, body_cy - 6, cx - 12, body_cy + 6],
                       fill=DRU_ROBE, outline=OUTLINE)
        draw.rectangle([cx - 18, body_cy + 2, cx - 12, body_cy + 6],
                       fill=DRU_SKIN, outline=OUTLINE)
        draw.rectangle([cx + 12, body_cy - 6, cx + 18, body_cy + 6],
                       fill=DRU_ROBE, outline=OUTLINE)
        draw.rectangle([cx + 12, body_cy + 2, cx + 18, body_cy + 6],
                       fill=DRU_SKIN, outline=OUTLINE)
        ellipse(draw, cx - 14, body_cy - 6, 5, 3, DRU_ROBE_LIGHT)
        ellipse(draw, cx + 14, body_cy - 6, 5, 3, DRU_ROBE_LIGHT)

        # Head
        ellipse(draw, cx, head_cy, 14, 13, DRU_HAIR)
        ellipse(draw, cx, head_cy + 2, 12, 10, DRU_SKIN)
        ellipse(draw, cx + 2, head_cy + 4, 8, 6, DRU_SKIN_DARK, outline=None)
        # Eyes
        draw.rectangle([cx - 6, head_cy + 1, cx - 3, head_cy + 5], fill=(255, 255, 255))
        draw.rectangle([cx - 5, head_cy + 2, cx - 4, head_cy + 4], fill=(60, 120, 50))
        draw.rectangle([cx + 3, head_cy + 1, cx + 6, head_cy + 5], fill=(255, 255, 255))
        draw.rectangle([cx + 4, head_cy + 2, cx + 5, head_cy + 4], fill=(60, 120, 50))
        draw.point((cx, head_cy + 6), fill=DRU_SKIN_DARK)
        draw.line([(cx - 2, head_cy + 8), (cx + 2, head_cy + 8)],
                  fill=DRU_SKIN_DARK, width=1)
        # Antler crown
        # Left antler
        draw.line([(cx - 8, head_cy - 10), (cx - 12, head_cy - 22)],
                  fill=DRU_ANTLER, width=2)
        draw.line([(cx - 10, head_cy - 16), (cx - 14, head_cy - 20)],
                  fill=DRU_ANTLER, width=1)
        draw.line([(cx - 11, head_cy - 18), (cx - 8, head_cy - 22)],
                  fill=DRU_ANTLER, width=1)
        draw.point((cx - 12, head_cy - 22), fill=DRU_ANTLER_LIGHT)
        draw.point((cx - 14, head_cy - 20), fill=DRU_ANTLER_LIGHT)
        # Right antler
        draw.line([(cx + 8, head_cy - 10), (cx + 12, head_cy - 22)],
                  fill=DRU_ANTLER, width=2)
        draw.line([(cx + 10, head_cy - 16), (cx + 14, head_cy - 20)],
                  fill=DRU_ANTLER, width=1)
        draw.line([(cx + 11, head_cy - 18), (cx + 8, head_cy - 22)],
                  fill=DRU_ANTLER, width=1)
        draw.point((cx + 12, head_cy - 22), fill=DRU_ANTLER_LIGHT)
        draw.point((cx + 14, head_cy - 20), fill=DRU_ANTLER_LIGHT)

    elif direction == UP:
        # Staff behind
        staff_x = cx - 16
        draw.line([(staff_x, head_cy - 8), (staff_x, base_y + 2)],
                  fill=DRU_STAFF, width=2)
        for vy in range(head_cy - 6, base_y, 6):
            draw.point((staff_x + 1, vy), fill=DRU_VINE)
            draw.point((staff_x - 1, vy + 3), fill=DRU_VINE)
        draw.polygon([(staff_x - 3, head_cy - 12), (staff_x, head_cy - 18),
                      (staff_x + 3, head_cy - 12)], fill=DRU_LEAF, outline=OUTLINE)

        # Robe
        draw.polygon([
            (cx - 14, body_cy - 10),
            (cx + 14, body_cy - 10),
            (cx + 16 + robe_sway, base_y + 2),
            (cx - 16 + robe_sway, base_y + 2),
        ], fill=DRU_ROBE, outline=OUTLINE)
        draw.line([(cx, body_cy - 6), (cx + robe_sway, base_y)],
                  fill=DRU_ROBE_DARK, width=1)
        draw.line([(cx - 8, body_cy - 4), (cx - 10 + robe_sway, base_y)],
                  fill=DRU_ROBE_DARK, width=1)
        draw.line([(cx + 8, body_cy - 4), (cx + 10 + robe_sway, base_y)],
                  fill=DRU_ROBE_DARK, width=1)
        draw.rectangle([cx - 14, body_cy + 8, cx + 14, body_cy + 12],
                       fill=DRU_VINE, outline=OUTLINE)

        # Arms
        draw.rectangle([cx - 18, body_cy - 6, cx - 12, body_cy + 6],
                       fill=DRU_ROBE, outline=OUTLINE)
        draw.rectangle([cx + 12, body_cy - 6, cx + 18, body_cy + 6],
                       fill=DRU_ROBE, outline=OUTLINE)
        ellipse(draw, cx - 14, body_cy - 6, 5, 3, DRU_ROBE_LIGHT)
        ellipse(draw, cx + 14, body_cy - 6, 5, 3, DRU_ROBE_LIGHT)

        # Head (back)
        ellipse(draw, cx, head_cy, 14, 13, DRU_HAIR)
        ellipse(draw, cx, head_cy - 2, 10, 8, _darken(DRU_HAIR, 0.85), outline=None)
        # Antlers from back
        draw.line([(cx - 8, head_cy - 10), (cx - 12, head_cy - 22)],
                  fill=DRU_ANTLER, width=2)
        draw.line([(cx - 10, head_cy - 16), (cx - 14, head_cy - 20)],
                  fill=DRU_ANTLER, width=1)
        draw.line([(cx + 8, head_cy - 10), (cx + 12, head_cy - 22)],
                  fill=DRU_ANTLER, width=2)
        draw.line([(cx + 10, head_cy - 16), (cx + 14, head_cy - 20)],
                  fill=DRU_ANTLER, width=1)

    elif direction == LEFT:
        # Staff in front
        staff_x = cx - 14
        draw.line([(staff_x, head_cy - 8), (staff_x, base_y + 2)],
                  fill=DRU_STAFF, width=2)
        for vy in range(head_cy - 6, base_y, 6):
            draw.point((staff_x + 1, vy), fill=DRU_VINE)
        draw.polygon([(staff_x - 3, head_cy - 12), (staff_x, head_cy - 18),
                      (staff_x + 3, head_cy - 12)], fill=DRU_LEAF, outline=OUTLINE)

        # Robe
        draw.polygon([
            (cx - 10, body_cy - 10),
            (cx + 8, body_cy - 10),
            (cx + 12 + robe_sway, base_y + 2),
            (cx - 14 + robe_sway, base_y + 2),
        ], fill=DRU_ROBE, outline=OUTLINE)
        draw.line([(cx - 2, body_cy - 6), (cx - 4 + robe_sway, base_y)],
                  fill=DRU_ROBE_DARK, width=1)
        draw.line([(cx - 8, body_cy - 4), (cx - 10 + robe_sway, base_y)],
                  fill=DRU_ROBE_LIGHT, width=1)
        draw.point((cx - 2, body_cy + 2), fill=DRU_LEAF)
        draw.point((cx + 4, body_cy - 2), fill=DRU_LEAF)
        draw.rectangle([cx - 12, body_cy + 8, cx + 10, body_cy + 12],
                       fill=DRU_VINE, outline=OUTLINE)

        # Arm
        draw.rectangle([cx - 14, body_cy - 4, cx - 8, body_cy + 6],
                       fill=DRU_ROBE, outline=OUTLINE)
        draw.rectangle([cx - 14, body_cy + 2, cx - 8, body_cy + 6],
                       fill=DRU_SKIN, outline=OUTLINE)
        ellipse(draw, cx - 10, body_cy - 6, 5, 3, DRU_ROBE_LIGHT)

        # Head
        ellipse(draw, cx - 2, head_cy, 13, 13, DRU_HAIR)
        ellipse(draw, cx - 4, head_cy + 2, 10, 9, DRU_SKIN)
        ellipse(draw, cx - 2, head_cy + 4, 6, 5, DRU_SKIN_DARK, outline=None)
        draw.rectangle([cx - 10, head_cy + 1, cx - 7, head_cy + 5], fill=(255, 255, 255))
        draw.rectangle([cx - 9, head_cy + 2, cx - 8, head_cy + 4], fill=(60, 120, 50))
        draw.point((cx - 9, head_cy + 6), fill=DRU_SKIN_DARK)
        # Antler (left side visible)
        draw.line([(cx - 8, head_cy - 10), (cx - 14, head_cy - 22)],
                  fill=DRU_ANTLER, width=2)
        draw.line([(cx - 10, head_cy - 16), (cx - 16, head_cy - 20)],
                  fill=DRU_ANTLER, width=1)
        draw.point((cx - 14, head_cy - 22), fill=DRU_ANTLER_LIGHT)

    else:  # RIGHT
        # Staff in front
        staff_x = cx + 14
        draw.line([(staff_x, head_cy - 8), (staff_x, base_y + 2)],
                  fill=DRU_STAFF, width=2)
        for vy in range(head_cy - 6, base_y, 6):
            draw.point((staff_x - 1, vy), fill=DRU_VINE)
        draw.polygon([(staff_x - 3, head_cy - 12), (staff_x, head_cy - 18),
                      (staff_x + 3, head_cy - 12)], fill=DRU_LEAF, outline=OUTLINE)

        # Robe
        draw.polygon([
            (cx - 8, body_cy - 10),
            (cx + 10, body_cy - 10),
            (cx + 14 + robe_sway, base_y + 2),
            (cx - 12 + robe_sway, base_y + 2),
        ], fill=DRU_ROBE, outline=OUTLINE)
        draw.line([(cx + 2, body_cy - 6), (cx + 4 + robe_sway, base_y)],
                  fill=DRU_ROBE_DARK, width=1)
        draw.line([(cx + 8, body_cy - 4), (cx + 10 + robe_sway, base_y)],
                  fill=DRU_ROBE_LIGHT, width=1)
        draw.point((cx + 2, body_cy + 2), fill=DRU_LEAF)
        draw.point((cx - 4, body_cy - 2), fill=DRU_LEAF)
        draw.rectangle([cx - 10, body_cy + 8, cx + 12, body_cy + 12],
                       fill=DRU_VINE, outline=OUTLINE)

        # Arm
        draw.rectangle([cx + 8, body_cy - 4, cx + 14, body_cy + 6],
                       fill=DRU_ROBE, outline=OUTLINE)
        draw.rectangle([cx + 8, body_cy + 2, cx + 14, body_cy + 6],
                       fill=DRU_SKIN, outline=OUTLINE)
        ellipse(draw, cx + 10, body_cy - 6, 5, 3, DRU_ROBE_LIGHT)

        # Head
        ellipse(draw, cx + 2, head_cy, 13, 13, DRU_HAIR)
        ellipse(draw, cx + 4, head_cy + 2, 10, 9, DRU_SKIN)
        ellipse(draw, cx + 6, head_cy + 4, 6, 5, DRU_SKIN_DARK, outline=None)
        draw.rectangle([cx + 7, head_cy + 1, cx + 10, head_cy + 5], fill=(255, 255, 255))
        draw.rectangle([cx + 8, head_cy + 2, cx + 9, head_cy + 4], fill=(60, 120, 50))
        draw.point((cx + 9, head_cy + 6), fill=DRU_SKIN_DARK)
        # Antler (right side visible)
        draw.line([(cx + 8, head_cy - 10), (cx + 14, head_cy - 22)],
                  fill=DRU_ANTLER, width=2)
        draw.line([(cx + 10, head_cy - 16), (cx + 16, head_cy - 20)],
                  fill=DRU_ANTLER, width=1)
        draw.point((cx + 14, head_cy - 22), fill=DRU_ANTLER_LIGHT)


# ===================================================================
# BARD (ID 47) -- Colorful outfit, lute on back, feathered cap,
#                  flowing cape, floating music notes
# ===================================================================

def draw_bard(draw, ox, oy, direction, frame):
    bob = [0, -2, 0, -1][frame]
    leg_spread = [-4, 0, 4, 0][frame]
    note_float = [0, -2, -3, -1][frame]

    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 20
    head_cy = body_cy - 20

    if direction == DOWN:
        # Cape behind
        cape_sway = [0, 2, 0, -2][frame]
        draw.polygon([
            (cx - 12, body_cy - 6),
            (cx + 12, body_cy - 6),
            (cx + 14 + cape_sway, base_y - 2),
            (cx - 14 + cape_sway, base_y - 2),
        ], fill=BRD_CAPE, outline=OUTLINE)
        draw.line([(cx - 4, body_cy), (cx - 6 + cape_sway, base_y - 4)],
                  fill=BRD_CAPE_DARK, width=1)
        draw.line([(cx + 4, body_cy), (cx + 6 + cape_sway, base_y - 4)],
                  fill=BRD_CAPE_DARK, width=1)

        # Lute on back (visible at side)
        ellipse(draw, cx + 14, body_cy - 2, 5, 8, BRD_LUTE)
        ellipse(draw, cx + 14, body_cy - 2, 3, 5, BRD_LUTE_DARK, outline=None)
        draw.rectangle([cx + 13, body_cy - 12, cx + 15, body_cy - 6],
                       fill=BRD_LUTE_DARK, outline=OUTLINE)
        # Strings
        draw.line([(cx + 14, body_cy - 6), (cx + 14, body_cy + 4)],
                  fill=BRD_LUTE_STRING, width=1)

        # Legs
        draw.rectangle([cx - 9 + leg_spread, body_cy + 10,
                        cx - 4 + leg_spread, base_y], fill=BRD_LEG, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, body_cy + 10,
                        cx + 9 - leg_spread, base_y], fill=BRD_LEG, outline=OUTLINE)
        draw.rectangle([cx - 9 + leg_spread, base_y - 5,
                        cx - 4 + leg_spread, base_y], fill=BRD_BOOT, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, base_y - 5,
                        cx + 9 - leg_spread, base_y], fill=BRD_BOOT, outline=OUTLINE)

        # Body
        ellipse(draw, cx, body_cy, 13, 11, BRD_OUTFIT)
        ellipse(draw, cx - 3, body_cy - 2, 7, 6, BRD_OUTFIT_LIGHT, outline=None)
        # V-neck with accent
        draw.line([(cx, body_cy - 8), (cx - 4, body_cy + 2)],
                  fill=BRD_ACCENT, width=1)
        draw.line([(cx, body_cy - 8), (cx + 4, body_cy + 2)],
                  fill=BRD_ACCENT, width=1)
        # Gold belt
        draw.rectangle([cx - 13, body_cy + 8, cx + 13, body_cy + 13],
                       fill=BRD_ACCENT, outline=OUTLINE)
        draw.rectangle([cx - 2, body_cy + 9, cx + 2, body_cy + 12],
                       fill=BRD_ACCENT_LIGHT, outline=OUTLINE)

        # Arms
        draw.rectangle([cx - 17, body_cy - 5, cx - 12, body_cy + 5],
                       fill=BRD_OUTFIT, outline=OUTLINE)
        draw.rectangle([cx - 17, body_cy + 1, cx - 12, body_cy + 5],
                       fill=BRD_SKIN, outline=OUTLINE)
        draw.rectangle([cx + 12, body_cy - 5, cx + 17, body_cy + 5],
                       fill=BRD_OUTFIT, outline=OUTLINE)
        draw.rectangle([cx + 12, body_cy + 1, cx + 17, body_cy + 5],
                       fill=BRD_SKIN, outline=OUTLINE)
        ellipse(draw, cx - 14, body_cy - 5, 5, 3, BRD_ACCENT)
        ellipse(draw, cx + 14, body_cy - 5, 5, 3, BRD_ACCENT)

        # Floating music notes
        draw.point((cx - 16, head_cy - 4 + note_float), fill=BRD_NOTE)
        draw.point((cx - 17, head_cy - 5 + note_float), fill=BRD_NOTE)
        draw.line([(cx - 16, head_cy - 4 + note_float), (cx - 16, head_cy - 8 + note_float)],
                  fill=BRD_NOTE, width=1)
        draw.point((cx + 18, head_cy - 8 + note_float), fill=BRD_NOTE)
        draw.point((cx + 17, head_cy - 9 + note_float), fill=BRD_NOTE)
        draw.line([(cx + 18, head_cy - 8 + note_float), (cx + 18, head_cy - 12 + note_float)],
                  fill=BRD_NOTE, width=1)

        # Head
        ellipse(draw, cx, head_cy, 13, 12, BRD_SKIN)
        ellipse(draw, cx + 2, head_cy + 3, 8, 6, BRD_SKIN_DARK, outline=None)
        draw.rectangle([cx - 6, head_cy + 1, cx - 3, head_cy + 4], fill=(255, 255, 255))
        draw.rectangle([cx - 5, head_cy + 2, cx - 4, head_cy + 3], fill=BLACK)
        draw.rectangle([cx + 3, head_cy + 1, cx + 6, head_cy + 4], fill=(255, 255, 255))
        draw.rectangle([cx + 4, head_cy + 2, cx + 5, head_cy + 3], fill=BLACK)
        draw.point((cx, head_cy + 5), fill=BRD_SKIN_DARK)
        draw.line([(cx - 2, head_cy + 7), (cx + 2, head_cy + 7)],
                  fill=BRD_SKIN_DARK, width=1)
        # Feathered cap
        ellipse(draw, cx, head_cy - 6, 14, 5, BRD_HAT)
        ellipse(draw, cx + 4, head_cy - 5, 8, 3, BRD_HAT_DARK, outline=None)
        # Feather plume
        draw.line([(cx + 10, head_cy - 8), (cx + 18, head_cy - 20)],
                  fill=BRD_FEATHER, width=3)
        draw.line([(cx + 14, head_cy - 14), (cx + 18, head_cy - 16)],
                  fill=BRD_ACCENT_DARK, width=1)
        draw.point((cx + 18, head_cy - 20), fill=BRD_ACCENT_LIGHT)

    elif direction == UP:
        cape_sway = [0, 2, 0, -2][frame]
        draw.polygon([
            (cx - 12, body_cy - 8),
            (cx + 12, body_cy - 8),
            (cx + 16 + cape_sway, base_y),
            (cx - 16 + cape_sway, base_y),
        ], fill=BRD_CAPE, outline=OUTLINE)
        draw.line([(cx, body_cy - 2), (cx + cape_sway, base_y - 4)],
                  fill=BRD_CAPE_DARK, width=1)

        # Lute on back (fully visible)
        ellipse(draw, cx, body_cy - 4, 7, 10, BRD_LUTE)
        ellipse(draw, cx, body_cy - 4, 4, 6, BRD_LUTE_DARK, outline=None)
        draw.rectangle([cx - 1, body_cy - 16, cx + 1, body_cy - 8],
                       fill=BRD_LUTE_DARK, outline=OUTLINE)
        # Tuning pegs
        draw.point((cx - 2, body_cy - 14), fill=BRD_ACCENT)
        draw.point((cx + 2, body_cy - 14), fill=BRD_ACCENT)

        # Legs
        draw.rectangle([cx - 9 + leg_spread, body_cy + 10,
                        cx - 4 + leg_spread, base_y], fill=BRD_LEG, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, body_cy + 10,
                        cx + 9 - leg_spread, base_y], fill=BRD_LEG, outline=OUTLINE)
        draw.rectangle([cx - 9 + leg_spread, base_y - 5,
                        cx - 4 + leg_spread, base_y], fill=BRD_BOOT, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, base_y - 5,
                        cx + 9 - leg_spread, base_y], fill=BRD_BOOT, outline=OUTLINE)

        # Body (back)
        ellipse(draw, cx, body_cy, 13, 11, BRD_OUTFIT)
        ellipse(draw, cx, body_cy, 9, 8, BRD_OUTFIT_DARK, outline=None)
        draw.rectangle([cx - 13, body_cy + 8, cx + 13, body_cy + 13],
                       fill=BRD_ACCENT, outline=OUTLINE)

        # Arms
        draw.rectangle([cx - 17, body_cy - 5, cx - 12, body_cy + 5],
                       fill=BRD_OUTFIT, outline=OUTLINE)
        draw.rectangle([cx + 12, body_cy - 5, cx + 17, body_cy + 5],
                       fill=BRD_OUTFIT, outline=OUTLINE)
        ellipse(draw, cx - 14, body_cy - 5, 5, 3, BRD_ACCENT)
        ellipse(draw, cx + 14, body_cy - 5, 5, 3, BRD_ACCENT)

        # Head (back)
        ellipse(draw, cx, head_cy, 13, 12, BRD_SKIN)
        ellipse(draw, cx, head_cy - 2, 9, 8, BRD_SKIN_DARK, outline=None)
        # Feathered cap from back
        ellipse(draw, cx, head_cy - 6, 14, 5, BRD_HAT)
        ellipse(draw, cx, head_cy - 5, 10, 4, BRD_HAT_DARK, outline=None)
        draw.line([(cx + 10, head_cy - 8), (cx + 18, head_cy - 20)],
                  fill=BRD_FEATHER, width=3)
        draw.point((cx + 18, head_cy - 20), fill=BRD_ACCENT_LIGHT)

    elif direction == LEFT:
        # Cape
        cape_sway = [0, 2, 0, -2][frame]
        draw.polygon([
            (cx + 4, body_cy - 8),
            (cx + 14, body_cy - 4),
            (cx + 16 + cape_sway, base_y),
            (cx + 4, base_y),
        ], fill=BRD_CAPE, outline=OUTLINE)
        draw.line([(cx + 8, body_cy - 2), (cx + 10 + cape_sway, base_y - 4)],
                  fill=BRD_CAPE_DARK, width=1)

        # Lute on back
        ellipse(draw, cx + 8, body_cy - 2, 4, 6, BRD_LUTE)
        draw.rectangle([cx + 7, body_cy - 10, cx + 9, body_cy - 4],
                       fill=BRD_LUTE_DARK, outline=OUTLINE)

        # Legs
        draw.rectangle([cx - 2 - leg_spread, body_cy + 10,
                        cx + 4 - leg_spread, base_y],
                       fill=_darken(BRD_LEG, 0.85), outline=OUTLINE)
        draw.rectangle([cx - 2 - leg_spread, base_y - 5,
                        cx + 4 - leg_spread, base_y], fill=BRD_BOOT, outline=OUTLINE)
        draw.rectangle([cx - 8 + leg_spread, body_cy + 10,
                        cx - 2 + leg_spread, base_y], fill=BRD_LEG, outline=OUTLINE)
        draw.rectangle([cx - 8 + leg_spread, base_y - 5,
                        cx - 2 + leg_spread, base_y], fill=BRD_BOOT, outline=OUTLINE)

        # Body
        ellipse(draw, cx - 2, body_cy, 11, 11, BRD_OUTFIT)
        ellipse(draw, cx - 4, body_cy - 2, 6, 6, BRD_OUTFIT_LIGHT, outline=None)
        draw.rectangle([cx - 13, body_cy + 8, cx + 9, body_cy + 13],
                       fill=BRD_ACCENT, outline=OUTLINE)

        # Arm
        draw.rectangle([cx - 14, body_cy - 4, cx - 8, body_cy + 5],
                       fill=BRD_OUTFIT, outline=OUTLINE)
        draw.rectangle([cx - 14, body_cy + 1, cx - 8, body_cy + 5],
                       fill=BRD_SKIN, outline=OUTLINE)
        ellipse(draw, cx - 10, body_cy - 5, 5, 3, BRD_ACCENT)

        # Music note
        draw.point((cx - 16, head_cy - 6 + note_float), fill=BRD_NOTE)
        draw.point((cx - 17, head_cy - 7 + note_float), fill=BRD_NOTE)
        draw.line([(cx - 16, head_cy - 6 + note_float), (cx - 16, head_cy - 10 + note_float)],
                  fill=BRD_NOTE, width=1)

        # Head
        ellipse(draw, cx - 2, head_cy, 12, 12, BRD_SKIN)
        ellipse(draw, cx - 4, head_cy + 2, 8, 7, BRD_SKIN)
        ellipse(draw, cx - 2, head_cy + 4, 5, 4, BRD_SKIN_DARK, outline=None)
        draw.rectangle([cx - 10, head_cy + 1, cx - 7, head_cy + 4], fill=(255, 255, 255))
        draw.rectangle([cx - 9, head_cy + 2, cx - 8, head_cy + 3], fill=BLACK)
        draw.point((cx - 9, head_cy + 5), fill=BRD_SKIN_DARK)
        # Feathered cap
        ellipse(draw, cx - 2, head_cy - 6, 12, 5, BRD_HAT)
        ellipse(draw, cx + 2, head_cy - 5, 6, 3, BRD_HAT_DARK, outline=None)
        draw.line([(cx + 6, head_cy - 8), (cx + 14, head_cy - 20)],
                  fill=BRD_FEATHER, width=3)
        draw.point((cx + 14, head_cy - 20), fill=BRD_ACCENT_LIGHT)

    else:  # RIGHT
        cape_sway = [0, -2, 0, 2][frame]
        draw.polygon([
            (cx - 4, body_cy - 8),
            (cx - 14, body_cy - 4),
            (cx - 16 + cape_sway, base_y),
            (cx - 4, base_y),
        ], fill=BRD_CAPE, outline=OUTLINE)
        draw.line([(cx - 8, body_cy - 2), (cx - 10 + cape_sway, base_y - 4)],
                  fill=BRD_CAPE_DARK, width=1)

        # Lute on back
        ellipse(draw, cx - 8, body_cy - 2, 4, 6, BRD_LUTE)
        draw.rectangle([cx - 9, body_cy - 10, cx - 7, body_cy - 4],
                       fill=BRD_LUTE_DARK, outline=OUTLINE)

        # Legs
        draw.rectangle([cx - 2 + leg_spread, body_cy + 10,
                        cx + 4 + leg_spread, base_y],
                       fill=_darken(BRD_LEG, 0.85), outline=OUTLINE)
        draw.rectangle([cx - 2 + leg_spread, base_y - 5,
                        cx + 4 + leg_spread, base_y], fill=BRD_BOOT, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, body_cy + 10,
                        cx + 10 - leg_spread, base_y], fill=BRD_LEG, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, base_y - 5,
                        cx + 10 - leg_spread, base_y], fill=BRD_BOOT, outline=OUTLINE)

        # Body
        ellipse(draw, cx + 2, body_cy, 11, 11, BRD_OUTFIT)
        ellipse(draw, cx, body_cy - 2, 6, 6, BRD_OUTFIT_LIGHT, outline=None)
        draw.rectangle([cx - 9, body_cy + 8, cx + 13, body_cy + 13],
                       fill=BRD_ACCENT, outline=OUTLINE)

        # Arm
        draw.rectangle([cx + 8, body_cy - 4, cx + 14, body_cy + 5],
                       fill=BRD_OUTFIT, outline=OUTLINE)
        draw.rectangle([cx + 8, body_cy + 1, cx + 14, body_cy + 5],
                       fill=BRD_SKIN, outline=OUTLINE)
        ellipse(draw, cx + 10, body_cy - 5, 5, 3, BRD_ACCENT)

        # Music note
        draw.point((cx + 16, head_cy - 6 + note_float), fill=BRD_NOTE)
        draw.point((cx + 15, head_cy - 7 + note_float), fill=BRD_NOTE)
        draw.line([(cx + 16, head_cy - 6 + note_float), (cx + 16, head_cy - 10 + note_float)],
                  fill=BRD_NOTE, width=1)

        # Head
        ellipse(draw, cx + 2, head_cy, 12, 12, BRD_SKIN)
        ellipse(draw, cx + 4, head_cy + 2, 8, 7, BRD_SKIN)
        ellipse(draw, cx + 6, head_cy + 4, 5, 4, BRD_SKIN_DARK, outline=None)
        draw.rectangle([cx + 7, head_cy + 1, cx + 10, head_cy + 4], fill=(255, 255, 255))
        draw.rectangle([cx + 8, head_cy + 2, cx + 9, head_cy + 3], fill=BLACK)
        draw.point((cx + 9, head_cy + 5), fill=BRD_SKIN_DARK)
        # Feathered cap
        ellipse(draw, cx + 2, head_cy - 6, 12, 5, BRD_HAT)
        ellipse(draw, cx - 2, head_cy - 5, 6, 3, BRD_HAT_DARK, outline=None)
        draw.line([(cx - 6, head_cy - 8), (cx - 14, head_cy - 20)],
                  fill=BRD_FEATHER, width=3)
        draw.point((cx - 14, head_cy - 20), fill=BRD_ACCENT_LIGHT)


# ===================================================================
# MONK (ID 48) -- Orange/saffron robes, bare feet, prayer beads,
#                  bald head, wrapped hands
# ===================================================================

def draw_monk(draw, ox, oy, direction, frame):
    bob = [0, -2, 0, -1][frame]
    leg_spread = [-4, 0, 4, 0][frame]

    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 20
    head_cy = body_cy - 20

    if direction == DOWN:
        # Legs (bare below knee, wrapped feet)
        draw.rectangle([cx - 9 + leg_spread, body_cy + 10,
                        cx - 4 + leg_spread, base_y], fill=MNK_SKIN, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, body_cy + 10,
                        cx + 9 - leg_spread, base_y], fill=MNK_SKIN, outline=OUTLINE)
        # Bare feet (no boots, just skin tone)
        draw.rectangle([cx - 10 + leg_spread, base_y - 3,
                        cx - 3 + leg_spread, base_y], fill=MNK_SKIN_DARK, outline=OUTLINE)
        draw.rectangle([cx + 3 - leg_spread, base_y - 3,
                        cx + 10 - leg_spread, base_y], fill=MNK_SKIN_DARK, outline=OUTLINE)

        # Robe body
        ellipse(draw, cx, body_cy, 14, 12, MNK_ROBE)
        ellipse(draw, cx - 3, body_cy - 2, 8, 7, MNK_ROBE_LIGHT, outline=None)
        # Robe wrap line
        draw.line([(cx - 8, body_cy - 8), (cx + 6, body_cy + 4)],
                  fill=MNK_ROBE_DARK, width=1)
        # Sash
        draw.rectangle([cx - 14, body_cy + 6, cx + 14, body_cy + 14],
                       fill=MNK_SASH, outline=OUTLINE)
        # Sash knot
        draw.rectangle([cx + 8, body_cy + 7, cx + 14, body_cy + 13],
                       fill=MNK_SASH_DARK, outline=None)
        # Sash tail hanging
        draw.line([(cx + 12, body_cy + 13), (cx + 14, body_cy + 18)],
                  fill=MNK_SASH, width=2)

        # Arms with wrapped hands
        draw.rectangle([cx - 17, body_cy - 5, cx - 12, body_cy + 5],
                       fill=MNK_ROBE, outline=OUTLINE)
        draw.rectangle([cx - 17, body_cy + 1, cx - 12, body_cy + 5],
                       fill=MNK_WRAP, outline=OUTLINE)
        draw.point((cx - 15, body_cy + 3), fill=MNK_WRAP_DARK)
        draw.rectangle([cx + 12, body_cy - 5, cx + 17, body_cy + 5],
                       fill=MNK_ROBE, outline=OUTLINE)
        draw.rectangle([cx + 12, body_cy + 1, cx + 17, body_cy + 5],
                       fill=MNK_WRAP, outline=OUTLINE)
        draw.point((cx + 15, body_cy + 3), fill=MNK_WRAP_DARK)
        ellipse(draw, cx - 14, body_cy - 5, 5, 3, MNK_ROBE_LIGHT)
        ellipse(draw, cx + 14, body_cy - 5, 5, 3, MNK_ROBE_LIGHT)

        # Head (bald)
        ellipse(draw, cx, head_cy, 14, 13, MNK_HEAD)
        ellipse(draw, cx - 3, head_cy - 4, 8, 5, MNK_SKIN_LIGHT, outline=None)
        ellipse(draw, cx + 2, head_cy + 4, 8, 6, MNK_SKIN_DARK, outline=None)
        # Shine on bald head
        draw.point((cx - 4, head_cy - 8), fill=MNK_SKIN_LIGHT)
        draw.point((cx - 3, head_cy - 9), fill=MNK_SKIN_LIGHT)
        # Eyes
        draw.rectangle([cx - 6, head_cy + 1, cx - 3, head_cy + 5], fill=(255, 255, 255))
        draw.rectangle([cx - 5, head_cy + 2, cx - 4, head_cy + 4], fill=BLACK)
        draw.rectangle([cx + 3, head_cy + 1, cx + 6, head_cy + 5], fill=(255, 255, 255))
        draw.rectangle([cx + 4, head_cy + 2, cx + 5, head_cy + 4], fill=BLACK)
        draw.point((cx, head_cy + 6), fill=MNK_SKIN_DARK)
        draw.line([(cx - 2, head_cy + 8), (cx + 2, head_cy + 8)],
                  fill=MNK_SKIN_DARK, width=1)
        # Prayer beads around neck
        for bx in range(-8, 10, 3):
            draw.point((cx + bx, body_cy - 10), fill=MNK_BEADS)
            if bx % 6 == 0:
                draw.point((cx + bx, body_cy - 10), fill=MNK_BEAD_HIGHLIGHT)

    elif direction == UP:
        # Legs
        draw.rectangle([cx - 9 + leg_spread, body_cy + 10,
                        cx - 4 + leg_spread, base_y], fill=MNK_SKIN, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, body_cy + 10,
                        cx + 9 - leg_spread, base_y], fill=MNK_SKIN, outline=OUTLINE)
        draw.rectangle([cx - 10 + leg_spread, base_y - 3,
                        cx - 3 + leg_spread, base_y], fill=MNK_SKIN_DARK, outline=OUTLINE)
        draw.rectangle([cx + 3 - leg_spread, base_y - 3,
                        cx + 10 - leg_spread, base_y], fill=MNK_SKIN_DARK, outline=OUTLINE)

        # Body (back)
        ellipse(draw, cx, body_cy, 14, 12, MNK_ROBE)
        ellipse(draw, cx, body_cy, 10, 9, MNK_ROBE_DARK, outline=None)
        draw.line([(cx, body_cy - 6), (cx, body_cy + 6)],
                  fill=_darken(MNK_ROBE, 0.7), width=1)
        draw.rectangle([cx - 14, body_cy + 6, cx + 14, body_cy + 14],
                       fill=MNK_SASH, outline=OUTLINE)

        # Arms
        draw.rectangle([cx - 17, body_cy - 5, cx - 12, body_cy + 5],
                       fill=MNK_ROBE, outline=OUTLINE)
        draw.rectangle([cx + 12, body_cy - 5, cx + 17, body_cy + 5],
                       fill=MNK_ROBE, outline=OUTLINE)
        ellipse(draw, cx - 14, body_cy - 5, 5, 3, MNK_ROBE_LIGHT)
        ellipse(draw, cx + 14, body_cy - 5, 5, 3, MNK_ROBE_LIGHT)

        # Head (back, bald)
        ellipse(draw, cx, head_cy, 14, 13, MNK_HEAD)
        ellipse(draw, cx, head_cy - 2, 10, 8, MNK_SKIN_DARK, outline=None)
        draw.point((cx - 4, head_cy - 8), fill=MNK_SKIN_LIGHT)
        draw.point((cx - 3, head_cy - 9), fill=MNK_SKIN_LIGHT)

    elif direction == LEFT:
        # Legs
        draw.rectangle([cx - 2 - leg_spread, body_cy + 10,
                        cx + 4 - leg_spread, base_y],
                       fill=_darken(MNK_SKIN, 0.85), outline=OUTLINE)
        draw.rectangle([cx - 2 - leg_spread, base_y - 3,
                        cx + 5 - leg_spread, base_y], fill=MNK_SKIN_DARK, outline=OUTLINE)
        draw.rectangle([cx - 8 + leg_spread, body_cy + 10,
                        cx - 2 + leg_spread, base_y], fill=MNK_SKIN, outline=OUTLINE)
        draw.rectangle([cx - 9 + leg_spread, base_y - 3,
                        cx - 1 + leg_spread, base_y], fill=MNK_SKIN_DARK, outline=OUTLINE)

        # Body
        ellipse(draw, cx - 2, body_cy, 12, 12, MNK_ROBE)
        ellipse(draw, cx - 4, body_cy - 2, 7, 7, MNK_ROBE_LIGHT, outline=None)
        draw.rectangle([cx - 14, body_cy + 6, cx + 10, body_cy + 14],
                       fill=MNK_SASH, outline=OUTLINE)
        # Sash tail
        draw.line([(cx + 8, body_cy + 13), (cx + 10, body_cy + 18)],
                  fill=MNK_SASH, width=2)

        # Arm with wrapped hand
        draw.rectangle([cx - 14, body_cy - 4, cx - 8, body_cy + 5],
                       fill=MNK_ROBE, outline=OUTLINE)
        draw.rectangle([cx - 14, body_cy + 1, cx - 8, body_cy + 5],
                       fill=MNK_WRAP, outline=OUTLINE)
        ellipse(draw, cx - 10, body_cy - 5, 5, 3, MNK_ROBE_LIGHT)

        # Head (bald)
        ellipse(draw, cx - 2, head_cy, 13, 13, MNK_HEAD)
        ellipse(draw, cx - 5, head_cy - 4, 6, 4, MNK_SKIN_LIGHT, outline=None)
        ellipse(draw, cx - 4, head_cy + 2, 8, 7, MNK_SKIN)
        ellipse(draw, cx - 2, head_cy + 4, 5, 4, MNK_SKIN_DARK, outline=None)
        draw.point((cx - 6, head_cy - 8), fill=MNK_SKIN_LIGHT)
        draw.rectangle([cx - 10, head_cy + 1, cx - 7, head_cy + 5], fill=(255, 255, 255))
        draw.rectangle([cx - 9, head_cy + 2, cx - 8, head_cy + 4], fill=BLACK)
        draw.point((cx - 9, head_cy + 6), fill=MNK_SKIN_DARK)
        # Beads
        for by_off in range(-8, 4, 3):
            draw.point((cx - 4, body_cy + by_off - 2), fill=MNK_BEADS)

    else:  # RIGHT
        # Legs
        draw.rectangle([cx - 2 + leg_spread, body_cy + 10,
                        cx + 4 + leg_spread, base_y],
                       fill=_darken(MNK_SKIN, 0.85), outline=OUTLINE)
        draw.rectangle([cx - 3 + leg_spread, base_y - 3,
                        cx + 5 + leg_spread, base_y], fill=MNK_SKIN_DARK, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, body_cy + 10,
                        cx + 10 - leg_spread, base_y], fill=MNK_SKIN, outline=OUTLINE)
        draw.rectangle([cx + 3 - leg_spread, base_y - 3,
                        cx + 11 - leg_spread, base_y], fill=MNK_SKIN_DARK, outline=OUTLINE)

        # Body
        ellipse(draw, cx + 2, body_cy, 12, 12, MNK_ROBE)
        ellipse(draw, cx, body_cy - 2, 7, 7, MNK_ROBE_LIGHT, outline=None)
        draw.rectangle([cx - 10, body_cy + 6, cx + 14, body_cy + 14],
                       fill=MNK_SASH, outline=OUTLINE)
        draw.line([(cx - 8, body_cy + 13), (cx - 10, body_cy + 18)],
                  fill=MNK_SASH, width=2)

        # Arm
        draw.rectangle([cx + 8, body_cy - 4, cx + 14, body_cy + 5],
                       fill=MNK_ROBE, outline=OUTLINE)
        draw.rectangle([cx + 8, body_cy + 1, cx + 14, body_cy + 5],
                       fill=MNK_WRAP, outline=OUTLINE)
        ellipse(draw, cx + 10, body_cy - 5, 5, 3, MNK_ROBE_LIGHT)

        # Head (bald)
        ellipse(draw, cx + 2, head_cy, 13, 13, MNK_HEAD)
        ellipse(draw, cx + 5, head_cy - 4, 6, 4, MNK_SKIN_LIGHT, outline=None)
        ellipse(draw, cx + 4, head_cy + 2, 8, 7, MNK_SKIN)
        ellipse(draw, cx + 6, head_cy + 4, 5, 4, MNK_SKIN_DARK, outline=None)
        draw.point((cx + 6, head_cy - 8), fill=MNK_SKIN_LIGHT)
        draw.rectangle([cx + 7, head_cy + 1, cx + 10, head_cy + 5], fill=(255, 255, 255))
        draw.rectangle([cx + 8, head_cy + 2, cx + 9, head_cy + 4], fill=BLACK)
        draw.point((cx + 9, head_cy + 6), fill=MNK_SKIN_DARK)
        # Beads
        for by_off in range(-8, 4, 3):
            draw.point((cx + 4, body_cy + by_off - 2), fill=MNK_BEADS)


# ===================================================================
# CLERIC (ID 49) -- White/gold robes, holy symbol necklace,
#                    halo, healing staff
# ===================================================================

def draw_cleric(draw, ox, oy, direction, frame):
    bob = [0, -2, 0, -1][frame]
    leg_spread = [-4, 0, 4, 0][frame]
    halo_pulse = [0, 1, 0, -1][frame]

    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 20
    head_cy = body_cy - 20

    if direction == DOWN:
        # Healing staff behind
        staff_x = cx + 16
        draw.line([(staff_x, head_cy - 8), (staff_x, base_y + 2)],
                  fill=CLR_STAFF, width=2)
        draw.line([(staff_x - 1, head_cy - 10), (staff_x - 1, base_y)],
                  fill=CLR_STAFF_DARK, width=1)
        # Staff head (cross shape)
        draw.rectangle([staff_x - 3, head_cy - 16, staff_x + 3, head_cy - 10],
                       fill=CLR_GOLD, outline=OUTLINE)
        draw.rectangle([staff_x - 5, head_cy - 14, staff_x + 5, head_cy - 12],
                       fill=CLR_GOLD, outline=OUTLINE)
        draw.point((staff_x, head_cy - 13), fill=CLR_GOLD_LIGHT)

        # Legs
        draw.rectangle([cx - 9 + leg_spread, body_cy + 10,
                        cx - 4 + leg_spread, base_y], fill=CLR_LEG, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, body_cy + 10,
                        cx + 9 - leg_spread, base_y], fill=CLR_LEG, outline=OUTLINE)
        draw.rectangle([cx - 9 + leg_spread, base_y - 5,
                        cx - 4 + leg_spread, base_y], fill=CLR_BOOT, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, base_y - 5,
                        cx + 9 - leg_spread, base_y], fill=CLR_BOOT, outline=OUTLINE)

        # Robe body
        ellipse(draw, cx, body_cy, 14, 12, CLR_ROBE)
        ellipse(draw, cx - 3, body_cy - 2, 8, 7, CLR_ROBE_LIGHT, outline=None)
        # Gold trim on V-neck
        draw.line([(cx, body_cy - 8), (cx - 4, body_cy + 2)],
                  fill=CLR_GOLD, width=1)
        draw.line([(cx, body_cy - 8), (cx + 4, body_cy + 2)],
                  fill=CLR_GOLD, width=1)
        # Holy symbol necklace
        draw.point((cx, body_cy - 4), fill=CLR_SYMBOL)
        draw.point((cx - 1, body_cy - 3), fill=CLR_SYMBOL)
        draw.point((cx + 1, body_cy - 3), fill=CLR_SYMBOL)
        draw.point((cx, body_cy - 2), fill=CLR_SYMBOL)
        # Gold belt
        draw.rectangle([cx - 14, body_cy + 8, cx + 14, body_cy + 13],
                       fill=CLR_GOLD, outline=OUTLINE)
        draw.rectangle([cx - 3, body_cy + 9, cx + 3, body_cy + 12],
                       fill=CLR_GOLD_LIGHT, outline=OUTLINE)

        # Arms
        draw.rectangle([cx - 17, body_cy - 5, cx - 12, body_cy + 5],
                       fill=CLR_ROBE, outline=OUTLINE)
        draw.rectangle([cx - 17, body_cy + 1, cx - 12, body_cy + 5],
                       fill=CLR_SKIN, outline=OUTLINE)
        draw.rectangle([cx + 12, body_cy - 5, cx + 17, body_cy + 5],
                       fill=CLR_ROBE, outline=OUTLINE)
        draw.rectangle([cx + 12, body_cy + 1, cx + 17, body_cy + 5],
                       fill=CLR_SKIN, outline=OUTLINE)
        ellipse(draw, cx - 14, body_cy - 5, 5, 3, CLR_GOLD)
        ellipse(draw, cx + 14, body_cy - 5, 5, 3, CLR_GOLD)

        # Head
        ellipse(draw, cx, head_cy, 14, 13, CLR_HAIR)
        ellipse(draw, cx, head_cy + 2, 12, 10, CLR_SKIN)
        ellipse(draw, cx + 2, head_cy + 4, 8, 6, CLR_SKIN_DARK, outline=None)
        draw.rectangle([cx - 6, head_cy + 1, cx - 3, head_cy + 5], fill=(255, 255, 255))
        draw.rectangle([cx - 5, head_cy + 2, cx - 4, head_cy + 4], fill=(60, 100, 180))
        draw.rectangle([cx + 3, head_cy + 1, cx + 6, head_cy + 5], fill=(255, 255, 255))
        draw.rectangle([cx + 4, head_cy + 2, cx + 5, head_cy + 4], fill=(60, 100, 180))
        draw.point((cx, head_cy + 6), fill=CLR_SKIN_DARK)
        draw.line([(cx - 2, head_cy + 8), (cx + 2, head_cy + 8)],
                  fill=CLR_SKIN_DARK, width=1)
        # Halo
        draw.ellipse([cx - 14, head_cy - 24 + halo_pulse, cx + 14, head_cy - 16 + halo_pulse],
                     fill=None, outline=CLR_HALO, width=2)
        draw.ellipse([cx - 12, head_cy - 22 + halo_pulse, cx + 12, head_cy - 18 + halo_pulse],
                     fill=None, outline=CLR_HALO_LIGHT, width=1)
        draw.point((cx, head_cy - 24 + halo_pulse), fill=CLR_HALO_LIGHT)

    elif direction == UP:
        # Staff
        staff_x = cx - 16
        draw.line([(staff_x, head_cy - 8), (staff_x, base_y + 2)],
                  fill=CLR_STAFF, width=2)
        draw.rectangle([staff_x - 3, head_cy - 16, staff_x + 3, head_cy - 10],
                       fill=CLR_GOLD, outline=OUTLINE)
        draw.rectangle([staff_x - 5, head_cy - 14, staff_x + 5, head_cy - 12],
                       fill=CLR_GOLD, outline=OUTLINE)

        # Legs
        draw.rectangle([cx - 9 + leg_spread, body_cy + 10,
                        cx - 4 + leg_spread, base_y], fill=CLR_LEG, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, body_cy + 10,
                        cx + 9 - leg_spread, base_y], fill=CLR_LEG, outline=OUTLINE)
        draw.rectangle([cx - 9 + leg_spread, base_y - 5,
                        cx - 4 + leg_spread, base_y], fill=CLR_BOOT, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, base_y - 5,
                        cx + 9 - leg_spread, base_y], fill=CLR_BOOT, outline=OUTLINE)

        # Body (back)
        ellipse(draw, cx, body_cy, 14, 12, CLR_ROBE)
        ellipse(draw, cx, body_cy, 10, 9, CLR_ROBE_DARK, outline=None)
        draw.line([(cx, body_cy - 6), (cx, body_cy + 6)],
                  fill=_darken(CLR_ROBE, 0.85), width=1)
        draw.rectangle([cx - 14, body_cy + 8, cx + 14, body_cy + 13],
                       fill=CLR_GOLD, outline=OUTLINE)

        # Arms
        draw.rectangle([cx - 17, body_cy - 5, cx - 12, body_cy + 5],
                       fill=CLR_ROBE, outline=OUTLINE)
        draw.rectangle([cx + 12, body_cy - 5, cx + 17, body_cy + 5],
                       fill=CLR_ROBE, outline=OUTLINE)
        ellipse(draw, cx - 14, body_cy - 5, 5, 3, CLR_GOLD)
        ellipse(draw, cx + 14, body_cy - 5, 5, 3, CLR_GOLD)

        # Head (back)
        ellipse(draw, cx, head_cy, 14, 13, CLR_HAIR)
        ellipse(draw, cx, head_cy - 2, 10, 8, _darken(CLR_HAIR, 0.85), outline=None)
        # Halo
        draw.ellipse([cx - 14, head_cy - 24 + halo_pulse, cx + 14, head_cy - 16 + halo_pulse],
                     fill=None, outline=CLR_HALO, width=2)
        draw.ellipse([cx - 12, head_cy - 22 + halo_pulse, cx + 12, head_cy - 18 + halo_pulse],
                     fill=None, outline=CLR_HALO_LIGHT, width=1)

    elif direction == LEFT:
        # Staff
        staff_x = cx - 14
        draw.line([(staff_x, head_cy - 8), (staff_x, base_y + 2)],
                  fill=CLR_STAFF, width=2)
        draw.rectangle([staff_x - 3, head_cy - 16, staff_x + 3, head_cy - 10],
                       fill=CLR_GOLD, outline=OUTLINE)
        draw.rectangle([staff_x - 5, head_cy - 14, staff_x + 5, head_cy - 12],
                       fill=CLR_GOLD, outline=OUTLINE)

        # Legs
        draw.rectangle([cx - 2 - leg_spread, body_cy + 10,
                        cx + 4 - leg_spread, base_y],
                       fill=_darken(CLR_LEG, 0.85), outline=OUTLINE)
        draw.rectangle([cx - 2 - leg_spread, base_y - 5,
                        cx + 4 - leg_spread, base_y], fill=CLR_BOOT, outline=OUTLINE)
        draw.rectangle([cx - 8 + leg_spread, body_cy + 10,
                        cx - 2 + leg_spread, base_y], fill=CLR_LEG, outline=OUTLINE)
        draw.rectangle([cx - 8 + leg_spread, base_y - 5,
                        cx - 2 + leg_spread, base_y], fill=CLR_BOOT, outline=OUTLINE)

        # Body
        ellipse(draw, cx - 2, body_cy, 12, 12, CLR_ROBE)
        ellipse(draw, cx - 4, body_cy - 2, 7, 7, CLR_ROBE_LIGHT, outline=None)
        draw.rectangle([cx - 14, body_cy + 8, cx + 10, body_cy + 13],
                       fill=CLR_GOLD, outline=OUTLINE)

        # Arm
        draw.rectangle([cx - 14, body_cy - 4, cx - 8, body_cy + 5],
                       fill=CLR_ROBE, outline=OUTLINE)
        draw.rectangle([cx - 14, body_cy + 1, cx - 8, body_cy + 5],
                       fill=CLR_SKIN, outline=OUTLINE)
        ellipse(draw, cx - 10, body_cy - 5, 5, 3, CLR_GOLD)

        # Head
        ellipse(draw, cx - 2, head_cy, 13, 13, CLR_HAIR)
        ellipse(draw, cx - 4, head_cy + 2, 10, 9, CLR_SKIN)
        ellipse(draw, cx - 2, head_cy + 4, 6, 5, CLR_SKIN_DARK, outline=None)
        draw.rectangle([cx - 10, head_cy + 1, cx - 7, head_cy + 5], fill=(255, 255, 255))
        draw.rectangle([cx - 9, head_cy + 2, cx - 8, head_cy + 4], fill=(60, 100, 180))
        draw.point((cx - 9, head_cy + 6), fill=CLR_SKIN_DARK)
        # Halo
        draw.ellipse([cx - 14, head_cy - 24 + halo_pulse, cx + 8, head_cy - 16 + halo_pulse],
                     fill=None, outline=CLR_HALO, width=2)
        draw.point((cx - 6, head_cy - 22 + halo_pulse), fill=CLR_HALO_LIGHT)

    else:  # RIGHT
        # Staff
        staff_x = cx + 14
        draw.line([(staff_x, head_cy - 8), (staff_x, base_y + 2)],
                  fill=CLR_STAFF, width=2)
        draw.rectangle([staff_x - 3, head_cy - 16, staff_x + 3, head_cy - 10],
                       fill=CLR_GOLD, outline=OUTLINE)
        draw.rectangle([staff_x - 5, head_cy - 14, staff_x + 5, head_cy - 12],
                       fill=CLR_GOLD, outline=OUTLINE)

        # Legs
        draw.rectangle([cx - 2 + leg_spread, body_cy + 10,
                        cx + 4 + leg_spread, base_y],
                       fill=_darken(CLR_LEG, 0.85), outline=OUTLINE)
        draw.rectangle([cx - 2 + leg_spread, base_y - 5,
                        cx + 4 + leg_spread, base_y], fill=CLR_BOOT, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, body_cy + 10,
                        cx + 10 - leg_spread, base_y], fill=CLR_LEG, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, base_y - 5,
                        cx + 10 - leg_spread, base_y], fill=CLR_BOOT, outline=OUTLINE)

        # Body
        ellipse(draw, cx + 2, body_cy, 12, 12, CLR_ROBE)
        ellipse(draw, cx, body_cy - 2, 7, 7, CLR_ROBE_LIGHT, outline=None)
        draw.rectangle([cx - 10, body_cy + 8, cx + 14, body_cy + 13],
                       fill=CLR_GOLD, outline=OUTLINE)

        # Arm
        draw.rectangle([cx + 8, body_cy - 4, cx + 14, body_cy + 5],
                       fill=CLR_ROBE, outline=OUTLINE)
        draw.rectangle([cx + 8, body_cy + 1, cx + 14, body_cy + 5],
                       fill=CLR_SKIN, outline=OUTLINE)
        ellipse(draw, cx + 10, body_cy - 5, 5, 3, CLR_GOLD)

        # Head
        ellipse(draw, cx + 2, head_cy, 13, 13, CLR_HAIR)
        ellipse(draw, cx + 4, head_cy + 2, 10, 9, CLR_SKIN)
        ellipse(draw, cx + 6, head_cy + 4, 6, 5, CLR_SKIN_DARK, outline=None)
        draw.rectangle([cx + 7, head_cy + 1, cx + 10, head_cy + 5], fill=(255, 255, 255))
        draw.rectangle([cx + 8, head_cy + 2, cx + 9, head_cy + 4], fill=(60, 100, 180))
        draw.point((cx + 9, head_cy + 6), fill=CLR_SKIN_DARK)
        # Halo
        draw.ellipse([cx - 8, head_cy - 24 + halo_pulse, cx + 14, head_cy - 16 + halo_pulse],
                     fill=None, outline=CLR_HALO, width=2)
        draw.point((cx + 6, head_cy - 22 + halo_pulse), fill=CLR_HALO_LIGHT)


# ===================================================================
# ROGUE (ID 50) -- Dark gray/black outfit, hood, daggers at belt,
#                   stealthy pose, mask over lower face
# ===================================================================

def draw_rogue(draw, ox, oy, direction, frame):
    bob = [0, -2, 0, -1][frame]
    leg_spread = [-4, 0, 4, 0][frame]

    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 20
    head_cy = body_cy - 20

    if direction == DOWN:
        # Legs (slim, dark)
        draw.rectangle([cx - 9 + leg_spread, body_cy + 10,
                        cx - 4 + leg_spread, base_y], fill=ROG_LEG, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, body_cy + 10,
                        cx + 9 - leg_spread, base_y], fill=ROG_LEG, outline=OUTLINE)
        draw.rectangle([cx - 9 + leg_spread, base_y - 5,
                        cx - 4 + leg_spread, base_y], fill=ROG_BOOT, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, base_y - 5,
                        cx + 9 - leg_spread, base_y], fill=ROG_BOOT, outline=OUTLINE)

        # Body
        ellipse(draw, cx, body_cy, 13, 11, ROG_OUTFIT)
        ellipse(draw, cx - 3, body_cy - 2, 7, 6, ROG_OUTFIT_LIGHT, outline=None)
        # Crossed chest straps
        draw.line([(cx - 10, body_cy - 8), (cx + 6, body_cy + 4)],
                  fill=ROG_BELT, width=2)
        draw.line([(cx + 10, body_cy - 8), (cx - 6, body_cy + 4)],
                  fill=ROG_BELT, width=2)
        # Belt with daggers
        draw.rectangle([cx - 13, body_cy + 8, cx + 13, body_cy + 13],
                       fill=ROG_BELT, outline=OUTLINE)
        draw.rectangle([cx - 2, body_cy + 9, cx + 2, body_cy + 12],
                       fill=ROG_BELT_BUCKLE, outline=OUTLINE)
        # Left dagger in sheath
        draw.rectangle([cx - 10, body_cy + 10, cx - 8, body_cy + 18],
                       fill=ROG_DAGGER_HANDLE, outline=OUTLINE)
        draw.rectangle([cx - 10, body_cy + 16, cx - 8, body_cy + 22],
                       fill=ROG_DAGGER, outline=OUTLINE)
        draw.point((cx - 9, body_cy + 22), fill=_brighten(ROG_DAGGER, 1.3))
        # Right dagger
        draw.rectangle([cx + 8, body_cy + 10, cx + 10, body_cy + 18],
                       fill=ROG_DAGGER_HANDLE, outline=OUTLINE)
        draw.rectangle([cx + 8, body_cy + 16, cx + 10, body_cy + 22],
                       fill=ROG_DAGGER, outline=OUTLINE)
        draw.point((cx + 9, body_cy + 22), fill=_brighten(ROG_DAGGER, 1.3))

        # Arms
        draw.rectangle([cx - 17, body_cy - 5, cx - 12, body_cy + 5],
                       fill=ROG_OUTFIT, outline=OUTLINE)
        draw.rectangle([cx - 17, body_cy + 1, cx - 12, body_cy + 5],
                       fill=ROG_SKIN, outline=OUTLINE)
        draw.rectangle([cx + 12, body_cy - 5, cx + 17, body_cy + 5],
                       fill=ROG_OUTFIT, outline=OUTLINE)
        draw.rectangle([cx + 12, body_cy + 1, cx + 17, body_cy + 5],
                       fill=ROG_SKIN, outline=OUTLINE)
        ellipse(draw, cx - 14, body_cy - 5, 5, 3, ROG_OUTFIT_LIGHT)
        ellipse(draw, cx + 14, body_cy - 5, 5, 3, ROG_OUTFIT_LIGHT)

        # Head
        ellipse(draw, cx, head_cy, 13, 12, ROG_SKIN)
        ellipse(draw, cx + 2, head_cy + 3, 8, 6, ROG_SKIN_DARK, outline=None)
        # Eyes (sharp, narrowed)
        draw.rectangle([cx - 6, head_cy + 1, cx - 3, head_cy + 4], fill=(255, 255, 255))
        draw.rectangle([cx - 5, head_cy + 2, cx - 4, head_cy + 3], fill=BLACK)
        draw.rectangle([cx + 3, head_cy + 1, cx + 6, head_cy + 4], fill=(255, 255, 255))
        draw.rectangle([cx + 4, head_cy + 2, cx + 5, head_cy + 3], fill=BLACK)
        # Mask over lower face
        draw.rectangle([cx - 8, head_cy + 5, cx + 8, head_cy + 12],
                       fill=ROG_MASK, outline=OUTLINE)
        draw.line([(cx - 6, head_cy + 8), (cx + 6, head_cy + 8)],
                  fill=_darken(ROG_MASK, 0.8), width=1)
        # Hood
        ellipse(draw, cx, head_cy - 4, 16, 10, ROG_HOOD)
        ellipse(draw, cx + 4, head_cy - 2, 10, 7, ROG_HOOD_DARK, outline=None)
        ellipse(draw, cx - 2, head_cy - 6, 8, 5, ROG_HOOD_LIGHT, outline=None)
        draw.polygon([(cx - 4, head_cy - 14), (cx, head_cy - 20),
                      (cx + 4, head_cy - 14)], fill=ROG_HOOD, outline=OUTLINE)

    elif direction == UP:
        # Legs
        draw.rectangle([cx - 9 + leg_spread, body_cy + 10,
                        cx - 4 + leg_spread, base_y], fill=ROG_LEG, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, body_cy + 10,
                        cx + 9 - leg_spread, base_y], fill=ROG_LEG, outline=OUTLINE)
        draw.rectangle([cx - 9 + leg_spread, base_y - 5,
                        cx - 4 + leg_spread, base_y], fill=ROG_BOOT, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, base_y - 5,
                        cx + 9 - leg_spread, base_y], fill=ROG_BOOT, outline=OUTLINE)

        # Body (back)
        ellipse(draw, cx, body_cy, 13, 11, ROG_OUTFIT)
        ellipse(draw, cx, body_cy, 9, 8, ROG_OUTFIT_DARK, outline=None)
        draw.line([(cx, body_cy - 5), (cx, body_cy + 5)],
                  fill=_darken(ROG_OUTFIT, 0.7), width=1)
        draw.rectangle([cx - 13, body_cy + 8, cx + 13, body_cy + 13],
                       fill=ROG_BELT, outline=OUTLINE)

        # Arms
        draw.rectangle([cx - 17, body_cy - 5, cx - 12, body_cy + 5],
                       fill=ROG_OUTFIT, outline=OUTLINE)
        draw.rectangle([cx + 12, body_cy - 5, cx + 17, body_cy + 5],
                       fill=ROG_OUTFIT, outline=OUTLINE)

        # Head (back)
        ellipse(draw, cx, head_cy, 13, 12, ROG_SKIN)
        ellipse(draw, cx, head_cy - 2, 9, 8, ROG_SKIN_DARK, outline=None)
        # Hood from back
        ellipse(draw, cx, head_cy - 2, 16, 12, ROG_HOOD)
        ellipse(draw, cx, head_cy, 12, 10, ROG_HOOD_DARK, outline=None)
        draw.polygon([(cx - 4, head_cy - 14), (cx, head_cy - 20),
                      (cx + 4, head_cy - 14)], fill=ROG_HOOD, outline=OUTLINE)
        draw.line([(cx, head_cy - 8), (cx, head_cy + 4)],
                  fill=ROG_HOOD_DARK, width=1)

    elif direction == LEFT:
        # Legs
        draw.rectangle([cx - 2 - leg_spread, body_cy + 10,
                        cx + 4 - leg_spread, base_y],
                       fill=_darken(ROG_LEG, 0.85), outline=OUTLINE)
        draw.rectangle([cx - 2 - leg_spread, base_y - 5,
                        cx + 4 - leg_spread, base_y], fill=ROG_BOOT, outline=OUTLINE)
        draw.rectangle([cx - 8 + leg_spread, body_cy + 10,
                        cx - 2 + leg_spread, base_y], fill=ROG_LEG, outline=OUTLINE)
        draw.rectangle([cx - 8 + leg_spread, base_y - 5,
                        cx - 2 + leg_spread, base_y], fill=ROG_BOOT, outline=OUTLINE)

        # Body
        ellipse(draw, cx - 2, body_cy, 11, 11, ROG_OUTFIT)
        ellipse(draw, cx - 4, body_cy - 2, 6, 6, ROG_OUTFIT_LIGHT, outline=None)
        draw.rectangle([cx - 13, body_cy + 8, cx + 9, body_cy + 13],
                       fill=ROG_BELT, outline=OUTLINE)
        # Dagger in belt
        draw.rectangle([cx - 8, body_cy + 10, cx - 6, body_cy + 20],
                       fill=ROG_DAGGER, outline=OUTLINE)
        draw.point((cx - 7, body_cy + 20), fill=_brighten(ROG_DAGGER, 1.3))

        # Arm
        draw.rectangle([cx - 14, body_cy - 4, cx - 8, body_cy + 5],
                       fill=ROG_OUTFIT, outline=OUTLINE)
        draw.rectangle([cx - 14, body_cy + 1, cx - 8, body_cy + 5],
                       fill=ROG_SKIN, outline=OUTLINE)
        ellipse(draw, cx - 10, body_cy - 5, 5, 3, ROG_OUTFIT_LIGHT)

        # Head
        ellipse(draw, cx - 2, head_cy, 12, 12, ROG_SKIN)
        ellipse(draw, cx - 4, head_cy + 2, 8, 7, ROG_SKIN)
        ellipse(draw, cx - 2, head_cy + 4, 5, 4, ROG_SKIN_DARK, outline=None)
        draw.rectangle([cx - 10, head_cy + 1, cx - 7, head_cy + 4], fill=(255, 255, 255))
        draw.rectangle([cx - 9, head_cy + 2, cx - 8, head_cy + 3], fill=BLACK)
        # Mask
        draw.rectangle([cx - 12, head_cy + 5, cx - 2, head_cy + 12],
                       fill=ROG_MASK, outline=OUTLINE)
        draw.line([(cx - 10, head_cy + 8), (cx - 4, head_cy + 8)],
                  fill=_darken(ROG_MASK, 0.8), width=1)
        # Hood
        ellipse(draw, cx - 2, head_cy - 4, 14, 10, ROG_HOOD)
        ellipse(draw, cx + 2, head_cy - 2, 8, 7, ROG_HOOD_DARK, outline=None)
        draw.polygon([(cx - 6, head_cy - 14), (cx - 2, head_cy - 20),
                      (cx + 2, head_cy - 14)], fill=ROG_HOOD, outline=OUTLINE)

    else:  # RIGHT
        # Legs
        draw.rectangle([cx - 2 + leg_spread, body_cy + 10,
                        cx + 4 + leg_spread, base_y],
                       fill=_darken(ROG_LEG, 0.85), outline=OUTLINE)
        draw.rectangle([cx - 2 + leg_spread, base_y - 5,
                        cx + 4 + leg_spread, base_y], fill=ROG_BOOT, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, body_cy + 10,
                        cx + 10 - leg_spread, base_y], fill=ROG_LEG, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, base_y - 5,
                        cx + 10 - leg_spread, base_y], fill=ROG_BOOT, outline=OUTLINE)

        # Body
        ellipse(draw, cx + 2, body_cy, 11, 11, ROG_OUTFIT)
        ellipse(draw, cx, body_cy - 2, 6, 6, ROG_OUTFIT_LIGHT, outline=None)
        draw.rectangle([cx - 9, body_cy + 8, cx + 13, body_cy + 13],
                       fill=ROG_BELT, outline=OUTLINE)
        # Dagger
        draw.rectangle([cx + 6, body_cy + 10, cx + 8, body_cy + 20],
                       fill=ROG_DAGGER, outline=OUTLINE)
        draw.point((cx + 7, body_cy + 20), fill=_brighten(ROG_DAGGER, 1.3))

        # Arm
        draw.rectangle([cx + 8, body_cy - 4, cx + 14, body_cy + 5],
                       fill=ROG_OUTFIT, outline=OUTLINE)
        draw.rectangle([cx + 8, body_cy + 1, cx + 14, body_cy + 5],
                       fill=ROG_SKIN, outline=OUTLINE)
        ellipse(draw, cx + 10, body_cy - 5, 5, 3, ROG_OUTFIT_LIGHT)

        # Head
        ellipse(draw, cx + 2, head_cy, 12, 12, ROG_SKIN)
        ellipse(draw, cx + 4, head_cy + 2, 8, 7, ROG_SKIN)
        ellipse(draw, cx + 6, head_cy + 4, 5, 4, ROG_SKIN_DARK, outline=None)
        draw.rectangle([cx + 7, head_cy + 1, cx + 10, head_cy + 4], fill=(255, 255, 255))
        draw.rectangle([cx + 8, head_cy + 2, cx + 9, head_cy + 3], fill=BLACK)
        # Mask
        draw.rectangle([cx + 2, head_cy + 5, cx + 12, head_cy + 12],
                       fill=ROG_MASK, outline=OUTLINE)
        draw.line([(cx + 4, head_cy + 8), (cx + 10, head_cy + 8)],
                  fill=_darken(ROG_MASK, 0.8), width=1)
        # Hood
        ellipse(draw, cx + 2, head_cy - 4, 14, 10, ROG_HOOD)
        ellipse(draw, cx - 2, head_cy - 2, 8, 7, ROG_HOOD_DARK, outline=None)
        draw.polygon([(cx - 2, head_cy - 14), (cx + 2, head_cy - 20),
                      (cx + 6, head_cy - 14)], fill=ROG_HOOD, outline=OUTLINE)


# ===================================================================
# BARBARIAN (ID 51) -- Fur/leather minimal armor, horned helm,
#                       massive axe, tribal tattoo marks
# ===================================================================

def draw_barbarian(draw, ox, oy, direction, frame):
    bob = [0, -2, 0, -1][frame]
    leg_spread = [-4, 0, 4, 0][frame]

    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 20
    head_cy = body_cy - 20

    if direction == DOWN:
        # Massive axe on back
        draw.line([(cx + 8, body_cy - 16), (cx + 8, body_cy + 8)],
                  fill=BAR_AXE_HANDLE, width=2)
        # Large axe head
        draw.polygon([(cx + 4, body_cy - 20), (cx + 8, body_cy - 16),
                      (cx + 16, body_cy - 22), (cx + 14, body_cy - 16)],
                     fill=BAR_AXE_HEAD, outline=OUTLINE)
        draw.line([(cx + 10, body_cy - 19), (cx + 14, body_cy - 19)],
                  fill=BAR_AXE_DARK, width=1)

        # Legs (thick)
        draw.rectangle([cx - 10 + leg_spread, body_cy + 10,
                        cx - 4 + leg_spread, base_y], fill=BAR_LEG, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, body_cy + 10,
                        cx + 10 - leg_spread, base_y], fill=BAR_LEG, outline=OUTLINE)
        draw.rectangle([cx - 10 + leg_spread, base_y - 5,
                        cx - 4 + leg_spread, base_y], fill=BAR_BOOT, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, base_y - 5,
                        cx + 10 - leg_spread, base_y], fill=BAR_BOOT, outline=OUTLINE)

        # Muscular torso
        ellipse(draw, cx, body_cy, 15, 13, BAR_SKIN)
        ellipse(draw, cx - 3, body_cy - 2, 9, 7, BAR_SKIN_LIGHT, outline=None)
        # Tribal tattoos on chest
        draw.line([(cx - 6, body_cy - 4), (cx - 4, body_cy)],
                  fill=BAR_TATTOO, width=1)
        draw.line([(cx - 4, body_cy), (cx - 6, body_cy + 4)],
                  fill=BAR_TATTOO, width=1)
        draw.line([(cx + 4, body_cy - 4), (cx + 6, body_cy)],
                  fill=BAR_TATTOO, width=1)
        draw.line([(cx + 6, body_cy), (cx + 4, body_cy + 4)],
                  fill=BAR_TATTOO, width=1)
        # Fur loincloth / belt
        draw.rectangle([cx - 15, body_cy + 8, cx + 15, body_cy + 14],
                       fill=BAR_FUR, outline=OUTLINE)
        draw.point((cx - 8, body_cy + 10), fill=BAR_FUR_LIGHT)
        draw.point((cx + 6, body_cy + 11), fill=BAR_FUR_LIGHT)
        draw.point((cx - 4, body_cy + 12), fill=BAR_FUR_DARK)
        draw.rectangle([cx - 3, body_cy + 9, cx + 3, body_cy + 13],
                       fill=BAR_BELT_BUCKLE, outline=OUTLINE)

        # Arms (big)
        draw.rectangle([cx - 20, body_cy - 6, cx - 13, body_cy + 6],
                       fill=BAR_SKIN, outline=OUTLINE)
        draw.rectangle([cx - 20, body_cy - 6, cx - 18, body_cy + 4],
                       fill=BAR_SKIN_LIGHT, outline=None)
        draw.rectangle([cx + 13, body_cy - 6, cx + 20, body_cy + 6],
                       fill=BAR_SKIN, outline=OUTLINE)
        draw.rectangle([cx + 13, body_cy - 6, cx + 15, body_cy + 4],
                       fill=BAR_SKIN_LIGHT, outline=None)
        # Tattoos on arms
        draw.line([(cx - 18, body_cy - 2), (cx - 14, body_cy - 2)],
                  fill=BAR_TATTOO, width=1)
        draw.line([(cx + 14, body_cy - 2), (cx + 18, body_cy - 2)],
                  fill=BAR_TATTOO, width=1)
        # Fur shoulder pads
        ellipse(draw, cx - 15, body_cy - 7, 6, 4, BAR_FUR)
        draw.point((cx - 17, body_cy - 9), fill=BAR_FUR_LIGHT)
        ellipse(draw, cx + 15, body_cy - 7, 6, 4, BAR_FUR)
        draw.point((cx + 13, body_cy - 9), fill=BAR_FUR_LIGHT)

        # Head
        ellipse(draw, cx, head_cy, 14, 13, BAR_SKIN)
        ellipse(draw, cx + 2, head_cy + 4, 8, 6, BAR_SKIN_DARK, outline=None)
        # Eyes
        draw.rectangle([cx - 6, head_cy + 1, cx - 3, head_cy + 5], fill=(255, 255, 255))
        draw.rectangle([cx - 5, head_cy + 2, cx - 4, head_cy + 4], fill=BLACK)
        draw.rectangle([cx + 3, head_cy + 1, cx + 6, head_cy + 5], fill=(255, 255, 255))
        draw.rectangle([cx + 4, head_cy + 2, cx + 5, head_cy + 4], fill=BLACK)
        draw.point((cx, head_cy + 6), fill=BAR_SKIN_DARK)
        draw.line([(cx - 2, head_cy + 8), (cx + 2, head_cy + 8)],
                  fill=BAR_SKIN_DARK, width=1)
        # Horned helm
        draw.rectangle([cx - 14, head_cy - 6, cx + 14, head_cy],
                       fill=BAR_HELM, outline=OUTLINE)
        draw.rectangle([cx + 4, head_cy - 5, cx + 13, head_cy - 1],
                       fill=BAR_HELM_DARK, outline=None)
        draw.point((cx - 10, head_cy - 3), fill=BAR_HELM_LIGHT)
        draw.point((cx + 10, head_cy - 3), fill=BAR_HELM_LIGHT)
        # Horns
        draw.polygon([(cx - 12, head_cy - 4), (cx - 18, head_cy - 22),
                      (cx - 14, head_cy - 18), (cx - 10, head_cy - 6)],
                     fill=BAR_HORN, outline=OUTLINE)
        draw.polygon([(cx + 12, head_cy - 4), (cx + 18, head_cy - 22),
                      (cx + 14, head_cy - 18), (cx + 10, head_cy - 6)],
                     fill=BAR_HORN, outline=OUTLINE)
        draw.line([(cx - 14, head_cy - 12), (cx - 16, head_cy - 12)],
                  fill=BAR_HORN_DARK, width=1)
        draw.line([(cx + 14, head_cy - 12), (cx + 16, head_cy - 12)],
                  fill=BAR_HORN_DARK, width=1)
        draw.point((cx - 18, head_cy - 22), fill=BAR_HORN_LIGHT)
        draw.point((cx + 18, head_cy - 22), fill=BAR_HORN_LIGHT)

    elif direction == UP:
        # Axe on back
        draw.line([(cx + 8, body_cy - 16), (cx + 8, body_cy + 8)],
                  fill=BAR_AXE_HANDLE, width=2)
        draw.polygon([(cx + 4, body_cy - 20), (cx + 8, body_cy - 16),
                      (cx + 16, body_cy - 22), (cx + 14, body_cy - 16)],
                     fill=BAR_AXE_HEAD, outline=OUTLINE)

        # Legs
        draw.rectangle([cx - 10 + leg_spread, body_cy + 10,
                        cx - 4 + leg_spread, base_y], fill=BAR_LEG, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, body_cy + 10,
                        cx + 10 - leg_spread, base_y], fill=BAR_LEG, outline=OUTLINE)
        draw.rectangle([cx - 10 + leg_spread, base_y - 5,
                        cx - 4 + leg_spread, base_y], fill=BAR_BOOT, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, base_y - 5,
                        cx + 10 - leg_spread, base_y], fill=BAR_BOOT, outline=OUTLINE)

        # Body (back)
        ellipse(draw, cx, body_cy, 15, 13, BAR_SKIN)
        ellipse(draw, cx, body_cy, 11, 10, BAR_SKIN_DARK, outline=None)
        draw.rectangle([cx - 15, body_cy + 8, cx + 15, body_cy + 14],
                       fill=BAR_FUR, outline=OUTLINE)

        # Arms
        draw.rectangle([cx - 20, body_cy - 6, cx - 13, body_cy + 6],
                       fill=BAR_SKIN, outline=OUTLINE)
        draw.rectangle([cx + 13, body_cy - 6, cx + 20, body_cy + 6],
                       fill=BAR_SKIN, outline=OUTLINE)
        ellipse(draw, cx - 15, body_cy - 7, 6, 4, BAR_FUR)
        ellipse(draw, cx + 15, body_cy - 7, 6, 4, BAR_FUR)

        # Head (back)
        ellipse(draw, cx, head_cy, 14, 13, BAR_SKIN)
        ellipse(draw, cx, head_cy - 2, 10, 8, BAR_SKIN_DARK, outline=None)
        # Helm
        draw.rectangle([cx - 14, head_cy - 6, cx + 14, head_cy],
                       fill=BAR_HELM, outline=OUTLINE)
        # Horns
        draw.polygon([(cx - 12, head_cy - 4), (cx - 18, head_cy - 22),
                      (cx - 14, head_cy - 18), (cx - 10, head_cy - 6)],
                     fill=BAR_HORN, outline=OUTLINE)
        draw.polygon([(cx + 12, head_cy - 4), (cx + 18, head_cy - 22),
                      (cx + 14, head_cy - 18), (cx + 10, head_cy - 6)],
                     fill=BAR_HORN, outline=OUTLINE)

    elif direction == LEFT:
        # Axe on back
        draw.line([(cx + 6, body_cy - 14), (cx + 6, body_cy + 6)],
                  fill=BAR_AXE_HANDLE, width=2)
        draw.polygon([(cx + 2, body_cy - 18), (cx + 6, body_cy - 14),
                      (cx + 14, body_cy - 20)], fill=BAR_AXE_HEAD, outline=OUTLINE)

        # Legs
        draw.rectangle([cx - 2 - leg_spread, body_cy + 10,
                        cx + 4 - leg_spread, base_y],
                       fill=_darken(BAR_LEG, 0.85), outline=OUTLINE)
        draw.rectangle([cx - 2 - leg_spread, base_y - 5,
                        cx + 4 - leg_spread, base_y], fill=BAR_BOOT, outline=OUTLINE)
        draw.rectangle([cx - 8 + leg_spread, body_cy + 10,
                        cx - 2 + leg_spread, base_y], fill=BAR_LEG, outline=OUTLINE)
        draw.rectangle([cx - 8 + leg_spread, base_y - 5,
                        cx - 2 + leg_spread, base_y], fill=BAR_BOOT, outline=OUTLINE)

        # Body
        ellipse(draw, cx - 2, body_cy, 13, 13, BAR_SKIN)
        ellipse(draw, cx - 4, body_cy - 2, 7, 7, BAR_SKIN_LIGHT, outline=None)
        # Tattoo
        draw.line([(cx - 6, body_cy - 2), (cx - 4, body_cy + 2)],
                  fill=BAR_TATTOO, width=1)
        draw.rectangle([cx - 15, body_cy + 8, cx + 11, body_cy + 14],
                       fill=BAR_FUR, outline=OUTLINE)

        # Arm
        draw.rectangle([cx - 16, body_cy - 5, cx - 10, body_cy + 6],
                       fill=BAR_SKIN, outline=OUTLINE)
        draw.line([(cx - 14, body_cy - 1), (cx - 11, body_cy - 1)],
                  fill=BAR_TATTOO, width=1)
        ellipse(draw, cx - 12, body_cy - 7, 5, 4, BAR_FUR)

        # Head
        ellipse(draw, cx - 2, head_cy, 13, 13, BAR_SKIN)
        ellipse(draw, cx - 4, head_cy + 2, 8, 7, BAR_SKIN)
        ellipse(draw, cx - 2, head_cy + 4, 5, 4, BAR_SKIN_DARK, outline=None)
        draw.rectangle([cx - 10, head_cy + 1, cx - 7, head_cy + 5], fill=(255, 255, 255))
        draw.rectangle([cx - 9, head_cy + 2, cx - 8, head_cy + 4], fill=BLACK)
        draw.point((cx - 9, head_cy + 6), fill=BAR_SKIN_DARK)
        # Helm
        draw.rectangle([cx - 14, head_cy - 6, cx + 8, head_cy],
                       fill=BAR_HELM, outline=OUTLINE)
        # Horn
        draw.polygon([(cx - 10, head_cy - 4), (cx - 16, head_cy - 22),
                      (cx - 12, head_cy - 18), (cx - 8, head_cy - 6)],
                     fill=BAR_HORN, outline=OUTLINE)
        draw.point((cx - 16, head_cy - 22), fill=BAR_HORN_LIGHT)

    else:  # RIGHT
        # Axe on back
        draw.line([(cx - 6, body_cy - 14), (cx - 6, body_cy + 6)],
                  fill=BAR_AXE_HANDLE, width=2)
        draw.polygon([(cx - 2, body_cy - 18), (cx - 6, body_cy - 14),
                      (cx - 14, body_cy - 20)], fill=BAR_AXE_HEAD, outline=OUTLINE)

        # Legs
        draw.rectangle([cx - 2 + leg_spread, body_cy + 10,
                        cx + 4 + leg_spread, base_y],
                       fill=_darken(BAR_LEG, 0.85), outline=OUTLINE)
        draw.rectangle([cx - 2 + leg_spread, base_y - 5,
                        cx + 4 + leg_spread, base_y], fill=BAR_BOOT, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, body_cy + 10,
                        cx + 10 - leg_spread, base_y], fill=BAR_LEG, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, base_y - 5,
                        cx + 10 - leg_spread, base_y], fill=BAR_BOOT, outline=OUTLINE)

        # Body
        ellipse(draw, cx + 2, body_cy, 13, 13, BAR_SKIN)
        ellipse(draw, cx, body_cy - 2, 7, 7, BAR_SKIN_LIGHT, outline=None)
        draw.line([(cx + 6, body_cy - 2), (cx + 4, body_cy + 2)],
                  fill=BAR_TATTOO, width=1)
        draw.rectangle([cx - 11, body_cy + 8, cx + 15, body_cy + 14],
                       fill=BAR_FUR, outline=OUTLINE)

        # Arm
        draw.rectangle([cx + 10, body_cy - 5, cx + 16, body_cy + 6],
                       fill=BAR_SKIN, outline=OUTLINE)
        draw.line([(cx + 11, body_cy - 1), (cx + 14, body_cy - 1)],
                  fill=BAR_TATTOO, width=1)
        ellipse(draw, cx + 12, body_cy - 7, 5, 4, BAR_FUR)

        # Head
        ellipse(draw, cx + 2, head_cy, 13, 13, BAR_SKIN)
        ellipse(draw, cx + 4, head_cy + 2, 8, 7, BAR_SKIN)
        ellipse(draw, cx + 6, head_cy + 4, 5, 4, BAR_SKIN_DARK, outline=None)
        draw.rectangle([cx + 7, head_cy + 1, cx + 10, head_cy + 5], fill=(255, 255, 255))
        draw.rectangle([cx + 8, head_cy + 2, cx + 9, head_cy + 4], fill=BLACK)
        draw.point((cx + 9, head_cy + 6), fill=BAR_SKIN_DARK)
        # Helm
        draw.rectangle([cx - 8, head_cy - 6, cx + 14, head_cy],
                       fill=BAR_HELM, outline=OUTLINE)
        # Horn
        draw.polygon([(cx + 10, head_cy - 4), (cx + 16, head_cy - 22),
                      (cx + 12, head_cy - 18), (cx + 8, head_cy - 6)],
                     fill=BAR_HORN, outline=OUTLINE)
        draw.point((cx + 16, head_cy - 22), fill=BAR_HORN_LIGHT)


# ===================================================================
# ENCHANTRESS (ID 52) -- Purple robes, magical sparkle particles,
#                          floating orbs, elegant staff
# ===================================================================

def draw_enchantress(draw, ox, oy, direction, frame):
    bob = [0, -2, 0, -1][frame]
    leg_spread = [-4, 0, 4, 0][frame]
    robe_sway = [-2, 0, 2, 0][frame]
    orb_bob = [0, -2, -1, -3][frame]

    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 20
    head_cy = body_cy - 20

    if direction == DOWN:
        # Staff behind
        staff_x = cx + 16
        draw.line([(staff_x, head_cy - 6), (staff_x, base_y + 2)],
                  fill=ENC_STAFF, width=2)
        # Gem at top
        ellipse(draw, staff_x, head_cy - 10, 4, 4, ENC_STAFF_GEM)
        draw.point((staff_x - 1, head_cy - 11), fill=ENC_SPARKLE_BRIGHT)

        # Robe body (flowing)
        draw.polygon([
            (cx - 14, body_cy - 10),
            (cx + 14, body_cy - 10),
            (cx + 16 + robe_sway, base_y + 2),
            (cx - 16 + robe_sway, base_y + 2),
        ], fill=ENC_ROBE, outline=OUTLINE)
        draw.line([(cx - 4, body_cy - 6), (cx - 6 + robe_sway, base_y)],
                  fill=ENC_ROBE_DARK, width=1)
        draw.line([(cx + 4, body_cy - 6), (cx + 6 + robe_sway, base_y)],
                  fill=ENC_ROBE_DARK, width=1)
        draw.line([(cx - 10, body_cy - 4), (cx - 12 + robe_sway, base_y)],
                  fill=ENC_ROBE_LIGHT, width=1)
        # Sparkle patterns on robe
        draw.point((cx - 6, body_cy + 2), fill=ENC_SPARKLE)
        draw.point((cx + 4, body_cy - 2), fill=ENC_SPARKLE)
        draw.point((cx - 2, body_cy + 6), fill=ENC_SPARKLE_BRIGHT)
        draw.point((cx + 8, body_cy + 4), fill=ENC_SPARKLE)
        # Belt
        draw.rectangle([cx - 14, body_cy + 8, cx + 14, body_cy + 12],
                       fill=ENC_ROBE_DARK, outline=OUTLINE)
        draw.point((cx, body_cy + 9), fill=ENC_SPARKLE_BRIGHT)

        # Arms
        draw.rectangle([cx - 17, body_cy - 5, cx - 12, body_cy + 5],
                       fill=ENC_ROBE, outline=OUTLINE)
        draw.rectangle([cx - 17, body_cy + 1, cx - 12, body_cy + 5],
                       fill=ENC_SKIN, outline=OUTLINE)
        draw.rectangle([cx + 12, body_cy - 5, cx + 17, body_cy + 5],
                       fill=ENC_ROBE, outline=OUTLINE)
        draw.rectangle([cx + 12, body_cy + 1, cx + 17, body_cy + 5],
                       fill=ENC_SKIN, outline=OUTLINE)
        ellipse(draw, cx - 14, body_cy - 5, 5, 3, ENC_ROBE_LIGHT)
        ellipse(draw, cx + 14, body_cy - 5, 5, 3, ENC_ROBE_LIGHT)

        # Floating orbs
        ellipse(draw, cx - 18, head_cy - 4 + orb_bob, 3, 3, ENC_ORB)
        draw.point((cx - 19, head_cy - 5 + orb_bob), fill=ENC_ORB_BRIGHT)
        ellipse(draw, cx + 20, head_cy - 8 + orb_bob, 3, 3, ENC_ORB)
        draw.point((cx + 19, head_cy - 9 + orb_bob), fill=ENC_ORB_BRIGHT)

        # Head
        ellipse(draw, cx, head_cy, 14, 13, ENC_HAIR)
        ellipse(draw, cx, head_cy + 2, 12, 10, ENC_SKIN)
        ellipse(draw, cx + 2, head_cy + 4, 8, 6, ENC_SKIN_DARK, outline=None)
        draw.rectangle([cx - 6, head_cy + 1, cx - 3, head_cy + 5], fill=(255, 255, 255))
        draw.rectangle([cx - 5, head_cy + 2, cx - 4, head_cy + 4], fill=(120, 60, 180))
        draw.rectangle([cx + 3, head_cy + 1, cx + 6, head_cy + 5], fill=(255, 255, 255))
        draw.rectangle([cx + 4, head_cy + 2, cx + 5, head_cy + 4], fill=(120, 60, 180))
        draw.point((cx, head_cy + 6), fill=ENC_SKIN_DARK)
        draw.line([(cx - 2, head_cy + 8), (cx + 2, head_cy + 8)],
                  fill=ENC_SKIN_DARK, width=1)
        # Sparkle in hair
        draw.point((cx - 6, head_cy - 8), fill=ENC_SPARKLE_BRIGHT)
        draw.point((cx + 8, head_cy - 6), fill=ENC_SPARKLE)

    elif direction == UP:
        staff_x = cx - 16
        draw.line([(staff_x, head_cy - 6), (staff_x, base_y + 2)],
                  fill=ENC_STAFF, width=2)
        ellipse(draw, staff_x, head_cy - 10, 4, 4, ENC_STAFF_GEM)

        draw.polygon([
            (cx - 14, body_cy - 10),
            (cx + 14, body_cy - 10),
            (cx + 16 + robe_sway, base_y + 2),
            (cx - 16 + robe_sway, base_y + 2),
        ], fill=ENC_ROBE, outline=OUTLINE)
        draw.line([(cx, body_cy - 6), (cx + robe_sway, base_y)],
                  fill=ENC_ROBE_DARK, width=1)
        draw.rectangle([cx - 14, body_cy + 8, cx + 14, body_cy + 12],
                       fill=ENC_ROBE_DARK, outline=OUTLINE)

        draw.rectangle([cx - 17, body_cy - 5, cx - 12, body_cy + 5],
                       fill=ENC_ROBE, outline=OUTLINE)
        draw.rectangle([cx + 12, body_cy - 5, cx + 17, body_cy + 5],
                       fill=ENC_ROBE, outline=OUTLINE)

        # Floating orbs
        ellipse(draw, cx - 18, head_cy - 4 + orb_bob, 3, 3, ENC_ORB)
        ellipse(draw, cx + 20, head_cy - 8 + orb_bob, 3, 3, ENC_ORB)

        # Head (back)
        ellipse(draw, cx, head_cy, 14, 13, ENC_HAIR)
        ellipse(draw, cx, head_cy - 2, 10, 8, _darken(ENC_HAIR, 0.85), outline=None)
        # Long hair flowing
        draw.line([(cx - 6, head_cy + 6), (cx - 8, body_cy)],
                  fill=ENC_HAIR, width=2)
        draw.line([(cx + 6, head_cy + 6), (cx + 8, body_cy)],
                  fill=ENC_HAIR, width=2)
        draw.point((cx - 4, head_cy - 8), fill=ENC_SPARKLE)

    elif direction == LEFT:
        staff_x = cx - 14
        draw.line([(staff_x, head_cy - 6), (staff_x, base_y + 2)],
                  fill=ENC_STAFF, width=2)
        ellipse(draw, staff_x, head_cy - 10, 4, 4, ENC_STAFF_GEM)
        draw.point((staff_x - 1, head_cy - 11), fill=ENC_SPARKLE_BRIGHT)

        draw.polygon([
            (cx - 10, body_cy - 10),
            (cx + 8, body_cy - 10),
            (cx + 12 + robe_sway, base_y + 2),
            (cx - 14 + robe_sway, base_y + 2),
        ], fill=ENC_ROBE, outline=OUTLINE)
        draw.line([(cx - 2, body_cy - 6), (cx - 4 + robe_sway, base_y)],
                  fill=ENC_ROBE_DARK, width=1)
        draw.point((cx - 4, body_cy + 2), fill=ENC_SPARKLE)
        draw.point((cx + 2, body_cy - 2), fill=ENC_SPARKLE)
        draw.rectangle([cx - 12, body_cy + 8, cx + 10, body_cy + 12],
                       fill=ENC_ROBE_DARK, outline=OUTLINE)

        draw.rectangle([cx - 14, body_cy - 4, cx - 8, body_cy + 5],
                       fill=ENC_ROBE, outline=OUTLINE)
        draw.rectangle([cx - 14, body_cy + 1, cx - 8, body_cy + 5],
                       fill=ENC_SKIN, outline=OUTLINE)
        ellipse(draw, cx - 10, body_cy - 5, 5, 3, ENC_ROBE_LIGHT)

        ellipse(draw, cx - 18, head_cy - 6 + orb_bob, 3, 3, ENC_ORB)
        draw.point((cx - 19, head_cy - 7 + orb_bob), fill=ENC_ORB_BRIGHT)

        ellipse(draw, cx - 2, head_cy, 13, 13, ENC_HAIR)
        ellipse(draw, cx - 4, head_cy + 2, 10, 9, ENC_SKIN)
        ellipse(draw, cx - 2, head_cy + 4, 6, 5, ENC_SKIN_DARK, outline=None)
        draw.rectangle([cx - 10, head_cy + 1, cx - 7, head_cy + 5], fill=(255, 255, 255))
        draw.rectangle([cx - 9, head_cy + 2, cx - 8, head_cy + 4], fill=(120, 60, 180))
        draw.point((cx - 9, head_cy + 6), fill=ENC_SKIN_DARK)
        draw.point((cx - 6, head_cy - 8), fill=ENC_SPARKLE_BRIGHT)

    else:  # RIGHT
        staff_x = cx + 14
        draw.line([(staff_x, head_cy - 6), (staff_x, base_y + 2)],
                  fill=ENC_STAFF, width=2)
        ellipse(draw, staff_x, head_cy - 10, 4, 4, ENC_STAFF_GEM)
        draw.point((staff_x + 1, head_cy - 11), fill=ENC_SPARKLE_BRIGHT)

        draw.polygon([
            (cx - 8, body_cy - 10),
            (cx + 10, body_cy - 10),
            (cx + 14 + robe_sway, base_y + 2),
            (cx - 12 + robe_sway, base_y + 2),
        ], fill=ENC_ROBE, outline=OUTLINE)
        draw.line([(cx + 2, body_cy - 6), (cx + 4 + robe_sway, base_y)],
                  fill=ENC_ROBE_DARK, width=1)
        draw.point((cx + 4, body_cy + 2), fill=ENC_SPARKLE)
        draw.point((cx - 2, body_cy - 2), fill=ENC_SPARKLE)
        draw.rectangle([cx - 10, body_cy + 8, cx + 12, body_cy + 12],
                       fill=ENC_ROBE_DARK, outline=OUTLINE)

        draw.rectangle([cx + 8, body_cy - 4, cx + 14, body_cy + 5],
                       fill=ENC_ROBE, outline=OUTLINE)
        draw.rectangle([cx + 8, body_cy + 1, cx + 14, body_cy + 5],
                       fill=ENC_SKIN, outline=OUTLINE)
        ellipse(draw, cx + 10, body_cy - 5, 5, 3, ENC_ROBE_LIGHT)

        ellipse(draw, cx + 18, head_cy - 6 + orb_bob, 3, 3, ENC_ORB)
        draw.point((cx + 17, head_cy - 7 + orb_bob), fill=ENC_ORB_BRIGHT)

        ellipse(draw, cx + 2, head_cy, 13, 13, ENC_HAIR)
        ellipse(draw, cx + 4, head_cy + 2, 10, 9, ENC_SKIN)
        ellipse(draw, cx + 6, head_cy + 4, 6, 5, ENC_SKIN_DARK, outline=None)
        draw.rectangle([cx + 7, head_cy + 1, cx + 10, head_cy + 5], fill=(255, 255, 255))
        draw.rectangle([cx + 8, head_cy + 2, cx + 9, head_cy + 4], fill=(120, 60, 180))
        draw.point((cx + 9, head_cy + 6), fill=ENC_SKIN_DARK)
        draw.point((cx + 6, head_cy - 8), fill=ENC_SPARKLE_BRIGHT)


# ===================================================================
# JESTER (ID 53) -- Split-color outfit (red/green), pointed jester
#                    hat with bells, juggling balls
# ===================================================================

def draw_jester(draw, ox, oy, direction, frame):
    bob = [0, -2, 0, -1][frame]
    leg_spread = [-4, 0, 4, 0][frame]
    ball_offset = [0, -3, -1, -4][frame]

    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 20
    head_cy = body_cy - 20

    if direction == DOWN:
        # Legs (split color: left=red, right=green)
        draw.rectangle([cx - 9 + leg_spread, body_cy + 10,
                        cx - 4 + leg_spread, base_y], fill=JST_LEG_R, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, body_cy + 10,
                        cx + 9 - leg_spread, base_y], fill=JST_LEG_G, outline=OUTLINE)
        # Curled-toe boots
        draw.rectangle([cx - 10 + leg_spread, base_y - 5,
                        cx - 4 + leg_spread, base_y], fill=JST_BOOT_R, outline=OUTLINE)
        draw.point((cx - 11 + leg_spread, base_y - 3), fill=JST_BOOT_R)
        draw.rectangle([cx + 4 - leg_spread, base_y - 5,
                        cx + 10 - leg_spread, base_y], fill=JST_BOOT_G, outline=OUTLINE)
        draw.point((cx + 11 - leg_spread, base_y - 3), fill=JST_BOOT_G)

        # Body (split: left=red, right=green)
        ellipse(draw, cx, body_cy, 13, 11, JST_RED)
        # Right half green
        draw.rectangle([cx, body_cy - 11, cx + 14, body_cy + 11],
                       fill=JST_GREEN, outline=None)
        # Redraw outline
        draw.arc([cx - 13, body_cy - 11, cx + 13, body_cy + 11],
                 start=0, end=360, fill=OUTLINE, width=1)
        # Diamond pattern on center seam
        for dy in range(-8, 10, 4):
            draw.polygon([(cx, body_cy + dy - 2), (cx + 2, body_cy + dy),
                          (cx, body_cy + dy + 2), (cx - 2, body_cy + dy)],
                         fill=JST_BELL, outline=None)
        # Belt
        draw.rectangle([cx - 13, body_cy + 8, cx + 13, body_cy + 13],
                       fill=JST_BELL, outline=OUTLINE)
        draw.rectangle([cx - 2, body_cy + 9, cx + 2, body_cy + 12],
                       fill=JST_BELL_DARK, outline=OUTLINE)

        # Arms (left=red, right=green)
        draw.rectangle([cx - 17, body_cy - 5, cx - 12, body_cy + 5],
                       fill=JST_RED, outline=OUTLINE)
        draw.rectangle([cx - 17, body_cy + 1, cx - 12, body_cy + 5],
                       fill=JST_SKIN, outline=OUTLINE)
        draw.rectangle([cx + 12, body_cy - 5, cx + 17, body_cy + 5],
                       fill=JST_GREEN, outline=OUTLINE)
        draw.rectangle([cx + 12, body_cy + 1, cx + 17, body_cy + 5],
                       fill=JST_SKIN, outline=OUTLINE)
        ellipse(draw, cx - 14, body_cy - 5, 5, 3, JST_RED_LIGHT)
        ellipse(draw, cx + 14, body_cy - 5, 5, 3, JST_GREEN_LIGHT)

        # Juggling balls
        ellipse(draw, cx - 14, head_cy - 6 + ball_offset, 3, 3, JST_BALL_1)
        ellipse(draw, cx, head_cy - 12 + ball_offset, 3, 3, JST_BALL_2)
        ellipse(draw, cx + 14, head_cy - 4 + ball_offset, 3, 3, JST_BALL_3)

        # Head
        ellipse(draw, cx, head_cy, 13, 12, JST_SKIN)
        ellipse(draw, cx + 2, head_cy + 3, 8, 6, JST_SKIN_DARK, outline=None)
        draw.rectangle([cx - 6, head_cy + 1, cx - 3, head_cy + 4], fill=(255, 255, 255))
        draw.rectangle([cx - 5, head_cy + 2, cx - 4, head_cy + 3], fill=BLACK)
        draw.rectangle([cx + 3, head_cy + 1, cx + 6, head_cy + 4], fill=(255, 255, 255))
        draw.rectangle([cx + 4, head_cy + 2, cx + 5, head_cy + 3], fill=BLACK)
        draw.point((cx, head_cy + 5), fill=JST_SKIN_DARK)
        # Big smile
        draw.arc([cx - 4, head_cy + 5, cx + 4, head_cy + 10],
                 start=0, end=180, fill=JST_SKIN_DARK, width=1)
        # Jester hat (3-pronged)
        draw.rectangle([cx - 14, head_cy - 8, cx + 14, head_cy - 2],
                       fill=JST_RED, outline=OUTLINE)
        draw.rectangle([cx, head_cy - 7, cx + 13, head_cy - 3],
                       fill=JST_GREEN, outline=None)
        # Left prong (red)
        draw.polygon([(cx - 12, head_cy - 8), (cx - 18, head_cy - 22),
                      (cx - 8, head_cy - 8)], fill=JST_RED, outline=OUTLINE)
        ellipse(draw, cx - 18, head_cy - 22, 2, 2, JST_BELL)
        # Center prong
        draw.polygon([(cx - 2, head_cy - 8), (cx, head_cy - 26),
                      (cx + 2, head_cy - 8)], fill=JST_GREEN, outline=OUTLINE)
        ellipse(draw, cx, head_cy - 26, 2, 2, JST_BELL)
        # Right prong (green)
        draw.polygon([(cx + 8, head_cy - 8), (cx + 18, head_cy - 22),
                      (cx + 12, head_cy - 8)], fill=JST_GREEN, outline=OUTLINE)
        ellipse(draw, cx + 18, head_cy - 22, 2, 2, JST_BELL)

    elif direction == UP:
        # Legs
        draw.rectangle([cx - 9 + leg_spread, body_cy + 10,
                        cx - 4 + leg_spread, base_y], fill=JST_LEG_G, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, body_cy + 10,
                        cx + 9 - leg_spread, base_y], fill=JST_LEG_R, outline=OUTLINE)
        draw.rectangle([cx - 10 + leg_spread, base_y - 5,
                        cx - 4 + leg_spread, base_y], fill=JST_BOOT_G, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, base_y - 5,
                        cx + 10 - leg_spread, base_y], fill=JST_BOOT_R, outline=OUTLINE)

        # Body (back: reversed)
        ellipse(draw, cx, body_cy, 13, 11, JST_GREEN)
        draw.rectangle([cx, body_cy - 11, cx + 14, body_cy + 11],
                       fill=JST_RED, outline=None)
        draw.arc([cx - 13, body_cy - 11, cx + 13, body_cy + 11],
                 start=0, end=360, fill=OUTLINE, width=1)
        draw.rectangle([cx - 13, body_cy + 8, cx + 13, body_cy + 13],
                       fill=JST_BELL, outline=OUTLINE)

        draw.rectangle([cx - 17, body_cy - 5, cx - 12, body_cy + 5],
                       fill=JST_GREEN, outline=OUTLINE)
        draw.rectangle([cx + 12, body_cy - 5, cx + 17, body_cy + 5],
                       fill=JST_RED, outline=OUTLINE)

        # Head (back)
        ellipse(draw, cx, head_cy, 13, 12, JST_SKIN)
        ellipse(draw, cx, head_cy - 2, 9, 8, JST_SKIN_DARK, outline=None)
        # Jester hat from back
        draw.rectangle([cx - 14, head_cy - 8, cx + 14, head_cy - 2],
                       fill=JST_GREEN, outline=OUTLINE)
        draw.rectangle([cx, head_cy - 7, cx + 13, head_cy - 3],
                       fill=JST_RED, outline=None)
        draw.polygon([(cx - 12, head_cy - 8), (cx - 18, head_cy - 22),
                      (cx - 8, head_cy - 8)], fill=JST_GREEN, outline=OUTLINE)
        ellipse(draw, cx - 18, head_cy - 22, 2, 2, JST_BELL)
        draw.polygon([(cx - 2, head_cy - 8), (cx, head_cy - 26),
                      (cx + 2, head_cy - 8)], fill=JST_RED, outline=OUTLINE)
        ellipse(draw, cx, head_cy - 26, 2, 2, JST_BELL)
        draw.polygon([(cx + 8, head_cy - 8), (cx + 18, head_cy - 22),
                      (cx + 12, head_cy - 8)], fill=JST_RED, outline=OUTLINE)
        ellipse(draw, cx + 18, head_cy - 22, 2, 2, JST_BELL)

    elif direction == LEFT:
        # Legs
        draw.rectangle([cx - 2 - leg_spread, body_cy + 10,
                        cx + 4 - leg_spread, base_y],
                       fill=_darken(JST_LEG_R, 0.85), outline=OUTLINE)
        draw.rectangle([cx - 2 - leg_spread, base_y - 5,
                        cx + 4 - leg_spread, base_y], fill=JST_BOOT_R, outline=OUTLINE)
        draw.rectangle([cx - 8 + leg_spread, body_cy + 10,
                        cx - 2 + leg_spread, base_y], fill=JST_LEG_G, outline=OUTLINE)
        draw.rectangle([cx - 8 + leg_spread, base_y - 5,
                        cx - 2 + leg_spread, base_y], fill=JST_BOOT_G, outline=OUTLINE)

        # Body
        ellipse(draw, cx - 2, body_cy, 11, 11, JST_RED)
        draw.rectangle([cx - 2, body_cy - 11, cx + 12, body_cy + 11],
                       fill=JST_GREEN, outline=None)
        draw.arc([cx - 13, body_cy - 11, cx + 9, body_cy + 11],
                 start=0, end=360, fill=OUTLINE, width=1)
        draw.rectangle([cx - 13, body_cy + 8, cx + 9, body_cy + 13],
                       fill=JST_BELL, outline=OUTLINE)

        draw.rectangle([cx - 14, body_cy - 4, cx - 8, body_cy + 5],
                       fill=JST_RED, outline=OUTLINE)
        draw.rectangle([cx - 14, body_cy + 1, cx - 8, body_cy + 5],
                       fill=JST_SKIN, outline=OUTLINE)
        ellipse(draw, cx - 10, body_cy - 5, 5, 3, JST_RED_LIGHT)

        # Juggling ball
        ellipse(draw, cx - 16, head_cy - 8 + ball_offset, 3, 3, JST_BALL_1)

        # Head
        ellipse(draw, cx - 2, head_cy, 12, 12, JST_SKIN)
        ellipse(draw, cx - 4, head_cy + 2, 8, 7, JST_SKIN)
        ellipse(draw, cx - 2, head_cy + 4, 5, 4, JST_SKIN_DARK, outline=None)
        draw.rectangle([cx - 10, head_cy + 1, cx - 7, head_cy + 4], fill=(255, 255, 255))
        draw.rectangle([cx - 9, head_cy + 2, cx - 8, head_cy + 3], fill=BLACK)
        draw.point((cx - 9, head_cy + 5), fill=JST_SKIN_DARK)
        draw.arc([cx - 8, head_cy + 5, cx - 4, head_cy + 9],
                 start=0, end=180, fill=JST_SKIN_DARK, width=1)
        # Jester hat
        draw.rectangle([cx - 14, head_cy - 8, cx + 6, head_cy - 2],
                       fill=JST_RED, outline=OUTLINE)
        draw.polygon([(cx - 10, head_cy - 8), (cx - 16, head_cy - 22),
                      (cx - 6, head_cy - 8)], fill=JST_RED, outline=OUTLINE)
        ellipse(draw, cx - 16, head_cy - 22, 2, 2, JST_BELL)
        draw.polygon([(cx - 2, head_cy - 8), (cx, head_cy - 24),
                      (cx + 2, head_cy - 8)], fill=JST_GREEN, outline=OUTLINE)
        ellipse(draw, cx, head_cy - 24, 2, 2, JST_BELL)

    else:  # RIGHT
        # Legs
        draw.rectangle([cx - 2 + leg_spread, body_cy + 10,
                        cx + 4 + leg_spread, base_y],
                       fill=_darken(JST_LEG_G, 0.85), outline=OUTLINE)
        draw.rectangle([cx - 2 + leg_spread, base_y - 5,
                        cx + 4 + leg_spread, base_y], fill=JST_BOOT_G, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, body_cy + 10,
                        cx + 10 - leg_spread, base_y], fill=JST_LEG_R, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, base_y - 5,
                        cx + 10 - leg_spread, base_y], fill=JST_BOOT_R, outline=OUTLINE)

        # Body
        ellipse(draw, cx + 2, body_cy, 11, 11, JST_GREEN)
        draw.rectangle([cx + 2, body_cy - 11, cx + 14, body_cy + 11],
                       fill=JST_RED, outline=None)
        draw.arc([cx - 9, body_cy - 11, cx + 13, body_cy + 11],
                 start=0, end=360, fill=OUTLINE, width=1)
        draw.rectangle([cx - 9, body_cy + 8, cx + 13, body_cy + 13],
                       fill=JST_BELL, outline=OUTLINE)

        draw.rectangle([cx + 8, body_cy - 4, cx + 14, body_cy + 5],
                       fill=JST_GREEN, outline=OUTLINE)
        draw.rectangle([cx + 8, body_cy + 1, cx + 14, body_cy + 5],
                       fill=JST_SKIN, outline=OUTLINE)
        ellipse(draw, cx + 10, body_cy - 5, 5, 3, JST_GREEN_LIGHT)

        # Juggling ball
        ellipse(draw, cx + 16, head_cy - 8 + ball_offset, 3, 3, JST_BALL_2)

        # Head
        ellipse(draw, cx + 2, head_cy, 12, 12, JST_SKIN)
        ellipse(draw, cx + 4, head_cy + 2, 8, 7, JST_SKIN)
        ellipse(draw, cx + 6, head_cy + 4, 5, 4, JST_SKIN_DARK, outline=None)
        draw.rectangle([cx + 7, head_cy + 1, cx + 10, head_cy + 4], fill=(255, 255, 255))
        draw.rectangle([cx + 8, head_cy + 2, cx + 9, head_cy + 3], fill=BLACK)
        draw.point((cx + 9, head_cy + 5), fill=JST_SKIN_DARK)
        draw.arc([cx + 4, head_cy + 5, cx + 8, head_cy + 9],
                 start=0, end=180, fill=JST_SKIN_DARK, width=1)
        # Jester hat
        draw.rectangle([cx - 6, head_cy - 8, cx + 14, head_cy - 2],
                       fill=JST_GREEN, outline=OUTLINE)
        draw.polygon([(cx + 6, head_cy - 8), (cx + 16, head_cy - 22),
                      (cx + 10, head_cy - 8)], fill=JST_GREEN, outline=OUTLINE)
        ellipse(draw, cx + 16, head_cy - 22, 2, 2, JST_BELL)
        draw.polygon([(cx - 2, head_cy - 8), (cx, head_cy - 24),
                      (cx + 2, head_cy - 8)], fill=JST_RED, outline=OUTLINE)
        ellipse(draw, cx, head_cy - 24, 2, 2, JST_BELL)


# ===================================================================
# VALKYRIE (ID 54) -- Silver armor, winged helm, cape,
#                      spear/shield, Norse motifs
# ===================================================================

def draw_valkyrie(draw, ox, oy, direction, frame):
    bob = [0, -2, 0, -1][frame]
    leg_spread = [-4, 0, 4, 0][frame]

    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 20
    head_cy = body_cy - 20

    if direction == DOWN:
        # Cape
        cape_sway = [0, 2, 0, -2][frame]
        draw.polygon([
            (cx - 14, body_cy - 6),
            (cx + 14, body_cy - 6),
            (cx + 16 + cape_sway, base_y - 2),
            (cx - 16 + cape_sway, base_y - 2),
        ], fill=VAL_CAPE, outline=OUTLINE)
        draw.line([(cx - 4, body_cy), (cx - 6 + cape_sway, base_y - 4)],
                  fill=VAL_CAPE_DARK, width=1)
        draw.line([(cx + 4, body_cy), (cx + 6 + cape_sway, base_y - 4)],
                  fill=VAL_CAPE_DARK, width=1)

        # Spear behind
        draw.line([(cx + 18, head_cy - 16), (cx + 18, base_y + 2)],
                  fill=VAL_SPEAR_DARK, width=2)
        # Spear tip
        draw.polygon([(cx + 16, head_cy - 20), (cx + 18, head_cy - 26),
                      (cx + 20, head_cy - 20)], fill=VAL_SPEAR, outline=OUTLINE)
        draw.line([(cx + 18, head_cy - 24), (cx + 18, head_cy - 20)],
                  fill=_brighten(VAL_SPEAR, 1.3), width=1)

        # Legs
        draw.rectangle([cx - 9 + leg_spread, body_cy + 10,
                        cx - 4 + leg_spread, base_y], fill=VAL_LEG, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, body_cy + 10,
                        cx + 9 - leg_spread, base_y], fill=VAL_LEG, outline=OUTLINE)
        draw.rectangle([cx - 9 + leg_spread, base_y - 5,
                        cx - 4 + leg_spread, base_y], fill=VAL_BOOT, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, base_y - 5,
                        cx + 9 - leg_spread, base_y], fill=VAL_BOOT, outline=OUTLINE)

        # Armor body
        ellipse(draw, cx, body_cy, 14, 12, VAL_ARMOR)
        ellipse(draw, cx - 3, body_cy - 2, 8, 7, VAL_ARMOR_LIGHT, outline=None)
        # Norse knotwork on chest
        draw.arc([cx - 4, body_cy - 6, cx + 4, body_cy + 2],
                 start=0, end=360, fill=VAL_SHIELD_ACCENT, width=1)
        # Belt
        draw.rectangle([cx - 14, body_cy + 8, cx + 14, body_cy + 13],
                       fill=VAL_ARMOR_DARK, outline=OUTLINE)
        draw.rectangle([cx - 2, body_cy + 9, cx + 2, body_cy + 12],
                       fill=VAL_ARMOR_LIGHT, outline=OUTLINE)

        # Shield on left
        draw.rectangle([cx - 22, body_cy - 6, cx - 14, body_cy + 8],
                       fill=VAL_SHIELD, outline=OUTLINE)
        draw.line([(cx - 18, body_cy - 4), (cx - 18, body_cy + 6)],
                  fill=VAL_SHIELD_ACCENT, width=1)
        draw.line([(cx - 20, body_cy + 1), (cx - 16, body_cy + 1)],
                  fill=VAL_SHIELD_ACCENT, width=1)

        # Arms
        draw.rectangle([cx - 17, body_cy - 5, cx - 12, body_cy + 5],
                       fill=VAL_ARMOR, outline=OUTLINE)
        draw.rectangle([cx + 12, body_cy - 5, cx + 17, body_cy + 5],
                       fill=VAL_ARMOR, outline=OUTLINE)
        draw.rectangle([cx + 12, body_cy + 1, cx + 17, body_cy + 5],
                       fill=VAL_SKIN, outline=OUTLINE)
        ellipse(draw, cx - 14, body_cy - 5, 5, 3, VAL_ARMOR_LIGHT)
        ellipse(draw, cx + 14, body_cy - 5, 5, 3, VAL_ARMOR_LIGHT)

        # Head
        ellipse(draw, cx, head_cy, 13, 12, VAL_HAIR)
        ellipse(draw, cx, head_cy + 2, 11, 9, VAL_SKIN)
        ellipse(draw, cx + 2, head_cy + 4, 7, 5, VAL_SKIN_DARK, outline=None)
        draw.rectangle([cx - 6, head_cy + 1, cx - 3, head_cy + 4], fill=(255, 255, 255))
        draw.rectangle([cx - 5, head_cy + 2, cx - 4, head_cy + 3], fill=(80, 120, 180))
        draw.rectangle([cx + 3, head_cy + 1, cx + 6, head_cy + 4], fill=(255, 255, 255))
        draw.rectangle([cx + 4, head_cy + 2, cx + 5, head_cy + 3], fill=(80, 120, 180))
        draw.point((cx, head_cy + 5), fill=VAL_SKIN_DARK)
        # Winged helm
        draw.rectangle([cx - 14, head_cy - 6, cx + 14, head_cy - 1],
                       fill=VAL_ARMOR, outline=OUTLINE)
        draw.rectangle([cx + 4, head_cy - 5, cx + 13, head_cy - 2],
                       fill=VAL_ARMOR_DARK, outline=None)
        # Wings
        draw.polygon([(cx - 12, head_cy - 4), (cx - 18, head_cy - 18),
                      (cx - 14, head_cy - 12)], fill=VAL_WING, outline=OUTLINE)
        draw.polygon([(cx + 12, head_cy - 4), (cx + 18, head_cy - 18),
                      (cx + 14, head_cy - 12)], fill=VAL_WING, outline=OUTLINE)
        draw.line([(cx - 16, head_cy - 14), (cx - 14, head_cy - 8)],
                  fill=VAL_WING_DARK, width=1)
        draw.line([(cx + 16, head_cy - 14), (cx + 14, head_cy - 8)],
                  fill=VAL_WING_DARK, width=1)

    elif direction == UP:
        cape_sway = [0, 2, 0, -2][frame]
        draw.polygon([
            (cx - 14, body_cy - 8),
            (cx + 14, body_cy - 8),
            (cx + 18 + cape_sway, base_y),
            (cx - 18 + cape_sway, base_y),
        ], fill=VAL_CAPE, outline=OUTLINE)
        draw.line([(cx, body_cy - 2), (cx + cape_sway, base_y - 4)],
                  fill=VAL_CAPE_DARK, width=1)

        # Spear
        draw.line([(cx - 18, head_cy - 16), (cx - 18, base_y + 2)],
                  fill=VAL_SPEAR_DARK, width=2)
        draw.polygon([(cx - 20, head_cy - 20), (cx - 18, head_cy - 26),
                      (cx - 16, head_cy - 20)], fill=VAL_SPEAR, outline=OUTLINE)

        # Legs
        draw.rectangle([cx - 9 + leg_spread, body_cy + 10,
                        cx - 4 + leg_spread, base_y], fill=VAL_LEG, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, body_cy + 10,
                        cx + 9 - leg_spread, base_y], fill=VAL_LEG, outline=OUTLINE)
        draw.rectangle([cx - 9 + leg_spread, base_y - 5,
                        cx - 4 + leg_spread, base_y], fill=VAL_BOOT, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, base_y - 5,
                        cx + 9 - leg_spread, base_y], fill=VAL_BOOT, outline=OUTLINE)

        # Body (back)
        ellipse(draw, cx, body_cy, 14, 12, VAL_ARMOR)
        ellipse(draw, cx, body_cy, 10, 9, VAL_ARMOR_DARK, outline=None)
        draw.rectangle([cx - 14, body_cy + 8, cx + 14, body_cy + 13],
                       fill=VAL_ARMOR_DARK, outline=OUTLINE)

        draw.rectangle([cx - 17, body_cy - 5, cx - 12, body_cy + 5],
                       fill=VAL_ARMOR, outline=OUTLINE)
        draw.rectangle([cx + 12, body_cy - 5, cx + 17, body_cy + 5],
                       fill=VAL_ARMOR, outline=OUTLINE)

        # Head (back) with braid
        ellipse(draw, cx, head_cy, 13, 12, VAL_HAIR)
        ellipse(draw, cx, head_cy - 2, 9, 8, VAL_HAIR_DARK, outline=None)
        # Braid down back
        draw.line([(cx, head_cy + 8), (cx, body_cy - 4)],
                  fill=VAL_HAIR, width=2)
        draw.point((cx, body_cy - 6), fill=VAL_HAIR_DARK)
        # Helm
        draw.rectangle([cx - 14, head_cy - 6, cx + 14, head_cy - 1],
                       fill=VAL_ARMOR, outline=OUTLINE)
        draw.polygon([(cx - 12, head_cy - 4), (cx - 18, head_cy - 18),
                      (cx - 14, head_cy - 12)], fill=VAL_WING, outline=OUTLINE)
        draw.polygon([(cx + 12, head_cy - 4), (cx + 18, head_cy - 18),
                      (cx + 14, head_cy - 12)], fill=VAL_WING, outline=OUTLINE)

    elif direction == LEFT:
        # Cape
        cape_sway = [0, 2, 0, -2][frame]
        draw.polygon([
            (cx + 4, body_cy - 8),
            (cx + 14, body_cy - 4),
            (cx + 16 + cape_sway, base_y),
            (cx + 4, base_y),
        ], fill=VAL_CAPE, outline=OUTLINE)

        # Spear
        draw.line([(cx - 14, head_cy - 16), (cx - 14, base_y + 2)],
                  fill=VAL_SPEAR_DARK, width=2)
        draw.polygon([(cx - 16, head_cy - 20), (cx - 14, head_cy - 26),
                      (cx - 12, head_cy - 20)], fill=VAL_SPEAR, outline=OUTLINE)

        # Legs
        draw.rectangle([cx - 2 - leg_spread, body_cy + 10,
                        cx + 4 - leg_spread, base_y],
                       fill=_darken(VAL_LEG, 0.85), outline=OUTLINE)
        draw.rectangle([cx - 2 - leg_spread, base_y - 5,
                        cx + 4 - leg_spread, base_y], fill=VAL_BOOT, outline=OUTLINE)
        draw.rectangle([cx - 8 + leg_spread, body_cy + 10,
                        cx - 2 + leg_spread, base_y], fill=VAL_LEG, outline=OUTLINE)
        draw.rectangle([cx - 8 + leg_spread, base_y - 5,
                        cx - 2 + leg_spread, base_y], fill=VAL_BOOT, outline=OUTLINE)

        # Body
        ellipse(draw, cx - 2, body_cy, 12, 12, VAL_ARMOR)
        ellipse(draw, cx - 4, body_cy - 2, 7, 7, VAL_ARMOR_LIGHT, outline=None)
        draw.rectangle([cx - 14, body_cy + 8, cx + 10, body_cy + 13],
                       fill=VAL_ARMOR_DARK, outline=OUTLINE)

        # Shield
        draw.rectangle([cx - 18, body_cy - 8, cx - 10, body_cy + 6],
                       fill=VAL_SHIELD, outline=OUTLINE)
        draw.line([(cx - 14, body_cy - 4), (cx - 14, body_cy + 2)],
                  fill=VAL_SHIELD_ACCENT, width=1)

        draw.rectangle([cx - 14, body_cy - 4, cx - 8, body_cy + 5],
                       fill=VAL_ARMOR, outline=OUTLINE)
        ellipse(draw, cx - 10, body_cy - 5, 5, 3, VAL_ARMOR_LIGHT)

        # Head
        ellipse(draw, cx - 2, head_cy, 12, 12, VAL_HAIR)
        ellipse(draw, cx - 4, head_cy + 2, 9, 8, VAL_SKIN)
        ellipse(draw, cx - 2, head_cy + 4, 5, 4, VAL_SKIN_DARK, outline=None)
        draw.rectangle([cx - 10, head_cy + 1, cx - 7, head_cy + 4], fill=(255, 255, 255))
        draw.rectangle([cx - 9, head_cy + 2, cx - 8, head_cy + 3], fill=(80, 120, 180))
        draw.point((cx - 9, head_cy + 5), fill=VAL_SKIN_DARK)
        # Helm
        draw.rectangle([cx - 14, head_cy - 6, cx + 6, head_cy - 1],
                       fill=VAL_ARMOR, outline=OUTLINE)
        draw.polygon([(cx - 10, head_cy - 4), (cx - 16, head_cy - 18),
                      (cx - 12, head_cy - 12)], fill=VAL_WING, outline=OUTLINE)

    else:  # RIGHT
        cape_sway = [0, -2, 0, 2][frame]
        draw.polygon([
            (cx - 4, body_cy - 8),
            (cx - 14, body_cy - 4),
            (cx - 16 + cape_sway, base_y),
            (cx - 4, base_y),
        ], fill=VAL_CAPE, outline=OUTLINE)

        # Spear
        draw.line([(cx + 14, head_cy - 16), (cx + 14, base_y + 2)],
                  fill=VAL_SPEAR_DARK, width=2)
        draw.polygon([(cx + 12, head_cy - 20), (cx + 14, head_cy - 26),
                      (cx + 16, head_cy - 20)], fill=VAL_SPEAR, outline=OUTLINE)

        # Legs
        draw.rectangle([cx - 2 + leg_spread, body_cy + 10,
                        cx + 4 + leg_spread, base_y],
                       fill=_darken(VAL_LEG, 0.85), outline=OUTLINE)
        draw.rectangle([cx - 2 + leg_spread, base_y - 5,
                        cx + 4 + leg_spread, base_y], fill=VAL_BOOT, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, body_cy + 10,
                        cx + 10 - leg_spread, base_y], fill=VAL_LEG, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, base_y - 5,
                        cx + 10 - leg_spread, base_y], fill=VAL_BOOT, outline=OUTLINE)

        # Body
        ellipse(draw, cx + 2, body_cy, 12, 12, VAL_ARMOR)
        ellipse(draw, cx, body_cy - 2, 7, 7, VAL_ARMOR_LIGHT, outline=None)
        draw.rectangle([cx - 10, body_cy + 8, cx + 14, body_cy + 13],
                       fill=VAL_ARMOR_DARK, outline=OUTLINE)

        # Shield
        draw.rectangle([cx + 10, body_cy - 8, cx + 18, body_cy + 6],
                       fill=VAL_SHIELD, outline=OUTLINE)
        draw.line([(cx + 14, body_cy - 4), (cx + 14, body_cy + 2)],
                  fill=VAL_SHIELD_ACCENT, width=1)

        draw.rectangle([cx + 8, body_cy - 4, cx + 14, body_cy + 5],
                       fill=VAL_ARMOR, outline=OUTLINE)
        ellipse(draw, cx + 10, body_cy - 5, 5, 3, VAL_ARMOR_LIGHT)

        # Head
        ellipse(draw, cx + 2, head_cy, 12, 12, VAL_HAIR)
        ellipse(draw, cx + 4, head_cy + 2, 9, 8, VAL_SKIN)
        ellipse(draw, cx + 6, head_cy + 4, 5, 4, VAL_SKIN_DARK, outline=None)
        draw.rectangle([cx + 7, head_cy + 1, cx + 10, head_cy + 4], fill=(255, 255, 255))
        draw.rectangle([cx + 8, head_cy + 2, cx + 9, head_cy + 3], fill=(80, 120, 180))
        draw.point((cx + 9, head_cy + 5), fill=VAL_SKIN_DARK)
        # Helm
        draw.rectangle([cx - 6, head_cy - 6, cx + 14, head_cy - 1],
                       fill=VAL_ARMOR, outline=OUTLINE)
        draw.polygon([(cx + 10, head_cy - 4), (cx + 16, head_cy - 18),
                      (cx + 12, head_cy - 12)], fill=VAL_WING, outline=OUTLINE)


# ===================================================================
# WARLOCK (ID 55) -- Dark purple/black robes, hood, glowing purple
#                     eyes, dark cape, void magic particles
# ===================================================================

def draw_warlock(draw, ox, oy, direction, frame):
    bob = [0, -2, 0, -1][frame]
    robe_sway = [-2, 0, 2, 0][frame]
    void_pulse = [0, 1, 0, -1][frame]

    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 20
    head_cy = body_cy - 20

    if direction == DOWN:
        # Dark cape
        cape_sway = [0, 2, 0, -2][frame]
        draw.polygon([
            (cx - 16, body_cy - 8),
            (cx + 16, body_cy - 8),
            (cx + 20 + cape_sway, base_y),
            (cx - 20 + cape_sway, base_y),
        ], fill=WLK_CAPE, outline=OUTLINE)
        draw.line([(cx - 6, body_cy - 2), (cx - 8 + cape_sway, base_y - 4)],
                  fill=WLK_CAPE_DARK, width=1)
        draw.line([(cx + 6, body_cy - 2), (cx + 8 + cape_sway, base_y - 4)],
                  fill=WLK_CAPE_DARK, width=1)

        # Robe body
        draw.polygon([
            (cx - 14, body_cy - 10),
            (cx + 14, body_cy - 10),
            (cx + 16 + robe_sway, base_y + 2),
            (cx - 16 + robe_sway, base_y + 2),
        ], fill=WLK_ROBE, outline=OUTLINE)
        draw.line([(cx - 4, body_cy - 6), (cx - 6 + robe_sway, base_y)],
                  fill=WLK_ROBE_DARK, width=1)
        draw.line([(cx + 4, body_cy - 6), (cx + 6 + robe_sway, base_y)],
                  fill=WLK_ROBE_DARK, width=1)
        draw.line([(cx - 10, body_cy - 4), (cx - 12 + robe_sway, base_y)],
                  fill=WLK_ROBE_LIGHT, width=1)
        # Void energy patterns
        draw.point((cx - 4, body_cy + 2), fill=WLK_VOID)
        draw.point((cx + 6, body_cy - 2), fill=WLK_VOID)
        draw.point((cx - 8, body_cy + 6), fill=WLK_VOID_BRIGHT)
        # Belt
        draw.rectangle([cx - 14, body_cy + 8, cx + 14, body_cy + 12],
                       fill=WLK_ROBE_DARK, outline=OUTLINE)
        draw.point((cx, body_cy + 9), fill=WLK_VOID_BRIGHT)

        # Arms
        draw.rectangle([cx - 17, body_cy - 5, cx - 12, body_cy + 5],
                       fill=WLK_ROBE, outline=OUTLINE)
        draw.rectangle([cx - 17, body_cy + 1, cx - 12, body_cy + 5],
                       fill=WLK_SKIN, outline=OUTLINE)
        draw.rectangle([cx + 12, body_cy - 5, cx + 17, body_cy + 5],
                       fill=WLK_ROBE, outline=OUTLINE)
        draw.rectangle([cx + 12, body_cy + 1, cx + 17, body_cy + 5],
                       fill=WLK_SKIN, outline=OUTLINE)
        ellipse(draw, cx - 14, body_cy - 5, 5, 3, WLK_ROBE_LIGHT)
        ellipse(draw, cx + 14, body_cy - 5, 5, 3, WLK_ROBE_LIGHT)

        # Void energy in hand
        ellipse(draw, cx + 18, body_cy + 2 + void_pulse, 3, 3, WLK_VOID)
        draw.point((cx + 17, body_cy + 1 + void_pulse), fill=WLK_VOID_BRIGHT)

        # Head
        ellipse(draw, cx, head_cy, 13, 12, WLK_SKIN)
        ellipse(draw, cx + 2, head_cy + 3, 8, 6, WLK_SKIN_DARK, outline=None)
        # Glowing purple eyes
        draw.rectangle([cx - 6, head_cy + 1, cx - 3, head_cy + 5], fill=WLK_EYE)
        draw.rectangle([cx - 5, head_cy + 2, cx - 4, head_cy + 4], fill=WLK_EYE_BRIGHT)
        draw.rectangle([cx + 3, head_cy + 1, cx + 6, head_cy + 5], fill=WLK_EYE)
        draw.rectangle([cx + 4, head_cy + 2, cx + 5, head_cy + 4], fill=WLK_EYE_BRIGHT)
        draw.point((cx, head_cy + 6), fill=WLK_SKIN_DARK)
        # Hood
        ellipse(draw, cx, head_cy - 4, 16, 10, WLK_HOOD)
        ellipse(draw, cx + 4, head_cy - 2, 10, 7, WLK_HOOD_DARK, outline=None)
        ellipse(draw, cx - 2, head_cy - 6, 8, 5, WLK_HOOD_LIGHT, outline=None)
        draw.polygon([(cx - 4, head_cy - 14), (cx, head_cy - 22),
                      (cx + 4, head_cy - 14)], fill=WLK_HOOD, outline=OUTLINE)

    elif direction == UP:
        cape_sway = [0, 2, 0, -2][frame]
        draw.polygon([
            (cx - 16, body_cy - 8),
            (cx + 16, body_cy - 8),
            (cx + 20 + cape_sway, base_y),
            (cx - 20 + cape_sway, base_y),
        ], fill=WLK_CAPE, outline=OUTLINE)
        draw.line([(cx, body_cy - 2), (cx + cape_sway, base_y - 4)],
                  fill=WLK_CAPE_DARK, width=1)

        draw.polygon([
            (cx - 14, body_cy - 10),
            (cx + 14, body_cy - 10),
            (cx + 16 + robe_sway, base_y + 2),
            (cx - 16 + robe_sway, base_y + 2),
        ], fill=WLK_ROBE, outline=OUTLINE)
        draw.line([(cx, body_cy - 6), (cx + robe_sway, base_y)],
                  fill=WLK_ROBE_DARK, width=1)
        draw.rectangle([cx - 14, body_cy + 8, cx + 14, body_cy + 12],
                       fill=WLK_ROBE_DARK, outline=OUTLINE)

        draw.rectangle([cx - 17, body_cy - 5, cx - 12, body_cy + 5],
                       fill=WLK_ROBE, outline=OUTLINE)
        draw.rectangle([cx + 12, body_cy - 5, cx + 17, body_cy + 5],
                       fill=WLK_ROBE, outline=OUTLINE)

        # Head (back)
        ellipse(draw, cx, head_cy, 13, 12, WLK_SKIN)
        ellipse(draw, cx, head_cy - 2, 9, 8, WLK_SKIN_DARK, outline=None)
        ellipse(draw, cx, head_cy - 2, 16, 12, WLK_HOOD)
        ellipse(draw, cx, head_cy, 12, 10, WLK_HOOD_DARK, outline=None)
        draw.polygon([(cx - 4, head_cy - 14), (cx, head_cy - 22),
                      (cx + 4, head_cy - 14)], fill=WLK_HOOD, outline=OUTLINE)
        draw.line([(cx, head_cy - 8), (cx, head_cy + 4)],
                  fill=WLK_HOOD_DARK, width=1)

    elif direction == LEFT:
        cape_sway = [0, 2, 0, -2][frame]
        draw.polygon([
            (cx + 6, body_cy - 10),
            (cx + 16, body_cy - 6),
            (cx + 18 + cape_sway, base_y),
            (cx + 6, base_y),
        ], fill=WLK_CAPE, outline=OUTLINE)

        draw.polygon([
            (cx - 10, body_cy - 10),
            (cx + 8, body_cy - 10),
            (cx + 12 + robe_sway, base_y + 2),
            (cx - 14 + robe_sway, base_y + 2),
        ], fill=WLK_ROBE, outline=OUTLINE)
        draw.line([(cx - 2, body_cy - 6), (cx - 4 + robe_sway, base_y)],
                  fill=WLK_ROBE_DARK, width=1)
        draw.point((cx - 4, body_cy + 2), fill=WLK_VOID)
        draw.rectangle([cx - 12, body_cy + 8, cx + 10, body_cy + 12],
                       fill=WLK_ROBE_DARK, outline=OUTLINE)

        draw.rectangle([cx - 14, body_cy - 4, cx - 8, body_cy + 5],
                       fill=WLK_ROBE, outline=OUTLINE)
        draw.rectangle([cx - 14, body_cy + 1, cx - 8, body_cy + 5],
                       fill=WLK_SKIN, outline=OUTLINE)
        ellipse(draw, cx - 10, body_cy - 5, 5, 3, WLK_ROBE_LIGHT)

        # Void energy
        ellipse(draw, cx - 16, body_cy + 2 + void_pulse, 3, 3, WLK_VOID)
        draw.point((cx - 17, body_cy + 1 + void_pulse), fill=WLK_VOID_BRIGHT)

        # Head
        ellipse(draw, cx - 2, head_cy, 12, 12, WLK_SKIN)
        ellipse(draw, cx - 4, head_cy + 2, 8, 7, WLK_SKIN)
        draw.rectangle([cx - 10, head_cy + 1, cx - 7, head_cy + 5], fill=WLK_EYE)
        draw.rectangle([cx - 9, head_cy + 2, cx - 8, head_cy + 4], fill=WLK_EYE_BRIGHT)
        draw.point((cx - 9, head_cy + 6), fill=WLK_SKIN_DARK)
        # Hood
        ellipse(draw, cx - 2, head_cy - 4, 14, 10, WLK_HOOD)
        ellipse(draw, cx + 2, head_cy - 2, 8, 7, WLK_HOOD_DARK, outline=None)
        draw.polygon([(cx - 6, head_cy - 14), (cx - 2, head_cy - 22),
                      (cx + 2, head_cy - 14)], fill=WLK_HOOD, outline=OUTLINE)

    else:  # RIGHT
        cape_sway = [0, -2, 0, 2][frame]
        draw.polygon([
            (cx - 6, body_cy - 10),
            (cx - 16, body_cy - 6),
            (cx - 18 + cape_sway, base_y),
            (cx - 6, base_y),
        ], fill=WLK_CAPE, outline=OUTLINE)

        draw.polygon([
            (cx - 8, body_cy - 10),
            (cx + 10, body_cy - 10),
            (cx + 14 + robe_sway, base_y + 2),
            (cx - 12 + robe_sway, base_y + 2),
        ], fill=WLK_ROBE, outline=OUTLINE)
        draw.line([(cx + 2, body_cy - 6), (cx + 4 + robe_sway, base_y)],
                  fill=WLK_ROBE_DARK, width=1)
        draw.point((cx + 4, body_cy + 2), fill=WLK_VOID)
        draw.rectangle([cx - 10, body_cy + 8, cx + 12, body_cy + 12],
                       fill=WLK_ROBE_DARK, outline=OUTLINE)

        draw.rectangle([cx + 8, body_cy - 4, cx + 14, body_cy + 5],
                       fill=WLK_ROBE, outline=OUTLINE)
        draw.rectangle([cx + 8, body_cy + 1, cx + 14, body_cy + 5],
                       fill=WLK_SKIN, outline=OUTLINE)
        ellipse(draw, cx + 10, body_cy - 5, 5, 3, WLK_ROBE_LIGHT)

        ellipse(draw, cx + 16, body_cy + 2 + void_pulse, 3, 3, WLK_VOID)
        draw.point((cx + 15, body_cy + 1 + void_pulse), fill=WLK_VOID_BRIGHT)

        # Head
        ellipse(draw, cx + 2, head_cy, 12, 12, WLK_SKIN)
        ellipse(draw, cx + 4, head_cy + 2, 8, 7, WLK_SKIN)
        draw.rectangle([cx + 7, head_cy + 1, cx + 10, head_cy + 5], fill=WLK_EYE)
        draw.rectangle([cx + 8, head_cy + 2, cx + 9, head_cy + 4], fill=WLK_EYE_BRIGHT)
        draw.point((cx + 9, head_cy + 6), fill=WLK_SKIN_DARK)
        # Hood
        ellipse(draw, cx + 2, head_cy - 4, 14, 10, WLK_HOOD)
        ellipse(draw, cx - 2, head_cy - 2, 8, 7, WLK_HOOD_DARK, outline=None)
        draw.polygon([(cx - 2, head_cy - 14), (cx + 2, head_cy - 22),
                      (cx + 6, head_cy - 14)], fill=WLK_HOOD, outline=OUTLINE)


# ===================================================================
# INQUISITOR (ID 56) -- Gray/tan armor, red accent, wide-brim hat,
#                         cross motif, stern appearance
# ===================================================================

def draw_inquisitor(draw, ox, oy, direction, frame):
    bob = [0, -2, 0, -1][frame]
    leg_spread = [-4, 0, 4, 0][frame]

    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 20
    head_cy = body_cy - 20

    if direction == DOWN:
        # Short cape
        cape_sway = [0, 1, 0, -1][frame]
        draw.polygon([
            (cx - 12, body_cy - 4),
            (cx + 12, body_cy - 4),
            (cx + 14 + cape_sway, body_cy + 14),
            (cx - 14 + cape_sway, body_cy + 14),
        ], fill=INQ_CAPE, outline=OUTLINE)
        draw.line([(cx - 4, body_cy), (cx - 5 + cape_sway, body_cy + 12)],
                  fill=INQ_CAPE_DARK, width=1)
        draw.line([(cx + 4, body_cy), (cx + 5 + cape_sway, body_cy + 12)],
                  fill=INQ_CAPE_DARK, width=1)

        # Legs
        draw.rectangle([cx - 9 + leg_spread, body_cy + 10,
                        cx - 4 + leg_spread, base_y], fill=INQ_LEG, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, body_cy + 10,
                        cx + 9 - leg_spread, base_y], fill=INQ_LEG, outline=OUTLINE)
        draw.rectangle([cx - 9 + leg_spread, base_y - 5,
                        cx - 4 + leg_spread, base_y], fill=INQ_BOOT, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, base_y - 5,
                        cx + 9 - leg_spread, base_y], fill=INQ_BOOT, outline=OUTLINE)

        # Armor body
        ellipse(draw, cx, body_cy, 14, 12, INQ_ARMOR)
        ellipse(draw, cx - 3, body_cy - 2, 8, 7, INQ_ARMOR_LIGHT, outline=None)
        # Red accent stripe down center
        draw.line([(cx, body_cy - 8), (cx, body_cy + 6)],
                  fill=INQ_ACCENT, width=2)
        # Cross on chest
        draw.rectangle([cx - 1, body_cy - 5, cx + 1, body_cy + 3],
                       fill=INQ_CROSS, outline=None)
        draw.rectangle([cx - 3, body_cy - 2, cx + 3, body_cy],
                       fill=INQ_CROSS, outline=None)
        # Belt
        draw.rectangle([cx - 14, body_cy + 8, cx + 14, body_cy + 13],
                       fill=INQ_BELT, outline=OUTLINE)
        draw.rectangle([cx - 2, body_cy + 9, cx + 2, body_cy + 12],
                       fill=INQ_BELT_BUCKLE, outline=OUTLINE)

        # Arms
        draw.rectangle([cx - 17, body_cy - 5, cx - 12, body_cy + 5],
                       fill=INQ_ARMOR, outline=OUTLINE)
        draw.rectangle([cx - 17, body_cy + 1, cx - 12, body_cy + 5],
                       fill=INQ_SKIN, outline=OUTLINE)
        draw.rectangle([cx + 12, body_cy - 5, cx + 17, body_cy + 5],
                       fill=INQ_ARMOR, outline=OUTLINE)
        draw.rectangle([cx + 12, body_cy + 1, cx + 17, body_cy + 5],
                       fill=INQ_SKIN, outline=OUTLINE)
        # Red accent on shoulders
        ellipse(draw, cx - 14, body_cy - 5, 5, 3, INQ_ACCENT)
        ellipse(draw, cx + 14, body_cy - 5, 5, 3, INQ_ACCENT)

        # Head
        ellipse(draw, cx, head_cy, 13, 12, INQ_SKIN)
        ellipse(draw, cx + 2, head_cy + 3, 8, 6, INQ_SKIN_DARK, outline=None)
        # Stern eyes (narrow)
        draw.line([(cx - 7, head_cy + 1), (cx - 3, head_cy + 1)],
                  fill=_darken(INQ_SKIN, 0.5), width=1)
        draw.rectangle([cx - 6, head_cy + 2, cx - 3, head_cy + 4], fill=(255, 255, 255))
        draw.rectangle([cx - 5, head_cy + 2, cx - 4, head_cy + 3], fill=BLACK)
        draw.line([(cx + 3, head_cy + 1), (cx + 7, head_cy + 1)],
                  fill=_darken(INQ_SKIN, 0.5), width=1)
        draw.rectangle([cx + 3, head_cy + 2, cx + 6, head_cy + 4], fill=(255, 255, 255))
        draw.rectangle([cx + 4, head_cy + 2, cx + 5, head_cy + 3], fill=BLACK)
        draw.point((cx, head_cy + 5), fill=INQ_SKIN_DARK)
        draw.line([(cx - 2, head_cy + 7), (cx + 2, head_cy + 7)],
                  fill=INQ_SKIN_DARK, width=1)
        # Wide-brim hat
        draw.rectangle([cx - 20, head_cy - 8, cx + 20, head_cy - 4],
                       fill=INQ_HAT, outline=OUTLINE)
        draw.rectangle([cx + 8, head_cy - 7, cx + 19, head_cy - 5],
                       fill=INQ_HAT_DARK, outline=None)
        # Hat crown
        draw.rectangle([cx - 10, head_cy - 18, cx + 10, head_cy - 8],
                       fill=INQ_HAT, outline=OUTLINE)
        draw.rectangle([cx + 4, head_cy - 17, cx + 9, head_cy - 9],
                       fill=INQ_HAT_DARK, outline=None)
        # Red band
        draw.rectangle([cx - 10, head_cy - 12, cx + 10, head_cy - 8],
                       fill=INQ_ACCENT, outline=None)
        # Cross on hat
        draw.line([(cx, head_cy - 16), (cx, head_cy - 12)],
                  fill=INQ_CROSS, width=1)
        draw.line([(cx - 2, head_cy - 14), (cx + 2, head_cy - 14)],
                  fill=INQ_CROSS, width=1)

    elif direction == UP:
        cape_sway = [0, 1, 0, -1][frame]
        draw.polygon([
            (cx - 12, body_cy - 4),
            (cx + 12, body_cy - 4),
            (cx + 16 + cape_sway, body_cy + 18),
            (cx - 16 + cape_sway, body_cy + 18),
        ], fill=INQ_CAPE, outline=OUTLINE)
        draw.line([(cx, body_cy), (cx + cape_sway, body_cy + 16)],
                  fill=INQ_CAPE_DARK, width=1)

        # Legs
        draw.rectangle([cx - 9 + leg_spread, body_cy + 10,
                        cx - 4 + leg_spread, base_y], fill=INQ_LEG, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, body_cy + 10,
                        cx + 9 - leg_spread, base_y], fill=INQ_LEG, outline=OUTLINE)
        draw.rectangle([cx - 9 + leg_spread, base_y - 5,
                        cx - 4 + leg_spread, base_y], fill=INQ_BOOT, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, base_y - 5,
                        cx + 9 - leg_spread, base_y], fill=INQ_BOOT, outline=OUTLINE)

        # Body (back)
        ellipse(draw, cx, body_cy, 14, 12, INQ_ARMOR)
        ellipse(draw, cx, body_cy, 10, 9, INQ_ARMOR_DARK, outline=None)
        draw.line([(cx, body_cy - 6), (cx, body_cy + 6)],
                  fill=_darken(INQ_ARMOR, 0.7), width=1)
        draw.rectangle([cx - 14, body_cy + 8, cx + 14, body_cy + 13],
                       fill=INQ_BELT, outline=OUTLINE)

        draw.rectangle([cx - 17, body_cy - 5, cx - 12, body_cy + 5],
                       fill=INQ_ARMOR, outline=OUTLINE)
        draw.rectangle([cx + 12, body_cy - 5, cx + 17, body_cy + 5],
                       fill=INQ_ARMOR, outline=OUTLINE)
        ellipse(draw, cx - 14, body_cy - 5, 5, 3, INQ_ACCENT)
        ellipse(draw, cx + 14, body_cy - 5, 5, 3, INQ_ACCENT)

        # Head (back)
        ellipse(draw, cx, head_cy, 13, 12, INQ_SKIN)
        ellipse(draw, cx, head_cy - 2, 9, 8, INQ_SKIN_DARK, outline=None)
        # Hat from back
        draw.rectangle([cx - 20, head_cy - 8, cx + 20, head_cy - 4],
                       fill=INQ_HAT, outline=OUTLINE)
        draw.rectangle([cx - 10, head_cy - 18, cx + 10, head_cy - 8],
                       fill=INQ_HAT, outline=OUTLINE)
        draw.rectangle([cx - 10, head_cy - 12, cx + 10, head_cy - 8],
                       fill=INQ_ACCENT, outline=None)

    elif direction == LEFT:
        cape_sway = [0, 1, 0, -1][frame]
        draw.polygon([
            (cx + 4, body_cy - 4),
            (cx + 12, body_cy - 2),
            (cx + 14 + cape_sway, body_cy + 14),
            (cx + 4, body_cy + 14),
        ], fill=INQ_CAPE, outline=OUTLINE)

        # Legs
        draw.rectangle([cx - 2 - leg_spread, body_cy + 10,
                        cx + 4 - leg_spread, base_y],
                       fill=_darken(INQ_LEG, 0.85), outline=OUTLINE)
        draw.rectangle([cx - 2 - leg_spread, base_y - 5,
                        cx + 4 - leg_spread, base_y], fill=INQ_BOOT, outline=OUTLINE)
        draw.rectangle([cx - 8 + leg_spread, body_cy + 10,
                        cx - 2 + leg_spread, base_y], fill=INQ_LEG, outline=OUTLINE)
        draw.rectangle([cx - 8 + leg_spread, base_y - 5,
                        cx - 2 + leg_spread, base_y], fill=INQ_BOOT, outline=OUTLINE)

        # Body
        ellipse(draw, cx - 2, body_cy, 12, 12, INQ_ARMOR)
        ellipse(draw, cx - 4, body_cy - 2, 7, 7, INQ_ARMOR_LIGHT, outline=None)
        draw.line([(cx - 2, body_cy - 6), (cx - 2, body_cy + 4)],
                  fill=INQ_ACCENT, width=1)
        draw.rectangle([cx - 14, body_cy + 8, cx + 10, body_cy + 13],
                       fill=INQ_BELT, outline=OUTLINE)

        draw.rectangle([cx - 14, body_cy - 4, cx - 8, body_cy + 5],
                       fill=INQ_ARMOR, outline=OUTLINE)
        draw.rectangle([cx - 14, body_cy + 1, cx - 8, body_cy + 5],
                       fill=INQ_SKIN, outline=OUTLINE)
        ellipse(draw, cx - 10, body_cy - 5, 5, 3, INQ_ACCENT)

        # Head
        ellipse(draw, cx - 2, head_cy, 12, 12, INQ_SKIN)
        ellipse(draw, cx - 4, head_cy + 2, 8, 7, INQ_SKIN)
        ellipse(draw, cx - 2, head_cy + 4, 5, 4, INQ_SKIN_DARK, outline=None)
        draw.line([(cx - 11, head_cy + 1), (cx - 7, head_cy + 1)],
                  fill=_darken(INQ_SKIN, 0.5), width=1)
        draw.rectangle([cx - 10, head_cy + 2, cx - 7, head_cy + 4], fill=(255, 255, 255))
        draw.rectangle([cx - 9, head_cy + 2, cx - 8, head_cy + 3], fill=BLACK)
        draw.point((cx - 9, head_cy + 5), fill=INQ_SKIN_DARK)
        # Hat
        draw.rectangle([cx - 20, head_cy - 8, cx + 12, head_cy - 4],
                       fill=INQ_HAT, outline=OUTLINE)
        draw.rectangle([cx - 8, head_cy - 18, cx + 4, head_cy - 8],
                       fill=INQ_HAT, outline=OUTLINE)
        draw.rectangle([cx - 8, head_cy - 12, cx + 4, head_cy - 8],
                       fill=INQ_ACCENT, outline=None)

    else:  # RIGHT
        cape_sway = [0, -1, 0, 1][frame]
        draw.polygon([
            (cx - 4, body_cy - 4),
            (cx - 12, body_cy - 2),
            (cx - 14 + cape_sway, body_cy + 14),
            (cx - 4, body_cy + 14),
        ], fill=INQ_CAPE, outline=OUTLINE)

        # Legs
        draw.rectangle([cx - 2 + leg_spread, body_cy + 10,
                        cx + 4 + leg_spread, base_y],
                       fill=_darken(INQ_LEG, 0.85), outline=OUTLINE)
        draw.rectangle([cx - 2 + leg_spread, base_y - 5,
                        cx + 4 + leg_spread, base_y], fill=INQ_BOOT, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, body_cy + 10,
                        cx + 10 - leg_spread, base_y], fill=INQ_LEG, outline=OUTLINE)
        draw.rectangle([cx + 4 - leg_spread, base_y - 5,
                        cx + 10 - leg_spread, base_y], fill=INQ_BOOT, outline=OUTLINE)

        # Body
        ellipse(draw, cx + 2, body_cy, 12, 12, INQ_ARMOR)
        ellipse(draw, cx, body_cy - 2, 7, 7, INQ_ARMOR_LIGHT, outline=None)
        draw.line([(cx + 2, body_cy - 6), (cx + 2, body_cy + 4)],
                  fill=INQ_ACCENT, width=1)
        draw.rectangle([cx - 10, body_cy + 8, cx + 14, body_cy + 13],
                       fill=INQ_BELT, outline=OUTLINE)

        draw.rectangle([cx + 8, body_cy - 4, cx + 14, body_cy + 5],
                       fill=INQ_ARMOR, outline=OUTLINE)
        draw.rectangle([cx + 8, body_cy + 1, cx + 14, body_cy + 5],
                       fill=INQ_SKIN, outline=OUTLINE)
        ellipse(draw, cx + 10, body_cy - 5, 5, 3, INQ_ACCENT)

        # Head
        ellipse(draw, cx + 2, head_cy, 12, 12, INQ_SKIN)
        ellipse(draw, cx + 4, head_cy + 2, 8, 7, INQ_SKIN)
        ellipse(draw, cx + 6, head_cy + 4, 5, 4, INQ_SKIN_DARK, outline=None)
        draw.line([(cx + 7, head_cy + 1), (cx + 11, head_cy + 1)],
                  fill=_darken(INQ_SKIN, 0.5), width=1)
        draw.rectangle([cx + 7, head_cy + 2, cx + 10, head_cy + 4], fill=(255, 255, 255))
        draw.rectangle([cx + 8, head_cy + 2, cx + 9, head_cy + 3], fill=BLACK)
        draw.point((cx + 9, head_cy + 5), fill=INQ_SKIN_DARK)
        # Hat
        draw.rectangle([cx - 12, head_cy - 8, cx + 20, head_cy - 4],
                       fill=INQ_HAT, outline=OUTLINE)
        draw.rectangle([cx - 4, head_cy - 18, cx + 8, head_cy - 8],
                       fill=INQ_HAT, outline=OUTLINE)
        draw.rectangle([cx - 4, head_cy - 12, cx + 8, head_cy - 8],
                       fill=INQ_ACCENT, outline=None)


# ---------------------------------------------------------------------------
# Registry and main
# ---------------------------------------------------------------------------

MEDIEVAL_DRAW_FUNCTIONS = {
    'paladin': draw_paladin,
    'ranger': draw_ranger,
    'berserker': draw_berserker,
    'crusader': draw_crusader,
    'druid': draw_druid,
    'bard': draw_bard,
    'monk': draw_monk,
    'cleric': draw_cleric,
    'rogue': draw_rogue,
    'barbarian': draw_barbarian,
    'enchantress': draw_enchantress,
    'jester': draw_jester,
    'valkyrie': draw_valkyrie,
    'warlock': draw_warlock,
    'inquisitor': draw_inquisitor,
}

def main():
    for name, draw_func in MEDIEVAL_DRAW_FUNCTIONS.items():
        generate_character(name, draw_func=draw_func)
    print(f"\nGenerated {len(MEDIEVAL_DRAW_FUNCTIONS)} medieval character sprites.")

if __name__ == "__main__":
    main()
