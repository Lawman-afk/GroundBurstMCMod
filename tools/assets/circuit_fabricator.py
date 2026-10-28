"""Circuit Fabricator (GeckoLib block model + animation), DESIGN.md §8. One block.

Look: on the shared hazard-striped plinth, an olive equipment cabinet (vent, readout and lamp on the front) carries
a glass-walled clean box. Inside, a green circuit board lies on the work bed and a chip feeder tray stands at the
east end. A gantry robot rides two rails under the glass roof: the bridge (bone "gantry") slides east-west, its
carriage ("head") slides north-south and the quill ("arm") drops to pick a chip ("chip", shown only while it is
carried) from the tray and set it on the board. Glass panes are mostly transparent with a few glints.
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

NAME = "circuit_fabricator"
PCB = fixed("pcb_green", "#06200e", "#0b3418", "#124a22", "#1a622e", "#257c3c", "#3c9a52", "#6cc07e")
SOLDER = fixed("solder", "#5a5a52", "#7a7a70", "#9a9a90", "#b8b8ae", "#d0d0c6", "#e4e4dc", "#f6f6f0")
CLEAR = (0, 0, 0, 0)
CAB_TOP = 7.0          # cabinet top = work bed
ROOF = 14.5            # underside of the glass roof frame
RAIL_Y = 13.0          # gantry rails
TRAY_X = 4.6           # chip tray centre (x)
BOARD_X = -1.6         # where chips are set down


def tone(d: str) -> int:
    return {"up": 4, "down": 2}.get(d, 3)


def build() -> tuple[GeoModel, Tex, Tex]:
    g = GeoModel(NAME, tex_size=(128, 128))
    g.bone("frame")
    g.bone("gantry", pivot=(0, RAIL_Y, 0), parent="frame")
    g.bone("head", pivot=(0, RAIL_Y, 0), parent="gantry")
    g.bone("arm", pivot=(0, RAIL_Y, 0), parent="head")
    g.bone("chip", pivot=(0, 9, 0), parent="arm")
    F = "frame"
    parts: dict[str, list] = {}

    def add(group, box):
        parts.setdefault(group, []).append(box)
        return box

    add("plinth", g.box("plinth", (-8, 0, -8), (8, 2, 8), bone=F))
    add("cabinet", g.box("cabinet", (-7, 2, -7), (7, CAB_TOP, 7), bone=F))
    # Clean box: four corner posts, a roof frame, glass walls and a glass roof.
    for i, (x, z) in enumerate(((-7, -7), (6, -7), (-7, 6), (6, 6))):
        add("post", g.box(f"post{i}", (x, CAB_TOP, z), (x + 1, ROOF, z + 1), faces=("north", "south", "east", "west"), bone=F))
    add("rim", g.box("rim_n", (-7, ROOF, -7), (7, ROOF + 1, -6), bone=F))
    add("rim", g.box("rim_s", (-7, ROOF, 6), (7, ROOF + 1, 7), bone=F))
    add("rim", g.box("rim_w", (-7, ROOF, -6), (-6, ROOF + 1, 6), bone=F))
    add("rim", g.box("rim_e", (6, ROOF, -6), (7, ROOF + 1, 6), bone=F))
    add("glass", g.box("glass_n", (-6, CAB_TOP, -6.9), (6, ROOF, -6.7), faces=("north",), bone=F))
    add("glass", g.box("glass_s", (-6, CAB_TOP, 6.7), (6, ROOF, 6.9), faces=("south",), bone=F))
    add("glass", g.box("glass_w", (-6.9, CAB_TOP, -6), (-6.7, ROOF, 6), faces=("west",), bone=F))
    add("glass", g.box("glass_e", (6.7, CAB_TOP, -6), (6.9, ROOF, 6), faces=("east",), bone=F))
    add("glass", g.box("glass_top", (-6, ROOF + 0.6, -6), (6, ROOF + 0.8, 6), faces=("up",), bone=F))
    # Rails (east-west) the gantry rides, hung under the roof frame on the north and south sides.
    add("rail", g.box("rail_n", (-6, RAIL_Y, -5.6), (6, RAIL_Y + 0.8, -4.8), faces=("north", "south", "up", "down"), bone=F))
    add("rail", g.box("rail_s", (-6, RAIL_Y, 4.8), (6, RAIL_Y + 0.8, 5.6), faces=("north", "south", "up", "down"), bone=F))
    # Work bed: the circuit board and its fiducial clamps; the chip tray at the east end.
    add("board", g.box("board", (-5.4, CAB_TOP, -3.6), (2.2, CAB_TOP + 0.4, 3.6), faces=("north", "south", "east", "west", "up"), bone=F))
    for i, z in enumerate((-4.2, 3.6)):
        add("clamp", g.box(f"clamp{i}", (-5.0, CAB_TOP, z), (-3.4, CAB_TOP + 0.8, z + 0.6), faces=("north", "south", "east", "west", "up"), bone=F))
    add("tray", g.box("tray", (3.4, CAB_TOP, -2.6), (5.8, CAB_TOP + 1.0, 2.6), faces=("north", "south", "east", "west", "up"), bone=F))
    # Placed chips already on the board (painted on its top) plus a raised one beside the target spot.
    add("placed", g.box("placed0", (-4.6, CAB_TOP + 0.4, -2.8), (-3.2, CAB_TOP + 0.8, -1.4), faces=("north", "south", "east", "west", "up"), bone=F))
    add("placed", g.box("placed1", (-0.4, CAB_TOP + 0.4, 1.0), (1.2, CAB_TOP + 0.8, 2.6), faces=("north", "south", "east", "west", "up"), bone=F))
    # Gantry: bridge spanning the rails, carriage, quill, gripper and the carried chip.
    add("bridge", g.box("bridge", (-0.7, RAIL_Y - 0.6, -5.2), (0.7, RAIL_Y, 5.2), bone="gantry"))
    for i, z in enumerate((-5.6, 4.8)):
        add("truck", g.box(f"truck{i}", (-1.0, RAIL_Y - 0.9, z), (1.0, RAIL_Y - 0.6, z + 0.8), bone="gantry"))
    add("carriage", g.box("carriage", (-1.1, RAIL_Y - 1.7, -1.0), (1.1, RAIL_Y - 0.6, 1.0), bone="head"))
    add("quill", g.box("quill", (-0.35, CAB_TOP + 2.0, -0.35), (0.35, RAIL_Y - 0.2, 0.35), faces=("north", "south", "east", "west"), bone="arm"))
    add("gripper", g.box("gripper", (-0.7, CAB_TOP + 1.4, -0.7), (0.7, CAB_TOP + 2.0, 0.7), bone="arm"))
    add("chip", g.box("chip_box", (-0.6, CAB_TOP + 1.0, -0.6), (0.6, CAB_TOP + 1.4, 0.6), faces=("north", "south", "east", "west", "down"), bone="chip"))
    # Cable trunk from the cabinet up the back post to the gantry rail.
    add("trunk", g.box("trunk", (-5.6, CAB_TOP, 5.9), (-4.8, RAIL_Y, 6.6), faces=("north", "east", "west", "up"), bone=F))

    g.layout()
    tex, glow = Tex(g.tex_w, g.tex_h), Tex(g.tex_w, g.tex_h)
    paint(g, tex, glow, parts)
    return g, tex, glow


def paint(g: GeoModel, tex: Tex, glow: Tex, parts: dict) -> None:
    """The fabricator's surfaces, in model space (tools/gb/machine_kit.py; the machines' remaster): an olive cabinet with
    louvred vents and a readout, a pale anti-static mat on top; a glass clean box on steel posts and rim; the green
    board with its traces; a yellow gantry bridge on gunmetal trucks over steel rails; black chips; a rubber trunk."""
    k = K.Kit(g, parts, seed=309)
    k.plinth(tex)
    k.paint(tex, "cabinet", k.OLIVE)
    mat = M.Material(ramp=M.CONCRETE_RAMP, tone=0.7, mottle=0.02, grain=0.03, dither=0.5, edge_light=0.1, edge_dark=0.08, ao=0.4,
                     grime=0.0, dust=0.02)
    k.paint(tex, "cabinet", mat, only=("up",))

    def mat_dots(f, img):
        a, b = f.plane_uv()
        M.darken(img, ((np.floor(a) + np.floor(b)) % 3) == 0, 0.9)
    k.overlay(tex, "cabinet", mat_dots, only=("up",))

    def louvres(f, img):
        a, b = f.plane_uv()
        width = f.shape[1]
        inside = (f.border["top"] >= 1) & (f.border["bottom"] >= 1)
        if f.d == "north":
            inside &= f.border["right"] >= 1
            inside &= f.border["right"] < 6
        else:
            inside &= (f.border["left"] >= 3) & (f.border["right"] >= 3)
        M.darken(img, inside & ((np.floor(b) % 2) == 0), 0.4)
        M.lighten(img, inside & ((np.floor(b) % 2) == 1), 1.12, 5)
    k.overlay(tex, "cabinet", louvres, only=("north", "east", "west"))
    k.paint(tex, ["post", "rim", "rail", "quill"], k.STEEL)

    def glass(t, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, CLEAR)
        t.hline(x, y, w, (200, 230, 240, 255))                               # top edge catch-light
        for i in range(min(w, h) // 3):                                      # diagonal glints
            t.px(x + 1 + i, y + 2 + i, (210, 236, 246, 255))
        t.px(x + w - 2, y + h - 3, (190, 220, 232, 255))
    for b in parts["glass"]:
        g.paint_all(tex, b, glass)

    def board(t, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, PCB[3] if d == "up" else PCB[2])
        if d != "up":
            return
        for yy in range(y + 2, y + h - 1, 3):                              # copper traces and pads
            t.hline(x + 2, yy, w - 4, PCB[5])
        for xx in range(x + 3, x + w - 2, 4):
            t.vline(xx, y + 1, h - 2, PCB[4])
        for yy in range(y + 1, y + h - 1, 2):
            t.px(x, yy, P.BRASS[5])                                         # a gold edge connector
        t.rect(x + w - 4, y + h // 2 - 1, 2, 2, SOLDER[5])                 # the empty pad the next chip goes on
        t.outline(x, y, w, h, PCB[1])
    g.paint_all(tex, parts["board"][0], board)
    k.paint(tex, ["clamp", "bridge"], k.YELLOW)
    k.paint(tex, ["tray", "truck", "carriage"], k.GUN)

    def tray(t, r, d):
        x, y, w, h = r
        if d == "up":
            for yy in range(y + 1, y + h - 1, 2):                          # rows of chips waiting in the tray
                t.hline(x + 1, yy, w - 2, P.BLACK_PAINT[2])
                t.px(x + 1, yy, SOLDER[4])
        elif d == "north":
            t.hline(x, y, w, P.HAZARD_YELLOW.base)
    g.paint_all(tex, parts["tray"][0], tray)

    def chip(t, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, P.BLACK_PAINT[2] if d in ("up", "down") else SOLDER[4])
        if d == "up":
            t.px(x, y, P.BLACK_PAINT[5])                                    # pin-1 dot
    for b in parts["placed"] + parts["chip"]:
        g.paint_all(tex, b, chip)
    k.paint(tex, ["gripper", "trunk"], k.RUBBER)
    k.ribs(tex, "trunk", every=2, k=0.75)

    def readout(t, r, d):
        x, y, w, h = r
        if d == "north":
            t.rect(x + 2, y + 1, 5, 2, P.BLACK_PAINT.base)
            t.hline(x + 3, y + 1, 3, P.INDICATOR_GREEN[3])
    g.paint_all(tex, parts["cabinet"][0], readout)
    g.paint_all(tex, parts["carriage"][0], lambda t, r, d: t.px(r[0] + r[2] - 1, r[1], P.INDICATOR_GREEN.light) if d == "north" else None)
    g.paint_all(glow, parts["cabinet"][0], lambda t, r, d: t.hline(r[0] + 3, r[1] + 1, 3, P.INDICATOR_GREEN[4]) if d == "north" else None)
    g.paint_all(glow, parts["carriage"][0], lambda t, r, d: t.px(r[0] + r[2] - 1, r[1], P.INDICATOR_GREEN.light) if d == "north" else None)


def animations() -> list[Animation]:
    """2 s pick-and-place cycle: over the tray, drop, lift with a chip, travel to the board, drop, lift, return."""
    w = Animation("working", 2.0, loop=True)
    tray, board = TRAY_X, BOARD_X
    w.move("gantry", {0: (tray, 0, 0), 0.5: (tray, 0, 0), 0.95: (board, 0, 0), 1.45: (board, 0, 0), 1.9: (tray, 0, 0), 2.0: (tray, 0, 0)},
           easing="easeInOutSine")
    w.move("head", {0: (0, 0, -1.2), 0.5: (0, 0, -1.2), 0.95: (0, 0, 0.4), 1.45: (0, 0, 0.4), 1.9: (0, 0, -1.2), 2.0: (0, 0, -1.2)},
           easing="easeInOutSine")
    # Quill: down onto the tray chip top (~1 px), back up; down onto the board, back up.
    w.move("arm", {0: (0, 0, 0), 0.15: (0, -0.9, 0), 0.3: (0, -0.9, 0), 0.45: (0, 0, 0),
                   1.0: (0, 0, 0), 1.15: (0, -1.4, 0), 1.3: (0, -1.4, 0), 1.45: (0, 0, 0), 2.0: (0, 0, 0)})
    # The carried chip appears when it is picked up and vanishes once it is set down.
    w.scale("chip", {0: (0, 0, 0), 0.29: (0, 0, 0), 0.3: (1, 1, 1), 1.29: (1, 1, 1), 1.3: (0, 0, 0), 2.0: (0, 0, 0)})
    return [w]


if __name__ == "__main__":
    model, texture, glowmask = build()
    for p in model.save(texture, glowmask):
        print(p)
    print(save_animations(NAME, animations()))
