"""Missile liveries (the remaster after slice 9b; DESIGN.md §4.3): every missile painted with the material library
(tools/gz/materials.py), its markings laid out in the missile's own item pixels, so a missile looks the same as an
item, in flight (its own finer flight model), standing on a launcher and lying on the Strategic Assembly Hall's
cradles.

    S = M.Surfacer(model)                       # after model.layout()
    paint(S, tex, [(box name, kind, nose), ...], Frame(4, (0, 14, 32)), "basic")

A Frame says where the missile's item pixels are in the model. Fine details (panel seams, rivets, data plates,
stencils) are drawn only where a texel is at most half an item pixel: on the launchers and in flight, not on the
inventory icon, where they would only be noise. Families: "basic" (missile_parts), "strategic" (strategic_parts),
"icbm" (icbm_parts and icbm_nose_parts).
"""
from __future__ import annotations

import dataclasses
from dataclasses import dataclass

import numpy as np

from gz import materials as M
from gz.tex import Tex
import warheads as W


@dataclass
class Frame:
    """Model point = (item point - (8, 0, 8)) * scale + origin. Lying (the hall): the item's length runs toward -z and
    its z is the model's height (origin then gives x, the axis height and the z of item y = 0)."""
    scale: float = 1.0
    origin: tuple = (8.0, 0.0, 8.0)
    lying: bool = False

    def item(self, f: M.Face) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
        ox, oy, oz = self.origin
        s = self.scale
        if self.lying:
            return (f.X - ox) / s + 8, (oz - f.Z) / s, (f.Y - oy) / s + 8
        return (f.X - ox) / s + 8, (f.Y - oy) / s, (f.Z - oz) / s + 8

    def texel(self, f: M.Face) -> float:
        """One texel of this face, in item pixels."""
        return float(max(np.linalg.norm(f.du), np.linalg.norm(f.dv))) / self.scale

    def side(self, f: M.Face) -> str:
        """Which side of the missile (in item space) a face is on: north, south, east, west, up (toward the nose) or
        down (the tail)."""
        if not self.lying:
            return f.d
        return {"north": "up", "south": "down", "up": "south", "down": "north"}.get(f.d, f.d)


def _ramp(r) -> np.ndarray:
    """A palette ramp (gz.palette.Ramp) as a material ramp."""
    return np.array([s[:3] for s in r.shades], dtype=np.float32)


def _ramp_round(c) -> np.ndarray:
    """A short ramp round one colour (a marking band's paint)."""
    c = np.array(c[:3], dtype=np.float32)
    return np.stack([c * 0.5, c * 0.68, c * 0.84, c, np.minimum(255, c * 1.1 + 8), np.minimum(255, c * 1.22 + 18)])


def _band(iy: np.ndarray, lo: float, hi: float, t: float) -> np.ndarray:
    """Item y in [lo, hi); a band thinner than a texel still takes the row nearest its middle."""
    if hi - lo >= t:
        return (iy >= lo) & (iy < hi)
    return np.abs(iy - (lo + hi) / 2 - 1e-3) < t / 2


def _across(f: M.Face, ix, iz) -> np.ndarray | None:
    """The coordinate across a side face, round the missile (None on its ends)."""
    if np.ptp(ix) < 1e-6 and np.ptp(iz) < 1e-6:
        return None
    return ix if np.ptp(ix) >= np.ptp(iz) else iz


def _span(a: np.ndarray, t: float) -> tuple[float, float]:
    return float(a.min()) - t / 2, float(a.max()) + t / 2


