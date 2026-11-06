"""The Depth Charge Rack (DESIGN.md §14.6): its formed model (GeckoLib), the Depth Charge Rail block, its controller (the
Rack Release) and its spawner.

After the stern racks of the Second World War's destroyers and escorts: two steel rails sloping down to the stern on
short legs, the charges lying across them on their rolling hoops, held at the low end by a hinged trap; a hand release
lever on its left; guard rails along both sides over the charges' ends; the loading end at the top over the Item Port's
housing, where pipes and hoppers feed it. Six charges, one block wide and four long. Navy haze grey, the rails' running
tops worn bright, the trap and lever red.

Bones (DepthChargeRackBlockEntity moves them; it has the same figures):
- "incline": the rails and everything on them, tilted SLOPE degrees down to the front about the front charge's foot.
- "slot0".."slot5" (on the incline): where each charge lies, slot0 against the trap. The block entity hides an empty
  slot's charge and rolls a charge down from one slot to the next (moved along z, turned about x).
- "drum0".."drum5": the charges, each the depth charge's own drum built standing and laid on its side by its bone.
- "trap": the hinged pawl at the front, turned down about its hinge to let the front charge go.
- "lever": the hand release lever, thrown forward with it.

Pixels: x right, y up, z back from the middle of the controller's cell (its bottom at y 0). The rack runs back from the
controller (the release end, z -8) over two Depth Charge Rails to the Item Port (the loading end, z 56). Front = north.

    tools\\.venv\\Scripts\\python.exe tools\\assets\\depth_charge_rack.py
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))

from gb import machine_kit as K  # noqa: E402
from gb import materials as M  # noqa: E402
from gb import palette as P  # noqa: E402
from gb import panels as PN  # noqa: E402
from gb import paths  # noqa: E402
from gb.geo import Animation, GeoModel, save_animations  # noqa: E402
from gb.model import write_json  # noqa: E402
from gb.tex import Tex  # noqa: E402
import launch as L  # noqa: E402
import naval as N  # noqa: E402
import scud_storm as SS  # noqa: E402
import warheads as W  # noqa: E402

NAME = "depth_charge_rack"
SIDES, ALL = SS.SIDES, SS.ALL
F, I = "frame", "incline"

# --- the figures DepthChargeRackBlockEntity shares --------------------------------------------------------------------------
SLOTS = 6
#: Between the charges' middles along the rails (their hoops touching), the rails' slope, the front charge's middle.
SPACING, SLOPE = 10.0, 3.0
FRONT_Z, DRUM_Y = -2.6, 8.4
#: The charge's rolling hoops (they ride on the rails): their radius, and their middles out from the drum's middle.
HOOP_R, HOOP_AT = 4.95, 3.0
RAIL_TOP = DRUM_Y - HOOP_R - 0.05
#: The incline's pivot: the front charge's foot on the rails.
PIVOT = (0.0, RAIL_TOP, FRONT_Z)
#: The trap's hinge, and the lever's.
TRAP_HINGE = (0.0, RAIL_TOP - 0.9, -7.5)
LEVER_PIVOT = (-7.7, 2.6, -3.9)
#: How far back the rails run (z), and the rack's length in cells.
RAIL_BACK = 55.2
CELLS = 4


def tilt_y(z: float, y: float) -> float:
    """Where a point (z, y) of the incline is in the frame's height (the incline turned SLOPE degrees about PIVOT)."""
    a = math.radians(SLOPE)
    dz, dy = z - PIVOT[2], y - PIVOT[1]
    return PIVOT[1] + dy * math.cos(a) + dz * math.sin(a)


# --- materials --------------------------------------------------------------------------------------------------------------
RACK_GREY = M.painted(N.NAVY, tone=0.6, wear=0.22, rust=0.16, under=M.STEEL_RAMP[2:8], grime=0.12, streaks=0.1, dust=0.02)
FRAME_GREY = M.painted(N.NAVY, tone=0.5, wear=0.25, rust=0.22, under=M.STEEL_RAMP[2:8], grime=0.16, streaks=0.12, dust=0.03)
RUNNING = M.steel(tone=0.66, brushed=0.12, rust=0.04)
TRAP_RED = M.painted(M.RED_PAINT_RAMP, tone=0.5, wear=0.28, rust=0.1, under=M.STEEL_RAMP[2:8], grime=0.06)
KNOB = M.painted(M.BLACK_RAMP, tone=0.45, wear=0.15)


