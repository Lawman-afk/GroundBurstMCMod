"""Surface materials for models (the remaster after slice 9b; DESIGN.md §4.3): concrete, painted and bare steel, hazard
stripes, rubber, grating, painted over model geometry the way a texture artist would, not box by box.

What it does, from the research summarised in DESIGN.md §4.3 (Jappa-era vanilla rules as the Faithful packs set them
down; pixel-art metal and weathering practice):

- **World space.** Every texel of a face is placed at its point on the model, and the materials sample their noise
  there, so one concrete slab made of eight boxes reads as one slab, and a stripe runs on across a seam.
- **Edges.** For each face border it works out whether the solid really ends there (an exposed edge: light catches the
  lip, paint chips off it) or carries on in another box (no edge at all) - so wear and highlights land where they would.
- **Ambient occlusion.** Each texel looks out over the face for other parts of the model close above it: contact
  shadows at the feet of posts, in corners, under hoods and down vents.
- **Limited palettes.** A material's tone is worked out as a value, then put onto a short hue-shifted ramp (lit shades
  warmer, shadows cooler) with ordered dithering in world space, its strength by how rough the material is; lone
  pixels are cleaned away. The darkest and lightest shades never meet without one between.
- **Weathering.** Grime gathers low and in corners, dust settles on top, rain streaks hang from top edges, paint chips
  on exposed edges and corners, rust starts in a dark core with an orange halo and runs down under gravity.

    S = Surfacer(model)                       # after model.layout()
    S.paint(tex, ["deck_a", "deck_b"], CONCRETE)
    S.paint(tex, "tower_post0", painted(TOWER_GREY, wear=0.4, rust=0.2))
    S.seams(tex, "deck_a", every=16)          # details on top, in the same world space

Works with BlockModel and GeoModel alike (a GeoModel's boxes only shadow boxes of their own bone).
"""
from __future__ import annotations

from dataclasses import dataclass, field
from typing import Callable, Iterable

import numpy as np

from .model import Box
from .tex import Tex

# --- palettes: hue-shifted ramps, darkest to lightest -------------------------------------------------------------------


def _hex(*codes: str) -> np.ndarray:
    return np.array([[int(c[1:3], 16), int(c[3:5], 16), int(c[5:7], 16)] for c in codes], dtype=np.float32)


def ramp(*codes: str) -> np.ndarray:
    """A ramp of its own, darkest to lightest ("#rrggbb" each)."""
    return _hex(*codes)


CONCRETE_RAMP = _hex("#3a3a3b", "#4b4a48", "#5e5b57", "#726e68", "#87827a", "#9c968c", "#b1aa9f", "#c5beb2", "#d7d1c5")
STEEL_RAMP = _hex("#16191e", "#22262c", "#30353c", "#41474f", "#545b64", "#6a727b", "#848c95", "#a2aab2", "#c3cad1", "#e2e7eb")
GREY_PAINT_RAMP = _hex("#1d2125", "#2a2f34", "#3a4046", "#4c535a", "#60686f", "#767e85", "#8d959b", "#a7aeb3", "#c3c8cc")
YELLOW_PAINT_RAMP = _hex("#3a2908", "#5a400d", "#7e5b14", "#a2781b", "#c39424", "#dcb032", "#ecc84e", "#f5dc80", "#fbecb4")
ORANGE_PAINT_RAMP = _hex("#351304", "#561f08", "#7b2f0d", "#a14214", "#c3571d", "#dc6f2c", "#ec8d4c", "#f5b07c", "#fad3b5")
RED_PAINT_RAMP = _hex("#260905", "#3f1009", "#5d180d", "#7e2214", "#9f2e1b", "#bb4127", "#d2603f", "#e48a6c", "#f0b5a0")
OLIVE_RAMP = _hex("#161a0e", "#212715", "#2d351c", "#3b4524", "#4a562d", "#5b6836", "#707d45", "#8a965d", "#a7b07e")
ARMOUR_RAMP = _hex("#121519", "#1b1f24", "#252a30", "#30363d", "#3d444c", "#4c545d", "#5f6871", "#77818b", "#949ea7")
BLACK_RAMP = _hex("#0b0c0d", "#121315", "#1a1c1f", "#23262a", "#2d3135", "#393d42", "#474c52")
WHITE_PAINT_RAMP = _hex("#5c5f61", "#77797a", "#919394", "#a9abab", "#bfc0bf", "#d1d2cf", "#e0e0dc", "#ecebe6", "#f6f5f0")
RUBBER_RAMP = _hex("#0d0d0e", "#151517", "#1d1e20", "#26282a", "#313336", "#3d4043")
SOOT_RAMP = _hex("#08090a", "#0f1012", "#16181a", "#1f2124", "#292c30", "#35383d", "#43474d")
WOOD_RAMP = _hex("#2a1a0e", "#3e2715", "#55361d", "#6d4626", "#865832", "#9e6b3f", "#b5804f", "#c99866")
GALVANIZED_RAMP = _hex("#2a2e33", "#3b4046", "#4f555c", "#646b72", "#7b8289", "#939aa0", "#acb2b7", "#c6cbcf", "#dfe2e5")
COPPER_RAMP = _hex("#2e1409", "#4b200e", "#6c3016", "#8f4320", "#b0582b", "#c9723c", "#dc9256", "#ebb57e")
RUST_CORE = np.array([59, 30, 18], dtype=np.float32)          # dark, dry rust in the pit (#3B1E12)
RUST_MID = np.array([107, 47, 21], dtype=np.float32)
RUST_HALO = np.array([200, 90, 23], dtype=np.float32)         # fresh orange round it (#C85A17)

# --- noise ----------------------------------------------------------------------------------------------------------------
_U32 = np.uint32