class _Painter:
    def __init__(self, S: M.Surfacer, tex: Tex, frame: Frame, glow: Tex | None):
        self.S, self.tex, self.frame, self.glow = S, tex, frame, glow

    def rounded(self, mat: M.Material, strength: float = 0.05) -> M.Material:
        """A material with each side face lit down its middle, darker toward its sides: round bodies read round."""
        frame = self.frame

        def extra(f: M.Face) -> np.ndarray:
            ix, iy, iz = frame.item(f)
            a = _across(f, ix, iz)
            if a is None or np.ptp(iy) < 1e-6:
                return np.zeros(f.shape, dtype=np.float32)
            t = frame.texel(f)
            a0, a1 = _span(a, t)
            half = (a1 - a0) / 2
            if half < 0.75:
                return np.zeros(f.shape, dtype=np.float32)
            return (strength * (1 - ((a - (a0 + a1) / 2) / half) ** 2) - strength / 3).astype(np.float32)
        return dataclasses.replace(mat, extra=extra)

    def base(self, names, mat: M.Material) -> None:
        self.S.paint(self.tex, names, mat)

    def mark(self, names, fn) -> None:
        """fn(f, img, ix, iy, iz, t) changes a face's pixels, with its item coordinates and texel size."""
        frame = self.frame

        def over(f, img):
            ix, iy, iz = frame.item(f)
            fn(f, img, ix, iy, iz, frame.texel(f))
        self.S.overlay(self.tex, names, over)

    def paste(self, f, img, mat: M.Material, mask) -> None:
        if np.any(mask):
            M.paste(img, self.S.material_on(f, mat), mask)


# --- the paints -------------------------------------------------------------------------------------------------------------
def factory(ramp: np.ndarray, tone: float = 0.55, **kw) -> M.Material:
    """A missile's paint: a factory finish, smooth and clean (after the first look: the structures' weathering, worn
    edges and shadowed corners, read as dirt on a missile; and a round body has no corners to shade: its plus-shaped
    boxes only stand in for a cylinder). Lit edges, a hint of grime at the very foot."""
    d = dict(tone=tone, mottle=0.012, grain=0.012, dither=0.3, edge_light=0.12, edge_dark=0.07, ao=0.0, grime=0.02,
             dust=0.0, wear=0.02, falloff=0.03)
    d.update(kw)
    return M.painted(ramp, **d)


WHITE = factory(M.WHITE_PAINT_RAMP, tone=0.74)
BLACK = factory(M.BLACK_RAMP, tone=0.55)
FIN = factory(M.GREY_PAINT_RAMP, tone=0.66, wear=0.05)
DARK = factory(M.ARMOUR_RAMP, tone=0.48, wear=0.05)                    # interstages, skirts, gunmetal fins
OLIVE = factory(M.OLIVE_RAMP, tone=0.5)
NOZZLE = M.steel(tone=0.34, brushed=0.04)
PLATE = M.painted(M.BLACK_RAMP, tone=0.7, wear=0.0)               # data plates
STEEL_LINE = M.steel(tone=0.7)
CANVAS = M.Material(ramp=_ramp(W.CANVAS), tone=0.55, mottle=0.05, grain=0.05, dither=0.7, edge_light=0.1, edge_dark=0.12,
                    ao=0.5, grime=0.06, dust=0.03, brushed=0.06, despeckle=False)
TSAR = factory(_ramp(W.TSAR), tone=0.55)


def paint(S: M.Surfacer, tex: Tex, parts, frame: Frame, family: str, glow: Tex | None = None) -> None:
    """Paints a missile's parts: [(box name, kind, nose)] as missile_parts / strategic_parts / icbm_parts name them."""
    p = _Painter(S, tex, frame, glow)
    groups: dict[tuple[str, str], list[str]] = {}
    for name, kind, nose in parts:
        groups.setdefault((kind, nose), []).append(name)
    for (kind, nose), names in groups.items():
        _paint_kind(p, names, kind, nose, family)


