"""The Radar Outpost, rebuilt (DESIGN.md §22.2): an abandoned Cold War radar station, after the DEW Line's stations and
the air-defence stations inland, built to the kit's rules (tools/structure/kit.py).

Its landmark is the radar tower in the north-west: concrete, three storeys with band courses and a ring of windows round
its radar room, a deck oversailing its top on corbels, and on the deck a radome of panels, its weather side blown out.
Across the yard the search radar's lattice tower stands empty, its antenna (the Wrecked Search Radar) lying in the grass
south of it. A module train of three corrugated modules on piers, joined by short corridors, runs along the south-east
(operations, quarters, the generator with its stack); a tropospheric-scatter billboard on lattice legs faces south-west,
a corner of it fallen. Utility poles carry the wire from the generator to the tower; floodlights on poles at the gate and
in the yard; chain-link round it all with barbed wire along its top, runs of it fallen; an I-beam gate with a guard hut
by it; a gravel road in from the gate, its verges grassed over.

Template: 46 x 26 x 44 (x east, y up, z south); layer 0 the land's top (kit.py).
"""
from __future__ import annotations

import math

from build import AIR, gb
from kit import (BARBED, CORRUGATED, CORRUGATED_SLAB, CORRUGATED_STAIRS, E, FLOODLIGHT, GRATING, GRATING_SLAB, I_BEAM, LATTICE,
                 N, PANEL, RADOME, RADOME_SLAB, RUSTED, RUSTED_PANEL, RUSTED_SLAB, S, W, Kit)

SIZE = (46, 26, 44)
CONCRETE = "light_gray_concrete"
TRIM = "polished_andesite"
HAZARD = gb("hazard_concrete")
TOWER = (3, 3, 11, 11)                      # x0, z0, x1, z1
DECK_Y = 13
RADOME_C = (7.5, 16.4, 7.5)
RADOME_R = 5.7


def radar_outpost() -> Kit:
    k = Kit(*SIZE, seed=4_407)
    yard(k)
    radar_tower(k)
    search_radar_tower(k)
    module_train(k)
    billboard(k)
    power_line(k)
    perimeter(k)
    finish(k)
    return k


# --- the ground --------------------------------------------------------------------------------------------------------------
def yard(k: Kit) -> None:
    """Gravel worn to dirt in the middle of the compound, ragged at its edges, and the road in from the gate."""
    k.patch(13, 13, 31, 25, [(0.28, None), (0.62, "gravel"), (0.82, "coarse_dirt"), (1.01, "gravel")], scale=4.0, salt=11, edge=0.9)
    k.road([(22, 43), (22, 22), (20, 19), (14, 16), (8, 13)], width=3)
    k.road([(22, 24), (23, 26)], width=3, salt=37)
    k.pad(TOWER[0] - 1, TOWER[1] - 1, TOWER[2] + 1, TOWER[3] + 1)


