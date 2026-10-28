"""The Abandoned Missile Silo, rebuilt (DESIGN.md §22.2): a Titan II complex after the Minuteman and Titan II launch
sites, in three variants (intact, flooded, collapsed), built to the kit's rules (tools/structure/kit.py).

On the surface (`silo_surface_<variant>`): a gravel hardstand in its fence, the grass taking it back at its edges; over
the shaft the Rusted Large Silo Door on its rails, stuck where it rusted (an inch ajar on an intact silo, half open on a
flooded one over black water; on a collapsed one gone into the shaft, its rails and winch houses torn away, wreckage at
the shaft's mouth); the two exhaust vents beside it with their grilles; the access portal's blockhouse over the ladder
down; the antenna mast; the corrugated support building; floodlight poles at the door's ends; chain-link with barbed wire,
a double gate, the guard shack and the road in.

Underground (`silo_below_<variant>`, its top layer just under the ground): the shaft, reinforced concrete under the
door's opening, with work platforms folded against its walls, cable runs and lamps, the missile in it (white, a dark
band at the stage joint, the dark re-entry vehicle at its top, its engines over the flame deflector's W); a corrugated
cableway from the shaft to the blast lock under the portal (two blast doors), and the launch control centre north of
it, two storeys: the consoles, the two key panels far apart, the status board and the commander's desk below, bunks,
lockers and the galley above. Flooded: black water to the cableway, glow lichen above it. Collapsed: the door's leaves
and rubble down the shaft, the missile broken off at its stage joint and its top lying in the rubble, the cableway
caved in at one end.

Both templates 41 x 37 in plan with the shaft's axis at (20, 18); the surface 20 high, the complex 34.
"""
from __future__ import annotations

import math

from build import AIR, gb
from kit import (CHAIN_LINK, CORRUGATED, CORRUGATED_SLAB, CORRUGATED_STAIRS, E, FLOODLIGHT, GRATING, GRATING_SLAB, I_BEAM,
                 LATTICE, N, PANEL, RUSTED, RUSTED_PANEL, RUSTED_SLAB, S, W, Kit)
from nbt import Byte, Int

SURF = (41, 20, 37)
BELOW = (41, 34, 37)
AX, AZ = 20, 18                              # the shaft's axis in both templates
HALF = 3                                     # the shaft's inside: 7 x 7, AX-3..AX+3
TOP = BELOW[1] - 1                           # the complex's top layer (just under the ground)
FLOOR = 23                                   # the cableway's, blast lock's and control centre's floor (complex's y)
PORTAL = (5, 10, 15, 21)                     # the portal blockhouse over the blast lock: x0, x1, z0, z1
LADDER = (7, 20)                             # the ladder down from the portal (on the south wall, facing north)
DOOR_POS = {"intact": 34, "flooded": 80, "collapsed": None}
CONCRETE = "light_gray_concrete"


# --- the door ------------------------------------------------------------------------------------------------------------------
def eased(t: float) -> float:
    t = max(0.0, min(1.0, t))
    return t * t * (3 - 2 * t)


def silo_door(k: Kit, cx: int, y: int, cz: int, position: int, block: str = "rusted_large_silo_door") -> None:
    """The door laid as its item lays it (facing north: its leaves slide along x), its blocks' gap set where it is stuck
    ({position} of 160 open), the middle's block entity told so (SiloDoorBlockEntity)."""
    gap = round(eased(position / 160) * 4)
    for u in range(15):
        for v in range(-3, 4):
            centre = u == 7 and v == 0
            nbt = {"door_pos": Int(position), "from": Int(position), "stuck": Byte(1), "wanted": Byte(0)} if centre else None
            k.set(cx + u - 7, y, cz - v, gb(block), {"facing": N, "u": u, "gap": gap, "center": centre}, nbt=nbt)


