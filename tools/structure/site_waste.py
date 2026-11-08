"""The Waste Storage, rebuilt (DESIGN.md §22.2): a repository mined in a salt bed, after WIPP (the Waste Isolation Pilot
Plant: drifts and disposal rooms cut from bedded salt, roof bolts in a grid, waste drums packed in rows and stacked to the
roof under magnesium-oxide sacks, full rooms closed with bulkheads, salt creeping in to close the rest), built to the
kit's rules (tools/structure/kit.py).

The salt in bands (halite white, clay seams pink-brown, greyer beds), not a speckle. The main drift east from the waste
shaft's station (its hoist cage at the foot of the shaft, sealed above by a concrete plug, the hoist's breakers on the
wall): a milled floor worn darker along the wheel tracks, roof bolts in a grid, strip lights along one rib under the
ventilation duct and its grilles, the cable and the water line (wall pipes, their valves) along the other. Rooms off it:
north, a full room closed by a bulkhead (steel frame, mesh, curtain, signs), the working room (drums at the back, the
forklift with a drum on its forks), a newly mined room standing empty, a room fallen in on its drums; south, behind
pressure doors, the decontamination room (showers, steel lockers, the suits, a crate of spare kit), the lead-lined hot
cell behind its window and manipulator arms (the operators' desks before it), the control room (its consoles under the
air monitors, the logbook, the shift supervisor's desk, the first-aid locker), each with its breaker on by the door and
its lamp lit; and a full room the salt is squeezing shut.

One template, 44 x 9 x 40, the floor at y 1, the rooms four high; a solid block of salt round them.
"""
from __future__ import annotations

from build import AIR, gb
from kit import BARBED, CHAIN_LINK, E, GRATING, I_BEAM, N, PANEL, RUSTED_PANEL, S, W, Kit

SIZE = (44, 9, 40)
F, ROOF = 1, 6                               # the floor, the roof (rooms y 2..5)
DRIFT = (6, 17, 41, 21)                      # the main drift: x0, z0, x1, z1
NORTH = [(8, 12), (16, 20), (24, 28), (32, 36)]   # the rooms' x ranges, north (z 4..16) and south (z 22..34) of it
NZ, SZ = (4, 16), (22, 34)
DRUM = gb("waste_drum")
LEAD, GLASS_R, RC, HAZARD = gb("lead_block"), gb("reactor_glass"), gb("reinforced_concrete"), gb("hazard_concrete")
SACK = "white_wool"                          # the magnesium-oxide super sacks laid on the drums


def waste_storage() -> Kit:
    k = Kit(*SIZE, seed=9_100)
    k.buried = True
    salt(k)
    drift(k)
    shaft(k)
    rooms = {}
    for i, (x0, x1) in enumerate(NORTH):
        rooms[f"n{i + 1}"] = (x0, NZ[0], x1, NZ[1])
        rooms[f"s{i + 1}"] = (x0, SZ[0], x1, SZ[1])
    for r in rooms.values():
        carve(k, r)
    full_room(k, rooms["n1"])
    bulkhead(k, rooms["n1"])
    working_room(k, rooms["n2"])
    empty_room(k, rooms["n3"])
    fallen_room(k, rooms["n4"])
    decontamination(k, rooms["s1"])
    hot_cell(k, rooms["s2"])
    control_room(k, rooms["s3"])
    creeping_room(k, rooms["s4"])
    face(k)
    return k


# --- the salt ----------------------------------------------------------------------------------------------------------------------
def salt_at(k: Kit, x: int, y: int, z: int) -> str:
    """Bedded salt: white halite, a pink-brown clay seam a block thick at y 4, greyer beds below the floor and above the
    roof; purer and darker lenses in clusters (noise), never a speckle."""
    v = k.n(x, y * 2.5, z, 4.0, 11)
    if y == 4:
        return "white_terracotta" if v > 0.3 else "calcite"
    if y <= 0 or y >= 7:
        return "diorite" if v > 0.42 else "calcite"
    if v > 0.74:
        return "smooth_quartz"                         # a lens of purer salt
    if v < 0.16:
        return "diorite"
    return "calcite"


def salt(k: Kit) -> None:
    for x in range(SIZE[0]):
        for y in range(SIZE[1]):
            for z in range(SIZE[2]):
                k.set(x, y, z, salt_at(k, x, y, z))


