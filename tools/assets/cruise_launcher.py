"""The Cruise Missile Launcher (Phase 9 slice 9c, DESIGN.md §17.11): its formed model, the Launch Box block, its
controller and its spawner.

After the Navy's Armored Box Launchers: two armored boxes side by side on a 2 x 6 concrete deck, hinged at its back, a
cruise missile in each. Haze-grey boxes banded with steel straps, a striped collar round each mouth and a stencilled
cover in it, the aft ends louvred for the booster's blast; a warning beacon on their backs. Under them two telescopic
rams fold down into pits in the deck; a low console and the Energy Port's housing at the deck's front edge, a scorched
blast plate behind the hinge. Fired, the rams raise the boxes to 45 degrees; each box's cover bursts off as its missile
lights (it is frangible, as a missile canister's is) and the missile leaves along the box; the beacon turns while it
counts down and fires.

The missiles are not drawn in their boxes (closed, armoured): the game draws each from its own model once it is lit
(MissileRenderer), its wings and scoop stowed until it is clear of its box (the "_folded" variant). The bones tail_a and
tail_b (no cubes) mark where a missile's tail lies in its box, for the preview. CruiseLauncherBlockEntity has the same
figures: the hinge, the boxes' axes, where a missile's tail sits.

Pixels: x right, y up, z back from the middle of the controller's cell (its bottom at y 0); the controller is the front
left cell, the Energy Port the front right.

    tools\\.venv\\Scripts\\python.exe tools\\assets\\cruise_launcher.py
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
import scud_storm as SS  # noqa: E402
import structures as ST  # noqa: E402
import warheads as W  # noqa: E402

SIDES, ALL, NO_DOWN = SS.SIDES, SS.ALL, SS.NO_DOWN
F = "frame"
B = "boxes"
DECK_Y = 14.0

# --- the layout (CruiseLauncherBlockEntity and GZLaunch have the same figures) ----------------------------------------------
#: The deck: the 2 x 6 cells, x -8..24, z -8..88.
DECK = (-8.0, 24.0, -8.0, 88.0)
#: The hinge (up from the deck's foot, back from the controller's cell's middle), each box's axis above the hinge line,
#: where a missile's tail lies (along the box from the hinge), the boxes' length and how far they rise.
HINGE_UP, HINGE_BACK, AXIS_OFF, TAIL_ALONG, BOX_LENGTH, ELEVATION = 20.0, 76.0, 8.0, 3.0, 84.0, 45.0
#: Each box's axis, right of the controller's cell's middle.
BOX_RIGHT = (0.0, 16.0)
#: A box's cross-section about its axis: inside half-width 7 (the heaviest missile's fins reach 7), walls 0.6, the
#: collar round its mouth 0.35 proud. The two boxes stand 0.8 apart.
INNER, OUTER, COLLAR = 7.0, 7.6, 7.95
#: Along a box (from the hinge): its solid core to the recess's floor, the collar, the cover inside it.
CORE_END, COLLAR_FROM = 72.0, 81.0
COVER = (82.0, 83.4)
#: The straps round both boxes, and the rams' lugs (along the box from the hinge, below its floor).
STRAPS = ((14.0, 16.0), (62.0, 64.0))
LUG_ALONG, LUG_BELOW = 28.0, 2.25
#: The rams' feet in their pits (y, z), one under each box's axis; the cylinder, stage and rod (lengths, radii).
RAM_FOOT_Y = 2.0
CYL_LEN, CYL_R = 15.0, 1.6
STAGE_FROM, STAGE_LEN, STAGE_R = 2.0, 13.8, 1.15
ROD_FROM, ROD_R = 3.0, 0.7
#: The rests the lowered boxes lie on (z ranges), the beacon (along the boxes from the hinge, over the gap between them).
RESTS = ((2.0, 6.0), (52.0, 56.0))
BEACON_ALONG = 6.0
#: How long the rams take to raise the boxes (the countdown's first 3 s) and to lower them (LOWERING: 70 ticks).
RAISE_T, LOWER_T = 3.0, 3.5


def box_pt(u: float, v: float) -> tuple[float, float]:
    """A point of the lowered boxes (u along from the hinge, v up from the hinge line) as (y, z)."""
    return HINGE_UP + v, HINGE_BACK - u


def raised_pt(u: float, v: float, alpha: float) -> tuple[float, float]:
    """The same point with the boxes raised {alpha} degrees, as (y, z)."""
    a = math.radians(alpha)
    return HINGE_UP + u * math.sin(a) + v * math.cos(a), HINGE_BACK - u * math.cos(a) + v * math.sin(a)


def bbox(g: GeoModel, name: str, x0: float, x1: float, u0: float, u1: float, v0: float, v1: float, faces=ALL, bone: str = B):
    """A box on the boxes, given along them (u) and up from the hinge line (v), lowered."""
    y0, z1 = box_pt(u0, v0)
    y1, z0 = box_pt(u1, v1)
    return g.box(name, (x0, y0, z0), (x1, y1, z1), faces=faces, bone=bone)


def ram_geometry(xa: float) -> dict:
    """A ram's figures for the box whose axis is at x {xa}: its foot, the lug lowered, and its length lowered."""
    ly, lz = box_pt(LUG_ALONG, -LUG_BELOW)
    hy, hz = raised_pt(LUG_ALONG, -LUG_BELOW, ELEVATION)
    # The foot on the line through the lug's two ends (the ram barely turns as it stretches), down in its pit.
    dy, dz = hy - ly, hz - lz
    t = (ly - RAM_FOOT_Y) / dy
    fz = lz - dz * t
    return {"x": xa, "foot": (RAM_FOOT_Y, fz), "lug": (ly, lz), "closed": math.hypot(ly - RAM_FOOT_Y, lz - fz)}


