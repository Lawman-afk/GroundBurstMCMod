"""The Tape Computer (DESIGN.md §22.4): a tape drive cabinet a block wide and two high, after the reel-to-reel tape units
that lined the computer rooms of the 1960s: two reels side by side behind the upper front, the vacuum columns and the
head under them, a strip of lamps and buttons, panel doors and a louvred grille below. A row of them makes the wall of
tape computers.

Built as it stands facing north (its front to the north), its lower block's bottom middle the origin. Bones:
- "cabinet": everything that stands still.
- "reel_l_mount" / "reel_r_mount": each reel laid on the reel plate (built standing, as the Blast Door's wheels are);
  "reel_l" / "reel_r" spin in them.
- "lamp0".."lamp5": the lamps lit (bright, in the glowmask); scaled to nothing when dark, over their dark lamps.
Animations: "working" (12 s, looped while it runs: the reels read forward, stop, rewind, stop, read on, as a drive
does; the lamps blink), "idle" (the reels still, every lamp dark).

    tools\\.venv\\Scripts\\python.exe tools\\assets\\tape_computer.py
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
from gb.tex import Tex  # noqa: E402
import command_room as CR  # noqa: E402
import scud_storm as SS  # noqa: E402
import warheads as W  # noqa: E402

NAME = "tape_computer"
ALL, SIDES = SS.ALL, SS.SIDES
#: The cabinet: half its width, its front and back, its top.
HALF, FRONT, BACK, TOP = 7.0, -6.0, 8.0, 30.5
#: The reel window (x from, to; y from, to) and the reel plate's face behind it.
WIN = (-6.0, 6.0, 17.0, 29.0)
PLATE_Z = -4.5
#: The reels' middles and their flanges' radius.
REELS = {"l": (-3.0, 24.6), "r": (3.0, 24.6)}
REEL_R = 2.8
#: The control strip's lamps (x, y, colour).
LAMPS = [(-5.4, 16.1, "red"), (-4.2, 16.1, "amber"), (-3.0, 16.1, "green"), (-1.8, 16.1, "white"), (-5.4, 15.0, "amber"),
         (-4.2, 15.0, "green")]
LAMP_ON = {"red": (255, 70, 50), "amber": (255, 176, 40), "green": (90, 255, 110), "white": (236, 240, 255)}
LAMP_OFF = {"red": (72, 18, 16), "amber": (76, 52, 16), "green": (20, 62, 26), "white": (66, 68, 74)}
LOOP = 12.0


def build() -> tuple[GeoModel, dict]:
    g = GeoModel(NAME, tex_size=(128, 128))
    g.bone("cabinet")
    C = "cabinet"
    parts: dict[str, list[str]] = {}

    def box(group, n, frm, to, faces=ALL, bone=C, rotation=None):
        g.box(n, frm, to, faces=faces, bone=bone, rotation=rotation)
        parts.setdefault(group, []).append(n)

    x0, x1, y0, y1 = WIN
    # The cabinet: its lower body, the posts and cap framing the reel window, the reel plate set back in it.
    box("body", "lower", (-HALF, 0, FRONT), (HALF, y0, BACK), faces=("north", "south", "east", "west", "down"))
    box("body", "post_l", (-HALF, y0, FRONT), (x0, y1, BACK), faces=("north", "south", "east", "west"))
    box("body", "post_r", (x1, y0, FRONT), (HALF, y1, BACK), faces=("north", "south", "east", "west"))
    box("body", "cap", (-HALF, y1, FRONT), (HALF, TOP, BACK), faces=ALL)
    box("plate", "plate", (x0, y0, PLATE_Z), (x1, y1, BACK), faces=("north", "south", "down"))
    # The read head between the vacuum columns, and the tape guides above it.
    box("head", "head", (-1.0, 19.6, PLATE_Z - 0.8), (1.0, 21.4, PLATE_Z), faces=("north", "east", "west", "up", "down"))
    for j, gx in enumerate((-1.8, 1.8)):
        box("guide", f"guide{j}", (gx - 0.3, 21.8, PLATE_Z - 0.6), (gx + 0.3, 22.4, PLATE_Z), faces=("north", "east", "west", "up", "down"))
    # The control strip's buttons, and its lamps dark (each lamp's lit one, in its own bone, closes over it).
    for j, (bx, c) in enumerate(((1.6, "white"), (3.2, "red"), (4.8, "green"))):
        box(f"button_{c}", f"button{j}", (bx - 0.6, 15.0, FRONT - 0.4), (bx + 0.6, 16.4, FRONT), faces=("north", "east", "west", "up", "down"))
    for j, (lx, ly, c) in enumerate(LAMPS):
        box(f"dark_{c}", f"dark{j}", (lx - 0.35, ly - 0.35, FRONT - 0.25), (lx + 0.35, ly + 0.35, FRONT), faces=("north", "east", "west", "up", "down"))
        g.bone(f"lamp{j}", pivot=(lx, ly, FRONT), parent=C)
        box(f"lit_{c}", f"lit{j}", (lx - 0.5, ly - 0.5, FRONT - 0.45), (lx + 0.5, ly + 0.5, FRONT), faces=("north", "east", "west", "up", "down"),
            bone=f"lamp{j}")
    # The reels: built standing about their middles, laid on the plate by their mounts; a back flange, the tape wound
    # on, a front flange with three windows (painted), the hub.
    SS.ROUND.clear()
    for side, (cx, cy) in REELS.items():
        mount, spin = f"reel_{side}_mount", f"reel_{side}"
        g.bone(mount, pivot=(cx, cy, PLATE_Z), parent=C, rotation=(-90.0, 0, 0))
        g.bone(spin, pivot=(cx, cy, PLATE_Z), parent=mount)
        parts.setdefault(f"flange_{side}", []).extend(SS.ring(g, f"back_{side}", REEL_R, cy, cy + 0.25, cx, PLATE_Z, spin, caps=("up",)))
        parts.setdefault(f"pack_{side}", []).extend(SS.ring(g, f"pack_{side}", 2.0, cy + 0.25, cy + 0.9, cx, PLATE_Z, spin, caps=("up",)))
        parts.setdefault(f"front_{side}", []).extend(SS.ring(g, f"front_{side}", REEL_R, cy + 0.9, cy + 1.15, cx, PLATE_Z, spin, caps=("up", "down")))
        parts.setdefault(f"hub_{side}", []).extend(SS.ring(g, f"hub_{side}", 1.2, cy + 1.1, cy + 1.5, cx, PLATE_Z, spin, caps=("up",)))
    return g, parts


# --- painting ----------------------------------------------------------------------------------------------------------------
CABINET = M.painted(CR.GREY_RAMP, tone=0.62, wear=0.1)
CAP = M.painted(M.ramp("#1f2b3b", "#283850", "#324766", "#3e577b", "#4c6a92", "#6283ab", "#7f9ec2"), tone=0.5, wear=0.12)
PLATE = CR.PANEL
FLANGE = M.painted(M.ramp("#5e646b", "#747b83", "#8a929b", "#a2aab3", "#b8c0c8", "#ccd3da", "#dde3e8"), tone=0.62, wear=0.02)
TAPE = M.painted(M.ramp("#1c120c", "#271a11", "#342317", "#422d1e", "#523926", "#634630", "#77563c"), tone=0.4, wear=0.0)


def windows(f: M.Face, img: np.ndarray, cx: float) -> None:
    """A reel's front flange: three windows onto the tape wound on it (dark brown), its rim lighter."""
    if f.d != "up":
        return
    dx, dz = f.X - cx, f.Z - PLATE_Z
    r = np.hypot(dx, dz)
    a = np.degrees(np.arctan2(dz, dx)) % 120.0
    win = (r > 1.4) & (r < 2.45) & (np.abs(a - 60.0) < 28.0)
    img[win, :3] = (52, 36, 24)
    img[(r >= 2.5), :3] = np.clip(img[(r >= 2.5), :3].astype(np.int32) + 18, 0, 255).astype(np.uint8)


