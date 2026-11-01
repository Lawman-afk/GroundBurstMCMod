"""The Special Weapons Assembler (DESIGN.md §16.15; Phase 11, after the user's second look: the exotic weapons' own
assembler, apart from the ICBM Assembly Hall).

- Controller (block): the machine cabinet, its placard a missile through a violet ring.
- The formed bay (GeckoLib), on the hall's footprint: 7 blocks wide, 13 deep and 4 high. A sealed grey floor with violet
  bay lines, hazard bands round its edge and a violet keep-out band round each ring's plinth; three magnetic
  containment rings across the bay at the fourth, seventh and tenth rows, each a dark violet housing sixteen planks
  round on a plinth, a copper-wound rotor inside it whose inner face glows violet, four emitter nodes on it; the missile
  on two cradles through them (bone "missile": the game draws it from its own model, nose to the front, section by
  section as the work goes on); power conduits from the back along both sides of the bay to each plinth; a control desk
  at the controller; the Energy and Item Ports as plates in the floor where their blocks are; a lead cask for the cores
  beside the desk. While it works ("working") the rotors turn, the middle one the other way, and the containment fields
  light (bone "field", shown only then: a thin violet membrane round the missile in each ring, glow only).
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))

import numpy as np  # noqa: E402

from gb import machine_kit as MK  # noqa: E402
from gb import materials as M  # noqa: E402
from gb import palette as P  # noqa: E402
from gb.geo import Animation, GeoModel, save_animations  # noqa: E402
from gb.tex import Tex  # noqa: E402
import assemblers as A  # noqa: E402
import launch as L  # noqa: E402
import multiblock_tools as MT  # noqa: E402
import structures as ST  # noqa: E402

NAME = "special_weapons_assembler"
CLEAR = (0, 0, 0, 0)
#: The floor's top, the missile's axis (the rings' centre) and where its tail lies (pixels; the controller's block at 0).
FLOOR_Y, AXIS_Y, Z_TAIL = 14.0, 40.0, 184.0
#: The rings' middles along the bay (the fourth, seventh and tenth rows: GBStrategic.RING_ROWS).
RING_Z = (48.0, 96.0, 144.0)
#: Planks round a ring; the housing, the rotor inside it and the field inside that (radii out from the axis).
PLANKS = 16
HOUSING = (19.0, 24.0)
ROTOR = (15.0, 18.9)
FIELD = (12.5, 14.95)
#: Half their depths along the bay. Every other plank is sunk this much at its faces, so the faces where neighbours
#: overlap (near the inner edge) never lie in one plane.
HOUSING_D, ROTOR_D, FIELD_D, SINK = 5.0, 4.0, 0.25, 0.06
#: The cradles (their middles along the bay) and their saddles' tops (SpecialWeaponsBlockEntity.CRADLES: 20 and 64 px up
#: the missile from its tail, so it lies with its axis at the rings' middle).
CRADLE_Z, SADDLE_TOP = (Z_TAIL - 20.0, Z_TAIL - 64.0), 28.5
#: The conduits' middles across the bay.
CONDUIT_X = 32.0

VIOLET_RAMP = M.ramp("#140c1e", "#21142f", "#2f1c42", "#3f2558", "#52306f", "#673e88", "#7f52a3", "#9d6fbf", "#c19bdc")
SIDES = ("north", "south", "east", "west")


def placard(t: Tex) -> None:
    """The controller's placard: a missile lying through a violet ring."""
    t.rect(4, 9, 6, 4, P.SINGULARITY[1])
    t.hline(4, 11, 6, P.WHITE_CERAMIC[5])                      # the missile
    t.px(9, 11, P.WARNING_RED[3])
    for x, y in ((6, 9), (7, 9), (5, 10), (8, 10), (5, 12), (8, 12), (6, 12), (7, 12)):
        t.px(x, y, P.ANTIMATTER[5])                             # the ring round it


