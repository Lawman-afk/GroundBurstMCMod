"""The Cold-War Command Bunker, rebuilt (DESIGN.md §22.2): a regional command post after the Diefenbunker, Kelvedon Hatch
and their kind, built to the kit's rules (tools/structure/kit.py).

On the surface (`bunker_surface`): an earth berm with a concrete portal in its south face (wing walls holding the bank,
hazard stripes round the opening, floodlights over it); inside the portal a vestibule whose blast-deflector wall stands
square in front of the first blast door, and behind that door the stair's head; the guyed radio mast over the radio room
with its tuning hut; the air intake and exhaust (mushroom caps over louvres) and the generator's stack over the plant
room; the escape hatch, a copper lid on a plug of gravel; a helipad going back to grass; chain-link and barbed wire round
it all, the guard post's sandbags inside the gate.

Underground (`bunker_below`, its top layer just under the ground): the stair down, twelve steps under a stepped ceiling,
to the second blast door and the decontamination room (white tile, showers over drains, two suits on stands); the main
corridor (a green dado, chamfered ribs every six blocks, a copper pipe and a cable along its ceiling) with the radio room
(consoles, the teleprinter, the transmitter racks, the mast's lead down through the roof), the armoury (a chain-link cage,
the rifle rack, crates) and the plant room (the diesel generator, the fuel tank, the filter bank under the ducts, the
switchboard) north of it; south of it the dormitory (bunks two high, steel lockers), the washroom, the situation room two
storeys tall (a continent on its south wall with the strikes marked, the plotting table, the gallery of consoles looking
down on both), the records room behind it, the mess and its galley, and the escape tube up from the mess's corner.

Both templates 44 x 44 in plan with the site's origin at (22, 22); the surface 27 high, the bunker 12.
"""
from __future__ import annotations

import math

from build import AIR, gb, mc
from kit import (CHAIN_LINK, CORRUGATED_SLAB, E, FLOODLIGHT, GRATING, GRATING_SLAB, I_BEAM, LATTICE, N, PANEL, RUSTED,
                 RUSTED_PANEL, S, SANDBAG_SLAB, SANDBAGS, W, Kit)

SIZE = 44
SURF_Y, BELOW_Y = 27, 12
ORIGIN = (22, 22)
TOP = BELOW_Y - 1                         # the bunker's top layer, just under the ground (the ground is its y 12)

WALL, DADO, FLOOR, TRIM = "light_gray_concrete", "green_terracotta", "polished_andesite", "smooth_stone"
CHAMFER = "polished_andesite_stairs"
HAZARD = gb("hazard_concrete")
PIPE = "exposed_lightning_rod"            # a copper pipe gone dull
WALL_POST = {"east": "none", "north": "none", "south": "none", "west": "none", "up": True, "waterlogged": False}

# Rooms (inside: x0, z0, x1, z1); walls one block round each.
CORRIDOR = (2, 9, 41, 11)
RADIO = (2, 1, 11, 7)
ARMOURY = (13, 1, 19, 7)
PLANT = (21, 1, 41, 7)
DORM = (2, 13, 14, 24)
WASH = (16, 19, 18, 24)
DECON = (17, 14, 22, 17)                   # walls of its own (tiled), apart from the corridor's and the situation room's
SITUATION = (25, 13, 41, 27)
RECORDS = (25, 29, 41, 33)
MESS = (2, 26, 17, 33)
STAIR_X = (20, 22)                         # the stair down, three wide
HEAD_Z = 32                                # its top step (in the surface's layer 0); step k at z HEAD_Z - k, y 12 - k
LANDING = (19, 20)                         # the landing at its foot (z)
MAST = (8, 6)                              # the radio mast, over the radio room; its lead comes down there
INTAKE, EXHAUST, STACK = (36, 4), (40, 4), (26, 4)
HATCH = (2, 33)                            # the escape tube, in the mess's corner


def step_y(k: int) -> int:
    """The height (the bunker's y) of step k of the stair, 0 at the top."""
    return 12 - k


# --- the bunker ---------------------------------------------------------------------------------------------------------------
def bunker_below() -> Kit:
    k = Kit(SIZE, BELOW_Y, SIZE, seed=7_100)
    k.buried = True
    for room, roof in ((CORRIDOR, 4), (RADIO, 4), (ARMOURY, 4), (PLANT, 4), (DORM, 5), (WASH, 4), (DECON, 4), (SITUATION, 7),
                       (RECORDS, 4), (MESS, 4)):
        shell(k, room, roof)
    stair(k)
    corridor(k)
    radio_room(k)
    armoury(k)
    plant_room(k)
    dormitory(k)
    washroom(k)
    decontamination(k)
    situation_room(k)
    records(k)
    mess(k)
    doors(k)
    escape_tube(k)
    wear(k)
    return k


def shell(k: Kit, room: tuple, roof: int) -> None:
    """A room's box: its floor, its walls (a dark dado course over the floor, concrete above), its ceiling, air inside.
    A wall shared with the next room is drawn alike by both; a wall never takes another room's inside."""
    x0, z0, x1, z1 = room
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            inner = x0 <= x <= x1 and z0 <= z <= z1
            for y in range(0, roof + 1):
                if inner:
                    k.set(x, y, z, FLOOR if y == 0 else WALL if y == roof else AIR)
                elif k.name(x, y, z) != AIR:
                    k.set(x, y, z, DADO if y == 1 else WALL)


