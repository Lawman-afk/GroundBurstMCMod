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

import numpy as np  # noqa: E402

from gz import machine_kit as K  # noqa: E402
from gz import materials as M  # noqa: E402
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
        # Hoops overlap their neighbours by 0.3 so their step faces never sit on the shell's step faces.
        # Hoops are closed boxes that abut (never overlap) and step 0.3 px outside the shell's steps, so no hoop
        # face shares a plane with a shell face or with another hoop.
        hz0 = z0 - 0.3 if z0 < 0 else z0 + 0.3
        hz1 = z1 + 0.3 if z1 > 0 else z1 - 0.3
        add("band", g.box(f"hoop{i}", (-hw - 0.3, 4, CZ + hz0), (hw + 0.3, 5, CZ + hz1), bone=F))
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
    # Brick lining for the throat between its floor and the roof, facing into the hole (otherwise you would see
    # through the crucible's hollow inside).
    add("liner", g.box("liner_n", (-2.5, 10, CZ - 2.6), (2.5, 11, CZ - 2.5), faces=("south",), bone=F))
    add("liner", g.box("liner_s", (-2.5, 10, CZ + 2.5), (2.5, 11, CZ + 2.6), faces=("north",), bone=F))
    add("liner", g.box("liner_w", (-2.6, 10, CZ - 2.5), (-2.5, 11, CZ + 2.5), faces=("east",), bone=F))
    add("liner", g.box("liner_e", (2.5, 10, CZ - 2.5), (2.6, 11, CZ + 2.5), faces=("west",), bone=F))
    # Melt surface: tucked below the throat floor at rest, rises into view while working.
    melt = add("melt", g.box("melt", (-2.4, 9.2, CZ - 2.4), (2.4, 9.3, CZ + 2.4), faces=("up",), shade=False, bone="melt"))
    # Pouring spout and inspection port on the front.
    add("spout", g.box("spout", (-1.5, 8, CZ - 8), (1.5, 9, CZ - 5.5), faces=("north", "east", "west", "up", "down"), bone=F))
    add("spout_lip", g.box("spout_lip", (-1.5, 9, CZ - 8), (-1, 10, CZ - 5.5), faces=("north", "east", "west", "up"), bone=F))
    add("spout_lip", g.box("spout_lip2", (1, 9, CZ - 8), (1.5, 10, CZ - 5.5), faces=("north", "east", "west", "up"), bone=F))
    add("port", g.box("port", (-2.5, 4.5, CZ - 6.6), (2.5, 7.5, CZ - 6), faces=("north", "east", "west", "up", "down"), bone=F))
    add("port_back", g.box("port_back", (-2, 5, CZ - 6.3), (2, 7, CZ - 6.25), faces=("north",), bone=F))
    glow = add("port_glow", g.box("port_glow", (-1.8, 5.6, CZ - 5.55), (1.8, 6.4, CZ - 5.5), faces=("north",), shade=False, bone="port_glow"))
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
    add("arm", g.box("arm", (-5, 23.8, CZ - 0.5), (1.5, 24.8, CZ + 2.0), faces=("north", "south", "up", "down", "east"), bone=E))
    add("arm", g.box("arm_back", (-5, 23.8, CZ + 2.0), (-4, 24.8, 4.5), faces=("east", "west", "up", "down"), bone=E))
    add("carriage", g.box("carriage", (-8, 22.5, 4), (-4.5, 26, 7.5), bone=E))
    # Heavy cables from the bushings up to the carriage (flexible, so they ride with it).
    add("cable", g.box("cable_v", (-3, 12, 6.5), (-2, 23, 7.5), faces=("north", "south", "east", "west"), bone=E))
    add("cable", g.box("cable_h", (-2.8, 11.65, 6.7), (4, 12.35, 7.3), faces=("north", "south", "up", "down", "east"), bone=F))

    g.layout()
    tex, glowmask = Tex(g.tex_w, g.tex_h), Tex(g.tex_w, g.tex_h)
    paint(g, tex, glowmask, parts)
    return g, tex, glowmask


