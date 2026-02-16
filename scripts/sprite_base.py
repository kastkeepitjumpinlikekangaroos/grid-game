#!/usr/bin/env python3
"""Shared sprite generation framework for character sprite sheets.

Each character script imports from here and provides:
- A color palette dict
- A draw function that renders one frame
- Calls generate_character() to produce the 128x128 sprite sheet

Output: 128x128 PNG, 4 columns (frames) x 4 rows (directions).
Row layout: Down=0, Up=1, Left=2, Right=3.
"""

from PIL import Image, ImageDraw

FRAME_SIZE = 32
COLS = 4
ROWS = 4
IMG_W = FRAME_SIZE * COLS   # 128
IMG_H = FRAME_SIZE * ROWS   # 128

DOWN, UP, LEFT, RIGHT = 0, 1, 2, 3

# Common outline color
OUTLINE = (40, 35, 35)
BLACK = (30, 30, 30)


def ellipse(draw, cx, cy, rx, ry, fill, outline=OUTLINE):
    draw.ellipse([cx - rx, cy - ry, cx + rx, cy + ry], fill=fill, outline=outline)


def pill(draw, cx, cy, rx, ry, fill, outline=OUTLINE):
    draw.rounded_rectangle([cx - rx, cy - ry, cx + rx, cy + ry],
                           radius=min(rx, ry), fill=fill, outline=outline)


