"""The Test Site, rebuilt (DESIGN.md §22.2): a nuclear proving ground in the desert, after the Nevada Test Site and
Trinity; the user's choice, an earlier shot's crater and a tower readied for the next one.

Two parts. The shot tower (`test_site_tower`, its middle the site's origin): a lattice tower twenty-nine high on splayed
legs, guyed out to concrete anchors, a grating deck at its top under the cab (corrugated, rusting at its foot) where the
device sits on its stand with its cables, a hoist on the cab's roof; instrument masts with their cameras (observers)
trained on it; poles carrying the firing line to the control bunker, banked with sand, its slit facing the tower; and
Doom Town, a street of two houses built to be blown down, dressed mannequins on their porches and at their windows.
Forty-eight blocks east, the earlier shot (`test_site_zero`, sunk three): its crater, glassed in rings from trinitite
at the middle out through glassed sand and irradiated soil to scorched earth, ragged at its edge; the old tower's
footings broken off at its floor; the obelisk on its rim; a fence ring with radiation signs, most of it down; the
charred frame of an earlier town; bleachers facing it all.

Templates: 48 x 44 x 48 and 40 x 14 x 40 (layer 0 the land's top; the crater's grade is its layer 3).
"""
from __future__ import annotations

import math

from build import AIR, gb
from kit import (CORRUGATED, CORRUGATED_SLAB, E, GRATING, GRATING_SLAB, I_BEAM, LATTICE, N, PANEL, RUSTED, RUSTED_PANEL,
                 RUSTED_SLAB, S, W, Kit)

TOWER_SIZE = (48, 44, 48)
C = 24                                       # the tower's middle (x and z)
TOP = 29                                     # its deck


def test_site_tower() -> Kit:
    k = Kit(*TOWER_SIZE, seed=5_301)
    grounds(k)
    tower(k)
    instruments(k)
    control_bunker(k)
    doom_town(k)
    desert(k)
    return k


# --- the ground -------------------------------------------------------------------------------------------------------------------
def grounds(k: Kit) -> None:
    """The road in from the east to the tower's pad, on south to the bunker, west along Doom Town's street."""
    k.road([(47, 24), (30, 24)], width=3, salt=11)
    k.road([(31, 26), (31, 39)], width=3, salt=13)
    k.road([(31, 40), (2, 40)], width=3, salt=17)
    k.pad(C - 4, C - 4, C + 4, C + 4, "light_gray_concrete", cracked="smooth_sandstone", salt=19)


