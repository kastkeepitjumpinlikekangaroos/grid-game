#!/usr/bin/env python3
"""Generate sprites/warden.png — 4-column x 4-row character spritesheet.

256x256 PNG, 64x64 per frame.
Row layout: Down=0, Up=1, Left=2, Right=3
4 walking animation frames per direction.

Style matches Spaceman/Gladiator: big round head, round body, small limbs, dark outlines.
Theme: Prison warden — dark iron armor, chain details, heavy key ring, iron mask/visor.
Enhanced 64x64: heavy mace weapon, intense visor glow, chain gauntlets, rivet details,
          heavier boots, bigger key ring, stomp animation.
          Shield with detailed emblem, chain mail texture (dot grid), nature-themed
          green accents with leaf detail.
"""

from PIL import Image, ImageDraw

FRAME_SIZE = 64
COLS = 4
ROWS = 4
IMG_W = FRAME_SIZE * COLS   # 256
IMG_H = FRAME_SIZE * ROWS   # 256

# Colors
OUTLINE = (40, 35, 35)
IRON = (70, 70, 80)
IRON_LIGHT = (100, 100, 115)
IRON_BRIGHT = (140, 140, 155)       # Rivet highlights
IRON_DARK = (45, 45, 55)
CHAIN = (120, 120, 130)
CHAIN_DARK = (80, 80, 90)
CHAIN_BRIGHT = (150, 150, 165)      # Bright chain links
VISOR_SLIT = (200, 220, 255)        # Brighter icy blue glow
VISOR_GLOW = (160, 200, 255, 200)   # Glow bleed around visor
VISOR_CORE = (230, 240, 255)        # White-hot center of visor
VISOR_DIM = (100, 130, 180)
ARMOR_ACCENT = (55, 60, 70)
KEY_GOLD = (200, 170, 60)
KEY_BRIGHT = (230, 200, 80)         # Key highlight
KEY_DARK = (150, 125, 40)
BELT = (50, 45, 40)
BOOT = (40, 38, 35)
BOOT_CAP = (110, 110, 120)          # Metal toe cap
BLACK = (25, 25, 30)
FROST_BLUE = (140, 190, 255)
MACE_HANDLE = (90, 65, 35)          # Brown wood handle
MACE_HEAD = (55, 55, 65)            # Dark iron mace ball
MACE_SPIKE = (180, 180, 195)        # Bright spike tips
# New detail colors
SHIELD_FACE = (60, 60, 70)
SHIELD_RIM = (90, 90, 105)
SHIELD_EMBLEM = (180, 160, 55)
CHAINMAIL_DOT = (100, 100, 110)
GREEN_ACCENT = (50, 100, 45)
GREEN_LIGHT = (75, 135, 65)
LEAF = (60, 120, 50)
LEAF_VEIN = (40, 85, 35)

DOWN, UP, LEFT, RIGHT = 0, 1, 2, 3


def ellipse(draw, cx, cy, rx, ry, fill, outline=OUTLINE):
    draw.ellipse([cx - rx, cy - ry, cx + rx, cy + ry], fill=fill, outline=outline)


