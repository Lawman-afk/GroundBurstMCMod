"""The bunker fittings (DESIGN.md §22.5), the third set of the cosmetic blocks: block models painted with the material
library.

- **Bunker Lamp**: a vapour-proof "jelly jar" lamp: a cast base, a glass jar, a cage of four bars and a ring over it,
  a cap; lit (the jar's elements lit) or dark. Built standing on a floor, pointing up; turned by its blockstate to point
  out of whatever face it is on. Its round parts, and the pipes', valve's, fuse box's and extinguisher's, are
  warheads.round_section's (their caps' planks a level apart only where they overlap in sight, the steps walled).
- **Vent Grille**: a square louvred grille plate on any face, a dark duct behind its slats; built lying on a floor.
- **Ventilation Duct**: square galvanised duct that joins its neighbours on all six sides: a core, and an arm toward each
  neighbour it joins (multipart), a flange at each joint.
- **Wall Pipes**: three pipes along a wall on a bracket plate (multipart: the run, and a cap at each end left unjoined).
- **Pipe Valve**: the big pipe through a gate valve, a handwheel out in front (shut and open: the stem out, the spokes on).
- **Fuse Box**: a labelled steel box, its conduit up, a breaker's handle at its side (off: its lamp red; on: green).
- **Fire Extinguisher**: a red cylinder on its foot, a head and lever, a hose and horn, a gauge, a label; standing, or
  hung on a wall on its bracket (a steel strip, two straps round it with buckles), as a torch is placed.
- **Steel Locker**: two blocks high, built whole and cut into its halves; open, its door past square on its hinges, a
  mirror inside it, a shelf and a coat rail, empty (the open door painted as the shut one: share_door).
- **Bunk Bed**: two bunks on olive steel posts, built whole (two blocks long, two high) and cut into its four blocks;
  the upper bunk's guard rails, the back one its whole length, the front one two thirds of it, the ladder in the gap.

    tools\\.venv\\Scripts\\python.exe tools\\assets\\bunker_fittings.py [lamp|grille|duct|pipes|fuse|extinguisher|locker|bed]
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
import bunker_hatch as BH  # noqa: E402
import command_room as CR  # noqa: E402
import warheads as W  # noqa: E402

ALL = ("north", "south", "east", "west", "up", "down")
SIDES = ("north", "south", "east", "west")
#: A model built pointing up, turned to point each way (the blockstate's x then y).
POINT = {"up": {}, "down": {"x": 180}, "north": {"x": 90}, "south": {"x": 90, "y": 180}, "east": {"x": 90, "y": 90},
         "west": {"x": 90, "y": 270}}
#: A model built facing north, turned to face each way.
TURN = {"north": {}, "east": {"y": 90}, "south": {"y": 180}, "west": {"y": 270}}


def round_along_faces(S: M.Surfacer, names, axis: str) -> None:
    """Readies a section's faces for painting as warheads.round_faces does for one along y: the borders that run along
    its axis are where one facet meets the next (no lip there); its ends keep theirs, painted evenly (W.round_faces);
    its skirts, the walls of its caps' steps, have no lips."""
    ax = {"x": 0, "y": 1, "z": 2}[axis]
    caps = {"x": ("east", "west"), "y": ("up", "down"), "z": ("north", "south")}[axis]
    for n in names:
        b = S.m.get(n)
        for d in list(b.faces):
            f = S.face(b, d)
            if W.is_skirt(n):
                W._even_wall(f, {k: np.full_like(v, np.inf) for k, v in f.border.items()})
                continue
            if d in caps:
                f.even = True
            along_u = abs(f.du[ax]) > abs(f.dv[ax])
            for k in (("top", "bottom") if along_u else ("left", "right")):
                f.edge[k] = np.full_like(f.border[k], np.inf)
                f.stop[k] = np.full_like(f.border[k], np.inf)


def light_up(m: BlockModel, names, level: int = 15) -> None:
    for n in names:
        m.get(n).light_emission = level


def states(name: str, models: dict[str, dict], key: str = "facing") -> None:
    """A blockstate of variants: {state key: {model, x, y}}."""
    write_json(paths.ASSETS / "blockstates" / f"{name}.json", {"variants": models})


def variant(model: str, rot: dict) -> dict:
    v = {"model": f"groundburst:block/{model}"}
    v.update({k: r for k, r in rot.items() if r})
    return v


# --- the Bunker Lamp ------------------------------------------------------------------------------------------------------------
GLASS_LIT = (255, 236, 186)
GLASS_DARK = (96, 92, 80)
CAST = M.steel(tone=0.32, rust=0.06)
CAGE = M.painted(M.BLACK_RAMP, tone=0.42, wear=0.12)


def lamp(lit: bool) -> BlockModel:
    name = "bunker_lamp" if lit else "bunker_lamp_off"
    m = BlockModel(name, tex_size=(64, 64), kind="block")
    m.open_ok = set()
    parts: dict[str, list[str]] = {}
    # (The base's foot against what it is on; the jar and the cap each reach a little down into the part under it.)
    parts["base"] = W.round_section(m, "base", 4.2, 0.0, 1.2, hi=2.7)
    parts["jar"] = W.round_section(m, "jar", 2.7, 1.2, 6.0, hi=1.8, root=0.4)
    parts["cap"] = W.round_section(m, "cap", 1.8, 6.0, 6.7, hi=0.0, root=0.4)
    if lit:
        light_up(m, parts["jar"])
    # The cage: a ring round the jar's middle, four bars from the base up to the cap.
    parts["ring"] = W.round_ring(m, "ring", 2.95, 3.4, 3.4, 3.9)
    for k, (dx, dz) in enumerate(((-1, -1), (1, -1), (-1, 1), (1, 1))):
        cx, cz = 8 + dx * 2.15, 8 + dz * 2.15
        m.box(f"bar{k}", (cx - 0.3, 1.2, cz - 0.3), (cx + 0.3, 6.4, cz + 0.3), faces=SIDES)
        parts.setdefault("bars", []).append(f"bar{k}")
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    k = K.Kit(m, {}, seed=5101, ao_reach=1.2, turned=True)
    for n in parts["base"] + parts["cap"]:
        W.round_faces(k.S, [n], True, True, foot=False)
    k.paint(t, parts["base"] + parts["cap"], CAST)
    k.paint(t, parts["ring"] + parts["bars"], CAGE)
    # The jar: lit, a warm glass with the filament's glow brighter at its middle; dark, a dull amber.
    k.paint(t, parts["jar"], M.white_paint(tone=0.8, wear=0.0))

    def glass(f: M.Face, img: np.ndarray) -> None:
        base = np.array(GLASS_LIT if lit else GLASS_DARK, np.float32)
        mid = np.clip(1.0 - np.abs(f.Y - 3.6) / 2.6, 0, 1)
        rgb = base * (0.82 + 0.18 * mid)[..., None]
        if lit:
            rgb = np.minimum(255, rgb + (mid > 0.7)[..., None] * 20)
        img[..., :3] = np.clip(rgb, 0, 255).astype(np.uint8)
    k.overlay(t, parts["jar"], glass)
    m.fit_display(gui_rotation=(30, 225, 0), gui_margin=1.5)
    W.match_walls(m, t, [b.name for b in m.boxes])
    m.save(t)
    return m


