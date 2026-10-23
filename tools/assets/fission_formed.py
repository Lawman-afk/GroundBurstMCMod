"""The formed Fission Reactor's sections (DESIGN.md §7.2): the vessel drawn over a reactor of any size, one section a
cell, by FissionReactorRenderer (the walls' own blocks hide while it is formed).

Every section is a block model for one cell, written for the cell's north side (corners and posts for the north-west):
the renderer turns it to the side it faces. Together they make one continuous vessel, as the old fixed formed model
was: a dark steel base banded in hazard paint all the way round, lead pillars with steel angles up the corners, wide
lead walls with a rivet grid that runs on from cell to cell, a lead head band under a steel rim, a steel roof with its
own rivet grid, a control-rod drive over every column that holds fuel (its rod rising as the control rods come out),
the ports' striped housings and the control console where the controller stands. Glass is clear and runs on too:
windows and skylights side by side make one glazed panel, a thin steel lip round its outside only (the renderer picks
each cell's lip pieces from its neighbours).

To join with no seam, a section's outer faces are painted as part of the whole (the material library's `period` and
`context`): the noise wraps every block, and the neighbouring cells' sections stand round it while it is painted, so a
face that runs on into the next cell has no lip, shadow or grime there; only the vessel's real edges (its corners, the
step from a pillar to a wall, the base's ledge, the head's overhang) are lit and shaded. The faces seen only through
the glass (a wall's cut side, the core's floor and ceiling) are painted as they are, on their own.

    tools\\.venv\\Scripts\\python.exe tools\\assets\\fission_formed.py        writes the sections, checks the joins,
                                                                           writes a review sheet

Sections (models/block/fission_<name>.json; the renderer's list is FissionSections.java):
    wall, post, corner_bottom, edge_bottom, bottom, corner_top, edge_top, top, window_glass, skylight_glass,
    wlip_up, wlip_down, wlip_lo, wlip_hi, wlip_lo_up, wlip_hi_up, wlip_lo_down, wlip_hi_down,
    slip_n, slip_s, slip_w, slip_e, slip_nw, slip_ne, slip_sw, slip_se,
    drive, drive_rod, port_item, port_fluid, port_energy, port_item_top, port_fluid_top, port_energy_top, console
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

#: The vessel's materials: even weathering (no grime, streaks or skylight falloff gathering at each cell's foot), so the
#: cells read as one surface; each tone sits right on a shade of its ramp (with the face's own lift: a roof's +0.1), so
#: the surface is that shade with specks of the next, not a dithered blotch that repeats every block.
CALM = dict(grime=0.03, streaks=0.0, falloff=0.0, dust=0.0)
LEAD_RAMP = np.stack([np.interp(np.linspace(0, 6, 9), np.arange(7), K.ramp_of(P.LEAD)[:, c]) for c in range(3)],
                     axis=1).astype(np.float32)           # the palette's lead in nine shades: fine steps for a big surface
LEAD = M.Material(ramp=LEAD_RAMP, tone=0.5, mottle=0.035, grain=0.035, dither=0.45, edge_light=0.16, edge_dark=0.12,
                  ao=0.45, brushed=0.02, **CALM)
LINING = M.Material(ramp=K.ramp_of(P.LEAD), tone=0.38, mottle=0.03, grain=0.03, dither=0.5, edge_light=0.08, edge_dark=0.08,
                    ao=0.5, **CALM)
ROOF = M.Material(ramp=M.STEEL_RAMP, tone=0.567, mottle=0.035, grain=0.035, dither=0.45, edge_light=0.14, edge_dark=0.1,
                  ao=0.45, brushed=0.02, **CALM)
GUN = replace(K.GUN, **CALM)
PLINTH = replace(K.PLINTH, **CALM)
STEEL = replace(K.STEEL, **CALM)
LIP = replace(STEEL, ao=0.0)                       # the glazing's lips: thin, no contact shadow from the next piece
ANGLE = replace(STEEL, tone=5 / 9, brushed=0.02, mottle=0.03, grain=0.03)   # the angles outlining the vessel: calm bars
HAZARD = replace(K.HAZARD, **CALM)
GLASS = fixed("reactor_pane", "#141a1e", "#1e262c", "#2a343c", "#36424c", "#4a5864", "#6a7a88", "#a4b4c0")
FLUID_BLUE = fixed("fluid_blue", "#0c1a3a", "#12285a", "#1a3a7e", "#2452a6", "#3a6ec8", "#6a98e0", "#a8c8f4")
SECTIONS: dict[str, BlockModel] = {}
#: Every section's noise wraps every block (16 px), so a cell joins the next with no seam.
PERIOD = 16


def _model(name: str, size: int = 64) -> BlockModel:
    m = BlockModel(f"fission_{name}", tex_size=(size, size), kind="block")
    m.particle = "groundzero:block/lead_lined_casing"
    return m


def _save(name: str, m: BlockModel, t: Tex) -> None:
    m.save(t)
    SECTIONS[name] = m


def kits(m: BlockModel, seed: int, context) -> tuple[K.Kit, K.Kit]:
    """Two painters for a section: one with its neighbours round it (its outer faces, which run on into theirs), one
    without (the faces seen only through the glass, which really end where the cell does)."""
    return K.Kit(m, {}, seed=seed, period=PERIOD, context=context), K.Kit(m, {}, seed=seed + 1, period=PERIOD)


def cell(dx: int = 0, dy: int = 0, dz: int = 0, frm=(0, 0, 0), to=(16, 16, 16)) -> tuple[tuple, tuple]:
    """A box in the cell {dx, dy, dz} cells away: a neighbour standing round a section while it is painted."""
    return (tuple(frm[i] + 16 * d for i, d in enumerate((dx, dy, dz))), tuple(to[i] + 16 * d for i, d in enumerate((dx, dy, dz))))


#: Neighbours: a wall plate (recessed a pixel), a full block, the ground under the vessel.
WALL_BOX = ((0, 0, 1), (16, 16, 16))
GROUND = ((-32, -32, -32), (48, 0, 48))


def studs(f, img, every: float = 8.0, at: float = 4.5) -> None:
    """Rivet heads in a grid every {every} pixels, on the vessel's grid: a lit head with its shadow under it."""
    a, b = f.plane_uv()
    rivet(img, (np.abs(np.mod(a, every) - at) < 0.5) & (np.abs(np.mod(b, every) - at) < 0.5))


