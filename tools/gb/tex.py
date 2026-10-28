"""Pixel-art texture canvas and primitives.

Everything is deliberate: bevels, rivets, seams, stripes, stencil text, decals and *placed* wear. Randomness is
always seeded (so textures are reproducible) and used only to place a small number of hand-scale details, never to
fill a surface with noise.

Coordinates are pixel coordinates, origin top-left, x right, y down. Rectangles are (x, y, w, h).
"""
from __future__ import annotations

import math
import random
from pathlib import Path
from typing import Callable, Iterable, Sequence

import numpy as np
from PIL import Image

from .palette import RGBA, TRANSPARENT, Ramp, mix

# --- 3x5 stencil font ----------------------------------------------------------------------------------------

_FONT_SRC = {
    "A": ".#.|#.#|###|#.#|#.#", "B": "##.|#.#|##.|#.#|##.", "C": ".##|#..|#..|#..|.##", "D": "##.|#.#|#.#|#.#|##.",
    "E": "###|#..|##.|#..|###", "F": "###|#..|##.|#..|#..", "G": ".##|#..|#.#|#.#|.##", "H": "#.#|#.#|###|#.#|#.#",
    "I": "###|.#.|.#.|.#.|###", "J": "..#|..#|..#|#.#|.#.", "K": "#.#|#.#|##.|#.#|#.#", "L": "#..|#..|#..|#..|###",
    "M": "#.#|###|###|#.#|#.#", "N": "##.|#.#|#.#|#.#|#.#", "O": ".#.|#.#|#.#|#.#|.#.", "P": "##.|#.#|##.|#..|#..",
    "Q": ".#.|#.#|#.#|##.|.##", "R": "##.|#.#|##.|#.#|#.#", "S": ".##|#..|.#.|..#|##.", "T": "###|.#.|.#.|.#.|.#.",
    "U": "#.#|#.#|#.#|#.#|###", "V": "#.#|#.#|#.#|#.#|.#.", "W": "#.#|#.#|###|###|#.#", "X": "#.#|#.#|.#.|#.#|#.#",
    "Y": "#.#|#.#|.#.|.#.|.#.", "Z": "###|..#|.#.|#..|###",
    "0": "###|#.#|#.#|#.#|###", "1": ".#.|##.|.#.|.#.|###", "2": "##.|..#|.#.|#..|###", "3": "##.|..#|.#.|..#|##.",
    "4": "#.#|#.#|###|..#|..#", "5": "###|#..|##.|..#|##.", "6": ".##|#..|###|#.#|###", "7": "###|..#|.#.|.#.|.#.",
    "8": "###|#.#|###|#.#|###", "9": "###|#.#|###|..#|##.",
    "-": "...|...|###|...|...", ".": "...|...|...|...|.#.", "/": "..#|..#|.#.|#..|#..", ":": "...|.#.|...|.#.|...",
    "!": ".#.|.#.|.#.|...|.#.", "+": "...|.#.|###|.#.|...", " ": "...|...|...|...|...", "%": "#.#|..#|.#.|#..|#.#",
}
FONT = {k: [[c == "#" for c in row] for row in v.split("|")] for k, v in _FONT_SRC.items()}
GLYPH_W, GLYPH_H = 3, 5

# --- hand-drawn decals ('#' = primary, 'o' = secondary, '.' = empty) ------------------------------------------

DECALS = {
    "skull5": ".###.|#o#o#|#####|.###.|.#.#.",
    "bolt5x7": "...##|..##.|.##..|#####|..##.|.##..|##...",
    "flame5x7": "..#..|.##..|.###.|###o.|#ooo#|#ooo#|.###.",
    "snow5": "#.#.#|.###.|##o##|.###.|#.#.#",
    "burst7": "#..#..#|.#.#.#.|..###..|###o###|..###..|.#.#.#.|#..#..#",
    "chevron5x3": "#...#|.#.#.|..#..",
    "arrow_up5": "..#..|.###.|#####|..#..|..#..",
    "drop3x4": ".#.|###|###|.#.",
    # Radiation trefoil for small placards: two blades up, hub, one blade down.
    "trefoil7": "###.###|.##.##.|.......|...#...|.......|..###..|.#####.",
}


