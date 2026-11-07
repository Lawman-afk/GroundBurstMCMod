"""GeckoLib (Bedrock geometry) model and animation DSL.

Geometry is authored in *world* orientation, exactly like block models (x east, y up, z south, front = north),
but centred on the block's bottom centre: x and z run -8..8, y 0..16 for one block. Faces are painted with the
same conventions as gb.model (sides as seen from outside with top up; up face top = north; down face top =
south). The exporter converts to Bedrock space the way GeckoLib reads it back (verified against GeckoLib 5.5.7):
    cube origin.x = -(x1)            pivot.x negated            rotation x/y negated, z kept
    up/down face UVs rotated 180 degrees (negative uv_size)
    animation rotation x/y negated, position x negated

    g = GeoModel("ore_crusher", tex_size=(128, 128))
    frame = g.bone("frame")
    roller = g.bone("roller_l", pivot=(-3, 13, 0), parent="frame")
    g.box("housing", (-7, 4, -7), (7, 12, 7), bone="frame")
    ...
    g.layout(); paint ...; g.save(tex)
    a = Animation("working", 1.0); a.rotate("roller_l", {0: (0, 0, 0), 1: (360, 0, 0)}); save_animations(...)
"""
from __future__ import annotations

import sys

import json
from dataclasses import dataclass, field
from pathlib import Path

from . import paths
from .model import DIRS, BlockModel, Box, Vec3, write_json


@dataclass
class Bone:
    name: str
    pivot: Vec3 = (0.0, 0.0, 0.0)
    parent: str | None = None
    rotation: Vec3 = (0.0, 0.0, 0.0)
    boxes: list[Box] = field(default_factory=list)


