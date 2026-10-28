"""Battery Charger (GeckoLib block model + animation), DESIGN.md §8. Just over half a block tall.

Look: a low gunmetal bench on the shared hazard-striped plinth. While one is loaded, a battery (bone "battery")
lies in a padded cradle on top between two spring clamps, its copper terminal against a contact block fed by a cable from the back. A row of five charge LEDs on
the front edge chases while it charges (bones "led0".."led4", hidden when idle); a vent grille runs along the sides.
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

NAME = "battery_charger"
TOP = 6.5


def tone(d: str) -> int:
    return {"up": 4, "down": 2}.get(d, 3)


def build() -> tuple[GeoModel, Tex, Tex]:
    g = GeoModel(NAME, tex_size=(64, 64))
    g.bone("frame")
    g.bone("battery", pivot=(0, TOP + 2, 0), parent="frame")
    for i in range(5):
        g.bone(f"led{i}", pivot=(-3.2 + i * 1.6, 4.6, -6.6), parent="frame")
    F = "frame"
    parts: dict[str, list] = {}

    def add(group, box):
        parts.setdefault(group, []).append(box)
        return box

    add("plinth", g.box("plinth", (-8, 0, -8), (8, 2, 8), bone=F))
    add("bench", g.box("bench", (-7, 2, -6.4), (7, TOP, 6.4), bone=F))
    add("top", g.box("top", (-7.4, TOP, -6.8), (7.4, TOP + 0.6, 6.8), bone=F))
    add("pad", g.box("pad", (-4.6, TOP + 0.6, -2.0), (3.4, TOP + 1.0, 2.0), bone=F))
    for i, x in enumerate((-3.6, 1.6)):
        add("clamp", g.box(f"clamp{i}", (x, TOP + 0.8, -2.1), (x + 0.8, TOP + 2.6, 2.1), bone=F))
    # The battery lying in the cradle, terminal to the east.
    # The battery (bone "battery") only shows while one is loaded; the renderer hides it otherwise.
    add("cell", g.box("cell", (-3.8, TOP + 1.0, -1.4), (3.0, TOP + 3.2, 1.4), bone="battery"))
    add("cap", g.box("cap", (3.0, TOP + 1.5, -0.7), (3.6, TOP + 2.7, 0.7), faces=("north", "south", "east", "up", "down"), bone="battery"))
    add("contact", g.box("contact", (3.6, TOP + 0.6, -1.6), (5.4, TOP + 3.0, 1.6), bone=F))
    add("cable", g.box("cable", (4.2, TOP + 0.6, 1.6), (4.8, TOP + 1.2, 6.8), faces=("east", "west", "up"), bone=F))
    # LED row on the front edge: dark sockets in the bench face, lit lenses in front of them (bones).
    add("sockets", g.box("sockets", (-3.8, 4.0, -6.6), (4.2, 5.2, -6.4), faces=("north", "east", "west", "up", "down"), bone=F))
    leds = []
    for i in range(5):
        x = -3.2 + i * 1.6
        leds.append(add("led", g.box(f"led_box{i}", (x - 0.4, 4.2, -6.8), (x + 0.4, 5.0, -6.7), faces=("north",), shade=False, bone=f"led{i}")))

    g.layout()
    tex, glow = Tex(g.tex_w, g.tex_h), Tex(g.tex_w, g.tex_h)
    paint(g, tex, glow, parts)
    return g, tex, glow


def paint(g: GeoModel, tex: Tex, glow: Tex, parts: dict) -> None:
    """The charger's surfaces, in model space (tools/gb/machine_kit.py; the machines' remaster): a gunmetal bench with
    louvred sides on the plinth, a steel top with a rubber pad and yellow clamps; the cell in the battery item's colours
    (olive, steel ends, a yellow band) with copper caps; black contacts and sockets; rubber cables; green LEDs."""
    k = K.Kit(g, parts, seed=319)
    k.plinth(tex)
    k.paint(tex, "bench", k.GUN)

    def louvres(f, img):
        a, b = f.plane_uv()
        inside = (f.border["left"] >= 2) & (f.border["right"] >= 2) & (f.border["top"] >= 1) & (f.border["bottom"] >= 1)
        M.darken(img, inside & ((np.floor(b) % 2) == 0), 0.45)
    k.overlay(tex, "bench", louvres, only=("east", "west", "south"))
    k.paint(tex, "top", k.STEEL)
    k.paint(tex, ["pad", "cable"], k.RUBBER)
    k.paint(tex, "clamp", k.YELLOW)
    k.paint(tex, "cell", k.OLIVE)
    k.paint(tex, "cell", k.STEEL, only=("east", "west"))

    def cell(t, r, d):
        """Matches the battery item: olive body, steel ends, a yellow rating band."""
        x, y, w, h = r
        if d in ("east", "west"):
            return
        t.vline(x, y, h, P.STEEL[4])
        t.vline(x + w - 1, y, h, P.STEEL[4])
        if w >= 6:
            t.vline(x + w // 2, y, h, P.HAZARD_YELLOW[4])                 # rating band round the cell
    g.paint_all(tex, parts["cell"][0], cell)
    k.paint(tex, "cap", k.COPPER)
    k.paint(tex, ["contact", "sockets"], k.BLACK)
    g.paint_all(tex, parts["contact"][0], lambda t, r, d: t.px(r[0], r[1], P.WARNING_RED.base) if d == "up" else None)
    g.paint_all(tex, parts["sockets"][0], lambda t, r, d: t.rect(*r, P.BLACK_PAINT[1]) if d == "north" else None)
    for b in parts["led"]:
        g.paint_all(tex, b, lambda t, r, d: t.rect(*r, P.INDICATOR_GREEN.light))
        g.paint_all(glow, b, lambda t, r, d: t.rect(*r, P.INDICATOR_GREEN.light))


def animations() -> list[Animation]:
    """1 s chase: the LEDs light one after another from the left, hold full, then go out together."""
    w = Animation("working", 1.0, loop=True)
    for i in range(5):
        on = round(0.1 + i * 0.12, 2)
        w.scale(f"led{i}", {0: (0, 0, 0), on - 0.01: (0, 0, 0), on: (1, 1, 1), 0.89: (1, 1, 1), 0.9: (0, 0, 0), 1.0: (0, 0, 0)})
    return [w]


if __name__ == "__main__":
    model, texture, glowmask = build()
    for p in model.save(texture, glowmask):
        print(p)
    print(save_animations(NAME, animations()))