def build() -> tuple[GeoModel, SS.Parts]:
    g = GeoModel(NAME, tex_size=(256, 256))
    g.bone(F)
    g.bone(I, pivot=PIVOT, parent=F, rotation=(-SLOPE, 0, 0))
    g.bone("trap", pivot=TRAP_HINGE, parent=I)
    g.bone("lever", pivot=LEVER_PIVOT, parent=F)
    for k in range(SLOTS):
        z = FRONT_Z + SPACING * k
        g.bone(f"slot{k}", pivot=(0.0, DRUM_Y, z), parent=I)
        g.bone(f"drum{k}", pivot=(0.0, DRUM_Y, z), parent=f"slot{k}", rotation=(0, 0, 90))
    p = SS.Parts()
    SS.ROUND.clear()

    # --- on the incline: the rails, the side members, the guard rails, the loading end -----------------------------------
    for side, s in (("w", -1), ("e", 1)):
        x0, x1 = sorted((s * (HOOP_AT - 0.7), s * (HOOP_AT + 0.7)))
        # The rail: an angle, its running flange on top and its web under it.
        g.box(f"rail_top_{side}", (x0, RAIL_TOP - 0.6, -7.6), (x1, RAIL_TOP, RAIL_BACK), faces=ALL, bone=I)
        w0, w1 = sorted((s * (HOOP_AT - 0.25), s * (HOOP_AT + 0.25)))
        g.box(f"rail_web_{side}", (w0, RAIL_TOP - 2.0, -7.2), (w1, RAIL_TOP - 0.6, RAIL_BACK - 0.4), faces=SIDES + ("down",), bone=I)
        p.add("rail", f"rail_top_{side}")
        p.add("web", f"rail_web_{side}")
        # The side member: a channel under the charges' ends.
        m0, m1 = sorted((s * 5.6, s * 7.0))
        g.box(f"member_{side}", (m0, RAIL_TOP - 2.0, -7.8), (m1, RAIL_TOP - 0.7, RAIL_BACK + 0.4), faces=ALL, bone=I)
        p.add("member", f"member_{side}")
        # The guard rail over the charges' ends, on its stanchions.
        r0, r1 = sorted((s * 7.1, s * 7.65))
        g.box(f"guard_{side}", (r0, DRUM_Y + 2.2, -6.4), (r1, DRUM_Y + 2.9, RAIL_BACK - 0.2), faces=ALL, bone=I)
        p.add("guard", f"guard_{side}")
        for j, z in enumerate((-6.4, 9.0, 24.4, 39.8, RAIL_BACK - 0.8)):
            g.box(f"stanchion_{side}{j}", (r0, RAIL_TOP - 2.0, z), (r1, DRUM_Y + 2.2, z + 0.6), faces=SIDES, bone=I)
            p.add("stanchion", f"stanchion_{side}{j}")
    # Cross members under the rails, between the side members (one in each cell but the loading end's).
    for j, z in enumerate((-7.6, 6.0, 22.0, 38.0)):
        g.box(f"cross{j}", (-5.6, RAIL_TOP - 1.9, z), (5.6, RAIL_TOP - 1.0, z + 1.0), faces=("north", "south", "up", "down"), bone=I)
        p.add("cross", f"cross{j}")
    # The loading end: the chute's floor the charges come up onto, its back plate and its cheeks.
    g.box("chute_floor", (-5.5, RAIL_TOP - 0.9, RAIL_BACK - 2.6), (5.5, RAIL_TOP - 0.3, RAIL_BACK + 0.4), faces=ALL, bone=I)
    g.box("chute_back", (-7.0, RAIL_TOP - 2.0, RAIL_BACK + 0.4), (7.0, DRUM_Y + 3.4, RAIL_BACK + 1.0), faces=ALL, bone=I)
    p.add("chute", "chute_floor", "chute_back")

    # --- the trap: two hinged arms in front of the front charge, on their shaft --------------------------------------------
    hx, hy, hz = TRAP_HINGE
    g.box("trap_shaft", (-4.8, hy - 0.4, hz - 0.4), (4.8, hy + 0.4, hz + 0.4), faces=ALL, bone="trap")
    p.add("trap_shaft", "trap_shaft")
    for side, s in (("w", -1), ("e", 1)):
        a0, a1 = sorted((s * 3.85, s * 4.45))
        g.box(f"trap_arm_{side}", (a0, hy + 0.4, -7.95), (a1, DRUM_Y + 1.6, -7.35), faces=ALL, bone="trap")
        p.add("trap", f"trap_arm_{side}")
    g.box("trap_bar", (-4.3, DRUM_Y + 0.95, -7.85), (4.3, DRUM_Y + 1.35, -7.45), faces=("north", "south", "up", "down"), bone="trap")
    p.add("trap", "trap_bar")

    # --- the frame: legs and their feet, the lever's quadrant, the Item Port's housing ----------------------------------------
    for side, s in (("w", -1), ("e", 1)):
        l0, l1 = sorted((s * 5.8, s * 6.8))
        for j, z in enumerate((-0.4, 15.6, 31.6)):
            top = tilt_y(z + 0.4, RAIL_TOP - 2.0) + 0.3
            g.box(f"leg_{side}{j}", (l0, 0.4, z), (l1, top, z + 0.8), faces=SIDES, bone=F)
            f0, f1 = sorted((s * 5.4, s * 7.2))
            g.box(f"foot_{side}{j}", (f0, 0.0, z - 0.6), (f1, 0.4, z + 1.4), faces=SIDES + ("up",), bone=F)
            p.add("leg", f"leg_{side}{j}")
            p.add("foot", f"foot_{side}{j}")
    lx, ly, lz = LEVER_PIVOT
    g.box("quadrant", (-7.35, 1.2, lz - 1.6), (-7.05, 3.6, lz + 1.6), faces=("west", "north", "south", "up"), bone=F)
    p.add("quadrant", "quadrant")
    g.box("lever_arm", (-7.95, ly, lz - 0.3), (-7.55, ly + 5.4, lz + 0.3), faces=ALL, bone="lever")
    g.box("lever_knob", (-7.97, ly + 5.4, lz - 0.55), (-7.52, ly + 6.4, lz + 0.55), faces=ALL, bone="lever")
    p.add("lever", "lever_arm")
    p.add("knob", "lever_knob")
    # The Item Port's housing under the loading end (pipes and hoppers meet its back and sides).
    housing_top = tilt_y(42.4, RAIL_TOP - 2.0) - 0.2
    g.box("housing", (-7.6, 0.0, 42.4), (7.6, housing_top, 55.6), faces=ALL, bone=F)
    p.add("housing", "housing")
    # Its saddle under the rails' webs (the rails bolted down onto it).
    g.box("saddle", (-4.0, housing_top, 44.0), (4.0, tilt_y(44.0, RAIL_TOP - 2.0) + 0.3, 45.0), faces=("north", "south", "east", "west"),
          bone=F)
    p.add("housing", "saddle")

    # --- the charges: each the depth charge's own drum, built standing at its slot and laid on its side by its bone ----------
    for k in range(SLOTS):
        drum(g, p, k)

    while True:
        try:
            g.layout()
            break
        except ValueError:
            g.tex_w *= 2
    return g, p


