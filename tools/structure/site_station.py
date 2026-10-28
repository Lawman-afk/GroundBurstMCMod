"""The Exotic Research Station, rebuilt (DESIGN.md §22.2): an outpost hung over the End's outer islands to study the
exotic matter of the void geodes, abandoned after its containment failed; built to the kit's rules (tools/structure/
kit.py).

A floating rock of end stone, a teardrop with its point down, veined with voidstone, exotic crystals grown out of its
sides and chorus on its rim, carries an octagonal deck of concrete edged in hazard stripes, railed. On it the
containment hall: a round steel drum under a white radome (the kit's, smoothed with its stairs and slabs), blown open on
its east side where the containment failed, the breach ragged, crystals erupting through it; inside, the broken
containment ring round a scorched pit full of crystals. North of it the laboratory and west of it the crew's module,
each joined to the hall by a covered way, steel with windows in rows, solar panels and railings on their roofs; the
Search Radar (stopped) on its lattice tower at the deck's south-east corner, the station's mark from afar; a cargo
corner of crates; and what the failure threw out, hanging in the void east of it: radome panels, steel, rock.

One template, 44 x 30 x 44, the deck at y 12 (the rock under it); the origin is the hall's middle on the deck.
"""
from __future__ import annotations

import math

from build import AIR, gb
from kit import CHAIN_LINK, E, GRATING, GRATING_SLAB, I_BEAM, LATTICE, N, PANEL, RADOME, RADOME_SLAB, RUSTED_PANEL, S, W, Kit

SIZE = (44, 30, 44)
C = 22                                       # the hall's middle (x, z)
D = 12                                       # the deck
HALL_R = 7.2
BREACH = (-0.35, 0.75)                       # the failed side: angles (radians, 0 east, +z south) the breach spans
RC, HAZARD, VOID = gb("reinforced_concrete"), gb("hazard_concrete"), gb("voidstone")
CASING = gb("machine_casing")
CRYSTALS = (gb("exotic_crystal_cluster"), gb("large_exotic_bud"), gb("small_exotic_bud"))


def research_station() -> Kit:
    k = Kit(*SIZE, seed=10_100)
    rock(k)
    deck(k)
    hall(k)
    module(k, (16, 5, 28, 10), door=(22, 10, S), name="lab")
    module(k, (5, 17, 10, 27), door=(10, 22, E), name="crew")
    covered_way(k, (21, 11, 23, 14))
    covered_way(k, (11, 21, 14, 23))
    radar(k)
    cargo(k)
    drift(k)
    return k


def angle_of(x: float, z: float) -> float:
    return math.atan2(z - C, x - C)


def in_breach(x: float, z: float, slack: float = 0.0) -> bool:
    a = angle_of(x, z)
    return BREACH[0] - slack < a < BREACH[1] + slack


# --- the rock ---------------------------------------------------------------------------------------------------------------------
def rock(k: Kit) -> None:
    """A teardrop of end stone under the deck, its point down, lumpy (its radius wanders with the angle and the height),
    veined with voidstone in clusters, obsidian toward its point; budding voidstone in its sides with crystals growing
    out sideways; chorus on the rim round the deck."""
    # Knobs swelling out of its sides (where it broke from a bigger island), at their own angles and heights.
    knobs = [(0.6, 7.0, 3.4), (2.3, 4.5, 2.8), (3.9, 8.0, 3.0), (5.2, 3.0, 2.4)]
    for y in range(0, D):
        t = (y + 1) / D
        base_r = 17.0 * t ** 1.1
        for x in range(SIZE[0]):
            for z in range(SIZE[2]):
                dx, dz = x + 0.5 - C, z + 0.5 - C
                a = math.atan2(dz, dx)
                lump = 0.74 + 0.46 * k.n(math.cos(a) * 3 + 50, y * 0.45, math.sin(a) * 3 + 50, 1.8, 11)
                inside = math.hypot(dx, dz) <= base_r * lump
                for ka, ky, kr in knobs:
                    kx, kz = (base_r * 0.8 + 1.0) * math.cos(ka), (base_r * 0.8 + 1.0) * math.sin(ka)
                    if (dx - kx) ** 2 + ((y - ky) * 1.3) ** 2 + (dz - kz) ** 2 <= kr * kr:
                        inside = True
                if not inside:
                    continue
                v = k.n(x * 0.7, y, z * 0.7, 6.0, 13)
                if y < 2:
                    block = "obsidian"
                elif v > 0.68:
                    block = VOID                                                   # a vein of voidstone
                else:
                    block = "end_stone"
                k.set(x, y, z, block)
    # Crystals out of the rock's sides: budding voidstone where the rock meets the void sideways, a crystal beside it.
    for x in range(1, SIZE[0] - 1):
        for z in range(1, SIZE[2] - 1):
            for y in range(1, D - 1):
                if k.name(x, y, z) is None:
                    continue
                for (dx, dz, face) in ((1, 0, E), (-1, 0, W), (0, 1, S), (0, -1, N)):
                    if k.name(x + dx, y, z + dz) is None and k.name(x + dx, y + 1, z + dz) is None and k.n(x, y, z, 1.3, 15) > 0.83:
                        k.set(x, y, z, gb("budding_voidstone"))
                        k.set(x + dx, y, z + dz, CRYSTALS[int(k.n(x, y, z, 1.1, 17) * 3) % 3], {"facing": face, "waterlogged": False})
                        break