def _hash(ix: np.ndarray, iy: np.ndarray, iz: np.ndarray, seed: int) -> np.ndarray:
    """A float in [0, 1) for each integer lattice point (wrapping integer hash)."""
    with np.errstate(over="ignore"):
        h = (ix.astype(np.int64).astype(_U32) * _U32(374761393) + iy.astype(np.int64).astype(_U32) * _U32(668265263)
             + iz.astype(np.int64).astype(_U32) * _U32(2246822519) + _U32((seed * 3266489917) & 0xFFFFFFFF))
        h = (h ^ (h >> _U32(13))) * _U32(1274126177)
        h = h ^ (h >> _U32(16))
    return (h & _U32(0xFFFFFF)).astype(np.float32) / float(0x1000000)


def value_noise(x: np.ndarray, y: np.ndarray, z: np.ndarray, scale: float | tuple[float, float, float], seed: int,
                period: int | tuple[int, int, int] | None = None) -> np.ndarray:
    """Smooth value noise in [0, 1) at model points; a scale per axis stretches it (streaks, brushing). With a period
    (in lattice cells, one for all axes or one each) it repeats: textures that must tile."""
    sx, sy, sz = scale if isinstance(scale, tuple) else (scale, scale, scale)
    fx, fy, fz = x / sx, y / sy, z / sz
    ix, iy, iz = np.floor(fx), np.floor(fy), np.floor(fz)
    tx, ty, tz = fx - ix, fy - iy, fz - iz
    tx, ty, tz = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty), tz * tz * (3 - 2 * tz)

    pp = (period, period, period) if isinstance(period, (int, float)) else period

    def h(dx, dy, dz):
        ax, ay, az = ix + dx, iy + dy, iz + dz
        if pp:
            ax, ay, az = np.mod(ax, pp[0]), np.mod(ay, pp[1]), np.mod(az, pp[2])
        return _hash(ax, ay, az, seed)
    c00 = h(0, 0, 0) * (1 - tx) + h(1, 0, 0) * tx
    c10 = h(0, 1, 0) * (1 - tx) + h(1, 1, 0) * tx
    c01 = h(0, 0, 1) * (1 - tx) + h(1, 0, 1) * tx
    c11 = h(0, 1, 1) * (1 - tx) + h(1, 1, 1) * tx
    c0 = c00 * (1 - ty) + c10 * ty
    c1 = c01 * (1 - ty) + c11 * ty
    return c0 * (1 - tz) + c1 * tz


def fbm(x, y, z, scale, seed: int, octaves: int = 3, gain: float = 0.5, period: int | None = None) -> np.ndarray:
    """Fractal value noise in [0, 1): octaves halving in scale, each fainter."""
    total = np.zeros_like(x, dtype=np.float32)
    amp, norm = 1.0, 0.0
    s = scale
    p = period
    for k in range(octaves):
        total += amp * value_noise(x, y, z, s, seed + 101 * k, p)
        norm += amp
        amp *= gain
        s = tuple(v / 2 for v in s) if isinstance(s, tuple) else s / 2
        p = (tuple(v * 2 for v in p) if isinstance(p, tuple) else p * 2) if p else None
    return total / norm


def white(x, y, z, seed: int) -> np.ndarray:
    """Per-texel noise in [0, 1), the same for the same texel of the model."""
    return _hash(np.floor(x * 1.0001), np.floor(y * 1.0001), np.floor(z * 1.0001), seed)


def _wrap(scale, period: int):
    """A noise scale near the one asked for that fits a whole number of cells (at least two) into the period, and
    that number: (scale, cells), per axis for a tuple."""
    if isinstance(scale, tuple):
        cells = tuple(max(2, round(period / s)) for s in scale)
        return tuple(period / c for c in cells), cells
    c = max(2, round(period / scale))
    return period / c, c


def noise(f: "Face", scale, seed: int, octaves: int = 1) -> np.ndarray:
    """Fractal value noise over a face's texels in [0, 1); on a tile it wraps (the block tiles seamlessly)."""
    if f.period:
        s, cells = _wrap(scale, f.period)
        return fbm(f.X, f.Y, f.Z, s, seed, octaves, period=cells)
    return fbm(f.X, f.Y, f.Z, scale, seed, octaves)


BAYER4 = (np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]], dtype=np.float32) + 0.5) / 16

# --- faces in model space -------------------------------------------------------------------------------------------------
_NORMALS = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}


@dataclass
class Face:
    """One face being painted: its texels' model-space points (texel centres), and what lies round it."""
    box: Box
    d: str
    rect: tuple[int, int, int, int]
    X: np.ndarray
    Y: np.ndarray
    Z: np.ndarray
    normal: tuple[int, int, int]
    #: Model-space step for one texel to the right (u) and one down (v) on the face.
    du: tuple[float, float, float]
    dv: tuple[float, float, float]
    ao: np.ndarray = field(default=None)                      # 0 open .. 1 shut in
    #: Distance (texels) to each border where the solid really ends there: top, bottom, left, right (inf: not an edge).
    edge: dict = field(default=None)
    #: Texels to the face's own borders, whether or not they are edges.
    border: dict = field(default=None)
    #: Distance (texels) to each border where the surface stops: an exposed edge, or a corner where it meets another
    #: surface (a wall's foot on the floor); inf where it runs on flat into another box. Grime, streaks and the skylight
    #: falloff go by these, so a surface made of several boxes shades as one (no dirty band at each box's foot).
    stop: dict = field(default=None)
    #: A block texture's face: its noise wraps every so many pixels, so the block tiles with its neighbours.
    period: int | None = None

    @property
    def shape(self) -> tuple[int, int]:
        return self.X.shape

    @property
    def vertical(self) -> bool:
        return self.d in ("north", "south", "east", "west")

    def edge_near(self, which: Iterable[str] = ("top", "bottom", "left", "right")) -> np.ndarray:
        """The nearest exposed edge's distance (texels)."""
        out = np.full(self.shape, np.inf, dtype=np.float32)
        for k in which:
            out = np.minimum(out, self.edge[k])
        return out

    def plane_uv(self) -> tuple[np.ndarray, np.ndarray]:
        """Two world coordinates across the face (horizontal, then vertical/depth), for world-aligned patterns."""
        if self.d == "tile":
            return self.X, self.Y
        if self.d in ("up", "down"):
            return self.X, self.Z
        if self.d in ("north", "south"):
            return self.X, self.Y
        return self.Z, self.Y


