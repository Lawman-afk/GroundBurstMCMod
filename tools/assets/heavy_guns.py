"""Artillery (DESIGN.md §20.1): the 155 mm Howitzer and the Atomic Cannon (GeckoLib), and their shells (3D items).

- 155 mm Howitzer (after the M777, in firing position): the bottom carriage on its octagonal firing base, its wheels
  raised either side, two split trails spread back to spades dug into the ground; the top carriage on it (bone
  "traverse") with the trunnion cheeks, handwheels and the sight; the cradle under the barrel (bone "barrel", raised
  about the trunnions) with its recoil cylinders; the barrel itself (bone "recoil", running back as it fires): breech
  ring and block, the thick chamber section, the long tube and the double-baffle muzzle brake.
- Atomic Cannon (after the M65 "Atomic Annie", lowered to fire): a long box-girder carriage on the ground between its
  two tractors (the front one facing out ahead, the rear one out behind: cabs, bonnets, wheels), a turntable at its
  middle; the top carriage (bone "traverse"), the cradle with its two big recoil cylinders (bone "barrel") and the
  280 mm barrel (bone "recoil": breech, chamber, tube, a swelled muzzle).
- Shells (items, and the shell in flight): a long artillery shell standing on its base: a boat-tail, a copper driving
  band, the body in its kind's paint (HE olive with a yellow band, incendiary olive with a red band, nuclear white with
  yellow and black bands), an ogive nose and a fuze.

BarrelGunBlockEntity and GBArtillery have the same figures (trunnion height, barrel length). Pixels: x right, y up, z
back from the block's bottom middle; front = north. Round parts along the barrel are built upright in a bone turned to
lie along it ("tube"); the wheels likewise, turned to lie across.

    tools\\.venv\\Scripts\\python.exe tools\\assets\\heavy_guns.py
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
import artillery as A  # noqa: E402
import particle_cannon as PC  # noqa: E402
import scud_storm as SS  # noqa: E402
import warheads as W  # noqa: E402

SIDES, ALL = ("north", "south", "east", "west"), ("north", "south", "east", "west", "up", "down")
OD = A.OD
OD_DARK = M.painted(A.OD_RAMP, tone=0.42, wear=0.16, streaks=0.05)
STEEL = M.steel(tone=0.5, brushed=0.06)
DARK = M.steel(tone=0.28, brushed=0.04)
RUBBER = M.rubber(tone=0.4)
GLASS = PC.GLASS
COPPER = M.Material(ramp=M.COPPER_RAMP, tone=0.6, mottle=0.02, grain=0.02, dither=0.4, edge_light=0.16, edge_dark=0.1, ao=0.35, grime=0.0,
                    dust=0.0, brushed=0.05)


def tube_bone(g: GeoModel, name: str, pivot, parent: str) -> str:
    """A bone turned to lie along the barrel: what is built upright in it points out over the front (-z)."""
    g.bone(name, pivot=pivot, parent=parent, rotation=(-90.0, 0.0, 0.0))
    return name


def round_along(g: GeoModel, p: SS.Parts, key: str, name: str, r: float, a: float, b: float, axis_y: float, bone: str,
                caps=("up", "down"), stagger=None) -> list[str]:
    """A round section of the barrel from {a} to {b} px in front of the trunnions (negative: behind), built upright in
    the barrel's tube bone (whose pivot is the trunnion: y there is axis_y + distance)."""
    names = SS.ring(g, name, r, axis_y + a, axis_y + b, 0.0, 0.0, bone, caps=caps, stagger=stagger)
    p.add(key, names)
    return names


def wheel(g: GeoModel, p: SS.Parts, name: str, x: float, y: float, z: float, r: float, width: float, parent: str, side: int) -> None:
    """A wheel on an axle along x: a tyre and a hub, built upright in a bone turned to lie across."""
    g.bone(name, pivot=(x, y, z), parent=parent, rotation=(0.0, 0.0, 90.0 * side))
    p.add("tyre", SS.ring(g, f"{name}_tyre", r, y - width / 2, y + width / 2, x, z, name, caps=("up", "down")))
    p.add("hub", SS.ring(g, f"{name}_hub", r * 0.42, y - width / 2 - 0.6, y + width / 2 + 0.6, x, z, name, caps=("up", "down")))


