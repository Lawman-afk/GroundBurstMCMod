"""The Bunker Hatch (DESIGN.md §22.3): a round steel hatch in a square steel coaming, a handwheel on its lid and the lid
on two hinges, as the hatches of shelters, silos and submarines have them; it opens and shuts as a trapdoor does.

Its block models, as the hatch lies at the top of its block (a floor hatch, flush with the floor), facing north, its
hinges at the south edge (vanilla's trapdoor facing north opens against its south side):
- "bunker_hatch_frame": the coaming round an octagonal opening (hazard paint on its top), the hinges' frame knuckles and
  their pins, the clamp the lid shuts under.
- "bunker_hatch_lid": the round lid (planks every 22.5 degrees), its boss and handwheel, its hinge straps and knuckles.
- "bunker_hatch_lid_open": the same lid moved so that the blockstate's x 270 turn (about the block's middle) leaves it
  where swinging it up 90 degrees on its hinge pins would: standing on the south edge, its wheel to the south, out of
  the floor into the block above. The blockstate (multipart) puts the frame and one lid together for each facing and
  half; the bottom half (a ceiling hatch, its wheel down) is the top half turned over, so its lid opens down.
- "bunker_hatch" (item): frame and lid in one.

    tools\\.venv\\Scripts\\python.exe tools\\assets\\bunker_hatch.py
"""
from __future__ import annotations

import copy
import json
import math
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))

from gb import machine_kit as K  # noqa: E402
from gb import materials as M  # noqa: E402
from gb import paths  # noqa: E402
from gb.model import BlockModel, make_polygon, write_json  # noqa: E402
from gb.tex import Tex  # noqa: E402
import blast_door as BD  # noqa: E402
import warheads as W  # noqa: E402

NAME = "bunker_hatch"
SIDES = ("north", "south", "east", "west")

#: The hinge pins' axis (along x): its height and its z, at the south edge.
PIN_Y, PIN_Z = 16.5, 15.0
#: The coaming (its height), the opening's half width, the lid (its apothem, its height).
COAMING = (13.5, 15.5)
OPENING = 6.0
LID_R, LID = 6.8, (15.5, 16.5)
#: The handwheel on the lid: its rim (out, in), its height.
WHEEL_R = (4.6, 3.8)
WHEEL = (17.3, 17.9)
#: The dogs' hub under the lid's middle (its radius).
UNDER_HUB = 2.4
#: The lid's knuckles (x from, to), between the frame's on one pin.
LID_KNUCKLES = ((5.2, 7.0), (9.0, 10.8))
FRAME_KNUCKLES = ((2.6, 4.9), (11.1, 13.4))
#: Where the open lid's model is moved to, so the blockstate's x 270 turn about the block's middle stands it on its hinge.
OPEN_SHIFT = (0.0, -1.5, -15.5)