# --- the radar tower -----------------------------------------------------------------------------------------------------------
def radar_tower(k: Kit) -> None:
    x0, z0, x1, z1 = TOWER
    cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
    # Walls: concrete, dirtier and patched with andesite low down; pilasters at the corners and the middles of its sides.
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if x not in (x0, x1) and z not in (z0, z1):
                continue
            pilaster = x in (x0, cx, x1) and z in (z0, cz, z1) or (x in (x0, x1) and z in (z0, z1))
            for y in range(1, DECK_Y):
                low = y <= 2 and k.n(x, y, z, 2.5, 3) > 0.55
                k.set(x, y, z, TRIM if pilaster else ("andesite" if low else CONCRETE))
    # Band courses between the storeys, set out a slab beyond the wall: they cast a line of shadow round the tower.
    for y in (4, 8):
        ring(k, x0 - 1, z0 - 1, x1 + 1, z1 + 1, lambda x, z: k.set(x, y, z, "smooth_stone_slab", {"type": "top", "waterlogged": False}))
    # Floors and the deck (oversailing on corbels: upside-down stairs under its edge).
    for y in (4, 8):
        k.fill(x0 + 1, y, z0 + 1, x1 - 1, y, z1 - 1, "smooth_stone")
    k.fill(x0 - 1, DECK_Y, z0 - 1, x1 + 1, DECK_Y, z1 + 1, "smooth_stone")
    ring(k, x0 - 1, z0 - 1, x1 + 1, z1 + 1, lambda x, z: corbel(k, x, DECK_Y - 1, z, x0, z0, x1, z1))
    # Windows: a band round the radar room, single lights below; recessed by the wall's own thickness.
    for face, a, b in ((N, (x0, z0), (x1, z0)), (S, (x0, z1), (x1, z1)), (W, (x0, z0), (x0, z1)), (E, (x1, z0), (x1, z1))):
        for t in (1, 2, 3, 5, 6, 7):
            x = a[0] + (b[0] - a[0]) * t // 8
            z = a[1] + (b[1] - a[1]) * t // 8
            for y in (9, 10):
                k.set(x, y, z, AIR if k.n(x, y, z, 2.5, 21) < 0.3 else "glass_pane")
        for t in (2, 6):
            x = a[0] + (b[0] - a[0]) * t // 8
            z = a[1] + (b[1] - a[1]) * t // 8
            k.set(x, 6, z, AIR if k.n(x, 6, z, 2.5, 23) < 0.35 else "glass_pane")
    # The door on the south, in a frame of hazard stripes; a floodlight over it; the step a slab.
    k.set(cx - 1, 1, z1, HAZARD), k.set(cx + 1, 1, z1, HAZARD)
    k.set(cx - 1, 2, z1, HAZARD), k.set(cx + 1, 2, z1, HAZARD), k.set(cx, 3, z1, HAZARD)
    k.set(cx - 1, 3, z1, HAZARD), k.set(cx + 1, 3, z1, HAZARD)
    k.iron_door(cx, 1, z1, S)
    k.set(cx, 3, z1 + 1, FLOODLIGHT, {"facing": S})
    k.set(cx + 2, 2, z1 + 1, gb("keep_out_sign"), {"facing": S})
    # Inside: a ladder up the north-west corner through every floor to the deck hatch.
    k.ladder(x0 + 1, 1, DECK_Y - 1, z0 + 1, S)
    for y in (4, 8, DECK_Y):
        k.set(x0 + 1, y, z0 + 1, AIR)
    k.set(x0 + 1, DECK_Y, z0 + 1, "iron_trapdoor", {"facing": S, "half": "top", "open": True, "powered": False, "waterlogged": False})
    # Ground floor: the plant (a switchboard, cable trays); first floor: the crew's room; the radar room's consoles.
    k.fill(x0 + 1, 1, z1 - 1, x0 + 3, 2, z1 - 1, PANEL)
    k.set(x0 + 2, 2, z1 - 2, "lever", {"face": "wall", "facing": N, "powered": False})
    k.barrel(x1 - 1, 1, z0 + 1, "up", "radar_outpost")
    k.bed(x1 - 2, 5, z0 + 1, S, "gray")
    k.set(x1 - 1, 5, z1 - 1, "smoker", {"facing": W, "lit": False})
    for x in range(x0 + 2, x1 - 1):                    # the consoles along the north wall: panels with screens over them
        k.set(x, 9, z0 + 1, PANEL)
        k.set(x, 10, z0 + 1, "black_stained_glass" if x % 2 else "gray_stained_glass")
    k.set(cx, 9, z0 + 2, "lever", {"face": "floor", "facing": S, "powered": False})
    k.chest(x1 - 1, 9, z1 - 1, W, "radar_outpost")
    for y in (3, 7, 11):
        k.lantern(cx, y, cz, hanging=True)
    # On the deck: the radome, its west (weather) side blown out; inside, the antenna on its pedestal.
    def blown(x, y, z):
        return x <= cx - 2 and y >= DECK_Y + 3 and k.n(x, y, z, 2.2, 41) > 0.42
    k.radome(*RADOME_C, RADOME_R, DECK_Y + 1, missing=blown)
    k.fill(cx, DECK_Y + 1, cz, cx, DECK_Y + 3, cz, LATTICE)
    k.fill(cx - 2, DECK_Y + 4, cz, cx + 2, DECK_Y + 4, cz, I_BEAM, {"axis": "x"})
    for x in (cx - 2, cx + 2):
        k.fill(x, DECK_Y + 5, cz, x, DECK_Y + 6, cz, PANEL)
    k.fill(cx - 1, DECK_Y + 5, cz + 1, cx + 1, DECK_Y + 7, cz + 1, PANEL)
    # Panels blown off lie on the deck and the ground below the gap.
    k.debris((x0 - 1, DECK_Y + 1, z0, cx - 1, DECK_Y + 1, z1), [RADOME_SLAB, RADOME], 0.35, salt=43, on_ground=False)
    k.debris((x0 - 4, 1, z0 - 1, x0 - 1, 1, z1 + 2), [RADOME_SLAB, RADOME, RADOME_SLAB], 0.4, salt=47)


