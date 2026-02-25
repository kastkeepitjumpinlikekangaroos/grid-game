#!/usr/bin/env python3
"""Elemental character sprite generators (IDs 12-26).

15 characters with elemental effects replacing the generic humanoid template.
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

# Pyromancer palette
PYRO_ROBE = (160, 40, 30)
PYRO_ROBE_DARK = (110, 25, 20)
PYRO_ROBE_LIGHT = (200, 60, 40)
PYRO_SKIN = (220, 185, 150)
PYRO_SKIN_DARK = (190, 155, 120)
PYRO_FLAME_OUTER = (255, 120, 20)
PYRO_FLAME_MID = (255, 180, 40)
PYRO_FLAME_INNER = (255, 240, 120)
PYRO_EMBER = (255, 100, 30)
PYRO_CROWN = (255, 200, 50)
PYRO_EYE = (255, 160, 20)

# Cryomancer palette
CRYO_ROBE = (80, 140, 200)
CRYO_ROBE_DARK = (50, 100, 160)
CRYO_ROBE_LIGHT = (120, 180, 230)
CRYO_SKIN = (210, 220, 235)
CRYO_SKIN_DARK = (180, 195, 210)
CRYO_ICE = (160, 220, 255)
CRYO_ICE_DARK = (100, 170, 220)
CRYO_ICE_BRIGHT = (200, 240, 255)
CRYO_SNOW = (230, 240, 255)
CRYO_CRYSTAL = (140, 200, 250)
CRYO_EYE = (100, 200, 255)

# Stormcaller palette
STORM_ROBE = (80, 60, 120)
STORM_ROBE_DARK = (55, 40, 85)
STORM_ROBE_LIGHT = (110, 85, 155)
STORM_SKIN = (200, 195, 210)
STORM_SKIN_DARK = (170, 165, 180)
STORM_LIGHTNING = (200, 220, 255)
STORM_LIGHTNING_BRIGHT = (240, 245, 255)
STORM_CLOUD = (90, 95, 110)
STORM_CLOUD_DARK = (60, 65, 80)
STORM_CLOUD_LIGHT = (130, 135, 150)
STORM_EYE = (180, 200, 255)

# Earthshaker palette
EARTH_ARMOR = (130, 100, 70)
EARTH_ARMOR_DARK = (95, 70, 45)
EARTH_ARMOR_LIGHT = (165, 130, 95)
EARTH_SKIN = (180, 155, 120)
EARTH_SKIN_DARK = (150, 125, 90)
EARTH_STONE = (140, 135, 125)
EARTH_STONE_DARK = (100, 95, 85)
EARTH_STONE_LIGHT = (180, 175, 165)
EARTH_CRYSTAL = (120, 200, 100)
EARTH_CRACK = (80, 70, 55)
EARTH_MOSS = (80, 120, 60)

# Windwalker palette
WIND_ROBE = (210, 230, 245)
WIND_ROBE_DARK = (170, 195, 215)
WIND_ROBE_LIGHT = (235, 245, 255)
WIND_SKIN = (220, 210, 200)
WIND_SKIN_DARK = (190, 180, 170)
WIND_SWIRL = (180, 210, 240, 180)
WIND_SWIRL_BRIGHT = (220, 235, 255)
WIND_CAPE = (190, 215, 235)
WIND_CAPE_DARK = (150, 180, 205)
WIND_EYE = (150, 200, 240)

# MagmaKnight palette
MAGMA_ARMOR = (50, 45, 50)
MAGMA_ARMOR_DARK = (30, 28, 32)
MAGMA_ARMOR_LIGHT = (75, 70, 78)
MAGMA_LAVA = (255, 130, 20)
MAGMA_LAVA_BRIGHT = (255, 200, 60)
MAGMA_LAVA_DIM = (200, 80, 10)
MAGMA_VISOR = (255, 160, 40)
MAGMA_VISOR_BRIGHT = (255, 220, 80)
MAGMA_CRACK = (255, 100, 10)
MAGMA_EYE = (255, 180, 40)

# Frostbite palette
FROST_BODY = (100, 160, 200)
FROST_BODY_DARK = (70, 120, 160)
FROST_BODY_LIGHT = (140, 195, 230)
FROST_SPIKE = (160, 220, 250)
FROST_SPIKE_DARK = (110, 170, 210)
FROST_SPIKE_BRIGHT = (200, 240, 255)
FROST_BREATH = (180, 220, 255, 160)
FROST_EYE = (200, 240, 255)
FROST_EYE_DIM = (140, 190, 230)
FROST_CLAW = (80, 130, 170)

# Sandstorm palette
SAND_ROBE = (190, 165, 120)
SAND_ROBE_DARK = (155, 130, 90)
SAND_ROBE_LIGHT = (220, 195, 150)
SAND_SKIN = (180, 150, 100)
SAND_SKIN_DARK = (150, 120, 75)
SAND_TURBAN = (210, 185, 140)
SAND_TURBAN_DARK = (175, 150, 105)
SAND_PARTICLE = (220, 195, 140, 180)
SAND_PARTICLE_DIM = (200, 175, 120, 120)
SAND_EYE = (160, 130, 60)


# ===================================================================
# PYROMANCER (ID 12) -- Red/orange robes, flame particles from hands
#                       and shoulders, fire crown effect
# ===================================================================

def draw_pyromancer(draw, ox, oy, direction, frame):
    bob = [0, -2, 0, -1][frame]
    robe_sway = [-2, 0, 2, 0][frame]
    flame_flicker = [0, 2, -1, 1][frame]

    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 20
    head_cy = body_cy - 18

    # --- Robe body (wide, flowing) ---
    if direction == DOWN:
        draw.polygon([
            (cx - 14, body_cy - 10),
            (cx + 14, body_cy - 10),
            (cx + 18 + robe_sway, base_y + 2),
            (cx - 18 + robe_sway, base_y + 2),
        ], fill=PYRO_ROBE, outline=OUTLINE)
        # Fold lines
        draw.line([(cx - 4, body_cy - 6), (cx - 6 + robe_sway, base_y)],
                  fill=PYRO_ROBE_DARK, width=1)
        draw.line([(cx + 4, body_cy - 6), (cx + 6 + robe_sway, base_y)],
                  fill=PYRO_ROBE_DARK, width=1)
        draw.line([(cx - 10, body_cy - 4), (cx - 14 + robe_sway, base_y)],
                  fill=PYRO_ROBE_LIGHT, width=1)
        # Belt
        draw.rectangle([cx - 14, body_cy + 6, cx + 14, body_cy + 12],
                       fill=PYRO_ROBE_DARK, outline=OUTLINE)
        # Belt buckle -- flame motif
        draw.rectangle([cx - 3, body_cy + 7, cx + 3, body_cy + 11],
                       fill=PYRO_FLAME_OUTER, outline=OUTLINE)
        draw.point((cx, body_cy + 9), fill=PYRO_FLAME_INNER)
        # Fire hem
        for hx in range(cx - 16 + robe_sway, cx + 16 + robe_sway, 5):
            draw.polygon([(hx, base_y), (hx + 2, base_y - 3 + flame_flicker),
                          (hx + 5, base_y)],
                         fill=PYRO_FLAME_OUTER, outline=None)
    elif direction == UP:
        draw.polygon([
            (cx - 14, body_cy - 10),
            (cx + 14, body_cy - 10),
            (cx + 18 + robe_sway, base_y + 2),
            (cx - 18 + robe_sway, base_y + 2),
        ], fill=PYRO_ROBE, outline=OUTLINE)
        draw.line([(cx, body_cy - 6), (cx + robe_sway, base_y)],
                  fill=PYRO_ROBE_DARK, width=1)
        draw.line([(cx - 8, body_cy - 4), (cx - 10 + robe_sway, base_y)],
                  fill=PYRO_ROBE_DARK, width=1)
        draw.line([(cx + 8, body_cy - 4), (cx + 10 + robe_sway, base_y)],
                  fill=PYRO_ROBE_DARK, width=1)
        draw.rectangle([cx - 14, body_cy + 6, cx + 14, body_cy + 12],
                       fill=PYRO_ROBE_DARK, outline=OUTLINE)
    elif direction == LEFT:
        draw.polygon([
            (cx - 10, body_cy - 10),
            (cx + 8, body_cy - 10),
            (cx + 12 + robe_sway, base_y + 2),
            (cx - 14 + robe_sway, base_y + 2),
        ], fill=PYRO_ROBE, outline=OUTLINE)
        draw.line([(cx - 2, body_cy - 6), (cx - 4 + robe_sway, base_y)],
                  fill=PYRO_ROBE_DARK, width=1)
        draw.line([(cx - 8, body_cy - 4), (cx - 10 + robe_sway, base_y)],
                  fill=PYRO_ROBE_LIGHT, width=1)
        draw.rectangle([cx - 10, body_cy + 6, cx + 8, body_cy + 12],
                       fill=PYRO_ROBE_DARK, outline=OUTLINE)
    else:  # RIGHT
        draw.polygon([
            (cx - 8, body_cy - 10),
            (cx + 10, body_cy - 10),
            (cx + 14 + robe_sway, base_y + 2),
            (cx - 12 + robe_sway, base_y + 2),
        ], fill=PYRO_ROBE, outline=OUTLINE)
        draw.line([(cx + 2, body_cy - 6), (cx + 4 + robe_sway, base_y)],
                  fill=PYRO_ROBE_DARK, width=1)
        draw.line([(cx + 8, body_cy - 4), (cx + 10 + robe_sway, base_y)],
                  fill=PYRO_ROBE_LIGHT, width=1)
        draw.rectangle([cx - 8, body_cy + 6, cx + 10, body_cy + 12],
                       fill=PYRO_ROBE_DARK, outline=OUTLINE)

    # --- Arms with flame hands ---
    if direction == DOWN:
        for side in [-1, 1]:
            ax = cx + side * 16
            draw.rectangle([ax - 3, body_cy - 4, ax + 3, body_cy + 4],
                           fill=PYRO_ROBE, outline=OUTLINE)
            # Flame hand
            ellipse(draw, ax, body_cy + 6, 4, 4, PYRO_FLAME_OUTER)
            ellipse(draw, ax, body_cy + 5, 2, 3, PYRO_FLAME_MID, outline=None)
            draw.point((ax, body_cy + 4), fill=PYRO_FLAME_INNER)
            # Shoulder flame
            draw.polygon([(ax - 3, body_cy - 6), (ax, body_cy - 12 + flame_flicker),
                          (ax + 3, body_cy - 6)],
                         fill=PYRO_FLAME_OUTER, outline=None)
            draw.polygon([(ax - 1, body_cy - 6), (ax, body_cy - 10 + flame_flicker),
                          (ax + 1, body_cy - 6)],
                         fill=PYRO_FLAME_MID, outline=None)
    elif direction == UP:
        for side in [-1, 1]:
            ax = cx + side * 16
            draw.rectangle([ax - 3, body_cy - 4, ax + 3, body_cy + 4],
                           fill=PYRO_ROBE, outline=OUTLINE)
            # Shoulder flame
            draw.polygon([(ax - 3, body_cy - 6), (ax, body_cy - 12 + flame_flicker),
                          (ax + 3, body_cy - 6)],
                         fill=PYRO_FLAME_OUTER, outline=None)
    elif direction == LEFT:
        ax = cx - 12
        draw.rectangle([ax - 3, body_cy - 3, ax + 3, body_cy + 5],
                       fill=PYRO_ROBE, outline=OUTLINE)
        ellipse(draw, ax, body_cy + 7, 4, 3, PYRO_FLAME_OUTER)
        ellipse(draw, ax, body_cy + 6, 2, 2, PYRO_FLAME_MID, outline=None)
        draw.polygon([(ax - 3, body_cy - 5), (ax, body_cy - 11 + flame_flicker),
                      (ax + 3, body_cy - 5)],
                     fill=PYRO_FLAME_OUTER, outline=None)
    else:  # RIGHT
        ax = cx + 12
        draw.rectangle([ax - 3, body_cy - 3, ax + 3, body_cy + 5],
                       fill=PYRO_ROBE, outline=OUTLINE)
        ellipse(draw, ax, body_cy + 7, 4, 3, PYRO_FLAME_OUTER)
        ellipse(draw, ax, body_cy + 6, 2, 2, PYRO_FLAME_MID, outline=None)
        draw.polygon([(ax - 3, body_cy - 5), (ax, body_cy - 11 + flame_flicker),
                      (ax + 3, body_cy - 5)],
                     fill=PYRO_FLAME_OUTER, outline=None)

    # --- Head ---
    if direction == DOWN:
        ellipse(draw, cx, head_cy, 12, 10, PYRO_ROBE)
        ellipse(draw, cx, head_cy + 2, 8, 6, PYRO_SKIN)
        ellipse(draw, cx + 2, head_cy + 4, 5, 4, PYRO_SKIN_DARK, outline=None)
        # Eyes (glowing orange)
        draw.rectangle([cx - 5, head_cy + 1, cx - 2, head_cy + 4], fill=PYRO_EYE)
        draw.rectangle([cx + 2, head_cy + 1, cx + 5, head_cy + 4], fill=PYRO_EYE)
        draw.point((cx - 4, head_cy + 2), fill=PYRO_FLAME_INNER)
        draw.point((cx + 3, head_cy + 2), fill=PYRO_FLAME_INNER)
    elif direction == UP:
        ellipse(draw, cx, head_cy, 12, 10, PYRO_ROBE)
        ellipse(draw, cx, head_cy, 9, 7, PYRO_ROBE_DARK, outline=None)
    elif direction == LEFT:
        ellipse(draw, cx - 2, head_cy, 11, 10, PYRO_ROBE)
        ellipse(draw, cx - 4, head_cy + 2, 6, 5, PYRO_SKIN)
        ellipse(draw, cx - 3, head_cy + 4, 4, 3, PYRO_SKIN_DARK, outline=None)
        draw.rectangle([cx - 8, head_cy + 1, cx - 5, head_cy + 4], fill=PYRO_EYE)
        draw.point((cx - 7, head_cy + 2), fill=PYRO_FLAME_INNER)
    else:  # RIGHT
        ellipse(draw, cx + 2, head_cy, 11, 10, PYRO_ROBE)
        ellipse(draw, cx + 4, head_cy + 2, 6, 5, PYRO_SKIN)
        ellipse(draw, cx + 3, head_cy + 4, 4, 3, PYRO_SKIN_DARK, outline=None)
        draw.rectangle([cx + 5, head_cy + 1, cx + 8, head_cy + 4], fill=PYRO_EYE)
        draw.point((cx + 6, head_cy + 2), fill=PYRO_FLAME_INNER)

    # --- Fire crown ---
    crown_offsets = [(-8, -4), (-4, -6), (0, -7), (4, -6), (8, -4)]
    for i, (dx, dy) in enumerate(crown_offsets):
        flame_h = 6 + (i % 2) * 3 + flame_flicker
        fx = cx + dx if direction in (DOWN, UP) else cx + dx + (-2 if direction == LEFT else 2)
        fy = head_cy + dy
        draw.polygon([(fx - 2, fy), (fx, fy - flame_h), (fx + 2, fy)],
                     fill=PYRO_FLAME_OUTER, outline=None)
        draw.polygon([(fx - 1, fy), (fx, fy - flame_h + 2), (fx + 1, fy)],
                     fill=PYRO_FLAME_MID, outline=None)

    # --- Orbiting ember particles ---
    ember_radius = 20
    for i in range(4):
        angle = frame * (math.pi / 2) + i * (math.pi / 2)
        ex = int(cx + math.cos(angle) * ember_radius)
        ey = int(body_cy + math.sin(angle) * (ember_radius * 0.5))
        draw.point((ex, ey), fill=PYRO_EMBER)
        draw.point((ex + 1, ey), fill=PYRO_FLAME_MID)


# ===================================================================
# CRYOMANCER (ID 13) -- Ice blue robes, frost crystal shoulders,
#                        icicle effects, snow particles
# ===================================================================

def draw_cryomancer(draw, ox, oy, direction, frame):
    bob = [0, -2, 0, -1][frame]
    robe_sway = [-2, 0, 2, 0][frame]
    crystal_pulse = [0, 1, 0, -1][frame]

    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 20
    head_cy = body_cy - 18

    # --- Robe body ---
    if direction == DOWN:
        draw.polygon([
            (cx - 14, body_cy - 10),
            (cx + 14, body_cy - 10),
            (cx + 18 + robe_sway, base_y + 2),
            (cx - 18 + robe_sway, base_y + 2),
        ], fill=CRYO_ROBE, outline=OUTLINE)
        draw.line([(cx - 4, body_cy - 6), (cx - 6 + robe_sway, base_y)],
                  fill=CRYO_ROBE_DARK, width=1)
        draw.line([(cx + 4, body_cy - 6), (cx + 6 + robe_sway, base_y)],
                  fill=CRYO_ROBE_DARK, width=1)
        draw.line([(cx - 10, body_cy - 4), (cx - 14 + robe_sway, base_y)],
                  fill=CRYO_ROBE_LIGHT, width=1)
        # Ice trim at hem
        for hx in range(cx - 16 + robe_sway, cx + 16 + robe_sway, 4):
            draw.polygon([(hx, base_y), (hx + 2, base_y + 4), (hx + 4, base_y)],
                         fill=CRYO_ICE, outline=None)
        # Belt
        draw.rectangle([cx - 14, body_cy + 6, cx + 14, body_cy + 12],
                       fill=CRYO_ICE_DARK, outline=OUTLINE)
        draw.rectangle([cx - 3, body_cy + 7, cx + 3, body_cy + 11],
                       fill=CRYO_CRYSTAL, outline=OUTLINE)
    elif direction == UP:
        draw.polygon([
            (cx - 14, body_cy - 10),
            (cx + 14, body_cy - 10),
            (cx + 18 + robe_sway, base_y + 2),
            (cx - 18 + robe_sway, base_y + 2),
        ], fill=CRYO_ROBE, outline=OUTLINE)
        draw.line([(cx, body_cy - 6), (cx + robe_sway, base_y)],
                  fill=CRYO_ROBE_DARK, width=1)
        draw.line([(cx - 8, body_cy - 4), (cx - 10 + robe_sway, base_y)],
                  fill=CRYO_ROBE_DARK, width=1)
        draw.rectangle([cx - 14, body_cy + 6, cx + 14, body_cy + 12],
                       fill=CRYO_ICE_DARK, outline=OUTLINE)
    elif direction == LEFT:
        draw.polygon([
            (cx - 10, body_cy - 10),
            (cx + 8, body_cy - 10),
            (cx + 12 + robe_sway, base_y + 2),
            (cx - 14 + robe_sway, base_y + 2),
        ], fill=CRYO_ROBE, outline=OUTLINE)
        draw.line([(cx - 2, body_cy - 6), (cx - 4 + robe_sway, base_y)],
                  fill=CRYO_ROBE_DARK, width=1)
        draw.rectangle([cx - 10, body_cy + 6, cx + 8, body_cy + 12],
                       fill=CRYO_ICE_DARK, outline=OUTLINE)
    else:  # RIGHT
        draw.polygon([
            (cx - 8, body_cy - 10),
            (cx + 10, body_cy - 10),
            (cx + 14 + robe_sway, base_y + 2),
            (cx - 12 + robe_sway, base_y + 2),
        ], fill=CRYO_ROBE, outline=OUTLINE)
        draw.line([(cx + 2, body_cy - 6), (cx + 4 + robe_sway, base_y)],
                  fill=CRYO_ROBE_DARK, width=1)
        draw.rectangle([cx - 8, body_cy + 6, cx + 10, body_cy + 12],
                       fill=CRYO_ICE_DARK, outline=OUTLINE)

    # --- Arms with frost crystal shoulders ---
    if direction == DOWN:
        for side in [-1, 1]:
            ax = cx + side * 16
            draw.rectangle([ax - 3, body_cy - 4, ax + 3, body_cy + 4],
                           fill=CRYO_ROBE, outline=OUTLINE)
            # Hands with frost glow
            draw.rectangle([ax - 3, body_cy + 2, ax + 3, body_cy + 6],
                           fill=CRYO_SKIN, outline=OUTLINE)
            # Ice crystal shoulder
            draw.polygon([(ax - 4, body_cy - 6), (ax, body_cy - 14 + crystal_pulse),
                          (ax + 4, body_cy - 6)],
                         fill=CRYO_ICE, outline=OUTLINE)
            draw.polygon([(ax - 2, body_cy - 6), (ax, body_cy - 12 + crystal_pulse),
                          (ax + 2, body_cy - 6)],
                         fill=CRYO_ICE_BRIGHT, outline=None)
    elif direction == UP:
        for side in [-1, 1]:
            ax = cx + side * 16
            draw.rectangle([ax - 3, body_cy - 4, ax + 3, body_cy + 4],
                           fill=CRYO_ROBE, outline=OUTLINE)
            draw.polygon([(ax - 4, body_cy - 6), (ax, body_cy - 14 + crystal_pulse),
                          (ax + 4, body_cy - 6)],
                         fill=CRYO_ICE, outline=OUTLINE)
    elif direction == LEFT:
        ax = cx - 12
        draw.rectangle([ax - 3, body_cy - 3, ax + 3, body_cy + 5],
                       fill=CRYO_ROBE, outline=OUTLINE)
        draw.rectangle([ax - 3, body_cy + 2, ax + 3, body_cy + 6],
                       fill=CRYO_SKIN, outline=OUTLINE)
        draw.polygon([(ax - 4, body_cy - 5), (ax, body_cy - 13 + crystal_pulse),
                      (ax + 4, body_cy - 5)],
                     fill=CRYO_ICE, outline=OUTLINE)
        draw.polygon([(ax - 2, body_cy - 5), (ax, body_cy - 11 + crystal_pulse),
                      (ax + 2, body_cy - 5)],
                     fill=CRYO_ICE_BRIGHT, outline=None)
    else:  # RIGHT
        ax = cx + 12
        draw.rectangle([ax - 3, body_cy - 3, ax + 3, body_cy + 5],
                       fill=CRYO_ROBE, outline=OUTLINE)
        draw.rectangle([ax - 3, body_cy + 2, ax + 3, body_cy + 6],
                       fill=CRYO_SKIN, outline=OUTLINE)
        draw.polygon([(ax - 4, body_cy - 5), (ax, body_cy - 13 + crystal_pulse),
                      (ax + 4, body_cy - 5)],
                     fill=CRYO_ICE, outline=OUTLINE)
        draw.polygon([(ax - 2, body_cy - 5), (ax, body_cy - 11 + crystal_pulse),
                      (ax + 2, body_cy - 5)],
                     fill=CRYO_ICE_BRIGHT, outline=None)

    # --- Head ---
    if direction == DOWN:
        ellipse(draw, cx, head_cy, 12, 10, CRYO_ROBE)
        ellipse(draw, cx, head_cy - 2, 10, 8, CRYO_ROBE_LIGHT, outline=None)
        ellipse(draw, cx, head_cy + 2, 8, 6, CRYO_SKIN)
        ellipse(draw, cx + 2, head_cy + 4, 5, 4, CRYO_SKIN_DARK, outline=None)
        draw.rectangle([cx - 5, head_cy + 1, cx - 2, head_cy + 4], fill=CRYO_EYE)
        draw.rectangle([cx + 2, head_cy + 1, cx + 5, head_cy + 4], fill=CRYO_EYE)
        draw.point((cx - 4, head_cy + 2), fill=CRYO_ICE_BRIGHT)
        draw.point((cx + 3, head_cy + 2), fill=CRYO_ICE_BRIGHT)
    elif direction == UP:
        ellipse(draw, cx, head_cy, 12, 10, CRYO_ROBE)
        ellipse(draw, cx, head_cy, 9, 7, CRYO_ROBE_DARK, outline=None)
    elif direction == LEFT:
        ellipse(draw, cx - 2, head_cy, 11, 10, CRYO_ROBE)
        ellipse(draw, cx - 4, head_cy + 2, 6, 5, CRYO_SKIN)
        draw.rectangle([cx - 8, head_cy + 1, cx - 5, head_cy + 4], fill=CRYO_EYE)
    else:  # RIGHT
        ellipse(draw, cx + 2, head_cy, 11, 10, CRYO_ROBE)
        ellipse(draw, cx + 4, head_cy + 2, 6, 5, CRYO_SKIN)
        draw.rectangle([cx + 5, head_cy + 1, cx + 8, head_cy + 4], fill=CRYO_EYE)

    # --- Icicle crown ---
    icicle_heights = [8, 5, 10, 5, 8]
    for i, (dx, ih) in enumerate(zip([-8, -4, 0, 4, 8], icicle_heights)):
        ix = cx + dx if direction in (DOWN, UP) else cx + dx + (-2 if direction == LEFT else 2)
        iy = head_cy - 6
        draw.polygon([(ix - 1, iy), (ix, iy - ih - crystal_pulse), (ix + 1, iy)],
                     fill=CRYO_ICE, outline=None)
        draw.point((ix, iy - ih + 1 - crystal_pulse), fill=CRYO_ICE_BRIGHT)

    # --- Snow particles ---
    snow_positions = [
        (cx - 14, body_cy - 8), (cx + 12, body_cy + 4),
        (cx - 8, body_cy + 10), (cx + 16, body_cy - 4),
        (cx - 18, body_cy + 2), (cx + 6, body_cy - 12),
    ]
    snow_offset = frame * 3
    for i, (sx, sy) in enumerate(snow_positions):
        if (i + frame) % 2 != 0:
            continue
        px = sx + (snow_offset + i * 5) % 7 - 3
        py = sy + (snow_offset + i * 3) % 5 - 2
        draw.point((px, py), fill=CRYO_SNOW)
        draw.point((px + 1, py), fill=CRYO_SNOW)


# ===================================================================
# STORMCALLER (ID 14) -- Purple robes, lightning bolt effects from
#                         hands, storm cloud above head
# ===================================================================

def draw_stormcaller(draw, ox, oy, direction, frame):
    bob = [0, -2, 0, -1][frame]
    robe_sway = [-2, 0, 2, 0][frame]
    bolt_flicker = [0, 2, -1, 3][frame]

    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 20
    head_cy = body_cy - 18

    # --- Robe body ---
    if direction == DOWN:
        draw.polygon([
            (cx - 14, body_cy - 10),
            (cx + 14, body_cy - 10),
            (cx + 18 + robe_sway, base_y + 2),
            (cx - 18 + robe_sway, base_y + 2),
        ], fill=STORM_ROBE, outline=OUTLINE)
        draw.line([(cx - 4, body_cy - 6), (cx - 6 + robe_sway, base_y)],
                  fill=STORM_ROBE_DARK, width=1)
        draw.line([(cx + 4, body_cy - 6), (cx + 6 + robe_sway, base_y)],
                  fill=STORM_ROBE_DARK, width=1)
        draw.line([(cx - 10, body_cy - 4), (cx - 14 + robe_sway, base_y)],
                  fill=STORM_ROBE_LIGHT, width=1)
        draw.rectangle([cx - 14, body_cy + 6, cx + 14, body_cy + 12],
                       fill=STORM_ROBE_DARK, outline=OUTLINE)
    elif direction == UP:
        draw.polygon([
            (cx - 14, body_cy - 10),
            (cx + 14, body_cy - 10),
            (cx + 18 + robe_sway, base_y + 2),
            (cx - 18 + robe_sway, base_y + 2),
        ], fill=STORM_ROBE, outline=OUTLINE)
        draw.line([(cx, body_cy - 6), (cx + robe_sway, base_y)],
                  fill=STORM_ROBE_DARK, width=1)
        draw.rectangle([cx - 14, body_cy + 6, cx + 14, body_cy + 12],
                       fill=STORM_ROBE_DARK, outline=OUTLINE)
    elif direction == LEFT:
        draw.polygon([
            (cx - 10, body_cy - 10),
            (cx + 8, body_cy - 10),
            (cx + 12 + robe_sway, base_y + 2),
            (cx - 14 + robe_sway, base_y + 2),
        ], fill=STORM_ROBE, outline=OUTLINE)
        draw.line([(cx - 2, body_cy - 6), (cx - 4 + robe_sway, base_y)],
                  fill=STORM_ROBE_DARK, width=1)
        draw.rectangle([cx - 10, body_cy + 6, cx + 8, body_cy + 12],
                       fill=STORM_ROBE_DARK, outline=OUTLINE)
    else:  # RIGHT
        draw.polygon([
            (cx - 8, body_cy - 10),
            (cx + 10, body_cy - 10),
            (cx + 14 + robe_sway, base_y + 2),
            (cx - 12 + robe_sway, base_y + 2),
        ], fill=STORM_ROBE, outline=OUTLINE)
        draw.line([(cx + 2, body_cy - 6), (cx + 4 + robe_sway, base_y)],
                  fill=STORM_ROBE_DARK, width=1)
        draw.rectangle([cx - 8, body_cy + 6, cx + 10, body_cy + 12],
                       fill=STORM_ROBE_DARK, outline=OUTLINE)

    # --- Arms with lightning from hands ---
    if direction == DOWN:
        for side in [-1, 1]:
            ax = cx + side * 16
            draw.rectangle([ax - 3, body_cy - 4, ax + 3, body_cy + 4],
                           fill=STORM_ROBE, outline=OUTLINE)
            draw.rectangle([ax - 3, body_cy + 2, ax + 3, body_cy + 6],
                           fill=STORM_SKIN, outline=OUTLINE)
            # Lightning bolts from hands
            bx = ax + side * 2
            by = body_cy + 8
            draw.line([(bx, by), (bx + side * 3, by + 4 + bolt_flicker),
                       (bx, by + 8 + bolt_flicker)],
                      fill=STORM_LIGHTNING, width=2)
            draw.line([(bx, by + 2), (bx + side * 2, by + 5 + bolt_flicker)],
                      fill=STORM_LIGHTNING_BRIGHT, width=1)
    elif direction == UP:
        for side in [-1, 1]:
            ax = cx + side * 16
            draw.rectangle([ax - 3, body_cy - 4, ax + 3, body_cy + 4],
                           fill=STORM_ROBE, outline=OUTLINE)
    elif direction == LEFT:
        ax = cx - 12
        draw.rectangle([ax - 3, body_cy - 3, ax + 3, body_cy + 5],
                       fill=STORM_ROBE, outline=OUTLINE)
        draw.rectangle([ax - 3, body_cy + 2, ax + 3, body_cy + 6],
                       fill=STORM_SKIN, outline=OUTLINE)
        draw.line([(ax - 2, body_cy + 8), (ax - 5, body_cy + 12 + bolt_flicker),
                   (ax - 2, body_cy + 16 + bolt_flicker)],
                  fill=STORM_LIGHTNING, width=2)
    else:  # RIGHT
        ax = cx + 12
        draw.rectangle([ax - 3, body_cy - 3, ax + 3, body_cy + 5],
                       fill=STORM_ROBE, outline=OUTLINE)
        draw.rectangle([ax - 3, body_cy + 2, ax + 3, body_cy + 6],
                       fill=STORM_SKIN, outline=OUTLINE)
        draw.line([(ax + 2, body_cy + 8), (ax + 5, body_cy + 12 + bolt_flicker),
                   (ax + 2, body_cy + 16 + bolt_flicker)],
                  fill=STORM_LIGHTNING, width=2)

    # --- Head ---
    if direction == DOWN:
        ellipse(draw, cx, head_cy, 12, 10, STORM_ROBE)
        ellipse(draw, cx, head_cy - 2, 10, 8, STORM_ROBE_LIGHT, outline=None)
        ellipse(draw, cx, head_cy + 2, 8, 6, STORM_SKIN)
        draw.rectangle([cx - 5, head_cy + 1, cx - 2, head_cy + 4], fill=STORM_EYE)
        draw.rectangle([cx + 2, head_cy + 1, cx + 5, head_cy + 4], fill=STORM_EYE)
        draw.point((cx - 4, head_cy + 2), fill=STORM_LIGHTNING_BRIGHT)
        draw.point((cx + 3, head_cy + 2), fill=STORM_LIGHTNING_BRIGHT)
    elif direction == UP:
        ellipse(draw, cx, head_cy, 12, 10, STORM_ROBE)
        ellipse(draw, cx, head_cy, 9, 7, STORM_ROBE_DARK, outline=None)
    elif direction == LEFT:
        ellipse(draw, cx - 2, head_cy, 11, 10, STORM_ROBE)
        ellipse(draw, cx - 4, head_cy + 2, 6, 5, STORM_SKIN)
        draw.rectangle([cx - 8, head_cy + 1, cx - 5, head_cy + 4], fill=STORM_EYE)
    else:  # RIGHT
        ellipse(draw, cx + 2, head_cy, 11, 10, STORM_ROBE)
        ellipse(draw, cx + 4, head_cy + 2, 6, 5, STORM_SKIN)
        draw.rectangle([cx + 5, head_cy + 1, cx + 8, head_cy + 4], fill=STORM_EYE)

    # --- Storm cloud above head ---
    cloud_y = head_cy - 18
    cloud_x = cx if direction in (DOWN, UP) else cx + (-2 if direction == LEFT else 2)
    cloud_sway = [-1, 0, 1, 0][frame]
    # Cloud puffs
    ellipse(draw, cloud_x + cloud_sway, cloud_y, 10, 5, STORM_CLOUD)
    ellipse(draw, cloud_x - 4 + cloud_sway, cloud_y + 1, 6, 4, STORM_CLOUD_DARK, outline=None)
    ellipse(draw, cloud_x + 4 + cloud_sway, cloud_y - 1, 7, 4, STORM_CLOUD_LIGHT, outline=None)
    # Mini lightning from cloud
    if direction != UP:
        bx = cloud_x + cloud_sway + bolt_flicker
        draw.line([(bx, cloud_y + 4), (bx - 2, cloud_y + 8),
                   (bx + 1, cloud_y + 10)],
                  fill=STORM_LIGHTNING_BRIGHT, width=1)


# ===================================================================
# EARTHSHAKER (ID 15) -- Heavy brown/stone armor, rocky shoulders,
#                         crystal formations, cracks at feet
# ===================================================================

def draw_earthshaker(draw, ox, oy, direction, frame):
    bob = [0, -2, 0, -1][frame]
    leg_spread = [-4, 0, 4, 0][frame]
    rumble = [0, 1, 0, -1][frame]

    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 20
    head_cy = body_cy - 20

    # --- Legs (stocky) ---
    if direction in (DOWN, UP):
        for side in [-1, 1]:
            ls = leg_spread if side == -1 else -leg_spread
            lx = cx + side * 7 + ls
            draw.rectangle([lx - 4, body_cy + 10, lx + 4, base_y],
                           fill=EARTH_ARMOR, outline=OUTLINE)
            draw.rectangle([lx - 4, body_cy + 10, lx - 2, base_y - 6],
                           fill=EARTH_ARMOR_LIGHT, outline=None)
            # Stone boots
            draw.rectangle([lx - 4, base_y - 6, lx + 4, base_y],
                           fill=EARTH_STONE, outline=OUTLINE)
            draw.point((lx, base_y - 3), fill=EARTH_STONE_LIGHT)
    elif direction == LEFT:
        for i, offset in enumerate([leg_spread, -leg_spread]):
            lx = cx - 3 + offset
            draw.rectangle([lx - 4, body_cy + 10, lx + 4, base_y],
                           fill=EARTH_ARMOR, outline=OUTLINE)
            draw.rectangle([lx - 4, base_y - 6, lx + 4, base_y],
                           fill=EARTH_STONE, outline=OUTLINE)
    else:  # RIGHT
        for i, offset in enumerate([leg_spread, -leg_spread]):
            lx = cx + 3 + offset
            draw.rectangle([lx - 4, body_cy + 10, lx + 4, base_y],
                           fill=EARTH_ARMOR, outline=OUTLINE)
            draw.rectangle([lx - 4, base_y - 6, lx + 4, base_y],
                           fill=EARTH_STONE, outline=OUTLINE)

    # --- Heavy body ---
    if direction == DOWN:
        ellipse(draw, cx, body_cy, 16, 14, EARTH_ARMOR)
        ellipse(draw, cx + 4, body_cy + 2, 12, 10, EARTH_ARMOR_DARK, outline=None)
        ellipse(draw, cx - 2, body_cy - 2, 10, 8, EARTH_ARMOR_LIGHT, outline=None)
        # Stone plate detail
        draw.line([(cx - 4, body_cy - 8), (cx - 8, body_cy + 6)],
                  fill=EARTH_CRACK, width=1)
        draw.line([(cx + 4, body_cy - 8), (cx + 8, body_cy + 6)],
                  fill=EARTH_CRACK, width=1)
        # Belt with stone buckle
        draw.rectangle([cx - 16, body_cy + 8, cx + 16, body_cy + 14],
                       fill=EARTH_ARMOR_DARK, outline=OUTLINE)
        draw.rectangle([cx - 4, body_cy + 9, cx + 4, body_cy + 13],
                       fill=EARTH_STONE_LIGHT, outline=OUTLINE)
        draw.point((cx, body_cy + 11), fill=EARTH_CRYSTAL)
    elif direction == UP:
        ellipse(draw, cx, body_cy, 16, 14, EARTH_ARMOR)
        ellipse(draw, cx + 4, body_cy + 2, 12, 10, EARTH_ARMOR_DARK, outline=None)
        ellipse(draw, cx, body_cy - 2, 12, 10, _darken(EARTH_ARMOR, 0.85), outline=None)
        draw.line([(cx, body_cy - 6), (cx, body_cy + 6)],
                  fill=EARTH_CRACK, width=1)
        draw.rectangle([cx - 16, body_cy + 8, cx + 16, body_cy + 14],
                       fill=EARTH_ARMOR_DARK, outline=OUTLINE)
    elif direction == LEFT:
        ellipse(draw, cx - 2, body_cy, 14, 14, EARTH_ARMOR)
        ellipse(draw, cx + 2, body_cy + 2, 10, 10, EARTH_ARMOR_DARK, outline=None)
        ellipse(draw, cx - 4, body_cy - 2, 8, 8, EARTH_ARMOR_LIGHT, outline=None)
        draw.rectangle([cx - 16, body_cy + 8, cx + 12, body_cy + 14],
                       fill=EARTH_ARMOR_DARK, outline=OUTLINE)
    else:  # RIGHT
        ellipse(draw, cx + 2, body_cy, 14, 14, EARTH_ARMOR)
        ellipse(draw, cx + 6, body_cy + 2, 10, 10, EARTH_ARMOR_DARK, outline=None)
        ellipse(draw, cx, body_cy - 2, 8, 8, EARTH_ARMOR_LIGHT, outline=None)
        draw.rectangle([cx - 12, body_cy + 8, cx + 16, body_cy + 14],
                       fill=EARTH_ARMOR_DARK, outline=OUTLINE)

    # --- Arms with rocky shoulders ---
    if direction == DOWN:
        for side in [-1, 1]:
            ax = cx + side * 18
            draw.rectangle([ax - 4, body_cy - 4, ax + 4, body_cy + 6],
                           fill=EARTH_ARMOR, outline=OUTLINE)
            draw.rectangle([ax - 3, body_cy + 2, ax + 3, body_cy + 6],
                           fill=EARTH_SKIN, outline=OUTLINE)
            # Rocky shoulder pauldron
            ellipse(draw, ax, body_cy - 6, 7, 5, EARTH_STONE)
            ellipse(draw, ax - 1, body_cy - 8, 4, 3, EARTH_STONE_LIGHT, outline=None)
            # Crystal shard on shoulder
            draw.polygon([(ax + side * 2, body_cy - 10),
                          (ax + side * 4, body_cy - 16 + rumble),
                          (ax + side * 6, body_cy - 10)],
                         fill=EARTH_CRYSTAL, outline=OUTLINE)
    elif direction == UP:
        for side in [-1, 1]:
            ax = cx + side * 18
            draw.rectangle([ax - 4, body_cy - 4, ax + 4, body_cy + 6],
                           fill=EARTH_ARMOR, outline=OUTLINE)
            ellipse(draw, ax, body_cy - 6, 7, 5, EARTH_STONE)
            draw.polygon([(ax + side * 2, body_cy - 10),
                          (ax + side * 4, body_cy - 16 + rumble),
                          (ax + side * 6, body_cy - 10)],
                         fill=EARTH_CRYSTAL, outline=OUTLINE)
    elif direction == LEFT:
        ax = cx - 14
        draw.rectangle([ax - 4, body_cy - 3, ax + 4, body_cy + 6],
                       fill=EARTH_ARMOR, outline=OUTLINE)
        draw.rectangle([ax - 3, body_cy + 2, ax + 3, body_cy + 6],
                       fill=EARTH_SKIN, outline=OUTLINE)
        ellipse(draw, ax, body_cy - 5, 7, 5, EARTH_STONE)
        draw.polygon([(ax - 4, body_cy - 9), (ax - 6, body_cy - 15 + rumble),
                      (ax - 2, body_cy - 9)],
                     fill=EARTH_CRYSTAL, outline=OUTLINE)
    else:  # RIGHT
        ax = cx + 14
        draw.rectangle([ax - 4, body_cy - 3, ax + 4, body_cy + 6],
                       fill=EARTH_ARMOR, outline=OUTLINE)
        draw.rectangle([ax - 3, body_cy + 2, ax + 3, body_cy + 6],
                       fill=EARTH_SKIN, outline=OUTLINE)
        ellipse(draw, ax, body_cy - 5, 7, 5, EARTH_STONE)
        draw.polygon([(ax + 2, body_cy - 9), (ax + 6, body_cy - 15 + rumble),
                      (ax + 4, body_cy - 9)],
                     fill=EARTH_CRYSTAL, outline=OUTLINE)

    # --- Head (stone helm) ---
    if direction == DOWN:
        ellipse(draw, cx, head_cy, 14, 12, EARTH_STONE)
        ellipse(draw, cx + 2, head_cy + 2, 10, 8, EARTH_STONE_DARK, outline=None)
        ellipse(draw, cx - 2, head_cy - 2, 8, 6, EARTH_STONE_LIGHT, outline=None)
        # Visor opening
        draw.rectangle([cx - 8, head_cy, cx + 8, head_cy + 4],
                       fill=BLACK, outline=None)
        # Eyes glow through visor
        draw.rectangle([cx - 5, head_cy + 1, cx - 2, head_cy + 3], fill=EARTH_CRYSTAL)
        draw.rectangle([cx + 2, head_cy + 1, cx + 5, head_cy + 3], fill=EARTH_CRYSTAL)
        # Moss growth on helm
        draw.point((cx - 10, head_cy - 4), fill=EARTH_MOSS)
        draw.point((cx - 11, head_cy - 3), fill=EARTH_MOSS)
        draw.point((cx + 10, head_cy - 2), fill=EARTH_MOSS)
    elif direction == UP:
        ellipse(draw, cx, head_cy, 14, 12, EARTH_STONE)
        ellipse(draw, cx + 2, head_cy + 2, 10, 8, EARTH_STONE_DARK, outline=None)
        ellipse(draw, cx, head_cy - 2, 10, 8, _darken(EARTH_STONE, 0.85), outline=None)
        draw.point((cx - 8, head_cy - 4), fill=EARTH_MOSS)
        draw.point((cx + 8, head_cy - 6), fill=EARTH_MOSS)
    elif direction == LEFT:
        ellipse(draw, cx - 2, head_cy, 13, 12, EARTH_STONE)
        ellipse(draw, cx + 2, head_cy + 2, 9, 8, EARTH_STONE_DARK, outline=None)
        ellipse(draw, cx - 4, head_cy - 2, 7, 6, EARTH_STONE_LIGHT, outline=None)
        draw.rectangle([cx - 10, head_cy, cx - 2, head_cy + 4], fill=BLACK, outline=None)
        draw.rectangle([cx - 8, head_cy + 1, cx - 4, head_cy + 3], fill=EARTH_CRYSTAL)
    else:  # RIGHT
        ellipse(draw, cx + 2, head_cy, 13, 12, EARTH_STONE)
        ellipse(draw, cx + 6, head_cy + 2, 9, 8, EARTH_STONE_DARK, outline=None)
        ellipse(draw, cx, head_cy - 2, 7, 6, EARTH_STONE_LIGHT, outline=None)
        draw.rectangle([cx + 2, head_cy, cx + 10, head_cy + 4], fill=BLACK, outline=None)
        draw.rectangle([cx + 4, head_cy + 1, cx + 8, head_cy + 3], fill=EARTH_CRYSTAL)

    # --- Cracks at feet ---
    crack_y = base_y + 1
    if direction in (DOWN, UP):
        draw.line([(cx - 8, crack_y), (cx - 4, crack_y + rumble)],
                  fill=EARTH_CRACK, width=1)
        draw.line([(cx - 4, crack_y + rumble), (cx, crack_y)],
                  fill=EARTH_CRACK, width=1)
        draw.line([(cx + 2, crack_y), (cx + 6, crack_y + rumble)],
                  fill=EARTH_CRACK, width=1)
        draw.line([(cx + 6, crack_y + rumble), (cx + 10, crack_y)],
                  fill=EARTH_CRACK, width=1)
    elif direction == LEFT:
        draw.line([(cx - 12, crack_y), (cx - 6, crack_y + rumble),
                   (cx, crack_y)], fill=EARTH_CRACK, width=1)
    else:
        draw.line([(cx, crack_y), (cx + 6, crack_y + rumble),
                   (cx + 12, crack_y)], fill=EARTH_CRACK, width=1)


# ===================================================================
# WINDWALKER (ID 16) -- Light flowing white/sky-blue robes, wind
#                        swirl particles, flowing cape
# ===================================================================

def draw_windwalker(draw, ox, oy, direction, frame):
    bob = [0, -2, 0, -1][frame]
    robe_sway = [-3, 0, 3, 0][frame]
    cape_sway = [0, 3, 0, -3][frame]

    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 20
    head_cy = body_cy - 18

    # --- Flowing cape (drawn behind body) ---
    if direction == DOWN:
        draw.polygon([
            (cx - 14, body_cy - 8),
            (cx + 14, body_cy - 8),
            (cx + 18 + cape_sway, base_y),
            (cx - 18 + cape_sway, base_y),
        ], fill=WIND_CAPE, outline=OUTLINE)
        draw.line([(cx - 6, body_cy - 4), (cx - 8 + cape_sway, base_y - 2)],
                  fill=WIND_CAPE_DARK, width=1)
        draw.line([(cx + 6, body_cy - 4), (cx + 8 + cape_sway, base_y - 2)],
                  fill=WIND_CAPE_DARK, width=1)
    elif direction == UP:
        draw.polygon([
            (cx - 14, body_cy - 8),
            (cx + 14, body_cy - 8),
            (cx + 20 + cape_sway, base_y + 2),
            (cx - 20 + cape_sway, base_y + 2),
        ], fill=WIND_CAPE, outline=OUTLINE)
        draw.polygon([
            (cx - 8, body_cy - 4),
            (cx + 8, body_cy - 4),
            (cx + 14 + cape_sway, base_y - 2),
            (cx - 14 + cape_sway, base_y - 2),
        ], fill=_brighten(WIND_CAPE, 1.1), outline=None)
    elif direction == LEFT:
        draw.polygon([
            (cx + 4, body_cy - 8),
            (cx + 16, body_cy - 6),
            (cx + 18 + cape_sway, base_y),
            (cx + 4, base_y),
        ], fill=WIND_CAPE, outline=OUTLINE)
    else:  # RIGHT
        draw.polygon([
            (cx - 4, body_cy - 8),
            (cx - 16, body_cy - 6),
            (cx - 18 - cape_sway, base_y),
            (cx - 4, base_y),
        ], fill=WIND_CAPE, outline=OUTLINE)

    # --- Robe body (light, flowing) ---
    if direction == DOWN:
        draw.polygon([
            (cx - 12, body_cy - 10),
            (cx + 12, body_cy - 10),
            (cx + 16 + robe_sway, base_y + 2),
            (cx - 16 + robe_sway, base_y + 2),
        ], fill=WIND_ROBE, outline=OUTLINE)
        draw.line([(cx - 4, body_cy - 6), (cx - 6 + robe_sway, base_y)],
                  fill=WIND_ROBE_DARK, width=1)
        draw.line([(cx + 4, body_cy - 6), (cx + 6 + robe_sway, base_y)],
                  fill=WIND_ROBE_DARK, width=1)
        draw.line([(cx - 8, body_cy - 4), (cx - 12 + robe_sway, base_y)],
                  fill=WIND_ROBE_LIGHT, width=1)
    elif direction == UP:
        draw.polygon([
            (cx - 12, body_cy - 10),
            (cx + 12, body_cy - 10),
            (cx + 16 + robe_sway, base_y + 2),
            (cx - 16 + robe_sway, base_y + 2),
        ], fill=WIND_ROBE, outline=OUTLINE)
        draw.line([(cx, body_cy - 6), (cx + robe_sway, base_y)],
                  fill=WIND_ROBE_DARK, width=1)
    elif direction == LEFT:
        draw.polygon([
            (cx - 10, body_cy - 10),
            (cx + 6, body_cy - 10),
            (cx + 10 + robe_sway, base_y + 2),
            (cx - 14 + robe_sway, base_y + 2),
        ], fill=WIND_ROBE, outline=OUTLINE)
        draw.line([(cx - 2, body_cy - 6), (cx - 4 + robe_sway, base_y)],
                  fill=WIND_ROBE_DARK, width=1)
    else:  # RIGHT
        draw.polygon([
            (cx - 6, body_cy - 10),
            (cx + 10, body_cy - 10),
            (cx + 14 + robe_sway, base_y + 2),
            (cx - 10 + robe_sway, base_y + 2),
        ], fill=WIND_ROBE, outline=OUTLINE)
        draw.line([(cx + 2, body_cy - 6), (cx + 4 + robe_sway, base_y)],
                  fill=WIND_ROBE_DARK, width=1)

    # --- Arms ---
    if direction == DOWN:
        for side in [-1, 1]:
            ax = cx + side * 14
            draw.rectangle([ax - 3, body_cy - 4, ax + 3, body_cy + 4],
                           fill=WIND_ROBE, outline=OUTLINE)
            draw.rectangle([ax - 3, body_cy + 2, ax + 3, body_cy + 6],
                           fill=WIND_SKIN, outline=OUTLINE)
    elif direction == UP:
        for side in [-1, 1]:
            ax = cx + side * 14
            draw.rectangle([ax - 3, body_cy - 4, ax + 3, body_cy + 4],
                           fill=WIND_ROBE, outline=OUTLINE)
    elif direction == LEFT:
        ax = cx - 10
        draw.rectangle([ax - 3, body_cy - 3, ax + 3, body_cy + 5],
                       fill=WIND_ROBE, outline=OUTLINE)
        draw.rectangle([ax - 3, body_cy + 2, ax + 3, body_cy + 6],
                       fill=WIND_SKIN, outline=OUTLINE)
    else:  # RIGHT
        ax = cx + 10
        draw.rectangle([ax - 3, body_cy - 3, ax + 3, body_cy + 5],
                       fill=WIND_ROBE, outline=OUTLINE)
        draw.rectangle([ax - 3, body_cy + 2, ax + 3, body_cy + 6],
                       fill=WIND_SKIN, outline=OUTLINE)

    # --- Head ---
    if direction == DOWN:
        ellipse(draw, cx, head_cy, 12, 10, WIND_ROBE_LIGHT)
        ellipse(draw, cx, head_cy + 2, 8, 6, WIND_SKIN)
        ellipse(draw, cx + 2, head_cy + 4, 5, 4, WIND_SKIN_DARK, outline=None)
        draw.rectangle([cx - 5, head_cy + 1, cx - 2, head_cy + 4], fill=WIND_EYE)
        draw.rectangle([cx + 2, head_cy + 1, cx + 5, head_cy + 4], fill=WIND_EYE)
        draw.point((cx, head_cy + 6), fill=WIND_SKIN_DARK)
    elif direction == UP:
        ellipse(draw, cx, head_cy, 12, 10, WIND_ROBE_LIGHT)
        ellipse(draw, cx, head_cy, 9, 7, WIND_ROBE_DARK, outline=None)
    elif direction == LEFT:
        ellipse(draw, cx - 2, head_cy, 11, 10, WIND_ROBE_LIGHT)
        ellipse(draw, cx - 4, head_cy + 2, 6, 5, WIND_SKIN)
        draw.rectangle([cx - 8, head_cy + 1, cx - 5, head_cy + 4], fill=WIND_EYE)
    else:  # RIGHT
        ellipse(draw, cx + 2, head_cy, 11, 10, WIND_ROBE_LIGHT)
        ellipse(draw, cx + 4, head_cy + 2, 6, 5, WIND_SKIN)
        draw.rectangle([cx + 5, head_cy + 1, cx + 8, head_cy + 4], fill=WIND_EYE)

    # --- Wind swirl particles orbiting ---
    swirl_radius = 20
    for i in range(5):
        angle = frame * (math.pi / 2) + i * (2 * math.pi / 5)
        sx = int(cx + math.cos(angle) * swirl_radius)
        sy = int(body_cy + math.sin(angle) * (swirl_radius * 0.6))
        draw.point((sx, sy), fill=WIND_SWIRL_BRIGHT)
        draw.point((sx + 1, sy), fill=WIND_ROBE_LIGHT)
    # Secondary inner swirl ring
    for i in range(3):
        angle = frame * (math.pi / 2) + i * (2 * math.pi / 3) + math.pi / 6
        sx = int(cx + math.cos(angle) * 12)
        sy = int(body_cy + math.sin(angle) * 8)
        draw.point((sx, sy), fill=WIND_SWIRL_BRIGHT)


# ===================================================================
# MAGMA KNIGHT (ID 17) -- Dark armor with glowing lava cracks,
#                          molten glow from visor, orange seams
# ===================================================================

def draw_magmaknight(draw, ox, oy, direction, frame):
    bob = [0, -2, 0, -1][frame]
    leg_spread = [-4, 0, 4, 0][frame]
    lava_pulse = [0, 1, 0, -1][frame]

    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 20
    head_cy = body_cy - 20

    # --- Legs (heavy plate) ---
    if direction in (DOWN, UP):
        for side in [-1, 1]:
            ls = leg_spread if side == -1 else -leg_spread
            lx = cx + side * 7 + ls
            draw.rectangle([lx - 4, body_cy + 10, lx + 4, base_y],
                           fill=MAGMA_ARMOR, outline=OUTLINE)
            # Lava crack seam on legs
            draw.line([(lx, body_cy + 12), (lx + 1, base_y - 4)],
                      fill=MAGMA_LAVA, width=1)
            # Boots
            draw.rectangle([lx - 4, base_y - 6, lx + 4, base_y],
                           fill=MAGMA_ARMOR_DARK, outline=OUTLINE)
            draw.point((lx - 2, base_y - 3), fill=MAGMA_LAVA_DIM)
    elif direction == LEFT:
        for i, offset in enumerate([leg_spread, -leg_spread]):
            lx = cx - 3 + offset
            draw.rectangle([lx - 4, body_cy + 10, lx + 4, base_y],
                           fill=MAGMA_ARMOR, outline=OUTLINE)
            draw.line([(lx, body_cy + 12), (lx, base_y - 4)],
                      fill=MAGMA_LAVA, width=1)
            draw.rectangle([lx - 4, base_y - 6, lx + 4, base_y],
                           fill=MAGMA_ARMOR_DARK, outline=OUTLINE)
    else:  # RIGHT
        for i, offset in enumerate([leg_spread, -leg_spread]):
            lx = cx + 3 + offset
            draw.rectangle([lx - 4, body_cy + 10, lx + 4, base_y],
                           fill=MAGMA_ARMOR, outline=OUTLINE)
            draw.line([(lx, body_cy + 12), (lx, base_y - 4)],
                      fill=MAGMA_LAVA, width=1)
            draw.rectangle([lx - 4, base_y - 6, lx + 4, base_y],
                           fill=MAGMA_ARMOR_DARK, outline=OUTLINE)

    # --- Heavy plate body ---
    if direction == DOWN:
        ellipse(draw, cx, body_cy, 16, 14, MAGMA_ARMOR)
        ellipse(draw, cx + 4, body_cy + 2, 12, 10, MAGMA_ARMOR_DARK, outline=None)
        ellipse(draw, cx - 2, body_cy - 2, 10, 8, MAGMA_ARMOR_LIGHT, outline=None)
        # Lava cracks across body
        draw.line([(cx - 8, body_cy - 4), (cx - 4, body_cy), (cx - 6, body_cy + 6)],
                  fill=MAGMA_LAVA, width=1)
        draw.line([(cx + 4, body_cy - 6), (cx + 8, body_cy + 2)],
                  fill=MAGMA_LAVA, width=1)
        # Bright lava glow in cracks (pulsing)
        if lava_pulse >= 0:
            draw.point((cx - 5, body_cy), fill=MAGMA_LAVA_BRIGHT)
            draw.point((cx + 6, body_cy - 2), fill=MAGMA_LAVA_BRIGHT)
        # Belt
        draw.rectangle([cx - 16, body_cy + 8, cx + 16, body_cy + 14],
                       fill=MAGMA_ARMOR_DARK, outline=OUTLINE)
        draw.line([(cx - 14, body_cy + 11), (cx + 14, body_cy + 11)],
                  fill=MAGMA_CRACK, width=1)
    elif direction == UP:
        ellipse(draw, cx, body_cy, 16, 14, MAGMA_ARMOR)
        ellipse(draw, cx + 4, body_cy + 2, 12, 10, MAGMA_ARMOR_DARK, outline=None)
        draw.line([(cx - 6, body_cy - 4), (cx - 2, body_cy + 4)],
                  fill=MAGMA_LAVA, width=1)
        draw.line([(cx + 6, body_cy - 2), (cx + 4, body_cy + 6)],
                  fill=MAGMA_LAVA, width=1)
        draw.rectangle([cx - 16, body_cy + 8, cx + 16, body_cy + 14],
                       fill=MAGMA_ARMOR_DARK, outline=OUTLINE)
    elif direction == LEFT:
        ellipse(draw, cx - 2, body_cy, 14, 14, MAGMA_ARMOR)
        ellipse(draw, cx + 2, body_cy + 2, 10, 10, MAGMA_ARMOR_DARK, outline=None)
        draw.line([(cx - 6, body_cy - 4), (cx - 4, body_cy + 4)],
                  fill=MAGMA_LAVA, width=1)
        draw.rectangle([cx - 16, body_cy + 8, cx + 12, body_cy + 14],
                       fill=MAGMA_ARMOR_DARK, outline=OUTLINE)
    else:  # RIGHT
        ellipse(draw, cx + 2, body_cy, 14, 14, MAGMA_ARMOR)
        ellipse(draw, cx + 6, body_cy + 2, 10, 10, MAGMA_ARMOR_DARK, outline=None)
        draw.line([(cx + 6, body_cy - 4), (cx + 4, body_cy + 4)],
                  fill=MAGMA_LAVA, width=1)
        draw.rectangle([cx - 12, body_cy + 8, cx + 16, body_cy + 14],
                       fill=MAGMA_ARMOR_DARK, outline=OUTLINE)

    # --- Arms with lava seams ---
    if direction == DOWN:
        for side in [-1, 1]:
            ax = cx + side * 18
            draw.rectangle([ax - 4, body_cy - 4, ax + 4, body_cy + 6],
                           fill=MAGMA_ARMOR, outline=OUTLINE)
            # Lava seam down arm
            draw.line([(ax, body_cy - 3), (ax + 1, body_cy + 5)],
                      fill=MAGMA_LAVA, width=1)
            # Shoulder pauldron
            ellipse(draw, ax, body_cy - 6, 6, 4, MAGMA_ARMOR_DARK)
            ellipse(draw, ax - 1, body_cy - 8, 3, 2, MAGMA_ARMOR_LIGHT, outline=None)
            draw.point((ax + 2, body_cy - 6), fill=MAGMA_CRACK)
    elif direction == UP:
        for side in [-1, 1]:
            ax = cx + side * 18
            draw.rectangle([ax - 4, body_cy - 4, ax + 4, body_cy + 6],
                           fill=MAGMA_ARMOR, outline=OUTLINE)
            draw.line([(ax, body_cy - 3), (ax, body_cy + 5)],
                      fill=MAGMA_LAVA, width=1)
            ellipse(draw, ax, body_cy - 6, 6, 4, MAGMA_ARMOR_DARK)
    elif direction == LEFT:
        ax = cx - 14
        draw.rectangle([ax - 4, body_cy - 3, ax + 4, body_cy + 6],
                       fill=MAGMA_ARMOR, outline=OUTLINE)
        draw.line([(ax, body_cy - 2), (ax, body_cy + 5)],
                  fill=MAGMA_LAVA, width=1)
        ellipse(draw, ax, body_cy - 5, 6, 4, MAGMA_ARMOR_DARK)
    else:  # RIGHT
        ax = cx + 14
        draw.rectangle([ax - 4, body_cy - 3, ax + 4, body_cy + 6],
                       fill=MAGMA_ARMOR, outline=OUTLINE)
        draw.line([(ax, body_cy - 2), (ax, body_cy + 5)],
                  fill=MAGMA_LAVA, width=1)
        ellipse(draw, ax, body_cy - 5, 6, 4, MAGMA_ARMOR_DARK)

    # --- Head (dark helm with glowing visor) ---
    if direction == DOWN:
        ellipse(draw, cx, head_cy, 14, 12, MAGMA_ARMOR)
        ellipse(draw, cx + 2, head_cy + 2, 10, 8, MAGMA_ARMOR_DARK, outline=None)
        ellipse(draw, cx - 2, head_cy - 2, 8, 6, MAGMA_ARMOR_LIGHT, outline=None)
        # Crest on helm
        draw.rectangle([cx - 2, head_cy - 14, cx + 2, head_cy - 4],
                       fill=MAGMA_ARMOR_LIGHT, outline=OUTLINE)
        # Visor slit with lava glow
        draw.rectangle([cx - 8, head_cy, cx + 8, head_cy + 3],
                       fill=BLACK, outline=None)
        draw.rectangle([cx - 6, head_cy + 1, cx + 6, head_cy + 2],
                       fill=MAGMA_VISOR, outline=None)
        # Bright visor center
        draw.rectangle([cx - 3, head_cy + 1, cx + 3, head_cy + 2],
                       fill=MAGMA_VISOR_BRIGHT, outline=None)
        # Lava cracks on helm
        draw.line([(cx - 10, head_cy - 4), (cx - 6, head_cy + 2)],
                  fill=MAGMA_CRACK, width=1)
        draw.line([(cx + 8, head_cy - 6), (cx + 10, head_cy)],
                  fill=MAGMA_CRACK, width=1)
    elif direction == UP:
        ellipse(draw, cx, head_cy, 14, 12, MAGMA_ARMOR)
        ellipse(draw, cx + 2, head_cy + 2, 10, 8, MAGMA_ARMOR_DARK, outline=None)
        draw.rectangle([cx - 2, head_cy - 14, cx + 2, head_cy - 4],
                       fill=MAGMA_ARMOR_LIGHT, outline=OUTLINE)
        draw.line([(cx - 8, head_cy - 4), (cx - 4, head_cy + 2)],
                  fill=MAGMA_CRACK, width=1)
    elif direction == LEFT:
        ellipse(draw, cx - 2, head_cy, 13, 12, MAGMA_ARMOR)
        ellipse(draw, cx + 2, head_cy + 2, 9, 8, MAGMA_ARMOR_DARK, outline=None)
        draw.rectangle([cx - 4, head_cy - 14, cx, head_cy - 4],
                       fill=MAGMA_ARMOR_LIGHT, outline=OUTLINE)
        draw.rectangle([cx - 10, head_cy, cx - 2, head_cy + 3],
                       fill=BLACK, outline=None)
        draw.rectangle([cx - 9, head_cy + 1, cx - 3, head_cy + 2],
                       fill=MAGMA_VISOR, outline=None)
    else:  # RIGHT
        ellipse(draw, cx + 2, head_cy, 13, 12, MAGMA_ARMOR)
        ellipse(draw, cx + 6, head_cy + 2, 9, 8, MAGMA_ARMOR_DARK, outline=None)
        draw.rectangle([cx, head_cy - 14, cx + 4, head_cy - 4],
                       fill=MAGMA_ARMOR_LIGHT, outline=OUTLINE)
        draw.rectangle([cx + 2, head_cy, cx + 10, head_cy + 3],
                       fill=BLACK, outline=None)
        draw.rectangle([cx + 3, head_cy + 1, cx + 9, head_cy + 2],
                       fill=MAGMA_VISOR, outline=None)


# ===================================================================
# FROSTBITE (ID 18) -- Hunched icy blue creature, ice spike shoulders,
#                       frozen breath effect
# ===================================================================

def draw_frostbite(draw, ox, oy, direction, frame):
    bob = [0, -2, 0, -1][frame]
    leg_spread = [-3, 0, 3, 0][frame]
    breath_extend = [0, 2, 4, 2][frame]

    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 18  # slightly hunched
    head_cy = body_cy - 16

    # --- Legs (digitigrade / bestial) ---
    if direction in (DOWN, UP):
        for side in [-1, 1]:
            ls = leg_spread if side == -1 else -leg_spread
            lx = cx + side * 7 + ls
            draw.rectangle([lx - 3, body_cy + 8, lx + 3, base_y],
                           fill=FROST_BODY, outline=OUTLINE)
            draw.rectangle([lx - 3, body_cy + 8, lx - 1, base_y - 4],
                           fill=FROST_BODY_LIGHT, outline=None)
            # Clawed feet
            draw.polygon([(lx - 4, base_y), (lx, base_y - 4), (lx + 4, base_y)],
                         fill=FROST_CLAW, outline=OUTLINE)
            draw.point((lx - 2, base_y - 1), fill=FROST_SPIKE_BRIGHT)
            draw.point((lx + 2, base_y - 1), fill=FROST_SPIKE_BRIGHT)
    elif direction == LEFT:
        for i, offset in enumerate([leg_spread, -leg_spread]):
            lx = cx - 3 + offset
            draw.rectangle([lx - 3, body_cy + 8, lx + 3, base_y],
                           fill=FROST_BODY, outline=OUTLINE)
            draw.polygon([(lx - 4, base_y), (lx, base_y - 4), (lx + 4, base_y)],
                         fill=FROST_CLAW, outline=OUTLINE)
    else:  # RIGHT
        for i, offset in enumerate([leg_spread, -leg_spread]):
            lx = cx + 3 + offset
            draw.rectangle([lx - 3, body_cy + 8, lx + 3, base_y],
                           fill=FROST_BODY, outline=OUTLINE)
            draw.polygon([(lx - 4, base_y), (lx, base_y - 4), (lx + 4, base_y)],
                         fill=FROST_CLAW, outline=OUTLINE)

    # --- Hunched body ---
    if direction == DOWN:
        ellipse(draw, cx, body_cy, 14, 12, FROST_BODY)
        ellipse(draw, cx + 3, body_cy + 2, 10, 8, FROST_BODY_DARK, outline=None)
        ellipse(draw, cx - 2, body_cy - 2, 8, 6, FROST_BODY_LIGHT, outline=None)
        # Frost texture on body
        draw.point((cx - 6, body_cy - 4), fill=FROST_SPIKE_BRIGHT)
        draw.point((cx + 8, body_cy), fill=FROST_SPIKE_BRIGHT)
        draw.point((cx - 2, body_cy + 4), fill=FROST_SPIKE_BRIGHT)
    elif direction == UP:
        ellipse(draw, cx, body_cy, 14, 12, FROST_BODY)
        ellipse(draw, cx + 3, body_cy + 2, 10, 8, FROST_BODY_DARK, outline=None)
        ellipse(draw, cx, body_cy - 2, 10, 8, _darken(FROST_BODY, 0.85), outline=None)
    elif direction == LEFT:
        ellipse(draw, cx - 2, body_cy, 12, 12, FROST_BODY)
        ellipse(draw, cx + 2, body_cy + 2, 8, 8, FROST_BODY_DARK, outline=None)
        ellipse(draw, cx - 4, body_cy - 2, 6, 6, FROST_BODY_LIGHT, outline=None)
    else:  # RIGHT
        ellipse(draw, cx + 2, body_cy, 12, 12, FROST_BODY)
        ellipse(draw, cx + 6, body_cy + 2, 8, 8, FROST_BODY_DARK, outline=None)
        ellipse(draw, cx, body_cy - 2, 6, 6, FROST_BODY_LIGHT, outline=None)

    # --- Arms with ice spikes ---
    if direction == DOWN:
        for side in [-1, 1]:
            ax = cx + side * 14
            draw.rectangle([ax - 3, body_cy - 2, ax + 3, body_cy + 6],
                           fill=FROST_BODY, outline=OUTLINE)
            # Claw hand
            draw.polygon([(ax - 3, body_cy + 6), (ax, body_cy + 10),
                          (ax + 3, body_cy + 6)],
                         fill=FROST_CLAW, outline=OUTLINE)
            # Ice spike shoulder
            draw.polygon([(ax - 3, body_cy - 4), (ax + side * 2, body_cy - 14),
                          (ax + 3, body_cy - 4)],
                         fill=FROST_SPIKE, outline=OUTLINE)
            draw.polygon([(ax - 1, body_cy - 4), (ax + side * 1, body_cy - 12),
                          (ax + 1, body_cy - 4)],
                         fill=FROST_SPIKE_BRIGHT, outline=None)
            # Secondary smaller spike
            draw.polygon([(ax + side * 3, body_cy - 2),
                          (ax + side * 6, body_cy - 10),
                          (ax + side * 5, body_cy - 2)],
                         fill=FROST_SPIKE_DARK, outline=OUTLINE)
    elif direction == UP:
        for side in [-1, 1]:
            ax = cx + side * 14
            draw.rectangle([ax - 3, body_cy - 2, ax + 3, body_cy + 6],
                           fill=FROST_BODY, outline=OUTLINE)
            draw.polygon([(ax - 3, body_cy - 4), (ax + side * 2, body_cy - 14),
                          (ax + 3, body_cy - 4)],
                         fill=FROST_SPIKE, outline=OUTLINE)
    elif direction == LEFT:
        ax = cx - 12
        draw.rectangle([ax - 3, body_cy - 1, ax + 3, body_cy + 6],
                       fill=FROST_BODY, outline=OUTLINE)
        draw.polygon([(ax - 3, body_cy + 6), (ax, body_cy + 10),
                      (ax + 3, body_cy + 6)],
                     fill=FROST_CLAW, outline=OUTLINE)
        draw.polygon([(ax - 3, body_cy - 3), (ax - 4, body_cy - 13),
                      (ax + 3, body_cy - 3)],
                     fill=FROST_SPIKE, outline=OUTLINE)
        draw.polygon([(ax - 5, body_cy - 1), (ax - 8, body_cy - 9),
                      (ax - 3, body_cy - 1)],
                     fill=FROST_SPIKE_DARK, outline=OUTLINE)
    else:  # RIGHT
        ax = cx + 12
        draw.rectangle([ax - 3, body_cy - 1, ax + 3, body_cy + 6],
                       fill=FROST_BODY, outline=OUTLINE)
        draw.polygon([(ax - 3, body_cy + 6), (ax, body_cy + 10),
                      (ax + 3, body_cy + 6)],
                     fill=FROST_CLAW, outline=OUTLINE)
        draw.polygon([(ax - 3, body_cy - 3), (ax + 4, body_cy - 13),
                      (ax + 3, body_cy - 3)],
                     fill=FROST_SPIKE, outline=OUTLINE)
        draw.polygon([(ax + 3, body_cy - 1), (ax + 8, body_cy - 9),
                      (ax + 5, body_cy - 1)],
                     fill=FROST_SPIKE_DARK, outline=OUTLINE)

    # --- Head (bestial, icy) ---
    if direction == DOWN:
        ellipse(draw, cx, head_cy, 10, 9, FROST_BODY)
        ellipse(draw, cx + 2, head_cy + 2, 7, 5, FROST_BODY_DARK, outline=None)
        ellipse(draw, cx - 2, head_cy - 2, 6, 4, FROST_BODY_LIGHT, outline=None)
        # Glowing eyes
        draw.rectangle([cx - 5, head_cy, cx - 2, head_cy + 3], fill=FROST_EYE)
        draw.rectangle([cx + 2, head_cy, cx + 5, head_cy + 3], fill=FROST_EYE)
        draw.point((cx - 4, head_cy + 1), fill=FROST_SPIKE_BRIGHT)
        draw.point((cx + 3, head_cy + 1), fill=FROST_SPIKE_BRIGHT)
        # Jagged mouth
        draw.line([(cx - 4, head_cy + 5), (cx + 4, head_cy + 5)],
                  fill=FROST_BODY_DARK, width=1)
        draw.point((cx - 2, head_cy + 6), fill=FROST_SPIKE_BRIGHT)
        draw.point((cx + 2, head_cy + 6), fill=FROST_SPIKE_BRIGHT)
        # Frozen breath effect
        for i in range(breath_extend):
            bx = cx + (i * 2) - breath_extend
            by = head_cy + 8 + i
            draw.point((bx, by), fill=FROST_SPIKE_BRIGHT)
            draw.point((bx + 1, by), fill=CRYO_SNOW)
    elif direction == UP:
        ellipse(draw, cx, head_cy, 10, 9, FROST_BODY)
        ellipse(draw, cx + 2, head_cy + 2, 7, 5, FROST_BODY_DARK, outline=None)
        ellipse(draw, cx, head_cy - 2, 7, 5, _darken(FROST_BODY, 0.8), outline=None)
    elif direction == LEFT:
        ellipse(draw, cx - 2, head_cy, 9, 9, FROST_BODY)
        ellipse(draw, cx + 1, head_cy + 2, 6, 5, FROST_BODY_DARK, outline=None)
        draw.rectangle([cx - 7, head_cy, cx - 3, head_cy + 3], fill=FROST_EYE)
        draw.line([(cx - 8, head_cy + 5), (cx - 2, head_cy + 5)],
                  fill=FROST_BODY_DARK, width=1)
        # Breath sideways
        for i in range(breath_extend):
            bx = cx - 10 - i * 2
            by = head_cy + 4 + i
            draw.point((bx, by), fill=FROST_SPIKE_BRIGHT)
    else:  # RIGHT
        ellipse(draw, cx + 2, head_cy, 9, 9, FROST_BODY)
        ellipse(draw, cx + 5, head_cy + 2, 6, 5, FROST_BODY_DARK, outline=None)
        draw.rectangle([cx + 3, head_cy, cx + 7, head_cy + 3], fill=FROST_EYE)
        draw.line([(cx + 2, head_cy + 5), (cx + 8, head_cy + 5)],
                  fill=FROST_BODY_DARK, width=1)
        for i in range(breath_extend):
            bx = cx + 10 + i * 2
            by = head_cy + 4 + i
            draw.point((bx, by), fill=FROST_SPIKE_BRIGHT)

    # --- Ice spike crown on head ---
    for dx in [-6, -2, 2, 6]:
        ix = cx + dx if direction in (DOWN, UP) else cx + dx + (-2 if direction == LEFT else 2)
        iy = head_cy - 6
        draw.polygon([(ix - 1, iy), (ix, iy - 5), (ix + 1, iy)],
                     fill=FROST_SPIKE, outline=None)


# ===================================================================
# SANDSTORM (ID 19) -- Desert robes, sand particle swirl around
#                       lower body, turban headwrap
# ===================================================================

def draw_sandstorm(draw, ox, oy, direction, frame):
    bob = [0, -2, 0, -1][frame]
    robe_sway = [-2, 0, 2, 0][frame]
    sand_phase = frame * (math.pi / 2)

    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 20
    head_cy = body_cy - 18

    # --- Robe body ---
    if direction == DOWN:
        draw.polygon([
            (cx - 14, body_cy - 10),
            (cx + 14, body_cy - 10),
            (cx + 18 + robe_sway, base_y + 2),
            (cx - 18 + robe_sway, base_y + 2),
        ], fill=SAND_ROBE, outline=OUTLINE)
        draw.line([(cx - 4, body_cy - 6), (cx - 6 + robe_sway, base_y)],
                  fill=SAND_ROBE_DARK, width=1)
        draw.line([(cx + 4, body_cy - 6), (cx + 6 + robe_sway, base_y)],
                  fill=SAND_ROBE_DARK, width=1)
        draw.line([(cx - 10, body_cy - 4), (cx - 14 + robe_sway, base_y)],
                  fill=SAND_ROBE_LIGHT, width=1)
        # Belt / sash
        draw.rectangle([cx - 14, body_cy + 6, cx + 14, body_cy + 12],
                       fill=SAND_ROBE_DARK, outline=OUTLINE)
        # Sash tail hanging
        draw.polygon([(cx + 8, body_cy + 12), (cx + 6, body_cy + 18),
                      (cx + 10, body_cy + 18), (cx + 12, body_cy + 12)],
                     fill=SAND_ROBE_DARK, outline=OUTLINE)
    elif direction == UP:
        draw.polygon([
            (cx - 14, body_cy - 10),
            (cx + 14, body_cy - 10),
            (cx + 18 + robe_sway, base_y + 2),
            (cx - 18 + robe_sway, base_y + 2),
        ], fill=SAND_ROBE, outline=OUTLINE)
        draw.line([(cx, body_cy - 6), (cx + robe_sway, base_y)],
                  fill=SAND_ROBE_DARK, width=1)
        draw.rectangle([cx - 14, body_cy + 6, cx + 14, body_cy + 12],
                       fill=SAND_ROBE_DARK, outline=OUTLINE)
    elif direction == LEFT:
        draw.polygon([
            (cx - 10, body_cy - 10),
            (cx + 8, body_cy - 10),
            (cx + 12 + robe_sway, base_y + 2),
            (cx - 14 + robe_sway, base_y + 2),
        ], fill=SAND_ROBE, outline=OUTLINE)
        draw.line([(cx - 2, body_cy - 6), (cx - 4 + robe_sway, base_y)],
                  fill=SAND_ROBE_DARK, width=1)
        draw.rectangle([cx - 10, body_cy + 6, cx + 8, body_cy + 12],
                       fill=SAND_ROBE_DARK, outline=OUTLINE)
    else:  # RIGHT
        draw.polygon([
            (cx - 8, body_cy - 10),
            (cx + 10, body_cy - 10),
            (cx + 14 + robe_sway, base_y + 2),
            (cx - 12 + robe_sway, base_y + 2),
        ], fill=SAND_ROBE, outline=OUTLINE)
        draw.line([(cx + 2, body_cy - 6), (cx + 4 + robe_sway, base_y)],
                  fill=SAND_ROBE_DARK, width=1)
        draw.rectangle([cx - 8, body_cy + 6, cx + 10, body_cy + 12],
                       fill=SAND_ROBE_DARK, outline=OUTLINE)

    # --- Arms ---
    if direction == DOWN:
        for side in [-1, 1]:
            ax = cx + side * 16
            draw.rectangle([ax - 3, body_cy - 4, ax + 3, body_cy + 4],
                           fill=SAND_ROBE, outline=OUTLINE)
            draw.rectangle([ax - 3, body_cy + 2, ax + 3, body_cy + 6],
                           fill=SAND_SKIN, outline=OUTLINE)
    elif direction == UP:
        for side in [-1, 1]:
            ax = cx + side * 16
            draw.rectangle([ax - 3, body_cy - 4, ax + 3, body_cy + 4],
                           fill=SAND_ROBE, outline=OUTLINE)
    elif direction == LEFT:
        ax = cx - 12
        draw.rectangle([ax - 3, body_cy - 3, ax + 3, body_cy + 5],
                       fill=SAND_ROBE, outline=OUTLINE)
        draw.rectangle([ax - 3, body_cy + 2, ax + 3, body_cy + 6],
                       fill=SAND_SKIN, outline=OUTLINE)
    else:  # RIGHT
        ax = cx + 12
        draw.rectangle([ax - 3, body_cy - 3, ax + 3, body_cy + 5],
                       fill=SAND_ROBE, outline=OUTLINE)
        draw.rectangle([ax - 3, body_cy + 2, ax + 3, body_cy + 6],
                       fill=SAND_SKIN, outline=OUTLINE)

    # --- Head with turban ---
    if direction == DOWN:
        # Turban (layered wraps)
        ellipse(draw, cx, head_cy - 4, 14, 10, SAND_TURBAN)
        ellipse(draw, cx + 2, head_cy - 2, 10, 7, SAND_TURBAN_DARK, outline=None)
        ellipse(draw, cx - 2, head_cy - 6, 8, 5, _brighten(SAND_TURBAN, 1.1), outline=None)
        # Turban wrap lines
        draw.line([(cx - 10, head_cy - 2), (cx + 10, head_cy - 4)],
                  fill=SAND_TURBAN_DARK, width=1)
        draw.line([(cx - 8, head_cy - 6), (cx + 8, head_cy - 8)],
                  fill=SAND_TURBAN_DARK, width=1)
        # Gem on front
        draw.rectangle([cx - 2, head_cy - 5, cx + 2, head_cy - 2],
                       fill=SAND_EYE, outline=OUTLINE)
        # Face
        ellipse(draw, cx, head_cy + 4, 8, 6, SAND_SKIN)
        ellipse(draw, cx + 1, head_cy + 6, 5, 4, SAND_SKIN_DARK, outline=None)
        # Eyes
        draw.rectangle([cx - 5, head_cy + 2, cx - 2, head_cy + 5], fill=SAND_EYE)
        draw.rectangle([cx + 2, head_cy + 2, cx + 5, head_cy + 5], fill=SAND_EYE)
        # Face covering (lower)
        draw.rectangle([cx - 6, head_cy + 6, cx + 6, head_cy + 10],
                       fill=SAND_ROBE, outline=None)
    elif direction == UP:
        ellipse(draw, cx, head_cy - 4, 14, 10, SAND_TURBAN)
        ellipse(draw, cx + 2, head_cy - 2, 10, 7, SAND_TURBAN_DARK, outline=None)
        draw.line([(cx - 10, head_cy - 2), (cx + 10, head_cy - 4)],
                  fill=SAND_TURBAN_DARK, width=1)
        # Turban tail hanging down back
        draw.polygon([(cx + 4, head_cy + 2), (cx + 6, head_cy + 10 + robe_sway),
                      (cx + 10, head_cy + 2)],
                     fill=SAND_TURBAN, outline=OUTLINE)
    elif direction == LEFT:
        ellipse(draw, cx - 2, head_cy - 4, 13, 10, SAND_TURBAN)
        ellipse(draw, cx + 1, head_cy - 2, 9, 7, SAND_TURBAN_DARK, outline=None)
        draw.line([(cx - 10, head_cy - 4), (cx + 6, head_cy - 6)],
                  fill=SAND_TURBAN_DARK, width=1)
        ellipse(draw, cx - 4, head_cy + 4, 6, 5, SAND_SKIN)
        draw.rectangle([cx - 8, head_cy + 2, cx - 5, head_cy + 5], fill=SAND_EYE)
        draw.rectangle([cx - 7, head_cy + 6, cx - 1, head_cy + 9],
                       fill=SAND_ROBE, outline=None)
    else:  # RIGHT
        ellipse(draw, cx + 2, head_cy - 4, 13, 10, SAND_TURBAN)
        ellipse(draw, cx + 5, head_cy - 2, 9, 7, SAND_TURBAN_DARK, outline=None)
        draw.line([(cx - 6, head_cy - 4), (cx + 10, head_cy - 6)],
                  fill=SAND_TURBAN_DARK, width=1)
        ellipse(draw, cx + 4, head_cy + 4, 6, 5, SAND_SKIN)
        draw.rectangle([cx + 5, head_cy + 2, cx + 8, head_cy + 5], fill=SAND_EYE)
        draw.rectangle([cx + 1, head_cy + 6, cx + 7, head_cy + 9],
                       fill=SAND_ROBE, outline=None)

    # --- Sand particle swirl around lower body ---
    sand_radius_outer = 22
    sand_radius_inner = 14
    for i in range(8):
        angle = sand_phase + i * (math.pi / 4)
        r = sand_radius_outer if i % 2 == 0 else sand_radius_inner
        sx = int(cx + math.cos(angle) * r)
        sy = int(base_y - 6 + math.sin(angle) * (r * 0.35))
        draw.point((sx, sy), fill=SAND_ROBE_LIGHT)
        draw.point((sx + 1, sy), fill=SAND_ROBE_LIGHT)
    # Additional scattered sand dots
    for i in range(5):
        angle = sand_phase + i * (2 * math.pi / 5) + math.pi / 3
        sx = int(cx + math.cos(angle) * 18)
        sy = int(base_y - 4 + math.sin(angle) * 6)
        draw.point((sx, sy), fill=SAND_TURBAN)


# ---------------------------------------------------------------------------
# Palettes (continued)
# ---------------------------------------------------------------------------

# Thornweaver palette
THORN_ROBE = (50, 100, 50)
THORN_ROBE_DARK = (35, 70, 35)
THORN_ROBE_LIGHT = (70, 130, 70)
THORN_SKIN = (200, 195, 170)
THORN_SKIN_DARK = (170, 165, 140)
THORN_VINE = (60, 110, 40)
THORN_VINE_DARK = (40, 80, 25)
THORN_THORN = (100, 70, 30)
THORN_STAFF = (110, 80, 40)
THORN_STAFF_DARK = (80, 55, 25)
THORN_LEAF = (80, 160, 50)
THORN_LEAF_DARK = (60, 120, 35)

# Cloudrunner palette
CLOUD_ROBE = (210, 225, 240)
CLOUD_ROBE_DARK = (175, 195, 215)
CLOUD_ROBE_LIGHT = (235, 245, 255)
CLOUD_SKIN = (225, 215, 205)
CLOUD_SKIN_DARK = (195, 185, 175)
CLOUD_PUFF = (240, 245, 255)
CLOUD_PUFF_DARK = (200, 210, 225)
CLOUD_EYE = (140, 190, 240)

# Inferno palette
INFERNO_BODY = (200, 60, 20)
INFERNO_BODY_DARK = (150, 40, 10)
INFERNO_BODY_LIGHT = (240, 100, 40)
INFERNO_CORE = (255, 200, 60)
INFERNO_CORE_BRIGHT = (255, 240, 140)
INFERNO_FLAME = (255, 140, 30)
INFERNO_FLAME_TIP = (255, 220, 80)
INFERNO_SPARK = (255, 180, 60)
INFERNO_EYE = (255, 255, 180)

# Glacier palette
GLACIER_BODY = (100, 150, 200)
GLACIER_BODY_DARK = (70, 110, 165)
GLACIER_BODY_LIGHT = (140, 190, 230)
GLACIER_PLATE = (130, 180, 230)
GLACIER_PLATE_DARK = (90, 140, 190)
GLACIER_PLATE_LIGHT = (170, 210, 245)
GLACIER_FROST = (200, 235, 255)
GLACIER_EYE = (180, 230, 255)

# Mudslinger palette
MUD_BODY = (120, 90, 60)
MUD_BODY_DARK = (85, 60, 38)
MUD_BODY_LIGHT = (155, 120, 85)
MUD_SPLASH = (100, 75, 45)
MUD_SPLASH_LIGHT = (140, 110, 70)
MUD_DRIP = (90, 65, 40)
MUD_EYE = (180, 160, 80)
MUD_CLOTH = (110, 80, 50)

# Ember palette
EMBER_BODY = (255, 160, 40)
EMBER_BODY_DARK = (220, 120, 20)
EMBER_BODY_LIGHT = (255, 200, 80)
EMBER_CORE = (255, 240, 120)
EMBER_SPARK = (255, 220, 100)
EMBER_SPARK_BRIGHT = (255, 255, 180)
EMBER_EYE = (255, 255, 200)

# Avalanche palette
AVAL_ARMOR = (160, 170, 180)
AVAL_ARMOR_DARK = (120, 130, 140)
AVAL_ARMOR_LIGHT = (195, 205, 215)
AVAL_ICE = (170, 210, 240)
AVAL_ICE_DARK = (130, 175, 210)
AVAL_ICE_BRIGHT = (210, 235, 255)
AVAL_FROST = (200, 230, 250)
AVAL_EYE = (160, 220, 255)
AVAL_SKIN = (200, 210, 220)


# ===================================================================
# THORNWEAVER (ID 20) -- Green robes, vine/thorn patterns, wooden
#                         staff, leaf particles
# ===================================================================

def draw_thornweaver(draw, ox, oy, direction, frame):
    bob = [0, -2, 0, -1][frame]
    robe_sway = [-2, 0, 2, 0][frame]
    vine_grow = [0, 1, 2, 1][frame]

    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 20
    head_cy = body_cy - 18

    # --- Wooden staff (behind body for DOWN/UP) ---
    if direction == DOWN:
        staff_x = cx + 14
        draw.line([(staff_x, head_cy - 10), (staff_x, base_y + 2)],
                  fill=THORN_STAFF, width=2)
        draw.line([(staff_x + 1, head_cy - 10), (staff_x + 1, base_y + 2)],
                  fill=THORN_STAFF_DARK, width=1)
        # Leaf cluster at top
        ellipse(draw, staff_x, head_cy - 14, 5, 4, THORN_LEAF)
        ellipse(draw, staff_x - 1, head_cy - 15, 3, 2, THORN_LEAF_DARK, outline=None)
        # Vine wrapping staff
        for vy in range(head_cy - 6, base_y - 4, 6):
            draw.point((staff_x - 1, vy), fill=THORN_VINE)
            draw.point((staff_x + 1, vy + 2), fill=THORN_VINE)
    elif direction == UP:
        staff_x = cx - 14
        draw.line([(staff_x, head_cy - 10), (staff_x, base_y + 2)],
                  fill=THORN_STAFF, width=2)
        ellipse(draw, staff_x, head_cy - 14, 5, 4, THORN_LEAF)
    elif direction == LEFT:
        staff_x = cx + 10
        draw.line([(staff_x, head_cy - 10), (staff_x, base_y + 2)],
                  fill=THORN_STAFF, width=2)
        ellipse(draw, staff_x, head_cy - 14, 5, 4, THORN_LEAF)
        draw.point((staff_x, head_cy - 15), fill=THORN_LEAF_DARK)
    else:  # RIGHT
        staff_x = cx - 10
        draw.line([(staff_x, head_cy - 10), (staff_x, base_y + 2)],
                  fill=THORN_STAFF, width=2)
        ellipse(draw, staff_x, head_cy - 14, 5, 4, THORN_LEAF)
        draw.point((staff_x, head_cy - 15), fill=THORN_LEAF_DARK)

    # --- Robe body ---
    if direction == DOWN:
        draw.polygon([
            (cx - 14, body_cy - 10),
            (cx + 14, body_cy - 10),
            (cx + 18 + robe_sway, base_y + 2),
            (cx - 18 + robe_sway, base_y + 2),
        ], fill=THORN_ROBE, outline=OUTLINE)
        draw.line([(cx - 4, body_cy - 6), (cx - 6 + robe_sway, base_y)],
                  fill=THORN_ROBE_DARK, width=1)
        draw.line([(cx + 4, body_cy - 6), (cx + 6 + robe_sway, base_y)],
                  fill=THORN_ROBE_DARK, width=1)
        draw.line([(cx - 10, body_cy - 4), (cx - 14 + robe_sway, base_y)],
                  fill=THORN_ROBE_LIGHT, width=1)
        # Vine pattern on robe
        draw.line([(cx - 6, body_cy), (cx - 8, body_cy + 6),
                   (cx - 4, body_cy + 10)],
                  fill=THORN_VINE, width=1)
        draw.line([(cx + 4, body_cy + 2), (cx + 6, body_cy + 8)],
                  fill=THORN_VINE, width=1)
        # Thorns on vine pattern
        draw.point((cx - 7, body_cy + 2), fill=THORN_THORN)
        draw.point((cx - 5, body_cy + 8), fill=THORN_THORN)
        draw.point((cx + 5, body_cy + 4), fill=THORN_THORN)
    elif direction == UP:
        draw.polygon([
            (cx - 14, body_cy - 10),
            (cx + 14, body_cy - 10),
            (cx + 18 + robe_sway, base_y + 2),
            (cx - 18 + robe_sway, base_y + 2),
        ], fill=THORN_ROBE, outline=OUTLINE)
        draw.line([(cx, body_cy - 6), (cx + robe_sway, base_y)],
                  fill=THORN_ROBE_DARK, width=1)
        draw.line([(cx - 6, body_cy), (cx - 8, body_cy + 8)],
                  fill=THORN_VINE, width=1)
        draw.line([(cx + 6, body_cy + 2), (cx + 4, body_cy + 10)],
                  fill=THORN_VINE, width=1)
    elif direction == LEFT:
        draw.polygon([
            (cx - 10, body_cy - 10),
            (cx + 8, body_cy - 10),
            (cx + 12 + robe_sway, base_y + 2),
            (cx - 14 + robe_sway, base_y + 2),
        ], fill=THORN_ROBE, outline=OUTLINE)
        draw.line([(cx - 2, body_cy - 6), (cx - 4 + robe_sway, base_y)],
                  fill=THORN_ROBE_DARK, width=1)
        draw.line([(cx - 4, body_cy), (cx - 6, body_cy + 6)],
                  fill=THORN_VINE, width=1)
        draw.point((cx - 5, body_cy + 2), fill=THORN_THORN)
    else:  # RIGHT
        draw.polygon([
            (cx - 8, body_cy - 10),
            (cx + 10, body_cy - 10),
            (cx + 14 + robe_sway, base_y + 2),
            (cx - 12 + robe_sway, base_y + 2),
        ], fill=THORN_ROBE, outline=OUTLINE)
        draw.line([(cx + 2, body_cy - 6), (cx + 4 + robe_sway, base_y)],
                  fill=THORN_ROBE_DARK, width=1)
        draw.line([(cx + 4, body_cy), (cx + 6, body_cy + 6)],
                  fill=THORN_VINE, width=1)
        draw.point((cx + 5, body_cy + 2), fill=THORN_THORN)

    # --- Arms ---
    if direction == DOWN:
        for side in [-1, 1]:
            ax = cx + side * 16
            draw.rectangle([ax - 3, body_cy - 4, ax + 3, body_cy + 4],
                           fill=THORN_ROBE, outline=OUTLINE)
            draw.rectangle([ax - 3, body_cy + 2, ax + 3, body_cy + 6],
                           fill=THORN_SKIN, outline=OUTLINE)
            # Vine wrapping arm
            draw.line([(ax - 2, body_cy - 2), (ax + 2, body_cy + 2)],
                      fill=THORN_VINE, width=1)
    elif direction == UP:
        for side in [-1, 1]:
            ax = cx + side * 16
            draw.rectangle([ax - 3, body_cy - 4, ax + 3, body_cy + 4],
                           fill=THORN_ROBE, outline=OUTLINE)
    elif direction == LEFT:
        ax = cx - 12
        draw.rectangle([ax - 3, body_cy - 3, ax + 3, body_cy + 5],
                       fill=THORN_ROBE, outline=OUTLINE)
        draw.rectangle([ax - 3, body_cy + 2, ax + 3, body_cy + 6],
                       fill=THORN_SKIN, outline=OUTLINE)
    else:  # RIGHT
        ax = cx + 12
        draw.rectangle([ax - 3, body_cy - 3, ax + 3, body_cy + 5],
                       fill=THORN_ROBE, outline=OUTLINE)
        draw.rectangle([ax - 3, body_cy + 2, ax + 3, body_cy + 6],
                       fill=THORN_SKIN, outline=OUTLINE)

    # --- Head with leaf crown ---
    if direction == DOWN:
        ellipse(draw, cx, head_cy, 12, 10, THORN_ROBE)
        ellipse(draw, cx, head_cy - 2, 10, 8, THORN_ROBE_LIGHT, outline=None)
        ellipse(draw, cx, head_cy + 2, 8, 6, THORN_SKIN)
        ellipse(draw, cx + 2, head_cy + 4, 5, 4, THORN_SKIN_DARK, outline=None)
        draw.rectangle([cx - 5, head_cy + 1, cx - 2, head_cy + 4], fill=(60, 120, 40))
        draw.rectangle([cx + 2, head_cy + 1, cx + 5, head_cy + 4], fill=(60, 120, 40))
        draw.point((cx, head_cy + 6), fill=THORN_SKIN_DARK)
    elif direction == UP:
        ellipse(draw, cx, head_cy, 12, 10, THORN_ROBE)
        ellipse(draw, cx, head_cy, 9, 7, THORN_ROBE_DARK, outline=None)
    elif direction == LEFT:
        ellipse(draw, cx - 2, head_cy, 11, 10, THORN_ROBE)
        ellipse(draw, cx - 4, head_cy + 2, 6, 5, THORN_SKIN)
        draw.rectangle([cx - 8, head_cy + 1, cx - 5, head_cy + 4], fill=(60, 120, 40))
    else:  # RIGHT
        ellipse(draw, cx + 2, head_cy, 11, 10, THORN_ROBE)
        ellipse(draw, cx + 4, head_cy + 2, 6, 5, THORN_SKIN)
        draw.rectangle([cx + 5, head_cy + 1, cx + 8, head_cy + 4], fill=(60, 120, 40))

    # Leaf crown
    for dx in [-8, -4, 0, 4, 8]:
        lx = cx + dx if direction in (DOWN, UP) else cx + dx + (-2 if direction == LEFT else 2)
        ly = head_cy - 8
        draw.polygon([(lx - 2, ly + 2), (lx, ly - 4 - vine_grow), (lx + 2, ly + 2)],
                     fill=THORN_LEAF, outline=None)
        draw.point((lx, ly - 3 - vine_grow), fill=THORN_LEAF_DARK)

    # --- Leaf particles ---
    leaf_positions = [
        (cx - 16, body_cy - 6), (cx + 14, body_cy + 6),
        (cx - 10, body_cy + 12), (cx + 18, body_cy - 2),
    ]
    for i, (lx, ly) in enumerate(leaf_positions):
        if (i + frame) % 2 != 0:
            continue
        draw.polygon([(lx, ly - 2), (lx - 2, ly), (lx, ly + 2), (lx + 2, ly)],
                     fill=THORN_LEAF, outline=None)


# ===================================================================
# CLOUDRUNNER (ID 21) -- White/sky-blue flowing robes, cloud puff
#                         effects, light/airy build
# ===================================================================

def draw_cloudrunner(draw, ox, oy, direction, frame):
    bob = [0, -2, 0, -1][frame]
    robe_sway = [-2, 0, 2, 0][frame]
    puff_phase = frame * (math.pi / 2)

    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 20
    head_cy = body_cy - 18

    # --- Cloud puff at feet (drawn first, behind everything) ---
    puff_y = base_y + 2
    ellipse(draw, cx - 6, puff_y, 8, 4, CLOUD_PUFF, outline=None)
    ellipse(draw, cx + 6, puff_y, 8, 4, CLOUD_PUFF, outline=None)
    ellipse(draw, cx, puff_y - 1, 10, 5, CLOUD_PUFF, outline=None)
    ellipse(draw, cx - 2, puff_y - 2, 6, 3, CLOUD_ROBE_LIGHT, outline=None)

    # --- Robe body (flowing, airy) ---
    if direction == DOWN:
        draw.polygon([
            (cx - 12, body_cy - 10),
            (cx + 12, body_cy - 10),
            (cx + 16 + robe_sway, base_y + 2),
            (cx - 16 + robe_sway, base_y + 2),
        ], fill=CLOUD_ROBE, outline=OUTLINE)
        draw.line([(cx - 3, body_cy - 6), (cx - 5 + robe_sway, base_y)],
                  fill=CLOUD_ROBE_DARK, width=1)
        draw.line([(cx + 3, body_cy - 6), (cx + 5 + robe_sway, base_y)],
                  fill=CLOUD_ROBE_DARK, width=1)
        draw.line([(cx - 8, body_cy - 4), (cx - 12 + robe_sway, base_y)],
                  fill=CLOUD_ROBE_LIGHT, width=1)
    elif direction == UP:
        draw.polygon([
            (cx - 12, body_cy - 10),
            (cx + 12, body_cy - 10),
            (cx + 16 + robe_sway, base_y + 2),
            (cx - 16 + robe_sway, base_y + 2),
        ], fill=CLOUD_ROBE, outline=OUTLINE)
        draw.line([(cx, body_cy - 6), (cx + robe_sway, base_y)],
                  fill=CLOUD_ROBE_DARK, width=1)
    elif direction == LEFT:
        draw.polygon([
            (cx - 10, body_cy - 10),
            (cx + 6, body_cy - 10),
            (cx + 10 + robe_sway, base_y + 2),
            (cx - 14 + robe_sway, base_y + 2),
        ], fill=CLOUD_ROBE, outline=OUTLINE)
        draw.line([(cx - 2, body_cy - 6), (cx - 4 + robe_sway, base_y)],
                  fill=CLOUD_ROBE_DARK, width=1)
    else:  # RIGHT
        draw.polygon([
            (cx - 6, body_cy - 10),
            (cx + 10, body_cy - 10),
            (cx + 14 + robe_sway, base_y + 2),
            (cx - 10 + robe_sway, base_y + 2),
        ], fill=CLOUD_ROBE, outline=OUTLINE)
        draw.line([(cx + 2, body_cy - 6), (cx + 4 + robe_sway, base_y)],
                  fill=CLOUD_ROBE_DARK, width=1)

    # --- Arms (slender) ---
    if direction == DOWN:
        for side in [-1, 1]:
            ax = cx + side * 14
            draw.rectangle([ax - 3, body_cy - 3, ax + 3, body_cy + 4],
                           fill=CLOUD_ROBE, outline=OUTLINE)
            draw.rectangle([ax - 2, body_cy + 2, ax + 2, body_cy + 5],
                           fill=CLOUD_SKIN, outline=OUTLINE)
    elif direction == UP:
        for side in [-1, 1]:
            ax = cx + side * 14
            draw.rectangle([ax - 3, body_cy - 3, ax + 3, body_cy + 4],
                           fill=CLOUD_ROBE, outline=OUTLINE)
    elif direction == LEFT:
        ax = cx - 10
        draw.rectangle([ax - 3, body_cy - 2, ax + 3, body_cy + 5],
                       fill=CLOUD_ROBE, outline=OUTLINE)
        draw.rectangle([ax - 2, body_cy + 2, ax + 2, body_cy + 5],
                       fill=CLOUD_SKIN, outline=OUTLINE)
    else:  # RIGHT
        ax = cx + 10
        draw.rectangle([ax - 3, body_cy - 2, ax + 3, body_cy + 5],
                       fill=CLOUD_ROBE, outline=OUTLINE)
        draw.rectangle([ax - 2, body_cy + 2, ax + 2, body_cy + 5],
                       fill=CLOUD_SKIN, outline=OUTLINE)

    # --- Head (light, airy) ---
    if direction == DOWN:
        ellipse(draw, cx, head_cy, 11, 9, CLOUD_ROBE_LIGHT)
        ellipse(draw, cx, head_cy - 2, 9, 7, CLOUD_ROBE_LIGHT, outline=None)
        ellipse(draw, cx, head_cy + 2, 7, 5, CLOUD_SKIN)
        ellipse(draw, cx + 1, head_cy + 4, 5, 3, CLOUD_SKIN_DARK, outline=None)
        draw.rectangle([cx - 4, head_cy + 1, cx - 2, head_cy + 3], fill=CLOUD_EYE)
        draw.rectangle([cx + 2, head_cy + 1, cx + 4, head_cy + 3], fill=CLOUD_EYE)
        draw.point((cx, head_cy + 5), fill=CLOUD_SKIN_DARK)
    elif direction == UP:
        ellipse(draw, cx, head_cy, 11, 9, CLOUD_ROBE_LIGHT)
        ellipse(draw, cx, head_cy, 8, 6, CLOUD_ROBE_DARK, outline=None)
    elif direction == LEFT:
        ellipse(draw, cx - 2, head_cy, 10, 9, CLOUD_ROBE_LIGHT)
        ellipse(draw, cx - 4, head_cy + 2, 5, 4, CLOUD_SKIN)
        draw.rectangle([cx - 7, head_cy + 1, cx - 5, head_cy + 3], fill=CLOUD_EYE)
    else:  # RIGHT
        ellipse(draw, cx + 2, head_cy, 10, 9, CLOUD_ROBE_LIGHT)
        ellipse(draw, cx + 4, head_cy + 2, 5, 4, CLOUD_SKIN)
        draw.rectangle([cx + 5, head_cy + 1, cx + 7, head_cy + 3], fill=CLOUD_EYE)

    # --- Cloud puff effects orbiting ---
    for i in range(4):
        angle = puff_phase + i * (math.pi / 2)
        px = int(cx + math.cos(angle) * 18)
        py = int(body_cy + math.sin(angle) * 12)
        ellipse(draw, px, py, 4, 3, CLOUD_PUFF, outline=None)
        ellipse(draw, px - 1, py - 1, 2, 2, CLOUD_ROBE_LIGHT, outline=None)

    # --- Wispy hair / aura on head ---
    for dx in [-6, -3, 0, 3, 6]:
        hx = cx + dx if direction in (DOWN, UP) else cx + dx + (-2 if direction == LEFT else 2)
        hy = head_cy - 8
        draw.line([(hx, hy), (hx + robe_sway, hy - 5)],
                  fill=CLOUD_PUFF, width=1)


# ===================================================================
# INFERNO (ID 22) -- Body made of fire, orange/red gradient,
#                     flame edges, ember core
# ===================================================================

def draw_inferno(draw, ox, oy, direction, frame):
    bob = [0, -2, 0, -1][frame]
    flame_flicker = [0, 2, -1, 1][frame]
    flame_phase = frame * (math.pi / 2)

    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 20
    head_cy = body_cy - 18

    # --- Fire legs (flickering) ---
    if direction in (DOWN, UP):
        for side in [-1, 1]:
            lx = cx + side * 6
            # Flame leg shape
            draw.polygon([
                (lx - 4, body_cy + 8),
                (lx + 4, body_cy + 8),
                (lx + 3, base_y + 2),
                (lx - 3, base_y + 2),
            ], fill=INFERNO_BODY, outline=None)
            draw.polygon([
                (lx - 2, body_cy + 10),
                (lx + 2, body_cy + 10),
                (lx + 1, base_y),
                (lx - 1, base_y),
            ], fill=INFERNO_CORE, outline=None)
            # Flame tips at feet
            draw.polygon([(lx - 3, base_y + 2),
                          (lx, base_y + 6 + flame_flicker),
                          (lx + 3, base_y + 2)],
                         fill=INFERNO_FLAME, outline=None)
    elif direction == LEFT:
        lx = cx - 3
        draw.polygon([(lx - 4, body_cy + 8), (lx + 4, body_cy + 8),
                      (lx + 3, base_y + 2), (lx - 3, base_y + 2)],
                     fill=INFERNO_BODY, outline=None)
        draw.polygon([(lx - 2, base_y + 2), (lx, base_y + 6 + flame_flicker),
                      (lx + 2, base_y + 2)], fill=INFERNO_FLAME, outline=None)
    else:  # RIGHT
        lx = cx + 3
        draw.polygon([(lx - 4, body_cy + 8), (lx + 4, body_cy + 8),
                      (lx + 3, base_y + 2), (lx - 3, base_y + 2)],
                     fill=INFERNO_BODY, outline=None)
        draw.polygon([(lx - 2, base_y + 2), (lx, base_y + 6 + flame_flicker),
                      (lx + 2, base_y + 2)], fill=INFERNO_FLAME, outline=None)

    # --- Fire body (gradient) ---
    if direction == DOWN:
        ellipse(draw, cx, body_cy, 14, 12, INFERNO_BODY)
        ellipse(draw, cx, body_cy, 10, 9, INFERNO_BODY_LIGHT, outline=None)
        ellipse(draw, cx, body_cy, 6, 5, INFERNO_CORE, outline=None)
        ellipse(draw, cx, body_cy, 3, 2, INFERNO_CORE_BRIGHT, outline=None)
        # Flame edge tendrils
        for angle_off in range(0, 360, 45):
            a = math.radians(angle_off) + flame_phase * 0.3
            fx = int(cx + math.cos(a) * 14)
            fy = int(body_cy + math.sin(a) * 12)
            draw.polygon([(fx - 2, fy), (fx, fy - 4 + flame_flicker), (fx + 2, fy)],
                         fill=INFERNO_FLAME, outline=None)
    elif direction == UP:
        ellipse(draw, cx, body_cy, 14, 12, INFERNO_BODY)
        ellipse(draw, cx, body_cy, 10, 9, INFERNO_BODY_DARK, outline=None)
        ellipse(draw, cx, body_cy, 6, 5, INFERNO_CORE, outline=None)
    elif direction == LEFT:
        ellipse(draw, cx - 2, body_cy, 12, 12, INFERNO_BODY)
        ellipse(draw, cx - 2, body_cy, 8, 8, INFERNO_BODY_LIGHT, outline=None)
        ellipse(draw, cx - 2, body_cy, 4, 4, INFERNO_CORE, outline=None)
    else:  # RIGHT
        ellipse(draw, cx + 2, body_cy, 12, 12, INFERNO_BODY)
        ellipse(draw, cx + 2, body_cy, 8, 8, INFERNO_BODY_LIGHT, outline=None)
        ellipse(draw, cx + 2, body_cy, 4, 4, INFERNO_CORE, outline=None)

    # --- Fire arms ---
    if direction == DOWN:
        for side in [-1, 1]:
            ax = cx + side * 14
            draw.rectangle([ax - 3, body_cy - 4, ax + 3, body_cy + 4],
                           fill=INFERNO_BODY, outline=None)
            draw.rectangle([ax - 2, body_cy - 3, ax + 2, body_cy + 3],
                           fill=INFERNO_BODY_LIGHT, outline=None)
            # Flame hand
            draw.polygon([(ax - 3, body_cy + 4), (ax, body_cy + 10 + flame_flicker),
                          (ax + 3, body_cy + 4)],
                         fill=INFERNO_FLAME, outline=None)
            draw.polygon([(ax - 1, body_cy + 4), (ax, body_cy + 8 + flame_flicker),
                          (ax + 1, body_cy + 4)],
                         fill=INFERNO_FLAME_TIP, outline=None)
    elif direction == UP:
        for side in [-1, 1]:
            ax = cx + side * 14
            draw.rectangle([ax - 3, body_cy - 4, ax + 3, body_cy + 4],
                           fill=INFERNO_BODY, outline=None)
    elif direction == LEFT:
        ax = cx - 12
        draw.rectangle([ax - 3, body_cy - 3, ax + 3, body_cy + 5],
                       fill=INFERNO_BODY, outline=None)
        draw.polygon([(ax - 3, body_cy + 5), (ax, body_cy + 10 + flame_flicker),
                      (ax + 3, body_cy + 5)], fill=INFERNO_FLAME, outline=None)
    else:  # RIGHT
        ax = cx + 12
        draw.rectangle([ax - 3, body_cy - 3, ax + 3, body_cy + 5],
                       fill=INFERNO_BODY, outline=None)
        draw.polygon([(ax - 3, body_cy + 5), (ax, body_cy + 10 + flame_flicker),
                      (ax + 3, body_cy + 5)], fill=INFERNO_FLAME, outline=None)

    # --- Fire head ---
    if direction == DOWN:
        ellipse(draw, cx, head_cy, 10, 9, INFERNO_BODY)
        ellipse(draw, cx, head_cy, 7, 6, INFERNO_BODY_LIGHT, outline=None)
        ellipse(draw, cx, head_cy, 4, 3, INFERNO_CORE, outline=None)
        # Eyes (white-hot)
        draw.rectangle([cx - 5, head_cy, cx - 2, head_cy + 3], fill=INFERNO_EYE)
        draw.rectangle([cx + 2, head_cy, cx + 5, head_cy + 3], fill=INFERNO_EYE)
        # Flame crown
        for dx in [-6, -3, 0, 3, 6]:
            fh = 8 + (abs(dx) % 4) + flame_flicker
            draw.polygon([(cx + dx - 2, head_cy - 6), (cx + dx, head_cy - 6 - fh),
                          (cx + dx + 2, head_cy - 6)],
                         fill=INFERNO_FLAME, outline=None)
            draw.polygon([(cx + dx - 1, head_cy - 6), (cx + dx, head_cy - 6 - fh + 2),
                          (cx + dx + 1, head_cy - 6)],
                         fill=INFERNO_FLAME_TIP, outline=None)
    elif direction == UP:
        ellipse(draw, cx, head_cy, 10, 9, INFERNO_BODY)
        ellipse(draw, cx, head_cy, 7, 6, INFERNO_BODY_DARK, outline=None)
        for dx in [-6, -3, 0, 3, 6]:
            fh = 7 + (abs(dx) % 3) + flame_flicker
            draw.polygon([(cx + dx - 2, head_cy - 6), (cx + dx, head_cy - 6 - fh),
                          (cx + dx + 2, head_cy - 6)],
                         fill=INFERNO_FLAME, outline=None)
    elif direction == LEFT:
        ellipse(draw, cx - 2, head_cy, 9, 9, INFERNO_BODY)
        ellipse(draw, cx - 2, head_cy, 6, 6, INFERNO_BODY_LIGHT, outline=None)
        draw.rectangle([cx - 7, head_cy, cx - 4, head_cy + 3], fill=INFERNO_EYE)
        for dx in [-6, -2, 2, 6]:
            fh = 6 + flame_flicker
            draw.polygon([(cx + dx - 3, head_cy - 6), (cx + dx - 1, head_cy - 6 - fh),
                          (cx + dx + 1, head_cy - 6)],
                         fill=INFERNO_FLAME, outline=None)
    else:  # RIGHT
        ellipse(draw, cx + 2, head_cy, 9, 9, INFERNO_BODY)
        ellipse(draw, cx + 2, head_cy, 6, 6, INFERNO_BODY_LIGHT, outline=None)
        draw.rectangle([cx + 4, head_cy, cx + 7, head_cy + 3], fill=INFERNO_EYE)
        for dx in [-6, -2, 2, 6]:
            fh = 6 + flame_flicker
            draw.polygon([(cx + dx - 1, head_cy - 6), (cx + dx + 1, head_cy - 6 - fh),
                          (cx + dx + 3, head_cy - 6)],
                         fill=INFERNO_FLAME, outline=None)

    # --- Ember spark particles ---
    for i in range(6):
        angle = flame_phase + i * (math.pi / 3)
        er = 16 + (i % 3) * 4
        ex = int(cx + math.cos(angle) * er)
        ey = int(body_cy - 6 + math.sin(angle) * (er * 0.5))
        draw.point((ex, ey), fill=INFERNO_SPARK)
        draw.point((ex + 1, ey), fill=INFERNO_CORE_BRIGHT)


# ===================================================================
# GLACIER (ID 23) -- Bulky ice/blue body, crystalline armor plates,
#                     frost aura
# ===================================================================

def draw_glacier(draw, ox, oy, direction, frame):
    bob = [0, -2, 0, -1][frame]
    leg_spread = [-4, 0, 4, 0][frame]
    frost_pulse = [0, 1, 0, -1][frame]

    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 20
    head_cy = body_cy - 20

    # --- Bulky legs ---
    if direction in (DOWN, UP):
        for side in [-1, 1]:
            ls = leg_spread if side == -1 else -leg_spread
            lx = cx + side * 7 + ls
            draw.rectangle([lx - 5, body_cy + 10, lx + 5, base_y],
                           fill=GLACIER_BODY, outline=OUTLINE)
            draw.rectangle([lx - 5, body_cy + 10, lx - 3, base_y - 6],
                           fill=GLACIER_BODY_LIGHT, outline=None)
            # Ice plate on legs
            draw.rectangle([lx - 4, body_cy + 12, lx + 4, body_cy + 18],
                           fill=GLACIER_PLATE, outline=None)
            draw.point((lx, body_cy + 14), fill=GLACIER_PLATE_LIGHT)
            # Boots
            draw.rectangle([lx - 5, base_y - 6, lx + 5, base_y],
                           fill=GLACIER_BODY_DARK, outline=OUTLINE)
    elif direction == LEFT:
        for i, offset in enumerate([leg_spread, -leg_spread]):
            lx = cx - 3 + offset
            draw.rectangle([lx - 5, body_cy + 10, lx + 5, base_y],
                           fill=GLACIER_BODY, outline=OUTLINE)
            draw.rectangle([lx - 5, base_y - 6, lx + 5, base_y],
                           fill=GLACIER_BODY_DARK, outline=OUTLINE)
    else:  # RIGHT
        for i, offset in enumerate([leg_spread, -leg_spread]):
            lx = cx + 3 + offset
            draw.rectangle([lx - 5, body_cy + 10, lx + 5, base_y],
                           fill=GLACIER_BODY, outline=OUTLINE)
            draw.rectangle([lx - 5, base_y - 6, lx + 5, base_y],
                           fill=GLACIER_BODY_DARK, outline=OUTLINE)

    # --- Bulky body with ice plates ---
    if direction == DOWN:
        ellipse(draw, cx, body_cy, 16, 14, GLACIER_BODY)
        ellipse(draw, cx + 4, body_cy + 2, 12, 10, GLACIER_BODY_DARK, outline=None)
        ellipse(draw, cx - 2, body_cy - 2, 10, 8, GLACIER_BODY_LIGHT, outline=None)
        # Crystal plate armor
        draw.polygon([(cx - 8, body_cy - 6), (cx, body_cy - 10),
                      (cx + 8, body_cy - 6), (cx + 6, body_cy + 4),
                      (cx - 6, body_cy + 4)],
                     fill=GLACIER_PLATE, outline=OUTLINE)
        draw.polygon([(cx - 4, body_cy - 4), (cx, body_cy - 8),
                      (cx + 4, body_cy - 4)],
                     fill=GLACIER_PLATE_LIGHT, outline=None)
        draw.point((cx, body_cy - 2), fill=GLACIER_FROST)
        # Belt
        draw.rectangle([cx - 16, body_cy + 8, cx + 16, body_cy + 14],
                       fill=GLACIER_BODY_DARK, outline=OUTLINE)
    elif direction == UP:
        ellipse(draw, cx, body_cy, 16, 14, GLACIER_BODY)
        ellipse(draw, cx + 4, body_cy + 2, 12, 10, GLACIER_BODY_DARK, outline=None)
        draw.rectangle([cx - 16, body_cy + 8, cx + 16, body_cy + 14],
                       fill=GLACIER_BODY_DARK, outline=OUTLINE)
    elif direction == LEFT:
        ellipse(draw, cx - 2, body_cy, 14, 14, GLACIER_BODY)
        ellipse(draw, cx + 2, body_cy + 2, 10, 10, GLACIER_BODY_DARK, outline=None)
        ellipse(draw, cx - 4, body_cy - 2, 8, 8, GLACIER_BODY_LIGHT, outline=None)
        draw.rectangle([cx - 16, body_cy + 8, cx + 12, body_cy + 14],
                       fill=GLACIER_BODY_DARK, outline=OUTLINE)
    else:  # RIGHT
        ellipse(draw, cx + 2, body_cy, 14, 14, GLACIER_BODY)
        ellipse(draw, cx + 6, body_cy + 2, 10, 10, GLACIER_BODY_DARK, outline=None)
        ellipse(draw, cx, body_cy - 2, 8, 8, GLACIER_BODY_LIGHT, outline=None)
        draw.rectangle([cx - 12, body_cy + 8, cx + 16, body_cy + 14],
                       fill=GLACIER_BODY_DARK, outline=OUTLINE)

    # --- Arms with ice plate shoulders ---
    if direction == DOWN:
        for side in [-1, 1]:
            ax = cx + side * 18
            draw.rectangle([ax - 4, body_cy - 4, ax + 4, body_cy + 6],
                           fill=GLACIER_BODY, outline=OUTLINE)
            # Ice shoulder plate
            ellipse(draw, ax, body_cy - 6, 7, 5, GLACIER_PLATE)
            ellipse(draw, ax - 1, body_cy - 8, 4, 3, GLACIER_PLATE_LIGHT, outline=None)
            # Ice crystal spike from shoulder
            draw.polygon([(ax + side * 2, body_cy - 10),
                          (ax + side * 3, body_cy - 18 + frost_pulse),
                          (ax + side * 5, body_cy - 10)],
                         fill=GLACIER_FROST, outline=OUTLINE)
    elif direction == UP:
        for side in [-1, 1]:
            ax = cx + side * 18
            draw.rectangle([ax - 4, body_cy - 4, ax + 4, body_cy + 6],
                           fill=GLACIER_BODY, outline=OUTLINE)
            ellipse(draw, ax, body_cy - 6, 7, 5, GLACIER_PLATE)
    elif direction == LEFT:
        ax = cx - 14
        draw.rectangle([ax - 4, body_cy - 3, ax + 4, body_cy + 6],
                       fill=GLACIER_BODY, outline=OUTLINE)
        ellipse(draw, ax, body_cy - 5, 7, 5, GLACIER_PLATE)
        draw.polygon([(ax - 4, body_cy - 9), (ax - 5, body_cy - 17 + frost_pulse),
                      (ax - 2, body_cy - 9)],
                     fill=GLACIER_FROST, outline=OUTLINE)
    else:  # RIGHT
        ax = cx + 14
        draw.rectangle([ax - 4, body_cy - 3, ax + 4, body_cy + 6],
                       fill=GLACIER_BODY, outline=OUTLINE)
        ellipse(draw, ax, body_cy - 5, 7, 5, GLACIER_PLATE)
        draw.polygon([(ax + 2, body_cy - 9), (ax + 5, body_cy - 17 + frost_pulse),
                      (ax + 4, body_cy - 9)],
                     fill=GLACIER_FROST, outline=OUTLINE)

    # --- Head (icy helm) ---
    if direction == DOWN:
        ellipse(draw, cx, head_cy, 14, 12, GLACIER_BODY)
        ellipse(draw, cx + 2, head_cy + 2, 10, 8, GLACIER_BODY_DARK, outline=None)
        ellipse(draw, cx - 2, head_cy - 2, 8, 6, GLACIER_BODY_LIGHT, outline=None)
        # Ice visor
        draw.rectangle([cx - 8, head_cy, cx + 8, head_cy + 4],
                       fill=GLACIER_PLATE_DARK, outline=None)
        draw.rectangle([cx - 6, head_cy + 1, cx + 6, head_cy + 3],
                       fill=GLACIER_EYE, outline=None)
        draw.rectangle([cx - 3, head_cy + 1, cx + 3, head_cy + 3],
                       fill=_brighten(GLACIER_EYE, 1.2), outline=None)
    elif direction == UP:
        ellipse(draw, cx, head_cy, 14, 12, GLACIER_BODY)
        ellipse(draw, cx + 2, head_cy + 2, 10, 8, GLACIER_BODY_DARK, outline=None)
    elif direction == LEFT:
        ellipse(draw, cx - 2, head_cy, 13, 12, GLACIER_BODY)
        ellipse(draw, cx + 2, head_cy + 2, 9, 8, GLACIER_BODY_DARK, outline=None)
        draw.rectangle([cx - 10, head_cy, cx - 2, head_cy + 4],
                       fill=GLACIER_PLATE_DARK, outline=None)
        draw.rectangle([cx - 9, head_cy + 1, cx - 3, head_cy + 3],
                       fill=GLACIER_EYE, outline=None)
    else:  # RIGHT
        ellipse(draw, cx + 2, head_cy, 13, 12, GLACIER_BODY)
        ellipse(draw, cx + 6, head_cy + 2, 9, 8, GLACIER_BODY_DARK, outline=None)
        draw.rectangle([cx + 2, head_cy, cx + 10, head_cy + 4],
                       fill=GLACIER_PLATE_DARK, outline=None)
        draw.rectangle([cx + 3, head_cy + 1, cx + 9, head_cy + 3],
                       fill=GLACIER_EYE, outline=None)

    # --- Frost aura particles ---
    for i in range(6):
        angle = frame * (math.pi / 2) + i * (math.pi / 3)
        fr = 22
        fx = int(cx + math.cos(angle) * fr)
        fy = int(body_cy + math.sin(angle) * (fr * 0.6))
        draw.point((fx, fy), fill=GLACIER_FROST)
        draw.point((fx + 1, fy), fill=GLACIER_FROST)


# ===================================================================
# MUDSLINGER (ID 24) -- Brown/earth tones, mud splash effects,
#                        hunched posture, mud drips
# ===================================================================

def draw_mudslinger(draw, ox, oy, direction, frame):
    bob = [0, -2, 0, -1][frame]
    leg_spread = [-3, 0, 3, 0][frame]
    drip_extend = [0, 1, 2, 1][frame]

    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 18  # slightly hunched
    head_cy = body_cy - 16

    # --- Legs (stocky, muddy) ---
    if direction in (DOWN, UP):
        for side in [-1, 1]:
            ls = leg_spread if side == -1 else -leg_spread
            lx = cx + side * 6 + ls
            draw.rectangle([lx - 4, body_cy + 8, lx + 4, base_y],
                           fill=MUD_BODY, outline=OUTLINE)
            draw.rectangle([lx - 4, body_cy + 8, lx - 2, base_y - 4],
                           fill=MUD_BODY_LIGHT, outline=None)
            # Mud-caked boots
            draw.rectangle([lx - 4, base_y - 5, lx + 4, base_y],
                           fill=MUD_BODY_DARK, outline=OUTLINE)
            # Mud drip on boot
            draw.point((lx - 1, base_y + drip_extend), fill=MUD_DRIP)
    elif direction == LEFT:
        for offset in [leg_spread, -leg_spread]:
            lx = cx - 3 + offset
            draw.rectangle([lx - 4, body_cy + 8, lx + 4, base_y],
                           fill=MUD_BODY, outline=OUTLINE)
            draw.rectangle([lx - 4, base_y - 5, lx + 4, base_y],
                           fill=MUD_BODY_DARK, outline=OUTLINE)
    else:  # RIGHT
        for offset in [leg_spread, -leg_spread]:
            lx = cx + 3 + offset
            draw.rectangle([lx - 4, body_cy + 8, lx + 4, base_y],
                           fill=MUD_BODY, outline=OUTLINE)
            draw.rectangle([lx - 4, base_y - 5, lx + 4, base_y],
                           fill=MUD_BODY_DARK, outline=OUTLINE)

    # --- Hunched body ---
    if direction == DOWN:
        ellipse(draw, cx, body_cy, 14, 12, MUD_BODY)
        ellipse(draw, cx + 3, body_cy + 2, 10, 8, MUD_BODY_DARK, outline=None)
        ellipse(draw, cx - 2, body_cy - 2, 8, 6, MUD_BODY_LIGHT, outline=None)
        # Mud texture blobs
        ellipse(draw, cx - 6, body_cy + 4, 3, 2, MUD_SPLASH, outline=None)
        ellipse(draw, cx + 8, body_cy - 2, 3, 2, MUD_SPLASH, outline=None)
        # Cloth wrap
        draw.rectangle([cx - 8, body_cy + 6, cx + 8, body_cy + 10],
                       fill=MUD_CLOTH, outline=OUTLINE)
    elif direction == UP:
        ellipse(draw, cx, body_cy, 14, 12, MUD_BODY)
        ellipse(draw, cx + 3, body_cy + 2, 10, 8, MUD_BODY_DARK, outline=None)
        draw.rectangle([cx - 8, body_cy + 6, cx + 8, body_cy + 10],
                       fill=MUD_CLOTH, outline=OUTLINE)
    elif direction == LEFT:
        ellipse(draw, cx - 2, body_cy, 12, 12, MUD_BODY)
        ellipse(draw, cx + 2, body_cy + 2, 8, 8, MUD_BODY_DARK, outline=None)
        ellipse(draw, cx - 4, body_cy - 2, 6, 6, MUD_BODY_LIGHT, outline=None)
        draw.rectangle([cx - 8, body_cy + 6, cx + 6, body_cy + 10],
                       fill=MUD_CLOTH, outline=OUTLINE)
    else:  # RIGHT
        ellipse(draw, cx + 2, body_cy, 12, 12, MUD_BODY)
        ellipse(draw, cx + 6, body_cy + 2, 8, 8, MUD_BODY_DARK, outline=None)
        ellipse(draw, cx, body_cy - 2, 6, 6, MUD_BODY_LIGHT, outline=None)
        draw.rectangle([cx - 6, body_cy + 6, cx + 8, body_cy + 10],
                       fill=MUD_CLOTH, outline=OUTLINE)

    # --- Arms (thick, muddy) ---
    if direction == DOWN:
        for side in [-1, 1]:
            ax = cx + side * 14
            draw.rectangle([ax - 4, body_cy - 2, ax + 4, body_cy + 6],
                           fill=MUD_BODY, outline=OUTLINE)
            # Mud glob hands
            ellipse(draw, ax, body_cy + 8, 5, 4, MUD_SPLASH)
            ellipse(draw, ax, body_cy + 7, 3, 2, MUD_SPLASH_LIGHT, outline=None)
            # Drip from hand
            draw.line([(ax, body_cy + 12), (ax, body_cy + 14 + drip_extend)],
                      fill=MUD_DRIP, width=1)
    elif direction == UP:
        for side in [-1, 1]:
            ax = cx + side * 14
            draw.rectangle([ax - 4, body_cy - 2, ax + 4, body_cy + 6],
                           fill=MUD_BODY, outline=OUTLINE)
    elif direction == LEFT:
        ax = cx - 12
        draw.rectangle([ax - 4, body_cy - 1, ax + 4, body_cy + 6],
                       fill=MUD_BODY, outline=OUTLINE)
        ellipse(draw, ax, body_cy + 8, 5, 4, MUD_SPLASH)
        draw.line([(ax, body_cy + 12), (ax, body_cy + 14 + drip_extend)],
                  fill=MUD_DRIP, width=1)
    else:  # RIGHT
        ax = cx + 12
        draw.rectangle([ax - 4, body_cy - 1, ax + 4, body_cy + 6],
                       fill=MUD_BODY, outline=OUTLINE)
        ellipse(draw, ax, body_cy + 8, 5, 4, MUD_SPLASH)
        draw.line([(ax, body_cy + 12), (ax, body_cy + 14 + drip_extend)],
                  fill=MUD_DRIP, width=1)

    # --- Head (round, muddy) ---
    if direction == DOWN:
        ellipse(draw, cx, head_cy, 10, 9, MUD_BODY)
        ellipse(draw, cx + 2, head_cy + 2, 7, 5, MUD_BODY_DARK, outline=None)
        ellipse(draw, cx - 2, head_cy - 2, 6, 4, MUD_BODY_LIGHT, outline=None)
        # Mud dripping from top
        draw.line([(cx - 4, head_cy - 6), (cx - 4, head_cy - 3)],
                  fill=MUD_DRIP, width=1)
        draw.line([(cx + 6, head_cy - 4), (cx + 6, head_cy)],
                  fill=MUD_DRIP, width=1)
        # Eyes (glowing yellow)
        draw.rectangle([cx - 5, head_cy, cx - 2, head_cy + 3], fill=MUD_EYE)
        draw.rectangle([cx + 2, head_cy, cx + 5, head_cy + 3], fill=MUD_EYE)
        # Wide grinning mouth
        draw.line([(cx - 4, head_cy + 5), (cx + 4, head_cy + 5)],
                  fill=MUD_BODY_DARK, width=1)
        draw.line([(cx - 4, head_cy + 6), (cx + 4, head_cy + 6)],
                  fill=BLACK, width=1)
    elif direction == UP:
        ellipse(draw, cx, head_cy, 10, 9, MUD_BODY)
        ellipse(draw, cx + 2, head_cy + 2, 7, 5, MUD_BODY_DARK, outline=None)
        draw.line([(cx - 6, head_cy - 4), (cx - 6, head_cy - 1)],
                  fill=MUD_DRIP, width=1)
        draw.line([(cx + 4, head_cy - 6), (cx + 4, head_cy - 3)],
                  fill=MUD_DRIP, width=1)
    elif direction == LEFT:
        ellipse(draw, cx - 2, head_cy, 9, 9, MUD_BODY)
        ellipse(draw, cx + 1, head_cy + 2, 6, 5, MUD_BODY_DARK, outline=None)
        draw.rectangle([cx - 7, head_cy, cx - 4, head_cy + 3], fill=MUD_EYE)
        draw.line([(cx - 7, head_cy + 5), (cx - 1, head_cy + 5)],
                  fill=BLACK, width=1)
        draw.line([(cx - 2, head_cy - 6), (cx - 2, head_cy - 3)],
                  fill=MUD_DRIP, width=1)
    else:  # RIGHT
        ellipse(draw, cx + 2, head_cy, 9, 9, MUD_BODY)
        ellipse(draw, cx + 5, head_cy + 2, 6, 5, MUD_BODY_DARK, outline=None)
        draw.rectangle([cx + 4, head_cy, cx + 7, head_cy + 3], fill=MUD_EYE)
        draw.line([(cx + 1, head_cy + 5), (cx + 7, head_cy + 5)],
                  fill=BLACK, width=1)
        draw.line([(cx + 2, head_cy - 6), (cx + 2, head_cy - 3)],
                  fill=MUD_DRIP, width=1)

    # --- Mud splash effects around feet ---
    splash_positions = [
        (cx - 14, base_y), (cx + 12, base_y + 1),
        (cx - 8, base_y + 2), (cx + 16, base_y),
    ]
    for i, (sx, sy) in enumerate(splash_positions):
        if (i + frame) % 2 != 0:
            continue
        ellipse(draw, sx, sy, 3, 2, MUD_SPLASH, outline=None)
        draw.point((sx, sy - 1), fill=MUD_SPLASH_LIGHT)


# ===================================================================
# EMBER (ID 25) -- Small fire elemental, glowing orange body,
#                   spark particles
# ===================================================================

def draw_ember(draw, ox, oy, direction, frame):
    bob = [0, -2, 0, -1][frame]
    flicker = [0, 2, -1, 1][frame]
    spark_phase = frame * (math.pi / 2)

    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 16  # smaller character
    head_cy = body_cy - 14

    # --- Small flame legs ---
    if direction in (DOWN, UP):
        for side in [-1, 1]:
            lx = cx + side * 5
            draw.polygon([(lx - 3, body_cy + 6), (lx + 3, body_cy + 6),
                          (lx + 2, base_y + 2), (lx - 2, base_y + 2)],
                         fill=EMBER_BODY, outline=None)
            draw.polygon([(lx - 1, body_cy + 8), (lx + 1, body_cy + 8),
                          (lx, base_y), (lx, base_y)],
                         fill=EMBER_CORE, outline=None)
            # Flame tip
            draw.polygon([(lx - 2, base_y + 2), (lx, base_y + 4 + flicker),
                          (lx + 2, base_y + 2)],
                         fill=EMBER_BODY_DARK, outline=None)
    elif direction == LEFT:
        lx = cx - 2
        draw.polygon([(lx - 3, body_cy + 6), (lx + 3, body_cy + 6),
                      (lx + 2, base_y + 2), (lx - 2, base_y + 2)],
                     fill=EMBER_BODY, outline=None)
    else:  # RIGHT
        lx = cx + 2
        draw.polygon([(lx - 3, body_cy + 6), (lx + 3, body_cy + 6),
                      (lx + 2, base_y + 2), (lx - 2, base_y + 2)],
                     fill=EMBER_BODY, outline=None)

    # --- Small round body (glowing) ---
    if direction == DOWN:
        ellipse(draw, cx, body_cy, 10, 9, EMBER_BODY)
        ellipse(draw, cx, body_cy, 7, 6, EMBER_BODY_LIGHT, outline=None)
        ellipse(draw, cx, body_cy, 4, 3, EMBER_CORE, outline=None)
        draw.point((cx, body_cy), fill=EMBER_SPARK_BRIGHT)
    elif direction == UP:
        ellipse(draw, cx, body_cy, 10, 9, EMBER_BODY)
        ellipse(draw, cx, body_cy, 7, 6, EMBER_BODY_DARK, outline=None)
        ellipse(draw, cx, body_cy, 4, 3, EMBER_CORE, outline=None)
    elif direction == LEFT:
        ellipse(draw, cx - 1, body_cy, 9, 9, EMBER_BODY)
        ellipse(draw, cx - 1, body_cy, 6, 6, EMBER_BODY_LIGHT, outline=None)
        ellipse(draw, cx - 1, body_cy, 3, 3, EMBER_CORE, outline=None)
    else:  # RIGHT
        ellipse(draw, cx + 1, body_cy, 9, 9, EMBER_BODY)
        ellipse(draw, cx + 1, body_cy, 6, 6, EMBER_BODY_LIGHT, outline=None)
        ellipse(draw, cx + 1, body_cy, 3, 3, EMBER_CORE, outline=None)

    # --- Small arms (flame wisps) ---
    if direction == DOWN:
        for side in [-1, 1]:
            ax = cx + side * 10
            draw.polygon([(ax - 2, body_cy - 2), (ax, body_cy + 6 + flicker),
                          (ax + 2, body_cy - 2)],
                         fill=EMBER_BODY, outline=None)
            draw.polygon([(ax - 1, body_cy - 1), (ax, body_cy + 4 + flicker),
                          (ax + 1, body_cy - 1)],
                         fill=EMBER_BODY_LIGHT, outline=None)
    elif direction == UP:
        for side in [-1, 1]:
            ax = cx + side * 10
            draw.polygon([(ax - 2, body_cy - 2), (ax, body_cy + 4),
                          (ax + 2, body_cy - 2)],
                         fill=EMBER_BODY, outline=None)
    elif direction == LEFT:
        ax = cx - 8
        draw.polygon([(ax - 2, body_cy - 1), (ax, body_cy + 5 + flicker),
                      (ax + 2, body_cy - 1)],
                     fill=EMBER_BODY, outline=None)
    else:  # RIGHT
        ax = cx + 8
        draw.polygon([(ax - 2, body_cy - 1), (ax, body_cy + 5 + flicker),
                      (ax + 2, body_cy - 1)],
                     fill=EMBER_BODY, outline=None)

    # --- Head (round flame) ---
    if direction == DOWN:
        ellipse(draw, cx, head_cy, 8, 7, EMBER_BODY)
        ellipse(draw, cx, head_cy, 5, 4, EMBER_BODY_LIGHT, outline=None)
        ellipse(draw, cx, head_cy, 3, 2, EMBER_CORE, outline=None)
        # Bright eyes
        draw.rectangle([cx - 4, head_cy, cx - 2, head_cy + 2], fill=EMBER_EYE)
        draw.rectangle([cx + 2, head_cy, cx + 4, head_cy + 2], fill=EMBER_EYE)
        # Flame top
        for dx in [-4, -1, 2, 5]:
            fh = 5 + (abs(dx) % 3) + flicker
            draw.polygon([(cx + dx - 1, head_cy - 4),
                          (cx + dx, head_cy - 4 - fh),
                          (cx + dx + 1, head_cy - 4)],
                         fill=EMBER_BODY, outline=None)
    elif direction == UP:
        ellipse(draw, cx, head_cy, 8, 7, EMBER_BODY)
        ellipse(draw, cx, head_cy, 5, 4, EMBER_BODY_DARK, outline=None)
        for dx in [-4, -1, 2, 5]:
            fh = 4 + flicker
            draw.polygon([(cx + dx - 1, head_cy - 4),
                          (cx + dx, head_cy - 4 - fh),
                          (cx + dx + 1, head_cy - 4)],
                         fill=EMBER_BODY, outline=None)
    elif direction == LEFT:
        ellipse(draw, cx - 1, head_cy, 7, 7, EMBER_BODY)
        ellipse(draw, cx - 1, head_cy, 4, 4, EMBER_BODY_LIGHT, outline=None)
        draw.rectangle([cx - 5, head_cy, cx - 3, head_cy + 2], fill=EMBER_EYE)
        for dx in [-4, -1, 2]:
            fh = 4 + flicker
            draw.polygon([(cx + dx - 2, head_cy - 4),
                          (cx + dx, head_cy - 4 - fh),
                          (cx + dx + 1, head_cy - 4)],
                         fill=EMBER_BODY, outline=None)
    else:  # RIGHT
        ellipse(draw, cx + 1, head_cy, 7, 7, EMBER_BODY)
        ellipse(draw, cx + 1, head_cy, 4, 4, EMBER_BODY_LIGHT, outline=None)
        draw.rectangle([cx + 3, head_cy, cx + 5, head_cy + 2], fill=EMBER_EYE)
        for dx in [-2, 1, 4]:
            fh = 4 + flicker
            draw.polygon([(cx + dx - 1, head_cy - 4),
                          (cx + dx, head_cy - 4 - fh),
                          (cx + dx + 2, head_cy - 4)],
                         fill=EMBER_BODY, outline=None)

    # --- Spark particles ---
    for i in range(8):
        angle = spark_phase + i * (math.pi / 4)
        er = 14 + (i % 3) * 3
        ex = int(cx + math.cos(angle) * er)
        ey = int(body_cy - 4 + math.sin(angle) * (er * 0.5))
        draw.point((ex, ey), fill=EMBER_SPARK)
        if i % 2 == 0:
            draw.point((ex + 1, ey), fill=EMBER_SPARK_BRIGHT)


# ===================================================================
# AVALANCHE (ID 26) -- Large icy/gray armored figure, snow/ice
#                       shoulder pads, frost breath
# ===================================================================

def draw_avalanche(draw, ox, oy, direction, frame):
    bob = [0, -2, 0, -1][frame]
    leg_spread = [-4, 0, 4, 0][frame]
    breath_extend = [0, 2, 4, 2][frame]

    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 20
    head_cy = body_cy - 20

    # --- Heavy legs ---
    if direction in (DOWN, UP):
        for side in [-1, 1]:
            ls = leg_spread if side == -1 else -leg_spread
            lx = cx + side * 7 + ls
            draw.rectangle([lx - 5, body_cy + 10, lx + 5, base_y],
                           fill=AVAL_ARMOR, outline=OUTLINE)
            draw.rectangle([lx - 5, body_cy + 10, lx - 3, base_y - 6],
                           fill=AVAL_ARMOR_LIGHT, outline=None)
            # Ice-encrusted boots
            draw.rectangle([lx - 5, base_y - 6, lx + 5, base_y],
                           fill=AVAL_ARMOR_DARK, outline=OUTLINE)
            draw.point((lx - 2, base_y - 4), fill=AVAL_ICE)
            draw.point((lx + 2, base_y - 2), fill=AVAL_ICE)
    elif direction == LEFT:
        for offset in [leg_spread, -leg_spread]:
            lx = cx - 3 + offset
            draw.rectangle([lx - 5, body_cy + 10, lx + 5, base_y],
                           fill=AVAL_ARMOR, outline=OUTLINE)
            draw.rectangle([lx - 5, base_y - 6, lx + 5, base_y],
                           fill=AVAL_ARMOR_DARK, outline=OUTLINE)
    else:  # RIGHT
        for offset in [leg_spread, -leg_spread]:
            lx = cx + 3 + offset
            draw.rectangle([lx - 5, body_cy + 10, lx + 5, base_y],
                           fill=AVAL_ARMOR, outline=OUTLINE)
            draw.rectangle([lx - 5, base_y - 6, lx + 5, base_y],
                           fill=AVAL_ARMOR_DARK, outline=OUTLINE)

    # --- Large body ---
    if direction == DOWN:
        ellipse(draw, cx, body_cy, 16, 14, AVAL_ARMOR)
        ellipse(draw, cx + 4, body_cy + 2, 12, 10, AVAL_ARMOR_DARK, outline=None)
        ellipse(draw, cx - 2, body_cy - 2, 10, 8, AVAL_ARMOR_LIGHT, outline=None)
        # Ice plate chest
        draw.polygon([(cx - 6, body_cy - 6), (cx, body_cy - 10),
                      (cx + 6, body_cy - 6), (cx + 4, body_cy + 2),
                      (cx - 4, body_cy + 2)],
                     fill=AVAL_ICE, outline=OUTLINE)
        draw.polygon([(cx - 3, body_cy - 4), (cx, body_cy - 8),
                      (cx + 3, body_cy - 4)],
                     fill=AVAL_ICE_BRIGHT, outline=None)
        # Belt
        draw.rectangle([cx - 16, body_cy + 8, cx + 16, body_cy + 14],
                       fill=AVAL_ARMOR_DARK, outline=OUTLINE)
    elif direction == UP:
        ellipse(draw, cx, body_cy, 16, 14, AVAL_ARMOR)
        ellipse(draw, cx + 4, body_cy + 2, 12, 10, AVAL_ARMOR_DARK, outline=None)
        draw.rectangle([cx - 16, body_cy + 8, cx + 16, body_cy + 14],
                       fill=AVAL_ARMOR_DARK, outline=OUTLINE)
    elif direction == LEFT:
        ellipse(draw, cx - 2, body_cy, 14, 14, AVAL_ARMOR)
        ellipse(draw, cx + 2, body_cy + 2, 10, 10, AVAL_ARMOR_DARK, outline=None)
        ellipse(draw, cx - 4, body_cy - 2, 8, 8, AVAL_ARMOR_LIGHT, outline=None)
        draw.rectangle([cx - 16, body_cy + 8, cx + 12, body_cy + 14],
                       fill=AVAL_ARMOR_DARK, outline=OUTLINE)
    else:  # RIGHT
        ellipse(draw, cx + 2, body_cy, 14, 14, AVAL_ARMOR)
        ellipse(draw, cx + 6, body_cy + 2, 10, 10, AVAL_ARMOR_DARK, outline=None)
        ellipse(draw, cx, body_cy - 2, 8, 8, AVAL_ARMOR_LIGHT, outline=None)
        draw.rectangle([cx - 12, body_cy + 8, cx + 16, body_cy + 14],
                       fill=AVAL_ARMOR_DARK, outline=OUTLINE)

    # --- Arms with ice/snow shoulder pads ---
    if direction == DOWN:
        for side in [-1, 1]:
            ax = cx + side * 18
            draw.rectangle([ax - 4, body_cy - 4, ax + 4, body_cy + 6],
                           fill=AVAL_ARMOR, outline=OUTLINE)
            draw.rectangle([ax - 3, body_cy + 2, ax + 3, body_cy + 6],
                           fill=AVAL_SKIN, outline=OUTLINE)
            # Snow/ice shoulder pad
            ellipse(draw, ax, body_cy - 6, 8, 5, AVAL_ICE)
            ellipse(draw, ax - 2, body_cy - 8, 5, 3, AVAL_ICE_BRIGHT, outline=None)
            # Snow cap on top
            ellipse(draw, ax, body_cy - 10, 6, 3, AVAL_FROST, outline=None)
            draw.point((ax - 3, body_cy - 10), fill=(255, 255, 255))
    elif direction == UP:
        for side in [-1, 1]:
            ax = cx + side * 18
            draw.rectangle([ax - 4, body_cy - 4, ax + 4, body_cy + 6],
                           fill=AVAL_ARMOR, outline=OUTLINE)
            ellipse(draw, ax, body_cy - 6, 8, 5, AVAL_ICE)
            ellipse(draw, ax, body_cy - 10, 6, 3, AVAL_FROST, outline=None)
    elif direction == LEFT:
        ax = cx - 14
        draw.rectangle([ax - 4, body_cy - 3, ax + 4, body_cy + 6],
                       fill=AVAL_ARMOR, outline=OUTLINE)
        draw.rectangle([ax - 3, body_cy + 2, ax + 3, body_cy + 6],
                       fill=AVAL_SKIN, outline=OUTLINE)
        ellipse(draw, ax, body_cy - 5, 8, 5, AVAL_ICE)
        ellipse(draw, ax - 2, body_cy - 7, 5, 3, AVAL_ICE_BRIGHT, outline=None)
        ellipse(draw, ax, body_cy - 9, 6, 3, AVAL_FROST, outline=None)
    else:  # RIGHT
        ax = cx + 14
        draw.rectangle([ax - 4, body_cy - 3, ax + 4, body_cy + 6],
                       fill=AVAL_ARMOR, outline=OUTLINE)
        draw.rectangle([ax - 3, body_cy + 2, ax + 3, body_cy + 6],
                       fill=AVAL_SKIN, outline=OUTLINE)
        ellipse(draw, ax, body_cy - 5, 8, 5, AVAL_ICE)
        ellipse(draw, ax + 2, body_cy - 7, 5, 3, AVAL_ICE_BRIGHT, outline=None)
        ellipse(draw, ax, body_cy - 9, 6, 3, AVAL_FROST, outline=None)

    # --- Head (icy helm) ---
    if direction == DOWN:
        ellipse(draw, cx, head_cy, 14, 12, AVAL_ARMOR)
        ellipse(draw, cx + 2, head_cy + 2, 10, 8, AVAL_ARMOR_DARK, outline=None)
        ellipse(draw, cx - 2, head_cy - 2, 8, 6, AVAL_ARMOR_LIGHT, outline=None)
        # Visor
        draw.rectangle([cx - 8, head_cy, cx + 8, head_cy + 4],
                       fill=BLACK, outline=None)
        draw.rectangle([cx - 6, head_cy + 1, cx + 6, head_cy + 3],
                       fill=AVAL_EYE, outline=None)
        # Snow on helm
        ellipse(draw, cx, head_cy - 8, 10, 4, AVAL_FROST, outline=None)
        draw.point((cx - 6, head_cy - 8), fill=(255, 255, 255))
        draw.point((cx + 4, head_cy - 9), fill=(255, 255, 255))
        # Frost breath
        if direction == DOWN:
            for i in range(breath_extend):
                bx = cx - breath_extend + i * 2
                by = head_cy + 8 + i
                draw.point((bx, by), fill=AVAL_FROST)
                draw.point((bx + 1, by), fill=AVAL_ICE_BRIGHT)
    elif direction == UP:
        ellipse(draw, cx, head_cy, 14, 12, AVAL_ARMOR)
        ellipse(draw, cx + 2, head_cy + 2, 10, 8, AVAL_ARMOR_DARK, outline=None)
        ellipse(draw, cx, head_cy - 8, 10, 4, AVAL_FROST, outline=None)
    elif direction == LEFT:
        ellipse(draw, cx - 2, head_cy, 13, 12, AVAL_ARMOR)
        ellipse(draw, cx + 2, head_cy + 2, 9, 8, AVAL_ARMOR_DARK, outline=None)
        ellipse(draw, cx - 4, head_cy - 2, 7, 6, AVAL_ARMOR_LIGHT, outline=None)
        draw.rectangle([cx - 10, head_cy, cx - 2, head_cy + 4],
                       fill=BLACK, outline=None)
        draw.rectangle([cx - 9, head_cy + 1, cx - 3, head_cy + 3],
                       fill=AVAL_EYE, outline=None)
        ellipse(draw, cx - 2, head_cy - 8, 9, 4, AVAL_FROST, outline=None)
        # Frost breath sideways
        for i in range(breath_extend):
            bx = cx - 12 - i * 2
            by = head_cy + 4 + i
            draw.point((bx, by), fill=AVAL_FROST)
    else:  # RIGHT
        ellipse(draw, cx + 2, head_cy, 13, 12, AVAL_ARMOR)
        ellipse(draw, cx + 6, head_cy + 2, 9, 8, AVAL_ARMOR_DARK, outline=None)
        ellipse(draw, cx, head_cy - 2, 7, 6, AVAL_ARMOR_LIGHT, outline=None)
        draw.rectangle([cx + 2, head_cy, cx + 10, head_cy + 4],
                       fill=BLACK, outline=None)
        draw.rectangle([cx + 3, head_cy + 1, cx + 9, head_cy + 3],
                       fill=AVAL_EYE, outline=None)
        ellipse(draw, cx + 2, head_cy - 8, 9, 4, AVAL_FROST, outline=None)
        for i in range(breath_extend):
            bx = cx + 12 + i * 2
            by = head_cy + 4 + i
            draw.point((bx, by), fill=AVAL_FROST)

    # --- Snow/frost particles ---
    for i in range(6):
        angle = frame * (math.pi / 2) + i * (math.pi / 3)
        fr = 22
        fx = int(cx + math.cos(angle) * fr)
        fy = int(body_cy + math.sin(angle) * (fr * 0.6))
        draw.point((fx, fy), fill=AVAL_FROST)
        draw.point((fx + 1, fy), fill=(255, 255, 255))


# ===================================================================
# REGISTRY & MAIN
# ===================================================================

ELEMENTAL_DRAW_FUNCTIONS = {
    'pyromancer': draw_pyromancer,
    'cryomancer': draw_cryomancer,
    'stormcaller': draw_stormcaller,
    'earthshaker': draw_earthshaker,
    'windwalker': draw_windwalker,
    'magmaknight': draw_magmaknight,
    'frostbite': draw_frostbite,
    'sandstorm': draw_sandstorm,
    'thornweaver': draw_thornweaver,
    'cloudrunner': draw_cloudrunner,
    'inferno': draw_inferno,
    'glacier': draw_glacier,
    'mudslinger': draw_mudslinger,
    'ember': draw_ember,
    'avalanche': draw_avalanche,
}


def main():
    for name, draw_func in ELEMENTAL_DRAW_FUNCTIONS.items():
        generate_character(name, draw_func=draw_func)
    print(f"\nGenerated {len(ELEMENTAL_DRAW_FUNCTIONS)} elemental character sprites.")


if __name__ == "__main__":
    main()
