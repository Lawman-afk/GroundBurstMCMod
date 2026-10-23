"""China's Nuclear Missile Silo (DESIGN.md §19.5, after C&C Generals): its formed model, its structure blocks, its
controller and the Nuclear Missile it fires.

The game's Nuclear Missile, as modelled here: not a silo dug in but an above-ground "coffin" (the Atlas-D's way): a long
hall, fieldstone below, grey walls between green steel pilasters with a row of windows, a brown pitched roof in four
sections with red ridges, gold finials and a red star on its white gables; the missile lies inside on its erector. Beside
the hall's front stands an octagonal control house under a conical roof with two antenna masts and a fan unit on the
hall's wall; at the launch end a green lattice tower with an umbilical arm. Fired, the roof sections fold open, the
erector raises the silver missile (a DF-5, its red bands and star) upright on its launch table beside the tower, the arm
swings in to it, and it lights.

The missile is not part of the model: the game draws it from its own model (tools/assets/missiles.py) at the erector's
"missile" bone (MissileCarrier). NuclearMissileBlockEntity has the same figures: where the raised missile's tail stands.

Pixels: x right, y up, z back from the middle of the controller's cell (its bottom at y 0); the controller is the
middle of the hall's front wall.

    tools\\.venv\\Scripts\\python.exe tools\\assets\\nuclear_silo.py
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
from gz import paths  # noqa: E402
from gz.geo import Animation, GeoModel, rest_pose, save_animations  # noqa: E402
from gz.model import write_json  # noqa: E402
from gz.tex import Tex  # noqa: E402
import launch as L  # noqa: E402
import multiblock_tools as MT  # noqa: E402
import scud_storm as SS  # noqa: E402
import structures as ST  # noqa: E402
import warheads as W  # noqa: E402

SIDES, ALL, NO_DOWN = SS.SIDES, SS.ALL, SS.NO_DOWN
F = "frame"
DECK_Y = 14

# --- the layout (NuclearMissileBlockEntity and GZSuperweapons have the same figures) -------------------------------------------
#: The hall: x -40..40 (walls a cell thick), z -8..232; inside x -24..24, z 8..216. Its walls two cells high.
HALL_X, HALL_Z0, HALL_Z1, WALL_TOP = 40.0, -8.0, 232.0, 32.0
#: The roof: eaves 2 px out from the walls, its ridge over the middle; four sections along the hall.
EAVE_X, RIDGE_Y = 42.0, 56.0
ROOF_Z = (-10.0, 51.0, 112.0, 173.0, 234.0)
PITCH = math.degrees(math.atan2(RIDGE_Y - WALL_TOP, EAVE_X))
SLOPE = math.hypot(RIDGE_Y - WALL_TOP, EAVE_X)
#: The erector: its hinge (z, y); the lying missile's tail on its axis; where the raised tail stands (the block entity's).
HINGE = (182.0, 22.0)
LYING_TAIL = (184.0, 32.0)
RAISED_TAIL = (192.0, 20.0)
#: The rams (one each side of the strongback): foot on the floor, lug on the strongback (along it from the hinge, below it).
RAM_X = 12.0
RAM_FOOT = (142.0, 15.0)
RAM_LUG = (16.0, -6.0)
#: The missile's girth (the DF-5's: its interstage and raceway stand a little proud), and where the clamps hold it.
MISSILE_R = 7.4
CLAMPS = (170.0, 128.0, 86.0)
#: The tower beside the launch end (a cell: x 40..56, z 184..200), nine cells tall; its arm's height.
TOWER = (40.0, 56.0, 184.0, 200.0)
TOWER_H = 144.0
ARM_Y = 120.0
#: The control house: an octagon beside the hall's front (cells x -88..-40, z -8..40).
HOUSE = (-64.0, 16.0)
#: The animations' timings (seconds): the roof opening, the erector raising, the arm swinging in.
ROOF_T, ERECT_T0, ERECT_T1, ARM_T1 = 2.0, 1.5, 5.5, 6.5
RAISE_SIGN, ROOF_SIGN, ARM_SIGN = 1.0, 1.0, 1.0

# --- materials ----------------------------------------------------------------------------------------------------------------
FIELDSTONE_RAMP = M.ramp("#2d2b28", "#3c3935", "#4d4943", "#5f5a52", "#716b62", "#847d73", "#978f84", "#aaa296", "#bcb4a8")
WALL_GREY_RAMP = M.ramp("#3c3d3b", "#4d4f4c", "#60625e", "#747672", "#888a85", "#9b9d98", "#aeb0ab", "#c0c2bd", "#d0d2cd")
ROOF_RAMP = M.ramp("#24150c", "#321e11", "#422816", "#53331d", "#653f25", "#784c2e", "#8b5a38", "#9e6944", "#b07a54")
PLA_GREEN_RAMP = M.ramp("#101b10", "#172717", "#1f341f", "#284228", "#315032", "#3b5f3c", "#476f48", "#558056", "#669166")
CREAM_RAMP = M.ramp("#4c4636", "#615a46", "#777057", "#8d8669", "#a29b7c", "#b5ae90", "#c7c0a4", "#d6d0b7", "#e3ddc8")

FIELDSTONE = M.Material(ramp=FIELDSTONE_RAMP, tone=0.55, mottle=0.08, grain=0.06, dither=0.9, edge_light=0.14, edge_dark=0.12, ao=0.6,
                        grime=0.12, dust=0.04, despeckle=False)
WALL_GREY = M.Material(ramp=WALL_GREY_RAMP, tone=0.6, mottle=0.04, grain=0.04, dither=0.7, edge_light=0.12, edge_dark=0.1, ao=0.6,
                       grime=0.14, streaks=0.14, falloff=0.04, despeckle=False)
ROOF = M.Material(ramp=ROOF_RAMP, tone=0.55, mottle=0.05, grain=0.04, dither=0.6, edge_light=0.16, edge_dark=0.12, ao=0.5, grime=0.08,
                  dust=0.05, streaks=0.05)
PLA_GREEN = M.painted(PLA_GREEN_RAMP, tone=0.58, wear=0.3, rust=0.12)
CREAM = M.Material(ramp=CREAM_RAMP, tone=0.6, mottle=0.05, grain=0.04, dither=0.7, edge_light=0.12, edge_dark=0.1, ao=0.6, grime=0.16,
                   streaks=0.12, falloff=0.04, despeckle=False)
#: The gables' white: no lips, grime or falloff (each gable is two leaned slabs, one a little in front of the other where
#: they cross: plain paint keeps the join out of sight).
WHITE_GABLE = M.white_paint(tone=0.7, grime=0.0, streaks=0.0, falloff=0.0, edge_light=0.0, edge_dark=0.0, ao=0.0, wear=0.0)
RED = M.painted(M.RED_PAINT_RAMP, tone=0.5, wear=0.2)
GOLD = M.Material(ramp=W.ramp9(P.BRASS), tone=0.68, edge_light=0.25, mottle=0.02)
CONCRETE = M.concrete(tone=0.6)
STEEL = M.steel(tone=0.5, rust=0.08)
DARK = M.steel(tone=0.3, rust=0.1)
BRIGHT = M.steel(tone=0.75, brushed=0.1)
STAR = ["....R....", "...RRR...", "...RRR...", "RRRRRRRRR", ".RRRRRRR.", "..RRRRR..", "..RRRRR..", ".RRR.RRR.", ".R.....R."]


def ring(g, name, r, y0, y1, cx, cz, bone=F, caps=("up",), sides=None, stagger=None):
    return SS.ring(g, name, r, y0, y1, cx, cz, bone, caps=caps, sides=sides, stagger=stagger)


# --- the model ----------------------------------------------------------------------------------------------------------------
def build_hall(g: GeoModel, p: SS.Parts) -> None:
    x, z0, z1 = HALL_X, HALL_Z0, HALL_Z1
    # The inside's floor; the walls: fieldstone up to the floor's height and a little over, grey above.
    g.box("floor", (-24, 0, 8), (24, DECK_Y, 216), faces=("up",), bone=F)
    for side, (a, b) in (("w", (-x, -24)), ("e", (24, x))):
        g.box(f"stone_{side}", (a, 0, z0), (b, 16, z1), faces=SIDES, bone=F)
        g.box(f"upper_{side}", (a, 16, z0), (b, WALL_TOP, z1), faces=("east", "west", "up"), bone=F)
        p.add("stone", f"stone_{side}")
        p.add("upper", f"upper_{side}")
    for end, (a, b) in (("front", (z0, 8)), ("back", (216, z1))):
        g.box(f"stone_{end}", (-24, 0, a), (24, 16, b), faces=("north", "south"), bone=F)
        g.box(f"upper_{end}", (-24, 16, a), (24, WALL_TOP, b), faces=("north", "south", "up"), bone=F)
        p.add("stone", f"stone_{end}")
        p.add("upper", f"upper_{end}")
    # Green steel pilasters up the side walls' outer faces, and at the corners.
    for i, z in enumerate((z0 + 1.5, 40.0, 88.0, 136.0, 184.0, z1 - 1.5)):
        for s, side in ((-1, "w"), (1, "e")):
            if s > 0 and TOWER[2] - 3 < z < TOWER[3] + 3:
                continue                                       # the tower stands against the wall there
            n = f"pilaster_{side}{i}"
            x0, x1 = (-x - 1.5, -x) if s < 0 else (x, x + 1.5)
            g.box(n, (x0, 0, z - 2.5), (x1, WALL_TOP, z + 2.5), faces=ALL, bone=F)
            p.add("pilaster", n)
    # The gables: each half a slab leaned to the roof's pitch, its foot hidden in the wall under it; the left half in front
    # (a little proud of the right, so the two never share a plane), the red star on it; a gold finial on top.
    for end, zf, d in (("front", z0, 1), ("back", z1, -1)):
        thick = 2.0
        for half, s, inset in (("l", -1, 0.0), ("r", 1, 0.35)):
            zz0 = zf + d * inset if d > 0 else zf - thick - inset
            zz1 = zz0 + thick
            n = f"gable_{end}_{half}"
            # Built lying along the slope from the eave (x = -40 or 40) up to the ridge, 26 px tall below its top edge.
            ex = s * 40.0
            top = WALL_TOP + 1.2
            if s < 0:
                g.box(n, (ex, top - 26, zz0), (ex + SLOPE - 1.5, top, zz1), faces=("north", "south", "up", "east"),
                      rotation={"origin": (ex, top, zz0), "axis": "z", "angle": PITCH}, bone=F)
            else:
                g.box(n, (ex - SLOPE + 1.5, top - 26, zz0), (ex, top, zz1), faces=("north", "south", "up", "west"),
                      rotation={"origin": (ex, top, zz0), "axis": "z", "angle": -PITCH}, bone=F)
            p.add("gable", n)
            if half == "l":
                p.add("gable_star_" + end, n)
        g.box(f"finial_{end}", (-1.5, RIDGE_Y - 0.5, zf - (2.5 if d > 0 else 0.5)), (1.5, RIDGE_Y + 3.5, zf + (0.5 if d > 0 else 2.5)),
              faces=ALL, bone=F)
        g.box(f"finial_tip_{end}", (-0.6, RIDGE_Y + 3.5, zf - (1.6 if d > 0 else -0.4)), (0.6, RIDGE_Y + 7, zf - (0.4 if d > 0 else -1.6)),
              faces=NO_DOWN, bone=F)
        p.add("gold", f"finial_{end}", f"finial_tip_{end}")
    # The controller's panel on the front wall; the ports' housings on the side walls; a fan unit on the left wall.
    g.box("panel", (-6, 4, z0 - 1.5), (6, 22, z0), faces=("north", "east", "west", "up", "down"), bone=F)
    g.box("e_housing", (x, 3, 27), (x + 5, 14, 37), faces=("north", "south", "east", "up", "down"), bone=F)
    g.box("i_housing", (-x - 5, 3, 59), (-x, 14, 69), faces=("north", "south", "west", "up", "down"), bone=F)
    g.box("fan", (-x - 6, 18, 98), (-x, 29, 112), faces=("north", "south", "west", "up", "down"), bone=F)
    g.box("fan_duct", (-x - 3, 29, 102), (-x, 31.5, 108), faces=("north", "south", "west", "up"), bone=F)
    p.add("olive", "i_housing", "fan", "fan_duct")


def build_roof(g: GeoModel, p: SS.Parts) -> None:
    """Four sections, each two halves hinged at their eaves (their bones fold them up to stand open)."""
    for i in range(4):
        za, zb = ROOF_Z[i], ROOF_Z[i + 1]
        for half, s in (("l", -1), ("r", 1)):
            bone = f"roof{i}{half}"
            g.bone(bone, pivot=(s * EAVE_X, WALL_TOP, (za + zb) / 2), parent=F)
            ends = tuple(d for d, at in (("north", za), ("south", zb)) if at in (ROOF_Z[0], ROOF_Z[-1]))
            n = f"roofpanel{i}{half}"
            if s < 0:
                g.box(n, (-EAVE_X, WALL_TOP, za), (-EAVE_X + SLOPE, WALL_TOP + 2, zb), faces=("up", "down", "west", "east") + ends,
                      rotation={"origin": (-EAVE_X, WALL_TOP, za), "axis": "z", "angle": PITCH}, bone=bone)
            else:
                g.box(n, (EAVE_X - SLOPE, WALL_TOP, za), (EAVE_X, WALL_TOP + 2, zb), faces=("up", "down", "west", "east") + ends,
                      rotation={"origin": (EAVE_X, WALL_TOP, za), "axis": "z", "angle": -PITCH}, bone=bone)
            p.add("roof", n)
            if s < 0:                                          # the red ridge cap rides on the left halves
                cap = f"ridge{i}"
                g.box(cap, (-EAVE_X + SLOPE - 3, WALL_TOP + 1.5, za), (-EAVE_X + SLOPE + 0.6, WALL_TOP + 3.2, zb), faces=("up", "east") + ends,
                      rotation={"origin": (-EAVE_X, WALL_TOP, za), "axis": "z", "angle": PITCH}, bone=bone)
                p.add("ridge", cap)


def build_erector(g: GeoModel, p: SS.Parts) -> None:
    hz, hy = HINGE
    tz, ty = LYING_TAIL
    g.bone("erector", pivot=(0, hy, hz), parent=F)
    g.bone("missile", pivot=(0, ty, tz), parent="erector")
    # The launch table under the raised missile's tail, its hinge's cheeks and pin.
    g.box("table", (-11, DECK_Y, 184), (11, 19.4, 202), faces=NO_DOWN, bone=F)
    for s in (-1, 1):
        g.box(f"cheek{s}", (s * 9 - 1, 14, hz - 4), (s * 9 + 1, 25, hz + 4), faces=ALL, bone=F)
        p.add("table", f"cheek{s}")
    g.box("pin", (-8, hy - 1.5, hz - 1.5), (8, hy + 1.5, hz + 1.5), faces=("north", "south", "up", "down"), bone=F)
    p.add("table", "table")
    p.add("bright", "pin")
    # The strongback: a box girder under the missile, its knuckle on the pin, the clamps round the missile, the plate its
    # tail stands on once raised, the rams' lugs.
    # A fork, not a beam under it: two box rails beside the missile (raised, the missile stands between them, not behind
    # a girder), joined at the hinge's knuckle and by the cradles.
    g.box("knuckle", (-11, hy - 4, hz - 4), (11, hy + 3, hz + 4), faces=ALL, bone="erector")
    for s in (-1, 1):
        g.box(f"rail{s}", (s * 10.6 - (2 if s > 0 else 0), 22, 60), (s * 10.6 + (2 if s < 0 else 0), 28, hz - 4),
              faces=("north", "east", "west", "up", "down"), bone="erector")
        p.add("erector", f"rail{s}")
    g.box("tailplate", (-7.5, 21, tz + 0.4), (7.5, 42, tz + 1.4), faces=ALL, bone="erector")
    p.add("erector", "knuckle")
    p.add("plate", "tailplate")
    for i, cz in enumerate(CLAMPS):
        g.box(f"cross{i}", (-8.6, 22, cz - 2), (8.6, 24.6, cz + 2), faces=("north", "south", "up", "down"), bone="erector")
        p.add("erector", f"cross{i}")
        g.box(f"clampbase{i}", (-5.5, 24.6, cz - 1.6), (5.5, 25.2, cz + 1.6), faces=("north", "south", "up"), bone="erector")
        g.box(f"strap{i}", (-10, ty + MISSILE_R + 0.2, cz - 1.5), (10, ty + MISSILE_R + 1.2, cz + 1.5), faces=ALL, bone="erector")
        g.box(f"strapside{i}w", (-10, 28, cz - 1.5), (-8.4, ty + MISSILE_R + 0.2, cz + 1.5), faces=("north", "south", "west"), bone="erector")
        g.box(f"strapside{i}e", (8.4, 28, cz - 1.5), (10, ty + MISSILE_R + 0.2, cz + 1.5), faces=("north", "south", "east"), bone="erector")
        p.add("rubber", f"clampbase{i}")
        p.add("clamp", f"strap{i}", f"strapside{i}w", f"strapside{i}e")
    for s in (-1, 1):
        lug_z, lug_y = hz - RAM_LUG[0], hy + RAM_LUG[1]
        g.box(f"lug{s}", (min(s * 8.6, s * (RAM_X + 1.5)), lug_y - 1.5, lug_z - 2), (max(s * 8.6, s * (RAM_X + 1.5)), 22.5, lug_z + 2),
              faces=ALL, bone="erector")
        p.add("erector", f"lug{s}")
    # The rams, each side: its cylinder from its foot on the floor toward the lug, the rod sliding out of it.
    fz, fy = RAM_FOOT
    lz, ly = hz - RAM_LUG[0], hy + RAM_LUG[1]
    length = math.dist((fz, fy), (lz, ly))
    dz, dy = (lz - fz) / length, (ly - fy) / length
    for s in (-1, 1):
        ram, rod = f"ram{'we'[s > 0]}", f"rod{'we'[s > 0]}"
        g.bone(ram, pivot=(s * RAM_X, fy, fz), parent=F)
        g.bone(rod, pivot=(s * RAM_X, fy, fz), parent=ram)
        g.box(f"foot{s}", (s * RAM_X - 2.5, DECK_Y, fz - 3), (s * RAM_X + 2.5, 17, fz + 3), faces=NO_DOWN, bone=F)
        p.add("table", f"foot{s}")
        SS.strut(g, f"cyl{s}", (s * RAM_X, fy, fz), (s * RAM_X, fy + dy * 22, fz + dz * 22), 3.4, ram)
        SS.strut(g, f"piston{s}", (s * RAM_X, fy + dy * 4, fz + dz * 4), (s * RAM_X, ly, lz), 1.8, rod)
        p.add("cylinder", f"cyl{s}")
        p.add("bright", f"piston{s}")


def build_tower(g: GeoModel, p: SS.Parts) -> None:
    """A green lattice tower: four corner posts, an X brace on each face of each level, a platform near the top, a red
    lamp on its crown and the umbilical arm (its bone swings it in to the raised missile)."""
    x0, x1, z0, z1 = TOWER
    for i, (x, z) in enumerate(((x0 + 1, z0 + 1), (x1 - 1, z0 + 1), (x0 + 1, z1 - 1), (x1 - 1, z1 - 1))):
        g.box(f"post{i}", (x - 1, 0, z - 1), (x + 1, TOWER_H, z + 1), faces=NO_DOWN, bone=F)
        p.add("lattice", f"post{i}")
    level = 16.0
    for k in range(int(TOWER_H // level)):
        y0, y1 = k * level, (k + 1) * level
        g.box(f"tie{k}n", (x0 + 2, y1 - 1, z0 + 0.5), (x1 - 2, y1, z0 + 1.5), faces=ALL, bone=F)
        g.box(f"tie{k}s", (x0 + 2, y1 - 1, z1 - 1.5), (x1 - 2, y1, z1 - 0.5), faces=ALL, bone=F)
        g.box(f"tie{k}w", (x0 + 0.5, y1 - 1, z0 + 2), (x0 + 1.5, y1, z1 - 2), faces=ALL, bone=F)
        g.box(f"tie{k}e", (x1 - 1.5, y1 - 1, z0 + 2), (x1 - 0.5, y1, z1 - 2), faces=ALL, bone=F)
        p.add("lattice", f"tie{k}n", f"tie{k}s", f"tie{k}w", f"tie{k}e")
        # A brace across the west and east faces (the north and south ones would show as the same line from the side).
        for side, xx in (("w", x0 + 1), ("e", x1 - 1)):
            n = f"brace{k}{side}"
            SS.strut(g, n, (xx, y0 + 1, z0 + 2), (xx, y1 - 1, z1 - 2), 0.8, F)
            p.add("lattice", n)
    g.box("platform", (x0 - 3, 108, z0 - 3), (x1 + 3, 110, z1 + 3), faces=ALL, bone=F)
    g.bone("lamp", pivot=((x0 + x1) / 2, TOWER_H, (z0 + z1) / 2), parent=F)
    g.box("lamp", ((x0 + x1) / 2 - 1.5, TOWER_H, (z0 + z1) / 2 - 1.5), ((x0 + x1) / 2 + 1.5, TOWER_H + 3, (z0 + z1) / 2 + 1.5), faces=NO_DOWN, bone="lamp")
    g.box("lamp_base", ((x0 + x1) / 2 - 2, TOWER_H - 0.5, (z0 + z1) / 2 - 2), ((x0 + x1) / 2 + 2, TOWER_H, (z0 + z1) / 2 + 2), faces=NO_DOWN, bone=F)
    p.add("table", "lamp_base")
    p.add("grating", "platform")
    p.add("lamp", "lamp")
    # The umbilical arm: hinged just off the tower's side toward the hall, at rest along the hall (toward the front); its
    # bone swings it a quarter turn in to the raised missile, its pad against the missile's skin.
    ax, az, length = x0 - 2.5, (z0 + z1) / 2, 28.0
    g.box("arm_hinge", (ax - 1.5, ARM_Y - 4, az - 1.5), (x0, ARM_Y + 3, az + 1.5), faces=ALL, bone=F)
    p.add("lattice", "arm_hinge")
    g.bone("arm", pivot=(ax, ARM_Y, az), parent=F)
    g.box("arm_beam", (ax - 1.5, ARM_Y - 2, az - length), (ax + 1.5, ARM_Y + 1, az - 1.5), faces=ALL, bone="arm")
    g.box("arm_pad", (ax - 3, ARM_Y - 4, az - length - 2.6), (ax + 3, ARM_Y + 3, az - length), faces=ALL, bone="arm")
    g.box("arm_hose", (ax - 0.6, ARM_Y - 3.3, az - length + 2), (ax + 0.6, ARM_Y - 2, az - 3), faces=("east", "west", "down", "north"),
          bone="arm")
    p.add("lattice", "arm_beam")
    p.add("plate", "arm_pad")
    p.add("hose", "arm_hose")


def build_house(g: GeoModel, p: SS.Parts) -> None:
    """The octagonal control house: a fieldstone footing, cream walls, a conical roof of brown tiles, two antenna masts."""
    cx, cz = HOUSE
    p.add("stone_round", ring(g, "house_foot", 21.5, 0, 8, cx, cz, sides=8, stagger="full", caps=("up",)))
    p.add("house", ring(g, "house", 20.0, 6, 30, cx, cz, sides=8, stagger="full", caps=()))
    # The roof's eave (a broad ring over the walls), then the cone a pixel at a time from a real profile.
    p.add("roof", ring(g, "house_eave", 22.5, 29, 30.5, cx, cz, sides=8, stagger="full", caps=("up", "down")))
    cone = []
    top = 30.5
    radii, last = [], 21.0
    for i in range(16):
        r = min(21.0 * (1 - (i + 0.5) / 16), last - 0.12)
        if r < 0.9:
            break
        radii.append(r)
        last = r
    for i, r in enumerate(radii):
        cone += ring(g, f"house_roof{i}", round(r, 3), top + i, top + i + 1, cx, cz, sides=8, stagger="full")
    top += len(radii)
    p.add("roof", cone)
    p.add("gold", ring(g, "house_finial", 0.8, top - 0.6, top + 3, cx, cz, sides=4))
    for i, (dx, h) in enumerate(((-9.0, 34.0), (9.0, 28.0))):
        g.box(f"aerial{i}", (cx + dx - 0.6, 40, cz - 0.6), (cx + dx + 0.6, 40 + h, cz + 0.6), faces=NO_DOWN, bone=F)
        g.box(f"aerial_bar{i}", (cx + dx - 4, 40 + h - 6, cz - 0.4), (cx + dx + 4, 40 + h - 5, cz + 0.4), faces=ALL, bone=F)
        p.add("mast", f"aerial{i}", f"aerial_bar{i}")


def build() -> tuple[GeoModel, SS.Parts]:
    SS.ROUND.clear()
    g = GeoModel("nuclear_missile_silo", tex_size=(1024, 1024))
    g.open_ok = set()
    g.bone(F)
    p = SS.Parts()
    build_hall(g, p)
    build_roof(g, p)
    build_erector(g, p)
    build_tower(g, p)
    build_house(g, p)
    return g, p


# --- painting -----------------------------------------------------------------------------------------------------------------
def paint_model(g: GeoModel, p: SS.Parts) -> tuple[Tex, Tex]:
    from gz import machine_kit as K  # noqa: PLC0415
    k = K.Kit(g, {}, seed=733, ao_reach=3.0, turned=True)
    W.round_faces(k.S, SS.ROUND, top=True, bottom=False, foot=True)
    S = k.S
    tex = Tex(g.tex_w, g.tex_h)
    glow = Tex(g.tex_w, g.tex_h)
    S.paint(tex, "floor", CONCRETE)
    S.overlay(tex, "floor", floor_marks, only=("up",))
    S.paint(tex, p["stone"] + p["stone_round"], FIELDSTONE)
    S.overlay(tex, p["stone"] + p["stone_round"], fieldstones)
    S.paint(tex, p["upper"], WALL_GREY)
    S.overlay(tex, [n for n in p["upper"] if n.endswith(("_w", "_e"))], windows, only=("east", "west"))
    S.paint(tex, p["pilaster"], PLA_GREEN)
    S.overlay(tex, p["pilaster"], lambda f, img: M.rivets(f, img, every=4, inset=1, axis="v"), only=SIDES)
    S.paint(tex, p["gable"], WHITE_GABLE)
    for end in ("front", "back"):
        S.overlay(tex, p["gable_star_" + end], star(end), only=("north", "south"))
    S.paint(tex, p["gold"], GOLD)
    S.paint(tex, p["roof"], ROOF)
    S.overlay(tex, p["roof"], tiles, only=("up",))
    S.paint(tex, p["ridge"], RED)
    L._console(S, g, tex, "panel")
    L._power_housing(S, g, tex, "e_housing", plug="west")
    S.paint(tex, p["olive"], M.painted(M.OLIVE_RAMP, tone=0.5, wear=0.35, rust=0.1))
    g.paint_all(tex, "fan", fan_grille)
    S.paint(tex, p["table"], DARK)
    S.paint(tex, p["erector"] + p["clamp"], PLA_GREEN)
    S.overlay(tex, ["rail-1", "rail1"], lambda f, img: M.rivets(f, img, every=6, inset=1), only=("east", "west"))
    S.paint(tex, p["plate"], M.stripes(width=2, wear=0.35))
    S.paint(tex, p["rubber"], M.rubber(tone=0.45))
    S.paint(tex, p["bright"], BRIGHT)
    S.paint(tex, p["cylinder"], M.painted(M.BLACK_RAMP, tone=0.5, wear=0.2))
    S.paint(tex, p["lattice"], PLA_GREEN)
    S.paint(tex, p["grating"], M.galvanized(tone=0.55))
    S.overlay(tex, p["grating"], lambda f, img: M.grid(f, img, every=2, depth=0.5), only=("up", "down"))
    S.paint(tex, p["hose"], M.rubber(tone=0.4))
    S.paint(tex, p["lamp"], M.Material(ramp=W.ramp9(P.INDICATOR_RED), tone=0.7))
    g.paint_all(glow, "lamp", lambda t, r, d: t.rect(r[0], r[1], r[2], r[3], (255, 70, 50, 255)))
    S.paint(tex, p["house"], CREAM)
    S.overlay(tex, p["house"], house_openings)
    S.paint(tex, p["mast"], M.galvanized(tone=0.5))
    return tex, glow


def floor_marks(f, img) -> None:
    """A yellow line round the erector's sweep and its launch table; oil under the rams."""
    line = (np.abs(np.abs(f.X) - 18) < 0.6) & (f.Z > 40) & (f.Z < 182)
    M.paste(img, np.broadcast_to(np.array([200, 160, 40, 255], dtype=np.uint8), img.shape).copy(), line & (M.white(f.X, f.Y, f.Z, 5) > 0.2))
    stain = M.fbm(f.X, f.Y, f.Z, 4.0, 9, 3)
    for s in (-1, 1):
        d = np.hypot(f.X - s * RAM_X, f.Z - RAM_FOOT[0])
        M.darken(img, (d < 7) & (stain > 0.5 + d / 20), 0.6)


