"""Close-up views of a formed structure's GeckoLib model, for reviewing its textures at a readable size: the whole
model from a few angles, then crops on its details. Writes tools/out/previews/<model>_views.png.

    tools/.venv/Scripts/python.exe tools/preview/geo_views.py strategic_assembly_hall
    tools/.venv/Scripts/python.exe tools/preview/geo_views.py launch_complex --cell 360

Each preset below lists its shots: (label, pitch, yaw, centre in model pixels or None for the whole model, extent in
model pixels or None, animation time or None). Of each set of swappable bones (the noses, the missiles) only the first
is drawn.
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))

from gb import paths  # noqa: E402
import geo_preview as GP  # noqa: E402
import render as R  # noqa: E402

WHOLE = [("iso front", 30, 225, None, None, None), ("iso back", 30, 45, None, None, None),
         ("low front", 10, 200, None, None, None), ("from above", 80, 180, None, None, None)]

# A machine's model with no preset of its own: from all four corners, low from the front, and from above.
DEFAULT = dict(shots=[("iso front", 30, 225, None, None, None), ("iso front-left", 30, 135, None, None, None),
                      ("iso back", 30, 45, None, None, None), ("iso back-right", 30, 315, None, None, None),
                      ("low front", 8, 200, None, None, None), ("from above", 80, 180, None, None, None)])

PRESETS = {
    "special_weapons_assembler": dict(
        anim="working",
        shots=WHOLE + [
            ("front: desk, cask, first ring", 30, 215, (0, 28, 30), 90, None),
            ("the rings from the bay", 12, 190, (0, 40, 96), 120, None),
            ("a ring, close", 20, 225, (0, 40, 48), 56, 0.5),
            ("back: ports and conduits", 40, 30, (-6, 18, 186), 90, None),
            ("detail: plinth and lamps", 25, 205, (0, 22, 42), 34, None),
            ("detail: rotor nodes", 10, 180, (0, 40, 44), 40, 1.0),
            ("detail: cradle", 30, 240, (-12, 24, 164), 34, None),
            ("detail: cask", 30, 225, (-31, 22, 11), 26, None),
        ]),
    "strategic_assembly_hall": dict(
        keep_one=("nose_",), anim="working",
        shots=WHOLE + [
            ("front: desk, cradles, floor", 35, 215, (0, 22, 30), 90, None),
            ("back: ports and pallet", 40, 30, (-6, 18, 188), 90, None),
            ("column and girders", 20, 250, (48, 60, 96), 70, None),
            ("the crane", 25, 210, (0, 98, 184), 100, None),
            ("detail: column foot", 25, 230, (44, 18, 96), 34, None),
            ("detail: girder end", 20, 200, (48, 84, -6), 30, None),
            ("detail: cradle", 30, 240, (-12, 24, 128), 34, None),
            ("detail: item port", 60, 200, (-32, 14, 192), 30, None),
        ]),
    "launch_complex": dict(
        keep_one=("nose_",), hide=("strategic", "icbm"), anim="working",
        shots=WHOLE + [
            ("deck and trench", 40, 240, (-10, 10, 32), 90, None),
            ("tower foot", 20, 210, (48, 20, 40), 70, None),
            ("tower top", 20, 210, (48, 200, 40), 70, None),
            ("the arms", 15, 250, (30, 100, 32), 90, None),
            ("detail: trench mouth", 35, 250, (-36, 10, 32), 34, None),
            ("detail: hold-down post", 30, 220, (-13, 18, 19), 20, None),
            ("detail: console and lip", 30, 200, (0, 16, 0), 30, None),
            ("detail: walkway", 35, 230, (48, 66, 30), 30, None),
        ]),
    "launch_silo": dict(
        keep_one=("nose_",), anim="working",
        shots=WHOLE + [
            ("pad", 40, 220, (0, 10, 16), 60, None),
            ("tower foot", 20, 210, (31, 20, 16), 50, None),
            ("arm and hinge", 15, 250, (18, 100, 16), 50, None),
            ("platform", 30, 210, (31, 140, 16), 40, None),
        ]),
    "launch_computer": dict(
        anim="working",
        shots=WHOLE + [
            ("desk and screens", 20, 200, (8, 12, 0), 36, None),
            ("dish", 20, 200, (0, 52, 12), 34, None),
            ("mast", 20, 225, (0, 30, 16), 40, None),
            ("desk side", 20, 120, (20, 8, 0), 30, None),
            ("dish from above", 60, 160, (0, 54, 10), 34, None),
            ("dish from below", -25, 200, (0, 54, 10), 34, None),
            ("dish side", 10, 270, (0, 54, 10), 34, None),
            ("dish back", 25, 20, (0, 54, 12), 34, None),
        ]),
    "radar_array": dict(
        anim="working",
        shots=WHOLE + [
            ("deck and cabinet", 30, 210, (0, 10, 4), 50, None),
            ("dish", 20, 200, (0, 76, 8), 60, None),
            ("mast", 20, 225, (0, 40, 16), 50, None),
            ("power box", 30, 30, (0, 10, 34), 30, None),
            ("dish from above", 60, 160, (0, 76, 8), 60, None),
            ("dish from below", -25, 200, (0, 76, 8), 60, None),
            ("dish side", 10, 270, (0, 76, 8), 60, None),
            ("dish back", 25, 20, (0, 76, 12), 60, None),
        ]),
    "missile_wreck": dict(
        shots=[("iso front", 30, 225, None, None, None), ("iso back", 30, 45, None, None, None),
               ("low east", 8, 110, None, None, None), ("from above", 80, 180, None, None, None),
               ("tail", 25, 200, (0, 12, 0), 60, None), ("tail's torn end", 15, 120, (-12, 12, -14), 36, None),
               ("body's torn ends", 20, 250, (-30, -6, -30), 80, None), ("nose", 20, 150, (-30, -20, -92), 60, None)]),
    "missile_silo": dict(
        keep_one=("nose_",), hide=("strategic", "icbm"), anim="preview_doors",
        shots=WHOLE + [
            ("collar and doors", 35, 225, (0, 4, 32), 90, 0.0),
            ("doors open", 35, 225, (0, 4, 32), 90, 2.0),
            ("console", 25, 200, (0, 8, -8), 40, 0.0),
        ]),
}


def hidden_bones(model: str, keep_one: tuple[str, ...], hide: tuple[str, ...]) -> tuple[str, ...]:
    geo = json.loads((paths.GEO / "block" / f"{model}.geo.json").read_text(encoding="utf-8"))["minecraft:geometry"][0]
    names = [b["name"] for b in geo["bones"]]
    out = list(hide)
    for prefix in keep_one:
        group = [n for n in names if n.startswith(prefix)]
        out += group[1:]
    return tuple(out)


def to_render(p) -> np.ndarray:
    """A model point (GeoModel pixels) to the renderer's space (the block's centre is at 8, 8: GeoModel writes x mirrored
    for GeckoLib, and the renderer mirrors it back)."""
    return np.array([p[0] + 8.0, p[1], p[2] + 8.0])


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("model")
    ap.add_argument("--cell", type=int, default=400)
    args = ap.parse_args()
    preset = PRESETS.get(args.model, DEFAULT)
    mid = f"groundburst:block/{args.model}"
    hidden = hidden_bones(args.model, preset.get("keep_one", ()), preset.get("hide", ()))
    anim = GP.load_animation(mid, preset["anim"]) if preset.get("anim") else None
    light = np.array([0.4, 0.8, 0.45])
    light /= np.linalg.norm(light)
    whole_q = GP.geo_quads(mid, None, 0.0, hidden=hidden)
    centre, extent = R.model_bounds(whole_q)
    shots = []
    for label, pitch, yaw, c, e, t in preset["shots"]:
        q = GP.geo_quads(mid, anim if t is not None else None, t or 0.0, hidden=hidden)
        img = R._checker(args.cell).convert("RGBA")
        img.alpha_composite(R.render(q, args.cell, pitch, yaw, centre if c is None else to_render(c), extent if e is None else e,
                                     light_dir=light))
        shots.append((label, img))
    cols = 4
    rows = (len(shots) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * args.cell, rows * (args.cell + 18)), (28, 30, 34, 255))
    d = ImageDraw.Draw(sheet)
    for i, (label, img) in enumerate(shots):
        x, y = (i % cols) * args.cell, (i // cols) * (args.cell + 18)
        sheet.alpha_composite(img, (x, y))
        d.text((x + 6, y + args.cell + 3), label, fill=(210, 210, 210, 255))
    out = paths.PREVIEWS / f"{args.model}_views.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    sheet.convert("RGB").save(out)
    print(out)


if __name__ == "__main__":
    main()
