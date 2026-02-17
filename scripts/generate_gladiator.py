#!/usr/bin/env python3
"""Generate sprites/gladiator.png — 4-column x 4-row character spritesheet.

256x256 PNG, 64x64 per frame.
Row layout: Down=0, Up=1, Left=2, Right=3
4 walking animation frames per direction.

Style matches Spaceman: big round head, round body, small limbs, dark outlines.
Theme: Roman gladiator — bronze helmet with tall flowing red crest, gold-trimmed
armor, short sword in right hand, round shield on left arm.
Enhanced 64x64: detailed shield face with cross emblem, sword with fuller/guard,
muscle definition on arms, pauldrons with rivet dots.
"""

from PIL import Image, ImageDraw

FRAME_SIZE = 64
COLS = 4
ROWS = 4
IMG_W = FRAME_SIZE * COLS   # 256
IMG_H = FRAME_SIZE * ROWS   # 256

# Colors — matching Spaceman's palette approach (dark outline + fill + highlight)
OUTLINE = (40, 35, 35)
SKIN = (220, 180, 140)
SKIN_DARK = (185, 145, 110)
BRONZE = (180, 135, 55)
BRONZE_LIGHT = (215, 170, 75)
BRONZE_DARK = (130, 95, 35)
RED = (175, 45, 45)
RED_DARK = (130, 35, 35)
RED_LIGHT = (200, 70, 70)
RED_BRIGHT = (220, 55, 55)
GOLD = (245, 200, 65)
GOLD_DARK = (200, 160, 40)
BROWN = (110, 75, 45)
BROWN_DARK = (80, 55, 35)
BLACK = (30, 30, 30)
WHITE_GLINT = (255, 245, 220)
STEEL = (170, 180, 195)
STEEL_LIGHT = (210, 215, 225)
STEEL_DARK = (120, 130, 145)
SHIELD_BROWN = (140, 90, 50)
SHIELD_BROWN_DARK = (100, 65, 35)
# New detail colors
MUSCLE_LINE = (200, 160, 120)
RIVET = (230, 210, 130)
SHIELD_EMBLEM = (170, 120, 60)

DOWN, UP, LEFT, RIGHT = 0, 1, 2, 3


def ellipse(draw, cx, cy, rx, ry, fill, outline=OUTLINE):
    """Draw an outlined ellipse centered at (cx, cy)."""
    draw.ellipse([cx - rx, cy - ry, cx + rx, cy + ry], fill=fill, outline=outline)


def pill(draw, cx, cy, rx, ry, fill, outline=OUTLINE):
    """Draw a rounded rectangle (pill shape) as an ellipse."""
    draw.rounded_rectangle([cx - rx, cy - ry, cx + rx, cy + ry],
                           radius=min(rx, ry), fill=fill, outline=outline)


def draw_shield_emblem(draw, cx, cy):
    """Draw a cross emblem pattern on the shield face."""
    # Vertical bar of cross
    draw.line([(cx, cy - 6), (cx, cy + 6)], fill=SHIELD_EMBLEM, width=2)
    # Horizontal bar of cross
    draw.line([(cx - 6, cy), (cx + 6, cy)], fill=SHIELD_EMBLEM, width=2)
    # Corner rivets
    for dx, dy in [(-4, -4), (4, -4), (-4, 4), (4, 4)]:
        draw.point((cx + dx, cy + dy), fill=RIVET)


