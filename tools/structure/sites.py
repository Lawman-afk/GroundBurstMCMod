"""The world's Cold War sites (DESIGN.md §22, Phase 15): their templates, their structure and structure-set definitions,
and the biome tags they stand in.

Researched from the real places (DESIGN.md §22's notes): the Titan II complex (a silo reached by a cableway from a
blast lock with two blast doors, the launch control centre a short tunnel on, the crew's stair down from an access
portal; the 740-ton closure door on rails; work platforms round the missile; Atlas F silos left flooded), the
Diefenbunker (a curved blast-diffuser entrance, blast doors, decontamination showers, a situation room with wall maps,
a radio room, filters and generators, an escape hatch filled with gravel), DEW Line stations (a radome over a
"building train" of linked modules, a long-range antenna), Trinity and the Nevada Test Site (the shallow glassed
crater, the shot tower's four concrete footings, the obelisk, the "Jumbo" steel vessel, instrument towers and
earth-covered bunkers 10,000 yards out, Doom Town's blasted houses and bleachers), the Damascus Titan II explosion and
the broken-arrow crashes (a missile in pieces, its warhead thrown clear), and WIPP (rooms of drums stacked three high
under magnesium-oxide sacks, mined from salt, closed off with bulkheads; a hot cell behind lead).

    tools/.venv/Scripts/python.exe tools/structure/sites.py

Writes data/groundzero/structure/site/*.nbt, worldgen/structure/*.json, worldgen/structure_set/*.json and
tags/worldgen/biome/has_structure/*.json under src/main/resources.
"""
from __future__ import annotations

import json
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from build import AIR, STEP, Build, gz, mc  # noqa: E402
from gz import paths  # noqa: E402

DATA = paths.DATA
CONC, CONC_DARK, CONC_WHITE = "light_gray_concrete", "gray_concrete", "white_concrete"
RC, HAZ, STEEL, CASING = gz("reinforced_concrete"), gz("hazard_concrete"), gz("steel_plating"), gz("machine_casing")
LEAD, GLASS_R = gz("lead_block"), gz("reactor_glass")
RUST = ("brown_terracotta", "terracotta", "orange_terracotta")
DIRT_STAIN = ("light_gray_terracotta", "gray_concrete_powder")


# =====================================================================================================================
# 1. The abandoned missile silo (Titan II): three variants, each a surface hardstand over the complex below.
# =====================================================================================================================
SILO_X, SILO_Y, SILO_Z = 60, 36, 15
SHAFT = (7.5, 7.5)               # the shaft's axis in the complex's template
SHAFT_IN, SHAFT_OUT = 4.4, 6.4
TUNNEL_Y = 12                    # the cableways' floor
LOCK = (28, 36)                  # the blast lock's x range
LCC = (45, 58)                   # the launch control centre's x range
PORTAL = (30, 32)                # the access portal's ladder shaft, x range (z 1..3)


def missile_column(b: Build, cx: int, cz: int, y0: int, y1: int, block: str, broken: float = 0.0) -> None:
    """A missile's body standing: a plus of five columns (it reads round at this size)."""
    for y in range(y0, y1 + 1):
        for dx, dz in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
            if b.rng.random() >= broken:
                b.set(cx + dx, y, cz + dz, block)


