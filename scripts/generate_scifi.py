#!/usr/bin/env python3
"""Sci-Fi/Tech character sprite generators (IDs 57-71), part 1.

First 8 characters with unique body shapes replacing the generic humanoid template.
"""

import math
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

# Cyborg palette
CYB_METAL = (140, 150, 160)
CYB_METAL_DARK = (100, 110, 120)
CYB_METAL_LIGHT = (180, 190, 200)
CYB_SKIN = (200, 180, 160)
CYB_SKIN_DARK = (170, 150, 130)
CYB_EYE = (0, 240, 200)
CYB_EYE_DIM = (0, 160, 140)
CYB_CIRCUIT = (0, 200, 180)
CYB_RIVET = (200, 200, 210)
CYB_JAW = (120, 130, 140)

# Hacker palette
HACK_HOODIE = (35, 40, 50)
HACK_HOODIE_DARK = (22, 26, 34)
HACK_HOODIE_LIGHT = (50, 58, 70)
HACK_SKIN = (200, 195, 190)
HACK_SKIN_DARK = (170, 165, 160)
HACK_VISOR = (0, 200, 100)
HACK_VISOR_BRIGHT = (0, 255, 130)
HACK_CODE = (0, 180, 80, 160)
HACK_CODE_DIM = (0, 140, 60, 100)
HACK_GLOW = (0, 160, 80, 120)

# MechPilot palette
MECH_ARMOR = (110, 120, 130)
MECH_ARMOR_DARK = (75, 85, 95)
MECH_ARMOR_LIGHT = (150, 160, 170)
MECH_VISOR = (60, 180, 220)
MECH_VISOR_DARK = (40, 130, 170)
MECH_RED = (200, 50, 50)
MECH_RED_BRIGHT = (240, 80, 80)
MECH_EXHAUST = (80, 70, 60)
MECH_EXHAUST_GLOW = (220, 140, 40, 180)
MECH_PAULDRON = (90, 100, 115)
MECH_BOOT = (70, 75, 85)

# Android palette
AND_BODY = (210, 215, 225)
AND_BODY_DARK = (170, 175, 185)
AND_BODY_LIGHT = (235, 240, 250)
AND_JOINT = (160, 165, 175)
AND_SEAM = (140, 145, 155)
AND_EYE_STRIP = (0, 160, 255)
AND_EYE_BRIGHT = (80, 200, 255)
AND_CORE = (0, 120, 200)
AND_PANEL = (190, 195, 205)

# Chronomancer palette
CHRONO_ROBE = (80, 55, 130)
CHRONO_ROBE_DARK = (55, 38, 95)
CHRONO_ROBE_LIGHT = (110, 80, 165)
CHRONO_SKIN = (210, 200, 220)
CHRONO_SKIN_DARK = (180, 170, 190)
CHRONO_GOLD = (220, 195, 60)
CHRONO_GOLD_BRIGHT = (255, 230, 100)
CHRONO_GEAR = (180, 165, 50)
CHRONO_GEAR_DARK = (140, 125, 30)
CHRONO_GLOW = (180, 140, 255, 160)
CHRONO_STAFF = (100, 80, 60)

# Graviton palette
GRAV_ROBE = (55, 40, 80)
GRAV_ROBE_DARK = (35, 25, 55)
GRAV_ROBE_LIGHT = (80, 60, 110)
GRAV_SKIN = (190, 180, 210)
GRAV_SKIN_DARK = (160, 150, 180)
GRAV_ORB = (140, 80, 220)
GRAV_ORB_BRIGHT = (180, 120, 255)
GRAV_ORB_DIM = (100, 60, 170, 140)
GRAV_DISTORT = (120, 80, 200, 100)
GRAV_EYE = (160, 100, 255)

# Tesla palette
TESLA_COAT = (220, 220, 230)
TESLA_COAT_DARK = (180, 180, 195)
TESLA_COAT_LIGHT = (240, 240, 250)
TESLA_SKIN = (210, 205, 200)
TESLA_SKIN_DARK = (180, 175, 170)
TESLA_GOGGLES = (140, 100, 50)
TESLA_GOGGLES_LENS = (100, 200, 255)
TESLA_COIL = (120, 120, 130)
TESLA_COIL_DARK = (90, 90, 100)
TESLA_SPARK = (100, 180, 255)
TESLA_SPARK_BRIGHT = (180, 220, 255)

# Nanoswarm palette
NANO_CORE = (0, 220, 120)
NANO_CORE_BRIGHT = (80, 255, 160)
NANO_BODY = (60, 100, 70)
NANO_BODY_DARK = (40, 70, 48)
NANO_BODY_LIGHT = (90, 140, 100)
NANO_PARTICLE = (0, 200, 100, 180)
NANO_PARTICLE_DIM = (0, 160, 80, 100)
NANO_PARTICLE_FAINT = (0, 130, 60, 60)
NANO_EYE = (0, 255, 150)


# ===================================================================
# CYBORG (ID 57) -- half-human half-machine, metal plating on right
#                   side, circuit lines, glowing cyan eye, robot arm
# ===================================================================

def draw_cyborg(draw, ox, oy, direction, frame):
    bob = [0, -2, 0, -1][frame]
    leg_spread = [-4, 0, 4, 0][frame]

    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 20
    head_cy = body_cy - 20

    metal_shadow = _darken(CYB_METAL, 0.7)

    # --- Legs ---
    if direction in (DOWN, UP):
        for side in [-1, 1]:
            ls = leg_spread if side == -1 else -leg_spread
            lx = cx + side * 7 + ls
            # Left leg = skin, right leg = metal
            leg_col = CYB_SKIN if side == -1 else CYB_METAL
            leg_dark = CYB_SKIN_DARK if side == -1 else CYB_METAL_DARK
            boot_col = _darken(leg_col, 0.65)
            draw.rectangle([lx - 3, body_cy + 10, lx + 3, base_y],
                           fill=leg_col, outline=OUTLINE)
            draw.rectangle([lx - 3, body_cy + 10, lx - 1, base_y - 6],
                           fill=_brighten(leg_col, 1.1), outline=None)
            # Boot
            draw.rectangle([lx - 3, base_y - 6, lx + 3, base_y],
                           fill=boot_col, outline=OUTLINE)
            # Metal leg circuit line
            if side == 1:
                draw.line([(lx, body_cy + 12), (lx, base_y - 7)],
                          fill=CYB_CIRCUIT, width=1)
    elif direction == LEFT:
        for i, offset in enumerate([leg_spread, -leg_spread]):
            lx = cx - 3 + offset
            leg_col = CYB_METAL if i == 0 else CYB_SKIN
            boot_col = _darken(leg_col, 0.65)
            draw.rectangle([lx - 3, body_cy + 10, lx + 3, base_y],
                           fill=leg_col, outline=OUTLINE)
            draw.rectangle([lx - 3, base_y - 6, lx + 3, base_y],
                           fill=boot_col, outline=OUTLINE)
    else:  # RIGHT
        for i, offset in enumerate([leg_spread, -leg_spread]):
            lx = cx + 3 + offset
            leg_col = CYB_METAL if i == 0 else CYB_SKIN
            boot_col = _darken(leg_col, 0.65)
            draw.rectangle([lx - 3, body_cy + 10, lx + 3, base_y],
                           fill=leg_col, outline=OUTLINE)
            draw.rectangle([lx - 3, base_y - 6, lx + 3, base_y],
                           fill=boot_col, outline=OUTLINE)

    # --- Body (half skin, half metal) ---
    if direction == DOWN:
        ellipse(draw, cx, body_cy, 14, 12, CYB_SKIN)
        # Metal right half overlay
        draw.rectangle([cx, body_cy - 12, cx + 14, body_cy + 12],
                       fill=CYB_METAL, outline=None)
        ellipse(draw, cx, body_cy, 14, 12, None, outline=OUTLINE)
        # Highlight on skin side
        ellipse(draw, cx - 4, body_cy - 3, 6, 5, _brighten(CYB_SKIN, 1.1), outline=None)
        # Metal plate detail
        draw.rectangle([cx + 2, body_cy - 6, cx + 12, body_cy + 6],
                       fill=CYB_METAL_LIGHT, outline=None)
        draw.rectangle([cx + 3, body_cy - 5, cx + 11, body_cy + 5],
                       fill=CYB_METAL, outline=None)
        # Rivets
        draw.point((cx + 4, body_cy - 4), fill=CYB_RIVET)
        draw.point((cx + 10, body_cy - 4), fill=CYB_RIVET)
        draw.point((cx + 4, body_cy + 4), fill=CYB_RIVET)
        draw.point((cx + 10, body_cy + 4), fill=CYB_RIVET)
        # Circuit line
        draw.line([(cx + 6, body_cy - 3), (cx + 6, body_cy + 3)],
                  fill=CYB_CIRCUIT, width=1)
        draw.line([(cx + 6, body_cy), (cx + 9, body_cy)],
                  fill=CYB_CIRCUIT, width=1)
        # Belt
        draw.rectangle([cx - 14, body_cy + 6, cx + 14, body_cy + 12],
                       fill=CYB_METAL_DARK, outline=OUTLINE)
        draw.rectangle([cx - 3, body_cy + 7, cx + 3, body_cy + 11],
                       fill=CYB_EYE_DIM, outline=OUTLINE)
    elif direction == UP:
        ellipse(draw, cx, body_cy, 14, 12, CYB_METAL)
        draw.rectangle([cx - 14, body_cy - 12, cx, body_cy + 12],
                       fill=CYB_SKIN, outline=None)
        ellipse(draw, cx, body_cy, 14, 12, None, outline=OUTLINE)
        draw.line([(cx, body_cy - 10), (cx, body_cy + 8)],
                  fill=CYB_METAL_DARK, width=2)
        draw.rectangle([cx - 14, body_cy + 6, cx + 14, body_cy + 12],
                       fill=CYB_METAL_DARK, outline=OUTLINE)
    elif direction == LEFT:
        ellipse(draw, cx - 2, body_cy, 12, 12, CYB_METAL)
        ellipse(draw, cx - 4, body_cy - 2, 6, 6, CYB_METAL_LIGHT, outline=None)
        # Circuit lines on visible metal side
        draw.line([(cx - 6, body_cy - 4), (cx - 6, body_cy + 4)],
                  fill=CYB_CIRCUIT, width=1)
        draw.point((cx - 8, body_cy - 2), fill=CYB_RIVET)
        draw.point((cx - 8, body_cy + 2), fill=CYB_RIVET)
        draw.rectangle([cx - 14, body_cy + 6, cx + 10, body_cy + 12],
                       fill=CYB_METAL_DARK, outline=OUTLINE)
    else:  # RIGHT
        ellipse(draw, cx + 2, body_cy, 12, 12, CYB_SKIN)
        # Metal on right side
        draw.rectangle([cx + 2, body_cy - 12, cx + 14, body_cy + 12],
                       fill=CYB_METAL, outline=None)
        ellipse(draw, cx + 2, body_cy, 12, 12, None, outline=OUTLINE)
        draw.line([(cx + 8, body_cy - 4), (cx + 8, body_cy + 4)],
                  fill=CYB_CIRCUIT, width=1)
        draw.point((cx + 6, body_cy - 2), fill=CYB_RIVET)
        draw.point((cx + 6, body_cy + 2), fill=CYB_RIVET)
        draw.rectangle([cx - 10, body_cy + 6, cx + 14, body_cy + 12],
                       fill=CYB_METAL_DARK, outline=OUTLINE)

    # --- Arms (left = skin, right = robot arm) ---
    if direction == DOWN:
        # Skin arm (left)
        draw.rectangle([cx - 18, body_cy - 6, cx - 12, body_cy + 6],
                       fill=CYB_SKIN, outline=OUTLINE)
        draw.rectangle([cx - 18, body_cy + 2, cx - 12, body_cy + 6],
                       fill=CYB_SKIN_DARK, outline=OUTLINE)
        ellipse(draw, cx - 15, body_cy - 6, 5, 3, CYB_SKIN)
        # Robot arm (right)
        draw.rectangle([cx + 12, body_cy - 6, cx + 18, body_cy + 6],
                       fill=CYB_METAL, outline=OUTLINE)
        draw.rectangle([cx + 12, body_cy - 6, cx + 14, body_cy + 2],
                       fill=CYB_METAL_LIGHT, outline=None)
        draw.rectangle([cx + 12, body_cy + 2, cx + 18, body_cy + 6],
                       fill=CYB_METAL_DARK, outline=OUTLINE)
        # Joint ring
        draw.line([(cx + 12, body_cy), (cx + 18, body_cy)],
                  fill=CYB_CIRCUIT, width=1)
        ellipse(draw, cx + 15, body_cy - 6, 5, 3, CYB_METAL_LIGHT)
        draw.point((cx + 15, body_cy - 6), fill=CYB_RIVET)
    elif direction == UP:
        draw.rectangle([cx - 18, body_cy - 6, cx - 12, body_cy + 6],
                       fill=CYB_SKIN, outline=OUTLINE)
        draw.rectangle([cx + 12, body_cy - 6, cx + 18, body_cy + 6],
                       fill=CYB_METAL, outline=OUTLINE)
        draw.line([(cx + 12, body_cy), (cx + 18, body_cy)],
                  fill=CYB_CIRCUIT, width=1)
    elif direction == LEFT:
        draw.rectangle([cx - 14, body_cy - 4, cx - 8, body_cy + 6],
                       fill=CYB_METAL, outline=OUTLINE)
        draw.line([(cx - 14, body_cy), (cx - 8, body_cy)],
                  fill=CYB_CIRCUIT, width=1)
        draw.rectangle([cx - 14, body_cy + 2, cx - 8, body_cy + 6],
                       fill=CYB_METAL_DARK, outline=OUTLINE)
        ellipse(draw, cx - 11, body_cy - 6, 5, 3, CYB_METAL_LIGHT)
    else:  # RIGHT
        draw.rectangle([cx + 8, body_cy - 4, cx + 14, body_cy + 6],
                       fill=CYB_METAL, outline=OUTLINE)
        draw.line([(cx + 8, body_cy), (cx + 14, body_cy)],
                  fill=CYB_CIRCUIT, width=1)
        draw.rectangle([cx + 8, body_cy + 2, cx + 14, body_cy + 6],
                       fill=CYB_METAL_DARK, outline=OUTLINE)
        ellipse(draw, cx + 11, body_cy - 6, 5, 3, CYB_METAL_LIGHT)

    # --- Head (half skin, half metal with glowing eye) ---
    if direction == DOWN:
        ellipse(draw, cx, head_cy, 14, 12, CYB_SKIN)
        # Metal right half
        draw.rectangle([cx, head_cy - 12, cx + 14, head_cy + 12],
                       fill=CYB_METAL, outline=None)
        ellipse(draw, cx, head_cy, 14, 12, None, outline=OUTLINE)
        # Skin highlight
        ellipse(draw, cx - 4, head_cy - 4, 6, 5, _brighten(CYB_SKIN, 1.1), outline=None)
        # Face
        ellipse(draw, cx - 4, head_cy + 4, 6, 5, CYB_SKIN, outline=None)
        # Metal jaw plate
        draw.rectangle([cx + 1, head_cy + 4, cx + 10, head_cy + 10],
                       fill=CYB_JAW, outline=OUTLINE)
        draw.point((cx + 3, head_cy + 6), fill=CYB_RIVET)
        draw.point((cx + 8, head_cy + 6), fill=CYB_RIVET)
        # Human eye (left)
        draw.rectangle([cx - 8, head_cy + 1, cx - 4, head_cy + 5], fill=(255, 255, 255))
        draw.rectangle([cx - 7, head_cy + 2, cx - 5, head_cy + 4], fill=BLACK)
        draw.point((cx - 7, head_cy + 2), fill=(255, 255, 255))
        # Cyborg eye (right, glowing)
        draw.rectangle([cx + 3, head_cy + 1, cx + 8, head_cy + 5], fill=CYB_EYE_DIM)
        draw.rectangle([cx + 4, head_cy + 2, cx + 7, head_cy + 4], fill=CYB_EYE)
        draw.point((cx + 5, head_cy + 3), fill=(200, 255, 240))
        # Circuit lines on metal half of head
        draw.line([(cx + 6, head_cy - 6), (cx + 10, head_cy - 2)],
                  fill=CYB_CIRCUIT, width=1)
        draw.line([(cx + 10, head_cy - 2), (cx + 10, head_cy + 2)],
                  fill=CYB_CIRCUIT, width=1)
    elif direction == UP:
        ellipse(draw, cx, head_cy, 14, 12, CYB_METAL)
        draw.rectangle([cx - 14, head_cy - 12, cx, head_cy + 12],
                       fill=CYB_SKIN, outline=None)
        ellipse(draw, cx, head_cy, 14, 12, None, outline=OUTLINE)
        # Seam line
        draw.line([(cx, head_cy - 10), (cx, head_cy + 8)],
                  fill=CYB_METAL_DARK, width=2)
        # Metal plate detail
        draw.line([(cx + 4, head_cy - 6), (cx + 8, head_cy - 2)],
                  fill=CYB_CIRCUIT, width=1)
    elif direction == LEFT:
        # Facing left shows more metal side
        ellipse(draw, cx - 2, head_cy, 13, 12, CYB_METAL)
        ellipse(draw, cx - 4, head_cy - 3, 7, 5, CYB_METAL_LIGHT, outline=None)
        # Face area
        ellipse(draw, cx - 6, head_cy + 4, 6, 5, CYB_METAL, outline=None)
        # Glowing eye
        draw.rectangle([cx - 10, head_cy + 1, cx - 6, head_cy + 5], fill=CYB_EYE_DIM)
        draw.rectangle([cx - 9, head_cy + 2, cx - 7, head_cy + 4], fill=CYB_EYE)
        draw.point((cx - 8, head_cy + 3), fill=(200, 255, 240))
        # Jaw plate
        draw.rectangle([cx - 10, head_cy + 5, cx - 4, head_cy + 9],
                       fill=CYB_JAW, outline=OUTLINE)
        draw.point((cx - 8, head_cy + 7), fill=CYB_RIVET)
        # Circuit lines
        draw.line([(cx - 4, head_cy - 6), (cx - 2, head_cy - 2)],
                  fill=CYB_CIRCUIT, width=1)
    else:  # RIGHT
        # Facing right shows skin side
        ellipse(draw, cx + 2, head_cy, 13, 12, CYB_SKIN)
        ellipse(draw, cx, head_cy - 3, 7, 5, _brighten(CYB_SKIN, 1.1), outline=None)
        # Face area
        ellipse(draw, cx + 6, head_cy + 4, 6, 5, CYB_SKIN, outline=None)
        ellipse(draw, cx + 8, head_cy + 6, 4, 3, CYB_SKIN_DARK, outline=None)
        # Human eye
        draw.rectangle([cx + 7, head_cy + 1, cx + 11, head_cy + 5], fill=(255, 255, 255))
        draw.rectangle([cx + 8, head_cy + 2, cx + 10, head_cy + 4], fill=BLACK)
        draw.point((cx + 8, head_cy + 2), fill=(255, 255, 255))
        # Nose/mouth
        draw.point((cx + 10, head_cy + 7), fill=CYB_SKIN_DARK)
        draw.line([(cx + 8, head_cy + 9), (cx + 10, head_cy + 9)],
                  fill=CYB_SKIN_DARK, width=1)


# ===================================================================
# HACKER (ID 58) -- dark hoodie, green terminal glow on face, floating
#                   code/matrix lines, visor with green glow
# ===================================================================

