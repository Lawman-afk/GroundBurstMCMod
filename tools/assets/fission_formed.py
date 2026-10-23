"""The formed Fission Reactor's sections (DESIGN.md §7.2): the vessel drawn over a reactor of any size, one section a
cell, by FissionReactorRenderer (the walls' own blocks hide while it is formed).

Every section is a block model for one cell, written for the cell's north side (corners and posts for the north-west):
the renderer turns it to the side it faces. The look follows the old formed model: a lead-lined vessel of bolted lead
plates, steel-angled corner posts, deep window bays with leaded panes, a dark steel plinth banded in hazard paint, a
gunmetal head with a raised rim and a control-rod drive over every column that holds fuel (its rod rising as the
control rods come out), the ports' striped housings, and the control console where the controller stands. Painted with
the material library (DESIGN.md §4.3: every model).

    tools\\.venv\\Scripts\\python.exe tools\\assets\\fission_formed.py        writes the sections and a review sheet

Sections (models/block/fission_<name>.json; the renderer's list is FissionSections.java):
    wall, post, corner_bottom, edge_bottom, bottom, corner_top, edge_top, top, window, window_glass, skylight,
    skylight_glass, drive, drive_rod, port_item, port_fluid, port_energy, port_item_top, port_fluid_top,
    port_energy_top, console
"""
from __future__ import annotations

import sys
from dataclasses import replace
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import numpy as np  # noqa: E402

from gz import machine_kit as K  # noqa: E402
from gz import materials as M  # noqa: E402
from gz import palette as P  # noqa: E402
from gz.model import BlockModel  # noqa: E402
from gz.palette import fixed  # noqa: E402
from gz.tex import Tex  # noqa: E402

#: The vessel's materials. Each section is painted as its own model, so the ones that run on from cell to cell keep their
#: weathering even (no grime, streaks or skylight falloff gathering at every cell's foot): each cell reads as a bolted
#: plate of one continuous wall.
CALM = dict(grime=0.03, streaks=0.0, falloff=0.015, dust=0.02)
LEAD = M.Material(ramp=K.ramp_of(P.LEAD), tone=0.52, mottle=0.04, grain=0.03, dither=0.5, edge_light=0.16, edge_dark=0.12, ao=0.45,
                  **CALM)
LINING = M.Material(ramp=K.ramp_of(P.LEAD), tone=0.38, mottle=0.03, grain=0.03, dither=0.5, edge_light=0.08, edge_dark=0.08, ao=0.5,
                    **CALM)
GUN = replace(K.GUN, **CALM)
PLINTH = replace(K.PLINTH, **CALM)
STEEL = replace(K.STEEL, **CALM)
GLASS = fixed("reactor_pane", "#141a1e", "#1e262c", "#2a343c", "#36424c", "#4a5864", "#6a7a88", "#a4b4c0")
FLUID_BLUE = fixed("fluid_blue", "#0c1a3a", "#12285a", "#1a3a7e", "#2452a6", "#3a6ec8", "#6a98e0", "#a8c8f4")
CLEAR = (0, 0, 0, 0)
SECTIONS: dict[str, BlockModel] = {}


def _model(name: str, size: int = 64) -> BlockModel:
    m = BlockModel(f"fission_{name}", tex_size=(size, size), kind="block")
    m.particle = "groundzero:block/lead_lined_casing"
    return m


def _save(name: str, m: BlockModel, t: Tex) -> None:
    m.save(t)
    SECTIONS[name] = m


def ring(f) -> np.ndarray:
    return K.ring(f)