class Surfacer:
    """Paints a laid-out model's faces with materials, knowing its geometry (see the module notes)."""

    def __init__(self, model, seed: int = 7, ao_reach: float = 4.0):
        self.m = model
        self.seed = seed
        self.ao_reach = ao_reach
        boxes = [b for b in model.boxes if not b.rotation]
        bone_of = getattr(model, "_bone_of", {})
        self._group = {}
        for b in boxes:
            self._group.setdefault(bone_of.get(b.name, ""), []).append(b)
        self._bone_of = bone_of
        self._arrays = {k: np.array([[*b.frm, *b.to] for b in v], dtype=np.float32) for k, v in self._group.items()}
        self._faces: dict[tuple[str, str], Face] = {}

    # -- geometry ---------------------------------------------------------------------------------------------------------
    def face(self, box: Box | str, d: str) -> Face:
        b = self.m.get(box) if isinstance(box, str) else box
        key = (b.name, d)
        if key in self._faces:
            return self._faces[key]
        rect = self.m.rect(b, d)
        x0, y0, z0 = b.frm
        x1, y1, z1 = b.to
        w, h = rect[2], rect[3]
        ii, jj = np.meshgrid(np.arange(w, dtype=np.float32) + 0.5, np.arange(h, dtype=np.float32) + 0.5)
        sx, sy, sz = x1 - x0, y1 - y0, z1 - z0
        if d == "north":
            X, Y, Z = x1 - ii * sx / w, y1 - jj * sy / h, np.full_like(ii, z0)
            du, dv = (-sx / w, 0, 0), (0, -sy / h, 0)
        elif d == "south":
            X, Y, Z = x0 + ii * sx / w, y1 - jj * sy / h, np.full_like(ii, z1)
            du, dv = (sx / w, 0, 0), (0, -sy / h, 0)
        elif d == "east":
            X, Y, Z = np.full_like(ii, x1), y1 - jj * sy / h, z1 - ii * sz / w
            du, dv = (0, 0, -sz / w), (0, -sy / h, 0)
        elif d == "west":
            X, Y, Z = np.full_like(ii, x0), y1 - jj * sy / h, z0 + ii * sz / w
            du, dv = (0, 0, sz / w), (0, -sy / h, 0)
        elif d == "up":
            X, Y, Z = x0 + ii * sx / w, np.full_like(ii, y1), z0 + jj * sz / h
            du, dv = (sx / w, 0, 0), (0, 0, sz / h)
        else:
            X, Y, Z = x0 + ii * sx / w, np.full_like(ii, y0), z1 - jj * sz / h
            du, dv = (sx / w, 0, 0), (0, 0, -sz / h)
        f = Face(b, d, rect, X.astype(np.float32), Y.astype(np.float32), Z.astype(np.float32), _NORMALS[d], du, dv)
        f.border = {"top": jj - 0.5, "bottom": h - jj - 0.5, "left": ii - 0.5, "right": w - ii - 0.5}
        if b.rotation:
            f.ao = np.zeros((h, w), dtype=np.float32)
            f.edge = {k: v.copy() for k, v in f.border.items()}
            f.stop = {k: v.copy() for k, v in f.border.items()}
        else:
            f.ao = self._ao(f)
            f.edge = self._edges(f)
        self._faces[key] = f
        return f

    def _solids(self, b: Box) -> np.ndarray:
        return self._arrays.get(self._bone_of.get(b.name, ""), np.zeros((0, 6), dtype=np.float32))

    @staticmethod
    def _inside(points: np.ndarray, boxes: np.ndarray, eps: float = 1e-3) -> np.ndarray:
        """Which points (..., 3) lie strictly inside any of the boxes (M, 6)."""
        if boxes.shape[0] == 0:
            return np.zeros(points.shape[:-1], dtype=bool)
        p = points[..., None, :]
        lo, hi = boxes[:, :3], boxes[:, 3:]
        inside = np.all((p > lo + eps) & (p < hi - eps), axis=-1)
        return inside.any(axis=-1)

    def _near(self, f: Face, reach: float) -> np.ndarray:
        """The model's boxes (same bone) within reach of a face."""
        boxes = self._solids(f.box)
        lo = np.array([f.X.min(), f.Y.min(), f.Z.min()]) - reach
        hi = np.array([f.X.max(), f.Y.max(), f.Z.max()]) + reach
        keep = np.all((boxes[:, 3:] > lo) & (boxes[:, :3] < hi), axis=1)
        return boxes[keep]

    def _ao(self, f: Face) -> np.ndarray:
        boxes = self._near(f, self.ao_reach + 1)
        if boxes.shape[0] == 0:
            return np.zeros(f.shape, dtype=np.float32)
        n = np.array(f.normal, dtype=np.float32)
        u = np.array(f.du, dtype=np.float32)
        v = np.array(f.dv, dtype=np.float32)
        u /= max(1e-6, float(np.linalg.norm(u)))
        v /= max(1e-6, float(np.linalg.norm(v)))
        P = np.stack([f.X, f.Y, f.Z], axis=-1)
        r = self.ao_reach
        samples = []
        for hgt, wgt in ((0.45, 1.0), (1.2, 0.8), (2.4, 0.55), (r, 0.3)):
            samples.append((n * hgt, wgt))
            for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1), (0.7, 0.7), (-0.7, 0.7), (0.7, -0.7), (-0.7, -0.7)):
                samples.append((n * hgt + (u * a + v * b) * hgt * 1.1, wgt * 0.8))
        occ = np.zeros(f.shape, dtype=np.float32)
        total = 0.0
        for off, wgt in samples:
            occ += wgt * self._inside(P + off.astype(np.float32), boxes)
            total += wgt
        return np.clip(occ / total * 2.8, 0, 1)

    def _edges(self, f: Face) -> dict:
        """Per border: the texels' distance to it where the solid really ends there (inf where it carries on)."""
        boxes = self._near(f, 2)
        n = np.array(f.normal, dtype=np.float32)
        u = np.array(f.du, dtype=np.float32)
        v = np.array(f.dv, dtype=np.float32)
        ul, vl = float(np.linalg.norm(u)), float(np.linalg.norm(v))
        u /= max(1e-6, ul)
        v /= max(1e-6, vl)
        P = np.stack([f.X, f.Y, f.Z], axis=-1)
        out, stop = {}, {}
        for k, t, steps in (("top", -v, f.border["top"] * vl), ("bottom", v, f.border["bottom"] * vl),
                            ("left", -u, f.border["left"] * ul), ("right", u, f.border["right"] * ul)):
            # The texel's point carried to just past this border, just under the surface: inside another box, the
            # solid carries on there (a coplanar continuation, or the foot of a wall); otherwise this border is an edge.
            past = P + t * (steps[..., None] + 0.5 + 0.25)
            carries_on = self._inside(past - n * 0.2, boxes)
            # Just over the surface there too: a corner where it meets another surface; open air: it runs on flat.
            meets = self._inside(past + n * 0.2, boxes)
            out[k] = np.where(carries_on, np.inf, f.border[k]).astype(np.float32)
            stop[k] = np.where(carries_on & ~meets, np.inf, f.border[k]).astype(np.float32)
        f.stop = stop
        return out

    # -- painting -----------------------------------------------------------------------------------------------------------
    def faces(self, names: str | Box | Iterable, only: Iterable[str] | None = None) -> list[Face]:
        """The named boxes' faces (those with their own texture), or only those facing the given ways."""
        if isinstance(names, (str, Box)):
            names = [names]
        out = []
        for name in names:
            b = self.m.get(name) if isinstance(name, str) else name
            for d, fc in b.faces.items():
                if fc.shared_with is None and fc.rect is not None and (only is None or d in only):
                    out.append(self.face(b, d))
        return out

    def paint(self, tex: Tex, names, material: "Material", only: Iterable[str] | None = None) -> None:
        for f in self.faces(names, only):
            rgba = material.render(f, self._seed(f))
            _blit(tex, f.rect, rgba)

    def _seed(self, f: Face) -> int:
        # (A stable per-box seed: Python's own str hash changes from run to run.)
        return self.seed + sum(ord(c) * 31 ** i for i, c in enumerate(f.box.name)) % 997

    def overlay(self, tex: Tex, names, fn: Callable[[Face, np.ndarray], None], only: Iterable[str] | None = None) -> None:
        """Lets fn change a face's pixels in place (an (h, w, 4) uint8 array), in model space: details on a material."""
        for f in self.faces(names, only):
            x, y, w, h = f.rect
            img = tex.a[y:y + h, x:x + w].copy()
            fn(f, img)
            _blit(tex, f.rect, img)

    def material_on(self, f: Face, material: "Material") -> np.ndarray:
        """A material as it would paint this face (to copy part of it in an overlay)."""
        return material.render(f, self._seed(f))