# --- the deck -------------------------------------------------------------------------------------------------------------------
def in_deck(x: int, z: int) -> bool:
    dx, dz = abs(x + 0.5 - C), abs(z + 0.5 - C)
    return max(dx, dz) <= 15 and dx + dz <= 21


def deck(k: Kit) -> None:
    """An octagon of concrete on the rock, steel grating set in along its walks, a band of hazard stripes at its edge
    and a railing round it (gone where the failure tore it east); bare rock with chorus round it where the rock is
    wider; the deck itself broken off at its east edge."""
    for x in range(SIZE[0]):
        for z in range(SIZE[2]):
            if not in_deck(x, z):
                if k.name(x, D - 1, z) == "minecraft:end_stone" and k.n(x, 0, z, 2.0, 21) > 0.62:
                    k.set(x, D, z, "chorus_plant", {"down": True, "up": False, "north": False, "south": False, "east": False,
                                                    "west": False})
                    if k.n(x, 1, z, 1.5, 23) > 0.5:
                        k.set(x, D + 1, z, "chorus_flower", {"age": 5})
                continue
            if in_breach(x + 0.5, z + 0.5, 0.15) and math.hypot(x + 0.5 - C, z + 0.5 - C) > 12.5 and k.n(x, 2, z, 2.0, 25) > 0.4:
                continue                                                          # torn away
            dx, dz = abs(x + 0.5 - C), abs(z + 0.5 - C)
            edge = max(dx, dz) > 14 or dx + dz > 20
            k.set(x, D, z, HAZARD if edge else (GRATING if (x + z) % 9 == 0 and max(dx, dz) > 9 else RC))
            if edge and not in_breach(x + 0.5, z + 0.5, 0.25) and k.n(x, 3, z, 3.0, 27) > 0.2:
                k.set(x, D + 1, z, CHAIN_LINK)


