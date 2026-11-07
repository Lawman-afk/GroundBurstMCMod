"""The Blast Door (DESIGN.md §22.3): a bunker's swinging steel blast door for a doorway two blocks wide and three high,
after the hinged blast doors of Cold War shelters and command posts (a thick steel leaf on two massive hinges, closed
against a steel frame and locked by bolts the handwheel drives out into it, a handwheel on either face so it can be
worked from both sides).

Pixels: x across the doorway, y up, z back from the front (north); the origin is the bottom middle of the hinges' cell
(the block entity's), the door reaching across the next cell along +x (HAND +1: its hinges on the right, seen from the
front) or along -x (HAND -1, "blast_door_mirrored": the other mirrored, not turned, so both swing out to the front). The
frame lines the doorway from z -4 back to the wall's back face; the leaf closes on its front, a pixel proud of the
wall, and swings out to the front about its hinge pins, which stand in front of it past its hinge edge.

Bones (BlastDoorBlockEntity moves them; it has the same figures):
- "frame": jambs, lintel, sill, the hinges' brackets, the frame's knuckles and the pin (static).
- "leaf": the leaf, turned about the hinge pins (y) up to OPEN_ANGLE as the door opens.
- "wheel_f_mount" / "wheel_b_mount" (on the leaf): each handwheel laid on its face (the wheel is built standing).
- "wheel_f" / "wheel_b": the wheels, spun about their shafts as the bolts are drawn and thrown.
- "bolt_top" / "bolt_bottom" / "bolt_side" (on the leaf, behind it): the locking bars, slid BOLT_TRAVEL px in as it
  unlocks, out into the frame as it locks.

    tools\\.venv\\Scripts\\python.exe tools\\assets\\blast_door.py
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))

from gb import machine_kit as K  # noqa: E402
from gb import materials as M  # noqa: E402
from gb import paths  # noqa: E402
from gb.geo import Animation, GeoModel, save_animations  # noqa: E402
from gb.model import write_json  # noqa: E402
from gb.tex import FONT, GLYPH_W, Tex  # noqa: E402
import scud_storm as SS  # noqa: E402
import warheads as W  # noqa: E402

NAME = "blast_door"
SIDES, ALL = SS.SIDES, SS.ALL

# --- the figures BlastDoorBlockEntity shares -------------------------------------------------------------------------------
#: The hinge pins' axis (x for the left-hand door; z): in front of the leaf and past its hinge edge, so the leaf swings
#: clear of the frame's knuckles. How far the leaf turns to stand open.
HINGE_X, HINGE_Z = -6.0, -12.0
OPEN_ANGLE = 90.0
#: The handwheels' middle (x, y on the leaf), and how far the bolts slide.
WHEEL_X, WHEEL_Y = 8.0, 24.0
BOLT_TRAVEL = 3.0

#: The frame: its jambs' width, the sill's top and the lintel's bottom, its depth (from the face the leaf closes on to
#: the wall's back face).
JAMB = 3.0
SILL, LINTEL = 1.5, 45.0
FRAME_Z = (-4.0, 8.0)
#: The leaf: across (x), up (y), front to back (z); its back closes on the frame's front.
LEAF_X = (-5.7, 21.6)
LEAF_Y = (0.5, 46.5)
LEAF_Z = (-9.0, -4.0)
#: The raised rim round the leaf's face: its width, how far it stands proud.
RIM_W, RIM_D = 1.5, 0.5
#: The gearbox on the leaf's back (half its width; its back face), and how deep the bolts run behind the leaf: always
#: behind the frame's front, so where they enter the frame they are inside it.
BOX_HALF, BOX_BACK = 3.5, -2.4
BAR_Z = (-3.7, -2.8)
#: The handwheel: rim radius (outside and in), how far out from its face the rim stands.
RIM_OUT, RIM_IN, RIM_OFF = 7.0, 5.9, 2.4
#: The hinges: the knuckles' radius; the frame's knuckles (y from, to) and the leaf's, between them on one pin.
KNUCKLE = 2.0
FRAME_KNUCKLES = ((5.0, 10.0), (36.0, 41.0))
LEAF_KNUCKLES = ((10.2, 15.0), (31.0, 35.8))


def xs(hand: int, a: float, b: float) -> tuple[float, float]:
    """x from a to b as drawn, mirrored for the mirrored door (in order)."""
    return tuple(sorted((hand * a, hand * b)))


def spokes(g: GeoModel, name: str, r0: float, r1: float, w: float, y0: float, y1: float, cx: float, cz: float, bone: str,
           count: int = 4, turn: float = 45.0) -> list[str]:
    """Spokes from the hub out to the rim, one turned to each place (they meet only inside the hub)."""
    names = []
    for k in range(count):
        nm = f"{name}_{k}"
        rot = {"origin": (cx, y0, cz), "axis": "y", "angle": turn + 360.0 * k / count}
        g.box(nm, (cx - w / 2, y0, cz - r1), (cx + w / 2, y1, cz - r0), faces=("east", "west", "up", "down"), rotation=rot, bone=bone)
        names.append(nm)
    return names


def wheel(g: GeoModel, p: SS.Parts, side: str, hand: int, z_face: float, outward: int) -> None:
    """A handwheel on a face at z_face, facing out along z (outward -1: the front, +1: the back), built standing about its
    middle and laid on the face by its mount; the boss stays with the mount, the rest spins with the wheel."""
    cx, cy = hand * WHEEL_X, WHEEL_Y
    mount, spin = f"wheel_{side}_mount", f"wheel_{side}"
    g.bone(mount, pivot=(cx, cy, z_face), parent="leaf", rotation=(90.0 * outward, 0, 0))
    g.bone(spin, pivot=(cx, cy, z_face), parent=mount)
    # (Standing: the axis runs up y from cy; the disc lies in x and z round (cx, z_face).)
    # (Round parts: warheads.round_section, their caps' planks a level apart only where they overlap in sight.)
    p.add(f"boss_{side}", ring(g, f"boss_{side}", 3.0, cy, cy + 1.0, cx, z_face, mount, hi=1.5))
    p.add(f"hub_{side}", ring(g, f"hub_{side}", 1.5, cy + 0.9, cy + RIM_OFF + 1.5, cx, z_face, spin, hi=0.0))
    p.add(f"rim_{side}", W.round_ring(SS.OnBone(g, spin), f"rim_{side}", RIM_IN, RIM_OUT, cy + RIM_OFF, cy + RIM_OFF + 1.0, c=(cx, z_face)))
    p.add(f"spoke_{side}", spokes(g, f"spoke_{side}", 1.2, RIM_IN + 0.3, 0.8, cy + RIM_OFF + 0.1, cy + RIM_OFF + 0.9, cx, z_face, spin))
    # A grip standing out from the rim, to spin it by.
    gx = cx + hand * (RIM_OUT - 0.55)
    p.add(f"grip_{side}", ring(g, f"grip_{side}", 0.7, cy + RIM_OFF + 0.8, cy + RIM_OFF + 3.2, gx, z_face, spin, hi=0.0))


def ring(g: GeoModel, name: str, r: float, y0: float, y1: float, cx: float, cz: float, bone: str, **kw) -> list[str]:
    """A round part on a bone (warheads.round_section), its planks kept for painting round."""
    names = W.round_section(SS.OnBone(g, bone), name, r, y0, y1, c=(cx, cz), **kw)
    SS.ROUND.extend(names)
    return names


def build(hand: int) -> tuple[GeoModel, SS.Parts]:
    name = NAME if hand > 0 else f"{NAME}_mirrored"
    g = GeoModel(name, tex_size=(256, 256))
    g.bone("frame")
    g.bone("leaf", pivot=(hand * HINGE_X, 0, HINGE_Z), parent="frame")
    p = SS.Parts()
    SS.ROUND.clear()
    F, L = "frame", "leaf"
    z0, z1 = FRAME_Z
    # --- the frame: two jambs, the lintel over them, the sill between --------------------------------------------------
    hx0, hx1 = xs(hand, -8.0, -8.0 + JAMB)
    lx0, lx1 = xs(hand, 24.0 - JAMB, 24.0)
    g.box("jamb_h", (hx0, 0, z0), (hx1, LINTEL, z1), faces=SIDES, bone=F)
    g.box("jamb_l", (lx0, 0, z0), (lx1, LINTEL, z1), faces=SIDES, bone=F)
    ax0, ax1 = xs(hand, -8.0, 24.0)
    g.box("lintel", (ax0, LINTEL, z0), (ax1, 48, z1), faces=ALL, bone=F)
    tx0, tx1 = xs(hand, -8.0 + JAMB, 24.0 - JAMB)
    g.box("sill", (tx0, 0, z0), (tx1, SILL, z1), faces=("north", "south", "up"), bone=F)
    p.add("frame", "jamb_h", "jamb_l", "lintel")
    p.add("sill", "sill")
    # --- the hinges: the frame's knuckles on brackets standing out of the hinge jamb, the leaf's on straps across its face,
    # all on one pin -------------------------------------------------------------------------------------------------------
    kx = hand * HINGE_X
    for j, (ky0, ky1) in enumerate(FRAME_KNUCKLES):
        p.add("knuckle", ring(g, f"knuckle_f{j}", KNUCKLE, ky0, ky1, kx, HINGE_Z, F, lo=1.25, hi=1.25))
        bx0, bx1 = xs(hand, -7.8, HINGE_X)
        g.box(f"bracket{j}", (bx0, ky0 + 0.5, HINGE_Z), (bx1, ky1 - 0.5, z0), faces=("north", "east", "west", "up", "down"), bone=F)
        p.add("bracket", f"bracket{j}")
    for j, (ky0, ky1) in enumerate(LEAF_KNUCKLES):
        p.add("knuckle", ring(g, f"knuckle_l{j}", KNUCKLE, ky0, ky1, kx, HINGE_Z, L, lo=1.25, hi=1.25))
        sx0, sx1 = xs(hand, HINGE_X, 1.0)
        g.box(f"strap{j}", (sx0, ky0 + 0.5, HINGE_Z + 0.8), (sx1, ky1 - 0.5, LEAF_Z[0] + 0.2), faces=("north", "east", "west", "up", "down"), bone=L)
        p.add("strap", f"strap{j}")
    # A pin through each hinge, its ends showing past the knuckles.
    pins = ((FRAME_KNUCKLES[0][0] - 0.6, LEAF_KNUCKLES[0][1] + 0.6), (LEAF_KNUCKLES[1][0] - 0.6, FRAME_KNUCKLES[1][1] + 0.6))
    for j, (a, b) in enumerate(pins):
        p.add("pin", ring(g, f"pin{j}", 1.25, a, b, kx, HINGE_Z, F, lo=0.0, hi=0.0))
    # --- the leaf: a steel slab, a raised rim round its face and two ribs across it ---------------------------------------
    fx0, fx1 = xs(hand, *LEAF_X)
    y0, y1 = LEAF_Y
    fz0, fz1 = LEAF_Z
    g.box("leaf", (fx0, y0, fz0), (fx1, y1, fz1), faces=ALL, bone=L)
    p.add("leaf", "leaf")
    rz0, rz1 = fz0 - RIM_D, fz0
    g.box("rim_top", (fx0, y1 - RIM_W, rz0), (fx1, y1, rz1), faces=("north", "east", "west", "up", "down"), bone=L)
    g.box("rim_bot", (fx0, y0, rz0), (fx1, y0 + RIM_W, rz1), faces=("north", "east", "west", "up", "down"), bone=L)
    for s, (a, b) in (("h", (LEAF_X[0], LEAF_X[0] + RIM_W)), ("l", (LEAF_X[1] - RIM_W, LEAF_X[1]))):
        x0, x1 = xs(hand, a, b)
        g.box(f"rim_{s}", (x0, y0 + RIM_W, rz0), (x1, y1 - RIM_W, rz1), faces=("north", "east", "west"), bone=L)
    p.add("leaf_rim", "rim_top", "rim_bot", "rim_h", "rim_l")
    for j, y in enumerate((12.0, 36.0)):
        x0, x1 = xs(hand, LEAF_X[0] + RIM_W, LEAF_X[1] - RIM_W)
        g.box(f"rib{j}", (x0, y, rz0), (x1, y + 1.0, rz1), faces=("north", "up", "down"), bone=L)
        p.add("leaf_rim", f"rib{j}")
    # --- the handwheels: one on the face, one on the gearbox behind ------------------------------------------------------
    wheel(g, p, "f", hand, fz0, -1)
    cx0, cx1 = xs(hand, WHEEL_X - BOX_HALF, WHEEL_X + BOX_HALF)
    g.box("gearbox", (cx0, WHEEL_Y - BOX_HALF, fz1), (cx1, WHEEL_Y + BOX_HALF, BOX_BACK), faces=("south", "east", "west", "up", "down"), bone=L)
    p.add("gearbox", "gearbox")
    wheel(g, p, "b", hand, BOX_BACK, +1)
    # --- the bolts: three bars out of the gearbox, thrown into the lintel, the sill and the latch jamb ------------------
    for b in ("bolt_top", "bolt_bottom", "bolt_side"):
        g.bone(b, pivot=(hand * WHEEL_X, WHEEL_Y, fz1), parent=L)
    bz0, bz1 = BAR_Z
    vx0, vx1 = xs(hand, WHEEL_X - 0.6, WHEEL_X + 0.6)
    g.box("bar_top", (vx0, WHEEL_Y + BOX_HALF - 1.0, bz0), (vx1, LINTEL + 2.0, bz1), faces=ALL, bone="bolt_top")
    g.box("bar_bot", (vx0, SILL - 1.0, bz0), (vx1, WHEEL_Y - BOX_HALF + 1.0, bz1), faces=ALL, bone="bolt_bottom")
    sx0, sx1 = xs(hand, WHEEL_X + BOX_HALF - 1.0, 24.0 - JAMB + 1.5)
    g.box("bar_side", (sx0, WHEEL_Y - 0.6, bz0), (sx1, WHEEL_Y + 0.6, bz1), faces=ALL, bone="bolt_side")
    p.add("bar", "bar_top", "bar_bot", "bar_side")
    # Guides the bars run through, on the leaf.
    for j, (a, b, c, d) in enumerate(((WHEEL_X - 1.2, WHEEL_X + 1.2, 40.0, 41.5), (WHEEL_X - 1.2, WHEEL_X + 1.2, 6.0, 7.5),
                                      (17.0, 18.5, WHEEL_Y - 1.2, WHEEL_Y + 1.2))):
        gx0, gx1 = xs(hand, a, b)
        g.box(f"guide{j}", (gx0, c, fz1), (gx1, d, BOX_BACK), faces=("south", "east", "west", "up", "down"), bone=L)
        p.add("guide", f"guide{j}")
    return g, p


# --- painting ------------------------------------------------------------------------------------------------------------------
#: Shelter green-grey: the leaf.
DOOR_RAMP = M.ramp("#2c332f", "#3c4540", "#4d5852", "#606c65", "#76827b", "#8e9a93", "#a7b1aa")
DOOR = M.painted(DOOR_RAMP, tone=0.5, wear=0.22, rust=0.05)
FRAME = M.steel(tone=0.38, rust=0.08)
SILL_STEEL = M.steel(tone=0.5, brushed=0.2)
STRAP = M.steel(tone=0.32, rust=0.1)
WHEEL_RED = M.painted(M.RED_PAINT_RAMP, tone=0.48, wear=0.32)
BRIGHT = M.steel(tone=0.62, brushed=0.12)
CAM = M.steel(tone=0.42)
GRIP = M.rubber(tone=0.3)
STENCIL = np.array([222, 222, 210], np.float32)
#: The stencilled number at the top of the leaf's face.
MARK = "B-3"


def stencil(f: M.Face, img: np.ndarray, text: str, hand: int) -> None:
    """Stencilled letters on the leaf's face, read from the front (where -x is to the right), centred across the leaf in
    the panel over its upper rib; a texel a pixel, 3x5 letters a texel apart."""
    width = len(text) * (GLYPH_W + 1) - 1
    left = hand * (LEAF_X[0] + LEAF_X[1]) / 2 + width / 2              # the letters' left edge, seen from the front
    col = np.floor(left - f.X).astype(int)
    row = np.floor(43.0 - f.Y).astype(int)
    on = np.zeros(img.shape[:2], bool)
    for i, ch in enumerate(text.upper()):
        glyph = FONT.get(ch)
        if glyph is None:
            continue
        gx = col - i * (GLYPH_W + 1)
        ok = (gx >= 0) & (gx < GLYPH_W) & (row >= 0) & (row < 5)
        lit = np.array(glyph, bool)
        on |= ok & lit[np.clip(row, 0, 4), np.clip(gx, 0, GLYPH_W - 1)]
    rgb = img[..., :3].astype(np.float32)
    rgb[on] = rgb[on] * 0.3 + STENCIL * 0.7
    img[..., :3] = np.clip(rgb, 0, 255).astype(np.uint8)


def kick_plate(S, f: M.Face, img: np.ndarray) -> None:
    """Hazard stripes low across the leaf's face, between its bottom rim and its lower rib."""
    M.paste(img, S.material_on(f, M.stripes(width=3, wear=0.4)), (f.Y > LEAF_Y[0] + RIM_W + 0.5) & (f.Y < 11.5))


