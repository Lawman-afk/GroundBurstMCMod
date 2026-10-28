"""Ore Crusher (GeckoLib block model + animation). Phase 2 pilot for the animated-model pipeline.

Look (DESIGN.md §8): olive steel hopper over twin toothed rollers that visibly spin. A riveted olive housing on a
gunmetal plinth with a hazard-striped kick plate, a discharge chute and status lamp on the front (north), an
electric drive motor with cooling fins and a spinning pulley on the east side, a power inlet on the back.
Geometry is centred on the block's bottom centre (x, z in -8..8).
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import numpy as np  # noqa: E402

from gb import machine_kit as K  # noqa: E402
from gb import materials as M  # noqa: E402
from gb import palette as P  # noqa: E402
from gb.geo import Animation, GeoModel, save_animations  # noqa: E402
from gb.tex import Tex  # noqa: E402

NAME = "ore_crusher"


def tone(d: str) -> int:
    return {"up": 4, "down": 2}.get(d, 3)


def build() -> tuple[GeoModel, Tex, Tex]:
    g = GeoModel(NAME, tex_size=(128, 128))
    g.bone("frame")
    # Roller axes sit high enough that the spinning teeth never dip through the throat plate (y 12).
    RY = 14.2
    g.bone("roller_n", pivot=(0, RY, -2.5), parent="frame")
    g.bone("roller_s", pivot=(0, RY, 2.5), parent="frame")
    g.bone("pulley", pivot=(11.5, 6.5, 0), parent="frame")

    F = "frame"
    parts: dict[str, list] = {}

    def add(group, box):
        parts.setdefault(group, []).append(box)
        return box

    # Plinth and housing
    add("plinth", g.box("plinth", (-8, 0, -8), (8, 2, 8), bone=F))
    add("housing", g.box("housing", (-7, 2, -7), (7, 11, 7), bone=F))
    add("rim", g.box("rim", (-7.5, 11, -7.5), (7.5, 12, 7.5), faces=("north", "south", "east", "west", "down"), bone=F))
    g.open_ok = {("rim", "up")}                     # the hopper mouth: you look down past the rim to the rollers
    # Corner posts give the housing a framed, fabricated look.
    for i, (x, z) in enumerate(((-7.5, -7.5), (6.5, -7.5), (-7.5, 6.5), (6.5, 6.5))):
        add("post", g.box(f"post{i}", (x, 2, z), (x + 1, 11, z + 1), faces=("north", "south", "east", "west"), bone=F))

    # Hopper walls, splayed outward 18 degrees around their bottom edge.
    # The walls start 0.3 above the throat plate so their tilted lower edges never cut through it.
    add("hopper", g.box("hop_n", (-7, 12.3, -7), (7, 16, -6), rotation={"origin": (0, 12.3, -6.5), "x": 18}, bone=F))
    add("hopper", g.box("hop_s", (-7, 12.3, 6), (7, 16, 7), rotation={"origin": (0, 12.3, 6.5), "x": -18}, bone=F))
    add("hopper", g.box("hop_e", (6, 12.3, -6), (7, 16, 6), rotation={"origin": (6.5, 12.3, 0), "z": -18}, bone=F))
    add("hopper", g.box("hop_w", (-7, 12.3, -6), (-6, 16, 6), rotation={"origin": (-6.5, 12.3, 0), "z": 18}, bone=F))
    add("throat", g.box("throat", (-7.5, 11.5, -7.5), (7.5, 12, 7.5), faces=("up",), bone=F))

    # Front: discharge chute + dust tray, status lamp, kick plate.
    add("chute", g.box("chute", (-3, 2, -8.5), (3, 6, -7), faces=("north", "east", "west", "up"), bone=F))
    add("tray", g.box("tray", (-4, 2, -9.5), (4, 3, -7), faces=("north", "east", "west", "up"), bone=F))
    lamp = add("lamp", g.box("lamp", (4, 8.5, -7.5), (5.5, 10, -7), faces=("north", "east", "west", "up", "down"), bone=F))

    # East side: drive motor with fins and an end bell; spinning pulley.
    add("motor", g.box("motor", (7, 4, -3), (10.5, 9, 3), faces=("north", "south", "east", "up", "down"), bone=F))
    for i in range(5):
        z = -2.5 + i * 1.25
        add("fin", g.box(f"fin{i}", (7, 9, z), (10.5, 9.5, z + 0.5), faces=("north", "south", "east", "up"), bone=F))
    add("pulley", g.box("pulley_hub", (10.5, 5.5, -1), (11.5, 7.5, 1), faces=("north", "south", "east", "up", "down"), bone="pulley"))
    add("pulley", g.box("pulley_disc", (11.5, 4, -2.5), (12.5, 9, 2.5), faces=("north", "south", "east", "up", "down"), bone="pulley"))
    add("pulley", g.box("pulley_disc2", (11.65, 4, -2.5), (12.35, 9, 2.5), faces=("north", "south", "east", "up", "down"),
                        rotation={"origin": (12, 6.5, 0), "x": 45}, bone="pulley"))

    # Back: power inlet.
    add("inlet", g.box("inlet", (-2, 4, 7), (2, 7, 8.5), faces=("south", "east", "west", "up", "down"), bone=F))

    # Rollers: octagonal cores with eight teeth each, along X.
    for rn, zc in (("roller_n", -2.5), ("roller_s", 2.5)):
        add("roller", g.box(f"{rn}_core", (-5.5, RY - 1.0, zc - 1.0), (5.5, RY + 1.0, zc + 1.0), bone=rn))
        add("roller", g.box(f"{rn}_core45", (-5.35, RY - 1.0, zc - 1.0), (5.35, RY + 1.0, zc + 1.0),
                            rotation={"origin": (0, RY, zc), "x": 45}, bone=rn))
        for k in range(8):
            add("tooth", g.box(f"{rn}_tooth{k}", (-5, RY + 1.05, zc - 0.4), (5, RY + 1.65, zc + 0.4), faces=("up", "north", "south"),
                               rotation={"origin": (0, RY, zc), "x": k * 45 + 22.5}, bone=rn))

    g.layout()
    tex, glow = Tex(g.tex_w, g.tex_h), Tex(g.tex_w, g.tex_h)
    paint(g, tex, glow, parts)
    return g, tex, glow


def paint(g: GeoModel, tex: Tex, glow: Tex, parts: dict) -> None:
    """The crusher's surfaces, in model space (tools/gb/machine_kit.py; the machines' remaster): an olive housing in a
    bolted access panel on the plinth, a nameplate and a chevron; a galvanised hopper on gunmetal posts and rim, its
    throat dark; tungsten rollers and teeth; a gunmetal motor with dark fins and a steel pulley; the copper inlet."""
    k = K.Kit(g, parts, seed=303)
    k.plinth(tex)
    k.paint(tex, "housing", k.OLIVE)
    k.panels(tex, "housing", inset=2)
    k.paint(tex, ["rim", "post", "chute", "tray", "motor"], k.GUN)
    k.paint(tex, "hopper", k.GALV)

    def rolled_lip(f, img):
        if f.vertical:
            M.lighten(img, f.border["top"] < 1, 1.2, 10)
    k.overlay(tex, "hopper", rolled_lip)
    k.paint(tex, "throat", k.DARK)
    k.overlay(tex, "throat", lambda f, img: img.__setitem__((K.ring(f) >= 2, slice(0, 3)), P.BLACK_PAINT.deep[:3]))
    k.paint(tex, "fin", k.DARK)
    k.ribs(tex, "motor", every=2, k=0.72, along="u", only=("north", "south", "up"))
    k.paint(tex, "pulley", k.STEEL)
    k.paint(tex, "inlet", k.BLACK)
    tungsten = M.steel(tone=0.42, brushed=0.03, mottle=0.05)
    k.paint(tex, "roller", tungsten)
    k.paint(tex, "tooth", M.steel(tone=0.62, brushed=0.0))

    def housing(t, r, d):
        x, y, w, h = r
        if d == "north":
            K.plate(t, x + 3, y + 3, 7, 3)                                         # nameplate with a yellow ID stripe
        if d == "west":
            t.decal("chevron5x3", x + w // 2 - 2, y + h // 2 - 1, P.HAZARD_YELLOW.base)
    g.paint_all(tex, parts["housing"][0], housing)

    def chute(t, r, d):
        x, y, w, h = r
        if d == "north":
            t.rect(x + 1, y + h - 3, w - 2, 2, P.BLACK_PAINT.deep)                 # discharge mouth
            t.hline(x + 1, y + h - 1, w - 2, P.DESERT_TAN.shadow)                  # spilled dust
    for b in parts["chute"]:
        g.paint_all(tex, b, chute)
    g.paint_all(tex, parts["motor"][0], lambda t, r, d: t.ring(r[0] + r[2] / 2, r[1] + r[3] / 2, 1.0, 2.4, P.GUNMETAL.light) if d == "east" else None)
    for b in parts["pulley"]:
        if "disc" in b.name:
            g.paint_all(tex, b, lambda t, r, d: t.px(r[0] + r[2] // 2, r[1] + 1, P.WARNING_RED.base) if d == "east" else None)
    g.paint_all(tex, parts["inlet"][0], lambda t, r, d: t.rect(r[0] + 1, r[1] + 1, max(1, r[2] - 2), max(1, r[3] - 2), P.COPPER.light)
                if d == "south" else None)

    def lamp_paint(t, r, d):
        t.rect(*r, P.INDICATOR_GREEN.base)
        t.px(r[0], r[1], P.INDICATOR_GREEN.shine)
    g.paint_all(tex, parts["lamp"][0], lamp_paint)
    g.paint_all(glow, parts["lamp"][0], lamp_paint)


def animations() -> list[Animation]:
    working = Animation("working", 1.0, loop=True)
    working.rotate("roller_n", {0: (0, 0, 0), 1: (360, 0, 0)})
    working.rotate("roller_s", {0: (0, 0, 0), 1: (-360, 0, 0)})
    working.rotate("pulley", {0: (0, 0, 0), 1: (720, 0, 0)})
    return [working]


if __name__ == "__main__":
    model, texture, glowmask = build()
    for p in model.save(texture, glowmask):
        print(p)
    print(save_animations(NAME, animations()))
