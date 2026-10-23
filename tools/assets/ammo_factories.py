"""The superweapons' ammunition factories (DESIGN.md §19.5, after C&C Generals): their formed models and controllers.

- Arms Dealer (the GLA's; builds Toxin SCUDs): a walled compound of tan adobe with green trim; at its back the workshop
  behind big dark double doors, its chimney smoking, a tile-roofed annex at one end; a corrugated awning over racks of
  rockets down one side of the yard; crates and drums; a gate arch over the controller with the faction's sign; and the
  SCUD being built lying across the yard on two trestles under an A-frame hoist, its trolley running and its hook
  lowering as the sections are set down.
- War Factory (China's; builds Nuclear Missiles): an octagonal hall of two tiers under brown pagoda roofs with red eaves
  (cream walls, fieldstone quoins), a roller door into the yard under a corrugated awning with the red star; the furnace
  annex glowing, its chimney striped red and grey; a conveyor carrying crates; the missile's yard with its cradles and a
  gantry crane on rails (a green lattice post, a red machinery house, a yellow boom with a claw) running along it; the
  red star painted on the apron.

The missile being built is not part of either model: the game draws it from its own model (tools/assets/missiles.py)
at the "missile" bone, set on the cradles by its own girth (AmmoFactoryBlockEntity.Kind has the cradles' figures).

Pixels: x right, y up, z back from the middle of the controller's cell (its bottom at y 0).

    tools\\.venv\\Scripts\\python.exe tools\\assets\\ammo_factories.py
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))

from gz import materials as M  # noqa: E402
from gz import palette as P  # noqa: E402
from gz import panels as PN  # noqa: E402
from gz.geo import Animation, GeoModel, rest_pose, save_animations  # noqa: E402
from gz.tex import Tex  # noqa: E402
import launch as L  # noqa: E402
import multiblock_tools as MT  # noqa: E402
import nuclear_silo as NS  # noqa: E402
import scud_storm as SS  # noqa: E402
import structures as ST  # noqa: E402
import warheads as W  # noqa: E402

SIDES, ALL, NO_DOWN = SS.SIDES, SS.ALL, SS.NO_DOWN
F = "frame"
DECK_Y = 14
GREEN_TRIM_RAMP = M.ramp("#0f1f10", "#16301a", "#1d4222", "#25552b", "#2e6935", "#3a7d41", "#4a914f", "#5ea561", "#78b978")
TILE_RAMP = M.ramp("#3a140a", "#52200f", "#6a2c14", "#82391a", "#994722", "#ad572c", "#bf6839", "#cf7b4b", "#dc8f61")
CORRUGATED_RAMP = M.ramp("#2c2620", "#3b342b", "#4c4337", "#5e5344", "#706352", "#837461", "#958671", "#a69783", "#b6a896")
YELLOW_BOOM = M.painted(M.YELLOW_PAINT_RAMP, tone=0.62, wear=0.35, rust=0.12)
GREEN_TRIM = M.painted(GREEN_TRIM_RAMP, tone=0.58, wear=0.3, rust=0.05)
TILES = M.Material(ramp=TILE_RAMP, tone=0.55, mottle=0.05, grain=0.04, dither=0.6, edge_light=0.16, edge_dark=0.12, ao=0.5, grime=0.08)
CORRUGATED = M.Material(ramp=CORRUGATED_RAMP, tone=0.55, mottle=0.06, grain=0.04, dither=0.6, edge_light=0.16, edge_dark=0.12, ao=0.5,
                        rust=0.35, streaks=0.1)
FURNACE_GLOW = (255, 150, 50, 255)


def ring(g, name, r, y0, y1, cx, cz, bone=F, caps=("up",), sides=None, stagger=None):
    return SS.ring(g, name, r, y0, y1, cx, cz, bone, caps=caps, sides=sides, stagger=stagger)


def corrugate(f, img) -> None:
    """Corrugated iron: ridges every 2 px across it."""
    a, b = f.plane_uv()
    M.lighten(img, (np.floor(a) % 2) == 0, 1.12, 4)
    M.darken(img, (np.floor(a) % 2) == 1, 0.86)


# =============================================================================================================================
# The Arms Dealer
# =============================================================================================================================
#: The SCUD in the yard: its tail (x), axis height and z, lying nose to the left (-x); its trestles' tops and where along
#: it they hold it (AmmoFactoryBlockEntity.Kind.ARMS_DEALER's cradles).
AD_TAIL_X, AD_AXIS, AD_Z = 34.0, 28.0, 32.0
AD_CRADLE_TOP, AD_CRADLES = 22.0, (20.0, 46.0)
AD_FRAME_X, AD_BEAM_Y = 30.0, 58.0
AD_PARK = 20.0                                   # the trolley's rest (x)


def arms_dealer() -> None:
    SS.ROUND.clear()
    g = GeoModel("arms_dealer", tex_size=(1024, 1024))
    g.open_ok = set()
    g.bone(F)
    p = SS.Parts()
    # The yard's floor (rows 1-3), the compound's walls (two cells high, a green trim course on top), the gate arch.
    g.box("deck", (-56, 0, 8), (56, DECK_Y, 56), faces=("up",), bone=F)
    for n, a, b, faces in (("wall_fl", (-72, 0, -8), (-8, 28, 8), NO_DOWN), ("wall_fr", (8, 0, -8), (72, 28, 8), NO_DOWN),
                           ("wall_l", (-72, 0, 8), (-56, 28, 88), ("west", "east", "up")), ("wall_r", (56, 0, 8), (72, 28, 88), ("west", "east", "up")),
                           ("wall_b", (-72, 0, 88), (72, 28, 104), ("north", "south", "east", "west", "up"))):
        g.box(n, a, b, faces=faces, bone=F)
        p.add("adobe", n)
    # The green coping along the walls' tops: a strip down the middle of each (the adobe showing either side of it).
    for n, a, b in (("trim_fl", (-68, 28, -4), (-8, 29.5, 4)), ("trim_fr", (8, 28, -4), (68, 29.5, 4)),
                    ("trim_l", (-68, 28, 4), (-60, 29.5, 92)), ("trim_r", (60, 28, 4), (68, 29.5, 92)),
                    ("trim_b", (-60, 28, 92), (60, 29.5, 100))):
        g.box(n, a, b, faces=ALL, bone=F)
        p.add("trim", n)
    g.box("console", (-5, 0, -7), (5, 22, -2), faces=("north", "east", "west", "up"), bone=F)
    for s in (-1, 1):
        g.box(f"gatepost{s}", (s * 10 - 3, 0, -9), (s * 10 + 3, 40, -3), faces=NO_DOWN, bone=F)
        p.add("trim", f"gatepost{s}")
    for n in ("wall_fl", "wall_fr", "gatepost-1", "gatepost1"):
        g.open_ok.add((n, "down"))                              # they stand on the ground
    g.box("lintel", (-13, 40, -8.5), (13, 44, -3.5), faces=ALL, bone=F)
    g.box("sign", (-9, 33, -8.2), (9, 39, -7.6), faces=("north", "south", "east", "west", "down"), bone=F)
    p.add("trim", "lintel")
    g.box("e_housing", (72, 3, 27), (77, 14, 37), faces=("north", "south", "east", "up", "down"), bone=F)
    g.box("i_housing", (-77, 3, 27), (-72, 14, 37), faces=("north", "south", "west", "up", "down"), bone=F)
    # The workshop at the back (rows 4-5): adobe, its big dark double doors onto the yard, a flat roof behind a parapet,
    # its chimney; the annex at its right end under a pitched roof of tiles.
    g.box("workshop", (-56, 0, 56), (24, 32, 88), faces=("north", "up"), bone=F)
    g.box("annex", (24, 0, 56), (56, 28, 88), faces=("north", "up"), bone=F)
    g.box("parapet_f", (-56, 32, 56), (24, 35, 58), faces=("north", "south", "east", "west", "up"), bone=F)
    p.add("adobe", "workshop", "annex", "parapet_f")
    g.box("door_l", (-28, DECK_Y, 54.5), (-12.2, 30, 56), faces=("north", "east", "west", "up"), bone=F)
    g.box("door_r", (-11.8, DECK_Y, 54.5), (4, 30, 56), faces=("north", "east", "west", "up"), bone=F)
    g.box("door_head", (-30, 30, 54), (6, 33, 56), faces=("north", "east", "west", "up", "down"), bone=F)
    p.add("door", "door_l", "door_r")
    p.add("trim", "door_head")
    roof = []
    for s, name in ((-1, "tileroof_f"), (1, "tileroof_b")):
        pitch = math.degrees(math.atan2(10, 18))
        length = math.hypot(10, 18)
        if s < 0:
            g.box(name, (22, 28, 54), (58, 30, 54 + length), faces=ALL,
                  rotation={"origin": (22, 28, 54), "axis": "x", "angle": -pitch}, bone=F)
        else:
            g.box(name, (22, 28, 90 - length), (58, 30, 90), faces=ALL,
                  rotation={"origin": (22, 28, 90), "axis": "x", "angle": pitch}, bone=F)
        roof.append(name)
    p.add("tiles", roof)
    p.add("adobe", ring(g, "chimney", 4.0, 32, 52, -44, 78, sides=8, stagger="full"))
    p.add("soot", ring(g, "chimney_top", 4.6, 52, 54, -44, 78, sides=8, stagger="full", caps=("up", "down")))
    # The awning down the yard's left side over the rocket racks, on its posts; the racks and their rockets.
    for i, z in enumerate((12.0, 52.0)):
        g.box(f"awning_post{i}", (-38, DECK_Y, z - 1), (-36, 40, z + 1), faces=NO_DOWN, bone=F)
        p.add("wood", f"awning_post{i}")
    slope = math.degrees(math.atan2(6, 20))
    g.box("awning", (-57, 44.5, 8), (-57 + math.hypot(6, 20), 46, 56), faces=ALL,
          rotation={"origin": (-57, 46, 8), "axis": "z", "angle": -slope}, bone=F)
    p.add("corrugated", "awning")
    for i, z in enumerate((18.0, 34.0)):
        g.box(f"rack{i}", (-54, DECK_Y, z), (-40, 15.5, z + 12), faces=NO_DOWN, bone=F)
        g.box(f"rack_back{i}", (-54, 15.5, z), (-53, 30, z + 12), faces=ALL, bone=F)
        p.add("wood", f"rack{i}", f"rack_back{i}")
        for k in range(4):
            zz = z + 1.5 + k * 2.8
            g.box(f"rocket{i}_{k}", (-52.5, 15.5 + k * 0.4, zz), (-41, 17.5 + k * 0.4, zz + 2), faces=ALL, bone=F)
            g.box(f"rocket_tip{i}_{k}", (-41, 15.9 + k * 0.4, zz + 0.4), (-38.5, 17.1 + k * 0.4, zz + 1.6), faces=ALL, bone=F)
            p.add("rocket", f"rocket{i}_{k}")
            p.add("rocket_tip", f"rocket_tip{i}_{k}")
    # Crates and drums in the yard's right corner.
    for n, a, b in (("crate0", (40, DECK_Y, 10), (52, 24, 22)), ("crate1", (42, 24, 12), (50, 31, 20)), ("crate2", (44, DECK_Y, 24), (54, 22, 34))):
        g.box(n, a, b, faces=NO_DOWN if a[1] == DECK_Y else ALL, bone=F)
        p.add("crate", n)
    for i, (x, z) in enumerate(((46.0, 44.0), (52.0, 48.0))):
        p.add(f"drum{i}", ring(g, f"drum{i}", 3.5, DECK_Y, 25, x, z, sides=8, stagger="full"))
    # The SCUD's trestles: timber frames with rubber saddles (their tops where the block entity's cradles say).
    for i, h in enumerate(AD_CRADLES):
        x = AD_TAIL_X - h
        g.box(f"trestle{i}", (x - 2, AD_CRADLE_TOP - 4, AD_Z - 10), (x + 2, AD_CRADLE_TOP - 1, AD_Z + 10), faces=ALL, bone=F)
        g.box(f"saddle{i}", (x - 2.5, AD_CRADLE_TOP - 1, AD_Z - 5), (x + 2.5, AD_CRADLE_TOP, AD_Z + 5), faces=NO_DOWN, bone=F)
        for s in (-1, 1):
            g.box(f"trestle{i}_leg{s}", (x - 1.5, DECK_Y, AD_Z + s * 9 - 1.5), (x + 1.5, AD_CRADLE_TOP - 2, AD_Z + s * 9 + 1.5),
                  faces=NO_DOWN, bone=F)
            p.add("wood", f"trestle{i}_leg{s}")
        p.add("wood", f"trestle{i}")
        p.add("rubber", f"saddle{i}")
    g.bone("missile", pivot=(AD_TAIL_X, AD_AXIS, AD_Z), parent=F, rotation=(0, 90, 0))
    # The A-frame hoist across the yard: an A-frame at each end, the beam between, the trolley, its chain and hook.
    for k, x in enumerate((-AD_FRAME_X, AD_FRAME_X)):
        for s in (-1, 1):
            n = f"aframe{k}_{'nf'[s > 0]}"
            SS.strut(g, n, (x, DECK_Y, AD_Z + s * 20), (x, AD_BEAM_Y - 2, AD_Z + s * 1.8), 2.5, F)
            g.box(f"foot{k}{s}", (x - 2.5, DECK_Y, AD_Z + s * 20 - 2.5), (x + 2.5, 15.2, AD_Z + s * 20 + 2.5), faces=NO_DOWN, bone=F)
            p.add("aframe", n)
            p.add("steel", f"foot{k}{s}")
        g.box(f"ahead{k}", (x - 3, AD_BEAM_Y - 4, AD_Z - 3), (x + 3, AD_BEAM_Y, AD_Z + 3), faces=SIDES + ("down",), bone=F)
        p.add("aframe", f"ahead{k}")
    g.box("beam_bot", (-AD_FRAME_X - 6, AD_BEAM_Y, AD_Z - 3), (AD_FRAME_X + 6, AD_BEAM_Y + 1.2, AD_Z + 3), faces=ALL, bone=F)
    g.box("beam_web", (-AD_FRAME_X - 6, AD_BEAM_Y + 1.2, AD_Z - 0.8), (AD_FRAME_X + 6, AD_BEAM_Y + 5, AD_Z + 0.8), faces=SIDES, bone=F)
    g.box("beam_top", (-AD_FRAME_X - 6, AD_BEAM_Y + 5, AD_Z - 3), (AD_FRAME_X + 6, AD_BEAM_Y + 6.2, AD_Z + 3), faces=ALL, bone=F)
    p.add("beam", "beam_bot", "beam_web", "beam_top")
    g.bone("trolley", pivot=(AD_PARK, AD_BEAM_Y, AD_Z), parent=F)
    g.bone("chain", pivot=(AD_PARK, AD_BEAM_Y - 10, AD_Z), parent="trolley")
    g.bone("hook", pivot=(AD_PARK, 40, AD_Z), parent="trolley")
    g.box("trolley_body", (AD_PARK - 3.5, AD_BEAM_Y - 4, AD_Z - 3), (AD_PARK + 3.5, AD_BEAM_Y - 0.2, AD_Z + 3), faces=ALL, bone="trolley")
    g.box("hoist", (AD_PARK - 2.5, AD_BEAM_Y - 10, AD_Z - 2.5), (AD_PARK + 2.5, AD_BEAM_Y - 4, AD_Z + 2.5), faces=SIDES + ("down",), bone="trolley")
    g.box("chain_0", (AD_PARK - 0.5, 42, AD_Z - 0.5), (AD_PARK + 0.5, AD_BEAM_Y - 10, AD_Z + 0.5), faces=SIDES, bone="chain")
    g.box("hook_block", (AD_PARK - 1.8, 39, AD_Z - 1.2), (AD_PARK + 1.8, 42, AD_Z + 1.2), faces=ALL, bone="hook")
    g.box("hook_tip", (AD_PARK - 0.6, 36, AD_Z - 0.6), (AD_PARK + 0.6, 39, AD_Z + 0.6), faces=SIDES + ("down",), bone="hook")
    p.add("trolley", "trolley_body")
    p.add("hoistbox", "hoist", "hook_block")
    p.add("chain", "chain_0")
    p.add("steel", "hook_tip")
    g.layout()
    tex, glow = paint_arms_dealer(g, p)
    g.save(tex, glow)
    work = Animation("working", 12.0, loop=True)
    a = AD_TAIL_X - AD_CRADLES[0] - AD_PARK
    b = AD_TAIL_X - AD_CRADLES[1] - AD_PARK
    work.move("trolley", {0: (0, 0, 0), 2.5: (a, 0, 0), 5.0: (a, 0, 0), 7.5: (b, 0, 0), 10.0: (b, 0, 0), 12: (0, 0, 0)}, easing="easeInOutSine")
    L_rest = 6.0
    keys = {0: 0, 2.7: 0, 3.4: 8, 4.4: 8, 4.9: 0, 7.7: 0, 8.4: 8, 9.4: 8, 9.9: 0, 12: 0}
    work.move("hook", {t: (0, -v, 0) for t, v in keys.items()}, easing="easeInOutSine")
    work.scale("chain", {t: (1, (L_rest + v) / L_rest, 1) for t, v in keys.items()}, easing="easeInOutSine")
    print(save_animations("arms_dealer", [work, rest_pose([work])]))
    print(f"arms_dealer: {len(g.boxes)} boxes, texture {g.tex_w}x{g.tex_h}")


def paint_arms_dealer(g: GeoModel, p: SS.Parts) -> tuple[Tex, Tex]:
    from gz import machine_kit as K  # noqa: PLC0415
    k = K.Kit(g, {}, seed=811, ao_reach=3.0, turned=True)
    W.round_faces(k.S, SS.ROUND, top=True, bottom=False, foot=True)
    S = k.S
    tex, glow = Tex(g.tex_w, g.tex_h), Tex(g.tex_w, g.tex_h)
    S.paint(tex, "deck", SS.SAND)
    S.overlay(tex, "deck", lambda f, img: M.darken(img, (M.fbm(f.X, f.Y, f.Z, 6.0, 3, 3) > 0.62), 0.78), only=("up",))
    SS.paint_adobe(S, tex, p["adobe"])
    S.paint(tex, p["trim"], GREEN_TRIM)
    L._console(S, g, tex, "console")
    L._power_housing(S, g, tex, "e_housing", plug="west")
    S.paint(tex, "i_housing", M.painted(M.OLIVE_RAMP, tone=0.5, wear=0.35, rust=0.1))
    g.paint_all(tex, "sign", gla_sign)
    S.paint(tex, p["door"], M.painted(M.BLACK_RAMP, tone=0.5, wear=0.35, rust=0.15))
    S.overlay(tex, p["door"], lambda f, img: M.seams(f, img, every=4, axis="v", light=False), only=("north",))
    S.paint(tex, p["tiles"], TILES)
    S.overlay(tex, p["tiles"], NS.tiles, only=("up",))
    S.paint(tex, p["soot"], M.soot(tone=0.3))
    S.paint(tex, p["corrugated"], CORRUGATED)
    S.overlay(tex, p["corrugated"], corrugate, only=("up", "down"))
    S.paint(tex, p["wood"], M.wood(tone=0.45))
    S.paint(tex, p["rocket"], M.painted(M.OLIVE_RAMP, tone=0.5, wear=0.2))
    S.paint(tex, p["rocket_tip"], M.painted(M.BLACK_RAMP, tone=0.45, wear=0.1))
    S.paint(tex, p["crate"], M.wood(tone=0.5))
    S.overlay(tex, p["crate"], lambda f, img: M.seams(f, img, every=4, axis="v", light=False), only=SIDES)
    for i, ramp in enumerate((M.RED_PAINT_RAMP, M.OLIVE_RAMP)):
        S.paint(tex, p[f"drum{i}"], M.painted(ramp, tone=0.5, wear=0.4, rust=0.4))
    S.paint(tex, p["rubber"], M.rubber(tone=0.45))
    S.paint(tex, p["aframe"], M.painted(SS.TAN_RAMP, tone=0.58, wear=0.35, rust=0.15))
    S.paint(tex, p["steel"], M.steel(tone=0.45, rust=0.2))
    S.paint(tex, p["beam"], M.painted(M.YELLOW_PAINT_RAMP, tone=0.6, wear=0.35, rust=0.12))
    S.paint(tex, p["trolley"], M.painted(M.GREY_PAINT_RAMP, tone=0.45, wear=0.3, rust=0.1))
    S.paint(tex, p["hoistbox"], M.painted(M.YELLOW_PAINT_RAMP, tone=0.55, wear=0.4))
    S.paint(tex, p["chain"], M.Material(ramp=M.BLACK_RAMP, tone=0.45, dither=0.3, edge_light=0.25))
    return tex, glow


def gla_sign(t: Tex, r, d) -> None:
    """The sign over the gate: GLA green, a black band and a white mark (the flag's)."""
    x, y, w, h = r
    if d != "north" or w < 6:
        return
    t.rect(x, y, w, h, (40, 104, 40, 255))
    t.hline(x, y + h - 2, w, (20, 20, 20, 255))
    for (dx, dy) in ((1, 1), (2, 1), (1, 2), (1, 3), (2, 4), (3, 4)):
        t.px(x + w // 2 - 2 + dx, y + dy, (236, 232, 220, 255))


# =============================================================================================================================
# The War Factory
# =============================================================================================================================
#: The missile in the yard: its tail, axis and x, lying nose to the front; its cradles' tops and where along it they
#: hold it (AmmoFactoryBlockEntity.Kind.WAR_FACTORY's cradles).
WF_TAIL_Z, WF_AXIS, WF_X = 176.0, 32.0, 32.0
WF_CRADLE_TOP, WF_CRADLES = 22.0, (30.0, 100.0)
#: The hall (an octagon round its middle), the crane on its rail (x), its park (z) and its boom's height.
HALL = (-32.0, 144.0)
RAIL_X, CRANE_PARK, BOOM_Y = 64.0, 150.0, 70.0


def war_factory() -> None:
    SS.ROUND.clear()
    g = GeoModel("war_factory", tex_size=(1024, 1024))
    g.open_ok = set()
    g.bone(F)
    p = SS.Parts()
    # The floors: the apron along the front, between the annex and the yard, the middle, the yard, the hall's inside and
    # the back row; the rail's plinth down the right.
    for n, a, b in (("deck_apron", (-72, 0, -8), (56, DECK_Y, 8)), ("deck_front", (-24, 0, 8), (8, DECK_Y, 56)),
                    ("deck_mid", (-72, 0, 56), (8, DECK_Y, 104)), ("deck_yard", (8, 0, 8), (56, DECK_Y, 184)),
                    ("deck_hall", (-56, 0, 120), (-8, DECK_Y, 168)), ("deck_back", (-72, 0, 184), (72, DECK_Y, 200))):
        g.box(n, a, b, faces=("up", "north", "south", "east", "west"), bone=F)
        p.add("deck", n)
    g.box("plinth", (56, 0, -8), (72, 16, 184), faces=NO_DOWN, bone=F)
    p.add("concrete", "plinth")
    for s in (-1, 1):
        g.box(f"rail{s}", (RAIL_X + s * 4 - 1, 16, -6), (RAIL_X + s * 4 + 1, 18, 182), faces=NO_DOWN, bone=F)
        p.add("steel", f"rail{s}")
    g.box("console", (-5, DECK_Y, -7), (5, 22, -2), faces=NO_DOWN, bone=F)
    g.box("e_housing", (27, DECK_Y, 195), (37, 19, 200), faces=NO_DOWN, bone=F)
    g.box("i_hatch", (41, DECK_Y, 185), (55, 14.5, 199), faces=NO_DOWN, bone=F)
    # The furnace annex (cols 0-2, rows 1-3): brick, its mouth glowing on its side toward the yard, its chimney.
    g.box("annex", (-72, 0, 8), (-24, 32, 56), faces=("north", "south", "east", "west", "up"), bone=F)
    g.box("annex_cap", (-73, 32, 7), (-23, 34, 57), faces=ALL, bone=F)
    g.box("furnace", (-24, 16, 22), (-22.5, 28, 42), faces=("east", "north", "south", "up", "down"), bone=F)
    g.box("furnace_mouth", (-22.5, 18, 26), (-22.3, 26, 38), faces=("east",), bone=F)
    p.add("brick", "annex")
    p.add("concrete", "annex_cap")
    p.add("dark", "furnace")
    p.add("glow", "furnace_mouth")
    p.add("chimney", ring(g, "chimney", 5.0, 34, 96, -64, 16, sides=8, stagger="full"))
    p.add("dark", ring(g, "chimney_lip", 5.6, 96, 98, -64, 16, sides=8, stagger="full", caps=("up", "down")))
    # The conveyor (rows 4-6) from the annex to the hall, its crates riding it.
    g.box("belt_frame", (-48, DECK_Y, 56), (-36, 19, 104), faces=NO_DOWN, bone=F)
    g.box("belt", (-47, 19, 56), (-37, 19.6, 104), faces=("up",), bone=F)
    p.add("steel", "belt_frame")
    p.add("belt", "belt")
    for i in range(3):
        bone = f"crate{i}"
        z = 60.0 + i * 16
        g.bone(bone, pivot=(-42, 19.6, z), parent=F)
        g.box(f"crate{i}_box", (-46, 19.6, z), (-38, 26, z + 8), faces=NO_DOWN, bone=bone)
        p.add("crate", f"crate{i}_box")
    # The hall: two octagonal tiers under flared pagoda roofs (red at their eaves), cream walls, fieldstone quoins.
    cx, cz = HALL
    p.add("cream", ring(g, "hall_low", 38.0, 0, 30, cx, cz, sides=8, stagger="full", caps=()))
    roof1 = pagoda(g, "roof1", 45.0, 26.0, 30.0, 40.0, cx, cz)
    p.add("roof", roof1[0])
    p.add("eave", roof1[1])
    p.add("cream", ring(g, "hall_up", 24.0, 39, 56, cx, cz, sides=8, stagger="full", caps=()))
    roof2 = pagoda(g, "roof2", 31.0, 4.0, 55.0, 70.0, cx, cz)
    p.add("roof", roof2[0])
    p.add("eave", roof2[1])
    p.add("gold", ring(g, "hall_finial", 1.4, 69.5, 76, cx, cz, sides=8, stagger="full"))
    # The roller door on the hall's yard side, its corrugated awning and the red star over it.
    dx = cx + 38.0                                             # its east facet (the section's apothem out)
    g.box("hall_door", (dx - 0.6, DECK_Y, cz - 10), (dx + 1.2, 28, cz + 10), faces=("east", "north", "south", "up"), bone=F)
    g.box("door_awning", (dx - 1, 28.5, cz - 13), (dx + 9, 30, cz + 13), faces=ALL,
          rotation={"origin": (dx - 1, 30, cz), "axis": "z", "angle": -12}, bone=F)
    g.box("door_star", (dx + 0.2, 31, cz - 3.5), (dx + 0.6, 38, cz + 3.5), faces=("east",), bone=F)
    p.add("roller", "hall_door")
    p.add("corrugated", "door_awning")
    p.add("starplate", "door_star")
    # The yard's cradles (green steel, rubber saddles), the missile's bone.
    for i, h in enumerate(WF_CRADLES):
        z = WF_TAIL_Z - h
        g.box(f"cradle{i}", (WF_X - 11, DECK_Y, z - 3), (WF_X + 11, WF_CRADLE_TOP - 1, z + 3), faces=NO_DOWN, bone=F)
        g.box(f"saddle{i}", (WF_X - 7, WF_CRADLE_TOP - 1, z - 2.5), (WF_X + 7, WF_CRADLE_TOP, z + 2.5), faces=NO_DOWN, bone=F)
        for s in (-1, 1):
            g.box(f"chock{i}{s}", (WF_X + s * 9 - 2, WF_CRADLE_TOP - 1, z - 3), (WF_X + s * 9 + 2, WF_CRADLE_TOP + 3, z + 3), faces=NO_DOWN, bone=F)
            p.add("green", f"chock{i}{s}")
        p.add("green", f"cradle{i}")
        p.add("rubber", f"saddle{i}")
    g.bone("missile", pivot=(WF_X, WF_AXIS, WF_TAIL_Z), parent=F)
    # The gantry crane on its rail: bogies, a green lattice post, the red machinery house, the yellow boom out over the
    # yard, its trolley and the claw on its cable.
    g.bone("crane", pivot=(RAIL_X, 18, CRANE_PARK), parent=F)
    g.bone("cable", pivot=(WF_X, BOOM_Y - 3, CRANE_PARK), parent="crane")
    g.bone("claw", pivot=(WF_X, 50, CRANE_PARK), parent="crane")
    g.box("bogie", (RAIL_X - 7, 18, CRANE_PARK - 9), (RAIL_X + 7, 24, CRANE_PARK + 9), faces=NO_DOWN, bone="crane")
    for i, (x, z) in enumerate(((RAIL_X - 4, CRANE_PARK - 4), (RAIL_X + 4, CRANE_PARK - 4), (RAIL_X - 4, CRANE_PARK + 4), (RAIL_X + 4, CRANE_PARK + 4))):
        g.box(f"cpost{i}", (x - 1, 24, z - 1), (x + 1, BOOM_Y - 2, z + 1), faces=NO_DOWN, bone="crane")
        p.add("lattice", f"cpost{i}")
    for k in range(int((BOOM_Y - 26) // 12)):
        y = 30 + k * 12
        for n, a, b in ((f"ctie{k}n", (RAIL_X - 3, y, CRANE_PARK - 4.5), (RAIL_X + 3, y + 1, CRANE_PARK - 3.5)),
                        (f"ctie{k}s", (RAIL_X - 3, y, CRANE_PARK + 3.5), (RAIL_X + 3, y + 1, CRANE_PARK + 4.5)),
                        (f"ctie{k}w", (RAIL_X - 4.5, y + 0.5, CRANE_PARK - 3), (RAIL_X - 3.5, y + 1.5, CRANE_PARK + 3)),
                        (f"ctie{k}e", (RAIL_X + 3.5, y + 0.5, CRANE_PARK - 3), (RAIL_X + 4.5, y + 1.5, CRANE_PARK + 3))):
            g.box(n, a, b, faces=ALL, bone="crane")
            p.add("lattice", n)
    g.box("house", (RAIL_X - 6, BOOM_Y - 2, CRANE_PARK - 6), (RAIL_X + 8, BOOM_Y + 8, CRANE_PARK + 6), faces=ALL, bone="crane")
    g.box("boom", (WF_X - 6, BOOM_Y, CRANE_PARK - 2.5), (RAIL_X - 6, BOOM_Y + 4, CRANE_PARK + 2.5), faces=ALL, bone="crane")
    g.box("counter", (RAIL_X + 8, BOOM_Y, CRANE_PARK - 3.5), (RAIL_X + 14, BOOM_Y + 6, CRANE_PARK + 3.5), faces=ALL, bone="crane")
    g.box("ctrolley", (WF_X - 3.5, BOOM_Y - 3, CRANE_PARK - 3.5), (WF_X + 3.5, BOOM_Y, CRANE_PARK + 3.5), faces=ALL, bone="crane")
    p.add("redhouse", "house")
    p.add("yellow", "boom")
    p.add("dark", "counter")
    p.add("steel", "ctrolley")
    g.box("cable", (WF_X - 0.5, 51, CRANE_PARK - 0.5), (WF_X + 0.5, BOOM_Y - 3, CRANE_PARK + 0.5), faces=SIDES, bone="cable")
    g.box("claw_head", (WF_X - 3, 48, CRANE_PARK - 3), (WF_X + 3, 51, CRANE_PARK + 3), faces=ALL, bone="claw")
    for s in (-1, 1):
        g.box(f"claw_jaw{s}", (WF_X + s * 2.4 - 0.8, 42, CRANE_PARK - 2), (WF_X + s * 2.4 + 0.8, 48, CRANE_PARK + 2), faces=ALL, bone="claw")
        p.add("dark", f"claw_jaw{s}")
    p.add("chain", "cable")
    p.add("yellow", "claw_head")
    g.layout()
    tex, glow = paint_war_factory(g, p)
    g.save(tex, glow)
    # Working (12 s): the crane runs down the yard to each cradle in turn, lowering its claw at each, and back; the
    # crates ride the conveyor into the hall (each sliding out of sight into its door and starting again).
    work = Animation("working", 12.0, loop=True)
    a = (WF_TAIL_Z - WF_CRADLES[0]) - CRANE_PARK
    b = (WF_TAIL_Z - WF_CRADLES[1]) - CRANE_PARK
    work.move("crane", {0: (0, 0, 0), 2.5: (0, 0, a), 5.0: (0, 0, a), 7.5: (0, 0, b), 10.0: (0, 0, b), 12: (0, 0, 0)}, easing="easeInOutSine")
    keys = {0: 0, 2.7: 0, 3.4: 2.4, 4.4: 2.4, 4.9: 0, 7.7: 0, 8.4: 2.4, 9.4: 2.4, 9.9: 0, 12: 0}
    cable = BOOM_Y - 3 - 51
    work.move("claw", {t: (0, -v, 0) for t, v in keys.items()}, easing="easeInOutSine")
    work.scale("cable", {t: (1, (cable + v) / cable, 1) for t, v in keys.items()}, easing="easeInOutSine")
    for i in range(3):
        work.move(f"crate{i}", {0: (0, 0, 0), 12.0: (0, 0, 48 - 16 * i)})
    print(save_animations("war_factory", [work, rest_pose([work])]))
    print(f"war_factory: {len(g.boxes)} boxes, texture {g.tex_w}x{g.tex_h}")


def pagoda(g: GeoModel, name: str, r0: float, r1: float, y0: float, y1: float, cx: float, cz: float) -> tuple[list[str], list[str]]:
    """A flared octagonal pagoda roof from radius r0 at y0 up to r1 at y1, a pixel a ring, its curve concave (the eaves
    flare out); a red fascia ring under its eave. Returns its rings and the fascia."""
    rings = []
    n = int(round(y1 - y0))
    last = r0 + 0.2
    for i in range(n):
        s = (i + 0.5) / n
        r = r1 + (r0 - r1) * (1 - s) ** 1.8
        r = min(r, last - 0.12)
        rings += ring(g, f"{name}_{i}", round(r, 3), y0 + i, y0 + i + 1, cx, cz, sides=8, stagger="full", caps=("up", "down") if i == 0 else ("up",))
        last = r
    eave = ring(g, f"{name}_eave", round(r0 + 0.6, 3), y0 - 1.5, y0 + 0.5, cx, cz, sides=8, stagger="full", caps=("up", "down"))
    return rings, eave


def paint_war_factory(g: GeoModel, p: SS.Parts) -> tuple[Tex, Tex]:
    from gz import machine_kit as K  # noqa: PLC0415
    k = K.Kit(g, {}, seed=853, ao_reach=3.0, turned=True)
    W.round_faces(k.S, SS.ROUND, top=True, bottom=False, foot=True)
    S = k.S
    tex, glow = Tex(g.tex_w, g.tex_h), Tex(g.tex_w, g.tex_h)
    S.paint(tex, p["deck"], M.concrete(tone=0.6))
    S.overlay(tex, p["deck"], apron_star, only=("up",))
    S.paint(tex, p["concrete"], M.concrete(tone=0.55))
    S.paint(tex, p["steel"], M.steel(tone=0.5, rust=0.12))
    L._console(S, g, tex, "console")
    L._power_housing(S, g, tex, "e_housing", plug="south")
    S.paint(tex, "i_hatch", M.steel(tone=0.4, brushed=0.04, rust=0.1))
    S.overlay(tex, "i_hatch", lambda f, img: M.grid(f, img, every=2, depth=0.5), only=("up",))
    S.paint(tex, p["brick"], M.Material(ramp=SS.BRICK_RAMP, tone=0.58, mottle=0.05, grain=0.05, dither=0.7, despeckle=False))
    S.overlay(tex, p["brick"], bricks, only=SIDES)
    S.paint(tex, p["dark"], M.steel(tone=0.28, rust=0.1))
    S.paint(tex, p["glow"], M.Material(ramp=M.ORANGE_PAINT_RAMP, tone=0.85, edge_light=0.0))
    g.paint_all(glow, "furnace_mouth", lambda t, r, d: t.rect(r[0], r[1], r[2], r[3], FURNACE_GLOW))
    S.paint(tex, p["chimney"], M.painted(M.GREY_PAINT_RAMP, tone=0.6, wear=0.2, streaks=0.15))
    S.overlay(tex, p["chimney"], lambda f, img: M.paste(img, S.material_on(f, NS.RED), (np.floor(f.Y / 8) % 2) == 0) if f.d in SIDES else None)
    S.paint(tex, p["belt"], M.rubber(tone=0.38))
    S.overlay(tex, p["belt"], lambda f, img: M.darken(img, (np.floor(f.Z) % 3) == 0, 0.7), only=("up",))
    S.paint(tex, p["crate"], M.painted(NS.PLA_GREEN_RAMP, tone=0.55, wear=0.3, under=M.WOOD_RAMP[2:6]))
    S.paint(tex, p["cream"], NS.CREAM)
    S.overlay(tex, p["cream"], quoins, only=SIDES)
    S.paint(tex, p["roof"], NS.ROOF)
    S.paint(tex, p["eave"], NS.RED)
    S.paint(tex, p["gold"], NS.GOLD)
    S.paint(tex, p["roller"], M.galvanized(tone=0.5))
    S.overlay(tex, p["roller"], lambda f, img: M.darken(img, (np.floor(f.Y) % 2) == 0, 0.8), only=("east",))
    S.paint(tex, p["corrugated"], CORRUGATED)
    S.overlay(tex, p["corrugated"], corrugate, only=("up", "down"))
    S.paint(tex, p["starplate"], M.white_paint(tone=0.7))
    S.overlay(tex, p["starplate"], small_star, only=("east",))
    S.paint(tex, p["green"], NS.PLA_GREEN)
    S.paint(tex, p["rubber"], M.rubber(tone=0.45))
    S.paint(tex, p["lattice"], NS.PLA_GREEN)
    S.paint(tex, p["redhouse"], NS.RED)
    S.overlay(tex, p["redhouse"], lambda f, img: M.rivets(f, img, every=4, inset=1), only=SIDES)
    S.paint(tex, p["yellow"], YELLOW_BOOM)
    S.paint(tex, p["chain"], M.Material(ramp=M.BLACK_RAMP, tone=0.45, dither=0.3, edge_light=0.25))
    return tex, glow


def apron_star(f, img) -> None:
    """The big red star painted on the apron (left of the controller), and yellow lines down the yard's edges."""
    x, z = f.X + 40, f.Z
    ang = np.arctan2(x, -z)
    r = np.hypot(x, z)
    k = np.cos(5 * ang) * 0.5 + 0.5
    star = r < 3.0 + 3.6 * k ** 3
    img[star] = (178, 30, 22, 255)
    line = (np.abs(f.X - 9.5) < 0.6) | (np.abs(f.X - 54.5) < 0.6)
    img[line & (f.Z > 8) & (f.Z < 184)] = (200, 160, 40, 255)


def bricks(f, img) -> None:
    a, b = f.plane_uv()
    course = np.floor(f.Y / 3)
    joint = (np.abs(f.Y - course * 3) < 0.5) | (np.abs(((a + 3 * (course % 2)) % 6) - 0.25) < 0.5)
    M.darken(img, joint, 0.62)


def quoins(f, img) -> None:
    """Fieldstone quoins at the octagon's corners (the facets' ends), alternating long and short."""
    a, b = f.plane_uv()
    course = np.floor(f.Y / 5)
    w = np.where(course % 2 == 0, 5.0, 3.0)
    col = f.border["left"]
    colr = f.border["right"]
    q = (col < w) | (colr < w)
    stone = S_FIELDSTONE.render(f, 5)
    img[q] = stone[q]
    M.darken(img, q & ((np.abs(f.Y - course * 5) < 0.5) | (np.abs(col - w) < 0.5) | (np.abs(colr - w) < 0.5)), 0.6)


S_FIELDSTONE = NS.FIELDSTONE


def small_star(f, img) -> None:
    x, y = f.Z - HALL[1], f.Y - 34.5
    ang = np.arctan2(x, y)
    r = np.hypot(x, y)
    k = np.cos(5 * ang) * 0.5 + 0.5
    img[r < 1.2 + 2.1 * k ** 3] = (178, 30, 22, 255)


# =============================================================================================================================
# The controllers and spawners
# =============================================================================================================================
def rocket_placard(t: Tex) -> None:
    """The Arms Dealer's placard: a rocket."""
    t.rect(3, 11, 7, 2, P.OLIVE[4])
    t.px(10, 11, P.BLACK_PAINT[2])
    t.px(10, 12, P.BLACK_PAINT[2])
    t.px(2, 10, P.OLIVE[3])
    t.px(2, 13, P.OLIVE[3])


def blocks() -> None:
    side = M.to_tex(PN.face(M.painted(SS.TAN_RAMP, tone=0.6, wear=0.3), style="inset", seed=61))
    ST.controller("arms_dealer", ST.controller_front(rocket_placard), ST.controller_top(), side)
    side = M.to_tex(PN.face(M.painted(NS.PLA_GREEN_RAMP, tone=0.58, wear=0.3), style="inset", seed=67))
    ST.controller("war_factory", ST.controller_front(NS.star_placard), ST.controller_top(), side)


def arms_plan(t: Tex) -> None:
    for x in range(3, 14):
        t.px(x, 3, MT.BLUEPRINT[6])
        t.px(x, 11, MT.BLUEPRINT[6])
    for y in range(3, 12):
        t.px(3, y, MT.BLUEPRINT[6])
        t.px(13, y, MT.BLUEPRINT[6])
    for x in range(4, 13):
        t.px(x, 9, MT.BLUEPRINT[5])
    for x in range(5, 11):
        t.px(x, 6, (236, 236, 228, 255))
    t.px(5, 6, (90, 200, 70, 255))
    t.px(8, 3, P.HAZARD_YELLOW[5])


def war_plan(t: Tex) -> None:
    for (x, y) in ((4, 9), (5, 8), (6, 8), (7, 9), (7, 10), (6, 11), (5, 11), (4, 10)):
        t.px(x, y, MT.BLUEPRINT[6])
    for y in range(3, 13):
        t.px(11, y, (220, 222, 226, 255))
        t.px(13, y, MT.BLUEPRINT[5])
    t.px(11, 3, (190, 30, 22, 255))
    t.px(4, 4, (255, 150, 50, 255))
    t.px(8, 2, P.HAZARD_YELLOW[5])


def main() -> None:
    arms_dealer()
    war_factory()
    blocks()
    MT.structure_spawner("arms_dealer_spawner", arms_plan)
    MT.structure_spawner("war_factory_spawner", war_plan)


if __name__ == "__main__":
    main()
