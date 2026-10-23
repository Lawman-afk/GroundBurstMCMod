"""Views of a superweapon's formed model with its missiles on it, drawn as the game draws them (MachineGeoRenderer's
missile layer): each missile's own model at its bone, posed by the bone as the animation has it at a given time (an
erector raised, a roof open), so a missile's fit on its launcher can be checked lying and raised. Markers (small white
cubes) show where the block entity lights each missile, to check against the raised missiles' tails.

    tools/.venv/Scripts/python.exe tools/preview/superweapon_view.py scud_storm
    tools/.venv/Scripts/python.exe tools/preview/superweapon_view.py scud_storm --time 3.0

Writes tools/out/previews/<model>_missiles_<time>.png.
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
import geo_preview as GP  # noqa: E402
import render as R  # noqa: E402


def _ry(a: float) -> np.ndarray:
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


def _rx(a: float) -> np.ndarray:
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


def bone_matrices(geo: dict, animation: dict | None, time: float) -> dict[str, np.ndarray]:
    """Each bone's matrix in the preview's model space (geo_preview's maths), and its pivot there."""
    bones = {b["name"]: b for b in geo["bones"]}
    cache: dict[str, np.ndarray] = {}

    def m(name: str) -> np.ndarray:
        if name in cache:
            return cache[name]
        b = bones[name]
        piv = np.array([-b["pivot"][0], b["pivot"][1], b["pivot"][2]], dtype=float)
        r0 = b.get("rotation", [0, 0, 0])
        rot = np.radians(np.array([-r0[0], -r0[1], r0[2]], dtype=float))
        pos = np.zeros(3)
        scl = np.ones(3)
        if animation and name in animation.get("bones", {}):
            ch = animation["bones"][name]
            if "rotation" in ch:
                a = GP._sample(ch["rotation"], time)
                rot = rot + np.radians(np.array([-a[0], -a[1], a[2]]))
            if "position" in ch:
                a = GP._sample(ch["position"], time)
                pos = np.array([-a[0], a[1], a[2]])
            if "scale" in ch:
                scl = GP._sample(ch["scale"], time)
        mat = GP._t(pos) @ GP._t(piv) @ GP._mat4(GP._rot_zyx(*rot)) @ np.diag([*scl, 1.0]) @ GP._t(-piv)
        parent = b.get("parent")
        cache[name] = (m(parent) @ mat) if parent else mat
        return cache[name]
    return {name: m(name) for name in bones}


def missile_quads(warhead: str, bone: dict, matrix: np.ndarray, lying: bool, turn: float) -> list[R.Quad]:
    """The missile carrying {warhead} as the game draws it at {bone}: its tail at the bone's pivot, lying nose to the bone's
    front or standing, turned {turn} degrees about its axis, all moved with the bone."""
    shape = json.loads((paths.ASSETS / "missiles.json").read_text(encoding="utf-8"))[warhead]
    pivot = np.array([-bone["pivot"][0], bone["pivot"][1], bone["pivot"][2]], dtype=float)
    rot = (_rx(math.radians(-90)) if lying else np.eye(3)) @ _ry(math.radians(turn))
    out: list[R.Quad] = []
    model = R.load_model(f"groundzero:item/missile/{warhead}")
    for q in R.build_quads(model):
        local = 4.0 * (q.pos - np.array([8.0, shape["tail"], 8.0]))
        p = (rot @ local.T).T + pivot
        world = (matrix @ np.c_[p, np.ones(len(p))].T).T[:, :3]
        n = matrix[:3, :3] @ (rot @ q.normal)
        out.append(R.Quad(world + np.array([8.0, 0.0, 8.0]), q.uv, q.tex, q.shade, q.emissive, n, q.directional))
    return out


def marker(at, size: float = 1.2) -> list[R.Quad]:
    """A small white cube at a model point (where the block entity lights a missile)."""
    tex = np.full((4, 4, 4), 255, dtype=np.uint8)
    x, y, z = at[0] + 8.0, at[1], at[2] + 8.0
    s = size / 2
    out = []
    for d, n in R.NORMALS.items():
        n = np.array(n, dtype=float)
        u = np.array([1.0, 0, 0]) if abs(n[0]) < 0.5 else np.array([0, 0, 1.0])
        v = np.cross(n, u)
        c = np.array([x, y, z]) + n * s
        pts = np.array([c - u * s - v * s, c + u * s - v * s, c + u * s + v * s, c - u * s + v * s])
        out.append(R.Quad(pts, np.array([[0, 0], [4, 0], [4, 4], [0, 4]], dtype=float), tex, 1.0, True, n, False))
    return out


#: Each superweapon: its missiles (bone, warhead, lying, turn), the animation that moves them, and where the block entity
#: lights each one (model pixels), as ScudStormBlockEntity has them.
def scud_points() -> list[tuple[float, float, float]]:
    out = []
    for k in range(9):
        a = math.radians(20 + 40 * k)
        out.append((35 * math.sin(a), 28.0, 112 - 35 * math.cos(a)))
    return out


PRESETS = {
    "arms_dealer": dict(missiles=[("missile", "scud_storm_warhead", True, 45.0)], anim="working", points=lambda: [],
                        shots=[("iso front", 30, 225, None, None), ("iso back", 30, 45, None, None), ("from above", 85, 180, None, None),
                               ("low front", 8, 200, None, None), ("the yard", 35, 210, (0, 30, 32), 90), ("workshop doors", 15, 190, (-12, 24, 56), 60),
                               ("awning and racks", 25, 250, (-46, 30, 32), 50), ("gate", 15, 200, (0, 30, -8), 50)]),
    "war_factory": dict(missiles=[("missile", "nuclear_missile_warhead", True, 90.0)], anim="working", points=lambda: [],
                        shots=[("iso front", 30, 225, None, None), ("iso back", 30, 45, None, None), ("from above", 85, 180, None, None),
                               ("low front", 8, 200, None, None), ("the hall", 20, 220, (-32, 40, 144), 110), ("the yard", 30, 230, (32, 30, 100), 150),
                               ("crane", 20, 250, (50, 50, 150), 70), ("crane's foot", 8, 270, (64, 22, 150), 40),
                               ("crane's foot, front", 8, 180, (64, 22, 150), 40), ("annex and chimney", 20, 210, (-48, 50, 32), 90),
                               ("door and star", 15, 260, (6, 28, 144), 50), ("conveyor", 35, 200, (-42, 20, 80), 50)]),
    "nuclear_missile_silo": dict(missiles=[("missile", "nuclear_missile_warhead", True, 90.0)], anim="raise",
                                 points=lambda: [(0.0, 20.0, 192.0)],
                                 shots=[("iso front", 30, 225, None, None), ("iso back", 30, 45, None, None),
                                        ("from above", 85, 180, None, None), ("low front", 8, 200, None, None),
                                        ("the gable", 15, 200, (0, 40, -8), 70), ("the hall's side", 15, 270, (-40, 30, 112), 140),
                                        ("launch end", 25, 230, (0, 50, 192), 90), ("control house", 20, 220, (-64, 30, 16), 60),
                                        ("the tower", 15, 240, (48, 90, 192), 120), ("tower top and arm", 20, 220, (30, 120, 192), 50),
                                        ("erector and rams", 35, 240, (0, 26, 160), 70), ("inside from above", 70, 200, (0, 20, 112), 120)]),
    "scud_storm": dict(missiles=[(f"scud{k}", "scud_storm_warhead", True, 45.0) for k in range(9)], anim="raise",
                       points=scud_points,
                       shots=[("iso front", 30, 225, None, None), ("iso back", 30, 45, None, None), ("from above", 85, 180, None, None),
                              ("low front", 8, 200, None, None), ("the ring", 35, 210, (0, 30, 112), 160),
                              ("an erector", 25, 250, (50, 26, 70), 70), ("tails and tank", 20, 200, (0, 34, 86), 70),
                              ("the scrap", 30, 160, (0, 26, 112), 70), ("domed tower", 20, 210, (-128, 40, 0), 60),
                              ("antenna tower", 20, 40, (112, 60, 240), 80), ("guard hut", 25, 230, (98, 40, 12), 60),
                              ("crates and drums", 30, 135, (-102, 22, 10), 50), ("drums", 30, 315, (103, 20, 195), 40)]),
}


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("model")
    ap.add_argument("--time", type=float, default=0.0)
    ap.add_argument("--cell", type=int, default=380)
    ap.add_argument("--only", type=int, default=None, help="draw only this missile and its marker (to check one bearing)")
    args = ap.parse_args()
    preset = PRESETS[args.model]
    mid = f"groundzero:block/{args.model}"
    geo = json.loads((paths.GEO / "block" / f"{args.model}.geo.json").read_text(encoding="utf-8"))["minecraft:geometry"][0]
    bones = {b["name"]: b for b in geo["bones"]}
    anim = GP.load_animation(mid, preset["anim"]) if args.time > 0 else None
    mats = bone_matrices(geo, anim, args.time)
    quads = GP.geo_quads(mid, anim, args.time)
    for i, (bone, warhead, lying, turn) in enumerate(preset["missiles"]):
        if args.only is None or args.only == i:
            quads += missile_quads(warhead, bones[bone], mats[bone], lying, turn)
    if args.time > 0:
        for i, p in enumerate(preset["points"]()):
            if args.only is None or args.only == i:
                quads += marker(p, 1.2 if args.only is None else 4.0)
    centre, extent = R.model_bounds(quads)
    light = np.array([0.4, 0.8, 0.45])
    light /= np.linalg.norm(light)
    shots = []
    for label, pitch, yaw, c, e in preset["shots"]:
        img = R._checker(args.cell).convert("RGBA")
        cc = centre if c is None else np.array([c[0] + 8.0, c[1], c[2] + 8.0])
        img.alpha_composite(R.render(quads, args.cell, pitch, yaw, cc, extent if e is None else e, light_dir=light))
        shots.append((label, img))
    cols = 4
    rows = (len(shots) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * args.cell, rows * (args.cell + 18)), (28, 30, 34, 255))
    d = ImageDraw.Draw(sheet)
    for i, (label, img) in enumerate(shots):
        x, y = (i % cols) * args.cell, (i // cols) * (args.cell + 18)
        sheet.alpha_composite(img, (x, y))
        d.text((x + 6, y + args.cell + 3), f"{label}  t={args.time:g}", fill=(210, 210, 210, 255))
    out = paths.PREVIEWS / f"{args.model}_missiles_{args.time:g}{'' if args.only is None else f'_only{args.only}'}.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    sheet.convert("RGB").save(out)
    print(out)


if __name__ == "__main__":
    main()
