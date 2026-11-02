"""Artillery (DESIGN.md §20.1): the 81 mm Mortar (GeckoLib) and its six shells (3D item models).

- 81 mm Mortar (after the M252): a round dark baseplate with radial ribs and a raised socket; the tube on its ball,
  finned at the breech, olive drab, a dark muzzle; a clamp collar two thirds up with the yoke the elevating screw
  holds and the sight on its left; the bipod in front, two olive legs splayed on their feet pads with a spreader
  between them, the elevating gear's housing where they meet with the traverse crank beside it, and the screw rising
  to the collar out of its sleeve.

  Bones (MortarBlockEntity turns them): "traverse" (round the socket), "tube" (raised about the socket: built upright,
  laid at 60 degrees at rest), "bipod" (leaned about its feet line: built upright on it, leaned back to put the screw's
  foot under the collar), "screw_base" (the sleeve, turned back upright) and its child "screw" (stretched to reach the
  collar as the tube rises and falls). The block entity has the same figures (SOCKET_Y, SOCKET_BACK, TUBE, COLLAR,
  FEET_Z, SCREW_Y, SCREW_Z).
- Shells (items; the shell in flight is drawn from the same model): an 81 mm mortar bomb standing on its fins, olive drab with
  its kind's band (HE yellow, smoke pale green, incendiary red, chemical grey with green bands, cryo pale blue, EMP
  violet), a ring of propellant increments on its tail boom, a fuze on its nose.

Pixels: x right, y up, z back from the block's bottom middle; front = north.

    tools\\.venv\\Scripts\\python.exe tools\\assets\\artillery.py
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
from gb import paths  # noqa: E402
from gb.geo import Animation, GeoModel, save_animations  # noqa: E402
from gb.model import BlockModel, write_json  # noqa: E402
from gb.tex import Tex  # noqa: E402
import particle_cannon as PC  # noqa: E402
import scud_storm as SS  # noqa: E402
import warheads as W  # noqa: E402

NAME = "mortar"
SIDES, ALL = ("north", "south", "east", "west"), ("north", "south", "east", "west", "up", "down")
# --- the figures MortarBlockEntity shares -------------------------------------------------------------------------------
SX, SY, SZ = 0.0, 2.2, 2.0                         # the socket
TUBE, COLLAR = 20.0, 12.0
FEET_Z, SCREW_Y, SCREW_Z = -8.0, 7.0, -4.0
REST = 60.0
LEAN = math.degrees(math.atan2(SCREW_Z - FEET_Z, SCREW_Y))
LEG_TOP = math.hypot(SCREW_Y, SCREW_Z - FEET_Z)    # the screw's foot, up the bipod's plane from its feet
SCREW_LEN = SY + COLLAR * math.sin(math.radians(REST)) - SCREW_Y
FOOT_X = 6.0                                       # the feet, either side
PLATE_R = 6.0

# --- materials --------------------------------------------------------------------------------------------------------
OD_RAMP = M.ramp("#1d2114", "#272c1a", "#323822", "#3e452b", "#4b5335", "#59623f", "#68724b", "#7a8459", "#8f9a6b")
OD = M.painted(OD_RAMP, tone=0.52, wear=0.2, streaks=0.05)
PLATE = M.painted(M.ARMOUR_RAMP, tone=0.36, wear=0.25, rust=0.06, grime=0.12, dust=0.06)
GUN = K.GUN
DARK = K.DARK
STEEL = M.steel(tone=0.58, brushed=0.08)
BLACK = M.painted(M.BLACK_RAMP, tone=0.4, wear=0.1)


def ring(g: GeoModel, name: str, r: float, y0: float, y1: float, bone: str, caps=("up", "down"), cx: float = SX, cz: float = SZ,
         stagger: str | None = None) -> list[str]:
    return SS.ring(g, name, r, y0, y1, cx, cz, bone, caps=caps, stagger=stagger)


def build() -> tuple[GeoModel, SS.Parts]:
    g = GeoModel(NAME, tex_size=(64, 64))
    g.bone("frame")
    g.bone("traverse", pivot=(SX, SY, SZ), parent="frame")
    g.bone("tube", pivot=(SX, SY, SZ), parent="traverse")
    g.bone("bipod", pivot=(0, 0, FEET_Z), parent="traverse")
    g.bone("screw_base", pivot=(0, LEG_TOP, FEET_Z), parent="bipod")
    g.bone("screw", pivot=(0, LEG_TOP, FEET_Z), parent="screw_base")
    p = SS.Parts()
    SS.ROUND.clear()

    # --- the baseplate (it stays put) ------------------------------------------------------------------------------
    # (Its planks a quarter turn apart meet in the middle, under the socket.)
    p.add("plate", ring(g, "plate", PLATE_R, 0, 1.0, "frame", caps=("up",)))          # (its foot a pixel into the ground)
    for k in range(6):
        a = 30 + 60 * k
        n = f"rib{k}"
        g.box(n, (SX - 0.4, 1.0, SZ - PLATE_R + 0.6), (SX + 0.4, 1.7, SZ - 2.6), faces=("north", "east", "west", "up"),
              rotation={"origin": (SX, 1.0, SZ), "axis": "y", "angle": a}, bone="frame")
        p.add("rib", n)
    # The socket: an octagon, each plank 0.15 under the last (its top shows round the ball).
    socket = PC.prism(g, "socket", 2.5, 0.4, 2.5, SX, SZ, "frame", sides=8, caps=("up",))     # (down into the plate)
    SS.ROUND.extend(socket)
    p.add("socket", socket)
    # The carrying handle at its back: two posts and a grip.
    for side, x in (("w", -1.6), ("e", 1.1)):
        g.box(f"handle_{side}", (x, 1.0, SZ + PLATE_R - 1.2), (x + 0.5, 2.6, SZ + PLATE_R - 0.6), faces=SIDES + ("up",), bone="frame")
        p.add("handle", f"handle_{side}")
    g.box("handle_grip", (-1.6, 2.6, SZ + PLATE_R - 1.2), (1.6, 3.1, SZ + PLATE_R - 0.6), faces=ALL, bone="frame")
    p.add("handle", "handle_grip")
    # The bipod's feet pads (they stay on the ground as the bipod leans about them).
    for side, x in (("w", -FOOT_X), ("e", FOOT_X)):
        g.box(f"pad_{side}", (x - 1.0, 0, FEET_Z - 1.0), (x + 1.0, 0.6, FEET_Z + 1.0), faces=SIDES + ("up",), bone="traverse")
        p.add("pad", f"pad_{side}")

    # --- the tube (built upright on the socket; laid by its bone) ----------------------------------------------------
    p.add("ball", ring(g, "ball", 1.9, SY - 0.6, SY + 1.2, "tube", caps=("up",)))
    p.add("bore", ring(g, "body", 1.55, SY + 1.2, SY + TUBE - 1.2, "tube", caps=()))
    for k, y in enumerate((SY + 2.2, SY + 3.8, SY + 5.4)):
        p.add("fin", ring(g, f"fin{k}", 2.0, y, y + 0.8, "tube"))
    p.add("collar", ring(g, "collar", 2.15, SY + COLLAR - 0.9, SY + COLLAR + 0.9, "tube"))
    # The muzzle: an octagon, each plank 0.12 under the last (its mouth shows, end on).
    muzzle = PC.prism(g, "muzzle", 1.85, SY + TUBE - 1.2, SY + TUBE, SX, SZ, "tube", sides=8, caps=("up", "down"), step=0.12)
    SS.ROUND.extend(muzzle)
    p.add("muzzle", muzzle)
    # The yoke under the collar (toward the bipod) where the screw holds the tube.
    g.box("yoke", (SX - 1.0, SY + COLLAR - 0.7, SZ - 3.3), (SX + 1.0, SY + COLLAR + 0.7, SZ - 2.0), faces=("north", "east", "west", "up", "down"),
          bone="tube")
    p.add("yoke", "yoke")
    # The sight on the collar's left: its mount, its body and its eyepiece toward the crew.
    g.box("sight_mount", (SX - 3.0, SY + COLLAR - 0.4, SZ - 0.5), (SX - 2.1, SY + COLLAR + 0.4, SZ + 0.5), faces=("north", "south", "west", "up", "down"),
          bone="tube")
    g.box("sight", (SX - 4.6, SY + COLLAR - 1.6, SZ - 1.2), (SX - 3.0, SY + COLLAR + 1.8, SZ + 1.4), faces=ALL, bone="tube")
    g.box("eyepiece", (SX - 4.2, SY + COLLAR + 0.4, SZ + 1.4), (SX - 3.4, SY + COLLAR + 1.2, SZ + 2.0), faces=("south", "east", "west", "up", "down"),
          bone="tube")
    p.add("sight", "sight_mount", "sight", "eyepiece")

    # --- the bipod (built upright on its feet line, z FEET_Z) -----------------------------------------------------------
    span = FOOT_X - 0.9
    lean = math.degrees(math.atan2(span, LEG_TOP))
    length = math.hypot(span, LEG_TOP) + 0.4
    for side, x, ang in (("w", -FOOT_X, -lean), ("e", FOOT_X, lean)):
        g.box(f"leg_{side}", (x - 0.45, 0.2, FEET_Z - 0.45), (x + 0.45, 0.2 + length, FEET_Z + 0.45), faces=ALL,
              rotation={"origin": (x, 0.2, FEET_Z), "axis": "z", "angle": ang}, bone="bipod")
        p.add("leg", f"leg_{side}")
    g.box("spreader", (-3.6, 3.4, FEET_Z - 0.3), (3.6, 3.9, FEET_Z + 0.3), faces=ALL, bone="bipod")
    p.add("spreader", "spreader")
    g.box("gearbox", (-1.7, LEG_TOP - 1.4, FEET_Z - 1.1), (1.7, LEG_TOP + 0.6, FEET_Z + 1.1), faces=ALL, bone="bipod")
    p.add("gearbox", "gearbox")
    g.box("crank_arm", (1.7, LEG_TOP - 0.9, FEET_Z - 0.25), (3.3, LEG_TOP - 0.4, FEET_Z + 0.25), faces=("north", "south", "up", "down", "east"),
          bone="bipod")
    g.box("crank_knob", (3.3, LEG_TOP - 1.3, FEET_Z - 0.4), (3.9, LEG_TOP + 0.3, FEET_Z + 0.4), faces=ALL, bone="bipod")
    p.add("crank", "crank_arm", "crank_knob")
    # The elevating screw: its sleeve, then the screw itself (stretched by its bone).
    g.box("sleeve", (-0.9, LEG_TOP + 0.6, FEET_Z - 0.9), (0.9, LEG_TOP + 2.4, FEET_Z + 0.9), faces=SIDES + ("up",), bone="screw_base")
    p.add("sleeve", "sleeve")
    g.box("screw_rod", (-0.5, LEG_TOP + 0.6, FEET_Z - 0.5), (0.5, LEG_TOP + SCREW_LEN, FEET_Z + 0.5), faces=SIDES, bone="screw")
    for d in ("up", "down"):
        g.open_ok.add(("screw_rod", d))
    p.add("screw", "screw_rod")

    # Built upright, its bones unturned: GeckoLib adds the block entity's turns to a bone's own, so the pose it rests in
    # is set by the block entity in the world (and MortarBlockEntity.restPose for the item), the "rest" animation here.
    while True:
        try:
            g.layout()
            break
        except ValueError:
            g.tex_w *= 2
            g.tex_h = g.tex_w
    return g, p


def bipod_lean(e: float) -> float:
    """MortarBlockEntity.bipod's lean at elevation e (degrees): 0 at rest."""
    r = math.radians(e)
    qy, qz = SY + COLLAR * math.sin(r), SZ - COLLAR * math.cos(r)
    dz = SCREW_Z - FEET_Z
    to_y, to_z = qy, qz - FEET_Z
    length = math.sqrt(max(0.0, to_y * to_y + to_z * to_z - dz * dz)) - SCREW_Y
    return math.degrees(math.atan2(to_z, to_y) - math.atan2(dz, SCREW_Y + length))