# --- the surface ---------------------------------------------------------------------------------------------------------------
def silo_surface(variant: str) -> Kit:
    k = Kit(*SURF, seed=6_100 + len(variant))
    hardstand(k, variant)
    if DOOR_POS[variant] is not None:
        silo_door(k, AX, 1, AZ, DOOR_POS[variant])
    else:
        door_gone(k)
    vents(k)
    portal(k)
    support_building(k)
    k.lattice_tower(33, 28, 1, 1, 1, 12, ladder=False)
    for y in (11, 12):
        k.set(34, y, 28, "lightning_rod", {"facing": E, "powered": False, "waterlogged": False})
        k.set(32, y, 28, "lightning_rod", {"facing": W, "powered": False, "waterlogged": False})
    k.set(33, 13, 28, "lightning_rod", {"facing": "up", "powered": False, "waterlogged": False})
    k.floodlight_pole(11, AZ - 2, 7, E)
    k.floodlight_pole(29, AZ + 2, 7, W)
    perimeter(k)
    k.overgrow((0, 0, SURF[0] - 1, SURF[2] - 1), density=0.38)
    return k


def hardstand(k: Kit, variant: str) -> None:
    """Gravel inside the fence, worn to dirt where the trucks turned, the grass back in over its edges; concrete under
    the door's rails and round the shaft's mouth; the shaft open under the opening."""
    k.patch(3, 4, 37, 32, [(0.22, None), (0.3, "coarse_dirt"), (0.78, "gravel"), (0.88, "coarse_dirt"), (1.01, "gravel")],
            scale=4.5, salt=11, edge=0.9)
    k.pad(AX - 8, AZ - 4, AX + 8, AZ + 4, CONCRETE, cracked="andesite", salt=13)
    k.road([(AX, 36), (AX, 27)], width=4, salt=17)
    for x in range(AX - HALF, AX + HALF + 1):
        for z in range(AZ - HALF, AZ + HALF + 1):
            k.ground(x, z, AIR)
    if variant == "collapsed":
        # The mouth broken back where the door fell in: its rim cracked to rubble a block or two out.
        for x in range(AX - HALF - 2, AX + HALF + 3):
            for z in range(AZ - HALF - 2, AZ + HALF + 3):
                if max(abs(x - AX), abs(z - AZ)) > HALF and k.n(x, 0, z, 2.0, 19) > 0.45:
                    k.ground(x, z, "cobblestone" if k.n(x, 1, z, 1.5, 21) > 0.5 else "gravel")


def door_gone(k: Kit) -> None:
    """The door fallen into the shaft: torn rails (I-beams) and a winch house's wreck either side, sheet scattered."""
    for side in (-1, 1):
        for x in range(AX + side * (HALF + 1), AX + side * (HALF + 8), side):
            for z in (AZ - 2, AZ + 2):
                if k.n(x, 0, z, 2.0, 23) > 0.35:
                    k.set(x, 1, z, I_BEAM, {"axis": "x"})
        k.fill(AX + side * 7, 1, AZ - 1, AX + side * 7, 1, AZ + 1, RUSTED_PANEL)
    k.debris((AX - 9, 1, AZ - 6, AX + 9, 1, AZ + 6), [RUSTED_SLAB, "gravel", RUSTED_SLAB, "cobblestone"], 0.3, salt=27)


def vents(k: Kit) -> None:
    """The two exhaust vents north and south of the door: concrete boxes, a steel grille on top, soot round its mouth."""
    for z0 in (AZ - 9, AZ + 7):
        k.fill(AX - 2, 1, z0, AX + 2, 1, z0 + 2, CONCRETE)
        k.fill(AX - 1, 1, z0 + 1, AX + 1, 1, z0 + 1, AIR)
        k.fill(AX - 1, 2, z0 + 1, AX + 1, 2, z0 + 1, GRATING_SLAB, {"type": "bottom", "waterlogged": False})
        for x in (AX - 2, AX + 2):
            k.set(x, 2, z0 + 1, "smooth_stone_slab", {"type": "bottom", "waterlogged": False})
        k.set(AX, 1, z0 + 1, "black_concrete")


