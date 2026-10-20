"""Field Manual photo plates: the formed multiblocks rendered by the preview renderer, from the front-right at 30
degrees, on a transparent background, cropped to the model and fitted to the plate size listed in content.PLATES.

Writes textures/gui/manual/plates/<name>.png. Run after the models change:
    tools\\.venv\\Scripts\\python.exe tools\\manual\\plates.py
"""
from __future__ import annotations

import sys
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "preview"))
sys.path.insert(0, str(Path(__file__).resolve().parent))

from gz import paths  # noqa: E402
import geo_preview as GP  # noqa: E402
import render as R  # noqa: E402
import content  # noqa: E402


# Bones a plate leaves out, as the game would for the pictured state (the singularity: no extra item hatches, the
# largest accretion disk).
HIDDEN = {"singularity_reactor": ("hatch_", "disk0", "disk1")}


def plate(name: str, w: int, h: int) -> Path:
    quads = GP.geo_quads(f"groundzero:block/{name}", hidden=HIDDEN.get(name, ()))
    center, extent = R.model_bounds(quads)
    light = np.array([0.4, 0.8, 0.45])
    light /= np.linalg.norm(light)
    size = 3 * max(w, h)
    img = R.render(quads, size, 30, 225, center, extent, light_dir=light)
    box = img.getbbox()
    img = img.crop(box)
    scale = min(w / img.width, h / img.height)
    img = img.resize((max(1, round(img.width * scale)), max(1, round(img.height * scale))), Image.LANCZOS)
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    out.alpha_composite(img, ((w - img.width) // 2, (h - img.height) // 2))
    path = paths.TEXTURES / "gui" / "manual" / "plates" / f"{name}.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    out.save(path)
    return path


if __name__ == "__main__":
    for name, (w, h) in content.PLATES.items():
        print(plate(name, w, h))