def paint(g: GeoModel, p: SS.Parts) -> Tex:
    """Olive drab tube, legs and yoke; a dark parkerized baseplate, fins and gear; steel at the wear points (the ball,
    the screw, the crank); a black sight with a lens; the muzzle's bore dark."""
    k = K.Kit(g, {}, seed=917, ao_reach=1.6, turned=True)
    W.round_faces(k.S, SS.ROUND, top=True, bottom=False, foot=True)
    S = k.S
    tex = Tex(g.tex_w, g.tex_h)
    S.paint(tex, p["plate"], PLATE)
    S.overlay(tex, p["plate"], lambda f, img: M.darken(img, np.hypot(f.X - SX, f.Z - SZ) > PLATE_R - 0.8, 0.8), only=("up",))
    S.paint(tex, p["rib"], PLATE)
    S.paint(tex, p["socket"], DARK)
    S.paint(tex, p["handle"], DARK)
    S.paint(tex, p["pad"], PLATE)
    S.paint(tex, p["ball"], STEEL)
    S.paint(tex, p["bore"], OD)
    # A white stencil band near the muzzle, as the tubes carry.
    S.overlay(tex, p["bore"], lambda f, img: M.lighten(img, (f.Y >= SY + TUBE - 4.2) & (f.Y < SY + TUBE - 3.6), 1.5, 30) if f.d in SIDES else None)
    S.paint(tex, p["fin"], M.steel(tone=0.3, brushed=0.04))
    S.paint(tex, p["collar"], GUN)
    S.paint(tex, p["muzzle"], DARK)

    def bore(f, img):                                            # the muzzle's mouth: the bore dark inside a thin lip
        if f.d == "up":
            img[np.hypot(f.X - SX, f.Z - SZ) < 1.25, :3] = P.BLACK_PAINT.deep[:3]
    S.overlay(tex, p["muzzle"], bore)
    S.paint(tex, p["yoke"], GUN)
    S.paint(tex, p["sight"], BLACK)

    def lens(t, r, d):                                           # the eyepiece's glass, toward the crew
        if d == "south":
            x, y, w, h = r
            t.rect(x, y, w, h, (24, 34, 40, 255))
            t.px(x, y, (120, 160, 180, 255))
    g.paint_all(tex, "eyepiece", lens)
    S.paint(tex, p["leg"], OD)
    S.paint(tex, p["spreader"], GUN)
    S.paint(tex, p["gearbox"], OD)
    S.overlay(tex, p["gearbox"], lambda f, img: M.rivets(f, img, every=2, inset=0.5) if f.d in ("north", "south") else None)
    S.paint(tex, p["crank"], STEEL)
    S.paint(tex, p["sleeve"], DARK)
    S.paint(tex, p["screw"], STEEL)
    S.overlay(tex, p["screw"], lambda f, img: M.darken(img, (np.floor(f.Y * 2) % 2) == 0, 0.75))   # its thread
    return tex


