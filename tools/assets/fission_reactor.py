"""Fission Reactor, formed (GeckoLib model + animation), DESIGN.md §7 / §23.3. A 3x3x4 structure drawn from its
controller at the front middle of the bottom layer: the model spans x -24..24, z -8..40, y 0..64 in the north-facing
frame (model origin = controller block bottom centre). A game abstraction, not a reactor design.

Look: a lead-lined vessel. The base carries the control cabinet on the front (green CRT, status lamps, rod-position
dial) and hazard edging; four lead corner pillars frame deep window bays on all four sides, each with a leaded glass
pane at the core so you look into the water-filled core (the renderer draws the coolant tank's real water there).
Inside, a 3x3 fuel bundle between grid plates, four control-rod blades shown at the set insertion (bones "rods0"
.."rods4", one shown at a time) and a Cherenkov-blue glow round the bundle while it runs (bone "glow"). The head
carries two port hatches (fuel in at the front, products out at the back), a central energy terminal on ceramic
insulators and four control-rod drive housings. The coolant inlet (the Fluid Port, blue-hatched) sits low on the back
of the base, with a header feeding the two coolant pipes that run up the back.
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from gz import palette as P  # noqa: E402
from gz.geo import Animation, GeoModel, save_animations  # noqa: E402
from gz.palette import fixed  # noqa: E402
from gz.tex import Tex  # noqa: E402

NAME = "fission_reactor"
LEAD = P.LEAD
CLEAR = (0, 0, 0, 0)
FLUID_BLUE = fixed("reactor_fluid_blue", "#0c1a3a", "#12285a", "#1a3a7e", "#2452a6", "#3a6ec8", "#6a98e0", "#a8c8f4")
GLASS_TINT = fixed("lead_glass", "#141a1e", "#1e262c", "#2a343c", "#36424c", "#4a5864", "#6a7a88", "#a4b4c0")
FUEL = fixed("zircaloy", "#2a2c2e", "#3e4144", "#575b5f", "#71767b", "#8d9398", "#acb2b7", "#d4d9dd")
BASE_Y, HEAD_Y, TOP = 16.0, 48.0, 64.0
CORE = (-8.0, 8.0, 8.0, 24.0)          # x0, x1, z0, z1 of the core cell
RODS_X, RODS_Z = (-5.0, 0.0, 5.0), (11.0, 16.0, 21.0)
BLADES = [(-2.5, 13.5), (2.5, 13.5), (-2.5, 18.5), (2.5, 18.5)]
ROD_BOTTOM, ROD_TOP = 18.0, 46.0


def tone(d: str) -> int:
    return {"up": 4, "down": 2}.get(d, 3)


def build() -> tuple[GeoModel, Tex, Tex]:
    g = GeoModel(NAME, tex_size=(256, 256))
    g.bone("frame")
    g.bone("glow", pivot=(0, 30, 16), parent="frame")
    for k in range(5):
        g.bone(f"rods{k}", pivot=(0, 30, 16), parent="frame")
    g.exclusive = [{f"rods{k}" for k in range(5)}]          # one insertion shown at a time
    F = "frame"
    parts: dict[str, list] = {}

    def add(group, box):
        parts.setdefault(group, []).append(box)
        return box

    # Base, pillars, head.
    add("base", g.box("base", (-24, 0, -8), (24, BASE_Y, 40), bone=F))
    for i, (x0, z0) in enumerate(((-24, -8), (8, -8), (-24, 24), (8, 24))):
        add("pillar", g.box(f"pillar{i}", (x0, BASE_Y, z0), (x0 + 16, HEAD_Y, z0 + 16), bone=F))
    add("head", g.box("head", (-24, HEAD_Y, -8), (24, TOP, 40), bone=F))
    # Window bays: a steel lip round each outer opening and a leaded glass pane against the core.
    x0, x1, z0, z1 = CORE
    bays = {"n": ((x0, -8), (x1, -7)), "s": ((x0, 39), (x1, 40)), "w": ((-24, z0), (-23, z1)), "e": ((23, z0), (24, z1))}
    for key, ((ax, az), (bx, bz)) in bays.items():
        horizontal = key in ("n", "s")
        if horizontal:
            add("lip", g.box(f"lip_{key}_l", (ax, BASE_Y, az), (ax + 1, HEAD_Y, bz), bone=F))
            add("lip", g.box(f"lip_{key}_r", (bx - 1, BASE_Y, az), (bx, HEAD_Y, bz), bone=F))
            add("lip", g.box(f"lip_{key}_t", (ax + 1, HEAD_Y - 1, az), (bx - 1, HEAD_Y, bz), faces=("north", "south", "down"), bone=F))
            add("lip", g.box(f"lip_{key}_b", (ax + 1, BASE_Y, az), (bx - 1, BASE_Y + 1, bz), faces=("north", "south", "up"), bone=F))
        else:
            add("lip", g.box(f"lip_{key}_l", (ax, BASE_Y, az), (bx, HEAD_Y, az + 1), bone=F))
            add("lip", g.box(f"lip_{key}_r", (ax, BASE_Y, bz - 1), (bx, HEAD_Y, bz), bone=F))
            add("lip", g.box(f"lip_{key}_t", (ax, HEAD_Y - 1, az + 1), (bx, HEAD_Y, bz - 1), faces=("east", "west", "down"), bone=F))
            add("lip", g.box(f"lip_{key}_b", (ax, BASE_Y, az + 1), (bx, BASE_Y + 1, bz - 1), faces=("east", "west", "up"), bone=F))
    add("pane", g.box("pane_n", (x0, BASE_Y, z0 - 0.2), (x1, HEAD_Y, z0), faces=("north",), bone=F))
    add("pane", g.box("pane_s", (x0, BASE_Y, z1), (x1, HEAD_Y, z1 + 0.2), faces=("south",), bone=F))
    add("pane", g.box("pane_w", (x0 - 0.2, BASE_Y, z0), (x0, HEAD_Y, z1), faces=("west",), bone=F))
    add("pane", g.box("pane_e", (x1, BASE_Y, z0), (x1 + 0.2, HEAD_Y, z1), faces=("east",), bone=F))
    # Core: grid plates, the fuel bundle, control-rod blades at five insertions, Cherenkov glow.
    add("grid", g.box("grid_lo", (-7, BASE_Y, 9), (7, BASE_Y + 1.5, 23), faces=("up", "north", "south", "east", "west"), bone=F))
    add("grid", g.box("grid_hi", (-7, ROD_TOP, 9), (7, ROD_TOP + 1, 23), bone=F))
    for i, x in enumerate(RODS_X):
        for j, z in enumerate(RODS_Z):
            add("fuel", g.box(f"fuel{i}{j}", (x - 0.8, BASE_Y + 1.5, z - 0.8), (x + 0.8, ROD_TOP, z + 0.8), faces=("north", "south", "east", "west"), bone=F))
    for k in range(5):
        bottom = ROD_TOP - (ROD_TOP - ROD_BOTTOM) * k / 4 - 0.5
        for b, (x, z) in enumerate(BLADES):
            add("blade", g.box(f"blade{k}_{b}", (x - 0.5, bottom, z - 0.5), (x + 0.5, ROD_TOP + 1.0, z + 0.5),
                               faces=("north", "south", "east", "west", "down"), bone=f"rods{k}"))
    glow = add("glow", g.box("glow_box", (-6.6, BASE_Y + 2, 9.4), (6.6, ROD_TOP - 1, 22.6), faces=("north", "south", "east", "west"),
                             shade=False, bone="glow"))
    # Head furniture: port hatches front and back, energy terminal, rod drives.
    for i, z in enumerate((-6.0, 26.0)):
        add("hatch", g.box(f"hatch{i}", (-6, TOP, z), (6, TOP + 1, z + 12), bone=F))
    add("terminal", g.box("terminal", (-2.5, TOP, 13.5), (2.5, TOP + 2, 18.5), bone=F))
    for i, x in enumerate((-1.6, 1.6)):
        add("insulator", g.box(f"insulator{i}", (x - 0.7, TOP + 2, 15.3), (x + 0.7, TOP + 4, 16.7), bone=F))
    for i, (x, z) in enumerate(((-6, 10), (6, 10), (-6, 22), (6, 22))):
        add("drive", g.box(f"drive{i}", (x - 1.2, TOP, z - 1.2), (x + 1.2, TOP + 3.5, z + 1.2), bone=F))
    # Control cabinet on the base front (the controller), coolant pipes down the back.
    add("cabinet", g.box("cabinet", (-7, 1.5, -9), (7, 14.5, -8), faces=("north", "east", "west", "up", "down"), bone=F))
    lamps = add("lamps", g.box("lamps", (2.5, 9, -9.2), (6, 10.5, -9), faces=("north",), shade=False, bone=F))
    # Coolant inlet (the Fluid Port's open face, back middle of the base) and a header out to both back pipes.
    add("inlet", g.box("inlet", (-6, 2, 40), (6, 14, 41.5), faces=("south", "east", "west", "up", "down"), bone=F))
    add("pipe", g.box("header_w", (-14, 6.75, 40), (-6, 9.25, 42.5), faces=("south", "up", "down"), bone=F))
    add("pipe", g.box("header_e", (6, 6.75, 40), (14, 9.25, 42.5), faces=("south", "up", "down"), bone=F))
    for i, x in enumerate((-16, 16)):
        add("pipe", g.box(f"pipe{i}", (x - 1.5, 4, 40), (x + 1.5, 60, 42.5), faces=("east", "west", "south", "up", "down"), bone=F))
        for y in (8, 32, 56):
            add("flange", g.box(f"flange{i}_{y}", (x - 2, y, 40), (x + 2, y + 1.2, 43), faces=("east", "west", "south", "up", "down"), bone=F))

    g.layout()
    tex, glow_tex = Tex(g.tex_w, g.tex_h), Tex(g.tex_w, g.tex_h)
    steel = P.STEEL

    def lead_panel(t, r, d, key=None):
        x, y, w, h = r
        t.rect(x, y, w, h, LEAD[tone(d)])
        if min(w, h) < 4:
            return
        t.outline(x, y, w, h, LEAD[1])
        t.hline(x + 1, y + 1, w - 2, LEAD[5])
        for xx in range(x + 2, x + w - 2, 8):
            for yy in range(y + 2, y + h - 2, 8):
                t.px(xx, yy, LEAD[6])                                       # bolt heads on the plates
        if w >= 32 and h >= 12 and d != "up" and d != "down":
            t.hline(x + 1, y + h // 2, w - 2, LEAD[2])                      # plate seam

    for key in ("base", "pillar", "head"):
        for b in parts[key]:
            g.paint_all(tex, b, lead_panel)
    # Hazard band along the base's top edge on all sides, trefoil placards on the sides.
    def base_paint(t, r, d):
        lead_panel(t, r, d)
        x, y, w, h = r
        if d in ("north", "south", "east", "west"):
            t.stripes(x, y, w, 2, P.HAZARD_YELLOW.base, P.HAZARD_BLACK.base, width=2)
            if d in ("east", "west"):
                cx = x + w // 2
                t.rect(cx - 3, y + 5, 6, 6, P.HAZARD_YELLOW.base)
                t.trefoil(cx, y + 8, 2.6, P.HAZARD_BLACK.base)
    g.paint_all(tex, parts["base"][0], base_paint)
    for b in parts["lip"]:
        g.paint_all(tex, b, lambda t, r, d: (t.rect(*r, steel[tone(d)]), t.outline(*r, steel[2]) if min(r[2], r[3]) >= 3 else None))

    def pane(t, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, CLEAR)
        t.outline(x, y, w, h, GLASS_TINT[3])
        for k in range(min(w, h) // 3):
            t.px(x + 2 + k, y + h - 4 - k, (*GLASS_TINT[6][:3], 255))
    for b in parts["pane"]:
        g.paint_all(tex, b, pane)
    for b in parts["grid"]:
        g.paint_all(tex, b, lambda t, r, d: (t.rect(*r, steel[tone(d) - 1]), [t.px(xx, yy, steel[1]) for yy in range(r[1], r[1] + r[3], 2)
                                                                                  for xx in range(r[0] + 1, r[0] + r[2], 3)] if d in ("up", "down") else None))
    for b in parts["fuel"]:
        g.paint_all(tex, b, lambda t, r, d: (t.rect(*r, FUEL[4] if d in ("north", "west") else FUEL[3]),
                                             [t.hline(r[0], yy, r[2], FUEL[2]) for yy in range(r[1] + 3, r[1] + r[3], 6)]))
    for b in parts["blade"]:
        g.paint_all(tex, b, lambda t, r, d: (t.rect(*r, P.BLACK_PAINT[2]), t.vline(r[0], r[1], r[3], P.BLACK_PAINT[4]) if r[2] > 0 else None))

    def cherenkov(t, r, d):
        """A haze of Cherenkov blue: scattered lit texels, clear between them, brighter toward the middle."""
        x, y, w, h = r
        t.rect(x, y, w, h, CLEAR)
        for yy in range(h):
            for xx in range(w):
                k = (xx * 7 + yy * 5) % 9
                if k == 0:
                    t.px(x + xx, y + yy, P.CHERENKOV[5])
                elif k == 4 and 2 < yy < h - 3:
                    t.px(x + xx, y + yy, P.CHERENKOV[4])
    g.paint_all(tex, glow, cherenkov)
    g.paint_all(glow_tex, glow, cherenkov)

    def hatch(t, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, steel[tone(d)])
        if d == "up":
            t.stripes(x, y, w, h, P.HAZARD_YELLOW.base, P.HAZARD_BLACK.base, width=2)
            t.rect(x + 3, y + 3, w - 6, h - 6, P.BLACK_PAINT.deep)
            t.outline(x + 3, y + 3, w - 6, h - 6, P.GUNMETAL[2])
    for b in parts["hatch"]:
        g.paint_all(tex, b, hatch)

    def inlet(t, r, d):
        """The Fluid Port's face: a blue-and-black hatched ring round a dark bore, like the item hatches."""
        x, y, w, h = r
        t.rect(x, y, w, h, steel[tone(d)])
        if d == "south":
            t.stripes(x, y, w, h, FLUID_BLUE[4], P.HAZARD_BLACK.base, width=2)
            t.rect(x + 3, y + 3, w - 6, h - 6, P.BLACK_PAINT.deep)
            t.outline(x + 3, y + 3, w - 6, h - 6, P.GUNMETAL[2])
    g.paint_all(tex, parts["inlet"][0], inlet)
    g.paint_all(tex, parts["terminal"][0], lambda t, r, d: (t.rect(*r, P.WARNING_RED[tone(d) + 1]), t.outline(*r, P.WARNING_RED[2]) if d == "up" else None))
    for b in parts["insulator"]:
        g.paint_all(tex, b, lambda t, r, d: (t.rect(*r, P.WHITE_CERAMIC[tone(d) + 1]),
                                             [t.hline(r[0], yy, r[2], P.WHITE_CERAMIC[2]) for yy in range(r[1] + 1, r[1] + r[3], 2)] if d not in ("up", "down") else None))
    for b in parts["drive"]:
        g.paint_all(tex, b, lambda t, r, d: (t.rect(*r, P.GUNMETAL[tone(d) + 1]), t.hline(r[0], r[1], r[2], P.GUNMETAL[5]) if d != "up" else
                                             t.px(r[0] + r[2] // 2, r[1] + r[3] // 2, P.INDICATOR_GREEN.light)))

    def cabinet(t, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, P.OLIVE[tone(d) + 1])
        if d != "north":
            return
        t.outline(x, y, w, h, P.OLIVE[1])
        t.rect(x + 2, y + 2, 6, 4, P.BLACK_PAINT.deep)                      # CRT
        for xx in range(x + 3, x + 7):
            t.px(xx, y + 3 + (xx % 2), P.INDICATOR_GREEN[3])
        t.rect(x + 2, y + 8, 4, 3, P.WHITE_CERAMIC[5])                     # rod-position dial
        t.px(x + 3, y + 9, P.BLACK_PAINT[1])
        t.stripes(x + 1, y + h - 2, w - 2, 1, P.HAZARD_YELLOW.base, P.HAZARD_BLACK.base, width=2)
    g.paint_all(tex, parts["cabinet"][0], cabinet)

    def lamps_paint(t, r, d):
        x, y, w, h = r
        for i, c in enumerate((P.INDICATOR_GREEN.light, P.NUCLEAR_AMBER.light, P.INDICATOR_RED.light)):
            t.px(x + i, y, c)
    g.paint_all(tex, lamps, lambda t, r, d: (t.rect(*r, P.BLACK_PAINT[2]), lamps_paint(t, r, d)))
    g.paint_all(glow_tex, lamps, lamps_paint)
    for b in parts["pipe"]:
        g.paint_all(tex, b, lambda t, r, d: (t.rect(*r, P.CHERENKOV[tone(d) - 1]), t.vline(r[0], r[1], r[3], P.CHERENKOV[3]) if r[2] > 2 else None))
    for b in parts["flange"]:
        g.paint_all(tex, b, lambda t, r, d: t.rect(*r, steel[tone(d) + 1]))
    return g, tex, glow_tex


def animations() -> list[Animation]:
    """While running, the Cherenkov haze breathes gently (the rods and everything else stay still)."""
    w = Animation("working", 2.0, loop=True)
    w.scale("glow", {0: (1, 1, 1), 1: (1.04, 1.0, 1.04), 2: (1, 1, 1)}, easing="easeInOutSine")
    return [w]


if __name__ == "__main__":
    model, texture, glowmask = build()
    for p in model.save(texture, glowmask):
        print(p)
    print(save_animations(NAME, animations()))