def lamp_files() -> None:
    lamp(True)
    lamp(False)
    v = {}
    for facing, rot in POINT.items():
        for lit in (True, False):
            v[f"facing={facing},lit={'true' if lit else 'false'}"] = variant("bunker_lamp" if lit else "bunker_lamp_off", rot)
    states("bunker_lamp", v)
    CR.block_item("bunker_lamp")


# --- the Vent Grille -----------------------------------------------------------------------------------------------------------
def grille() -> None:
    m = BlockModel("vent_grille", tex_size=(64, 64), kind="block")
    m.open_ok = set()
    parts: dict[str, list[str]] = {}

    def box(group, n, frm, to, faces=ALL, rotation=None):
        m.box(n, frm, to, faces=faces, rotation=rotation)
        parts.setdefault(group, []).append(n)

    box("back", "back", (2.5, 0.0, 2.5), (13.5, 0.1, 13.5), faces=("up",))
    box("frame", "frame_n", (1, 0, 1), (15, 1.5, 2.5), faces=("up", "north", "south", "east", "west"))
    box("frame", "frame_s", (1, 0, 13.5), (15, 1.5, 15), faces=("up", "north", "south", "east", "west"))
    box("frame", "frame_w", (1, 0, 2.5), (2.5, 1.5, 13.5), faces=("up", "east", "west"))
    box("frame", "frame_e", (13.5, 0, 2.5), (15, 1.5, 13.5), faces=("up", "east", "west"))
    for j, c in enumerate((3.6, 5.8, 8.0, 10.2, 12.4)):
        box("slat", f"slat{j}", (2.5, 0.35, c - 1.05), (13.5, 0.75, c + 1.05), faces=("up", "down", "north", "south"),
            rotation={"origin": (8.0, 0.55, c), "axis": "x", "angle": -30.0})
    # Four screws in the frame's corners.
    for j, (sx, sz) in enumerate(((1.75, 1.75), (14.25, 1.75), (1.75, 14.25), (14.25, 14.25))):
        box("screw", f"screw{j}", (sx - 0.35, 1.5, sz - 0.35), (sx + 0.35, 1.7, sz + 0.35), faces=("up", "north", "south", "east", "west"))
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    k = K.Kit(m, {}, seed=5201, ao_reach=1.4, turned=True)
    k.paint(t, parts["back"], M.painted(M.BLACK_RAMP, tone=0.2, wear=0.0))
    k.paint(t, parts["frame"] + parts["slat"], M.galvanized(tone=0.56))
    k.paint(t, parts["screw"], K.BRIGHT)
    m.fit_display(gui_rotation=(30, 225, 0), gui_margin=1.0)
    W.match_walls(m, t, [b.name for b in m.boxes])
    m.save(t)
    states("vent_grille", {f"facing={f}": variant("vent_grille", rot) for f, rot in POINT.items()})
    CR.block_item("vent_grille")


# --- the Ventilation Duct -------------------------------------------------------------------------------------------------------
DUCT_LO, DUCT_HI = 3.0, 13.0


def duct() -> None:
    """The core (a cube of duct, closed where nothing joins it) and the arm toward a neighbour (built toward the north,
    turned for the others), a flange where it meets the next block's."""
    core = BlockModel("ventilation_duct_core", tex_size=(64, 64), kind="block")
    core.open_ok = set()
    core.box("core", (DUCT_LO,) * 3, (DUCT_HI,) * 3, faces=ALL)
    core.layout()
    t = Tex(core.tex_w, core.tex_h)
    k = K.Kit(core, {}, seed=5301, ao_reach=1.4)
    k.paint(t, ["core"], M.galvanized(tone=0.58))
    k.overlay(t, ["core"], lambda f, img: duct_seams(f, img))
    core.fit_display(gui_rotation=(30, 225, 0), gui_margin=2.0)
    core.save(t)
    arm = BlockModel("ventilation_duct_arm", tex_size=(64, 64), kind="block")
    arm.open_ok = set()
    arm.box("arm", (DUCT_LO, DUCT_LO, 0.6), (DUCT_HI, DUCT_HI, DUCT_LO), faces=("east", "west", "up", "down"))
    arm.box("flange", (DUCT_LO - 0.5, DUCT_LO - 0.5, 0.0), (DUCT_HI + 0.5, DUCT_HI + 0.5, 0.6), faces=("east", "west", "up", "down", "south"))
    arm.layout()
    t = Tex(arm.tex_w, arm.tex_h)
    k = K.Kit(arm, {}, seed=5302, ao_reach=1.4)
    k.paint(t, ["arm"], M.galvanized(tone=0.58))
    k.overlay(t, ["arm"], lambda f, img: duct_seams(f, img))
    k.paint(t, ["flange"], M.galvanized(tone=0.48))
    k.overlay(t, ["flange"], lambda f, img: M.rivets(f, img, every=2.5, inset=0) if f.d in ("up", "down", "east", "west") else None)
    arm.save(t)
    turn = {"north": {}, "south": {"y": 180}, "east": {"y": 90}, "west": {"y": 270}, "up": {"x": 270}, "down": {"x": 90}}
    parts = [{"apply": {"model": "groundburst:block/ventilation_duct_core"}}]
    for d, rot in turn.items():
        a = {"model": "groundburst:block/ventilation_duct_arm"}
        a.update(rot)
        parts.append({"when": {d: "true"}, "apply": a})
    write_json(paths.ASSETS / "blockstates" / "ventilation_duct.json", {"multipart": parts})
    write_json(paths.ASSETS / "items" / "ventilation_duct.json", {"model": {"type": "minecraft:model", "model": "groundburst:block/ventilation_duct_core"}})