def ring(k: Kit, x0: int, z0: int, x1: int, z1: int, put) -> None:
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if x in (x0, x1) or z in (z0, z1):
                put(x, z)


def corbel(k: Kit, x: int, y: int, z: int, x0: int, z0: int, x1: int, z1: int) -> None:
    """An upside-down stair under the deck's oversailing edge, its high side to the wall."""
    face = N if z < z0 else S if z > z1 else W if x < x0 else E
    k.set(x, y, z, "polished_andesite_stairs",
          {"facing": {N: S, S: N, W: E, E: W}[face], "half": "top", "shape": "straight", "waterlogged": False})


# --- the search radar --------------------------------------------------------------------------------------------------------------
def search_radar_tower(k: Kit) -> None:
    """Its lattice tower three across, a grating deck on top with nothing on its mount but a girder stub; the antenna
    lies in the grass south of it where it came down (the reflector behind its head, to the south)."""
    k.lattice_tower(37, 3, 3, 3, 1, 8)
    k.fill(36, 9, 2, 40, 9, 6, GRATING)
    for x in range(36, 41):
        for z in range(2, 7):
            if x in (36, 40) or z in (2, 6):
                k.set(x, 10, z, GRATING_SLAB, {"type": "bottom", "waterlogged": False}) if k.n(x, 10, z, 2.0, 61) > 0.3 else None
    k.set(38, 10, 4, I_BEAM, {"axis": "y"})
    k.set(38, 11, 4, RUSTED_PANEL)
    k.ladder(38, 1, 8, 2, N)
    k.set(38, 9, 2, AIR)
    k.set(38, 1, 8, gb("search_radar_wreck"), {"facing": N})


# --- the module train ------------------------------------------------------------------------------------------------------------
MODULES = ((24, 28), (30, 34), (36, 40))           # x spans; each runs z 22..30
MZ0, MZ1, FLOOR = 22, 30, 2


