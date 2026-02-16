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
INDIGO_PLEAT = (22, 20, 50)
GRAY_KIMONO = (160, 155, 145)
GRAY_KIMONO_LIGHT = (185, 180, 170)
GRAY_KIMONO_DARK = (130, 125, 115)
STRAW = (210, 190, 130)
STRAW_LIGHT = (230, 215, 160)
STRAW_DARK = (175, 155, 100)
STRAW_STITCH = (150, 130, 80)
KATANA_BLADE = (200, 205, 215)
KATANA_GLINT = (255, 250, 240)
KATANA_HILT = (90, 50, 50)
KATANA_WRAP = (60, 40, 40)
KATANA_GUARD = (170, 150, 50)
WAKI_BLADE = (190, 195, 205)
WAKI_HILT = (80, 45, 45)
BLACK = (30, 30, 30)
HAIR = (35, 25, 20)
HAIR_LIGHT = (55, 40, 35)
WHITE_GLINT = (255, 250, 240)
GOLD_ACCENT = (200, 170, 60)
GOLD_DARK = (160, 130, 40)
TASSEL = (180, 150, 50)
SANDAL_STRAP = (195, 175, 115)

DOWN, UP, LEFT, RIGHT = 0, 1, 2, 3


def ellipse(draw, cx, cy, rx, ry, fill, outline=OUTLINE):
    """Draw an outlined ellipse centered at (cx, cy)."""
    draw.ellipse([cx - rx, cy - ry, cx + rx, cy + ry], fill=fill, outline=outline)


def draw_hakama_pleats(draw, x1, y1, x2, y2, direction):
    """Draw vertical pleat lines on hakama pants."""
    w = x2 - x1
    if w < 3:
        return
    # Draw 2-3 thin darker lines depending on width
    for i in range(1, min(4, w)):
        px = x1 + i * w // min(4, w)
        if px < x2:
            draw.line([px, y1 + 1, px, y2 - 1], fill=INDIGO_PLEAT, width=1)


def draw_sandal(draw, x1, y1, x2, y2):
    """Draw a sandal with straw-colored horizontal straps."""
    draw.rectangle([x1, y1, x2, y2], fill=STRAW_DARK, outline=OUTLINE)
    # Horizontal straps
    h = y2 - y1
    if h >= 2:
        draw.line([x1 + 1, y1 + 1, x2 - 1, y1 + 1], fill=SANDAL_STRAP, width=1)
    if h >= 3:
        draw.line([x1 + 1, y2 - 1, x2 - 1, y2 - 1], fill=SANDAL_STRAP, width=1)


def draw_katana(draw, x_base, y_top, x_tip, y_tip, hilt_x, hilt_y):
    """Draw a katana with 2px wide blade and white glint line."""
    # Blade — 2px wide
    draw.line([x_base, y_top, x_tip, y_tip], fill=KATANA_BLADE, width=2)
    # Glint highlight along blade edge (offset by 1px)
    dx = x_tip - x_base
    dy = y_tip - y_top
    steps = max(abs(dx), abs(dy))
    if steps > 0:
        for i in range(0, steps, 2):
            t = i / steps
            gx = int(x_base + dx * t) + 1
            gy = int(y_top + dy * t)
            draw.point((gx, gy), fill=KATANA_GLINT)
    # Guard (tsuba) — small gold square at blade base
    draw.point((x_base, y_top), fill=KATANA_GUARD)
    draw.point((x_base + 1, y_top), fill=KATANA_GUARD)
    # Hilt (tsuka)
    draw.line([x_base, y_top, hilt_x, hilt_y], fill=KATANA_HILT, width=2)
    # Hilt wrap detail
    mid_hx = (x_base + hilt_x) // 2
    mid_hy = (y_top + hilt_y) // 2
    draw.point((mid_hx, mid_hy), fill=KATANA_WRAP)
    draw.point((hilt_x, hilt_y), fill=KATANA_WRAP)


def draw_wakizashi(draw, x1, y1, x2, y2, hx, hy):
    """Draw a short wakizashi sword tucked at the waist."""
    # Short blade
    draw.line([x1, y1, x2, y2], fill=WAKI_BLADE, width=1)
    # Glint pixel at midpoint
    mx = (x1 + x2) // 2
    my = (y1 + y2) // 2
    draw.point((mx, my), fill=KATANA_GLINT)
    # Hilt
    draw.line([x1, y1, hx, hy], fill=WAKI_HILT, width=1)


