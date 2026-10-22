"""Machine faces for 16x16 block textures (the remaster after slice 9b; DESIGN.md §4.3): a bevelled steel frame round a
panel, both painted with the material library as tiles (their noise wraps, so neighbouring blocks join), and the
details machines are built from: bolts, openings, louvres, hazard stripes. Shading as pixel art: lips and grooves one
texel wide and solid, never dithered.

    img = face(M.painted(M.OLIVE_RAMP), style="inset")
    stripes(img, area(2, 13, 12, 1), YELLOW, BLACK)
    t = M.to_tex(img)                                   # then draw glyphs with Tex as before
"""
from __future__ import annotations

import numpy as np

from . import materials as M

#: The frame's steel, and the paints and metals machine faces are made of.
STEEL = M.steel(tone=0.56, brushed=0.06)
GUNMETAL = M.painted(M.ARMOUR_RAMP, tone=0.5, wear=0.0)
YELLOW = M.painted(M.YELLOW_PAINT_RAMP, tone=0.625, wear=0.0, dither=0.4)
BLACK = M.painted(M.BLACK_RAMP, tone=0.5, wear=0.0, dither=0.4)


def grid() -> tuple[np.ndarray, np.ndarray]:
    return M.grid_xy(16)


def ring() -> np.ndarray:
    """How far in from the block's edge each texel is (0 on the outer row)."""
    col, row = grid()
    return np.minimum(np.minimum(col, 15 - col), np.minimum(row, 15 - row))


def area(x: int, y: int, w: int, h: int) -> np.ndarray:
    col, row = grid()
    return (col >= x) & (col < x + w) & (row >= y) & (row < y + h)


def outline(x: int, y: int, w: int, h: int) -> np.ndarray:
    return area(x, y, w, h) & ~area(x + 1, y + 1, w - 2, h - 2)


def tile(material: M.Material, seed: int = 0) -> np.ndarray:
    return M.tile(material, seed)


def bevel_box(img: np.ndarray, x: int, y: int, w: int, h: int, raised: bool = True, strength: float = 1.0) -> None:
    """A box's rim lit on its top and left and shaded on its bottom and right (raised), or the other way (set in)."""
    col, row = grid()
    inside = area(x, y, w, h)
    top, left = inside & (row == y), inside & (col == x) & (row > y)
    bottom, right = inside & (row == y + h - 1) & (col > x), inside & (col == x + w - 1) & (row > y) & (row < y + h - 1)
    lit, shade = (top | left, bottom | right) if raised else (bottom | right, top | left)
    M.lighten(img, lit, 1 + 0.18 * strength, 6 * strength)
    M.darken(img, shade, 1 - 0.32 * strength)


def face(panel: M.Material, frame: M.Material = STEEL, style: str = "inset", seed: int = 0, bolts: bool = True) -> np.ndarray:
    """A machine block's face: a steel frame two texels wide, bevelled (lit on its top and left, shaded on its bottom
    and right), a bolt in each corner, round a panel of {panel} set in (in the frame's shadow at its top and left) or
    raised (its own lit rim)."""
    img = tile(frame, seed)
    r = ring()
    inner = tile(panel, seed + 1)
    img[r >= 2] = inner[r >= 2]
    bevel_box(img, 0, 0, 16, 16, True, 1.2)
    bevel_box(img, 1, 1, 14, 14, True, 0.6)
    if style == "inset":
        bevel_box(img, 2, 2, 12, 12, False, 1.0)
    elif style == "raised":
        bevel_box(img, 2, 2, 12, 12, True, 0.8)
    if bolts:
        for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
            bolt(img, x, y)
    return img


def bolt(img: np.ndarray, x: int, y: int) -> None:
    """A bolt head: lit, its shadow below and to the right."""
    rgb = img[..., :3].astype(np.float32)
    rgb[y, x] = np.minimum(255, rgb[y, x] * 1.4 + 18)
    if y + 1 < 16 and x + 1 < 16:
        rgb[y + 1, x + 1] *= 0.6
    img[..., :3] = rgb.astype(np.uint8)


def paste(img: np.ndarray, mask: np.ndarray, material: M.Material, seed: int = 0) -> None:
    layer = tile(material, seed)
    img[mask] = layer[mask]


def stripes(img: np.ndarray, mask: np.ndarray, a: M.Material = YELLOW, b: M.Material = BLACK, width: int = 2, seed: int = 0) -> None:
    """45-degree stripes ("/", {width} texels each) of two paints, within a mask."""
    col, row = grid()
    band = (((col + row) // width) % 2) == 0
    paste(img, mask & band, a, seed)
    paste(img, mask & ~band, b, seed + 1)


def opening(img: np.ndarray, x: int, y: int, w: int, h: int, rim: M.Material = GUNMETAL, depth: float = 0.16) -> None:
    """A dark opening with a rim: its far wall's lip catching the light at the bottom and right, deep shadow under the
    top and left."""
    paste(img, outline(x, y, w, h), rim, 7)
    inner = area(x + 1, y + 1, w - 2, h - 2)
    rgb = img[..., :3].astype(np.float32)
    rgb[inner] = np.array([20, 22, 26], dtype=np.float32)
    img[..., :3] = rgb.astype(np.uint8)
    col, row = grid()
    M.lighten(img, inner & ((row == y + h - 2) | (col == x + w - 2)), 1.0, 22)
    bevel_box(img, x, y, w, h, False, 0.8)


def louvres(img: np.ndarray, x: int, y: int, w: int, h: int, slat: int = 2) -> None:
    """Horizontal louvres: dark gaps between slats whose top edges catch the light."""
    col, row = grid()
    inside = area(x, y, w, h)
    gap = inside & (((row - y) % slat) == slat - 1)
    M.darken(img, gap, 0.3)
    M.lighten(img, inside & (((row - y) % slat) == 0), 1.15, 6)


def groove(img: np.ndarray, x: int, y: int, w: int, h: int) -> None:
    """A seam round a hatch: a dark groove with a lit lip inside it at the bottom and right."""
    M.darken(img, outline(x, y, w, h), 0.6)
    col, row = grid()
    M.lighten(img, area(x + 1, y + 1, w - 2, h - 2) & ((row == y + h - 2) | (col == x + w - 2)), 1.1, 4)
