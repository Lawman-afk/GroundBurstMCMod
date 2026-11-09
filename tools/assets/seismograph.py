"""The Seismograph (DESIGN.md §17.17): a Cold War station's drum recorder on its cabinet, after the Helicorder and the
WWSSN recorders: a paper drum on its axle between two standards, the pen on a carriage that steps along a rail in front of
it (the record a spiral of lines), the timing clock at one end and the drive motor at the other; on the cabinet's front a
dark instrument panel with the amplifier's meter, gain and time knobs, toggles, the power and event lamps, an amber
readout of the last blast's magnitude (written by the block entity's renderer) and a maker's plate. One block.

Built facing north (its panel to the north), its bottom middle the origin. Bones:
- "body": everything that stands still.
- "drum": the drum and its flanges, turned about its axle by the block entity (once a minute).
- "carriage": the pen's carriage on its rail, moved along it by the block entity (across the drum in twelve minutes);
  "pen_mount" leans the pen back to the paper; "pen" swings it across (the trace), turned by the block entity.
- "needle": the meter's needle, swinging with the pen.
- "lamp_power", "lamp_event": the lamps lit (shown by the block entity); "readout": the readout's lit glass.
Animations: "idle" and "working", both still (the block entity moves the bones).

    tools\\.venv\\Scripts\\python.exe tools\\assets\\seismograph.py
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
from gb.geo import Animation, GeoModel, save_animations  # noqa: E402
from gb.tex import Tex  # noqa: E402
import command_room as CR  # noqa: E402
import machines  # noqa: E402
import scud_storm as SS  # noqa: E402
import warheads as W  # noqa: E402

NAME = "seismograph"
ALL, SIDES = SS.ALL, SS.SIDES
NO_DOWN = SIDES + ("up",)
FRONT_FACES = ("north", "east", "west", "up", "down")
#: The cabinet: half its width, its front and back, its top; the top plate over it; the instrument panel's face.
HALF, FRONT, BACK, TOP = 7.0, -6.0, 6.0, 9.0
PLATE = 9.6
PANEL_Z = FRONT - 0.2
#: The drum: its axle's height and z, its radius, its ends; its flanges' radius.
DRUM_Y, DRUM_Z, DRUM_R, DRUM_X = 12.4, 0.6, 2.6, 4.9
FLANGE_R = 2.95
#: The rail the carriage rides (its height and z, its radius); the pen's lean back to the paper.
RAIL_Y, RAIL_Z, RAIL_R = 10.4, -3.4, 0.35
PEN_LEAN = -60.0
#: The meter (its middle, half its width and height) and the readout's glass, the maker's plate (x0, y0, x1, y1).
METER = (-3.7, 6.3, 2.5, 1.8)
READOUT = (1.0, 5.2, 5.8, 6.8)
NAMEPLATE = (1.0, 7.4, 5.8, 8.2)
KNOBS = ((-5.0, 2.7), (-2.6, 2.7))
TOGGLES = (-0.6, 0.6)
#: The lamps: power (green) and event (red), their middles.
LAMPS = {"power": (2.8, 2.9), "event": (4.4, 2.9)}
#: The cabinet's hammertone grey-green, as instrument cabinets of the time were.
LAB_RAMP = M.ramp("#26302b", "#323d37", "#3f4b44", "#4d5a52", "#5c6a61", "#6d7b71", "#808e83", "#96a397", "#aebaae")


def build() -> tuple[GeoModel, dict]:
    g = GeoModel(NAME, tex_size=(128, 128))
    parts: dict[str, list[str]] = {}
    SS.ROUND.clear()

    def box(group, n, frm, to, bone="body", faces=ALL, rotation=None):
        g.box(n, frm, to, faces=faces, bone=bone, rotation=rotation)
        parts.setdefault(group, []).append(n)

    def ring(group, n, r, a0, a1, bone, axis="y", c=(0.0, 0.0), **kw):
        names = W.round_section(SS.OnBone(g, bone), n, r, a0, a1, axis=axis, c=c, **kw)
        SS.ROUND.extend(names)
        parts.setdefault(group, []).extend(names)
        return names

    g.bone("body")
    # --- the cabinet ----------------------------------------------------------------------------------------------------
    box("plinth", "plinth", (-6.6, 0, -5.6), (6.6, 1.0, 5.6), faces=SIDES)
    box("cabinet", "cabinet", (-HALF, 1.0, FRONT), (HALF, TOP, BACK), faces=SIDES + ("down",))
    box("top", "top", (-7.4, TOP, -6.4), (7.4, PLATE, 6.4), faces=ALL)
    box("gland", "gland", (-1.0, 2.0, BACK), (1.0, 3.6, BACK + 0.8), faces=("south", "east", "west", "up", "down"))
    # The instrument panel on the cabinet's front, and on it: the meter in its housing (its needle in a bone of its own),
    # the readout's glass in its bezel, the knobs, the toggles, the lamps (dark; each lit one in its own bone closes over
    # it), the maker's plate.
    box("panel", "panel", (-6.5, 1.5, PANEL_Z), (6.5, 8.6, FRONT), faces=FRONT_FACES)
    mx, my, mw, mh = METER
    box("meter", "meter", (mx - mw, my - mh, PANEL_Z - 0.5), (mx + mw, my + mh, PANEL_Z), faces=FRONT_FACES)
    g.bone("needle", pivot=(mx, my - mh + 0.4, PANEL_Z - 0.5), parent="body")
    box("needle", "needle_arm", (mx - 0.1, my - mh + 0.4, PANEL_Z - 0.62), (mx + 0.1, my + mh - 0.5, PANEL_Z - 0.54), bone="needle",
        faces=FRONT_FACES)
    rx0, ry0, rx1, ry1 = READOUT
    box("bezel", "bezel", (rx0 - 0.35, ry0 - 0.35, PANEL_Z - 0.4), (rx1 + 0.35, ry1 + 0.35, PANEL_Z), faces=FRONT_FACES)
    g.bone("readout", pivot=(rx0, ry0, PANEL_Z - 0.4), parent="body")
    box("readout", "readout_lit", (rx0, ry0, PANEL_Z - 0.56), (rx1, ry1, PANEL_Z - 0.4), bone="readout", faces=FRONT_FACES)
    nx0, ny0, nx1, ny1 = NAMEPLATE
    box("nameplate", "nameplate", (nx0, ny0, PANEL_Z - 0.15), (nx1, ny1, PANEL_Z), faces=FRONT_FACES)
    for j, (kx, ky) in enumerate(KNOBS):
        ring("knob_skirt", f"skirt{j}", 0.95, PANEL_Z - 0.25, PANEL_Z, "body", axis="z", c=(kx, ky), lo=0.0)
        ring("knob", f"knob{j}", 0.7, PANEL_Z - 0.85, PANEL_Z - 0.25, "body", axis="z", c=(kx, ky), lo=0.0)
    for j, tx in enumerate(TOGGLES):
        box("toggle_base", f"toggle_base{j}", (tx - 0.4, 2.2, PANEL_Z - 0.2), (tx + 0.4, 3.6, PANEL_Z), faces=FRONT_FACES)
        box("toggle", f"toggle{j}", (tx - 0.13, 3.0, PANEL_Z - 1.0), (tx + 0.13, 3.26, PANEL_Z - 0.2), faces=ALL)
    for which, (lx, ly) in LAMPS.items():
        box("lamp_dark", f"lamp_{which}_dark", (lx - 0.45, ly - 0.45, PANEL_Z - 0.25), (lx + 0.45, ly + 0.45, PANEL_Z), faces=FRONT_FACES)
        g.bone(f"lamp_{which}", pivot=(lx, ly, PANEL_Z), parent="body")
        box(f"lamp_{which}", f"lamp_{which}_lit", (lx - 0.62, ly - 0.62, PANEL_Z - 0.42), (lx + 0.62, ly + 0.62, PANEL_Z - 0.05),
            bone=f"lamp_{which}", faces=FRONT_FACES)
    # --- the recorder on the top plate -----------------------------------------------------------------------------------
    # The standards: each an upright holding the axle, on a foot that runs forward to carry the rail's end.
    for side, x0, x1 in (("w", -6.0, -5.2), ("e", 5.2, 6.0)):
        box("standard", f"foot_{side}", (x0, PLATE, -4.1), (x1, PLATE + 1.4, 2.2), faces=NO_DOWN)
        box("standard", f"upright_{side}", (x0, PLATE + 1.4, -0.6), (x1, DRUM_Y + 1.0, 1.8), faces=NO_DOWN)
    # The timing clock at the west end, the drive motor at the east (its gear cover against the standard).
    box("clock", "clock", (-7.2, PLATE, -1.4), (-6.0, PLATE + 3.0, 2.4), faces=NO_DOWN)
    ring("motor", "motor", 1.2, 6.0, 7.2, "body", axis="x", c=(PLATE + 1.4, 0.4), hi=0.0)
    box("motor", "motor_foot", (6.0, PLATE, -0.6), (6.95, PLATE + 0.4, 1.4), faces=NO_DOWN)
    # The rail in front of the drum, its ends in the standards' feet.
    ring("rail", "rail", RAIL_R, -5.2, 5.2, "body", axis="x", c=(RAIL_Y, RAIL_Z))
    # The drum: the paper wound round it, a flange at each end, turned about its axle.
    g.bone("drum", pivot=(0.0, DRUM_Y, DRUM_Z), parent="body")
    ring("paper", "paper", DRUM_R, -DRUM_X, DRUM_X, "drum", axis="x", c=(DRUM_Y, DRUM_Z))
    ring("flange", "flange_w", FLANGE_R, -DRUM_X - 0.3, -DRUM_X, "drum", axis="x", c=(DRUM_Y, DRUM_Z), lo=0.0, hi=DRUM_R)
    ring("flange", "flange_e", FLANGE_R, DRUM_X, DRUM_X + 0.3, "drum", axis="x", c=(DRUM_Y, DRUM_Z), lo=DRUM_R, hi=0.0)
    ring("axle", "axle_w", 0.45, -5.2, -DRUM_X - 0.3, "drum", axis="x", c=(DRUM_Y, DRUM_Z))
    ring("axle", "axle_e", 0.45, DRUM_X + 0.3, 5.2, "drum", axis="x", c=(DRUM_Y, DRUM_Z))
    # The carriage on the rail; the pen leaning back from it to the paper, swinging across.
    g.bone("carriage", pivot=(0.0, RAIL_Y, RAIL_Z), parent="body")
    box("carriage", "carriage", (-0.75, RAIL_Y - 0.6, RAIL_Z - 0.6), (0.75, RAIL_Y + 0.8, RAIL_Z + 0.6), bone="carriage", faces=ALL)
    g.bone("pen_mount", pivot=(0.0, RAIL_Y + 0.8, RAIL_Z), parent="carriage", rotation=(PEN_LEAN, 0, 0))
    g.bone("pen", pivot=(0.0, RAIL_Y + 0.8, RAIL_Z), parent="pen_mount")
    reach = pen_reach()
    box("pen", "pen_arm", (-0.15, RAIL_Y + 0.8, RAIL_Z - 0.15), (0.15, RAIL_Y + 0.8 + reach - 0.35, RAIL_Z + 0.15), bone="pen", faces=SIDES + ("up",))
    box("pen_tip", "pen_tip", (-0.25, RAIL_Y + 0.8 + reach - 0.35, RAIL_Z - 0.25), (0.25, RAIL_Y + 0.8 + reach - 0.05, RAIL_Z + 0.25),
        bone="pen", faces=SIDES + ("up",))
    return g, parts


def pen_reach() -> float:
    """How long the pen must be, leaning back {PEN_LEAN} degrees from its carriage, to touch the paper."""
    lean = math.radians(-PEN_LEAN)
    y0, z0 = RAIL_Y + 0.8, RAIL_Z
    for i in range(1, 400):
        t = i * 0.02
        y, z = y0 + t * math.cos(lean), z0 + t * math.sin(lean)
        if math.hypot(y - DRUM_Y, z - DRUM_Z) <= DRUM_R + 0.02:
            return t
    return 3.0


# --- painting ----------------------------------------------------------------------------------------------------------------
CABINET = M.painted(LAB_RAMP, tone=0.6, wear=0.07, mottle=0.02, grain=0.0, dither=0.0, falloff=0.03)
TOP_PLATE = M.painted(M.BLACK_RAMP, tone=0.42, wear=0.05)
PAPER = M.white_paint(tone=0.9, wear=0.0)
INK = np.array((52, 60, 96), np.float32)
LAMP_ON = {"power": (90, 255, 110), "event": (255, 70, 50)}
LAMP_OFF = {"power": (20, 62, 26), "event": (74, 18, 16)}
LEGEND = (214, 214, 204)


def paper_face(f: M.Face, img: np.ndarray) -> None:
    """The record wound round the drum: the pen's lines run round it every other texel along the axle, faint, as a
    record's lines look from across a room; here and there a blast's darker stroke."""
    rgb = img[..., :3].astype(np.float32)
    col = np.floor(f.X + DRUM_X)
    line = col % 2 == 0
    rgb[line] = rgb[line] * 0.7 + INK * 0.3
    angle = np.arctan2(f.Z - DRUM_Z, f.Y - DRUM_Y)
    strokes = line & (np.abs(np.sin(angle * 3.0 + col * 2.3)) > 0.985)
    rgb[strokes] = INK
    img[..., :3] = np.clip(rgb, 0, 255).astype(np.uint8)


def panel_face(f: M.Face, img: np.ndarray) -> None:
    """The instrument panel: a fine light rule round its edge, the legends under its knobs, toggles and lamps."""
    if f.d != "north":
        return
    rgb = img[..., :3].astype(np.float32)
    rule = ((np.abs(np.abs(f.X) - 6.15) < 0.2) & (f.Y > 1.8) & (f.Y < 8.3)) | ((np.abs(f.Y - 1.85) < 0.2) | (np.abs(f.Y - 8.25) < 0.2)) & (np.abs(f.X) < 6.15)
    rgb[rule] = rgb[rule] * 0.5 + np.array(LEGEND) * 0.5
    under = np.abs(f.Y - 1.95 - 0.0) < 0.05
    xs = [k[0] for k in KNOBS] + list(TOGGLES) + [l[0] for l in LAMPS.values()]
    legend = np.zeros_like(under)
    for x in xs:
        legend |= (np.abs(f.Y - 1.6 - 0.45) < 0.2) & (np.abs(f.X - x) < 0.55)
    rgb[legend & (np.floor(f.X * 2) % 2 == 0)] = LEGEND
    img[..., :3] = np.clip(rgb, 0, 255).astype(np.uint8)


def cabinet_face(f: M.Face, img: np.ndarray) -> None:
    """The cabinet's sides and back: louvres low on the sides, louvres and a data plate on the back."""
    rgb = img[..., :3].astype(np.float32)
    if f.d in ("east", "west"):
        louvre = (f.Y > 2.5) & (f.Y < 7.0) & (np.abs(f.Z) < 4.0) & (np.floor(f.Y * 2) % 2 == 0)
        rgb[louvre] *= 0.5
    if f.d == "south":
        louvre = (f.Y > 5.0) & (f.Y < 8.0) & (np.abs(f.X) < 5.0) & (np.floor(f.Y * 2) % 2 == 0)
        rgb[louvre] *= 0.5
        plate = (np.abs(f.X - 4.2) < 1.4) & (np.abs(f.Y - 2.8) < 0.7)
        rgb[plate] = (188, 170, 110)
    img[..., :3] = np.clip(rgb, 0, 255).astype(np.uint8)