def frame(m: BlockModel, parts: dict) -> None:
    """The coaming: four plates round the opening, a fillet across each inner corner (an octagonal opening the round lid
    covers), its outer faces at the block's sides culled against its neighbours; the frame's knuckles, the pins and a
    clamp on the north."""
    y0, y1 = COAMING
    a, b = 8 - OPENING, 8 + OPENING
    # Only its outer faces, on the block's sides, are culled against the neighbours (its faces into the opening were
    # too, and vanished in a floor: the hatch's striped bars showed hollow).
    m.box("coam_n", (0, y0, 0), (16, y1, a), faces=("north", "south", "up", "down", "east", "west"),
          cull={"north": "north", "east": "east", "west": "west"})
    m.box("coam_s", (0, y0, b), (16, y1, 16), faces=("north", "south", "up", "down", "east", "west"),
          cull={"south": "south", "east": "east", "west": "west"})
    m.box("coam_w", (0, y0, a), (a, y1, b), faces=("west", "east", "up", "down"), cull={"west": "west"})
    m.box("coam_e", (b, y0, a), (16, y1, b), faces=("west", "east", "up", "down"), cull={"east": "east"})
    parts["coaming"] = ["coam_n", "coam_s", "coam_w", "coam_e"]
    # Fillets: each inner corner filled to a regular octagon's side, 6 px from the middle like the plates' edges, so the
    # opening is a true octagon (its corners 6.49 px out) the lid (6.8) covers all round. Each a triangle on top and under
    # (polygons, level with the plates: no face in their planes but its own) and its diagonal side, a face turned 45
    # degrees. (A square turned 45 degrees on each corner, as before, left the opening's corners 6.95 px out: past the
    # lid, the sky showed through them from below; the user saw it.)
    h = 2 * OPENING - OPENING * math.sqrt(2)                 # how far each triangle runs along the plates' edges
    L = h * math.sqrt(2)                                      # its diagonal side's length
    q = OPENING / math.sqrt(2)                                # the diagonal side's middle, from the opening's middle
    for k, (cx, cz, sx, sz) in enumerate(((a, a, 1, 1), (b, a, -1, 1), (a, b, 1, -1), (b, b, -1, -1))):
        tri = [(cx, 0.0, cz), (cx + sx * h, 0.0, cz), (cx, 0.0, cz + sz * h)]
        for d, y in (("up", y1), ("down", y0)):
            n = make_polygon(m, f"fillet{k}_{d}", [[(p[0], y, p[2]) for p in tri]], d).name
            parts["coaming"].append(n)
        # The diagonal side: a face a plane thin, its middle where the side's is, turned to face the opening's middle
        # (south turned 45 degrees faces +x+z; north turned -45 faces +x-z, ...).
        mx, mz = 8 - sx * q, 8 - sz * q
        face, angle = {(1, 1): ("south", 45.0), (-1, 1): ("south", -45.0), (1, -1): ("north", -45.0), (-1, -1): ("north", 45.0)}[(sx, sz)]
        n = f"fillet{k}_side"
        m.box(n, (mx - L / 2, y0, mz), (mx + L / 2, y1, mz), faces=(face,), rotation={"origin": (mx, y0, mz), "axis": "y", "angle": angle})
        parts["coaming"].append(n)
    # The frame's knuckles on the coaming's south plate, a pin through each pair.
    for j, (x0, x1) in enumerate(FRAME_KNUCKLES):
        m.box(f"fknuckle{j}", (x0, PIN_Y - 1.0, PIN_Z - 1.0), (x1, PIN_Y + 1.0, PIN_Z + 1.0), faces=("north", "south", "east", "west", "up"))
        parts.setdefault("knuckle", []).append(f"fknuckle{j}")
    m.box("pin", (FRAME_KNUCKLES[0][0] - 0.4, PIN_Y - 0.4, PIN_Z - 0.4), (FRAME_KNUCKLES[1][1] + 0.4, PIN_Y + 0.4, PIN_Z + 0.4),
          faces=("north", "south", "east", "west", "up", "down"))
    parts["pin"] = ["pin"]
    # The clamp the lid's edge shuts under, on the north plate.
    m.box("clamp", (7.0, y1, 0.4), (9.0, y1 + 1.4, 1.4), faces=("north", "south", "east", "west", "up"))
    m.box("clamp_lip", (7.2, y1 + 1.1, 1.4), (8.8, y1 + 1.4, 1.9), faces=("south", "east", "west", "up", "down"))
    parts["clamp"] = ["clamp", "clamp_lip"]


