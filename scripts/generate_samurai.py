#!/usr/bin/env python3
"""Generate sprites/samurai.png — 4-column x 4-row character spritesheet.

128x128 PNG, 32x32 per frame.
Row layout: Down=0, Up=1, Left=2, Right=3
4 walking animation frames per direction.

Style matches Spaceman/Gladiator: big round head, round body, small limbs, dark outlines.
Theme: Samurai — dark indigo hakama, gray kimono top, straw hat (kasa), katana on back/side.
"""

from PIL import Image, ImageDraw

FRAME_SIZE = 32
COLS = 4
ROWS = 4
IMG_W = FRAME_SIZE * COLS   # 128
IMG_H = FRAME_SIZE * ROWS   # 128

# Colors
OUTLINE = (40, 35, 35)
SKIN = (220, 185, 150)
SKIN_DARK = (190, 155, 120)
INDIGO = (45, 40, 90)
INDIGO_LIGHT = (65, 58, 120)
INDIGO_DARK = (30, 28, 65)
GRAY_KIMONO = (160, 155, 145)
GRAY_KIMONO_LIGHT = (185, 180, 170)
GRAY_KIMONO_DARK = (130, 125, 115)
STRAW = (210, 190, 130)
STRAW_LIGHT = (230, 215, 160)
STRAW_DARK = (175, 155, 100)
KATANA_BLADE = (200, 205, 215)
KATANA_HILT = (90, 50, 50)
KATANA_WRAP = (60, 40, 40)
BLACK = (30, 30, 30)
WHITE_GLINT = (255, 250, 240)
GOLD_ACCENT = (200, 170, 60)

DOWN, UP, LEFT, RIGHT = 0, 1, 2, 3


def ellipse(draw, cx, cy, rx, ry, fill, outline=OUTLINE):
    """Draw an outlined ellipse centered at (cx, cy)."""
    draw.ellipse([cx - rx, cy - ry, cx + rx, cy + ry], fill=fill, outline=outline)


