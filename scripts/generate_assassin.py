#!/usr/bin/env python3
"""Generate sprites/assassin.png — 4-column x 4-row character spritesheet.

128x128 PNG, 32x32 per frame.
Row layout: Down=0, Up=1, Left=2, Right=3
4 walking animation frames per direction.

Style matches Spaceman/Gladiator: big round head, round body, small limbs.
Theme: Shadowy assassin — hooded figure with mask, dark cloak, visible daggers.
Color palette: deep purple/black with crimson accents.
"""

from PIL import Image, ImageDraw

FRAME_SIZE = 32
COLS = 4
ROWS = 4
IMG_W = FRAME_SIZE * COLS   # 128
IMG_H = FRAME_SIZE * ROWS   # 128

# Colors — brightened from original so the character is visible
OUTLINE = (25, 18, 35)
CLOAK_DARK = (40, 28, 55)
CLOAK = (55, 40, 70)
CLOAK_LIGHT = (75, 55, 95)
HOOD = (48, 35, 65)
HOOD_DARK = (35, 25, 50)
HOOD_EDGE = (90, 70, 115)       # Lighter edge highlight for hood silhouette
MASK = (28, 28, 35)
MASK_EDGE = (50, 42, 60)
EYE_GLOW = (240, 55, 45)        # Brighter red glow
EYE_CORE = (255, 150, 120)      # Hot bright core
EYE_HALO = (180, 30, 30, 140)   # Subtle glow halo around eyes
SKIN = (65, 50, 45)
BELT = (140, 30, 30)
BELT_BUCKLE = (190, 170, 65)
DAGGER_BLADE = (185, 190, 205)  # Slightly brighter steel
DAGGER_HILT = (130, 30, 30)
DAGGER_GLINT = (230, 240, 255)  # Bright glint at blade tip
PANTS = (42, 32, 52)
BOOTS = (38, 28, 48)
BOOT_BUCKLE = (160, 140, 55)
SCARF_TAIL = (180, 35, 35)      # Brighter, more visible crimson
THROWING_STAR = (190, 200, 215)  # Metallic bright for shuriken

DOWN, UP, LEFT, RIGHT = 0, 1, 2, 3


def ellipse(draw, cx, cy, rx, ry, fill, outline=OUTLINE):
    """Draw an outlined ellipse centered at (cx, cy)."""
    draw.ellipse([cx - rx, cy - ry, cx + rx, cy + ry], fill=fill, outline=outline)


def draw_throwing_star(draw, cx, cy):
    """Draw a small X-shaped throwing star (shuriken) at (cx, cy)."""
    draw.point((cx, cy), fill=THROWING_STAR)
    draw.point((cx - 1, cy - 1), fill=THROWING_STAR)
    draw.point((cx + 1, cy - 1), fill=THROWING_STAR)
    draw.point((cx - 1, cy + 1), fill=THROWING_STAR)
    draw.point((cx + 1, cy + 1), fill=THROWING_STAR)


def draw_boot_down(draw, cx, base_y, flip=False):
    """Draw a pointed boot (front-facing, for DOWN direction).
    flip=True mirrors horizontally for the other foot."""
    # Pointed angular boot shape
    if not flip:
        draw.polygon([
            (cx - 3, base_y - 2),
            (cx + 2, base_y - 2),
            (cx + 3, base_y),
            (cx + 3, base_y + 1),
            (cx - 3, base_y + 1),
            (cx - 3, base_y - 2),
        ], fill=BOOTS, outline=OUTLINE)
        # Buckle detail
        draw.point((cx, base_y - 1), fill=BOOT_BUCKLE)
    else:
        draw.polygon([
            (cx - 2, base_y - 2),
            (cx + 3, base_y - 2),
            (cx + 3, base_y + 1),
            (cx - 3, base_y + 1),
            (cx - 3, base_y),
            (cx - 2, base_y - 2),
        ], fill=BOOTS, outline=OUTLINE)
        draw.point((cx, base_y - 1), fill=BOOT_BUCKLE)