def portal(k: Kit) -> None:
    """The access portal's blockhouse over the ladder down to the blast lock: concrete, hazard-framed steel door, the
    ladder's hole in its floor; a stair rail of iron round the hole."""
    x0, x1, z0, z1 = PORTAL
    k.pad(x0 - 1, z0 - 1, x1 + 1, z1 + 1, CONCRETE, cracked="andesite", salt=31)
    k.walls(x0, 1, z0, x1, 4, z1, CONCRETE)
    k.fill(x0, 5, z0, x1, 5, z1, "smooth_stone")
    k.fill(x0 + 1, 1, z0 + 1, x1 - 1, 4, z1 - 1, AIR)
    ring(k, x0 - 1, z0 - 1, x1 + 1, z1 + 1, lambda x, z: k.set(x, 5, z, "smooth_stone_slab", {"type": "bottom", "waterlogged": False}))
    k.iron_door((x0 + x1) // 2, 1, z1, S)
    for y in (1, 2, 3):
        k.set((x0 + x1) // 2 - 1, y, z1, gb("hazard_concrete"))
        k.set((x0 + x1) // 2 + 1, y, z1, gb("hazard_concrete"))
    k.set((x0 + x1) // 2, 3, z1, gb("hazard_concrete"))
    lx, lz = LADDER
    k.ground(lx, lz, AIR)
    k.set(lx, 0, lz + 1, CONCRETE)
    k.set(lx, 0, lz, "ladder", {"facing": N, "waterlogged": False})
    for x, z in ((lx - 1, lz), (lx + 1, lz), (lx - 1, lz - 1), (lx, lz - 1), (lx + 1, lz - 1)):
        k.set(x, 1, z, "iron_bars")
    k.set((x0 + x1) // 2 + 2, 2, z1 + 1, gb("keep_out_sign"), {"facing": S})
    k.lantern(x0 + 2, 4, z0 + 2, hanging=True)


def support_building(k: Kit) -> None:
    """The soft support building: corrugated on a concrete slab, a low gable, a door and a window; inside, what the
    generator left."""
    x0, x1, z0, z1 = 26, 34, 6, 12
    k.pad(x0 - 1, z0 - 1, x1 + 1, z1 + 1, CONCRETE, cracked="andesite", salt=41)
    k.clad(x0, 1, z0, x1, 3, z1, salt=43, rust_courses=1.3)
    k.clear(x0 + 1, 1, z0 + 1, x1 - 1, 3, z1 - 1)
    mid = (z0 + z1) / 2
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            d = abs(z - mid)
            if d < 0.6:
                k.set(x, 5, z, CORRUGATED_SLAB, {"type": "bottom", "waterlogged": False})
                k.set(x, 4, z, CORRUGATED)
            else:
                k.set(x, 4 + (1 if d <= 2 else 0), z, CORRUGATED_STAIRS,
                      {"facing": S if z < mid else N, "half": "bottom", "shape": "straight", "waterlogged": False})
                if d <= 2:
                    k.set(x, 4, z, CORRUGATED)
    k.iron_door(x0 + 2, 1, z1, S)
    k.set(x1, 2, z0 + 3, "glass_pane" if k.n(x1, 2, z0 + 3, 2.0, 45) > 0.4 else AIR)
    k.fill(x0 + 4, 1, z0 + 2, x1 - 1, 1, z0 + 4, RUSTED_PANEL)
    k.fill(x0 + 4, 2, z0 + 2, x1 - 2, 2, z0 + 4, "black_concrete")
    k.barrel(x0 + 1, 1, z0 + 1, "up", "silo_control")
    k.barrel(x0 + 1, 2, z0 + 1, "up")


def perimeter(k: Kit) -> None:
    gate = lambda x, z: z == 34 and AX - 3 <= x <= AX + 3
    k.fence(1, 2, 39, 2, fallen=0.2, salt=51)
    k.fence(1, 2, 1, 34, fallen=0.22, salt=53)
    k.fence(39, 2, 39, 34, fallen=0.18, salt=55)
    k.fence(1, 34, 39, 34, fallen=0.12, salt=57, skip=gate)
    for x in (AX - 4, AX + 4):
        k.fill(x, 1, 34, x, 3, 34, I_BEAM, {"axis": "y"})
    # The double gate: one leaf of chain-link swung open into the yard, the other fallen.
    for z in range(30, 34):
        k.set(AX + 3, 1, z, CHAIN_LINK)
        k.set(AX + 3, 2, z, CHAIN_LINK)
    k.set(AX - 4, 2, 35, gb("keep_out_sign"), {"facing": S})
    k.set(AX + 4, 2, 35, gb("radiation_sign"), {"facing": S})
    # The guard shack inside the gate.
    sx0, sz0 = AX + 6, 29
    k.patch(sx0, sz0, sx0 + 3, sz0 + 3, [(1.01, "smooth_stone")])
    k.clad(sx0, 1, sz0, sx0 + 3, 3, sz0 + 3, salt=59, rust_courses=1.0)
    k.clear(sx0 + 1, 1, sz0 + 1, sx0 + 2, 3, sz0 + 2)
    k.fill(sx0 - 1, 4, sz0 - 1, sx0 + 4, 4, sz0 + 4, CORRUGATED_SLAB, {"type": "bottom", "waterlogged": False})
    k.iron_door(sx0, 1, sz0 + 1, W)
    k.set(sx0 + 1, 2, sz0 + 3, "glass_pane")


def ring(k: Kit, x0: int, z0: int, x1: int, z1: int, put) -> None:
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if x in (x0, x1) or z in (z0, z1):
                put(x, z)


# --- the complex ----------------------------------------------------------------------------------------------------------------
def silo_below(variant: str) -> Kit:
    k = Kit(*BELOW, seed=6_200 + len(variant))
    k.buried = True
    shaft(k, variant)
    missile(k, variant)
    cableway(k, variant)
    blast_lock(k)
    control_centre(k)
    if variant == "flooded":
        flood(k)
    return k


def concrete_wall(k: Kit, x: int, y: int, z: int, salt: int = 61) -> None:
    """Reinforced concrete: banded every six courses, stained and spalled to rough stone in patches."""
    v = k.n(x, y, z, 3.0, salt)
    k.set(x, y, z, "smooth_stone" if y % 6 == 0 else ("andesite" if v > 0.7 else "stone" if v > 0.62 else CONCRETE))


def inside(x: int, z: int) -> bool:
    """The shaft's round inside, seven across (a circle of 3.5 on the block grid)."""
    return (x - AX) ** 2 + (z - AZ) ** 2 <= 12.25


def shaft(k: Kit, variant: str) -> None:
    """A round shaft of reinforced concrete, squared off at its head to the door's opening (the collar the door's
    rails sit on)."""
    collar = TOP - 1
    for x in range(AX - HALF - 2, AX + HALF + 3):
        for z in range(AZ - HALF - 2, AZ + HALF + 3):
            d2 = (x - AX) ** 2 + (z - AZ) ** 2
            square = max(abs(x - AX), abs(z - AZ)) <= HALF
            for y in range(0, TOP + 1):
                open_ = y > 1 and (inside(x, z) or (y >= collar and square))
                if open_:
                    k.set(x, y, z, AIR)
                elif d2 <= 21 or (y >= collar - 1 and max(abs(x - AX), abs(z - AZ)) <= HALF + 1):
                    concrete_wall(k, x, y, z)
    # The flame deflector's W at the bottom.
    for x in range(AX - HALF, AX + HALF + 1):
        for z in range(AZ - 1, AZ + 2):
            if inside(x, z):
                k.set(x, 2, z, "andesite_stairs", {"facing": N if z < AZ else S if z > AZ else E, "half": "bottom",
                                                    "shape": "straight", "waterlogged": False})
    missile_cells = {(AX, AZ), (AX + 1, AZ), (AX - 1, AZ), (AX, AZ + 1), (AX, AZ - 1)}
    ring = [(x, z) for x in range(AX - HALF, AX + HALF + 1) for z in range(AZ - HALF, AZ + HALF + 1) if inside(x, z)]
    ledge = [(x, z) for (x, z) in ring if not all(inside(x + dx, z + dz) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))]
    # A work platform folded out round the missile at its stage joint (a few gratings rusted through), another folded
    # back to a ledge against the wall lower down.
    for (x, z) in ring:
        if (x, z) not in missile_cells and k.n(x, 16, z, 1.6, 63) > 0.12:
            k.set(x, 16, z, GRATING_SLAB, {"type": "top", "waterlogged": False})
    for (x, z) in ledge:
        if k.n(x, 9, z, 2.0, 64) > 0.15:
            k.set(x, 9, z, GRATING_SLAB, {"type": "top", "waterlogged": False})
    # The cableway's landing: grating at its level on the west side, joined to the tunnel.
    for x in (AX - HALF, AX - HALF + 1):
        for z in range(AZ - 1, AZ + 2):
            k.set(x, FLOOR, z, GRATING)
    # Cable runs down two of the wall's corners, lamps on the walls, the ladder up the west wall.
    for x, z in ((AX + 2, AZ - 2), (AX - 2, AZ + 2)):
        for y in range(3, TOP + 1):
            if k.name(x, y, z) in (None, AIR):
                k.set(x, y, z, "iron_chain", {"axis": "y", "waterlogged": False})
    for y, face in ((5, E), (11, S), (18, W), (25, N), (30, E)):
        x = {E: AX - HALF, W: AX + HALF, N: AX, S: AX}[face]
        z = {E: AZ, W: AZ, N: AZ + HALF, S: AZ - HALF}[face]
        k.set(x, y, z, FLOODLIGHT, {"facing": face})
    k.ladder(AX - 2, 3, collar - 1, AZ - 2, E)             # up to the collar (the wall it hangs on ends there)
    if variant == "collapsed":
        # The door's leaves wedged down the shaft at a slant, rubble heaped under them.
        for i in range(7):
            y = TOP - 2 - i
            k.set(AX - HALF + i, y, AZ - 1, RUSTED_PANEL)
            k.set(AX - HALF + i, y, AZ, RUSTED_PANEL)
            k.set(AX - HALF + i, y + 1, AZ + 1, I_BEAM, {"axis": "x"})
        for x in range(AX - HALF, AX + HALF + 1):
            for z in range(AZ - HALF, AZ + HALF + 1):
                h = 3 + round(3 * k.n(x, 0, z, 2.5, 65))
                for y in range(3, 3 + h):
                    if k.name(x, y, z) in (None, AIR):
                        k.set(x, y, z, "gravel" if (x + z + y) % 3 else "cobblestone")
        k.chest(AX + 2, 7, AZ + 2, W, "silo_shaft")
    else:
        k.chest(AX + HALF, 10, AZ, W, "silo_shaft")


def missile(k: Kit, variant: str) -> None:
    """A plus of five columns (it reads round at this size): engines over the deflector, the first stage to a dark band
    at the joint, the second stage, the re-entry vehicle tapering to its tip; grime in patches, not specks."""
    broken = variant == "collapsed"
    def body(y: int, block: str, plus: bool = True) -> None:
        for dx, dz in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)) if plus else ((0, 0),):
            x, z = AX + dx, AZ + dz
            # Grime a shade off the white (calcite), so the body keeps its outline against the shaft's grey.
            grime = k.n(x, y * 0.6, z, 2.5, 67) > 0.66
            k.set(x, y, z, "calcite" if block == "white_concrete" and grime else block)
    for dx in (-1, 1):
        k.set(AX + dx, 3, AZ, "polished_blackstone_wall")
    k.set(AX, 3, AZ, "polished_blackstone")
    top_first = 14
    for y in range(4, top_first):
        body(y, "white_concrete")
    body(top_first, "black_concrete")
    if broken:
        # Broken off at the joint: jagged white over the band; the upper stage and its warhead down in the rubble.
        k.set(AX, top_first + 1, AZ, "white_concrete")
        k.set(AX + 1, top_first + 1, AZ, "white_concrete")
        for x in range(AX - 2, AX + 3):
            k.set(x, 10, AZ - 2, "white_concrete")
        k.set(AX + 2, 10, AZ - 1, "polished_deepslate")
        return
    for y in range(top_first + 1, 24):
        body(y, "white_concrete")
    # The re-entry vehicle: a blunt dark cone, its shoulders stairs turned in to the middle, a spike at its tip.
    for y in (24, 25):
        body(y, "polished_deepslate")
    k.set(AX, 26, AZ, "polished_deepslate")
    for dx, dz, face in ((1, 0, W), (-1, 0, E), (0, 1, N), (0, -1, S)):
        k.set(AX + dx, 26, AZ + dz, "polished_deepslate_stairs", {"facing": face, "half": "bottom", "shape": "straight",
                                                                    "waterlogged": False})
    k.set(AX, 27, AZ, "polished_deepslate_wall")
    k.set(AX, 28, AZ, "lightning_rod", {"facing": "up", "powered": False, "waterlogged": False})


def cableway(k: Kit, variant: str) -> None:
    """A corrugated tube on I-beam frames from the shaft west to the blast lock: grating underfoot, a cable tray (chain)
    along its ceiling, a lamp every few frames; caved in at its lock end on a collapsed silo."""
    lx1 = PORTAL[1] + 1
    for x in range(lx1, AX - HALF):
        for z in range(AZ - 2, AZ + 3):
            for y in range(FLOOR, FLOOR + 5):
                wall = z in (AZ - 2, AZ + 2) or y in (FLOOR, FLOOR + 4)
                if wall:
                    frame = (x - lx1) % 3 == 0
                    k.set(x, y, z, I_BEAM if frame and y not in (FLOOR,) else (GRATING if y == FLOOR else
                          (RUSTED if y == FLOOR + 1 or k.n(x, y, z, 2.5, 71) > 0.72 else CORRUGATED)),
                          {"axis": "y" if z in (AZ - 2, AZ + 2) else "z"} if frame and y != FLOOR else None)
                else:
                    k.set(x, y, z, AIR)
        k.set(x, FLOOR + 3, AZ, "iron_chain", {"axis": "x", "waterlogged": False})
        if (x - lx1) % 4 == 2:
            k.lantern(x, FLOOR + 3, AZ - 1, hanging=True)
    for z in range(AZ - 1, AZ + 2):                    # through the shaft's wall
        for y in range(FLOOR + 1, FLOOR + 4):
            k.set(AX - HALF - 1, y, z, AIR)
    if variant == "collapsed":
        for x in range(lx1, lx1 + 3):
            for z in range(AZ - 1, AZ + 2):
                for y in range(FLOOR + 1, FLOOR + 1 + (3 - (x - lx1))):
                    k.set(x, y, z, "gravel" if (x + y + z) % 2 else "cobblestone")


def blast_lock(k: Kit) -> None:
    """Under the portal: the lock between two blast doors (east to the cableway, north to the control centre), the
    ladder down from the portal into it."""
    x0, x1, z0, z1 = PORTAL
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            for y in range(FLOOR, FLOOR + 6):
                if x in (x0, x1) or z in (z0, z1) or y in (FLOOR, FLOOR + 5):
                    concrete_wall(k, x, y, z, salt=73)
                else:
                    k.set(x, y, z, AIR)
    k.iron_door(x1, FLOOR + 1, AZ, E)
    for y in (FLOOR + 1, FLOOR + 2, FLOOR + 3):
        k.set(x1, y, AZ - 1, gb("hazard_concrete")), k.set(x1, y, AZ + 1, gb("hazard_concrete"))
    k.set(x1, FLOOR + 3, AZ, gb("hazard_concrete"))
    k.iron_door((x0 + x1) // 2, FLOOR + 1, z0, N)
    # The ladder from the portal down the column above the lock.
    lx, lz = LADDER
    for y in range(FLOOR + 1, TOP + 1):
        if y >= FLOOR + 5:
            for x in range(lx - 1, lx + 2):
                for z in range(lz - 1, lz + 2):
                    if (x, z) != (lx, lz):
                        concrete_wall(k, x, y, z, salt=75)
            k.set(lx, y, lz, AIR)
        k.set(lx, y, lz, "ladder", {"facing": N, "waterlogged": False})
    k.lantern(x0 + 1, FLOOR + 4, z0 + 1, hanging=True)


def control_centre(k: Kit) -> None:
    """The launch control centre north of the lock, two storeys inside a concrete box with its corners cut."""
    x0, x1, z0, z1 = 1, 13, 1, 12
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            corner = (x in (x0, x1)) and (z in (z0, z1))
            for y in range(FLOOR, FLOOR + 10):
                if corner:
                    continue
                edge = x in (x0, x1) or z in (z0, z1) or y in (FLOOR, FLOOR + 9) or (x in (x0 + 1, x1 - 1) and z in (z0 + 1, z1 - 1))
                if edge:
                    concrete_wall(k, x, y, z, salt=77)
                elif y == FLOOR + 5:
                    k.set(x, y, z, "smooth_stone")
                else:
                    k.set(x, y, z, AIR)
    # The way in from the lock.
    cx = (PORTAL[0] + PORTAL[1]) // 2
    for z in range(z1, PORTAL[2]):
        for y in (FLOOR + 1, FLOOR + 2):
            k.set(cx, y, z, AIR)
        for x in (cx - 1, cx + 1):
            for y in range(FLOOR + 1, FLOOR + 3):
                concrete_wall(k, x, y, z, salt=79)
        k.set(cx, FLOOR + 3, z, CONCRETE)
    # Below: the consoles along the north wall, the two key panels far apart, the status board, the commander's desk.
    for x in range(x0 + 2, x1 - 1):
        k.set(x, FLOOR + 1, z0 + 2, PANEL)
        k.set(x, FLOOR + 2, z0 + 2, "black_stained_glass" if x % 2 else "gray_stained_glass")
    for x in (x0 + 2, x1 - 2):
        k.set(x, FLOOR + 1, z0 + 5, PANEL)
        k.set(x, FLOOR + 2, z0 + 5, "lever", {"face": "floor", "facing": S, "powered": False})
    for z in range(z0 + 3, z1 - 2):
        k.set(x0 + 1, FLOOR + 2, z, "redstone_lamp", {"lit": False})
        k.set(x0 + 1, FLOOR + 3, z, "black_concrete")
    k.set(x1 - 3, FLOOR + 1, z1 - 3, "spruce_stairs", {"facing": N, "half": "bottom", "shape": "straight", "waterlogged": False})
    k.fill(x1 - 4, FLOOR + 1, z1 - 4, x1 - 2, FLOOR + 1, z1 - 4, "spruce_slab", {"type": "top", "waterlogged": False})
    k.chest(x1 - 2, FLOOR + 1, z0 + 3, W, "silo_control")
    k.ladder(x1 - 1, FLOOR + 1, FLOOR + 5, z1 - 3, W)       # up the east wall through the floor
    # Above: bunks, lockers, the galley.
    for z in (z0 + 2, z0 + 5):
        k.bed(x0 + 2, FLOOR + 6, z, E, "gray")
    for x in range(x0 + 5, x0 + 8):
        k.barrel(x, FLOOR + 6, z0 + 2, "up", "bunker_stores" if x == x0 + 6 else None)
    k.set(x1 - 3, FLOOR + 6, z0 + 2, "smoker", {"facing": S, "lit": False})
    k.set(x1 - 2, FLOOR + 6, z0 + 2, "cauldron")
    for y in (FLOOR + 4, FLOOR + 8):
        k.lantern((x0 + x1) // 2, y, (z0 + z1) // 2, hanging=True)
    k.cobwebs((x0 + 1, FLOOR + 1, z0 + 1, x1 - 1, FLOOR + 8, z1 - 1), 0.05)


def flood(k: Kit) -> None:
    """Black water up to the cableway's floor wherever the complex is open; glow lichen on the shaft above it."""
    level = FLOOR
    for (x, y, z) in list(k.each(0, 0, 0, BELOW[0] - 1, level, BELOW[2] - 1)):
        if k.name(x, y, z) == AIR:
            k.set(x, y, z, "water", {"level": 0})
    for y in range(level + 1, level + 6):
        for x in range(AX - HALF, AX + HALF + 1):
            for z in (AZ - HALF, AZ + HALF):
                if k.name(x, y, z) == AIR and k.n(x, y, z, 2.0, 81) > 0.6:
                    k.set(x, y, z, "glow_lichen", {"north": z == AZ - HALF, "south": z == AZ + HALF, "east": False, "west": False,
                                                    "up": False, "down": False, "waterlogged": False})