def ram_pose(alpha: float, foot: tuple[float, float]) -> tuple[float, float]:
    """For the boxes raised {alpha} degrees: the ram's lean from upright toward the back (degrees) and its length."""
    ly, lz = raised_pt(LUG_ALONG, -LUG_BELOW, alpha)
    fy, fz = foot
    return math.degrees(math.atan2(lz - fz, ly - fy)), math.hypot(ly - fy, lz - fz)


# --- materials ----------------------------------------------------------------------------------------------------------------
#: The Navy's haze grey, a little blue.
HAZE_RAMP = M.ramp("#1b2125", "#272e34", "#343c43", "#424b53", "#525c65", "#636e77", "#76828b", "#8b979f", "#a3aeb5")
AMBER_RAMP = M.ramp("#3a1d04", "#5a2d06", "#7e420a", "#a2590f", "#c37217", "#dc8d22", "#eda93a", "#f6c568", "#fbdf9f")
HAZE = M.painted(HAZE_RAMP, tone=0.6, wear=0.22, rust=0.06, streaks=0.07)
HAZE_DARK = M.painted(HAZE_RAMP, tone=0.42, wear=0.15, rust=0.05)
ARMOUR = M.painted(M.ARMOUR_RAMP, tone=0.5, wear=0.3, rust=0.08)
CHROME = M.steel(tone=0.8, brushed=0.12)
INSIDE = M.painted(M.BLACK_RAMP, tone=0.45, wear=0.05)
AMBER = M.Material(ramp=AMBER_RAMP, tone=0.55, mottle=0.03, grain=0.02, dither=0.4, edge_light=0.25, edge_dark=0.1, ao=0.3)
AMBER_LIT = (255, 196, 92, 255)
WHITE = P.WHITE_CERAMIC

ROUND: list[str] = []


class Parts(SS.Parts):
    pass


def ring(g: GeoModel, name: str, r: float, y0: float, y1: float, cx: float, cz: float, bone: str, caps=("up",), stagger=None) -> list[str]:
    names = SS.ring(g, name, r, y0, y1, cx, cz, bone=bone, caps=caps, stagger=stagger)
    ROUND.extend(names)
    return names


# --- the model ----------------------------------------------------------------------------------------------------------------
def pits() -> tuple[float, float, list[tuple[float, float]]]:
    """The rams' pits: from and to (z), and each one's sides (x): round the ram's foot and where it passes the deck's
    top, lowered or raised (it barely turns)."""
    rams = [ram_geometry(xa) for xa in BOX_RIGHT]
    return (min(r["foot"][1] for r in rams) - 3.0, max(r["lug"][1] for r in rams) + 2.0,
            [(r["x"] - 2.6, r["x"] + 2.6) for r in rams])


