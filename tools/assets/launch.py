"""Launching (DESIGN.md §17, a basic launch site ahead of Phase 9): the component blocks, the two controllers' cabinets,
the Basic Missile, and the two formed structures (GeckoLib).

- Component blocks (cubes, see-through where they are frames): Launch Pad (grey concrete with a hazard-striped
  border), Gantry Frame (a red lattice), Gantry Arm (a red truss), Radar Mast (a grey lattice); the Radar Dish and the
  Antenna are modelled (a dish on a stub, a whip with crossbars).
- Basic Missile (item, 2 blocks tall as drawn; the missile in flight and on the pad is it three times over): a white
  body with two black roll bands, a red nose, four grey fins with black tips, a dark nozzle.
- Launch Silo, formed: a concrete pad with a hazard border and a flame trench under the missile (a grate in it),
  four hold-down posts, a control box at the front with a green CRT, the Energy Port's red-hatched housing at the
  back; beside it a red lattice gantry tower 9 blocks tall with a platform on top and an arm reaching in to the
  missile at its sixth level (the arm swings away during the countdown); the missile on the pad while one is loaded.
- Launch Computer, formed: an olive desk with a sloped console, two CRT monitors (glowing green), the Energy Port's
  red-hatched socket on its side, a Yagi antenna on top; behind it a grey lattice mast with a radar dish on a yoke,
  tilted up, that turns while the computer is powered.
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))

import numpy as np  # noqa: E402

from gz import materials as M  # noqa: E402
from gz import palette as P  # noqa: E402
from gz import paths  # noqa: E402
from gz.geo import Animation, GeoModel, save_animations  # noqa: E402
from gz.model import BlockModel, write_json  # noqa: E402
from gz.tex import Tex  # noqa: E402
import grenades as G  # noqa: E402
import structures as ST  # noqa: E402
import warheads as W  # noqa: E402
import missile_paint as MP  # noqa: E402

STEEL, GUN, OLIVE = P.STEEL, P.GUNMETAL, P.OLIVE
WHITE = P.WHITE_CERAMIC
BLACK = P.HAZARD_BLACK
YELLOW = P.HAZARD_YELLOW
RED = P.WARNING_RED
GREEN = P.INDICATOR_GREEN
CLEAR = (0, 0, 0, 0)
shaded = G.shaded


def tone(d: str) -> int:
    return {"up": 4, "down": 2, "north": 3, "west": 3}.get(d, 2)


# --- the missile (shared by the item and the formed silo) -----------------------------------------------------------------
def missile_parts() -> list[tuple[str, tuple, tuple, tuple, str]]:
    """(name, from, to, faces, paint) in item pixels: 9 x 32 x 9, centred on x = z = 8, tail at y 0. Drawn 4x over
    (8 blocks tall) on the silo and in flight."""
    all6 = ("north", "south", "east", "west", "up", "down")
    parts = [("nozzle", (7.0, 0, 7.0), (9.0, 1.5, 9.0), ("north", "south", "east", "west", "down"), "nozzle")]

    def prism(name, r, w, y0, y1, paint, ends=("up", "down")):
        parts.append((f"{name}_c", (8 - r, y0, 8 - w), (8 + r, y1, 8 + w), ("north", "south", "east", "west") + ends, paint))
        parts.append((f"{name}_n", (8 - w, y0, 8 - r), (8 + w, y1, 8 - w), ("north", "east", "west") + ends, paint))
        parts.append((f"{name}_s", (8 - w, y0, 8 + w), (8 + w, y1, 8 + r), ("south", "east", "west") + ends, paint))
    prism("body", 2.0, 1.3, 1.5, 26, "body")
    prism("nose0", 1.6, 1.05, 26, 28.5, "nose_base")
    prism("nose1", 1.0, 0.66, 28.5, 30.8, "nose")
    parts.append(("tip", (7.6, 30.8, 7.6), (8.4, 32, 8.4), all6, "tip"))
    parts.append(("fin_n", (7.75, 1.5, 3.5), (8.25, 7, 6), ("north", "east", "west", "up", "down"), "fin"))
    parts.append(("fin_s", (7.75, 1.5, 10), (8.25, 7, 12.5), ("south", "east", "west", "up", "down"), "fin"))
    parts.append(("fin_w", (3.5, 1.5, 7.75), (6, 7, 8.25), ("west", "north", "south", "up", "down"), "fin"))
    parts.append(("fin_e", (10, 1.5, 7.75), (12.5, 7, 8.25), ("east", "north", "south", "up", "down"), "fin"))
    return parts


NOSE_PARTS = ("nose_base", "nose", "tip")


FLIGHT_DENSITY = 4


def missile_item() -> None:
    """The Missile (bare steel nose), and a model for each warhead it can carry: the renderer of a missile in
    flight picks one by the warhead's id (custom model data), so the nose shows what it carries. A Strategic missile
    ("strategic_<warhead>") is its own longer model. Each has a flight model too ("flight_<look>"), four texels an
    item pixel, for the missile in flight, its spent stages and the assembly screens' preview; the inventory keeps the
    item model (fine details would only be noise at icon size)."""
    for pre, d in (("", 1), ("flight_", FLIGHT_DENSITY)):
        for nose in BENCH_NOSES:
            missile_model(pre + ("missile" if nose == "none" else f"missile_{nose}"), nose, d)
            strategic_model(f"{pre}strategic_missile_{nose}", nose, density=d)
            strategic_model(f"{pre}strategic_missile_{nose}_s1", nose, stages=(2,), density=d)    # in flight, the first stage gone
        for nose in ICBM_NOSES:
            icbm_model(f"{pre}icbm_missile_{nose}", nose, density=d)
            icbm_model(f"{pre}icbm_missile_{nose}_s1", nose, stages=(2, 3), density=d)
            icbm_model(f"{pre}icbm_missile_{nose}_s2", nose, stages=(3,), density=d)
        # The spent stages falling away (slice 9a).
        strategic_model(f"{pre}strategic_stage1", "none", stages=(1,), density=d)
        icbm_model(f"{pre}icbm_stage1", "none", stages=(1,), density=d)
        icbm_model(f"{pre}icbm_stage2", "none", stages=(2,), density=d)
    def case(when, model):
        return {"when": when, "model": {"type": "minecraft:model", "model": f"groundzero:item/{model}"}}
    cases = []
    for pre in ("", "flight_"):
        cases += [case(pre + nose, f"{pre}missile_{nose}") for nose in BENCH_NOSES if nose != "none"]
        cases += [case(f"{pre}strategic_{nose}", f"{pre}strategic_missile_{nose}") for nose in BENCH_NOSES]
        cases += [case(f"{pre}strategic_{nose}_s1", f"{pre}strategic_missile_{nose}_s1") for nose in BENCH_NOSES]
        cases += [case(f"{pre}icbm_{nose}", f"{pre}icbm_missile_{nose}") for nose in ICBM_NOSES]
        cases += [case(f"{pre}icbm_{nose}_s{k}", f"{pre}icbm_missile_{nose}_s{k}") for nose in ICBM_NOSES for k in (1, 2)]
        cases += [case(pre + stage, pre + stage) for stage in ("strategic_stage1", "icbm_stage1", "icbm_stage2")]
    cases.append(case("flight_none", "flight_missile"))                    # a missile with no warhead, in flight
    write_json(paths.ASSETS / "items" / "missile.json", {"model": {
        "type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0, "cases": cases,
        "fallback": {"type": "minecraft:model", "model": "groundzero:item/missile"}}})


def missile_model(model_name: str, nose: str, density: int = 1) -> None:
    m = BlockModel(model_name, tex_size=(64 * density, 64 * density), kind="item", density=density)
    for name, frm, to, faces, _ in missile_parts():
        m.box(name, frm, to, faces=faces)
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    MP.paint(M.Surfacer(m, seed=101), t, [(name, kind, nose) for name, _, _, _, kind in missile_parts()], MP.Frame(), "basic")
    m.fit_display(gui_rotation=(30, 45, 30), gui_margin=0.5)
    lo, hi = m.bounds()
    c = [(lo[i] + hi[i]) / 2 for i in range(3)]

    def ctx(scale, base, rotation=(0, 0, 0)):
        return {"rotation": list(rotation), "translation": [round(base[i] - (c[i] - 8) * scale, 3) for i in range(3)], "scale": [scale] * 3}
    m.display["ground"] = ctx(0.5, (0, 0, 0))
    m.display["fixed"] = ctx(0.5, (0, 0, 0))
    m.display["thirdperson_righthand"] = ctx(0.4, (0, 3, 1), (0, 90, 0))
    m.display["thirdperson_lefthand"] = ctx(0.4, (0, 3, 1), (0, 90, 0))
    m.display["firstperson_righthand"] = ctx(0.35, (1, 3, 0), (0, -25, 0))
    m.display["firstperson_lefthand"] = ctx(0.35, (1, 3, 0), (0, 155, 0))
    m.save(t)

#: The four quadrants round a missile's axis (between its fins), and which of them is black in the first band of a
#: roll pattern (the others are white; the colours swap every band).
QUADRANTS = ("ne", "se", "sw", "nw")
BLACK_FIRST = ("ne", "sw")


def quad_boxes(r: float, w: float, y0: float, y1: float, cx: float = 8, cz: float = 8):
    """A "plus" prism cut into its four quadrants, eight boxes: (suffix, from, to, faces, quadrant). Every outer face
    of a box lies in one quadrant, so a roll pattern painted per box has solid corners (each corner's two faces in the
    same colour) and changes colour only across the middle of each side. North is -z, east +x."""
    ends = ("up", "down")
    return [
        ("c_ne", (cx, y0, cz - w), (cx + r, y1, cz), ("east", "north") + ends, "ne"),
        ("c_se", (cx, y0, cz), (cx + r, y1, cz + w), ("east", "south") + ends, "se"),
        ("c_sw", (cx - r, y0, cz), (cx, y1, cz + w), ("west", "south") + ends, "sw"),
        ("c_nw", (cx - r, y0, cz - w), (cx, y1, cz), ("west", "north") + ends, "nw"),
        ("n_e", (cx, y0, cz - r), (cx + w, y1, cz - w), ("north", "east") + ends, "ne"),
        ("n_w", (cx - w, y0, cz - r), (cx, y1, cz - w), ("north", "west") + ends, "nw"),
        ("s_e", (cx, y0, cz + w), (cx + w, y1, cz + r), ("south", "east") + ends, "se"),
        ("s_w", (cx - w, y0, cz + w), (cx, y1, cz + r), ("south", "west") + ends, "sw"),
    ]


def roll_colour(quadrant: str, band: int, black, white):
    """The roll pattern: a quadrant's colour in a band."""
    return black if ((quadrant in BLACK_FIRST) == (band % 2 == 0)) else white