def draw_hacker(draw, ox, oy, direction, frame):
    bob = [0, -2, 0, -1][frame]
    leg_spread = [-4, 0, 4, 0][frame]
    code_shift = frame * 3  # scrolling code offset

    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 20
    head_cy = body_cy - 20

    # --- Legs ---
    if direction in (DOWN, UP):
        for side in [-1, 1]:
            ls = leg_spread if side == -1 else -leg_spread
            lx = cx + side * 7 + ls
            draw.rectangle([lx - 3, body_cy + 10, lx + 3, base_y],
                           fill=HACK_HOODIE_DARK, outline=OUTLINE)
            draw.rectangle([lx - 3, base_y - 5, lx + 3, base_y],
                           fill=_darken(HACK_HOODIE_DARK, 0.7), outline=OUTLINE)
    elif direction == LEFT:
        for offset in [leg_spread, -leg_spread]:
            lx = cx - 3 + offset
            draw.rectangle([lx - 3, body_cy + 10, lx + 3, base_y],
                           fill=HACK_HOODIE_DARK, outline=OUTLINE)
            draw.rectangle([lx - 3, base_y - 5, lx + 3, base_y],
                           fill=_darken(HACK_HOODIE_DARK, 0.7), outline=OUTLINE)
    else:  # RIGHT
        for offset in [leg_spread, -leg_spread]:
            lx = cx + 3 + offset
            draw.rectangle([lx - 3, body_cy + 10, lx + 3, base_y],
                           fill=HACK_HOODIE_DARK, outline=OUTLINE)
            draw.rectangle([lx - 3, base_y - 5, lx + 3, base_y],
                           fill=_darken(HACK_HOODIE_DARK, 0.7), outline=OUTLINE)

    # --- Body (hoodie, baggy) ---
    if direction == DOWN:
        ellipse(draw, cx, body_cy, 14, 12, HACK_HOODIE)
        ellipse(draw, cx + 3, body_cy + 2, 10, 8, HACK_HOODIE_DARK, outline=None)
        ellipse(draw, cx, body_cy, 11, 9, HACK_HOODIE, outline=None)
        ellipse(draw, cx - 3, body_cy - 2, 7, 5, HACK_HOODIE_LIGHT, outline=None)
        # Hoodie pocket
        draw.rectangle([cx - 8, body_cy + 2, cx + 8, body_cy + 8],
                       fill=HACK_HOODIE_DARK, outline=None)
        draw.line([(cx - 8, body_cy + 2), (cx + 8, body_cy + 2)],
                  fill=OUTLINE, width=1)
        # Kangaroo pocket center seam
        draw.line([(cx, body_cy + 3), (cx, body_cy + 7)],
                  fill=HACK_HOODIE, width=1)
        # Belt area
        draw.rectangle([cx - 14, body_cy + 8, cx + 14, body_cy + 12],
                       fill=HACK_HOODIE_DARK, outline=OUTLINE)
    elif direction == UP:
        ellipse(draw, cx, body_cy, 14, 12, HACK_HOODIE)
        ellipse(draw, cx, body_cy, 11, 9, HACK_HOODIE_DARK, outline=None)
        draw.line([(cx, body_cy - 6), (cx, body_cy + 6)],
                  fill=HACK_HOODIE_DARK, width=1)
        draw.rectangle([cx - 14, body_cy + 8, cx + 14, body_cy + 12],
                       fill=HACK_HOODIE_DARK, outline=OUTLINE)
    elif direction == LEFT:
        ellipse(draw, cx - 2, body_cy, 12, 12, HACK_HOODIE)
        ellipse(draw, cx + 1, body_cy + 2, 8, 8, HACK_HOODIE_DARK, outline=None)
        ellipse(draw, cx - 3, body_cy - 2, 6, 6, HACK_HOODIE_LIGHT, outline=None)
        draw.rectangle([cx - 14, body_cy + 8, cx + 10, body_cy + 12],
                       fill=HACK_HOODIE_DARK, outline=OUTLINE)
    else:  # RIGHT
        ellipse(draw, cx + 2, body_cy, 12, 12, HACK_HOODIE)
        ellipse(draw, cx + 5, body_cy + 2, 8, 8, HACK_HOODIE_DARK, outline=None)
        ellipse(draw, cx, body_cy - 2, 6, 6, HACK_HOODIE_LIGHT, outline=None)
        draw.rectangle([cx - 10, body_cy + 8, cx + 14, body_cy + 12],
                       fill=HACK_HOODIE_DARK, outline=OUTLINE)

    # --- Arms (typing gesture, hands visible) ---
    if direction == DOWN:
        draw.rectangle([cx - 18, body_cy - 6, cx - 12, body_cy + 6],
                       fill=HACK_HOODIE, outline=OUTLINE)
        draw.rectangle([cx - 18, body_cy + 2, cx - 12, body_cy + 6],
                       fill=HACK_SKIN, outline=OUTLINE)
        draw.rectangle([cx + 12, body_cy - 6, cx + 18, body_cy + 6],
                       fill=HACK_HOODIE, outline=OUTLINE)
        draw.rectangle([cx + 12, body_cy + 2, cx + 18, body_cy + 6],
                       fill=HACK_SKIN, outline=OUTLINE)
        ellipse(draw, cx - 15, body_cy - 6, 5, 3, HACK_HOODIE)
        ellipse(draw, cx + 15, body_cy - 6, 5, 3, HACK_HOODIE)
    elif direction == UP:
        draw.rectangle([cx - 18, body_cy - 6, cx - 12, body_cy + 6],
                       fill=HACK_HOODIE, outline=OUTLINE)
        draw.rectangle([cx + 12, body_cy - 6, cx + 18, body_cy + 6],
                       fill=HACK_HOODIE, outline=OUTLINE)
    elif direction == LEFT:
        draw.rectangle([cx - 14, body_cy - 4, cx - 8, body_cy + 6],
                       fill=HACK_HOODIE, outline=OUTLINE)
        draw.rectangle([cx - 14, body_cy + 2, cx - 8, body_cy + 6],
                       fill=HACK_SKIN, outline=OUTLINE)
        ellipse(draw, cx - 11, body_cy - 6, 5, 3, HACK_HOODIE)
    else:  # RIGHT
        draw.rectangle([cx + 8, body_cy - 4, cx + 14, body_cy + 6],
                       fill=HACK_HOODIE, outline=OUTLINE)
        draw.rectangle([cx + 8, body_cy + 2, cx + 14, body_cy + 6],
                       fill=HACK_SKIN, outline=OUTLINE)
        ellipse(draw, cx + 11, body_cy - 6, 5, 3, HACK_HOODIE)

    # --- Head with hood and visor ---
    if direction == DOWN:
        # Hood
        ellipse(draw, cx, head_cy - 2, 16, 12, HACK_HOODIE)
        ellipse(draw, cx + 3, head_cy, 11, 8, HACK_HOODIE_DARK, outline=None)
        ellipse(draw, cx - 2, head_cy - 4, 9, 6, HACK_HOODIE_LIGHT, outline=None)
        # Hood peak
        draw.polygon([(cx - 4, head_cy - 14), (cx, head_cy - 20),
                      (cx + 4, head_cy - 14)], fill=HACK_HOODIE, outline=OUTLINE)
        # Shadowed face
        ellipse(draw, cx, head_cy + 2, 10, 7, HACK_HOODIE_DARK, outline=None)
        # Lower face (chin)
        ellipse(draw, cx, head_cy + 5, 6, 4, HACK_SKIN, outline=None)
        # Green glow on face from below
        ellipse(draw, cx, head_cy + 3, 8, 5, (0, 80, 50, 60), outline=None)
        # Visor (green glow bar across eyes)
        draw.rectangle([cx - 9, head_cy, cx + 9, head_cy + 4],
                       fill=HACK_VISOR, outline=OUTLINE)
        draw.rectangle([cx - 7, head_cy + 1, cx + 7, head_cy + 3],
                       fill=HACK_VISOR_BRIGHT, outline=None)
        # Visor scan line
        scan_x = cx - 6 + (frame * 4) % 12
        draw.line([(scan_x, head_cy + 1), (scan_x, head_cy + 3)],
                  fill=(200, 255, 200), width=1)
    elif direction == UP:
        ellipse(draw, cx, head_cy - 2, 16, 12, HACK_HOODIE)
        ellipse(draw, cx, head_cy, 12, 8, HACK_HOODIE_DARK, outline=None)
        draw.polygon([(cx - 4, head_cy - 14), (cx, head_cy - 20),
                      (cx + 4, head_cy - 14)], fill=HACK_HOODIE, outline=OUTLINE)
        # Back of hood folds
        draw.line([(cx - 4, head_cy - 10), (cx - 6, head_cy + 4)],
                  fill=HACK_HOODIE_DARK, width=1)
        draw.line([(cx + 4, head_cy - 10), (cx + 6, head_cy + 4)],
                  fill=HACK_HOODIE_DARK, width=1)
    elif direction == LEFT:
        ellipse(draw, cx - 2, head_cy - 2, 14, 12, HACK_HOODIE)
        ellipse(draw, cx, head_cy, 10, 8, HACK_HOODIE_DARK, outline=None)
        ellipse(draw, cx - 5, head_cy - 4, 7, 5, HACK_HOODIE_LIGHT, outline=None)
        draw.polygon([(cx - 4, head_cy - 14), (cx - 2, head_cy - 20),
                      (cx + 2, head_cy - 14)], fill=HACK_HOODIE, outline=OUTLINE)
        # Face
        ellipse(draw, cx - 6, head_cy + 4, 5, 4, HACK_SKIN, outline=None)
        # Visor
        draw.rectangle([cx - 11, head_cy, cx - 3, head_cy + 4],
                       fill=HACK_VISOR, outline=OUTLINE)
        draw.rectangle([cx - 10, head_cy + 1, cx - 4, head_cy + 3],
                       fill=HACK_VISOR_BRIGHT, outline=None)
    else:  # RIGHT
        ellipse(draw, cx + 2, head_cy - 2, 14, 12, HACK_HOODIE)
        ellipse(draw, cx + 4, head_cy, 10, 8, HACK_HOODIE_DARK, outline=None)
        ellipse(draw, cx, head_cy - 4, 7, 5, HACK_HOODIE_LIGHT, outline=None)
        draw.polygon([(cx - 2, head_cy - 14), (cx + 2, head_cy - 20),
                      (cx + 4, head_cy - 14)], fill=HACK_HOODIE, outline=OUTLINE)
        # Face
        ellipse(draw, cx + 6, head_cy + 4, 5, 4, HACK_SKIN, outline=None)
        # Visor
        draw.rectangle([cx + 3, head_cy, cx + 11, head_cy + 4],
                       fill=HACK_VISOR, outline=OUTLINE)
        draw.rectangle([cx + 4, head_cy + 1, cx + 10, head_cy + 3],
                       fill=HACK_VISOR_BRIGHT, outline=None)

    # --- Floating code/matrix lines (semi-transparent) ---
    code_positions = [
        [(-20, -8), (-18, 2), (18, -4), (20, 6)],
        [(-22, -4), (-16, 6), (20, -8), (16, 2)],
        [(-18, -6), (-20, 4), (16, -2), (22, 8)],
        [(-16, -2), (-22, 8), (22, -6), (18, 4)],
    ]
    for px, py in code_positions[frame]:
        x = cx + px
        y = body_cy + py
        draw.line([(x, y), (x, y + 4)], fill=HACK_CODE, width=1)
        draw.point((x, y), fill=HACK_CODE_DIM)


# ===================================================================
# MECHPILOT (ID 59) -- bulky mech suit armor, large pauldrons, visor
#                       helmet, heavy boots, jetpack exhaust on back
# ===================================================================

def draw_mechpilot(draw, ox, oy, direction, frame):
    bob = [0, -2, 0, -1][frame]
    leg_spread = [-4, 0, 4, 0][frame]

    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 20
    head_cy = body_cy - 20

    armor_shadow = _darken(MECH_ARMOR, 0.7)

    # --- Legs (heavy, armored boots) ---
    if direction in (DOWN, UP):
        for side in [-1, 1]:
            ls = leg_spread if side == -1 else -leg_spread
            lx = cx + side * 7 + ls
            # Upper leg armor
            draw.rectangle([lx - 4, body_cy + 10, lx + 4, body_cy + 18],
                           fill=MECH_ARMOR, outline=OUTLINE)
            draw.rectangle([lx - 4, body_cy + 10, lx - 2, body_cy + 16],
                           fill=MECH_ARMOR_LIGHT, outline=None)
            # Lower leg
            draw.rectangle([lx - 3, body_cy + 16, lx + 3, base_y - 4],
                           fill=MECH_ARMOR_DARK, outline=OUTLINE)
            # Heavy boot
            draw.rectangle([lx - 5, base_y - 6, lx + 5, base_y],
                           fill=MECH_BOOT, outline=OUTLINE)
            draw.line([(lx - 5, base_y - 1), (lx + 5, base_y - 1)],
                      fill=_darken(MECH_BOOT, 0.7), width=1)
            # Red accent on knee
            draw.point((lx, body_cy + 14), fill=MECH_RED)
    elif direction == LEFT:
        for offset in [leg_spread, -leg_spread]:
            lx = cx - 3 + offset
            draw.rectangle([lx - 4, body_cy + 10, lx + 4, body_cy + 18],
                           fill=MECH_ARMOR, outline=OUTLINE)
            draw.rectangle([lx - 3, body_cy + 16, lx + 3, base_y - 4],
                           fill=MECH_ARMOR_DARK, outline=OUTLINE)
            draw.rectangle([lx - 5, base_y - 6, lx + 5, base_y],
                           fill=MECH_BOOT, outline=OUTLINE)
    else:  # RIGHT
        for offset in [leg_spread, -leg_spread]:
            lx = cx + 3 + offset
            draw.rectangle([lx - 4, body_cy + 10, lx + 4, body_cy + 18],
                           fill=MECH_ARMOR, outline=OUTLINE)
            draw.rectangle([lx - 3, body_cy + 16, lx + 3, base_y - 4],
                           fill=MECH_ARMOR_DARK, outline=OUTLINE)
            draw.rectangle([lx - 5, base_y - 6, lx + 5, base_y],
                           fill=MECH_BOOT, outline=OUTLINE)

    # --- Jetpack (behind body, visible on UP/side views) ---
    if direction == UP:
        # Jetpack rectangle on back
        draw.rectangle([cx - 8, body_cy - 10, cx + 8, body_cy + 6],
                       fill=MECH_EXHAUST, outline=OUTLINE)
        draw.rectangle([cx - 6, body_cy - 8, cx + 6, body_cy + 4],
                       fill=_brighten(MECH_EXHAUST, 1.2), outline=None)
        # Exhaust nozzles
        draw.rectangle([cx - 6, body_cy + 4, cx - 2, body_cy + 10],
                       fill=MECH_ARMOR_DARK, outline=OUTLINE)
        draw.rectangle([cx + 2, body_cy + 4, cx + 6, body_cy + 10],
                       fill=MECH_ARMOR_DARK, outline=OUTLINE)
        # Exhaust glow
        ellipse(draw, cx - 4, body_cy + 12, 3, 3, MECH_EXHAUST_GLOW, outline=None)
        ellipse(draw, cx + 4, body_cy + 12, 3, 3, MECH_EXHAUST_GLOW, outline=None)
    elif direction == LEFT:
        # Jetpack visible on right side
        draw.rectangle([cx + 6, body_cy - 8, cx + 14, body_cy + 4],
                       fill=MECH_EXHAUST, outline=OUTLINE)
        draw.rectangle([cx + 8, body_cy + 2, cx + 12, body_cy + 8],
                       fill=MECH_ARMOR_DARK, outline=OUTLINE)
        ellipse(draw, cx + 10, body_cy + 10, 3, 3, MECH_EXHAUST_GLOW, outline=None)
    elif direction == RIGHT:
        # Jetpack visible on left side
        draw.rectangle([cx - 14, body_cy - 8, cx - 6, body_cy + 4],
                       fill=MECH_EXHAUST, outline=OUTLINE)
        draw.rectangle([cx - 12, body_cy + 2, cx - 8, body_cy + 8],
                       fill=MECH_ARMOR_DARK, outline=OUTLINE)
        ellipse(draw, cx - 10, body_cy + 10, 3, 3, MECH_EXHAUST_GLOW, outline=None)

    # --- Body (bulky mech armor) ---
    if direction == DOWN:
        ellipse(draw, cx, body_cy, 16, 14, MECH_ARMOR)
        ellipse(draw, cx + 4, body_cy + 3, 12, 10, armor_shadow, outline=None)
        ellipse(draw, cx, body_cy, 13, 11, MECH_ARMOR, outline=None)
        ellipse(draw, cx - 3, body_cy - 3, 8, 6, MECH_ARMOR_LIGHT, outline=None)
        # Chest plate
        draw.rectangle([cx - 8, body_cy - 4, cx + 8, body_cy + 4],
                       fill=MECH_ARMOR_LIGHT, outline=None)
        draw.rectangle([cx - 6, body_cy - 2, cx + 6, body_cy + 2],
                       fill=MECH_ARMOR, outline=None)
        # Red chest light
        ellipse(draw, cx, body_cy, 2, 2, MECH_RED)
        draw.point((cx - 1, body_cy - 1), fill=MECH_RED_BRIGHT)
        # Belt
        draw.rectangle([cx - 16, body_cy + 8, cx + 16, body_cy + 14],
                       fill=MECH_ARMOR_DARK, outline=OUTLINE)
    elif direction == UP:
        ellipse(draw, cx, body_cy, 16, 14, MECH_ARMOR)
        ellipse(draw, cx, body_cy, 13, 11, armor_shadow, outline=None)
        draw.rectangle([cx - 16, body_cy + 8, cx + 16, body_cy + 14],
                       fill=MECH_ARMOR_DARK, outline=OUTLINE)
    elif direction == LEFT:
        ellipse(draw, cx - 2, body_cy, 14, 14, MECH_ARMOR)
        ellipse(draw, cx + 2, body_cy + 2, 10, 10, armor_shadow, outline=None)
        ellipse(draw, cx - 4, body_cy - 2, 8, 8, MECH_ARMOR_LIGHT, outline=None)
        ellipse(draw, cx - 6, body_cy, 2, 2, MECH_RED, outline=None)
        draw.rectangle([cx - 16, body_cy + 8, cx + 12, body_cy + 14],
                       fill=MECH_ARMOR_DARK, outline=OUTLINE)
    else:  # RIGHT
        ellipse(draw, cx + 2, body_cy, 14, 14, MECH_ARMOR)
        ellipse(draw, cx + 6, body_cy + 2, 10, 10, armor_shadow, outline=None)
        ellipse(draw, cx, body_cy - 2, 8, 8, MECH_ARMOR_LIGHT, outline=None)
        ellipse(draw, cx + 6, body_cy, 2, 2, MECH_RED, outline=None)
        draw.rectangle([cx - 12, body_cy + 8, cx + 16, body_cy + 14],
                       fill=MECH_ARMOR_DARK, outline=OUTLINE)

    # --- Arms with large pauldrons ---
    if direction == DOWN:
        # Left arm
        draw.rectangle([cx - 20, body_cy - 6, cx - 14, body_cy + 6],
                       fill=MECH_ARMOR, outline=OUTLINE)
        draw.rectangle([cx - 20, body_cy + 2, cx - 14, body_cy + 6],
                       fill=MECH_ARMOR_DARK, outline=OUTLINE)
        # Left pauldron (large)
        ellipse(draw, cx - 17, body_cy - 8, 8, 5, MECH_PAULDRON)
        ellipse(draw, cx - 18, body_cy - 9, 5, 3, MECH_ARMOR_LIGHT, outline=None)
        draw.point((cx - 17, body_cy - 8), fill=MECH_RED)
        # Right arm
        draw.rectangle([cx + 14, body_cy - 6, cx + 20, body_cy + 6],
                       fill=MECH_ARMOR, outline=OUTLINE)
        draw.rectangle([cx + 14, body_cy + 2, cx + 20, body_cy + 6],
                       fill=MECH_ARMOR_DARK, outline=OUTLINE)
        # Right pauldron (large)
        ellipse(draw, cx + 17, body_cy - 8, 8, 5, MECH_PAULDRON)
        ellipse(draw, cx + 16, body_cy - 9, 5, 3, MECH_ARMOR_LIGHT, outline=None)
        draw.point((cx + 17, body_cy - 8), fill=MECH_RED)
    elif direction == UP:
        draw.rectangle([cx - 20, body_cy - 6, cx - 14, body_cy + 6],
                       fill=MECH_ARMOR, outline=OUTLINE)
        ellipse(draw, cx - 17, body_cy - 8, 8, 5, MECH_PAULDRON)
        draw.rectangle([cx + 14, body_cy - 6, cx + 20, body_cy + 6],
                       fill=MECH_ARMOR, outline=OUTLINE)
        ellipse(draw, cx + 17, body_cy - 8, 8, 5, MECH_PAULDRON)
    elif direction == LEFT:
        draw.rectangle([cx - 16, body_cy - 4, cx - 10, body_cy + 6],
                       fill=MECH_ARMOR, outline=OUTLINE)
        draw.rectangle([cx - 16, body_cy + 2, cx - 10, body_cy + 6],
                       fill=MECH_ARMOR_DARK, outline=OUTLINE)
        ellipse(draw, cx - 13, body_cy - 6, 7, 5, MECH_PAULDRON)
        ellipse(draw, cx - 14, body_cy - 7, 4, 3, MECH_ARMOR_LIGHT, outline=None)
        draw.point((cx - 13, body_cy - 6), fill=MECH_RED)
    else:  # RIGHT
        draw.rectangle([cx + 10, body_cy - 4, cx + 16, body_cy + 6],
                       fill=MECH_ARMOR, outline=OUTLINE)
        draw.rectangle([cx + 10, body_cy + 2, cx + 16, body_cy + 6],
                       fill=MECH_ARMOR_DARK, outline=OUTLINE)
        ellipse(draw, cx + 13, body_cy - 6, 7, 5, MECH_PAULDRON)
        ellipse(draw, cx + 12, body_cy - 7, 4, 3, MECH_ARMOR_LIGHT, outline=None)
        draw.point((cx + 13, body_cy - 6), fill=MECH_RED)

    # --- Head (visor helmet) ---
    if direction == DOWN:
        # Helmet dome
        ellipse(draw, cx, head_cy, 14, 12, MECH_ARMOR)
        ellipse(draw, cx - 2, head_cy - 3, 8, 6, MECH_ARMOR_LIGHT, outline=None)
        # Visor slit
        draw.rectangle([cx - 10, head_cy + 1, cx + 10, head_cy + 6],
                       fill=MECH_VISOR_DARK, outline=OUTLINE)
        draw.rectangle([cx - 8, head_cy + 2, cx + 8, head_cy + 5],
                       fill=MECH_VISOR, outline=None)
        # Visor glare
        draw.line([(cx - 6, head_cy + 3), (cx - 2, head_cy + 3)],
                  fill=(180, 230, 250), width=1)
        # Chin guard
        draw.rectangle([cx - 6, head_cy + 6, cx + 6, head_cy + 10],
                       fill=MECH_ARMOR_DARK, outline=OUTLINE)
        # Helmet crest
        draw.rectangle([cx - 2, head_cy - 14, cx + 2, head_cy - 6],
                       fill=MECH_RED, outline=OUTLINE)
    elif direction == UP:
        ellipse(draw, cx, head_cy, 14, 12, MECH_ARMOR)
        ellipse(draw, cx, head_cy, 11, 9, armor_shadow, outline=None)
        draw.rectangle([cx - 2, head_cy - 14, cx + 2, head_cy - 6],
                       fill=MECH_RED, outline=OUTLINE)
        # Back vent
        draw.rectangle([cx - 4, head_cy + 4, cx + 4, head_cy + 8],
                       fill=MECH_ARMOR_DARK, outline=OUTLINE)
    elif direction == LEFT:
        ellipse(draw, cx - 2, head_cy, 13, 12, MECH_ARMOR)
        ellipse(draw, cx - 4, head_cy - 3, 7, 5, MECH_ARMOR_LIGHT, outline=None)
        # Side visor
        draw.rectangle([cx - 12, head_cy + 1, cx - 2, head_cy + 6],
                       fill=MECH_VISOR_DARK, outline=OUTLINE)
        draw.rectangle([cx - 11, head_cy + 2, cx - 3, head_cy + 5],
                       fill=MECH_VISOR, outline=None)
        draw.line([(cx - 10, head_cy + 3), (cx - 6, head_cy + 3)],
                  fill=(180, 230, 250), width=1)
        # Chin
        draw.rectangle([cx - 8, head_cy + 6, cx - 2, head_cy + 10],
                       fill=MECH_ARMOR_DARK, outline=OUTLINE)
        draw.rectangle([cx - 4, head_cy - 14, cx, head_cy - 6],
                       fill=MECH_RED, outline=OUTLINE)
    else:  # RIGHT
        ellipse(draw, cx + 2, head_cy, 13, 12, MECH_ARMOR)
        ellipse(draw, cx, head_cy - 3, 7, 5, MECH_ARMOR_LIGHT, outline=None)
        # Side visor
        draw.rectangle([cx + 2, head_cy + 1, cx + 12, head_cy + 6],
                       fill=MECH_VISOR_DARK, outline=OUTLINE)
        draw.rectangle([cx + 3, head_cy + 2, cx + 11, head_cy + 5],
                       fill=MECH_VISOR, outline=None)
        draw.line([(cx + 4, head_cy + 3), (cx + 8, head_cy + 3)],
                  fill=(180, 230, 250), width=1)
        # Chin
        draw.rectangle([cx + 2, head_cy + 6, cx + 8, head_cy + 10],
                       fill=MECH_ARMOR_DARK, outline=OUTLINE)
        draw.rectangle([cx, head_cy - 14, cx + 4, head_cy - 6],
                       fill=MECH_RED, outline=OUTLINE)


