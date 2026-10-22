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
  Energy Port's flush plate at the back; down the shaft, conduits in its corners, caged amber lamps on its walls, and
  the missile standing on its launch ring over the flame duct at the bottom.

Coordinates are model pixels with the controller's cell centred on x = z = 0 and y = 0 the bottom of the top layer:
the opening is x -24..24, z 8..56; the ground (the collar's top) is y 16; the shaft's floor is y -192.
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))

from gz import palette as P  # noqa: E402
from gz import paths  # noqa: E402
from gz.geo import Animation, GeoModel, save_animations  # noqa: E402
from gz.model import BlockModel, write_json  # noqa: E402
from gz.palette import fixed  # noqa: E402
from gz.tex import Tex  # noqa: E402
import grenades as G  # noqa: E402
import launch as L  # noqa: E402
import multiblock_tools as MT  # noqa: E402
import structures as ST  # noqa: E402

CONCRETE, STEEL, GUN, OLIVE = P.CONCRETE, P.STEEL, P.GUNMETAL, P.OLIVE
YELLOW, BLACK, RED, GREEN = P.HAZARD_YELLOW, P.HAZARD_BLACK, P.WARNING_RED, P.INDICATOR_GREEN
WHITE = P.WHITE_CERAMIC
AMBER = P.NUCLEAR_AMBER
SOOT = L.SOOT
#: The door leaves' armour: a dark blue-grey steel, darker than the collar so the doors read from a distance.
ARMOUR = fixed("silo_armour", "#16191d", "#22272c", "#30363c", "#40474e", "#535b63", "#6c757e", "#8e98a2")
CLEAR = (0, 0, 0, 0)
shaded = G.shaded
tone = L.tone

TOP = 16                      # the ground: the collar's top face
FLOOR = -192                  # the shaft's floor (the top of the bottom layer)
TAIL = FLOOR + 3              # the missile's tail, on its launch ring
CZ = 32                       # the shaft's middle (z); its middle in x is 0
HALF = 24                     # half the shaft's width
LEAF_Y = (17, 24)             # the door leaves, resting on their rails (MissileSiloBlockEntity: the hatch is 24 px tall)
SLIDE = 24                    # how far each leaf slides out to open


# --- the building blocks ---------------------------------------------------------------------------------------------------
def reinforced_concrete() -> Tex:
    """Formed concrete: smooth and even, the seam where two formwork boards met across the middle and at the bottom
    (each a dark line with the lip above it catching the light), the formwork's four tie holes, a little grit."""
    t = Tex(16, 16)
    t.rect(0, 0, 16, 16, CONCRETE[3])
    t.speckle(0, 0, 16, 16, [CONCRETE[4]], seed=41, density=0.05)
    t.speckle(0, 0, 16, 16, [(*CONCRETE[2][:3], 255)], seed=42, density=0.04)
    for y in (7, 15):
        t.hline(0, y, 16, CONCRETE[2])                     # the board seams
        t.hline(0, y - 1 if y > 0 else 0, 16, CONCRETE[4])
    for x, y in ((3, 3), (12, 3), (3, 11), (12, 11)):
        t.px(x, y, CONCRETE[1])                            # the tie holes, their lower edge lit
        t.px(x, y + 1, CONCRETE[4])
    return t