# --- the shot tower --------------------------------------------------------------------------------------------------------------
def tower(k: Kit) -> None:
    ring = [(x, z) for x in range(C - 1, C + 2) for z in range(C - 1, C + 2) if (x, z) != (C, C)]
    for x, z in ring:
        for y in range(1, TOP):
            k.set(x, y, z, LATTICE)
    # Splayed legs: out three at the foot, two above, braced back to the shaft.
    for sx in (-1, 1):
        for sz in (-1, 1):
            for y in range(1, 4):
                k.set(C + 3 * sx, y, C + 3 * sz, LATTICE)
            k.footing(C + 3 * sx, C + 3 * sz, "polished_andesite")
            for y in range(4, 7):
                k.set(C + 2 * sx, y, C + 2 * sz, LATTICE)
            k.set(C + 2 * sx, 3, C + 2 * sz, LATTICE)
            k.set(C + sx * 2, 7, C + sz * 2, LATTICE)
    # Rest platforms on the way up, and the ladder up the middle (on the shaft's north side).
    for y in (12, 20):
        for x in range(C - 2, C + 3):
            for z in range(C - 2, C + 3):
                if max(abs(x - C), abs(z - C)) == 2:
                    k.set(x, y, z, GRATING_SLAB, {"type": "top", "waterlogged": False})
    k.ladder(C, 1, TOP, C, S)
    # The deck and the cab on it.
    k.fill(C - 3, TOP, C - 3, C + 3, TOP, C + 3, GRATING)
    k.set(C, TOP, C, AIR)
    for x in range(C - 3, C + 4):
        for z in range(C - 3, C + 4):
            if max(abs(x - C), abs(z - C)) == 3 and k.n(x, 1, z, 2.0, 23) > 0.2:
                k.set(x, TOP + 1, z, GRATING_SLAB, {"type": "bottom", "waterlogged": False})
    k.clad(C - 2, TOP + 1, C - 2, C + 2, TOP + 4, C + 2, salt=29, rust_courses=1.0)
    k.clear(C - 1, TOP + 1, C - 1, C + 1, TOP + 4, C + 1)
    k.fill(C - 3, TOP + 5, C - 3, C + 3, TOP + 5, C + 3, CORRUGATED_SLAB, {"type": "bottom", "waterlogged": False})
    k.iron_door(C, TOP + 1, C + 2, S)
    k.set(C + 2, TOP + 2, C, "glass_pane")
    k.set(C - 2, TOP + 2, C, "glass_pane")
    # The device on its stand, its cables up into the roof; the firing panel; a chest of what the crew left.
    k.set(C, TOP + 1, C - 1, "heavy_core", {"waterlogged": False})
    for y in range(TOP + 2, TOP + 5):
        k.set(C, y, C - 1, "iron_chain", {"axis": "y", "waterlogged": False})
    k.set(C - 1, TOP + 2, C + 1, "lever", {"face": "wall", "facing": E, "powered": False})
    k.set(C - 1, TOP + 3, C + 1, "lever", {"face": "wall", "facing": E, "powered": True})
    k.chest(C + 1, TOP + 1, C + 1, W, "test_site")
    k.lantern(C - 1, TOP + 4, C - 1, hanging=True)
    # The hoist on the cab's roof.
    for x in (C - 2, C + 2):
        k.fill(x, TOP + 6, C, x, TOP + 8, C, I_BEAM, {"axis": "y"})
    k.fill(C - 2, TOP + 9, C, C + 2, TOP + 9, C, I_BEAM, {"axis": "x"})
    for y in (TOP + 7, TOP + 8):
        k.set(C, y, C, "iron_chain", {"axis": "y", "waterlogged": False})
    k.set(C, TOP + 6, C, "tripwire_hook", {"facing": N, "attached": False, "powered": False})
    # Guys from the deck's corners down to concrete anchors out in the sand.
    for sx in (-1, 1):
        for sz in (-1, 1):
            a = (C + 3 * sx, TOP - 1, C + 3 * sz)
            b = (C + 12 * sx, 1, C + 12 * sz)
            guy(k, a, b)
            k.set(b[0], 1, b[2], "polished_andesite")
            k.footing(b[0], b[2], "polished_andesite")
    k.set(C + 3, 2, C + 4, gb("radiation_sign"), {"facing": S})


def guy(k: Kit, a: tuple[int, int, int], b: tuple[int, int, int]) -> None:
    """A guy wire as a line of chain from a to b (steep: each link set upright)."""
    (ax, ay, az), (bx, by, bz) = a, b
    steps = max(abs(by - ay), abs(bx - ax), abs(bz - az))
    for i in range(1, steps):
        t = i / steps
        x, y, z = round(ax + (bx - ax) * t), round(ay + (by - ay) * t), round(az + (bz - az) * t)
        if k.name(x, y, z) is None:
            k.set(x, y, z, "iron_chain", {"axis": "y", "waterlogged": False})


# --- instruments --------------------------------------------------------------------------------------------------------------------
def instruments(k: Kit) -> None:
    """Two instrument masts with their cameras turned on the tower; the firing line on poles to the bunker."""
    for x, z, h in ((8, 10, 10), (40, 12, 8)):
        k.lattice_tower(x, z, 1, 1, 1, h)
        k.set(x, h + 1, z, PANEL)
        face = W if x > C else E                       # the camera's lens turned on the tower
        k.set(x + (1 if face == E else -1), h + 1, z, "observer", {"facing": face, "powered": False})
    tops = [k.pole(x, z, 6, arm="x") for x, z in ((C + 4, C + 7), (C + 4, C + 13), (C + 5, 33))]
    for a, b in zip(tops, tops[1:]):
        k.wire((a[0] + 1, a[1] + 1, a[2]), (b[0] + 1, b[1] + 1, b[2]), sag=1.2)
    k.wire((C + 1, 8, C + 2), (tops[0][0] + 1, tops[0][1] + 1, tops[0][2]), sag=0.8)


