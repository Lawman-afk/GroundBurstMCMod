"""Preview renderer support for GeckoLib geometry and animations.

Converts a .geo.json (+ optional .animation.json at a given time) into world-space quads using the same maths as
GeckoLib 5.5.7 (GeometryCube.bake, VertexSet quads, GeometryQuadUvs, RenderUtil.prepMatrixForBone), then reuses the
block-model rasteriser.

    tools\\.venv\\Scripts\\python.exe tools\\preview\\geo_preview.py groundzero:block/ore_crusher --anim working
"""
from __future__ import annotations

import argparse
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))
from gz import paths  # noqa: E402
import render as R  # noqa: E402


def _rot_zyx(rx: float, ry: float, rz: float) -> np.ndarray:
    """Rz * Ry * Rx with radian angles (Quaternionf.rotationZYX)."""
    return R._rz(rz) @ R._ry(ry) @ R._rx(rx)


def _mat4(rot: np.ndarray | None = None, trans=(0, 0, 0)) -> np.ndarray:
    m = np.eye(4)
    if rot is not None:
        m[:3, :3] = rot
    m[:3, 3] = trans
    return m


def _t(v) -> np.ndarray:
    return _mat4(None, v)


# --- animation sampling ----------------------------------------------------------------------------------------------

def _ease(name: str | None, t: float) -> float:
    if name in (None, "linear"):
        return t
    if name == "easeInOutSine":
        return -(math.cos(math.pi * t) - 1) / 2
    if name == "easeInSine":
        return 1 - math.cos(t * math.pi / 2)
    if name == "easeOutSine":
        return math.sin(t * math.pi / 2)
    if name == "easeInOutQuad":
        return 2 * t * t if t < 0.5 else 1 - (-2 * t + 2) ** 2 / 2
    if name == "step":
        return 0.0
    return t


def _sample(channel: dict, time: float) -> np.ndarray:
    keys = sorted((float(k), v) for k, v in channel.items())

    def vec(v):
        if isinstance(v, dict):
            v = v.get("vector", v.get("post", v.get("pre")))
        return np.array(v, dtype=float), None

    if time <= keys[0][0]:
        return vec(keys[0][1])[0]
    for (t0, v0), (t1, v1) in zip(keys, keys[1:]):
        if t0 <= time <= t1:
            a, _ = vec(v0)
            b, _ = vec(v1)
            ease = v1.get("easing") if isinstance(v1, dict) else None
            f = _ease(ease, (time - t0) / max(1e-9, t1 - t0))
            return a + (b - a) * f
    return vec(keys[-1][1])[0]


def load_animation(model_id: str, anim: str) -> dict:
    ns, p = R._split_id(model_id)
    kind, name = p.split("/", 1)
    data = json.loads((paths.ANIMATIONS / kind / f"{name}.animation.json").read_text(encoding="utf-8"))
    key = anim if anim in data["animations"] else f"animation.{name}.{anim}"
    if key not in data["animations"]:
        raise KeyError(f"{key} not in {list(data['animations'])}")
    return data["animations"][key]


# --- geometry ------------------------------------------------------------------------------------------------------------

