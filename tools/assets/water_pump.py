"""Water Pump (GeckoLib block model + animation), DESIGN.md §6.4. Sits on a water source and pushes water into pipes.

Look: the shared hazard-striped plinth with a drab-green pump casing on it; an electric motor lies on top with a
cooling fan at its east end (spins while pumping); a flanged outlet on the east, west and south faces, reaching the
block edge at pipe height so fluid pipes meet a solid flange; a pressure gauge and a status lamp on the front.
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from gz import palette as P  # noqa: E402
from gz.geo import Animation, GeoModel, save_animations  # noqa: E402
from gz.tex import Tex  # noqa: E402

NAME = "water_pump"


def tone(d: str) -> int:
    return {"up": 4, "down": 2}.get(d, 3)


def build() -> tuple[GeoModel, Tex, Tex]:
    g = GeoModel(NAME, tex_size=(128, 128))
    g.bone("frame")
    g.bone("fan", pivot=(5.0, 11.0, 0.0), parent="frame")
    F = "frame"
    parts: dict[str, list] = {}

    def add(group, box):
        parts.setdefault(group, []).append(box)
        return box

    add("plinth", g.box("plinth", (-8, 0, -8), (8, 2, 8), bone=F))
    add("casing", g.box("casing", (-5, 2, -5), (5, 9, 5), bone=F))
    add("cap", g.box("cap", (-5.5, 9, -5.5), (5.5, 10, 5.5), bone=F))
    # Motor lying east-west on the cap, with cooling fins and the fan at its east end.
    add("motor", g.box("motor", (-4, 10, -2.5), (4, 13, 2.5), bone=F))
    add("fins", g.box("fins", (-3, 13, -2), (3, 13.6, 2), faces=("north", "south", "east", "west", "up"), bone=F))
    add("fan_hub", g.box("fan_hub", (4, 10.5, -0.5), (4.8, 11.5, 0.5), faces=("north", "south", "up", "down", "east"), bone="fan"))
    for i, (y0, y1, z0, z1) in enumerate(((11.5, 13.5, -0.4, 0.4), (8.5, 10.5, -0.4, 0.4), (10.6, 11.4, 0.5, 2.5), (10.6, 11.4, -2.5, -0.5))):
        add("blade", g.box(f"blade{i}", (4.35, y0, z0), (4.65, y1, z1), faces=("north", "south", "east", "west", "up", "down"), bone="fan"))
    # Outlets: stub + flange on east, west and south, the flange face flush with the block edge at pipe height.
    outlets = {
        "east": ((5, 6.4, -1.5), (7.4, 9.6, 1.5), (7.4, 5.5, -2.5), (7.9, 10.5, 2.5)),
        "west": ((-7.4, 6.4, -1.5), (-5, 9.6, 1.5), (-7.9, 5.5, -2.5), (-7.4, 10.5, 2.5)),
        "south": ((-1.5, 6.4, 5), (1.5, 9.6, 7.4), (-2.5, 5.5, 7.4), (2.5, 10.5, 7.9)),
    }
    for side, (s0, s1, f0, f1) in outlets.items():
        stub_faces = ("north", "south", "up", "down") if side != "south" else ("east", "west", "up", "down")
        add("stub", g.box(f"stub_{side}", s0, s1, faces=stub_faces, bone=F))
        add("flange", g.box(f"flange_{side}", f0, f1, bone=F))
    # Front: pressure gauge and lamp.
    add("gauge", g.box("gauge", (-3.5, 4, -5.6), (0.5, 8, -5), faces=("north", "east", "west", "up", "down"), bone=F))
    add("lamp", g.box("lamp", (2, 6.5, -5.3), (3.2, 7.7, -5), faces=("north", "east", "west", "up", "down"), shade=False, bone=F))

    g.layout()
    tex, glow = Tex(g.tex_w, g.tex_h), Tex(g.tex_w, g.tex_h)
    gun, steel, green = P.GUNMETAL, P.STEEL, P.NATO_GREEN

    def plinth(t, r, d):
        x, y, w, h = r
        if d == "north":
            t.stripes(x, y, w, h, P.HAZARD_YELLOW.base, P.HAZARD_BLACK.base, width=2)
        else:
            t.rect(x, y, w, h, gun[tone(d)])
            if d == "up":
                t.outline(x, y, w, h, gun[2])
    g.paint_all(tex, parts["plinth"][0], plinth)

    def casing(t, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, green[tone(d)])
        if d != "down":
            t.outline(x, y, w, h, green[1])
            t.hline(x + 1, y + 1, w - 2, green[4])
        if d in ("east", "west", "south") and h > 4:
            for px, py in ((x + 1, y + 1), (x + w - 2, y + 1), (x + 1, y + h - 2), (x + w - 2, y + h - 2)):
                t.px(px, py, steel[5])                                      # corner bolts
        if d == "north":
            t.hline(x + 1, y + h - 2, w - 2, P.HAZARD_YELLOW[3])            # warning strip
    g.paint_all(tex, parts["casing"][0], casing)
    g.paint_all(tex, parts["cap"][0], lambda t, r, d: (t.rect(*r, gun[tone(d)]), t.outline(*r, gun[2]) if d == "up" else None))

    def motor(t, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, gun[tone(d) + (1 if d == "north" else 0)])
        if d in ("north", "south"):
            for xx in range(x + 1, x + w - 1, 2):
                t.vline(xx, y, h, gun[2])                                   # ribbed housing
        if d == "west":
            t.rect(x + 1, y + 1, w - 2, h - 2, steel[4])                    # end bell
    g.paint_all(tex, parts["motor"][0], motor)
    g.paint_all(tex, parts["fins"][0], lambda t, r, d: [t.vline(xx, r[1], r[3], gun[4] if xx % 2 else gun[2]) for xx in range(r[0], r[0] + r[2])])
    g.paint_all(tex, parts["fan_hub"][0], lambda t, r, d: t.rect(*r, steel[5]))
    for b in parts["blade"]:
        g.paint_all(tex, b, lambda t, r, d: t.rect(*r, P.WHITE_CERAMIC[4] if d in ("east", "west") else P.WHITE_CERAMIC[3]))
    for b in parts["stub"]:
        g.paint_all(tex, b, lambda t, r, d: (t.rect(*r, steel[tone(d)]), t.hline(r[0], r[1], r[2], steel[5])))

    def flange(t, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, gun[3])
        if w >= 4 and h >= 4:
            t.rect(x + 1, y + 1, w - 2, h - 2, P.BLACK_PAINT.deep)           # open bore inside a bolted rim
            t.hline(x + 1, y + 1, w - 2, gun[1])
            for px, py in ((x, y), (x + w - 1, y), (x, y + h - 1), (x + w - 1, y + h - 1)):
                t.px(px, py, steel[5])
    for b in parts["flange"]:
        g.paint_all(tex, b, flange)

    def gauge(t, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, gun[2])
        if d == "north":
            t.rect(x + 1, y + 1, w - 2, h - 2, P.WHITE_CERAMIC[5])
            t.px(x + 1, y + 2, P.WARNING_RED.base)                          # needle
            t.px(x + 2, y + 2, P.WARNING_RED.base)
    g.paint_all(tex, parts["gauge"][0], gauge)

    def lamp(t, r, d):
        t.rect(*r, P.CHERENKOV[5])
    g.paint_all(tex, parts["lamp"][0], lamp)
    g.paint_all(glow, parts["lamp"][0], lamp)
    return g, tex, glow


def animations() -> list[Animation]:
    working = Animation("working", 1.0, loop=True)
    working.rotate("fan", {0: (0, 0, 0), 1.0: (1080, 0, 0)})
    return [working]


if __name__ == "__main__":
    model, texture, glowmask = build()
    for p in model.save(texture, glowmask):
        print(p)
    print(save_animations(NAME, animations()))
