#!/usr/bin/env python3
"""Generate sprites/tiles.png — 34-column x 4-row isometric tileset.

Cartoony style: walkable ground is very flat/plain for gameplay clarity.
Non-walkable obstacles are tall and imposing. 80x112 pixels per cell.
"""

import math
from PIL import Image

CELL_W, CELL_H = 80, 112
HW, HH = 40, 20
CX, BASE_Y = 40, 92
NUM_TILES, ANIM_FRAMES = 34, 4

# Walkable ground tiles have elevation 0 — simple flat colors.
# Non-walkable obstacles have HIGH elevations — tall and imposing.
TILES = [
    # Walkable ground (h=0) — plain and clean
    (0,  "Grass",       0xFF4CAF50,  0),
    (2,  "Sand",        0xFFF5DEB3,  0),
    (3,  "Stone",       0xFF9E9E9E,  0),
    (6,  "Path",        0xFFD7CCC8,  0),
    (8,  "Snow",        0xFFFAFAFA,  0),
    (9,  "Ice",         0xFFB3E5FC,  0),
    (13, "Metal",       0xFF78909C,  0),
    (14, "Glass",       0xFF80DEEA,  0),
    (16, "Circuit",     0xFF004D40,  0),
    (20, "Flowers",     0xFF66BB6A,  0),
    (21, "Dirt",        0xFF8D6E63,  0),
    (22, "Cobblestone", 0xFF90A4AE,  0),
    (23, "Marsh",       0xFF5D7A3E,  0),
    (27, "Moss",        0xFF689F38,  0),
    (30, "Ash",         0xFF6B6B6B,  0),
    (33, "Gravel",      0xFFB0A899,  0),
    # Non-walkable obstacles — TALL and imposing
    (1,  "Water",       0xFF2196F3, 14),
    (4,  "Wall",        0xFF5D4037, 50),
    (5,  "Tree",        0xFF2E7D32, 52),
    (7,  "DeepWater",   0xFF1565C0, 18),
    (10, "Lava",        0xFFFF5722, 16),
    (11, "Mountain",    0xFF757575, 68),
    (12, "Fence",       0xFFA1887F, 30),
    (15, "EnergyField", 0xFFAB47BC, 44),
    (17, "Void",        0xFF0A0A12, 32),
    (18, "Toxic",       0xFF76FF03, 16),
    (19, "Plasma",      0xFFFF4081, 24),
    (24, "Crystal",     0xFF7E57C2, 50),
    (25, "Coral",       0xFFFF7043, 28),
    (26, "Ruins",       0xFF7E7360, 42),
    (28, "Obsidian",    0xFF1A1A2E, 50),
    (29, "Cliff",       0xFF5C5040, 72),
    (31, "Thorns",      0xFF3B2F18, 40),
    (32, "Basalt",      0xFF2D2D3D, 58),
]

# ── Color helpers ─────────────────────────────────────────────────

def cl(v): return max(0, min(255, int(round(v))))
def rgb(r, g, b): return (cl(r), cl(g), cl(b))
def darken(c, f): return rgb(c[0]*(1-f), c[1]*(1-f), c[2]*(1-f))
def lighten(c, f): return rgb(c[0]+(255-c[0])*f, c[1]+(255-c[1])*f, c[2]+(255-c[2])*f)
def lerp(a, b, t):
    t = max(0.0, min(1.0, t))
    return rgb(a[0]+(b[0]-a[0])*t, a[1]+(b[1]-a[1])*t, a[2]+(b[2]-a[2])*t)
def scale(c, s): return rgb(c[0]*s, c[1]*s, c[2]*s)
def argb(v): return ((v>>16)&0xFF, (v>>8)&0xFF, v&0xFF)

# ── Hash / noise ──────────────────────────────────────────────────

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

# ── Geometry ──────────────────────────────────────────────────────

