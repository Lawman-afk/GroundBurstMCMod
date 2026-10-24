"""Missile parts (DESIGN.md §9.3-9.7, Phase 7 slice 7b): item models for what the Assembly Bench's missile page takes.

- Strategic Missile Body (Phase 8): two stages, black-and-white roll quadrants on the wider first, big black fins.
- Missile bodies: white airframe tubes, open at the top (the warhead seats there), with four tail fins; bigger by class,
  with yellow class bands like the warhead casings (one Tactical, two Medium, three Heavy) and black roll marks.
- Solid Rocket Motor I: a short grey propellant case on a dark nozzle, one red band.
- Solid Rocket Motor II: a longer case, two red bands, a tungsten nozzle.
- Liquid-fuel Stage: two tanks (fuel and oxygen) one over the other, a feed line down the side, a bell nozzle.
- Inertial Guidance Unit: an olive box carrying a brass gyroscope ring.
- GPS Guidance Unit: a dark box with a white antenna dome and a green status lamp.
- Hardened Electronics: a lead-grey shielded box with a yellow shield emblem and a braided cable.
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))

from gz import palette as P  # noqa: E402
from gz.model import BlockModel  # noqa: E402
from gz.palette import fixed  # noqa: E402
from gz.tex import Tex  # noqa: E402
import grenades as G  # noqa: E402
import launch as L  # noqa: E402

prism, shaded, fill_all = G.prism, G.shaded, G.fill_all
STEEL, BRASS, YELLOW, BLACK, WHITE = P.STEEL, P.BRASS, P.HAZARD_YELLOW, P.HAZARD_BLACK, P.WHITE_CERAMIC
RED, OLIVE, GUN, LEAD = P.WARNING_RED, P.OLIVE, P.GUNMETAL, P.LEAD
CASE = fixed("motor_case", "#2a2e32", "#3e4348", "#575c62", "#71777d", "#8c9298", "#a8adb2", "#c8ccd0")
NOZZLE = fixed("nozzle", "#101112", "#1a1b1d", "#26282b", "#33363a", "#43474c", "#585d63", "#71777d")
SIDES = ("north", "south", "east", "west")


def model(name: str) -> BlockModel:
    return BlockModel(name, tex_size=(64, 64), kind="item", render_type="minecraft:cutout")


def banded(ramp, bands, top: float, y0: float):
    """Shading for a round body with coloured bands at heights (model y)."""
    def fn(t: Tex, r, d):
        shaded(ramp)(t, r, d)
        x, y, w, h = r
        if d in SIDES:
            for by, colour in bands:
                row = y + max(0, min(h - 1, int(round((top - by) * h / (top - y0) - 0.5))))
                t.hline(x, row, w, colour)
    return fn


def body(name: str, r: float, w: float, top: float, fin: float, bands: int) -> None:
    m = model(name)
    tube = prism(m, "tube", 8, 8, r, w, 2, top)
    m.box("fin_x", (8 - fin, 1, 7.6), (8 + fin, 6, 8.4))
    m.box("fin_z", (7.6, 1.25, 8 - fin), (8.4, 6, 8 + fin))
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    marks = [(top - 1.5 - 2.2 * k, YELLOW.base) for k in range(bands)] + [(3.5, BLACK[2])]

    def tube_paint(t, rr, d):
        if d == "up":
            x, y, ww, h = rr
            t.rect(x, y, ww, h, STEEL.deep)                 # open at the top: the warhead seats in it
            t.outline(x, y, ww, h, WHITE.light)
            return
        banded(WHITE, marks, top, 2)(t, rr, d)
    fill_all(m, t, tube, tube_paint)
    fill_all(m, t, ["fin_x", "fin_z"], shaded(STEEL))
    G.finish(m, t)


def strategic_body() -> None:
    """The Strategic Missile Body (Phase 8): two stages, open at the top (the warhead seats there), the first wider and
    painted in black-and-white roll quadrants, a riveted interstage, big black fins: the long missile's livery."""
    m = model("strategic_missile_body")
    # The first stage in quadrants, so its roll pattern has solid corners (the same cut as the missile's, launch.py).
    stage1 = []
    for suffix, frm, to, faces, quadrant in L.quad_boxes(3.4, 2.3, 2, 8):
        m.box(f"stage1_{suffix}", frm, to, faces=faces)
        stage1.append((f"stage1_{suffix}", quadrant))
    ring = prism(m, "ring", 8, 8, 3.5, 2.35, 8, 8.8)
    stage2 = prism(m, "stage2", 8, 8, 2.9, 1.95, 8.8, 15)
    m.box("fin_x", (2.4, 1.5, 7.6), (13.6, 6, 8.4))
    m.box("fin_z", (7.6, 1.75, 2.4), (8.4, 6, 13.6))
    m.layout()
    t = Tex(m.tex_w, m.tex_h)

    def quadrants(quadrant):
        def fn(t, r, d):
            x, y, w, h = r
            tone = {"up": 5, "down": 2}.get(d, 4 if d in ("north", "west") else 3)
            if d in SIDES:
                for yy in range(y, y + h):               # two bands, each quadrant one colour
                    band = (yy - y) * 2 // max(1, h)
                    t.hline(x, yy, w, L.roll_colour(quadrant, band, BLACK[tone - 1], WHITE[tone]))
            else:
                t.rect(x, y, w, h, L.roll_colour(quadrant, 1, BLACK[tone - 1], WHITE[tone]))
        return fn
    for name, quadrant in stage1:
        m.paint_all(t, name, quadrants(quadrant))
    fill_all(m, t, ring, shaded(GUN))

    def upper(t, r, d):
        x, y, w, h = r
        if d == "up":
            t.rect(x, y, w, h, STEEL.deep)                     # open at the top: the warhead seats in it
            t.outline(x, y, w, h, WHITE.light)
            return
        shaded(WHITE)(t, r, d)
        if d in SIDES:
            t.hline(x, y + h - 1, w, BLACK[2])
    fill_all(m, t, stage2, upper)
    fill_all(m, t, ["fin_x", "fin_z"], shaded(BLACK))
    G.finish(m, t)


