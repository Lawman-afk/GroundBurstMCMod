"""Industrial Press (GeckoLib block model + animation), DESIGN.md §8. Two blocks tall.

Look: a heavy C-frame press. A cast bed with a die block sits on the plinth; a thick spine rises at the back to a
crown that overhangs the bed and carries the hydraulic cylinder. The ram and platen stroke down onto the die once
per 2 s cycle (timed with the press sound: impact at 1.0 s). Hazard-striped guard posts with red light-curtain
emitters flank the opening; a pump and hydraulic hoses sit on the east flank. Front = north.
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from gz import palette as P  # noqa: E402
from gz.geo import Animation, GeoModel, save_animations  # noqa: E402
from gz.tex import Tex  # noqa: E402

NAME = "industrial_press"


def tone(d: str) -> int:
    return {"up": 4, "down": 2}.get(d, 3)


def build() -> tuple[GeoModel, Tex, Tex]:
    g = GeoModel(NAME, tex_size=(128, 128))
    g.bone("frame")
    g.bone("ram", pivot=(0, 20, -1), parent="frame")
    g.bone("gauge_needle", pivot=(7.15, 22, -3), parent="frame")
    F = "frame"
    parts: dict[str, list] = {}

    def add(group, box):
        parts.setdefault(group, []).append(box)
        return box

    add("plinth", g.box("plinth", (-8, 0, -8), (8, 2, 8), bone=F))
    add("bed", g.box("bed", (-6.5, 2, -7), (6.5, 7, 4), bone=F))
    # One die block per die type; the renderer shows only the die loaded in the press (none when empty).
    DIES = {"die_plate": P.HAZARD_YELLOW, "die_rod": P.WARNING_RED, "die_wire": P.COPPER, "die_canister": P.CHERENKOV}
    g.exclusive = [set(DIES)]
    for name in DIES:
        g.bone(name, pivot=(0, 7, -1.5), parent="frame")
        add(name, g.box(name, (-3.5, 7, -4.5), (3.5, 9, 1.5), faces=("north", "south", "east", "west", "up"), bone=name))
    # Locating pins on the empty bed.
    for i, x in enumerate((-4.5, 3.5)):
        add("pin", g.box(f"pin{i}", (x, 7, -2), (x + 1, 8, -1), faces=("north", "south", "east", "west", "up"), bone=F))
    # Spine and crown (the C-frame), with gusset ribs.
    add("spine", g.box("spine", (-4.5, 2, 4), (4.5, 30, 8), bone=F))
    add("crown", g.box("crown", (-5.5, 24, -6), (5.5, 30, 4), faces=("north", "east", "west", "up", "down"), bone=F))
    for i, x in enumerate((-4.5, 3.5)):
        add("rib", g.box(f"rib{i}", (x, 7, 1), (x + 1, 24, 4), faces=("north", "east", "west"), bone=F))
    # Tie rods on the spine's back face and a junction box.
    for i, x in enumerate((-3.5, 2.5)):
        add("tie", g.box(f"tie{i}", (x, 3, 8), (x + 1, 29, 8.6), faces=("south", "east", "west"), bone=F))
    add("jbox", g.box("jbox", (-1.5, 10, 8), (1.5, 14, 8.8), faces=("south", "east", "west", "up", "down"), bone=F))
    add("cylinder", g.box("cylinder", (-3, 30, -4), (3, 32, 2), faces=("north", "south", "east", "west", "up"), bone=F))
    add("cyl_body", g.box("cyl_body", (-2.5, 21.5, -3.5), (2.5, 24, 1.5), faces=("north", "south", "east", "west", "down"), bone=F))
    # Ram: polished rod + heavy platen; at rest the platen hangs 7 px above the die.
    R = "ram"
    # The rod runs up inside the cylinder so it never shows a gap at full stroke (5.6 px).
    add("rod", g.box("rod", (-1.25, 16, -2.25), (1.25, 28, 0.25), faces=("north", "south", "east", "west"), bone=R))
    add("platen", g.box("platen", (-4, 14, -5), (4, 16, 2), bone=R))
    add("punch", g.box("punch", (-2, 13, -3), (2, 14, 0), faces=("north", "south", "east", "west", "down"), bone=R))
    # Guard posts with light-curtain emitters at the front corners.
    for i, x in enumerate((-6.5, 5)):
        add("post", g.box(f"post{i}", (x, 7, -7), (x + 1.5, 23, -5.5), faces=("north", "south", "east", "west"), bone=F))
        add("emitter", g.box(f"emitter{i}", (x + (1.5 if i == 0 else -0.2), 10, -6.8), (x + (1.7 if i == 0 else 0), 21, -5.7),
                             faces=("east",) if i == 0 else ("west",), shade=False, bone=F))
    # East flank: pump with motor, hoses up to the cylinder, pressure gauge.
    add("pump", g.box("pump", (6.5, 2, -1), (8.5, 8, 4), faces=("north", "south", "east", "up"), bone=F))
    add("hose", g.box("hose_v", (6.5, 8, 1.5), (7.5, 26, 2.5), faces=("north", "south", "east", "west"), bone=F))
    add("hose", g.box("hose_h", (3, 26, 1.5), (7.5, 27, 2.5), faces=("north", "south", "up", "down"), bone=F))
    add("gauge", g.box("gauge", (6.5, 20.5, -4.5), (7, 23.5, -1.5), faces=("east", "north", "south", "up", "down"), bone=F))
    add("needle", g.box("needle", (7.15, 21.9, -3.1), (7.2, 23.2, -2.9), faces=("east",), shade=False, bone="gauge_needle"))

    g.layout()
    tex, glow = Tex(g.tex_w, g.tex_h), Tex(g.tex_w, g.tex_h)
    gun, steel, olive = P.GUNMETAL, P.STEEL, P.OLIVE

    g.paint_all(tex, parts["plinth"][0], lambda t, r, d: (
        t.stripes(*r, P.HAZARD_YELLOW.base, P.HAZARD_BLACK.base, width=2) if d == "north" else
        t.panel(*r, gun, base=tone(d), style="raised") if min(r[2], r[3]) >= 3 else t.rect(*r, gun[tone(d)])))

    def casting(t, r, d):
        x, y, w, h = r
        t.panel(x, y, w, h, olive, base=tone(d), style="raised") if min(w, h) >= 3 else t.rect(x, y, w, h, olive[tone(d)])
        if d in ("north", "south", "east", "west") and w >= 6 and h >= 6:
            for bx, by in ((x + 1, y + 1), (x + w - 2, y + 1), (x + 1, y + h - 2), (x + w - 2, y + h - 2)):
                t.rivet(bx, by, olive)
            t.chip_edges(x, y, w, h, P.PRIMER_RED.shadow, seed=x + y + len(d), chips=2, edges="tb", max_len=2)
    for key in ("bed", "spine", "crown", "rib"):
        for b in parts[key]:
            g.paint_all(tex, b, casting)

    def crown_front(t, r, d):
        casting(t, r, d)
        x, y, w, h = r
        if d == "north":
            t.stripes(x, y + h - 2, w, 2, P.HAZARD_YELLOW.base, P.HAZARD_BLACK.base, width=2)   # hazard lip
            t.rect(x + w // 2 - 3, y + 1, 6, 2, P.BLACK_PAINT.base)       # rating plate
            t.hline(x + w // 2 - 2, y + 1, 4, P.HAZARD_YELLOW[3])
    g.paint_all(tex, parts["crown"][0], crown_front)

    def die_painter(name, band):
        def paint(t, r, d):
            x, y, w, h = r
            t.rect(x, y, w, h, steel[tone(d)])
            if d == "up":
                t.outline(x, y, w, h, steel[2])
                cx, cy = x + w // 2, y + h // 2
                cav = P.BLACK_PAINT[1]
                if name == "die_plate":
                    t.rect(x + 1, cy - 1, w - 2, 2, cav)                 # long shallow slot
                elif name == "die_rod":
                    t.rect(cx - 1, cy - 1, 2, 2, cav)                    # round bore
                elif name == "die_wire":
                    for dx in (-2, 0, 2):
                        t.px(cx + dx, cy, cav)                           # drawing holes
                else:
                    t.rect(cx - 2, cy - 2, 4, 4, cav)                    # deep cylindrical cavity
                    t.outline(cx - 2, cy - 2, 4, 4, P.BLACK_PAINT[2])
                t.px(x + 1, y + 1, band[4])                              # colour-coded corner mark
            else:
                t.hline(x, y, w, steel[5])
                t.hline(x, y + 1, w, band[4])                            # die colour band
        return paint
    for name, band in DIES.items():
        g.paint_all(tex, parts[name][0], die_painter(name, band))
    for b in parts["pin"]:
        g.paint_all(tex, b, lambda t, r, d: (t.rect(*r, steel[tone(d) + 1]), t.px(r[0], r[1], steel[6])))

    for b in parts["cylinder"] + parts["cyl_body"]:
        g.paint_all(tex, b, lambda t, r, d: (t.rect(*r, gun[tone(d) + 1]), t.hline(r[0], r[1], r[2], gun[5]),
                                               t.vline(r[0] + 1, r[1], r[3], gun[5]) if d in ("north", "west") and r[2] > 3 else None))
    g.paint_all(tex, parts["rod"][0], lambda t, r, d: (t.rect(*r, steel[5]), t.vline(r[0] + 1, r[1], r[3], steel[6]) if r[2] > 2 else None))

    def platen(t, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, gun[tone(d)])
        if d in ("north", "east", "west", "south"):
            t.hline(x, y, w, gun[5])
            if d == "north":
                t.stripes(x + 1, y + h - 1, w - 2, 1, P.HAZARD_YELLOW.base, P.HAZARD_BLACK.base, width=1)
    g.paint_all(tex, parts["platen"][0], platen)
    g.paint_all(tex, parts["punch"][0], lambda t, r, d: t.rect(*r, steel[tone(d) + 1]))

    def post(t, r, d):
        x, y, w, h = r
        t.stripes(x, y, w, h, P.HAZARD_YELLOW.base, P.HAZARD_BLACK.base, width=2, flip=d in ("east", "south"))
    for b in parts["post"]:
        g.paint_all(tex, b, post)

    def emitter(t, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, P.BLACK_PAINT[2])
        for yy in range(y, y + h, 2):
            t.px(x + w // 2, yy, P.INDICATOR_RED[4])
    for b in parts["emitter"]:
        g.paint_all(tex, b, emitter)
        g.paint_all(glow, b, lambda t, r, d: [t.px(r[0] + r[2] // 2, yy, P.INDICATOR_RED[4]) for yy in range(r[1], r[1] + r[3], 2)])

    for b in parts["tie"]:
        g.paint_all(tex, b, lambda t, r, d: (t.rect(*r, steel[tone(d)]), [t.px(r[0], yy, steel[5]) for yy in range(r[1], r[1] + r[3], 7)]))
    g.paint_all(tex, parts["jbox"][0], lambda t, r, d: (t.rect(*r, P.NATO_GREEN[tone(d)]),
                                                         t.px(r[0] + 1, r[1] + 1, P.HAZARD_YELLOW.base) if d == "south" else None))
    g.paint_all(tex, parts["pump"][0], lambda t, r, d: (t.rect(*r, P.NATO_GREEN[tone(d)]),
                                                         t.vent(r[0] + 1, r[1] + 1, r[2] - 2, r[3] - 2, P.NATO_GREEN, slat=2) if d == "east" and r[2] > 3 else None))
    for b in parts["hose"]:
        g.paint_all(tex, b, lambda t, r, d: (t.rect(*r, P.RUBBER[2]), t.vline(r[0], r[1], r[3], P.RUBBER[4]) if r[2] > 1 else None))

    def gauge(t, r, d):
        x, y, w, h = r
        if d == "east":
            t.rect(x, y, w, h, P.BRASS[3])
            t.rect(x + 1, y + 1, w - 2, h - 2, P.WHITE_CERAMIC[5])
            t.px(x + w - 2, y + 1, P.WARNING_RED.base)
        else:
            t.rect(x, y, w, h, P.BRASS[tone(d)])
    g.paint_all(tex, parts["gauge"][0], gauge)
    g.paint_all(tex, parts["needle"][0], lambda t, r, d: t.rect(*r, P.BLACK_PAINT.deep))
    return g, tex, glow


def animations() -> list[Animation]:
    working = Animation("working", 2.0, loop=True)
    # Slow approach, fast final drop, impact at 1.0 s (matches the press sound), then a smooth return.
    working.move("ram", {0: (0, 0, 0), 0.6: (0, -2.5, 0), 0.9: (0, -5.2, 0), 1.0: (0, -5.6, 0), 1.15: (0, -5.6, 0),
                         1.6: (0, -1.5, 0), 2.0: (0, 0, 0)})
    working.rotate("gauge_needle", {0: (-20, 0, 0), 1.0: (55, 0, 0), 1.15: (55, 0, 0), 2.0: (-20, 0, 0)})
    working.move("frame", {0: (0, 0, 0), 0.98: (0, 0, 0), 1.0: (0, -0.12, 0), 1.06: (0, 0.04, 0), 1.12: (0, 0, 0), 2.0: (0, 0, 0)})
    return [working]


if __name__ == "__main__":
    model, texture, glowmask = build()
    for p in model.save(texture, glowmask):
        print(p)
    print(save_animations(NAME, animations()))