def fieldstones(f, img) -> None:
    """Dressed fieldstone: irregular stones in roughly level courses, dark mortar between them."""
    if not f.vertical:
        return
    a, b = f.plane_uv()
    course = np.floor(f.Y / 4)
    offset = M.white(course, course * 0, course * 0, 17) * 7
    stone = np.floor((a + offset) / (5 + 2 * M.white(course, course, course * 0, 19)))
    edge_y = (f.Y - course * 4) < 0.6
    cell = (a + offset) / (5 + 2 * M.white(course, course, course * 0, 19))
    edge_x = (cell - np.floor(cell)) < 0.12
    M.darken(img, edge_y | edge_x, 0.55)
    tone = M.white(stone, course, stone * 0, 23)
    M.darken(img, ~(edge_y | edge_x) & (tone < 0.3), 0.85)
    M.lighten(img, ~(edge_y | edge_x) & (tone > 0.75), 1.1, 4)


def windows(f, img) -> None:
    """A row of tall windows between the pilasters, dark glass in steel frames."""
    a, b = f.plane_uv()
    zc = ((f.Z - 16) % 48) - 24
    win = (np.abs(zc) < 5) & (f.Y > 19) & (f.Y < 29)
    frame = (np.abs(zc) < 6) & (f.Y > 18) & (f.Y < 30) & ~win
    img[win] = (28, 34, 40, 255)
    M.lighten(img, win & (f.Y > 27), 1.6, 18)
    img[frame] = (70, 78, 70, 255)
    mull = win & (np.abs(zc) < 0.5)
    img[mull] = (70, 78, 70, 255)