def paint(g: GeoModel, p: SS.Parts, hand: int) -> Tex:
    k = K.Kit(g, {}, seed=2203 + (hand < 0), ao_reach=2.6)
    W.round_faces(k.S, SS.ROUND, top=True, bottom=True, foot=False)
    S = k.S
    tex = Tex(g.tex_w, g.tex_h)
    S.paint(tex, p["frame"], FRAME)
    # Hazard paint on the frame's front, seen round the leaf and when the door stands open.
    S.overlay(tex, p["frame"], lambda f, img: M.paste(img, S.material_on(f, M.stripes(width=2, wear=0.3)), np.ones(img.shape[:2], bool)),
              only=("north",))
    S.paint(tex, p["sill"], SILL_STEEL)
    S.paint(tex, p["knuckle"] + p["pin"] + p["bracket"] + p["strap"], STRAP)
    S.overlay(tex, p["strap"], lambda f, img: M.rivets(f, img, every=2.5, inset=1), only=("north",))
    S.overlay(tex, p["bracket"], lambda f, img: M.rivets(f, img, every=2.5, inset=1), only=("east", "west"))
    S.paint(tex, p["leaf"] + p["leaf_rim"], DOOR)
    S.overlay(tex, p["leaf"], lambda f, img: kick_plate(S, f, img), only=("north",))
    S.overlay(tex, p["leaf"], lambda f, img: stencil(f, img, MARK, hand), only=("north",))
    # Rivets round the panel, just inside the rim.
    S.overlay(tex, p["leaf"], lambda f, img: (M.rivets(f, img, every=3, inset=2), M.rivets(f, img, every=3, inset=2, axis="v")),
              only=("north",))
    for side in ("f", "b"):
        S.paint(tex, p[f"boss_{side}"], CAM)
        S.paint(tex, p[f"hub_{side}"], BRIGHT)
        S.paint(tex, p[f"rim_{side}"] + p[f"spoke_{side}"], WHEEL_RED)
        S.paint(tex, p[f"grip_{side}"], GRIP)
    S.paint(tex, p["gearbox"] + p["guide"], CAM)
    S.paint(tex, p["bar"], BRIGHT)
    return tex