def duct_seams(f: M.Face, img: np.ndarray) -> None:
    """Sheet duct's standing seams: a darker crease a pixel in from each edge of the duct's faces, a ridge across."""
    rgb = img[..., :3].astype(np.float32)
    w = np.minimum.reduce([f.border["left"], f.border["right"], f.border["top"], f.border["bottom"]])
    rgb[(w > 0.5) & (w < 1.5)] *= 0.82
    img[..., :3] = np.clip(rgb, 0, 255).astype(np.uint8)


# --- the Wall Pipes and the Pipe Valve ------------------------------------------------------------------------------------------
#: The three pipes along the wall (built facing north: the wall to the south, z 16): radius, middle (y, z), paint.
PIPES = (("big", 1.6, 12.0, 14.0), ("mid", 1.1, 8.4, 14.4), ("small", 0.8, 15.1, 14.6))
#: How far each pipe's clamp and end flange stand out round it (so the small pipe's meet the big one's, never cross them).
RIM = {"big": 0.35, "mid": 0.45, "small": 0.3}
PIPE_PAINT = {"big": M.steel(tone=0.46, rust=0.06), "mid": M.painted(M.RED_PAINT_RAMP, tone=0.46, wear=0.18),
              "small": M.painted(M.YELLOW_PAINT_RAMP, tone=0.58, wear=0.2)}


def pipes(m: BlockModel, parts: dict, x0: float = 0.0, x1: float = 16.0, skip_big=None) -> None:
    """The three pipes along x from x0 to x1 (the big one left out between skip_big's ends, for a valve: it runs a fifth
    of a pixel on into the valve's flanges). No ends of their own: in the next block's pipe, or in a cap or a flange."""
    for key, r, cy, cz in PIPES:
        spans = [(x0, x1)] if not (key == "big" and skip_big) else [(x0, skip_big[0] + 0.2), (skip_big[1] - 0.2, x1)]
        for j, (a, b) in enumerate(spans):
            names = W.round_section(m, f"pipe_{key}{j}", r, a, b, axis="x", c=(cy, cz))
            parts.setdefault(f"pipe_{key}", []).extend(names)


def paint_pipes(k: K.Kit, t: Tex, parts: dict) -> None:
    for key, *_ in PIPES:
        names = parts.get(f"pipe_{key}", [])
        round_along_faces(k.S, names, "x")
        k.paint(t, names, PIPE_PAINT[key])


def wall_pipes() -> None:
    m = BlockModel("wall_pipes", tex_size=(128, 128), kind="block")
    m.open_ok = set()
    parts: dict[str, list[str]] = {}
    pipes(m, parts)
    # The bracket: a plate on the wall, a clamp round each pipe.
    m.box("plate", (7.0, 6.6, 15.6), (9.0, 16.0, 16.0), faces=("north", "east", "west", "up", "down"))
    parts["bracket"] = ["plate"]
    for key, r, cy, cz in PIPES:
        w = 0.6 if key != "small" else 0.45
        m.box(f"clamp_{key}", (8.0 - w, cy - r - RIM[key], cz - r - RIM[key]), (8.0 + w, min(16.0, cy + r + RIM[key]), 15.6),
              faces=("north", "east", "west", "up", "down"))
        parts["bracket"].append(f"clamp_{key}")
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    k = K.Kit(m, {}, seed=5401, ao_reach=1.4, turned=True)
    paint_pipes(k, t, parts)
    k.paint(t, parts["bracket"], K.GUN)
    m.fit_display(gui_rotation=(30, 225, 0), gui_margin=1.0)
    W.match_walls(m, t, [b.name for b in m.boxes])
    m.save(t)
    # The caps: a flange on each pipe's end where the run stops (left as seen from the front: the east end, facing north).
    # The pipe runs into it to the block's edge; its planks' levels step outward from there (a hair past the edge), so no
    # pipe shows past its cap.
    for side, (a, b), outer in (("left", (15.3, 16.0), "hi"), ("right", (0.0, 0.7), "lo")):
        c = BlockModel(f"wall_pipes_cap_{side}", tex_size=(64, 64), kind="block")
        c.open_ok = set()
        cp: dict[str, list[str]] = {}
        for key, r, cy, cz in PIPES:
            ends = {"lo": 0.0, "hi": r} if outer == "lo" else {"lo": r, "hi": 0.0}
            cp.setdefault("flange", []).extend(W.round_section(c, f"flange_{key}", r + RIM[key] + 0.05, a, b, axis="x", c=(cy, cz),
                                                                 toward=outer, **ends))
        c.layout()
        ct = Tex(c.tex_w, c.tex_h)
        ck = K.Kit(c, {}, seed=5402, ao_reach=1.0, turned=True)
        round_along_faces(ck.S, cp["flange"], "x")
        ck.paint(ct, cp["flange"], K.GUN)
        W.match_walls(c, ct, [b.name for b in c.boxes])
        c.save(ct)