# --- the Strategic missile (Phase 8): the long two-stage missile that carries nuclear warheads -----------------------------
#: Item pixels its model reaches below the usual missile's tail line (MissileParts.STRATEGIC_TAIL in Java).
STRATEGIC_TAIL = 11.5


def strategic_parts() -> list[tuple[str, tuple, tuple, tuple, str]]:
    """(name, from, to, faces, paint) in item pixels, centred on x = z = 8, tail at y -11.5, nose tip at 32: a wide
    first stage with four big swept fins and four nozzles, an interstage ring, a slimmer second stage, the warhead's
    nose. Drawn 4x over on the Launch Complex (about 11 blocks) and in flight."""
    all6 = ("north", "south", "east", "west", "up", "down")
    parts = []
    for i, (x, z) in enumerate(((6.5, 6.5), (8.3, 6.5), (6.5, 8.3), (8.3, 8.3))):
        parts.append((f"nozzle{i}", (x, -11.5, z), (x + 1.2, -10, z + 1.2), ("north", "south", "east", "west", "down"), "nozzle"))

    def prism(name, r, w, y0, y1, paint, ends=("up", "down")):
        parts.append((f"{name}_c", (8 - r, y0, 8 - w), (8 + r, y1, 8 + w), ("north", "south", "east", "west") + ends, paint))
        parts.append((f"{name}_n", (8 - w, y0, 8 - r), (8 + w, y1, 8 - w), ("north", "east", "west") + ends, paint))
        parts.append((f"{name}_s", (8 - w, y0, 8 + w), (8 + w, y1, 8 + r), ("south", "east", "west") + ends, paint))
    # The first stage in quadrants, so its roll pattern has solid corners (see quad_boxes).
    for suffix, frm, to, faces, quadrant in quad_boxes(2.4, 1.6, -10, 8):
        parts.append((f"stage1_{suffix}", frm, to, faces, f"stage1_{quadrant}"))
    prism("inter", 2.5, 1.65, 8, 9.2, "interstage")
    # The second stage's engine bell, hidden inside the interstage until the first stage drops away in flight.
    parts.append(("bell2", (7.0, 8.1, 7.0), (9.0, 9.2, 9.0), ("north", "south", "east", "west", "down"), "nozzle"))
    prism("stage2", 2.0, 1.3, 9.2, 23, "stage2")
    prism("nose0", 1.75, 1.15, 23, 26.5, "nose_base")
    prism("nose1", 1.1, 0.73, 26.5, 30, "nose")
    parts.append(("tip", (7.55, 30, 7.55), (8.45, 32, 8.45), all6, "tip"))
    # Four swept fins: a long root low on the first stage and a shorter step above it.
    fins = {"n": ((7.7, 2.4), (8.3, 5.6), (3.8, 5.6)), "s": ((7.7, 10.4), (8.3, 13.6), (10.4, 12.2)),
            "w": ((2.4, 7.7), (5.6, 8.3), (3.8, 5.6)), "e": ((10.4, 7.7), (13.6, 8.3), (10.4, 12.2))}
    for d, (lo, hi, upper) in fins.items():
        if d in "ns":
            parts.append((f"sfin_{d}", (lo[0], -10, lo[1]), (hi[0], -4, hi[1]), all6, "sfin"))
            parts.append((f"sfin_{d}2", (lo[0], -4, upper[0]), (hi[0], -1, upper[1]), all6, "sfin"))
        else:
            parts.append((f"sfin_{d}", (lo[0], -10, lo[1]), (hi[0], -4, hi[1]), all6, "sfin"))
            parts.append((f"sfin_{d}2", (upper[0], -4, lo[1]), (upper[1], -1, hi[1]), all6, "sfin"))
    return parts


def strategic_flight_stage(name: str) -> int:
    """Which of the Strategic missile's stages a part flies off with: 1, the first (dropped in flight), or 2."""
    return 1 if name.startswith(("nozzle", "stage1_", "sfin", "inter_")) else 2


def strategic_model(model_name: str, nose: str, stages=(1, 2), density: int = 1) -> None:
    """The Strategic missile as an item (and in flight), or only some of its stages: (2,) once the first has dropped,
    (1,) the spent first stage falling away."""
    m = BlockModel(model_name, tex_size=(64 * density, 64 * density), kind="item", density=density)
    parts = [p for p in strategic_parts() if strategic_flight_stage(p[0]) in stages]
    for name, frm, to, faces, _ in parts:
        m.box(name, frm, to, faces=faces)
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    MP.paint(M.Surfacer(m, seed=103), t, [(name, kind, nose) for name, _, _, _, kind in parts], MP.Frame(), "strategic")
    m.fit_display(gui_rotation=(30, 45, 30), gui_margin=0.5)
    lo, hi = m.bounds()
    c = [(lo[i] + hi[i]) / 2 for i in range(3)]

    def ctx(scale, base, rotation=(0, 0, 0)):
        return {"rotation": list(rotation), "translation": [round(base[i] - (c[i] - 8) * scale, 3) for i in range(3)], "scale": [scale] * 3}
    m.display["ground"] = ctx(0.4, (0, 0, 0))
    m.display["fixed"] = ctx(0.4, (0, 0, 0))
    m.display["thirdperson_righthand"] = ctx(0.32, (0, 3, 1), (0, 90, 0))
    m.display["thirdperson_lefthand"] = ctx(0.32, (0, 3, 1), (0, 90, 0))
    m.display["firstperson_righthand"] = ctx(0.28, (1, 3, 0), (0, -25, 0))
    m.display["firstperson_lefthand"] = ctx(0.28, (1, 3, 0), (0, 155, 0))
    m.save(t)


# --- the intercontinental missile (slice 8c): a whole weapon from the Strategic Assembly Hall -----------------------------
#: Item pixels its model reaches below the usual missile's tail line (MissileParts.ICBM_TAIL in Java).
ICBM_TAIL = 11.5
#: The only warheads it carries (whole weapons: the hall builds them in); every other missile carries the rest.
ICBM_NOSES = ("thermonuclear_warhead", "cobalt_warhead", "tsar_warhead")
BENCH_NOSES = [n for n in W.NOSES if n not in ICBM_NOSES]

#: Its sections, tail to nose, in item pixels (centred on x = z = 8): (name, half-width, side half-width, y0, y1, paint).
#: Three solid-fuel stages narrowing upward and dark interstages; then its warhead's nose (ICBM_SHROUD, or the Tsar's
#: bomb, TSAR_SECTIONS). Drawn upright four times over on the Launch Complex (about 11 blocks) and in flight, and lying
#: on the Strategic Assembly Hall's cradles.
ICBM_SECTIONS = [
    ("skirt", 3.2, 2.15, -10, -8.6, "iskirt"),
    ("stage1", 3.0, 2.0, -8.6, 6, "istage1"),
    ("inter1", 2.8, 1.85, 6, 7.2, "iinter"),
    ("stage2", 2.6, 1.75, 7.2, 16.4, "istage2"),
    ("inter2", 2.35, 1.55, 16.4, 17.4, "iinter"),
    ("stage3", 2.2, 1.45, 17.4, 22.4, "istage3"),
]
#: The thermonuclear and cobalt warheads' shroud over the third stage, and its tip.
ICBM_SHROUD = [
    ("nose0", 2.0, 1.3, 22.4, 26.2, "nose_base"),
    ("nose1", 1.35, 0.9, 26.2, 29.6, "nose"),
]
ICBM_TIP = ((7.5, 29.6, 7.5), (8.5, 32, 8.5))
#: The Tsar rides bare on the third stage, wider than any stage (slice 8d): its canvas parachute pack wrapped round the
#: stage's top just above the complex's upper clamp (item y 18-20), the tail cone, the fat body (a white stencil band,
#: four glowing vents), a rounded nose and its fuze cap; four fins round the pack.
TSAR_SECTIONS = [
    ("tpack", 2.7, 1.8, 20.4, 21.8, "tsar_pack"),
    ("ttail", 3.3, 2.25, 21.8, 23.0, "tsar_tail"),        # (not 2.2: that is the third stage's width, z-fighting)
    ("tbody", 3.8, 2.55, 23.0, 29.4, "tsar_body"),
    ("tnose0", 3.2, 2.15, 29.4, 30.6, "tsar_nose"),
    ("tnose1", 2.2, 1.45, 30.6, 31.4, "tsar_nose"),
]
TSAR_TIP = ((7.4, 31.4, 7.4), (8.6, 32, 8.6))
#: Its fins: a root along the pack out to 4.4, a step against the tail cone out to 4.0 (from its skin).
TSAR_FIN_ROWS = (("tfin", 20.4, 21.8, 2.7, 4.4), ("tfin2", 21.8, 22.6, 3.3, 4.0))
#: Its vents, one on each side of the body: lit in flight (item model) and on the GeckoLib models (a glow layer).
TSAR_VENT = (23.6, 24.8, 0.8)                              # (y0, y1, half-width along the face)
ICBM_NOZZLES = ((6.3, 6.3), (8.5, 6.3), (6.3, 8.5), (8.5, 8.5))
#: Four swept fins low on the first stage, one on each side (after the in-game look: it had none): a long root out to
#: 5.4 and a shorter step above it out to 4.3, from the stage's skin (3.0). (direction: x step, z step)
ICBM_FINS = {"n": (0, -1), "s": (0, 1), "w": (-1, 0), "e": (1, 0)}
ICBM_FIN_ROWS = (("fin", -8.6, -3.0, 5.4), ("fin2", -3.0, -0.2, 4.3))
#: Which of the hall crane's four loads each section comes in: the first stage, the second, the third (the warhead's
#: nose is the fourth). The bells are the upper stages' engines.
ICBM_LOAD = {"skirt": 1, "stage1": 1, "inter1": 2, "stage2": 2, "inter2": 3, "stage3": 3, "bell2": 2, "bell3": 3}
#: Which stage each section flies off with (slice 9a): the first drops with its interstage, then the second with its
#: own; the third stays on to the end with the warhead. The upper stages' engine bells hang inside the interstage
#: below them until it goes.
ICBM_FLIGHT = {"skirt": 1, "stage1": 1, "inter1": 1, "stage2": 2, "inter2": 2, "stage3": 3, "bell2": 2, "bell3": 3}


def icbm_flight_stage(name: str) -> int:
    """Which stage a part of the intercontinental missile flies off with (3: on to the end, as the nose does)."""
    if name.startswith(("nozzle", "fin")):
        return 1
    return ICBM_FLIGHT.get(name.rsplit("_", 1)[0], 3)
ALL6 = ("north", "south", "east", "west", "up", "down")
#: The face of a fin (or vent) that lies against the body, by the side it stands out from.
INNER = {"n": "south", "s": "north", "w": "east", "e": "west"}


