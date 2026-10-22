"""Close-up views of the formed Missile Silo (tools/assets/silo.py): the top with its doors shut and open, and the
shaft with each missile it can hold, at a readable size. Writes tools/out/previews/missile_silo_views.png.

    tools/.venv/Scripts/python.exe tools/preview/silo_views.py
"""
from __future__ import annotations

import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))

from gz import paths  # noqa: E402
import geo_preview as GP  # noqa: E402
import render as R  # noqa: E402

MODEL = "groundzero:block/missile_silo"
NOSES_BUT = lambda keep: tuple(n for n in ("nose_", "snose_") if n != keep)  # noqa: E731


def quads(t_doors: float, hidden: tuple[str, ...], ymin: float | None = None, ymax: float | None = None):
    anim = GP.load_animation(MODEL, "preview_doors")
    q = GP.geo_quads(MODEL, anim, t_doors, hidden=hidden)
    if ymin is not None:
        q = [x for x in q if x.pos[:, 1].min() >= ymin]
    if ymax is not None:
        q = [x for x in q if x.pos[:, 1].max() <= ymax]
    return q


def tile(q, pitch, yaw, cell=420, centre=None, extent=None):
    c, e = R.model_bounds(q)
    light = np.array([0.4, 0.8, 0.45])
    light /= np.linalg.norm(light)
    img = R._checker(cell).convert("RGBA")
    img.alpha_composite(R.render(q, cell, pitch, yaw, c if centre is None else centre, e if extent is None else extent, light_dir=light))
    return img


def main() -> None:
    strategic_only = ("missile", "icbm")               # hide the others: the Strategic one shows
    usual_only = ("strategic", "icbm")
    noses = ("nose_he_warhead", "snose_he_warhead")
    hide_noses = tuple(n for n in ())
    shots = []
    top_shut = quads(0.0, ("missile", "strategic", "icbm"), ymin=-20)
    top_open = quads(2.0, ("missile", "strategic", "icbm"), ymin=-20)
    shots.append(("top, doors shut (iso)", tile(top_shut, 35, 225)))
    shots.append(("top, doors open (iso)", tile(top_open, 35, 225)))
    shots.append(("top, doors open (from above)", tile(top_open, 85, 180)))
    shots.append(("top, doors shut (low, front)", tile(top_shut, 12, 200)))
    shaft_m = quads(2.0, usual_only, ymax=-1)
    shaft_s = quads(2.0, strategic_only, ymax=-1)
    shots.append(("shaft with a Medium missile", tile(shaft_m, 20, 225)))
    shots.append(("shaft with a Strategic missile", tile(shaft_s, 20, 225)))
    bottom = quads(2.0, usual_only, ymax=-150)
    shots.append(("the launch ring (Medium)", tile(bottom, 40, 225)))
    looking_down = quads(2.0, usual_only)
    shots.append(("looking down the open shaft", tile(looking_down, 80, 200)))
    cols = 4
    cell = 420
    rows = (len(shots) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * cell, rows * (cell + 18)), (28, 30, 34, 255))
    d = ImageDraw.Draw(sheet)
    for i, (label, img) in enumerate(shots):
        x, y = (i % cols) * cell, (i // cols) * (cell + 18)
        sheet.alpha_composite(img, (x, y))
        d.text((x + 6, y + cell + 3), label, fill=(210, 210, 210, 255))
    out = paths.PREVIEWS / "missile_silo_views.png"
    sheet.convert("RGB").save(out)
    print(out)


if __name__ == "__main__":
    main()