def build_deck(g: GeoModel, p: Parts) -> list[dict]:
    """The deck, cut by the rams' two pits; the hinge's base, cheeks and pin; the rests; the console, the Energy Port's
    housing and the blast plate."""
    x0, x1, z0, z1 = DECK
    rams = [ram_geometry(xa) for xa in BOX_RIGHT]
    pz0, pz1, pits_x = pits()
    # (Each slab's face toward the pits is their front and back walls; where the strips stand against it, it is buried.)
    g.box("deck_front", (x0, 0, z0), (x1, DECK_Y, pz0), faces=NO_DOWN, bone=F)
    g.box("deck_back", (x0, 0, pz1), (x1, DECK_Y, z1), faces=NO_DOWN, bone=F)
    edges = [x0] + [v for pit in pits_x for v in pit] + [x1]
    strips = []
    for i in range(0, len(edges), 2):
        n = f"deck_mid{i // 2}"
        g.box(n, (edges[i], 0, pz0), (edges[i + 1], DECK_Y, pz1), faces=("east", "west", "up"), bone=F)
        strips.append(n)
    p.add("deck", "deck_front", "deck_back", strips)
    for i, (a, b) in enumerate(pits_x):
        g.box(f"pit_floor{i}", (a, 0, pz0), (b, 1, pz1), faces=("up",), bone=F)
        p.add("pit", f"pit_floor{i}")
    # The hinge: a base beam across the back, three cheeks up beside the boxes' hinge barrels and the pin through them.
    g.box("hinge_base", (x0, DECK_Y, 72.6), (x1, 16.4, 79.4), faces=NO_DOWN, bone=F)
    for i, (a, b) in enumerate(((x0, -5.8), (6.6, 9.4), (21.8, x1))):
        g.box(f"cheek{i}", (a, 16.4, 73.4), (b, 21.6, 78.6), faces=NO_DOWN, bone=F)
        p.add("hinge", f"cheek{i}")
    g.box("pin", (x0 + 0.3, 19.2, 75.2), (x1 - 0.3, 20.8, 76.8), faces=("north", "south", "up", "down"), bone=F)
    p.add("hinge", "hinge_base")
    p.add("pin", "pin")
    # The rests the lowered boxes lie on: a beam across and a rubber pad under each box's sides.
    for i, (a, b) in enumerate(RESTS):
        g.box(f"rest{i}", (x0 + 1, DECK_Y, a), (x1 - 1, 18.6, b), faces=NO_DOWN, bone=F)
        p.add("rest", f"rest{i}")
        for xa in BOX_RIGHT:
            for s in (-1, 1):
                n = f"pad{i}_{int(xa)}_{'lr'[s > 0]}"
                g.box(n, (xa + s * 3.5 - 1.5, 18.6, a + 0.5), (xa + s * 3.5 + 1.5, 20.1, b - 0.5), faces=NO_DOWN, bone=F)
                p.add("pad", n)
    # The console (the controller's) and the Energy Port's housing at the front edge, low enough to go under the boxes.
    g.box("console", (-6, DECK_Y, z0), (4, 19.4, z0 + 4), faces=NO_DOWN, bone=F)
    g.box("housing", (11, DECK_Y, z0), (21, 19.0, z0 + 5), faces=NO_DOWN, bone=F)
    # The blast plate behind the hinge, where the boosters' blast comes out of the boxes' backs.
    g.box("blast_plate", (x0, DECK_Y, 79.4), (x1, 14.4, z1), faces=NO_DOWN, bone=F)
    p.add("blast", "blast_plate")
    return rams


def build_rams(g: GeoModel, p: Parts, rams: list[dict]) -> None:
    """Each ram: a clevis in its pit, then the cylinder, a stage and the rod, telescoping, built upright on the ram's bone
    (turned to lean at the lug); the rod's eye between the lug's two plates under the box."""
    for k, r in enumerate(rams):
        side = "ab"[k]
        xa = r["x"]
        fy, fz = r["foot"]
        lean, closed = ram_pose(0.0, r["foot"])
        ram, stage, rod = f"ram_{side}", f"stage_{side}", f"rod_{side}"
        g.bone(ram, pivot=(xa, fy, fz), parent=F, rotation=(lean, 0, 0))
        g.bone(stage, pivot=(xa, fy, fz), parent=ram)
        g.bone(rod, pivot=(xa, fy, fz), parent=stage)
        g.box(f"clevis_{side}", (xa - 2.2, 1, fz - 1.6), (xa + 2.2, fy + 1.2, fz + 1.6), faces=NO_DOWN, bone=F)
        p.add("clevis", f"clevis_{side}")
        p.add("cylinder", ring(g, f"cyl_{side}", CYL_R, fy - 0.8, fy + CYL_LEN, xa, fz, ram, caps=("up", "down"), stagger="full"))
        p.add("chrome", ring(g, f"stg_{side}", STAGE_R, fy + STAGE_FROM, fy + STAGE_FROM + STAGE_LEN, xa, fz, stage, caps=("up",)))
        top = fy + closed
        p.add("chrome", ring(g, f"rodr_{side}", ROD_R, fy + ROD_FROM, top - 0.6, xa, fz, rod, caps=("up",)))
        g.box(f"eye_{side}", (xa - 0.9, top - 1.0, fz - 1.0), (xa + 0.9, top + 1.0, fz + 1.0), faces=ALL, bone=rod)
        p.add("eye", f"eye_{side}")