def star(end: str):
    """The red star on a gable's white face, under the ridge."""
    def fn(f, img):
        if (end == "front" and f.d != "north") or (end == "back" and f.d != "south"):
            return
        col = np.floor(f.X + 4.5).astype(int)
        if end == "back":
            col = 8 - col
        row = np.floor(RIDGE_Y - 7 - f.Y).astype(int)
        for j, line in enumerate(STAR):
            for i, ch in enumerate(line):
                if ch == "R":
                    img[(row == j) & (col == i)] = (190, 30, 22, 255)
    return fn


def tiles(f, img) -> None:
    """Roof tiles in rows down the slope (barrel tiles: a dark gap every 3 px across, every 4 along)."""
    a, b = f.plane_uv()
    M.darken(img, (np.floor(a) % 3) == 0, 0.78)
    M.darken(img, (np.floor(b) % 4) == 0, 0.7)


def fan_grille(t: Tex, r, d) -> None:
    x, y, w, h = r
    if d == "west" and w > 6:
        t.disc(x + w / 2, y + h / 2, min(w, h) / 2 - 1, (34, 36, 34, 255))
        for k in range(3):
            t.line(int(x + w / 2), int(y + h / 2), int(x + w / 2 + (h / 2 - 2) * math.cos(k * 2.1)),
                   int(y + h / 2 + (h / 2 - 2) * math.sin(k * 2.1)), (110, 112, 100, 255))


