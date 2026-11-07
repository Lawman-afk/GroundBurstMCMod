"""The Pressure Door (DESIGN.md §22.3): a steel door, a block wide and two high, as the doors between a bunker's rooms
and a ship's compartments: a round porthole at eye height, a dog lever on either face, heavy hinges, hazard paint at its
foot. It opens and shuts as a door does.

Built as vanilla's door models are, facing east: the leaf across the block's west side (x 0..3, z 0..16), its outside
to the west. "left" has its hinges at the north end (z 0), "right" at the south; open, vanilla turns the leaf a quarter
about the block's middle (y 90 for left, 270 for right), which leaves its hinges at the far end, so an open left door
is drawn with the right door's model and an open right door with the left's (the blockstate maps them). Each half is its
own model, cut from the whole door built and painted as one (the top's elements written 16 lower), both in its texture.

    tools\\.venv\\Scripts\\python.exe tools\\assets\\pressure_door.py
"""
from __future__ import annotations

import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))

from gb import machine_kit as K  # noqa: E402
from gb import materials as M  # noqa: E402
from gb import paths  # noqa: E402
from gb.model import BlockModel, write_json  # noqa: E402
from gb.tex import Tex  # noqa: E402
import blast_door as BD  # noqa: E402
import warheads as W  # noqa: E402

NAME = "pressure_door"
T = 3.0                                  # the leaf's thickness (vanilla's)
#: The porthole: its middle (z, y), the hole's half width (an octagon), the rim's radii.
PORT = (8.0, 24.5)
HOLE = 3.2
RIM = (3.0, 4.0)
#: The dog lever's pivot (z, y) at the latch end.
DOG = (12.6, 15.0)


def zs(hinge_far: bool, a: float, b: float) -> tuple[float, float]:
    """z from a to b with the hinges at the north end, mirrored for the hinges at the south (in order)."""
    return tuple(sorted((16 - b, 16 - a))) if hinge_far else (a, b)


def build(hinge_far: bool) -> BlockModel:
    """The whole door, both halves in one model so it is painted as one (no lip where the halves meet), split into its
    two block models as it is written; both share its texture."""
    side = "right" if hinge_far else "left"
    m = BlockModel(f"{NAME}_{side}", tex_size=(128, 64), kind="block", render_type="minecraft:translucent")
    m.open_ok = set()
    parts: dict[str, list[str]] = {}

    def box(group: str, n: str, frm, to, faces=("north", "south", "east", "west", "up", "down"), rotation=None):
        m.box(n, frm, to, faces=faces, rotation=rotation)
        parts.setdefault(group, []).append(n)

    # --- the bottom half: the leaf, the dog lever on each face (its boss, the lever hanging down from it) -------------
    box("leaf", "leaf", (0, 0, 0), (T, 16, 16), faces=("east", "west", "north", "south", "down"))
    for face, (a, b, c) in (("out", (-0.8, -1.5, 0.0)), ("in", (T + 0.8, T + 1.5, T))):
        z0, z1 = zs(hinge_far, DOG[0] - 0.75, DOG[0] + 0.75)
        lo, hi = sorted((c, a))
        box("boss", f"boss_{face}", (lo, DOG[1] - 0.75, z0), (hi, DOG[1] + 0.75, z1))
        lo2, hi2 = sorted((a, b))
        lz0, lz1 = zs(hinge_far, DOG[0] - 0.35, DOG[0] + 0.35)
        box("lever", f"lever_{face}", (lo2, DOG[1] - 4.2, lz0), (hi2, DOG[1] + 0.6, lz1))
        box("lever", f"grip_{face}", (lo2 - 0.3 if face == "out" else lo2 + 0.1, DOG[1] - 4.8, lz0 - 0.15),
            (hi2 - 0.1 if face == "out" else hi2 + 0.3, DOG[1] - 3.6, lz1 + 0.15))
    # --- the top half: the leaf round the porthole's hole, the hole's corners filled, the glass, the rims ---------------
    y0, y1 = 16.0, 32.0
    cz, cy = PORT
    hz0, hz1 = zs(hinge_far, cz - HOLE, cz + HOLE)
    box("leaf", "leaf_low", (0, y0, 0), (T, cy - HOLE, 16), faces=("east", "west", "north", "south", "up"))
    box("leaf", "leaf_high", (0, cy + HOLE, 0), (T, y1, 16), faces=("east", "west", "north", "south", "up", "down"))
    box("leaf", "leaf_a", (0, cy - HOLE, 0), (T, cy + HOLE, hz0), faces=("east", "west", "north", "south"))
    box("leaf", "leaf_b", (0, cy - HOLE, hz1), (T, cy + HOLE, 16), faces=("east", "west", "north", "south"))
    pz = zs(hinge_far, cz, cz)[0]
    f = (HOLE * math.sqrt(2) - HOLE) * math.sqrt(2)
    for k, (z, y) in enumerate(((hz0, cy - HOLE), (hz1, cy - HOLE), (hz0, cy + HOLE), (hz1, cy + HOLE))):
        box("leaf", f"fillet{k}", (0.1, y - f / 2, z - f / 2), (T - 0.1, y + f / 2, z + f / 2), faces=("north", "south", "up", "down", "east", "west"),
            rotation={"origin": (0.1, y, z), "axis": "x", "angle": 45.0})
    # The glass: one pane across the hole (planks overlapping would stack their tint), its corners in the fillets.
    box("glass", "glass", (T / 2 - 0.25, cy - HOLE, hz0), (T / 2 + 0.25, cy + HOLE, hz1), faces=("east", "west"))
    # The porthole's rims, each reaching 0.3 into the leaf: their backs never lie in the leaf's faces' planes (open, the
    # outer face lies against the wall, and the rim's back there flickered with the wall through the glass).
    parts["rim"] = (W.round_ring(m, "rim_out", RIM[0], RIM[1], -0.6, 0.3, axis="x", c=(cy, pz))
                    + W.round_ring(m, "rim_in", RIM[0], RIM[1], T - 0.3, T + 0.6, axis="x", c=(cy, pz)))
    # --- the hinges: two heavy knuckles proud of both faces at the hinge end ------------------------------------------------
    for j, (k0, k1) in enumerate(((2.0, 6.0), (26.0, 30.0))):
        z0, z1 = zs(hinge_far, 0.3, 1.8)
        box("knuckle", f"knuckle{j}", (-0.5, k0, z0), (T + 0.5, k1, z1))
    m.layout()
    t = paint(m, parts, hinge_far, 3201 + hinge_far)
    m.save(t)
    split(m.name, side)
    return m


