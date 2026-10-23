"""Views of a missile assembler's formed model with a missile on it, drawn as the game draws it (MachineGeoRenderer's
missile layer): the warhead's own missile, its first {laid} assembly sections, at the model's "missile" bone, lying on
the cradles (set on the saddles by its own girth, as MissileShapes and the block entity's cradles have it) or standing
on the stand. Writes tools/out/previews/<model>_<warhead>.png.

    tools/.venv/Scripts/python.exe tools/preview/assembler_view.py light_missile_assembler he_warhead
    tools/.venv/Scripts/python.exe tools/preview/assembler_view.py strategic_missile_assembler fission_warhead --laid 2 --time 4
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
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "assets"))

from gz import paths  # noqa: E402
import geo_preview as GP  # noqa: E402
import render as R  # noqa: E402
import assemblers as A  # noqa: E402


def _ry(a: float) -> np.ndarray:
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


def _rx(a: float) -> np.ndarray:
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


def radius_at(shape: dict, y: float) -> float:
    r = 0.0
    for a, b, rr in shape["profile"]:
        if a <= y < b:
            r = max(r, rr)
    return r


def skin_radius(shape: dict, y: float) -> float:
    for k in range(9):
        r = max(radius_at(shape, y - k), radius_at(shape, y + k))
        if r > 0:
            return r
    return 4.0


def missile_quads(warhead: str, laid: int, pivot: np.ndarray, lying: bool, turn: float) -> list[R.Quad]:
    """The missile's first {laid} sections as the game poses them at a bone whose pivot (model pixels) is {pivot}."""
    shape = json.loads((paths.ASSETS / "missiles.json").read_text(encoding="utf-8"))[warhead]
    rot = (_rx(math.radians(-90)) if lying else np.eye(3)) @ _ry(math.radians(turn))
    out: list[R.Quad] = []
    for k in range(1, laid + 1):
        model = R.load_model(f"groundzero:item/missile/{warhead}_part{k}")
        for q in R.build_quads(model):
            m = 4.0 * (q.pos - np.array([8.0, shape["tail"], 8.0]))           # the item drawn four times over, tail at 0
            p = (rot @ m.T).T + pivot
            n = rot @ q.normal
            out.append(R.Quad(p + np.array([8.0, 0.0, 8.0]), q.uv, q.tex, q.shade, q.emissive, n, q.directional))
    return out


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("model")
    ap.add_argument("warhead")
    ap.add_argument("--laid", type=int, default=99)
    ap.add_argument("--anim", default="working")
    ap.add_argument("--time", type=float, default=None)
    ap.add_argument("--cell", type=int, default=420)
    args = ap.parse_args()
    mid = f"groundzero:block/{args.model}"
    geo = json.loads((paths.GEO / "block" / f"{args.model}.geo.json").read_text(encoding="utf-8"))["minecraft:geometry"][0]
    bone = next(b for b in geo["bones"] if b["name"] == "missile")
    pivot = np.array([-bone["pivot"][0], bone["pivot"][1], bone["pivot"][2]], dtype=float)
    shape = json.loads((paths.ASSETS / "missiles.json").read_text(encoding="utf-8"))[args.warhead]
    laid = min(args.laid, len(shape["parts"]))
    cr = A.CRADLES.get(args.model)
    lying = cr is not None
    if lying:
        r = max(skin_radius(shape, h) for h in cr["at"])
        pivot[1] = cr["top"] + r                                       # set on the saddles by its own girth
    anim = GP.load_animation(mid, args.anim) if args.time is not None else None
    quads = GP.geo_quads(mid, anim, args.time or 0.0) + missile_quads(args.warhead, laid, pivot, lying, 45 if lying else 180)
    centre, extent = R.model_bounds(quads)
    light = np.array([0.4, 0.8, 0.45])
    light /= np.linalg.norm(light)
    mp = pivot + np.array([8.0, 0.0, 8.0])
    length = shape["length"]
    mid_m = mp + (np.array([0, 0, -length / 2]) if lying else np.array([0, length / 2, 0]))
    tail_m = mp + (np.array([0, 0, -10]) if lying else np.array([0, 12, 0]))
    nose_m = mp + (np.array([0, 0, -length + 10]) if lying else np.array([0, length - 12, 0]))
    shots = [("iso front", 30, 225, centre, extent), ("iso back", 30, 45, centre, extent), ("side", 8, 270, centre, extent),
             ("from above", 80, 180, centre, extent), ("missile", 20, 240, mid_m, length * 1.1),
             ("tail / foot", 15, 250, tail_m, 40), ("nose / top", 15, 210, nose_m, 40), ("low front", 6, 200, centre, extent)]
    imgs = []
    for label, pitch, yaw, c, e in shots:
        img = R._checker(args.cell).convert("RGBA")
        img.alpha_composite(R.render(quads, args.cell, pitch, yaw, c, e, light_dir=light))
        imgs.append((label, img))
    cols = 4
    rows = (len(imgs) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * args.cell, rows * (args.cell + 18)), (30, 32, 36, 255))
    d = ImageDraw.Draw(sheet)
    for i, (label, img) in enumerate(imgs):
        x, y = (i % cols) * args.cell, (i // cols) * (args.cell + 18)
        sheet.paste(img, (x, y + 18))
        d.text((x + 4, y + 3), label, fill=(220, 220, 220, 255))
    out = paths.ROOT / "tools" / "out" / "previews" / f"{args.model}_{args.warhead}.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(out)
    print(out)


if __name__ == "__main__":
    main()