def rivet(img, on) -> None:
    M.lighten(img, on, 1.42, 16)
    M.darken(img, np.roll(on, 1, axis=0) & ~on, 0.8)


def courses(f, img) -> None:
    """The vessel's cladding: courses a block tall, a thin seam at each block's foot and a row of rivets either side of
    it every 4 px. The courses run on round the whole vessel at the same heights (walls, pillars, the head band; on the
    roof, deck plates the same way), so the cells read as one skin, not a grid of tiles."""
    a, b = f.plane_uv()
    vb = np.mod(b, 16.0)
    seam = vb < 1
    M.darken(img, seam, 0.72)
    rivet(img, ((np.abs(vb - 1.5) < 0.5) | (np.abs(vb - 14.5) < 0.5)) & (np.abs(np.mod(a, 4.0) - 2.5) < 0.5))


def band(k: K.Kit, f, img) -> None:
    """The base's hazard band (y 11 to 14), a shadowed groove under it."""
    M.paste(img, k.on(f, HAZARD), (f.Y >= 11) & (f.Y < 14))
    M.darken(img, (f.Y >= 10.5) & (f.Y < 11), 0.6)


def tread(f, img) -> None:
    """Tread plate (the base's top: the ledge, the window sills, the core's floor)."""
    a, b = f.plane_uv()
    bar = ((np.floor(a) % 4) == 1) & ((np.floor(b + 2 * (np.floor(a / 4) % 2)) % 4) == 1)
    M.lighten(img, bar, 1.22, 7)
    M.darken(img, np.roll(bar, 1, axis=0) & ~bar, 0.74)


#: Where the head's steel angle starts: 3 px under the roof's edge, as wide as a pillar's angle.
TOP_ANGLE = 13.0


def angle(k: K.Kit, f, img, below: float = 16.0) -> None:
    """A pillar's steel angle: 3 px up each side of the corner, a rivet every 4 px up it. At a top corner it runs up the
    head band to {below}, where it meets the head's angle along the roof's edge."""
    across = f.X if f.d == "north" else f.Z
    strip = (across < 3) & (f.Y < below)
    M.paste(img, k.on(f, ANGLE), strip)
    M.darken(img, (across >= 3) & (across < 3.6) & (f.Y < below), 0.7)
    rivet(img, strip & (np.abs(across - 1.5) < 0.5) & (np.abs(np.mod(f.Y, 4.0) - 2.5) < 0.5))