def drum(g: GeoModel, p: SS.Parts, k: int) -> None:
    """Charge {k}: as naval.depth_charge, standing with its middle at its slot (DRUM_Y, the slot's z), on bone drum{k}."""
    b, z, cy = f"drum{k}", FRONT_Z + SPACING * k, DRUM_Y
    p.add(f"body{k}", SS.ring(g, f"c{k}_drum", 4.5, cy - 5.4, cy + 5.4, 0.0, z, b, caps=("up", "down"), stagger="alternate"))
    for j, (y0, y1) in enumerate(((cy - 5.8, cy - 5.4), (cy + 5.4, cy + 5.8))):
        p.add(f"rim{k}", SS.ring(g, f"c{k}_chime{j}", 4.75, y0, y1, 0.0, z, b, caps=("up", "down"), stagger="full"))
    for j, y0 in enumerate((cy - HOOP_AT - 0.4, cy + HOOP_AT - 0.4)):
        p.add(f"rim{k}", SS.ring(g, f"c{k}_hoop{j}", HOOP_R, y0, y0 + 0.8, 0.0, z, b, caps=("up", "down"), stagger="full"))
    p.add(f"pistol{k}", SS.ring(g, f"c{k}_pistol", 1.8, cy + 5.8, cy + 6.4, 0.0, z, b, caps=("up",)))
    p.add(f"knob{k}", SS.ring(g, f"c{k}_knob", 0.6, cy + 6.4, cy + 6.8, 0.0, z, b, caps=("up",)))
    p.add(f"primer{k}", SS.ring(g, f"c{k}_primer", 1.2, cy - 6.4, cy - 5.8, 0.0, z, b, caps=("down",)))