def hazard_concrete() -> Tex:
    """Concrete painted in diagonal yellow and black stripes (period 8: they run on across a row of blocks), a few
    scuffs where boots have worn the paint."""
    t = Tex(16, 16)
    for y in range(16):
        for x in range(16):
            yellow = ((x + y) // 4) % 2 == 0
            t.px(x, y, YELLOW[4] if yellow else BLACK[3])
    for x, y in ((2, 5), (13, 11), (6, 13)):
        t.px(x, y, CONCRETE[3])                            # scuffed through to the concrete
    for x, y in ((3, 5), (6, 12)):
        t.px(x, y, CONCRETE[2])
    for x, y in ((9, 1), (1, 9), (12, 5), (5, 3), (14, 13)):
        if ((x + y) // 4) % 2 == 0:
            t.px(x, y, YELLOW[3])                          # the paint a little uneven (on the yellow only)
    return t


def concrete_block(name: str, tex: Tex) -> None:
    """A plain cube (not a structure part: it is never hidden)."""
    tex.save(paths.TEXTURES / "block" / f"{name}.png")
    write_json(paths.MODELS / "block" / f"{name}.json", {"parent": "minecraft:block/cube_all", "textures": {"all": f"groundzero:block/{name}"}})
    write_json(paths.ASSETS / "blockstates" / f"{name}.json", {"variants": {"": {"model": f"groundzero:block/{name}"}}})
    write_json(paths.ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": f"groundzero:block/{name}"}})


# --- the Silo Hatch (one item, a 3x3 lid) ----------------------------------------------------------------------------------
def hatch_icon() -> Tex:
    t = Tex(16, 16)
    t.rect(0, 0, 16, 16, ARMOUR[3])
    t.outline(0, 0, 16, 16, GUN[1])
    t.outline(1, 1, 14, 14, GUN[4])
    for y in range(2, 14):
        t.px(7, y, YELLOW[4] if (y // 2) % 2 == 0 else BLACK[2])
        t.px(8, y, BLACK[2] if (y // 2) % 2 == 0 else YELLOW[4])
    return t


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
    for n in ("frame_n", "frame_s", "frame_w", "frame_e"):
        def frame(t, r, d):
            x, y, w, h = r
            t.rect(x, y, w, h, GUN[tone(d) + 1])
            if d == "up":
                t.outline(x, y, w, h, GUN[1])
        m.paint_all(t, n, frame)

    def leaf(t, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, ARMOUR[tone(d) + 1])
        if d == "up":
            t.outline(x, y, w, h, ARMOUR[1])
            for yy in range(y + 3, y + h - 2, 6):
                for xx in range(x + 3, x + w - 2, 6):
                    t.px(xx, yy, ARMOUR[5])
            # The stripes along the seam (the leaves' inner edges meet in the middle).
            for yy in range(y, y + h):
                for k in range(3):
                    xx = x + w - 1 - k if w > 3 else x
                    t.px(xx, yy, YELLOW[4] if ((yy - y) // 3 + k) % 2 == 0 else BLACK[3])
    m.paint_all(t, "leaf_w", leaf)
    m.paint_all(t, "leaf_e", lambda t, r, d: leaf(t, r, d) if d != "up" else _leaf_east_up(t, r))
    m.fit_display(gui_rotation=(30, 225, 0))
    m.save(t)
    return m


def _leaf_east_up(t: Tex, r) -> None:
    x, y, w, h = r
    t.rect(x, y, w, h, ARMOUR[5])
    t.outline(x, y, w, h, ARMOUR[1])
    for yy in range(y + 3, y + h - 2, 6):
        for xx in range(x + 3, x + w - 2, 6):
            t.px(xx, yy, ARMOUR[6])
    for yy in range(y, y + h):
        for k in range(3):
            t.px(x + k, yy, YELLOW[4] if ((yy - y) // 3 + k) % 2 == 0 else BLACK[3])


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
    for nose in L.BENCH_NOSES:
        g.bone(f"nose_{nose}", parent="missile")
    g.bone("strategic", pivot=(0, TAIL, CZ), parent=F)           # a Strategic missile: its own model, not scaled
    for nose in L.BENCH_NOSES:
        g.bone(f"snose_{nose}", parent="strategic")
    g.exclusive = [{f"nose_{nose}" for nose in L.BENCH_NOSES}, {f"snose_{nose}" for nose in L.BENCH_NOSES}, {"missile", "strategic"}]
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

    # Down the shaft: a conduit in each corner (just off the walls, which are the blocks you built), lamps on the walls.
    conduits = []
    for i, (x0, z0, walls) in enumerate(((-23.9, 8.1, ("west", "north")), (21.9, 8.1, ("east", "north")),
                                          (-23.9, 53.9, ("west", "south")), (21.9, 53.9, ("east", "south")))):
        n = f"conduit{i}"
        g.box(n, (x0, FLOOR, z0), (x0 + 2, 0, z0 + 2), faces=tuple(d for d in sides if d not in walls), bone=F)
        for d in ("up", "down") + walls:                      # its ends meet floor and collar; its back the walls
            g.open_ok.add((n, d))
        conduits.append(n)
    lamps = []
    for k, y in enumerate((-170, -122, -74, -26)):
        off = 14 if k % 2 == 0 else -14
        for wall, back, a, b in (("f", "north", (off - 1.5, y, 8.05), (off + 1.5, y + 2.5, 9.5)),
                                 ("b", "south", (-off - 1.5, y, 54.5), (-off + 1.5, y + 2.5, 55.95)),
                                 ("l", "west", (-23.95, y, CZ - off - 1.5), (-22.5, y + 2.5, CZ - off + 1.5)),
                                 ("r", "east", (22.5, y, CZ + off - 1.5), (23.95, y + 2.5, CZ + off + 1.5))):
            n = f"lamp{k}{wall}"
            g.box(n, a, b, faces=tuple(d for d in sides + ("up", "down") if d != back), bone=F)
            g.open_ok.add((n, back))                          # against the wall
            lamps.append(n)
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

    # The missile: as on the Launch Complex, the item model four times over, its tail on the ring.
    for name, frm, to, faces, kind in L.missile_parts():
        a = ((frm[0] - 8) * 4, frm[1] * 4 + TAIL, (frm[2] - 8) * 4 + CZ)
        b = ((to[0] - 8) * 4, to[1] * 4 + TAIL, (to[2] - 8) * 4 + CZ)
        if kind in L.NOSE_PARTS:
            for nose in L.BENCH_NOSES:
                g.box(f"n_{nose}_{name}", a, b, faces=faces, bone=f"nose_{nose}")
        else:
            g.box("m_" + name, a, b, faces=faces, bone="missile")
    for name, frm, to, faces, kind in L.strategic_parts():
        a = ((frm[0] - 8) * 4, (frm[1] + L.STRATEGIC_TAIL) * 4 + TAIL, (frm[2] - 8) * 4 + CZ)
        b = ((to[0] - 8) * 4, (to[1] + L.STRATEGIC_TAIL) * 4 + TAIL, (to[2] - 8) * 4 + CZ)
        if kind in L.NOSE_PARTS:
            for nose in L.BENCH_NOSES:
                g.box(f"sn_{nose}_{name}", a, b, faces=faces, bone=f"snose_{nose}")
        else:
            g.box("s_" + name, a, b, faces=faces, bone="strategic")
    g.layout()
    tex = Tex(g.tex_w, g.tex_h)
    glow = Tex(g.tex_w, g.tex_h, CLEAR)

    # --- paint ---
    def collar_paint(t: Tex, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, CONCRETE[tone(d) + 1])
        if d == "up":
            t.speckle(x, y, w, h, [CONCRETE[3], CONCRETE[5]], seed=x * 7 + y, density=0.07)
            t.speckle(x, y, w, h, [CONCRETE[2]], seed=x * 3 + y * 5, density=0.02)
        else:
            t.hline(x, y, w, CONCRETE[5])
            t.speckle(x, y + 1, w, h - 1, [CONCRETE[2]], seed=x + y, density=0.05)
    for n, _, _ in collar:
        g.paint_all(tex, n, collar_paint)
    # The ring's outer edge: a band of hazard stripes along the outside of the collar's top.
    for n in ("c_fl", "c_fr", "c_bl", "c_br", "m_front", "m_back", "s_left", "s_right"):
        x, y, w, h = g.rect(n, "up")
        box = g.get(n)
        # (up face: top row = north, left column = west)
        if box.frm[2] <= -8:
            _stripe_band(tex, x, y, w, 1, horizontal=True)
        if box.to[2] >= 72:
            _stripe_band(tex, x, y + h - 1, w, 1, horizontal=True)
        if box.frm[0] <= -40:
            _stripe_band(tex, x, y, 1, h, horizontal=False)
        if box.to[0] >= 40:
            _stripe_band(tex, x + w - 1, y, 1, h, horizontal=False)
    for n, _, _ in lips:
        g.paint_all(tex, n, lambda t, r, d: _stripe_band(t, *r, horizontal=r[2] >= r[3]) if d == "up"
                    else t.rect(*r, YELLOW[tone(d)]))
    for n in rails:
        g.paint_all(tex, n, (lambda t, r, d: (t.rect(*r, STEEL[tone(d) + 1]), t.hline(r[0], r[1], r[2], STEEL[5]) if d == "up" else None))
                    if n.startswith("rail") else lambda t, r, d: t.rect(*r, YELLOW[tone(d) + 1]))

    def plate(t: Tex, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, ARMOUR[tone(d) + 1])
        if d == "up":
            t.outline(x, y, w, h, ARMOUR[1])
            t.outline(x + 1, y + 1, w - 2, h - 2, ARMOUR[4])
            for yy in range(y + 3, y + h - 2, 5):              # bolt rows along the long edges
                t.px(x + 2, yy, ARMOUR[6])
                t.px(x + w - 3, yy, ARMOUR[6])
        elif d in ("north", "south", "east", "west"):
            t.hline(x, y, w, ARMOUR[5])
            t.hline(x, y + h - 1, w, ARMOUR[1])
    for bone in ("door_l", "door_r"):
        g.paint_all(tex, f"{bone}_plate", plate)
        # The stripes along the seam where the leaves meet.
        x, y, w, h = g.rect(f"{bone}_plate", "up")
        seam_x = x + w - 4 if bone == "door_l" else x
        for yy in range(y + 1, y + h - 1):
            for k in range(4):
                tex.px(seam_x + k, yy, YELLOW[4] if ((yy - y) // 3 + k) % 2 == 0 else BLACK[3])
        for d in ("east" if bone == "door_l" else "west",):     # the meeting edge itself
            ex, ey, ew, eh = g.rect(f"{bone}_plate", d)
            for xx in range(ex, ex + ew):
                for yy in range(ey, ey + eh):
                    tex.px(xx, yy, YELLOW[3] if ((xx - ex + yy - ey) // 2) % 2 == 0 else BLACK[2])
        g.paint_all(tex, f"{bone}_rib", lambda t, r, d: (t.rect(*r, ARMOUR[tone(d) + 2]), t.outline(*r, ARMOUR[2]) if d == "up" else None))
        for k in range(2):
            g.paint_all(tex, f"{bone}_eye{k}", shaded(STEEL))

    def vent_paint(t: Tex, r, d, n):
        x, y, w, h = r
        if n.endswith("mouth"):
            t.rect(x, y, w, h, SOOT[0])
            t.speckle(x, y, w, h, [SOOT[1]], seed=x + y, density=0.2)
            return
        t.rect(x, y, w, h, GUN[tone(d) + 1])
        if "slat" in n and d == "up":
            t.hline(x, y, w, GUN[5])
            t.speckle(x, y, w, h, [SOOT[3]], seed=x * 5 + y, density=0.25)   # sooted from the exhaust
    for n in vents:
        g.paint_all(tex, n, lambda t, r, d, n=n: vent_paint(t, r, d, n))

    def console(t: Tex, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, OLIVE[tone(d) + 1])
        if d == "north" and w > 8:
            # The CRT: a green phosphor screen with a status line and a count, and two lamps under it.
            t.rect(x + 2, y + 1, w - 4, 5, P.BLACK_PAINT.deep)
            t.outline(x + 1, y, w - 2, 7, OLIVE[1])
            for xx in range(x + 3, x + w - 3):
                t.px(xx, y + 2, GREEN[3] if xx % 3 else GREEN[1])
            for xx in range(x + 3, x + 3 + (w - 6) // 2):
                t.px(xx, y + 4, GREEN[4])
            t.px(x + 3, y + h - 2, RED[4])
            t.px(x + 5, y + h - 2, YELLOW[4])
            t.px(x + w - 4, y + h - 2, GREEN[4])
    g.paint_all(tex, "console", console)
    cx, cy, cw, ch = g.rect("console", "north")
    glow.rect(cx + 2, cy + 1, cw - 4, 5, (20, 60, 24, 255))
    for xx in range(cx + 3, cx + cw - 3):
        glow.px(xx, cy + 2, GREEN[3] if xx % 3 else GREEN[1])
    for xx in range(cx + 3, cx + 3 + (cw - 6) // 2):
        glow.px(xx, cy + 4, GREEN[4])
    g.paint_all(tex, "hood", lambda t, r, d: (t.rect(*r, OLIVE[tone(d)]), t.hline(r[0], r[1], r[2], OLIVE[4]) if d != "down" else None))
    for bone in ("beacon_l", "beacon_r"):
        n = f"{bone}_lamp"
        g.paint_all(tex, n, lambda t, r, d: (t.rect(*r, RED[4]), t.px(r[0], r[1], RED[6])))
        g.paint_all(glow, n, lambda t, r, d: (t.rect(*r, RED[4]), t.px(r[0], r[1], RED[6])))
        g.paint_all(tex, f"{bone}_glass", lambda t, r, d: (t.rect(*r, RED[1]), t.px(r[0], r[1], RED[3])))

    def port(t: Tex, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, GUN[tone(d) + 1])
        if d == "up":
            t.stripes(x, y, w, h, RED[3], BLACK[2], width=1)
            t.rect(x + 2, y + 2, w - 4, h - 4, GUN[3])
            for bx, by in ((x + 1, y + 1), (x + w - 2, y + 1), (x + 1, y + h - 2), (x + w - 2, y + h - 2)):
                t.px(bx, by, STEEL[5])
    g.paint_all(tex, "port", port)
    g.paint_all(tex, "socket", lambda t, r, d: (t.rect(*r, GUN[tone(d)]), t.rect(r[0] + 1, r[1] + 1, max(1, r[2] - 2), max(1, r[3] - 2), BLACK[1]) if d == "up" else None))
    for n in conduits:
        g.paint_all(tex, n, lambda t, r, d: (t.rect(*r, GUN[tone(d) + 1]),
                                             [t.hline(r[0], yy, r[2], GUN[1]) for yy in range(r[1] + 7, r[1] + r[3], 16)]))
    for n in lamps:
        def lamp(t: Tex, r, d):
            x, y, w, h = r
            t.rect(x, y, w, h, AMBER[5])
            t.outline(x, y, w, h, GUN[2])                        # the cage round it
        g.paint_all(tex, n, lamp)
        g.paint_all(glow, n, lambda t, r, d: (t.rect(*r, AMBER[5]), t.outline(*r, AMBER[3])))
    for n, _, _ in stand:
        g.paint_all(tex, n, lambda t, r, d: (t.rect(*r, GUN[tone(d) + 1]), t.outline(*r, YELLOW[3]) if d == "up" else None))
    for n in posts:
        g.paint_all(tex, n, lambda t, r, d: (t.rect(*r, YELLOW[tone(d) + 1]), t.hline(r[0], r[1], r[2], BLACK[3])))
    for n in grate:
        g.paint_all(tex, n, lambda t, r, d: t.rect(*r, SOOT[tone(d) + 2]))
    g.paint_all(tex, "duct", lambda t, r, d: t.rect(*r, SOOT[0]))
    for name, _, _, _, kind in L.missile_parts():
        if kind in L.NOSE_PARTS:
            for nose in L.BENCH_NOSES:
                g.paint_all(tex, f"n_{nose}_{name}", L.missile_paint(kind, nose))
        else:
            g.paint_all(tex, "m_" + name, L.missile_paint(kind))
    for name, _, _, _, kind in L.strategic_parts():
        if kind in L.NOSE_PARTS:
            for nose in L.BENCH_NOSES:
                g.paint_all(tex, f"sn_{nose}_{name}", L.strategic_paint(kind, nose))
        else:
            g.paint_all(tex, "s_" + name, L.strategic_paint(kind))
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
