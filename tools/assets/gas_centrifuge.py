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
    gun, steel = P.GUNMETAL, P.STEEL

    g.paint_all(tex, parts["plinth"][0], lambda t, r, d: (
        t.stripes(*r, P.HAZARD_YELLOW.base, P.HAZARD_BLACK.base, width=2) if d == "north" else
        t.panel(*r, gun, base=tone(d), style="raised") if min(r[2], r[3]) >= 3 else t.rect(*r, gun[tone(d)])))

    def drive(t, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, P.NATO_GREEN[tone(d)])
        if d == "up":
            t.outline(x, y, w, h, P.NATO_GREEN.shadow)
            return
        t.hline(x, y, w, P.NATO_GREEN.light)
        if d == "north":
            t.rect(x + 2, y + 1, 4, 2, P.BLACK_PAINT.base)                 # nameplate
            t.hline(x + 2, y + 1, 3, P.WHITE_CERAMIC[4])
        else:
            t.vent(x + 2, y + 1, w - 4, h - 2, P.NATO_GREEN, slat=2)
    g.paint_all(tex, parts["drive"][0], drive)
    g.paint_all(tex, parts["collar"][0], lambda t, r, d: (t.rect(*r, gun[tone(d)]), t.hline(r[0], r[1], r[2], gun[5]) if d != "up" else None))

    # Casing: brushed aluminium with vertical brushing, a hazard band and a stencilled cascade number. Paint is
    # laid out by world height so every row's band lines up.
    def casing(t, r, d, box):
        x, y, w, h = r
        y_top = box.to[1]
        t.rect(x, y, w, h, ALU[4] if d in ("north", "west", "up") else ALU[3])
        if d in ("up", "down"):
            return
        if d in ("north", "west") and w >= 2:
            t.vline(x, y, h, ALU[5])                                       # lit edge of each facet
        for yy in range(h):
            wy = y_top - yy - 0.5                                          # world height of this texel row
            if 21.0 <= wy < 23.0:
                for xx in range(w):
                    t.px(x + xx, y + yy, P.HAZARD_YELLOW.base if ((xx + int(wy)) // 2) % 2 == 0 else P.HAZARD_BLACK.base)
            elif wy < CAS_Y0 + 1 or wy > CAS_Y1 - 1:
                t.hline(x, y + yy, w, steel[3])                            # end rings
            elif int(wy) in (13, 18):
                t.hline(x, y + yy, w, ALU[2])                              # casing section seams
        if d == "north" and box.name == "cas_front_lo":
            t.hline(x, y, w, ALU[2])
    for b in parts["casing"]:
        g.paint_all(tex, b, lambda t, r, d, b=b: casing(t, r, d, b))

    # The slit's jambs (inner faces) and the slit back read as dark recess.
    def jamb(t, r):
        t.rect(*r, ROTOR[3])
    for name, face in (("cas_front_l", "east"), ("cas_front_r", "west")):
        g.paint(tex, name, face, lambda t, r: jamb(t, r))
    g.paint(tex, "cas_front_lo", "up", lambda t, r: t.rect(*r, ROTOR[2]))
    g.paint(tex, "cas_front_hi", "down", lambda t, r: t.rect(*r, ROTOR[2]))

    def back(t, r, d):
        """Second casing row's north face: aluminium, with the rotor's dark drum where the slit looks in."""
        casing(t, r, d, g.get("cas1"))
        if d != "north":
            return
        x, y, w, h = r
        cx = x + w // 2
        top = round(CAS_Y1 - SLIT_Y1)
        t.rect(cx - 1, y + top, 2, round(SLIT_Y1 - SLIT_Y0), ROTOR[3])
        t.vline(cx - 1, y + top, round(SLIT_Y1 - SLIT_Y0), ROTOR[4])
    g.paint_all(tex, parts["casing"][4], back)

    for b in parts["cap"] + parts["cap_top"]:
        g.paint_all(tex, b, lambda t, r, d: (t.rect(*r, gun[tone(d) + 1]), t.outline(*r, gun[2]) if d == "up" and min(r[2], r[3]) > 3 else None))
    for b in parts["pipe"]:
        g.paint_all(tex, b, lambda t, r, d: (t.rect(*r, steel[tone(d)]), t.hline(r[0], r[1], r[2], steel[5]) if d == "up" else None))
    for b in parts["flange"]:
        g.paint_all(tex, b, lambda t, r, d: (t.rect(*r, P.BRASS[tone(d)]), t.outline(*r, P.BRASS[2]) if min(r[2], r[3]) >= 3 else None))
    g.paint_all(tex, parts["valve"][0], lambda t, r, d: t.rect(*r, P.WARNING_RED[tone(d) + 1]))
    for b in parts["mark"]:
        g.paint_all(tex, b, lambda t, r, d: t.rect(*r, ALU[6]))

    def lamp_paint(t, r, d):
        t.rect(*r, P.NUCLEAR_AMBER.light)
        t.px(r[0], r[1], P.NUCLEAR_AMBER.shine)
    g.paint_all(tex, lamp, lamp_paint)
    g.paint_all(glow, lamp, lamp_paint)
    return g, tex, glow


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
