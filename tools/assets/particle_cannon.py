"""The USA's Particle Uplink Cannon (DESIGN.md §19.6, after C&C Generals): its formed model, its structure blocks, its
controller and its spawner.

The game's Particle Uplink Cannon, as modelled here: a sixteen-sided drum of light grey concrete on a wider plate, its
inside painted blue, eight emitters through its wall (a steel cone pointing in at the pit, a blue-capped coil housing
outside), a box of two fans on its right; inside, a hexagonal pit. At its left the control tower: a tan base, a grey
shaft ribbed near the top, a glazed cab with a blue band under a hipped glass roof and two tall antennas, a chain-link
yard in front of it. Behind, the operations building: blue-grey walls with tan corners, two fans by its door, two
satellite dishes on its roof, two conduits running from it to the drum. Charged, the focusing column rises out of the
pit (a hexagonal foot, a braced neck, a flared head), its core aglow and arcs leaping to it from the emitters' tips.

ParticleCannonBlockEntity has the same figures: where the pit's middle is and how high the core stands once raised.

Pixels: x right, y up, z back from the middle of the controller's cell (its bottom at y 0), the controller's own right
and back (so as you face the controller, +x is on your left); the controller is a console in the middle of the pad's
front edge. Laid out as the game's picture has it seen from the front: the drum at the front right, the tower at the
left and the operations building behind it.

    tools\\.venv\\Scripts\\python.exe tools\\assets\\particle_cannon.py
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))

from gb import materials as M  # noqa: E402
from gb import palette as P  # noqa: E402
from gb import panels as PN  # noqa: E402
from gb import paths  # noqa: E402
from gb.geo import Animation, GeoModel, rest_pose, save_animations  # noqa: E402
from gb.model import make_polygon, regular, write_json  # noqa: E402
from gb.tex import Tex  # noqa: E402
import launch as L  # noqa: E402
import multiblock_tools as MT  # noqa: E402
import scud_storm as SS  # noqa: E402
import structures as ST  # noqa: E402
import warheads as W  # noqa: E402

SIDES, ALL, NO_DOWN = SS.SIDES, SS.ALL, SS.NO_DOWN
F = "frame"
DECK_Y = 14

# --- the layout (ParticleCannonBlockEntity and GBSuperweapons have the same figures) ---------------------------------------------
#: The pad: 15x15 cells round the controller (front middle).
PAD = (-120.0, 120.0, -8.0, 232.0)
#: The drum's middle (two cells to the controller's left, six back); the hexagonal pit in it (corner radius, its floor).
RX, RZ = -32.0, 96.0
PIT_R, PIT_FLOOR = 36.0, 4.0
PIT_A = PIT_R * math.cos(math.pi / 6)
#: The drum: sixteen facets, its wall from apothem WALL_IN to WALL_OUT, up to WALL_TOP; the lip round its top; the plate
#: round its foot; the floor inside it.
SIDES16 = 16
WALL_IN, WALL_OUT, WALL_TOP = 58.0, 70.0, 46.0
LIP_IN, LIP_OUT, LIP_Y0, LIP_Y1 = 56.5, 71.5, 43.0, 47.5
PLATE_OUT, PLATE_TOP = 86.0, 17.0
FLOOR_TOP = 15.0
#: The emitters' bearings (degrees clockwise from the front, as the structure's Particle Emitter cells), their height.
EMITTERS = [45.0 * k for k in range(8)]
EMIT_Y = 31.0
#: The emitters' cones: from the wall's inside to their tips, pointing at the pit's middle.
CONE_BASE, CONE_LEN, CONE_R = WALL_IN - 1.5, 12.0, 4.2
#: The fan box on the drum's outside (its bearing: between the emitters on its far side from the tower, toward the back).
FAN_BEARING = 247.5
#: The focusing column, built raised: its foot from the pit's floor; its core's middle (the block entity's CORE_UP). At
#: rest it is telescoped down into the pit: squeezed to SQUEEZE of its height and sunk SINK (its core's top under the
#: pit's floor, its foot a block down).
COLUMN_TOP, CORE_Y, CORE_R = 49.0, 56.0, 7.0
SQUEEZE, SINK = 0.3, 18.5
#: The control tower (cells x 72..104, z 136..168) and the yard fenced in front of it.
TOWER = (72.0, 104.0, 136.0, 168.0)
TX, TZ = 88.0, 152.0
BASE_TOP, SHAFT_TOP, CAB_TOP = 40.0, 104.0, 126.0
YARD = (58.0, 118.0, 70.0, 134.0)
FENCE_H = 22.0
#: The operations building (cells x 8..104, z 168..232, its walls a little in), its height; its dishes (x, z, the bearing
#: they face: out over the front, toward the drum's side).
OPS = (10.0, 102.0, 168.0, 229.0)
OPS_TOP = 44.0
DISHES = [(82.0, 206.0, 330.0), (38.0, 198.0, 300.0)]
DISH_R, DISH_ELEVATION, DISH_PIVOT_Y = 14.0, 35.0, 60.0
#: The conduits from the operations building's front to the drum (from x on its front, to a bearing on the drum).
CONDUITS = [(62.0, 110.0), (46.0, 128.0)]
#: The animations' timings (seconds).
RAISE_T, LOWER_T, CHARGED_T = 3.0, 2.5, 2.4

# --- materials ----------------------------------------------------------------------------------------------------------------
UPLINK_GREY_RAMP = M.ramp("#33363b", "#454950", "#5a5e65", "#70747b", "#878b91", "#9ea2a7", "#b5b8bc", "#cbcdd0", "#dfe0e2")
DRUM_BLUE_RAMP = M.ramp("#171b2e", "#20263e", "#2a3250", "#353f63", "#424d76", "#515d88", "#636f99", "#7883aa", "#909bbc")
USA_BLUE_RAMP = M.ramp("#08142f", "#0c1c45", "#11265c", "#173275", "#1f3f8e", "#2a4fa6", "#3b64bd", "#557fd0", "#7a9ee0")
GLASS_RAMP = M.ramp("#0c1216", "#131d24", "#1a2a34", "#233744", "#2e4656", "#3b5768", "#4c6b7c", "#628292", "#7c9bab")
ROOFING_RAMP = M.ramp("#1c1d1f", "#26282a", "#313336", "#3d3f42", "#4a4c4f", "#57595c", "#65676a", "#747679", "#848689")

UPLINK = M.Material(ramp=UPLINK_GREY_RAMP, tone=0.66, mottle=0.04, grain=0.04, dither=0.7, edge_light=0.12, edge_dark=0.1, ao=0.6,
                    grime=0.12, streaks=0.12, falloff=0.04, despeckle=False)
DRUM_BLUE = M.painted(DRUM_BLUE_RAMP, tone=0.55, wear=0.18, streaks=0.08)
OPS_BLUE = M.painted(DRUM_BLUE_RAMP, tone=0.62, wear=0.22, streaks=0.1, grime=0.14)
USA_BLUE = M.painted(USA_BLUE_RAMP, tone=0.6, wear=0.15)
TAN = M.painted(SS.TAN_RAMP, tone=0.62, wear=0.25, rust=0.04, streaks=0.06)
CONCRETE = M.Material(ramp=UPLINK_GREY_RAMP, tone=0.6, mottle=0.06, grain=0.05, dither=0.85, edge_light=0.1, edge_dark=0.08, ao=0.6,
                      grime=0.1, dust=0.05, pits=0.01, despeckle=False)
GUNMETAL = M.steel(tone=0.36, rust=0.05)
DARK = M.steel(tone=0.26, rust=0.06)
BRIGHT = M.steel(tone=0.78, brushed=0.12)
GALV = M.galvanized(tone=0.55)
WHITE = M.white_paint(tone=0.74, wear=0.12)
GLASS = M.Material(ramp=GLASS_RAMP, tone=0.5, mottle=0.03, grain=0.02, dither=0.5, edge_light=0.25, edge_dark=0.05, ao=0.3, grime=0.0)
ROOFING = M.Material(ramp=ROOFING_RAMP, tone=0.5, mottle=0.08, grain=0.1, dither=0.9, edge_light=0.06, edge_dark=0.06, ao=0.5, dust=0.06,
                     despeckle=False)
RUBBER = M.rubber(tone=0.45)

CORE_GLOW = (232, 244, 255, 255)
ARC_GLOW = (196, 220, 255, 255)
TIP_GLOW = (150, 196, 255, 255)
LAMP_GLOW = (255, 64, 40, 255)
CLEAR = (0, 0, 0, 0)


def ring(g, name, r, y0, y1, cx, cz, bone=F, caps=("up",), sides=None, stagger=None):
    return SS.ring(g, name, r, y0, y1, cx, cz, bone, caps=caps, sides=sides, stagger=stagger)


def facets(g: GeoModel, name: str, a_in: float, a_out: float, y0: float, y1: float, faces=("north", "south", "up"),
           bone: str = F, sink: float = 0.15) -> list[str]:
    """A ring of sixteen wall panels round the drum's middle (apothem a_in to a_out), each as wide as a facet at its
    outer face and turned to its bearing; their caps, which overlap toward the inside, one flat group each way (W.FLAT:
    one even colour, nothing to flicker; before, every other panel sat a little lower). Built facing the front: north is
    a panel's outside, south its inside."""
    half = a_out * math.tan(math.pi / SIDES16)
    names = []
    caps = tuple(d for d in ("up", "down") if d in faces)
    own = tuple(d for d in faces if not (W.POLY and d in caps))
    for k in range(SIDES16):
        n = f"{name}{k}"
        rot = {"origin": (RX, y0, RZ), "axis": "y", "angle": -k * 360.0 / SIDES16} if k else None
        dip = 0.0 if (W.POLY or W.FLAT) else sink * (k % 2)
        lift = dip if "down" in faces else 0.0
        W.mark_round(g, g.box(n, (RX - half, y0 + lift, RZ - a_out), (RX + half, y1 - dip, RZ - a_in), faces=own, rotation=rot, bone=bone))
        for d in ALL:
            if d not in own:
                g.open_ok.add((n, d))
        if W.FLAT and not W.POLY:
            for d in caps:
                g.flat(f"{name}:{d}", n, d)
        names.append(n)
    if W.POLY:
        # The panels' caps one ring each way (warheads.annulus), the first panel's outside facing north.
        for d in caps:
            names.append(W.annulus(SS.OnBone(g, bone), f"{name}_{d}", "y", (RX, RZ), a_in, a_out, SIDES16,
                                   y1 if d == "up" else y0, d, (0.0, 0.0, -1.0)))
    return names


