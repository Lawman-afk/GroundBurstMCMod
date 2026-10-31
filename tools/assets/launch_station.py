"""The Launch Control Station (DESIGN.md §17.12; the user's plan: "a way of networking your launchers across the whole
world") and its Launch Codes.

- Formed (GeckoLib), 3 blocks wide, 2 deep and 3 high, the controller at the front middle (front = north):
  * the desk across the front row: an olive body with drawers, a dark top board; on its back half the raked instrument
    panel (leaning back 30 degrees) with the status board (two rows of eight lamps, one for each of the first sixteen
    members: bones "lamp<i>_<state>" lit green, red or amber), the two launch keys far apart ("key_l", "key_r", turned
    while anything is armed) and the guarded LAUNCH button under its flip cover ("cover", lifted while anything is
    armed), a row of push buttons; behind the panel a shelf; on the desk top a keyboard, the red telephone at the west end
    and the teletype printer with its paper at the east end;
  * behind it the instrument bank, two blocks high: dark grey cabinets in panels on a plinth, the big tactical screen in
    the middle (its glass "screen_main", lit while powered; the block entity's renderer writes the network's state on it),
    a smaller screen on each side ("screen_l", "screen_r") over a lamp panel, louvres and drawers below, a clock, the
    placard over the big screen; a cornice round its top;
  * on top over the bank's middle, the uplink: a mast with a small white dish looking up at the sky ("dish") and a
    beacon lamp; a whip antenna at the bank's east corner.
- Controller (block): the machine cabinet, its placard a console with its red button.
- Launch Codes (item): the sealed codes card, red-banded, in its clear sleeve.
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))

import numpy as np  # noqa: E402

from gb import machine_kit as K  # noqa: E402
from gb import materials as M  # noqa: E402
from gb import palette as P  # noqa: E402
from gb import paths  # noqa: E402
from gb.geo import Animation, GeoModel, save_animations  # noqa: E402
from gb.model import write_json  # noqa: E402
from gb.tex import Tex  # noqa: E402
import assemblers as A  # noqa: E402
import multiblock_tools as MT  # noqa: E402
import structures as ST  # noqa: E402

NAME = "launch_control_station"
SIDES = ("north", "south", "east", "west")
#: The desk: its body's top, its board's top; the raked panel's foot (its front edge on the board) and lean.
DESK, BOARD = 10.5, 11.5
PANEL_Z, LEAN, PANEL_H, PANEL_T = -1.0, 30.0, 5.2, 2.6
#: The bank: its front, back, top; the big screen; the uplink's dish (its pivot) and lean.
BANK_Z0, BANK_Z1, BANK_TOP = 9.0, 23.5, 31.0
SCREEN = ((-10.5, 14.0), (10.5, 28.0))
DISH_Y, DISH_Z, DISH_LEAN = 42.0, 16.0, 50.0
#: The status board: two rows of eight lamps on the raked face (along the face: u across, v up the slope from its foot).
LAMP_U0, LAMP_DU, LAMP_V = 0.5, -2.0, (2.9, 1.2)
LAMP_STATES = ("ready", "armed", "busy", "fault")
GREY_RAMP = M.ramp("#15171a", "#1f2226", "#2a2e33", "#363b41", "#444a51", "#545b63", "#666e77", "#7c858f", "#99a3ad")


def lamp_slot(i: int) -> tuple[float, float]:
    """Lamp {i}'s place on the raked face: across (x; numbered from the operator's left, which is east, +x) and up the
    slope from its foot."""
    return LAMP_U0 + (i % 8) * LAMP_DU, LAMP_V[i // 8]


def build() -> tuple[GeoModel, Tex, Tex, dict]:
    g = GeoModel(NAME, tex_size=(256, 256))
    g.bone("frame")
    F = "frame"
    # The raked panel: a bone leaning back about its foot, its parts laid on its face in its own (upright) frame.
    g.bone("panel", pivot=(0, BOARD, PANEL_Z), parent=F, rotation=(LEAN, 0, 0))
    g.bone("key_l", pivot=(-18.5, BOARD + 2.6, PANEL_Z), parent="panel")
    g.bone("key_r", pivot=(17.5, BOARD + 2.6, PANEL_Z), parent="panel")
    g.bone("cover", pivot=(11.0, BOARD + 4.0, PANEL_Z - 0.6), parent="panel")
    g.bone("screens", parent=F)
    g.bone("beacon", parent=F)
    g.bone("dish", pivot=(0, DISH_Y, DISH_Z), parent=F, rotation=(DISH_LEAN, 0, 0))
    for i in range(16):
        for s in LAMP_STATES:
            g.bone(f"lamp{i}_{s}", parent="panel")
    parts: dict[str, list] = {}

    def add(group, box):
        parts.setdefault(group, []).append(box)
        return box

    # --- the desk ---------------------------------------------------------------------------------------------------------
    add("desk", g.box("desk", (-23, 0, -6.5), (23, DESK, 7), faces=SIDES, bone=F))
    add("kick", g.box("kick", (-22.5, 0, -7), (22.5, 1.5, -6.5), faces=("north", "east", "west", "up"), bone=F))
    add("board", g.box("board", (-23.5, DESK, -7.75), (23.5, BOARD, 7.5), bone=F))
    # The raked panel (upright here: x across, y up its face from its foot, its face toward -z) and its shelf behind.
    add("panel", g.box("panel_plate", (-22, BOARD - 1.5, PANEL_Z), (22, BOARD + PANEL_H, PANEL_Z + PANEL_T),
                       faces=SIDES + ("up",), bone="panel"))
    # The shelf behind: its front corner hidden inside the plate (4 sin 30 < the plate's thickness).
    lean = math.radians(LEAN)
    back_foot = PANEL_Z + PANEL_T / math.cos(lean) + (1.5 * math.tan(lean))
    add("shelf", g.box("shelf", (-22, BOARD, round(back_foot - 0.2, 2)), (22, BOARD + 3.2, 6.8), faces=("south", "east", "west", "up"), bone=F))
    # On the face: the status board's plate, the key collars, the button guard, a row of push buttons.
    add("statusboard", g.box("statusboard", (-15.5, BOARD + 0.6, PANEL_Z - 0.3), (1.5, BOARD + 4.8, PANEL_Z),
                             faces=("north", "east", "west", "up", "down"), bone="panel"))
    for side, x in (("l", -18.5), ("r", 17.5)):
        add("collar", g.box(f"collar_{side}", (x - 1.3, BOARD + 1.3, PANEL_Z - 0.4), (x + 1.3, BOARD + 3.9, PANEL_Z),
                            faces=("north", "east", "west", "up", "down"), bone="panel"))
        add("key", g.box(f"key_{side}_bit", (x - 0.35, BOARD + 2.0, PANEL_Z - 1.4), (x + 0.35, BOARD + 3.2, PANEL_Z - 0.4),
                         faces=("north", "east", "west", "up", "down"), bone=f"key_{side}"))
        add("key", g.box(f"key_{side}_bow", (x - 1.0, BOARD + 2.3, PANEL_Z - 1.9), (x + 1.0, BOARD + 2.9, PANEL_Z - 1.4),
                         faces=("north", "east", "west", "up", "down"), bone=f"key_{side}"))
    add("guard", g.box("guard", (8.5, BOARD + 0.8, PANEL_Z - 0.3), (13.5, BOARD + 4.0, PANEL_Z), faces=("north", "east", "west", "up", "down"),
                       bone="panel"))
    add("button", g.box("button", (9.8, BOARD + 1.6, PANEL_Z - 1.0), (12.2, BOARD + 3.4, PANEL_Z - 0.3), faces=("north", "east", "west", "up", "down"),
                        bone="panel"))
    add("cover", g.box("cover_lid", (8.8, BOARD + 0.9, PANEL_Z - 1.5), (13.2, BOARD + 4.0, PANEL_Z - 1.2), faces=SIDES + ("up", "down"),
                       bone="cover"))
    add("cover", g.box("cover_w", (8.8, BOARD + 0.9, PANEL_Z - 1.2), (9.1, BOARD + 4.0, PANEL_Z - 0.4), faces=("east", "west", "down"),
                       bone="cover"))
    add("cover", g.box("cover_e", (12.9, BOARD + 0.9, PANEL_Z - 1.2), (13.2, BOARD + 4.0, PANEL_Z - 0.4), faces=("east", "west", "down"),
                       bone="cover"))
    for k in range(4):
        add("pushbutton", g.box(f"push{k}", (2.6 + k * 1.4, BOARD + 1.4, PANEL_Z - 0.5), (3.6 + k * 1.4, BOARD + 2.4, PANEL_Z),
                                faces=("north", "east", "west", "up", "down"), bone="panel"))
    # The status lamps: one of each state for each lamp, shown or hidden by the block entity.
    for i in range(16):
        u, v = lamp_slot(i)
        for s in LAMP_STATES:
            add(f"lamp_{s}", g.box(f"lamp{i}_{s}_c", (u - 0.5, BOARD + v - 0.5, PANEL_Z - 0.55), (u + 0.5, BOARD + v + 0.5, PANEL_Z - 0.3),
                                   faces=("north", "east", "west", "up", "down"), bone=f"lamp{i}_{s}"))
    # On the desk top: the keyboard, the telephone, the teletype.
    add("keyboard", g.box("keyboard", (-8, BOARD, -6.5), (8, BOARD + 0.8, -2.5), faces=SIDES + ("up",), bone=F))
    add("phone", g.box("phone_base", (-21, BOARD, -6), (-16, BOARD + 1.6, -2.5), faces=SIDES + ("up",), bone=F))
    add("phone", g.box("handset", (-21.4, BOARD + 1.6, -5.4), (-15.6, BOARD + 2.6, -3.6), faces=SIDES + ("up", "down"), bone=F))
    add("cord", g.box("cord", (-15.6, BOARD + 0.2, -4.8), (-15.0, BOARD + 0.8, -4.2), faces=("north", "south", "east", "up"), bone=F))
    add("tty", g.box("tty", (15.0, BOARD, -7.2), (22.0, BOARD + 3.0, -3.6), faces=SIDES + ("up",), bone=F))
    add("paper", g.box("paper", (16.2, BOARD + 3.0, -5.4), (20.8, 16.0, -4.9), faces=SIDES + ("up",), bone=F))
    add("roll", g.box("roll", (15.8, BOARD + 3.0, -4.9), (21.2, BOARD + 4.0, -3.8), faces=SIDES + ("up",), bone=F))

    # --- the bank -----------------------------------------------------------------------------------------------------------
    add("plinth", g.box("bank_plinth", (-23.5, 0, BANK_Z0 + 0.5), (23.5, 2, BANK_Z1), faces=SIDES, bone=F))
    add("bank", g.box("bank", (-24, 2, BANK_Z0), (24, BANK_TOP, BANK_Z1 + 0.5), faces=SIDES, bone=F))
    add("cornice", g.box("cornice", (-24, BANK_TOP, BANK_Z0 - 0.5), (24, BANK_TOP + 1, 24), faces=SIDES + ("up", "down"), bone=F))
    (sx0, sy0), (sx1, sy1) = SCREEN
    add("bezel", g.box("bezel_main", (sx0 - 1.5, sy0 - 1.5, BANK_Z0 - 0.75), (sx1 + 1.5, sy1 + 1.5, BANK_Z0), faces=("north", "east", "west", "up", "down"),
                       bone=F))
    add("bezel", g.box("bezel_l", (-22.5, 18.5, BANK_Z0 - 0.5), (-13.5, 27.5, BANK_Z0), faces=("north", "east", "west", "up", "down"), bone=F))
    add("bezel", g.box("bezel_r", (13.5, 18.5, BANK_Z0 - 0.5), (22.5, 27.5, BANK_Z0), faces=("north", "east", "west", "up", "down"), bone=F))
    add("glass", g.box("screen_main", (sx0, sy0, BANK_Z0 - 1.0), (sx1, sy1, BANK_Z0 - 0.75), faces=("north", "east", "west", "up", "down"),
                       bone="screens"))
    add("glass", g.box("screen_l", (-21.5, 19.5, BANK_Z0 - 0.75), (-14.5, 26.5, BANK_Z0 - 0.5), faces=("north", "east", "west", "up", "down"),
                       bone="screens"))
    add("glass", g.box("screen_r", (14.5, 19.5, BANK_Z0 - 0.75), (21.5, 26.5, BANK_Z0 - 0.5), faces=("north", "east", "west", "up", "down"),
                       bone="screens"))
    add("energy_port", g.box("energy_port", (-6, 2.5, BANK_Z1 + 0.5), (6, 13.5, BANK_Z1 + 1.0), faces=("south", "east", "west", "up", "down"),
                             bone=F))
    add("placard", g.box("placard", (-7, 29.6, BANK_Z0 - 0.4), (7, 30.6, BANK_Z0), faces=("north", "east", "west", "up", "down"), bone=F))
    add("clock", g.box("clock", (-22, 28.0, BANK_Z0 - 0.4), (-19.6, 30.4, BANK_Z0), faces=("north", "east", "west", "up", "down"), bone=F))

    # --- the uplink ---------------------------------------------------------------------------------------------------------
    add("mast", g.box("mast_base", (-3, BANK_TOP + 1, 13), (3, BANK_TOP + 2.5, 19), faces=SIDES + ("up",), bone=F))
    add("mast", g.box("mast", (-0.75, BANK_TOP + 2.5, 15.25), (0.75, DISH_Y - 0.5, 16.75), faces=SIDES, bone=F))
    add("yoke", g.box("yoke", (-1.6, DISH_Y - 1.4, 15.0), (1.6, DISH_Y + 0.6, 17.0), faces=SIDES + ("up", "down"), bone="dish"))
    rings = []
    for i, (r, dz) in enumerate(((6.5, -2.4), (4.6, -1.8), (2.6, -1.2))):
        w = round(r * 0.66, 2)
        z0, z1 = DISH_Z + dz, DISH_Z + dz + 0.9
        for part, a, b, faces in ((f"d{i}c", (-r, DISH_Y + 3 - w, z0), (r, DISH_Y + 3 + w, z1), SIDES),
                                  (f"d{i}u", (-w, DISH_Y + 3 + w, z0), (w, DISH_Y + 3 + r, z1), SIDES + ("up",)),
                                  (f"d{i}d", (-w, DISH_Y + 3 - r, z0), (w, DISH_Y + 3 - w, z1), SIDES + ("down",))):
            rings.append(add("dish", g.box(part, a, b, faces=faces, bone="dish")))
    add("feed", g.box("feed_arm", (-0.3, DISH_Y + 2.7, DISH_Z - 7.0), (0.3, DISH_Y + 3.3, DISH_Z - 2.4), faces=("east", "west", "up", "down"),
                      bone="dish"))
    add("feed", g.box("feed", (-0.9, DISH_Y + 2.1, DISH_Z - 8.0), (0.9, DISH_Y + 3.9, DISH_Z - 7.0), bone="dish"))
    add("beacon", g.box("beacon_lamp", (2.0, BANK_TOP + 2.5, 17.2), (3.0, BANK_TOP + 3.7, 18.2), faces=SIDES + ("up",), bone="beacon"))
    add("whip", g.box("whip_base", (19.5, BANK_TOP + 1, 19.5), (21.5, BANK_TOP + 2, 21.5), faces=SIDES + ("up",), bone=F))
    add("whip", g.box("whip", (20.2, BANK_TOP + 2, 20.2), (20.8, 47.5, 20.8), faces=SIDES + ("up",), bone=F))
    g.layout()
    tex, glow = Tex(g.tex_w, g.tex_h), Tex(g.tex_w, g.tex_h)
    paint(g, tex, glow, parts)
    return g, tex, glow, parts


def paint(g: GeoModel, tex: Tex, glow: Tex, parts: dict) -> None:
    """The station's surfaces, in model space (the material library): the desk olive with its drawers and a dark board;
    the raked panel dark grey, its status board black with its lamps' sockets, brass key collars, the red guard and
    button under a clear red cover, white push buttons; the keyboard's keys, the red telephone, the grey teletype and
    its paper; the bank dark grey in panels on a dark plinth, louvres and drawers below, its screens dark glass in
    gunmetal bezels (lit glass over them while powered), a lamp panel under each side screen, the clock, the white
    placard; the uplink galvanised with a white dish."""
    k = K.Kit(g, parts, seed=919)
    grey = M.painted(GREY_RAMP, tone=0.5, wear=0.18, grime=0.04)
    grey_light = M.painted(GREY_RAMP, tone=0.6, wear=0.15, grime=0.03)
    # --- the desk ---------------------------------------------------------------------------------------------------------
    k.paint(tex, "desk", K.OLIVE)

    def drawers(f, img):                                       # three drawers each side of the knee hole
        u = f.X if f.d == "north" else (-f.X if f.d == "south" else f.Z)
        if f.d not in ("north",):
            return
        for x0 in (-21.0, -14.5, 8.5, 15.0):
            leaf = (u >= x0) & (u < x0 + 6) & (f.Y >= 2.5) & (f.Y < 9.5)
            M.darken(img, leaf & ((u < x0 + 0.6) | (u >= x0 + 5.4) | (f.Y < 3.1) | (f.Y >= 8.9)), 0.55)
            M.lighten(img, leaf & (np.abs(u - (x0 + 3)) < 1.1) & (np.abs(f.Y - 7.6) < 0.4), 1.4, 12)
        knee = (np.abs(u) < 7.5) & (f.Y < 9.0)
        M.darken(img, knee, 0.42)                               # the knee hole's shadow
    k.overlay(tex, "desk", drawers)
    k.paint(tex, "kick", K.DARK)
    k.paint(tex, "board", M.painted(M.ARMOUR_RAMP, tone=0.42, wear=0.2))
    k.overlay(tex, "board", lambda f, img: M.darken(img, K.ring(f) < 1, 0.7), only=("up",))

    # --- the raked panel ------------------------------------------------------------------------------------------------
    k.paint(tex, "panel", grey)
    k.panels(tex, "panel", inset=1, rivets=False, only=("north",), min_size=4)
    k.paint(tex, "shelf", grey)
    k.paint(tex, "statusboard", M.painted(M.BLACK_RAMP, tone=0.45, wear=0.05))

    def sockets(t: Tex, r, d):                                  # each lamp's dark socket, and the board's legend line
        x, y, w, h = r
        if d == "north":
            t.outline(x, y, w, h, P.GUNMETAL[3])
    g.paint_all(tex, "statusboard", sockets)
    k.paint(tex, "collar", K.BRASS)
    k.paint(tex, "key", K.STEEL)
    k.paint(tex, "guard", M.painted(M.YELLOW_PAINT_RAMP, tone=0.6, wear=0.25))
    k.overlay(tex, "guard", lambda f, img: M.paste(img, k.on(f, K.HAZARD), K.ring(f) < 1), only=("north",))
    k.paint(tex, "button", K.RED)

    def cover(t: Tex, r, d):                                    # a clear red cover: the button seen through it
        x, y, w, h = r
        t.rect(x, y, w, h, (150, 24, 18, 255))
        if d == "north" and w >= 3:
            t.rect(x + 1, y + 1, w - 2, h - 2, (196, 40, 28, 255))
            t.px(x + 1, y + 1, (240, 150, 140, 255))
    for b in parts["cover"]:
        g.paint_all(tex, b, cover)
    k.paint(tex, "pushbutton", K.CERAMIC)
    lamp_colours = {"ready": P.INDICATOR_GREEN, "armed": P.INDICATOR_RED, "busy": P.NUCLEAR_AMBER, "fault": P.INDICATOR_RED}

    def lamp_of(state):
        def draw(t: Tex, r, d):
            x, y, w, h = r
            c = lamp_colours[state]
            t.rect(x, y, w, h, c[4])
            if d == "north":
                t.px(x, y, c.light)
        return draw
    for s in LAMP_STATES:
        for b in parts[f"lamp_{s}"]:
            g.paint_all(tex, b, lamp_of(s))
            g.paint_all(glow, b, lamp_of(s))

    # --- the desk top's things ------------------------------------------------------------------------------------------
    k.paint(tex, "keyboard", M.painted(M.ARMOUR_RAMP, tone=0.5, wear=0.2))

    def keys(t: Tex, r, d):
        x, y, w, h = r
        if d == "up":
            for yy in range(y + 1, y + h - 1):
                for xx in range(x + 1, x + w - 1):
                    if (xx - x) % 4 != 0:
                        t.px(xx, yy, P.WHITE_CERAMIC[3] if (yy - y) % 2 else P.WHITE_CERAMIC[2])
    g.paint_all(tex, "keyboard", keys)
    k.paint(tex, "phone", M.painted(M.RED_PAINT_RAMP, tone=0.5, wear=0.12))

    def dial(t: Tex, r, d):
        x, y, w, h = r
        if d == "up" and w >= 4:
            t.rect(x + w // 2 - 1, y + h // 2 - 1, 2, 2, P.WHITE_CERAMIC[5])
    g.paint_all(tex, "phone_base", dial)
    k.paint(tex, "cord", K.BLACK)
    k.paint(tex, "tty", grey_light)
    k.overlay(tex, "tty", lambda f, img: M.darken(img, (np.abs(f.Z + 6.2) < 0.3) & (np.abs(f.X - 18.5) < 2.6), 0.4), only=("up",))
    k.paint(tex, "paper", M.white_paint(tone=0.86, wear=0.0))

    def printed(t: Tex, r, d):                                  # lines of print on the paper
        x, y, w, h = r
        if d in ("north", "south") and h >= 3:
            for yy in range(y + 1, y + h, 2):
                t.hline(x + 1, yy, max(1, w - 2 - (yy % 3)), P.BLACK_PAINT[3])
    g.paint_all(tex, "paper", printed)
    k.paint(tex, "roll", M.white_paint(tone=0.8, wear=0.0))

    # --- the bank -----------------------------------------------------------------------------------------------------------
    k.plinth(tex, "plinth", front="north", band=True)
    k.paint(tex, "bank", grey)
    k.panels(tex, "bank", inset=2, min_size=10, only=("east", "west", "south"))

    def bank_front(f, img):
        if f.d != "north":
            return
        X, Y = f.X, f.Y
        # Three cabinets: a seam where each meets the next, a rivet line round each.
        M.darken(img, (np.abs(X + 12.5) < 0.4) | (np.abs(X - 12.5) < 0.4), 0.55)
        # Below: a louvre in each cabinet and drawers in the side ones.
        for cx in (-18.0, 0.0, 18.0):
            louvre = (np.abs(X - cx) < 4.5) & (Y > 3) & (Y < 7.5)
            M.darken(img, louvre & (np.floor(Y * 2) % 2 == 0), 0.45)
            M.lighten(img, louvre & (np.floor(Y * 2) % 2 == 1), 1.1, 4)
        for cx in (-18.0, 18.0):
            drawer = (np.abs(X - cx) < 4.5) & (Y >= 8.5) & (Y < 15.5)
            M.darken(img, drawer & ((np.abs(X - cx) > 4.0) | (Y < 9.0) | (Y >= 15.0)), 0.55)
            M.lighten(img, drawer & (np.abs(X - cx) < 1.0) & (np.abs(Y - 14.0) < 0.4), 1.4, 12)
        # The lamp panels under the side screens: a dark strip, its lamps drawn as glyphs.
        for cx in (-18.0, 18.0):
            M.darken(img, (np.abs(X - cx) < 4.0) & (Y > 16.2) & (Y < 18.0), 0.4)
        # The big screen's cabinet: a dark band below it, a nameplate line above.
        M.darken(img, (np.abs(X) < 11) & (Y > 9) & (Y < 11.5), 0.6)
    k.overlay(tex, "bank", bank_front)

    def bank_glyphs(t: Tex, r, d):
        x, y, w, h = r
        if d != "north":
            return
        # Its face runs y 2..31 bottom to top (texels top down) and x -24..24.
        def at(mx, my):
            return x + int(mx + 24), y + int(31 - my)
        for cx in (-18, 18):
            for i in range(4):
                px, py = at(cx - 3 + i * 2, 17)
                t.px(px, py, (P.INDICATOR_GREEN.light, P.NUCLEAR_AMBER.light, P.INDICATOR_GREEN.light, P.INDICATOR_RED.light)[i])
                glow.px(px, py, (P.INDICATOR_GREEN.light, P.NUCLEAR_AMBER.light, P.INDICATOR_GREEN.light, P.INDICATOR_RED.light)[i])
        for i in range(6):                                       # the big screen's control buttons, under it
            px, py = at(-6 + i * 2.5, 10)
            t.px(px, py, P.WHITE_CERAMIC[4])
    g.paint_all(tex, "bank", bank_glyphs)
    k.paint(tex, "cornice", M.painted(GREY_RAMP, tone=0.4, wear=0.15))
    k.paint(tex, "bezel", M.painted(M.ARMOUR_RAMP, tone=0.5, wear=0.15))

    def dark_glass(t: Tex, r, d):                               # the bezels' unlit glass (seen when the station is dark)
        x, y, w, h = r
        if d == "north" and w > 4 and h > 4:
            t.rect(x + 1, y + 1, w - 2, h - 2, P.BLACK_PAINT.deep)
            t.px(x + 2, y + 2, P.GUNMETAL[3])
    for b in parts["bezel"]:
        g.paint_all(tex, b, dark_glass)

    def lit_main(t: Tex, r, d, lit=True):                       # the big screen: a dark phosphor field with a grid
        x, y, w, h = r
        t.rect(x, y, w, h, (6, 22, 10, 255))
        if d == "north":
            for xx in range(x, x + w):
                for yy in range(y, y + h):
                    if (xx - x) % 4 == 0 or (yy - y) % 4 == 0:
                        t.px(xx, yy, (10, 40, 18, 255))
            t.outline(x, y, w, h, (18, 70, 30, 255))

    def lit_side(t: Tex, r, d):                                 # the side screens: a trace and a few blips
        x, y, w, h = r
        t.rect(x, y, w, h, (6, 22, 10, 255))
        if d == "north":
            for xx in range(x, x + w):
                t.px(xx, y + h // 2 + ((xx * 7) % 5) - 2, P.INDICATOR_GREEN[4])
            t.px(x + 1, y + 1, P.INDICATOR_GREEN[6])
            t.px(x + w - 2, y + h - 2, P.NUCLEAR_AMBER.light)
    for target in (tex, glow):
        g.paint_all(target, "screen_main", lit_main)
        g.paint_all(target, "screen_l", lit_side)
        g.paint_all(target, "screen_r", lit_side)
    k.paint(tex, "placard", K.WHITE)

    def placard_text(t: Tex, r, d):                             # "LAUNCH CONTROL" as a line of dark lettering
        x, y, w, h = r
        if d == "north":
            for xx in range(x + 1, x + w - 1):
                if xx % 4 != 0:
                    t.px(xx, y + h // 2, P.BLACK_PAINT[2])
    g.paint_all(tex, "placard", placard_text)
    k.paint(tex, "clock", K.CERAMIC)

    def clock(t: Tex, r, d):
        x, y, w, h = r
        if d == "north":
            t.outline(x, y, w, h, P.BLACK_PAINT[2])
            t.px(x + w // 2, y + 1, P.BLACK_PAINT[1])
            t.px(x + w // 2, y + h // 2, P.WARNING_RED.base)
    g.paint_all(tex, "clock", clock)

    # --- the uplink ---------------------------------------------------------------------------------------------------------
    k.paint(tex, ["mast", "yoke", "whip", "feed"], K.GALV)
    k.paint(tex, "dish", M.white_paint(tone=0.8, wear=0.1))

    def hollow(f, img):                                         # the dish's inner rings in its own shadow (its hollow)
        if f.d == "north":
            r = np.hypot(f.X, f.Y - (DISH_Y + 3))
            M.darken(img, r < 4.6, 0.88)
            M.darken(img, r < 2.6, 0.9)
    k.overlay(tex, "dish", hollow)
    plate = M.steel(tone=0.4, brushed=0.04, rust=0.08)
    k.paint(tex, "energy_port", plate)
    border = M.painted(M.RED_PAINT_RAMP, tone=0.58, wear=0.3)

    def port_rim(f, img):
        ring = K.ring(f)
        M.paste(img, k.on(f, border), ring < 2)
        M.darken(img, ring == 2, 0.55)
    k.overlay(tex, "energy_port", port_rim, only=("south",))

    def bolt(t: Tex, r, d):
        if d == "south":
            cx, cy = r[0] + r[2] // 2, r[1] + r[3] // 2
            for dx, dy in ((1, -3), (0, -2), (-1, -1), (0, -1), (1, -1), (0, 0), (-1, 1), (-2, 2)):
                t.px(cx + dx, cy + dy, P.HAZARD_YELLOW[4])
    g.paint_all(tex, "energy_port", bolt)

    def back(f, img):                                           # louvres high on the back, either side of the uplink's cable
        if f.d != "south":
            return
        for cx in (-15.0, 15.0):
            louvre = (np.abs(f.X - cx) < 5.0) & (f.Y > 21) & (f.Y < 27)
            M.darken(img, louvre & (np.floor(f.Y * 2) % 2 == 0), 0.5)
            M.lighten(img, louvre & (np.floor(f.Y * 2) % 2 == 1), 1.1, 4)
        M.darken(img, (np.abs(f.X) < 1.0) & (f.Y > 14), 0.6)              # the cable conduit up to the uplink
    k.overlay(tex, "bank", back)

    def beacon(t: Tex, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, P.INDICATOR_RED[3])
        t.px(x, y, P.INDICATOR_RED.light)
    g.paint_all(tex, "beacon_lamp", beacon)
    g.paint_all(glow, "beacon_lamp", beacon)


# --- the blocks and the item ---------------------------------------------------------------------------------------------

def placard(t: Tex) -> None:
    """The controller's placard: a console desk with its red launch button and a key."""
    t.rect(4, 9, 6, 4, P.BLACK_PAINT[1])
    t.hline(4, 12, 6, P.GUNMETAL[4])                                # the desk
    t.rect(5, 10, 2, 1, P.INDICATOR_GREEN[4])                       # its screen
    t.px(8, 10, P.WARNING_RED.base)                                 # the button
    t.px(8, 11, P.BRASS[4])                                         # the key


