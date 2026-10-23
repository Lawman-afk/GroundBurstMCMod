"""The machines' finish (the remaster after the Fission Reactor rework; DESIGN.md §4.3): one set of materials and the
details machines share, painted with the material library in model space, so every machine reads as one family.

    K = Kit(g, parts, seed=301)
    K.plinth(tex)                              # dark steel, a hazard band on its front, a tread-plate top
    K.paint(tex, "block", K.OLIVE)
    K.overlay(tex, "block", lambda f, img: K.panel_lines(f, img))
    g.paint_all(tex, box, glyph)               # screens, dials, glow layers: drawn over the material as before

Materials (as on the multiblocks): olive machine paint, gunmetal fittings, dark steel frames and plinths, bare and
stainless steel, galvanised pipework, copper, brass, rubber, white ceramic, yellow, red and black paint, hazard paint.
"""
from __future__ import annotations

from typing import Callable, Iterable

import numpy as np

from . import materials as M
from . import palette as P


def ramp_of(r) -> np.ndarray:
    """A palette ramp (gz.palette) as the material library's array of shades."""
    return np.array([s[:3] for s in r.shades], dtype=np.float32)


def metal(ramp, tone: float = 0.58, brushed: float = 0.04) -> M.Material:
    """A bare metal from a palette ramp (copper, brass, lead, steel): smooth, lit lips, little grime."""
    return M.Material(ramp=ramp_of(ramp), tone=tone, mottle=0.03, grain=0.03, dither=0.4, edge_light=0.16, edge_dark=0.1, ao=0.35,
                      grime=0.02, dust=0.0, brushed=brushed)


def paint_of(ramp, tone: float = 0.5, wear: float = 0.2) -> M.Material:
    """Paint from a palette ramp (a tier's colour, a hazard colour)."""
    return M.painted(ramp_of(ramp), tone=tone, wear=wear)


OLIVE = M.painted(M.OLIVE_RAMP, tone=0.55, wear=0.22)
OLIVE_DARK = M.painted(M.OLIVE_RAMP, tone=0.42, wear=0.18)
NATO = M.painted(ramp_of(P.NATO_GREEN), tone=0.5, wear=0.2)
GUN = M.painted(M.ARMOUR_RAMP, tone=0.55, wear=0.2)
DARK = M.painted(M.ARMOUR_RAMP, tone=0.46, wear=0.18, rust=0.04)
PLINTH = M.painted(M.ARMOUR_RAMP, tone=0.46, wear=0.18)
STEEL = M.steel(tone=0.6, brushed=0.06)
BRIGHT = M.steel(tone=0.72, brushed=0.06, grime=0.035)          # stainless, kept clean
GALV = M.galvanized(tone=0.6)
COPPER = M.Material(ramp=M.COPPER_RAMP, tone=0.58, mottle=0.02, grain=0.02, dither=0.4, edge_light=0.16, edge_dark=0.1, ao=0.35,
                    grime=0.0, dust=0.0, brushed=0.05)
BRASS = M.Material(ramp=ramp_of(P.BRASS), tone=0.58, mottle=0.03, grain=0.03, dither=0.4, edge_light=0.16, edge_dark=0.1, ao=0.35,
                   grime=0.02, dust=0.0, brushed=0.04)
RUBBER = M.rubber(tone=0.45)
CERAMIC = M.white_paint(tone=0.82)
WHITE = M.white_paint(tone=0.74, wear=0.12)
YELLOW = M.painted(M.YELLOW_PAINT_RAMP, tone=0.6, wear=0.3)
ORANGE = M.painted(M.ORANGE_PAINT_RAMP, tone=0.55, wear=0.25)
RED = M.painted(M.RED_PAINT_RAMP, tone=0.48, wear=0.2)
BLACK = M.painted(M.BLACK_RAMP, tone=0.45, wear=0.1)
HAZARD = M.stripes(width=2, wear=0.35, under=M.STEEL_RAMP[2:7])
GRAPHITE = M.Material(ramp=M.ramp("#101114", "#1c1e23", "#2c2f36", "#3e424b", "#555a65", "#737986", "#a4abb8"), tone=0.5, mottle=0.05,
                      grain=0.05, dither=0.7, edge_light=0.18, edge_dark=0.1, ao=0.4, grime=0.0, dust=0.0, despeckle=False)
