"""Vanilla-style item and ore primitives (16x16), so mod materials sit next to Minecraft's own.

What vanilla does (studied from the 26.3 textures, never copied):
* Ingots fill the cell width (x 0..15) and sit just above centre (rows ~3..12): a long bar lying diagonally, lit top
  face, mid-tone front, dark end; ~8 colours; the outline is the material's own darkest shade, not black.
* Dusts are a rounded heap (x 2..13, y 3..13) of dithered grains in 5-7 tones, darker toward the lower right.
* Ores are host stone plus 6-9 small mineral clusters of 2-6 pixels, each lit top-left (highlight, mid, shade)
  with a dark rim pixel or two on the lower right; clusters stay a pixel off the edges so faces tile cleanly.

Every generator here draws from its own templates and shading rules.
"""
from __future__ import annotations

import math
import random

from .palette import Ramp
from .tex import Tex

TRANSPARENT = (0, 0, 0, 0)


def _poly(points: list[tuple[float, float]], px: float, py: float) -> bool:
    inside = False
    j = len(points) - 1
    for i in range(len(points)):
        xi, yi = points[i]
        xj, yj = points[j]
        if (yi > py) != (yj > py) and px < (xj - xi) * (py - yi) / (yj - yi + 1e-12) + xi:
            inside = not inside
        j = i
    return inside


def _outline(t: Tex, pts: set[tuple[int, int]], color) -> None:
    for (x, y) in list(pts):
        for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            q = (x + ox, y + oy)
            if q not in pts and 0 <= q[0] < 16 and 0 <= q[1] < 16 and t.get(*q)[3] == 0:
                t.px(*q, color)


# --- ingot ---------------------------------------------------------------------------------------------------------------

# Every vanilla ingot shares one silhouette; ours use the same outline so all ingots line up exactly. Row spans.
INGOT_ROWS = {2: (10, 11), 3: (7, 12), 4: (4, 13), 5: (1, 14), 6: (0, 15), 7: (0, 15), 8: (0, 15), 9: (0, 15),
              10: (0, 14), 11: (1, 12), 12: (2, 9), 13: (3, 6)}


def ingot(ramp: Ramp, mark=None) -> Tex:
    """An ingot on the shared vanilla outline (a bar lying diagonally, rising to the right), shaded with our own
    rules: lit top face with a bright ridge along its upper edge, darker front face below the crease line, a lit
    right end, and an outline in the material's own darks (mid-dark up-left, darkest down-right).
    `mark(t, faces)` can stamp a detail afterwards."""
    t = Tex(16, 16)
    shape = {(x, y) for y, (x0, x1) in INGOT_ROWS.items() for x in range(x0, x1 + 1)}
    edge = {p for p in shape if any((p[0] + ox, p[1] + oy) not in shape for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)))}
    body = shape - edge

    def crease(x: float) -> float:
        return 12.4 - 0.48 * x         # top face above this line, front face below

    faces: dict[str, set[tuple[int, int]]] = {"top": set(), "front": set(), "end": set()}
    for (x, y) in body:
        if x >= 13 and 6 <= y <= 9:
            faces["end"].add((x, y))
        elif y + 0.5 < crease(x + 0.5):
            faces["top"].add((x, y))
        else:
            faces["front"].add((x, y))
    for (x, y) in faces["top"]:
        t.px(x, y, ramp[5])
    for (x, y) in faces["top"]:
        if (x, y - 1) not in faces["top"]:
            t.px(x, y, ramp[6])                               # lit ridge along the back edge
        elif (x, y + 1) in faces["front"]:
            t.px(x, y, ramp[4])                               # crease into the front face
    for (x, y) in faces["front"]:
        t.px(x, y, ramp[4] if x < 6 else ramp[3])
        if (x, y + 1) not in shape - edge:
            t.px(x, y, ramp[2])                               # foot of the bar in shadow
    for (x, y) in faces["end"]:
        t.px(x, y, ramp[6] if y <= 7 else ramp[5] if y == 8 else ramp[4])   # the right end catches the light
    for q in ((4, 7), (8, 5)):
        if q in faces["top"]:
            t.px(*q, ramp[6])                                 # specular glints
    for (x, y) in edge:
        lower_right = (x + 1, y) not in shape or (x, y + 1) not in shape
        t.px(x, y, ramp[0] if lower_right else ramp[2])
    if mark:
        mark(t, faces)
    return t


