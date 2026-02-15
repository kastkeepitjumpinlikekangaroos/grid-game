#!/usr/bin/env python3
"""Generate sprites/tiles.png — 34-column × 4-row isometric tileset.

Per-pixel renderer with strong directional lighting, bold textures,
and high contrast between tile faces for clear 3D depth.
"""

import math
from PIL import Image

CELL_W, CELL_H = 40, 56
HW, HH = 20, 10
CX, BASE_Y = 20, 46
NUM_TILES, ANIM_FRAMES = 34, 4

TILES = [
    (0,  "Grass",       0xFF4CAF50,  0),
    (1,  "Water",       0xFF2196F3, 12),
    (2,  "Sand",        0xFFF5DEB3,  0),
    (3,  "Stone",       0xFF9E9E9E,  0),
    (4,  "Wall",        0xFF5D4037, 20),
    (5,  "Tree",        0xFF2E7D32, 28),
    (6,  "Path",        0xFFD7CCC8,  0),
    (7,  "DeepWater",   0xFF1565C0, 16),
    (8,  "Snow",        0xFFFAFAFA,  0),
    (9,  "Ice",         0xFFB3E5FC,  0),
    (10, "Lava",        0xFFFF5722, 12),
    (11, "Mountain",    0xFF757575, 36),
    (12, "Fence",       0xFFA1887F, 12),
    (13, "Metal",       0xFF78909C,  0),
    (14, "Glass",       0xFF80DEEA,  0),
    (15, "EnergyField", 0xFFAB47BC, 20),
    (16, "Circuit",     0xFF004D40,  0),
    (17, "Void",        0xFF0A0A12, 12),
    (18, "Toxic",       0xFF76FF03, 12),
    (19, "Plasma",      0xFFFF4081, 16),
    (20, "Flowers",     0xFF66BB6A,  0),
    (21, "Dirt",        0xFF8D6E63,  0),
    (22, "Cobblestone", 0xFF90A4AE,  0),
    (23, "Marsh",       0xFF5D7A3E,  0),
    (24, "Crystal",     0xFF7E57C2, 24),
    (25, "Coral",       0xFFFF7043, 10),
    (26, "Ruins",       0xFF7E7360, 18),
    (27, "Moss",        0xFF689F38,  0),
    (28, "Obsidian",    0xFF1A1A2E, 22),
    (29, "Cliff",       0xFF5C5040, 44),
    (30, "Ash",         0xFF6B6B6B,  0),
    (31, "Thorns",      0xFF3B2F18, 18),
    (32, "Basalt",      0xFF2D2D3D, 34),
    (33, "Gravel",      0xFFB0A899,  0),
]

# ── Color ────────────────────────────────────────────────────────────

def cl(v): return max(0, min(255, int(round(v))))
def rgb(r, g, b): return (cl(r), cl(g), cl(b))
def darken(c, f): return rgb(c[0]*(1-f), c[1]*(1-f), c[2]*(1-f))
def lighten(c, f): return rgb(c[0]+(255-c[0])*f, c[1]+(255-c[1])*f, c[2]+(255-c[2])*f)
def lerp(a, b, t):
    t = max(0.0, min(1.0, t))
    return rgb(a[0]+(b[0]-a[0])*t, a[1]+(b[1]-a[1])*t, a[2]+(b[2]-a[2])*t)
def scale(c, s): return rgb(c[0]*s, c[1]*s, c[2]*s)
def argb(v): return ((v>>16)&0xFF, (v>>8)&0xFF, v&0xFF)

# ── Noise ────────────────────────────────────────────────────────────

def _h(x, y, s=0):
    n = (x * 374761393 + y * 668265263 + s * 1274126177) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1103515245) & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0x7FFFFFFF) / 0x7FFFFFFF

def _sn(x, y, s=0):
    ix, iy = int(math.floor(x)), int(math.floor(y))
    fx, fy = x - ix, y - iy
    fx = fx*fx*(3 - 2*fx); fy = fy*fy*(3 - 2*fy)
    return (_h(ix,iy,s)*(1-fx)+_h(ix+1,iy,s)*fx)*(1-fy) + \
           (_h(ix,iy+1,s)*(1-fx)+_h(ix+1,iy+1,s)*fx)*fy

def fbm(x, y, oct=3, s=0):
    v=0.0; a=1.0; f=1.0; t=0.0
    for i in range(oct):
        v += _sn(x*f, y*f, s+i*997)*a; t+=a; a*=0.5; f*=2.0
    return v/t

# ── Geometry ─────────────────────────────────────────────────────────

