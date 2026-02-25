#!/usr/bin/env python3
"""Beast/Nature character sprite generators (IDs 72-86).

15 characters with unique body shapes replacing the generic humanoid template.
Each draw function produces a visually distinct silhouette per creature.
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
# Shared constants
# ---------------------------------------------------------------------------

# Wolf palette
WOLF_BODY = (120, 110, 100)
WOLF_LIGHT = (155, 145, 130)
WOLF_DARK = (85, 75, 65)
WOLF_BELLY = (160, 150, 135)
WOLF_EYE = (200, 180, 50)
WOLF_NOSE = (30, 25, 25)
WOLF_INNER_EAR = (150, 110, 100)

# Serpent palette
SERP_BODY = (50, 120, 50)
SERP_SCALE = (80, 150, 60)
SERP_BELLY = (120, 180, 100)
SERP_DARK = (30, 80, 30)
SERP_HOOD = (60, 140, 55)
SERP_HOOD_EDGE = (40, 100, 40)
SERP_EYE = (200, 200, 50)
SERP_TONGUE = (180, 40, 40)

# Spider palette
SPIDER_BODY = (50, 40, 50)
SPIDER_ABDOMEN = (60, 45, 55)
SPIDER_LEG = (40, 30, 40)
SPIDER_LIGHT = (80, 65, 80)
SPIDER_MARK = (180, 30, 30)
SPIDER_EYE = (180, 40, 40)
SPIDER_FANG = (200, 200, 190)

# Bear palette
BEAR_BODY = (130, 90, 50)
BEAR_LIGHT = (165, 120, 75)
BEAR_BELLY = (180, 150, 110)
BEAR_DARK = (95, 65, 35)
BEAR_NOSE = (30, 25, 25)
BEAR_INNER_EAR = (160, 110, 90)
BEAR_EYE = (35, 30, 25)
BEAR_CLAW = (60, 50, 40)

# Scorpion palette
SCORP_BODY = (100, 50, 30)
SCORP_ARMOR = (120, 60, 35)
SCORP_STINGER = (80, 30, 20)
SCORP_DARK = (70, 35, 20)
SCORP_LIGHT = (145, 80, 50)
SCORP_PINCER = (110, 55, 30)
SCORP_EYE = (30, 30, 25)


# ===================================================================
# WOLF (ID 72) — upright beast biped with muzzle, pointed ears, tail
# ===================================================================

def draw_wolf(draw, ox, oy, direction, frame):
    bob = [0, -2, 0, -1][frame]
    leg_spread = [-3, 0, 3, 0][frame]
    tail_sway = [-3, 0, 3, 0][frame]

    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 20
    head_cy = body_cy - 18

    body_shadow = _darken(WOLF_BODY, 0.75)

    # --- Tail (drawn behind body) ---
    if direction == DOWN:
        # Tail visible poking out to the side
        draw.polygon([
            (cx + 10, body_cy + 4),
            (cx + 20 + tail_sway, body_cy - 6),
            (cx + 22 + tail_sway, body_cy - 4),
            (cx + 12, body_cy + 6),
        ], fill=WOLF_BODY, outline=OUTLINE)
        # Tail tip
        ellipse(draw, cx + 21 + tail_sway, body_cy - 5, 3, 3, WOLF_LIGHT)
    elif direction == UP:
        # Tail hangs down center
        draw.polygon([
            (cx - 2, body_cy + 8),
            (cx + 2, body_cy + 8),
            (cx + 4 + tail_sway, base_y + 2),
            (cx - 4 + tail_sway, base_y + 4),
        ], fill=WOLF_BODY, outline=OUTLINE)
        ellipse(draw, cx + tail_sway, base_y + 3, 4, 3, WOLF_LIGHT)
    elif direction == LEFT:
        draw.polygon([
            (cx + 8, body_cy + 2),
            (cx + 18 + tail_sway, body_cy - 8),
            (cx + 20 + tail_sway, body_cy - 4),
            (cx + 10, body_cy + 6),
        ], fill=WOLF_BODY, outline=OUTLINE)
        ellipse(draw, cx + 19 + tail_sway, body_cy - 6, 3, 3, WOLF_LIGHT)
    else:  # RIGHT
        draw.polygon([
            (cx - 8, body_cy + 2),
            (cx - 18 - tail_sway, body_cy - 8),
            (cx - 20 - tail_sway, body_cy - 4),
            (cx - 10, body_cy + 6),
        ], fill=WOLF_BODY, outline=OUTLINE)
        ellipse(draw, cx - 19 - tail_sway, body_cy - 6, 3, 3, WOLF_LIGHT)

    # --- Legs (digitigrade — ankle higher, paws smaller) ---
    if direction in (DOWN, UP):
        for side in [-1, 1]:
            lx = cx + side * (7 + abs(leg_spread)) + (leg_spread if side == -1 else -leg_spread)
            # Upper leg
            draw.rectangle([lx - 3, body_cy + 8, lx + 3, body_cy + 18],
                           fill=WOLF_BODY, outline=OUTLINE)
            # Lower leg (thinner)
            draw.rectangle([lx - 2, body_cy + 16, lx + 2, base_y - 3],
                           fill=WOLF_DARK, outline=OUTLINE)
            # Paw
            ellipse(draw, lx, base_y - 1, 4, 3, WOLF_DARK)
            # Claw dots
            draw.point((lx - 2, base_y + 1), fill=BLACK)
            draw.point((lx + 2, base_y + 1), fill=BLACK)
    elif direction == LEFT:
        for offset in [leg_spread, -leg_spread]:
            lx = cx - 2 + offset
            draw.rectangle([lx - 3, body_cy + 8, lx + 3, body_cy + 18],
                           fill=WOLF_BODY, outline=OUTLINE)
            draw.rectangle([lx - 2, body_cy + 16, lx + 2, base_y - 3],
                           fill=WOLF_DARK, outline=OUTLINE)
            ellipse(draw, lx, base_y - 1, 4, 3, WOLF_DARK)
            draw.point((lx - 2, base_y + 1), fill=BLACK)
    else:  # RIGHT
        for offset in [leg_spread, -leg_spread]:
            lx = cx + 2 + offset
            draw.rectangle([lx - 3, body_cy + 8, lx + 3, body_cy + 18],
                           fill=WOLF_BODY, outline=OUTLINE)
            draw.rectangle([lx - 2, body_cy + 16, lx + 2, base_y - 3],
                           fill=WOLF_DARK, outline=OUTLINE)
            ellipse(draw, lx, base_y - 1, 4, 3, WOLF_DARK)
            draw.point((lx + 2, base_y + 1), fill=BLACK)

    # --- Body ---
    if direction == DOWN:
        ellipse(draw, cx, body_cy, 14, 12, WOLF_BODY)
        ellipse(draw, cx + 3, body_cy + 2, 10, 8, body_shadow, outline=None)
        ellipse(draw, cx, body_cy, 11, 9, WOLF_BODY, outline=None)
        ellipse(draw, cx - 2, body_cy - 2, 8, 6, WOLF_LIGHT, outline=None)
        # Belly patch
        ellipse(draw, cx, body_cy + 4, 7, 5, WOLF_BELLY, outline=None)
        draw_fur_texture(draw, cx, body_cy, 18, 14, WOLF_BODY, density=4)
    elif direction == UP:
        ellipse(draw, cx, body_cy, 14, 12, WOLF_BODY)
        ellipse(draw, cx, body_cy, 11, 9, WOLF_DARK, outline=None)
        draw_fur_texture(draw, cx, body_cy, 18, 14, WOLF_DARK, density=4)
    elif direction == LEFT:
        ellipse(draw, cx - 2, body_cy, 12, 12, WOLF_BODY)
        ellipse(draw, cx + 2, body_cy + 2, 8, 8, body_shadow, outline=None)
        ellipse(draw, cx - 2, body_cy, 9, 9, WOLF_BODY, outline=None)
        ellipse(draw, cx - 4, body_cy - 2, 6, 6, WOLF_LIGHT, outline=None)
        draw_fur_texture(draw, cx, body_cy, 14, 14, WOLF_BODY, density=4)
    else:  # RIGHT
        ellipse(draw, cx + 2, body_cy, 12, 12, WOLF_BODY)
        ellipse(draw, cx + 6, body_cy + 2, 8, 8, body_shadow, outline=None)
        ellipse(draw, cx + 2, body_cy, 9, 9, WOLF_BODY, outline=None)
        ellipse(draw, cx, body_cy - 2, 6, 6, WOLF_LIGHT, outline=None)
        draw_fur_texture(draw, cx + 2, body_cy, 14, 14, WOLF_BODY, density=4)

    # --- Head ---
    if direction == DOWN:
        ellipse(draw, cx, head_cy, 12, 10, WOLF_BODY)
        ellipse(draw, cx - 2, head_cy - 2, 8, 6, WOLF_LIGHT, outline=None)
        # Muzzle
        ellipse(draw, cx, head_cy + 6, 6, 5, WOLF_BELLY)
        draw.point((cx, head_cy + 4), fill=WOLF_NOSE)
        draw.point((cx - 1, head_cy + 4), fill=WOLF_NOSE)
        draw.point((cx + 1, head_cy + 4), fill=WOLF_NOSE)
        # Eyes
        draw.rectangle([cx - 7, head_cy - 1, cx - 3, head_cy + 2], fill=WOLF_EYE)
        draw.point((cx - 5, head_cy), fill=BLACK)
        draw.rectangle([cx + 3, head_cy - 1, cx + 7, head_cy + 2], fill=WOLF_EYE)
        draw.point((cx + 5, head_cy), fill=BLACK)
        # Pointed ears
        draw.polygon([(cx - 8, head_cy - 6), (cx - 12, head_cy - 18),
                      (cx - 4, head_cy - 8)], fill=WOLF_BODY, outline=OUTLINE)
        draw.polygon([(cx - 7, head_cy - 8), (cx - 11, head_cy - 16),
                      (cx - 5, head_cy - 10)], fill=WOLF_INNER_EAR, outline=None)
        draw.polygon([(cx + 8, head_cy - 6), (cx + 12, head_cy - 18),
                      (cx + 4, head_cy - 8)], fill=WOLF_BODY, outline=OUTLINE)
        draw.polygon([(cx + 7, head_cy - 8), (cx + 11, head_cy - 16),
                      (cx + 5, head_cy - 10)], fill=WOLF_INNER_EAR, outline=None)
    elif direction == UP:
        ellipse(draw, cx, head_cy, 12, 10, WOLF_BODY)
        ellipse(draw, cx, head_cy, 9, 7, WOLF_DARK, outline=None)
        # Ears
        draw.polygon([(cx - 8, head_cy - 6), (cx - 12, head_cy - 18),
                      (cx - 4, head_cy - 8)], fill=WOLF_BODY, outline=OUTLINE)
        draw.polygon([(cx + 8, head_cy - 6), (cx + 12, head_cy - 18),
                      (cx + 4, head_cy - 8)], fill=WOLF_BODY, outline=OUTLINE)
    elif direction == LEFT:
        ellipse(draw, cx - 2, head_cy, 11, 10, WOLF_BODY)
        ellipse(draw, cx - 4, head_cy - 2, 7, 6, WOLF_LIGHT, outline=None)
        # Muzzle extends left
        draw.polygon([(cx - 10, head_cy + 2), (cx - 18, head_cy + 4),
                      (cx - 16, head_cy + 6), (cx - 10, head_cy + 6)],
                     fill=WOLF_BELLY, outline=OUTLINE)
        draw.point((cx - 17, head_cy + 4), fill=WOLF_NOSE)
        draw.point((cx - 17, head_cy + 5), fill=WOLF_NOSE)
        # Eye
        draw.rectangle([cx - 8, head_cy - 1, cx - 4, head_cy + 2], fill=WOLF_EYE)
        draw.point((cx - 6, head_cy), fill=BLACK)
        # Ear
        draw.polygon([(cx - 4, head_cy - 6), (cx - 8, head_cy - 18),
                      (cx, head_cy - 8)], fill=WOLF_BODY, outline=OUTLINE)
        draw.polygon([(cx - 3, head_cy - 8), (cx - 7, head_cy - 16),
                      (cx - 1, head_cy - 10)], fill=WOLF_INNER_EAR, outline=None)
    else:  # RIGHT
        ellipse(draw, cx + 2, head_cy, 11, 10, WOLF_BODY)
        ellipse(draw, cx + 4, head_cy - 2, 7, 6, WOLF_LIGHT, outline=None)
        # Muzzle extends right
        draw.polygon([(cx + 10, head_cy + 2), (cx + 18, head_cy + 4),
                      (cx + 16, head_cy + 6), (cx + 10, head_cy + 6)],
                     fill=WOLF_BELLY, outline=OUTLINE)
        draw.point((cx + 17, head_cy + 4), fill=WOLF_NOSE)
        draw.point((cx + 17, head_cy + 5), fill=WOLF_NOSE)
        # Eye
        draw.rectangle([cx + 4, head_cy - 1, cx + 8, head_cy + 2], fill=WOLF_EYE)
        draw.point((cx + 6, head_cy), fill=BLACK)
        # Ear
        draw.polygon([(cx + 4, head_cy - 6), (cx + 8, head_cy - 18),
                      (cx, head_cy - 8)], fill=WOLF_BODY, outline=OUTLINE)
        draw.polygon([(cx + 3, head_cy - 8), (cx + 7, head_cy - 16),
                      (cx + 1, head_cy - 10)], fill=WOLF_INNER_EAR, outline=None)


# ===================================================================
# SERPENT (ID 73) — coiled S-curve body, cobra hood, forked tongue
# ===================================================================

def draw_serpent(draw, ox, oy, direction, frame):
    bob = [0, -1, 0, -1][frame]
    # Slithering body phase shifts per frame
    curve_shift = [-2, 0, 2, 0][frame]
    tongue_out = frame % 2 == 0  # tongue flicks every other frame

    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 14
    head_cy = body_cy - 22

    body_dark = _darken(SERP_BODY, 0.7)

    if direction == DOWN:
        # --- Coiled body: S-curve using overlapping ellipses ---
        # Lower coil (on ground)
        ellipse(draw, cx + 4 + curve_shift, base_y - 4, 12, 5, SERP_BODY)
        draw_scale_texture(draw, cx + 4 + curve_shift, base_y - 4, 16, 6, SERP_SCALE)
        # Middle coil
        ellipse(draw, cx - 4 - curve_shift, body_cy + 4, 10, 6, SERP_BODY)
        ellipse(draw, cx - 4 - curve_shift, body_cy + 4, 7, 4, SERP_BELLY, outline=None)
        draw_scale_texture(draw, cx - 4 - curve_shift, body_cy + 4, 12, 8, SERP_SCALE)
        # Upper coil / neck
        ellipse(draw, cx + curve_shift, body_cy - 6, 6, 8, SERP_BODY)
        ellipse(draw, cx + curve_shift, body_cy - 6, 4, 6, SERP_BELLY, outline=None)

        # --- Head ---
        ellipse(draw, cx, head_cy, 10, 8, SERP_BODY)
        ellipse(draw, cx - 2, head_cy - 2, 6, 4, _brighten(SERP_BODY, 1.2), outline=None)
        # Cobra hood fans out
        draw.polygon([
            (cx - 16, head_cy + 2),
            (cx - 10, head_cy - 8),
            (cx, head_cy - 10),
            (cx + 10, head_cy - 8),
            (cx + 16, head_cy + 2),
            (cx + 12, head_cy + 6),
            (cx - 12, head_cy + 6),
        ], fill=SERP_HOOD, outline=OUTLINE)
        # Hood pattern (V-marks)
        draw.polygon([(cx, head_cy - 6), (cx - 4, head_cy),
                      (cx, head_cy + 2), (cx + 4, head_cy)],
                     fill=SERP_HOOD_EDGE, outline=None)
        # Head over hood
        ellipse(draw, cx, head_cy, 7, 6, SERP_BODY)
        # Eyes (slit pupils)
        draw.rectangle([cx - 5, head_cy - 2, cx - 2, head_cy + 1], fill=SERP_EYE)
        draw.line([(cx - 3, head_cy - 2), (cx - 3, head_cy + 1)], fill=BLACK, width=1)
        draw.rectangle([cx + 2, head_cy - 2, cx + 5, head_cy + 1], fill=SERP_EYE)
        draw.line([(cx + 3, head_cy - 2), (cx + 3, head_cy + 1)], fill=BLACK, width=1)
        # Forked tongue
        if tongue_out:
            draw.line([(cx, head_cy + 6), (cx, head_cy + 12)], fill=SERP_TONGUE, width=1)
            draw.line([(cx, head_cy + 12), (cx - 2, head_cy + 14)], fill=SERP_TONGUE, width=1)
            draw.line([(cx, head_cy + 12), (cx + 2, head_cy + 14)], fill=SERP_TONGUE, width=1)

    elif direction == UP:
        # Lower coil
        ellipse(draw, cx - 4 + curve_shift, base_y - 4, 12, 5, SERP_BODY)
        draw_scale_texture(draw, cx - 4 + curve_shift, base_y - 4, 16, 6, SERP_SCALE)
        # Middle coil
        ellipse(draw, cx + 4 - curve_shift, body_cy + 4, 10, 6, SERP_BODY)
        draw_scale_texture(draw, cx + 4 - curve_shift, body_cy + 4, 12, 8, SERP_SCALE)
        # Upper coil / neck
        ellipse(draw, cx - curve_shift, body_cy - 6, 6, 8, SERP_BODY)

        # Head (back view, hood narrower)
        ellipse(draw, cx, head_cy, 10, 8, SERP_BODY)
        # Cobra hood (back view, flatter)
        draw.polygon([
            (cx - 12, head_cy + 2),
            (cx - 8, head_cy - 6),
            (cx, head_cy - 8),
            (cx + 8, head_cy - 6),
            (cx + 12, head_cy + 2),
            (cx + 8, head_cy + 4),
            (cx - 8, head_cy + 4),
        ], fill=SERP_HOOD, outline=OUTLINE)
        ellipse(draw, cx, head_cy, 7, 6, SERP_DARK)
        draw_scale_texture(draw, cx, head_cy, 10, 8, SERP_SCALE)

    elif direction == LEFT:
        # Body stretches horizontally in S-curve
        # Rear coil
        ellipse(draw, cx + 10 - curve_shift, base_y - 6, 8, 6, SERP_BODY)
        draw_scale_texture(draw, cx + 10 - curve_shift, base_y - 6, 10, 8, SERP_SCALE)
        # Mid section
        ellipse(draw, cx + curve_shift, body_cy + 2, 8, 5, SERP_BODY)
        ellipse(draw, cx + curve_shift, body_cy + 2, 5, 3, SERP_BELLY, outline=None)
        # Neck
        draw.polygon([
            (cx - 4, body_cy),
            (cx - 10, head_cy + 6),
            (cx - 8, head_cy + 4),
            (cx - 2, body_cy - 2),
        ], fill=SERP_BODY, outline=OUTLINE)

        # Head facing left
        ellipse(draw, cx - 12, head_cy, 8, 7, SERP_BODY)
        # Cobra hood (side view, narrower)
        draw.polygon([
            (cx - 12, head_cy - 10),
            (cx - 6, head_cy - 4),
            (cx - 6, head_cy + 6),
            (cx - 12, head_cy + 10),
            (cx - 16, head_cy + 4),
            (cx - 16, head_cy - 4),
        ], fill=SERP_HOOD, outline=OUTLINE)
        ellipse(draw, cx - 12, head_cy, 6, 5, SERP_BODY)
        # Eye
        draw.rectangle([cx - 16, head_cy - 2, cx - 13, head_cy + 1], fill=SERP_EYE)
        draw.line([(cx - 14, head_cy - 2), (cx - 14, head_cy + 1)], fill=BLACK, width=1)
        # Tongue
        if tongue_out:
            draw.line([(cx - 18, head_cy + 2), (cx - 24, head_cy + 2)],
                      fill=SERP_TONGUE, width=1)
            draw.line([(cx - 24, head_cy + 2), (cx - 26, head_cy)],
                      fill=SERP_TONGUE, width=1)
            draw.line([(cx - 24, head_cy + 2), (cx - 26, head_cy + 4)],
                      fill=SERP_TONGUE, width=1)

    else:  # RIGHT
        # Rear coil
        ellipse(draw, cx - 10 + curve_shift, base_y - 6, 8, 6, SERP_BODY)
        draw_scale_texture(draw, cx - 10 + curve_shift, base_y - 6, 10, 8, SERP_SCALE)
        # Mid section
        ellipse(draw, cx - curve_shift, body_cy + 2, 8, 5, SERP_BODY)
        ellipse(draw, cx - curve_shift, body_cy + 2, 5, 3, SERP_BELLY, outline=None)
        # Neck
        draw.polygon([
            (cx + 4, body_cy),
            (cx + 10, head_cy + 6),
            (cx + 8, head_cy + 4),
            (cx + 2, body_cy - 2),
        ], fill=SERP_BODY, outline=OUTLINE)

        # Head facing right
        ellipse(draw, cx + 12, head_cy, 8, 7, SERP_BODY)
        # Hood
        draw.polygon([
            (cx + 12, head_cy - 10),
            (cx + 6, head_cy - 4),
            (cx + 6, head_cy + 6),
            (cx + 12, head_cy + 10),
            (cx + 16, head_cy + 4),
            (cx + 16, head_cy - 4),
        ], fill=SERP_HOOD, outline=OUTLINE)
        ellipse(draw, cx + 12, head_cy, 6, 5, SERP_BODY)
        # Eye
        draw.rectangle([cx + 13, head_cy - 2, cx + 16, head_cy + 1], fill=SERP_EYE)
        draw.line([(cx + 14, head_cy - 2), (cx + 14, head_cy + 1)], fill=BLACK, width=1)
        # Tongue
        if tongue_out:
            draw.line([(cx + 18, head_cy + 2), (cx + 24, head_cy + 2)],
                      fill=SERP_TONGUE, width=1)
            draw.line([(cx + 24, head_cy + 2), (cx + 26, head_cy)],
                      fill=SERP_TONGUE, width=1)
            draw.line([(cx + 24, head_cy + 2), (cx + 26, head_cy + 4)],
                      fill=SERP_TONGUE, width=1)


# ===================================================================
# SPIDER (ID 74) — round abdomen, 8 legs, multiple eyes, no humanoid
# ===================================================================

def draw_spider(draw, ox, oy, direction, frame):
    bob = [0, -1, 0, -1][frame]
    # Legs alternate in two groups: group A moves forward when group B moves back
    leg_phase_a = [-3, 0, 3, 0][frame]
    leg_phase_b = [3, 0, -3, 0][frame]

    base_y = oy + 54 + bob
    cx = ox + 32
    # Spider body sits low — abdomen is the main mass
    abdomen_cy = base_y - 10
    ceph_cy = abdomen_cy - 12  # cephalothorax (head section)

    abd_light = _brighten(SPIDER_ABDOMEN, 1.2)
    abd_dark = _darken(SPIDER_ABDOMEN, 0.7)

    def draw_legs_front_back(leg_cx, cy):
        """Draw 8 legs — 4 per side, angled out and down."""
        # Leg angles from cephalothorax, not abdomen
        # Group A: legs 1,3 on left; legs 2,4 on right
        # Group B: legs 2,4 on left; legs 1,3 on right
        leg_defs = [
            # (side, angle_x_base, angle_y_end, group)
            (-1, 14, 6, 'A'),   # left front leg
            (-1, 18, 2, 'B'),   # left mid-front
            (-1, 16, -4, 'A'),  # left mid-rear
            (-1, 10, -8, 'B'),  # left rear
            (+1, 14, 6, 'B'),   # right front
            (+1, 18, 2, 'A'),   # right mid-front
            (+1, 16, -4, 'B'),  # right mid-rear
            (+1, 10, -8, 'A'),  # right rear
        ]
        for side, ax, ay, group in leg_defs:
            phase = leg_phase_a if group == 'A' else leg_phase_b
            # Knee joint midpoint
            knee_x = leg_cx + side * (ax - 2)
            knee_y = cy + ay - 4
            # Foot endpoint
            foot_x = leg_cx + side * (ax + 4) + phase * side
            foot_y = base_y + 1
            # Upper leg segment (body to knee)
            draw.line([(leg_cx + side * 4, cy), (knee_x, knee_y)],
                      fill=SPIDER_LEG, width=2)
            # Lower leg segment (knee to foot)
            draw.line([(knee_x, knee_y), (foot_x, foot_y)],
                      fill=SPIDER_LEG, width=2)
            # Joint dot
            draw.point((knee_x, knee_y), fill=SPIDER_LIGHT)

    def draw_legs_side(leg_cx, cy, facing_left):
        """Draw legs for side view — all 8 visible, fanning out."""
        d = -1 if facing_left else 1
        # 4 legs on near side (prominent), 4 on far side (shorter)
        near_legs = [
            (8, -6, 'A'), (14, -2, 'B'), (16, 4, 'A'), (12, 8, 'B'),
        ]
        far_legs = [
            (6, -8, 'B'), (10, -4, 'A'), (12, 2, 'B'), (8, 6, 'A'),
        ]
        # Draw far legs first (behind body)
        for ax, ay, group in far_legs:
            phase = leg_phase_a if group == 'A' else leg_phase_b
            knee_x = leg_cx + d * (ax - 4)
            knee_y = cy + ay - 2
            foot_x = leg_cx + d * (ax + 2) + phase * d
            foot_y = base_y + 1
            draw.line([(leg_cx + d * 2, cy + ay // 2), (knee_x, knee_y)],
                      fill=_darken(SPIDER_LEG, 0.8), width=1)
            draw.line([(knee_x, knee_y), (foot_x, foot_y)],
                      fill=_darken(SPIDER_LEG, 0.8), width=1)
        # Near legs (in front)
        for ax, ay, group in near_legs:
            phase = leg_phase_a if group == 'A' else leg_phase_b
            knee_x = leg_cx + d * ax
            knee_y = cy + ay - 4
            foot_x = leg_cx + d * (ax + 6) + phase * d
            foot_y = base_y + 1
            draw.line([(leg_cx + d * 4, cy + ay // 2), (knee_x, knee_y)],
                      fill=SPIDER_LEG, width=2)
            draw.line([(knee_x, knee_y), (foot_x, foot_y)],
                      fill=SPIDER_LEG, width=2)
            draw.point((knee_x, knee_y), fill=SPIDER_LIGHT)

    if direction == DOWN:
        draw_legs_front_back(cx, ceph_cy)
        # Abdomen (large, round)
        ellipse(draw, cx, abdomen_cy, 14, 10, SPIDER_ABDOMEN)
        ellipse(draw, cx - 2, abdomen_cy - 2, 10, 6, abd_light, outline=None)
        ellipse(draw, cx + 3, abdomen_cy + 3, 8, 5, abd_dark, outline=None)
        # Red hourglass marking
        draw.polygon([(cx - 3, abdomen_cy - 2), (cx, abdomen_cy - 5),
                      (cx + 3, abdomen_cy - 2), (cx, abdomen_cy + 1)],
                     fill=SPIDER_MARK, outline=None)
        draw.polygon([(cx - 3, abdomen_cy + 2), (cx, abdomen_cy - 1),
                      (cx + 3, abdomen_cy + 2), (cx, abdomen_cy + 5)],
                     fill=SPIDER_MARK, outline=None)
        # Cephalothorax (smaller, in front)
        ellipse(draw, cx, ceph_cy, 8, 6, SPIDER_BODY)
        ellipse(draw, cx - 1, ceph_cy - 1, 5, 3, SPIDER_LIGHT, outline=None)
        # 8 eyes — cluster of small dots
        for ex, ey in [(-4, -3), (-2, -4), (0, -4), (2, -4), (4, -3),
                       (-3, -1), (0, -2), (3, -1)]:
            draw.point((cx + ex, ceph_cy + ey), fill=SPIDER_EYE)
        # Fangs
        draw.line([(cx - 3, ceph_cy + 4), (cx - 4, ceph_cy + 8)],
                  fill=SPIDER_FANG, width=2)
        draw.line([(cx + 3, ceph_cy + 4), (cx + 4, ceph_cy + 8)],
                  fill=SPIDER_FANG, width=2)

    elif direction == UP:
        draw_legs_front_back(cx, ceph_cy)
        # Abdomen
        ellipse(draw, cx, abdomen_cy, 14, 10, SPIDER_ABDOMEN)
        ellipse(draw, cx, abdomen_cy, 10, 7, abd_dark, outline=None)
        # Spinnerets at rear
        draw.point((cx - 2, abdomen_cy + 9), fill=SPIDER_LIGHT)
        draw.point((cx + 2, abdomen_cy + 9), fill=SPIDER_LIGHT)
        # Cephalothorax
        ellipse(draw, cx, ceph_cy, 8, 6, SPIDER_BODY)
        ellipse(draw, cx, ceph_cy, 5, 3, _darken(SPIDER_BODY, 0.8), outline=None)

    elif direction == LEFT:
        draw_legs_side(cx, ceph_cy, facing_left=True)
        # Abdomen (shifted slightly right to show profile)
        ellipse(draw, cx + 4, abdomen_cy, 13, 10, SPIDER_ABDOMEN)
        ellipse(draw, cx + 2, abdomen_cy - 2, 9, 6, abd_light, outline=None)
        ellipse(draw, cx + 6, abdomen_cy + 3, 7, 5, abd_dark, outline=None)
        # Red marking (side view — partial)
        draw.polygon([(cx + 2, abdomen_cy - 2), (cx + 4, abdomen_cy - 5),
                      (cx + 6, abdomen_cy - 2), (cx + 4, abdomen_cy + 3)],
                     fill=SPIDER_MARK, outline=None)
        # Cephalothorax
        ellipse(draw, cx - 6, ceph_cy, 7, 6, SPIDER_BODY)
        ellipse(draw, cx - 7, ceph_cy - 1, 4, 3, SPIDER_LIGHT, outline=None)
        # Eyes (side cluster)
        for ex, ey in [(-4, -3), (-3, -4), (-1, -3), (-4, -1)]:
            draw.point((cx - 6 + ex, ceph_cy + ey), fill=SPIDER_EYE)
        # Fangs
        draw.line([(cx - 10, ceph_cy + 3), (cx - 13, ceph_cy + 7)],
                  fill=SPIDER_FANG, width=2)

    else:  # RIGHT
        draw_legs_side(cx, ceph_cy, facing_left=False)
        # Abdomen
        ellipse(draw, cx - 4, abdomen_cy, 13, 10, SPIDER_ABDOMEN)
        ellipse(draw, cx - 6, abdomen_cy - 2, 9, 6, abd_light, outline=None)
        ellipse(draw, cx - 2, abdomen_cy + 3, 7, 5, abd_dark, outline=None)
        # Red marking
        draw.polygon([(cx - 6, abdomen_cy - 2), (cx - 4, abdomen_cy - 5),
                      (cx - 2, abdomen_cy - 2), (cx - 4, abdomen_cy + 3)],
                     fill=SPIDER_MARK, outline=None)
        # Cephalothorax
        ellipse(draw, cx + 6, ceph_cy, 7, 6, SPIDER_BODY)
        ellipse(draw, cx + 7, ceph_cy - 1, 4, 3, SPIDER_LIGHT, outline=None)
        # Eyes
        for ex, ey in [(4, -3), (3, -4), (1, -3), (4, -1)]:
            draw.point((cx + 6 + ex, ceph_cy + ey), fill=SPIDER_EYE)
        # Fangs
        draw.line([(cx + 10, ceph_cy + 3), (cx + 13, ceph_cy + 7)],
                  fill=SPIDER_FANG, width=2)


# ===================================================================
# BEAR (ID 75) — very wide body, small ears, thick paws, lumbering
# ===================================================================

def draw_bear(draw, ox, oy, direction, frame):
    bob = [0, -1, 0, -1][frame]
    # Body sways side to side for lumbering walk
    sway = [-2, 0, 2, 0][frame]
    leg_spread = [-3, 0, 3, 0][frame]

    base_y = oy + 54 + bob
    cx = ox + 32 + sway
    body_cy = base_y - 18
    head_cy = body_cy - 16

    body_shadow = _darken(BEAR_BODY, 0.7)

    # --- Legs (short, thick, wide stance) ---
    if direction in (DOWN, UP):
        for side in [-1, 1]:
            lx = cx + side * 10 + (leg_spread if side == -1 else -leg_spread)
            # Thick leg
            draw.rectangle([lx - 5, body_cy + 10, lx + 5, base_y - 2],
                           fill=BEAR_BODY, outline=OUTLINE)
            if direction == DOWN:
                draw.rectangle([lx - 5, body_cy + 10, lx - 3, base_y - 6],
                               fill=BEAR_LIGHT, outline=None)
            # Paw
            ellipse(draw, lx, base_y, 6, 3, BEAR_DARK)
            # Claw marks
            for c in [-3, 0, 3]:
                draw.point((lx + c, base_y + 2), fill=BEAR_CLAW)
    elif direction == LEFT:
        for offset in [leg_spread, -leg_spread]:
            lx = cx - 2 + offset
            draw.rectangle([lx - 5, body_cy + 10, lx + 5, base_y - 2],
                           fill=BEAR_BODY, outline=OUTLINE)
            ellipse(draw, lx, base_y, 6, 3, BEAR_DARK)
            for c in [-3, 0, 3]:
                draw.point((lx + c, base_y + 2), fill=BEAR_CLAW)
    else:  # RIGHT
        for offset in [leg_spread, -leg_spread]:
            lx = cx + 2 + offset
            draw.rectangle([lx - 5, body_cy + 10, lx + 5, base_y - 2],
                           fill=BEAR_BODY, outline=OUTLINE)
            ellipse(draw, lx, base_y, 6, 3, BEAR_DARK)
            for c in [-3, 0, 3]:
                draw.point((lx + c, base_y + 2), fill=BEAR_CLAW)

    # --- Body (very wide) ---
    if direction == DOWN:
        ellipse(draw, cx, body_cy, 18, 14, BEAR_BODY)
        ellipse(draw, cx + 4, body_cy + 3, 14, 10, body_shadow, outline=None)
        ellipse(draw, cx, body_cy, 15, 11, BEAR_BODY, outline=None)
        ellipse(draw, cx - 3, body_cy - 3, 10, 7, BEAR_LIGHT, outline=None)
        # Belly patch
        ellipse(draw, cx, body_cy + 4, 10, 6, BEAR_BELLY, outline=None)
        draw_fur_texture(draw, cx, body_cy, 24, 18, BEAR_BODY, density=4)
        # Arms / thick forelimbs at sides
        draw.rectangle([cx - 22, body_cy - 6, cx - 16, body_cy + 8],
                       fill=BEAR_BODY, outline=OUTLINE)
        draw.rectangle([cx - 22, body_cy - 6, cx - 20, body_cy + 4],
                       fill=BEAR_LIGHT, outline=None)
        ellipse(draw, cx - 19, body_cy + 10, 4, 3, BEAR_DARK)
        for c in [-2, 0, 2]:
            draw.point((cx - 19 + c, body_cy + 12), fill=BEAR_CLAW)
        draw.rectangle([cx + 16, body_cy - 6, cx + 22, body_cy + 8],
                       fill=BEAR_BODY, outline=OUTLINE)
        ellipse(draw, cx + 19, body_cy + 10, 4, 3, BEAR_DARK)
        for c in [-2, 0, 2]:
            draw.point((cx + 19 + c, body_cy + 12), fill=BEAR_CLAW)
    elif direction == UP:
        ellipse(draw, cx, body_cy, 18, 14, BEAR_BODY)
        ellipse(draw, cx, body_cy, 15, 11, BEAR_DARK, outline=None)
        draw_fur_texture(draw, cx, body_cy, 24, 18, BEAR_DARK, density=4)
        # Arms
        draw.rectangle([cx - 22, body_cy - 6, cx - 16, body_cy + 8],
                       fill=BEAR_BODY, outline=OUTLINE)
        draw.rectangle([cx + 16, body_cy - 6, cx + 22, body_cy + 8],
                       fill=BEAR_BODY, outline=OUTLINE)
    elif direction == LEFT:
        ellipse(draw, cx - 2, body_cy, 16, 14, BEAR_BODY)
        ellipse(draw, cx + 2, body_cy + 3, 12, 10, body_shadow, outline=None)
        ellipse(draw, cx - 2, body_cy, 13, 11, BEAR_BODY, outline=None)
        ellipse(draw, cx - 4, body_cy - 3, 8, 6, BEAR_LIGHT, outline=None)
        draw_fur_texture(draw, cx, body_cy, 20, 18, BEAR_BODY, density=4)
        # Forelimb
        draw.rectangle([cx - 16, body_cy - 4, cx - 10, body_cy + 8],
                       fill=BEAR_BODY, outline=OUTLINE)
        draw.rectangle([cx - 16, body_cy - 4, cx - 14, body_cy + 4],
                       fill=BEAR_LIGHT, outline=None)
        ellipse(draw, cx - 13, body_cy + 10, 4, 3, BEAR_DARK)
        for c in [-2, 0, 2]:
            draw.point((cx - 13 + c, body_cy + 12), fill=BEAR_CLAW)
    else:  # RIGHT
        ellipse(draw, cx + 2, body_cy, 16, 14, BEAR_BODY)
        ellipse(draw, cx + 6, body_cy + 3, 12, 10, body_shadow, outline=None)
        ellipse(draw, cx + 2, body_cy, 13, 11, BEAR_BODY, outline=None)
        ellipse(draw, cx, body_cy - 3, 8, 6, BEAR_LIGHT, outline=None)
        draw_fur_texture(draw, cx + 2, body_cy, 20, 18, BEAR_BODY, density=4)
        # Forelimb
        draw.rectangle([cx + 10, body_cy - 4, cx + 16, body_cy + 8],
                       fill=BEAR_BODY, outline=OUTLINE)
        ellipse(draw, cx + 13, body_cy + 10, 4, 3, BEAR_DARK)
        for c in [-2, 0, 2]:
            draw.point((cx + 13 + c, body_cy + 12), fill=BEAR_CLAW)

    # --- Head (large, round, with small ears and short muzzle) ---
    if direction == DOWN:
        ellipse(draw, cx, head_cy, 14, 12, BEAR_BODY)
        ellipse(draw, cx - 2, head_cy - 3, 10, 7, BEAR_LIGHT, outline=None)
        # Muzzle
        ellipse(draw, cx, head_cy + 6, 6, 4, BEAR_BELLY)
        # Nose
        ellipse(draw, cx, head_cy + 4, 3, 2, BEAR_NOSE)
        # Eyes
        draw.rectangle([cx - 6, head_cy - 1, cx - 3, head_cy + 2], fill=BEAR_EYE)
        draw.point((cx - 5, head_cy), fill=(255, 255, 255))
        draw.rectangle([cx + 3, head_cy - 1, cx + 6, head_cy + 2], fill=BEAR_EYE)
        draw.point((cx + 4, head_cy), fill=(255, 255, 255))
        # Small round ears
        ellipse(draw, cx - 10, head_cy - 10, 5, 4, BEAR_BODY)
        ellipse(draw, cx - 10, head_cy - 10, 3, 2, BEAR_INNER_EAR, outline=None)
        ellipse(draw, cx + 10, head_cy - 10, 5, 4, BEAR_BODY)
        ellipse(draw, cx + 10, head_cy - 10, 3, 2, BEAR_INNER_EAR, outline=None)
    elif direction == UP:
        ellipse(draw, cx, head_cy, 14, 12, BEAR_BODY)
        ellipse(draw, cx, head_cy, 10, 8, BEAR_DARK, outline=None)
        # Ears
        ellipse(draw, cx - 10, head_cy - 10, 5, 4, BEAR_BODY)
        ellipse(draw, cx + 10, head_cy - 10, 5, 4, BEAR_BODY)
    elif direction == LEFT:
        ellipse(draw, cx - 2, head_cy, 12, 11, BEAR_BODY)
        ellipse(draw, cx - 4, head_cy - 3, 8, 6, BEAR_LIGHT, outline=None)
        # Muzzle extending left
        ellipse(draw, cx - 10, head_cy + 4, 6, 4, BEAR_BELLY)
        ellipse(draw, cx - 12, head_cy + 3, 3, 2, BEAR_NOSE)
        # Eye
        draw.rectangle([cx - 7, head_cy - 1, cx - 4, head_cy + 2], fill=BEAR_EYE)
        draw.point((cx - 6, head_cy), fill=(255, 255, 255))
        # Ear
        ellipse(draw, cx - 6, head_cy - 10, 5, 4, BEAR_BODY)
        ellipse(draw, cx - 6, head_cy - 10, 3, 2, BEAR_INNER_EAR, outline=None)
    else:  # RIGHT
        ellipse(draw, cx + 2, head_cy, 12, 11, BEAR_BODY)
        ellipse(draw, cx + 4, head_cy - 3, 8, 6, BEAR_LIGHT, outline=None)
        # Muzzle
        ellipse(draw, cx + 10, head_cy + 4, 6, 4, BEAR_BELLY)
        ellipse(draw, cx + 12, head_cy + 3, 3, 2, BEAR_NOSE)
        # Eye
        draw.rectangle([cx + 4, head_cy - 1, cx + 7, head_cy + 2], fill=BEAR_EYE)
        draw.point((cx + 5, head_cy), fill=(255, 255, 255))
        # Ear
        ellipse(draw, cx + 6, head_cy - 10, 5, 4, BEAR_BODY)
        ellipse(draw, cx + 6, head_cy - 10, 3, 2, BEAR_INNER_EAR, outline=None)


# ===================================================================
# SCORPION (ID 76) — low wide body, pincers, curled stinger tail
# ===================================================================

def draw_scorpion(draw, ox, oy, direction, frame):
    bob = [0, -1, 0, -1][frame]
    # Scuttling: body stays low, legs alternate
    leg_phase_a = [-2, 0, 2, 0][frame]
    leg_phase_b = [2, 0, -2, 0][frame]
    # Tail sway / pulse
    tail_pulse = [0, 1, 0, -1][frame]
    pincer_open = [2, 0, -1, 0][frame]

    base_y = oy + 54 + bob
    cx = ox + 32
    # Scorpion body sits very low
    body_cy = base_y - 8
    head_cy = body_cy - 6

    armor_dark = _darken(SCORP_ARMOR, 0.7)
    armor_light = _brighten(SCORP_ARMOR, 1.2)

    def draw_scorp_legs(lcx, lcy):
        """Draw 8 small legs underneath body — 4 per side."""
        for side in [-1, 1]:
            for i, (ax, group) in enumerate([(6, 'A'), (10, 'B'), (14, 'A'), (18, 'B')]):
                phase = leg_phase_a if group == 'A' else leg_phase_b
                knee_x = lcx + side * ax
                knee_y = lcy + 2
                foot_x = lcx + side * (ax + 4) + phase * side
                foot_y = base_y + 1
                draw.line([(lcx + side * 4, lcy + i), (knee_x, knee_y)],
                          fill=SCORP_DARK, width=1)
                draw.line([(knee_x, knee_y), (foot_x, foot_y)],
                          fill=SCORP_DARK, width=1)

    def draw_tail_curl(tcx, tcy, facing):
        """Draw segmented tail curling up and over, ending in stinger."""
        # Segments: base → up → forward → stinger tip
        if facing == DOWN:
            # Tail goes up from rear and curves forward over body
            segs = [
                (tcx, tcy + 4),
                (tcx, tcy - 2),
                (tcx + tail_pulse, tcy - 10),
                (tcx + tail_pulse, tcy - 18),
                (tcx + tail_pulse * 2, tcy - 22),
            ]
        elif facing == UP:
            segs = [
                (tcx, tcy + 4),
                (tcx, tcy - 2),
                (tcx - tail_pulse, tcy - 10),
                (tcx - tail_pulse, tcy - 18),
                (tcx - tail_pulse * 2, tcy - 22),
            ]
        elif facing == LEFT:
            segs = [
                (tcx + 8, tcy),
                (tcx + 4, tcy - 6),
                (tcx + tail_pulse, tcy - 12),
                (tcx - 4 + tail_pulse, tcy - 18),
                (tcx - 6 + tail_pulse, tcy - 22),
            ]
        else:  # RIGHT
            segs = [
                (tcx - 8, tcy),
                (tcx - 4, tcy - 6),
                (tcx - tail_pulse, tcy - 12),
                (tcx + 4 - tail_pulse, tcy - 18),
                (tcx + 6 - tail_pulse, tcy - 22),
            ]

        # Draw segments as connected thick lines with ellipse joints
        seg_width = [4, 3, 3, 2, 2]
        for i in range(len(segs) - 1):
            draw.line([segs[i], segs[i + 1]], fill=SCORP_BODY, width=seg_width[i])
            # Joint dot
            ellipse(draw, segs[i][0], segs[i][1], 3, 2, SCORP_ARMOR)
        # Armor highlight on segments
        for i in range(len(segs) - 1):
            mx = (segs[i][0] + segs[i + 1][0]) // 2
            my = (segs[i][1] + segs[i + 1][1]) // 2
            draw.point((mx - 1, my), fill=armor_light)

        # Stinger at tip
        tip = segs[-1]
        draw.polygon([
            (tip[0] - 3, tip[1] + 2),
            (tip[0], tip[1] - 4),
            (tip[0] + 3, tip[1] + 2),
        ], fill=SCORP_STINGER, outline=OUTLINE)
        draw.point((tip[0], tip[1] - 3), fill=_brighten(SCORP_STINGER, 1.4))

    def draw_pincers(pcx, pcy, facing):
        """Draw two large pincers extending forward from body."""
        if facing == DOWN:
            for side in [-1, 1]:
                # Arm extending forward
                arm_x = pcx + side * 12
                draw.line([(pcx + side * 8, pcy), (arm_x, pcy + 10)],
                          fill=SCORP_PINCER, width=3)
                # Pincer claw (two prongs)
                open_amt = pincer_open * side
                draw.line([(arm_x, pcy + 10),
                           (arm_x - 4 + open_amt, pcy + 16)],
                          fill=SCORP_PINCER, width=2)
                draw.line([(arm_x, pcy + 10),
                           (arm_x + 4 + open_amt, pcy + 16)],
                          fill=SCORP_PINCER, width=2)
                # Pincer tips
                draw.point((arm_x - 4 + open_amt, pcy + 16), fill=SCORP_DARK)
                draw.point((arm_x + 4 + open_amt, pcy + 16), fill=SCORP_DARK)
        elif facing == UP:
            for side in [-1, 1]:
                arm_x = pcx + side * 12
                draw.line([(pcx + side * 8, pcy), (arm_x, pcy - 10)],
                          fill=SCORP_PINCER, width=3)
                open_amt = pincer_open * side
                draw.line([(arm_x, pcy - 10),
                           (arm_x - 4 + open_amt, pcy - 16)],
                          fill=SCORP_PINCER, width=2)
                draw.line([(arm_x, pcy - 10),
                           (arm_x + 4 + open_amt, pcy - 16)],
                          fill=SCORP_PINCER, width=2)
                draw.point((arm_x - 4 + open_amt, pcy - 16), fill=SCORP_DARK)
                draw.point((arm_x + 4 + open_amt, pcy - 16), fill=SCORP_DARK)
        elif facing == LEFT:
            for v_off in [-6, 6]:
                arm_y = pcy + v_off
                draw.line([(pcx - 6, pcy + v_off // 2),
                           (pcx - 16, arm_y)],
                          fill=SCORP_PINCER, width=3)
                draw.line([(pcx - 16, arm_y),
                           (pcx - 22, arm_y - 3 + pincer_open)],
                          fill=SCORP_PINCER, width=2)
                draw.line([(pcx - 16, arm_y),
                           (pcx - 22, arm_y + 3 + pincer_open)],
                          fill=SCORP_PINCER, width=2)
                draw.point((pcx - 22, arm_y - 3 + pincer_open), fill=SCORP_DARK)
                draw.point((pcx - 22, arm_y + 3 + pincer_open), fill=SCORP_DARK)
        else:  # RIGHT
            for v_off in [-6, 6]:
                arm_y = pcy + v_off
                draw.line([(pcx + 6, pcy + v_off // 2),
                           (pcx + 16, arm_y)],
                          fill=SCORP_PINCER, width=3)
                draw.line([(pcx + 16, arm_y),
                           (pcx + 22, arm_y - 3 - pincer_open)],
                          fill=SCORP_PINCER, width=2)
                draw.line([(pcx + 16, arm_y),
                           (pcx + 22, arm_y + 3 - pincer_open)],
                          fill=SCORP_PINCER, width=2)
                draw.point((pcx + 22, arm_y - 3 - pincer_open), fill=SCORP_DARK)
                draw.point((pcx + 22, arm_y + 3 - pincer_open), fill=SCORP_DARK)

    if direction == DOWN:
        # Draw order: tail behind, then legs, body, pincers on top
        draw_tail_curl(cx, body_cy, DOWN)
        draw_scorp_legs(cx, body_cy)
        # Body: wide, flat, armored
        ellipse(draw, cx, body_cy, 16, 8, SCORP_ARMOR)
        ellipse(draw, cx - 2, body_cy - 2, 12, 5, armor_light, outline=None)
        ellipse(draw, cx + 3, body_cy + 2, 10, 4, armor_dark, outline=None)
        # Armor plate segments
        draw.line([(cx - 10, body_cy - 2), (cx + 10, body_cy - 2)],
                  fill=armor_dark, width=1)
        draw.line([(cx - 8, body_cy + 2), (cx + 8, body_cy + 2)],
                  fill=armor_dark, width=1)
        # Head section
        ellipse(draw, cx, head_cy, 8, 5, SCORP_ARMOR)
        ellipse(draw, cx - 1, head_cy - 1, 5, 3, armor_light, outline=None)
        # Eyes
        draw.point((cx - 4, head_cy - 1), fill=SCORP_EYE)
        draw.point((cx + 4, head_cy - 1), fill=SCORP_EYE)
        draw.point((cx - 3, head_cy - 2), fill=SCORP_EYE)
        draw.point((cx + 3, head_cy - 2), fill=SCORP_EYE)
        draw_pincers(cx, head_cy, DOWN)

    elif direction == UP:
        draw_pincers(cx, head_cy, UP)
        draw_scorp_legs(cx, body_cy)
        # Body
        ellipse(draw, cx, body_cy, 16, 8, SCORP_ARMOR)
        ellipse(draw, cx, body_cy, 12, 5, armor_dark, outline=None)
        draw.line([(cx - 10, body_cy - 2), (cx + 10, body_cy - 2)],
                  fill=armor_dark, width=1)
        draw.line([(cx - 8, body_cy + 2), (cx + 8, body_cy + 2)],
                  fill=armor_dark, width=1)
        # Head
        ellipse(draw, cx, head_cy, 8, 5, SCORP_ARMOR)
        ellipse(draw, cx, head_cy, 5, 3, _darken(SCORP_ARMOR, 0.8), outline=None)
        draw_tail_curl(cx, body_cy, UP)

    elif direction == LEFT:
        draw_tail_curl(cx, body_cy, LEFT)
        draw_scorp_legs(cx, body_cy)
        # Body (side view — slightly elongated)
        ellipse(draw, cx + 2, body_cy, 14, 8, SCORP_ARMOR)
        ellipse(draw, cx, body_cy - 2, 10, 5, armor_light, outline=None)
        ellipse(draw, cx + 5, body_cy + 2, 8, 4, armor_dark, outline=None)
        # Segment lines
        draw.line([(cx - 6, body_cy - 2), (cx + 10, body_cy - 2)],
                  fill=armor_dark, width=1)
        # Head
        ellipse(draw, cx - 8, head_cy, 6, 5, SCORP_ARMOR)
        ellipse(draw, cx - 9, head_cy - 1, 4, 3, armor_light, outline=None)
        # Eyes
        draw.point((cx - 12, head_cy - 1), fill=SCORP_EYE)
        draw.point((cx - 11, head_cy - 2), fill=SCORP_EYE)
        draw_pincers(cx - 8, head_cy, LEFT)

    else:  # RIGHT
        draw_tail_curl(cx, body_cy, RIGHT)
        draw_scorp_legs(cx, body_cy)
        # Body
        ellipse(draw, cx - 2, body_cy, 14, 8, SCORP_ARMOR)
        ellipse(draw, cx - 4, body_cy - 2, 10, 5, armor_light, outline=None)
        ellipse(draw, cx + 1, body_cy + 2, 8, 4, armor_dark, outline=None)
        draw.line([(cx - 10, body_cy - 2), (cx + 6, body_cy - 2)],
                  fill=armor_dark, width=1)
        # Head
        ellipse(draw, cx + 8, head_cy, 6, 5, SCORP_ARMOR)
        ellipse(draw, cx + 9, head_cy - 1, 4, 3, armor_light, outline=None)
        # Eyes
        draw.point((cx + 12, head_cy - 1), fill=SCORP_EYE)
        draw.point((cx + 11, head_cy - 2), fill=SCORP_EYE)
        draw_pincers(cx + 8, head_cy, RIGHT)


# Hawk palette
HAWK_BODY = (160, 120, 50)
HAWK_WING = (140, 100, 40)
HAWK_BELLY = (210, 180, 120)
HAWK_DARK = (100, 70, 30)
HAWK_LIGHT = (190, 150, 70)
HAWK_WING_TIP = (140, 60, 30)
HAWK_BEAK = (200, 160, 40)
HAWK_EYE = (220, 180, 40)
HAWK_TALON = (90, 70, 30)
HAWK_CREST = (180, 130, 45)

# Shark palette
SHARK_BODY = (100, 120, 140)
SHARK_BELLY = (180, 190, 200)
SHARK_FIN = (80, 100, 120)
SHARK_DARK = (60, 80, 100)
SHARK_LIGHT = (130, 150, 170)
SHARK_EYE = (30, 30, 35)
SHARK_MOUTH = (50, 30, 30)
SHARK_TEETH = (230, 230, 225)
SHARK_GILL = (70, 90, 110)
SHARK_SHADOW = (40, 40, 50, 90)

# Beetle palette
BEETLE_SHELL = (50, 120, 60)
BEETLE_DARK = (30, 80, 40)
BEETLE_LIGHT = (80, 180, 90)
BEETLE_HEAD = (40, 70, 45)
BEETLE_HORN = (60, 50, 30)
BEETLE_HORN_TIP = (90, 75, 45)
BEETLE_LEG = (45, 60, 35)
BEETLE_EYE = (180, 160, 40)

# Treant palette
TREANT_TRUNK = (100, 80, 50)
TREANT_DARK = (70, 55, 30)
TREANT_LIGHT = (140, 110, 70)
TREANT_LEAF = (60, 140, 50)
TREANT_LEAF_LIGHT = (90, 180, 70)
TREANT_KNOT = (50, 40, 25)
TREANT_ROOT = (90, 70, 40)

# Phoenix palette
PHOENIX_BODY = (240, 100, 20)
PHOENIX_WING = (255, 150, 50)
PHOENIX_FLAME = (255, 200, 50)
PHOENIX_EMBER = (255, 255, 100)
PHOENIX_DARK = (180, 60, 10)
PHOENIX_CHEST = (255, 210, 80)
PHOENIX_EYE = (255, 255, 200)
PHOENIX_BEAK = (200, 80, 20)


# ===================================================================
# HAWK (ID 77) — broad-winged raptor, golden-brown, feather crest
# ===================================================================

def draw_hawk(draw, ox, oy, direction, frame):
    bob = [0, -2, 0, -1][frame]
    wing_flap = [-4, 2, -4, 0][frame]
    leg_step = [0, 2, 0, -2][frame]

    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 18
    head_cy = body_cy - 16

    hawk_shadow = _darken(HAWK_BODY, 0.7)

    # --- Talons (3-toed) ---
    def draw_hawk_talons():
        if direction in (DOWN, UP):
            for side in [-1, 1]:
                fx = cx + side * 7 + side * leg_step
                for tx in [-4, 0, 4]:
                    draw.line([(fx, base_y), (fx + tx, base_y + 5)],
                              fill=HAWK_TALON, width=2)
                    draw.point((fx + tx, base_y + 5), fill=OUTLINE)
        elif direction == LEFT:
            for off in [leg_step, -leg_step]:
                fx = cx - 3 + off
                for tx in [-4, 0, 3]:
                    draw.line([(fx, base_y), (fx + tx, base_y + 5)],
                              fill=HAWK_TALON, width=2)
                    draw.point((fx + tx, base_y + 5), fill=OUTLINE)
        else:  # RIGHT
            for off in [leg_step, -leg_step]:
                fx = cx + 3 + off
                for tx in [-3, 0, 4]:
                    draw.line([(fx, base_y), (fx + tx, base_y + 5)],
                              fill=HAWK_TALON, width=2)
                    draw.point((fx + tx, base_y + 5), fill=OUTLINE)

    # --- Legs ---
    def draw_hawk_legs():
        if direction in (DOWN, UP):
            for side in [-1, 1]:
                lx = cx + side * 7 + side * leg_step
                draw.line([(lx, base_y), (cx + side * 5, body_cy + 8)],
                          fill=HAWK_DARK, width=3)
        elif direction == LEFT:
            for off in [leg_step, -leg_step]:
                lx = cx - 3 + off
                draw.line([(lx, base_y), (cx - 2, body_cy + 8)],
                          fill=HAWK_DARK, width=3)
        else:
            for off in [leg_step, -leg_step]:
                lx = cx + 3 + off
                draw.line([(lx, base_y), (cx + 2, body_cy + 8)],
                          fill=HAWK_DARK, width=3)

    # --- Wings (broad, spread to sides) ---
    def draw_hawk_wing(side, trailing=False):
        wy = body_cy + wing_flap
        base = HAWK_DARK if trailing else HAWK_WING
        if direction in (DOWN, UP):
            # Broad wing polygon
            draw.polygon([
                (cx + side * 10, body_cy - 6),
                (cx + side * 24, wy - 2),
                (cx + side * 26, wy + 4),
                (cx + side * 22, wy + 8),
                (cx + side * 10, body_cy + 6),
            ], fill=base, outline=OUTLINE)
            if not trailing:
                # Feather lines
                draw.line([(cx + side * 12, body_cy - 2),
                           (cx + side * 22, wy + 2)], fill=HAWK_LIGHT, width=1)
                draw.line([(cx + side * 12, body_cy + 2),
                           (cx + side * 22, wy + 6)], fill=HAWK_DARK, width=1)
                # Wing tips (red-brown accent)
                draw.point((cx + side * 26, wy + 4), fill=HAWK_WING_TIP)
                draw.point((cx + side * 24, wy + 6), fill=HAWK_WING_TIP)
                draw.point((cx + side * 22, wy + 8), fill=HAWK_WING_TIP)
        else:
            d = -1 if direction == LEFT else 1
            draw.polygon([
                (cx + d * 8, body_cy - 6),
                (cx + d * 20, wy - 2),
                (cx + d * 22, wy + 4),
                (cx + d * 18, wy + 8),
                (cx + d * 8, body_cy + 6),
            ], fill=base, outline=OUTLINE)
            if not trailing:
                draw.line([(cx + d * 10, body_cy),
                           (cx + d * 18, wy + 2)], fill=HAWK_LIGHT, width=1)
                draw.point((cx + d * 22, wy + 4), fill=HAWK_WING_TIP)
                draw.point((cx + d * 20, wy + 6), fill=HAWK_WING_TIP)

    # --- Draw order ---
    if direction == DOWN:
        draw_hawk_talons()
        draw_hawk_legs()
        for s in [-1, 1]:
            draw_hawk_wing(s)
        # Body
        ellipse(draw, cx, body_cy, 12, 10, HAWK_BODY)
        ellipse(draw, cx + 2, body_cy + 2, 8, 6, hawk_shadow, outline=None)
        ellipse(draw, cx - 2, body_cy - 2, 8, 6, HAWK_LIGHT, outline=None)
        ellipse(draw, cx, body_cy + 3, 8, 5, HAWK_BELLY, outline=None)
        # Head
        ellipse(draw, cx, head_cy, 10, 9, HAWK_BODY)
        ellipse(draw, cx - 2, head_cy - 2, 6, 5, HAWK_LIGHT, outline=None)
        # Crest feathers
        draw.polygon([(cx - 2, head_cy - 8), (cx, head_cy - 16),
                      (cx + 2, head_cy - 8)], fill=HAWK_CREST, outline=OUTLINE)
        draw.polygon([(cx - 6, head_cy - 6), (cx - 8, head_cy - 13),
                      (cx - 3, head_cy - 7)], fill=HAWK_CREST, outline=OUTLINE)
        draw.polygon([(cx + 6, head_cy - 6), (cx + 8, head_cy - 13),
                      (cx + 3, head_cy - 7)], fill=HAWK_CREST, outline=OUTLINE)
        # Eyes
        draw.rectangle([cx - 6, head_cy - 2, cx - 3, head_cy + 1], fill=HAWK_EYE)
        draw.point((cx - 4, head_cy - 1), fill=BLACK)
        draw.rectangle([cx + 3, head_cy - 2, cx + 6, head_cy + 1], fill=HAWK_EYE)
        draw.point((cx + 4, head_cy - 1), fill=BLACK)
        # Hooked beak
        draw.polygon([(cx - 3, head_cy + 4), (cx + 3, head_cy + 4),
                      (cx, head_cy + 10)], fill=HAWK_BEAK, outline=OUTLINE)
        draw.point((cx, head_cy + 9), fill=_darken(HAWK_BEAK, 0.7))

    elif direction == UP:
        draw_hawk_talons()
        draw_hawk_legs()
        for s in [-1, 1]:
            draw_hawk_wing(s)
        ellipse(draw, cx, body_cy, 12, 10, HAWK_BODY)
        ellipse(draw, cx, body_cy, 9, 7, HAWK_DARK, outline=None)
        ellipse(draw, cx, head_cy, 10, 9, HAWK_BODY)
        ellipse(draw, cx, head_cy, 7, 6, HAWK_DARK, outline=None)
        # Crest (back view)
        draw.polygon([(cx - 2, head_cy - 8), (cx, head_cy - 16),
                      (cx + 2, head_cy - 8)], fill=HAWK_CREST, outline=OUTLINE)
        draw.polygon([(cx - 6, head_cy - 6), (cx - 8, head_cy - 13),
                      (cx - 3, head_cy - 7)], fill=HAWK_CREST, outline=OUTLINE)
        draw.polygon([(cx + 6, head_cy - 6), (cx + 8, head_cy - 13),
                      (cx + 3, head_cy - 7)], fill=HAWK_CREST, outline=OUTLINE)

    elif direction == LEFT:
        draw_hawk_talons()
        draw_hawk_legs()
        draw_hawk_wing(1, trailing=True)
        ellipse(draw, cx - 2, body_cy, 11, 10, HAWK_BODY)
        ellipse(draw, cx, body_cy + 2, 7, 6, hawk_shadow, outline=None)
        ellipse(draw, cx - 4, body_cy - 2, 7, 5, HAWK_LIGHT, outline=None)
        ellipse(draw, cx - 2, body_cy + 3, 7, 5, HAWK_BELLY, outline=None)
        draw_hawk_wing(-1)
        # Head
        ellipse(draw, cx - 4, head_cy, 9, 9, HAWK_BODY)
        ellipse(draw, cx - 6, head_cy - 2, 5, 5, HAWK_LIGHT, outline=None)
        # Eye
        draw.rectangle([cx - 9, head_cy - 2, cx - 6, head_cy + 1], fill=HAWK_EYE)
        draw.point((cx - 7, head_cy - 1), fill=BLACK)
        # Crest
        draw.polygon([(cx - 2, head_cy - 8), (cx, head_cy - 15),
                      (cx + 2, head_cy - 7)], fill=HAWK_CREST, outline=OUTLINE)
        draw.polygon([(cx + 4, head_cy - 6), (cx + 6, head_cy - 12),
                      (cx + 6, head_cy - 5)], fill=HAWK_CREST, outline=OUTLINE)
        # Hooked beak (shorter than raptor)
        draw.polygon([(cx - 12, head_cy + 1), (cx - 6, head_cy - 2),
                      (cx - 6, head_cy + 3)], fill=HAWK_BEAK, outline=OUTLINE)
        draw.point((cx - 11, head_cy + 1), fill=_darken(HAWK_BEAK, 0.7))

    else:  # RIGHT
        draw_hawk_talons()
        draw_hawk_legs()
        draw_hawk_wing(-1, trailing=True)
        ellipse(draw, cx + 2, body_cy, 11, 10, HAWK_BODY)
        ellipse(draw, cx, body_cy + 2, 7, 6, hawk_shadow, outline=None)
        ellipse(draw, cx + 4, body_cy - 2, 7, 5, HAWK_LIGHT, outline=None)
        ellipse(draw, cx + 2, body_cy + 3, 7, 5, HAWK_BELLY, outline=None)
        draw_hawk_wing(1)
        # Head
        ellipse(draw, cx + 4, head_cy, 9, 9, HAWK_BODY)
        ellipse(draw, cx + 6, head_cy - 2, 5, 5, HAWK_LIGHT, outline=None)
        # Eye
        draw.rectangle([cx + 6, head_cy - 2, cx + 9, head_cy + 1], fill=HAWK_EYE)
        draw.point((cx + 7, head_cy - 1), fill=BLACK)
        # Crest
        draw.polygon([(cx + 2, head_cy - 8), (cx, head_cy - 15),
                      (cx - 2, head_cy - 7)], fill=HAWK_CREST, outline=OUTLINE)
        draw.polygon([(cx - 4, head_cy - 6), (cx - 6, head_cy - 12),
                      (cx - 6, head_cy - 5)], fill=HAWK_CREST, outline=OUTLINE)
        # Hooked beak
        draw.polygon([(cx + 12, head_cy + 1), (cx + 6, head_cy - 2),
                      (cx + 6, head_cy + 3)], fill=HAWK_BEAK, outline=OUTLINE)
        draw.point((cx + 11, head_cy + 1), fill=_darken(HAWK_BEAK, 0.7))


# ===================================================================
# SHARK (ID 78) — torpedo body, dorsal fin, hovering with shadow
# ===================================================================

def draw_shark(draw, ox, oy, direction, frame):
    # Hovering bob + undulation
    hover = [0, -2, 0, -2][frame]
    undulate = [-2, 0, 2, 0][frame]
    tail_wag = [-3, 0, 3, 0][frame]
    mouth_open = [0, 1, 0, -1][frame]

    base_y = oy + 54
    cx = ox + 32
    body_cy = base_y - 18 + hover
    head_cy = body_cy  # head is same vertical level, offset horizontally

    body_dark = _darken(SHARK_BODY, 0.75)

    # --- Shadow on ground (always below hovering body) ---
    ellipse(draw, cx, base_y + 2, 14, 4, (40, 40, 50, 90), outline=None)

    if direction == DOWN:
        # Tail fin (behind body)
        draw.polygon([
            (cx + tail_wag, body_cy - 14),
            (cx - 6 + tail_wag, body_cy - 20),
            (cx + 6 + tail_wag, body_cy - 20),
        ], fill=SHARK_FIN, outline=OUTLINE)

        # Body — torpedo oval
        ellipse(draw, cx, body_cy, 12, 16, SHARK_BODY)
        # Belly (lighter underside)
        ellipse(draw, cx, body_cy + 4, 8, 10, SHARK_BELLY, outline=None)
        # Shading
        ellipse(draw, cx + 3, body_cy - 2, 8, 12, body_dark, outline=None)
        ellipse(draw, cx - 2, body_cy, 8, 12, SHARK_BODY, outline=None)

        # Side pectoral fins
        draw.polygon([
            (cx - 10, body_cy + 2),
            (cx - 20, body_cy + 8 + undulate),
            (cx - 16, body_cy + 12 + undulate),
            (cx - 8, body_cy + 6),
        ], fill=SHARK_FIN, outline=OUTLINE)
        draw.polygon([
            (cx + 10, body_cy + 2),
            (cx + 20, body_cy + 8 + undulate),
            (cx + 16, body_cy + 12 + undulate),
            (cx + 8, body_cy + 6),
        ], fill=SHARK_FIN, outline=OUTLINE)

        # Dorsal fin (on top/front of body)
        draw.polygon([
            (cx, body_cy - 4),
            (cx - 2, body_cy - 14),
            (cx + 3, body_cy - 10),
        ], fill=SHARK_FIN, outline=OUTLINE)

        # Eyes
        draw.point((cx - 6, body_cy + 6), fill=SHARK_EYE)
        draw.point((cx - 5, body_cy + 6), fill=SHARK_EYE)
        draw.point((cx + 5, body_cy + 6), fill=SHARK_EYE)
        draw.point((cx + 6, body_cy + 6), fill=SHARK_EYE)

        # Mouth with teeth
        my = body_cy + 14 + mouth_open
        draw.line([(cx - 6, my), (cx + 6, my)], fill=SHARK_MOUTH, width=2)
        for tx in range(-5, 6, 2):
            draw.point((cx + tx, my + 1), fill=SHARK_TEETH)

        # Gill slits
        for gy in [-2, 0, 2]:
            draw.line([(cx - 8, body_cy + gy + 2), (cx - 10, body_cy + gy + 4)],
                      fill=SHARK_GILL, width=1)
            draw.line([(cx + 8, body_cy + gy + 2), (cx + 10, body_cy + gy + 4)],
                      fill=SHARK_GILL, width=1)

    elif direction == UP:
        # Tail fin
        draw.polygon([
            (cx + tail_wag, body_cy + 14),
            (cx - 6 + tail_wag, body_cy + 20),
            (cx + 6 + tail_wag, body_cy + 20),
        ], fill=SHARK_FIN, outline=OUTLINE)

        # Body
        ellipse(draw, cx, body_cy, 12, 16, SHARK_BODY)
        ellipse(draw, cx, body_cy, 9, 12, body_dark, outline=None)
        draw_scale_texture(draw, cx, body_cy, 14, 20, SHARK_LIGHT)

        # Pectoral fins
        draw.polygon([
            (cx - 10, body_cy - 2),
            (cx - 20, body_cy - 8 - undulate),
            (cx - 16, body_cy - 12 - undulate),
            (cx - 8, body_cy - 6),
        ], fill=SHARK_FIN, outline=OUTLINE)
        draw.polygon([
            (cx + 10, body_cy - 2),
            (cx + 20, body_cy - 8 - undulate),
            (cx + 16, body_cy - 12 - undulate),
            (cx + 8, body_cy - 6),
        ], fill=SHARK_FIN, outline=OUTLINE)

        # Dorsal fin
        draw.polygon([
            (cx, body_cy + 4),
            (cx - 2, body_cy + 14),
            (cx + 3, body_cy + 10),
        ], fill=SHARK_FIN, outline=OUTLINE)

    elif direction == LEFT:
        # Tail fin at right
        draw.polygon([
            (cx + 14, body_cy + tail_wag),
            (cx + 20, body_cy - 6 + tail_wag),
            (cx + 20, body_cy + 6 + tail_wag),
        ], fill=SHARK_FIN, outline=OUTLINE)

        # Body — horizontal torpedo
        ellipse(draw, cx, body_cy, 16, 10, SHARK_BODY)
        # Belly underside
        ellipse(draw, cx, body_cy + 4, 12, 5, SHARK_BELLY, outline=None)
        # Shading
        ellipse(draw, cx + 2, body_cy - 2, 12, 6, body_dark, outline=None)
        ellipse(draw, cx - 2, body_cy, 12, 6, SHARK_BODY, outline=None)

        # Dorsal fin
        draw.polygon([
            (cx + 2, body_cy - 8),
            (cx - 2, body_cy - 18),
            (cx + 6, body_cy - 12),
        ], fill=SHARK_FIN, outline=OUTLINE)

        # Pectoral fin (underside)
        draw.polygon([
            (cx + 2, body_cy + 6),
            (cx - 4, body_cy + 14 + undulate),
            (cx + 6, body_cy + 10 + undulate),
        ], fill=SHARK_FIN, outline=OUTLINE)

        # Eye
        draw.point((cx - 10, body_cy - 2), fill=SHARK_EYE)
        draw.point((cx - 11, body_cy - 2), fill=SHARK_EYE)

        # Mouth
        my = body_cy + 2 + mouth_open
        draw.line([(cx - 14, my - 1), (cx - 8, my)], fill=SHARK_MOUTH, width=2)
        for tx in range(0, 6):
            draw.point((cx - 14 + tx, my), fill=SHARK_TEETH)

        # Gill slits
        for gy in [-3, 0, 3]:
            draw.line([(cx - 4, body_cy + gy - 1), (cx - 6, body_cy + gy + 1)],
                      fill=SHARK_GILL, width=1)

    else:  # RIGHT
        # Tail fin at left
        draw.polygon([
            (cx - 14, body_cy + tail_wag),
            (cx - 20, body_cy - 6 + tail_wag),
            (cx - 20, body_cy + 6 + tail_wag),
        ], fill=SHARK_FIN, outline=OUTLINE)

        # Body
        ellipse(draw, cx, body_cy, 16, 10, SHARK_BODY)
        ellipse(draw, cx, body_cy + 4, 12, 5, SHARK_BELLY, outline=None)
        ellipse(draw, cx - 2, body_cy - 2, 12, 6, body_dark, outline=None)
        ellipse(draw, cx + 2, body_cy, 12, 6, SHARK_BODY, outline=None)

        # Dorsal fin
        draw.polygon([
            (cx - 2, body_cy - 8),
            (cx + 2, body_cy - 18),
            (cx - 6, body_cy - 12),
        ], fill=SHARK_FIN, outline=OUTLINE)

        # Pectoral fin
        draw.polygon([
            (cx - 2, body_cy + 6),
            (cx + 4, body_cy + 14 + undulate),
            (cx - 6, body_cy + 10 + undulate),
        ], fill=SHARK_FIN, outline=OUTLINE)

        # Eye
        draw.point((cx + 10, body_cy - 2), fill=SHARK_EYE)
        draw.point((cx + 11, body_cy - 2), fill=SHARK_EYE)

        # Mouth
        my = body_cy + 2 + mouth_open
        draw.line([(cx + 14, my - 1), (cx + 8, my)], fill=SHARK_MOUTH, width=2)
        for tx in range(0, 6):
            draw.point((cx + 14 - tx, my), fill=SHARK_TEETH)

        # Gill slits
        for gy in [-3, 0, 3]:
            draw.line([(cx + 4, body_cy + gy - 1), (cx + 6, body_cy + gy + 1)],
                      fill=SHARK_GILL, width=1)


# ===================================================================
# BEETLE (ID 79) — dome shell, horn, 6 thin legs, scuttling
# ===================================================================

def draw_beetle(draw, ox, oy, direction, frame):
    bob = [0, -1, 0, -1][frame]
    # 6 legs in two alternating groups
    leg_a = [-2, 0, 2, 0][frame]
    leg_b = [2, 0, -2, 0][frame]

    base_y = oy + 54 + bob
    cx = ox + 32
    shell_cy = base_y - 14
    head_cy = shell_cy - 6

    shell_light = _brighten(BEETLE_SHELL, 1.3)
    shell_dark = _darken(BEETLE_SHELL, 0.7)

    def draw_beetle_legs(lcx, lcy):
        """Draw 6 thin jointed legs — 3 per side."""
        if direction in (DOWN, UP):
            for side in [-1, 1]:
                for i, (ax, group) in enumerate([(8, 'A'), (14, 'B'), (18, 'A')]):
                    phase = leg_a if group == 'A' else leg_b
                    knee_x = lcx + side * ax
                    knee_y = lcy + i * 2 - 2
                    foot_x = lcx + side * (ax + 5) + phase * side
                    foot_y = base_y + 1
                    draw.line([(lcx + side * 6, lcy + i * 2),
                               (knee_x, knee_y)], fill=BEETLE_LEG, width=1)
                    draw.line([(knee_x, knee_y), (foot_x, foot_y)],
                              fill=BEETLE_LEG, width=1)
                    draw.point((knee_x, knee_y), fill=BEETLE_LIGHT)
        else:
            d = -1 if direction == LEFT else 1
            for i, (ax, group) in enumerate([(6, 'A'), (12, 'B'), (16, 'A')]):
                phase = leg_a if group == 'A' else leg_b
                knee_x = lcx + d * (ax - 2)
                knee_y = lcy + i * 2 - 2
                foot_x = lcx + d * (ax + 4) + phase * d
                foot_y = base_y + 1
                draw.line([(lcx + d * 4, lcy + i), (knee_x, knee_y)],
                          fill=BEETLE_LEG, width=1)
                draw.line([(knee_x, knee_y), (foot_x, foot_y)],
                          fill=BEETLE_LEG, width=1)
                draw.point((knee_x, knee_y), fill=BEETLE_LIGHT)
            # Far-side legs (thinner, behind)
            for i, (ax, group) in enumerate([(4, 'B'), (9, 'A'), (13, 'B')]):
                phase = leg_a if group == 'A' else leg_b
                knee_x = lcx - d * (ax - 2)
                knee_y = lcy + i * 2 - 1
                foot_x = lcx - d * (ax + 3) - phase * d
                foot_y = base_y + 1
                draw.line([(lcx - d * 2, lcy + i), (knee_x, knee_y)],
                          fill=_darken(BEETLE_LEG, 0.7), width=1)
                draw.line([(knee_x, knee_y), (foot_x, foot_y)],
                          fill=_darken(BEETLE_LEG, 0.7), width=1)

    if direction == DOWN:
        draw_beetle_legs(cx, shell_cy)
        # Shell (dome — large ellipse)
        ellipse(draw, cx, shell_cy, 16, 12, BEETLE_SHELL)
        # Highlight dome
        ellipse(draw, cx - 3, shell_cy - 4, 10, 6, shell_light, outline=None)
        ellipse(draw, cx + 4, shell_cy + 3, 10, 6, shell_dark, outline=None)
        # Elytra seam (center line)
        draw.line([(cx, shell_cy - 10), (cx, shell_cy + 10)],
                  fill=BEETLE_DARK, width=1)
        # Head (small, tucked under front)
        ellipse(draw, cx, head_cy + 10, 7, 4, BEETLE_HEAD)
        # Eyes
        draw.point((cx - 4, head_cy + 9), fill=BEETLE_EYE)
        draw.point((cx + 4, head_cy + 9), fill=BEETLE_EYE)
        # Horn curving forward
        draw.polygon([
            (cx - 2, head_cy + 8),
            (cx, head_cy - 4),
            (cx + 2, head_cy + 8),
        ], fill=BEETLE_HORN, outline=OUTLINE)
        draw.point((cx, head_cy - 3), fill=BEETLE_HORN_TIP)

    elif direction == UP:
        draw_beetle_legs(cx, shell_cy)
        # Shell
        ellipse(draw, cx, shell_cy, 16, 12, BEETLE_SHELL)
        ellipse(draw, cx, shell_cy, 12, 8, shell_dark, outline=None)
        # Elytra seam
        draw.line([(cx, shell_cy - 10), (cx, shell_cy + 10)],
                  fill=BEETLE_DARK, width=1)
        # Head (visible at top in back view, small)
        ellipse(draw, cx, head_cy + 10, 7, 4, BEETLE_HEAD)
        ellipse(draw, cx, head_cy + 10, 5, 3, _darken(BEETLE_HEAD, 0.8), outline=None)
        # Horn base visible
        draw.polygon([
            (cx - 2, head_cy + 8),
            (cx, head_cy),
            (cx + 2, head_cy + 8),
        ], fill=BEETLE_HORN, outline=OUTLINE)

    elif direction == LEFT:
        draw_beetle_legs(cx, shell_cy)
        # Shell (dome from side — taller)
        ellipse(draw, cx + 2, shell_cy, 14, 12, BEETLE_SHELL)
        ellipse(draw, cx, shell_cy - 3, 10, 7, shell_light, outline=None)
        ellipse(draw, cx + 5, shell_cy + 3, 9, 6, shell_dark, outline=None)
        # Elytra seam (side = horizontal curve)
        draw.line([(cx + 2, shell_cy - 10), (cx + 2, shell_cy + 10)],
                  fill=BEETLE_DARK, width=1)
        # Head
        ellipse(draw, cx - 10, head_cy + 8, 6, 5, BEETLE_HEAD)
        ellipse(draw, cx - 11, head_cy + 7, 4, 3, _brighten(BEETLE_HEAD, 1.2), outline=None)
        # Eye
        draw.point((cx - 14, head_cy + 7), fill=BEETLE_EYE)
        # Horn curving forward-left
        draw.polygon([
            (cx - 10, head_cy + 5),
            (cx - 18, head_cy - 2),
            (cx - 10, head_cy + 8),
        ], fill=BEETLE_HORN, outline=OUTLINE)
        draw.point((cx - 17, head_cy - 1), fill=BEETLE_HORN_TIP)

    else:  # RIGHT
        draw_beetle_legs(cx, shell_cy)
        # Shell
        ellipse(draw, cx - 2, shell_cy, 14, 12, BEETLE_SHELL)
        ellipse(draw, cx - 4, shell_cy - 3, 10, 7, shell_light, outline=None)
        ellipse(draw, cx + 1, shell_cy + 3, 9, 6, shell_dark, outline=None)
        # Elytra seam
        draw.line([(cx - 2, shell_cy - 10), (cx - 2, shell_cy + 10)],
                  fill=BEETLE_DARK, width=1)
        # Head
        ellipse(draw, cx + 10, head_cy + 8, 6, 5, BEETLE_HEAD)
        ellipse(draw, cx + 11, head_cy + 7, 4, 3, _brighten(BEETLE_HEAD, 1.2), outline=None)
        # Eye
        draw.point((cx + 14, head_cy + 7), fill=BEETLE_EYE)
        # Horn
        draw.polygon([
            (cx + 10, head_cy + 5),
            (cx + 18, head_cy - 2),
            (cx + 10, head_cy + 8),
        ], fill=BEETLE_HORN, outline=OUTLINE)
        draw.point((cx + 17, head_cy - 1), fill=BEETLE_HORN_TIP)


# ===================================================================
# TREANT (ID 80) — tree trunk body, branch arms, knothole face, leaves
# ===================================================================

def draw_treant(draw, ox, oy, direction, frame):
    bob = [0, -1, 0, -1][frame]
    sway = [-1, 0, 1, 0][frame]
    branch_sway = [-2, 0, 2, 0][frame]

    base_y = oy + 54 + bob
    cx = ox + 32 + sway
    body_cy = base_y - 20
    head_cy = body_cy - 14

    trunk_shadow = _darken(TREANT_TRUNK, 0.7)

    def draw_bark_texture(tcx, tcy, w, h):
        """Bark line texture on trunk."""
        dark = _darken(TREANT_TRUNK, 0.8)
        for row in range(0, h, 5):
            offset = 2 if (row // 5) % 2 == 0 else 0
            for col in range(offset, w, 6):
                px = tcx - w // 2 + col
                py = tcy - h // 2 + row
                draw.point((px, py), fill=dark)
                draw.point((px + 1, py + 1), fill=dark)

    def draw_leaf_cluster(lcx, lcy, size=3):
        """Draw a small cluster of leaves."""
        for dx, dy in [(-size, -1), (0, -size), (size, -1), (-1, size-2), (1, size-2)]:
            ellipse(draw, lcx + dx, lcy + dy, size, size - 1, TREANT_LEAF, outline=None)
        for dx, dy in [(-size+1, -2), (1, -size+1)]:
            draw.point((lcx + dx, lcy + dy), fill=TREANT_LEAF_LIGHT)

    # --- Root-feet ---
    def draw_roots():
        if direction in (DOWN, UP):
            for side in [-1, 1]:
                rx = cx + side * 8
                # Thick spreading roots
                draw.polygon([
                    (rx - 4, base_y - 4),
                    (rx + 4, base_y - 4),
                    (rx + 8, base_y + 2),
                    (rx - 8, base_y + 2),
                ], fill=TREANT_ROOT, outline=OUTLINE)
                # Root tendrils
                draw.line([(rx - 6, base_y + 1), (rx - 10, base_y + 3)],
                          fill=TREANT_ROOT, width=2)
                draw.line([(rx + 6, base_y + 1), (rx + 10, base_y + 3)],
                          fill=TREANT_ROOT, width=2)
        elif direction == LEFT:
            for off in [-4, 4]:
                rx = cx + off
                draw.polygon([
                    (rx - 4, base_y - 4), (rx + 4, base_y - 4),
                    (rx + 7, base_y + 2), (rx - 7, base_y + 2),
                ], fill=TREANT_ROOT, outline=OUTLINE)
                draw.line([(rx - 5, base_y + 1), (rx - 9, base_y + 3)],
                          fill=TREANT_ROOT, width=2)
        else:  # RIGHT
            for off in [-4, 4]:
                rx = cx + off
                draw.polygon([
                    (rx - 4, base_y - 4), (rx + 4, base_y - 4),
                    (rx + 7, base_y + 2), (rx - 7, base_y + 2),
                ], fill=TREANT_ROOT, outline=OUTLINE)
                draw.line([(rx + 5, base_y + 1), (rx + 9, base_y + 3)],
                          fill=TREANT_ROOT, width=2)

    # --- Branch arms ---
    def draw_branches():
        if direction == DOWN:
            for side in [-1, 1]:
                bx = cx + side * 16
                by = body_cy - 4 + branch_sway * side
                # Main branch
                draw.line([(cx + side * 10, body_cy - 2), (bx, by)],
                          fill=TREANT_TRUNK, width=4)
                draw.line([(bx, by), (bx + side * 4, by - 6)],
                          fill=TREANT_TRUNK, width=3)
                draw.line([(bx, by), (bx + side * 6, by + 2)],
                          fill=TREANT_TRUNK, width=2)
                # Leaf clusters at branch tips
                draw_leaf_cluster(bx + side * 5, by - 7, 3)
                draw_leaf_cluster(bx + side * 7, by + 2, 2)
        elif direction == UP:
            for side in [-1, 1]:
                bx = cx + side * 16
                by = body_cy - 4 + branch_sway * side
                draw.line([(cx + side * 10, body_cy - 2), (bx, by)],
                          fill=TREANT_TRUNK, width=4)
                draw.line([(bx, by), (bx + side * 4, by - 5)],
                          fill=TREANT_TRUNK, width=3)
                draw_leaf_cluster(bx + side * 5, by - 6, 3)
        elif direction == LEFT:
            # Leading branch extends left
            draw.line([(cx - 8, body_cy - 4), (cx - 20, body_cy - 8 + branch_sway)],
                      fill=TREANT_TRUNK, width=4)
            draw.line([(cx - 20, body_cy - 8 + branch_sway),
                       (cx - 24, body_cy - 14 + branch_sway)],
                      fill=TREANT_TRUNK, width=2)
            draw_leaf_cluster(cx - 25, body_cy - 15 + branch_sway, 3)
            # Trailing branch (shorter)
            draw.line([(cx + 6, body_cy - 2), (cx + 14, body_cy - 6 - branch_sway)],
                      fill=TREANT_TRUNK, width=3)
            draw_leaf_cluster(cx + 15, body_cy - 7 - branch_sway, 2)
        else:  # RIGHT
            draw.line([(cx + 8, body_cy - 4), (cx + 20, body_cy - 8 + branch_sway)],
                      fill=TREANT_TRUNK, width=4)
            draw.line([(cx + 20, body_cy - 8 + branch_sway),
                       (cx + 24, body_cy - 14 + branch_sway)],
                      fill=TREANT_TRUNK, width=2)
            draw_leaf_cluster(cx + 25, body_cy - 15 + branch_sway, 3)
            draw.line([(cx - 6, body_cy - 2), (cx - 14, body_cy - 6 - branch_sway)],
                      fill=TREANT_TRUNK, width=3)
            draw_leaf_cluster(cx - 15, body_cy - 7 - branch_sway, 2)

    # --- Draw order ---
    draw_roots()

    if direction == DOWN:
        # Trunk body (wide rectangle-ish)
        draw.rectangle([cx - 10, body_cy - 8, cx + 10, body_cy + 12],
                       fill=TREANT_TRUNK, outline=OUTLINE)
        draw.rectangle([cx - 10, body_cy - 8, cx - 6, body_cy + 8],
                       fill=TREANT_LIGHT, outline=None)
        draw.rectangle([cx + 4, body_cy - 4, cx + 10, body_cy + 10],
                       fill=trunk_shadow, outline=None)
        draw_bark_texture(cx, body_cy + 2, 16, 18)
        draw_branches()
        # Leaf crown
        draw_leaf_cluster(cx - 6, head_cy - 6, 4)
        draw_leaf_cluster(cx + 6, head_cy - 6, 4)
        draw_leaf_cluster(cx, head_cy - 10, 5)
        draw_leaf_cluster(cx - 10, head_cy - 2, 3)
        draw_leaf_cluster(cx + 10, head_cy - 2, 3)
        # Head / knothole face
        # Knothole eyes
        ellipse(draw, cx - 5, head_cy + 2, 3, 4, TREANT_KNOT)
        ellipse(draw, cx + 5, head_cy + 2, 3, 4, TREANT_KNOT)
        # Dark pupil dots
        draw.point((cx - 5, head_cy + 2), fill=BLACK)
        draw.point((cx + 5, head_cy + 2), fill=BLACK)
        # Knothole mouth
        ellipse(draw, cx, head_cy + 10, 4, 3, TREANT_KNOT)

    elif direction == UP:
        draw.rectangle([cx - 10, body_cy - 8, cx + 10, body_cy + 12],
                       fill=TREANT_TRUNK, outline=OUTLINE)
        draw.rectangle([cx - 10, body_cy - 8, cx + 10, body_cy + 10],
                       fill=trunk_shadow, outline=None)
        draw_bark_texture(cx, body_cy + 2, 16, 18)
        draw_branches()
        # Leaf crown
        draw_leaf_cluster(cx - 6, head_cy - 6, 4)
        draw_leaf_cluster(cx + 6, head_cy - 6, 4)
        draw_leaf_cluster(cx, head_cy - 10, 5)

    elif direction == LEFT:
        draw.rectangle([cx - 8, body_cy - 8, cx + 8, body_cy + 12],
                       fill=TREANT_TRUNK, outline=OUTLINE)
        draw.rectangle([cx - 8, body_cy - 8, cx - 4, body_cy + 8],
                       fill=TREANT_LIGHT, outline=None)
        draw.rectangle([cx + 2, body_cy - 4, cx + 8, body_cy + 10],
                       fill=trunk_shadow, outline=None)
        draw_bark_texture(cx, body_cy + 2, 12, 18)
        draw_branches()
        # Leaf crown
        draw_leaf_cluster(cx - 4, head_cy - 6, 4)
        draw_leaf_cluster(cx + 4, head_cy - 8, 4)
        draw_leaf_cluster(cx, head_cy - 10, 5)
        # Knothole face (facing left)
        ellipse(draw, cx - 6, head_cy + 2, 3, 3, TREANT_KNOT)
        draw.point((cx - 6, head_cy + 2), fill=BLACK)
        ellipse(draw, cx - 4, head_cy + 8, 3, 2, TREANT_KNOT)

    else:  # RIGHT
        draw.rectangle([cx - 8, body_cy - 8, cx + 8, body_cy + 12],
                       fill=TREANT_TRUNK, outline=OUTLINE)
        draw.rectangle([cx + 4, body_cy - 8, cx + 8, body_cy + 8],
                       fill=TREANT_LIGHT, outline=None)
        draw.rectangle([cx - 8, body_cy - 4, cx - 2, body_cy + 10],
                       fill=trunk_shadow, outline=None)
        draw_bark_texture(cx, body_cy + 2, 12, 18)
        draw_branches()
        # Leaf crown
        draw_leaf_cluster(cx + 4, head_cy - 6, 4)
        draw_leaf_cluster(cx - 4, head_cy - 8, 4)
        draw_leaf_cluster(cx, head_cy - 10, 5)
        # Knothole face (facing right)
        ellipse(draw, cx + 6, head_cy + 2, 3, 3, TREANT_KNOT)
        draw.point((cx + 6, head_cy + 2), fill=BLACK)
        ellipse(draw, cx + 4, head_cy + 8, 3, 2, TREANT_KNOT)


# ===================================================================
# PHOENIX (ID 81) — fire bird, flame wings, ember particles
# ===================================================================

def draw_phoenix(draw, ox, oy, direction, frame):
    hover = [0, -2, 0, -2][frame]
    wing_flap = [-4, 3, -4, 0][frame]
    flame_phase = frame  # used to shift flame particles

    base_y = oy + 54
    cx = ox + 32
    body_cy = base_y - 20 + hover
    head_cy = body_cy - 14

    body_dark = _darken(PHOENIX_BODY, 0.75)

    # --- Hovering shadow ---
    ellipse(draw, cx, base_y + 2, 12, 3, (60, 30, 10, 80), outline=None)

    # --- Flame particles (shift per frame for flickering effect) ---
    def draw_flames(fcx, fcy, count=5, spread=10, upward=True):
        """Draw flame ember particles near a point."""
        import random
        # Use deterministic positions seeded by frame
        offsets = [
            (-4, -3), (3, -5), (-1, -7), (5, -2), (-6, -4),
            (2, -8), (-3, -1), (4, -6), (-5, -5), (6, -3),
        ]
        colors = [PHOENIX_FLAME, PHOENIX_EMBER, PHOENIX_WING, PHOENIX_FLAME]
        for i in range(count):
            idx = (i + flame_phase) % len(offsets)
            dx, dy = offsets[idx]
            dx = dx * spread // 8
            if not upward:
                dy = -dy
            c = colors[(i + flame_phase) % len(colors)]
            draw.point((fcx + dx, fcy + dy), fill=c)
            draw.point((fcx + dx + 1, fcy + dy), fill=c)

    # --- Tail feathers (fire) ---
    def draw_fire_tail():
        if direction == DOWN:
            # Tail behind, going up
            for i, color in enumerate([PHOENIX_DARK, PHOENIX_BODY, PHOENIX_WING]):
                tw = 4 - i
                ty = body_cy - 8 - i * 4
                draw.polygon([
                    (cx - tw - i, body_cy - 4),
                    (cx + tw + i, body_cy - 4),
                    (cx + i * 2, ty),
                    (cx - i * 2, ty),
                ], fill=color, outline=None)
            draw_flames(cx, body_cy - 18, 4, 6, True)
        elif direction == UP:
            for i, color in enumerate([PHOENIX_DARK, PHOENIX_BODY, PHOENIX_WING]):
                tw = 4 - i
                ty = body_cy + 10 + i * 4
                draw.polygon([
                    (cx - tw - i, body_cy + 6),
                    (cx + tw + i, body_cy + 6),
                    (cx + i * 2, ty),
                    (cx - i * 2, ty),
                ], fill=color, outline=None)
            draw_flames(cx, body_cy + 22, 4, 6, False)
        elif direction == LEFT:
            for i, color in enumerate([PHOENIX_DARK, PHOENIX_BODY, PHOENIX_WING]):
                tw = 3 - i
                tx = cx + 10 + i * 4
                draw.polygon([
                    (cx + 6, body_cy - tw),
                    (cx + 6, body_cy + tw),
                    (tx, body_cy + i * 2),
                    (tx, body_cy - i * 2),
                ], fill=color, outline=None)
            draw_flames(cx + 22, body_cy, 4, 4, True)
        else:  # RIGHT
            for i, color in enumerate([PHOENIX_DARK, PHOENIX_BODY, PHOENIX_WING]):
                tw = 3 - i
                tx = cx - 10 - i * 4
                draw.polygon([
                    (cx - 6, body_cy - tw),
                    (cx - 6, body_cy + tw),
                    (tx, body_cy + i * 2),
                    (tx, body_cy - i * 2),
                ], fill=color, outline=None)
            draw_flames(cx - 22, body_cy, 4, 4, True)

    # --- Wings (flame gradient: red base -> orange -> yellow tips) ---
    def draw_fire_wing(side, trailing=False):
        wy = body_cy + wing_flap
        if direction in (DOWN, UP):
            # Wing polygon
            draw.polygon([
                (cx + side * 8, body_cy - 6),
                (cx + side * 22, wy - 4),
                (cx + side * 24, wy + 2),
                (cx + side * 20, wy + 8),
                (cx + side * 8, body_cy + 4),
            ], fill=PHOENIX_BODY if not trailing else PHOENIX_DARK, outline=OUTLINE)
            if not trailing:
                # Gradient layers: orange mid, yellow tips
                draw.polygon([
                    (cx + side * 14, body_cy - 4),
                    (cx + side * 22, wy - 2),
                    (cx + side * 24, wy + 2),
                    (cx + side * 20, wy + 6),
                    (cx + side * 14, body_cy + 2),
                ], fill=PHOENIX_WING, outline=None)
                draw.polygon([
                    (cx + side * 18, wy - 2),
                    (cx + side * 24, wy),
                    (cx + side * 22, wy + 4),
                    (cx + side * 18, wy + 2),
                ], fill=PHOENIX_FLAME, outline=None)
                # Yellow ember tips
                draw.point((cx + side * 24, wy + 2), fill=PHOENIX_EMBER)
                draw.point((cx + side * 22, wy + 4), fill=PHOENIX_EMBER)
                draw.point((cx + side * 20, wy + 8), fill=PHOENIX_EMBER)
                # Flame particles at wing tip
                draw_flames(cx + side * 24, wy, 3, 4, True)
        else:
            d = -1 if direction == LEFT else 1
            draw.polygon([
                (cx + d * 6, body_cy - 6),
                (cx + d * 18, wy - 4),
                (cx + d * 20, wy + 2),
                (cx + d * 16, wy + 8),
                (cx + d * 6, body_cy + 4),
            ], fill=PHOENIX_BODY if not trailing else PHOENIX_DARK, outline=OUTLINE)
            if not trailing:
                draw.polygon([
                    (cx + d * 12, body_cy - 3),
                    (cx + d * 18, wy - 2),
                    (cx + d * 20, wy + 2),
                    (cx + d * 16, wy + 6),
                    (cx + d * 12, body_cy + 2),
                ], fill=PHOENIX_WING, outline=None)
                draw.polygon([
                    (cx + d * 16, wy - 1),
                    (cx + d * 20, wy + 1),
                    (cx + d * 18, wy + 4),
                ], fill=PHOENIX_FLAME, outline=None)
                draw.point((cx + d * 20, wy + 2), fill=PHOENIX_EMBER)
                draw_flames(cx + d * 20, wy, 3, 3, True)

    # --- Draw order ---
    if direction == DOWN:
        draw_fire_tail()
        for s in [-1, 1]:
            draw_fire_wing(s)
        # Body
        ellipse(draw, cx, body_cy, 10, 10, PHOENIX_BODY)
        ellipse(draw, cx + 2, body_cy + 2, 7, 7, body_dark, outline=None)
        ellipse(draw, cx - 2, body_cy - 2, 7, 6, _brighten(PHOENIX_BODY, 1.2), outline=None)
        # Golden chest plumage
        ellipse(draw, cx, body_cy + 3, 6, 5, PHOENIX_CHEST, outline=None)
        # Head
        ellipse(draw, cx, head_cy, 8, 8, PHOENIX_BODY)
        ellipse(draw, cx - 2, head_cy - 2, 5, 4, _brighten(PHOENIX_BODY, 1.3), outline=None)
        # Fierce eyes
        draw.rectangle([cx - 5, head_cy - 2, cx - 2, head_cy + 1], fill=PHOENIX_EYE)
        draw.point((cx - 3, head_cy - 1), fill=BLACK)
        draw.rectangle([cx + 2, head_cy - 2, cx + 5, head_cy + 1], fill=PHOENIX_EYE)
        draw.point((cx + 3, head_cy - 1), fill=BLACK)
        # Sharp beak
        draw.polygon([(cx - 3, head_cy + 4), (cx + 3, head_cy + 4),
                      (cx, head_cy + 9)], fill=PHOENIX_BEAK, outline=OUTLINE)
        # Flame crest on head
        for dx, dy in [(-2, -8), (0, -12), (2, -9), (-4, -6), (4, -7)]:
            draw.polygon([
                (cx + dx - 1, head_cy - 4),
                (cx + dx, head_cy + dy),
                (cx + dx + 1, head_cy - 4),
            ], fill=PHOENIX_FLAME, outline=None)
        draw.point((cx, head_cy - 11), fill=PHOENIX_EMBER)
        draw.point((cx - 2, head_cy - 7), fill=PHOENIX_EMBER)
        draw.point((cx + 2, head_cy - 8), fill=PHOENIX_EMBER)

    elif direction == UP:
        for s in [-1, 1]:
            draw_fire_wing(s)
        # Body
        ellipse(draw, cx, body_cy, 10, 10, PHOENIX_BODY)
        ellipse(draw, cx, body_cy, 7, 7, body_dark, outline=None)
        # Head
        ellipse(draw, cx, head_cy, 8, 8, PHOENIX_BODY)
        ellipse(draw, cx, head_cy, 6, 6, body_dark, outline=None)
        # Flame crest (back view)
        for dx, dy in [(-2, -8), (0, -12), (2, -9), (-4, -6), (4, -7)]:
            draw.polygon([
                (cx + dx - 1, head_cy - 4),
                (cx + dx, head_cy + dy),
                (cx + dx + 1, head_cy - 4),
            ], fill=PHOENIX_FLAME, outline=None)
        draw.point((cx, head_cy - 11), fill=PHOENIX_EMBER)
        draw_fire_tail()

    elif direction == LEFT:
        draw_fire_tail()
        draw_fire_wing(1, trailing=True)
        # Body
        ellipse(draw, cx - 2, body_cy, 9, 10, PHOENIX_BODY)
        ellipse(draw, cx, body_cy + 2, 6, 6, body_dark, outline=None)
        ellipse(draw, cx - 4, body_cy - 2, 6, 6, _brighten(PHOENIX_BODY, 1.2), outline=None)
        ellipse(draw, cx - 2, body_cy + 3, 5, 4, PHOENIX_CHEST, outline=None)
        draw_fire_wing(-1)
        # Head
        ellipse(draw, cx - 4, head_cy, 7, 7, PHOENIX_BODY)
        ellipse(draw, cx - 6, head_cy - 2, 4, 4, _brighten(PHOENIX_BODY, 1.3), outline=None)
        # Eye
        draw.rectangle([cx - 8, head_cy - 2, cx - 5, head_cy + 1], fill=PHOENIX_EYE)
        draw.point((cx - 6, head_cy - 1), fill=BLACK)
        # Beak
        draw.polygon([(cx - 10, head_cy + 2), (cx - 4, head_cy - 1),
                      (cx - 4, head_cy + 3)], fill=PHOENIX_BEAK, outline=OUTLINE)
        # Flame crest
        for dx, dy in [(0, -10), (2, -8), (4, -6)]:
            draw.polygon([
                (cx - 4 + dx - 1, head_cy - 3),
                (cx - 4 + dx, head_cy + dy),
                (cx - 4 + dx + 1, head_cy - 3),
            ], fill=PHOENIX_FLAME, outline=None)
        draw.point((cx - 4, head_cy - 9), fill=PHOENIX_EMBER)

    else:  # RIGHT
        draw_fire_tail()
        draw_fire_wing(-1, trailing=True)
        # Body
        ellipse(draw, cx + 2, body_cy, 9, 10, PHOENIX_BODY)
        ellipse(draw, cx, body_cy + 2, 6, 6, body_dark, outline=None)
        ellipse(draw, cx + 4, body_cy - 2, 6, 6, _brighten(PHOENIX_BODY, 1.2), outline=None)
        ellipse(draw, cx + 2, body_cy + 3, 5, 4, PHOENIX_CHEST, outline=None)
        draw_fire_wing(1)
        # Head
        ellipse(draw, cx + 4, head_cy, 7, 7, PHOENIX_BODY)
        ellipse(draw, cx + 6, head_cy - 2, 4, 4, _brighten(PHOENIX_BODY, 1.3), outline=None)
        # Eye
        draw.rectangle([cx + 5, head_cy - 2, cx + 8, head_cy + 1], fill=PHOENIX_EYE)
        draw.point((cx + 6, head_cy - 1), fill=BLACK)
        # Beak
        draw.polygon([(cx + 10, head_cy + 2), (cx + 4, head_cy - 1),
                      (cx + 4, head_cy + 3)], fill=PHOENIX_BEAK, outline=OUTLINE)
        # Flame crest
        for dx, dy in [(0, -10), (-2, -8), (-4, -6)]:
            draw.polygon([
                (cx + 4 + dx - 1, head_cy - 3),
                (cx + 4 + dx, head_cy + dy),
                (cx + 4 + dx + 1, head_cy - 3),
            ], fill=PHOENIX_FLAME, outline=None)
        draw.point((cx + 4, head_cy - 9), fill=PHOENIX_EMBER)


# ===================================================================
# HYDRA (ID 82) — wide dragon-like body, 3 serpentine necks + heads
# ===================================================================

# Hydra palette
HYDRA_BODY = (50, 100, 60)
HYDRA_SCALE = (70, 130, 80)
HYDRA_BELLY = (100, 160, 110)
HYDRA_NECK = (60, 110, 70)
HYDRA_EYE = (200, 200, 50)
HYDRA_MOUTH = (120, 30, 30)
HYDRA_TOOTH = (220, 220, 210)


def draw_hydra(draw, ox, oy, direction, frame):
    bob = [0, -1, 0, -1][frame]
    # Each head sways with different phase
    sway_a = [-2, 1, 2, -1][frame]
    sway_b = [1, -2, -1, 2][frame]
    sway_c = [0, 2, -2, 0][frame]

    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 14
    neck_base_y = body_cy - 8

    body_dark = _darken(HYDRA_BODY, 0.7)
    neck_dark = _darken(HYDRA_NECK, 0.75)

    def draw_head(hx, hy, facing_dir, mouth_open=True):
        """Draw a single hydra head at (hx, hy)."""
        ellipse(draw, hx, hy, 6, 5, HYDRA_NECK)
        ellipse(draw, hx, hy - 1, 4, 3, _brighten(HYDRA_NECK, 1.2), outline=None)
        # Eyes
        if facing_dir == 'front':
            draw.rectangle([hx - 4, hy - 2, hx - 2, hy], fill=HYDRA_EYE)
            draw.point((hx - 3, hy - 1), fill=BLACK)
            draw.rectangle([hx + 2, hy - 2, hx + 4, hy], fill=HYDRA_EYE)
            draw.point((hx + 3, hy - 1), fill=BLACK)
            if mouth_open:
                draw.rectangle([hx - 3, hy + 3, hx + 3, hy + 5], fill=HYDRA_MOUTH)
                for tx in [-2, 0, 2]:
                    draw.point((hx + tx, hy + 3), fill=HYDRA_TOOTH)
        elif facing_dir == 'left':
            draw.rectangle([hx - 5, hy - 2, hx - 3, hy], fill=HYDRA_EYE)
            draw.point((hx - 4, hy - 1), fill=BLACK)
            if mouth_open:
                draw.polygon([(hx - 6, hy + 1), (hx - 6, hy + 4), (hx - 2, hy + 3)],
                             fill=HYDRA_MOUTH, outline=OUTLINE)
                draw.point((hx - 5, hy + 1), fill=HYDRA_TOOTH)
        elif facing_dir == 'right':
            draw.rectangle([hx + 3, hy - 2, hx + 5, hy], fill=HYDRA_EYE)
            draw.point((hx + 4, hy - 1), fill=BLACK)
            if mouth_open:
                draw.polygon([(hx + 6, hy + 1), (hx + 6, hy + 4), (hx + 2, hy + 3)],
                             fill=HYDRA_MOUTH, outline=OUTLINE)
                draw.point((hx + 5, hy + 1), fill=HYDRA_TOOTH)
        else:  # back
            ellipse(draw, hx, hy, 5, 4, neck_dark, outline=None)

    def draw_neck(x1, y1, x2, y2):
        """Draw a thick neck segment."""
        draw.polygon([
            (x1 - 3, y1), (x1 + 3, y1),
            (x2 + 2, y2), (x2 - 2, y2),
        ], fill=HYDRA_NECK, outline=OUTLINE)
        # Scale dots along neck
        steps = 3
        for i in range(1, steps + 1):
            t = i / (steps + 1)
            mx = int(x1 + (x2 - x1) * t)
            my = int(y1 + (y2 - y1) * t)
            draw.point((mx, my), fill=HYDRA_SCALE)

    # --- Thick tail ---
    if direction == DOWN:
        draw.polygon([
            (cx + 8, body_cy + 2), (cx + 18, body_cy - 4),
            (cx + 20, body_cy - 2), (cx + 10, body_cy + 4),
        ], fill=HYDRA_BODY, outline=OUTLINE)
        draw.point((cx + 19, body_cy - 3), fill=HYDRA_SCALE)
    elif direction == UP:
        draw.polygon([
            (cx - 2, body_cy + 10), (cx + 2, body_cy + 10),
            (cx + 4, body_cy + 20), (cx - 4, body_cy + 20),
        ], fill=HYDRA_BODY, outline=OUTLINE)
        draw.point((cx, body_cy + 16), fill=HYDRA_SCALE)
    elif direction == LEFT:
        draw.polygon([
            (cx + 10, body_cy), (cx + 22, body_cy - 2),
            (cx + 24, body_cy + 1), (cx + 12, body_cy + 3),
        ], fill=HYDRA_BODY, outline=OUTLINE)
    else:
        draw.polygon([
            (cx - 10, body_cy), (cx - 22, body_cy - 2),
            (cx - 24, body_cy + 1), (cx - 12, body_cy + 3),
        ], fill=HYDRA_BODY, outline=OUTLINE)

    # --- Short thick legs ---
    if direction in (DOWN, UP):
        for side in [-1, 1]:
            lx = cx + side * 10
            draw.rectangle([lx - 4, body_cy + 6, lx + 4, base_y],
                           fill=HYDRA_BODY, outline=OUTLINE)
            if direction == DOWN:
                draw.rectangle([lx - 4, body_cy + 6, lx - 2, base_y - 4],
                               fill=_brighten(HYDRA_BODY, 1.1), outline=None)
            ellipse(draw, lx, base_y + 1, 5, 2, body_dark)
    else:
        d = -1 if direction == LEFT else 1
        for offset in [-3, 3]:
            lx = cx + d * 4 + offset
            draw.rectangle([lx - 4, body_cy + 6, lx + 4, base_y],
                           fill=HYDRA_BODY, outline=OUTLINE)
            ellipse(draw, lx, base_y + 1, 5, 2, body_dark)

    # --- Body (wide, heavy, low) ---
    if direction == DOWN:
        ellipse(draw, cx, body_cy, 16, 12, HYDRA_BODY)
        ellipse(draw, cx + 3, body_cy + 3, 12, 8, body_dark, outline=None)
        ellipse(draw, cx - 2, body_cy - 2, 10, 6, _brighten(HYDRA_BODY, 1.15), outline=None)
        ellipse(draw, cx, body_cy + 3, 8, 5, HYDRA_BELLY, outline=None)
        draw_scale_texture(draw, cx, body_cy, 24, 16, HYDRA_SCALE)
    elif direction == UP:
        ellipse(draw, cx, body_cy, 16, 12, HYDRA_BODY)
        ellipse(draw, cx, body_cy, 12, 8, body_dark, outline=None)
        draw_scale_texture(draw, cx, body_cy, 24, 16, HYDRA_SCALE)
    elif direction == LEFT:
        ellipse(draw, cx + 2, body_cy, 14, 12, HYDRA_BODY)
        ellipse(draw, cx + 4, body_cy + 3, 10, 8, body_dark, outline=None)
        ellipse(draw, cx - 2, body_cy - 2, 8, 6, _brighten(HYDRA_BODY, 1.15), outline=None)
        draw_scale_texture(draw, cx + 2, body_cy, 20, 16, HYDRA_SCALE)
    else:
        ellipse(draw, cx - 2, body_cy, 14, 12, HYDRA_BODY)
        ellipse(draw, cx - 4, body_cy + 3, 10, 8, body_dark, outline=None)
        ellipse(draw, cx + 2, body_cy - 2, 8, 6, _brighten(HYDRA_BODY, 1.15), outline=None)
        draw_scale_texture(draw, cx - 2, body_cy, 20, 16, HYDRA_SCALE)

    # --- Three necks and heads ---
    if direction == DOWN:
        # Left head — angles outward left
        lhx = cx - 10 + sway_a
        lhy = neck_base_y - 16
        draw_neck(cx - 6, neck_base_y, lhx, lhy + 4)
        draw_head(lhx, lhy, 'front', mouth_open=(frame == 1))
        # Center head — straight forward
        chx = cx + sway_b
        chy = neck_base_y - 20
        draw_neck(cx, neck_base_y, chx, chy + 4)
        draw_head(chx, chy, 'front', mouth_open=(frame == 3))
        # Right head — angles outward right
        rhx = cx + 10 + sway_c
        rhy = neck_base_y - 16
        draw_neck(cx + 6, neck_base_y, rhx, rhy + 4)
        draw_head(rhx, rhy, 'front', mouth_open=(frame == 0))

    elif direction == UP:
        # All three necks visible from behind, heads facing away
        lhx = cx - 10 + sway_a
        lhy = neck_base_y - 16
        draw_neck(cx - 6, neck_base_y, lhx, lhy + 4)
        draw_head(lhx, lhy, 'back')
        chx = cx + sway_b
        chy = neck_base_y - 20
        draw_neck(cx, neck_base_y, chx, chy + 4)
        draw_head(chx, chy, 'back')
        rhx = cx + 10 + sway_c
        rhy = neck_base_y - 16
        draw_neck(cx + 6, neck_base_y, rhx, rhy + 4)
        draw_head(rhx, rhy, 'back')

    elif direction == LEFT:
        # Heads extend to the left in a fan
        # Far head (behind)
        fhx = cx - 14 + sway_a
        fhy = neck_base_y - 12
        draw_neck(cx - 4, neck_base_y, fhx + 4, fhy + 4)
        draw_head(fhx, fhy, 'left', mouth_open=(frame == 2))
        # Mid head
        mhx = cx - 16 + sway_b
        mhy = neck_base_y - 18
        draw_neck(cx - 2, neck_base_y - 2, mhx + 4, mhy + 4)
        draw_head(mhx, mhy, 'left', mouth_open=(frame == 0))
        # Near head (front)
        nhx = cx - 14 + sway_c
        nhy = neck_base_y - 8
        draw_neck(cx - 4, neck_base_y + 2, nhx + 4, nhy + 4)
        draw_head(nhx, nhy, 'left', mouth_open=(frame == 1))

    else:  # RIGHT
        # Heads extend to the right in a fan
        fhx = cx + 14 + sway_a
        fhy = neck_base_y - 12
        draw_neck(cx + 4, neck_base_y, fhx - 4, fhy + 4)
        draw_head(fhx, fhy, 'right', mouth_open=(frame == 2))
        mhx = cx + 16 + sway_b
        mhy = neck_base_y - 18
        draw_neck(cx + 2, neck_base_y - 2, mhx - 4, mhy + 4)
        draw_head(mhx, mhy, 'right', mouth_open=(frame == 0))
        nhx = cx + 14 + sway_c
        nhy = neck_base_y - 8
        draw_neck(cx + 4, neck_base_y + 2, nhx - 4, nhy + 4)
        draw_head(nhx, nhy, 'right', mouth_open=(frame == 1))


# ===================================================================
# MANTIS (ID 83) — thin elongated body, triangular head, raptorial arms
# ===================================================================

# Mantis palette
MANTIS_BODY = (80, 160, 50)
MANTIS_HEAD = (90, 170, 60)
MANTIS_ARMS = (70, 140, 45)
MANTIS_DARK = (50, 110, 30)
MANTIS_LIGHT = (120, 200, 90)
MANTIS_EYE = (180, 160, 40)
MANTIS_WING = (100, 170, 80, 120)
MANTIS_SPIKE = (60, 120, 35)


def draw_mantis(draw, ox, oy, direction, frame):
    bob = [0, -1, 0, -1][frame]
    # Walking legs alternate
    leg_a = [-2, 1, 2, -1][frame]
    leg_b = [2, -1, -2, 1][frame]
    # Raptorial arms subtle motion
    arm_shift = [0, 1, 0, -1][frame]

    base_y = oy + 54 + bob
    cx = ox + 32
    # Mantis is tall and thin
    body_cy = base_y - 22
    thorax_cy = body_cy - 8
    head_cy = thorax_cy - 12

    body_dark = _darken(MANTIS_BODY, 0.7)

    def draw_walking_legs(lcx, lcy):
        """Draw 4 thin walking legs."""
        if direction in (DOWN, UP):
            for side in [-1, 1]:
                # Front pair
                foot_x = lcx + side * 10 + (leg_a if side == 1 else leg_b)
                draw.line([(lcx + side * 3, lcy + 4), (lcx + side * 7, lcy + 10),
                           (foot_x, base_y)], fill=MANTIS_DARK, width=1)
                # Rear pair
                foot_x2 = lcx + side * 8 + (leg_b if side == 1 else leg_a)
                draw.line([(lcx + side * 2, lcy + 8), (lcx + side * 6, lcy + 14),
                           (foot_x2, base_y)], fill=MANTIS_DARK, width=1)
        else:
            d = -1 if direction == LEFT else 1
            # 4 legs visible in profile
            for i, phase in enumerate([leg_a, leg_b, -leg_a, -leg_b]):
                ly = lcy + 4 + i * 3
                foot_x = lcx + d * 6 + phase
                draw.line([(lcx + d * 2, ly), (lcx + d * 5, ly + 4),
                           (foot_x, base_y)], fill=MANTIS_DARK, width=1)

    def draw_raptorial_arms(acx, acy):
        """Draw large folded raptorial forelegs."""
        if direction == DOWN:
            for side in [-1, 1]:
                # Upper arm segment
                ux = acx + side * 8
                uy = acy + arm_shift
                draw.polygon([
                    (acx + side * 4, acy - 2),
                    (ux, acy - 6),
                    (ux + side * 2, acy - 4),
                    (acx + side * 5, acy),
                ], fill=MANTIS_ARMS, outline=OUTLINE)
                # Forearm (folded back, pointing down)
                draw.polygon([
                    (ux, acy - 6),
                    (ux + side * 1, acy + 6 + arm_shift),
                    (ux - side * 1, acy + 6 + arm_shift),
                    (ux - side * 1, acy - 4),
                ], fill=MANTIS_ARMS, outline=OUTLINE)
                # Spikes on inner edge of forearm
                for sy in range(-3, 5, 3):
                    draw.point((ux - side * 1, acy + sy), fill=MANTIS_SPIKE)
        elif direction == UP:
            # Arms mostly hidden behind body, just tips visible
            for side in [-1, 1]:
                ux = acx + side * 6
                draw.line([(acx + side * 3, acy), (ux, acy - 4)],
                          fill=MANTIS_ARMS, width=2)
        elif direction == LEFT:
            # One arm visible, folded in front
            ax = acx - 6
            ay = acy + arm_shift
            draw.polygon([
                (acx - 3, acy - 2), (ax, acy - 8),
                (ax - 2, acy - 6), (acx - 4, acy),
            ], fill=MANTIS_ARMS, outline=OUTLINE)
            draw.polygon([
                (ax, acy - 8), (ax - 1, acy + 4 + arm_shift),
                (ax + 2, acy + 4 + arm_shift), (ax + 1, acy - 6),
            ], fill=MANTIS_ARMS, outline=OUTLINE)
            for sy in range(-5, 3, 3):
                draw.point((ax + 1, acy + sy), fill=MANTIS_SPIKE)
        else:  # RIGHT
            ax = acx + 6
            ay = acy + arm_shift
            draw.polygon([
                (acx + 3, acy - 2), (ax, acy - 8),
                (ax + 2, acy - 6), (acx + 4, acy),
            ], fill=MANTIS_ARMS, outline=OUTLINE)
            draw.polygon([
                (ax, acy - 8), (ax + 1, acy + 4 + arm_shift),
                (ax - 2, acy + 4 + arm_shift), (ax - 1, acy - 6),
            ], fill=MANTIS_ARMS, outline=OUTLINE)
            for sy in range(-5, 3, 3):
                draw.point((ax - 1, acy + sy), fill=MANTIS_SPIKE)

    # --- Draw order ---
    # Walking legs (behind body)
    draw_walking_legs(cx, body_cy)

    # --- Abdomen (elongated, lower body) ---
    if direction == DOWN:
        ellipse(draw, cx, body_cy + 4, 7, 10, MANTIS_BODY)
        ellipse(draw, cx - 1, body_cy + 2, 4, 6, MANTIS_LIGHT, outline=None)
        ellipse(draw, cx + 2, body_cy + 6, 4, 6, body_dark, outline=None)
    elif direction == UP:
        ellipse(draw, cx, body_cy + 4, 7, 10, MANTIS_BODY)
        ellipse(draw, cx, body_cy + 4, 5, 7, body_dark, outline=None)
        # Wings (subtle, folded on back)
        draw.polygon([
            (cx - 5, body_cy - 2), (cx - 8, body_cy + 10),
            (cx - 3, body_cy + 12), (cx - 2, body_cy),
        ], fill=MANTIS_WING, outline=None)
        draw.polygon([
            (cx + 5, body_cy - 2), (cx + 8, body_cy + 10),
            (cx + 3, body_cy + 12), (cx + 2, body_cy),
        ], fill=MANTIS_WING, outline=None)
    elif direction == LEFT:
        ellipse(draw, cx + 2, body_cy + 4, 6, 10, MANTIS_BODY)
        ellipse(draw, cx, body_cy + 2, 4, 6, MANTIS_LIGHT, outline=None)
        ellipse(draw, cx + 4, body_cy + 6, 4, 6, body_dark, outline=None)
        # Wing edge visible
        draw.polygon([
            (cx + 6, body_cy - 2), (cx + 8, body_cy + 10),
            (cx + 4, body_cy + 12), (cx + 4, body_cy),
        ], fill=MANTIS_WING, outline=None)
    else:  # RIGHT
        ellipse(draw, cx - 2, body_cy + 4, 6, 10, MANTIS_BODY)
        ellipse(draw, cx, body_cy + 2, 4, 6, MANTIS_LIGHT, outline=None)
        ellipse(draw, cx - 4, body_cy + 6, 4, 6, body_dark, outline=None)
        draw.polygon([
            (cx - 6, body_cy - 2), (cx - 8, body_cy + 10),
            (cx - 4, body_cy + 12), (cx - 4, body_cy),
        ], fill=MANTIS_WING, outline=None)

    # --- Thorax (narrow waist connecting abdomen to head) ---
    if direction in (DOWN, UP):
        ellipse(draw, cx, thorax_cy, 5, 6, MANTIS_BODY)
        if direction == DOWN:
            ellipse(draw, cx - 1, thorax_cy - 1, 3, 3, MANTIS_LIGHT, outline=None)
    elif direction == LEFT:
        ellipse(draw, cx - 1, thorax_cy, 4, 6, MANTIS_BODY)
    else:
        ellipse(draw, cx + 1, thorax_cy, 4, 6, MANTIS_BODY)

    # Raptorial arms (attached at thorax)
    draw_raptorial_arms(cx, thorax_cy)

    # --- Head (triangular, wider than neck) ---
    if direction == DOWN:
        # Triangular head, wider at top
        draw.polygon([
            (cx - 10, head_cy - 2), (cx + 10, head_cy - 2),
            (cx + 6, head_cy + 6), (cx - 6, head_cy + 6),
        ], fill=MANTIS_HEAD, outline=OUTLINE)
        ellipse(draw, cx, head_cy + 2, 6, 4, _brighten(MANTIS_HEAD, 1.2), outline=None)
        # Large compound eyes on sides
        ellipse(draw, cx - 9, head_cy, 4, 4, MANTIS_EYE)
        draw.point((cx - 10, head_cy), fill=BLACK)
        draw.point((cx - 8, head_cy - 1), fill=BLACK)
        ellipse(draw, cx + 9, head_cy, 4, 4, MANTIS_EYE)
        draw.point((cx + 10, head_cy), fill=BLACK)
        draw.point((cx + 8, head_cy - 1), fill=BLACK)
        # Antennae
        draw.line([(cx - 4, head_cy - 4), (cx - 8, head_cy - 12)],
                  fill=MANTIS_DARK, width=1)
        draw.point((cx - 8, head_cy - 12), fill=MANTIS_LIGHT)
        draw.line([(cx + 4, head_cy - 4), (cx + 8, head_cy - 12)],
                  fill=MANTIS_DARK, width=1)
        draw.point((cx + 8, head_cy - 12), fill=MANTIS_LIGHT)

    elif direction == UP:
        draw.polygon([
            (cx - 10, head_cy - 2), (cx + 10, head_cy - 2),
            (cx + 6, head_cy + 6), (cx - 6, head_cy + 6),
        ], fill=MANTIS_HEAD, outline=OUTLINE)
        ellipse(draw, cx, head_cy + 2, 6, 4, body_dark, outline=None)
        # Eyes peeking around sides
        ellipse(draw, cx - 10, head_cy, 3, 3, MANTIS_EYE)
        ellipse(draw, cx + 10, head_cy, 3, 3, MANTIS_EYE)
        # Antennae
        draw.line([(cx - 4, head_cy - 4), (cx - 8, head_cy - 12)],
                  fill=MANTIS_DARK, width=1)
        draw.line([(cx + 4, head_cy - 4), (cx + 8, head_cy - 12)],
                  fill=MANTIS_DARK, width=1)

    elif direction == LEFT:
        # Triangular head profile
        draw.polygon([
            (cx - 4, head_cy - 6), (cx - 4, head_cy + 4),
            (cx - 12, head_cy + 2), (cx - 12, head_cy - 4),
        ], fill=MANTIS_HEAD, outline=OUTLINE)
        ellipse(draw, cx - 6, head_cy, 5, 4, _brighten(MANTIS_HEAD, 1.15), outline=None)
        # Large compound eye
        ellipse(draw, cx - 5, head_cy - 4, 4, 4, MANTIS_EYE)
        draw.point((cx - 6, head_cy - 4), fill=BLACK)
        draw.point((cx - 4, head_cy - 5), fill=BLACK)
        # Antenna
        draw.line([(cx - 8, head_cy - 6), (cx - 14, head_cy - 14)],
                  fill=MANTIS_DARK, width=1)
        draw.point((cx - 14, head_cy - 14), fill=MANTIS_LIGHT)

    else:  # RIGHT
        draw.polygon([
            (cx + 4, head_cy - 6), (cx + 4, head_cy + 4),
            (cx + 12, head_cy + 2), (cx + 12, head_cy - 4),
        ], fill=MANTIS_HEAD, outline=OUTLINE)
        ellipse(draw, cx + 6, head_cy, 5, 4, _brighten(MANTIS_HEAD, 1.15), outline=None)
        ellipse(draw, cx + 5, head_cy - 4, 4, 4, MANTIS_EYE)
        draw.point((cx + 6, head_cy - 4), fill=BLACK)
        draw.point((cx + 4, head_cy - 5), fill=BLACK)
        draw.line([(cx + 8, head_cy - 6), (cx + 14, head_cy - 14)],
                  fill=MANTIS_DARK, width=1)
        draw.point((cx + 14, head_cy - 14), fill=MANTIS_LIGHT)


# ===================================================================
# JELLYFISH (ID 84) — translucent dome, trailing tentacles, pulsing
# ===================================================================

# Jellyfish palette (RGBA for translucency)
JELLY_DOME = (150, 100, 200, 180)
JELLY_DOME_LIGHT = (180, 140, 230, 160)
JELLY_DOME_DARK = (110, 70, 160, 190)
JELLY_GLOW_A = (200, 180, 255, 200)
JELLY_GLOW_B = (100, 220, 255, 200)
JELLY_GLOW_C = (255, 180, 220, 200)
JELLY_TENTACLE = (140, 90, 190, 150)
JELLY_TENT_TIP = (120, 70, 170, 100)
JELLY_EYE = (200, 220, 255, 220)


def draw_jellyfish(draw, ox, oy, direction, frame):
    # Hovering bob — gentle float
    hover = [0, -3, -1, -2][frame]
    # Dome pulsing — expands/contracts
    pulse = [0, 2, 0, -1][frame]

    base_y = oy + 54
    cx = ox + 32
    dome_cy = base_y - 30 + hover
    dome_rx = 14 + pulse
    dome_ry = 12 + pulse

    # --- Hovering shadow on ground ---
    shadow_alpha = 60 + pulse * 5
    ellipse(draw, cx, base_y + 2, 10, 3, (80, 50, 100, shadow_alpha), outline=None)

    # --- Tentacles (drawn behind dome) ---
    # Tentacles sway based on frame and direction
    tent_sways = [
        [-2, 1, 3, -1],   # tentacle 0
        [1, -3, 0, 2],    # tentacle 1
        [3, 0, -2, 1],    # tentacle 2
        [-1, 2, -3, 0],   # tentacle 3
        [0, -1, 2, -2],   # tentacle 4
    ]

    # Direction offset for tentacles
    if direction == DOWN:
        dx_bias = 0
    elif direction == UP:
        dx_bias = 0
    elif direction == LEFT:
        dx_bias = 3
    else:
        dx_bias = -3

    tent_base_y = dome_cy + dome_ry - 2
    tent_positions = [-10, -5, 0, 5, 10]
    for i, tx_off in enumerate(tent_positions):
        sway = tent_sways[i][frame]
        tx = cx + tx_off + dx_bias
        # Each tentacle is 3-4 line segments curving downward
        mid_y = tent_base_y + 8 + (i % 3) * 2
        end_y = tent_base_y + 18 + (i % 2) * 4
        mid_x = tx + sway
        end_x = tx + sway * 2
        # Tentacle color fades with depth
        alpha_mid = 140 - i * 10
        alpha_tip = 90 - i * 8
        draw.line([(tx, tent_base_y), (mid_x, mid_y)],
                  fill=JELLY_TENTACLE[:3], width=2)
        draw.line([(mid_x, mid_y), (end_x, end_y)],
                  fill=JELLY_TENT_TIP[:3], width=1)
        # Curl at tip
        draw.point((end_x + sway // 2, end_y + 2), fill=JELLY_TENT_TIP[:3])

    # --- Dome / bell shape ---
    # Outer dome
    ellipse(draw, cx, dome_cy, dome_rx, dome_ry, JELLY_DOME)
    # Inner highlight (upper-left)
    ellipse(draw, cx - 3, dome_cy - 3, dome_rx - 4, dome_ry - 4,
            JELLY_DOME_LIGHT, outline=None)
    # Dark underside
    ellipse(draw, cx + 2, dome_cy + 3, dome_rx - 3, dome_ry - 5,
            JELLY_DOME_DARK, outline=None)

    # --- Bioluminescent glow dots (shift per frame) ---
    glow_positions = [
        (-6, -4), (-2, -6), (3, -3), (6, -1), (-4, 1),
        (1, -1), (5, -5), (-3, 3), (4, 2), (-7, -2),
    ]
    glow_colors = [JELLY_GLOW_A, JELLY_GLOW_B, JELLY_GLOW_C]
    for i, (gx, gy) in enumerate(glow_positions):
        idx = (i + frame) % len(glow_colors)
        c = glow_colors[idx]
        px = cx + gx + (frame % 2)
        py = dome_cy + gy - (frame % 3)
        # Only draw if inside dome area (rough check)
        if abs(gx) < dome_rx - 2 and abs(gy) < dome_ry - 2:
            draw.point((px, py), fill=c[:3])
            # Some dots slightly larger
            if i % 3 == 0:
                draw.point((px + 1, py), fill=c[:3])

    # --- Eyes (two glowing spots inside dome) ---
    if direction == DOWN:
        draw.rectangle([cx - 5, dome_cy, cx - 3, dome_cy + 2], fill=JELLY_EYE[:3])
        draw.point((cx - 4, dome_cy + 1), fill=BLACK)
        draw.rectangle([cx + 3, dome_cy, cx + 5, dome_cy + 2], fill=JELLY_EYE[:3])
        draw.point((cx + 4, dome_cy + 1), fill=BLACK)
    elif direction == UP:
        # Eyes not visible from behind, just faint glow
        draw.point((cx - 4, dome_cy), fill=JELLY_GLOW_A[:3])
        draw.point((cx + 4, dome_cy), fill=JELLY_GLOW_A[:3])
    elif direction == LEFT:
        draw.rectangle([cx - 7, dome_cy - 1, cx - 5, dome_cy + 1], fill=JELLY_EYE[:3])
        draw.point((cx - 6, dome_cy), fill=BLACK)
    else:  # RIGHT
        draw.rectangle([cx + 5, dome_cy - 1, cx + 7, dome_cy + 1], fill=JELLY_EYE[:3])
        draw.point((cx + 6, dome_cy), fill=BLACK)

    # --- Dome edge / rim (frilly border at bottom of bell) ---
    rim_y = dome_cy + dome_ry - 1
    for rx in range(-dome_rx + 2, dome_rx - 1, 3):
        wave = (frame + rx) % 2
        draw.point((cx + rx, rim_y + wave), fill=JELLY_DOME_LIGHT[:3])
        draw.point((cx + rx + 1, rim_y + wave), fill=JELLY_DOME_LIGHT[:3])


# ===================================================================
# GORILLA (ID 85) — massive upper body, long arms, knuckle-walking
# ===================================================================

# Gorilla palette
GORILLA_BODY = (60, 55, 50)
GORILLA_ARMS = (55, 50, 45)
GORILLA_SILVER = (120, 115, 110)
GORILLA_CHEST = (80, 75, 68)
GORILLA_DARK = (40, 35, 30)
GORILLA_LIGHT = (90, 85, 78)
GORILLA_SKIN = (50, 45, 42)
GORILLA_EYE = (140, 100, 40)


def draw_gorilla(draw, ox, oy, direction, frame):
    bob = [0, -2, 0, -1][frame]
    # Heavy arm swing
    arm_swing = [-4, 0, 4, 0][frame]
    leg_spread = [-2, 0, 2, 0][frame]

    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 18
    head_cy = body_cy - 14

    body_shadow = _darken(GORILLA_BODY, 0.7)

    # --- Legs (short, wide stance) ---
    if direction in (DOWN, UP):
        for side in [-1, 1]:
            lx = cx + side * 7 + (leg_spread if side == 1 else -leg_spread)
            draw.rectangle([lx - 5, body_cy + 10, lx + 5, base_y - 1],
                           fill=GORILLA_BODY, outline=OUTLINE)
            if direction == DOWN and side == -1:
                draw.rectangle([lx - 5, body_cy + 10, lx - 3, base_y - 4],
                               fill=GORILLA_LIGHT, outline=None)
            # Feet
            ellipse(draw, lx, base_y + 1, 6, 3, GORILLA_DARK)
    else:
        d = -1 if direction == LEFT else 1
        for offset in [leg_spread, -leg_spread]:
            lx = cx + d * 2 + offset
            draw.rectangle([lx - 5, body_cy + 10, lx + 5, base_y - 1],
                           fill=GORILLA_BODY, outline=OUTLINE)
            ellipse(draw, lx, base_y + 1, 6, 3, GORILLA_DARK)

    # --- Long arms (reaching to ground, knuckle-walking) ---
    if direction == DOWN:
        for side in [-1, 1]:
            ax = cx + side * 18
            shoulder_y = body_cy - 6
            elbow_y = body_cy + 4
            knuckle_y = base_y - 2
            ax_swing = arm_swing * side
            # Upper arm
            draw.polygon([
                (cx + side * 14, shoulder_y), (cx + side * 16, shoulder_y),
                (ax + ax_swing + side, elbow_y), (ax + ax_swing - side, elbow_y),
            ], fill=GORILLA_ARMS, outline=OUTLINE)
            # Forearm to ground
            draw.polygon([
                (ax + ax_swing - side * 2, elbow_y),
                (ax + ax_swing + side * 2, elbow_y),
                (ax + ax_swing + side * 2, knuckle_y),
                (ax + ax_swing - side * 2, knuckle_y),
            ], fill=GORILLA_ARMS, outline=OUTLINE)
            if side == -1:
                draw.rectangle([ax + ax_swing - 2, elbow_y, ax + ax_swing, knuckle_y - 4],
                               fill=GORILLA_LIGHT, outline=None)
            # Knuckle
            ellipse(draw, ax + ax_swing, knuckle_y + 1, 4, 2, GORILLA_SKIN)

    elif direction == UP:
        for side in [-1, 1]:
            ax = cx + side * 18
            shoulder_y = body_cy - 6
            elbow_y = body_cy + 4
            knuckle_y = base_y - 2
            ax_swing = arm_swing * side
            draw.polygon([
                (cx + side * 14, shoulder_y), (cx + side * 16, shoulder_y),
                (ax + ax_swing + side, elbow_y), (ax + ax_swing - side, elbow_y),
            ], fill=GORILLA_ARMS, outline=OUTLINE)
            draw.polygon([
                (ax + ax_swing - side * 2, elbow_y),
                (ax + ax_swing + side * 2, elbow_y),
                (ax + ax_swing + side * 2, knuckle_y),
                (ax + ax_swing - side * 2, knuckle_y),
            ], fill=GORILLA_ARMS, outline=OUTLINE)
            ellipse(draw, ax + ax_swing, knuckle_y + 1, 4, 2, GORILLA_SKIN)

    elif direction == LEFT:
        # Far arm (behind body)
        ax_far = cx + 8
        draw.polygon([
            (cx + 6, body_cy - 6), (cx + 8, body_cy - 4),
            (ax_far - arm_swing + 2, base_y - 2),
            (ax_far - arm_swing - 2, base_y - 2),
        ], fill=_darken(GORILLA_ARMS, 0.85), outline=OUTLINE)
        ellipse(draw, ax_far - arm_swing, base_y - 1, 3, 2, GORILLA_SKIN)
        # Near arm (in front)
        ax_near = cx - 14
        draw.polygon([
            (cx - 10, body_cy - 6), (cx - 12, body_cy - 4),
            (ax_near + arm_swing + 3, body_cy + 4),
            (ax_near + arm_swing - 1, body_cy + 4),
        ], fill=GORILLA_ARMS, outline=OUTLINE)
        draw.polygon([
            (ax_near + arm_swing - 2, body_cy + 4),
            (ax_near + arm_swing + 4, body_cy + 4),
            (ax_near + arm_swing + 4, base_y - 2),
            (ax_near + arm_swing - 2, base_y - 2),
        ], fill=GORILLA_ARMS, outline=OUTLINE)
        draw.rectangle([ax_near + arm_swing - 2, body_cy + 4,
                        ax_near + arm_swing, base_y - 6],
                       fill=GORILLA_LIGHT, outline=None)
        ellipse(draw, ax_near + arm_swing + 1, base_y - 1, 4, 2, GORILLA_SKIN)

    else:  # RIGHT
        # Far arm (behind body)
        ax_far = cx - 8
        draw.polygon([
            (cx - 6, body_cy - 6), (cx - 8, body_cy - 4),
            (ax_far + arm_swing + 2, base_y - 2),
            (ax_far + arm_swing - 2, base_y - 2),
        ], fill=_darken(GORILLA_ARMS, 0.85), outline=OUTLINE)
        ellipse(draw, ax_far + arm_swing, base_y - 1, 3, 2, GORILLA_SKIN)
        # Near arm (in front)
        ax_near = cx + 14
        draw.polygon([
            (cx + 10, body_cy - 6), (cx + 12, body_cy - 4),
            (ax_near - arm_swing - 3, body_cy + 4),
            (ax_near - arm_swing + 1, body_cy + 4),
        ], fill=GORILLA_ARMS, outline=OUTLINE)
        draw.polygon([
            (ax_near - arm_swing - 4, body_cy + 4),
            (ax_near - arm_swing + 2, body_cy + 4),
            (ax_near - arm_swing + 2, base_y - 2),
            (ax_near - arm_swing - 4, base_y - 2),
        ], fill=GORILLA_ARMS, outline=OUTLINE)
        ellipse(draw, ax_near - arm_swing - 1, base_y - 1, 4, 2, GORILLA_SKIN)

    # --- Body (massive upper body, broad chest and shoulders) ---
    if direction == DOWN:
        # Broad shoulders + torso
        ellipse(draw, cx, body_cy, 18, 14, GORILLA_BODY)
        ellipse(draw, cx + 3, body_cy + 4, 14, 10, body_shadow, outline=None)
        ellipse(draw, cx - 3, body_cy - 3, 12, 8, GORILLA_LIGHT, outline=None)
        # Chest
        ellipse(draw, cx, body_cy + 2, 10, 7, GORILLA_CHEST, outline=None)
        draw_fur_texture(draw, cx, body_cy, 28, 20, GORILLA_BODY, density=4)
    elif direction == UP:
        ellipse(draw, cx, body_cy, 18, 14, GORILLA_BODY)
        ellipse(draw, cx, body_cy, 14, 10, body_shadow, outline=None)
        # Silver-back stripe
        draw.rectangle([cx - 8, body_cy - 6, cx + 8, body_cy - 2],
                       fill=GORILLA_SILVER, outline=None)
        draw.rectangle([cx - 6, body_cy - 4, cx + 6, body_cy],
                       fill=_brighten(GORILLA_SILVER, 1.1), outline=None)
        draw_fur_texture(draw, cx, body_cy, 28, 20, GORILLA_BODY, density=4)
    elif direction == LEFT:
        ellipse(draw, cx - 2, body_cy, 16, 14, GORILLA_BODY)
        ellipse(draw, cx + 2, body_cy + 3, 12, 10, body_shadow, outline=None)
        ellipse(draw, cx - 4, body_cy - 3, 8, 6, GORILLA_LIGHT, outline=None)
        draw_fur_texture(draw, cx, body_cy, 24, 20, GORILLA_BODY, density=4)
    else:  # RIGHT
        ellipse(draw, cx + 2, body_cy, 16, 14, GORILLA_BODY)
        ellipse(draw, cx - 2, body_cy + 3, 12, 10, body_shadow, outline=None)
        ellipse(draw, cx + 4, body_cy - 3, 8, 6, GORILLA_LIGHT, outline=None)
        draw_fur_texture(draw, cx + 2, body_cy, 24, 20, GORILLA_BODY, density=4)

    # --- Head (small relative to body) ---
    if direction == DOWN:
        ellipse(draw, cx, head_cy, 9, 8, GORILLA_BODY)
        ellipse(draw, cx - 2, head_cy - 2, 6, 4, GORILLA_LIGHT, outline=None)
        # Brow ridge
        draw.rectangle([cx - 7, head_cy - 3, cx + 7, head_cy - 1],
                       fill=GORILLA_DARK, outline=None)
        # Eyes (deep-set under brow)
        draw.rectangle([cx - 5, head_cy, cx - 3, head_cy + 2], fill=GORILLA_EYE)
        draw.point((cx - 4, head_cy + 1), fill=BLACK)
        draw.rectangle([cx + 3, head_cy, cx + 5, head_cy + 2], fill=GORILLA_EYE)
        draw.point((cx + 4, head_cy + 1), fill=BLACK)
        # Flat nose
        draw.rectangle([cx - 3, head_cy + 3, cx + 3, head_cy + 5],
                       fill=GORILLA_SKIN, outline=OUTLINE)
        draw.point((cx - 1, head_cy + 4), fill=BLACK)
        draw.point((cx + 1, head_cy + 4), fill=BLACK)
        # Mouth
        draw.line([(cx - 3, head_cy + 6), (cx + 3, head_cy + 6)],
                  fill=GORILLA_DARK, width=1)

    elif direction == UP:
        ellipse(draw, cx, head_cy, 9, 8, GORILLA_BODY)
        ellipse(draw, cx, head_cy, 7, 6, body_shadow, outline=None)
        # Sagittal crest (ridge on top of skull)
        draw.rectangle([cx - 2, head_cy - 7, cx + 2, head_cy - 3],
                       fill=GORILLA_DARK, outline=None)

    elif direction == LEFT:
        ellipse(draw, cx - 4, head_cy, 8, 8, GORILLA_BODY)
        ellipse(draw, cx - 6, head_cy - 2, 5, 4, GORILLA_LIGHT, outline=None)
        # Brow ridge
        draw.rectangle([cx - 10, head_cy - 3, cx - 2, head_cy - 1],
                       fill=GORILLA_DARK, outline=None)
        # Eye
        draw.rectangle([cx - 8, head_cy, cx - 6, head_cy + 2], fill=GORILLA_EYE)
        draw.point((cx - 7, head_cy + 1), fill=BLACK)
        # Flat nose/muzzle
        draw.polygon([(cx - 10, head_cy + 2), (cx - 12, head_cy + 4),
                      (cx - 10, head_cy + 6), (cx - 8, head_cy + 4)],
                     fill=GORILLA_SKIN, outline=OUTLINE)
        draw.point((cx - 11, head_cy + 4), fill=BLACK)

    else:  # RIGHT
        ellipse(draw, cx + 4, head_cy, 8, 8, GORILLA_BODY)
        ellipse(draw, cx + 6, head_cy - 2, 5, 4, GORILLA_LIGHT, outline=None)
        draw.rectangle([cx + 2, head_cy - 3, cx + 10, head_cy - 1],
                       fill=GORILLA_DARK, outline=None)
        draw.rectangle([cx + 6, head_cy, cx + 8, head_cy + 2], fill=GORILLA_EYE)
        draw.point((cx + 7, head_cy + 1), fill=BLACK)
        draw.polygon([(cx + 10, head_cy + 2), (cx + 12, head_cy + 4),
                      (cx + 10, head_cy + 6), (cx + 8, head_cy + 4)],
                     fill=GORILLA_SKIN, outline=OUTLINE)
        draw.point((cx + 11, head_cy + 4), fill=BLACK)


# ===================================================================
# CHAMELEON (ID 86) — upright lizard, curled tail, turret eyes, color shift
# ===================================================================

# Chameleon palette
CHAM_BODY = (80, 180, 80)
CHAM_HIGHLIGHT = (120, 220, 110)
CHAM_ACCENT = (200, 200, 50)
CHAM_DARK = (50, 120, 50)
CHAM_BELLY = (140, 210, 130)
CHAM_EYE_DOME = (100, 200, 100)
CHAM_EYE_PUPIL = (30, 30, 25)
CHAM_TOE = (60, 140, 60)
# Color-shift spots per frame
CHAM_SPOTS = [
    (200, 200, 50),   # yellow
    (200, 100, 50),   # orange
    (50, 150, 200),   # blue
    (180, 80, 180),   # purple
]


def draw_chameleon(draw, ox, oy, direction, frame):
    bob = [0, -1, 0, -1][frame]
    # Slow deliberate walk
    leg_step = [-2, 0, 2, 0][frame]
    # Eye direction shifts per frame (pupil offset)
    eye_dx = [-1, 1, 0, -1][frame]
    eye_dy = [0, -1, 1, 0][frame]
    spot_color = CHAM_SPOTS[frame]

    base_y = oy + 54 + bob
    cx = ox + 32
    body_cy = base_y - 18
    head_cy = body_cy - 14

    body_dark = _darken(CHAM_BODY, 0.75)

    def draw_curled_tail(tx, ty, curl_dir):
        """Draw a distinctive curled spiral tail."""
        # Tail base
        draw.polygon([
            (tx, ty - 2), (tx, ty + 2),
            (tx + curl_dir * 8, ty + 1),
            (tx + curl_dir * 8, ty - 1),
        ], fill=CHAM_BODY, outline=OUTLINE)
        # Spiral curl (series of small arcs approximated by points/lines)
        spiral_cx = tx + curl_dir * 10
        spiral_cy = ty
        # Outer ring
        r = 5
        points = []
        import math
        for a in range(0, 300, 30):
            rad = math.radians(a)
            px = spiral_cx + int(r * math.cos(rad) * curl_dir)
            py = spiral_cy + int(r * math.sin(rad))
            points.append((px, py))
            r -= 0.4
        if len(points) >= 2:
            draw.line(points, fill=CHAM_BODY, width=2)
        # Inner spiral
        for a in range(0, 200, 40):
            rad = math.radians(a)
            r2 = 3 - a / 100
            if r2 < 0.5:
                break
            px = spiral_cx + int(r2 * math.cos(rad) * curl_dir)
            py = spiral_cy + int(r2 * math.sin(rad))
            draw.point((px, py), fill=CHAM_DARK)

    def draw_turret_eye(ex, ey, pupil_dx, pupil_dy):
        """Draw a prominent dome/turret eye."""
        ellipse(draw, ex, ey, 5, 5, CHAM_EYE_DOME)
        ellipse(draw, ex, ey, 3, 3, _brighten(CHAM_EYE_DOME, 1.2), outline=None)
        # Pupil (shifts position for independent eye movement)
        draw.point((ex + pupil_dx, ey + pupil_dy), fill=CHAM_EYE_PUPIL)
        draw.point((ex + pupil_dx + 1, ey + pupil_dy), fill=CHAM_EYE_PUPIL)

    def draw_gripping_feet(fx, fy, facing):
        """Draw split-toe gripping feet (2+3 syndactyl)."""
        if facing in ('front', 'back'):
            # Front group (2 toes)
            draw.line([(fx - 3, fy), (fx - 4, fy + 3)], fill=CHAM_TOE, width=2)
            draw.line([(fx - 1, fy), (fx - 2, fy + 3)], fill=CHAM_TOE, width=2)
            # Back group (3 toes)
            draw.line([(fx + 1, fy), (fx + 2, fy + 3)], fill=CHAM_TOE, width=1)
            draw.line([(fx + 3, fy), (fx + 4, fy + 3)], fill=CHAM_TOE, width=1)
            draw.line([(fx + 2, fy), (fx + 3, fy + 2)], fill=CHAM_TOE, width=1)
        else:
            d = -1 if facing == 'left' else 1
            draw.line([(fx, fy), (fx + d * 3, fy + 2)], fill=CHAM_TOE, width=2)
            draw.line([(fx, fy + 1), (fx + d * 2, fy + 3)], fill=CHAM_TOE, width=1)
            draw.line([(fx, fy - 1), (fx - d * 2, fy + 2)], fill=CHAM_TOE, width=1)

    def draw_color_spots(scx, scy, w, h):
        """Draw subtle color-shifting spots on body."""
        spot_positions = [
            (-3, -2), (4, -1), (-1, 3), (3, 4), (-4, 1),
            (2, -4), (-2, 2), (5, 0),
        ]
        for i, (sx, sy) in enumerate(spot_positions):
            if abs(sx) < w // 2 and abs(sy) < h // 2:
                c = spot_color if (i + frame) % 3 == 0 else _darken(spot_color, 0.6)
                draw.point((scx + sx, scy + sy), fill=c)

    # --- Tail (behind body) ---
    if direction == DOWN:
        draw_curled_tail(cx + 10, body_cy + 4, 1)
    elif direction == UP:
        draw_curled_tail(cx - 10, body_cy + 4, -1)
    elif direction == LEFT:
        draw_curled_tail(cx + 12, body_cy + 2, 1)
    else:
        draw_curled_tail(cx - 12, body_cy + 2, -1)

    # --- Legs ---
    if direction in (DOWN, UP):
        for side in [-1, 1]:
            lx = cx + side * 8 + (leg_step if side == 1 else -leg_step)
            draw.rectangle([lx - 3, body_cy + 8, lx + 3, base_y - 2],
                           fill=CHAM_BODY, outline=OUTLINE)
            if direction == DOWN and side == -1:
                draw.rectangle([lx - 3, body_cy + 8, lx - 1, base_y - 4],
                               fill=CHAM_HIGHLIGHT, outline=None)
            foot_face = 'front' if direction == DOWN else 'back'
            draw_gripping_feet(lx, base_y - 1, foot_face)
    else:
        d = -1 if direction == LEFT else 1
        for offset in [leg_step, -leg_step]:
            lx = cx + d * 2 + offset
            draw.rectangle([lx - 3, body_cy + 8, lx + 3, base_y - 2],
                           fill=CHAM_BODY, outline=OUTLINE)
            facing = 'left' if direction == LEFT else 'right'
            draw_gripping_feet(lx, base_y - 1, facing)

    # --- Body (upright lizard shape) ---
    if direction == DOWN:
        ellipse(draw, cx, body_cy, 12, 12, CHAM_BODY)
        ellipse(draw, cx + 2, body_cy + 3, 8, 8, body_dark, outline=None)
        ellipse(draw, cx - 2, body_cy - 2, 8, 6, CHAM_HIGHLIGHT, outline=None)
        ellipse(draw, cx, body_cy + 3, 6, 4, CHAM_BELLY, outline=None)
        draw_scale_texture(draw, cx, body_cy, 16, 16, _darken(CHAM_BODY, 0.85))
        draw_color_spots(cx, body_cy, 16, 16)
    elif direction == UP:
        ellipse(draw, cx, body_cy, 12, 12, CHAM_BODY)
        ellipse(draw, cx, body_cy, 8, 8, body_dark, outline=None)
        draw_scale_texture(draw, cx, body_cy, 16, 16, _darken(CHAM_BODY, 0.85))
        draw_color_spots(cx, body_cy, 16, 16)
    elif direction == LEFT:
        ellipse(draw, cx - 2, body_cy, 10, 12, CHAM_BODY)
        ellipse(draw, cx, body_cy + 3, 7, 8, body_dark, outline=None)
        ellipse(draw, cx - 4, body_cy - 2, 6, 6, CHAM_HIGHLIGHT, outline=None)
        draw_scale_texture(draw, cx - 2, body_cy, 14, 16, _darken(CHAM_BODY, 0.85))
        draw_color_spots(cx - 2, body_cy, 14, 16)
    else:
        ellipse(draw, cx + 2, body_cy, 10, 12, CHAM_BODY)
        ellipse(draw, cx, body_cy + 3, 7, 8, body_dark, outline=None)
        ellipse(draw, cx + 4, body_cy - 2, 6, 6, CHAM_HIGHLIGHT, outline=None)
        draw_scale_texture(draw, cx + 2, body_cy, 14, 16, _darken(CHAM_BODY, 0.85))
        draw_color_spots(cx + 2, body_cy, 14, 16)

    # --- Head (with turret eyes) ---
    if direction == DOWN:
        ellipse(draw, cx, head_cy, 9, 8, CHAM_BODY)
        ellipse(draw, cx - 2, head_cy - 1, 6, 4, CHAM_HIGHLIGHT, outline=None)
        # Prominent turret eyes on top of head
        draw_turret_eye(cx - 7, head_cy - 3, eye_dx, eye_dy)
        draw_turret_eye(cx + 7, head_cy - 3, -eye_dx, eye_dy)  # opposite direction
        # Snout
        draw.polygon([(cx - 3, head_cy + 4), (cx + 3, head_cy + 4),
                      (cx, head_cy + 8)], fill=CHAM_BODY, outline=OUTLINE)
        # Mouth line
        draw.line([(cx - 4, head_cy + 5), (cx + 4, head_cy + 5)],
                  fill=CHAM_DARK, width=1)

    elif direction == UP:
        ellipse(draw, cx, head_cy, 9, 8, CHAM_BODY)
        ellipse(draw, cx, head_cy, 6, 5, body_dark, outline=None)
        # Turret eyes visible from behind
        draw_turret_eye(cx - 7, head_cy - 3, -eye_dx, eye_dy)
        draw_turret_eye(cx + 7, head_cy - 3, eye_dx, eye_dy)

    elif direction == LEFT:
        ellipse(draw, cx - 4, head_cy, 8, 7, CHAM_BODY)
        ellipse(draw, cx - 6, head_cy - 1, 5, 4, CHAM_HIGHLIGHT, outline=None)
        # Turret eye (one visible, prominent)
        draw_turret_eye(cx - 5, head_cy - 5, eye_dx, eye_dy)
        # Long snout
        draw.polygon([(cx - 10, head_cy), (cx - 10, head_cy + 3),
                      (cx - 16, head_cy + 2)], fill=CHAM_BODY, outline=OUTLINE)
        draw.line([(cx - 10, head_cy + 2), (cx - 16, head_cy + 2)],
                  fill=CHAM_DARK, width=1)

    else:  # RIGHT
        ellipse(draw, cx + 4, head_cy, 8, 7, CHAM_BODY)
        ellipse(draw, cx + 6, head_cy - 1, 5, 4, CHAM_HIGHLIGHT, outline=None)
        draw_turret_eye(cx + 5, head_cy - 5, -eye_dx, eye_dy)
        draw.polygon([(cx + 10, head_cy), (cx + 10, head_cy + 3),
                      (cx + 16, head_cy + 2)], fill=CHAM_BODY, outline=OUTLINE)
        draw.line([(cx + 10, head_cy + 2), (cx + 16, head_cy + 2)],
                  fill=CHAM_DARK, width=1)


# ─── REGISTRY ─────────────────────────────────────────────────────
BEAST_DRAW_FUNCTIONS = {
    'wolf': draw_wolf,
    'serpent': draw_serpent,
    'spider': draw_spider,
    'bear': draw_bear,
    'scorpion': draw_scorpion,
    'hawk': draw_hawk,
    'shark': draw_shark,
    'beetle': draw_beetle,
    'treant': draw_treant,
    'phoenix': draw_phoenix,
    'hydra': draw_hydra,
    'mantis': draw_mantis,
    'jellyfish': draw_jellyfish,
    'gorilla': draw_gorilla,
    'chameleon': draw_chameleon,
}


def main():
    for name, draw_func in BEAST_DRAW_FUNCTIONS.items():
        generate_character(name, draw_func=draw_func)
    print(f"\nGenerated {len(BEAST_DRAW_FUNCTIONS)} beast character sprites.")


if __name__ == "__main__":
    main()
