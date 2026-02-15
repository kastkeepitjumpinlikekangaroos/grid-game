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

# Colors
OUTLINE = (15, 10, 20)
CLOAK_DARK = (25, 15, 35)
CLOAK = (40, 25, 55)
CLOAK_LIGHT = (55, 35, 70)
HOOD = (30, 20, 45)
HOOD_DARK = (20, 12, 30)
MASK = (20, 20, 25)
MASK_EDGE = (35, 30, 45)
EYE_GLOW = (220, 50, 50)
EYE_CORE = (255, 100, 80)
SKIN = (65, 50, 45)
BELT = (140, 30, 30)
BELT_BUCKLE = (180, 160, 60)
DAGGER_BLADE = (170, 175, 185)
DAGGER_HILT = (120, 30, 30)
PANTS = (30, 22, 38)
BOOTS = (25, 18, 30)
SCARF_TAIL = (130, 25, 25)

DOWN, UP, LEFT, RIGHT = 0, 1, 2, 3


def ellipse(draw, cx, cy, rx, ry, fill, outline=OUTLINE):
    """Draw an outlined ellipse centered at (cx, cy)."""
    draw.ellipse([cx - rx, cy - ry, cx + rx, cy + ry], fill=fill, outline=outline)


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
                   (body_cx - 3 - leg_spread, base_y)],
                  fill=PANTS, width=3)
        draw.rectangle([body_cx - 5 - leg_spread, base_y - 1,
                        body_cx - 1 - leg_spread, base_y + 1], fill=BOOTS)
        # Right leg
        draw.line([(body_cx + 3 + leg_spread, body_cy + 5),
                   (body_cx + 3 + leg_spread, base_y)],
                  fill=PANTS, width=3)
        draw.rectangle([body_cx + 1 + leg_spread, base_y - 1,
                        body_cx + 5 + leg_spread, base_y + 1], fill=BOOTS)

        # --- Body (dark cloak/tunic) ---
        ellipse(draw, body_cx, body_cy, 7, 6, CLOAK)
        # Cloak highlight center
        ellipse(draw, body_cx, body_cy, 4, 4, CLOAK_LIGHT, outline=None)

        # --- Belt with buckle ---
        draw.rectangle([body_cx - 7, body_cy + 3, body_cx + 7, body_cy + 5], fill=BELT)
        draw.rectangle([body_cx - 2, body_cy + 3, body_cx + 2, body_cy + 5], fill=BELT_BUCKLE)

        # --- Daggers on belt (both sides) ---
        # Left dagger
        draw.line([(body_cx - 8, body_cy + 2), (body_cx - 8, body_cy + 8)],
                  fill=DAGGER_BLADE, width=1)
        draw.point((body_cx - 8, body_cy + 1), fill=DAGGER_HILT)
        # Right dagger
        draw.line([(body_cx + 8, body_cy + 2), (body_cx + 8, body_cy + 8)],
                  fill=DAGGER_BLADE, width=1)
        draw.point((body_cx + 8, body_cy + 1), fill=DAGGER_HILT)

        # --- Hood ---
        ellipse(draw, body_cx, head_cy, 8, 8, HOOD)
        # Hood inner shadow
        ellipse(draw, body_cx, head_cy + 1, 6, 6, HOOD_DARK)

        # --- Mask (covers lower face) ---
        draw.rectangle([body_cx - 5, head_cy + 1, body_cx + 5, head_cy + 5],
                        fill=MASK, outline=MASK_EDGE)

        # --- Eyes (glowing red, menacing) ---
        draw.rectangle([body_cx - 4, head_cy - 1, body_cx - 1, head_cy + 1], fill=EYE_GLOW)
        draw.rectangle([body_cx + 1, head_cy - 1, body_cx + 4, head_cy + 1], fill=EYE_GLOW)
        draw.point((body_cx - 2, head_cy), fill=EYE_CORE)
        draw.point((body_cx + 2, head_cy), fill=EYE_CORE)

        # --- Hood peak ---
        draw.polygon([
            (body_cx - 2, head_cy - 9),
            (body_cx + 2, head_cy - 9),
            (body_cx + 1, head_cy - 5),
            (body_cx - 1, head_cy - 5),
        ], fill=HOOD, outline=OUTLINE)

        # --- Scarf tail (flows behind) ---
        scarf_sway = [-1, 0, 1, 0][frame]
        draw.line([(body_cx + 4, head_cy + 4),
                   (body_cx + 6 + scarf_sway, head_cy + 9)],
                  fill=SCARF_TAIL, width=2)

    elif direction == UP:
        # --- Legs ---
        draw.line([(body_cx - 3 - leg_spread, body_cy + 5),
                   (body_cx - 3 - leg_spread, base_y)],
                  fill=PANTS, width=3)
        draw.rectangle([body_cx - 5 - leg_spread, base_y - 1,
                        body_cx - 1 - leg_spread, base_y + 1], fill=BOOTS)
        draw.line([(body_cx + 3 + leg_spread, body_cy + 5),
                   (body_cx + 3 + leg_spread, base_y)],
                  fill=PANTS, width=3)
        draw.rectangle([body_cx + 1 + leg_spread, base_y - 1,
                        body_cx + 5 + leg_spread, base_y + 1], fill=BOOTS)

        # --- Scarf tail (flowing down in back) ---
        scarf_sway = [-1, 0, 1, 0][frame]
        draw.line([(body_cx, head_cy + 5),
                   (body_cx + scarf_sway, head_cy + 12)],
                  fill=SCARF_TAIL, width=2)

        # --- Body ---
        ellipse(draw, body_cx, body_cy, 7, 6, CLOAK)
        ellipse(draw, body_cx, body_cy, 5, 4, CLOAK_DARK, outline=None)

        # --- Belt ---
        draw.rectangle([body_cx - 7, body_cy + 3, body_cx + 7, body_cy + 5], fill=BELT)

        # --- Daggers (crossed on back) ---
        draw.line([(body_cx - 3, body_cy - 4), (body_cx + 3, body_cy + 3)],
                  fill=DAGGER_BLADE, width=1)
        draw.line([(body_cx + 3, body_cy - 4), (body_cx - 3, body_cy + 3)],
                  fill=DAGGER_BLADE, width=1)
        draw.point((body_cx - 3, body_cy - 4), fill=DAGGER_HILT)
        draw.point((body_cx + 3, body_cy - 4), fill=DAGGER_HILT)

        # --- Hood (back view) ---
        ellipse(draw, body_cx, head_cy, 8, 8, HOOD)
        ellipse(draw, body_cx, head_cy, 6, 6, HOOD_DARK)

        # --- Hood peak ---
        draw.polygon([
            (body_cx - 2, head_cy - 9),
            (body_cx + 2, head_cy - 9),
            (body_cx + 1, head_cy - 5),
            (body_cx - 1, head_cy - 5),
        ], fill=HOOD, outline=OUTLINE)

    elif direction == LEFT:
        # --- Legs ---
        draw.line([(body_cx - 1 - leg_spread, body_cy + 5),
                   (body_cx - 1 - leg_spread, base_y)],
                  fill=PANTS, width=3)
        draw.rectangle([body_cx - 3 - leg_spread, base_y - 1,
                        body_cx + 1 - leg_spread, base_y + 1], fill=BOOTS)
        draw.line([(body_cx + 2 + leg_spread, body_cy + 5),
                   (body_cx + 2 + leg_spread, base_y)],
                  fill=PANTS, width=3)
        draw.rectangle([body_cx + 0 + leg_spread, base_y - 1,
                        body_cx + 4 + leg_spread, base_y + 1], fill=BOOTS)

        # --- Body ---
        ellipse(draw, body_cx, body_cy, 6, 6, CLOAK)
        ellipse(draw, body_cx - 1, body_cy, 4, 4, CLOAK_LIGHT, outline=None)

        # --- Belt ---
        draw.rectangle([body_cx - 6, body_cy + 3, body_cx + 6, body_cy + 5], fill=BELT)

        # --- Dagger (held forward) ---
        draw.line([(body_cx - 7, body_cy), (body_cx - 7, body_cy + 7)],
                  fill=DAGGER_BLADE, width=1)
        draw.point((body_cx - 7, body_cy - 1), fill=DAGGER_HILT)

        # --- Hood (side facing left) ---
        ellipse(draw, body_cx - 1, head_cy, 7, 8, HOOD)
        ellipse(draw, body_cx - 1, head_cy + 1, 5, 6, HOOD_DARK)

        # --- Mask (side view) ---
        draw.rectangle([body_cx - 5, head_cy + 1, body_cx + 1, head_cy + 5],
                        fill=MASK, outline=MASK_EDGE)

        # --- Eye (one visible, glowing) ---
        draw.rectangle([body_cx - 4, head_cy - 1, body_cx - 1, head_cy + 1], fill=EYE_GLOW)
        draw.point((body_cx - 2, head_cy), fill=EYE_CORE)

        # --- Hood peak ---
        draw.polygon([
            (body_cx - 3, head_cy - 9),
            (body_cx + 1, head_cy - 9),
            (body_cx, head_cy - 5),
            (body_cx - 2, head_cy - 5),
        ], fill=HOOD, outline=OUTLINE)

        # --- Scarf tail (flowing right) ---
        scarf_sway = [0, 1, 0, -1][frame]
        draw.line([(body_cx + 3, head_cy + 4),
                   (body_cx + 8, head_cy + 6 + scarf_sway)],
                  fill=SCARF_TAIL, width=2)

    elif direction == RIGHT:
        # --- Legs ---
        draw.line([(body_cx - 2 - leg_spread, body_cy + 5),
                   (body_cx - 2 - leg_spread, base_y)],
                  fill=PANTS, width=3)
        draw.rectangle([body_cx - 4 - leg_spread, base_y - 1,
                        body_cx + 0 - leg_spread, base_y + 1], fill=BOOTS)
        draw.line([(body_cx + 1 + leg_spread, body_cy + 5),
                   (body_cx + 1 + leg_spread, base_y)],
                  fill=PANTS, width=3)
        draw.rectangle([body_cx - 1 + leg_spread, base_y - 1,
                        body_cx + 3 + leg_spread, base_y + 1], fill=BOOTS)

        # --- Body ---
        ellipse(draw, body_cx, body_cy, 6, 6, CLOAK)
        ellipse(draw, body_cx + 1, body_cy, 4, 4, CLOAK_LIGHT, outline=None)

        # --- Belt ---
        draw.rectangle([body_cx - 6, body_cy + 3, body_cx + 6, body_cy + 5], fill=BELT)

        # --- Dagger (held forward) ---
        draw.line([(body_cx + 7, body_cy), (body_cx + 7, body_cy + 7)],
                  fill=DAGGER_BLADE, width=1)
        draw.point((body_cx + 7, body_cy - 1), fill=DAGGER_HILT)

        # --- Hood (side facing right) ---
        ellipse(draw, body_cx + 1, head_cy, 7, 8, HOOD)
        ellipse(draw, body_cx + 1, head_cy + 1, 5, 6, HOOD_DARK)

        # --- Mask (side view) ---
        draw.rectangle([body_cx - 1, head_cy + 1, body_cx + 5, head_cy + 5],
                        fill=MASK, outline=MASK_EDGE)

        # --- Eye (one visible, glowing) ---
        draw.rectangle([body_cx + 1, head_cy - 1, body_cx + 4, head_cy + 1], fill=EYE_GLOW)
        draw.point((body_cx + 2, head_cy), fill=EYE_CORE)

        # --- Hood peak ---
        draw.polygon([
            (body_cx - 1, head_cy - 9),
            (body_cx + 3, head_cy - 9),
            (body_cx + 2, head_cy - 5),
            (body_cx, head_cy - 5),
        ], fill=HOOD, outline=OUTLINE)

        # --- Scarf tail (flowing left) ---
        scarf_sway = [0, -1, 0, 1][frame]
        draw.line([(body_cx - 3, head_cy + 4),
                   (body_cx - 8, head_cy + 6 + scarf_sway)],
                  fill=SCARF_TAIL, width=2)


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
