"""The Dead Hand (DESIGN.md §17.17), after the Soviet Perimeter system's command module: a squat module of hazard-banded
concrete with its console set in its front, the sensor mast on its roof and the command rocket's tube in its back corner.

- Formed (GeckoLib), 3 wide, 2 deep and 2 high, the controller in the middle of the front (front = north):
  * the module: concrete in formwork courses on a hazard band, its roof slab overhanging a little, banded round its edge;
  * the console on its front: a steel plate with the little screen (the block entity's renderer writes its state on it:
    SAFE, WATCHING, the countdown, FIRED), the status lamps ("lamp_safe" green, "lamp_watch" amber, "lamp_alert" red,
    "lamp_fired" red: each lit in its own bone), the two arming keys ("key_l", "key_r", turned while it is armed) on a
    hazard-striped guard, the DEAD HAND plate;
  * the command rocket's tube rising through the module's back-right corner (as you face it), its mouth a ring a few
    pixels proud of the roof; its lid ("lid") hinged at the back, swung up and over by the block entity as it fires; the
    rocket's nose ("rocket") in the tube while one is loaded;
  * the sensor mast on the roof's front-left corner: a light-flash sensor's dome at its top with its four lenses, a
    radiation probe and an overpressure gauge on arms, the command link's whip antenna and its red light ("mast_light"),
    its cable down the module's side;
  * the back: the Energy Port's plate.
- Controller (block); the Command Rocket (item); the spawner.

    tools\\.venv\\Scripts\\python.exe tools\\assets\\dead_hand.py
"""
from __future__ import annotations

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
from gb.model import write_json  # noqa: E402
from gb.tex import Tex  # noqa: E402
import assemblers as A  # noqa: E402
import command_room as CR  # noqa: E402
import multiblock_tools as MT  # noqa: E402
import scud_storm as SS  # noqa: E402
import structures as ST  # noqa: E402
import warheads as W  # noqa: E402

NAME = "dead_hand"
ALL, SIDES = SS.ALL, SS.SIDES
FRONT_FACES = ("north", "east", "west", "up", "down")
#: The module: its sides, front and back, its walls' top, the roof slab's top.
X0, X1, Z0, Z1, WALL, ROOF = -24.0, 24.0, -8.0, 24.0, 30.0, 32.0
#: The tube: its middle (the back-right cell as you face it: west, back), its radius, how far its mouth stands proud.
TUBE = (-16.0, 16.0)
TUBE_R, TUBE_TOP = 6.2, 36.0
#: The console's plate on the front, the screen's bezel on it, the lamps (x, colour key), the keys.
CONSOLE = (-7.0, 4.0, 7.0, 27.0)
CZ = Z0 - 1.2
SCREEN = (-4.5, 19.0, 4.5, 25.0)
#: (Read left to right by someone facing the console: their left is the model's +x.)
LAMPS = (("safe", 4.5), ("watch", 1.5), ("alert", -1.5), ("fired", -4.5))
LAMP_Y = 16.5
KEYS = {"key_l": 4.0, "key_r": -4.0}
KEY_Y = 10.5
#: The mast: its middle on the roof's front-left cell (east, front), its top.
MAST = (16.0, 0.0)
MAST_TOP = 52.0
LAMP_ON = {"safe": (90, 255, 110), "watch": (255, 176, 40), "alert": (255, 52, 36), "fired": (255, 52, 36)}
LAMP_OFF = {"safe": (20, 60, 26), "watch": (72, 50, 14), "alert": (70, 16, 12), "fired": (70, 16, 12)}
CONSOLE_RAMP = M.ramp("#1d231d", "#263026", "#303c30", "#3b493a", "#475646", "#556553", "#667663", "#7b8b77", "#94a38f")


