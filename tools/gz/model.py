"""Block/item model DSL.

Workflow:
    m = BlockModel("fission_bomb", tex_size=(64, 64))
    body = m.box("body", (2, 0, 4), (14, 12, 12))          # model units: 16 = one block = 16 texture pixels
    m.layout()                                              # every face gets its own pixel rect in the texture
    m.paint(body, "north", lambda tex, r: ...)              # paint each face as seen from outside
    m.save(texture)                                         # writes models/block/<name>.json + textures/block/<name>.png

Face orientation when painting (what the top-left of a face rect shows):
    north/south/east/west: the face seen from outside, image top = up
    up:   seen from above, image top = north (-Z)
    down: seen from below, image top = south (+Z)
This matches vanilla's automatic UVs, so a face painted here appears exactly as drawn in game.
"""
from __future__ import annotations

import json
import math
from dataclasses import dataclass, field
from pathlib import Path
from typing import Callable

from . import paths
from .tex import Tex

DIRS = ("north", "south", "east", "west", "up", "down")
Vec3 = tuple[float, float, float]


@dataclass
class Face:
    rect: tuple[int, int, int, int] | None = None   # x, y, w, h in texture pixels (None until laid out)
    cullface: str | None = None
    tintindex: int | None = None
    rotation: int = 0
    shared_with: tuple[str, str] | None = None       # (box name, dir) whose rect this face reuses
    mirror_x: bool = False                            # sample the shared rect mirrored horizontally
    texture: str = "0"                                # texture key in the model's "textures" map
    fixed_uv: tuple[float, float, float, float] | None = None  # explicit UV (0..16 space); skips packing


@dataclass
class Box:
    name: str
    frm: Vec3
    to: Vec3
    faces: dict[str, Face] = field(default_factory=dict)
    rotation: dict | None = None                      # {"origin": (x,y,z), "x":.., "y":.., "z":..} or axis/angle
    shade: bool = True
    light_emission: int = 0

    def size(self) -> Vec3:
        return (self.to[0] - self.frm[0], self.to[1] - self.frm[1], self.to[2] - self.frm[2])

    def face_px(self, d: str) -> tuple[int, int]:
        """Pixel size of a face's texture rect (width, height) as painted."""
        sx, sy, sz = self.size()
        w, h = {
            "north": (sx, sy), "south": (sx, sy), "east": (sz, sy), "west": (sz, sy), "up": (sx, sz), "down": (sx, sz),
        }[d]
        return max(1, math.ceil(w - 1e-6)), max(1, math.ceil(h - 1e-6))


# Display transform presets (vanilla values; "block" mirrors minecraft:block/block).
DISPLAY_BLOCK = {
    "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
    "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
    "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
}


