"""Field Manual photo plates: the formed multiblocks rendered by the preview renderer, from the front-right at 30
degrees, on a transparent background, cropped to the model and fitted to the plate size listed in content.PLATES.
A structure built of ordinary blocks (the Fission Reactor) is drawn block by block from its example pattern.

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


# Structures built of ordinary blocks: the example pattern as GZMultiblocks has it (layers bottom-up, rows front first,
# the front facing north), a block model for each legend character, which of them are full opaque blocks (faces against
# them are hidden), and the box of cells the coolant fills (it is drawn glowing, as while the reactor runs).
REACTOR = [
    ["LXLL", "LLLL", "LLLL", "LLWL"],
    ["LGGL", "GCCG", "GCCG", "LGGL"],
    ["LGGL", "GCCG", "GCCG", "LGGL"],
    ["LLLL", "LUEL", "LIGL", "LLLL"],
]
BLOCK_PLATES = {
    "fission_reactor": (REACTOR, {"L": "lead_lined_casing", "G": "reactor_glass", "W": "fluid_port", "U": "item_port", "I": "item_port",
                                  "E": "energy_port", "C": "fuel_channel_lit", "X": "fission_reactor"}, set("LWUIEX"), ((1, 1, 1), (3, 3, 3), 0.85)),
}
WATER_TINT = np.array([0x3B, 0xA4, 0xF8], dtype=np.float32)


def _water_tex() -> np.ndarray:
    """The vanilla still water tiled 4 x 4 (the renderer clamps UVs), tinted the reactor's glowing blue, see-through."""
    t = R._load_tex("minecraft:block/water_still").astype(np.float32)
    t = np.tile(t, (4, 4, 1))
    t[..., :3] = np.clip(t[..., :3] / 255.0 * WATER_TINT * 1.25, 0, 255)
    t[..., 3] = 130
    return t.astype(np.uint8)


def block_structure_quads(name: str) -> list:
    layers, legend, solid, (lo, hi, fill) = BLOCK_PLATES[name]
    cells = {}
    for y, layer in enumerate(layers):
        for z, row in enumerate(layer):
            for x, key in enumerate(row):
                if key in legend:
                    cells[(x, y, z)] = key
    models = {}
    opaque, glass = [], []
    for (x, y, z), key in cells.items():
        if key not in models:
            models[key] = R.build_quads(R.load_model(f"groundzero:block/{legend[key]}"))
        for q in models[key]:
            n = np.round(q.normal).astype(int)
            axis = int(np.argmax(np.abs(n)))
            edge = 16.0 if n[axis] > 0 else 0.0
            if np.abs(n).sum() == 1 and np.allclose(q.pos[:, axis], edge):
                other = cells.get((x + n[0], y + n[1], z + n[2]))
                if other in solid or (other == "G" and key == "G"):
                    continue                                       # hidden against its neighbour, as in game
            moved = R.Quad(q.pos + np.array([x * 16, y * 16, z * 16], dtype=float), q.uv, q.tex, q.shade, q.emissive, q.normal,
                           q.directional)
            (glass if key == "G" else opaque).append(moved)
    tex = _water_tex()
    f = tuple(c * 16 + 0.05 for c in lo)
    t = (hi[0] * 16 - 0.05, lo[1] * 16 + (hi[1] - lo[1]) * 16 * fill, hi[2] * 16 - 0.05)
    water = []
    for d in ("up", "north", "south", "east", "west"):
        pos = np.array(R._face_corners(d, f, t), dtype=float)
        w = max(np.ptp(pos[:, 0]), np.ptp(pos[:, 2])) if d != "up" else np.ptp(pos[:, 0])
        hgt = np.ptp(pos[:, 1]) if d != "up" else np.ptp(pos[:, 2])
        uv = np.array([(0, 0), (w, 0), (w, hgt), (0, hgt)], dtype=float)
        water.append(R.Quad(pos, uv, tex, 1.0, True, np.array(R.NORMALS[d], dtype=float), False))
    return opaque, water, glass


def plate(name: str, w: int, h: int) -> Path:
    if name in BLOCK_PLATES:
        opaque, water, glass = block_structure_quads(name)
        center, extent = R.model_bounds(opaque + glass)
        rot = R.view_matrix(30, 225)
        glass.sort(key=lambda q: float((rot @ (q.pos.mean(axis=0) - center))[2]))   # far panes first
        return _render(name, opaque + water + glass, w, h, center, extent)
    quads = GP.geo_quads(f"groundzero:block/{name}", hidden=HIDDEN.get(name, ()))
    center, extent = R.model_bounds(quads)
    return _render(name, quads, w, h, center, extent)


def _render(name: str, quads, w: int, h: int, center, extent) -> Path:
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