# --- the control bunker ------------------------------------------------------------------------------------------------------------
def control_bunker(k: Kit) -> None:
    """Concrete banked with sand on three sides, its north face (to the tower) a slit under a visor, a ramp down to its
    door at the back; inside, the panels, a periscope up through its roof."""
    x0, x1, z0, z1 = 33, 41, 38, 43
    k.pad(x0 - 1, z0 - 1, x1 + 1, z1 + 1, "light_gray_concrete", cracked="smooth_sandstone", salt=31)
    k.walls(x0, 1, z0, x1, 4, z1, "light_gray_concrete")
    k.fill(x0, 5, z0, x1, 5, z1, "smooth_stone")
    k.fill(x0 + 1, 1, z0 + 1, x1 - 1, 4, z1 - 1, AIR)
    for x in range(x0 + 2, x1 - 1):
        k.set(x, 3, z0, "glass_pane")
    k.fill(x0, 4, z0 - 1, x1, 4, z0 - 1, "smooth_stone_slab", {"type": "top", "waterlogged": False})
    # Sand banked up its east, west and south sides and over its roof: a mound, its slope uneven (the noise lifts and
    # drops it a block), thinning out raggedly into the desert; no steps.
    for x in range(x0 - 6, x1 + 7):
        for z in range(z0, z1 + 7):
            if x0 <= x <= x1 and z <= z1:
                continue
            d = max(x0 - x, x - x1, z - z1, 0)
            h = round(5.0 - d * 0.8 + (k.n(x, 0, z, 2.5, 33) - 0.5) * 3.4)
            for y in range(1, min(h, 5) + 1):
                k.set(x, y, z, "sand", keep=True)
    for x in range(x0, x1 + 1):
        for z in range(z0 + 1, z1 + 1):
            if k.n(x, 6, z, 2.5, 35) > 0.3:
                k.set(x, 6, z, "sand")
    # The door at the back, down a cut in the bank.
    dx = (x0 + x1) // 2
    k.fill(dx, 1, z1 + 1, dx, 3, z1 + 4, AIR)
    k.iron_door(dx, 1, z1, S)
    # Inside: the firing panel and the recorders, a chest; the periscope through the roof.
    for x in range(x0 + 1, x1):
        k.set(x, 1, z0 + 1, PANEL)
        k.set(x, 2, z0 + 1, "black_stained_glass" if x % 2 else "lever", {"face": "floor", "facing": S, "powered": False} if x % 2 == 0 else None)
    k.chest(x1 - 1, 1, z1 - 1, W, "test_site")
    k.set(x0 + 1, 1, z1 - 1, "barrel", {"facing": "up", "open": False})
    k.fill(dx - 2, 4, z0 + 2, dx - 2, 7, z0 + 2, "iron_chain", {"axis": "y", "waterlogged": False})
    k.set(dx - 2, 8, z0 + 2, "observer", {"facing": N, "powered": False})      # looking north at the tower
    k.lantern(dx, 4, z0 + 3, hanging=True)


# --- Doom Town ---------------------------------------------------------------------------------------------------------------------
def doom_town(k: Kit) -> None:
    house_two_storey(k, 3, 30)
    bungalow(k, 13, 31)
    # Picket fences along the front yards, a gap at each path; a pole bringing a wire to the houses.
    for x in range(2, 21):
        if x in (6, 7, 15):
            continue
        if k.n(x, 1, 38, 3.0, 41) > 0.2:
            k.set(x, 1, 38, "birch_fence")
    k.pole(21, 36, 6, arm="z")
    k.wire((21, 7, 35), (10, 8, 33), sag=1.0)