def plate_face(f: M.Face, img: np.ndarray) -> None:
    """The reel plate between the reels: the two vacuum columns under them (dark wells, the tape's loop at their foot), the
    tape's path down from each reel to the head."""
    rgb = img[..., :3].astype(np.float32)
    for xa, xb in ((-5.0, -2.4), (2.4, 5.0)):
        well = (f.X > xa) & (f.X < xb) & (f.Y > 17.6) & (f.Y < 21.6)
        rgb[well] = (14, 15, 17)
        rgb[well & (np.abs(f.Y - 18.4) < 0.5)] = (70, 48, 30)
        rgb[(np.abs(f.X - xa) < 0.5) & (f.Y > 18.4) & (f.Y < 21.6)] = (70, 48, 30)
    for gx in (-1.8, 1.8):
        rgb[(np.abs(f.X - gx) < 0.5) & (f.Y > 21.6) & (f.Y < 22.6)] = (70, 48, 30)
    img[..., :3] = np.clip(rgb, 0, 255).astype(np.uint8)


def front_face(f: M.Face, img: np.ndarray) -> None:
    """The lower body's front: the control strip (black, white legends under its lamps and buttons), two panel doors with
    their seams and handles, a louvred grille near the floor, a maker's plate."""
    rgb = img[..., :3].astype(np.float32)
    strip = (f.Y > 14.4) & (f.Y < 16.9)
    rgb[strip] = (26, 27, 30)
    legend = strip & (np.abs(f.Y - 14.6) < 0.3) & (np.floor(f.X * 1.25) % 2 == 0) & (f.X < 5.6)
    rgb[legend] = (210, 210, 200)
    seams = ((np.abs(f.X) < 0.25) | (np.abs(f.Y - 13.9) < 0.25) | (np.abs(f.Y - 3.6) < 0.25)) & (f.Y < 14.0)
    rgb[seams] = rgb[seams] * 0.45
    handles = (np.abs(np.abs(f.X) - 1.2) < 0.4) & (np.abs(f.Y - 9.0) < 1.0)
    rgb[handles] = (190, 194, 196)
    grille = (f.Y > 0.8) & (f.Y < 3.2) & (np.abs(f.X) < 6.0) & (np.floor(f.Y * 2) % 2 == 0)
    rgb[grille] = rgb[grille] * 0.35
    plate = (np.abs(f.X - 3.8) < 1.6) & (np.abs(f.Y - 12.0) < 0.6)
    rgb[plate] = (196, 170, 96)
    img[..., :3] = np.clip(rgb, 0, 255).astype(np.uint8)