def _blit(tex: Tex, rect, rgba: np.ndarray) -> None:
    x, y, w, h = rect
    tex.a[y:y + h, x:x + w] = np.clip(rgba, 0, 255).astype(np.uint8)


# --- tone to palette --------------------------------------------------------------------------------------------------------

def quantize(value: np.ndarray, ramp: np.ndarray, f: Face | None, dither: float, grid: tuple[np.ndarray, np.ndarray] | None = None,
             despeckle: bool = True, scatter: bool = False) -> np.ndarray:
    """Values (0-1) onto a ramp's shades, with ordered dithering (aligned to the model's own grid) of the given
    strength, or scattered (noise) dithering for rough stuff on a block texture, where an ordered pattern would repeat
    block after block; lone pixels then take their neighbours' shade. Returns the shade indices."""
    n = ramp.shape[0]
    if scatter and f is not None:
        threshold = (white(f.X, f.Y, f.Z, 389) - 0.5) * dither
        idx = np.clip(np.floor(np.clip(value, 0, 1) * (n - 1) + 0.5 + threshold), 0, n - 1).astype(np.int64)
        return _despeckle(idx) if despeckle and idx.shape[0] > 2 and idx.shape[1] > 2 else idx
    if grid is None and f is not None:
        a, b = f.plane_uv()
        grid = (np.floor(a + 1e-3).astype(np.int64) % 4, np.floor(b + 1e-3).astype(np.int64) % 4)
    if grid is None:
        h, w = value.shape
        gy, gx = np.mgrid[0:h, 0:w]
        grid = (gx % 4, gy % 4)
    threshold = (BAYER4[grid[1], grid[0]] - 0.5) * dither
    idx = np.clip(np.floor(np.clip(value, 0, 1) * (n - 1) + 0.5 + threshold), 0, n - 1).astype(np.int64)
    if despeckle and idx.shape[0] > 2 and idx.shape[1] > 2:
        idx = _despeckle(idx)
    return idx


def _despeckle(idx: np.ndarray) -> np.ndarray:
    """A pixel unlike all four of its neighbours, three or four of which agree, takes theirs (no lone pixels)."""
    out = idx.copy()
    p = np.pad(idx, 1, mode="edge")
    c = p[1:-1, 1:-1]
    nb = np.stack([p[:-2, 1:-1], p[2:, 1:-1], p[1:-1, :-2], p[1:-1, 2:]])
    differs = np.all(nb != c, axis=0)
    for k in range(4):
        agree = (nb == nb[k]).sum(axis=0) >= 3
        sel = differs & agree
        out[sel] = nb[k][sel]
    return out


