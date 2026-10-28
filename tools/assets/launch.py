"""Launching (DESIGN.md §17, a basic launch site ahead of Phase 9): the component blocks, the two controllers' cabinets,
the Basic Missile, and the two formed structures (GeckoLib).

- Component blocks (cubes, see-through where they are frames): Launch Pad (grey concrete with a hazard-striped
  border), Gantry Frame (a red lattice), Gantry Arm (a red truss), Radar Mast (a grey lattice); the Radar Dish and the
  Antenna are modelled (a dish on a stub, a whip with crossbars).
- The plain missile (item, 2 blocks tall as drawn): a white body with two black roll bands, a bare steel nose, four grey
  fins with black tips, a dark nozzle; only a missile with no warhead looks like this (every warhead has its own missile:
  tools/assets/missiles.py).
- Launch Silo, formed: a concrete pad with a hazard border and a flame trench under the missile (a grate in it),
  four hold-down posts, a control box at the front with a green CRT, the Energy Port's red-hatched housing at the
  back; beside it a red lattice gantry tower 9 blocks tall with a platform on top and an arm reaching in to the
  missile at its sixth level (the arm swings away during the countdown); the missile on the pad while one is loaded
  (drawn by the game from its own model at the "missile" bone, its tail on the pad).
- Launch Computer, formed: an olive desk with a sloped console, two CRT monitors (glowing green), the Energy Port's
  red-hatched socket on its side, a Yagi antenna on top; behind it a grey lattice mast with a radar dish on a yoke,
  tilted up, that turns while the computer is powered.
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))

import math  # noqa: E402

import numpy as np  # noqa: E402

from gb import materials as M  # noqa: E402
from gb import palette as P  # noqa: E402
from gb import paths  # noqa: E402
from gb.geo import Animation, GeoModel, save_animations  # noqa: E402
from gb.model import BlockModel, write_json  # noqa: E402
from gb.tex import Tex  # noqa: E402
import grenades as G  # noqa: E402
import structures as ST  # noqa: E402
import warheads as W  # noqa: E402
import missile_paint as MP  # noqa: E402

STEEL, GUN, OLIVE = P.STEEL, P.GUNMETAL, P.OLIVE
WHITE = P.WHITE_CERAMIC
BLACK = P.HAZARD_BLACK
YELLOW = P.HAZARD_YELLOW
RED = P.WARNING_RED
GREEN = P.INDICATOR_GREEN
CLEAR = (0, 0, 0, 0)
shaded = G.shaded


def tone(d: str) -> int:
    return {"up": 4, "down": 2, "north": 3, "west": 3}.get(d, 2)


# --- the missile (shared by the item and the formed silo) -----------------------------------------------------------------
def missile_parts() -> list[tuple[str, tuple, tuple, tuple, str]]:
    """(name, from, to, faces, paint) in item pixels: 9 x 32 x 9, centred on x = z = 8, tail at y 0. Drawn 4x over
    (8 blocks tall) on the silo and in flight."""
    all6 = ("north", "south", "east", "west", "up", "down")
    parts = [("nozzle", (7.0, 0, 7.0), (9.0, 1.5, 9.0), ("north", "south", "east", "west", "down"), "nozzle")]

    def prism(name, r, w, y0, y1, paint, ends=("up", "down")):
        parts.append((f"{name}_c", (8 - r, y0, 8 - w), (8 + r, y1, 8 + w), ("north", "south", "east", "west") + ends, paint))
        parts.append((f"{name}_n", (8 - w, y0, 8 - r), (8 + w, y1, 8 - w), ("north", "east", "west") + ends, paint))
        parts.append((f"{name}_s", (8 - w, y0, 8 + w), (8 + w, y1, 8 + r), ("south", "east", "west") + ends, paint))
    prism("body", 2.0, 1.3, 1.5, 26, "body")
    prism("nose0", 1.6, 1.05, 26, 28.5, "nose_base")
    prism("nose1", 1.0, 0.66, 28.5, 30.8, "nose")
    parts.append(("tip", (7.6, 30.8, 7.6), (8.4, 32, 8.4), all6, "tip"))
    parts.append(("fin_n", (7.75, 1.5, 3.5), (8.25, 7, 6), ("north", "east", "west", "up", "down"), "fin"))
    parts.append(("fin_s", (7.75, 1.5, 10), (8.25, 7, 12.5), ("south", "east", "west", "up", "down"), "fin"))
    parts.append(("fin_w", (3.5, 1.5, 7.75), (6, 7, 8.25), ("west", "north", "south", "up", "down"), "fin"))
    parts.append(("fin_e", (10, 1.5, 7.75), (12.5, 7, 8.25), ("east", "north", "south", "up", "down"), "fin"))
    return parts


NOSE_PARTS = ("nose_base", "nose", "tip")


def missile_item() -> None:
    """The plain missile: what a missile with no warhead looks like (a test flight's, an old item's). Every warhead's own
    missile, and the Missile item's model choice, are tools/assets/missiles.py's."""
    missile_model("missile", "none", 1)
    stale()


def stale() -> None:
    """Deletes the models (and their textures) the missiles once shared: the plain missile's flight model, the airframes
    a warhead's model stood on, the Strategic and intercontinental missiles' own models and their spent stages."""
    old = ("flight_missile", "missile_airframe", "flight_missile_airframe", "strategic_missile_airframe", "strategic_missile_none",
           "flight_strategic_missile_",
           "icbm_missile_", "flight_icbm_missile_", "strategic_stage1", "flight_strategic_stage1", "icbm_stage", "flight_icbm_stage")
    for folder, ext in ((paths.MODELS / "item", ".json"), (paths.ASSETS / "textures" / "item", ".png")):
        for f in folder.glob(f"*{ext}"):
            if f.name[:-len(ext)].startswith(old):
                f.unlink()


def missile_model(model_name: str, nose: str, density: int = 1) -> None:
    """The plain missile: a white body, a bare steel nose."""
    parts = missile_parts()
    m = BlockModel(model_name, tex_size=(64 * density, 64 * density), kind="item", density=density)
    for name, frm, to, faces, _ in parts:
        m.box(name, frm, to, faces=faces)
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    MP.paint(M.Surfacer(m, seed=101), t, [(name, kind, nose) for name, _, _, _, kind in parts], MP.Frame(), "basic")
    m.fit_display(gui_rotation=(30, 45, 30), gui_margin=0.5)
    lo, hi = m.bounds()
    c = [(lo[i] + hi[i]) / 2 for i in range(3)]

    def ctx(scale, base, rotation=(0, 0, 0)):
        return {"rotation": list(rotation), "translation": [round(base[i] - (c[i] - 8) * scale, 3) for i in range(3)], "scale": [scale] * 3}
    m.display["ground"] = ctx(0.5, (0, 0, 0))
    m.display["fixed"] = ctx(0.5, (0, 0, 0))
    m.display["thirdperson_righthand"] = ctx(0.4, (0, 3, 1), (0, 90, 0))
    m.display["thirdperson_lefthand"] = ctx(0.4, (0, 3, 1), (0, 90, 0))
    m.display["firstperson_righthand"] = ctx(0.35, (1, 3, 0), (0, -25, 0))
    m.display["firstperson_lefthand"] = ctx(0.35, (1, 3, 0), (0, 155, 0))
    m.save(t)


# --- component blocks -----------------------------------------------------------------------------------------------------
def launch_pad() -> Tex:
    """Concrete with a hazard-striped border two texels wide and a groove inside it."""
    img = M.tile(M.concrete(**M.CONCRETE_TILE), seed=31)
    col, row = M.grid_xy()
    ring = np.minimum(np.minimum(col, 15 - col), np.minimum(row, 15 - row))
    img[ring < 2] = M.hazard_tile(width=2, seed=32, scuff=0.2)[ring < 2]
    M.darken(img, ring == 2, 0.72)
    return M.to_tex(img)


def lattice(material: "M.Material", step: int = 4, seed: int = 0) -> Tex:
    """A see-through lattice face: a frame two texels wide, an X brace and cross bars, the rest clear; the bars shaded
    round (lit above and left, dark below and right), the brace in front lighter than the one behind."""
    col, row = M.grid_xy()
    ring = np.minimum(np.minimum(col, 15 - col), np.minimum(row, 15 - row))
    inner = (col >= 2) & (col <= 13) & (row >= 2) & (row <= 13)
    front, back = inner & (col == row), inner & (col == 15 - row)
    bars = np.zeros_like(front)
    for y in range(step, 16, step * 2):
        bars |= (row == y) & (col >= 1) & (col <= 14)
    mask = (ring < 2) | front | back | bars
    img = M.tile(material, seed)
    M.bevel(img, mask)
    M.lighten(img, front, 1.1, 4)
    M.darken(img, back & ~front, 0.82)
    img[~mask, 3] = 0
    return M.to_tex(img)


def truss(material: "M.Material", seed: int = 0) -> Tex:
    """A see-through truss face (a Warren truss): top and bottom chords, a post every half block and a diagonal in
    each bay, the two leaning opposite ways; the chords shaded round."""
    col, row = M.grid_xy()
    mask = (row < 2) | (row > 13) | (((col % 8) == 0) & (row >= 2) & (row <= 13))
    for r in range(2, 14):
        mask |= (row == r) & (col == 1 + round((13 - r) * 6 / 11))
        mask |= (row == r) & (col == 9 + round((r - 2) * 6 / 11))
    img = M.tile(material, seed)
    M.bevel(img, mask)
    M.darken(img, row > 13, 0.86)
    img[~mask, 3] = 0
    return M.to_tex(img)


def shaped_part(name: str, build, icon: Tex) -> None:
    """A component block with a real shape (not a cube) and the usual formed / unformed blockstate. `icon` is a 16x16
    face used as its particle: break particles, and the Holo-Projector draws ghost blocks as cubes of it (the packed
    model texture would look like noise there)."""
    icon.save(paths.TEXTURES / "block" / f"{name}_icon.png")
    m = build(f"groundburst:block/{name}_icon")
    write_json(paths.MODELS / "block" / f"{name}_formed.json", ST.particle_model(f"groundburst:block/{name}_icon"))
    write_json(paths.ASSETS / "blockstates" / f"{name}.json", {"variants": {
        "formed=false": {"model": f"groundburst:block/{name}"},
        "formed=true": {"model": f"groundburst:block/{name}_formed"},
    }})
    write_json(paths.ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": f"groundburst:block/{name}"}})


def radar_dish_icon() -> Tex:
    t = M.to_tex(M.tile(M.painted(M.ARMOUR_RAMP, tone=0.45), seed=5, edges=True))
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d < 6.5:
                t.px(x, y, WHITE[5] if d < 2.5 else WHITE[4] if d < 4.5 else WHITE[3])
            elif d < 7.2:
                t.px(x, y, WHITE[2])
    t.rect(7, 7, 2, 2, GUN[3])                                   # the feed horn
    return t


def antenna_icon() -> Tex:
    t = M.to_tex(M.tile(M.painted(M.ARMOUR_RAMP, tone=0.45), seed=6, edges=True))
    t.rect(7, 1, 2, 13, STEEL[4])                                # the whip and its cross bars
    for i, y in enumerate((4, 7, 10)):
        t.rect(4 + i, y, 8 - 2 * i, 1, STEEL[3])
    t.rect(5, 13, 6, 2, GUN[4])                                  # the base
    return t


def radar_dish_block(particle: str) -> BlockModel:
    m = BlockModel("radar_dish", tex_size=(64, 64), kind="block")
    m.particle = particle
    m.box("stub", (6, 0, 6), (10, 4, 10), faces=("north", "south", "east", "west", "down"))
    m.box("yoke", (3, 4, 7), (13, 6, 9))
    # The dish, tilted back, as stepped rings facing north.
    for i, (r, z) in enumerate(((7, 3), (5.5, 4), (3.5, 5))):
        m.box(f"ring{i}", (8 - r, 8 - r * 0.7 + 3, z), (8 + r, 8 + r * 0.7 + 3, z + 1),
              faces=("north", "south", "east", "west", "up", "down"))
    m.box("feed", (7.5, 10.5, 0.5), (8.5, 11.5, 3))
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    S = M.Surfacer(m, seed=73, ao_reach=2.0)
    S.paint(t, ["stub", "yoke", "feed"], M.painted(M.ARMOUR_RAMP, tone=0.55, wear=0.25))
    S.paint(t, [f"ring{i}" for i in range(3)], M.white_paint())
    m.fit_display(gui_rotation=(30, 225, 0))
    m.save(t)
    return m


def antenna_block(particle: str) -> BlockModel:
    m = BlockModel("antenna", tex_size=(32, 32), kind="block")
    m.particle = particle
    m.box("base", (5, 0, 5), (11, 2, 11), faces=("north", "south", "east", "west", "up", "down"))
    m.box("whip", (7.5, 2, 7.5), (8.5, 16, 8.5), faces=("north", "south", "east", "west", "up"))
    for i, y in enumerate((8, 11, 14)):
        w = 4 - i
        m.box(f"bar{i}", (8 - w, y, 7.7), (7.5, y + 0.6, 8.3), faces=("north", "south", "west", "up", "down"))
        m.box(f"bar{i}e", (8.5, y, 7.7), (8 + w, y + 0.6, 8.3), faces=("north", "south", "east", "up", "down"))
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    S = M.Surfacer(m, seed=79, ao_reach=2.0)
    S.paint(t, "base", M.painted(M.ARMOUR_RAMP, tone=0.55, wear=0.25))
    S.paint(t, ["whip"] + [f"bar{i}{e}" for i in range(3) for e in ("", "e")], M.steel(tone=0.62))
    m.fit_display(gui_rotation=(30, 225, 0))
    m.save(t)
    return m


def plus_ring(r_out: float, r_in: float) -> list[tuple[float, float, float, float]]:
    """The flat ring between two "plus" discs (a plus disc of radius r: |x| <= r, |z| <= 0.66 r, or the other way round),
    centred on 0, as rectangles (x0, z0, x1, z1): cut on the grid of their edges, cells merged along x."""
    def plus(r, x, z):
        w = r * 0.66
        return (abs(x) <= r and abs(z) <= w) or (abs(x) <= w and abs(z) <= r)
    cuts = sorted({round(v, 3) for rr in (r_out, r_in) if rr > 0 for v in (rr, -rr, rr * 0.66, -rr * 0.66)})
    rects = []
    for j in range(len(cuts) - 1):
        z0, z1 = cuts[j], cuts[j + 1]
        run = None
        for i in range(len(cuts) - 1):
            x0, x1 = cuts[i], cuts[i + 1]
            mx, mz = (x0 + x1) / 2, (z0 + z1) / 2
            inside = plus(r_out, mx, mz) and not (r_in > 0 and plus(r_in, mx, mz))
            if inside:
                run = (run[0], x1) if run else (x0, x1)
            elif run:
                rects.append((run[0], z0, run[1], z1))
                run = None
        if run:
            rects.append((run[0], z0, run[1], z1))
    return rects


def large_dish_icon() -> Tex:
    t = M.to_tex(M.tile(M.white_paint(tone=0.62), seed=7, edges=True))
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d <= 6.5:
                t.px(x, y, WHITE[4] if d > 4.5 else WHITE[3] if d > 2 else GUN[3])
    return t


def large_dish_block(particle: str) -> BlockModel:
    """The Large Radar Dish, drawn by its middle block across all 3x3 (x and z -16..32): a bowl lying face up, stepped
    rings rising to its rim, on a hub, a feed horn on a post over its middle."""
    m = BlockModel("large_radar_dish", tex_size=(256, 256), kind="block")
    m.particle = particle
    m.box("hub", (5, 0, 5), (11, 3, 11), faces=("north", "south", "east", "west", "down"))
    rings = [(5, 0, 3, 4.5), (10.5, 5, 3.5, 6), (16, 10.5, 4, 7.5), (21, 16, 4.5, 9), (24, 21, 5, 10)]
    names = []
    for k, (ro, ri, y0, y1) in enumerate(rings):
        for i, (x0, z0, x1, z1) in enumerate(plus_ring(ro, ri)):
            n = f"r{k}_{i}"
            m.box(n, (8 + x0, y0, 8 + z0), (8 + x1, y1, 8 + z1))
            names.append((n, k))
    m.box("post", (7.4, 4.5, 7.4), (8.6, 13, 8.6), faces=("north", "south", "east", "west"))
    m.box("horn", (6.5, 13, 6.5), (9.5, 16, 9.5))
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    S = M.Surfacer(m, seed=83, ao_reach=3.0)
    S.paint(t, ["hub", "horn"], M.painted(M.ARMOUR_RAMP, tone=0.55, wear=0.25))
    for k in range(len(rings)):
        # The bowl's face lighter toward the rim (it catches the light).
        S.paint(t, [n for n, kk in names if kk == k], M.white_paint(tone=0.6 + 0.04 * k))
    S.paint(t, "post", M.steel(tone=0.6))
    m.fit_display(gui_rotation=(30, 225, 0))
    m.save(t)
    return m


def large_dish() -> None:
    icon = large_dish_icon()
    icon.save(paths.TEXTURES / "block" / "large_radar_dish_icon.png")
    large_dish_block("groundburst:block/large_radar_dish_icon")
    write_json(paths.MODELS / "block" / "large_radar_dish_formed.json", ST.particle_model("groundburst:block/large_radar_dish_icon"))
    write_json(paths.ASSETS / "blockstates" / "large_radar_dish.json", {"variants": {
        "formed=false,part=center": {"model": "groundburst:block/large_radar_dish"},
        "formed=false,part=edge": {"model": "groundburst:block/large_radar_dish_formed"},
        "formed=true": {"model": "groundburst:block/large_radar_dish_formed"},
    }})
    write_json(paths.ASSETS / "items" / "large_radar_dish.json", {"model": {"type": "minecraft:model", "model": "groundburst:block/large_radar_dish"}})


def missile_bone(g: GeoModel, parent: str, tail_y: float, cz: float) -> None:
    """A launcher's "missile" bone: no boxes, its pivot at the loaded missile's tail on its axis. The game draws the
    missile there from its own model (MachineGeoRenderer's missile layer)."""
    g.bone("missile", pivot=(0, tail_y, cz), parent=parent)


def missile_placard(t: Tex) -> None:
    t.rect(4, 9, 4, 4, P.BLACK_PAINT[1])
    t.vline(6, 9, 4, WHITE[5])
    t.px(6, 9, RED[4])
    t.px(5, 12, STEEL[4])
    t.px(7, 12, STEEL[4])


def radar_placard(t: Tex) -> None:
    t.rect(4, 9, 4, 4, P.BLACK_PAINT[1])
    t.px(5, 11, GREEN[4])
    t.px(6, 10, GREEN[4])
    t.px(7, 9, GREEN[5])
    t.px(5, 12, GREEN[2])


# --- the formed Launch Silo ---------------------------------------------------------------------------------------------
#: The arms as modelled (LaunchSiloBlockEntity's and LaunchComplexBlockEntity's grips carry the same figures): the silo
#: arm's middle height and the clamp's face (px from the missile's axis); the complex's two beams' bottoms and clamp.
ARM_Y, SILO_CLAMP_X = 101.5, 6.0
COMPLEX_LOW_Y, COMPLEX_HIGH_Y, COMPLEX_CLAMP_X = 70.0, 134.0, 7.0


def silo() -> None:
    g = GeoModel("launch_silo", tex_size=(256, 512))
    g.bone("frame")
    # The arm (DESIGN.md §10.4): its collar slides up the hinge spine to the loaded missile's own height and its beam
    # telescopes in to the missile's skin (MissileCarrier.Grip, MachineGeoRenderer); it swings away on its pin.
    g.bone("arm_mount", pivot=(24, ARM_Y, 16), parent="frame")
    g.bone("arm", pivot=(24, ARM_Y, 16), parent="arm_mount")      # on its hinge pin, just off the tower's face
    g.bone("arm_reach", pivot=(SILO_CLAMP_X, ARM_Y, 16), parent="arm")
    missile_bone(g, "frame", 14, 16)                             # its tail on the pad
    F = "frame"
    pad = [("pad_front", (-24, 0, -8), (24, 14, 10)), ("pad_back", (-24, 0, 22), (24, 14, 40)),
           ("pad_left", (-24, 0, 10), (-6, 14, 22)), ("pad_right", (6, 0, 10), (24, 14, 22))]
    for n, a, b in pad:
        g.box(n, a, b, bone=F)
    g.box("grate", (-6, 6, 10), (6, 7, 22), faces=("up", "down"), bone=F)
    posts = []
    for i, (x, z) in enumerate(((-10.5, 5.5), (8.5, 5.5), (-10.5, 24.5), (8.5, 24.5))):
        posts.append(f"hold{i}")
        g.box(posts[-1], (x, 14, z), (x + 2, 19, z + 2), faces=("north", "south", "east", "west", "up"), bone=F)
    g.box("console", (-5, 14, -7), (5, 22, -2), faces=("north", "south", "east", "west", "up"), bone=F)
    g.box("housing", (-5, 14, 35), (5, 19, 40), faces=("north", "south", "east", "west", "up"), bone=F)
    tower = []
    for i, (x, z) in enumerate(((25, 9), (37, 9), (25, 21), (37, 21))):
        tower.append(f"post{i}")
        g.box(tower[-1], (x, 0, z), (x + 2, 140, z + 2), faces=("north", "south", "east", "west"), bone=F)
    girders = []
    for k in range(1, 9):
        y = 16 * k - 1
        for n, a, b in ((f"gn{k}", (27, y, 9.3), (37, y + 1.5, 10.7)), (f"gs{k}", (27, y, 21.3), (37, y + 1.5, 22.7)),
                        (f"gw{k}", (25.3, y, 11), (26.7, y + 1.5, 21)), (f"ge{k}", (37.3, y, 11), (38.7, y + 1.5, 21))):
            g.box(n, a, b, faces=("north", "south", "east", "west", "up", "down"), bone=F)
            girders.append(n)
    g.box("platform", (24, 140, 8), (40, 142, 24), bone=F)
    # The arm: a sleeve off the hinge, a galvanised inner beam sliding in it and the clamp on its end (modelled at a
    # missile of radius SILO_CLAMP_X; it reaches in to 4 and out to 7.5 without leaving the sleeve).
    # (In these GeckoLib models "west" is the +x face, toward the tower, and "east" the face toward the missile.)
    g.box("arm_sleeve", (12, ARM_Y - 1.5, 14), (23.5, ARM_Y + 1.5, 18), faces=("north", "south", "up", "down", "east", "west"), bone="arm")
    g.box("arm_beam", (SILO_CLAMP_X + 1.5, ARM_Y - 1, 14.5), (SILO_CLAMP_X + 10.5, ARM_Y + 1, 17.5), faces=("north", "south", "up", "down"),
          bone="arm_reach")
    g.box("arm_clamp", (SILO_CLAMP_X, ARM_Y - 3.5, 12), (SILO_CLAMP_X + 1.5, ARM_Y + 4.5, 20), faces=("west", "north", "south", "up", "down"),
          bone="arm_reach")
    # The arm's hinge: a spine up the tower's face, a collar of two knuckles that slides on it and the pin the arm
    # turns on.
    hinge = ["hinge_spine", "hinge_top", "hinge_bottom", "hinge_pin"]
    g.box("hinge_spine", (24.6, 0, 15), (25.3, 140, 17), faces=("east", "north", "south", "up"), bone=F)
    g.box("hinge_top", (22.5, ARM_Y + 2, 14.5), (24.6, ARM_Y + 4, 17.5), faces=("east", "north", "south", "up", "down"), bone="arm_mount")
    g.box("hinge_bottom", (22.5, ARM_Y - 4, 14.5), (24.6, ARM_Y - 2, 17.5), faces=("east", "north", "south", "up", "down"), bone="arm_mount")
    g.box("hinge_pin", (23.5, ARM_Y - 5, 15.5), (24.5, ARM_Y + 5, 16.5), faces=("west", "north", "south", "east", "up", "down"), bone="arm_mount")
    g.layout()
    tex = Tex(g.tex_w, g.tex_h)

    paint_silo(g, tex, [n for n, _, _ in pad], posts, tower, girders, hinge)
    g.save(tex)
    # The arm swings away from the missile during the countdown and stays away.
    count = Animation("working", 10.0, loop="hold_on_last_frame")
    # (75 degrees: swung any further it would pass through the tower's corner post.)
    count.rotate("arm", {0: (0, 0, 0), 2.5: (0, -75, 0), 10: (0, -75, 0)}, easing="easeInOutSine")
    print(save_animations("launch_silo", [count]))


# --- the formed Launch Complex (Phase 8) ---------------------------------------------------------------------------------


def flame_deflector() -> Tex:
    """The deflector block: heavy steel plates blackened by exhaust, heat-tinted (straw, then blue) along their welds,
    a hazard edge along the top."""
    f = M.tile_face()
    img = M.Material(ramp=M.STEEL_RAMP[:7], tone=0.5, mottle=0.06, grain=0.05, dither=0.7, despeckle=False).render(f, 17)
    img = np.clip(img, 0, 255).astype(np.uint8)
    M.sooted(f, img, 0.35 + 0.5 * M.noise(f, 6.0, 19, 2), 0.6)
    col, row = M.grid_xy()
    for y in (3, 7, 11, 15):
        img[row == y, :3] = (22, 23, 26)                          # the weld between two plates
        if y + 1 < 16:                                           # heat-blued just below it, here and there
            tint = (row == y + 1) & (M.noise(f, (5.0, 16.0, 1.0), 29 + y) > 0.4)
            img[tint, :3] = np.clip(img[tint, :3].astype(np.float32) * 0.55 + np.array([58, 66, 104]) * 0.45, 0, 255)
    top = (row == 0)
    img[top] = M.hazard_tile(width=2, seed=18)[top]
    M.darken(img, ((col == 0) | (col == 15) | (row == 15)) & (row > 0), 0.7)
    return M.to_tex(img)


def complex_placard(t: Tex) -> None:
    """The controller's placard: a missile on a yellow radiation tag."""
    t.rect(4, 9, 4, 4, YELLOW[4])
    t.vline(6, 9, 4, P.BLACK_PAINT[1])
    t.px(5, 12, P.BLACK_PAINT[1])
    t.px(7, 12, P.BLACK_PAINT[1])


def launch_complex() -> None:
    g = GeoModel("launch_complex", tex_size=(512, 1024))
    g.bone("frame")
    # Each umbilical arm (DESIGN.md §10.4): its collar slides up the spine to its place on the loaded missile and its
    # beam telescopes in to the missile's skin (MissileCarrier.Grip, MachineGeoRenderer); each swings away on its pin.
    for arm, y in (("arm_low", COMPLEX_LOW_Y), ("arm_high", COMPLEX_HIGH_Y)):
        g.bone(f"{arm}_mount", pivot=(38, y + 2, 32), parent="frame")
        g.bone(arm, pivot=(38, y + 2, 32), parent=f"{arm}_mount")   # on its hinge pin, just off the tower's face
        g.bone(f"{arm}_reach", pivot=(COMPLEX_CLAMP_X, y + 2, 32), parent=arm)
    missile_bone(g, "frame", 14, 32)                           # its tail over the trench
    F = "frame"
    # The deck: 5x5 with a flame trench (z 26-38) running from under the missile out of its west edge.
    deck = [("deck_front", (-40, 0, -8), (40, 14, 26), ("north", "south", "east", "west", "up")),
            ("deck_back", (-40, 0, 38), (40, 14, 72), ("north", "south", "east", "west", "up")),
            ("deck_east", (8, 0, 26), (40, 14, 38), ("east", "west", "up"))]
    for n, a, b, faces in deck:
        g.box(n, a, b, faces=faces, bone=F)
    g.box("trench_floor", (-40, 0, 26), (8, 2, 38), faces=("up", "west"), bone=F)
    wedge = []
    for i, (x0, y0, y1) in enumerate(((-8, 2, 5), (-4, 5, 8), (0, 8, 11))):
        wedge.append(f"wedge{i}")
        g.box(wedge[-1], (x0, y0, 26), (8, y1, 38), faces=("up", "west"), bone=F)
    # Deluge pipes along both lips of the trench, nozzles pointing in.
    pipes = []
    for n, z0 in (("deluge_n", 23.5), ("deluge_s", 38)):
        pipes.append(n)
        g.box(n, (-39, 14, z0), (7, 16.5, z0 + 2.5), faces=("north", "south", "west", "up"), bone=F)
    # Hold-down posts on the diagonals round the missile (clear of its fins).
    posts = []
    for i, (x, z) in enumerate(((-14, 18), (12, 18), (-14, 44), (12, 44))):
        posts.append(f"hold{i}")
        g.box(posts[-1], (x, 14, z), (x + 2, 20, z + 2), faces=("north", "south", "east", "west", "up"), bone=F)
    g.box("console", (-5, 14, -7), (5, 22, -2), faces=("north", "south", "east", "west", "up"), bone=F)
    g.box("housing", (-5, 14, 67), (5, 19, 72), faces=("north", "south", "east", "west", "up"), bone=F)
    # The service tower: column 5, rows 2-3 (x 40-56, z 24-56), 13 high.
    tower = []
    for i, (x, z) in enumerate(((40, 24), (54, 24), (40, 54), (54, 54))):
        tower.append(f"post{i}")
        g.box(tower[-1], (x, 0, z), (x + 2, 208, z + 2), faces=("north", "south", "east", "west"), bone=F)
    girders = []
    for k in range(1, 13):
        if k in (4, 8, 12):
            continue                                     # the platform decks are the frame at these storeys
        y = 16 * k - 1
        for n, a, b in ((f"gn{k}", (42, y, 24.3), (54, y + 1.5, 25.7)), (f"gs{k}", (42, y, 54.3), (54, y + 1.5, 55.7)),
                        (f"gw{k}", (40.3, y, 26), (41.7, y + 1.5, 54)), (f"ge{k}", (54.3, y, 26), (55.7, y + 1.5, 54))):
            g.box(n, a, b, faces=("north", "south", "east", "west", "up", "down"), bone=F)
            girders.append(n)
    # Diagonal braces down the outer (east) face: short steps from corner to corner of each storey.
    braces = []
    for k in range(13):
        for s in range(4):
            z = 26 + s * 7 if k % 2 == 0 else 47 - s * 7
            braces.append(f"br{k}_{s}")
            g.box(braces[-1], (54.6, 16 * k + 2 + s * 3.5, z), (55.4, 16 * k + 5.5 + s * 3.5, z + 7), faces=("east", "west", "up", "down"), bone=F)
    decks = []
    for y in (63, 127, 191):
        decks.append(f"level{y}")
        g.box(decks[-1], (39, y, 23), (57, y + 2, 57), bone=F)
    rails = []
    for y in (65, 129, 193):
        rails.append(f"rail{y}")
        g.box(rails[-1], (56.5, y, 23), (57, y + 1, 57), faces=("east", "west", "up"), bone=F)
    g.box("roof", (39, 208, 23), (57, 210, 57), bone=F)
    g.box("mast", (47, 210, 39), (49, 226, 41), faces=("north", "south", "east", "west"), bone=F)
    g.box("beacon", (46.5, 226, 38.5), (49.5, 229, 41.5), bone=F)
    # The umbilical arms: a sleeve off the hinge with a hose slung under it, a galvanised inner beam sliding in it and
    # the clamp on its end (modelled at a missile of radius COMPLEX_CLAMP_X; it reaches in to 3.5 and out to 10.5
    # without leaving the sleeve); each turns on a pin between two knuckles of a collar that slides up a spine on the
    # tower's face.
    arms = []
    hinges = ["spine"]
    g.box("spine", (39.4, 14, 30.5), (40.3, 208, 33.5), faces=("east", "north", "south", "up"), bone=F)
    for bone, y in (("arm_low", COMPLEX_LOW_Y), ("arm_high", COMPLEX_HIGH_Y)):
        x = COMPLEX_CLAMP_X
        for n, a, b, faces, owner in (
                (f"{bone}_sleeve", (15, y, 30), (37.5, y + 4, 34), ("north", "south", "up", "down", "east", "west"), bone),
                (f"{bone}_hose", (15.5, y - 3, 31), (36.5, y - 1, 33), ("north", "south", "down", "west", "east"), bone),
                (f"{bone}_beam", (x + 2, y + 0.5, 30.5), (x + 14, y + 3.5, 33.5), ("north", "south", "up", "down"), f"{bone}_reach"),
                (f"{bone}_clamp", (x, y - 2, 27), (x + 2, y + 6, 37), ("west", "north", "south", "up", "down"), f"{bone}_reach")):
            g.box(n, a, b, faces=faces, bone=owner)
            arms.append(n)
        for n, a, b, faces in ((f"{bone}_knuckle_top", (36.5, y + 4.5, 30), (39.4, y + 6.5, 34), ("east", "north", "south", "up", "down")),
                               (f"{bone}_knuckle_bottom", (36.5, y - 2.5, 30), (39.4, y - 0.5, 34), ("east", "north", "south", "up", "down")),
                               (f"{bone}_pin", (37.5, y - 3.5, 31.5), (38.5, y + 7.5, 32.5), ("west", "north", "south", "east", "up", "down"))):
            g.box(n, a, b, faces=faces, bone=f"{bone}_mount")
            hinges.append(n)
    g.layout()
    tex = Tex(g.tex_w, g.tex_h)

    paint_complex(g, tex, [n for n, _, _, _ in deck], wedge, pipes, posts, tower, girders, braces, decks, rails, hinges)
    g.save(tex)
    glowmask = paths.ASSETS / "textures" / "block" / "launch_complex_glowmask.png"
    glowmask.unlink(missing_ok=True)                           # (the Tsar's vents were all it lit: its own model lights them now)
    # The countdown (15 s): the lower umbilical swings away at T-13, the upper one at T-9; both stay away. The clip runs
    # 20 s, past the countdown and the ignition smoke while the complex is still working (the machine loops it), and
    # an empty complex rests in "clear" with both arms still away.
    count = Animation("working", 20.0, loop="hold_on_last_frame")
    # (75 degrees: swung any further they would pass through the tower's corner posts.)
    count.rotate("arm_low", {0: (0, 0, 0), 2: (0, 0, 0), 4.5: (0, -75, 0), 20: (0, -75, 0)}, easing="easeInOutSine")
    count.rotate("arm_high", {0: (0, 0, 0), 6: (0, 0, 0), 8.5: (0, -75, 0), 20: (0, -75, 0)}, easing="easeInOutSine")
    clear = Animation("clear", 1.0, loop="hold_on_last_frame")
    clear.rotate("arm_low", {0: (0, -75, 0), 1: (0, -75, 0)})
    clear.rotate("arm_high", {0: (0, -75, 0), 1: (0, -75, 0)})
    print(save_animations("launch_complex", [count, clear]))


# --- the formed Launch Computer -----------------------------------------------------------------------------------------
def computer() -> None:
    g = GeoModel("launch_computer", tex_size=(256, 256))
    g.bone("frame")
    g.bone("dish", pivot=(0, 52, 16), parent="frame")
    g.bone("dish_tilt", pivot=(0, 54, 16), parent="dish", rotation=(40, 0, 0))     # looking well up at the sky
    F = "frame"
    g.box("desk", (-7, 0, -7), (23, 10, 7), bone=F)
    g.box("console", (-7, 10, -7), (23, 12, 0), faces=("north", "east", "west", "up"), bone=F)
    # The screens stand on the controller half of the desk, in front of the mast; the antenna on the far half, where
    # nothing the same colour stands behind it.
    g.box("crt_l", (-6.5, 10, 0), (2, 20, 6), faces=("north", "south", "east", "west", "up"), bone=F)
    g.box("crt_r", (2.5, 10, 0), (11, 20, 6), faces=("north", "south", "east", "west", "up"), bone=F)
    g.box("socket", (23, 2, -5), (24.5, 9, 5), faces=("north", "south", "east", "up", "down"), bone=F)
    g.box("ant_base", (16, 10, 1), (20, 12, 5), faces=("north", "south", "east", "west", "up"), bone=F)
    g.box("ant_whip", (17.6, 12, 2.4), (18.4, 40, 3.6), faces=("north", "south", "east", "west", "up"), bone=F)
    bars = []
    for i, y in enumerate((28, 32, 36)):
        w = 5 - i
        bars.append(f"ant_bar{i}")
        g.box(bars[-1], (18 - w, y, 2.7), (17.6, y + 0.6, 3.3), faces=("north", "south", "west", "up", "down"), bone=F)
        bars.append(f"ant_bar{i}e")
        g.box(bars[-1], (18.4, y, 2.7), (18 + w, y + 0.6, 3.3), faces=("north", "south", "east", "up", "down"), bone=F)
    mast = []
    for i, (x, z) in enumerate(((-6, 10), (4, 10), (-6, 20), (4, 20))):
        mast.append(f"mpost{i}")
        g.box(mast[-1], (x, 0, z), (x + 2, 48, z + 2), faces=("north", "south", "east", "west"), bone=F)
    for k in range(1, 6):
        y = 8 * k
        for n, a, b in ((f"mn{k}", (-4, y, 10.4), (4, y + 1, 11.6)), (f"ms{k}", (-4, y, 20.4), (4, y + 1, 21.6)),
                        (f"mw{k}", (-5.6, y, 12), (-4.4, y + 1, 20)), (f"me{k}", (4.4, y, 12), (5.6, y + 1, 20))):
            g.box(n, a, b, faces=("north", "south", "east", "west", "up", "down"), bone=F)
            mast.append(n)
    g.box("mast_cap", (-6, 48, 10), (6, 50, 22), bone=F)
    g.box("yoke", (-2, 50, 14), (2, 53, 18), faces=("north", "south", "east", "west", "up"), bone="dish")
    rings = []
    for i, (r, z) in enumerate(((12, 9), (9, 10), (6, 11), (3, 12))):
        w = round(r * 0.66, 2)
        for part, a, b, faces in ((f"d{i}c", (-r, 54 - w, z), (r, 54 + w, z + 1), ("north", "south", "east", "west")),
                                  (f"d{i}u", (-w, 54 + w, z), (w, 54 + r, z + 1), ("north", "south", "east", "west", "up")),
                                  (f"d{i}d", (-w, 54 - r, z), (w, 54 - w, z + 1), ("north", "south", "east", "west", "down"))):
            g.box(part, a, b, faces=faces, bone="dish_tilt")
            rings.append(part)
    g.box("back", (-2.5, 51.5, 13), (2.5, 56.5, 14.5), faces=("south", "east", "west", "up", "down"), bone="dish_tilt")
    g.box("feed_arm", (-0.4, 53.6, 3), (0.4, 54.4, 9), faces=("east", "west", "up", "down"), bone="dish_tilt")
    g.box("feed", (-1.2, 52.8, 1.5), (1.2, 55.2, 3), bone="dish_tilt")
    g.layout()
    tex = Tex(g.tex_w, g.tex_h)
    glow = Tex(g.tex_w, g.tex_h)

    paint_computer(g, tex, glow, bars, mast, rings)
    g.save(tex, glow)
    spin = Animation("working", 4.0, loop=True)
    spin.rotate("dish", {0: (0, 0, 0), 4: (0, 360, 0)})
    print(save_animations("launch_computer", [spin]))


# --- the formed Radar Array ---------------------------------------------------------------------------------------------
def radar_placard_big(t: Tex) -> None:
    """The Radar Array controller's placard: a dish's arc over a blip."""
    t.rect(4, 9, 5, 4, P.BLACK_PAINT[1])
    for x, y in ((4, 9), (5, 10), (6, 11), (7, 11), (8, 10)):
        t.px(x, y, STEEL[5])
    t.px(6, 12, RED[4])


def radar_array() -> None:
    """A 3x3 deck (the controller's cabinet at the front, the power box at the back), a lattice mast three blocks up
    from its middle, and on top a big dish (3 blocks across) that turns on its yoke, looking up at the sky; a red
    warning lamp on the mast cap."""
    g = GeoModel("radar_array", tex_size=(256, 256))
    g.bone("frame")
    g.bone("dish", pivot=(0, 68, 16), parent="frame")
    g.bone("dish_tilt", pivot=(0, 76, 16), parent="dish", rotation=(30, 0, 0))
    F = "frame"
    g.box("deck", (-23, 0, -7), (23, 6, 39), bone=F)
    g.box("cabinet", (-7, 6, -6), (7, 20, 0), faces=("north", "south", "east", "west", "up"), bone=F)
    g.box("powerbox", (-5, 6, 31), (5, 15, 36), faces=("north", "south", "east", "west", "up"), bone=F)
    rails = []
    for i, (x, z) in enumerate(((-22, -6), (21, -6), (-22, 37), (21, 37))):
        rails.append(f"rpost{i}")
        g.box(rails[-1], (x, 6, z), (x + 1, 13, z + 1), faces=("north", "south", "east", "west", "up"), bone=F)
    for n, a, b in (("rail_w", (-22, 12, -5), (-21, 13, 37)), ("rail_e", (21, 12, -5), (22, 13, 37)),
                    ("rail_s", (-21, 12, 37), (21, 13, 38))):
        g.box(n, a, b, faces=("north", "south", "east", "west", "up", "down"), bone=F)
        rails.append(n)
    mast = []
    for i, (x, z) in enumerate(((-6, 10), (4, 10), (-6, 20), (4, 20))):
        mast.append(f"mpost{i}")
        g.box(mast[-1], (x, 6, z), (x + 2, 66, z + 2), faces=("north", "south", "east", "west"), bone=F)
    for k in range(1, 8):
        y = 6 + 8 * k
        for n, a, b in ((f"mn{k}", (-4, y, 10.4), (4, y + 1, 11.6)), (f"ms{k}", (-4, y, 20.4), (4, y + 1, 21.6)),
                        (f"mw{k}", (-5.6, y, 12), (-4.4, y + 1, 20)), (f"me{k}", (4.4, y, 12), (5.6, y + 1, 20))):
            g.box(n, a, b, faces=("north", "south", "east", "west", "up", "down"), bone=F)
            mast.append(n)
    g.box("mast_cap", (-7, 66, 9), (7, 68, 23), bone=F)
    g.box("lamp", (6, 60, 20), (7.5, 62.5, 22), faces=("north", "south", "east", "up", "down"), bone=F)   # on the mast
    g.box("yoke", (-3, 68, 13), (3, 73, 19), faces=("north", "south", "east", "west", "up"), bone="dish")
    g.box("yoke_arm_w", (-5, 70, 14), (-3, 78, 18), faces=("north", "south", "east", "west", "up"), bone="dish")
    g.box("yoke_arm_e", (3, 70, 14), (5, 78, 18), faces=("north", "south", "east", "west", "up"), bone="dish")
    rings = []
    for i, (rr, z) in enumerate(((22, 6), (17, 7.5), (11, 9), (5, 10.5))):
        w = round(rr * 0.66, 2)
        for part, a, b, faces in ((f"d{i}c", (-rr, 76 - w, z), (rr, 76 + w, z + 1.5), ("north", "south", "east", "west")),
                                  (f"d{i}u", (-w, 76 + w, z), (w, 76 + rr, z + 1.5), ("north", "south", "east", "west", "up")),
                                  (f"d{i}d", (-w, 76 - rr, z), (w, 76 - w, z + 1.5), ("north", "south", "east", "west", "down"))):
            g.box(part, a, b, faces=faces, bone="dish_tilt")
            rings.append(part)
    g.box("dish_back", (-6, 70, 12), (6, 82, 14), faces=("south", "east", "west", "up", "down"), bone="dish_tilt")
    g.box("feed_arm", (-0.5, 75.5, -6), (0.5, 76.5, 6), faces=("east", "west", "up", "down"), bone="dish_tilt")
    g.box("feed", (-2, 74, -9), (2, 78, -6), bone="dish_tilt")
    g.layout()
    tex = Tex(g.tex_w, g.tex_h)
    glow = Tex(g.tex_w, g.tex_h)

    paint_radar(g, tex, glow, rails, mast, rings)
    g.save(tex, glow)
    spin = Animation("working", 4.8, loop=True)                 # one turn every 4.8 s, as its screen's sweep
    spin.rotate("dish", {0: (0, 0, 0), 4.8: (0, 360, 0)})
    print(save_animations("radar_array", [spin]))


# --- surfaces (the remaster after slice 9b: tools/gb/materials.py, DESIGN.md §4.3) --------------------------------------
GANTRY_RAMP = M.ramp("#2a0904", "#3a0e06", "#64180a", "#8e2410", "#b83418", "#d4502a", "#e67a52", "#f4ac8c", "#fbd2bd")
SIDES = ("north", "south", "east", "west")


def _hazard_posts(S, tex, posts, top: float) -> None:
    """Hold-down posts: yellow, worn, their tops black."""
    S.paint(tex, posts, M.painted(M.YELLOW_PAINT_RAMP, tone=0.6, wear=0.45))
    black = M.painted(M.BLACK_RAMP, tone=0.5, wear=0.3)
    S.overlay(tex, posts, lambda f, img: M.paste(img, S.material_on(f, black), (f.Y > top) if f.vertical else np.ones(f.shape, bool)))


def _console(S, g, tex, name: str, lamps=(RED, GREEN)) -> None:
    """An olive console with its screen and two lamps on its front."""
    S.paint(tex, name, M.painted(M.OLIVE_RAMP, tone=0.55, wear=0.3))

    def front(t: Tex, r, d):
        x, y, w, h = r
        if d == "north" and w > 6:
            t.rect(x + 2, y + 1, w - 4, 4, P.BLACK_PAINT.deep)
            for xx in range(x + 2, x + w - 2):
                t.px(xx, y + 3 + (xx % 2), GREEN[4])
            t.px(x + 2, y + h - 2, lamps[0][4])
            t.px(x + 4, y + h - 2, lamps[1][4])
    g.paint_all(tex, name, front)


def _power_housing(S, g, tex, name: str, plug: str | None = "south") -> None:
    """The Energy Port's housing: dark steel, a red-and-black band round its top, its socket on one face."""
    S.paint(tex, name, M.painted(M.ARMOUR_RAMP, tone=0.55, wear=0.3, rust=0.08))
    band = M.warning_stripes(width=2)
    S.overlay(tex, name, lambda f, img: M.paste(img, S.material_on(f, band), f.border["top"] < 3), only=SIDES)

    def socket(t: Tex, r, d):
        x, y, w, h = r
        if d == plug and w > 4:
            t.rect(x + w // 2 - 2, y + 2, 4, 3, GUN[4])
            t.rect(x + w // 2 - 1, y + 3, 2, 1, BLACK[1])
    if plug:
        g.paint_all(tex, name, socket)


def _grating(S, tex, names, toe) -> None:
    """Galvanised walkway grating, its toe boards painted."""
    S.paint(tex, names, M.galvanized(tone=0.6))
    S.overlay(tex, names, lambda f, img: M.grid(f, img, every=2, depth=0.5), only=("up", "down"))
    S.paint(tex, names, M.painted(toe, tone=0.58, wear=0.35), only=SIDES)


def _apron(cx: float, cz: float, half: float, reach: float, seed: int, trench=None):
    """How scorched a launch apron is round its flame hole (a square of half-size {half} at cx, cz), or along its trench
    (x0, x1, z0, z1): heaviest at the lip, gone {reach} pixels out, patchy."""
    def amount(f):
        if trench is None:
            d = np.maximum(np.abs(f.X - cx), np.abs(f.Z - cz)) - half
        else:
            x0, x1, z0, z1 = trench
            dx = np.maximum(0, np.maximum(x0 - f.X, f.X - x1))
            dz = np.maximum(0, np.maximum(z0 - f.Z, f.Z - z1))
            d = np.hypot(dx, dz)
        # Concentric with the source, its boundaries ragged (noise moves them in and out), not blotches of its own.
        d = d + (M.fbm(f.X, f.Y, f.Z, 3.0, seed, 3) - 0.5) * reach * 0.6
        return np.clip(1 - d / reach, 0, 1) ** 1.1
    return amount


def paint_silo(g: GeoModel, tex: Tex, pad, posts, tower, girders, hinge) -> None:
    """The Launch Silo's surfaces: a concrete pad scorched round its flame hole, hazard bands round its edge and the
    hole; a red gantry, its platform galvanised grating; a dark steel grate over the hole."""
    S = M.Surfacer(g, seed=61)
    S.paint(tex, pad, M.concrete(tone=0.6))
    band = M.stripes(width=3, wear=0.4)
    scorch = _apron(0, 16, 6, 10, 7)

    def apron(f, img):
        hole = np.maximum(np.abs(f.X), np.abs(f.Z - 16))
        M.paste(img, S.material_on(f, band), (np.abs(f.X) > 21) | (f.Z < -5) | (f.Z > 37) | (hole < 8))
        M.sooted(f, img, scorch(f), 0.62)
    S.overlay(tex, pad, apron, only=("up",))
    S.paint(tex, "grate", M.steel(tone=0.32, rust=0.2))
    S.overlay(tex, "grate", lambda f, img: (M.darken(img, (np.floor(f.X) % 2) == 1, 0.25), M.sooted(f, img, np.full(f.shape, 0.6), 0.5)))
    _hazard_posts(S, tex, posts, top=17)
    _console(S, g, tex, "console")
    _power_housing(S, g, tex, "housing")
    red = M.painted(GANTRY_RAMP, tone=0.6, wear=0.18, rust=0.08, streaks=0.05)
    S.paint(tex, tower + girders + ["hinge_spine", "arm_sleeve"], red)
    S.paint(tex, "arm_beam", M.galvanized(tone=0.6, rust=0.08))
    _grating(S, tex, ["platform"], GANTRY_RAMP)
    S.paint(tex, ["arm_clamp"] + [n for n in hinge if n != "hinge_spine"], M.painted(M.ARMOUR_RAMP, tone=0.58, wear=0.3))


def paint_complex(g: GeoModel, tex: Tex, deck, wedge, pipes, posts, tower, girders, braces, decks, rails, hinges) -> None:
    """The Launch Complex's surfaces: a concrete deck cut into slabs round the missile, scorched along its trench and
    banded round its edge; a sooted trench and deflector; galvanised deluge pipes; a grey tower with galvanised
    walkways and orange toe boards and rails; orange umbilical arms."""
    S = M.Surfacer(g, seed=53)
    S.paint(tex, deck, M.concrete(tone=0.6))
    band = M.stripes(width=3, wear=0.4)
    scorch = _apron(0, 0, 0, 16, 11, trench=(-40, 8, 26, 38))

    def apron(f, img):
        joint = (np.abs(np.abs(f.X) - 24.5) < 0.25) | (np.abs(np.abs(f.Z - 32) - 24.5) < 0.25)
        M.darken(img, joint, 0.62)
        M.paste(img, S.material_on(f, band), (np.abs(f.X) > 37) | (f.Z < -5) | (f.Z > 69))
        M.sooted(f, img, scorch(f), 0.62)
    S.overlay(tex, deck, apron, only=("up",))
    S.paint(tex, "trench_floor", M.soot(tone=0.25))
    S.paint(tex, wedge, M.soot(tone=0.45, edge_light=0.3))

    def weld(f, img):                                          # heat-blued welds along the deflector's steps
        lip = (f.border["left"] < 1) if f.d == "up" else (f.border["top"] < 1)
        img[lip, :3] = (70, 82, 120)
    S.overlay(tex, wedge, weld)
    S.paint(tex, pipes, M.galvanized(tone=0.62, rust=0.12))

    def nozzles(t: Tex, r, d):
        x, y, w, h = r
        if d in ("north", "south") and w > 4:
            for xx in range(x + 2, x + w, 5):
                t.px(xx, y + h // 2, P.CHERENKOV[4])
                t.px(xx, y + h // 2 + 1, P.BLACK_PAINT.deep)
    for n in pipes:
        g.paint_all(tex, n, nozzles)
    _hazard_posts(S, tex, posts, top=18)
    _console(S, g, tex, "console", lamps=(RED, YELLOW))
    _power_housing(S, g, tex, "housing", plug=None)
    grey = M.painted(M.GREY_PAINT_RAMP, tone=0.58, wear=0.15, rust=0.08, streaks=0.05)
    S.paint(tex, tower + girders + braces + ["mast", "spine"], grey)
    _grating(S, tex, decks + ["roof"], M.ORANGE_PAINT_RAMP)
    S.paint(tex, rails, M.painted(M.ORANGE_PAINT_RAMP, tone=0.6, wear=0.3))
    g.paint_all(tex, "beacon", lambda t, r, d: t.rect(*r, RED[5] if d != "down" else RED[3]))
    orange = M.painted(M.ORANGE_PAINT_RAMP, tone=0.58, wear=0.3, rust=0.06)
    S.paint(tex, ["arm_low_sleeve", "arm_high_sleeve"], orange)
    S.paint(tex, ["arm_low_beam", "arm_high_beam"], M.galvanized(tone=0.6, rust=0.08))
    clamps = ["arm_low_clamp", "arm_high_clamp"]
    S.paint(tex, clamps + [n for n in hinges if n != "spine"], M.painted(M.ARMOUR_RAMP, tone=0.58, wear=0.3))
    S.paint(tex, ["arm_low_hose", "arm_high_hose"], M.rubber(tone=0.55))


def paint_computer(g: GeoModel, tex: Tex, glow: Tex, bars, mast, rings) -> None:
    """The Launch Computer's surfaces: an olive desk with drawers, a dark console and screens, a galvanised mast and a
    white dish."""
    S = M.Surfacer(g, seed=67)
    S.paint(tex, "desk", M.painted(M.OLIVE_RAMP, tone=0.55, wear=0.3))

    def drawers(f, img):                                       # four drawers: a dark gap round each, a steel pull
        col, row = f.border["left"], f.border["top"]
        h = f.shape[0]
        for i in range(4):
            s = 2 + 7 * i
            ring = (col >= s) & (col <= s + 4) & (row >= 2) & (row <= h - 2)
            inside = (col > s) & (col < s + 4) & (row > 2) & (row < h - 2)
            M.darken(img, ring & ~inside, 0.55)
            M.darken(img, inside, 0.92)
            img[(col == s + 2) & (row == 4), :3] = STEEL[5][:3]
    S.overlay(tex, "desk", drawers, only=("north",))
    S.paint(tex, ["console", "crt_l", "crt_r", "socket", "yoke", "back", "mast_cap"], M.painted(M.ARMOUR_RAMP, tone=0.55, wear=0.25))

    def keys(t: Tex, r, d):
        x, y, w, h = r
        if d == "up":
            for i in range(x + 2, x + w - 2, 3):
                t.px(i, y + 2, STEEL[5])
                t.px(i, y + h - 3, [RED[4], YELLOW[4], GREEN[4]][(i // 3) % 3])
    g.paint_all(tex, "console", keys)

    def crt(t: Tex, r, d):
        x, y, w, h = r
        if d == "north" and w > 6:
            t.rect(x + 1, y + 1, w - 2, h - 3, P.BLACK_PAINT.deep)
            glow.rect(x + 1, y + 1, w - 2, h - 3, (0, 0, 0, 255))
            for xx in range(x + 1, x + w - 1):            # a sweep and a few blips, glowing
                yy = y + 1 + (h - 4) // 2 + ((xx * 7) % 5) - 2
                for target in (t, glow):
                    target.px(xx, yy, GREEN[4])
            for target in (t, glow):
                target.px(x + 3, y + 3, GREEN[6])
                target.px(x + w - 4, y + h - 4, GREEN[6])
    for n in ("crt_l", "crt_r"):
        g.paint_all(tex, n, crt)
    band = M.warning_stripes(width=2)
    S.overlay(tex, "socket", lambda f, img: M.paste(img, S.material_on(f, band), f.border["top"] < 3), only=("east",))

    def plug(t: Tex, r, d):
        x, y, w, h = r
        if d == "east":
            t.rect(x + w // 2 - 2, y + 3, 4, 3, GUN[4])
            t.rect(x + w // 2 - 1, y + 4, 2, 1, BLACK[1])
    g.paint_all(tex, "socket", plug)
    S.paint(tex, ["ant_base", "ant_whip", *bars, "feed_arm", "feed"], M.steel(tone=0.6))
    S.paint(tex, mast, M.galvanized(tone=0.6, wear=0.0))
    S.paint(tex, rings, M.white_paint())


def paint_radar(g: GeoModel, tex: Tex, glow: Tex, rails, mast, rings) -> None:
    """The Radar Array's surfaces: a concrete deck banded round its edge, yellow railings, an olive cabinet with its
    scope, the power box, a galvanised mast and a white dish on a dark yoke."""
    S = M.Surfacer(g, seed=71)
    S.paint(tex, "deck", M.concrete(tone=0.6))
    band = M.stripes(width=3, wear=0.4)
    S.overlay(tex, "deck", lambda f, img: M.paste(img, S.material_on(f, band), (np.abs(f.X) > 20) | (f.Z < -4) | (f.Z > 36)),
              only=("up",))
    S.paint(tex, "cabinet", M.painted(M.OLIVE_RAMP, tone=0.55, wear=0.3))

    def scope(t: Tex, rr, d):
        x, y, w, h = rr
        if d == "north" and w > 8:
            t.rect(x + 2, y + 2, w - 4, 6, P.BLACK_PAINT.deep)
            glow.rect(x + 2, y + 2, w - 4, 6, (0, 0, 0, 255))
            for target in (t, glow):                     # the scope: a ring and a sweep line
                target.px(x + w // 2, y + 5, GREEN[5])
                target.px(x + w // 2 + 1, y + 4, GREEN[4])
                target.px(x + w // 2 + 2, y + 3, GREEN[3])
                target.px(x + 3, y + 3, GREEN[2])
            t.px(x + 2, y + h - 3, RED[4])
            t.px(x + 4, y + h - 3, GREEN[4])
    g.paint_all(tex, "cabinet", scope)
    _power_housing(S, g, tex, "powerbox", plug=None)
    S.paint(tex, rails, M.painted(M.YELLOW_PAINT_RAMP, tone=0.6, wear=0.35, rust=0.08))
    S.paint(tex, mast + ["mast_cap"], M.galvanized(tone=0.6, wear=0.0))
    g.paint_all(tex, "lamp", lambda t, rr, d: t.rect(*rr, RED[5]))
    for d in ("north", "south", "east", "up", "down"):
        glow.rect(*g.rect("lamp", d), RED[5])
    S.paint(tex, ["yoke", "yoke_arm_w", "yoke_arm_e", "dish_back", "feed_arm", "feed"], M.painted(M.ARMOUR_RAMP, tone=0.55, wear=0.25))
    S.paint(tex, rings, M.white_paint())


def main() -> None:
    missile_item()
    ST.part_block("launch_pad", launch_pad())
    ST.part_block("gantry_frame", lattice(M.painted(GANTRY_RAMP, tone=0.6, wear=0.0), seed=3), cutout=True)
    ST.part_block("gantry_arm", truss(M.painted(GANTRY_RAMP, tone=0.6, wear=0.0), seed=4), cutout=True)
    ST.part_block("radar_mast", lattice(M.galvanized(tone=0.6), step=3, seed=5), cutout=True)
    shaped_part("radar_dish", radar_dish_block, radar_dish_icon())
    shaped_part("antenna", antenna_block, antenna_icon())
    large_dish()
    ST.controller("launch_silo", ST.controller_front(missile_placard), ST.controller_top(), ST.casing())
    ST.controller("launch_complex", ST.controller_front(complex_placard), ST.controller_top(), ST.casing())
    ST.part_block("flame_deflector", flame_deflector())
    ST.controller("launch_computer", ST.controller_front(radar_placard), ST.controller_top(), ST.casing())
    ST.controller("radar_array", ST.controller_front(radar_placard_big), ST.controller_top(), ST.casing())
    silo()
    launch_complex()
    computer()
    radar_array()
    print("launch written")


if __name__ == "__main__":
    main()