def classify(px, py, h):
    if h == 0:
        dx, dy = abs(px - CX), abs(py - BASE_Y)
        d = dx/HW + dy/HH
        if d > 1.0: return None
        return ("top", px/39.0, (py - BASE_Y + HH)/(2*HH - 1), 1.0 - d)

    top_cy = BASE_Y - h
    dx, dy = abs(px - CX), abs(py - top_cy)
    d = dx/HW + dy/HH
    if d <= 1.0:
        return ("top", px/39.0, (py - top_cy + HH)/(2*HH - 1), 1.0 - d)

    if px <= CX:
        yt = BASE_Y - h + px * 0.5
        yb = BASE_Y + px * 0.5
        if yt <= py <= yb and h > 0:
            return ("left", px/CX, (py - yt)/h, min(px/CX, 1-px/CX, (py-yt)/h, 1-(py-yt)/h))
    if px >= CX:
        rpx = px - CX
        yt = BASE_Y + HH - h - rpx * 0.5
        yb = BASE_Y + HH - rpx * 0.5
        if yt <= py <= yb and h > 0:
            return ("right", rpx/HW, (py-yt)/h, min(rpx/HW, 1-rpx/HW, (py-yt)/h, 1-(py-yt)/h))
    return None

# ── Face brightness ──────────────────────────────────────────────────

def face_bright(face, u, v, edge, h):
    """Strong directional lighting. Returns brightness multiplier."""
    dither = (_h(int(u*100), int(v*100), 777) - 0.5) * 0.012

    if face == "top":
        if h <= 4:
            # FLAT TILES: clean, simple shading
            b = 1.0 + dither
        else:
            # ELEVATED top face: moderate directional
            b = 1.04 - (u + v - 1.0) * 0.10 + dither
            if edge < 0.14:
                ef = (0.14 - edge) / 0.14
                if u + v < 1.0:
                    b += ef * 0.16
                else:
                    b -= ef * 0.12
    elif face == "left":
        b = 0.74 - v * 0.24 + dither   # 0.74 top -> 0.50 bottom
        if v < 0.07: b += (0.07 - v) / 0.07 * 0.12   # top edge highlight
        if v > 0.88: b -= (v - 0.88) / 0.12 * 0.14   # bottom AO
    else:  # right
        b = 0.50 - v * 0.20 + dither   # 0.50 top -> 0.30 bottom
        if v < 0.07: b += (0.07 - v) / 0.07 * 0.08
        if v > 0.88: b -= (v - 0.88) / 0.12 * 0.12
    return max(0.15, min(1.3, b))


# ── Tile renderers ───────────────────────────────────────────────────

