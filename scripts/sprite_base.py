#!/usr/bin/env python3
"""Shared sprite generation framework for character sprite sheets.

Each character script imports from here and provides:
- A color palette dict
- A draw function that renders one frame
- Calls generate_character() to produce the 256x256 sprite sheet

Output: 256x256 PNG, 4 columns (frames) x 4 rows (directions).
Row layout: Down=0, Up=1, Left=2, Right=3.
"""

from PIL import Image, ImageDraw

FRAME_SIZE = 64
COLS = 4
ROWS = 4
IMG_W = FRAME_SIZE * COLS   # 256
IMG_H = FRAME_SIZE * ROWS   # 256

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

    hat_style: None, 'pointed', 'helm', 'hood', 'crown', 'horns', 'ears', 'crest', 'halo', 'antenna',
               'bandana', 'tophat', 'toque', 'feathered', 'fin'
    weapon_style: None (no visible weapon shown - weapons are projectiles)
    """
    if accent_color is None:
        accent_color = body_color
    if cape_color is None:
        cape_color = body_color
    if hat_color is None:
        hat_color = head_color

    bob = [0, -2, 0, -1][frame]
    leg_spread = [-4, 0, 4, 0][frame]

    base_y = oy + 54 + bob
    body_cx = ox + 32
    body_cy = base_y - 20
    head_cy = body_cy - 20

    boot_color = _darken(leg_color, 0.65)
    boot_highlight = _brighten(leg_color, 0.9)
    arm_color = body_color
    hand_color = skin_color
    belt_buckle = _brighten(accent_color, 1.4)
    belt_buckle_highlight = _brighten(accent_color, 1.7)
    head_dark = _darken(head_color, 0.75)
    head_shadow = _darken(head_color, 0.6)
    hat_dark = _darken(hat_color, 0.7)
    hat_light = _brighten(hat_color, 1.25)
    body_shadow = _darken(body_color, 0.65)
    body_dark = _darken(body_color, 0.8)
    skin_shadow = _darken(skin_color, 0.8)
    skin_nose = _darken(skin_color, 0.7)
    leg_highlight = _brighten(leg_color, 1.15)
    arm_highlight = _brighten(body_color, 1.15)
    # Determine if eyes should glow (non-standard eye color)
    has_glow_eyes = eye_color != (30, 30, 30)
    eye_bright = _brighten(eye_color, 1.5) if has_glow_eyes else eye_color

    def draw_legs():
        if direction in (DOWN, UP):
            # Left leg
            draw.rectangle([body_cx - 10 + leg_spread, body_cy + 10,
                            body_cx - 4 + leg_spread, base_y], fill=leg_color, outline=OUTLINE)
            # Left leg highlight stripe (light-facing side)
            draw.rectangle([body_cx - 10 + leg_spread, body_cy + 10,
                            body_cx - 8 + leg_spread, base_y - 6], fill=leg_highlight, outline=None)
            # Right leg
            draw.rectangle([body_cx + 4 - leg_spread, body_cy + 10,
                            body_cx + 10 - leg_spread, base_y], fill=leg_color, outline=OUTLINE)
            # Right leg highlight stripe
            draw.rectangle([body_cx + 4 - leg_spread, body_cy + 10,
                            body_cx + 6 - leg_spread, base_y - 6], fill=leg_highlight, outline=None)
            # Boots - left
            draw.rectangle([body_cx - 10 + leg_spread, base_y - 6,
                            body_cx - 4 + leg_spread, base_y], fill=boot_color, outline=OUTLINE)
            # Boot sole line - left
            draw.line([(body_cx - 10 + leg_spread, base_y - 1),
                       (body_cx - 4 + leg_spread, base_y - 1)],
                      fill=_darken(boot_color, 0.7), width=1)
            # Boot lace dot - left
            draw.point((body_cx - 7 + leg_spread, base_y - 4), fill=boot_highlight)
            # Boots - right
            draw.rectangle([body_cx + 4 - leg_spread, base_y - 6,
                            body_cx + 10 - leg_spread, base_y], fill=boot_color, outline=OUTLINE)
            # Boot sole line - right
            draw.line([(body_cx + 4 - leg_spread, base_y - 1),
                       (body_cx + 10 - leg_spread, base_y - 1)],
                      fill=_darken(boot_color, 0.7), width=1)
            # Boot lace dot - right
            draw.point((body_cx + 7 - leg_spread, base_y - 4), fill=boot_highlight)
        elif direction == LEFT:
            # Back leg
            draw.rectangle([body_cx - 2 - leg_spread, body_cy + 10,
                            body_cx + 4 - leg_spread, base_y], fill=_darken(leg_color, 0.85), outline=OUTLINE)
            draw.rectangle([body_cx - 2 - leg_spread, base_y - 6,
                            body_cx + 4 - leg_spread, base_y], fill=boot_color, outline=OUTLINE)
            draw.line([(body_cx - 2 - leg_spread, base_y - 1),
                       (body_cx + 4 - leg_spread, base_y - 1)],
                      fill=_darken(boot_color, 0.7), width=1)
            # Front leg
            draw.rectangle([body_cx - 8 + leg_spread, body_cy + 10,
                            body_cx - 2 + leg_spread, base_y], fill=leg_color, outline=OUTLINE)
            # Front leg highlight
            draw.rectangle([body_cx - 8 + leg_spread, body_cy + 10,
                            body_cx - 6 + leg_spread, base_y - 6], fill=leg_highlight, outline=None)
            draw.rectangle([body_cx - 8 + leg_spread, base_y - 6,
                            body_cx - 2 + leg_spread, base_y], fill=boot_color, outline=OUTLINE)
            draw.line([(body_cx - 8 + leg_spread, base_y - 1),
                       (body_cx - 2 + leg_spread, base_y - 1)],
                      fill=_darken(boot_color, 0.7), width=1)
            draw.point((body_cx - 5 + leg_spread, base_y - 4), fill=boot_highlight)
        else:  # RIGHT
            # Back leg
            draw.rectangle([body_cx - 2 + leg_spread, body_cy + 10,
                            body_cx + 4 + leg_spread, base_y], fill=_darken(leg_color, 0.85), outline=OUTLINE)
            draw.rectangle([body_cx - 2 + leg_spread, base_y - 6,
                            body_cx + 4 + leg_spread, base_y], fill=boot_color, outline=OUTLINE)
            draw.line([(body_cx - 2 + leg_spread, base_y - 1),
                       (body_cx + 4 + leg_spread, base_y - 1)],
                      fill=_darken(boot_color, 0.7), width=1)
            # Front leg
            draw.rectangle([body_cx + 4 - leg_spread, body_cy + 10,
                            body_cx + 10 - leg_spread, base_y], fill=leg_color, outline=OUTLINE)
            # Front leg highlight
            draw.rectangle([body_cx + 8 - leg_spread, body_cy + 10,
                            body_cx + 10 - leg_spread, base_y - 6], fill=leg_highlight, outline=None)
            draw.rectangle([body_cx + 4 - leg_spread, base_y - 6,
                            body_cx + 10 - leg_spread, base_y], fill=boot_color, outline=OUTLINE)
            draw.line([(body_cx + 4 - leg_spread, base_y - 1),
                       (body_cx + 10 - leg_spread, base_y - 1)],
                      fill=_darken(boot_color, 0.7), width=1)
            draw.point((body_cx + 7 - leg_spread, base_y - 4), fill=boot_highlight)

    def draw_cape():
        if not has_cape:
            return
        cape_sway = [0, 2, 0, -2][frame]
        cape_inner = _brighten(cape_color, 1.2)
        cape_hem = _darken(cape_color, 0.7)
        if direction == DOWN:
            # Cape behind body, visible at sides
            draw.polygon([
                (body_cx - 16, body_cy - 6),
                (body_cx + 16, body_cy - 6),
                (body_cx + 18 + cape_sway, base_y - 2),
                (body_cx - 18 + cape_sway, base_y - 2),
            ], fill=cape_color, outline=OUTLINE)
            # Fabric fold lines
            draw.line([(body_cx - 6, body_cy), (body_cx - 8 + cape_sway, base_y - 4)],
                      fill=_darken(cape_color, 0.85), width=1)
            draw.line([(body_cx + 6, body_cy), (body_cx + 8 + cape_sway, base_y - 4)],
                      fill=_darken(cape_color, 0.85), width=1)
            # Darker hem at bottom
            draw.line([(body_cx - 17 + cape_sway, base_y - 3),
                       (body_cx + 17 + cape_sway, base_y - 3)],
                      fill=cape_hem, width=1)
        elif direction == UP:
            draw.polygon([
                (body_cx - 16, body_cy - 8),
                (body_cx + 16, body_cy - 8),
                (body_cx + 20 + cape_sway, base_y),
                (body_cx - 20 + cape_sway, base_y),
            ], fill=cape_color, outline=OUTLINE)
            # Inner cape highlight (lining visible from back)
            draw.polygon([
                (body_cx - 10, body_cy - 4),
                (body_cx + 10, body_cy - 4),
                (body_cx + 14 + cape_sway, base_y - 4),
                (body_cx - 14 + cape_sway, base_y - 4),
            ], fill=cape_inner, outline=None)
            # Fabric fold lines
            draw.line([(body_cx - 4, body_cy - 2), (body_cx - 6 + cape_sway, base_y - 6)],
                      fill=_darken(cape_color, 0.85), width=1)
            draw.line([(body_cx + 4, body_cy - 2), (body_cx + 6 + cape_sway, base_y - 6)],
                      fill=_darken(cape_color, 0.85), width=1)
            draw.line([(body_cx, body_cy), (body_cx + cape_sway, base_y - 6)],
                      fill=_darken(cape_color, 0.85), width=1)
        elif direction == LEFT:
            draw.polygon([
                (body_cx + 6, body_cy - 10),
                (body_cx + 18, body_cy - 6),
                (body_cx + 20 + cape_sway, base_y),
                (body_cx + 6, base_y),
            ], fill=cape_color, outline=OUTLINE)
            # Inner lining visible
            draw.polygon([
                (body_cx + 8, body_cy - 6),
                (body_cx + 14, body_cy - 4),
                (body_cx + 16 + cape_sway, base_y - 2),
                (body_cx + 8, base_y - 2),
            ], fill=cape_inner, outline=None)
            # Fold line
            draw.line([(body_cx + 12, body_cy - 4), (body_cx + 14 + cape_sway, base_y - 4)],
                      fill=_darken(cape_color, 0.85), width=1)
        elif direction == RIGHT:
            csway2 = [0, -2, 0, 2][frame]
            draw.polygon([
                (body_cx - 6, body_cy - 10),
                (body_cx - 18, body_cy - 6),
                (body_cx - 20 + csway2, base_y),
                (body_cx - 6, base_y),
            ], fill=cape_color, outline=OUTLINE)
            # Inner lining visible
            draw.polygon([
                (body_cx - 8, body_cy - 6),
                (body_cx - 14, body_cy - 4),
                (body_cx - 16 + csway2, base_y - 2),
                (body_cx - 8, base_y - 2),
            ], fill=cape_inner, outline=None)
            # Fold line
            draw.line([(body_cx - 12, body_cy - 4), (body_cx - 14 + csway2, base_y - 4)],
                      fill=_darken(cape_color, 0.85), width=1)

    def draw_body():
        if direction == DOWN:
            # Main body - 3-tone gradient shading
            ellipse(draw, body_cx, body_cy, 14, 12, body_color)
            # Dark shadow on right/bottom
            ellipse(draw, body_cx + 4, body_cy + 2, 10, 8, body_shadow, outline=None)
            # Base color center
            ellipse(draw, body_cx, body_cy, 11, 9, body_color, outline=None)
            # Highlight upper-left
            ellipse(draw, body_cx - 3, body_cy - 2, 8, 6, body_highlight, outline=None)
            # Vest/armor detail -- V-neck seam with shadow
            draw.line([(body_cx, body_cy - 8), (body_cx - 4, body_cy + 4)],
                      fill=body_dark, width=2)
            draw.line([(body_cx, body_cy - 8), (body_cx + 4, body_cy + 4)],
                      fill=body_dark, width=2)
            # Accent trim lines along edges
            draw.arc([body_cx - 14, body_cy - 12, body_cx + 14, body_cy + 12],
                     start=160, end=200, fill=accent_color, width=1)
            # Belt with buckle (wider, 8px tall)
            draw.rectangle([body_cx - 14, body_cy + 6, body_cx + 14, body_cy + 14],
                           fill=accent_color, outline=OUTLINE)
            # Belt buckle (4x4 metallic square with highlight dot)
            draw.rectangle([body_cx - 4, body_cy + 7, body_cx + 4, body_cy + 13],
                           fill=belt_buckle, outline=OUTLINE)
            draw.point((body_cx - 1, body_cy + 9), fill=belt_buckle_highlight)
        elif direction == UP:
            ellipse(draw, body_cx, body_cy, 14, 12, body_color)
            # Shadow on right
            ellipse(draw, body_cx + 4, body_cy + 2, 10, 8, body_shadow, outline=None)
            # Center
            ellipse(draw, body_cx, body_cy, 11, 9, body_color, outline=None)
            # Darker upper area for back view
            ellipse(draw, body_cx, body_cy - 2, 10, 8, _darken(body_color), outline=None)
            # Back seam
            draw.line([(body_cx, body_cy - 6), (body_cx, body_cy + 6)],
                      fill=_darken(body_color, 0.75), width=2)
            # Belt
            draw.rectangle([body_cx - 14, body_cy + 6, body_cx + 14, body_cy + 14],
                           fill=accent_color, outline=OUTLINE)
        elif direction == LEFT:
            ellipse(draw, body_cx - 2, body_cy, 12, 12, body_color)
            # Shadow on right side
            ellipse(draw, body_cx + 2, body_cy + 2, 8, 8, body_shadow, outline=None)
            # Center
            ellipse(draw, body_cx - 2, body_cy, 9, 9, body_color, outline=None)
            # Highlight
            ellipse(draw, body_cx - 4, body_cy - 2, 6, 6, body_highlight, outline=None)
            # Belt
            draw.rectangle([body_cx - 14, body_cy + 6, body_cx + 10, body_cy + 14],
                           fill=accent_color, outline=OUTLINE)
            # Belt buckle
            draw.rectangle([body_cx - 6, body_cy + 7, body_cx - 2, body_cy + 13],
                           fill=belt_buckle, outline=OUTLINE)
        else:  # RIGHT
            ellipse(draw, body_cx + 2, body_cy, 12, 12, body_color)
            # Shadow on right
            ellipse(draw, body_cx + 6, body_cy + 2, 8, 8, body_shadow, outline=None)
            # Center
            ellipse(draw, body_cx + 2, body_cy, 9, 9, body_color, outline=None)
            # Highlight
            ellipse(draw, body_cx, body_cy - 2, 6, 6, body_highlight, outline=None)
            # Belt
            draw.rectangle([body_cx - 10, body_cy + 6, body_cx + 14, body_cy + 14],
                           fill=accent_color, outline=OUTLINE)
            # Belt buckle
            draw.rectangle([body_cx + 2, body_cy + 7, body_cx + 6, body_cy + 13],
                           fill=belt_buckle, outline=OUTLINE)

    def draw_arms():
        if direction == DOWN:
            # Left arm (sleeved, 6px wide)
            draw.rectangle([body_cx - 18, body_cy - 6, body_cx - 12, body_cy + 6],
                           fill=arm_color, outline=OUTLINE)
            # Left arm highlight (light-facing side)
            draw.rectangle([body_cx - 18, body_cy - 6, body_cx - 16, body_cy + 4],
                           fill=arm_highlight, outline=None)
            # Left hand
            draw.rectangle([body_cx - 18, body_cy + 2, body_cx - 12, body_cy + 6],
                           fill=hand_color, outline=OUTLINE)
            # Right arm (sleeved, 6px wide)
            draw.rectangle([body_cx + 12, body_cy - 6, body_cx + 18, body_cy + 6],
                           fill=arm_color, outline=OUTLINE)
            # Right arm highlight
            draw.rectangle([body_cx + 12, body_cy - 6, body_cx + 14, body_cy + 4],
                           fill=arm_highlight, outline=None)
            # Right hand
            draw.rectangle([body_cx + 12, body_cy + 2, body_cx + 18, body_cy + 6],
                           fill=hand_color, outline=OUTLINE)
            # Shoulder pads (larger with gradient shading)
            ellipse(draw, body_cx - 14, body_cy - 6, 6, 4, accent_color)
            # Shoulder highlight top
            ellipse(draw, body_cx - 14, body_cy - 8, 4, 2, _brighten(accent_color, 1.2), outline=None)
            ellipse(draw, body_cx + 14, body_cy - 6, 6, 4, accent_color)
            ellipse(draw, body_cx + 14, body_cy - 8, 4, 2, _brighten(accent_color, 1.2), outline=None)
        elif direction == UP:
            draw.rectangle([body_cx - 18, body_cy - 6, body_cx - 12, body_cy + 6],
                           fill=arm_color, outline=OUTLINE)
            draw.rectangle([body_cx + 12, body_cy - 6, body_cx + 18, body_cy + 6],
                           fill=arm_color, outline=OUTLINE)
            # Shoulder pads (back view)
            ellipse(draw, body_cx - 14, body_cy - 6, 6, 4, accent_color)
            ellipse(draw, body_cx - 14, body_cy - 8, 4, 2, _brighten(accent_color, 1.2), outline=None)
            ellipse(draw, body_cx + 14, body_cy - 6, 6, 4, accent_color)
            ellipse(draw, body_cx + 14, body_cy - 8, 4, 2, _brighten(accent_color, 1.2), outline=None)
        elif direction == LEFT:
            draw.rectangle([body_cx - 14, body_cy - 4, body_cx - 8, body_cy + 6],
                           fill=arm_color, outline=OUTLINE)
            # Arm highlight
            draw.rectangle([body_cx - 14, body_cy - 4, body_cx - 12, body_cy + 4],
                           fill=arm_highlight, outline=None)
            # Hand
            draw.rectangle([body_cx - 14, body_cy + 2, body_cx - 8, body_cy + 6],
                           fill=hand_color, outline=OUTLINE)
            # Shoulder (larger with gradient)
            ellipse(draw, body_cx - 10, body_cy - 6, 6, 4, accent_color)
            ellipse(draw, body_cx - 10, body_cy - 8, 4, 2, _brighten(accent_color, 1.2), outline=None)
        else:
            draw.rectangle([body_cx + 8, body_cy - 4, body_cx + 14, body_cy + 6],
                           fill=arm_color, outline=OUTLINE)
            # Arm highlight
            draw.rectangle([body_cx + 12, body_cy - 4, body_cx + 14, body_cy + 4],
                           fill=arm_highlight, outline=None)
            # Hand
            draw.rectangle([body_cx + 8, body_cy + 2, body_cx + 14, body_cy + 6],
                           fill=hand_color, outline=OUTLINE)
            # Shoulder (larger with gradient)
            ellipse(draw, body_cx + 10, body_cy - 6, 6, 4, accent_color)
            ellipse(draw, body_cx + 10, body_cy - 8, 4, 2, _brighten(accent_color, 1.2), outline=None)

    def draw_head():
        if direction == DOWN:
            # Head - 3-tone gradient shading with overlapping ellipses
            ellipse(draw, body_cx, head_cy, 16, 14, head_color)
            # Shadow chin (darker bottom)
            ellipse(draw, body_cx + 2, head_cy + 4, 12, 8, head_shadow, outline=None)
            # Base color center
            ellipse(draw, body_cx, head_cy, 13, 11, head_color, outline=None)
            # Highlight crown (lighter upper-left)
            ellipse(draw, body_cx - 3, head_cy - 4, 9, 6, head_highlight, outline=None)
            # Head highlight (shine spots)
            draw.point((body_cx - 6, head_cy - 8), fill=head_highlight)
            draw.point((body_cx - 4, head_cy - 8), fill=head_highlight)
            draw.point((body_cx - 5, head_cy - 9), fill=head_highlight)
            # Face area
            ellipse(draw, body_cx, head_cy + 4, 10, 8, skin_color)
            # Face shadow on lower-right
            ellipse(draw, body_cx + 2, head_cy + 6, 7, 5, skin_shadow, outline=None)
            # Eyes (4x4 with 2x2 pupil + 1px white highlight)
            draw.rectangle([body_cx - 7, head_cy + 2, body_cx - 3, head_cy + 6], fill=(255, 255, 255))
            draw.rectangle([body_cx - 6, head_cy + 3, body_cx - 4, head_cy + 5], fill=eye_color)
            draw.point((body_cx - 6, head_cy + 3), fill=(255, 255, 255))
            draw.rectangle([body_cx + 3, head_cy + 2, body_cx + 7, head_cy + 6], fill=(255, 255, 255))
            draw.rectangle([body_cx + 4, head_cy + 3, body_cx + 6, head_cy + 5], fill=eye_color)
            draw.point((body_cx + 4, head_cy + 3), fill=(255, 255, 255))
            if has_glow_eyes:
                draw.rectangle([body_cx - 6, head_cy + 3, body_cx - 4, head_cy + 5], fill=eye_bright)
                draw.rectangle([body_cx + 4, head_cy + 3, body_cx + 6, head_cy + 5], fill=eye_bright)
            # Eyebrows (1px dark lines above eyes)
            draw.line([(body_cx - 7, head_cy + 1), (body_cx - 3, head_cy + 1)],
                      fill=_darken(skin_color, 0.5), width=1)
            draw.line([(body_cx + 3, head_cy + 1), (body_cx + 7, head_cy + 1)],
                      fill=_darken(skin_color, 0.5), width=1)
            # Nose dot (1px skin-tone darker)
            draw.point((body_cx, head_cy + 7), fill=skin_nose)
            # Mouth line (1px below nose)
            draw.line([(body_cx - 2, head_cy + 9), (body_cx + 2, head_cy + 9)],
                      fill=skin_nose, width=1)
        elif direction == UP:
            ellipse(draw, body_cx, head_cy, 16, 14, head_color)
            # Shadow
            ellipse(draw, body_cx + 2, head_cy + 4, 12, 8, head_shadow, outline=None)
            # Center
            ellipse(draw, body_cx, head_cy, 13, 11, head_color, outline=None)
            # Dark back-of-head
            ellipse(draw, body_cx, head_cy, 12, 10, head_dark, outline=None)
            # Subtle highlight
            draw.point((body_cx - 6, head_cy - 8), fill=head_highlight)
            draw.point((body_cx - 5, head_cy - 9), fill=head_highlight)
        elif direction == LEFT:
            ellipse(draw, body_cx - 2, head_cy, 14, 14, head_color)
            # Shadow
            ellipse(draw, body_cx + 2, head_cy + 3, 10, 8, head_shadow, outline=None)
            # Center
            ellipse(draw, body_cx - 2, head_cy, 11, 11, head_color, outline=None)
            # Highlight
            ellipse(draw, body_cx - 5, head_cy - 4, 7, 5, head_highlight, outline=None)
            draw.point((body_cx - 8, head_cy - 8), fill=head_highlight)
            # Face
            ellipse(draw, body_cx - 6, head_cy + 4, 8, 6, skin_color)
            # Face shadow
            ellipse(draw, body_cx - 4, head_cy + 6, 5, 4, skin_shadow, outline=None)
            # Eye (4x4 with pupil + highlight)
            draw.rectangle([body_cx - 11, head_cy + 2, body_cx - 7, head_cy + 6], fill=(255, 255, 255))
            draw.rectangle([body_cx - 10, head_cy + 3, body_cx - 8, head_cy + 5], fill=eye_color)
            draw.point((body_cx - 10, head_cy + 3), fill=(255, 255, 255))
            if has_glow_eyes:
                draw.rectangle([body_cx - 10, head_cy + 3, body_cx - 8, head_cy + 5], fill=eye_bright)
            # Eyebrow
            draw.line([(body_cx - 11, head_cy + 1), (body_cx - 7, head_cy + 1)],
                      fill=_darken(skin_color, 0.5), width=1)
            # Nose
            draw.point((body_cx - 10, head_cy + 7), fill=skin_nose)
            # Mouth
            draw.line([(body_cx - 9, head_cy + 9), (body_cx - 7, head_cy + 9)],
                      fill=skin_nose, width=1)
        else:
            ellipse(draw, body_cx + 2, head_cy, 14, 14, head_color)
            # Shadow
            ellipse(draw, body_cx + 6, head_cy + 3, 10, 8, head_shadow, outline=None)
            # Center
            ellipse(draw, body_cx + 2, head_cy, 11, 11, head_color, outline=None)
            # Highlight
            ellipse(draw, body_cx - 1, head_cy - 4, 7, 5, head_highlight, outline=None)
            draw.point((body_cx + 8, head_cy - 8), fill=head_highlight)
            # Face
            ellipse(draw, body_cx + 6, head_cy + 4, 8, 6, skin_color)
            # Face shadow
            ellipse(draw, body_cx + 8, head_cy + 6, 5, 4, skin_shadow, outline=None)
            # Eye (4x4 with pupil + highlight)
            draw.rectangle([body_cx + 7, head_cy + 2, body_cx + 11, head_cy + 6], fill=(255, 255, 255))
            draw.rectangle([body_cx + 8, head_cy + 3, body_cx + 10, head_cy + 5], fill=eye_color)
            draw.point((body_cx + 8, head_cy + 3), fill=(255, 255, 255))
            if has_glow_eyes:
                draw.rectangle([body_cx + 8, head_cy + 3, body_cx + 10, head_cy + 5], fill=eye_bright)
            # Eyebrow
            draw.line([(body_cx + 7, head_cy + 1), (body_cx + 11, head_cy + 1)],
                      fill=_darken(skin_color, 0.5), width=1)
            # Nose
            draw.point((body_cx + 10, head_cy + 7), fill=skin_nose)
            # Mouth
            draw.line([(body_cx + 7, head_cy + 9), (body_cx + 9, head_cy + 9)],
                      fill=skin_nose, width=1)

    def draw_hat():
        if hat_style is None:
            return
        hc = hat_color
        if hat_style == 'pointed':
            if direction in (DOWN, UP):
                # Wider brim
                ellipse(draw, body_cx, head_cy - 8, 16, 4, hc)
                # Cone
                draw.polygon([(body_cx, head_cy - 28), (body_cx - 10, head_cy - 10),
                              (body_cx + 10, head_cy - 10)], fill=hc, outline=OUTLINE)
                # Gradient shading on cone - shadow right side
                draw.polygon([(body_cx + 2, head_cy - 24), (body_cx + 10, head_cy - 10),
                              (body_cx + 6, head_cy - 10)], fill=hat_dark, outline=None)
                # Highlight left side
                draw.line([(body_cx - 2, head_cy - 24), (body_cx - 6, head_cy - 12)],
                          fill=hat_light, width=1)
                # Band
                draw.rectangle([body_cx - 10, head_cy - 12, body_cx + 10, head_cy - 8],
                               fill=_brighten(accent_color, 1.2), outline=None)
                # Star/moon decal on cone
                draw.point((body_cx - 2, head_cy - 18), fill=_brighten(hat_color, 1.6))
                draw.point((body_cx - 3, head_cy - 19), fill=_brighten(hat_color, 1.5))
                draw.point((body_cx - 1, head_cy - 19), fill=_brighten(hat_color, 1.5))
                # Fabric fold lines
                draw.line([(body_cx - 4, head_cy - 20), (body_cx - 8, head_cy - 12)],
                          fill=_darken(hc, 0.85), width=1)
                # Tip highlight
                draw.point((body_cx - 2, head_cy - 24), fill=hat_light)
                draw.point((body_cx - 1, head_cy - 26), fill=hat_light)
            elif direction == LEFT:
                ellipse(draw, body_cx - 2, head_cy - 8, 14, 4, hc)
                draw.polygon([(body_cx - 6, head_cy - 28), (body_cx - 14, head_cy - 10),
                              (body_cx + 6, head_cy - 10)], fill=hc, outline=OUTLINE)
                draw.polygon([(body_cx - 2, head_cy - 24), (body_cx + 6, head_cy - 10),
                              (body_cx + 2, head_cy - 10)], fill=hat_dark, outline=None)
                draw.rectangle([body_cx - 12, head_cy - 12, body_cx + 6, head_cy - 8],
                               fill=_brighten(accent_color, 1.2), outline=None)
                draw.point((body_cx - 6, head_cy - 20), fill=_brighten(hat_color, 1.6))
                draw.line([(body_cx - 8, head_cy - 20), (body_cx - 12, head_cy - 12)],
                          fill=_darken(hc, 0.85), width=1)
            else:
                ellipse(draw, body_cx + 2, head_cy - 8, 14, 4, hc)
                draw.polygon([(body_cx + 6, head_cy - 28), (body_cx - 6, head_cy - 10),
                              (body_cx + 14, head_cy - 10)], fill=hc, outline=OUTLINE)
                draw.polygon([(body_cx + 10, head_cy - 24), (body_cx + 14, head_cy - 10),
                              (body_cx + 10, head_cy - 10)], fill=hat_dark, outline=None)
                draw.rectangle([body_cx - 6, head_cy - 12, body_cx + 12, head_cy - 8],
                               fill=_brighten(accent_color, 1.2), outline=None)
                draw.point((body_cx + 6, head_cy - 20), fill=_brighten(hat_color, 1.6))
                draw.line([(body_cx + 8, head_cy - 20), (body_cx + 12, head_cy - 12)],
                          fill=_darken(hc, 0.85), width=1)
        elif hat_style == 'helm':
            if direction in (DOWN, UP):
                # Helm dome covers top of head
                draw.rectangle([body_cx - 16, head_cy - 6, body_cx + 16, head_cy + 2],
                               fill=hc, outline=OUTLINE)
                # Gradient shading - shadow on right
                draw.rectangle([body_cx + 6, head_cy - 5, body_cx + 15, head_cy + 1],
                               fill=hat_dark, outline=None)
                # Crest ridge on top with plume detail
                draw.rectangle([body_cx - 2, head_cy - 14, body_cx + 2, head_cy - 4],
                               fill=hat_light, outline=OUTLINE)
                # Plume detail (small triangular extension)
                draw.polygon([(body_cx - 2, head_cy - 14), (body_cx, head_cy - 18),
                              (body_cx + 2, head_cy - 14)], fill=hat_light, outline=OUTLINE)
                # Visor slit
                if direction == DOWN:
                    draw.line([(body_cx - 10, head_cy - 1), (body_cx + 10, head_cy - 1)],
                              fill=BLACK, width=2)
                # Cheek guards
                draw.rectangle([body_cx - 16, head_cy + 0, body_cx - 12, head_cy + 6],
                               fill=hc, outline=OUTLINE)
                draw.rectangle([body_cx + 12, head_cy + 0, body_cx + 16, head_cy + 6],
                               fill=hc, outline=OUTLINE)
                # Rivet details
                draw.point((body_cx - 12, head_cy - 2), fill=hat_light)
                draw.point((body_cx + 12, head_cy - 2), fill=hat_light)
                draw.point((body_cx - 14, head_cy + 3), fill=hat_light)
                draw.point((body_cx + 14, head_cy + 3), fill=hat_light)
            elif direction == LEFT:
                draw.rectangle([body_cx - 16, head_cy - 6, body_cx + 8, head_cy + 2],
                               fill=hc, outline=OUTLINE)
                draw.rectangle([body_cx + 2, head_cy - 5, body_cx + 7, head_cy + 1],
                               fill=hat_dark, outline=None)
                draw.rectangle([body_cx - 4, head_cy - 14, body_cx, head_cy - 4],
                               fill=hat_light, outline=OUTLINE)
                draw.polygon([(body_cx - 4, head_cy - 14), (body_cx - 2, head_cy - 18),
                              (body_cx, head_cy - 14)], fill=hat_light, outline=OUTLINE)
                # Cheek guard
                draw.rectangle([body_cx - 16, head_cy + 0, body_cx - 12, head_cy + 6],
                               fill=hc, outline=OUTLINE)
                draw.point((body_cx - 14, head_cy + 3), fill=hat_light)
            else:
                draw.rectangle([body_cx - 8, head_cy - 6, body_cx + 16, head_cy + 2],
                               fill=hc, outline=OUTLINE)
                draw.rectangle([body_cx + 8, head_cy - 5, body_cx + 15, head_cy + 1],
                               fill=hat_dark, outline=None)
                draw.rectangle([body_cx, head_cy - 14, body_cx + 4, head_cy - 4],
                               fill=hat_light, outline=OUTLINE)
                draw.polygon([(body_cx, head_cy - 14), (body_cx + 2, head_cy - 18),
                              (body_cx + 4, head_cy - 14)], fill=hat_light, outline=OUTLINE)
                # Cheek guard
                draw.rectangle([body_cx + 12, head_cy + 0, body_cx + 16, head_cy + 6],
                               fill=hc, outline=OUTLINE)
                draw.point((body_cx + 14, head_cy + 3), fill=hat_light)
        elif hat_style == 'hood':
            if direction == DOWN:
                ellipse(draw, body_cx, head_cy - 4, 18, 12, hc)
                # Gradient shading
                ellipse(draw, body_cx + 4, head_cy - 2, 12, 8, hat_dark, outline=None)
                ellipse(draw, body_cx - 2, head_cy - 6, 10, 6, _brighten(hc, 1.1), outline=None)
                # Hood peak
                draw.polygon([(body_cx - 4, head_cy - 16), (body_cx, head_cy - 22),
                              (body_cx + 4, head_cy - 16)], fill=hc, outline=OUTLINE)
                # Inner shadow
                ellipse(draw, body_cx, head_cy - 2, 14, 8, hat_dark, outline=None)
                # Draped fabric folds (2-3 curved lines)
                draw.arc([body_cx - 14, head_cy - 10, body_cx - 4, head_cy + 4],
                         start=180, end=360, fill=_darken(hc, 0.8), width=1)
                draw.arc([body_cx + 4, head_cy - 10, body_cx + 14, head_cy + 4],
                         start=180, end=360, fill=_darken(hc, 0.8), width=1)
                draw.arc([body_cx - 8, head_cy - 6, body_cx + 8, head_cy + 2],
                         start=200, end=340, fill=_darken(hc, 0.75), width=1)
            elif direction == UP:
                ellipse(draw, body_cx, head_cy - 2, 18, 14, hc)
                ellipse(draw, body_cx + 4, head_cy, 12, 10, hat_dark, outline=None)
                ellipse(draw, body_cx - 2, head_cy - 4, 12, 8, _brighten(hc, 1.1), outline=None)
                draw.polygon([(body_cx - 4, head_cy - 16), (body_cx, head_cy - 22),
                              (body_cx + 4, head_cy - 16)], fill=hc, outline=OUTLINE)
                ellipse(draw, body_cx, head_cy, 14, 10, hat_dark, outline=None)
                # Fabric folds on back
                draw.line([(body_cx - 6, head_cy - 8), (body_cx - 8, head_cy + 6)],
                          fill=_darken(hc, 0.8), width=1)
                draw.line([(body_cx + 6, head_cy - 8), (body_cx + 8, head_cy + 6)],
                          fill=_darken(hc, 0.8), width=1)
                draw.line([(body_cx, head_cy - 10), (body_cx, head_cy + 4)],
                          fill=_darken(hc, 0.8), width=1)
            elif direction == LEFT:
                ellipse(draw, body_cx - 2, head_cy - 4, 16, 12, hc)
                ellipse(draw, body_cx + 2, head_cy - 2, 10, 8, hat_dark, outline=None)
                ellipse(draw, body_cx - 6, head_cy - 6, 8, 6, _brighten(hc, 1.1), outline=None)
                draw.polygon([(body_cx - 6, head_cy - 16), (body_cx - 2, head_cy - 22),
                              (body_cx + 2, head_cy - 16)], fill=hc, outline=OUTLINE)
                # Fabric fold
                draw.arc([body_cx - 12, head_cy - 8, body_cx - 2, head_cy + 4],
                         start=180, end=360, fill=_darken(hc, 0.8), width=1)
            else:
                ellipse(draw, body_cx + 2, head_cy - 4, 16, 12, hc)
                ellipse(draw, body_cx + 6, head_cy - 2, 10, 8, hat_dark, outline=None)
                ellipse(draw, body_cx, head_cy - 6, 8, 6, _brighten(hc, 1.1), outline=None)
                draw.polygon([(body_cx - 2, head_cy - 16), (body_cx + 2, head_cy - 22),
                              (body_cx + 6, head_cy - 16)], fill=hc, outline=OUTLINE)
                # Fabric fold
                draw.arc([body_cx + 2, head_cy - 8, body_cx + 12, head_cy + 4],
                         start=180, end=360, fill=_darken(hc, 0.8), width=1)
        elif hat_style == 'crown':
            if direction in (DOWN, UP):
                draw.rectangle([body_cx - 12, head_cy - 20, body_cx + 12, head_cy - 12],
                               fill=hc, outline=OUTLINE)
                # Gradient shading on crown band
                draw.rectangle([body_cx + 4, head_cy - 19, body_cx + 11, head_cy - 13],
                               fill=hat_dark, outline=None)
                # Crown points with individual prong detail
                for px in range(-8, 12, 8):
                    draw.rectangle([body_cx + px - 2, head_cy - 24, body_cx + px + 2, head_cy - 20],
                                   fill=hc, outline=OUTLINE)
                    # Gem dot at tip of each prong
                    draw.point((body_cx + px, head_cy - 22), fill=_brighten(accent_color, 1.8))
                # Gems on front band
                draw.point((body_cx, head_cy - 16), fill=_brighten(accent_color, 1.5))
                draw.point((body_cx - 1, head_cy - 16), fill=_brighten(accent_color, 1.3))
                draw.point((body_cx + 1, head_cy - 16), fill=_brighten(accent_color, 1.3))
                draw.point((body_cx - 6, head_cy - 16), fill=hat_light)
                draw.point((body_cx + 6, head_cy - 16), fill=hat_light)
            elif direction == LEFT:
                draw.rectangle([body_cx - 12, head_cy - 20, body_cx + 6, head_cy - 12],
                               fill=hc, outline=OUTLINE)
                draw.rectangle([body_cx, head_cy - 19, body_cx + 5, head_cy - 13],
                               fill=hat_dark, outline=None)
                draw.rectangle([body_cx - 10, head_cy - 24, body_cx - 6, head_cy - 20],
                               fill=hc, outline=OUTLINE)
                draw.point((body_cx - 8, head_cy - 22), fill=_brighten(accent_color, 1.8))
                draw.rectangle([body_cx + 2, head_cy - 24, body_cx + 6, head_cy - 20],
                               fill=hc, outline=OUTLINE)
                draw.point((body_cx + 4, head_cy - 22), fill=_brighten(accent_color, 1.8))
                draw.point((body_cx - 4, head_cy - 16), fill=_brighten(accent_color, 1.5))
            else:
                draw.rectangle([body_cx - 6, head_cy - 20, body_cx + 12, head_cy - 12],
                               fill=hc, outline=OUTLINE)
                draw.rectangle([body_cx + 4, head_cy - 19, body_cx + 11, head_cy - 13],
                               fill=hat_dark, outline=None)
                draw.rectangle([body_cx - 6, head_cy - 24, body_cx - 2, head_cy - 20],
                               fill=hc, outline=OUTLINE)
                draw.point((body_cx - 4, head_cy - 22), fill=_brighten(accent_color, 1.8))
                draw.rectangle([body_cx + 6, head_cy - 24, body_cx + 10, head_cy - 20],
                               fill=hc, outline=OUTLINE)
                draw.point((body_cx + 8, head_cy - 22), fill=_brighten(accent_color, 1.8))
                draw.point((body_cx + 4, head_cy - 16), fill=_brighten(accent_color, 1.5))
        elif hat_style == 'horns':
            if direction in (DOWN, UP):
                # Curved horns (wider base, tapered tip) with ring texture
                draw.polygon([(body_cx - 12, head_cy - 8), (body_cx - 18, head_cy - 24),
                              (body_cx - 14, head_cy - 22), (body_cx - 10, head_cy - 10)],
                             fill=hc, outline=OUTLINE)
                draw.polygon([(body_cx + 12, head_cy - 8), (body_cx + 18, head_cy - 24),
                              (body_cx + 14, head_cy - 22), (body_cx + 10, head_cy - 10)],
                             fill=hc, outline=OUTLINE)
                # Ring texture (horizontal lines) - gradient base-to-tip
                draw.line([(body_cx - 11, head_cy - 12), (body_cx - 13, head_cy - 12)],
                          fill=hat_dark, width=1)
                draw.line([(body_cx - 12, head_cy - 16), (body_cx - 15, head_cy - 16)],
                          fill=hat_dark, width=1)
                draw.line([(body_cx - 14, head_cy - 20), (body_cx - 16, head_cy - 20)],
                          fill=hat_dark, width=1)
                draw.line([(body_cx + 11, head_cy - 12), (body_cx + 13, head_cy - 12)],
                          fill=hat_dark, width=1)
                draw.line([(body_cx + 12, head_cy - 16), (body_cx + 15, head_cy - 16)],
                          fill=hat_dark, width=1)
                draw.line([(body_cx + 14, head_cy - 20), (body_cx + 16, head_cy - 20)],
                          fill=hat_dark, width=1)
                # Horn tips (lighter)
                draw.point((body_cx - 18, head_cy - 24), fill=hat_light)
                draw.point((body_cx - 17, head_cy - 23), fill=hat_light)
                draw.point((body_cx + 18, head_cy - 24), fill=hat_light)
                draw.point((body_cx + 17, head_cy - 23), fill=hat_light)
            elif direction == LEFT:
                draw.polygon([(body_cx - 10, head_cy - 8), (body_cx - 16, head_cy - 24),
                              (body_cx - 12, head_cy - 22), (body_cx - 8, head_cy - 10)],
                             fill=hc, outline=OUTLINE)
                # Ring texture
                draw.line([(body_cx - 9, head_cy - 12), (body_cx - 12, head_cy - 12)],
                          fill=hat_dark, width=1)
                draw.line([(body_cx - 11, head_cy - 16), (body_cx - 14, head_cy - 16)],
                          fill=hat_dark, width=1)
                draw.line([(body_cx - 13, head_cy - 20), (body_cx - 15, head_cy - 20)],
                          fill=hat_dark, width=1)
                draw.point((body_cx - 16, head_cy - 24), fill=hat_light)
                draw.point((body_cx - 15, head_cy - 23), fill=hat_light)
            else:
                draw.polygon([(body_cx + 10, head_cy - 8), (body_cx + 16, head_cy - 24),
                              (body_cx + 12, head_cy - 22), (body_cx + 8, head_cy - 10)],
                             fill=hc, outline=OUTLINE)
                # Ring texture
                draw.line([(body_cx + 9, head_cy - 12), (body_cx + 12, head_cy - 12)],
                          fill=hat_dark, width=1)
                draw.line([(body_cx + 11, head_cy - 16), (body_cx + 14, head_cy - 16)],
                          fill=hat_dark, width=1)
                draw.line([(body_cx + 13, head_cy - 20), (body_cx + 15, head_cy - 20)],
                          fill=hat_dark, width=1)
                draw.point((body_cx + 16, head_cy - 24), fill=hat_light)
                draw.point((body_cx + 15, head_cy - 23), fill=hat_light)
        elif hat_style == 'ears':
            if direction in (DOWN, UP):
                # Rounded triangular ears
                draw.polygon([(body_cx - 12, head_cy - 8), (body_cx - 16, head_cy - 24),
                              (body_cx - 6, head_cy - 12)], fill=hc, outline=OUTLINE)
                draw.polygon([(body_cx + 12, head_cy - 8), (body_cx + 16, head_cy - 24),
                              (body_cx + 6, head_cy - 12)], fill=hc, outline=OUTLINE)
                # Inner ear pink accent
                draw.polygon([(body_cx - 10, head_cy - 10), (body_cx - 14, head_cy - 20),
                              (body_cx - 8, head_cy - 14)], fill=_brighten(hc, 1.3), outline=None)
                draw.polygon([(body_cx + 10, head_cy - 10), (body_cx + 14, head_cy - 20),
                              (body_cx + 8, head_cy - 14)], fill=_brighten(hc, 1.3), outline=None)
                # Fur texture (speckle dots)
                draw.point((body_cx - 12, head_cy - 14), fill=_darken(hc, 0.85))
                draw.point((body_cx - 14, head_cy - 18), fill=_darken(hc, 0.85))
                draw.point((body_cx + 12, head_cy - 14), fill=_darken(hc, 0.85))
                draw.point((body_cx + 14, head_cy - 18), fill=_darken(hc, 0.85))
                draw.point((body_cx - 11, head_cy - 16), fill=_brighten(hc, 1.1))
                draw.point((body_cx + 11, head_cy - 16), fill=_brighten(hc, 1.1))
            elif direction == LEFT:
                draw.polygon([(body_cx - 8, head_cy - 8), (body_cx - 12, head_cy - 24),
                              (body_cx - 2, head_cy - 12)], fill=hc, outline=OUTLINE)
                # Inner ear pink accent
                draw.polygon([(body_cx - 6, head_cy - 10), (body_cx - 10, head_cy - 20),
                              (body_cx - 4, head_cy - 14)], fill=_brighten(hc, 1.3), outline=None)
                # Fur texture
                draw.point((body_cx - 8, head_cy - 14), fill=_darken(hc, 0.85))
                draw.point((body_cx - 10, head_cy - 18), fill=_darken(hc, 0.85))
                draw.point((body_cx - 7, head_cy - 16), fill=_brighten(hc, 1.1))
            else:
                draw.polygon([(body_cx + 8, head_cy - 8), (body_cx + 12, head_cy - 24),
                              (body_cx + 2, head_cy - 12)], fill=hc, outline=OUTLINE)
                # Inner ear pink accent
                draw.polygon([(body_cx + 6, head_cy - 10), (body_cx + 10, head_cy - 20),
                              (body_cx + 4, head_cy - 14)], fill=_brighten(hc, 1.3), outline=None)
                # Fur texture
                draw.point((body_cx + 8, head_cy - 14), fill=_darken(hc, 0.85))
                draw.point((body_cx + 10, head_cy - 18), fill=_darken(hc, 0.85))
                draw.point((body_cx + 7, head_cy - 16), fill=_brighten(hc, 1.1))
        elif hat_style == 'crest':
            if direction in (DOWN, UP):
                # Wider, more dramatic crest with taper
                draw.polygon([(body_cx - 4, head_cy - 10), (body_cx - 2, head_cy - 26),
                              (body_cx + 2, head_cy - 26), (body_cx + 4, head_cy - 10)],
                             fill=hc, outline=OUTLINE)
                # Gradient shading - highlight on left, shadow on right
                draw.line([(body_cx - 2, head_cy - 24), (body_cx - 3, head_cy - 12)],
                          fill=hat_light, width=1)
                draw.line([(body_cx + 2, head_cy - 24), (body_cx + 3, head_cy - 12)],
                          fill=hat_dark, width=1)
                draw.point((body_cx, head_cy - 24), fill=hat_light)
                draw.point((body_cx, head_cy - 22), fill=hat_light)
            elif direction == LEFT:
                draw.polygon([(body_cx - 4, head_cy - 10), (body_cx - 4, head_cy - 26),
                              (body_cx, head_cy - 26), (body_cx, head_cy - 10)],
                             fill=hc, outline=OUTLINE)
                draw.line([(body_cx - 3, head_cy - 24), (body_cx - 3, head_cy - 12)],
                          fill=hat_light, width=1)
            else:
                draw.polygon([(body_cx, head_cy - 10), (body_cx, head_cy - 26),
                              (body_cx + 4, head_cy - 26), (body_cx + 4, head_cy - 10)],
                             fill=hc, outline=OUTLINE)
                draw.line([(body_cx + 1, head_cy - 24), (body_cx + 1, head_cy - 12)],
                          fill=hat_light, width=1)
        elif hat_style == 'halo':
            if direction in (DOWN, UP):
                # Gradient glow ring - outer ring
                draw.ellipse([body_cx - 16, head_cy - 26, body_cx + 16, head_cy - 16],
                             fill=None, outline=hc, width=3)
                # Inner glow (semi-transparent effect via lighter color)
                draw.ellipse([body_cx - 14, head_cy - 24, body_cx + 14, head_cy - 18],
                             fill=None, outline=hat_light, width=1)
                # Sparkle dots
                draw.point((body_cx - 12, head_cy - 22), fill=hat_light)
                draw.point((body_cx + 12, head_cy - 22), fill=hat_light)
                draw.point((body_cx, head_cy - 26), fill=_brighten(hc, 1.5))
                draw.point((body_cx - 8, head_cy - 24), fill=_brighten(hc, 1.4))
                draw.point((body_cx + 8, head_cy - 24), fill=_brighten(hc, 1.4))
            elif direction == LEFT:
                draw.ellipse([body_cx - 16, head_cy - 26, body_cx + 8, head_cy - 16],
                             fill=None, outline=hc, width=3)
                draw.ellipse([body_cx - 14, head_cy - 24, body_cx + 6, head_cy - 18],
                             fill=None, outline=hat_light, width=1)
                draw.point((body_cx - 10, head_cy - 24), fill=_brighten(hc, 1.5))
                draw.point((body_cx + 2, head_cy - 22), fill=hat_light)
            else:
                draw.ellipse([body_cx - 8, head_cy - 26, body_cx + 16, head_cy - 16],
                             fill=None, outline=hc, width=3)
                draw.ellipse([body_cx - 6, head_cy - 24, body_cx + 14, head_cy - 18],
                             fill=None, outline=hat_light, width=1)
                draw.point((body_cx + 10, head_cy - 24), fill=_brighten(hc, 1.5))
                draw.point((body_cx - 2, head_cy - 22), fill=hat_light)
        elif hat_style == 'antenna':
            if direction in (DOWN, UP):
                draw.line([(body_cx, head_cy - 14), (body_cx, head_cy - 26)], fill=hc, width=2)
                ellipse(draw, body_cx, head_cy - 28, 4, 4, hc)
                # Antenna glow highlight
                ellipse(draw, body_cx - 1, head_cy - 29, 2, 2, _brighten(hc, 1.5), outline=None)
                draw.point((body_cx - 2, head_cy - 28), fill=_brighten(hc, 1.5))
                draw.point((body_cx + 2, head_cy - 28), fill=_brighten(hc, 1.5))
                draw.point((body_cx, head_cy - 30), fill=_brighten(hc, 1.5))
            elif direction == LEFT:
                draw.line([(body_cx - 2, head_cy - 14), (body_cx - 2, head_cy - 26)], fill=hc, width=2)
                ellipse(draw, body_cx - 2, head_cy - 28, 4, 4, hc)
                ellipse(draw, body_cx - 3, head_cy - 29, 2, 2, _brighten(hc, 1.5), outline=None)
                draw.point((body_cx - 4, head_cy - 28), fill=_brighten(hc, 1.5))
            else:
                draw.line([(body_cx + 2, head_cy - 14), (body_cx + 2, head_cy - 26)], fill=hc, width=2)
                ellipse(draw, body_cx + 2, head_cy - 28, 4, 4, hc)
                ellipse(draw, body_cx + 1, head_cy - 29, 2, 2, _brighten(hc, 1.5), outline=None)
                draw.point((body_cx + 4, head_cy - 28), fill=_brighten(hc, 1.5))
        elif hat_style == 'bandana':
            # Pirate-style bandana/headscarf -- tied at back
            if direction in (DOWN, UP):
                draw.rectangle([body_cx - 16, head_cy - 8, body_cx + 16, head_cy - 2],
                               fill=hc, outline=OUTLINE)
                # Gradient shading
                draw.rectangle([body_cx + 6, head_cy - 7, body_cx + 15, head_cy - 3],
                               fill=hat_dark, outline=None)
                # Knot tails at back
                if direction == UP:
                    draw.line([(body_cx + 6, head_cy - 2), (body_cx + 10, head_cy + 6)],
                              fill=hc, width=3)
                    draw.line([(body_cx + 10, head_cy - 2), (body_cx + 14, head_cy + 4)],
                              fill=hat_dark, width=2)
                # Highlight stripe
                draw.line([(body_cx - 12, head_cy - 6), (body_cx + 12, head_cy - 6)],
                          fill=hat_light, width=1)
                # Second stripe detail
                draw.line([(body_cx - 10, head_cy - 4), (body_cx + 10, head_cy - 4)],
                          fill=_darken(hc, 0.9), width=1)
            elif direction == LEFT:
                draw.rectangle([body_cx - 16, head_cy - 8, body_cx + 8, head_cy - 2],
                               fill=hc, outline=OUTLINE)
                draw.rectangle([body_cx + 2, head_cy - 7, body_cx + 7, head_cy - 3],
                               fill=hat_dark, outline=None)
                # Tails trailing right
                draw.line([(body_cx + 6, head_cy - 4), (body_cx + 14, head_cy + 2)],
                          fill=hc, width=3)
                draw.line([(body_cx - 12, head_cy - 6), (body_cx + 4, head_cy - 6)],
                          fill=hat_light, width=1)
            else:
                draw.rectangle([body_cx - 8, head_cy - 8, body_cx + 16, head_cy - 2],
                               fill=hc, outline=OUTLINE)
                draw.rectangle([body_cx + 6, head_cy - 7, body_cx + 15, head_cy - 3],
                               fill=hat_dark, outline=None)
                # Tails trailing left
                draw.line([(body_cx - 6, head_cy - 4), (body_cx - 14, head_cy + 2)],
                          fill=hc, width=3)
                draw.line([(body_cx - 4, head_cy - 6), (body_cx + 12, head_cy - 6)],
                          fill=hat_light, width=1)
        elif hat_style == 'tophat':
            # Tall top hat -- gentleman/gambler style
            if direction in (DOWN, UP):
                # Brim
                draw.rectangle([body_cx - 16, head_cy - 10, body_cx + 16, head_cy - 6],
                               fill=hc, outline=OUTLINE)
                # Tall crown
                draw.rectangle([body_cx - 10, head_cy - 28, body_cx + 10, head_cy - 10],
                               fill=hc, outline=OUTLINE)
                # Gradient shading on crown
                draw.rectangle([body_cx + 4, head_cy - 27, body_cx + 9, head_cy - 11],
                               fill=hat_dark, outline=None)
                # Band
                draw.rectangle([body_cx - 10, head_cy - 14, body_cx + 10, head_cy - 10],
                               fill=_brighten(accent_color, 1.2), outline=None)
                # Shine highlight
                draw.line([(body_cx - 6, head_cy - 24), (body_cx - 6, head_cy - 16)],
                          fill=hat_light, width=1)
                draw.point((body_cx - 6, head_cy - 24), fill=hat_light)
            elif direction == LEFT:
                draw.rectangle([body_cx - 16, head_cy - 10, body_cx + 8, head_cy - 6],
                               fill=hc, outline=OUTLINE)
                draw.rectangle([body_cx - 10, head_cy - 28, body_cx + 4, head_cy - 10],
                               fill=hc, outline=OUTLINE)
                draw.rectangle([body_cx, head_cy - 27, body_cx + 3, head_cy - 11],
                               fill=hat_dark, outline=None)
                draw.rectangle([body_cx - 10, head_cy - 14, body_cx + 4, head_cy - 10],
                               fill=_brighten(accent_color, 1.2), outline=None)
                draw.line([(body_cx - 6, head_cy - 24), (body_cx - 6, head_cy - 16)],
                          fill=hat_light, width=1)
            else:
                draw.rectangle([body_cx - 8, head_cy - 10, body_cx + 16, head_cy - 6],
                               fill=hc, outline=OUTLINE)
                draw.rectangle([body_cx - 4, head_cy - 28, body_cx + 10, head_cy - 10],
                               fill=hc, outline=OUTLINE)
                draw.rectangle([body_cx + 4, head_cy - 27, body_cx + 9, head_cy - 11],
                               fill=hat_dark, outline=None)
                draw.rectangle([body_cx - 4, head_cy - 14, body_cx + 10, head_cy - 10],
                               fill=_brighten(accent_color, 1.2), outline=None)
                draw.line([(body_cx, head_cy - 24), (body_cx, head_cy - 16)],
                          fill=hat_light, width=1)
        elif hat_style == 'toque':
            # Chef's toque -- tall white puffy hat
            if direction in (DOWN, UP):
                # Puffy top
                ellipse(draw, body_cx, head_cy - 20, 12, 10, hc)
                # Puff highlight
                ellipse(draw, body_cx - 3, head_cy - 22, 6, 4, hat_light, outline=None)
                # Middle
                draw.rectangle([body_cx - 10, head_cy - 16, body_cx + 10, head_cy - 8],
                               fill=hc, outline=OUTLINE)
                # Shadow on right side of middle
                draw.rectangle([body_cx + 4, head_cy - 15, body_cx + 9, head_cy - 9],
                               fill=hat_dark, outline=None)
                # Band at base
                draw.rectangle([body_cx - 12, head_cy - 10, body_cx + 12, head_cy - 6],
                               fill=hat_dark, outline=OUTLINE)
                # Puff highlights
                draw.point((body_cx - 4, head_cy - 24), fill=hat_light)
                draw.point((body_cx + 2, head_cy - 22), fill=hat_light)
                draw.point((body_cx - 2, head_cy - 26), fill=hat_light)
            elif direction == LEFT:
                ellipse(draw, body_cx - 2, head_cy - 20, 10, 10, hc)
                ellipse(draw, body_cx - 5, head_cy - 22, 5, 4, hat_light, outline=None)
                draw.rectangle([body_cx - 10, head_cy - 16, body_cx + 6, head_cy - 8],
                               fill=hc, outline=OUTLINE)
                draw.rectangle([body_cx + 2, head_cy - 15, body_cx + 5, head_cy - 9],
                               fill=hat_dark, outline=None)
                draw.rectangle([body_cx - 12, head_cy - 10, body_cx + 8, head_cy - 6],
                               fill=hat_dark, outline=OUTLINE)
                draw.point((body_cx - 4, head_cy - 26), fill=hat_light)
            else:
                ellipse(draw, body_cx + 2, head_cy - 20, 10, 10, hc)
                ellipse(draw, body_cx - 1, head_cy - 22, 5, 4, hat_light, outline=None)
                draw.rectangle([body_cx - 6, head_cy - 16, body_cx + 10, head_cy - 8],
                               fill=hc, outline=OUTLINE)
                draw.rectangle([body_cx + 4, head_cy - 15, body_cx + 9, head_cy - 9],
                               fill=hat_dark, outline=None)
                draw.rectangle([body_cx - 8, head_cy - 10, body_cx + 12, head_cy - 6],
                               fill=hat_dark, outline=OUTLINE)
                draw.point((body_cx + 4, head_cy - 26), fill=hat_light)
        elif hat_style == 'feathered':
            # Bard/musketeer feathered cap -- floppy beret with a plume
            if direction in (DOWN, UP):
                # Floppy beret
                ellipse(draw, body_cx, head_cy - 8, 16, 6, hc)
                # Gradient shading
                ellipse(draw, body_cx + 4, head_cy - 6, 10, 4, hat_dark, outline=None)
                ellipse(draw, body_cx + 4, head_cy - 10, 10, 6, hat_light, outline=None)
                # Feather plume
                draw.line([(body_cx + 10, head_cy - 10), (body_cx + 16, head_cy - 24)],
                          fill=_brighten(accent_color, 1.3), width=3)
                # Feather barb lines
                draw.line([(body_cx + 12, head_cy - 16), (body_cx + 16, head_cy - 18)],
                          fill=_brighten(accent_color, 1.1), width=1)
                draw.line([(body_cx + 13, head_cy - 20), (body_cx + 17, head_cy - 22)],
                          fill=_brighten(accent_color, 1.1), width=1)
                draw.point((body_cx + 16, head_cy - 24), fill=_brighten(accent_color, 1.6))
            elif direction == LEFT:
                ellipse(draw, body_cx - 2, head_cy - 8, 14, 6, hc)
                ellipse(draw, body_cx + 2, head_cy - 6, 8, 4, hat_dark, outline=None)
                ellipse(draw, body_cx - 4, head_cy - 10, 8, 4, hat_light, outline=None)
                # Feather trails behind
                draw.line([(body_cx + 6, head_cy - 10), (body_cx + 14, head_cy - 22)],
                          fill=_brighten(accent_color, 1.3), width=3)
                draw.line([(body_cx + 8, head_cy - 14), (body_cx + 12, head_cy - 16)],
                          fill=_brighten(accent_color, 1.1), width=1)
                draw.point((body_cx + 14, head_cy - 22), fill=_brighten(accent_color, 1.6))
            else:
                ellipse(draw, body_cx + 2, head_cy - 8, 14, 6, hc)
                ellipse(draw, body_cx + 6, head_cy - 6, 8, 4, hat_dark, outline=None)
                ellipse(draw, body_cx, head_cy - 10, 8, 4, hat_light, outline=None)
                draw.line([(body_cx - 6, head_cy - 10), (body_cx - 14, head_cy - 22)],
                          fill=_brighten(accent_color, 1.3), width=3)
                draw.line([(body_cx - 8, head_cy - 14), (body_cx - 12, head_cy - 16)],
                          fill=_brighten(accent_color, 1.1), width=1)
                draw.point((body_cx - 14, head_cy - 22), fill=_brighten(accent_color, 1.6))
        elif hat_style == 'fin':
            # Dorsal fin -- for shark/fish characters
            if direction in (DOWN, UP):
                draw.polygon([(body_cx - 2, head_cy - 8), (body_cx, head_cy - 26),
                              (body_cx + 6, head_cy - 12), (body_cx + 2, head_cy - 8)],
                             fill=hc, outline=OUTLINE)
                # Gradient shading
                draw.line([(body_cx, head_cy - 22), (body_cx - 1, head_cy - 10)],
                          fill=hat_light, width=1)
                draw.line([(body_cx + 4, head_cy - 14), (body_cx + 2, head_cy - 10)],
                          fill=hat_dark, width=1)
                draw.point((body_cx, head_cy - 22), fill=hat_light)
                draw.point((body_cx, head_cy - 24), fill=hat_light)
            elif direction == LEFT:
                draw.polygon([(body_cx - 2, head_cy - 8), (body_cx - 2, head_cy - 26),
                              (body_cx + 6, head_cy - 12), (body_cx + 2, head_cy - 8)],
                             fill=hc, outline=OUTLINE)
                draw.line([(body_cx - 1, head_cy - 22), (body_cx - 1, head_cy - 10)],
                          fill=hat_light, width=1)
                draw.line([(body_cx + 4, head_cy - 14), (body_cx + 2, head_cy - 10)],
                          fill=hat_dark, width=1)
            else:
                draw.polygon([(body_cx - 2, head_cy - 8), (body_cx + 2, head_cy - 26),
                              (body_cx - 6, head_cy - 12), (body_cx + 2, head_cy - 8)],
                             fill=hc, outline=OUTLINE)
                draw.line([(body_cx + 1, head_cy - 22), (body_cx + 1, head_cy - 10)],
                          fill=hat_light, width=1)
                draw.line([(body_cx - 4, head_cy - 14), (body_cx - 2, head_cy - 10)],
                          fill=hat_dark, width=1)

    # Draw order: legs, cape (behind), body, arms, head, hat
    draw_legs()
    draw_cape()
    draw_body()
    draw_arms()
    draw_head()
    draw_hat()


def _darken(color, factor=0.75):
    return tuple(max(0, int(c * factor)) for c in color[:3])


def _brighten(color, factor=1.25):
    return tuple(min(255, int(c * factor)) for c in color[:3])


def generate_character(name, draw_func=None, **kwargs):
    """Generate a character sprite sheet.

    If draw_func is provided, it's called as draw_func(draw, ox, oy, direction, frame).
    Otherwise, draw_generic_character is used with kwargs.

    Each frame is drawn on its own 64x64 canvas to prevent pixel bleeding
    between adjacent cells when elements extend beyond frame boundaries.
    """
    img = Image.new("RGBA", (IMG_W, IMG_H), (0, 0, 0, 0))

    for direction in range(ROWS):
        for frame_idx in range(COLS):
            frame_img = Image.new("RGBA", (FRAME_SIZE, FRAME_SIZE), (0, 0, 0, 0))
            frame_draw = ImageDraw.Draw(frame_img)
            if draw_func:
                draw_func(frame_draw, 0, 0, direction, frame_idx)
            else:
                draw_generic_character(frame_draw, 0, 0, direction, frame_idx, **kwargs)
            img.paste(frame_img, (frame_idx * FRAME_SIZE, direction * FRAME_SIZE))

    path = f"sprites/{name}.png"
    img.save(path)
    print(f"Generated {path} ({IMG_W}x{IMG_H})")
