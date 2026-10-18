"""Arc Furnace (GeckoLib block model + animation), DESIGN.md §8. Two blocks tall.

Look: a squat octagonal steel crucible lined with refractory brick on the shared hazard-striped plinth, its roof a
ring around an open throat. Three graphite electrodes hang through the throat from clamp arms on a steel mast at
the back; while working they lower and jitter, and the white-hot melt rises in the throat (glowmask). A
transformer behind the crucible feeds them through ceramic bushings and heavy cables; a pouring spout and an
inspection port with a glowing slit face the front (north). Geometry is centred on the block's bottom centre.
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from gz import palette as P  # noqa: E402
from gz.geo import Animation, GeoModel, save_animations  # noqa: E402
from gz.palette import fixed  # noqa: E402
from gz.tex import Tex  # noqa: E402

NAME = "arc_furnace"
GRAPHITE = fixed("graphite", "#101114", "#1c1e23", "#2c2f36", "#3e424b", "#555a65", "#737986", "#a4abb8")
BRICK = fixed("refractory", "#2a1a12", "#4a2c1e", "#6c4430", "#8c5c42", "#a87a5c", "#c49c7e", "#e2c6aa")
CZ = -1.0   # crucible centre z (shifted forward to leave room for the transformer)


def tone(d: str) -> int:
    return {"up": 4, "down": 2}.get(d, 3)


def build() -> tuple[GeoModel, Tex, Tex]:
    g = GeoModel(NAME, tex_size=(128, 128))
    g.bone("frame")
    g.bone("electrodes", pivot=(0, 24, CZ), parent="frame")
    g.bone("melt", pivot=(0, 11, CZ), parent="frame")
    g.bone("port_glow", pivot=(0, 6, -7), parent="frame")
    F = "frame"
    parts: dict[str, list] = {}

    def add(group, box):
        parts.setdefault(group, []).append(box)
        return box

    add("plinth", g.box("plinth", (-8, 0, -8), (8, 2, 8), bone=F))
    # Round crucible as a stepped pixel circle: bands of (z0, z1, half-width in x) around the centre.
    bands = [(-6, -5, 3.5), (-5, -3.5, 5), (-3.5, 3.5, 6), (3.5, 5, 5), (5, 6, 3.5)]
    for i, (z0, z1, hw) in enumerate(bands):
        add("shell", g.box(f"shell{i}", (-hw, 2, CZ + z0), (hw, 11, CZ + z1), faces=("north", "south", "east", "west"), bone=F))
        add("band", g.box(f"hoop{i}", (-hw - 0.3, 4, CZ + z0 - (0.3 if z0 == -6 else 0)), (hw + 0.3, 5, CZ + z1 + (0.3 if z1 == 6 else 0)),
                          faces=("north", "south", "east", "west"), bone=F))
    # Roof: the same stepped outline in brick-lined steel, with an open throat (|x|, |z| < 2.5).
    roof_boxes = []
    for i, (z0, z1, hw) in enumerate(bands):
        if z1 <= -2.5 or z0 >= 2.5:
            roof_boxes.append((f"roof{i}", (-hw, z0), (hw, z1)))
        else:
            for zz0, zz1 in ((z0, -2.5), (2.5, z1)):
                if zz1 > zz0:
                    roof_boxes.append((f"roof{i}_{zz0}", (-hw, zz0), (hw, zz1)))
            roof_boxes.append((f"roof{i}_w", (-hw, -2.5), (-2.5, 2.5)))
            roof_boxes.append((f"roof{i}_e", (2.5, -2.5), (hw, 2.5)))
    for name, (x0, z0), (x1, z1) in roof_boxes:
        add("roof", g.box(name, (x0, 11, CZ + z0), (x1, 12.5, CZ + z1), faces=("north", "south", "east", "west", "up"), bone=F))
    add("lining", g.box("throat_floor", (-2.5, 10, CZ - 2.5), (2.5, 10.01, CZ + 2.5), faces=("up",), bone=F))
    # Melt surface: tucked below the throat floor at rest, rises into view while working.
    melt = add("melt", g.box("melt", (-2.4, 9.2, CZ - 2.4), (2.4, 9.3, CZ + 2.4), faces=("up",), shade=False, bone="melt"))
    # Pouring spout and inspection port on the front.
    add("spout", g.box("spout", (-1.5, 8, CZ - 8), (1.5, 9, CZ - 5.5), faces=("north", "east", "west", "up", "down"), bone=F))
    add("spout_lip", g.box("spout_lip", (-1.5, 9, CZ - 8), (-1, 10, CZ - 5.5), faces=("north", "east", "west", "up"), bone=F))
    add("spout_lip", g.box("spout_lip2", (1, 9, CZ - 8), (1.5, 10, CZ - 5.5), faces=("north", "east", "west", "up"), bone=F))
    add("port", g.box("port", (-2.5, 4.5, CZ - 6.6), (2.5, 7.5, CZ - 6), faces=("north", "east", "west", "up", "down"), bone=F))
    add("port_back", g.box("port_back", (-2, 5, CZ - 6.05), (2, 7, CZ - 6.04), faces=("north",), bone=F))
    glow = add("port_glow", g.box("port_glow", (-1.8, 5.6, CZ - 5.9), (1.8, 6.4, CZ - 5.85), faces=("north",), shade=False, bone="port_glow"))
    # Mast (back-west) and the transformer (back).
    add("mast", g.box("mast", (-7.5, 2, 4.5), (-5, 30, 7), bone=F))
    add("mast_cap", g.box("mast_cap", (-8, 30, 4), (-4.5, 31, 7.5), bone=F))
    add("xfmr", g.box("transformer", (-4, 2, 5.5), (5, 10, 8), bone=F))
    for i, x in enumerate((-2.5, 0.5, 3.5)):
        add("fin", g.box(f"xfin{i}", (x - 1, 3, 8), (x, 9, 8.5), faces=("south", "east", "west", "up"), bone=F))
        add("bushing", g.box(f"bushing{i}", (x - 0.75, 10, 6.25), (x + 0.75, 12, 7.75), faces=("north", "south", "east", "west", "up"), bone=F))
        add("bushing_ring", g.box(f"bring{i}", (x - 1, 10.8, 6), (x + 1, 11.2, 8), faces=("north", "south", "east", "west", "up", "down"), bone=F))

    # Electrodes: three graphite columns in a triangle, clamped to arms on a carriage that rides the mast.
    E = "electrodes"
    for i, (ex, ez) in enumerate(((0, CZ - 1.3), (-1.2, CZ + 0.8), (1.2, CZ + 0.8))):
        add("electrode", g.box(f"electrode{i}", (ex - 0.6, 12, ez - 0.6), (ex + 0.6, 27, ez + 0.6), faces=("north", "south", "east", "west", "up"), bone=E))
        add("clamp", g.box(f"clamp{i}", (ex - 0.9, 23.5, ez - 0.9), (ex + 0.9, 25, ez + 0.9), faces=("north", "south", "east", "west", "up", "down"), bone=E))
    add("arm", g.box("arm", (-5, 23.8, CZ - 0.5), (1.5, 24.8, CZ + 1.5), faces=("north", "south", "up", "down", "east"), bone=E))
    add("arm", g.box("arm_back", (-5, 23.8, CZ + 1.5), (-4, 24.8, 4.5), faces=("east", "west", "up", "down"), bone=E))
    add("carriage", g.box("carriage", (-8, 22.5, 4), (-4.5, 26, 7.5), bone=E))
    # Heavy cables from the bushings up to the carriage (flexible, so they ride with it).
    add("cable", g.box("cable_v", (-3, 12, 6.5), (-2, 23, 7.5), faces=("north", "south", "east", "west"), bone=E))
    add("cable", g.box("cable_h", (-3, 11.5, 6.5), (4, 12.5, 7.5), faces=("north", "south", "up", "down", "east"), bone=F))

    g.layout()
    tex, glowmask = Tex(g.tex_w, g.tex_h), Tex(g.tex_w, g.tex_h)
    gun, steel = P.GUNMETAL, P.STEEL

    g.paint_all(tex, parts["plinth"][0], lambda t, r, d: (
        t.stripes(*r, P.HAZARD_YELLOW.base, P.HAZARD_BLACK.base, width=2) if d == "north" else
        t.panel(*r, gun, base=tone(d), style="raised") if min(r[2], r[3]) >= 3 else t.rect(*r, gun[tone(d)])))

    def shell(t, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, steel[3] if d in ("north", "west") else steel[2])
        t.hline(x, y, w, steel[4])                            # lip under the roof
        for xx in range(x + 3, x + w - 1, 5):
            t.vline(xx, y + 1, h - 2, steel[1])               # plate seams
            t.px(xx + 1, y + 2, steel[5])
        t.hline(x, y + h - 1, w, P.RUST[2])                   # heat-scorched base
    for b in parts["shell"]:
        g.paint_all(tex, b, shell)
    for b in parts["band"]:
        g.paint_all(tex, b, lambda t, r, d: (t.rect(*r, gun[2]), t.hline(r[0], r[1], r[2], gun[4])))

    def roof(t, r, d):
        x, y, w, h = r
        if d == "up":
            t.rect(x, y, w, h, BRICK[3])
            for yy in range(y, y + h, 2):                      # refractory brick courses
                t.hline(x, yy, w, BRICK[2])
                for xx in range(x + (yy // 2) % 2 * 2, x + w, 4):
                    t.px(xx, yy + 1, BRICK[2])
            t.speckle(x, y, w, h, [BRICK[4], P.BLACK_PAINT[2]], seed=x * 3 + y, density=0.1)
        else:
            t.rect(x, y, w, h, steel[tone(d)])
            t.hline(x, y, w, steel[5])
    for b in parts["roof"]:
        g.paint_all(tex, b, roof)
    g.paint_all(tex, parts["lining"][0], lambda t, r, d: (t.rect(*r, BRICK[1]), t.speckle(*r, [P.BLACK_PAINT[1]], seed=4, density=0.3)))

    def melt_paint(t, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, P.NUCLEAR_AMBER[5])
        for yy in range(y, y + h):
            for xx in range(x, x + w):
                if (xx * 5 + yy * 3) % 7 == 0:
                    t.px(xx, yy, P.NUCLEAR_AMBER[6])
                elif (xx + yy * 2) % 9 == 0:
                    t.px(xx, yy, P.WARNING_RED[5])            # slag skin
        t.rect(x + w // 2 - 1, y + h // 2 - 1, 2, 2, (255, 252, 236, 255))   # arc spot, white-hot
    g.paint_all(tex, melt, melt_paint)
    g.paint_all(glowmask, melt, melt_paint)

    for b in parts["spout"] + parts["spout_lip"]:
        g.paint_all(tex, b, lambda t, r, d: (t.rect(*r, steel[tone(d)]), t.hline(r[0], r[1], r[2], BRICK[3]) if d == "up" else None))

    def port(t, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, gun[tone(d) + 1])
        if d == "north":
            t.outline(x, y, w, h, gun[1])
            t.rect(x + 1, y + 1, w - 2, h - 2, (0, 0, 0, 0))   # open sight slit: the glow panel shows through
            t.px(x, y, gun[5])
    g.paint_all(tex, parts["port"][0], port)
    g.paint_all(tex, parts["port_back"][0], lambda t, r, d: t.rect(*r, P.BLACK_PAINT.deep))

    def glow_paint(t, r, d):
        t.gradient_v(*r, P.NUCLEAR_AMBER[6], P.NUCLEAR_AMBER[4])
    g.paint_all(tex, glow, glow_paint)
    g.paint_all(glowmask, glow, glow_paint)

    def mast(t, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, P.HAZARD_YELLOW[3] if d in ("north", "south", "east", "west") else gun[tone(d)])
        if d in ("north", "east") and h > 8:
            for yy in range(y, y + h, 6):
                t.hline(x, yy, w, P.HAZARD_YELLOW[2])         # bolted sections
            t.vline(x, y, h, P.HAZARD_YELLOW[4])
    g.paint_all(tex, parts["mast"][0], mast)
    g.paint_all(tex, parts["mast_cap"][0], lambda t, r, d: t.rect(*r, gun[tone(d) + 1]))

    def xfmr(t, r, d):
        x, y, w, h = r
        t.panel(x, y, w, h, P.NATO_GREEN, base=tone(d), style="raised") if min(w, h) >= 3 else t.rect(x, y, w, h, P.NATO_GREEN[tone(d)])
        if d == "south" and w >= 6:
            t.placard(x + w // 2 - 2, y + 1, 5, 6, "bolt5x7", P.HAZARD_BLACK.base, P.HAZARD_YELLOW.base)
    g.paint_all(tex, parts["xfmr"][0], xfmr)
    for b in parts["fin"]:
        g.paint_all(tex, b, lambda t, r, d: t.rect(*r, P.NATO_GREEN[tone(d) + 1]))
    for b in parts["bushing"] + parts["bushing_ring"]:
        g.paint_all(tex, b, lambda t, r, d: (t.rect(*r, P.WHITE_CERAMIC[tone(d) + 1]), t.hline(r[0], r[1], r[2], P.WHITE_CERAMIC[6])))

    def electrode(t, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, GRAPHITE[4 if d in ("north", "west") else 2])
        if d in ("north", "west"):
            t.vline(x, y, h, GRAPHITE[5])                     # lit edge keeps the three columns apart
        for yy in range(y + 4, y + h - 1, 5):
            t.hline(x, yy, w, GRAPHITE[1])                    # nipple joints between electrode sections
        if d != "up" and h > 6:
            t.hline(x, y + h - 1, w, P.NUCLEAR_AMBER[3])      # tips stay hot
    for b in parts["electrode"]:
        g.paint_all(tex, b, electrode)
    for b in parts["clamp"] + parts["arm"] + parts["carriage"]:
        g.paint_all(tex, b, lambda t, r, d: (t.rect(*r, gun[tone(d)]), t.hline(r[0], r[1], r[2], gun[5]) if r[3] > 1 else None))
    for b in parts["cable"]:
        g.paint_all(tex, b, lambda t, r, d: (t.rect(*r, P.RUBBER[2]), t.vline(r[0], r[1], r[3], P.RUBBER[4]) if r[2] > 1 else None))
    return g, tex, glowmask


def animations() -> list[Animation]:
    working = Animation("working", 2.0, loop=True)
    # Electrodes drop into the bath and hunt up and down as the regulator holds the arc length.
    working.move("electrodes", {0: (0, -4, 0), 0.3: (0, -4.6, 0), 0.7: (0, -3.8, 0), 1.0: (0, -4.4, 0), 1.4: (0, -3.9, 0),
                                1.7: (0, -4.5, 0), 2.0: (0, -4, 0)})
    working.move("melt", {0: (0, 1.0, 0), 1.0: (0, 1.1, 0), 2.0: (0, 1.0, 0)})
    working.move("port_glow", {0: (0, 0, -0.35), 2.0: (0, 0, -0.35)})
    working.scale("port_glow", {0: (1, 1, 1), 0.25: (1, 0.6, 1), 0.5: (1, 1.1, 1), 0.9: (1, 0.7, 1), 1.3: (1, 1.2, 1), 1.6: (1, 0.8, 1), 2.0: (1, 1, 1)})
    return [working]


if __name__ == "__main__":
    model, texture, glowmask = build()
    for p in model.save(texture, glowmask):
        print(p)
    print(save_animations(NAME, animations()))
