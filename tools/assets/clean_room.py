"""Clean Room, formed (GeckoLib model + animation), DESIGN.md §8.1. A 3x2x2 structure drawn from its controller, which
sits at the front-left (north-west) bottom; the model spans x -8..40, z -8..24, y 0..32 in the north-facing frame
(model origin = the controller block's bottom centre).

Look: a sealed module of white sandwich panels on a stainless kick plate. Across the front of its upper storey a band
of windows onto the lithography bay, lit yellow (photoresist is safe only under yellow light): a coat-and-develop track
with its robot arm, a scanner with its wafer stage stepping, a wet bench under its fume hood; the ceiling's filter
grilles glow. Below the windows, the control panel (a green CRT) on the controller's cell and an air-shower door beside
it, its lamp green. Fan filter units sit on the roof; an acid scrubber's tower stands in the back-east corner, a duct
from the bay into it and its stack above. The ports wear striped housings on their open faces: red for power (front and
east of the front-east cell), blue for fluids (back and west of the back-west cell), yellow for items (back of the
back-middle cell).
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))

import numpy as np  # noqa: E402

from gb import machine_kit as K  # noqa: E402
from gb import materials as M  # noqa: E402
from gb import palette as P  # noqa: E402
from gb.geo import Animation, GeoModel, save_animations  # noqa: E402
from gb.palette import fixed  # noqa: E402
from gb.tex import Tex  # noqa: E402

NAME = "clean_room"
# White surfaces seen under the bay's yellow light.
LIT = fixed("bay_lit", "#4a3c14", "#766222", "#a08c3a", "#c4b056", "#dccb74", "#eedf96", "#fcf3c6")
FLUID_BLUE = fixed("port_fluid_blue", "#0c1a3a", "#12285a", "#1a3a7e", "#2452a6", "#3a6ec8", "#6a98e0", "#a8c8f4")
CLEAR = (0, 0, 0, 0)
X0, X1, Z0, Z1 = -7.6, 39.6, -7.6, 23.6     # the shell's outer faces
Y0, Y1 = 1.0, 29.0                          # kick plate to roof
BAY_Y0, BAY_Y1, BAY_Z = 16.5, 27.5, -3.0    # the window bay: its floor, ceiling and back wall
BAY_X0, BAY_X1 = -6.0, 38.0
MULLIONS = (8.0, 24.0)
SCRUB_X, SCRUB_Z = 32.0, 16.0               # the scrubber's centre, in the back-east cell
SCRUB_BANDS = [(-6, -4.5, 3.5), (-4.5, -3, 5), (-3, 3, 6), (3, 4.5, 5), (4.5, 6, 3.5)]
FFUS = [(0, 0), (16, 0), (32, 0), (0, 16), (16, 16)]


def build() -> tuple[GeoModel, Tex, Tex]:
    g = GeoModel(NAME, tex_size=(256, 256))
    g.bone("frame")
    g.bone("stage", pivot=(16, 18.9, -5.7), parent="frame")
    g.bone("arm", pivot=(0.7, 20.5, -4.4), parent="frame")
    F = "frame"
    parts: dict[str, list] = {}

    def add(group, box):
        parts.setdefault(group, []).append(box)
        return box

    add("base", g.box("base", (-8, 0, -8), (40, Y0, 24), bone=F))
    # The shell, in pieces round the window bay and clear of the scrubber's corner.
    add("shell", g.box("shell_mid", (X0, Y0, BAY_Z), (X1, Y1, 8), faces=("north", "south", "east", "west", "up"), bone=F))
    add("shell", g.box("shell_rear", (X0, Y0, 8), (24, Y1, Z1), faces=("south", "east", "west", "up"), bone=F))
    add("shell", g.box("shell_low", (X0, Y0, Z0), (X1, BAY_Y0, BAY_Z), faces=("north", "east", "west", "up"), bone=F))
    add("shell", g.box("shell_head", (X0, BAY_Y1, Z0), (X1, Y1, BAY_Z), faces=("north", "east", "west", "up", "down"), bone=F))
    add("shell", g.box("shell_w", (X0, BAY_Y0, Z0), (BAY_X0, BAY_Y1, BAY_Z), faces=("north", "east", "west"), bone=F))
    add("shell", g.box("shell_e", (BAY_X1, BAY_Y0, Z0), (X1, BAY_Y1, BAY_Z), faces=("north", "east", "west"), bone=F))
    # Window frames and glass.
    for i, m in enumerate(MULLIONS):
        add("mullion", g.box(f"mullion{i}", (m - 0.7, BAY_Y0, Z0), (m + 0.7, BAY_Y1, Z0 + 1.2), faces=("north", "south", "east", "west"), bone=F))
    spans = [(BAY_X0, MULLIONS[0] - 0.7), (MULLIONS[0] + 0.7, MULLIONS[1] - 0.7), (MULLIONS[1] + 0.7, BAY_X1)]
    for i, (a, b) in enumerate(spans):
        add("glass", g.box(f"glass{i}", (a, BAY_Y0, Z0 + 0.3), (b, BAY_Y1, Z0 + 0.35), faces=("north",), bone=F))
    # The bay's tools: a coat-and-develop track with a robot arm, a scanner with its wafer stage, a wet bench and hood.
    add("track", g.box("track", (-5.5, BAY_Y0, -5.4), (6.5, 20.5, BAY_Z), faces=("north", "east", "west", "up"), bone=F))
    add("arm", g.box("arm_post", (0.2, 20.5, -4.9), (1.2, 21.7, -3.9), faces=("north", "south", "east", "west"), bone="arm"))
    add("arm", g.box("arm_link", (0.7, 21.7, -4.8), (3.1, 22.3, -4.0), faces=("north", "south", "east", "west", "up", "down"), bone="arm"))
    add("scanner", g.box("scanner", (9.5, BAY_Y0, -5.6), (22.5, 25.5, BAY_Z), faces=("north", "east", "west", "up"), bone=F))
    add("scanner_top", g.box("lens_column", (14.0, 25.5, -5.0), (18.0, 27.0, -3.5), faces=("north", "east", "west", "up"), bone=F))
    add("stage", g.box("stage", (14.6, 18.5, -5.85), (17.4, 19.3, -5.75), faces=("north",), bone="stage"))
    add("bench", g.box("bench", (25.5, BAY_Y0, -5.6), (37.5, 21.0, BAY_Z), faces=("north", "east", "west", "up"), bone=F))
    add("hood", g.box("hood", (25.5, 24.6, -5.8), (37.5, 26.6, BAY_Z), faces=("north", "east", "west", "up", "down"), bone=F))
    add("hood_duct", g.box("hood_duct", (30.5, 26.6, -4.8), (32.5, BAY_Y1, -3.6), faces=("north", "east", "west"), bone=F))
    # Below the windows: the control panel on the controller's cell, the air-shower door, its lamp.
    add("panel", g.box("panel", (-6.0, 4.0, Z0 - 0.4), (6.0, 13.0, Z0), faces=("north", "east", "west", "up", "down"), bone=F))
    add("door", g.box("door", (10.5, Y0, Z0 - 0.3), (21.5, 14.5, Z0), faces=("north", "east", "west", "up"), bone=F))
    add("lamp", g.box("lamp", (15.4, 14.9, Z0 - 0.5), (16.6, 15.7, Z0), faces=("north", "east", "west", "up", "down"), bone=F))
    # The ports' housings on their open faces.
    add("ehousing_n", g.box("ehousing_n", (25.5, 2.0, Z0 - 0.35), (38.5, 13.0, Z0), faces=("north", "east", "west", "up", "down"), bone=F))
    add("ehousing_e", g.box("ehousing_e", (X1, 2.0, -6.5), (X1 + 0.35, 13.0, 6.5), faces=("east", "north", "south", "up", "down"), bone=F))
    add("fhousing_s", g.box("fhousing_s", (-6.5, 2.0, Z1), (6.5, 13.0, Z1 + 0.35), faces=("south", "east", "west", "up", "down"), bone=F))
    add("fhousing_w", g.box("fhousing_w", (X0 - 0.35, 2.0, 9.5), (X0, 13.0, 22.5), faces=("west", "north", "south", "up", "down"), bone=F))
    add("ihousing_s", g.box("ihousing_s", (9.5, 2.0, Z1), (22.5, 13.0, Z1 + 0.35), faces=("south", "east", "west", "up", "down"), bone=F))
    # The acid scrubber: a round tower, its caps and stack, a duct from the bay into it.
    for i, (z0, z1, hw) in enumerate(SCRUB_BANDS):
        add("scrubber", g.box(f"scrubber{i}", (SCRUB_X - hw, Y0, SCRUB_Z + z0), (SCRUB_X + hw, 24.0, SCRUB_Z + z1),
                              faces=("north", "south", "east", "west"), bone=F))
    add("scrubber_cap", g.box("scrubber_cap0", (SCRUB_X - 4.5, 24.0, SCRUB_Z - 4.5), (SCRUB_X + 4.5, 25.5, SCRUB_Z + 4.5), bone=F))
    add("scrubber_cap", g.box("scrubber_cap1", (SCRUB_X - 3.0, 25.5, SCRUB_Z - 3.0), (SCRUB_X + 3.0, 26.5, SCRUB_Z + 3.0),
                              faces=("north", "south", "east", "west", "up"), bone=F))
    add("stack", g.box("stack", (SCRUB_X - 1.4, 26.5, SCRUB_Z - 1.4), (SCRUB_X + 1.4, 31.0, SCRUB_Z + 1.4), faces=("north", "south", "east", "west"), bone=F))
    add("stack_cap", g.box("stack_cap", (SCRUB_X - 2.2, 31.0, SCRUB_Z - 2.2), (SCRUB_X + 2.2, 31.6, SCRUB_Z + 2.2), bone=F))
    add("duct", g.box("duct", (30.8, 20.0, 8.0), (33.2, 22.4, 10.2), faces=("east", "west", "up", "down"), bone=F))
    # Fan filter units on the roof.
    for i, (cx, cz) in enumerate(FFUS):
        add("ffu", g.box(f"ffu{i}", (cx - 5.5, Y1, cz - 5.5), (cx + 5.5, 30.4, cz + 5.5), faces=("north", "south", "east", "west", "up"), bone=F))
        add("ffu_motor", g.box(f"ffu_motor{i}", (cx - 1.6, 30.4, cz - 1.6), (cx + 1.6, 31.6, cz + 1.6), faces=("north", "south", "east", "west", "up"), bone=F))

    g.layout()
    tex, glow = Tex(g.tex_w, g.tex_h), Tex(g.tex_w, g.tex_h)
    paint(g, tex, glow, parts)
    return g, tex, glow


def _lit(t: Tex, r, tone: int, glow: Tex | None = None) -> None:
    """A surface in the bay, white under yellow light; lit in the glowmask too, so the bay shines at night."""
    x, y, w, h = r
    t.rect(x, y, w, h, LIT[tone])
    if glow is not None:
        glow.rect(x, y, w, h, LIT[tone])


def paint(g: GeoModel, tex: Tex, glow: Tex, parts: dict) -> None:
    """The room's surfaces, in model space (tools/gb/machine_kit.py): white sandwich panels in courses with aluminium
    trims on a stainless kick plate; the bay's surfaces and tools white under yellow light, glowing; clear glass with a
    streak of reflection; a gunmetal control panel with a green CRT; a stainless air-shower door; dark steel port housings
    striped round their sockets; a pale grey glass-fibre scrubber with its corrosive label; galvanised duct and stack;
    steel filter units with dark motors."""
    k = K.Kit(g, parts, seed=351)
    names = lambda key: [b.name for b in parts.get(key, [])]   # noqa: E731
    white = M.white_paint(tone=0.8, wear=0.04)
    k.paint(tex, "base", k.BRIGHT)
    k.paint(tex, "shell", white)

    def panels(f, img):
        if not f.vertical:
            return
        M.seams(f, img, every=8, axis="u", offset=0, light=False)          # the joints between panels
        trim = (f.border["top"] < 0.6) | (f.border["bottom"] < 0.6) | (f.border["left"] < 0.6) | (f.border["right"] < 0.6)
        M.paste(img, k.on(f, k.STEEL), trim)
    k.overlay(tex, "shell", panels)
    k.paint(tex, "mullion", k.STEEL)

    # The bay: its back wall, floor, ceiling and ends, white under the yellow light.
    def back_wall(t, r, d):
        if d != "north":
            return
        x, y, w, h = r
        _lit(t, r, 4, glow)
        for xx in range(x, x + w, 8):                                    # wall panels' joints
            t.vline(xx, y, h, LIT[3])
            glow.vline(xx, y, h, LIT[3])
    g.paint_all(tex, parts["shell"][0], back_wall)                       # shell_mid: only its bay shows from the front

    def floor(t, r, d):
        if d != "up":
            return
        x, y, w, h = r
        _lit(t, r, 3, glow)
        for yy in range(y, y + h):
            for xx in range(x, x + w):
                if (xx - x) % 4 == 0 or (yy - y) % 4 == 0:               # the raised floor's tiles
                    t.px(xx, yy, LIT[2])
                    glow.px(xx, yy, LIT[2])
    g.paint_all(tex, parts["shell"][2], floor)                           # shell_low

    def ceiling(t, r, d):
        if d != "down":
            return
        x, y, w, h = r
        _lit(t, r, 3, glow)
        for cx in range(x + 2, x + w - 3, 6):                            # the filter grilles, lit
            t.rect(cx, y + 1, 4, max(1, h - 2), LIT[6])
            glow.rect(cx, y + 1, 4, max(1, h - 2), LIT[6])
    g.paint_all(tex, parts["shell"][3], ceiling)                         # shell_head
    g.paint_all(tex, parts["shell"][4], lambda t, r, d: _lit(t, r, 3, glow) if d == "east" else None)    # shell_w
    g.paint_all(tex, parts["shell"][5], lambda t, r, d: _lit(t, r, 3, glow) if d == "west" else None)    # shell_e

    def glass(t, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, CLEAR)
        for i in range(min(w, h)):                                       # a streak of reflection across each pane
            xx, yy = x + 2 + i, y + h - 3 - i
            if x <= xx < x + w and y <= yy < y + h and i % 5 < 2:
                t.px(xx, yy, (236, 242, 246, 255))
    for b in names("glass"):
        g.paint_all(tex, b, glass)

    # The tools, white under yellow: lit tops, sides a shade down.
    def tool(t, r, d):
        _lit(t, r, 5 if d == "up" else 4 if d == "north" else 3, glow)
    for key in ("track", "arm", "scanner", "scanner_top", "bench", "hood", "hood_duct"):
        for b in names(key):
            g.paint_all(tex, b, tool)

    def track_top(t, r, d):
        x, y, w, h = r
        if d == "up":
            for cx in (x + 2, x + w - 5):
                t.rect(cx, y + h // 2 - 1, 3, 2, LIT[1])                 # the spin-coater bowls
                glow.rect(cx, y + h // 2 - 1, 3, 2, LIT[1])
        elif d == "north":
            t.hline(x, y + 1, w, LIT[2])                                 # the robot's rail
            glow.hline(x, y + 1, w, LIT[2])
    g.paint_all(tex, names("track")[0], track_top)

    def scanner_face(t, r, d):
        x, y, w, h = r
        if d == "north":
            t.rect(x + 1, y + h - 4, w - 2, 2, LIT[1])                  # the wafer stage's slot
            glow.rect(x + 1, y + h - 4, w - 2, 2, LIT[1])
            t.hline(x, y + 2, w, LIT[2])                                 # an accent band
            glow.hline(x, y + 2, w, LIT[2])
            t.rect(x + 2, y + 4, 3, 2, (60, 140, 90, 255))               # a status screen
            glow.rect(x + 2, y + 4, 3, 2, (60, 140, 90, 255))
    g.paint_all(tex, names("scanner")[0], scanner_face)
    g.paint_all(tex, names("stage")[0], lambda t, r, d: _lit(t, r, 6, glow))

    def bench_top(t, r, d):
        x, y, w, h = r
        if d == "up":
            t.rect(x + 1, y + 1, w - 2, max(1, h - 2), (120, 200, 190, 255))   # the etch bath
            glow.rect(x + 1, y + 1, w - 2, max(1, h - 2), (120, 200, 190, 255))
    g.paint_all(tex, names("bench")[0], bench_top)

    # The control panel: gunmetal, a green CRT and its lamps.
    k.paint(tex, "panel", k.GUN)

    def panel(t, r, d):
        x, y, w, h = r
        if d == "north":
            K.crt(t, x + 2, y + 1, 6, 4)
            K.crt(glow, x + 2, y + 1, 6, 4)                              # the screen glows
            K.lamps(t, x + w - 4, y + 2)
            K.dial(t, x + 2, y + h - 3)
    g.paint_all(tex, names("panel")[0], panel)
    k.paint(tex, "door", k.BRIGHT)

    def door(t, r, d):
        x, y, w, h = r
        if d == "north":
            t.rect(x + w // 2 - 1, y + 2, 2, 5, P.BLACK_PAINT[1])        # its narrow window
            t.vline(x + w // 2, y + 2, 5, P.BLACK_PAINT[2])
            t.vline(x + w - 2, y + h // 2, 3, P.STEEL[5])                 # handle
    g.paint_all(tex, names("door")[0], door)
    g.paint_all(tex, names("lamp")[0], lambda t, r, d: (t.rect(*r, P.INDICATOR_GREEN[4]), glow.rect(*r, P.INDICATOR_GREEN[4])))

    # The ports' housings: dark steel, striped round a socket on their open faces.
    blue = M.ramp(*[f"#{c[0]:02x}{c[1]:02x}{c[2]:02x}" for c in FLUID_BLUE.shades])
    for key, face, ramp in (("ehousing_n", "north", M.RED_PAINT_RAMP), ("ehousing_e", "east", M.RED_PAINT_RAMP),
                            ("fhousing_s", "south", blue), ("fhousing_w", "west", blue), ("ihousing_s", "south", M.YELLOW_PAINT_RAMP)):
        k.paint(tex, key, k.DARK)
        k.socket(tex, key, face, ramp)

    # The scrubber: pale grey glass-fibre tower in courses, its corrosive label; galvanised duct and stack.
    frp = M.white_paint(tone=0.62, wear=0.1)
    k.paint(tex, ["scrubber", "scrubber_cap"], frp)
    k.overlay(tex, "scrubber", lambda f, img: M.seams(f, img, every=6, axis="v", offset=3, light=False) if f.vertical else None)

    def label(t, r, d):
        x, y, w, h = r
        if d in ("north", "west") and w >= 8:
            cx, cy = x + w // 2, y + 6
            for dy in range(-2, 3):
                span = 2 - abs(dy)
                t.hline(cx - span, cy + dy, 2 * span + 1, P.WHITE_CERAMIC[6])
            for px, py in ((cx, cy - 2), (cx - 2, cy), (cx + 2, cy), (cx, cy + 2)):
                t.px(px, py, P.WARNING_RED[3])                            # the red-bordered diamond
            t.px(cx, cy, P.BLACK_PAINT[1])
    g.paint_all(tex, parts["scrubber"][2], label)
    k.paint(tex, ["duct", "stack", "stack_cap"], k.GALV)
    k.paint(tex, "ffu", k.STEEL)
    k.overlay(tex, "ffu", lambda f, img: M.darken(img, (np.floor(f.plane_uv()[0]) % 2 == 0) & (K.ring(f) >= 1), 0.75), only=("up",))
    k.paint(tex, "ffu_motor", k.GUN)


def spawner_plan(t: Tex) -> None:
    """The Structure Spawner's blueprint: the room's 3x2 plan, its cells marked, the window band along the front in
    yellow and the scrubber's round corner."""
    import multiblock_tools as MT  # noqa: PLC0415
    line = MT.BLUEPRINT[6]
    t.outline(3, 4, 10, 7, line)
    for x in (6, 9):
        t.vline(x, 5, 5, MT.BLUEPRINT[5])
    t.hline(4, 7, 8, MT.BLUEPRINT[5])
    t.hline(4, 9, 8, P.HAZARD_YELLOW[4])                                 # the windows, front (bottom of the plan)
    t.rect(10, 5, 2, 2, (210, 214, 218, 255))                            # the scrubber
    t.px(4, 5, P.INDICATOR_GREEN[4])                                     # the controller's corner


def animations() -> list[Animation]:
    working = Animation("working", 2.0, loop=True)
    # The scanner's wafer stage steps across under the lens; the track's robot arm swings between its stations.
    working.move("stage", {0: (0, 0, 0), 0.5: (2.0, 0, 0), 1.0: (0, 0, 0), 1.5: (-2.0, 0, 0), 2.0: (0, 0, 0)})
    working.rotate("arm", {0: (0, 30, 0), 1.0: (0, -30, 0), 2.0: (0, 30, 0)})
    return [working]


if __name__ == "__main__":
    model, texture, glowmask = build()
    for p in model.save(texture, glowmask):
        print(p)
    print(save_animations(NAME, animations()))
    import multiblock_tools as MT  # noqa: E402
    MT.structure_spawner("clean_room_spawner", spawner_plan)
