"""RTG (GeckoLib block model + animation), DESIGN.md §7. One block.

Look: a squat drum on a lead-lined base (trefoil placard on the front), ringed by eight radial cooling fins whose
outer edges glow dull orange with the heat of the fuel slug; a hot core window on top glows amber and swells gently
while the slug is in (bone "core"). No moving parts: an RTG is silent and still apart from its heat.
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

NAME = "rtg"
DRUM = [(-3.5, -2.5, 2.2), (-2.5, 2.5, 3.5), (2.5, 3.5, 2.2)]      # stepped round drum: (z0, z1, half-width x)
DRUM_Y0, DRUM_Y1 = 5.0, 13.5


def tone(d: str) -> int:
    return {"up": 4, "down": 2}.get(d, 3)


def build() -> tuple[GeoModel, Tex, Tex]:
    g = GeoModel(NAME, tex_size=(128, 128))
    g.bone("frame")
    g.bone("core", pivot=(0, 14, 0), parent="frame")
    F = "frame"
    parts: dict[str, list] = {}

    def add(group, box):
        parts.setdefault(group, []).append(box)
        return box

    add("plinth", g.box("plinth", (-8, 0, -8), (8, 2, 8), bone=F))
    add("base", g.box("base", (-6, 2, -6), (6, DRUM_Y0, 6), bone=F))
    for i, (z0, z1, hw) in enumerate(DRUM):
        add("drum", g.box(f"drum{i}", (-hw, DRUM_Y0, z0), (hw, DRUM_Y1, z1), faces=("north", "south", "east", "west"), bone=F))
    add("cap", g.box("cap", (-3, DRUM_Y1, -3), (3, DRUM_Y1 + 1, 3), bone=F))
    # Fins: four on the axes and four turned 45 degrees, from the drum out to the base edge.
    for i, (x0, z0, x1, z1) in enumerate(((-0.4, -7.2, 0.4, -3.0), (-0.4, 3.0, 0.4, 7.2), (-7.2, -0.4, -3.0, 0.4), (3.0, -0.4, 7.2, 0.4))):
        add("fin", g.box(f"fin{i}", (x0, DRUM_Y0 + 0.5, z0), (x1, DRUM_Y1 - 0.5, z1), bone=F))
    for i, (x0, z0, x1, z1) in enumerate(((-0.4, -6.6, 0.4, -3.0), (-0.4, 3.0, 0.4, 6.6), (-6.6, -0.4, -3.0, 0.4), (3.0, -0.4, 6.6, 0.4))):
        add("fin45", g.box(f"fin45_{i}", (x0, DRUM_Y0 + 1.0, z0), (x1, DRUM_Y1 - 1.0, z1),
                           rotation={"origin": (0, 8, 0), "axis": "y", "angle": 45}, bone=F))
    core = add("core", g.box("core_box", (-1.4, DRUM_Y1 + 1, -1.4), (1.4, DRUM_Y1 + 1.5, 1.4), faces=("north", "south", "east", "west", "up"),
                             shade=False, bone="core"))
    # Trefoil placard on the base front.
    plac = add("placard", g.box("placard", (-1.5, 2.0, -6.2), (1.5, 5.0, -6.0), faces=("north",), bone=F))

    g.layout()
    tex, glow = Tex(g.tex_w, g.tex_h), Tex(g.tex_w, g.tex_h)
    paint(g, tex, glow, parts)
    return g, tex, glow


def paint(g: GeoModel, tex: Tex, glow: Tex, parts: dict) -> None:
    """The RTG's surfaces, in model space (tools/gz/machine_kit.py; the machines' remaster): a lead plinth and base; a
    gunmetal drum and cap; steel fins whose outer edges glow dull orange with heat; the glowing fuel core; a radiation
    placard."""
    k = K.Kit(g, parts, seed=317)
    lead = M.Material(ramp=K.ramp_of(P.LEAD), tone=0.5, mottle=0.04, grain=0.03, dither=0.5, edge_light=0.14, edge_dark=0.1, ao=0.5,
                      grime=0.08, dust=0.03)
    k.paint(tex, ["plinth", "base"], lead)
    k.paint(tex, "plinth", k.HAZARD, only=("north",))
    k.paint(tex, ["drum", "cap"], k.GUN)
    k.seams(tex, "drum", every=6, axis="v", offset=1)
    k.paint(tex, ["fin", "fin45"], k.STEEL)

    def hot(t, r, d, glow_layer=False):
        """The fin's outer edge (the far end from the drum) glows dull orange with heat."""
        x, y, w, h = r
        if d in ("north", "south", "east", "west") and w >= 3:
            c = P.NUCLEAR_AMBER[2] if glow_layer else P.NUCLEAR_AMBER[3]
            t.vline(x, y + 1, h - 2, c)
            t.vline(x + w - 1, y + 1, h - 2, c)
    for b in parts["fin"] + parts["fin45"]:
        g.paint_all(tex, b, hot)
        g.paint_all(glow, b, lambda t, r, d: hot(t, r, d, True))

    def core_paint(t, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, P.NUCLEAR_AMBER[4])
        if d == "up" and min(w, h) >= 2:
            t.rect(x + 1, y + 1, w - 2, h - 2, P.NUCLEAR_AMBER[6])
    g.paint_all(tex, parts["core"][0], core_paint)
    g.paint_all(glow, parts["core"][0], core_paint)

    def placard(t, r, d):
        x, y, w, h = r
        # A 3x3 radiation sign: yellow plate, black hub and three black blades (top, lower left, lower right).
        t.rect(x, y, w, h, P.HAZARD_YELLOW.base)
        for dx, dy in ((1, 1), (1, 0), (0, 2), (2, 2)):
            t.px(x + dx, y + dy, P.HAZARD_BLACK.base)
    g.paint_all(tex, parts["placard"][0], placard)


def animations() -> list[Animation]:
    """The hot core window swells and settles, slowly, while a slug is in."""
    w = Animation("working", 3.0, loop=True)
    w.scale("core", {0: (1, 1, 1), 1.5: (1.12, 1.4, 1.12), 3: (1, 1, 1)}, easing="easeInOutSine")
    return [w]


if __name__ == "__main__":
    model, texture, glowmask = build()
    for p in model.save(texture, glowmask):
        print(p)
    print(save_animations(NAME, animations()))