def house_openings(f, img) -> None:
    """The control house's door (toward the front) and its windows (round the rest)."""
    if not f.vertical:
        return
    cx, cz = HOUSE
    ang = (np.degrees(np.arctan2(f.X - cx, -(f.Z - cz))) + 360) % 360
    facet = np.round(ang / 45) * 45
    across = np.radians(ang - facet) * 20
    door = (facet % 360 == 0) & (np.abs(across) < 3.5) & (f.Y < 22)
    win = (facet % 360 != 0) & (np.abs(across) < 3) & (f.Y > 16) & (f.Y < 24)
    img[door] = (40, 30, 22, 255)
    img[win] = (30, 38, 46, 255)
    M.lighten(img, win & (f.Y > 22), 1.6, 18)


# --- animations ----------------------------------------------------------------------------------------------------------------
def erector_alpha(t: float) -> float:
    s = min(1.0, max(0.0, (t - ERECT_T0) / (ERECT_T1 - ERECT_T0)))
    return 90.0 * s * s * (3 - 2 * s)


def ram_pose(alpha: float) -> tuple[float, float]:
    """For the erector raised {alpha} degrees: each ram's turn (degrees, + raising its end) and its rod's travel."""
    hz, hy = HINGE
    fz, fy = RAM_FOOT
    au, ay = RAM_LUG

    def lug(a: float):
        c, s = math.cos(math.radians(a)), math.sin(math.radians(a))
        u, y = au * c - ay * s, au * s + ay * c          # along the strongback (toward the nose: -z) and up
        return hz - u, hy + y
    z0, y0 = lug(0)
    z1, y1 = lug(alpha)
    e0 = math.degrees(math.atan2(y0 - fy, z0 - fz))
    e1 = math.degrees(math.atan2(y1 - fy, z1 - fz))
    return e1 - e0, math.dist((fz, fy), (z1, y1)) - math.dist((fz, fy), (z0, y0))