# ===================================================================
# ANDROID (ID 60) -- smooth white/silver body, visible joint segments,
#                    glowing blue eye strips, panel seam lines, no skin
# ===================================================================

def draw_android(draw, ox, oy, direction, frame):
    bob = [0, -2, 0, -1][frame]
    leg_spread = [-4, 0, 4, 0][frame]

    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 20
    head_cy = body_cy - 20

    body_shadow = _darken(AND_BODY, 0.8)

    # --- Legs (segmented with joint ring at knee) ---
    # Leg space: body_cy+10 to base_y (range of 8-10px depending on bob)
    if direction in (DOWN, UP):
        for side in [-1, 1]:
            ls = leg_spread if side == -1 else -leg_spread
            lx = cx + side * 7 + ls
            # Full leg segment
            draw.rectangle([lx - 3, body_cy + 10, lx + 3, base_y],
                           fill=AND_BODY, outline=OUTLINE)
            # Highlight stripe
            draw.rectangle([lx - 3, body_cy + 10, lx - 1, base_y - 4],
                           fill=AND_BODY_LIGHT, outline=None)
            # Joint ring at knee (midpoint)
            knee_y = (body_cy + 10 + base_y) // 2
            draw.rectangle([lx - 4, knee_y - 1, lx + 4, knee_y + 2],
                           fill=AND_JOINT, outline=OUTLINE)
            # Ankle joint
            draw.rectangle([lx - 4, base_y - 4, lx + 4, base_y],
                           fill=AND_JOINT, outline=OUTLINE)
            # Seam line on upper leg
            draw.line([(lx, body_cy + 11), (lx, knee_y - 2)],
                      fill=AND_SEAM, width=1)
    elif direction == LEFT:
        for offset in [leg_spread, -leg_spread]:
            lx = cx - 3 + offset
            draw.rectangle([lx - 3, body_cy + 10, lx + 3, base_y],
                           fill=AND_BODY, outline=OUTLINE)
            knee_y = (body_cy + 10 + base_y) // 2
            draw.rectangle([lx - 4, knee_y - 1, lx + 4, knee_y + 2],
                           fill=AND_JOINT, outline=OUTLINE)
            draw.rectangle([lx - 4, base_y - 4, lx + 4, base_y],
                           fill=AND_JOINT, outline=OUTLINE)
    else:  # RIGHT
        for offset in [leg_spread, -leg_spread]:
            lx = cx + 3 + offset
            draw.rectangle([lx - 3, body_cy + 10, lx + 3, base_y],
                           fill=AND_BODY, outline=OUTLINE)
            knee_y = (body_cy + 10 + base_y) // 2
            draw.rectangle([lx - 4, knee_y - 1, lx + 4, knee_y + 2],
                           fill=AND_JOINT, outline=OUTLINE)
            draw.rectangle([lx - 4, base_y - 4, lx + 4, base_y],
                           fill=AND_JOINT, outline=OUTLINE)

    # --- Body (smooth, paneled) ---
    if direction == DOWN:
        ellipse(draw, cx, body_cy, 14, 12, AND_BODY)
        ellipse(draw, cx + 3, body_cy + 2, 10, 8, body_shadow, outline=None)
        ellipse(draw, cx, body_cy, 11, 9, AND_BODY, outline=None)
        ellipse(draw, cx - 3, body_cy - 2, 8, 6, AND_BODY_LIGHT, outline=None)
        # Panel seam lines
        draw.line([(cx - 2, body_cy - 10), (cx - 2, body_cy + 8)],
                  fill=AND_SEAM, width=1)
        draw.line([(cx + 2, body_cy - 10), (cx + 2, body_cy + 8)],
                  fill=AND_SEAM, width=1)
        # Core light
        ellipse(draw, cx, body_cy + 2, 3, 3, AND_CORE, outline=None)
        draw.point((cx - 1, body_cy + 1), fill=AND_EYE_BRIGHT)
        # Belt joint
        draw.rectangle([cx - 14, body_cy + 8, cx + 14, body_cy + 12],
                       fill=AND_JOINT, outline=OUTLINE)
    elif direction == UP:
        ellipse(draw, cx, body_cy, 14, 12, AND_BODY)
        ellipse(draw, cx, body_cy, 11, 9, body_shadow, outline=None)
        # Back panel seam
        draw.line([(cx, body_cy - 8), (cx, body_cy + 6)],
                  fill=AND_SEAM, width=1)
        draw.line([(cx - 6, body_cy - 4), (cx - 6, body_cy + 4)],
                  fill=AND_SEAM, width=1)
        draw.line([(cx + 6, body_cy - 4), (cx + 6, body_cy + 4)],
                  fill=AND_SEAM, width=1)
        draw.rectangle([cx - 14, body_cy + 8, cx + 14, body_cy + 12],
                       fill=AND_JOINT, outline=OUTLINE)
    elif direction == LEFT:
        ellipse(draw, cx - 2, body_cy, 12, 12, AND_BODY)
        ellipse(draw, cx + 1, body_cy + 2, 8, 8, body_shadow, outline=None)
        ellipse(draw, cx - 4, body_cy - 2, 6, 6, AND_BODY_LIGHT, outline=None)
        draw.line([(cx - 4, body_cy - 8), (cx - 4, body_cy + 6)],
                  fill=AND_SEAM, width=1)
        ellipse(draw, cx - 6, body_cy + 2, 2, 2, AND_CORE, outline=None)
        draw.rectangle([cx - 14, body_cy + 8, cx + 10, body_cy + 12],
                       fill=AND_JOINT, outline=OUTLINE)
    else:  # RIGHT
        ellipse(draw, cx + 2, body_cy, 12, 12, AND_BODY)
        ellipse(draw, cx + 5, body_cy + 2, 8, 8, body_shadow, outline=None)
        ellipse(draw, cx, body_cy - 2, 6, 6, AND_BODY_LIGHT, outline=None)
        draw.line([(cx + 4, body_cy - 8), (cx + 4, body_cy + 6)],
                  fill=AND_SEAM, width=1)
        ellipse(draw, cx + 6, body_cy + 2, 2, 2, AND_CORE, outline=None)
        draw.rectangle([cx - 10, body_cy + 8, cx + 14, body_cy + 12],
                       fill=AND_JOINT, outline=OUTLINE)

    # --- Arms (segmented) ---
    if direction == DOWN:
        for side in [-1, 1]:
            ax = cx + side * 15
            # Upper arm
            draw.rectangle([ax - 3, body_cy - 6, ax + 3, body_cy],
                           fill=AND_BODY, outline=OUTLINE)
            # Joint
            draw.rectangle([ax - 4, body_cy - 1, ax + 4, body_cy + 2],
                           fill=AND_JOINT, outline=OUTLINE)
            # Lower arm
            draw.rectangle([ax - 3, body_cy + 2, ax + 3, body_cy + 6],
                           fill=AND_BODY, outline=OUTLINE)
            # Shoulder joint
            ellipse(draw, ax, body_cy - 6, 5, 3, AND_JOINT)
            # Seam line
            draw.line([(ax, body_cy - 5), (ax, body_cy - 2)],
                      fill=AND_SEAM, width=1)
    elif direction == UP:
        for side in [-1, 1]:
            ax = cx + side * 15
            draw.rectangle([ax - 3, body_cy - 6, ax + 3, body_cy + 6],
                           fill=AND_BODY, outline=OUTLINE)
            draw.rectangle([ax - 4, body_cy - 1, ax + 4, body_cy + 2],
                           fill=AND_JOINT, outline=OUTLINE)
            ellipse(draw, ax, body_cy - 6, 5, 3, AND_JOINT)
    elif direction == LEFT:
        draw.rectangle([cx - 14, body_cy - 4, cx - 8, body_cy + 6],
                       fill=AND_BODY, outline=OUTLINE)
        draw.rectangle([cx - 15, body_cy, cx - 7, body_cy + 3],
                       fill=AND_JOINT, outline=OUTLINE)
        ellipse(draw, cx - 11, body_cy - 6, 5, 3, AND_JOINT)
    else:  # RIGHT
        draw.rectangle([cx + 8, body_cy - 4, cx + 14, body_cy + 6],
                       fill=AND_BODY, outline=OUTLINE)
        draw.rectangle([cx + 7, body_cy, cx + 15, body_cy + 3],
                       fill=AND_JOINT, outline=OUTLINE)
        ellipse(draw, cx + 11, body_cy - 6, 5, 3, AND_JOINT)

    # --- Head (smooth, eye strip instead of normal eyes) ---
    if direction == DOWN:
        ellipse(draw, cx, head_cy, 14, 12, AND_BODY)
        ellipse(draw, cx - 2, head_cy - 3, 8, 6, AND_BODY_LIGHT, outline=None)
        # Panel seams
        draw.line([(cx - 4, head_cy - 10), (cx - 4, head_cy + 4)],
                  fill=AND_SEAM, width=1)
        draw.line([(cx + 4, head_cy - 10), (cx + 4, head_cy + 4)],
                  fill=AND_SEAM, width=1)
        # Blue eye strip (horizontal bar across face)
        draw.rectangle([cx - 10, head_cy + 1, cx + 10, head_cy + 5],
                       fill=AND_EYE_STRIP, outline=OUTLINE)
        # Brighter inner eye areas
        draw.rectangle([cx - 8, head_cy + 2, cx - 4, head_cy + 4],
                       fill=AND_EYE_BRIGHT, outline=None)
        draw.rectangle([cx + 4, head_cy + 2, cx + 8, head_cy + 4],
                       fill=AND_EYE_BRIGHT, outline=None)
        # Chin line
        draw.line([(cx - 4, head_cy + 8), (cx + 4, head_cy + 8)],
                  fill=AND_SEAM, width=1)
    elif direction == UP:
        ellipse(draw, cx, head_cy, 14, 12, AND_BODY)
        ellipse(draw, cx, head_cy, 10, 8, body_shadow, outline=None)
        # Panel seams on back
        draw.line([(cx, head_cy - 8), (cx, head_cy + 6)],
                  fill=AND_SEAM, width=1)
        draw.line([(cx - 6, head_cy - 4), (cx - 6, head_cy + 4)],
                  fill=AND_SEAM, width=1)
        draw.line([(cx + 6, head_cy - 4), (cx + 6, head_cy + 4)],
                  fill=AND_SEAM, width=1)
    elif direction == LEFT:
        ellipse(draw, cx - 2, head_cy, 13, 12, AND_BODY)
        ellipse(draw, cx - 4, head_cy - 3, 7, 5, AND_BODY_LIGHT, outline=None)
        # Side eye strip
        draw.rectangle([cx - 12, head_cy + 1, cx - 2, head_cy + 5],
                       fill=AND_EYE_STRIP, outline=OUTLINE)
        draw.rectangle([cx - 10, head_cy + 2, cx - 4, head_cy + 4],
                       fill=AND_EYE_BRIGHT, outline=None)
        # Panel seam
        draw.line([(cx - 4, head_cy - 8), (cx - 4, head_cy + 4)],
                  fill=AND_SEAM, width=1)
        # Chin
        draw.line([(cx - 6, head_cy + 8), (cx - 2, head_cy + 8)],
                  fill=AND_SEAM, width=1)
    else:  # RIGHT
        ellipse(draw, cx + 2, head_cy, 13, 12, AND_BODY)
        ellipse(draw, cx, head_cy - 3, 7, 5, AND_BODY_LIGHT, outline=None)
        # Side eye strip
        draw.rectangle([cx + 2, head_cy + 1, cx + 12, head_cy + 5],
                       fill=AND_EYE_STRIP, outline=OUTLINE)
        draw.rectangle([cx + 4, head_cy + 2, cx + 10, head_cy + 4],
                       fill=AND_EYE_BRIGHT, outline=None)
        # Panel seam
        draw.line([(cx + 4, head_cy - 8), (cx + 4, head_cy + 4)],
                  fill=AND_SEAM, width=1)
        # Chin
        draw.line([(cx + 2, head_cy + 8), (cx + 6, head_cy + 8)],
                  fill=AND_SEAM, width=1)


# ===================================================================
# CHRONOMANCER (ID 61) -- purple robes with clock motifs, floating
#                          clock gears near head, hourglass staff,
#                          golden time symbols, temporal glow
# ===================================================================

def draw_chronomancer(draw, ox, oy, direction, frame):
    bob = [0, -2, 0, -1][frame]
    robe_sway = [-2, 0, 2, 0][frame]
    gear_angle = frame * (math.pi / 2)

    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 20
    head_cy = body_cy - 18

    robe_shadow = _darken(CHRONO_ROBE, 0.7)

    # --- Staff (hourglass on top) ---
    if direction == DOWN:
        staff_x = cx + 14
        draw.line([(staff_x, head_cy - 8), (staff_x, base_y + 2)],
                  fill=CHRONO_STAFF, width=2)
        # Hourglass shape at top
        draw.polygon([(staff_x - 3, head_cy - 16), (staff_x + 3, head_cy - 16),
                      (staff_x, head_cy - 12)], fill=CHRONO_GOLD)
        draw.polygon([(staff_x, head_cy - 12), (staff_x - 3, head_cy - 8),
                      (staff_x + 3, head_cy - 8)], fill=CHRONO_GOLD)
        draw.point((staff_x, head_cy - 12), fill=CHRONO_GOLD_BRIGHT)
        draw.line([(staff_x - 3, head_cy - 16), (staff_x + 3, head_cy - 16)],
                  fill=OUTLINE, width=1)
        draw.line([(staff_x - 3, head_cy - 8), (staff_x + 3, head_cy - 8)],
                  fill=OUTLINE, width=1)
    elif direction == UP:
        staff_x = cx - 14
        draw.line([(staff_x, head_cy - 8), (staff_x, base_y + 2)],
                  fill=CHRONO_STAFF, width=2)
        draw.polygon([(staff_x - 3, head_cy - 16), (staff_x + 3, head_cy - 16),
                      (staff_x, head_cy - 12)], fill=CHRONO_GOLD)
        draw.polygon([(staff_x, head_cy - 12), (staff_x - 3, head_cy - 8),
                      (staff_x + 3, head_cy - 8)], fill=CHRONO_GOLD)
    elif direction == LEFT:
        staff_x = cx + 12
        draw.line([(staff_x, head_cy - 8), (staff_x, base_y + 2)],
                  fill=CHRONO_STAFF, width=2)
        draw.polygon([(staff_x - 3, head_cy - 16), (staff_x + 3, head_cy - 16),
                      (staff_x, head_cy - 12)], fill=CHRONO_GOLD)
        draw.polygon([(staff_x, head_cy - 12), (staff_x - 3, head_cy - 8),
                      (staff_x + 3, head_cy - 8)], fill=CHRONO_GOLD)
        draw.point((staff_x, head_cy - 12), fill=CHRONO_GOLD_BRIGHT)
    else:  # RIGHT
        staff_x = cx - 12
        draw.line([(staff_x, head_cy - 8), (staff_x, base_y + 2)],
                  fill=CHRONO_STAFF, width=2)
        draw.polygon([(staff_x - 3, head_cy - 16), (staff_x + 3, head_cy - 16),
                      (staff_x, head_cy - 12)], fill=CHRONO_GOLD)
        draw.polygon([(staff_x, head_cy - 12), (staff_x - 3, head_cy - 8),
                      (staff_x + 3, head_cy - 8)], fill=CHRONO_GOLD)
        draw.point((staff_x, head_cy - 12), fill=CHRONO_GOLD_BRIGHT)

    # --- Robe body (wide, flowing, purple) ---
    if direction == DOWN:
        draw.polygon([
            (cx - 14, body_cy - 10),
            (cx + 14, body_cy - 10),
            (cx + 18 + robe_sway, base_y + 2),
            (cx - 18 + robe_sway, base_y + 2),
        ], fill=CHRONO_ROBE, outline=OUTLINE)
        draw.line([(cx - 4, body_cy - 6), (cx - 6 + robe_sway, base_y)],
                  fill=CHRONO_ROBE_DARK, width=1)
        draw.line([(cx + 4, body_cy - 6), (cx + 6 + robe_sway, base_y)],
                  fill=CHRONO_ROBE_DARK, width=1)
        draw.line([(cx - 10, body_cy - 4), (cx - 14 + robe_sway, base_y)],
                  fill=CHRONO_ROBE_LIGHT, width=1)
        # Clock motifs on robe (golden dots in circle pattern)
        for i in range(4):
            a = i * (math.pi / 2) + 0.3
            mx = int(cx + math.cos(a) * 6)
            my = int(body_cy + math.sin(a) * 5)
            draw.point((mx, my), fill=CHRONO_GOLD)
        # Gold trim at hem
        draw.line([(cx - 16 + robe_sway, base_y), (cx + 16 + robe_sway, base_y)],
                  fill=CHRONO_GOLD, width=1)
    elif direction == UP:
        draw.polygon([
            (cx - 14, body_cy - 10),
            (cx + 14, body_cy - 10),
            (cx + 18 + robe_sway, base_y + 2),
            (cx - 18 + robe_sway, base_y + 2),
        ], fill=CHRONO_ROBE, outline=OUTLINE)
        draw.line([(cx, body_cy - 6), (cx + robe_sway, base_y)],
                  fill=CHRONO_ROBE_DARK, width=1)
        draw.line([(cx - 8, body_cy - 4), (cx - 10 + robe_sway, base_y)],
                  fill=CHRONO_ROBE_DARK, width=1)
        draw.line([(cx + 8, body_cy - 4), (cx + 10 + robe_sway, base_y)],
                  fill=CHRONO_ROBE_DARK, width=1)
        draw.line([(cx - 16 + robe_sway, base_y), (cx + 16 + robe_sway, base_y)],
                  fill=CHRONO_GOLD, width=1)
    elif direction == LEFT:
        draw.polygon([
            (cx - 10, body_cy - 10),
            (cx + 8, body_cy - 10),
            (cx + 12 + robe_sway, base_y + 2),
            (cx - 14 + robe_sway, base_y + 2),
        ], fill=CHRONO_ROBE, outline=OUTLINE)
        draw.line([(cx - 2, body_cy - 6), (cx - 4 + robe_sway, base_y)],
                  fill=CHRONO_ROBE_DARK, width=1)
        draw.line([(cx - 8, body_cy - 4), (cx - 10 + robe_sway, base_y)],
                  fill=CHRONO_ROBE_LIGHT, width=1)
        draw.line([(cx - 12 + robe_sway, base_y), (cx + 10 + robe_sway, base_y)],
                  fill=CHRONO_GOLD, width=1)
    else:  # RIGHT
        draw.polygon([
            (cx - 8, body_cy - 10),
            (cx + 10, body_cy - 10),
            (cx + 14 + robe_sway, base_y + 2),
            (cx - 12 + robe_sway, base_y + 2),
        ], fill=CHRONO_ROBE, outline=OUTLINE)
        draw.line([(cx + 2, body_cy - 6), (cx + 4 + robe_sway, base_y)],
                  fill=CHRONO_ROBE_DARK, width=1)
        draw.line([(cx + 8, body_cy - 4), (cx + 10 + robe_sway, base_y)],
                  fill=CHRONO_ROBE_LIGHT, width=1)
        draw.line([(cx - 10 + robe_sway, base_y), (cx + 12 + robe_sway, base_y)],
                  fill=CHRONO_GOLD, width=1)

    # --- Hands (from sleeves) ---
    if direction == DOWN:
        for side in [-1, 1]:
            hx = cx + side * 16
            draw.rectangle([hx - 2, body_cy + 2, hx + 2, body_cy + 6],
                           fill=CHRONO_SKIN, outline=OUTLINE)
    elif direction in (LEFT, RIGHT):
        d = -1 if direction == LEFT else 1
        hx = cx + d * (-12)
        draw.rectangle([hx - 2, body_cy + 2, hx + 2, body_cy + 6],
                       fill=CHRONO_SKIN, outline=OUTLINE)

    # --- Head with pointed hat ---
    if direction == DOWN:
        ellipse(draw, cx, head_cy, 12, 10, CHRONO_SKIN)
        ellipse(draw, cx - 2, head_cy - 2, 8, 6, _brighten(CHRONO_SKIN, 1.1), outline=None)
        # Face
        ellipse(draw, cx, head_cy + 4, 8, 6, CHRONO_SKIN, outline=None)
        # Eyes
        draw.rectangle([cx - 5, head_cy + 2, cx - 3, head_cy + 4], fill=BLACK)
        draw.rectangle([cx + 3, head_cy + 2, cx + 5, head_cy + 4], fill=BLACK)
        draw.point((cx, head_cy + 6), fill=CHRONO_SKIN_DARK)
        # Pointed hat (purple with gold band)
        ellipse(draw, cx, head_cy - 6, 14, 4, CHRONO_ROBE)
        draw.polygon([(cx, head_cy - 26), (cx - 8, head_cy - 8),
                      (cx + 8, head_cy - 8)], fill=CHRONO_ROBE, outline=OUTLINE)
        draw.polygon([(cx + 2, head_cy - 22), (cx + 8, head_cy - 8),
                      (cx + 5, head_cy - 8)], fill=CHRONO_ROBE_DARK, outline=None)
        # Gold band
        draw.rectangle([cx - 8, head_cy - 10, cx + 8, head_cy - 6],
                       fill=CHRONO_GOLD, outline=None)
        # Clock symbol on hat
        draw.arc([cx - 3, head_cy - 18, cx + 3, head_cy - 12],
                 start=0, end=360, fill=CHRONO_GOLD_BRIGHT, width=1)
        draw.line([(cx, head_cy - 15), (cx + 1, head_cy - 13)],
                  fill=CHRONO_GOLD_BRIGHT, width=1)
    elif direction == UP:
        ellipse(draw, cx, head_cy, 12, 10, CHRONO_ROBE_DARK)
        ellipse(draw, cx, head_cy - 6, 14, 4, CHRONO_ROBE)
        draw.polygon([(cx, head_cy - 26), (cx - 8, head_cy - 8),
                      (cx + 8, head_cy - 8)], fill=CHRONO_ROBE, outline=OUTLINE)
        draw.rectangle([cx - 8, head_cy - 10, cx + 8, head_cy - 6],
                       fill=CHRONO_GOLD, outline=None)
    elif direction == LEFT:
        ellipse(draw, cx - 2, head_cy, 11, 10, CHRONO_SKIN)
        ellipse(draw, cx - 4, head_cy - 2, 6, 5, _brighten(CHRONO_SKIN, 1.1), outline=None)
        ellipse(draw, cx - 4, head_cy + 4, 6, 5, CHRONO_SKIN, outline=None)
        draw.rectangle([cx - 7, head_cy + 2, cx - 5, head_cy + 4], fill=BLACK)
        draw.point((cx - 8, head_cy + 6), fill=CHRONO_SKIN_DARK)
        ellipse(draw, cx - 2, head_cy - 6, 12, 4, CHRONO_ROBE)
        draw.polygon([(cx - 4, head_cy - 26), (cx - 10, head_cy - 8),
                      (cx + 4, head_cy - 8)], fill=CHRONO_ROBE, outline=OUTLINE)
        draw.rectangle([cx - 10, head_cy - 10, cx + 4, head_cy - 6],
                       fill=CHRONO_GOLD, outline=None)
    else:  # RIGHT
        ellipse(draw, cx + 2, head_cy, 11, 10, CHRONO_SKIN)
        ellipse(draw, cx, head_cy - 2, 6, 5, _brighten(CHRONO_SKIN, 1.1), outline=None)
        ellipse(draw, cx + 4, head_cy + 4, 6, 5, CHRONO_SKIN, outline=None)
        draw.rectangle([cx + 5, head_cy + 2, cx + 7, head_cy + 4], fill=BLACK)
        draw.point((cx + 8, head_cy + 6), fill=CHRONO_SKIN_DARK)
        ellipse(draw, cx + 2, head_cy - 6, 12, 4, CHRONO_ROBE)
        draw.polygon([(cx + 4, head_cy - 26), (cx - 4, head_cy - 8),
                      (cx + 10, head_cy - 8)], fill=CHRONO_ROBE, outline=OUTLINE)
        draw.rectangle([cx - 4, head_cy - 10, cx + 10, head_cy - 6],
                       fill=CHRONO_GOLD, outline=None)

    # --- Floating clock gears (orbiting near head) ---
    gear_radius = 16
    for i in range(3):
        angle = gear_angle + i * (2 * math.pi / 3)
        gx = int(cx + math.cos(angle) * gear_radius)
        gy = int(head_cy - 4 + math.sin(angle) * (gear_radius * 0.4))
        # Gear body
        ellipse(draw, gx, gy, 3, 3, CHRONO_GEAR, outline=CHRONO_GEAR_DARK)
        # Gear teeth (tiny dots around)
        for t in range(4):
            ta = t * (math.pi / 2)
            tx = int(gx + math.cos(ta) * 3)
            ty = int(gy + math.sin(ta) * 3)
            draw.point((tx, ty), fill=CHRONO_GEAR_DARK)
        # Gear center
        draw.point((gx, gy), fill=CHRONO_GOLD_BRIGHT)


