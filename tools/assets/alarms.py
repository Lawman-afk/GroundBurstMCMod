"""The base alarm system's blocks (DESIGN.md §18.6), painted with the material library.

Wall devices (block models, built facing north: the back against the block's south face, z 16):
- Missile Warning Receiver: an olive receiver cabinet, a CRT on its front (a green trace standing by; a red ALERT bar
  warning), a red lamp on top (lit while it warns) and a whip antenna.
- Alarm Panel: a grey steel enclosure banded in hazard paint, a red mushroom button in a yellow guard, a key switch, an
  amber lamp (red, lit, while its channel is raised by it), the panel's label.
- Hazard Monitor: an olive monitor, a dial (its needle in the green; in the red while it alarms), a trefoil and a gas
  placard, a sensor grille, a red lamp (lit in alarm).
- Alert Display: a steel bezel round a dark screen (its text drawn by the game).
- Alarm Relay: a grey relay cabinet, its front a lamp (lit while it gives redstone), a terminal strip and a label.
Round devices (built standing on the wall, pointing up, and turned to the wall by their block states):
- Klaxon: a black motor housing on a wall bracket, a chromed tube and its flared bell, the mouth dark.
- Alarm Bell: a signal-red gong on a backplate, its crown and boss, the brass striker at its foot.
- Emergency Light (block model, any face): a cast bulkhead base, a lens behind a two-by-two cage: dark, white, or red
  and flashing (an animated texture: every one in step).
GeckoLib:
- Air-Raid Siren: a steel plate, a grey motor pedestal; its head (bone "head", turned by the game while it sounds) a
  yellow rotor drum with its cap, the big square horn flaring forward with a rim at its mouth and the chopper's ports at
  its throat, the blower behind.
- Warning Beacon (built standing up; the game turns it to its face): a gunmetal base and gasket; the lens on the rotor
  (bone "rotor"): dark when off ("lens"), one side bright as it turns ("lens_spot"), all bright ("lens_full"), and two
  light beams ("beam"); five colours, each its own texture and glowmask.
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))

import numpy as np  # noqa: E402

from gb import machine_kit as K  # noqa: E402
from gb import materials as M  # noqa: E402
from gb import palette as P  # noqa: E402
from gb import panels as PN  # noqa: E402
from gb import paths  # noqa: E402
from gb.geo import Animation, GeoModel, save_animations  # noqa: E402
from gb.model import BlockModel, write_json  # noqa: E402
from gb.tex import Tex  # noqa: E402
import machines as MC  # noqa: E402
import warheads as W  # noqa: E402

SIDES = ("north", "south", "east", "west")
FRONT = ("north", "east", "west", "up", "down")
CLEAR = (0, 0, 0, 0)
GREY = M.painted(M.GREY_PAINT_RAMP, tone=0.55, wear=0.25, rust=0.04)
OLIVE = M.painted(M.OLIVE_RAMP, tone=0.55, wear=0.25)
BLACK_ENAMEL = M.painted(M.BLACK_RAMP, tone=0.45, wear=0.2)
CHROME = M.steel(tone=0.72, brushed=0.08)
SIGNAL_RED = M.painted(M.RED_PAINT_RAMP, tone=0.55, wear=0.18)
CD_YELLOW = M.painted(M.YELLOW_PAINT_RAMP, tone=0.6, wear=0.32, rust=0.08)
BRASS = K.BRASS

#: The Warning Beacon's colours: (name, lens ramp light to dark as RGB).
BEACON_COLOURS = {
    "red": ((255, 92, 70), (214, 36, 24), (120, 14, 10), (60, 8, 6)),
    "amber": ((255, 214, 96), (246, 150, 22), (150, 82, 8), (72, 40, 6)),
    "blue": ((130, 190, 255), (36, 104, 236), (16, 46, 130), (8, 22, 64)),
    "green": ((150, 255, 140), (40, 196, 60), (16, 104, 30), (8, 52, 16)),
    "white": ((255, 255, 246), (226, 232, 238), (140, 148, 156), (70, 76, 82)),
}


def bid(name: str) -> str:
    return f"groundburst:block/{name}"


def item(name: str, model_id: str) -> None:
    write_json(paths.ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": model_id}})


Y_TURN = {"north": 0, "east": 90, "south": 180, "west": 270}


def wall_state(name: str, off: str, on: str | None = None, upright: bool = False) -> None:
    """Four facings by two: its model (lit when active, if it has one), turned to the wall it is on; an upright model
    (built pointing up) laid onto the wall first."""
    variants = {}
    for facing, y in Y_TURN.items():
        for active in ("false", "true"):
            v = {"model": bid(on if active == "true" and on else off)}
            if upright:
                v["x"] = 90
            if y:
                v["y"] = y
            variants[f"active={active},facing={facing}"] = v
    write_json(paths.ASSETS / "blockstates" / f"{name}.json", {"variants": variants})


SIX = {"north": {}, "east": {"y": 90}, "south": {"y": 180}, "west": {"y": 270}, "up": {"x": 270}, "down": {"x": 90}}


class OnBone:
    """A GeoModel's boxes going into one bone (so warheads.section can build round parts in it)."""

    def __init__(self, g: GeoModel, bone: str):
        self.__dict__["_g"] = g
        self.__dict__["_bone"] = bone

    def box(self, name, frm, to, faces=("north", "south", "east", "west", "up", "down"), rotation=None, **_):
        return self._g.box(name, frm, to, faces=faces, rotation=rotation, bone=self._bone)

    def __getattr__(self, key):
        return getattr(self._g, key)