def animations(hand: int) -> list[Animation]:
    """Idle: shut (the block entity moves it). Open: how it stands open, for the previews: the leaf swung out, the wheels
    turned, the bolts drawn."""
    idle = Animation("idle", 0.05, loop="hold_on_last_frame")
    idle.move("frame", {0: (0, 0, 0)})
    opened = Animation("open", 0.05, loop="hold_on_last_frame")
    opened.rotate("leaf", {0: (0, hand * OPEN_ANGLE, 0)})
    opened.rotate("wheel_f", {0: (0, 60, 0)})
    opened.rotate("wheel_b", {0: (0, -60, 0)})
    opened.move("bolt_top", {0: (0, -BOLT_TRAVEL, 0)})
    opened.move("bolt_bottom", {0: (0, BOLT_TRAVEL, 0)})
    opened.move("bolt_side", {0: (-hand * BOLT_TRAVEL, 0, 0)})
    return [idle, opened]


# --- the item and the blocks ----------------------------------------------------------------------------------------------------
ICON = [
    "................",
    ".kkkkkkkkkkkkkk.",
    ".kGGGGGGGGGGGGk.",
    ".kGgggggggggGGk.",
    "skGgggggggggggk.",
    "SkGggggRRggggGk.",
    "skGgggRrrRgggGk.",
    ".kGgggRrhrRggGk.",
    ".kGgggRrrRgggGk.",
    "skGggggRRggggGk.",
    "SkGgggggggggggk.",
    "skGgggggggggggk.",
    ".kGYyYyYyYyYyGk.",
    ".kGyYyYyYyYyYGk.",
    ".kkkkkkkkkkkkkk.",
    "................",
]


