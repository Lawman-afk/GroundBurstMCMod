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

from gz import palette as P  # noqa: E402
from gz import paths  # noqa: E402
from gz.geo import Animation, GeoModel, save_animations  # noqa: E402
from gz.model import BlockModel, write_json  # noqa: E402
from gz.palette import fixed  # noqa: E402
from gz.tex import Tex  # noqa: E402
import grenades as G  # noqa: E402
import structures as ST  # noqa: E402
import warheads as W  # noqa: E402

STEEL, GUN, OLIVE, CONCRETE = P.STEEL, P.GUNMETAL, P.OLIVE, P.CONCRETE
GANTRY = fixed("gantry_red", "#3a0e06", "#64180a", "#8e2410", "#b83418", "#d4502a", "#e67a52", "#f4ac8c")
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


def missile_paint(kind: str, nose: str = "none"):
    """Paints a missile part; the nose cone (nose_base, nose, tip) in the colours of the warhead it carries (warheads.NOSES:
    bare steel with none)."""
    ramp, band, tip = W.NOSES[nose]

    def fn(t: Tex, r, d):
        x, y, w, h = r
        if kind == "body":
            t.rect(x, y, w, h, WHITE[tone(d) + 1])
            if d in ("north", "south", "east", "west") and h > 8:
                # Two black roll bands (rows count down from the top of the body).
                for frac in (0.12, 0.62):
                    row = y + int(frac * h)
                    t.rect(x, row, w, max(1, h // 12), BLACK[3])
            return
        if kind in ("nose_base", "nose"):
            t.rect(x, y, w, h, ramp[tone(d) + 1])
            if kind == "nose_base" and band is not None and d in ("north", "south", "east", "west"):
                t.hline(x, y + h - 1, w, band)               # the warhead's marking band at the foot of the cone
            return
        if kind == "tip":
            t.rect(x, y, w, h, tip[tone(d) + 1])
            return
        if kind == "fin":
            t.rect(x, y, w, h, STEEL[tone(d) + 1])
            if d in ("east", "west", "north", "south") and h > 2:
                t.hline(x, y + h - 1, w, BLACK[3])
            return
        t.rect(x, y, w, h, GUN[tone(d)])
        if d == "down":
            t.rect(x + 1, y + 1, max(1, w - 2), max(1, h - 2), BLACK[1])
    return fn


def missile_item() -> None:
    """The Missile (bare steel nose), and a model for each warhead it can carry: the renderer of a missile in
    flight picks one by the warhead's id (custom model data), so the nose shows what it carries. A Strategic missile
    ("strategic_<warhead>") is its own longer model."""
    for nose in BENCH_NOSES:
        missile_model("missile" if nose == "none" else f"missile_{nose}", nose)
        strategic_model(f"strategic_missile_{nose}", nose)
    for nose in ICBM_NOSES:
        icbm_model(f"icbm_missile_{nose}", nose)
    cases = [{"when": nose, "model": {"type": "minecraft:model", "model": f"groundzero:item/missile_{nose}"}}
             for nose in BENCH_NOSES if nose != "none"]
    cases += [{"when": f"strategic_{nose}", "model": {"type": "minecraft:model", "model": f"groundzero:item/strategic_missile_{nose}"}}
              for nose in BENCH_NOSES]
    cases += [{"when": f"icbm_{nose}", "model": {"type": "minecraft:model", "model": f"groundzero:item/icbm_missile_{nose}"}}
              for nose in ICBM_NOSES]
    write_json(paths.ASSETS / "items" / "missile.json", {"model": {
        "type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0, "cases": cases,
        "fallback": {"type": "minecraft:model", "model": "groundzero:item/missile"}}})


def missile_model(model_name: str, nose: str) -> None:
    m = BlockModel(model_name, tex_size=(64, 64), kind="item")
    for name, frm, to, faces, _ in missile_parts():
        m.box(name, frm, to, faces=faces)
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    for name, _, _, _, kind in missile_parts():
        m.paint_all(t, name, missile_paint(kind, nose))
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


def strategic_paint(kind: str, nose: str = "none"):
    """The Strategic missile's livery: black-and-white roll quadrants on the first stage, a dark interstage, a white
    second stage with a black band and a grey data plate, black fins; the nose as the warhead's."""
    if kind in NOSE_PARTS:
        return missile_paint(kind, nose)

    def fn(t: Tex, r, d):
        x, y, w, h = r
        sides = ("north", "south", "east", "west")
        if kind.startswith("stage1_"):
            quadrant = kind[len("stage1_"):]
            if d in sides:
                for yy in range(y, y + h):               # three bands down the stage, each quadrant one colour
                    band = (yy - y) * 3 // max(1, h)
                    t.hline(x, yy, w, roll_colour(quadrant, band, BLACK[tone(d) + 1], WHITE[tone(d) + 1]))
            else:
                t.rect(x, y, w, h, roll_colour(quadrant, 2, BLACK[tone(d) + 1], WHITE[tone(d) + 1]))
            return
        if kind == "interstage":
            t.rect(x, y, w, h, GUN[tone(d)])
            if d in sides:
                for xx in range(x, x + w, 2):
                    t.px(xx, y + h // 2, STEEL[4])               # rivets round the ring
            return
        if kind == "stage2":
            t.rect(x, y, w, h, WHITE[tone(d) + 1])
            if d in sides and h > 8:
                t.rect(x, y + h - max(2, h // 10), w, max(2, h // 10), BLACK[3])      # a black band at its foot
                if d == "north" and w >= 4:
                    t.rect(x + 1, y + h // 3, w - 2, 3, P.BLACK_PAINT[2])          # the data plate
                    t.hline(x + 1, y + h // 3 + 1, w - 2, STEEL[3])
            return
        if kind == "sfin":
            t.rect(x, y, w, h, BLACK[tone(d) + 1])
            if d in sides and h > 1:
                t.hline(x, y, w, WHITE[3])
            return
        t.rect(x, y, w, h, GUN[tone(d)])                         # the nozzles
        if d == "down":
            t.rect(x, y, w, h, BLACK[1])
    return fn


def strategic_model(model_name: str, nose: str) -> None:
    m = BlockModel(model_name, tex_size=(64, 64), kind="item")
    for name, frm, to, faces, _ in strategic_parts():
        m.box(name, frm, to, faces=faces)
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    for name, _, _, _, kind in strategic_parts():
        m.paint_all(t, name, strategic_paint(kind, nose))
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
ICBM_NOSES = ("thermonuclear_warhead", "cobalt_warhead")
BENCH_NOSES = [n for n in W.NOSES if n not in ICBM_NOSES]

#: Its sections, tail to nose, in item pixels (centred on x = z = 8): (name, half-width, side half-width, y0, y1, paint).
#: Three solid-fuel stages narrowing upward, dark interstages, the warhead's shroud. Drawn upright four times over on the
#: Launch Complex (about 11 blocks) and in flight, and lying on the Strategic Assembly Hall's cradles.
ICBM_SECTIONS = [
    ("skirt", 3.2, 2.15, -10, -8.6, "iskirt"),
    ("stage1", 3.0, 2.0, -8.6, 6, "istage1"),
    ("inter1", 2.8, 1.85, 6, 7.2, "iinter"),
    ("stage2", 2.6, 1.75, 7.2, 16.4, "istage2"),
    ("inter2", 2.35, 1.55, 16.4, 17.4, "iinter"),
    ("stage3", 2.2, 1.45, 17.4, 22.4, "istage3"),
    ("nose0", 2.0, 1.3, 22.4, 26.2, "nose_base"),
    ("nose1", 1.35, 0.9, 26.2, 29.6, "nose"),
]
ICBM_TIP = ((7.5, 29.6, 7.5), (8.5, 32, 8.5))
ICBM_NOZZLES = ((6.3, 6.3), (8.5, 6.3), (6.3, 8.5), (8.5, 8.5))
#: Which of the hall crane's four loads each section comes in: the first stage, the second, the third, the shroud.
ICBM_LOAD = {"skirt": 1, "stage1": 1, "inter1": 2, "stage2": 2, "inter2": 3, "stage3": 3, "nose0": 4, "nose1": 4}


def icbm_parts() -> list[tuple[str, tuple, tuple, tuple, str]]:
    """(name, from, to, faces, paint) in item pixels, tail at y -11.5 (four nozzles), nose tip at 32."""
    all6 = ("north", "south", "east", "west", "up", "down")
    parts = []
    for i, (x, z) in enumerate(ICBM_NOZZLES):
        parts.append((f"nozzle{i}", (x, -11.5, z), (x + 1.2, -10, z + 1.2), ("north", "south", "east", "west", "down"), "nozzle"))
    for name, r, w, y0, y1, paint in ICBM_SECTIONS:
        parts.append((f"{name}_c", (8 - r, y0, 8 - w), (8 + r, y1, 8 + w), ("north", "south", "east", "west", "up", "down"), paint))
        parts.append((f"{name}_n", (8 - w, y0, 8 - r), (8 + w, y1, 8 - w), ("north", "east", "west", "up", "down"), paint))
        parts.append((f"{name}_s", (8 - w, y0, 8 + w), (8 + w, y1, 8 + r), ("south", "east", "west", "up", "down"), paint))
    parts.append(("tip", ICBM_TIP[0], ICBM_TIP[1], all6, "tip"))
    return parts


def icbm_paint(kind: str, nose: str = "none", lying: bool = False):
    """The intercontinental missile's livery: olive-drab stages, a white band round the first stage's middle, a yellow
    one round the second's and a white one round the third's, dark riveted interstages, a gunmetal skirt; the shroud in
    its warhead's colours, its marking band at its middle. Every band sits at the middle of its section, so the lying
    missile in the hall (whose sections run across its faces' u or v the other way round) is painted alike."""
    ramp, mark, tip = W.NOSES[nose]
    band = {"istage1": WHITE[5], "istage2": YELLOW[4], "istage3": WHITE[5], "nose_base": mark}.get(kind)
    base = {"iskirt": GUN, "iinter": GUN, "nose_base": ramp, "nose": ramp, "tip": tip, "nozzle": GUN}.get(kind, OLIVE)
    ends = ("north", "south") if lying else ("up", "down")

    def fn(t: Tex, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, base[tone(d) + (0 if base is GUN else 1)])
        if kind == "nozzle" and d == ("south" if lying else "down"):    # the exhaust end, dark inside
            t.rect(x, y, w, h, BLACK[1])
            return
        if d in ends:
            return
        along_u = lying and d in ("east", "west")             # the section's length runs across the face
        if band is not None and (w if along_u else h) > 3:
            if along_u:
                t.vline(x + w // 2, y, h, band)
            else:
                t.hline(x, y + h // 2, w, band)
        if kind == "iinter":
            for i in range(0, w if not along_u else h, 2):  # rivets round the ring
                t.px(x + i, y + h // 2, STEEL[4]) if not along_u else t.px(x + w // 2, y + i, STEEL[4])
    return fn


def icbm_model(model_name: str, nose: str) -> None:
    m = BlockModel(model_name, tex_size=(64, 64), kind="item")
    for name, frm, to, faces, _ in icbm_parts():
        m.box(name, frm, to, faces=faces)
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    for name, _, _, _, kind in icbm_parts():
        m.paint_all(t, name, icbm_paint(kind, nose))
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
    t = Tex(16, 16)
    t.rect(0, 0, 16, 16, CONCRETE[3])
    t.speckle(0, 0, 16, 16, [CONCRETE[2], CONCRETE[4]], seed=31, density=0.12)
    for i in range(16):                                  # a hazard-striped border
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
            t.px(x, y, YELLOW[4] if (i // 2) % 2 == 0 else BLACK[3])
    t.outline(1, 1, 14, 14, CONCRETE[2])
    return t


def lattice(ramp, step: int = 4) -> Tex:
    """A see-through lattice face: a frame with an X brace, the rest clear."""
    t = Tex(16, 16, CLEAR)
    t.outline(0, 0, 16, 16, ramp[3])
    t.outline(1, 1, 14, 14, ramp[2])
    for i in range(2, 14):
        t.px(i, i, ramp[4])
        t.px(15 - i, i, ramp[3])
    for y in range(step, 16, step * 2):
        t.hline(1, y, 14, ramp[2])
    return t


def truss(ramp) -> Tex:
    t = Tex(16, 16, CLEAR)
    t.rect(0, 0, 16, 2, ramp[4])
    t.rect(0, 14, 16, 2, ramp[2])
    for i in range(0, 16, 4):
        t.vline(i, 2, 12, ramp[3])
    for i in range(2, 14):
        t.px((i * 2) % 16, i, ramp[4])
    return t


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
    t = Tex(16, 16)
    t.rect(0, 0, 16, 16, GUN[2])
    t.outline(0, 0, 16, 16, GUN[1])
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
    t = Tex(16, 16)
    t.rect(0, 0, 16, 16, GUN[2])
    t.outline(0, 0, 16, 16, GUN[1])
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
    m.paint_all(t, "stub", shaded(GUN))
    m.paint_all(t, "yoke", shaded(GUN))
    for i in range(3):
        m.paint_all(t, f"ring{i}", lambda t, r, d: (t.rect(*r, WHITE[tone(d) + 1]), t.outline(*r, WHITE[2]) if d == "north" and min(r[2], r[3]) > 3 else None))
    m.paint_all(t, "feed", shaded(GUN))
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
    m.paint_all(t, "base", shaded(GUN))
    m.paint_all(t, "whip", shaded(STEEL))
    for i in range(3):
        m.paint_all(t, f"bar{i}", shaded(STEEL))
        m.paint_all(t, f"bar{i}e", shaded(STEEL))
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
    t = Tex(16, 16)
    t.rect(0, 0, 16, 16, WHITE[3])
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            t.px(x, y, WHITE[5] if d > 6.5 else WHITE[4] if d > 4.5 else WHITE[3] if d > 2 else GUN[3])
    t.outline(0, 0, 16, 16, WHITE[2])
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
    m.paint_all(t, "hub", shaded(GUN))
    for n, k in names:
        # The bowl's face lighter toward the rim (it catches the light), its underside and sides plain.
        def fn(t, rr, d, k=k):
            x, y, w, h = rr
            t.rect(x, y, w, h, WHITE[(tone(d) + 1) if d != "up" else 2 + k])
        m.paint_all(t, n, fn)
    m.paint_all(t, "post", shaded(STEEL))
    m.paint_all(t, "horn", shaded(GUN))
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

    def concrete(t: Tex, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, CONCRETE[tone(d) + 1])
        if d == "up":
            t.speckle(x, y, w, h, [CONCRETE[3], CONCRETE[5]], seed=x * 7 + y, density=0.06)
            for i in range(w):                           # the hazard border along the pad's outer edges
                t.px(x + i, y, YELLOW[4] if (i // 3) % 2 == 0 else BLACK[3])
        elif d in ("north", "south", "east", "west"):
            t.hline(x, y, w, CONCRETE[5])
    for n, _, _ in pad:
        g.paint_all(tex, n, concrete)
    g.paint_all(tex, "grate", lambda t, r, d: (t.rect(*r, BLACK[1]), [t.vline(xx, r[1], r[3], GUN[3]) for xx in range(r[0], r[0] + r[2], 2)]))
    for p in posts:
        g.paint_all(tex, p, lambda t, r, d: (t.rect(*r, YELLOW[tone(d) + 1]), t.hline(r[0], r[1], r[2], BLACK[3])))

    def console(t: Tex, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, OLIVE[tone(d) + 1])
        if d == "north" and w > 6:
            t.rect(x + 2, y + 1, w - 4, 4, P.BLACK_PAINT.deep)
            for xx in range(x + 2, x + w - 2):
                t.px(xx, y + 3 + (xx % 2), GREEN[4])
            t.px(x + 2, y + h - 2, RED[4])
            t.px(x + 4, y + h - 2, GREEN[4])
    g.paint_all(tex, "console", console)

    def housing(t: Tex, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, GUN[tone(d) + 1])
        if d in ("north", "south", "east", "west"):
            t.stripes(x, y, w, min(2, h), RED[3], BLACK[2], width=1)
        if d == "south" and w > 4:
            t.rect(x + w // 2 - 2, y + 2, 4, 3, GUN[4])
            t.rect(x + w // 2 - 1, y + 3, 2, 1, BLACK[1])
    g.paint_all(tex, "housing", housing)
    for p in tower + girders + ["platform"]:
        g.paint_all(tex, p, lambda t, r, d: (t.rect(*r, GANTRY[tone(d) + 1]), t.hline(r[0], r[1], r[2], GANTRY[5]) if d != "down" and r[3] > 2 else None))
    g.paint_all(tex, "arm_beam", lambda t, r, d: (t.rect(*r, GANTRY[tone(d) + 1]), t.hline(r[0], r[1] + r[3] - 1, r[2], GANTRY[2])))
    g.paint_all(tex, "arm_clamp", shaded(GUN))
    for n in hinge:
        g.paint_all(tex, n, shaded(GUN) if n != "hinge_spine" else lambda t, r, d: t.rect(*r, GANTRY[tone(d) + 1]))
    for name, _, _, _, kind in missile_parts():
        if kind in NOSE_PARTS:
            for nose in BENCH_NOSES:
                g.paint_all(tex, f"n_{nose}_{name}", missile_paint(kind, nose))
        else:
            g.paint_all(tex, "m_" + name, missile_paint(kind))
    g.save(tex)
    # The arm swings away from the missile during the countdown and stays away.
    count = Animation("working", 10.0, loop="hold_on_last_frame")
    # (75 degrees: swung any further it would pass through the tower's corner post.)
    count.rotate("arm", {0: (0, 0, 0), 2.5: (0, -75, 0), 10: (0, -75, 0)}, easing="easeInOutSine")
    print(save_animations("launch_silo", [count]))


# --- the formed Launch Complex (Phase 8) ---------------------------------------------------------------------------------
TOWER = fixed("service_grey", "#1e2226", "#30353a", "#464c52", "#5e656c", "#7a8188", "#9aa1a8", "#c0c6cc")
SAFETY = fixed("safety_orange", "#3e1606", "#682208", "#96340c", "#c04a14", "#dc6a2c", "#ec9258", "#f6c098")
SOOT = fixed("trench_soot", "#0a0a0b", "#121214", "#1a1b1d", "#24262a", "#303338", "#40444a", "#565b62")


def flame_deflector() -> Tex:
    """The deflector block: heavy sooted steel with heat-blued welds and a hazard edge."""
    t = Tex(16, 16)
    t.rect(0, 0, 16, 16, SOOT[4])
    t.speckle(0, 0, 16, 16, [SOOT[3], SOOT[5]], seed=17, density=0.18)
    for y in range(3, 16, 4):
        t.hline(1, y, 14, SOOT[2])                        # weld seams between the wedge's plates
        t.hline(1, y + 1, 14, (70, 82, 120, 255))
    for i in range(16):
        t.px(i, 0, YELLOW[4] if (i // 2) % 2 == 0 else BLACK[3])
    t.outline(0, 1, 16, 15, SOOT[1])
    return t


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
    # Or an intercontinental missile: its own model four times over (about 11 blocks), its tail on the same line.
    for name, frm, to, faces, kind in icbm_parts():
        a = ((frm[0] - 8) * 4, (frm[1] + ICBM_TAIL) * 4 + 14, (frm[2] - 8) * 4 + 32)
        b = ((to[0] - 8) * 4, (to[1] + ICBM_TAIL) * 4 + 14, (to[2] - 8) * 4 + 32)
        if kind in NOSE_PARTS:
            for nose in ICBM_NOSES:
                g.box(f"in_{nose}_{name}", a, b, faces=faces, bone=f"inose_{nose}")
        else:
            g.box("i_" + name, a, b, faces=faces, bone="icbm")
    g.layout()
    tex = Tex(g.tex_w, g.tex_h)

    def concrete(t: Tex, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, CONCRETE[tone(d) + 1])
        if d == "up":
            t.speckle(x, y, w, h, [CONCRETE[3], CONCRETE[5]], seed=x * 7 + y, density=0.06)
            for i in range(w):                           # the hazard border along the deck's outer edges
                t.px(x + i, y, YELLOW[4] if (i // 3) % 2 == 0 else BLACK[3])
        elif d in ("north", "south", "east", "west"):
            t.hline(x, y, w, CONCRETE[5])
    for n, _, _, _ in deck:
        g.paint_all(tex, n, concrete)
    g.paint_all(tex, "trench_floor", lambda t, r, d: (t.rect(*r, SOOT[1]), t.speckle(*r, [SOOT[0], SOOT[2]], seed=r[0] + r[1], density=0.2)))
    for n in wedge:
        g.paint_all(tex, n, lambda t, r, d: (t.rect(*r, SOOT[3 if d == "up" else 2]), t.hline(r[0], r[1], r[2], (70, 82, 120, 255))))

    def deluge(t: Tex, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, TOWER[tone(d) + 2])
        if d in ("north", "south") and w > 4:
            for xx in range(x + 2, x + w, 5):            # nozzles
                t.px(xx, y + h // 2, P.CHERENKOV[4])
    for n in pipes:
        g.paint_all(tex, n, deluge)
    for n in posts:
        g.paint_all(tex, n, lambda t, r, d: (t.rect(*r, YELLOW[tone(d) + 1]), t.hline(r[0], r[1], r[2], BLACK[3])))

    def console(t: Tex, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, OLIVE[tone(d) + 1])
        if d == "north" and w > 6:
            t.rect(x + 2, y + 1, w - 4, 4, P.BLACK_PAINT.deep)
            for xx in range(x + 2, x + w - 2):
                t.px(xx, y + 3 + (xx % 2), GREEN[4])
            t.px(x + 2, y + h - 2, RED[4])
            t.px(x + 4, y + h - 2, YELLOW[4])
    g.paint_all(tex, "console", console)

    def housing(t: Tex, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, GUN[tone(d) + 1])
        if d in ("north", "south", "east", "west"):
            t.stripes(x, y, w, min(2, h), RED[3], BLACK[2], width=1)
    g.paint_all(tex, "housing", housing)
    for n in tower + girders + braces + ["mast"]:
        g.paint_all(tex, n, lambda t, r, d: (t.rect(*r, TOWER[tone(d) + 1]), t.hline(r[0], r[1], r[2], TOWER[5]) if d != "down" and r[3] > 2 else None))

    def grating(t: Tex, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, TOWER[tone(d) + 2])
        if d in ("up", "down"):
            for xx in range(x, x + w, 2):
                t.vline(xx, y, h, TOWER[2])
        elif d in ("north", "south", "east", "west"):
            t.hline(x, y, w, SAFETY[4])
    for n in decks + ["roof"]:
        g.paint_all(tex, n, grating)
    for n in rails:
        g.paint_all(tex, n, shaded(SAFETY))
    g.paint_all(tex, "beacon", lambda t, r, d: t.rect(*r, RED[5] if d != "down" else RED[3]))
    for bone in ("arm_low", "arm_high"):
        g.paint_all(tex, f"{bone}_beam", lambda t, r, d: (t.rect(*r, SAFETY[tone(d) + 1]), t.hline(r[0], r[1] + r[3] - 1, r[2], SAFETY[2])))
        g.paint_all(tex, f"{bone}_clamp", shaded(GUN))
        g.paint_all(tex, f"{bone}_hose", lambda t, r, d: t.rect(*r, P.RUBBER[2 if d == "down" else 3]))
    g.paint_all(tex, "arm_high_reach", lambda t, r, d: (t.rect(*r, SAFETY[tone(d) + 1]), t.hline(r[0], r[1] + r[3] - 1, r[2], SAFETY[2])))
    g.paint_all(tex, "arm_high_clamp_strategic", shaded(GUN))
    for n in ("arm_low_reach", "arm_high_reach_icbm"):
        g.paint_all(tex, n, lambda t, r, d: (t.rect(*r, SAFETY[tone(d) + 1]), t.hline(r[0], r[1] + r[3] - 1, r[2], SAFETY[2])))
    for n in ("arm_low_clamp_icbm", "arm_high_clamp_icbm"):
        g.paint_all(tex, n, shaded(GUN))
    for n in hinges:
        g.paint_all(tex, n, shaded(GUN) if n != "spine" else lambda t, r, d: t.rect(*r, TOWER[tone(d) + 1]))
    for name, _, _, _, kind in missile_parts():
        if kind in NOSE_PARTS:
            for nose in BENCH_NOSES:
                g.paint_all(tex, f"n_{nose}_{name}", missile_paint(kind, nose))
        else:
            g.paint_all(tex, "m_" + name, missile_paint(kind))
    for name, _, _, _, kind in strategic_parts():
        if kind in NOSE_PARTS:
            for nose in BENCH_NOSES:
                g.paint_all(tex, f"sn_{nose}_{name}", strategic_paint(kind, nose))
        else:
            g.paint_all(tex, "s_" + name, strategic_paint(kind))
    for name, _, _, _, kind in icbm_parts():
        if kind in NOSE_PARTS:
            for nose in ICBM_NOSES:
                g.paint_all(tex, f"in_{nose}_{name}", icbm_paint(kind, nose))
        else:
            g.paint_all(tex, "i_" + name, icbm_paint(kind))
    g.save(tex)
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

    def desk(t: Tex, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, OLIVE[tone(d) + 1])
        if d in ("north", "south", "east", "west") and h > 4:
            t.outline(x, y, w, h, OLIVE[1])
        if d == "north" and w > 20:
            for i in range(4):                           # drawers
                t.rect(x + 2 + i * (w // 4), y + 3, w // 4 - 3, h - 5, OLIVE[2])
                t.px(x + 2 + i * (w // 4) + (w // 8) - 1, y + 4, STEEL[5])
    g.paint_all(tex, "desk", desk)

    def console(t: Tex, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, GUN[tone(d) + 1])
        if d == "up":
            for i in range(x + 2, x + w - 2, 3):          # keys and lamps
                t.px(i, y + 2, STEEL[5])
                t.px(i, y + h - 3, [RED[4], YELLOW[4], GREEN[4]][(i // 3) % 3])
    g.paint_all(tex, "console", console)

    def crt(t: Tex, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, GUN[tone(d) + 1])
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

    def socket(t: Tex, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, GUN[tone(d) + 1])
        if d == "east":
            t.stripes(x, y, w, 2, RED[3], BLACK[2], width=1)
            t.rect(x + w // 2 - 2, y + 3, 4, 3, GUN[4])
            t.rect(x + w // 2 - 1, y + 4, 2, 1, BLACK[1])
    g.paint_all(tex, "socket", socket)
    for n in ["ant_base", "ant_whip", *bars, "feed_arm", "feed", "back", "yoke", "mast_cap"]:
        g.paint_all(tex, n, shaded(STEEL if n.startswith("ant") or n.startswith("feed") else GUN))
    for n in mast:
        g.paint_all(tex, n, shaded(STEEL))
    for n in rings:
        g.paint_all(tex, n, lambda t, r, d: (t.rect(*r, WHITE[tone(d) + 1]), t.outline(*r, WHITE[2]) if d == "north" and min(r[2], r[3]) > 3 else None))
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

    def deck(t: Tex, rr, d):
        x, y, w, h = rr
        t.rect(x, y, w, h, CONCRETE[tone(d) + 1])
        if d == "up":
            t.speckle(x, y, w, h, [CONCRETE[3], CONCRETE[5]], seed=x * 5 + y, density=0.05)
            for i in range(w):                           # a hazard border round the deck
                c = YELLOW[4] if (i // 3) % 2 == 0 else BLACK[3]
                t.px(x + i, y, c)
                t.px(x + i, y + h - 1, c)
        elif d in ("north", "south", "east", "west"):
            t.hline(x, y, w, CONCRETE[5])
    g.paint_all(tex, "deck", deck)

    def cabinet(t: Tex, rr, d):
        x, y, w, h = rr
        t.rect(x, y, w, h, OLIVE[tone(d) + 1])
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
    g.paint_all(tex, "cabinet", cabinet)

    def powerbox(t: Tex, rr, d):
        x, y, w, h = rr
        t.rect(x, y, w, h, GUN[tone(d) + 1])
        if d in ("north", "south", "east", "west"):
            t.stripes(x, y, w, min(2, h), RED[3], BLACK[2], width=1)
    g.paint_all(tex, "powerbox", powerbox)
    for n in rails:
        g.paint_all(tex, n, shaded(YELLOW))
    for n in mast + ["mast_cap"]:
        g.paint_all(tex, n, shaded(STEEL))
    g.paint_all(tex, "lamp", lambda t, rr, d: t.rect(*rr, RED[5]))
    glow.rect(*g.rect("lamp", "north"), RED[5])
    for d in ("south", "east", "up", "down"):
        glow.rect(*g.rect("lamp", d), RED[5])
    for n in ("yoke", "yoke_arm_w", "yoke_arm_e", "dish_back", "feed_arm", "feed"):
        g.paint_all(tex, n, shaded(GUN))
    for n in rings:
        g.paint_all(tex, n, lambda t, rr, d: (t.rect(*rr, WHITE[tone(d) + 1]),
                                              t.outline(*rr, WHITE[2]) if d == "north" and min(rr[2], rr[3]) > 3 else None))
    g.save(tex, glow)
    spin = Animation("working", 4.8, loop=True)                 # one turn every 4.8 s, as its screen's sweep
    spin.rotate("dish", {0: (0, 0, 0), 4.8: (0, 360, 0)})
    print(save_animations("radar_array", [spin]))


def main() -> None:
    missile_item()
    ST.part_block("launch_pad", launch_pad())
    ST.part_block("gantry_frame", lattice(GANTRY), cutout=True)
    ST.part_block("gantry_arm", truss(GANTRY), cutout=True)
    ST.part_block("radar_mast", lattice(STEEL, step=3), cutout=True)
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