def build_boxes(g: GeoModel, p: Parts) -> None:
    """The two boxes on their hinge: each a solid core to the recess's floor, a tube of four walls on to the mouth, the
    collar round the mouth and the cover in it; their hinge barrels, the lugs' plates, the straps round both, the
    beacon on their backs."""
    hy, hz = box_pt(0, 0)
    g.bone(B, pivot=(8.0, hy, hz), parent=F)
    for k, xa in enumerate(BOX_RIGHT):
        side = "ab"[k]
        lo, hi = xa - OUTER, xa + OUTER
        core = f"core_{side}"
        bbox(g, core, lo, hi, 0.0, CORE_END, 0.4, 15.6)
        p.add("box", core)
        walls = [(f"floor_{side}", lo, hi, 0.4, 1.0), (f"roof_{side}", lo, hi, 15.0, 15.6),
                 (f"left_{side}", lo, xa - INNER, 1.0, 15.0), (f"right_{side}", xa + INNER, hi, 1.0, 15.0)]
        for n, a, b, v0, v1 in walls:
            bbox(g, n, a, b, CORE_END, BOX_LENGTH, v0, v1, faces=("north", "east", "west", "up", "down"))
            p.add("box", n)
        collar = [(f"col_top_{side}", xa - COLLAR, xa + COLLAR, 15.6, 15.95), (f"col_bot_{side}", xa - COLLAR, xa + COLLAR, 0.05, 0.4),
                  (f"col_l_{side}", xa - COLLAR, lo, 0.4, 15.6), (f"col_r_{side}", hi, xa + COLLAR, 0.4, 15.6)]
        for n, a, b, v0, v1 in collar:
            bbox(g, n, a, b, COLLAR_FROM, BOX_LENGTH, v0, v1)
            p.add("collar", n)
        # The cover, on its own bone (it bursts off when its missile lights).
        cy, cz = box_pt((COVER[0] + COVER[1]) / 2, 8.0)
        g.bone(f"cover_{side}", pivot=(xa, cy, cz), parent=B)
        bbox(g, f"lid_{side}", xa - INNER + 0.15, xa + INNER - 0.15, COVER[0], COVER[1], 1.15, 14.85, bone=f"cover_{side}")
        p.add(f"lid_{side}", f"lid_{side}")
        # The hinge barrel round the pin, between the cheeks.
        a, b = ((-5.5, 6.3), (9.7, 21.5))[k]
        bbox(g, f"barrel_{side}", a, b, -2.2, 2.2, -2.2, 2.2)
        p.add("barrel", f"barrel_{side}")
        # The lug's two plates under the floor, the rod's eye between them, and its pin.
        for s in (-1, 1):
            n = f"lug_{side}{'lr'[s > 0]}"
            bbox(g, n, xa + s * 1.0 - (1.0 if s < 0 else 0.0), xa + s * 1.0 + (1.0 if s > 0 else 0.0),
                 LUG_ALONG - 2.2, LUG_ALONG + 2.2, -3.4, 0.4, faces=("north", "south", "east", "west", "down"))
            p.add("lug", n)
        bbox(g, f"lugpin_{side}", xa - 2.3, xa + 2.3, LUG_ALONG - 0.6, LUG_ALONG + 0.6, -LUG_BELOW - 0.6, -LUG_BELOW + 0.6,
             faces=("north", "south", "east", "west", "down"))
        p.add("pin", f"lugpin_{side}")
        # Where a missile's tail lies in this box (no cubes: the preview's).
        ty, tz = box_pt(TAIL_ALONG, AXIS_OFF)
        g.bone(f"tail_{side}", pivot=(xa, ty, tz), parent=B)
    # The straps round both boxes.
    for i, (u0, u1) in enumerate(STRAPS):
        for n, a, b, v0, v1 in ((f"strap{i}_top", -COLLAR + 0.05, 16 + COLLAR - 0.05, 15.6, 15.9),
                                (f"strap{i}_bot", -COLLAR + 0.05, 16 + COLLAR - 0.05, 0.1, 0.4),
                                (f"strap{i}_l", -COLLAR + 0.05, -OUTER, 0.4, 15.6), (f"strap{i}_r", 16 + OUTER, 16 + COLLAR - 0.05, 0.4, 15.6)):
            bbox(g, n, a, b, u0, u1, v0, v1)
            p.add("strap", n)
    # The beacon on their backs, over the gap: a base, the lens (unlit) and its cap; the lit lens turns on its own bone.
    bu = BEACON_ALONG
    bbox(g, "beacon_base", 5.0, 11.0, bu - 2.5, bu + 2.5, 15.6, 16.3)
    p.add("box", "beacon_base")
    by, bz = box_pt(bu, 16.3)
    p.add("lens", ring(g, "lens", 1.5, by, by + 2.5, 8.0, bz, B, stagger="full"))
    p.add("beacon_cap", ring(g, "beacon_cap", 0.9, by + 2.5, by + 3.1, 8.0, bz, B, stagger="full"))
    g.bone("beacon", pivot=(8.0, by, bz), parent=B)
    p.add("lit", ring(g, "lit", 1.7, by + 0.2, by + 2.3, 8.0, bz, "beacon", caps=("up", "down"), stagger="full"))