def draw_samurai(draw, ox, oy, direction, frame):
    """Draw a single samurai frame at offset (ox, oy)."""
    # Walking bob
    bob = [0, -1, 0, -1][frame]
    leg_spread = [-2, 0, 2, 0][frame]

    base_y = oy + 27 + bob
    body_cx = ox + 16
    body_cy = base_y - 10
    head_cy = body_cy - 10

    if direction == DOWN:
        # --- Legs (hakama — wide pants) ---
        # Left leg
        draw.rectangle([body_cx - 6 + leg_spread, body_cy + 4,
                        body_cx - 2 + leg_spread, base_y], fill=INDIGO, outline=OUTLINE)
        # Right leg
        draw.rectangle([body_cx + 2 - leg_spread, body_cy + 4,
                        body_cx + 6 - leg_spread, base_y], fill=INDIGO, outline=OUTLINE)
        # Sandal accents
        draw.rectangle([body_cx - 6 + leg_spread, base_y - 2,
                        body_cx - 2 + leg_spread, base_y], fill=STRAW_DARK, outline=OUTLINE)
        draw.rectangle([body_cx + 2 - leg_spread, base_y - 2,
                        body_cx + 6 - leg_spread, base_y], fill=STRAW_DARK, outline=OUTLINE)

        # --- Katana on back (diagonal, behind body) ---
        draw.line([body_cx + 5, body_cy - 6, body_cx + 8, body_cy + 5],
                  fill=KATANA_BLADE, width=2)
        draw.line([body_cx + 5, body_cy - 6, body_cx + 4, body_cy - 8],
                  fill=KATANA_HILT, width=2)

        # --- Body (kimono top) ---
        ellipse(draw, body_cx, body_cy, 7, 6, GRAY_KIMONO)
        # Kimono V-neckline
        draw.line([body_cx, body_cy - 4, body_cx - 3, body_cy + 2], fill=GRAY_KIMONO_DARK, width=1)
        draw.line([body_cx, body_cy - 4, body_cx + 3, body_cy + 2], fill=GRAY_KIMONO_DARK, width=1)
        # Obi (belt)
        draw.rectangle([body_cx - 7, body_cy + 3, body_cx + 7, body_cy + 5],
                       fill=INDIGO_DARK, outline=OUTLINE)
        # Gold obi knot accent
        draw.rectangle([body_cx - 1, body_cy + 3, body_cx + 1, body_cy + 5],
                       fill=GOLD_ACCENT, outline=None)

        # --- Arms ---
        draw.rectangle([body_cx - 9, body_cy - 3, body_cx - 6, body_cy + 3],
                       fill=GRAY_KIMONO, outline=OUTLINE)
        draw.rectangle([body_cx + 6, body_cy - 3, body_cx + 9, body_cy + 3],
                       fill=GRAY_KIMONO, outline=OUTLINE)
        # Hands
        draw.rectangle([body_cx - 9, body_cy + 1, body_cx - 6, body_cy + 3],
                       fill=SKIN, outline=OUTLINE)
        draw.rectangle([body_cx + 6, body_cy + 1, body_cx + 9, body_cy + 3],
                       fill=SKIN, outline=OUTLINE)

        # --- Head (big round with kasa hat) ---
        # Face
        ellipse(draw, body_cx, head_cy + 1, 6, 5, SKIN)
        # Eyes
        draw.rectangle([body_cx - 3, head_cy, body_cx - 1, head_cy + 2], fill=BLACK)
        draw.rectangle([body_cx + 1, head_cy, body_cx + 3, head_cy + 2], fill=BLACK)
        # Kasa (straw hat) — wide brim
        ellipse(draw, body_cx, head_cy - 2, 10, 4, STRAW)
        # Hat top dome
        ellipse(draw, body_cx, head_cy - 4, 6, 3, STRAW_LIGHT)
        # Hat brim line
        draw.line([body_cx - 10, head_cy - 1, body_cx + 10, head_cy - 1],
                  fill=STRAW_DARK, width=1)

    elif direction == UP:
        # --- Legs ---
        draw.rectangle([body_cx - 6 + leg_spread, body_cy + 4,
                        body_cx - 2 + leg_spread, base_y], fill=INDIGO, outline=OUTLINE)
        draw.rectangle([body_cx + 2 - leg_spread, body_cy + 4,
                        body_cx + 6 - leg_spread, base_y], fill=INDIGO, outline=OUTLINE)
        draw.rectangle([body_cx - 6 + leg_spread, base_y - 2,
                        body_cx - 2 + leg_spread, base_y], fill=STRAW_DARK, outline=OUTLINE)
        draw.rectangle([body_cx + 2 - leg_spread, base_y - 2,
                        body_cx + 6 - leg_spread, base_y], fill=STRAW_DARK, outline=OUTLINE)

        # --- Body (back view) ---
        ellipse(draw, body_cx, body_cy, 7, 6, GRAY_KIMONO_DARK)
        # Obi
        draw.rectangle([body_cx - 7, body_cy + 3, body_cx + 7, body_cy + 5],
                       fill=INDIGO_DARK, outline=OUTLINE)

        # --- Katana on back (visible from behind) ---
        draw.line([body_cx + 3, body_cy - 8, body_cx + 6, body_cy + 4],
                  fill=KATANA_BLADE, width=2)
        draw.line([body_cx + 3, body_cy - 8, body_cx + 2, body_cy - 10],
                  fill=KATANA_HILT, width=2)
        # Hilt wrap detail
        draw.point((body_cx + 2, body_cy - 9), fill=KATANA_WRAP)

        # --- Arms ---
        draw.rectangle([body_cx - 9, body_cy - 3, body_cx - 6, body_cy + 3],
                       fill=GRAY_KIMONO_DARK, outline=OUTLINE)
        draw.rectangle([body_cx + 6, body_cy - 3, body_cx + 9, body_cy + 3],
                       fill=GRAY_KIMONO_DARK, outline=OUTLINE)

        # --- Head (back of kasa hat) ---
        ellipse(draw, body_cx, head_cy + 1, 6, 5, SKIN_DARK)
        # Kasa hat
        ellipse(draw, body_cx, head_cy - 2, 10, 4, STRAW)
        ellipse(draw, body_cx, head_cy - 4, 6, 3, STRAW_DARK)
        # Hat string visible from behind
        draw.line([body_cx - 3, head_cy + 2, body_cx - 2, head_cy + 5],
                  fill=STRAW_DARK, width=1)
        draw.line([body_cx + 3, head_cy + 2, body_cx + 2, head_cy + 5],
                  fill=STRAW_DARK, width=1)

    elif direction == LEFT:
        # --- Legs (side view) ---
        draw.rectangle([body_cx - 1 - leg_spread, body_cy + 4,
                        body_cx + 2 - leg_spread, base_y], fill=INDIGO_DARK, outline=OUTLINE)
        draw.rectangle([body_cx - 1 - leg_spread, base_y - 2,
                        body_cx + 2 - leg_spread, base_y], fill=STRAW_DARK, outline=OUTLINE)
        draw.rectangle([body_cx - 4 + leg_spread, body_cy + 4,
                        body_cx - 1 + leg_spread, base_y], fill=INDIGO, outline=OUTLINE)
        draw.rectangle([body_cx - 4 + leg_spread, base_y - 2,
                        body_cx - 1 + leg_spread, base_y], fill=STRAW_DARK, outline=OUTLINE)

        # --- Katana (on far side/back, angled up) ---
        draw.line([body_cx + 4, body_cy - 7, body_cx + 6, body_cy + 3],
                  fill=KATANA_BLADE, width=2)
        draw.line([body_cx + 4, body_cy - 7, body_cx + 3, body_cy - 9],
                  fill=KATANA_HILT, width=2)

        # --- Body ---
        ellipse(draw, body_cx - 1, body_cy, 6, 6, GRAY_KIMONO)
        ellipse(draw, body_cx - 1, body_cy - 1, 4, 4, GRAY_KIMONO_LIGHT)
        # Obi
        draw.rectangle([body_cx - 7, body_cy + 3, body_cx + 5, body_cy + 5],
                       fill=INDIGO_DARK, outline=OUTLINE)

        # --- Arm (front) ---
        draw.rectangle([body_cx - 7, body_cy - 2, body_cx - 4, body_cy + 3],
                       fill=GRAY_KIMONO, outline=OUTLINE)
        draw.rectangle([body_cx - 7, body_cy + 1, body_cx - 4, body_cy + 3],
                       fill=SKIN, outline=OUTLINE)

        # --- Head (side view, facing left) ---
        ellipse(draw, body_cx - 1, head_cy + 1, 5, 5, SKIN)
        # Eye
        draw.rectangle([body_cx - 4, head_cy, body_cx - 2, head_cy + 2], fill=BLACK)
        # Kasa hat (side view — extends further left)
        ellipse(draw, body_cx - 1, head_cy - 2, 9, 4, STRAW)
        ellipse(draw, body_cx - 1, head_cy - 4, 5, 3, STRAW_LIGHT)
        draw.line([body_cx - 10, head_cy - 1, body_cx + 8, head_cy - 1],
                  fill=STRAW_DARK, width=1)

    elif direction == RIGHT:
        # --- Legs ---
        draw.rectangle([body_cx - 1 + leg_spread, body_cy + 4,
                        body_cx + 2 + leg_spread, base_y], fill=INDIGO_DARK, outline=OUTLINE)
        draw.rectangle([body_cx - 1 + leg_spread, base_y - 2,
                        body_cx + 2 + leg_spread, base_y], fill=STRAW_DARK, outline=OUTLINE)
        draw.rectangle([body_cx + 2 - leg_spread, body_cy + 4,
                        body_cx + 5 - leg_spread, base_y], fill=INDIGO, outline=OUTLINE)
        draw.rectangle([body_cx + 2 - leg_spread, base_y - 2,
                        body_cx + 5 - leg_spread, base_y], fill=STRAW_DARK, outline=OUTLINE)

        # --- Katana (on far side/back, angled up) ---
        draw.line([body_cx - 4, body_cy - 7, body_cx - 6, body_cy + 3],
                  fill=KATANA_BLADE, width=2)
        draw.line([body_cx - 4, body_cy - 7, body_cx - 3, body_cy - 9],
                  fill=KATANA_HILT, width=2)

        # --- Body ---
        ellipse(draw, body_cx + 1, body_cy, 6, 6, GRAY_KIMONO)
        ellipse(draw, body_cx + 1, body_cy - 1, 4, 4, GRAY_KIMONO_LIGHT)
        # Obi
        draw.rectangle([body_cx - 5, body_cy + 3, body_cx + 7, body_cy + 5],
                       fill=INDIGO_DARK, outline=OUTLINE)

        # --- Arm ---
        draw.rectangle([body_cx + 4, body_cy - 2, body_cx + 7, body_cy + 3],
                       fill=GRAY_KIMONO, outline=OUTLINE)
        draw.rectangle([body_cx + 4, body_cy + 1, body_cx + 7, body_cy + 3],
                       fill=SKIN, outline=OUTLINE)

        # --- Head ---
        ellipse(draw, body_cx + 1, head_cy + 1, 5, 5, SKIN)
        # Eye
        draw.rectangle([body_cx + 2, head_cy, body_cx + 4, head_cy + 2], fill=BLACK)
        # Kasa hat
        ellipse(draw, body_cx + 1, head_cy - 2, 9, 4, STRAW)
        ellipse(draw, body_cx + 1, head_cy - 4, 5, 3, STRAW_LIGHT)
        draw.line([body_cx - 8, head_cy - 1, body_cx + 10, head_cy - 1],
                  fill=STRAW_DARK, width=1)


def main():
    img = Image.new("RGBA", (IMG_W, IMG_H), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    for direction in range(ROWS):
        for frame in range(COLS):
            ox = frame * FRAME_SIZE
            oy = direction * FRAME_SIZE
            draw_samurai(draw, ox, oy, direction, frame)

    img.save("sprites/samurai.png")
    print(f"Generated sprites/samurai.png ({IMG_W}x{IMG_H})")


if __name__ == "__main__":
    main()