class GeoModel(BlockModel):
    """Reuses BlockModel's face layout and painting; exports Bedrock geometry instead of block JSON."""

    def __init__(self, name: str, tex_size: tuple[int, int] = (64, 64), kind: str = "block", padding: int = 1):
        super().__init__(name, tex_size=tex_size, kind=kind, padding=padding)
        self.bones: list[Bone] = []
        self._bone_of: dict[str, str] = {}

    def bone(self, name: str, pivot: Vec3 = (0, 0, 0), parent: str | None = None, rotation: Vec3 = (0, 0, 0)) -> Bone:
        if any(b.name == name for b in self.bones):
            raise ValueError(f"duplicate bone {name}")
        if parent is not None and not any(b.name == parent for b in self.bones):
            raise ValueError(f"parent bone {parent} must be declared before {name}")
        b = Bone(name, tuple(map(float, pivot)), parent, tuple(map(float, rotation)))
        self.bones.append(b)
        return b

    def get_bone(self, name: str) -> Bone:
        for b in self.bones:
            if b.name == name:
                return b
        raise KeyError(name)

    def box(self, name: str, frm: Vec3, to: Vec3, faces=DIRS, rotation: dict | None = None, shade: bool = True,
            light: int = 0, cull=None, bone: str | None = None, inflate: float = 0.0) -> Box:  # type: ignore[override]
        if bone is None:
            raise ValueError("geo boxes need a bone")
        b = super().box(name, frm, to, faces=faces, rotation=rotation, shade=shade, light=light)
        b.__dict__["inflate"] = inflate
        self.get_bone(bone).boxes.append(b)
        self._bone_of[name] = bone
        return b

    # -- export ------------------------------------------------------------------------------------------------------
    def _face_uv(self, box: Box, d: str) -> dict:
        x, y, w, h = box.faces[d].rect  # type: ignore[misc]
        fw, fh = box.face_px(d)
        if box.faces[d].mirror_x:
            x, fw = x + fw, -fw
        if d in ("up", "down"):
            return {"uv": [x + fw, y + fh], "uv_size": [-fw, -fh]}
        return {"uv": [x, y], "uv_size": [fw, fh]}

    def hidden_faces(self, tol: float = 0.05) -> set[tuple[str, str]]:
        """(box, face) pairs that are never visible: a face lying in the same plane as an opposite-facing face of
        another unrotated box in the same bone, and wholly covered by it. GeckoLib's cutout pipeline draws back faces
        (no culling in 26.x), so such back-to-back pairs flicker; only the covering face is kept. Of two identical
        faces the later box's is dropped."""
        axis = {"east": (0, 1), "west": (0, -1), "up": (1, 1), "down": (1, -1), "south": (2, 1), "north": (2, -1)}
        hidden: set[tuple[str, str]] = set()
        for bone in self.bones:
            boxes = [b for b in bone.boxes if not b.rotation and b.poly is None]
            for i, a in enumerate(boxes):
                for d in list(a.faces):
                    ax, sign = axis[d]
                    plane = (a.to if sign > 0 else a.frm)[ax]
                    others = [k for k in range(3) if k != ax]
                    for j, c in enumerate(boxes):
                        if j == i:
                            continue
                        od = next(n for n, (x, s_) in axis.items() if x == ax and s_ == -sign)
                        if od not in c.faces or (c.name, od) in hidden:
                            continue
                        cplane = (c.to if -sign > 0 else c.frm)[ax]
                        if abs(cplane - plane) > tol:
                            continue
                        if all(c.frm[k] - tol <= a.frm[k] and a.to[k] <= c.to[k] + tol for k in others):
                            identical = all(abs(c.frm[k] - a.frm[k]) <= tol and abs(c.to[k] - a.to[k]) <= tol for k in others)
                            if identical and j > i:
                                continue          # keep the earlier box's face; the later one is dropped
                            hidden.add((a.name, d))
                            break
        return hidden

    def enclosed_faces(self, plane: float = 0.3, eps: float = 0.02, step: float = 0.25) -> set[tuple[str, str]]:
        """(box, face) pairs buried in solid geometry: every point just outside the face lies inside another solid
        box of the same bone (e.g. a dome's underside resting on its tank). Never visible, and drawing them only risks
        fighting the faces they rest on."""
        rotated = {b.name for b in self.bones if any(b.rotation)}
        axis = {"west": (0, 0), "east": (0, 1), "down": (1, 0), "up": (1, 1), "north": (2, 0), "south": (2, 1)}
        out: set[tuple[str, str]] = set()
        for bone in self.bones:
            if bone.name in rotated:
                continue
            solid = [b for b in bone.boxes if not b.rotation and all(b.to[k] - b.frm[k] >= plane for k in range(3))]
            for b in solid:
                for d in b.faces:
                    ax, side = axis[d]
                    o = [k for k in range(3) if k != ax]
                    buried = True
                    u = b.frm[o[0]] + step / 2
                    while u < b.to[o[0]] and buried:
                        v = b.frm[o[1]] + step / 2
                        while v < b.to[o[1]]:
                            p = [0.0, 0.0, 0.0]
                            p[o[0]], p[o[1]] = u, v
                            p[ax] = b.to[ax] + eps if side else b.frm[ax] - eps
                            if not any(c is not b and all(c.frm[k] - 1e-6 <= p[k] <= c.to[k] + 1e-6 for k in range(3)) for c in solid):
                                buried = False
                                break
                            v += step
                        u += step
                    if buried:
                        out.add((b.name, d))
        return out

    def to_geo_json(self) -> dict:
        if not self._laid_out:
            raise RuntimeError("call layout() first")
        hidden = self.hidden_faces() | self.enclosed_faces()
        bones_json = []
        for bone in self.bones:
            bj: dict = {"name": bone.name, "pivot": [-bone.pivot[0], bone.pivot[1], bone.pivot[2]]}
            if bone.parent:
                bj["parent"] = bone.parent
            if any(bone.rotation):
                bj["rotation"] = [-bone.rotation[0], -bone.rotation[1], bone.rotation[2]]
            cubes = []
            mesh = {"positions": [], "normals": [], "uvs": [], "polys": []}
            for b in bone.boxes:
                if b.poly is not None:
                    self._poly_mesh(b, mesh)
                    continue
                (x0, y0, z0), (x1, y1, z1) = b.frm, b.to
                cj: dict = {"origin": [-x1, y0, z0], "size": [x1 - x0, y1 - y0, z1 - z0]}
                if b.rotation:
                    o = b.rotation.get("origin", (0, 0, 0))
                    if "axis" in b.rotation:
                        r = {"x": (b.rotation["angle"], 0, 0), "y": (0, b.rotation["angle"], 0), "z": (0, 0, b.rotation["angle"])}[b.rotation["axis"]]
                    else:
                        r = (b.rotation.get("x", 0), b.rotation.get("y", 0), b.rotation.get("z", 0))
                    cj["pivot"] = [-o[0], o[1], o[2]]
                    cj["rotation"] = [-r[0], -r[1], r[2]]
                if b.__dict__.get("inflate"):
                    cj["inflate"] = b.__dict__["inflate"]
                cj["uv"] = {d: self._face_uv(b, d) for d in b.faces if (b.name, d) not in hidden}
                if cj["uv"]:
                    cubes.append(cj)
            if cubes:
                bj["cubes"] = cubes
            if mesh["polys"]:
                # The bone's polygons, laid out as Bedrock's poly mesh but under a key of the mod's own: GeckoLib reads a
                # "poly_mesh" as flat lists of numbers (and fails on lists of points, losing the whole model), and draws
                # none; the mod's PolyGeoModel draws this one, in the bone.
                bj["groundburst:poly_mesh"] = {"normalized_uvs": True, **mesh}
            bones_json.append(bj)
        lo, hi = self.bounds()
        width = max(abs(lo[0]), abs(hi[0]), abs(lo[2]), abs(hi[2])) * 2 / 16 + 1
        height = (hi[1] - min(0, lo[1])) / 16 + 1
        return {
            "format_version": "1.12.0",
            "minecraft:geometry": [{
                "description": {
                    "identifier": f"geometry.{paths.MODID}.{self.name}",
                    "texture_width": self.tex_w,
                    "texture_height": self.tex_h,
                    "visible_bounds_width": round(width, 2),
                    "visible_bounds_height": round(height, 2),
                    "visible_bounds_offset": [0, round(height / 2, 2), 0],
                },
                "bones": bones_json,
            }],
        }

    def _poly_mesh(self, b: Box, mesh: dict) -> None:
        """A carrier's polygons into its bone's poly mesh, in Bedrock's space as the cubes are (x negated): each split
        into fours (and a three left over) from its first point, counter-clockwise seen from outside; UVs 0..1 from the
        texture's top left, where its face's paint has each point (model.poly_uv)."""
        from .model import NORMALS, _element_matrix, _mat_vec, poly_uv  # noqa: PLC0415
        (d, _f), = b.faces.items()
        turn = _element_matrix(b.rotation) if b.rotation else None
        o = (b.rotation or {}).get("origin", (0.0, 0.0, 0.0))
        nrm = _mat_vec(turn, NORMALS[d]) if turn else NORMALS[d]
        ni = len(mesh["normals"])
        mesh["normals"].append([round(-nrm[0], 5), round(nrm[1], 5), round(nrm[2], 5)])
        for poly in b.poly or []:
            idx = []
            for p in poly:
                u, v = poly_uv(b, d, p, self)
                if turn is not None:
                    q = _mat_vec(turn, (p[0] - o[0], p[1] - o[1], p[2] - o[2]))
                    p = (q[0] + o[0], q[1] + o[1], q[2] + o[2])
                idx.append(len(mesh["positions"]))
                mesh["positions"].append([round(-p[0], 5), round(p[1], 5), round(p[2], 5)])
                mesh["uvs"].append([round(u / self.tex_w, 6), round(v / self.tex_h, 6)])
            k = 1
            while k + 1 < len(idx):
                piece = [idx[0], idx[k], idx[k + 1]] + ([idx[k + 2]] if k + 2 < len(idx) else [])
                mesh["polys"].append([[i, ni, i] for i in piece])
                k += 2

    def geo_path(self) -> Path:
        return paths.GEO / self.kind / f"{self.name}.geo.json"

    #: Groups of bones that are never drawn at the same time (the renderer shows one of them).
    exclusive: list[set[str]] = []

    def zfight(self, tol: float = 0.1) -> list[str]:
        """Coplanar overlapping faces (see tools/preview/zfight.py), by box name. Ignores rotated boxes/bones."""
        sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "preview"))
        import zfight  # noqa: PLC0415
        rotated = {b.name for b in self.bones if any(b.rotation)}
        bone_of = {bx.name: bone.name for bone in self.bones for bx in bone.boxes}
        boxes = [(b.name, tuple(b.frm), tuple(b.to), list(b.faces)) for b in self.boxes
                 if not b.rotation and b.poly is None and bone_of.get(b.name) not in rotated]
        parent = {bone.name: bone.parent for bone in self.bones}

        def chain(name: str | None) -> list[str]:
            """The bone and its ancestors: a bone inside a hidden one is hidden too."""
            out = []
            while name:
                out.append(name)
                name = parent.get(name)
            return out
        flat = self.flat_group_of()
        named = {"+x": "east", "-x": "west", "+y": "up", "-y": "down", "+z": "south", "-z": "north"}
        issues = []
        for issue in zfight.check(boxes, tol):
            a, b = issue.split(" vs ")[0].split("/")[0], issue.split(" vs ")[1].split("/")[0]
            da = named.get(issue.split(" vs ")[0].split("/")[1].split(" ")[0])
            db = named.get(issue.split(" vs ")[1].split("/")[1].split(" ")[0])
            if flat.get((a, da)) is not None and flat.get((a, da)) == flat.get((b, db)):
                continue                                   # (one flat group: they draw alike)
            ca, cb = chain(bone_of.get(a)), chain(bone_of.get(b))
            if any(x != y and x in grp and y in grp for grp in self.exclusive for x in ca for y in cb):
                continue   # never drawn together (two of a group, or inside two of a group)
            issues.append(issue)
        # A polygon's face against the faces in its plane (BlockModel's check, which knows polygons), in unturned bones.
        carriers = {b.name for b in self.boxes if b.poly is not None and bone_of.get(b.name) not in rotated}
        if carriers:
            for issue in BlockModel.zfight(self):
                a, b = issue.split(" vs ")[0].split("/")[0], issue.split(" vs ")[1].split("/")[0]
                if (a in carriers or b in carriers) and bone_of.get(a) not in rotated and bone_of.get(b) not in rotated:
                    issues.append(issue)
        return issues

    #: (box name, face) openings that are meant to be seen into (a hopper mouth, a vent).
    open_ok: set[tuple[str, str]] = set()

    def frames(self) -> dict[str, str | None]:
        """Each bone's frame: its nearest rotated ancestor (or itself), None for the model's own space. Boxes in one
        frame keep their places relative to each other however the frame is turned."""
        rot = {b.name: any(b.rotation) for b in self.bones}
        parent = {b.name: b.parent for b in self.bones}
        out: dict[str, str | None] = {}
        for bone in self.bones:
            n = bone.name
            while n is not None and not rot.get(n):
                n = parent.get(n)
            out[bone.name] = n
        return out

    def holes(self, plane: float = 0.3, eps: float = 0.02, step: float = 0.25) -> list[str]:
        """Missing faces you can see into: on a solid box (thicker than a plane), the part of a left-out face that no
        other box covers. A face may be left out only where another box hides it. A rotated bone's boxes are checked
        against the boxes turned with them (they had been skipped, and the radar dishes kept holes: after slice 9b);
        boxes rotated on their own are skipped."""
        bone_of = {bx.name: bone.name for bone in self.bones for bx in bone.boxes}
        frame_of = self.frames()
        groups: dict[str | None, list] = {}
        for b in self.boxes:
            if not b.rotation:
                groups.setdefault(frame_of.get(bone_of.get(b.name)), []).append(b)
        issues = []
        for solid in groups.values():
            issues += self._holes_in(solid, plane, eps, step)
        return issues

    def _holes_in(self, solid: list, plane: float, eps: float, step: float) -> list[str]:
        import numpy as np  # noqa: PLC0415
        axis = {"west": (0, 0), "east": (0, 1), "down": (1, 0), "up": (1, 1), "north": (2, 0), "south": (2, 1)}
        arr = np.array([[*c.frm, *c.to] for c in solid], dtype=np.float64).reshape(-1, 6)
        issues = []
        for i, b in enumerate(solid):
            lo, hi = b.frm, b.to
            if any(hi[k] - lo[k] < plane for k in range(3)) or (b.name, "*") in self.open_ok:
                continue
            for d, (ax, side) in axis.items():
                if d in b.faces or (b.name, d) in self.open_ok:
                    continue
                o = [k for k in range(3) if k != ax]
                at = hi[ax] + eps if side else lo[ax] - eps
                us = np.arange(lo[o[0]] + step / 2, hi[o[0]], step)
                vs = np.arange(lo[o[1]] + step / 2, hi[o[1]], step)
                if us.size == 0 or vs.size == 0:
                    continue
                # The boxes that could cover any of this face's sample points (all but the box itself).
                near = np.ones(len(solid), dtype=bool)
                near[i] = False
                near &= (arr[:, ax] - 2 * eps <= at) & (arr[:, 3 + ax] + 2 * eps >= at)
                for k in o:
                    near &= (arr[:, 3 + k] + 2 * eps >= lo[k]) & (arr[:, k] - 2 * eps <= hi[k])
                cand = arr[near]
                uu, vv = np.meshgrid(us, vs, indexing="ij")
                pts = np.zeros((uu.size, 3))
                pts[:, o[0]], pts[:, o[1]], pts[:, ax] = uu.ravel(), vv.ravel(), at
                if cand.shape[0] == 0:
                    open_n = pts.shape[0]
                else:
                    covered = np.zeros(pts.shape[0], dtype=bool)
                    for s in range(0, pts.shape[0], 4096):
                        q = pts[s:s + 4096, None, :]
                        covered[s:s + 4096] = np.all((q >= cand[None, :, :3] - 2 * eps) & (q <= cand[None, :, 3:] + 2 * eps), axis=2).any(axis=1)
                    open_n = int((~covered).sum())
                if open_n * step * step > 0.2:
                    issues.append(f"{b.name} has no {d} face: {open_n * step * step:.2f} px2 open")
        return issues

    def close_holes(self) -> list[str]:
        """Adds every left-out face that you could see into (see holes()), except openings listed in open_ok.
        Called by layout(), so the new faces get texture space and are painted like the box's other faces."""
        from gb.model import Face  # noqa: PLC0415
        added = []
        for issue in self.holes():
            name, rest = issue.split(" has no ")
            d = rest.split(" face")[0]
            box = next(b for b in self.boxes if b.name == name)
            box.faces[d] = Face()
            added.append(f"{name}/{d}")
        return added

    def close_floor(self, eps: float = 0.01) -> list[str]:
        """Bottoms for the boxes standing on the model's floor (its lowest level), each painted as its own top (sharing
        its texture rect, so no texture space): a model left open underneath shows straight through when seen from
        below, stood off the ground or over a drop (the superweapons, after the user's look, 2026-11-07).

        No two bottoms may overlap (seen from below they would flicker): an unturned box's bottom covers only where no
        bigger box's (or one already there) does: the whole of it a face; part of it polygons (a carrier sharing its top's
        rect); none, if it is all covered. Round parts' planks (their bottom is the part's own cap), boxes turned about x
        or z, flat ones and whole-box openings (open_ok "*") are left as they are."""
        from gb.model import Face, make_polygon  # noqa: PLC0415

        def level(b: Box) -> bool:
            r = b.rotation
            return not r or r.get("axis") == "y" or (not r.get("axis") and not r.get("x") and not r.get("z"))
        flat = [b for b in self.boxes if level(b) and b.poly is None and not b.round and b.to[1] - b.frm[1] > eps
                and (b.name, "*") not in self.open_ok]
        if not flat:
            return []
        floor = min(b.frm[1] for b in flat)
        on_floor = [b for b in flat if abs(b.frm[1] - floor) <= eps]
        # Bottoms already there (unturned), and the order the rest are closed in: biggest first.
        closed = [(b.frm[0], b.frm[2], b.to[0], b.to[2]) for b in on_floor if "down" in b.faces and not b.rotation]
        added = []
        for b in sorted(on_floor, key=lambda b: -(b.to[0] - b.frm[0]) * (b.to[2] - b.frm[2])):
            if "down" in b.faces:
                continue
            if b.rotation:                        # (turned about y: its bottom as it is)
                b.faces["down"] = Face()
                if "up" in b.faces:
                    self.share(b, "down", b, "up")
                self.open_ok.discard((b.name, "down"))
                added.append(b.name)
                continue
            rect = (b.frm[0], b.frm[2], b.to[0], b.to[2])
            left = [rect]
            for c in closed:
                left = [piece for r in left for piece in _minus(r, c)]
            left = [r for r in left if r[2] - r[0] > eps and r[3] - r[1] > eps]
            self.open_ok.discard((b.name, "down"))
            if not left:
                continue                          # (all under other bottoms)
            if left == [rect] or "up" not in b.faces:
                b.faces["down"] = Face()
                if "up" in b.faces:
                    self.share(b, "down", b, "up")
            else:
                y = b.frm[1]
                polys = [[(r[0], y, r[1]), (r[2], y, r[1]), (r[2], y, r[3]), (r[0], y, r[3])] for r in left]
                bone = self._bone_of[b.name]
                carrier = make_polygon(_OnBone(self, bone), f"{b.name}_under", polys, "down")
                carrier.faces["down"].shared_with = (b.name, "up")
            closed.append(rect)
            added.append(b.name)
        return added

    def layout(self) -> None:  # type: ignore[override]
        self.close_holes()
        super().layout()

    def save(self, tex, emissive=None):  # type: ignore[override]
        for issue in self.zfight():
            print(f"  z-fight warning [{self.name}]: {issue}")
        for issue in self.holes():
            print(f"  open-face warning [{self.name}]: {issue}")
        self.flatten(tex, emissive)
        self.bleed(tex)
        if emissive is not None:
            self.bleed(emissive)
        gp = write_json(self.geo_path(), self.to_geo_json())
        tp = tex.save(self.texture_path())
        if emissive is not None:
            # GeckoLib's auto-glow layer convention: <texture>_glowmask.png
            emissive.save(tp.with_name(tp.stem + "_glowmask.png"))
        return gp, tp