REFRACTORY = M.Material(ramp=M.ramp("#2a1a12", "#4a2c1e", "#6c4430", "#8c5c42", "#a87a5c", "#c49c7e", "#e2c6aa"), tone=0.5, mottle=0.06,
                        grain=0.06, dither=0.8, edge_light=0.12, edge_dark=0.1, ao=0.5, grime=0.1, dust=0.04, despeckle=False)


def ring(f: M.Face) -> np.ndarray:
    """How far in from the face's own border each texel is (0 on its outer row)."""
    b = f.border
    return np.minimum(np.minimum(b["top"], b["bottom"]), np.minimum(b["left"], b["right"]))


class Kit:
    """A machine model being painted: its parts by group, the Surfacer, and the shared details."""
    OLIVE, OLIVE_DARK, NATO, GUN, DARK, STEEL, BRIGHT, GALV = OLIVE, OLIVE_DARK, NATO, GUN, DARK, STEEL, BRIGHT, GALV
    COPPER, BRASS, RUBBER, CERAMIC, WHITE, YELLOW, ORANGE, RED, BLACK, HAZARD = (COPPER, BRASS, RUBBER, CERAMIC, WHITE, YELLOW,
                                                                                ORANGE, RED, BLACK, HAZARD)
    GRAPHITE, REFRACTORY = GRAPHITE, REFRACTORY

    def __init__(self, g, parts: dict, seed: int, ao_reach: float = 3.0, period: int | None = None, context=()):
        self.g = g
        self.parts = parts
        self.S = M.Surfacer(g, seed=seed, ao_reach=ao_reach, period=period, context=context)

    # -- picking parts -------------------------------------------------------------------------------------------------
    def names(self, *keys: str) -> list[str]:
        out: list[str] = []
        for key in keys:
            out += [b.name for b in self.parts.get(key, [])]
        return out

    def _resolve(self, keys) -> list[str]:
        if isinstance(keys, str):
            keys = [keys]
        out: list[str] = []
        for k in keys:
            out += self.names(k) if k in self.parts else [k]
        return out

    def paint(self, tex, keys, material: M.Material, only: Iterable[str] | None = None) -> None:
        names = self._resolve(keys)
        if names:
            self.S.paint(tex, names, material, only=only)

    def overlay(self, tex, keys, fn: Callable[[M.Face, np.ndarray], None], only: Iterable[str] | None = None) -> None:
        names = self._resolve(keys)
        if names:
            self.S.overlay(tex, names, fn, only=only)

    def on(self, f: M.Face, material: M.Material) -> np.ndarray:
        return self.S.material_on(f, material)

    # -- shared details --------------------------------------------------------------------------------------------------
    def plinth(self, tex, key: str = "plinth", front: str = "north", band: bool = True) -> None:
        """The machines' plinth: dark steel, a hazard band on its front, a tread-plate top seen round the machine."""
        self.paint(tex, key, PLINTH)
        if band:
            self.paint(tex, key, HAZARD, only=(front,))

        def top(f, img):
            a, b = f.plane_uv()
            bar = ((np.floor(a) % 4) == 1) & ((np.floor(b + 2 * (np.floor(a / 4) % 2)) % 4) == 1)
            M.lighten(img, bar, 1.25, 8)
            M.darken(img, np.roll(bar, 1, axis=0) & ~bar, 0.72)
            M.darken(img, ring(f) < 1, 0.72)
        self.overlay(tex, key, top, only=("up",))

    def panels(self, tex, keys, inset: int = 2, rivets: bool = True, only: Iterable[str] | None = None, min_size: int = 7) -> None:
        """A panel seam round each big upright face {inset} texels in, with a rivet at each corner of it."""
        def fn(f, img):
            if not f.vertical or min(f.shape) < min_size:
                return
            r = ring(f)
            line = r == inset
            M.darken(img, line, 0.62)
            M.lighten(img, (r == inset + 1) & ((f.border["top"] == inset + 1) | (f.border["left"] == inset + 1)), 1.08, 3)
            if rivets:
                b = f.border
                corner = (np.minimum(b["top"], b["bottom"]) == inset - 1) & (np.minimum(b["left"], b["right"]) == inset - 1)
                M.lighten(img, corner, 1.3, 14)
        self.overlay(tex, keys, fn, only=only)

    def seams(self, tex, keys, every: float, axis: str = "v", offset: float = 0.0, only: Iterable[str] | None = None) -> None:
        self.overlay(tex, keys, lambda f, img: M.seams(f, img, every=every, axis=axis, offset=offset) if f.vertical else None, only=only)

    def ribs(self, tex, keys, every: float = 2.0, k: float = 0.68, along: str = "v", only: Iterable[str] | None = None) -> None:
        """Cooling fins or windings: every other band darkened, across ({along} 'v': horizontal bands up the face)."""
        def fn(f, img):
            a, b = f.plane_uv()
            c = b if along == "v" else a
            M.darken(img, (np.floor(c * 2 / every) % 2) == 1, k)
        self.overlay(tex, keys, fn, only=only)

    def soot(self, tex, keys, reach: float = 4.0, strength: float = 0.7, edge: str = "top", only: Iterable[str] | None = None) -> None:
        """Exhaust soot spreading from one edge of each face (a stack's lip, round a vent)."""
        def fn(f, img):
            amount = np.clip(1 - f.border[edge] / reach, 0, 1)
            M.sooted(f, img, amount, strength)
        self.overlay(tex, keys, fn, only=only)

    def socket(self, tex, keys, face: str, ramp: np.ndarray, inset: int = 3) -> None:
        """A port's open face: striped paint round a dark bore, its far lip catching the light."""
        band = M.Striped(ramp=ramp, tone=0.6, dither=0.4, wear=0.3, under=M.STEEL_RAMP[2:7], width=2,
                         other=M.painted(M.BLACK_RAMP, tone=0.5, wear=0.3))

        def fn(f, img):
            r = ring(f)
            M.paste(img, self.on(f, band), r < inset)
            img[r >= inset, :3] = P.BLACK_PAINT.deep[:3]
            M.darken(img, r == inset, 0.5)
            M.lighten(img, (r >= inset) & ((f.border["bottom"] == inset) | (f.border["right"] == inset)), 1.0, 24)
        self.overlay(tex, keys, fn, only=(face,))