def paint(g: GeoModel, tex: Tex, glow: Tex, parts: dict) -> None:
    """The furnace's surfaces, in model space (tools/gz/machine_kit.py; the machines' remaster): a steel crucible in
    welded plates, scorched at its foot, with gunmetal hoops; a roof lined with refractory brick round the throat; the
    white-hot melt and the inspection port's glow; a yellow mast and carriage, gunmetal arms and clamps; graphite
    electrodes in sections, their tips hot; a green transformer with white ceramic bushings and rubber cables."""
    k = K.Kit(g, parts, seed=321)
    k.plinth(tex)
    k.paint(tex, ["shell", "spout", "spout_lip"], k.STEEL)
    k.seams(tex, "shell", every=5, axis="u", offset=2)
    scorch = lambda f, img: M.sooted(f, img, np.clip(1 - f.border["bottom"] / 3.0, 0, 1), 0.7) if f.vertical else None   # noqa: E731
    k.overlay(tex, "shell", scorch)
    k.paint(tex, "band", k.GUN)
    k.paint(tex, "roof", k.STEEL)
    k.paint(tex, ["roof", "liner", "lining"], k.REFRACTORY, only=("up",))
    k.paint(tex, "liner", k.REFRACTORY)

    def courses(f, img):                                                       # refractory brick courses
        a, b = f.plane_uv()
        M.darken(img, (np.floor(b * 2) % 2) == 0, 0.82)
        M.darken(img, ((np.floor(a / 2) + np.floor(b * 2)) % 2 == 0) & ((np.floor(a) % 2) == 0), 0.85)
    k.overlay(tex, ["roof"], courses, only=("up",))
    k.overlay(tex, "spout", lambda f, img: M.paste(img, k.on(f, k.REFRACTORY), K.ring(f) >= 1), only=("up",))

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
    g.paint_all(tex, parts["melt"][0], melt_paint)
    g.paint_all(glow, parts["melt"][0], melt_paint)
    k.paint(tex, "port", k.GUN)

    def port(t, r, d):
        x, y, w, h = r
        if d == "north":
            t.outline(x, y, w, h, P.GUNMETAL[1])
            t.rect(x + 1, y + 1, w - 2, h - 2, (0, 0, 0, 0))   # open sight slit: the glow panel shows through
    g.paint_all(tex, parts["port"][0], port)
    g.paint_all(tex, parts["port_back"][0], lambda t, r, d: t.rect(*r, P.BLACK_PAINT.deep))

    def glow_paint(t, r, d):
        t.gradient_v(*r, P.NUCLEAR_AMBER[6], P.NUCLEAR_AMBER[4])
    g.paint_all(tex, parts["port_glow"][0], glow_paint)
    g.paint_all(glow, parts["port_glow"][0], glow_paint)
    k.paint(tex, ["mast", "carriage"], k.YELLOW)
    k.seams(tex, "mast", every=6, axis="v", offset=2)
    k.paint(tex, ["mast_cap", "clamp", "arm"], k.GUN)
    k.paint(tex, ["xfmr", "fin"], k.NATO)
    k.ribs(tex, "fin", every=2, k=0.75, along="u")
    g.paint_all(tex, parts["xfmr"][0], lambda t, r, d: t.placard(r[0] + r[2] // 2 - 2, r[1] + 1, 5, 6, "bolt5x7", P.HAZARD_BLACK.base,
                                                                P.HAZARD_YELLOW.base) if d == "south" and r[2] >= 6 else None)
    k.paint(tex, ["bushing", "bushing_ring"], k.CERAMIC)
    k.ribs(tex, "bushing", every=1, k=0.82)
    k.paint(tex, "electrode", k.GRAPHITE)

    def joints(f, img):                                                        # nipple joints between sections, hot tips
        if not f.vertical:
            return
        M.darken(img, (f.border["top"] % 5 >= 4) & (f.border["bottom"] >= 1), 0.6)
        tip = f.border["bottom"] < 1
        img[tip, :3] = P.NUCLEAR_AMBER[3][:3]
    k.overlay(tex, "electrode", joints)
    k.paint(tex, "cable", k.RUBBER)


def animations() -> list[Animation]:
    working = Animation("working", 2.0, loop=True)
    # Electrodes drop into the bath and hunt up and down as the regulator holds the arc length.
    working.move("electrodes", {0: (0, -4, 0), 0.3: (0, -4.6, 0), 0.7: (0, -3.8, 0), 1.0: (0, -4.4, 0), 1.4: (0, -3.9, 0),
                                1.7: (0, -4.5, 0), 2.0: (0, -4, 0)})
    working.move("melt", {0: (0, 1.0, 0), 1.0: (0, 1.1, 0), 2.0: (0, 1.0, 0)})
    working.move("port_glow", {0: (0, 0, -0.95), 2.0: (0, 0, -0.95)})
    working.scale("port_glow", {0: (1, 1, 1), 0.25: (1, 0.6, 1), 0.5: (1, 1.1, 1), 0.9: (1, 0.7, 1), 1.3: (1, 1.2, 1), 1.6: (1, 0.8, 1), 2.0: (1, 1, 1)})
    return [working]


if __name__ == "__main__":
    model, texture, glowmask = build()
    for p in model.save(texture, glowmask):
        print(p)
    print(save_animations(NAME, animations()))