def animations() -> list[Animation]:
    """Idle: nothing (the block entity lays it). Rest: as it rests, laid at 60 degrees (the previews' pose; the game never
    plays it)."""
    idle = Animation("idle", 0.05, loop="hold_on_last_frame")
    idle.move("frame", {0: (0, 0, 0)})
    rest = Animation("rest", 0.05, loop="hold_on_last_frame")
    rest.rotate("tube", {0: (-(90 - REST), 0, 0)})
    rest.rotate("bipod", {0: (bipod_lean(REST) + LEAN, 0, 0)})
    rest.rotate("screw_base", {0: (-LEAN, 0, 0)})
    return [idle, rest]


def mortar() -> None:
    g, p = build()
    tex = paint(g, p)
    for path in g.save(tex):
        print(path)
    print(save_animations(NAME, animations()))
    write_json(paths.MODELS / "block" / f"{NAME}.json", {"textures": {"particle": "groundburst:block/machine_particle"}})
    write_json(paths.ASSETS / "blockstates" / f"{NAME}.json",
               {"variants": {f"facing={f}": {"model": f"groundburst:block/{NAME}"} for f in ("north", "east", "south", "west")}})
    write_json(paths.MODELS / "item" / f"{NAME}.json", {"parent": "minecraft:block/block", "textures": {"particle": "groundburst:block/machine_particle"},
                                                       "display": DISPLAY})
    write_json(paths.ASSETS / "items" / f"{NAME}.json",
               {"model": {"type": "minecraft:special", "base": f"groundburst:item/{NAME}", "model": {"type": "geckolib:geckolib"}}})