def head_angle(k: K.Kit, f, img, corner: bool) -> None:
    """The head's steel angle along the roof's edge: the top 3 px of the head band under the rim, a rivet every 4 px
    along it on the pillars' pitch. With the pillars' angles it outlines the vessel's top in steel; at a top corner the
    pillar's angle comes up into it, one rivet in the corner where they meet."""
    a, _ = f.plane_uv()                                                   # along the edge, 0 at a corner's outer edge
    strip = f.Y >= TOP_ANGLE
    M.paste(img, k.on(f, ANGLE), strip)
    edge = (f.Y >= TOP_ANGLE - 0.6) & (f.Y < TOP_ANGLE)
    row = strip & (np.abs(f.Y - 14.5) < 0.5)
    rivets = row & (np.abs(np.mod(a, 4.0) - 2.5) < 0.5)
    if corner:
        edge &= a >= 3
        rivets = (rivets & (a >= 3)) | (row & (np.abs(a - 1.5) < 0.5))
    M.darken(img, edge, 0.7)
    rivet(img, rivets)


# --- the base ----------------------------------------------------------------------------------------------------------------

def corner_bottom() -> None:
    m = _model("corner_bottom")
    m.box("base", (0, 0, 0), (16, 16, 16))
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    kc, kp = kits(m, 407, [cell(1), cell(dz=1), cell(1, 0, 1), cell(dy=1), GROUND])
    kp.paint(t, "base", PLINTH)
    kc.paint(t, "base", PLINTH, only=("north", "west"))
    kc.overlay(t, "base", lambda f, img: band(kc, f, img), only=("north", "west"))
    _save("corner_bottom", m, t)


def edge_bottom() -> None:
    m = _model("edge_bottom")
    m.box("base", (0, 0, 0), (16, 16, 16))
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    kc, kp = kits(m, 408, [cell(-1), cell(1), cell(dz=1), cell(-1, 0, 1), cell(1, 0, 1), GROUND])
    kp.paint(t, "base", PLINTH)
    kc.paint(t, "base", PLINTH, only=("north", "up"))
    kc.overlay(t, "base", lambda f, img: band(kc, f, img), only=("north",))
    kc.overlay(t, "base", tread, only=("up",))
    _save("edge_bottom", m, t)


def bottom() -> None:
    m = _model("bottom")
    m.box("base", (0, 0, 0), (16, 16, 16))
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    kc, kp = kits(m, 409, [cell(dx, 0, dz) for dx in (-1, 0, 1) for dz in (-1, 0, 1) if dx or dz] + [GROUND])
    kp.paint(t, "base", PLINTH)
    kc.paint(t, "base", PLINTH, only=("up",))
    kc.overlay(t, "base", tread, only=("up",))
    _save("bottom", m, t)


# --- pillars and walls -------------------------------------------------------------------------------------------------------

def post() -> None:
    m = _model("post")
    m.box("pillar", (0, 0, 0), (16, 16, 16))
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    kc, kp = kits(m, 403, [cell(dy=1), cell(dy=-1), cell(1, frm=WALL_BOX[0], to=WALL_BOX[1]),
                           cell(dz=1, frm=(1, 0, 0), to=(16, 16, 16))])
    kp.paint(t, "pillar", LINING)
    kc.paint(t, "pillar", LEAD, only=("north", "west"))
    kc.overlay(t, "pillar", courses, only=("north", "west"))
    kc.overlay(t, "pillar", lambda f, img: angle(kc, f, img), only=("north", "west"))
    _save("post", m, t)


def wall() -> None:
    m = _model("wall")
    m.box("plate", WALL_BOX[0], WALL_BOX[1])
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    kc, kp = kits(m, 401, [cell(dx, dy, frm=WALL_BOX[0], to=WALL_BOX[1]) for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1))])
    kp.paint(t, "plate", LINING)
    kc.paint(t, "plate", LEAD, only=("north",))
    kc.overlay(t, "plate", courses, only=("north",))
    _save("wall", m, t)


# --- the head and roof -------------------------------------------------------------------------------------------------------