def prism(g: GeoModel, name: str, a: float, y0: float, y1: float, cx: float, cz: float, bone: str, sides: int = 6, caps=("up",),
          step: float = 0.15) -> list[str]:
    """A prism of {sides} (even) sides and apothem a: planks a facet wide turned 360/sides degrees apart (each shows its
    two ends, opposite facets, and its caps), its caps one flat group (W.FLAT; before, each {step} lower than the last,
    so no two of its caps shared a plane where nothing covers its middle: a ball's top, a hexagon's)."""
    s = a * math.tan(math.pi / sides)
    names = []
    faces = ("north", "south") + (() if W.POLY else tuple(caps))
    for k in range(sides // 2):
        n = f"{name}_{k}"
        sink = 0.0 if (W.POLY or W.FLAT) else step * k
        rot = {"origin": (cx, y0, cz), "axis": "y", "angle": -360.0 / sides * k} if k else None
        W.mark_round(g, g.box(n, (cx - s, y0 - sink, cz - a), (cx + s, y1 - sink, cz + a), faces=faces, rotation=rot, bone=bone))
        for d in ALL:
            if d not in faces:
                g.open_ok.add((n, d))
        if W.FLAT and not W.POLY:
            for d in caps:
                g.flat(f"{name}:{d}", n, d)
        names.append(n)
    if W.POLY:
        # Its caps one polygon each, its first plank's ends facing north and south.
        for d in caps:
            pts = regular((cx, y1 if d == "up" else y0, cz), "y", (0.0, 0.0, -1.0), sides, a)
            names.append(make_polygon(SS.OnBone(g, bone), f"{name}_{d}", [pts], d).name)
    return names


def hexagon(g: GeoModel, name: str, a: float, y0: float, y1: float, cx: float, cz: float, bone: str, caps=("up",)) -> list[str]:
    return prism(g, name, a, y0, y1, cx, cz, bone, sides=6, caps=caps)


def sphere(g: GeoModel, name: str, r: float, cy: float, cx: float, cz: float, bone: str, steps: int = 7) -> list[str]:
    """A ball of rings, each its own slice of a sphere: those below the middle wider going up (their bottoms show),
    those above narrower (their tops show); each at least 0.12 px inside the one beside it toward the middle."""
    names = []
    hs = [cy - r + 2 * r * i / steps for i in range(steps + 1)]
    mid = steps // 2
    radii = [math.sqrt(max(0.0, r * r - ((hs[i] + hs[i + 1]) / 2 - cy) ** 2)) for i in range(steps)]
    for i in range(steps):
        if i < mid:
            radii[i] = min(radii[i], radii[i + 1] - 0.12)
    for i in range(steps - 1, mid, -1):
        radii[i] = min(radii[i], radii[i - 1] - 0.12)
    for i in range(steps):
        caps = ("down",) if i < mid else ("up",) if i > mid else ("up", "down")
        names += prism(g, f"{name}{i}", round(max(0.8, radii[i]), 3), round(hs[i], 3), round(hs[i + 1], 3), cx, cz, bone, sides=8, caps=caps)
    return names


def bar(g: GeoModel, name: str, a, b, w: float, bone: str, d: float | None = None, faces=ALL) -> str:
    """A bar w by d along the ground from a to b (x, z; at heights y0..y1 in a[2], a[3]), built toward the front from a
    and turned to point at b."""
    ax, az, y0, y1 = a
    bx, bz = b
    length = math.hypot(bx - ax, bz - az)
    bearing = math.degrees(math.atan2(bx - ax, -(bz - az)))
    d = w if d is None else d
    rot = {"origin": (ax, y0, az), "axis": "y", "angle": -bearing}
    g.box(name, (ax - w / 2, y0, az - length), (ax + w / 2, y1, az), faces=faces, rotation=rot, bone=bone)
    return name


def drum_point(bearing: float, apothem: float) -> tuple[float, float]:
    a = math.radians(bearing)
    return RX + apothem * math.sin(a), RZ - apothem * math.cos(a)


# --- the model ----------------------------------------------------------------------------------------------------------------
def build_pad(g: GeoModel, p: SS.Parts) -> None:
    """The pad's deck round a hole for the pit; the console; the Energy Port's housing."""
    x0, x1, z0, z1 = PAD
    hx0, hx1, hz0, hz1 = RX - PIT_R, RX + PIT_R, RZ - 32.0, RZ + 32.0
    for n, a, b in (("deck_front", (x0, 0, z0), (x1, DECK_Y, hz0)), ("deck_back", (x0, 0, hz1), (x1, DECK_Y, z1)),
                    ("deck_left", (x0, 0, hz0), (hx0, DECK_Y, hz1)), ("deck_right", (hx1, 0, hz0), (x1, DECK_Y, hz1))):
        g.box(n, a, b, faces=("north", "south", "east", "west", "up"), bone=F)
        g.open_ok.add((n, "down"))
        p.add("deck", n)
    g.box("console", (-5, DECK_Y, -7), (5, 22, -2), faces=NO_DOWN, bone=F)
    g.box("e_housing", (27, DECK_Y, -8), (37, 19, -3), faces=NO_DOWN, bone=F)


def build_drum(g: GeoModel, p: SS.Parts) -> None:
    """The plate, the wall and its lip, the floor inside round the hexagonal pit, the pit's floor."""
    p.add("plate", facets(g, "plate", WALL_OUT, PLATE_OUT, DECK_Y, PLATE_TOP, faces=("north", "up")))
    p.add("wall", facets(g, "wall", WALL_IN, WALL_OUT, DECK_Y, WALL_TOP, faces=("north", "south")))
    p.add("lip", facets(g, "lip", LIP_IN, LIP_OUT, LIP_Y0, LIP_Y1, faces=("north", "south", "up", "down")))
    # The floor inside: six slabs round the pit, each from its edge of the hexagon out under the wall, their tops one
    # flat group (they overlap toward the wall; W.FLAT); their inner ends are the pit's walls.
    for k in range(6):
        n = f"floor{k}"
        rot = {"origin": (RX, PIT_FLOOR, RZ), "axis": "y", "angle": -60.0 * k} if k else None
        flat_top = W.POLY or W.FLAT
        g.box(n, (RX - 30, PIT_FLOOR, RZ - 62), (RX + 30, FLOOR_TOP - (0.0 if flat_top else 0.15 * (k % 2)), RZ - PIT_A),
              faces=("south",) if W.POLY else ("up", "south"), rotation=rot, bone=F)
        for d in ("north", "east", "west", "down") + (("up",) if W.POLY else ()):
            g.open_ok.add((n, d))
        if W.FLAT and not W.POLY:
            g.flat("floor:up", n, "up")
        p.add("floor", n)
    if W.POLY:
        # Its top one surface from the pit's hexagonal edge to the wall's inside (warheads.between).
        p.add("floor", W.between(SS.OnBone(g, F), "floor_up", "y", (RX, RZ), FLOOR_TOP, "up",
                                 (6, PIT_A, (0.0, 0.0, -1.0)), (SIDES16, WALL_IN, (0.0, 0.0, -1.0))))
    # The pit's floor closed underneath too, painted as its top (the deck has a hole under it: seen from below, the
    # drum showed through; after the user's look).
    pit = g.box("pit_floor", (RX - PIT_R, 2, RZ - 32), (RX + PIT_R, PIT_FLOOR, RZ + 32), faces=("up", "down"), bone=F)
    g.share(pit, "down", pit, "up")
    for d in ("north", "south", "east", "west"):
        g.open_ok.add(("pit_floor", d))


def build_emitters(g: GeoModel, p: SS.Parts) -> None:
    """Eight emitters through the wall, each built at the front and turned to its bearing: a collar on the wall's inside
    and a steel cone out of it pointing at the pit's middle, its tip's glow (lit while charged); outside, a flange and a
    coil housing with a blue cap."""
    for k, bearing in enumerate(EMITTERS):
        e = f"emitter{k}"
        g.bone(e, pivot=(RX, 0, RZ), parent=F, rotation=(0, -bearing, 0))
        g.box(f"collar{k}", (RX - 6, EMIT_Y - 6, RZ - WALL_IN), (RX + 6, EMIT_Y + 6, RZ - CONE_BASE),
              faces=("south", "east", "west", "up", "down"), bone=e)
        g.box(f"flange{k}", (RX - 6, EMIT_Y - 6, RZ - WALL_OUT - 1.5), (RX + 6, EMIT_Y + 6, RZ - WALL_OUT),
              faces=("north", "east", "west", "up", "down"), bone=e)
        p.add("collar", f"collar{k}", f"flange{k}")
        cone, can, tip = f"cone{k}", f"can{k}", f"tip{k}"
        g.bone(cone, pivot=(RX, EMIT_Y, RZ - CONE_BASE), parent=e, rotation=(90, 0, 0))
        band = ring(g, f"coneband{k}_", 4.7, EMIT_Y - 1, EMIT_Y + 1.5, RX, RZ - CONE_BASE, bone=cone, caps=("up",), sides=8, stagger="full")
        body, top = SS.dome(g, f"cone{k}_", CONE_R, EMIT_Y + 1.5, CONE_LEN - 1.5, RX, RZ - CONE_BASE, steps=6, bone=cone, shape="cone",
                            min_r=0.7)
        p.add("cone_band", band)
        p.add("cone", body)
        g.bone(tip, pivot=(RX, top, RZ - CONE_BASE), parent=cone)
        g.box(f"tipglow{k}", (RX - 0.9, top - 2.2, RZ - CONE_BASE - 0.9), (RX + 0.9, top + 0.8, RZ - CONE_BASE + 0.9), faces=ALL, bone=tip,
              shade=False)
        p.add("tip", f"tipglow{k}")
        g.bone(can, pivot=(RX, EMIT_Y, RZ - WALL_OUT - 1.5), parent=e, rotation=(-90, 0, 0))
        y0 = EMIT_Y
        p.add("can", ring(g, f"canbody{k}_", 4.0, y0 - 1, y0 + 7, RX, RZ - WALL_OUT - 1.5, bone=can, caps=("up",), sides=8, stagger="full"))
        p.add("can_band", ring(g, f"canband{k}_", 4.35, y0 + 1.5, y0 + 2.5, RX, RZ - WALL_OUT - 1.5, bone=can, caps=("up", "down"), sides=8,
                               stagger="full"))
        names, _ = SS.dome(g, f"cap{k}_", 4.6, y0 + 7, 3.2, RX, RZ - WALL_OUT - 1.5, steps=3, bone=can, shape="dome", min_r=1.6)
        p.add("cap", names)


def fan(g: GeoModel, p: SS.Parts, name: str, cx: float, cy: float, z_face: float, r: float, parent: str) -> None:
    """A round fan on a north face at z_face: its housing (a short open tube), its four blades (on their own bone, turned
    about the fan's axis by the animations) and the dark backing behind them (painted on the face: see fan_backing)."""
    mount = f"{name}_mount"
    g.bone(mount, pivot=(cx, cy, z_face), parent=parent, rotation=(-90, 0, 0))
    p.add("fan_ring", ring(g, f"{name}_ring", r, cy - 0.5, cy + 1.5, cx, z_face, bone=mount, caps=(), sides=16))
    FANS.append((name, cx, cy, r))
    g.bone(name, pivot=(cx, cy, z_face), parent=mount)
    g.box(f"{name}_hub", (cx - 1.2, cy - 0.5, z_face - 1.2), (cx + 1.2, cy + 1.0, z_face + 1.2), faces=NO_DOWN, bone=name)
    for i, (a, b) in enumerate((((cx - r + 0.8, z_face - 0.9), (cx + r - 0.8, z_face + 0.9)), ((cx - 0.9, z_face - r + 0.8), (cx + 0.9, z_face + r - 0.8)))):
        g.box(f"{name}_blade{i}", (a[0], cy - 0.3 + 0.1 * i, a[1]), (b[0], cy + 0.3 + 0.1 * i, b[1]), faces=("up", "down", "north", "south", "east", "west"),
              bone=name)
        p.add("blade", f"{name}_blade{i}")
    p.add("hub", f"{name}_hub")


#: Every fan: its name, its middle (x, y) on its face, its radius (the backing behind it is painted there).
FANS: list[tuple[str, float, float, float]] = []


def build_drum_fans(g: GeoModel, p: SS.Parts) -> None:
    """The box of two fans on the drum's right side."""
    bone = "drumfans"
    g.bone(bone, pivot=(RX, 0, RZ), parent=F, rotation=(0, -FAN_BEARING, 0))
    z_face = RZ - WALL_OUT - 7
    g.box("fanbox", (RX - 12, 22, z_face), (RX + 12, 40, RZ - WALL_OUT + 1), faces=("north", "east", "west", "up", "down"), bone=bone)
    p.add("fanbox", "fanbox")
    for i, dx in enumerate((-6.0, 6.0)):
        fan(g, p, f"drumfan{i}", RX + dx, 31.0, z_face, 4.6, bone)


def build_column(g: GeoModel, p: SS.Parts) -> None:
    """The focusing column, built raised: a hexagonal foot banded at its top, a braced square neck, a flared hexagonal
    head with a collar, and its core, a ball of light (its glow its own bone, pulsing while charged)."""
    g.bone("column", pivot=(RX, PIT_FLOOR, RZ), parent=F)
    p.add("column_foot", hexagon(g, "colfoot", 9.0, PIT_FLOOR, 20.0, RX, RZ, "column"))
    p.add("column_band", hexagon(g, "colband", 9.6, 18.0, 21.5, RX, RZ, "column"))
    g.box("colneck", (RX - 4.5, 21.5, RZ - 4.5), (RX + 4.5, 42.0, RZ + 4.5), faces=SIDES, bone="column")
    p.add("column_neck", "colneck")
    for s, side in ((-1, "w"), (1, "e")):                       # the neck's braces, crossed on its sides
        for i, (za, zb) in enumerate(((RZ - 6.0, RZ + 6.0), (RZ + 6.0, RZ - 6.0))):
            n = f"colbrace{side}{i}"
            SS.strut(g, n, (RX + s * 5.4, 23.0, za), (RX + s * 5.4, 40.5, zb), 0.9, "column")
            p.add("column_brace", n)
    for s, side in ((-1, "n"), (1, "s")):
        n = f"colbrace_{side}"
        g.box(n, (RX - 5.4, 30.5, RZ + s * 5.4 - 0.45), (RX + 5.4, 31.4, RZ + s * 5.4 + 0.45), faces=ALL, bone="column")
        p.add("column_brace", n)
    p.add("column_head", hexagon(g, "colhead", 10.5, 42.0, 46.5, RX, RZ, "column", caps=("up", "down")))
    p.add("column_collar", prism(g, "colcollar", 6.0, 46.5, COLUMN_TOP, RX, RZ, "column", sides=8))
    g.bone("core", pivot=(RX, CORE_Y, RZ), parent="column")
    p.add("core", sphere(g, "core", CORE_R, CORE_Y, RX, RZ, "core"))


def build_arcs(g: GeoModel, p: SS.Parts) -> None:
    """The arcs leaping from each emitter's tip to the core: a jagged channel of short bars on its own bone (turned to the
    emitter's bearing), shown while charged."""
    tip_z = RZ - CONE_BASE + CONE_LEN
    for k, bearing in enumerate(EMITTERS):
        bone = f"arc{k}"
        g.bone(bone, pivot=(RX, 0, RZ), parent=F, rotation=(0, -bearing, 0))
        rng = np.random.default_rng(300 + k)
        a = (EMIT_Y + 0.5, tip_z)
        b = (CORE_Y - 1.5, RZ - CORE_R + 1.0)
        pts = [a]
        n = 8
        for i in range(1, n):
            t = i / n
            kink = math.sin(math.pi * t)                        # (pinned at its ends, wildest mid-way)
            y = a[0] + (b[0] - a[0]) * t + rng.uniform(-3.2, 3.2) * kink
            z = a[1] + (b[1] - a[1]) * t + rng.uniform(-2.2, 2.2) * kink
            pts.append((y, z))
        pts.append(b)
        for i in range(n):
            (y0, z0), (y1, z1) = pts[i], pts[i + 1]
            name = f"arc{k}_{i}"
            SS.strut(g, name, (RX + rng.uniform(-0.6, 0.6), y0, z0), (RX, y1, z1), 0.45, bone)
            p.add("arc", name)


def build_tower(g: GeoModel, p: SS.Parts) -> None:
    """The control tower: a tan base with its door, a grey shaft ribbed near the top, the glazed cab with a blue band, its
    hipped glass roof, two antennas (a red lamp on the taller); the chain-link yard in front of it."""
    x0, x1, z0, z1 = TOWER
    g.box("tower_base", (x0, 0, z0), (x1, BASE_TOP, z1), faces=NO_DOWN, bone=F)
    g.box("tower_shaft", (TX - 12, BASE_TOP, TZ - 12), (TX + 12, SHAFT_TOP, TZ + 12), faces=SIDES, bone=F)
    g.box("tower_ribs", (TX - 12.8, SHAFT_TOP - 14, TZ - 12.8), (TX + 12.8, SHAFT_TOP, TZ + 12.8), faces=SIDES + ("down",), bone=F)
    g.box("tower_cab", (TX - 17, SHAFT_TOP, TZ - 17), (TX + 17, CAB_TOP, TZ + 17), faces=ALL, bone=F)
    g.box("tower_band", (TX - 17.6, SHAFT_TOP + 3, TZ - 17.6), (TX + 17.6, SHAFT_TOP + 5.5, TZ + 17.6), faces=ALL, bone=F)
    g.box("tower_door", (TX - 5, 0, z0 - 1.2), (TX + 5, 22, z0), faces=("north", "east", "west", "up"), bone=F)
    g.box("tower_canopy", (TX - 7, 22, z0 - 4), (TX + 7, 23.5, z0), faces=ALL, bone=F)
    p.add("tan", "tower_base")
    p.add("grey", "tower_shaft")
    p.add("ribs", "tower_ribs")
    p.add("cab", "tower_cab")
    p.add("blue", "tower_band")
    p.add("door", "tower_door")
    p.add("canopy", "tower_canopy")
    # The hipped roof: four glass slabs leaning in from the cab's eaves to a flat top, the top's cap.
    # (A slab built flat from its eave inward and turned up about the eave: north and south about x, west and east about
    # z; the east and west ones a little thicker and higher, so where the slabs cross at the hips no two faces meet.)
    pitch, eave, top_half = 38.0, 17.0, 8.0
    slope = (eave - top_half) / math.cos(math.radians(pitch)) + 1.0
    for i, (axis, s) in enumerate((("x", -1), ("x", 1), ("z", -1), ("z", 1))):
        n = f"roof{i}"
        if axis == "x":
            zf = TZ + s * eave
            rot = {"origin": (TX, CAB_TOP, zf), "axis": "x", "angle": s * pitch}
            g.box(n, (TX - eave - 0.3, CAB_TOP, min(zf, zf - s * slope)), (TX + eave + 0.3, CAB_TOP + 1.6, max(zf, zf - s * slope)),
                  faces=("up", "down", "north", "south", "east", "west"), rotation=rot, bone=F)
        else:
            xf = TX + s * eave
            rot = {"origin": (xf, CAB_TOP, TZ), "axis": "z", "angle": -s * pitch}
            g.box(n, (min(xf, xf - s * slope), CAB_TOP + 0.25, TZ - eave + 0.2), (max(xf, xf - s * slope), CAB_TOP + 1.95, TZ + eave - 0.2),
                  faces=("up", "down", "east", "west", "north", "south"), rotation=rot, bone=F)
        p.add("roof_glass", n)
    cap_y = CAB_TOP + (eave - top_half) * math.tan(math.radians(pitch))
    g.box("roof_cap", (TX - top_half - 1, cap_y - 0.5, TZ - top_half - 1), (TX + top_half + 1, cap_y + 1.5, TZ + top_half + 1), faces=ALL, bone=F)
    p.add("roof_cap", "roof_cap")
    for i, (dx, dz, h) in enumerate(((-6.0, -4.0, 66.0), (6.0, 4.0, 52.0))):
        n = f"antenna{i}"
        g.box(n, (TX + dx - 0.7, cap_y + 1.5, TZ + dz - 0.7), (TX + dx + 0.7, cap_y + 1.5 + h, TZ + dz + 0.7), faces=NO_DOWN, bone=F)
        g.box(f"antenna_bar{i}", (TX + dx - 3, cap_y + h - 12, TZ + dz - 0.4), (TX + dx + 3, cap_y + h - 11, TZ + dz + 0.4), faces=ALL, bone=F)
        p.add("antenna", n, f"antenna_bar{i}")
    g.bone("lamp", pivot=(TX - 6, cap_y + 67.5, TZ - 4), parent=F)
    g.box("lamp", (TX - 7.1, cap_y + 67.5, TZ - 5.1), (TX - 4.9, cap_y + 69.7, TZ - 2.9), faces=NO_DOWN, bone="lamp", shade=False)
    p.add("lamp", "lamp")
    build_fence(g, p)


def build_fence(g: GeoModel, p: SS.Parts) -> None:
    """A chain-link yard in front of the tower: posts a cell apart, mesh panels between them, a top rail; open toward the
    tower."""
    x0, x1, z0, z1 = YARD
    corners = [(x0, z1), (x0, z0), (x1, z0), (x1, z1)]
    k = 0
    for (ax, az), (bx, bz) in zip(corners, corners[1:]):
        length = math.hypot(bx - ax, bz - az)
        steps = max(1, round(length / 16))
        for i in range(steps):
            px0, pz0 = ax + (bx - ax) * i / steps, az + (bz - az) * i / steps
            px1, pz1 = ax + (bx - ax) * (i + 1) / steps, az + (bz - az) * (i + 1) / steps
            n = f"mesh{k}"                                     # (one face: the model is drawn from both sides)
            if abs(bx - ax) < 1e-6:                             # along z
                g.box(n, (px0 - 0.05, DECK_Y, min(pz0, pz1) + 0.6), (px0 + 0.05, DECK_Y + FENCE_H - 1, max(pz0, pz1) - 0.6),
                      faces=("west",), bone=F)
            else:
                g.box(n, (min(px0, px1) + 0.6, DECK_Y, pz0 - 0.05), (max(px0, px1) - 0.6, DECK_Y + FENCE_H - 1, pz0 + 0.05),
                      faces=("north",), bone=F)
            for d in ALL:
                g.open_ok.add((n, d))
            p.add("mesh", n)
            post = f"post{k}"
            g.box(post, (px0 - 0.8, DECK_Y, pz0 - 0.8), (px0 + 0.8, DECK_Y + FENCE_H + 0.5, pz0 + 0.8), faces=NO_DOWN, bone=F)
            p.add("post", post)
            k += 1
        rail = f"rail{k}"                                       # (ends inside the corner posts, short of each other)
        if abs(bx - ax) < 1e-6:
            g.box(rail, (ax - 0.5, DECK_Y + FENCE_H - 1, min(az, bz) + 0.6), (ax + 0.5, DECK_Y + FENCE_H, max(az, bz) - 0.6), faces=ALL, bone=F)
        else:
            g.box(rail, (min(ax, bx) + 0.6, DECK_Y + FENCE_H - 1, az - 0.5), (max(ax, bx) - 0.6, DECK_Y + FENCE_H, az + 0.5), faces=ALL, bone=F)
        p.add("post", rail)
    last = corners[-1]
    g.box("post_end", (last[0] - 0.8, DECK_Y, last[1] - 0.8), (last[0] + 0.8, DECK_Y + FENCE_H + 0.5, last[1] + 0.8), faces=NO_DOWN, bone=F)
    p.add("post", "post_end")


def build_ops(g: GeoModel, p: SS.Parts) -> None:
    """The operations building: blue-grey walls, tan corners, a parapet round its flat roof; its door and its two fans
    in a tan panel on its front; an air-conditioning unit and the two satellite dishes on its roof."""
    x0, x1, z0, z1 = OPS
    g.box("ops", (x0, 0, z0), (x1, OPS_TOP, z1), faces=NO_DOWN, bone=F)
    p.add("ops", "ops")
    for i, (x, z) in enumerate(((x0, z0), (x1, z0), (x0, z1), (x1, z1))):
        n = f"ops_corner{i}"
        g.box(n, (x - 2.5, 0, z - 2.5), (x + 2.5, OPS_TOP + 3.5, z + 2.5), faces=NO_DOWN, bone=F)
        p.add("tan", n)
    for n, a, b in (("parapet_n", (x0 + 2.5, OPS_TOP, z0 - 0.6), (x1 - 2.5, OPS_TOP + 3, z0 + 1.5)),
                    ("parapet_s", (x0 + 2.5, OPS_TOP, z1 - 1.5), (x1 - 2.5, OPS_TOP + 3, z1 + 0.6)),
                    ("parapet_w", (x0 - 0.6, OPS_TOP, z0 + 2.5), (x0 + 1.5, OPS_TOP + 3, z1 - 2.5)),
                    ("parapet_e", (x1 - 1.5, OPS_TOP, z0 + 2.5), (x1 + 0.6, OPS_TOP + 3, z1 - 2.5))):
        g.box(n, a, b, faces=NO_DOWN, bone=F)
        p.add("parapet", n)
    g.box("ops_door", (x1 - 26, 0, z0 - 1.2), (x1 - 14, 24, z0), faces=("north", "east", "west", "up"), bone=F)
    p.add("door", "ops_door")
    g.box("fan_panel", (x0 + 6, 10, z0 - 1.5), (x0 + 34, 30, z0), faces=("north", "east", "west", "up", "down"), bone=F)
    p.add("tan_panel", "fan_panel")
    for i, cx in enumerate((x0 + 13.0, x0 + 27.0)):
        fan(g, p, f"opsfan{i}", cx, 20.0, z0 - 1.5, 5.6, F)
    g.box("aircon", (x0 + 40, OPS_TOP, z1 - 22), (x0 + 54, OPS_TOP + 8, z1 - 8), faces=NO_DOWN, bone=F)
    p.add("aircon", "aircon")
    for i, (dx, dz, bearing) in enumerate(DISHES):
        build_dish(g, p, i, dx, dz, bearing)


def build_dish(g: GeoModel, p: SS.Parts, i: int, x: float, z: float, bearing: float) -> None:
    """A satellite dish on a pedestal and yoke: a shallow bowl of rings (its back toward the roof), its feed held out in
    front of it on three struts; turned to its bearing and tilted up to the sky."""
    g.box(f"pedestal{i}", (x - 3, OPS_TOP, z - 3), (x + 3, DISH_PIVOT_Y - 3, z + 3), faces=NO_DOWN, bone=F)
    p.add("pedestal", f"pedestal{i}")
    yaw, tilt = f"dish{i}_yaw", f"dish{i}"
    g.bone(yaw, pivot=(x, DISH_PIVOT_Y, z), parent=F, rotation=(0, -bearing, 0))
    g.box(f"yoke{i}", (x - 4, DISH_PIVOT_Y - 3.5, z - 1.5), (x + 4, DISH_PIVOT_Y - 2, z + 1.5), faces=ALL, bone=yaw)
    p.add("pedestal", f"yoke{i}")
    g.bone(tilt, pivot=(x, DISH_PIVOT_Y, z), parent=yaw, rotation=(90 + DISH_ELEVATION, 0, 0))
    y0 = DISH_PIVOT_Y
    bowl = []
    for j, (r, h) in enumerate(((DISH_R, 1.0), (DISH_R - 2.2, 1.0), (DISH_R - 5.0, 1.0), (DISH_R - 8.5, 1.2))):
        bowl += ring(g, f"dish{i}_{j}", r, y0 + j * 1.0, y0 + j * 1.0 + h, x, z, bone=tilt, caps=("up", "down") if j == 0 else ("up",),
                     stagger="full")
    p.add("dish", bowl)
    g.box(f"dish{i}_hub", (x - 2, y0 + 4.2, z - 2), (x + 2, y0 + 6.2, z + 2), faces=NO_DOWN, bone=tilt)
    p.add("pedestal", f"dish{i}_hub")
    feed_y = y0 - 9.0
    g.box(f"dish{i}_feed", (x - 1.3, feed_y - 2.5, z - 1.3), (x + 1.3, feed_y, z + 1.3), faces=ALL, bone=tilt)
    p.add("feed", f"dish{i}_feed")
    for s, a in enumerate((0.0, 120.0, 240.0)):
        sb = f"dish{i}_strut{s}"
        g.bone(sb, pivot=(x, y0, z), parent=tilt, rotation=(0, a, 0))
        n = f"dish{i}_leg{s}"
        SS.strut(g, n, (x, y0 - 0.2, z - DISH_R + 1.2), (x, feed_y, z - 0.8), 0.6, sb)
        p.add("feed", n)


def build_conduits(g: GeoModel, p: SS.Parts) -> None:
    """Two square conduits along the deck from the operations building's front to the drum, flanged at both ends."""
    for i, (x, bearing) in enumerate(CONDUITS):
        start = (x, OPS[2])
        end = drum_point(bearing, WALL_OUT - 2)
        bar(g, f"conduit{i}", (start[0], start[1] + 0.5, DECK_Y, DECK_Y + 8), end, 8.0, F, faces=("up", "east", "west"))
        p.add("conduit", f"conduit{i}")
        for j, (cx, cz) in enumerate((start, drum_point(bearing, WALL_OUT + 1.5))):
            n = f"conduit{i}_flange{j}"
            heading = math.degrees(math.atan2(end[0] - start[0], -(end[1] - start[1])))
            rot = {"origin": (cx, DECK_Y, cz), "axis": "y", "angle": -heading}
            g.box(n, (cx - 5.5, DECK_Y, cz - 1.2 - (0 if j else 1.5)), (cx + 5.5, DECK_Y + 10, cz + 1.2 - (0 if j else 1.5)),
                  faces=ALL, rotation=rot, bone=F)
            p.add("collar", n)


def build() -> tuple[GeoModel, SS.Parts]:
    SS.ROUND.clear()
    FANS.clear()
    g = GeoModel("particle_uplink_cannon", tex_size=(1024, 1024))
    g.open_ok = set()
    g.bone(F)
    p = SS.Parts()
    build_pad(g, p)
    build_drum(g, p)
    build_emitters(g, p)
    build_drum_fans(g, p)
    build_column(g, p)
    build_arcs(g, p)
    build_tower(g, p)
    build_ops(g, p)
    build_conduits(g, p)
    return g, p


# --- painting -----------------------------------------------------------------------------------------------------------------
def paint_model(g: GeoModel, p: SS.Parts) -> tuple[Tex, Tex]:
    from gb import machine_kit as K  # noqa: PLC0415
    k = K.Kit(g, {}, seed=823, ao_reach=3.0, turned=True)
    W.round_faces(k.S, SS.ROUND, top=True, bottom=False, foot=True)
    S = k.S
    tex = Tex(g.tex_w, g.tex_h)
    glow = Tex(g.tex_w, g.tex_h)
    paint_deck(S, tex, p["deck"])
    console(S, g, tex)
    L._power_housing(S, g, tex, "e_housing", plug="north")
    # The drum: light grey outside with panel seams and dark vents between the emitters; blue inside; the lip and plate
    # light; the floor inside grey, the pit's walls steel with a hazard band, its floor dark grating.
    S.paint(tex, p["plate"], CONCRETE)
    S.overlay(tex, p["plate"], plate_joints, only=("up",))
    S.paint(tex, p["wall"], UPLINK, only=("north",))
    S.overlay(tex, p["wall"], wall_outside, only=("north",))
    S.paint(tex, p["wall"], DRUM_BLUE, only=("south",))
    S.overlay(tex, p["wall"], lambda f, img: M.seams(f, img, every=14, axis="v", light=False), only=("south",))
    S.paint(tex, p["lip"], UPLINK)
    S.paint(tex, p["floor"], CONCRETE, only=("up",))
    S.overlay(tex, p["floor"], floor_marks, only=("up",))
    S.paint(tex, p["floor"], M.steel(tone=0.46, rust=0.06), only=("south",))
    S.overlay(tex, p["floor"], pit_wall, only=("south",))
    S.paint(tex, "pit_floor", DARK)
    S.overlay(tex, "pit_floor", lambda f, img: M.grid(f, img, every=2, depth=0.45), only=("up",))
    # The emitters.
    S.paint(tex, p["collar"], GUNMETAL)
    S.paint(tex, p["cone_band"], DARK)
    S.paint(tex, p["cone"], BRIGHT)
    S.paint(tex, p["tip"], M.Material(ramp=W.ramp9(P.CHERENKOV), tone=0.75))
    for n in p["tip"]:
        g.paint_all(glow, n, lambda t, r, d: t.rect(r[0], r[1], r[2], r[3], TIP_GLOW))
    S.paint(tex, p["can"], GUNMETAL)
    S.paint(tex, p["can_band"], DARK)
    S.paint(tex, p["cap"], USA_BLUE)
    # The fans.
    S.paint(tex, p["fanbox"], UPLINK)
    S.overlay(tex, p["fanbox"], fan_backing, only=("north",))
    S.paint(tex, p["fan_ring"], DARK)
    S.paint(tex, p["blade"] + p["hub"], M.steel(tone=0.55, brushed=0.06))
    # The column and its core.
    S.paint(tex, p["column_foot"] + p["column_head"], GUNMETAL)
    S.paint(tex, p["column_band"] + p["column_collar"], USA_BLUE)
    S.paint(tex, p["column_neck"], M.steel(tone=0.5, brushed=0.08))
    S.paint(tex, p["column_brace"], DARK)
    S.paint(tex, p["core"], M.Material(ramp=W.ramp9(P.EMP), tone=0.85))
    for n in p["core"]:
        g.paint_all(glow, n, lambda t, r, d: t.rect(r[0], r[1], r[2], r[3], CORE_GLOW))
        g.paint_all(tex, n, lambda t, r, d: t.rect(r[0], r[1], r[2], r[3], CORE_GLOW))
    for n in p["arc"]:
        g.paint_all(tex, n, lambda t, r, d: t.rect(r[0], r[1], r[2], r[3], ARC_GLOW))
        g.paint_all(glow, n, lambda t, r, d: t.rect(r[0], r[1], r[2], r[3], ARC_GLOW))
    # The tower.
    S.paint(tex, p["tan"], TAN)
    S.paint(tex, p["grey"], UPLINK)
    S.overlay(tex, p["grey"], lambda f, img: M.seams(f, img, every=16, axis="v", light=False), only=SIDES)
    S.paint(tex, p["ribs"], UPLINK)
    S.overlay(tex, p["ribs"], ribbed, only=SIDES)
    S.paint(tex, p["cab"], UPLINK)
    S.overlay(tex, p["cab"], cab_windows, only=SIDES)
    S.paint(tex, p["blue"], USA_BLUE)
    S.paint(tex, p["door"], M.painted(M.GREY_PAINT_RAMP, tone=0.42, wear=0.3))
    S.overlay(tex, p["door"], door_panel, only=("north",))
    S.paint(tex, p["canopy"], UPLINK)
    S.paint(tex, p["roof_glass"], GLASS)
    S.overlay(tex, p["roof_glass"], glazing, only=("up",))
    S.paint(tex, p["roof_cap"], UPLINK)
    S.paint(tex, p["antenna"], GALV)
    S.paint(tex, p["lamp"], M.Material(ramp=W.ramp9(P.INDICATOR_RED), tone=0.7))
    g.paint_all(glow, "lamp", lambda t, r, d: t.rect(r[0], r[1], r[2], r[3], LAMP_GLOW))
    # The fence: galvanised posts and rails, the mesh see-through between its wires.
    S.paint(tex, p["post"], GALV)
    S.paint(tex, p["mesh"], M.galvanized(tone=0.62, rust=0.05))
    S.overlay(tex, p["mesh"], chain_link)
    # The operations building.
    S.paint(tex, p["ops"], OPS_BLUE, only=SIDES)
    S.overlay(tex, p["ops"], ops_walls, only=SIDES)
    S.paint(tex, p["ops"], ROOFING, only=("up",))
    S.paint(tex, p["parapet"], UPLINK)
    S.paint(tex, p["tan_panel"], TAN)
    S.overlay(tex, p["tan_panel"], fan_backing, only=("north",))
    S.paint(tex, p["aircon"], M.galvanized(tone=0.5))
    S.overlay(tex, p["aircon"], lambda f, img: M.grid(f, img, every=1.5, depth=0.4), only=("up",))
    S.overlay(tex, p["aircon"], lambda f, img: M.seams(f, img, every=2, axis="v", light=False), only=SIDES)
    S.paint(tex, p["pedestal"], M.painted(M.GREY_PAINT_RAMP, tone=0.55, wear=0.25))
    S.paint(tex, p["dish"], WHITE)
    S.overlay(tex, p["dish"], dish_face, only=("down",))
    S.paint(tex, p["feed"], DARK)
    S.paint(tex, p["conduit"], GUNMETAL)
    S.overlay(tex, p["conduit"], lambda f, img: M.seams(f, img, every=8, axis="u", light=False))
    return tex, glow


def paint_deck(S, tex: Tex, names: list[str]) -> None:
    """Light concrete in slabs a cell and a half across, a darker curb round the edge; a yellow line round the drum's plate
    and a walkway from the console to it; tyre tracks to the tower's yard."""
    S.paint(tex, names, CONCRETE)
    x0, x1, z0, z1 = PAD

    def floor(f, img):
        cut = lambda v: np.abs(((v - 8) + 12) % 24 - 12) < 0.3
        M.darken(img, cut(f.X) | cut(f.Z), 0.72)
        edge = np.minimum(np.minimum(f.X - x0, x1 - f.X), np.minimum(f.Z - z0, z1 - f.Z))
        M.darken(img, edge < 4, 0.84)
        M.darken(img, (edge >= 4) & (edge < 5), 0.7)
        d = np.hypot(f.X - RX, f.Z - RZ)
        line = (np.abs(d - (PLATE_OUT + 6)) < 0.7) & (((np.degrees(np.arctan2(f.X - RX, f.Z - RZ)) + 360) % 12) < 8)
        M.paste(img, np.broadcast_to(np.array([206, 168, 44, 255], dtype=np.uint8), img.shape).copy(), line & (M.white(f.X, f.Y, f.Z, 5) > 0.15))
        for tx in (86.0, 96.0):
            track = (np.abs(f.X - tx) < 1.5) & (f.Z < YARD[2]) & (M.white(f.X, f.Y, f.Z, 71) > 0.4)
            M.darken(img, track, 0.88)
        stain = M.fbm(f.X, f.Y, f.Z, 5.0, 67, 3)
        M.darken(img, (np.abs(f.X - TX) < 14) & (f.Z > YARD[2]) & (f.Z < TOWER[2]) & (stain > 0.62), 0.8)
    S.overlay(tex, names, floor, only=("up",))


def console(S, g, tex) -> None:
    """The controller's console: blue-grey, a green CRT and two lamps on its front."""
    S.paint(tex, "console", M.painted(DRUM_BLUE_RAMP, tone=0.6, wear=0.25))

    def front(t: Tex, r, d):
        x, y, w, h = r
        if d == "north" and w > 6:
            t.rect(x + 2, y + 1, w - 4, 4, P.BLACK_PAINT.deep)
            for xx in range(x + 2, x + w - 2):
                t.px(xx, y + 3 + (xx % 2), (90, 230, 120, 255))
            t.px(x + 2, y + h - 2, P.INDICATOR_RED[4])
            t.px(x + 4, y + h - 2, P.INDICATOR_GREEN[4])
    g.paint_all(tex, "console", front)


def plate_joints(f, img) -> None:
    """The plate's top: a joint out from each of the drum's corners (a pixel wide), a groove near its edge."""
    d = np.hypot(f.X - RX, f.Z - RZ)
    ang = (np.degrees(np.arctan2(f.X - RX, -(f.Z - RZ))) + 360 + 11.25) % 22.5
    off = np.abs(ang - 11.25) * np.pi / 180 * d                  # pixels from the joint
    M.darken(img, off > 11.25 * np.pi / 180 * d - 0.5, 0.72)
    M.darken(img, np.abs(d - (PLATE_OUT - 2.5)) < 0.5, 0.8)


def wall_outside(f, img) -> None:
    """The drum's outside: a seam round its middle, and on the facets between the emitters a dark louvred vent."""
    M.seams(f, img, every=16, axis="v", light=False)
    ang = (np.degrees(np.arctan2(f.X - RX, -(f.Z - RZ))) + 360) % 360
    facet = np.round(ang / 22.5) % 16
    odd = (facet % 2) == 1
    across = (ang - np.round(ang / 22.5) * 22.5) / 22.5 * 27.8
    vent = odd & (np.abs(across) < 6) & (f.Y > 22) & (f.Y < 36) & (np.round(ang / 22.5) * 22.5 % 360 != FAN_BEARING)
    img[vent] = (34, 37, 42, 255)
    slat = vent & (np.floor(f.Y) % 2 == 0)
    M.lighten(img, slat, 1.5, 10)
    rim = odd & (np.abs(across) >= 6) & (np.abs(across) < 7) & (f.Y > 21) & (f.Y < 37)
    M.darken(img, rim, 0.75)


def fan_backing(f, img) -> None:
    """Behind each fan, inside its housing: dark, a grille's bars across it."""
    a, b = f.plane_uv()
    for _, cx, cy, r in FANS:
        if f.box.name == "fanbox":                              # (the drum's box is turned: its face's own middle row)
            h, w = img.shape[:2]
            yy, xx = np.mgrid[0:h, 0:w]
            for dx in (-6.0, 6.0):
                d = np.hypot(xx + 0.5 - (w / 2 + dx), yy + 0.5 - (h / 2))
                img[d < r - 0.3] = (24, 26, 29, 255)
                M.lighten(img, (d < r - 0.3) & (yy % 2 == 0), 1.35, 6)
            return
        d = np.hypot(a - cx, f.Y - cy)
        img[d < r - 0.3] = (24, 26, 29, 255)
        M.lighten(img, (d < r - 0.3) & (np.floor(f.Y) % 2 == 0), 1.35, 6)


def floor_marks(f, img) -> None:
    """The floor inside the drum: a yellow and black band round the pit's edge."""
    d = np.hypot(f.X - RX, f.Z - RZ)
    ang = np.arctan2(f.X - RX, f.Z - RZ)
    hexr = PIT_A / np.cos(((ang + np.pi / 6) % (np.pi / 3)) - np.pi / 6)      # the pit's flats face 0, 60, 120... degrees
    band = (d > hexr) & (d < hexr + 3)
    stripe = ((np.floor((f.X + f.Z) / 2) % 2) == 0)
    img[band & stripe] = (204, 166, 40, 255)
    img[band & ~stripe] = (30, 30, 32, 255)


def pit_wall(f, img) -> None:
    """The pit's walls: steel, a row of small lamps along them."""
    a, b = f.plane_uv()
    lamp = (np.abs(f.Y - 10) < 0.6) & ((np.floor(a) % 6) == 0)
    img[lamp] = (120, 170, 230, 255)


def ribbed(f, img) -> None:
    """The ribbed band round the top of the tower's shaft: horizontal ribs every other pixel."""
    M.darken(img, (np.floor(f.Y) % 2) == 0, 0.72)


def cab_windows(f, img) -> None:
    """The cab's windows all round: dark glass in grey frames, a sky reflection along their tops."""
    a, b = f.plane_uv()
    win = (f.Y > SHAFT_TOP + 7.5) & (f.Y < CAB_TOP - 2)
    mull = (np.abs(((a - TX if f.d in ("north", "south") else a - TZ) + 17) % 8.5 - 4.25) > 3.6)
    img[win] = (30, 40, 50, 255)
    M.lighten(img, win & (f.Y > CAB_TOP - 5), 1.7, 24)
    img[win & mull] = (150, 154, 160, 255)


def glazing(f, img) -> None:
    """The roof's glass: frames along it every few pixels, a pale sheen."""
    a, b = f.plane_uv()
    M.lighten(img, (np.floor(a) % 6) == 0, 1.5, 20)
    M.lighten(img, M.white(f.X, f.Y, f.Z, 41) > 0.85, 1.2, 10)


def door_panel(f, img) -> None:
    """A door: its frame, a small window high in it, a handle."""
    a, b = f.plane_uv()
    edge = (f.border["left"] < 1) | (f.border["right"] < 1) | (f.border["top"] < 1)
    M.darken(img, edge, 0.6)
    win = (f.Y > 14) & (f.Y < 19) & (f.border["left"] > 3) & (f.border["right"] > 3)
    img[win] = (40, 52, 62, 255)


def chain_link(f, img) -> None:
    """Chain-link: galvanised wire in diamonds (a pixel wide, three across), see-through between."""
    a, b = f.plane_uv()
    u = (a + b) % 4
    v = (a - b) % 4
    wire = (u < 1) | (v < 1)
    img[~wire] = CLEAR


def ops_walls(f, img) -> None:
    """The operations building's walls: seams between their panels, a row of small high windows round the sides."""
    a, b = f.plane_uv()
    M.darken(img, (np.abs((a + 4) % 16 - 8) > 7.6), 0.78)
    win = (f.Y > 30) & (f.Y < 36) & (np.abs((a + 4) % 16 - 8) < 4) & (f.d in ("east", "west", "south"))
    img[win] = (30, 40, 50, 255)
    M.lighten(img, win & (f.Y > 34), 1.6, 18)


def dish_face(f, img) -> None:
    """The dish's face (each plank's face spans its middle): shaded toward its middle (a bowl), a darker ring near its rim."""
    h, w = img.shape[:2]
    yy, xx = np.mgrid[0:h, 0:w]
    d = np.hypot(xx + 0.5 - w / 2, yy + 0.5 - h / 2)
    M.darken(img, d < DISH_R * 0.65, 0.88)
    M.darken(img, d < DISH_R * 0.3, 0.9)
    M.darken(img, np.abs(d - DISH_R + 1.5) < 0.5, 0.8)


# --- animations ----------------------------------------------------------------------------------------------------------------
def smooth(u: float) -> float:
    u = min(1.0, max(0.0, u))
    return u * u * (3 - 2 * u)


def column_keys(T: float, up: bool) -> tuple[dict, dict]:
    """The column telescoping out of the pit (or back into it): its sink and its squeeze, eased."""
    move, size = {}, {}
    for i in range(int(T * 8) + 1):
        t = T * i / (T * 8)
        u = smooth(t / T)
        out = u if up else 1 - u                                # 0 at rest, 1 raised
        move[t] = (0, -SINK * (1 - out), 0)
        size[t] = (1, SQUEEZE + (1 - SQUEEZE) * out, 1)
    return move, size


HIDDEN, SHOWN = (0.01, 0.01, 0.01), (1.0, 1.0, 1.0)


def animations() -> list[Animation]:
    up_move, up_size = column_keys(RAISE_T, True)
    down_move, down_size = column_keys(LOWER_T, False)
    raise_ = Animation("raise", RAISE_T, loop="hold_on_last_frame").move("column", up_move).scale("column", up_size)
    lower = Animation("lower", LOWER_T, loop="hold_on_last_frame").move("column", down_move).scale("column", down_size)
    lowered = Animation("lowered", 0.05, loop="hold_on_last_frame").move("column", {0: (0, -SINK, 0)}).scale("column", {0: (1, SQUEEZE, 1)})
    charged = Animation("charged", CHARGED_T, loop=True)
    charged.scale("core", {0: SHOWN, CHARGED_T / 2: (1.12, 1.12, 1.12), CHARGED_T: SHOWN})
    for k in range(len(EMITTERS)):
        for a in (raise_, lower, lowered):
            a.scale(f"arc{k}", {0: HIDDEN})
        raise_.scale(f"tip{k}", {0: HIDDEN, RAISE_T - 0.3: HIDDEN, RAISE_T: SHOWN})
        lower.scale(f"tip{k}", {0: HIDDEN})
        lowered.scale(f"tip{k}", {0: HIDDEN})
        charged.scale(f"tip{k}", {0: SHOWN})
        # Each arc flashes a few times a cycle, each its own times (seeded): on for a few ticks, off between.
        rng = np.random.default_rng(900 + k)
        keys = {0.0: HIDDEN}
        for _ in range(3):
            t0 = round(float(rng.uniform(0.05, CHARGED_T - 0.4)), 2)
            dur = round(float(rng.uniform(0.1, 0.3)), 2)
            if any(abs(t0 - t) < 0.12 for t in keys):
                continue
            keys[t0 - 0.01] = HIDDEN
            keys[t0] = SHOWN
            keys[t0 + dur] = SHOWN
            keys[t0 + dur + 0.01] = HIDDEN
        keys[CHARGED_T] = HIDDEN
        charged.scale(f"arc{k}", dict(sorted(keys.items())))
    # The main controller: the fans turning (fast while it charges), the dishes swinging a little to and fro, the
    # antenna's lamp blinking (fast while it charges).
    ambient = Animation("ambient", 4.0, loop=True)
    working = Animation("working", 1.0, loop=True)
    for name in ("drumfan0", "drumfan1", "opsfan0", "opsfan1"):
        ambient.rotate(name, {0: (0, 0, 0), 4.0: (0, 360 * 2, 0)})
        working.rotate(name, {0: (0, 0, 0), 1.0: (0, 360 * 2, 0)})
    for i in range(len(DISHES)):
        ambient.rotate(f"dish{i}_yaw", {0: (0, 0, 0), 2.0: (0, 6 * (1 if i else -1), 0), 4.0: (0, 0, 0)})
    blink = {0: SHOWN, 1.4: SHOWN, 1.45: HIDDEN, 2.0: HIDDEN}
    ambient.scale("lamp", {0: SHOWN, 1.9: SHOWN, 2.0: HIDDEN, 2.6: HIDDEN, 2.7: SHOWN, 4.0: SHOWN})
    working.scale("lamp", {t / 2: v for t, v in blink.items()})
    idle = rest_pose([working, ambient], "idle")
    return [raise_, charged, lower, lowered, ambient, working, idle]


# --- blocks, the controller, the spawner ----------------------------------------------------------------------------------------
def uplink_wall_block() -> Tex:
    """Uplink Wall: a light grey concrete panel, a block square, a blue band along its top."""
    img = M.tile(M.Material(ramp=UPLINK_GREY_RAMP, tone=0.64, mottle=0.06, grain=0.05, dither=0.7, scale=0.5, despeckle=False), seed=91)
    col, row = M.grid_xy()
    blue = M.tile(M.painted(USA_BLUE_RAMP, tone=0.58, wear=0.0, dither=0.4), seed=92)
    band = (row >= 2) & (row <= 3)
    img[band] = blue[band]
    seam = (row == 0) | (col == 0)
    M.darken(img, seam, 0.62)
    M.darken(img, (row == 4) & ~seam, 0.86)                    # the band's shadow on the panel under it
    M.lighten(img, (row == 1) & ~seam, 1.08, 4)
    return M.to_tex(img)


def uplink_wall_end() -> Tex:
    """Uplink Wall's top and bottom: the panel's concrete and its joints round the edge, no band (the sides' band, drawn
    on a top, sat along one edge of it: the user's look)."""
    img = M.tile(M.Material(ramp=UPLINK_GREY_RAMP, tone=0.64, mottle=0.06, grain=0.05, dither=0.7, scale=0.5, despeckle=False), seed=93)
    col, row = M.grid_xy()
    seam = (row == 0) | (col == 0)
    M.darken(img, seam, 0.62)
    M.lighten(img, ((row == 1) | (col == 1)) & ~seam, 1.06, 3)
    return M.to_tex(img)


def particle_emitter_block() -> Tex:
    """Particle Emitter: a gunmetal plate, the coil housing's blue cap in its middle round a bright lens."""
    img = PN.face(M.painted(M.ARMOUR_RAMP, tone=0.5, wear=0.2), style="inset", seed=93)
    col, row = M.grid_xy()
    d = np.hypot(col - 7.5, row - 7.5)
    blue = M.tile(M.painted(USA_BLUE_RAMP, tone=0.6, wear=0.0, dither=0.4), seed=94)
    cap = d < 5.2
    img[cap] = blue[cap]
    M.darken(img, (d >= 5.2) & (d < 6.2), 0.45)
    M.bevel(img, cap, 1.25, 0.75, 4)
    lens = d < 2.2
    steel = M.tile(M.steel(tone=0.8, brushed=0.0), seed=95)
    img[lens] = steel[lens]
    M.lighten(img, d < 1.0, 1.3, 30)
    return M.to_tex(img)


def focusing_column_block() -> Tex:
    """Focusing Column: dark steel, a hexagonal frame round a core glowing blue-white."""
    img = PN.face(M.steel(tone=0.32, rust=0.04), style="inset", seed=96)
    col, row = M.grid_xy()
    x, y = col - 7.5, row - 7.5
    hexd = np.maximum(np.abs(x) * math.cos(math.pi / 6) + np.abs(y) * 0.5, np.abs(y))
    frame = (hexd > 4.2) & (hexd < 5.6)
    blue = M.tile(M.painted(USA_BLUE_RAMP, tone=0.55, wear=0.0, dither=0.4), seed=97)
    img[frame] = blue[frame]
    M.bevel(img, frame, 1.2, 0.8, 3)
    core = np.hypot(x, y) < 3.2
    img[core] = (190, 220, 255, 255)
    img[np.hypot(x, y) < 1.8] = (240, 248, 255, 255)
    return M.to_tex(img)


def roundel(t: Tex) -> None:
    """The controller's placard: a white star on a blue disc."""
    t.rect(3, 9, 7, 6, P.WHITE_CERAMIC[5])
    t.disc(6.0, 11.5, 2.6, (40, 70, 160, 255))
    for (x, y) in ((6, 10), (5, 11), (6, 11), (7, 11), (6, 12), (5, 13), (7, 13)):
        t.px(x, y, (240, 242, 246, 255))


def blocks() -> None:
    ST.part_block("uplink_wall", uplink_wall_block())
    uplink_wall_end().save(paths.TEXTURES / "block" / "uplink_wall_end.png")       # its ends plain concrete
    write_json(paths.MODELS / "block" / "uplink_wall.json", {"parent": "minecraft:block/cube_column",
                                                            "textures": {"end": "groundburst:block/uplink_wall_end",
                                                                         "side": "groundburst:block/uplink_wall"}})
    ST.part_block("particle_emitter", particle_emitter_block())
    ST.part_block("focusing_column", focusing_column_block())
    side = M.to_tex(PN.face(M.Material(ramp=UPLINK_GREY_RAMP, tone=0.62, mottle=0.04, grain=0.04, dither=0.6), style="inset", seed=98))
    ST.controller("particle_uplink_cannon", ST.controller_front(roundel), ST.controller_top(), side)


def spawner_plan(t: Tex) -> None:
    """The Particle Uplink Cannon from above: the drum and its pit at the front right, the tower and its yard at the left,
    the operations building behind with its two dishes."""
    cx, cy = 5.5, 6.5
    for a in range(48):
        ang = a / 48 * 2 * math.pi
        t.px(int(round(cx + 3.6 * math.cos(ang))), int(round(cy + 3.6 * math.sin(ang))), MT.BLUEPRINT[6])
    t.px(5, 6, (220, 236, 255, 255))
    t.px(5, 7, (150, 196, 255, 255))
    for (x, y) in ((12, 9), (13, 9), (12, 10), (13, 10)):
        t.px(x, y, MT.BLUEPRINT[5])
    for x in range(8, 15):
        for y in (12, 14):
            t.px(x, y, MT.BLUEPRINT[6])
    for y in range(12, 15):
        t.px(8, y, MT.BLUEPRINT[6])
        t.px(14, y, MT.BLUEPRINT[6])
    t.px(9, 13, (230, 232, 236, 255))
    t.px(12, 13, (230, 232, 236, 255))
    t.px(8, 1, P.HAZARD_YELLOW[5])


def main() -> None:
    g, p = build()
    g.close_floor()            # closed underneath: seen from below it showed through (after the user's look)
    g.layout()
    tex, glow = paint_model(g, p)
    g.save(tex, glow)
    print(save_animations("particle_uplink_cannon", animations()))
    blocks()
    MT.structure_spawner("particle_uplink_cannon_spawner", spawner_plan)
    print(f"particle_uplink_cannon: {len(g.boxes)} boxes, texture {g.tex_w}x{g.tex_h}")


if __name__ == "__main__":
    main()