#: The item: the mortar is a block and a half tall (its muzzle over its front), so drawn smaller than a block.
DISPLAY = {
    "gui": {"rotation": [25, 145, 0], "translation": [0, -1.6, 0], "scale": [0.5, 0.5, 0.5]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 1, 0], "scale": [0.25, 0.25, 0.25]},
    "fixed": {"rotation": [0, 0, 0], "translation": [0, -2.0, 0], "scale": [0.5, 0.5, 0.5]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 1.0, 0], "scale": [0.3, 0.3, 0.3]},
    "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 1.0, 0], "scale": [0.3, 0.3, 0.3]},
    "firstperson_righthand": {"rotation": [0, 135, 0], "translation": [0, -1.5, 0], "scale": [0.32, 0.32, 0.32]},
    "firstperson_lefthand": {"rotation": [0, 135, 0], "translation": [0, -1.5, 0], "scale": [0.32, 0.32, 0.32]},
}


# --- the shells (items) -----------------------------------------------------------------------------------------------------
#: Each kind's band(s) on its body: (ramp, heights).
BANDS = {
    "he": (M.YELLOW_PAINT_RAMP, [(10.0, 11.0)]),
    "smoke": (M.ramp("#1e2a1a", "#2d3d27", "#3e5236", "#526b47", "#678559", "#7e9d6e", "#97b586", "#b1cba0", "#cadfbb"), [(10.0, 11.0)]),
    "incendiary": (M.RED_PAINT_RAMP, [(10.0, 11.0)]),
    "chemical": (M.ramp("#12240f", "#1a3416", "#23461e", "#2e5a27", "#3a7032", "#49873f", "#5a9e4f", "#70b563", "#8acb7c"), [(9.0, 9.6), (10.6, 11.2)]),
    "cryo": (M.ramp("#10222e", "#173244", "#1f455d", "#295a78", "#357195", "#4589b1", "#5aa2c9", "#78bbdc", "#9fd2ea"), [(10.0, 11.0)]),
    "emp": (M.ramp("#1c1430", "#271c44", "#33255a", "#402f71", "#4e3a88", "#5e489f", "#7059b5", "#866fc9", "#a08bdb"), [(10.0, 11.0)]),
}
#: The body's colour: olive drab for most; grey for chemical (as chemical rounds are painted).
BODY = {"chemical": M.painted(M.GREY_PAINT_RAMP, tone=0.5, wear=0.08)}


