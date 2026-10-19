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

from gz import palette as P  # noqa: E402
from gz.geo import Animation, GeoModel, save_animations  # noqa: E402
from gz.tex import Tex  # noqa: E402

NAME = "ore_crusher"


def tone(d: str) -> int:
    return {"up": 4, "down": 2}.get(d, 3)


def build() -> tuple[GeoModel, Tex, Tex]:
    g = GeoModel(NAME, tex_size=(128, 128))
    g.bone("frame")
    g.bone("roller_n", pivot=(0, 12.5, -2.5), parent="frame")
    g.bone("roller_s", pivot=(0, 12.5, 2.5), parent="frame")
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
    # Corner posts give the housing a framed, fabricated look.
    for i, (x, z) in enumerate(((-7.5, -7.5), (6.5, -7.5), (-7.5, 6.5), (6.5, 6.5))):
        add("post", g.box(f"post{i}", (x, 2, z), (x + 1, 11, z + 1), faces=("north", "south", "east", "west"), bone=F))

    # Hopper walls, splayed outward 18 degrees around their bottom edge.
    add("hopper", g.box("hop_n", (-7, 12, -7), (7, 16, -6), rotation={"origin": (0, 12, -6.5), "x": 18}, bone=F))
    add("hopper", g.box("hop_s", (-7, 12, 6), (7, 16, 7), rotation={"origin": (0, 12, 6.5), "x": -18}, bone=F))
    add("hopper", g.box("hop_e", (6, 12, -6), (7, 16, 6), rotation={"origin": (6.5, 12, 0), "z": -18}, bone=F))
    add("hopper", g.box("hop_w", (-7, 12, -6), (-6, 16, 6), rotation={"origin": (-6.5, 12, 0), "z": 18}, bone=F))
    add("throat", g.box("throat", (-6, 11.5, -6), (6, 12, 6), faces=("up",), bone=F))

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
    add("pulley", g.box("pulley_disc2", (11.5, 4, -2.5), (12.5, 9, 2.5), faces=("north", "south", "east", "up", "down"),
                        rotation={"origin": (12, 6.5, 0), "x": 45}, bone="pulley"))

    # Back: power inlet.
    add("inlet", g.box("inlet", (-2, 4, 7), (2, 7, 8.5), faces=("south", "east", "west", "up", "down"), bone=F))

    # Rollers: octagonal cores with eight teeth each, along X.
    for rn, zc in (("roller_n", -2.5), ("roller_s", 2.5)):
        add("roller", g.box(f"{rn}_core", (-5.5, 11.2, zc - 1.3), (5.5, 13.8, zc + 1.3), bone=rn))
        add("roller", g.box(f"{rn}_core45", (-5.5, 11.2, zc - 1.3), (5.5, 13.8, zc + 1.3),
                            rotation={"origin": (0, 12.5, zc), "x": 45}, bone=rn))
        for k in range(8):
            add("tooth", g.box(f"{rn}_tooth{k}", (-5, 14, zc - 0.5), (5, 14.8, zc + 0.5), faces=("up", "north", "south"),
                               rotation={"origin": (0, 12.5, zc), "x": k * 45 + 22.5}, bone=rn))

    g.layout()
    tex, glow = Tex(g.tex_w, g.tex_h), Tex(g.tex_w, g.tex_h)
    olive, gun, steel = P.OLIVE, P.GUNMETAL, P.STEEL

    g.paint_all(tex, parts["plinth"][0], lambda t, r, d: (
        t.stripes(*r, P.HAZARD_YELLOW.base, P.HAZARD_BLACK.base, width=2) if d in ("north",) else
        t.panel(*r, gun, base=tone(d), style="raised") if min(r[2], r[3]) >= 3 else t.rect(*r, gun[tone(d)])))

    def housing(t, r, d):
        x, y, w, h = r
        t.panel(x, y, w, h, olive, base=tone(d), style="raised")
        if d in ("north", "south", "east", "west"):
            # Restraint: one access-panel seam, corner bolts, a couple of chips at the corners.
            t.outline(x + 2, y + 2, w - 4, h - 4, olive.shadow)
            t.hline(x + 3, y + 2, w - 6, olive.light)
            for bx, by in ((x + 1, y + 1), (x + w - 2, y + 1), (x + 1, y + h - 2), (x + w - 2, y + h - 2)):
                t.rivet(bx, by, olive)
            t.chip_edges(x, y, w, h, P.PRIMER_RED.shadow, seed=sum(map(ord, d)), chips=2, edges="tb", max_len=2)
        if d == "north":
            t.rect(x + 3, y + 3, 7, 3, P.BLACK_PAINT.base)       # nameplate with a yellow ID stripe
            t.hline(x + 4, y + 4, 5, P.HAZARD_YELLOW.base)
        if d == "west":
            t.decal("chevron5x3", x + w // 2 - 2, y + h // 2 - 1, P.HAZARD_YELLOW.base)
    g.paint_all(tex, parts["housing"][0], housing)

    for b in parts["rim"] + parts["post"]:
        g.paint_all(tex, b, lambda t, r, d: t.rect(*r, gun[tone(d) + 1 if d != "down" else 2]))
    for b in parts["hopper"]:
        def hop(t, r, d):
            x, y, w, h = r
            if d in ("north", "south", "east", "west") and h >= 4 and w >= 4:
                t.rect(x, y, w, h, steel[3])
                t.hline(x, y, w, steel.highlight)          # rolled top lip
                t.hline(x, y + h - 1, w, steel.shadow)
                t.rivet(x + 1, y + h - 2, steel)
                t.rivet(x + w - 2, y + h - 2, steel)
            else:
                t.rect(x, y, w, h, steel[tone(d)])
        g.paint_all(tex, b, hop)
    g.paint_all(tex, parts["throat"][0], lambda t, r, d: t.rect(*r, P.BLACK_PAINT.deep))
    for b in parts["chute"] + parts["tray"]:
        def chute(t, r, d, b=b):
            x, y, w, h = r
            t.rect(x, y, w, h, gun[tone(d)])
            if b.name == "chute" and d == "north":
                t.rect(x + 1, y + h - 3, w - 2, 2, P.BLACK_PAINT.deep)   # discharge mouth
                t.hline(x + 1, y + h - 1, w - 2, P.DESERT_TAN.shadow)    # spilled dust
        g.paint_all(tex, b, chute)

    def lamp_paint(t, r, d):
        t.rect(*r, P.INDICATOR_GREEN.base)
        t.px(r[0], r[1], P.INDICATOR_GREEN.shine)
    g.paint_all(tex, lamp, lamp_paint)
    g.paint_all(glow, lamp, lamp_paint)

    def motor(t, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, gun[tone(d)])
        if d == "east":
            t.ring(x + w / 2, y + h / 2, 1.0, 2.4, gun.light)
        if d in ("north", "south") and h >= 4:
            t.hline(x, y + 1, w, gun.highlight)
    g.paint_all(tex, parts["motor"][0], motor)
    for b in parts["fin"]:
        g.paint_all(tex, b, lambda t, r, d: t.rect(*r, gun[tone(d) + 1]))
    for b in parts["pulley"]:
        def pul(t, r, d, b=b):
            t.rect(*r, steel[tone(d)])
            if d == "east" and "disc" in b.name:
                x, y, w, h = r
                t.px(x + w // 2, y + 1, P.WARNING_RED.base)        # index mark shows the spin
        g.paint_all(tex, b, pul)
    g.paint_all(tex, parts["inlet"][0], lambda t, r, d: (t.rect(*r, P.BLACK_PAINT.base), t.rect(r[0] + 1, r[1] + 1, max(1, r[2] - 2), max(1, r[3] - 2), P.COPPER.light) if d == "south" else None))

    for b in parts["roller"]:
        g.paint_all(tex, b, lambda t, r, d: t.rect(*r, P.TUNGSTEN[tone(d) - 1]))
    for b in parts["tooth"]:
        g.paint_all(tex, b, lambda t, r, d: (t.rect(*r, P.TUNGSTEN[tone(d) + 1]), t.hline(r[0], r[1], r[2], P.TUNGSTEN.shine)))

    return g, tex, glow


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