class BlockModel:
    def __init__(self, name: str, tex_size: tuple[int, int] = (64, 64), kind: str = "block",
                 render_type: str | None = None, ambient_occlusion: bool = True, padding: int = 0):
        self.name = name
        self.kind = kind                      # "block" or "item": decides model/texture folders
        self.tex_w, self.tex_h = tex_size
        self.boxes: list[Box] = []
        self.render_type = render_type        # e.g. "minecraft:cutout"
        self.ambient_occlusion = ambient_occlusion
        self.padding = padding
        self.auto_height = True               # shrink the texture height to the next power of two that fits
        self.display: dict = {k: dict(v) for k, v in DISPLAY_BLOCK.items()}
        self.extra_textures: dict[str, str] = {}   # additional texture keys, e.g. vanilla stone for ore bases
        self._laid_out = False
        self.particle: str | None = None

    # -- geometry ------------------------------------------------------------------------------------------------
    def box(self, name: str, frm: Vec3, to: Vec3, faces: str | tuple[str, ...] = DIRS, rotation: dict | None = None,
            shade: bool = True, light: int = 0, cull: dict[str, str] | None = None) -> Box:
        if any(b.name == name for b in self.boxes):
            raise ValueError(f"duplicate box {name}")
        b = Box(name, tuple(map(float, frm)), tuple(map(float, to)), rotation=rotation, shade=shade, light_emission=light)
        for d in (DIRS if faces == "all" else faces):
            b.faces[d] = Face(cullface=(cull or {}).get(d))
        self.boxes.append(b)
        return b

    def get(self, name: str) -> Box:
        for b in self.boxes:
            if b.name == name:
                return b
        raise KeyError(name)

    def share(self, box: Box, d: str, source: Box, source_dir: str, mirror_x: bool = False) -> None:
        """Make box.d reuse source.source_dir's texture rect (identical faces cost no texture space)."""
        box.faces[d].shared_with = (source.name, source_dir)
        box.faces[d].mirror_x = mirror_x

    # -- texture layout --------------------------------------------------------------------------------------------
    def layout(self) -> None:
        """Shelf-packs every non-shared face into the texture. Raises if it does not fit."""
        items = []
        for b in self.boxes:
            for d, f in b.faces.items():
                if f.shared_with is None and f.fixed_uv is None:
                    w, h = b.face_px(d)
                    items.append((h, w, b, d))
        items.sort(key=lambda t: (-t[0], -t[1]))
        x = y = shelf_h = 0
        pad = self.padding
        for h, w, b, d in items:
            if x + w > self.tex_w:
                x, y, shelf_h = 0, y + shelf_h + pad, 0
            if y + h > self.tex_h:
                raise ValueError(f"{self.name}: faces do not fit in {self.tex_w}x{self.tex_h}")
            b.faces[d].rect = (x, y, w, h)
            x += w + pad
            shelf_h = max(shelf_h, h)
        used_h = y + shelf_h
        if self.auto_height:
            size = 16
            while size < used_h:
                size *= 2
            self.tex_h = size
        for b in self.boxes:
            for d, f in b.faces.items():
                if f.shared_with is not None:
                    src = self.get(f.shared_with[0]).faces[f.shared_with[1]]
                    f.rect = src.rect
        self._laid_out = True

    def rect(self, box: Box | str, d: str) -> tuple[int, int, int, int]:
        b = self.get(box) if isinstance(box, str) else box
        r = b.faces[d].rect
        if r is None:
            raise RuntimeError("call layout() first")
        return r

    def paint(self, tex: Tex, box: Box | str, d: str, fn: Callable[[Tex, tuple[int, int, int, int]], None]) -> None:
        fn(tex, self.rect(box, d))

    def paint_all(self, tex: Tex, box: Box | str, fn: Callable[[Tex, tuple[int, int, int, int], str], None]) -> None:
        b = self.get(box) if isinstance(box, str) else box
        for d, f in b.faces.items():
            if f.shared_with is None:
                fn(tex, f.rect, d)

    # -- display ---------------------------------------------------------------------------------------------------------
    def bounds(self) -> tuple[Vec3, Vec3]:
        lo = [min(b.frm[i] for b in self.boxes) for i in range(3)]
        hi = [max(b.to[i] for b in self.boxes) for i in range(3)]
        return tuple(lo), tuple(hi)  # type: ignore[return-value]

    def fit_display(self, gui_rotation: tuple[float, float, float] | None = None, gui_margin: float = 1.0) -> None:
        """Fits an oversized model into every display context and centres it.

        The GUI transform is fitted using the model's projected silhouette under its GUI rotation, so a long
        object can lie diagonally across the slot instead of shrinking to a speck. Other contexts scale by the
        largest dimension.
        """
        lo, hi = self.bounds()
        extent = max(hi[i] - lo[i] for i in range(3))
        k = min(1.0, 16.0 / extent)
        centre = [(lo[i] + hi[i]) / 2 - 8 for i in range(3)]
        for key, v in self.display.items():
            if key == "gui":
                continue
            s = DISPLAY_BLOCK[key]["scale"][0] * k
            v["scale"] = [round(s, 4)] * 3
            base = DISPLAY_BLOCK[key]["translation"]
            v["translation"] = [round(base[i] - centre[i] * s, 3) for i in range(3)]

        rot = gui_rotation or tuple(DISPLAY_BLOCK["gui"]["rotation"])
        m = _rot_xyz(*rot)
        corners = [(x, y, z) for x in (lo[0], hi[0]) for y in (lo[1], hi[1]) for z in (lo[2], hi[2])]
        proj = [_mat_vec(m, (c[0] - 8 - centre[0], c[1] - 8 - centre[1], c[2] - 8 - centre[2])) for c in corners]
        half = max(max(abs(p[0]) for p in proj), max(abs(p[1]) for p in proj))
        s = (8.0 - gui_margin) / half
        # Translation is applied after rotation+scale, in GUI space: re-centre the rotated model.
        cpx = _mat_vec(m, tuple(centre))
        self.display["gui"] = {"rotation": list(rot), "translation": [round(-cpx[0] * s, 3), round(-cpx[1] * s, 3), 0],
                               "scale": [round(s, 4)] * 3}

    # -- export ------------------------------------------------------------------------------------------------------------
    def texture_ref(self) -> str:
        return f"{paths.MODID}:{self.kind}/{self.name}"

    def to_json(self) -> dict:
        if not self._laid_out:
            raise RuntimeError("call layout() first")
        su, sv = 16.0 / self.tex_w, 16.0 / self.tex_h
        elements = []
        for b in self.boxes:
            el: dict = {"name": b.name, "from": list(b.frm), "to": list(b.to)}
            if b.rotation:
                el["rotation"] = {k: (list(v) if isinstance(v, tuple) else v) for k, v in b.rotation.items()}
            if not b.shade:
                el["shade"] = False
            if b.light_emission:
                el["light_emission"] = b.light_emission
            faces = {}
            for d, f in b.faces.items():
                if f.fixed_uv is not None:
                    uv = [round(v, 4) for v in f.fixed_uv]
                else:
                    x, y, w, h = f.rect  # type: ignore[misc]
                    fw, fh = b.face_px(d)
                    u0, u1 = (x + fw, x) if f.mirror_x else (x, x + fw)
                    uv = [round(u0 * su, 4), round(y * sv, 4), round(u1 * su, 4), round((y + fh) * sv, 4)]
                face: dict = {"uv": uv, "texture": f"#{f.texture}"}
                if f.cullface:
                    face["cullface"] = f.cullface
                if f.tintindex is not None:
                    face["tintindex"] = f.tintindex
                if f.rotation:
                    face["rotation"] = f.rotation
                faces[d] = face
            el["faces"] = faces
            elements.append(el)
        out: dict = {}
        if self.render_type:
            out["render_type"] = self.render_type
        if not self.ambient_occlusion:
            out["ambientocclusion"] = False
        out["texture_size"] = [self.tex_w, self.tex_h]
        textures = {"0": self.texture_ref(), "particle": self.particle or self.texture_ref()}
        textures.update(self.extra_textures)
        out["textures"] = textures
        out["elements"] = elements
        out["display"] = self.display
        return out

    def model_path(self) -> Path:
        return paths.MODELS / self.kind / f"{self.name}.json"

    def texture_path(self) -> Path:
        return paths.TEXTURES / self.kind / f"{self.name}.png"

    def save(self, tex: Tex, emissive: Tex | None = None) -> tuple[Path, Path]:
        mp = self.model_path()
        mp.parent.mkdir(parents=True, exist_ok=True)
        mp.write_text(json.dumps(self.to_json(), indent=2) + "\n", encoding="utf-8")
        tp = tex.save(self.texture_path())
        if emissive is not None:
            emissive.save(tp.with_name(tp.stem + "_glow.png"))
        return mp, tp


