"""The missile assemblers (DESIGN.md §10.4): one multiblock per class of missile below the Strategic Assembly Hall's.

Each formed model is drawn by its controller (GeckoLib); the missile it builds is not part of it: the game draws the
warhead's own missile (tools/assets/missiles.py) section by section at the model's "missile" bone, lying on the cradles
nose to the front, or standing on the Strategic Missile Assembler's stand (MissileCarrier). The cradles' saddles are
flat: the game sets each missile on them by its own girth (CRADLES below; MissileAssemblerBlockEntity has the same
figures).

- Light Missile Assembler (3x6, 4 high): a concrete stand with two cradles under an A-frame hoist: an olive-drab
  A-frame at each end carrying a yellow I-beam, a chain hoist on a trolley running along it.
- Medium Missile Assembler (5x7, 5 high): a bay with two cradles and a yellow slewing jib crane on a grey post at the
  side, swinging sections over from a cantilever rack; a tool cart.
- Heavy Missile Assembler (5x9, 6 high): three cradles between two rails, an orange portal gantry crane (splayed legs on
  bogies, twin box girders, a trolley with its hoist, the driver's cab) running the length of the bay, and a flatbed
  cart of crated sections beside the cradles.
- Strategic Missile Assembler (5x5, 13 high): an integration tower: four white lattice columns, a yellow crane beam
  across the top with its hoist, a stand in the middle where the missile is stacked stage on stage, and three levels of
  work platforms that fold down round it while there is a missile on the stand.
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))

import numpy as np  # noqa: E402

from gb import materials as M  # noqa: E402
from gb import palette as P  # noqa: E402
from gb.geo import Animation, GeoModel, rest_pose, save_animations  # noqa: E402
from gb.tex import Tex  # noqa: E402
import launch as L  # noqa: E402
import multiblock_tools as MT  # noqa: E402
import structures as ST  # noqa: E402

SIDES = ("north", "south", "east", "west")
ALL = SIDES + ("up", "down")
NO_DOWN = SIDES + ("up",)
DECK_Y = 14
YELLOW, GREEN, RED = P.HAZARD_YELLOW, P.INDICATOR_GREEN, P.WARNING_RED

#: Where each model's missile lies (MissileAssemblerBlockEntity's cradles carry the same figures): the saddles' tops,
#: the missile bone's height (the axis as modelled) and the tail's z, and the heights up the missile the cradles hold it.
CRADLES = {
    "light_missile_assembler": dict(top=20, axis=25, tail=80, at=(20, 36)),
    "medium_missile_assembler": dict(top=21, axis=27, tail=96, at=(25, 55)),
    "heavy_missile_assembler": dict(top=22, axis=30, tail=128, at=(25, 55, 85)),
}
#: The Strategic Missile Assembler's stand: the standing missile's tail.
STAND_Y, STAND_Z = 20, 32


# --- shared parts ---------------------------------------------------------------------------------------------------------
def strut(g: GeoModel, name: str, a, b, w: float, bone: str, faces=ALL, d: float | None = None):
    """A bar {w} by {d} from point a to point b (they differ in y and in x or in z), built upright at a and leaned over."""
    ax, ay, az = a
    bx, by, bz = b
    d = w if d is None else d
    length = math.dist(a, b)
    if abs(bz - az) < 1e-6:
        rot = {"origin": tuple(a), "axis": "z", "angle": -math.degrees(math.atan2(bx - ax, by - ay))}
    else:
        rot = {"origin": tuple(a), "axis": "x", "angle": math.degrees(math.atan2(bz - az, by - ay))}
    return g.box(name, (ax - w / 2, ay, az - d / 2), (ax + w / 2, ay + length, az + d / 2), faces=faces, rotation=rot, bone=bone)


def deck(g: GeoModel, x0: float, x1: float, z0: float, z1: float) -> None:
    g.box("deck", (x0, 0, z0), (x1, DECK_Y, z1), faces=NO_DOWN, bone="frame")


def console(g: GeoModel) -> None:
    """The controller's console on the front half of its cell (PartCut.CONSOLE)."""
    g.box("console", (-5, DECK_Y, -7), (5, 22, -2), faces=NO_DOWN, bone="frame")


def housing(g: GeoModel, cx: float, cz: float, edge: str = "back") -> None:
    """The Energy Port's housing against the structure's outer edge, so its banded box shows from outside where the port
    is: along the back of its cell (PartCut.HOUSING_NORTH) or, a port on the right-hand edge, along its east side
    (PartCut.HOUSING_WEST)."""
    if edge == "east":
        g.box("housing", (cx + 3, DECK_Y, cz - 5), (cx + 8, 19, cz + 5), faces=NO_DOWN, bone="frame")
    else:
        g.box("housing", (cx - 5, DECK_Y, cz + 3), (cx + 5, 19, cz + 8), faces=NO_DOWN, bone="frame")


def hatch(g: GeoModel, cx: float, cz: float) -> None:
    """The Item Port: a hatch in the deck over its block."""
    g.box("item_port", (cx - 7, DECK_Y, cz - 7), (cx + 7, DECK_Y + 0.5, cz + 7), faces=NO_DOWN, bone="frame")


def cradle(g: GeoModel, i: int, zc: float, inner: float, base_top: float, top: float) -> list[str]:
    """A cradle across the bay: a steel base, a rubber saddle {inner} either side of the axis with its top at {top}, and
    a chock each side of it standing a little higher, to keep the missile from rolling."""
    names = [f"cradle{i}_base", f"cradle{i}_saddle", f"cradle{i}_cheek_w", f"cradle{i}_cheek_e"]
    half = inner + 3
    g.box(names[0], (-half, DECK_Y, zc - 3), (half, base_top, zc + 3), faces=NO_DOWN, bone="frame")
    g.box(names[1], (-inner, base_top, zc - 2), (inner, top, zc + 2), faces=("north", "south", "up"), bone="frame")
    g.box(names[2], (-inner - 2, base_top, zc - 2.5), (-inner, top + 2.5, zc + 2.5), faces=NO_DOWN, bone="frame")
    g.box(names[3], (inner, base_top, zc - 2.5), (inner + 2, top + 2.5, zc + 2.5), faces=NO_DOWN, bone="frame")
    return names


def paint_deck(S: M.Surfacer, tex: Tex, bounds, bay_half: float, seed: int, bay_z=None) -> None:
    """A sealed concrete deck cut into slabs at the block lines, a hazard band round its edge and worn yellow lines
    down the missile's bay."""
    x0, x1, z0, z1 = bounds
    S.paint(tex, "deck", M.concrete(tone=0.64, mottle=0.028, grain=0.035))
    S.paint(tex, "deck", M.concrete(tone=0.58), only=SIDES)
    line = M.painted(M.YELLOW_PAINT_RAMP, tone=0.62, wear=0.0, grime=0.0, edge_light=0.0, edge_dark=0.0)
    band = M.stripes(width=3, wear=0.4)
    bz0, bz1 = bay_z if bay_z is not None else (z0 + 4, z1 - 4)

    def floor(f, img):
        cut = lambda v: np.abs(((v - 8) + 8) % 16 - 8) < 0.25         # saw cuts on the block lines
        M.darken(img, cut(f.X) | cut(f.Z), 0.64)
        bay = (np.abs(np.abs(f.X) - bay_half) < 0.75) & (f.Z > bz0) & (f.Z < bz1)
        M.paste(img, S.material_on(f, line), bay & ~M.scuffed(f, 0.35, seed=seed))
        edge = (f.X < x0 + 2.5) | (f.X > x1 - 2.5) | (f.Z < z0 + 2.5) | (f.Z > z1 - 2.5)
        M.paste(img, S.material_on(f, band), edge)
    S.overlay(tex, "deck", floor, only=("up",))