# --- glyphs: drawn with Tex over the material (screens, dials, lamps, plates) ---------------------------------------------

def crt(t, x: int, y: int, w: int, h: int, glow=None, trace: bool = True) -> None:
    """A small CRT: dark glass in a gunmetal bezel, a phosphor trace across it."""
    glow = glow or P.INDICATOR_GREEN
    t.rect(x, y, w, h, P.BLACK_PAINT.deep)
    t.outline(x - 1, y - 1, w + 2, h + 2, P.GUNMETAL[2])
    t.hline(x - 1, y + h, w + 2, P.GUNMETAL[4])
    if trace:
        for xx in range(x, x + w):
            t.px(xx, y + h // 2 + ((xx * 3) % 3 == 0) - ((xx * 5) % 4 == 1), glow[4])
        t.px(x, y, glow[5])


def dial(t, x: int, y: int, size: int = 3, needle=None) -> None:
    """A round gauge: brass rim, white face, a dark needle."""
    t.rect(x, y, size, size, P.BRASS[3])
    if size >= 3:
        t.rect(x + 1, y + 1, size - 2, size - 2, P.WHITE_CERAMIC[5])
    t.px(x + size // 2, y + size // 2, needle or P.BLACK_PAINT[1])


def plate(t, x: int, y: int, w: int = 4, h: int = 2) -> None:
    """A data plate: black with a yellow line of text."""
    t.rect(x, y, w, h, P.BLACK_PAINT.base)
    t.hline(x + 1, y + h // 2, max(1, w - 2), P.HAZARD_YELLOW.base)


def lamps(t, x: int, y: int, colours=None) -> None:
    """A row of indicator lamps, one texel each."""
    for i, c in enumerate(colours or (P.INDICATOR_GREEN.light, P.NUCLEAR_AMBER.light, P.INDICATOR_RED.light)):
        t.px(x + i * 2, y, c)


def trefoil(t, x: int, y: int) -> None:
    """A 4x4 radiation placard."""
    t.rect(x, y, 4, 4, P.HAZARD_YELLOW.base)
    for px, py in ((1, 1), (2, 1), (0, 3), (3, 3), (1, 0)):
        t.px(x + px, y + py, P.HAZARD_BLACK.base)