def silo_below(variant: str) -> Build:
    b = Build(SILO_X, SILO_Y, SILO_Z, seed={"intact": 11, "flooded": 12, "collapsed": 13}[variant])
    cx, cz = SHAFT
    top = SILO_Y - 1
    # The shaft: two blocks of reinforced concrete round a well nine across, from its floor to the closure door above.
    b.cylinder(cx, cz, SHAFT_OUT, 1, top, RC, inner=SHAFT_IN)
    b.cylinder(cx, cz, SHAFT_IN, 3, top, AIR)
    b.disc(cx, cz, SHAFT_OUT, 0, RC)
    b.disc(cx, cz, SHAFT_OUT, 1, RC)
    b.disc(cx, cz, SHAFT_IN, 2, HAZ)
    # The flame pit under the missile: a grate over the exhaust duct, scorched.
    b.fill(6, 2, 6, 8, 2, 8, "iron_bars")
    b.fill(6, 1, 6, 8, 1, 8, AIR)
    b.fill(6, 0, 6, 8, 0, 8, "blackstone")
    # Work platforms round the missile, folded back: three partial rings of steel grating with chains hanging.
    for y in (10, 17, 24):
        for x in range(2, 13):
            for z in range(2, 13):
                d = math.hypot(x + 0.5 - cx, z + 0.5 - cz)
                ang = math.atan2(z + 0.5 - cz, x + 0.5 - cx)
                if 2.6 <= d <= SHAFT_IN and not (-0.9 < ang < 0.5):
                    b.set(x, y, z, STEEL)
        for x, z in ((4, 4), (10, 10), (4, 10)):
            for k in range(1, 4):
                b.set(x, y - k, z, "iron_chain", {"axis": "y", "waterlogged": "false"})
    # A ladder up the shaft's east wall from the cableway's door.
    b.ladder(11, 3, top - 1, 7, "west")
    # Lights on the wall (most long dead: the weathering takes them).
    for y in (6, 13, 20, 27, 32):
        for x, z in ((3, 7), (7, 3), (7, 11)):
            b.lamp(x - (1 if x == 3 else 0), y, z - (1 if z == 3 else 0) + (1 if z == 11 else 0))
    # The missile (a Titan II: no fins, two engines): white over its first stage, a dark band at the stage joint, the
    # re-entry vehicle dark grey on top; its warhead section broken open (the damaged warhead lies at its foot).
    mx, mz = 7, 7
    if variant != "collapsed":
        b.set(6, 3, 7, "cauldron")
        b.set(8, 3, 7, "cauldron")
        missile_column(b, mx, mz, 4, 20, CONC_WHITE)
        missile_column(b, mx, mz, 21, 21, "black_concrete")
        missile_column(b, mx, mz, 22, 26, CONC_WHITE)
        missile_column(b, mx, mz, 27, 28, CONC_DARK, broken=0.35)
        b.set(mx, 29, mz, CONC_DARK)
        b.set(mx, 30, mz, "end_rod", {"facing": "up"})
        b.swap((5, 4, 5, 9, 26, 9), CONC_WHITE, lambda: b.rng.choice(("light_gray_concrete", "white_terracotta")), 0.12)
        b.stains((5, 4, 5, 9, 26, 9), [CONC_WHITE, "white_terracotta"], RUST, 0.03, run=3)
    else:
        # Toppled when the door came down: the first stage lies broken across the floor, its upper stage in pieces.
        for x in range(3, 12):
            for dy, dz in ((0, 0), (1, 0), (0, 1), (0, -1), (-1, 0)):
                if b.rng.random() > 0.15:
                    b.set(x, 4 + dy, 7 + dz, CONC_WHITE if x != 8 else "black_concrete")
        b.set(3, 4, 7, "cauldron")
        for x, z in ((5, 4), (9, 10), (10, 5), (4, 9)):
            b.set(x, 3, z, b.rng.choice((CONC_WHITE, CONC_DARK, "iron_block")))
    # The foot of the shaft: what fell, and the warhead.
    b.chest(4, 3, 9, "east", "silo_shaft")
    b.rubble((3, 3, 3, 11, 3, 11), 0.08, ("iron_bars", "iron_chain", "gravel", "cobblestone"))
    # The cableway to the blast lock: a steel tube three across, cable trays along its wall, lamps every six.
    def tube(x0: int, x1: int) -> None:
        b.fill(x0, TUNNEL_Y, 5, x1, TUNNEL_Y + 4, 9, STEEL)
        b.clear(x0, TUNNEL_Y + 1, 6, x1, TUNNEL_Y + 3, 8)
        b.fill(x0, TUNNEL_Y, 6, x1, TUNNEL_Y, 8, "iron_block")
        for x in range(x0, x1 + 1):
            b.set(x, TUNNEL_Y + 3, 6, gz("insulated_cable"))
            if (x - x0) % 6 == 3:
                b.lantern(x, TUNNEL_Y + 3, 7)
    tube(12, LOCK[0] - 1)
    b.clear(11, TUNNEL_Y + 1, 6, 13, TUNNEL_Y + 3, 8)            # through the shaft wall
    b.ladder(11, TUNNEL_Y + 1, TUNNEL_Y + 3, 7, "west")
    # The blast lock: reinforced concrete, a blast door at each end, the portal's ladder down from the surface.
    lx0, lx1 = LOCK
    b.room(lx0, TUNNEL_Y - 1, 3, lx1, TUNNEL_Y + 5, 11, RC, floor=CONC_DARK)
    b.door(lx0, TUNNEL_Y + 1, 7, "west")
    b.door(lx1, TUNNEL_Y + 1, 7, "east")
    b.fill(lx0, TUNNEL_Y + 3, 6, lx0, TUNNEL_Y + 4, 8, RC)
    b.fill(lx1, TUNNEL_Y + 3, 6, lx1, TUNNEL_Y + 4, 8, RC)
    for x in (lx0 + 2, lx1 - 2):
        b.lamp(x, TUNNEL_Y + 5, 7)
    b.set(lx0 + 1, TUNNEL_Y + 2, 4, "lever", {"face": "wall", "facing": "south", "powered": "false"})
    b.set(lx1 - 1, TUNNEL_Y + 2, 10, "lever", {"face": "wall", "facing": "north", "powered": "false"})
    px0, px1 = PORTAL
    b.walls(px0 - 1, TUNNEL_Y + 1, 0, px1 + 1, top, 4, RC)
    b.clear(px0, TUNNEL_Y + 1, 1, px1, top, 3)
    b.clear(px0, TUNNEL_Y + 1, 3, px1, TUNNEL_Y + 3, 4)          # into the lock
    b.ladder(px0 + 1, TUNNEL_Y + 1, top, 1, "south")
    tube(lx1 + 1, LCC[0] - 1)
    # The launch control centre: two floors in a reinforced box with its corners cut, launch control below, the crew
    # above, a ladder between.
    cx0, cx1 = LCC
    for y in range(TUNNEL_Y - 1, TUNNEL_Y + 11):
        for x in range(cx0, cx1 + 1):
            for z in range(1, 14):
                corner = min(x - cx0, cx1 - x) + min(z - 1, 13 - z) < 2
                if corner:
                    continue
                edge = x in (cx0, cx1) or z in (1, 13) or min(x - cx0, cx1 - x) + min(z - 1, 13 - z) == 2
                if y in (TUNNEL_Y - 1, TUNNEL_Y + 10) or edge:
                    b.set(x, y, z, RC)
                else:
                    b.set(x, y, z, AIR)
    upper = TUNNEL_Y + 5
    for x in range(cx0 + 1, cx1):
        for z in range(2, 13):
            if b.name(x, upper, z) == AIR:
                b.set(x, upper, z, "spruce_planks" if (x + z) % 5 else "dark_oak_planks")
            if b.name(x, TUNNEL_Y, z) not in (RC,):
                b.set(x, TUNNEL_Y, z, CONC_DARK if (x + z) % 2 else "gray_terracotta")
    b.clear(cx0, TUNNEL_Y + 1, 6, cx0, TUNNEL_Y + 3, 8)
    b.door(cx0, TUNNEL_Y + 1, 7, "west")
    b.fill(cx0, TUNNEL_Y + 3, 6, cx0, TUNNEL_Y + 3, 8, RC)
    b.ladder(cx1 - 2, TUNNEL_Y + 1, upper + 1, 3, "south")
    b.set(cx1 - 2, upper, 3, AIR)
    # Launch control: consoles along the walls (casings, dead screens, keys and switches), the two launch-key panels
    # far apart, the status board on the east wall, the commander's desk.
    for x in range(cx0 + 3, cx1 - 2):
        b.set(x, TUNNEL_Y + 1, 2, CASING)
        b.set(x, TUNNEL_Y + 2, 2, "black_stained_glass" if x % 2 else CASING)
        if x % 2 == 0:
            b.set(x, TUNNEL_Y + 3, 2, "stone_button", {"face": "floor", "facing": "south", "powered": "false"})
    for x in (cx0 + 3, cx1 - 3):
        b.set(x, TUNNEL_Y + 1, 12, CASING)
        b.set(x, TUNNEL_Y + 2, 12, "tripwire_hook", {"facing": "north", "attached": "false", "powered": "false"})
        b.set(x, TUNNEL_Y + 2, 11, AIR)
    for z in range(4, 11):
        b.set(cx1 - 1, TUNNEL_Y + 2, z, "black_concrete")
        b.set(cx1 - 1, TUNNEL_Y + 3, z, "black_concrete")
        b.set(cx1 - 1, TUNNEL_Y + 2 + (z % 2), z, ("lime_concrete", "red_concrete", "yellow_concrete")[z % 3])
    b.set(cx0 + 6, TUNNEL_Y + 1, 7, "spruce_slab", {"type": "top", "waterlogged": "false"})
    b.set(cx0 + 7, TUNNEL_Y + 1, 7, "spruce_slab", {"type": "top", "waterlogged": "false"})
    b.stairs(cx0 + 6, TUNNEL_Y + 1, 8, "spruce_stairs", "north")
    b.chest(cx0 + 5, TUNNEL_Y + 1, 11, "north", "silo_control")
    for x, z in ((cx0 + 4, 5), (cx0 + 8, 9), (cx1 - 4, 7)):
        b.lamp(x, TUNNEL_Y + 4, z)
    # The crew's floor: bunks, lockers, a galley and a table.
    for k, z in enumerate((3, 6, 9)):
        b.bed(cx0 + 2, upper + 1, z, "west", ("gray", "green", "gray")[k])
    for z in (3, 6, 9):
        b.barrel(cx0 + 1, upper + 2, z, "east")
    b.set(cx1 - 1, upper + 1, 9, "smoker", {"facing": "west", "lit": "false"})
    b.set(cx1 - 1, upper + 1, 10, "cauldron")
    b.chest(cx1 - 1, upper + 1, 11, "west", "bunker_stores")
    b.set(cx0 + 7, upper + 1, 7, "spruce_fence")
    b.set(cx0 + 7, upper + 2, 7, "spruce_pressure_plate", {"powered": "false"})
    b.stairs(cx0 + 6, upper + 1, 7, "spruce_stairs", "east")
    b.stairs(cx0 + 8, upper + 1, 7, "spruce_stairs", "west")
    b.lamp(cx0 + 6, upper + 4, 5)
    b.lamp(cx0 + 6, upper + 4, 10)
    # Wear: cracks and stains, cobwebs, rubble, a cleared emergency escape tube up from the crew floor.
    b.swap((0, 0, 0, SILO_X - 1, top, SILO_Z - 1), RC, lambda: b.rng.choice(("cracked_stone_bricks", RC, "andesite")), 0.04)
    b.stains((0, 0, 0, SILO_X - 1, top, SILO_Z - 1), [RC, CONC_DARK], ("gray_terracotta", "cyan_terracotta"), 0.015, run=4)
    b.cobwebs((0, 0, 0, SILO_X - 1, top, SILO_Z - 1), 0.25)
    b.rubble((12, TUNNEL_Y + 1, 5, cx1, TUNNEL_Y + 1, 9), 0.05, ("gravel", "cobblestone_slab", "iron_bars"))
    esc = (cx1 - 3, 11)
    b.fill(esc[0] - 1, upper + 1, esc[1] - 1, esc[0] + 1, top, esc[1] + 1, RC)
    b.fill(esc[0], upper + 5, esc[1], esc[0], top, esc[1], "gravel")    # filled with gravel, as the escape tubes were
    b.ladder(esc[0], upper + 1, upper + 4, esc[1], "north")
    b.set(esc[0], upper + 1, esc[1] - 1, AIR)
    if variant == "flooded":
        b.flood((0, 0, 0, SILO_X - 1, top, SILO_Z - 1), TUNNEL_Y + 2)
        b.flood((1, 0, 1, 13, top, 13), top - 9)                         # the shaft holds water higher
        for x, y, z in ((3, 20, 6), (11, 22, 9), (6, 18, 3)):
            b.set(x, y, z, "glow_lichen", {"west": "true", "east": "false", "north": "false", "south": "false", "up": "false",
                                            "down": "false", "waterlogged": "true"})
    if variant == "collapsed":
        # The closure door fell in: rubble heaped in the upper shaft, steel from the door on it.
        for y in range(top - 12, top + 1):
            r = SHAFT_IN * (0.3 + 0.7 * (top - y) / 12)
            for x in range(2, 13):
                for z in range(2, 13):
                    d = math.hypot(x + 0.5 - cx, z + 0.5 - cz)
                    if d <= SHAFT_IN and (d > r or y < top - 9) and b.rng.random() < 0.92:
                        b.set(x, y, z, b.rng.choice(("gravel", "cobblestone", "dirt", "andesite", RC, STEEL)))
        b.rubble((12, TUNNEL_Y + 1, 5, LOCK[0] - 1, TUNNEL_Y + 2, 9), 0.3, ("gravel", "cobblestone", "dirt"))
    return b