# =========================================================================================================================
# The 155 mm Howitzer
# =========================================================================================================================
H_TRUNNION, H_MUZZLE, H_BREECH = 21.0, 72.0, 12.0
H_REST = 10.0


def build_howitzer() -> tuple[GeoModel, SS.Parts]:
    g = GeoModel("howitzer", tex_size=(256, 256))
    SS.ROUND.clear()
    p = SS.Parts()
    g.bone("frame")
    # --- the bottom carriage, its firing base, its wheels and trails (fixed) ----------------------------------------------
    base = PC.prism(g, "firing_base", 7.5, 0, 2.0, 0, -6, "frame", sides=8, caps=("up",))
    SS.ROUND.extend(base)
    p.add("base", base)
    g.box("carriage", (-8, 2, -13), (8, 8, 9), faces=ALL, bone="frame")
    g.box("carriage_nose", (-6, 2, -17), (6, 6.5, -13), faces=("north", "east", "west", "up"), bone="frame")
    p.add("carriage", "carriage", "carriage_nose")
    for side, sx in (("w", -1), ("e", 1)):
        g.box(f"axle_{side}", (sx * 8 if sx > 0 else -11, 4.5, -4), (11 if sx > 0 else -8, 7.5, -1), faces=("north", "south", "up", "down"), bone="frame")
        p.add("axle", f"axle_{side}")
        wheel(g, p, f"wheel_{side}", sx * 12.5, 9.0, -2.5, 6.0, 3.0, "frame", -sx)
        # The trail: a box girder from the carriage's back corner, spread out and sloping down to its spade.
        tb = f"trail_{side}"
        g.bone(tb, pivot=(sx * 5.0, 5.5, 8.0), parent="frame", rotation=(7.0, 28.0 * sx, 0.0))
        g.box(f"{tb}_beam", (sx * 5.0 - 2, 3.5, 8.0), (sx * 5.0 + 2, 7.5, 64.0), faces=ALL, bone=tb)
        g.box(f"{tb}_spade", (sx * 5.0 - 5, -2.0, 62.0), (sx * 5.0 + 5, 6.0, 63.5), faces=ALL, bone=tb)
        g.box(f"{tb}_lug", (sx * 5.0 - 1.2, 7.5, 56.0), (sx * 5.0 + 1.2, 9.0, 60.0), faces=("north", "south", "east", "west", "up"), bone=tb)
        p.add("trail", f"{tb}_beam")
        p.add("spade", f"{tb}_spade", f"{tb}_lug")
    # --- the top carriage (turned) ------------------------------------------------------------------------------------
    g.bone("traverse", pivot=(0, 8, 0), parent="frame")
    g.box("saddle", (-7, 8, -7), (7, 13, 8), faces=("north", "south", "east", "west", "up"), bone="traverse")
    for side, sx in (("w", -1), ("e", 1)):
        x0, x1 = (-7.0, -5.0) if sx < 0 else (5.0, 7.0)
        g.box(f"cheek_{side}", (x0, 13, -5), (x1, 24.5, 6), faces=ALL, bone="traverse")
        hx0, hx1 = (-8.2, -7.0) if sx < 0 else (7.0, 8.2)
        g.box(f"trunnion_{side}", (hx0, H_TRUNNION - 1.5, -1.5), (hx1, H_TRUNNION + 1.5, 1.5), faces=ALL, bone="traverse")
        p.add("cheek", f"cheek_{side}")
        p.add("trunnion", f"trunnion_{side}")
    g.box("handwheel_e", (8.2, 15, 1.5), (8.8, 19, 5.5), faces=ALL, bone="traverse")
    g.box("handwheel_w", (-8.8, 15, 1.5), (-8.2, 19, 5.5), faces=ALL, bone="traverse")
    p.add("handwheel", "handwheel_e", "handwheel_w")
    g.box("sight_post", (-9.6, 19, -3.5), (-8.2, 25, -2.0), faces=ALL, bone="traverse")
    g.box("sight", (-11.2, 25, -5), (-8.0, 27.4, 0.5), faces=ALL, bone="traverse")
    p.add("sight", "sight_post", "sight")
    # --- the cradle (raised) ----------------------------------------------------------------------------------------------
    g.bone("barrel", pivot=(0, H_TRUNNION, 0), parent="traverse")
    g.box("cradle", (-3.4, H_TRUNNION - 4.4, -30), (3.4, H_TRUNNION - 1.6, 8), faces=ALL, bone="barrel")
    g.box("cradle_ring", (-3.6, H_TRUNNION - 3.4, -32), (3.6, H_TRUNNION + 3.4, -29), faces=ALL, bone="barrel")
    for side, sx in (("w", -1), ("e", 1)):
        g.box(f"recoil_cyl_{side}", (sx * 2.2 - 1.2, H_TRUNNION - 6.6, -27), (sx * 2.2 + 1.2, H_TRUNNION - 4.4, 6), faces=ALL, bone="barrel")
        p.add("cylinder", f"recoil_cyl_{side}")
    p.add("cradle", "cradle", "cradle_ring")
    # --- the barrel (runs back as it fires) ------------------------------------------------------------------------------
    g.bone("recoil", pivot=(0, H_TRUNNION, 0), parent="barrel")
    tube = tube_bone(g, "tube", (0, H_TRUNNION, 0), "recoil")
    round_along(g, p, "breech_ring", "breech_ring", 3.2, -H_BREECH, -5, H_TRUNNION, tube, stagger="full")
    round_along(g, p, "chamber", "chamber", 2.6, -5, 20, H_TRUNNION, tube, caps=("up",))
    round_along(g, p, "tube", "barrel_tube", 1.9, 20, H_MUZZLE - 7, H_TRUNNION, tube, caps=())
    round_along(g, p, "brake", "brake", 2.7, H_MUZZLE - 7, H_MUZZLE, H_TRUNNION, tube, caps=("up", "down"), stagger="full")
    g.box("breech_block", (-2.4, H_TRUNNION - 2.4, H_BREECH), (2.4, H_TRUNNION + 2.4, H_BREECH + 2.2), faces=ALL, bone="recoil")
    g.box("breech_lever", (2.4, H_TRUNNION - 0.6, H_BREECH + 0.4), (5.0, H_TRUNNION + 0.6, H_BREECH + 1.6), faces=ALL, bone="recoil")
    p.add("breech", "breech_block", "breech_lever")
    while True:
        try:
            g.layout()
            break
        except ValueError:
            g.tex_w *= 2
            g.tex_h = g.tex_w
    return g, p