def module_train(k: Kit) -> None:
    for i, (x0, x1) in enumerate(MODULES):
        # Piers: a column of andesite under each corner and every three blocks along the long sides; the floor over them.
        for x in range(x0, x1 + 1, 2):
            for z in (MZ0, MZ0 + 4, MZ1):
                k.footing(x, z, "polished_andesite")
                k.set(x, 1, z, TRIM)
        k.fill(x0, FLOOR, MZ0, x1, FLOOR, MZ1, PANEL)
        k.clad(x0, FLOOR + 1, MZ0, x1, FLOOR + 3, MZ1, salt=91 + i, rust_courses=1.2)
        k.clear(x0 + 1, FLOOR + 1, MZ0 + 1, x1 - 1, FLOOR + 3, MZ1 - 1)
        roof(k, x0, x1, salt=97 + i, hole=(i == 1))
        k.windows(S, x0, MZ1, x1, MZ1, FLOOR + 2, every=2, salt=103 + i)
        k.windows(N, x0, MZ0, x1, MZ0, FLOOR + 2, every=2, salt=107 + i)
    for xa, xb in ((28, 30), (34, 36)):                 # the corridors between them
        x = xa + 1
        k.set(x, 1, MZ0 + 4, TRIM)
        k.footing(x, MZ0 + 4, "polished_andesite")
        k.fill(x, FLOOR, MZ0 + 3, x, FLOOR, MZ0 + 5, PANEL)
        k.fill(x, FLOOR + 1, MZ0 + 2, x, FLOOR + 3, MZ0 + 2, CORRUGATED)
        k.fill(x, FLOOR + 1, MZ0 + 6, x, FLOOR + 3, MZ0 + 6, CORRUGATED)
        k.fill(x, FLOOR + 4, MZ0 + 2, x, FLOOR + 4, MZ0 + 6, CORRUGATED_SLAB, {"type": "bottom", "waterlogged": False})
        k.clear(xa, FLOOR + 1, MZ0 + 3, xb, FLOOR + 2, MZ0 + 5)
    # The way in: the west end's door up two steps.
    x0 = MODULES[0][0]
    k.iron_door(x0, FLOOR + 1, MZ0 + 4, W)
    k.set(x0 - 1, FLOOR, MZ0 + 4, "smooth_stone_slab", {"type": "top", "waterlogged": False})
    k.set(x0 - 2, 1, MZ0 + 4, "stone_brick_stairs", {"facing": E, "half": "bottom", "shape": "straight", "waterlogged": False})
    k.footing(x0 - 2, MZ0 + 4, "gravel")
    k.set(x0 - 1, FLOOR + 3, MZ0 + 3, FLOODLIGHT, {"facing": W})
    # Inside: operations (consoles), quarters (bunks, lockers, a table), the generator (its frame and drums).
    a0, a1 = MODULES[0]
    for z in range(MZ0 + 1, MZ0 + 4):
        k.set(a1 - 1, FLOOR + 1, z, PANEL)
        k.set(a1 - 1, FLOOR + 2, z, "black_stained_glass")
    k.set(a1 - 2, FLOOR + 1, MZ0 + 2, "lever", {"face": "floor", "facing": W, "powered": False})
    b0, b1 = MODULES[1]
    for z in (MZ0 + 1, MZ1 - 1):
        k.bed(b0 + 1, FLOOR + 1, z, E, "gray")
    k.barrel(b1 - 1, FLOOR + 1, MZ0 + 1, "up", "bunker_stores")
    k.barrel(b1 - 1, FLOOR + 2, MZ0 + 1, "up")
    k.set(b0 + 2, FLOOR + 1, MZ0 + 4, "spruce_fence")
    k.set(b0 + 2, FLOOR + 2, MZ0 + 4, "spruce_pressure_plate")
    c0, c1 = MODULES[2]
    k.fill(c0 + 1, FLOOR + 1, MZ0 + 3, c1 - 1, FLOOR + 1, MZ0 + 5, RUSTED_PANEL)
    k.fill(c0 + 1, FLOOR + 2, MZ0 + 3, c1 - 2, FLOOR + 2, MZ0 + 5, "black_concrete")
    k.fill(c0 + 1, FLOOR + 3, MZ0 + 4, c1 - 2, FLOOR + 3, MZ0 + 4, I_BEAM, {"axis": "x"})
    for z in (MZ0 + 1, MZ1 - 1):
        k.barrel(c1 - 1, FLOOR + 1, z, "up", "radar_outpost" if z == MZ0 + 1 else None)
    # The generator's stack through its roof, blackened at the top.
    sx, sz = c1 - 1, MZ0 + 2
    for y in range(FLOOR + 4, FLOOR + 9):
        k.set(sx, y, sz, "polished_deepslate_wall")
    k.set(sx, FLOOR + 9, sz, "blackstone_wall")
    for y in range(FLOOR + 1, FLOOR + 4):
        k.set(sx, y, sz, "polished_deepslate_wall")