def lid(m: BlockModel, parts: dict, lips: dict) -> None:
    """The lid: a round plate on the coaming, a boss in its middle with the handwheel's hub, the wheel's rim on four
    spokes, the hinge straps across it to its knuckles."""
    def sec(group: str, name: str, r: float, y0: float, y1: float, top=True, bottom=False, **kw):
        names = W.round_section(m, name, r, y0, y1, **kw)
        parts.setdefault(group, []).extend(names)
        for n in names:
            lips[n] = (top, bottom)
        return names
    # The lid, 32-sided: its top's planks on four levels (the boss covers their middle), its underside's likewise (the
    # dogs' hub under it covers theirs); the rubber seal round its underside where it shuts on the coaming.
    sec("lid", "lid", LID_R, *LID, top=True, bottom=True, lo=UNDER_HUB, hi=2.2)
    sec("under", "under_hub", UNDER_HUB, LID[0] - 0.45, LID[0] + 0.1, top=False, bottom=True, lo=0.0, toward="lo")
    parts["seal"] = W.round_ring(m, "seal", 5.6, 6.4, LID[0] - 0.2, LID[0], faces=("north", "south", "east", "west", "down"))
    sec("boss", "boss", 2.2, LID[1], LID[1] + 0.6, hi=1.2, root=0.3)
    sec("hub", "hub", 1.2, LID[1] + 0.5, WHEEL[1] + 0.3, hi=0.0, root=0.2)
    # The wheel's rim: a ring of straight lengths, one to each of its 24 sides, and its spokes.
    rim = W.round_ring(m, "rim", WHEEL_R[1], WHEEL_R[0], WHEEL[0], WHEEL[1])
    parts["wheel"] = rim
    spokes = BD.spokes(_Geo(m), "spoke", 1.0, WHEEL_R[1] + 0.3, 0.7, WHEEL[0] + 0.1, WHEEL[1] - 0.1, 8.0, 8.0, None)
    parts["wheel"] += spokes
    # The hinge straps and the lid's knuckles.
    for j, (x0, x1) in enumerate(LID_KNUCKLES):
        m.box(f"strap{j}", (x0 + 0.2, LID[1], 10.5), (x1 - 0.2, LID[1] + 0.45, PIN_Z - 0.6), faces=("north", "east", "west", "up"))
        # Their bottoms a twentieth under the lid's (in its plane, they flickered with its underside when it stood open),
        # the whole knuckle a twentieth down (still two pixels tall: two texels, not three thin ones), their sides 0.06 in
        # from the coaming's (so the twentieth down in it is inside it, in none of its faces' planes).
        m.box(f"lknuckle{j}", (x0, PIN_Y - 1.05, PIN_Z - 0.94), (x1, PIN_Y + 0.95, PIN_Z + 0.94), faces=("north", "south", "east", "west", "up", "down"))
        parts.setdefault("strap", []).append(f"strap{j}")
        parts.setdefault("knuckle", []).append(f"lknuckle{j}")


class _Geo:
    """The Blast Door's ring and spoke builders take a GeckoLib model and a bone: this lends them a block model."""

    def __init__(self, m: BlockModel):
        self.m = m

    def box(self, name, frm, to, faces=None, rotation=None, bone=None):
        return self.m.box(name, frm, to, faces=faces, rotation=rotation)


# --- painting ----------------------------------------------------------------------------------------------------------------
LID_PAINT = BD.DOOR
#: The lid's underside: the same paint, little worn, not darkened where it sits on the coaming (it shows when open).
UNDERSIDE = M.painted(BD.DOOR_RAMP, tone=0.68, wear=0.08, ao=0.12)
COAMING_STEEL = M.steel(tone=0.36, rust=0.1)
DARK = M.steel(tone=0.3, rust=0.1)


SEAL_RUBBER = M.rubber(tone=0.3)


def paint(m: BlockModel, parts: dict, lips: dict, seed: int) -> Tex:
    t = Tex(m.tex_w, m.tex_h)
    k = K.Kit(m, {}, seed=seed, ao_reach=1.6, turned=True)
    for n, (top, bottom) in lips.items():
        W.round_faces(k.S, [n], top, bottom, foot=False)
    if "coaming" in parts:
        k.paint(t, parts["coaming"], COAMING_STEEL)
        # Hazard paint round the opening on the coaming's top: seen in its corners beside the lid, and all round when open.
        k.overlay(t, parts["coaming"], lambda f, img: M.paste(img, k.S.material_on(f, M.stripes(width=1.5, wear=0.35)),
                                                               np.ones(img.shape[:2], bool)) if f.d == "up" else None)
        k.paint(t, parts["pin"] + parts["clamp"], DARK)
    if "knuckle" in parts:
        k.paint(t, parts["knuckle"], DARK)
    if "lid" in parts:
        k.paint(t, parts["lid"], LID_PAINT)
        k.S.paint(t, parts["lid"], UNDERSIDE, only=("down",))
        k.paint(t, parts["under"], BD.CAM)
        k.paint(t, parts["seal"], SEAL_RUBBER)
        k.paint(t, parts["boss"], BD.CAM)
        k.paint(t, parts["hub"], BD.BRIGHT)
        k.paint(t, parts["wheel"], BD.WHEEL_RED)
        k.paint(t, parts["strap"], DARK)
        k.overlay(t, parts["strap"], lambda f, img: M.rivets(f, img, every=2.0, inset=0) if f.d == "up" else None)
    return t


