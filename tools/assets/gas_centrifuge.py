"""Gas Centrifuge (GeckoLib block model + animation), DESIGN.md §8. Two blocks tall.

Look: a tall slim rotor casing (brushed aluminium with a yellow-and-black band and a stencilled number) on a drive
housing over the shared hazard-striped plinth. A vertical sight slit in the front shows the dark rotor inside; bright
balance marks on the rotor (bone "rotor") sweep across the slit while it spins. On top, a cap takes the feed pipe
and two take-off pipes that run out to east and west flanges at the block edges: standing centrifuges side by side
joins their header pipes into one cascade line. A status lamp on the drive housing faces the front.
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

NAME = "gas_centrifuge"
ALU = fixed("aluminium", "#2e343a", "#4a525a", "#6c757e", "#8f989f", "#b0b8be", "#cdd3d7", "#e8ecee")
ROTOR = fixed("rotor_dark", "#07090b", "#0e1114", "#161a1f", "#20262c", "#2c333a", "#3c454e", "#56616c")
CAS_Y0, CAS_Y1 = 8.0, 28.0          # casing
SLIT_Y0, SLIT_Y1 = 11.0, 25.0       # sight slit
SLIT = 1.0                          # half-width of the slit (x)
# Stepped round casing: (z0, z1, half-width in x). The front row is split round the slit.
BANDS = [(-4, -3, 2), (-3, -2, 3), (-2, 2, 4), (2, 3, 3), (3, 4, 2)]
MARK_PITCH = 0.8                    # spacing of the rotor's balance marks; one animation cycle moves one pitch
HEADER_Y = 29.6                     # header pipes to the neighbours


def tone(d: str) -> int:
    return {"up": 4, "down": 2}.get(d, 3)


def build() -> tuple[GeoModel, Tex, Tex]:
    g = GeoModel(NAME, tex_size=(128, 128))
    g.bone("frame")
    g.bone("rotor", pivot=(0, 18, -3.3), parent="frame")
    F = "frame"
    parts: dict[str, list] = {}

    def add(group, box):
        parts.setdefault(group, []).append(box)
        return box

    add("plinth", g.box("plinth", (-8, 0, -8), (8, 3, 8), bone=F))
    # Drive housing (motor and bearing) under the casing.
    add("drive", g.box("drive", (-5.5, 3, -5.5), (5.5, 7, 5.5), bone=F))
    add("collar", g.box("collar", (-4.5, 7, -4.5), (4.5, CAS_Y0, 4.5), bone=F))
    # Casing rows. The front row (z -4..-3) is cut round the slit: two jambs plus pieces below and above it.
    for i, (z0, z1, hw) in enumerate(BANDS):
        if i == 0:
            add("casing", g.box("cas_front_lo", (-hw, CAS_Y0, z0), (hw, SLIT_Y0, z1), faces=("north", "east", "west", "up"), bone=F))
            add("casing", g.box("cas_front_hi", (-hw, SLIT_Y1, z0), (hw, CAS_Y1, z1), faces=("north", "east", "west", "down"), bone=F))
            add("casing", g.box("cas_front_l", (-hw, SLIT_Y0, z0), (-SLIT, SLIT_Y1, z1), faces=("north", "east", "west"), bone=F))
            add("casing", g.box("cas_front_r", (SLIT, SLIT_Y0, z0), (hw, SLIT_Y1, z1), faces=("north", "east", "west"), bone=F))
        else:
            add("casing", g.box(f"cas{i}", (-hw, CAS_Y0, z0), (hw, CAS_Y1, z1), faces=("north", "south", "east", "west"), bone=F))
    add("cap", g.box("cap", (-4.5, CAS_Y1, -4.5), (4.5, CAS_Y1 + 1.0, 4.5), bone=F))
    add("cap_top", g.box("cap_top", (-3, CAS_Y1 + 1.0, -3), (3, CAS_Y1 + 2.0, 3), bone=F))
    # Feed pipe rising from the cap with a valve; take-off pipes to the east and west header flanges.
    add("pipe", g.box("feed", (-0.6, CAS_Y1 + 2.0, -0.6), (0.6, 31.4, 0.6), faces=("north", "south", "east", "west"), bone=F))
    add("valve", g.box("valve", (-1.1, 31.4, -1.1), (1.1, 32.0, 1.1), bone=F))
    for i, s in enumerate((-1, 1)):
        x0, x1 = sorted((s * 3.0, s * 7.4))
        add("pipe", g.box(f"header{i}", (x0, HEADER_Y - 0.6, -0.6), (x1, HEADER_Y + 0.6, 0.6), faces=("north", "south", "up", "down"), bone=F))
        fx0, fx1 = sorted((s * 7.4, s * 8.0))
        add("flange", g.box(f"flange{i}", (fx0, HEADER_Y - 1.2, -1.2), (fx1, HEADER_Y + 1.2, 1.2), bone=F))
        # Short drop from the header into the cap.
        add("pipe", g.box(f"drop{i}", (s * 3.0 - 0.6 if s > 0 else -3.6, CAS_Y1 + 1.0, -0.6),
                          (s * 3.0 + 0.6 if s < 0 else 3.6, HEADER_Y - 0.6, 0.6), faces=("north", "south", "east", "west"), bone=F))
    # Rotor balance marks behind the slit (moving bone). Only their north faces are ever seen.
    for i in range(5):
        x = -1.6 + i * MARK_PITCH
        add("mark", g.box(f"mark{i}", (x - 0.15, SLIT_Y0 + 0.5, -3.4), (x + 0.15, SLIT_Y1 - 0.5, -3.2), faces=("north",), bone="rotor"))
    # Status lamp and a nameplate on the drive housing.
    lamp = add("lamp", g.box("lamp", (-3.6, 5.0, -5.7), (-2.6, 6.0, -5.5), faces=("north",), shade=False, bone=F))

    g.layout()
    tex, glow = Tex(g.tex_w, g.tex_h), Tex(g.tex_w, g.tex_h)
    paint(g, tex, glow, parts)
    return g, tex, glow


def paint(g: GeoModel, tex: Tex, glow: Tex, parts: dict) -> None:
    """The centrifuge's surfaces, in model space (tools/gz/machine_kit.py; the machines' remaster): a NATO-green drive
    with louvred sides and a nameplate on the plinth; a brushed aluminium casing in sections with steel end rings and a
    hazard band lined up across the cascade, its slit dark onto the rotor; gunmetal caps and collar; steel pipes with
    brass flanges and a red valve; the rotor's balance marks."""
    k = K.Kit(g, parts, seed=327)
    k.plinth(tex)
    k.paint(tex, "drive", k.NATO)

    def louvres(f, img):
        a, b = f.plane_uv()
        inside = (f.border["left"] >= 2) & (f.border["right"] >= 2) & (f.border["top"] >= 1) & (f.border["bottom"] >= 1)
        M.darken(img, inside & ((np.floor(b) % 2) == 0), 0.45)
    k.overlay(tex, "drive", louvres, only=("east", "west", "south"))
    g.paint_all(tex, parts["drive"][0], lambda t, r, d: (t.rect(r[0] + 2, r[1] + 1, 4, 2, P.BLACK_PAINT.base),
                                                         t.hline(r[0] + 2, r[1] + 1, 3, P.WHITE_CERAMIC[4])) if d == "north" else None)
    k.paint(tex, ["collar", "cap", "cap_top"], k.GUN)
    alu = M.Material(ramp=K.ramp_of(ALU), tone=0.62, mottle=0.03, grain=0.02, dither=0.45, edge_light=0.16, edge_dark=0.1, ao=0.45,
                     grime=0.05, dust=0.02, brushed=0.07)
    k.paint(tex, "casing", alu)

    def bands(f, img):
        """By world height, so every unit's band lines up along the cascade."""
        if not f.vertical:
            return
        y = f.Y
        M.paste(img, k.on(f, k.HAZARD), (y >= 21.0) & (y < 23.0))
        M.paste(img, k.on(f, k.STEEL), (y < CAS_Y0 + 1) | (y > CAS_Y1 - 1))
        M.darken(img, (np.floor(y) == 13) | (np.floor(y) == 18), 0.7)          # casing section seams
    k.overlay(tex, "casing", bands)
    rotor = M.Material(ramp=K.ramp_of(ROTOR), tone=0.4, mottle=0.03, grain=0.02, dither=0.3, edge_light=0.1, edge_dark=0.05, ao=0.2,
                       grime=0.0, dust=0.0)
    for name, face in (("cas_front_l", "east"), ("cas_front_r", "west"), ("cas_front_lo", "up"), ("cas_front_hi", "down")):
        k.paint(tex, name, rotor, only=(face,))                                 # the slit's jambs: a dark recess

    def back(t, r, d):
        """The second casing row's north face: the rotor's dark drum where the slit looks in."""
        if d != "north":
            return
        x, y, w, h = r
        cx = x + w // 2
        top = round(CAS_Y1 - SLIT_Y1)
        t.rect(cx - 1, y + top, 2, round(SLIT_Y1 - SLIT_Y0), ROTOR[3])
        t.vline(cx - 1, y + top, round(SLIT_Y1 - SLIT_Y0), ROTOR[4])
    g.paint_all(tex, parts["casing"][4], back)
    k.paint(tex, "pipe", k.STEEL)
    k.paint(tex, "flange", k.BRASS)
    k.paint(tex, "valve", k.RED)
    for b in parts["mark"]:
        g.paint_all(tex, b, lambda t, r, d: t.rect(*r, ALU[6]))

    def lamp_paint(t, r, d):
        t.rect(*r, P.NUCLEAR_AMBER.light)
        t.px(r[0], r[1], P.NUCLEAR_AMBER.shine)
    g.paint_all(tex, parts["lamp"][0], lamp_paint)
    g.paint_all(glow, parts["lamp"][0], lamp_paint)


def animations() -> list[Animation]:
    """The marks slide one pitch per 0.1 s cycle: a rotor spinning far faster than the eye can follow."""
    w = Animation("working", 0.1, loop=True)
    w.move("rotor", {0: (0, 0, 0), 0.1: (MARK_PITCH, 0, 0)})
    return [w]


if __name__ == "__main__":
    model, texture, glowmask = build()
    for p in model.save(texture, glowmask):
        print(p)
    print(save_animations(NAME, animations()))