def codes() -> None:
    """Launch Codes: a sealed card in a clear plastic sleeve (the 'biscuit'): the card's white stock with a red band across
    it, the code's lines printed under the band, the gold seal; the sleeve's edge round it and its tear strip along the
    top catching the light. Painted from the material library's tiles (paper, red paint, gold), the sleeve drawn over."""
    paper = M.to_tex(M.tile(M.white_paint(tone=0.86, wear=0.0), seed=77))
    red = M.to_tex(M.tile(M.painted(M.RED_PAINT_RAMP, tone=0.5, wear=0.05), seed=78))
    gold = M.to_tex(M.tile(K.BRASS, seed=79))
    t = Tex(16, 16)
    for y in range(3, 13):
        for x in range(2, 14):
            t.px(x, y, paper.get(x, y))
    for y in (6, 7):
        for x in range(3, 13):
            t.px(x, y, red.get(x, y))
    t.hline(3, 7, 10, P.INDICATOR_RED[2])                           # the band's lower edge, in its shade
    for x in range(4, 12):                                          # the code, printed in two groups
        if x != 7:
            t.px(x, 9, P.BLACK_PAINT[2])
    for x in range(4, 9):
        t.px(x, 11, P.BLACK_PAINT[4])
    t.px(11, 11, gold.get(11, 11))                                  # the seal
    t.px(12, 11, gold.get(12, 11))
    t.px(11, 10, gold.get(11, 10))
    t.outline(2, 3, 12, 10, (112, 126, 138, 255))                   # the sleeve's edge
    t.hline(3, 4, 10, (222, 232, 238, 255))                         # its tear strip, catching the light
    t.px(13, 4, (160, 176, 186, 255))
    t.save(paths.TEXTURES / "item" / "launch_codes.png")
    write_json(paths.MODELS / "item" / "launch_codes.json", {"parent": "minecraft:item/generated",
                                                              "textures": {"layer0": "groundburst:item/launch_codes"}})
    write_json(paths.ASSETS / "items" / "launch_codes.json", {"model": {"type": "minecraft:model", "model": "groundburst:item/launch_codes"}})


def spawner_marks(ox: int, oy: int, w: int, h: int):
    """The spawner sheet's plan: the desk across the front, the bank behind."""
    marks = []
    for x in range(ox + 1, ox + w - 1):
        marks.append((x, oy + 2, P.NATO_GREEN[4]))
        marks.append((x, oy + h - 2, P.GUNMETAL[4]))
    marks.append((ox + w // 2, oy + h - 3, P.INDICATOR_GREEN.light))
    return marks


def main() -> None:
    ST.controller(NAME, ST.controller_front(placard), ST.controller_top(), ST.casing())
    codes()
    model, texture, glowmask, _ = build()
    print("z-fights:", model.zfight())
    for p in model.save(texture, glowmask):
        print(p)
    # Nothing animates (the block entity sets the keys, the cover, the lamps, the screens and the beacon); a still pose.
    idle = Animation("idle", 1.0, loop="hold_on_last_frame")
    idle.rotate("key_l", {0: (0, 0, 0), 1: (0, 0, 0)})
    print(save_animations(NAME, [idle]))
    MT.structure_spawner(f"{NAME}_spawner", A.plan(3, 2, spawner_marks))
    print("launch control station written")


if __name__ == "__main__":
    main()
