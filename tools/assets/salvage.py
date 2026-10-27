"""What the old sites hold (DESIGN.md §22, Phase 15): the Waste Drum block and the Damaged Warhead's sprite.

- Waste Drum: a 55-gallon steel drum in yellow paint seen side on (a full block, so a stack of them reads as a stack),
  shaded round as a cylinder (lit left of middle, dark at its sides), two rolling hoops standing proud, a radiation
  label (black trefoil on yellow-white), the paint chipped on the hoops and rust run down from them; its lid with the
  rim, two bungs and scuffs. Painted with the material library (`tools/gz/materials.py`).
- Damaged Warhead: a re-entry vehicle's cone, dark ablative shell scorched and split along its side, the trefoil band
  burnt half away, its core glinting through the split.

    tools/.venv/Scripts/python.exe tools/assets/salvage.py
"""
from __future__ import annotations

import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from gz import materials as M  # noqa: E402
from gz import palette as P  # noqa: E402
from gz import paths  # noqa: E402
from gz.model import write_json  # noqa: E402
from gz.sprite import outline  # noqa: E402
from gz.tex import Tex  # noqa: E402

N = 16
DRUM_YELLOW = M.ramp("#4a3606", "#6e510c", "#946d12", "#b8891a", "#d6a426", "#e8bb3e", "#f2cf62", "#f8e08e")
RUST = ((59, 30, 18, 255), (110, 52, 22, 255), (160, 78, 30, 255), (200, 92, 24, 255))
#: The trefoil five pixels square (as the Lead Container's): two blades up, the hub, one blade down.
TREFOIL5 = [(0, 0), (1, 0), (3, 0), (4, 0), (0, 1), (1, 1), (3, 1), (4, 1), (2, 2), (1, 3), (2, 3), (3, 3), (1, 4), (2, 4), (3, 4)]


def drum_side() -> np.ndarray:
    f = M.tile_face()
    mat = M.painted(DRUM_YELLOW, tone=0.62, wear=0.0, rust=0.0, mottle=0.035, grain=0.03)
    shade = np.zeros((N, N), dtype=np.float32)
    xs = (np.arange(N) + 0.5) / N
    # Round: brightest a little left of the middle, falling off to the sides.
    shade += 0.16 * np.cos((xs - 0.42) * np.pi * 1.05)[None, :] - 0.06
    mat.extra = lambda face, s=shade: s
    idx = M.quantize(mat.value(f, 7), DRUM_YELLOW, f, mat.dither, despeckle=True, scatter=False)
    img = M.colours(DRUM_YELLOW, np.clip(idx, 0, len(DRUM_YELLOW) - 1))
    img = np.clip(img, 0, 255).astype(np.uint8)
    # The rolling hoops (two raised bands): lit above, shaded below, their paint chipped through to steel.
    for y in (4, 11):
        img[y - 1, :, :3] = M.colours(DRUM_YELLOW, np.clip(idx[y - 1] + 2, 0, 7))[:, :3]
        img[y, :, :3] = M.colours(DRUM_YELLOW, np.clip(idx[y] + 1, 0, 7))[:, :3]
        img[y + 1, :, :3] = M.colours(DRUM_YELLOW, np.clip(idx[y + 1] - 2, 0, 7))[:, :3]
        for x in (2, 9, 13):
            img[y - 1, x] = (138, 136, 128, 255)
    # The label: a yellow-white square with the black trefoil.
    t = Tex(N, N)
    t.rect(4, 5, 7, 7, (238, 222, 150, 255))
    t.outline(4, 5, 7, 7, (196, 160, 60, 255))
    for dx, dy in TREFOIL5:                                         # two blades up, the hub, one blade down
        t.px(5 + dx, 6 + dy, (24, 22, 20, 255))
    lab = t.a
    mask = lab[..., 3] > 0
    img[mask] = lab[mask]
    # Rust run down from the hoops and the drum's foot, the left and right edges darkest (the drum's sides).
    rng = np.random.default_rng(9)
    for x0 in rng.choice(N, size=5, replace=False):
        y0 = int(rng.choice((5, 12)))
        for k in range(int(rng.integers(1, 4))):
            if y0 + k < N:
                img[y0 + k, x0] = RUST[min(3, k + 1)]
    for x in (0, 15):
        img[:, x, :3] = (img[:, x, :3] * 0.72).astype(np.uint8)
    img[15, :, :3] = (img[15, :, :3] * 0.8).astype(np.uint8)
    return img