def _head(name: str, lips: list[tuple[str, tuple, tuple]], outer: tuple[str, ...], context, seed: int) -> None:
    """A cell of the head: a full block, its roof decked in steel, its outer faces lead (the head band) under a steel
    angle along the roof's edge (at a corner the pillar's angle runs up into it), the steel rim on top. {context} stands
    the neighbouring cells round it, the rim running on into theirs."""
    m = _model(name)
    m.box("head", (0, 0, 0), (16, 16, 16))
    for bn, frm, to in lips:
        m.box(bn, frm, to)
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    kc, kp = kits(m, seed, context)
    kp.paint(t, "head", LINING)
    kc.paint(t, "head", ROOF, only=("up",))
    kc.overlay(t, "head", courses, only=("up",))
    if outer:
        corner = len(outer) == 2
        kc.paint(t, "head", LEAD, only=outer)
        kc.overlay(t, "head", courses, only=outer)
        kc.overlay(t, "head", lambda f, img: head_angle(kc, f, img, corner), only=outer)
        if corner:
            kc.overlay(t, "head", lambda f, img: angle(kc, f, img, below=TOP_ANGLE), only=outer)
    if lips:
        names = [n for n, _, _ in lips]
        kc.paint(t, names, STEEL)
        kc.overlay(t, names, lambda f, img: studs(f, img, every=4.0, at=2.5) if f.d == "up" else None)
    _save(name, m, t)


# --- glass: clear panes, and the lips round the outside of a glazed panel ------------------------------------------------------

def _glass(m: BlockModel, t: Tex, box: str, seed: int) -> None:
    """Clear, faintly tinted glass: the core's glow shows through it. A sheen of thin diagonal streaks runs on from
    pane to pane (the glazing reads as one sheet)."""
    k = K.Kit(m, {}, seed=seed, period=PERIOD)
    for f in k.S.faces(box):
        x, y, w, h = f.rect
        a, b = f.plane_uv()
        img = np.zeros((h, w, 4), dtype=np.uint8)
        img[..., :3] = GLASS[5][:3]
        img[..., 3] = 44
        diag = np.mod(a + b, 16.0)
        img[(diag >= 3) & (diag < 4)] = (*GLASS[6][:3], 80)
        img[(diag >= 5.5) & (diag < 6)] = (*GLASS[6][:3], 60)
        t.a[y:y + h, x:x + w] = img


def window_glass() -> None:
    g = _model("window_glass", 64)
    g.box("pane", (0, 0, 1.5), (16, 16, 1.75), faces=("north", "south"))
    g.layout()
    t = Tex(g.tex_w, g.tex_h)
    _glass(g, t, "pane", 451)
    g.render_type = "minecraft:translucent"
    _save("window_glass", g, t)


def skylight_glass() -> None:
    g = _model("skylight_glass", 64)
    g.box("pane", (0, 15, 0), (16, 15.25, 16), faces=("up", "down"))
    g.layout()
    t = Tex(g.tex_w, g.tex_h)
    _glass(g, t, "pane", 452)
    g.render_type = "minecraft:translucent"
    _save("skylight_glass", g, t)


#: A window's lip pieces, in the cell's own frame (the wall's face to the north, x across it, y up): a side's run where
#: the cell beside it is not glass, a corner where any of the three cells round that corner is not. "lo" is the low-x
#: side, "hi" the high-x side.
WINDOW_LIPS = {
    "up": ((1, 15, 0), (15, 16, 1.4)), "down": ((1, 0, 0), (15, 1, 1.4)),
    "lo": ((0, 1, 0), (1, 15, 1.4)), "hi": ((15, 1, 0), (16, 15, 1.4)),
    "lo_up": ((0, 15, 0), (1, 16, 1.4)), "hi_up": ((15, 15, 0), (16, 16, 1.4)),
    "lo_down": ((0, 0, 0), (1, 1, 1.4)), "hi_down": ((15, 0, 0), (16, 1, 1.4)),
}
#: A skylight's lip pieces in the roof (x east, z south), standing a pixel proud of it.
SKYLIGHT_LIPS = {
    "n": ((1, 15.5, 0), (15, 17, 1)), "s": ((1, 15.5, 15), (15, 17, 16)),
    "w": ((0, 15.5, 1), (1, 17, 15)), "e": ((15, 15.5, 1), (16, 17, 15)),
    "nw": ((0, 15.5, 0), (1, 17, 1)), "ne": ((15, 15.5, 0), (16, 17, 1)),
    "sw": ((0, 15.5, 15), (1, 17, 16)), "se": ((15, 15.5, 15), (16, 17, 16)),
}