def build() -> tuple[GeoModel, dict]:
    g = GeoModel(NAME, tex_size=(256, 256))
    parts: dict[str, list[str]] = {}
    SS.ROUND.clear()
    g.bone("frame")
    F = "frame"

    def box(group, n, frm, to, bone=F, faces=ALL, rotation=None):
        g.box(n, frm, to, faces=faces, bone=bone, rotation=rotation)
        parts.setdefault(group, []).append(n)

    def sec(group, n, r, a0, a1, bone=F, axis="y", c=(0.0, 0.0), **kw):
        names = W.round_section(SS.OnBone(g, bone), n, r, a0, a1, axis=axis, c=c, **kw)
        SS.ROUND.extend(names)
        parts.setdefault(group, []).extend(names)
        return names

    def ring(group, n, r_in, r_out, a0, a1, bone=F, axis="y", c=(0.0, 0.0), **kw):
        names = W.round_ring(SS.OnBone(g, bone), n, r_in, r_out, a0, a1, axis=axis, c=c, **kw)
        SS.ROUND.extend(names)
        parts.setdefault(group, []).extend(names)
        return names

    # --- the module -----------------------------------------------------------------------------------------------------
    box("module", "module", (X0, 0, Z0), (X1, WALL, Z1), faces=SIDES)
    box("roof", "roof", (X0 - 0.5, WALL, Z0 - 0.5), (X1 + 0.5, ROOF, Z1 + 0.5), faces=ALL)
    # --- the console on its front ----------------------------------------------------------------------------------------
    cx0, cy0, cx1, cy1 = CONSOLE
    box("console", "console", (cx0, cy0, CZ), (cx1, cy1, Z0), faces=FRONT_FACES)
    sx0, sy0, sx1, sy1 = SCREEN
    box("screen", "screen", (sx0, sy0, CZ - 0.6), (sx1, sy1, CZ), faces=FRONT_FACES)
    for which, x in LAMPS:
        box("lamp_dark", f"lamp_{which}_dark", (x - 0.9, LAMP_Y - 0.9, CZ - 0.3), (x + 0.9, LAMP_Y + 0.9, CZ), faces=FRONT_FACES)
        g.bone(f"lamp_{which}", pivot=(x, LAMP_Y, CZ), parent=F)
        box(f"lamp_{which}", f"lamp_{which}_lit", (x - 1.05, LAMP_Y - 1.05, CZ - 0.5), (x + 1.05, LAMP_Y + 1.05, CZ - 0.05),
            bone=f"lamp_{which}", faces=FRONT_FACES)
    box("guard", "guard", (-6.4, 7.6, CZ - 0.3), (6.4, 13.4, CZ), faces=FRONT_FACES)
    for bone, x in KEYS.items():
        sec("collar", f"collar_{bone}", 1.3, CZ - 0.9, CZ - 0.3, axis="z", c=(x, KEY_Y), lo=0.0)
        g.bone(bone, pivot=(x, KEY_Y, CZ - 0.9), parent=F)
        box("key", f"{bone}_bit", (x - 0.35, KEY_Y - 0.9, CZ - 2.0), (x + 0.35, KEY_Y + 0.9, CZ - 0.9), bone=bone, faces=ALL)
        box("key", f"{bone}_bow", (x - 1.0, KEY_Y - 0.3, CZ - 2.5), (x + 1.0, KEY_Y + 0.3, CZ - 2.0), bone=bone, faces=ALL)
    box("plate", "plate", (-5.5, 25.6, CZ - 0.25), (5.5, 26.8, CZ), faces=FRONT_FACES)
    # A warning placard on the wall beside the console (as you face it, its left).
    box("placard", "placard", (9.5, 13.0, Z0 - 0.3), (18.5, 21.0, Z0), faces=FRONT_FACES)
    # A mushroom vent on the roof.
    sec("vent", "vent_pipe", 0.9, ROOF, ROOF + 2.6, c=(2.0, 15.0))
    sec("vent", "vent_cap", 2.0, ROOF + 2.6, ROOF + 3.4, c=(2.0, 15.0), lo=0.9, hi=0.0)
    # --- the command rocket's tube ---------------------------------------------------------------------------------------
    tx, tz = TUBE
    # Its wall standing proud of the roof (the rest is in the concrete), its mouth's floor deep inside it; its lid.
    ring("tube", "tube_wall", TUBE_R - 1.0, TUBE_R, WALL, TUBE_TOP, c=(tx, tz), faces=("north", "south", "east", "west", "up"))
    sec("tube_floor", "tube_floor", TUBE_R - 0.9, WALL, 33.2, c=(tx, tz), hi=0.0)
    hinge_z = tz + TUBE_R + 0.2
    box("hinge", "hinge", (tx - 2.0, TUBE_TOP - 1.0, hinge_z - 0.6), (tx + 2.0, TUBE_TOP + 0.6, hinge_z + 0.8), faces=ALL)
    g.bone("lid", pivot=(tx, TUBE_TOP + 0.5, hinge_z), parent=F)
    sec("lid", "lid_plate", TUBE_R + 0.3, TUBE_TOP, TUBE_TOP + 1.0, bone="lid", c=(tx, tz), lo=0.0, hi=0.0)
    sec("lid_boss", "lid_boss", 2.2, TUBE_TOP + 1.0, TUBE_TOP + 1.6, bone="lid", c=(tx, tz), hi=0.0)
    # The rocket's nose in the tube, ring by ring from its shoulder to its tip.
    g.bone("rocket", pivot=(tx, 33.2, tz), parent=F)
    y = 33.2
    for i, (r, h) in enumerate(((3.6, 0.8), (3.0, 0.7), (2.3, 0.6), (1.5, 0.5), (0.7, 0.35))):
        sec("rocket", f"nose{i}", r, y, y + h, bone="rocket", c=(tx, tz), hi=0.0 if i == 4 else r - 0.6 if i < 4 else 0.0)
        y += h
    # --- the sensor mast -------------------------------------------------------------------------------------------------
    mx, mz = MAST
    box("pad", "pad", (mx - 4.0, ROOF, mz - 4.0), (mx + 4.0, ROOF + 1.4, mz + 4.0), faces=SIDES + ("up",))
    sec("mast", "mast", 0.9, ROOF + 1.4, MAST_TOP, c=(mx, mz))
    # The flash sensor's dome and its four lenses.
    sec("dome_base", "dome_base", 1.9, MAST_TOP, MAST_TOP + 0.8, c=(mx, mz), lo=0.9, hi=1.5)
    sec("dome", "dome0", 1.5, MAST_TOP + 0.8, MAST_TOP + 1.5, c=(mx, mz), hi=1.0)
    sec("dome", "dome1", 1.0, MAST_TOP + 1.5, MAST_TOP + 2.0, c=(mx, mz), hi=0.0)
    for j, (dx, dz) in enumerate(((0, -1), (1, 0), (0, 1), (-1, 0))):
        lx, lz = mx + dx * 1.9, mz + dz * 1.9
        box("lens", f"lens{j}", (lx - 0.45 - abs(dz) * 0.0, MAST_TOP + 0.15, lz - 0.45), (lx + 0.45, MAST_TOP + 0.65, lz + 0.45), faces=ALL)
    # The radiation probe out east, the overpressure gauge out west.
    box("arm", "probe_arm", (mx + 0.9, 45.6, mz - 0.35), (mx + 3.2, 46.3, mz + 0.35), faces=("north", "south", "up", "down", "east"))
    sec("probe", "probe", 0.65, mx + 3.0, mx + 6.2, axis="x", c=(45.95, mz), lo=0.0, hi=0.0)
    box("arm", "gauge_arm", (mx - 3.2, 41.6, mz - 0.35), (mx - 0.9, 42.3, mz + 0.35), faces=("north", "south", "up", "down", "west"))
    sec("gauge", "gauge", 1.5, 42.3, 42.8, c=(mx - 3.6, mz), lo=0.0, hi=0.0)
    box("arm", "gauge_stem", (mx - 3.85, 41.6, mz - 0.25), (mx - 3.35, 42.3, mz + 0.25), faces=SIDES)
    # The whip antenna at the pad's corner, its red light on top.
    sec("whip", "whip", 0.25, ROOF + 1.4, 61.5, c=(mx + 3.0, mz + 3.0))
    g.bone("mast_light", pivot=(mx + 3.0, 61.5, mz + 3.0), parent=F)
    box("mast_light", "mast_light_lamp", (mx + 2.55, 61.5, mz + 2.55), (mx + 3.45, 62.4, mz + 3.45), bone="mast_light", faces=ALL)
    # Its cable down the module's east side, from the pad to the ground.
    box("conduit", "conduit", (X1, 1.0, -2.0), (X1 + 0.7, ROOF + 0.9, -0.8), faces=("north", "south", "east", "up"))
    # --- the back: the Energy Port's plate ----------------------------------------------------------------------------------
    box("port", "port", (-6.0, 1.0, Z1), (6.0, 15.0, Z1 + 0.5), faces=("south", "east", "west", "up", "down"))
    return g, parts


