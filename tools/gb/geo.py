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
            boxes = [b for b in bone.boxes if not b.rotation]
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
            for b in bone.boxes:
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
                 if not b.rotation and bone_of.get(b.name) not in rotated]
        parent = {bone.name: bone.parent for bone in self.bones}

        def chain(name: str | None) -> list[str]:
            """The bone and its ancestors: a bone inside a hidden one is hidden too."""
            out = []
            while name:
                out.append(name)
                name = parent.get(name)
            return out
        issues = []
        for issue in zfight.check(boxes, tol):
            a, b = issue.split(" vs ")[0].split("/")[0], issue.split(" vs ")[1].split("/")[0]
            ca, cb = chain(bone_of.get(a)), chain(bone_of.get(b))
            if any(x != y and x in grp and y in grp for grp in self.exclusive for x in ca for y in cb):
                continue   # never drawn together (two of a group, or inside two of a group)
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

    def layout(self) -> None:  # type: ignore[override]
        self.close_holes()
        super().layout()

    def save(self, tex, emissive=None):  # type: ignore[override]
        for issue in self.zfight():
            print(f"  z-fight warning [{self.name}]: {issue}")
        for issue in self.holes():
            print(f"  open-face warning [{self.name}]: {issue}")
        self.bleed(tex)
        if emissive is not None:
            self.bleed(emissive)
        gp = write_json(self.geo_path(), self.to_geo_json())
        tp = tex.save(self.texture_path())
        if emissive is not None:
            # GeckoLib's auto-glow layer convention: <texture>_glowmask.png
            emissive.save(tp.with_name(tp.stem + "_glowmask.png"))
        return gp, tp


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