def build() -> tuple[GeoModel, Parts]:
    ROUND.clear()
    g = GeoModel("cruise_launcher", tex_size=(512, 512))
    g.open_ok = {(n, "down") for n in ("deck_front", "deck_back", "deck_mid0", "deck_mid1", "deck_mid2", "pit_floor0", "pit_floor1")}
    g.bone(F)
    p = Parts()
    rams = build_deck(g, p)
    build_boxes(g, p)
    build_rams(g, p, rams)
    return g, p


# --- painting -----------------------------------------------------------------------------------------------------------------
def _kit(g: GeoModel):
    from gz import machine_kit as K  # noqa: PLC0415
    k = K.Kit(g, {}, seed=523, ao_reach=3.0, turned=True)
    W.round_faces(k.S, ROUND, top=True, bottom=True, foot=False)
    return k


def paint_model(g: GeoModel, p: Parts) -> tuple[Tex, Tex]:
    k = _kit(g)
    S = k.S
    tex = Tex(g.tex_w, g.tex_h)
    glow = Tex(g.tex_w, g.tex_h)
    paint_deck(S, tex, p)
    L._console(S, g, tex, "console", lamps=(P.WARNING_RED, P.HAZARD_YELLOW))
    L._power_housing(S, g, tex, "housing", plug="north")
    # The hinge: dark armour steel, its pins bright.
    S.paint(tex, p["hinge"] + p["barrel"] + p["lug"] + p["clevis"], ARMOUR)
    S.paint(tex, p["pin"], M.steel(tone=0.62, brushed=0.08))
    S.paint(tex, p["rest"], M.painted(M.YELLOW_PAINT_RAMP, tone=0.58, wear=0.45, rust=0.1))
    S.paint(tex, p["pad"], M.rubber(tone=0.45))
    # The rams: black cylinders, chrome stages and rods, their eyes armour steel.
    S.paint(tex, p["cylinder"], M.painted(M.BLACK_RAMP, tone=0.5, wear=0.2))
    S.paint(tex, p["chrome"], CHROME)
    S.paint(tex, p["eye"], ARMOUR)
    paint_boxes(g, S, tex, p)
    paint_beacon(g, S, tex, glow, p)
    return tex, glow


