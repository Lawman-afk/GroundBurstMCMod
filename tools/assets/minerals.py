"""Mineral painters for ore overlays (16x16, transparent background).

Each mineral is drawn as its real habit so ores are recognisable at a glance:
    spodumene (lithium)   pale pink-lilac prismatic crystals in diagonal sprays
    sulfur                bright yellow granular crusts with amber shadows
    niter (saltpeter)     fluffy white efflorescence with fine needles
    wolframite (tungsten) dark tabular crystals with a steel-blue metallic edge glint
    galena (lead)         small silver cubes with crisp three-face shading
    monazite (rare earth) honey-red resinous grains with gold highlights
Placements are hand-made per variant; seeds only pick small details.
"""
from __future__ import annotations

import math
import random

from gz.palette import fixed
from gz.tex import Tex

SPODUMENE = fixed("spodumene", "#4a2a3e", "#7a4a66", "#a77394", "#c99ab7", "#e2bfd4", "#f2dce8", "#fff4fa")
SULFUR = fixed("sulfur", "#5a3a08", "#8a6410", "#c2971a", "#e3c22c", "#f2db4c", "#f9ec8a", "#fffbd0")
NITER = fixed("niter", "#4e5254", "#7d8386", "#a8aeb0", "#c9cdce", "#e1e4e4", "#f0f2f2", "#ffffff")
WOLFRAMITE = fixed("wolframite", "#100a08", "#21140f", "#332018", "#463024", "#5e4636", "#8fa2b8", "#d4e6f8")
GALENA = fixed("galena", "#1f2328", "#363c44", "#525a64", "#737c87", "#9aa3ad", "#c4cbd2", "#eef2f6")
MONAZITE = fixed("monazite", "#3a1408", "#62240e", "#8c3a16", "#b45a22", "#d4823a", "#ecb062", "#fbe0a0")


def _shadow(t: Tex, pts: set[tuple[int, int]], color) -> None:
    """Drop a 1-px shadow below/right of a mineral so it sits in the rock."""
    for (x, y) in pts:
        for ox, oy in ((1, 0), (0, 1), (1, 1)):
            q = (x + ox, y + oy)
            if q not in pts and 0 <= q[0] < 16 and 0 <= q[1] < 16 and t.get(*q)[3] == 0:
                t.px(*q, (*color[:3], 170))


# --- spodumene: faceted prisms ------------------------------------------------------------------------------------

def spodumene(t: Tex, prisms: list[tuple[int, int, int, int, int]]) -> None:
    """prisms = (x, y, width, length, slope): a slanted prism growing upward from (x, y) (its foot), each row
    shifted by `slope` (-1, 0, 1). Lit left facet, body, shadowed right facet, bright termination at the tip."""
    all_pts: set[tuple[int, int]] = set()
    for x, y, w, length, slope in prisms:
        for k in range(length):
            row_y = y - k
            row_x = x + (k * slope) // 2
            for i in range(w):
                px, py = row_x + i, row_y
                if not (0 <= px < 16 and 0 <= py < 16):
                    continue
                tone = 5 if i == 0 else (2 if i == w - 1 else 4)
                if k == length - 1:
                    tone = 6 if i < w - 1 else 4        # pale termination facet
                elif k == 0:
                    tone = max(1, tone - 1)             # foot sinks into the rock
                t.px(px, py, SPODUMENE[tone])
                all_pts.add((px, py))
    _shadow(t, all_pts, SPODUMENE.outline)


# --- sulfur: granular crusts ------------------------------------------------------------------------------------------

def _grain_patch(cx: float, cy: float, rx: float, ry: float) -> set[tuple[int, int]]:
    pts = set()
    for y in range(int(cy - ry - 1), int(cy + ry + 2)):
        for x in range(int(cx - rx - 1), int(cx + rx + 2)):
            if ((x + 0.5 - cx) / rx) ** 2 + ((y + 0.5 - cy) / ry) ** 2 <= 1 and 0 <= x < 16 and 0 <= y < 16:
                pts.add((x, y))
    return pts


def granular(t: Tex, ramp, patches: list[tuple[float, float, float, float]], seed: int, grain: float = 0.35) -> None:
    """Crust patches lit from the top-left, broken up by individual grains (a few lighter/darker pixels)."""
    rng = random.Random(seed)
    all_pts: set[tuple[int, int]] = set()
    for cx, cy, rx, ry in patches:
        pts = _grain_patch(cx, cy, rx, ry)
        for (x, y) in pts:
            nx, ny = (x + 0.5 - cx) / rx, (y + 0.5 - cy) / ry
            lum = -(nx + ny) * 0.5
            tone = 4 if lum > 0.25 else 3 if lum > -0.25 else 2
            if rng.random() < grain:
                tone += rng.choice((-1, 1))
            t.px(x, y, ramp[max(1, min(6, tone))])
        # A bright crystal face on the lit shoulder of bigger patches.
        if rx >= 1.5:
            t.px(int(cx - rx * 0.4), int(cy - ry * 0.4), ramp.shine)
        all_pts |= pts
    _shadow(t, all_pts, ramp.outline)