# ===================================================================
# GRAVITON (ID 62) -- dark purple robes, orbiting purple spheres,
#                     levitation (no ground contact), distortion lines
# ===================================================================

def draw_graviton(draw, ox, oy, direction, frame):
    # Float higher than normal, no ground contact
    float_bob = [-4, -6, -4, -5][frame]
    orb_angle_base = frame * (math.pi / 2)

    base_y = oy + 54 + float_bob
    cx = ox + 32
    body_cy = base_y - 20
    head_cy = body_cy - 18

    robe_shadow = _darken(GRAV_ROBE, 0.7)

    # --- Gravitational distortion lines below (ground shimmer) ---
    ground_y = oy + 54
    distort_offsets = [
        [(-10, 0), (-4, 2), (4, 0), (10, 2)],
        [(-12, 2), (-2, 0), (6, 2), (12, 0)],
        [(-8, 0), (-6, 2), (2, 0), (8, 2)],
        [(-14, 2), (0, 0), (8, 2), (14, 0)],
    ]
    for dx, dy in distort_offsets[frame]:
        x = cx + dx
        y = ground_y + dy
        draw.line([(x - 2, y), (x + 2, y)], fill=GRAV_DISTORT, width=1)

    # --- Robe body (flowing, wider at bottom) ---
    robe_sway = [-2, 0, 2, 0][frame]
    if direction == DOWN:
        draw.polygon([
            (cx - 14, body_cy - 10),
            (cx + 14, body_cy - 10),
            (cx + 20 + robe_sway, base_y + 6),
            (cx - 20 + robe_sway, base_y + 6),
        ], fill=GRAV_ROBE, outline=OUTLINE)
        draw.line([(cx - 4, body_cy - 6), (cx - 8 + robe_sway, base_y + 4)],
                  fill=GRAV_ROBE_DARK, width=1)
        draw.line([(cx + 4, body_cy - 6), (cx + 8 + robe_sway, base_y + 4)],
                  fill=GRAV_ROBE_DARK, width=1)
        draw.line([(cx - 10, body_cy - 4), (cx - 16 + robe_sway, base_y + 4)],
                  fill=GRAV_ROBE_LIGHT, width=1)
        # Wispy hem (jagged)
        for hx in range(cx - 18 + robe_sway, cx + 18 + robe_sway, 6):
            draw.polygon([(hx, base_y + 4), (hx + 3, base_y + 8), (hx + 6, base_y + 4)],
                         fill=GRAV_ROBE_DARK, outline=None)
    elif direction == UP:
        draw.polygon([
            (cx - 14, body_cy - 10),
            (cx + 14, body_cy - 10),
            (cx + 20 + robe_sway, base_y + 6),
            (cx - 20 + robe_sway, base_y + 6),
        ], fill=GRAV_ROBE, outline=OUTLINE)
        draw.line([(cx, body_cy - 6), (cx + robe_sway, base_y + 4)],
                  fill=GRAV_ROBE_DARK, width=1)
        draw.line([(cx - 8, body_cy - 4), (cx - 10 + robe_sway, base_y + 4)],
                  fill=GRAV_ROBE_DARK, width=1)
        draw.line([(cx + 8, body_cy - 4), (cx + 10 + robe_sway, base_y + 4)],
                  fill=GRAV_ROBE_DARK, width=1)
    elif direction == LEFT:
        draw.polygon([
            (cx - 10, body_cy - 10),
            (cx + 8, body_cy - 10),
            (cx + 14 + robe_sway, base_y + 6),
            (cx - 16 + robe_sway, base_y + 6),
        ], fill=GRAV_ROBE, outline=OUTLINE)
        draw.line([(cx - 2, body_cy - 6), (cx - 6 + robe_sway, base_y + 4)],
                  fill=GRAV_ROBE_DARK, width=1)
        draw.line([(cx - 8, body_cy - 4), (cx - 12 + robe_sway, base_y + 4)],
                  fill=GRAV_ROBE_LIGHT, width=1)
    else:  # RIGHT
        draw.polygon([
            (cx - 8, body_cy - 10),
            (cx + 10, body_cy - 10),
            (cx + 16 + robe_sway, base_y + 6),
            (cx - 14 + robe_sway, base_y + 6),
        ], fill=GRAV_ROBE, outline=OUTLINE)
        draw.line([(cx + 2, body_cy - 6), (cx + 6 + robe_sway, base_y + 4)],
                  fill=GRAV_ROBE_DARK, width=1)
        draw.line([(cx + 8, body_cy - 4), (cx + 12 + robe_sway, base_y + 4)],
                  fill=GRAV_ROBE_LIGHT, width=1)

    # --- Hands ---
    if direction == DOWN:
        for side in [-1, 1]:
            hx = cx + side * 16
            draw.rectangle([hx - 2, body_cy + 2, hx + 2, body_cy + 6],
                           fill=GRAV_SKIN, outline=OUTLINE)
    elif direction in (LEFT, RIGHT):
        d = -1 if direction == LEFT else 1
        hx = cx + d * (-12)
        draw.rectangle([hx - 2, body_cy + 2, hx + 2, body_cy + 6],
                       fill=GRAV_SKIN, outline=OUTLINE)

    # --- Head with hood ---
    if direction == DOWN:
        ellipse(draw, cx, head_cy, 12, 10, GRAV_ROBE)
        ellipse(draw, cx, head_cy + 2, 8, 6, GRAV_ROBE_DARK, outline=None)
        # Hood peak
        draw.polygon([(cx - 4, head_cy - 8), (cx, head_cy - 14),
                      (cx + 4, head_cy - 8)], fill=GRAV_ROBE, outline=OUTLINE)
        # Face (partially shadowed)
        ellipse(draw, cx, head_cy + 2, 6, 4, GRAV_SKIN, outline=None)
        # Glowing eyes
        draw.rectangle([cx - 4, head_cy, cx - 2, head_cy + 2], fill=GRAV_EYE)
        draw.rectangle([cx + 2, head_cy, cx + 4, head_cy + 2], fill=GRAV_EYE)
        draw.point((cx - 3, head_cy), fill=GRAV_ORB_BRIGHT)
        draw.point((cx + 3, head_cy), fill=GRAV_ORB_BRIGHT)
    elif direction == UP:
        ellipse(draw, cx, head_cy, 12, 10, GRAV_ROBE)
        ellipse(draw, cx, head_cy, 8, 7, GRAV_ROBE_DARK, outline=None)
        draw.polygon([(cx - 4, head_cy - 8), (cx, head_cy - 14),
                      (cx + 4, head_cy - 8)], fill=GRAV_ROBE, outline=OUTLINE)
    elif direction == LEFT:
        ellipse(draw, cx - 2, head_cy, 11, 10, GRAV_ROBE)
        ellipse(draw, cx - 2, head_cy + 2, 7, 6, GRAV_ROBE_DARK, outline=None)
        draw.polygon([(cx - 4, head_cy - 8), (cx - 2, head_cy - 14),
                      (cx + 2, head_cy - 8)], fill=GRAV_ROBE, outline=OUTLINE)
        ellipse(draw, cx - 4, head_cy + 2, 4, 3, GRAV_SKIN, outline=None)
        draw.rectangle([cx - 7, head_cy, cx - 5, head_cy + 2], fill=GRAV_EYE)
        draw.point((cx - 6, head_cy), fill=GRAV_ORB_BRIGHT)
    else:  # RIGHT
        ellipse(draw, cx + 2, head_cy, 11, 10, GRAV_ROBE)
        ellipse(draw, cx + 2, head_cy + 2, 7, 6, GRAV_ROBE_DARK, outline=None)
        draw.polygon([(cx - 2, head_cy - 8), (cx + 2, head_cy - 14),
                      (cx + 4, head_cy - 8)], fill=GRAV_ROBE, outline=OUTLINE)
        ellipse(draw, cx + 4, head_cy + 2, 4, 3, GRAV_SKIN, outline=None)
        draw.rectangle([cx + 5, head_cy, cx + 7, head_cy + 2], fill=GRAV_EYE)
        draw.point((cx + 6, head_cy), fill=GRAV_ORB_BRIGHT)

    # --- Orbiting purple spheres (3 orbs) ---
    orb_radius = 20
    for i in range(3):
        angle = orb_angle_base + i * (2 * math.pi / 3)
        ox2 = int(cx + math.cos(angle) * orb_radius)
        oy2 = int(body_cy + math.sin(angle) * (orb_radius * 0.45))
        # Outer glow
        ellipse(draw, ox2, oy2, 4, 4, GRAV_ORB_DIM, outline=None)
        # Orb body
        ellipse(draw, ox2, oy2, 3, 3, GRAV_ORB)
        # Inner bright core
        ellipse(draw, ox2 - 1, oy2 - 1, 1, 1, GRAV_ORB_BRIGHT, outline=None)


# ===================================================================
# TESLA (ID 63) -- lab coat with tesla coils on back, sparking arcs,
#                  blue lightning effects, goggles on forehead
# ===================================================================

