"""Fusion Fuel Synthesizer (GeckoLib block model + animation), DESIGN.md §8. One block.

Look: a squat round pressure chamber (bolted flange ring and dome) on the shared hazard-striped plinth. A pellet press
stands on the dome: two posts, a crosshead and a hydraulic cylinder whose ram (bone "ram") drives down into the dome
with a clunk once per cycle. A viewport on the front glows blue-white while the chamber runs (bone "core") and frosts
over (bone "frost"); both are hidden when idle. A gauge cabinet at the front-east carries two dials whose needles
(bones "needle0", "needle1") climb as the press builds pressure. A deuterium cylinder (blue band) stands behind it,
piped into the chamber, and finished pellets drop down a spout into a tray at the front-west.
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from gz import palette as P  # noqa: E402
from gz.geo import Animation, GeoModel, save_animations  # noqa: E402
from gz.palette import fixed  # noqa: E402
from gz.tex import Tex  # noqa: E402

NAME = "fusion_fuel_synthesizer"
FROST = fixed("frost", "#5c7a8c", "#86a6b8", "#aecbd8", "#cfe4ec", "#e4f2f7", "#f2f9fc", "#ffffff")
CLEAR = (0, 0, 0, 0)
CX = -1.5                       # chamber centre (x); z centre 0
CH_Y0, CH_Y1 = 2.0, 9.0         # chamber body
BANDS = [(-5, -4, 2.5), (-4, -2.5, 4), (-2.5, 2.5, 5), (2.5, 4, 4), (4, 5, 2.5)]
DOME = [(-4, -3, 2), (-3, 3, 3.5), (3, 4, 2)]
DOME_Y0, DOME_Y1 = 10.0, 11.5
RAM_X = (CX - 0.4, CX + 0.4)
GAUGES = [(5.75, 7.0), (5.75, 3.8)]   # dial centres (x, y) on the cabinet front


def tone(d: str) -> int:
    return {"up": 4, "down": 2}.get(d, 3)


def build() -> tuple[GeoModel, Tex, Tex]:
    g = GeoModel(NAME, tex_size=(128, 128))
    g.bone("frame")
    g.bone("ram", pivot=(CX, 13, 0), parent="frame")
    g.bone("core", pivot=(CX, 6, -5), parent="frame")
    g.bone("frost", pivot=(CX, 6, -5), parent="frame")
    for i, (gx, gy) in enumerate(GAUGES):
        g.bone(f"needle{i}", pivot=(gx, gy, -7.0), parent="frame")
    F = "frame"
    parts: dict[str, list] = {}

    def add(group, box):
        parts.setdefault(group, []).append(box)
        return box

    add("plinth", g.box("plinth", (-8, 0, -8), (8, 2, 8), bone=F))
    for i, (z0, z1, hw) in enumerate(BANDS):
        add("chamber", g.box(f"ch{i}", (CX - hw, CH_Y0, z0), (CX + hw, CH_Y1, z1), faces=("north", "south", "east", "west"), bone=F))
    # Bolted flange ring (one step wider), then the dome.
    for i, (z0, z1, hw) in enumerate(BANDS):
        add("flange", g.box(f"fl{i}", (CX - hw - 0.4, CH_Y1, z0 - (0.4 if i == 0 else 0)), (CX + hw + 0.4, DOME_Y0, z1 + (0.4 if i == 4 else 0)),
                            faces=("north", "south", "east", "west", "up", "down"), bone=F))
    for i, (z0, z1, hw) in enumerate(DOME):
        add("dome", g.box(f"dome{i}", (CX - hw, DOME_Y0, z0), (CX + hw, DOME_Y1, z1), bone=F))
    # Press: posts, crosshead, cylinder, gland on the dome, and the ram.
    for i, x in enumerate((CX - 3.2, CX + 2.4)):
        add("post", g.box(f"post{i}", (x, DOME_Y1, -0.4), (x + 0.8, 15.2, 0.4), faces=("north", "south", "east", "west"), bone=F))
    add("crosshead", g.box("crosshead", (CX - 3.6, 15.2, -1.0), (CX + 3.6, 16.0, 1.0), bone=F))
    # The cylinder is closed on top: it is a touch deeper than the crosshead, so its roof shows along the edges.
    add("cylinder", g.box("cylinder", (CX - 1.1, 13.4, -1.1), (CX + 1.1, 15.2, 1.1), faces=("north", "south", "east", "west", "up", "down"), bone=F))
    add("gland", g.box("gland", (CX - 0.9, DOME_Y1, -0.9), (CX + 0.9, 12.2, 0.9), faces=("north", "south", "east", "west", "up"), bone=F))
    add("ram", g.box("ram_rod", (RAM_X[0], 12.0, -0.4), (RAM_X[1], 15.0, 0.4), faces=("north", "south", "east", "west"), bone="ram"))
    # Viewport on the front of the chamber: frame, dark window, glowing core, frost (front to back: frost, core, window).
    add("vframe", g.box("vframe", (CX - 2.2, 3.6, -5.6), (CX + 2.2, 7.8, -5.0), faces=("north", "east", "west", "up", "down"), bone=F))
    window = add("window", g.box("window", (CX - 1.5, 4.3, -5.7), (CX + 1.5, 7.1, -5.6), faces=("north",), bone=F))
    core = add("core", g.box("core_glow", (CX - 1.5, 4.3, -5.84), (CX + 1.5, 7.1, -5.82), faces=("north",), shade=False, bone="core"))
    frost = add("frostbox", g.box("frost_box", (CX - 1.5, 4.3, -5.98), (CX + 1.5, 7.1, -5.96), faces=("north",), bone="frost"))
    # Gauge cabinet (front-east) with two dials and their needles.
    add("cabinet", g.box("cabinet", (4.0, 2, -6.6), (7.5, 9.0, -2.6), bone=F))
    for i, (gx, gy) in enumerate(GAUGES):
        add("dial", g.box(f"dial{i}", (gx - 1.5, gy - 1.5, -6.8), (gx + 1.5, gy + 1.5, -6.6), faces=("north", "east", "west", "up", "down"), bone=F))
        add("needle", g.box(f"needle_box{i}", (gx - 0.1, gy - 0.1, -7.0), (gx + 0.1, gy + 0.9, -6.9), faces=("north",), bone=f"needle{i}"))
    # Deuterium cylinder behind the cabinet, piped into the chamber.
    add("bottle", g.box("bottle", (4.4, 2, 1.6), (7.4, 10.5, 4.6), faces=("north", "south", "east", "west"), bone=F))
    add("shoulder", g.box("shoulder", (4.8, 10.5, 2.0), (7.0, 11.4, 4.2), bone=F))
    add("valve", g.box("valve", (5.5, 11.4, 2.7), (6.3, 12.2, 3.5), faces=("north", "south", "east", "west", "up"), bone=F))
    add("pipe", g.box("d_pipe", (2.9, 7.6, 2.8), (4.4, 8.2, 3.4), faces=("north", "south", "up", "down"), bone=F))
    # Pellet spout and tray (front-west).
    add("spout", g.box("spout", (-6.4, 3.4, -4.6), (-5.0, 4.6, -3.4), faces=("north", "west", "up", "down", "east"), bone=F))
    add("tray", g.box("tray", (-7.4, 2, -7.4), (-4.4, 3.0, -4.6), bone=F))

    g.layout()
    tex, glow = Tex(g.tex_w, g.tex_h), Tex(g.tex_w, g.tex_h)
    gun, steel = P.GUNMETAL, P.STEEL
    body = P.WHITE_CERAMIC

    g.paint_all(tex, parts["plinth"][0], lambda t, r, d: (
        t.stripes(*r, P.HAZARD_YELLOW.base, P.HAZARD_BLACK.base, width=2) if d == "north" else
        t.panel(*r, gun, base=tone(d), style="raised") if min(r[2], r[3]) >= 3 else t.rect(*r, gun[tone(d)])))

    def chamber(t, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, body[3] if d in ("north", "west") else body[2])
        if w >= 2 and d in ("north", "west"):
            t.vline(x, y, h, body[4])
        t.hline(x, y + h - 1, w, steel[2])                                 # foot ring
        t.hline(x, y + 2, w, P.NUCLEAR_AMBER[3])                           # amber pressure-class stripe
    for b in parts["chamber"]:
        g.paint_all(tex, b, chamber)

    def flange(t, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, steel[tone(d)])
        if d not in ("up", "down"):
            for xx in range(x + 1, x + w - 1, 3):
                t.px(xx, y + h // 2, steel[6])                             # bolt heads
    for b in parts["flange"]:
        g.paint_all(tex, b, flange)
    for b in parts["dome"]:
        g.paint_all(tex, b, lambda t, r, d: (t.rect(*r, body[tone(d)]), t.outline(*r, body[1]) if d == "up" and min(r[2], r[3]) > 3 else None))
    for b in parts["post"]:
        g.paint_all(tex, b, lambda t, r, d: (t.rect(*r, steel[tone(d)]), t.vline(r[0], r[1], r[3], steel[5])))
    g.paint_all(tex, parts["crosshead"][0], lambda t, r, d: (
        t.stripes(*r, P.HAZARD_YELLOW.base, P.HAZARD_BLACK.base, width=2) if d in ("north", "south") else t.rect(*r, gun[tone(d) + 1])))
    g.paint_all(tex, parts["cylinder"][0], lambda t, r, d: (t.rect(*r, P.WARNING_RED[tone(d)]), t.vline(r[0], r[1], r[3], P.WARNING_RED[5]) if r[2] > 1 else None))
    g.paint_all(tex, parts["gland"][0], lambda t, r, d: t.rect(*r, gun[tone(d)]))
    g.paint_all(tex, parts["ram"][0], lambda t, r, d: (t.rect(*r, steel[5]), t.px(r[0], r[1], steel[6])))

    g.paint_all(tex, parts["vframe"][0], lambda t, r, d: (t.rect(*r, steel[tone(d) + 1]), t.outline(*r, steel[2]) if d == "north" else None))

    def window_paint(t, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, P.BLACK_PAINT[2])
        t.hline(x, y, w, P.BLACK_PAINT[4])
    g.paint_all(tex, window, window_paint)

    def core_paint(t, r, d, target):
        """A pellet glowing in the compression cell, in a blue-white halo; the rest of the pane is clear."""
        x, y, w, h = r
        t.rect(x, y, w, h, CLEAR)
        cx, cy = x + w / 2 - 0.5, y + h / 2 - 0.5
        for yy in range(h):
            for xx in range(w):
                dd = ((xx + x - cx) ** 2 + (yy + y - cy) ** 2) ** 0.5
                if dd < 1.0:
                    target.px(x + xx, y + yy, P.WHITE_CERAMIC.shine)
                elif dd < 2.2:
                    target.px(x + xx, y + yy, P.CHERENKOV[5])
                elif dd < 3.0 and (xx + yy) % 2 == 0:
                    target.px(x + xx, y + yy, P.CHERENKOV[3])
    g.paint_all(tex, core, lambda t, r, d: core_paint(t, r, d, t))
    g.paint_all(glow, core, lambda t, r, d: core_paint(t, r, d, t))

    def frost_paint(t, r, d):
        """Frost creeping in from the frame: opaque at the edges, clear in the middle where the glow shows."""
        x, y, w, h = r
        t.rect(x, y, w, h, CLEAR)
        for yy in range(h):
            for xx in range(w):
                edge = min(xx, yy, w - 1 - xx, h - 1 - yy)
                k = (xx * 7 + yy * 13) % 5
                if edge == 0 or (edge == 1 and k < 3) or (edge == 2 and k == 0):
                    t.px(x + xx, y + yy, FROST[5] if k == 0 else FROST[4] if k < 3 else FROST[3])
    g.paint_all(tex, frost, frost_paint)

    def cabinet(t, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, P.OLIVE[tone(d)])
        if d == "up":
            t.outline(x, y, w, h, P.OLIVE.shadow)
        elif d == "north":
            t.outline(x, y, w, h, P.OLIVE.shadow)
            t.px(x + 1, y + h - 2, P.INDICATOR_GREEN.light)                # power lamp
        else:
            t.hline(x, y, w, P.OLIVE.light)
            t.vent(x + 1, y + 2, w - 2, h - 4, P.OLIVE, slat=2)
    g.paint_all(tex, parts["cabinet"][0], cabinet)
    g.paint_all(glow, parts["cabinet"][0], lambda t, r, d: t.px(r[0] + 1, r[1] + r[3] - 2, P.INDICATOR_GREEN.light) if d == "north" else None)

    def dial(t, r, d):
        x, y, w, h = r
        if d != "north":
            t.rect(x, y, w, h, P.BRASS[tone(d)])
            return
        t.rect(x, y, w, h, P.WHITE_CERAMIC[5])
        for cx_, cy_ in ((x, y), (x + w - 1, y), (x, y + h - 1), (x + w - 1, y + h - 1)):
            t.px(cx_, cy_, P.BRASS[4])                                      # brass bezel corners: a round dial
        t.px(x + w - 1, y + 1, P.WARNING_RED.base)                         # red zone
    for b in parts["dial"]:
        g.paint_all(tex, b, dial)
    for b in parts["needle"]:
        g.paint_all(tex, b, lambda t, r, d: t.rect(*r, P.BLACK_PAINT[1]))

    def bottle(t, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, P.NATO_GREEN[tone(d) + (1 if d in ("north", "west") else 0)])
        if h >= 6:
            t.vline(x + 1, y, h, P.NATO_GREEN.light)
            t.rect(x, y + 1, w, 2, P.CHERENKOV[4])                         # deuterium: blue band
            t.px(x + w // 2, y + h - 3, P.HAZARD_YELLOW.base)
    g.paint_all(tex, parts["bottle"][0], bottle)
    g.paint_all(tex, parts["shoulder"][0], lambda t, r, d: t.rect(*r, P.CHERENKOV[tone(d) + 1]))
    g.paint_all(tex, parts["valve"][0], lambda t, r, d: t.rect(*r, P.BRASS[tone(d) + 1]))
    g.paint_all(tex, parts["pipe"][0], lambda t, r, d: (t.rect(*r, steel[tone(d)]), t.hline(r[0], r[1], r[2], P.CHERENKOV[4]) if d == "up" else None))
    g.paint_all(tex, parts["spout"][0], lambda t, r, d: t.rect(*r, steel[tone(d)]))

    def tray(t, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, steel[tone(d) + 1])
        if d == "up":
            t.outline(x, y, w, h, steel[2])
            t.rect(x + 1, y + 1, w - 2, h - 2, gun[2])                      # tray well
            for px_, py_ in ((1, 1), (2, 1), (1, 2)):                       # pellets heaped in a corner
                t.px(x + px_, y + py_, P.CONCRETE[6])
        elif d == "north":
            t.hline(x, y, w, P.HAZARD_YELLOW.base)
    g.paint_all(tex, parts["tray"][0], tray)
    return g, tex, glow


def animations() -> list[Animation]:
    """2 s cycle: pressure builds (needles climb), the ram slams down at 1.0 s, holds, then lifts as pressure bleeds."""
    w = Animation("working", 2.0, loop=True)
    w.move("ram", {0: (0, 0, 0), 0.85: (0, 0, 0), 1.0: (0, -1.4, 0), 1.25: (0, -1.4, 0), 1.85: (0, 0, 0), 2.0: (0, 0, 0)})
    w.rotate("needle0", {0: (0, 0, 60), 1.0: (0, 0, -70), 1.2: (0, 0, -55), 2.0: (0, 0, 60)}, easing="easeInOutSine")
    w.rotate("needle1", {0: (0, 0, 20), 0.5: (0, 0, -10), 1.0: (0, 0, 25), 1.5: (0, 0, -5), 2.0: (0, 0, 20)})
    return [w]


if __name__ == "__main__":
    model, texture, glowmask = build()
    for p in model.save(texture, glowmask):
        print(p)
    print(save_animations(NAME, animations()))