def _paint_kind(p: _Painter, names, kind: str, nose: str, family: str) -> None:
    sides = ("north", "south", "east", "west")
    if kind == "body":                                                  # the basic missile's white body
        p.base(names, p.rounded(WHITE))

        def body(f, img, ix, iy, iz, t):
            if p.frame.side(f) not in sides:
                return
            p.paste(f, img, p.rounded(BLACK), _band(iy, 21, 23, t) | _band(iy, 9, 11, t))   # two roll bands
            if t <= 0.5:
                for y in (16.0, 4.0):                                   # section joints: a groove and a lit lip under it
                    M.darken(img, np.abs(iy - y) < t / 2 + 1e-4, 0.62)
                    M.lighten(img, np.abs(iy - (y - t)) < t / 2 + 1e-4, 1.08, 3)
                a = _across(f, ix, iz)
                heads = (np.abs(iy - 25.3) < t / 2 + 1e-4) & ((np.floor(a / 0.75) % 2) == 0)
                M.lighten(img, heads, 1.12, 6)                          # rivets round the nose joint
                if p.frame.side(f) == "north":                          # a data plate on the front
                    plate = (np.abs(a - 8) < 0.9) & (iy > 13.0) & (iy < 14.6)
                    p.paste(f, img, PLATE, plate)
                    p.paste(f, img, STEEL_LINE, plate & (np.abs(iy - 13.8) < 0.13))
        p.mark(names, body)
    elif kind in ("nose_base", "nose", "tip"):
        ramp, mark, tip = W.NOSES[nose]
        mat = factory(_ramp(tip if kind == "tip" else ramp), tone=0.56)
        p.base(names, p.rounded(mat))
        if kind == "nose_base" and mark is not None:
            band = p.rounded(factory(_ramp_round(mark), tone=0.6))

            def marking(f, img, ix, iy, iz, t):
                if p.frame.side(f) not in sides:
                    return
                lo, hi = _span(iy, t)
                if family == "icbm":                                    # round the shroud's middle
                    c = (lo + hi) / 2
                    p.paste(f, img, band, _band(iy, c - 0.4, c + 0.4, t))
                else:                                                   # at the cone's foot
                    p.paste(f, img, band, _band(iy, lo, lo + 0.75, t))
            p.mark(names, marking)
    elif kind == "fin":                                                 # the basic missile's fins: black trailing edges
        p.base(names, FIN)
        p.mark(names, lambda f, img, ix, iy, iz, t: p.paste(f, img, BLACK, _band(iy, -1, 1.5 + 0.75, t))
               if p.frame.side(f) in sides else None)
    elif kind == "nozzle":
        p.base(names, NOZZLE)

        def exhaust(f, img, ix, iy, iz, t):
            if p.frame.side(f) == "down":                               # the open end, dark inside its rim
                rim = np.minimum(np.minimum(f.border["top"], f.border["bottom"]), np.minimum(f.border["left"], f.border["right"]))
                M.darken(img, rim >= max(1, round(0.25 / t)), 0.18)
            else:
                M.sooted(f, img, np.full(f.shape, 0.35), 0.4)              # blackened by its own exhaust
        p.mark(names, exhaust)
    elif kind.startswith("stage1_"):                                    # the Strategic first stage's roll pattern
        quadrant = kind[len("stage1_"):]
        black_first = quadrant in ("ne", "sw")
        white, black = p.rounded(WHITE), p.rounded(BLACK)
        p.base(names, white)

        def roll(f, img, ix, iy, iz, t):
            if p.frame.side(f) in sides:
                band = np.clip(np.floor((8 - iy) / 6), 0, 2)
            else:
                band = np.full(f.shape, 2.0)
            is_black = (band % 2 == 0) == black_first
            p.paste(f, img, black, is_black)
        p.mark(names, roll)
    elif kind in ("interstage", "iinter"):                               # dark rings riveted round
        p.base(names, p.rounded(DARK))

        def rivets(f, img, ix, iy, iz, t):
            a = _across(f, ix, iz)
            if a is None or p.frame.side(f) not in sides:
                return
            lo, hi = _span(iy, t)
            c = (lo + hi) / 2
            pitch = 1.0 if t > 0.5 else 0.75
            heads = (np.abs(iy - c) < max(t, 0.13) / 2 + 1e-4) & ((np.floor(a / pitch) % 2) == 0)
            M.lighten(img, heads, 1.35, 12)
            M.darken(img, np.roll(heads, 1, axis=0) & ~heads, 0.75)
        p.mark(names, rivets)
    elif kind == "stage2":                                              # the Strategic second stage
        p.base(names, p.rounded(WHITE))

        def stage2(f, img, ix, iy, iz, t):
            if p.frame.side(f) not in sides:
                return
            p.paste(f, img, p.rounded(BLACK), _band(iy, 9.2, 10.8, t))
            if t <= 0.5:
                M.darken(img, np.abs(iy - 16.2) < t / 2 + 1e-4, 0.65)    # a panel joint
                if p.frame.side(f) == "north":
                    a = _across(f, ix, iz)
                    plate = (np.abs(a - 8) < 0.9) & (iy > 17.2) & (iy < 19.0)
                    p.paste(f, img, PLATE, plate)
                    p.paste(f, img, STEEL_LINE, plate & (np.abs(iy - 18.1) < 0.13))
        p.mark(names, stage2)
    elif kind == "sfin":                                                # black fins, their top edges white
        p.base(names, BLACK)

        def edge(f, img, ix, iy, iz, t):
            if p.frame.side(f) in sides:
                hi = _span(iy, t)[1]
                p.paste(f, img, WHITE, iy > hi - max(t, 0.5))
        p.mark(names, edge)
    elif kind == "iskirt":
        p.base(names, p.rounded(dataclasses.replace(DARK, tone=0.4)))
    elif kind in ("istage1", "istage2", "istage3"):                     # olive drab, a band round each stage's middle
        colour = {"istage1": (236, 236, 228, 255), "istage2": (236, 196, 64, 255), "istage3": (236, 236, 228, 255)}[kind]
        band = p.rounded(factory(_ramp_round(colour), tone=0.6))
        p.base(names, p.rounded(OLIVE))

        def stage(f, img, ix, iy, iz, t):
            if p.frame.side(f) not in sides:
                return
            lo, hi = _span(iy, t)
            c = (lo + hi) / 2
            p.paste(f, img, band, _band(iy, c - 0.4, c + 0.4, t))
            if t <= 0.5:
                a = _across(f, ix, iz)
                if p.frame.side(f) == "east":                           # a cable raceway down the stage
                    run = (np.abs(a - 8) < 0.3) & (iy > lo + 0.4) & (iy < hi - 0.4) & ~_band(iy, c - 0.5, c + 0.5, t)
                    M.darken(img, run, 0.78)
                    M.lighten(img, run & (np.abs(a - 8 + 0.2) < t / 2 + 1e-4), 1.12, 4)
                if p.frame.side(f) == "north" and kind == "istage1":    # a stencilled marking under the band
                    word = (iy > c - 2.2) & (iy < c - 1.75) & (np.abs(a - 8) < 1.2) & ((np.floor((a - 8) / 0.3) % 3) != 2)
                    p.paste(f, img, dataclasses.replace(WHITE, tone=0.7), word)
                for y in (lo + 0.5, hi - 0.5):                           # weld lines near the ends
                    M.darken(img, np.abs(iy - y) < t / 2 + 1e-4, 0.8)
        p.mark(names, stage)
    elif kind == "ifin":
        p.base(names, DARK)
    elif kind == "tsar_pack":                                           # the canvas parachute pack, strapped
        p.base(names, CANVAS)

        def strap(f, img, ix, iy, iz, t):
            if p.frame.side(f) in sides:
                lo, hi = _span(iy, t)
                M.darken(img, _band(iy, (lo + hi) / 2 - 0.25, (lo + hi) / 2 + 0.25, t), 0.62)
        p.mark(names, strap)
    elif kind in ("tsar_tail", "tsar_body", "tsar_nose", "tsar_fin"):
        p.base(names, p.rounded(TSAR))
        if kind == "tsar_body":                                         # a white stencil band near the shoulder
            p.mark(names, lambda f, img, ix, iy, iz, t: p.paste(f, img, WHITE, _band(iy, 27.6, 28.2, t))
                   if p.frame.side(f) in sides else None)
    elif kind == "tsar_tip":
        p.base(names, M.steel(tone=0.62))
    elif kind == "tsar_vent":                                           # dark slats over glowing slits
        def vent(f, img, ix, iy, iz, t):
            img[...] = W.VENT[0]
            if p.frame.side(f) in ("up", "down"):
                return
            lo = _span(iy, t)[0]
            pitch = max(t, 0.25)
            slit = (np.floor((iy - lo) / pitch) % 2) == 0
            img[slit, :3] = W.VENT[1][:3]
            if p.glow is not None:
                x, y, w, h = f.rect
                g = p.glow.a[y:y + h, x:x + w]
                g[slit] = W.VENT[1]
        p.mark(names, vent)
    else:
        raise ValueError(f"no livery for {kind}")