def draw_tesla(draw, ox, oy, direction, frame):
    bob = [0, -2, 0, -1][frame]
    leg_spread = [-4, 0, 4, 0][frame]
    spark_shift = frame  # varies spark positions per frame

    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 20
    head_cy = body_cy - 20

    coat_shadow = _darken(TESLA_COAT, 0.75)

    # --- Legs ---
    if direction in (DOWN, UP):
        for side in [-1, 1]:
            ls = leg_spread if side == -1 else -leg_spread
            lx = cx + side * 7 + ls
            draw.rectangle([lx - 3, body_cy + 10, lx + 3, base_y],
                           fill=TESLA_COAT_DARK, outline=OUTLINE)
            draw.rectangle([lx - 3, base_y - 5, lx + 3, base_y],
                           fill=_darken(TESLA_COAT_DARK, 0.7), outline=OUTLINE)
    elif direction == LEFT:
        for offset in [leg_spread, -leg_spread]:
            lx = cx - 3 + offset
            draw.rectangle([lx - 3, body_cy + 10, lx + 3, base_y],
                           fill=TESLA_COAT_DARK, outline=OUTLINE)
            draw.rectangle([lx - 3, base_y - 5, lx + 3, base_y],
                           fill=_darken(TESLA_COAT_DARK, 0.7), outline=OUTLINE)
    else:  # RIGHT
        for offset in [leg_spread, -leg_spread]:
            lx = cx + 3 + offset
            draw.rectangle([lx - 3, body_cy + 10, lx + 3, base_y],
                           fill=TESLA_COAT_DARK, outline=OUTLINE)
            draw.rectangle([lx - 3, base_y - 5, lx + 3, base_y],
                           fill=_darken(TESLA_COAT_DARK, 0.7), outline=OUTLINE)

    # --- Tesla coils on back (behind body) ---
    if direction == UP:
        # Two coils visible
        for coil_x in [cx - 6, cx + 6]:
            draw.rectangle([coil_x - 2, body_cy - 14, coil_x + 2, body_cy - 4],
                           fill=TESLA_COIL, outline=OUTLINE)
            # Coil rings
            for ry in range(body_cy - 12, body_cy - 4, 3):
                draw.line([(coil_x - 2, ry), (coil_x + 2, ry)],
                          fill=TESLA_COIL_DARK, width=1)
            # Spark ball at top
            ellipse(draw, coil_x, body_cy - 16, 3, 3, TESLA_SPARK)
            draw.point((coil_x - 1, body_cy - 17), fill=TESLA_SPARK_BRIGHT)
    elif direction == DOWN:
        # Coils partially visible behind shoulders
        for coil_x in [cx - 10, cx + 10]:
            draw.rectangle([coil_x - 1, body_cy - 12, coil_x + 1, body_cy - 6],
                           fill=TESLA_COIL, outline=OUTLINE)
            ellipse(draw, coil_x, body_cy - 14, 2, 2, TESLA_SPARK)
    elif direction == LEFT:
        # Right coil visible behind
        draw.rectangle([cx + 8, body_cy - 14, cx + 12, body_cy - 4],
                       fill=TESLA_COIL, outline=OUTLINE)
        for ry in range(body_cy - 12, body_cy - 4, 3):
            draw.line([(cx + 8, ry), (cx + 12, ry)],
                      fill=TESLA_COIL_DARK, width=1)
        ellipse(draw, cx + 10, body_cy - 16, 3, 3, TESLA_SPARK)
        draw.point((cx + 9, body_cy - 17), fill=TESLA_SPARK_BRIGHT)
    else:  # RIGHT
        # Left coil visible behind
        draw.rectangle([cx - 12, body_cy - 14, cx - 8, body_cy - 4],
                       fill=TESLA_COIL, outline=OUTLINE)
        for ry in range(body_cy - 12, body_cy - 4, 3):
            draw.line([(cx - 12, ry), (cx - 8, ry)],
                      fill=TESLA_COIL_DARK, width=1)
        ellipse(draw, cx - 10, body_cy - 16, 3, 3, TESLA_SPARK)
        draw.point((cx - 11, body_cy - 17), fill=TESLA_SPARK_BRIGHT)

    # --- Body (lab coat) ---
    if direction == DOWN:
        ellipse(draw, cx, body_cy, 14, 12, TESLA_COAT)
        ellipse(draw, cx + 3, body_cy + 2, 10, 8, coat_shadow, outline=None)
        ellipse(draw, cx, body_cy, 11, 9, TESLA_COAT, outline=None)
        ellipse(draw, cx - 3, body_cy - 2, 7, 5, TESLA_COAT_LIGHT, outline=None)
        # Coat lapels (V-shape)
        draw.line([(cx, body_cy - 8), (cx - 5, body_cy + 4)],
                  fill=TESLA_COAT_DARK, width=2)
        draw.line([(cx, body_cy - 8), (cx + 5, body_cy + 4)],
                  fill=TESLA_COAT_DARK, width=2)
        # Buttons
        for by in range(body_cy - 4, body_cy + 6, 3):
            draw.point((cx, by), fill=TESLA_COIL_DARK)
        draw.rectangle([cx - 14, body_cy + 8, cx + 14, body_cy + 12],
                       fill=TESLA_COAT_DARK, outline=OUTLINE)
    elif direction == UP:
        ellipse(draw, cx, body_cy, 14, 12, TESLA_COAT)
        ellipse(draw, cx, body_cy, 11, 9, coat_shadow, outline=None)
        draw.line([(cx, body_cy - 6), (cx, body_cy + 6)],
                  fill=TESLA_COAT_DARK, width=1)
        draw.rectangle([cx - 14, body_cy + 8, cx + 14, body_cy + 12],
                       fill=TESLA_COAT_DARK, outline=OUTLINE)
    elif direction == LEFT:
        ellipse(draw, cx - 2, body_cy, 12, 12, TESLA_COAT)
        ellipse(draw, cx + 1, body_cy + 2, 8, 8, coat_shadow, outline=None)
        ellipse(draw, cx - 4, body_cy - 2, 6, 6, TESLA_COAT_LIGHT, outline=None)
        draw.rectangle([cx - 14, body_cy + 8, cx + 10, body_cy + 12],
                       fill=TESLA_COAT_DARK, outline=OUTLINE)
    else:  # RIGHT
        ellipse(draw, cx + 2, body_cy, 12, 12, TESLA_COAT)
        ellipse(draw, cx + 5, body_cy + 2, 8, 8, coat_shadow, outline=None)
        ellipse(draw, cx, body_cy - 2, 6, 6, TESLA_COAT_LIGHT, outline=None)
        draw.rectangle([cx - 10, body_cy + 8, cx + 14, body_cy + 12],
                       fill=TESLA_COAT_DARK, outline=OUTLINE)

    # --- Arms ---
    if direction == DOWN:
        draw.rectangle([cx - 18, body_cy - 6, cx - 12, body_cy + 6],
                       fill=TESLA_COAT, outline=OUTLINE)
        draw.rectangle([cx - 18, body_cy + 2, cx - 12, body_cy + 6],
                       fill=TESLA_SKIN, outline=OUTLINE)
        draw.rectangle([cx + 12, body_cy - 6, cx + 18, body_cy + 6],
                       fill=TESLA_COAT, outline=OUTLINE)
        draw.rectangle([cx + 12, body_cy + 2, cx + 18, body_cy + 6],
                       fill=TESLA_SKIN, outline=OUTLINE)
        ellipse(draw, cx - 15, body_cy - 6, 5, 3, TESLA_COAT)
        ellipse(draw, cx + 15, body_cy - 6, 5, 3, TESLA_COAT)
    elif direction == UP:
        draw.rectangle([cx - 18, body_cy - 6, cx - 12, body_cy + 6],
                       fill=TESLA_COAT, outline=OUTLINE)
        draw.rectangle([cx + 12, body_cy - 6, cx + 18, body_cy + 6],
                       fill=TESLA_COAT, outline=OUTLINE)
    elif direction == LEFT:
        draw.rectangle([cx - 14, body_cy - 4, cx - 8, body_cy + 6],
                       fill=TESLA_COAT, outline=OUTLINE)
        draw.rectangle([cx - 14, body_cy + 2, cx - 8, body_cy + 6],
                       fill=TESLA_SKIN, outline=OUTLINE)
        ellipse(draw, cx - 11, body_cy - 6, 5, 3, TESLA_COAT)
    else:  # RIGHT
        draw.rectangle([cx + 8, body_cy - 4, cx + 14, body_cy + 6],
                       fill=TESLA_COAT, outline=OUTLINE)
        draw.rectangle([cx + 8, body_cy + 2, cx + 14, body_cy + 6],
                       fill=TESLA_SKIN, outline=OUTLINE)
        ellipse(draw, cx + 11, body_cy - 6, 5, 3, TESLA_COAT)

    # --- Head with goggles ---
    if direction == DOWN:
        ellipse(draw, cx, head_cy, 14, 12, TESLA_SKIN)
        ellipse(draw, cx - 2, head_cy - 3, 8, 6, _brighten(TESLA_SKIN, 1.1), outline=None)
        # Face
        ellipse(draw, cx, head_cy + 4, 8, 6, TESLA_SKIN, outline=None)
        ellipse(draw, cx + 2, head_cy + 6, 5, 3, TESLA_SKIN_DARK, outline=None)
        # Eyes
        draw.rectangle([cx - 6, head_cy + 2, cx - 2, head_cy + 6], fill=(255, 255, 255))
        draw.rectangle([cx - 5, head_cy + 3, cx - 3, head_cy + 5], fill=BLACK)
        draw.point((cx - 5, head_cy + 3), fill=(255, 255, 255))
        draw.rectangle([cx + 2, head_cy + 2, cx + 6, head_cy + 6], fill=(255, 255, 255))
        draw.rectangle([cx + 3, head_cy + 3, cx + 5, head_cy + 5], fill=BLACK)
        draw.point((cx + 3, head_cy + 3), fill=(255, 255, 255))
        draw.point((cx, head_cy + 7), fill=TESLA_SKIN_DARK)
        draw.line([(cx - 2, head_cy + 9), (cx + 2, head_cy + 9)],
                  fill=TESLA_SKIN_DARK, width=1)
        # Goggles on forehead
        draw.rectangle([cx - 10, head_cy - 4, cx - 4, head_cy],
                       fill=TESLA_GOGGLES, outline=OUTLINE)
        ellipse(draw, cx - 7, head_cy - 2, 2, 2, TESLA_GOGGLES_LENS, outline=None)
        draw.rectangle([cx + 4, head_cy - 4, cx + 10, head_cy],
                       fill=TESLA_GOGGLES, outline=OUTLINE)
        ellipse(draw, cx + 7, head_cy - 2, 2, 2, TESLA_GOGGLES_LENS, outline=None)
        # Goggle strap
        draw.line([(cx - 4, head_cy - 2), (cx + 4, head_cy - 2)],
                  fill=TESLA_GOGGLES, width=1)
        # Wild hair above goggles
        for hx in range(-8, 10, 4):
            draw.line([(cx + hx, head_cy - 8), (cx + hx + 1, head_cy - 13)],
                      fill=_darken(TESLA_SKIN, 0.5), width=1)
    elif direction == UP:
        ellipse(draw, cx, head_cy, 14, 12, TESLA_SKIN)
        ellipse(draw, cx, head_cy, 10, 8, TESLA_SKIN_DARK, outline=None)
        # Goggle strap visible from behind
        draw.line([(cx - 10, head_cy - 2), (cx + 10, head_cy - 2)],
                  fill=TESLA_GOGGLES, width=2)
        # Wild hair
        for hx in range(-8, 10, 4):
            draw.line([(cx + hx, head_cy - 8), (cx + hx - 1, head_cy - 13)],
                      fill=_darken(TESLA_SKIN, 0.5), width=1)
    elif direction == LEFT:
        ellipse(draw, cx - 2, head_cy, 13, 12, TESLA_SKIN)
        ellipse(draw, cx - 4, head_cy - 3, 7, 5, _brighten(TESLA_SKIN, 1.1), outline=None)
        ellipse(draw, cx - 6, head_cy + 4, 6, 5, TESLA_SKIN, outline=None)
        # Eye
        draw.rectangle([cx - 10, head_cy + 2, cx - 6, head_cy + 6], fill=(255, 255, 255))
        draw.rectangle([cx - 9, head_cy + 3, cx - 7, head_cy + 5], fill=BLACK)
        draw.point((cx - 9, head_cy + 3), fill=(255, 255, 255))
        draw.point((cx - 8, head_cy + 7), fill=TESLA_SKIN_DARK)
        # Goggle (side view)
        draw.rectangle([cx - 12, head_cy - 4, cx - 6, head_cy],
                       fill=TESLA_GOGGLES, outline=OUTLINE)
        ellipse(draw, cx - 9, head_cy - 2, 2, 2, TESLA_GOGGLES_LENS, outline=None)
        # Wild hair
        for hx in range(-6, 4, 3):
            draw.line([(cx + hx, head_cy - 8), (cx + hx - 1, head_cy - 13)],
                      fill=_darken(TESLA_SKIN, 0.5), width=1)
    else:  # RIGHT
        ellipse(draw, cx + 2, head_cy, 13, 12, TESLA_SKIN)
        ellipse(draw, cx, head_cy - 3, 7, 5, _brighten(TESLA_SKIN, 1.1), outline=None)
        ellipse(draw, cx + 6, head_cy + 4, 6, 5, TESLA_SKIN, outline=None)
        # Eye
        draw.rectangle([cx + 6, head_cy + 2, cx + 10, head_cy + 6], fill=(255, 255, 255))
        draw.rectangle([cx + 7, head_cy + 3, cx + 9, head_cy + 5], fill=BLACK)
        draw.point((cx + 7, head_cy + 3), fill=(255, 255, 255))
        draw.point((cx + 8, head_cy + 7), fill=TESLA_SKIN_DARK)
        # Goggle (side view)
        draw.rectangle([cx + 6, head_cy - 4, cx + 12, head_cy],
                       fill=TESLA_GOGGLES, outline=OUTLINE)
        ellipse(draw, cx + 9, head_cy - 2, 2, 2, TESLA_GOGGLES_LENS, outline=None)
        # Wild hair
        for hx in range(-4, 6, 3):
            draw.line([(cx + hx, head_cy - 8), (cx + hx + 1, head_cy - 13)],
                      fill=_darken(TESLA_SKIN, 0.5), width=1)

    # --- Sparking electricity arcs between coils ---
    spark_positions = [
        [(-12, -18), (-4, -22), (4, -20), (12, -18)],
        [(-10, -20), (-2, -18), (6, -22), (14, -20)],
        [(-14, -20), (-6, -24), (2, -18), (10, -22)],
        [(-8, -22), (0, -20), (8, -18), (12, -22)],
    ]
    for sx, sy in spark_positions[frame]:
        x = cx + sx
        y = body_cy + sy
        draw.point((x, y), fill=TESLA_SPARK)
        draw.point((x + 1, y - 1), fill=TESLA_SPARK_BRIGHT)


# ===================================================================
# NANOSWARM (ID 64) -- body made of tiny green particles/dots, swarm
#                      edges that dissolve, glowing green core, less
#                      defined silhouette
# ===================================================================

def draw_nanoswarm(draw, ox, oy, direction, frame):
    bob = [0, -2, 0, -1][frame]
    leg_spread = [-4, 0, 4, 0][frame]

    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 20
    head_cy = body_cy - 20

    # --- Legs (particle-based, less defined) ---
    if direction in (DOWN, UP):
        for side in [-1, 1]:
            ls = leg_spread if side == -1 else -leg_spread
            lx = cx + side * 7 + ls
            # Semi-solid leg core
            draw.rectangle([lx - 2, body_cy + 10, lx + 2, base_y - 2],
                           fill=NANO_BODY, outline=None)
            # Particle dots along leg edges
            for py in range(body_cy + 10, base_y, 3):
                draw.point((lx - 3, py), fill=NANO_PARTICLE)
                draw.point((lx + 3, py), fill=NANO_PARTICLE)
                draw.point((lx - 4, py + 1), fill=NANO_PARTICLE_DIM)
                draw.point((lx + 4, py + 1), fill=NANO_PARTICLE_DIM)
            # Dissolving foot
            for fx in range(-3, 4):
                draw.point((lx + fx, base_y - 1), fill=NANO_PARTICLE)
                draw.point((lx + fx, base_y), fill=NANO_PARTICLE_FAINT)
    elif direction == LEFT:
        for offset in [leg_spread, -leg_spread]:
            lx = cx - 3 + offset
            draw.rectangle([lx - 2, body_cy + 10, lx + 2, base_y - 2],
                           fill=NANO_BODY, outline=None)
            for py in range(body_cy + 10, base_y, 3):
                draw.point((lx - 3, py), fill=NANO_PARTICLE)
                draw.point((lx + 3, py), fill=NANO_PARTICLE)
            for fx in range(-3, 4):
                draw.point((lx + fx, base_y), fill=NANO_PARTICLE_FAINT)
    else:  # RIGHT
        for offset in [leg_spread, -leg_spread]:
            lx = cx + 3 + offset
            draw.rectangle([lx - 2, body_cy + 10, lx + 2, base_y - 2],
                           fill=NANO_BODY, outline=None)
            for py in range(body_cy + 10, base_y, 3):
                draw.point((lx - 3, py), fill=NANO_PARTICLE)
                draw.point((lx + 3, py), fill=NANO_PARTICLE)
            for fx in range(-3, 4):
                draw.point((lx + fx, base_y), fill=NANO_PARTICLE_FAINT)

    # --- Body (particle cloud with glowing core) ---
    if direction == DOWN:
        # Inner core (solid-ish)
        ellipse(draw, cx, body_cy, 10, 8, NANO_BODY, outline=None)
        ellipse(draw, cx - 2, body_cy - 2, 6, 4, NANO_BODY_LIGHT, outline=None)
        # Glowing core center
        ellipse(draw, cx, body_cy + 2, 4, 3, NANO_CORE)
        draw.point((cx - 1, body_cy + 1), fill=NANO_CORE_BRIGHT)
        # Particle edge swarm
        swarm_offsets = [
            [(-14, -6), (-16, 0), (-14, 6), (-12, 10),
             (14, -6), (16, 0), (14, 6), (12, 10),
             (-8, -10), (0, -12), (8, -10)],
            [(-16, -4), (-14, 2), (-16, 8), (-10, 12),
             (16, -4), (14, 2), (16, 8), (10, 12),
             (-6, -12), (2, -10), (10, -12)],
            [(-14, -8), (-16, -2), (-12, 4), (-14, 10),
             (14, -8), (16, -2), (12, 4), (14, 10),
             (-10, -10), (-2, -12), (6, -10)],
            [(-16, -6), (-12, 0), (-16, 6), (-12, 8),
             (16, -6), (12, 0), (16, 6), (12, 8),
             (-4, -10), (4, -12), (12, -10)],
        ]
        for sx, sy in swarm_offsets[frame]:
            x = cx + sx
            y = body_cy + sy
            draw.point((x, y), fill=NANO_PARTICLE)
            draw.point((x + 1, y), fill=NANO_PARTICLE_DIM)
    elif direction == UP:
        ellipse(draw, cx, body_cy, 10, 8, NANO_BODY, outline=None)
        ellipse(draw, cx, body_cy, 6, 5, NANO_BODY_DARK, outline=None)
        ellipse(draw, cx, body_cy + 2, 4, 3, NANO_CORE)
        for sx, sy in [(-14, -6), (-16, 0), (-14, 6), (14, -6), (16, 0), (14, 6),
                       (-8, -10 - frame), (0, -12 + frame), (8, -10 - frame)]:
            draw.point((cx + sx, body_cy + sy), fill=NANO_PARTICLE)
            draw.point((cx + sx + 1, body_cy + sy), fill=NANO_PARTICLE_DIM)
    elif direction == LEFT:
        ellipse(draw, cx - 2, body_cy, 10, 8, NANO_BODY, outline=None)
        ellipse(draw, cx - 4, body_cy - 2, 6, 4, NANO_BODY_LIGHT, outline=None)
        ellipse(draw, cx - 2, body_cy + 2, 4, 3, NANO_CORE)
        for sx, sy in [(-14, -4), (-12, 4), (-14, 8), (10, -4), (12, 4), (10, 8),
                       (-6, -10 - frame), (4, -10 + frame)]:
            draw.point((cx + sx, body_cy + sy), fill=NANO_PARTICLE)
    else:  # RIGHT
        ellipse(draw, cx + 2, body_cy, 10, 8, NANO_BODY, outline=None)
        ellipse(draw, cx, body_cy - 2, 6, 4, NANO_BODY_LIGHT, outline=None)
        ellipse(draw, cx + 2, body_cy + 2, 4, 3, NANO_CORE)
        for sx, sy in [(14, -4), (12, 4), (14, 8), (-10, -4), (-12, 4), (-10, 8),
                       (6, -10 - frame), (-4, -10 + frame)]:
            draw.point((cx + sx, body_cy + sy), fill=NANO_PARTICLE)

    # --- Arms (dissolving particle arms) ---
    if direction == DOWN:
        for side in [-1, 1]:
            ax = cx + side * 14
            # Arm core
            draw.rectangle([ax - 2, body_cy - 4, ax + 2, body_cy + 4],
                           fill=NANO_BODY, outline=None)
            # Particle edges
            draw.point((ax - 3, body_cy - 2), fill=NANO_PARTICLE)
            draw.point((ax + 3, body_cy), fill=NANO_PARTICLE)
            draw.point((ax - 3, body_cy + 2), fill=NANO_PARTICLE)
            draw.point((ax + 3, body_cy + 4), fill=NANO_PARTICLE_DIM)
            # Hand dissolve
            draw.point((ax - 1, body_cy + 5), fill=NANO_PARTICLE)
            draw.point((ax + 1, body_cy + 5), fill=NANO_PARTICLE)
            draw.point((ax, body_cy + 6), fill=NANO_PARTICLE_FAINT)
    elif direction == UP:
        for side in [-1, 1]:
            ax = cx + side * 14
            draw.rectangle([ax - 2, body_cy - 4, ax + 2, body_cy + 4],
                           fill=NANO_BODY, outline=None)
            draw.point((ax - 3, body_cy), fill=NANO_PARTICLE)
            draw.point((ax + 3, body_cy), fill=NANO_PARTICLE)
    elif direction == LEFT:
        draw.rectangle([cx - 13, body_cy - 2, cx - 9, body_cy + 4],
                       fill=NANO_BODY, outline=None)
        draw.point((cx - 14, body_cy), fill=NANO_PARTICLE)
        draw.point((cx - 14, body_cy + 2), fill=NANO_PARTICLE)
        draw.point((cx - 11, body_cy + 5), fill=NANO_PARTICLE_DIM)
    else:  # RIGHT
        draw.rectangle([cx + 9, body_cy - 2, cx + 13, body_cy + 4],
                       fill=NANO_BODY, outline=None)
        draw.point((cx + 14, body_cy), fill=NANO_PARTICLE)
        draw.point((cx + 14, body_cy + 2), fill=NANO_PARTICLE)
        draw.point((cx + 11, body_cy + 5), fill=NANO_PARTICLE_DIM)

    # --- Head (particle cluster with glowing eyes) ---
    if direction == DOWN:
        # Head core (semi-solid)
        ellipse(draw, cx, head_cy, 10, 8, NANO_BODY, outline=None)
        ellipse(draw, cx - 2, head_cy - 2, 6, 4, NANO_BODY_LIGHT, outline=None)
        # Particle edge halo
        head_particles = [
            [(-12, -4), (-10, 4), (12, -4), (10, 4),
             (-8, -8), (0, -10), (8, -8), (-12, 0), (12, 0)],
            [(-10, -6), (-12, 2), (10, -6), (12, 2),
             (-6, -10), (2, -8), (10, -10), (-14, 0), (14, 0)],
            [(-12, -6), (-10, 0), (12, -6), (10, 0),
             (-8, -10), (-2, -8), (6, -10), (-10, 2), (10, 2)],
            [(-10, -4), (-12, 4), (10, -4), (12, 4),
             (-6, -8), (4, -10), (8, -8), (-12, -2), (12, -2)],
        ]
        for px, py in head_particles[frame]:
            draw.point((cx + px, head_cy + py), fill=NANO_PARTICLE)
        # Glowing eyes
        draw.rectangle([cx - 5, head_cy, cx - 2, head_cy + 3], fill=NANO_EYE)
        draw.rectangle([cx + 2, head_cy, cx + 5, head_cy + 3], fill=NANO_EYE)
        draw.point((cx - 4, head_cy + 1), fill=NANO_CORE_BRIGHT)
        draw.point((cx + 3, head_cy + 1), fill=NANO_CORE_BRIGHT)
    elif direction == UP:
        ellipse(draw, cx, head_cy, 10, 8, NANO_BODY, outline=None)
        ellipse(draw, cx, head_cy, 6, 5, NANO_BODY_DARK, outline=None)
        for px, py in [(-10, -4), (-12, 2), (10, -4), (12, 2),
                       (-6, -8 - frame), (0, -10 + frame), (6, -8 - frame)]:
            draw.point((cx + px, head_cy + py), fill=NANO_PARTICLE)
    elif direction == LEFT:
        ellipse(draw, cx - 2, head_cy, 10, 8, NANO_BODY, outline=None)
        ellipse(draw, cx - 4, head_cy - 2, 6, 4, NANO_BODY_LIGHT, outline=None)
        for px, py in [(-12, -2), (-10, 4), (8, -2), (8, 4),
                       (-8, -8 - frame), (0, -8 + frame)]:
            draw.point((cx + px, head_cy + py), fill=NANO_PARTICLE)
        draw.rectangle([cx - 7, head_cy, cx - 4, head_cy + 3], fill=NANO_EYE)
        draw.point((cx - 6, head_cy + 1), fill=NANO_CORE_BRIGHT)
    else:  # RIGHT
        ellipse(draw, cx + 2, head_cy, 10, 8, NANO_BODY, outline=None)
        ellipse(draw, cx, head_cy - 2, 6, 4, NANO_BODY_LIGHT, outline=None)
        for px, py in [(12, -2), (10, 4), (-8, -2), (-8, 4),
                       (8, -8 - frame), (0, -8 + frame)]:
            draw.point((cx + px, head_cy + py), fill=NANO_PARTICLE)
        draw.rectangle([cx + 4, head_cy, cx + 7, head_cy + 3], fill=NANO_EYE)
        draw.point((cx + 5, head_cy + 1), fill=NANO_CORE_BRIGHT)

    # --- Ambient dissolve particles (floating around body) ---
    ambient = [
        [(-20, -14), (22, -10), (-18, 12), (20, 14), (0, -18)],
        [(-22, -12), (20, -14), (-20, 14), (18, 10), (2, -20)],
        [(-18, -10), (22, -12), (-22, 10), (20, 16), (-2, -16)],
        [(-20, -16), (18, -10), (-16, 14), (22, 12), (4, -18)],
    ]
    for ax, ay in ambient[frame]:
        draw.point((cx + ax, body_cy + ay), fill=NANO_PARTICLE_FAINT)


# ===================================================================
# VOIDWALKER (ID 65) -- dark purple/black robes, void portal effects,
#                       cape, glowing purple eyes, dark energy wisps
# ===================================================================

# Voidwalker palette
VOID_ROBE = (40, 20, 65)
VOID_ROBE_DARK = (22, 10, 40)
VOID_ROBE_LIGHT = (65, 35, 100)
VOID_SKIN = (160, 140, 180)
VOID_SKIN_DARK = (130, 110, 150)
VOID_EYE = (180, 60, 255)
VOID_EYE_BRIGHT = (220, 120, 255)
VOID_PORTAL = (100, 30, 180, 140)
VOID_PORTAL_BRIGHT = (140, 60, 220, 180)
VOID_WISP = (120, 40, 200, 120)
VOID_WISP_DIM = (80, 20, 150, 80)
VOID_CAPE = (30, 12, 50)
VOID_CAPE_LIGHT = (55, 30, 85)