def draw_gladiator(draw, ox, oy, direction, frame):
    """Draw a single gladiator frame at offset (ox, oy).

    Proportions match Spaceman: big round head ~22px, body ~16px tall,
    small stick legs, centered in 64x64 frame.
    """
    # Walking bob
    bob = [0, -2, 0, -1][frame]
    leg_spread = [-4, 0, 4, 0][frame]

    # Anchor: bottom of feet at oy+56, so character sits in lower portion of frame
    base_y = oy + 54 + bob
    # Body center
    body_cx = ox + 32
    body_cy = base_y - 20
    # Head center (big round head above body)
    head_cy = body_cy - 20

    if direction == DOWN:
        # --- Legs (behind body) ---
        # Left leg
        draw.rectangle([body_cx - 10 + leg_spread, body_cy + 10,
                        body_cx - 4 + leg_spread, base_y], fill=BROWN, outline=OUTLINE)
        # Right leg
        draw.rectangle([body_cx + 4 - leg_spread, body_cy + 10,
                        body_cx + 10 - leg_spread, base_y], fill=BROWN, outline=OUTLINE)
        # Greave accents
        draw.rectangle([body_cx - 10 + leg_spread, base_y - 6,
                        body_cx - 4 + leg_spread, base_y], fill=BRONZE_DARK, outline=OUTLINE)
        draw.rectangle([body_cx + 4 - leg_spread, base_y - 6,
                        body_cx + 10 - leg_spread, base_y], fill=BRONZE_DARK, outline=OUTLINE)
        # Gold knee accents
        draw.line([body_cx - 10 + leg_spread, base_y - 6,
                   body_cx - 4 + leg_spread, base_y - 6], fill=GOLD)
        draw.line([body_cx + 4 - leg_spread, base_y - 6,
                   body_cx + 10 - leg_spread, base_y - 6], fill=GOLD)

        # --- Sword (right hand, extends below) ---
        sword_x = body_cx + 16
        # Blade with fuller detail
        draw.rectangle([sword_x, body_cy + 4, sword_x + 2, body_cy + 20],
                       fill=STEEL, outline=OUTLINE)
        # Fuller line (groove in blade)
        draw.line([sword_x + 1, body_cy + 6, sword_x + 1, body_cy + 18], fill=STEEL_LIGHT)
        # Blade tip
        draw.point((sword_x, body_cy + 22), fill=STEEL_LIGHT)
        draw.point((sword_x + 1, body_cy + 22), fill=STEEL_LIGHT)
        draw.point((sword_x + 2, body_cy + 22), fill=STEEL_LIGHT)
        # Crossguard (wider)
        draw.rectangle([sword_x - 2, body_cy + 2, sword_x + 4, body_cy + 4],
                       fill=GOLD, outline=None)
        # Guard detail
        draw.point((sword_x - 2, body_cy + 3), fill=GOLD_DARK)
        draw.point((sword_x + 4, body_cy + 3), fill=GOLD_DARK)

        # --- Shield (left arm) ---
        ellipse(draw, body_cx - 20, body_cy, 8, 10, SHIELD_BROWN, outline=OUTLINE)
        ellipse(draw, body_cx - 20, body_cy, 4, 6, SHIELD_BROWN_DARK, outline=None)
        # Shield boss (center nub)
        draw.rectangle([body_cx - 21, body_cy - 1, body_cx - 19, body_cy + 1], fill=GOLD)
        # Shield cross emblem
        draw_shield_emblem(draw, body_cx - 20, body_cy)

        # --- Body (round torso with armor) ---
        ellipse(draw, body_cx, body_cy, 14, 12, BRONZE)
        # Armor highlight
        ellipse(draw, body_cx, body_cy - 2, 10, 8, BRONZE_LIGHT)
        # Gold trim on armor edge
        draw.arc([body_cx - 14, body_cy - 12, body_cx + 14, body_cy + 12],
                 start=200, end=340, fill=GOLD)
        # Red belt
        draw.rectangle([body_cx - 14, body_cy + 6, body_cx + 14, body_cy + 10],
                       fill=RED, outline=OUTLINE)
        # Gold belt buckle
        draw.rectangle([body_cx - 2, body_cy + 6, body_cx + 2, body_cy + 10],
                       fill=GOLD, outline=None)

        # --- Arms (small, at sides) ---
        # Left arm (behind shield)
        draw.rectangle([body_cx - 18, body_cy - 6, body_cx - 12, body_cy + 6],
                       fill=SKIN, outline=OUTLINE)
        # Muscle definition on left arm
        draw.line([(body_cx - 15, body_cy - 4), (body_cx - 15, body_cy + 4)], fill=MUSCLE_LINE)
        # Right arm (holding sword)
        draw.rectangle([body_cx + 12, body_cy - 6, body_cx + 18, body_cy + 6],
                       fill=SKIN, outline=OUTLINE)
        # Muscle definition on right arm
        draw.line([(body_cx + 15, body_cy - 4), (body_cx + 15, body_cy + 4)], fill=MUSCLE_LINE)

        # Shoulder pauldrons (more defined)
        ellipse(draw, body_cx - 14, body_cy - 8, 8, 6, BRONZE)
        ellipse(draw, body_cx - 14, body_cy - 8, 4, 2, BRONZE_LIGHT, outline=None)
        draw.arc([body_cx - 22, body_cy - 14, body_cx - 6, body_cy - 2],
                 start=180, end=360, fill=GOLD)
        # Rivet dots on left pauldron
        draw.point((body_cx - 18, body_cy - 10), fill=RIVET)
        draw.point((body_cx - 10, body_cy - 10), fill=RIVET)
        ellipse(draw, body_cx + 14, body_cy - 8, 8, 6, BRONZE)
        ellipse(draw, body_cx + 14, body_cy - 8, 4, 2, BRONZE_LIGHT, outline=None)
        draw.arc([body_cx + 6, body_cy - 14, body_cx + 22, body_cy - 2],
                 start=180, end=360, fill=GOLD)
        # Rivet dots on right pauldron
        draw.point((body_cx + 10, body_cy - 10), fill=RIVET)
        draw.point((body_cx + 18, body_cy - 10), fill=RIVET)

        # --- Head (big round helmet) ---
        # Helmet (main dome)
        ellipse(draw, body_cx, head_cy, 16, 14, BRONZE)
        # Helmet highlight band
        draw.arc([body_cx - 16, head_cy - 14, body_cx + 16, head_cy + 14],
                 start=210, end=330, fill=BRONZE_LIGHT)
        # Face opening
        ellipse(draw, body_cx, head_cy + 4, 10, 8, SKIN)
        # Helmet brim
        draw.rectangle([body_cx - 16, head_cy + 2, body_cx + 16, head_cy + 6],
                       fill=BRONZE_DARK, outline=OUTLINE)
        # Gold trim on brim
        draw.line([body_cx - 16, head_cy + 2, body_cx + 16, head_cy + 2], fill=GOLD)
        # Eyes
        draw.rectangle([body_cx - 6, head_cy + 2, body_cx - 2, head_cy + 6], fill=BLACK)
        draw.rectangle([body_cx + 2, head_cy + 2, body_cx + 6, head_cy + 6], fill=BLACK)
        # White glint on helmet dome
        draw.point((body_cx - 4, head_cy - 10), fill=WHITE_GLINT)
        draw.point((body_cx - 6, head_cy - 8), fill=WHITE_GLINT)
        draw.point((body_cx - 5, head_cy - 9), fill=WHITE_GLINT)
        # Crest (tall, flowing red plume)
        # Wider base, tapers up
        draw.rectangle([body_cx - 4, head_cy - 14, body_cx + 4, head_cy - 10],
                       fill=RED, outline=OUTLINE)
        draw.rectangle([body_cx - 2, head_cy - 22, body_cx + 2, head_cy - 14],
                       fill=RED, outline=OUTLINE)
        draw.rectangle([body_cx - 2, head_cy - 26, body_cx, head_cy - 22],
                       fill=RED_LIGHT, outline=None)
        # Crest highlight streaks
        draw.line([body_cx, head_cy - 24, body_cx, head_cy - 16], fill=RED_BRIGHT)
        draw.line([body_cx - 2, head_cy - 20, body_cx - 2, head_cy - 14], fill=RED_LIGHT)
        draw.line([body_cx + 1, head_cy - 22, body_cx + 1, head_cy - 16], fill=RED_BRIGHT)
        # Gold accent at crest base
        draw.line([body_cx - 4, head_cy - 10, body_cx + 4, head_cy - 10], fill=GOLD)

    elif direction == UP:
        # --- Legs ---
        draw.rectangle([body_cx - 10 + leg_spread, body_cy + 10,
                        body_cx - 4 + leg_spread, base_y], fill=BROWN, outline=OUTLINE)
        draw.rectangle([body_cx + 4 - leg_spread, body_cy + 10,
                        body_cx + 10 - leg_spread, base_y], fill=BROWN, outline=OUTLINE)
        draw.rectangle([body_cx - 10 + leg_spread, base_y - 6,
                        body_cx - 4 + leg_spread, base_y], fill=BRONZE_DARK, outline=OUTLINE)
        draw.rectangle([body_cx + 4 - leg_spread, base_y - 6,
                        body_cx + 10 - leg_spread, base_y], fill=BRONZE_DARK, outline=OUTLINE)
        # Gold knee accents
        draw.line([body_cx - 10 + leg_spread, base_y - 6,
                   body_cx - 4 + leg_spread, base_y - 6], fill=GOLD)
        draw.line([body_cx + 4 - leg_spread, base_y - 6,
                   body_cx + 10 - leg_spread, base_y - 6], fill=GOLD)

        # --- Cape (visible from behind, flowing) ---
        cape_sway = [0, 2, 0, -2][frame]
        draw.rounded_rectangle([body_cx - 12 + cape_sway, body_cy - 6,
                                body_cx + 12 + cape_sway, body_cy + 14],
                               radius=6, fill=RED, outline=OUTLINE)
        draw.rounded_rectangle([body_cx - 10 + cape_sway, body_cy - 4,
                                body_cx + 10 + cape_sway, body_cy + 12],
                               radius=4, fill=RED_DARK, outline=None)

        # --- Sword (right hand, visible from behind) ---
        sword_x = body_cx + 16
        draw.rectangle([sword_x, body_cy + 4, sword_x + 2, body_cy + 20],
                       fill=STEEL, outline=OUTLINE)
        draw.line([sword_x + 1, body_cy + 6, sword_x + 1, body_cy + 18], fill=STEEL_LIGHT)
        draw.point((sword_x, body_cy + 22), fill=STEEL_LIGHT)
        draw.point((sword_x + 1, body_cy + 22), fill=STEEL_LIGHT)
        draw.point((sword_x + 2, body_cy + 22), fill=STEEL_LIGHT)
        draw.rectangle([sword_x - 2, body_cy + 2, sword_x + 4, body_cy + 4],
                       fill=GOLD, outline=None)

        # --- Shield (left arm, from behind) ---
        ellipse(draw, body_cx - 20, body_cy, 8, 10, SHIELD_BROWN, outline=OUTLINE)
        ellipse(draw, body_cx - 20, body_cy, 4, 6, SHIELD_BROWN_DARK, outline=None)

        # --- Body ---
        ellipse(draw, body_cx, body_cy, 14, 12, BRONZE)
        ellipse(draw, body_cx, body_cy - 2, 10, 8, BRONZE_DARK)
        # Gold trim on back
        draw.arc([body_cx - 14, body_cy - 12, body_cx + 14, body_cy + 12],
                 start=200, end=340, fill=GOLD)

        # --- Arms ---
        draw.rectangle([body_cx - 18, body_cy - 6, body_cx - 12, body_cy + 6],
                       fill=SKIN, outline=OUTLINE)
        draw.rectangle([body_cx + 12, body_cy - 6, body_cx + 18, body_cy + 6],
                       fill=SKIN, outline=OUTLINE)
        # Shoulder pauldrons
        ellipse(draw, body_cx - 14, body_cy - 8, 8, 6, BRONZE)
        ellipse(draw, body_cx - 14, body_cy - 8, 4, 2, BRONZE_LIGHT, outline=None)
        draw.arc([body_cx - 22, body_cy - 14, body_cx - 6, body_cy - 2],
                 start=180, end=360, fill=GOLD)
        draw.point((body_cx - 18, body_cy - 10), fill=RIVET)
        draw.point((body_cx - 10, body_cy - 10), fill=RIVET)
        ellipse(draw, body_cx + 14, body_cy - 8, 8, 6, BRONZE)
        ellipse(draw, body_cx + 14, body_cy - 8, 4, 2, BRONZE_LIGHT, outline=None)
        draw.arc([body_cx + 6, body_cy - 14, body_cx + 22, body_cy - 2],
                 start=180, end=360, fill=GOLD)
        draw.point((body_cx + 10, body_cy - 10), fill=RIVET)
        draw.point((body_cx + 18, body_cy - 10), fill=RIVET)

        # --- Head (back of helmet) ---
        ellipse(draw, body_cx, head_cy, 16, 14, BRONZE)
        ellipse(draw, body_cx, head_cy, 12, 10, BRONZE_DARK)
        # Gold trim band on back of helmet
        draw.arc([body_cx - 16, head_cy - 14, body_cx + 16, head_cy + 14],
                 start=20, end=160, fill=GOLD)
        # Crest (tall, flowing red plume)
        draw.rectangle([body_cx - 4, head_cy - 14, body_cx + 4, head_cy - 10],
                       fill=RED, outline=OUTLINE)
        draw.rectangle([body_cx - 2, head_cy - 22, body_cx + 2, head_cy - 14],
                       fill=RED, outline=OUTLINE)
        draw.rectangle([body_cx - 2, head_cy - 26, body_cx, head_cy - 22],
                       fill=RED_LIGHT, outline=None)
        draw.line([body_cx, head_cy - 24, body_cx, head_cy - 16], fill=RED_BRIGHT)
        draw.line([body_cx - 2, head_cy - 20, body_cx - 2, head_cy - 14], fill=RED_LIGHT)
        draw.line([body_cx + 1, head_cy - 22, body_cx + 1, head_cy - 16], fill=RED_BRIGHT)
        # Gold accent at crest base
        draw.line([body_cx - 4, head_cy - 10, body_cx + 4, head_cy - 10], fill=GOLD)

    elif direction == LEFT:
        # --- Legs (side view) ---
        # Back leg
        draw.rectangle([body_cx - 2 - leg_spread, body_cy + 10,
                        body_cx + 4 - leg_spread, base_y], fill=BROWN_DARK, outline=OUTLINE)
        draw.rectangle([body_cx - 2 - leg_spread, base_y - 6,
                        body_cx + 4 - leg_spread, base_y], fill=BRONZE_DARK, outline=OUTLINE)
        # Front leg
        draw.rectangle([body_cx - 8 + leg_spread, body_cy + 10,
                        body_cx - 2 + leg_spread, base_y], fill=BROWN, outline=OUTLINE)
        draw.rectangle([body_cx - 8 + leg_spread, base_y - 6,
                        body_cx - 2 + leg_spread, base_y], fill=BRONZE_DARK, outline=OUTLINE)
        # Gold knee accents
        draw.line([body_cx - 2 - leg_spread, base_y - 6,
                   body_cx + 4 - leg_spread, base_y - 6], fill=GOLD)
        draw.line([body_cx - 8 + leg_spread, base_y - 6,
                   body_cx - 2 + leg_spread, base_y - 6], fill=GOLD)

        # --- Cape (flowing right/behind) ---
        cape_sway = [0, 2, 0, -2][frame]
        draw.rounded_rectangle([body_cx + 6, body_cy - 8,
                                body_cx + 16 + cape_sway, body_cy + 12],
                               radius=6, fill=RED, outline=OUTLINE)

        # --- Sword (right hand, behind body in left-facing view) ---
        sword_x = body_cx + 10
        draw.rectangle([sword_x, body_cy + 4, sword_x + 2, body_cy + 20],
                       fill=STEEL, outline=OUTLINE)
        draw.line([sword_x + 1, body_cy + 6, sword_x + 1, body_cy + 18], fill=STEEL_LIGHT)
        draw.point((sword_x, body_cy + 22), fill=STEEL_LIGHT)
        draw.point((sword_x + 1, body_cy + 22), fill=STEEL_LIGHT)
        draw.point((sword_x + 2, body_cy + 22), fill=STEEL_LIGHT)
        draw.rectangle([sword_x - 2, body_cy + 2, sword_x + 4, body_cy + 4],
                       fill=GOLD, outline=None)

        # --- Body ---
        ellipse(draw, body_cx - 2, body_cy, 12, 12, BRONZE)
        ellipse(draw, body_cx - 2, body_cy - 2, 8, 8, BRONZE_LIGHT)
        # Gold trim on armor edge
        draw.arc([body_cx - 14, body_cy - 12, body_cx + 10, body_cy + 12],
                 start=220, end=320, fill=GOLD)
        # Red belt
        draw.rectangle([body_cx - 14, body_cy + 6, body_cx + 10, body_cy + 10],
                       fill=RED, outline=OUTLINE)
        # Gold belt buckle
        draw.rectangle([body_cx - 4, body_cy + 6, body_cx, body_cy + 10],
                       fill=GOLD, outline=None)

        # --- Shield (left arm, facing viewer) ---
        ellipse(draw, body_cx - 16, body_cy - 2, 8, 10, SHIELD_BROWN, outline=OUTLINE)
        ellipse(draw, body_cx - 16, body_cy - 2, 4, 6, SHIELD_BROWN_DARK, outline=None)
        # Shield boss
        draw.rectangle([body_cx - 17, body_cy - 3, body_cx - 15, body_cy - 1], fill=GOLD)
        # Shield cross emblem
        draw_shield_emblem(draw, body_cx - 16, body_cy - 2)
        # Shield rim highlight
        draw.arc([body_cx - 24, body_cy - 12, body_cx - 8, body_cy + 8],
                 start=240, end=360, fill=GOLD_DARK)

        # --- Arm (front, leading with shield) ---
        draw.rectangle([body_cx - 14, body_cy - 4, body_cx - 8, body_cy + 6],
                       fill=SKIN, outline=OUTLINE)
        # Muscle definition
        draw.line([(body_cx - 11, body_cy - 2), (body_cx - 11, body_cy + 4)], fill=MUSCLE_LINE)
        # Shoulder pauldron (more defined)
        ellipse(draw, body_cx - 10, body_cy - 8, 8, 6, BRONZE)
        ellipse(draw, body_cx - 10, body_cy - 8, 4, 2, BRONZE_LIGHT, outline=None)
        draw.arc([body_cx - 18, body_cy - 14, body_cx - 2, body_cy - 2],
                 start=180, end=360, fill=GOLD)
        draw.point((body_cx - 14, body_cy - 10), fill=RIVET)
        draw.point((body_cx - 6, body_cy - 10), fill=RIVET)

        # --- Head (side view, facing left) ---
        ellipse(draw, body_cx - 2, head_cy, 14, 14, BRONZE)
        # Face (partial)
        ellipse(draw, body_cx - 6, head_cy + 4, 8, 6, SKIN)
        # Helmet brim
        draw.rectangle([body_cx - 16, head_cy + 2, body_cx + 8, head_cy + 6],
                       fill=BRONZE_DARK, outline=OUTLINE)
        # Gold trim on brim
        draw.line([body_cx - 16, head_cy + 2, body_cx + 8, head_cy + 2], fill=GOLD)
        # Eye
        draw.rectangle([body_cx - 10, head_cy + 2, body_cx - 6, head_cy + 6], fill=BLACK)
        # White glint on helmet dome
        draw.point((body_cx - 6, head_cy - 10), fill=WHITE_GLINT)
        draw.point((body_cx - 8, head_cy - 8), fill=WHITE_GLINT)
        draw.point((body_cx - 7, head_cy - 9), fill=WHITE_GLINT)
        # Crest (tall, swept back in side view)
        # Base of crest
        draw.rectangle([body_cx - 4, head_cy - 14, body_cx + 2, head_cy - 10],
                       fill=RED, outline=OUTLINE)
        # Main crest body sweeping back
        draw.rectangle([body_cx - 2, head_cy - 22, body_cx + 2, head_cy - 14],
                       fill=RED, outline=OUTLINE)
        # Flowing back portion
        draw.rectangle([body_cx + 2, head_cy - 20, body_cx + 6, head_cy - 12],
                       fill=RED_DARK, outline=OUTLINE)
        # Crest tip
        draw.point((body_cx, head_cy - 24), fill=RED_LIGHT)
        draw.point((body_cx + 1, head_cy - 24), fill=RED_LIGHT)
        # Highlight streaks
        draw.line([body_cx, head_cy - 20, body_cx, head_cy - 14], fill=RED_BRIGHT)
        draw.line([body_cx + 1, head_cy - 22, body_cx + 1, head_cy - 16], fill=RED_BRIGHT)
        # Gold accent at crest base
        draw.line([body_cx - 4, head_cy - 10, body_cx + 2, head_cy - 10], fill=GOLD)

    elif direction == RIGHT:
        # --- Legs ---
        # Back leg
        draw.rectangle([body_cx - 2 + leg_spread, body_cy + 10,
                        body_cx + 4 + leg_spread, base_y], fill=BROWN_DARK, outline=OUTLINE)
        draw.rectangle([body_cx - 2 + leg_spread, base_y - 6,
                        body_cx + 4 + leg_spread, base_y], fill=BRONZE_DARK, outline=OUTLINE)
        # Front leg
        draw.rectangle([body_cx + 4 - leg_spread, body_cy + 10,
                        body_cx + 10 - leg_spread, base_y], fill=BROWN, outline=OUTLINE)
        draw.rectangle([body_cx + 4 - leg_spread, base_y - 6,
                        body_cx + 10 - leg_spread, base_y], fill=BRONZE_DARK, outline=OUTLINE)
        # Gold knee accents
        draw.line([body_cx - 2 + leg_spread, base_y - 6,
                   body_cx + 4 + leg_spread, base_y - 6], fill=GOLD)
        draw.line([body_cx + 4 - leg_spread, base_y - 6,
                   body_cx + 10 - leg_spread, base_y - 6], fill=GOLD)

        # --- Cape (flowing left/behind) ---
        cape_sway = [0, -2, 0, 2][frame]
        draw.rounded_rectangle([body_cx - 16 + cape_sway, body_cy - 8,
                                body_cx - 6, body_cy + 12],
                               radius=6, fill=RED, outline=OUTLINE)

        # --- Sword (right hand, in front in right-facing view) ---
        sword_x = body_cx + 10
        draw.rectangle([sword_x, body_cy + 4, sword_x + 2, body_cy + 20],
                       fill=STEEL, outline=OUTLINE)
        # Blade highlight / fuller
        draw.line([sword_x, body_cy + 6, sword_x, body_cy + 18], fill=STEEL_LIGHT)
        draw.point((sword_x, body_cy + 22), fill=STEEL_LIGHT)
        draw.point((sword_x + 1, body_cy + 22), fill=STEEL_LIGHT)
        draw.point((sword_x + 2, body_cy + 22), fill=STEEL_LIGHT)
        draw.rectangle([sword_x - 2, body_cy + 2, sword_x + 4, body_cy + 4],
                       fill=GOLD, outline=None)
        draw.point((sword_x - 2, body_cy + 3), fill=GOLD_DARK)
        draw.point((sword_x + 4, body_cy + 3), fill=GOLD_DARK)

        # --- Body ---
        ellipse(draw, body_cx + 2, body_cy, 12, 12, BRONZE)
        ellipse(draw, body_cx + 2, body_cy - 2, 8, 8, BRONZE_LIGHT)
        # Gold trim on armor edge
        draw.arc([body_cx - 10, body_cy - 12, body_cx + 14, body_cy + 12],
                 start=220, end=320, fill=GOLD)
        # Red belt
        draw.rectangle([body_cx - 10, body_cy + 6, body_cx + 14, body_cy + 10],
                       fill=RED, outline=OUTLINE)
        # Gold belt buckle
        draw.rectangle([body_cx, body_cy + 6, body_cx + 4, body_cy + 10],
                       fill=GOLD, outline=None)

        # --- Shield (left arm, behind body in right-facing view) ---
        ellipse(draw, body_cx - 12, body_cy - 2, 8, 10, SHIELD_BROWN, outline=OUTLINE)
        ellipse(draw, body_cx - 12, body_cy - 2, 4, 6, SHIELD_BROWN_DARK, outline=None)
        draw.rectangle([body_cx - 13, body_cy - 3, body_cx - 11, body_cy - 1], fill=GOLD)

        # --- Arm (front, sword arm) ---
        draw.rectangle([body_cx + 8, body_cy - 4, body_cx + 14, body_cy + 6],
                       fill=SKIN, outline=OUTLINE)
        # Muscle definition
        draw.line([(body_cx + 11, body_cy - 2), (body_cx + 11, body_cy + 4)], fill=MUSCLE_LINE)
        # Shoulder pauldron (more defined)
        ellipse(draw, body_cx + 10, body_cy - 8, 8, 6, BRONZE)
        ellipse(draw, body_cx + 10, body_cy - 8, 4, 2, BRONZE_LIGHT, outline=None)
        draw.arc([body_cx + 2, body_cy - 14, body_cx + 18, body_cy - 2],
                 start=180, end=360, fill=GOLD)
        draw.point((body_cx + 6, body_cy - 10), fill=RIVET)
        draw.point((body_cx + 14, body_cy - 10), fill=RIVET)

        # --- Head ---
        ellipse(draw, body_cx + 2, head_cy, 14, 14, BRONZE)
        ellipse(draw, body_cx + 6, head_cy + 4, 8, 6, SKIN)
        draw.rectangle([body_cx - 8, head_cy + 2, body_cx + 16, head_cy + 6],
                       fill=BRONZE_DARK, outline=OUTLINE)
        # Gold trim on brim
        draw.line([body_cx - 8, head_cy + 2, body_cx + 16, head_cy + 2], fill=GOLD)
        # Eye
        draw.rectangle([body_cx + 6, head_cy + 2, body_cx + 10, head_cy + 6], fill=BLACK)
        # White glint on helmet dome
        draw.point((body_cx + 6, head_cy - 10), fill=WHITE_GLINT)
        draw.point((body_cx + 8, head_cy - 8), fill=WHITE_GLINT)
        draw.point((body_cx + 7, head_cy - 9), fill=WHITE_GLINT)
        # Crest (tall, swept back in side view - mirrored from LEFT)
        # Base of crest
        draw.rectangle([body_cx - 2, head_cy - 14, body_cx + 4, head_cy - 10],
                       fill=RED, outline=OUTLINE)
        # Main crest body sweeping back
        draw.rectangle([body_cx - 2, head_cy - 22, body_cx + 2, head_cy - 14],
                       fill=RED, outline=OUTLINE)
        # Flowing back portion (swept left for right-facing)
        draw.rectangle([body_cx - 6, head_cy - 20, body_cx - 2, head_cy - 12],
                       fill=RED_DARK, outline=OUTLINE)
        # Crest tip
        draw.point((body_cx, head_cy - 24), fill=RED_LIGHT)
        draw.point((body_cx + 1, head_cy - 24), fill=RED_LIGHT)
        # Highlight streaks
        draw.line([body_cx, head_cy - 20, body_cx, head_cy - 14], fill=RED_BRIGHT)
        draw.line([body_cx - 1, head_cy - 22, body_cx - 1, head_cy - 16], fill=RED_BRIGHT)
        # Gold accent at crest base
        draw.line([body_cx - 2, head_cy - 10, body_cx + 4, head_cy - 10], fill=GOLD)


def main():
    img = Image.new("RGBA", (IMG_W, IMG_H), (0, 0, 0, 0))

    for direction in range(ROWS):
        for frame in range(COLS):
            frame_img = Image.new("RGBA", (FRAME_SIZE, FRAME_SIZE), (0, 0, 0, 0))
            frame_draw = ImageDraw.Draw(frame_img)
            draw_gladiator(frame_draw, 0, 0, direction, frame)
            img.paste(frame_img, (frame * FRAME_SIZE, direction * FRAME_SIZE))

    img.save("sprites/gladiator.png")
    print(f"Generated sprites/gladiator.png ({IMG_W}x{IMG_H})")


if __name__ == "__main__":
    main()