def draw_obi_knot(draw, cx, cy):
    """Draw a 2x2 gold obi knot with a tassel hanging from it."""
    # 2x2 knot
    draw.rectangle([cx - 1, cy, cx + 1, cy + 2], fill=GOLD_ACCENT, outline=None)
    draw.point((cx, cy), fill=GOLD_DARK)
    # Tassel hanging down (2-3 pixels)
    draw.line([cx, cy + 2, cx, cy + 4], fill=TASSEL, width=1)
    draw.point((cx, cy + 4), fill=GOLD_DARK)


def draw_kasa_hat(draw, cx, cy, direction):
    """Draw an ornate kasa hat with stitch lines and chin string."""
    if direction == DOWN:
        # Wide brim
        ellipse(draw, cx, cy - 2, 10, 4, STRAW)
        # Top dome
        ellipse(draw, cx, cy - 4, 6, 3, STRAW_LIGHT)
        # Brim line
        draw.line([cx - 10, cy - 1, cx + 10, cy - 1], fill=STRAW_DARK, width=1)
        # Radial stitch lines from center to edge
        draw.line([cx, cy - 5, cx - 7, cy - 1], fill=STRAW_STITCH, width=1)
        draw.line([cx, cy - 5, cx + 7, cy - 1], fill=STRAW_STITCH, width=1)
        draw.line([cx, cy - 5, cx, cy - 1], fill=STRAW_STITCH, width=1)
        # Chin string (two lines hanging from brim sides)
        draw.line([cx - 5, cy, cx - 4, cy + 4], fill=STRAW_DARK, width=1)
        draw.line([cx + 5, cy, cx + 4, cy + 4], fill=STRAW_DARK, width=1)

    elif direction == UP:
        # Brim
        ellipse(draw, cx, cy - 2, 10, 4, STRAW)
        # Top dome (darker from behind)
        ellipse(draw, cx, cy - 4, 6, 3, STRAW_DARK)
        # Stitch lines
        draw.line([cx, cy - 5, cx - 7, cy - 1], fill=STRAW_STITCH, width=1)
        draw.line([cx, cy - 5, cx + 7, cy - 1], fill=STRAW_STITCH, width=1)
        draw.line([cx, cy - 5, cx, cy - 1], fill=STRAW_STITCH, width=1)
        # Chin string visible from behind (hanging down at back)
        draw.line([cx - 3, cy + 2, cx - 2, cy + 5], fill=STRAW_DARK, width=1)
        draw.line([cx + 3, cy + 2, cx + 2, cy + 5], fill=STRAW_DARK, width=1)

    elif direction == LEFT:
        # Brim (extends left)
        ellipse(draw, cx - 1, cy - 2, 9, 4, STRAW)
        # Top dome
        ellipse(draw, cx - 1, cy - 4, 5, 3, STRAW_LIGHT)
        # Brim line
        draw.line([cx - 10, cy - 1, cx + 8, cy - 1], fill=STRAW_DARK, width=1)
        # Stitch lines
        draw.line([cx - 1, cy - 5, cx - 8, cy - 1], fill=STRAW_STITCH, width=1)
        draw.line([cx - 1, cy - 5, cx + 6, cy - 1], fill=STRAW_STITCH, width=1)
        # Chin string
        draw.line([cx + 2, cy, cx + 1, cy + 4], fill=STRAW_DARK, width=1)

    elif direction == RIGHT:
        # Brim (extends right)
        ellipse(draw, cx + 1, cy - 2, 9, 4, STRAW)
        # Top dome
        ellipse(draw, cx + 1, cy - 4, 5, 3, STRAW_LIGHT)
        # Brim line
        draw.line([cx - 8, cy - 1, cx + 10, cy - 1], fill=STRAW_DARK, width=1)
        # Stitch lines
        draw.line([cx + 1, cy - 5, cx + 8, cy - 1], fill=STRAW_STITCH, width=1)
        draw.line([cx + 1, cy - 5, cx - 6, cy - 1], fill=STRAW_STITCH, width=1)
        # Chin string
        draw.line([cx - 2, cy, cx - 1, cy + 4], fill=STRAW_DARK, width=1)