def silo_surface(variant: str) -> Build:
    """The hardstand over the complex: the closure door on its rails, the access portal, vents, an antenna, the
    floodlight poles and the chain-link fence (fallen in places), the guard shack, the ground cracked and grown over."""
    b = Build(SILO_X, 9, 21, seed={"intact": 21, "flooded": 22, "collapsed": 23}[variant])
    # The pad (the grade layer, y 0): gravel hardstand, a concrete apron round the door.
    for x in range(0, SILO_X):
        for z in range(0, 21):
            edge = x in (0, SILO_X - 1) or z in (0, 20)
            if not edge and b.rng.random() < 0.94:
                b.set(x, 0, z, "gravel" if b.rng.random() < 0.7 else ("coarse_dirt" if b.rng.random() < 0.5 else "andesite"))
    cx, cz = 7.5, 10.5
    b.disc(cx, cz, 8.5, 0, CONC)
    b.disc(cx, cz, SHAFT_OUT + 0.2, 0, HAZ, inner=SHAFT_IN + 0.5)
    # The closure door: two leaves of steel, closed, rails running out east for it to roll on.
    door_open = variant == "collapsed"
    for x in range(2, 14):
        for z in range(5, 17):
            if math.hypot(x + 0.5 - cx, z + 0.5 - cz) <= SHAFT_IN + 0.6:
                if door_open:
                    b.set(x, 0, z, AIR)
                else:
                    b.set(x, 0, z, STEEL if z != 10 else "black_concrete")
                    b.set(x, 1, z, STEEL if 3 <= x <= 12 and z != 10 and b.rng.random() < 0.9 else AIR)
    for x in range(14, 27):
        for z in (6, 14):
            b.set(x, 0, z, "iron_block")
            b.set(x, 1, z, "rail", {"shape": "east_west", "waterlogged": "false"})
    if door_open:
        # The door went in; the lip of the hole crumbled and sank.
        for x in range(0, 16):
            for z in range(2, 19):
                d = math.hypot(x + 0.5 - cx, z + 0.5 - cz)
                if SHAFT_IN + 0.6 < d < SHAFT_IN + 2.6 and b.rng.random() < 0.5:
                    b.set(x, 0, z, b.rng.choice(("gravel", "coarse_dirt", "dirt")))
    # Vents round the door.
    for x, z in ((1, 3), (14, 3), (14, 18)):
        b.fill(x, 1, z, x, 2, z, CONC)
        b.set(x, 3, z, "iron_bars")
    # The access portal over the ladder shaft: a concrete blockhouse, its door to the road.
    px0, px1 = PORTAL
    b.room(px0 - 2, 0, 3, px1 + 2, 4, 9, RC, floor=CONC_DARK)
    b.clear(px0, 0, 4, px1, 0, 6)                                     # the shaft's mouth
    b.door(px0 + 1, 1, 9, "south")
    b.set(px0 + 1, 3, 9, RC)
    b.set(px0 - 2, 2, 6, "glass_pane")
    b.set(px1 + 2, 2, 6, "glass_pane")
    b.lantern(px0 + 1, 3, 7)
    b.fill(px0 - 2, 5, 3, px1 + 2, 5, 9, "smooth_stone_slab", {"type": "bottom", "waterlogged": "false"})
    # The emergency escape hatch over the crew floor.
    b.fill(LCC[1] - 4, 0, 13, LCC[1] - 2, 1, 15, RC)
    b.trapdoor(LCC[1] - 3, 2, 14, "iron_trapdoor", "north")
    # An antenna mast (rusting) and floodlight poles.
    for y in range(1, 9):
        b.set(40, y, 2, "iron_bars")
    b.set(40, 8, 2, "lightning_rod", {"facing": "up", "powered": "false", "waterlogged": "false"})
    for x, z in ((18, 2), (18, 18), (44, 18), (55, 2)):
        for y in range(1, 5):
            b.set(x, y, z, "iron_bars")
        b.lantern(x, 5, z, hanging=False)
    # The guard shack by the gate: spruce, its roof half gone, its window out.
    b.room(47, 0, 3, 52, 4, 8, "spruce_planks", floor="spruce_planks", roof="spruce_planks")
    b.door(49, 1, 8, "south", "spruce_door")
    b.set(52, 2, 5, "glass_pane")
    b.clear(48, 4, 3, 52, 4, 6)
    b.chest(48, 1, 4, "east", "bunker_stores")
    # The chain-link fence round it all, fallen in places, a gate gap on the south.
    for x in range(0, SILO_X):
        for z in (0, 20):
            if b.rng.random() < 0.8 and not (z == 20 and 26 <= x <= 30):
                b.fill(x, 1, z, x, 2, z, "iron_bars")
                if x % 4 == 0:
                    b.set(x, 3, z, "iron_bars")
    for z in range(0, 21):
        for x in (0, SILO_X - 1):
            if b.rng.random() < 0.8:
                b.fill(x, 1, z, x, 2, z, "iron_bars")
    # Grown over: grass and moss in the gravel, the pad cracked.
    b.moss((0, 0, 0, SILO_X - 1, 0, 20), 0.12, ["gravel", "coarse_dirt", CONC])
    b.swap((0, 0, 0, SILO_X - 1, 0, 20), CONC, lambda: b.rng.choice(("cracked_stone_bricks", "andesite", "light_gray_concrete_powder")), 0.1)
    return b


# =====================================================================================================================
# 2. The command bunker (the Diefenbunker and its kind): a blockhouse on the surface, the bunker under it.
# =====================================================================================================================
def bunker_below() -> Build:
    b = Build(33, 15, 30, seed=31)
    F, top = 1, 14                                   # the bunker's floor; its stair rises to the template's top (grade - 1)
    H = F + 5                                        # its roof
    # Shell: reinforced concrete all round, the rooms carved out of it.
    b.fill(0, 0, 4, 32, H, 29, RC)
    # The way down from the blockhouse: a ladder well at the north edge, rising to the template's top.
    sx = 16
    b.fill(sx - 2, F, 0, sx + 2, top, 4, RC)
    b.clear(sx - 1, F + 1, 1, sx + 1, top, 3)
    b.ladder(sx, F + 1, top, 1, "south")
    b.clear(sx - 1, F + 1, 3, sx + 1, F + 3, 5)
    # The blast doors and the decontamination chamber: two doors, showers, lockers, suits on stands.
    b.clear(13, F + 1, 5, 19, F + 3, 10)
    b.door(16, F + 1, 5, "south")
    b.door(16, F + 1, 10, "south")
    b.fill(15, F + 3, 5, 17, F + 3, 5, RC)
    b.fill(15, F + 3, 10, 17, F + 3, 10, RC)
    for x in (14, 18):
        b.trapdoor(x, F, 7, "iron_trapdoor", "north", half="top")
        b.set(x, F + 3, 7, "lightning_rod", {"facing": "down", "powered": "false", "waterlogged": "false"})
    for z in (6, 8, 9):
        b.barrel(13, F + 1, z, "east") if z != 8 else b.marker(13, F + 1, z, "stand:hazmat")
    b.marker(19, F + 1, 8, "stand:hazmat")
    b.barrel(19, F + 1, 6, "west", "bunker_stores")
    b.lamp(16, F + 4, 7)
    # The main corridor south, lit, a yellow line along its floor.
    b.clear(15, F + 1, 11, 17, F + 3, 28)
    for z in range(11, 29):
        b.set(16, F, z, "yellow_concrete" if z % 2 else CONC_DARK)
        if z % 5 == 1:
            b.lamp(16, F + 4, z)
    # West: the situation room (tall, the wall map), then the bunk room.
    b.clear(1, F + 1, 11, 13, F + 4, 20)
    b.clear(14, F + 1, 14, 14, F + 2, 15)
    b.door(14, F + 1, 14, "west", "iron_door")
    # The wall map: a continent in concrete on the west wall, sea round it, red marks where the strikes would fall.
    land = {(z, y) for z in range(12, 20) for y in range(F + 1, F + 5)
            if (z - 15.5) ** 2 / 14 + (y - F - 2.5) ** 2 / 3.2 < 1 or (z - 18) ** 2 / 4 + (y - F - 1.2) ** 2 / 1.5 < 1}
    for z in range(11, 21):
        for y in range(F + 1, F + 5):
            b.set(0, y, z, "lime_terracotta" if (z, y) in land else "blue_concrete")
    for z, y in ((14, F + 3), (17, F + 2), (13, F + 2)):
        b.set(0, y, z, "red_concrete")                                      # where the strikes would fall
    # The plotting table in the middle: the map in carpets, markers on it.
    for x in range(4, 10):
        for z in range(13, 18):
            b.set(x, F + 1, z, "spruce_planks")
            b.set(x, F + 2, z, "green_carpet" if (x - 7) ** 2 / 5 + (z - 15) ** 2 / 3 < 1 else "blue_carpet")
    for x, z in ((6, 14), (8, 16), (5, 16)):
        b.set(x, F + 2, z, "red_carpet")                                    # markers on the plot
    for x in range(4, 10):
        b.stairs(x, F + 1, 12, "spruce_stairs", "south")
        b.stairs(x, F + 1, 18, "spruce_stairs", "north")
    for z in (11, 20):
        for x in range(2, 13, 3):
            b.barrel(x, F + 1, z, "up")
    b.lamp(7, F + 5, 15)
    b.set(7, H, 15, RC)
    # The bunks.
    b.clear(1, F + 1, 22, 13, F + 3, 28)
    b.clear(14, F + 1, 24, 14, F + 2, 24)
    b.door(14, F + 1, 24, "west", "iron_door")
    for i, x in enumerate(range(2, 13, 3)):
        b.bed(x, F + 1, 23, "south", ("gray", "green")[i % 2])
        b.bed(x, F + 1, 27, "north", ("gray", "green")[(i + 1) % 2])
    for x in range(2, 13, 3):
        b.set(x + 1, F + 1, 25, "barrel", {"facing": "up", "open": "false"})
    b.chest(12, F + 1, 25, "west", "bunker_stores")
    b.lamp(7, F + 4, 25)
    # The escape hatch: a ladder tube up from the bunk room, filled with gravel near the top.
    b.fill(1, F + 1, 28, 3, top, 29, RC)
    b.ladder(2, F + 1, top - 4, 28, "north")
    b.fill(2, top - 3, 28, 2, top, 28, "gravel")
    b.set(2, F + 1, 27, AIR)
    # East: the radio room, the armoury, the plant room (generator and filters).
    b.clear(19, F + 1, 11, 31, F + 3, 15)
    b.clear(18, F + 1, 13, 18, F + 2, 13)
    b.door(18, F + 1, 13, "east", "iron_door")
    for x in range(20, 31):
        b.set(x, F + 1, 11, CASING)
        b.set(x, F + 2, 11, "black_stained_glass" if x % 3 else "note_block")
    b.set(30, F + 1, 13, "jukebox", {"has_record": "false"})
    b.set(26, F + 1, 14, "spruce_slab", {"type": "top", "waterlogged": "false"})
    b.stairs(26, F + 1, 15, "spruce_stairs", "north")
    b.set(24, F + 1, 11, CASING)
    b.chest(21, F + 1, 15, "north", "bunker_stores")
    for y in range(F + 4, top + 1):
        b.set(28, y, 13, "iron_chain", {"axis": "y", "waterlogged": "false"})     # the antenna lead up to the mast
    b.lamp(25, F + 4, 13)
    b.clear(19, F + 1, 17, 31, F + 3, 21)
    b.clear(18, F + 1, 19, 18, F + 2, 19)
    b.door(18, F + 1, 19, "east", "iron_door")
    for x in range(20, 31, 2):
        b.chest(x, F + 1, 21, "north", "bunker_armory") if x in (22, 28) else b.barrel(x, F + 1, 21, "north")
        b.set(x, F + 1, 17, "spruce_trapdoor", {"facing": "south", "half": "top", "open": "false", "powered": "false",
                                               "waterlogged": "false"})
    b.marker(30, F + 1, 19, "stand:bare")
    b.lamp(25, F + 4, 19)
    b.clear(19, F + 1, 23, 31, F + 3, 28)
    b.clear(18, F + 1, 25, 18, F + 2, 25)
    b.door(18, F + 1, 25, "east", "iron_door")
    b.fill(26, F + 1, 24, 29, F + 2, 26, "iron_block")                      # the diesel's block
    b.fill(27, F + 3, 25, 28, F + 3, 25, "iron_bars")
    for z in (24, 26):
        b.fill(21, F + 1, z, 22, F + 3, z, "iron_block")                    # filter banks
        b.set(23, F + 2, z, "iron_bars")
    for y in range(F + 4, top + 1):
        b.set(24, y, 27, "iron_bars")                                       # the air intake up to its stack
    b.barrel(30, F + 1, 28, "west")
    b.barrel(30, F + 1, 27, "west")
    b.lamp(25, F + 4, 25)
    # Stores at the corridor's end.
    for x in (15, 17):
        b.barrel(x, F + 1, 28, "north", "bunker_stores" if x == 15 else None)
    # Wear.
    b.stains((0, 0, 4, 32, H, 29), [RC], ("gray_terracotta", "cyan_terracotta"), 0.01, run=3)
    b.cobwebs((0, 0, 0, 32, H, 29), 0.3)
    b.rubble((1, F + 1, 5, 31, F + 1, 28), 0.02, ("gravel", "cobblestone_slab"))
    return b