# --- dust -----------------------------------------------------------------------------------------------------------------

def dust(ramp: Ramp, seed: int, sparkle: bool = False) -> Tex:
    """A rounded heap of fine powder: dome silhouette, dithered grains lit from the upper left, darker rim."""
    t = Tex(16, 16)
    rng = random.Random(seed)
    cx, base, half, height = 7.5, 13.0, 6.2, 9.0
    pts = set()
    for y in range(16):
        for x in range(16):
            dx = (x + 0.5 - cx) / half
            top = base - height * max(0.0, 1 - abs(dx) ** 1.7) ** 0.75
            if abs(dx) <= 1 and top <= y + 0.5 <= base:
                pts.add((x, y))
    # A few loose grains at the foot.
    for gx, gy in ((1, 12), (14, 12), (13, 13), (2, 13)):
        if rng.random() < 0.6:
            pts.add((gx, gy))
    for (x, y) in pts:
        # Light from the upper left: lighter near the top-left of the dome.
        nx, ny = (x + 0.5 - cx) / half, (y + 0.5 - (base - height * 0.55)) / (height * 0.6)
        lum = -0.6 * nx - 0.8 * ny + rng.uniform(-0.45, 0.45)
        tone = 5 if lum > 0.55 else 4 if lum > 0.0 else 3 if lum > -0.6 else 2
        t.px(x, y, ramp[tone])
    if sparkle:
        inner = sorted(p for p in pts if all((p[0] + ox, p[1] + oy) in pts for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1))))
        for p in rng.sample(inner, min(3, len(inner))):
            t.px(*p, ramp[6])
    _outline(t, pts, ramp[1])
    return t


# --- ore clusters --------------------------------------------------------------------------------------------------------
# Stamps: H highlight, M mid, D shade, r rim (darkest). Coordinates relative to the stamp's top-left.
STAMPS = {
    "round": [".HM.", "HMMD", "MMDr", ".Dr."],
    "blob3": [".H.", "HMD", ".Dr"],
    "pair": ["HM", "MD", ".r"],
    "bar": ["HMM.", ".MDD", "..rr"],
    "tall": ["H.", "MM", "MD", ".r"],
    "speck": ["H", "D"],
    "ell": ["HM.", "M..", "Dr."],
    # Crystalline minerals
    "prism": ["...H", "..HM", ".HMD", "HMD.", "MD..", "r..."],
    "prism_s": ["..H", ".HM", "HMD", "Dr."],
    "cube": ["HHM", "MMD", "MDD", ".rr"],
    "cube_s": ["HM", "DD", "rr"],
    "tab": ["HHHM", "MMDD", "rrr."],
    # Crusts (evaporites): flat, fluffy, light rim
    "crust": [".HHM.", "HMMMH", ".MDM.", "..r.."],
    "crust_s": ["HHM", ".MD"],
}


def _place(rng: random.Random, sizes: list[tuple[int, int]], min_gap: int = 1, attempts: int = 400) -> list[tuple[int, int]]:
    """Positions for stamps of the given sizes, a pixel off the edges and not touching each other."""
    taken: set[tuple[int, int]] = set()
    out = []
    for w, h in sizes:
        for _ in range(attempts):
            x, y = rng.randint(1, 15 - w), rng.randint(1, 15 - h)
            cells = {(x + i, y + j) for i in range(-min_gap, w + min_gap) for j in range(-min_gap, h + min_gap)}
            if not cells & taken:
                taken |= {(x + i, y + j) for i in range(w) for j in range(h)}
                out.append((x, y))
                break
        else:
            out.append((-99, -99))
    return out