def draw_topknot(draw, cx, cy, direction):
    """Draw a small dark hair bun visible under the hat brim."""
    if direction == UP:
        # Hair bun visible on top of head (under hat brim, seen from behind)
        draw.rectangle([cx - 1, cy - 1, cx + 1, cy + 1], fill=HAIR, outline=None)
        draw.point((cx, cy - 1), fill=HAIR_LIGHT)
    elif direction == LEFT:
        # Small bun peeking out on right side (back of head)
        draw.rectangle([cx + 3, cy - 2, cx + 5, cy], fill=HAIR, outline=None)
        draw.point((cx + 4, cy - 2), fill=HAIR_LIGHT)
    elif direction == RIGHT:
        # Small bun peeking out on left side (back of head)
        draw.rectangle([cx - 5, cy - 2, cx - 3, cy], fill=HAIR, outline=None)
        draw.point((cx - 4, cy - 2), fill=HAIR_LIGHT)


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
        lx1 = body_cx - 6 + leg_spread
        lx2 = body_cx - 2 + leg_spread
        rx1 = body_cx + 2 - leg_spread
        rx2 = body_cx + 6 - leg_spread
        ly1 = body_cy + 4
        ly2 = base_y

        # Left leg
        draw.rectangle([lx1, ly1, lx2, ly2], fill=INDIGO, outline=OUTLINE)
        draw_hakama_pleats(draw, lx1, ly1, lx2, ly2, DOWN)
        # Right leg
        draw.rectangle([rx1, ly1, rx2, ly2], fill=INDIGO, outline=OUTLINE)
        draw_hakama_pleats(draw, rx1, ly1, rx2, ly2, DOWN)
        # Sandals
        draw_sandal(draw, lx1, base_y - 2, lx2, base_y)
        draw_sandal(draw, rx1, base_y - 2, rx2, base_y)

        # --- Katana on back (diagonal, behind body) — LONGER with glint ---
        draw_katana(draw, body_cx + 5, body_cy - 4, body_cx + 9, body_cy + 7,
                    body_cx + 4, body_cy - 8)

        # --- Body (kimono top) ---
        ellipse(draw, body_cx, body_cy, 7, 6, GRAY_KIMONO)
        # Kimono V-neckline
        draw.line([body_cx, body_cy - 4, body_cx - 3, body_cy + 2], fill=GRAY_KIMONO_DARK, width=1)
        draw.line([body_cx, body_cy - 4, body_cx + 3, body_cy + 2], fill=GRAY_KIMONO_DARK, width=1)
        # Obi (belt)
        draw.rectangle([body_cx - 7, body_cy + 3, body_cx + 7, body_cy + 5],
                       fill=INDIGO_DARK, outline=OUTLINE)
        # Ornate obi knot
        draw_obi_knot(draw, body_cx, body_cy + 3)

        # --- Wakizashi tucked in obi (front, angled slightly) ---
        draw_wakizashi(draw, body_cx - 5, body_cy + 3, body_cx - 8, body_cy + 8,
                       body_cx - 4, body_cy + 2)

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
        # Eye glint
        draw.point((body_cx - 2, head_cy), fill=WHITE_GLINT)
        draw.point((body_cx + 2, head_cy), fill=WHITE_GLINT)
        # Kasa hat with stitch lines
        draw_kasa_hat(draw, body_cx, head_cy, DOWN)

    elif direction == UP:
        # --- Legs ---
        lx1 = body_cx - 6 + leg_spread
        lx2 = body_cx - 2 + leg_spread
        rx1 = body_cx + 2 - leg_spread
        rx2 = body_cx + 6 - leg_spread
        ly1 = body_cy + 4
        ly2 = base_y

        draw.rectangle([lx1, ly1, lx2, ly2], fill=INDIGO, outline=OUTLINE)
        draw_hakama_pleats(draw, lx1, ly1, lx2, ly2, UP)
        draw.rectangle([rx1, ly1, rx2, ly2], fill=INDIGO, outline=OUTLINE)
        draw_hakama_pleats(draw, rx1, ly1, rx2, ly2, UP)
        draw_sandal(draw, lx1, base_y - 2, lx2, base_y)
        draw_sandal(draw, rx1, base_y - 2, rx2, base_y)

        # --- Body (back view) ---
        ellipse(draw, body_cx, body_cy, 7, 6, GRAY_KIMONO_DARK)
        # Obi
        draw.rectangle([body_cx - 7, body_cy + 3, body_cx + 7, body_cy + 5],
                       fill=INDIGO_DARK, outline=OUTLINE)

        # --- Katana on back (visible from behind) — LONGER with glint ---
        draw_katana(draw, body_cx + 3, body_cy - 6, body_cx + 7, body_cy + 6,
                    body_cx + 2, body_cy - 10)

        # --- Arms ---
        draw.rectangle([body_cx - 9, body_cy - 3, body_cx - 6, body_cy + 3],
                       fill=GRAY_KIMONO_DARK, outline=OUTLINE)
        draw.rectangle([body_cx + 6, body_cy - 3, body_cx + 9, body_cy + 3],
                       fill=GRAY_KIMONO_DARK, outline=OUTLINE)

        # --- Head (back of kasa hat) ---
        ellipse(draw, body_cx, head_cy + 1, 6, 5, SKIN_DARK)
        # Topknot hair bun (visible under hat brim from behind)
        draw_topknot(draw, body_cx, head_cy + 3, UP)
        # Kasa hat
        draw_kasa_hat(draw, body_cx, head_cy, UP)

    elif direction == LEFT:
        # --- Legs (side view) ---
        # Back leg
        blx1 = body_cx - 1 - leg_spread
        blx2 = body_cx + 2 - leg_spread
        # Front leg
        flx1 = body_cx - 4 + leg_spread
        flx2 = body_cx - 1 + leg_spread
        ly1 = body_cy + 4
        ly2 = base_y

        draw.rectangle([blx1, ly1, blx2, ly2], fill=INDIGO_DARK, outline=OUTLINE)
        draw_hakama_pleats(draw, blx1, ly1, blx2, ly2, LEFT)
        draw_sandal(draw, blx1, base_y - 2, blx2, base_y)
        draw.rectangle([flx1, ly1, flx2, ly2], fill=INDIGO, outline=OUTLINE)
        draw_hakama_pleats(draw, flx1, ly1, flx2, ly2, LEFT)
        draw_sandal(draw, flx1, base_y - 2, flx2, base_y)

        # --- Katana (on far side/back, angled up) — LONGER with glint ---
        draw_katana(draw, body_cx + 4, body_cy - 5, body_cx + 7, body_cy + 6,
                    body_cx + 3, body_cy - 9)

        # --- Body ---
        ellipse(draw, body_cx - 1, body_cy, 6, 6, GRAY_KIMONO)
        ellipse(draw, body_cx - 1, body_cy - 1, 4, 4, GRAY_KIMONO_LIGHT)
        # Obi
        draw.rectangle([body_cx - 7, body_cy + 3, body_cx + 5, body_cy + 5],
                       fill=INDIGO_DARK, outline=OUTLINE)

        # --- Wakizashi tucked in obi (visible from side) ---
        draw_wakizashi(draw, body_cx - 3, body_cy + 4, body_cx - 6, body_cy + 9,
                       body_cx - 2, body_cy + 3)

        # Ornate obi knot (back side, smaller from side view)
        draw.rectangle([body_cx + 3, body_cy + 3, body_cx + 5, body_cy + 5],
                       fill=GOLD_ACCENT, outline=None)
        draw.line([body_cx + 4, body_cy + 5, body_cx + 4, body_cy + 7],
                  fill=TASSEL, width=1)

        # --- Arm (front) ---
        draw.rectangle([body_cx - 7, body_cy - 2, body_cx - 4, body_cy + 3],
                       fill=GRAY_KIMONO, outline=OUTLINE)
        draw.rectangle([body_cx - 7, body_cy + 1, body_cx - 4, body_cy + 3],
                       fill=SKIN, outline=OUTLINE)

        # --- Head (side view, facing left) ---
        ellipse(draw, body_cx - 1, head_cy + 1, 5, 5, SKIN)
        # Eye
        draw.rectangle([body_cx - 4, head_cy, body_cx - 2, head_cy + 2], fill=BLACK)
        draw.point((body_cx - 3, head_cy), fill=WHITE_GLINT)
        # Topknot hair bun
        draw_topknot(draw, body_cx - 1, head_cy + 1, LEFT)
        # Kasa hat (side view)
        draw_kasa_hat(draw, body_cx - 1, head_cy, LEFT)

    elif direction == RIGHT:
        # --- Legs ---
        # Back leg
        brx1 = body_cx - 1 + leg_spread
        brx2 = body_cx + 2 + leg_spread
        # Front leg
        frx1 = body_cx + 2 - leg_spread
        frx2 = body_cx + 5 - leg_spread
        ly1 = body_cy + 4
        ly2 = base_y

        draw.rectangle([brx1, ly1, brx2, ly2], fill=INDIGO_DARK, outline=OUTLINE)
        draw_hakama_pleats(draw, brx1, ly1, brx2, ly2, RIGHT)
        draw_sandal(draw, brx1, base_y - 2, brx2, base_y)
        draw.rectangle([frx1, ly1, frx2, ly2], fill=INDIGO, outline=OUTLINE)
        draw_hakama_pleats(draw, frx1, ly1, frx2, ly2, RIGHT)
        draw_sandal(draw, frx1, base_y - 2, frx2, base_y)

        # --- Katana (on far side/back, angled up) — LONGER with glint ---
        draw_katana(draw, body_cx - 4, body_cy - 5, body_cx - 7, body_cy + 6,
                    body_cx - 3, body_cy - 9)

        # --- Body ---
        ellipse(draw, body_cx + 1, body_cy, 6, 6, GRAY_KIMONO)
        ellipse(draw, body_cx + 1, body_cy - 1, 4, 4, GRAY_KIMONO_LIGHT)
        # Obi
        draw.rectangle([body_cx - 5, body_cy + 3, body_cx + 7, body_cy + 5],
                       fill=INDIGO_DARK, outline=OUTLINE)

        # --- Wakizashi tucked in obi (visible from side) ---
        draw_wakizashi(draw, body_cx + 3, body_cy + 4, body_cx + 6, body_cy + 9,
                       body_cx + 2, body_cy + 3)

        # Ornate obi knot (back side)
        draw.rectangle([body_cx - 5, body_cy + 3, body_cx - 3, body_cy + 5],
                       fill=GOLD_ACCENT, outline=None)
        draw.line([body_cx - 4, body_cy + 5, body_cx - 4, body_cy + 7],
                  fill=TASSEL, width=1)

        # --- Arm ---
        draw.rectangle([body_cx + 4, body_cy - 2, body_cx + 7, body_cy + 3],
                       fill=GRAY_KIMONO, outline=OUTLINE)
        draw.rectangle([body_cx + 4, body_cy + 1, body_cx + 7, body_cy + 3],
                       fill=SKIN, outline=OUTLINE)

        # --- Head ---
        ellipse(draw, body_cx + 1, head_cy + 1, 5, 5, SKIN)
        # Eye
        draw.rectangle([body_cx + 2, head_cy, body_cx + 4, head_cy + 2], fill=BLACK)
        draw.point((body_cx + 3, head_cy), fill=WHITE_GLINT)
        # Topknot hair bun
        draw_topknot(draw, body_cx + 1, head_cy + 1, RIGHT)
        # Kasa hat
        draw_kasa_hat(draw, body_cx + 1, head_cy, RIGHT)


def main():
    img = Image.new("RGBA", (IMG_W, IMG_H), (0, 0, 0, 0))

    for direction in range(ROWS):
        for frame in range(COLS):
            frame_img = Image.new("RGBA", (FRAME_SIZE, FRAME_SIZE), (0, 0, 0, 0))
            frame_draw = ImageDraw.Draw(frame_img)
            draw_samurai(frame_draw, 0, 0, direction, frame)
            img.paste(frame_img, (frame * FRAME_SIZE, direction * FRAME_SIZE))

    img.save("sprites/samurai.png")
    print(f"Generated sprites/samurai.png ({IMG_W}x{IMG_H})")


if __name__ == "__main__":
    main()