def motor(name: str, top: float, r: float, w: float, bands: int, nozzle_ramp) -> None:
    m = model(name)
    case = prism(m, "case", 8, 8, r, w, 4, top)
    throat = prism(m, "throat", 8, 8, r * 0.55, w * 0.55, 3, 4)
    bell = prism(m, "bell", 8, 8, r * 0.8, w * 0.8, 1, 3)
    m.box("cap", (8 - w * 0.6, top, 8 - w * 0.6), (8 + w * 0.6, top + 0.6, 8 + w * 0.6))
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    fill_all(m, t, case, banded(CASE, [(top - 1.5 - 1.3 * k, RED.base) for k in range(bands)], top, 4))
    fill_all(m, t, throat + bell, shaded(nozzle_ramp))
    m.paint_all(t, "cap", shaded(STEEL))
    G.finish(m, t)


def liquid_stage() -> None:
    m = model("liquid_fuel_stage")
    lower = prism(m, "lox", 8, 8, 3.2, 2.2, 4, 8)
    upper = prism(m, "fuel", 8, 8, 3.2, 2.2, 8.4, 13)
    m.box("ring", (4.6, 8, 5.6), (11.4, 8.4, 10.4), faces=SIDES)
    m.box("line", (11.2, 4, 7.5), (12, 12, 8.5), faces=("north", "south", "east", "up", "down"))
    bell = prism(m, "bell", 8, 8, 2.4, 1.6, 1, 3)
    throat = prism(m, "throat", 8, 8, 1.2, 0.8, 3, 4)
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    fill_all(m, t, lower, banded(WHITE, [(6, P.CHERENKOV[4])], 8, 4))          # oxygen: a blue band
    fill_all(m, t, upper, banded(WHITE, [(11, RED.base)], 13, 8.4))           # fuel: a red band
    m.paint_all(t, "ring", shaded(STEEL))
    m.paint_all(t, "line", shaded(BRASS))
    fill_all(m, t, bell + throat, shaded(NOZZLE))
    G.finish(m, t)