def bunker_surface() -> Build:
    """The blockhouse over the bunker's stair: its entrance turned twice (a blast diffuser), sandbags, the two air
    stacks, the radio mast, the escape hatch's plug of gravel under a slab."""
    b = Build(33, 10, 30, seed=32)
    # The blockhouse over the stair (x 12..20, z 0..8): the stair well at (15..17, 1..3).
    b.room(12, 0, -1, 20, 5, 7, RC, floor=CONC_DARK)
    b.clear(15, 0, 1, 17, 0, 3)
    b.set(16, 0, 1, AIR)
    # The diffuser: the door is round a corner and round another.
    b.clear(13, 1, 4, 19, 3, 6)
    b.fill(13, 1, 4, 18, 3, 4, RC)
    b.clear(18, 1, 7, 19, 3, 7)
    b.door(19, 1, 7, "south", "iron_door")
    b.set(18, 1, 7, RC)
    b.set(18, 2, 7, RC)
    b.lantern(16, 4, 2)
    b.fill(12, 6, -1, 20, 6, 7, "smooth_stone_slab", {"type": "bottom", "waterlogged": "false"})
    # Sandbags (packed mud) round the door.
    for x in range(20, 25):
        b.set(x, 1, 9, "packed_mud")
        b.slab(x, 2, 9, "mud_brick_slab")
    for z in range(9, 13):
        b.set(24, 1, z, "packed_mud")
    # Air stacks over the plant room, the radio mast over the radio room, the escape hatch.
    for x, z in ((24, 27), (21, 25)):
        b.fill(x, 0, z, x, 3, z, RC)
        b.set(x, 4, z, "iron_bars")
    for y in range(0, 10):
        b.set(28, y, 13, "iron_bars" if y else RC)
    b.set(28, 9, 13, "lightning_rod", {"facing": "up", "powered": "false", "waterlogged": "false"})
    for x, z in ((27, 12), (29, 14), (27, 14), (29, 12)):
        b.set(x, 3, z, "iron_chain", {"axis": "y", "waterlogged": "false"})
    b.fill(1, 0, 28, 3, 0, 29, RC)
    b.slab(2, 1, 28, "smooth_stone_slab")
    b.moss((0, 0, 0, 32, 2, 29), 0.15, [RC, CONC_DARK, "packed_mud"])
    return b