def _rot_xyz(rx: float, ry: float, rz: float) -> list[list[float]]:
    """Rx * Ry * Rz (degrees), the order vanilla's ItemTransform uses."""
    def mul(a, b):
        return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]
    cx, sx = math.cos(math.radians(rx)), math.sin(math.radians(rx))
    cy, sy = math.cos(math.radians(ry)), math.sin(math.radians(ry))
    cz, sz = math.cos(math.radians(rz)), math.sin(math.radians(rz))
    mx = [[1, 0, 0], [0, cx, -sx], [0, sx, cx]]
    my = [[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]]
    mz = [[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]]
    return mul(mul(mx, my), mz)


def _mat_vec(m, v) -> tuple[float, float, float]:
    return tuple(sum(m[i][k] * v[k] for k in range(3)) for i in range(3))  # type: ignore[return-value]


def auto_uv(box: Box, d: str) -> tuple[float, float, float, float]:
    """Vanilla's automatic UV for a face (used for full-block style mappings such as ore layers)."""
    (x0, y0, z0), (x1, y1, z1) = box.frm, box.to
    return {
        "north": (16 - x1, 16 - y1, 16 - x0, 16 - y0), "south": (x0, 16 - y1, x1, 16 - y0),
        "west": (z0, 16 - y1, z1, 16 - y0), "east": (16 - z1, 16 - y1, 16 - z0, 16 - y0),
        "up": (x0, z0, x1, z1), "down": (x0, 16 - z1, x1, 16 - z0),
    }[d]


def write_json(path: Path, data: dict) -> Path:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
    return path