def split(name: str, side: str) -> None:
    """The whole door's model written as its two halves (the top's elements 16 lower), in the whole's texture."""
    path = paths.MODELS / "block" / f"{name}.json"
    whole = json.loads(path.read_text(encoding="utf-8"))
    for half in ("bottom", "top"):
        out = {k: v for k, v in whole.items() if k != "elements"}
        els = []
        for el in whole["elements"]:
            top = el["from"][1] >= 16 - 1e-6
            if top != (half == "top"):
                continue
            el = json.loads(json.dumps(el))
            if top:
                el["from"][1] -= 16
                el["to"][1] -= 16
                if "rotation" in el and "origin" in el["rotation"]:
                    el["rotation"]["origin"][1] -= 16
            els.append(el)
        out["elements"] = els
        write_json(paths.MODELS / "block" / f"{NAME}_{half}_{side}.json", out)
    path.unlink()


GLASS = np.array([70, 98, 108], np.float32)


def edge_rivets(f: M.Face, img: np.ndarray) -> None:
    """Rivet heads a pixel in from the leaf's outer edges, every three pixels (lit, a shadow under each)."""
    if f.d not in ("east", "west"):
        return
    yi, zi = np.floor(f.Y).astype(int), np.floor(f.Z).astype(int)
    head = (((zi == 1) | (zi == 14)) & (yi % 3 == 1) & (yi >= 1) & (yi <= 30)) | (((yi == 1) | (yi == 30)) & (zi % 3 == 1) & (zi >= 1) & (zi <= 14))
    rgb = img[..., :3].astype(np.float32)
    rgb[head] = np.minimum(255, rgb[head] * 1.35 + 14)
    shadow = np.zeros_like(head)
    shadow[1:, :] = head[:-1, :]
    rgb[shadow & ~head] *= 0.7
    img[..., :3] = rgb.astype(np.uint8)