def paint(g: GeoModel, p: SS.Parts) -> Tex:
    """Haze grey steel, the rails' running tops worn bright where the hoops roll, rust at the joints; the trap and the lever
    red, a hazard band on the trap's bar; the Item Port's housing as every launcher's; the charges as the depth charge."""
    k = K.Kit(g, {}, seed=1411, ao_reach=2.2)
    W.round_faces(k.S, SS.ROUND, top=True, bottom=True, foot=False)
    S = k.S
    tex = Tex(g.tex_w, g.tex_h)
    S.paint(tex, p["rail"], RACK_GREY)
    S.paint(tex, p["rail"], RUNNING, only=("up",))
    S.paint(tex, p["web"] + p["cross"], FRAME_GREY)
    S.paint(tex, p["member"], FRAME_GREY)
    S.overlay(tex, p["member"], lambda f, img: M.rivets(f, img, every=4, inset=0.5), only=("east", "west"))
    S.paint(tex, p["guard"] + p["stanchion"], RACK_GREY)
    S.paint(tex, p["chute"], FRAME_GREY)
    S.overlay(tex, p["chute"], lambda f, img: M.paste(img, S.material_on(f, M.stripes(width=2, wear=0.3)), f.border["top"] < 2),
              only=("north", "south"))
    S.paint(tex, p["trap_shaft"], M.steel(tone=0.45, brushed=0.06, rust=0.12))
    S.paint(tex, p["trap"], TRAP_RED)
    S.overlay(tex, ["trap_bar"], lambda f, img: M.paste(img, S.material_on(f, M.stripes(width=1, wear=0.25)), np.ones(img.shape[:2], bool)),
              only=("north",))
    S.paint(tex, p["leg"] + p["foot"], FRAME_GREY)
    S.paint(tex, p["quadrant"], FRAME_GREY)
    S.paint(tex, p["lever"], TRAP_RED)
    S.paint(tex, p["knob"], KNOB)
    L._item_housing(S, g, tex, "housing", mouth="south")
    S.paint(tex, ["saddle"], FRAME_GREY)
    for kk in range(SLOTS):
        paint_drum(k, tex, p, kk)
    return tex


def paint_drum(k: K.Kit, tex: Tex, p: SS.Parts, kk: int) -> None:
    """Charge {kk} as naval.paint_depth_charge: navy grey, a yellow band and white stencils, the hoops and chimes darker, the
    pistol's brass dial on its end with its marks, the primer's cap on the other."""
    S, cy, cz = k.S, DRUM_Y, FRONT_Z + SPACING * kk
    S.paint(tex, p[f"body{kk}"], N.CHARGE_GREY)
    S.overlay(tex, p[f"body{kk}"], lambda f, img: W.band(k, f, img, cy, cy + 1, N.BAND_YELLOW))
    S.overlay(tex, p[f"body{kk}"], lambda f, img: W.stencil(f, img, [cy - 1.6], N.STENCIL, width=5, pattern="xx.x."))
    S.paint(tex, p[f"rim{kk}"], N.HOOP_GREY)
    S.paint(tex, p[f"pistol{kk}"], N.PISTOL)

    def dial(f, img):
        if f.d != "up":
            return
        a = (np.degrees(np.arctan2(f.Z - cz, f.X)) + 360) % 360
        rr = np.hypot(f.X, f.Z - cz)
        M.darken(img, (rr > 1.0) & (np.floor(a / 30) % 2 == 0), 0.55)
    S.overlay(tex, p[f"pistol{kk}"], dial)
    S.paint(tex, p[f"knob{kk}"] + p[f"primer{kk}"], N.PRIMER)


def animations() -> list[Animation]:
    """Idle: nothing (the block entity moves the charges, the trap and the lever)."""
    idle = Animation("idle", 0.05, loop="hold_on_last_frame")
    idle.move(F, {0: (0, 0, 0)})
    return [idle]


# --- the blocks ---------------------------------------------------------------------------------------------------------------