def pipe_valve(open_: bool) -> None:
    name = "pipe_valve_open" if open_ else "pipe_valve"
    m = BlockModel(name, tex_size=(128, 128), kind="block")
    m.open_ok = set()
    parts: dict[str, list[str]] = {}
    pipes(m, parts, skip_big=(5.2, 10.8))
    _, r, cy, cz = PIPES[0]
    # The gate valve: its body round the big pipe, a flange each side, the bonnet out toward the room, the stem through
    # the handwheel (rising: out further when the valve is open), the wheel on its spokes.
    # (Each part's ends capped only where they show: the body's round the flanges, a flange's toward its pipe, which runs
    # into it; the flanges reach into the body, the bonnet into the body, the stem through the hub.)
    parts["body"] = W.round_section(m, "vbody", 2.3, 5.6, 10.4, axis="x", c=(cy, cz), lo=2.0, hi=2.0)
    parts["vflange"] = (W.round_section(m, "vflange_a", 2.0, 5.2, 5.8, axis="x", c=(cy, cz), lo=r, toward="lo")
                        + W.round_section(m, "vflange_b", 2.0, 10.2, 10.8, axis="x", c=(cy, cz), hi=r, toward="hi"))
    parts["bonnet"] = W.round_section(m, "bonnet", 1.4, 9.6, cz - 2.15, axis="z", c=(8.0, cy), lo=0.45, toward="lo")
    tip = 6.4 if open_ else 8.2
    parts["stem"] = W.round_section(m, "stem", 0.45, tip, 9.6, axis="z", c=(8.0, cy), sides=4, lo=0.0)
    wz0, wz1 = 8.6, 9.2
    parts["wheel"] = W.round_ring(m, "rim", 2.0, 2.6, wz0, wz1, axis="z", c=(8.0, cy))
    turn = 45.0 if open_ else 0.0
    for j in range(4):
        n = f"spoke{j}"
        m.box(n, (7.65, cy + 0.5, wz0 + 0.1), (8.35, cy + 2.1, wz1 - 0.1), faces=("east", "west", "north", "south"),
              rotation={"origin": (8.0, cy, wz0), "axis": "z", "angle": turn + 90.0 * j + 22.5})
        parts["wheel"].append(n)
    parts["hub"] = W.round_section(m, "hub", 0.8, wz0 - 0.2, wz1 + 0.2, axis="z", c=(8.0, cy), lo=0.45, hi=0.45)
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    k = K.Kit(m, {}, seed=5403, ao_reach=1.4, turned=True)
    paint_pipes(k, t, parts)
    for key, axis in (("body", "x"), ("vflange", "x"), ("bonnet", "z"), ("stem", "z"), ("hub", "z")):
        round_along_faces(k.S, parts[key], axis)
    k.paint(t, parts["body"] + parts["vflange"] + parts["bonnet"], K.GUN)
    k.paint(t, parts["stem"] + parts["hub"], K.BRIGHT)
    k.paint(t, parts["wheel"], K.RED)
    m.fit_display(gui_rotation=(30, 225, 0), gui_margin=1.0)
    W.match_walls(m, t, [b.name for b in m.boxes])
    m.save(t)


def pipe_files() -> None:
    wall_pipes()
    pipe_valve(False)
    pipe_valve(True)
    for name, models in (("wall_pipes", {False: "wall_pipes"}), ("pipe_valve", {False: "pipe_valve", True: "pipe_valve_open"})):
        parts = []
        for facing, rot in TURN.items():
            for open_, model in models.items():
                when = {"facing": facing}
                if name == "pipe_valve" and open_:
                    # Open by its wheel, or held open by a redstone signal (its "held").
                    when = {"OR": [{"facing": facing, "open": "true"}, {"facing": facing, "held": "true"}]}
                elif name == "pipe_valve":
                    when.update({"open": "false", "held": "false"})
                parts.append({"when": when, "apply": variant(model, rot)})
            for side in ("left", "right"):
                parts.append({"when": {"facing": facing, side: "false"}, "apply": variant(f"wall_pipes_cap_{side}", rot)})
        write_json(paths.ASSETS / "blockstates" / f"{name}.json", {"multipart": parts})
        CR.block_item(name if name == "wall_pipes" else "pipe_valve")


# --- the Fuse Box ----------------------------------------------------------------------------------------------------------------
def fuse_box(on: bool) -> None:
    name = "fuse_box_on" if on else "fuse_box"
    m = BlockModel(name, tex_size=(64, 64), kind="block")
    m.open_ok = set()
    parts: dict[str, list[str]] = {}

    def box(group, n, frm, to, faces=ALL, rotation=None, light=0):
        m.box(n, frm, to, faces=faces, rotation=rotation, light=light)
        parts.setdefault(group, []).append(n)

    box("body", "body", (2.5, 2.5, 12.5), (13.5, 14.5, 16.0), faces=("north", "east", "west", "up", "down"))
    box("door", "door", (2.8, 2.8, 12.1), (13.2, 14.2, 12.5), faces=("north", "east", "west", "up", "down"))
    box("latch", "latch", (12.3, 7.6, 11.7), (12.9, 9.4, 12.1), faces=("north", "east", "west", "up", "down"))
    for j, y in enumerate((4.0, 12.0)):
        box("hinge", f"hinge{j}", (2.6, y, 11.8), (3.2, y + 1.0, 12.5), faces=("north", "east", "west", "up", "down"))
    box(f"lamp_{'on' if on else 'off'}", "lamp", (10.4, 11.6, 11.8), (11.4, 12.6, 12.1), faces=("north", "east", "west", "up", "down"), light=15)
    # The conduit up from its fitting, capped at the block's top (it showed hollow from above with nothing over it).
    parts["conduit"] = W.round_section(m, "conduit", 0.9, 14.6, 16.0, c=(5.0, 14.3), hi=0.0)
    parts["fitting"] = W.round_section(m, "fitting", 1.25, 14.5, 15.1, c=(5.0, 14.3), hi=0.9)
    parts["boss"] = W.round_section(m, "boss", 0.9, 13.3, 14.3, axis="x", c=(8.5, 14.2), hi=0.0, toward="lo")
    # The breaker's handle: up (on) or down (off), a red grip at its end.
    a = -25.0 if on else 25.0
    y0, y1 = (8.5, 12.8) if on else (4.2, 8.5)
    rot = {"origin": (14.3, 8.5, 14.2), "axis": "x", "angle": a}
    box("handle", "handle", (14.3, y0, 13.85), (14.9, y1, 14.55), faces=ALL, rotation=rot)
    gy0, gy1 = (12.0, 13.6) if on else (3.4, 5.0)
    box("grip", "grip", (14.2, gy0, 13.75), (15.0, gy1, 14.65), faces=ALL, rotation=rot)
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    k = K.Kit(m, {}, seed=5501, ao_reach=1.4, turned=True)
    k.paint(t, parts["body"] + parts["door"], CR.CONSOLE)
    k.overlay(t, ["door"], fuse_label, only=("north",))
    k.paint(t, parts["latch"] + parts["hinge"] + parts["boss"] + parts["handle"], K.GUN)
    W.round_faces(k.S, parts["conduit"] + parts["fitting"], True, False, foot=False)
    round_along_faces(k.S, parts["boss"], "x")
    k.paint(t, parts["conduit"] + parts["fitting"], M.galvanized(tone=0.5))
    k.paint(t, parts["grip"], K.RED)
    CR.fill_faces(m, t, parts.get("lamp_on", []), (96, 255, 112))
    CR.fill_faces(m, t, parts.get("lamp_off", []), (220, 50, 36))
    m.fit_display(gui_rotation=(30, 225, 0), gui_margin=1.0)
    W.match_walls(m, t, [b.name for b in m.boxes])
    m.save(t)