# --- the containment hall -----------------------------------------------------------------------------------------------------
def hall(k: Kit) -> None:
    """The containment hall: a ring wall of steel five high on a hazard-striped foot, a band of slit windows, the white
    radome over it; blown open on the breach side, wall and dome, ragged; inside the broken containment ring round the
    scorched pit, crystals grown through it all."""
    top = D + 5
    for x in range(SIZE[0]):
        for z in range(SIZE[2]):
            r = math.hypot(x + 0.5 - C, z + 0.5 - C)
            if r > HALL_R:
                continue
            if r > HALL_R - 1.0:
                for y in range(D + 1, top + 1):
                    torn = in_breach(x + 0.5, z + 0.5) and y > D + 1 + int(2.5 * k.n(x, y, z, 1.5, 31))
                    if torn:
                        continue
                    k.set(x, y, z, HAZARD if y == D + 1 else ("glass_pane" if y == D + 3 and (x + z) % 3 == 0 else PANEL))
            else:
                for y in range(D + 1, top + 1):
                    k.set(x, y, z, AIR)
    missing = lambda x, y, z: in_breach(x + 0.5, z + 0.5, 0.1) and k.n(x, y, z, 1.8, 33) > 0.25 or k.n(x, y, z, 2.0, 35) > 0.88
    k.radome(C, top + 0.5, C, HALL_R + 0.3, top + 1, missing=missing)            # centred as the wall is
    # The pit: scorched to obsidian, crying obsidian and voidstone, a block down, crystals grown out of it.
    for x in range(C - 3, C + 4):
        for z in range(C - 3, C + 4):
            r = math.hypot(x + 0.5 - C, z + 0.5 - C)
            if r < 2.8:
                v = k.n(x, 0, z, 1.5, 37)
                k.set(x, D, z, AIR)
                k.set(x, D - 1, z, "crying_obsidian" if v > 0.6 else VOID if v > 0.35 else "obsidian")
                if v > 0.45:
                    k.set(x, D, z, CRYSTALS[0], {"facing": "up", "waterlogged": False})
            elif r < 3.6:
                k.set(x, D, z, "black_concrete" if k.n(x, 1, z, 1.5, 39) > 0.5 else RC)      # scorched round its lip
    # The containment ring: casings and copper coils round the pit, broken where it failed, a coil fallen.
    for i in range(32):
        a = 2 * math.pi * i / 32
        x, z = int(math.floor(C + 4.6 * math.cos(a))), int(math.floor(C + 4.6 * math.sin(a)))
        if BREACH[0] < a < BREACH[1] or BREACH[0] < a - 2 * math.pi < BREACH[1]:
            continue
        k.set(x, D + 1, z, CASING if i % 4 else "oxidized_cut_copper", {"formed": False} if i % 4 else None)
        if i % 4 == 0:
            k.set(x, D + 2, z, "oxidized_cut_copper")
            k.set(x, D + 3, z, "lightning_rod", {"facing": "up", "powered": False, "waterlogged": False})
    for (x, z) in ((C + 5, C + 1), (C + 4, C + 3)):
        k.set(x, D + 1, z, "exposed_cut_copper")                                           # what fell from the ring
    # Crystals erupting through the breach and up the inside of the wall there.
    for x in range(C, C + 9):
        for z in range(C - 4, C + 7):
            if in_breach(x + 0.5, z + 0.5) and k.name(x, D + 1, z) in (None, AIR) and k.n(x, 5, z, 1.4, 41) > 0.62:
                k.set(x, D, z, gb("budding_voidstone"))
                k.set(x, D + 1, z, CRYSTALS[int(k.n(x, 6, z, 1.2, 43) * 3) % 3], {"facing": "up", "waterlogged": False})
    # Floodlights on the wall turned on the pit, a lamp hung on its chain from the dome's crown.
    for a in (2.2, 3.4, 4.6):
        x, z = int(math.floor(C + 5.6 * math.cos(a))), int(math.floor(C + 5.6 * math.sin(a)))
        ox, oz = -math.cos(a), -math.sin(a)
        face = (E if ox > 0 else W) if abs(ox) >= abs(oz) else (S if oz > 0 else N)
        k.set(x, D + 4, z, gb("floodlight"), {"facing": face})
    for y in range(D + 7, top + 7):
        if k.name(C, y, C) in (None, AIR):
            k.set(C, y, C, "iron_chain", {"axis": "y", "waterlogged": False})
    k.set(C, D + 6, C, gb("floodlight"), {"facing": "down"})
    k.iron_door(C, D + 1, C - 7, N)                                                        # from the lab's covered way
    k.iron_door(C - 7, D + 1, C, W)                                                        # from the crew's