def rail_top() -> Tex:
    """The Depth Charge Rail's top: two rails running along it over a haze grey frame, guard rails at its sides."""
    grey = M.painted(N.NAVY, tone=0.55, wear=0.22, rust=0.12)
    img = PN.face(grey, style="inset", seed=1413)
    col, row = PN.grid()
    rails = ((col >= 4) & (col <= 5)) | ((col >= 10) & (col <= 11))
    M.darken(img, ~rails & (col > 1) & (col < 14), 0.6)                                        # the gap between, dark
    M.lighten(img, rails, 1.35, 12)                                                             # the running tops, worn bright
    M.darken(img, (col == 1) | (col == 14), 0.8)                                                # the guard rails' shadow
    M.darken(img, rails & (row % 5 == 0), 0.85)                                                 # the rails' joints
    return M.to_tex(img)


def rail_side() -> Tex:
    """Its sides: the side member's channel with its rivets, the guard rail along the top on its stanchions."""
    grey = M.painted(N.NAVY, tone=0.5, wear=0.24, rust=0.16)
    img = PN.face(grey, style="inset", seed=1417)
    col, row = PN.grid()
    M.darken(img, PN.area(1, 3, 14, 1), 0.6)                                                    # under the guard rail
    M.lighten(img, PN.area(1, 2, 14, 1), 1.2, 8)                                                # the guard rail
    M.darken(img, PN.area(2, 4, 1, 7) | PN.area(13, 4, 1, 7), 0.75)                             # the stanchions
    M.darken(img, PN.area(1, 11, 14, 1), 0.7)                                                   # the channel's lip
    M.lighten(img, (row == 12) & (col % 4 == 2), 1.5, 20)                                       # its rivets
    return M.to_tex(img)


def release_placard(t: Tex) -> None:
    """The Rack Release's front: a charge rolling off over the trap."""
    t.rect(4, 6, 4, 4, P.GUNMETAL[3])
    t.rect(5, 7, 2, 2, P.GUNMETAL[5])
    t.vline(8, 7, 4, P.WARNING_RED.base)
    t.hline(4, 11, 6, P.HAZARD_YELLOW[4])


def blocks() -> None:
    import structures as ST  # noqa: PLC0415
    top, side = rail_top(), rail_side()
    top.save(paths.TEXTURES / "block" / "depth_charge_rail_top.png")
    side.save(paths.TEXTURES / "block" / "depth_charge_rail_side.png")
    write_json(paths.MODELS / "block" / "depth_charge_rail.json", {"parent": "minecraft:block/cube_column",
                                                                   "textures": {"end": "groundburst:block/depth_charge_rail_top",
                                                                                "side": "groundburst:block/depth_charge_rail_side"}})
    write_json(paths.MODELS / "block" / "depth_charge_rail_formed.json", ST.particle_model("groundburst:block/depth_charge_rail_side"))
    write_json(paths.ASSETS / "blockstates" / "depth_charge_rail.json", {"variants": {
        "formed=false": {"model": "groundburst:block/depth_charge_rail"},
        "formed=true": {"model": "groundburst:block/depth_charge_rail_formed"},
    }})
    write_json(paths.ASSETS / "items" / "depth_charge_rail.json", {"model": {"type": "minecraft:model", "model": "groundburst:block/depth_charge_rail"}})
    ST.controller(NAME, ST.controller_front(release_placard), ST.controller_top(), ST.casing())


def spawner_plan(t: Tex) -> None:
    """The blueprint: the rack, four long, six charges on it, the trap at the front (bottom)."""
    import multiblock_tools as MT  # noqa: PLC0415
    line = MT.BLUEPRINT[6]
    t.outline(6, 1, 4, 14, line)
    for j in range(6):
        t.px(7, 3 + 2 * j, MT.BLUEPRINT[5])
        t.px(8, 3 + 2 * j, MT.BLUEPRINT[5])
    t.hline(6, 14, 4, P.WARNING_RED.base)                                                       # the trap, front (bottom)


if __name__ == "__main__":
    import multiblock_tools as MT  # noqa: E402
    model, parts = build()
    texture = paint(model, parts)
    print("z-fights:", model.zfight()[:12])
    for path in model.save(texture):
        print(path)
    print(save_animations(NAME, animations()))
    blocks()
    MT.structure_spawner("depth_charge_rack_spawner", spawner_plan)