def draw_voidwalker(draw, ox, oy, direction, frame):
    bob = [0, -2, 0, -1][frame]
    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 20
    head_cy = body_cy - 18

    wisp_drift = [0, 1, -1, 0][frame]
    cape_sway = [-1, 0, 1, 0][frame]

    # --- Cape (behind body, drawn first) ---
    if direction == DOWN:
        # Cape visible behind at sides
        cape_x1 = cx - 10 + cape_sway
        cape_x2 = cx + 10 + cape_sway
        draw.rectangle([cape_x1, body_cy - 4, cape_x1 + 4, base_y + 4],
                       fill=VOID_CAPE, outline=OUTLINE)
        draw.rectangle([cape_x2 - 4, body_cy - 4, cape_x2, base_y + 4],
                       fill=VOID_CAPE, outline=OUTLINE)
    elif direction == UP:
        # Full cape visible from behind
        draw.rectangle([cx - 10, body_cy - 4, cx + 10, base_y + 6],
                       fill=VOID_CAPE, outline=OUTLINE)
        draw.rectangle([cx - 8, body_cy - 2, cx + 8, base_y + 4],
                       fill=VOID_CAPE_LIGHT, outline=None)
        # Cape bottom tatter
        for tx in range(-8, 9, 4):
            t_len = 2 + (frame + tx) % 3
            draw.rectangle([cx + tx - 1, base_y + 4, cx + tx + 1, base_y + 4 + t_len],
                           fill=VOID_CAPE, outline=None)
    elif direction == LEFT:
        draw.rectangle([cx + 2, body_cy - 4, cx + 12 + cape_sway, base_y + 5],
                       fill=VOID_CAPE, outline=OUTLINE)
        draw.rectangle([cx + 4, body_cy - 2, cx + 10 + cape_sway, base_y + 3],
                       fill=VOID_CAPE_LIGHT, outline=None)
    else:  # RIGHT
        draw.rectangle([cx - 12 - cape_sway, body_cy - 4, cx - 2, base_y + 5],
                       fill=VOID_CAPE, outline=OUTLINE)
        draw.rectangle([cx - 10 - cape_sway, body_cy - 2, cx - 4, base_y + 3],
                       fill=VOID_CAPE_LIGHT, outline=None)

    # --- Legs (robed, dark) ---
    if direction in (DOWN, UP):
        leg_spread = [-3, 0, 3, 0][frame]
        for side in [-1, 1]:
            ls = leg_spread if side == -1 else -leg_spread
            lx = cx + side * 5 + ls
            draw.rectangle([lx - 3, body_cy + 10, lx + 3, base_y],
                           fill=VOID_ROBE_DARK, outline=OUTLINE)
            draw.rectangle([lx - 3, base_y - 4, lx + 3, base_y],
                           fill=_darken(VOID_ROBE_DARK, 0.7), outline=OUTLINE)
    else:
        off = -2 if direction == LEFT else 2
        draw.rectangle([cx + off - 3, body_cy + 10, cx + off + 3, base_y],
                       fill=VOID_ROBE_DARK, outline=OUTLINE)
        draw.rectangle([cx + off - 6, body_cy + 12, cx + off, base_y],
                       fill=VOID_ROBE_DARK, outline=OUTLINE)
        draw.rectangle([cx + off - 3, base_y - 4, cx + off + 3, base_y],
                       fill=_darken(VOID_ROBE_DARK, 0.7), outline=OUTLINE)

    # --- Body (robed torso) ---
    pill(draw, cx, body_cy, 11, 14, VOID_ROBE, outline=OUTLINE)
    pill(draw, cx - 2, body_cy - 2, 6, 8, VOID_ROBE_LIGHT, outline=None)

    # Void portal on chest (down-facing)
    if direction == DOWN:
        ellipse(draw, cx, body_cy + 2, 5, 4, VOID_PORTAL, outline=None)
        ellipse(draw, cx, body_cy + 2, 3, 2, VOID_PORTAL_BRIGHT, outline=None)
    elif direction == LEFT:
        ellipse(draw, cx + 3, body_cy + 2, 4, 4, VOID_PORTAL, outline=None)
    elif direction == RIGHT:
        ellipse(draw, cx - 3, body_cy + 2, 4, 4, VOID_PORTAL, outline=None)

    # --- Arms ---
    if direction in (DOWN, UP):
        for side in [-1, 1]:
            ax = cx + side * 12
            draw.rectangle([ax - 3, body_cy - 6, ax + 3, body_cy + 8],
                           fill=VOID_ROBE, outline=OUTLINE)
            # Dark energy around hands
            draw.rectangle([ax - 2, body_cy + 5, ax + 2, body_cy + 9],
                           fill=VOID_ROBE_DARK, outline=OUTLINE)
            # Wisp on hand
            wy = body_cy + 8 + wisp_drift
            draw.point((ax, wy), fill=VOID_WISP)
    elif direction == LEFT:
        draw.rectangle([cx + 5, body_cy - 6, cx + 11, body_cy + 8],
                       fill=VOID_ROBE, outline=OUTLINE)
        draw.point((cx + 8, body_cy + 8 + wisp_drift), fill=VOID_WISP)
    else:  # RIGHT
        draw.rectangle([cx - 11, body_cy - 6, cx - 5, body_cy + 8],
                       fill=VOID_ROBE, outline=OUTLINE)
        draw.point((cx - 8, body_cy + 8 + wisp_drift), fill=VOID_WISP)

    # --- Head (hooded) ---
    if direction == DOWN:
        # Hood
        ellipse(draw, cx, head_cy, 11, 10, VOID_ROBE, outline=OUTLINE)
        # Shadow inside hood
        ellipse(draw, cx, head_cy + 2, 8, 6, VOID_ROBE_DARK, outline=None)
        # Glowing purple eyes
        draw.rectangle([cx - 5, head_cy + 1, cx - 2, head_cy + 4], fill=VOID_EYE)
        draw.rectangle([cx + 2, head_cy + 1, cx + 5, head_cy + 4], fill=VOID_EYE)
        draw.point((cx - 4, head_cy + 2), fill=VOID_EYE_BRIGHT)
        draw.point((cx + 3, head_cy + 2), fill=VOID_EYE_BRIGHT)
    elif direction == UP:
        ellipse(draw, cx, head_cy, 11, 10, VOID_ROBE, outline=OUTLINE)
        ellipse(draw, cx, head_cy - 1, 8, 7, VOID_ROBE_DARK, outline=None)
    elif direction == LEFT:
        ellipse(draw, cx - 2, head_cy, 11, 10, VOID_ROBE, outline=OUTLINE)
        ellipse(draw, cx - 4, head_cy + 1, 7, 5, VOID_ROBE_DARK, outline=None)
        draw.rectangle([cx - 8, head_cy + 1, cx - 5, head_cy + 4], fill=VOID_EYE)
        draw.point((cx - 7, head_cy + 2), fill=VOID_EYE_BRIGHT)
    else:  # RIGHT
        ellipse(draw, cx + 2, head_cy, 11, 10, VOID_ROBE, outline=OUTLINE)
        ellipse(draw, cx, head_cy + 1, 7, 5, VOID_ROBE_DARK, outline=None)
        draw.rectangle([cx + 5, head_cy + 1, cx + 8, head_cy + 4], fill=VOID_EYE)
        draw.point((cx + 6, head_cy + 2), fill=VOID_EYE_BRIGHT)

    # --- Dark energy wisps (floating particles) ---
    wisp_positions = [
        [(-18, -10), (16, -14), (-14, 8), (20, 6), (0, -20)],
        [(-16, -14), (18, -10), (-20, 6), (14, 10), (-2, -18)],
        [(-20, -8), (14, -16), (-16, 10), (18, 4), (2, -22)],
        [(-14, -12), (20, -12), (-18, 4), (16, 8), (-4, -16)],
    ]
    for wx, wy in wisp_positions[frame]:
        draw.point((cx + wx, body_cy + wy), fill=VOID_WISP)
        draw.point((cx + wx + 1, body_cy + wy - 1), fill=VOID_WISP_DIM)

    # --- Void portal particles around feet ---
    portal_pts = [
        [(-8, 4), (8, 2), (-4, 6), (6, 5)],
        [(-6, 2), (10, 4), (-2, 5), (4, 6)],
        [(-10, 3), (6, 6), (-6, 4), (8, 3)],
        [(-4, 5), (8, 3), (-8, 6), (10, 5)],
    ]
    for px, py in portal_pts[frame]:
        draw.point((cx + px, base_y + py), fill=VOID_PORTAL)


# ===================================================================
# PHOTON (ID 66) -- bright golden/yellow body, light emanating,
#                   energy aura, bright glowing core, light trail
# ===================================================================

# Photon palette
PHOT_BODY = (255, 220, 80)
PHOT_BODY_DARK = (220, 180, 40)
PHOT_BODY_LIGHT = (255, 240, 140)
PHOT_CORE = (255, 255, 200)
PHOT_CORE_BRIGHT = (255, 255, 255)
PHOT_AURA = (255, 240, 120, 100)
PHOT_AURA_BRIGHT = (255, 250, 160, 140)
PHOT_EYE = (255, 255, 255)
PHOT_EYE_PUPIL = (255, 200, 0)
PHOT_TRAIL = (255, 230, 100, 80)
PHOT_TRAIL_DIM = (255, 220, 80, 50)
PHOT_BEAM = (255, 250, 180, 160)


def draw_photon(draw, ox, oy, direction, frame):
    bob = [0, -2, 0, -1][frame]
    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 20
    head_cy = body_cy - 18

    glow_pulse = [0, 1, 2, 1][frame]

    # --- Outer aura glow (drawn first, behind everything) ---
    aura_r = 22 + glow_pulse
    ellipse(draw, cx, body_cy - 4, aura_r, aura_r + 4, PHOT_AURA, outline=None)
    ellipse(draw, cx, body_cy - 4, aura_r - 4, aura_r, PHOT_AURA_BRIGHT, outline=None)

    # --- Legs ---
    if direction in (DOWN, UP):
        leg_spread = [-3, 0, 3, 0][frame]
        for side in [-1, 1]:
            ls = leg_spread if side == -1 else -leg_spread
            lx = cx + side * 6 + ls
            draw.rectangle([lx - 3, body_cy + 10, lx + 3, base_y],
                           fill=PHOT_BODY, outline=OUTLINE)
            draw.rectangle([lx - 2, body_cy + 12, lx, base_y - 2],
                           fill=PHOT_BODY_LIGHT, outline=None)
            # Bright boots
            draw.rectangle([lx - 4, base_y - 4, lx + 4, base_y],
                           fill=PHOT_BODY_LIGHT, outline=OUTLINE)
    else:
        off = -2 if direction == LEFT else 2
        draw.rectangle([cx + off - 3, body_cy + 10, cx + off + 3, base_y],
                       fill=PHOT_BODY, outline=OUTLINE)
        draw.rectangle([cx + off - 6, body_cy + 13, cx + off, base_y],
                       fill=PHOT_BODY, outline=OUTLINE)
        draw.rectangle([cx + off - 4, base_y - 4, cx + off + 4, base_y],
                       fill=PHOT_BODY_LIGHT, outline=OUTLINE)

    # --- Body (glowing energy form) ---
    pill(draw, cx, body_cy, 11, 14, PHOT_BODY, outline=OUTLINE)
    pill(draw, cx, body_cy - 1, 7, 9, PHOT_BODY_LIGHT, outline=None)
    # Glowing core in chest
    ellipse(draw, cx, body_cy, 4, 4, PHOT_CORE, outline=None)
    ellipse(draw, cx, body_cy, 2, 2, PHOT_CORE_BRIGHT, outline=None)

    # --- Arms ---
    if direction in (DOWN, UP):
        arm_swing = [-2, 0, 2, 0][frame]
        for side in [-1, 1]:
            ax = cx + side * 13
            ay_off = arm_swing if side == -1 else -arm_swing
            draw.rectangle([ax - 3, body_cy - 5 + ay_off, ax + 3, body_cy + 8 + ay_off],
                           fill=PHOT_BODY, outline=OUTLINE)
            # Light energy at hand
            draw.rectangle([ax - 2, body_cy + 6 + ay_off, ax + 2, body_cy + 10 + ay_off],
                           fill=PHOT_CORE, outline=None)
    elif direction == LEFT:
        draw.rectangle([cx + 6, body_cy - 5, cx + 12, body_cy + 8],
                       fill=PHOT_BODY, outline=OUTLINE)
        draw.rectangle([cx + 7, body_cy + 6, cx + 11, body_cy + 10],
                       fill=PHOT_CORE, outline=None)
    else:  # RIGHT
        draw.rectangle([cx - 12, body_cy - 5, cx - 6, body_cy + 8],
                       fill=PHOT_BODY, outline=OUTLINE)
        draw.rectangle([cx - 11, body_cy + 6, cx - 7, body_cy + 10],
                       fill=PHOT_CORE, outline=None)

    # --- Head ---
    if direction == DOWN:
        ellipse(draw, cx, head_cy, 10, 10, PHOT_BODY, outline=OUTLINE)
        ellipse(draw, cx, head_cy - 1, 6, 6, PHOT_BODY_LIGHT, outline=None)
        # Eyes
        draw.rectangle([cx - 5, head_cy, cx - 2, head_cy + 3], fill=PHOT_EYE)
        draw.rectangle([cx + 2, head_cy, cx + 5, head_cy + 3], fill=PHOT_EYE)
        draw.point((cx - 4, head_cy + 1), fill=PHOT_EYE_PUPIL)
        draw.point((cx + 3, head_cy + 1), fill=PHOT_EYE_PUPIL)
        # Light crown
        for cx_off in [-6, -3, 0, 3, 6]:
            h = 2 + (frame + abs(cx_off)) % 3
            draw.rectangle([cx + cx_off - 1, head_cy - 10 - h,
                            cx + cx_off + 1, head_cy - 8],
                           fill=PHOT_CORE, outline=None)
    elif direction == UP:
        ellipse(draw, cx, head_cy, 10, 10, PHOT_BODY, outline=OUTLINE)
        ellipse(draw, cx, head_cy, 7, 7, PHOT_BODY_DARK, outline=None)
        for cx_off in [-6, -3, 0, 3, 6]:
            h = 2 + (frame + abs(cx_off)) % 3
            draw.rectangle([cx + cx_off - 1, head_cy - 10 - h,
                            cx + cx_off + 1, head_cy - 8],
                           fill=PHOT_CORE, outline=None)
    elif direction == LEFT:
        ellipse(draw, cx - 2, head_cy, 10, 10, PHOT_BODY, outline=OUTLINE)
        ellipse(draw, cx - 4, head_cy - 1, 6, 6, PHOT_BODY_LIGHT, outline=None)
        draw.rectangle([cx - 8, head_cy, cx - 5, head_cy + 3], fill=PHOT_EYE)
        draw.point((cx - 7, head_cy + 1), fill=PHOT_EYE_PUPIL)
        for cx_off in [-4, -1, 2]:
            h = 2 + (frame + abs(cx_off)) % 3
            draw.rectangle([cx + cx_off - 1, head_cy - 10 - h,
                            cx + cx_off + 1, head_cy - 8],
                           fill=PHOT_CORE, outline=None)
    else:  # RIGHT
        ellipse(draw, cx + 2, head_cy, 10, 10, PHOT_BODY, outline=OUTLINE)
        ellipse(draw, cx, head_cy - 1, 6, 6, PHOT_BODY_LIGHT, outline=None)
        draw.rectangle([cx + 5, head_cy, cx + 8, head_cy + 3], fill=PHOT_EYE)
        draw.point((cx + 6, head_cy + 1), fill=PHOT_EYE_PUPIL)
        for cx_off in [-2, 1, 4]:
            h = 2 + (frame + abs(cx_off)) % 3
            draw.rectangle([cx + cx_off - 1, head_cy - 10 - h,
                            cx + cx_off + 1, head_cy - 8],
                           fill=PHOT_CORE, outline=None)

    # --- Light trail particles ---
    trail_positions = [
        [(-14, 10), (16, 12), (-10, -16), (12, -14), (0, 16)],
        [(-16, 12), (14, 10), (-12, -14), (10, -16), (2, 14)],
        [(-12, 14), (18, 8), (-14, -12), (8, -18), (-2, 18)],
        [(-18, 8), (12, 14), (-8, -18), (14, -12), (4, 12)],
    ]
    for tx, ty in trail_positions[frame]:
        draw.point((cx + tx, body_cy + ty), fill=PHOT_TRAIL)
        draw.point((cx + tx + 1, body_cy + ty + 1), fill=PHOT_TRAIL_DIM)


# ===================================================================
# RAILGUNNER (ID 67) -- military armor, large railgun barrel,
#                       targeting visor, heavy boots, ammo belt
# ===================================================================

# Railgunner palette
RAIL_ARMOR = (80, 95, 65)
RAIL_ARMOR_DARK = (55, 68, 42)
RAIL_ARMOR_LIGHT = (105, 120, 85)
RAIL_SKIN = (190, 170, 150)
RAIL_SKIN_DARK = (160, 140, 120)
RAIL_VISOR = (200, 40, 40)
RAIL_VISOR_BRIGHT = (255, 80, 60)
RAIL_GUN_BODY = (90, 90, 100)
RAIL_GUN_DARK = (60, 60, 70)
RAIL_GUN_BARREL = (70, 70, 80)
RAIL_GUN_GLOW = (60, 140, 220)
RAIL_BOOT = (50, 55, 40)
RAIL_BELT = (120, 100, 60)
RAIL_AMMO = (180, 160, 60)


