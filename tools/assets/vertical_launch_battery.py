"""Vertical Launch Battery, formed (GeckoLib model), DESIGN.md §17.15: the user's volley launcher, after the Mk 41 as it
stands ashore. Drawn from its controller, second from the left of the front row; the model spans x -24..40, z -8..88,
y -80..32 in the north-facing frame (model origin = the controller block's bottom centre).

Look: a concrete walkway along the front, banded in hazard paint, the fire-control console on it (olive, its green
scope); behind it the launcher, two haze-grey modules of eight cells (two across, four deep) side by side, each cell
under a square armoured hatch (bones "hatch0" to "hatch15", swung open by the block entity as its cell fires) over its
open cell: a square bore six blocks deep, down through the deck block and the five Missile Canisters under it to the
floor its missile stands on, dark, guide rails down its walls and the canisters' joints across them (after the user's
look: the missiles light in their cells and climb out through the hatches); each module's uptake grate down its middle
between its two columns, a hazard coaming round the whole; under the deck, the canisters' haze-grey sides; at
the back a row of launch control cabinets, louvred, the Energy and Item Ports' striped housings on their back, and a
status beacon on a short mast at their east end, lit red while it counts down and fires (bone "beacon").

Blocks (this script too): the Strike Cell (its hatch in a hazard coaming on top, haze-grey sides with a stencil band),
the Missile Canister (its square bore at each end, haze-grey sides with a guide-rail seam), the controller's faces (a
missile leaving its cell on the placard), and the Structure Spawner's blueprint.
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
from gb.geo import Animation, GeoModel, save_animations  # noqa: E402
from gb.tex import Tex  # noqa: E402

NAME = "vertical_launch_battery"
SIDES = ("north", "south", "east", "west")
#: The walkway's top (PartCut.DECK), the launcher's top, its hatches' thickness and half-size (pixels).
DECK, VLS_TOP, LID, HALF = 14.0, 16.0, 0.75, 6.0
#: The cells' middles (VerticalLaunchBatteryBlockEntity.CELL_X, CELL_Z) and the modules' uptakes (UPTAKE_X).
CELL_X = (-16.0, 0.0, 16.0, 32.0)
CELL_Z = (16.0, 32.0, 48.0, 64.0)
UPTAKE_X = (-8.0, 24.0)
#: The cells' depth under the deck (five Missile Canisters: VerticalLaunchBatteryBlockEntity.DEPTH) and their floor
#: (CELL_FLOOR); a cell's bore's half-width (the hatch's opening).
DEPTH = 80.0
CELL_FLOOR = 1.0 - DEPTH
BORE = HALF - 1
#: The webs between the bores (and the outer walls), across (x) and along (z) the launcher; the bores' columns.
X_WALLS = ((-24.0, -21.0), (-11.0, -5.0), (5.0, 11.0), (21.0, 27.0), (37.0, 40.0))
Z_WALLS = ((8.0, 11.0), (21.0, 27.0), (37.0, 43.0), (53.0, 59.0), (69.0, 72.0))
COLS = tuple((x - BORE, x + BORE) for x in CELL_X)
CAB_Z0, CAB_Z1, CAB_TOP = 72.0, 88.0, 20.0
HAZE_RAMP = M.ramp("#1b1f24", "#272c33", "#343a42", "#434a53", "#545c66", "#68717c", "#808a95", "#9ba5b0", "#bcc5ce")


def build() -> tuple[GeoModel, Tex, Tex, dict]:
    g = GeoModel(NAME, tex_size=(256, 512))
    g.bone("frame")
    g.bone("beacon", pivot=(37.75, 27, 84.75), parent="frame")
    for i in range(16):
        x, z = CELL_X[i % 4], CELL_Z[i // 4]
        g.bone(f"hatch{i}", pivot=(x, VLS_TOP, z + HALF), parent="frame")             # hinged on its back edge
    F = "frame"
    parts: dict[str, list] = {}

    def add(group, box):
        parts.setdefault(group, []).append(box)
        return box

    add("deck", g.box("deck", (-24, 0, -8), (40, DECK, 8), faces=("north", "east", "west", "up"), bone=F))
    add("console", g.box("console", (-5, DECK, -7), (5, 22, -2), faces=SIDES + ("up",), bone=F))
    build_cells(g, add)
    for i in range(16):
        x, z = CELL_X[i % 4], CELL_Z[i // 4]
        add("lid", g.box(f"lid{i}", (x - HALF, VLS_TOP, z - HALF), (x + HALF, VLS_TOP + LID, z + HALF), bone=f"hatch{i}"))
    add("cabinets", g.box("cabinets", (-24, 0, CAB_Z0), (40, CAB_TOP, CAB_Z1), faces=SIDES + ("up",), bone=F))
    add("eport", g.box("eport", (-6, 2, CAB_Z1), (6, 14, CAB_Z1 + 0.5), faces=("south", "east", "west", "up", "down"), bone=F))
    add("iport", g.box("iport", (10, 2, CAB_Z1), (22, 14, CAB_Z1 + 0.5), faces=("south", "east", "west", "up", "down"), bone=F))
    add("mast", g.box("mast", (37, CAB_TOP, 84), (38.5, 27, 85.5), faces=SIDES, bone=F))
    add("beacon", g.box("beacon_lamp", (36.5, 27, 83.5), (39, 29, 86), faces=SIDES + ("up", "down"), shade=False, bone="beacon"))
    add("beacon_cap", g.box("beacon_cap", (36.75, 29, 83.75), (38.75, 29.5, 85.75), faces=SIDES + ("up",), bone=F))

    g.layout()
    tex, glow = Tex(g.tex_w, g.tex_h), Tex(g.tex_w, g.tex_h)
    paint(g, tex, glow, parts)
    return g, tex, glow, parts


def build_cells(g: GeoModel, add) -> None:
    """The launcher: its sixteen cells, each a square bore from its hatch down through the deck block and the five Missile
    Canisters under it to its floor. The webs between the bores and the outer walls are boxes: the deck layer's ("vls",
    y 0-16, its own paint: the coaming, the uptake grates, the hazard band) and the canisters' under it ("deep"); a
    floor in each bore ("floor"). Faces seen alike (the bores' walls, the canisters' outsides, the floors) share one
    texture each."""
    F = "frame"
    box = {}
    nx, nz, nc = len(X_WALLS), len(Z_WALLS), len(COLS)

    def wall(group, name, frm, to, faces):
        box[name] = add(group, g.box(name, frm, to, faces=faces, bone=F))

    for i, (a, b) in enumerate(X_WALLS):
        wall("vls", f"vls_x{i}", (a, 0, Z_WALLS[0][0]), (b, VLS_TOP, Z_WALLS[-1][1]), ("north", "east", "west", "up"))
        wall("deep", f"deep_x{i}", (a, -DEPTH, Z_WALLS[0][0]), (b, 0, Z_WALLS[-1][1]), ("north", "south", "east", "west", "down"))
    for c, (c0, c1) in enumerate(COLS):
        for j, (a, b) in enumerate(Z_WALLS):
            # (the deck layer's back face is the cabinets' front)
            wall("vls", f"vls_z{c}_{j}", (c0, 0, a), (c1, VLS_TOP, b), ("north", "up") if j == nz - 1 else ("north", "south", "up"))
            wall("deep", f"deep_z{c}_{j}", (c0, -DEPTH, a), (c1, 0, b), ("north", "south", "down"))
        for r, z in enumerate(CELL_Z):
            wall("floor", f"floor{c}_{r}", (c0, -DEPTH, z - BORE), (c1, CELL_FLOOR, z + BORE), ("up", "down"))

    def share(faces):
        (src, sd), *rest = faces
        for n, d in rest:
            g.share(box[n], d, box[src], sd)

    for prefix in ("vls", "deep"):
        # The bores' walls: the webs' faces toward them, along the launcher and across it.
        share([(f"{prefix}_x{i}", "east") for i in range(nx - 1)] + [(f"{prefix}_x{i}", "west") for i in range(1, nx)])
        share([(f"{prefix}_z{c}_{j}", d) for c in range(nc) for j in range(nz) for d in ("north", "south")
               if not (j == 0 and d == "north") and not (j == nz - 1 and d == "south")])
    # The canisters' outside: its two long sides, its ends (the bores' ends, the webs' ends by their width), its foot.
    share([("deep_x0", "west"), (f"deep_x{nx - 1}", "east")])
    share([(f"deep_z{c}_0", "north") for c in range(nc)] + [(f"deep_z{c}_{nz - 1}", "south") for c in range(nc)])
    share([(f"deep_x{i}", d) for i in (0, nx - 1) for d in ("north", "south")])
    share([(f"deep_x{i}", d) for i in range(1, nx - 1) for d in ("north", "south")])
    share([(f"deep_x{i}", "down") for i in (0, nx - 1)])
    share([(f"deep_x{i}", "down") for i in range(1, nx - 1)])
    share([(f"deep_z{c}_{j}", "down") for c in range(nc) for j in (0, nz - 1)])
    share([(f"deep_z{c}_{j}", "down") for c in range(nc) for j in range(1, nz - 1)])
    share([(f"floor{c}_{r}", "up") for c in range(nc) for r in range(len(CELL_Z))])
    share([(f"floor{c}_{r}", "down") for c in range(nc) for r in range(len(CELL_Z))])


def paint(g: GeoModel, tex: Tex, glow: Tex, parts: dict) -> None:
    """The battery's surfaces, in model space (the material library): a concrete walkway in slabs, hazard bands on its
    front and ends; the launcher haze grey, its two modules' seams, the uptake grates, the cells' dark bores under their
    hatches, a hazard coaming; the hatches haze grey with lit edges, their seal rings and dark undersides; the console
    olive with its green scope; the cabinets haze grey in panels with louvres; the ports' striped housings; a galvanised
    mast with a red lamp."""
    k = K.Kit(g, parts, seed=871)
    band = M.stripes(width=2, wear=0.35)
    k.paint(tex, "deck", M.concrete(tone=0.6))

    def deck(f, img):
        M.seams(f, img, every=16, axis="u", offset=8, light=False)                 # the slabs, a block each
        edge = (f.Z < -6) | (f.X < -22) | (f.X > 38)
        M.paste(img, k.on(f, band), edge)
    k.overlay(tex, "deck", deck, only=("up",))
    k.overlay(tex, "deck", lambda f, img: M.paste(img, k.on(f, band), f.border["top"] < 3), only=("north", "east", "west"))

    haze = M.painted(HAZE_RAMP, tone=0.55, wear=0.18, rust=0.03)
    k.paint(tex, "vls", haze)
    k.overlay(tex, "vls", lambda f, img: M.paste(img, k.on(f, band), f.border["top"] < 2), only=("north", "east", "west"))
    k.seams(tex, "vls", every=16, axis="u", offset=8, only=("east", "west"))     # the cells' canisters, a block each

    def vls(f, img):
        M.darken(img, np.abs(f.X - 8) < 0.5, 0.55)                                   # the seam between the two modules
        rim = (f.X < -23) | (f.X > 39) | (f.Z < 9) | (f.Z > 71)
        M.paste(img, k.on(f, band), rim)
        for ux in UPTAKE_X:                                                          # each module's uptake grate
            grate = (np.abs(f.X - ux) < 1.5) & (f.Z > 9) & (f.Z < 71)
            M.darken(img, grate & (np.floor(f.Z) % 2 == 0), 0.35)
            M.darken(img, grate & (np.floor(f.Z) % 2 == 1), 0.75)
        for x in CELL_X:
            for z in CELL_Z:
                M.darken(img, (np.abs(f.X - x) < HALF) & (np.abs(f.Z - z) < HALF), 0.6)  # the hatch's seat round its bore
    k.overlay(tex, "vls", vls, only=("up",))

    def stencil(f, img):                                                             # a white stencil band on the launcher's front
        s = (f.Y > 3) & (f.Y < 5) & (np.floor(f.X) % 4 != 3)
        M.lighten(img, s & (f.X > -20) & (f.X < 36), 1.8, 60)
    k.overlay(tex, "vls", stencil, only=("east", "west"))

    # Under the deck: the canisters' haze-grey outside, their joints a block apart and their seams between the cells.
    k.paint(tex, "deep", M.painted(HAZE_RAMP, tone=0.5, wear=0.16, rust=0.04))
    k.overlay(tex, "deep", lambda f, img: M.seams(f, img, every=16, axis="v", offset=0, light=False), only=SIDES)
    k.seams(tex, "deep", every=16, axis="u", offset=8, only=("east", "west"))
    # Inside the cells (painted last: a web's face toward a bore is its wall): dark, a guide rail down the middle of each
    # wall, the canisters' joints across; the floor a scorched blast plate.
    walls = [("vls_x0", "east"), ("vls_z0_0", "south"), ("deep_x0", "east"), ("deep_z0_0", "south")]
    dark = M.painted(HAZE_RAMP, tone=0.16, wear=0.05, rust=0.02)

    def bore(f, img):
        along = f.Z if f.d in ("east", "west") else f.X
        rail = np.abs(((along + 8) % 16) - 8) < 0.6                                  # a cell's middle (16 px apart)
        M.lighten(img, rail, 1.5, 10)
        M.darken(img, np.abs(((f.Y + 8) % 16) - 8) > 7.4, 0.55)                     # a canister's joint
        M.darken(img, f.Y < CELL_FLOOR + 6, 0.7)                                     # soot round the foot
    for name, d in walls:
        k.paint(tex, [name], dark, only=(d,))
        k.overlay(tex, [name], bore, only=(d,))
    k.paint(tex, "floor", M.steel(tone=0.3, brushed=0.02, rust=0.12))

    def scorch(f, img):
        r = np.hypot(f.X - CELL_X[0], f.Z - CELL_Z[0])
        M.darken(img, r < 4, 0.45)
        M.darken(img, (r >= 4) & (r < 5.5), 0.7)
    k.overlay(tex, ["floor0_0"], scorch, only=("up",))

    k.paint(tex, "lid", M.painted(HAZE_RAMP, tone=0.62, wear=0.22, rust=0.02))

    def lid(f, img):
        if f.d == "up":
            b = f.border
            M.lighten(img, (b["top"] < 1) | (b["left"] < 1), 1.15, 6)
            M.darken(img, (b["bottom"] < 1) | (b["right"] < 1), 0.7)
            M.darken(img, (np.minimum(b["top"], b["bottom"]) >= 2) & (np.minimum(b["left"], b["right"]) >= 2)
                     & (np.minimum(b["top"], b["bottom"]) < 2.6), 0.85)             # the seal's ring
            M.darken(img, (np.abs(b["top"] - b["bottom"]) < 0.6) & (np.abs(b["left"] - b["right"]) < 3), 0.8)   # its lifting lug
        elif f.d == "down":
            img[..., :3] = (img[..., :3] * 0.45).astype(img.dtype)                  # its dark underside
    k.overlay(tex, "lid", lid)

    k.paint(tex, "console", M.painted(M.OLIVE_RAMP, tone=0.55, wear=0.3))

    def scope(t: Tex, r, d):                                                        # its screen and lamps
        x, y, w, h = r
        if d == "north" and w >= 8:
            t.rect(x + 2, y + 1, w - 4, 4, P.BLACK_PAINT.deep)
            for xx in range(x + 2, x + w - 2):
                t.px(xx, y + 3 + (xx % 2), P.INDICATOR_GREEN[4])
            t.px(x + 2, y + h - 2, P.INDICATOR_RED.light)
            t.px(x + 4, y + h - 2, P.INDICATOR_GREEN.light)
    g.paint_all(tex, "console", scope)
    g.paint_all(glow, "console", scope)

    k.paint(tex, "cabinets", haze)
    k.panels(tex, "cabinets", inset=2, min_size=10, only=("north", "south"))
    k.ribs(tex, "cabinets", every=2.0, only=("east", "west"))
    k.overlay(tex, "cabinets", lambda f, img: M.darken(img, (np.floor(f.Y) % 3 == 0) & (f.Y > 6) & (f.Y < 15)
                                                       & (np.floor(f.X + 24) % 16 > 3) & (np.floor(f.X + 24) % 16 < 12), 0.45),
              only=("north",))                                                       # louvres on their fronts
    for key, ramp in (("eport", M.RED_PAINT_RAMP), ("iport", M.YELLOW_PAINT_RAMP)):
        k.paint(tex, key, k.DARK)
        k.socket(tex, key, "south", ramp)
    k.paint(tex, ["mast", "beacon_cap"], K.GALV)

    def lamp(t: Tex, r, d):
        t.gradient_v(*r, P.INDICATOR_RED[6], P.INDICATOR_RED[4])
    g.paint_all(tex, "beacon_lamp", lamp)
    g.paint_all(glow, "beacon_lamp", lamp)


def animations() -> list[Animation]:
    working = Animation("working", 1.0, loop=True)
    working.rotate("beacon", {0: (0, 0, 0), 1.0: (0, 360, 0)})                  # the beacon turns while it counts down and fires
    return [working]


# --- the blocks --------------------------------------------------------------------------------------------------------

def strike_cell_top() -> Tex:
    """The Strike Cell's top: its square hatch in a hazard coaming, the seal round it, a lifting lug."""
    haze = M.painted(HAZE_RAMP, tone=0.6, wear=0.2, rust=0.02)
    img = PN.face(haze, style="inset", seed=91)
    col, row = PN.grid()
    coaming = PN.ring() < 2
    PN.stripes(img, coaming, seed=91)
    hatch = PN.area(3, 3, 10, 10)
    M.lighten(img, hatch & ((col == 3) | (row == 3)), 1.15, 6)
    M.darken(img, hatch & ((col == 12) | (row == 12)), 0.7)
    M.darken(img, PN.outline(4, 4, 8, 8), 0.85)                                         # the seal's ring
    M.darken(img, PN.area(7, 7, 2, 2), 0.75)                                            # the lifting lug
    return M.to_tex(img)