def fuse_label(f: M.Face, img: np.ndarray) -> None:
    """The door's warning label: a yellow triangle with a black lightning bolt, a white card under it."""
    rgb = img[..., :3].astype(np.float32)
    cx, top = 8.0, 12.0
    dy = top - f.Y
    tri = (dy > 0) & (dy < 4.2) & (np.abs(f.X - cx) < dy * 0.62)
    rgb[tri] = (226, 186, 40)
    edge_ = tri & ((dy > 3.6) | (np.abs(f.X - cx) > dy * 0.62 - 0.6))
    rgb[edge_] = (30, 30, 30)
    bolt = tri & (np.abs((f.X - cx) + (dy - 2.3) * 0.5) < 0.45) & (dy > 1.0) & (dy < 3.6)
    rgb[bolt] = (30, 30, 30)
    card = (np.abs(f.X - cx) < 2.6) & (f.Y > 5.0) & (f.Y < 6.6)
    rgb[card] = (222, 220, 210)
    img[..., :3] = np.clip(rgb, 0, 255).astype(np.uint8)


def fuse_files() -> None:
    fuse_box(False)
    fuse_box(True)
    v = {}
    for facing, rot in TURN.items():
        for on in (False, True):
            for held in (False, True):                  # (on by its breaker, or held on by a redstone signal)
                v[f"facing={facing},held={'true' if held else 'false'},powered={'true' if on else 'false'}"] = variant(
                    "fuse_box_on" if on or held else "fuse_box", rot)
    states("fuse_box", v)
    CR.block_item("fuse_box")


# --- the Fire Extinguisher -----------------------------------------------------------------------------------------------------
#: Hung on a wall: how far the extinguisher is lifted, and moved back toward the wall (to the south, behind it).
WALL_LIFT, WALL_BACK = 3.0, 5.0


def extinguisher(wall: bool) -> None:
    """The extinguisher standing on its foot (built facing north); or hung on a wall behind it on the usual bracket: a
    steel strip screwed to the wall, two straps round the cylinder from it, a buckle at the front of each."""
    name = "fire_extinguisher_wall" if wall else "fire_extinguisher"
    m = BlockModel(name, tex_size=(64, 64), kind="block")
    m.open_ok = set()
    parts: dict[str, list[str]] = {}
    dy, dz = (WALL_LIFT, WALL_BACK) if wall else (0.0, 0.0)
    c = (8.0, 8.0 + dz)

    def box(group, n, frm, to, faces=ALL, rotation=None):
        m.box(n, (frm[0], frm[1] + dy, frm[2] + dz), (to[0], to[1] + dy, to[2] + dz), faces=faces, rotation=rotation)
        parts.setdefault(group, []).append(n)

    # (The foot's underside shows only when it hangs.)
    parts["foot"] = W.round_section(m, "foot", 2.15, dy, dy + 0.6, c=c, lo=0.0 if wall else None, hi=2.0)
    parts["body"] = W.round_section(m, "body", 2.0, dy + 0.6, dy + 9.0, c=c, hi=1.45, root=0.3)
    parts["shoulder"] = W.round_section(m, "shoulder", 1.45, dy + 9.0, dy + 9.8, c=c, hi=0.8, root=0.3)
    box("head", "head", (7.2, 9.8, 7.2), (8.8, 11.4, 8.8))
    box("head", "lever", (7.5, 11.4, 5.4), (8.5, 11.9, 9.2))
    box("head", "carry", (7.5, 10.6, 5.6), (8.5, 11.0, 7.2), faces=("north", "east", "west", "up", "down"))
    parts["gauge"] = W.round_section(m, "gauge", 0.65, dz + 6.8, dz + 7.4, axis="z", c=(8.0, dy + 10.6), lo=0.0, toward="lo")
    box("hose", "hose_top", (8.8, 9.9, 7.7), (10.6, 10.5, 8.3), faces=("north", "south", "east", "up", "down"))
    box("hose", "hose", (10.0, 4.2, 7.6), (10.6, 9.9, 8.4), faces=("north", "south", "east", "west"))
    box("nozzle", "nozzle", (9.9, 3.4, 7.5), (10.7, 4.2, 8.5), faces=ALL)
    if wall:
        # The strip on the wall, two screws in it; the straps round the cylinder, touching the strip at their backs.
        m.box("strip", (6.9, dy - 0.6, 15.4), (9.1, dy + 10.4, 16.0), faces=("north", "east", "west", "up", "down"))
        parts["bracket"] = ["strip"]
        for j, y in enumerate((dy, dy + 9.2)):
            n = f"screw{j}"
            m.box(n, (7.6, y, 15.2), (8.4, y + 0.6, 15.4), faces=("north", "east", "west", "up", "down"))
            parts.setdefault("screw", []).append(n)
        for j, y in enumerate((dy + 2.4, dy + 7.6)):
            parts["bracket"] += W.round_ring(m, f"strap{j}", 2.02, 2.4, y, y + 0.6, c=c)
            n = f"buckle{j}"
            m.box(n, (7.4, y - 0.1, c[1] - 2.7), (8.6, y + 0.7, c[1] - 2.3), faces=("north", "east", "west", "up", "down"))
            parts.setdefault("buckle", []).append(n)
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    k = K.Kit(m, {}, seed=5601, ao_reach=1.2, turned=True)
    for n in parts["foot"] + parts["body"] + parts["shoulder"]:
        W.round_faces(k.S, [n], True, n.startswith("foot") and wall, foot=False)
    round_along_faces(k.S, parts["gauge"], "z")
    k.paint(t, parts["foot"], K.RUBBER)
    k.paint(t, parts["body"] + parts["shoulder"], K.RED)
    k.overlay(t, parts["body"], lambda f, img: extinguisher_label(f, img, dy, c[1]))
    k.paint(t, parts["head"], K.BRIGHT)
    k.paint(t, parts["gauge"], K.WHITE)
    k.paint(t, parts["hose"] + parts["nozzle"], K.RUBBER)
    if wall:
        k.paint(t, parts["bracket"], K.GUN)
        k.paint(t, parts["screw"] + parts["buckle"], K.BRIGHT)
    m.fit_display(gui_rotation=(30, 225, 0), gui_margin=1.5)
    W.match_walls(m, t, [b.name for b in m.boxes])
    m.save(t)


