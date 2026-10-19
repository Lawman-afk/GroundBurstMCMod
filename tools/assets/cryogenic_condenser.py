"""Cryogenic Condenser (GeckoLib block model + animation), DESIGN.md §8. Two blocks tall.

Look: a tall insulated cold column (white insulation, steel hoop bands) on the shared hazard-striped plinth,
wrapped in copper cooling coils. A compressor with a spinning fan sits at its foot on the west side; a vapour vent
on top puffs cold vapour while running. A frost crust (bone "frost") covers the coils only while the machine runs;
the renderer hides it otherwise. A filling port with a frost-blue window faces the front (north).
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from gz import palette as P  # noqa: E402
from gz.geo import Animation, GeoModel, save_animations  # noqa: E402
from gz.palette import fixed  # noqa: E402
from gz.tex import Tex  # noqa: E402

NAME = "cryogenic_condenser"
INSUL = fixed("insulation", "#3c464e", "#5c6a74", "#86949e", "#aeb9c0", "#cdd6db", "#e4eaed", "#f6f9fa")
FROST = fixed("frost", "#5c7a8c", "#86a6b8", "#aecbd8", "#cfe4ec", "#e4f2f7", "#f2f9fc", "#ffffff")
COLUMN_Y0, COLUMN_Y1 = 3.0, 27.0
# Stepped round column: (z0, z1, half-width in x).
BANDS = [(-5, -4, 2.5), (-4, -2.5, 4), (-2.5, 2.5, 5), (2.5, 4, 4), (4, 5, 2.5)]
COIL_YS = [7.0, 11.0, 15.0, 19.0, 23.0]


def tone(d: str) -> int:
    return {"up": 4, "down": 2}.get(d, 3)


def build() -> tuple[GeoModel, Tex, Tex]:
    g = GeoModel(NAME, tex_size=(128, 128))
    g.bone("frame")
    g.bone("fan", pivot=(-7.75, 5, 0), parent="frame")
    g.bone("frost", pivot=(0, 15, 0), parent="frame")
    for i in range(3):
        g.bone(f"puff{i}", pivot=(0, 30, 0), parent="frame")
    F = "frame"
    parts: dict[str, list] = {}

    def add(group, box):
        parts.setdefault(group, []).append(box)
        return box

    add("plinth", g.box("plinth", (-8, 0, -8), (8, 3, 8), bone=F))
    for i, (z0, z1, hw) in enumerate(BANDS):
        add("column", g.box(f"col{i}", (-hw, COLUMN_Y0, z0), (hw, COLUMN_Y1, z1), faces=("north", "south", "east", "west"), bone=F))
    add("cap", g.box("cap", (-4, COLUMN_Y1, -4), (4, COLUMN_Y1 + 1.5, 4), bone=F))
    add("vent", g.box("vent", (-1.5, COLUMN_Y1 + 1.5, -1.5), (1.5, COLUMN_Y1 + 3.5, 1.5), faces=("north", "south", "east", "west", "up"), bone=F))
    add("vent_cap", g.box("vent_cap", (-2.2, COLUMN_Y1 + 3.5, -2.2), (2.2, COLUMN_Y1 + 4.1, 2.2), bone=F))
    # Copper coil rings, a hair outside the column; frost shells a hair outside the coils (only while running).
    for i, y in enumerate(COIL_YS):
        add("coil", g.box(f"coil_ns{i}", (-5.4, y, -5.4), (5.4, y + 1, 5.4), faces=("north", "south", "east", "west"), bone=F))
        add("frost", g.box(f"frost{i}", (-5.7, y - 0.4, -5.7), (5.7, y + 1.4, 5.7), faces=("north", "south", "east", "west", "up"), bone="frost"))
    # Compressor at the foot (west) with a fan grille.
    # Solid housing that runs back into the column (no gap, no open back), its top just under the first coil.
    add("compressor", g.box("compressor", (-7.5, 3, -3.8), (-3.6, 6.6, 3.8), faces=("north", "south", "west", "up"), bone=F))
    fan = add("fan", g.box("fan_blades", (-7.9, 3.6, -1.4), (-7.6, 6.4, 1.4), faces=("west",), bone="fan"))
    add("fan", g.box("fan_blades45", (-7.8, 3.6, -1.4), (-7.6, 6.4, 1.4), faces=("west",), rotation={"origin": (-7.75, 5, 0), "x": 45}, bone="fan"))
    # Filling port on the front.
    add("port", g.box("port", (-2, 9, -6.8), (2, 13, -5), faces=("north", "east", "west", "up", "down"), bone=F))
    window = add("window", g.box("window", (-1.2, 10, -7.0), (1.2, 12, -6.8), faces=("north",), shade=False, bone=F))
    # Vapour puffs over the vent.
    for i in range(3):
        px, pz = (-1.6, 0.0, 1.4)[i], (-0.9, 0.8, -0.4)[i]
        add("puff", g.box(f"puff_box{i}", (px - 0.6, COLUMN_Y1 + 4.4 + i * 0.3, pz - 0.6), (px + 0.6, COLUMN_Y1 + 5.6 + i * 0.3, pz + 0.6),
                          faces=("north", "south", "east", "west", "up"), bone=f"puff{i}"))

    g.layout()
    tex, glow = Tex(g.tex_w, g.tex_h), Tex(g.tex_w, g.tex_h)
    gun, steel = P.GUNMETAL, P.STEEL

    g.paint_all(tex, parts["plinth"][0], lambda t, r, d: (
        t.stripes(*r, P.HAZARD_YELLOW.base, P.HAZARD_BLACK.base, width=2) if d == "north" else
        t.panel(*r, gun, base=tone(d), style="raised") if min(r[2], r[3]) >= 3 else t.rect(*r, gun[tone(d)])))

    def column(t, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, INSUL[4] if d in ("north", "west") else INSUL[3])
        for yy in range(y + 2, y + h, 4):
            t.hline(x, yy, w, INSUL[2])                                     # insulation wrap seams
        for yy in (y, y + h - 1):
            t.hline(x, yy, w, steel[3])                                     # steel hoop bands at the ends
        if d == "north" and w >= 4:
            t.rect(x + w // 2 - 1, y + h - 8, 3, 4, P.CHERENKOV[3])        # "LN2/LOX" decal block
            t.px(x + w // 2, y + h - 7, P.WHITE_CERAMIC[6])
    for b in parts["column"]:
        g.paint_all(tex, b, column)
    for b in parts["cap"] + parts["vent_cap"]:
        g.paint_all(tex, b, lambda t, r, d: (t.rect(*r, steel[tone(d) + 1]), t.outline(*r, steel[2]) if d == "up" and min(r[2], r[3]) > 3 else None))
    g.paint_all(tex, parts["vent"][0], lambda t, r, d: (t.rect(*r, gun[tone(d)]), t.vent(r[0], r[1], r[2], r[3], gun, slat=2) if r[3] >= 2 and d != "up" else None))
    for b in parts["coil"]:
        g.paint_all(tex, b, lambda t, r, d: (t.rect(*r, P.COPPER[4]), t.hline(r[0], r[1], r[2], P.COPPER[6])))

    def frost(t, r, d):
        x, y, w, h = r
        for yy in range(y, y + h):
            for xx in range(x, x + w):
                k = (xx * 7 + yy * 13) % 5
                t.px(xx, yy, FROST[5] if k == 0 else FROST[4] if k < 3 else FROST[3])
    for b in parts["frost"]:
        g.paint_all(tex, b, frost)

    def compressor(t, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, P.NATO_GREEN[tone(d)])
        if d == "west":
            t.outline(x, y, w, h, P.NATO_GREEN[1])
            t.ring(x + w / 2, y + h / 2, 1.0, 2.6, P.NATO_GREEN[2])         # fan shroud
    g.paint_all(tex, parts["compressor"][0], compressor)
    for b in parts["fan"]:
        g.paint_all(tex, b, lambda t, r, d: (t.rect(*r, steel[4]), t.px(r[0] + r[2] // 2, r[1], P.WARNING_RED.base)))

    def port(t, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, gun[tone(d) + 1])
        if d == "north":
            t.outline(x, y, w, h, gun[1])
    g.paint_all(tex, parts["port"][0], port)

    def window_paint(t, r, d):
        t.gradient_v(*r, P.CRYO[5], P.CRYO[3])
        t.px(r[0], r[1], P.CRYO.shine)
    g.paint_all(tex, window, window_paint)
    g.paint_all(glow, window, window_paint)
    for b in parts["puff"]:
        g.paint_all(tex, b, lambda t, r, d: t.rect(*r, FROST[5] if d == "up" else FROST[4]))
    return g, tex, glow


def animations() -> list[Animation]:
    working = Animation("working", 1.5, loop=True)
    working.rotate("fan", {0: (0, 0, 0), 1.5: (1080, 0, 0)})
    for i in range(3):
        off = i * 0.5
        keys_m, keys_s = {}, {}
        for k in range(4):
            t = round(k * 0.5, 2)
            phase = ((t + off) % 1.5) / 1.5
            keys_m[t] = (0, phase * 3.0, 0)
            s = 0.4 + phase * 1.0
            keys_s[t] = (s, s, s)
        working.move(f"puff{i}", keys_m)
        working.scale(f"puff{i}", keys_s)
    # Idle: puffs tucked inside the vent cap (scale 0 hides them).
    return [working]


if __name__ == "__main__":
    model, texture, glowmask = build()
    for p in model.save(texture, glowmask):
        print(p)
    print(save_animations(NAME, animations()))