def _runs(frm, to, axes) -> list[tuple[tuple, tuple]]:
    """A lip piece's run carried on a block's length either way along {axes} (the next cells' lips, for painting)."""
    out = []
    for axis in axes:
        for lo_a, hi_a in ((to[axis], to[axis] + 16), (frm[axis] - 16, frm[axis])):
            lo, hi = list(frm), list(to)
            lo[axis], hi[axis] = lo_a, hi_a
            out.append((tuple(lo), tuple(hi)))
    return out


def _lip(name: str, frm, to, axes, seed: int) -> None:
    """One lip piece: steel, a pixel proud of the wall or roof, running on into the next cell's lip."""
    m = _model(name, 32)
    m.box("lip", frm, to)
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    kc, _ = kits(m, seed, _runs(frm, to, axes))
    kc.paint(t, "lip", LIP)
    _save(name, m, t)


def lips() -> None:
    """The window lips run along x or up (corners both ways); the skylight lips along x or z."""
    for i, (key, (frm, to)) in enumerate(WINDOW_LIPS.items()):
        axes = (0,) if key in ("up", "down") else (1,) if key in ("lo", "hi") else (0, 1)
        _lip(f"wlip_{key}", frm, to, axes, 461 + i)
    for i, (key, (frm, to)) in enumerate(SKYLIGHT_LIPS.items()):
        axes = (0,) if key in ("n", "s") else (2,) if key in ("w", "e") else (0, 2)
        _lip(f"slip_{key}", frm, to, axes, 471 + i)


# --- the control-rod drives --------------------------------------------------------------------------------------------------

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
    m.box("cabinet", (1, 0, -4), (15, 14, 1), faces=("north", "south", "east", "west", "up"))
    m.open_ok = {("cabinet", "down")}                                     # it stands on the base or the ground
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


# --- a formed reactor put together (the review sheet, the Field Manual's plate, the join check) -------------------------------

#: Section turns (FissionSections.java turns them the same way): a quarter turn clockwise seen from above (a blockstate's
#: y: 90) takes (x, z) to (16 - z, x), north to east. Walls and edges are written facing north, corners and posts north-west.
TURN = {"north": 0, "east": 1, "south": 2, "west": 3}
CORNER_TURN = {frozenset(("north", "west")): 0, frozenset(("north", "east")): 1, frozenset(("south", "east")): 2,
               frozenset(("south", "west")): 3}
PORT_KEYS = {"U": "item", "W": "fluid", "E": "energy"}
#: For a wall facing each way, the (dx, dz) of the cells on its low-x and high-x sides (in the section's own frame): the
#: directions anticlockwise and clockwise of the way it faces, seen from above.
SIDES = {"north": ((-1, 0), (1, 0)), "south": ((1, 0), (-1, 0)), "west": ((0, 1), (0, -1)), "east": ((0, -1), (0, 1))}


def pieces(layers: list[list[str]], channel_cols: set[tuple[int, int]] | None = None) -> list[tuple[str, int, tuple[int, int, int], bool]]:
    """The sections FissionReactorRenderer draws for a reactor laid out like a pattern (layers bottom-up, rows front first,
    the front to the north; X the controller, L casing, G glass, U W E the Item, Fluid and Energy Ports, C a channel):
    (section, quarter turns, cell, upside down). Mirrors FissionSections.java."""
    h, d, w = len(layers), len(layers[0]), len(layers[0][0])
    if channel_cols is None:
        channel_cols = {(x, z) for y in range(h) for z in range(d) for x in range(w) if layers[y][z][x] == "C"}

    def key(x, y, z):
        return layers[y][z][x] if 0 <= y < h and 0 <= z < d and 0 <= x < w else " "

    out = []
    for y in range(h):
        for z in range(d):
            for x in range(w):
                k = layers[y][z][x]
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
                    elif k == "G":
                        out.append(("window_glass", turn, cell, False))
                        (lx, lz), (hx, hz) = SIDES[horiz[0]]

                        def glass(dx, dy, dz):
                            return key(x + dx, y + dy, z + dz) == "G"
                        up, down, lo, hi = glass(0, 1, 0), glass(0, -1, 0), glass(lx, 0, lz), glass(hx, 0, hz)
                        need = {"up": not up, "down": not down, "lo": not lo, "hi": not hi,
                                "lo_up": not (up and lo and glass(lx, 1, lz)), "hi_up": not (up and hi and glass(hx, 1, hz)),
                                "lo_down": not (down and lo and glass(lx, -1, lz)), "hi_down": not (down and hi and glass(hx, -1, hz))}
                        out += [(f"wlip_{n}", turn, cell, False) for n, on in need.items() if on]
                    else:
                        out.append(("wall", turn, cell, False))
                        if k in PORT_KEYS:
                            out.append((f"port_{PORT_KEYS[k]}", turn, cell, False))
                    if k == "X":
                        out.append(("console", turn, cell, False))
                elif vert == "up":
                    if k == "G":
                        out.append(("skylight_glass", 0, cell, False))

                        def glass(dx, dz):
                            return key(x + dx, y, z + dz) == "G"
                        n, s, wv, e = glass(0, -1), glass(0, 1), glass(-1, 0), glass(1, 0)
                        need = {"n": not n, "s": not s, "w": not wv, "e": not e, "nw": not (n and wv and glass(-1, -1)),
                                "ne": not (n and e and glass(1, -1)), "sw": not (s and wv and glass(-1, 1)),
                                "se": not (s and e and glass(1, 1))}
                        out += [(f"slip_{n_}", 0, cell, False) for n_, on in need.items() if on]
                    else:
                        out.append(("top", 0, cell, False))
                        if k in PORT_KEYS:
                            out.append((f"port_{PORT_KEYS[k]}_top", 0, cell, False))
                        elif (x, z) in channel_cols:
                            out += [("drive", 0, cell, False), ("drive_rod", 0, cell, False)]
                elif vert == "down":
                    out.append(("bottom", 0, cell, False))
                    if k in PORT_KEYS:                                       # a port in the floor: its housing hangs under it
                        out.append((f"port_{PORT_KEYS[k]}_top", 0, cell, True))
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




