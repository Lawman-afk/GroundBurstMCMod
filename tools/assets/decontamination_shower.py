"""Decontamination Shower (GeckoLib block model + animation), DESIGN.md §8. Two blocks tall, walk-in.

Look: a booth open at the front (north) on a steel floor grate with a central drain. White panelled side walls in a
steel frame with yellow-and-black hazard edging, a back wall carrying the water riser, a valve box with a lamp and
a pressure gauge, and a steel canopy with four yellow nozzles. While someone stands inside, streams of water fall
from the nozzles (bones "spray0".."spray3" under the parent "spray", hidden when idle). The collision shape matches:
1 px walls, back wall, floor grate, and a canopy high enough to stand under.
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import numpy as np  # noqa: E402

from gz import machine_kit as K  # noqa: E402
from gz import materials as M  # noqa: E402
from gz import palette as P  # noqa: E402
from gz.geo import Animation, GeoModel, save_animations  # noqa: E402
from gz.palette import fixed  # noqa: E402
from gz.tex import Tex  # noqa: E402

NAME = "decontamination_shower"
PANEL = fixed("panel_white", "#5a6268", "#7c858c", "#a2aab0", "#c4cacd", "#dadfe1", "#eaedee", "#f8f9f9")
WATER = fixed("spray", "#2c66b0", "#4282cc", "#6aa4e0", "#8cbcea", "#acd0f2", "#cce2f8", "#eef6fe")
CANOPY_Y = 30.5
NOZZLES = [(-3.5, -3.0), (3.5, -3.0), (-3.5, 3.5), (3.5, 3.5)]
FALL = 8.0                       # how far a water drop falls in one cycle


def tone(d: str) -> int:
    return {"up": 4, "down": 2}.get(d, 3)


def build() -> tuple[GeoModel, Tex, Tex]:
    g = GeoModel(NAME, tex_size=(128, 128))
    g.bone("frame")
    g.bone("spray", pivot=(0, 20, 0), parent="frame")
    for i in range(4):
        g.bone(f"spray{i}", pivot=(0, 20, 0), parent="spray")
    F = "frame"
    parts: dict[str, list] = {}

    def add(group, box):
        parts.setdefault(group, []).append(box)
        return box

    add("floor", g.box("floor", (-8, 0, -8), (8, 1, 8), bone=F))
    add("wall", g.box("wall_w", (-8, 1, -8), (-7, CANOPY_Y, 7), bone=F))
    add("wall", g.box("wall_e", (7, 1, -8), (8, CANOPY_Y, 7), bone=F))
    add("back", g.box("back", (-8, 1, 7), (8, CANOPY_Y, 8), bone=F))
    add("canopy", g.box("canopy", (-8, CANOPY_Y, -8), (8, 32, 8), bone=F))
    add("riser", g.box("riser", (-0.6, 1, 6.2), (0.6, CANOPY_Y, 7), faces=("north", "east", "west"), bone=F))
    add("valve", g.box("valve", (2.2, 15, 5.8), (5.4, 19, 7), faces=("north", "east", "west", "up", "down"), bone=F))
    lamp = add("lamp", g.box("lamp", (3.0, 17.6, 5.6), (3.8, 18.4, 5.8), faces=("north",), shade=False, bone=F))
    add("handle", g.box("handle", (-4.6, 13, 5.6), (-2.6, 13.6, 7), faces=("north", "east", "west", "up", "down"), bone=F))
    for i, (x, z) in enumerate(NOZZLES):
        add("nozzle", g.box(f"nozzle{i}", (x - 0.6, CANOPY_Y - 1.0, z - 0.6), (x + 0.6, CANOPY_Y, z + 0.6), faces=("north", "south", "east", "west", "down"), bone=F))
        # Streaks down to the floor, staggered so the fall looks continuous; each moves one gap per cycle.
        for k in range(int((CANOPY_Y - 3) // (FALL / 2))):
            y = CANOPY_Y - 1.5 - k * FALL / 2
            add("drop", g.box(f"drop{i}_{k}", (x - 0.2, y - 2.6, z - 0.2), (x + 0.2, y, z + 0.2), faces=("north", "south", "east", "west"), bone=f"spray{i}"))

    g.layout()
    tex, glow = Tex(g.tex_w, g.tex_h), Tex(g.tex_w, g.tex_h)
    paint(g, tex, glow, parts)
    return g, tex, glow


def paint(g: GeoModel, tex: Tex, glow: Tex, parts: dict) -> None:
    """The shower's surfaces, in model space (tools/gz/machine_kit.py; the machines' remaster): a steel grate floor with
    a drain and a hazard front; white tiles inside, NATO-green panels outside with a hazard placard, hazard-striped
    front edges; a steel canopy with an extractor vent; a blue riser, a green valve box, a red handle, yellow nozzles,
    the falling spray."""
    k = K.Kit(g, parts, seed=331)
    k.paint(tex, ["floor", "canopy"], k.STEEL)
    k.paint(tex, ["floor", "canopy"], k.HAZARD, only=("north",))

    def grate(f, img):
        a, b = f.plane_uv()
        M.darken(img, (K.ring(f) >= 1) & ((np.floor(b) % 2) == 1), 0.5)
    k.overlay(tex, "floor", grate, only=("up",))
    g.paint_all(tex, parts["floor"][0], lambda t, r, d: t.rect(r[0] + r[2] // 2 - 1, r[1] + r[3] // 2 - 1, 2, 2, P.BLACK_PAINT[0])
                if d == "up" else None)                                            # the drain
    tiles = M.white_paint(tone=0.8, wear=0.0, grime=0.14)

    def grout(f, img):
        a, b = f.plane_uv()
        M.darken(img, ((np.floor(a) % 4) == 0) | ((np.floor(b) % 4) == 0), 0.86)
    outside = M.painted(K.ramp_of(P.NATO_GREEN), tone=0.5, wear=0.2)

    def panels(f, img):
        M.darken(img, K.ring(f) < 1, 0.62)                                          # the steel frame's shadow line
        M.seams(f, img, every=8, axis="v", offset=7, light=False)
    for i, inner in enumerate(("east", "west")):
        name = parts["wall"][i].name
        k.paint(tex, name, outside)
        k.paint(tex, name, tiles, only=(inner,))
        k.overlay(tex, name, grout, only=(inner,))
        k.paint(tex, name, k.HAZARD, only=("north",))
        k.paint(tex, name, k.STEEL, only=("up", "down", "south"))
        k.overlay(tex, name, panels, only=tuple({"east", "west"} - {inner}))
    k.paint(tex, "back", outside)
    k.paint(tex, "back", tiles, only=("north",))
    k.overlay(tex, "back", grout, only=("north",))
    k.paint(tex, "back", k.STEEL, only=("up", "down", "east", "west"))
    k.overlay(tex, "back", panels, only=("south",))

    def placard(t, r, d):
        x, y, w, h = r
        if w >= 8:
            px, py = x + w // 2 - 3, y + 9
            t.rect(px, py, 6, 6, P.HAZARD_YELLOW.base)
            t.outline(px, py, 6, 6, P.HAZARD_BLACK.base)
            t.rect(px + 2, py + 2, 2, 2, P.HAZARD_BLACK.base)
    g.paint_all(tex, parts["wall"][0], lambda t, r, d: placard(t, r, d) if d == "west" else None)
    g.paint_all(tex, parts["wall"][1], lambda t, r, d: placard(t, r, d) if d == "east" else None)

    def vent(f, img):
        a, b = f.plane_uv()
        inside = K.ring(f) >= 4
        M.darken(img, inside & ((np.floor(b) % 2) == 0), 0.45)
    k.overlay(tex, "canopy", vent, only=("up",))
    k.paint(tex, "riser", M.painted(K.ramp_of(P.CHERENKOV), tone=0.5, wear=0.15))
    k.paint(tex, "valve", k.NATO)
    g.paint_all(tex, parts["valve"][0], lambda t, r, d: t.px(r[0] + 1, r[1] + r[3] - 2, P.WHITE_CERAMIC[5]) if d == "north" else None)
    g.paint_all(tex, parts["lamp"][0], lambda t, r, d: t.rect(*r, P.INDICATOR_GREEN.light))
    g.paint_all(glow, parts["lamp"][0], lambda t, r, d: t.rect(*r, P.INDICATOR_GREEN.light))
    k.paint(tex, "handle", k.RED)
    k.paint(tex, "nozzle", k.YELLOW)
    for b in parts["nozzle"]:
        g.paint_all(tex, b, lambda t, r, d: t.px(r[0], r[1], P.BLACK_PAINT[1]) if d == "down" else None)
    for b in parts["drop"]:
        g.paint_all(tex, b, lambda t, r, d: (t.rect(*r, WATER[5]), t.px(r[0], r[1] + r[3] - 1, WATER[6])))


def animations() -> list[Animation]:
    """0.4 s cycle: each streak falls half the distance to the floor and starts again (two per nozzle, staggered)."""
    w = Animation("working", 0.4, loop=True)
    for i in range(4):
        off = i * 0.1
        keys = {}
        for k in range(5):
            t = round(k * 0.1, 2)
            phase = ((t + off) % 0.4) / 0.4
            keys[t] = (0, -phase * FALL / 2, 0)
        w.move(f"spray{i}", keys)
    return [w]


if __name__ == "__main__":
    model, texture, glowmask = build()
    for p in model.save(texture, glowmask):
        print(p)
    print(save_animations(NAME, animations()))
