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
    gun, steel = P.GUNMETAL, P.STEEL

    def floor(t, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, steel[tone(d)])
        if d == "up":
            for yy in range(y + 1, y + h - 1, 2):
                t.hline(x + 1, yy, w - 2, gun[1])                          # grate slots
            t.rect(x + w // 2 - 1, y + h // 2 - 1, 2, 2, P.BLACK_PAINT[0])  # drain
        elif d == "north":
            t.stripes(x, y, w, h, P.HAZARD_YELLOW.base, P.HAZARD_BLACK.base, width=2)
    g.paint_all(tex, parts["floor"][0], floor)

    def tiles(t, x, y, w, h):
        for yy in range(h):
            for xx in range(w):
                t.px(x + xx, y + yy, PANEL[5] if (xx % 4 == 0 or yy % 4 == 0) else PANEL[4])

    def outside(t, x, y, w, h, shade):
        """Painted NATO-green panels in a steel frame, with a yellow hazard placard at eye height."""
        t.rect(x, y, w, h, P.NATO_GREEN[shade])
        t.outline(x, y, w, h, steel[3])
        for yy in range(y + 7, y + h - 2, 8):
            t.hline(x + 1, yy, w - 2, P.NATO_GREEN[shade - 1])
        if w >= 8:
            px, py = x + w // 2 - 3, y + 9
            t.rect(px, py, 6, 6, P.HAZARD_YELLOW.base)
            t.outline(px, py, 6, 6, P.HAZARD_BLACK.base)
            t.rect(px + 2, py + 2, 2, 2, P.HAZARD_BLACK.base)

    def wall(t, r, d, inner):
        x, y, w, h = r
        if d == inner:
            tiles(t, x, y, w, h)
        elif d in ("east", "west"):
            outside(t, x, y, w, h, 3 if d == "west" else 2)
        elif d == "north":
            t.stripes(x, y, w, h, P.HAZARD_YELLOW.base, P.HAZARD_BLACK.base, width=2)
        else:
            t.rect(x, y, w, h, steel[tone(d)])
    g.paint_all(tex, parts["wall"][0], lambda t, r, d: wall(t, r, d, "east"))
    g.paint_all(tex, parts["wall"][1], lambda t, r, d: wall(t, r, d, "west"))

    def back(t, r, d):
        x, y, w, h = r
        if d == "north":
            tiles(t, x, y, w, h)
        elif d == "south":
            outside(t, x, y, w, h, 3)                                       # plain panel: the riser is inside
        else:
            t.rect(x, y, w, h, steel[tone(d)])
    g.paint_all(tex, parts["back"][0], back)

    def canopy(t, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, steel[tone(d)])
        if d == "north":
            t.stripes(x, y, w, h, P.HAZARD_YELLOW.base, P.HAZARD_BLACK.base, width=2)
        elif d == "down":
            t.outline(x, y, w, h, steel[2])
        elif d == "up":
            t.outline(x, y, w, h, steel[2])
            t.vent(x + 4, y + 4, w - 8, h - 8, steel, slat=2)               # extractor vent
    g.paint_all(tex, parts["canopy"][0], canopy)
    g.paint_all(tex, parts["riser"][0], lambda t, r, d: (t.rect(*r, P.CHERENKOV[tone(d) - 1]), t.vline(r[0], r[1], r[3], P.CHERENKOV[4])))

    def valve(t, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, P.NATO_GREEN[tone(d)])
        if d == "north":
            t.outline(x, y, w, h, P.NATO_GREEN[1])
            t.rect(x + 1, y + h - 2, 1, 1, P.WHITE_CERAMIC[5])              # pressure gauge
    g.paint_all(tex, parts["valve"][0], valve)
    g.paint_all(tex, lamp, lambda t, r, d: t.rect(*r, P.INDICATOR_GREEN.light))
    g.paint_all(glow, lamp, lambda t, r, d: t.rect(*r, P.INDICATOR_GREEN.light))
    g.paint_all(tex, parts["handle"][0], lambda t, r, d: t.rect(*r, P.WARNING_RED[tone(d) + 1]))
    for b in parts["nozzle"]:
        g.paint_all(tex, b, lambda t, r, d: (t.rect(*r, P.HAZARD_YELLOW[tone(d) + 1]), t.px(r[0], r[1], P.BLACK_PAINT[1]) if d == "down" else None))
    for b in parts["drop"]:
        g.paint_all(tex, b, lambda t, r, d: (t.rect(*r, WATER[5]), t.px(r[0], r[1] + r[3] - 1, WATER[6])))
    return g, tex, glow


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
