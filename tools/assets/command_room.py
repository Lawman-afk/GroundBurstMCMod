"""The command room (DESIGN.md §22.4), the second set of the cosmetic blocks: block models painted with the material
library, each facing north as built (its front, where its operator stands, to the north).

- **Control Console**: a steel console a block wide that runs on into its neighbours: a writing ledge, a control panel
  tilted up toward its operator (toggle switches, push buttons, dial gauges, lamps), a riser behind with a meter strip.
  Its lamps blink (an animated texture: the lamps are lit elements, their colours changing frame by frame).
- **Command Desk**: a steel desk with a linoleum top, a drawer pedestal, a CRT monitor whose green screen scrolls
  (animated, lit) and a red telephone.
- **Filing Cabinet**: four steel drawers, olive drab; open (its block entity holds 27 stacks), the top drawer pulled out.
- **Situation Map Table**: two blocks by two, a map of a coast with its grid, units and arrows; built and painted whole,
  cut into its four blocks' models.

    tools\\.venv\\Scripts\\python.exe tools\\assets\\command_room.py [console|desk|cabinet|table]
"""
from __future__ import annotations

import json
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
from gb.model import BlockModel, moved_polygon, polygon_centre, polygons_of, with_polygons, write_json  # noqa: E402
from gb.tex import FONT, GLYPH_W, Tex  # noqa: E402

ALL = ("north", "south", "east", "west", "up", "down")
SIDES = ("north", "south", "east", "west")
#: The block's sides, culled against a full neighbour.
CULL = {"north": "north", "south": "south", "east": "east", "west": "west", "down": "down", "up": "up"}

# --- the shared paints ----------------------------------------------------------------------------------------------------------
#: Instrument grey: the consoles' and the desk's steel.
GREY_RAMP = M.ramp("#3a3e3c", "#4f5451", "#666b67", "#7e837e", "#979c96", "#b0b5ae", "#c8ccc5")
CONSOLE = M.painted(GREY_RAMP, tone=0.56, wear=0.14)
#: The control panel's face: black crackle paint.
PANEL = M.painted(M.ramp("#121315", "#1b1c1f", "#25272a", "#303236", "#3c3f44", "#4b4e54", "#5d6168"), tone=0.4, wear=0.06)
GREEN_RAMP = M.ramp("#10260f", "#173816", "#1f4b1e", "#2a6228", "#367a33", "#4a9346", "#68ad5f")
GREEN_PAINT = M.painted(GREEN_RAMP, tone=0.5, wear=0.1)
WHITE = np.array([226, 226, 214], np.float32)


def blockstate(name: str, model: str | None = None) -> None:
    """A block facing one of four ways: its model turned to face it."""
    turn = {"north": 0, "east": 90, "south": 180, "west": 270}
    variants = {}
    for f, r in turn.items():
        v = {"model": f"groundburst:block/{model or name}"}
        if r:
            v["y"] = r
        variants[f"facing={f}"] = v
    write_json(paths.ASSETS / "blockstates" / f"{name}.json", {"variants": variants})