def plank(g: GeoModel, name: str, k: int, z: float, radii: tuple[float, float], half: float, faces, bone: str):
    """One of a ring's planks: a box spanning the ring's width {radii} out from the axis, as wide as a sixteenth of the
    ring's outside, turned round the axis to its place. Every other one is sunk {SINK} at its front and back."""
    ri, ro = radii
    w = ro * math.tan(math.pi / PLANKS)
    d = half - (SINK if k % 2 else 0.0)
    return g.box(name, (-w, AXIS_Y + ri, z - d), (w, AXIS_Y + ro, z + d), faces=faces, bone=bone,
                 rotation={"origin": (0.0, AXIS_Y, z), "axis": "z", "angle": k * 360.0 / PLANKS})


def build() -> tuple[GeoModel, Tex, Tex, dict]:
    g = GeoModel(NAME, tex_size=(512, 512))
    g.bone("frame")
    for i, z in enumerate(RING_Z):
        g.bone(f"rotor{i}", pivot=(0, AXIS_Y, z), parent="frame")
    g.bone("field", pivot=(0, AXIS_Y, RING_Z[1]), parent="frame")
    # The missile on the cradles: the game draws its sections from its own model (MachineGeoRenderer's missile layer),
    # lying along the bay through the rings, its tail here.
    g.bone("missile", pivot=(0, AXIS_Y, Z_TAIL), parent="frame")
    F = "frame"
    parts: dict[str, list] = {}

    def add(group, box):
        parts.setdefault(group, []).append(box)
        return box

    add("floor", g.box("floor", (-56, 0, -8), (56, FLOOR_Y, 200), faces=("up",) + SIDES, bone=F))
    # The rings: each a housing on its plinth, a rotor turning inside it, a field inside that.
    for i, z in enumerate(RING_Z):
        add("plinth", g.box(f"plinth{i}", (-18, FLOOR_Y, z - 6), (18, 19, z + 6), faces=("up",) + SIDES, bone=F))
        add("lamp", g.box(f"lamp{i}", (-4, 15.5, z - 6.3), (4, 17.5, z - 6), faces=("north", "up", "east", "west"), bone=F))
        for k in range(PLANKS):
            add("housing", plank(g, f"housing{i}_{k}", k, z, HOUSING, HOUSING_D, ("up", "down", "north", "south"), F))
            add(f"rotor{i}", plank(g, f"rotor{i}_{k}", k, z, ROTOR, ROTOR_D, ("down", "north", "south"), f"rotor{i}"))
            add("field", plank(g, f"field{i}_{k}", k, z, FIELD, FIELD_D, ("north", "south"), "field"))
    # The cradles, under the first stage and the second (their saddles' tops at SADDLE_TOP).
    for i, zc in enumerate(CRADLE_Z):
        for n, a, b, faces in ((f"cradle{i}_base", (-14, FLOOR_Y, zc - 5), (14, 18, zc + 5), SIDES + ("up",)),
                               (f"cradle{i}_mid", (-8, 18, zc - 4), (8, SADDLE_TOP, zc + 4), ("north", "south", "up")),
                               (f"cradle{i}_pad_w", (-14, 18, zc - 5), (-8, 32, zc + 5), ("north", "south", "west", "up")),
                               (f"cradle{i}_pad_e", (8, 18, zc - 5), (14, 32, zc + 5), ("north", "south", "east", "up"))):
            add("cradle", g.box(n, a, b, faces=faces, bone=F))
    # Power: a conduit up each side of the bay from a crossing conduit at the back, and a branch to each plinth.
    add("conduit", g.box("conduit_back", (-CONDUIT_X - 2, FLOOR_Y, 178), (CONDUIT_X + 2, FLOOR_Y + 1.5, 182), faces=("up",) + SIDES, bone=F))
    add("conduit", g.box("conduit_feed", (-2, FLOOR_Y, 182), (2, FLOOR_Y + 1.5, 185), faces=("up", "east", "west"), bone=F))
    for side, sx in (("w", -1), ("e", 1)):
        x0, x1 = sorted((sx * (CONDUIT_X - 2), sx * (CONDUIT_X + 2)))
        add("conduit", g.box(f"conduit_{side}", (x0, FLOOR_Y, RING_Z[0] - 2), (x1, FLOOR_Y + 1.5, 178), faces=("up",) + SIDES, bone=F))
        for i, z in enumerate(RING_Z):
            b0, b1 = sorted((sx * 18, sx * (CONDUIT_X - 2)))
            add("conduit", g.box(f"branch_{side}{i}", (b0, FLOOR_Y, z - 2), (b1, FLOOR_Y + 1.5, z + 2), faces=("up", "north", "south"), bone=F))
    # The desk at the controller, and the cores' lead cask beside it.
    add("desk", g.box("desk", (-7, FLOOR_Y, -6), (7, 26, 2), faces=SIDES + ("up",), bone=F))
    add("screen", g.box("screen", (-6, 26, -5), (6, 31, -3), faces=SIDES + ("up",), bone=F))
    add("cask", g.box("cask", (-37, FLOOR_Y, 5), (-25, 25, 17), faces=SIDES, bone=F))
    add("cask_lid", g.box("cask_lid", (-38, 25, 4), (-24, 27.5, 18), faces=SIDES + ("up", "down"), bone=F))
    # The ports are floor blocks (the back row's middle and two west of it): plates flush with the floor over them.
    add("energy_port", g.box("energy_port", (-7, FLOOR_Y, 185), (7, FLOOR_Y + 0.5, 199), faces=SIDES + ("up",), bone=F))
    add("item_port", g.box("item_port", (-39, FLOOR_Y, 185), (-25, FLOOR_Y + 0.5, 199), faces=SIDES + ("up",), bone=F))
    add("item_port_side", g.box("item_port_side", (-38, 2, 200), (-26, 12, 200.5), faces=("south", "east", "west", "up", "down"), bone=F))
    g.layout()
    tex = Tex(g.tex_w, g.tex_h)
    glow = Tex(g.tex_w, g.tex_h)
    paint(g, tex, glow, parts)
    return g, tex, glow, parts


