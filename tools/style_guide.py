"""Builds docs/style/ : palette swatches and the reviewed pilot previews, so the art direction is visible in the repo.

    tools\\.venv\\Scripts\\python.exe tools\\style_guide.py
"""
from __future__ import annotations

import shutil
import sys
from pathlib import Path

from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))
from gz import palette as P  # noqa: E402
from gz import paths  # noqa: E402

OUT = paths.ROOT / "docs" / "style"


def palettes() -> Path:
    sw, label_w, pad = 28, 150, 6
    ramps = list(P.ALL.values())
    img = Image.new("RGB", (label_w + 7 * sw + pad * 2, len(ramps) * (sw + 4) + pad * 2), (24, 26, 30))
    d = ImageDraw.Draw(img)
    for i, r in enumerate(ramps):
        y = pad + i * (sw + 4)
        d.text((pad, y + 8), r.name, fill=(220, 220, 220))
        for j, c in enumerate(r.shades):
            d.rectangle([label_w + j * sw, y, label_w + (j + 1) * sw - 2, y + sw - 1], fill=c[:3])
    OUT.mkdir(parents=True, exist_ok=True)
    out = OUT / "palettes.png"
    img.save(out)
    return out


def copy_previews(names: list[str]) -> list[Path]:
    out = []
    for n in names:
        src = paths.PREVIEWS / n
        if src.exists():
            dst = OUT / n
            shutil.copyfile(src, dst)
            out.append(dst)
    return out


if __name__ == "__main__":
    print(palettes())
    for p in copy_previews(["groundzero_block_uranium_ore.png", "groundzero_block_deepslate_uranium_ore.png",
                            "groundzero_block_fission_bomb.png", "groundzero_block_ore_crusher_working.png",
                            "sound_review.png"]):
        print(p)