def paint_deck(S: M.Surfacer, tex: Tex, p: Parts) -> None:
    """Concrete cut into slabs at the cells' edges, a hazard band round its edge, scorched behind the hinge where the
    boosters' blast comes down, oil round the rams' pits; the pits sooty concrete; the blast plate heat-blued steel."""
    deck = p["deck"]
    S.paint(tex, deck, M.concrete(tone=0.6))
    band = M.stripes(width=3, wear=0.4)
    x0, x1, z0, z1 = DECK
    scorch = L._apron(8, 84, 16, 14, 17, trench=(x0, x1, 79, z1))

    def top(f, img):
        cut = lambda v: np.abs(((v - 8) + 8) % 16 - 8) < 0.25
        M.darken(img, cut(f.X) | cut(f.Z), 0.66)
        edge = np.minimum(np.minimum(f.X - x0, x1 - f.X), np.minimum(f.Z - z0, z1 - f.Z))
        M.paste(img, S.material_on(f, band), edge < 2.5)
        M.darken(img, (edge >= 2.5) & (edge < 3.2), 0.72)
        M.sooted(f, img, scorch(f), 0.6)
        stain = M.fbm(f.X, f.Y, f.Z, 4.0, 529, 3)
        for xa in BOX_RIGHT:
            d = np.hypot(f.X - xa, (f.Z - 45) * 0.6)
            M.darken(img, (d < 8) & (stain > 0.5 + d / 24), 0.6)
    S.overlay(tex, deck, top, only=("up",))
    # The pits' walls (the inner faces of the strips and slabs round them) and floors: dark, sooty.
    pz0, pz1, pits_x = pits()

    def pit_walls(f, img):
        inside = np.zeros(f.shape, dtype=bool)
        for a, b in pits_x:
            inside |= (f.X > a - 0.01) & (f.X < b + 0.01) & (f.Z > pz0 - 0.01) & (f.Z < pz1 + 0.01)
        M.darken(img, inside, 0.45)
    S.overlay(tex, deck, pit_walls, only=SIDES)
    S.paint(tex, p["pit"], M.soot(tone=0.3))
    S.paint(tex, p["blast"], M.steel(tone=0.45, rust=0.1))

    def heat(f, img):
        blue = (M.fbm(f.X, f.Y, f.Z, 6.0, 531, 2) > 0.45) & (f.Z < 86)
        img[blue, :3] = np.clip(img[blue, :3].astype(np.int16) * np.array([0.8, 0.88, 1.18]), 0, 255).astype(np.uint8)
        M.sooted(f, img, np.clip(1 - (f.Z - 79) / 12, 0, 1) * 0.8, 0.55)
        M.darken(img, (np.abs(((f.X - x0) % 8) - 0.25) < 0.3), 0.7)
    S.overlay(tex, p["blast"], heat, only=("up",))


def paint_boxes(g: GeoModel, S: M.Surfacer, tex: Tex, p: Parts) -> None:
    """Haze grey, darker underneath; the recess and the tube's insides black; striped collars; stencils down their sides
    and a red warning band at the aft end; the aft faces louvred; steel straps; the covers stencilled 1 and 2."""
    boxes = p["box"]
    S.paint(tex, boxes, HAZE)
    S.paint(tex, boxes, HAZE_DARK, only=("down",))
    # Inside the mouth: the recess's floor and the walls' inner faces.
    def inside(f, img):
        for xa in BOX_RIGHT:
            m = (np.abs(f.X - xa) < INNER + 0.01) & (f.Y > HINGE_UP + 0.99) & (f.Y < HINGE_UP + 15.01) & (f.Z < HINGE_BACK - CORE_END + 0.01)
            if f.d == "north":
                m &= np.abs(f.Z - (HINGE_BACK - CORE_END)) < 0.02
            M.paste(img, S.material_on(f, INSIDE), m)
            # The floor of the recess: the canister's round end, a dull ring round a darker middle.
            if f.d == "north":
                r = np.hypot(f.X - xa, f.Y - (HINGE_UP + AXIS_OFF))
                M.lighten(img, m & (r > 4.6) & (r < 5.6), 1.6, 10)
    S.overlay(tex, boxes, inside)

    def marks(f, img):
        if f.d not in ("east", "west"):
            return
        along = HINGE_BACK - f.Z                                # u along the box
        v = f.Y - HINGE_UP
        # A red band at the aft end (the booster's blast comes out there), white stencils down the side.
        M.paste(img, S.material_on(f, M.painted(M.RED_PAINT_RAMP, tone=0.55, wear=0.25)), (along > 2) & (along < 5))
        for row, (a0, a1) in enumerate(((10, 30), (10, 22), (40, 56))):
            line = (np.abs(v - (12.0 - row * 2.0 if row < 2 else 5.0)) < 0.5) & (along > a0) & (along < a1)
            dash = (np.floor(along) % 5) != 4
            img[line & dash] = WHITE[5]
    S.overlay(tex, boxes, marks)

    def aft(f, img):
        if f.d != "south":
            return
        for xa in BOX_RIGHT:
            m = (np.abs(f.X - xa) < 6.0) & (f.Y > HINGE_UP + 2.0) & (f.Y < HINGE_UP + 14.0)
            M.darken(img, m, 0.55)
            slat = m & ((np.floor(f.Y) % 2) == 0)
            M.lighten(img, slat, 1.3, 6)
    S.overlay(tex, boxes, aft)
    S.paint(tex, p["collar"], M.stripes(width=2, wear=0.3, under=M.STEEL_RAMP[2:7]))
    S.paint(tex, p["strap"], M.steel(tone=0.4, rust=0.15))
    S.overlay(tex, p["strap"], lambda f, img: M.rivets(f, img, every=4, inset=1), only=("east", "west", "up"))
    for k, side in enumerate("ab"):
        lid = p[f"lid_{side}"]
        S.paint(tex, lid, HAZE)
        S.overlay(tex, lid, numeral(BOX_RIGHT[k], k + 1), only=("north",))