def sequence(name: str, raising: bool) -> Animation:
    """The roof folding open, the erector raising the missile and the tower's arm swinging in (or all of it backwards)."""
    T = ARM_T1
    a = Animation(name, T, loop="hold_on_last_frame")
    steps = int(T * 4)
    hz, hy = HINGE
    fz, fy = RAM_FOOT
    lz0, ly0 = hz - RAM_LUG[0], hy + RAM_LUG[1]
    length = math.dist((fz, fy), (lz0, ly0))
    dz, dy = (lz0 - fz) / length, (ly0 - fy) / length
    roof = {i: {} for i in range(4)}
    erect, ram, rod, arm = {}, {}, {}, {}
    for k in range(steps + 1):
        t = T * k / steps
        tt = t if raising else T - t                             # lowering runs the raising backwards
        for i in range(4):                                      # the sections open one after another, a little apart
            ri = min(1.0, max(0.0, (tt - 0.15 * i) / (ROOF_T - 0.45)))
            roof[i][t] = (90.0 - PITCH) * ri * ri * (3 - 2 * ri)    # folded up from its pitch to stand upright
        alpha = erector_alpha(tt)
        turn, travel = ram_pose(alpha)
        erect[t] = (RAISE_SIGN * alpha, 0, 0)
        ram[t] = (RAISE_SIGN * turn, 0, 0)
        rod[t] = (0, dy * travel, dz * travel)
        sa = min(1.0, max(0.0, (tt - ERECT_T1) / (ARM_T1 - ERECT_T1)))
        arm[t] = (0, ARM_SIGN * 90.0 * sa * sa * (3 - 2 * sa), 0)
    for i in range(4):
        a.rotate(f"roof{i}l", {t: (0, 0, ROOF_SIGN * v) for t, v in roof[i].items()})
        a.rotate(f"roof{i}r", {t: (0, 0, -ROOF_SIGN * v) for t, v in roof[i].items()})
    a.rotate("erector", erect)
    for side in ("w", "e"):
        a.rotate(f"ram{side}", ram)
        a.move(f"rod{side}", rod)
    a.rotate("arm", arm)
    return a