def plates(f, img, panel: bool = True) -> None:
    """Bolted lead plates a block square: a seam between them (on one side of each cell, so each joint gets one), the
    next plate's lit lip, a bolt in each corner and a shallow inspection panel in the middle."""
    if not f.vertical and f.d != "up":
        return
    a, b = f.plane_uv()
    ua, vb = np.mod(a, 16.0), np.mod(b, 16.0)
    M.darken(img, (ua < 1) | (vb < 1), 0.6)
    M.lighten(img, ((ua >= 1) & (ua < 2) & (vb >= 1)) | ((vb >= 15) & (ua >= 1)), 1.08, 3)
    if panel:
        edge = np.minimum(np.minimum(ua - 1, 16 - ua), np.minimum(vb - 1, 16 - vb))
        M.darken(img, (edge >= 4) & (edge < 5), 0.78)
        M.lighten(img, (edge >= 5) & (edge < 6) & ((np.abs(vb - 10.5) < 0.6) | (np.abs(ua - 5.5) < 0.6)), 1.07, 2)
    bolt = ((np.abs(ua - 2.5) < 0.6) | (np.abs(ua - 14.5) < 0.6)) & ((np.abs(vb - 2.5) < 0.6) | (np.abs(vb - 14.5) < 0.6))
    M.lighten(img, bolt, 1.32, 14)
    M.darken(img, np.roll(bolt, 1, axis=0) & ~bolt, 0.7)


def hazard_band(k: K.Kit, f, img, y0: float, y1: float) -> None:
    M.paste(img, k.on(f, K.HAZARD), (f.Y >= y0) & (f.Y < y1))


# --- the walls ---------------------------------------------------------------------------------------------------------------

def wall() -> None:
    m = _model("wall")
    m.box("plate", (0, 0, 1), (16, 16, 16))
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    k = K.Kit(m, {}, seed=401)
    k.paint(t, "plate", LEAD)
    k.paint(t, "plate", LINING, only=("south",))
    k.overlay(t, "plate", plates, only=("north",))
    _save("wall", m, t)


def post() -> None:
    m = _model("post")
    m.box("post", (0, 0, 0), (16, 16, 16))
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    k = K.Kit(m, {}, seed=403)
    k.paint(t, "post", LEAD)
    k.paint(t, "post", LINING, only=("south", "east"))

    def angle(f, img):                                                        # the steel angle on the corner, banded
        corner = (f.X < 3) if f.d == "north" else (f.Z < 3)
        M.paste(img, k.on(f, STEEL), corner)
        M.darken(img, corner & ((f.X < 3) & (f.Z < 3) | False) & (f.Y % 4 < 0.5), 0.8)
        band = (f.Y > 13.5) & (f.Y < 15)
        M.paste(img, k.on(f, STEEL), band)
        M.lighten(img, band & (f.Y > 14.5), 1.15, 6)
        bolt = corner & (np.abs(np.mod(f.Y, 8.0) - 4) < 0.6) & ((np.abs(f.X - 1.5) < 0.6) if f.d == "north" else (np.abs(f.Z - 1.5) < 0.6))
        M.lighten(img, bolt, 1.35, 16)
    k.overlay(t, "post", plates, only=("north", "west"))
    k.overlay(t, "post", angle, only=("north", "west"))
    k.overlay(t, "post", lambda f, img: M.darken(img, (f.X < 3) & (f.Z < 3) & (ring(f) < 1), 0.8), only=("up", "down"))
    _save("post", m, t)


def window() -> None:
    """A window: a steel frame at the wall's face, standing a pixel proud of the plates, the pane (a translucent section
    of its own) just behind it. There is no bay: the sides of the walls and posts round it frame the opening, so windows
    side by side make one glazed panel and the core shows through from any angle."""
    m = _model("window", 64)
    bars = (("bar_t", (0, 14, 0), (16, 16, 2)), ("bar_b", (0, 0, 0), (16, 2, 2)),
            ("bar_l", (0, 2, 0), (2, 14, 2)), ("bar_r", (14, 2, 0), (16, 14, 2)))
    for name, frm, to in bars:
        m.box(name, frm, to)
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    k = K.Kit(m, {}, seed=405)
    names = [n for n, _, _ in bars]
    k.paint(t, names, STEEL)
    k.paint(t, names, LINING, only=("south",))

    def gasket(f, img):                                                       # a dark seal round the pane, on the frame's inner sides
        inner = {"bar_t": "down", "bar_b": "up", "bar_l": "east", "bar_r": "west"}[f.box.name]
        if f.d == inner:
            M.darken(img, f.Z > 1.2, 0.55)
    k.overlay(t, names, gasket)
    _save("window", m, t)

    g = _model("window_glass", 32)
    g.box("pane", (2, 2, 1.25), (14, 14, 1.75), faces=("north", "south"))
    g.layout()
    tg = Tex(g.tex_w, g.tex_h)
    g.paint_all(tg, "pane", leaded)
    g.render_type = "minecraft:translucent"
    _save("window_glass", g, tg)