def draw_boot_side(draw, cx, base_y, facing_left=True):
    """Draw a pointed boot from the side — toe extends in walking direction."""
    if facing_left:
        draw.polygon([
            (cx + 2, base_y - 2),
            (cx + 2, base_y + 1),
            (cx - 4, base_y + 1),
            (cx - 4, base_y),
            (cx - 1, base_y - 2),
        ], fill=BOOTS, outline=OUTLINE)
        draw.point((cx, base_y - 1), fill=BOOT_BUCKLE)
    else:
        draw.polygon([
            (cx - 2, base_y - 2),
            (cx + 1, base_y - 2),
            (cx + 4, base_y),
            (cx + 4, base_y + 1),
            (cx - 2, base_y + 1),
        ], fill=BOOTS, outline=OUTLINE)
        draw.point((cx, base_y - 1), fill=BOOT_BUCKLE)


def draw_assassin(draw, ox, oy, direction, frame):
    """Draw a single assassin frame at offset (ox, oy).

    Proportions match other characters: big round head, round body, small limbs.
    """
    # Walking bob animation
    bob = [0, -1, 0, -1][frame]
    leg_spread = [-2, 0, 2, 0][frame]

    base_y = oy + 29 + bob
    body_cx = ox + 16
    body_cy = base_y - 11
    head_cy = body_cy - 10

    if direction == DOWN:
        # --- Legs ---
        # Left leg
        draw.line([(body_cx - 3 - leg_spread, body_cy + 5),
                   (body_cx - 3 - leg_spread, base_y - 2)],
                  fill=PANTS, width=3)
        draw_boot_down(draw, body_cx - 3 - leg_spread, base_y)
        # Right leg
        draw.line([(body_cx + 3 + leg_spread, body_cy + 5),
                   (body_cx + 3 + leg_spread, base_y - 2)],
                  fill=PANTS, width=3)
        draw_boot_down(draw, body_cx + 3 + leg_spread, base_y, flip=True)

        # --- Body (dark cloak/tunic) ---
        ellipse(draw, body_cx, body_cy, 7, 6, CLOAK)
        # Cloak highlight center
        ellipse(draw, body_cx, body_cy, 4, 4, CLOAK_LIGHT, outline=None)

        # --- Belt with buckle ---
        draw.rectangle([body_cx - 7, body_cy + 3, body_cx + 7, body_cy + 5], fill=BELT)
        draw.rectangle([body_cx - 2, body_cy + 3, body_cx + 2, body_cy + 5], fill=BELT_BUCKLE)

        # --- Throwing stars on belt ---
        draw_throwing_star(draw, body_cx - 5, body_cy + 4)
        draw_throwing_star(draw, body_cx + 5, body_cy + 4)

        # --- Daggers on belt (both sides, bigger) ---
        # Left dagger — 2px wide, 7px long
        draw.line([(body_cx - 9, body_cy + 1), (body_cx - 9, body_cy + 8)],
                  fill=DAGGER_BLADE, width=2)
        draw.point((body_cx - 9, body_cy + 8), fill=DAGGER_GLINT)
        draw.rectangle([body_cx - 10, body_cy - 1, body_cx - 8, body_cy + 1], fill=DAGGER_HILT)
        # Right dagger — 2px wide, 7px long
        draw.line([(body_cx + 9, body_cy + 1), (body_cx + 9, body_cy + 8)],
                  fill=DAGGER_BLADE, width=2)
        draw.point((body_cx + 9, body_cy + 8), fill=DAGGER_GLINT)
        draw.rectangle([body_cx + 8, body_cy - 1, body_cx + 10, body_cy + 1], fill=DAGGER_HILT)

        # --- Hood ---
        ellipse(draw, body_cx, head_cy, 8, 8, HOOD)
        # Hood edge highlight — arc along the top
        for px in range(-6, 7):
            draw.point((body_cx + px, head_cy - 7), fill=HOOD_EDGE)
        for px in range(-7, 8):
            draw.point((body_cx + px, head_cy - 6), fill=HOOD_EDGE)
        # Hood inner shadow
        ellipse(draw, body_cx, head_cy + 1, 6, 6, HOOD_DARK)

        # --- Mask (covers lower face) ---
        draw.rectangle([body_cx - 5, head_cy + 1, body_cx + 5, head_cy + 5],
                        fill=MASK, outline=MASK_EDGE)

        # --- Eyes (glowing red, larger and brighter) ---
        # Left eye
        draw.rectangle([body_cx - 5, head_cy - 2, body_cx - 1, head_cy + 1], fill=EYE_GLOW)
        draw.point((body_cx - 3, head_cy - 1), fill=EYE_CORE)
        draw.point((body_cx - 2, head_cy), fill=EYE_CORE)
        # Right eye
        draw.rectangle([body_cx + 1, head_cy - 2, body_cx + 5, head_cy + 1], fill=EYE_GLOW)
        draw.point((body_cx + 3, head_cy - 1), fill=EYE_CORE)
        draw.point((body_cx + 2, head_cy), fill=EYE_CORE)

        # --- Hood peak ---
        draw.polygon([
            (body_cx - 2, head_cy - 9),
            (body_cx + 2, head_cy - 9),
            (body_cx + 1, head_cy - 5),
            (body_cx - 1, head_cy - 5),
        ], fill=HOOD, outline=OUTLINE)
        # Peak edge highlight
        draw.point((body_cx - 1, head_cy - 9), fill=HOOD_EDGE)
        draw.point((body_cx, head_cy - 9), fill=HOOD_EDGE)
        draw.point((body_cx + 1, head_cy - 9), fill=HOOD_EDGE)

        # --- Scarf tail (wider, longer, more dramatic) ---
        scarf_sway = [-1, 0, 1, 0][frame]
        # Main scarf body — 3px wide
        draw.line([(body_cx + 4, head_cy + 3),
                   (body_cx + 7 + scarf_sway, head_cy + 10)],
                  fill=SCARF_TAIL, width=3)
        # Scarf tip extension
        draw.line([(body_cx + 7 + scarf_sway, head_cy + 10),
                   (body_cx + 9 + scarf_sway, head_cy + 13)],
                  fill=SCARF_TAIL, width=2)

    elif direction == UP:
        # --- Legs ---
        draw.line([(body_cx - 3 - leg_spread, body_cy + 5),
                   (body_cx - 3 - leg_spread, base_y - 2)],
                  fill=PANTS, width=3)
        draw_boot_down(draw, body_cx - 3 - leg_spread, base_y)
        draw.line([(body_cx + 3 + leg_spread, body_cy + 5),
                   (body_cx + 3 + leg_spread, base_y - 2)],
                  fill=PANTS, width=3)
        draw_boot_down(draw, body_cx + 3 + leg_spread, base_y, flip=True)

        # --- Scarf tail (flowing down in back, wider and longer) ---
        scarf_sway = [-1, 0, 1, 0][frame]
        draw.line([(body_cx, head_cy + 5),
                   (body_cx + scarf_sway, head_cy + 12)],
                  fill=SCARF_TAIL, width=3)
        draw.line([(body_cx + scarf_sway, head_cy + 12),
                   (body_cx + scarf_sway * 2, head_cy + 16)],
                  fill=SCARF_TAIL, width=2)

        # --- Body ---
        ellipse(draw, body_cx, body_cy, 7, 6, CLOAK)
        ellipse(draw, body_cx, body_cy, 5, 4, CLOAK_DARK, outline=None)

        # --- Belt ---
        draw.rectangle([body_cx - 7, body_cy + 3, body_cx + 7, body_cy + 5], fill=BELT)

        # --- Daggers (crossed on back, bigger) ---
        draw.line([(body_cx - 4, body_cy - 5), (body_cx + 4, body_cy + 4)],
                  fill=DAGGER_BLADE, width=2)
        draw.line([(body_cx + 4, body_cy - 5), (body_cx - 4, body_cy + 4)],
                  fill=DAGGER_BLADE, width=2)
        draw.point((body_cx - 4, body_cy - 5), fill=DAGGER_GLINT)
        draw.point((body_cx + 4, body_cy - 5), fill=DAGGER_GLINT)
        draw.rectangle([body_cx - 5, body_cy - 6, body_cx - 3, body_cy - 4], fill=DAGGER_HILT)
        draw.rectangle([body_cx + 3, body_cy - 6, body_cx + 5, body_cy - 4], fill=DAGGER_HILT)

        # --- Hood (back view) ---
        ellipse(draw, body_cx, head_cy, 8, 8, HOOD)
        # Hood edge highlight along top
        for px in range(-6, 7):
            draw.point((body_cx + px, head_cy - 7), fill=HOOD_EDGE)
        for px in range(-7, 8):
            draw.point((body_cx + px, head_cy - 6), fill=HOOD_EDGE)
        ellipse(draw, body_cx, head_cy, 6, 6, HOOD_DARK)

        # --- Hood peak ---
        draw.polygon([
            (body_cx - 2, head_cy - 9),
            (body_cx + 2, head_cy - 9),
            (body_cx + 1, head_cy - 5),
            (body_cx - 1, head_cy - 5),
        ], fill=HOOD, outline=OUTLINE)
        draw.point((body_cx - 1, head_cy - 9), fill=HOOD_EDGE)
        draw.point((body_cx, head_cy - 9), fill=HOOD_EDGE)
        draw.point((body_cx + 1, head_cy - 9), fill=HOOD_EDGE)

    elif direction == LEFT:
        # --- Legs ---
        draw.line([(body_cx - 1 - leg_spread, body_cy + 5),
                   (body_cx - 1 - leg_spread, base_y - 2)],
                  fill=PANTS, width=3)
        draw_boot_side(draw, body_cx - 1 - leg_spread, base_y, facing_left=True)
        draw.line([(body_cx + 2 + leg_spread, body_cy + 5),
                   (body_cx + 2 + leg_spread, base_y - 2)],
                  fill=PANTS, width=3)
        draw_boot_side(draw, body_cx + 2 + leg_spread, base_y, facing_left=True)

        # --- Scarf tail (flowing right, wider and longer) ---
        scarf_sway = [0, 1, 0, -1][frame]
        draw.line([(body_cx + 3, head_cy + 3),
                   (body_cx + 9, head_cy + 6 + scarf_sway)],
                  fill=SCARF_TAIL, width=3)
        draw.line([(body_cx + 9, head_cy + 6 + scarf_sway),
                   (body_cx + 12, head_cy + 9 + scarf_sway)],
                  fill=SCARF_TAIL, width=2)

        # --- Body ---
        ellipse(draw, body_cx, body_cy, 6, 6, CLOAK)
        ellipse(draw, body_cx - 1, body_cy, 4, 4, CLOAK_LIGHT, outline=None)

        # --- Belt ---
        draw.rectangle([body_cx - 6, body_cy + 3, body_cx + 6, body_cy + 5], fill=BELT)

        # --- Throwing star on belt ---
        draw_throwing_star(draw, body_cx + 3, body_cy + 4)

        # --- Dagger (held forward prominently, bigger) ---
        draw.line([(body_cx - 8, body_cy - 1), (body_cx - 8, body_cy + 7)],
                  fill=DAGGER_BLADE, width=2)
        draw.point((body_cx - 8, body_cy + 7), fill=DAGGER_GLINT)
        draw.rectangle([body_cx - 9, body_cy - 3, body_cx - 7, body_cy - 1], fill=DAGGER_HILT)
        # Second dagger on belt (smaller, behind)
        draw.line([(body_cx + 6, body_cy + 2), (body_cx + 6, body_cy + 6)],
                  fill=DAGGER_BLADE, width=1)
        draw.point((body_cx + 6, body_cy + 6), fill=DAGGER_GLINT)

        # --- Hood (side facing left) ---
        ellipse(draw, body_cx - 1, head_cy, 7, 8, HOOD)
        # Hood edge highlight along the top-left rim
        for px in range(-6, 4):
            draw.point((body_cx - 1 + px, head_cy - 7), fill=HOOD_EDGE)
        ellipse(draw, body_cx - 1, head_cy + 1, 5, 6, HOOD_DARK)

        # --- Mask (side view) ---
        draw.rectangle([body_cx - 5, head_cy + 1, body_cx + 1, head_cy + 5],
                        fill=MASK, outline=MASK_EDGE)

        # --- Eye (one visible, glowing, larger) ---
        draw.rectangle([body_cx - 5, head_cy - 2, body_cx - 1, head_cy + 1], fill=EYE_GLOW)
        draw.point((body_cx - 3, head_cy - 1), fill=EYE_CORE)
        draw.point((body_cx - 2, head_cy), fill=EYE_CORE)

        # --- Hood peak ---
        draw.polygon([
            (body_cx - 3, head_cy - 9),
            (body_cx + 1, head_cy - 9),
            (body_cx, head_cy - 5),
            (body_cx - 2, head_cy - 5),
        ], fill=HOOD, outline=OUTLINE)
        draw.point((body_cx - 2, head_cy - 9), fill=HOOD_EDGE)
        draw.point((body_cx - 1, head_cy - 9), fill=HOOD_EDGE)
        draw.point((body_cx, head_cy - 9), fill=HOOD_EDGE)

    elif direction == RIGHT:
        # --- Legs ---
        draw.line([(body_cx - 2 - leg_spread, body_cy + 5),
                   (body_cx - 2 - leg_spread, base_y - 2)],
                  fill=PANTS, width=3)
        draw_boot_side(draw, body_cx - 2 - leg_spread, base_y, facing_left=False)
        draw.line([(body_cx + 1 + leg_spread, body_cy + 5),
                   (body_cx + 1 + leg_spread, base_y - 2)],
                  fill=PANTS, width=3)
        draw_boot_side(draw, body_cx + 1 + leg_spread, base_y, facing_left=False)

        # --- Scarf tail (flowing left, wider and longer) ---
        scarf_sway = [0, -1, 0, 1][frame]
        draw.line([(body_cx - 3, head_cy + 3),
                   (body_cx - 9, head_cy + 6 + scarf_sway)],
                  fill=SCARF_TAIL, width=3)
        draw.line([(body_cx - 9, head_cy + 6 + scarf_sway),
                   (body_cx - 12, head_cy + 9 + scarf_sway)],
                  fill=SCARF_TAIL, width=2)

        # --- Body ---
        ellipse(draw, body_cx, body_cy, 6, 6, CLOAK)
        ellipse(draw, body_cx + 1, body_cy, 4, 4, CLOAK_LIGHT, outline=None)

        # --- Belt ---
        draw.rectangle([body_cx - 6, body_cy + 3, body_cx + 6, body_cy + 5], fill=BELT)

        # --- Throwing star on belt ---
        draw_throwing_star(draw, body_cx - 3, body_cy + 4)

        # --- Dagger (held forward prominently, bigger) ---
        draw.line([(body_cx + 8, body_cy - 1), (body_cx + 8, body_cy + 7)],
                  fill=DAGGER_BLADE, width=2)
        draw.point((body_cx + 8, body_cy + 7), fill=DAGGER_GLINT)
        draw.rectangle([body_cx + 7, body_cy - 3, body_cx + 9, body_cy - 1], fill=DAGGER_HILT)
        # Second dagger on belt (smaller, behind)
        draw.line([(body_cx - 6, body_cy + 2), (body_cx - 6, body_cy + 6)],
                  fill=DAGGER_BLADE, width=1)
        draw.point((body_cx - 6, body_cy + 6), fill=DAGGER_GLINT)

        # --- Hood (side facing right) ---
        ellipse(draw, body_cx + 1, head_cy, 7, 8, HOOD)
        # Hood edge highlight along the top-right rim
        for px in range(-3, 7):
            draw.point((body_cx + 1 + px, head_cy - 7), fill=HOOD_EDGE)
        ellipse(draw, body_cx + 1, head_cy + 1, 5, 6, HOOD_DARK)

        # --- Mask (side view) ---
        draw.rectangle([body_cx - 1, head_cy + 1, body_cx + 5, head_cy + 5],
                        fill=MASK, outline=MASK_EDGE)

        # --- Eye (one visible, glowing, larger) ---
        draw.rectangle([body_cx + 1, head_cy - 2, body_cx + 5, head_cy + 1], fill=EYE_GLOW)
        draw.point((body_cx + 3, head_cy - 1), fill=EYE_CORE)
        draw.point((body_cx + 2, head_cy), fill=EYE_CORE)

        # --- Hood peak ---
        draw.polygon([
            (body_cx - 1, head_cy - 9),
            (body_cx + 3, head_cy - 9),
            (body_cx + 2, head_cy - 5),
            (body_cx, head_cy - 5),
        ], fill=HOOD, outline=OUTLINE)
        draw.point((body_cx, head_cy - 9), fill=HOOD_EDGE)
        draw.point((body_cx + 1, head_cy - 9), fill=HOOD_EDGE)
        draw.point((body_cx + 2, head_cy - 9), fill=HOOD_EDGE)


def main():
    img = Image.new("RGBA", (IMG_W, IMG_H), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    for direction in range(ROWS):
        for frame in range(COLS):
            ox = frame * FRAME_SIZE
            oy = direction * FRAME_SIZE
            draw_assassin(draw, ox, oy, direction, frame)

    img.save("sprites/assassin.png")
    print(f"Generated sprites/assassin.png ({IMG_W}x{IMG_H})")


if __name__ == "__main__":
    main()