# --- painting ----------------------------------------------------------------------------------------------------------------
CONCRETE = M.concrete(tone=0.6)
CONSOLE_PAINT = M.painted(CONSOLE_RAMP, tone=0.55, wear=0.15)
ROCKET = M.white_paint(tone=0.82, wear=0.05)


def module_face(f: M.Face, img: np.ndarray) -> None:
    """The module's concrete: formwork courses every four pixels, tie holes in rows, the hazard band along its foot."""
    rgb = img[..., :3].astype(np.float32)
    if f.d in SIDES:
        rgb[np.abs(f.Y % 4.0 - 0.0) < 0.25] *= 0.8
        horiz = f.X if f.d in ("north", "south") else f.Z
        ties = (np.abs((horiz + 100) % 8.0 - 4.0) < 0.35) & (np.abs(f.Y % 8.0 - 6.0) < 0.35)
        rgb[ties] *= 0.55
        band = f.Y < 2.4
        stripe = np.floor((horiz + f.Y + 100) / 2.0) % 2 == 0
        rgb[band & stripe] = (206, 168, 40)
        rgb[band & ~stripe] = (34, 32, 30)
    img[..., :3] = np.clip(rgb, 0, 255).astype(np.uint8)


def roof_face(f: M.Face, img: np.ndarray) -> None:
    """The roof slab's edge banded yellow and black."""
    if f.d not in SIDES:
        return
    rgb = img[..., :3].astype(np.float32)
    horiz = f.X if f.d in ("north", "south") else f.Z
    stripe = np.floor((horiz + f.Y + 100) / 2.0) % 2 == 0
    rgb[stripe] = (206, 168, 40)
    rgb[~stripe] = (34, 32, 30)
    img[..., :3] = np.clip(rgb, 0, 255).astype(np.uint8)


