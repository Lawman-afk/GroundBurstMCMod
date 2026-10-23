"""The GLA's SCUD Storm (DESIGN.md §19.5, after C&C Generals): its formed model, its structure blocks, its controller and
the Toxin SCUD it fires.

The game's SCUD Storm, as modelled here: a big square pad of sand-coloured concrete; nine SCUDs (white bodies, toxin-green
noses and fins) lying like the spokes of a wheel, tails in, each on its own erector round a green tank of toxin with a
biohazard mark on a rusty base; a low adobe wall down the left side and across the back with a watchtower at each end
(one under an orange dome, one carrying an antenna and a little radar); a guard hut flying the GLA's flag; crates and
drums; steam rising from vents in the pad. Fired, the erectors raise the SCUDs upright on their hydraulic rams.

The SCUDs are not part of the model: the game draws each from its own missile model (tools/assets/missiles.py) at its
erector's "scud<k>" bone, lying on the erector's cradles and raised with it (MissileCarrier). Each erector is built once
pointing to the front and turned to its bearing by its bone; ScudStormBlockEntity has the same figures (the pad's middle,
the bearings, where a raised SCUD's tail stands).

Pixels: x right, y up, z back from the middle of the controller's cell (its bottom at y 0), as for every structure.

    tools\\.venv\\Scripts\\python.exe tools\\assets\\scud_storm.py
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
import structures as ST  # noqa: E402
import warheads as W  # noqa: E402

SIDES = ("north", "south", "east", "west")
ALL = SIDES + ("up", "down")
NO_DOWN = SIDES + ("up",)
F = "frame"
DECK_Y = 14

# --- the layout (ScudStormBlockEntity and GZSuperweapons have the same figures) -----------------------------------------------
#: The pad: 13 x 13 cells, the controller's cell at the front middle; its middle six cells back.
PAD = (-104.0, 104.0, -8.0, 200.0)
CX, CZ = 0.0, 96.0
#: The wall: the column left of the pad and the row behind it; the watchtowers at its two ends.
WALL_H = 28.0
TOWER_H = 56.0
#: The erectors' bearings, degrees clockwise from the front.
FIRST_BEARING, BEARING_STEP, COUNT = 20.0, 40.0, 9
#: An erector, built pointing to the front (u: pixels out from the pad's middle, y up): the boom's hinge, a lying SCUD's
#: tail on its axis, the ram's foot on the deck and where its rod meets the boom (along the boom from the hinge, below it).
HINGE = (30.0, 26.0)
TAIL = (32.0, 30.0)
RAM_FOOT = (60.0, 15.0)
RAM_ARM = (10.0, -6.0)
#: The SCUD's girth on its cradles, and where along it (from its tail) they hold it.
SCUD_R = 4.0
CRADLES = (22.0, 48.0)
#: How long the erectors take to raise or lower the SCUDs, seconds; which way a positive turn about x raises them.
RAISE_T = 3.0
RAISE_SIGN = 1.0
YAW_SIGN = -1.0


def z_of(u: float) -> float:
    """An erector's pixels out from the pad's middle, as z (it is built pointing to the front, -z)."""
    return CZ - u


# --- materials ----------------------------------------------------------------------------------------------------------------
SAND_RAMP = M.ramp("#4b3d2a", "#5f4e37", "#746045", "#887354", "#9c8664", "#af9975", "#c1ab88", "#d1bd9c", "#dfcdb1")
ADOBE_RAMP = M.ramp("#4a3322", "#5f432e", "#76553a", "#8b6748", "#9f7a57", "#b18c68", "#c19d7a", "#cfae8d", "#dcbfa2")
BRICK_RAMP = M.ramp("#3b2416", "#4c2f1c", "#5e3a23", "#71462b", "#835234", "#945f3e", "#a46d4a")
TANK_RAMP = M.ramp("#0f1c0b", "#162a10", "#1f3916", "#29491c", "#335b22", "#3e6e2a", "#4b8233", "#5c9644", "#73aa5b")
TAN_RAMP = M.ramp("#3a2e1d", "#4d3d27", "#625033", "#77623f", "#8b744c", "#9d855a", "#ae966b", "#bea77e", "#cdb894")
DOME_RAMP = M.ORANGE_PAINT_RAMP
FLAG_GREEN = M.ramp("#0c1f0a", "#123010", "#1a4317", "#23581f", "#2e6e28", "#3b8533", "#4e9b45")

SAND = M.Material(ramp=SAND_RAMP, tone=0.6, mottle=0.035, grain=0.045, dither=0.85, edge_light=0.12, edge_dark=0.08, ao=0.6,
                  grime=0.12, dust=0.03, streaks=0.06, pits=0.006, falloff=0.03, despeckle=False)
ADOBE = M.Material(ramp=ADOBE_RAMP, tone=0.6, mottle=0.07, grain=0.05, dither=0.9, edge_light=0.14, edge_dark=0.1, ao=0.6,
                   grime=0.16, dust=0.05, streaks=0.12, falloff=0.04, despeckle=False)
BRICK = M.Material(ramp=BRICK_RAMP, tone=0.55, mottle=0.05, grain=0.06, dither=0.8, edge_light=0.1, edge_dark=0.1, ao=0.5,
                   grime=0.1, despeckle=False)
TANK = M.painted(TANK_RAMP, tone=0.58, wear=0.25, rust=0.12, streaks=0.06)
TAN = M.painted(TAN_RAMP, tone=0.6, wear=0.35, rust=0.15, streaks=0.05)
RUSTY = M.steel(tone=0.42, rust=0.45, brushed=0.02)
DARK_STEEL = M.steel(tone=0.3, rust=0.15)
BRIGHT = M.steel(tone=0.75, brushed=0.1)
DOME = M.painted(DOME_RAMP, tone=0.58, wear=0.2, streaks=0.1, grime=0.18)
WOOD = M.wood(tone=0.5)
OLIVE = M.painted(M.OLIVE_RAMP, tone=0.55, wear=0.35, rust=0.08)
RUBBER = M.rubber(tone=0.45)
TOXIN_GLOW = (150, 255, 90, 255)

YELLOW, BLACK = P.HAZARD_YELLOW, P.HAZARD_BLACK
#: The biohazard mark, black on yellow (nine texels square).
BIOHAZARD = ["YYYYYYYYY",
             "YYYKKKYYY",
             "YYKYKYKYY",
             "YYYKKKYYY",
             "YKKYKYKKY",
             "KYYKKKYYK",
             "KYKKYKKYK",
             "YKKYYYKKY",
             "YYYYYYYYY"]


#: Every round section's planks (painted as round: no lips at their facets' sides).
ROUND: list[str] = []


class OnBone:
    """A model whose boxes all go on one bone (for warheads.section, which builds round sections plank by plank)."""

    def __init__(self, g: GeoModel, bone: str):
        self.g, self.bone = g, bone

    def box(self, name, frm, to, **kw):
        return self.g.box(name, frm, to, bone=self.bone, **kw)

    @property
    def open_ok(self):
        return self.g.open_ok


def ring(g: GeoModel, name: str, r: float, y0: float, y1: float, cx: float, cz: float, bone: str = F, caps=("up",),
         sides: int | None = None, stagger: str | None = None) -> list[str]:
    """A round section (warheads.section: planks every 22.5 degrees) on a bone."""
    names = W.section(OnBone(g, bone), name, r, y0, y1, sides or W.sides_for(r), cx=cx, cz=cz, caps=caps, stagger=stagger)
    ROUND.extend(names)
    return names


def dome(g: GeoModel, name: str, R: float, y0: float, L: float, cx: float, cz: float, steps: int, bone: str = F,
         shape: str = "dome", min_r: float = 0.7) -> tuple[list[str], float]:
    """A dome of rings from a real profile (warheads.nose's way, about any middle): each ring the profile's radius at its
    middle and at least 0.12 px inside the one under it. Returns its planks and its top."""
    radii, last = [], R
    for i in range(steps):
        r = min(W.profile(shape, (i + 0.5) / steps, R, L), last - 0.12)
        if r < min_r and radii:
            break
        radii.append(max(r, min_r))
        last = radii[-1]
    names = []
    for i, r in enumerate(radii):
        a = y0 + L * i / steps
        b = y0 + L * (i + 1) / steps if i + 1 < len(radii) else y0 + L
        above = radii[i + 1] if i + 1 < len(radii) else 0.0
        stagger = "alternate" if W.sides_for(r) == 16 and above >= 0.55 * r else "full"
        names += ring(g, f"{name}{i}", round(r, 3), round(a, 3), round(b, 3), cx, cz, bone, caps=("up",), stagger=stagger)
    return names, y0 + L


def strut(g: GeoModel, name: str, a, b, w: float, bone: str, faces=ALL, d: float | None = None) -> str:
    """A bar {w} by {d} from point a to point b (they differ in y and z), built upright at a and leaned over."""
    ax, ay, az = a
    _, by, bz = b
    d = w if d is None else d
    length = math.dist(a, b)
    rot = {"origin": tuple(a), "axis": "x", "angle": math.degrees(math.atan2(bz - az, by - ay))}
    g.box(name, (ax - w / 2, ay, az - d / 2), (ax + w / 2, ay + length, az + d / 2), faces=faces, rotation=rot, bone=bone)
    return name


# --- the model ----------------------------------------------------------------------------------------------------------------
class Parts:
    """The model's boxes by what they are (for the painter)."""

    def __init__(self):
        self.groups: dict[str, list[str]] = {}

    def add(self, key: str, *names) -> None:
        for n in names:
            if isinstance(n, (list, tuple)):
                self.groups.setdefault(key, []).extend(n)
            else:
                self.groups.setdefault(key, []).append(n)

    def __getitem__(self, key: str) -> list[str]:
        return self.groups.get(key, [])


def build_pad(g: GeoModel, p: Parts) -> None:
    x0, x1, z0, z1 = PAD
    g.box("deck", (x0, 0, z0), (x1, DECK_Y, z1), faces=("north", "east", "up"), bone=F)
    g.box("console", (-5, DECK_Y, -7), (5, 22, -2), faces=NO_DOWN, bone=F)
    # The Energy Port's housing along the front edge of its cell (x 48), the Item Port's beside the controller (x -48).
    g.box("e_housing", (43, DECK_Y, -8), (53, 19, -3), faces=NO_DOWN, bone=F)
    g.box("i_housing", (-53, DECK_Y, -8), (-43, 19, -3), faces=NO_DOWN, bone=F)
    g.box("item_port", (-55, DECK_Y, -2), (-41, 14.5, 12), faces=NO_DOWN, bone=F)
    for i, (vx, vz) in enumerate(VENTS):
        g.box(f"vent{i}", (vx - 3, DECK_Y, vz - 3), (vx + 3, 14.6, vz + 3), faces=NO_DOWN, bone=F)
        p.add("vent", f"vent{i}")


#: The steam vents in the pad (x, z): ScudStormBlockEntity puffs vapour out of them while it charges.
VENTS = [(-80.0, 24.0), (84.0, 150.0), (-64.0, 176.0), (72.0, 40.0)]


def build_walls(g: GeoModel, p: Parts) -> None:
    # The L of wall: down the left of the pad from the front tower, across the back to the back tower.
    g.box("wall_left", (-120, 0, 9), (-104, WALL_H, 200), faces=("west", "east", "up", "north"), bone=F)
    g.box("wall_back", (-120, 0, 200), (103, WALL_H, 216), faces=("north", "south", "west", "up", "east"), bone=F)
    g.box("cap_left", (-119, WALL_H, 9), (-105, 30.5, 201), faces=("west", "east", "up", "north"), bone=F)
    g.box("cap_back", (-119, WALL_H, 201), (103, 30.5, 215), faces=("north", "south", "west", "up", "east"), bone=F)
    p.add("adobe", "wall_left", "wall_back", "cap_left", "cap_back")
    # Vigas: the roof beams' ends standing out of the outer faces under the cap.
    for i, z in enumerate(range(24, 200, 24)):
        g.box(f"viga_l{i}", (-122.5, 20, z - 1.2), (-120, 22.4, z + 1.2), faces=("west", "north", "south", "up", "down"), bone=F)
        p.add("viga", f"viga_l{i}")
    for i, x in enumerate(range(-96, 100, 24)):
        g.box(f"viga_b{i}", (x - 1.2, 20, 216), (x + 1.2, 22.4, 218.5), faces=("south", "east", "west", "up", "down"), bone=F)
        p.add("viga", f"viga_b{i}")
    # The front-left watchtower: a square tower, a band round its top, a drum and the orange dome on it.
    g.box("tower_a", (-121, 0, -9), (-103, TOWER_H, 9), faces=ALL, bone=F)
    g.box("tower_a_band", (-122, TOWER_H, -10), (-102, 60, 10), faces=ALL, bone=F)
    p.add("adobe", "tower_a", "tower_a_band")
    drum = ring(g, "tower_a_drum", 7.0, 60, 63, -112, 0)
    dome_planks, top = dome(g, "tower_a_dome", 7.4, 63, 9, -112, 0, 9)
    finial = ring(g, "tower_a_finial", 0.7, top - 0.6, top + 2.5, -112, 0)
    p.add("adobe", drum)
    p.add("dome", dome_planks)
    p.add("brass", finial)
    # The back-right watchtower: a flat roof inside a low parapet, its antenna mast with crossbars and a little radar.
    g.box("tower_b", (103, 0, 199), (121, TOWER_H, 217), faces=ALL, bone=F)
    g.box("tower_b_band", (102, TOWER_H, 198), (122, 60, 218), faces=ALL, bone=F)
    for n, a, b in (("pp_n", (102, 60, 198), (122, 63, 200)), ("pp_s", (102, 60, 216), (122, 63, 218)),
                    ("pp_w", (102, 60, 200), (104, 63, 216)), ("pp_e", (120, 60, 200), (122, 63, 216))):
        g.box(n, a, b, faces=NO_DOWN, bone=F)
        p.add("adobe", n)
    p.add("adobe", "tower_b", "tower_b_band")
    g.box("mast", (111.3, 60, 207.3), (112.7, 96, 208.7), faces=NO_DOWN, bone=F)
    g.box("mast_bar1", (106, 82, 207.6), (118, 83, 208.4), faces=ALL, bone=F)
    g.box("mast_bar2", (108, 89, 207.6), (116, 90, 208.4), faces=ALL, bone=F)
    g.box("whip", (111.7, 96, 207.7), (112.3, 112, 208.3), faces=NO_DOWN, bone=F)
    p.add("mast", "mast", "mast_bar1", "mast_bar2", "whip")
    g.bone("radar", pivot=(112, 70, 208), parent=F)
    g.box("radar_post", (111, 63, 207), (113, 70, 209), faces=NO_DOWN, bone="radar")
    g.box("radar_dish", (106, 70, 207.4), (118, 74, 208.6), faces=ALL, bone="radar")
    p.add("radar", "radar_post", "radar_dish")


def build_tank(g: GeoModel, p: Parts) -> None:
    p.add("plinth", ring(g, "plinth", 21.0, DECK_Y, 22, CX, CZ))
    p.add("tank", ring(g, "tank", 15.0, 22, 58, CX, CZ, caps=()))
    for i, y in enumerate((30, 48)):
        p.add("strap", ring(g, f"strap{i}", 15.5, y, y + 2, CX, CZ, caps=("up", "down"), stagger="full"))
    planks, top = dome(g, "tank_dome", 15.0, 58, 9, CX, CZ, 9)
    p.add("tank", planks)
    p.add("steel", ring(g, "hatch", 3.0, top - 0.5, top + 1.2, CX, CZ))
    g.box("vent_pipe", (6, 60, 92), (8, 72, 94), faces=NO_DOWN, bone=F)
    g.box("vent_cap", (5, 72, 91), (9, 73.5, 95), faces=ALL, bone=F)
    p.add("steel", "vent_pipe", "vent_cap")
    # The sight gauge up its front (the toxin glowing in the glass) and the ladder up its back.
    g.box("gauge", (-1.0, 26, z_of(16.6)), (1.0, 54, z_of(15.7)), faces=("north", "east", "west", "up", "down"), bone=F)
    for i, y in enumerate((25, 54)):
        g.box(f"gauge_clamp{i}", (-1.8, y, z_of(16.9)), (1.8, y + 1.5, z_of(14.6)), faces=("north", "east", "west", "up", "down"), bone=F)
        p.add("steel", f"gauge_clamp{i}")
    p.add("gauge", "gauge")
    for s, n in ((-1, "ladder_l"), (1, "ladder_r")):
        g.box(n, (2.6 * s - 0.4, 22, CZ + 15.3), (2.6 * s + 0.4, 64, CZ + 16.1), faces=ALL, bone=F)
        p.add("steel", n)
    for i, y in enumerate(range(25, 63, 3)):
        g.box(f"rung{i}", (-2.2, y, CZ + 15.5), (2.2, y + 0.5, CZ + 15.9), faces=("south", "up", "down"), bone=F)
        p.add("steel", f"rung{i}")


def build_erectors(g: GeoModel, p: Parts) -> None:
    """Nine erectors, each built pointing to the front from the pad's middle and turned to its bearing by its bone."""
    hu, hy = HINGE
    tu, ty = TAIL
    fu, fy = RAM_FOOT
    for k in range(COUNT):
        e, boom, scud, ram, rod = f"erector{k}", f"boom{k}", f"scud{k}", f"ram{k}", f"rod{k}"
        g.bone(e, pivot=(CX, DECK_Y, CZ), parent=F, rotation=(0, YAW_SIGN * (FIRST_BEARING + k * BEARING_STEP), 0))
        g.bone(boom, pivot=(0, hy, z_of(hu)), parent=e)
        g.bone(scud, pivot=(0, ty, z_of(tu)), parent=boom)
        g.bone(ram, pivot=(0, fy, z_of(fu)), parent=e)
        g.bone(rod, pivot=(0, fy, z_of(fu)), parent=ram)
        # The fuel hose from the tank's foot, the launch table, the hinge's cheeks and pin.
        g.box(f"hose{k}", (-1, DECK_Y, z_of(25)), (1, 16, z_of(21)), faces=NO_DOWN, bone=e)
        g.box(f"table{k}", (-6, DECK_Y, z_of(36)), (6, 21, z_of(24)), faces=NO_DOWN, bone=e)
        g.box(f"cheek_w{k}", (-6.5, 18, z_of(33)), (-5, 29, z_of(27)), faces=ALL, bone=e)
        g.box(f"cheek_e{k}", (5, 18, z_of(33)), (6.5, 29, z_of(27)), faces=ALL, bone=e)
        g.box(f"pin{k}", (-5, 24.5, z_of(31.5)), (5, 27.5, z_of(28.5)), faces=("north", "south", "up", "down"), bone=e)
        g.box(f"foot{k}", (-2.5, DECK_Y, z_of(fu + 2)), (2.5, 16.5, z_of(fu - 2)), faces=NO_DOWN, bone=e)
        p.add("hose", f"hose{k}")
        p.add("table", f"table{k}", f"foot{k}")
        p.add("cheek", f"cheek_w{k}", f"cheek_e{k}")
        p.add("pin", f"pin{k}")
        # The boom: its knuckle round the pin, the beam out under the SCUD, the plate its tail stands on once raised, the
        # cradles, the lug the ram pushes on and a hydraulic line down its side.
        g.box(f"knuckle{k}", (-4.5, 20, z_of(34)), (4.5, 27, z_of(28)), faces=ALL, bone=boom)
        g.box(f"beam{k}", (-3, 20, z_of(92)), (3, 24, z_of(34)), faces=("north", "east", "west", "up", "down"), bone=boom)
        g.box(f"plate{k}", (-5, 22, z_of(31.2)), (5, 35, z_of(30.2)), faces=ALL, bone=boom)
        g.box(f"lug{k}", (-1.5, 18.5, z_of(41.5)), (1.5, 20, z_of(38.5)), faces=("north", "south", "east", "west", "down"), bone=boom)
        g.box(f"line{k}", (3, 21, z_of(88)), (3.8, 22, z_of(36)), faces=("north", "east", "up", "down"), bone=boom)
        p.add("boom", f"knuckle{k}", f"beam{k}", f"lug{k}")
        p.add("plate", f"plate{k}")
        p.add("hose", f"line{k}")
        for c, h in enumerate(CRADLES):
            zc = z_of(tu + h)
            base, cw, ce, strap = f"cradle{k}_{c}", f"cradle{k}_{c}w", f"cradle{k}_{c}e", f"cradle{k}_{c}s"
            g.box(base, (-4.6, 24, zc - 2), (4.6, 26, zc + 2), faces=("north", "south", "up", "down"), bone=boom)
            g.box(cw, (-6.6, 23, zc - 2.5), (-4.6, 35.2, zc + 2.5), faces=ALL, bone=boom)
            g.box(ce, (4.6, 23, zc - 2.5), (6.6, 35.2, zc + 2.5), faces=ALL, bone=boom)
            g.box(strap, (-4.6, 34.2, zc - 1), (4.6, 35.2, zc + 1), faces=("north", "south", "up", "down"), bone=boom)
            p.add("saddle", base)
            p.add("cradle", cw, ce, strap)
        # The ram: its cylinder from the foot toward the lug, the rod sliding out of it.
        lu, ly = hu + RAM_ARM[0], hy + RAM_ARM[1]
        length = math.dist((fu, fy), (lu, ly))
        du, dy = (lu - fu) / length, (ly - fy) / length
        # The cylinder holds the rod's whole travel (it slides out 11.3 px as the boom rises) with some to spare.
        a = (0.0, fy, z_of(fu))
        end = (0.0, fy + dy * 19, z_of(fu + du * 19))
        strut(g, f"cyl{k}", a, end, 3.0, ram)
        strut(g, f"piston{k}", (0.0, fy + dy * 6.5, z_of(fu + du * 6.5)), (0.0, ly, z_of(lu)), 1.6, rod)
        p.add("cylinder", f"cyl{k}")
        p.add("rod", f"piston{k}")


def build_props(g: GeoModel, p: Parts) -> None:
    """The guard hut and its flag, crates by the domed tower, drums by the antenna tower."""
    g.box("hut", (66, DECK_Y, -2), (98, 40, 26), faces=NO_DOWN, bone=F)
    g.box("hut_roof", (64, 40, -4), (100, 42, 28), faces=ALL, bone=F)
    p.add("adobe", "hut")
    p.add("roof", "hut_roof")
    g.box("pole", (91.4, 42, 19.4), (92.6, 76, 20.6), faces=NO_DOWN, bone=F)
    p.add("steel", "pole")
    g.bone("flag", pivot=(92, 74, 20), parent=F)
    g.box("flag_cloth", (92.6, 64, 19.9), (108, 74, 20.1), faces=("north", "south", "east", "up", "down"), bone="flag")
    p.add("flag", "flag_cloth")
    crates = [("crate0", (-98, DECK_Y, 2), (-86, 25, 14)), ("crate1", (-84, DECK_Y, 3), (-74, 23, 13)),
              ("crate2", (-96, 25, 4), (-88, 32, 12)), ("ammo0", (-100, DECK_Y, 17), (-88, 19, 24)),
              ("ammo1", (-99, 19, 18), (-90, 23, 23))]
    for n, a, b in crates:
        g.box(n, a, b, faces=NO_DOWN if a[1] == DECK_Y else ALL, bone=F)
        p.add("crate" if n.startswith("crate") else "ammo", n)
    for i, (x, z) in enumerate(((78, 174), (87, 173), (96, 175), (82, 183), (91, 184))):
        names = ring(g, f"drum{i}", 4.0, DECK_Y, 26, x, z, sides=8, stagger="full")
        hoops = ring(g, f"hoop{i}", 4.3, 19, 20, x, z, caps=("up", "down"), sides=8, stagger="full")
        p.add(f"drum{i % 3}", names)
        p.add("hoop", hoops)


def build() -> tuple[GeoModel, Parts]:
    g = GeoModel("scud_storm", tex_size=(1024, 1024))
    g.open_ok = set()
    g.bone(F)
    p = Parts()
    build_pad(g, p)
    build_walls(g, p)
    build_tank(g, p)
    build_erectors(g, p)
    build_props(g, p)
    return g, p


# --- painting -----------------------------------------------------------------------------------------------------------------
def _kit(g: GeoModel):
    from gz import machine_kit as K  # noqa: PLC0415
    k = K.Kit(g, {}, seed=419, ao_reach=3.0, turned=True)
    W.round_faces(k.S, ROUND, top=True, bottom=False, foot=True)
    return k


def paint_model(g: GeoModel, p: Parts) -> tuple[Tex, Tex]:
    k = _kit(g)
    S = k.S
    tex = Tex(g.tex_w, g.tex_h)
    glow = Tex(g.tex_w, g.tex_h)
    paint_deck(S, tex)
    L._console(S, g, tex, "console")
    L._power_housing(S, g, tex, "e_housing", plug="north")
    S.paint(tex, "i_housing", OLIVE)
    S.paint(tex, "item_port", M.steel(tone=0.4, brushed=0.04, rust=0.1))
    S.overlay(tex, "item_port", lambda f, img: M.grid(f, img, every=2, depth=0.5), only=("up",))
    S.paint(tex, p["vent"], DARK_STEEL)
    S.overlay(tex, p["vent"], lambda f, img: M.grid(f, img, every=1.5, depth=0.35), only=("up",))
    paint_adobe(S, tex, p["adobe"])
    S.paint(tex, p["viga"], M.wood(tone=0.38))
    S.paint(tex, p["dome"], DOME)
    S.paint(tex, p["brass"], M.Material(ramp=W.ramp9(P.BRASS), tone=0.6, edge_light=0.2))
    S.paint(tex, p["mast"], M.galvanized(tone=0.5))
    S.paint(tex, p["radar"], M.white_paint(tone=0.7))
    # The tank: green paint over a rusty base, rusty straps, its biohazard marks, the gauge's glass glowing.
    S.paint(tex, p["plinth"], RUSTY)
    S.overlay(tex, p["plinth"], lambda f, img: W.bolts(f, img, 18.5), only=SIDES)
    S.paint(tex, p["tank"], TANK)
    S.overlay(tex, p["tank"], biohazards)
    S.paint(tex, p["strap"], M.steel(tone=0.38, rust=0.5))
    S.paint(tex, p["steel"], M.steel(tone=0.5, rust=0.1))
    S.paint(tex, p["gauge"], M.Material(ramp=W.ramp9(P.TOXIN), tone=0.7, mottle=0.02, edge_light=0.2))
    g.paint_all(glow, "gauge", lambda t, r, d: t.rect(r[0], r[1], r[2], r[3], TOXIN_GLOW) if d == "north" else None)
    # The erectors: rusty tables, tan booms and cradles, black rubber saddles, dark rams with bright rods.
    S.paint(tex, p["table"], RUSTY)
    S.paint(tex, p["cheek"] + p["boom"] + p["cradle"], TAN)
    S.overlay(tex, p["boom"], lambda f, img: M.rivets(f, img, every=4, inset=1), only=("east", "west"))
    S.paint(tex, p["plate"], M.stripes(width=2, wear=0.4))
    S.paint(tex, p["pin"] + p["rod"], BRIGHT)
    S.paint(tex, p["saddle"], RUBBER)
    S.paint(tex, p["cylinder"], M.painted(M.BLACK_RAMP, tone=0.5, wear=0.2))
    S.paint(tex, p["hose"], RUBBER)
    # The props.
    S.paint(tex, p["roof"], M.wood(tone=0.42))
    S.overlay(tex, p["roof"], lambda f, img: M.seams(f, img, every=3, axis="u", light=False), only=("up",))
    paint_hut(g, tex)
    paint_towers(g, tex)
    paint_flag(g, tex)
    S.paint(tex, p["crate"], WOOD)
    S.overlay(tex, p["crate"], lambda f, img: M.seams(f, img, every=4, axis="v", light=False), only=SIDES)
    S.paint(tex, p["ammo"], OLIVE)
    for i, ramp in enumerate((M.RED_PAINT_RAMP, M.GREY_PAINT_RAMP, M.OLIVE_RAMP)):
        S.paint(tex, p[f"drum{i}"], M.painted(ramp, tone=0.5, wear=0.4, rust=0.4))
    S.paint(tex, p["hoop"], M.steel(tone=0.35, rust=0.5))
    return tex, glow


def paint_deck(S: M.Surfacer, tex: Tex) -> None:
    """Sand-coloured concrete cut into slabs every two cells; a darker curb round its edge; the scorch of launches round the
    raised SCUDs' feet; oil under the erectors' tables; green toxin stains splashed round the tank; tracks from the front."""
    S.paint(tex, "deck", SAND)
    S.paint(tex, "deck", M.Material(ramp=SAND_RAMP, tone=0.5, mottle=0.04, grain=0.05, dither=0.8, despeckle=False), only=("north", "east"))
    x0, x1, z0, z1 = PAD
    raised = [(CX + 26 * math.sin(math.radians(FIRST_BEARING + k * BEARING_STEP)), CZ - 26 * math.cos(math.radians(FIRST_BEARING + k * BEARING_STEP)))
              for k in range(COUNT)]
    tables = [(CX + 30 * math.sin(math.radians(FIRST_BEARING + k * BEARING_STEP)), CZ - 30 * math.cos(math.radians(FIRST_BEARING + k * BEARING_STEP)))
              for k in range(COUNT)]

    def floor(f, img):
        cut = lambda v: np.abs(((v - 8) + 16) % 32 - 16) < 0.3
        M.darken(img, cut(f.X) | cut(f.Z), 0.7)
        edge = np.minimum(np.minimum(f.X - x0, x1 - f.X), np.minimum(f.Z - z0, z1 - f.Z))
        M.darken(img, edge < 4, 0.82)
        M.darken(img, (edge >= 4) & (edge < 5), 0.68)
        # Scorch round each raised SCUD's foot (where its exhaust has hit the pad), ragged.
        burn = np.zeros(f.shape, dtype=np.float32)
        for bx, bz in raised:
            d = np.hypot(f.X - bx, f.Z - bz) + (M.fbm(f.X, f.Y, f.Z, 3.0, 61, 3) - 0.5) * 6
            burn = np.maximum(burn, np.clip(1 - d / 13, 0, 1) ** 1.3)
        M.sooted(f, img, burn, strength=0.7)
        # Oil under the tables; toxin splashed round the tank.
        stain = M.fbm(f.X, f.Y, f.Z, 5.0, 67, 3)
        for bx, bz in tables:
            d = np.hypot(f.X - bx, f.Z - bz)
            M.darken(img, (d < 9) & (stain > 0.55 + d / 30), 0.55)
        dt = np.hypot(f.X - CX, f.Z - CZ)
        toxin = (dt > 21) & (dt < 34) & (stain > 0.62)
        rgb = img[..., :3].astype(np.float32)
        rgb[toxin] = rgb[toxin] * 0.55 + np.array([60, 120, 30], dtype=np.float32) * 0.45
        img[..., :3] = np.clip(rgb, 0, 255).astype(np.uint8)
        # Tyre tracks in from the front edge to the guard hut and the crates.
        for tx in (-60.0, -50.0, 50.0, 60.0):
            track = (np.abs(f.X - tx) < 1.6) & (f.Z < 70) & (M.white(f.X, f.Y, f.Z, 71) > 0.35)
            M.darken(img, track, 0.86)
    S.overlay(tex, "deck", floor, only=("up",))


def paint_adobe(S: M.Surfacer, tex: Tex, names: list[str]) -> None:
    """Plaster over mud brick: the plaster fallen away here and there to show brick courses, cracks and drip stains."""
    S.paint(tex, names, ADOBE)
    S.paint(tex, names, M.Material(ramp=ADOBE_RAMP, tone=0.66, mottle=0.06, grain=0.04, dither=0.8, despeckle=False), only=("up",))

    def patches(f, img):
        if not f.vertical:
            return
        n = M.fbm(f.X, f.Y, f.Z, 16.0, 83, 3)
        bare = n > 0.69
        brick = S.material_on(f, BRICK)
        a, b = f.plane_uv()
        course = np.floor(f.Y / 3)
        joint = (np.abs(f.Y - course * 3) < 0.5) | (np.abs(((a + 3 * (course % 2)) % 6) - 0.25) < 0.5)
        M.paste(img, brick, bare)
        M.darken(img, bare & joint, 0.6)
        M.darken(img, (n > 0.665) & ~bare, 0.78)                # the plaster's broken edge
        crack = (np.abs(M.fbm(f.X * 0.6, f.Y * 2, f.Z * 0.6, 4.0, 89, 2) - 0.5) < 0.012)
        M.darken(img, crack, 0.6)
    S.overlay(tex, names, patches)


def biohazards(f, img) -> None:
    """The biohazard mark on the tank's four sides."""
    if f.d not in SIDES:
        return
    ang = (np.degrees(np.arctan2(f.X - CX, f.Z - CZ)) + 360) % 360
    for centre in (0, 90, 180, 270):
        d = (ang - centre + 540) % 360 - 180
        if np.abs(d).min() > 20:
            continue
        across = np.radians(d) * 15.0                           # pixels round the tank from the mark's middle
        col = np.floor(across + 4.5).astype(int)
        row = np.floor(46.5 - f.Y).astype(int)
        inside = (col >= 0) & (col < 9) & (row >= 0) & (row < 9)
        for j, line in enumerate(BIOHAZARD):
            for i, ch in enumerate(line):
                on = inside & (row == j) & (col == i)
                img[on] = BLACK[1] if ch == "K" else YELLOW[4]


def paint_hut(g: GeoModel, tex: Tex) -> None:
    """The guard hut's dark doorway (on its side toward the pad's middle) and a shuttered window on its front."""
    def fn(t: Tex, r, d):
        x, y, w, h = r
        if d == "west" and w > 10:
            t.rect(x + w // 2 - 4, y + h - 16, 8, 16, P.BLACK_PAINT.deep)
            t.outline(x + w // 2 - 5, y + h - 17, 10, 17, (82, 56, 32, 255))
        if d == "north" and w > 10:
            t.rect(x + w // 2 - 4, y + 6, 8, 6, (24, 20, 16, 255))
            for xx in range(x + w // 2 - 4, x + w // 2 + 4, 2):
                t.vline(xx, y + 6, 6, (96, 68, 40, 255))
    g.paint_all(tex, "hut", fn)


def paint_towers(g: GeoModel, tex: Tex) -> None:
    """The watchtowers' slit windows (two on each outer side, high up) and a timber-framed door at the foot of the side
    toward the pad."""
    dark, frame = (20, 16, 13, 255), (86, 60, 36, 255)

    def fn(door_side: str):
        def paint_face(t: Tex, r, d):
            x, y, w, h = r
            if d not in SIDES:
                return
            for cx in (x + w // 3, x + 2 * w // 3):
                t.rect(cx - 1, y + 12, 2, 6, dark)
                t.hline(cx - 2, y + 11, 4, frame)
            if d == door_side:
                t.rect(x + w // 2 - 3, y + h - 30, 6, 14, dark)
                t.outline(x + w // 2 - 4, y + h - 31, 8, 15, frame)
        return paint_face
    g.paint_all(tex, "tower_a", fn("east"))
    g.paint_all(tex, "tower_b", fn("north"))


def paint_flag(g: GeoModel, tex: Tex) -> None:
    """The GLA's flag: green, a black band along its foot and a white crescent-and-dagger mark near the pole."""
    def fn(t: Tex, r, d):
        x, y, w, h = r
        if d not in ("north", "south"):
            t.rect(x, y, w, h, FLAG_GREEN_RGBA[3])
            return
        for yy in range(h):
            for xx in range(w):
                shade = 3 + (1 if (xx // 3) % 2 == 0 else 0) - (1 if yy > h - 3 else 0)
                t.px(x + xx, y + yy, FLAG_GREEN_RGBA[max(0, min(6, shade))])
        for xx in range(w):
            t.px(x + xx, y + h - 3, P.BLACK_PAINT[1])
            t.px(x + xx, y + h - 2, P.BLACK_PAINT[1])
        ax = x + (3 if d == "south" else w - 7)
        for (dx, dy) in ((1, 2), (2, 1), (3, 1), (1, 3), (1, 4), (2, 5), (3, 5), (3, 3), (3, 4)):
            t.px(ax + dx, y + dy, (236, 232, 220, 255))
    g.paint_all(tex, "flag_cloth", fn)


FLAG_GREEN_RGBA = [tuple(int(c) for c in v) + (255,) for v in FLAG_GREEN]


# --- animations ----------------------------------------------------------------------------------------------------------------
def erector_pose(alpha: float) -> tuple[float, float]:
    """For a boom raised {alpha} degrees: the ram's turn (degrees, + raising its end) and its rod's travel (pixels)."""
    hu, hy = HINGE
    fu, fy = RAM_FOOT
    au, ay = RAM_ARM

    def lug(a: float) -> tuple[float, float]:
        c, s = math.cos(math.radians(a)), math.sin(math.radians(a))
        return hu + au * c - ay * s, hy + au * s + ay * c
    lu0, ly0 = lug(0)
    lu, ly = lug(alpha)
    e0 = math.degrees(math.atan2(ly0 - fy, fu - lu0))
    e = math.degrees(math.atan2(ly - fy, fu - lu))
    return e - e0, math.dist((fu, fy), (lu, ly)) - math.dist((fu, fy), (lu0, ly0))


def erector_animation(name: str, raising: bool) -> Animation:
    """The erectors raising (or lowering) their SCUDs: each boom turned up about its hinge, eased; the ram turning with it
    and its rod sliding out (sampled every quarter second: its angle and travel follow the boom's)."""
    a = Animation(name, RAISE_T, loop="hold_on_last_frame")
    hu, hy = HINGE
    fu, fy = RAM_FOOT
    lu0, ly0 = hu + RAM_ARM[0], hy + RAM_ARM[1]
    length = math.dist((fu, fy), (lu0, ly0))
    du, dy = (lu0 - fu) / length, (ly0 - fy) / length
    steps = int(RAISE_T * 4)
    boom, ram, rod = {}, {}, {}
    for i in range(steps + 1):
        t = RAISE_T * i / steps
        s = i / steps
        s = s if raising else 1 - s
        alpha = 90.0 * (s * s * (3 - 2 * s))
        turn, travel = erector_pose(alpha)
        boom[t] = (RAISE_SIGN * alpha, 0, 0)
        ram[t] = (-RAISE_SIGN * turn, 0, 0)
        rod[t] = (0, dy * travel, -du * travel)
    for k in range(COUNT):
        a.rotate(f"boom{k}", boom)
        a.rotate(f"ram{k}", ram)
        a.move(f"rod{k}", rod)
    return a


def animations() -> list[Animation]:
    raise_ = erector_animation("raise", True)
    lower = erector_animation("lower", False)
    lowered = rest_pose([raise_], "lowered")
    # Always: the flag stirs in the wind. Charging ("working"): the little radar on the back tower turns too.
    ambient = Animation("ambient", 4.0, loop=True)
    ambient.rotate("flag", {0: (0, -8, 0), 1.0: (0, 6, 0), 2.0: (0, -4, 0), 3.0: (0, 9, 0), 4.0: (0, -8, 0)}, easing="easeInOutSine")
    working = Animation("working", 4.0, loop=True)
    working.rotate("flag", ambient.bones["flag"]["rotation"], easing="easeInOutSine")
    working.rotate("radar", {0: (0, 0, 0), 4.0: (0, 360, 0)})
    idle = rest_pose([working], "idle")
    return [raise_, lower, lowered, ambient, working, idle]


# --- blocks, the controller, the spawner, the ammunition ----------------------------------------------------------------------
def adobe_wall_block() -> Tex:
    """Adobe Wall: plaster over mud brick, a patch of the brick showing."""
    img = M.tile(M.Material(ramp=ADOBE_RAMP, tone=0.625, mottle=0.08, grain=0.06, dither=0.7, scale=0.5, despeckle=False), seed=5)
    col, row = M.grid_xy()
    bare = (col >= 2) & (col <= 8) & (row >= 9) & (row <= 13)
    brick = M.tile(M.Material(ramp=BRICK_RAMP, tone=0.55, mottle=0.05, grain=0.06, dither=0.7, despeckle=False), seed=6)
    img[bare] = brick[bare]
    joints = bare & ((row == 11) | ((row < 11) & (col == 5)) | ((row > 11) & ((col == 3) | (col == 7))))
    M.darken(img, joints, 0.6)
    M.darken(img, ((col == 1) | (col == 9)) & (row >= 9) & (row <= 13) | ((row == 8) | (row == 14)) & (col >= 1) & (col <= 9), 0.8)
    crack = ((col == 12) & (row >= 2) & (row <= 4)) | ((col == 11) & (row >= 4) & (row <= 6)) | ((col == 12) & (row == 7))
    M.darken(img, crack, 0.62)
    return M.to_tex(img)


def toxin_tank_block() -> Tex:
    """Toxin Tank: green-painted plate riveted at its seams, a yellow biohazard placard on it."""
    img = PN.face(M.painted(TANK_RAMP, tone=0.58, wear=0.2, rust=0.1), style="raised", seed=13)
    t = M.to_tex(img)
    t.rect(4, 4, 8, 8, YELLOW[4])
    t.outline(4, 4, 8, 8, BLACK[2])
    for (x, y) in ((7, 5), (8, 5), (6, 6), (9, 6), (7, 7), (8, 7), (5, 8), (10, 8), (6, 9), (7, 9), (8, 9), (9, 9), (5, 10), (10, 10)):
        t.px(x, y, BLACK[1])
    return t


def scud_erector_block() -> Tex:
    """SCUD Erector: a tan boom's side, its hydraulic cylinder across it and a hazard-striped end."""
    img = PN.face(M.painted(TAN_RAMP, tone=0.6, wear=0.3, rust=0.15), style="inset", seed=17)
    PN.stripes(img, PN.area(1, 1, 3, 14), seed=17)
    t = M.to_tex(img)
    t.rect(5, 6, 10, 4, P.BLACK_PAINT[2])
    t.hline(5, 6, 10, P.BLACK_PAINT[4])
    t.rect(11, 7, 4, 2, P.STEEL[5])
    t.px(5, 8, P.STEEL[3])
    return t


def toxin_placard(t: Tex) -> None:
    """The controller's placard: a biohazard mark."""
    t.rect(4, 9, 5, 5, YELLOW[4])
    for (x, y) in ((6, 9), (5, 10), (7, 10), (6, 11), (4, 12), (8, 12), (5, 13), (7, 13)):
        t.px(x, y, BLACK[1])


def blocks() -> None:
    ST.part_block("adobe_wall", adobe_wall_block())
    ST.part_block("toxin_tank", toxin_tank_block())
    ST.part_block("scud_erector", scud_erector_block())
    side = M.to_tex(PN.face(M.painted(TAN_RAMP, tone=0.6, wear=0.3), style="inset", seed=21))
    ST.controller("scud_storm", ST.controller_front(toxin_placard), ST.controller_top(), side)


def spawner_plan(t: Tex) -> None:
    """The SCUD Storm from above: the wall's L, the tank in the middle and the nine SCUDs round it."""
    for x in range(4, 13):
        t.px(x, 12, MT.BLUEPRINT[6])
    for y in range(3, 13):
        t.px(4, y, MT.BLUEPRINT[6])
    for (x, y) in ((8, 7), (9, 7), (8, 8), (9, 8)):
        t.px(x, y, (90, 200, 70, 255))
    for k in range(COUNT):
        a = math.radians(FIRST_BEARING + k * BEARING_STEP)
        for r in (2.4, 3.4):
            t.px(int(round(8.5 + r * math.sin(a))), int(round(7.5 - r * math.cos(a))), (236, 236, 228, 255))
    t.px(8, 3, P.HAZARD_YELLOW[5])


def ammo() -> None:
    """The Toxin SCUD item: the SCUD's own missile model (tools/assets/missiles.py)."""
    write_json(paths.ASSETS / "items" / "toxin_scud.json",
               {"model": {"type": "minecraft:model", "model": "groundzero:item/missile/scud_storm_warhead"}})


#: The anthrax residue's glow, darkest to brightest (brighter and yellower than the toxin grenade's sludge).
RESIDUE = [(12, 40, 8, 255), (22, 74, 12, 255), (38, 112, 18, 255), (62, 156, 26, 255), (98, 200, 40, 255), (150, 236, 74, 255),
           (214, 255, 150, 255)]


def residue() -> None:
    """The anthrax residue a toxin SCUD's front leaves (DESIGN.md §19.5): a film a pixel thick, the toxin grenade's sludge's
    way (a splatter mask that thins out with each of its four stages), but glowing brighter, its pools deeper at their
    hearts, and alive: eight frames of bubbles welling up, swelling and popping across it, its sheen drifting."""
    import json  # noqa: PLC0415
    rng = np.random.default_rng(1957)
    yy, xx = np.indices((16, 16))
    field = np.zeros((16, 16))
    for _ in range(16):                                       # overlapping blobs, wrapping so neighbours join up
        cx, cy, r = rng.uniform(0, 16), rng.uniform(0, 16), rng.uniform(2.2, 5.0)
        dx = np.minimum(abs(xx - cx), 16 - abs(xx - cx))
        dy = np.minimum(abs(yy - cy), 16 - abs(yy - cy))
        field += np.exp(-(dx * dx + dy * dy) / (r * r))
    field += rng.uniform(-0.1, 0.1, (16, 16))
    order = np.sort(field.ravel())
    frames = 8
    variants = {}
    for age, cover in enumerate((0.95, 0.76, 0.52, 0.3)):
        cut = order[int((1 - cover) * 255)]
        bubbles = [(int(rng.integers(0, 16)), int(rng.integers(0, 16)), int(rng.integers(0, frames))) for _ in range(7 - age)]
        sheet = Tex(16, 16 * frames)
        for fr in range(frames):
            sheen = 0.5 + 0.5 * np.sin((xx + yy * 0.6) * 0.55 + fr * 2 * np.pi / frames)
            for y in range(16):
                for x in range(16):
                    v = field[y, x]
                    if v < cut:
                        continue
                    depth = (v - cut) / (order[-1] - cut + 1e-6)
                    k = 0 if depth < 0.08 else 1 if depth < 0.2 else 2 if depth < 0.42 else 3 if depth < 0.66 else 4
                    if k >= 3 and sheen[y, x] > 0.86:
                        k += 1                                  # the sheen sliding over the deeper pools
                    sheet.px(x, fr * 16 + y, RESIDUE[k])
            for bx, by, phase in bubbles:
                if field[by, bx] < cut + 0.25:
                    continue
                s = (fr + phase) % frames
                at = lambda dx, dy: ((bx + dx) % 16, fr * 16 + (by + dy) % 16)
                if s in (0, 1):
                    sheet.px(*at(0, 0), RESIDUE[5])                       # a bead welling up
                elif s in (2, 3):
                    for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1)):
                        sheet.px(*at(dx, dy), RESIDUE[5])
                    sheet.px(*at(0, 0), RESIDUE[6])                       # swelling, its highlight
                elif s == 4:
                    for dx, dy in ((-1, 0), (2, 0), (0, -1), (1, -1), (0, 2), (1, 2), (-1, 1), (2, 1)):
                        sheet.px(*at(dx, dy), RESIDUE[6])                 # popping: a ring of spray
        name = f"anthrax_residue_{age}"
        path = sheet.save(paths.TEXTURES / "block" / f"{name}.png")
        Path(str(path) + ".mcmeta").write_text(json.dumps({"animation": {"frametime": 3}}, indent=2) + "\n", encoding="utf-8")
        side = {"uv": [0, 0, 16, 1], "texture": "#residue"}
        write_json(paths.MODELS / "block" / f"{name}.json", {
            "render_type": "minecraft:cutout",
            "textures": {"residue": f"groundzero:block/{name}", "particle": f"groundzero:block/{name}"},
            "elements": [{"from": [0, 0, 0], "to": [16, 1, 16], "faces": {
                "up": {"uv": [0, 0, 16, 16], "texture": "#residue"},
                "down": {"uv": [0, 0, 16, 16], "texture": "#residue", "cullface": "down"},
                "north": side, "south": side, "east": side, "west": side}}],
        })
        variants[f"age={age}"] = [{"model": f"groundzero:block/{name}", "y": r} for r in (0, 90, 180, 270)]
    write_json(paths.ASSETS / "blockstates" / "anthrax_residue.json", {"variants": variants})


def main() -> None:
    residue()
    g, p = build()
    g.layout()
    tex, glow = paint_model(g, p)
    g.save(tex, glow)
    print(save_animations("scud_storm", animations()))
    blocks()
    MT.structure_spawner("scud_storm_spawner", spawner_plan)
    ammo()
    print(f"scud_storm: {len(g.boxes)} boxes, texture {g.tex_w}x{g.tex_h}")


if __name__ == "__main__":
    main()
