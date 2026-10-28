"""Item-sprite helpers (16x16): lit blobs, pixel-isometric boxes, powder piles, outlines.

Light comes from the top-left everywhere so every icon in the mod reads as part of one set.
"""
from __future__ import annotations

import math
import random

from .palette import Ramp
from .tex import Tex

LIGHT = (-0.55, -0.6, 0.58)


def disc_union(lobes: list[tuple[float, float, float]], size: int = 16) -> set[tuple[int, int]]:
    pts = set()
    for cx, cy, r in lobes:
        for y in range(int(cy - r - 1), int(cy + r + 2)):
            for x in range(int(cx - r - 1), int(cx + r + 2)):
                if (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 <= r * r and 0 <= x < size and 0 <= y < size:
                    pts.add((x, y))
    return pts


def faceted_blob(t: Tex, lobes: list[tuple[float, float, float]], ramp: Ramp, facets: int = 0, seed: int = 0) -> set[tuple[int, int]]:
    """A lumpy chunk: each lobe shaded as a dome; optional flat facets (a single tone across a straight cut)
    make it read as broken rock rather than a ball."""
    pts = disc_union(lobes)
    rng = random.Random(seed)
    cuts = [(rng.uniform(-1, 1), rng.uniform(-1, 1), rng.uniform(-0.2, 0.6)) for _ in range(facets)]
    for (x, y) in pts:
        best = None
        for cx, cy, r in lobes:
            dx, dy = (x + 0.5 - cx) / r, (y + 0.5 - cy) / r
            d = math.hypot(dx, dy)
            if best is None or d < best[0]:
                best = (d, dx, dy)
        d, dx, dy = best  # type: ignore[misc]
        nz = math.sqrt(max(0.0, 1 - min(d, 1.0) ** 2))
        lum = dx * LIGHT[0] + dy * LIGHT[1] + nz * LIGHT[2]
        for ax, ay, k in cuts:                     # flat facet: quantise brightness on one side of a cut
            if dx * ax + dy * ay > k:
                lum = 0.45 + 0.2 * (-(ax * LIGHT[0] + ay * LIGHT[1]))
        tone = 5 if lum > 0.7 else 4 if lum > 0.45 else 3 if lum > 0.2 else 2 if lum > -0.1 else 1
        t.px(x, y, ramp[tone])
    return pts


def outline(t: Tex, pts: set[tuple[int, int]], color, diagonal: bool = False) -> None:
    """1-px outline around a shape (vanilla item style)."""
    nbrs = [(1, 0), (-1, 0), (0, 1), (0, -1)] + ([(1, 1), (-1, -1), (1, -1), (-1, 1)] if diagonal else [])
    edge = set()
    for (x, y) in pts:
        for ox, oy in nbrs:
            q = (x + ox, y + oy)
            if q not in pts and 0 <= q[0] < t.w and 0 <= q[1] < t.h:
                edge.add(q)
    for q in edge:
        t.px(*q, color)


def _poly_fill(t: Tex, poly: list[tuple[float, float]], color) -> set[tuple[int, int]]:
    pts = set()
    xs = [p[0] for p in poly]
    ys = [p[1] for p in poly]
    for y in range(int(min(ys)), int(max(ys)) + 1):
        for x in range(int(min(xs)), int(max(xs)) + 1):
            px, py = x + 0.5, y + 0.5
            inside = False
            j = len(poly) - 1
            for i in range(len(poly)):
                xi, yi = poly[i]
                xj, yj = poly[j]
                if (yi > py) != (yj > py) and px < (xj - xi) * (py - yi) / (yj - yi + 1e-12) + xi:
                    inside = not inside
                j = i
            if inside and 0 <= x < t.w and 0 <= y < t.h:
                t.px(x, y, color)
                pts.add((x, y))
    return pts


def iso_bar(t: Tex, ox: float, oy: float, length: float, width: float, height: float, ramp: Ramp,
            taper: float = 1.0) -> dict[str, set[tuple[int, int]]]:
    """A cast bar in 2:1 pixel isometric, long axis running down-right. taper < 1 narrows the top face
    (ingot draft). Returns the pixels of each face for detailing."""
    def proj(x, y, z):
        return ox + (x - z), oy + (x + z) * 0.5 - y
    L, W, H = length, width, height
    inset = (1 - taper) * W / 2
    top = [proj(inset, H, inset), proj(L - inset, H, inset), proj(L - inset, H, W - inset), proj(inset, H, W - inset)]
    front = [proj(0, 0, W), proj(L, 0, W), proj(L - inset, H, W - inset), proj(inset, H, W - inset)]
    end = [proj(L, 0, 0), proj(L, 0, W), proj(L - inset, H, W - inset), proj(L - inset, H, inset)]
    faces = {
        "end": _poly_fill(t, end, ramp.shadow),
        "front": _poly_fill(t, front, ramp.base),
        "top": _poly_fill(t, top, ramp.light),
    }
    return faces


def powder_pile(t: Tex, cx: float, base_y: int, height: int, half_width: float, ramp: Ramp, seed: int) -> set[tuple[int, int]]:
    """A conical heap of powder: lit left flank, shadowed right flank, bright crest, grain speckle and a few
    loose grains around the foot."""
    rng = random.Random(seed)
    pts = set()
    for k in range(height):
        y = base_y - k
        hw = half_width * (1 - k / height) ** 0.8 + 0.5
        for x in range(int(cx - hw), int(cx + hw) + 1):
            if not (0 <= x < 16 and 0 <= y < 16):
                continue
            rel = (x + 0.5 - cx) / max(hw, 0.5)
            tone = 5 if rel < -0.35 else 4 if rel < 0.15 else 3 if rel < 0.6 else 2
            if k >= height - 2:
                tone = min(6, tone + 1)
            if rng.random() < 0.18:
                tone = max(1, min(6, tone + rng.choice((-1, 1))))
            t.px(x, y, ramp[tone])
            pts.add((x, y))
    for _ in range(5):
        gx = int(cx + rng.choice((-1, 1)) * rng.uniform(half_width, half_width + 2.5))
        gy = base_y - rng.randint(0, 1)
        if 0 <= gx < 16 and 0 <= gy < 16 and (gx, gy) not in pts:
            t.px(gx, gy, ramp[rng.choice((3, 4))])
            pts.add((gx, gy))
    return pts