def stair(k: Kit) -> None:
    """The stair from the surface: twelve steps of polished andesite three wide, solid under them, a stepped ceiling of
    upturned stairs four over each, the dado following the steps down its walls; the landing at its foot. Steps 0 to 4
    reach into the surface template (`stair_head`)."""
    xa, xb = STAIR_X
    for kk in range(1, 12):
        z, y = HEAD_Z - kk, step_y(kk)
        for x in range(xa, xb + 1):
            for yy in range(0, y):
                k.set(x, yy, z, WALL)
            k.stairs(x, y, z, CHAMFER, S)
            for yy in range(y + 1, min(y + 4, TOP + 1)):
                k.set(x, yy, z, AIR)
            if y + 4 <= TOP:
                k.stairs(x, y + 4, z, CHAMFER, N, half="top")
        for x in (xa - 1, xb + 1):
            for yy in range(0, min(y + 5, TOP + 1)):
                k.set(x, yy, z, DADO if yy == y + 1 else WALL)
        if kk % 3 == 0:
            k.lantern((xa + xb) // 2, y + 3, z, hanging=True)
    # The landing: four high, its ceiling level with the last step's.
    z0, z1 = LANDING
    for x in range(xa - 1, xb + 2):
        for z in range(z0, z1 + 1):
            for y in range(0, 6):
                wall = x in (xa - 1, xb + 1)
                if wall:
                    k.set(x, y, z, DADO if y == 1 else WALL)
                else:
                    k.set(x, y, z, FLOOR if y == 0 else WALL if y == 5 else AIR)
    k.set(xa + 1, 4, z1, FLOODLIGHT, {"facing": "down"})


def corridor(k: Kit) -> None:
    """The main corridor: a stripe down its floor, chamfered ribs across its ceiling every six blocks, a copper pipe along
    one side of the ceiling and a cable along the other, a lamp between ribs (some gone)."""
    x0, z0, x1, z1 = CORRIDOR
    for x in range(x0, x1 + 1):
        k.set(x, 0, (z0 + z1) // 2, TRIM)
        if x % 6 == 1:
            k.stairs(x, 3, z0, CHAMFER, N, half="top")
            k.stairs(x, 3, z1, CHAMFER, S, half="top")
        else:
            k.set(x, 3, z1, PIPE, {"facing": E, "powered": False, "waterlogged": False})
            k.set(x, 3, z0, "iron_chain", {"axis": "x", "waterlogged": False})
        if x % 6 == 4 and k.n(x, 3, 10, 4.0, 11) > 0.3:
            k.lantern(x, 3, (z0 + z1) // 2, hanging=True)


def radio_room(k: Kit) -> None:
    """Consoles along the north wall with their chairs, the transmitter racks on the east wall, the teleprinter on its
    desk, the mast's lead down through the roof to the operating table."""
    x0, z0, x1, z1 = RADIO
    for x in range(x0 + 1, x1 - 1):
        k.set(x, 1, z0, PANEL)
        k.set(x, 2, z0, "black_stained_glass" if x % 3 else "gray_stained_glass")
        if x % 2 == 0:
            k.stairs(x, 1, z0 + 2, "spruce_stairs", S)
    for z in range(z0 + 1, z1 - 1):
        k.set(x1, 1, z, PANEL)
        k.set(x1, 2, z, "redstone_lamp", {"lit": False})
        k.set(x1, 3, z, PANEL)
    for z in (z0 + 3, z0 + 4):
        k.slab(x0, 1, z, "spruce_slab", "top")
    k.set(x0, 2, z0 + 3, "crafter", {"crafting": False, "orientation": "east_up", "triggered": False})
    lx, lz = MAST
    k.set(lx, 1, lz, PANEL)
    k.set(lx, 2, lz, "lever", {"face": "floor", "facing": S, "powered": False})
    for y in range(3, TOP + 1):
        k.set(lx, y, lz, "iron_chain", {"axis": "y", "waterlogged": False})
    k.chest(x1 - 1, 1, z1, N, "bunker_stores")
    k.lantern(x0 + 3, 3, z0 + 4, hanging=True)


def armoury(k: Kit) -> None:
    """A chain-link cage across the back with its gate, the rifle rack and the crates inside it, the chests; a stand by
    the door."""
    x0, z0, x1, z1 = ARMOURY
    cage, gx = z0 + 3, (x0 + x1) // 2
    for x in range(x0, x1 + 1):
        for y in (1, 2, 3):
            if x != gx or y == 3:
                k.set(x, y, cage, CHAIN_LINK)
    k.door(gx, 1, cage, S, "iron_door", open_=True)
    for x in range(x0 + 1, x1):
        k.trapdoor(x, 2, z0, "spruce_trapdoor", S, half="top")          # the rifle rack's shelf
        k.set(x, 3, z0, "tripwire_hook", {"attached": False, "facing": S, "powered": False})
    k.chest(x0, 1, z0, E, "bunker_armory")
    k.chest(x1, 1, z0 + 1, W, "bunker_armory")
    for x in (x0 + 2, x0 + 3, x0 + 4):
        k.barrel(x, 1, z0, "north")
    k.barrel(x0 + 3, 1, z0 + 1, "east")
    k.barrel(x0 + 3, 2, z0 + 1, "east")
    k.marker(x1, 1, z1, "stand:bare:west")
    k.lantern(x0 + 3, 3, z1 - 1, hanging=True)


def plant_room(k: Kit) -> None:
    """The diesel generator on its skid (radiator, engine, alternator), its exhaust up through the roof; the fuel tank on
    its saddles; the filter bank along the south wall under the intake and exhaust ducts; the switchboard."""
    x0, z0, x1, z1 = PLANT
    gx0, gx1, gz0, gz1 = 23, 28, 3, 4
    for x in range(gx0, gx1 + 1):
        for z in range(gz0, gz1 + 1):
            k.set(x, 0, z, TRIM)
            k.set(x, 1, z, GRATING if x == gx0 else RUSTED_PANEL if x < gx1 else "gray_concrete")
            if x == gx0 or x < gx1 - 1:
                k.set(x, 2, z, GRATING if x == gx0 else PANEL)
            elif x == gx1:
                k.set(x, 2, z, "gray_concrete")
    sx, sz = STACK
    for y in range(3, TOP + 1):
        k.set(sx, y, sz, "polished_blackstone_wall", WALL_POST)
    # The fuel tank: a plus of panels laid along x on I-beam saddles.
    tz = 3
    for x in range(31, 35):
        for dz, dy in ((0, 1), (0, 2), (0, 3), (-1, 2), (1, 2)):
            low = dy == 1 or (dy == 2 and dz != 0 and k.n(x, dy, tz + dz, 2.0, 21) > 0.6)
            k.set(x, dy, tz + dz, RUSTED_PANEL if low else PANEL)
    for x in (30, 35):
        k.set(x, 1, tz, I_BEAM, {"axis": "z"})
    # The filter bank: housings along the south wall, a grille in each; the ducts up through the roof above them.
    for x in range(36, x1 + 1):
        for y in (1, 2, 3):
            k.set(x, y, z1, GRATING if y == 2 else PANEL)
    for (dx, dz) in (INTAKE, EXHAUST):
        k.set(dx, 4, dz, GRATING)
        for y in range(5, TOP + 1):
            k.set(dx, y, dz, AIR)
    # The switchboard on the north wall: panels, levers, a row of dead lamps for its dials.
    for x in range(36, x1 + 1):
        k.set(x, 1, z0, PANEL)
        k.set(x, 2, z0, PANEL)
        k.set(x, 3, z0, "redstone_lamp", {"lit": False})
        k.set(x, 2, z0 + 1, "lever", {"face": "wall", "facing": S, "powered": x % 3 == 0})
    for x in range(x0, x1 + 1):
        if k.name(x, 3, z0 + 1) == AIR:
            k.set(x, 3, z0 + 1, PIPE, {"facing": E, "powered": False, "waterlogged": False})
    for x in (24, 33, 39):
        k.set(x, 3, z1 - 1, FLOODLIGHT, {"facing": "down"})


def dormitory(k: Kit) -> None:
    """Bunks two high down both walls (a top slab under the upper bed), steel lockers between them, a table and its
    chairs (one pushed back askew), the doorway through to the mess."""
    x0, z0, x1, z1 = DORM
    def bunk(xf: int, z: int, facing: str, colour: str) -> None:
        xh = xf - 1 if facing == W else xf + 1
        k.bed(xf, 1, z, facing, colour)
        for x in (xf, xh):
            k.slab(x, 2, z, "spruce_slab", "top")
        k.bed(xf, 3, z, facing, colour)
    for i, z in enumerate((z0 + 1, z0 + 3, z0 + 5, z0 + 7, z0 + 9)):
        bunk(x0 + 1, z, W, ("gray", "green")[i % 2])
        for y in (1, 2):
            k.trapdoor(x0, y, z + 1, "iron_trapdoor", E, open_=True)      # the lockers
    for i, z in enumerate((z0 + 1, z0 + 3, z0 + 5)):
        bunk(x1 - 1, z, E, ("green", "gray")[i % 2])
        for y in (1, 2):
            k.trapdoor(x1, y, z + 1, "iron_trapdoor", W, open_=True)
    tx, tz = (x0 + x1) // 2, z1 - 3
    for x in range(tx - 1, tx + 2):
        k.slab(x, 1, tz, "spruce_slab", "top")
        k.stairs(x, 1, tz - 1, "spruce_stairs", N)
        k.stairs(x, 1, tz + 1, "spruce_stairs", S if x != tx else E)
    k.barrel(x1, 1, z1, "up", "bunker_stores")
    for x in (x0 + 4, x1 - 4):
        k.lantern(x, 4, (z0 + z1) // 2, hanging=True)


def washroom(k: Kit) -> None:
    x0, z0, x1, z1 = WASH
    for z in (z0 + 1, z0 + 3):
        k.set(x1, 1, z, "cauldron")
    for z in (z1 - 1, z1):
        k.set(x0, 1, z, "hopper", {"enabled": False, "facing": "down"})
    k.lantern(x0 + 1, 3, z0 + 2, hanging=True)


def decontamination(k: Kit) -> None:
    """Tiled in white, four showers over drains, a bench down the middle; the suits on their stands by the inner door."""
    x0, z0, x1, z1 = DECON
    plain = {mc(WALL), mc(DADO), mc(FLOOR)}
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            for y in range(0, 4):
                if x0 <= x <= x1 and z0 <= z <= z1 and y > 0:
                    continue
                if k.name(x, y, z) in plain:
                    k.set(x, y, z, "polished_diorite" if y else "white_concrete")
    for x in (x0 + 1, x1 - 1):
        for z in (z0 + 1, z1 - 1):
            k.set(x, 3, z, "lightning_rod", {"facing": "down", "powered": False, "waterlogged": False})
            k.set(x, 0, z, GRATING)
    for z in range(z0 + 1, z1):
        k.slab((x0 + x1) // 2, 1, z, "smooth_stone_slab")
    k.marker(x0, 1, z0, "stand:hazmat:south")
    k.marker(x1, 1, z0, "stand:hazmat:south")
    k.lantern((x0 + x1) // 2, 3, z0 + 2, hanging=True)


def situation_room(k: Kit) -> None:
    """Two storeys: the map of a continent across the south wall (the sea in blue, the land in greens, the strikes marked
    in red, framed in smooth stone), the plotting table under it, the gallery of consoles along the north wall looking
    down on both, reached by a stair at its west end; lamps hung from the roof on chains; status boards on the east."""
    x0, z0, x1, z1 = SITUATION
    mx0, mx1, my0, my1 = x0 + 3, x1 - 3, 2, 6
    land = {}
    for x in range(mx0, mx1 + 1):
        for y in range(my0, my1 + 1):
            u, v = (x - mx0) / (mx1 - mx0), (y - my0) / (my1 - my0)
            a = ((u - 0.32) / 0.3) ** 2 + ((v - 0.55) / 0.42) ** 2 + 0.25 * math.sin(u * 17.0)
            b = ((u - 0.82) / 0.1) ** 2 + ((v - 0.35) / 0.3) ** 2
            if a < 1.0 or b < 1.0:
                land[(x, y)] = "lime_terracotta" if (a < 0.45 or b < 0.4) and k.n(x, y, 0, 2.0, 31) > 0.45 else "green_terracotta"
    for x in range(mx0 - 1, mx1 + 2):
        for y in range(my0 - 1, my1 + 2):
            edge = x in (mx0 - 1, mx1 + 1) or y in (my0 - 1, my1 + 1)
            k.set(x, y, z1 + 1, TRIM if edge else land.get((x, y), "blue_concrete"))
    for x, y in ((mx0 + 2, my0 + 2), (mx0 + 5, my0 + 3), (mx1 - 1, my0 + 1)):
        k.set(x, y, z1 + 1, "red_concrete")                                       # where the strikes would fall
    # The plotting table: the same map in carpet on a spruce table, its markers; chairs along both sides.
    tx0, tx1, tz0, tz1 = x0 + 5, x1 - 5, z0 + 7, z0 + 10
    for x in range(tx0, tx1 + 1):
        for z in range(tz0, tz1 + 1):
            k.slab(x, 1, z, "spruce_slab", "top")
            u, v = (x - tx0) / (tx1 - tx0), (z - tz0) / (tz1 - tz0)
            on_land = ((u - 0.32) / 0.3) ** 2 + ((v - 0.5) / 0.5) ** 2 < 1.0 or ((u - 0.82) / 0.12) ** 2 + ((v - 0.4) / 0.35) ** 2 < 1.0
            k.set(x, 2, z, "green_carpet" if on_land else "blue_carpet")
    for x, z in ((tx0 + 2, tz0 + 1), (tx0 + 4, tz0 + 2), (tx1 - 1, tz0 + 1)):
        k.set(x, 2, z, "red_carpet")
    for x in range(tx0, tx1 + 1, 2):
        k.stairs(x, 1, tz0 - 1, "spruce_stairs", N)
        k.stairs(x, 1, tz1 + 1, "spruce_stairs", S)
    # The gallery: grating three deep along the north wall at half height, a chain-link rail, consoles facing the map.
    gz0, gz1, gy = z0, z0 + 2, 3
    for x in range(x0, x1 + 1):
        for z in range(gz0, gz1 + 1):
            k.slab(x, gy, z, GRATING_SLAB, "top")
        if x > x0 + 1:
            k.set(x, gy + 1, gz1 + 1, CHAIN_LINK)
        k.set(x, gy + 1, gz0, PANEL)
        k.set(x, gy + 2, gz0, "black_stained_glass" if (x // 2) % 2 else "gray_stained_glass")
        if x % 2:
            k.stairs(x, gy + 1, gz0 + 1, "spruce_stairs", S)
    for x in range(x0, x0 + 2):
        for y, z in ((1, gz1 + 3), (2, gz1 + 2), (3, gz1 + 1)):
            k.stairs(x, y, z, CHAMFER, N)
    # Status boards on the east wall: dead lamps in steel.
    for z in range(z0 + 5, z1 - 1):
        for y in (2, 3, 4):
            if (z + y) % 2:
                k.set(x1 + 1, y, z, "redstone_lamp", {"lit": False})
            else:
                k.set(x1 + 1, y, z, PANEL)
    # Lamps hung on chains from the roof; floodlights over the gallery.
    for x, z in ((x0 + 4, z0 + 8), (x1 - 4, z0 + 8), ((x0 + x1) // 2, z1 - 2)):
        k.set(x, 6, z, "iron_chain", {"axis": "y", "waterlogged": False})
        k.lantern(x, 5, z, hanging=True)
    for x in range(x0 + 2, x1, 5):
        k.set(x, 6, z0, FLOODLIGHT, {"facing": S})


def records(k: Kit) -> None:
    """Filing cabinets in rows, the officer's desk and his safe."""
    x0, z0, x1, z1 = RECORDS
    for x in range(x0 + 1, x1 - 3, 4):
        for z in range(z0, z1 - 1):
            for y in (1, 2):
                slots = {f"slot_{i}_occupied": (x + z + y + i) % 3 == 0 for i in range(6)}
                k.set(x, y, z, "chiseled_bookshelf", {"facing": E, **slots})
    k.chest(x1, 1, z1, W, "bunker_stores")
    for x in (x1 - 3, x1 - 2):
        k.slab(x, 1, z1 - 1, "spruce_slab", "top")
    k.stairs(x1 - 2, 1, z1, "spruce_stairs", S)
    k.lantern((x0 + x1) // 2, 3, (z0 + z1) // 2, hanging=True)


def mess(k: Kit) -> None:
    """Two long tables and their benches, the galley's counter along the east wall: the stove, the sink, crates."""
    x0, z0, x1, z1 = MESS
    for tz in (z0 + 2, z0 + 5):
        for x in range(x0 + 3, x0 + 11):
            k.slab(x, 1, tz, "spruce_slab", "top")
            k.stairs(x, 1, tz - 1, "spruce_stairs", N)
            k.stairs(x, 1, tz + 1, "spruce_stairs", S)
    for z in range(z0, z1 + 1):
        if z == z0 + 2:
            k.set(x1, 1, z, "smoker", {"facing": W, "lit": False})
        elif z == z0 + 4:
            k.set(x1, 1, z, "cauldron")
        elif z in (z0, z1):
            k.barrel(x1, 1, z, "up")
        else:
            k.set(x1, 1, z, TRIM)
        if z % 2:
            k.trapdoor(x1, 3, z, "spruce_trapdoor", W, half="top")
    k.barrel(x1, 2, z0, "up", "bunker_stores")
    for x in (x0 + 4, x0 + 10):
        k.lantern(x, 3, (z0 + z1) // 2, hanging=True)


def doors(k: Kit) -> None:
    """The doors: iron (buttons by each, `Kit.finish`) into the radio room, the armoury, the plant room (a pair), the
    situation room, the records and the decontamination room; the second blast door a steel leaf with its wicket; wood
    and an open doorway in the living quarters."""
    k.iron_door(7, 1, 8, S)
    k.iron_door(16, 1, 8, S)
    k.iron_door(30, 1, 8, S, hinge="left")
    k.iron_door(31, 1, 8, S, hinge="right")
    k.iron_door(27, 1, 12, N)
    k.iron_door(39, 1, 12, N)
    k.iron_door(40, 1, 28, S)
    for y in (1, 2):
        k.set(20, y, 12, AIR)                                           # through the corridor's wall to the decontamination room's
    k.iron_door(20, 1, 13, N)
    k.door(8, 1, 12, N, "spruce_door")                                  # the dormitory
    # The second blast door: a steel leaf across the stair's foot with a wicket in it, hazard-framed.
    xa, xb = STAIR_X
    z = DECON[3] + 1
    for y in (1, 2, 3):
        k.set(xa - 1, y, z, HAZARD)
        k.set(xb + 1, y, z, HAZARD)
        for x in (xa, xb):
            k.set(x, y, z, PANEL)
    k.set(xa + 1, 3, z, PANEL)
    k.iron_door(xa + 1, 1, z, S)
    k.door(15, 1, 21, E, "spruce_door")
    for x in (7, 8):
        for y in (1, 2):
            k.set(x, y, DORM[3] + 1, AIR)


def escape_tube(k: Kit) -> None:
    """Up from the mess's corner: a ladder in a concrete tube to the hatch, its top three blocks filled with gravel (the
    way out has to be dug)."""
    hx, hz = HATCH
    for y in range(4, TOP + 1):
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                if (dx, dz) != (0, 0) and y > 4:
                    k.set(hx + dx, y, hz + dz, WALL)
        k.set(hx, y, hz, "gravel" if y > TOP - 3 else AIR)
    k.ladder(hx, 1, TOP - 3, hz, E)


def wear(k: Kit) -> None:
    """Sixty years shut: cobwebs in the upper corners, rubble where the ceiling has spalled, stains down the walls."""
    k.cobwebs((1, 1, 1, SIZE - 2, 6, SIZE - 2), 0.12)
    k.rubble((2, 1, 2, SIZE - 3, 1, SIZE - 3), 0.012, ("gravel", "cobblestone_slab", "andesite"))
    k.stains((0, 1, 0, SIZE - 1, 6, SIZE - 1), [WALL], ("gray_concrete", "cyan_terracotta"), 0.012, run=3)


# --- the surface --------------------------------------------------------------------------------------------------------------
def bunker_surface() -> Kit:
    k = Kit(SIZE, SURF_Y, SIZE, seed=7_200)
    stair_head(k)
    vestibule(k)
    portal(k)
    mast(k)
    vents(k)
    hatch(k)
    helipad(k)
    fuel_tank(k)
    antenna(k)
    gatehouse(k)
    tracks(k)
    berm(k)
    yard(k)
    k.overgrow((0, 0, SIZE - 1, SIZE - 1), density=0.42)
    return k


def stair_head(k: Kit) -> None:
    """The top of the stair: steps 0 to 4 reach into the surface (the top step itself, the air over the next ones, the
    stepped ceiling), walled each side."""
    xa, xb = STAIR_X
    for kk in range(0, 5):
        z, y = HEAD_Z - kk, step_y(kk) - 12              # in the surface's y (its layer 0 is the bunker's 12)
        ceil = y + 4
        for x in range(xa, xb + 1):
            if y >= 0:
                k.stairs(x, y, z, CHAMFER, S)
            for yy in range(max(0, y + 1), ceil):
                k.set(x, yy, z, AIR)
            k.stairs(x, ceil, z, CHAMFER, N, half="top")
        for x in (xa - 1, xb + 1):
            for yy in range(0, ceil + 1):
                k.set(x, yy, z, WALL)
        if kk == 3:
            k.lantern((xa + xb) // 2, ceil - 1, z, hanging=True)


def vestibule(k: Kit) -> None:
    """Inside the portal: the deflector wall square in front of the first blast door (a blast would strike it, not the
    door), a way round it each side; the blast door a steel leaf with its wicket, hazard-framed."""
    x0, x1, z0, z1 = 18, 24, 34, 37
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 1):
            for y in range(0, 5):
                side = x in (x0 - 1, x1 + 1)
                if side or z == z0 - 1 or y == 4:
                    k.set(x, y, z, WALL)
                else:
                    k.set(x, y, z, FLOOR if y == 0 else AIR)
    for x in range(x0 + 1, x1):
        for y in (1, 2, 3):
            k.set(x, y, z0 + 1, WALL)
    k.set((x0 + x1) // 2, 2, z0 + 2, gb("radiation_sign"), {"facing": S})
    xa, xb = STAIR_X
    z = z0 - 1
    for y in (1, 2, 3, 4):
        k.set(xa - 1, y, z, HAZARD)
        k.set(xb + 1, y, z, HAZARD)
    for x in range(xa - 1, xb + 2):
        k.set(x, 4, z, HAZARD)
        k.set(x, 0, z, FLOOR)
    for y in (1, 2, 3):
        for x in (xa, xb):
            k.set(x, y, z, PANEL if y > 1 else RUSTED_PANEL)
    k.set(xa + 1, 3, z, PANEL)
    k.iron_door(xa + 1, 1, z, S)
    k.lantern(x0, 3, z1, hanging=True)
    k.lantern(x1, 3, z0, hanging=True)


def portal(k: Kit) -> None:
    """The headwall in the berm's south face, five high under its coping; the opening framed in hazard stripes, a
    floodlight either side over it; wing walls stepping down the bank each side; the apron in front, a drain across it."""
    hz, hx0, hx1 = 38, 15, 27
    xa, xb = STAIR_X
    for x in range(hx0, hx1 + 1):
        for y in range(1, 6):
            k.set(x, y, hz, WALL)
        k.slab(x, 6, hz, "smooth_stone_slab")
        k.set(x, 0, hz, FLOOR)
    for x in range(xa, xb + 1):
        for y in (1, 2, 3):
            k.set(x, y, hz, AIR)
    for y in (1, 2, 3, 4):
        k.set(xa - 1, y, hz, HAZARD)
        k.set(xb + 1, y, hz, HAZARD)
    for x in range(xa - 1, xb + 2):
        k.set(x, 4, hz, HAZARD)
    k.set(xa - 3, 2, hz + 1, gb("keep_out_sign"), {"facing": S})
    for x in (hx0 + 2, hx1 - 2):
        k.set(x, 5, hz + 1, FLOODLIGHT, {"facing": S})
    for i, z in enumerate(range(hz + 1, hz + 4)):
        h = 4 - i
        for x in (hx0, hx1):
            for y in range(1, h + 1):
                k.set(x, y, z, WALL)
            k.slab(x, h + 1, z, "smooth_stone_slab")
            k.set(x, 0, z, FLOOR)
    k.pad(hx0 + 1, hz + 1, hx1 - 1, SIZE - 1, cracked="andesite", salt=41)
    for x in range(xa, xb + 1):
        k.ground(x, hz + 1, GRATING)


def berm(k: Kit) -> None:
    """The earth heaped over the vestibule and the stair's head: a low hill highest over the vestibule and falling away
    gently to the north and the sides (a dome, its contours wandering with the noise, not terraces), banked up against the
    headwall and round behind the wing walls; grass on top, coarse dirt where it is thin, ferns, berry bushes and a few
    shrubs of leaves on its lower slopes."""
    cx, cz, h_top = 21.5, 33.0, 7.5
    flank_h = {35: 6.5, 36: 6.0, 37: 5.5, 38: 5.0, 39: 4.0, 40: 3.0, 41: 2.0, 42: 1.0}
    for x in range(2, SIZE - 2):
        for z in range(14, SIZE - 1):
            if z >= 38 and 15 <= x <= 27:
                continue                                                  # the headwall and the apron between the wing walls
            dx = (x + 0.5 - cx) / 11.5
            dz = (z + 0.5 - cz) / (12.5 if z + 0.5 < cz else 7.5)
            q = dx * dx + dz * dz + (k.n(x, 0, z, 4.0, 51) - 0.5) * 0.32
            h = h_top * (1.0 - q) if q < 1.0 else 0.0
            # Banked up behind the wing walls, falling away from them a block a block.
            if z in flank_h and not 15 <= x <= 27:
                h = max(h, flank_h[z] - (15 - x if x < 15 else x - 27) * 1.05)
            h = round(h)
            if h < 1:
                continue
            for y in range(1, h + 1):
                if k.name(x, y, z) is not None:
                    continue
                if y == h:
                    k.set(x, y, z, "coarse_dirt" if k.n(x, y, z, 2.5, 53) > 0.74 else "grass_block")
                else:
                    k.set(x, y, z, "rooted_dirt" if k.n(x, y, z, 2.0, 55) > 0.82 else "dirt")
            if k.name(x, h + 1, z) is not None or k.name(x, h, z) != mc("grass_block"):
                continue
            v, w = k.n(x, 9, z, 2.2, 57), k.n(x, 9, z, 1.3, 59)
            if v > 0.78 and h <= 4:
                k.set(x, h + 1, z, "spruce_leaves" if w > 0.5 else "azalea_leaves", {"persistent": True, "distance": 7,
                                                                                     "waterlogged": False})
            elif v > 0.5:
                if w > 0.3:
                    k.set(x, h + 1, z, "fern" if w > 0.62 else "short_grass")
                else:
                    k.set(x, h + 1, z, "sweet_berry_bush", {"age": 1})


def mast(k: Kit) -> None:
    """The radio mast: a lattice column twenty-four high on its footing, dipole arms at two heights, a rod at the top,
    three guys down to anchors; the tuning hut at its foot."""
    mx, mz = MAST
    k.fill(mx - 1, 0, mz - 1, mx + 1, 0, mz + 1, FLOOR)
    for y in range(1, 25):
        k.set(mx, y, mz, LATTICE)
    for y in (14, 21):
        for (dx, dz, face) in ((1, 0, E), (-1, 0, W), (0, 1, S), (0, -1, N)):
            for d in (1, 2):
                k.set(mx + dx * d, y, mz + dz * d, "lightning_rod", {"facing": face, "powered": False, "waterlogged": False})
    k.set(mx, 25, mz, "lightning_rod", {"facing": "up", "powered": False, "waterlogged": False})
    for ax, az in ((mx - 6, mz + 7), (mx + 8, mz + 6), (mx + 1, mz - 4)):
        guy(k, (mx, 18, mz), (ax, 1, az))
        k.ground(ax, az, FLOOR)
    hx0, hz0 = mx + 2, mz + 1
    k.pad(hx0 - 1, hz0 - 1, hx0 + 3, hz0 + 3, salt=61)
    k.clad(hx0, 1, hz0, hx0 + 2, 2, hz0 + 2, salt=63, rust_courses=0.8)
    k.clear(hx0 + 1, 1, hz0 + 1, hx0 + 1, 2, hz0 + 1)
    k.fill(hx0 - 1, 3, hz0 - 1, hx0 + 3, 3, hz0 + 3, CORRUGATED_SLAB, {"type": "bottom", "waterlogged": False})
    k.door(hx0 + 1, 1, hz0 + 2, S, "weathered_copper_door")
    k.set(hx0 + 2, 2, hz0 + 3, gb("high_voltage_sign"), {"facing": S})


def fuel_tank(k: Kit) -> None:
    """The generator's day tank above ground: a steel drum lying on two I-beam saddles, rusted along its belly, its
    filler pipe going down into the ground at one end."""
    x0, x1, tz = 28, 33, 12
    for x in range(x0, x1 + 1):
        for dz, y in ((0, 1), (0, 2), (0, 3), (-1, 2), (1, 2)):
            low = y == 1 or k.n(x, y, tz + dz, 2.0, 95) > 0.68
            k.set(x, y, tz + dz, RUSTED_PANEL if low else PANEL)
    for x in (x0 + 1, x1 - 1):
        for dz in (-1, 1):
            k.set(x, 1, tz + dz, I_BEAM, {"axis": "x"})
        k.footing(x, tz - 1, FLOOR)
        k.footing(x, tz + 1, FLOOR)
    for y in (0, 1, 2):
        k.set(x0 - 1, y, tz, "polished_blackstone_wall", WALL_POST)
    k.set(x0 - 1, 3, tz, "lever", {"face": "floor", "facing": E, "powered": False})
    k.set((x0 + x1) // 2, 2, tz + 2, gb("toxic_sign"), {"facing": S})


def antenna(k: Kit) -> None:
    """A wire antenna across the north of the compound: two wooden poles with their crossarms, the wire sagging
    between them."""
    a = k.pole(14, 15, 9, arm="z")
    b = k.pole(31, 15, 9, arm="z")
    k.wire(a, b, sag=1.6)


def gatehouse(k: Kit) -> None:
    """The guard's hut inside the gate: concrete with smooth-stone corners, windows on the gate and the road, a flat roof
    of slabs overhanging; inside, his desk and chair and a crate."""
    x0, x1, z0, z1 = 7, 10, 38, 41
    k.pad(x0 - 1, z0 - 1, x1 + 1, z1 + 1, salt=97)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            ring = x in (x0, x1) or z in (z0, z1)
            corner = x in (x0, x1) and z in (z0, z1)
            for y in (1, 2, 3):
                k.set(x, y, z, (TRIM if corner else WALL) if ring else AIR)
    for z in (z0 + 1, z0 + 2):
        k.set(x1, 2, z, "glass_pane" if k.n(x1, 2, z, 2.0, 99) > 0.35 else AIR)
    k.set(x0 + 1, 2, z1, "glass_pane")
    k.set(x0 + 2, 2, z1, AIR)
    k.door(x0 + 1, 1, z0, N, "weathered_copper_door")
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            k.slab(x, 4, z, "smooth_stone_slab")
    k.slab(x1 - 1, 1, z0 + 1, "spruce_slab", "top")
    k.slab(x1 - 1, 1, z0 + 2, "spruce_slab", "top")
    k.stairs(x0 + 1, 1, z0 + 2, "spruce_stairs", W)
    k.barrel(x0 + 1, 1, z1 - 1, "up")
    k.lantern(x0 + 1, 3, z0 + 1, hanging=True)


def tracks(k: Kit) -> None:
    """Gravel tracks worn between the places the crews went: the gate to the guard's hut, up the west side to the mast,
    across the north past the stack and the vents, down the east to the helipad; round the berm, never over it."""
    k.road([(15, 42), (11, 42)], width=2, salt=101)
    k.road([(8, 36), (6, 28), (6, 17), (10, 11)], width=3, salt=103)
    k.road([(12, 10), (24, 8), (35, 8)], width=3, salt=105)
    k.road([(35, 9), (37, 20), (37, 28)], width=3, salt=107)
    k.road([(27, 42), (31, 40)], width=2, salt=109)


def guy(k: Kit, a: tuple, b: tuple) -> None:
    """A guy wire from the mast down to its anchor: a run of chain stepping out as it falls."""
    (ax, ay, az), (bx, by, bz) = a, b
    for y in range(by, ay):
        t = (ay - y) / (ay - by)
        x, z = round(ax + (bx - ax) * t), round(az + (bz - az) * t)
        if k.name(x, y, z) is None:
            k.set(x, y, z, "iron_chain", {"axis": "y", "waterlogged": False})


def vents(k: Kit) -> None:
    """The intake and the exhaust over the plant room: a concrete collar, louvres, a mushroom cap of slabs; the
    generator's stack beside them, sooted at its mouth."""
    for (vx, vz) in (INTAKE, EXHAUST):
        k.fill(vx - 1, 0, vz - 1, vx + 1, 1, vz + 1, WALL)
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                k.set(vx + dx, 2, vz + dz, "iron_bars" if (dx, dz) != (0, 0) else AIR)
                k.slab(vx + dx, 3, vz + dz, "smooth_stone_slab")
        for y in (0, 1):
            k.set(vx, y, vz, AIR)
    sx, sz = STACK
    k.fill(sx - 1, 0, sz - 1, sx + 1, 0, sz + 1, WALL)
    for y in range(0, 6):
        k.set(sx, y, sz, "polished_blackstone_wall", WALL_POST)
    k.set(sx, 6, sz, "blackstone_wall", WALL_POST)


def hatch(k: Kit) -> None:
    """The escape tube's hatch: a concrete collar, its copper lid shut on the gravel."""
    hx, hz = HATCH
    k.fill(hx - 1, 0, hz - 1, hx + 1, 0, hz + 1, WALL)
    k.ground(hx, hz, "gravel")
    k.trapdoor(hx, 1, hz, "weathered_copper_trapdoor", N)


def helipad(k: Kit) -> None:
    """The helipad east of the berm: a concrete square, a yellow ring and a white H, moss in its joints and the grass
    over its edges; a floodlight on a pole at its corner."""
    cx, cz, r = 37, 35, 5
    for x in range(cx - r, cx + r + 1):
        for z in range(cz - r, cz + r + 1):
            edge = max(abs(x - cx), abs(z - cz)) == r
            if edge and k.n(x, 0, z, 2.0, 71) < 0.35:
                continue
            d = math.hypot(x - cx, z - cz)
            h = (abs(x - cx) == 2 and abs(z - cz) <= 2) or (z == cz and abs(x - cx) <= 2)
            v = k.n(x, 0, z, 3.0, 73)
            if h:
                k.ground(x, z, "white_concrete")
            elif 3.6 < d < 4.6 and v < 0.75:
                k.ground(x, z, "yellow_concrete")
            else:
                k.ground(x, z, "moss_block" if v > 0.8 else WALL)
    k.floodlight_pole(cx - r - 1, cz - r - 1, 5, S)


def yard(k: Kit) -> None:
    """The fence round the edge with its gate, the guard post's sandbags inside the gate."""
    e = SIZE - 1
    gate = lambda x, z: z == e and 16 <= x <= 26
    k.fence(0, 0, e, 0, fallen=0.15, salt=81)
    k.fence(0, 0, 0, e, fallen=0.2, salt=83)
    k.fence(e, 0, e, e, fallen=0.12, salt=85)
    k.fence(0, e, e, e, fallen=0.1, salt=87, skip=gate)
    for x in (15, 27):
        k.fill(x, 1, e, x, 3, e, I_BEAM, {"axis": "y"})
    k.set(14, 2, e, gb("keep_out_sign"), {"facing": S})
    # The guard post: a ring of sandbags two high (a slab course on top in places), its way in at the back.
    px0, pz0 = 2, 36
    for x in range(px0, px0 + 4):
        for z in range(pz0, pz0 + 4):
            if (x in (px0, px0 + 3) or z in (pz0, pz0 + 3)) and (x, z) != (px0 + 1, pz0):
                k.set(x, 1, z, SANDBAGS)
                k.set(x, 2, z, SANDBAG_SLAB if k.n(x, 2, z, 1.5, 89) > 0.3 else SANDBAGS)
    k.ground(px0 + 1, pz0 + 1, "coarse_dirt")
    k.ground(px0 + 2, pz0 + 2, "coarse_dirt")
    k.barrel(px0 + 2, 1, pz0 + 1, "up")
    k.debris((1, 1, 1, SIZE - 2, 1, SIZE - 2), [SANDBAG_SLAB, "gravel", RUSTED, "spruce_trapdoor"], 0.06, salt=91)