def paint_ports(S: M.Surfacer, g: GeoModel, tex: Tex, plug: str = "south") -> None:
    """The console, the Energy Port's housing (its socket on its outer face: "south" for a back edge, "west" for the
    +x edge, as GeckoLib models name their faces) and the Item Port's grated hatch (hazard-bordered)."""
    L._console(S, g, tex, "console")
    L._power_housing(S, g, tex, "housing", plug=plug)
    S.paint(tex, "item_port", M.steel(tone=0.4, brushed=0.04, rust=0.08))
    border = M.stripes(width=2, wear=0.3)

    def rim(f, img):
        ring = np.minimum(np.minimum(f.border["top"], f.border["bottom"]), np.minimum(f.border["left"], f.border["right"]))
        M.paste(img, S.material_on(f, border), ring < 2)
        M.darken(img, ring == 2, 0.55)
    S.overlay(tex, "item_port", rim, only=("up",))

    def grate(t: Tex, r, d):
        if d == "up":
            cx, cy = r[0] + r[2] // 2, r[1] + r[3] // 2
            for yy in range(cy - 3, cy + 4, 2):
                t.hline(cx - 3, yy, 7, P.STEEL[5])
                t.hline(cx - 3, yy + 1, 7, P.BLACK_PAINT.deep)
    g.paint_all(tex, "item_port", grate)


def paint_cradles(S: M.Surfacer, tex: Tex, names: list[str]) -> None:
    """Dark steel bases, yellow cheeks, black rubber saddles."""
    S.paint(tex, [n for n in names if n.endswith("_base")], M.painted(M.ARMOUR_RAMP, tone=0.58, wear=0.3, rust=0.1))
    S.paint(tex, [n for n in names if "_cheek" in n], M.painted(M.YELLOW_PAINT_RAMP, tone=0.58, wear=0.45))
    S.paint(tex, [n for n in names if n.endswith("_saddle")], M.rubber(tone=0.5))


def hook_parts(g: GeoModel, prefix: str, x: float, z: float, top: float, cable_to: float, block_h: float, block_w: float,
               bone_cable: str, bone_hook: str, falls: int = 1) -> tuple[list[str], list[str]]:
    """A hoist's rope (one fall or two) from {top} down to {cable_to}, then the hook block and its hook."""
    ropes = []
    xs = (x,) if falls == 1 else (x - 1.5, x + 1.5)
    for i, xx in enumerate(xs):
        ropes.append(f"{prefix}_rope{i}")
        g.box(ropes[-1], (xx - 0.5, cable_to, z - 0.5), (xx + 0.5, top, z + 0.5), faces=SIDES, bone=bone_cable)
    blocks = [f"{prefix}_block", f"{prefix}_hook"]
    g.box(blocks[0], (x - block_w / 2, cable_to - block_h, z - 1.5), (x + block_w / 2, cable_to, z + 1.5), faces=ALL, bone=bone_hook)
    g.box(blocks[1], (x - 0.7, cable_to - block_h - 3.5, z - 0.7), (x + 0.7, cable_to - block_h, z + 0.7), faces=SIDES + ("down",),
          bone=bone_hook)
    return ropes, blocks


def paint_hook(S: M.Surfacer, tex: Tex, ropes: list[str], blocks: list[str]) -> None:
    S.paint(tex, ropes, M.Material(ramp=M.BLACK_RAMP, tone=0.45, dither=0.3, edge_light=0.25))
    S.paint(tex, blocks[0], M.painted(M.YELLOW_PAINT_RAMP, tone=0.6, wear=0.35))
    S.paint(tex, blocks[0], M.stripes(width=2, wear=0.4, under=M.STEEL_RAMP[2:7]), only=SIDES)
    S.paint(tex, blocks[1], M.steel(tone=0.35))


def drop(anim: Animation, hook: str, rope: str, rest_len: float, keys: dict[float, float]) -> None:
    """Lowers a hook by keys[t] pixels (its rope paying out in step)."""
    anim.move(hook, {t: (0, -v, 0) for t, v in keys.items()}, easing="easeInOutSine")
    anim.scale(rope, {t: (1, (rest_len + v) / rest_len, 1) for t, v in keys.items()}, easing="easeInOutSine")


# --- Light Missile Assembler -----------------------------------------------------------------------------------------------
LIGHT_PARK = 8                                   # the trolley's rest, over the front of the stand