def geo_quads(model_id: str, animation: dict | None = None, time: float = 0.0, labels: list | None = None) -> list[R.Quad]:
    ns, p = R._split_id(model_id)
    kind, name = p.split("/", 1)
    geo = json.loads((paths.GEO / kind / f"{name}.geo.json").read_text(encoding="utf-8"))["minecraft:geometry"][0]
    tw, th = geo["description"]["texture_width"], geo["description"]["texture_height"]
    tex = R._load_tex(f"{paths.MODID}:{kind}/{name}")
    bones = {b["name"]: b for b in geo["bones"]}

    def bone_matrix(name: str) -> np.ndarray:
        b = bones[name]
        piv = np.array([-b["pivot"][0], b["pivot"][1], b["pivot"][2]], dtype=float)
        rot = np.radians(np.array([-b.get("rotation", [0, 0, 0])[0], -b.get("rotation", [0, 0, 0])[1], b.get("rotation", [0, 0, 0])[2]], dtype=float))
        pos = np.zeros(3)
        scl = np.ones(3)
        if animation and name in animation.get("bones", {}):
            ch = animation["bones"][name]
            if "rotation" in ch:
                a = _sample(ch["rotation"], time)
                rot = rot + np.radians(np.array([-a[0], -a[1], a[2]]))
            if "position" in ch:
                a = _sample(ch["position"], time)
                pos = np.array([-a[0], a[1], a[2]])
            if "scale" in ch:
                scl = _sample(ch["scale"], time)
        m = _t(pos) @ _t(piv) @ _mat4(_rot_zyx(*rot)) @ np.diag([*scl, 1.0]) @ _t(-piv)
        parent = b.get("parent")
        return (bone_matrix(parent) @ m) if parent else m

    quads: list[R.Quad] = []
    for bname, b in bones.items():
        bm = bone_matrix(bname)
        for ci, c in enumerate(b.get("cubes", [])):
            ox, oy, oz = c["origin"]
            sx, sy, sz = c["size"]
            inf = c.get("inflate", 0)
            x0, x1 = -(ox + sx) - inf, -ox + inf
            y0, y1 = oy - inf, oy + sy + inf
            z0, z1 = oz - inf, oz + sz + inf
            TLB, TRB = (x0, y1, z0), (x0, y1, z1)
            TLF, TRF = (x1, y1, z0), (x1, y1, z1)
            BLB, BRB = (x0, y0, z0), (x0, y0, z1)
            BLF, BRF = (x1, y0, z0), (x1, y0, z1)
            verts = {
                "north": [TLB, TLF, BLF, BLB], "south": [TRF, TRB, BRB, BRF],
                "west": [TRB, TLB, BLB, BRB], "east": [TLF, TRF, BRF, BLF],
                "up": [TRB, TRF, TLF, TLB], "down": [BLB, BLF, BRF, BRB],
            }
            cm = np.eye(4)
            if "rotation" in c:
                piv = np.array([-c["pivot"][0], c["pivot"][1], c["pivot"][2]], dtype=float)
                r = np.radians([-c["rotation"][0], -c["rotation"][1], c["rotation"][2]])
                cm = _t(piv) @ _mat4(_rot_zyx(*r)) @ _t(-piv)
            full = bm @ cm
            for d, face in c.get("uv", {}).items():
                u, v = face["uv"]
                us, vs = face["uv_size"]
                ul, ur = u, u + us          # GeckoLib (non-mirrored) swaps: vertex 0 takes the right edge
                uvp = np.array([(ur, v), (ul, v), (ul, v + vs), (ur, v + vs)], dtype=float)
                pos = np.array([list(vv) + [1.0] for vv in verts[d]])
                wp = (full @ pos.T).T[:, :3]
                n = np.cross(wp[1] - wp[0], wp[3] - wp[0])
                nl = np.linalg.norm(n)
                if nl < 1e-9:
                    continue
                n = n / nl
                # Point the normal outward (GeckoLib winds quads consistently; correct if needed).
                expected = np.array(R.NORMALS[d], dtype=float)
                if np.dot(n, full[:3, :3] @ expected) < 0:
                    n = -n
                shade = 0.6 * n[0] ** 2 + 0.8 * n[2] ** 2 + (1.0 if n[1] > 0 else 0.5) * n[1] ** 2
                if labels is not None:
                    labels.append(f"{bname}#{ci}/{d}")
                quads.append(R.Quad(wp + np.array([8.0, 0.0, 8.0]), uvp * np.array([tex.shape[1] / tw, tex.shape[0] / th]),
                                    tex, shade, False, n, True))
    return quads


def contact_sheet(model_id: str, anim: str | None = None, frames: int = 4, cell: int = 300) -> Path:
    animation = load_animation(model_id, anim) if anim else None
    quads = geo_quads(model_id)
    center, extent = R.model_bounds(quads)
    light = np.array([0.4, 0.8, 0.45])
    light /= np.linalg.norm(light)
    tiles = []
    for label, pitch, yaw in R.VIEWS:
        tile = R._checker(cell).convert("RGBA")
        tile.alpha_composite(R.render(quads, cell, pitch, yaw, center, extent, light_dir=light))
        tiles.append((label, tile))
    if animation:
        length = animation.get("animation_length", 1.0)
        for i in range(frames):
            t = length * i / frames
            q = geo_quads(model_id, animation, t)
            tile = R._checker(cell).convert("RGBA")
            tile.alpha_composite(R.render(q, cell, 30, 225, center, extent, light_dir=light))
            tiles.append((f"{anim} t={t:.2f}s", tile))
    cols = 4
    rows = math.ceil(len(tiles) / cols)
    header = 34
    sheet = Image.new("RGBA", (cols * cell, header + rows * (cell + 18)), (28, 30, 34, 255))
    d = ImageDraw.Draw(sheet)
    pts = np.concatenate([q.pos for q in quads])
    dims = pts.max(axis=0) - pts.min(axis=0)
    d.text((10, 10), f"{model_id} (GeckoLib)   {len(quads)} quads   size {dims[0]:.1f} x {dims[1]:.1f} x {dims[2]:.1f} px", fill=(230, 230, 230, 255))
    for i, (label, tile) in enumerate(tiles):
        x, y = (i % cols) * cell, header + (i // cols) * (cell + 18)
        sheet.alpha_composite(tile, (x, y))
        d.text((x + 6, y + cell + 3), label, fill=(200, 200, 200, 255))
    out = paths.PREVIEWS / f"{model_id.replace(':', '_').replace('/', '_')}{'_' + anim if anim else ''}.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    sheet.convert("RGB").save(out)
    return out


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("model")
    ap.add_argument("--anim")
    ap.add_argument("--frames", type=int, default=4)
    args = ap.parse_args()
    print(contact_sheet(args.model, args.anim, args.frames))


if __name__ == "__main__":
    main()