def ore_overlay(ramp: Ramp, stamps: list[str], seed: int, rim=None, accent=None, shift: int = 0) -> tuple[Tex, set[tuple[int, int]]]:
    """Mineral clusters for an ore overlay (transparent elsewhere). Returns the texture and the highlight pixels
    (for emissive glints). `rim` overrides the rim colour; `accent(t, rng, pixels)` adds mineral-specific detail."""
    t = Tex(16, 16)
    rng = random.Random(seed)
    rim = rim if rim is not None else ramp[1]
    # `shift` lifts the tones for pale minerals so they still stand out from grey stone.
    tones = {"H": ramp[min(6, 5 + shift)], "M": ramp[min(6, 4 + shift)], "D": ramp[min(6, 3 + shift)], "r": rim}
    order = list(stamps)
    rng.shuffle(order)
    sizes = [(len(STAMPS[s][0]), len(STAMPS[s])) for s in order]
    highlights: set[tuple[int, int]] = set()
    painted: set[tuple[int, int]] = set()
    for name, (x, y) in zip(order, _place(rng, sizes)):
        if x < 0:
            continue
        rows = STAMPS[name]
        for j, row in enumerate(rows):
            for i, ch in enumerate(row):
                if ch == ".":
                    continue
                px, py = x + i, y + j
                if not (0 <= px < 16 and 0 <= py < 16):
                    continue
                color = tones[ch]
                if ch == "M" and rng.random() < 0.25:
                    color = ramp[min(6, 5 + shift)] if rng.random() < 0.4 else ramp[min(6, 3 + shift)]
                t.px(px, py, color)
                painted.add((px, py))
                if ch == "H":
                    highlights.add((px, py))
    if accent:
        accent(t, rng, painted)
    return t, highlights


# --- raw materials -------------------------------------------------------------------------------------------------------
# Vanilla raw ores are a wide, faceted lump: a big upper-left mass and a smaller lower-right one with a dark crease
# between, shaded in flat planes (bright top facets, mid sides, dark undersides) rather than noise.
RAW_LOBES = {
    # Back lump (upper right), main lump (left), front lump (lower right); irregular outlines with a notch or two.
    "a": [[(7.6, 3.6), (10.4, 1.6), (13.6, 2.4), (14.4, 5.6), (12.4, 7.4), (8.6, 6.8)],
          [(0.8, 7.4), (2.4, 3.6), (5.6, 2.4), (8.4, 3.4), (10.8, 5.6), (10.6, 9.0), (7.0, 11.6), (3.6, 11.8), (1.0, 10.6)],
          [(7.4, 10.4), (9.6, 7.6), (12.4, 6.8), (15.2, 8.8), (15.2, 11.8), (13.2, 13.6), (9.4, 13.8), (7.6, 12.6)]],
    "b": [[(1.6, 4.6), (4.0, 2.4), (7.0, 2.6), (8.4, 5.0), (6.4, 7.0), (2.6, 7.2)],
          [(4.6, 6.4), (8.0, 3.0), (12.0, 3.2), (14.8, 5.6), (14.6, 9.4), (11.4, 11.4), (7.0, 11.2), (4.8, 9.6)],
          [(0.8, 10.0), (3.0, 8.0), (7.4, 8.4), (9.0, 11.0), (7.6, 13.8), (3.4, 14.0), (1.0, 12.4)]],
}


def _facet_tone(px: float, py: float, cx: float, cy: float, r: float) -> int:
    """Flat facet shading round a lump's centre: light from the upper left, a bright plateau on top."""
    dx, dy = px - cx, py - cy
    dist = math.hypot(dx, dy) / r
    if dist < 0.30:
        return 5
    ang = math.degrees(math.atan2(dy, dx)) % 360        # 0 = right, 90 = down
    if 180 <= ang < 250:
        return 6                                         # upper-left facet: lit
    if 250 <= ang < 300:
        return 6 if dist < 0.7 else 5                   # top
    if 300 <= ang < 345:
        return 4                                         # upper right
    if 135 <= ang < 180:
        return 4                                         # left
    if 345 <= ang or ang < 40:
        return 3                                         # right
    if 40 <= ang < 95:
        return 2                                         # underside
    return 3                                             # lower left