def _round(k: K.Kit, names, top=True) -> None:
    W.round_faces(k.S, names, top, False)


# --- the Missile Warning Receiver ------------------------------------------------------------------------------------

def receiver(on: bool) -> str:
    name = "missile_warning_receiver" + ("_on" if on else "")
    m = BlockModel(name, tex_size=(64, 64))
    m.open_ok = set()
    m.box("cabinet", (3, 2, 10), (13, 12, 16), faces=FRONT)
    m.box("screen", (4.5, 5.5, 9.7), (11.5, 10.5, 10), faces=FRONT, light=15)
    m.box("lamp", (6, 12, 11.5), (10, 13.5, 14.5), faces=("north", "south", "east", "west", "up"), light=15 if on else 0)
    m.box("antenna_foot", (10.6, 12, 12.6), (12.4, 12.8, 14.4), faces=SIDES + ("up",))
    m.box("antenna", (11.25, 12.8, 13.25), (11.75, 16, 13.75), faces=SIDES + ("up",))
    m.layout()
    tex = Tex(m.tex_w, m.tex_h)
    k = K.Kit(m, {}, seed=901)
    k.paint(tex, "cabinet", OLIVE)
    k.panels(tex, "cabinet", inset=1, min_size=6, only=("east", "west"))
    k.ribs(tex, "cabinet", every=1.5, only=("up",))
    m.paint_all(tex, "cabinet", lambda t, r, d: K.plate(t, r[0] + 3, r[1] + r[3] - 3, 4, 2) if d == "north" else None)
    k.paint(tex, "antenna_foot", K.DARK)
    k.paint(tex, "antenna", K.GALV)

    def screen(t: Tex, r, d):
        x, y, w, h = r
        if d != "north":
            t.rect(x, y, w, h, P.GUNMETAL[2])
            return
        t.rect(x, y, w, h, P.BLACK_PAINT.deep)
        if on:                                                   # a red bar of warning, ALERT under it
            t.rect(x + 1, y + 1, w - 2, 2, P.INDICATOR_RED[4])
            for xx in range(x + 1, x + w - 1, 2):
                t.px(xx, y + h - 2, P.INDICATOR_RED[3])
        else:                                                    # standing by: a green trace
            for xx in range(x, x + w):
                t.px(xx, y + h // 2 + ((xx * 3) % 3 == 0) - ((xx * 5) % 4 == 1), P.INDICATOR_GREEN[4])
    m.paint_all(tex, "screen", screen)

    def lamp(t: Tex, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, P.INDICATOR_RED.light if on else P.INDICATOR_RED[1])
        if w > 2 and h > 1:
            t.px(x + 1, y, P.WHITE_CERAMIC[6] if on else P.INDICATOR_RED[3])          # a glint
    m.paint_all(tex, "lamp", lamp)
    m.save(tex)
    return name


# --- the Alarm Panel ------------------------------------------------------------------------------------------------

def panel(on: bool) -> str:
    name = "alarm_panel" + ("_on" if on else "")
    m = BlockModel(name, tex_size=(64, 64))
    m.open_ok = set()
    m.box("plate", (2, 2, 13), (14, 14, 16), faces=FRONT)
    m.box("guard", (5.5, 4.5, 12.2), (10.5, 9.5, 13), faces=FRONT)
    m.box("button", (6.5, 5.5, 11.2), (9.5, 8.5, 12.2), faces=FRONT)
    m.box("lamp", (11, 11, 12.4), (13, 13, 13), faces=FRONT, light=15 if on else 0)
    m.box("key", (3.5, 11, 12.5), (5, 12.5, 13), faces=FRONT)
    m.layout()
    tex = Tex(m.tex_w, m.tex_h)
    k = K.Kit(m, {}, seed=911)
    k.paint(tex, "plate", GREY)
    k.overlay(tex, "plate", lambda f, img: M.paste(img, k.on(f, K.HAZARD), K.ring(f) < 1), only=("north",))

    def label(t: Tex, r, d):                                     # the panel's label over its guard: white, a red word on it
        x, y, w, h = r
        if d == "north":
            t.rect(x + 3, y + 2, 6, 2, P.WHITE_CERAMIC[5])
            t.hline(x + 4, y + 3, 4, P.INDICATOR_RED[2])
    m.paint_all(tex, "plate", label)
    k.paint(tex, "guard", K.YELLOW)
    k.paint(tex, "button", SIGNAL_RED)
    k.overlay(tex, "button", lambda f, img: M.lighten(img, (f.border["top"] < 1) | (f.border["left"] < 1), 1.3, 12), only=("north",))
    k.paint(tex, "key", K.DARK)
    m.paint_all(tex, "key", lambda t, r, d: t.px(r[0] + r[2] // 2, r[1] + r[3] // 2, P.BRASS[5]) if d == "north" else None)
    m.paint_all(tex, "lamp", lambda t, r, d: t.rect(*r, P.INDICATOR_RED.light if on else P.NUCLEAR_AMBER[2]))
    m.save(tex)
    return name


# --- the Hazard Monitor ---------------------------------------------------------------------------------------------

def monitor(on: bool) -> str:
    name = "hazard_monitor" + ("_on" if on else "")
    m = BlockModel(name, tex_size=(64, 64))
    m.open_ok = set()
    m.box("body", (3, 2, 12), (13, 14, 16), faces=FRONT)
    m.box("meter", (4.5, 8.5, 11.7), (11.5, 12.5, 12), faces=FRONT)
    m.box("lamp", (7, 6, 11.5), (9, 7.5, 12), faces=FRONT, light=15 if on else 0)
    m.layout()
    tex = Tex(m.tex_w, m.tex_h)
    k = K.Kit(m, {}, seed=921)
    k.paint(tex, "body", OLIVE)
    k.panels(tex, "body", inset=1, min_size=6, only=("east", "west"))

    def front(t: Tex, r, d):
        x, y, w, h = r
        if d != "north":
            return
        K.trefoil(t, x, y + h - 5)                                   # radiation, lower left
        gx, gy = x + w - 4, y + h - 5                                # poison gas, lower right: a droplet on yellow
        t.rect(gx, gy, 4, 4, P.HAZARD_YELLOW.base)
        t.px(gx + 2, gy + 1, P.HAZARD_BLACK.base)
        t.rect(gx + 1, gy + 2, 2, 1, P.HAZARD_BLACK.base)
    m.paint_all(tex, "body", front)
    k.paint(tex, "meter", K.DARK)

    def dial(t: Tex, r, d):
        x, y, w, h = r
        if d != "north":
            return
        t.rect(x, y, w, h, P.WHITE_CERAMIC[5])
        for i, c in enumerate((P.INDICATOR_GREEN[3], P.INDICATOR_GREEN[3], P.NUCLEAR_AMBER[3], P.NUCLEAR_AMBER[3], P.INDICATOR_RED[3])):
            t.px(x + 1 + i, y + 1, c)                                # its scale, green to red
        cx = x + w // 2
        tip = x + w - 2 if on else x + 1                             # the needle: in the red, or resting in the green
        t.px(cx, y + h - 1, P.BLACK_PAINT.deep)
        t.px((cx + tip) // 2, y + h - 2, P.BLACK_PAINT.deep)
        t.px(tip, y + 2, P.BLACK_PAINT.deep)
    m.paint_all(tex, "meter", dial)
    m.paint_all(tex, "lamp", lambda t, r, d: t.rect(*r, P.INDICATOR_RED.light if on else P.INDICATOR_RED[1]))
    m.save(tex)
    return name


# --- the Klaxon and the Alarm Bell (built pointing up) --------------------------------------------------------------

#: The klaxon's horn: each ring's radius, mouth last (an exponential flare, ring by ring), each 0.8 px tall but the rim.
KLAXON_FLARE = (2.0, 2.4, 2.9, 3.5, 4.2)
KLAXON_RIM = 4.6


def klaxon() -> str:
    """A motor-driven diaphragm horn on a wall bracket (built pointing up; its blockstate turns it out from the wall): a
    black motor housing with a red band on a saddle bracket, a terminal box on its side, the throat, and the projector
    flaring ring by ring to a rolled rim, its mouth dark. Every ring's planks are sunk only the 0.06 px that keeps their
    caps apart ("alternate"); the middle where a ring's planks overlap is always covered by the ring beside it, so no
    ring needs the bigger stagger that left the old horn's edges ragged."""
    name = "klaxon"
    m = BlockModel(name, tex_size=(64, 64))
    m.open_ok = set()
    m.box("plate", (4.5, 0, 4.5), (11.5, 1, 11.5), faces=SIDES + ("up",))
    m.box("bracket", (6, 1, 6), (10, 2.5, 10), faces=SIDES)
    housing = W.section(m, "housing", 3.0, 2.5, 7.5, 16, caps=("up", "down"), stagger="alternate")
    m.box("terminal", (10.8, 3.6, 6.9), (12.0, 6.4, 9.1), faces=("north", "south", "east", "up", "down"))
    m.open_ok.add(("terminal", "west"))                               # against the housing
    throat = W.section(m, "throat", 1.6, 7.5, 9.0, 8, caps=())
    flare = []
    y = 9.0
    for i, r in enumerate(KLAXON_FLARE):
        flare += W.section(m, f"flare{i}", r, y, y + 0.8, W.sides_for(r), caps=("down",), stagger="alternate")
        y += 0.8
    rim = W.section(m, "rim", KLAXON_RIM, y, y + 0.6, 16, caps=("up", "down"), stagger="alternate")
    m.layout()
    tex = Tex(m.tex_w, m.tex_h)
    k = K.Kit(m, {}, seed=931, turned=True)
    for names in (housing, throat, flare, rim):
        _round(k, names)
    k.paint(tex, ["plate", "bracket"], K.DARK)
    k.overlay(tex, "plate", lambda f, img: M.rivets(f, img, every=5, inset=1), only=("up",))
    k.paint(tex, housing + ["terminal"], BLACK_ENAMEL)
    k.overlay(tex, housing, lambda f, img: M.paste(img, k.on(f, SIGNAL_RED), (f.Y >= 4.6) & (f.Y < 5.4)) if f.d in SIDES else None)

    def end_bell(f, img):                                             # the housing's ends: a lit lip and its screws
        if f.d in ("up", "down"):
            r = np.hypot(f.X - 8, f.Z - 8)
            M.lighten(img, r > 2.5, 1.18, 6)
            M.darken(img, (r > 2.0) & (r < 2.3), 0.7)
    k.overlay(tex, housing, end_bell)
    k.overlay(tex, "terminal", lambda f, img: M.darken(img, (np.abs(f.Y - 5) < 0.35) & (f.d == "east"), 0.4))   # its cable gland
    k.paint(tex, throat + flare + rim, CHROME)

    def mouth(f, img):                                                # the mouth: dark inside the rolled rim
        if f.d == "up":
            r = np.hypot(f.X - 8, f.Z - 8)
            img[r < KLAXON_RIM - 0.9, :3] = P.BLACK_PAINT.deep[:3]
            M.darken(img, (r >= KLAXON_RIM - 0.9) & (r < KLAXON_RIM - 0.5), 0.55)
    k.overlay(tex, rim, mouth)
    m.save(tex)
    return name


def bell() -> str:
    name = "alarm_bell"
    m = BlockModel(name, tex_size=(64, 64))
    m.open_ok = set()
    m.box("plate", (4, 0, 1.5), (12, 1, 12), faces=SIDES + ("up",))
    skirt = W.section(m, "skirt", 5.0, 1.0, 3.0, 16, caps=("up", "down"), stagger="full")
    dome = W.section(m, "dome", 3.4, 3.0, 4.2, 16, caps=("up",), stagger="full")
    boss = W.section(m, "boss", 1.0, 4.2, 4.8, 8, caps=("up",))
    m.box("striker_box", (6.5, 1, 1.8), (9.5, 2.0, 3.6), faces=SIDES + ("up",))
    m.box("striker", (7.3, 2.0, 2.4), (8.7, 3.2, 3.2), faces=SIDES + ("up",))
    m.layout()
    tex = Tex(m.tex_w, m.tex_h)
    k = K.Kit(m, {}, seed=941, turned=True)
    _round(k, skirt)
    _round(k, dome)
    _round(k, boss)
    k.paint(tex, "plate", GREY)
    k.paint(tex, skirt + dome, SIGNAL_RED)

    def shine(f, img):                                            # a ring of shine round the dome
        if f.d == "up":
            r = np.hypot(f.X - 8, f.Z - 8)
            M.lighten(img, (r > 3.8) & (r < 4.4), 1.25, 10)
    k.overlay(tex, skirt, shine)
    k.paint(tex, boss, CHROME)
    k.paint(tex, ["striker_box"], K.DARK)
    k.paint(tex, ["striker"], BRASS)
    m.save(tex)
    return name


# --- the Emergency Light (any face; three looks: off, white, red flashing) ------------------------------------------

def emergency_light(lamp: str) -> str:
    name = "emergency_light" + ("" if lamp == "off" else "_" + lamp)
    lit = lamp != "off"

    def build() -> BlockModel:
        m = BlockModel(name, tex_size=(64, 32))
        m.open_ok = set()
        m.box("base", (4, 3, 14.5), (12, 13, 16), faces=FRONT)
        m.box("lens", (5, 4, 11.5), (11, 12, 14.5), faces=FRONT, light=15 if lit else 0, shade=not lit)
        # The cage: two hoops round the lens's sides and across its front, two over its top, front and bottom, each
        # running back to the base (the across hoops a little in front of the up-and-down ones where they cross).
        for i, (y0, y1) in enumerate(((6.4, 7.0), (9.0, 9.6))):
            m.box(f"bar_h{i}", (4.3, y0, 10.7), (11.7, y1, 11.2), faces=FRONT)
            m.box(f"bar_h{i}w", (4.3, y0, 11.2), (4.8, y1, 14.5), faces=("east", "west", "up", "down"))
            m.box(f"bar_h{i}e", (11.2, y0, 11.2), (11.7, y1, 14.5), faces=("east", "west", "up", "down"))
        for i, (x0, x1) in enumerate(((6.4, 7.0), (9.0, 9.6))):
            m.box(f"bar_v{i}", (x0, 3.3, 10.9), (x1, 12.7, 11.4), faces=FRONT)
            m.box(f"bar_v{i}t", (x0, 12.2, 11.4), (x1, 12.7, 14.5), faces=("east", "west", "up", "down"))
            m.box(f"bar_v{i}b", (x0, 3.3, 11.4), (x1, 3.8, 14.5), faces=("east", "west", "up", "down"))
        for n in ("bar_h0w", "bar_h0e", "bar_h1w", "bar_h1e", "bar_v0t", "bar_v0b", "bar_v1t", "bar_v1b"):
            m.open_ok.add((n, "north"))                          # against the bar across the front
            m.open_ok.add((n, "south"))                          # against the base
        m.layout()
        return m

    def paint(m: BlockModel, bright: bool) -> Tex:
        tex = Tex(m.tex_w, m.tex_h)
        k = K.Kit(m, {}, seed=951)
        k.paint(tex, "base", GREY)
        k.overlay(tex, "base", lambda f, img: M.rivets(f, img, every=4, inset=1), only=("north",))
        k.paint(tex, [b.name for b in m.boxes if b.name.startswith("bar_")], M.painted(M.ARMOUR_RAMP, tone=0.4, wear=0.12))   # clean: rust reads as specks on bars this thin
        ramp = {"off": ((210, 212, 206), (160, 164, 160), (110, 114, 112)),
                "white": ((255, 252, 236), (255, 238, 196), (236, 210, 150)),
                "red": ((255, 110, 86), (236, 40, 26), (150, 16, 10)) if bright else ((150, 30, 22), (110, 18, 12), (70, 10, 8))}[lamp]

        def lens(t: Tex, r, d):
            x, y, w, h = r
            t.rect(x, y, w, h, (*ramp[1], 255))
            for yy in range(y, y + h):
                for xx in range(x, x + w):
                    if (xx + yy) % 3 == 0:
                        t.px(xx, yy, (*ramp[2], 255))           # the lens's prisms
            if d == "north" and w > 3 and h > 3:
                t.rect(x + w // 2 - 1, y + h // 2 - 1, 2, 2, (*ramp[0], 255))   # the bulb behind it
        m.paint_all(tex, "lens", lens)
        m.bleed(tex)
        return tex

    m = build()
    if lamp != "red":
        m.save(paint(m, True))
        return name
    # Red: two frames, bright and dim, half a second each: every emergency light flashes in step.
    a, b = paint(m, True), paint(m, False)
    m.save(a)
    sheet = Tex(m.tex_w, m.tex_h * 2)
    for yy in range(m.tex_h):
        for xx in range(m.tex_w):
            sheet.px(xx, yy, a.get(xx, yy))
            sheet.px(xx, yy + m.tex_h, b.get(xx, yy))
    path = sheet.save(m.texture_path())
    write_json(Path(str(path) + ".mcmeta"), {"animation": {"frametime": 10, "width": m.tex_w, "height": m.tex_h}})
    return name


# --- the Alert Display and the Alarm Relay ---------------------------------------------------------------------------

def display() -> str:
    name = "alert_display"
    m = BlockModel(name, tex_size=(64, 32))
    m.open_ok = set()
    m.box("frame", (1, 3, 13), (15, 13, 16), faces=FRONT)
    m.layout()
    tex = Tex(m.tex_w, m.tex_h)
    k = K.Kit(m, {}, seed=961)
    k.paint(tex, "frame", K.GUN)

    def screen(t: Tex, r, d):
        x, y, w, h = r
        if d == "north":
            t.rect(x + 1, y + 1, w - 2, h - 2, P.BLACK_PAINT.deep)
            for yy in range(y + 2, y + h - 1, 2):
                for xx in range(x + 1, x + w - 1):
                    t.px(xx, yy, (14, 24, 16, 255))              # its faint scanlines
    m.paint_all(tex, "frame", screen)
    m.save(tex)
    return name


def relay_textures() -> None:
    def side() -> Tex:
        img = PN.face(GREY, style="inset", seed=971)
        PN.louvres(img, 4, 4, 8, 8)
        return M.to_tex(img)

    def front(on: bool) -> Tex:
        img = PN.face(GREY, style="inset", seed=972)
        PN.bevel_box(img, 5, 3, 6, 4, False, 0.8)
        t = M.to_tex(img)
        t.rect(6, 4, 4, 2, P.INDICATOR_RED.light if on else P.INDICATOR_RED[1])            # its lamp
        for x in range(4, 12, 2):
            t.px(x, 10, P.COPPER[5])                                                      # the terminal strip's screws
            t.px(x, 11, P.BLACK_PAINT.deep)
        K.plate(t, 5, 12, 6, 2)
        return t
    side().save(paths.TEXTURES / "block" / "alarm_relay_side.png")
    M.to_tex(PN.face(GREY, style="inset", seed=973)).save(paths.TEXTURES / "block" / "alarm_relay_top.png")
    front(False).save(paths.TEXTURES / "block" / "alarm_relay_front.png")
    front(True).save(paths.TEXTURES / "block" / "alarm_relay_front_on.png")
    for suffix, f in (("", "alarm_relay_front"), ("_on", "alarm_relay_front_on")):
        write_json(paths.MODELS / "block" / f"alarm_relay{suffix}.json", {"parent": "minecraft:block/orientable", "textures": {
            "front": bid(f), "side": bid("alarm_relay_side"), "top": bid("alarm_relay_top")}})


# --- the Air-Raid Siren (GeckoLib) ------------------------------------------------------------------------------------

#: The siren's horn: its axis height (the drum's middle), the throat and the mouth (z), their half-sizes (the walls'
#: outer faces), its walls' thickness; each wall's centre band (half-width) and how far it stands proud of the plates
#: beside it (on both faces: a seam you can see, wide enough to draw as a line, and clear of the drum's top and bottom).
HORN_CY, HORN_THROAT, HORN_MOUTH, HORN_A, HORN_B, HORN_T = 11.5, -3.6, -13.2, 2.4, 5.0, 0.6
HORN_BAND, HORN_PROUD = 1.4, 0.25

def siren() -> None:
    name = "air_raid_siren"
    g = GeoModel(name, tex_size=(128, 128))
    g.open_ok = set()
    g.bone("frame")
    g.bone("head", pivot=(0, 7.5, 0), parent="frame")
    F, H = OnBone(g, "frame"), OnBone(g, "head")
    g.box("plate", (-6, 0, -6), (6, 1.5, 6), faces=SIDES + ("up",), bone="frame")
    motor = W.section(F, "motor", 3.6, 1.5, 7.3, 16, cx=0, cz=0, caps=(), stagger="alternate", step=0.12)
    turn = W.section(H, "turn", 4.4, 7.5, 8.5, 16, cx=0, cz=0, caps=("up", "down"), stagger="alternate", step=0.12)
    drum = W.section(H, "drum", 4.0, 8.5, 14.5, 16, cx=0, cz=0, caps=("up",), stagger="alternate", step=0.12)
    cap = W.section(H, "cap", 2.8, 14.5, 15.5, 16, cx=0, cz=0, caps=("up",), stagger="alternate", step=0.12)
    g.box("bolt", (-0.9, 15.5, -0.9), (0.9, 15.9, 0.9), faces=SIDES + ("up",), bone="head")             # the cap's bolt
    # The horn: a square flare from the throat (just inside the drum's face) to the mouth. A flared square horn's walls
    # are trapezoids, which boxes cannot be (no set of rectangles fills a trapezoid's sharp corners), so each wall is
    # three plates: a centre band straight down its middle, standing proud on both faces, and two side plates fanned in
    # the wall's own plane so their outer edges lie exactly on the horn's corner lines: walls meet flush along their
    # corners. Each side plate reaches the band's edge at the mouth; near the throat the two side plates overlap under
    # the band (hidden there, inside and out). Every plate runs back into the drum, and its slanted end at the mouth is
    # held inside the rim.
    cy, zt, zm, a, b, t = HORN_CY, HORN_THROAT, HORN_MOUTH, HORN_A, HORN_B, HORN_T
    run = zt - zm                                                   # along the axis, throat to mouth
    tau = math.atan((b - a) / run)                                  # each wall's lean out from the axis
    ls = run / math.cos(tau)                                        # a wall's length down its slope
    phi = math.atan((b - a) / ls)                                   # its corner edges' fan, in its own plane
    c, proud, back = HORN_BAND, HORN_PROUD, 1.4
    w = (b - c) * math.cos(phi)                                     # a side plate's width: it reaches the band's edge at the mouth
    reach = ls / math.cos(phi) - w * math.tan(phi) / 2              # a side plate's length (its slanted end straddles the mouth)
    lean, fan = math.degrees(tau), math.degrees(phi)
    horn_inner = {}
    for wall, pivot, rot in (("top", (0, cy + a, zt), (lean, 0, 0)), ("bottom", (0, cy - a, zt), (-lean, 0, 0)),
                             ("east", (a, cy, zt), (0, -lean, 0)), ("west", (-a, cy, zt), (0, lean, 0))):
        bone = f"horn_{wall}"
        g.bone(bone, pivot=pivot, parent="head", rotation=rot)
        z0, z1 = zt - ls, zt + back
        if wall in ("top", "bottom"):
            sy = 1 if wall == "top" else -1                         # outward
            y_out = cy + sy * a
            yy = sorted((y_out, y_out - sy * t))
            g.box(f"{bone}_c", (-c, yy[0] - proud, z0), (c, yy[1] + proud, z1), faces=SIDES + ("up", "down"), bone=bone)
            for side, sx in (("e", 1), ("w", -1)):
                xs = sorted((sx * a, sx * (a - w)))
                g.box(f"{bone}_{side}", (xs[0], yy[0], zt - reach), (xs[1], yy[1], z1), faces=SIDES + ("up", "down"), bone=bone,
                      rotation={"origin": (sx * a, y_out, zt), "axis": "y", "angle": -sx * fan})
            horn_inner[wall] = "down" if wall == "top" else "up"
        else:
            sx = 1 if wall == "east" else -1
            x_out = sx * a
            xx = sorted((x_out, x_out - sx * t))
            g.box(f"{bone}_c", (xx[0] - proud, cy - c, z0), (xx[1] + proud, cy + c, z1), faces=SIDES + ("up", "down"), bone=bone)
            for side, sy in (("u", 1), ("d", -1)):
                ys = sorted((cy + sy * a, cy + sy * (a - w)))
                g.box(f"{bone}_{side}", (xx[0], ys[0], zt - reach), (xx[1], ys[1], z1), faces=SIDES + ("up", "down"), bone=bone,
                      rotation={"origin": (x_out, cy + sy * a, zt), "axis": "x", "angle": sy * fan})
            horn_inner[wall] = "west" if wall == "east" else "east"
    rim_in, rim_out, rim_d = b - 0.2, b + 0.6, 0.6
    for n, lo, hi in (("rim_top", (-rim_out, cy + rim_in, zm - rim_d), (rim_out, cy + rim_out, zm + rim_d)),
                      ("rim_bottom", (-rim_out, cy - rim_out, zm - rim_d), (rim_out, cy - rim_in, zm + rim_d)),
                      ("rim_west", (-rim_out, cy - rim_in, zm - rim_d), (-rim_in, cy + rim_in, zm + rim_d)),
                      ("rim_east", (rim_in, cy - rim_in, zm - rim_d), (rim_out, cy + rim_in, zm + rim_d))):
        g.box(n, lo, hi, faces=SIDES + ("up", "down"), bone="head")
    g.box("ports", (-(a - 0.3), cy - (a - 0.3), -4.3), (a - 0.3, cy + (a - 0.3), -4.0), faces=("north",), bone="head")   # in the walls' thickness
    g.box("blower", (-2.8, 9.2, 3.4), (2.8, 13.4, 6.6), faces=("south", "east", "west", "up", "down"), bone="head")
    g.layout()
    tex, glow = Tex(g.tex_w, g.tex_h), Tex(g.tex_w, g.tex_h)
    k = K.Kit(g, {}, seed=981, turned=True)
    for names in (motor, turn, drum, cap):
        _round(k, names)
    k.paint(tex, "plate", K.DARK)
    k.overlay(tex, "plate", lambda f, img: M.rivets(f, img, every=5, inset=1), only=("up",))
    k.paint(tex, motor, GREY)
    k.paint(tex, turn, K.DARK)
    k.paint(tex, drum + cap, CD_YELLOW)
    k.overlay(tex, drum, lambda f, img: M.darken(img, np.hypot(f.X, f.Z) < 3.3, 0.72) if f.d == "up" else None)   # under the cap's lip
    k.paint(tex, "bolt", K.STEEL)
    k.overlay(tex, drum, lambda f, img: M.darken(img, (f.Y > 10.5) & (f.Y < 12.6) & (np.floor(f.plane_uv()[0]) % 2 == 0), 0.55)
              if f.d in SIDES else None)                                             # the stator's slots round the drum
    horn = [b.name for b in g.boxes if b.name.startswith("horn_")]
    k.paint(tex, horn, CD_YELLOW)

    def inside(f, img):                                            # the horn's inner faces in shadow
        wall = f.box.name.split("_")[1]
        if horn_inner.get(wall) == f.d:
            M.darken(img, np.ones(img.shape[:2], dtype=bool), 0.45)
        # A centre band's long edges, where it stands proud of the plates beside it: a seam in shadow (lit, the first
        # band's thin edges glinted as broken bright lines along the walls and inside the horn).
        seam = ("east", "west") if wall in ("top", "bottom") else ("up", "down")
        if f.box.name.endswith("_c") and f.d in seam:
            M.darken(img, np.ones(img.shape[:2], dtype=bool), 0.42)
    k.overlay(tex, horn, inside)
    k.paint(tex, ["rim_top", "rim_bottom", "rim_west", "rim_east"], CD_YELLOW)
    k.paint(tex, "ports", K.BLACK)
    g.paint_all(tex, "ports", lambda t, r, d: [t.vline(xx, r[1], r[3], P.GUNMETAL[3]) for xx in range(r[0] + 1, r[0] + r[2], 2)])
    k.paint(tex, "blower", GREY)
    k.ribs(tex, "blower", every=1.0, only=("south",))
    for p in g.save(tex, glow):
        print(p)
    idle = Animation("idle", 1.0, loop="hold_on_last_frame")
    idle.rotate("head", {0: (0, 0, 0), 1: (0, 0, 0)})
    print(save_animations(name, [idle]))
    MC.gecko_machine(name)


# --- the Warning Beacon (GeckoLib, built standing up) ------------------------------------------------------------------

def beacon() -> None:
    name = "warning_beacon"
    g = GeoModel(name, tex_size=(64, 64))
    g.open_ok = set()
    g.bone("frame")
    g.bone("rotor", pivot=(0, 0, 0), parent="frame")
    for b in ("lens", "lens_spot", "lens_full", "beam"):
        g.bone(b, pivot=(0, 0, 0), parent="rotor")
    F = OnBone(g, "frame")
    base = W.section(F, "base", 4.0, 0.0, 3.0, 16, cx=0, cz=0, caps=("up",), stagger="alternate", step=0.12)
    collar = W.section(F, "collar", 3.6, 3.0, 3.6, 16, cx=0, cz=0, caps=("up",), stagger="alternate", step=0.12)
    lenses = {}
    for b in ("lens", "lens_spot", "lens_full"):
        L = OnBone(g, b)
        lenses[b] = (W.section(L, f"{b}_body", 3.2, 3.6, 8.4, 16, cx=0, cz=0, caps=("up",), stagger="alternate", step=0.12)
                     + W.section(L, f"{b}_top", 2.2, 8.4, 9.2, 16, cx=0, cz=0, caps=("up",), stagger="alternate", step=0.12))
        g.box(f"{b}_vent", (-0.8, 9.2, -0.8), (0.8, 9.6, 0.8), faces=SIDES + ("up",), bone=b)              # its vent cap
        lenses[b].append(f"{b}_vent")
    for n, x0, x1 in (("beam_e", 3.4, 14.0), ("beam_w", -14.0, -3.4)):
        g.box(n, (x0, 5.0, -0.25), (x1, 7.2, 0.25), faces=("north", "south"), bone="beam")
    g.layout()
    k = K.Kit(g, {}, seed=991, turned=True)
    for names in [base, collar] + list(lenses.values()):
        _round(k, names)

    def textures(colour: str) -> tuple[Tex, Tex]:
        light, mid, deep, dark = BEACON_COLOURS[colour]
        tex, glow = Tex(g.tex_w, g.tex_h), Tex(g.tex_w, g.tex_h)
        k.paint(tex, base, K.GUN)
        k.overlay(tex, base, lambda f, img: M.rivets(f, img, every=3, inset=1) if f.d in SIDES else None)
        k.paint(tex, collar, K.RUBBER)

        def lens_paint(names, top, bottom, lit_side: bool | None):
            def fn(t: Tex, r, d):
                x, y, w, h = r
                if d == "up":                                       # the dome's top ring: smooth, no ribs
                    t.rect(x, y, w, h, (*top, 255))
                    return
                for yy in range(y, y + h):
                    for xx in range(x, x + w):
                        c = top if (xx // 1) % 2 == 0 else bottom                 # its Fresnel ribs, up the dome
                        t.px(xx, yy, (*c, 255))
            for n in names:
                g.paint_all(tex, n, fn)
        lens_paint(lenses["lens"], deep, dark, None)
        lens_paint(lenses["lens_spot"], mid, deep, True)
        lens_paint(lenses["lens_full"], light, mid, None)

        def glow_full(t: Tex, r, d):
            x, y, w, h = r
            t.rect(x, y, w, h, (*light, 255))

        def glow_spot(box_name: str):
            def fn(t: Tex, r, d):
                x, y, w, h = r
                b = g.get(box_name)
                cx = (b.frm[0] + b.to[0]) / 2
                face_x = {"east": b.to[0], "west": b.frm[0]}.get(d, cx)
                if face_x > 1.0 or d == "up":                     # its bright side, toward the east beam (and the top)
                    t.rect(x, y, w, h, (*light, 255) if face_x > 2.5 else (*mid, 255))
                elif face_x < -2.5:                                # and the west beam's side
                    t.rect(x, y, w, h, (*mid, 255))
            return fn
        for n in lenses["lens_full"]:
            g.paint_all(glow, n, glow_full)
        for n in lenses["lens_spot"]:
            g.paint_all(glow, n, glow_spot(n))
        for n in ("beam_e", "beam_w"):                             # the beams: nothing by daylight but their glow, fading out
            def beam(t: Tex, r, d, n=n):
                x, y, w, h = r
                t.rect(x, y, w, h, CLEAR)

            def beam_glow(t: Tex, r, d, n=n):
                x, y, w, h = r
                for xx in range(w):
                    share = xx / max(1, w - 1)                     # 0 by the lens on the east beam's face
                    out = share if (n == "beam_e") == (d == "south") else 1 - share
                    every = 2 if out < 0.3 else 3 if out < 0.65 else 5          # a shaft of light, thinning out
                    for yy in range(h):
                        if (xx + 2 * yy) % every == 0:
                            t.px(x + xx, y + yy, (*light, 255) if out < 0.3 else (*mid, 255))
            g.paint_all(tex, n, beam)
            g.paint_all(glow, n, beam_glow)
        return tex, glow

    saved = False
    for colour in BEACON_COLOURS:
        tex, glow = textures(colour)
        if not saved:                                              # its own texture (the item's) is the red one
            for p in g.save(tex, glow):
                print(p)
            saved = True
        tex.save(paths.TEXTURES / "block" / f"{name}_{colour}.png")
        glow.save(paths.TEXTURES / "block" / f"{name}_{colour}_glowmask.png")
    idle = Animation("idle", 1.0, loop="hold_on_last_frame")
    idle.rotate("rotor", {0: (0, 0, 0), 1: (0, 0, 0)})
    print(save_animations(name, [idle]))
    # Six facings by two (it stands on any face), a particle model; its item drawn by GeckoLib.
    particle = {"textures": {"particle": "groundburst:block/machine_particle"}}
    write_json(paths.MODELS / "block" / f"{name}.json", particle)
    variants = {f"active={a},facing={f}": {"model": bid(name)} for f in SIX for a in ("false", "true")}
    write_json(paths.ASSETS / "blockstates" / f"{name}.json", {"variants": variants})
    write_json(paths.MODELS / "item" / f"{name}.json", {"parent": "minecraft:block/block", "textures": particle["textures"]})
    write_json(paths.ASSETS / "items" / f"{name}.json",
               {"model": {"type": "minecraft:special", "base": f"groundburst:item/{name}", "model": {"type": "geckolib:geckolib"}}})


def main() -> None:
    for build in (receiver, panel, monitor):
        off, on = build(False), build(True)
        wall_state(off, off, on)
        item(off, bid(off))
    for build in (klaxon, bell):
        n = build()
        wall_state(n, n, upright=True)
        item(n, bid(n))
    lamps = {lamp: emergency_light(lamp) for lamp in ("off", "white", "red")}
    variants = {}
    for facing, rot in SIX.items():
        for active in ("false", "true"):
            for lamp, model in lamps.items():
                variants[f"active={active},facing={facing},lamp={lamp}"] = {"model": bid(model)} | rot
    write_json(paths.ASSETS / "blockstates" / "emergency_light.json", {"variants": variants})
    item("emergency_light", bid("emergency_light_white"))
    n = display()
    wall_state(n, n)
    item(n, bid(n))
    relay_textures()
    wall_state("alarm_relay", "alarm_relay", "alarm_relay_on")
    item("alarm_relay", bid("alarm_relay"))
    siren()
    beacon()
    print("alarm devices written")


if __name__ == "__main__":
    main()