# --- niter: fluffy efflorescence ---------------------------------------------------------------------------------------

def niter(t: Tex, patches: list[tuple[float, float, float, float]], seed: int) -> None:
    """Bright white crust: mostly light tones, a grey underside, and loose fluff specks around the edges."""
    rng = random.Random(seed)
    all_pts: set[tuple[int, int]] = set()
    for cx, cy, rx, ry in patches:
        pts = _grain_patch(cx, cy, rx, ry)
        for (x, y) in pts:
            ny = (y + 0.5 - cy) / ry
            nx = (x + 0.5 - cx) / rx
            tone = 6 if ny < -0.3 and nx < 0.4 else 5 if ny < 0.35 else 3
            if rng.random() < 0.25:
                tone = max(3, tone - 1)
            t.px(x, y, NITER[tone])
        all_pts |= pts
        for _ in range(int(rx * ry)):                    # fluff specks drifting off the crust
            fx = int(cx + rng.uniform(-rx - 1.5, rx + 1.5))
            fy = int(cy + rng.uniform(-ry - 1.5, ry + 0.5))
            if 0 <= fx < 16 and 0 <= fy < 16 and (fx, fy) not in all_pts:
                t.px(fx, fy, NITER.highlight)
                all_pts.add((fx, fy))
    _shadow(t, all_pts, NITER.outline)


# --- wolframite: tabular crystals with metallic edges ---------------------------------------------------------------------

def wolframite(t: Tex, tabs: list[tuple[int, int, int, int]]) -> None:
    """tabs = (x, y, w, h) blocky crystals; top-left edges catch a steel-blue metallic glint."""
    all_pts = set()
    for x, y, w, h in tabs:
        for yy in range(y, y + h):
            for xx in range(x, x + w):
                if not (0 <= xx < 16 and 0 <= yy < 16):
                    continue
                tone = 3
                if yy == y or xx == x:
                    tone = 5                       # metallic sheen on the lit edges
                if (yy == y and xx == x):
                    tone = 6
                if yy == y + h - 1 or xx == x + w - 1:
                    tone = 2
                if w >= 3 and h >= 3 and xx == x + w // 2 and y < yy < y + h - 1:
                    tone = 1                       # cleavage line
                t.px(xx, yy, WOLFRAMITE[tone])
                all_pts.add((xx, yy))
    _shadow(t, all_pts, WOLFRAMITE.outline)


# --- galena: cubes ------------------------------------------------------------------------------------------------------------

def galena(t: Tex, cubes: list[tuple[int, int, int]]) -> None:
    """cubes = (x, y, size). Drawn as tiny isometric-ish cubes: lit top band, mid left, dark right."""
    all_pts = set()
    for x, y, s in cubes:
        for yy in range(y, y + s):
            for xx in range(x, x + s):
                if not (0 <= xx < 16 and 0 <= yy < 16):
                    continue
                if yy == y:
                    tone = 5
                elif xx >= x + s - 1 or (s >= 3 and xx > x + s // 2):
                    tone = 2
                else:
                    tone = 4
                t.px(xx, yy, GALENA[tone])
                all_pts.add((xx, yy))
        t.px(x, y, GALENA.shine)
    _shadow(t, all_pts, GALENA.outline)


# --- monazite: resinous grains ----------------------------------------------------------------------------------------------------

def monazite(t: Tex, grains: list[tuple[float, float, float]]) -> None:
    all_pts = set()
    for cx, cy, r in grains:
        pts = _grain_patch(cx, cy, r, r * 0.85)
        for (x, y) in pts:
            dx, dy = (x + 0.5 - cx) / r, (y + 0.5 - cy) / r
            d = math.hypot(dx, dy)
            nz = math.sqrt(max(0.0, 1 - min(d, 1) ** 2))
            lum = -0.55 * dx - 0.6 * dy + 0.6 * nz
            tone = 5 if lum > 0.62 else 4 if lum > 0.4 else 3 if lum > 0.15 else 2
            t.px(x, y, MONAZITE[tone])
        hx, hy = int(cx - r * 0.35), int(cy - r * 0.35)
        if (hx, hy) in pts:
            t.px(hx, hy, MONAZITE.shine)
        all_pts |= pts
    _shadow(t, all_pts, MONAZITE.outline)