def animations() -> list[Animation]:
    raise_ = sequence("raise", True)
    lower = sequence("lower", False)
    lowered = rest_pose([raise_], "lowered")
    # The red lamp on the tower's crown blinks, always (slowly at rest, quickly while it charges).
    blink = {0: (1, 1, 1), 0.99: (1, 1, 1), 1.0: (0.01, 0.01, 0.01), 2.0: (0.01, 0.01, 0.01)}
    ambient = Animation("ambient", 2.0, loop=True).scale("lamp", blink)
    working = Animation("working", 1.0, loop=True).scale("lamp", {t / 2: v for t, v in blink.items()})
    idle = rest_pose([working], "idle")
    return [raise_, lower, lowered, ambient, working, idle]


# --- blocks, the controller, the spawner, the ammunition ----------------------------------------------------------------------
def fieldstone_block() -> Tex:
    """Fieldstone Wall: irregular grey stones in courses, dark mortar."""
    img = M.tile(M.Material(ramp=FIELDSTONE_RAMP, tone=0.6, mottle=0.08, grain=0.06, dither=0.7, scale=0.5, despeckle=False), seed=41)
    col, row = M.grid_xy()
    mortar = np.zeros((16, 16), dtype=bool)
    for r0, cuts in ((0, (3, 9, 14)), (4, (0, 6, 12)), (8, (2, 7, 11)), (12, (4, 10, 15))):
        mortar |= row == r0
        for c in cuts:
            mortar |= (col == c) & (row > r0) & (row < r0 + 4)
    M.darken(img, mortar, 0.5)
    M.bevel(img, ~mortar, 1.12, 0.82, 3)
    return M.to_tex(img)


