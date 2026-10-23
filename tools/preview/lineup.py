"""A family of block/item models side by side at one scale (so their sizes compare), from one angle, labelled.

    tools\\.venv\\Scripts\\python.exe tools\\preview\\lineup.py groundzero:block/he_warhead groundzero:block/heavy_warhead ...
        [--out path] [--px 18] [--pitch 25] [--yaw 210] [--cols 7]

--px is screen pixels per model pixel (the same for every model, so a big warhead stands taller than a small one).
Writes tools/out/previews/lineup.png unless --out says otherwise.
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))

from gz import paths  # noqa: E402
import render as R  # noqa: E402


def lineup(models: list[str], out: Path | None = None, px: float = 18, pitch: float = 25, yaw: float = 210, cols: int = 7) -> Path:
    light = np.array([0.4, 0.8, 0.45])
    light /= np.linalg.norm(light)
    shots = []
    for ident in models:
        quads = R.build_quads(R.load_model(ident))
        pts = np.concatenate([q.pos for q in quads])
        lo, hi = pts.min(axis=0), pts.max(axis=0)
        centre = (lo + hi) / 2
        extent = float(np.linalg.norm(hi - lo)) * 1.08
        size = max(64, int(extent * px))
        img = R.render(quads, size, pitch, yaw, centre, extent, light_dir=light)
        shots.append((ident.split("/")[-1], img))
    cell_w = max(img.width for _, img in shots)
    cell_h = max(img.height for _, img in shots)
    rows = (len(shots) + cols - 1) // cols
    sheet = Image.new("RGBA", (min(cols, len(shots)) * cell_w, rows * (cell_h + 16)), (34, 37, 42, 255))
    d = ImageDraw.Draw(sheet)
    for i, (label, img) in enumerate(shots):
        cx, cy = (i % cols) * cell_w, (i // cols) * (cell_h + 16)
        sheet.alpha_composite(img, (cx + (cell_w - img.width) // 2, cy + cell_h - img.height))   # all stand on one line
        d.text((cx + 4, cy + cell_h + 2), label, fill=(210, 210, 210, 255))
    path = out or paths.PREVIEWS / "lineup.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    sheet.convert("RGB").save(path)
    return path


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("model", nargs="+")
    ap.add_argument("--out", type=Path)
    ap.add_argument("--px", type=float, default=18)
    ap.add_argument("--pitch", type=float, default=25)
    ap.add_argument("--yaw", type=float, default=210)
    ap.add_argument("--cols", type=int, default=7)
    a = ap.parse_args()
    print(lineup(a.model, a.out, a.px, a.pitch, a.yaw, a.cols))


if __name__ == "__main__":
    main()