def paint_howitzer(g: GeoModel, p: SS.Parts) -> Tex:
    """Olive drab carriage, trails and barrel; dark steel at the breech and the muzzle brake (its baffles' ports dark),
    bare steel trunnions and handwheels, black tyres on steel hubs, the spades dark and earthy at their foot."""
    k = K.Kit(g, {}, seed=941, ao_reach=2.5, turned=True)
    W.round_faces(k.S, SS.ROUND, top=True, bottom=False, foot=True)
    S = k.S
    tex = Tex(g.tex_w, g.tex_h)
    S.paint(tex, p["base"], M.painted(M.ARMOUR_RAMP, tone=0.38, wear=0.25, rust=0.05, grime=0.14, dust=0.08))
    S.paint(tex, p["carriage"] + p["trail"] + p["cheek"] + p["cradle"], OD)
    S.overlay(tex, p["trail"], lambda f, img: M.rivets(f, img, every=4, inset=1) if f.d in ("east", "west") else None)
    S.overlay(tex, p["carriage"], lambda f, img: M.seams(f, img, every=8, axis="u", light=False) if f.d in SIDES else None)
    S.paint(tex, p["axle"] + p["cylinder"], OD_DARK)
    S.paint(tex, p["tyre"], RUBBER)
    S.paint(tex, p["hub"], STEEL)
    S.paint(tex, p["spade"], M.painted(M.ARMOUR_RAMP, tone=0.32, wear=0.3, rust=0.08, grime=0.2, dust=0.2))
    S.paint(tex, p["trunnion"] + p["handwheel"], STEEL)
    S.paint(tex, p["sight"], A.BLACK)
    S.paint(tex, p["saddle"] if p["saddle"] else ["saddle"], OD)
    S.paint(tex, p["breech_ring"] + p["breech"], DARK)
    S.paint(tex, p["chamber"] + p["tube"], OD)
    S.paint(tex, p["brake"], DARK)

    def ports(f, img):                                         # the brake's two baffles: dark ports on its sides
        if f.d in SIDES:
            y = f.Y - H_TRUNNION
            on = ((y >= H_MUZZLE - 6) & (y < H_MUZZLE - 4.5)) | ((y >= H_MUZZLE - 3) & (y < H_MUZZLE - 1.5))
            M.darken(img, on, 0.35)
        if f.d == "up":
            img[np.hypot(f.X, f.Z) < 1.6, :3] = P.BLACK_PAINT.deep[:3]
    S.overlay(tex, p["brake"], ports)
    return tex