def _sections(table) -> list[tuple[str, tuple, tuple, tuple, str]]:
    """Round sections as "plus" prisms: a core box full width in x, and a box either side of it in z."""
    parts = []
    for name, r, w, y0, y1, paint in table:
        parts.append((f"{name}_c", (8 - r, y0, 8 - w), (8 + r, y1, 8 + w), ALL6, paint))
        parts.append((f"{name}_n", (8 - w, y0, 8 - r), (8 + w, y1, 8 - w), ("north", "east", "west", "up", "down"), paint))
        parts.append((f"{name}_s", (8 - w, y0, 8 + w), (8 + w, y1, 8 + r), ("south", "east", "west", "up", "down"), paint))
    return parts


def _fins(rows, thick: float, paint: str) -> list[tuple[str, tuple, tuple, tuple, str]]:
    """Four fins, one on each side: rows of (name, y0, y1, from radius, to radius), each row resting on the one below."""
    parts = []
    for d, (dx, dz) in ICBM_FINS.items():
        for i, (part, y0, y1, r0, r1) in enumerate(rows):
            faces = tuple(f for f in ALL6 if f != INNER[d] and not (i > 0 and f == "down"))
            if dx == 0:
                z0, z1 = sorted((8 + dz * r0, 8 + dz * r1))
                parts.append((f"{part}_{d}", (8 - thick, y0, z0), (8 + thick, y1, z1), faces, paint))
            else:
                x0, x1 = sorted((8 + dx * r0, 8 + dx * r1))
                parts.append((f"{part}_{d}", (x0, y0, 8 - thick), (x1, y1, 8 + thick), faces, paint))
    return parts


def icbm_parts() -> list[tuple[str, tuple, tuple, tuple, str]]:
    """The booster: (name, from, to, faces, paint) in item pixels, tail at y -11.5 (four nozzles), up to the third
    stage's top at 22.4; its four fins. The warhead's nose comes from icbm_nose_parts."""
    parts = []
    for i, (x, z) in enumerate(ICBM_NOZZLES):
        parts.append((f"nozzle{i}", (x, -11.5, z), (x + 1.2, -10, z + 1.2), ("north", "south", "east", "west", "down"), "nozzle"))
    parts += _sections(ICBM_SECTIONS)
    parts += _fins([(part, y0, y1, 3.0, reach) for part, y0, y1, reach in ICBM_FIN_ROWS], 0.3, "ifin")
    # The upper stages' engine bells, inside the interstages below them (seen only once the stage below has dropped).
    sides = ("north", "south", "east", "west", "down")
    parts.append(("bell2", (6.9, 6.1, 6.9), (9.1, 7.2, 9.1), sides, "nozzle"))
    parts.append(("bell3", (7.0, 16.5, 7.0), (9.0, 17.4, 9.0), sides, "nozzle"))
    return parts


def icbm_nose_parts(nose: str) -> list[tuple[str, tuple, tuple, tuple, str]]:
    """What sits on the third stage for a warhead, up to y 32: the shroud and its tip, or the Tsar's bomb."""
    if nose != "tsar_warhead":
        return _sections(ICBM_SHROUD) + [("tip", ICBM_TIP[0], ICBM_TIP[1], ALL6, "tip")]
    parts = _sections(TSAR_SECTIONS) + [("ttip", TSAR_TIP[0], TSAR_TIP[1], ALL6, "tsar_tip")]
    parts += _fins(TSAR_FIN_ROWS, 0.3, "tsar_fin")
    y0, y1, half = TSAR_VENT
    r = TSAR_SECTIONS[2][1]                                  # the body's half-width
    for d, (dx, dz) in ICBM_FINS.items():
        faces = tuple(f for f in ALL6 if f != INNER[d])
        if dx == 0:
            z0, z1 = sorted((8 + dz * r, 8 + dz * (r + 0.15)))
            parts.append((f"tvent_{d}", (8 - half, y0, z0), (8 + half, y1, z1), faces, "tsar_vent"))
        else:
            x0, x1 = sorted((8 + dx * r, 8 + dx * (r + 0.15)))
            parts.append((f"tvent_{d}", (x0, y0, 8 - half), (x1, y1, 8 + half), faces, "tsar_vent"))
    return parts


def icbm_model(model_name: str, nose: str, stages=(1, 2, 3), density: int = 1) -> None:
    """The intercontinental missile as an item (and in flight), or only some of its stages: (2, 3) or (3,) once the
    first stages have dropped in flight (the nose stays with the third), (1,) or (2,) a spent stage falling away."""
    size = (128, 64) if nose == "tsar_warhead" else (64, 64)                # the bomb's faces need room
    m = BlockModel(model_name, tex_size=(size[0] * density, size[1] * density), kind="item", density=density)
    parts = [p for p in icbm_parts() if icbm_flight_stage(p[0]) in stages]
    if 3 in stages:
        parts += icbm_nose_parts(nose)
    for name, frm, to, faces, kind in parts:
        m.box(name, frm, to, faces=faces, light=15 if kind == "tsar_vent" else 0)
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    MP.paint(M.Surfacer(m, seed=107), t, [(name, kind, nose) for name, _, _, _, kind in parts], MP.Frame(), "icbm")
    m.fit_display(gui_rotation=(30, 45, 30), gui_margin=0.5)
    lo, hi = m.bounds()
    c = [(lo[i] + hi[i]) / 2 for i in range(3)]

    def ctx(scale, base, rotation=(0, 0, 0)):
        return {"rotation": list(rotation), "translation": [round(base[i] - (c[i] - 8) * scale, 3) for i in range(3)], "scale": [scale] * 3}
    m.display["ground"] = ctx(0.4, (0, 0, 0))
    m.display["fixed"] = ctx(0.4, (0, 0, 0))
    m.display["thirdperson_righthand"] = ctx(0.32, (0, 3, 1), (0, 90, 0))
    m.display["thirdperson_lefthand"] = ctx(0.32, (0, 3, 1), (0, 90, 0))
    m.display["firstperson_righthand"] = ctx(0.28, (1, 3, 0), (0, -25, 0))
    m.display["firstperson_lefthand"] = ctx(0.28, (1, 3, 0), (0, 155, 0))
    m.save(t)


# --- component blocks -----------------------------------------------------------------------------------------------------
def launch_pad() -> Tex:
    """Concrete with a hazard-striped border two texels wide and a groove inside it."""
    img = M.tile(M.concrete(**M.CONCRETE_TILE), seed=31)
    col, row = M.grid_xy()
    ring = np.minimum(np.minimum(col, 15 - col), np.minimum(row, 15 - row))
    img[ring < 2] = M.hazard_tile(width=2, seed=32, scuff=0.2)[ring < 2]
    M.darken(img, ring == 2, 0.72)
    return M.to_tex(img)


def lattice(material: "M.Material", step: int = 4, seed: int = 0) -> Tex:
    """A see-through lattice face: a frame two texels wide, an X brace and cross bars, the rest clear; the bars shaded
    round (lit above and left, dark below and right), the brace in front lighter than the one behind."""
    col, row = M.grid_xy()
    ring = np.minimum(np.minimum(col, 15 - col), np.minimum(row, 15 - row))
    inner = (col >= 2) & (col <= 13) & (row >= 2) & (row <= 13)
    front, back = inner & (col == row), inner & (col == 15 - row)
    bars = np.zeros_like(front)
    for y in range(step, 16, step * 2):
        bars |= (row == y) & (col >= 1) & (col <= 14)
    mask = (ring < 2) | front | back | bars
    img = M.tile(material, seed)
    M.bevel(img, mask)
    M.lighten(img, front, 1.1, 4)
    M.darken(img, back & ~front, 0.82)
    img[~mask, 3] = 0
    return M.to_tex(img)


def truss(material: "M.Material", seed: int = 0) -> Tex:
    """A see-through truss face (a Warren truss): top and bottom chords, a post every half block and a diagonal in
    each bay, the two leaning opposite ways; the chords shaded round."""
    col, row = M.grid_xy()
    mask = (row < 2) | (row > 13) | (((col % 8) == 0) & (row >= 2) & (row <= 13))
    for r in range(2, 14):
        mask |= (row == r) & (col == 1 + round((13 - r) * 6 / 11))
        mask |= (row == r) & (col == 9 + round((r - 2) * 6 / 11))
    img = M.tile(material, seed)
    M.bevel(img, mask)
    M.darken(img, row > 13, 0.86)
    img[~mask, 3] = 0
    return M.to_tex(img)