def icon() -> None:
    """The item's picture: the door from the front, its red handwheel, its hinges on the left, the kick plate's stripes."""
    col = {"k": (44, 48, 46), "G": (96, 108, 101), "g": (118, 130, 123), "R": (168, 44, 36), "r": (196, 64, 52),
           "h": (190, 190, 186), "s": (70, 74, 72), "S": (110, 114, 112), "Y": (218, 172, 40), "y": (36, 36, 38)}
    img = np.zeros((16, 16, 4), np.uint8)
    assert all(len(row) == 16 for row in ICON) and len(ICON) == 16
    for y, row in enumerate(ICON):
        for x, c in enumerate(row):
            if c != ".":
                img[y, x] = (*col[c], 255)
    Image.fromarray(img, "RGBA").save(paths.TEXTURES / "item" / f"{NAME}.png")
    write_json(paths.MODELS / "item" / f"{NAME}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"groundburst:item/{NAME}"}})
    write_json(paths.ASSETS / "items" / f"{NAME}.json", {"model": {"type": "minecraft:model", "model": f"groundburst:item/{NAME}"}})


def block_files() -> None:
    """Every block of the door draws nothing itself (the hinge cell's block entity draws it all): one model, a particle."""
    write_json(paths.MODELS / "block" / f"{NAME}.json", {"textures": {"particle": "groundburst:block/steel_panel"}})
    write_json(paths.ASSETS / "blockstates" / f"{NAME}.json", {"multipart": [{"apply": {"model": f"groundburst:block/{NAME}"}}]})


def main() -> None:
    for hand in (1, -1):
        g, p = build(hand)
        g.layout()
        tex = paint(g, p, hand)
        W.match_walls(g, tex, [b.name for b in g.boxes], entity=True)
        g.save(tex)
        print(save_animations(g.name, animations(hand)))
        lo, hi = g.bounds()
        print(g.name, "bounds", [round(v, 1) for v in lo], [round(v, 1) for v in hi], "tex", g.tex_w, g.tex_h)
    icon()
    block_files()


if __name__ == "__main__":
    main()