def carve(k: Kit, room: tuple, roof: int = ROOF) -> None:
    """A room cut from the salt: its floor milled flat, roof bolts in a grid, open to the drift."""
    x0, z0, x1, z1 = room
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            k.set(x, F, z, "polished_diorite" if k.n(x, 0, z, 3.0, 13) > 0.25 else "calcite")
            for y in range(F + 1, roof):
                k.set(x, y, z, AIR)
            if (x - x0) % 2 == 0 and (z - z0) % 2 == 1:
                k.set(x, roof - 1, z, "stone_button", {"face": "ceiling", "facing": N, "powered": False})


# --- the drift and the shaft --------------------------------------------------------------------------------------------------
def drift(k: Kit) -> None:
    """The main drift: five wide, four high; the milled floor worn darker along the wheel tracks; roof bolts; along the
    north rib the ventilation duct under the roof, a grille down out of it every six blocks, the strip lights under it;
    the power cable along the south, and the water line under it (wall pipes on the rib, a valve every ten blocks; not
    across the open mouth of the last room)."""
    x0, z0, x1, z1 = DRIFT
    carve(k, DRIFT)
    for x in range(x0, x1 + 1):
        for z in (z0 + 1, z1 - 1):
            if k.n(x, 1, z, 2.0, 15) > 0.2:
                k.set(x, F, z, "andesite" if k.n(x, 2, z, 1.5, 17) > 0.5 else "tuff")      # the tracks
        if x % 6 == 3:
            k.set(x, ROOF - 2, z0, "end_rod", {"facing": E})                              # a strip light
        elif x % 6 == 0:
            k.grille(x, ROOF - 2, z0, "down")
        k.set(x, ROOF - 1, z1, "iron_chain", {"axis": "x", "waterlogged": False})        # the cable
    k.duct(x0, ROOF - 1, z0, x1 - 1, ROOF - 1, z0)
    open_mouth = range(NORTH[3][0], NORTH[3][1] + 1)                                      # the creeping room's
    k.pipes((x0, z1), (open_mouth[0] - 1, z1), ROOF - 2, N, valves=[(x, z1) for x in range(x0 + 4, open_mouth[0], 10)])
    k.pipes((open_mouth[-1] + 1, z1), (x1 - 1, z1), ROOF - 2, N, valves=[(open_mouth[-1] + 2, z1)])


def shaft(k: Kit) -> None:
    """The waste shaft's station at the drift's west end: the hoist cage at the foot of the shaft (its floor grating,
    its sides mesh, its gate open to the drift), the shaft sealed above it by a plug of concrete; the hoist ropes."""
    for x in range(1, 6):
        for z in range(15, 24):
            k.set(x, F, z, "polished_diorite")
            for y in range(F + 1, ROOF + 1):
                k.set(x, y, z, AIR)
    cx0, cx1, cz0, cz1 = 2, 4, 17, 21
    for x in range(cx0, cx1 + 1):
        for z in range(cz0, cz1 + 1):
            k.set(x, F, z, GRATING)
            edge = x in (cx0, cx1) or z in (cz0, cz1)
            for y in range(F + 1, F + 4):
                if edge and not (x == cx1 and cz0 < z < cz1):
                    k.set(x, y, z, CHAIN_LINK)
            k.set(x, F + 4, z, PANEL if edge else GRATING)
            for y in range(F + 5, SIZE[1]):
                k.set(x, y, z, RC if y >= SIZE[1] - 2 else AIR)
    for x, z in ((cx0 + 1, cz0 + 1), (cx0 + 1, cz1 - 1)):
        for y in range(F + 5, SIZE[1] - 2):
            k.set(x, y, z, "iron_chain", {"axis": "y", "waterlogged": False})
    k.set(5, F + 2, 15, gb("radiation_sign"), {"facing": S})
    # The hoist's breakers on the south wall, a block apart (one left on beside another would have it look on), the hoist's
    # own on, the lamp over it lit; an extinguisher on the north wall.
    k.fuse_box(cx0, F + 2, 23, N)
    k.breaker(cx1, F + 2, 23, N)
    k.extinguisher(cx0, F + 2, 15, S, wall=True)


# --- the rooms -----------------------------------------------------------------------------------------------------------------
def stack(k: Kit, x: int, z: int, height: int = 3, sack: bool = True) -> None:
    """Drums three high on the floor, a sack laid on top where the stack is full."""
    for y in range(F + 1, F + 1 + height):
        k.set(x, y, z, DRUM)
    if sack and height == 3:
        k.set(x, F + 4, z, SACK)


