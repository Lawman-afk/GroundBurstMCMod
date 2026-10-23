"""The Missile Silo (DESIGN.md §17.2, §17.10; Phase 9 slice 9b): its building blocks, the Silo Hatch, the controller's
cabinet, the formed structure (GeckoLib) and its Structure Spawner.

- Reinforced Concrete: grey board-formed concrete showing its formwork's seams and tie holes. It lines the shaft and
  stays as it is when the silo forms, so the shaft's walls are the blocks you built.
- Hazard Concrete: the same, striped yellow and black: the silo's top ring (hidden while formed).
- Silo Hatch: one item that places a 3x3 lid, drawn by its middle block: two heavy leaves meeting in a hazard-striped
  seam inside a steel frame.
- Missile Silo, formed: a concrete collar flush with the ground round the shaft, a striped steel coaming round the
  opening, two heavy door leaves on rails that run out to either side (they slide apart for a launch, moved by the
  block entity: MissileSiloBlockEntity#boneOffsets), an exhaust vent grate at each corner, a squat armoured console at
  the front with a green CRT and two red beacons (they flash while it counts down: the "working" animation), the
  Energy Port's flush plate at the back; down the shaft, conduits in its corners and the missile standing on its
  launch ring over the flame duct at the bottom (no lamps: the user asked for them to go after 9b).

Coordinates are model pixels with the controller's cell centred on x = z = 0 and y = 0 the bottom of the top layer:
the opening is x -24..24, z 8..56; the ground (the collar's top) is y 16; the shaft's floor is y -192.
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))

import numpy as np  # noqa: E402

from gz import materials as M  # noqa: E402
from gz import palette as P  # noqa: E402
from gz import paths  # noqa: E402
from gz.geo import Animation, GeoModel, save_animations  # noqa: E402
from gz.model import BlockModel, write_json  # noqa: E402
from gz.tex import Tex  # noqa: E402
import launch as L  # noqa: E402
import multiblock_tools as MT  # noqa: E402
import structures as ST  # noqa: E402

OLIVE = P.OLIVE
YELLOW, BLACK, RED, GREEN = P.HAZARD_YELLOW, P.HAZARD_BLACK, P.WARNING_RED, P.INDICATOR_GREEN
WHITE = P.WHITE_CERAMIC
CLEAR = (0, 0, 0, 0)

TOP = 16                      # the ground: the collar's top face
FLOOR = -192                  # the shaft's floor (the top of the bottom layer)
TAIL = FLOOR + 3              # the missile's tail, on its launch ring
CZ = 32                       # the shaft's middle (z); its middle in x is 0
HALF = 24                     # half the shaft's width
LEAF_Y = (17, 24)             # the door leaves, resting on their rails (MissileSiloBlockEntity: the hatch is 24 px tall)
SLIDE = 24                    # how far each leaf slides out to open


# --- the building blocks ---------------------------------------------------------------------------------------------------
def reinforced_concrete() -> Tex:
    """Cast concrete: even, a little grit; the seams where two formwork boards met across the middle and at the bottom
    (each a dark line, the lip above it catching the light), and the formwork's four tie holes."""
    img = M.tile(M.concrete(**M.CONCRETE_TILE), seed=41)
    col, row = M.grid_xy()
    for y in (7, 15):
        M.darken(img, row == y, 0.66)
        M.lighten(img, row == y - 1, 1.08, 4)
    for x, y in ((3, 3), (12, 3), (3, 11), (12, 11)):
        img[y, x, :3] = M.CONCRETE_RAMP[1]
        img[y + 1, x, :3] = M.CONCRETE_RAMP[6]
    return M.to_tex(img)


def hazard_concrete() -> Tex:
    """Concrete painted in diagonal yellow and black stripes (four texels each: they run on across a row of blocks),
    worn through to the concrete here and there by boots."""
    return M.to_tex(M.hazard_tile(width=4, seed=43, scuff=0.22))