def _grass(c, base, f, u, v, e, px, py, h, fr):
    hi = (92, 205, 52)
    lo = (38, 135, 38)
    n = _sn(px * 0.07, py * 0.07, 42)  # low freq = big patches
    n = round(n * 3) / 3  # quantize for cleaner look
    gc = lerp(lo, hi, n)
    # Bold tuft accents
    t = _h(px//3, py//3, 50)
    if t > 0.78:
        gc = darken(gc, 0.18)
    elif t < 0.12:
        gc = lighten(gc, 0.15)
    return scale(gc, face_bright(f, u, v, e, h))

def _water(c, base, f, u, v, e, px, py, h, fr):
    if f == "top":
        deep = (18, 90, 205)
        shallow = (70, 175, 250)
        wc = lerp(deep, shallow, max(0, 1.0 - e * 1.3))
        # Bold animated wave bands
        w1 = math.sin(px * 0.4 + py * 0.2 + fr * 1.8)
        w2 = math.sin(px * 0.2 - py * 0.5 + fr * 1.3)
        wave = max(0, w1 * 0.5 + w2 * 0.5)
        wc = lighten(wc, wave * 0.32)
        # Big specular highlights
        sp = math.sin(px * 1.1 + fr * 2.5) * math.cos(py * 0.8 + fr * 1.7)
        if sp > 0.6:
            wc = lighten(wc, min(0.7, (sp - 0.6) * 1.8))
        return scale(wc, face_bright(f, u, v, e, h))
    else:
        wc = lerp((8, 40, 110), (18, 70, 155), 1.0 - v)
        rip = math.sin(py * 0.7 + fr * 1.0) * 0.5 + 0.5
        wc = lighten(wc, rip * 0.08)
        return scale(wc, face_bright(f, u, v, e, h))

def _sand(c, base, f, u, v, e, px, py, h, fr):
    hi = (248, 225, 162)
    lo = (215, 188, 132)
    n = _sn(px * 0.06, py * 0.06, 101)
    n = round(n * 3) / 3
    sc = lerp(lo, hi, n)
    # Wind ripple
    rip = math.sin(px * 0.25 + py * 0.06 + _h(py//5, 0, 105) * 4) * 0.5 + 0.5
    sc = lighten(sc, rip * 0.12)
    return scale(sc, face_bright(f, u, v, e, h))

def _stone(c, base, f, u, v, e, px, py, h, fr):
    warm = (172, 165, 152)
    cool = (145, 150, 162)
    n = _sn(px * 0.06, py * 0.06, 200)
    n = round(n * 3) / 3
    sc = lerp(cool, warm, n)
    # Bold cracks
    cn = _sn(px * 0.14, py * 0.14, 210)
    crack = abs(cn * 2.5 - round(cn * 2.5))
    if crack < 0.07:
        sc = darken(sc, 0.35)
    return scale(sc, face_bright(f, u, v, e, h))

def _wall(c, base, f, u, v, e, px, py, h, fr):
    if f == "top":
        cap = lerp((105, 80, 65), (120, 95, 78), _h(px, py, 400))
        return scale(cap, face_bright(f, u, v, e, h))
    # Brick sides
    bh, bw = 5, 8
    row = py // bh
    col = (px + (row % 2) * (bw // 2)) // bw
    lx = (px + (row % 2) * (bw // 2)) % bw
    ly = py % bh
    if lx == 0 or ly == 0:
        return scale(lerp((50, 40, 32), (62, 50, 40), _h(px, py, 410)),
                     face_bright(f, u, v, e, h))
    bc = lerp((82, 54, 40), (112, 78, 58), _h(col, row, 420))
    d = (_h(px, py, 425) - 0.5) * 10
    bc = rgb(bc[0]+d, bc[1]+d, bc[2]+d)
    return scale(bc, face_bright(f, u, v, e, h))

def _tree(c, base, f, u, v, e, px, py, h, fr):
    # Canopy colors — rich vivid greens
    leaf_hi = (78, 195, 45)
    leaf_md = (40, 148, 30)
    leaf_lo = (18, 88, 18)
    # Trunk — warm distinct brown
    bark_hi = (105, 75, 38)
    bark_lo = (55, 35, 14)

    if f == "top":
        n = _sn(px * 0.18, py * 0.18, 500)
        leaf = lerp(leaf_lo, leaf_hi, n)
        # Rounded dome: bright center, dark edges
        dome = min(1.0, e * 2.0)  # 0 at edge, 1 at center
        leaf = lerp(leaf_lo, leaf, 0.4 + dome * 0.6)
        # Highlight spots
        if _h(px, py, 505) > 0.86:
            leaf = lighten(leaf, 0.2)
        b = 1.0 - (u + v - 1.0) * 0.05
        b += dome * 0.08
        return scale(leaf, max(0.5, b))

    # Side faces
    canopy_end = 0.50
    trunk_start = 0.58

    if v < canopy_end:
        n = _sn(px * 0.15, py * 0.15, 510)
        leaf = lerp(leaf_lo, leaf_md, n)
        # Rounded: peak at v≈0.2
        dome = 1.0 - ((v / canopy_end - 0.35) / 0.45) ** 2
        dome = max(0, min(1, dome))
        base_b = 0.70 if f == "left" else 0.48
        leaf = lerp(leaf_lo, leaf, 0.4 + dome * 0.6)
        if _h(px, py, 515) > 0.88:
            leaf = lighten(leaf, 0.14)
        return scale(leaf, max(0.3, base_b + dome * 0.15))
    elif v < trunk_start:
        t = (v - canopy_end) / (trunk_start - canopy_end)
        n1 = _sn(px * 0.15, py * 0.15, 510)
        leaf = lerp(leaf_lo, leaf_md, n1)
        n2 = _sn(px * 0.12, py * 0.6, 520)
        trunk = lerp(bark_lo, bark_hi, n2)
        mixed = lerp(darken(leaf, 0.15), trunk, t)
        b = 0.65 if f == "left" else 0.45
        return scale(mixed, b)
    else:
        n = _sn(px * 0.12, py * 0.6, 520)
        trunk = lerp(bark_lo, bark_hi, n)
        # Bark lines
        if _h(px, py // 2, 525) > 0.8:
            trunk = darken(trunk, 0.15)
        # Center highlight
        cu = u if f == "left" else 1.0 - u
        if cu > 0.4 and cu < 0.7:
            trunk = lighten(trunk, 0.08)
        b = 0.72 - (v - trunk_start) * 0.35
        if f == "right": b -= 0.18
        return scale(trunk, max(0.25, b))

def _path(c, base, f, u, v, e, px, py, h, fr):
    hi = (215, 202, 192)
    lo = (190, 180, 172)
    n = _sn(px * 0.12, py * 0.12, 300)
    pc = lerp(lo, hi, n)
    if _h(px*2, py*2, 305) > 0.9:
        pc = lighten(pc, 0.1)
    return scale(pc, face_bright(f, u, v, e, h))

def _deep_water(c, base, f, u, v, e, px, py, h, fr):
    if f == "top":
        deep = (10, 55, 145)
        mid = (20, 95, 190)
        wc = lerp(deep, mid, max(0, 1.0 - e * 1.2))
        w = math.sin(px * 0.35 + py * 0.2 + fr * 1.0) * 0.5 + 0.5
        wc = lighten(wc, w * 0.14)
        sp = math.sin(px * 0.9 + fr * 1.6) * math.cos(py * 0.7 + fr * 1.1)
        if sp > 0.78:
            wc = lighten(wc, (sp - 0.78) * 1.5)
        return scale(wc, face_bright(f, u, v, e, h))
    wc = lerp((6, 28, 85), (12, 48, 115), 1.0 - v)
    return scale(wc, face_bright(f, u, v, e, h))

def _snow(c, base, f, u, v, e, px, py, h, fr):
    n = _sn(px * 0.08, py * 0.08, 800)
    sc = lerp((230, 235, 248), (250, 252, 255), n)
    if _sn(px * 0.06, py * 0.06, 810) < 0.35:
        sc = lerp(sc, (210, 220, 242), 0.25)
    if _h(px, py, 820) > 0.95:
        sc = (255, 255, 255)
    return scale(sc, face_bright(f, u, v, e, h))

def _ice(c, base, f, u, v, e, px, py, h, fr):
    ic = lerp((160, 218, 250), (188, 238, 255), _sn(px * 0.08, py * 0.08, 900))
    for off in [0, 7]:
        if (px + py + off) % 10 == 0 and _h(px, py, 910+off) > 0.45:
            ic = lighten(ic, 0.22)
    if f == "top" and u < 0.35 and v < 0.35:
        ic = lighten(ic, (1.0 - u/0.35) * (1.0 - v/0.35) * 0.18)
    return scale(ic, face_bright(f, u, v, e, h))

def _lava(c, base, f, u, v, e, px, py, h, fr):
    crust = (32, 10, 5)
    hot = (255, 220, 60)
    warm = (255, 130, 20)
    if f == "top":
        n = fbm(px * 0.1 + fr * 0.35, py * 0.1 + fr * 0.15, 2, 1000)
        frac = n * 2.5
        cd = abs(frac - round(frac))
        if cd < 0.14:
            t = 1.0 - cd / 0.14
            lc = lerp(warm, hot, t)
        elif cd < 0.28:
            t = (0.28 - cd) / 0.14
            lc = lerp(crust, warm, t * 0.4)
        else:
            lc = lerp(crust, darken(crust, 0.25), _h(px, py, 1010))
        return scale(lc, face_bright(f, u, v, e, h))
    glow = max(0, 1.0 - v * 1.8)
    lc = lerp((22, 8, 3), warm, glow * 0.5)
    return scale(lc, face_bright(f, u, v, e, h))

def _mountain(c, base, f, u, v, e, px, py, h, fr):
    if f == "top":
        rock = lerp((130, 122, 110), (158, 150, 138), _sn(px*0.12, py*0.12, 1100))
        if v < 0.3:
            snow = lerp((228, 234, 245), (248, 250, 255), _h(px, py, 1110))
            rock = lerp(rock, snow, (0.3 - v) / 0.3 * 0.8)
        if _h(px, py, 1120) > 0.88:
            rock = darken(rock, 0.1)
        return scale(rock, face_bright(f, u, v, e, h))
    # Layered strata
    stratum = py // 5
    sn = _h(stratum, 0, 1130)
    rock = lerp((88, 82, 72), (132, 126, 116), sn)
    d = (_h(px, py, 1140) - 0.5) * 14
    rock = rgb(rock[0]+d, rock[1]+d, rock[2]+d)
    if py % 5 == 0:
        rock = darken(rock, 0.18)
    rock = lighten(rock, (1.0 - v) * 0.08)
    return scale(rock, face_bright(f, u, v, e, h))

def _fence(c, base, f, u, v, e, px, py, h, fr):
    wc = lerp((135, 105, 75), (175, 145, 110), _sn(px * 0.18, py * 0.5, 1200))
    if _h(px, py, 1210) > 0.85:
        wc = darken(wc, 0.12)
    return scale(wc, face_bright(f, u, v, e, h))

def _metal(c, base, f, u, v, e, px, py, h, fr):
    mc = lerp((108, 125, 140), (135, 152, 165), _sn(px * 0.08, py * 0.35, 1300))
    if f == "top" and u < 0.4 and v < 0.4:
        mc = lighten(mc, (1-u/0.4)*(1-v/0.4) * 0.2)
    return scale(mc, face_bright(f, u, v, e, h))

def _glass(c, base, f, u, v, e, px, py, h, fr):
    gc = lerp((118, 212, 232), (148, 232, 248), _sn(px*0.06, py*0.06, 1400))
    diag = (px * 0.6 + py * 0.4) % 14
    if 3 < diag < 6:
        gc = lighten(gc, 0.18)
    if f == "top" and 0.15 < u < 0.35 and 0.15 < v < 0.35:
        gc = lighten(gc, 0.22)
    return scale(gc, face_bright(f, u, v, e, h))

def _energy(c, base, f, u, v, e, px, py, h, fr):
    pulse = math.sin(fr * 1.6 + px * 0.25 + py * 0.25) * 0.5 + 0.5
    ec = lerp((130, 55, 175), (180, 100, 225), pulse)
    if f == "top":
        hx = (px * 2 + py) % 7
        hy = (py * 2 - px) % 7
        if hx == 0 or hy == 0:
            ec = lighten(ec, 0.3 + pulse * 0.15)
    else:
        streak = math.sin(px * 0.7 + fr * 2.2) * 0.5 + 0.5
        if streak > 0.65:
            ec = lighten(ec, (streak - 0.65) * 0.6)
    return scale(ec, face_bright(f, u, v, e, h))

def _circuit(c, base, f, u, v, e, px, py, h, fr):
    pcb = (5, 65, 52)
    cc = rgb(pcb[0] + (_h(px,py,1600)-0.5)*10,
             pcb[1] + (_h(px,py,1600)-0.5)*10,
             pcb[2] + (_h(px,py,1600)-0.5)*10)
    on_h = py % 5 == 0
    on_v = px % 6 == 0
    if on_h and on_v:
        cc = (50, 230, 170)
    elif on_h or on_v:
        cc = lerp((15, 120, 90), (25, 150, 118), _h(px+py, 0, 1610))
    return scale(cc, face_bright(f, u, v, e, h))

def _void(c, base, f, u, v, e, px, py, h, fr):
    vc = (6, 6, 14)
    neb = _sn(px * 0.07, py * 0.07, 1700)
    if neb > 0.58:
        vc = lerp(vc, (28, 15, 50), (neb - 0.58) * 1.2)
    star = _h(px, py, 1710)
    if star > 0.965:
        vc = lerp((180, 180, 220), (255, 255, 255), (star - 0.965) / 0.035)
    elif star > 0.95:
        vc = (90, 100, 140)
    return scale(vc, face_bright(f, u, v, e, h))

def _toxic(c, base, f, u, v, e, px, py, h, fr):
    if f == "top":
        dark = (45, 125, 5)
        bright = (100, 225, 15)
        n = _sn(px * 0.12 + fr * 0.2, py * 0.12 + fr * 0.15, 1800)
        tc = lerp(dark, bright, n)
        bub = math.sin(px * 1.3 + fr * 2.0) * math.cos(py * 1.0 + fr * 1.4)
        if bub > 0.55:
            tc = lighten(tc, (bub - 0.55) * 0.8)
        return scale(tc, face_bright(f, u, v, e, h))
    glow = max(0, 1.0 - v * 2.0)
    tc = lerp((25, 60, 5), (70, 175, 10), glow * 0.55)
    return scale(tc, face_bright(f, u, v, e, h))

def _plasma(c, base, f, u, v, e, px, py, h, fr):
    s = fr * 0.25
    r = 140 + 115 * math.sin(2*math.pi*(s + px*0.06 + py*0.03))
    g = 100 + 100 * math.sin(2*math.pi*(s + 0.33 + px*0.04))
    b = 140 + 115 * math.sin(2*math.pi*(s + 0.66 + py*0.05))
    pc = rgb(r, g, b)
    sw = _sn(px * 0.1 + fr * 0.35, py * 0.1, 1900)
    pc = lighten(pc, sw * 0.18)
    return scale(pc, face_bright(f, u, v, e, h))

def _flowers(c, base, f, u, v, e, px, py, h, fr):
    n = _sn(px * 0.07, py * 0.07, 2000)
    n = round(n * 3) / 3
    fc = lerp((42, 140, 40), (82, 185, 52), n)
    # Big bold flower dots - 3x3 cells
    cx_f, cy_f = px // 3, py // 3
    if _h(cx_f, cy_f, 2010) > 0.42:
        lx, ly = px % 3, py % 3
        cs = _h(cx_f, cy_f, 2020)
        is_center = (lx == 1 and ly == 1)
        is_petal = (lx == 1 or ly == 1) and not is_center
        if is_center:
            if cs < 0.22:   fc = (240, 55, 48)    # Red
            elif cs < 0.44: fc = (255, 230, 42)   # Yellow
            elif cs < 0.66: fc = (70, 90, 235)    # Blue
            elif cs < 0.88: fc = (232, 72, 210)   # Purple
            else:           fc = (252, 252, 252)   # White
        elif is_petal:
            if cs < 0.22:   fc = (200, 48, 42)
            elif cs < 0.44: fc = (230, 205, 38)
            elif cs < 0.66: fc = (55, 72, 198)
            elif cs < 0.88: fc = (195, 58, 175)
            else:           fc = (225, 225, 225)
    return scale(fc, face_bright(f, u, v, e, h))

def _dirt(c, base, f, u, v, e, px, py, h, fr):
    hi = (148, 115, 88)
    lo = (118, 88, 65)
    n = _sn(px * 0.1, py * 0.1, 2100)
    dc = lerp(lo, hi, n)
    p = _h(px*2, py*2, 2110)
    if p > 0.9: dc = lighten(dc, 0.14)
    elif p < 0.06: dc = darken(dc, 0.12)
    return scale(dc, face_bright(f, u, v, e, h))

def _cobblestone(c, base, f, u, v, e, px, py, h, fr):
    row = py // 5
    off = (row % 2) * 4
    col = (px + off) // 7
    lx = (px + off) % 7
    ly = py % 5
    if lx == 0 or ly == 0:
        return scale(lerp((98, 95, 90), (112, 108, 102), _h(px, py, 2200)),
                     face_bright(f, u, v, e, h) * 0.82)
    sn = _h(col, row, 2210)
    sc = lerp((132, 138, 148), (162, 168, 176), sn)
    # Rounded stone
    cx_s = (lx - 3.5) / 3.5
    cy_s = (ly - 2.5) / 2.5
    dist = cx_s*cx_s + cy_s*cy_s
    if dist < 0.4:
        sc = lighten(sc, (0.4 - dist) * 0.15)
    return scale(sc, face_bright(f, u, v, e, h))

def _marsh(c, base, f, u, v, e, px, py, h, fr):
    mud = (78, 102, 48)
    murk = (48, 72, 32)
    n = _sn(px * 0.1, py * 0.1, 2300)
    mc = lerp(murk, mud, n)
    water = _sn(px * 0.07, py * 0.07, 2310)
    if water > 0.55:
        pool = lerp((35, 65, 40), (48, 88, 50), _h(px, py, 2315))
        rip = math.sin(px * 0.45 + py * 0.3 + fr * 1.2) * 0.5 + 0.5
        pool = lighten(pool, rip * 0.12)
        mc = lerp(mc, pool, min(1, (water - 0.55) * 2.5))
    if _h(px, py//2, 2320) > 0.92:
        mc = lerp(mc, (95, 138, 45), 0.6)
    return scale(mc, face_bright(f, u, v, e, h))

def _crystal(c, base, f, u, v, e, px, py, h, fr):
    base_c = (130, 88, 210)
    light_c = (200, 170, 250)
    bright_c = (240, 225, 255)
    n = _sn(px * 0.15 + fr * 0.18, py * 0.15, 2400)
    facet = int(n * 4) / 4.0
    rem = abs(n - facet - 0.125)
    if rem < 0.045:
        cc = lighten(bright_c, 0.25)
    else:
        cc = lerp(base_c, light_c, facet)
    sp = math.sin(px * 1.0 + fr * 2.6) * math.cos(py * 0.75 + fr * 1.9)
    if sp > 0.55:
        cc = lighten(cc, min(0.7, (sp - 0.55) * 2.2))
    b = face_bright(f, u, v, e, h)
    if f == "top" and e > 0.3:
        b += e * 0.06
    if f != "top":
        glow = max(0, 1.0 - v * 1.4)
        cc = lighten(cc, glow * 0.1)
    return scale(cc, b)

def _coral(c, base, f, u, v, e, px, py, h, fr):
    warm = (248, 118, 68)
    pink = (238, 88, 102)
    n = _sn(px * 0.14, py * 0.14, 2500)
    cc = lerp(warm, pink, n)
    br = _sn(px * 0.22, py * 0.12, 2510)
    if br > 0.62:
        cc = lighten(cc, (br - 0.62) * 0.35)
    elif br < 0.28:
        cc = darken(cc, (0.28 - br) * 0.18)
    sway = math.sin(px * 0.28 + fr * 0.85) * 0.5 + 0.5
    cc = lighten(cc, sway * 0.08)
    return scale(cc, face_bright(f, u, v, e, h))

def _ruins(c, base, f, u, v, e, px, py, h, fr):
    warm = (138, 125, 105)
    cool = (118, 112, 100)
    if f == "top":
        n = _sn(px * 0.1, py * 0.1, 2600)
        rc = lerp(cool, warm, n)
        if _h(px//3, py//3, 2610) > 0.78:
            rc = darken(rc, 0.28)
            if _h(px, py, 2615) > 0.5:
                rc = lerp(rc, (55, 78, 38), 0.35)
        crack = _sn(px * 0.2, py * 0.2, 2620)
        if abs(crack * 3 - round(crack * 3)) < 0.05:
            rc = darken(rc, 0.22)
        return scale(rc, face_bright(f, u, v, e, h))
    bh, bw = 4, 6
    row = py // bh
    col = (px + (row%2)*(bw//2)) // bw
    lx, ly = (px + (row%2)*(bw//2)) % bw, py % bh
    if lx == 0 or ly == 0:
        return scale(lerp((68, 62, 52), (82, 75, 65), _h(px, py, 2630)),
                     face_bright(f, u, v, e, h))
    bc = lerp(cool, warm, _h(col, row, 2640))
    if _h(col, row, 2650) > 0.82:
        bc = darken(bc, 0.28)
    d = (_h(px, py, 2655) - 0.5) * 12
    bc = rgb(bc[0]+d, bc[1]+d, bc[2]+d)
    return scale(bc, face_bright(f, u, v, e, h))

def _moss(c, base, f, u, v, e, px, py, h, fr):
    bright = (98, 162, 48)
    dark = (58, 112, 28)
    n = _sn(px * 0.12, py * 0.12, 2700)
    mc = lerp(dark, bright, n)
    cl_n = _sn(px * 0.09, py * 0.09, 2710)
    if cl_n > 0.62:
        mc = lighten(mc, (cl_n - 0.62) * 0.3)
    elif cl_n < 0.28:
        mc = lerp(mc, (108, 98, 82), (0.28 - cl_n) * 0.45)
    return scale(mc, face_bright(f, u, v, e, h))

def _obsidian(c, base, f, u, v, e, px, py, h, fr):
    """Glossy dark volcanic glass — imposing, reflective, sharp."""
    dark = (18, 18, 38)
    mid = (35, 30, 55)
    gloss = (90, 80, 130)
    if f == "top":
        n = _sn(px * 0.15, py * 0.15, 2800)
        oc = lerp(dark, mid, n * 0.6)
        # Sharp glassy reflections
        refl = math.sin(px * 0.6 + py * 0.3) * math.cos(px * 0.2 - py * 0.5)
        if refl > 0.4:
            oc = lerp(oc, gloss, (refl - 0.4) * 1.2)
        # Bright specular highlight — upper left
        if u < 0.3 and v < 0.3:
            sp = (1 - u/0.3) * (1 - v/0.3)
            oc = lighten(oc, sp * 0.35)
        # Fracture lines
        crack = _sn(px * 0.22, py * 0.22, 2810)
        if abs(crack * 4 - round(crack * 4)) < 0.04:
            oc = lighten(oc, 0.25)
        return scale(oc, face_bright(f, u, v, e, h))
    # Side faces: deep dark with purple sheen
    oc = lerp((10, 10, 22), (25, 20, 45), 1.0 - v)
    sheen = math.sin(py * 0.4 + px * 0.2) * 0.5 + 0.5
    if sheen > 0.7:
        oc = lighten(oc, (sheen - 0.7) * 0.4)
    # Vertical fracture glints
    if _h(px, py // 3, 2820) > 0.92:
        oc = lighten(oc, 0.3)
    return scale(oc, face_bright(f, u, v, e, h))

def _cliff(c, base, f, u, v, e, px, py, h, fr):
    """Towering cliff face — the tallest, most imposing terrain."""
    if f == "top":
        # Rough rocky top with sparse vegetation
        rock = lerp((105, 92, 72), (125, 110, 88), _sn(px * 0.1, py * 0.1, 2900))
        if _h(px//3, py//3, 2910) > 0.82:
            rock = lerp(rock, (58, 80, 42), 0.4)  # sparse moss
        crack = _sn(px * 0.18, py * 0.18, 2920)
        if abs(crack * 3 - round(crack * 3)) < 0.06:
            rock = darken(rock, 0.25)
        return scale(rock, face_bright(f, u, v, e, h))
    # Side: layered stratified rock — most detail since it's so tall
    stratum = py // 4
    sn = _h(stratum, 0, 2930)
    warm = lerp((82, 72, 55), (115, 100, 78), sn)
    cool = lerp((72, 68, 62), (98, 92, 82), sn)
    rock = lerp(warm, cool, _sn(px * 0.08, stratum * 0.5, 2935))
    # Horizontal strata lines
    if py % 4 == 0:
        rock = darken(rock, 0.22)
    # Vertical cracks
    cx_v = _sn(px * 0.2, py * 0.02, 2940)
    if abs(cx_v * 3 - round(cx_v * 3)) < 0.035:
        rock = darken(rock, 0.3)
    # Overhangs / shadows at top
    if v < 0.08:
        rock = lighten(rock, (0.08 - v) / 0.08 * 0.15)
    # Darkening toward base (imposing shadow)
    rock = darken(rock, v * 0.2)
    d = (_h(px, py, 2945) - 0.5) * 10
    rock = rgb(rock[0]+d, rock[1]+d, rock[2]+d)
    return scale(rock, face_bright(f, u, v, e, h))

def _ash(c, base, f, u, v, e, px, py, h, fr):
    """Volcanic ash — desolate flat ground, gray and bleak."""
    dark = (85, 82, 78)
    light = (118, 115, 108)
    n = _sn(px * 0.08, py * 0.08, 3000)
    ac = lerp(dark, light, n)
    # Ashy texture — fine grit
    grit = _h(px * 3, py * 3, 3010)
    if grit > 0.85:
        ac = lighten(ac, 0.1)
    elif grit < 0.1:
        ac = darken(ac, 0.08)
    # Scattered embers
    ember = _h(px, py, 3020)
    if ember > 0.96:
        pulse = math.sin(fr * 2.0 + px * 0.5) * 0.5 + 0.5
        ac = lerp(ac, (200, 80, 20), 0.3 + pulse * 0.2)
    return scale(ac, face_bright(f, u, v, e, h))

def _thorns(c, base, f, u, v, e, px, py, h, fr):
    """Dense thorny brambles — dark, tangled, dangerous."""
    bark = (48, 38, 18)
    thorn = (72, 55, 22)
    leaf_dark = (28, 52, 15)
    leaf = (38, 68, 22)
    if f == "top":
        # Tangled canopy
        n = _sn(px * 0.2, py * 0.2, 3100)
        tc = lerp(leaf_dark, leaf, n)
        # Thorn points poking through
        tp = _h(px // 2, py // 2, 3110)
        if tp > 0.72:
            tc = lerp(tc, thorn, (tp - 0.72) * 2.5)
            if tp > 0.9:
                tc = lighten(tc, 0.15)  # thorn tip glint
        # Dark tangled shadows
        shadow = _sn(px * 0.15, py * 0.15, 3115)
        if shadow < 0.3:
            tc = darken(tc, (0.3 - shadow) * 0.4)
        return scale(tc, face_bright(f, u, v, e, h))
    # Side faces: twisted branches and thorns
    branch_v = _sn(px * 0.25, py * 0.08, 3120)
    is_branch = abs(branch_v * 5 - round(branch_v * 5)) < 0.12
    if is_branch:
        tc = lerp(bark, thorn, _h(px, py, 3125))
        # Thorn spikes on branches
        if _h(px * 2, py * 2, 3130) > 0.8:
            tc = lighten(tc, 0.2)
    else:
        tc = lerp(leaf_dark, darken(leaf, 0.15), _sn(px * 0.18, py * 0.18, 3135))
    # Darker at base
    tc = darken(tc, v * 0.15)
    return scale(tc, face_bright(f, u, v, e, h))

def _basalt(c, base, f, u, v, e, px, py, h, fr):
    """Dark columnar basalt — tall hexagonal pillars, geological & imposing."""
    dark = (32, 32, 48)
    mid = (52, 50, 65)
    light = (72, 68, 82)
    if f == "top":
        # Hexagonal column tops
        # Simple hex grid approximation
        hx = px / 6.0
        hy = py / 6.0
        # Offset rows
        row = int(hy)
        col_off = 0.5 if row % 2 else 0.0
        col = int(hx + col_off)
        # Distance to hex center
        cx_h = (col - col_off) * 6.0 + 3
        cy_h = row * 6.0 + 3
        dx = abs(px - cx_h) / 3.0
        dy = abs(py - cy_h) / 3.0
        hex_edge = dx + dy * 0.5
        if hex_edge > 0.85:
            # Column gap / mortar
            bc = darken(dark, 0.3)
        else:
            sn = _h(col, row, 3200)
            bc = lerp(dark, mid, sn)
            # Center highlight
            if hex_edge < 0.4:
                bc = lighten(bc, (0.4 - hex_edge) * 0.2)
        return scale(bc, face_bright(f, u, v, e, h))
    # Side: vertical columns with subtle variation
    col_id = px // 4
    col_n = _h(col_id, 0, 3210)
    bc = lerp(dark, mid, col_n * 0.7)
    # Column edges
    if px % 4 == 0:
        bc = darken(bc, 0.25)
    # Vertical cracks within columns
    if _h(px, py // 5, 3220) > 0.88:
        bc = darken(bc, 0.15)
    # Slight horizontal banding
    if py % 7 == 0:
        bc = darken(bc, 0.1)
    # Darken toward bottom
    bc = darken(bc, v * 0.15)
    return scale(bc, face_bright(f, u, v, e, h))

def _gravel(c, base, f, u, v, e, px, py, h, fr):
    """Loose rocky ground — rough, uneven pebble texture."""
    light = (185, 175, 158)
    mid = (155, 148, 135)
    dark = (125, 118, 108)
    # Pebble grid
    cx_p = px // 3
    cy_p = py // 3
    pn = _h(cx_p, cy_p, 3300)
    if pn > 0.6:
        gc = lerp(mid, light, (pn - 0.6) * 2.5)
    elif pn < 0.3:
        gc = lerp(dark, mid, pn / 0.3)
    else:
        gc = mid
    # Rounded pebble shading
    lx = (px % 3 - 1.0) / 1.5
    ly = (py % 3 - 1.0) / 1.5
    dist = lx * lx + ly * ly
    if dist < 0.5:
        gc = lighten(gc, (0.5 - dist) * 0.12)
    elif dist > 1.2:
        gc = darken(gc, 0.1)  # gap shadow
    # Occasional darker stones
    if _h(cx_p * 3, cy_p * 7, 3310) > 0.88:
        gc = darken(gc, 0.18)
    return scale(gc, face_bright(f, u, v, e, h))

RENDERERS = {
    0: _grass, 1: _water, 2: _sand, 3: _stone, 4: _wall, 5: _tree,
    6: _path, 7: _deep_water, 8: _snow, 9: _ice, 10: _lava, 11: _mountain,
    12: _fence, 13: _metal, 14: _glass, 15: _energy, 16: _circuit,
    17: _void, 18: _toxic, 19: _plasma, 20: _flowers, 21: _dirt,
    22: _cobblestone, 23: _marsh, 24: _crystal, 25: _coral, 26: _ruins,
    27: _moss, 28: _obsidian, 29: _cliff, 30: _ash, 31: _thorns,
    32: _basalt, 33: _gravel,
}

# ── Render ───────────────────────────────────────────────────────────

def render_tile(tid, base_rgb, h, frame):
    cell = Image.new("RGBA", (CELL_W, CELL_H), (0, 0, 0, 0))
    pixels = cell.load()
    renderer = RENDERERS.get(tid)
    for py in range(CELL_H):
        for px in range(CELL_W):
            r = classify(px, py, h)
            if r is None:
                continue
            face, u, v, edge = r
            if renderer:
                color = renderer(None, base_rgb, face, u, v, edge, px, py, h, frame)
            else:
                color = scale(base_rgb, face_bright(face, u, v, edge, h))
            pixels[px, py] = (*color, 255)
    return cell

def main():
    img = Image.new("RGBA", (NUM_TILES * CELL_W, CELL_H * ANIM_FRAMES), (0,0,0,0))
    for frame in range(ANIM_FRAMES):
        for tid, name, a, elev in TILES:
            cell = render_tile(tid, argb(a), elev, frame)
            img.paste(cell, (tid * CELL_W, frame * CELL_H))
    import os
    out = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "sprites")
    os.makedirs(out, exist_ok=True)
    p = os.path.join(out, "tiles.png")
    img.save(p)
    print(f"Generated {p} ({img.width}x{img.height})")

if __name__ == "__main__":
    main()
