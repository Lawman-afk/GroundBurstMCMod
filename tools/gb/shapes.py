"""Higher-level shapes built from model boxes: pixel-stepped cylinders and lathe profiles.

Round things are built the Minecraft way: a pixel circle cross-section made of non-overlapping row bands, so the
silhouette is a clean stepped circle and every face is axis-aligned (no coplanar overlaps, no z-fighting).
"""
from __future__ import annotations

import math
from dataclasses import dataclass

from .model import BlockModel, Box


def circle_rows(diameter: int) -> list[int]:
    """Widths of each pixel row of a filled pixel circle (top to bottom). Parity matches the diameter so the
    shape stays centred on whole/half pixels."""
    r = diameter / 2
    rows = []
    for i in range(diameter):
        y = i + 0.5 - r
        hw = math.sqrt(max(0.0, r * r - y * y))
        if diameter % 2 == 0:
            w = max(2, 2 * round(hw))
        else:
            w = max(1, 2 * round(hw - 0.5) + 1)
        rows.append(min(w, diameter))
    return rows


def circle_bands(diameter: int) -> list[tuple[int, int, int]]:
    """Merges equal-width rows into bands: (row_start, row_count, width), top to bottom."""
    bands: list[tuple[int, int, int]] = []
    for i, w in enumerate(circle_rows(diameter)):
        if bands and bands[-1][2] == w:
            s, n, _ = bands[-1]
            bands[-1] = (s, n + 1, w)
        else:
            bands.append((i, 1, w))
    return bands


@dataclass
class Disc:
    """Boxes making up one cylinder slab, with the band index of each (0 = top band)."""
    boxes: list[Box]
    bands: list[tuple[int, int, int]]
    diameter: int


def cylinder_z(model: BlockModel, name: str, cx: float, cy: float, z0: float, z1: float, diameter: int,
               faces_ns: bool = True, light: int = 0, shade: bool = False) -> Disc:
    """A cylinder along Z (north-south) centred on (cx, cy). Returns its band boxes, top band first.

    shade=False by default: curvature lighting is baked into the texture with band_tone(), otherwise the game's
    per-direction shading lights every 1-px ledge and the cylinder looks ribbed."""
    bands = circle_bands(diameter)
    top = cy + diameter / 2
    boxes = []
    for i, (row, count, width) in enumerate(bands):
        y1 = top - row
        y0 = y1 - count
        x0, x1 = cx - width / 2, cx + width / 2
        faces = ("east", "west", "up", "down") + (("north", "south") if faces_ns else ())
        # Hide up/down faces that are completely covered by the neighbouring wider band.
        faces = tuple(f for f in faces if not _covered(bands, i, f))
        boxes.append(model.box(f"{name}_b{i}", (x0, y0, z0), (x1, y1, z1), faces=faces, light=light, shade=shade))
    return Disc(boxes, bands, diameter)


def cylinder_y(model: BlockModel, name: str, cx: float, cz: float, y0: float, y1: float, diameter: int,
               faces_ud: bool = True, light: int = 0) -> Disc:
    """A vertical cylinder centred on (cx, cz): bands run along Z (north to south)."""
    bands = circle_bands(diameter)
    north = cz - diameter / 2
    boxes = []
    for i, (row, count, width) in enumerate(bands):
        z0 = north + row
        z1 = z0 + count
        x0, x1 = cx - width / 2, cx + width / 2
        faces = ("east", "west", "north", "south") + (("up", "down") if faces_ud else ())
        faces = tuple(f for f in faces if not _covered_y(bands, i, f))
        boxes.append(model.box(f"{name}_b{i}", (x0, y0, z0), (x1, y1, z1), faces=faces, light=light))
    return Disc(boxes, bands, diameter)


def _covered(bands: list[tuple[int, int, int]], i: int, face: str) -> bool:
    # For a Z cylinder the up face of band i is covered if the band above (i-1) is at least as wide; never true
    # for a circle (bands narrow toward the top), so only down faces of the upper half / up faces of the lower
    # half can be hidden: up face of band i is covered by band i-1 when width(i-1) >= width(i).
    if face == "up" and i > 0:
        return bands[i - 1][2] >= bands[i][2]
    if face == "down" and i < len(bands) - 1:
        return bands[i + 1][2] >= bands[i][2]
    return False


def _covered_y(bands: list[tuple[int, int, int]], i: int, face: str) -> bool:
    if face == "north" and i > 0:
        return bands[i - 1][2] >= bands[i][2]
    if face == "south" and i < len(bands) - 1:
        return bands[i + 1][2] >= bands[i][2]
    return False


def band_tone(disc: Disc, box: Box, face: str, base: int = 3) -> int:
    """Baked curvature lighting for an unshaded stepped cylinder lit from above: tone depends only on the band's
    height, so a band's 1-px ledge and its side face match and the steps read as one smooth curve. End caps
    (north/south faces) use the same gradient."""
    i = disc.boxes.index(box)
    row, count, _ = disc.bands[i]
    centre = (row + count / 2) / disc.diameter          # 0 = top, 1 = bottom
    t = base + round(2.2 * (0.5 - centre) * 2)          # about +2 at the top .. -2 at the bottom
    if face in ("north", "south"):
        t -= 0                                          # end caps share the band gradient
    return max(0, min(6, t))


def lathe_z(model: BlockModel, name: str, cx: float, cy: float, profile: list[tuple[float, float, int]]) -> list[Disc]:
    """A turned body along Z: profile = [(z0, z1, diameter), ...] from north to south."""
    return [cylinder_z(model, f"{name}{i}", cx, cy, z0, z1, d) for i, (z0, z1, d) in enumerate(profile)]