def draw_generic_character(draw, ox, oy, direction, frame,
                           body_color, body_highlight, head_color, head_highlight,
                           skin_color=(220, 180, 140), eye_color=(30, 30, 30),
                           leg_color=(110, 75, 45), accent_color=None,
                           has_cape=False, cape_color=None,
                           hat_style=None, hat_color=None,
                           weapon_style=None):
    """Draw a generic character with customizable colors and features.

    hat_style: None, 'pointed', 'helm', 'hood', 'crown', 'horns', 'ears', 'crest', 'halo', 'antenna'
    weapon_style: None (no visible weapon shown - weapons are projectiles)
    """
    if accent_color is None:
        accent_color = body_color
    if cape_color is None:
        cape_color = body_color
    if hat_color is None:
        hat_color = head_color

    bob = [0, -1, 0, -1][frame]
    leg_spread = [-2, 0, 2, 0][frame]

    base_y = oy + 27 + bob
    body_cx = ox + 16
    body_cy = base_y - 10
    head_cy = body_cy - 10

    def draw_legs():
        if direction in (DOWN, UP):
            draw.rectangle([body_cx - 5 + leg_spread, body_cy + 5,
                            body_cx - 2 + leg_spread, base_y], fill=leg_color, outline=OUTLINE)
            draw.rectangle([body_cx + 2 - leg_spread, body_cy + 5,
                            body_cx + 5 - leg_spread, base_y], fill=leg_color, outline=OUTLINE)
        elif direction == LEFT:
            draw.rectangle([body_cx - 1 - leg_spread, body_cy + 5,
                            body_cx + 2 - leg_spread, base_y], fill=leg_color, outline=OUTLINE)
            draw.rectangle([body_cx - 4 + leg_spread, body_cy + 5,
                            body_cx - 1 + leg_spread, base_y], fill=leg_color, outline=OUTLINE)
        else:  # RIGHT
            draw.rectangle([body_cx - 1 + leg_spread, body_cy + 5,
                            body_cx + 2 + leg_spread, base_y], fill=leg_color, outline=OUTLINE)
            draw.rectangle([body_cx + 2 - leg_spread, body_cy + 5,
                            body_cx + 5 - leg_spread, base_y], fill=leg_color, outline=OUTLINE)

    def draw_cape():
        if not has_cape:
            return
        cape_sway = [0, 1, 0, -1][frame]
        if direction == UP:
            draw.rounded_rectangle([body_cx - 6 + cape_sway, body_cy - 3,
                                    body_cx + 6 + cape_sway, body_cy + 7],
                                   radius=3, fill=cape_color, outline=OUTLINE)
        elif direction == LEFT:
            draw.rounded_rectangle([body_cx + 3, body_cy - 4,
                                    body_cx + 8 + cape_sway, body_cy + 6],
                                   radius=3, fill=cape_color, outline=OUTLINE)
        elif direction == RIGHT:
            csway2 = [0, -1, 0, 1][frame]
            draw.rounded_rectangle([body_cx - 8 + csway2, body_cy - 4,
                                    body_cx - 3, body_cy + 6],
                                   radius=3, fill=cape_color, outline=OUTLINE)

    def draw_body():
        if direction == DOWN:
            ellipse(draw, body_cx, body_cy, 7, 6, body_color)
            ellipse(draw, body_cx, body_cy - 1, 5, 4, body_highlight)
            draw.rectangle([body_cx - 7, body_cy + 3, body_cx + 7, body_cy + 5],
                           fill=accent_color, outline=OUTLINE)
        elif direction == UP:
            ellipse(draw, body_cx, body_cy, 7, 6, body_color)
            ellipse(draw, body_cx, body_cy - 1, 5, 4, _darken(body_color))
        elif direction == LEFT:
            ellipse(draw, body_cx - 1, body_cy, 6, 6, body_color)
            ellipse(draw, body_cx - 1, body_cy - 1, 4, 4, body_highlight)
            draw.rectangle([body_cx - 7, body_cy + 3, body_cx + 5, body_cy + 5],
                           fill=accent_color, outline=OUTLINE)
        else:  # RIGHT
            ellipse(draw, body_cx + 1, body_cy, 6, 6, body_color)
            ellipse(draw, body_cx + 1, body_cy - 1, 4, 4, body_highlight)
            draw.rectangle([body_cx - 5, body_cy + 3, body_cx + 7, body_cy + 5],
                           fill=accent_color, outline=OUTLINE)

    def draw_arms():
        if direction == DOWN:
            draw.rectangle([body_cx - 9, body_cy - 3, body_cx - 6, body_cy + 3],
                           fill=skin_color, outline=OUTLINE)
            draw.rectangle([body_cx + 6, body_cy - 3, body_cx + 9, body_cy + 3],
                           fill=skin_color, outline=OUTLINE)
        elif direction == UP:
            draw.rectangle([body_cx - 9, body_cy - 3, body_cx - 6, body_cy + 3],
                           fill=skin_color, outline=OUTLINE)
            draw.rectangle([body_cx + 6, body_cy - 3, body_cx + 9, body_cy + 3],
                           fill=skin_color, outline=OUTLINE)
        elif direction == LEFT:
            draw.rectangle([body_cx - 7, body_cy - 2, body_cx - 4, body_cy + 3],
                           fill=skin_color, outline=OUTLINE)
        else:
            draw.rectangle([body_cx + 4, body_cy - 2, body_cx + 7, body_cy + 3],
                           fill=skin_color, outline=OUTLINE)

    def draw_head():
        if direction == DOWN:
            ellipse(draw, body_cx, head_cy, 8, 7, head_color)
            ellipse(draw, body_cx, head_cy + 2, 5, 4, skin_color)
            draw.rectangle([body_cx - 3, head_cy + 1, body_cx - 1, head_cy + 3], fill=eye_color)
            draw.rectangle([body_cx + 1, head_cy + 1, body_cx + 3, head_cy + 3], fill=eye_color)
        elif direction == UP:
            ellipse(draw, body_cx, head_cy, 8, 7, head_color)
            ellipse(draw, body_cx, head_cy, 6, 5, _darken(head_color))
        elif direction == LEFT:
            ellipse(draw, body_cx - 1, head_cy, 7, 7, head_color)
            ellipse(draw, body_cx - 3, head_cy + 2, 4, 3, skin_color)
            draw.rectangle([body_cx - 5, head_cy + 1, body_cx - 3, head_cy + 3], fill=eye_color)
        else:
            ellipse(draw, body_cx + 1, head_cy, 7, 7, head_color)
            ellipse(draw, body_cx + 3, head_cy + 2, 4, 3, skin_color)
            draw.rectangle([body_cx + 3, head_cy + 1, body_cx + 5, head_cy + 3], fill=eye_color)

    def draw_hat():
        if hat_style is None:
            return
        hc = hat_color
        if hat_style == 'pointed':
            if direction in (DOWN, UP):
                draw.polygon([(body_cx, head_cy - 14), (body_cx - 5, head_cy - 5),
                              (body_cx + 5, head_cy - 5)], fill=hc, outline=OUTLINE)
            elif direction == LEFT:
                draw.polygon([(body_cx - 3, head_cy - 14), (body_cx - 7, head_cy - 5),
                              (body_cx + 3, head_cy - 5)], fill=hc, outline=OUTLINE)
            else:
                draw.polygon([(body_cx + 3, head_cy - 14), (body_cx - 3, head_cy - 5),
                              (body_cx + 7, head_cy - 5)], fill=hc, outline=OUTLINE)
        elif hat_style == 'helm':
            if direction in (DOWN, UP):
                draw.rectangle([body_cx - 8, head_cy - 3, body_cx + 8, head_cy + 1],
                               fill=hc, outline=OUTLINE)
            elif direction == LEFT:
                draw.rectangle([body_cx - 8, head_cy - 3, body_cx + 4, head_cy + 1],
                               fill=hc, outline=OUTLINE)
            else:
                draw.rectangle([body_cx - 4, head_cy - 3, body_cx + 8, head_cy + 1],
                               fill=hc, outline=OUTLINE)
        elif hat_style == 'hood':
            if direction == DOWN:
                ellipse(draw, body_cx, head_cy - 2, 9, 6, hc)
            elif direction == UP:
                ellipse(draw, body_cx, head_cy - 1, 9, 7, hc)
            elif direction == LEFT:
                ellipse(draw, body_cx - 1, head_cy - 2, 8, 6, hc)
            else:
                ellipse(draw, body_cx + 1, head_cy - 2, 8, 6, hc)
        elif hat_style == 'crown':
            if direction in (DOWN, UP):
                draw.rectangle([body_cx - 6, head_cy - 10, body_cx + 6, head_cy - 6],
                               fill=hc, outline=OUTLINE)
                for px in range(-4, 6, 4):
                    draw.rectangle([body_cx + px - 1, head_cy - 12, body_cx + px + 1, head_cy - 10],
                                   fill=hc, outline=OUTLINE)
            elif direction == LEFT:
                draw.rectangle([body_cx - 6, head_cy - 10, body_cx + 3, head_cy - 6],
                               fill=hc, outline=OUTLINE)
            else:
                draw.rectangle([body_cx - 3, head_cy - 10, body_cx + 6, head_cy - 6],
                               fill=hc, outline=OUTLINE)
        elif hat_style == 'horns':
            if direction in (DOWN, UP):
                draw.rectangle([body_cx - 8, head_cy - 10, body_cx - 6, head_cy - 4],
                               fill=hc, outline=OUTLINE)
                draw.rectangle([body_cx + 6, head_cy - 10, body_cx + 8, head_cy - 4],
                               fill=hc, outline=OUTLINE)
            elif direction == LEFT:
                draw.rectangle([body_cx - 8, head_cy - 10, body_cx - 6, head_cy - 4],
                               fill=hc, outline=OUTLINE)
            else:
                draw.rectangle([body_cx + 6, head_cy - 10, body_cx + 8, head_cy - 4],
                               fill=hc, outline=OUTLINE)
        elif hat_style == 'ears':
            if direction in (DOWN, UP):
                draw.polygon([(body_cx - 6, head_cy - 4), (body_cx - 8, head_cy - 12),
                              (body_cx - 3, head_cy - 6)], fill=hc, outline=OUTLINE)
                draw.polygon([(body_cx + 6, head_cy - 4), (body_cx + 8, head_cy - 12),
                              (body_cx + 3, head_cy - 6)], fill=hc, outline=OUTLINE)
            elif direction == LEFT:
                draw.polygon([(body_cx - 4, head_cy - 4), (body_cx - 6, head_cy - 12),
                              (body_cx - 1, head_cy - 6)], fill=hc, outline=OUTLINE)
            else:
                draw.polygon([(body_cx + 4, head_cy - 4), (body_cx + 6, head_cy - 12),
                              (body_cx + 1, head_cy - 6)], fill=hc, outline=OUTLINE)
        elif hat_style == 'crest':
            if direction in (DOWN, UP):
                draw.rectangle([body_cx - 1, head_cy - 12, body_cx + 1, head_cy - 5],
                               fill=hc, outline=OUTLINE)
            elif direction == LEFT:
                draw.rectangle([body_cx - 2, head_cy - 12, body_cx, head_cy - 5],
                               fill=hc, outline=OUTLINE)
            else:
                draw.rectangle([body_cx, head_cy - 12, body_cx + 2, head_cy - 5],
                               fill=hc, outline=OUTLINE)
        elif hat_style == 'halo':
            if direction in (DOWN, UP):
                draw.ellipse([body_cx - 7, head_cy - 12, body_cx + 7, head_cy - 8],
                             fill=None, outline=hc, width=1)
            elif direction == LEFT:
                draw.ellipse([body_cx - 7, head_cy - 12, body_cx + 4, head_cy - 8],
                             fill=None, outline=hc, width=1)
            else:
                draw.ellipse([body_cx - 4, head_cy - 12, body_cx + 7, head_cy - 8],
                             fill=None, outline=hc, width=1)
        elif hat_style == 'antenna':
            if direction in (DOWN, UP):
                draw.line([(body_cx, head_cy - 7), (body_cx, head_cy - 13)], fill=hc, width=1)
                ellipse(draw, body_cx, head_cy - 14, 2, 2, hc)
            elif direction == LEFT:
                draw.line([(body_cx - 1, head_cy - 7), (body_cx - 1, head_cy - 13)], fill=hc, width=1)
                ellipse(draw, body_cx - 1, head_cy - 14, 2, 2, hc)
            else:
                draw.line([(body_cx + 1, head_cy - 7), (body_cx + 1, head_cy - 13)], fill=hc, width=1)
                ellipse(draw, body_cx + 1, head_cy - 14, 2, 2, hc)

    # Draw order: legs, cape (behind), body, arms, head, hat
    draw_legs()
    if direction != DOWN:
        draw_cape()
    draw_body()
    draw_arms()
    draw_head()
    draw_hat()
    if direction == DOWN:
        draw_cape()


def _darken(color, factor=0.75):
    return tuple(int(c * factor) for c in color)


def generate_character(name, draw_func=None, **kwargs):
    """Generate a character sprite sheet.

    If draw_func is provided, it's called as draw_func(draw, ox, oy, direction, frame).
    Otherwise, draw_generic_character is used with kwargs.
    """
    img = Image.new("RGBA", (IMG_W, IMG_H), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    for direction in range(ROWS):
        for frame_idx in range(COLS):
            ox = frame_idx * FRAME_SIZE
            oy = direction * FRAME_SIZE
            if draw_func:
                draw_func(draw, ox, oy, direction, frame_idx)
            else:
                draw_generic_character(draw, ox, oy, direction, frame_idx, **kwargs)

    path = f"sprites/{name}.png"
    img.save(path)
    print(f"Generated {path} ({IMG_W}x{IMG_H})")
