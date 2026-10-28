"""Block textures side by side, each alone and tiled 3x3 (to see that it joins its neighbours without a seam and does
not repeat too plainly), at a readable size. Writes tools/out/previews/tile_sheet.png.

    tools/.venv/Scripts/python.exe tools/preview/tile_sheet.py launch_pad reinforced_concrete hazard_concrete
"""
from __future__ import annotations

import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from gb import paths  # noqa: E402

SCALE = 6


def main() -> None:
    names = sys.argv[1:]
    cells = []
    for name in names:
        img = np.array(Image.open(paths.TEXTURES / "block" / f"{name}.png").convert("RGBA"))
        tiled = np.tile(img, (3, 3, 1))
        check = np.zeros_like(tiled)
        yy, xx = np.mgrid[0:tiled.shape[0], 0:tiled.shape[1]]
        check[..., :3] = np.where((((xx // 4) + (yy // 4)) % 2 == 0)[..., None], 70, 86)
        check[..., 3] = 255
        a = tiled[..., 3:4].astype(np.float32) / 255
        comp = (tiled[..., :3] * a + check[..., :3] * (1 - a)).astype(np.uint8)
        cells.append((name, Image.fromarray(comp).resize((comp.shape[1] * SCALE, comp.shape[0] * SCALE), Image.NEAREST)))
    w = max(c.width for _, c in cells)
    h = max(c.height for _, c in cells)
    cols = min(4, len(cells))
    rows = (len(cells) + cols - 1) // cols
    sheet = Image.new("RGB", (cols * (w + 10), rows * (h + 22)), (28, 30, 34))
    d = ImageDraw.Draw(sheet)
    for i, (name, c) in enumerate(cells):
        x, y = (i % cols) * (w + 10), (i // cols) * (h + 22)
        sheet.paste(c, (x, y))
        d.text((x + 4, y + h + 4), name, fill=(210, 210, 210))
    out = paths.PREVIEWS / "tile_sheet.png"
    sheet.save(out)
    print(out)


if __name__ == "__main__":
    main()
