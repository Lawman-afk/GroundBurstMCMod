"""Chemical Plant, formed (GeckoLib model + animation), DESIGN.md §8 / §23.3. A 2x2x2 structure drawn from its
controller, which sits at the front-left (north-west) bottom; the model spans x -8..24, z -8..24, y 0..32 in the
north-facing frame (model origin = controller block bottom centre).

Look: a steel skid with a hazard-striped front edge; two tall round process tanks (stepped pixel circles) with a
vertical glass window on the front showing the liquid inside (acid green, fuel amber) and bubbles rising through it;
a stirrer motor on the left tank turning a paddle shaft you can see through the window; a manifold of pipes and red
hand-wheel valves between the tanks; a ladder on the back; the control station (green CRT) at the controller corner.
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from gz import palette as P  # noqa: E402
from gz.geo import Animation, GeoModel, save_animations  # noqa: E402
from gz.palette import fixed  # noqa: E402
from gz.tex import Tex  # noqa: E402

NAME = "chemical_plant"
ACID = fixed("acid", "#1c3208", "#2c4c0c", "#3e6a12", "#56901a", "#74b428", "#9cd44a", "#d0f090")
FUEL = fixed("fuel", "#3a1c04", "#5c2c06", "#844208", "#b05e10", "#d4801c", "#eca440", "#fcd890")
CLEAR = (0, 0, 0, 0)
TANK_Y0, TANK_Y1 = 3.0, 26.0
TANKS = [(0.0, 8.0, ACID), (16.0, 8.0, FUEL)]
# Stepped round tank: (dz0, dz1, half-width in x) around the tank centre.
BANDS = [(-6.5, -5.0, 3.0), (-5.0, -3.5, 5.0), (-3.5, 3.5, 6.5), (3.5, 5.0, 5.0), (5.0, 6.5, 3.0)]
WINDOW = (-1.5, 1.5)          # x range of the window slot on the front band (relative to tank centre)


def tone(d: str) -> int:
    return {"up": 4, "down": 2}.get(d, 3)


def build() -> tuple[GeoModel, Tex, Tex]:
    g = GeoModel(NAME, tex_size=(256, 256))
    g.bone("frame")
    g.bone("stirrer", pivot=(0, 15, 8), parent="frame")
    for t in range(2):
        for i in range(4):
            g.bone(f"bubble{t}_{i}", pivot=(TANKS[t][0], 6, TANKS[t][1]), parent="frame")
    F = "frame"
    parts: dict[str, list] = {}

    def add(group, box):
        parts.setdefault(group, []).append(box)
        return box

    add("skid", g.box("skid", (-8, 0, -8), (24, 3, 24), bone=F))

    for ti, (cx, cz, liquid) in enumerate(TANKS):
        for bi, (z0, z1, hw) in enumerate(BANDS):
            front = bi == 0
            add(f"tank{ti}_front" if front else f"tank{ti}", g.box(f"tank{ti}_b{bi}", (cx - hw, TANK_Y0, cz + z0), (cx + hw, TANK_Y1, cz + z1),
                                                                    faces=("north", "south", "east", "west"), bone=F))
        # Dome: two stepped caps.
        add(f"dome{ti}", g.box(f"dome{ti}_a", (cx - 5, TANK_Y1, cz - 5), (cx + 5, TANK_Y1 + 1.5, cz + 5), bone=F))
        add(f"dome{ti}", g.box(f"dome{ti}_b", (cx - 3, TANK_Y1 + 1.5, cz - 3), (cx + 3, TANK_Y1 + 2.5, cz + 3), bone=F))
        # Liquid seen through the window: a face just behind the front band, bubbles in front of it.
        add(f"liquid{ti}", g.box(f"liquid{ti}", (cx - 1.4, TANK_Y0 + 0.5, cz - 5.5), (cx + 1.4, 18.0, cz - 5.4),
                                 faces=("north",), shade=False, bone=F))
        add(f"liquid_back{ti}", g.box(f"liquid_back{ti}", (cx - 1.4, 18.0, cz - 5.5), (cx + 1.4, TANK_Y1 - 0.5, cz - 5.4),
                                      faces=("north",), bone=F))
        for i in range(4):
            bx = cx + (-1.2, -0.7, 0.7, 1.2)[i]            # clear of the stirrer shaft
            add("bubble", g.box(f"bub{ti}_{i}", (bx - 0.25, 4.0, cz - 6.25), (bx + 0.25, 4.5, cz - 6.2), faces=("north",),
                                shade=False, bone=f"bubble{ti}_{i}"))
        # Hoop bands and a nameplate on each tank.
        for hi, y in enumerate((8.0, 17.0)):
            add("hoop", g.box(f"hoop{ti}_{hi}", (cx - 6.8, y, cz - 3.2), (cx + 6.8, y + 0.8, cz + 3.2), faces=("east", "west"), bone=F))

    # Stirrer motor on the left tank, shaft and paddle (spins) visible through the window.
    add("motor", g.box("motor", (-2.5, TANK_Y1 + 2.5, 5.5), (2.5, TANK_Y1 + 5.5, 10.5), bone=F))
    add("motor_fins", g.box("motor_fins", (-2.8, TANK_Y1 + 3.0, 6.5), (2.8, TANK_Y1 + 3.6, 9.5), faces=("east", "west", "up"), bone=F))
    add("shaft", g.box("shaft", (-0.4, 6.0, 1.8), (0.4, 18.0, 2.6), faces=("north", "south", "east", "west"), bone="stirrer"))
    add("paddle", g.box("paddle", (-1.3, 6.0, 2.0), (1.3, 7.5, 2.4), faces=("north", "south", "east", "west", "up", "down"), bone="stirrer"))

    # Manifold between the tanks: a header pipe at the front, risers into each tank, valves with red hand-wheels.
    add("pipe", g.box("header", (-1.0, 9.0, -4.0), (17.0, 10.5, -2.5), faces=("north", "south", "up", "down"), bone=F))
    for i, x in enumerate((4.0, 8.0, 12.0)):
        add("pipe", g.box(f"riser{i}", (x - 0.6, 3.0, -3.85), (x + 0.6, 9.0, -2.65), faces=("north", "south", "east", "west"), bone=F))
        add("valve", g.box(f"valve{i}", (x - 0.8, 10.5, -3.9), (x + 0.8, 11.5, -2.6), faces=("north", "south", "east", "west", "up"), bone=F))
        add("wheel", g.box(f"wheel{i}", (x - 1.4, 11.5, -3.9), (x + 1.4, 11.9, -2.6), faces=("north", "south", "east", "west", "up", "down"), bone=F))
    add("pipe", g.box("crossover", (5.0, 20.0, 7.2), (11.0, 21.4, 8.8), faces=("north", "south", "up", "down"), bone=F))

    # Control station at the controller corner (front-left).
    add("station", g.box("station", (-7.5, 3, -7.5), (-2.5, 13, -5.5), bone=F))
    screen = add("screen", g.box("screen", (-6.8, 8.5, -7.7), (-3.2, 11.5, -7.5), faces=("north",), shade=False, bone=F))

    # Ladder on the back of the right tank.
    for i, x in enumerate((14.5, 17.5)):
        add("ladder", g.box(f"rail{i}", (x, 3, 21.2), (x + 0.6, 28, 21.8), faces=("north", "south", "east", "west"), bone=F))
    for k in range(7):
        add("ladder", g.box(f"rung{k}", (15.1, 6 + k * 3, 21.3), (17.5, 6.5 + k * 3, 21.7), faces=("south", "up", "down"), bone=F))

    g.layout()
    tex, glow = Tex(g.tex_w, g.tex_h), Tex(g.tex_w, g.tex_h)
    gun, steel = P.GUNMETAL, P.STEEL

    def skid(t, r, d):
        x, y, w, h = r
        if d == "north":
            t.stripes(x, y, w, h, P.HAZARD_YELLOW.base, P.HAZARD_BLACK.base, width=2)
        elif d == "up":
            t.rect(x, y, w, h, gun[3])
            for xx in range(x, x + w, 4):
                for yy in range(y + (xx // 4) % 2 * 2, y + h, 4):
                    t.px(xx, yy, gun[4])                                       # tread plate
        else:
            t.rect(x, y, w, h, gun[tone(d)])
    g.paint_all(tex, parts["skid"][0], skid)

    def tank_paint(liquid, front: bool):
        def paint(t, r, d):
            x, y, w, h = r
            t.rect(x, y, w, h, steel[4] if d in ("north", "west") else steel[3])
            for yy in range(y + 2, y + h, 6):
                t.hline(x, yy, w, steel[2])                                    # welded courses
            t.vline(x, y, h, steel[5])
            if front and d == "north":
                # Window slot (transparent) framed in brass, liquid and bubbles show through.
                cx = x + w // 2
                t.rect(cx - 2, y + 2, 4, h - 4, CLEAR)
                t.vline(cx - 3, y + 1, h - 2, P.BRASS[3])
                t.vline(cx + 2, y + 1, h - 2, P.BRASS[3])
                t.hline(cx - 3, y + 1, 6, P.BRASS[4])
                t.hline(cx - 3, y + h - 2, 6, P.BRASS[2])
                for yy in range(y + 4, y + h - 2, 4):
                    t.px(cx + 3, yy, liquid[5])                                # level marks
        return paint
    for ti, (_, _, liquid) in enumerate(TANKS):
        for b in parts[f"tank{ti}"]:
            g.paint_all(tex, b, tank_paint(liquid, False))
        for b in parts[f"tank{ti}_front"]:
            g.paint_all(tex, b, tank_paint(liquid, True))
        for b in parts[f"dome{ti}"]:
            g.paint_all(tex, b, lambda t, r, d: (t.rect(*r, steel[tone(d) + 1]), t.outline(*r, steel[2]) if d == "up" and min(r[2], r[3]) > 4 else None))

        def liquid_paint(t, r, d, liquid=liquid):
            x, y, w, h = r
            t.gradient_v(x, y, w, h, liquid[5], liquid[3])
            t.hline(x, y, w, liquid[6])                                         # surface
            for yy in range(y + 3, y + h, 5):
                t.px(x + (yy % 2), yy, liquid[6])
        g.paint_all(tex, parts[f"liquid{ti}"][0], liquid_paint)
        g.paint_all(tex, parts[f"liquid_back{ti}"][0], lambda t, r, d: t.rect(*r, P.BLACK_PAINT[2]))   # headspace
    for b in parts["bubble"]:
        g.paint_all(tex, b, lambda t, r, d: t.rect(*r, (235, 250, 225, 255)))
    for b in parts["hoop"]:
        g.paint_all(tex, b, lambda t, r, d: (t.rect(*r, gun[3]), t.hline(r[0], r[1], r[2], gun[5])))

    g.paint_all(tex, parts["motor"][0], lambda t, r, d: (t.rect(*r, P.NATO_GREEN[tone(d)]), t.outline(*r, P.NATO_GREEN[1]) if min(r[2], r[3]) > 3 else None))
    g.paint_all(tex, parts["motor_fins"][0], lambda t, r, d: [t.vline(xx, r[1], r[3], P.NATO_GREEN[4]) for xx in range(r[0], r[0] + r[2], 2)])
    g.paint_all(tex, parts["shaft"][0], lambda t, r, d: t.rect(*r, steel[5]))
    g.paint_all(tex, parts["paddle"][0], lambda t, r, d: (t.rect(*r, steel[4]), t.px(r[0], r[1], P.WARNING_RED.base)))
    for b in parts["pipe"]:
        g.paint_all(tex, b, lambda t, r, d: (t.rect(*r, steel[tone(d)]), t.hline(r[0], r[1], r[2], steel[5]) if r[3] > 1 else None))
    for b in parts["valve"]:
        g.paint_all(tex, b, lambda t, r, d: t.rect(*r, gun[tone(d) + 1]))
    for b in parts["wheel"]:
        g.paint_all(tex, b, lambda t, r, d: (t.rect(*r, P.WARNING_RED[tone(d) + 1]), t.px(r[0] + r[2] // 2, r[1], P.WARNING_RED[6]) if d == "up" else None))

    def station(t, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, P.OLIVE[tone(d)])
        if d == "north":
            t.outline(x, y, w, h, P.OLIVE[1])
            t.rect(x + 1, y + h - 4, w - 2, 2, P.BLACK_PAINT.base)            # keyboard shelf
            for xx in range(x + 1, x + w - 1, 2):
                t.px(xx, y + h - 4, P.WHITE_CERAMIC[4])
    g.paint_all(tex, parts["station"][0], station)

    def screen_paint(t, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, P.BLACK_PAINT.deep)
        for xx in range(x, x + w):
            t.px(xx, y + h // 2 + (xx % 3 == 0) - (xx % 3 == 1), P.INDICATOR_GREEN[4])
        t.px(x, y, P.INDICATOR_GREEN[5])
    g.paint_all(tex, screen, screen_paint)
    g.paint_all(glow, screen, screen_paint)
    for b in parts["ladder"]:
        g.paint_all(tex, b, lambda t, r, d: t.rect(*r, P.HAZARD_YELLOW[tone(d)]))
    return g, tex, glow


def animations() -> list[Animation]:
    working = Animation("working", 2.0, loop=True)
    working.rotate("stirrer", {0: (0, 0, 0), 2.0: (0, 720, 0)})
    rise = 12.0
    for t in range(2):
        for i in range(4):
            off = (i * 0.5 + t * 0.25) % 2.0
            keys = {}
            for k in range(5):
                tt = round(k * 0.5, 2)
                phase = ((tt + off) % 2.0) / 2.0
                keys[tt] = (0, phase * rise, 0)
            working.move(f"bubble{t}_{i}", keys)
    return [working]


if __name__ == "__main__":
    model, texture, glowmask = build()
    for p in model.save(texture, glowmask):
        print(p)
    print(save_animations(NAME, animations()))
