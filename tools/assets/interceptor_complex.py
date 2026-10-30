"""The Interceptor Complex (DESIGN.md §18.5; after the Interceptor Battery, the user's request: a multiblock version,
more reliable, two and a half times the reach and bigger volleys).

- Formed (GeckoLib), 5 blocks wide, 5 deep and 3 high, the controller at the front middle (front = north): a concrete
  walkway deck (14 px, its edges banded in hazard paint) round the front and the sides of the launcher, a cable tray down
  each side strip from the deckhouse; the vertical launch system in the middle, three haze-grey modules side by side,
  each eight cells (two across, four deep) under armoured hatches (bones "hatch0" to "hatch23", swung open by the block
  entity as each fires, the cell's dark bore under it), an exhaust uptake grate between each module's two columns, a
  coaming striped in hazard paint standing two pixels over the deck; at the back the deckhouse, NATO green, two blocks
  deep and three high: on its front over the deck the fire-control radar, a grey phased-array face of elements in rows in
  a steel rim, a lamp strip under it; louvres down its sides; on its roof a parapet, red obstruction lamps at its corners,
  an air-conditioning unit and an IFF antenna on a mast that turns while it works ("antenna"); in its back wall a door
  and the Energy and Item Ports as plates; at the controller an olive console with a green radar scope.
- Controller (block): the machine cabinet, its placard an interceptor leaving its cell.
- Launch Cell (block): its top four hatches in a hazard coaming, its sides haze-grey steel with a stencil band.
- Phased Array Panel (block): a face of radar elements in a steel rim.
"""
from __future__ import annotations

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
from gb.model import write_json  # noqa: E402
from gb.tex import Tex  # noqa: E402
import assemblers as A  # noqa: E402
import multiblock_tools as MT  # noqa: E402
import structures as ST  # noqa: E402

NAME = "interceptor_complex"
SIDES = ("north", "south", "east", "west")
#: The walkway deck's top (PartCut.DECK), the launcher's top and its hatches' (pixels; the controller's block at 0).
DECK, VLS_TOP, LID = 14.0, 16.0, 0.75
#: The cells' middles (InterceptorComplexBlockEntity.CELL_X, CELL_Z): six across the launcher, four deep; their hatches' size.
CELL_X = (-20.0, -12.0, -4.0, 4.0, 12.0, 20.0)
CELL_Z = (12.0, 20.0, 28.0, 36.0)
HATCH = 6.0
#: The deckhouse: its front (the radar's face) and back, its roof; the radar array on its front.
HOUSE_Z0, HOUSE_Z1, ROOF = 40.0, 72.0, 48.0
ARRAY = ((-22.0, 17.0), (22.0, 47.0))
HAZE_RAMP = M.ramp("#1b1f24", "#272c33", "#343a42", "#434a53", "#545c66", "#68717c", "#808a95", "#9ba5b0", "#bcc5ce")


