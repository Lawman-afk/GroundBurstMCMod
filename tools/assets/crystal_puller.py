"""Crystal Puller (GeckoLib block model + animation), DESIGN.md §8.1. Two blocks tall.

Look: a Czochralski puller as the real ones are built. On the shared hazard-striped plinth, a round, water-jacketed
stainless furnace chamber with a domed lid and a viewport onto the melt, glowing orange while the heater is on;
copper cooling-water lines down its west side. Above it the isolation valve (gunmetal, a red handwheel), then the
narrow pull chamber with a sight glass in which the crystal shows, rising as the run goes on, and on top the white
seed-lift head that turns the crystal as it draws it up (seed rotation), its motor on top. A brass argon line climbs
the east side to the top of the pull chamber, where the cover gas goes in. Geometry is centred on the block's bottom
centre; the front faces north.
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
from gb.palette import fixed  # noqa: E402
from gb.tex import Tex  # noqa: E402

NAME = "crystal_puller"
SILICON = fixed("silicon_crystal", "#151a24", "#262e3c", "#3a4456", "#556176", "#7a879e", "#a9b5ca", "#e6ecf6")
# Round parts as stepped pixel circles: bands of (z0, z1, half-width in x).
CHAMBER = [(-6, -5, 3.5), (-5, -3.5, 5), (-3.5, 3.5, 6), (3.5, 5, 5), (5, 6, 3.5)]
LID = [(-4.5, -3.2, 2.8), (-3.2, 3.2, 4.5), (3.2, 4.5, 2.8)]
PULL = [(-3, -2, 2), (-2, 2, 3), (2, 3, 2)]
DRUM = [(-2.6, -1.6, 1.6), (-1.6, 1.6, 2.6), (1.6, 2.6, 1.6)]
CH_Y0, CH_Y1, LID_Y1, NECK_Y1, VALVE_Y1, PULL_Y1, FLANGE_Y1, DRUM_Y1 = 3.0, 13.0, 15.0, 16.5, 19.0, 27.0, 27.8, 30.4
CRYSTAL_Y0, CRYSTAL_Y1 = 20.3, 26.3


def build() -> tuple[GeoModel, Tex, Tex]:
    g = GeoModel(NAME, tex_size=(128, 128))
    g.bone("frame")
    g.bone("melt_glow", pivot=(0, 9, -6), parent="frame")
    g.bone("crystal", pivot=(0, CRYSTAL_Y0, -3.2), parent="frame")
    g.bone("head", pivot=(0, FLANGE_Y1, 0), parent="frame")
    F = "frame"
    parts: dict[str, list] = {}

    def add(group, box):
        parts.setdefault(group, []).append(box)
        return box

    def round_part(group, bands, y0, y1, bone=F, faces=("north", "south", "east", "west")):
        for i, (z0, z1, hw) in enumerate(bands):
            add(group, g.box(f"{group}{i}", (-hw, y0, z0), (hw, y1, z1), faces=faces, bone=bone))

    add("plinth", g.box("plinth", (-8, 0, -8), (8, CH_Y0, 8), bone=F))
    round_part("chamber", CHAMBER, CH_Y0, CH_Y1, faces=("north", "south", "east", "west", "up"))
    round_part("lid", LID, CH_Y1, LID_Y1, faces=("north", "south", "east", "west", "up"))
    add("neck", g.box("neck", (-2.2, LID_Y1, -2.2), (2.2, NECK_Y1, 2.2), faces=("north", "south", "east", "west"), bone=F))
    # The isolation valve between the furnace and the pull chamber: its body, the bonnet and a red handwheel (east).
    add("valve", g.box("valve", (-3.2, NECK_Y1, -2.8), (3.2, VALVE_Y1, 2.8), bone=F))
    add("bonnet", g.box("bonnet", (3.2, NECK_Y1 + 0.6, -1.0), (5.0, VALVE_Y1 - 0.6, 1.0), faces=("north", "south", "east", "up", "down"), bone=F))
    add("wheel", g.box("wheel", (5.15, NECK_Y1 - 1.0, -2.5), (5.55, VALVE_Y1 + 1.0, 2.5), faces=("east", "west"), bone=F))
    round_part("pull", PULL, VALVE_Y1, PULL_Y1)
    add("flange", g.box("flange", (-3.5, PULL_Y1, -3.5), (3.5, FLANGE_Y1, 3.5), bone=F))
    # The sight glass on the pull chamber: a frame with an open slot onto a dark recess, the crystal inside it.
    add("sight", g.box("sight", (-1.6, VALVE_Y1 + 0.8, -3.5), (1.6, PULL_Y1 - 0.4, -3.0),
                       faces=("north", "east", "west", "up", "down"), bone=F))
    add("crystal", g.box("crystal_face", (-0.9, CRYSTAL_Y0, -3.3), (0.9, CRYSTAL_Y1, -3.2), faces=("north",), bone="crystal"))
    # The furnace's viewport: a frame with an open slot, dark glass behind it and the melt's glow in front of the glass.
    add("viewport", g.box("viewport", (-1.8, 7.8, -6.7), (1.8, 10.8, -6.0), faces=("north", "east", "west", "up", "down"), bone=F))
    add("glass", g.box("viewport_glass", (-1.4, 8.2, -6.15), (1.4, 10.4, -6.1), faces=("north",), bone=F))
    add("melt_glow", g.box("melt_glow", (-1.4, 8.2, -6.35), (1.4, 10.4, -6.3), faces=("north",), shade=False, bone="melt_glow"))
    # The seed-lift head: a turning drum, its motor on top.
    round_part("drum", DRUM, FLANGE_Y1, DRUM_Y1, bone="head", faces=("north", "south", "east", "west", "up"))
    add("motor", g.box("motor", (-1.1, DRUM_Y1, -1.1), (1.1, DRUM_Y1 + 1.4, 1.1), faces=("north", "south", "east", "west", "up"), bone="head"))
    # Cooling water down the west side, into the chamber's jacket at its foot and top.
    for i, z in enumerate((-2.4, 1.6)):
        add("water", g.box(f"water{i}", (-6.9, CH_Y0, z), (-6.3, CH_Y1 - 0.6, z + 0.8), faces=("north", "south", "east", "west", "up"), bone=F))
        add("water", g.box(f"water_in{i}", (-6.3, CH_Y1 - 1.4, z), (-6.0, CH_Y1 - 0.6, z + 0.8), faces=("north", "south", "up", "down"), bone=F))
    # Argon up the east side to the top of the pull chamber.
    add("argon", g.box("argon_up", (6.3, CH_Y0, 2.4), (6.9, PULL_Y1 - 1.0, 3.0), faces=("north", "south", "east", "west", "up"), bone=F))
    add("argon", g.box("argon_in", (2.0, PULL_Y1 - 1.6, 2.4), (6.3, PULL_Y1 - 1.0, 3.0), faces=("north", "south", "up", "down"), bone=F))

    g.layout()
    tex, glow = Tex(g.tex_w, g.tex_h), Tex(g.tex_w, g.tex_h)
    paint(g, tex, glow, parts)
    return g, tex, glow


def paint(g: GeoModel, tex: Tex, glow: Tex, parts: dict) -> None:
    """The puller's surfaces, in model space (tools/gb/machine_kit.py): bright stainless chambers, the furnace's in the
    courses of its water jacket with hoops at top and foot; a gunmetal valve and sight frames; a red handwheel; a white
    head with a yellow index stripe so its turning shows; copper water lines and a brass argon line; the melt's glow."""
    k = K.Kit(g, parts, seed=341)
    k.plinth(tex)
    k.paint(tex, ["chamber", "lid", "pull"], k.BRIGHT)

    def jacket(f, img):
        if not f.vertical:
            return
        M.seams(f, img, every=5, axis="v", offset=2.5, light=False)
        hoop = (f.border["top"] < 1) | (f.border["bottom"] < 1)
        M.paste(img, k.on(f, k.STEEL), hoop)
    k.overlay(tex, "chamber", jacket)
    k.overlay(tex, "pull", lambda f, img: M.paste(img, k.on(f, k.STEEL), f.border["bottom"] < 0.8) if f.vertical else None)
    k.paint(tex, ["neck", "flange"], k.STEEL)
    k.paint(tex, ["valve", "bonnet", "viewport", "sight"], k.GUN)
    k.paint(tex, "wheel", k.RED)
    k.paint(tex, "water", k.COPPER)
    k.paint(tex, "argon", k.BRASS)
    k.paint(tex, ["drum", "motor"], k.WHITE)
    k.paint(tex, "motor", k.BLACK)
    k.ribs(tex, "motor", every=1, k=0.75, only=("north", "south", "east", "west"))

    # A data plate on the chamber's front.
    def plate(t, r, d):
        x, y, w, h = r
        if d == "north" and w >= 6:
            K.plate(t, x + w // 2 - 2, y + h - 4)
    g.paint_all(tex, parts["chamber"][0], plate)

    def stripe(t, r, d):
        x, y, w, h = r
        if d in ("north", "south", "east", "west") and w >= 2:
            t.rect(x + w // 2, y, 1, h, P.HAZARD_YELLOW[4])            # the index stripe: shows the head turning
    for b in parts["drum"]:
        g.paint_all(tex, b, stripe)

    def wheel(t, r, d):
        """A handwheel on a square plate: the rim, four spokes and the hub; the plate's corners cut away."""
        x, y, w, h = r
        cx, cy = x + (w - 1) / 2, y + (h - 1) / 2
        rad = min(w, h) / 2
        for yy in range(y, y + h):
            for xx in range(x, x + w):
                dd = ((xx - cx) ** 2 + (yy - cy) ** 2) ** 0.5
                spoke = abs(xx - cx) < 0.6 or abs(yy - cy) < 0.6
                if dd > rad:
                    t.px(xx, yy, (0, 0, 0, 0))
                elif dd > rad - 1.1:
                    pass                                                # the rim keeps its red paint
                elif dd < 1.0:
                    t.px(xx, yy, P.GUNMETAL[2])                           # hub
                elif not spoke:
                    t.px(xx, yy, (0, 0, 0, 0))
    g.paint_all(tex, parts["wheel"][0], wheel)

    def slot(t, r, d):
        x, y, w, h = r
        if d == "north":
            t.outline(x, y, w, h, P.GUNMETAL[1])
            t.rect(x + 1, y + 1, w - 2, h - 2, (0, 0, 0, 0))           # open: the glass, the crystal show through
    g.paint_all(tex, parts["sight"][0], slot)
    g.paint_all(tex, parts["viewport"][0], slot)

    # The pull chamber's inside, seen through the sight: dark, the seed cable hanging down its middle.
    def recess(t, r, d):
        x, y, w, h = r
        if d == "north":
            t.gradient_v(x, y, w, h, P.BLACK_PAINT[1], P.BLACK_PAINT[2])
            t.rect(x + w // 2, y, 1, h, P.STEEL[3])
    g.paint_all(tex, parts["pull"][0], recess)

    def crystal(t, r, d):
        x, y, w, h = r
        for xx in range(x, x + w):
            k_ = (xx - x) / max(1, w - 1)
            t.vline(xx, y + 1, h - 1, SILICON[6] if k_ < 0.4 else SILICON[4])
        t.px(x + w // 2, y, SILICON[6])                                # the seed's neck, at the top
        t.vline(x, y + 2, max(1, h - 3), SILICON[6])                    # the polished crystal's light edge
    g.paint_all(tex, parts["crystal"][0], crystal)
    g.paint_all(tex, parts["glass"][0], lambda t, r, d: (t.rect(*r, P.BLACK_PAINT[1]), t.px(r[0], r[1], P.STEEL[4])))

    def melt(t, r, d):
        x, y, w, h = r
        t.gradient_v(x, y, w, h, P.NUCLEAR_AMBER[6], P.NUCLEAR_AMBER[4])
        t.hline(x, y + h - 1, w, (255, 246, 214, 255))                 # the melt's white-hot surface, low in the view
    g.paint_all(tex, parts["melt_glow"][0], melt)
    g.paint_all(glow, parts["melt_glow"][0], melt)


def animations() -> list[Animation]:
    working = Animation("working", 4.0, loop=True)
    # Seed rotation: the head turns the crystal once every 4 s as it draws it up.
    working.rotate("head", {0: (0, 0, 0), 4.0: (0, 360, 0)})
    return [working]


if __name__ == "__main__":
    model, texture, glowmask = build()
    for p in model.save(texture, glowmask):
        print(p)
    print(save_animations(NAME, animations()))