#: The covers' numbers (five texels tall, three wide).
GLYPHS = {1: [".x.", "xx.", ".x.", ".x.", "xxx"], 2: ["xx.", "..x", ".x.", "x..", "xxx"]}


def numeral(xa: float, n: int):
    def fn(f, img):
        col = np.floor((xa + 1.5) - f.X).astype(int)          # (seen from in front, +x is on the left)
        row = np.floor((HINGE_UP + 10.5) - f.Y).astype(int)
        for j, line in enumerate(GLYPHS[n]):
            for i, ch in enumerate(line):
                if ch == "x":
                    img[(col == i) & (row == j)] = WHITE[6]
        # A thin white ring round the cover's edge.
        edge = (np.abs(f.X - xa) > INNER - 1.2) | (np.abs(f.Y - (HINGE_UP + AXIS_OFF)) > INNER - 1.2)
        M.darken(img, edge, 0.78)
    return fn


def paint_beacon(g: GeoModel, S: M.Surfacer, tex: Tex, glow: Tex, p: Parts) -> None:
    """The lens amber, unlit; the lit lens bright on two opposite facets (glowing) and glowing dimmer round the rest."""
    S.paint(tex, p["lens"], AMBER)
    S.paint(tex, p["beacon_cap"], M.painted(M.BLACK_RAMP, tone=0.5, wear=0.2))
    S.paint(tex, p["lit"], M.Material(ramp=AMBER_RAMP, tone=0.8, mottle=0.02, grain=0.01, dither=0.3, edge_light=0.1, ao=0.0))
    bz = box_pt(BEACON_ALONG, 0)[1]

    def bright(f, img):
        if f.d not in SIDES:
            return
        ang = np.degrees(np.arctan2(f.X - 8.0, f.Z - bz)) % 180
        hot = (np.abs(ang - 90) < 25)
        img[hot] = AMBER_LIT
    S.overlay(tex, p["lit"], bright)
    for n in p["lit"]:
        g.paint_all(glow, n, lambda t, r, d: t.rect(*r, (255, 186, 80, 255)) if d in SIDES else None)


# --- animations ----------------------------------------------------------------------------------------------------------------
def boxes_animation(name: str, raising: bool, length: float) -> Animation:
    """The rams raising (or lowering) the boxes: the boxes turned up about the hinge, eased; each ram leaning to follow
    its lug and stretching, its stage and rod each taking half the stretch (sampled every quarter second)."""
    a = Animation(name, length, loop="hold_on_last_frame")
    steps = int(length * 4)
    boxes: dict = {}
    per: dict = {side: ({}, {}, {}) for side in "ab"}
    rams = [ram_geometry(xa) for xa in BOX_RIGHT]
    for i in range(steps + 1):
        t = length * i / steps
        s = i / steps
        s = s if raising else 1 - s
        alpha = ELEVATION * (s * s * (3 - 2 * s))
        boxes[t] = (alpha, 0, 0)
        for k, r in enumerate(rams):
            lean0, closed = ram_pose(0.0, r["foot"])
            lean, length_now = ram_pose(alpha, r["foot"])
            stretch = length_now - closed
            ram, stage, rod = per["ab"[k]]
            ram[t] = (lean - lean0, 0, 0)
            stage[t] = (0, stretch / 2, 0)
            rod[t] = (0, stretch / 2, 0)
    a.rotate(B, boxes)
    for side, (ram, stage, rod) in per.items():
        a.rotate(f"ram_{side}", ram)
        a.move(f"stage_{side}", stage)
        a.move(f"rod_{side}", rod)
    return a


