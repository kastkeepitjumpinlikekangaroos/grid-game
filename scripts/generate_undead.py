#!/usr/bin/env python3
"""Undead/Dark character sprite generators (IDs 27-41).

15 characters with unique body shapes replacing the generic humanoid template.
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
# Shared constants
# ---------------------------------------------------------------------------

# Necromancer palette
NECRO_ROBE = (60, 30, 80)
NECRO_ROBE_DARK = (40, 20, 55)
NECRO_ROBE_LIGHT = (80, 45, 105)
NECRO_SKIN = (180, 170, 150)
NECRO_GLOW = (100, 200, 50)
NECRO_GLOW_DIM = (60, 140, 30)
NECRO_STAFF = (90, 70, 50)
NECRO_SKULL = (210, 200, 180)
NECRO_HOOD = (50, 25, 70)

# SkeletonKing palette
SKEL_BONE = (220, 210, 190)
SKEL_BONE_DARK = (180, 170, 150)
SKEL_BONE_LIGHT = (240, 235, 220)
SKEL_SOCKET = (30, 25, 25)
SKEL_CROWN = (220, 200, 50)
SKEL_CROWN_DARK = (180, 160, 30)
SKEL_CROWN_GEM = (200, 50, 50)
SKEL_CAPE = (80, 30, 30)
SKEL_CAPE_DARK = (55, 20, 20)

# Banshee palette
BANSH_BODY = (150, 170, 200)
BANSH_BODY_LIGHT = (180, 200, 230)
BANSH_BODY_DARK = (110, 130, 160)
BANSH_GLOW = (180, 200, 230)
BANSH_HAIR = (200, 210, 230)
BANSH_MOUTH = (50, 40, 60)
BANSH_EYE = (200, 220, 255)

# Lich palette
LICH_ROBE = (50, 40, 70)
LICH_ROBE_DARK = (35, 28, 50)
LICH_ROBE_LIGHT = (70, 55, 95)
LICH_GLOW = (100, 200, 255)
LICH_GLOW_DIM = (60, 140, 200)
LICH_SKULL = (200, 190, 170)
LICH_SKULL_DARK = (160, 150, 130)
LICH_STAFF = (80, 65, 50)
LICH_CRYSTAL = (120, 220, 255)

# Ghoul palette
GHOUL_SKIN = (80, 100, 70)
GHOUL_SKIN_DARK = (55, 70, 48)
GHOUL_SKIN_LIGHT = (100, 125, 90)
GHOUL_CLOTH = (70, 65, 55)
GHOUL_CLOTH_DARK = (50, 45, 38)
GHOUL_EYE = (200, 50, 50)
GHOUL_TEETH = (210, 200, 180)
GHOUL_CLAW = (60, 75, 50)

# Reaper palette
REAPER_ROBE = (30, 30, 40)
REAPER_ROBE_DARK = (20, 20, 28)
REAPER_ROBE_LIGHT = (45, 45, 58)
REAPER_EYE = (200, 50, 50)
REAPER_SCYTHE_BLADE = (180, 190, 200)
REAPER_SCYTHE_DARK = (140, 150, 160)
REAPER_SCYTHE_HANDLE = (80, 60, 45)
REAPER_BONE = (200, 190, 170)

# Shade palette
SHADE_BODY = (40, 35, 55)
SHADE_BODY_DARK = (25, 22, 38)
SHADE_BODY_LIGHT = (55, 48, 72)
SHADE_EYE = (150, 80, 200)
SHADE_EYE_BRIGHT = (190, 120, 240)
SHADE_TENDRIL = (50, 40, 70)
SHADE_TENDRIL_DARK = (30, 25, 48)

# Revenant palette
REV_ARMOR = (100, 70, 60)
REV_ARMOR_DARK = (70, 48, 40)
REV_ARMOR_LIGHT = (130, 95, 80)
REV_GLOW = (120, 180, 150)
REV_GLOW_DIM = (80, 140, 110)
REV_CRACK = (60, 90, 75)
REV_SWORD = (160, 155, 150)
REV_SWORD_DARK = (120, 115, 110)
REV_EYE = (150, 220, 180)
REV_SKIN = (140, 130, 120)


# ===================================================================
# NECROMANCER (ID 27) -- hooded robed figure, skull-topped staff,
#                        orbiting green soul orbs, skeletal hands
# ===================================================================

def draw_necromancer(draw, ox, oy, direction, frame):
    bob = [0, -2, 0, -1][frame]
    robe_sway = [-2, 0, 2, 0][frame]
    orb_angle_base = frame * (math.pi / 2)  # 90 degrees per frame

    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 20
    head_cy = body_cy - 18

    robe_shadow = _darken(NECRO_ROBE, 0.7)

    # --- Staff (drawn behind body for DOWN/UP) ---
    if direction == DOWN:
        staff_x = cx + 14
        # Staff shaft
        draw.line([(staff_x, head_cy - 12), (staff_x, base_y + 2)],
                  fill=NECRO_STAFF, width=2)
        # Skull at top
        ellipse(draw, staff_x, head_cy - 16, 4, 4, NECRO_SKULL)
        draw.point((staff_x - 1, head_cy - 17), fill=SKEL_SOCKET)
        draw.point((staff_x + 1, head_cy - 17), fill=SKEL_SOCKET)
        draw.point((staff_x, head_cy - 14), fill=SKEL_SOCKET)
    elif direction == UP:
        staff_x = cx - 14
        draw.line([(staff_x, head_cy - 12), (staff_x, base_y + 2)],
                  fill=NECRO_STAFF, width=2)
        ellipse(draw, staff_x, head_cy - 16, 4, 4, NECRO_SKULL)
    elif direction == LEFT:
        staff_x = cx + 12
        draw.line([(staff_x, head_cy - 12), (staff_x, base_y + 2)],
                  fill=NECRO_STAFF, width=2)
        ellipse(draw, staff_x, head_cy - 16, 4, 4, NECRO_SKULL)
        draw.point((staff_x, head_cy - 17), fill=SKEL_SOCKET)
        draw.point((staff_x, head_cy - 14), fill=SKEL_SOCKET)
    else:  # RIGHT
        staff_x = cx - 12
        draw.line([(staff_x, head_cy - 12), (staff_x, base_y + 2)],
                  fill=NECRO_STAFF, width=2)
        ellipse(draw, staff_x, head_cy - 16, 4, 4, NECRO_SKULL)
        draw.point((staff_x, head_cy - 17), fill=SKEL_SOCKET)
        draw.point((staff_x, head_cy - 14), fill=SKEL_SOCKET)

    # --- Robe body (wide, flowing) ---
    if direction == DOWN:
        # Main robe shape -- wide trapezoid
        draw.polygon([
            (cx - 14, body_cy - 10),
            (cx + 14, body_cy - 10),
            (cx + 18 + robe_sway, base_y + 2),
            (cx - 18 + robe_sway, base_y + 2),
        ], fill=NECRO_ROBE, outline=OUTLINE)
        # Fabric fold lines
        draw.line([(cx - 4, body_cy - 6), (cx - 6 + robe_sway, base_y)],
                  fill=NECRO_ROBE_DARK, width=1)
        draw.line([(cx + 4, body_cy - 6), (cx + 6 + robe_sway, base_y)],
                  fill=NECRO_ROBE_DARK, width=1)
        # Light highlight on left
        draw.line([(cx - 10, body_cy - 4), (cx - 14 + robe_sway, base_y)],
                  fill=NECRO_ROBE_LIGHT, width=1)
        # Jagged hem
        for hx in range(cx - 16 + robe_sway, cx + 16 + robe_sway, 6):
            draw.polygon([(hx, base_y), (hx + 3, base_y + 4), (hx + 6, base_y)],
                         fill=NECRO_ROBE_DARK, outline=None)
    elif direction == UP:
        draw.polygon([
            (cx - 14, body_cy - 10),
            (cx + 14, body_cy - 10),
            (cx + 18 + robe_sway, base_y + 2),
            (cx - 18 + robe_sway, base_y + 2),
        ], fill=NECRO_ROBE, outline=OUTLINE)
        draw.line([(cx, body_cy - 6), (cx + robe_sway, base_y)],
                  fill=NECRO_ROBE_DARK, width=1)
        draw.line([(cx - 8, body_cy - 4), (cx - 10 + robe_sway, base_y)],
                  fill=NECRO_ROBE_DARK, width=1)
        draw.line([(cx + 8, body_cy - 4), (cx + 10 + robe_sway, base_y)],
                  fill=NECRO_ROBE_DARK, width=1)
    elif direction == LEFT:
        draw.polygon([
            (cx - 10, body_cy - 10),
            (cx + 8, body_cy - 10),
            (cx + 12 + robe_sway, base_y + 2),
            (cx - 14 + robe_sway, base_y + 2),
        ], fill=NECRO_ROBE, outline=OUTLINE)
        draw.line([(cx - 2, body_cy - 6), (cx - 4 + robe_sway, base_y)],
                  fill=NECRO_ROBE_DARK, width=1)
        draw.line([(cx - 8, body_cy - 4), (cx - 10 + robe_sway, base_y)],
                  fill=NECRO_ROBE_LIGHT, width=1)
    else:  # RIGHT
        draw.polygon([
            (cx - 8, body_cy - 10),
            (cx + 10, body_cy - 10),
            (cx + 14 + robe_sway, base_y + 2),
            (cx - 12 + robe_sway, base_y + 2),
        ], fill=NECRO_ROBE, outline=OUTLINE)
        draw.line([(cx + 2, body_cy - 6), (cx + 4 + robe_sway, base_y)],
                  fill=NECRO_ROBE_DARK, width=1)
        draw.line([(cx + 8, body_cy - 4), (cx + 10 + robe_sway, base_y)],
                  fill=NECRO_ROBE_LIGHT, width=1)

    # --- Skeletal hands (bony fingers extending from sleeves) ---
    if direction == DOWN:
        for side in [-1, 1]:
            hx = cx + side * 16
            hy = body_cy + 2
            # Bony wrist
            draw.rectangle([hx - 2, hy - 2, hx + 2, hy + 2],
                           fill=NECRO_SKIN, outline=OUTLINE)
            # Fingers (3 bony lines)
            for fi in [-2, 0, 2]:
                draw.line([(hx + fi, hy + 2), (hx + fi + side, hy + 7)],
                          fill=NECRO_SKIN, width=1)
    elif direction in (LEFT, RIGHT):
        d = -1 if direction == LEFT else 1
        hx = cx + d * (-12)
        hy = body_cy + 2
        draw.rectangle([hx - 2, hy - 2, hx + 2, hy + 2],
                       fill=NECRO_SKIN, outline=OUTLINE)
        for fi in [-2, 0, 2]:
            draw.line([(hx - d * 2, hy + fi), (hx - d * 7, hy + fi)],
                      fill=NECRO_SKIN, width=1)

    # --- Hood / Head ---
    if direction == DOWN:
        # Hood outer shape
        ellipse(draw, cx, head_cy, 12, 10, NECRO_HOOD)
        # Hood peak
        draw.polygon([(cx - 6, head_cy - 8), (cx, head_cy - 16),
                      (cx + 6, head_cy - 8)], fill=NECRO_HOOD, outline=OUTLINE)
        # Shadowed face
        ellipse(draw, cx, head_cy + 2, 8, 6, NECRO_ROBE_DARK, outline=None)
        # Face (partially visible)
        ellipse(draw, cx, head_cy + 2, 6, 4, NECRO_SKIN, outline=None)
        # Eyes (glowing green)
        draw.rectangle([cx - 4, head_cy, cx - 2, head_cy + 2], fill=NECRO_GLOW)
        draw.rectangle([cx + 2, head_cy, cx + 4, head_cy + 2], fill=NECRO_GLOW)
    elif direction == UP:
        ellipse(draw, cx, head_cy, 12, 10, NECRO_HOOD)
        draw.polygon([(cx - 6, head_cy - 8), (cx, head_cy - 16),
                      (cx + 6, head_cy - 8)], fill=NECRO_HOOD, outline=OUTLINE)
        ellipse(draw, cx, head_cy, 9, 7, NECRO_ROBE_DARK, outline=None)
    elif direction == LEFT:
        ellipse(draw, cx - 2, head_cy, 11, 10, NECRO_HOOD)
        draw.polygon([(cx - 4, head_cy - 8), (cx - 2, head_cy - 16),
                      (cx + 2, head_cy - 8)], fill=NECRO_HOOD, outline=OUTLINE)
        ellipse(draw, cx - 4, head_cy + 2, 6, 5, NECRO_ROBE_DARK, outline=None)
        ellipse(draw, cx - 4, head_cy + 2, 4, 3, NECRO_SKIN, outline=None)
        draw.rectangle([cx - 7, head_cy + 1, cx - 5, head_cy + 3], fill=NECRO_GLOW)
    else:  # RIGHT
        ellipse(draw, cx + 2, head_cy, 11, 10, NECRO_HOOD)
        draw.polygon([(cx - 2, head_cy - 8), (cx + 2, head_cy - 16),
                      (cx + 4, head_cy - 8)], fill=NECRO_HOOD, outline=OUTLINE)
        ellipse(draw, cx + 4, head_cy + 2, 6, 5, NECRO_ROBE_DARK, outline=None)
        ellipse(draw, cx + 4, head_cy + 2, 4, 3, NECRO_SKIN, outline=None)
        draw.rectangle([cx + 5, head_cy + 1, cx + 7, head_cy + 3], fill=NECRO_GLOW)

    # --- Orbiting green soul orbs (2-3 small glow dots) ---
    orb_radius = 18
    for i in range(3):
        angle = orb_angle_base + i * (2 * math.pi / 3)
        orb_x = int(cx + math.cos(angle) * orb_radius)
        orb_y = int(body_cy + math.sin(angle) * (orb_radius * 0.5))
        # Outer glow
        ellipse(draw, orb_x, orb_y, 3, 3, NECRO_GLOW_DIM, outline=None)
        # Inner bright core
        ellipse(draw, orb_x, orb_y, 2, 2, NECRO_GLOW, outline=None)


# ===================================================================
# SKELETON KING (ID 28) -- visible ribcage, skull head, golden crown,
#                          tattered cape, bone-colored limbs
# ===================================================================

def draw_skeletonking(draw, ox, oy, direction, frame):
    bob = [0, -2, 0, -1][frame]
    leg_spread = [-3, 0, 3, 0][frame]
    cape_sway = [0, 2, 0, -2][frame]

    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 20
    head_cy = body_cy - 18

    bone_shadow = _darken(SKEL_BONE, 0.75)

    # --- Tattered cape (drawn behind body) ---
    if direction == DOWN:
        draw.polygon([
            (cx - 14, body_cy - 8),
            (cx + 14, body_cy - 8),
            (cx + 16 + cape_sway, base_y + 2),
            (cx - 16 + cape_sway, base_y + 2),
        ], fill=SKEL_CAPE, outline=OUTLINE)
        # Tattered hem -- jagged bottom
        for hx in range(cx - 14 + cape_sway, cx + 14 + cape_sway, 5):
            draw.polygon([(hx, base_y), (hx + 2, base_y + 4), (hx + 5, base_y + 1)],
                         fill=SKEL_CAPE_DARK, outline=None)
        # Fold lines
        draw.line([(cx - 4, body_cy - 4), (cx - 6 + cape_sway, base_y - 2)],
                  fill=SKEL_CAPE_DARK, width=1)
        draw.line([(cx + 4, body_cy - 4), (cx + 6 + cape_sway, base_y - 2)],
                  fill=SKEL_CAPE_DARK, width=1)
    elif direction == UP:
        draw.polygon([
            (cx - 14, body_cy - 10),
            (cx + 14, body_cy - 10),
            (cx + 18 + cape_sway, base_y + 2),
            (cx - 18 + cape_sway, base_y + 2),
        ], fill=SKEL_CAPE, outline=OUTLINE)
        # Inner lining
        draw.polygon([
            (cx - 10, body_cy - 6),
            (cx + 10, body_cy - 6),
            (cx + 14 + cape_sway, base_y - 2),
            (cx - 14 + cape_sway, base_y - 2),
        ], fill=_brighten(SKEL_CAPE, 1.2), outline=None)
        for hx in range(cx - 16 + cape_sway, cx + 16 + cape_sway, 5):
            draw.polygon([(hx, base_y), (hx + 2, base_y + 4), (hx + 5, base_y + 1)],
                         fill=SKEL_CAPE_DARK, outline=None)
    elif direction == LEFT:
        draw.polygon([
            (cx + 4, body_cy - 10),
            (cx + 16, body_cy - 8),
            (cx + 18 + cape_sway, base_y + 2),
            (cx + 4, base_y + 2),
        ], fill=SKEL_CAPE, outline=OUTLINE)
        draw.line([(cx + 10, body_cy - 4), (cx + 12 + cape_sway, base_y - 2)],
                  fill=SKEL_CAPE_DARK, width=1)
    else:  # RIGHT
        draw.polygon([
            (cx - 4, body_cy - 10),
            (cx - 16, body_cy - 8),
            (cx - 18 + cape_sway, base_y + 2),
            (cx - 4, base_y + 2),
        ], fill=SKEL_CAPE, outline=OUTLINE)
        draw.line([(cx - 10, body_cy - 4), (cx - 12 + cape_sway, base_y - 2)],
                  fill=SKEL_CAPE_DARK, width=1)

    # --- Bone legs ---
    if direction in (DOWN, UP):
        for side in [-1, 1]:
            lx = cx + side * (7 + abs(leg_spread)) + (leg_spread if side == -1 else -leg_spread)
            # Femur
            draw.rectangle([lx - 3, body_cy + 8, lx + 3, body_cy + 16],
                           fill=SKEL_BONE, outline=OUTLINE)
            # Knee joint
            ellipse(draw, lx, body_cy + 16, 3, 3, SKEL_BONE_DARK)
            # Tibia
            draw.rectangle([lx - 2, body_cy + 17, lx + 2, base_y - 2],
                           fill=SKEL_BONE, outline=OUTLINE)
            # Foot bone
            ellipse(draw, lx, base_y, 4, 2, SKEL_BONE_DARK)
    elif direction == LEFT:
        for offset in [leg_spread, -leg_spread]:
            lx = cx - 2 + offset
            draw.rectangle([lx - 3, body_cy + 8, lx + 3, body_cy + 16],
                           fill=SKEL_BONE, outline=OUTLINE)
            ellipse(draw, lx, body_cy + 16, 3, 3, SKEL_BONE_DARK)
            draw.rectangle([lx - 2, body_cy + 17, lx + 2, base_y - 2],
                           fill=SKEL_BONE, outline=OUTLINE)
            ellipse(draw, lx, base_y, 4, 2, SKEL_BONE_DARK)
    else:  # RIGHT
        for offset in [leg_spread, -leg_spread]:
            lx = cx + 2 + offset
            draw.rectangle([lx - 3, body_cy + 8, lx + 3, body_cy + 16],
                           fill=SKEL_BONE, outline=OUTLINE)
            ellipse(draw, lx, body_cy + 16, 3, 3, SKEL_BONE_DARK)
            draw.rectangle([lx - 2, body_cy + 17, lx + 2, base_y - 2],
                           fill=SKEL_BONE, outline=OUTLINE)
            ellipse(draw, lx, base_y, 4, 2, SKEL_BONE_DARK)

    # --- Torso (ribcage -- bone oval with rib lines) ---
    if direction == DOWN:
        ellipse(draw, cx, body_cy, 13, 11, SKEL_BONE)
        # Ribcage horizontal lines
        for ry in range(-6, 8, 3):
            draw.line([(cx - 10, body_cy + ry), (cx + 10, body_cy + ry)],
                      fill=SKEL_BONE_DARK, width=1)
        # Sternum center line
        draw.line([(cx, body_cy - 8), (cx, body_cy + 8)],
                  fill=bone_shadow, width=2)
        # Spine dot at bottom
        ellipse(draw, cx, body_cy + 8, 2, 2, SKEL_BONE_DARK, outline=None)
    elif direction == UP:
        ellipse(draw, cx, body_cy, 13, 11, SKEL_BONE)
        # Back ribs
        for ry in range(-6, 8, 3):
            draw.line([(cx - 10, body_cy + ry), (cx + 10, body_cy + ry)],
                      fill=bone_shadow, width=1)
        # Spine
        draw.line([(cx, body_cy - 8), (cx, body_cy + 8)],
                  fill=bone_shadow, width=2)
    elif direction == LEFT:
        ellipse(draw, cx - 2, body_cy, 11, 11, SKEL_BONE)
        for ry in range(-6, 8, 3):
            draw.line([(cx - 10, body_cy + ry), (cx + 4, body_cy + ry)],
                      fill=SKEL_BONE_DARK, width=1)
        draw.line([(cx - 2, body_cy - 8), (cx - 2, body_cy + 8)],
                  fill=bone_shadow, width=2)
    else:  # RIGHT
        ellipse(draw, cx + 2, body_cy, 11, 11, SKEL_BONE)
        for ry in range(-6, 8, 3):
            draw.line([(cx - 4, body_cy + ry), (cx + 10, body_cy + ry)],
                      fill=SKEL_BONE_DARK, width=1)
        draw.line([(cx + 2, body_cy - 8), (cx + 2, body_cy + 8)],
                  fill=bone_shadow, width=2)

    # --- Bone arms ---
    if direction == DOWN:
        for side in [-1, 1]:
            ax = cx + side * 16
            draw.rectangle([ax - 2, body_cy - 6, ax + 2, body_cy + 4],
                           fill=SKEL_BONE, outline=OUTLINE)
            # Hand bone
            ellipse(draw, ax, body_cy + 6, 3, 2, SKEL_BONE_DARK)
    elif direction == LEFT:
        draw.rectangle([cx - 14, body_cy - 4, cx - 10, body_cy + 4],
                       fill=SKEL_BONE, outline=OUTLINE)
        ellipse(draw, cx - 12, body_cy + 6, 3, 2, SKEL_BONE_DARK)
    elif direction == RIGHT:
        draw.rectangle([cx + 10, body_cy - 4, cx + 14, body_cy + 4],
                       fill=SKEL_BONE, outline=OUTLINE)
        ellipse(draw, cx + 12, body_cy + 6, 3, 2, SKEL_BONE_DARK)

    # --- Skull head (no skin) ---
    if direction == DOWN:
        ellipse(draw, cx, head_cy, 10, 10, SKEL_BONE)
        ellipse(draw, cx - 2, head_cy - 2, 6, 5, SKEL_BONE_LIGHT, outline=None)
        # Eye sockets (large dark ovals)
        ellipse(draw, cx - 4, head_cy - 1, 3, 3, SKEL_SOCKET)
        ellipse(draw, cx + 4, head_cy - 1, 3, 3, SKEL_SOCKET)
        # Nose hole (small triangle)
        draw.polygon([(cx - 1, head_cy + 3), (cx + 1, head_cy + 3),
                      (cx, head_cy + 5)], fill=SKEL_SOCKET)
        # Jaw line -- teeth
        draw.line([(cx - 6, head_cy + 6), (cx + 6, head_cy + 6)],
                  fill=SKEL_BONE_DARK, width=1)
        for tx in range(cx - 5, cx + 6, 2):
            draw.line([(tx, head_cy + 6), (tx, head_cy + 8)],
                      fill=SKEL_BONE_LIGHT, width=1)
    elif direction == UP:
        ellipse(draw, cx, head_cy, 10, 10, SKEL_BONE)
        ellipse(draw, cx, head_cy, 7, 7, bone_shadow, outline=None)
    elif direction == LEFT:
        ellipse(draw, cx - 2, head_cy, 9, 10, SKEL_BONE)
        ellipse(draw, cx - 4, head_cy - 2, 5, 5, SKEL_BONE_LIGHT, outline=None)
        # Eye socket
        ellipse(draw, cx - 6, head_cy - 1, 3, 3, SKEL_SOCKET)
        # Nose hole
        draw.point((cx - 8, head_cy + 3), fill=SKEL_SOCKET)
        # Jaw
        draw.line([(cx - 8, head_cy + 6), (cx + 2, head_cy + 6)],
                  fill=SKEL_BONE_DARK, width=1)
        for tx in range(cx - 7, cx + 2, 2):
            draw.line([(tx, head_cy + 6), (tx, head_cy + 8)],
                      fill=SKEL_BONE_LIGHT, width=1)
    else:  # RIGHT
        ellipse(draw, cx + 2, head_cy, 9, 10, SKEL_BONE)
        ellipse(draw, cx + 4, head_cy - 2, 5, 5, SKEL_BONE_LIGHT, outline=None)
        ellipse(draw, cx + 6, head_cy - 1, 3, 3, SKEL_SOCKET)
        draw.point((cx + 8, head_cy + 3), fill=SKEL_SOCKET)
        draw.line([(cx - 2, head_cy + 6), (cx + 8, head_cy + 6)],
                  fill=SKEL_BONE_DARK, width=1)
        for tx in range(cx - 1, cx + 8, 2):
            draw.line([(tx, head_cy + 6), (tx, head_cy + 8)],
                      fill=SKEL_BONE_LIGHT, width=1)

    # --- Golden crown on skull ---
    if direction == DOWN:
        # Crown base band
        draw.rectangle([cx - 8, head_cy - 10, cx + 8, head_cy - 7],
                       fill=SKEL_CROWN, outline=OUTLINE)
        # Crown points
        for px in range(cx - 7, cx + 8, 5):
            draw.polygon([(px, head_cy - 10), (px + 2, head_cy - 16),
                          (px + 4, head_cy - 10)], fill=SKEL_CROWN, outline=OUTLINE)
        # Crown gem
        ellipse(draw, cx, head_cy - 12, 2, 2, SKEL_CROWN_GEM, outline=None)
        # Crown highlight
        draw.line([(cx - 6, head_cy - 9), (cx + 6, head_cy - 9)],
                  fill=_brighten(SKEL_CROWN, 1.3), width=1)
    elif direction == UP:
        draw.rectangle([cx - 8, head_cy - 10, cx + 8, head_cy - 7],
                       fill=SKEL_CROWN, outline=OUTLINE)
        for px in range(cx - 7, cx + 8, 5):
            draw.polygon([(px, head_cy - 10), (px + 2, head_cy - 16),
                          (px + 4, head_cy - 10)], fill=SKEL_CROWN_DARK, outline=OUTLINE)
    elif direction == LEFT:
        draw.rectangle([cx - 8, head_cy - 10, cx + 4, head_cy - 7],
                       fill=SKEL_CROWN, outline=OUTLINE)
        for px in range(cx - 7, cx + 4, 5):
            draw.polygon([(px, head_cy - 10), (px + 2, head_cy - 16),
                          (px + 4, head_cy - 10)], fill=SKEL_CROWN, outline=OUTLINE)
        ellipse(draw, cx - 4, head_cy - 12, 2, 2, SKEL_CROWN_GEM, outline=None)
    else:  # RIGHT
        draw.rectangle([cx - 4, head_cy - 10, cx + 8, head_cy - 7],
                       fill=SKEL_CROWN, outline=OUTLINE)
        for px in range(cx - 3, cx + 8, 5):
            draw.polygon([(px, head_cy - 10), (px + 2, head_cy - 16),
                          (px + 4, head_cy - 10)], fill=SKEL_CROWN, outline=OUTLINE)
        ellipse(draw, cx + 4, head_cy - 12, 2, 2, SKEL_CROWN_GEM, outline=None)


# ===================================================================
# BANSHEE (ID 29) -- floating, screaming mouth, hair streaming upward,
#                    spectral glow edges, translucent body
# ===================================================================

def draw_banshee(draw, ox, oy, direction, frame):
    float_bob = [0, -3, -1, -2][frame]  # floating bob
    hair_sway = [-2, 0, 2, 0][frame]

    base_y = oy + 54 + float_bob
    cx = ox + 32
    body_cy = base_y - 16
    head_cy = body_cy - 18

    body_dark = _darken(BANSH_BODY, 0.75)

    # --- Spectral body (no legs, tapers to wispy bottom) ---
    if direction == DOWN:
        # Outer glow edge
        ellipse(draw, cx, body_cy - 2, 16, 16, BANSH_GLOW, outline=None)
        # Main body
        draw.polygon([
            (cx - 14, body_cy - 10),
            (cx + 14, body_cy - 10),
            (cx + 10, base_y + 2),
            (cx + 4, base_y + 6),
            (cx, base_y + 4),
            (cx - 4, base_y + 6),
            (cx - 10, base_y + 2),
        ], fill=BANSH_BODY, outline=OUTLINE)
        # Lighter center
        ellipse(draw, cx, body_cy, 8, 10, BANSH_BODY_LIGHT, outline=None)
        # Dark fold
        draw.line([(cx - 4, body_cy - 4), (cx - 2, base_y)],
                  fill=body_dark, width=1)
        draw.line([(cx + 4, body_cy - 4), (cx + 2, base_y)],
                  fill=body_dark, width=1)
    elif direction == UP:
        ellipse(draw, cx, body_cy - 2, 16, 16, BANSH_GLOW, outline=None)
        draw.polygon([
            (cx - 14, body_cy - 10),
            (cx + 14, body_cy - 10),
            (cx + 10, base_y + 2),
            (cx + 4, base_y + 6),
            (cx, base_y + 4),
            (cx - 4, base_y + 6),
            (cx - 10, base_y + 2),
        ], fill=BANSH_BODY, outline=OUTLINE)
        ellipse(draw, cx, body_cy, 8, 10, body_dark, outline=None)
    elif direction == LEFT:
        ellipse(draw, cx - 2, body_cy - 2, 14, 16, BANSH_GLOW, outline=None)
        draw.polygon([
            (cx - 10, body_cy - 10),
            (cx + 8, body_cy - 10),
            (cx + 6, base_y + 2),
            (cx + 2, base_y + 6),
            (cx - 2, base_y + 4),
            (cx - 8, base_y + 2),
        ], fill=BANSH_BODY, outline=OUTLINE)
        ellipse(draw, cx - 2, body_cy, 6, 10, BANSH_BODY_LIGHT, outline=None)
    else:  # RIGHT
        ellipse(draw, cx + 2, body_cy - 2, 14, 16, BANSH_GLOW, outline=None)
        draw.polygon([
            (cx - 8, body_cy - 10),
            (cx + 10, body_cy - 10),
            (cx + 8, base_y + 2),
            (cx + 2, base_y + 4),
            (cx - 2, base_y + 6),
            (cx - 6, base_y + 2),
        ], fill=BANSH_BODY, outline=OUTLINE)
        ellipse(draw, cx + 2, body_cy, 6, 10, BANSH_BODY_LIGHT, outline=None)

    # --- Spectral arms (wispy, flowing) ---
    if direction == DOWN:
        for side in [-1, 1]:
            ax = cx + side * 14
            draw.polygon([
                (ax, body_cy - 4),
                (ax + side * 6, body_cy + 4),
                (ax + side * 4, body_cy + 8),
                (ax - side * 2, body_cy + 2),
            ], fill=BANSH_BODY, outline=OUTLINE)
    elif direction == LEFT:
        draw.polygon([
            (cx - 10, body_cy - 4),
            (cx - 18, body_cy + 2),
            (cx - 16, body_cy + 6),
            (cx - 8, body_cy + 2),
        ], fill=BANSH_BODY, outline=OUTLINE)
    elif direction == RIGHT:
        draw.polygon([
            (cx + 10, body_cy - 4),
            (cx + 18, body_cy + 2),
            (cx + 16, body_cy + 6),
            (cx + 8, body_cy + 2),
        ], fill=BANSH_BODY, outline=OUTLINE)

    # --- Head ---
    if direction == DOWN:
        # Glow around head
        ellipse(draw, cx, head_cy, 13, 11, BANSH_GLOW, outline=None)
        ellipse(draw, cx, head_cy, 10, 9, BANSH_BODY)
        ellipse(draw, cx - 2, head_cy - 2, 6, 5, BANSH_BODY_LIGHT, outline=None)
        # Eyes (bright white glow)
        ellipse(draw, cx - 4, head_cy - 1, 2, 2, BANSH_EYE, outline=None)
        ellipse(draw, cx + 4, head_cy - 1, 2, 2, BANSH_EYE, outline=None)
        # Screaming open mouth (wide dark oval)
        ellipse(draw, cx, head_cy + 5, 5, 4, BANSH_MOUTH)
        # Hair streaming UPWARD
        for hx in range(-6, 7, 3):
            draw.line([(cx + hx, head_cy - 8),
                       (cx + hx + hair_sway, head_cy - 20)],
                      fill=BANSH_HAIR, width=2)
            draw.line([(cx + hx + hair_sway, head_cy - 20),
                       (cx + hx + hair_sway * 2, head_cy - 24)],
                      fill=BANSH_BODY_LIGHT, width=1)
    elif direction == UP:
        ellipse(draw, cx, head_cy, 13, 11, BANSH_GLOW, outline=None)
        ellipse(draw, cx, head_cy, 10, 9, BANSH_BODY)
        ellipse(draw, cx, head_cy, 7, 6, body_dark, outline=None)
        # Hair upward from back
        for hx in range(-6, 7, 3):
            draw.line([(cx + hx, head_cy - 8),
                       (cx + hx + hair_sway, head_cy - 22)],
                      fill=BANSH_HAIR, width=2)
    elif direction == LEFT:
        ellipse(draw, cx - 2, head_cy, 12, 11, BANSH_GLOW, outline=None)
        ellipse(draw, cx - 2, head_cy, 9, 9, BANSH_BODY)
        ellipse(draw, cx - 4, head_cy - 2, 5, 5, BANSH_BODY_LIGHT, outline=None)
        # Eye
        ellipse(draw, cx - 6, head_cy - 1, 2, 2, BANSH_EYE, outline=None)
        # Screaming mouth
        ellipse(draw, cx - 6, head_cy + 5, 4, 3, BANSH_MOUTH)
        # Hair upward
        for hx in range(-4, 5, 3):
            draw.line([(cx - 2 + hx, head_cy - 8),
                       (cx - 2 + hx + hair_sway, head_cy - 22)],
                      fill=BANSH_HAIR, width=2)
    else:  # RIGHT
        ellipse(draw, cx + 2, head_cy, 12, 11, BANSH_GLOW, outline=None)
        ellipse(draw, cx + 2, head_cy, 9, 9, BANSH_BODY)
        ellipse(draw, cx + 4, head_cy - 2, 5, 5, BANSH_BODY_LIGHT, outline=None)
        # Eye
        ellipse(draw, cx + 6, head_cy - 1, 2, 2, BANSH_EYE, outline=None)
        # Screaming mouth
        ellipse(draw, cx + 6, head_cy + 5, 4, 3, BANSH_MOUTH)
        # Hair upward
        for hx in range(-4, 5, 3):
            draw.line([(cx + 2 + hx, head_cy - 8),
                       (cx + 2 + hx + hair_sway, head_cy - 22)],
                      fill=BANSH_HAIR, width=2)


# ===================================================================
# LICH (ID 30) -- hooded robed figure, phylactery glow near chest,
#                 skeletal face under hood, crystal-topped staff
# ===================================================================

def draw_lich(draw, ox, oy, direction, frame):
    bob = [0, -2, 0, -1][frame]
    robe_sway = [-2, 0, 2, 0][frame]
    glow_pulse = [0, 1, 2, 1][frame]  # phylactery glow pulse

    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 20
    head_cy = body_cy - 18

    robe_shadow = _darken(LICH_ROBE, 0.7)

    # --- Crystal staff (behind body) ---
    if direction == DOWN:
        staff_x = cx + 16
        draw.line([(staff_x, head_cy - 14), (staff_x, base_y + 2)],
                  fill=LICH_STAFF, width=2)
        # Crystal at top
        draw.polygon([(staff_x - 3, head_cy - 14), (staff_x, head_cy - 22),
                      (staff_x + 3, head_cy - 14)], fill=LICH_CRYSTAL, outline=OUTLINE)
        # Crystal glow
        ellipse(draw, staff_x, head_cy - 18, 4 + glow_pulse, 4 + glow_pulse,
                LICH_GLOW_DIM, outline=None)
    elif direction == UP:
        staff_x = cx - 16
        draw.line([(staff_x, head_cy - 14), (staff_x, base_y + 2)],
                  fill=LICH_STAFF, width=2)
        draw.polygon([(staff_x - 3, head_cy - 14), (staff_x, head_cy - 22),
                      (staff_x + 3, head_cy - 14)], fill=LICH_CRYSTAL, outline=OUTLINE)
        ellipse(draw, staff_x, head_cy - 18, 4 + glow_pulse, 4 + glow_pulse,
                LICH_GLOW_DIM, outline=None)
    elif direction == LEFT:
        staff_x = cx + 14
        draw.line([(staff_x, head_cy - 14), (staff_x, base_y + 2)],
                  fill=LICH_STAFF, width=2)
        draw.polygon([(staff_x - 3, head_cy - 14), (staff_x, head_cy - 22),
                      (staff_x + 3, head_cy - 14)], fill=LICH_CRYSTAL, outline=OUTLINE)
        ellipse(draw, staff_x, head_cy - 18, 4 + glow_pulse, 4 + glow_pulse,
                LICH_GLOW_DIM, outline=None)
    else:  # RIGHT
        staff_x = cx - 14
        draw.line([(staff_x, head_cy - 14), (staff_x, base_y + 2)],
                  fill=LICH_STAFF, width=2)
        draw.polygon([(staff_x - 3, head_cy - 14), (staff_x, head_cy - 22),
                      (staff_x + 3, head_cy - 14)], fill=LICH_CRYSTAL, outline=OUTLINE)
        ellipse(draw, staff_x, head_cy - 18, 4 + glow_pulse, 4 + glow_pulse,
                LICH_GLOW_DIM, outline=None)

    # --- Robe body ---
    if direction == DOWN:
        draw.polygon([
            (cx - 14, body_cy - 10),
            (cx + 14, body_cy - 10),
            (cx + 18 + robe_sway, base_y + 2),
            (cx - 18 + robe_sway, base_y + 2),
        ], fill=LICH_ROBE, outline=OUTLINE)
        # Fold lines
        draw.line([(cx - 4, body_cy - 6), (cx - 6 + robe_sway, base_y)],
                  fill=LICH_ROBE_DARK, width=1)
        draw.line([(cx + 4, body_cy - 6), (cx + 6 + robe_sway, base_y)],
                  fill=LICH_ROBE_DARK, width=1)
        # Light edge
        draw.line([(cx - 12, body_cy - 4), (cx - 16 + robe_sway, base_y)],
                  fill=LICH_ROBE_LIGHT, width=1)
        # Phylactery glow on chest
        ellipse(draw, cx, body_cy - 2, 4 + glow_pulse, 4 + glow_pulse,
                LICH_GLOW_DIM, outline=None)
        ellipse(draw, cx, body_cy - 2, 3, 3, LICH_GLOW, outline=None)
    elif direction == UP:
        draw.polygon([
            (cx - 14, body_cy - 10),
            (cx + 14, body_cy - 10),
            (cx + 18 + robe_sway, base_y + 2),
            (cx - 18 + robe_sway, base_y + 2),
        ], fill=LICH_ROBE, outline=OUTLINE)
        draw.line([(cx, body_cy - 6), (cx + robe_sway, base_y)],
                  fill=LICH_ROBE_DARK, width=1)
        draw.line([(cx - 8, body_cy - 4), (cx - 10 + robe_sway, base_y)],
                  fill=LICH_ROBE_DARK, width=1)
    elif direction == LEFT:
        draw.polygon([
            (cx - 10, body_cy - 10),
            (cx + 8, body_cy - 10),
            (cx + 12 + robe_sway, base_y + 2),
            (cx - 14 + robe_sway, base_y + 2),
        ], fill=LICH_ROBE, outline=OUTLINE)
        draw.line([(cx - 2, body_cy - 6), (cx - 4 + robe_sway, base_y)],
                  fill=LICH_ROBE_DARK, width=1)
        # Phylactery glow (visible from side)
        ellipse(draw, cx - 4, body_cy - 2, 3 + glow_pulse, 3 + glow_pulse,
                LICH_GLOW_DIM, outline=None)
        ellipse(draw, cx - 4, body_cy - 2, 2, 2, LICH_GLOW, outline=None)
    else:  # RIGHT
        draw.polygon([
            (cx - 8, body_cy - 10),
            (cx + 10, body_cy - 10),
            (cx + 14 + robe_sway, base_y + 2),
            (cx - 12 + robe_sway, base_y + 2),
        ], fill=LICH_ROBE, outline=OUTLINE)
        draw.line([(cx + 2, body_cy - 6), (cx + 4 + robe_sway, base_y)],
                  fill=LICH_ROBE_DARK, width=1)
        ellipse(draw, cx + 4, body_cy - 2, 3 + glow_pulse, 3 + glow_pulse,
                LICH_GLOW_DIM, outline=None)
        ellipse(draw, cx + 4, body_cy - 2, 2, 2, LICH_GLOW, outline=None)

    # --- Skeletal hands ---
    if direction == DOWN:
        for side in [-1, 1]:
            hx = cx + side * 16
            hy = body_cy + 2
            draw.rectangle([hx - 2, hy - 2, hx + 2, hy + 2],
                           fill=LICH_SKULL, outline=OUTLINE)
            for fi in [-2, 0, 2]:
                draw.line([(hx + fi, hy + 2), (hx + fi + side, hy + 7)],
                          fill=LICH_SKULL, width=1)
    elif direction in (LEFT, RIGHT):
        d = -1 if direction == LEFT else 1
        hx = cx + d * (-12)
        hy = body_cy + 2
        draw.rectangle([hx - 2, hy - 2, hx + 2, hy + 2],
                       fill=LICH_SKULL, outline=OUTLINE)
        for fi in [-2, 0, 2]:
            draw.line([(hx - d * 2, hy + fi), (hx - d * 7, hy + fi)],
                      fill=LICH_SKULL, width=1)

    # --- Hood / Skull face ---
    if direction == DOWN:
        ellipse(draw, cx, head_cy, 12, 10, LICH_ROBE)
        draw.polygon([(cx - 6, head_cy - 8), (cx, head_cy - 16),
                      (cx + 6, head_cy - 8)], fill=LICH_ROBE, outline=OUTLINE)
        # Shadowed interior
        ellipse(draw, cx, head_cy + 2, 8, 6, LICH_ROBE_DARK, outline=None)
        # Skull face visible under hood
        ellipse(draw, cx, head_cy + 2, 6, 5, LICH_SKULL, outline=None)
        # Glowing eye sockets
        ellipse(draw, cx - 3, head_cy + 1, 2, 2, LICH_GLOW)
        ellipse(draw, cx + 3, head_cy + 1, 2, 2, LICH_GLOW)
        # Nose hole
        draw.point((cx, head_cy + 4), fill=LICH_SKULL_DARK)
        # Jaw line
        draw.line([(cx - 4, head_cy + 5), (cx + 4, head_cy + 5)],
                  fill=LICH_SKULL_DARK, width=1)
    elif direction == UP:
        ellipse(draw, cx, head_cy, 12, 10, LICH_ROBE)
        draw.polygon([(cx - 6, head_cy - 8), (cx, head_cy - 16),
                      (cx + 6, head_cy - 8)], fill=LICH_ROBE, outline=OUTLINE)
        ellipse(draw, cx, head_cy, 9, 7, LICH_ROBE_DARK, outline=None)
    elif direction == LEFT:
        ellipse(draw, cx - 2, head_cy, 11, 10, LICH_ROBE)
        draw.polygon([(cx - 4, head_cy - 8), (cx - 2, head_cy - 16),
                      (cx + 2, head_cy - 8)], fill=LICH_ROBE, outline=OUTLINE)
        ellipse(draw, cx - 4, head_cy + 2, 6, 5, LICH_ROBE_DARK, outline=None)
        ellipse(draw, cx - 4, head_cy + 2, 4, 3, LICH_SKULL, outline=None)
        # Glowing eye
        ellipse(draw, cx - 6, head_cy + 1, 2, 2, LICH_GLOW)
    else:  # RIGHT
        ellipse(draw, cx + 2, head_cy, 11, 10, LICH_ROBE)
        draw.polygon([(cx - 2, head_cy - 8), (cx + 2, head_cy - 16),
                      (cx + 4, head_cy - 8)], fill=LICH_ROBE, outline=OUTLINE)
        ellipse(draw, cx + 4, head_cy + 2, 6, 5, LICH_ROBE_DARK, outline=None)
        ellipse(draw, cx + 4, head_cy + 2, 4, 3, LICH_SKULL, outline=None)
        ellipse(draw, cx + 6, head_cy + 1, 2, 2, LICH_GLOW)


# ===================================================================
# GHOUL (ID 31) -- hunched forward posture, long clawed arms,
#                  torn clothing, exposed teeth, sickly green-grey
# ===================================================================

def draw_ghoul(draw, ox, oy, direction, frame):
    bob = [0, -1, 0, -1][frame]
    leg_spread = [-3, 0, 3, 0][frame]
    claw_reach = [-2, 0, 2, 0][frame]

    base_y = oy + 54 + bob
    cx = ox + 32
    # Hunched: body tilted forward, head lower than normal
    body_cy = base_y - 16  # lower body center (hunched)
    head_cy = body_cy - 14  # head closer to body

    skin_shadow = _darken(GHOUL_SKIN, 0.7)

    # --- Legs (short, bent) ---
    if direction in (DOWN, UP):
        for side in [-1, 1]:
            lx = cx + side * (6 + abs(leg_spread)) + (leg_spread if side == -1 else -leg_spread)
            draw.rectangle([lx - 3, body_cy + 6, lx + 3, base_y - 2],
                           fill=GHOUL_SKIN_DARK, outline=OUTLINE)
            # Torn cloth on leg
            draw.line([(lx - 2, body_cy + 8), (lx + 1, body_cy + 12)],
                      fill=GHOUL_CLOTH, width=1)
            # Clawed foot
            ellipse(draw, lx, base_y, 4, 2, GHOUL_SKIN_DARK)
            draw.point((lx - 2, base_y + 1), fill=GHOUL_CLAW)
            draw.point((lx + 2, base_y + 1), fill=GHOUL_CLAW)
    elif direction == LEFT:
        for offset in [leg_spread, -leg_spread]:
            lx = cx + offset
            draw.rectangle([lx - 3, body_cy + 6, lx + 3, base_y - 2],
                           fill=GHOUL_SKIN_DARK, outline=OUTLINE)
            ellipse(draw, lx, base_y, 4, 2, GHOUL_SKIN_DARK)
            draw.point((lx - 2, base_y + 1), fill=GHOUL_CLAW)
    else:  # RIGHT
        for offset in [leg_spread, -leg_spread]:
            lx = cx + offset
            draw.rectangle([lx - 3, body_cy + 6, lx + 3, base_y - 2],
                           fill=GHOUL_SKIN_DARK, outline=OUTLINE)
            ellipse(draw, lx, base_y, 4, 2, GHOUL_SKIN_DARK)
            draw.point((lx + 2, base_y + 1), fill=GHOUL_CLAW)

    # --- Hunched body ---
    if direction == DOWN:
        # Body tilted forward (offset up and forward)
        ellipse(draw, cx, body_cy, 12, 10, GHOUL_SKIN)
        ellipse(draw, cx - 2, body_cy - 2, 8, 6, GHOUL_SKIN_LIGHT, outline=None)
        ellipse(draw, cx + 3, body_cy + 2, 6, 4, skin_shadow, outline=None)
        # Torn clothing remnants
        draw.polygon([
            (cx - 10, body_cy - 6),
            (cx + 10, body_cy - 6),
            (cx + 8, body_cy + 2),
            (cx - 8, body_cy + 2),
        ], fill=GHOUL_CLOTH, outline=None)
        # Ragged edges
        for rx in range(cx - 9, cx + 9, 4):
            draw.line([(rx, body_cy + 1), (rx + 2, body_cy + 5)],
                      fill=GHOUL_CLOTH_DARK, width=1)
    elif direction == UP:
        ellipse(draw, cx, body_cy, 12, 10, GHOUL_SKIN)
        ellipse(draw, cx, body_cy, 9, 7, skin_shadow, outline=None)
        # Spine bumps visible
        for sy in range(body_cy - 6, body_cy + 6, 3):
            draw.point((cx, sy), fill=GHOUL_SKIN_DARK)
    elif direction == LEFT:
        # Hunched forward -- body shifted left
        ellipse(draw, cx - 4, body_cy, 10, 10, GHOUL_SKIN)
        ellipse(draw, cx - 6, body_cy - 2, 6, 6, GHOUL_SKIN_LIGHT, outline=None)
        # Torn cloth
        draw.polygon([
            (cx - 10, body_cy - 4),
            (cx + 2, body_cy - 4),
            (cx, body_cy + 4),
            (cx - 8, body_cy + 4),
        ], fill=GHOUL_CLOTH, outline=None)
    else:  # RIGHT
        ellipse(draw, cx + 4, body_cy, 10, 10, GHOUL_SKIN)
        ellipse(draw, cx + 6, body_cy - 2, 6, 6, GHOUL_SKIN_LIGHT, outline=None)
        draw.polygon([
            (cx - 2, body_cy - 4),
            (cx + 10, body_cy - 4),
            (cx + 8, body_cy + 4),
            (cx, body_cy + 4),
        ], fill=GHOUL_CLOTH, outline=None)

    # --- Long clawed arms (extending far down) ---
    if direction == DOWN:
        for side in [-1, 1]:
            ax = cx + side * 14
            # Upper arm
            draw.rectangle([ax - 2, body_cy - 4, ax + 2, body_cy + 8],
                           fill=GHOUL_SKIN, outline=OUTLINE)
            # Forearm (longer, reaching down)
            draw.rectangle([ax - 2, body_cy + 6, ax + 2, base_y - 4],
                           fill=GHOUL_SKIN_DARK, outline=OUTLINE)
            # Clawed hand with long fingers
            fy = base_y - 4 + claw_reach
            for fi in [-2, 0, 2]:
                draw.line([(ax + fi, fy), (ax + fi + side, fy + 6)],
                          fill=GHOUL_CLAW, width=1)
    elif direction == LEFT:
        # Front arm (reaching forward)
        draw.rectangle([cx - 16, body_cy - 2, cx - 12, body_cy + 10],
                       fill=GHOUL_SKIN, outline=OUTLINE)
        fy = body_cy + 10 + claw_reach
        for fi in [-2, 0, 2]:
            draw.line([(cx - 14 + fi, fy), (cx - 16 + fi, fy + 5)],
                      fill=GHOUL_CLAW, width=1)
    elif direction == RIGHT:
        draw.rectangle([cx + 12, body_cy - 2, cx + 16, body_cy + 10],
                       fill=GHOUL_SKIN, outline=OUTLINE)
        fy = body_cy + 10 + claw_reach
        for fi in [-2, 0, 2]:
            draw.line([(cx + 14 + fi, fy), (cx + 16 + fi, fy + 5)],
                      fill=GHOUL_CLAW, width=1)

    # --- Head (hunched forward, lower position) ---
    if direction == DOWN:
        ellipse(draw, cx, head_cy, 9, 8, GHOUL_SKIN)
        ellipse(draw, cx - 2, head_cy - 2, 5, 4, GHOUL_SKIN_LIGHT, outline=None)
        # Red eyes
        draw.rectangle([cx - 5, head_cy - 2, cx - 2, head_cy], fill=GHOUL_EYE)
        draw.rectangle([cx + 2, head_cy - 2, cx + 5, head_cy], fill=GHOUL_EYE)
        # Exposed teeth (white dots in a row)
        for tx in range(cx - 4, cx + 5, 2):
            draw.point((tx, head_cy + 4), fill=GHOUL_TEETH)
        # Open mouth
        draw.line([(cx - 5, head_cy + 3), (cx + 5, head_cy + 3)],
                  fill=GHOUL_SKIN_DARK, width=1)
    elif direction == UP:
        ellipse(draw, cx, head_cy, 9, 8, GHOUL_SKIN)
        ellipse(draw, cx, head_cy, 6, 5, skin_shadow, outline=None)
        # Matted hair tufts
        for hx in range(-4, 5, 3):
            draw.line([(cx + hx, head_cy - 6), (cx + hx, head_cy - 10)],
                      fill=GHOUL_SKIN_DARK, width=1)
    elif direction == LEFT:
        # Head pushed forward due to hunch
        ellipse(draw, cx - 6, head_cy, 8, 8, GHOUL_SKIN)
        ellipse(draw, cx - 8, head_cy - 2, 4, 4, GHOUL_SKIN_LIGHT, outline=None)
        # Eye
        draw.rectangle([cx - 10, head_cy - 2, cx - 8, head_cy], fill=GHOUL_EYE)
        # Teeth
        for tx in range(cx - 12, cx - 6, 2):
            draw.point((tx, head_cy + 4), fill=GHOUL_TEETH)
        draw.line([(cx - 12, head_cy + 3), (cx - 6, head_cy + 3)],
                  fill=GHOUL_SKIN_DARK, width=1)
    else:  # RIGHT
        ellipse(draw, cx + 6, head_cy, 8, 8, GHOUL_SKIN)
        ellipse(draw, cx + 8, head_cy - 2, 4, 4, GHOUL_SKIN_LIGHT, outline=None)
        draw.rectangle([cx + 8, head_cy - 2, cx + 10, head_cy], fill=GHOUL_EYE)
        for tx in range(cx + 6, cx + 12, 2):
            draw.point((tx, head_cy + 4), fill=GHOUL_TEETH)
        draw.line([(cx + 6, head_cy + 3), (cx + 12, head_cy + 3)],
                  fill=GHOUL_SKIN_DARK, width=1)


# ===================================================================
# REAPER (ID 32) -- hooded robe, large scythe, skeletal hands,
#                   void face with faint eye glow, tattered hem
# ===================================================================

def draw_reaper(draw, ox, oy, direction, frame):
    bob = [0, -2, 0, -1][frame]
    robe_sway = [-2, 0, 2, 0][frame]

    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 20
    head_cy = body_cy - 18

    robe_shadow = _darken(REAPER_ROBE, 0.7)

    # --- Large scythe (prominent, drawn behind for DOWN/UP) ---
    if direction == DOWN:
        # Long handle from top to bottom
        handle_x = cx + 16
        draw.line([(handle_x, head_cy - 18), (handle_x, base_y + 2)],
                  fill=REAPER_SCYTHE_HANDLE, width=3)
        # Curved blade at top (large arc)
        draw.polygon([
            (handle_x, head_cy - 18),
            (handle_x - 4, head_cy - 22),
            (handle_x - 16, head_cy - 18),
            (handle_x - 22, head_cy - 10),
            (handle_x - 18, head_cy - 8),
            (handle_x - 12, head_cy - 14),
            (handle_x - 2, head_cy - 16),
        ], fill=REAPER_SCYTHE_BLADE, outline=OUTLINE)
        # Blade edge highlight
        draw.line([(handle_x - 20, head_cy - 14), (handle_x - 6, head_cy - 20)],
                  fill=_brighten(REAPER_SCYTHE_BLADE, 1.3), width=1)
    elif direction == UP:
        handle_x = cx - 16
        draw.line([(handle_x, head_cy - 18), (handle_x, base_y + 2)],
                  fill=REAPER_SCYTHE_HANDLE, width=3)
        draw.polygon([
            (handle_x, head_cy - 18),
            (handle_x + 4, head_cy - 22),
            (handle_x + 16, head_cy - 18),
            (handle_x + 22, head_cy - 10),
            (handle_x + 18, head_cy - 8),
            (handle_x + 12, head_cy - 14),
            (handle_x + 2, head_cy - 16),
        ], fill=REAPER_SCYTHE_BLADE, outline=OUTLINE)
    elif direction == LEFT:
        handle_x = cx + 6
        draw.line([(handle_x, head_cy - 18), (handle_x, base_y + 2)],
                  fill=REAPER_SCYTHE_HANDLE, width=3)
        # Blade extends left
        draw.polygon([
            (handle_x, head_cy - 18),
            (handle_x - 4, head_cy - 22),
            (handle_x - 18, head_cy - 18),
            (handle_x - 24, head_cy - 10),
            (handle_x - 20, head_cy - 8),
            (handle_x - 14, head_cy - 14),
            (handle_x - 2, head_cy - 16),
        ], fill=REAPER_SCYTHE_BLADE, outline=OUTLINE)
        draw.line([(handle_x - 22, head_cy - 14), (handle_x - 6, head_cy - 20)],
                  fill=_brighten(REAPER_SCYTHE_BLADE, 1.3), width=1)
    else:  # RIGHT
        handle_x = cx - 6
        draw.line([(handle_x, head_cy - 18), (handle_x, base_y + 2)],
                  fill=REAPER_SCYTHE_HANDLE, width=3)
        draw.polygon([
            (handle_x, head_cy - 18),
            (handle_x + 4, head_cy - 22),
            (handle_x + 18, head_cy - 18),
            (handle_x + 24, head_cy - 10),
            (handle_x + 20, head_cy - 8),
            (handle_x + 14, head_cy - 14),
            (handle_x + 2, head_cy - 16),
        ], fill=REAPER_SCYTHE_BLADE, outline=OUTLINE)
        draw.line([(handle_x + 22, head_cy - 14), (handle_x + 6, head_cy - 20)],
                  fill=_brighten(REAPER_SCYTHE_BLADE, 1.3), width=1)

    # --- Robe body (all black, flowing) ---
    if direction == DOWN:
        draw.polygon([
            (cx - 14, body_cy - 10),
            (cx + 14, body_cy - 10),
            (cx + 18 + robe_sway, base_y + 2),
            (cx - 18 + robe_sway, base_y + 2),
        ], fill=REAPER_ROBE, outline=OUTLINE)
        # Dark fold lines
        draw.line([(cx - 4, body_cy - 6), (cx - 6 + robe_sway, base_y)],
                  fill=REAPER_ROBE_DARK, width=1)
        draw.line([(cx + 4, body_cy - 6), (cx + 6 + robe_sway, base_y)],
                  fill=REAPER_ROBE_DARK, width=1)
        # Subtle highlight
        draw.line([(cx - 10, body_cy - 4), (cx - 14 + robe_sway, base_y)],
                  fill=REAPER_ROBE_LIGHT, width=1)
        # Tattered jagged hem
        for hx in range(cx - 16 + robe_sway, cx + 16 + robe_sway, 5):
            draw.polygon([(hx, base_y), (hx + 2, base_y + 5), (hx + 5, base_y + 1)],
                         fill=REAPER_ROBE_DARK, outline=None)
    elif direction == UP:
        draw.polygon([
            (cx - 14, body_cy - 10),
            (cx + 14, body_cy - 10),
            (cx + 18 + robe_sway, base_y + 2),
            (cx - 18 + robe_sway, base_y + 2),
        ], fill=REAPER_ROBE, outline=OUTLINE)
        draw.line([(cx, body_cy - 6), (cx + robe_sway, base_y)],
                  fill=REAPER_ROBE_DARK, width=1)
        for hx in range(cx - 16 + robe_sway, cx + 16 + robe_sway, 5):
            draw.polygon([(hx, base_y), (hx + 2, base_y + 5), (hx + 5, base_y + 1)],
                         fill=REAPER_ROBE_DARK, outline=None)
    elif direction == LEFT:
        draw.polygon([
            (cx - 10, body_cy - 10),
            (cx + 8, body_cy - 10),
            (cx + 12 + robe_sway, base_y + 2),
            (cx - 14 + robe_sway, base_y + 2),
        ], fill=REAPER_ROBE, outline=OUTLINE)
        draw.line([(cx - 2, body_cy - 6), (cx - 4 + robe_sway, base_y)],
                  fill=REAPER_ROBE_DARK, width=1)
        draw.line([(cx - 8, body_cy - 4), (cx - 10 + robe_sway, base_y)],
                  fill=REAPER_ROBE_LIGHT, width=1)
        for hx in range(cx - 12 + robe_sway, cx + 10 + robe_sway, 5):
            draw.polygon([(hx, base_y), (hx + 2, base_y + 5), (hx + 5, base_y + 1)],
                         fill=REAPER_ROBE_DARK, outline=None)
    else:  # RIGHT
        draw.polygon([
            (cx - 8, body_cy - 10),
            (cx + 10, body_cy - 10),
            (cx + 14 + robe_sway, base_y + 2),
            (cx - 12 + robe_sway, base_y + 2),
        ], fill=REAPER_ROBE, outline=OUTLINE)
        draw.line([(cx + 2, body_cy - 6), (cx + 4 + robe_sway, base_y)],
                  fill=REAPER_ROBE_DARK, width=1)
        draw.line([(cx + 8, body_cy - 4), (cx + 10 + robe_sway, base_y)],
                  fill=REAPER_ROBE_LIGHT, width=1)
        for hx in range(cx - 10 + robe_sway, cx + 12 + robe_sway, 5):
            draw.polygon([(hx, base_y), (hx + 2, base_y + 5), (hx + 5, base_y + 1)],
                         fill=REAPER_ROBE_DARK, outline=None)

    # --- Skeletal hands gripping scythe ---
    if direction == DOWN:
        # Hands on the scythe handle
        hx = cx + 16
        draw.rectangle([hx - 3, body_cy - 2, hx + 3, body_cy + 4],
                       fill=REAPER_BONE, outline=OUTLINE)
        draw.rectangle([hx - 3, body_cy + 6, hx + 3, body_cy + 12],
                       fill=REAPER_BONE, outline=OUTLINE)
    elif direction == LEFT:
        hx = cx + 6
        draw.rectangle([hx - 3, body_cy - 2, hx + 3, body_cy + 4],
                       fill=REAPER_BONE, outline=OUTLINE)
        draw.rectangle([hx - 3, body_cy + 6, hx + 3, body_cy + 12],
                       fill=REAPER_BONE, outline=OUTLINE)
    elif direction == RIGHT:
        hx = cx - 6
        draw.rectangle([hx - 3, body_cy - 2, hx + 3, body_cy + 4],
                       fill=REAPER_BONE, outline=OUTLINE)
        draw.rectangle([hx - 3, body_cy + 6, hx + 3, body_cy + 12],
                       fill=REAPER_BONE, outline=OUTLINE)

    # --- Hood (void face, no features except faint eye glow) ---
    if direction == DOWN:
        ellipse(draw, cx, head_cy, 12, 10, REAPER_ROBE)
        draw.polygon([(cx - 6, head_cy - 8), (cx, head_cy - 16),
                      (cx + 6, head_cy - 8)], fill=REAPER_ROBE, outline=OUTLINE)
        # Void face (just black)
        ellipse(draw, cx, head_cy + 2, 8, 6, REAPER_ROBE_DARK, outline=None)
        # Faint red eye glow
        ellipse(draw, cx - 3, head_cy + 1, 2, 1, REAPER_EYE, outline=None)
        ellipse(draw, cx + 3, head_cy + 1, 2, 1, REAPER_EYE, outline=None)
    elif direction == UP:
        ellipse(draw, cx, head_cy, 12, 10, REAPER_ROBE)
        draw.polygon([(cx - 6, head_cy - 8), (cx, head_cy - 16),
                      (cx + 6, head_cy - 8)], fill=REAPER_ROBE, outline=OUTLINE)
        ellipse(draw, cx, head_cy, 9, 7, REAPER_ROBE_DARK, outline=None)
    elif direction == LEFT:
        ellipse(draw, cx - 2, head_cy, 11, 10, REAPER_ROBE)
        draw.polygon([(cx - 4, head_cy - 8), (cx - 2, head_cy - 16),
                      (cx + 2, head_cy - 8)], fill=REAPER_ROBE, outline=OUTLINE)
        ellipse(draw, cx - 4, head_cy + 2, 6, 5, REAPER_ROBE_DARK, outline=None)
        # Single faint eye
        ellipse(draw, cx - 6, head_cy + 1, 2, 1, REAPER_EYE, outline=None)
    else:  # RIGHT
        ellipse(draw, cx + 2, head_cy, 11, 10, REAPER_ROBE)
        draw.polygon([(cx - 2, head_cy - 8), (cx + 2, head_cy - 16),
                      (cx + 4, head_cy - 8)], fill=REAPER_ROBE, outline=OUTLINE)
        ellipse(draw, cx + 4, head_cy + 2, 6, 5, REAPER_ROBE_DARK, outline=None)
        ellipse(draw, cx + 6, head_cy + 1, 2, 1, REAPER_EYE, outline=None)


# ===================================================================
# SHADE (ID 33) -- floating, semi-transparent, shadow tendrils,
#                  only glowing eyes visible, amorphous shape
# ===================================================================

def draw_shade(draw, ox, oy, direction, frame):
    float_bob = [0, -3, -1, -2][frame]
    tendril_sway = [-3, 0, 3, 0][frame]
    # Shape shifts slightly per frame for amorphous feel
    shape_shift = [-1, 0, 1, 0][frame]

    base_y = oy + 54 + float_bob
    cx = ox + 32
    body_cy = base_y - 16
    head_cy = body_cy - 16

    # --- Shadow tendrils extending from bottom (drawn first, behind body) ---
    tendril_colors = [SHADE_TENDRIL, SHADE_TENDRIL_DARK, SHADE_TENDRIL]
    for i, tc in enumerate(tendril_colors):
        tx_offset = (i - 1) * 8
        # Wavy tendril lines
        for seg in range(4):
            seg_y = base_y + seg * 4
            sway = tendril_sway * (1 + seg * 0.5)
            draw.line([
                (cx + tx_offset + int(sway * 0.5), seg_y),
                (cx + tx_offset + int(sway), seg_y + 4),
            ], fill=tc, width=2)

    # --- Side tendrils ---
    if direction == DOWN:
        for side in [-1, 1]:
            for i in range(3):
                ty = body_cy - 4 + i * 6
                sway = tendril_sway * (1 + i * 0.3)
                draw.line([
                    (cx + side * (12 + shape_shift), ty),
                    (cx + side * (18 + shape_shift) + int(sway), ty + 4),
                ], fill=SHADE_TENDRIL, width=2)
    elif direction == LEFT:
        for i in range(3):
            ty = body_cy - 4 + i * 6
            sway = tendril_sway * (1 + i * 0.3)
            draw.line([
                (cx + 10 + shape_shift, ty),
                (cx + 16 + shape_shift + int(sway), ty + 4),
            ], fill=SHADE_TENDRIL, width=2)
    elif direction == RIGHT:
        for i in range(3):
            ty = body_cy - 4 + i * 6
            sway = tendril_sway * (1 + i * 0.3)
            draw.line([
                (cx - 10 - shape_shift, ty),
                (cx - 16 - shape_shift + int(sway), ty + 4),
            ], fill=SHADE_TENDRIL, width=2)

    # --- Amorphous body (shape shifts per frame) ---
    if direction == DOWN:
        # Main dark mass
        ellipse(draw, cx + shape_shift, body_cy, 14 + shape_shift, 14, SHADE_BODY)
        ellipse(draw, cx - 2 + shape_shift, body_cy - 2, 10, 10,
                SHADE_BODY_LIGHT, outline=None)
        # Darker core
        ellipse(draw, cx + shape_shift, body_cy + 2, 8, 8,
                SHADE_BODY_DARK, outline=None)
    elif direction == UP:
        ellipse(draw, cx - shape_shift, body_cy, 14 - shape_shift, 14, SHADE_BODY)
        ellipse(draw, cx - shape_shift, body_cy, 10, 10,
                SHADE_BODY_DARK, outline=None)
    elif direction == LEFT:
        ellipse(draw, cx - 2 + shape_shift, body_cy, 12 + shape_shift, 14, SHADE_BODY)
        ellipse(draw, cx - 4 + shape_shift, body_cy - 2, 8, 10,
                SHADE_BODY_LIGHT, outline=None)
        ellipse(draw, cx - 2 + shape_shift, body_cy + 2, 6, 8,
                SHADE_BODY_DARK, outline=None)
    else:  # RIGHT
        ellipse(draw, cx + 2 - shape_shift, body_cy, 12 - shape_shift, 14, SHADE_BODY)
        ellipse(draw, cx + 4 - shape_shift, body_cy - 2, 8, 10,
                SHADE_BODY_LIGHT, outline=None)
        ellipse(draw, cx + 2 - shape_shift, body_cy + 2, 6, 8,
                SHADE_BODY_DARK, outline=None)

    # --- Wispy bottom (tapers, no legs) ---
    if direction in (DOWN, UP):
        draw.polygon([
            (cx - 10, body_cy + 8),
            (cx + 10, body_cy + 8),
            (cx + 4 + tendril_sway, base_y + 4),
            (cx + tendril_sway, base_y + 2),
            (cx - 4 + tendril_sway, base_y + 4),
        ], fill=SHADE_BODY, outline=None)
    elif direction == LEFT:
        draw.polygon([
            (cx - 8, body_cy + 8),
            (cx + 6, body_cy + 8),
            (cx + 2 + tendril_sway, base_y + 4),
            (cx - 4 + tendril_sway, base_y + 2),
        ], fill=SHADE_BODY, outline=None)
    else:  # RIGHT
        draw.polygon([
            (cx - 6, body_cy + 8),
            (cx + 8, body_cy + 8),
            (cx + 4 + tendril_sway, base_y + 2),
            (cx - 2 + tendril_sway, base_y + 4),
        ], fill=SHADE_BODY, outline=None)

    # --- Head (amorphous dark mass, ONLY glowing eyes visible) ---
    if direction == DOWN:
        ellipse(draw, cx + shape_shift, head_cy, 10 + shape_shift, 9, SHADE_BODY)
        ellipse(draw, cx - 1 + shape_shift, head_cy - 1, 7, 6,
                SHADE_BODY_DARK, outline=None)
        # Glowing purple eyes (only feature)
        ellipse(draw, cx - 4 + shape_shift, head_cy, 2, 2, SHADE_EYE)
        ellipse(draw, cx - 4 + shape_shift, head_cy, 1, 1,
                SHADE_EYE_BRIGHT, outline=None)
        ellipse(draw, cx + 4 + shape_shift, head_cy, 2, 2, SHADE_EYE)
        ellipse(draw, cx + 4 + shape_shift, head_cy, 1, 1,
                SHADE_EYE_BRIGHT, outline=None)
    elif direction == UP:
        ellipse(draw, cx - shape_shift, head_cy, 10 - shape_shift, 9, SHADE_BODY)
        ellipse(draw, cx - shape_shift, head_cy, 7, 6,
                SHADE_BODY_DARK, outline=None)
    elif direction == LEFT:
        ellipse(draw, cx - 2 + shape_shift, head_cy, 9 + shape_shift, 9, SHADE_BODY)
        ellipse(draw, cx - 4 + shape_shift, head_cy - 1, 6, 6,
                SHADE_BODY_DARK, outline=None)
        # Single glowing eye
        ellipse(draw, cx - 6 + shape_shift, head_cy, 2, 2, SHADE_EYE)
        ellipse(draw, cx - 6 + shape_shift, head_cy, 1, 1,
                SHADE_EYE_BRIGHT, outline=None)
    else:  # RIGHT
        ellipse(draw, cx + 2 - shape_shift, head_cy, 9 - shape_shift, 9, SHADE_BODY)
        ellipse(draw, cx + 4 - shape_shift, head_cy - 1, 6, 6,
                SHADE_BODY_DARK, outline=None)
        ellipse(draw, cx + 6 - shape_shift, head_cy, 2, 2, SHADE_EYE)
        ellipse(draw, cx + 6 - shape_shift, head_cy, 1, 1,
                SHADE_EYE_BRIGHT, outline=None)


# ===================================================================
# REVENANT (ID 34) -- armored warrior, cracked/damaged armor,
#                     ghostly glow edges, broken sword, glowing eye
# ===================================================================

def draw_revenant(draw, ox, oy, direction, frame):
    bob = [0, -2, 0, -1][frame]
    leg_spread = [-3, 0, 3, 0][frame]

    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 20
    head_cy = body_cy - 18

    armor_shadow = _darken(REV_ARMOR, 0.7)

    # --- Ghostly glow outline (drawn behind everything) ---
    if direction == DOWN:
        ellipse(draw, cx, body_cy, 17, 15, REV_GLOW_DIM, outline=None)
    elif direction == UP:
        ellipse(draw, cx, body_cy, 17, 15, REV_GLOW_DIM, outline=None)
    elif direction == LEFT:
        ellipse(draw, cx - 2, body_cy, 15, 15, REV_GLOW_DIM, outline=None)
    else:  # RIGHT
        ellipse(draw, cx + 2, body_cy, 15, 15, REV_GLOW_DIM, outline=None)

    # --- Broken sword prop ---
    if direction == DOWN:
        sword_x = cx + 18
        # Handle
        draw.line([(sword_x, body_cy + 4), (sword_x, body_cy - 4)],
                  fill=REV_ARMOR_DARK, width=2)
        # Cross guard
        draw.line([(sword_x - 3, body_cy - 4), (sword_x + 3, body_cy - 4)],
                  fill=REV_ARMOR, width=2)
        # Broken blade (shorter, jagged end)
        draw.rectangle([sword_x - 1, body_cy - 16, sword_x + 1, body_cy - 4],
                       fill=REV_SWORD, outline=OUTLINE)
        # Jagged break at top
        draw.polygon([(sword_x - 2, body_cy - 16), (sword_x + 1, body_cy - 18),
                      (sword_x + 2, body_cy - 15)], fill=REV_SWORD, outline=OUTLINE)
    elif direction == LEFT:
        sword_x = cx - 16
        draw.line([(sword_x, body_cy + 4), (sword_x, body_cy - 4)],
                  fill=REV_ARMOR_DARK, width=2)
        draw.line([(sword_x - 3, body_cy - 4), (sword_x + 3, body_cy - 4)],
                  fill=REV_ARMOR, width=2)
        draw.rectangle([sword_x - 1, body_cy - 16, sword_x + 1, body_cy - 4],
                       fill=REV_SWORD, outline=OUTLINE)
        draw.polygon([(sword_x - 2, body_cy - 16), (sword_x + 1, body_cy - 18),
                      (sword_x + 2, body_cy - 15)], fill=REV_SWORD, outline=OUTLINE)
    elif direction == RIGHT:
        sword_x = cx + 18
        draw.line([(sword_x, body_cy + 4), (sword_x, body_cy - 4)],
                  fill=REV_ARMOR_DARK, width=2)
        draw.line([(sword_x - 3, body_cy - 4), (sword_x + 3, body_cy - 4)],
                  fill=REV_ARMOR, width=2)
        draw.rectangle([sword_x - 1, body_cy - 16, sword_x + 1, body_cy - 4],
                       fill=REV_SWORD, outline=OUTLINE)
        draw.polygon([(sword_x - 2, body_cy - 16), (sword_x + 1, body_cy - 18),
                      (sword_x + 2, body_cy - 15)], fill=REV_SWORD, outline=OUTLINE)

    # --- Legs (armored) ---
    if direction in (DOWN, UP):
        for side in [-1, 1]:
            lx = cx + side * (7 + abs(leg_spread)) + (leg_spread if side == -1 else -leg_spread)
            # Armored leg
            draw.rectangle([lx - 3, body_cy + 8, lx + 3, base_y - 3],
                           fill=REV_ARMOR, outline=OUTLINE)
            # Leg highlight
            if direction == DOWN:
                draw.rectangle([lx - 3, body_cy + 8, lx - 1, base_y - 6],
                               fill=REV_ARMOR_LIGHT, outline=None)
            # Armored boot
            draw.rectangle([lx - 4, base_y - 4, lx + 4, base_y],
                           fill=REV_ARMOR_DARK, outline=OUTLINE)
            # Crack on one leg
            if side == 1:
                draw.line([(lx - 1, body_cy + 10), (lx + 2, body_cy + 16)],
                          fill=REV_CRACK, width=1)
    elif direction == LEFT:
        for offset in [leg_spread, -leg_spread]:
            lx = cx - 2 + offset
            draw.rectangle([lx - 3, body_cy + 8, lx + 3, base_y - 3],
                           fill=REV_ARMOR, outline=OUTLINE)
            draw.rectangle([lx - 4, base_y - 4, lx + 4, base_y],
                           fill=REV_ARMOR_DARK, outline=OUTLINE)
    else:  # RIGHT
        for offset in [leg_spread, -leg_spread]:
            lx = cx + 2 + offset
            draw.rectangle([lx - 3, body_cy + 8, lx + 3, base_y - 3],
                           fill=REV_ARMOR, outline=OUTLINE)
            draw.rectangle([lx - 4, base_y - 4, lx + 4, base_y],
                           fill=REV_ARMOR_DARK, outline=OUTLINE)

    # --- Armored torso (with cracks) ---
    if direction == DOWN:
        ellipse(draw, cx, body_cy, 14, 12, REV_ARMOR)
        ellipse(draw, cx + 3, body_cy + 2, 10, 8, armor_shadow, outline=None)
        ellipse(draw, cx, body_cy, 11, 9, REV_ARMOR, outline=None)
        ellipse(draw, cx - 3, body_cy - 2, 8, 6, REV_ARMOR_LIGHT, outline=None)
        # Crack lines across armor
        draw.line([(cx - 6, body_cy - 4), (cx - 2, body_cy + 2), (cx + 4, body_cy)],
                  fill=REV_CRACK, width=1)
        draw.line([(cx + 2, body_cy - 6), (cx + 6, body_cy - 2), (cx + 4, body_cy + 4)],
                  fill=REV_CRACK, width=1)
        # Ghostly glow through cracks
        draw.point((cx - 2, body_cy + 1), fill=REV_GLOW)
        draw.point((cx + 5, body_cy - 1), fill=REV_GLOW)
    elif direction == UP:
        ellipse(draw, cx, body_cy, 14, 12, REV_ARMOR)
        ellipse(draw, cx, body_cy, 11, 9, REV_ARMOR_DARK, outline=None)
        # Back plate crack
        draw.line([(cx - 4, body_cy - 4), (cx, body_cy), (cx + 2, body_cy + 4)],
                  fill=REV_CRACK, width=1)
    elif direction == LEFT:
        ellipse(draw, cx - 2, body_cy, 12, 12, REV_ARMOR)
        ellipse(draw, cx + 2, body_cy + 2, 8, 8, armor_shadow, outline=None)
        ellipse(draw, cx - 2, body_cy, 9, 9, REV_ARMOR, outline=None)
        ellipse(draw, cx - 4, body_cy - 2, 6, 6, REV_ARMOR_LIGHT, outline=None)
        # Crack
        draw.line([(cx - 6, body_cy - 4), (cx - 2, body_cy + 2)],
                  fill=REV_CRACK, width=1)
        draw.point((cx - 3, body_cy), fill=REV_GLOW)
    else:  # RIGHT
        ellipse(draw, cx + 2, body_cy, 12, 12, REV_ARMOR)
        ellipse(draw, cx + 6, body_cy + 2, 8, 8, armor_shadow, outline=None)
        ellipse(draw, cx + 2, body_cy, 9, 9, REV_ARMOR, outline=None)
        ellipse(draw, cx, body_cy - 2, 6, 6, REV_ARMOR_LIGHT, outline=None)
        draw.line([(cx + 6, body_cy - 4), (cx + 2, body_cy + 2)],
                  fill=REV_CRACK, width=1)
        draw.point((cx + 3, body_cy), fill=REV_GLOW)

    # --- Armored arms ---
    if direction == DOWN:
        for side in [-1, 1]:
            ax = cx + side * 16
            draw.rectangle([ax - 3, body_cy - 6, ax + 3, body_cy + 6],
                           fill=REV_ARMOR, outline=OUTLINE)
            # Shoulder plate
            ellipse(draw, ax, body_cy - 6, 5, 3, REV_ARMOR_LIGHT)
            # Gauntlet
            draw.rectangle([ax - 3, body_cy + 2, ax + 3, body_cy + 6],
                           fill=REV_ARMOR_DARK, outline=OUTLINE)
    elif direction == LEFT:
        draw.rectangle([cx - 14, body_cy - 4, cx - 8, body_cy + 6],
                       fill=REV_ARMOR, outline=OUTLINE)
        ellipse(draw, cx - 11, body_cy - 4, 5, 3, REV_ARMOR_LIGHT)
    elif direction == RIGHT:
        draw.rectangle([cx + 8, body_cy - 4, cx + 14, body_cy + 6],
                       fill=REV_ARMOR, outline=OUTLINE)
        ellipse(draw, cx + 11, body_cy - 4, 5, 3, REV_ARMOR_LIGHT)

    # --- Head (cracked visor helmet, one glowing eye) ---
    if direction == DOWN:
        # Helmet
        ellipse(draw, cx, head_cy, 10, 10, REV_ARMOR)
        ellipse(draw, cx - 2, head_cy - 2, 6, 6, REV_ARMOR_LIGHT, outline=None)
        # Visor slit
        draw.rectangle([cx - 7, head_cy - 1, cx + 7, head_cy + 2],
                       fill=REV_ARMOR_DARK, outline=OUTLINE)
        # Visor crack
        draw.line([(cx + 2, head_cy - 6), (cx + 4, head_cy - 1),
                   (cx + 3, head_cy + 4)], fill=REV_CRACK, width=1)
        # Glowing eye through crack (right eye)
        ellipse(draw, cx + 4, head_cy, 2, 1, REV_EYE, outline=None)
        # Dark left eye
        draw.rectangle([cx - 5, head_cy, cx - 2, head_cy + 1],
                       fill=SHADE_BODY_DARK, outline=None)
        # Helmet crest
        draw.rectangle([cx - 1, head_cy - 12, cx + 1, head_cy - 6],
                       fill=REV_ARMOR, outline=OUTLINE)
    elif direction == UP:
        ellipse(draw, cx, head_cy, 10, 10, REV_ARMOR)
        ellipse(draw, cx, head_cy, 7, 7, REV_ARMOR_DARK, outline=None)
        # Back crack
        draw.line([(cx - 2, head_cy - 6), (cx, head_cy + 2)],
                  fill=REV_CRACK, width=1)
        draw.rectangle([cx - 1, head_cy - 12, cx + 1, head_cy - 6],
                       fill=REV_ARMOR, outline=OUTLINE)
    elif direction == LEFT:
        ellipse(draw, cx - 2, head_cy, 9, 10, REV_ARMOR)
        ellipse(draw, cx - 4, head_cy - 2, 5, 6, REV_ARMOR_LIGHT, outline=None)
        # Visor slit
        draw.rectangle([cx - 9, head_cy - 1, cx + 2, head_cy + 2],
                       fill=REV_ARMOR_DARK, outline=OUTLINE)
        # Crack across visor
        draw.line([(cx - 4, head_cy - 6), (cx - 6, head_cy), (cx - 4, head_cy + 4)],
                  fill=REV_CRACK, width=1)
        # Glowing eye through crack
        ellipse(draw, cx - 6, head_cy, 2, 1, REV_EYE, outline=None)
        draw.rectangle([cx - 1, head_cy - 12, cx + 1, head_cy - 6],
                       fill=REV_ARMOR, outline=OUTLINE)
    else:  # RIGHT
        ellipse(draw, cx + 2, head_cy, 9, 10, REV_ARMOR)
        ellipse(draw, cx + 4, head_cy - 2, 5, 6, REV_ARMOR_LIGHT, outline=None)
        draw.rectangle([cx - 2, head_cy - 1, cx + 9, head_cy + 2],
                       fill=REV_ARMOR_DARK, outline=OUTLINE)
        draw.line([(cx + 4, head_cy - 6), (cx + 6, head_cy), (cx + 4, head_cy + 4)],
                  fill=REV_CRACK, width=1)
        ellipse(draw, cx + 6, head_cy, 2, 1, REV_EYE, outline=None)
        draw.rectangle([cx - 1, head_cy - 12, cx + 1, head_cy - 6],
                       fill=REV_ARMOR, outline=OUTLINE)


# ===================================================================
# GRAVEDIGGER (ID 35) -- hunched humanoid, shovel prop, dirty tattered
#                         clothing, lantern at belt, wide-brimmed hat
# ===================================================================

# Gravedigger palette
GRAVE_CLOTH = (90, 80, 70)
GRAVE_CLOTH_DARK = (65, 58, 50)
GRAVE_CLOTH_LIGHT = (115, 105, 92)
GRAVE_SKIN = (170, 155, 135)
GRAVE_SHOVEL_HANDLE = (100, 75, 50)
GRAVE_SHOVEL_BLADE = (140, 140, 145)
GRAVE_SHOVEL_DARK = (100, 100, 105)
GRAVE_LANTERN = (200, 180, 60)
GRAVE_LANTERN_GLOW = (220, 200, 80)
GRAVE_HAT = (70, 60, 50)
GRAVE_HAT_DARK = (50, 42, 35)
GRAVE_BOOT = (60, 50, 40)
GRAVE_MUD = (85, 70, 45)


def draw_gravedigger(draw, ox, oy, direction, frame):
    bob = [0, -1, 0, -1][frame]
    leg_spread = [-2, 0, 2, 0][frame]
    lantern_sway = [-1, 0, 1, 0][frame]

    base_y = oy + 54 + bob
    cx = ox + 32
    # Hunched posture -- body center lower than normal
    body_cy = base_y - 16
    head_cy = body_cy - 14

    # --- Shovel prop (behind body for DOWN/UP) ---
    if direction == DOWN:
        sx = cx + 16
        # Long handle
        draw.line([(sx, head_cy - 16), (sx, base_y + 2)],
                  fill=GRAVE_SHOVEL_HANDLE, width=2)
        # Rectangular blade at top
        draw.rectangle([sx - 4, head_cy - 22, sx + 4, head_cy - 16],
                       fill=GRAVE_SHOVEL_BLADE, outline=OUTLINE)
        draw.rectangle([sx - 3, head_cy - 21, sx - 1, head_cy - 17],
                       fill=GRAVE_SHOVEL_DARK, outline=None)
    elif direction == UP:
        sx = cx - 16
        draw.line([(sx, head_cy - 16), (sx, base_y + 2)],
                  fill=GRAVE_SHOVEL_HANDLE, width=2)
        draw.rectangle([sx - 4, head_cy - 22, sx + 4, head_cy - 16],
                       fill=GRAVE_SHOVEL_BLADE, outline=OUTLINE)
    elif direction == LEFT:
        sx = cx + 14
        draw.line([(sx, head_cy - 16), (sx, base_y + 2)],
                  fill=GRAVE_SHOVEL_HANDLE, width=2)
        draw.rectangle([sx - 4, head_cy - 22, sx + 4, head_cy - 16],
                       fill=GRAVE_SHOVEL_BLADE, outline=OUTLINE)
        draw.rectangle([sx - 3, head_cy - 21, sx - 1, head_cy - 17],
                       fill=GRAVE_SHOVEL_DARK, outline=None)
    else:  # RIGHT
        sx = cx - 14
        draw.line([(sx, head_cy - 16), (sx, base_y + 2)],
                  fill=GRAVE_SHOVEL_HANDLE, width=2)
        draw.rectangle([sx - 4, head_cy - 22, sx + 4, head_cy - 16],
                       fill=GRAVE_SHOVEL_BLADE, outline=OUTLINE)
        draw.rectangle([sx + 1, head_cy - 21, sx + 3, head_cy - 17],
                       fill=GRAVE_SHOVEL_DARK, outline=None)

    # --- Legs (short, muddied boots) ---
    if direction in (DOWN, UP):
        for side in [-1, 1]:
            lx = cx + side * (6 + abs(leg_spread)) + (leg_spread if side == -1 else -leg_spread)
            draw.rectangle([lx - 3, body_cy + 6, lx + 3, base_y - 3],
                           fill=GRAVE_CLOTH_DARK, outline=OUTLINE)
            # Muddied boots
            draw.rectangle([lx - 4, base_y - 4, lx + 4, base_y],
                           fill=GRAVE_BOOT, outline=OUTLINE)
            # Mud splatter on boots
            draw.point((lx - 2, base_y - 2), fill=GRAVE_MUD)
            draw.point((lx + 2, base_y - 1), fill=GRAVE_MUD)
    elif direction == LEFT:
        for offset in [leg_spread, -leg_spread]:
            lx = cx - 2 + offset
            draw.rectangle([lx - 3, body_cy + 6, lx + 3, base_y - 3],
                           fill=GRAVE_CLOTH_DARK, outline=OUTLINE)
            draw.rectangle([lx - 4, base_y - 4, lx + 4, base_y],
                           fill=GRAVE_BOOT, outline=OUTLINE)
            draw.point((lx - 2, base_y - 2), fill=GRAVE_MUD)
    else:  # RIGHT
        for offset in [leg_spread, -leg_spread]:
            lx = cx + 2 + offset
            draw.rectangle([lx - 3, body_cy + 6, lx + 3, base_y - 3],
                           fill=GRAVE_CLOTH_DARK, outline=OUTLINE)
            draw.rectangle([lx - 4, base_y - 4, lx + 4, base_y],
                           fill=GRAVE_BOOT, outline=OUTLINE)
            draw.point((lx + 2, base_y - 2), fill=GRAVE_MUD)

    # --- Torso (hunched, dirty tattered clothing) ---
    if direction == DOWN:
        ellipse(draw, cx, body_cy, 13, 11, GRAVE_CLOTH)
        # Tattered edges
        draw.line([(cx - 12, body_cy + 4), (cx - 10, body_cy + 8)],
                  fill=GRAVE_CLOTH_DARK, width=1)
        draw.line([(cx + 10, body_cy + 4), (cx + 12, body_cy + 8)],
                  fill=GRAVE_CLOTH_DARK, width=1)
        # Dirt stains
        draw.point((cx - 4, body_cy + 2), fill=GRAVE_MUD)
        draw.point((cx + 6, body_cy - 2), fill=GRAVE_MUD)
        draw.point((cx - 2, body_cy + 4), fill=GRAVE_MUD)
        # Lantern dangling from belt
        lant_x = cx - 14 + lantern_sway
        lant_y = body_cy + 4
        draw.rectangle([lant_x - 2, lant_y, lant_x + 2, lant_y + 5],
                       fill=GRAVE_LANTERN, outline=OUTLINE)
        # Lantern glow
        ellipse(draw, lant_x, lant_y + 2, 4, 4, GRAVE_LANTERN_GLOW, outline=None)
    elif direction == UP:
        ellipse(draw, cx, body_cy, 13, 11, GRAVE_CLOTH)
        draw.line([(cx - 6, body_cy - 4), (cx - 4, body_cy + 4)],
                  fill=GRAVE_CLOTH_DARK, width=1)
        draw.line([(cx + 6, body_cy - 4), (cx + 4, body_cy + 4)],
                  fill=GRAVE_CLOTH_DARK, width=1)
        draw.point((cx, body_cy + 2), fill=GRAVE_MUD)
    elif direction == LEFT:
        ellipse(draw, cx - 2, body_cy, 11, 11, GRAVE_CLOTH)
        draw.line([(cx - 10, body_cy - 2), (cx - 8, body_cy + 6)],
                  fill=GRAVE_CLOTH_DARK, width=1)
        draw.point((cx - 6, body_cy + 2), fill=GRAVE_MUD)
        # Lantern visible on side
        lant_x = cx - 12 + lantern_sway
        lant_y = body_cy + 4
        draw.rectangle([lant_x - 2, lant_y, lant_x + 2, lant_y + 5],
                       fill=GRAVE_LANTERN, outline=OUTLINE)
        ellipse(draw, lant_x, lant_y + 2, 3, 3, GRAVE_LANTERN_GLOW, outline=None)
    else:  # RIGHT
        ellipse(draw, cx + 2, body_cy, 11, 11, GRAVE_CLOTH)
        draw.line([(cx + 10, body_cy - 2), (cx + 8, body_cy + 6)],
                  fill=GRAVE_CLOTH_DARK, width=1)
        draw.point((cx + 6, body_cy + 2), fill=GRAVE_MUD)
        lant_x = cx + 12 + lantern_sway
        lant_y = body_cy + 4
        draw.rectangle([lant_x - 2, lant_y, lant_x + 2, lant_y + 5],
                       fill=GRAVE_LANTERN, outline=OUTLINE)
        ellipse(draw, lant_x, lant_y + 2, 3, 3, GRAVE_LANTERN_GLOW, outline=None)

    # --- Arms ---
    if direction == DOWN:
        for side in [-1, 1]:
            ax = cx + side * 14
            draw.rectangle([ax - 3, body_cy - 4, ax + 3, body_cy + 4],
                           fill=GRAVE_CLOTH, outline=OUTLINE)
            # Dirty hand
            draw.rectangle([ax - 2, body_cy + 4, ax + 2, body_cy + 7],
                           fill=GRAVE_SKIN, outline=OUTLINE)
    elif direction == LEFT:
        draw.rectangle([cx - 12, body_cy - 2, cx - 6, body_cy + 5],
                       fill=GRAVE_CLOTH, outline=OUTLINE)
    elif direction == RIGHT:
        draw.rectangle([cx + 6, body_cy - 2, cx + 12, body_cy + 5],
                       fill=GRAVE_CLOTH, outline=OUTLINE)

    # --- Head (wide-brimmed hat) ---
    if direction == DOWN:
        # Face
        ellipse(draw, cx, head_cy + 2, 8, 7, GRAVE_SKIN)
        # Stubble dots
        draw.point((cx - 3, head_cy + 5), fill=GRAVE_CLOTH_DARK)
        draw.point((cx + 3, head_cy + 5), fill=GRAVE_CLOTH_DARK)
        draw.point((cx, head_cy + 6), fill=GRAVE_CLOTH_DARK)
        # Eyes (squinting)
        draw.line([(cx - 4, head_cy + 1), (cx - 2, head_cy + 1)],
                  fill=BLACK, width=1)
        draw.line([(cx + 2, head_cy + 1), (cx + 4, head_cy + 1)],
                  fill=BLACK, width=1)
        # Wide-brimmed hat
        draw.rectangle([cx - 14, head_cy - 6, cx + 14, head_cy - 4],
                       fill=GRAVE_HAT, outline=OUTLINE)
        # Hat crown
        draw.rectangle([cx - 7, head_cy - 14, cx + 7, head_cy - 6],
                       fill=GRAVE_HAT, outline=OUTLINE)
        draw.rectangle([cx - 6, head_cy - 13, cx - 3, head_cy - 7],
                       fill=GRAVE_HAT_DARK, outline=None)
    elif direction == UP:
        ellipse(draw, cx, head_cy + 2, 8, 7, GRAVE_SKIN)
        draw.rectangle([cx - 14, head_cy - 6, cx + 14, head_cy - 4],
                       fill=GRAVE_HAT, outline=OUTLINE)
        draw.rectangle([cx - 7, head_cy - 14, cx + 7, head_cy - 6],
                       fill=GRAVE_HAT, outline=OUTLINE)
        draw.rectangle([cx - 6, head_cy - 13, cx + 4, head_cy - 7],
                       fill=GRAVE_HAT_DARK, outline=None)
    elif direction == LEFT:
        ellipse(draw, cx - 2, head_cy + 2, 7, 7, GRAVE_SKIN)
        draw.line([(cx - 6, head_cy + 1), (cx - 4, head_cy + 1)],
                  fill=BLACK, width=1)
        # Hat brim extends left
        draw.rectangle([cx - 16, head_cy - 6, cx + 10, head_cy - 4],
                       fill=GRAVE_HAT, outline=OUTLINE)
        draw.rectangle([cx - 7, head_cy - 14, cx + 5, head_cy - 6],
                       fill=GRAVE_HAT, outline=OUTLINE)
        draw.rectangle([cx - 6, head_cy - 13, cx - 3, head_cy - 7],
                       fill=GRAVE_HAT_DARK, outline=None)
    else:  # RIGHT
        ellipse(draw, cx + 2, head_cy + 2, 7, 7, GRAVE_SKIN)
        draw.line([(cx + 4, head_cy + 1), (cx + 6, head_cy + 1)],
                  fill=BLACK, width=1)
        draw.rectangle([cx - 10, head_cy - 6, cx + 16, head_cy - 4],
                       fill=GRAVE_HAT, outline=OUTLINE)
        draw.rectangle([cx - 5, head_cy - 14, cx + 7, head_cy - 6],
                       fill=GRAVE_HAT, outline=OUTLINE)
        draw.rectangle([cx + 3, head_cy - 13, cx + 6, head_cy - 7],
                       fill=GRAVE_HAT_DARK, outline=None)


# ===================================================================
# DULLAHAN (ID 36) -- headless horseman body, holds own head at hip,
#                      armored body, cape, neck stump, glowing eyes
# ===================================================================

# Dullahan palette
DULL_ARMOR = (50, 45, 60)
DULL_ARMOR_DARK = (35, 30, 42)
DULL_ARMOR_LIGHT = (70, 62, 82)
DULL_SKIN = (160, 150, 140)
DULL_SKIN_DARK = (120, 112, 105)
DULL_CAPE = (40, 30, 50)
DULL_CAPE_DARK = (25, 18, 32)
DULL_EYE = (180, 220, 100)
DULL_EYE_BRIGHT = (220, 255, 140)
DULL_STUMP = (80, 50, 50)
DULL_STUMP_DARK = (55, 35, 35)
DULL_HAIR = (50, 40, 35)


def draw_dullahan(draw, ox, oy, direction, frame):
    bob = [0, -2, 0, -1][frame]
    cape_sway = [0, 2, 0, -2][frame]
    head_bob = [0, -1, 0, 1][frame]

    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 20
    # No head on neck -- neck stump at normal head position
    neck_y = body_cy - 12

    # --- Cape (behind body) ---
    if direction == DOWN:
        draw.polygon([
            (cx - 14, body_cy - 10),
            (cx + 14, body_cy - 10),
            (cx + 16 + cape_sway, base_y + 2),
            (cx - 16 + cape_sway, base_y + 2),
        ], fill=DULL_CAPE, outline=OUTLINE)
        for hx in range(cx - 14 + cape_sway, cx + 14 + cape_sway, 5):
            draw.polygon([(hx, base_y), (hx + 2, base_y + 4), (hx + 5, base_y + 1)],
                         fill=DULL_CAPE_DARK, outline=None)
    elif direction == UP:
        draw.polygon([
            (cx - 14, body_cy - 12),
            (cx + 14, body_cy - 12),
            (cx + 18 + cape_sway, base_y + 2),
            (cx - 18 + cape_sway, base_y + 2),
        ], fill=DULL_CAPE, outline=OUTLINE)
        for hx in range(cx - 16 + cape_sway, cx + 16 + cape_sway, 5):
            draw.polygon([(hx, base_y), (hx + 2, base_y + 4), (hx + 5, base_y + 1)],
                         fill=DULL_CAPE_DARK, outline=None)
    elif direction == LEFT:
        draw.polygon([
            (cx + 4, body_cy - 10),
            (cx + 16, body_cy - 8),
            (cx + 18 + cape_sway, base_y + 2),
            (cx + 4, base_y + 2),
        ], fill=DULL_CAPE, outline=OUTLINE)
    else:  # RIGHT
        draw.polygon([
            (cx - 4, body_cy - 10),
            (cx - 16, body_cy - 8),
            (cx - 18 + cape_sway, base_y + 2),
            (cx - 4, base_y + 2),
        ], fill=DULL_CAPE, outline=OUTLINE)

    # --- Legs (armored) ---
    leg_spread = [-3, 0, 3, 0][frame]
    if direction in (DOWN, UP):
        for side in [-1, 1]:
            lx = cx + side * (7 + abs(leg_spread)) + (leg_spread if side == -1 else -leg_spread)
            draw.rectangle([lx - 3, body_cy + 8, lx + 3, base_y - 3],
                           fill=DULL_ARMOR, outline=OUTLINE)
            draw.rectangle([lx - 4, base_y - 4, lx + 4, base_y],
                           fill=DULL_ARMOR_DARK, outline=OUTLINE)
    elif direction == LEFT:
        for offset in [leg_spread, -leg_spread]:
            lx = cx - 2 + offset
            draw.rectangle([lx - 3, body_cy + 8, lx + 3, base_y - 3],
                           fill=DULL_ARMOR, outline=OUTLINE)
            draw.rectangle([lx - 4, base_y - 4, lx + 4, base_y],
                           fill=DULL_ARMOR_DARK, outline=OUTLINE)
    else:  # RIGHT
        for offset in [leg_spread, -leg_spread]:
            lx = cx + 2 + offset
            draw.rectangle([lx - 3, body_cy + 8, lx + 3, base_y - 3],
                           fill=DULL_ARMOR, outline=OUTLINE)
            draw.rectangle([lx - 4, base_y - 4, lx + 4, base_y],
                           fill=DULL_ARMOR_DARK, outline=OUTLINE)

    # --- Armored torso ---
    if direction == DOWN:
        ellipse(draw, cx, body_cy, 14, 12, DULL_ARMOR)
        ellipse(draw, cx - 2, body_cy - 2, 8, 6, DULL_ARMOR_LIGHT, outline=None)
        # Armor plate lines
        draw.line([(cx - 10, body_cy), (cx + 10, body_cy)],
                  fill=DULL_ARMOR_DARK, width=1)
        draw.line([(cx - 8, body_cy + 4), (cx + 8, body_cy + 4)],
                  fill=DULL_ARMOR_DARK, width=1)
    elif direction == UP:
        ellipse(draw, cx, body_cy, 14, 12, DULL_ARMOR)
        ellipse(draw, cx, body_cy, 10, 8, DULL_ARMOR_DARK, outline=None)
    elif direction == LEFT:
        ellipse(draw, cx - 2, body_cy, 12, 12, DULL_ARMOR)
        ellipse(draw, cx - 4, body_cy - 2, 6, 6, DULL_ARMOR_LIGHT, outline=None)
    else:  # RIGHT
        ellipse(draw, cx + 2, body_cy, 12, 12, DULL_ARMOR)
        ellipse(draw, cx + 4, body_cy - 2, 6, 6, DULL_ARMOR_LIGHT, outline=None)

    # --- Arms ---
    if direction == DOWN:
        # Right arm normal
        draw.rectangle([cx + 14, body_cy - 4, cx + 20, body_cy + 6],
                       fill=DULL_ARMOR, outline=OUTLINE)
        ellipse(draw, cx + 17, body_cy - 4, 5, 3, DULL_ARMOR_LIGHT)
        # Left arm extended down holding head
        draw.rectangle([cx - 20, body_cy - 4, cx - 14, body_cy + 10],
                       fill=DULL_ARMOR, outline=OUTLINE)
        ellipse(draw, cx - 17, body_cy - 4, 5, 3, DULL_ARMOR_LIGHT)
    elif direction == UP:
        draw.rectangle([cx - 18, body_cy - 4, cx - 12, body_cy + 6],
                       fill=DULL_ARMOR, outline=OUTLINE)
        draw.rectangle([cx + 12, body_cy - 4, cx + 18, body_cy + 6],
                       fill=DULL_ARMOR, outline=OUTLINE)
    elif direction == LEFT:
        draw.rectangle([cx - 14, body_cy - 4, cx - 8, body_cy + 10],
                       fill=DULL_ARMOR, outline=OUTLINE)
        ellipse(draw, cx - 11, body_cy - 4, 5, 3, DULL_ARMOR_LIGHT)
    elif direction == RIGHT:
        draw.rectangle([cx + 8, body_cy - 4, cx + 14, body_cy + 10],
                       fill=DULL_ARMOR, outline=OUTLINE)
        ellipse(draw, cx + 11, body_cy - 4, 5, 3, DULL_ARMOR_LIGHT)

    # --- Neck stump (dark flat oval on shoulders) ---
    if direction == DOWN:
        ellipse(draw, cx, neck_y, 7, 3, DULL_STUMP)
        ellipse(draw, cx, neck_y, 5, 2, DULL_STUMP_DARK, outline=None)
    elif direction == UP:
        ellipse(draw, cx, neck_y, 7, 3, DULL_STUMP)
        ellipse(draw, cx, neck_y, 5, 2, DULL_STUMP_DARK, outline=None)
    elif direction == LEFT:
        ellipse(draw, cx - 2, neck_y, 6, 3, DULL_STUMP)
        ellipse(draw, cx - 2, neck_y, 4, 2, DULL_STUMP_DARK, outline=None)
    else:  # RIGHT
        ellipse(draw, cx + 2, neck_y, 6, 3, DULL_STUMP)
        ellipse(draw, cx + 2, neck_y, 4, 2, DULL_STUMP_DARK, outline=None)

    # --- Held head (drawn at hip level, to the side) ---
    if direction == DOWN:
        hx = cx - 18
        hy = body_cy + 10 + head_bob
        # Head shape
        ellipse(draw, hx, hy, 7, 7, DULL_SKIN)
        ellipse(draw, hx - 2, hy - 1, 4, 4, DULL_SKIN_DARK, outline=None)
        # Hair on top
        ellipse(draw, hx, hy - 5, 6, 3, DULL_HAIR, outline=None)
        # Glowing eyes
        draw.rectangle([hx - 4, hy - 1, hx - 2, hy + 1], fill=DULL_EYE)
        draw.point((hx - 3, hy), fill=DULL_EYE_BRIGHT)
        draw.rectangle([hx + 2, hy - 1, hx + 4, hy + 1], fill=DULL_EYE)
        draw.point((hx + 3, hy), fill=DULL_EYE_BRIGHT)
        # Mouth
        draw.line([(hx - 3, hy + 3), (hx + 3, hy + 3)],
                  fill=DULL_STUMP_DARK, width=1)
    elif direction == UP:
        # Head held behind, mostly hidden
        hx = cx + 16
        hy = body_cy + 10 + head_bob
        ellipse(draw, hx, hy, 7, 7, DULL_SKIN)
        ellipse(draw, hx, hy - 5, 6, 3, DULL_HAIR, outline=None)
    elif direction == LEFT:
        hx = cx - 14
        hy = body_cy + 10 + head_bob
        ellipse(draw, hx, hy, 7, 7, DULL_SKIN)
        ellipse(draw, hx - 2, hy - 1, 4, 4, DULL_SKIN_DARK, outline=None)
        ellipse(draw, hx, hy - 5, 6, 3, DULL_HAIR, outline=None)
        # One glowing eye visible
        draw.rectangle([hx - 4, hy - 1, hx - 2, hy + 1], fill=DULL_EYE)
        draw.point((hx - 3, hy), fill=DULL_EYE_BRIGHT)
        draw.line([(hx - 3, hy + 3), (hx + 2, hy + 3)],
                  fill=DULL_STUMP_DARK, width=1)
    else:  # RIGHT
        hx = cx + 14
        hy = body_cy + 10 + head_bob
        ellipse(draw, hx, hy, 7, 7, DULL_SKIN)
        ellipse(draw, hx + 2, hy - 1, 4, 4, DULL_SKIN_DARK, outline=None)
        ellipse(draw, hx, hy - 5, 6, 3, DULL_HAIR, outline=None)
        draw.rectangle([hx + 2, hy - 1, hx + 4, hy + 1], fill=DULL_EYE)
        draw.point((hx + 3, hy), fill=DULL_EYE_BRIGHT)
        draw.line([(hx - 2, hy + 3), (hx + 3, hy + 3)],
                  fill=DULL_STUMP_DARK, width=1)


# ===================================================================
# PHANTOM (ID 37) -- floating, translucent, chains wrapped around body,
#                     spectral blue glow, hooded with glowing eyes
# ===================================================================

# Phantom palette
PHAN_BODY = (140, 150, 180)
PHAN_BODY_DARK = (100, 110, 145)
PHAN_BODY_LIGHT = (170, 180, 210)
PHAN_GLOW = (100, 140, 220)
PHAN_GLOW_DIM = (70, 100, 180)
PHAN_CHAIN = (150, 150, 155)
PHAN_CHAIN_DARK = (110, 110, 115)
PHAN_EYE = (180, 220, 255)
PHAN_HOOD = (120, 130, 160)
PHAN_HOOD_DARK = (90, 98, 125)


def draw_phantom(draw, ox, oy, direction, frame):
    float_bob = [0, -3, -1, -2][frame]
    chain_sway = [-2, 0, 2, 0][frame]
    wisp_extend = [0, 2, 4, 2][frame]

    base_y = oy + 54 + float_bob
    cx = ox + 32
    body_cy = base_y - 18
    head_cy = body_cy - 16

    # Translucent colors (RGBA)
    body_t = PHAN_BODY + (160,)
    body_dark_t = PHAN_BODY_DARK + (140,)
    glow_t = PHAN_GLOW + (80,)

    # --- Spectral blue glow aura (behind everything) ---
    ellipse(draw, cx, body_cy, 20, 18, glow_t, outline=None)

    # --- Wispy trailing bottom (no legs, floating) ---
    if direction in (DOWN, UP):
        draw.polygon([
            (cx - 10, body_cy + 8),
            (cx + 10, body_cy + 8),
            (cx + 4 + chain_sway, base_y + 2 + wisp_extend),
            (cx + chain_sway, base_y + wisp_extend),
            (cx - 4 + chain_sway, base_y + 2 + wisp_extend),
        ], fill=body_dark_t, outline=None)
    elif direction == LEFT:
        draw.polygon([
            (cx - 8, body_cy + 8),
            (cx + 6, body_cy + 8),
            (cx + 2 + chain_sway, base_y + 2 + wisp_extend),
            (cx - 4 + chain_sway, base_y + wisp_extend),
        ], fill=body_dark_t, outline=None)
    else:  # RIGHT
        draw.polygon([
            (cx - 6, body_cy + 8),
            (cx + 8, body_cy + 8),
            (cx + 4 + chain_sway, base_y + wisp_extend),
            (cx - 2 + chain_sway, base_y + 2 + wisp_extend),
        ], fill=body_dark_t, outline=None)

    # --- Main body (translucent) ---
    if direction == DOWN:
        ellipse(draw, cx, body_cy, 14, 14, body_t)
        ellipse(draw, cx - 2, body_cy - 2, 10, 10, PHAN_BODY_LIGHT + (120,),
                outline=None)
        ellipse(draw, cx, body_cy + 2, 8, 8, body_dark_t, outline=None)
    elif direction == UP:
        ellipse(draw, cx, body_cy, 14, 14, body_t)
        ellipse(draw, cx, body_cy, 10, 10, body_dark_t, outline=None)
    elif direction == LEFT:
        ellipse(draw, cx - 2, body_cy, 12, 14, body_t)
        ellipse(draw, cx - 4, body_cy - 2, 8, 10, PHAN_BODY_LIGHT + (120,),
                outline=None)
    else:  # RIGHT
        ellipse(draw, cx + 2, body_cy, 12, 14, body_t)
        ellipse(draw, cx + 4, body_cy - 2, 8, 10, PHAN_BODY_LIGHT + (120,),
                outline=None)

    # --- Chains wrapped around body (small grey rectangles as links) ---
    if direction == DOWN:
        for i in range(5):
            ch_x = cx - 8 + i * 4 + chain_sway
            ch_y = body_cy - 4 + (i % 3) * 4
            draw.rectangle([ch_x - 2, ch_y - 1, ch_x + 2, ch_y + 1],
                           fill=PHAN_CHAIN, outline=PHAN_CHAIN_DARK)
        # Vertical chain segment
        for i in range(3):
            ch_y = body_cy - 2 + i * 5
            draw.rectangle([cx + 10, ch_y, cx + 12, ch_y + 3],
                           fill=PHAN_CHAIN, outline=PHAN_CHAIN_DARK)
    elif direction == UP:
        for i in range(5):
            ch_x = cx - 8 + i * 4 + chain_sway
            ch_y = body_cy - 2 + (i % 3) * 4
            draw.rectangle([ch_x - 2, ch_y - 1, ch_x + 2, ch_y + 1],
                           fill=PHAN_CHAIN, outline=PHAN_CHAIN_DARK)
    elif direction == LEFT:
        for i in range(4):
            ch_y = body_cy - 6 + i * 5
            ch_x = cx - 4 + chain_sway + (i % 2) * 3
            draw.rectangle([ch_x - 2, ch_y - 1, ch_x + 2, ch_y + 1],
                           fill=PHAN_CHAIN, outline=PHAN_CHAIN_DARK)
        # Dangling chain
        for i in range(3):
            ch_y = body_cy + 4 + i * 4
            draw.rectangle([cx - 10 + chain_sway, ch_y, cx - 8 + chain_sway, ch_y + 2],
                           fill=PHAN_CHAIN, outline=PHAN_CHAIN_DARK)
    else:  # RIGHT
        for i in range(4):
            ch_y = body_cy - 6 + i * 5
            ch_x = cx + 4 + chain_sway - (i % 2) * 3
            draw.rectangle([ch_x - 2, ch_y - 1, ch_x + 2, ch_y + 1],
                           fill=PHAN_CHAIN, outline=PHAN_CHAIN_DARK)
        for i in range(3):
            ch_y = body_cy + 4 + i * 4
            draw.rectangle([cx + 8 + chain_sway, ch_y, cx + 10 + chain_sway, ch_y + 2],
                           fill=PHAN_CHAIN, outline=PHAN_CHAIN_DARK)

    # --- Head (hooded, glowing eyes) ---
    if direction == DOWN:
        ellipse(draw, cx, head_cy, 10, 9, PHAN_HOOD + (180,))
        draw.polygon([(cx - 5, head_cy - 7), (cx, head_cy - 14),
                      (cx + 5, head_cy - 7)], fill=PHAN_HOOD + (180,), outline=OUTLINE)
        ellipse(draw, cx, head_cy + 2, 7, 5, PHAN_HOOD_DARK + (160,), outline=None)
        # Glowing eyes
        ellipse(draw, cx - 3, head_cy + 1, 2, 2, PHAN_EYE)
        ellipse(draw, cx + 3, head_cy + 1, 2, 2, PHAN_EYE)
    elif direction == UP:
        ellipse(draw, cx, head_cy, 10, 9, PHAN_HOOD + (180,))
        draw.polygon([(cx - 5, head_cy - 7), (cx, head_cy - 14),
                      (cx + 5, head_cy - 7)], fill=PHAN_HOOD + (180,), outline=OUTLINE)
        ellipse(draw, cx, head_cy, 7, 6, PHAN_HOOD_DARK + (160,), outline=None)
    elif direction == LEFT:
        ellipse(draw, cx - 2, head_cy, 9, 9, PHAN_HOOD + (180,))
        draw.polygon([(cx - 4, head_cy - 7), (cx - 2, head_cy - 14),
                      (cx + 2, head_cy - 7)], fill=PHAN_HOOD + (180,), outline=OUTLINE)
        ellipse(draw, cx - 4, head_cy + 2, 5, 4, PHAN_HOOD_DARK + (160,), outline=None)
        ellipse(draw, cx - 6, head_cy + 1, 2, 2, PHAN_EYE)
    else:  # RIGHT
        ellipse(draw, cx + 2, head_cy, 9, 9, PHAN_HOOD + (180,))
        draw.polygon([(cx - 2, head_cy - 7), (cx + 2, head_cy - 14),
                      (cx + 4, head_cy - 7)], fill=PHAN_HOOD + (180,), outline=OUTLINE)
        ellipse(draw, cx + 4, head_cy + 2, 5, 4, PHAN_HOOD_DARK + (160,), outline=None)
        ellipse(draw, cx + 6, head_cy + 1, 2, 2, PHAN_EYE)


# ===================================================================
# MUMMY (ID 38) -- bandage-wrapped body, one glowing eye, trailing
#                   bandage strips, dust particles, sandy beige
# ===================================================================

# Mummy palette
MUMMY_WRAP = (210, 195, 160)
MUMMY_WRAP_DARK = (175, 162, 130)
MUMMY_WRAP_LIGHT = (230, 218, 185)
MUMMY_SKIN = (160, 140, 110)
MUMMY_EYE = (100, 200, 255)
MUMMY_EYE_DIM = (60, 140, 200)
MUMMY_DUST = (190, 175, 140)


def draw_mummy(draw, ox, oy, direction, frame):
    bob = [0, -1, 0, -1][frame]
    leg_spread = [-2, 0, 2, 0][frame]
    bandage_sway = [-3, 0, 3, 0][frame]
    dust_offset = frame * 5  # shift dust per frame

    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 20
    head_cy = body_cy - 16

    # --- Legs (wrapped) ---
    if direction in (DOWN, UP):
        for side in [-1, 1]:
            lx = cx + side * (6 + abs(leg_spread)) + (leg_spread if side == -1 else -leg_spread)
            draw.rectangle([lx - 3, body_cy + 8, lx + 3, base_y - 2],
                           fill=MUMMY_WRAP, outline=OUTLINE)
            # Bandage criss-cross lines on legs
            for wy in range(body_cy + 9, base_y - 3, 4):
                draw.line([(lx - 3, wy), (lx + 3, wy + 3)],
                          fill=MUMMY_WRAP_DARK, width=1)
            # Trailing bandage from leg
            if side == 1:
                draw.line([(lx + 3, base_y - 4),
                           (lx + 6 + bandage_sway, base_y),
                           (lx + 8 + bandage_sway, base_y + 3)],
                          fill=MUMMY_WRAP_LIGHT, width=1)
            # Foot
            draw.rectangle([lx - 4, base_y - 3, lx + 4, base_y],
                           fill=MUMMY_WRAP_DARK, outline=OUTLINE)
    elif direction == LEFT:
        for offset in [leg_spread, -leg_spread]:
            lx = cx - 2 + offset
            draw.rectangle([lx - 3, body_cy + 8, lx + 3, base_y - 2],
                           fill=MUMMY_WRAP, outline=OUTLINE)
            for wy in range(body_cy + 9, base_y - 3, 4):
                draw.line([(lx - 3, wy), (lx + 3, wy + 3)],
                          fill=MUMMY_WRAP_DARK, width=1)
            draw.rectangle([lx - 4, base_y - 3, lx + 4, base_y],
                           fill=MUMMY_WRAP_DARK, outline=OUTLINE)
    else:  # RIGHT
        for offset in [leg_spread, -leg_spread]:
            lx = cx + 2 + offset
            draw.rectangle([lx - 3, body_cy + 8, lx + 3, base_y - 2],
                           fill=MUMMY_WRAP, outline=OUTLINE)
            for wy in range(body_cy + 9, base_y - 3, 4):
                draw.line([(lx - 3, wy), (lx + 3, wy + 3)],
                          fill=MUMMY_WRAP_DARK, width=1)
            draw.rectangle([lx - 4, base_y - 3, lx + 4, base_y],
                           fill=MUMMY_WRAP_DARK, outline=OUTLINE)

    # --- Torso (bandage-wrapped) ---
    if direction == DOWN:
        ellipse(draw, cx, body_cy, 13, 12, MUMMY_WRAP)
        # Criss-cross bandage lines over torso
        for wy in range(-8, 8, 4):
            draw.line([(cx - 10, body_cy + wy), (cx + 10, body_cy + wy + 4)],
                      fill=MUMMY_WRAP_DARK, width=1)
            draw.line([(cx + 10, body_cy + wy), (cx - 10, body_cy + wy + 4)],
                      fill=MUMMY_WRAP_DARK, width=1)
        # Skin peeking through gaps
        draw.point((cx + 4, body_cy + 2), fill=MUMMY_SKIN)
        draw.point((cx - 6, body_cy - 2), fill=MUMMY_SKIN)
    elif direction == UP:
        ellipse(draw, cx, body_cy, 13, 12, MUMMY_WRAP)
        for wy in range(-8, 8, 4):
            draw.line([(cx - 10, body_cy + wy), (cx + 10, body_cy + wy + 4)],
                      fill=MUMMY_WRAP_DARK, width=1)
    elif direction == LEFT:
        ellipse(draw, cx - 2, body_cy, 11, 12, MUMMY_WRAP)
        for wy in range(-8, 8, 4):
            draw.line([(cx - 10, body_cy + wy), (cx + 6, body_cy + wy + 4)],
                      fill=MUMMY_WRAP_DARK, width=1)
        draw.point((cx - 4, body_cy), fill=MUMMY_SKIN)
    else:  # RIGHT
        ellipse(draw, cx + 2, body_cy, 11, 12, MUMMY_WRAP)
        for wy in range(-8, 8, 4):
            draw.line([(cx - 6, body_cy + wy), (cx + 10, body_cy + wy + 4)],
                      fill=MUMMY_WRAP_DARK, width=1)
        draw.point((cx + 4, body_cy), fill=MUMMY_SKIN)

    # --- Arms (with trailing bandage strips) ---
    if direction == DOWN:
        for side in [-1, 1]:
            ax = cx + side * 15
            draw.rectangle([ax - 3, body_cy - 4, ax + 3, body_cy + 6],
                           fill=MUMMY_WRAP, outline=OUTLINE)
            # Bandage lines on arm
            draw.line([(ax - 3, body_cy - 2), (ax + 3, body_cy + 2)],
                      fill=MUMMY_WRAP_DARK, width=1)
            # Trailing bandage strip from arm
            draw.line([(ax + side * 3, body_cy + 4),
                       (ax + side * (6 + abs(bandage_sway)), body_cy + 8),
                       (ax + side * (8 + abs(bandage_sway)), body_cy + 12)],
                      fill=MUMMY_WRAP_LIGHT, width=1)
    elif direction == LEFT:
        draw.rectangle([cx - 14, body_cy - 2, cx - 8, body_cy + 6],
                       fill=MUMMY_WRAP, outline=OUTLINE)
        draw.line([(cx - 14, body_cy), (cx - 8, body_cy + 4)],
                  fill=MUMMY_WRAP_DARK, width=1)
        # Trailing strip
        draw.line([(cx - 14, body_cy + 4),
                   (cx - 18 + bandage_sway, body_cy + 8),
                   (cx - 20 + bandage_sway, body_cy + 12)],
                  fill=MUMMY_WRAP_LIGHT, width=1)
    elif direction == RIGHT:
        draw.rectangle([cx + 8, body_cy - 2, cx + 14, body_cy + 6],
                       fill=MUMMY_WRAP, outline=OUTLINE)
        draw.line([(cx + 8, body_cy), (cx + 14, body_cy + 4)],
                  fill=MUMMY_WRAP_DARK, width=1)
        draw.line([(cx + 14, body_cy + 4),
                   (cx + 18 + bandage_sway, body_cy + 8),
                   (cx + 20 + bandage_sway, body_cy + 12)],
                  fill=MUMMY_WRAP_LIGHT, width=1)

    # --- Head (bandage-wrapped, one glowing eye) ---
    if direction == DOWN:
        ellipse(draw, cx, head_cy, 10, 9, MUMMY_WRAP)
        # Bandage lines criss-crossing head
        draw.line([(cx - 8, head_cy - 2), (cx + 8, head_cy + 2)],
                  fill=MUMMY_WRAP_DARK, width=1)
        draw.line([(cx + 6, head_cy - 4), (cx - 6, head_cy + 4)],
                  fill=MUMMY_WRAP_DARK, width=1)
        draw.line([(cx - 8, head_cy - 6), (cx + 8, head_cy - 2)],
                  fill=MUMMY_WRAP_DARK, width=1)
        # One glowing eye (left eye visible)
        ellipse(draw, cx - 3, head_cy, 3, 2, MUMMY_EYE)
        ellipse(draw, cx - 3, head_cy, 2, 1, MUMMY_EYE_DIM, outline=None)
        # Right eye covered by bandage (just a dark line)
        draw.line([(cx + 2, head_cy - 1), (cx + 5, head_cy + 1)],
                  fill=MUMMY_WRAP_DARK, width=2)
    elif direction == UP:
        ellipse(draw, cx, head_cy, 10, 9, MUMMY_WRAP)
        draw.line([(cx - 8, head_cy - 2), (cx + 8, head_cy + 2)],
                  fill=MUMMY_WRAP_DARK, width=1)
        draw.line([(cx + 6, head_cy - 4), (cx - 6, head_cy + 4)],
                  fill=MUMMY_WRAP_DARK, width=1)
        draw.line([(cx - 6, head_cy - 6), (cx + 6, head_cy - 2)],
                  fill=MUMMY_WRAP_DARK, width=1)
    elif direction == LEFT:
        ellipse(draw, cx - 2, head_cy, 9, 9, MUMMY_WRAP)
        draw.line([(cx - 8, head_cy - 2), (cx + 4, head_cy + 2)],
                  fill=MUMMY_WRAP_DARK, width=1)
        draw.line([(cx + 2, head_cy - 6), (cx - 6, head_cy + 2)],
                  fill=MUMMY_WRAP_DARK, width=1)
        # Glowing eye
        ellipse(draw, cx - 5, head_cy, 3, 2, MUMMY_EYE)
        ellipse(draw, cx - 5, head_cy, 2, 1, MUMMY_EYE_DIM, outline=None)
    else:  # RIGHT
        ellipse(draw, cx + 2, head_cy, 9, 9, MUMMY_WRAP)
        draw.line([(cx - 4, head_cy - 2), (cx + 8, head_cy + 2)],
                  fill=MUMMY_WRAP_DARK, width=1)
        draw.line([(cx - 2, head_cy - 6), (cx + 6, head_cy + 2)],
                  fill=MUMMY_WRAP_DARK, width=1)
        # Glowing eye
        ellipse(draw, cx + 5, head_cy, 3, 2, MUMMY_EYE)
        ellipse(draw, cx + 5, head_cy, 2, 1, MUMMY_EYE_DIM, outline=None)

    # --- Dust particles (shift per frame) ---
    dust_positions = [
        (cx - 12, body_cy + 10), (cx + 14, body_cy + 6),
        (cx - 8, body_cy - 8), (cx + 10, body_cy + 14),
        (cx - 16, body_cy + 2), (cx + 6, body_cy - 12),
    ]
    for i, (dx, dy) in enumerate(dust_positions):
        # Only show 3-4 dust particles per frame, cycling through them
        if (i + frame) % 3 != 0:
            continue
        px = dx + (dust_offset % 7) - 3
        py = dy + (dust_offset % 5) - 2
        draw.point((px, py), fill=MUMMY_DUST)
        draw.point((px + 1, py), fill=MUMMY_DUST)


# ===================================================================
# DEATHKNIGHT (ID 39) -- dark plate armor, skull motifs, red glowing
#                         visor, dark cape, imposing stance, sword
# ===================================================================

# Deathknight palette
DK_ARMOR = (50, 50, 60)
DK_ARMOR_DARK = (35, 35, 42)
DK_ARMOR_LIGHT = (70, 70, 82)
DK_RED = (200, 30, 30)
DK_RED_DIM = (140, 20, 20)
DK_CAPE = (35, 25, 35)
DK_CAPE_DARK = (22, 16, 22)
DK_SWORD = (160, 160, 170)
DK_SWORD_DARK = (110, 110, 120)
DK_SKULL_DOT = (80, 80, 90)


def draw_deathknight(draw, ox, oy, direction, frame):
    bob = [0, -2, 0, -1][frame]
    leg_spread = [-3, 0, 3, 0][frame]
    cape_sway = [0, 2, 0, -2][frame]

    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 20
    head_cy = body_cy - 18

    # --- Dark cape (behind body) ---
    if direction == DOWN:
        draw.polygon([
            (cx - 14, body_cy - 10),
            (cx + 14, body_cy - 10),
            (cx + 18 + cape_sway, base_y + 2),
            (cx - 18 + cape_sway, base_y + 2),
        ], fill=DK_CAPE, outline=OUTLINE)
        for hx in range(cx - 16 + cape_sway, cx + 16 + cape_sway, 5):
            draw.polygon([(hx, base_y), (hx + 2, base_y + 4), (hx + 5, base_y + 1)],
                         fill=DK_CAPE_DARK, outline=None)
    elif direction == UP:
        draw.polygon([
            (cx - 14, body_cy - 12),
            (cx + 14, body_cy - 12),
            (cx + 20 + cape_sway, base_y + 2),
            (cx - 20 + cape_sway, base_y + 2),
        ], fill=DK_CAPE, outline=OUTLINE)
        for hx in range(cx - 18 + cape_sway, cx + 18 + cape_sway, 5):
            draw.polygon([(hx, base_y), (hx + 2, base_y + 4), (hx + 5, base_y + 1)],
                         fill=DK_CAPE_DARK, outline=None)
    elif direction == LEFT:
        draw.polygon([
            (cx + 4, body_cy - 10),
            (cx + 18, body_cy - 8),
            (cx + 20 + cape_sway, base_y + 2),
            (cx + 4, base_y + 2),
        ], fill=DK_CAPE, outline=OUTLINE)
    else:  # RIGHT
        draw.polygon([
            (cx - 4, body_cy - 10),
            (cx - 18, body_cy - 8),
            (cx - 20 + cape_sway, base_y + 2),
            (cx - 4, base_y + 2),
        ], fill=DK_CAPE, outline=OUTLINE)

    # --- Sword prop ---
    if direction == DOWN:
        sw_x = cx + 20
        # Handle
        draw.line([(sw_x, body_cy + 6), (sw_x, body_cy - 2)],
                  fill=DK_ARMOR_DARK, width=2)
        # Cross guard
        draw.line([(sw_x - 4, body_cy - 2), (sw_x + 4, body_cy - 2)],
                  fill=DK_RED_DIM, width=2)
        # Blade
        draw.rectangle([sw_x - 1, body_cy - 22, sw_x + 1, body_cy - 2],
                       fill=DK_SWORD, outline=OUTLINE)
        # Blade tip
        draw.polygon([(sw_x - 1, body_cy - 22), (sw_x, body_cy - 26),
                      (sw_x + 1, body_cy - 22)], fill=DK_SWORD, outline=OUTLINE)
        # Dark fuller line
        draw.line([(sw_x, body_cy - 20), (sw_x, body_cy - 4)],
                  fill=DK_SWORD_DARK, width=1)
    elif direction == LEFT:
        sw_x = cx + 16
        draw.line([(sw_x, body_cy + 6), (sw_x, body_cy - 2)],
                  fill=DK_ARMOR_DARK, width=2)
        draw.line([(sw_x - 4, body_cy - 2), (sw_x + 4, body_cy - 2)],
                  fill=DK_RED_DIM, width=2)
        draw.rectangle([sw_x - 1, body_cy - 22, sw_x + 1, body_cy - 2],
                       fill=DK_SWORD, outline=OUTLINE)
        draw.polygon([(sw_x - 1, body_cy - 22), (sw_x, body_cy - 26),
                      (sw_x + 1, body_cy - 22)], fill=DK_SWORD, outline=OUTLINE)
    elif direction == RIGHT:
        sw_x = cx - 16
        draw.line([(sw_x, body_cy + 6), (sw_x, body_cy - 2)],
                  fill=DK_ARMOR_DARK, width=2)
        draw.line([(sw_x - 4, body_cy - 2), (sw_x + 4, body_cy - 2)],
                  fill=DK_RED_DIM, width=2)
        draw.rectangle([sw_x - 1, body_cy - 22, sw_x + 1, body_cy - 2],
                       fill=DK_SWORD, outline=OUTLINE)
        draw.polygon([(sw_x - 1, body_cy - 22), (sw_x, body_cy - 26),
                      (sw_x + 1, body_cy - 22)], fill=DK_SWORD, outline=OUTLINE)

    # --- Legs (imposing wide stance, dark plate) ---
    if direction in (DOWN, UP):
        for side in [-1, 1]:
            lx = cx + side * (8 + abs(leg_spread)) + (leg_spread if side == -1 else -leg_spread)
            draw.rectangle([lx - 4, body_cy + 8, lx + 4, base_y - 3],
                           fill=DK_ARMOR, outline=OUTLINE)
            if direction == DOWN:
                draw.rectangle([lx - 4, body_cy + 8, lx - 2, base_y - 6],
                               fill=DK_ARMOR_LIGHT, outline=None)
            # Heavy boots
            draw.rectangle([lx - 5, base_y - 4, lx + 5, base_y],
                           fill=DK_ARMOR_DARK, outline=OUTLINE)
    elif direction == LEFT:
        for offset in [leg_spread, -leg_spread]:
            lx = cx - 2 + offset
            draw.rectangle([lx - 4, body_cy + 8, lx + 4, base_y - 3],
                           fill=DK_ARMOR, outline=OUTLINE)
            draw.rectangle([lx - 5, base_y - 4, lx + 5, base_y],
                           fill=DK_ARMOR_DARK, outline=OUTLINE)
    else:  # RIGHT
        for offset in [leg_spread, -leg_spread]:
            lx = cx + 2 + offset
            draw.rectangle([lx - 4, body_cy + 8, lx + 4, base_y - 3],
                           fill=DK_ARMOR, outline=OUTLINE)
            draw.rectangle([lx - 5, base_y - 4, lx + 5, base_y],
                           fill=DK_ARMOR_DARK, outline=OUTLINE)

    # --- Armored torso (with skull motifs) ---
    if direction == DOWN:
        ellipse(draw, cx, body_cy, 15, 13, DK_ARMOR)
        ellipse(draw, cx - 2, body_cy - 2, 10, 8, DK_ARMOR_LIGHT, outline=None)
        ellipse(draw, cx + 2, body_cy + 2, 10, 8, DK_ARMOR_DARK, outline=None)
        # Skull motif dots on chest plate
        draw.point((cx - 3, body_cy - 4), fill=DK_SKULL_DOT)
        draw.point((cx + 3, body_cy - 4), fill=DK_SKULL_DOT)
        draw.point((cx, body_cy - 2), fill=DK_SKULL_DOT)
        draw.point((cx - 1, body_cy), fill=DK_SKULL_DOT)
        draw.point((cx + 1, body_cy), fill=DK_SKULL_DOT)
        # Center line
        draw.line([(cx, body_cy - 8), (cx, body_cy + 8)],
                  fill=DK_ARMOR_DARK, width=1)
    elif direction == UP:
        ellipse(draw, cx, body_cy, 15, 13, DK_ARMOR)
        ellipse(draw, cx, body_cy, 11, 9, DK_ARMOR_DARK, outline=None)
        draw.line([(cx, body_cy - 8), (cx, body_cy + 8)],
                  fill=DK_ARMOR_LIGHT, width=1)
    elif direction == LEFT:
        ellipse(draw, cx - 2, body_cy, 13, 13, DK_ARMOR)
        ellipse(draw, cx - 4, body_cy - 2, 8, 8, DK_ARMOR_LIGHT, outline=None)
        # Skull dots visible from side
        draw.point((cx - 6, body_cy - 2), fill=DK_SKULL_DOT)
        draw.point((cx - 4, body_cy), fill=DK_SKULL_DOT)
    else:  # RIGHT
        ellipse(draw, cx + 2, body_cy, 13, 13, DK_ARMOR)
        ellipse(draw, cx + 4, body_cy - 2, 8, 8, DK_ARMOR_LIGHT, outline=None)
        draw.point((cx + 6, body_cy - 2), fill=DK_SKULL_DOT)
        draw.point((cx + 4, body_cy), fill=DK_SKULL_DOT)

    # --- Arms (heavy pauldrons) ---
    if direction == DOWN:
        for side in [-1, 1]:
            ax = cx + side * 17
            draw.rectangle([ax - 4, body_cy - 6, ax + 4, body_cy + 6],
                           fill=DK_ARMOR, outline=OUTLINE)
            # Large shoulder pauldron
            ellipse(draw, ax, body_cy - 6, 6, 4, DK_ARMOR_LIGHT)
            # Gauntlet
            draw.rectangle([ax - 3, body_cy + 3, ax + 3, body_cy + 7],
                           fill=DK_ARMOR_DARK, outline=OUTLINE)
    elif direction == LEFT:
        draw.rectangle([cx - 16, body_cy - 4, cx - 10, body_cy + 6],
                       fill=DK_ARMOR, outline=OUTLINE)
        ellipse(draw, cx - 13, body_cy - 4, 6, 4, DK_ARMOR_LIGHT)
    elif direction == RIGHT:
        draw.rectangle([cx + 10, body_cy - 4, cx + 16, body_cy + 6],
                       fill=DK_ARMOR, outline=OUTLINE)
        ellipse(draw, cx + 13, body_cy - 4, 6, 4, DK_ARMOR_LIGHT)

    # --- Head (dark helm with red glowing visor slit) ---
    if direction == DOWN:
        ellipse(draw, cx, head_cy, 11, 11, DK_ARMOR)
        ellipse(draw, cx - 2, head_cy - 2, 7, 7, DK_ARMOR_LIGHT, outline=None)
        # Visor slit (glowing red)
        draw.rectangle([cx - 7, head_cy - 1, cx + 7, head_cy + 1],
                       fill=DK_RED, outline=OUTLINE)
        # Red glow from visor
        draw.rectangle([cx - 5, head_cy - 1, cx - 3, head_cy + 1],
                       fill=DK_RED, outline=None)
        draw.rectangle([cx + 3, head_cy - 1, cx + 5, head_cy + 1],
                       fill=DK_RED, outline=None)
        # Helm crest
        draw.rectangle([cx - 1, head_cy - 14, cx + 1, head_cy - 7],
                       fill=DK_ARMOR, outline=OUTLINE)
        # Skull motif on forehead
        draw.point((cx - 2, head_cy - 5), fill=DK_SKULL_DOT)
        draw.point((cx + 2, head_cy - 5), fill=DK_SKULL_DOT)
        draw.point((cx, head_cy - 3), fill=DK_SKULL_DOT)
    elif direction == UP:
        ellipse(draw, cx, head_cy, 11, 11, DK_ARMOR)
        ellipse(draw, cx, head_cy, 8, 8, DK_ARMOR_DARK, outline=None)
        draw.rectangle([cx - 1, head_cy - 14, cx + 1, head_cy - 7],
                       fill=DK_ARMOR, outline=OUTLINE)
    elif direction == LEFT:
        ellipse(draw, cx - 2, head_cy, 10, 11, DK_ARMOR)
        ellipse(draw, cx - 4, head_cy - 2, 6, 7, DK_ARMOR_LIGHT, outline=None)
        # Visor slit
        draw.rectangle([cx - 10, head_cy - 1, cx + 2, head_cy + 1],
                       fill=DK_RED, outline=OUTLINE)
        draw.rectangle([cx - 1, head_cy - 14, cx + 1, head_cy - 7],
                       fill=DK_ARMOR, outline=OUTLINE)
    else:  # RIGHT
        ellipse(draw, cx + 2, head_cy, 10, 11, DK_ARMOR)
        ellipse(draw, cx + 4, head_cy - 2, 6, 7, DK_ARMOR_LIGHT, outline=None)
        draw.rectangle([cx - 2, head_cy - 1, cx + 10, head_cy + 1],
                       fill=DK_RED, outline=OUTLINE)
        draw.rectangle([cx - 1, head_cy - 14, cx + 1, head_cy - 7],
                       fill=DK_ARMOR, outline=OUTLINE)


# ===================================================================
# SHADOWFIEND (ID 40) -- floating, amorphous dark shape, multiple eyes,
#                          shadow tendrils all directions, shifts shape
# ===================================================================

# Shadowfiend palette
SF_BODY = (60, 30, 60)
SF_BODY_DARK = (40, 18, 40)
SF_BODY_LIGHT = (80, 45, 80)
SF_EYE = (200, 50, 200)
SF_EYE_BRIGHT = (240, 100, 240)
SF_TENDRIL = (50, 25, 55)
SF_TENDRIL_DARK = (35, 15, 38)


def draw_shadowfiend(draw, ox, oy, direction, frame):
    float_bob = [0, -3, -1, -2][frame]
    tendril_sway = [-3, 0, 3, 0][frame]
    shape_shift = [-2, 0, 2, 1][frame]

    base_y = oy + 54 + float_bob
    cx = ox + 32
    body_cy = base_y - 16
    head_cy = body_cy - 14

    # --- Shadow tendrils extending in all directions ---
    tendril_angles = [0, math.pi / 3, 2 * math.pi / 3, math.pi,
                      4 * math.pi / 3, 5 * math.pi / 3]
    for i, angle in enumerate(tendril_angles):
        t_len = 14 + (i % 3) * 4
        sway_factor = tendril_sway * (0.5 + (i % 2) * 0.5)
        start_x = cx + int(math.cos(angle) * 8)
        start_y = body_cy + int(math.sin(angle) * 6)
        end_x = cx + int(math.cos(angle + sway_factor * 0.05) * t_len)
        end_y = body_cy + int(math.sin(angle + sway_factor * 0.05) * (t_len * 0.7))
        tc = SF_TENDRIL if i % 2 == 0 else SF_TENDRIL_DARK
        draw.line([(start_x, start_y), (end_x, end_y)], fill=tc, width=2)
        # Tapered end
        draw.line([(end_x, end_y),
                   (end_x + int(sway_factor), end_y + 3)],
                  fill=SF_TENDRIL_DARK, width=1)

    # --- Amorphous body (irregular shape via polygon, shifts per frame) ---
    if direction == DOWN:
        pts = [
            (cx - 12 + shape_shift, body_cy - 10),
            (cx - 6 - shape_shift, body_cy - 14 + abs(shape_shift)),
            (cx + 4 + shape_shift, body_cy - 12),
            (cx + 14 - shape_shift, body_cy - 6),
            (cx + 12 + shape_shift, body_cy + 8),
            (cx + 6 - shape_shift, body_cy + 14),
            (cx - 4 + shape_shift, body_cy + 12),
            (cx - 14 - shape_shift, body_cy + 6),
        ]
        draw.polygon(pts, fill=SF_BODY, outline=OUTLINE)
        # Darker core
        ellipse(draw, cx + shape_shift, body_cy, 8, 8, SF_BODY_DARK, outline=None)
    elif direction == UP:
        pts = [
            (cx - 14 - shape_shift, body_cy - 8),
            (cx - 4 + shape_shift, body_cy - 14),
            (cx + 6 - shape_shift, body_cy - 12 + abs(shape_shift)),
            (cx + 12 + shape_shift, body_cy - 4),
            (cx + 14 - shape_shift, body_cy + 8),
            (cx + 4 + shape_shift, body_cy + 12),
            (cx - 6 - shape_shift, body_cy + 14),
            (cx - 12 + shape_shift, body_cy + 4),
        ]
        draw.polygon(pts, fill=SF_BODY, outline=OUTLINE)
        ellipse(draw, cx - shape_shift, body_cy, 8, 8, SF_BODY_DARK, outline=None)
    elif direction == LEFT:
        pts = [
            (cx - 14 + shape_shift, body_cy - 8),
            (cx - 8 - shape_shift, body_cy - 14),
            (cx + 2 + shape_shift, body_cy - 10),
            (cx + 10 - shape_shift, body_cy - 4),
            (cx + 12 + shape_shift, body_cy + 6),
            (cx + 4 - shape_shift, body_cy + 14),
            (cx - 6 + shape_shift, body_cy + 12),
            (cx - 14 - shape_shift, body_cy + 4),
        ]
        draw.polygon(pts, fill=SF_BODY, outline=OUTLINE)
        ellipse(draw, cx - 2 + shape_shift, body_cy, 7, 7, SF_BODY_DARK, outline=None)
    else:  # RIGHT
        pts = [
            (cx - 10 - shape_shift, body_cy - 4),
            (cx - 2 + shape_shift, body_cy - 14),
            (cx + 8 - shape_shift, body_cy - 10),
            (cx + 14 + shape_shift, body_cy - 6),
            (cx + 14 - shape_shift, body_cy + 6),
            (cx + 6 + shape_shift, body_cy + 14),
            (cx - 4 - shape_shift, body_cy + 12),
            (cx - 12 + shape_shift, body_cy + 4),
        ]
        draw.polygon(pts, fill=SF_BODY, outline=OUTLINE)
        ellipse(draw, cx + 2 - shape_shift, body_cy, 7, 7, SF_BODY_DARK, outline=None)

    # --- Wispy bottom (tapers into nothing, floating) ---
    draw.polygon([
        (cx - 8, body_cy + 10),
        (cx + 8, body_cy + 10),
        (cx + 2 + tendril_sway, base_y + 4),
        (cx + tendril_sway, base_y + 2),
        (cx - 2 + tendril_sway, base_y + 4),
    ], fill=SF_BODY, outline=None)

    # --- Multiple eyes (4-6 glowing dots scattered on body) ---
    # Eye positions shift with shape_shift for organic feel
    if direction == DOWN:
        eye_positions = [
            (cx - 6 + shape_shift, body_cy - 6),
            (cx + 4 - shape_shift, body_cy - 4),
            (cx - 2, body_cy + 2 + shape_shift),
            (cx + 6 + shape_shift, body_cy + 4),
            (cx - 8, body_cy + shape_shift),
            (cx + 2 - shape_shift, body_cy - 8),
        ]
    elif direction == UP:
        eye_positions = [
            (cx - 4 - shape_shift, body_cy - 4),
            (cx + 6 + shape_shift, body_cy - 6),
            (cx + 2, body_cy + 2),
            (cx - 6 + shape_shift, body_cy + 4),
        ]
    elif direction == LEFT:
        eye_positions = [
            (cx - 8 + shape_shift, body_cy - 4),
            (cx - 4, body_cy - 8),
            (cx - 6 - shape_shift, body_cy + 2),
            (cx + 2 + shape_shift, body_cy),
            (cx - 2, body_cy + 6),
        ]
    else:  # RIGHT
        eye_positions = [
            (cx + 8 - shape_shift, body_cy - 4),
            (cx + 4, body_cy - 8),
            (cx + 6 + shape_shift, body_cy + 2),
            (cx - 2 - shape_shift, body_cy),
            (cx + 2, body_cy + 6),
        ]

    for ex, ey in eye_positions:
        ellipse(draw, ex, ey, 2, 2, SF_EYE, outline=None)
        ellipse(draw, ex, ey, 1, 1, SF_EYE_BRIGHT, outline=None)


# ===================================================================
# POLTERGEIST (ID 41) -- floating, nearly invisible body, orbiting
#                         debris, flickering face, very translucent
# ===================================================================

# Poltergeist palette
POLTER_BODY = (160, 180, 200)
POLTER_BODY_DARK = (130, 148, 168)
POLTER_EYE = (180, 200, 240)
POLTER_DEBRIS_1 = (180, 120, 80)   # wooden debris
POLTER_DEBRIS_2 = (140, 140, 150)  # stone debris
POLTER_DEBRIS_3 = (100, 160, 100)  # green debris


def draw_poltergeist(draw, ox, oy, direction, frame):
    float_bob = [0, -3, -1, -2][frame]
    debris_angle_base = frame * (math.pi / 2)  # 90 degrees per frame

    base_y = oy + 54 + float_bob
    cx = ox + 32
    body_cy = base_y - 16
    head_cy = body_cy - 14

    # Very low alpha for nearly invisible body
    body_alpha = 50
    body_t = POLTER_BODY + (body_alpha,)
    body_dark_t = POLTER_BODY_DARK + (40,)
    outline_t = (40, 35, 35, 60)

    # --- Faint wispy bottom ---
    draw.polygon([
        (cx - 6, body_cy + 8),
        (cx + 6, body_cy + 8),
        (cx + 2, base_y + 2),
        (cx, base_y),
        (cx - 2, base_y + 2),
    ], fill=body_dark_t, outline=None)

    # --- Nearly invisible body (very faint outline) ---
    if direction == DOWN:
        ellipse(draw, cx, body_cy, 12, 12, body_t, outline=outline_t)
        ellipse(draw, cx, body_cy, 8, 8, body_dark_t, outline=None)
    elif direction == UP:
        ellipse(draw, cx, body_cy, 12, 12, body_t, outline=outline_t)
        ellipse(draw, cx, body_cy, 8, 8, body_dark_t, outline=None)
    elif direction == LEFT:
        ellipse(draw, cx - 2, body_cy, 10, 12, body_t, outline=outline_t)
        ellipse(draw, cx - 2, body_cy, 6, 8, body_dark_t, outline=None)
    else:  # RIGHT
        ellipse(draw, cx + 2, body_cy, 10, 12, body_t, outline=outline_t)
        ellipse(draw, cx + 2, body_cy, 6, 8, body_dark_t, outline=None)

    # --- Head (faint, translucent) ---
    head_t = POLTER_BODY + (55,)
    head_dark_t = POLTER_BODY_DARK + (45,)

    if direction == DOWN:
        ellipse(draw, cx, head_cy, 8, 8, head_t, outline=outline_t)
        ellipse(draw, cx, head_cy, 5, 5, head_dark_t, outline=None)
        # Flickering face -- only appears on frames 0 and 2
        if frame % 2 == 0:
            draw.rectangle([cx - 3, head_cy - 1, cx - 1, head_cy + 1],
                           fill=POLTER_EYE + (180,))
            draw.rectangle([cx + 1, head_cy - 1, cx + 3, head_cy + 1],
                           fill=POLTER_EYE + (180,))
            # Faint mouth
            draw.line([(cx - 2, head_cy + 3), (cx + 2, head_cy + 3)],
                      fill=POLTER_BODY_DARK + (100,), width=1)
    elif direction == UP:
        ellipse(draw, cx, head_cy, 8, 8, head_t, outline=outline_t)
        ellipse(draw, cx, head_cy, 5, 5, head_dark_t, outline=None)
    elif direction == LEFT:
        ellipse(draw, cx - 2, head_cy, 7, 8, head_t, outline=outline_t)
        ellipse(draw, cx - 2, head_cy, 4, 5, head_dark_t, outline=None)
        if frame % 2 == 0:
            draw.rectangle([cx - 5, head_cy - 1, cx - 3, head_cy + 1],
                           fill=POLTER_EYE + (180,))
    else:  # RIGHT
        ellipse(draw, cx + 2, head_cy, 7, 8, head_t, outline=outline_t)
        ellipse(draw, cx + 2, head_cy, 4, 5, head_dark_t, outline=None)
        if frame % 2 == 0:
            draw.rectangle([cx + 3, head_cy - 1, cx + 5, head_cy + 1],
                           fill=POLTER_EYE + (180,))

    # --- Orbiting debris objects (2-3 small colored shapes rotating per frame) ---
    debris_radius = 20
    debris_items = [
        (POLTER_DEBRIS_1, 3, 4),   # wooden chunk (width, height)
        (POLTER_DEBRIS_2, 4, 3),   # stone chunk
        (POLTER_DEBRIS_3, 3, 3),   # green chunk
    ]
    for i, (color, dw, dh) in enumerate(debris_items):
        angle = debris_angle_base + i * (2 * math.pi / 3)
        dx = int(cx + math.cos(angle) * debris_radius)
        dy = int(body_cy + math.sin(angle) * (debris_radius * 0.6))
        # Rectangular debris piece
        draw.rectangle([dx - dw // 2, dy - dh // 2,
                        dx + dw // 2, dy + dh // 2],
                       fill=color, outline=OUTLINE)
        # Small triangular chip near each debris
        chip_angle = angle + 0.4
        chip_x = int(cx + math.cos(chip_angle) * (debris_radius - 5))
        chip_y = int(body_cy + math.sin(chip_angle) * ((debris_radius - 5) * 0.6))
        draw.polygon([
            (chip_x, chip_y - 2),
            (chip_x - 2, chip_y + 1),
            (chip_x + 2, chip_y + 1),
        ], fill=color, outline=None)


# ===================================================================
# REGISTRY & MAIN
# ===================================================================

UNDEAD_DRAW_FUNCTIONS = {
    'necromancer': draw_necromancer,
    'skeletonking': draw_skeletonking,
    'banshee': draw_banshee,
    'lich': draw_lich,
    'ghoul': draw_ghoul,
    'reaper': draw_reaper,
    'shade': draw_shade,
    'revenant': draw_revenant,
    'gravedigger': draw_gravedigger,
    'dullahan': draw_dullahan,
    'phantom': draw_phantom,
    'mummy': draw_mummy,
    'deathknight': draw_deathknight,
    'shadowfiend': draw_shadowfiend,
    'poltergeist': draw_poltergeist,
}


def main():
    for name, draw_func in UNDEAD_DRAW_FUNCTIONS.items():
        generate_character(name, draw_func=draw_func)
    print(f"\nGenerated {len(UNDEAD_DRAW_FUNCTIONS)} undead character sprites.")


if __name__ == "__main__":
    main()
