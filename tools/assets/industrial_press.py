"""Industrial Press (GeckoLib block model + animation), DESIGN.md §8. Two blocks tall.

Look: a heavy C-frame press. A cast bed with a die block sits on the plinth; a thick spine rises at the back to a
crown that overhangs the bed and carries the hydraulic cylinder. The ram and platen stroke down onto the die once
per 2 s cycle (timed with the press sound: impact at 1.0 s). A pump and hydraulic hoses sit on the east flank,
with a pressure gauge on the crown. Front = north.
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

NAME = "industrial_press"


def tone(d: str) -> int:
    return {"up": 4, "down": 2}.get(d, 3)


def build() -> tuple[GeoModel, Tex, Tex]:
    g = GeoModel(NAME, tex_size=(128, 128))
    g.bone("frame")
    g.bone("ram", pivot=(0, 20, -1), parent="frame")
    g.bone("gauge_needle", pivot=(6.15, 26.5, -3), parent="frame")
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
    # East flank: pump with motor, hoses up to the cylinder; a pressure gauge mounted on the crown's east face.
    add("pump", g.box("pump", (6.5, 2, -1), (8.5, 8, 4), faces=("north", "south", "east", "up"), bone=F))
    add("hose", g.box("hose_v", (6.5, 8, 1.5), (7.5, 26, 2.5), faces=("north", "south", "east", "west"), bone=F))
    add("hose", g.box("hose_h", (3, 26, 1.5), (7.5, 27, 2.5), faces=("north", "south", "up", "down"), bone=F))
    add("gauge", g.box("gauge", (5.5, 25, -4.5), (6.0, 28, -1.5), faces=("east", "north", "south", "up", "down"), bone=F))
    add("needle", g.box("needle", (6.15, 26.4, -3.1), (6.2, 27.7, -2.9), faces=("east",), shade=False, bone="gauge_needle"))

    g.layout()
    tex, glow = Tex(g.tex_w, g.tex_h), Tex(g.tex_w, g.tex_h)
    paint(g, tex, glow, parts)
    return g, tex, glow


def paint(g: GeoModel, tex: Tex, glow: Tex, parts: dict) -> None:
    """The press's surfaces, in model space (tools/gz/machine_kit.py; the machines' remaster): an olive cast frame (bed,
    spine, crown, ribs) bolted at its corners, a hazard lip and rating plate on the crown; gunmetal cylinders with a
    bright steel rod; a gunmetal platen with a hazard edge; steel dies colour-banded, their cavities dark; steel tie
    rods; a green pump and junction box with rubber hoses; a brass gauge."""
    k = K.Kit(g, parts, seed=323)
    k.plinth(tex)
    k.paint(tex, ["bed", "spine", "crown", "rib"], k.OLIVE)
    k.panels(tex, ["bed", "spine", "crown"], inset=1, min_size=6)
    k.overlay(tex, "crown", lambda f, img: M.paste(img, k.on(f, k.HAZARD), f.border["bottom"] < 2), only=("north",))
    g.paint_all(tex, parts["crown"][0], lambda t, r, d: K.plate(t, r[0] + r[2] // 2 - 3, r[1] + 1, 6, 2) if d == "north" else None)
    k.paint(tex, ["cylinder", "cyl_body", "platen"], k.GUN)
    k.overlay(tex, "platen", lambda f, img: M.paste(img, k.on(f, M.stripes(width=1, wear=0.3)), f.border["bottom"] < 1), only=("north",))
    k.paint(tex, ["rod", "pin", "punch"], k.BRIGHT)
    k.paint(tex, "tie", k.STEEL)
    k.overlay(tex, "tie", lambda f, img: M.lighten(img, (f.border["top"] % 7 < 1), 1.3, 12) if f.vertical else None)

    def die_painter(name, band):
        def paint_die(t, r, d):
            x, y, w, h = r
            if d == "up":
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
                t.hline(x, y + 1, w, band[4])                            # die colour band
        return paint_die
    dies = {"die_plate": P.HAZARD_YELLOW, "die_rod": P.WARNING_RED, "die_wire": P.COPPER, "die_canister": P.CHERENKOV}
    for name, band in dies.items():
        k.paint(tex, name, k.STEEL)
        g.paint_all(tex, parts[name][0], die_painter(name, band))
    k.paint(tex, ["jbox", "pump"], k.NATO)
    g.paint_all(tex, parts["jbox"][0], lambda t, r, d: t.px(r[0] + 1, r[1] + 1, P.HAZARD_YELLOW.base) if d == "south" else None)
    k.overlay(tex, "pump", lambda f, img: M.darken(img, (K.ring(f) >= 1) & ((np.floor(f.plane_uv()[1]) % 2) == 0), 0.45), only=("east",))
    k.paint(tex, "hose", k.RUBBER)
    k.paint(tex, "gauge", k.BRASS)
    g.paint_all(tex, parts["gauge"][0], lambda t, r, d: (t.rect(r[0] + 1, r[1] + 1, r[2] - 2, r[3] - 2, P.WHITE_CERAMIC[5]),
                                                          t.px(r[0] + r[2] - 2, r[1] + 1, P.WARNING_RED.base)) if d == "east" else None)
    g.paint_all(tex, parts["needle"][0], lambda t, r, d: t.rect(*r, P.BLACK_PAINT.deep))


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