def colours(ramp: np.ndarray, idx: np.ndarray) -> np.ndarray:
    rgb = ramp[idx]
    return np.concatenate([rgb, np.full(idx.shape + (1,), 255, dtype=np.float32)], axis=-1)


# --- materials ----------------------------------------------------------------------------------------------------------------
#: How a face's direction lights it (before the game's own shading): tops catch the sky, undersides are in shadow.
FACING = {"up": 0.10, "north": 0.0, "west": 0.0, "south": -0.04, "east": -0.04, "down": -0.18, "tile": 0.0}


@dataclass
class Material:
    """A surface: its ramp, base tone, roughness and weathering."""
    ramp: np.ndarray
    tone: float = 0.55               # base value on the ramp (0 darkest .. 1 lightest)
    mottle: float = 0.06             # broad unevenness
    grain: float = 0.04              # per-texel grain
    dither: float = 0.8              # ordered dithering strength (rough materials more)
    edge_light: float = 0.16         # the lip of an exposed top/left edge catching the light
    edge_dark: float = 0.10          # an exposed bottom/right edge in its own shadow
    ao: float = 0.55                 # how dark the occluded corners go
    grime: float = 0.10              # darker low on upright faces
    dust: float = 0.04               # lighter, flatter tops
    streaks: float = 0.0             # rain streaks hanging from top edges on upright faces
    wear: float = 0.0                # paint chipped off exposed edges (0 none .. 1 badly)
    under: np.ndarray | None = None  # what shows through the chips (bare steel, concrete)
    rust: float = 0.0                # rust spots by edges and seams, with runs
    brushed: float = 0.0             # brushed/rolled metal: streaks along the face's long axis
    pits: float = 0.0                # concrete bug holes on upright faces (fraction of texels)
    falloff: float = 0.05            # upright faces lighter at the top, darker at the bottom (skylight)
    scale: float = 1.0               # stretch of the material's features
    #: Anything more to add to the value before it goes onto the ramp, from the face (scorching round a trench, say).
    extra: Callable[[Face], np.ndarray] | None = None
    #: Clean lone pixels away (smooth paint and metal); rough stuff (concrete, soot) keeps its grain.
    despeckle: bool = True

    def value(self, f: Face, seed: int) -> np.ndarray:
        X, Y, Z = f.X, f.Y, f.Z
        s = self.scale
        v = np.full(f.shape, self.tone + FACING[f.d], dtype=np.float32)
        v += self.mottle * (noise(f, 18 * s, seed, 3) - 0.5) * 2
        v += self.mottle * 0.6 * (noise(f, 3 * s, seed + 7, 2) - 0.5) * 2
        if self.grain:
            v += self.grain * (white(X, Y, Z, seed + 11) - 0.5) * 2
        if self.brushed:
            # Rolled/brushed metal: fine streaks along the face's long side.
            v += self.brushed * (noise(f, _streak_scale(f), seed + 23) - 0.5) * 2
        # (The lit and shaded lips of exposed edges go on after the ramp: see render.)
        # Skylight down upright faces, occlusion, grime low on them, dust on tops.
        if f.vertical and self.falloff:
            half = max(2.0, f.shape[0] / 2)
            v += self.falloff * (np.clip(1 - f.stop["top"] / half, 0, 1) - np.clip(1 - f.stop["bottom"] / half, 0, 1))
        v -= self.ao * f.ao
        if f.vertical and self.grime:
            low = np.clip(1 - f.stop["bottom"] / 5.0, 0, 1)
            v -= self.grime * low * (0.6 + 0.4 * value_noise(X, Y, Z, (3, 9, 3), seed + 31))
        if f.d == "up" and self.dust:
            v += self.dust * (noise(f, 6 * s, seed + 41, 2) - 0.3)
        if f.vertical and self.streaks:
            # Rain streaks: thin vertical runs from the top edge, fading down the face.
            a, _ = f.plane_uv()
            col = value_noise(a, np.zeros_like(a), np.zeros_like(a), (1.2, 1, 1), seed + 53)
            run = np.clip(1 - f.stop["top"] / (6 + 10 * value_noise(a, a * 0, a * 0, (3, 1, 1), seed + 59)), 0, 1)
            v -= self.streaks * np.clip(col - 0.55, 0, 1) * 2.2 * run
        if self.extra is not None:
            v += self.extra(f)
        return v

    def render(self, f: Face, seed: int, **kw) -> np.ndarray:
        v = self.value(f, seed)
        idx = quantize(v, self.ramp, f, self.dither, despeckle=self.despeckle, scatter=f.d == "tile" and not self.despeckle)
        idx = self._lips(f, idx)
        rgba = colours(self.ramp, idx)
        if self.pits:
            self._pits(f, seed, rgba)
        if self.wear and self.under is not None:
            self._chips(f, seed, rgba)
        if self.rust:
            self._rust(f, seed, rgba)
        return rgba

    def _lips(self, f: Face, idx: np.ndarray) -> np.ndarray:
        """The lit and shaded lips of exposed edges (light from the top left of each face, as vanilla draws it): a
        whole shade up or down the ramp, solid along the edge. (They had been added to the value before dithering,
        which broke a one-texel lip into dashes: after the missiles' first look.)"""
        e = f.edge
        n = len(self.ramp)
        steps = np.zeros(idx.shape, dtype=np.int64)
        one = 1.0 / (n - 1)                                   # one shade, as a value
        if self.edge_light >= one * 0.5:
            steps += (e["top"] < 1).astype(np.int64)
            steps += ((e["left"] < 1) & (e["top"] >= 1) & (self.edge_light * 0.6 >= one * 0.5)).astype(np.int64)
        if self.edge_dark >= one * 0.5:
            dark = (e["bottom"] < 1) | ((e["right"] < 1) & (self.edge_dark * 0.6 >= one * 0.5))
            steps -= (dark & (steps == 0)).astype(np.int64)
        return np.clip(idx + steps, 0, n - 1)

    def _pits(self, f: Face, seed: int, rgba: np.ndarray) -> None:
        """Concrete bug holes: a dark pit with a lit lower lip, here and there."""
        if not (f.vertical or f.d == "tile"):
            return
        r = white(f.X, f.Y, f.Z, seed + 71)
        pit = (r < self.pits) & (f.edge_near() > 1.5)
        dark = self.ramp[max(0, int(self.tone * (len(self.ramp) - 1)) - 3)]
        rgba[pit, :3] = dark
        if f.vertical or f.d == "tile":
            below = np.roll(pit, 1, axis=0)
            below[0, :] = False
            light = self.ramp[min(len(self.ramp) - 1, int(self.tone * (len(self.ramp) - 1)) + 2)]
            rgba[below & ~pit, :3] = light

    def _chips(self, f: Face, seed: int, rgba: np.ndarray) -> None:
        """Paint gone from exposed edges and corners, in little clusters, showing what is under it."""
        near = f.edge_near()
        corner = np.minimum(f.edge_near(("top", "bottom")), 1e9) + np.minimum(f.edge_near(("left", "right")), 1e9)
        reach = 1.0 + 2.5 * self.wear
        chance = np.clip(1 - near / reach, 0, 1) * (0.55 + 0.45 * np.clip(1 - corner / 6, 0, 1))
        # A thin member is all edge: chip it as little as its size (a 2-pixel post keeps most of its paint).
        chance = chance * float(np.clip((min(f.shape) - 1) / 6, 0.15, 1))
        n = noise(f, 1.6, seed + 83)
        # (The threshold rises steeply as the wear falls: at first it barely moved, and "hardly worn" paint lost half
        # of every edge.)
        chip = (n * chance) > (1 - 0.85 * self.wear)
        if not chip.any():
            return
        u = self.under
        tone = np.clip(0.55 + 0.25 * (white(f.X, f.Y, f.Z, seed + 89) - 0.5) - 0.3 * f.ao, 0, 1)
        idx = np.clip(np.round(tone * (len(u) - 1)), 0, len(u) - 1).astype(np.int64)
        rgba[chip, :3] = u[idx[chip]]

    def _rust(self, f: Face, seed: int, rgba: np.ndarray) -> None:
        """Rust spots by exposed edges and in corners (dark core, orange halo), and their runs down upright faces."""
        near = np.minimum(f.edge_near(), 6)
        where = np.clip(1 - near / 4, 0, 1) * 0.6 + np.clip(f.ao * 1.5, 0, 1) * 0.6
        n = noise(f, 2.2, seed + 97, 2)
        spot = n * where > (0.62 - 0.25 * self.rust)
        core = n * where > (0.70 - 0.25 * self.rust)
        out = rgba[..., :3]
        out[spot] = out[spot] * 0.35 + RUST_HALO * 0.65
        out[core] = RUST_CORE
        if f.vertical:
            # Runs: below each spot, a fading orange-brown streak a few texels long.
            run = np.zeros(f.shape, dtype=np.float32)
            src = spot.astype(np.float32)
            for k in range(1, 7):
                shifted = np.zeros_like(src)
                shifted[k:, :] = src[:-k, :]
                run = np.maximum(run, shifted * (1 - k / 7.5))
            mask = (run > 0.15) & ~spot
            a = (run[mask] * 0.55 * self.rust * 1.6).clip(0, 0.6)[..., None]
            out[mask] = out[mask] * (1 - a) + RUST_MID * a