def classify(px, py, h):
    if h == 0:
        dx, dy = abs(px - CX), abs(py - BASE_Y)
        d = dx/HW + dy/HH
        if d > 1.0: return None
        return ("top", px/79.0, (py - BASE_Y + HH)/(2*HH - 1), 1.0 - d)

    top_cy = BASE_Y - h
    dx, dy = abs(px - CX), abs(py - top_cy)
    d = dx/HW + dy/HH
    if d <= 1.0:
        return ("top", px/79.0, (py - top_cy + HH)/(2*HH - 1), 1.0 - d)

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

# ── Cell-shaded face brightness ──────────────────────────────────

def face_bright(face, u, v, edge, h):
    if face == "top":
        if h <= 4:
            return 1.0
        return 1.05 - (u + v - 1.0) * 0.08
    elif face == "left":
        return 0.70 - v * 0.10
    else:
        return 0.45 - v * 0.08


# ══════════════════════════════════════════════════════════════════
# WALKABLE GROUND — extremely plain, just flat color
# ══════════════════════════════════════════════════════════════════

def _flat(c, base, f, u, v, e, px, py, h, fr):
    """Soft flat walkable tile — multi-octave noise, radial glow, color shift."""
    # Multi-octave noise for organic feel
    n1 = _sn(px * 0.025, py * 0.025, fr * 10000)
    n2 = _sn(px * 0.06, py * 0.06, fr * 10000 + 500)
    nv = n1 * 0.65 + n2 * 0.35
    # Gentle radial brightness — brighter center, softer edges
    radial = 0.96 + e * 0.08
    # Subtle warm/cool color shift across the surface
    warmth = _sn(px * 0.035 + 100, py * 0.035 + 100, fr * 10000 + 900)
    r_adj = 1.0 + (warmth - 0.5) * 0.06
    b_adj = 1.0 - (warmth - 0.5) * 0.04
    bright = (0.92 + nv * 0.12) * radial
    return rgb(base[0] * bright * r_adj, base[1] * bright, base[2] * bright * b_adj)

def _grass(c, base, f, u, v, e, px, py, h, fr):
    return _flat(c, (92, 155, 78), f, u, v, e, px, py, h, fr)

def _sand(c, base, f, u, v, e, px, py, h, fr):
    return _flat(c, (222, 205, 165), f, u, v, e, px, py, h, fr)

def _stone(c, base, f, u, v, e, px, py, h, fr):
    return _flat(c, (158, 154, 148), f, u, v, e, px, py, h, fr)

def _path(c, base, f, u, v, e, px, py, h, fr):
    return _flat(c, (198, 188, 176), f, u, v, e, px, py, h, fr)

def _snow(c, base, f, u, v, e, px, py, h, fr):
    return _flat(c, (236, 238, 244), f, u, v, e, px, py, h, fr)

def _ice(c, base, f, u, v, e, px, py, h, fr):
    return _flat(c, (178, 212, 232), f, u, v, e, px, py, h, fr)

def _metal(c, base, f, u, v, e, px, py, h, fr):
    return _flat(c, (140, 150, 160), f, u, v, e, px, py, h, fr)

def _glass(c, base, f, u, v, e, px, py, h, fr):
    return _flat(c, (155, 210, 222), f, u, v, e, px, py, h, fr)

def _circuit(c, base, f, u, v, e, px, py, h, fr):
    n1 = _sn(px * 0.025, py * 0.025, fr * 10000)
    n2 = _sn(px * 0.06, py * 0.06, fr * 10000 + 500)
    nv = n1 * 0.65 + n2 * 0.35
    radial = 0.96 + e * 0.08
    cc = scale((18, 72, 60), (0.92 + nv * 0.12) * radial)
    # Subtle trace grid for identity
    if py % 10 < 1 or px % 12 < 1:
        cc = lerp(cc, (28, 115, 92), 0.6)
    return cc