def shaped_part(name: str, build, icon: Tex) -> None:
    """A component block with a real shape (not a cube) and the usual formed / unformed blockstate. `icon` is a 16x16
    face used as its particle: break particles, and the Holo-Projector draws ghost blocks as cubes of it (the packed
    model texture would look like noise there)."""
    icon.save(paths.TEXTURES / "block" / f"{name}_icon.png")
    m = build(f"groundzero:block/{name}_icon")
    write_json(paths.MODELS / "block" / f"{name}_formed.json", ST.particle_model(f"groundzero:block/{name}_icon"))
    write_json(paths.ASSETS / "blockstates" / f"{name}.json", {"variants": {
        "formed=false": {"model": f"groundzero:block/{name}"},
        "formed=true": {"model": f"groundzero:block/{name}_formed"},
    }})
    write_json(paths.ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": f"groundzero:block/{name}"}})


def radar_dish_icon() -> Tex:
    t = M.to_tex(M.tile(M.painted(M.ARMOUR_RAMP, tone=0.45), seed=5, edges=True))
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d < 6.5:
                t.px(x, y, WHITE[5] if d < 2.5 else WHITE[4] if d < 4.5 else WHITE[3])
            elif d < 7.2:
                t.px(x, y, WHITE[2])
    t.rect(7, 7, 2, 2, GUN[3])                                   # the feed horn
    return t


def antenna_icon() -> Tex:
    t = M.to_tex(M.tile(M.painted(M.ARMOUR_RAMP, tone=0.45), seed=6, edges=True))
    t.rect(7, 1, 2, 13, STEEL[4])                                # the whip and its cross bars
    for i, y in enumerate((4, 7, 10)):
        t.rect(4 + i, y, 8 - 2 * i, 1, STEEL[3])
    t.rect(5, 13, 6, 2, GUN[4])                                  # the base
    return t


def radar_dish_block(particle: str) -> BlockModel:
    m = BlockModel("radar_dish", tex_size=(64, 64), kind="block")
    m.particle = particle
    m.box("stub", (6, 0, 6), (10, 4, 10), faces=("north", "south", "east", "west", "down"))
    m.box("yoke", (3, 4, 7), (13, 6, 9))
    # The dish, tilted back, as stepped rings facing north.
    for i, (r, z) in enumerate(((7, 3), (5.5, 4), (3.5, 5))):
        m.box(f"ring{i}", (8 - r, 8 - r * 0.7 + 3, z), (8 + r, 8 + r * 0.7 + 3, z + 1),
              faces=("north", "south", "east", "west", "up", "down"))
    m.box("feed", (7.5, 10.5, 0.5), (8.5, 11.5, 3))
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    S = M.Surfacer(m, seed=73, ao_reach=2.0)
    S.paint(t, ["stub", "yoke", "feed"], M.painted(M.ARMOUR_RAMP, tone=0.55, wear=0.25))
    S.paint(t, [f"ring{i}" for i in range(3)], M.white_paint())
    m.fit_display(gui_rotation=(30, 225, 0))
    m.save(t)
    return m


def antenna_block(particle: str) -> BlockModel:
    m = BlockModel("antenna", tex_size=(32, 32), kind="block")
    m.particle = particle
    m.box("base", (5, 0, 5), (11, 2, 11), faces=("north", "south", "east", "west", "up", "down"))
    m.box("whip", (7.5, 2, 7.5), (8.5, 16, 8.5), faces=("north", "south", "east", "west", "up"))
    for i, y in enumerate((8, 11, 14)):
        w = 4 - i
        m.box(f"bar{i}", (8 - w, y, 7.7), (7.5, y + 0.6, 8.3), faces=("north", "south", "west", "up", "down"))
        m.box(f"bar{i}e", (8.5, y, 7.7), (8 + w, y + 0.6, 8.3), faces=("north", "south", "east", "up", "down"))
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    S = M.Surfacer(m, seed=79, ao_reach=2.0)
    S.paint(t, "base", M.painted(M.ARMOUR_RAMP, tone=0.55, wear=0.25))
    S.paint(t, ["whip"] + [f"bar{i}{e}" for i in range(3) for e in ("", "e")], M.steel(tone=0.62))
    m.fit_display(gui_rotation=(30, 225, 0))
    m.save(t)
    return m


def plus_ring(r_out: float, r_in: float) -> list[tuple[float, float, float, float]]:
    """The flat ring between two "plus" discs (a plus disc of radius r: |x| <= r, |z| <= 0.66 r, or the other way round),
    centred on 0, as rectangles (x0, z0, x1, z1): cut on the grid of their edges, cells merged along x."""
    def plus(r, x, z):
        w = r * 0.66
        return (abs(x) <= r and abs(z) <= w) or (abs(x) <= w and abs(z) <= r)
    cuts = sorted({round(v, 3) for rr in (r_out, r_in) if rr > 0 for v in (rr, -rr, rr * 0.66, -rr * 0.66)})
    rects = []
    for j in range(len(cuts) - 1):
        z0, z1 = cuts[j], cuts[j + 1]
        run = None
        for i in range(len(cuts) - 1):
            x0, x1 = cuts[i], cuts[i + 1]
            mx, mz = (x0 + x1) / 2, (z0 + z1) / 2
            inside = plus(r_out, mx, mz) and not (r_in > 0 and plus(r_in, mx, mz))
            if inside:
                run = (run[0], x1) if run else (x0, x1)
            elif run:
                rects.append((run[0], z0, run[1], z1))
                run = None
        if run:
            rects.append((run[0], z0, run[1], z1))
    return rects


def large_dish_icon() -> Tex:
    t = M.to_tex(M.tile(M.white_paint(tone=0.62), seed=7, edges=True))
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d <= 6.5:
                t.px(x, y, WHITE[4] if d > 4.5 else WHITE[3] if d > 2 else GUN[3])
    return t


def large_dish_block(particle: str) -> BlockModel:
    """The Large Radar Dish, drawn by its middle block across all 3x3 (x and z -16..32): a bowl lying face up, stepped
    rings rising to its rim, on a hub, a feed horn on a post over its middle."""
    m = BlockModel("large_radar_dish", tex_size=(256, 256), kind="block")
    m.particle = particle
    m.box("hub", (5, 0, 5), (11, 3, 11), faces=("north", "south", "east", "west", "down"))
    rings = [(5, 0, 3, 4.5), (10.5, 5, 3.5, 6), (16, 10.5, 4, 7.5), (21, 16, 4.5, 9), (24, 21, 5, 10)]
    names = []
    for k, (ro, ri, y0, y1) in enumerate(rings):
        for i, (x0, z0, x1, z1) in enumerate(plus_ring(ro, ri)):
            n = f"r{k}_{i}"
            m.box(n, (8 + x0, y0, 8 + z0), (8 + x1, y1, 8 + z1))
            names.append((n, k))
    m.box("post", (7.4, 4.5, 7.4), (8.6, 13, 8.6), faces=("north", "south", "east", "west"))
    m.box("horn", (6.5, 13, 6.5), (9.5, 16, 9.5))
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    S = M.Surfacer(m, seed=83, ao_reach=3.0)
    S.paint(t, ["hub", "horn"], M.painted(M.ARMOUR_RAMP, tone=0.55, wear=0.25))
    for k in range(len(rings)):
        # The bowl's face lighter toward the rim (it catches the light).
        S.paint(t, [n for n, kk in names if kk == k], M.white_paint(tone=0.6 + 0.04 * k))
    S.paint(t, "post", M.steel(tone=0.6))
    m.fit_display(gui_rotation=(30, 225, 0))
    m.save(t)
    return m


def large_dish() -> None:
    icon = large_dish_icon()
    icon.save(paths.TEXTURES / "block" / "large_radar_dish_icon.png")
    large_dish_block("groundzero:block/large_radar_dish_icon")
    write_json(paths.MODELS / "block" / "large_radar_dish_formed.json", ST.particle_model("groundzero:block/large_radar_dish_icon"))
    write_json(paths.ASSETS / "blockstates" / "large_radar_dish.json", {"variants": {
        "formed=false,part=center": {"model": "groundzero:block/large_radar_dish"},
        "formed=false,part=edge": {"model": "groundzero:block/large_radar_dish_formed"},
        "formed=true": {"model": "groundzero:block/large_radar_dish_formed"},
    }})
    write_json(paths.ASSETS / "items" / "large_radar_dish.json", {"model": {"type": "minecraft:model", "model": "groundzero:block/large_radar_dish"}})


def missile_boxes(body: str, nose: str, parts=None, noses=None) -> list[tuple[str, str, str]]:
    """A structure's missile boxes for missile_paint: (box name, kind, nose), the nose parts once for each warhead
    (named <nose prefix><warhead>_<part>, as the structures name them)."""
    out = []
    for name, _, _, _, kind in (parts if parts is not None else missile_parts()):
        if kind in NOSE_PARTS:
            out += [(f"{nose}{n}_{name}", kind, n) for n in (noses if noses is not None else BENCH_NOSES)]
        else:
            out.append((body + name, kind, "none"))
    return out


def paint_missiles(g: GeoModel, tex: Tex, glow: Tex, tail_y: float, cz: float) -> None:
    """The three missiles a heavy launcher can hold (the complex, the Missile Silo), standing with their tails at
    tail_y over z = cz: the usual missile, a Strategic one and an intercontinental one with each of its warheads."""
    S = M.Surfacer(g, seed=97)
    MP.paint(S, tex, missile_boxes("m_", "n_"), MP.Frame(4, (0, tail_y, cz)), "basic")
    MP.paint(S, tex, missile_boxes("s_", "sn_", strategic_parts()), MP.Frame(4, (0, tail_y + 4 * STRATEGIC_TAIL, cz)), "strategic")
    icbm = [("i_" + name, kind, "none") for name, _, _, _, kind in icbm_parts()]
    icbm += [(f"in_{nose}_{name}", kind, nose) for nose in ICBM_NOSES for name, _, _, _, kind in icbm_nose_parts(nose)]
    MP.paint(S, tex, icbm, MP.Frame(4, (0, tail_y + 4 * ICBM_TAIL, cz)), "icbm", glow=glow)


def missile_placard(t: Tex) -> None:
    t.rect(4, 9, 4, 4, P.BLACK_PAINT[1])
    t.vline(6, 9, 4, WHITE[5])
    t.px(6, 9, RED[4])
    t.px(5, 12, STEEL[4])
    t.px(7, 12, STEEL[4])


def radar_placard(t: Tex) -> None:
    t.rect(4, 9, 4, 4, P.BLACK_PAINT[1])
    t.px(5, 11, GREEN[4])
    t.px(6, 10, GREEN[4])
    t.px(7, 9, GREEN[5])
    t.px(5, 12, GREEN[2])


# --- the formed Launch Silo ---------------------------------------------------------------------------------------------
def silo() -> None:
    g = GeoModel("launch_silo", tex_size=(256, 512))
    g.bone("frame")
    g.bone("arm", pivot=(24, 101.5, 16), parent="frame")        # on its hinge pin, just off the tower's face
    g.bone("missile", pivot=(0, 14, 16), parent="frame")         # its tail on the pad: the body's size scales it from there
    for nose in BENCH_NOSES:
        g.bone(f"nose_{nose}", parent="missile")
    g.exclusive = [{f"nose_{nose}" for nose in BENCH_NOSES}]
    F = "frame"
    pad = [("pad_front", (-24, 0, -8), (24, 14, 10)), ("pad_back", (-24, 0, 22), (24, 14, 40)),
           ("pad_left", (-24, 0, 10), (-6, 14, 22)), ("pad_right", (6, 0, 10), (24, 14, 22))]
    for n, a, b in pad:
        g.box(n, a, b, bone=F)
    g.box("grate", (-6, 6, 10), (6, 7, 22), faces=("up", "down"), bone=F)
    posts = []
    for i, (x, z) in enumerate(((-10.5, 5.5), (8.5, 5.5), (-10.5, 24.5), (8.5, 24.5))):
        posts.append(f"hold{i}")
        g.box(posts[-1], (x, 14, z), (x + 2, 19, z + 2), faces=("north", "south", "east", "west", "up"), bone=F)
    g.box("console", (-5, 14, -7), (5, 22, -2), faces=("north", "south", "east", "west", "up"), bone=F)
    g.box("housing", (-5, 14, 35), (5, 19, 40), faces=("north", "south", "east", "west", "up"), bone=F)
    tower = []
    for i, (x, z) in enumerate(((25, 9), (37, 9), (25, 21), (37, 21))):
        tower.append(f"post{i}")
        g.box(tower[-1], (x, 0, z), (x + 2, 140, z + 2), faces=("north", "south", "east", "west"), bone=F)
    girders = []
    for k in range(1, 9):
        y = 16 * k - 1
        for n, a, b in ((f"gn{k}", (27, y, 9.3), (37, y + 1.5, 10.7)), (f"gs{k}", (27, y, 21.3), (37, y + 1.5, 22.7)),
                        (f"gw{k}", (25.3, y, 11), (26.7, y + 1.5, 21)), (f"ge{k}", (37.3, y, 11), (38.7, y + 1.5, 21))):
            g.box(n, a, b, faces=("north", "south", "east", "west", "up", "down"), bone=F)
            girders.append(n)
    g.box("platform", (24, 140, 8), (40, 142, 24), bone=F)
    g.box("arm_beam", (10.9, 100, 14), (23.5, 103, 18), bone="arm")
    g.box("arm_clamp", (9.4, 98, 12), (10.9, 106, 20), faces=("west", "north", "south", "up", "down"), bone="arm")
    # The arm's hinge: a spine up the tower's face, two knuckles off it and the pin the arm turns on.
    hinge = ["hinge_spine", "hinge_top", "hinge_bottom", "hinge_pin"]
    # (In these GeckoLib models "west" is the +x face, toward the tower, and "east" the face toward the missile.)
    g.box("hinge_spine", (24.6, 0, 15), (25.3, 140, 17), faces=("east", "north", "south", "up"), bone=F)
    g.box("hinge_top", (22.5, 103.5, 14.5), (24.6, 105.5, 17.5), faces=("east", "north", "south", "up", "down"), bone=F)
    g.box("hinge_bottom", (22.5, 97.5, 14.5), (24.6, 99.5, 17.5), faces=("east", "north", "south", "up", "down"), bone=F)
    g.box("hinge_pin", (23.5, 96.5, 15.5), (24.5, 106.5, 16.5), faces=("west", "north", "south", "east", "up", "down"), bone=F)
    # The missile, standing on the pad: the item model four times over (8 blocks), its tail on the pad's top; the
    # gantry arm holds its body below the nose.
    for name, frm, to, faces, kind in missile_parts():
        a = ((frm[0] - 8) * 4, frm[1] * 4 + 14, (frm[2] - 8) * 4 + 16)
        b = ((to[0] - 8) * 4, to[1] * 4 + 14, (to[2] - 8) * 4 + 16)
        if kind in NOSE_PARTS:
            for nose in BENCH_NOSES:
                g.box(f"n_{nose}_{name}", a, b, faces=faces, bone=f"nose_{nose}")
        else:
            g.box("m_" + name, a, b, faces=faces, bone="missile")
    g.layout()
    tex = Tex(g.tex_w, g.tex_h)

    paint_silo(g, tex, [n for n, _, _ in pad], posts, tower, girders, hinge)
    MP.paint(M.Surfacer(g, seed=97), tex, missile_boxes("m_", "n_"), MP.Frame(4, (0, 14, 16)), "basic")
    g.save(tex)
    # The arm swings away from the missile during the countdown and stays away.
    count = Animation("working", 10.0, loop="hold_on_last_frame")
    # (75 degrees: swung any further it would pass through the tower's corner post.)
    count.rotate("arm", {0: (0, 0, 0), 2.5: (0, -75, 0), 10: (0, -75, 0)}, easing="easeInOutSine")
    print(save_animations("launch_silo", [count]))


# --- the formed Launch Complex (Phase 8) ---------------------------------------------------------------------------------


def flame_deflector() -> Tex:
    """The deflector block: heavy steel plates blackened by exhaust, heat-tinted (straw, then blue) along their welds,
    a hazard edge along the top."""
    f = M.tile_face()
    img = M.Material(ramp=M.STEEL_RAMP[:7], tone=0.5, mottle=0.06, grain=0.05, dither=0.7, despeckle=False).render(f, 17)
    img = np.clip(img, 0, 255).astype(np.uint8)
    M.sooted(f, img, 0.35 + 0.5 * M.noise(f, 6.0, 19, 2), 0.6)
    col, row = M.grid_xy()
    for y in (3, 7, 11, 15):
        img[row == y, :3] = (22, 23, 26)                          # the weld between two plates
        if y + 1 < 16:                                           # heat-blued just below it, here and there
            tint = (row == y + 1) & (M.noise(f, (5.0, 16.0, 1.0), 29 + y) > 0.4)
            img[tint, :3] = np.clip(img[tint, :3].astype(np.float32) * 0.55 + np.array([58, 66, 104]) * 0.45, 0, 255)
    top = (row == 0)
    img[top] = M.hazard_tile(width=2, seed=18)[top]
    M.darken(img, ((col == 0) | (col == 15) | (row == 15)) & (row > 0), 0.7)
    return M.to_tex(img)


def complex_placard(t: Tex) -> None:
    """The controller's placard: a missile on a yellow radiation tag."""
    t.rect(4, 9, 4, 4, YELLOW[4])
    t.vline(6, 9, 4, P.BLACK_PAINT[1])
    t.px(5, 12, P.BLACK_PAINT[1])
    t.px(7, 12, P.BLACK_PAINT[1])


def launch_complex() -> None:
    g = GeoModel("launch_complex", tex_size=(512, 1024))
    g.bone("frame")
    g.bone("arm_low", pivot=(38, 72, 32), parent="frame")         # each on its hinge pin, just off the tower's face
    g.bone("arm_high", pivot=(38, 136, 32), parent="frame")
    # Each arm's clamp, one for each airframe (LaunchComplexBlockEntity shows the loaded missile's): the lower at a usual
    # missile's body and a Strategic first stage, or out at an intercontinental first stage; the upper at a usual
    # missile's body, in to a Strategic second stage, or to an intercontinental third stage.
    g.bone("clamp_low", parent="arm_low")
    g.bone("clamp_low_icbm", parent="arm_low")
    g.bone("clamp_high", parent="arm_high")
    g.bone("clamp_high_strategic", parent="arm_high")
    g.bone("clamp_high_icbm", parent="arm_high")
    g.bone("missile", pivot=(0, 14, 32), parent="frame")       # its tail over the trench: the body's size scales it from there
    for nose in BENCH_NOSES:
        g.bone(f"nose_{nose}", parent="missile")
    g.bone("strategic", pivot=(0, 14, 32), parent="frame")     # a Strategic missile: its own model, not scaled
    for nose in BENCH_NOSES:
        g.bone(f"snose_{nose}", parent="strategic")
    g.bone("icbm", pivot=(0, 14, 32), parent="frame")          # an intercontinental missile: its own model too
    for nose in ICBM_NOSES:
        g.bone(f"inose_{nose}", parent="icbm")
    g.exclusive = [{f"nose_{nose}" for nose in BENCH_NOSES}, {f"snose_{nose}" for nose in BENCH_NOSES}, {f"inose_{nose}" for nose in ICBM_NOSES},
                   {"missile", "strategic", "icbm"}, {"clamp_low", "clamp_low_icbm"}, {"clamp_high", "clamp_high_strategic", "clamp_high_icbm"}]
    F = "frame"
    # The deck: 5x5 with a flame trench (z 26-38) running from under the missile out of its west edge.
    deck = [("deck_front", (-40, 0, -8), (40, 14, 26), ("north", "south", "east", "west", "up")),
            ("deck_back", (-40, 0, 38), (40, 14, 72), ("north", "south", "east", "west", "up")),
            ("deck_east", (8, 0, 26), (40, 14, 38), ("east", "west", "up"))]
    for n, a, b, faces in deck:
        g.box(n, a, b, faces=faces, bone=F)
    g.box("trench_floor", (-40, 0, 26), (8, 2, 38), faces=("up", "west"), bone=F)
    wedge = []
    for i, (x0, y0, y1) in enumerate(((-8, 2, 5), (-4, 5, 8), (0, 8, 11))):
        wedge.append(f"wedge{i}")
        g.box(wedge[-1], (x0, y0, 26), (8, y1, 38), faces=("up", "west"), bone=F)
    # Deluge pipes along both lips of the trench, nozzles pointing in.
    pipes = []
    for n, z0 in (("deluge_n", 23.5), ("deluge_s", 38)):
        pipes.append(n)
        g.box(n, (-39, 14, z0), (7, 16.5, z0 + 2.5), faces=("north", "south", "west", "up"), bone=F)
    # Hold-down posts on the diagonals round the missile (clear of its fins).
    posts = []
    for i, (x, z) in enumerate(((-14, 18), (12, 18), (-14, 44), (12, 44))):
        posts.append(f"hold{i}")
        g.box(posts[-1], (x, 14, z), (x + 2, 20, z + 2), faces=("north", "south", "east", "west", "up"), bone=F)
    g.box("console", (-5, 14, -7), (5, 22, -2), faces=("north", "south", "east", "west", "up"), bone=F)
    g.box("housing", (-5, 14, 67), (5, 19, 72), faces=("north", "south", "east", "west", "up"), bone=F)
    # The service tower: column 5, rows 2-3 (x 40-56, z 24-56), 13 high.
    tower = []
    for i, (x, z) in enumerate(((40, 24), (54, 24), (40, 54), (54, 54))):
        tower.append(f"post{i}")
        g.box(tower[-1], (x, 0, z), (x + 2, 208, z + 2), faces=("north", "south", "east", "west"), bone=F)
    girders = []
    for k in range(1, 13):
        if k in (4, 8, 12):
            continue                                     # the platform decks are the frame at these storeys
        y = 16 * k - 1
        for n, a, b in ((f"gn{k}", (42, y, 24.3), (54, y + 1.5, 25.7)), (f"gs{k}", (42, y, 54.3), (54, y + 1.5, 55.7)),
                        (f"gw{k}", (40.3, y, 26), (41.7, y + 1.5, 54)), (f"ge{k}", (54.3, y, 26), (55.7, y + 1.5, 54))):
            g.box(n, a, b, faces=("north", "south", "east", "west", "up", "down"), bone=F)
            girders.append(n)
    # Diagonal braces down the outer (east) face: short steps from corner to corner of each storey.
    braces = []
    for k in range(13):
        for s in range(4):
            z = 26 + s * 7 if k % 2 == 0 else 47 - s * 7
            braces.append(f"br{k}_{s}")
            g.box(braces[-1], (54.6, 16 * k + 2 + s * 3.5, z), (55.4, 16 * k + 5.5 + s * 3.5, z + 7), faces=("east", "west", "up", "down"), bone=F)
    decks = []
    for y in (63, 127, 191):
        decks.append(f"level{y}")
        g.box(decks[-1], (39, y, 23), (57, y + 2, 57), bone=F)
    rails = []
    for y in (65, 129, 193):
        rails.append(f"rail{y}")
        g.box(rails[-1], (56.5, y, 23), (57, y + 1, 57), faces=("east", "west", "up"), bone=F)
    g.box("roof", (39, 208, 23), (57, 210, 57), bone=F)
    g.box("mast", (47, 210, 39), (49, 226, 41), faces=("north", "south", "east", "west"), bone=F)
    g.box("beacon", (46.5, 226, 38.5), (49.5, 229, 41.5), bone=F)
    # The umbilical arms: a beam from the hinge to a clamp on the missile's body, a hose slung under it; each turns on
    # a pin between two knuckles off a spine up the tower's face. The lower clamp meets a Strategic first stage (radius
    # 9.6) and every other missile's body; the upper one has two clamps (see the bones above).
    arms = []
    hinges = ["spine"]
    g.box("spine", (39.4, 14, 30.5), (40.3, 208, 33.5), faces=("east", "north", "south", "up"), bone=F)
    for bone, y, reach in (("arm_low", 70, 9.6), ("arm_high", 134, 10.0)):
        inner = 14 if bone == "arm_low" else reach + 2   # the lower beam stops short of an intercontinental first stage
        for n, a, b, faces in ((f"{bone}_beam", (inner, y, 30), (37.5, y + 4, 34), ("north", "south", "up", "down", "west")),
                               (f"{bone}_hose", (inner + 0.5, y - 3, 31), (36.5, y - 1, 33), ("north", "south", "down", "west", "east"))):
            g.box(n, a, b, faces=faces, bone=bone)
            arms.append(n)
        clamp_bone = "clamp_high" if bone == "arm_high" else "clamp_low"
        g.box(f"{bone}_clamp", (reach, y - 2, 27), (reach + 2, y + 6, 37), faces=("west", "north", "south", "up", "down"), bone=clamp_bone)
        arms.append(f"{bone}_clamp")
        for n, a, b, faces in ((f"{bone}_knuckle_top", (36.5, y + 4.5, 30), (39.4, y + 6.5, 34), ("east", "north", "south", "up", "down")),
                               (f"{bone}_knuckle_bottom", (36.5, y - 2.5, 30), (39.4, y - 0.5, 34), ("east", "north", "south", "up", "down")),
                               (f"{bone}_pin", (37.5, y - 3.5, 31.5), (38.5, y + 7.5, 32.5), ("west", "north", "south", "east", "up", "down"))):
            g.box(n, a, b, faces=faces, bone=F)
            hinges.append(n)
    # The Strategic clamp: in to the second stage (radius 8), the beam carried on to it.
    g.box("arm_high_reach", (10, 134, 30), (12, 138, 34), faces=("north", "south", "up", "down"), bone="clamp_high_strategic")
    g.box("arm_high_clamp_strategic", (8, 132, 27), (10, 140, 37), faces=("west", "north", "south", "up", "down"),
          bone="clamp_high_strategic")
    arms += ["arm_high_reach", "arm_high_clamp_strategic"]
    # The lower clamp's reach out to the beam; the intercontinental clamps: the lower on the first stage (radius 12), the
    # upper reaching in to the third (radius 8.8).
    g.box("arm_low_reach", (11.6, 70, 30), (14, 74, 34), faces=("north", "south", "up", "down"), bone="clamp_low")
    g.box("arm_low_clamp_icbm", (12, 68, 27), (14, 76, 37), faces=("west", "north", "south", "up", "down"), bone="clamp_low_icbm")
    g.box("arm_high_reach_icbm", (10.8, 134, 30), (12, 138, 34), faces=("north", "south", "up", "down"), bone="clamp_high_icbm")
    g.box("arm_high_clamp_icbm", (8.8, 132, 27), (10.8, 140, 37), faces=("west", "north", "south", "up", "down"), bone="clamp_high_icbm")
    arms += ["arm_low_reach", "arm_low_clamp_icbm", "arm_high_reach_icbm", "arm_high_clamp_icbm"]
    # The missile: the item model four times over (8 blocks), standing over the trench.
    for name, frm, to, faces, kind in missile_parts():
        a = ((frm[0] - 8) * 4, frm[1] * 4 + 14, (frm[2] - 8) * 4 + 32)
        b = ((to[0] - 8) * 4, to[1] * 4 + 14, (to[2] - 8) * 4 + 32)
        if kind in NOSE_PARTS:
            for nose in BENCH_NOSES:
                g.box(f"n_{nose}_{name}", a, b, faces=faces, bone=f"nose_{nose}")
        else:
            g.box("m_" + name, a, b, faces=faces, bone="missile")
    # Or a Strategic missile: its own model four times over (about 11 blocks), its tail on the same line.
    for name, frm, to, faces, kind in strategic_parts():
        a = ((frm[0] - 8) * 4, (frm[1] + STRATEGIC_TAIL) * 4 + 14, (frm[2] - 8) * 4 + 32)
        b = ((to[0] - 8) * 4, (to[1] + STRATEGIC_TAIL) * 4 + 14, (to[2] - 8) * 4 + 32)
        if kind in NOSE_PARTS:
            for nose in BENCH_NOSES:
                g.box(f"sn_{nose}_{name}", a, b, faces=faces, bone=f"snose_{nose}")
        else:
            g.box("s_" + name, a, b, faces=faces, bone="strategic")
    # Or an intercontinental missile: its own model four times over (about 11 blocks), its tail on the same line; each
    # warhead's nose its own bone.
    def icbm_box(frm, to):
        return (((frm[0] - 8) * 4, (frm[1] + ICBM_TAIL) * 4 + 14, (frm[2] - 8) * 4 + 32),
                ((to[0] - 8) * 4, (to[1] + ICBM_TAIL) * 4 + 14, (to[2] - 8) * 4 + 32))
    for name, frm, to, faces, kind in icbm_parts():
        g.box("i_" + name, *icbm_box(frm, to), faces=faces, bone="icbm")
    for nose in ICBM_NOSES:
        for name, frm, to, faces, kind in icbm_nose_parts(nose):
            g.box(f"in_{nose}_{name}", *icbm_box(frm, to), faces=faces, bone=f"inose_{nose}")
    g.layout()
    tex = Tex(g.tex_w, g.tex_h)

    paint_complex(g, tex, [n for n, _, _, _ in deck], wedge, pipes, posts, tower, girders, braces, decks, rails, hinges)
    glow = Tex(g.tex_w, g.tex_h, CLEAR)                       # the Tsar's vents (the renderer draws it lit)
    paint_missiles(g, tex, glow, 14, 32)
    g.save(tex, glow)
    # The countdown (15 s): the lower umbilical swings away at T-13, the upper one at T-9; both stay away. The clip runs
    # 20 s, past the countdown and the ignition smoke while the complex is still working (the machine loops it), and
    # an empty complex rests in "clear" with both arms still away.
    count = Animation("working", 20.0, loop="hold_on_last_frame")
    # (75 degrees: swung any further they would pass through the tower's corner posts.)
    count.rotate("arm_low", {0: (0, 0, 0), 2: (0, 0, 0), 4.5: (0, -75, 0), 20: (0, -75, 0)}, easing="easeInOutSine")
    count.rotate("arm_high", {0: (0, 0, 0), 6: (0, 0, 0), 8.5: (0, -75, 0), 20: (0, -75, 0)}, easing="easeInOutSine")
    clear = Animation("clear", 1.0, loop="hold_on_last_frame")
    clear.rotate("arm_low", {0: (0, -75, 0), 1: (0, -75, 0)})
    clear.rotate("arm_high", {0: (0, -75, 0), 1: (0, -75, 0)})
    print(save_animations("launch_complex", [count, clear]))


# --- the formed Launch Computer -----------------------------------------------------------------------------------------
def computer() -> None:
    g = GeoModel("launch_computer", tex_size=(256, 256))
    g.bone("frame")
    g.bone("dish", pivot=(0, 52, 16), parent="frame")
    g.bone("dish_tilt", pivot=(0, 54, 16), parent="dish", rotation=(40, 0, 0))     # looking well up at the sky
    F = "frame"
    g.box("desk", (-7, 0, -7), (23, 10, 7), bone=F)
    g.box("console", (-7, 10, -7), (23, 12, 0), faces=("north", "east", "west", "up"), bone=F)
    # The screens stand on the controller half of the desk, in front of the mast; the antenna on the far half, where
    # nothing the same colour stands behind it.
    g.box("crt_l", (-6.5, 10, 0), (2, 20, 6), faces=("north", "south", "east", "west", "up"), bone=F)
    g.box("crt_r", (2.5, 10, 0), (11, 20, 6), faces=("north", "south", "east", "west", "up"), bone=F)
    g.box("socket", (23, 2, -5), (24.5, 9, 5), faces=("north", "south", "east", "up", "down"), bone=F)
    g.box("ant_base", (16, 10, 1), (20, 12, 5), faces=("north", "south", "east", "west", "up"), bone=F)
    g.box("ant_whip", (17.6, 12, 2.4), (18.4, 40, 3.6), faces=("north", "south", "east", "west", "up"), bone=F)
    bars = []
    for i, y in enumerate((28, 32, 36)):
        w = 5 - i
        bars.append(f"ant_bar{i}")
        g.box(bars[-1], (18 - w, y, 2.7), (17.6, y + 0.6, 3.3), faces=("north", "south", "west", "up", "down"), bone=F)
        bars.append(f"ant_bar{i}e")
        g.box(bars[-1], (18.4, y, 2.7), (18 + w, y + 0.6, 3.3), faces=("north", "south", "east", "up", "down"), bone=F)
    mast = []
    for i, (x, z) in enumerate(((-6, 10), (4, 10), (-6, 20), (4, 20))):
        mast.append(f"mpost{i}")
        g.box(mast[-1], (x, 0, z), (x + 2, 48, z + 2), faces=("north", "south", "east", "west"), bone=F)
    for k in range(1, 6):
        y = 8 * k
        for n, a, b in ((f"mn{k}", (-4, y, 10.4), (4, y + 1, 11.6)), (f"ms{k}", (-4, y, 20.4), (4, y + 1, 21.6)),
                        (f"mw{k}", (-5.6, y, 12), (-4.4, y + 1, 20)), (f"me{k}", (4.4, y, 12), (5.6, y + 1, 20))):
            g.box(n, a, b, faces=("north", "south", "east", "west", "up", "down"), bone=F)
            mast.append(n)
    g.box("mast_cap", (-6, 48, 10), (6, 50, 22), bone=F)
    g.box("yoke", (-2, 50, 14), (2, 53, 18), faces=("north", "south", "east", "west", "up"), bone="dish")
    rings = []
    for i, (r, z) in enumerate(((12, 9), (9, 10), (6, 11), (3, 12))):
        w = round(r * 0.66, 2)
        for part, a, b, faces in ((f"d{i}c", (-r, 54 - w, z), (r, 54 + w, z + 1), ("north", "south", "east", "west")),
                                  (f"d{i}u", (-w, 54 + w, z), (w, 54 + r, z + 1), ("north", "south", "east", "west", "up")),
                                  (f"d{i}d", (-w, 54 - r, z), (w, 54 - w, z + 1), ("north", "south", "east", "west", "down"))):
            g.box(part, a, b, faces=faces, bone="dish_tilt")
            rings.append(part)
    g.box("back", (-2.5, 51.5, 13), (2.5, 56.5, 14.5), faces=("south", "east", "west", "up", "down"), bone="dish_tilt")
    g.box("feed_arm", (-0.4, 53.6, 3), (0.4, 54.4, 9), faces=("east", "west", "up", "down"), bone="dish_tilt")
    g.box("feed", (-1.2, 52.8, 1.5), (1.2, 55.2, 3), bone="dish_tilt")
    g.layout()
    tex = Tex(g.tex_w, g.tex_h)
    glow = Tex(g.tex_w, g.tex_h)

    paint_computer(g, tex, glow, bars, mast, rings)
    g.save(tex, glow)
    spin = Animation("working", 4.0, loop=True)
    spin.rotate("dish", {0: (0, 0, 0), 4: (0, 360, 0)})
    print(save_animations("launch_computer", [spin]))


# --- the formed Radar Array ---------------------------------------------------------------------------------------------
def radar_placard_big(t: Tex) -> None:
    """The Radar Array controller's placard: a dish's arc over a blip."""
    t.rect(4, 9, 5, 4, P.BLACK_PAINT[1])
    for x, y in ((4, 9), (5, 10), (6, 11), (7, 11), (8, 10)):
        t.px(x, y, STEEL[5])
    t.px(6, 12, RED[4])


def radar_array() -> None:
    """A 3x3 deck (the controller's cabinet at the front, the power box at the back), a lattice mast three blocks up
    from its middle, and on top a big dish (3 blocks across) that turns on its yoke, looking up at the sky; a red
    warning lamp on the mast cap."""
    g = GeoModel("radar_array", tex_size=(256, 256))
    g.bone("frame")
    g.bone("dish", pivot=(0, 68, 16), parent="frame")
    g.bone("dish_tilt", pivot=(0, 76, 16), parent="dish", rotation=(30, 0, 0))
    F = "frame"
    g.box("deck", (-23, 0, -7), (23, 6, 39), bone=F)
    g.box("cabinet", (-7, 6, -6), (7, 20, 0), faces=("north", "south", "east", "west", "up"), bone=F)
    g.box("powerbox", (-5, 6, 31), (5, 15, 36), faces=("north", "south", "east", "west", "up"), bone=F)
    rails = []
    for i, (x, z) in enumerate(((-22, -6), (21, -6), (-22, 37), (21, 37))):
        rails.append(f"rpost{i}")
        g.box(rails[-1], (x, 6, z), (x + 1, 13, z + 1), faces=("north", "south", "east", "west", "up"), bone=F)
    for n, a, b in (("rail_w", (-22, 12, -5), (-21, 13, 37)), ("rail_e", (21, 12, -5), (22, 13, 37)),
                    ("rail_s", (-21, 12, 37), (21, 13, 38))):
        g.box(n, a, b, faces=("north", "south", "east", "west", "up", "down"), bone=F)
        rails.append(n)
    mast = []
    for i, (x, z) in enumerate(((-6, 10), (4, 10), (-6, 20), (4, 20))):
        mast.append(f"mpost{i}")
        g.box(mast[-1], (x, 6, z), (x + 2, 66, z + 2), faces=("north", "south", "east", "west"), bone=F)
    for k in range(1, 8):
        y = 6 + 8 * k
        for n, a, b in ((f"mn{k}", (-4, y, 10.4), (4, y + 1, 11.6)), (f"ms{k}", (-4, y, 20.4), (4, y + 1, 21.6)),
                        (f"mw{k}", (-5.6, y, 12), (-4.4, y + 1, 20)), (f"me{k}", (4.4, y, 12), (5.6, y + 1, 20))):
            g.box(n, a, b, faces=("north", "south", "east", "west", "up", "down"), bone=F)
            mast.append(n)
    g.box("mast_cap", (-7, 66, 9), (7, 68, 23), bone=F)
    g.box("lamp", (6, 60, 20), (7.5, 62.5, 22), faces=("north", "south", "east", "up", "down"), bone=F)   # on the mast
    g.box("yoke", (-3, 68, 13), (3, 73, 19), faces=("north", "south", "east", "west", "up"), bone="dish")
    g.box("yoke_arm_w", (-5, 70, 14), (-3, 78, 18), faces=("north", "south", "east", "west", "up"), bone="dish")
    g.box("yoke_arm_e", (3, 70, 14), (5, 78, 18), faces=("north", "south", "east", "west", "up"), bone="dish")
    rings = []
    for i, (rr, z) in enumerate(((22, 6), (17, 7.5), (11, 9), (5, 10.5))):
        w = round(rr * 0.66, 2)
        for part, a, b, faces in ((f"d{i}c", (-rr, 76 - w, z), (rr, 76 + w, z + 1.5), ("north", "south", "east", "west")),
                                  (f"d{i}u", (-w, 76 + w, z), (w, 76 + rr, z + 1.5), ("north", "south", "east", "west", "up")),
                                  (f"d{i}d", (-w, 76 - rr, z), (w, 76 - w, z + 1.5), ("north", "south", "east", "west", "down"))):
            g.box(part, a, b, faces=faces, bone="dish_tilt")
            rings.append(part)
    g.box("dish_back", (-6, 70, 12), (6, 82, 14), faces=("south", "east", "west", "up", "down"), bone="dish_tilt")
    g.box("feed_arm", (-0.5, 75.5, -6), (0.5, 76.5, 6), faces=("east", "west", "up", "down"), bone="dish_tilt")
    g.box("feed", (-2, 74, -9), (2, 78, -6), bone="dish_tilt")
    g.layout()
    tex = Tex(g.tex_w, g.tex_h)
    glow = Tex(g.tex_w, g.tex_h)

    paint_radar(g, tex, glow, rails, mast, rings)
    g.save(tex, glow)
    spin = Animation("working", 4.8, loop=True)                 # one turn every 4.8 s, as its screen's sweep
    spin.rotate("dish", {0: (0, 0, 0), 4.8: (0, 360, 0)})
    print(save_animations("radar_array", [spin]))


# --- surfaces (the remaster after slice 9b: tools/gz/materials.py, DESIGN.md §4.3) --------------------------------------
GANTRY_RAMP = M.ramp("#2a0904", "#3a0e06", "#64180a", "#8e2410", "#b83418", "#d4502a", "#e67a52", "#f4ac8c", "#fbd2bd")
SIDES = ("north", "south", "east", "west")


def _hazard_posts(S, tex, posts, top: float) -> None:
    """Hold-down posts: yellow, worn, their tops black."""
    S.paint(tex, posts, M.painted(M.YELLOW_PAINT_RAMP, tone=0.6, wear=0.45))
    black = M.painted(M.BLACK_RAMP, tone=0.5, wear=0.3)
    S.overlay(tex, posts, lambda f, img: M.paste(img, S.material_on(f, black), (f.Y > top) if f.vertical else np.ones(f.shape, bool)))


def _console(S, g, tex, name: str, lamps=(RED, GREEN)) -> None:
    """An olive console with its screen and two lamps on its front."""
    S.paint(tex, name, M.painted(M.OLIVE_RAMP, tone=0.55, wear=0.3))

    def front(t: Tex, r, d):
        x, y, w, h = r
        if d == "north" and w > 6:
            t.rect(x + 2, y + 1, w - 4, 4, P.BLACK_PAINT.deep)
            for xx in range(x + 2, x + w - 2):
                t.px(xx, y + 3 + (xx % 2), GREEN[4])
            t.px(x + 2, y + h - 2, lamps[0][4])
            t.px(x + 4, y + h - 2, lamps[1][4])
    g.paint_all(tex, name, front)


def _power_housing(S, g, tex, name: str, plug: str | None = "south") -> None:
    """The Energy Port's housing: dark steel, a red-and-black band round its top, its socket on one face."""
    S.paint(tex, name, M.painted(M.ARMOUR_RAMP, tone=0.55, wear=0.3, rust=0.08))
    band = M.warning_stripes(width=2)
    S.overlay(tex, name, lambda f, img: M.paste(img, S.material_on(f, band), f.border["top"] < 3), only=SIDES)

    def socket(t: Tex, r, d):
        x, y, w, h = r
        if d == plug and w > 4:
            t.rect(x + w // 2 - 2, y + 2, 4, 3, GUN[4])
            t.rect(x + w // 2 - 1, y + 3, 2, 1, BLACK[1])
    if plug:
        g.paint_all(tex, name, socket)


def _grating(S, tex, names, toe) -> None:
    """Galvanised walkway grating, its toe boards painted."""
    S.paint(tex, names, M.galvanized(tone=0.6))
    S.overlay(tex, names, lambda f, img: M.grid(f, img, every=2, depth=0.5), only=("up", "down"))
    S.paint(tex, names, M.painted(toe, tone=0.58, wear=0.35), only=SIDES)


def _apron(cx: float, cz: float, half: float, reach: float, seed: int, trench=None):
    """How scorched a launch apron is round its flame hole (a square of half-size {half} at cx, cz), or along its trench
    (x0, x1, z0, z1): heaviest at the lip, gone {reach} pixels out, patchy."""
    def amount(f):
        if trench is None:
            d = np.maximum(np.abs(f.X - cx), np.abs(f.Z - cz)) - half
        else:
            x0, x1, z0, z1 = trench
            dx = np.maximum(0, np.maximum(x0 - f.X, f.X - x1))
            dz = np.maximum(0, np.maximum(z0 - f.Z, f.Z - z1))
            d = np.hypot(dx, dz)
        # Concentric with the source, its boundaries ragged (noise moves them in and out), not blotches of its own.
        d = d + (M.fbm(f.X, f.Y, f.Z, 3.0, seed, 3) - 0.5) * reach * 0.6
        return np.clip(1 - d / reach, 0, 1) ** 1.1
    return amount


def paint_silo(g: GeoModel, tex: Tex, pad, posts, tower, girders, hinge) -> None:
    """The Launch Silo's surfaces: a concrete pad scorched round its flame hole, hazard bands round its edge and the
    hole; a red gantry, its platform galvanised grating; a dark steel grate over the hole."""
    S = M.Surfacer(g, seed=61)
    S.paint(tex, pad, M.concrete(tone=0.6))
    band = M.stripes(width=3, wear=0.4)
    scorch = _apron(0, 16, 6, 10, 7)

    def apron(f, img):
        hole = np.maximum(np.abs(f.X), np.abs(f.Z - 16))
        M.paste(img, S.material_on(f, band), (np.abs(f.X) > 21) | (f.Z < -5) | (f.Z > 37) | (hole < 8))
        M.sooted(f, img, scorch(f), 0.62)
    S.overlay(tex, pad, apron, only=("up",))
    S.paint(tex, "grate", M.steel(tone=0.32, rust=0.2))
    S.overlay(tex, "grate", lambda f, img: (M.darken(img, (np.floor(f.X) % 2) == 1, 0.25), M.sooted(f, img, np.full(f.shape, 0.6), 0.5)))
    _hazard_posts(S, tex, posts, top=17)
    _console(S, g, tex, "console")
    _power_housing(S, g, tex, "housing")
    red = M.painted(GANTRY_RAMP, tone=0.6, wear=0.18, rust=0.08, streaks=0.05)
    S.paint(tex, tower + girders + ["hinge_spine", "arm_beam"], red)
    _grating(S, tex, ["platform"], GANTRY_RAMP)
    S.paint(tex, ["arm_clamp"] + [n for n in hinge if n != "hinge_spine"], M.painted(M.ARMOUR_RAMP, tone=0.58, wear=0.3))


def paint_complex(g: GeoModel, tex: Tex, deck, wedge, pipes, posts, tower, girders, braces, decks, rails, hinges) -> None:
    """The Launch Complex's surfaces: a concrete deck cut into slabs round the missile, scorched along its trench and
    banded round its edge; a sooted trench and deflector; galvanised deluge pipes; a grey tower with galvanised
    walkways and orange toe boards and rails; orange umbilical arms."""
    S = M.Surfacer(g, seed=53)
    S.paint(tex, deck, M.concrete(tone=0.6))
    band = M.stripes(width=3, wear=0.4)
    scorch = _apron(0, 0, 0, 16, 11, trench=(-40, 8, 26, 38))

    def apron(f, img):
        joint = (np.abs(np.abs(f.X) - 24.5) < 0.25) | (np.abs(np.abs(f.Z - 32) - 24.5) < 0.25)
        M.darken(img, joint, 0.62)
        M.paste(img, S.material_on(f, band), (np.abs(f.X) > 37) | (f.Z < -5) | (f.Z > 69))
        M.sooted(f, img, scorch(f), 0.62)
    S.overlay(tex, deck, apron, only=("up",))
    S.paint(tex, "trench_floor", M.soot(tone=0.25))
    S.paint(tex, wedge, M.soot(tone=0.45, edge_light=0.3))

    def weld(f, img):                                          # heat-blued welds along the deflector's steps
        lip = (f.border["left"] < 1) if f.d == "up" else (f.border["top"] < 1)
        img[lip, :3] = (70, 82, 120)
    S.overlay(tex, wedge, weld)
    S.paint(tex, pipes, M.galvanized(tone=0.62, rust=0.12))

    def nozzles(t: Tex, r, d):
        x, y, w, h = r
        if d in ("north", "south") and w > 4:
            for xx in range(x + 2, x + w, 5):
                t.px(xx, y + h // 2, P.CHERENKOV[4])
                t.px(xx, y + h // 2 + 1, P.BLACK_PAINT.deep)
    for n in pipes:
        g.paint_all(tex, n, nozzles)
    _hazard_posts(S, tex, posts, top=18)
    _console(S, g, tex, "console", lamps=(RED, YELLOW))
    _power_housing(S, g, tex, "housing", plug=None)
    grey = M.painted(M.GREY_PAINT_RAMP, tone=0.58, wear=0.15, rust=0.08, streaks=0.05)
    S.paint(tex, tower + girders + braces + ["mast", "spine"], grey)
    _grating(S, tex, decks + ["roof"], M.ORANGE_PAINT_RAMP)
    S.paint(tex, rails, M.painted(M.ORANGE_PAINT_RAMP, tone=0.6, wear=0.3))
    g.paint_all(tex, "beacon", lambda t, r, d: t.rect(*r, RED[5] if d != "down" else RED[3]))
    orange = M.painted(M.ORANGE_PAINT_RAMP, tone=0.58, wear=0.3, rust=0.06)
    S.paint(tex, ["arm_low_beam", "arm_high_beam", "arm_high_reach", "arm_low_reach", "arm_high_reach_icbm"], orange)
    clamps = ["arm_low_clamp", "arm_high_clamp", "arm_high_clamp_strategic", "arm_low_clamp_icbm", "arm_high_clamp_icbm"]
    S.paint(tex, clamps + [n for n in hinges if n != "spine"], M.painted(M.ARMOUR_RAMP, tone=0.58, wear=0.3))
    S.paint(tex, ["arm_low_hose", "arm_high_hose"], M.rubber(tone=0.55))


def paint_computer(g: GeoModel, tex: Tex, glow: Tex, bars, mast, rings) -> None:
    """The Launch Computer's surfaces: an olive desk with drawers, a dark console and screens, a galvanised mast and a
    white dish."""
    S = M.Surfacer(g, seed=67)
    S.paint(tex, "desk", M.painted(M.OLIVE_RAMP, tone=0.55, wear=0.3))

    def drawers(f, img):                                       # four drawers: a dark gap round each, a steel pull
        col, row = f.border["left"], f.border["top"]
        h = f.shape[0]
        for i in range(4):
            s = 2 + 7 * i
            ring = (col >= s) & (col <= s + 4) & (row >= 2) & (row <= h - 2)
            inside = (col > s) & (col < s + 4) & (row > 2) & (row < h - 2)
            M.darken(img, ring & ~inside, 0.55)
            M.darken(img, inside, 0.92)
            img[(col == s + 2) & (row == 4), :3] = STEEL[5][:3]
    S.overlay(tex, "desk", drawers, only=("north",))
    S.paint(tex, ["console", "crt_l", "crt_r", "socket", "yoke", "back", "mast_cap"], M.painted(M.ARMOUR_RAMP, tone=0.55, wear=0.25))

    def keys(t: Tex, r, d):
        x, y, w, h = r
        if d == "up":
            for i in range(x + 2, x + w - 2, 3):
                t.px(i, y + 2, STEEL[5])
                t.px(i, y + h - 3, [RED[4], YELLOW[4], GREEN[4]][(i // 3) % 3])
    g.paint_all(tex, "console", keys)

    def crt(t: Tex, r, d):
        x, y, w, h = r
        if d == "north" and w > 6:
            t.rect(x + 1, y + 1, w - 2, h - 3, P.BLACK_PAINT.deep)
            glow.rect(x + 1, y + 1, w - 2, h - 3, (0, 0, 0, 255))
            for xx in range(x + 1, x + w - 1):            # a sweep and a few blips, glowing
                yy = y + 1 + (h - 4) // 2 + ((xx * 7) % 5) - 2
                for target in (t, glow):
                    target.px(xx, yy, GREEN[4])
            for target in (t, glow):
                target.px(x + 3, y + 3, GREEN[6])
                target.px(x + w - 4, y + h - 4, GREEN[6])
    for n in ("crt_l", "crt_r"):
        g.paint_all(tex, n, crt)
    band = M.warning_stripes(width=2)
    S.overlay(tex, "socket", lambda f, img: M.paste(img, S.material_on(f, band), f.border["top"] < 3), only=("east",))

    def plug(t: Tex, r, d):
        x, y, w, h = r
        if d == "east":
            t.rect(x + w // 2 - 2, y + 3, 4, 3, GUN[4])
            t.rect(x + w // 2 - 1, y + 4, 2, 1, BLACK[1])
    g.paint_all(tex, "socket", plug)
    S.paint(tex, ["ant_base", "ant_whip", *bars, "feed_arm", "feed"], M.steel(tone=0.6))
    S.paint(tex, mast, M.galvanized(tone=0.6, wear=0.0))
    S.paint(tex, rings, M.white_paint())


def paint_radar(g: GeoModel, tex: Tex, glow: Tex, rails, mast, rings) -> None:
    """The Radar Array's surfaces: a concrete deck banded round its edge, yellow railings, an olive cabinet with its
    scope, the power box, a galvanised mast and a white dish on a dark yoke."""
    S = M.Surfacer(g, seed=71)
    S.paint(tex, "deck", M.concrete(tone=0.6))
    band = M.stripes(width=3, wear=0.4)
    S.overlay(tex, "deck", lambda f, img: M.paste(img, S.material_on(f, band), (np.abs(f.X) > 20) | (f.Z < -4) | (f.Z > 36)),
              only=("up",))
    S.paint(tex, "cabinet", M.painted(M.OLIVE_RAMP, tone=0.55, wear=0.3))

    def scope(t: Tex, rr, d):
        x, y, w, h = rr
        if d == "north" and w > 8:
            t.rect(x + 2, y + 2, w - 4, 6, P.BLACK_PAINT.deep)
            glow.rect(x + 2, y + 2, w - 4, 6, (0, 0, 0, 255))
            for target in (t, glow):                     # the scope: a ring and a sweep line
                target.px(x + w // 2, y + 5, GREEN[5])
                target.px(x + w // 2 + 1, y + 4, GREEN[4])
                target.px(x + w // 2 + 2, y + 3, GREEN[3])
                target.px(x + 3, y + 3, GREEN[2])
            t.px(x + 2, y + h - 3, RED[4])
            t.px(x + 4, y + h - 3, GREEN[4])
    g.paint_all(tex, "cabinet", scope)
    _power_housing(S, g, tex, "powerbox", plug=None)
    S.paint(tex, rails, M.painted(M.YELLOW_PAINT_RAMP, tone=0.6, wear=0.35, rust=0.08))
    S.paint(tex, mast + ["mast_cap"], M.galvanized(tone=0.6, wear=0.0))
    g.paint_all(tex, "lamp", lambda t, rr, d: t.rect(*rr, RED[5]))
    for d in ("north", "south", "east", "up", "down"):
        glow.rect(*g.rect("lamp", d), RED[5])
    S.paint(tex, ["yoke", "yoke_arm_w", "yoke_arm_e", "dish_back", "feed_arm", "feed"], M.painted(M.ARMOUR_RAMP, tone=0.55, wear=0.25))
    S.paint(tex, rings, M.white_paint())


def main() -> None:
    missile_item()
    ST.part_block("launch_pad", launch_pad())
    ST.part_block("gantry_frame", lattice(M.painted(GANTRY_RAMP, tone=0.6, wear=0.0), seed=3), cutout=True)
    ST.part_block("gantry_arm", truss(M.painted(GANTRY_RAMP, tone=0.6, wear=0.0), seed=4), cutout=True)
    ST.part_block("radar_mast", lattice(M.galvanized(tone=0.6), step=3, seed=5), cutout=True)
    shaped_part("radar_dish", radar_dish_block, radar_dish_icon())
    shaped_part("antenna", antenna_block, antenna_icon())
    large_dish()
    ST.controller("launch_silo", ST.controller_front(missile_placard), ST.controller_top(), ST.casing())
    ST.controller("launch_complex", ST.controller_front(complex_placard), ST.controller_top(), ST.casing())
    ST.part_block("flame_deflector", flame_deflector())
    ST.controller("launch_computer", ST.controller_front(radar_placard), ST.controller_top(), ST.casing())
    ST.controller("radar_array", ST.controller_front(radar_placard_big), ST.controller_top(), ST.casing())
    silo()
    launch_complex()
    computer()
    radar_array()
    print("launch written")


if __name__ == "__main__":
    main()