def extinguisher_files() -> None:
    extinguisher(False)
    extinguisher(True)
    v = {}
    for facing, rot in TURN.items():
        for wall in (False, True):
            v[f"facing={facing},wall={'true' if wall else 'false'}"] = variant("fire_extinguisher_wall" if wall else "fire_extinguisher", rot)
    states("fire_extinguisher", v)
    CR.block_item("fire_extinguisher")


def extinguisher_label(f: M.Face, img: np.ndarray, dy: float = 0.0, cz: float = 8.0) -> None:
    """The cylinder's label on its front: a white card with black lines of instructions, a black band over it."""
    if f.d not in SIDES:
        return
    Y = f.Y - dy                                       # (its height up the cylinder, hung or standing)
    front = (f.Z < cz - 0.8) & (np.abs(f.X - 8.0) < 1.6)
    rgb = img[..., :3].astype(np.float32)
    card = front & (Y > 3.2) & (Y < 7.0)
    rgb[card] = (226, 224, 214)
    lines = card & (np.floor(Y * 2) % 3 == 0) & (np.abs(f.X - 8.0) < 1.2)
    rgb[lines] = (40, 40, 40)
    band = (np.abs(Y - 7.6) < 0.4)
    rgb[band] = (30, 30, 30)
    img[..., :3] = np.clip(rgb, 0, 255).astype(np.uint8)


# --- splitting a model built whole into its blocks' models ------------------------------------------------------------------------
def split_cells(name: str, cells: dict[str, tuple[float, float, float]], which) -> None:
    """The whole model's elements written as each block's model: {model name: (dx, dy, dz) the block's offset (pixels)},
    each element to the block {which}(element centre) names, moved by that block's offset; all in the whole's texture.
    The whole's own model file is left for the item, or removed if {which} says so."""
    path = paths.MODELS / "block" / f"{name}.json"
    whole = json.loads(path.read_text(encoding="utf-8"))
    out = {cell: [] for cell in cells}
    for el in whole["elements"]:
        c = [(el["from"][i] + el["to"][i]) / 2 for i in range(3)]
        cell = which(c)
        dx, dy, dz = cells[cell]
        el = json.loads(json.dumps(el))
        for i, d in enumerate((dx, dy, dz)):
            el["from"][i] -= d
            el["to"][i] -= d
            if "rotation" in el and "origin" in el["rotation"]:
                el["rotation"]["origin"][i] -= d
        out[cell].append(el)
    for cell, els in out.items():
        model = {k: v for k, v in whole.items() if k not in ("elements", "display")}
        model["elements"] = els
        write_json(paths.MODELS / "block" / f"{cell}.json", model)
    path.unlink()


# --- the Steel Locker -------------------------------------------------------------------------------------------------------------


def locker(open_: bool) -> None:
    """Built whole, two blocks high, facing north, its hinges on the viewer's left (east); cut at y 16 into its halves.
    A shell (back, sides, top, floor) so it is hollow when open: a shelf and a coat rail, nothing on them."""
    name = "steel_locker_open" if open_ else "steel_locker"
    m = BlockModel(name, tex_size=(128, 128), kind="block")
    m.open_ok = set()
    parts: dict[str, list[str]] = {}

    def box(group, n, frm, to, faces=ALL, rotation=None):
        m.box(n, frm, to, faces=faces, rotation=rotation)
        parts.setdefault(group, []).append(n)

    def tall(group, n, frm, to, faces=ALL, rotation=None):
        """A box cut at y 16 where it crosses it (each half's model gets its piece)."""
        if frm[1] < 16 < to[1]:
            box(group, f"{n}_lo", frm, (to[0], 16.0, to[2]), faces=tuple(f for f in faces if f != "up"), rotation=rotation)
            box(group, f"{n}_hi", (frm[0], 16.0, frm[2]), to, faces=tuple(f for f in faces if f != "down"), rotation=rotation)
        else:
            box(group, n, frm, to, faces=faces, rotation=rotation)

    tall("shell", "back", (1.0, 0.0, 15.2), (15.0, 31.0, 16.0), faces=("north", "south", "east", "west"))
    tall("shell", "side_l", (14.4, 0.0, 3.0), (15.0, 30.4, 15.2), faces=("north", "east", "west"))
    tall("shell", "side_r", (1.0, 0.0, 3.0), (1.6, 30.4, 15.2), faces=("north", "east", "west"))
    box("shell", "top", (1.0, 30.4, 3.0), (15.0, 31.0, 15.2), faces=("north", "east", "west", "up", "down"))
    box("shell", "floor", (1.6, 0.0, 3.0), (14.4, 0.6, 15.2), faces=("north", "up"))
    door = {"origin": (14.7, 0.0, 2.8), "axis": "y", "angle": -100.0} if open_ else None
    tall("door", "door", (1.3, 0.8, 2.6), (14.7, 30.4, 3.0), rotation=door)
    tall("handle", "handle", (2.6, 14.5, 2.0), (3.2, 17.5, 2.6), faces=("north", "east", "west", "up", "down"), rotation=door)
    box("lock", "padlock", (2.4, 18.2, 1.8), (3.4, 19.2, 2.6), faces=("north", "east", "west", "up", "down"), rotation=door)
    if open_:
        # Inside, empty (the user's wish: the clothes looked wrong): a shelf near its top, a coat rail under it.
        box("shelf", "shelf", (1.6, 24.0, 3.6), (14.4, 24.5, 15.2), faces=("north", "up", "down"))
        box("rail", "rail", (2.0, 22.6, 8.6), (14.0, 23.0, 9.0), faces=("north", "south", "up", "down"))
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    k = K.Kit(m, {}, seed=5701, ao_reach=1.6, turned=True)
    k.paint(t, parts["shell"] + parts["door"], CR.CONSOLE)
    k.paint(t, parts["shell"], M.painted(CR.GREY_RAMP, tone=0.42, wear=0.06), only=("north",) if open_ else ())
    k.overlay(t, parts["door"], locker_door)
    k.paint(t, parts["handle"], K.BRIGHT)
    k.paint(t, parts["lock"], K.BRASS)
    if open_:
        k.paint(t, parts["shelf"] + parts["rail"], CR.CONSOLE)
    W.match_walls(m, t, [b.name for b in m.boxes])
    m.save(t)
    split_cells(name, {f"{name}_bottom": (0, 0, 0), f"{name}_top": (0, 16, 0)},
                lambda c: f"{name}_top" if c[1] >= 16 else f"{name}_bottom")