def _flowers(c, base, f, u, v, e, px, py, h, fr):
    n1 = _sn(px * 0.025, py * 0.025, fr * 10000)
    n2 = _sn(px * 0.06, py * 0.06, fr * 10000 + 500)
    nv = n1 * 0.65 + n2 * 0.35
    radial = 0.96 + e * 0.08
    fc = scale((82, 150, 62), (0.92 + nv * 0.12) * radial)
    # Sparse small flower dots for identity
    cx_f, cy_f = px // 12, py // 12
    if _h(cx_f, cy_f, fr * 10000 + 2010) > 0.6:
        lx, ly = px % 12, py % 12
        if 5 <= lx <= 6 and 5 <= ly <= 6:
            cs = _h(cx_f, cy_f, fr * 10000 + 2020)
            if cs < 0.33:   fc = (218, 105, 95)
            elif cs < 0.66: fc = (232, 215, 95)
            else:           fc = (212, 115, 192)
    return fc

def _dirt(c, base, f, u, v, e, px, py, h, fr):
    return _flat(c, (145, 118, 88), f, u, v, e, px, py, h, fr)

def _cobblestone(c, base, f, u, v, e, px, py, h, fr):
    return _flat(c, (155, 155, 158), f, u, v, e, px, py, h, fr)

def _marsh(c, base, f, u, v, e, px, py, h, fr):
    return _flat(c, (72, 88, 52), f, u, v, e, px, py, h, fr)

def _moss(c, base, f, u, v, e, px, py, h, fr):
    return _flat(c, (88, 142, 58), f, u, v, e, px, py, h, fr)

def _ash(c, base, f, u, v, e, px, py, h, fr):
    return _flat(c, (108, 104, 98), f, u, v, e, px, py, h, fr)

def _gravel(c, base, f, u, v, e, px, py, h, fr):
    return _flat(c, (158, 152, 142), f, u, v, e, px, py, h, fr)


# ══════════════════════════════════════════════════════════════════
# NON-WALKABLE OBSTACLES — tall, imposing, visually interesting
# ══════════════════════════════════════════════════════════════════

def _water(c, base, f, u, v, e, px, py, h, fr):
    if f == "top":
        wc = (35, 125, 215)
        w = math.sin(px * 0.15 + py * 0.08 + fr * 1.8)
        if w > 0.3:
            wc = lighten(wc, (w - 0.3) * 0.28)
        sp = math.sin(px * 0.4 + fr * 2.5) * math.cos(py * 0.3 + fr * 1.7)
        if sp > 0.55:
            wc = lighten(wc, min(0.55, (sp - 0.55) * 1.6))
        if e < 0.18:
            wc = lighten(wc, (0.18 - e) / 0.18 * 0.35)
        return scale(wc, face_bright(f, u, v, e, h))
    return scale(lerp((8, 38, 105), (20, 68, 155), 1.0 - v), face_bright(f, u, v, e, h))

