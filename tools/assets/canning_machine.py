"""Canning Machine (GeckoLib block model + animation), DESIGN.md §8. One block.

Look: on the shared hazard-striped plinth, a glass supply reservoir stands along the west side (steel corner posts,
olive lid with a brass filler cap); the renderer fills it with the input tank's real fluid. Beside it, at the back
east, an olive filling column carries an overhead arm reaching forward over a steel cradle, and a brass filling
nozzle (bone "nozzle") hangs from the arm. While the machine runs the nozzle dips onto the neck of the canister
standing in the cradle (bone "canister", shown only while a canister is loaded). A pipe runs from the reservoir lid
into the column; a lamp and a pressure dial sit on the column's front.
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from gz import palette as P  # noqa: E402
from gz.geo import Animation, GeoModel, save_animations  # noqa: E402
from gz.tex import Tex  # noqa: E402

NAME = "canning_machine"
CLEAR = (0, 0, 0, 0)
# Reservoir interior: x -7..-1, z -2.5..6.5, y 2..13 (the renderer's fluid box sits just inside the glass).
X0, X1, Z0, Z1, Y0, Y1 = -7.0, -1.0, -2.5, 6.5, 2.0, 13.0
# Canister and nozzle centre line.
CX, CZ = 4.0, -2.0
NOZZLE_DIP = 0.6


def tone(d: str) -> int:
    return {"up": 4, "down": 2}.get(d, 3)


def build() -> tuple[GeoModel, Tex, Tex]:
    g = GeoModel(NAME, tex_size=(128, 128))
    g.bone("frame")
    g.bone("nozzle", pivot=(CX, 12.5, CZ), parent="frame")
    g.bone("canister", pivot=(CX, 3, CZ), parent="frame")
    F = "frame"
    parts: dict[str, list] = {}

    def add(group, box):
        parts.setdefault(group, []).append(box)
        return box

    sides = ("north", "south", "east", "west")
    add("plinth", g.box("plinth", (-8, 0, -8), (8, 2, 8), bone=F))

    # Reservoir: glass panes between steel corner posts, lid on top. No fluid geometry (the renderer draws it).
    add("glass", g.box("glass_n", (X0 + 1, Y0, Z0), (X1 - 1, Y1, Z0 + 0.2), faces=("north",), bone=F))
    add("glass", g.box("glass_s", (X0 + 1, Y0, Z1 - 0.2), (X1 - 1, Y1, Z1), faces=("south",), bone=F))
    add("glass", g.box("glass_w", (X0, Y0, Z0 + 1), (X0 + 0.2, Y1, Z1 - 1), faces=("west",), bone=F))
    add("glass", g.box("glass_e", (X1 - 0.2, Y0, Z0 + 1), (X1, Y1, Z1 - 1), faces=("east",), bone=F))
    for i, (x, z) in enumerate(((X0, Z0), (X1 - 1, Z0), (X0, Z1 - 1), (X1 - 1, Z1 - 1))):
        add("post", g.box(f"post{i}", (x, Y0, z), (x + 1, Y1, z + 1), faces=sides, bone=F))
    add("lid", g.box("lid", (X0 - 0.3, Y1, Z0 - 0.3), (X1 + 0.3, Y1 + 1.2, Z1 + 0.3), bone=F))
    add("filler", g.box("filler", (-5.6, Y1 + 1.2, -0.6), (-4.0, Y1 + 2.0, 1.0), faces=(*sides, "up"), bone=F))

    # Filling column at the back east, the arm reaching forward over the cradle.
    add("column", g.box("column", (1, 2, 3.5), (7, 15, 7.5), faces=(*sides, "up"), bone=F))
    add("arm", g.box("arm", (CX - 1.4, 12.5, CZ - 1.5), (CX + 1.4, 14.5, 3.5), faces=("north", "east", "west", "up", "down"), bone=F))
    add("cradle", g.box("cradle", (1.5, 2, -4.5), (6.5, 3, 0.5), faces=(*sides, "up"), bone=F))
    # Nozzle: a brass body under the arm and a narrow tip.
    add("nozzle", g.box("nozzle_body", (CX - 0.7, 11.2, CZ - 0.7), (CX + 0.7, 12.5, CZ + 0.7), faces=(*sides, "down"), bone="nozzle"))
    add("nozzle", g.box("nozzle_tip", (CX - 0.35, 10.4, CZ - 0.35), (CX + 0.35, 11.2, CZ + 0.35), faces=(*sides, "down"), bone="nozzle"))

    # Canister: a rounded steel cylinder (two crossed boxes), shoulder and neck, under the nozzle.
    add("can_body", g.box("can_x", (CX - 1.5, 3, CZ - 0.75), (CX + 1.5, 8.3, CZ + 0.75), faces=(*sides, "up"), bone="canister"))
    add("can_body", g.box("can_z", (CX - 0.75, 3, CZ - 1.5), (CX + 0.75, 8.5, CZ + 1.5), faces=(*sides, "up"), bone="canister"))
    add("can_top", g.box("can_shoulder", (CX - 1.0, 8.5, CZ - 1.0), (CX + 1.0, 9.2, CZ + 1.0), faces=(*sides, "up"), bone="canister"))
    add("can_top", g.box("can_neck", (CX - 0.4, 9.2, CZ - 0.4), (CX + 0.4, 9.8, CZ + 0.4), faces=(*sides, "up"), bone="canister"))

    # Supply pipe: up out of the lid, across and into the column's west face. Over the lid it has no underside
    # (it rests on the lid); across the gap it does.
    add("pipe", g.box("pipe_lid", (-2.6, Y1 + 1.2, 4.8), (X1 + 0.3, Y1 + 2.0, 5.6), faces=("north", "south", "west", "up"), bone=F))
    add("pipe", g.box("pipe_gap", (X1 + 0.3, Y1 + 1.2, 4.8), (1, Y1 + 2.0, 5.6), faces=("north", "south", "up", "down"), bone=F))

    # Column front: a lamp and a pressure dial.
    lamp = add("lamp", g.box("lamp", (5.6, 6.5, 3.3), (6.6, 7.5, 3.5), faces=("north",), shade=False, bone=F))
    add("dial", g.box("dial", (5.3, 9.0, 3.3), (6.7, 10.4, 3.5), faces=("north", "east", "west", "up", "down"), bone=F))

    g.layout()
    tex, glow = Tex(g.tex_w, g.tex_h), Tex(g.tex_w, g.tex_h)
    gun, steel, olive = P.GUNMETAL, P.STEEL, P.OLIVE

    g.paint_all(tex, parts["plinth"][0], lambda t, r, d: (
        t.stripes(*r, P.HAZARD_YELLOW.base, P.HAZARD_BLACK.base, width=2) if d == "north" else
        t.panel(*r, gun, base=tone(d), style="raised") if min(r[2], r[3]) >= 3 else t.rect(*r, gun[tone(d)])))

    def glass(t, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, CLEAR)
        t.hline(x, y, w, (200, 230, 240, 255))                               # top edge catch-light
        for k in range(min(w, h) // 3):                                      # diagonal glints
            t.px(x + 1 + k, y + 2 + k, (210, 236, 246, 255))
        t.px(x + w - 2, y + h - 3, (190, 220, 232, 255))
    for b in parts["glass"]:
        g.paint_all(tex, b, glass)
    for b in parts["post"]:
        g.paint_all(tex, b, lambda t, r, d: (t.rect(*r, steel[tone(d)]), t.vline(r[0], r[1], r[3], steel[5]) if d in ("north", "west") else None))

    def lid(t, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, olive[tone(d)])
        if d == "up":
            t.outline(x, y, w, h, olive.shadow)
            t.rivet(x + 1, y + 1, olive)
            t.rivet(x + w - 2, y + h - 2, olive)
        else:
            t.hline(x, y, w, olive.light)
    g.paint_all(tex, parts["lid"][0], lid)
    g.paint_all(tex, parts["filler"][0], lambda t, r, d: (t.rect(*r, P.BRASS[tone(d) + 1]),
                                                          t.px(r[0] + r[2] // 2, r[1] + r[3] // 2, P.BRASS[6]) if d == "up" else None))

    def column(t, r, d):
        x, y, w, h = r
        if d == "up" or min(w, h) < 3:
            t.rect(x, y, w, h, olive[tone(d)])
            return
        t.panel(x, y, w, h, olive, base=tone(d), style="raised")
        if d == "north":
            t.rect(x + 1, y + h - 4, w - 2, 2, olive[1])                           # kick plate
        if d in ("east", "west"):
            t.rivet(x + 1, y + 1, olive)
            t.rivet(x + w - 2, y + 1, olive)
    g.paint_all(tex, parts["column"][0], column)

    def arm(t, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, gun[tone(d) + 1])
        if d in ("east", "west"):
            t.hline(x, y, w, gun[5])
            t.hline(x, y + h - 1, w, gun[2])
    g.paint_all(tex, parts["arm"][0], arm)

    def cradle(t, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, steel[tone(d)])
        if d == "up":
            t.outline(x, y, w, h, steel[2])
            for k in range(1, h - 1, 2):                                         # grate slots
                t.hline(x + 1, y + k, w - 2, steel[2])
    g.paint_all(tex, parts["cradle"][0], cradle)
    for b in parts["nozzle"]:
        g.paint_all(tex, b, lambda t, r, d: (t.rect(*r, P.BRASS[tone(d) + 1]), t.vline(r[0], r[1], r[3], P.BRASS[6]) if d in ("north", "west") else None))

    def can_body(t, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, steel[tone(d) + (1 if d in ("north", "west") else 0)])
        if d in sides and h >= 4:
            t.rect(x, y + 1, w, 1, P.HAZARD_YELLOW.base)                        # stencil band
            t.rect(x, y + h - 2, w, 1, P.OLIVE[3])                              # base ring
            if w >= 3:
                t.vline(x + 1, y + 2, h - 4, steel[5])                          # cylinder highlight
    for b in parts["can_body"]:
        g.paint_all(tex, b, can_body)
    for b in parts["can_top"]:
        g.paint_all(tex, b, lambda t, r, d: t.rect(*r, steel[tone(d) + 1]))
    for b in parts["pipe"]:
        g.paint_all(tex, b, lambda t, r, d: (t.rect(*r, steel[tone(d)]), t.hline(r[0], r[1], r[2], steel[5]) if d == "up" else None))

    def dial(t, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, gun[tone(d)])
        if d == "north":
            t.rect(x, y, w, h, P.WHITE_CERAMIC[5])
            t.outline(x, y, w, h, gun[1])
            t.px(x + w // 2, y + h // 2, P.WARNING_RED.base)                    # needle hub
            t.px(x + w // 2 + 1, y + h // 2 - 1, P.WARNING_RED.base)
    g.paint_all(tex, parts["dial"][0], dial)

    def lamp_paint(t, r, d):
        t.rect(*r, P.INDICATOR_GREEN.light)
        t.px(r[0], r[1], P.INDICATOR_GREEN.shine)
    g.paint_all(tex, lamp, lamp_paint)
    g.paint_all(glow, lamp, lamp_paint)
    return g, tex, glow


def animations() -> list[Animation]:
    # Each canister: the nozzle lowers onto the neck, holds while it fills, and lifts clear again.
    working = Animation("working", 2.0, loop=True)
    working.move("nozzle", {0.0: (0, 0, 0), 0.3: (0, -NOZZLE_DIP, 0), 1.6: (0, -NOZZLE_DIP, 0), 1.9: (0, 0, 0), 2.0: (0, 0, 0)})
    return [working]


if __name__ == "__main__":
    model, texture, glowmask = build()
    for p in model.save(texture, glowmask):
        print(p)
    print(save_animations(NAME, animations()))