def locker_door(f: M.Face, img: np.ndarray) -> None:
    """The door's face: louvred vents near its top and its foot, a card holder, a hinge's knuckles at its edge; inside
    (its back, seen when it stands open), a mirror."""
    rgb = img[..., :3].astype(np.float32)
    if f.d == "north":
        # Three slits a group, every other texel row (counted down from the face's top: the door is two boxes).
        row = np.indices(f.shape)[0]
        band = ((f.Y > 24.6) & (f.Y < 29.8)) | ((f.Y > 2.0) & (f.Y < 7.6))
        slit = band & (np.abs(f.X - 8.0) < 3.6) & (row % 2 == 1)
        rgb[slit] *= 0.3
        rgb[np.roll(slit, -1, axis=0) & ~slit & band] *= 1.12
        card = (np.abs(f.X - 8.0) < 2.0) & (np.abs(f.Y - 23.0) < 0.7)
        rgb[card] = (220, 218, 204)
        knuckles = (f.X > 13.9) & ((np.abs(f.Y - 6.0) < 1.0) | (np.abs(f.Y - 24.0) < 1.0))
        rgb[knuckles] = rgb[knuckles] * 0.7
    elif f.d == "south":
        mirror = (np.abs(f.X - 8.0) < 3.0) & (f.Y > 20.0) & (f.Y < 26.0)
        rgb[mirror] = (176, 190, 196)
        rgb[mirror & ((f.X - 8.0) + (f.Y - 23.0) * 0.8 > 1.6)] = (214, 224, 228)
    img[..., :3] = np.clip(rgb, 0, 255).astype(np.uint8)


def share_door() -> None:
    """The open locker's door, handle and padlock painted as the shut one's (the shut model's texels, its texture):
    painted where it swings to, its noise and bands ran across it another way, and the door changed as it opened."""
    shut = "groundburst:block/steel_locker"
    for half in ("bottom", "top"):
        closed = json.loads((paths.MODELS / "block" / f"steel_locker_{half}.json").read_text(encoding="utf-8"))
        faces = {el["name"]: el["faces"] for el in closed["elements"]}
        path = paths.MODELS / "block" / f"steel_locker_open_{half}.json"
        model = json.loads(path.read_text(encoding="utf-8"))
        model["textures"]["shut"] = shut
        for el in model["elements"]:
            if el["name"].split("_")[0] in ("door", "handle", "padlock"):
                el["faces"] = {d: {**f, "texture": "#shut"} for d, f in faces[el["name"]].items()}
        write_json(path, model)


def locker_files() -> None:
    locker(False)
    locker(True)
    share_door()
    v = {}
    for facing, rot in TURN.items():
        for half, h in (("lower", "bottom"), ("upper", "top")):
            for open_ in (False, True):
                v[f"facing={facing},half={half},open={'true' if open_ else 'false'}"] = variant(
                    f"steel_locker{'_open' if open_ else ''}_{h}", rot)
    states("steel_locker", v)
    icon("steel_locker", LOCKER_ICON, {"k": (44, 48, 46), "G": (150, 156, 150), "g": (120, 126, 120), "d": (60, 62, 64),
                                       "w": (220, 218, 204), "y": (176, 146, 70), "s": (196, 200, 202)})


LOCKER_ICON = [
    "................",
    ".....kkkkkk.....",
    ".....kGGGGk.....",
    ".....kdddGk.....",
    ".....kGGGGk.....",
    ".....kGwwGk.....",
    ".....kGGGGk.....",
    ".....kGGysk.....",
    ".....kGGGsk.....",
    ".....kGGGGk.....",
    ".....kGGGGk.....",
    ".....kdddGk.....",
    ".....kGGGGk.....",
    ".....kggggk.....",
    ".....kkkkkk.....",
    "................",
]


