"""The Large Silo Door and its rusted twin (DESIGN.md §22.2): the closure door of a missile silo, after the Titan II's
and the Minuteman's (a slab of steel and concrete rolling aside on rails, its leading edge bevelled), made a pair of
leaves for a 7x7 opening, each sliding three blocks and a half onto its own rails.

Facing north (the leaves slide along x): the opening x -56..56, z -56..56 (model pixels, the middle block's bottom middle
at the origin); a concrete coaming along its north and south sides, hazard-banded on top; two rails each way on concrete
beds out to the door's ends, with a toothed rack between them; a winch house at each end. Each leaf: a steel box seven
pixels deep on bogies over the rails, its top concrete inside a steel rim, a band of hazard stripes along the edge where
the two meet. Drawn by the middle block's block entity (GeckoLib); the leaves are bones the block entity slides apart.

    tools/.venv/Scripts/python.exe tools/assets/silo_door.py
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import numpy as np  # noqa: E402
from PIL import Image  # noqa: E402

from gb import materials as M  # noqa: E402
from gb import paths  # noqa: E402
from gb.geo import Animation, GeoModel, save_animations  # noqa: E402
from gb.model import write_json  # noqa: E402
from gb.tex import Tex  # noqa: E402

HALF = 56                                # half the opening (3.5 blocks)
END = 120                                # the door's ends (7.5 blocks)
RAIL_Z = 40                              # the rails' middles either side of the axis
TOP = (("north", "south", "east", "west", "up"))


def build(rusted: bool) -> GeoModel:
    name = "rusted_large_silo_door" if rusted else "large_silo_door"
    g = GeoModel(name, tex_size=(512, 512))
    g.bone("frame")
    g.bone("leaf_w", pivot=(-HALF / 2, 0, 0), parent="frame")
    g.bone("leaf_e", pivot=(HALF / 2, 0, 0), parent="frame")
    F = "frame"
    concrete, steel, rack, stripes_on = [], [], [], []
    # The coaming along the opening's north and south sides.
    for n, z0, z1 in (("coaming_n", -HALF - 4, -HALF), ("coaming_s", HALF, HALF + 4)):
        g.box(n, (-HALF - 4, 0, z0), (HALF + 4, 6, z1), faces=TOP, bone=F)
        concrete.append(n)
        stripes_on.append(n)
    # The rails out to the ends: a concrete bed, the rail on it, a toothed rack between the two.
    for side, (a, b) in (("w", (-END, -HALF)), ("e", (HALF, END))):
        for k, z in enumerate((-RAIL_Z, RAIL_Z)):
            g.box(f"bed_{side}{k}", (a, 0, z - 6), (b, 1.5, z + 6), faces=TOP, bone=F)
            concrete.append(f"bed_{side}{k}")
            g.box(f"rail_{side}{k}", (a, 1.5, z - 2), (b, 3.5, z + 2), faces=TOP, bone=F)
            steel.append(f"rail_{side}{k}")
        ra, rb = (a + 8, b) if side == "w" else (a, b - 8)       # the rack runs up to the winch house, not into it
        g.box(f"rack_{side}", (ra, 0, -1.5), (rb, 2.5, 1.5), faces=("north", "south", "up"), bone=F)
        rack.append(f"rack_{side}")
        # The winch house at the end, low and squat.
        x0, x1 = (a, a + 8) if side == "w" else (b - 8, b)
        g.box(f"winch_{side}", (x0, 0, -20), (x1, 10, 20), faces=TOP, bone=F)
        steel.append(f"winch_{side}")
    # The leaves: a steel box on two bogies, its top concrete inside a steel rim.
    leaf_boxes, leaf_tops = [], []
    for side, (x0, x1) in (("w", (-HALF, 0)), ("e", (0, HALF))):
        bone = f"leaf_{side}"
        g.box(f"body_{side}", (x0, 2.5, -HALF + 1), (x1, 8.5, HALF - 1), faces=TOP, bone=bone)
        g.box(f"top_{side}", (x0 + 2, 8.5, -HALF + 3), (x1 - 2, 9.5, HALF - 3), faces=TOP, bone=bone)
        for k, z in enumerate((-RAIL_Z, RAIL_Z)):
            g.box(f"bogie_{side}{k}", (x0 + 6, 0.5, z - 5), (x1 - 6, 2.5, z + 5), faces=("north", "south", "east", "west"), bone=bone)
            leaf_boxes.append(f"bogie_{side}{k}")
        leaf_boxes.append(f"body_{side}")
        leaf_tops.append(f"top_{side}")
    g.layout()
    tex = Tex(g.tex_w, g.tex_h)
    S = M.Surfacer(g, seed=307)
    rust = 0.55 if rusted else 0.04
    S.paint(tex, concrete, M.concrete(tone=0.58, streaks=0.16 if rusted else 0.08))
    S.paint(tex, steel, M.steel(tone=0.45, rust=rust))
    S.paint(tex, rack, M.steel(tone=0.35, rust=rust))
    S.paint(tex, leaf_boxes, M.painted(M.OLIVE_RAMP, tone=0.5, wear=0.45 if rusted else 0.15, rust=rust))
    S.paint(tex, leaf_tops, M.concrete(tone=0.62, streaks=0.0, grime=0.2 if rusted else 0.08))
    band = M.stripes(width=3, wear=0.55 if rusted else 0.2)
    # The hazard band along the edge where the leaves meet, and along the coaming's top.
    S.overlay(tex, leaf_tops, lambda f, img: M.paste(img, S.material_on(f, band), np.abs(f.X) < 9), only=("up",))
    S.overlay(tex, stripes_on, lambda f, img: M.paste(img, S.material_on(f, band), np.ones_like(f.X, bool)), only=("up",))
    if rusted:
        S.overlay(tex, leaf_tops, lambda f, img: M.sooted(f, img, M.noise(f, 6.0, 311, 2) * 0.8, strength=0.35), only=("up",))
    g.save(tex)
    return g


def icon(rusted: bool) -> None:
    """The item's picture: the two leaves shut, seen from above at a slant, the hazard band down the middle."""
    img = np.zeros((16, 16, 4), np.uint8)
    olive = np.array([88, 98, 60, 255], np.uint8) if not rusted else np.array([112, 74, 48, 255], np.uint8)
    dark = np.array([52, 58, 38, 255], np.uint8) if not rusted else np.array([70, 44, 30, 255], np.uint8)
    grey = np.array([150, 150, 146, 255], np.uint8)
    rail = np.array([64, 66, 70, 255], np.uint8)
    yellow, black = np.array([224, 178, 40, 255], np.uint8), np.array([28, 28, 30, 255], np.uint8)
    img[4:12, 0:16] = rail * 0 + np.array([96, 96, 92, 255], np.uint8)          # the concrete beds
    img[5, :] = rail
    img[10, :] = rail
    img[3:13, 3:13] = olive
    img[4:12, 4:12] = grey
    img[12, 3:13] = dark
    for y in range(3, 13):
        img[y, 7] = yellow if y % 2 else black
        img[y, 8] = black if y % 2 else yellow
    name = "rusted_large_silo_door" if rusted else "large_silo_door"
    Image.fromarray(img, "RGBA").save(paths.TEXTURES / "item" / f"{name}.png")
    write_json(paths.MODELS / "item" / f"{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"groundburst:item/{name}"}})
    write_json(paths.ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": f"groundburst:item/{name}"}})


def block_files(name: str) -> None:
    """Every block of the door draws nothing of its own (the middle's block entity draws it all): one model, a particle."""
    write_json(paths.MODELS / "block" / f"{name}.json", {"textures": {"particle": "groundburst:block/steel_panel"}})
    write_json(paths.ASSETS / "blockstates" / f"{name}.json", {"multipart": [{"apply": {"model": f"groundburst:block/{name}"}}]})


def main() -> None:
    for rusted in (False, True):
        g = build(rusted)
        save_animations(g.name, [Animation("idle", 0.05, loop=False)])
        icon(rusted)
        block_files(g.name)
    print("silo doors written")


if __name__ == "__main__":
    main()
