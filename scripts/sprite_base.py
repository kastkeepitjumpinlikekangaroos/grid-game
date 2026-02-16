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

    bob = [0, -1, 0, -1][frame]
    leg_spread = [-2, 0, 2, 0][frame]

    base_y = oy + 27 + bob
    body_cx = ox + 16
    body_cy = base_y - 10
    head_cy = body_cy - 10

    boot_color = _darken(leg_color, 0.65)
    arm_color = body_color
    hand_color = skin_color
    belt_buckle = _brighten(accent_color, 1.4)
    head_dark = _darken(head_color, 0.75)
    hat_dark = _darken(hat_color, 0.7)
    hat_light = _brighten(hat_color, 1.25)
    # Determine if eyes should glow (non-standard eye color)
    has_glow_eyes = eye_color != (30, 30, 30)
    eye_bright = _brighten(eye_color, 1.5) if has_glow_eyes else eye_color

    def draw_legs():
        if direction in (DOWN, UP):
            # Left leg
            draw.rectangle([body_cx - 5 + leg_spread, body_cy + 5,
                            body_cx - 2 + leg_spread, base_y], fill=leg_color, outline=OUTLINE)
            # Right leg
            draw.rectangle([body_cx + 2 - leg_spread, body_cy + 5,
                            body_cx + 5 - leg_spread, base_y], fill=leg_color, outline=OUTLINE)
            # Boots
            draw.rectangle([body_cx - 5 + leg_spread, base_y - 3,
                            body_cx - 2 + leg_spread, base_y], fill=boot_color, outline=OUTLINE)
            draw.rectangle([body_cx + 2 - leg_spread, base_y - 3,
                            body_cx + 5 - leg_spread, base_y], fill=boot_color, outline=OUTLINE)
        elif direction == LEFT:
            # Back leg
            draw.rectangle([body_cx - 1 - leg_spread, body_cy + 5,
                            body_cx + 2 - leg_spread, base_y], fill=_darken(leg_color, 0.85), outline=OUTLINE)
            draw.rectangle([body_cx - 1 - leg_spread, base_y - 3,
                            body_cx + 2 - leg_spread, base_y], fill=boot_color, outline=OUTLINE)
            # Front leg
            draw.rectangle([body_cx - 4 + leg_spread, body_cy + 5,
                            body_cx - 1 + leg_spread, base_y], fill=leg_color, outline=OUTLINE)
            draw.rectangle([body_cx - 4 + leg_spread, base_y - 3,
                            body_cx - 1 + leg_spread, base_y], fill=boot_color, outline=OUTLINE)
        else:  # RIGHT
            # Back leg
            draw.rectangle([body_cx - 1 + leg_spread, body_cy + 5,
                            body_cx + 2 + leg_spread, base_y], fill=_darken(leg_color, 0.85), outline=OUTLINE)
            draw.rectangle([body_cx - 1 + leg_spread, base_y - 3,
                            body_cx + 2 + leg_spread, base_y], fill=boot_color, outline=OUTLINE)
            # Front leg
            draw.rectangle([body_cx + 2 - leg_spread, body_cy + 5,
                            body_cx + 5 - leg_spread, base_y], fill=leg_color, outline=OUTLINE)
            draw.rectangle([body_cx + 2 - leg_spread, base_y - 3,
                            body_cx + 5 - leg_spread, base_y], fill=boot_color, outline=OUTLINE)

    def draw_cape():
        if not has_cape:
            return
        cape_sway = [0, 1, 0, -1][frame]
        cape_inner = _brighten(cape_color, 1.2)
        if direction == DOWN:
            # Cape behind body, visible at sides
            draw.polygon([
                (body_cx - 8, body_cy - 3),
                (body_cx + 8, body_cy - 3),
                (body_cx + 9 + cape_sway, base_y - 1),
                (body_cx - 9 + cape_sway, base_y - 1),
            ], fill=cape_color, outline=OUTLINE)
        elif direction == UP:
            draw.polygon([
                (body_cx - 8, body_cy - 4),
                (body_cx + 8, body_cy - 4),
                (body_cx + 10 + cape_sway, base_y),
                (body_cx - 10 + cape_sway, base_y),
            ], fill=cape_color, outline=OUTLINE)
            # Inner cape highlight
            draw.polygon([
                (body_cx - 5, body_cy - 2),
                (body_cx + 5, body_cy - 2),
                (body_cx + 7 + cape_sway, base_y - 2),
                (body_cx - 7 + cape_sway, base_y - 2),
            ], fill=cape_inner, outline=None)
        elif direction == LEFT:
            draw.polygon([
                (body_cx + 3, body_cy - 5),
                (body_cx + 9, body_cy - 3),
                (body_cx + 10 + cape_sway, base_y),
                (body_cx + 3, base_y),
            ], fill=cape_color, outline=OUTLINE)
        elif direction == RIGHT:
            csway2 = [0, -1, 0, 1][frame]
            draw.polygon([
                (body_cx - 3, body_cy - 5),
                (body_cx - 9, body_cy - 3),
                (body_cx - 10 + csway2, base_y),
                (body_cx - 3, base_y),
            ], fill=cape_color, outline=OUTLINE)

    def draw_body():
        if direction == DOWN:
            ellipse(draw, body_cx, body_cy, 7, 6, body_color)
            # Vest/armor detail — V-line
            draw.line([(body_cx, body_cy - 4), (body_cx - 2, body_cy + 2)],
                      fill=_darken(body_color, 0.8), width=1)
            draw.line([(body_cx, body_cy - 4), (body_cx + 2, body_cy + 2)],
                      fill=_darken(body_color, 0.8), width=1)
            # Highlight on chest
            ellipse(draw, body_cx, body_cy - 1, 4, 3, body_highlight, outline=None)
            # Belt with buckle
            draw.rectangle([body_cx - 7, body_cy + 3, body_cx + 7, body_cy + 5],
                           fill=accent_color, outline=OUTLINE)
            draw.rectangle([body_cx - 1, body_cy + 3, body_cx + 1, body_cy + 5],
                           fill=belt_buckle, outline=None)
        elif direction == UP:
            ellipse(draw, body_cx, body_cy, 7, 6, body_color)
            ellipse(draw, body_cx, body_cy - 1, 5, 4, _darken(body_color))
            # Back seam
            draw.line([(body_cx, body_cy - 3), (body_cx, body_cy + 3)],
                      fill=_darken(body_color, 0.75), width=1)
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
            # Left arm (sleeved)
            draw.rectangle([body_cx - 9, body_cy - 3, body_cx - 6, body_cy + 3],
                           fill=arm_color, outline=OUTLINE)
            # Left hand
            draw.rectangle([body_cx - 9, body_cy + 1, body_cx - 6, body_cy + 3],
                           fill=hand_color, outline=OUTLINE)
            # Right arm (sleeved)
            draw.rectangle([body_cx + 6, body_cy - 3, body_cx + 9, body_cy + 3],
                           fill=arm_color, outline=OUTLINE)
            # Right hand
            draw.rectangle([body_cx + 6, body_cy + 1, body_cx + 9, body_cy + 3],
                           fill=hand_color, outline=OUTLINE)
            # Shoulder pads
            ellipse(draw, body_cx - 7, body_cy - 3, 3, 2, accent_color)
            ellipse(draw, body_cx + 7, body_cy - 3, 3, 2, accent_color)
        elif direction == UP:
            draw.rectangle([body_cx - 9, body_cy - 3, body_cx - 6, body_cy + 3],
                           fill=arm_color, outline=OUTLINE)
            draw.rectangle([body_cx + 6, body_cy - 3, body_cx + 9, body_cy + 3],
                           fill=arm_color, outline=OUTLINE)
            # Shoulder pads (back view)
            ellipse(draw, body_cx - 7, body_cy - 3, 3, 2, accent_color)
            ellipse(draw, body_cx + 7, body_cy - 3, 3, 2, accent_color)
        elif direction == LEFT:
            draw.rectangle([body_cx - 7, body_cy - 2, body_cx - 4, body_cy + 3],
                           fill=arm_color, outline=OUTLINE)
            # Hand
            draw.rectangle([body_cx - 7, body_cy + 1, body_cx - 4, body_cy + 3],
                           fill=hand_color, outline=OUTLINE)
            # Shoulder
            ellipse(draw, body_cx - 5, body_cy - 3, 3, 2, accent_color)
        else:
            draw.rectangle([body_cx + 4, body_cy - 2, body_cx + 7, body_cy + 3],
                           fill=arm_color, outline=OUTLINE)
            # Hand
            draw.rectangle([body_cx + 4, body_cy + 1, body_cx + 7, body_cy + 3],
                           fill=hand_color, outline=OUTLINE)
            # Shoulder
            ellipse(draw, body_cx + 5, body_cy - 3, 3, 2, accent_color)

    def draw_head():
        if direction == DOWN:
            ellipse(draw, body_cx, head_cy, 8, 7, head_color)
            # Head highlight (shine)
            draw.point((body_cx - 3, head_cy - 4), fill=head_highlight)
            draw.point((body_cx - 2, head_cy - 4), fill=head_highlight)
            # Face area
            ellipse(draw, body_cx, head_cy + 2, 5, 4, skin_color)
            # Eyes
            draw.rectangle([body_cx - 3, head_cy + 1, body_cx - 1, head_cy + 3], fill=eye_color)
            draw.rectangle([body_cx + 1, head_cy + 1, body_cx + 3, head_cy + 3], fill=eye_color)
            if has_glow_eyes:
                draw.point((body_cx - 2, head_cy + 2), fill=eye_bright)
                draw.point((body_cx + 2, head_cy + 2), fill=eye_bright)
        elif direction == UP:
            ellipse(draw, body_cx, head_cy, 8, 7, head_color)
            ellipse(draw, body_cx, head_cy, 6, 5, head_dark)
            # Subtle highlight
            draw.point((body_cx - 3, head_cy - 4), fill=head_highlight)
        elif direction == LEFT:
            ellipse(draw, body_cx - 1, head_cy, 7, 7, head_color)
            draw.point((body_cx - 4, head_cy - 4), fill=head_highlight)
            ellipse(draw, body_cx - 3, head_cy + 2, 4, 3, skin_color)
            draw.rectangle([body_cx - 5, head_cy + 1, body_cx - 3, head_cy + 3], fill=eye_color)
            if has_glow_eyes:
                draw.point((body_cx - 4, head_cy + 2), fill=eye_bright)
        else:
            ellipse(draw, body_cx + 1, head_cy, 7, 7, head_color)
            draw.point((body_cx + 4, head_cy - 4), fill=head_highlight)
            ellipse(draw, body_cx + 3, head_cy + 2, 4, 3, skin_color)
            draw.rectangle([body_cx + 3, head_cy + 1, body_cx + 5, head_cy + 3], fill=eye_color)
            if has_glow_eyes:
                draw.point((body_cx + 4, head_cy + 2), fill=eye_bright)

    def draw_hat():
        if hat_style is None:
            return
        hc = hat_color
        if hat_style == 'pointed':
            if direction in (DOWN, UP):
                # Brim
                ellipse(draw, body_cx, head_cy - 4, 8, 2, hc)
                # Cone
                draw.polygon([(body_cx, head_cy - 14), (body_cx - 5, head_cy - 5),
                              (body_cx + 5, head_cy - 5)], fill=hc, outline=OUTLINE)
                # Band
                draw.rectangle([body_cx - 5, head_cy - 6, body_cx + 5, head_cy - 4],
                               fill=_brighten(accent_color, 1.2), outline=None)
                # Tip highlight
                draw.point((body_cx - 1, head_cy - 12), fill=hat_light)
            elif direction == LEFT:
                ellipse(draw, body_cx - 1, head_cy - 4, 7, 2, hc)
                draw.polygon([(body_cx - 3, head_cy - 14), (body_cx - 7, head_cy - 5),
                              (body_cx + 3, head_cy - 5)], fill=hc, outline=OUTLINE)
                draw.rectangle([body_cx - 6, head_cy - 6, body_cx + 3, head_cy - 4],
                               fill=_brighten(accent_color, 1.2), outline=None)
            else:
                ellipse(draw, body_cx + 1, head_cy - 4, 7, 2, hc)
                draw.polygon([(body_cx + 3, head_cy - 14), (body_cx - 3, head_cy - 5),
                              (body_cx + 7, head_cy - 5)], fill=hc, outline=OUTLINE)
                draw.rectangle([body_cx - 3, head_cy - 6, body_cx + 6, head_cy - 4],
                               fill=_brighten(accent_color, 1.2), outline=None)
        elif hat_style == 'helm':
            if direction in (DOWN, UP):
                # Helm dome covers top of head
                draw.rectangle([body_cx - 8, head_cy - 3, body_cx + 8, head_cy + 1],
                               fill=hc, outline=OUTLINE)
                # Crest ridge on top
                draw.rectangle([body_cx - 1, head_cy - 7, body_cx + 1, head_cy - 2],
                               fill=hat_light, outline=OUTLINE)
                # Rivet details
                draw.point((body_cx - 6, head_cy - 1), fill=hat_light)
                draw.point((body_cx + 6, head_cy - 1), fill=hat_light)
            elif direction == LEFT:
                draw.rectangle([body_cx - 8, head_cy - 3, body_cx + 4, head_cy + 1],
                               fill=hc, outline=OUTLINE)
                draw.rectangle([body_cx - 2, head_cy - 7, body_cx, head_cy - 2],
                               fill=hat_light, outline=OUTLINE)
            else:
                draw.rectangle([body_cx - 4, head_cy - 3, body_cx + 8, head_cy + 1],
                               fill=hc, outline=OUTLINE)
                draw.rectangle([body_cx, head_cy - 7, body_cx + 2, head_cy - 2],
                               fill=hat_light, outline=OUTLINE)
        elif hat_style == 'hood':
            if direction == DOWN:
                ellipse(draw, body_cx, head_cy - 2, 9, 6, hc)
                # Hood peak
                draw.polygon([(body_cx - 2, head_cy - 8), (body_cx, head_cy - 11),
                              (body_cx + 2, head_cy - 8)], fill=hc, outline=OUTLINE)
                # Inner shadow
                ellipse(draw, body_cx, head_cy - 1, 7, 4, hat_dark, outline=None)
            elif direction == UP:
                ellipse(draw, body_cx, head_cy - 1, 9, 7, hc)
                draw.polygon([(body_cx - 2, head_cy - 8), (body_cx, head_cy - 11),
                              (body_cx + 2, head_cy - 8)], fill=hc, outline=OUTLINE)
                ellipse(draw, body_cx, head_cy, 7, 5, hat_dark, outline=None)
            elif direction == LEFT:
                ellipse(draw, body_cx - 1, head_cy - 2, 8, 6, hc)
                draw.polygon([(body_cx - 3, head_cy - 8), (body_cx - 1, head_cy - 11),
                              (body_cx + 1, head_cy - 8)], fill=hc, outline=OUTLINE)
            else:
                ellipse(draw, body_cx + 1, head_cy - 2, 8, 6, hc)
                draw.polygon([(body_cx - 1, head_cy - 8), (body_cx + 1, head_cy - 11),
                              (body_cx + 3, head_cy - 8)], fill=hc, outline=OUTLINE)
        elif hat_style == 'crown':
            if direction in (DOWN, UP):
                draw.rectangle([body_cx - 6, head_cy - 10, body_cx + 6, head_cy - 6],
                               fill=hc, outline=OUTLINE)
                # Crown points
                for px in range(-4, 6, 4):
                    draw.rectangle([body_cx + px - 1, head_cy - 12, body_cx + px + 1, head_cy - 10],
                                   fill=hc, outline=OUTLINE)
                # Gem on front
                draw.point((body_cx, head_cy - 8), fill=_brighten(accent_color, 1.5))
                draw.point((body_cx - 3, head_cy - 8), fill=hat_light)
                draw.point((body_cx + 3, head_cy - 8), fill=hat_light)
            elif direction == LEFT:
                draw.rectangle([body_cx - 6, head_cy - 10, body_cx + 3, head_cy - 6],
                               fill=hc, outline=OUTLINE)
                draw.rectangle([body_cx - 5, head_cy - 12, body_cx - 3, head_cy - 10],
                               fill=hc, outline=OUTLINE)
                draw.rectangle([body_cx + 1, head_cy - 12, body_cx + 3, head_cy - 10],
                               fill=hc, outline=OUTLINE)
                draw.point((body_cx - 2, head_cy - 8), fill=_brighten(accent_color, 1.5))
            else:
                draw.rectangle([body_cx - 3, head_cy - 10, body_cx + 6, head_cy - 6],
                               fill=hc, outline=OUTLINE)
                draw.rectangle([body_cx - 3, head_cy - 12, body_cx - 1, head_cy - 10],
                               fill=hc, outline=OUTLINE)
                draw.rectangle([body_cx + 3, head_cy - 12, body_cx + 5, head_cy - 10],
                               fill=hc, outline=OUTLINE)
                draw.point((body_cx + 2, head_cy - 8), fill=_brighten(accent_color, 1.5))
        elif hat_style == 'horns':
            if direction in (DOWN, UP):
                # Curved horns (wider base, tapered tip)
                draw.polygon([(body_cx - 6, head_cy - 4), (body_cx - 9, head_cy - 12),
                              (body_cx - 7, head_cy - 11), (body_cx - 5, head_cy - 5)],
                             fill=hc, outline=OUTLINE)
                draw.polygon([(body_cx + 6, head_cy - 4), (body_cx + 9, head_cy - 12),
                              (body_cx + 7, head_cy - 11), (body_cx + 5, head_cy - 5)],
                             fill=hc, outline=OUTLINE)
                # Horn tips
                draw.point((body_cx - 9, head_cy - 12), fill=hat_light)
                draw.point((body_cx + 9, head_cy - 12), fill=hat_light)
            elif direction == LEFT:
                draw.polygon([(body_cx - 5, head_cy - 4), (body_cx - 8, head_cy - 12),
                              (body_cx - 6, head_cy - 11), (body_cx - 4, head_cy - 5)],
                             fill=hc, outline=OUTLINE)
                draw.point((body_cx - 8, head_cy - 12), fill=hat_light)
            else:
                draw.polygon([(body_cx + 5, head_cy - 4), (body_cx + 8, head_cy - 12),
                              (body_cx + 6, head_cy - 11), (body_cx + 4, head_cy - 5)],
                             fill=hc, outline=OUTLINE)
                draw.point((body_cx + 8, head_cy - 12), fill=hat_light)
        elif hat_style == 'ears':
            if direction in (DOWN, UP):
                # Rounded triangular ears
                draw.polygon([(body_cx - 6, head_cy - 4), (body_cx - 8, head_cy - 12),
                              (body_cx - 3, head_cy - 6)], fill=hc, outline=OUTLINE)
                draw.polygon([(body_cx + 6, head_cy - 4), (body_cx + 8, head_cy - 12),
                              (body_cx + 3, head_cy - 6)], fill=hc, outline=OUTLINE)
                # Inner ear pink/highlight
                draw.polygon([(body_cx - 5, head_cy - 5), (body_cx - 7, head_cy - 10),
                              (body_cx - 4, head_cy - 7)], fill=_brighten(hc, 1.3), outline=None)
                draw.polygon([(body_cx + 5, head_cy - 5), (body_cx + 7, head_cy - 10),
                              (body_cx + 4, head_cy - 7)], fill=_brighten(hc, 1.3), outline=None)
            elif direction == LEFT:
                draw.polygon([(body_cx - 4, head_cy - 4), (body_cx - 6, head_cy - 12),
                              (body_cx - 1, head_cy - 6)], fill=hc, outline=OUTLINE)
                draw.polygon([(body_cx - 3, head_cy - 5), (body_cx - 5, head_cy - 10),
                              (body_cx - 2, head_cy - 7)], fill=_brighten(hc, 1.3), outline=None)
            else:
                draw.polygon([(body_cx + 4, head_cy - 4), (body_cx + 6, head_cy - 12),
                              (body_cx + 1, head_cy - 6)], fill=hc, outline=OUTLINE)
                draw.polygon([(body_cx + 3, head_cy - 5), (body_cx + 5, head_cy - 10),
                              (body_cx + 2, head_cy - 7)], fill=_brighten(hc, 1.3), outline=None)
        elif hat_style == 'crest':
            if direction in (DOWN, UP):
                # Wider, more dramatic crest with taper
                draw.polygon([(body_cx - 2, head_cy - 5), (body_cx - 1, head_cy - 13),
                              (body_cx + 1, head_cy - 13), (body_cx + 2, head_cy - 5)],
                             fill=hc, outline=OUTLINE)
                draw.point((body_cx, head_cy - 12), fill=hat_light)
            elif direction == LEFT:
                draw.polygon([(body_cx - 2, head_cy - 5), (body_cx - 2, head_cy - 13),
                              (body_cx, head_cy - 13), (body_cx, head_cy - 5)],
                             fill=hc, outline=OUTLINE)
            else:
                draw.polygon([(body_cx, head_cy - 5), (body_cx, head_cy - 13),
                              (body_cx + 2, head_cy - 13), (body_cx + 2, head_cy - 5)],
                             fill=hc, outline=OUTLINE)
        elif hat_style == 'halo':
            if direction in (DOWN, UP):
                # Filled golden halo ring
                draw.ellipse([body_cx - 8, head_cy - 13, body_cx + 8, head_cy - 8],
                             fill=None, outline=hc, width=2)
                # Glow pixels
                draw.point((body_cx - 6, head_cy - 11), fill=hat_light)
                draw.point((body_cx + 6, head_cy - 11), fill=hat_light)
            elif direction == LEFT:
                draw.ellipse([body_cx - 8, head_cy - 13, body_cx + 4, head_cy - 8],
                             fill=None, outline=hc, width=2)
            else:
                draw.ellipse([body_cx - 4, head_cy - 13, body_cx + 8, head_cy - 8],
                             fill=None, outline=hc, width=2)
        elif hat_style == 'antenna':
            if direction in (DOWN, UP):
                draw.line([(body_cx, head_cy - 7), (body_cx, head_cy - 13)], fill=hc, width=1)
                ellipse(draw, body_cx, head_cy - 14, 2, 2, hc)
                # Antenna glow
                draw.point((body_cx - 1, head_cy - 14), fill=_brighten(hc, 1.5))
                draw.point((body_cx + 1, head_cy - 14), fill=_brighten(hc, 1.5))
            elif direction == LEFT:
                draw.line([(body_cx - 1, head_cy - 7), (body_cx - 1, head_cy - 13)], fill=hc, width=1)
                ellipse(draw, body_cx - 1, head_cy - 14, 2, 2, hc)
                draw.point((body_cx - 2, head_cy - 14), fill=_brighten(hc, 1.5))
            else:
                draw.line([(body_cx + 1, head_cy - 7), (body_cx + 1, head_cy - 13)], fill=hc, width=1)
                ellipse(draw, body_cx + 1, head_cy - 14, 2, 2, hc)
                draw.point((body_cx + 2, head_cy - 14), fill=_brighten(hc, 1.5))

        elif hat_style == 'bandana':
            # Pirate-style bandana/headscarf — tied at back
            if direction in (DOWN, UP):
                draw.rectangle([body_cx - 8, head_cy - 4, body_cx + 8, head_cy - 1],
                               fill=hc, outline=OUTLINE)
                # Knot tails at back
                if direction == UP:
                    draw.line([(body_cx + 3, head_cy - 1), (body_cx + 5, head_cy + 3)],
                              fill=hc, width=2)
                    draw.line([(body_cx + 5, head_cy - 1), (body_cx + 7, head_cy + 2)],
                              fill=hat_dark, width=1)
                # Highlight stripe
                draw.line([(body_cx - 6, head_cy - 3), (body_cx + 6, head_cy - 3)],
                          fill=hat_light, width=1)
            elif direction == LEFT:
                draw.rectangle([body_cx - 8, head_cy - 4, body_cx + 4, head_cy - 1],
                               fill=hc, outline=OUTLINE)
                # Tails trailing right
                draw.line([(body_cx + 3, head_cy - 2), (body_cx + 7, head_cy + 1)],
                          fill=hc, width=2)
            else:
                draw.rectangle([body_cx - 4, head_cy - 4, body_cx + 8, head_cy - 1],
                               fill=hc, outline=OUTLINE)
                # Tails trailing left
                draw.line([(body_cx - 3, head_cy - 2), (body_cx - 7, head_cy + 1)],
                          fill=hc, width=2)
        elif hat_style == 'tophat':
            # Tall top hat — gentleman/gambler style
            if direction in (DOWN, UP):
                # Brim
                draw.rectangle([body_cx - 8, head_cy - 5, body_cx + 8, head_cy - 3],
                               fill=hc, outline=OUTLINE)
                # Tall crown
                draw.rectangle([body_cx - 5, head_cy - 14, body_cx + 5, head_cy - 5],
                               fill=hc, outline=OUTLINE)
                # Band
                draw.rectangle([body_cx - 5, head_cy - 7, body_cx + 5, head_cy - 5],
                               fill=_brighten(accent_color, 1.2), outline=None)
                # Shine
                draw.point((body_cx - 3, head_cy - 12), fill=hat_light)
            elif direction == LEFT:
                draw.rectangle([body_cx - 8, head_cy - 5, body_cx + 4, head_cy - 3],
                               fill=hc, outline=OUTLINE)
                draw.rectangle([body_cx - 5, head_cy - 14, body_cx + 2, head_cy - 5],
                               fill=hc, outline=OUTLINE)
                draw.rectangle([body_cx - 5, head_cy - 7, body_cx + 2, head_cy - 5],
                               fill=_brighten(accent_color, 1.2), outline=None)
            else:
                draw.rectangle([body_cx - 4, head_cy - 5, body_cx + 8, head_cy - 3],
                               fill=hc, outline=OUTLINE)
                draw.rectangle([body_cx - 2, head_cy - 14, body_cx + 5, head_cy - 5],
                               fill=hc, outline=OUTLINE)
                draw.rectangle([body_cx - 2, head_cy - 7, body_cx + 5, head_cy - 5],
                               fill=_brighten(accent_color, 1.2), outline=None)
        elif hat_style == 'toque':
            # Chef's toque — tall white puffy hat
            if direction in (DOWN, UP):
                # Puffy top
                ellipse(draw, body_cx, head_cy - 10, 6, 5, hc)
                # Middle
                draw.rectangle([body_cx - 5, head_cy - 8, body_cx + 5, head_cy - 4],
                               fill=hc, outline=OUTLINE)
                # Band at base
                draw.rectangle([body_cx - 6, head_cy - 5, body_cx + 6, head_cy - 3],
                               fill=hat_dark, outline=OUTLINE)
                # Puff highlight
                draw.point((body_cx - 2, head_cy - 12), fill=hat_light)
                draw.point((body_cx + 1, head_cy - 11), fill=hat_light)
            elif direction == LEFT:
                ellipse(draw, body_cx - 1, head_cy - 10, 5, 5, hc)
                draw.rectangle([body_cx - 5, head_cy - 8, body_cx + 3, head_cy - 4],
                               fill=hc, outline=OUTLINE)
                draw.rectangle([body_cx - 6, head_cy - 5, body_cx + 4, head_cy - 3],
                               fill=hat_dark, outline=OUTLINE)
            else:
                ellipse(draw, body_cx + 1, head_cy - 10, 5, 5, hc)
                draw.rectangle([body_cx - 3, head_cy - 8, body_cx + 5, head_cy - 4],
                               fill=hc, outline=OUTLINE)
                draw.rectangle([body_cx - 4, head_cy - 5, body_cx + 6, head_cy - 3],
                               fill=hat_dark, outline=OUTLINE)
        elif hat_style == 'feathered':
            # Bard/musketeer feathered cap — floppy beret with a plume
            if direction in (DOWN, UP):
                # Floppy beret
                ellipse(draw, body_cx, head_cy - 4, 8, 3, hc)
                ellipse(draw, body_cx + 2, head_cy - 5, 5, 3, hat_light, outline=None)
                # Feather plume
                draw.line([(body_cx + 5, head_cy - 5), (body_cx + 8, head_cy - 12)],
                          fill=_brighten(accent_color, 1.3), width=2)
                draw.point((body_cx + 8, head_cy - 12), fill=_brighten(accent_color, 1.6))
            elif direction == LEFT:
                ellipse(draw, body_cx - 1, head_cy - 4, 7, 3, hc)
                # Feather trails behind
                draw.line([(body_cx + 3, head_cy - 5), (body_cx + 7, head_cy - 11)],
                          fill=_brighten(accent_color, 1.3), width=2)
            else:
                ellipse(draw, body_cx + 1, head_cy - 4, 7, 3, hc)
                draw.line([(body_cx - 3, head_cy - 5), (body_cx - 7, head_cy - 11)],
                          fill=_brighten(accent_color, 1.3), width=2)
        elif hat_style == 'fin':
            # Dorsal fin — for shark/fish characters
            if direction in (DOWN, UP):
                draw.polygon([(body_cx - 1, head_cy - 4), (body_cx, head_cy - 13),
                              (body_cx + 3, head_cy - 6), (body_cx + 1, head_cy - 4)],
                             fill=hc, outline=OUTLINE)
                draw.point((body_cx, head_cy - 11), fill=hat_light)
            elif direction == LEFT:
                draw.polygon([(body_cx - 1, head_cy - 4), (body_cx - 1, head_cy - 13),
                              (body_cx + 3, head_cy - 6), (body_cx + 1, head_cy - 4)],
                             fill=hc, outline=OUTLINE)
            else:
                draw.polygon([(body_cx - 1, head_cy - 4), (body_cx + 1, head_cy - 13),
                              (body_cx - 3, head_cy - 6), (body_cx + 1, head_cy - 4)],
                             fill=hc, outline=OUTLINE)

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

    Each frame is drawn on its own 32x32 canvas to prevent pixel bleeding
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