def paint(g: GeoModel, parts: dict) -> tuple[Tex, Tex]:
    k = K.Kit(g, {}, seed=4501, ao_reach=1.8)
    W.round_faces(k.S, SS.ROUND, top=True, bottom=False, foot=False)
    S = k.S
    t = Tex(g.tex_w, g.tex_h)
    S.paint(t, parts["body"], CABINET)
    S.paint(t, ["cap"], CAP)
    S.overlay(t, ["cap"], lambda f, img: M.darken(img, (np.floor(f.X) % 2 == 0) & (np.abs(f.Z - 1.0) < 6.0), 0.55), only=("up",))
    S.overlay(t, ["lower"], front_face, only=("north",))
    S.overlay(t, ["lower"], lambda f, img: M.darken(img, (np.floor(f.Y) % 2 == 0) & (f.Y > 2.0) & (f.Y < 12.0) & (np.abs(f.Z - 1.0) < 4.0), 0.6),
              only=("east", "west"))
    S.paint(t, parts["plate"], PLATE)
    S.paint(t, parts["plate"], CABINET, only=("south", "down"))          # its back is the cabinet's back
    S.overlay(t, parts["plate"], plate_face, only=("north",))
    S.paint(t, parts["head"] + parts["guide"], K.BRIGHT)
    for c, mat in (("white", K.WHITE), ("red", K.RED), ("green", CR.GREEN_PAINT)):
        S.paint(t, parts.get(f"button_{c}", []), mat)
    for side, (cx, cy) in REELS.items():
        S.paint(t, parts[f"flange_{side}"] + parts[f"front_{side}"], FLANGE)
        S.overlay(t, parts[f"front_{side}"], lambda f, img, cx=cx: windows(f, img, cx))
        S.paint(t, parts[f"pack_{side}"], TAPE)
        S.paint(t, parts[f"hub_{side}"], K.BRIGHT)
    glow = Tex(g.tex_w, g.tex_h)
    for colour in LAMP_ON:
        for n in parts.get(f"dark_{colour}", []):
            CR.fill_faces(g, t, [n], LAMP_OFF[colour])
        for n in parts.get(f"lit_{colour}", []):
            CR.fill_faces(g, t, [n], LAMP_ON[colour])
            CR.fill_faces(g, glow, [n], LAMP_ON[colour])
    return t, glow