def full_room(k: Kit, room: tuple) -> None:
    """Filled from the back to the front, drums three high to the roof under their sacks, a gap along one rib."""
    x0, z0, x1, z1 = room
    for x in range(x0, x1):
        for z in range(z0, z1 - 1):
            stack(k, x, z)


def bulkhead(k: Kit, room: tuple) -> None:
    """A full room closed off: a steel frame across its mouth, mesh over it, the ventilation curtain behind, signs."""
    x0, _, x1, z1 = room
    z = z1
    for y in range(F + 1, ROOF):
        k.set(x0, y, z, I_BEAM, {"axis": "y"})
        k.set(x1, y, z, I_BEAM, {"axis": "y"})
    for x in range(x0, x1 + 1):
        k.set(x, ROOF - 1, z, I_BEAM, {"axis": "x"})
    for x in range(x0 + 1, x1):
        for y in range(F + 1, ROOF - 1):
            k.set(x, y, z, CHAIN_LINK)
            k.set(x, y, z - 1, "light_gray_wool" if (x + y) % 3 else "white_wool")
    k.set(x0 + 1, F + 2, z + 1, gb("keep_out_sign"), {"facing": S})
    k.set(x1 - 1, F + 2, z + 1, gb("radiation_sign"), {"facing": S})


def working_room(k: Kit, room: tuple) -> None:
    """The room being filled when the work stopped: drums stacked at the back, the next row half laid, the forklift
    with a drum on its forks, a pallet of sacks."""
    x0, z0, x1, z1 = room
    for x in range(x0, x1 + 1):
        for z in range(z0, z0 + 5):
            stack(k, x, z)
    for x in range(x0, x1 + 1):
        if k.n(x, 0, z0 + 5, 2.0, 21) > 0.4:
            stack(k, x, z0 + 5, height=1 + int(k.n(x, 1, z0 + 5, 1.5, 23) * 2), sack=False)
    forklift(k, x0 + 2, z0 + 9)
    for x in (x0, x0 + 1):                                                                 # a pallet of sacks
        k.slab(x, F + 1, z1 - 2, "spruce_slab")
        k.set(x, F + 1, z1 - 3, SACK)


def forklift(k: Kit, x: int, z: int) -> None:
    """A forklift facing north (its forks toward the stack): a yellow body two wide and three long, black wheels at its
    corners, the overhead guard, the mast and its forks carrying a drum."""
    for dx in (0, 1):
        for dz in (0, 1, 2):
            k.set(x + dx, F + 1, z + dz, "black_concrete" if dz in (0, 2) else "yellow_concrete")
        k.set(x + dx, F + 2, z + 2, "yellow_concrete")                                     # the counterweight
        k.set(x + dx, F + 3, z + 2, "iron_bars")
        k.set(x + dx, F + 3, z, "iron_bars")
        k.set(x + dx, F + 4, z + 1, "iron_trapdoor", {"facing": N, "half": "bottom", "open": False, "powered": False,
                                                      "waterlogged": False})               # the overhead guard
    k.stairs(x, F + 2, z + 1, "spruce_stairs", S)                                          # the seat
    k.set(x + 1, F + 2, z + 1, "lever", {"face": "floor", "facing": N, "powered": False})
    for dx in (0, 1):
        for y in (F + 1, F + 2, F + 3):
            k.set(x + dx, y, z - 1, "iron_chain", {"axis": "y", "waterlogged": False})     # the mast
        k.trapdoor(x + dx, F + 1, z - 2, "iron_trapdoor", N)                               # the forks
    k.set(x, F + 2, z - 2, DRUM)


def empty_room(k: Kit, room: tuple) -> None:
    """Newly mined and never used: bare salt, the miner's last cut ragged at the back, salt fines along the ribs."""
    x0, z0, x1, z1 = room
    for x in range(x0, x1 + 1):
        for y in range(F + 1, ROOF):
            if k.n(x, y, z0, 1.6, 31) > 0.45:
                k.set(x, y, z0, salt_at(k, x, y, z0))
    for z in range(z0, z1 + 1):
        for x in (x0, x1):
            if k.n(x, 0, z, 2.0, 33) > 0.5:
                k.slab(x, F + 1, z, "diorite_slab")
    k.set((x0 + x1) // 2, F + 1, z1 - 2, "lantern", {"hanging": False, "waterlogged": False})


def fallen_room(k: Kit, room: tuple) -> None:
    """The roof fallen in on the drums: a heap of salt slabs and rubble rising to the broken roof, crushed drums
    showing through its foot, the roof above torn a block higher."""
    x0, z0, x1, z1 = room
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 - 2):
            stack(k, x, z, height=1, sack=False)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            h = (z1 - z) * 0.45 + (k.n(x, 0, z, 2.0, 41) - 0.5) * 2.0
            top = min(ROOF, F + 1 + int(h))
            for y in range(F + 1, top):
                if k.name(x, y, z) != DRUM or k.n(x, y, z, 1.5, 43) > 0.6:
                    k.set(x, y, z, "gravel" if k.n(x, y, z, 1.4, 45) > 0.7 else salt_at(k, x, y, z))
            if k.n(x, 9, z, 2.0, 47) > 0.45 and z < z1 - 1:
                k.set(x, ROOF, z, AIR)                                                     # the roof fallen out
    k.set(x0 - 1, F + 2, z1 + 1, gb("keep_out_sign"), {"facing": S})                   # on the pillar by its mouth