def paint(g: GeoModel, tex: Tex, glow: Tex, parts: dict) -> None:
    """The bay's surfaces, in model space (tools/gb/materials.py): a sealed grey floor in plates, violet bay lines,
    hazard bands round its edge and a violet keep-out band round each plinth; dark violet ring housings, riveted, on
    gunmetal plinths striped in hazard paint; copper-wound rotors, their inner faces an emitter strip that glows violet,
    four bright nodes round each; dark cradles with yellow cheeks and rubber saddles; steel conduits; the hall's desk
    with a violet screen; a lead cask with a trefoil."""
    S = M.Surfacer(g, seed=61)
    names = lambda key: [b.name for b in parts.get(key, [])]                       # noqa: E731
    epoxy = M.painted(M.GREY_PAINT_RAMP, tone=0.6, wear=0.1, grime=0.02)
    S.paint(tex, "floor", epoxy, only=("up",))
    S.paint(tex, "floor", M.concrete(tone=0.58), only=SIDES)
    line = M.painted(VIOLET_RAMP, tone=0.66, wear=0.0, grime=0.0, edge_light=0.0, edge_dark=0.0)
    band = M.stripes(width=3, wear=0.35)
    keep_out = M.Striped(ramp=VIOLET_RAMP, tone=0.62, dither=0.4, wear=0.25, under=M.STEEL_RAMP[2:7], width=2,
                         other=M.painted(M.BLACK_RAMP, tone=0.5, wear=0.25))

    def floor(f, img):
        M.seams(f, img, every=16, axis="u", offset=8, light=False)                  # the floor's plates
        joint = np.abs(((f.Z - 0.5) + 8) % 16 - 8) < 0.25
        M.darken(img, joint, 0.7)
        bay = (np.abs(np.abs(f.X) - 22) < 0.75) & (f.Z > -2) & (f.Z < 194)
        for z in RING_Z:                                                           # broken where the keep-out bands cross
            bay &= np.abs(f.Z - z) > 10
        M.paste(img, S.material_on(f, line), bay & ~M.scuffed(f, 0.3, seed=7))
        for z in RING_Z:
            ring = np.maximum(np.abs(f.X) / 21.0, np.abs(f.Z - z) / 9.0)
            M.paste(img, S.material_on(f, keep_out), (ring > 0.86) & (ring <= 1.0))
        M.paste(img, S.material_on(f, band), (f.Z < -5) | (f.Z > 197) | (np.abs(f.X) > 53))
    S.overlay(tex, "floor", floor, only=("up",))

    gunmetal = M.painted(M.ARMOUR_RAMP, tone=0.5, wear=0.25, rust=0.05)
    S.paint(tex, names("plinth"), gunmetal)
    S.paint(tex, names("plinth"), M.stripes(width=2, wear=0.35, under=M.STEEL_RAMP[2:7]), only=("north", "south"))
    S.overlay(tex, names("plinth"), lambda f, img: M.rivets(f, img, every=6, inset=1), only=("up",))

    # The housings: dark violet, a steel lip round their front and back, rivets along it.
    housing = M.painted(VIOLET_RAMP, tone=0.4, wear=0.28, rust=0.04)
    S.paint(tex, names("housing"), housing)
    steel = M.steel(tone=0.55, brushed=0.06)

    def lip(f, img):
        if f.d in ("north", "south"):
            outer = f.border["top"]                      # the plank's own top: the ring's outside (planks are turned whole)
            M.paste(img, S.material_on(f, steel), outer < 1)                        # the outer rim of the face
            M.lighten(img, (outer >= 1) & (outer < 2) & (np.floor(f.X * 0.5 + f.Y * 0.5) % 3 == 0), 1.3, 12)
    S.overlay(tex, names("housing"), lip)
    S.paint(tex, names("housing"), M.painted(M.BLACK_RAMP, tone=0.45, wear=0.1), only=("down",))

    # The rotors: copper windings on their faces (each turn a texel), the inner face an emitter strip.
    copper = M.Material(ramp=M.COPPER_RAMP, tone=0.55, mottle=0.03, grain=0.03, dither=0.4, edge_light=0.2, edge_dark=0.15,
                        ao=0.3, grime=0.02, dust=0.0, brushed=0.04)
    rotors = sum((names(f"rotor{i}") for i in range(len(RING_Z))), [])
    S.paint(tex, rotors, copper, only=("north", "south"))

    def turns(f, img):
        r = np.hypot(f.X, f.Y - AXIS_Y)
        M.darken(img, (np.floor(r * 1.0) % 2) == 0, 0.78)
    S.overlay(tex, rotors, turns, only=("north", "south"))
    S.paint(tex, rotors, M.painted(M.BLACK_RAMP, tone=0.4, wear=0.05), only=("down",))

    def emitter(t: Tex, r, d, node: bool):
        x, y, w, h = r
        if d != "down" or w < 2 or h < 2:
            return
        mid = y + h // 2
        t.rect(x, mid - 1, w, 2, P.SINGULARITY[3])                                 # the strip along the ring
        t.hline(x, mid - 1, w, P.SINGULARITY[4])
        if node:
            t.rect(x + w // 2 - 2, y + 1, 4, h - 2, P.ANTIMATTER[5])
            t.rect(x + w // 2 - 1, y + 2, 2, h - 4, P.ANTIMATTER[6])
    for i in range(len(RING_Z)):
        for k, b in enumerate(parts[f"rotor{i}"]):
            node = k % 4 == 0
            g.paint_all(tex, b, lambda t, r, d, node=node: emitter(t, r, d, node))
            g.paint_all(glow, b, lambda t, r, d, node=node: emitter(t, r, d, node))

    # The fields: nothing by daylight but their glow, brightest at the inside.
    def field(t: Tex, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, CLEAR)

    def field_glow(t: Tex, r, d):
        x, y, w, h = r
        for yy in range(h):
            share = yy / max(1, h - 1)                                             # 0 at the outer edge (up), 1 inside
            c = P.SINGULARITY[2] if share < 0.34 else P.SINGULARITY[3] if share < 0.67 else P.ANTIMATTER[4]
            for xx in range(w):
                if (xx + yy) % 3 or share > 0.6:
                    t.px(x + xx, y + yy, c)
    for b in parts["field"]:
        g.paint_all(tex, b, field)
        g.paint_all(glow, b, field_glow)

    def lamp(t: Tex, r, d):
        x, y, w, h = r
        if d != "north":
            t.rect(x, y, w, h, P.BLACK_PAINT[2])
            return
        t.rect(x, y, w, h, P.BLACK_PAINT[1])
        for k, c in enumerate((P.INDICATOR_GREEN.light, P.SINGULARITY[5], P.ANTIMATTER[5], P.NUCLEAR_AMBER.light)):
            if 1 + k * 2 < w:
                t.px(x + 1 + k * 2, y + h // 2, c)
    for b in parts["lamp"]:
        g.paint_all(tex, b, lamp)
        g.paint_all(glow, b, lambda t, r, d: lamp(t, r, d) if d == "north" else None)

    cradles = names("cradle")
    S.paint(tex, [n for n in cradles if "pad" not in n], M.painted(M.ARMOUR_RAMP, tone=0.6, wear=0.3, rust=0.1))
    S.paint(tex, [n for n in cradles if "pad" in n], M.painted(M.YELLOW_PAINT_RAMP, tone=0.58, wear=0.45))
    S.paint(tex, [n for n in cradles if n.endswith("mid")], M.rubber(tone=0.5), only=("up",))

    S.paint(tex, names("conduit"), M.steel(tone=0.42, brushed=0.04, rust=0.06))

    def tray(f, img):                                                               # the conduit's lid in sections
        M.seams(f, img, every=8, axis="u" if f.box.to[0] - f.box.frm[0] > f.box.to[2] - f.box.frm[2] else "v", light=False)
    S.overlay(tex, names("conduit"), tray, only=("up",))

    S.paint(tex, "desk", M.painted(M.OLIVE_RAMP, tone=0.55, wear=0.3))
    S.paint(tex, "screen", M.painted(M.ARMOUR_RAMP, tone=0.5, wear=0.2))

    def desk(t: Tex, r, d):
        x, y, w, h = r
        if d == "north" and w > 6:
            t.rect(x + 1, y + 1, w - 2, min(3, h - 2), P.BLACK_PAINT.deep)
            for xx in range(x + 1, x + w - 1):
                t.px(xx, y + 2, P.INDICATOR_GREEN[4] if xx % 3 else P.SINGULARITY[5])
    g.paint_all(tex, "desk", desk)

    def screen(t: Tex, r, d):
        x, y, w, h = r
        if d == "north" and w > 4:
            t.rect(x + 1, y + 1, w - 2, max(1, h - 2), P.BLACK_PAINT.deep)
            for xx in range(x + 1, x + w - 1):
                t.px(xx, y + 1 + (xx % 2), P.ANTIMATTER[5])
    g.paint_all(tex, "screen", screen)
    g.paint_all(glow, "screen", screen)

    lead = M.painted(M.GALVANIZED_RAMP, tone=0.38, wear=0.2, rust=0.0)
    S.paint(tex, ["cask", "cask_lid"], lead)
    S.paint(tex, "cask_lid", M.stripes(width=2, wear=0.3, under=M.STEEL_RAMP[2:7]), only=SIDES)

    def trefoil(t: Tex, r, d):
        x, y, w, h = r
        if d in ("east", "south") and w >= 8 and h >= 8:
            t.outline(x + w // 2 - 3, y + h // 2 - 3, 6, 6, P.HAZARD_BLACK.base)
            MK.trefoil(t, x + w // 2 - 2, y + h // 2 - 2)                       # a 4x4 placard in a black frame
    g.paint_all(tex, "cask", trefoil)

    # The ports: dark steel hatches in the floor, a painted border (red for power, hazard for items) and a glyph.
    plate = M.steel(tone=0.4, brushed=0.04, rust=0.08)
    for name, border in (("energy_port", M.painted(M.RED_PAINT_RAMP, tone=0.58, wear=0.3)), ("item_port", M.stripes(width=2, wear=0.3))):
        S.paint(tex, name, plate)

        def rim(f, img, border=border):
            ring = np.minimum(np.minimum(f.border["top"], f.border["bottom"]), np.minimum(f.border["left"], f.border["right"]))
            M.paste(img, S.material_on(f, border), ring < 2)
            M.darken(img, ring == 2, 0.55)
        S.overlay(tex, name, rim, only=("up",))

    def bolt(t: Tex, r, d):                                                         # the Energy Port's lightning bolt
        if d == "up":
            cx, cy = r[0] + r[2] // 2, r[1] + r[3] // 2
            for dx, dy in ((1, -3), (0, -2), (-1, -1), (0, -1), (1, -1), (0, 0), (-1, 1), (-2, 2)):
                t.px(cx + dx, cy + dy, P.HAZARD_YELLOW[4])

    def grate(t: Tex, r, d):                                                        # the Item Port's grated hatch
        if d == "up":
            cx, cy = r[0] + r[2] // 2, r[1] + r[3] // 2
            for yy in range(cy - 3, cy + 4, 2):
                t.hline(cx - 3, yy, 7, P.STEEL[5])
                t.hline(cx - 3, yy + 1, 7, P.BLACK_PAINT.deep)
    g.paint_all(tex, "energy_port", bolt)
    g.paint_all(tex, "item_port", grate)
    L._paint_side_port(S, g, tex, "item_port_side", "south")


def animations() -> list[Animation]:
    """Working (4 s, looped while it runs): the rotors turn, the middle one the other way. Idle: they rest."""
    work = Animation("working", 4.0, loop=True)
    for i in range(len(RING_Z)):
        sign = -1 if i == 1 else 1
        work.rotate(f"rotor{i}", {0: (0, 0, 0), 1: (0, 0, sign * 90), 2: (0, 0, sign * 180), 3: (0, 0, sign * 270), 4: (0, 0, sign * 360)})
    idle = Animation("idle", 1.0, loop="hold_on_last_frame")
    for i in range(len(RING_Z)):
        idle.rotate(f"rotor{i}", {0: (0, 0, 0), 1: (0, 0, 0)})
    return [work, idle]


def spawner_marks(ox: int, oy: int, w: int, h: int):
    """The spawner sheet's plan: the three rings across the bay, the missile's bay down its middle."""
    B = MT.BLUEPRINT
    marks = [(ox + w // 2, y, B[4]) for y in range(oy + 2, oy + h - 1)]
    for row in (3, 6, 9):
        y = oy + 1 + row * (h - 2) // 13
        marks += [(ox + w // 2 - 1, y, P.ANTIMATTER[5]), (ox + w // 2, y, P.ANTIMATTER[6]), (ox + w // 2 + 1, y, P.ANTIMATTER[5])]
    return marks


def main() -> None:
    ST.controller(NAME, ST.controller_front(placard), ST.controller_top(), ST.casing())
    model, texture, glowmask, _ = build()
    for p in model.save(texture, glowmask):
        print(p)
    print(save_animations(NAME, animations()))
    MT.structure_spawner(f"{NAME}_spawner", A.plan(5, 6, spawner_marks))
    print("special weapons assembler written")


if __name__ == "__main__":
    main()