def cruise_engine() -> None:
    """The Cruise Engine (slice 9c, DESIGN.md §17.11): a small turbojet, its intake lip on top (the dark mouth with the
    fan's hub in it), its brushed compressor case, the hot tungsten turbine section and a short nozzle, on the launch
    booster under it (a dark solid motor, a hazard band at its joint), a fuel line and pump down its side. Round parts
    are true prisms (warheads.section) painted with the material library, as the missiles are."""
    import numpy as np  # noqa: PLC0415
    import missiles as MS  # noqa: PLC0415
    import warheads as W  # noqa: PLC0415
    from gz import machine_kit as K  # noqa: PLC0415
    m = BlockModel("cruise_engine", tex_size=(64, 64), kind="item", render_type="minecraft:cutout")
    m.open_ok = set()
    groups: dict[str, list[str]] = {}

    def ring(group, r, y0, y1, caps=("up", "down"), stagger=None):
        names = W.section(m, f"{group}{len(groups.get(group, []))}", r, y0, y1, W.sides_for(r), caps=caps, stagger=stagger)
        groups.setdefault(group, []).extend(names)
        return names
    ring("bell", 1.5, 0.6, 1.8, stagger="full")
    ring("booster", 2.5, 1.8, 6.2, stagger="base")
    ring("nozzle", 1.5, 6.4, 7.4, stagger="full")
    ring("turbine", 2.25, 7.4, 9.2)
    ring("case", 2.55, 9.2, 13.0)
    ring("lip", 2.8, 13.0, 13.9, stagger="full")                 # (its top shows: no two caps level)
    m.box("line", (10.5, 7.6, 7.6), (11.1, 12.6, 8.4), faces=("north", "south", "east", "up", "down"))
    m.box("pump", (10.3, 9.4, 6.9), (11.7, 11.0, 9.1), faces=("north", "south", "east", "up", "down"))
    groups["line"], groups["pump"] = ["line"], ["pump"]
    m.layout()
    k = K.Kit(m, {}, seed=731, ao_reach=1.5, turned=True)
    W.round_faces(k.S, [n for g in ("bell", "booster", "nozzle", "turbine", "case", "lip") for n in groups[g]], True, True, False)
    t = Tex(m.tex_w, m.tex_h)
    k.paint(t, groups["bell"] + groups["nozzle"], W.metal(W.GUNMETAL, 0.3))
    k.paint(t, groups["booster"], W.metal(W.GUNMETAL, 0.38))
    k.paint(t, groups["turbine"], W.metal(W.TUNGSTEN, 0.45))
    k.paint(t, groups["case"], W.metal(W.STEEL, 0.6, brushed=0.06))
    k.paint(t, groups["lip"], W.metal(W.STEEL, 0.72))
    k.paint(t, groups["line"], W.metal(W.STEEL, 0.45))
    k.paint(t, groups["pump"], W.paint(W.OLIVE, 0.5))
    k.overlay(t, groups["booster"], lambda f, img: MS.hazard(4.6, 5.8, width=0.7)(k, f, img))
    k.overlay(t, groups["bell"] + groups["nozzle"], lambda f, img: MS.soot(k, f, img))

    def mouth(f, img):
        if f.d != "up":
            return
        d = np.hypot(f.X - 8, f.Z - 8)
        dark = d < 2.1
        img[dark, :3] = (img[dark, :3] * 0.2).astype(np.uint8)              # the intake's mouth
        img[d < 0.7, :3] = (150, 154, 160)                                  # the fan's hub
    k.overlay(t, groups["lip"], mouth)
    G.finish(m, t)