def shell(kind: str) -> None:
    """An 81 mm mortar bomb standing on its fins: a tapering tail cone, the body, an ogive nose and a fuze; the tail boom
    with its ring of propellant increments; four fins."""
    m = BlockModel(f"mortar_shell_{kind}", tex_size=(64, 64), kind="item", render_type="minecraft:cutout")
    m.open_ok = set()
    R = 2.0
    groups: dict[str, list[str]] = {}
    lips: dict[str, tuple[bool, bool]] = {}

    def add(group, names, top=True, bottom=False):
        groups.setdefault(group, []).extend(names)
        for n in names:
            lips[n] = (top, bottom)
        return names
    add("boom", W.section(m, "boom", 0.6, 0.6, 6.4, 4, caps=("down",), stagger="full"), False, True)
    add("charge", W.section(m, "charge", 1.25, 3.2, 5.0, W.sides_for(1.25), caps=("up", "down"), stagger="full"), True, True)
    # The tail cone: rings growing to the body (their bottoms show).
    for i, (r, y0, y1) in enumerate(((1.2, 6.0, 6.8), (1.55, 6.8, 7.6), (1.8, 7.6, 8.4))):
        add("tail", W.section(m, f"tail{i}", r, y0, y1, W.sides_for(r), caps=("down",), stagger="full"), False, True)
    add("body", W.section(m, "body", R, 8.4, 11.6, W.sides_for(R), caps=("down",), stagger="full"), False, True)
    rings, top, last = W.nose(m, "nose", R, 11.6, 3.0, "ogive", 4, min_r=0.6)
    add("nose", [n for ring_ in rings for n in ring_], True, False)
    fr = min(0.55, last - 0.12) if last else 0.55
    add("fuze", W.section(m, "fuze", round(fr, 3), top - 0.2, top + 0.9, 4, caps=("up",)), True, False)
    # Four fins on the boom.
    for axis in ("x", "z"):
        frm, to = ((8 - 2.6, 0.0, 7.85), (8 + 2.6, 3.0, 8.15)) if axis == "x" else ((7.85, 0.15, 8 - 2.6), (8.15, 3.0, 8 + 2.6))
        m.box(f"fin_{axis}", frm, to)
        groups.setdefault("fin", []).append(f"fin_{axis}")
    while True:
        try:
            m.layout()
            break
        except ValueError:
            m.tex_w *= 2
            m.tex_h = m.tex_w
    t = Tex(m.tex_w, m.tex_h)
    k = K.Kit(m, {}, seed=900 + sum(map(ord, kind)) % 97, ao_reach=1.2, turned=True)
    for n, (lit, base) in lips.items():
        W.round_faces(k.S, [n], lit, base, False)
    body = BODY.get(kind, M.painted(OD_RAMP, tone=0.55, wear=0.06))
    k.paint(t, groups["boom"], W.metal(W.GUNMETAL, 0.4))
    k.paint(t, groups["fin"], W.metal(W.GUNMETAL, 0.45))
    k.paint(t, groups["charge"], M.painted(M.ramp("#3a2c14", "#4c3a1b", "#5f4a24", "#735b2e", "#886d39", "#9c8046", "#b09456", "#c3a96a",
                                                   "#d4bd80"), tone=0.55, wear=0.04))
    k.paint(t, groups["tail"] + groups["body"] + groups["nose"], body)
    ramp, bands = BANDS[kind]
    band = M.painted(ramp, tone=0.6, wear=0.05)
    for y0, y1 in bands:
        k.overlay(t, groups["body"], lambda f, img, y0=y0, y1=y1: M.paste(img, k.on(f, band), (f.Y >= y0) & (f.Y < y1)) if f.d in SIDES else None)
    k.overlay(t, groups["body"], lambda f, img: W.stencil(f, img, [9.2], (222, 222, 210, 255), width=4, pattern="xx.x"))
    k.paint(t, groups["fuze"], W.metal(P.BRASS, 0.55))
    m.fit_display(gui_rotation=(0, 0, -45), gui_margin=0.5)
    lo, hi = m.bounds()
    c = [(lo[i] + hi[i]) / 2 for i in range(3)]

    def ctx(scale: float, base: tuple[float, float, float], rotation=(0, 0, 0)) -> dict:
        return {"rotation": list(rotation), "translation": [round(base[i] - (c[i] - 8) * scale, 3) for i in range(3)], "scale": [scale] * 3}
    m.display["ground"] = ctx(0.5, (0, 0, 0))
    m.display["fixed"] = ctx(0.85, (0, 0, 0))
    m.display["thirdperson_righthand"] = ctx(0.55, (0, 2.5, 1.5), (0, 90, 0))
    m.display["thirdperson_lefthand"] = ctx(0.55, (0, 2.5, 1.5), (0, 90, 0))
    m.display["firstperson_righthand"] = ctx(0.45, (1.5, 3.5, 0), (0, -25, 0))
    m.display["firstperson_lefthand"] = ctx(0.45, (1.5, 3.5, 0), (0, 155, 0))
    m.save(t)
    write_json(paths.ASSETS / "items" / f"mortar_shell_{kind}.json",
               {"model": {"type": "minecraft:model", "model": f"groundburst:item/mortar_shell_{kind}"}})


KINDS = ("he", "smoke", "incendiary", "chemical", "cryo", "emp")


def main() -> None:
    mortar()
    for kind in KINDS:
        shell(kind)
    print("artillery written")


if __name__ == "__main__":
    main()