def _wall(c, base, f, u, v, e, px, py, h, fr):
    if f == "top":
        return scale((115, 90, 72), face_bright(f, u, v, e, h))

    # Clean bold bricks
    bh, bw = 10, 16
    row = py // bh
    col = (px + (row % 2) * (bw // 2)) // bw
    lx = (px + (row % 2) * (bw // 2)) % bw
    ly = py % bh

    if lx <= 1 or ly <= 1:
        return scale((50, 40, 32), face_bright(f, u, v, e, h))

    seed = _h(col, row, 420)
    if seed > 0.65:
        bc = (112, 78, 58)
    elif seed > 0.30:
        bc = (95, 65, 48)
    else:
        bc = (80, 55, 40)

    return scale(bc, face_bright(f, u, v, e, h))

def _tree(c, base, f, u, v, e, px, py, h, fr):
    leaf_hi = (88, 212, 55)
    leaf_md = (52, 168, 40)
    leaf_dk = (28, 108, 25)
    bark = (88, 60, 30)
    bark_dk = (52, 35, 15)

    if f == "top":
        dome = min(1.0, e * 2.2)
        if dome > 0.55:
            leaf = leaf_hi
        elif dome > 0.25:
            leaf = leaf_md
        else:
            leaf = leaf_dk
        if _h(px // 5, py // 5, 500) > 0.80:
            leaf = lighten(leaf, 0.16)
        return scale(leaf, max(0.55, 1.0 + dome * 0.08))

    trunk_start = 0.55
    if v < 0.48:
        dome = 1.0 - ((v / 0.48 - 0.3) / 0.5) ** 2
        dome = max(0, min(1, dome))
        leaf = leaf_md if dome > 0.4 else leaf_dk
        if _h(px // 4, py // 4, 510) > 0.78:
            leaf = lighten(leaf, 0.14)
        base_b = 0.68 if f == "left" else 0.42
        return scale(leaf, max(0.28, base_b + dome * 0.15))
    elif v < trunk_start:
        return scale(lerp(leaf_dk, bark, (v - 0.48) / 0.07), 0.52 if f == "left" else 0.38)
    else:
        trunk = bark_dk
        cu = u if f == "left" else 1.0 - u
        if 0.3 < cu < 0.7:
            trunk = bark
        b = 0.68 - (v - trunk_start) * 0.28
        if f == "right": b -= 0.18
        return scale(trunk, max(0.20, b))

def _deep_water(c, base, f, u, v, e, px, py, h, fr):
    if f == "top":
        wc = (15, 58, 150)
        w = math.sin(px * 0.14 + py * 0.07 + fr * 1.0) * 0.5 + 0.5
        wc = lighten(wc, w * 0.15)
        sp = math.sin(px * 0.38 + fr * 1.6) * math.cos(py * 0.28 + fr * 1.1)
        if sp > 0.65:
            wc = lighten(wc, (sp - 0.65) * 1.4)
        return scale(wc, face_bright(f, u, v, e, h))
    return scale(lerp((5, 22, 72), (12, 42, 108), 1.0 - v), face_bright(f, u, v, e, h))

def _lava(c, base, f, u, v, e, px, py, h, fr):
    crust = (38, 14, 6)
    hot = (255, 228, 68)
    warm = (255, 142, 28)
    if f == "top":
        n = _sn(px * 0.04 + fr * 0.3, py * 0.04 + fr * 0.12, 1000)
        frac = n * 2.5
        cd = abs(frac - round(frac))
        if cd < 0.10:
            lc = lerp(warm, hot, 1.0 - cd / 0.10)
        elif cd < 0.20:
            lc = lerp(crust, warm, (0.20 - cd) / 0.10 * 0.35)
        else:
            lc = crust
        if _h(px + fr * 41, py + fr * 67, 1020) > 0.97:
            lc = hot
        return scale(lc, face_bright(f, u, v, e, h))
    glow = max(0, 1.0 - v * 1.5)
    return scale(lerp((22, 8, 3), warm, glow * 0.50), face_bright(f, u, v, e, h))

def _mountain(c, base, f, u, v, e, px, py, h, fr):
    if f == "top":
        rock = (145, 135, 122)
        if v < 0.30:
            return scale(lerp(rock, (242, 248, 255), (0.30 - v) / 0.30), face_bright(f, u, v, e, h))
        return scale(rock, face_bright(f, u, v, e, h))
    stratum = py // 10
    rock = lerp((82, 75, 65), (130, 122, 110), _h(stratum, 0, 1130))
    if py % 10 < 2:
        rock = darken(rock, 0.22)
    return scale(rock, face_bright(f, u, v, e, h))

def _fence(c, base, f, u, v, e, px, py, h, fr):
    wc = (158, 128, 92)
    if py % 6 < 1:
        wc = darken(wc, 0.16)
    return scale(wc, face_bright(f, u, v, e, h))

def _energy(c, base, f, u, v, e, px, py, h, fr):
    pulse = math.sin(fr * 1.6 + px * 0.12 + py * 0.12) * 0.5 + 0.5
    ec = lerp((132, 58, 182), (188, 110, 232), pulse)
    if f == "top":
        hx = (px * 2 + py) % 14
        hy = (py * 2 - px) % 14
        if hx <= 1 or hy <= 1:
            ec = lighten(ec, 0.38 + pulse * 0.15)
    else:
        streak = math.sin(px * 0.30 + fr * 2.2) * 0.5 + 0.5
        if streak > 0.58:
            ec = lighten(ec, (streak - 0.58) * 0.62)
    return scale(ec, face_bright(f, u, v, e, h))

def _void(c, base, f, u, v, e, px, py, h, fr):
    vc = (5, 5, 14)
    cx_v = 40
    cy_v = BASE_Y - h + HH if h > 0 else BASE_Y
    dx = px - cx_v
    dy = (py - cy_v) * 2
    angle = math.atan2(dy, dx)
    dist = math.sqrt(dx*dx + dy*dy)
    spiral = _sn((angle + fr * 0.4 + dist * 0.04) * 1.5, dist * 0.025, 1700)
    if spiral > 0.52:
        vc = lerp(vc, (42, 22, 75), (spiral - 0.52) * 1.8)
    star = _h(px, py, 1710)
    if star > 0.97:
        vc = (255, 255, 255)
    elif star > 0.955:
        vc = (125, 125, 185)
    return scale(vc, face_bright(f, u, v, e, h))

def _toxic(c, base, f, u, v, e, px, py, h, fr):
    if f == "top":
        tc = (68, 185, 15)
        bub = math.sin(px * 0.5 + fr * 2.0) * math.cos(py * 0.4 + fr * 1.4)
        if bub > 0.48:
            tc = lighten(tc, (bub - 0.48) * 0.82)
        return scale(tc, face_bright(f, u, v, e, h))
    glow = max(0, 1.0 - v * 1.8)
    return scale(lerp((22, 58, 5), (58, 155, 10), glow * 0.55), face_bright(f, u, v, e, h))

def _plasma(c, base, f, u, v, e, px, py, h, fr):
    s = fr * 0.25
    r = 148 + 108 * math.sin(2*math.pi*(s + px*0.025 + py*0.012))
    g = 108 + 100 * math.sin(2*math.pi*(s + 0.33 + px*0.016))
    b = 148 + 108 * math.sin(2*math.pi*(s + 0.66 + py*0.020))
    return scale(rgb(r, g, b), face_bright(f, u, v, e, h))

def _crystal(c, base, f, u, v, e, px, py, h, fr):
    base_c = (132, 90, 218)
    light_c = (205, 175, 255)
    facet = _sn(px * 0.065 + fr * 0.09, py * 0.065, 2400)
    facet_id = int(facet * 5) / 5.0
    if abs(facet - facet_id - 0.1) < 0.03:
        cc = (248, 238, 255)
    else:
        cc = lerp(base_c, light_c, _h(int(facet_id * 5), 0, 2405))
    if _h(px + fr * 23, py + fr * 37, 2415) > 0.98:
        cc = (255, 255, 255)
    b = face_bright(f, u, v, e, h)
    if f != "top":
        cc = lighten(cc, max(0, 1.0 - v * 1.3) * 0.12)
    return scale(cc, b)

def _coral(c, base, f, u, v, e, px, py, h, fr):
    cc = (250, 118, 70)
    br = _sn(px * 0.08, py * 0.05, 2510)
    if br > 0.60:
        cc = lighten(cc, 0.22)
    elif br < 0.30:
        cc = lerp(cc, (238, 88, 105), 0.42)
    cc = lighten(cc, math.sin(px * 0.12 + fr * 0.85) * 0.04 + 0.04)
    return scale(cc, face_bright(f, u, v, e, h))

def _ruins(c, base, f, u, v, e, px, py, h, fr):
    if f == "top":
        rc = (135, 125, 108)
        if _h(px//6, py//6, 2610) > 0.76:
            rc = darken(rc, 0.30)
        return scale(rc, face_bright(f, u, v, e, h))
    bh, bw = 8, 12
    row = py // bh
    col = (px + (row%2)*(bw//2)) // bw
    lx, ly = (px + (row%2)*(bw//2)) % bw, py % bh
    if lx <= 1 or ly <= 1:
        return scale((68, 62, 52), face_bright(f, u, v, e, h))
    bc = lerp((112, 108, 95), (138, 130, 115), _h(col, row, 2640))
    if _h(col, row, 2650) > 0.78:
        bc = darken(bc, 0.32)
    return scale(bc, face_bright(f, u, v, e, h))

def _obsidian(c, base, f, u, v, e, px, py, h, fr):
    if f == "top":
        oc = (22, 20, 45)
        refl = math.sin(px * 0.25 + py * 0.12) * math.cos(px * 0.08 - py * 0.20)
        if refl > 0.38:
            oc = lerp(oc, (88, 78, 128), (refl - 0.38) * 1.2)
        if u < 0.28 and v < 0.28:
            oc = lighten(oc, (1 - u/0.28) * (1 - v/0.28) * 0.38)
        return scale(oc, face_bright(f, u, v, e, h))
    oc = lerp((10, 10, 25), (25, 22, 48), 1.0 - v)
    if math.sin(py * 0.16 + px * 0.08) > 0.36:
        oc = lighten(oc, 0.15)
    return scale(oc, face_bright(f, u, v, e, h))

def _cliff(c, base, f, u, v, e, px, py, h, fr):
    if f == "top":
        rock = (110, 98, 78)
        if _h(px//6, py//6, 2910) > 0.78:
            rock = (62, 82, 42)
        return scale(rock, face_bright(f, u, v, e, h))
    stratum = py // 8
    rock = lerp((78, 68, 52), (115, 100, 78), _h(stratum, 0, 2930))
    if py % 8 < 2:
        rock = darken(rock, 0.25)
    rock = darken(rock, v * 0.18)
    return scale(rock, face_bright(f, u, v, e, h))

def _thorns(c, base, f, u, v, e, px, py, h, fr):
    leaf = (35, 65, 22)
    thorn = (75, 58, 24)
    if f == "top":
        tc = leaf
        if _h(px // 4, py // 4, 3110) > 0.68:
            tc = thorn
        if _h(px // 6, py // 6, 3115) < 0.18:
            tc = darken(leaf, 0.28)
        return scale(tc, face_bright(f, u, v, e, h))
    branch = _sn(px * 0.10, py * 0.03, 3120)
    if abs(branch * 5 - round(branch * 5)) < 0.12:
        tc = thorn
    else:
        tc = darken(leaf, 0.18)
    tc = darken(tc, v * 0.15)
    return scale(tc, face_bright(f, u, v, e, h))

def _basalt(c, base, f, u, v, e, px, py, h, fr):
    dark = (32, 32, 50)
    mid = (55, 52, 68)
    if f == "top":
        hx = px / 12.0
        hy = py / 12.0
        row = int(hy)
        col_off = 0.5 if row % 2 else 0.0
        col = int(hx + col_off)
        cx_h = (col - col_off) * 12.0 + 6
        cy_h = row * 12.0 + 6
        hex_edge = abs(px - cx_h) / 6.0 + abs(py - cy_h) / 6.0 * 0.5
        if hex_edge > 0.80:
            bc = darken(dark, 0.32)
        else:
            bc = lerp(dark, mid, _h(col, row, 3200))
            if hex_edge < 0.32:
                bc = lighten(bc, 0.16)
        return scale(bc, face_bright(f, u, v, e, h))
    col_id = px // 8
    bc = lerp(dark, mid, _h(col_id, 0, 3210) * 0.7)
    if px % 8 <= 1:
        bc = darken(bc, 0.30)
    if py % 14 < 2:
        bc = darken(bc, 0.14)
    bc = darken(bc, v * 0.15)
    return scale(bc, face_bright(f, u, v, e, h))


RENDERERS = {
    0: _grass, 1: _water, 2: _sand, 3: _stone, 4: _wall, 5: _tree,
    6: _path, 7: _deep_water, 8: _snow, 9: _ice, 10: _lava, 11: _mountain,
    12: _fence, 13: _metal, 14: _glass, 15: _energy, 16: _circuit,
    17: _void, 18: _toxic, 19: _plasma, 20: _flowers, 21: _dirt,
    22: _cobblestone, 23: _marsh, 24: _crystal, 25: _coral, 26: _ruins,
    27: _moss, 28: _obsidian, 29: _cliff, 30: _ash, 31: _thorns,
    32: _basalt, 33: _gravel,
}

# ── Render ────────────────────────────────────────────────────────

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