def drum_top() -> np.ndarray:
    f = M.tile_face()
    mat = M.painted(DRUM_YELLOW, tone=0.55, wear=0.0, mottle=0.04, grain=0.04)
    idx = M.quantize(mat.value(f, 11), DRUM_YELLOW, f, mat.dither, despeckle=True, scatter=False)
    img = np.clip(M.colours(DRUM_YELLOW, np.clip(idx, 0, 7)), 0, 255).astype(np.uint8)
    yy, xx = np.mgrid[0:N, 0:N] + 0.5
    d = np.hypot(xx - 8, yy - 8)
    rim = (d > 6.2) & (d <= 7.6)
    img[rim] = np.clip(M.colours(DRUM_YELLOW, np.clip(idx[rim] + 1, 0, 7)), 0, 255).astype(np.uint8)
    img[(d > 7.6)] = (img[(d > 7.6)] * np.array([0.7, 0.7, 0.7, 1])).astype(np.uint8)
    for cx, cy, r in ((5.0, 6.0, 1.3), (11.0, 10.0, 0.9)):        # the bungs
        m = np.hypot(xx - cx, yy - cy) <= r
        img[m] = (120, 118, 110, 255)
        img[np.hypot(xx - cx + 0.4, yy - cy + 0.4) <= r * 0.45] = (170, 168, 160, 255)
    for x, y in ((9, 4), (3, 11), (12, 6)):
        img[y, x] = RUST[1]
    return img


def damaged_warhead() -> Tex:
    t = Tex(16, 16)
    shell = P.GUNMETAL
    pts = set()
    # The cone, nose up and to the right, its base lower left.
    for y in range(16):
        for x in range(16):
            u, v = (x + 0.5 - 4) / 9.0, (12 - (y + 0.5)) / 9.0          # along the axis (x - y) / across
            a, c = (u + v) / 1.414, (v - u) / 1.414
            if 0 <= a <= 1.12 and abs(c) <= 0.58 * (1.2 - a) / 1.2 + 0.05:
                pts.add((x, y))
    for (x, y) in pts:
        lit = x + y < 15
        t.px(x, y, shell[4] if lit else shell[2])
    # The ablative shell scorched dark in patches; the trefoil band burnt half away.
    rng = np.random.default_rng(3)
    for (x, y) in pts:
        if rng.random() < 0.25:
            t.px(x, y, shell[1])
    for (x, y) in pts:
        if 13 <= x + y <= 14:
            t.px(x, y, P.HAZARD_YELLOW[4] if (x + rng.integers(0, 3)) % 3 else P.HAZARD_BLACK[1])
    # The split along its side, the core glinting green through it.
    for k, (x, y) in enumerate(((6, 9), (7, 8), (8, 7), (9, 6))):
        if (x, y) in pts:
            t.px(x, y, (30, 34, 30, 255) if k % 2 else (120, 210, 90, 255))
    outline(t, pts, P.HAZARD_BLACK[0])
    return t


def main() -> None:
    M.to_tex(drum_side()).save(paths.TEXTURES / "block" / "waste_drum_side.png")
    M.to_tex(drum_top()).save(paths.TEXTURES / "block" / "waste_drum_top.png")
    write_json(paths.MODELS / "block" / "waste_drum.json", {"parent": "minecraft:block/cube_bottom_top", "textures": {
        "top": "groundzero:block/waste_drum_top", "bottom": "groundzero:block/waste_drum_top", "side": "groundzero:block/waste_drum_side"}})
    write_json(paths.ASSETS / "blockstates" / "waste_drum.json", {"variants": {"": {"model": "groundzero:block/waste_drum"}}})
    write_json(paths.ASSETS / "items" / "waste_drum.json", {"model": {"type": "minecraft:model", "model": "groundzero:block/waste_drum"}})
    damaged_warhead().save(paths.TEXTURES / "item" / "damaged_warhead.png")
    write_json(paths.MODELS / "item" / "damaged_warhead.json", {"parent": "minecraft:item/generated",
                                                                "textures": {"layer0": "groundzero:item/damaged_warhead"}})
    write_json(paths.ASSETS / "items" / "damaged_warhead.json", {"model": {"type": "minecraft:model", "model": "groundzero:item/damaged_warhead"}})
    print("salvage assets written")


if __name__ == "__main__":
    main()