def inertial() -> None:
    m = model("inertial_guidance_unit")
    m.box("box", (4.5, 1, 4.5), (11.5, 5, 11.5))
    m.box("ring_x", (5, 5, 7.6), (11, 9.5, 8.4), faces=SIDES + ("up",))
    m.box("ring_z", (7.6, 5, 5), (8.4, 9.25, 11), faces=SIDES + ("up",))
    m.box("rotor", (7, 6.5, 7), (9, 8, 9))
    m.layout()
    t = Tex(m.tex_w, m.tex_h)

    def box(t, r, d):
        shaded(OLIVE)(t, r, d)
        x, y, w, h = r
        if d == "north" and w >= 5:
            t.rect(x + 1, y + 1, 3, 1, YELLOW.base)                         # a stencil plate
            t.px(x + w - 2, y + 1, P.INDICATOR_GREEN[4])                    # the power lamp
    m.paint_all(t, "box", box)

    def ring(t, r, d):
        x, y, w, h = r
        if d in ("north", "south") and w >= 4:
            t.outline(x, y, w, h, BRASS.base)                               # a hoop: its middle stays clear
            t.px(x, y, BRASS.highlight)
        else:
            t.rect(x, y, w, h, BRASS.shadow)
    fill_all(m, t, ["ring_x", "ring_z"], ring)
    m.paint_all(t, "rotor", shaded(STEEL))
    G.finish(m, t)


def gps() -> None:
    m = model("gps_guidance_unit")
    m.box("box", (4.5, 1, 4.5), (11.5, 5, 11.5))
    dome = prism(m, "dome", 8, 8, 2.2, 1.4, 5, 6.5)
    m.box("dome_top", (7.2, 6.5, 7.2), (8.8, 7.2, 8.8))
    m.layout()
    t = Tex(m.tex_w, m.tex_h)

    def box(t, r, d):
        shaded(GUN)(t, r, d)
        x, y, w, h = r
        if d == "north" and w >= 5:
            t.px(x + 1, y + 1, P.INDICATOR_GREEN[4])                        # status lamp
            t.hline(x + 3, y + 2, w - 5, GUN.light)                         # a vent slot
    m.paint_all(t, "box", box)
    fill_all(m, t, dome + ["dome_top"], shaded(WHITE))
    G.finish(m, t)


def hardened() -> None:
    m = model("hardened_electronics")
    m.box("box", (4, 1, 5), (12, 6, 11))
    m.box("lid", (4.5, 6, 5.5), (11.5, 6.6, 10.5))
    m.box("cable", (12, 2, 7.5), (13.6, 3, 8.5), faces=("north", "south", "east", "up", "down"))
    m.layout()
    t = Tex(m.tex_w, m.tex_h)

    def box(t, r, d):
        shaded(LEAD)(t, r, d)
        x, y, w, h = r
        if d in SIDES:
            for xx in range(x, x + w, 3):
                t.px(xx, y + h - 1, LEAD.deep)                              # rivets along the foot
        if d == "north" and w >= 6:
            cx = x + w // 2                                                 # a yellow shield
            t.rect(cx - 2, y + 1, 4, 2, YELLOW.base)
            t.hline(cx - 1, y + 3, 2, YELLOW.base)
    m.paint_all(t, "box", box)
    m.paint_all(t, "lid", shaded(STEEL))

    def cable(t, r, d):
        x, y, w, h = r
        for xx in range(x, x + w):
            t.vline(xx, y, h, P.RUBBER[3] if xx % 2 else STEEL[3])          # braided
    m.paint_all(t, "cable", cable)
    G.finish(m, t)


NAMES = ["tactical_missile_body", "medium_missile_body", "heavy_missile_body", "strategic_missile_body", "solid_rocket_motor_i", "solid_rocket_motor_ii",
         "liquid_fuel_stage", "cruise_engine", "inertial_guidance_unit", "gps_guidance_unit", "hardened_electronics"]


def main() -> None:
    if sys.argv[1:] == ["cruise_engine"]:                        # just the Cruise Engine (slice 9c)
        cruise_engine()
        G.item_defs(["cruise_engine"])
        print("cruise engine written")
        return
    body("tactical_missile_body", 2.5, 1.5, 11, 4, 1)
    body("medium_missile_body", 3, 2, 12.5, 4.6, 2)
    body("heavy_missile_body", 3.5, 2.5, 14, 5.4, 3)
    strategic_body()
    motor("solid_rocket_motor_i", 9, 2.6, 1.6, 1, NOZZLE)
    motor("solid_rocket_motor_ii", 12, 3, 2, 2, P.TUNGSTEN)
    liquid_stage()
    inertial()
    gps()
    hardened()
    G.item_defs(NAMES)
    print("missile parts written")


if __name__ == "__main__":
    main()