def draw_chainmail_texture(draw, x1, y1, x2, y2):
    """Draw a chain mail dot grid pattern on the body."""
    for y in range(y1 + 2, y2, 4):
        offset = 2 if ((y - y1) // 4) % 2 == 0 else 0
        for x in range(x1 + 2 + offset, x2, 4):
            draw.point((x, y), fill=CHAINMAIL_DOT)


def draw_shield_with_emblem(draw, cx, cy, direction):
    """Draw a shield with a detailed emblem (cross or diamond)."""
    if direction == DOWN:
        # Shield on left arm — round shield
        ellipse(draw, cx, cy, 10, 10, SHIELD_FACE, outline=OUTLINE)
        # Shield rim
        draw.arc([cx - 10, cy - 10, cx + 10, cy + 10], 0, 360, fill=SHIELD_RIM, width=2)
        # Cross emblem
        draw.line([(cx, cy - 6), (cx, cy + 6)], fill=SHIELD_EMBLEM, width=2)
        draw.line([(cx - 6, cy), (cx + 6, cy)], fill=SHIELD_EMBLEM, width=2)
        # Center rivet
        draw.rectangle([cx - 1, cy - 1, cx + 1, cy + 1], fill=IRON_BRIGHT)
    elif direction == LEFT:
        # Shield visible on right side (back arm)
        ellipse(draw, cx, cy, 8, 10, SHIELD_FACE, outline=OUTLINE)
        draw.arc([cx - 8, cy - 10, cx + 8, cy + 10], 0, 360, fill=SHIELD_RIM, width=2)
        draw.line([(cx, cy - 6), (cx, cy + 6)], fill=SHIELD_EMBLEM, width=2)
        draw.line([(cx - 5, cy), (cx + 5, cy)], fill=SHIELD_EMBLEM, width=2)
        draw.rectangle([cx - 1, cy - 1, cx + 1, cy + 1], fill=IRON_BRIGHT)
    elif direction == RIGHT:
        ellipse(draw, cx, cy, 8, 10, SHIELD_FACE, outline=OUTLINE)
        draw.arc([cx - 8, cy - 10, cx + 8, cy + 10], 0, 360, fill=SHIELD_RIM, width=2)
        draw.line([(cx, cy - 6), (cx, cy + 6)], fill=SHIELD_EMBLEM, width=2)
        draw.line([(cx - 5, cy), (cx + 5, cy)], fill=SHIELD_EMBLEM, width=2)
        draw.rectangle([cx - 1, cy - 1, cx + 1, cy + 1], fill=IRON_BRIGHT)


def draw_leaf_detail(draw, cx, cy):
    """Draw a small nature-themed leaf accent."""
    # Leaf shape — small pointed oval
    draw.polygon([
        (cx, cy - 4),
        (cx + 3, cy),
        (cx, cy + 4),
        (cx - 3, cy),
    ], fill=LEAF, outline=OUTLINE)
    # Leaf vein
    draw.line([(cx, cy - 3), (cx, cy + 3)], fill=LEAF_VEIN, width=1)


def draw_warden(draw, ox, oy, direction, frame):
    # Heavier stomp bob pattern: [0, -2, 0, -4]
    bob = [0, -2, 0, -4][frame]
    leg_spread = [-4, 0, 4, 0][frame]

    base_y = oy + 54 + bob
    body_cx = ox + 32
    body_cy = base_y - 20
    head_cy = body_cy - 20

    if direction == DOWN:
        # ---- Legs — heavy iron boots (wider/blockier) ----
        draw.rectangle([body_cx - 10 + leg_spread, body_cy + 10,
                        body_cx - 4 + leg_spread, base_y], fill=IRON_DARK, outline=OUTLINE)
        draw.rectangle([body_cx + 4 - leg_spread, body_cy + 10,
                        body_cx + 10 - leg_spread, base_y], fill=IRON_DARK, outline=OUTLINE)
        # Boots — wider with metal toe cap
        draw.rectangle([body_cx - 14 + leg_spread, base_y - 8,
                        body_cx - 2 + leg_spread, base_y], fill=BOOT, outline=OUTLINE)
        draw.rectangle([body_cx + 2 - leg_spread, base_y - 8,
                        body_cx + 14 - leg_spread, base_y], fill=BOOT, outline=OUTLINE)
        # Metal toe caps
        draw.rectangle([body_cx - 13 + leg_spread, base_y - 3,
                        body_cx - 9 + leg_spread, base_y - 1], fill=BOOT_CAP)
        draw.rectangle([body_cx + 9 - leg_spread, base_y - 3,
                        body_cx + 13 - leg_spread, base_y - 1], fill=BOOT_CAP)

        # ---- Body — dark iron armor ----
        ellipse(draw, body_cx, body_cy, 14, 12, IRON)
        # Chain mail texture on body
        draw_chainmail_texture(draw, body_cx - 10, body_cy - 8, body_cx + 10, body_cy + 4)
        # Armor chest plate with center seam
        draw.rectangle([body_cx - 6, body_cy - 8, body_cx + 6, body_cy + 2],
                       fill=IRON_LIGHT, outline=OUTLINE)
        # Center plate seam line
        draw.line([(body_cx, body_cy - 8), (body_cx, body_cy + 2)], fill=ARMOR_ACCENT)
        # Rivet dots at chest plate corners
        draw.rectangle([body_cx - 7, body_cy - 9, body_cx - 5, body_cy - 7], fill=IRON_BRIGHT)
        draw.rectangle([body_cx + 5, body_cy - 9, body_cx + 7, body_cy - 7], fill=IRON_BRIGHT)
        draw.rectangle([body_cx - 7, body_cy + 1, body_cx - 5, body_cy + 3], fill=IRON_BRIGHT)
        draw.rectangle([body_cx + 5, body_cy + 1, body_cx + 7, body_cy + 3], fill=IRON_BRIGHT)

        # Nature-themed green accent — leaf on chest plate
        draw_leaf_detail(draw, body_cx, body_cy - 3)

        # Chain belt
        draw.rectangle([body_cx - 14, body_cy + 6, body_cx + 14, body_cy + 10],
                       fill=CHAIN_DARK, outline=OUTLINE)
        # Chain link details on belt
        for i in range(-10, 12, 6):
            draw.rectangle([body_cx + i, body_cy + 6, body_cx + i + 2, body_cy + 10],
                           fill=CHAIN)

        # Green accent on belt
        draw.rectangle([body_cx - 2, body_cy + 7, body_cx + 2, body_cy + 9], fill=GREEN_ACCENT)

        # Key ring hanging from belt — bigger with key-tooth shape
        draw.ellipse([body_cx + 6, body_cy + 8, body_cx + 16, body_cy + 16],
                     fill=KEY_GOLD, outline=OUTLINE)
        draw.rectangle([body_cx + 9, body_cy + 10, body_cx + 11, body_cy + 12], fill=KEY_BRIGHT)
        # Key shaft
        draw.rectangle([body_cx + 10, body_cy + 14, body_cx + 14, body_cy + 20],
                       fill=KEY_DARK, outline=OUTLINE)
        # Key teeth (distinct tooth shape)
        draw.rectangle([body_cx + 14, body_cy + 17, body_cx + 16, body_cy + 19], fill=KEY_GOLD)
        draw.rectangle([body_cx + 14, body_cy + 19, body_cx + 17, body_cy + 20], fill=KEY_DARK)

        # ---- Arms — armored gauntlets with chains ----
        # Left arm
        draw.rectangle([body_cx - 18, body_cy - 6, body_cx - 12, body_cy + 6],
                       fill=IRON, outline=OUTLINE)
        draw.rectangle([body_cx - 18, body_cy + 2, body_cx - 12, body_cy + 6],
                       fill=IRON_DARK, outline=OUTLINE)
        # Left gauntlet chain links (alternating bright/dark)
        draw.rectangle([body_cx - 19, body_cy + 7, body_cx - 17, body_cy + 9], fill=CHAIN_BRIGHT)
        draw.rectangle([body_cx - 17, body_cy + 9, body_cx - 15, body_cy + 11], fill=CHAIN_DARK)
        draw.rectangle([body_cx - 15, body_cy + 7, body_cx - 13, body_cy + 9], fill=CHAIN_BRIGHT)

        # Shield on left arm
        draw_shield_with_emblem(draw, body_cx - 18, body_cy - 2, DOWN)

        # Right arm
        draw.rectangle([body_cx + 12, body_cy - 6, body_cx + 18, body_cy + 6],
                       fill=IRON, outline=OUTLINE)
        draw.rectangle([body_cx + 12, body_cy + 2, body_cx + 18, body_cy + 6],
                       fill=IRON_DARK, outline=OUTLINE)
        # Right gauntlet chain links
        draw.rectangle([body_cx + 13, body_cy + 7, body_cx + 15, body_cy + 9], fill=CHAIN_BRIGHT)
        draw.rectangle([body_cx + 15, body_cy + 9, body_cx + 17, body_cy + 11], fill=CHAIN_DARK)
        draw.rectangle([body_cx + 17, body_cy + 7, body_cx + 19, body_cy + 9], fill=CHAIN_BRIGHT)

        # ---- Heavy Mace (right hand, extending down) ----
        # Handle (brown, 4px tall from right hand)
        draw.rectangle([body_cx + 18, body_cy + 4, body_cx + 20, body_cy + 8],
                       fill=MACE_HANDLE, outline=OUTLINE)
        # Mace head — dark iron ball
        draw.ellipse([body_cx + 16, body_cy + 8, body_cx + 24, body_cy + 16],
                     fill=MACE_HEAD, outline=OUTLINE)
        # Spikes on mace head (bright metallic)
        draw.rectangle([body_cx + 24, body_cy + 9, body_cx + 26, body_cy + 11], fill=MACE_SPIKE)
        draw.rectangle([body_cx + 19, body_cy + 7, body_cx + 21, body_cy + 9], fill=MACE_SPIKE)
        draw.rectangle([body_cx + 15, body_cy + 11, body_cx + 17, body_cy + 13], fill=MACE_SPIKE)
        draw.rectangle([body_cx + 19, body_cy + 15, body_cx + 21, body_cy + 17], fill=MACE_SPIKE)

        # ---- Head — iron helm with intense visor glow ----
        ellipse(draw, body_cx, head_cy, 16, 14, IRON)
        # Chain mail texture on helm sides
        draw_chainmail_texture(draw, body_cx - 12, head_cy - 8, body_cx - 4, head_cy + 2)
        draw_chainmail_texture(draw, body_cx + 4, head_cy - 8, body_cx + 12, head_cy + 2)
        # Visor plate
        draw.rectangle([body_cx - 12, head_cy, body_cx + 12, head_cy + 8],
                       fill=IRON_DARK, outline=OUTLINE)
        # Visor slit — brighter glowing icy blue
        draw.rectangle([body_cx - 8, head_cy + 2, body_cx + 8, head_cy + 6],
                       fill=VISOR_SLIT)
        # White-hot center core of visor
        draw.rectangle([body_cx - 4, head_cy + 4, body_cx + 4, head_cy + 4],
                       fill=VISOR_CORE)
        # Glow leak pixels above and below the slit
        draw.rectangle([body_cx - 5, head_cy - 1, body_cx - 3, head_cy + 1], fill=FROST_BLUE)
        draw.rectangle([body_cx + 3, head_cy - 1, body_cx + 5, head_cy + 1], fill=FROST_BLUE)
        draw.rectangle([body_cx - 3, head_cy + 7, body_cx - 1, head_cy + 9], fill=FROST_BLUE)
        draw.rectangle([body_cx + 1, head_cy + 7, body_cx + 3, head_cy + 9], fill=FROST_BLUE)
        # Glow pixel right at slit edges
        draw.rectangle([body_cx - 10, head_cy + 3, body_cx - 8, head_cy + 5], fill=VISOR_DIM)
        draw.rectangle([body_cx + 8, head_cy + 3, body_cx + 10, head_cy + 5], fill=VISOR_DIM)

        # Helm crest
        draw.rectangle([body_cx - 2, head_cy - 14, body_cx + 2, head_cy - 4],
                       fill=IRON_LIGHT, outline=OUTLINE)
        # Green accent on crest
        draw.rectangle([body_cx - 1, head_cy - 12, body_cx + 1, head_cy - 6], fill=GREEN_LIGHT)
        # Rivet details on helm
        draw.rectangle([body_cx - 13, head_cy - 5, body_cx - 11, head_cy - 3], fill=IRON_BRIGHT)
        draw.rectangle([body_cx + 11, head_cy - 5, body_cx + 13, head_cy - 3], fill=IRON_BRIGHT)

    elif direction == UP:
        # ---- Legs — heavier boots ----
        draw.rectangle([body_cx - 10 + leg_spread, body_cy + 10,
                        body_cx - 4 + leg_spread, base_y], fill=IRON_DARK, outline=OUTLINE)
        draw.rectangle([body_cx + 4 - leg_spread, body_cy + 10,
                        body_cx + 10 - leg_spread, base_y], fill=IRON_DARK, outline=OUTLINE)
        # Wider boots
        draw.rectangle([body_cx - 14 + leg_spread, base_y - 8,
                        body_cx - 2 + leg_spread, base_y], fill=BOOT, outline=OUTLINE)
        draw.rectangle([body_cx + 2 - leg_spread, base_y - 8,
                        body_cx + 14 - leg_spread, base_y], fill=BOOT, outline=OUTLINE)
        # Metal toe caps (visible even from back, at edges)
        draw.rectangle([body_cx - 13 + leg_spread, base_y - 3,
                        body_cx - 9 + leg_spread, base_y - 1], fill=BOOT_CAP)
        draw.rectangle([body_cx + 9 - leg_spread, base_y - 3,
                        body_cx + 13 - leg_spread, base_y - 1], fill=BOOT_CAP)

        # Chain cape on back
        chain_sway = [0, 2, 0, -2][frame]
        for i in range(3):
            cy_off = body_cy - 4 + i * 6
            draw.rectangle([body_cx - 8 + chain_sway, cy_off,
                            body_cx + 8 + chain_sway, cy_off + 4],
                           fill=CHAIN_DARK, outline=None)
            for j in range(-6, 8, 4):
                draw.rectangle([body_cx + j + chain_sway, cy_off + 1,
                                body_cx + j + 1 + chain_sway, cy_off + 3], fill=CHAIN)

        # ---- Body ----
        ellipse(draw, body_cx, body_cy, 14, 12, IRON)
        # Back armor plate
        ellipse(draw, body_cx, body_cy - 2, 10, 8, IRON_DARK)
        # Chain mail on back
        draw_chainmail_texture(draw, body_cx - 8, body_cy - 6, body_cx + 8, body_cy + 4)
        # Rivet dots on back plate
        draw.rectangle([body_cx - 9, body_cy - 7, body_cx - 7, body_cy - 5], fill=IRON_BRIGHT)
        draw.rectangle([body_cx + 7, body_cy - 7, body_cx + 9, body_cy - 5], fill=IRON_BRIGHT)
        draw.rectangle([body_cx - 9, body_cy + 1, body_cx - 7, body_cy + 3], fill=IRON_BRIGHT)
        draw.rectangle([body_cx + 7, body_cy + 1, body_cx + 9, body_cy + 3], fill=IRON_BRIGHT)

        # ---- Arms with chain gauntlets ----
        draw.rectangle([body_cx - 18, body_cy - 6, body_cx - 12, body_cy + 6],
                       fill=IRON, outline=OUTLINE)
        draw.rectangle([body_cx + 12, body_cy - 6, body_cx + 18, body_cy + 6],
                       fill=IRON, outline=OUTLINE)
        # Gauntlet chain links
        draw.rectangle([body_cx - 19, body_cy + 7, body_cx - 17, body_cy + 9], fill=CHAIN_BRIGHT)
        draw.rectangle([body_cx - 17, body_cy + 9, body_cx - 15, body_cy + 11], fill=CHAIN_DARK)
        draw.rectangle([body_cx + 15, body_cy + 7, body_cx + 17, body_cy + 9], fill=CHAIN_BRIGHT)
        draw.rectangle([body_cx + 17, body_cy + 9, body_cx + 19, body_cy + 11], fill=CHAIN_DARK)

        # ---- Mace (visible on right side from behind) ----
        draw.rectangle([body_cx + 18, body_cy + 4, body_cx + 20, body_cy + 8],
                       fill=MACE_HANDLE, outline=OUTLINE)
        draw.ellipse([body_cx + 16, body_cy + 8, body_cx + 24, body_cy + 16],
                     fill=MACE_HEAD, outline=OUTLINE)
        draw.rectangle([body_cx + 24, body_cy + 9, body_cx + 26, body_cy + 11], fill=MACE_SPIKE)
        draw.rectangle([body_cx + 19, body_cy + 15, body_cx + 21, body_cy + 17], fill=MACE_SPIKE)

        # ---- Head — back of helm ----
        ellipse(draw, body_cx, head_cy, 16, 14, IRON)
        ellipse(draw, body_cx, head_cy, 12, 10, IRON_DARK)
        # Chain mail on back of helm
        draw_chainmail_texture(draw, body_cx - 8, head_cy - 6, body_cx + 8, head_cy + 4)
        # Crest
        draw.rectangle([body_cx - 2, head_cy - 14, body_cx + 2, head_cy - 4],
                       fill=IRON_LIGHT, outline=OUTLINE)
        draw.rectangle([body_cx - 1, head_cy - 12, body_cx + 1, head_cy - 6], fill=GREEN_LIGHT)
        # Rivet dots on back of helm
        draw.rectangle([body_cx - 11, head_cy - 1, body_cx - 9, head_cy + 1], fill=IRON_BRIGHT)
        draw.rectangle([body_cx + 9, head_cy - 1, body_cx + 11, head_cy + 1], fill=IRON_BRIGHT)

    elif direction == LEFT:
        # ---- Legs (side view) — heavier boots ----
        draw.rectangle([body_cx - 2 - leg_spread, body_cy + 10,
                        body_cx + 4 - leg_spread, base_y], fill=IRON_DARK, outline=OUTLINE)
        draw.rectangle([body_cx - 2 - leg_spread, base_y - 8,
                        body_cx + 6 - leg_spread, base_y], fill=BOOT, outline=OUTLINE)
        # Metal toe cap on front boot
        draw.rectangle([body_cx - 3 - leg_spread, base_y - 3,
                        body_cx - 1 - leg_spread, base_y - 1], fill=BOOT_CAP)

        draw.rectangle([body_cx - 8 + leg_spread, body_cy + 10,
                        body_cx - 2 + leg_spread, base_y], fill=IRON_DARK, outline=OUTLINE)
        draw.rectangle([body_cx - 10 + leg_spread, base_y - 8,
                        body_cx + 0 + leg_spread, base_y], fill=BOOT, outline=OUTLINE)
        # Toe cap
        draw.rectangle([body_cx - 11 + leg_spread, base_y - 3,
                        body_cx - 9 + leg_spread, base_y - 1], fill=BOOT_CAP)

        # ---- Body ----
        ellipse(draw, body_cx - 2, body_cy, 12, 12, IRON)
        ellipse(draw, body_cx - 2, body_cy - 2, 8, 8, IRON_LIGHT)
        # Chain mail texture on side body
        draw_chainmail_texture(draw, body_cx - 8, body_cy - 6, body_cx + 4, body_cy + 4)
        # Center plate seam
        draw.line([(body_cx - 2, body_cy - 8), (body_cx - 2, body_cy + 2)], fill=ARMOR_ACCENT)
        # Rivet dots
        draw.rectangle([body_cx - 9, body_cy - 7, body_cx - 7, body_cy - 5], fill=IRON_BRIGHT)
        draw.rectangle([body_cx + 3, body_cy - 7, body_cx + 5, body_cy - 5], fill=IRON_BRIGHT)

        # Nature leaf detail on body
        draw_leaf_detail(draw, body_cx - 2, body_cy - 2)

        # Belt
        draw.rectangle([body_cx - 14, body_cy + 6, body_cx + 10, body_cy + 10],
                       fill=CHAIN_DARK, outline=OUTLINE)
        # Key ring — bigger with tooth detail
        draw.ellipse([body_cx - 18, body_cy + 6, body_cx - 8, body_cy + 16],
                     fill=KEY_GOLD, outline=OUTLINE)
        draw.rectangle([body_cx - 15, body_cy + 9, body_cx - 13, body_cy + 11], fill=KEY_BRIGHT)
        # Key shaft hanging down
        draw.rectangle([body_cx - 14, body_cy + 14, body_cx - 10, body_cy + 20],
                       fill=KEY_DARK, outline=OUTLINE)
        # Key teeth
        draw.rectangle([body_cx - 10, body_cy + 17, body_cx - 8, body_cy + 19], fill=KEY_GOLD)
        draw.rectangle([body_cx - 10, body_cy + 19, body_cx - 7, body_cy + 21], fill=KEY_DARK)

        # ---- Arm (front) with gauntlet chains ----
        draw.rectangle([body_cx - 14, body_cy - 4, body_cx - 8, body_cy + 6],
                       fill=IRON, outline=OUTLINE)
        draw.rectangle([body_cx - 14, body_cy + 2, body_cx - 8, body_cy + 6],
                       fill=IRON_DARK, outline=OUTLINE)
        # Chain links dangling from gauntlet
        draw.rectangle([body_cx - 15, body_cy + 7, body_cx - 13, body_cy + 9], fill=CHAIN_BRIGHT)
        draw.rectangle([body_cx - 13, body_cy + 9, body_cx - 11, body_cy + 11], fill=CHAIN_DARK)
        draw.rectangle([body_cx - 11, body_cy + 7, body_cx - 9, body_cy + 9], fill=CHAIN_BRIGHT)

        # Shield on far arm
        draw_shield_with_emblem(draw, body_cx + 10, body_cy, LEFT)

        # ---- Head (side, facing left) — iron helm with intense visor ----
        ellipse(draw, body_cx - 2, head_cy, 14, 14, IRON)
        # Chain mail on side of helm
        draw_chainmail_texture(draw, body_cx + 2, head_cy - 8, body_cx + 10, head_cy + 4)
        # Visor plate
        draw.rectangle([body_cx - 16, head_cy, body_cx + 4, head_cy + 6],
                       fill=IRON_DARK, outline=OUTLINE)
        # Visor slit — bright
        draw.rectangle([body_cx - 12, head_cy + 2, body_cx, head_cy + 4],
                       fill=VISOR_SLIT)
        # White-hot core
        draw.rectangle([body_cx - 9, head_cy + 2, body_cx - 5, head_cy + 3], fill=VISOR_CORE)
        # Glow leak above/below
        draw.rectangle([body_cx - 11, head_cy - 1, body_cx - 9, head_cy + 1], fill=FROST_BLUE)
        draw.rectangle([body_cx - 7, head_cy + 5, body_cx - 5, head_cy + 7], fill=FROST_BLUE)
        draw.rectangle([body_cx - 15, head_cy + 1, body_cx - 13, head_cy + 3], fill=VISOR_DIM)

        # Crest
        draw.rectangle([body_cx - 4, head_cy - 14, body_cx, head_cy - 4],
                       fill=IRON_LIGHT, outline=OUTLINE)
        draw.rectangle([body_cx - 3, head_cy - 12, body_cx - 1, head_cy - 6], fill=GREEN_LIGHT)
        # Helm rivets
        draw.rectangle([body_cx - 13, head_cy - 5, body_cx - 11, head_cy - 3], fill=IRON_BRIGHT)
        draw.rectangle([body_cx + 7, head_cy - 5, body_cx + 9, head_cy - 3], fill=IRON_BRIGHT)

    elif direction == RIGHT:
        # ---- Legs — heavier boots ----
        draw.rectangle([body_cx - 2 + leg_spread, body_cy + 10,
                        body_cx + 4 + leg_spread, base_y], fill=IRON_DARK, outline=OUTLINE)
        draw.rectangle([body_cx - 4 + leg_spread, base_y - 8,
                        body_cx + 6 + leg_spread, base_y], fill=BOOT, outline=OUTLINE)
        # Metal toe cap
        draw.rectangle([body_cx + 3 + leg_spread, base_y - 3,
                        body_cx + 7 + leg_spread, base_y - 1], fill=BOOT_CAP)

        draw.rectangle([body_cx + 4 - leg_spread, body_cy + 10,
                        body_cx + 10 - leg_spread, base_y], fill=IRON_DARK, outline=OUTLINE)
        draw.rectangle([body_cx + 2 - leg_spread, base_y - 8,
                        body_cx + 12 - leg_spread, base_y], fill=BOOT, outline=OUTLINE)
        # Toe cap
        draw.rectangle([body_cx + 11 - leg_spread, base_y - 3,
                        body_cx + 13 - leg_spread, base_y - 1], fill=BOOT_CAP)

        # ---- Body ----
        ellipse(draw, body_cx + 2, body_cy, 12, 12, IRON)
        ellipse(draw, body_cx + 2, body_cy - 2, 8, 8, IRON_LIGHT)
        # Chain mail texture on side body
        draw_chainmail_texture(draw, body_cx - 4, body_cy - 6, body_cx + 8, body_cy + 4)
        # Center plate seam
        draw.line([(body_cx + 2, body_cy - 8), (body_cx + 2, body_cy + 2)], fill=ARMOR_ACCENT)
        # Rivet dots
        draw.rectangle([body_cx - 5, body_cy - 7, body_cx - 3, body_cy - 5], fill=IRON_BRIGHT)
        draw.rectangle([body_cx + 7, body_cy - 7, body_cx + 9, body_cy - 5], fill=IRON_BRIGHT)

        # Nature leaf detail on body
        draw_leaf_detail(draw, body_cx + 2, body_cy - 2)

        # Belt
        draw.rectangle([body_cx - 10, body_cy + 6, body_cx + 14, body_cy + 10],
                       fill=CHAIN_DARK, outline=OUTLINE)
        # Key ring — bigger with tooth detail
        draw.ellipse([body_cx + 8, body_cy + 6, body_cx + 18, body_cy + 16],
                     fill=KEY_GOLD, outline=OUTLINE)
        draw.rectangle([body_cx + 13, body_cy + 9, body_cx + 15, body_cy + 11], fill=KEY_BRIGHT)
        # Key shaft
        draw.rectangle([body_cx + 10, body_cy + 14, body_cx + 14, body_cy + 20],
                       fill=KEY_DARK, outline=OUTLINE)
        # Key teeth
        draw.rectangle([body_cx + 14, body_cy + 17, body_cx + 16, body_cy + 19], fill=KEY_GOLD)
        draw.rectangle([body_cx + 14, body_cy + 19, body_cx + 17, body_cy + 21], fill=KEY_DARK)

        # ---- Arm with gauntlet chains ----
        draw.rectangle([body_cx + 8, body_cy - 4, body_cx + 14, body_cy + 6],
                       fill=IRON, outline=OUTLINE)
        draw.rectangle([body_cx + 8, body_cy + 2, body_cx + 14, body_cy + 6],
                       fill=IRON_DARK, outline=OUTLINE)
        # Chain links dangling from gauntlet
        draw.rectangle([body_cx + 9, body_cy + 7, body_cx + 11, body_cy + 9], fill=CHAIN_BRIGHT)
        draw.rectangle([body_cx + 11, body_cy + 9, body_cx + 13, body_cy + 11], fill=CHAIN_DARK)
        draw.rectangle([body_cx + 13, body_cy + 7, body_cx + 15, body_cy + 9], fill=CHAIN_BRIGHT)

        # Shield on far arm
        draw_shield_with_emblem(draw, body_cx - 10, body_cy, RIGHT)

        # ---- Heavy Mace (right hand, extending right) ----
        # Handle (short, brown)
        draw.rectangle([body_cx + 14, body_cy + 0, body_cx + 18, body_cy + 2],
                       fill=MACE_HANDLE, outline=OUTLINE)
        # Mace head — dark iron ball with spikes
        draw.ellipse([body_cx + 18, body_cy - 4, body_cx + 26, body_cy + 4],
                     fill=MACE_HEAD, outline=OUTLINE)
        # Spikes
        draw.rectangle([body_cx + 26, body_cy - 3, body_cx + 28, body_cy - 1], fill=MACE_SPIKE)
        draw.rectangle([body_cx + 21, body_cy - 5, body_cx + 23, body_cy - 3], fill=MACE_SPIKE)
        draw.rectangle([body_cx + 26, body_cy + 1, body_cx + 28, body_cy + 3], fill=MACE_SPIKE)

        # ---- Head (side, facing right) — iron helm with intense visor ----
        ellipse(draw, body_cx + 2, head_cy, 14, 14, IRON)
        # Chain mail on side of helm
        draw_chainmail_texture(draw, body_cx - 8, head_cy - 8, body_cx, head_cy + 4)
        # Visor plate
        draw.rectangle([body_cx - 4, head_cy, body_cx + 16, head_cy + 6],
                       fill=IRON_DARK, outline=OUTLINE)
        # Visor slit — bright
        draw.rectangle([body_cx, head_cy + 2, body_cx + 12, head_cy + 4],
                       fill=VISOR_SLIT)
        # White-hot core
        draw.rectangle([body_cx + 5, head_cy + 2, body_cx + 9, head_cy + 3], fill=VISOR_CORE)
        # Glow leak above/below
        draw.rectangle([body_cx + 9, head_cy - 1, body_cx + 11, head_cy + 1], fill=FROST_BLUE)
        draw.rectangle([body_cx + 5, head_cy + 5, body_cx + 7, head_cy + 7], fill=FROST_BLUE)
        draw.rectangle([body_cx + 13, head_cy + 1, body_cx + 15, head_cy + 3], fill=VISOR_DIM)

        # Crest
        draw.rectangle([body_cx, head_cy - 14, body_cx + 4, head_cy - 4],
                       fill=IRON_LIGHT, outline=OUTLINE)
        draw.rectangle([body_cx + 1, head_cy - 12, body_cx + 3, head_cy - 6], fill=GREEN_LIGHT)
        # Helm rivets
        draw.rectangle([body_cx - 9, head_cy - 5, body_cx - 7, head_cy - 3], fill=IRON_BRIGHT)
        draw.rectangle([body_cx + 11, head_cy - 5, body_cx + 13, head_cy - 3], fill=IRON_BRIGHT)


def main():
    img = Image.new("RGBA", (IMG_W, IMG_H), (0, 0, 0, 0))

    for direction in range(ROWS):
        for frame in range(COLS):
            frame_img = Image.new("RGBA", (FRAME_SIZE, FRAME_SIZE), (0, 0, 0, 0))
            frame_draw = ImageDraw.Draw(frame_img)
            draw_warden(frame_draw, 0, 0, direction, frame)
            img.paste(frame_img, (frame * FRAME_SIZE, direction * FRAME_SIZE))

    img.save("sprites/warden.png")
    print(f"Generated sprites/warden.png ({IMG_W}x{IMG_H})")


if __name__ == "__main__":
    main()