def siding(k: Kit, x0: int, y0: int, z0: int, x1: int, y1: int, z1: int) -> None:
    """White clapboard on a frame: birch planks, the corners stripped birch logs."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if x not in (x0, x1) and z not in (z0, z1):
                continue
            corner = x in (x0, x1) and z in (z0, z1)
            for y in range(y0, y1 + 1):
                k.set(x, y, z, "stripped_birch_log" if corner else "birch_planks", {"axis": "y"} if corner else None)


def shuttered_window(k: Kit, x: int, y: int, z: int, face: str) -> None:
    """A window with a shutter open beside it on each side (trapdoors flat against the wall)."""
    k.set(x, y, z, "glass_pane" if k.n(x, y, z, 2.0, 43) > 0.25 else AIR)
    along = (1, 0) if face in (N, S) else (0, 1)
    fx, fz = {N: (0, -1), S: (0, 1), E: (1, 0), W: (-1, 0)}[face]
    for s in (-1, 1):
        k.set(x + along[0] * s + fx, y, z + along[1] * s + fz, "spruce_trapdoor",
              {"facing": face, "half": "bottom", "open": True, "powered": False, "waterlogged": False})


def gable(k: Kit, x0: int, x1: int, z0: int, z1: int, y: int, block: str = "deepslate_tile") -> None:
    """A gable roof over z0..z1 (eaves a block out), its ridge along x; deepslate shingles."""
    mid = (z0 + z1) / 2
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            d = abs(z - mid)
            ry = y + int((mid - z0 + 1) - d)
            if d < 0.6:
                k.set(x, ry, z, f"{block}_slab", {"type": "bottom", "waterlogged": False})
                k.set(x, ry - 1, z, f"{block}s")
            else:
                k.set(x, ry, z, f"{block}_stairs", {"facing": S if z < mid else N, "half": "bottom", "shape": "straight",
                                                     "waterlogged": False})
                if x in (x0, x1) and ry > y:
                    for yy in range(y, ry):
                        k.set(x, yy, z, "birch_planks", keep=True)


def house_two_storey(k: Kit, x0: int, z0: int) -> None:
    x1, z1 = x0 + 6, z0 + 6
    k.patch(x0, z0, x1, z1, [(1.01, "stone_bricks")])
    siding(k, x0, 1, z0, x1, 7, z1)
    k.fill(x0 + 1, 4, z0 + 1, x1 - 1, 4, z1 - 1, "spruce_planks")
    k.fill(x0 + 1, 1, z0 + 1, x1 - 1, 3, z1 - 1, AIR)
    k.fill(x0 + 1, 5, z0 + 1, x1 - 1, 7, z1 - 1, AIR)
    gable(k, x0, x1, z0, z1, 8)
    k.door(x0 + 3, 1, z1, S, "spruce_door")
    for x in (x0 + 1, x1 - 1):
        shuttered_window(k, x, 2, z1, S)
        shuttered_window(k, x, 6, z1, S)
    shuttered_window(k, x0 + 3, 6, z1, S)
    for z in (z0 + 2, z0 + 4):
        shuttered_window(k, x0, 2, z, W)
        shuttered_window(k, x0, 6, z, W)
    # The porch along the front: a slab deck, birch posts, a shingle roof.
    for x in range(x0, x1 + 1):
        k.set(x, 1, z1 + 1, "spruce_slab", {"type": "bottom", "waterlogged": False})
        k.set(x, 4, z1 + 1, "deepslate_tile_slab", {"type": "bottom", "waterlogged": False})
    for x in (x0, x1):
        k.fill(x, 2, z1 + 1, x, 3, z1 + 1, "birch_fence")
    # The chimney up the east end, brick.
    k.fill(x1 + 1, 1, z0 + 3, x1 + 1, 11, z0 + 3, "bricks")
    k.set(x1 + 1, 12, z0 + 3, "brick_wall")
    # Furnished, and lived in by mannequins.
    k.bed(x0 + 1, 5, z0 + 1, E, "light_blue")
    k.set(x0 + 5, 1, z0 + 1, "crafting_table")
    k.set(x0 + 4, 1, z0 + 1, "furnace", {"facing": S, "lit": False})
    k.set(x0 + 2, 1, z0 + 3, "spruce_fence")
    k.set(x0 + 2, 2, z0 + 3, "spruce_pressure_plate")
    k.marker(x0 + 1, 1, z1 + 1, "stand:civilian")
    k.marker(x0 + 5, 1, z1 + 1, "stand:civilian")
    k.marker(x0 + 2, 5, z1 - 1, "stand:civilian")
    k.marker(x0 + 4, 1, z0 + 2, "stand:civilian:east")


def bungalow(k: Kit, x0: int, z0: int) -> None:
    x1, z1 = x0 + 6, z0 + 5
    k.patch(x0, z0, x1, z1, [(1.01, "stone_bricks")])
    siding(k, x0, 1, z0, x1, 3, z1)
    k.fill(x0 + 1, 1, z0 + 1, x1 - 1, 3, z1 - 1, AIR)
    gable(k, x0, x1, z0, z1, 4)
    k.door(x0 + 2, 1, z1, S, "spruce_door")
    for x in (x0 + 4, x1 - 1):
        shuttered_window(k, x, 2, z1, S)
    shuttered_window(k, x1, 2, z0 + 2, E)
    k.set(x0 + 2, 1, z1 + 1, "spruce_slab", {"type": "bottom", "waterlogged": False})
    k.marker(x0 + 4, 1, z1 + 1, "stand:civilian")
    k.marker(x0 + 1, 1, z0 + 1, "stand:civilian:east")
    k.marker(x1 - 1, 1, z0 + 1, "stand:civilian:west")
    k.set(x1 - 1, 1, z1 - 1, "barrel", {"facing": "up", "open": False})


# --- the desert ------------------------------------------------------------------------------------------------------------------------
def desert(k: Kit) -> None:
    """Dead bushes and a few cacti in the sand away from the works; sand drifted against the tower's pad and the houses;
    a warning sign on a post where the road comes in."""
    sx, sz = TOWER_SIZE[0], TOWER_SIZE[2]
    for x in range(sx):
        for z in range(sz):
            if k.name(x, 0, z) is not None or k.name(x, 1, z) is not None:
                continue
            v = k.n(x, 0, z, 1.6, 51)
            if v > 0.86:
                k.set(x, 1, z, "dead_bush")
            elif v < 0.035 and min(abs(x - C), abs(z - C)) > 8:
                k.fill(x, 1, z, x, 2, z, "cactus", {"age": 0})
    k.fill(45, 1, 21, 45, 2, 21, "spruce_fence")
    k.set(45, 2, 22, gb("keep_out_sign"), {"facing": S})
    k.set(44, 2, 21, gb("radiation_sign"), {"facing": W})


# --- the earlier shot ---------------------------------------------------------------------------------------------------------------
ZERO_SIZE = (40, 14, 40)
GRADE = 3                                     # the crater template's ground (it is sunk three)
CX = CZ = 20
RIM = 12.0


def floor_height(r: float) -> int:
    """The crater's surface (the top block's y) a distance r from its middle: a saucer, flat at the middle."""
    if r >= RIM:
        return GRADE
    t = max(0.0, (r - 3.5) / (RIM - 3.5))
    return round(GRADE - 3 * (1 - t * t * (3 - 2 * t)))


def test_site_zero() -> Kit:
    k = Kit(*ZERO_SIZE, seed=5_419)
    sx, _, sz = ZERO_SIZE
    for x in range(sx):
        for z in range(sz):
            r = math.hypot(x + 0.5 - CX, z + 0.5 - CZ)
            if r > RIM + 5.5:
                continue
            h = floor_height(r)
            wob = k.n(x, 0, z, 3.0, 61)
            # Rings from the middle out, their edges ragged with the noise.
            rr = r + (wob - 0.5) * 3.0
            if rr < 4:
                top = "groundburst:trinitite" if wob > 0.3 else "groundburst:glassed_sand"
            elif rr < 8:
                top = "groundburst:glassed_sand" if wob > 0.35 else "groundburst:trinitite"
            elif rr < 11:
                top = "groundburst:glassed_sand" if wob > 0.45 else "groundburst:irradiated_soil"
            elif rr < 14:
                top = "groundburst:irradiated_soil" if wob > 0.55 else "groundburst:scorched_earth"
            elif rr < 16.5:
                top = "groundburst:scorched_earth" if wob > 0.4 else None
            else:
                top = None
            if r < RIM:
                for y in range(0, h):
                    k.set(x, y, z, "sandstone" if y < h - 1 else "sand")
                for y in range(h + 1, GRADE + 1):
                    k.set(x, y, z, AIR)
            if top:
                k.set(x, h, z, top)
            elif r < RIM:
                k.set(x, h, z, "sand")
    old_tower(k)
    obelisk(k)
    fence_ring(k)
    old_town(k)
    bleachers(k)
    return k


def old_tower(k: Kit) -> None:
    """The earlier tower's four footings broken off at the crater's floor, their steel twisted over and rusting."""
    y = floor_height(0) + 1
    for sx in (-1, 1):
        for sz in (-1, 1):
            x, z = CX + 2 * sx, CZ + 2 * sz
            k.fill(x, y, z, x, y + (1 if (sx + sz) % 4 else 0), z, "light_gray_concrete")
            k.set(x + sx, y, z, I_BEAM, {"axis": "x"})
            k.set(x, y, z + sz, RUSTED_PANEL) if (sx > 0) else k.set(x, y + 1, z + sz, I_BEAM, {"axis": "z"})
    k.debris((CX - 5, y, CZ - 5, CX + 5, y, CZ + 5), [RUSTED_SLAB, RUSTED_SLAB, "groundburst:slag"], 0.3, salt=67, on_ground=False)


def obelisk(k: Kit) -> None:
    """The marker set up on the rim, as Trinity's: dark stone on a stepped base."""
    x, z, y = CX, CZ + 15, GRADE + 1
    k.fill(x - 1, y, z - 1, x + 1, y, z + 1, "polished_blackstone_slab", {"type": "bottom", "waterlogged": False})
    k.set(x, y, z, "polished_blackstone")
    k.fill(x, y + 1, z, x, y + 3, z, "polished_blackstone_bricks")
    k.set(x, y + 4, z, "polished_blackstone_wall")