def build() -> tuple[GeoModel, Tex, Tex, dict]:
    g = GeoModel(NAME, tex_size=(256, 256))
    g.bone("frame")
    g.bone("antenna", pivot=(0, 58, 56), parent="frame")
    for i in range(len(CELL_X) * len(CELL_Z)):
        x, z = CELL_X[i % 6], CELL_Z[i // 6]
        g.bone(f"hatch{i}", pivot=(x, VLS_TOP, z + HATCH / 2), parent="frame")     # hinged on its back edge
    F = "frame"
    parts: dict[str, list] = {}

    def add(group, box):
        parts.setdefault(group, []).append(box)
        return box

    # --- the deck and the launcher ----------------------------------------------------------------------------------
    add("deck", g.box("deck_front", (-40, 0, -8), (40, DECK, 8), faces=("north", "east", "west", "up"), bone=F))
    add("deck", g.box("deck_w", (-40, 0, 8), (-24, DECK, HOUSE_Z0), faces=("west", "up"), bone=F))
    add("deck", g.box("deck_e", (24, 0, 8), (40, DECK, HOUSE_Z0), faces=("east", "up"), bone=F))
    add("vls", g.box("vls", (-24, 0, 8), (24, VLS_TOP, HOUSE_Z0), faces=("north", "east", "west", "up"), bone=F))
    for i in range(len(CELL_X) * len(CELL_Z)):
        x, z = CELL_X[i % 6], CELL_Z[i // 6]
        h = HATCH / 2
        add("lid", g.box(f"lid{i}", (x - h, VLS_TOP, z - h), (x + h, VLS_TOP + LID, z + h), bone=f"hatch{i}"))
    for side, x0 in (("w", -36.0), ("e", 32.0)):
        add("tray", g.box(f"tray_{side}", (x0, DECK, 12), (x0 + 4, DECK + 1.5, HOUSE_Z0), faces=("north", "east", "west", "up"), bone=F))
    add("console", g.box("console", (-5, DECK, -7), (5, 22, -2), faces=SIDES + ("up",), bone=F))
    # --- the deckhouse and its radar ------------------------------------------------------------------------------------
    add("house", g.box("house", (-40, 0, HOUSE_Z0), (40, ROOF, HOUSE_Z1), faces=SIDES + ("up",), bone=F))
    (ax0, ay0), (ax1, ay1) = ARRAY
    add("array", g.box("array", (ax0, ay0, HOUSE_Z0 - 1), (ax1, ay1, HOUSE_Z0), faces=("north", "east", "west", "up", "down"), bone=F))
    add("lampstrip", g.box("lampstrip", (-12, 16.2, HOUSE_Z0 - 0.75), (12, 16.8, HOUSE_Z0), faces=("north", "east", "west", "up", "down"),
                           bone=F))
    # The roof: a parapet round it (its side walls between the front and back ones, so no two faces meet in one plane),
    # obstruction lamps on its corners, the air-conditioning unit, the antenna's mast.
    add("parapet", g.box("parapet_n", (-40, ROOF, HOUSE_Z0), (40, ROOF + 2, HOUSE_Z0 + 1), faces=SIDES + ("up",), bone=F))
    add("parapet", g.box("parapet_s", (-40, ROOF, HOUSE_Z1 - 1), (40, ROOF + 2, HOUSE_Z1), faces=SIDES + ("up",), bone=F))
    add("parapet", g.box("parapet_w", (-40, ROOF, HOUSE_Z0 + 1), (-39, ROOF + 2, HOUSE_Z1 - 1), faces=("east", "west", "up"), bone=F))
    add("parapet", g.box("parapet_e", (39, ROOF, HOUSE_Z0 + 1), (40, ROOF + 2, HOUSE_Z1 - 1), faces=("east", "west", "up"), bone=F))
    for k, (x, z) in enumerate(((-39.25, HOUSE_Z0 + 0.5), (39.25, HOUSE_Z0 + 0.5), (-39.25, HOUSE_Z1 - 0.5), (39.25, HOUSE_Z1 - 0.5))):
        add("lamp", g.box(f"lamp{k}", (x - 0.75, ROOF + 2, z - 0.75), (x + 0.75, ROOF + 3.25, z + 0.75), faces=SIDES + ("up",), bone=F))
    add("ac", g.box("ac", (-32, ROOF, 58), (-20, ROOF + 4.5, 68), faces=SIDES + ("up",), bone=F))
    add("mast", g.box("mast", (-0.75, ROOF, 55.25), (0.75, 58, 56.75), faces=SIDES, bone=F))
    add("antenna", g.box("antenna_bar", (-6, 58, 55.4), (6, 59.2, 56.6), bone="antenna"))
    add("antenna", g.box("antenna_cap", (-1.25, 59.2, 54.75), (1.25, 60.4, 57.25), faces=SIDES + ("up",), bone="antenna"))
    # The ports in the back wall, low down where their blocks are (the second and fourth columns of the back row).
    add("energy_port", g.box("energy_port", (-22, 2, HOUSE_Z1), (-10, 14, HOUSE_Z1 + 0.5), faces=("south", "east", "west", "up", "down"), bone=F))
    add("item_port", g.box("item_port", (10, 2, HOUSE_Z1), (22, 14, HOUSE_Z1 + 0.5), faces=("south", "east", "west", "up", "down"), bone=F))
    g.layout()
    tex, glow = Tex(g.tex_w, g.tex_h), Tex(g.tex_w, g.tex_h)
    paint(g, tex, glow, parts)
    return g, tex, glow, parts


def paint(g: GeoModel, tex: Tex, glow: Tex, parts: dict) -> None:
    """The complex's surfaces, in model space (the material library): a concrete deck in slabs, hazard bands round its
    edges; the launcher haze grey, its modules' seams, the uptake grates, the cells' dark bores under their hatches, a
    hazard coaming; the deckhouse NATO green in panels, louvred sides, a grey roof; the radar's face of elements in rows
    in a steel rim, a lamp strip under it; red obstruction lamps; galvanised trays, mast and antenna; the console olive
    with its green scope; the ports' plates."""
    k = K.Kit(g, parts, seed=853)
    S = k.S
    # --- deck -----------------------------------------------------------------------------------------------------------
    k.paint(tex, "deck", M.concrete(tone=0.6))
    band = M.stripes(width=2, wear=0.35)

    def deck(f, img):
        M.seams(f, img, every=16, axis="u", offset=8, light=False)                 # the slabs, a block each
        joint = np.abs(((f.Z - 0.5) + 8) % 16 - 8) < 0.25
        M.darken(img, joint, 0.72)
        edge = (f.Z < -6) | (np.abs(f.X) > 38)
        M.paste(img, k.on(f, band), edge)
        M.darken(img, (np.abs(np.abs(f.X) - 24) < 1.0) & (f.Z > 8), 0.8)           # the launcher's shadow line
    k.overlay(tex, "deck", deck, only=("up",))

    # --- the launcher: its modules, their grates, the cells' bores, the coaming ---------------------------------------
    haze = M.painted(HAZE_RAMP, tone=0.55, wear=0.18, rust=0.03)
    k.paint(tex, "vls", haze)
    k.paint(tex, "vls", band, only=("north", "east", "west"))

    def vls(f, img):
        M.darken(img, (np.abs(f.X - 8) < 0.5) | (np.abs(f.X + 8) < 0.5), 0.55)      # the modules' seams
        rim = (np.abs(f.X) > 23) | (f.Z < 9) | (f.Z > 39)
        M.paste(img, k.on(f, band), rim)
        for ux in (-16.0, 0.0, 16.0):                                                # the uptake grates
            grate = (np.abs(f.X - ux) < 1.0) & (f.Z > 9) & (f.Z < 39)
            M.darken(img, grate & (np.floor(f.Z) % 2 == 0), 0.35)
            M.darken(img, grate & (np.floor(f.Z) % 2 == 1), 0.75)
        for x in CELL_X:
            for z in CELL_Z:
                bore = (np.abs(f.X - x) < 2.5) & (np.abs(f.Z - z) < 2.5)
                img[bore, :3] = P.BLACK_PAINT.deep[:3]
                M.darken(img, (np.abs(f.X - x) < 3.0) & (np.abs(f.Z - z) < 3.0) & ~bore, 0.6)
    k.overlay(tex, "vls", vls, only=("up",))

    k.paint(tex, "lid", M.painted(HAZE_RAMP, tone=0.62, wear=0.22, rust=0.02))

    def lid(f, img):
        if f.d == "up":
            b = f.border
            M.lighten(img, (b["top"] < 1) | (b["left"] < 1), 1.15, 6)
            M.darken(img, (b["bottom"] < 1) | (b["right"] < 1), 0.7)
            M.darken(img, (np.minimum(b["top"], b["bottom"]) >= 2) & (np.minimum(b["left"], b["right"]) >= 2)
                     & (np.minimum(b["top"], b["bottom"]) < 2.6), 0.85)             # the seal's ring
        elif f.d == "down":
            img[..., :3] = (img[..., :3] * 0.45).astype(img.dtype)                  # its dark underside
    k.overlay(tex, "lid", lid)

    k.paint(tex, "tray", K.GALV)
    k.overlay(tex, "tray", lambda f, img: M.seams(f, img, every=8, axis="v", light=False), only=("up",))

    # --- the console ----------------------------------------------------------------------------------------------------
    k.paint(tex, "console", M.painted(M.OLIVE_RAMP, tone=0.55, wear=0.3))

    def scope(t: Tex, r, d):                                                        # its screen and lamps (the consoles' look)
        x, y, w, h = r
        if d == "north" and w >= 8:
            t.rect(x + 2, y + 1, w - 4, 4, P.BLACK_PAINT.deep)
            for xx in range(x + 2, x + w - 2):
                t.px(xx, y + 3 + (xx % 2), P.INDICATOR_GREEN[4])
            t.px(x + 2, y + h - 2, P.INDICATOR_RED.light)
            t.px(x + 4, y + h - 2, P.INDICATOR_GREEN.light)
    g.paint_all(tex, "console", scope)
    g.paint_all(glow, "console", scope)

    # --- the deckhouse --------------------------------------------------------------------------------------------------
    k.paint(tex, "house", K.NATO, only=SIDES)
    k.panels(tex, "house", inset=2, min_size=10, only=("north", "south"))
    k.ribs(tex, "house", every=2.0, only=("east", "west"))

    def door(f, img, x0: float, y0: float):
        """A steel door 8 wide and 12 high from ({x0}, {y0}) along the face: its frame, its leaf a shade darker, a
        handle and a vision slit."""
        u = f.X - x0 if f.d == "north" else x0 + 8 - f.X
        v = f.Y - y0
        leaf = (u >= 0) & (u < 8) & (v >= 0) & (v < 12)
        M.paste(img, k.on(f, K.GUN), leaf)
        M.darken(img, leaf & ((u < 1) | (u >= 7) | (v >= 11)), 0.55)                 # its frame
        M.lighten(img, leaf & (np.floor(u) == 5) & (np.floor(v) == 5), 1.5, 14)     # the handle
        M.darken(img, leaf & (u >= 2.5) & (u < 5.5) & (np.floor(v) == 9), 0.35)    # the vision slit

    k.overlay(tex, "house", lambda f, img: door(f, img, -4, 0), only=("south",))
    k.overlay(tex, "house", lambda f, img: door(f, img, -36, DECK), only=("north",))

    def sign(f, img):                                                               # a warning sign on the front's east pillar
        s = (f.X > 28) & (f.X < 36) & (f.Y > 26) & (f.Y < 32)
        M.paste(img, k.on(f, K.YELLOW), s)
        M.darken(img, s & ((f.X < 29) | (f.X > 35) | (f.Y < 27) | (f.Y > 31)), 0.3)
        M.darken(img, s & (np.abs(f.Y - 29) < 0.5) & (np.floor(f.X) % 2 == 0), 0.3)   # its line of lettering
    k.overlay(tex, "house", sign, only=("north",))
    k.paint(tex, "house", M.painted(M.GREY_PAINT_RAMP, tone=0.45, wear=0.15, grime=0.04), only=("up",))
    k.overlay(tex, "house", lambda f, img: M.seams(f, img, every=8, axis="u", light=False), only=("up",))
    k.paint(tex, "parapet", K.NATO)
    k.paint(tex, "ac", K.GUN)
    k.ribs(tex, "ac", every=1.0, only=("north", "south", "east", "west"))
    k.paint(tex, ["mast", "antenna"], K.GALV)

    # The radar: its face of elements in rows in a steel rim.
    k.paint(tex, "array", M.painted(M.GREY_PAINT_RAMP, tone=0.6, wear=0.08, grime=0.02))
    rim = M.steel(tone=0.5, brushed=0.04)

    def array(f, img):
        if f.d != "north":
            M.paste(img, k.on(f, rim), np.ones(img.shape[:2], dtype=bool))
            return
        r = K.ring(f)
        M.paste(img, k.on(f, rim), r < 1.5)
        inside = r >= 1.5
        u, v = f.X - ax0, f.Y - ay0
        M.darken(img, inside & ((np.floor(u) % 3 == 2) | (np.floor(v) % 3 == 2)), 0.78)        # between the elements
        M.lighten(img, inside & (np.floor(u) % 3 == 0) & (np.floor(v) % 3 == 1), 1.08, 3)       # each element's lit edge
        M.darken(img, inside & (np.abs(f.X) < 0.5), 0.88)                                       # the two halves' seam
    (ax0, ay0), _ = ARRAY
    k.overlay(tex, "array", array)

    def lampstrip(t: Tex, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, P.BLACK_PAINT[1])
        if d == "north":
            for xx in range(x + 1, x + w - 1, 3):
                t.px(xx, y + h // 2, P.INDICATOR_GREEN.light if (xx // 3) % 4 else P.NUCLEAR_AMBER.light)
    g.paint_all(tex, "lampstrip", lampstrip)
    g.paint_all(glow, "lampstrip", lambda t, r, d: lampstrip(t, r, d) if d == "north" else None)

    def lamp(t: Tex, r, d):
        x, y, w, h = r
        t.rect(x, y, w, h, P.INDICATOR_RED[3])
        t.px(x + w // 2, y + h // 2, P.INDICATOR_RED.light)
    for b in parts["lamp"]:
        g.paint_all(tex, b, lamp)
        g.paint_all(glow, b, lamp)

    # The ports: dark steel plates, a painted border (red for power, hazard for items) and a glyph.
    plate = M.steel(tone=0.4, brushed=0.04, rust=0.08)
    for name, border in (("energy_port", M.painted(M.RED_PAINT_RAMP, tone=0.58, wear=0.3)), ("item_port", M.stripes(width=2, wear=0.3))):
        k.paint(tex, name, plate)

        def port_rim(f, img, border=border):
            ring = K.ring(f)
            M.paste(img, k.on(f, border), ring < 2)
            M.darken(img, ring == 2, 0.55)
        k.overlay(tex, name, port_rim, only=("south",))

    def bolt(t: Tex, r, d):
        if d == "south":
            cx, cy = r[0] + r[2] // 2, r[1] + r[3] // 2
            for dx, dy in ((1, -3), (0, -2), (-1, -1), (0, -1), (1, -1), (0, 0), (-1, 1), (-2, 2)):
                t.px(cx + dx, cy + dy, P.HAZARD_YELLOW[4])

    def grate(t: Tex, r, d):
        if d == "south":
            cx, cy = r[0] + r[2] // 2, r[1] + r[3] // 2
            for yy in range(cy - 3, cy + 4, 2):
                t.hline(cx - 3, yy, 7, P.STEEL[5])
                t.hline(cx - 3, yy + 1, 7, P.BLACK_PAINT.deep)
    g.paint_all(tex, "energy_port", bolt)
    g.paint_all(tex, "item_port", grate)


def animations() -> list[Animation]:
    """Working (2 s, looped while it is powered): its IFF antenna turns. Idle: it rests."""
    work = Animation("working", 2.0, loop=True)
    work.rotate("antenna", {0: (0, 0, 0), 1: (0, 180, 0), 2: (0, 360, 0)})
    idle = Animation("idle", 1.0, loop="hold_on_last_frame")
    idle.rotate("antenna", {0: (0, 0, 0), 1: (0, 0, 0)})
    return [work, idle]


# --- the blocks ------------------------------------------------------------------------------------------------------------

def placard(t: Tex) -> None:
    """The controller's placard: an interceptor leaving its cell, its flame under it."""
    t.rect(4, 9, 6, 4, P.BLACK_PAINT[2])
    t.hline(4, 12, 6, P.GUNMETAL[4])                                # the launcher's deck
    t.vline(7, 9, 2, P.WHITE_CERAMIC[5])                            # the interceptor
    t.px(7, 8, P.WHITE_CERAMIC[6])
    t.px(7, 11, P.NUCLEAR_AMBER.light)                              # its flame
    t.px(6, 12, P.INDICATOR_RED[4])
    t.px(8, 12, P.INDICATOR_RED[4])


def launch_cell() -> None:
    """The Launch Cell: its top four hatches (two by two) in a hazard coaming, the cells' uptake grate between them; its
    sides haze-grey steel in two panels with a stencil band; its bottom plain."""
    haze = M.painted(HAZE_RAMP, tone=0.55, wear=0.0, mottle=0.015, grain=0.015)
    top = PN.face(haze, style="inset", seed=41)
    col, row = PN.grid()
    PN.paste(top, (col <= 0) | (col >= 15) | (row <= 0) | (row >= 15), M.stripes(width=2, wear=0.2), 41)
    grate = (col == 7) | (col == 8)                                             # the uptake grate between the two columns
    M.darken(top, grate & (row >= 1) & (row <= 14), 0.55)
    M.darken(top, grate & (row % 2 == 0) & (row >= 1) & (row <= 14), 0.55)
    for x0, y0 in ((2, 2), (9, 2), (2, 9), (9, 9)):                              # four hatches, five square
        gap = PN.outline(x0 - 1, y0 - 1, 7, 7) & ~grate
        M.darken(top, gap, 0.45)                                                # the seam round it
        hatch = PN.area(x0, y0, 5, 5)
        M.lighten(top, hatch, 1.12, 4)
        M.lighten(top, hatch & ((col == x0) | (row == y0)), 1.12, 6)             # its lit edges
        M.darken(top, hatch & ((col == x0 + 4) | (row == y0 + 4)), 0.8)
        M.lighten(top, (col == x0 + 2) & (row == y0 + 2), 1.4, 10)              # its centre boss
    t = M.to_tex(top)
    t.save(paths.TEXTURES / "block" / "launch_cell_top.png")
    side = PN.face(haze, style="inset", seed=42)
    PN.groove(side, 2, 2, 12, 12)
    PN.paste(side, PN.area(2, 4, 12, 2), M.painted(M.YELLOW_PAINT_RAMP, tone=0.6, wear=0.15), 42)
    s = M.to_tex(side)
    for x in range(3, 13, 2):
        s.px(x, 5, P.BLACK_PAINT[1])                                            # the stencil band's lettering
    s.save(paths.TEXTURES / "block" / "launch_cell_side.png")
    bottom = M.to_tex(PN.face(haze, style="inset", seed=43))
    bottom.save(paths.TEXTURES / "block" / "launch_cell_bottom.png")
    model = {"parent": "minecraft:block/cube_bottom_top", "textures": {"top": "groundburst:block/launch_cell_top",
             "side": "groundburst:block/launch_cell_side", "bottom": "groundburst:block/launch_cell_bottom"}}
    write_json(paths.MODELS / "block" / "launch_cell.json", model)
    write_json(paths.MODELS / "block" / "launch_cell_formed.json", ST.particle_model("groundburst:block/launch_cell_side"))
    write_json(paths.ASSETS / "blockstates" / "launch_cell.json", {"variants": {
        "formed=false": {"model": "groundburst:block/launch_cell"},
        "formed=true": {"model": "groundburst:block/launch_cell_formed"},
    }})
    write_json(paths.ASSETS / "items" / "launch_cell.json", {"model": {"type": "minecraft:model", "model": "groundburst:block/launch_cell"}})


def phased_array_panel() -> Tex:
    """The Phased Array Panel: a face of radar elements in rows in a steel rim, its bolts at the corners."""
    img = PN.face(M.painted(M.GREY_PAINT_RAMP, tone=0.6, wear=0.0, mottle=0.015, grain=0.015), frame=PN.GUNMETAL, style="inset", seed=44)
    col, row = PN.grid()
    inside = (col >= 2) & (col <= 13) & (row >= 2) & (row <= 13)
    M.darken(img, inside & (((col - 2) % 3 == 2) | ((row - 2) % 3 == 2)), 0.78)
    M.lighten(img, inside & ((col - 2) % 3 == 0) & ((row - 2) % 3 == 0), 1.1, 4)
    return M.to_tex(img)


def spawner_marks(ox: int, oy: int, w: int, h: int):
    """The spawner sheet's plan: the launcher's cells in the middle, the deckhouse across the back."""
    B = MT.BLUEPRINT
    marks = []
    for x in range(ox + 3, ox + w - 3):
        for y in (oy + 3, oy + 5):
            marks.append((x, y, B[3] if (x - ox) % 2 else P.STEEL[5]))
    for x in range(ox + 1, ox + w - 1):
        marks.append((x, oy + h - 3, P.NATO_GREEN[4]))
        marks.append((x, oy + h - 2, P.NATO_GREEN[3]))
    return marks


def main() -> None:
    ST.controller(NAME, ST.controller_front(placard), ST.controller_top(), ST.casing())
    launch_cell()
    ST.part_block("phased_array_panel", phased_array_panel())
    model, texture, glowmask, _ = build()
    print("z-fights:", model.zfight())
    for p in model.save(texture, glowmask):
        print(p)
    print(save_animations(NAME, animations()))
    MT.structure_spawner(f"{NAME}_spawner", A.plan(5, 5, spawner_marks))
    print("interceptor complex written")


if __name__ == "__main__":
    main()