def draw_railgunner(draw, ox, oy, direction, frame):
    bob = [0, -2, 0, -1][frame]
    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 20
    head_cy = body_cy - 18

    leg_spread = [-3, 0, 3, 0][frame]

    # --- Legs (heavy military boots) ---
    if direction in (DOWN, UP):
        for side in [-1, 1]:
            ls = leg_spread if side == -1 else -leg_spread
            lx = cx + side * 6 + ls
            draw.rectangle([lx - 4, body_cy + 10, lx + 4, base_y],
                           fill=RAIL_ARMOR_DARK, outline=OUTLINE)
            # Shin guard
            draw.rectangle([lx - 3, body_cy + 12, lx + 3, body_cy + 18],
                           fill=RAIL_ARMOR, outline=None)
            # Heavy boots
            draw.rectangle([lx - 5, base_y - 5, lx + 5, base_y],
                           fill=RAIL_BOOT, outline=OUTLINE)
            # Boot buckle
            draw.rectangle([lx - 2, base_y - 4, lx + 2, base_y - 2],
                           fill=RAIL_BELT, outline=None)
    else:
        off = -2 if direction == LEFT else 2
        draw.rectangle([cx + off - 4, body_cy + 10, cx + off + 4, base_y],
                       fill=RAIL_ARMOR_DARK, outline=OUTLINE)
        draw.rectangle([cx + off - 7, body_cy + 13, cx + off + 1, base_y],
                       fill=RAIL_ARMOR_DARK, outline=OUTLINE)
        draw.rectangle([cx + off - 5, base_y - 5, cx + off + 5, base_y],
                       fill=RAIL_BOOT, outline=OUTLINE)

    # --- Body (armored torso) ---
    pill(draw, cx, body_cy, 12, 14, RAIL_ARMOR, outline=OUTLINE)
    # Chest plate highlight
    pill(draw, cx - 1, body_cy - 2, 7, 8, RAIL_ARMOR_LIGHT, outline=None)
    # Ammo belt across chest
    if direction in (DOWN, LEFT, RIGHT):
        bx1 = cx - 10
        bx2 = cx + 10
        draw.rectangle([bx1, body_cy + 2, bx2, body_cy + 5],
                       fill=RAIL_BELT, outline=None)
        # Ammo rounds on belt
        for bx in range(bx1 + 2, bx2 - 1, 4):
            draw.rectangle([bx, body_cy + 2, bx + 2, body_cy + 5],
                           fill=RAIL_AMMO, outline=None)

    # --- Arms ---
    if direction in (DOWN, UP):
        for side in [-1, 1]:
            ax = cx + side * 13
            draw.rectangle([ax - 4, body_cy - 6, ax + 4, body_cy + 8],
                           fill=RAIL_ARMOR, outline=OUTLINE)
            # Shoulder pauldron
            draw.rectangle([ax - 5, body_cy - 8, ax + 5, body_cy - 4],
                           fill=RAIL_ARMOR_LIGHT, outline=OUTLINE)
            # Glove
            draw.rectangle([ax - 3, body_cy + 6, ax + 3, body_cy + 10],
                           fill=RAIL_ARMOR_DARK, outline=OUTLINE)
    elif direction == LEFT:
        draw.rectangle([cx + 6, body_cy - 6, cx + 14, body_cy + 8],
                       fill=RAIL_ARMOR, outline=OUTLINE)
        draw.rectangle([cx + 5, body_cy - 8, cx + 15, body_cy - 4],
                       fill=RAIL_ARMOR_LIGHT, outline=OUTLINE)
    else:  # RIGHT
        draw.rectangle([cx - 14, body_cy - 6, cx - 6, body_cy + 8],
                       fill=RAIL_ARMOR, outline=OUTLINE)
        draw.rectangle([cx - 15, body_cy - 8, cx - 5, body_cy - 4],
                       fill=RAIL_ARMOR_LIGHT, outline=OUTLINE)

    # --- Railgun (on shoulder/back) ---
    if direction == DOWN:
        # Gun barrel visible over right shoulder
        draw.rectangle([cx + 8, body_cy - 20, cx + 12, body_cy - 4],
                       fill=RAIL_GUN_BODY, outline=OUTLINE)
        draw.rectangle([cx + 9, body_cy - 26, cx + 11, body_cy - 18],
                       fill=RAIL_GUN_BARREL, outline=OUTLINE)
        # Glow at barrel tip
        draw.rectangle([cx + 9, body_cy - 28, cx + 11, body_cy - 26],
                       fill=RAIL_GUN_GLOW, outline=None)
    elif direction == UP:
        # Gun barrel over left shoulder
        draw.rectangle([cx - 12, body_cy - 20, cx - 8, body_cy - 4],
                       fill=RAIL_GUN_BODY, outline=OUTLINE)
        draw.rectangle([cx - 11, body_cy - 26, cx - 9, body_cy - 18],
                       fill=RAIL_GUN_BARREL, outline=OUTLINE)
        draw.rectangle([cx - 11, body_cy - 28, cx - 9, body_cy - 26],
                       fill=RAIL_GUN_GLOW, outline=None)
    elif direction == LEFT:
        # Gun barrel extends to the left from shoulder
        draw.rectangle([cx - 6, body_cy - 14, cx + 4, body_cy - 10],
                       fill=RAIL_GUN_BODY, outline=OUTLINE)
        draw.rectangle([cx - 18, body_cy - 13, cx - 6, body_cy - 11],
                       fill=RAIL_GUN_BARREL, outline=OUTLINE)
        draw.rectangle([cx - 20, body_cy - 13, cx - 18, body_cy - 11],
                       fill=RAIL_GUN_GLOW, outline=None)
    else:  # RIGHT
        draw.rectangle([cx - 4, body_cy - 14, cx + 6, body_cy - 10],
                       fill=RAIL_GUN_BODY, outline=OUTLINE)
        draw.rectangle([cx + 6, body_cy - 13, cx + 18, body_cy - 11],
                       fill=RAIL_GUN_BARREL, outline=OUTLINE)
        draw.rectangle([cx + 18, body_cy - 13, cx + 20, body_cy - 11],
                       fill=RAIL_GUN_GLOW, outline=None)

    # --- Head (helmet with targeting visor) ---
    if direction == DOWN:
        ellipse(draw, cx, head_cy, 10, 10, RAIL_ARMOR, outline=OUTLINE)
        # Helmet ridge
        draw.rectangle([cx - 3, head_cy - 10, cx + 3, head_cy - 8],
                       fill=RAIL_ARMOR_LIGHT, outline=None)
        # Targeting visor (red strip)
        draw.rectangle([cx - 7, head_cy, cx + 7, head_cy + 3],
                       fill=RAIL_VISOR, outline=None)
        draw.rectangle([cx - 5, head_cy + 1, cx + 5, head_cy + 2],
                       fill=RAIL_VISOR_BRIGHT, outline=None)
    elif direction == UP:
        ellipse(draw, cx, head_cy, 10, 10, RAIL_ARMOR, outline=OUTLINE)
        draw.rectangle([cx - 3, head_cy - 10, cx + 3, head_cy - 8],
                       fill=RAIL_ARMOR_LIGHT, outline=None)
        ellipse(draw, cx, head_cy, 7, 7, RAIL_ARMOR_DARK, outline=None)
    elif direction == LEFT:
        ellipse(draw, cx - 2, head_cy, 10, 10, RAIL_ARMOR, outline=OUTLINE)
        draw.rectangle([cx - 5, head_cy - 10, cx + 1, head_cy - 8],
                       fill=RAIL_ARMOR_LIGHT, outline=None)
        # Side visor
        draw.rectangle([cx - 10, head_cy, cx - 4, head_cy + 3],
                       fill=RAIL_VISOR, outline=None)
        draw.rectangle([cx - 9, head_cy + 1, cx - 5, head_cy + 2],
                       fill=RAIL_VISOR_BRIGHT, outline=None)
    else:  # RIGHT
        ellipse(draw, cx + 2, head_cy, 10, 10, RAIL_ARMOR, outline=OUTLINE)
        draw.rectangle([cx - 1, head_cy - 10, cx + 5, head_cy - 8],
                       fill=RAIL_ARMOR_LIGHT, outline=None)
        draw.rectangle([cx + 4, head_cy, cx + 10, head_cy + 3],
                       fill=RAIL_VISOR, outline=None)
        draw.rectangle([cx + 5, head_cy + 1, cx + 9, head_cy + 2],
                       fill=RAIL_VISOR_BRIGHT, outline=None)


# ===================================================================
# BOMBARDIER (ID 68) -- brown/tan military gear, explosive belt,
#                       bomber helmet with goggles, ammo pouches
# ===================================================================

# Bombardier palette
BOMB_GEAR = (140, 120, 80)
BOMB_GEAR_DARK = (105, 90, 55)
BOMB_GEAR_LIGHT = (175, 155, 110)
BOMB_SKIN = (200, 180, 155)
BOMB_SKIN_DARK = (170, 150, 125)
BOMB_HELMET = (120, 105, 70)
BOMB_HELMET_DARK = (90, 78, 50)
BOMB_GOGGLES = (180, 160, 100)
BOMB_GOGGLE_LENS = (140, 200, 220)
BOMB_GRENADE = (70, 80, 50)
BOMB_GRENADE_PIN = (200, 200, 60)
BOMB_POUCH = (110, 95, 60)
BOMB_BLAST = (220, 160, 40, 100)
BOMB_BOOT = (80, 70, 45)
BOMB_BELT = (90, 80, 50)


def draw_bombardier(draw, ox, oy, direction, frame):
    bob = [0, -2, 0, -1][frame]
    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 20
    head_cy = body_cy - 18

    leg_spread = [-3, 0, 3, 0][frame]

    # --- Legs ---
    if direction in (DOWN, UP):
        for side in [-1, 1]:
            ls = leg_spread if side == -1 else -leg_spread
            lx = cx + side * 6 + ls
            draw.rectangle([lx - 4, body_cy + 10, lx + 4, base_y],
                           fill=BOMB_GEAR_DARK, outline=OUTLINE)
            # Cargo pocket on thigh
            if direction == DOWN:
                draw.rectangle([lx - 3, body_cy + 14, lx + 1, body_cy + 18],
                               fill=BOMB_POUCH, outline=OUTLINE)
            # Heavy boots
            draw.rectangle([lx - 5, base_y - 5, lx + 5, base_y],
                           fill=BOMB_BOOT, outline=OUTLINE)
    else:
        off = -2 if direction == LEFT else 2
        draw.rectangle([cx + off - 4, body_cy + 10, cx + off + 4, base_y],
                       fill=BOMB_GEAR_DARK, outline=OUTLINE)
        draw.rectangle([cx + off - 7, body_cy + 13, cx + off + 1, base_y],
                       fill=BOMB_GEAR_DARK, outline=OUTLINE)
        # Cargo pocket
        if direction == LEFT:
            draw.rectangle([cx + off + 1, body_cy + 14, cx + off + 5, body_cy + 18],
                           fill=BOMB_POUCH, outline=OUTLINE)
        else:
            draw.rectangle([cx + off - 5, body_cy + 14, cx + off - 1, body_cy + 18],
                           fill=BOMB_POUCH, outline=OUTLINE)
        draw.rectangle([cx + off - 5, base_y - 5, cx + off + 5, base_y],
                       fill=BOMB_BOOT, outline=OUTLINE)

    # --- Body (tactical vest) ---
    pill(draw, cx, body_cy, 12, 14, BOMB_GEAR, outline=OUTLINE)
    pill(draw, cx - 1, body_cy - 2, 7, 8, BOMB_GEAR_LIGHT, outline=None)

    # Explosive belt with grenades
    draw.rectangle([cx - 11, body_cy + 6, cx + 11, body_cy + 9],
                   fill=BOMB_BELT, outline=OUTLINE)
    # Grenades on belt
    for gx in [-8, -3, 3, 8]:
        draw.rectangle([cx + gx - 2, body_cy + 3, cx + gx + 2, body_cy + 7],
                       fill=BOMB_GRENADE, outline=OUTLINE)
        # Pin
        draw.point((cx + gx, body_cy + 3), fill=BOMB_GRENADE_PIN)

    # Ammo pouches on sides
    if direction in (DOWN, LEFT, RIGHT):
        draw.rectangle([cx - 12, body_cy - 2, cx - 9, body_cy + 4],
                       fill=BOMB_POUCH, outline=OUTLINE)
        draw.rectangle([cx + 9, body_cy - 2, cx + 12, body_cy + 4],
                       fill=BOMB_POUCH, outline=OUTLINE)

    # --- Arms ---
    if direction in (DOWN, UP):
        arm_swing = [-1, 0, 1, 0][frame]
        for side in [-1, 1]:
            ax = cx + side * 13
            ay_off = arm_swing if side == -1 else -arm_swing
            draw.rectangle([ax - 4, body_cy - 5 + ay_off, ax + 4, body_cy + 8 + ay_off],
                           fill=BOMB_GEAR, outline=OUTLINE)
            # Gloves
            draw.rectangle([ax - 3, body_cy + 6 + ay_off, ax + 3, body_cy + 10 + ay_off],
                           fill=BOMB_GEAR_DARK, outline=OUTLINE)
    elif direction == LEFT:
        draw.rectangle([cx + 6, body_cy - 5, cx + 14, body_cy + 8],
                       fill=BOMB_GEAR, outline=OUTLINE)
        draw.rectangle([cx + 7, body_cy + 6, cx + 13, body_cy + 10],
                       fill=BOMB_GEAR_DARK, outline=OUTLINE)
    else:  # RIGHT
        draw.rectangle([cx - 14, body_cy - 5, cx - 6, body_cy + 8],
                       fill=BOMB_GEAR, outline=OUTLINE)
        draw.rectangle([cx - 13, body_cy + 6, cx - 7, body_cy + 10],
                       fill=BOMB_GEAR_DARK, outline=OUTLINE)

    # --- Head (bomber helmet with goggles) ---
    if direction == DOWN:
        ellipse(draw, cx, head_cy, 10, 10, BOMB_HELMET, outline=OUTLINE)
        # Helmet top ridge
        draw.rectangle([cx - 6, head_cy - 10, cx + 6, head_cy - 7],
                       fill=BOMB_HELMET_DARK, outline=None)
        # Goggles
        draw.rectangle([cx - 7, head_cy - 2, cx - 2, head_cy + 2],
                       fill=BOMB_GOGGLES, outline=OUTLINE)
        draw.rectangle([cx + 2, head_cy - 2, cx + 7, head_cy + 2],
                       fill=BOMB_GOGGLES, outline=OUTLINE)
        # Goggle lenses
        draw.rectangle([cx - 6, head_cy - 1, cx - 3, head_cy + 1],
                       fill=BOMB_GOGGLE_LENS, outline=None)
        draw.rectangle([cx + 3, head_cy - 1, cx + 6, head_cy + 1],
                       fill=BOMB_GOGGLE_LENS, outline=None)
        # Chin/jaw
        draw.rectangle([cx - 4, head_cy + 4, cx + 4, head_cy + 7],
                       fill=BOMB_SKIN, outline=None)
    elif direction == UP:
        ellipse(draw, cx, head_cy, 10, 10, BOMB_HELMET, outline=OUTLINE)
        draw.rectangle([cx - 6, head_cy - 10, cx + 6, head_cy - 7],
                       fill=BOMB_HELMET_DARK, outline=None)
        ellipse(draw, cx, head_cy, 7, 7, BOMB_HELMET_DARK, outline=None)
    elif direction == LEFT:
        ellipse(draw, cx - 2, head_cy, 10, 10, BOMB_HELMET, outline=OUTLINE)
        draw.rectangle([cx - 5, head_cy - 10, cx + 3, head_cy - 7],
                       fill=BOMB_HELMET_DARK, outline=None)
        # Side goggle
        draw.rectangle([cx - 10, head_cy - 2, cx - 5, head_cy + 2],
                       fill=BOMB_GOGGLES, outline=OUTLINE)
        draw.rectangle([cx - 9, head_cy - 1, cx - 6, head_cy + 1],
                       fill=BOMB_GOGGLE_LENS, outline=None)
        draw.rectangle([cx - 6, head_cy + 4, cx - 1, head_cy + 7],
                       fill=BOMB_SKIN, outline=None)
    else:  # RIGHT
        ellipse(draw, cx + 2, head_cy, 10, 10, BOMB_HELMET, outline=OUTLINE)
        draw.rectangle([cx - 3, head_cy - 10, cx + 5, head_cy - 7],
                       fill=BOMB_HELMET_DARK, outline=None)
        draw.rectangle([cx + 5, head_cy - 2, cx + 10, head_cy + 2],
                       fill=BOMB_GOGGLES, outline=OUTLINE)
        draw.rectangle([cx + 6, head_cy - 1, cx + 9, head_cy + 1],
                       fill=BOMB_GOGGLE_LENS, outline=None)
        draw.rectangle([cx + 1, head_cy + 4, cx + 6, head_cy + 7],
                       fill=BOMB_SKIN, outline=None)

    # --- Blast mark particles (subtle) ---
    blast_pts = [
        [(-16, 8), (18, 6), (-12, -12)],
        [(-18, 6), (16, 8), (-14, -10)],
        [(-14, 10), (20, 4), (-10, -14)],
        [(-20, 4), (14, 10), (-16, -8)],
    ]
    for bx, by in blast_pts[frame]:
        draw.point((cx + bx, body_cy + by), fill=BOMB_BLAST)


# ===================================================================
# SENTINEL (ID 69) -- blue/gray heavy armor, energy shield projector,
#                     visor helm, sentinel stance, blue energy accents
# ===================================================================

# Sentinel palette
SENT_ARMOR = (100, 115, 140)
SENT_ARMOR_DARK = (70, 82, 105)
SENT_ARMOR_LIGHT = (135, 150, 175)
SENT_VISOR = (60, 160, 255)
SENT_VISOR_BRIGHT = (120, 200, 255)
SENT_ENERGY = (80, 180, 255, 140)
SENT_ENERGY_BRIGHT = (140, 220, 255, 180)
SENT_SHIELD = (60, 140, 220, 120)
SENT_SHIELD_EDGE = (100, 180, 255, 160)
SENT_BOOT = (60, 70, 90)
SENT_JOINT = (80, 90, 110)
SENT_ACCENT = (40, 120, 200)


def draw_sentinel(draw, ox, oy, direction, frame):
    bob = [0, -2, 0, -1][frame]
    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 20
    head_cy = body_cy - 18

    leg_spread = [-3, 0, 3, 0][frame]
    shield_pulse = [0, 1, 2, 1][frame]

    # --- Legs (heavy plated) ---
    if direction in (DOWN, UP):
        for side in [-1, 1]:
            ls = leg_spread if side == -1 else -leg_spread
            lx = cx + side * 6 + ls
            draw.rectangle([lx - 4, body_cy + 10, lx + 4, base_y],
                           fill=SENT_ARMOR, outline=OUTLINE)
            # Knee plate
            draw.rectangle([lx - 5, body_cy + 14, lx + 5, body_cy + 18],
                           fill=SENT_ARMOR_LIGHT, outline=OUTLINE)
            # Blue energy line on leg
            draw.line([(lx, body_cy + 12), (lx, base_y - 6)],
                      fill=SENT_ACCENT, width=1)
            # Heavy boots
            draw.rectangle([lx - 5, base_y - 5, lx + 5, base_y],
                           fill=SENT_BOOT, outline=OUTLINE)
    else:
        off = -2 if direction == LEFT else 2
        draw.rectangle([cx + off - 4, body_cy + 10, cx + off + 4, base_y],
                       fill=SENT_ARMOR, outline=OUTLINE)
        draw.rectangle([cx + off - 7, body_cy + 13, cx + off + 1, base_y],
                       fill=SENT_ARMOR, outline=OUTLINE)
        draw.rectangle([cx + off - 5, body_cy + 14, cx + off + 5, body_cy + 18],
                       fill=SENT_ARMOR_LIGHT, outline=OUTLINE)
        draw.rectangle([cx + off - 5, base_y - 5, cx + off + 5, base_y],
                       fill=SENT_BOOT, outline=OUTLINE)

    # --- Body (heavy chest plate) ---
    pill(draw, cx, body_cy, 13, 15, SENT_ARMOR, outline=OUTLINE)
    pill(draw, cx - 1, body_cy - 2, 8, 9, SENT_ARMOR_LIGHT, outline=None)
    # Blue energy core on chest
    if direction in (DOWN, LEFT, RIGHT):
        ellipse(draw, cx, body_cy, 4, 4, SENT_ENERGY, outline=None)
        ellipse(draw, cx, body_cy, 2, 2, SENT_ENERGY_BRIGHT, outline=None)
    # Blue accent lines
    draw.line([(cx - 10, body_cy - 4), (cx - 10, body_cy + 8)],
              fill=SENT_ACCENT, width=1)
    draw.line([(cx + 10, body_cy - 4), (cx + 10, body_cy + 8)],
              fill=SENT_ACCENT, width=1)

    # --- Arms ---
    if direction in (DOWN, UP):
        for side in [-1, 1]:
            ax = cx + side * 14
            draw.rectangle([ax - 4, body_cy - 6, ax + 4, body_cy + 8],
                           fill=SENT_ARMOR, outline=OUTLINE)
            # Shoulder pauldron
            draw.rectangle([ax - 6, body_cy - 9, ax + 6, body_cy - 4],
                           fill=SENT_ARMOR_LIGHT, outline=OUTLINE)
            draw.rectangle([ax - 4, body_cy - 8, ax + 4, body_cy - 5],
                           fill=SENT_ACCENT, outline=None)
            # Gauntlet
            draw.rectangle([ax - 3, body_cy + 6, ax + 3, body_cy + 10],
                           fill=SENT_JOINT, outline=OUTLINE)
    elif direction == LEFT:
        # Rear arm
        draw.rectangle([cx + 6, body_cy - 6, cx + 14, body_cy + 8],
                       fill=SENT_ARMOR, outline=OUTLINE)
        draw.rectangle([cx + 5, body_cy - 9, cx + 15, body_cy - 4],
                       fill=SENT_ARMOR_LIGHT, outline=OUTLINE)
        # Front arm with shield projector
        draw.rectangle([cx - 14, body_cy - 6, cx - 6, body_cy + 8],
                       fill=SENT_ARMOR, outline=OUTLINE)
        draw.rectangle([cx - 15, body_cy - 9, cx - 5, body_cy - 4],
                       fill=SENT_ARMOR_LIGHT, outline=OUTLINE)
    else:  # RIGHT
        draw.rectangle([cx - 14, body_cy - 6, cx - 6, body_cy + 8],
                       fill=SENT_ARMOR, outline=OUTLINE)
        draw.rectangle([cx - 15, body_cy - 9, cx - 5, body_cy - 4],
                       fill=SENT_ARMOR_LIGHT, outline=OUTLINE)
        draw.rectangle([cx + 6, body_cy - 6, cx + 14, body_cy + 8],
                       fill=SENT_ARMOR, outline=OUTLINE)
        draw.rectangle([cx + 5, body_cy - 9, cx + 15, body_cy - 4],
                       fill=SENT_ARMOR_LIGHT, outline=OUTLINE)

    # --- Energy shield projector (on left arm / front) ---
    shield_r = 8 + shield_pulse
    if direction == DOWN:
        shield_cx = cx - 14
        shield_cy = body_cy + 2
        ellipse(draw, shield_cx - 4, shield_cy, shield_r, shield_r + 2,
                SENT_SHIELD, outline=None)
        ellipse(draw, shield_cx - 4, shield_cy, shield_r - 3, shield_r - 1,
                SENT_SHIELD_EDGE, outline=None)
    elif direction == LEFT:
        shield_cx = cx - 16
        shield_cy = body_cy
        ellipse(draw, shield_cx, shield_cy, shield_r + 2, shield_r + 4,
                SENT_SHIELD, outline=None)
        ellipse(draw, shield_cx, shield_cy, shield_r - 1, shield_r + 1,
                SENT_SHIELD_EDGE, outline=None)
    elif direction == RIGHT:
        shield_cx = cx + 16
        shield_cy = body_cy
        ellipse(draw, shield_cx, shield_cy, shield_r + 2, shield_r + 4,
                SENT_SHIELD, outline=None)
        ellipse(draw, shield_cx, shield_cy, shield_r - 1, shield_r + 1,
                SENT_SHIELD_EDGE, outline=None)

    # --- Head (visor helm) ---
    if direction == DOWN:
        ellipse(draw, cx, head_cy, 11, 10, SENT_ARMOR, outline=OUTLINE)
        # Helmet crest
        draw.rectangle([cx - 3, head_cy - 11, cx + 3, head_cy - 8],
                       fill=SENT_ARMOR_LIGHT, outline=None)
        # T-shaped visor
        draw.rectangle([cx - 7, head_cy - 1, cx + 7, head_cy + 2],
                       fill=SENT_VISOR, outline=None)
        draw.rectangle([cx - 1, head_cy + 2, cx + 1, head_cy + 6],
                       fill=SENT_VISOR, outline=None)
        draw.rectangle([cx - 5, head_cy, cx + 5, head_cy + 1],
                       fill=SENT_VISOR_BRIGHT, outline=None)
    elif direction == UP:
        ellipse(draw, cx, head_cy, 11, 10, SENT_ARMOR, outline=OUTLINE)
        draw.rectangle([cx - 3, head_cy - 11, cx + 3, head_cy - 8],
                       fill=SENT_ARMOR_LIGHT, outline=None)
        ellipse(draw, cx, head_cy, 8, 7, SENT_ARMOR_DARK, outline=None)
    elif direction == LEFT:
        ellipse(draw, cx - 2, head_cy, 11, 10, SENT_ARMOR, outline=OUTLINE)
        draw.rectangle([cx - 5, head_cy - 11, cx + 1, head_cy - 8],
                       fill=SENT_ARMOR_LIGHT, outline=None)
        # Side visor
        draw.rectangle([cx - 11, head_cy - 1, cx - 4, head_cy + 2],
                       fill=SENT_VISOR, outline=None)
        draw.rectangle([cx - 10, head_cy, cx - 5, head_cy + 1],
                       fill=SENT_VISOR_BRIGHT, outline=None)
    else:  # RIGHT
        ellipse(draw, cx + 2, head_cy, 11, 10, SENT_ARMOR, outline=OUTLINE)
        draw.rectangle([cx - 1, head_cy - 11, cx + 5, head_cy - 8],
                       fill=SENT_ARMOR_LIGHT, outline=None)
        draw.rectangle([cx + 4, head_cy - 1, cx + 11, head_cy + 2],
                       fill=SENT_VISOR, outline=None)
        draw.rectangle([cx + 5, head_cy, cx + 10, head_cy + 1],
                       fill=SENT_VISOR_BRIGHT, outline=None)