# =========================================================================================================================
# The Atomic Cannon
# =========================================================================================================================
A_TRUNNION, A_MUZZLE, A_BREECH = 30.0, 120.0, 16.0
A_REST = 5.0


def tractor(g: GeoModel, p: SS.Parts, name: str, dz: int) -> None:
    """A tractor at one end of the carriage, facing out ({dz} -1: ahead, +1: behind): its cab, its bonnet and its
    bumper, a pair of wheels under each."""
    z0 = 86 * dz
    def zr(a, b):
        lo, hi = sorted((z0 + a * dz, z0 + b * dz))
        return lo, hi
    c0, c1 = zr(0, 24)
    g.box(f"{name}_cab", (-10, 6, c0), (10, 27, c1), faces=ALL, bone="frame")
    h0, h1 = zr(24, 42)
    g.box(f"{name}_bonnet", (-8, 6, h0), (8, 19, h1), faces=ALL, bone="frame")
    b0, b1 = zr(42, 44)
    g.box(f"{name}_bumper", (-10, 6, b0), (10, 10, b1), faces=ALL, bone="frame")
    g.box(f"{name}_grille", (-6, 10, b0), (6, 18, b1), faces=ALL, bone="frame")
    s0, s1 = zr(-6, 0)
    g.box(f"{name}_coupling", (-6, 4, s0), (6, 12, s1), faces=ALL, bone="frame")
    g.box(f"{name}_stack", (8.6, 19, c0 + 2 if dz < 0 else c1 - 4), (10.6, 33, c0 + 4 if dz < 0 else c1 - 2), faces=ALL, bone="frame")
    p.add("cab", f"{name}_cab")
    p.add("bonnet", f"{name}_bonnet")
    p.add("bumper", f"{name}_bumper", f"{name}_coupling")
    p.add("grille", f"{name}_grille")
    p.add("stack", f"{name}_stack")
    for i, zz in enumerate((8, 32)):
        wz = z0 + zz * dz
        for side, sx in (("w", -1), ("e", 1)):
            wheel(g, p, f"{name}_wheel{i}{side}", sx * 11.0, 6.0, wz, 6.0, 4.0, "frame", -sx)