def strike_cell_side() -> Tex:
    """Its sides: haze-grey steel in the frame, a white stencil band."""
    haze = M.painted(HAZE_RAMP, tone=0.52, wear=0.2, rust=0.03)
    img = PN.face(haze, style="inset", seed=93)
    col, row = PN.grid()
    M.lighten(img, PN.area(3, 6, 10, 1) & (col % 3 != 2), 1.8, 60)
    return M.to_tex(img)


def canister_end() -> Tex:
    """The Missile Canister's ends: its square bore, dark, a guide rail in the middle of each side, in a haze-grey frame."""
    haze = M.painted(HAZE_RAMP, tone=0.55, wear=0.2, rust=0.03)
    img = PN.face(haze, style="inset", seed=95)
    col, row = PN.grid()
    bore = PN.area(3, 3, 10, 10)
    M.darken(img, bore, 0.22)
    M.darken(img, PN.outline(3, 3, 10, 10), 0.7)                                       # the bore's lip
    rails = bore & (((col >= 7) & (col <= 8) & ((row == 3) | (row == 12))) | ((row >= 7) & (row <= 8) & ((col == 3) | (col == 12))))
    M.lighten(img, rails, 2.2, 30)
    return M.to_tex(img)


def canister_side() -> Tex:
    """Its sides: haze-grey steel, a guide rail's seam down the middle, a stencilled data plate."""
    haze = M.painted(HAZE_RAMP, tone=0.5, wear=0.2, rust=0.04)
    img = PN.face(haze, style="inset", seed=97)
    M.darken(img, PN.area(8, 1, 1, 14), 0.6)                                            # the rail's seam
    M.lighten(img, PN.area(9, 1, 1, 14), 1.15, 6)
    M.lighten(img, PN.area(3, 10, 3, 2), 1.8, 60)                                        # its data plate
    return M.to_tex(img)