# --- animations ----------------------------------------------------------------------------------------------------------------
#: The reels' turn (degrees) at each moment of the loop: reading forward, stopping, rewinding, stopping, reading on, ending a
#: whole turn on (so the loop joins). The right-hand reel runs a little behind the left, as a take-up reel does.
REEL_KEYS = {
    "l": {0: 0, 3.0: 540, 3.6: 600, 4.2: 600, 6.4: 0, 7.0: -40, 7.6: -40, 10.6: 330, 11.2: 360, 12.0: 360},
    "r": {0: 0, 3.0: 470, 3.6: 520, 4.2: 520, 6.4: -20, 7.0: -60, 7.6: -60, 10.6: 300, 11.2: 360, 12.0: 360},
}


def lamp_keys(seed: int, always: bool) -> dict[float, tuple]:
    """A lamp's scale through the loop: on and off at odd moments (a step at each), or lit throughout."""
    if always:
        return {0: (1, 1, 1), LOOP: (1, 1, 1)}
    rng = np.random.default_rng(seed)
    keys, t, on = {0.0: (1, 1, 1)}, 0.0, True
    while True:
        t += float(rng.uniform(0.3, 1.5))
        if t >= LOOP - 0.2:
            break
        keys[round(t - 0.02, 3)] = (1, 1, 1) if on else (0, 0, 0)
        on = not on
        keys[round(t, 3)] = (1, 1, 1) if on else (0, 0, 0)
    keys[round(LOOP - 0.02, 3)] = (1, 1, 1) if on else (0, 0, 0)
    keys[LOOP] = (1, 1, 1)
    return keys


def shifted_turn(keys: dict, s: float) -> dict[float, float]:
    """A reel's keys begun {s} seconds into the loop: the turn from there on, running on past the loop's end with the
    whole turn the loop makes added (so it joins, a whole turn on), starting at 0."""
    times = sorted(keys)

    def at(t: float) -> float:
        t %= LOOP
        for a, b in zip(times, times[1:]):
            if a <= t <= b:
                return keys[a] + (keys[b] - keys[a]) * (t - a) / (b - a)
        return keys[times[-1]]
    turn = keys[times[-1]] - keys[times[0]]
    out = {0.0: 0.0}
    for t in times:
        if t >= LOOP:
            continue                                   # the loop's end is its start
        tau = round((t - s) % LOOP, 4)
        if 0 < tau < LOOP:
            out[tau] = at(t) + (turn if t < s else 0.0) - at(s)
    out[LOOP] = turn
    return dict(sorted(out.items()))


