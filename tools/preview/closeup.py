"""Close-ups of part of a model: a height band seen from a few angles, large, so details (fins, joints, a nozzle)
can be judged. Several models side by side compare them at one scale.

    tools\\.venv\\Scripts\\python.exe tools\\preview\\closeup.py groundburst:item/missile/he_warhead --y0 -16 --y1 -10
        [--px 40] [--views front,iso,low,back] [--out path]

Heights are the model JSON's (an item model's pixels). Writes tools/out/previews/closeup.png unless --out says.
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))

from gb import paths  # noqa: E402
import render as R  # noqa: E402

VIEWS = {"front": (8, 180), "back": (8, 0), "iso": (25, 210), "iso_back": (25, 30), "low": (-30, 200), "east": (5, -90),
         "top": (60, 200)}


def closeup(models: list[str], y0: float, y1: float, px: float = 40, views=("front", "iso", "low"), out: Path | None = None) -> Path:
    light = np.array([0.4, 0.8, 0.45])
    light /= np.linalg.norm(light)
    cells = []
    for ident in models:
        quads = R.build_quads(R.load_model(ident))
        pts = np.concatenate([q.pos for q in quads])
        lo, hi = pts.min(axis=0), pts.max(axis=0)
        width = float(max(hi[0] - lo[0], hi[2] - lo[2]))
        extent = max(y1 - y0, width) * 1.15
        centre = np.array([(lo[0] + hi[0]) / 2, (y0 + y1) / 2, (lo[2] + hi[2]) / 2])
        size = int(extent * px)
        row = []
        for v in views:
            pitch, yaw = VIEWS[v]
            row.append((f"{ident.split('/')[-1]} {v}", R.render(quads, size, pitch, yaw, centre, extent, light_dir=light)))
        cells.append(row)
    cw = max(img.width for row in cells for _, img in row)
    ch = max(img.height for row in cells for _, img in row)
    sheet = Image.new("RGBA", (cw * len(views), (ch + 16) * len(cells)), (34, 37, 42, 255))
    d = ImageDraw.Draw(sheet)
    for j, row in enumerate(cells):
        for i, (label, img) in enumerate(row):
            sheet.alpha_composite(img, (i * cw, j * (ch + 16)))
            d.text((i * cw + 4, j * (ch + 16) + ch + 2), label, fill=(210, 210, 210, 255))
    path = out or paths.PREVIEWS / "closeup.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    sheet.convert("RGB").save(path)
    return path


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("model", nargs="+")
    ap.add_argument("--y0", type=float, required=True)
    ap.add_argument("--y1", type=float, required=True)
    ap.add_argument("--px", type=float, default=40)
    ap.add_argument("--views", default="front,iso,low")
    ap.add_argument("--out", type=Path)
    a = ap.parse_args()
    print(closeup(a.model, a.y0, a.y1, a.px, tuple(a.views.split(",")), a.out))


if __name__ == "__main__":
    main()
