"""Small packed block textures (cables, pipes: a 16x16 sheet of regions a model's faces point into) painted with the
material library: each region filled from a material's block tile, shaded across its width where it wraps a round
tube, then the details drawn on as pixel art (the remaster after the Fission Reactor rework; DESIGN.md §4.3).

    fill(t, (0, 0, 4, 6), K.metal(P.COPPER), seed=1, profile=ROUND4)   # a cable's side, lit from its left
    rim(t, (6, 0, 11, 5), 0.7)                                        # a box face's edge in its own shadow
"""
from __future__ import annotations

import numpy as np

from . import materials as M
from .tex import Tex

#: Brightness across a round tube's width, lit from its left: 4 and 6 texels wide.
ROUND4 = (1.2, 1.05, 0.9, 0.74)
ROUND6 = (1.18, 1.18, 1.03, 1.03, 0.9, 0.76)


def render(rect, material: M.Material, seed: int = 0, profile=None) -> np.ndarray:
    """A region (x0, y0, x1, y1) of a material's 16x16 block tile, shaded per column by {profile}: (h, w, 4) floats."""
    x0, y0, x1, y1 = rect
    img = M.tile(material, seed)[y0:y1, x0:x1].astype(np.float32)
    if profile is not None:
        k = np.asarray(profile, dtype=np.float32)[: x1 - x0]
        img[..., :3] *= k[None, :, None]
    return np.clip(img, 0, 255)


def fill(t: Tex, rect, material: M.Material, seed: int = 0, profile=None, mask: np.ndarray | None = None) -> None:
    """Paints a region with a material (only where {mask}, a (h, w) bool array, when given)."""
    x0, y0, x1, y1 = rect
    img = render(rect, material, seed, profile).astype(np.uint8)
    if mask is None:
        t.a[y0:y1, x0:x1] = img
    else:
        t.a[y0:y1, x0:x1][mask] = img[mask]


def scale(t: Tex, rect, k: float, mask: np.ndarray | None = None) -> None:
    """Darkens (k < 1) or lightens (k > 1) a region, or the part of it {mask} picks."""
    x0, y0, x1, y1 = rect
    a = t.a[y0:y1, x0:x1].astype(np.float32)
    sel = np.ones(a.shape[:2], bool) if mask is None else mask
    a[sel, :3] = np.clip(a[sel, :3] * k, 0, 255)
    t.a[y0:y1, x0:x1] = a.astype(np.uint8)


def rim(t: Tex, rect, k: float = 0.7, lit: float = 1.15) -> None:
    """A box face's rim: its right and bottom texels in shadow, its top and left catching the light."""
    x0, y0, x1, y1 = rect
    h, w = y1 - y0, x1 - x0
    col, row = np.meshgrid(np.arange(w), np.arange(h))
    scale(t, rect, k, (col == w - 1) | (row == h - 1))
    scale(t, rect, lit, ((col == 0) | (row == 0)) & (col < w - 1) & (row < h - 1))


def grid(rect) -> tuple[np.ndarray, np.ndarray]:
    """Column and row indices within a region (for masks)."""
    x0, y0, x1, y1 = rect
    return np.meshgrid(np.arange(x1 - x0), np.arange(y1 - y0))