def build_atomic() -> tuple[GeoModel, SS.Parts]:
    g = GeoModel("atomic_cannon", tex_size=(512, 512))
    SS.ROUND.clear()
    p = SS.Parts()
    g.bone("frame")
    # --- the carriage girder on the ground, its turntable, its tractors (fixed) ----------------------------------------
    g.box("girder", (-9, 0, -84), (9, 14, 84), faces=ALL, bone="frame")
    g.box("girder_deck", (-11, 14, -40), (11, 16, 40), faces=ALL, bone="frame")
    p.add("girder", "girder", "girder_deck")
    turntable = PC.prism(g, "turntable", 12.5, 16, 18, 0, 0, "frame", sides=16, caps=("up",))
    SS.ROUND.extend(turntable)
    p.add("turntable", turntable)
    tractor(g, p, "front", -1)
    tractor(g, p, "rear", 1)
    # --- the top carriage (turned) ------------------------------------------------------------------------------------
    g.bone("traverse", pivot=(0, 18, 0), parent="frame")
    g.box("saddle", (-11, 18, -18), (11, 24, 20), faces=("north", "south", "east", "west", "up"), bone="traverse")
    for side, sx in (("w", -1), ("e", 1)):
        x0, x1 = (-10.0, -7.0) if sx < 0 else (7.0, 10.0)
        g.box(f"cheek_{side}", (x0, 24, -10), (x1, 35, 10), faces=ALL, bone="traverse")
        hx0, hx1 = (-11.6, -10.0) if sx < 0 else (10.0, 11.6)
        g.box(f"trunnion_{side}", (hx0, A_TRUNNION - 2.2, -2.2), (hx1, A_TRUNNION + 2.2, 2.2), faces=ALL, bone="traverse")
        p.add("cheek", f"cheek_{side}")
        p.add("trunnion", f"trunnion_{side}")
    g.box("layer_seat", (-14, 24, 10), (-10, 27, 14), faces=ALL, bone="traverse")
    g.box("handwheel", (-12.4, 26, 2), (-11.6, 31, 7), faces=ALL, bone="traverse")
    p.add("handwheel", "handwheel", "layer_seat")
    # --- the cradle and its two big recoil cylinders (raised) -------------------------------------------------------------
    g.bone("barrel", pivot=(0, A_TRUNNION, 0), parent="traverse")
    g.box("cradle", (-5.4, A_TRUNNION - 6.6, -52), (5.4, A_TRUNNION - 3.0, 14), faces=ALL, bone="barrel")
    g.box("cradle_ring", (-5.6, A_TRUNNION - 5.0, -55), (5.6, A_TRUNNION + 5.0, -51), faces=ALL, bone="barrel")
    for side, sx in (("w", -1), ("e", 1)):
        g.box(f"recoil_cyl_{side}", (sx * 3.4 - 1.9, A_TRUNNION + 3.6, -48), (sx * 3.4 + 1.9, A_TRUNNION + 7.4, 10), faces=ALL, bone="barrel")
        p.add("cylinder", f"recoil_cyl_{side}")
    p.add("cradle", "cradle", "cradle_ring")
    # --- the barrel (runs back as it fires) ------------------------------------------------------------------------------
    g.bone("recoil", pivot=(0, A_TRUNNION, 0), parent="barrel")
    tube = tube_bone(g, "tube", (0, A_TRUNNION, 0), "recoil")
    round_along(g, p, "breech_ring", "breech_ring", 4.8, -A_BREECH, -7, A_TRUNNION, tube, stagger="full")
    round_along(g, p, "chamber", "chamber", 4.1, -7, 36, A_TRUNNION, tube, caps=("up",))
    round_along(g, p, "tube", "barrel_tube", 3.2, 36, A_MUZZLE - 8, A_TRUNNION, tube, caps=())
    round_along(g, p, "swell", "swell", 3.7, A_MUZZLE - 8, A_MUZZLE, A_TRUNNION, tube, caps=("up", "down"), stagger="full")
    g.box("breech_block", (-3.6, A_TRUNNION - 3.6, A_BREECH), (3.6, A_TRUNNION + 3.6, A_BREECH + 3.0), faces=ALL, bone="recoil")
    p.add("breech", "breech_block")
    while True:
        try:
            g.layout()
            break
        except ValueError:
            g.tex_w *= 2
            g.tex_h = g.tex_w
    return g, p