def shifted_steps(keys: dict, s: float) -> dict[float, tuple]:
    """A lamp's keys begun {s} seconds into the loop (each moment moved round the loop)."""
    times = sorted(keys)

    def at(t: float):
        t %= LOOP
        last = keys[times[0]]
        for k in times:
            if k <= t + 1e-9:
                last = keys[k]
        return last
    out = {0.0: at(s)}
    for t in times:
        if t >= LOOP:
            continue
        tau = round((t - s) % LOOP, 4)
        if 0 < tau < LOOP:
            out[tau] = keys[t]
    out[LOOP] = at(s)
    return dict(sorted(out.items()))


def animations() -> list[Animation]:
    """Idle, and three takes of the drive at work begun 0, 4 and 8 seconds into it ("working", "working_b",
    "working_c"): the block entity picks one by where it stands, so a row never runs in step."""
    idle = Animation("idle", 0.05, loop="hold_on_last_frame")
    for j in range(len(LAMPS)):
        idle.scale(f"lamp{j}", {0: (0, 0, 0)})
    out = [idle]
    for name, s in (("working", 0.0), ("working_b", 4.0), ("working_c", 8.0)):
        work = Animation(name, LOOP, loop=True)
        for side, keys in REEL_KEYS.items():
            work.rotate(f"reel_{side}", {t: (0, v, 0) for t, v in shifted_turn(keys, s).items()})
        for j, (_, _, colour) in enumerate(LAMPS):
            work.scale(f"lamp{j}", shifted_steps(lamp_keys(4600 + j, colour == "white"), s))
        out.append(work)
    return out


ICON = [
    "................",
    "....bbbbbbbb....",
    "....kkkkkkkk....",
    "....kgggggGk....",
    "....kgRRgRRk....",
    "....kRwRRwRk....",
    "....kgRRgRRk....",
    "....kgdgdgdk....",
    "....kyrgGGGk....",
    "....kGGG|GGk....",
    "....kGGG|GGk....",
    "....kGGG|GGk....",
    "....kGGGGGGk....",
    "....k======k....",
    "....kkkkkkkk....",
    "................",
]


def icon() -> None:
    """The item: the cabinet from the front, its two reels, its lamps, its doors and grille."""
    col = {"b": (60, 84, 116), "k": (44, 46, 50), "g": (32, 34, 38), "G": (150, 156, 150), "R": (170, 176, 184), "w": (70, 48, 30),
           "d": (14, 15, 17), "y": (255, 176, 40), "r": (255, 70, 50), "|": (90, 94, 92), "=": (70, 72, 70)}
    img = np.zeros((16, 16, 4), np.uint8)
    for y, row in enumerate(ICON):
        for x, c in enumerate(row):
            if c != ".":
                img[y, x] = (*col[c], 255)
    Image.fromarray(img, "RGBA").save(paths.TEXTURES / "item" / f"{NAME}.png")
    write_json(paths.MODELS / "item" / f"{NAME}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"groundburst:item/{NAME}"}})
    write_json(paths.ASSETS / "items" / f"{NAME}.json", {"model": {"type": "minecraft:model", "model": f"groundburst:item/{NAME}"}})


def block_files() -> None:
    """Both halves draw nothing themselves (the lower one's block entity draws the cabinet): a model with its particle."""
    write_json(paths.MODELS / "block" / f"{NAME}.json", {"textures": {"particle": "groundburst:block/steel_panel"}})
    write_json(paths.ASSETS / "blockstates" / f"{NAME}.json", {"multipart": [{"apply": {"model": f"groundburst:block/{NAME}"}}]})


def main() -> None:
    g, parts = build()
    g.layout()
    t, glow = paint(g, parts)
    g.save(t, glow)
    print(save_animations(NAME, animations()))
    print(NAME, "tex", g.tex_w, g.tex_h)
    icon()
    block_files()


if __name__ == "__main__":
    main()