# =====================================================================================================================
# 3. The radar outpost (a DEW Line station): a radome over a building train, the long-range dish toppled.
# =====================================================================================================================
def dome(b: Build, cx: float, cy: float, cz: float, r: float, shell: str, panel: str, broken: float = 0.0,
         gap: tuple[float, float] | None = None) -> None:
    """A hemispherical shell (a radome): its panels in two materials laid out irregularly (later radomes spaced their
    panels at random so they would not interfere with the radar); a stretch of it fallen in {gap} (an angle range)."""
    for x in range(math.floor(cx - r - 1), math.ceil(cx + r + 1) + 1):
        for z in range(math.floor(cz - r - 1), math.ceil(cz + r + 1) + 1):
            for y in range(int(cy), int(cy + r + 1)):
                d = math.sqrt((x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 + (z + 0.5 - cz) ** 2)
                if r - 1 < d <= r:
                    ang = math.atan2(z + 0.5 - cz, x + 0.5 - cx)
                    if gap and gap[0] < ang < gap[1] and y > cy + 1:
                        continue
                    if b.rng.random() < broken:
                        continue
                    b.set(x, y, z, panel if b.rng.random() < 0.3 else shell)


def dish(b: Build, cx: float, cy: float, cz: float, r: float, tilt: float, yaw: float, block: str, rim: str) -> None:
    """A parabolic dish of radius {r}, tilted {tilt} radians from facing up, turned {yaw}: its shell one block thick."""
    focal = r * 0.55
    ct, st, cy_, sy = math.cos(tilt), math.sin(tilt), math.cos(yaw), math.sin(yaw)
    steps = int(r * 10)
    for i in range(steps + 1):
        rho = r * i / steps
        for j in range(int(2 * math.pi * rho * 3) + 1):
            a = 2 * math.pi * j / max(1, int(2 * math.pi * rho * 3))
            u, v = rho * math.cos(a), rho * math.sin(a)
            w = rho * rho / (4 * focal)
            # tilt about x, then turn about y
            y1, w1 = v * ct - w * st, v * st + w * ct
            x2, z2 = u * cy_ - w1 * sy, u * sy + w1 * cy_
            b.set(round(cx + x2 - 0.5), round(cy + y1 - 0.5), round(cz + z2 - 0.5), rim if rho > r - 0.8 else block)


def radar_outpost() -> Build:
    b = Build(35, 21, 30, seed=41)
    G = 0                                            # grade
    # The building train: three linked modules along z 16..24, white-clad steel, a corridor through them.
    mods = ((2, 11, 8), (12, 19, 4), (20, 26, 5))   # (x0, x1, height)
    for x0, x1, h in mods:
        b.room(x0, G, 16, x1, G + h, 24, CONC_WHITE, floor=CONC_DARK, roof=CONC)
        b.fill(x0, G + 1, 16, x1, G + 1, 16, CONC)                          # a grey plinth course
        b.fill(x0, G + 1, 24, x1, G + 1, 24, CONC)
        for x in range(x0 + 2, x1 - 1, 3):
            b.set(x, G + 2, 16, "glass_pane")
            b.set(x, G + 2, 24, "glass_pane")
    for x0, x1, h in mods:                                                 # the corridor through the train
        b.clear(x0, G + 1, 19, x1, G + 3, 21)
    for x in (2, 26):
        b.fill(x, G + 1, 19, x, G + 3, 21, CONC_WHITE)
    b.door(26, G + 1, 20, "east", "iron_door")
    b.fill(2, G + 4, 17, 11, G + 4, 23, CONC_DARK)                         # the radar module's upper floor
    b.clear(3, G + 5, 17, 10, G + 7, 23)
    b.ladder(3, G + 1, G + 5, 22, "east")
    b.set(3, G + 4, 22, AIR)
    for x in range(4, 10):
        b.set(x, G + 5, 17, CASING)
        b.set(x, G + 6, 17, "black_stained_glass" if x % 2 else CASING)
    b.chest(9, G + 5, 23, "north", "radar_outpost")
    # The radome on the radar module's roof, a stretch fallen in, the antenna inside on its pedestal.
    dome(b, 6.5, G + 9, 20.0, 5.6, CONC_WHITE, "white_stained_glass", broken=0.04, gap=(0.3, 1.4))
    b.fill(5, G + 8, 19, 7, G + 9, 21, "iron_block")
    dish(b, 6.5, G + 12, 20.0, 3.2, 1.2, 0.4, "iron_block", "light_gray_concrete")
    # Quarters in the middle module: bunks, a table, a stove; the garage at the end with the generator.
    for z in (17, 23):
        b.bed(13, G + 1, z, "east", "gray")
        b.bed(16, G + 1, z, "east", "blue")
    b.set(18, G + 1, 17, "smoker", {"facing": "south", "lit": "false"})
    b.chest(18, G + 1, 23, "north", "bunker_stores")
    b.fill(21, G + 1, 17, 24, G + 2, 18, "iron_block")
    b.fill(22, G + 3, 17, 23, G + 3, 18, "iron_bars")
    b.barrel(25, G + 1, 23, "west", "radar_outpost")
    b.barrel(24, G + 1, 23, "up")
    for x0, x1, h in mods:
        b.lamp((x0 + x1) // 2, G + h - 1, 20)
    # Masts on the middle module's roof.
    for x, z in ((13, 18), (18, 22)):
        for y in range(G + 5, G + 11):
            b.set(x, y, z, "iron_bars")
        b.set(x, G + 11, z, "lightning_rod", {"facing": "up", "powered": "false", "waterlogged": "false"})
    # The long-range dish, toppled from its pedestal: the lattice stump, the dish lying tilted on the ground.
    for y in range(G + 1, G + 6):
        for x, z in ((26, 5), (29, 5), (26, 8), (29, 8)):
            if y < G + 4 or (x, z) != (29, 8):
                b.set(x, y, z, "iron_bars")
        if y % 2:
            for x in range(26, 30):
                b.set(x, y, 5, "iron_bars")
    b.fill(26, G, 5, 29, G, 8, CONC)
    dish(b, 15.0, G + 4.5, 7.0, 6.5, 1.05, -0.35, CONC_WHITE, "iron_block")
    # The watchtower: spruce legs, a platform, a rail and a roof, a ladder up one leg.
    for x, z in ((1, 1), (5, 1), (1, 5), (5, 5)):
        b.fill(x, G + 1, z, x, G + 8, z, "spruce_log", {"axis": "y"})
    b.fill(1, G + 8, 1, 5, G + 8, 5, "spruce_planks")
    for x in range(1, 6):
        for z in range(1, 6):
            if x in (1, 5) or z in (1, 5):
                b.set(x, G + 9, z, "spruce_fence")
            b.slab(x, G + 11, z, "spruce_slab")
    for x, z in ((1, 1), (5, 1), (1, 5), (5, 5)):
        b.fill(x, G + 9, z, x, G + 10, z, "spruce_fence")
    b.ladder(3, G + 1, G + 8, 6, "south")
    b.set(3, G + 8, 6, "spruce_planks")
    b.set(3, G + 8, 5, AIR)
    # The fence round it, fallen in places; gravel tracks.
    for x in range(0, 35):
        for z in (0, 29):
            if b.rng.random() < 0.7:
                b.fill(x, G + 1, z, x, G + 2, z, "iron_bars")
    for z in range(0, 30):
        for x in (0, 34):
            if b.rng.random() < 0.7:
                b.fill(x, G + 1, z, x, G + 2, z, "iron_bars")
    for x in range(8, 33):
        for z in (12, 13):
            if b.rng.random() < 0.85:
                b.set(x, G, z, "gravel")
    b.stains((0, 0, 0, 34, 20, 29), [CONC_WHITE], ("light_gray_concrete", "white_terracotta"), 0.04, run=3)
    b.cobwebs((0, 0, 0, 34, 20, 29), 0.2)
    return b


# =====================================================================================================================
# 4. The test site (Trinity, the Nevada Test Site): ground zero's glassed crater, Jumbo, the bunker 10,000 yards out.
# =====================================================================================================================
def test_site_zero() -> Build:
    b = Build(37, 15, 37, seed=51)
    G = 3                                            # grade
    c = 18.0
    # The crater: shallow and wide (Trinity's was 8 feet deep at the point, 800 feet across): sand fused green near
    # the middle, glassed sand round it, irradiated soil in the ring, ash in the hollows.
    for x in range(37):
        for z in range(37):
            d = math.hypot(x + 0.5 - c, z + 0.5 - c)
            if d > 16.5:
                continue
            depth = 0 if d > 14 else round(2.2 * (1 - (d / 14) ** 2))
            for y in range(G - depth + 1, G + 1):
                b.set(x, y, z, AIR)
            k = b.rng.random()
            if d < 5:
                top = "groundzero:trinitite" if k < 0.65 else "groundzero:glassed_sand"
            elif d < 10:
                top = "groundzero:glassed_sand" if k < 0.55 else ("groundzero:trinitite" if k < 0.75 else "sand")
            elif d < 14:
                top = "groundzero:irradiated_soil" if k < 0.35 else ("sand" if k < 0.8 else "groundzero:scorched_earth")
            else:
                top = "sand" if k < 0.85 else "groundzero:irradiated_soil"
            b.set(x, G - depth, z, top)
            b.set(x, G - depth - 1, z, "sandstone" if k < 0.5 else "sand")
    # The shot tower's four concrete footings, twisted steel sticking out of them.
    for dx, dz in ((-2, -2), (2, -2), (-2, 2), (2, 2)):
        x, z = int(c) + dx, int(c) + dz
        b.fill(x, G - 2, z, x, G - 1, z, RC)
        b.set(x, G, z, "iron_bars")
        b.set(x + (1 if dx > 0 else -1), G - 1, z, "iron_chain", {"axis": "x", "waterlogged": "false"})
    # The obelisk on its base, at ground zero's edge (the marker set there years after).
    ox, oz = int(c) + 4, int(c) - 1
    b.fill(ox - 1, G - 1, oz - 1, ox + 1, G - 1, oz + 1, "polished_basalt")
    b.fill(ox, G, oz, ox, G + 6, oz, "polished_blackstone_bricks")
    b.set(ox, G + 7, oz, "polished_blackstone_slab", {"type": "bottom", "waterlogged": "false"})
    b.set(ox + 1, G, oz, "polished_blackstone_button", {"face": "wall", "facing": "east", "powered": "false"})
    # The fence ring round ground zero, broken.
    for i in range(80):
        a = 2 * math.pi * i / 80
        x, z = round(c + 16.5 * math.cos(a) - 0.5), round(c + 16.5 * math.sin(a) - 0.5)
        if b.rng.random() < 0.7:
            b.fill(x, G + 1, z, x, G + 1, z, "iron_bars")
            if i % 5 == 0:
                b.set(x, G + 2, z, "iron_bars")
    # Jumbo: the great steel vessel meant to hold the blast, never used, lying on its side, rusted, one end blown off.
    jx0, jx1, jy, jz = 2, 15, G + 4, 30
    for x in range(jx0, jx1 + 1):
        for y in range(G, G + 9):
            for z in range(25, 35):
                d = math.hypot(y + 0.5 - jy - 0.5, z + 0.5 - jz - 0.5)
                if 3.0 < d <= 4.2 and not (x == jx1 and b.rng.random() < 0.6):
                    b.set(x, y, z, b.rng.choice(("iron_block", "iron_block", "brown_terracotta", "terracotta")))
                elif d <= 3.0 and x not in (jx0,):
                    b.set(x, y, z, AIR)
        if x in (jx0 + 3, jx1 - 3):                                  # its stiffening rings
            for y in range(G, G + 9):
                for z in range(25, 35):
                    if 4.2 < math.hypot(y + 0.5 - jy - 0.5, z + 0.5 - jz - 0.5) <= 4.9:
                        b.set(x, y, z, "iron_block")
    # Instrument towers: lattices of bars with a box on top.
    for tx, tz in ((3, 3), (33, 6)):
        for y in range(G + 1, G + 10):
            for dx, dz in ((0, 0), (1, 0), (0, 1), (1, 1)):
                if (y + dx + dz) % 3 or (dx, dz) == (0, 0):
                    b.set(tx + dx, y, tz + dz, "iron_bars")
        b.fill(tx, G + 10, tz, tx + 1, G + 10, tz + 1, CASING)
        b.set(tx, G + 11, tz, "lightning_rod", {"facing": "up", "powered": "false", "waterlogged": "false"})
    # Doom Town: a frame house half blown away (charred), and bleachers facing ground zero.
    hx, hz = 26, 26
    b.fill(hx, G, hz, hx + 6, G, hz + 5, "groundzero:charred_planks")
    for y in range(G + 1, G + 5):
        for x in range(hx, hx + 7):
            for z in range(hz, hz + 6):
                edge = x in (hx, hx + 6) or z in (hz, hz + 5)
                blown = x + y < hx + G + 5 or z < hz + 2 and y > G + 2
                if edge and not blown and b.rng.random() < 0.85:
                    b.set(x, y, z, "groundzero:charred_planks" if (x + z) % 4 else "groundzero:charred_log")
    for x in (hx + 6,):
        for z in range(hz, hz + 6):
            b.set(x, G + 5, z, "groundzero:charred_planks")
    for row in range(4):
        for x in range(22, 31):
            if b.rng.random() < 0.9:
                b.stairs(x, G + 1 + row // 2 * 0 + (row // 2), 3 + row, "spruce_stairs", "north")
    b.rubble((0, G + 1, 0, 36, G + 1, 36), 0.004, ("groundzero:charred_planks", "iron_bars", "dead_bush"))
    return b


def test_site_bunker() -> Build:
    """The observation bunker 10,000 yards out: concrete, banked with earth, a slit facing ground zero (north), its
    door on the far side, inside the instruments, the bench, a periscope through the roof."""
    b = Build(13, 8, 11, seed=52)
    G = 1
    b.fill(0, G - 1, 0, 12, G + 4, 10, "sand")                         # the berm
    for y in range(G + 3, G + 6):
        for x in range(13):
            for z in range(11):
                if min(x, 12 - x, z, 10 - z) < (y - G - 2):
                    b.set(x, y, z, AIR)
    b.room(2, G - 1, 2, 10, G + 3, 8, RC, floor=CONC_DARK)
    b.fill(4, G + 1, 2, 8, G + 1, 2, "glass_pane")                      # the slit, facing ground zero
    b.clear(4, G + 1, 0, 8, G + 1, 1)
    b.clear(5, G + 1, 8, 7, G + 2, 10)
    b.door(6, G + 1, 8, "south", "iron_door")
    b.set(5, G + 1, 8, RC)
    b.set(7, G + 1, 8, RC)
    b.set(5, G + 2, 8, RC)
    b.set(7, G + 2, 8, RC)
    for x in range(3, 10):
        b.set(x, G, 3, CASING)
    b.set(4, G + 1, 3, "lever", {"face": "floor", "facing": "north", "powered": "false"})
    b.set(8, G + 1, 3, "stone_button", {"face": "floor", "facing": "north", "powered": "false"})
    for x in range(3, 10):
        b.stairs(x, G, 5, "spruce_stairs", "north") if x % 3 else None
    b.chest(9, G, 7, "west", "test_site")
    b.set(3, G, 7, "barrel", {"facing": "up", "open": "false"})
    for y in range(G + 1, G + 6):
        b.set(6, y, 4, "iron_bars")
    b.set(6, G + 6, 4, "lightning_rod", {"facing": "up", "powered": "false", "waterlogged": "false"})
    b.lantern(6, G + 2, 6)
    b.cobwebs((2, G, 2, 10, G + 3, 8), 0.3)
    return b


# =====================================================================================================================
# 5. The crashed missile: an impact furrow, the missile in three pieces, its warhead broken and leaking.
# =====================================================================================================================
def lying_body(b: Build, x0: int, x1: int, y: int, z: int, block: str, band: int | None = None, broken: float = 0.0) -> None:
    for x in range(x0, x1 + 1):
        for dy, dz in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
            if b.rng.random() >= broken:
                b.set(x, y + dy, z + dz, "black_concrete" if band is not None and x == band else block)


def crashed_missile() -> Build:
    b = Build(38, 11, 17, seed=61)
    G = 5
    zc = 8
    # The furrow: ploughed in from the west, deepening to the impact at the east end; earth thrown up either side.
    for x in range(2, 34):
        t = (x - 2) / 31
        depth = round(3.2 * t ** 1.4)
        half = 1.5 + 1.8 * t
        for z in range(17):
            dz = abs(z + 0.5 - zc - 0.5)
            if dz <= half:
                floor = G - max(0, depth - (1 if dz > half - 1 else 0))
                for y in range(floor + 1, G + 1):
                    b.set(x, y, z, AIR)
                b.set(x, floor, z, b.rng.choice(("coarse_dirt", "dirt", "gravel", "groundzero:scorched_earth" if t > 0.7 else "dirt")))
            elif dz <= half + 2.2:
                h = round(1.6 * t * (1 - (dz - half) / 2.2) + 0.3)
                for y in range(G + 1, G + 1 + h):
                    b.set(x, y, z, b.rng.choice(("dirt", "coarse_dirt", "gravel")))
    # The impact crater at the east end, scorched, ash in it.
    for x in range(29, 37):
        for z in range(3, 14):
            d = math.hypot(x + 0.5 - 33, z + 0.5 - zc - 0.5)
            if d < 4.5:
                fl = G - 4 + round(d * 0.6)
                for y in range(fl + 1, G + 2):
                    b.set(x, y, z, AIR)
                b.set(x, fl, z, b.rng.choice(("groundzero:scorched_earth", "groundzero:scorched_earth", "coarse_dirt",
                                              "groundzero:irradiated_soil")))
                if b.rng.random() < 0.3:
                    b.set(x, fl + 1, z, "groundzero:fallout_ash", {"layers": str(b.rng.randint(1, 3))})
    # The warhead section at the crater's bottom, split open: its dirty payload spilled (a uranium core block, the
    # ground round it irradiated).
    b.fill(32, G - 3, zc - 1, 34, G - 1, zc + 1, CONC_DARK)
    b.clear(33, G - 2, zc, 34, G - 1, zc + 1)
    b.set(33, G - 3, zc, "groundzero:uranium_block")
    b.set(34, G - 3, zc + 1, "groundzero:irradiated_soil")
    b.set(32, G - 2, zc + 2, "groundzero:irradiated_soil")
    # The middle of the body lying in the furrow, its stage band; the tail with its engine bells, thrown aside.
    lying_body(b, 17, 28, G - 1, zc, CONC_WHITE, band=22, broken=0.08)
    b.clear(26, G, zc - 1, 28, G, zc + 1)                                 # torn open at the front
    b.chest(27, G - 1, zc, "west", "crashed_missile")
    lying_body(b, 7, 13, G + 1, zc + 3, CONC_WHITE, band=9, broken=0.1)
    b.set(6, G + 1, zc + 3, "cauldron")
    b.set(6, G + 1, zc + 2, "cauldron")
    b.swap((5, G, 0, 30, G + 3, 16), CONC_WHITE, lambda: b.rng.choice(("light_gray_concrete", "white_terracotta")), 0.15)
    b.stains((5, G, 0, 30, G + 3, 16), [CONC_WHITE], RUST + ("gray_concrete",), 0.05, run=2)
    # Debris along the furrow.
    for _ in range(26):
        x, z = b.rng.randint(4, 33), b.rng.randint(1, 15)
        for y in range(G + 3, 0, -1):
            if b.solid(x, y - 1, z) and b.name(x, y, z) in (None, AIR):
                b.set(x, y, z, b.rng.choice(("iron_bars", "iron_chain", CONC_WHITE, "iron_block", "glass_pane", "groundzero:steel_plating")))
                break
    return b


# =====================================================================================================================
# 6. The waste storage (WIPP): rooms mined from salt, drums three high under their sacks, decontamination, a hot cell.
# =====================================================================================================================
SALT = ("calcite", "calcite", "calcite", "diorite", "white_terracotta", "smooth_quartz")


def waste_storage() -> Build:
    b = Build(39, 9, 33, seed=71)
    F, C = 1, 6                                       # floor, ceiling
    # The salt: banded (halite with clay seams), with the rooms cut out of it.
    for y in range(0, 9):
        band = ("calcite", "diorite", "white_terracotta")[(y // 2) % 3]
        for x in range(39):
            for z in range(33):
                b.set(x, y, z, band if b.rng.random() < 0.55 else b.rng.choice(SALT))
    # The main drift east-west, rails down it, roof bolts (bars) and lamps.
    b.clear(0, F + 1, 14, 38, C - 1, 18)
    for x in range(0, 39):
        b.set(x, F + 1, 16, "rail", {"shape": "east_west", "waterlogged": "false"})
        if x % 4 == 0:
            b.set(x, C - 1, 14, "iron_bars")
            b.set(x, C - 1, 18, "iron_bars")
        if x % 8 == 4:
            b.lantern(x, C - 1, 16)
    # Rooms (panels) off it: north side z 2..12, south side z 20..30, seven across, three-wide pillars between.
    rooms_n = ((2, 8), (12, 18), (22, 28), (31, 37))
    rooms_s = ((2, 8), (12, 18), (22, 28), (31, 37))
    for x0, x1 in rooms_n:
        b.clear(x0, F + 1, 2, x1, C - 1, 13)
    for x0, x1 in rooms_s:
        b.clear(x0, F + 1, 19, x1, C - 1, 30)
    # Drum rooms: drums three high in rows, filled from the back, a white sack layer on top (magnesium oxide).
    def drums(x0: int, x1: int, z0: int, z1: int, gaps: float = 0.04) -> None:
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if (x - x0) % 3 == 2:
                    continue                                 # an aisle between rows of seven-packs
                for y in range(F + 1, F + 4):
                    if b.rng.random() > gaps:
                        b.set(x, y, z, gz("waste_drum"))
                if b.name(x, F + 3, z) == gz("waste_drum") and b.rng.random() < 0.8:
                    b.set(x, F + 4, z, "white_carpet")
    drums(2, 8, 2, 11)
    drums(12, 18, 2, 9)
    drums(22, 28, 20, 30, gaps=0.12)
    # One north room closed off by a bulkhead (its drums sealed in).
    b.fill(22, F + 1, 13, 28, C - 1, 13, RC)
    drums(22, 28, 2, 11, gaps=0.0)
    b.set(25, F + 2, 13, "gray_concrete")
    # The far north room collapsed (the salt creeping in): rubble over crushed drums.
    for x in range(31, 38):
        for z in range(2, 14):
            for y in range(F + 1, C):
                if b.rng.random() < 0.55 + 0.1 * (y - F):
                    b.set(x, y, z, b.rng.choice(SALT + ("gravel",)))
                elif y == F + 1 and b.rng.random() < 0.3:
                    b.set(x, y, z, gz("waste_drum"))
    # Decontamination at the west end of the south side: a shower, lockers, suits on stands, the waste store's chest.
    b.fill(2, F, 20, 8, F, 30, CONC)
    b.walls(2, F + 1, 20, 8, C - 1, 30, CONC)
    b.clear(4, F + 1, 20, 6, F + 3, 20)
    b.door(5, F + 1, 20, "north", "iron_door")
    b.set(4, F + 1, 20, CONC)
    b.set(6, F + 1, 20, CONC)
    b.set(4, F + 2, 20, CONC)
    b.set(6, F + 2, 20, CONC)
    for z in (23, 26):
        b.trapdoor(4, F, z, "iron_trapdoor", "north", half="top")
        b.set(4, C - 2, z, "lightning_rod", {"facing": "down", "powered": "false", "waterlogged": "false"})
    b.marker(7, F + 1, 22, "stand:hazmat")
    b.marker(7, F + 1, 24, "stand:hazmat")
    b.marker(7, F + 1, 26, "stand:lead")
    for z in (28, 29):
        b.barrel(3, F + 1, z, "east")
    b.chest(7, F + 1, 29, "west", "waste_storage")
    b.lamp(5, C - 1, 25)
    # The hot cell: lead-lined, a thick window into it, the manipulator's arms through the wall, a chest inside.
    b.fill(12, F, 19, 18, C - 1, 30, CONC)
    b.fill(13, F + 1, 20, 17, C - 2, 29, LEAD)
    b.clear(14, F + 1, 21, 16, C - 3, 28)
    b.fill(14, F + 2, 20, 16, F + 2, 20, GLASS_R)
    b.clear(14, F + 1, 19, 16, F + 3, 19)
    for x in (14, 16):
        b.set(x, F + 3, 20, "iron_bars")
    b.set(15, F + 1, 20, CASING)
    b.set(15, F + 2, 19, "lever", {"face": "wall", "facing": "north", "powered": "false"})
    b.clear(18, F + 1, 25, 18, F + 2, 25)
    b.clear(17, F + 1, 25, 17, F + 2, 25)
    b.door(17, F + 1, 25, "east", "iron_door")
    b.chest(14, F + 1, 27, "east", "waste_storage")
    b.set(16, F + 1, 22, gz("waste_drum"))
    # The shaft at the west end: the cage at its foot, sealed above by a concrete plug.
    b.clear(0, F + 1, 14, 3, C + 1, 18)
    b.fill(0, C + 1, 14, 3, 8, 18, RC)
    for y in range(F + 1, C + 1):
        for x, z in ((0, 15), (0, 17), (2, 15), (2, 17)):
            b.set(x, y, z, "iron_bars")
    b.set(1, F + 1, 16, "iron_block")
    b.cobwebs((0, 0, 0, 38, 8, 32), 0.15)
    b.rubble((0, F + 1, 0, 38, F + 1, 32), 0.02, ("calcite", "diorite", "gravel"))
    return b


# =====================================================================================================================
# 7. The exotic research station in the End: a deck on a floating rock, a glass dome over a broken containment ring,
# exotic crystals growing through the floor.
# =====================================================================================================================
def research_station() -> Build:
    b = Build(31, 24, 31, seed=81)
    c = 15.0
    D = 9                                            # the deck
    # The rock under it: an inverted cone of end stone, ragged.
    for y in range(0, D):
        r = 11.5 * ((y + 1) / D) ** 0.8
        for x in range(31):
            for z in range(31):
                d = math.hypot(x + 0.5 - c, z + 0.5 - c)
                if d <= r - b.rng.random() * 1.6:
                    b.set(x, y, z, "end_stone" if b.rng.random() < 0.9 else gz("voidstone"))
    # The deck: an octagon of steel plating over reinforced concrete, hazard bands at its edge.
    for x in range(31):
        for z in range(31):
            dx, dz = abs(x + 0.5 - c), abs(z + 0.5 - c)
            if max(dx, dz) <= 13 and dx + dz <= 18:
                edge = max(dx, dz) > 12 or dx + dz > 17
                b.set(x, D, z, HAZ if edge else (STEEL if (x + z) % 7 else RC))
    # The dome over the containment chamber.
    dome(b, c, D + 1, c, 7.4, GLASS_R, STEEL, broken=0.08, gap=(-2.6, -1.6))
    # The containment ring, broken: a ring of steel and casings round a scorched pit where the exotic matter got out.
    for i in range(24):
        a = 2 * math.pi * i / 24
        x, z = round(c + 4.5 * math.cos(a) - 0.5), round(c + 4.5 * math.sin(a) - 0.5)
        if not (0.4 < a < 1.3):
            b.set(x, D + 1, z, CASING if i % 3 == 0 else "iron_block")
            if i % 6 == 0:
                b.set(x, D + 2, z, "end_rod", {"facing": "up"})
    for x in range(11, 20):
        for z in range(11, 20):
            if math.hypot(x + 0.5 - c, z + 0.5 - c) < 3:
                b.set(x, D, z, b.rng.choice(("obsidian", "crying_obsidian", gz("voidstone"), "black_concrete")))
    # Exotic crystals grown through the floor round the pit.
    for _ in range(14):
        a, d = b.rng.random() * 2 * math.pi, 2 + b.rng.random() * 6
        x, z = round(c + d * math.cos(a) - 0.5), round(c + d * math.sin(a) - 0.5)
        if b.name(x, D + 1, z) in (None, AIR):
            b.set(x, D, z, gz("voidstone"))
            b.set(x, D + 1, z, gz(b.rng.choice(("exotic_crystal_cluster", "large_exotic_bud", "small_exotic_bud"))), {"facing": "up", "waterlogged": "false"})
    # Two laboratories on the deck, benches and chests inside.
    for x0, z0 in ((2, 12), (24, 12)):
        b.room(x0, D, z0, x0 + 4, D + 4, z0 + 6, "white_concrete", floor=STEEL, roof=STEEL)
        b.clear(x0 + 1, D + 1, z0 + 6, x0 + 3, D + 2, z0 + 6) if x0 < 10 else b.clear(x0 + 1, D + 1, z0, x0 + 3, D + 2, z0)
        b.fill(x0 + 1, D + 1, z0 + 1, x0 + 3, D + 1, z0 + 1, CASING)
        b.chest(x0 + 2, D + 1, z0 + 3, "east" if x0 < 10 else "west", "research_station")
        b.set(x0 + 4 if x0 < 10 else x0, D + 2, z0 + 3, "glass_pane")
        b.set(x0 + 2, D + 3, z0 + 4, "end_rod", {"facing": "down"})
    # The antenna, its dish bent over.
    for y in range(D + 1, D + 12):
        b.set(27, y, 27, "iron_bars")
    dish(b, 25.5, D + 12, 25.5, 2.6, 2.2, 0.8, "iron_block", "white_concrete")
    # Debris drifting off the deck.
    for cx_, cy_, cz_ in ((3, 14, 3), (28, 6, 4), (5, 4, 27), (27, 17, 22)):
        for _ in range(5):
            b.set(cx_ + b.rng.randint(-1, 1), cy_ + b.rng.randint(-1, 1), cz_ + b.rng.randint(-1, 1),
                  b.rng.choice(("end_stone", STEEL, CONC_WHITE, gz("voidstone"))))
    return b


def test_room() -> Build:
    """For the GameTests: a small room with a chest (its loot table) and a data marker (a suited stand)."""
    b = Build(5, 4, 5, seed=1)
    b.room(0, 0, 0, 4, 3, 4, CONC)
    b.chest(1, 1, 1, "south", "waste_storage")
    b.marker(3, 1, 3, "stand:hazmat")
    return b


# =====================================================================================================================
TEMPLATES = {
    "test_room": test_room,
    "silo_surface_intact": lambda: silo_surface("intact"), "silo_surface_flooded": lambda: silo_surface("flooded"),
    "silo_surface_collapsed": lambda: silo_surface("collapsed"),
    "silo_below_intact": lambda: silo_below("intact"), "silo_below_flooded": lambda: silo_below("flooded"),
    "silo_below_collapsed": lambda: silo_below("collapsed"),
    "bunker_surface": bunker_surface, "bunker_below": bunker_below,
    "radar_outpost": radar_outpost,
    "test_site_zero": test_site_zero, "test_site_bunker": test_site_bunker,
    "crashed_missile": crashed_missile,
    "waste_storage": waste_storage,
    "research_station": research_station,
}


def part(template: str, offset: tuple[int, int, int], terrain: str = "none", integrity: float = 1.0, weathering: str = "abandoned") -> dict:
    return {"template": f"groundzero:site/{template}", "offset": list(offset), "terrain": terrain, "integrity": integrity,
            "weathering": weathering}


#: The structures: (biome tag values, generation step, placement, spacing, separation, salt, extra, variants). The
#: spacings and max_slope (the most the land may rise and fall over the parts on the surface, read on a 5x5 grid) were
#: set from the world survey's census (`gradlew runWorldSurvey -Psurvey=census`, DESIGN.md §22.1): each site about as
#: common as the vanilla structure it is most like. apart_from_villages: no closer than this many chunks to where a
#: village could stand (vanilla's exclusion zone, as its pillager outposts keep), since the census found a few sites in
#: or against villages.
STRUCTURES = {
    "abandoned_silo": dict(
        biomes=["#minecraft:is_taiga", "minecraft:plains", "minecraft:sunflower_plains", "minecraft:snowy_plains", "minecraft:desert",
                "minecraft:savanna", "minecraft:meadow"],
        step="surface_structures", placement="surface", spacing=48, separation=18, salt=1_946_311, max_slope=12, apart_from_villages=6,
        variants=[(3, "intact"), (2, "flooded"), (2, "collapsed")]),
    "command_bunker": dict(
        biomes=["#minecraft:is_hill", "#minecraft:is_forest", "#minecraft:is_taiga", "minecraft:meadow", "minecraft:plains"],
        step="surface_structures", placement="surface", spacing=52, separation=20, salt=1_957_101, max_slope=8, apart_from_villages=6),
    "radar_outpost": dict(
        biomes=["#minecraft:is_hill", "minecraft:meadow", "minecraft:savanna_plateau", "minecraft:wooded_badlands",
                "minecraft:snowy_plains", "minecraft:plains", "minecraft:windswept_savanna"],
        step="surface_structures", placement="surface", spacing=44, separation=16, salt=1_954_820, max_slope=12, apart_from_villages=6),
    "test_site": dict(
        biomes=["minecraft:desert", "#minecraft:is_badlands"],
        step="surface_structures", placement="surface", spacing=32, separation=12, salt=1_945_716, max_slope=12, apart_from_villages=6),
    "crashed_missile": dict(
        biomes=["minecraft:plains", "minecraft:sunflower_plains", "#minecraft:is_forest", "#minecraft:is_taiga", "#minecraft:is_savanna",
                "minecraft:desert", "#minecraft:is_badlands", "minecraft:snowy_plains", "minecraft:meadow"],
        step="surface_structures", placement="surface", spacing=38, separation=14, salt=1_980_919, max_slope=5, apart_from_villages=6),
    "waste_storage": dict(
        biomes=["#minecraft:is_overworld"],
        step="underground_structures", placement="buried", spacing=44, separation=16, salt=1_999_326, depth=42, frequency=0.35),
    "exotic_research_station": dict(
        biomes=["minecraft:end_highlands", "minecraft:end_midlands", "minecraft:end_barrens", "minecraft:small_end_islands"],
        step="surface_structures", placement="floating", spacing=64, separation=24, salt=1_983_055),
}


def site_variants(name: str) -> list[dict]:
    if name == "abandoned_silo":
        # The origin is the shaft's axis at grade: the hardstand's (7, 0, 10), the complex's (7, 35, 7) under it.
        return [{"weight": w, "parts": [part(f"silo_surface_{v}", (-7, 0, -10), "beard_thin"),
                                        part(f"silo_below_{v}", (-7, -SILO_Y, -7), "encapsulate")]}
                for w, v in STRUCTURES[name]["variants"]]
    if name == "command_bunker":
        return [{"weight": 1, "parts": [part("bunker_surface", (-16, 0, -15), "beard_thin"),
                                        part("bunker_below", (-16, -15, -15), "encapsulate")]}]
    if name == "radar_outpost":
        return [{"weight": 1, "parts": [part("radar_outpost", (-17, 0, -15), "beard_thin")]}]
    if name == "test_site":
        return [{"weight": 1, "parts": [part("test_site_zero", (-18, -3, -18), "beard_thin", weathering="none"),
                                        part("test_site_bunker", (-6, -1, 30), "beard_thin")]}]
    if name == "crashed_missile":
        return [{"weight": 1, "parts": [part("crashed_missile", (-19, -5, -8), "beard_thin", weathering="none")]}]
    if name == "waste_storage":
        return [{"weight": 1, "parts": [part("waste_storage", (-19, 0, -16), "encapsulate")]}]
    if name == "exotic_research_station":
        return [{"weight": 1, "parts": [part("research_station", (-15, -9, -15), "none", weathering="end")]}]
    raise KeyError(name)


def check(name: str, b: Build) -> None:
    """Every block a template names must exist (26.3 renamed some: chain is iron_chain): vanilla's from the decompiled
    client's blockstates, ours from the mod's."""
    missing = set()
    for _, block, _ in b.t.blocks():
        ns, _, n = block.partition(":")
        folder = paths.VANILLA_ASSETS / "blockstates" if ns == "minecraft" else paths.ASSETS / "blockstates"
        if n != "air" and not (folder / f"{n}.json").exists():
            missing.add(block)
    if missing:
        raise SystemExit(f"{name}: unknown blocks {sorted(missing)}")


def main() -> None:
    out = DATA / "structure" / "site"
    for name, make in TEMPLATES.items():
        b = make()
        check(name, b)
        b.save(out / f"{name}.nbt")
        print("template", name)
    for name, s in STRUCTURES.items():
        structure = {"type": "groundzero:site", "biomes": f"#groundzero:has_structure/{name}", "spawn_overrides": {}, "step": s["step"],
                     "variants": site_variants(name), "placement": s["placement"]}
        if s["placement"] != "floating":
            # Not "none": the beardifier skips a structure whose own adaptation is none before it asks the pieces for
            # theirs (each SitePiece gives its own: beard_thin, encapsulate).
            structure["terrain_adaptation"] = "beard_thin"
        if "max_slope" in s:
            structure["max_slope"] = s["max_slope"]
        if "depth" in s:
            structure["depth"] = s["depth"]
        path = DATA / "worldgen" / "structure" / f"{name}.json"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(structure, indent=2) + "\n", encoding="utf-8")
        sset = {"placement": {"type": "minecraft:random_spread", "salt": s["salt"], "separation": s["separation"], "spacing": s["spacing"]},
                "structures": [{"structure": f"groundzero:{name}", "weight": 1}]}
        if "frequency" in s:
            sset["placement"]["frequency"] = s["frequency"]       # a share of the places it could stand
        if "apart_from_villages" in s:
            sset["placement"]["exclusion_zone"] = {"chunk_count": s["apart_from_villages"], "other_set": "minecraft:villages"}
        path = DATA / "worldgen" / "structure_set" / f"{name}.json"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(sset, indent=2) + "\n", encoding="utf-8")
        tag = DATA / "tags" / "worldgen" / "biome" / "has_structure" / f"{name}.json"
        tag.parent.mkdir(parents=True, exist_ok=True)
        tag.write_text(json.dumps({"values": s["biomes"]}, indent=2) + "\n", encoding="utf-8")
        print("structure", name)


if __name__ == "__main__":
    main()