def light() -> None:
    c = CRADLES["light_missile_assembler"]
    g = GeoModel("light_missile_assembler", tex_size=(512, 512))
    F = "frame"
    g.bone(F)
    g.bone("missile", pivot=(0, c["axis"], c["tail"]), parent=F)
    g.bone("trolley", pivot=(0, 56, LIGHT_PARK), parent=F)
    g.bone("chain", pivot=(0, 43, LIGHT_PARK), parent="trolley")
    g.bone("hook", pivot=(0, 39, LIGHT_PARK), parent="trolley")
    deck(g, -24, 24, -8, 88)
    console(g)
    housing(g, 16, 64, edge="east")                       # on the deck's right-hand edge
    hatch(g, -16, 64)
    cradles = []
    for i, h in enumerate(c["at"]):
        cradles += cradle(g, i, c["tail"] - h, 5, 16, c["top"])
    # The A-frames: two legs a side leaning in from their foot plates to a head block under the beam, a tie across.
    frames, feet, heads = [], [], []
    for k, z0 in enumerate((0, 80)):
        for s in (-1, 1):
            n = f"aframe{k}_leg_{'w' if s < 0 else 'e'}"
            strut(g, n, (18 * s, 15, z0), (2.2 * s, 53, z0), 3, F, faces=SIDES)
            frames.append(n)
            feet.append(f"aframe{k}_foot_{'w' if s < 0 else 'e'}")
            g.box(feet[-1], (18 * s - 3, DECK_Y, z0 - 3), (18 * s + 3, 15, z0 + 3), faces=NO_DOWN, bone=F)
        heads.append(f"aframe{k}_head")
        g.box(heads[-1], (-4, 51, z0 - 2.5), (4, 56, z0 + 2.5), faces=SIDES + ("down",), bone=F)
        frames.append(f"aframe{k}_tie")
        g.box(frames[-1], (-11, 32.5, z0 - 1), (11, 34.5, z0 + 1), faces=ALL, bone=F)
    # The hoist beam: an I-beam the stand's length, a stop at each end.
    beam = ["beam_bottom", "beam_web", "beam_top"]
    g.box("beam_bottom", (-4, 56, -6), (4, 57.5, 86), faces=ALL, bone=F)
    g.box("beam_web", (-1, 57.5, -6), (1, 62.5, 86), faces=("east", "west", "north", "south"), bone=F)
    g.box("beam_top", (-4, 62.5, -6), (4, 64, 86), faces=ALL, bone=F)
    stops = ["stop_front", "stop_back"]
    g.box("stop_front", (-3, 52, -6), (3, 56, -4), faces=SIDES + ("down",), bone=F)
    g.box("stop_back", (-3, 52, 84), (3, 56, 86), faces=SIDES + ("down",), bone=F)
    # The trolley on the bottom flange (its wheels either side of the web) and the chain hoist under it.
    T = LIGHT_PARK
    trolley = ["trolley_body", "trolley_side_w", "trolley_side_e", "hoist"]
    g.box("trolley_body", (-4, 51, T - 3.5), (4, 55.5, T + 3.5), faces=ALL, bone="trolley")
    for s, n in ((-1, "trolley_side_w"), (1, "trolley_side_e")):
        g.box(n, (4.5 * s - 0.5, 51, T - 4), (4.5 * s + 0.5, 60, T + 4), faces=ALL, bone="trolley")
    wheels = []
    for s in (-1, 1):
        for dz in (-2.5, 2.5):
            wheels.append(f"wheel_{'w' if s < 0 else 'e'}{'f' if dz < 0 else 'b'}")
            x0, x1 = (1.5, 4.0) if s > 0 else (-4.0, -1.5)
            g.box(wheels[-1], (x0, 57.5, T + dz - 1.2), (x1, 60, T + dz + 1.2), faces=SIDES + ("up",), bone="trolley")
    g.box("hoist", (-3, 43, T - 3), (3, 51, T + 3), faces=SIDES + ("down",), bone="trolley")
    ropes, blocks = hook_parts(g, "chain", 0, T, 43, 39, 3, 4, "chain", "hook")
    # A red tool chest and a pallet of crated parts beside the bay.
    props = ["chest", "chest_lid", "pallet", "crate"]
    g.box("chest", (-22, DECK_Y, 13), (-12, 24, 23), faces=NO_DOWN, bone=F)
    g.box("chest_lid", (-22.5, 24, 12.5), (-11.5, 25.5, 23.5), faces=ALL, bone=F)
    g.box("pallet", (11, DECK_Y, 24), (22, 15.5, 38), faces=NO_DOWN, bone=F)
    g.box("crate", (12, 15.5, 25), (21, 22, 37), faces=NO_DOWN, bone=F)
    g.layout()
    tex = Tex(g.tex_w, g.tex_h)
    S = M.Surfacer(g, seed=71)
    paint_deck(S, tex, (-24, 24, -8, 88), 10, 71, bay_z=(10, 86))
    paint_ports(S, g, tex, plug="west")
    paint_cradles(S, tex, cradles)
    olive = M.painted(M.OLIVE_RAMP, tone=0.55, wear=0.3, rust=0.08)
    S.paint(tex, frames + heads, olive)
    S.paint(tex, feet, M.painted(M.ARMOUR_RAMP, tone=0.5, wear=0.3, rust=0.1))
    S.overlay(tex, feet, lambda f, img: M.rivets(f, img, every=3, inset=1), only=("up",))
    yellow = M.painted(M.YELLOW_PAINT_RAMP, tone=0.6, wear=0.3, rust=0.1, streaks=0.05)
    S.paint(tex, beam, yellow)
    S.overlay(tex, ["beam_bottom", "beam_top"], lambda f, img: M.rivets(f, img, every=8, inset=1), only=("up", "down"))
    S.paint(tex, stops, M.stripes(width=2, wear=0.3))
    S.paint(tex, trolley[:3], M.painted(M.GREY_PAINT_RAMP, tone=0.45, wear=0.3, rust=0.1))
    S.paint(tex, wheels, M.steel(tone=0.5))
    S.paint(tex, "hoist", M.painted(M.YELLOW_PAINT_RAMP, tone=0.55, wear=0.4))
    paint_hook(S, tex, ropes, blocks)
    S.paint(tex, ["chest", "chest_lid"], M.painted(M.RED_PAINT_RAMP, tone=0.55, wear=0.3))

    def drawers(t: Tex, r, d):
        x, y, w, h = r
        if d == "north" and h >= 8:
            for yy in range(y + 2, y + h - 1, 3):
                t.hline(x + 1, yy, w - 2, P.BLACK_PAINT[1])
                t.hline(x + w // 2 - 1, yy + 1, 2, P.STEEL[5])
    g.paint_all(tex, "chest", drawers)
    S.paint(tex, "pallet", M.wood(tone=0.55))
    S.overlay(tex, "pallet", lambda f, img: M.seams(f, img, every=3, axis="u", light=False), only=("up",))
    S.paint(tex, "crate", M.painted(M.OLIVE_RAMP, tone=0.5, wear=0.35, under=M.WOOD_RAMP[2:6]))
    g.save(tex)
    # Working (10 s): the trolley runs back over the cradles in turn, the hook lowering at each, and home again.
    work = Animation("working", 10.0, loop=True)
    a, b = c["tail"] - c["at"][0] - T, c["tail"] - c["at"][1] - T
    work.move("trolley", {0: (0, 0, 0), 2.2: (0, 0, a), 4.0: (0, 0, a), 6.0: (0, 0, b), 7.8: (0, 0, b), 9.6: (0, 0, 0), 10: (0, 0, 0)},
              easing="easeInOutSine")
    drop(work, "hook", "chain_rope0", 4, {0: 0, 2.4: 0, 3.0: 3, 3.6: 0, 6.2: 0, 6.8: 3, 7.4: 0, 10: 0})
    print(save_animations("light_missile_assembler", [work, rest_pose([work])]))


# --- Medium Missile Assembler ----------------------------------------------------------------------------------------------
JIB_X, JIB_Z, JIB_Y = 32, 48, 75                 # the slewing ring on top of the post


def medium() -> None:
    c = CRADLES["medium_missile_assembler"]
    g = GeoModel("medium_missile_assembler", tex_size=(512, 512))
    F = "frame"
    g.bone(F)
    g.bone("missile", pivot=(0, c["axis"], c["tail"]), parent=F)
    g.bone("jib", pivot=(JIB_X, JIB_Y, JIB_Z), parent=F)
    g.bone("jib_trolley", pivot=(0, JIB_Y, JIB_Z), parent="jib")
    g.bone("jib_rope", pivot=(0, 69, JIB_Z), parent="jib_trolley")
    g.bone("jib_hook", pivot=(0, 50, JIB_Z), parent="jib_trolley")
    deck(g, -40, 40, -8, 104)
    console(g)
    housing(g, 16, 96)
    hatch(g, -16, 96)
    cradles = []
    for i, h in enumerate(c["at"]):
        cradles += cradle(g, i, c["tail"] - h, 6, 16, c["top"])
    # The post: a base plate, a column of box section, the slewing ring at its head.
    post = ["post_base", "post", "slew_ring"]
    g.box("post_base", (JIB_X - 7, DECK_Y, JIB_Z - 7), (JIB_X + 7, 16, JIB_Z + 7), faces=NO_DOWN, bone=F)
    g.box("post", (JIB_X - 4, 16, JIB_Z - 4), (JIB_X + 4, 72, JIB_Z + 4), faces=SIDES, bone=F)
    g.box("slew_ring", (JIB_X - 5, 72, JIB_Z - 5), (JIB_X + 5, JIB_Y, JIB_Z + 5), faces=ALL, bone=F)
    # The jib: an I-beam out over the bay, a mast over the ring with a tie rod out to the jib's end, a counterweight.
    jib = ["jib_bottom", "jib_web", "jib_top", "mast", "counter_arm"]
    g.box("jib_bottom", (-28, JIB_Y, JIB_Z - 2.5), (JIB_X + 2, JIB_Y + 1.5, JIB_Z + 2.5), faces=ALL, bone="jib")
    g.box("jib_web", (-28, JIB_Y + 1.5, JIB_Z - 0.75), (JIB_X + 2, JIB_Y + 5.5, JIB_Z + 0.75), faces=("east", "west", "north", "south"), bone="jib")
    g.box("jib_top", (-28, JIB_Y + 5.5, JIB_Z - 2.5), (JIB_X + 2, JIB_Y + 7, JIB_Z + 2.5), faces=ALL, bone="jib")
    g.box("mast", (JIB_X - 2, JIB_Y + 7, JIB_Z - 2), (JIB_X + 2, JIB_Y + 19, JIB_Z + 2), faces=SIDES + ("up",), bone="jib")
    g.box("counter_arm", (JIB_X + 2, JIB_Y, JIB_Z - 2.5), (JIB_X + 9, JIB_Y + 4, JIB_Z + 2.5), faces=ALL, bone="jib")
    ties = ["tie_out", "tie_back"]
    strut(g, "tie_out", (JIB_X, JIB_Y + 18, JIB_Z), (-26, JIB_Y + 7.2, JIB_Z), 0.8, "jib", faces=SIDES)
    strut(g, "tie_back", (JIB_X, JIB_Y + 18, JIB_Z), (JIB_X + 8, JIB_Y + 4.2, JIB_Z), 0.8, "jib", faces=SIDES)
    g.box("counterweight", (JIB_X + 5, JIB_Y - 5, JIB_Z - 3.5), (JIB_X + 10, JIB_Y + 5, JIB_Z + 3.5), faces=ALL, bone="jib")
    # The trolley under the jib's bottom flange, its hoist and hook (resting over the missile's middle).
    g.box("jib_trolley_body", (-4, 69, JIB_Z - 4), (4, 74.5, JIB_Z + 4), faces=ALL, bone="jib_trolley")
    wheels = []
    for s in (-1, 1):
        wheels.append(f"jib_wheel_{'n' if s < 0 else 's'}")
        z0, z1 = (JIB_Z - 2.5, JIB_Z - 0.8) if s < 0 else (JIB_Z + 0.8, JIB_Z + 2.5)
        g.box(wheels[-1], (-3, JIB_Y + 1.5, z0), (3, JIB_Y + 3, z1), faces=("east", "west", "up"), bone="jib_trolley")
    g.box("jib_hanger_n", (-3, 74.5, JIB_Z - 3.5), (3, JIB_Y + 3, JIB_Z - 2.5), faces=ALL, bone="jib_trolley")
    g.box("jib_hanger_s", (-3, 74.5, JIB_Z + 2.5), (3, JIB_Y + 3, JIB_Z + 3.5), faces=ALL, bone="jib_trolley")
    ropes, blocks = hook_parts(g, "jib", 0, JIB_Z, 69, 50, 5, 5, "jib_rope", "jib_hook", falls=2)
    # The cantilever rack at the front of the post's side: two columns, arms out at two levels, a crated section on each.
    rack = ["rack_post_n", "rack_post_s", "rack_arm_n0", "rack_arm_s0", "rack_arm_n1", "rack_arm_s1"]
    g.box("rack_post_n", (35, DECK_Y, -1), (38, 44, 2), faces=NO_DOWN, bone=F)
    g.box("rack_post_s", (35, DECK_Y, 22), (38, 44, 25), faces=NO_DOWN, bone=F)
    for k, y in enumerate((20, 33)):
        g.box(f"rack_arm_n{k}", (25, y, -0.5), (35, y + 2, 1.5), faces=ALL, bone=F)
        g.box(f"rack_arm_s{k}", (25, y, 22.5), (35, y + 2, 24.5), faces=ALL, bone=F)
    crates = ["rack_crate0", "rack_crate1"]
    g.box("rack_crate0", (26, 22, -2), (34, 30, 27), faces=ALL, bone=F)
    g.box("rack_crate1", (26.5, 35, 1), (33.5, 41, 22), faces=ALL, bone=F)
    # A tool cart on the other side of the bay.
    cart = ["cart", "cart_tray"]
    g.box("cart", (-37, 18, 32), (-27, 26, 58), faces=ALL, bone=F)
    g.box("cart_tray", (-37.5, 26, 31.5), (-26.5, 27.5, 58.5), faces=SIDES + ("up",), bone=F)
    cart_wheels = []
    for i, (x, z) in enumerate(((-36, 34), (-30, 34), (-36, 54), (-30, 54))):
        cart_wheels.append(f"cart_wheel{i}")
        g.box(cart_wheels[-1], (x, DECK_Y, z), (x + 2, 18, z + 2), faces=NO_DOWN, bone=F)
    g.layout()
    tex = Tex(g.tex_w, g.tex_h)
    S = M.Surfacer(g, seed=73)
    paint_deck(S, tex, (-40, 40, -8, 104), 13, 73, bay_z=(4, 102))
    paint_ports(S, g, tex)
    paint_cradles(S, tex, cradles)
    grey = M.painted(M.GREY_PAINT_RAMP, tone=0.52, wear=0.3, rust=0.1)
    S.paint(tex, post, grey)
    S.overlay(tex, "post", lambda f, img: M.rivets(f, img, every=8, inset=1, axis="v"), only=SIDES)
    S.paint(tex, "post_base", M.painted(M.ARMOUR_RAMP, tone=0.5, wear=0.3))
    yellow = M.painted(M.YELLOW_PAINT_RAMP, tone=0.6, wear=0.3, rust=0.1, streaks=0.05)
    S.paint(tex, jib + ["jib_hanger_n", "jib_hanger_s"], yellow)
    S.paint(tex, ["jib_bottom", "jib_top"], M.stripes(width=2, wear=0.35), only=("east", "west"))
    S.paint(tex, ties, M.steel(tone=0.45))
    S.paint(tex, "counterweight", M.concrete(tone=0.55))
    S.overlay(tex, "counterweight", lambda f, img: M.paste(img, S.material_on(f, M.stripes(width=2, wear=0.4)), f.border["top"] < 2), only=SIDES)
    S.paint(tex, "jib_trolley_body", M.painted(M.GREY_PAINT_RAMP, tone=0.42, wear=0.3))
    S.paint(tex, wheels, M.steel(tone=0.5))
    paint_hook(S, tex, ropes, blocks)
    S.paint(tex, rack[:2], M.painted(M.ORANGE_PAINT_RAMP, tone=0.55, wear=0.3, rust=0.08))
    S.paint(tex, rack[2:], M.painted(M.ORANGE_PAINT_RAMP, tone=0.5, wear=0.35, rust=0.1))
    crate = M.painted(M.OLIVE_RAMP, tone=0.5, wear=0.35, under=M.WOOD_RAMP[2:6])
    S.paint(tex, crates, crate)
    S.overlay(tex, crates, lambda f, img: M.seams(f, img, every=6, axis="u", light=False), only=SIDES)

    def stencil(t: Tex, r, d):
        x, y, w, h = r
        if d in ("east", "west") and w > 12 and h >= 5:
            t.hline(x + 3, y + h // 2, 5, YELLOW[4])
            t.hline(x + w - 8, y + h // 2, 3, YELLOW[4])
    for n in crates:
        g.paint_all(tex, n, stencil)
    S.paint(tex, cart, M.painted(M.RED_PAINT_RAMP, tone=0.5, wear=0.3))
    S.paint(tex, "cart_tray", M.steel(tone=0.48), only=("up",))
    S.paint(tex, cart_wheels, M.rubber(tone=0.45))
    g.save(tex)
    # Working (12 s): the jib swings to the rack, takes up a section, swings it over the tail end and lowers it; again
    # for the nose end; and home over the middle. The trolley runs out along the jib to reach (it rests 32 out).
    work = Animation("working", 12.0, loop=True)

    def aim(z: float) -> tuple[float, float]:
        d = math.hypot(JIB_X, z - JIB_Z)
        return math.degrees(math.atan2(z - JIB_Z, JIB_X)), -(d - JIB_X)
    rack_a, rack_t = -90.0, -5.0
    tail_a, tail_t = aim(c["tail"] - 12)
    nose_a, nose_t = aim(c["tail"] - 70)
    work.rotate("jib", {0: (0, 0, 0), 1.4: (0, rack_a, 0), 2.6: (0, rack_a, 0), 4.2: (0, tail_a, 0), 5.4: (0, tail_a, 0),
                        7.0: (0, rack_a, 0), 8.2: (0, rack_a, 0), 9.6: (0, nose_a, 0), 10.8: (0, nose_a, 0), 12: (0, 0, 0)},
                easing="easeInOutSine")
    work.move("jib_trolley", {0: (0, 0, 0), 1.4: (rack_t, 0, 0), 2.6: (rack_t, 0, 0), 4.2: (tail_t, 0, 0), 5.4: (tail_t, 0, 0),
                              7.0: (rack_t, 0, 0), 8.2: (rack_t, 0, 0), 9.6: (nose_t, 0, 0), 10.8: (nose_t, 0, 0), 12: (0, 0, 0)},
              easing="easeInOutSine")
    drop(work, "jib_hook", "jib_rope0", 19, {0: 0, 1.5: 0, 2.0: 9, 2.5: 0, 4.3: 0, 4.8: 5, 5.3: 0, 7.1: 0, 7.6: 3, 8.1: 0, 9.7: 0, 10.2: 5,
                                               10.7: 0, 12: 0})
    work.scale("jib_rope1", {t: v for t, v in work.bones["jib_rope0"]["scale"].items()}, easing="easeInOutSine")
    print(save_animations("medium_missile_assembler", [work, rest_pose([work])]))


# --- Heavy Missile Assembler -----------------------------------------------------------------------------------------------
CRANE_Z = 118                                    # the gantry crane parks over the tail, clear of the buffers


def heavy() -> None:
    c = CRADLES["heavy_missile_assembler"]
    g = GeoModel("heavy_missile_assembler", tex_size=(1024, 1024))
    F = "frame"
    g.bone(F)
    g.bone("missile", pivot=(0, c["axis"], c["tail"]), parent=F)
    g.bone("crane", pivot=(0, 16, CRANE_Z), parent=F)
    g.bone("gantry_trolley", pivot=(0, 92, CRANE_Z), parent="crane")
    g.bone("gantry_rope", pivot=(0, 92.5, CRANE_Z), parent="gantry_trolley")
    g.bone("gantry_hook", pivot=(0, 52, CRANE_Z), parent="gantry_trolley")
    deck(g, -40, 40, -8, 136)
    console(g)
    housing(g, 16, 128)
    hatch(g, -16, 128)
    rails = []
    for s in (-1, 1):
        rails.append(f"rail_{'w' if s < 0 else 'e'}")
        g.box(rails[-1], (32 * s - 1.5, DECK_Y, -8), (32 * s + 1.5, 16, 136), faces=NO_DOWN, bone=F)
    buffers = []
    for s in (-1, 1):
        for z0, z1 in ((-7.5, -3.5), (131.5, 135.5)):
            buffers.append(f"buffer_{'w' if s < 0 else 'e'}{0 if z0 < 0 else 1}")
            g.box(buffers[-1], (32 * s - 4, DECK_Y, z0), (32 * s + 4, 21, z1), faces=NO_DOWN, bone=F)
    cradles = []
    for i, h in enumerate(c["at"]):
        cradles += cradle(g, i, c["tail"] - h, 7.5, 17, c["top"])
    # The flatbed cart of crated sections beside the cradles.
    cart = ["flatbed", "flatbed_crate0", "flatbed_crate1"]
    g.box("flatbed", (-28, 17, 38), (-14, 19.5, 92), faces=ALL, bone=F)
    g.box("flatbed_crate0", (-27, 19.5, 41), (-15, 28, 64), faces=NO_DOWN, bone=F)
    g.box("flatbed_crate1", (-26.5, 19.5, 67), (-15.5, 26, 89), faces=NO_DOWN, bone=F)
    cart_wheels = []
    for i, (x, z) in enumerate(((-27.5, 41), (-16.5, 41), (-27.5, 85), (-16.5, 85))):
        cart_wheels.append(f"flatbed_wheel{i}")
        g.box(cart_wheels[-1], (x, DECK_Y, z), (x + 2, 17, z + 4), faces=NO_DOWN, bone=F)
    # The portal gantry crane: on each rail a bogie at each end of a sill beam, two legs splayed up to the bridge's
    # twin box girders, a tie across the legs; the trolley and hoist on the girders, the driver's cab under one end.
    Z = CRANE_Z
    crane_steel, legs, bogies = [], [], []
    for s in (-1, 1):
        side = "w" if s < 0 else "e"
        crane_steel.append(f"sill_{side}")
        g.box(crane_steel[-1], (32 * s - 4, 18, Z - 12), (32 * s + 4, 23, Z + 12), faces=ALL, bone="crane")
        for k, dz in enumerate((-12, 6)):
            bogies.append(f"bogie_{side}{k}")
            g.box(bogies[-1], (32 * s - 3, 16, Z + dz), (32 * s + 3, 18, Z + dz + 6), faces=SIDES, bone="crane")
        for k, (z0, z1) in enumerate(((Z - 9, Z - 3), (Z + 9, Z + 3))):
            legs.append(f"leg_{side}{k}")
            strut(g, legs[-1], (32 * s, 23, z0), (32 * s, 82, z1), 5, "crane", faces=SIDES)
        crane_steel.append(f"leg_tie_{side}")
        g.box(crane_steel[-1], (32 * s - 2, 50, Z - 7), (32 * s + 2, 53, Z + 7), faces=ALL, bone="crane")
    girders = ["girder_n", "girder_s", "end_w", "end_e"]
    g.box("girder_n", (-38, 82, Z - 6), (38, 92, Z - 2), faces=ALL, bone="crane")
    g.box("girder_s", (-38, 82, Z + 2), (38, 92, Z + 6), faces=ALL, bone="crane")
    g.box("end_w", (-38, 84, Z - 2), (-34, 90, Z + 2), faces=("up", "down", "east", "west"), bone="crane")
    g.box("end_e", (34, 84, Z - 2), (38, 90, Z + 2), faces=("up", "down", "east", "west"), bone="crane")
    cab = ["cab", "cab_roof"]
    g.box("cab", (24, 66, Z + 6), (34, 78, Z + 14), faces=ALL, bone="crane")
    g.box("cab_roof", (23.5, 78, Z + 5.5), (34.5, 79.5, Z + 14.5), faces=ALL, bone="crane")
    g.box("cab_hanger", (27, 79.5, Z + 6), (31, 82, Z + 6.5), faces=ALL, bone="crane")
    # The trolley rides on wheels on the girders' tops; its drum on top, two falls of rope down to the hook block.
    trolley = ["gantry_trolley_body", "gantry_drum"]
    g.box("gantry_trolley_body", (-9, 92.5, Z - 7), (9, 98, Z + 7), faces=ALL, bone="gantry_trolley")
    g.box("gantry_drum", (-5, 98, Z - 4), (5, 102, Z + 4), faces=SIDES + ("up",), bone="gantry_trolley")
    twheels = []
    for sx in (-1, 1):
        for zc in (Z - 4, Z + 4):
            twheels.append(f"gantry_wheel{len(twheels)}")
            g.box(twheels[-1], (7 * sx - 1.5, 92, zc - 1), (7 * sx + 1.5, 92.5, zc + 1), faces=SIDES, bone="gantry_trolley")
    ropes, blocks = hook_parts(g, "gantry", 0, Z, 92.5, 52, 7, 9, "gantry_rope", "gantry_hook", falls=2)
    g.layout()
    tex = Tex(g.tex_w, g.tex_h)
    S = M.Surfacer(g, seed=79)
    paint_deck(S, tex, (-40, 40, -8, 136), 24, 79, bay_z=(4, 134))
    paint_ports(S, g, tex)
    paint_cradles(S, tex, cradles)
    S.paint(tex, rails, M.steel(tone=0.5, brushed=0.08, rust=0.12))
    S.overlay(tex, rails, lambda f, img: M.lighten(img, np.abs(f.X - np.sign(f.X) * 32) < 0.75, 1.25, 12), only=("up",))
    S.paint(tex, buffers, M.stripes(width=2, wear=0.35))
    S.paint(tex, "flatbed", M.painted(M.GREY_PAINT_RAMP, tone=0.45, wear=0.3, rust=0.12))
    crate = M.painted(M.OLIVE_RAMP, tone=0.5, wear=0.35, under=M.WOOD_RAMP[2:6])
    S.paint(tex, cart[1:], crate)
    S.overlay(tex, cart[1:], lambda f, img: M.seams(f, img, every=7, axis="u", light=False), only=SIDES + ("up",))
    S.paint(tex, cart_wheels, M.rubber(tone=0.45))
    orange = M.painted(M.ORANGE_PAINT_RAMP, tone=0.58, wear=0.3, rust=0.08, streaks=0.05)
    S.paint(tex, crane_steel + legs + girders + ["cab_hanger"], orange)
    S.overlay(tex, ["girder_n", "girder_s"], lambda f, img: M.rivets(f, img, every=8, inset=1), only=("north", "south"))
    S.paint(tex, ["girder_n", "girder_s"], M.stripes(width=3, wear=0.35), only=("east", "west"))
    S.paint(tex, bogies, M.painted(M.ARMOUR_RAMP, tone=0.5, wear=0.3, rust=0.1))
    S.paint(tex, cab, M.painted(M.GREY_PAINT_RAMP, tone=0.6, wear=0.25))

    def windows(t: Tex, r, d):
        x, y, w, h = r
        if d != "up" and d != "down" and w >= 6 and h >= 8:
            t.rect(x + 1, y + 2, w - 2, h // 2 - 1, P.BLACK_PAINT.deep)
            for xx in range(x + 1, x + w - 1):
                t.px(xx, y + 2, (90, 120, 140, 255))
    g.paint_all(tex, "cab", windows)
    S.paint(tex, trolley, M.painted(M.GREY_PAINT_RAMP, tone=0.42, wear=0.3, rust=0.1))
    S.overlay(tex, "gantry_drum", lambda f, img: M.darken(img, (np.floor(f.X) % 2) == 0, 0.72), only=("north", "south", "up"))
    S.paint(tex, twheels, M.steel(tone=0.5))
    paint_hook(S, tex, ropes, blocks)
    g.save(tex)
    # Working (15 s): the crane runs to the cart, the trolley out over it, the hook takes up a section; the crane runs
    # it to the tail cradle and sets it down; then the same for the nose; and home.
    work = Animation("working", 15.0, loop=True)
    cart_z, tail_z, nose_z = 73 - Z, c["tail"] - c["at"][0] - Z, c["tail"] - c["at"][2] - Z
    work.move("crane", {0: (0, 0, 0), 2.0: (0, 0, cart_z), 4.8: (0, 0, cart_z), 6.0: (0, 0, tail_z), 7.0: (0, 0, tail_z), 8.2: (0, 0, cart_z),
                        10.8: (0, 0, cart_z), 12.0: (0, 0, nose_z), 13.0: (0, 0, nose_z), 15: (0, 0, 0)}, easing="easeInOutSine")
    work.move("gantry_trolley", {0: (0, 0, 0), 2.0: (0, 0, 0), 2.8: (-21, 0, 0), 4.0: (-21, 0, 0), 4.8: (0, 0, 0), 8.2: (0, 0, 0),
                                 8.8: (-21, 0, 0), 10.0: (-21, 0, 0), 10.8: (0, 0, 0), 15: (0, 0, 0)}, easing="easeInOutSine")
    drop(work, "gantry_hook", "gantry_rope0", 40.5, {0: 0, 2.8: 0, 3.4: 13, 4.0: 0, 6.0: 0, 6.5: 3, 7.0: 0, 8.8: 0, 9.4: 13, 10.0: 0,
                                                    12.0: 0, 12.5: 3, 13.0: 0, 15: 0})
    work.scale("gantry_rope1", {t: v for t, v in work.bones["gantry_rope0"]["scale"].items()}, easing="easeInOutSine")
    print(save_animations("heavy_missile_assembler", [work, rest_pose([work])]))


# --- Strategic Missile Assembler -------------------------------------------------------------------------------------------
TOWER_TOP = 192
PLATFORM_Y = (50, 98, 146)


def strategic() -> None:
    g = GeoModel("strategic_missile_assembler", tex_size=(1024, 1024))
    F = "frame"
    g.bone(F)
    g.bone("missile", pivot=(0, STAND_Y, STAND_Z), parent=F)
    g.bone("tower_trolley", pivot=(0, 196, STAND_Z), parent=F)
    g.bone("tower_rope", pivot=(0, 190, STAND_Z), parent="tower_trolley")
    g.bone("tower_hook", pivot=(0, 174, STAND_Z), parent="tower_trolley")
    plat_bones = []
    for k, y in enumerate(PLATFORM_Y):
        for s in (-1, 1):
            plat_bones.append((f"platform_{'w' if s < 0 else 'e'}{k}", s, y))
            g.bone(plat_bones[-1][0], pivot=(27 * s, y, STAND_Z), parent=F)
    deck(g, -40, 40, -8, 72)
    console(g)
    housing(g, 16, 64)
    hatch(g, -16, 64)
    # The stand: a plinth with a ring on it under the missile, four hold-down posts on the diagonals clear of its fins.
    stand = ["stand_plinth", "stand_ring"]
    g.box("stand_plinth", (-15, DECK_Y, STAND_Z - 15), (15, 17, STAND_Z + 15), faces=NO_DOWN, bone=F)
    g.box("stand_ring", (-9, 17, STAND_Z - 9), (9, STAND_Y, STAND_Z + 9), faces=NO_DOWN, bone=F)
    posts = []
    for i, (x, z) in enumerate(((-14, STAND_Z - 14), (12, STAND_Z - 14), (-14, STAND_Z + 12), (12, STAND_Z + 12))):
        posts.append(f"hold{i}")
        g.box(posts[-1], (x, 17, z), (x + 2, 25, z + 2), faces=NO_DOWN, bone=F)
    # The four lattice columns: corner posts, a girder round each storey.
    lattice, girders = [], []
    for cx in (-32, 32):
        for cz in (0, 64):
            tag = f"{'w' if cx < 0 else 'e'}{'f' if cz == 0 else 'b'}"
            for i, (dx, dz) in enumerate(((-4, -4), (2, -4), (-4, 2), (2, 2))):
                lattice.append(f"col_{tag}{i}")
                g.box(lattice[-1], (cx + dx, DECK_Y, cz + dz), (cx + dx + 2, TOWER_TOP, cz + dz + 2), faces=SIDES, bone=F)
            for k in range(1, 12):
                y = 16 * k + 6
                for n, a, b in ((f"g{tag}{k}n", (cx - 2, y, cz - 3.7), (cx + 2, y + 1.5, cz - 2.3)),
                                (f"g{tag}{k}s", (cx - 2, y, cz + 2.3), (cx + 2, y + 1.5, cz + 3.7)),
                                (f"g{tag}{k}w", (cx - 3.7, y, cz - 2), (cx - 2.3, y + 1.5, cz + 2)),
                                (f"g{tag}{k}e", (cx + 2.3, y, cz - 2), (cx + 3.7, y + 1.5, cz + 2))):
                    g.box(n, a, b, faces=ALL, bone=F)
                    girders.append(n)
    # Cross-bracing across the back between the back columns, a pair of diagonals a bay (the front stays open).
    braces = []
    for k, (y0, y1) in enumerate(((24, 70), (72, 118), (120, 166))):
        for s in (-1, 1):
            braces.append(f"brace{k}_{'a' if s < 0 else 'b'}")
            strut(g, braces[-1], (28 * s, y0, 64 + 2.6 * s), (-28 * s, y1, 64 + 2.6 * s), 1.2, F, faces=SIDES, d=1.0)
    # The side beams the platforms hinge on, front column to back on each side, at each platform level.
    beams = []
    for k, y in enumerate(PLATFORM_Y):
        for s in (-1, 1):
            beams.append(f"side_beam_{'w' if s < 0 else 'e'}{k}")
            g.box(beams[-1], (27 * s - 1.5 if s > 0 else -28.5, y - 4, 4), (28.5 if s > 0 else -25.5, y, 60), faces=ALL, bone=F)
    # The top: a box girder down each side over the columns, the crane beam across the middle.
    top = ["top_w", "top_e", "crane_beam"]
    g.box("top_w", (-38, TOWER_TOP, -6), (-26, TOWER_TOP + 8, 70), faces=ALL, bone=F)
    g.box("top_e", (26, TOWER_TOP, -6), (38, TOWER_TOP + 8, 70), faces=ALL, bone=F)
    g.box("crane_beam", (-26, 198, STAND_Z - 3), (26, 206, STAND_Z + 3), faces=ALL, bone=F)
    # A ladder up the back right column's outside.
    g.box("ladder", (38, DECK_Y, 60), (39, TOWER_TOP, 68), faces=("east", "west", "north", "south"), bone=F)
    # The hoist: a trolley hung under the crane beam, its rope and hook block.
    trolley = ["tower_trolley_body"]
    g.box("tower_trolley_body", (-5, 190, STAND_Z - 5), (5, 197.5, STAND_Z + 5), faces=ALL, bone="tower_trolley")
    ropes, blocks = hook_parts(g, "tower", 0, STAND_Z, 190, 174, 6, 6, "tower_rope", "tower_hook", falls=2)
    # The work platforms: a grating deck from the side beam in to the missile, toe boards and a rail at each end.
    plats = []
    for name, s, y in plat_bones:
        x0, x1 = (-25.5, -11) if s < 0 else (11, 25.5)
        parts = [f"{name}_deck", f"{name}_toe_n", f"{name}_toe_s", f"{name}_rail_n", f"{name}_rail_s"]
        g.box(parts[0], (x0, y - 1.5, STAND_Z - 12), (x1, y, STAND_Z + 12), faces=ALL, bone=name)
        g.box(parts[1], (x0, y, STAND_Z - 12), (x1, y + 2, STAND_Z - 11.5), faces=ALL, bone=name)
        g.box(parts[2], (x0, y, STAND_Z + 11.5), (x1, y + 2, STAND_Z + 12), faces=ALL, bone=name)
        g.box(parts[3], (x0, y + 9, STAND_Z - 12), (x1, y + 10, STAND_Z - 11), faces=ALL, bone=name)
        g.box(parts[4], (x0, y + 9, STAND_Z + 11), (x1, y + 10, STAND_Z + 12), faces=ALL, bone=name)
        for i, z in enumerate((STAND_Z - 12, STAND_Z + 11)):
            for j, x in enumerate((x0 + 1, x1 - 2) if s < 0 else (x0 + 1, x1 - 2)):
                parts.append(f"{name}_post{i}{j}")
                g.box(parts[-1], (x, y + 2, z), (x + 1, y + 9, z + 1), faces=SIDES, bone=name)
        plats.append(parts)
    g.layout()
    tex = Tex(g.tex_w, g.tex_h)
    S = M.Surfacer(g, seed=83)
    paint_deck(S, tex, (-40, 40, -8, 72), 18, 83, bay_z=(10, 70))
    paint_ports(S, g, tex)
    S.paint(tex, stand, M.painted(M.ARMOUR_RAMP, tone=0.5, wear=0.3, rust=0.08))
    S.overlay(tex, "stand_plinth", lambda f, img: M.rivets(f, img, every=5, inset=2), only=("up",))
    L._hazard_posts(S, tex, posts, top=23)
    white = M.painted(M.WHITE_PAINT_RAMP, tone=0.66, wear=0.2, rust=0.06, streaks=0.08)
    S.paint(tex, lattice + girders + beams + braces, white)
    yellow = M.painted(M.YELLOW_PAINT_RAMP, tone=0.6, wear=0.3, rust=0.1, streaks=0.05)
    S.paint(tex, top, yellow)
    S.overlay(tex, ["top_w", "top_e", "crane_beam"], lambda f, img: M.rivets(f, img, every=8, inset=1), only=SIDES)
    S.paint(tex, "ladder", M.galvanized(tone=0.6))

    def rungs(f, img):
        M.darken(img, (np.floor(f.Y) % 4) != 0, 0.45)
    S.overlay(tex, "ladder", rungs, only=("east", "west"))
    S.paint(tex, trolley, M.painted(M.GREY_PAINT_RAMP, tone=0.42, wear=0.3, rust=0.1))
    paint_hook(S, tex, ropes, blocks)
    for parts in plats:
        L._grating(S, tex, [parts[0]], M.ORANGE_PAINT_RAMP)
        S.paint(tex, parts[1:3], M.painted(M.ORANGE_PAINT_RAMP, tone=0.58, wear=0.35))
        S.paint(tex, parts[3:], M.painted(M.YELLOW_PAINT_RAMP, tone=0.6, wear=0.35))
    g.save(tex)
    # Working (12 s, looped while it works): the trolley runs out to the tower's side, the hook goes down for a stage,
    # comes up with it, the trolley runs in over the stand and lowers it onto the stack.
    work = Animation("working", 12.0, loop=True)
    work.move("tower_trolley", {0: (0, 0, 0), 2.0: (21, 0, 0), 6.6: (21, 0, 0), 8.0: (0, 0, 0), 12: (0, 0, 0)}, easing="easeInOutSine")
    drop(work, "tower_hook", "tower_rope0", 16, {0: 0, 2.2: 0, 4.0: 134, 4.6: 134, 6.4: 0, 8.2: 0, 9.2: 4, 10.0: 4, 11.0: 0, 12: 0})
    work.scale("tower_rope1", {t: v for t, v in work.bones["tower_rope0"]["scale"].items()}, easing="easeInOutSine")
    idle = rest_pose([work])
    # The platforms (their own controller): folded up against the side beams while the stand is empty, down round
    # the missile while one stands there.
    fold = {name: (0, 0, 90 if s < 0 else -90) for name, s, _ in plat_bones}
    down = Animation("platforms_in", 2.5, loop="hold_on_last_frame")
    up = Animation("platforms_out", 2.5, loop="hold_on_last_frame")
    for k, (name, s, _) in enumerate(plat_bones):
        lag = 0.25 * (k // 2)
        down.rotate(name, {0: fold[name], lag: fold[name], 1.5 + lag: (0, 0, 0), 2.5: (0, 0, 0)}, easing="easeInOutSine")
        up.rotate(name, {0: (0, 0, 0), lag: (0, 0, 0), 1.5 + lag: fold[name], 2.5: fold[name]}, easing="easeInOutSine")
    print(save_animations("strategic_missile_assembler", [work, idle, down, up]))


# --- controllers and spawners -----------------------------------------------------------------------------------------------
def light_placard(t: Tex) -> None:
    """An A-frame over a missile on cradles."""
    t.rect(4, 9, 6, 4, YELLOW[4])
    t.hline(4, 12, 6, P.BLACK_PAINT[1])
    t.px(4, 10, P.BLACK_PAINT[1])
    t.px(9, 10, P.BLACK_PAINT[1])
    t.hline(5, 9, 4, P.BLACK_PAINT[1])


def medium_placard(t: Tex) -> None:
    """A jib crane."""
    t.rect(4, 9, 6, 4, YELLOW[4])
    t.vline(8, 9, 4, P.BLACK_PAINT[1])
    t.hline(4, 9, 5, P.BLACK_PAINT[1])
    t.px(5, 10, P.BLACK_PAINT[1])


def heavy_placard(t: Tex) -> None:
    """A gantry portal."""
    t.rect(4, 9, 6, 4, YELLOW[4])
    t.hline(4, 9, 6, P.BLACK_PAINT[1])
    t.vline(4, 9, 4, P.BLACK_PAINT[1])
    t.vline(9, 9, 4, P.BLACK_PAINT[1])
    t.px(6, 11, P.BLACK_PAINT[1])


def strategic_placard(t: Tex) -> None:
    """A tower with a missile standing in it."""
    t.rect(4, 9, 6, 4, YELLOW[4])
    t.vline(4, 9, 4, P.BLACK_PAINT[1])
    t.vline(9, 9, 4, P.BLACK_PAINT[1])
    t.vline(6, 10, 3, P.WARNING_RED[3])
    t.vline(7, 10, 3, P.WARNING_RED[3])


def plan(cols: int, rows: int, marks) -> callable:
    """A spawner sheet's floor plan: the structure's cells, the controller marked, and its own marks."""
    B = MT.BLUEPRINT
    w, h = cols * 2 + 1, rows * 2 + 1
    ox, oy = 8 - w // 2, 7 - h // 2

    def draw(t: Tex) -> None:
        for x in range(ox, ox + w):
            t.px(x, oy, B[6])
            t.px(x, oy + h - 1, B[6])
        for y in range(oy, oy + h):
            t.px(ox, y, B[6])
            t.px(ox + w - 1, y, B[6])
        t.px(ox + w // 2, oy, P.HAZARD_YELLOW[5])                   # the controller at the front
        for x, y, col in marks(ox, oy, w, h):
            t.px(x, y, col)
    return draw


def main() -> None:
    for kind, placard in (("light", light_placard), ("medium", medium_placard), ("heavy", heavy_placard), ("strategic", strategic_placard)):
        ST.controller(f"{kind}_missile_assembler", ST.controller_front(placard), ST.controller_top(), ST.casing())
    light()
    medium()
    heavy()
    strategic()
    B = MT.BLUEPRINT
    MT.structure_spawner("light_missile_assembler_spawner", plan(3, 6, lambda ox, oy, w, h: [(ox + w // 2, y, B[4]) for y in range(oy + 3, oy + h - 2)]))
    MT.structure_spawner("medium_missile_assembler_spawner", plan(5, 6, lambda ox, oy, w, h: [(ox + w // 2, y, B[4]) for y in range(oy + 2, oy + h - 2)]
                                                             + [(ox + w - 2, oy + h // 2, P.HAZARD_YELLOW[4])]))
    MT.structure_spawner("heavy_missile_assembler_spawner", plan(5, 6, lambda ox, oy, w, h: [(ox + w // 2, y, B[4]) for y in range(oy + 2, oy + h - 2)]
                                                            + [(ox + 1, y, B[5]) for y in range(oy + 1, oy + h - 1)]
                                                            + [(ox + w - 2, y, B[5]) for y in range(oy + 1, oy + h - 1)]))
    MT.structure_spawner("strategic_missile_assembler_spawner", plan(5, 5, lambda ox, oy, w, h: [(ox + 1, oy + 1, B[5]), (ox + w - 2, oy + 1, B[5]),
                                                                (ox + 1, oy + h - 2, B[5]), (ox + w - 2, oy + h - 2, B[5]),
                                                                (ox + w // 2, oy + h // 2, P.WARNING_RED[4])]))
    MT.structure_spawner("strategic_assembly_hall_spawner", plan(5, 6, lambda ox, oy, w, h: [(ox + w // 2, y, B[4]) for y in range(oy + 2, oy + h - 1)]
                                                            + [(ox, oy + h // 2, B[5]), (ox + w - 1, oy + h // 2, B[5])]))
    print("assemblers written")


if __name__ == "__main__":
    main()