def cover_animations(side: str) -> list[Animation]:
    """Its cover bursting off as its missile lights: out of the mouth tumbling, then gone (shrunk away inside the tube);
    back in place (a new cover for the next missile) once the boxes are down."""
    bone = f"cover_{side}"
    spin = 1 if side == "a" else -1
    burst = Animation(f"open_{side}", 0.5, loop="hold_on_last_frame")
    burst.move(bone, {0: (0, 0, 0), 0.08: (0, 2.5, -9), 0.3: (spin * 4, 9, -34), 0.32: (0, 0, 6), 0.5: (0, 0, 6)})
    burst.rotate(bone, {0: (0, 0, 0), 0.08: (55, 0, spin * 12), 0.3: (210, spin * 25, spin * 40), 0.32: (0, 0, 0), 0.5: (0, 0, 0)})
    burst.scale(bone, {0: (1, 1, 1), 0.08: (1, 1, 1), 0.3: (0.6, 0.6, 0.6), 0.32: (0.02, 0.02, 0.02), 0.5: (0.02, 0.02, 0.02)})
    shut = Animation(f"shut_{side}", 0.05, loop="hold_on_last_frame")
    shut.move(bone, {0: (0, 0, 0)})
    shut.rotate(bone, {0: (0, 0, 0)})
    shut.scale(bone, {0: (1, 1, 1)})
    return [burst, shut]


def animations() -> list[Animation]:
    raise_ = boxes_animation("raise", True, RAISE_T)
    lower = boxes_animation("lower", False, LOWER_T)
    lowered = rest_pose([raise_], "lowered")
    working = Animation("working", 1.0, loop=True)
    working.rotate("beacon", {0: (0, 0, 0), 1.0: (0, 360, 0)})
    idle = rest_pose([working], "idle")
    return [raise_, lower, lowered, *cover_animations("a"), *cover_animations("b"), working, idle]


# --- blocks, the controller, the spawner ---------------------------------------------------------------------------------------
def launch_box_block() -> Tex:
    """Launch Box: a haze-grey armoured panel, a striped band across its top and a stencilled cruise missile on it."""
    img = PN.face(HAZE, style="raised", seed=533)
    PN.stripes(img, PN.area(2, 2, 12, 2), seed=533)
    t = M.to_tex(img)
    for x in range(4, 12):
        t.px(x, 9, WHITE[5])                                  # the missile
    t.px(12, 9, WHITE[4])
    for (x, y) in ((7, 8), (6, 7), (7, 10), (6, 11), (4, 8), (4, 10)):
        t.px(x, y, WHITE[4])                                  # its wings and fins
    for x in range(4, 12, 2):
        t.px(x, 13, WHITE[3])                                 # a stencil line
    return t


def cruise_placard(t: Tex) -> None:
    """The controller's placard: a cruise missile, wings out."""
    t.rect(3, 9, 8, 5, P.BLACK_PAINT[1])
    for x in range(4, 10):
        t.px(x, 11, WHITE[5])
    t.px(10, 11, P.WARNING_RED[4])
    for (x, y) in ((6, 10), (6, 12), (5, 9), (5, 13), (4, 10), (4, 12)):
        t.px(x, y, WHITE[4])


def blocks() -> None:
    ST.part_block("launch_box", launch_box_block())
    ST.controller("cruise_launcher", ST.controller_front(cruise_placard), ST.controller_top(), ST.casing())


def spawner_plan(t: Tex) -> None:
    """The launcher from above: the long deck, the two boxes along it, the hinge across its back."""
    for y in range(2, 14):
        for x in (5, 10):
            t.px(x, y, MT.BLUEPRINT[6])
    for x in range(5, 11):
        t.px(x, 2, MT.BLUEPRINT[6])
        t.px(x, 13, MT.BLUEPRINT[6])
    for y in range(3, 12):
        t.px(7, y, (190, 200, 206, 255))
        t.px(8, y, (190, 200, 206, 255))
    for x in range(6, 10):
        t.px(x, 12, P.HAZARD_YELLOW[4])


def main() -> None:
    g, p = build()
    g.layout()
    tex, glow = paint_model(g, p)
    g.save(tex, glow)
    print(save_animations("cruise_launcher", animations()))
    blocks()
    MT.structure_spawner("cruise_launcher_spawner", spawner_plan)
    r = ram_geometry(0.0)
    print(f"cruise_launcher: {len(g.boxes)} boxes, texture {g.tex_w}x{g.tex_h}; ram foot {r['foot']}, closed {r['closed']:.2f}, "
          f"stretch {ram_pose(ELEVATION, r['foot'])[1] - r['closed']:.2f}")


if __name__ == "__main__":
    main()