def roof(k: Kit, x0: int, x1: int, salt: int, hole: bool) -> None:
    """A low gable of corrugated sheet, ridge along x; {hole}: a stretch fallen in, its sheets on the floor below."""
    y = FLOOR + 4
    for x in range(x0 - 1, x1 + 2):
        if hole and x0 + 1 <= x <= x1 - 1 and k.n(x, y, MZ0 + 4, 2.0, salt) > 0.35:
            k.debris((x, FLOOR + 1, MZ0 + 2, x, FLOOR + 1, MZ1 - 2), [RUSTED_SLAB, CORRUGATED_SLAB], 0.7, salt=salt + 5, on_ground=False)
            for z in (MZ0 - 1, MZ0, MZ0 + 1, MZ1 - 1, MZ1, MZ1 + 1):
                stair(k, x, y, z)
            continue
        for z in range(MZ0 - 1, MZ1 + 2):
            stair(k, x, y, z)


def stair(k: Kit, x: int, y: int, z: int) -> None:
    mid = (MZ0 + MZ1) / 2
    if abs(z - mid) < 0.6:
        k.set(x, y + 1, z, CORRUGATED_SLAB, {"type": "bottom", "waterlogged": False})
        k.set(x, y, z, CORRUGATED)
    elif abs(z - mid) <= 2.5:
        k.set(x, y + 1 if abs(z - mid) <= 1.5 else y, z, CORRUGATED_STAIRS,
              {"facing": S if z < mid else N, "half": "bottom", "shape": "straight", "waterlogged": False})
        if abs(z - mid) <= 1.5:
            k.set(x, y, z, CORRUGATED)
    else:
        k.set(x, y, z, CORRUGATED_STAIRS if z in (MZ0 - 1, MZ1 + 1) else CORRUGATED_SLAB,
              {"facing": S if z < mid else N, "half": "bottom", "shape": "straight", "waterlogged": False}
              if z in (MZ0 - 1, MZ1 + 1) else {"type": "bottom", "waterlogged": False})


# --- the billboard ---------------------------------------------------------------------------------------------------------------
def billboard(k: Kit) -> None:
    """A tropospheric-scatter billboard: a wall of white panels eleven across and eight high on two rows of lattice
    legs, bowed (its ends a block forward), facing south-west; its lower west corner fallen out, the panels on the
    ground in front."""
    x0, x1, zb = 4, 14, 33
    for x in range(x0, x1 + 1, 2):
        k.lattice_tower(x, zb - 2, 1, 2, 1, 9, ladder=False)
    for x in range(x0, x1 + 1):
        bow = 1 if x in (x0, x0 + 1, x1 - 1, x1) else 0
        for y in range(2, 10):
            gone = x <= x0 + 3 and y <= 5 and k.n(x, y, zb, 2.0, 71) > 0.25
            if not gone:
                k.set(x, y, zb + bow, RADOME)
        k.set(x, 10, zb + bow, RADOME_SLAB, {"type": "bottom", "waterlogged": False})
    k.debris((x0 - 1, 1, zb + 1, x0 + 5, 1, zb + 4), [RADOME, RADOME_SLAB, RADOME], 0.55, salt=73)
    k.pad(x0 - 1, zb - 3, x1 + 1, zb - 1, "gray_concrete", salt=79)