def missile_erector_block() -> Tex:
    """Missile Erector: green steel girder with a black hydraulic cylinder and a hazard-striped end."""
    img = PN.face(M.painted(PLA_GREEN_RAMP, tone=0.58, wear=0.3, rust=0.12), style="inset", seed=43)
    PN.stripes(img, PN.area(12, 1, 3, 14), seed=43)
    t = M.to_tex(img)
    t.rect(2, 6, 9, 4, P.BLACK_PAINT[2])
    t.hline(2, 6, 9, P.BLACK_PAINT[4])
    t.rect(1, 7, 3, 2, P.STEEL[5])
    return t


def star_placard(t: Tex) -> None:
    """The controller's placard: a red star."""
    t.rect(3, 9, 7, 6, P.WHITE_CERAMIC[5])
    for (x, y) in ((6, 9), (5, 10), (6, 10), (7, 10), (3, 11), (4, 11), (5, 11), (6, 11), (7, 11), (8, 11), (9, 11), (5, 12),
                   (6, 12), (7, 12), (4, 13), (5, 13), (7, 13), (8, 13), (4, 14), (8, 14)):
        t.px(x, y, (190, 30, 22, 255))


def blocks() -> None:
    ST.part_block("fieldstone_wall", fieldstone_block())
    ST.part_block("missile_erector", missile_erector_block())
    side = M.to_tex(PN.face(M.painted(PLA_GREEN_RAMP, tone=0.58, wear=0.3), style="inset", seed=47))
    ST.controller("nuclear_missile_silo", ST.controller_front(star_placard), ST.controller_top(), side)