# ===================================================================
# PILOT (ID 70) -- flight suit, aviator helmet with visor, flight
#                  jacket, goggles, oxygen mask area, wing patches
# ===================================================================

# Pilot palette
PLT_SUIT = (80, 95, 60)
PLT_SUIT_DARK = (55, 68, 38)
PLT_SUIT_LIGHT = (110, 125, 85)
PLT_SKIN = (200, 180, 160)
PLT_SKIN_DARK = (170, 150, 130)
PLT_HELMET = (60, 70, 55)
PLT_HELMET_LIGHT = (85, 95, 75)
PLT_VISOR = (140, 180, 200)
PLT_VISOR_BRIGHT = (180, 210, 230)
PLT_JACKET = (90, 75, 50)
PLT_JACKET_DARK = (65, 55, 35)
PLT_GOGGLES = (160, 140, 90)
PLT_GOGGLE_LENS = (100, 160, 200)
PLT_MASK = (70, 75, 65)
PLT_PATCH = (180, 160, 50)
PLT_BOOT = (55, 50, 35)
PLT_WING = (200, 180, 80)


def draw_pilot(draw, ox, oy, direction, frame):
    bob = [0, -2, 0, -1][frame]
    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 20
    head_cy = body_cy - 18

    leg_spread = [-3, 0, 3, 0][frame]

    # --- Legs ---
    if direction in (DOWN, UP):
        for side in [-1, 1]:
            ls = leg_spread if side == -1 else -leg_spread
            lx = cx + side * 6 + ls
            draw.rectangle([lx - 3, body_cy + 10, lx + 3, base_y],
                           fill=PLT_SUIT, outline=OUTLINE)
            # Flight suit stripe
            draw.line([(lx + 2, body_cy + 12), (lx + 2, base_y - 5)],
                      fill=PLT_SUIT_LIGHT, width=1)
            # Boots
            draw.rectangle([lx - 4, base_y - 5, lx + 4, base_y],
                           fill=PLT_BOOT, outline=OUTLINE)
    else:
        off = -2 if direction == LEFT else 2
        draw.rectangle([cx + off - 3, body_cy + 10, cx + off + 3, base_y],
                       fill=PLT_SUIT, outline=OUTLINE)
        draw.rectangle([cx + off - 6, body_cy + 13, cx + off, base_y],
                       fill=PLT_SUIT, outline=OUTLINE)
        draw.rectangle([cx + off - 4, base_y - 5, cx + off + 4, base_y],
                       fill=PLT_BOOT, outline=OUTLINE)

    # --- Body (flight jacket over suit) ---
    pill(draw, cx, body_cy, 11, 14, PLT_JACKET, outline=OUTLINE)
    pill(draw, cx - 1, body_cy - 2, 7, 8, PLT_JACKET_DARK, outline=None)
    # Jacket zipper
    if direction in (DOWN, LEFT, RIGHT):
        draw.line([(cx, body_cy - 8), (cx, body_cy + 10)],
                  fill=_brighten(PLT_JACKET, 1.3), width=1)
    # Wing/patch on chest
    if direction == DOWN:
        draw.rectangle([cx - 8, body_cy - 4, cx - 4, body_cy - 1],
                       fill=PLT_PATCH, outline=None)
        # Wing shape (small V)
        draw.line([(cx - 8, body_cy - 2), (cx - 6, body_cy - 4)],
                  fill=PLT_WING, width=1)
        draw.line([(cx - 6, body_cy - 4), (cx - 4, body_cy - 2)],
                  fill=PLT_WING, width=1)
    elif direction == LEFT:
        draw.rectangle([cx + 2, body_cy - 4, cx + 6, body_cy - 1],
                       fill=PLT_PATCH, outline=None)
    elif direction == RIGHT:
        draw.rectangle([cx - 6, body_cy - 4, cx - 2, body_cy - 1],
                       fill=PLT_PATCH, outline=None)

    # Suit collar
    draw.rectangle([cx - 6, body_cy - 10, cx + 6, body_cy - 7],
                   fill=PLT_SUIT_LIGHT, outline=None)

    # --- Arms ---
    if direction in (DOWN, UP):
        arm_swing = [-1, 0, 1, 0][frame]
        for side in [-1, 1]:
            ax = cx + side * 12
            ay_off = arm_swing if side == -1 else -arm_swing
            draw.rectangle([ax - 3, body_cy - 5 + ay_off, ax + 3, body_cy + 8 + ay_off],
                           fill=PLT_JACKET, outline=OUTLINE)
            # Sleeve cuff
            draw.rectangle([ax - 3, body_cy + 6 + ay_off, ax + 3, body_cy + 9 + ay_off],
                           fill=PLT_SUIT, outline=OUTLINE)
            # Glove
            draw.rectangle([ax - 2, body_cy + 8 + ay_off, ax + 2, body_cy + 11 + ay_off],
                           fill=PLT_SUIT_DARK, outline=None)
    elif direction == LEFT:
        draw.rectangle([cx + 5, body_cy - 5, cx + 11, body_cy + 8],
                       fill=PLT_JACKET, outline=OUTLINE)
        draw.rectangle([cx + 5, body_cy + 6, cx + 11, body_cy + 9],
                       fill=PLT_SUIT, outline=OUTLINE)
    else:  # RIGHT
        draw.rectangle([cx - 11, body_cy - 5, cx - 5, body_cy + 8],
                       fill=PLT_JACKET, outline=OUTLINE)
        draw.rectangle([cx - 11, body_cy + 6, cx - 5, body_cy + 9],
                       fill=PLT_SUIT, outline=OUTLINE)

    # --- Head (aviator helmet with visor/goggles) ---
    if direction == DOWN:
        ellipse(draw, cx, head_cy, 10, 10, PLT_HELMET, outline=OUTLINE)
        # Helmet top
        draw.rectangle([cx - 6, head_cy - 10, cx + 6, head_cy - 7],
                       fill=PLT_HELMET_LIGHT, outline=None)
        # Goggles
        draw.rectangle([cx - 7, head_cy - 3, cx - 2, head_cy + 1],
                       fill=PLT_GOGGLES, outline=OUTLINE)
        draw.rectangle([cx + 2, head_cy - 3, cx + 7, head_cy + 1],
                       fill=PLT_GOGGLES, outline=OUTLINE)
        # Goggle lenses
        draw.rectangle([cx - 6, head_cy - 2, cx - 3, head_cy],
                       fill=PLT_GOGGLE_LENS, outline=None)
        draw.rectangle([cx + 3, head_cy - 2, cx + 6, head_cy],
                       fill=PLT_GOGGLE_LENS, outline=None)
        # Oxygen mask area
        draw.rectangle([cx - 3, head_cy + 3, cx + 3, head_cy + 7],
                       fill=PLT_MASK, outline=OUTLINE)
        # Mask detail
        draw.point((cx, head_cy + 5), fill=_brighten(PLT_MASK, 1.3))
    elif direction == UP:
        ellipse(draw, cx, head_cy, 10, 10, PLT_HELMET, outline=OUTLINE)
        draw.rectangle([cx - 6, head_cy - 10, cx + 6, head_cy - 7],
                       fill=PLT_HELMET_LIGHT, outline=None)
        ellipse(draw, cx, head_cy, 7, 7, PLT_HELMET, outline=None)
        # Goggle strap visible from behind
        draw.rectangle([cx - 8, head_cy - 1, cx + 8, head_cy + 1],
                       fill=PLT_GOGGLES, outline=None)
    elif direction == LEFT:
        ellipse(draw, cx - 2, head_cy, 10, 10, PLT_HELMET, outline=OUTLINE)
        draw.rectangle([cx - 5, head_cy - 10, cx + 3, head_cy - 7],
                       fill=PLT_HELMET_LIGHT, outline=None)
        # Side goggle
        draw.rectangle([cx - 10, head_cy - 3, cx - 5, head_cy + 1],
                       fill=PLT_GOGGLES, outline=OUTLINE)
        draw.rectangle([cx - 9, head_cy - 2, cx - 6, head_cy],
                       fill=PLT_GOGGLE_LENS, outline=None)
        # Side mask
        draw.rectangle([cx - 6, head_cy + 3, cx - 1, head_cy + 7],
                       fill=PLT_MASK, outline=OUTLINE)
    else:  # RIGHT
        ellipse(draw, cx + 2, head_cy, 10, 10, PLT_HELMET, outline=OUTLINE)
        draw.rectangle([cx - 3, head_cy - 10, cx + 5, head_cy - 7],
                       fill=PLT_HELMET_LIGHT, outline=None)
        draw.rectangle([cx + 5, head_cy - 3, cx + 10, head_cy + 1],
                       fill=PLT_GOGGLES, outline=OUTLINE)
        draw.rectangle([cx + 6, head_cy - 2, cx + 9, head_cy],
                       fill=PLT_GOGGLE_LENS, outline=None)
        draw.rectangle([cx + 1, head_cy + 3, cx + 6, head_cy + 7],
                       fill=PLT_MASK, outline=OUTLINE)


# ===================================================================
# GLITCHER (ID 71) -- cyan/magenta glitching body, offset color
#                     channels, digital artifacts, scan lines,
#                     distorted silhouette edges
# ===================================================================

# Glitcher palette
GLI_BODY_CYAN = (0, 220, 220)
GLI_BODY_MAGENTA = (220, 0, 220)
GLI_BODY_MIX = (120, 100, 220)
GLI_BODY_DARK = (40, 30, 80)
GLI_BODY_LIGHT = (160, 140, 255)
GLI_EYE = (255, 255, 255)
GLI_EYE_RED = (255, 0, 80)
GLI_PIXEL = (0, 255, 200, 180)
GLI_PIXEL_MAG = (255, 0, 200, 180)
GLI_PIXEL_DIM = (80, 60, 160, 100)
GLI_SCAN = (200, 180, 255, 60)
GLI_ARTIFACT = (0, 200, 180, 140)
GLI_ARTIFACT_MAG = (200, 0, 180, 140)


def draw_glitcher(draw, ox, oy, direction, frame):
    bob = [0, -2, 0, -1][frame]
    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 20
    head_cy = body_cy - 18

    # Per-frame glitch offset for color channel separation
    glitch_offsets = [
        (2, 0),    # frame 0: slight right shift
        (-1, 1),   # frame 1: left and down
        (0, -1),   # frame 2: up shift
        (-2, 0),   # frame 3: left shift
    ]
    gx_off, gy_off = glitch_offsets[frame]

    # --- Scan lines (behind everything, across full body area) ---
    scan_y_start = head_cy - 12
    scan_y_end = base_y + 2
    for sy in range(scan_y_start, scan_y_end, 4):
        # Offset scan lines per frame for flicker
        sx_off = [0, 2, -1, 1][frame]
        draw.rectangle([cx - 14 + sx_off, sy, cx + 14 + sx_off, sy + 1],
                       fill=GLI_SCAN, outline=None)

    # --- Legs (with color channel offset) ---
    if direction in (DOWN, UP):
        leg_spread = [-3, 0, 3, 0][frame]
        for side in [-1, 1]:
            ls = leg_spread if side == -1 else -leg_spread
            lx = cx + side * 6 + ls
            # Cyan channel (shifted)
            draw.rectangle([lx - 3 + gx_off, body_cy + 10 + gy_off,
                            lx + 3 + gx_off, base_y + gy_off],
                           fill=GLI_BODY_CYAN, outline=None)
            # Magenta channel (opposite shift)
            draw.rectangle([lx - 3 - gx_off, body_cy + 10 - gy_off,
                            lx + 3 - gx_off, base_y - gy_off],
                           fill=GLI_BODY_MAGENTA, outline=None)
            # Main body on top
            draw.rectangle([lx - 3, body_cy + 10, lx + 3, base_y],
                           fill=GLI_BODY_MIX, outline=OUTLINE)
            # Boot
            draw.rectangle([lx - 4, base_y - 4, lx + 4, base_y],
                           fill=GLI_BODY_DARK, outline=OUTLINE)
    else:
        off = -2 if direction == LEFT else 2
        # Cyan channel
        draw.rectangle([cx + off - 3 + gx_off, body_cy + 10 + gy_off,
                        cx + off + 3 + gx_off, base_y + gy_off],
                       fill=GLI_BODY_CYAN, outline=None)
        # Magenta channel
        draw.rectangle([cx + off - 3 - gx_off, body_cy + 10 - gy_off,
                        cx + off + 3 - gx_off, base_y - gy_off],
                       fill=GLI_BODY_MAGENTA, outline=None)
        # Main
        draw.rectangle([cx + off - 3, body_cy + 10, cx + off + 3, base_y],
                       fill=GLI_BODY_MIX, outline=OUTLINE)
        draw.rectangle([cx + off - 6, body_cy + 13, cx + off, base_y],
                       fill=GLI_BODY_MIX, outline=OUTLINE)
        draw.rectangle([cx + off - 4, base_y - 4, cx + off + 4, base_y],
                       fill=GLI_BODY_DARK, outline=OUTLINE)

    # --- Body (glitched torso with channel separation) ---
    # Cyan offset layer
    pill(draw, cx + gx_off, body_cy + gy_off, 11, 14,
         GLI_BODY_CYAN, outline=None)
    # Magenta offset layer
    pill(draw, cx - gx_off, body_cy - gy_off, 11, 14,
         GLI_BODY_MAGENTA, outline=None)
    # Main mixed layer
    pill(draw, cx, body_cy, 11, 14, GLI_BODY_MIX, outline=OUTLINE)
    pill(draw, cx, body_cy - 1, 6, 8, GLI_BODY_LIGHT, outline=None)

    # --- Arms ---
    if direction in (DOWN, UP):
        for side in [-1, 1]:
            ax = cx + side * 12
            # Channel offsets on arms
            draw.rectangle([ax - 3 + gx_off, body_cy - 5 + gy_off,
                            ax + 3 + gx_off, body_cy + 7 + gy_off],
                           fill=GLI_BODY_CYAN, outline=None)
            draw.rectangle([ax - 3 - gx_off, body_cy - 5 - gy_off,
                            ax + 3 - gx_off, body_cy + 7 - gy_off],
                           fill=GLI_BODY_MAGENTA, outline=None)
            draw.rectangle([ax - 3, body_cy - 5, ax + 3, body_cy + 7],
                           fill=GLI_BODY_MIX, outline=OUTLINE)
    elif direction == LEFT:
        draw.rectangle([cx + 5, body_cy - 5, cx + 11, body_cy + 7],
                       fill=GLI_BODY_MIX, outline=OUTLINE)
    else:  # RIGHT
        draw.rectangle([cx - 11, body_cy - 5, cx - 5, body_cy + 7],
                       fill=GLI_BODY_MIX, outline=OUTLINE)

    # --- Head (glitched) ---
    if direction == DOWN:
        # Channel separation on head
        ellipse(draw, cx + gx_off, head_cy + gy_off, 10, 9,
                GLI_BODY_CYAN, outline=None)
        ellipse(draw, cx - gx_off, head_cy - gy_off, 10, 9,
                GLI_BODY_MAGENTA, outline=None)
        ellipse(draw, cx, head_cy, 10, 9, GLI_BODY_MIX, outline=OUTLINE)
        # Glitched eyes (one white, one red)
        draw.rectangle([cx - 5, head_cy, cx - 2, head_cy + 3], fill=GLI_EYE)
        draw.rectangle([cx + 2, head_cy, cx + 5, head_cy + 3], fill=GLI_EYE_RED)
        draw.point((cx - 4, head_cy + 1), fill=GLI_EYE_RED)
        draw.point((cx + 3, head_cy + 1), fill=GLI_EYE)
    elif direction == UP:
        ellipse(draw, cx + gx_off, head_cy + gy_off, 10, 9,
                GLI_BODY_CYAN, outline=None)
        ellipse(draw, cx - gx_off, head_cy - gy_off, 10, 9,
                GLI_BODY_MAGENTA, outline=None)
        ellipse(draw, cx, head_cy, 10, 9, GLI_BODY_MIX, outline=OUTLINE)
        ellipse(draw, cx, head_cy, 6, 6, GLI_BODY_DARK, outline=None)
    elif direction == LEFT:
        ellipse(draw, cx - 2 + gx_off, head_cy + gy_off, 10, 9,
                GLI_BODY_CYAN, outline=None)
        ellipse(draw, cx - 2 - gx_off, head_cy - gy_off, 10, 9,
                GLI_BODY_MAGENTA, outline=None)
        ellipse(draw, cx - 2, head_cy, 10, 9, GLI_BODY_MIX, outline=OUTLINE)
        draw.rectangle([cx - 8, head_cy, cx - 5, head_cy + 3], fill=GLI_EYE)
        draw.point((cx - 7, head_cy + 1), fill=GLI_EYE_RED)
    else:  # RIGHT
        ellipse(draw, cx + 2 + gx_off, head_cy + gy_off, 10, 9,
                GLI_BODY_CYAN, outline=None)
        ellipse(draw, cx + 2 - gx_off, head_cy - gy_off, 10, 9,
                GLI_BODY_MAGENTA, outline=None)
        ellipse(draw, cx + 2, head_cy, 10, 9, GLI_BODY_MIX, outline=OUTLINE)
        draw.rectangle([cx + 5, head_cy, cx + 8, head_cy + 3], fill=GLI_EYE_RED)
        draw.point((cx + 6, head_cy + 1), fill=GLI_EYE)

    # --- Digital artifact pixels (scattered around body) ---
    artifact_positions = [
        [(-16, -12), (14, -8), (-12, 6), (18, 10), (4, -16), (-8, 14)],
        [(-14, -10), (16, -12), (-18, 8), (12, 6), (-4, -14), (10, 14)],
        [(-18, -8), (12, -14), (-14, 10), (16, 4), (0, -18), (-6, 12)],
        [(-12, -14), (18, -6), (-16, 4), (14, 12), (6, -12), (-10, 10)],
    ]
    for i, (ax, ay) in enumerate(artifact_positions[frame]):
        col = GLI_PIXEL if i % 2 == 0 else GLI_PIXEL_MAG
        draw.point((cx + ax, body_cy + ay), fill=col)
        # Small artifact block (2x2) for some
        if i % 3 == 0:
            draw.rectangle([cx + ax, body_cy + ay, cx + ax + 2, body_cy + ay + 2],
                           fill=GLI_PIXEL_DIM, outline=None)

    # --- Distorted edge pixels along silhouette ---
    edge_glitch = [
        [(-12, -6), (12, -4), (-10, 8), (10, 6)],
        [(-10, -8), (10, -6), (-12, 6), (12, 8)],
        [(-12, -4), (12, -8), (-10, 6), (10, 4)],
        [(-10, -6), (12, -4), (-12, 4), (10, 8)],
    ]
    for ex, ey in edge_glitch[frame]:
        c = GLI_ARTIFACT if (ex + ey) % 2 == 0 else GLI_ARTIFACT_MAG
        draw.point((cx + ex, body_cy + ey), fill=c)


# ─── REGISTRY ─────────────────────────────────────────────────────
SCIFI_DRAW_FUNCTIONS = {
    'cyborg': draw_cyborg,
    'hacker': draw_hacker,
    'mechpilot': draw_mechpilot,
    'android': draw_android,
    'chronomancer': draw_chronomancer,
    'graviton': draw_graviton,
    'tesla': draw_tesla,
    'nanoswarm': draw_nanoswarm,
    'voidwalker': draw_voidwalker,
    'photon': draw_photon,
    'railgunner': draw_railgunner,
    'bombardier': draw_bombardier,
    'sentinel': draw_sentinel,
    'pilot': draw_pilot,
    'glitcher': draw_glitcher,
}


def main():
    for name, draw_func in SCIFI_DRAW_FUNCTIONS.items():
        generate_character(name, draw_func=draw_func)
    print(f"\nGenerated {len(SCIFI_DRAW_FUNCTIONS)} sci-fi character sprites.")


if __name__ == "__main__":
    main()