# --- the modules ----------------------------------------------------------------------------------------------------------------
def module(k: Kit, box: tuple, door: tuple, name: str) -> None:
    """A steel module on the deck: walls of steel panel on a rusted foot, a band of windows, a flat roof with a
    railing and solar panels; inside, its work."""
    x0, z0, x1, z1 = box
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            ring = x in (x0, x1) or z in (z0, z1)
            for y in range(D + 1, D + 5):
                if ring:
                    k.set(x, y, z, RUSTED_PANEL if y == D + 1 and k.n(x, y, z, 2.0, 51) > 0.45 else PANEL)
                else:
                    k.set(x, y, z, AIR)
            k.set(x, D + 5, z, PANEL)
            if ring and k.n(x, 9, z, 3.0, 53) > 0.15:
                k.set(x, D + 6, z, CHAIN_LINK)
            elif not ring and (x + z) % 2 == 0:
                k.set(x, D + 6, z, "daylight_detector", {"inverted": False, "power": 0})          # solar panels
    if x1 - x0 > z1 - z0:
        k.windows(N, x0, z0, x1, z0, D + 3, every=2)
        k.windows(S, x0, z1, x1, z1, D + 3, every=2)
    else:
        k.windows(W, x0, z0, x0, z1, D + 3, every=2)
        k.windows(E, x1, z0, x1, z1, D + 3, every=2)
    dx_, dz_, face = door
    k.iron_door(dx_, D + 1, dz_, face)
    if name == "lab":
        # The laboratory: benches of machine casings along the back, the containment cell under study on one, the
        # notes' lectern, a chest of samples; a light.
        for x in range(x0 + 1, x1):
            if x != (x0 + x1) // 2:
                k.set(x, D + 1, z0 + 1, CASING, {"formed": False})
        k.set(x0 + 3, D + 2, z0 + 1, gb("reactor_glass"), {"formed": False})
        k.set(x0 + 3, D + 3, z0 + 1, CRYSTALS[2], {"facing": "up", "waterlogged": False})
        k.set(x1 - 2, D + 1, z1 - 1, "lectern", {"facing": N, "has_book": False, "powered": False})
        k.chest(x0 + 1, D + 1, z1 - 1, E, "research_station")
        k.set((x0 + x1) // 2, D + 4, (z0 + z1) // 2, "end_rod", {"facing": "down"})
    else:
        # The crew's module: bunks, lockers, a table; the second chest.
        for z in (z0 + 1, z0 + 4):
            k.bed(x0 + 2, D + 1, z, W, "light_gray")
        for z in range(z0 + 6, z1):
            k.trapdoor(x0 + 1, D + 1, z, "iron_trapdoor", E, open_=True)
            k.trapdoor(x0 + 1, D + 2, z, "iron_trapdoor", E, open_=True)
        k.slab(x1 - 2, D + 1, z0 + 7, "smooth_stone_slab", "top")
        k.chest(x1 - 1, D + 1, z1 - 1, W, "research_station")
        k.set((x0 + x1) // 2, D + 4, (z0 + z1) // 2, "end_rod", {"facing": "down"})


def covered_way(k: Kit, box: tuple) -> None:
    """A short covered way between a module and the hall: grating underfoot, steel sides, a roof of slabs."""
    x0, z0, x1, z1 = box
    along_z = (z1 - z0) >= (x1 - x0)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            side = (x in (x0, x1)) if along_z else (z in (z0, z1))
            k.set(x, D, z, GRATING)
            for y in range(D + 1, D + 4):
                k.set(x, y, z, PANEL if side else AIR)
            k.set(x, D + 4, z, PANEL)


# --- the radar, the cargo corner, the drift ------------------------------------------------------------------------------------
def radar(k: Kit) -> None:
    """The Search Radar, stopped, on a lattice tower at the deck's south-east corner (its reflector out over the
    void): the station's mark from afar; a ladder up the tower, its cable down."""
    x, z = 32, 31
    k.lattice_tower(x - 1, z - 1, 2, 2, D + 1, D + 7, ladder=False)
    k.set(x, D + 8, z, gb("search_radar"), {"facing": S, "active": False})
    k.ladder(x - 2, D + 1, D + 7, z, W)
    for y in range(D + 1, D + 8):
        k.set(x + 1, y, z - 1, "iron_chain", {"axis": "y", "waterlogged": False})


def cargo(k: Kit) -> None:
    """Crates where the supply craft unloaded, in the deck's south-west corner: a stack under a tarp, a drum, a
    floodlight on the hall's side turned on them."""
    for x, z, h in ((12, 29, 2), (13, 29, 1), (12, 30, 1), (15, 30, 1)):
        for y in range(D + 1, D + 1 + h):
            k.barrel(x, y, z, "up")
        k.set(x, D + 1 + h, z, "purple_carpet")
    k.set(14, D + 1, 31, gb("waste_drum"))


def drift(k: Kit) -> None:
    """What the failure threw out, hanging in the void east of the hall where it stopped: radome panels, steel, a
    beam, rock with a crystal on it."""
    pieces = [((38, D + 6, 20), RADOME), ((40, D + 3, 25), PANEL), ((37, D + 9, 27), RADOME_SLAB), ((41, D - 2, 18), "end_stone"),
              ((36, D + 2, 31), RADOME), ((39, D + 11, 22), PANEL), ((42, D + 5, 29), "end_stone")]
    for (x, y, z), block in pieces:
        props = {"type": "bottom", "waterlogged": False} if "slab" in block else None
        k.set(x, y, z, block, props)
        if k.n(x, y, z, 1.3, 61) > 0.4:
            k.set(x + 1, y, z, block, props)
    k.set(41, D - 1, 18, CRYSTALS[1], {"facing": "up", "waterlogged": False})
    for x in range(35, 39):
        k.set(x, D + 4, 24, I_BEAM, {"axis": "x"})