def block_item(name: str) -> None:
    """The item is the block's own model."""
    write_json(paths.ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": f"groundburst:block/{name}"}})


def animated(m: BlockModel, frames: list[Tex], frametime: int) -> None:
    """Saves the model with its texture's frames stacked into one strip (each bled at its edges), and its .mcmeta."""
    for t in frames:
        m.bleed(t)
    m.save(frames[0])
    strip = Tex(m.tex_w, m.tex_h * len(frames))
    for i, t in enumerate(frames):
        strip.blit(t, 0, i * m.tex_h, blend=False)
    path = strip.save(m.texture_path())
    write_json(Path(str(path) + ".mcmeta"), {"animation": {"frametime": frametime, "width": m.tex_w, "height": m.tex_h}})


def fill_faces(m: BlockModel, t: Tex, names, colour) -> None:
    """Every face of these boxes one flat colour (lit lamps and screens, drawn whole)."""
    for n in names:
        b = m.get(n)
        for d in b.faces:
            x, y, w, h = m.rect(b, d)
            t.rect(x, y, w, h, (*[int(c) for c in colour], 255))


# --- the Control Console ------------------------------------------------------------------------------------------------------
#: The control panel's tilt (about x, its back edge up), and its front edge on the ledge (the tilt's origin).
TILT = -30.0
TILT_O = (8.0, 10.5, 6.0)
#: The lamps on the panel and on the riser, and their colours lit and dark.
LAMP_COLOURS = {"red": ((255, 72, 52), (70, 16, 14)), "amber": ((255, 178, 44), (74, 50, 14)), "green": ((96, 255, 112), (18, 60, 22)),
                "white": ((236, 240, 255), (64, 66, 72))}


def tilted() -> dict:
    return {"origin": TILT_O, "axis": "x", "angle": TILT}


def console() -> None:
    m = BlockModel("control_console", tex_size=(64, 64), kind="block")
    m.open_ok = set()
    parts: dict[str, list[str]] = {}

    def box(group, n, frm, to, faces=ALL, rotation=None, light=0, cull=None):
        m.box(n, frm, to, faces=faces, rotation=rotation, light=light, cull=cull)
        parts.setdefault(group, []).append(n)

    # The body: the base under the ledge, the ledge, the riser behind the panel.
    box("body", "base", (0, 0, 2), (16, 9.5, 16), faces=("north", "south", "east", "west", "down"), cull=CULL)
    box("body", "ledge", (0, 9.5, 0.5), (16, 10.5, 6), faces=("north", "east", "west", "up", "down"), cull=CULL)
    box("body", "riser", (0, 9.5, 12), (16, 16, 16), faces=("north", "south", "east", "west", "up"), cull=CULL)
    # The control panel: a slab tilted up from the ledge's back edge into the riser, its hidden parts inside them; a
    # quarter pixel in from the block's sides (its sides would lie in the base's planes where it runs down into it), so
    # consoles in a row show their panels as modules with a seam between.
    y0 = TILT_O[1]
    box("panel", "panel", (0.25, y0 - 4.5, TILT_O[2]), (15.75, y0, TILT_O[2] + 7.6), faces=("up", "east", "west"), rotation=tilted())
    # Toggle switches in a row near the operator, push buttons, then dials (painted) and four lamps up the panel.
    z0 = TILT_O[2]
    for k in range(6):
        x = 1.6 + 2.2 * k
        box("switch", f"switch{k}", (x - 0.25, y0, z0 + 1.2), (x + 0.25, y0 + 1.1, z0 + 1.7), faces=("north", "south", "east", "west", "up"),
            rotation=tilted())
    box("button_red", "button_red", (11.6, y0, z0 + 2.4), (13.4, y0 + 0.6, z0 + 4.2), faces=("north", "south", "east", "west", "up"), rotation=tilted())
    for k, (x, c) in enumerate(((2.2, "yellow"), (4.6, "green"), (7.0, "white"))):
        box(f"button_{c}", f"button{k}", (x - 0.6, y0, z0 + 2.9), (x + 0.6, y0 + 0.5, z0 + 4.1), faces=("north", "south", "east", "west", "up"),
            rotation=tilted())
    for k, c in enumerate(("red", "amber", "green", "white")):
        x = 9.8 + 1.55 * k
        box(f"lamp_{c}", f"lamp{k}", (x - 0.5, y0, z0 + 5.4), (x + 0.5, y0 + 0.5, z0 + 6.4), faces=("north", "south", "east", "west", "up"),
            rotation=tilted(), light=15)
    # The riser's meter strip: a lamp each end of a meter window (painted).
    for k, (x, c) in enumerate(((1.6, "amber"), (14.4, "green"))):
        box(f"lamp_{c}", f"rlamp{k}", (x - 0.6, 14.4, 11.5), (x + 0.6, 15.6, 12.0), faces=("north", "east", "west", "up", "down"), light=15)
    m.layout()

    t0 = Tex(m.tex_w, m.tex_h)
    k = K.Kit(m, {}, seed=4101, ao_reach=1.6, turned=True)
    k.paint(t0, parts["body"], CONSOLE)
    k.panels(t0, ["base"], inset=2, rivets=False, only=("north",))
    k.overlay(t0, ["base"], lambda f, img: M.darken(img, f.Y < 1.0, 0.55), only=("north",))      # the toe-kick's shadow
    k.paint(t0, parts["panel"], CONSOLE)
    k.S.paint(t0, parts["panel"], PANEL, only=("up",))
    k.overlay(t0, parts["panel"], panel_face, only=("up",))
    k.overlay(t0, ["riser"], meter_strip, only=("north",))
    k.overlay(t0, ["ledge"], lambda f, img: M.darken(img, (f.Z > 5.0) & (f.Z < 5.6), 0.8), only=("up",))
    k.paint(t0, parts["switch"], K.BRIGHT)
    k.paint(t0, parts["button_red"], K.RED)
    k.paint(t0, parts["button_yellow"], K.YELLOW)
    k.paint(t0, parts["button_green"], GREEN_PAINT)
    k.paint(t0, parts["button_white"], K.WHITE)
    # The lamps' frames: each lamp on or off in turn, as a busy console's would be.
    pattern = {"lamp0": "1100", "lamp1": "1010", "lamp2": "1111", "lamp3": "0110", "rlamp0": "1001", "rlamp1": "1110"}
    frames = []
    for i in range(4):
        t = Tex(m.tex_w, m.tex_h)
        t.blit(t0, 0, 0, blend=False)
        for group in ("lamp_red", "lamp_amber", "lamp_green", "lamp_white"):
            on, off = LAMP_COLOURS[group[5:]]
            for n in parts.get(group, []):
                fill_faces(m, t, [n], on if pattern[n][i] == "1" else off)
        frames.append(t)
    m.fit_display(gui_rotation=(30, 225, 0), gui_margin=1.0)
    animated(m, frames, 6)
    blockstate("control_console")
    block_item("control_console")
    print("control_console", m.tex_w, m.tex_h, "z-fights:", m.zfight()[:6])


def panel_face(f: M.Face, img: np.ndarray) -> None:
    """The panel's face as its operator reads it (z up the panel, x along it): a white legend under each switch, a plate
    round each button, two dials up the panel with their needles, a hazard ring round the red button."""
    rgb = img[..., :3].astype(np.float32)
    # The panel's own coordinates: along it (x) and up it (from its front edge).
    up = np.hypot(f.Y - TILT_O[1], f.Z - TILT_O[2])
    x = f.X
    legend = (np.abs(up - 0.6) < 0.35) & (np.abs(((x - 1.6) / 2.2) - np.round((x - 1.6) / 2.2)) * 2.2 < 0.7) & (x < 13.2)
    rgb[legend] = rgb[legend] * 0.2 + WHITE * 0.8
    plates = (np.abs(up - 1.45) < 0.75) & (np.abs(((x - 1.6) / 2.2) - np.round((x - 1.6) / 2.2)) * 2.2 < 0.6) & (x < 13.2)
    rgb[plates] = rgb[plates] * 0.5 + np.array([150, 152, 150], np.float32) * 0.5
    ring = (np.hypot(x - 12.5, up - 3.3) < 1.6) & (np.hypot(x - 12.5, up - 3.3) > 1.0)
    stripe = ((np.floor(x) + np.floor(up)) % 2) == 0
    rgb[ring & stripe] = (226, 176, 40)
    rgb[ring & ~stripe] = (24, 24, 26)
    for cx in (2.6, 6.2):
        d = np.hypot(x - cx, up - 5.9)
        face = d < 1.35
        rgb[face] = (218, 214, 196)
        rgb[(d >= 1.35) & (d < 1.7)] = (150, 152, 150)
        needle = face & (np.abs((x - cx) * 0.6 - (up - 5.9) * 0.8) < 0.35) & (up > 5.9)
        rgb[needle] = (30, 30, 30)
    img[..., :3] = np.clip(rgb, 0, 255).astype(np.uint8)


def meter_strip(f: M.Face, img: np.ndarray) -> None:
    """The riser's meter window between its lamps: a lit dial scale, black framed."""
    rgb = img[..., :3].astype(np.float32)
    window = (f.X > 3.2) & (f.X < 12.8) & (f.Y > 14.2) & (f.Y < 15.8)
    frame = (f.X > 2.7) & (f.X < 13.3) & (f.Y > 13.9) & (f.Y < 16.1) & ~window
    rgb[frame] = (32, 33, 36)
    rgb[window] = (222, 216, 190)
    ticks = window & (np.floor(f.X) % 2 == 0) & (f.Y > 15.0)
    rgb[ticks] = (40, 40, 40)
    needle = window & (np.abs(f.X - 9.5) < 0.5)
    rgb[needle] = (180, 30, 24)
    img[..., :3] = np.clip(rgb, 0, 255).astype(np.uint8)


# --- the Command Desk ---------------------------------------------------------------------------------------------------------
LINO = M.painted(M.ramp("#1c2420", "#25302a", "#2f3b34", "#3a483f", "#46564b", "#556658", "#677866"), tone=0.5, wear=0.05)
BEIGE_RAMP = M.ramp("#5c5446", "#736a59", "#8b826e", "#a39a84", "#bab29b", "#cfc8b1", "#e2dcc7")
BEIGE = M.painted(BEIGE_RAMP, tone=0.55, wear=0.08)
BEIGE_DARK = M.painted(BEIGE_RAMP, tone=0.38, wear=0.06)
PAPER = M.white_paint(tone=0.86, wear=0.0)
PHOSPHOR = ((92, 255, 120), (36, 120, 52), (8, 26, 12))          # a lit character, a dim one, the screen's black


def desk() -> None:
    m = BlockModel("command_desk", tex_size=(64, 64), kind="block")
    m.open_ok = set()
    parts: dict[str, list[str]] = {}

    def box(group, n, frm, to, faces=ALL, rotation=None, light=0, cull=None):
        m.box(n, frm, to, faces=faces, rotation=rotation, light=light, cull=cull)
        parts.setdefault(group, []).append(n)

    # The desk: its top running on into the next desk's, a drawer pedestal on the right, a panel leg on the left, a
    # modesty panel at the back.
    box("top", "top", (0, 11, 0.5), (16, 12, 15.5), cull={"east": "east", "west": "west"})
    box("body", "pedestal", (10.5, 0, 1.0), (15.5, 11, 15.0), faces=("north", "south", "east", "west", "down"), cull={"down": "down"})
    box("body", "leg", (0.5, 0, 1.0), (1.8, 11, 15.0), faces=("north", "south", "east", "west", "down"), cull={"down": "down"})
    box("body", "modesty", (1.8, 4.0, 14.0), (10.5, 11, 15.0), faces=("north", "south", "down"))
    for j, (a, b) in enumerate(((7.6, 10.6), (4.2, 7.4), (0.6, 4.0))):
        box("handle", f"pull{j}", (12.0, b - 1.0, 0.5), (14.0, b - 0.6, 1.0), faces=("north", "east", "west", "up", "down"))
    # The CRT: its case, the bezel framing the screen a little proud of it, the screen lit, two knobs, a foot.
    box("crt", "case", (5.5, 12.3, 7.0), (12.5, 18.5, 14.0), faces=("south", "east", "west", "up"))
    box("crt", "back", (6.5, 13.0, 14.0), (11.5, 17.6, 15.2), faces=("south", "east", "west", "up", "down"))
    box("crt", "foot", (6.8, 12.0, 8.0), (11.2, 12.3, 13.0), faces=("north", "south", "east", "west"))
    sx0, sx1, sy0, sy1 = 6.4, 11.6, 13.6, 17.6
    box("bezel", "bezel_b", (5.5, 12.3, 6.6), (12.5, sy0, 7.0), faces=("north", "up", "down", "east", "west"))
    box("bezel", "bezel_t", (5.5, sy1, 6.6), (12.5, 18.5, 7.0), faces=("north", "up", "down", "east", "west"))
    box("bezel", "bezel_l", (5.5, sy0, 6.6), (sx0, sy1, 7.0), faces=("north", "east", "west"))
    box("bezel", "bezel_r", (sx1, sy0, 6.6), (12.5, sy1, 7.0), faces=("north", "east", "west"))
    box("screen", "screen", (sx0, sy0, 6.85), (sx1, sy1, 7.0), faces=("north",), light=15)
    for j, x in enumerate((10.2, 11.2)):
        box("knob", f"knob{j}", (x - 0.3, 12.65, 6.2), (x + 0.3, 13.25, 6.6), faces=("north", "east", "west", "up", "down"))
    # The red telephone: its base, the handset on its cradle, its cups.
    box("phone", "phone_base", (1.5, 12.0, 2.5), (5.2, 13.3, 6.3), faces=("north", "south", "east", "west", "up"))
    box("phone", "cradle_a", (1.9, 13.3, 3.9), (2.5, 13.7, 5.0), faces=("north", "south", "east", "west"))
    box("phone", "cradle_b", (4.2, 13.3, 3.9), (4.8, 13.7, 5.0), faces=("north", "south", "east", "west"))
    box("phone", "handset", (1.6, 13.7, 4.1), (5.1, 14.3, 4.8))
    box("phone", "cup_a", (1.2, 13.5, 3.7), (2.4, 14.5, 5.2))
    box("phone", "cup_b", (4.3, 13.5, 3.7), (5.5, 14.5, 5.2))
    # Papers by the keyboard's place, turned a little, and a pen on them.
    paper = {"origin": (8.2, 12.0, 3.5), "axis": "y", "angle": 22.5}
    box("paper", "paper", (6.4, 12.0, 1.6), (10.0, 12.12, 5.4), faces=("north", "south", "east", "west", "up"), rotation=paper)
    box("pen", "pen", (7.0, 12.12, 2.4), (9.6, 12.36, 2.64), faces=("north", "south", "east", "west", "up"), rotation=paper)
    m.layout()

    t0 = Tex(m.tex_w, m.tex_h)
    k = K.Kit(m, {}, seed=4201, ao_reach=1.6, turned=True)
    k.paint(t0, parts["body"], CONSOLE)
    k.overlay(t0, ["pedestal"], lambda f, img: drawers(f, img, ((7.6, 10.6), (4.2, 7.4), (0.6, 4.0))), only=("north",))
    k.paint(t0, parts["top"], K.BRIGHT)
    k.S.paint(t0, parts["top"], LINO, only=("up",))
    k.paint(t0, parts["handle"], K.BRIGHT)
    k.paint(t0, parts["crt"], BEIGE)
    k.overlay(t0, ["case"], lambda f, img: M.darken(img, (np.floor(f.Z) % 2 == 0) & (f.Y > 17.4), 0.6), only=("east", "west"))  # vents
    k.paint(t0, parts["bezel"], BEIGE_DARK)
    k.paint(t0, parts["knob"], K.BLACK)
    k.paint(t0, parts["phone"], K.RED)
    k.overlay(t0, ["phone_base"], lambda f, img: phone_dial(f, img), only=("north", "up"))
    k.paint(t0, parts["paper"], PAPER)
    k.overlay(t0, ["paper"], lambda f, img: M.darken(img, (np.floor(f.X * 0.9 + f.Z * 0.4) % 2 == 0), 0.82), only=("up",))
    k.paint(t0, parts["pen"], K.BLACK)
    # The screen's frames: lines of green characters scrolling up a row each frame, a cursor blinking at the bottom.
    frames = []
    rx, ry, rw, rh = m.rect(m.get("screen"), "north")
    rng = np.random.default_rng(42)
    rows = [rng.random(rw) < 0.62 for _ in range(rh + 8)]
    lengths = [int(rng.integers(1, rw + 1)) for _ in range(rh + 8)]
    for i in range(8):
        t = Tex(m.tex_w, m.tex_h)
        t.blit(t0, 0, 0, blend=False)
        for yy in range(rh):
            line = (i + yy) % len(rows)
            for xx in range(rw):
                lit = xx < lengths[line] and rows[line][xx]
                bright = PHOSPHOR[0] if lit and (xx + line) % 3 else PHOSPHOR[1] if lit else PHOSPHOR[2]
                t.px(rx + xx, ry + yy, (*bright, 255))
        if i % 2 == 0:
            t.px(rx + min(rw - 1, lengths[(i + rh - 1) % len(rows)]), ry + rh - 1, (*PHOSPHOR[0], 255))
        frames.append(t)
    m.fit_display(gui_rotation=(30, 225, 0), gui_margin=1.0)
    animated(m, frames, 10)
    blockstate("command_desk")
    block_item("command_desk")
    print("command_desk", m.tex_w, m.tex_h, "z-fights:", m.zfight()[:6])


def drawers(f: M.Face, img: np.ndarray, spans) -> None:
    """A pedestal's drawer fronts: a dark gap round each drawer, a label holder over its pull."""
    for a, b in spans:
        M.darken(img, (np.abs(f.Y - a) < 0.5) | (np.abs(f.Y - b) < 0.5), 0.55)
        label = (np.abs(f.Y - (b - 1.8)) < 0.5) & (f.X > 12.2) & (f.X < 13.8)
        img[label, :3] = (206, 202, 186)


def phone_dial(f: M.Face, img: np.ndarray) -> None:
    """The telephone's rotary dial: a white disc with its finger holes, on the base's sloping front."""
    if f.d == "north":
        d = np.hypot(f.X - 3.35, f.Y - 12.65)
        img[d < 0.6, :3] = (230, 228, 220)
    else:
        d = np.hypot(f.X - 3.35, f.Z - 3.1)
        img[(d < 0.8), :3] = (230, 228, 220)


# --- the Filing Cabinet ---------------------------------------------------------------------------------------------------------
#: Its drawers (y from, to), the top one first; how far the top drawer comes out when it is open.
DRAWERS = ((12.0, 15.2), (8.2, 11.8), (4.4, 8.0), (0.6, 4.2))
PULL = 5.4
MANILA = M.painted(M.ramp("#6b5a33", "#83703f", "#9a854c", "#b09a5b", "#c3ae6c", "#d3c07f", "#e1d096"), tone=0.6, wear=0.0)


def cabinet(open_: bool) -> None:
    name = "filing_cabinet_open" if open_ else "filing_cabinet"
    m = BlockModel(name, tex_size=(64, 64), kind="block")
    m.open_ok = set()
    parts: dict[str, list[str]] = {}

    def box(group, n, frm, to, faces=ALL, rotation=None, light=0, cull=None):
        m.box(n, frm, to, faces=faces, rotation=rotation, light=light, cull=cull)
        parts.setdefault(group, []).append(n)

    box("body", "body", (2.0, 0, 2.0), (14.0, 15.6, 15.5), faces=("north", "south", "east", "west", "up", "down"), cull={"down": "down"})
    for j, (a, b) in enumerate(DRAWERS):
        out = -PULL if (open_ and j == 0) else 0.0
        box("front", f"front{j}", (2.4, a, 1.4 + out), (13.6, b, 2.0 + out))
        hy = b - 1.1
        box("handle", f"handle{j}", (6.2, hy - 0.3, 0.8 + out), (9.8, hy + 0.3, 1.4 + out), faces=("north", "east", "west", "up", "down"))
    if open_:
        a, b = DRAWERS[0]
        # The drawer pulled out: its sides and bottom, and the folders hanging in it.
        box("tray", "tray_l", (2.8, a + 0.2, 2.0 - PULL), (3.2, b - 0.4, 2.0), faces=("east", "west", "up"))
        box("tray", "tray_r", (12.8, a + 0.2, 2.0 - PULL), (13.2, b - 0.4, 2.0), faces=("east", "west", "up"))
        box("tray", "tray_b", (3.2, a + 0.2, 2.0 - PULL), (12.8, a + 0.6, 2.0), faces=("up", "down"))
        box("folders", "folders", (3.4, a + 0.6, 2.4 - PULL), (12.6, b - 0.7, 2.0), faces=("up",))
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    k = K.Kit(m, {}, seed=4301, ao_reach=1.6, turned=True)
    k.paint(t, parts["body"], K.OLIVE)
    k.paint(t, parts["front"], K.OLIVE)
    if open_:
        # Where the top drawer was: the dark inside of the cabinet.
        k.overlay(t, ["body"], lambda f, img: M.darken(img, f.Y > DRAWERS[0][0], 0.18), only=("north",))
        k.paint(t, parts["tray"], K.OLIVE_DARK)
        k.paint(t, parts["folders"], MANILA)
        k.overlay(t, parts["folders"], folder_tabs, only=("up",))
    k.overlay(t, parts["front"], label_holder, only=("north",))
    k.paint(t, parts["handle"], K.BRIGHT)
    m.fit_display(gui_rotation=(30, 225, 0), gui_margin=1.0)
    m.save(t)
    print(name, m.tex_w, m.tex_h, "z-fights:", m.zfight()[:6])


def label_holder(f: M.Face, img: np.ndarray) -> None:
    """A brass card holder with a white card over each drawer's handle."""
    for a, b in DRAWERS:
        hy = b - 1.1
        holder = (np.abs(f.Y - (hy + 1.0)) < 0.5) & (np.abs(f.X - 8.0) < 1.6)
        img[holder, :3] = (176, 146, 70)
        card = holder & (np.abs(f.X - 8.0) < 1.0)
        img[card, :3] = (226, 224, 214)


def folder_tabs(f: M.Face, img: np.ndarray) -> None:
    """Hanging folders seen from above: a dark line between each, a coloured tab here and there."""
    rgb = img[..., :3].astype(np.float32)
    rows = np.floor(f.Z)
    rgb[(rows % 1.0 == 0) & (np.floor(f.Z * 2) % 2 == 0)] *= 0.72
    tabs = {(-2.0, 4.0): (180, 40, 34), (-1.0, 9.0): (40, 90, 170), (0.0, 6.0): (60, 140, 60), (1.0, 11.0): (200, 170, 40)}
    for (z, x), c in tabs.items():
        rgb[(np.floor(f.Z) == z) & (np.abs(f.X - x) < 0.8)] = c
    img[..., :3] = np.clip(rgb, 0, 255).astype(np.uint8)


def cabinet_files() -> None:
    cabinet(False)
    cabinet(True)
    turn = {"north": 0, "east": 90, "south": 180, "west": 270}
    variants = {}
    for f, r in turn.items():
        for o in (False, True):
            v = {"model": f"groundburst:block/filing_cabinet{'_open' if o else ''}"}
            if r:
                v["y"] = r
            variants[f"facing={f},open={'true' if o else 'false'}"] = v
    write_json(paths.ASSETS / "blockstates" / "filing_cabinet.json", {"variants": variants})
    block_item("filing_cabinet")


# --- the Situation Map Table ----------------------------------------------------------------------------------------------------
#: The table: its map's edge (inside the rim), the map plate's top, the rim's top. Built as it stands facing north: its
#: block at x 0..16, z 0..16 (the placer's), the next along +x (to the placer's right), two more along -z (ahead).
MAP_LO, MAP_HI = (1.5, -14.5), (30.5, 14.5)
MAP_Y, RIM_Y = 13.5, 14.0
SEA = np.array([88, 118, 134], np.float32)
SEA_DEEP = np.array([70, 98, 116], np.float32)
LAND = np.array([200, 190, 146], np.float32)
LOW = np.array([160, 172, 118], np.float32)
COAST = np.array([96, 88, 64], np.float32)
GRID = np.array([40, 44, 48], np.float32)
BLUE = np.array([40, 74, 168], np.float32)
REDS = np.array([178, 36, 30], np.float32)


def cells_of(x0: float, z0: float, x1: float, z1: float):
    """Splits a box's x and z spans at the cells' joins (x 16, z 0): the pieces, each with the cell it lies in."""
    xs = [x0] + [c for c in (16.0,) if x0 < c < x1] + [x1]
    zs_ = [z0] + [c for c in (0.0,) if z0 < c < z1] + [z1]
    for i in range(len(xs) - 1):
        for j in range(len(zs_) - 1):
            a, b, c, d = xs[i], xs[i + 1], zs_[j], zs_[j + 1]
            yield (a, b, c, d), (0 if a < 16 else 1, 0 if d > 0 else 1)


def land_at(x: np.ndarray, z: np.ndarray) -> np.ndarray:
    """How far above the sea the map's ground stands (< 0: sea): a coast running across the map from its south-west to
    its north-east, a peninsula out from it, an island off it."""
    u, v = (x - 16.0) / 14.5, z / 14.5
    h = 0.55 * (u - v) + 0.18
    h += 0.42 * np.exp(-((u + 0.15) ** 2 + (v - 0.05) ** 2) / 0.05)            # the peninsula
    h += 0.6 * np.exp(-((u + 0.55) ** 2 + (v + 0.55) ** 2) / 0.02) - 0.25       # the island
    h += 0.07 * np.sin(u * 9.0 + 1.3) * np.cos(v * 7.0 - 0.4)
    return h


def edge(mask: np.ndarray) -> np.ndarray:
    """The texels of a mask next to one outside it (a line a texel wide along its edge)."""
    pad = np.pad(mask, 1, mode="edge")
    out = ~pad[:-2, 1:-1] | ~pad[2:, 1:-1] | ~pad[1:-1, :-2] | ~pad[1:-1, 2:]
    return mask & out


def map_face(f: M.Face, img: np.ndarray) -> None:
    """The map: sea and land, the low ground green, a dark coast a texel wide; a grid every seven pixels; the front in
    red along the higher ground, an arrow of each side (blue landing from the sea, red pushing across the front), towns."""
    x, z = f.X, f.Z
    h = land_at(x, z)
    land = h >= 0
    rgb = np.where((h < -0.2)[..., None], SEA_DEEP, SEA)
    rgb = np.where(land[..., None], np.where((h < 0.22)[..., None], LOW, LAND), rgb)
    grid = ((np.floor(x - MAP_LO[0]) % 7) == 0) | ((np.floor(z - MAP_LO[1]) % 7) == 0)
    rgb[grid] = rgb[grid] * 0.6 + GRID * 0.4
    rgb[edge(land)] = COAST
    rgb[edge(h > 0.62)] = REDS

    def arrow(x0, z0, x1, z1):
        t = np.clip(((x - x0) * (x1 - x0) + (z - z0) * (z1 - z0)) / ((x1 - x0) ** 2 + (z1 - z0) ** 2), 0, 1)
        d = np.hypot(x - (x0 + t * (x1 - x0)), z - (z0 + t * (z1 - z0)))
        return (d < 0.5) | ((np.hypot(x - x1, z - z1) < 1.7) & (d < 1.3 * (1 - t) + 0.45))
    rgb[arrow(25.0, 9.0, 19.0, 3.5)] = BLUE
    rgb[arrow(7.0, -9.0, 12.5, -3.0)] = REDS
    for tx, tz in ((9.5, 4.5), (5.5, -6.5), (13.5, -11.0)):
        rgb[(np.floor(x) == np.floor(tx)) & (np.floor(z) == np.floor(tz))] = (40, 36, 30)
    img[..., :3] = np.clip(rgb, 0, 255).astype(np.uint8)


def table() -> None:
    m = BlockModel("situation_map_table", tex_size=(128, 128), kind="block")
    m.open_ok = set()
    parts: dict[str, list[str]] = {}

    def box(group, n, frm, to, faces=ALL, rotation=None):
        m.box(n, frm, to, faces=faces, rotation=rotation)
        parts.setdefault(group, []).append(n)

    (x0, z0), (x1, z1) = MAP_LO, MAP_HI
    # The map plate, cut at the cells' joins (no faces between its pieces).
    for k, ((a, b, c, d), cell) in enumerate(cells_of(x0, z0, x1, z1)):
        faces = ["up", "down"] + [s for s, edge in (("west", a == x0), ("east", b == x1), ("north", c == z0), ("south", d == z1)) if edge]
        box("map", f"map{k}", (a, MAP_Y - 1.0, c), (b, MAP_Y, d), faces=tuple(faces))
    # The rim round it, a raised wooden lip, and the apron under it; cut at the joins too.
    rims = [((0, 32), (-16, z0)), ((0, 32), (z1, 16)), ((0, x0), (z0, z1)), ((x1, 32), (z0, z1))]
    for r, ((a, b), (c, d)) in enumerate(rims):
        for k, ((aa, bb, cc, dd), cell) in enumerate(cells_of(a, c, b, d)):
            faces = ["up", "down"] + [s for s, edge in (("west", aa == a), ("east", bb == b), ("north", cc == c), ("south", dd == d)) if edge]
            box("rim", f"rim{r}_{k}", (aa, MAP_Y - 1.0, cc), (bb, RIM_Y, dd), faces=tuple(faces))
    aprons = [((0.6, 31.4), (-15.4, -14.6)), ((0.6, 31.4), (14.6, 15.4)), ((0.6, 1.4), (-14.6, 14.6)), ((30.6, 31.4), (-14.6, 14.6))]
    for r, ((a, b), (c, d)) in enumerate(aprons):
        for k, ((aa, bb, cc, dd), cell) in enumerate(cells_of(a, c, b, d)):
            faces = [s for s, edge in (("west", aa == a), ("east", bb == b), ("north", cc == c), ("south", dd == d)) if edge] + ["down"]
            box("apron", f"apron{r}_{k}", (aa, 10.8, cc), (bb, MAP_Y - 1.0, dd), faces=tuple(faces))
    for k, (lx, lz) in enumerate(((0.6, -15.4), (29.4, -15.4), (0.6, 13.4), (29.4, 13.4))):
        box("leg", f"leg{k}", (lx, 0, lz), (lx + 2.0, 10.8, lz + 2.0), faces=("north", "south", "east", "west"))
    # On the map: the two sides' unit counters, pins at the front, and the pointer laid across it.
    for k, (ux, uz, side) in enumerate(((7.5, 6.5, "blue"), (11.0, 9.0, "blue"), (4.0, 2.0, "blue"), (22.0, -2.5, "red"),
                                         (25.0, -6.5, "red"), (19.5, -9.0, "red"))):
        box(f"unit_{side}", f"unit{k}", (ux - 0.8, MAP_Y, uz - 0.5), (ux + 0.8, MAP_Y + 0.6, uz + 0.5), faces=("north", "south", "east", "west", "up"))
    for k, (px, pz, c) in enumerate(((14.5, -1.5, "red"), (17.0, 2.5, "red"), (9.5, -4.0, "blue"))):
        box("pin", f"pin{k}", (px - 0.15, MAP_Y, pz - 0.15), (px + 0.15, MAP_Y + 1.3, pz + 0.15), faces=("north", "south", "east", "west"))
        box(f"pinhead_{c}", f"pinhead{k}", (px - 0.35, MAP_Y + 1.3, pz - 0.35), (px + 0.35, MAP_Y + 1.9, pz + 0.35),
            faces=("north", "south", "east", "west", "up", "down"))
    pointer = {"origin": (20.0, MAP_Y, 9.0), "axis": "y", "angle": 22.5}
    box("pointer", "pointer", (14.0, MAP_Y, 8.8), (26.0, MAP_Y + 0.35, 9.2), faces=("north", "south", "east", "west", "up"), rotation=pointer)
    box("pointer_tip", "pointer_tip", (13.2, MAP_Y, 8.85), (14.0, MAP_Y + 0.3, 9.15), faces=("north", "south", "west", "up"), rotation=pointer)
    m.layout()

    t = Tex(m.tex_w, m.tex_h)
    k = K.Kit(m, {}, seed=4401, ao_reach=1.6, turned=True)
    wood = M.wood(tone=0.42)
    k.paint(t, parts["rim"] + parts["apron"], wood)
    k.paint(t, parts["leg"], K.STEEL)
    k.paint(t, parts["map"], M.white_paint(tone=0.8, wear=0.0))
    k.overlay(t, parts["map"], map_face, only=("up",))
    k.paint(t, parts["unit_blue"], M.painted(M.ramp("#0e1c3c", "#162a56", "#20396f", "#2c4b89", "#3b5ea2", "#5276ba", "#7193d0"), tone=0.5, wear=0.1))
    k.paint(t, parts["unit_red"], K.RED)
    k.paint(t, parts["pin"], K.BRIGHT)
    k.paint(t, parts["pinhead_red"], K.RED)
    k.paint(t, parts["pinhead_blue"], M.painted(M.ramp("#0e1c3c", "#162a56", "#20396f", "#2c4b89", "#3b5ea2", "#5276ba", "#7193d0"), tone=0.55))
    k.paint(t, parts["pointer"], M.wood(tone=0.62))
    k.paint(t, parts["pointer_tip"], K.BLACK)
    m.fit_display(gui_rotation=(30, 225, 0), gui_margin=1.0)
    m.save(t)
    split_table(m)
    print("situation_map_table", m.tex_w, m.tex_h, "z-fights:", m.zfight()[:6])


def split_table(m: BlockModel) -> None:
    """The whole table's model written as its four blocks' (each moved to its own block), in the whole's texture; the
    whole stays as the item's model."""
    whole = json.loads((paths.MODELS / "block" / f"{m.name}.json").read_text(encoding="utf-8"))
    for u in (0, 1):
        for w in (0, 1):
            dx, dz = -16.0 * u, 16.0 * w
            out = {k: v for k, v in whole.items() if k not in ("elements", "display")}
            els = []
            for el in whole["elements"]:
                cx = (el["from"][0] + el["to"][0]) / 2
                cz = (el["from"][2] + el["to"][2]) / 2
                if (cx >= 16) != (u == 1) or (cz < 0) != (w == 1):
                    continue
                el = json.loads(json.dumps(el))
                el["from"][0] += dx
                el["to"][0] += dx
                el["from"][2] += dz
                el["to"][2] += dz
                if "rotation" in el and "origin" in el["rotation"]:
                    el["rotation"]["origin"][0] += dx
                    el["rotation"]["origin"][2] += dz
                els.append(el)
            out["elements"] = els
            polys = []
            for p in polygons_of(whole):
                cx, _, cz = polygon_centre(p)
                if (cx >= 16) == (u == 1) and (cz < 0) == (w == 1):
                    polys.append(moved_polygon(p, (dx, 0, dz)))
            write_json(paths.MODELS / "block" / f"{m.name}_{u}_{w}.json", with_polygons(out, polys))
    turn = {"north": 0, "east": 90, "south": 180, "west": 270}
    variants = {}
    for f, r in turn.items():
        for u in (0, 1):
            for w in (0, 1):
                v = {"model": f"groundburst:block/{m.name}_{u}_{w}"}
                if r:
                    v["y"] = r
                variants[f"facing={f},u={u},w={w}"] = v
    write_json(paths.ASSETS / "blockstates" / f"{m.name}.json", {"variants": variants})
    block_item(m.name)


def main() -> None:
    which = sys.argv[1:] or ["console", "desk", "cabinet", "table"]
    if "table" in which:
        table()
    if "console" in which:
        console()
    if "desk" in which:
        desk()
    if "cabinet" in which:
        cabinet_files()


if __name__ == "__main__":
    main()
