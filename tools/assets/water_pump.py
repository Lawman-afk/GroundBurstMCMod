"""Water Pump (GeckoLib block model + animation), DESIGN.md §6.4. Sits on a water source and pushes water into pipes.

Look: the shared hazard-striped plinth with a drab-green pump casing on it; an electric motor lies on top with a
cooling fan at its east end (spins while pumping); a flanged outlet on each side (east and west, the only faces
water leaves by), reaching the block edge at pipe height so fluid pipes meet a solid flange; a pressure gauge and a
status lamp on the front.
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
    }
    for side, (s0, s1, f0, f1) in outlets.items():
        stub_faces = ("north", "south", "up", "down")
        add("stub", g.box(f"stub_{side}", s0, s1, faces=stub_faces, bone=F))
        add("flange", g.box(f"flange_{side}", f0, f1, bone=F))
    # Front: pressure gauge and lamp.
    add("gauge", g.box("gauge", (-3.5, 4, -5.6), (0.5, 8, -5), faces=("north", "east", "west", "up", "down"), bone=F))
    add("lamp", g.box("lamp", (2, 6.5, -5.3), (3.2, 7.7, -5), faces=("north", "east", "west", "up", "down"), shade=False, bone=F))

    g.layout()
    tex, glow = Tex(g.tex_w, g.tex_h), Tex(g.tex_w, g.tex_h)
    paint(g, tex, glow, parts)
    return g, tex, glow


def paint(g: GeoModel, tex: Tex, glow: Tex, parts: dict) -> None:
    """The pump's surfaces, in model space (tools/gz/machine_kit.py; the machines' remaster): the plinth; a NATO-green
    pump casing bolted at its corners with a hazard line on its front under a gunmetal cap; a ribbed gunmetal motor with
    a steel end bell and a white fan; steel stubs ending in bolted flanges round dark bores; a gauge and a lamp."""
    k = K.Kit(g, parts, seed=307)
    k.plinth(tex)
    k.paint(tex, "casing", k.NATO)
    k.panels(tex, "casing", inset=1, min_size=5)
    k.overlay(tex, "casing", lambda f, img: M.paste(img, k.on(f, k.HAZARD), f.border["bottom"] == 1), only=("north",))
    k.paint(tex, ["cap", "motor", "gauge"], k.GUN)
    k.ribs(tex, "motor", every=2, k=0.7, along="u", only=("north", "south"))
    k.paint(tex, "fins", k.DARK)
    k.ribs(tex, "fins", every=2, k=0.6, along="u")
    k.paint(tex, ["fan_hub", "stub"], k.STEEL)
    k.paint(tex, "blade", k.CERAMIC)
    k.paint(tex, "flange", k.GUN)

    def bore(f, img):
        if min(f.shape) >= 4:
            r = K.ring(f)
            img[r >= 1, :3] = P.BLACK_PAINT.deep[:3]
            M.lighten(img, (r == 0) & (np.minimum(f.border["top"], f.border["bottom"]) == 0)
                      & (np.minimum(f.border["left"], f.border["right"]) == 0), 1.4, 20)   # corner bolts
    for b in parts["flange"]:
        big = max(b.faces, key=lambda d: (lambda r: r[2] * r[3])(g.rect(b, d)) if b.faces[d].rect else 0)
        k.overlay(tex, b.name, bore, only=(big,))
    g.paint_all(tex, parts["motor"][0], lambda t, r, d: t.rect(r[0] + 1, r[1] + 1, r[2] - 2, r[3] - 2, P.STEEL[4]) if d == "west" else None)

    def gauge(t, r, d):
        x, y, w, h = r
        if d == "north":
            t.rect(x + 1, y + 1, w - 2, h - 2, P.WHITE_CERAMIC[5])
            t.px(x + 1, y + 2, P.WARNING_RED.base)                          # needle
            t.px(x + 2, y + 2, P.WARNING_RED.base)
    g.paint_all(tex, parts["gauge"][0], gauge)

    def lamp(t, r, d):
        t.rect(*r, P.CHERENKOV[5])
    g.paint_all(tex, parts["lamp"][0], lamp)
    g.paint_all(glow, parts["lamp"][0], lamp)


def animations() -> list[Animation]:
    working = Animation("working", 1.0, loop=True)
    working.rotate("fan", {0: (0, 0, 0), 1.0: (1080, 0, 0)})
    return [working]


if __name__ == "__main__":
    model, texture, glowmask = build()
    for p in model.save(texture, glowmask):
        print(p)
    print(save_animations(NAME, animations()))