def _streak_scale(f: Face) -> tuple[float, float, float]:
    """A noise stretch running along the face's longer side: long that way, fine across it."""
    long_u = f.shape[1] >= f.shape[0]
    u_axis, v_axis = {"up": (0, 2), "down": (0, 2), "north": (0, 1), "south": (0, 1), "east": (2, 1), "west": (2, 1),
                      "tile": (0, 1)}[f.d]
    s = [1.0, 1.0, 1.0]
    s[u_axis], s[v_axis] = (14.0, 0.7) if long_u else (0.7, 14.0)
    return tuple(s)


@dataclass
class Striped(Material):
    """Two paints in 45-degree stripes laid in model space (so they run on round corners and over seams), each shaded
    and weathered as its own material; where worn through, what is under them shows."""
    other: Material | None = None
    width: float = 3.0

    def render(self, f: Face, seed: int, **kw) -> np.ndarray:
        a, b = f.plane_uv()
        band = (np.floor((a + b) / self.width + 1e-3).astype(np.int64) % 2) == 0
        first = Material.render(self, f, seed)
        second = self.other.render(f, seed + 1) if self.other is not None else first
        return np.where(band[..., None], first, second)


def stripes(width: float = 3.0, wear: float = 0.3, under: np.ndarray | None = None) -> Striped:
    """Hazard paint: yellow and black."""
    under = CONCRETE_RAMP[2:7] if under is None else under
    black = painted(BLACK_RAMP, tone=0.5, wear=wear, under=under, edge_light=0.25, ao=0.35)
    return Striped(ramp=YELLOW_PAINT_RAMP, tone=0.62, mottle=0.04, grain=0.02, dither=0.45, edge_light=0.14, edge_dark=0.12,
                   ao=0.5, grime=0.1, dust=0.04, wear=wear, under=under, other=black, width=width)


def concrete(tone: float = 0.6, **kw) -> Material:
    """Cast concrete: dithered, low contrast, mottled, pitted here and there, streaked below its top edges."""
    d = dict(ramp=CONCRETE_RAMP, tone=tone, mottle=0.028, grain=0.045, dither=0.85, edge_light=0.12, edge_dark=0.08, ao=0.6,
             grime=0.12, dust=0.03, streaks=0.10, pits=0.006, falloff=0.03, despeckle=False)
    d.update(kw)
    return Material(**d)