def decontamination(k: Kit, room: tuple) -> None:
    """Lined in concrete: the pressure door from the drift, showers over drains, steel lockers, the bench, the suits on
    their stands (two hazmat, one lead-lined), a crate of spare kit (the store's); the breaker by the door, its lamp lit,
    an extinguisher on the wall."""
    x0, z0, x1, z1 = room
    lining(k, room)
    k.pressure_door((x0 + x1) // 2, F + 1, z0, N)
    for z in (z0 + 3, z0 + 6):
        k.set(x0 + 1, ROOF - 1, z, "lightning_rod", {"facing": "down", "powered": False, "waterlogged": False})
        k.set(x0 + 1, F, z, GRATING)
    for z in range(z0 + 2, z0 + 8):
        k.slab(x1 - 1, F + 1, z, "smooth_stone_slab")
    for z in range(z0 + 9, z1):
        k.locker(x0 + 1, F + 1, z, E)
    k.marker(x1 - 1, F + 1, z0 + 8, "stand:hazmat:west")
    k.marker(x1 - 1, F + 1, z0 + 9, "stand:hazmat:west")
    k.marker(x1 - 1, F + 1, z0 + 10, "stand:lead:west")
    k.crates((x0 + x1) // 2, F + 1, z1 - 1, N, "waste_storage")
    k.breaker(x0 + 1, F + 2, z0 + 2, E)                  # on the side wall: beside the door its signal opens it
    k.extinguisher(x1 - 1, F + 2, z0 + 1, S, wall=True)
    k.bunker_lamp((x0 + x1) // 2, ROOF - 1, z0 + 5)


def lining(k: Kit, room: tuple) -> None:
    """A room lined in concrete against the salt: a skin a block in from the rock, a white floor."""
    x0, z0, x1, z1 = room
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            for y in range(F, ROOF):
                wall = x in (x0, x1) or z in (z0, z1) or y == F
                if wall:
                    k.set(x, y, z, "white_concrete" if y == F else "light_gray_concrete")
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            for y in range(F + 1, ROOF):
                k.set(x, y, z, AIR)


def hot_cell(k: Kit, room: tuple) -> None:
    """The hot cell: an operating gallery off the drift, the cell behind a lead wall two blocks thick with a window of
    reactor glass, the manipulators' arms through the wall over it and their handles before it, the operators' desks;
    inside, a waste drum opened on the bench, the store's second crate; pressure doors, the gallery's breaker on."""
    x0, z0, x1, z1 = room
    lining(k, room)
    k.pressure_door((x0 + x1) // 2, F + 1, z0, N)
    wall = z0 + 4
    for x in range(x0 + 1, x1):
        for y in range(F + 1, ROOF):
            k.set(x, y, wall, LEAD)
            k.set(x, y, wall + 1, LEAD)
    for x in range(x0 + 1, x1):
        for y in (F + 2, F + 3):
            k.set(x, y, wall, GLASS_R, {"formed": False})
            k.set(x, y, wall + 1, GLASS_R, {"formed": False})
    for x in (x0 + 1, x1 - 1):
        k.set(x, F + 4, wall, "lightning_rod", {"facing": S, "powered": False, "waterlogged": False})
        k.set(x, F + 4, wall + 1, "lightning_rod", {"facing": S, "powered": False, "waterlogged": False})
        k.set(x, F + 4, wall - 1, "lever", {"face": "wall", "facing": N, "powered": False})
    for x in range(x0 + 1, x1 - 1):
        k.desk(x, F + 1, wall - 1, N)                                                     # the operators' desks
    k.breaker(x0 + 1, F + 2, z0 + 2, E)                  # on the side wall, clear of the door
    # The cell: its own door from the gallery round the side (lead), the bench and the drum on it, the crate.
    for y in (F + 1, F + 2):
        k.set(x1 - 1, y, wall, AIR)
        k.set(x1 - 1, y, wall + 1, AIR)
    k.pressure_door(x1 - 1, F + 1, wall, S)
    k.set(x1 - 1, F + 1, wall + 1, AIR)
    for x in range(x0 + 1, x1 - 1):
        k.set(x, F + 1, wall + 3, RUSTED_PANEL)
    k.set(x0 + 2, F + 2, wall + 3, DRUM)
    k.crates(x0 + 1, F + 1, z1 - 1, E, "waste_storage")
    k.bunker_lamp((x0 + x1) // 2, ROOF - 1, z0 + 2)
    k.set((x0 + x1) // 2, ROOF - 1, wall + 4, "end_rod", {"facing": "down"})


def control_room(k: Kit, room: tuple) -> None:
    """The control room (and refuge): the consoles along the back under the air monitors' dead lamps, the logbook on its
    lectern, the supervisor's desk and chair, the records cabinet and the tape drive, the oxygen bottles racked, the
    first-aid locker; the breaker by the door, its lamp lit."""
    x0, z0, x1, z1 = room
    lining(k, room)
    k.pressure_door((x0 + x1) // 2, F + 1, z0, N)
    for x in range(x0 + 1, x1):
        k.console(x, F + 1, z1 - 1, N)
        k.set(x, F + 2, z1 - 1, "redstone_lamp" if x % 2 else "black_stained_glass", {"lit": False} if x % 2 else None)
        k.set(x, F + 3, z1 - 1, PANEL)
    k.set(x0 + 1, F + 1, z0 + 5, "lectern", {"facing": E, "has_book": False, "powered": False})
    for z in (z0 + 4, z0 + 5):
        k.desk(x1 - 1, F + 1, z, W)
    k.stairs(x1 - 2, F + 1, z0 + 4, "spruce_stairs", W)
    for z in (z0 + 7, z0 + 8):
        for y in (F + 1, F + 2):
            k.set(x0 + 1, y, z, "iron_chain", {"axis": "y", "waterlogged": False})       # oxygen bottles in their rack
        k.set(x0 + 1, F + 3, z, "lightning_rod", {"facing": "up", "powered": False, "waterlogged": False})
    k.locker(x1 - 1, F + 1, z1 - 3, W)                                                    # the first-aid locker
    k.cabinet(x1 - 1, F + 1, z0 + 2, W)
    k.tape_computer(x1 - 1, F + 1, z0 + 1, W)
    k.breaker(x0 + 1, F + 2, z0 + 2, E)                  # on the side wall, clear of the door
    k.bunker_lamp((x0 + x1) // 2, ROOF - 1, z0 + 5)


def creeping_room(k: Kit, room: tuple) -> None:
    """A full room the salt is closing: drums in rows to the roof, the floor heaved and the roof sagged at the back so
    the last rows are crushed under it."""
    x0, z0, x1, z1 = room
    for x in range(x0, x1 + 1):
        for z in range(z0 + 3, z1 + 1):
            squeeze = max(0, (z - (z1 - 5)))                                              # 0 at the front .. 5 at the back
            heave = 1 if squeeze >= 3 else 0
            sag = min(2, squeeze // 2)
            for y in range(F, F + 1 + heave):
                k.set(x, y, z, salt_at(k, x, y, z))
            for y in range(ROOF - sag, ROOF):
                k.set(x, y, z, salt_at(k, x, y, z))
            top = ROOF - sag
            for y in range(F + 1 + heave, top):
                k.set(x, y, z, DRUM)
            if top - (F + 1 + heave) >= 3 and k.name(x, top - 1, z) == DRUM:
                k.set(x, top - 1, z, SACK)
    k.set(x0 - 1, F + 2, z0 - 1, gb("radiation_sign"), {"facing": N})                  # on the pillar by its mouth


def face(k: Kit) -> None:
    """The drift's east end: the last cut, ragged, the miner's dust drifted at its foot."""
    x0, z0, x1, z1 = DRIFT
    for z in range(z0, z1 + 1):
        for y in range(F + 1, ROOF):
            if k.n(x1, y, z, 1.5, 51) > 0.5:
                k.set(x1, y, z, salt_at(k, x1, y, z))
        if k.n(x1 - 1, 0, z, 1.5, 53) > 0.45:
            k.slab(x1 - 1, F + 1, z, "diorite_slab")