def fence_ring(k: Kit) -> None:
    """Chain-link round the crater fifteen out, most of it down; radiation signs on what stands."""
    y = GRADE + 1
    for a in range(0, 360, 4):
        x = round(CX + 15 * math.cos(math.radians(a)))
        z = round(CZ + 15 * math.sin(math.radians(a)))
        if k.n(x, 0, z, 6.0, 71) < 0.55 or k.name(x, y, z) is not None:
            continue
        k.fill(x, y, z, x, y + 1, z, gb("chain_link_fence"))
    for a in (40, 160, 290):
        x = round(CX + 15 * math.cos(math.radians(a)))
        z = round(CZ + 15 * math.sin(math.radians(a)))
        k.fill(x, y, z, x, y + 1, z, "spruce_fence")
        face = S if z > CZ else N
        k.set(x, y + 1, z + (1 if face == S else -1), gb("radiation_sign"), {"facing": face})


def old_town(k: Kit) -> None:
    """An earlier test town's house east of the crater, burnt to its frame: charred walls standing two high in an L,
    its roof beams down across them, ash in its corners."""
    x0, z0 = 33, 14
    y = GRADE + 1
    for x in range(x0, x0 + 5):
        h = 2 if k.n(x, 0, z0, 2.0, 73) > 0.35 else 1
        k.fill(x, y, z0, x, y + h - 1, z0, gb("charred_planks"))
    for z in range(z0, z0 + 5):
        h = 2 if k.n(x0, 0, z, 2.0, 75) > 0.4 else 1
        k.fill(x0, y, z, x0, y + h - 1, z, gb("charred_planks"))
    for z in (z0 + 1, z0 + 3):
        k.fill(x0 + 1, y, z, x0 + 4, y, z, gb("charred_log"), {"axis": "x"})
    k.set(x0 + 4, y, z0, gb("charred_log"), {"axis": "y"})
    k.set(x0 + 4, y + 1, z0, gb("charred_log"), {"axis": "y"})
    for x, z in ((x0 + 1, z0 + 2), (x0 + 3, z0 + 4), (x0 + 2, z0 + 1)):
        k.set(x, y, z, gb("ash"), {"layers": 2}, keep=True)


def bleachers(k: Kit) -> None:
    """Wooden bleachers on the north edge facing the crater: four steps of seats on a frame."""
    x0, x1, z0 = 13, 27, 1
    y = GRADE + 1
    for i in range(4):
        z = z0 + 3 - i
        for x in range(x0, x1 + 1):
            k.set(x, y + i, z, "spruce_stairs", {"facing": N, "half": "bottom", "shape": "straight", "waterlogged": False})
            for yy in range(y, y + i):
                k.set(x, yy, z, "spruce_planks" if x in (x0, x1) else "spruce_fence")
    for x in (x0, x1):
        k.fill(x, y + 4, z0, x, y + 5, z0, "spruce_fence")