def raw(ramp: Ramp, variant: str = "a", detail=None) -> Tex:
    """A raw-ore lump in vanilla's construction (own silhouettes): lumps drawn back to front, each shaded in flat
    facets, dark creases where a nearer lump sits on a farther one, lit rims on the nearer lump.
    `detail(t, rng, top_pixels)` adds mineral marks on the lit facets."""
    t = Tex(16, 16)
    masks = [{(x, y) for y in range(16) for x in range(16) if _poly(poly, x + 0.5, y + 0.5)} for poly in RAW_LOBES[variant]]
    drawn: set[tuple[int, int]] = set()
    lit: list[tuple[int, int]] = []
    for m in masks:
        xs = [p[0] for p in m]
        ys = [p[1] for p in m]
        cx, cy = (min(xs) + max(xs) + 1) / 2 - 0.6, (min(ys) + max(ys) + 1) / 2 - 0.8
        r = max(max(xs) - min(xs), max(ys) - min(ys)) / 2 + 0.5
        for (x, y) in m:
            tone = _facet_tone(x + 0.5, y + 0.5, cx, cy, r)
            t.px(x, y, ramp[tone])
            if tone >= 5:
                lit.append((x, y))
        for (x, y) in drawn - m:                         # crease on the farther lump
            if any((x + ox, y + oy) in m for ox, oy in ((1, 0), (0, 1), (1, 1))):
                t.px(x, y, ramp[1])
        for (x, y) in m:                                 # lit rim on the nearer lump
            if any((x + ox, y + oy) in drawn - m for ox, oy in ((-1, 0), (0, -1))):
                t.px(x, y, ramp[5])
        drawn |= m
    if detail:
        detail(t, random.Random(len(lit)), sorted(p for p in lit if t.get(*p)[3]))
    for (x, y) in list(drawn):
        for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            q = (x + ox, y + oy)
            if q not in drawn and 0 <= q[0] < 16 and 0 <= q[1] < 16 and t.get(*q)[3] == 0:
                t.px(*q, ramp[0] if (ox > 0 or oy > 0) else ramp[1])
    return t


# --- dust (v2) ------------------------------------------------------------------------------------------------------------
# Every vanilla dust shares one silhouette: a point at the top, widest across rows 8-10, rounded at the bottom
# (x 2..13, y 3..13, outline included). Ours use the same outline; the inside is our own shading: a checker dither
# of two tones per region (lighter toward the upper left), a few bright clumps, darker grains toward the base.
DUST_ROWS = {3: (7, 8), 4: (6, 9), 5: (5, 10), 6: (4, 11), 7: (3, 12), 8: (2, 13), 9: (2, 13), 10: (2, 13),
             11: (3, 12), 12: (4, 11), 13: (6, 9)}


def dust2(ramp: Ramp, seed: int, sparkle: bool = False) -> Tex:
    t = Tex(16, 16)
    rng = random.Random(seed)
    shape = {(x, y) for y, (x0, x1) in DUST_ROWS.items() for x in range(x0, x1 + 1)}
    edge = {p for p in shape if any((p[0] + ox, p[1] + oy) not in shape for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)))}
    body = shape - edge
    cx, cy = 7.5, 8.0
    for (x, y) in body:
        k = (-(x + 0.5 - cx) * 0.55 - (y + 0.5 - cy) * 0.85) / 5.0     # > 0 toward the upper left
        hi, lo = (5, 4) if k > 0.3 else (4, 3) if k > -0.25 else (3, 2)
        t.px(x, y, ramp[hi] if (x + y) % 2 == 0 else ramp[lo])
    lit = sorted(p for p in body if p[0] + p[1] < 15 and (p[0] + 1, p[1]) in body)
    for (x, y) in rng.sample(lit, min(3, len(lit))):
        t.px(x, y, ramp[6] if sparkle else ramp[5])
        t.px(x + 1, y, ramp[5])
    dark = sorted(p for p in body if p[0] + p[1] > 17)
    for p in rng.sample(dark, min(3, len(dark))):
        t.px(*p, ramp[1])
    # Outline: the material's own darks, a touch lighter along the upper-left edges.
    for (x, y) in edge:
        upper_left = (x - 1, y) not in shape or (x, y - 1) not in shape
        lower_right = (x + 1, y) not in shape or (x, y + 1) not in shape
        t.px(x, y, ramp[1] if upper_left and not lower_right else ramp[0])
    return t