def icon(name: str, rows: list[str], col: dict) -> None:
    img = np.zeros((16, 16, 4), np.uint8)
    for y, row in enumerate(rows):
        for x, c in enumerate(row):
            if c != ".":
                img[y, x] = (*col[c], 255)
    Image.fromarray(img, "RGBA").save(paths.TEXTURES / "item" / f"{name}.png")
    write_json(paths.MODELS / "item" / f"{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"groundburst:item/{name}"}})
    write_json(paths.ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": f"groundburst:item/{name}"}})


# --- the Bunk Bed ------------------------------------------------------------------------------------------------------------------
BLANKET = M.painted(M.ramp("#1d2633", "#26313f", "#303d4d", "#3b4a5c", "#47586b", "#566a7e", "#6a7f93"), tone=0.5, wear=0.0, mottle=0.07, dither=0.85)
FRAME_PAINT = M.painted(M.OLIVE_RAMP, tone=0.38, wear=0.2)
#: Where the upper bunk's front guard rail ends (z, two thirds along from its head), and the ladder's two stiles (z from,
#: to) in the gap past it, up the bunk's front (west) side.
GUARD_END = 4.7
LADDER = ((6.2, 6.9), (11.9, 12.6))


def bunk_bed() -> None:
    """Built whole, facing north (its foot block at z 0..16, its head block ahead at z -16..0), two bunks high; cut into
    its four blocks' models: foot and head, bottom and top."""
    m = BlockModel("bunk_bed", tex_size=(128, 128), kind="block")
    m.open_ok = set()
    parts: dict[str, list[str]] = {}

    def box(group, n, frm, to, faces=ALL):
        """A box cut where it crosses a block's side (y 16, z 0), each piece to its block's model."""
        ys = [frm[1]] + ([16.0] if frm[1] < 16 < to[1] else []) + [to[1]]
        zs_ = [frm[2]] + ([0.0] if frm[2] < 0 < to[2] else []) + [to[2]]
        for i in range(len(ys) - 1):
            for j in range(len(zs_) - 1):
                fa = [f for f in faces if not (f == "up" and i < len(ys) - 2) and not (f == "down" and i > 0)
                      and not (f == "south" and j < len(zs_) - 2) and not (f == "north" and j > 0)]
                nm = n if len(ys) == 2 and len(zs_) == 2 else f"{n}_{i}{j}"
                m.box(nm, (frm[0], ys[i], zs_[j]), (to[0], ys[i + 1], zs_[j + 1]), faces=tuple(fa))
                parts.setdefault(group, []).append(nm)

    # The posts at its corners, two bunks high.
    for k, (x, z) in enumerate(((0.6, -15.4), (14.0, -15.4), (0.6, 14.0), (14.0, 14.0))):
        box("frame", f"post{k}", (x, 0.0, z), (x + 1.4, 30.0, z + 1.4))
    # Each bunk: its side rails, its end rails, its springs' frame under the mattress, the mattress, the pillow.
    for b, y0 in (("lower", 6.4), ("upper", 22.4)):
        for s, x in (("w", 0.6), ("e", 14.0)):
            box("frame", f"rail_{b}_{s}", (x, y0, -14.0), (x + 1.4, y0 + 1.0, 14.0), faces=("east", "west", "up", "down"))
        for e, z in (("head", -15.4), ("foot", 14.0)):
            box("frame", f"end_{b}_{e}", (2.0, y0, z), (14.0, y0 + 1.0, z + 1.4), faces=("north", "south", "up", "down"))
            box("frame", f"bar_{b}_{e}", (2.0, y0 + 4.6, z + 0.2), (14.0, y0 + 5.6, z + 1.2), faces=("north", "south", "up", "down"))
        box("mattress", f"mattress_{b}", (2.0, y0 + 1.0, -14.0), (14.0, y0 + 3.2, 14.0), faces=("north", "south", "east", "west", "up", "down"))
        box("pillow", f"pillow_{b}", (3.5, y0 + 3.2, -13.6), (12.5, y0 + 4.3, -10.4), faces=("north", "south", "east", "west", "up"))
    # The upper bunk's guard rails: the back one (east) its whole length, post to post; the front one (west) two thirds
    # of it from the head, its end held down on the bunk's side rail by an upright; the ladder in the gap past it.
    box("frame", "guard_back", (14.0, 27.4, -14.0), (15.4, 28.4, 14.0), faces=("east", "west", "up", "down"))
    box("frame", "guard_front", (0.6, 27.4, -14.0), (2.0, 28.4, GUARD_END), faces=("south", "east", "west", "up", "down"))
    box("frame", "guard_stay", (0.6, 23.4, GUARD_END - 1.0), (2.0, 27.4, GUARD_END), faces=("north", "south", "east", "west"))
    for k, (z0, z1) in enumerate(LADDER):
        box("frame", f"ladder{k}", (0.0, 0.0, z0), (0.6, 27.4, z1), faces=("north", "south", "east", "west", "up"))
    for k, y in enumerate((4.5, 9.5, 14.5, 19.5, 24.5)):
        box("frame", f"rung{k}", (0.1, y, LADDER[0][1]), (0.5, y + 0.6, LADDER[1][0]), faces=("east", "west", "up", "down"))
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    k = K.Kit(m, {}, seed=5801, ao_reach=1.6, turned=True)
    k.paint(t, parts["frame"], FRAME_PAINT)
    k.paint(t, parts["mattress"], M.white_paint(tone=0.78, wear=0.0))
    k.paint(t, parts["mattress"], BLANKET, only=("up", "east", "west", "south"))
    k.overlay(t, parts["mattress"], blanket_fold)
    k.paint(t, parts["pillow"], M.white_paint(tone=0.86, wear=0.0))
    W.match_walls(m, t, [b.name for b in m.boxes])
    m.save(t)
    cells = {"bunk_bed_foot_bottom": (0, 0, 0), "bunk_bed_head_bottom": (0, 0, -16), "bunk_bed_foot_top": (0, 16, 0),
             "bunk_bed_head_top": (0, 16, -16)}
    split_cells("bunk_bed", cells, lambda c: f"bunk_bed_{'head' if c[2] < 0 else 'foot'}_{'top' if c[1] >= 16 else 'bottom'}")
    v = {}
    for facing, rot in TURN.items():
        for part in ("foot", "head"):
            for half, h in (("lower", "bottom"), ("upper", "top")):
                for occ in ("false", "true"):
                    v[f"facing={facing},half={half},occupied={occ},part={part}"] = variant(f"bunk_bed_{part}_{h}", rot)
    states("bunk_bed", v)
    icon("bunk_bed", BUNK_ICON, {"k": (40, 44, 36), "o": (88, 96, 60), "b": (70, 88, 108), "w": (226, 226, 220), "m": (196, 196, 190)})


def blanket_fold(f: M.Face, img: np.ndarray) -> None:
    """The sheet folded back over the blanket at the head, white, the blanket's edge dark under it."""
    if f.d not in ("up", "east", "west"):
        return
    rgb = img[..., :3].astype(np.float32)
    fold = (f.Z > -10.4) & (f.Z < -8.6)
    rgb[fold] = (222, 222, 216)
    rgb[(np.abs(f.Z + 8.4) < 0.3)] *= 0.7
    img[..., :3] = np.clip(rgb, 0, 255).astype(np.uint8)


BUNK_ICON = [
    "................",
    "..k..........k..",
    "..kwmbbbbbbbbk..",
    "..kkkkkkkkkkkk..",
    "..k..........k..",
    "..k..........k..",
    "..k..........k..",
    "..k..........k..",
    "..kwmbbbbbbbbk..",
    "..kkkkkkkkkkkk..",
    "..k..........k..",
    "..k..........k..",
    "..k..........k..",
    "..k..........k..",
    "..k..........k..",
    "................",
]


def main() -> None:
    which = sys.argv[1:] or ["lamp", "grille", "duct", "pipes", "fuse", "extinguisher", "locker", "bed"]
    if "locker" in which:
        locker_files()
    if "bed" in which:
        bunk_bed()
    if "pipes" in which:
        pipe_files()
    if "fuse" in which:
        fuse_files()
    if "extinguisher" in which:
        extinguisher_files()
    if "lamp" in which:
        lamp_files()
    if "grille" in which:
        grille()
    if "duct" in which:
        duct()


if __name__ == "__main__":
    main()