# --- power --------------------------------------------------------------------------------------------------------------------------
def power_line(k: Kit) -> None:
    """Poles from the generator to the radar tower, the wire sagging between them."""
    tops = [k.pole(x, z, 7, arm="z") for x, z in ((38, 18), (31, 18), (24, 18), (17, 17))]
    start = (MODULES[2][1] - 1, FLOOR + 5, MZ0 - 1)
    k.wire(start, (tops[0][0], tops[0][1] + 1, tops[0][2] - 1), sag=0.8)
    for a, b in zip(tops, tops[1:]):
        k.wire((a[0], a[1] + 1, a[2] - 1), (b[0], b[1] + 1, b[2] - 1), sag=1.4)
    k.wire((tops[-1][0], tops[-1][1] + 1, tops[-1][2] - 1), (TOWER[2] + 1, 9, TOWER[3] + 1), sag=1.0)
    k.floodlight_pole(16, 24, 6, E)


# --- the fence ---------------------------------------------------------------------------------------------------------------------
def perimeter(k: Kit) -> None:
    gate = lambda x, z: z == 42 and 19 <= x <= 25
    k.fence(1, 1, 44, 1, fallen=0.22, salt=81)
    k.fence(1, 1, 1, 42, fallen=0.18, salt=83)
    k.fence(44, 1, 44, 42, fallen=0.2, salt=85)
    k.fence(1, 42, 44, 42, fallen=0.15, salt=87, skip=gate)
    # The gate: I-beam posts and a crossbeam, the gate itself gone; a sign on the post; a floodlight on each side.
    for x in (19, 25):
        k.footing(x, 42, "polished_andesite")
        k.fill(x, 1, 42, x, 3, 42, I_BEAM, {"axis": "y"})
    k.fill(19, 4, 42, 25, 4, 42, I_BEAM, {"axis": "x"})
    k.set(19, 2, 43, gb("keep_out_sign"), {"facing": S})
    k.set(25, 2, 43, gb("radiation_sign"), {"facing": S})
    k.floodlight_pole(18, 40, 5, N)
    # The guard hut inside the gate: corrugated, a window on the gate, a door to the yard.
    hx0, hz0, hx1, hz1 = 27, 37, 30, 40
    k.patch(hx0, hz0, hx1, hz1, [(1.01, "smooth_stone")])
    k.clad(hx0, 1, hz0, hx1, 3, hz1, salt=89, rust_courses=1.0)
    k.clear(hx0 + 1, 1, hz0 + 1, hx1 - 1, 3, hz1 - 1)
    k.fill(hx0 - 1, 4, hz0 - 1, hx1 + 1, 4, hz1 + 1, CORRUGATED_SLAB, {"type": "bottom", "waterlogged": False})
    k.set(hx0, 2, hz0 + 2, "glass_pane")
    k.iron_door(hx0 + 1, 1, hz0, N)
    k.set(hx1 - 1, 1, hz1 - 1, "spruce_stairs", {"facing": N, "half": "bottom", "shape": "straight", "waterlogged": False})


def finish(k: Kit) -> None:
    """The yard taken back: grass in clumps along the fences and walls, vines down the north faces, sheet and rubble
    in the corners, cobwebs indoors."""
    k.overgrow((0, 0, SIZE[0] - 1, SIZE[2] - 1), density=0.35)
    k.vines((TOWER[0], 2, TOWER[1], TOWER[2], DECK_Y - 1, TOWER[1]), N, density=0.35)
    k.vines((24, 2, MZ0, 40, 5, MZ0), N, density=0.25, salt=137)
    k.debris((32, 1, 34, 43, 1, 41), [RUSTED_SLAB, "gravel", RUSTED_SLAB], 0.25, salt=139)
    k.cobwebs((TOWER[0] + 1, 1, TOWER[1] + 1, TOWER[2] - 1, DECK_Y - 1, TOWER[3] - 1), 0.08)
    k.cobwebs((24, FLOOR + 1, MZ0 + 1, 40, FLOOR + 3, MZ1 - 1), 0.05)