def painted(ramp: np.ndarray, tone: float = 0.55, wear: float = 0.3, rust: float = 0.0, under: np.ndarray | None = None, **kw) -> Material:
    """Painted steel: smooth (little dithering), lit lips on its edges, chipped where it is knocked, rust if neglected."""
    d = dict(ramp=ramp, tone=tone, mottle=0.03, grain=0.03, dither=0.55, edge_light=0.18, edge_dark=0.12, ao=0.6,
             grime=0.12, dust=0.04, wear=wear, under=STEEL_RAMP[2:7] if under is None else under, rust=rust, falloff=0.06)
    d.update(kw)
    return Material(**d)


def steel(tone: float = 0.5, brushed: float = 0.05, **kw) -> Material:
    """Bare or dark steel: cool, contrasty lips, brushed along its length."""
    d = dict(ramp=STEEL_RAMP, tone=tone, mottle=0.04, grain=0.02, dither=0.4, edge_light=0.22, edge_dark=0.14, ao=0.55,
             grime=0.08, dust=0.03, brushed=brushed)
    d.update(kw)
    return Material(**d)


def wood(tone: float = 0.5, **kw) -> Material:
    """Rough sawn wood (pallets, crates): grain along its length."""
    d = dict(ramp=WOOD_RAMP, tone=tone, mottle=0.04, grain=0.04, dither=0.7, edge_light=0.14, edge_dark=0.12, ao=0.55,
             grime=0.1, dust=0.04, brushed=0.12, falloff=0.03)
    d.update(kw)
    return Material(**d)


def soot(tone: float = 0.3, **kw) -> Material:
    """Blackened steel and concrete where exhaust has scorched it."""
    d = dict(ramp=SOOT_RAMP, tone=tone, mottle=0.06, grain=0.06, dither=1.0, edge_light=0.18, edge_dark=0.08, ao=0.4,
             grime=0.0, dust=0.0, despeckle=False)
    d.update(kw)
    return Material(**d)


def white_paint(tone: float = 0.76, **kw) -> Material:
    """Gloss white paint (dishes, radomes): even, clean, little dithering; only its edges and corners darken."""
    d = dict(ramp=WHITE_PAINT_RAMP, tone=tone, mottle=0.015, grain=0.018, dither=0.35, edge_light=0.1, edge_dark=0.1, ao=0.5,
             grime=0.06, dust=0.02, wear=0.06, under=STEEL_RAMP[3:7], falloff=0.03)
    d.update(kw)
    return Material(**d)


def galvanized(tone: float = 0.55, **kw) -> Material:
    """Hot-dip galvanised steel (gratings, pipes, masts): pale, cool, mottled with spangle, dull lips."""
    d = dict(ramp=GALVANIZED_RAMP, tone=tone, mottle=0.05, grain=0.035, dither=0.6, edge_light=0.18, edge_dark=0.12, ao=0.55,
             grime=0.1, dust=0.03, falloff=0.03)
    d.update(kw)
    return Material(**d)


def warning_stripes(width: float = 1.5, wear: float = 0.3) -> "Striped":
    """Red and black warning paint (power: sockets, ports)."""
    return Striped(ramp=RED_PAINT_RAMP, tone=0.55, mottle=0.03, grain=0.03, dither=0.4, edge_light=0.18, edge_dark=0.12, ao=0.5,
                   grime=0.1, wear=wear, under=STEEL_RAMP[2:7], other=painted(BLACK_RAMP, tone=0.5, wear=wear), width=width)


def rubber(tone: float = 0.45, **kw) -> Material:
    d = dict(ramp=RUBBER_RAMP, tone=tone, mottle=0.03, grain=0.02, dither=0.3, edge_light=0.18, edge_dark=0.1, ao=0.4,
             grime=0.0, dust=0.06)
    d.update(kw)
    return Material(**d)


# --- block textures: tiles -----------------------------------------------------------------------------------------------------

def tile_face(size: int = 16, edges: bool = False, period: int | None = None) -> Face:
    """A block texture's face (one texel a model pixel, x across, y up), its noise wrapping every {period} pixels (the
    face's size by default) so the block tiles seamlessly. With edges, its borders are exposed edges (a lone block's
    lit and shaded lips, its paint chipped there); without, it carries on into its neighbours."""
    jj, ii = np.mgrid[0:size, 0:size].astype(np.float32) + 0.5
    f = Face(None, "tile", (0, 0, size, size), ii, size - jj, np.zeros_like(ii), (0, 0, 1), (1, 0, 0), (0, -1, 0))
    f.border = {"top": jj - 0.5, "bottom": size - jj - 0.5, "left": ii - 0.5, "right": size - ii - 0.5}
    f.edge = {k: (v.copy() if edges else np.full_like(v, np.inf)) for k, v in f.border.items()}
    f.stop = {k: v.copy() for k, v in f.edge.items()}
    f.ao = np.zeros((size, size), dtype=np.float32)
    f.period = period or size
    return f


def tile(material: "Material", seed: int = 0, size: int = 16, edges: bool = False) -> np.ndarray:
    """A material over a whole block face: (size, size, 4) uint8."""
    return np.clip(material.render(tile_face(size, edges), seed), 0, 255).astype(np.uint8)


#: Concrete as a block texture: its tone right on a shade (so it is that shade, with specks and clusters of the next
#: ones, not a 50/50 flicker between two), its mottle small enough to show inside one block.
CONCRETE_TILE = dict(tone=0.625, pits=0.0, grain=0.06, mottle=0.08, scale=0.5, dither=0.6)


def grid_xy(size: int = 16) -> tuple[np.ndarray, np.ndarray]:
    """Column and row indices of a block texture's texels."""
    row, col = np.mgrid[0:size, 0:size]
    return col, row


