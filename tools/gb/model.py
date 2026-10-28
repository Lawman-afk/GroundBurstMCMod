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
    #: Texels per model pixel (2: twice as fine). A missile in flight is its item model drawn four times over, so its
    #: own flight model is four texels a pixel, as fine as the missiles standing on the launchers (after slice 9b).
    density: int = 1

    def size(self) -> Vec3:
        return (self.to[0] - self.frm[0], self.to[1] - self.frm[1], self.to[2] - self.frm[2])

    def face_px(self, d: str) -> tuple[int, int]:
        """Pixel size of a face's texture rect (width, height) as painted."""
        sx, sy, sz = self.size()
        w, h = {
            "north": (sx, sy), "south": (sx, sy), "east": (sz, sy), "west": (sz, sy), "up": (sx, sz), "down": (sx, sz),
        }[d]
        k = self.density
        return max(1, math.ceil(w * k - 1e-6)), max(1, math.ceil(h * k - 1e-6))


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
                 render_type: str | None = None, ambient_occlusion: bool = True, padding: int = 1, density: int = 1):
        self.name = name
        self.density = density                # texels per model pixel for every box (see Box.density)
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
        #: Where the texture goes, under textures/ (by default the model's kind): an item model whose faces share an atlas
        #: with block textures keeps its own texture under block/ too (a missile, whose head is a warhead's block model).
        self.texture_folder: str = kind
        #: (scale, offset): the JSON's coordinates are the model's times scale plus offset. A missile is built in the
        #: world's pixels and written at a quarter of that, as an item model the game draws four times over.
        self.export: tuple[float, tuple[float, float, float]] | None = None

    def exported(self, p) -> tuple[float, float, float]:
        """A model point as the JSON has it."""
        if self.export is None:
            return tuple(float(v) for v in p)  # type: ignore[return-value]
        s, o = self.export
        return (p[0] * s + o[0], p[1] * s + o[1], p[2] * s + o[2])

    # -- geometry ------------------------------------------------------------------------------------------------
    def box(self, name: str, frm: Vec3, to: Vec3, faces: str | tuple[str, ...] = DIRS, rotation: dict | None = None,
            shade: bool = True, light: int = 0, cull: dict[str, str] | None = None) -> Box:
        if any(b.name == name for b in self.boxes):
            raise ValueError(f"duplicate box {name}")
        b = Box(name, tuple(map(float, frm)), tuple(map(float, to)), rotation=rotation, shade=shade, light_emission=light,
                density=self.density)
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

    # -- checks: holes and flicker ------------------------------------------------------------------------------------------
    #: (box name, face) openings that are meant to be seen into (a hopper mouth, a vent); (name, "*") for a whole box.
    open_ok: set[tuple[str, str]] = set()
    _AXIS = {"west": (0, 0), "east": (0, 1), "down": (1, 0), "up": (1, 1), "north": (2, 0), "south": (2, 1)}

    def _solid(self) -> list[Box]:
        return [b for b in self.boxes if not b.rotation]

    @staticmethod
    def _inside(p, boxes, skip, eps: float) -> bool:
        return any(c not in skip and all(c.frm[k] - 2 * eps <= p[k] <= c.to[k] + 2 * eps for k in range(3)) for c in boxes)

    def holes(self, plane: float = 0.3, eps: float = 0.02, step: float = 0.25) -> list[str]:
        """Missing faces you can see into: on a solid box (thicker than a plane), the part of a left-out face that no
        other box covers. A face may be left out only where another box hides it. Ignores rotated boxes."""
        solid = self._solid()
        issues = []
        for b in solid:
            lo, hi = b.frm, b.to
            if any(hi[k] - lo[k] < plane for k in range(3)) or (b.name, "*") in self.open_ok:
                continue
            for d, (ax, side) in self._AXIS.items():
                if d in b.faces or (b.name, d) in self.open_ok:
                    continue
                o = [k for k in range(3) if k != ax]
                open_n = 0
                u = lo[o[0]] + step / 2
                while u < hi[o[0]]:
                    v = lo[o[1]] + step / 2
                    while v < hi[o[1]]:
                        p = [0.0, 0.0, 0.0]
                        p[o[0]], p[o[1]] = u, v
                        p[ax] = hi[ax] + eps if side else lo[ax] - eps
                        if not self._inside(p, solid, (b,), eps):
                            open_n += 1
                        v += step
                    u += step
                if open_n * step * step > 0.2:
                    issues.append(f"{b.name} has no {d} face: {open_n * step * step:.2f} px2 open")
        return issues

    def close_holes(self) -> list[str]:
        """Adds every left-out face you could see into (see holes()), except openings listed in open_ok. Called by
        layout(), so the new faces get texture space and are painted like the box's other faces."""
        added = []
        for issue in self.holes():
            name, rest = issue.split(" has no ")
            d = rest.split(" face")[0]
            self.get(name).faces[d] = Face()
            added.append(f"{name}/{d}")
        return added

    def zfight(self, tol: float = 0.05, eps: float = 0.02, step: float = 0.25) -> list[str]:
        """Faces that flicker: two faces pointing the same way on (or within `tol` of) the same plane, overlapping
        where no third box covers them. Block and item models are back-face culled, so opposite faces never fight.
        Identical boxes (overlay layers) are skipped. Ignores rotated boxes."""
        solid = self._solid()
        faces = [(b, d) for b in solid for d in b.faces if d in self._AXIS]
        issues = []
        for i in range(len(faces)):
            b1, d1 = faces[i]
            ax, side = self._AXIS[d1]
            p1 = b1.to[ax] if side else b1.frm[ax]
            for j in range(i + 1, len(faces)):
                b2, d2 = faces[j]
                if d2 != d1 or b2 is b1 or (b1.frm == b2.frm and b1.to == b2.to):
                    continue
                p2 = b2.to[ax] if side else b2.frm[ax]
                if abs(p1 - p2) > tol:
                    continue
                o = [k for k in range(3) if k != ax]
                lo = [max(b1.frm[k], b2.frm[k]) for k in o]
                hi = [min(b1.to[k], b2.to[k]) for k in o]
                if hi[0] - lo[0] <= 1e-6 or hi[1] - lo[1] <= 1e-6:
                    continue
                seen = 0.0
                u = lo[0] + min(step, hi[0] - lo[0]) / 2
                while u < hi[0]:
                    v = lo[1] + min(step, hi[1] - lo[1]) / 2
                    while v < hi[1]:
                        p = [0.0, 0.0, 0.0]
                        p[o[0]], p[o[1]] = u, v
                        p[ax] = max(p1, p2) + eps if side else min(p1, p2) - eps
                        if not self._inside(p, solid, (b1, b2), eps):
                            seen += min(step, hi[0] - lo[0]) * min(step, hi[1] - lo[1])
                        v += step
                    u += step
                if seen > 0.05:
                    issues.append(f"{b1.name}/{d1} vs {b2.name}/{d2} at {p1:g}/{p2:g}: {seen:.2f} px2 visible")
        return issues

    # -- texture layout --------------------------------------------------------------------------------------------
    def layout(self) -> None:
        """Shelf-packs every non-shared face into the texture. Raises if it does not fit. First adds any face left out
        where you could see into the box (close_holes)."""
        if type(self).close_holes is BlockModel.close_holes:
            self.close_holes()
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
    def bounds(self, also: tuple[Vec3, Vec3] | None = None) -> tuple[Vec3, Vec3]:
        """The box round every element (and round {also} too, if given), as the JSON has it."""
        lo = [min(b.frm[i] for b in self.boxes) for i in range(3)]
        hi = [max(b.to[i] for b in self.boxes) for i in range(3)]
        lo, hi = list(self.exported(lo)), list(self.exported(hi))
        if also is not None:
            lo = [min(lo[i], also[0][i]) for i in range(3)]
            hi = [max(hi[i], also[1][i]) for i in range(3)]
        return tuple(lo), tuple(hi)  # type: ignore[return-value]

    def fit_display(self, gui_rotation: tuple[float, float, float] | None = None, gui_margin: float = 1.0,
                    also: tuple[Vec3, Vec3] | None = None) -> None:
        """Fits an oversized model into every display context and centres it.

        The GUI transform is fitted using the model's projected silhouette under its GUI rotation, so a long
        object can lie diagonally across the slot instead of shrinking to a speck. Other contexts scale by the
        largest dimension. {also}: a box drawn with the model that is not among its elements (a missile's warhead,
        drawn by the game from its own model), fitted in too.
        """
        lo, hi = self.bounds(also)
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
        return f"{paths.MODID}:{self.texture_folder}/{self.name}"

    def to_json(self) -> dict:
        if not self._laid_out:
            raise RuntimeError("call layout() first")
        su, sv = 16.0 / self.tex_w, 16.0 / self.tex_h
        elements = []
        for b in self.boxes:
            el: dict = {"name": b.name, "from": [round(v, 5) for v in self.exported(b.frm)],
                        "to": [round(v, 5) for v in self.exported(b.to)]}
            if b.rotation:
                el["rotation"] = {k: (list(v) if isinstance(v, tuple) else v) for k, v in b.rotation.items()}
                if "origin" in el["rotation"]:
                    el["rotation"]["origin"] = [round(v, 5) for v in self.exported(el["rotation"]["origin"])]
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
        return paths.TEXTURES / self.texture_folder / f"{self.name}.png"

    def bleed(self, tex) -> None:
        """Copies every face's edge pixels one texel outward into the layout gutter. Sampling a face right at its UV
        edge (rounding, mip-free nearest filtering at grazing angles) then still lands on the face's own colour
        instead of a neighbour's, which otherwise shows as shimmering seams."""
        rects = {f.rect for b in self.boxes for f in b.faces.values() if f.rect is not None}
        snapshot = tex.copy()
        for (x, y, w, h) in rects:
            for i in range(-1, w + 1):
                for j in range(-1, h + 1):
                    if 0 <= i < w and 0 <= j < h:
                        continue
                    tx, ty = x + i, y + j
                    if not tex.inside(tx, ty) or snapshot.get(tx, ty)[3] != 0:
                        continue
                    src = snapshot.get(x + min(max(i, 0), w - 1), y + min(max(j, 0), h - 1))
                    if src[3] != 0 and tex.get(tx, ty)[3] == 0:
                        tex.px(tx, ty, src)

    def save(self, tex: Tex, emissive: Tex | None = None) -> tuple[Path, Path]:
        for issue in self.zfight():
            print(f"  z-fight warning [{self.name}]: {issue}")
        for issue in self.holes():
            print(f"  open-face warning [{self.name}]: {issue}")
        self.bleed(tex)
        if emissive is not None:
            self.bleed(emissive)
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
