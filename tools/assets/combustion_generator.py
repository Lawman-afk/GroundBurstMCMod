"""Combustion Generator (GeckoLib block model + animation), DESIGN.md §7.

Look: a squat gunmetal engine block on the same hazard-striped plinth as the Ore Crusher. The front (north) has a
cast-iron firebox door with a grille; behind it sits a fire panel that is tucked away at rest and slides forward to
glow through the bars while burning (glowmask). An exhaust stack with a rain cap rises from the back corner, a
flywheel spins on the west side, a pressure gauge sits above the door and copper output terminals are on the back.
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

NAME = "combustion_generator"


def tone(d: str) -> int:
    return {"up": 4, "down": 2}.get(d, 3)


def build() -> tuple[GeoModel, Tex, Tex]:
    g = GeoModel(NAME, tex_size=(128, 128))
    g.bone("frame")
    g.bone("fire", pivot=(0, 5, -6), parent="frame")
    g.bone("flywheel", pivot=(-9.5, 6, 0), parent="frame")
    g.bone("cap", pivot=(4.5, 16, 4.5), parent="frame")
    g.bone("needle", pivot=(0, 10.1, -7.6), parent="frame")

    F = "frame"
    parts: dict[str, list] = {}

    def add(group, box):
        parts.setdefault(group, []).append(box)
        return box

    add("plinth", g.box("plinth", (-8, 0, -8), (8, 2, 8), bone=F))
    # Engine block with a firebox opening (x -3..3, y 3..7) cut out of the front: built from four slabs.
    add("block", g.box("block_rear", (-7, 2, -5), (7, 12, 7), bone=F))
    add("front", g.box("front_l", (-7, 2, -7), (-3, 12, -5), faces=("north", "west", "up", "down", "east"), bone=F))
    add("front", g.box("front_r", (3, 2, -7), (7, 12, -5), faces=("north", "east", "up", "down", "west"), bone=F))
    add("front", g.box("front_t", (-3, 7, -7), (3, 12, -5), faces=("north", "up", "down"), bone=F))
    add("front", g.box("front_b", (-3, 2, -7), (3, 3, -5), faces=("north", "up"), bone=F))
    add("lid", g.box("lid", (-7.5, 12, -7.5), (7.5, 13, 7.5), bone=F))
    # Firebox: soot-black back wall, a fire panel just behind it at rest, grille bars and door frame in front.
    add("firebox", g.box("firebox_back", (-3, 3, -5.2), (3, 7, -5.15), faces=("north",), bone=F))
    fire = add("fire", g.box("fire_panel", (-2.8, 3.1, -4.6), (2.8, 6.9, -4.5), faces=("north",), shade=False, bone="fire"))
    for i, x in enumerate((-1.75, -0.25, 1.25)):
        add("bar", g.box(f"bar{i}", (x, 3, -7.1), (x + 0.5, 7, -6.8), faces=("north", "east", "west"), bone=F))
    add("doorframe", g.box("door_top", (-3.5, 7, -7.5), (3.5, 8, -7), faces=("north", "up", "down", "east", "west"), bone=F))
    add("doorframe", g.box("door_bot", (-3.5, 2.5, -7.5), (3.5, 3, -7), faces=("north", "up", "east", "west"), bone=F))
    add("handle", g.box("handle", (3.5, 4.5, -8), (4.5, 5.5, -7), faces=("north", "east", "up", "down"), bone=F))
    # Pressure gauge above the door.
    add("gauge", g.box("gauge", (-1.5, 8.6, -7.4), (1.5, 11.6, -7), faces=("north", "east", "west", "up", "down"), bone=F))
    add("needle", g.box("needle", (-0.15, 10.1, -7.65), (0.15, 11.3, -7.6), faces=("north",), shade=False, bone="needle"))
    # Exhaust stack (back-east corner) with a rain cap that chatters while running.
    add("stack", g.box("stack", (3, 13, 3), (6, 19, 6), faces=("north", "south", "east", "west"), bone=F))
    add("stack_top", g.box("stack_top", (3, 18.99, 3), (6, 19, 6), faces=("up",), bone=F))
    add("collar", g.box("collar", (2.5, 13, 2.5), (6.5, 14, 6.5), faces=("north", "south", "east", "west", "up"), bone=F))
    add("cap", g.box("cap", (2.5, 19.5, 2.5), (6.5, 20.2, 6.5), bone="cap"))
    add("cap_post", g.box("cap_post", (4.2, 19, 4.2), (4.8, 19.5, 4.8), faces=("north", "south", "east", "west"), bone="cap"))
    # Fuel hopper lid on top (front-west), intake vents on the east side.
    add("hatch", g.box("hatch", (-6, 13, -6), (-1, 13.5, -1), faces=("north", "south", "east", "west", "up"), bone=F))
    # West: flywheel on a stub axle.
    add("axle", g.box("axle", (-8, 5.25, -0.75), (-7, 6.75, 0.75), faces=("north", "south", "up", "down"), bone=F))
    add("wheel", g.box("wheel", (-9.5, 2.5, -3.5), (-8, 9.5, 3.5), faces=("north", "south", "west", "east", "up", "down"), bone="flywheel"))
    add("wheel", g.box("wheel45", (-9.35, 2.5, -3.5), (-8.15, 9.5, 3.5), faces=("north", "south", "west", "east", "up", "down"),
                       rotation={"origin": (-8.75, 6, 0), "x": 45}, bone="flywheel"))
    add("hub", g.box("hub", (-10, 5, -1), (-9.5, 7, 1), faces=("west", "north", "south", "up", "down"), bone="flywheel"))
    # Back: copper output terminals.
    for i, x in enumerate((-4, 1)):
        add("terminal", g.box(f"terminal{i}", (x, 6, 7), (x + 3, 9, 8.5), faces=("south", "east", "west", "up", "down"), bone=F))

    g.layout()
    tex, glow = Tex(g.tex_w, g.tex_h), Tex(g.tex_w, g.tex_h)
    paint(g, tex, glow, parts)
    return g, tex, glow


def paint(g: GeoModel, tex: Tex, glow: Tex, parts: dict) -> None:
    """The generator's surfaces, in model space (tools/gb/machine_kit.py; the machines' remaster): an olive engine block
    in bolted panels on the plinth, the firebox opening sooted inside, a cast-iron door frame and grille, a brass gauge
    and handle, a gunmetal lid and hatch, a sooty exhaust stack, a dark flywheel, copper terminals."""
    k = K.Kit(g, parts, seed=301)
    k.plinth(tex)
    k.paint(tex, ["block", "front"], k.OLIVE)
    k.panels(tex, "block", inset=2, only=("east", "west", "south"))

    def louvres(f, img):                                                       # the air intake on the east side
        a, b = f.plane_uv()
        inside = (f.border["left"] >= 3) & (f.border["right"] >= 3) & (f.border["top"] >= 3) & (f.border["top"] < 7)
        M.darken(img, inside & ((np.floor(b) % 2) == 0), 0.35)
        M.lighten(img, inside & ((np.floor(b) % 2) == 1), 1.15, 6)
    k.overlay(tex, "block", louvres, only=("east",))
    cast = M.steel(tone=0.28, brushed=0.0, mottle=0.06, grain=0.05, dither=0.6)
    soot = M.soot(tone=0.3)
    inner = {"front_l": "east", "front_r": "west", "front_t": "down", "front_b": "up"}
    for name, d in inner.items():
        k.paint(tex, name, soot, only=(d,))                                    # the firebox opening's sooted sides
    k.paint(tex, "firebox", soot)
    k.paint(tex, ["bar", "doorframe"], cast)
    k.paint(tex, "lid", k.GUN)
    k.overlay(tex, "lid", lambda f, img: M.darken(img, K.ring(f) == 1, 0.7), only=("up",))
    k.paint(tex, ["handle", "gauge"], k.BRASS)
    k.paint(tex, ["stack", "collar", "cap", "cap_post"], k.GUN)
    k.soot(tex, "stack", reach=4, strength=0.8)
    k.paint(tex, "stack_top", soot)
    k.paint(tex, "hatch", k.GUN)
    k.paint(tex, ["axle", "hub"], k.STEEL)
    k.paint(tex, "wheel", k.DARK)
    k.paint(tex, "terminal", k.BLACK)

    def fire_paint(t, r, d):
        x, y, w, h = r
        amber = P.NUCLEAR_AMBER
        t.gradient_v(x, y, w, h, P.WARNING_RED[4], amber[5])
        for i in range(w):
            if (i * 7) % 3 == 0:
                t.px(x + i, y, P.WARNING_RED[5])
            if (i * 5) % 4 == 1:
                t.px(x + i, y + h - 1, amber[6])                       # white-hot coal bed
    g.paint_all(tex, parts["fire"][0], fire_paint)
    g.paint_all(glow, parts["fire"][0], fire_paint)

    def gauge(t, r, d):
        if d == "north":
            K.dial(t, r[0], r[1], min(r[2], r[3]))
            t.px(r[0] + r[2] - 2, r[1] + 1, P.WARNING_RED.base)               # red line
    g.paint_all(tex, parts["gauge"][0], gauge)
    g.paint_all(tex, parts["needle"][0], lambda t, r, d: t.rect(*r, P.BLACK_PAINT.deep))
    g.paint_all(tex, parts["block"][0], lambda t, r, d: K.plate(t, r[0] + r[2] - 6, r[1] + r[3] - 5) if d == "south" else None)
    g.paint_all(tex, parts["hatch"][0], lambda t, r, d: t.px(r[0] + r[2] // 2, r[1] + r[3] // 2, P.BRASS.light) if d == "up" else None)

    def wheel(t, r, d):
        if d == "west":
            t.px(r[0] + r[2] // 2, r[1] + 1, P.HAZARD_YELLOW.base)              # balance mark shows the spin
    for b in parts["wheel"]:
        g.paint_all(tex, b, wheel)

    def terminal(t, r, d):
        x, y, w, h = r
        if d == "south":
            t.rect(x + 1, y + 1, max(1, w - 2), max(1, h - 2), P.COPPER.light)
            t.px(x + 1, y + 1, P.COPPER.shine)
    for b in parts["terminal"]:
        g.paint_all(tex, b, terminal)


def animations() -> list[Animation]:
    working = Animation("working", 1.0, loop=True)
    # The fire panel slides from behind the soot wall to just in front of it and flickers.
    working.move("fire", {0: (0, 0, -0.9), 0.25: (0, 0, -0.95), 0.5: (0, 0, -0.9), 0.75: (0, 0, -0.95), 1: (0, 0, -0.9)})
    working.scale("fire", {0: (1, 1, 1), 0.2: (1, 0.92, 1), 0.45: (1, 1.0, 1), 0.7: (1, 0.95, 1), 1: (1, 1, 1)})
    working.rotate("flywheel", {0: (0, 0, 0), 1: (-720, 0, 0)})
    working.rotate("cap", {0: (0, 0, 0), 0.125: (4, 0, 2), 0.25: (0, 0, 0), 0.375: (-3, 0, -2), 0.5: (0, 0, 0),
                           0.625: (3, 0, 2), 0.75: (0, 0, 0), 0.875: (-4, 0, -2), 1: (0, 0, 0)})
    working.rotate("needle", {0: (0, 0, -35), 0.5: (0, 0, -42), 1: (0, 0, -35)})
    working.move("frame", {0: (0, 0, 0), 0.125: (0, 0.05, 0), 0.25: (0, 0, 0), 0.375: (0, 0.05, 0), 0.5: (0, 0, 0),
                           0.625: (0, 0.05, 0), 0.75: (0, 0, 0), 0.875: (0, 0.05, 0), 1: (0, 0, 0)})
    return [working]


if __name__ == "__main__":
    model, texture, glowmask = build()
    for p in model.save(texture, glowmask):
        print(p)
    print(save_animations(NAME, animations()))