def paint_atomic(g: GeoModel, p: SS.Parts) -> Tex:
    """Olive drab carriage, tractors and barrel; dark steel at the breech and the muzzle; black glass in the cabs; steel
    bumpers and grilles; black tyres on steel hubs; the turntable bare steel; white stars on the cab doors."""
    k = K.Kit(g, {}, seed=947, ao_reach=3.0, turned=True)
    W.round_faces(k.S, SS.ROUND, top=True, bottom=False, foot=True)
    S = k.S
    tex = Tex(g.tex_w, g.tex_h)
    S.paint(tex, p["girder"] + p["cheek"] + p["cradle"] + p["cab"] + p["bonnet"], OD)
    S.overlay(tex, p["girder"], lambda f, img: M.seams(f, img, every=16, axis="u", light=False) if f.d in ("east", "west") else None)
    S.overlay(tex, p["girder"], lambda f, img: M.rivets(f, img, every=4, inset=1) if f.d in ("east", "west") else None)
    S.paint(tex, ["saddle"], OD)
    S.paint(tex, p["turntable"] + p["trunnion"] + p["handwheel"], STEEL)
    S.paint(tex, p["cylinder"], OD_DARK)
    S.paint(tex, p["bumper"] + p["grille"], STEEL)
    S.overlay(tex, p["grille"], lambda f, img: M.darken(img, (np.floor(f.Y) % 2) == 0, 0.55) if f.d in ("north", "south") else None)
    S.paint(tex, p["stack"], DARK)
    S.paint(tex, p["tyre"], RUBBER)
    S.paint(tex, p["hub"], STEEL)

    def windows(f, img):                                       # the cab's glass: its front and back, and its doors' upper half
        if f.Y.min() is None:
            return
        upper = f.Y >= 16
        if f.d in ("north", "south"):
            M.paste(img, k.on(f, GLASS), upper & (np.abs(f.X) < 8.5))
        elif f.d in ("east", "west"):
            M.paste(img, k.on(f, GLASS), upper & (f.Y < 25))
    S.overlay(tex, p["cab"], windows)

    def star(f, img):                                          # a white star on each door, under its window
        if f.d not in ("east", "west"):
            return
        zc = float(np.mean(f.Z))
        u = f.Z - zc
        v = f.Y - 12
        r = np.hypot(u, v)
        a = np.arctan2(v, u)
        edge = 3.2 * (0.55 + 0.45 * np.cos(5 * (a - math.pi / 2)) ** 2)
        img[(r < edge) & (r < 3.4), :3] = (214, 214, 204)
    S.overlay(tex, p["cab"], star)
    S.paint(tex, p["breech_ring"] + p["breech"], DARK)
    S.paint(tex, p["chamber"] + p["tube"], OD)
    S.paint(tex, p["swell"], DARK)
    S.overlay(tex, p["swell"], lambda f, img: img.__setitem__((np.hypot(f.X, f.Z) < 2.4) & (f.d == "up"), (*P.BLACK_PAINT.deep[:3], 255))
              if f.d == "up" else None)
    return tex


# =========================================================================================================================
def animations(rest: float) -> list[Animation]:
    """Idle: nothing (the block entity lays it). Rest: its barrel raised as it rests (the previews' pose)."""
    idle = Animation("idle", 0.05, loop="hold_on_last_frame")
    idle.move("frame", {0: (0, 0, 0)})
    rest_anim = Animation("rest", 0.05, loop="hold_on_last_frame")
    rest_anim.rotate("barrel", {0: (rest, 0, 0)})
    return [idle, rest_anim]


def item_files(name: str, display: dict) -> None:
    write_json(paths.MODELS / "block" / f"{name}.json", {"textures": {"particle": "groundburst:block/machine_particle"}})
    write_json(paths.ASSETS / "blockstates" / f"{name}.json",
               {"variants": {f"facing={f}": {"model": f"groundburst:block/{name}"} for f in ("north", "east", "south", "west")}})
    write_json(paths.MODELS / "item" / f"{name}.json", {"parent": "minecraft:block/block", "textures": {"particle": "groundburst:block/machine_particle"},
                                                       "display": display})
    write_json(paths.ASSETS / "items" / f"{name}.json",
               {"model": {"type": "minecraft:special", "base": f"groundburst:item/{name}", "model": {"type": "geckolib:geckolib"}}})


def display(scale: float, lift: float) -> dict:
    return {
        "gui": {"rotation": [25, 145, 0], "translation": [0, lift, 0], "scale": [scale] * 3},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 1, 0], "scale": [scale * 0.5] * 3},
        "fixed": {"rotation": [0, 0, 0], "translation": [0, lift, 0], "scale": [scale] * 3},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 1.0, 0], "scale": [scale * 0.6] * 3},
        "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 1.0, 0], "scale": [scale * 0.6] * 3},
        "firstperson_righthand": {"rotation": [0, 135, 0], "translation": [0, 0, 0], "scale": [scale * 0.6] * 3},
        "firstperson_lefthand": {"rotation": [0, 135, 0], "translation": [0, 0, 0], "scale": [scale * 0.6] * 3},
    }


def howitzer() -> None:
    g, p = build_howitzer()
    for path in g.save(paint_howitzer(g, p)):
        print(path)
    print(save_animations("howitzer", animations(H_REST)))
    item_files("howitzer", display(0.16, 0.0))