def hazard_tile(width: int = 2, seed: int = 0, scuff: float = 0.0, size: int = 16) -> np.ndarray:
    """Yellow and black paint in diagonal stripes ("/", {width} texels each: they run on across neighbouring blocks),
    worn through to concrete here and there by {scuff}."""
    f = tile_face(size)
    yellow = painted(YELLOW_PAINT_RAMP, tone=0.625, wear=0.0, dither=0.4).render(f, seed)
    black = painted(BLACK_RAMP, tone=0.5, wear=0.0, dither=0.4).render(f, seed + 1)
    col, row = grid_xy(size)
    img = np.where(((((col + row) // width) % 2) == 0)[..., None], yellow, black)
    if scuff:
        worn = scuffed(f, scuff, seed + 2, scale=2.0)
        img[worn] = concrete(**CONCRETE_TILE).render(f, seed + 3)[worn]
    return np.clip(img, 0, 255).astype(np.uint8)


def to_tex(img: np.ndarray) -> Tex:
    t = Tex(img.shape[1], img.shape[0])
    t.a[:, :] = img
    return t


def bevel(img: np.ndarray, mask: np.ndarray, light: float = 1.22, dark: float = 0.68, add: float = 6.0) -> None:
    """Shades a drawn shape (a lattice's bars, say) as if raised: the texels whose upper or left neighbour is outside it
    catch the light, those whose lower or right neighbour is outside it fall into shadow."""
    p = np.pad(mask, 1, constant_values=False)
    up, left = ~p[:-2, 1:-1], ~p[1:-1, :-2]
    down, right = ~p[2:, 1:-1], ~p[1:-1, 2:]
    lit = mask & (up | left) & ~(down | right)
    shade = mask & (down | right) & ~(up | left)
    lighten(img, lit, light, add)
    darken(img, shade, dark)


# --- details in model space ----------------------------------------------------------------------------------------------------

def grid(f: Face, img: np.ndarray, every: float = 2.0, depth: float = 0.5) -> None:
    """Grating: open slots between bars on a world grid (darkened, not cut out)."""
    a, b = f.plane_uv()
    pa = (a / every) - np.floor(a / every)
    pb = (b / every) - np.floor(b / every)
    slot = (pa > 0.5) & (pb > 0.5)
    rgb = img[..., :3].astype(np.float32)
    rgb[slot] *= depth
    img[..., :3] = rgb.astype(np.uint8)


def scuffed(f: Face, amount: float = 0.25, seed: int = 0, scale: float = 2.5) -> np.ndarray:
    """Where floor paint is worn through by traffic (not by edges): patches, more of them the higher the amount."""
    n = noise(f, scale, seed + 131, 2)
    return n > 1 - amount * 0.55


def sooted(f: Face, img: np.ndarray, amount: np.ndarray, strength: float = 0.6) -> None:
    """Scorching laid over a face (0..1 per texel), in four steps scattered by per-texel noise (soot is gritty: an
    ordered pattern would read as a checkerboard), each step darker and a little warmer-black."""
    t = white(f.X, f.Y, f.Z, 977) * 0.8 + 0.1
    level = np.clip(np.floor(np.clip(amount, 0, 1) * 3 + t), 0, 3)
    rgb = img[..., :3].astype(np.float32)
    k = 1 - strength * level[..., None] / 3
    soot = np.array([14, 12, 11], dtype=np.float32)
    rgb = rgb * k + soot * (1 - k) * 0.6
    img[..., :3] = np.clip(rgb, 0, 255).astype(np.uint8)


def darken(img: np.ndarray, mask: np.ndarray, k: float) -> None:
    rgb = img[..., :3].astype(np.float32)
    rgb[mask] *= k
    img[..., :3] = np.clip(rgb, 0, 255).astype(np.uint8)


def lighten(img: np.ndarray, mask: np.ndarray, k: float, add: float = 0.0) -> None:
    rgb = img[..., :3].astype(np.float32)
    rgb[mask] = rgb[mask] * k + add
    img[..., :3] = np.clip(rgb, 0, 255).astype(np.uint8)


def paste(img: np.ndarray, layer: np.ndarray, mask: np.ndarray) -> None:
    """Copies a rendered layer onto a face where the mask says."""
    img[mask] = layer[mask].astype(np.uint8)


def seams(f: Face, img: np.ndarray, every: float, axis: str = "u", offset: float = 0.0, light: bool = True) -> None:
    """Panel seams every so many model pixels: a dark groove with a lit lip on its lower/right side."""
    a, b = f.plane_uv()
    coord = a if axis == "u" else b
    pos = (coord - offset) / every
    frac = pos - np.floor(pos)
    groove = frac < (1.0 / every) * 0.99
    rgb = img[..., :3].astype(np.float32)
    rgb[groove] *= 0.55
    if light:
        lip = (frac >= (1.0 / every)) & (frac < (2.0 / every) * 0.99)
        rgb[lip] = np.minimum(255, rgb[lip] * 1.18 + 6)
    img[..., :3] = rgb.astype(np.uint8)


def rivets(f: Face, img: np.ndarray, every: float, inset: int = 1, axis: str = "u", offset: float = 0.0) -> None:
    """Rivet rows a few texels in from two of a face's borders: along u, its top and bottom borders; along v, its left
    and right ones. A head every so many model pixels (on the model's grid), lit, with a shadow under it."""
    a, b = f.plane_uv()
    if axis == "u":
        coord = a
        on_row = np.isclose(f.border["top"], inset) | np.isclose(f.border["bottom"], inset)
    else:
        coord = b
        on_row = np.isclose(f.border["left"], inset) | np.isclose(f.border["right"], inset)
    rgb = img[..., :3].astype(np.float32)
    pos = ((coord - offset) / every) - np.floor((coord - offset) / every)
    head = on_row & (pos < (1.0 / every) * 0.99)
    rgb[head] = np.minimum(255, rgb[head] * 1.35 + 14)
    shadow = np.zeros_like(head)
    shadow[1:, :] = head[:-1, :]
    rgb[shadow & ~head] *= 0.7
    img[..., :3] = rgb.astype(np.uint8)