def _decal_mask(name: str) -> list[str]:
    return DECALS[name].split("|")


class Tex:
    """An RGBA canvas (numpy uint8, shape (h, w, 4))."""

    def __init__(self, w: int, h: int, fill: RGBA = TRANSPARENT):
        self.w, self.h = w, h
        self.a = np.zeros((h, w, 4), dtype=np.uint8)
        self.a[:, :] = fill

    # -- I/O ---------------------------------------------------------------------------------------------------
    @classmethod
    def load(cls, path: Path) -> "Tex":
        img = Image.open(path).convert("RGBA")
        t = cls(img.width, img.height)
        t.a = np.array(img, dtype=np.uint8)
        return t

    def copy(self) -> "Tex":
        t = Tex(self.w, self.h)
        t.a = self.a.copy()
        return t

    def image(self) -> Image.Image:
        return Image.fromarray(self.a, "RGBA")

    def save(self, path: Path) -> Path:
        path.parent.mkdir(parents=True, exist_ok=True)
        self.image().save(path, optimize=True)
        return path

    def scaled(self, factor: int) -> Image.Image:
        return self.image().resize((self.w * factor, self.h * factor), Image.NEAREST)

    # -- basic pixels ------------------------------------------------------------------------------------------
    def inside(self, x: int, y: int) -> bool:
        return 0 <= x < self.w and 0 <= y < self.h

    def px(self, x: int, y: int, c: RGBA, blend: bool = False) -> None:
        if not self.inside(x, y):
            return
        if blend and c[3] < 255:
            dst = self.a[y, x].astype(np.float32)
            src = np.array(c, dtype=np.float32)
            sa = src[3] / 255
            out_a = sa + dst[3] / 255 * (1 - sa)
            if out_a <= 0:
                return
            rgb = (src[:3] * sa + dst[:3] * (dst[3] / 255) * (1 - sa)) / out_a
            self.a[y, x] = (*np.clip(rgb, 0, 255).astype(np.uint8), int(out_a * 255))
        else:
            self.a[y, x] = c

    def get(self, x: int, y: int) -> RGBA:
        return tuple(int(v) for v in self.a[y, x])  # type: ignore[return-value]

    def rect(self, x: int, y: int, w: int, h: int, c: RGBA) -> None:
        x0, y0, x1, y1 = max(0, x), max(0, y), min(self.w, x + w), min(self.h, y + h)
        if x1 > x0 and y1 > y0:
            self.a[y0:y1, x0:x1] = c

    def outline(self, x: int, y: int, w: int, h: int, c: RGBA) -> None:
        self.hline(x, y, w, c)
        self.hline(x, y + h - 1, w, c)
        self.vline(x, y, h, c)
        self.vline(x + w - 1, y, h, c)

    def hline(self, x: int, y: int, length: int, c: RGBA) -> None:
        self.rect(x, y, length, 1, c)

    def vline(self, x: int, y: int, length: int, c: RGBA) -> None:
        self.rect(x, y, 1, length, c)

    def line(self, x0: int, y0: int, x1: int, y1: int, c: RGBA) -> None:
        dx, dy = abs(x1 - x0), -abs(y1 - y0)
        sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
        err = dx + dy
        while True:
            self.px(x0, y0, c)
            if x0 == x1 and y0 == y1:
                return
            e2 = 2 * err
            if e2 >= dy:
                err += dy
                x0 += sx
            if e2 <= dx:
                err += dx
                y0 += sy

    def disc(self, cx: float, cy: float, r: float, c: RGBA) -> None:
        for y in range(int(cy - r - 1), int(cy + r + 2)):
            for x in range(int(cx - r - 1), int(cx + r + 2)):
                if (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 <= r * r:
                    self.px(x, y, c)

    def ring(self, cx: float, cy: float, r_in: float, r_out: float, c: RGBA) -> None:
        for y in range(int(cy - r_out - 1), int(cy + r_out + 2)):
            for x in range(int(cx - r_out - 1), int(cx + r_out + 2)):
                d2 = (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2
                if r_in * r_in <= d2 <= r_out * r_out:
                    self.px(x, y, c)

    def map_rect(self, x: int, y: int, w: int, h: int, fn: Callable[[int, int, RGBA], RGBA | None]) -> None:
        """Calls fn(x, y, current) for each pixel; a non-None return replaces the pixel."""
        for yy in range(max(0, y), min(self.h, y + h)):
            for xx in range(max(0, x), min(self.w, x + w)):
                r = fn(xx, yy, self.get(xx, yy))
                if r is not None:
                    self.a[yy, xx] = r

    # -- composition ---------------------------------------------------------------------------------------------
    def blit(self, other: "Tex", x: int, y: int, blend: bool = True) -> None:
        for yy in range(other.h):
            for xx in range(other.w):
                c = other.get(xx, yy)
                if c[3] == 0:
                    continue
                self.px(x + xx, y + yy, c, blend=blend)

    # -- shaded building blocks ----------------------------------------------------------------------------------
    def panel(self, x: int, y: int, w: int, h: int, ramp: Ramp, base: int = 3, style: str = "raised",
              bevel: int = 1, outline: bool = False) -> None:
        """A flat panel with a bevel. style: raised (lit top-left), inset (lit bottom-right), flat."""
        self.rect(x, y, w, h, ramp[base])
        if style == "flat":
            if outline:
                self.outline(x, y, w, h, ramp[base - 2])
            return
        hi, lo = (ramp[base + 1], ramp[base - 1]) if style == "raised" else (ramp[base - 1], ramp[base + 1])
        hi2, lo2 = (ramp[base + 2], ramp[base - 2]) if style == "raised" else (ramp[base - 2], ramp[base + 2])
        for i in range(bevel):
            self.hline(x + i, y + i, w - 2 * i, hi if i else hi2)
            self.vline(x + i, y + i, h - 2 * i, hi)
            self.hline(x + i, y + h - 1 - i, w - 2 * i, lo if i else lo2)
            self.vline(x + w - 1 - i, y + i, h - 2 * i, lo)
        # Corners read better softened to the base tone.
        self.px(x + w - 1, y, ramp[base])
        self.px(x, y + h - 1, ramp[base])
        if outline:
            self.outline(x, y, w, h, ramp[base - 3])

    def rivet(self, x: int, y: int, ramp: Ramp, size: int = 1) -> None:
        """size 1: lit pixel with a drop shadow. size 2: 2x2 domed head."""
        if size == 1:
            self.px(x, y, ramp.highlight)
            self.px(x + 1, y + 1, ramp.deep, blend=False) if self.inside(x + 1, y + 1) else None
        else:
            self.px(x, y, ramp.shine)
            self.px(x + 1, y, ramp.light)
            self.px(x, y + 1, ramp.light)
            self.px(x + 1, y + 1, ramp.shadow)
            self.px(x + 2, y + 2, ramp.deep)

    def rivet_row(self, x: int, y: int, length: int, spacing: int, ramp: Ramp, vertical: bool = False) -> None:
        for i in range(0, length, spacing):
            if vertical:
                self.rivet(x, y + i, ramp)
            else:
                self.rivet(x + i, y, ramp)

    def seam_h(self, x: int, y: int, length: int, ramp: Ramp) -> None:
        """Recessed horizontal joint: dark line with a lit lip below."""
        self.hline(x, y, length, ramp.deep)
        self.hline(x, y + 1, length, ramp.light)

    def seam_v(self, x: int, y: int, length: int, ramp: Ramp) -> None:
        self.vline(x, y, length, ramp.deep)
        self.vline(x + 1, y, length, ramp.light)

    def stripes(self, x: int, y: int, w: int, h: int, a: RGBA, b: RGBA, width: int = 2, flip: bool = False) -> None:
        """45-degree hazard stripes."""
        for yy in range(h):
            for xx in range(w):
                k = (xx - yy if flip else xx + yy) // width
                self.px(x + xx, y + yy, a if k % 2 == 0 else b)

    def gradient_v(self, x: int, y: int, w: int, h: int, top: RGBA, bottom: RGBA, steps: int | None = None) -> None:
        for yy in range(h):
            t = yy / max(1, h - 1)
            if steps:
                t = round(t * (steps - 1)) / (steps - 1)
            self.hline(x, y + yy, w, mix(top, bottom, t))

    def vent(self, x: int, y: int, w: int, h: int, ramp: Ramp, slat: int = 2) -> None:
        """Horizontal louvre slats: dark gap, lit slat edge."""
        self.rect(x, y, w, h, ramp.outline)
        for yy in range(y, y + h, slat):
            self.hline(x, yy, w, ramp.shadow)
            if slat > 2:
                self.hline(x, yy + 1, w, ramp.base)

    def grille(self, x: int, y: int, w: int, h: int, ramp: Ramp) -> None:
        """Checker mesh grille."""
        for yy in range(h):
            for xx in range(w):
                self.px(x + xx, y + yy, ramp.outline if (xx + yy) % 2 else ramp.shadow)

    # -- text & decals ------------------------------------------------------------------------------------------------
    def text(self, x: int, y: int, s: str, c: RGBA, shadow: RGBA | None = None, spacing: int = 1) -> int:
        """Draws stencil text; returns the width used."""
        cx = x
        for ch in s.upper():
            glyph = FONT.get(ch)
            if glyph is None:
                raise ValueError(f"No glyph for {ch!r}")
            for gy, row in enumerate(glyph):
                for gx, on in enumerate(row):
                    if on:
                        if shadow:
                            self.px(cx + gx + 1, y + gy + 1, shadow)
                        self.px(cx + gx, y + gy, c)
            cx += GLYPH_W + spacing
        return cx - x - spacing

    @staticmethod
    def text_width(s: str, spacing: int = 1) -> int:
        return len(s) * (GLYPH_W + spacing) - spacing

    def decal(self, name: str, x: int, y: int, primary: RGBA, secondary: RGBA | None = None,
              flip_x: bool = False) -> None:
        rows = _decal_mask(name)
        for dy, row in enumerate(rows):
            for dx, ch in enumerate(row[::-1] if flip_x else row):
                if ch == "#":
                    self.px(x + dx, y + dy, primary)
                elif ch == "o" and secondary is not None:
                    self.px(x + dx, y + dy, secondary)

    def placard(self, x: int, y: int, w: int, h: int, symbol: str, fg: RGBA, bg: RGBA, border: RGBA | None = None) -> None:
        """A rectangular warning placard with a centred decal."""
        self.rect(x, y, w, h, bg)
        if border is not None:
            self.outline(x, y, w, h, border)
        rows = _decal_mask(symbol)
        dw, dh = len(rows[0]), len(rows)
        self.decal(symbol, x + (w - dw) // 2, y + (h - dh) // 2, fg)

    def trefoil(self, cx: float, cy: float, r: float, c: RGBA, bg: RGBA | None = None) -> None:
        """Radiation trefoil centred at (cx, cy) with outer radius r (pixels): two blades up, one down, with a
        clear gap between hub and blades even at small sizes."""
        if bg is not None:
            self.disc(cx, cy, r + 0.8, bg)
        r_hub = max(0.75, r * 0.2)
        r_in = r_hub + max(0.9, r * 0.18)
        for y in range(int(cy - r - 1), int(cy + r + 2)):
            for x in range(int(cx - r - 1), int(cx + r + 2)):
                dx, dy = x + 0.5 - cx, cy - (y + 0.5)
                d = math.hypot(dx, dy)
                if d <= r_hub:
                    self.px(x, y, c)
                    continue
                if r_in <= d <= r:
                    ang = math.degrees(math.atan2(dy, dx)) % 360
                    for centre in (30, 150, 270):
                        if abs(((ang - centre + 180) % 360) - 180) <= 30:
                            self.px(x, y, c)
                            break

    # -- wear (placed, seeded) -----------------------------------------------------------------------------------------
    def chip_edges(self, x: int, y: int, w: int, h: int, under: RGBA, seed: int, chips: int = 4,
                   edges: str = "tblr", max_len: int = 3) -> None:
        """Paint chips along chosen edges exposing `under` (primer or bare metal). A handful of small chips,
        biased toward corners where real wear happens."""
        rng = random.Random(seed)
        spots: list[tuple[int, int, int, int]] = []  # (x, y, along_dx, along_dy)
        if "t" in edges:
            spots += [(x + i, y, 1, 0) for i in range(w)]
        if "b" in edges:
            spots += [(x + i, y + h - 1, 1, 0) for i in range(w)]
        if "l" in edges:
            spots += [(x, y + i, 0, 1) for i in range(h)]
        if "r" in edges:
            spots += [(x + w - 1, y + i, 0, 1) for i in range(h)]
        if not spots:
            return
        # Corner bias: weight spots by distance to the nearest corner.
        weights = []
        for sx, sy, _, _ in spots:
            dc = min(abs(sx - x) + abs(sy - y), abs(sx - (x + w - 1)) + abs(sy - y),
                     abs(sx - x) + abs(sy - (y + h - 1)), abs(sx - (x + w - 1)) + abs(sy - (y + h - 1)))
            weights.append(1.0 / (1 + dc * 0.6))
        for _ in range(chips):
            sx, sy, ax, ay = rng.choices(spots, weights)[0]
            length = rng.randint(1, max_len)
            for k in range(length):
                self.px(sx + ax * k, sy + ay * k, under)
            if length >= 2 and rng.random() < 0.5:
                # A second row makes the chip read as a flake, not a line.
                inward = (0, 1) if sy == y else (0, -1) if sy == y + h - 1 else (1, 0) if sx == x else (-1, 0)
                self.px(sx + inward[0], sy + inward[1], under)

    def scratches(self, x: int, y: int, w: int, h: int, c: RGBA, seed: int, count: int = 3, length: int = 3) -> None:
        rng = random.Random(seed)
        for _ in range(count):
            sx, sy = rng.randint(x, x + w - 1), rng.randint(y, y + h - 1)
            dx = rng.choice((1, -1))
            for k in range(length):
                self.px(sx + dx * k, sy + (k // 2), c, blend=True)

    def drips(self, x: int, y: int, w: int, c: RGBA, seed: int, count: int = 2, max_len: int = 4) -> None:
        """Grime streaks running down from a ledge at row y; fade out as they go."""
        rng = random.Random(seed)
        cols = rng.sample(range(x, x + w), min(count, w))
        for cx in cols:
            length = rng.randint(2, max_len)
            for k in range(length):
                alpha = int(c[3] * (1 - k / (length + 1)))
                self.px(cx, y + k, (c[0], c[1], c[2], alpha), blend=True)

    def speckle(self, x: int, y: int, w: int, h: int, colors: Sequence[RGBA], seed: int, density: float = 0.08) -> None:
        """Sparse aggregate/pore specks (concrete, cast metal). Density is kept low on purpose."""
        rng = random.Random(seed)
        n = int(w * h * density)
        for _ in range(n):
            self.px(rng.randint(x, x + w - 1), rng.randint(y, y + h - 1), rng.choice(colors))


def emissive_from(base: Tex, mask_pixels: Iterable[tuple[int, int]]) -> Tex:
    """Copies the listed pixels of `base` onto a transparent canvas (a glow layer)."""
    t = Tex(base.w, base.h)
    for x, y in mask_pixels:
        t.a[y, x] = base.a[y, x]
    return t