def concrete_block(name: str, tex: Tex) -> None:
    """A plain cube (not a structure part: it is never hidden)."""
    tex.save(paths.TEXTURES / "block" / f"{name}.png")
    write_json(paths.MODELS / "block" / f"{name}.json", {"parent": "minecraft:block/cube_all", "textures": {"all": f"groundzero:block/{name}"}})
    write_json(paths.ASSETS / "blockstates" / f"{name}.json", {"variants": {"": {"model": f"groundzero:block/{name}"}}})
    write_json(paths.ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": f"groundzero:block/{name}"}})


# --- the Silo Hatch (one item, a 3x3 lid) ----------------------------------------------------------------------------------
def hatch_icon() -> Tex:
    img = M.tile(M.painted(M.ARMOUR_RAMP, tone=0.56), seed=9, edges=True)
    col, row = M.grid_xy()
    seam = (col >= 7) & (col <= 8) & (row >= 2) & (row <= 13)
    img[seam] = M.hazard_tile(width=2, seed=10)[seam]
    M.darken(img, (np.minimum(np.minimum(col, 15 - col), np.minimum(row, 15 - row)) == 1), 0.75)
    return M.to_tex(img)


def hatch_block(particle: str) -> BlockModel:
    """The hatch as placed, drawn by its middle block across all 3x3 (x and z -16..32): a steel frame flush with the
    ground holding two thick leaves, their seam striped."""
    m = BlockModel("silo_hatch", tex_size=(256, 256), kind="block")
    m.particle = particle
    m.box("frame_n", (-16, 0, -16), (32, 16, -13))
    m.box("frame_s", (-16, 0, 29), (32, 16, 32))
    m.box("frame_w", (-16, 0, -13), (-13, 16, 29))
    m.box("frame_e", (29, 0, -13), (32, 16, 29))
    m.box("leaf_w", (-13, 0, -13), (8, 15, 29), faces=("up", "down", "west", "north", "south"))
    m.box("leaf_e", (8, 0, -13), (29, 15, 29), faces=("up", "down", "east", "north", "south"))
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    S = M.Surfacer(m, seed=89)
    S.paint(t, ["frame_n", "frame_s", "frame_w", "frame_e"], M.steel(tone=0.45, rust=0.12))
    S.paint(t, ["leaf_w", "leaf_e"], M.painted(M.ARMOUR_RAMP, tone=0.56, wear=0.35, rust=0.1))
    seam = M.stripes(width=3, wear=0.3, under=M.ARMOUR_RAMP[1:6])
    S.overlay(t, ["leaf_w", "leaf_e"], lambda f, img: M.paste(img, S.material_on(f, seam), np.abs(f.X - 8) < 3), only=("up",))
    S.overlay(t, ["leaf_w", "leaf_e"], lambda f, img: M.rivets(f, img, every=5, inset=2, axis="v"), only=("up",))
    m.fit_display(gui_rotation=(30, 225, 0))
    m.save(t)
    return m


def silo_hatch() -> None:
    icon = hatch_icon()
    icon.save(paths.TEXTURES / "block" / "silo_hatch_icon.png")
    hatch_block("groundzero:block/silo_hatch_icon")
    write_json(paths.MODELS / "block" / "silo_hatch_formed.json", ST.particle_model("groundzero:block/silo_hatch_icon"))
    write_json(paths.ASSETS / "blockstates" / "silo_hatch.json", {"variants": {
        "formed=false,part=center": {"model": "groundzero:block/silo_hatch"},
        "formed=false,part=edge": {"model": "groundzero:block/silo_hatch_formed"},
        "formed=true": {"model": "groundzero:block/silo_hatch_formed"},
    }})
    write_json(paths.ASSETS / "items" / "silo_hatch.json", {"model": {"type": "minecraft:model", "model": "groundzero:block/silo_hatch"}})


def silo_placard(t: Tex) -> None:
    """The controller's placard: a missile's nose in a shaft between two door leaves."""
    t.rect(4, 9, 4, 4, P.BLACK_PAINT[1])
    t.px(4, 9, YELLOW[4])
    t.px(7, 9, YELLOW[4])
    t.vline(6, 10, 3, WHITE[5])
    t.px(6, 10, RED[4])


def silo_plan(t: Tex) -> None:
    """The spawner's sheet: the silo from above, its collar, the hatch's two leaves and the controller marked."""
    B = MT.BLUEPRINT
    for x in range(3, 13):
        t.px(x, 2, B[6])
        t.px(x, 11, B[6])
    for y in range(2, 12):
        t.px(3, y, B[6])
        t.px(12, y, B[6])
    for y in range(4, 10):
        for x in range(5, 11):
            t.px(x, y, B[3])
    for y in range(4, 10):
        t.px(7 + (y % 2), y, P.HAZARD_YELLOW[5])
    for x, y in ((3, 2), (12, 2), (3, 11), (12, 11)):
        t.px(x, y, B[5])
    t.px(7, 2, P.HAZARD_YELLOW[5])                                  # the controller at the front
    t.px(8, 2, P.HAZARD_YELLOW[5])


# --- the formed Missile Silo -------------------------------------------------------------------------------------------------
def formed() -> None:
    g = GeoModel("missile_silo", tex_size=(512, 1024))
    g.bone("frame")
    F = "frame"
    g.bone("door_l", pivot=(-13, LEAF_Y[0], CZ), parent=F)       # each leaf slides out sideways (moved by the block entity)
    g.bone("door_r", pivot=(13, LEAF_Y[0], CZ), parent=F)
    g.bone("beacon_l", parent=F)                                  # the console's beacons flash in turn while it counts down
    g.bone("beacon_r", parent=F)
    g.bone("missile", pivot=(0, TAIL, CZ), parent=F)             # its tail on the launch ring: the body's size scales it from there
    g.bone("nose_none", parent="missile")                        # its bare nose with no warhead, or the warhead's own model
    L.warhead_bone(g, "warhead", "missile", "basic", TAIL, CZ)
    g.bone("strategic", pivot=(0, TAIL, CZ), parent=F)           # a Strategic missile: its own model, not scaled
    g.bone("snose_none", parent="strategic")
    L.warhead_bone(g, "swarhead", "strategic", "strategic", TAIL, CZ, L.STRATEGIC_TAIL)
    g.bone("icbm", pivot=(0, TAIL, CZ), parent=F)                # an intercontinental missile: its own model too
    L.warhead_bone(g, "iwarhead", "icbm", "icbm", TAIL, CZ, L.ICBM_TAIL)
    g.exclusive = [{"missile", "strategic", "icbm"}]
    sides = ("north", "south", "east", "west")

    # The collar: the top layer round the opening, its top the ground.
    collar = [("c_fl", (-40, 0, -8), (-24, TOP, 8)), ("c_fr", (24, 0, -8), (40, TOP, 8)),
              ("c_bl", (-40, 0, 56), (-24, TOP, 72)), ("c_br", (24, 0, 56), (40, TOP, 72)),
              ("m_front", (-24, 0, -8), (24, TOP, 8)), ("m_back", (-24, 0, 56), (24, TOP, 72)),
              ("s_left", (-40, 0, 8), (-24, TOP, 56)), ("s_right", (24, 0, 8), (40, TOP, 56))]
    for n, a, b in collar:
        g.box(n, a, b, faces=sides + ("up",), bone=F)
        g.open_ok.add((n, "down"))                               # it sits on the shaft's top ring
    # The coaming: a striped steel lip round the opening.
    lips = [("lip_f", (-26, TOP, 6), (26, TOP + 0.6, 8)), ("lip_b", (-26, TOP, 56), (26, TOP + 0.6, 58)),
            ("lip_l", (-26, TOP, 8), (-24, TOP + 0.6, 56)), ("lip_r", (24, TOP, 8), (26, TOP + 0.6, 56))]
    for n, a, b in lips:
        g.box(n, a, b, faces=sides + ("up",), bone=F)
        g.open_ok.add((n, "down"))
    # Rails each leaf rolls out on, two a side, running past the collar's edge; a stop at each end.
    rails = []
    for side, x0, x1 in (("l", -52, -26), ("r", 26, 52)):
        for k, z in enumerate((11, 51)):
            n = f"rail_{side}{k}"
            g.box(n, (x0, TOP, z), (x1, TOP + 1, z + 2), faces=sides + ("up",), bone=F)
            g.open_ok.add((n, "down"))
            rails.append(n)
            stop = f"stop_{side}{k}"
            sx = x0 - 1 if side == "l" else x1 - 0.5           # the rail's end runs into it
            g.box(stop, (sx, TOP, z - 0.5), (sx + 1.5, TOP + 2.5, z + 2.5), faces=sides + ("up",), bone=F)
            g.open_ok.add((stop, "down"))
            rails.append(stop)
    # The door leaves: thick armour plates with a raised rib, meeting over the middle of the opening.
    leaves = []
    for bone, x0, x1 in (("door_l", -26, 0), ("door_r", 0, 26)):
        n = f"{bone}_plate"
        g.box(n, (x0, LEAF_Y[0], 6), (x1, LEAF_Y[1], 58), bone=bone)
        leaves.append(n)
        rx0, rx1 = (x0 + 4, x1 - 6) if bone == "door_l" else (x0 + 6, x1 - 4)
        n = f"{bone}_rib"
        g.box(n, (rx0, LEAF_Y[1], 11), (rx1, LEAF_Y[1] + 1, 53), faces=sides + ("up",), bone=bone)
        g.open_ok.add((n, "down"))
        leaves.append(n)
        for k, z in enumerate((13, 49)):                         # lifting eyes at the rib's ends
            n = f"{bone}_eye{k}"
            cx = (rx0 + rx1) / 2
            g.box(n, (cx - 1.5, LEAF_Y[1] + 1, z), (cx + 1.5, LEAF_Y[1] + 3, z + 2), faces=sides + ("up",), bone=bone)
            g.open_ok.add((n, "down"))
            leaves.append(n)
    # An exhaust vent grate at each corner: a steel rim, slats over the duct's dark mouth.
    vents = []
    for vx, vz in ((-32, 0), (32, 0), (-32, 64), (32, 64)):
        tag = f"{'l' if vx < 0 else 'r'}{'f' if vz < 32 else 'b'}"
        rim = [(f"vent_{tag}_n", (vx - 6, TOP, vz - 6), (vx + 6, TOP + 2, vz - 5)),
               (f"vent_{tag}_s", (vx - 6, TOP, vz + 5), (vx + 6, TOP + 2, vz + 6)),
               (f"vent_{tag}_w", (vx - 6, TOP, vz - 5), (vx - 5, TOP + 2, vz + 5)),
               (f"vent_{tag}_e", (vx + 5, TOP, vz - 5), (vx + 6, TOP + 2, vz + 5))]
        for n, a, b in rim:
            g.box(n, a, b, faces=sides + ("up",), bone=F)
            g.open_ok.add((n, "down"))
            vents.append(n)
        n = f"vent_{tag}_mouth"
        g.box(n, (vx - 5, TOP - 0.5, vz - 5), (vx + 5, TOP + 0.3, vz + 5), faces=("up",), bone=F)
        g.open_ok.add((n, "*"))
        vents.append(n)
        for k in range(4):
            n = f"vent_{tag}_slat{k}"
            z0 = vz - 4.5 + k * 2.5
            g.box(n, (vx - 5, TOP + 0.3, z0), (vx + 5, TOP + 1.5, z0 + 1), faces=sides + ("up",), bone=F)
            g.open_ok.add((n, "down"))
            vents.append(n)
    # The console: a squat armoured box with its CRT facing out (north), a hood, and two red beacons on top.
    g.box("console", (-7, TOP, -6), (7, TOP + 9, 4), faces=sides + ("up",), bone=F)
    g.open_ok.add(("console", "down"))
    g.box("hood", (-7.5, TOP + 9, -7), (7.5, TOP + 11, 4.5), bone=F)
    for side, x0 in (("l", -6), ("r", 3)):
        g.box(f"beacon_{side}_glass", (x0, TOP + 11, -1), (x0 + 3, TOP + 14, 2), faces=sides + ("up",), bone=F)
        g.box(f"beacon_{side}_lamp", (x0 - 0.2, TOP + 11, -1.2), (x0 + 3.2, TOP + 14.2, 2.2), faces=sides + ("up",), bone=f"beacon_{side}")
        g.open_ok.add((f"beacon_{side}_glass", "down"))
        g.open_ok.add((f"beacon_{side}_lamp", "down"))
    # The Energy Port: a flush plate with its socket at the back.
    g.box("port", (-6, TOP, 58), (6, TOP + 0.6, 70), faces=sides + ("up",), bone=F)
    g.open_ok.add(("port", "down"))
    g.box("socket", (-2, TOP + 0.6, 62), (2, TOP + 2, 66), faces=sides + ("up",), bone=F)
    g.open_ok.add(("socket", "down"))

    # Down the shaft: a conduit in each corner (just off the walls, which are the blocks you built).
    conduits = []
    for i, (x0, z0, walls) in enumerate(((-23.9, 8.1, ("west", "north")), (21.9, 8.1, ("east", "north")),
                                          (-23.9, 53.9, ("west", "south")), (21.9, 53.9, ("east", "south")))):
        n = f"conduit{i}"
        # All the way up to the ground (after the user's look: they stopped a block short, at the collar), its top
        # capped (it shows when the doors are open).
        g.box(n, (x0, FLOOR, z0), (x0 + 2, TOP, z0 + 2), faces=tuple(d for d in sides if d not in walls) + ("up",), bone=F)
        for d in ("down",) + walls:                           # its foot meets the floor; its back the walls
            g.open_ok.add((n, d))
        conduits.append(n)
    # The launch ring the missile stands on, its hold-down posts on the diagonals (clear of the fins), and the flame
    # duct's grate under it (the Flame Deflector's cell, hidden while formed); the duct's floor is dark.
    stand = [("ring_n", (-14, FLOOR, CZ - 14), (14, TAIL, CZ - 8)), ("ring_s", (-14, FLOOR, CZ + 8), (14, TAIL, CZ + 14)),
             ("ring_w", (-14, FLOOR, CZ - 8), (-8, TAIL, CZ + 8)), ("ring_e", (8, FLOOR, CZ - 8), (14, TAIL, CZ + 8))]
    for n, a, b in stand:
        g.box(n, a, b, faces=sides + ("up",), bone=F)
        g.open_ok.add((n, "down"))
    posts = []
    for i, (px, pz) in enumerate(((-13, CZ - 13), (11, CZ - 13), (-13, CZ + 11), (11, CZ + 11))):
        n = f"hold{i}"
        g.box(n, (px, TAIL, pz), (px + 2, TAIL + 6, pz + 2), faces=sides + ("up",), bone=F)
        g.open_ok.add((n, "down"))
        posts.append(n)
    grate = []
    for k in range(6):
        n = f"grate{k}"
        z0 = CZ - 7.5 + k * 2.75
        g.box(n, (-7.5, FLOOR - 1.5, z0), (7.5, FLOOR - 0.2, z0 + 1), faces=sides + ("up",), bone=F)
        g.open_ok.add((n, "down"))
        grate.append(n)
    g.box("duct", (-7.9, FLOOR - 15, CZ - 7.9), (7.9, FLOOR - 14, CZ + 7.9), faces=("up",), bone=F)
    g.open_ok.add(("duct", "*"))

    # The missile: as on the Launch Complex, the item model four times over, its tail on the ring (or a Strategic or an
    # intercontinental one's own model, its tail on the same line).
    L.place_missile(g, L.missile_parts(), "m_", "missile", "n_", "nose_none", TAIL, CZ)
    L.place_missile(g, L.strategic_parts(), "s_", "strategic", "sn_", "snose_none", TAIL, CZ, L.STRATEGIC_TAIL)
    L.place_missile(g, L.icbm_parts(), "i_", "icbm", "in_", "icbm", TAIL, CZ, L.ICBM_TAIL)
    g.layout()
    tex = Tex(g.tex_w, g.tex_h)
    glow = Tex(g.tex_w, g.tex_h, CLEAR)

    # --- paint: materials in model space (tools/gz/materials.py, the remaster after 9b) ---
    S = M.Surfacer(g, seed=31)
    # The collar: one cast slab round the opening, a band of worn hazard paint round its outer edge.
    collar_names = [n for n, _, _ in collar]
    S.paint(tex, collar_names, M.concrete(tone=0.62))
    band = M.stripes(width=3, wear=0.35)

    def outer_band(f, img):
        if f.d == "up":
            M.paste(img, S.material_on(f, band), np.maximum(np.abs(f.X), np.abs(f.Z - CZ)) > 40 - 2.6)
    S.overlay(tex, collar_names, outer_band)
    # The coaming round the opening: striped steel lips. The rails: rolled steel, their stops yellow.
    S.paint(tex, [n for n, _, _ in lips], M.stripes(width=2, wear=0.45, under=M.STEEL_RAMP[2:7]))
    S.paint(tex, [n for n in rails if n.startswith("rail")], M.steel(tone=0.58, brushed=0.06))
    S.paint(tex, [n for n in rails if n.startswith("stop")], M.painted(M.YELLOW_PAINT_RAMP, wear=0.6))
    # The doors: heavy blue-grey armour, chipped at its edges, bolted along its long sides, striped where they meet.
    for bone in ("door_l", "door_r"):
        plate = f"{bone}_plate"
        S.paint(tex, plate, M.painted(M.ARMOUR_RAMP, tone=0.56, wear=0.35, rust=0.12))
        seam = M.stripes(width=3, wear=0.3, under=M.ARMOUR_RAMP[1:6])
        inner = "east" if bone == "door_l" else "west"

        def meeting(f, img, inner=inner, seam=seam):
            mask = (np.abs(f.X) < 4) if f.d == "up" else np.full(f.shape, f.d == inner)
            M.paste(img, S.material_on(f, seam), mask)
        S.overlay(tex, plate, meeting)
        S.overlay(tex, plate, lambda f, img: M.rivets(f, img, every=5, inset=2, axis="v") if f.d == "up" else None)
        S.paint(tex, f"{bone}_rib", M.painted(M.ARMOUR_RAMP, tone=0.68, wear=0.3))
        S.paint(tex, [f"{bone}_eye{k}" for k in range(2)], M.steel(tone=0.62))
    # The vents: sooted steel rims and slats over the duct's black mouth.
    S.paint(tex, [n for n in vents if not n.endswith("mouth") and "slat" not in n], M.steel(tone=0.42, rust=0.25))
    S.paint(tex, [n for n in vents if "slat" in n], M.Material(ramp=M.SOOT_RAMP, tone=0.62, edge_light=0.3, dither=0.6))
    S.paint(tex, [n for n in vents if n.endswith("mouth")], M.Material(ramp=M.SOOT_RAMP, tone=0.12, dither=1.0, grain=0.06))
    # The console: olive paint, its screen and lamps drawn over it.
    S.paint(tex, ["console", "hood"], M.painted(M.OLIVE_RAMP, tone=0.55, wear=0.3))

    def screen(t: Tex, r, d):
        x, y, w, h = r
        if d == "north" and w > 8:
            # The CRT: a green phosphor screen with a status line and a count, and three lamps under it.
            t.rect(x + 2, y + 1, w - 4, 5, P.BLACK_PAINT.deep)
            t.outline(x + 1, y, w - 2, 7, OLIVE[1])
            for xx in range(x + 3, x + w - 3):
                t.px(xx, y + 2, GREEN[3] if xx % 3 else GREEN[1])
            for xx in range(x + 3, x + 3 + (w - 6) // 2):
                t.px(xx, y + 4, GREEN[4])
            t.px(x + 3, y + h - 2, RED[4])
            t.px(x + 5, y + h - 2, YELLOW[4])
            t.px(x + w - 4, y + h - 2, GREEN[4])
    g.paint_all(tex, "console", screen)
    cx, cy, cw, ch = g.rect("console", "north")
    glow.rect(cx + 2, cy + 1, cw - 4, 5, (20, 60, 24, 255))
    for xx in range(cx + 3, cx + cw - 3):
        glow.px(xx, cy + 2, GREEN[3] if xx % 3 else GREEN[1])
    for xx in range(cx + 3, cx + 3 + (cw - 6) // 2):
        glow.px(xx, cy + 4, GREEN[4])
    for bone in ("beacon_l", "beacon_r"):
        n = f"{bone}_lamp"
        g.paint_all(tex, n, lambda t, r, d: (t.rect(*r, RED[4]), t.px(r[0], r[1], RED[6])))
        g.paint_all(glow, n, lambda t, r, d: (t.rect(*r, RED[4]), t.px(r[0], r[1], RED[6])))
        g.paint_all(tex, f"{bone}_glass", lambda t, r, d: (t.rect(*r, RED[1]), t.px(r[0], r[1], RED[3])))
    # The Energy Port's plate: red and black hazard paint round a steel socket plate.
    S.paint(tex, "port", M.Striped(ramp=M.RED_PAINT_RAMP, tone=0.55, dither=0.4, wear=0.3, under=M.STEEL_RAMP[2:7],
                                   other=M.painted(M.BLACK_RAMP, tone=0.5), width=1.5))
    S.overlay(tex, "port", lambda f, img: M.paste(img, S.material_on(f, M.steel(tone=0.5)), f.edge_near() > 2) if f.d == "up" else None)
    S.paint(tex, "socket", M.steel(tone=0.3))
    # Down the shaft: grey-painted conduits clamped every block, the yellow launch ring and posts, the sooted grate over
    # the duct.
    S.paint(tex, conduits, M.painted(M.GREY_PAINT_RAMP, tone=0.48, wear=0.2, rust=0.15))
    S.overlay(tex, conduits, lambda f, img: M.seams(f, img, every=16, axis="v", offset=7) if f.vertical else None)
    S.paint(tex, [n for n, _, _ in stand], M.painted(M.YELLOW_PAINT_RAMP, tone=0.5, wear=0.55, rust=0.2))
    S.paint(tex, posts, M.stripes(width=2, wear=0.4, under=M.STEEL_RAMP[2:7]))
    S.paint(tex, grate, M.steel(tone=0.22, rust=0.2))
    S.paint(tex, "duct", M.Material(ramp=M.SOOT_RAMP, tone=0.08, dither=1.0))
    L.paint_missiles(g, tex, TAIL, CZ)                           # as on the complex
    g.save(tex, glow)
    # While it counts down (the machine's "working"), the beacons flash in turn, twice a second.
    count = Animation("working", 1.0, loop=True)
    count.scale("beacon_l", {0: (1, 1, 1), 0.49: (1, 1, 1), 0.5: (0, 0, 0), 0.99: (0, 0, 0), 1: (1, 1, 1)})
    count.scale("beacon_r", {0: (0, 0, 0), 0.49: (0, 0, 0), 0.5: (1, 1, 1), 0.99: (1, 1, 1), 1: (0, 0, 0)})
    idle = Animation("idle", 1.0, loop="hold_on_last_frame")
    idle.scale("beacon_l", {0: (0, 0, 0), 1: (0, 0, 0)})
    idle.scale("beacon_r", {0: (0, 0, 0), 1: (0, 0, 0)})
    # (Not played: the doors open by the block entity's door position. For the preview renderer.)
    doors = Animation("preview_doors", 2.0, loop="hold_on_last_frame")
    doors.move("door_l", {0: (0, 0, 0), 2: (-SLIDE, 0, 0)}, easing="easeInOutSine")
    doors.move("door_r", {0: (0, 0, 0), 2: (SLIDE, 0, 0)}, easing="easeInOutSine")
    print(save_animations("missile_silo", [count, idle, doors]))


def _stripe_band(t: Tex, x: int, y: int, w: int, h: int, horizontal: bool = True) -> None:
    """Hazard stripes along a strip: 3-px yellow and black blocks."""
    if horizontal:
        for i in range(w):
            t.vline(x + i, y, h, YELLOW[4] if (i // 3) % 2 == 0 else BLACK[3])
    else:
        for i in range(h):
            t.hline(x, y + i, w, YELLOW[4] if (i // 3) % 2 == 0 else BLACK[3])


def main() -> None:
    concrete_block("reinforced_concrete", reinforced_concrete())
    ST.part_block("hazard_concrete", hazard_concrete())
    silo_hatch()
    ST.controller("missile_silo", ST.controller_front(silo_placard), ST.controller_top(), ST.casing())
    MT.structure_spawner("missile_silo_spawner", silo_plan)
    formed()
    print("silo written")


if __name__ == "__main__":
    main()
