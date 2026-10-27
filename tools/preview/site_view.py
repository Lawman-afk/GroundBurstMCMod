"""Previews a structure template from tools/structure/sites.py (DESIGN.md §22): isometric views coloured from each
block's own texture (vanilla's from the decompiled client, ours from the mod), whole and cut away (above a height, or
along a plane), so a site's rooms can be read without starting the game.

    tools/.venv/Scripts/python.exe tools/preview/site_view.py silo_below_intact [--cut-y 20] [--cut-z 10] [--scale 6]

Writes tools/out/previews/site_<name>[_cut].png.
"""
from __future__ import annotations

import argparse
import sys
from functools import lru_cache
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools"))
sys.path.insert(0, str(ROOT / "tools" / "structure"))
from gz import paths  # noqa: E402

VANILLA = paths.VANILLA_ASSETS / "textures" / "block"
OURS = paths.TEXTURES / "block"
FIXED = {
    "water": (52, 92, 190), "air": None, "cobweb": (220, 220, 220), "structure_block": (255, 0, 255), "chest": (160, 110, 40),
    "barrel": (130, 95, 55), "lava": (230, 110, 20), "short_grass": (96, 140, 60), "tall_grass": (96, 140, 60),
    "moss_carpet": (90, 120, 50), "iron_door": (200, 200, 200), "ladder": (150, 115, 70), "lantern": (90, 80, 60),
    "iron_chain": (70, 75, 85), "iron_bars": (110, 112, 115), "lever": (110, 100, 90), "redstone_wire": (170, 20, 20),
}
STRIP = ("_stairs", "_slab", "_wall", "_fence_gate", "_fence", "_pressure_plate", "_button", "_trapdoor", "_door", "_carpet",
         "_pane", "_bed")


@lru_cache(maxsize=None)
def colour(name: str):
    ns, _, n = name.partition(":")
    if n in FIXED:
        return FIXED[n]
    folder = OURS if ns == "groundzero" else VANILLA
    candidates = [n, n + "_side", n + "_top", n + "_front", n.replace("bricks", "brick")]
    base = n
    for s in STRIP:
        if base.endswith(s):
            base = base[: -len(s)]
            candidates += [base, base + "s", base + "_planks", base + "_block", base.replace("brick", "bricks"), base + "_top",
                           base.replace("_glass", "_stained_glass")]
            break
    if base.endswith("_bed"):
        candidates.append(base.replace("_bed", "_wool"))
    candidates += [n.replace("_wall_", "_"), n + "_off", n + "_0", n + "_still", n + "_planks"]
    for c in candidates:
        p = folder / f"{c}.png"
        if p.exists():
            a = np.array(Image.open(p).convert("RGBA"))[:16, :16].reshape(-1, 4).astype(np.float32)
            keep = a[a[:, 3] > 20]
            if len(keep):
                return tuple(int(v) for v in keep[:, :3].mean(axis=0))
    return (150, 140, 160) if ns == "groundzero" else (128, 128, 128)


def render(blocks: dict, size: tuple, scale: int = 6, cut_y: int | None = None, cut_z: int | None = None) -> Image.Image:
    sx, sy, sz = size
    kept = {p: n for p, n in blocks.items() if n not in ("minecraft:air", "minecraft:structure_void")
            and (cut_y is None or p[1] <= cut_y) and (cut_z is None or p[2] <= cut_z)}
    occupied = {p for p, n in kept.items() if n != "minecraft:water"}
    w, h = scale, scale
    cos, sin = 0.866 * w, 0.5 * w
    W = int((sx + sz) * cos) + 40
    H = int((sx + sz) * sin + sy * h) + 40
    img = Image.new("RGB", (W, H), (34, 36, 40))
    d = ImageDraw.Draw(img, "RGBA")
    ox, oy = sz * cos + 20, sy * h + 20

    def pt(x, y, z):
        return (ox + (x - z) * cos, oy + (x + z) * sin - y * h)

    for (x, y, z) in sorted(kept, key=lambda p: (p[0] + p[1] + p[2], p[1])):
        name = kept[(x, y, z)]
        c = colour(name)
        if c is None:
            continue
        wet = name == "minecraft:water"
        alpha = 110 if wet else 255
        faces = []
        if (x, y + 1, z) not in occupied and not (wet and kept.get((x, y + 1, z)) == "minecraft:water"):
            faces.append(([pt(x, y + 1, z), pt(x + 1, y + 1, z), pt(x + 1, y + 1, z + 1), pt(x, y + 1, z + 1)], 1.0))
        if (x, y, z + 1) not in occupied and not wet:
            faces.append(([pt(x, y, z + 1), pt(x + 1, y, z + 1), pt(x + 1, y + 1, z + 1), pt(x, y + 1, z + 1)], 0.78))
        if (x + 1, y, z) not in occupied and not wet:
            faces.append(([pt(x + 1, y, z), pt(x + 1, y, z + 1), pt(x + 1, y + 1, z + 1), pt(x + 1, y + 1, z)], 0.62))
        for poly, k in faces:
            d.polygon(poly, fill=(int(c[0] * k), int(c[1] * k), int(c[2] * k), alpha))
    return img


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("names", nargs="+")
    ap.add_argument("--cut-y", type=int)
    ap.add_argument("--cut-z", type=int)
    ap.add_argument("--scale", type=int, default=6)
    args = ap.parse_args()
    import sites  # noqa: E402
    for name in args.names:
        b = sites.TEMPLATES[name]()
        blocks = {pos: n for pos, n, _ in b.t.blocks()}
        img = render(blocks, b.size, args.scale, args.cut_y, args.cut_z)
        tag = "" if args.cut_y is None and args.cut_z is None else f"_cut{args.cut_y if args.cut_y is not None else ''}" \
                                                                  f"{'z' + str(args.cut_z) if args.cut_z is not None else ''}"
        out = paths.PREVIEWS / f"site_{name}{tag}.png"
        out.parent.mkdir(parents=True, exist_ok=True)
        img.save(out)
        print(out, img.size)


if __name__ == "__main__":
    main()