def build(name: str, kind: str, with_frame: bool, with_lid: bool, seed: int) -> BlockModel:
    m = BlockModel(name, tex_size=(128, 128) if with_lid else (64, 64), kind=kind)
    m.open_ok = set()
    parts: dict = {}
    lips: dict = {}
    if with_frame:
        frame(m, parts)
    if with_lid:
        lid(m, parts, lips)
    m.layout()
    t = paint(m, parts, lips, seed)
    W.match_walls(m, t, [b.name for b in m.boxes])
    m.save(t)
    return m


def open_lid() -> None:
    """The open lid's model: the lid's, every point moved by OPEN_SHIFT, in the lid's own texture."""
    src = json.loads((paths.MODELS / "block" / f"{NAME}_lid.json").read_text(encoding="utf-8"))
    out = copy.deepcopy(src)
    dx, dy, dz = OPEN_SHIFT

    def moved(p):
        return [round(p[0] + dx, 5), round(p[1] + dy, 5), round(p[2] + dz, 5)]
    for el in out["elements"]:
        el["from"], el["to"] = moved(el["from"]), moved(el["to"])
        if "rotation" in el and "origin" in el["rotation"]:
            el["rotation"]["origin"] = moved(el["rotation"]["origin"])
        for f in el["faces"].values():
            f.pop("cullface", None)
    for poly in out.get("groundburst:polygons", []):        # (its round parts' caps: the mod's polygons)
        poly["points"] = [moved(p) for p in poly["points"]]
        poly.pop("cull", None)
    write_json(paths.MODELS / "block" / f"{NAME}_lid_open.json", out)


def blockstate() -> None:
    """Multipart: the frame, and the lid shut or open, turned for each facing and half (see the module's notes)."""
    turn = {"north": 0, "east": 90, "south": 180, "west": 270}
    parts = []
    for facing, r in turn.items():
        top = (r) % 360
        bottom = (r + 180) % 360

        def when(half, open_=None):
            w = {"facing": facing, "half": half}
            if open_ is not None:
                w["open"] = "true" if open_ else "false"
            return w

        def apply(model, x, y):
            a = {"model": f"groundburst:block/{model}"}
            if x:
                a["x"] = x
            if y:
                a["y"] = y
            return a
        parts += [
            {"when": when("top"), "apply": apply(f"{NAME}_frame", 0, top)},
            {"when": when("bottom"), "apply": apply(f"{NAME}_frame", 180, bottom)},
            {"when": when("top", False), "apply": apply(f"{NAME}_lid", 0, top)},
            {"when": when("bottom", False), "apply": apply(f"{NAME}_lid", 180, bottom)},
            {"when": when("top", True), "apply": apply(f"{NAME}_lid_open", 270, top)},
            {"when": when("bottom", True), "apply": apply(f"{NAME}_lid_open", 90, bottom)},
        ]
    write_json(paths.ASSETS / "blockstates" / f"{NAME}.json", {"multipart": parts})
    # The open lid's own block (Java's BunkerHatchLidBlock) draws nothing: a model with its particle alone.
    write_json(paths.MODELS / "block" / f"{NAME}_lid_particle.json", {"textures": {"particle": f"groundburst:block/{NAME}_lid"}})
    write_json(paths.ASSETS / "blockstates" / f"{NAME}_lid.json", {"variants": {"": {"model": f"groundburst:block/{NAME}_lid_particle"}}})
    write_json(paths.ASSETS / "items" / f"{NAME}.json", {"model": {"type": "minecraft:model", "model": f"groundburst:item/{NAME}"}})


def main() -> None:
    for m in (build(f"{NAME}_frame", "block", True, False, 3101), build(f"{NAME}_lid", "block", False, True, 3102)):
        lo, hi = m.bounds()
        print(m.name, "bounds", [round(v, 2) for v in lo], [round(v, 2) for v in hi], "tex", m.tex_w, m.tex_h)
    item = BlockModel(NAME, tex_size=(128, 128), kind="item")
    item.open_ok = set()
    parts: dict = {}
    lips: dict = {}
    frame(item, parts)
    lid(item, parts, lips)
    item.layout()
    t = paint(item, parts, lips, 3103)
    item.fit_display(gui_rotation=(30, 225, 0), gui_margin=1.5)
    W.match_walls(item, t, [b.name for b in item.boxes])
    item.save(t)
    open_lid()
    blockstate()


if __name__ == "__main__":
    main()