def leaded(t: Tex, r, d) -> None:
    """A leaded pane: clear, faintly tinted glass in four lights (the core's glow shows through it), a lead came across
    it each way, a glint on each light."""
    x, y, w, h = r
    for yy in range(h):
        for xx in range(w):
            t.px(x + xx, y + yy, (*GLASS[5][:3], 60))
    for i in range(w):
        t.px(x + i, y + h // 2, P.LEAD[1])
    for j in range(h):
        t.px(x + w // 2, y + j, P.LEAD[1])
    for ox, oy in ((0, 0), (w // 2 + 1, 0), (0, h // 2 + 1), (w // 2 + 1, h // 2 + 1)):
        t.px(x + ox + 1, y + oy + 1, (*GLASS[6][:3], 170))
        t.px(x + ox + 2, y + oy + 1, (*GLASS[5][:3], 140))
        t.px(x + ox + 1, y + oy + 2, (*GLASS[5][:3], 140))


# --- the plinth --------------------------------------------------------------------------------------------------------------

def _plinth(name: str, outer: tuple[str, ...], seed: int) -> None:
    m = _model(name)
    m.box("plinth", (0, 0, 0), (16, 16, 16))
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    k = K.Kit(m, {}, seed=seed)
    k.paint(t, "plinth", PLINTH)

    def band(f, img):
        hazard_band(k, f, img, 11, 14)
        M.darken(img, (f.Y >= 10.5) & (f.Y < 11), 0.6)
        M.lighten(img, f.Y >= 15, 1.15, 6)                                       # the plinth's lit top edge
        M.seams(f, img, every=16, axis="u", offset=0)
    k.overlay(t, "plinth", band, only=outer)

    def tread(f, img):
        a, b = f.plane_uv()
        bar = ((np.floor(a) % 4) == 1) & ((np.floor(b + 2 * (np.floor(a / 4) % 2)) % 4) == 1)
        M.lighten(img, bar, 1.25, 8)
        M.darken(img, np.roll(bar, 1, axis=0) & ~bar, 0.72)
    k.overlay(t, "plinth", tread, only=("up",))
    _save(name, m, t)


# --- the head ----------------------------------------------------------------------------------------------------------------

def _head(name: str, lips: list[tuple[str, tuple, tuple]], outer: tuple[str, ...], seed: int, opening: bool = False) -> None:
    m = _model(name)
    if opening:                                                               # a skylight: a frame at the roof's face, as a window
        for bn, frm, to in (("head_l", (0, 14, 0), (2, 16, 16)), ("head_r", (14, 14, 0), (16, 16, 16)),
                            ("head_f", (2, 14, 0), (14, 16, 2)), ("head_k", (2, 14, 14), (14, 16, 16))):
            m.box(bn, frm, to)
        for bn, frm, to in (("rim_f", (1, 16, 1), (15, 17, 2)), ("rim_k", (1, 16, 14), (15, 17, 15)),
                            ("rim_l", (1, 16, 2), (2, 17, 14)), ("rim_r", (14, 16, 2), (15, 17, 14))):
            m.box(bn, frm, to)
    else:
        m.box("head", (0, 0, 0), (16, 16, 16))
    for bn, frm, to in lips:
        m.box(bn, frm, to)
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    k = K.Kit(m, {}, seed=seed)
    heads = [b.name for b in m.boxes if b.name.startswith("head")]
    k.paint(t, heads, GUN)
    k.paint(t, heads, LINING, only=("down",))
    if opening:
        k.paint(t, ["rim_f", "rim_k", "rim_l", "rim_r"], STEEL)
    if lips:
        k.paint(t, [n for n, _, _ in lips], STEEL)
        k.overlay(t, [n for n, _, _ in lips], lambda f, img: M.lighten(img, (np.abs(np.mod(f.X + f.Z, 6.0) - 3) < 0.6), 1.3, 12),
                  only=("up",))

    def ring_band(f, img):                                                    # the head's outer ring: lead plates, a steel band
        M.paste(img, k.on(f, LEAD), f.Y < 12)
        plates(f, img, panel=False)
        M.darken(img, (f.Y >= 11.5) & (f.Y < 12), 0.6)
    k.overlay(t, heads, ring_band, only=outer)

    def plate(f, img):
        a, b = f.plane_uv()
        M.darken(img, (np.mod(a, 16.0) < 0.5) | (np.mod(b, 16.0) < 0.5), 0.7)
        bolt = (np.abs(np.mod(a, 16.0) - 2.5) < 0.6) & (np.abs(np.mod(b, 16.0) - 2.5) < 0.6)
        M.lighten(img, bolt, 1.3, 12)
    k.overlay(t, heads, plate, only=("up",))
    _save(name, m, t)


def drive() -> None:
    """A control-rod drive housing on the head over a column that holds fuel: a gunmetal can, a steel cap, a green lamp."""
    m = _model("drive", 32)
    m.box("can", (5, 16, 5), (11, 22, 11))
    m.box("cap", (4.5, 22, 4.5), (11.5, 23, 11.5))
    m.box("lamp", (5, 23, 5), (6, 23.5, 6), light=15)
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    k = K.Kit(m, {}, seed=411)
    k.paint(t, "can", GUN)
    k.overlay(t, "can", lambda f, img: M.paste(img, k.on(f, K.YELLOW), (f.Y >= 17) & (f.Y < 18)) if f.vertical else None)
    k.paint(t, "cap", STEEL)
    m.paint_all(t, "lamp", lambda t2, r, d: t2.rect(*r, P.INDICATOR_GREEN.light))
    _save("drive", m, t)

    rod = _model("drive_rod", 32)
    rod.box("rod", (7.25, 15, 7.25), (8.75, 31, 8.75), faces=("north", "south", "east", "west", "up"))
    rod.layout()
    tr = Tex(rod.tex_w, rod.tex_h)
    kr = K.Kit(rod, {}, seed=413)
    kr.paint(tr, "rod", K.BRIGHT)
    kr.overlay(tr, "rod", lambda f, img: M.darken(img, (np.floor(f.Y) % 4) == 0, 0.75) if f.vertical else None)
    _save("drive_rod", rod, tr)


# --- ports and the console ---------------------------------------------------------------------------------------------------

PORTS = {"item": M.YELLOW_PAINT_RAMP, "fluid": K.ramp_of(FLUID_BLUE), "energy": M.RED_PAINT_RAMP}


def _port_glyph(kind: str, t: Tex, x: int, y: int) -> None:
    """The port's sign in its bore (as on the port blocks): chevrons, a drop, a lightning bolt."""
    if kind == "item":
        for px, py in ((1, 1), (2, 2), (1, 3)):
            t.px(x + px, y + py, P.NUCLEAR_AMBER[5])
        for px, py in ((4, 1), (3, 2), (4, 3)):
            t.px(x + px, y + py, P.NUCLEAR_AMBER[4])
    elif kind == "fluid":
        for px, py in ((2, 0), (2, 1), (1, 2), (2, 2), (3, 2), (1, 3), (2, 3), (3, 3), (2, 4)):
            t.px(x + px, y + py, FLUID_BLUE[5])
    else:
        for px, py in ((3, 0), (2, 1), (1, 2), (2, 2), (3, 2), (2, 3), (1, 4)):
            t.px(x + px, y + py, P.HAZARD_YELLOW[5])


def port(kind: str, top: bool) -> None:
    name = f"port_{kind}" + ("_top" if top else "")
    m = _model(name, 32)
    if top:
        m.box("housing", (2, 16, 2), (14, 19, 14))
        face = "up"
    else:
        m.box("housing", (2, 2, -3), (14, 14, 1))
        face = "north"
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    k = K.Kit(m, {}, seed=421 + len(kind))
    k.paint(t, "housing", K.DARK)
    k.socket(t, "housing", face, PORTS[kind], inset=3)
    m.paint(t, "housing", face, lambda t2, r: _port_glyph(kind, t2, r[0] + r[2] // 2 - 2, r[1] + r[3] // 2 - 2))
    _save(name, m, t)


def console() -> None:
    """The controller: a control console standing out from the wall, its front a CRT, status lamps, a rod-position
    dial and the radiation placard, a hazard strip along its foot."""
    m = _model("console", 64)
    m.box("cabinet", (1, 0, -4), (15, 14, 1))
    m.box("desk", (0.5, 14, -5), (15.5, 15, 1))
    m.box("screen", (3, 6, -4.2), (13, 12, -4), faces=("north",), light=15)
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    k = K.Kit(m, {}, seed=431)
    k.paint(t, "cabinet", K.OLIVE)
    k.overlay(t, "cabinet", lambda f, img: M.paste(img, k.on(f, K.HAZARD), f.Y < 2), only=("north", "east", "west"))
    k.panels(t, "cabinet", inset=1, min_size=6, only=("east", "west"))
    k.paint(t, "desk", GUN)

    def front(t2, r, d):
        x, y, w, h = r
        if d != "north":
            return
        t2.outline(x + 1, y + 1, w - 2, 8, P.GUNMETAL[1])                        # the screen's bezel
        K.dial(t2, x + 2, y + 9, 3)                                             # rod-position dial
        K.lamps(t2, x + 6, y + 10)
        K.trefoil(t2, x + w - 6, y + 8)
    m.paint_all(t, "cabinet", front)

    def screen(t2, r, d):
        """The CRT: dark green glass, a faint graticule, the core's power trace and the rods' bars."""
        x, y, w, h = r
        t2.rect(x, y, w, h, (8, 20, 12, 255))
        for xx in range(x + 1, x + w, 3):
            for yy in range(y + 1, y + h, 2):
                t2.px(xx, yy, (16, 40, 24, 255))
        for i, dy in enumerate((0, 0, 0, -1, -3, 1, 0, 0, 0, 0)[:w]):        # a steady trace with one beat
            t2.px(x + i, y + h - 2 + dy, P.INDICATOR_GREEN[4])
        for i, n in enumerate((2, 3, 1, 2)):                                   # control-rod bars along the top
            t2.hline(x + 1 + i * 2, y + 1, 1, P.INDICATOR_GREEN[5 if n > 1 else 3])
            if n > 1:
                t2.px(x + 1 + i * 2, y + 2, P.INDICATOR_GREEN[3])
        t2.px(x, y, (90, 140, 110, 255))                                       # a glint on the glass
    m.paint_all(t, "screen", screen)
    _save("console", m, t)


# --- a formed reactor put together (the review sheet, and the Field Manual's plate) -------------------------------------------

#: Section turns (FissionSections.java turns them the same way): a quarter turn clockwise seen from above (a blockstate's
#: y: 90) takes (x, z) to (16 - z, x), north to east. Walls and edges are written facing north, corners and posts north-west.
TURN = {"north": 0, "east": 1, "south": 2, "west": 3}
CORNER_TURN = {frozenset(("north", "west")): 0, frozenset(("north", "east")): 1, frozenset(("south", "east")): 2,
               frozenset(("south", "west")): 3}
PORT_KEYS = {"U": "item", "W": "fluid", "E": "energy"}


def pieces(layers: list[list[str]], channel_cols: set[tuple[int, int]] | None = None) -> list[tuple[str, int, tuple[int, int, int], bool]]:
    """The sections FissionReactorRenderer draws for a reactor laid out like a pattern (layers bottom-up, rows front first,
    the front to the north; X the controller, L casing, G glass, U W E the Item, Fluid and Energy Ports, C a channel):
    (section, quarter turns, cell, upside down). Mirrors FissionSections.java."""
    h, d, w = len(layers), len(layers[0]), len(layers[0][0])
    if channel_cols is None:
        channel_cols = {(x, z) for y in range(h) for z in range(d) for x in range(w) if layers[y][z][x] == "C"}
    out = []
    for y in range(h):
        for z in range(d):
            for x in range(w):
                key = layers[y][z][x]
                horiz = [s for s, on in (("north", z == 0), ("south", z == d - 1), ("west", x == 0), ("east", x == w - 1)) if on]
                vert = "down" if y == 0 else "up" if y == h - 1 else None
                cell = (x, y, z)
                if len(horiz) == 2:
                    base = "corner_bottom" if vert == "down" else "corner_top" if vert == "up" else "post"
                    out.append((base, CORNER_TURN[frozenset(horiz)], cell, False))
                elif len(horiz) == 1:
                    turn = TURN[horiz[0]]
                    if vert is not None:
                        out.append(("edge_bottom" if vert == "down" else "edge_top", turn, cell, False))
                    elif key == "G":
                        out += [("window", turn, cell, False), ("window_glass", turn, cell, False)]
                    else:
                        out.append(("wall", turn, cell, False))
                        if key in PORT_KEYS:
                            out.append((f"port_{PORT_KEYS[key]}", turn, cell, False))
                    if key == "X":
                        out.append(("console", turn, cell, False))
                elif vert == "up":
                    if key == "G":
                        out += [("skylight", 0, cell, False), ("skylight_glass", 0, cell, False)]
                    else:
                        out.append(("top", 0, cell, False))
                        if key in PORT_KEYS:
                            out.append((f"port_{PORT_KEYS[key]}_top", 0, cell, False))
                        elif (x, z) in channel_cols:
                            out += [("drive", 0, cell, False), ("drive_rod", 0, cell, False)]
                elif vert == "down":
                    out.append(("bottom", 0, cell, False))
                    if key in PORT_KEYS:                                       # a port in the floor: its housing hangs under it
                        out.append((f"port_{PORT_KEYS[key]}_top", 0, cell, True))
    return out


def place(pos: np.ndarray, normal: np.ndarray, turns: int, flip: bool) -> tuple[np.ndarray, np.ndarray]:
    """A section's points and normal in its cell: upside down (half a turn about the x axis) first, then turned."""
    p, n = pos.copy(), np.array(normal, dtype=float)
    if flip:
        p[:, 1], p[:, 2] = 16 - p[:, 1], 16 - p[:, 2]
        n = np.array([n[0], -n[1], -n[2]])
    for _ in range(turns % 4):
        x, z = p[:, 0].copy(), p[:, 2].copy()
        p[:, 0], p[:, 2] = 16 - z, x
        n = np.array([-n[2], n[1], n[0]])
    return p, n


def compose(layers: list[list[str]], rod_out: float = 0.7):
    """Quads of a formed reactor (the preview renderer's): the opaque sections, and the panes (drawn last, far first)."""
    sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "preview"))
    import render as R
    cache: dict[str, list] = {}
    opaque, panes = [], []
    for name, turns, (x, y, z), flip in pieces(layers):
        if name not in cache:
            cache[name] = R.build_quads(R.load_model(f"groundzero:block/fission_{name}"))
        lift = (rod_out - 1) * 8 if name == "drive_rod" else 0
        for q in cache[name]:
            pos, normal = place(q.pos, q.normal, turns, flip)
            moved = R.Quad(pos + np.array([x * 16, y * 16 + lift, z * 16]), q.uv, q.tex, q.shade, q.emissive, normal, q.directional)
            (panes if name.endswith("_glass") else opaque).append(moved)
    return opaque, panes


#: The example reactor (GZMultiblocks' pattern): 4 x 4 x 4 outside, a 2 x 2 x 2 core of channels.
EXAMPLE = [
    ["LXLL", "LLLL", "LLLL", "LLLL"],
    ["LGGL", "GCCG", "WCCG", "LEGL"],
    ["LGGL", "GCCG", "GCCG", "LGGL"],
    ["LLLL", "LLUL", "LLLL", "LLLL"],
]
#: A bigger one to review the other sections: 6 x 5 x 5, the controller in the front wall, skylights, a port on the roof
#: and one in the floor.
EXAMPLE_BIG = [
    ["LLLLLL", "LLLLLL", "LLLELL", "LLLLLL", "LLLLLL"],
    ["LGXGGL", "WCCCCG", "GC..CG", "GCCCCG", "LGGGGL"],
    ["LGGGGL", "G....G", "G.CC.G", "G....G", "LGGGGL"],
    ["LGGGGL", "G.CC.G", "GCCCCG", "G.CC.G", "LGGGGL"],
    ["LLLLLL", "LGLLGL", "LLULLL", "LGLLGL", "LLLLLL"],
]
VIEWS = (("front (north-east)", 30, 225), ("back (south-west)", 30, 45), ("front-left", 25, 135), ("from below", -35, 160))


def review(layouts=(EXAMPLE, EXAMPLE_BIG), out_name: str = "fission_formed_review.png", cell: int = 420,
           views=VIEWS, rod_out: float = 0.7) -> Path:
    """A sheet of formed reactors (a row each) from several sides."""
    sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "preview"))
    import render as R
    from PIL import Image, ImageDraw
    from gz import paths
    light = np.array([0.4, 0.8, 0.45])
    light /= np.linalg.norm(light)
    sheet = Image.new("RGBA", (len(views) * cell, len(layouts) * (cell + 18)), (28, 30, 34, 255))
    dr = ImageDraw.Draw(sheet)
    for row, layers in enumerate(layouts):
        opaque, panes = compose(layers, rod_out)
        centre, extent = R.model_bounds(opaque)
        for i, (label, pitch, yaw) in enumerate(views):
            rot = R.view_matrix(pitch, yaw)
            ordered = sorted(panes, key=lambda q: float((rot @ (q.pos.mean(axis=0) - centre))[2]))
            img = R._checker(cell).convert("RGBA")
            img.alpha_composite(R.render(opaque + ordered, cell, pitch, yaw, centre, extent, light_dir=light))
            y = row * (cell + 18)
            sheet.alpha_composite(img, (i * cell, y))
            dr.text((i * cell + 6, y + cell + 3), label, fill=(210, 210, 210, 255))
    path = paths.PREVIEWS / out_name
    path.parent.mkdir(parents=True, exist_ok=True)
    sheet.convert("RGB").save(path)
    return path


def build_all() -> None:
    wall()
    post()
    window()
    _plinth("corner_bottom", ("north", "west"), 407)
    _plinth("edge_bottom", ("north",), 408)
    _plinth("bottom", (), 409)
    _head("corner_top", [("lip_n", (0, 16, 0), (16, 18, 2)), ("lip_w", (0, 16, 2), (2, 18, 16))], ("north", "west"), 415)
    _head("edge_top", [("lip_n", (0, 16, 0), (16, 18, 2))], ("north",), 416)
    _head("top", [], (), 417)
    _head("skylight", [], (), 418, opening=True)
    g = _model("skylight_glass", 32)
    g.box("pane", (2, 14.25, 2), (14, 14.75, 14), faces=("up", "down"))
    g.layout()
    tg = Tex(g.tex_w, g.tex_h)
    g.paint_all(tg, "pane", leaded)
    g.render_type = "minecraft:translucent"
    _save("skylight_glass", g, tg)
    drive()
    for kind in PORTS:
        port(kind, False)
        port(kind, True)
    console()


if __name__ == "__main__":
    build_all()
    print(f"{len(SECTIONS)} sections written")
    print(review())