def vls_placard(t: Tex) -> None:
    """The battery's controller: a missile leaving its cell."""
    t.rect(4, 11, 4, 2, P.HAZARD_BLACK.base)
    t.vline(5, 8, 3, P.WHITE_CERAMIC[5])
    t.vline(6, 8, 3, P.WHITE_CERAMIC[4])
    t.px(5, 7, P.WARNING_RED.base)
    t.px(6, 7, P.WARNING_RED.base)
    t.px(4, 12, P.NUCLEAR_AMBER[5])
    t.px(7, 12, P.NUCLEAR_AMBER[5])


def blocks() -> None:
    import structures as ST  # noqa: PLC0415
    from gb import paths  # noqa: PLC0415
    from gb.model import write_json  # noqa: PLC0415
    top, side = strike_cell_top(), strike_cell_side()
    top.save(paths.TEXTURES / "block" / "strike_cell_top.png")
    side.save(paths.TEXTURES / "block" / "strike_cell_side.png")
    write_json(paths.MODELS / "block" / "strike_cell.json", {"parent": "minecraft:block/cube_column",
                                                             "textures": {"end": "groundburst:block/strike_cell_top",
                                                                          "side": "groundburst:block/strike_cell_side"}})
    write_json(paths.MODELS / "block" / "strike_cell_formed.json", ST.particle_model("groundburst:block/strike_cell_side"))
    write_json(paths.ASSETS / "blockstates" / "strike_cell.json", {"variants": {
        "formed=false": {"model": "groundburst:block/strike_cell"},
        "formed=true": {"model": "groundburst:block/strike_cell_formed"},
    }})
    write_json(paths.ASSETS / "items" / "strike_cell.json", {"model": {"type": "minecraft:model", "model": "groundburst:block/strike_cell"}})
    end, wall = canister_end(), canister_side()
    end.save(paths.TEXTURES / "block" / "missile_canister_end.png")
    wall.save(paths.TEXTURES / "block" / "missile_canister_side.png")
    write_json(paths.MODELS / "block" / "missile_canister.json", {"parent": "minecraft:block/cube_column",
                                                                  "textures": {"end": "groundburst:block/missile_canister_end",
                                                                               "side": "groundburst:block/missile_canister_side"}})
    write_json(paths.MODELS / "block" / "missile_canister_formed.json", ST.particle_model("groundburst:block/missile_canister_side"))
    write_json(paths.ASSETS / "blockstates" / "missile_canister.json", {"variants": {
        "formed=false": {"model": "groundburst:block/missile_canister"},
        "formed=true": {"model": "groundburst:block/missile_canister_formed"},
    }})
    write_json(paths.ASSETS / "items" / "missile_canister.json",
               {"model": {"type": "minecraft:model", "model": "groundburst:block/missile_canister"}})
    ST.controller(NAME, ST.controller_front(vls_placard), ST.controller_top(), ST.casing())


def spawner_plan(t: Tex) -> None:
    """The blueprint: the walkway with its console, the sixteen cells four by four, the cabinets at the back."""
    import multiblock_tools as MT  # noqa: PLC0415
    line = MT.BLUEPRINT[6]
    t.outline(4, 2, 8, 11, line)
    for cx in range(5, 12, 2):
        for cy in range(4, 11, 2):
            t.px(cx, cy, MT.BLUEPRINT[5])
    t.hline(5, 11, 6, P.HAZARD_YELLOW[4])                                                # the walkway, front (bottom)
    t.px(6, 11, P.INDICATOR_GREEN[4])                                                    # the console
    t.hline(5, 3, 6, (210, 214, 218, 255))                                               # the cabinets, back (top)


if __name__ == "__main__":
    import multiblock_tools as MT  # noqa: E402
    model, texture, glowmask, _ = build()
    for p in model.save(texture, glowmask):
        print(p)
    print(save_animations(NAME, animations()))
    blocks()
    MT.structure_spawner("vertical_launch_battery_spawner", spawner_plan)