def _minus(r, c) -> list:
    """Rect r (x0, z0, x1, z1) less rect c: up to four rects."""
    x0, z0, x1, z1 = r
    cx0, cz0, cx1, cz1 = max(c[0], x0), max(c[1], z0), min(c[2], x1), min(c[3], z1)
    if cx0 >= cx1 or cz0 >= cz1:
        return [r]
    out = []
    if z0 < cz0:
        out.append((x0, z0, x1, cz0))
    if cz1 < z1:
        out.append((x0, cz1, x1, z1))
    if x0 < cx0:
        out.append((x0, cz0, cx0, cz1))
    if cx1 < x1:
        out.append((cx1, cz0, x1, cz1))
    return out


class _OnBone:
    """A GeckoLib model's boxes going into one bone (for gb.model.make_polygon)."""

    def __init__(self, g: "GeoModel", bone: str):
        self.g, self.bone = g, bone

    def box(self, name, frm, to, **kw):
        return self.g.box(name, frm, to, bone=self.bone, **kw)


@dataclass
class Animation:
    """One named animation. Values are in world space (degrees / pixels); export converts to Bedrock signs."""
    name: str
    length: float
    loop: bool | str = True
    bones: dict[str, dict[str, dict[float, tuple]]] = field(default_factory=dict)
    easing: dict[tuple[str, str], str] = field(default_factory=dict)

    def rotate(self, bone: str, keys: dict[float, Vec3], easing: str | None = None) -> "Animation":
        self.bones.setdefault(bone, {})["rotation"] = {float(t): tuple(map(float, v)) for t, v in keys.items()}
        if easing:
            self.easing[(bone, "rotation")] = easing
        return self

    def move(self, bone: str, keys: dict[float, Vec3], easing: str | None = None) -> "Animation":
        self.bones.setdefault(bone, {})["position"] = {float(t): tuple(map(float, v)) for t, v in keys.items()}
        if easing:
            self.easing[(bone, "position")] = easing
        return self

    def scale(self, bone: str, keys: dict[float, Vec3], easing: str | None = None) -> "Animation":
        self.bones.setdefault(bone, {})["scale"] = {float(t): tuple(map(float, v)) for t, v in keys.items()}
        if easing:
            self.easing[(bone, "scale")] = easing
        return self

    def to_json(self) -> dict:
        bones = {}
        for bone, channels in self.bones.items():
            bj = {}
            for ch, keys in channels.items():
                frames = {}
                for t, (x, y, z) in sorted(keys.items()):
                    if ch == "rotation":
                        v = [-x, -y, z]
                    elif ch == "position":
                        v = [-x, y, z]
                    else:
                        v = [x, y, z]
                    ease = self.easing.get((bone, ch))
                    frames[f"{t:g}"] = {"vector": v, "easing": ease} if ease else v
                bj[ch] = frames
            bones[bone] = bj
        out: dict = {"animation_length": self.length, "bones": bones}
        if self.loop:
            out["loop"] = self.loop
        return out


def rest_pose(animations: list[Animation], name: str = "idle") -> Animation:
    """A held rest pose covering every bone/channel the other animations touch, so a controller can ease the model
    back to rest (GeckoLib only stops a looping animation by switching to another one)."""
    idle = Animation(name, 0.05, loop="hold_on_last_frame")
    for a in animations:
        for bone, channels in a.bones.items():
            for ch in channels:
                rest = (1.0, 1.0, 1.0) if ch == "scale" else (0.0, 0.0, 0.0)
                idle.bones.setdefault(bone, {})[ch] = {0.0: rest}
    return idle


def save_animations(model_name: str, animations: list[Animation], kind: str = "block") -> Path:
    if not any(a.name == "idle" for a in animations):
        animations = [*animations, rest_pose(animations)]
    data = {"format_version": "1.8.0",
            "animations": {a.name: a.to_json() for a in animations}}
    return write_json(paths.ANIMATIONS / kind / f"{model_name}.animation.json", data)
