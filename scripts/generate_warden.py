#!/usr/bin/env python3
"""Generate sprites/warden.png — 4-column x 4-row character spritesheet.

128x128 PNG, 32x32 per frame.
Row layout: Down=0, Up=1, Left=2, Right=3
4 walking animation frames per direction.

Style matches Spaceman/Gladiator: big round head, round body, small limbs, dark outlines.
Theme: Prison warden — dark iron armor, chain details, heavy key ring, iron mask/visor.
Enhanced: heavy mace weapon, intense visor glow, chain gauntlets, rivet details,
          heavier boots, bigger key ring, stomp animation.
"""

from PIL import Image, ImageDraw

FRAME_SIZE = 32
COLS = 4
ROWS = 4
IMG_W = FRAME_SIZE * COLS   # 128
IMG_H = FRAME_SIZE * ROWS   # 128

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

DOWN, UP, LEFT, RIGHT = 0, 1, 2, 3


def ellipse(draw, cx, cy, rx, ry, fill, outline=OUTLINE):
    draw.ellipse([cx - rx, cy - ry, cx + rx, cy + ry], fill=fill, outline=outline)


def draw_warden(draw, ox, oy, direction, frame):
    # Heavier stomp bob pattern: [0, -1, 0, -2]
    bob = [0, -1, 0, -2][frame]
    leg_spread = [-2, 0, 2, 0][frame]

    base_y = oy + 27 + bob
    body_cx = ox + 16
    body_cy = base_y - 10
    head_cy = body_cy - 10

    if direction == DOWN:
        # ---- Legs — heavy iron boots (wider/blockier) ----
        draw.rectangle([body_cx - 5 + leg_spread, body_cy + 5,
                        body_cx - 2 + leg_spread, base_y], fill=IRON_DARK, outline=OUTLINE)
        draw.rectangle([body_cx + 2 - leg_spread, body_cy + 5,
                        body_cx + 5 - leg_spread, base_y], fill=IRON_DARK, outline=OUTLINE)
        # Boots — wider with metal toe cap
        draw.rectangle([body_cx - 7 + leg_spread, base_y - 4,
                        body_cx - 1 + leg_spread, base_y], fill=BOOT, outline=OUTLINE)
        draw.rectangle([body_cx + 1 - leg_spread, base_y - 4,
                        body_cx + 7 - leg_spread, base_y], fill=BOOT, outline=OUTLINE)
        # Metal toe caps
        draw.point((body_cx - 6 + leg_spread, base_y - 1), fill=BOOT_CAP)
        draw.point((body_cx - 5 + leg_spread, base_y - 1), fill=BOOT_CAP)
        draw.point((body_cx + 5 - leg_spread, base_y - 1), fill=BOOT_CAP)
        draw.point((body_cx + 6 - leg_spread, base_y - 1), fill=BOOT_CAP)

        # ---- Body — dark iron armor ----
        ellipse(draw, body_cx, body_cy, 7, 6, IRON)
        # Armor chest plate with center seam
        draw.rectangle([body_cx - 3, body_cy - 4, body_cx + 3, body_cy + 1],
                       fill=IRON_LIGHT, outline=OUTLINE)
        # Center plate seam line
        draw.line([(body_cx, body_cy - 4), (body_cx, body_cy + 1)], fill=ARMOR_ACCENT)
        # Rivet dots at chest plate corners
        draw.point((body_cx - 3, body_cy - 4), fill=IRON_BRIGHT)
        draw.point((body_cx + 3, body_cy - 4), fill=IRON_BRIGHT)
        draw.point((body_cx - 3, body_cy + 1), fill=IRON_BRIGHT)
        draw.point((body_cx + 3, body_cy + 1), fill=IRON_BRIGHT)

        # Chain belt
        draw.rectangle([body_cx - 7, body_cy + 3, body_cx + 7, body_cy + 5],
                       fill=CHAIN_DARK, outline=OUTLINE)
        # Chain link details on belt
        for i in range(-5, 6, 3):
            draw.rectangle([body_cx + i, body_cy + 3, body_cx + i + 1, body_cy + 5],
                           fill=CHAIN)

        # Key ring hanging from belt — bigger with key-tooth shape
        draw.ellipse([body_cx + 3, body_cy + 4, body_cx + 8, body_cy + 8],
                     fill=KEY_GOLD, outline=OUTLINE)
        draw.point((body_cx + 5, body_cy + 5), fill=KEY_BRIGHT)
        # Key shaft
        draw.rectangle([body_cx + 5, body_cy + 7, body_cx + 7, body_cy + 10],
                       fill=KEY_DARK, outline=OUTLINE)
        # Key teeth (distinct tooth shape)
        draw.point((body_cx + 7, body_cy + 9), fill=KEY_GOLD)
        draw.point((body_cx + 8, body_cy + 9), fill=KEY_GOLD)
        draw.point((body_cx + 7, body_cy + 10), fill=KEY_DARK)

        # ---- Arms — armored gauntlets with chains ----
        # Left arm
        draw.rectangle([body_cx - 9, body_cy - 3, body_cx - 6, body_cy + 3],
                       fill=IRON, outline=OUTLINE)
        draw.rectangle([body_cx - 9, body_cy + 1, body_cx - 6, body_cy + 3],
                       fill=IRON_DARK, outline=OUTLINE)
        # Left gauntlet chain links (alternating bright/dark)
        draw.point((body_cx - 9, body_cy + 4), fill=CHAIN_BRIGHT)
        draw.point((body_cx - 8, body_cy + 5), fill=CHAIN_DARK)
        draw.point((body_cx - 7, body_cy + 4), fill=CHAIN_BRIGHT)

        # Right arm
        draw.rectangle([body_cx + 6, body_cy - 3, body_cx + 9, body_cy + 3],
                       fill=IRON, outline=OUTLINE)
        draw.rectangle([body_cx + 6, body_cy + 1, body_cx + 9, body_cy + 3],
                       fill=IRON_DARK, outline=OUTLINE)
        # Right gauntlet chain links
        draw.point((body_cx + 7, body_cy + 4), fill=CHAIN_BRIGHT)
        draw.point((body_cx + 8, body_cy + 5), fill=CHAIN_DARK)
        draw.point((body_cx + 9, body_cy + 4), fill=CHAIN_BRIGHT)

        # ---- Heavy Mace (right hand, extending down) ----
        # Handle (brown, 2px tall from right hand)
        draw.rectangle([body_cx + 9, body_cy + 2, body_cx + 10, body_cy + 4],
                       fill=MACE_HANDLE, outline=OUTLINE)
        # Mace head — dark iron ball
        draw.ellipse([body_cx + 8, body_cy + 4, body_cx + 12, body_cy + 8],
                     fill=MACE_HEAD, outline=OUTLINE)
        # Spikes on mace head (bright metallic)
        draw.point((body_cx + 12, body_cy + 5), fill=MACE_SPIKE)
        draw.point((body_cx + 10, body_cy + 4), fill=MACE_SPIKE)
        draw.point((body_cx + 8, body_cy + 6), fill=MACE_SPIKE)

        # ---- Head — iron helm with intense visor glow ----
        ellipse(draw, body_cx, head_cy, 8, 7, IRON)
        # Visor plate
        draw.rectangle([body_cx - 6, head_cy, body_cx + 6, head_cy + 4],
                       fill=IRON_DARK, outline=OUTLINE)
        # Visor slit — brighter glowing icy blue
        draw.rectangle([body_cx - 4, head_cy + 1, body_cx + 4, head_cy + 3],
                       fill=VISOR_SLIT)
        # White-hot center core of visor
        draw.rectangle([body_cx - 2, head_cy + 2, body_cx + 2, head_cy + 2],
                       fill=VISOR_CORE)
        # Glow leak pixels above and below the slit
        draw.point((body_cx - 2, head_cy), fill=FROST_BLUE)
        draw.point((body_cx + 2, head_cy), fill=FROST_BLUE)
        draw.point((body_cx - 1, head_cy + 4), fill=FROST_BLUE)
        draw.point((body_cx + 1, head_cy + 4), fill=FROST_BLUE)
        # Glow pixel right at slit edges
        draw.point((body_cx - 5, head_cy + 2), fill=VISOR_DIM)
        draw.point((body_cx + 5, head_cy + 2), fill=VISOR_DIM)

        # Helm crest
        draw.rectangle([body_cx - 1, head_cy - 7, body_cx + 1, head_cy - 2],
                       fill=IRON_LIGHT, outline=OUTLINE)
        # Rivet details on helm
        draw.point((body_cx - 6, head_cy - 2), fill=IRON_BRIGHT)
        draw.point((body_cx + 6, head_cy - 2), fill=IRON_BRIGHT)

    elif direction == UP:
        # ---- Legs — heavier boots ----
        draw.rectangle([body_cx - 5 + leg_spread, body_cy + 5,
                        body_cx - 2 + leg_spread, base_y], fill=IRON_DARK, outline=OUTLINE)
        draw.rectangle([body_cx + 2 - leg_spread, body_cy + 5,
                        body_cx + 5 - leg_spread, base_y], fill=IRON_DARK, outline=OUTLINE)
        # Wider boots
        draw.rectangle([body_cx - 7 + leg_spread, base_y - 4,
                        body_cx - 1 + leg_spread, base_y], fill=BOOT, outline=OUTLINE)
        draw.rectangle([body_cx + 1 - leg_spread, base_y - 4,
                        body_cx + 7 - leg_spread, base_y], fill=BOOT, outline=OUTLINE)
        # Metal toe caps (visible even from back, at edges)
        draw.point((body_cx - 6 + leg_spread, base_y - 1), fill=BOOT_CAP)
        draw.point((body_cx + 6 - leg_spread, base_y - 1), fill=BOOT_CAP)

        # Chain cape on back
        chain_sway = [0, 1, 0, -1][frame]
        for i in range(3):
            cy_off = body_cy - 2 + i * 3
            draw.rectangle([body_cx - 4 + chain_sway, cy_off,
                            body_cx + 4 + chain_sway, cy_off + 2],
                           fill=CHAIN_DARK, outline=None)
            for j in range(-3, 4, 2):
                draw.point((body_cx + j + chain_sway, cy_off + 1), fill=CHAIN)

        # ---- Body ----
        ellipse(draw, body_cx, body_cy, 7, 6, IRON)
        # Back armor plate
        ellipse(draw, body_cx, body_cy - 1, 5, 4, IRON_DARK)
        # Rivet dots on back plate
        draw.point((body_cx - 4, body_cy - 3), fill=IRON_BRIGHT)
        draw.point((body_cx + 4, body_cy - 3), fill=IRON_BRIGHT)
        draw.point((body_cx - 4, body_cy + 1), fill=IRON_BRIGHT)
        draw.point((body_cx + 4, body_cy + 1), fill=IRON_BRIGHT)

        # ---- Arms with chain gauntlets ----
        draw.rectangle([body_cx - 9, body_cy - 3, body_cx - 6, body_cy + 3],
                       fill=IRON, outline=OUTLINE)
        draw.rectangle([body_cx + 6, body_cy - 3, body_cx + 9, body_cy + 3],
                       fill=IRON, outline=OUTLINE)
        # Gauntlet chain links
        draw.point((body_cx - 9, body_cy + 4), fill=CHAIN_BRIGHT)
        draw.point((body_cx - 8, body_cy + 5), fill=CHAIN_DARK)
        draw.point((body_cx + 8, body_cy + 4), fill=CHAIN_BRIGHT)
        draw.point((body_cx + 9, body_cy + 5), fill=CHAIN_DARK)

        # ---- Mace (visible on right side from behind) ----
        draw.rectangle([body_cx + 9, body_cy + 2, body_cx + 10, body_cy + 4],
                       fill=MACE_HANDLE, outline=OUTLINE)
        draw.ellipse([body_cx + 8, body_cy + 4, body_cx + 12, body_cy + 8],
                     fill=MACE_HEAD, outline=OUTLINE)
        draw.point((body_cx + 12, body_cy + 5), fill=MACE_SPIKE)
        draw.point((body_cx + 10, body_cy + 8), fill=MACE_SPIKE)

        # ---- Head — back of helm ----
        ellipse(draw, body_cx, head_cy, 8, 7, IRON)
        ellipse(draw, body_cx, head_cy, 6, 5, IRON_DARK)
        # Crest
        draw.rectangle([body_cx - 1, head_cy - 7, body_cx + 1, head_cy - 2],
                       fill=IRON_LIGHT, outline=OUTLINE)
        # Rivet dots on back of helm
        draw.point((body_cx - 5, head_cy), fill=IRON_BRIGHT)
        draw.point((body_cx + 5, head_cy), fill=IRON_BRIGHT)

    elif direction == LEFT:
        # ---- Legs (side view) — heavier boots ----
        draw.rectangle([body_cx - 1 - leg_spread, body_cy + 5,
                        body_cx + 2 - leg_spread, base_y], fill=IRON_DARK, outline=OUTLINE)
        draw.rectangle([body_cx - 1 - leg_spread, base_y - 4,
                        body_cx + 3 - leg_spread, base_y], fill=BOOT, outline=OUTLINE)
        # Metal toe cap on front boot
        draw.point((body_cx - 1 - leg_spread, base_y - 1), fill=BOOT_CAP)
        draw.point((body_cx - 2 - leg_spread, base_y - 1), fill=BOOT_CAP)

        draw.rectangle([body_cx - 4 + leg_spread, body_cy + 5,
                        body_cx - 1 + leg_spread, base_y], fill=IRON_DARK, outline=OUTLINE)
        draw.rectangle([body_cx - 5 + leg_spread, base_y - 4,
                        body_cx + 0 + leg_spread, base_y], fill=BOOT, outline=OUTLINE)
        # Toe cap
        draw.point((body_cx - 5 + leg_spread, base_y - 1), fill=BOOT_CAP)

        # ---- Body ----
        ellipse(draw, body_cx - 1, body_cy, 6, 6, IRON)
        ellipse(draw, body_cx - 1, body_cy - 1, 4, 4, IRON_LIGHT)
        # Center plate seam
        draw.line([(body_cx - 1, body_cy - 4), (body_cx - 1, body_cy + 1)], fill=ARMOR_ACCENT)
        # Rivet dots
        draw.point((body_cx - 4, body_cy - 3), fill=IRON_BRIGHT)
        draw.point((body_cx + 2, body_cy - 3), fill=IRON_BRIGHT)

        # Belt
        draw.rectangle([body_cx - 7, body_cy + 3, body_cx + 5, body_cy + 5],
                       fill=CHAIN_DARK, outline=OUTLINE)
        # Key ring — bigger with tooth detail
        draw.ellipse([body_cx - 9, body_cy + 3, body_cx - 4, body_cy + 8],
                     fill=KEY_GOLD, outline=OUTLINE)
        draw.point((body_cx - 7, body_cy + 5), fill=KEY_BRIGHT)
        # Key shaft hanging down
        draw.rectangle([body_cx - 7, body_cy + 7, body_cx - 5, body_cy + 10],
                       fill=KEY_DARK, outline=OUTLINE)
        # Key teeth
        draw.point((body_cx - 5, body_cy + 9), fill=KEY_GOLD)
        draw.point((body_cx - 4, body_cy + 9), fill=KEY_GOLD)
        draw.point((body_cx - 5, body_cy + 10), fill=KEY_DARK)

        # ---- Arm (front) with gauntlet chains ----
        draw.rectangle([body_cx - 7, body_cy - 2, body_cx - 4, body_cy + 3],
                       fill=IRON, outline=OUTLINE)
        draw.rectangle([body_cx - 7, body_cy + 1, body_cx - 4, body_cy + 3],
                       fill=IRON_DARK, outline=OUTLINE)
        # Chain links dangling from gauntlet
        draw.point((body_cx - 7, body_cy + 4), fill=CHAIN_BRIGHT)
        draw.point((body_cx - 6, body_cy + 5), fill=CHAIN_DARK)
        draw.point((body_cx - 5, body_cy + 4), fill=CHAIN_BRIGHT)

        # ---- Head (side, facing left) — iron helm with intense visor ----
        ellipse(draw, body_cx - 1, head_cy, 7, 7, IRON)
        # Visor plate
        draw.rectangle([body_cx - 8, head_cy, body_cx + 2, head_cy + 3],
                       fill=IRON_DARK, outline=OUTLINE)
        # Visor slit — bright
        draw.rectangle([body_cx - 6, head_cy + 1, body_cx, head_cy + 2],
                       fill=VISOR_SLIT)
        # White-hot core
        draw.point((body_cx - 4, head_cy + 1), fill=VISOR_CORE)
        draw.point((body_cx - 3, head_cy + 1), fill=VISOR_CORE)
        # Glow leak above/below
        draw.point((body_cx - 5, head_cy), fill=FROST_BLUE)
        draw.point((body_cx - 3, head_cy + 3), fill=FROST_BLUE)
        draw.point((body_cx - 7, head_cy + 1), fill=VISOR_DIM)

        # Crest
        draw.rectangle([body_cx - 2, head_cy - 7, body_cx, head_cy - 2],
                       fill=IRON_LIGHT, outline=OUTLINE)
        # Helm rivets
        draw.point((body_cx - 6, head_cy - 2), fill=IRON_BRIGHT)
        draw.point((body_cx + 4, head_cy - 2), fill=IRON_BRIGHT)

    elif direction == RIGHT:
        # ---- Legs — heavier boots ----
        draw.rectangle([body_cx - 1 + leg_spread, body_cy + 5,
                        body_cx + 2 + leg_spread, base_y], fill=IRON_DARK, outline=OUTLINE)
        draw.rectangle([body_cx - 2 + leg_spread, base_y - 4,
                        body_cx + 3 + leg_spread, base_y], fill=BOOT, outline=OUTLINE)
        # Metal toe cap
        draw.point((body_cx + 2 + leg_spread, base_y - 1), fill=BOOT_CAP)
        draw.point((body_cx + 3 + leg_spread, base_y - 1), fill=BOOT_CAP)

        draw.rectangle([body_cx + 2 - leg_spread, body_cy + 5,
                        body_cx + 5 - leg_spread, base_y], fill=IRON_DARK, outline=OUTLINE)
        draw.rectangle([body_cx + 1 - leg_spread, base_y - 4,
                        body_cx + 6 - leg_spread, base_y], fill=BOOT, outline=OUTLINE)
        # Toe cap
        draw.point((body_cx + 6 - leg_spread, base_y - 1), fill=BOOT_CAP)

        # ---- Body ----
        ellipse(draw, body_cx + 1, body_cy, 6, 6, IRON)
        ellipse(draw, body_cx + 1, body_cy - 1, 4, 4, IRON_LIGHT)
        # Center plate seam
        draw.line([(body_cx + 1, body_cy - 4), (body_cx + 1, body_cy + 1)], fill=ARMOR_ACCENT)
        # Rivet dots
        draw.point((body_cx - 2, body_cy - 3), fill=IRON_BRIGHT)
        draw.point((body_cx + 4, body_cy - 3), fill=IRON_BRIGHT)

        # Belt
        draw.rectangle([body_cx - 5, body_cy + 3, body_cx + 7, body_cy + 5],
                       fill=CHAIN_DARK, outline=OUTLINE)
        # Key ring — bigger with tooth detail
        draw.ellipse([body_cx + 4, body_cy + 3, body_cx + 9, body_cy + 8],
                     fill=KEY_GOLD, outline=OUTLINE)
        draw.point((body_cx + 7, body_cy + 5), fill=KEY_BRIGHT)
        # Key shaft
        draw.rectangle([body_cx + 5, body_cy + 7, body_cx + 7, body_cy + 10],
                       fill=KEY_DARK, outline=OUTLINE)
        # Key teeth
        draw.point((body_cx + 7, body_cy + 9), fill=KEY_GOLD)
        draw.point((body_cx + 8, body_cy + 9), fill=KEY_GOLD)
        draw.point((body_cx + 7, body_cy + 10), fill=KEY_DARK)

        # ---- Arm with gauntlet chains ----
        draw.rectangle([body_cx + 4, body_cy - 2, body_cx + 7, body_cy + 3],
                       fill=IRON, outline=OUTLINE)
        draw.rectangle([body_cx + 4, body_cy + 1, body_cx + 7, body_cy + 3],
                       fill=IRON_DARK, outline=OUTLINE)
        # Chain links dangling from gauntlet
        draw.point((body_cx + 5, body_cy + 4), fill=CHAIN_BRIGHT)
        draw.point((body_cx + 6, body_cy + 5), fill=CHAIN_DARK)
        draw.point((body_cx + 7, body_cy + 4), fill=CHAIN_BRIGHT)

        # ---- Heavy Mace (right hand, extending right) ----
        # Handle (short, brown)
        draw.rectangle([body_cx + 7, body_cy + 0, body_cx + 9, body_cy + 1],
                       fill=MACE_HANDLE, outline=OUTLINE)
        # Mace head — dark iron ball with spikes
        draw.ellipse([body_cx + 9, body_cy - 2, body_cx + 13, body_cy + 2],
                     fill=MACE_HEAD, outline=OUTLINE)
        # Spikes
        draw.point((body_cx + 13, body_cy - 1), fill=MACE_SPIKE)
        draw.point((body_cx + 11, body_cy - 2), fill=MACE_SPIKE)
        draw.point((body_cx + 13, body_cy + 1), fill=MACE_SPIKE)

        # ---- Head (side, facing right) — iron helm with intense visor ----
        ellipse(draw, body_cx + 1, head_cy, 7, 7, IRON)
        # Visor plate
        draw.rectangle([body_cx - 2, head_cy, body_cx + 8, head_cy + 3],
                       fill=IRON_DARK, outline=OUTLINE)
        # Visor slit — bright
        draw.rectangle([body_cx, head_cy + 1, body_cx + 6, head_cy + 2],
                       fill=VISOR_SLIT)
        # White-hot core
        draw.point((body_cx + 3, head_cy + 1), fill=VISOR_CORE)
        draw.point((body_cx + 4, head_cy + 1), fill=VISOR_CORE)
        # Glow leak above/below
        draw.point((body_cx + 5, head_cy), fill=FROST_BLUE)
        draw.point((body_cx + 3, head_cy + 3), fill=FROST_BLUE)
        draw.point((body_cx + 7, head_cy + 1), fill=VISOR_DIM)

        # Crest
        draw.rectangle([body_cx, head_cy - 7, body_cx + 2, head_cy - 2],
                       fill=IRON_LIGHT, outline=OUTLINE)
        # Helm rivets
        draw.point((body_cx - 4, head_cy - 2), fill=IRON_BRIGHT)
        draw.point((body_cx + 6, head_cy - 2), fill=IRON_BRIGHT)


def main():
    img = Image.new("RGBA", (IMG_W, IMG_H), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    for direction in range(ROWS):
        for frame in range(COLS):
            ox = frame * FRAME_SIZE
            oy = direction * FRAME_SIZE
            draw_warden(draw, ox, oy, direction, frame)

    img.save("sprites/warden.png")
    print(f"Generated sprites/warden.png ({IMG_W}x{IMG_H})")


if __name__ == "__main__":
    main()