def paint(m: BlockModel, parts: dict, hinge_far: bool, seed: int) -> Tex:
    t = Tex(m.tex_w, m.tex_h)
    k = K.Kit(m, {}, seed=seed, ao_reach=1.4, turned=True)
    k.paint(t, parts["leaf"], BD.DOOR)
    # The kick plate's stripes low on both faces, rivets round the edges.
    k.overlay(t, parts["leaf"], lambda f, img: M.paste(img, k.S.material_on(f, M.stripes(width=2, wear=0.4)), (f.Y > 0.9) & (f.Y < 4.0))
              if f.d in ("east", "west") else None)
    k.overlay(t, parts["leaf"], edge_rivets)
    k.paint(t, parts["knuckle"], BD.STRAP)
    k.paint(t, parts["boss"], BD.CAM)
    k.paint(t, parts["lever"], BD.WHEEL_RED)
    k.paint(t, parts["rim"], BD.CAM)

    def glass(f: M.Face, img: np.ndarray) -> None:
        """Thick tinted glass: see-through, a glint across its upper side."""
        rgb = np.broadcast_to(GLASS, img.shape[:2] + (3,)).copy()
        glint = (f.Y - PORT[1]) + 0.6 * np.abs(f.Z - PORT[0]) > 1.4
        rgb[glint] = rgb[glint] * 1.5 + 30
        img[..., :3] = np.clip(rgb, 0, 255).astype(np.uint8)
        img[..., 3] = np.where(glint, 140, 90).astype(np.uint8)
    k.overlay(t, parts["glass"], glass)
    return t


ICON = [
    "................",
    ".....kkkkkkk....",
    ".....kGGGGGk....",
    "....skGrrrGk....",
    "....Skrbbbrk....",
    "....skrbbbrk....",
    ".....kGrrrGk....",
    ".....kGGGGGk....",
    "....skGGGGGk....",
    "....SkGGGRRk....",
    "....skGGGRGk....",
    ".....kGGGRGk....",
    ".....kYyYyYk....",
    ".....kyYyYyk....",
    ".....kkkkkkk....",
    "................",
]


def icon() -> None:
    """The item: the door from the front, its porthole, its red lever, its hinges on the left, the kick plate."""
    col = {"k": (44, 48, 46), "G": (110, 122, 115), "r": (70, 74, 72), "b": (92, 128, 140), "s": (70, 74, 72),
           "S": (110, 114, 112), "R": (176, 50, 40), "Y": (218, 172, 40), "y": (36, 36, 38)}
    img = np.zeros((16, 16, 4), np.uint8)
    assert all(len(row) == 16 for row in ICON) and len(ICON) == 16
    for y, row in enumerate(ICON):
        for x, c in enumerate(row):
            if c != ".":
                img[y, x] = (*col[c], 255)
    Image.fromarray(img, "RGBA").save(paths.TEXTURES / "item" / f"{NAME}.png")
    write_json(paths.MODELS / "item" / f"{NAME}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"groundburst:item/{NAME}"}})
    write_json(paths.ASSETS / "items" / f"{NAME}.json", {"model": {"type": "minecraft:model", "model": f"groundburst:item/{NAME}"}})


def blockstate() -> None:
    """Vanilla's door blockstate, its models ours: an open door drawn with the other hand's model (see the notes above)."""
    turn = {"east": 0, "south": 90, "west": 180, "north": 270}
    variants = {}
    for facing, r in turn.items():
        for half, h in (("lower", "bottom"), ("upper", "top")):
            for hinge in ("left", "right"):
                for open_ in (False, True):
                    model = hinge if not open_ else ("right" if hinge == "left" else "left")
                    y = (r + (0 if not open_ else (90 if hinge == "left" else 270))) % 360
                    v = {"model": f"groundburst:block/{NAME}_{h}_{model}"}
                    if y:
                        v["y"] = y
                    variants[f"facing={facing},half={half},hinge={hinge},open={'true' if open_ else 'false'}"] = v
    write_json(paths.ASSETS / "blockstates" / f"{NAME}.json", {"variants": variants})


def main() -> None:
    for far in (False, True):
        m = build(far)
        lo, hi = m.bounds()
        print(m.name, "bounds", [round(v, 2) for v in lo], [round(v, 2) for v in hi], "tex", m.tex_w, m.tex_h)
    icon()
    blockstate()


if __name__ == "__main__":
    main()