def spawner_plan(t: Tex) -> None:
    """The Nuclear Missile Silo from above: the long hall with the missile in it, the house, the tower."""
    for y in range(2, 14):
        t.px(6, y, MT.BLUEPRINT[6])
        t.px(10, y, MT.BLUEPRINT[6])
    for x in range(6, 11):
        t.px(x, 2, MT.BLUEPRINT[6])
        t.px(x, 13, MT.BLUEPRINT[6])
    for y in range(4, 12):
        t.px(8, y, (220, 222, 226, 255))
    t.px(8, 4, (190, 30, 22, 255))
    for (x, y) in ((3, 3), (4, 3), (3, 4), (4, 4)):
        t.px(x, y, MT.BLUEPRINT[5])
    t.px(12, 11, (80, 140, 80, 255))
    t.px(8, 1, P.HAZARD_YELLOW[5])


def ammo() -> None:
    """The Nuclear Missile item: the missile's own model (tools/assets/missiles.py)."""
    write_json(paths.ASSETS / "items" / "nuclear_missile.json",
               {"model": {"type": "minecraft:model", "model": "groundzero:item/missile/nuclear_missile_warhead"}})


def main() -> None:
    g, p = build()
    g.layout()
    tex, glow = paint_model(g, p)
    g.save(tex, glow)
    print(save_animations("nuclear_missile_silo", animations()))
    blocks()
    MT.structure_spawner("nuclear_missile_silo_spawner", spawner_plan)
    ammo()
    print(f"nuclear_missile_silo: {len(g.boxes)} boxes, texture {g.tex_w}x{g.tex_h}")


if __name__ == "__main__":
    main()