def paint(g: GeoModel, parts: dict) -> tuple[Tex, Tex]:
    k = K.Kit(g, {}, seed=7801, ao_reach=1.6, turned=True)
    W.round_faces(k.S, SS.ROUND, top=True, bottom=False, foot=False)
    S = k.S
    t, glow = Tex(g.tex_w, g.tex_h), Tex(g.tex_w, g.tex_h)
    S.paint(t, parts["plinth"], K.DARK)
    S.paint(t, parts["cabinet"] + parts["gland"], CABINET)
    S.overlay(t, parts["cabinet"], cabinet_face)
    S.paint(t, parts["panel"], CR.PANEL)
    S.overlay(t, parts["panel"], panel_face)
    S.paint(t, parts["top"], TOP_PLATE)
    S.paint(t, parts["standard"] + parts["clock"] + parts["motor"], K.GUN)
    S.paint(t, parts["rail"] + parts["axle"], K.STEEL)
    S.paint(t, parts["flange"], K.BRIGHT)
    S.paint(t, parts["paper"], PAPER)
    S.overlay(t, parts["paper"], paper_face)
    S.paint(t, parts["carriage"] + parts["meter"] + parts["toggle_base"], K.BLACK)
    S.paint(t, parts["pen"], K.BRASS)
    S.paint(t, parts["toggle"] + parts["knob_skirt"], K.BRIGHT)
    S.paint(t, parts["knob"], K.BLACK)
    S.paint(t, parts["nameplate"], K.WHITE)
    CR.fill_faces(g, t, parts["pen_tip"], (176, 34, 30))
    CR.fill_faces(g, t, parts["needle"], (24, 24, 28))

    def meter_face(tx: Tex, r, d):
        # The meter's cream face under its glass, its scale's arc and ticks, the red zone at its right end.
        x, y, w, h = r
        if d != "north" or w < 4 or h < 3:
            return
        tx.rect(x + 1, y + 1, w - 2, h - 2, (226, 216, 188, 255))
        n = w - 2
        for i in range(n):
            a = math.pi * (0.2 + 0.6 * i / max(1, n - 1))
            px = int(round(x + 1 + i))
            py = int(round(y + 1 + (h - 3) * (1 - math.sin(a)) * 0.9))
            tx.px(px, py, (150, 40, 32, 255) if i >= n * 0.75 else (40, 38, 34, 255))
    g.paint_all(t, "meter", meter_face)

    def plate_text(tx: Tex, r, d):
        x, y, w, h = r
        if d == "north" and w >= 4:
            for xx in range(x + 1, x + w - 1):
                if xx % 3 != 0:
                    tx.px(xx, y + h // 2, (30, 32, 36, 255))
    g.paint_all(t, "nameplate", plate_text)

    def clock_face(tx: Tex, r, d):
        x, y, w, h = r
        if d == "north" and w >= 2 and h >= 3:
            tx.rect(x, y + 1, w, h - 2, (214, 208, 190, 255))
            tx.px(x + w // 2, y + h // 2, (30, 30, 30, 255))
    g.paint_all(t, "clock", clock_face)

    def knob_mark(f: M.Face, img: np.ndarray) -> None:
        if f.d != "north":
            return
        for kx, ky in KNOBS:
            img[..., :3][(np.abs(f.X - kx) < 0.2) & (f.Y > ky) & (f.Y < ky + 0.7)] = (226, 226, 220)
    S.overlay(t, parts["knob"], knob_mark)
    # The readout's bezel and dark glass; lit, amber glass over it (in the glowmask too).
    CR.fill_faces(g, t, parts["bezel"], (22, 20, 20))
    for which in LAMPS:
        CR.fill_faces(g, t, [f"lamp_{which}_dark"], LAMP_OFF[which])
        CR.fill_faces(g, t, parts[f"lamp_{which}"], LAMP_ON[which])
        CR.fill_faces(g, glow, parts[f"lamp_{which}"], LAMP_ON[which])
    CR.fill_faces(g, t, parts["readout"], (74, 28, 10))
    CR.fill_faces(g, glow, parts["readout"], (74, 28, 10))
    return t, glow


def animations() -> list[Animation]:
    """Still both ways: the block entity turns the drum and swings the pen."""
    return [Animation("idle", 0.05, loop="hold_on_last_frame"), Animation("working", 0.05, loop="hold_on_last_frame")]


def main() -> None:
    g, parts = build()
    g.layout()
    t, glow = paint(g, parts)
    W.match_walls(g, t, [b.name for b in g.boxes], entity=True)
    for p in g.save(t, glow):
        print(p)
    print(save_animations(NAME, animations()))
    machines.gecko_machine(NAME)
    print(NAME, "tex", g.tex_w, g.tex_h, "pen reach", round(pen_reach(), 2))


if __name__ == "__main__":
    main()