def atomic_cannon() -> None:
    g, p = build_atomic()
    for path in g.save(paint_atomic(g, p)):
        print(path)
    print(save_animations("atomic_cannon", animations(A_REST)))
    item_files("atomic_cannon", display(0.085, 0.0))


# =========================================================================================================================
# The shells
# =========================================================================================================================
#: Each kind's body and bands: (body material, [(ramp, y0, y1)]).
BIG = {
    "he": (M.painted(A.OD_RAMP, tone=0.55, wear=0.06), [(M.YELLOW_PAINT_RAMP, 9.5, 10.5)]),
    "incendiary": (M.painted(A.OD_RAMP, tone=0.55, wear=0.06), [(M.RED_PAINT_RAMP, 9.5, 10.5)]),
    "nuclear": (M.white_paint(tone=0.78, wear=0.04), [(M.YELLOW_PAINT_RAMP, 9.0, 10.0), (M.BLACK_RAMP, 10.0, 11.0)]),
}


def big_shell(calibre: str, kind: str) -> None:
    """A long artillery shell standing on its base: a boat-tail, the copper driving band, the body, an ogive nose and a
    fuze; its kind's paint and bands."""
    name = f"{calibre}_shell_{kind}"
    m = BlockModel(name, tex_size=(64, 64), kind="item", render_type="minecraft:cutout")
    m.open_ok = set()
    R = 2.4 if calibre == "atomic" else 2.0
    groups: dict[str, list[str]] = {}
    lips: dict[str, tuple[bool, bool]] = {}

    def add(group, names, top=True, bottom=False):
        groups.setdefault(group, []).extend(names)
        for n in names:
            lips[n] = (top, bottom)
    add("tail", W.section(m, "tail", round(R - 0.4, 3), 0.0, 1.2, W.sides_for(R - 0.4), caps=("down",), stagger="full"), False, True)
    add("band", W.section(m, "band", round(R + 0.25, 3), 1.2, 2.2, W.sides_for(R + 0.25), caps=("up", "down"), stagger="full"), True, True)
    add("body", W.section(m, "body", R, 2.2, 10.8, W.sides_for(R), caps=(), stagger="full"), False, False)
    rings, top, last = W.nose(m, "nose", R, 10.8, 4.0, "ogive", 4, min_r=0.6)
    add("nose", [n for ring_ in rings for n in ring_], True, False)
    fr = min(0.6, last - 0.12) if last else 0.6
    add("fuze", W.section(m, "fuze", round(fr, 3), top - 0.2, top + 1.0, 4, caps=("up",)), True, False)
    while True:
        try:
            m.layout()
            break
        except ValueError:
            m.tex_w *= 2
            m.tex_h = m.tex_w
    t = Tex(m.tex_w, m.tex_h)
    k = K.Kit(m, {}, seed=950 + sum(map(ord, name)) % 97, ao_reach=1.2, turned=True)
    for n, (lit, base) in lips.items():
        W.round_faces(k.S, [n], lit, base, False)
    body, bands = BIG[kind]
    k.paint(t, groups["tail"], W.metal(W.GUNMETAL, 0.4))
    k.paint(t, groups["band"], COPPER)
    k.paint(t, groups["body"] + groups["nose"], body)
    for ramp, y0, y1 in bands:
        band = M.painted(ramp, tone=0.6, wear=0.05)
        k.overlay(t, groups["body"], lambda f, img, y0=y0, y1=y1, band=band: M.paste(img, k.on(f, band), (f.Y >= y0) & (f.Y < y1))
                  if f.d in SIDES else None)
    k.overlay(t, groups["body"], lambda f, img: W.stencil(f, img, [6.2, 4.6], (222, 222, 210, 255) if kind != "nuclear" else (30, 30, 30, 255),
                                                       width=4, pattern="xx.x"))
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
    write_json(paths.ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": f"groundburst:item/{name}"}})


def main() -> None:
    howitzer()
    atomic_cannon()
    for calibre, kinds in (("howitzer", ("he", "incendiary")), ("atomic", ("he", "incendiary", "nuclear"))):
        for kind in kinds:
            big_shell(calibre, kind)
    print("heavy guns written")


if __name__ == "__main__":
    main()