NORMALS = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}


def check_joins(layers) -> list[str]:
    """Every section of a formed reactor in one model, checked for faces that would flicker (two pointing the same way on
    one plane, as model.py checks a model): the joins between sections must have none."""
    import json
    from gz import paths
    joined = BlockModel("fission_joins")
    n = 0
    for name, turns, (x, y, z), flip in pieces(layers):
        if name.endswith("_glass"):
            continue
        data = json.loads((paths.MODELS / "block" / f"fission_{name}.json").read_text(encoding="utf-8"))
        for el in data["elements"]:
            corners = np.array([el["from"], el["to"]], dtype=float)
            moved, _ = place(corners, np.zeros(3), turns, flip)
            lo = moved.min(axis=0) + np.array([x, y, z]) * 16
            hi = moved.max(axis=0) + np.array([x, y, z]) * 16
            faces = []
            for d in el["faces"]:                                          # each face turned with its section
                _, normal = place(corners, np.array(NORMALS[d], dtype=float), turns, flip)
                faces.append(next(k for k, v in NORMALS.items() if np.allclose(v, normal)))
            joined.box(f"{name}_{n}", tuple(lo), tuple(hi), faces=tuple(faces))
            n += 1
    return joined.zfight()


def build_all() -> None:
    corner_bottom()
    edge_bottom()
    bottom()
    post()
    wall()
    full = ((0, 0, 0), (16, 16, 16))
    lip_n, lip_w = ((0, 16, 0), (16, 18, 2)), ((0, 16, 2), (2, 18, 16))
    _head("corner_top", [("lip_n", *lip_n), ("lip_w", *lip_w)], ("north", "west"),
          [cell(1), cell(dz=1), cell(1, 0, 1), cell(dy=-1)] + _runs(*lip_n, (0,))[:1] + [_runs(*lip_w, (2,))[0]], 415)
    _head("edge_top", [("lip_n", *lip_n)], ("north",),
          [cell(-1), cell(1), cell(dz=1), cell(-1, 0, 1), cell(1, 0, 1), cell(dy=-1, frm=WALL_BOX[0], to=WALL_BOX[1])]
          + _runs(*lip_n, (0,)), 416)
    _head("top", [], (), [cell(dx, 0, dz, *full) for dx in (-1, 0, 1) for dz in (-1, 0, 1) if dx or dz], 417)
    window_glass()
    skylight_glass()
    lips()
    drive()
    for kind in PORTS:
        port(kind, False)
        port(kind, True)
    console()


if __name__ == "__main__":
    build_all()
    print(f"{len(SECTIONS)} sections written")
    for label, layers in (("example", EXAMPLE), ("big", EXAMPLE_BIG)):
        issues = check_joins(layers)
        print(f"joins ({label}): {len(issues)} flicker{'s' if len(issues) != 1 else ''}")
        for issue in issues[:12]:
            print("  ", issue)
    print(review())
