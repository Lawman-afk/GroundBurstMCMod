"""Electrolytic Separator (GeckoLib block model + animation), DESIGN.md §8. One block.

Look: on the shared hazard-striped plinth, a glass-walled electrolysis cell (steel corner posts, a lid with cable
glands) full of water, two electrodes hanging into it and bubble streams rising off them while running. Two gas
bottles stand on the east side, fed by pipes from the lid: oxygen (white shoulder) and deuterium (blue band). A
small control panel with a lamp faces the front (north). Glass panes are mostly transparent; the renderer fills the
cell with the tank's actual fluid (texture, tint and level), so the electrodes and bubbles show through it.
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from gz import palette as P  # noqa: E402
from gz.geo import Animation, GeoModel, save_animations  # noqa: E402
from gz.palette import fixed  # noqa: E402
from gz.tex import Tex  # noqa: E402

NAME = "electrolytic_separator"
WATER = fixed("cell_water", "#0c2448", "#14366a", "#1e4c8e", "#2c66b0", "#4282cc", "#6aa4e0", "#a8d0f4")
CLEAR = (0, 0, 0, 0)
# Cell interior (x, z): the tank spans x -6.5..2.5, z -5.5..5.5, y 2..13.
X0, X1, Z0, Z1, Y0, Y1 = -6.5, 2.5, -5.5, 5.5, 2.0, 13.0


def tone(d: str) -> int:
    return {"up": 4, "down": 2}.get(d, 3)


def build() -> tuple[GeoModel, Tex, Tex]:
    g = GeoModel(NAME, tex_size=(128, 128))
    g.bone("frame")
    for i in range(6):
        g.bone(f"bubble{i}", pivot=(0, 4, 0), parent="frame")
    F = "frame"
    parts: dict[str, list] = {}

    def add(group, box):
        parts.setdefault(group, []).append(box)
        return box

    add("plinth", g.box("plinth", (-8, 0, -8), (8, 2, 8), bone=F))
    # No water geometry: the renderer draws the tank's real fluid at its real fill level inside the glass.
    # Glass panes (thin, mostly transparent) between the corner posts.
    add("glass", g.box("glass_n", (X0 + 1, Y0, Z0), (X1 - 1, Y1, Z0 + 0.2), faces=("north",), bone=F))
    add("glass", g.box("glass_s", (X0 + 1, Y0, Z1 - 0.2), (X1 - 1, Y1, Z1), faces=("south",), bone=F))
    add("glass", g.box("glass_w", (X0, Y0, Z0 + 1), (X0 + 0.2, Y1, Z1 - 1), faces=("west",), bone=F))
    add("glass", g.box("glass_e", (X1 - 0.2, Y0, Z0 + 1), (X1, Y1, Z1 - 1), faces=("east",), bone=F))
    for i, (x, z) in enumerate(((X0, Z0), (X1 - 1, Z0), (X0, Z1 - 1), (X1 - 1, Z1 - 1))):
        add("post", g.box(f"post{i}", (x, Y0, z), (x + 1, Y1, z + 1), faces=("north", "south", "east", "west"), bone=F))
    add("lid", g.box("lid", (X0 - 0.3, Y1, Z0 - 0.3), (X1 + 0.3, Y1 + 1.2, Z1 + 0.3), bone=F))
    # Electrodes (anode / cathode) through glands in the lid.
    for i, z in enumerate((-2.2, 1.4)):
        add("gland", g.box(f"gland{i}", (-3.0, Y1 + 1.2, z - 0.4), (-1.6, Y1 + 2.0, z + 1.2), faces=("north", "south", "east", "west", "up"), bone=F))
        add("electrode", g.box(f"electrode{i}", (-2.7, 4.0, z), (-1.9, Y1, z + 0.8), faces=("north", "south", "east", "west", "down"), bone=F))
    # Bubbles: small cubes rising off the electrodes (animated).
    for i in range(6):
        x = -2.3 + (0.9 if i % 2 else -0.9)
        z = (-1.8 if i < 3 else 1.8) + (i % 3 - 1) * 0.5
        add("bubble", g.box(f"bub{i}", (x - 0.3, 4.0, z - 0.3), (x + 0.3, 4.6, z + 0.3), faces=("north", "south", "east", "west", "up"), bone=f"bubble{i}"))
    # Gas bottles on the east side with a pipe run from the lid.
    for i, (z, cap) in enumerate(((-3.0, P.WHITE_CERAMIC), (2.6, P.CHERENKOV))):
        add(f"bottle{i}", g.box(f"bottle{i}", (4.2, 2, z - 1.6), (7.4, 12, z + 1.6), faces=("north", "south", "east", "west", "up"), bone=F))
        add(f"shoulder{i}", g.box(f"shoulder{i}", (4.6, 12, z - 1.2), (7.0, 13, z + 1.2), faces=("north", "south", "east", "west", "up"), bone=F))
        add("valve", g.box(f"valve{i}", (5.4, 13, z - 0.4), (6.2, 14, z + 0.4), faces=("north", "south", "east", "west", "up"), bone=F))
        add("pipe", g.box(f"pipe_up{i}", (5.55, 14, z - 0.25), (6.05, 14.6, z + 0.25), faces=("north", "south", "east", "west"), bone=F))
        # Runs across the lid into the header pipe, so each bottle is plumbed to the cell.
        add("pipe", g.box(f"pipe_run{i}", (0.5, 14.6, z - 0.25), (6.05, 15.1, z + 0.25), faces=("north", "south", "east", "up", "down"), bone=F))
    add("pipe", g.box("pipe_drop", (0.0, Y1 + 1.2, -3.25), (0.5, 15.1, 2.85), faces=("north", "south", "east", "west", "up"), bone=F))
    # Front control panel with a lamp and a little dial.
    add("panel", g.box("panel", (3.6, 3, -7.4), (7.6, 9, -5.6), faces=("north", "east", "west", "up", "down"), bone=F))
    lamp = add("lamp", g.box("lamp", (4.2, 7.2, -7.6), (5.2, 8.2, -7.4), faces=("north",), shade=False, bone=F))

    g.layout()
    tex, glow = Tex(g.tex_w, g.tex_h), Tex(g.tex_w, g.tex_h)
    gun, steel = P.GUNMETAL, P.STEEL

    g.paint_all(tex, parts["plinth"][0], lambda t, r, d: (
        t.stripes(*r, P.HAZARD_YELLOW.base, P.HAZARD_BLACK.base, width=2) if d == "north" else
        t.panel(*r, gun, base=tone(d), style="raised") if min(r[2], r[3]) >= 3 else t.rect(*r, gun[tone(d)])))

    def glass(t, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, CLEAR)
        t.hline(x, y, w, (200, 230, 240, 255))                               # top edge catch-light
        for k in range(min(w, h) // 3):                                      # diagonal glints
            t.px(x + 1 + k, y + 2 + k, (210, 236, 246, 255))
        t.px(x + w - 2, y + h - 3, (190, 220, 232, 255))
    for b in parts["glass"]:
        g.paint_all(tex, b, glass)
    for b in parts["post"]:
        g.paint_all(tex, b, lambda t, r, d: (t.rect(*r, steel[tone(d)]), t.vline(r[0], r[1], r[3], steel[5]) if d in ("north", "west") else None))

    def lid(t, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, P.OLIVE[tone(d)])
        if d == "up":
            t.outline(x, y, w, h, P.OLIVE.shadow)
            t.rivet(x + 1, y + 1, P.OLIVE)
            t.rivet(x + w - 2, y + h - 2, P.OLIVE)
        else:
            t.hline(x, y, w, P.OLIVE.light)
    g.paint_all(tex, parts["lid"][0], lid)
    for b in parts["gland"]:
        g.paint_all(tex, b, lambda t, r, d: t.rect(*r, P.RUBBER[tone(d)]))
    for i, b in enumerate(parts["electrode"]):
        metal = P.COPPER if i == 0 else P.STEEL
        g.paint_all(tex, b, lambda t, r, d, m=metal: (t.rect(*r, m[tone(d)]), t.vline(r[0], r[1], r[3], m[5]) if r[2] > 1 else None))
    for b in parts["bubble"]:
        g.paint_all(tex, b, lambda t, r, d: (t.rect(*r, WATER[6]), t.px(r[0], r[1], (240, 250, 255, 255))))

    for i, cap in enumerate((P.WHITE_CERAMIC, P.CHERENKOV)):
        def bottle(t, r, d, cap=cap):
            x, y, w, h = r
            t.rect(x, y, w, h, P.NATO_GREEN[tone(d) + (1 if d in ("north", "west") else 0)])
            if d in ("north", "south", "east", "west") and h >= 6:
                t.vline(x + 1, y, h, P.NATO_GREEN.light)                    # cylinder highlight
                t.rect(x, y + 1, w, 2, cap[4])                              # gas colour band
                t.px(x + w // 2, y + h - 3, P.HAZARD_YELLOW.base)          # stencil mark
        g.paint_all(tex, parts[f"bottle{i}"][0], bottle)
        g.paint_all(tex, parts[f"shoulder{i}"][0], lambda t, r, d, cap=cap: t.rect(*r, cap[tone(d) + 1]))
    for b in parts["valve"]:
        g.paint_all(tex, b, lambda t, r, d: t.rect(*r, P.BRASS[tone(d) + 1]))
    for b in parts["pipe"]:
        g.paint_all(tex, b, lambda t, r, d: (t.rect(*r, steel[tone(d)]), t.hline(r[0], r[1], r[2], steel[5]) if d == "up" else None))

    def panel(t, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, gun[tone(d) + 1])
        if d == "north":
            t.outline(x, y, w, h, gun[1])
            t.rect(x + 1, y + 1, w - 2, 2, P.BLACK_PAINT.base)              # readout window
            t.hline(x + 2, y + 1, 2, P.INDICATOR_GREEN[3])
            t.px(x + w - 2, y + h - 2, P.WARNING_RED.base)                  # switch
    g.paint_all(tex, parts["panel"][0], panel)

    def lamp_paint(t, r, d):
        t.rect(*r, P.CHERENKOV.light)
        t.px(r[0], r[1], P.CHERENKOV.shine)
    g.paint_all(tex, lamp, lamp_paint)
    g.paint_all(glow, lamp, lamp_paint)
    return g, tex, glow


def animations() -> list[Animation]:
    working = Animation("working", 1.2, loop=True)
    # Each bubble rises from the electrode tip to the surface, then pops back down, staggered.
    rise = 5.6
    for i in range(6):
        off = (i * 0.2) % 1.2
        keys = {}
        for k in range(7):
            t = round(k * 0.2, 2)
            phase = ((t + off) % 1.2) / 1.2
            keys[t] = (0, phase * rise, 0)
        working.move(f"bubble{i}", keys)
    return [working]


if __name__ == "__main__":
    model, texture, glowmask = build()
    for p in model.save(texture, glowmask):
        print(p)
    print(save_animations(NAME, animations()))