def paint(g: GeoModel, parts: dict) -> tuple[Tex, Tex]:
    k = K.Kit(g, {}, seed=6611, ao_reach=2.4, turned=True)
    W.round_faces(k.S, SS.ROUND, top=True, bottom=False, foot=False)
    S = k.S
    t, glow = Tex(g.tex_w, g.tex_h), Tex(g.tex_w, g.tex_h)
    S.paint(t, parts["module"] + parts["roof"] + parts["pad"], CONCRETE)
    S.overlay(t, parts["module"], module_face)
    S.overlay(t, parts["roof"], roof_face)
    S.paint(t, parts["console"], CONSOLE_PAINT)
    S.paint(t, parts["guard"], K.HAZARD)
    S.paint(t, parts["collar"], K.BRASS)
    S.paint(t, parts["key"], K.STEEL)
    S.paint(t, parts["plate"], K.WHITE)
    S.paint(t, parts["placard"], K.WHITE)
    S.paint(t, parts["vent"], K.GALV)
    S.paint(t, parts["tube"] + parts["hinge"] + parts["lid"] + parts["lid_boss"], K.OLIVE_DARK)
    S.paint(t, parts["mast"] + parts["arm"] + parts["whip"] + parts["conduit"], K.GALV)
    S.paint(t, parts["dome_base"] + parts["gauge"], K.GUN)
    S.paint(t, parts["probe"], K.YELLOW)
    S.paint(t, parts["port"], M.steel(tone=0.4, brushed=0.04, rust=0.05))
    S.paint(t, parts["rocket"], ROCKET)
    CR.fill_faces(g, t, parts["tube_floor"], (14, 14, 14))
    CR.fill_faces(g, t, parts["dome"], (36, 46, 58))
    CR.fill_faces(g, t, parts["lens"], (22, 30, 40))
    CR.fill_faces(g, t, parts["screen"], (10, 14, 10))
    CR.fill_faces(g, t, parts["mast_light"], (255, 52, 36))
    CR.fill_faces(g, glow, parts["mast_light"], (255, 52, 36))

    def rocket_band(f: M.Face, img: np.ndarray) -> None:
        img[..., :3][(f.Y > 33.4) & (f.Y < 34.0)] = (176, 30, 26)
    S.overlay(t, parts["rocket"], rocket_band)

    def plate_text(tx: Tex, r, d):
        x, y, w, h = r
        if d == "north":
            tw = Tex.text_width("DEAD HAND")
            tx.text(x + max(0, (w - tw) // 2), y + max(0, (h - 5) // 2), "DEAD HAND", (26, 28, 32, 255))
    g.paint_all(t, "plate", plate_text)

    def placard_text(tx: Tex, r, d):
        # A red-bordered warning: a red bar across its top, three lines of black type under it.
        x, y, w, h = r
        if d != "north" or w < 6:
            return
        tx.outline(x, y, w, h, (176, 30, 26, 255))
        tx.rect(x + 1, y + 1, w - 2, 2, (176, 30, 26, 255))
        for k in range(3):
            yy = y + 4 + k * 2
            if yy < y + h - 1:
                for xx in range(x + 2, x + w - 2 - (k % 2) * 2):
                    if xx % 4 != 1:
                        tx.px(xx, yy, (34, 34, 38, 255))
    g.paint_all(t, "placard", placard_text)
    for which, _ in LAMPS:
        CR.fill_faces(g, t, [f"lamp_{which}_dark"], LAMP_OFF[which])
        CR.fill_faces(g, t, parts[f"lamp_{which}"], LAMP_ON[which])
        CR.fill_faces(g, glow, parts[f"lamp_{which}"], LAMP_ON[which])
    border = M.painted(M.RED_PAINT_RAMP, tone=0.58, wear=0.3)

    def port_rim(f, img):
        ring = K.ring(f)
        M.paste(img, k.on(f, border), ring < 2)
        M.darken(img, ring == 2, 0.55)
    k.overlay(t, "port", port_rim, only=("south",))

    def tube_stencil(f: M.Face, img: np.ndarray) -> None:
        # A white band round the tube's top and a stencilled warning stripe.
        img[..., :3][(f.Y > TUBE_TOP - 1.4) & (f.Y < TUBE_TOP - 0.8)] = (210, 210, 200)
    S.overlay(t, parts["tube"], tube_stencil)
    return t, glow


# --- the blocks and the item ---------------------------------------------------------------------------------------------------
def placard(t: Tex) -> None:
    """The controller's placard: a dark console with its two keys and a red lamp."""
    t.rect(4, 9, 6, 4, (30, 36, 30, 255))
    t.px(5, 11, (196, 160, 70, 255))
    t.px(8, 11, (196, 160, 70, 255))
    t.px(6, 10, (255, 60, 40, 255))
    t.px(7, 10, (90, 255, 110, 255))


#: The Command Rocket's sprite, nose to the top right as the game's items lie: h highlight, w white, s shade, r red band,
#: d its shade, f olive fins, F their shade, a the whip antenna, g the nozzle's glow.
ROCKET_SPRITE = [
    "...............a",
    "..............a.",
    "............hw..",
    "...........hwws.",
    "..........hwwws.",
    ".........hwwws..",
    "........rrrrd...",
    ".......rrrrd....",
    "......hwwws.....",
    ".....hwwws......",
    "....hwwws.......",
    "...fhwws........",
    "..ffwwsF........",
    "..fffsFF........",
    "...g.FF.........",
    "..g.............",
]


def rocket_item() -> None:
    """The Command Rocket: a small white rocket nose up across the slot's diagonal, a red band under its nose, olive fins,
    the whip antenna out of its tip: each colour taken from the library's tiles (white paint, red paint, olive), so its
    paint has the same grain as the models'."""
    white = M.to_tex(M.tile(M.white_paint(tone=0.84, wear=0.0), seed=91))
    red = M.to_tex(M.tile(M.painted(M.RED_PAINT_RAMP, tone=0.5, wear=0.0), seed=92))
    olive = M.to_tex(M.tile(K.OLIVE, seed=93))

    def shade(c, k):
        return tuple(int(min(255, v * k)) for v in c[:3]) + (255,)
    t = Tex(16, 16)
    for y, row in enumerate(ROCKET_SPRITE):
        for x, ch in enumerate(row):
            if ch == ".":
                continue
            c = {"h": shade(white.get(x, y), 1.12), "w": white.get(x, y), "s": shade(white.get(x, y), 0.68),
                 "r": red.get(x, y), "d": shade(red.get(x, y), 0.7), "f": olive.get(x, y), "F": shade(olive.get(x, y), 0.72),
                 "a": (150, 152, 150, 255), "g": (255, 150, 60, 255)}[ch]
            t.px(x, y, c)
    t.save(paths.TEXTURES / "item" / "command_rocket.png")
    write_json(paths.MODELS / "item" / "command_rocket.json", {"parent": "minecraft:item/generated",
                                                                "textures": {"layer0": "groundburst:item/command_rocket"}})
    write_json(paths.ASSETS / "items" / "command_rocket.json", {"model": {"type": "minecraft:model", "model": "groundburst:item/command_rocket"}})


def spawner_marks(ox: int, oy: int, w: int, h: int):
    """The spawner sheet's plan: the module, the tube in its back-right corner, the mast at its front-left."""
    return [(ox + 1, oy + h - 2, P.NATO_GREEN[5]), (ox + w - 2, oy + 1, P.INDICATOR_RED.light)]


def main() -> None:
    ST.controller(NAME, ST.controller_front(placard), ST.controller_top(), ST.casing())
    rocket_item()
    model, parts = build()
    model.layout()
    texture, glowmask = paint(model, parts)
    W.match_walls(model, texture, [b.name for b in model.boxes], entity=True)
    for p in model.save(texture, glowmask):
        print(p)
    idle = Animation("idle", 1.0, loop="hold_on_last_frame")
    idle.rotate("lid", {0: (0, 0, 0), 1: (0, 0, 0)})
    print(save_animations(NAME, [idle]))
    MT.structure_spawner(f"{NAME}_spawner", A.plan(3, 2, spawner_marks))
    print("dead hand written; texture", model.tex_w, model.tex_h)


if __name__ == "__main__":
    main()
