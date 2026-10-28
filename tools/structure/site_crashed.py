"""The Crashed Missile, rebuilt (DESIGN.md §22.2): a broken-arrow site after the Damascus Titan II explosion and the
crashes of the Cold War, the missile itself a model (the Missile Wreck, tools/assets/missile_wreck.py), built to the
kit's rules (tools/structure/kit.py).

A two-stage missile came down from the south at a shallow angle, ploughed a furrow and broke in three: its tail and
engine thrown onto the land beside the furrow, the body lying in it torn and buckled, the nose driven into the crater at
its end; the warhead split there, its core bare in the crater's floor, the soil round it irradiated. The furrow shallows
back south to where it first struck, earth thrown up along its sides, burned black near the crater, debris all along.
Afterwards a recovery team roped the place off (posts and rope round it, radiation signs on them), set up a floodlight
behind sandbags, a decontamination point and its crates under a tarp, and left: a suit still hangs on its stand.

One template, 28 x 11 x 46, its grade at y 4 (the furrow and crater dug below it); the origin is the wreck's block, at
(15, 20) on the land.
"""
from __future__ import annotations

import math

from build import AIR, gb
from kit import E, FLOODLIGHT, I_BEAM, LATTICE, N, RUSTED_SLAB, S, SANDBAG_SLAB, SANDBAGS, W, Kit

SIZE = (28, 11, 46)
G = 4                                        # grade: the land's top block
WX, WZ = 15, 20                              # the wreck's block (it stands on the land at y G + 1)
CRATER = (13.9, 13.7)                        # where the nose went in: the crater's middle (x, z)
CRATER_R = 2.6
#: The furrow's line, crater to where it first struck (x, z), and its half-width along it.
FURROW = [(13.4, 15.5), (12.9, 16.6), (13.6, 20.4), (14.2, 30.0), (14.8, 42.0)]
SCORCHED, IRRADIATED, ASH, URANIUM = gb("scorched_earth"), gb("irradiated_soil"), gb("fallout_ash"), gb("uranium_block")


def crashed_missile() -> Kit:
    k = Kit(*SIZE, seed=8_100)
    furrow(k)
    crater(k)
    for x in range(WX - 1, WX + 2):
        for z in range(WZ - 1, WZ + 2):
            k.set(x, G, z, "packed_mud" if k.n(x, 7, z, 1.5, 37) > 0.4 else "gravel")    # under the tail
    for lx, lz, r in LOOSE:
        for x in range(int(lx - r), int(lx + r) + 1):
            for z in range(int(lz - r), int(lz + r) + 1):
                if math.hypot(x + 0.5 - lx, z + 0.5 - lz) < r and k.name(x, G, z) is None:
                    k.set(x, G, z, "packed_mud")
    k.set(WX, G + 1, WZ, gb("missile_wreck"), {"facing": N})
    gantry(k)
    scorch(k)
    cordon(k)
    camp(k)
    k.debris((2, G + 1, 22, SIZE[0] - 3, G + 1, SIZE[2] - 3), ["smooth_quartz_slab", RUSTED_SLAB, "gravel"], 0.07, salt=21)
    overgrow(k)
    return k


def overgrow(k: Kit) -> None:
    """The grass back over the land round it (the kit's overgrowth is for sites whose ground is layer 0; here it is
    layer G), thinner toward the crater, none in it."""
    cx, cz = CRATER
    for x in range(SIZE[0]):
        for z in range(SIZE[2]):
            if k.name(x, G, z) is not None or k.name(x, G + 1, z) is not None:
                continue
            r = math.hypot(x + 0.5 - cx, z + 0.5 - cz)
            v = k.n(x, 0, z, 2.2, 81) + min(0.0, (r - 12.0) * 0.03)
            if v > 0.6:
                if v > 0.86:
                    k.set(x, G + 1, z, "tall_grass", {"half": "lower"})
                    k.set(x, G + 2, z, "tall_grass", {"half": "upper"})
                else:
                    k.set(x, G + 1, z, "fern" if v > 0.78 else "short_grass")


#: Where the wreck's loose pieces lie (x, z, reach): no earth heaped there over them.
LOOSE = [(15.7, 18.9, 1.3), (12.0, 23.25, 1.5), (15.0, 17.75, 0.9)]


def _loose(x: int, z: int) -> bool:
    return any(math.hypot(x + 0.5 - lx, z + 0.5 - lz) < r for lx, lz, r in LOOSE)


def _line_distance(x: float, z: float) -> tuple[float, float]:
    """The distance from (x, z) to the furrow's line, and how far along it (0 at the crater .. 1 where it struck)."""
    best, along, total = 1e9, 0.0, 0.0
    lengths = [math.dist(a, b) for a, b in zip(FURROW, FURROW[1:])]
    run = 0.0
    for (ax, az), (bx, bz), ln in zip(FURROW, FURROW[1:], lengths):
        t = max(0.0, min(1.0, ((x - ax) * (bx - ax) + (z - az) * (bz - az)) / (ln * ln)))
        d = math.hypot(x - (ax + t * (bx - ax)), z - (az + t * (bz - az)))
        if d < best:
            best, along = d, (run + t * ln) / sum(lengths)
        run += ln
    return best, along


def _wreck_block(x: int, z: int) -> bool:
    """The tail's place round the wreck's block: the land kept level there."""
    return abs(x - WX) <= 1 and abs(z - WZ) <= 1


def furrow(k: Kit) -> None:
    """Ploughed a block deep where the body lies and for a few blocks behind it, shallowing south to churned ground at
    grade where it first struck; its floor broken earth and gravel, scorched toward the crater, the grass coming back
    over its far end. Earth thrown out in low clods along its sides behind the wreck (none beside the body, so it shows),
    grassed over in places."""
    for x in range(SIZE[0]):
        for z in range(SIZE[2]):
            if _wreck_block(x, z):
                continue
            d, t = _line_distance(x + 0.5, z + 0.5)
            half = 1.55 - 0.6 * t
            v = k.n(x, 0, z, 2.2, 31)
            if d <= half:
                depth = 1 if t < 0.32 or (t < 0.6 and d < half - 0.7) else 0
                for y in range(G - depth + 1, G + 1):
                    k.set(x, y, z, AIR)
                if t > 0.62 and v > 0.58:
                    floor = "grass_block"
                elif t > 0.55:
                    floor = "coarse_dirt" if v > 0.6 else "gravel" if v < 0.28 else "rooted_dirt" if v > 0.48 else "dirt"
                else:
                    floor = "packed_mud" if v > 0.45 else "gravel"     # no tree roots where the wreck lies
                k.set(x, G - depth, z, SCORCHED if t < 0.18 and v > 0.35 else floor)
            elif d <= half + 1.6 and t < 0.85 and not _loose(x, z):
                # The earth thrown out to the sides: torn turf and soil lying flat, the grass back through it in places.
                c = k.n(x, 3, z, 1.8, 33)
                if c > 0.4 and k.name(x, G, z) is None:
                    k.set(x, G, z, "coarse_dirt" if c > 0.62 else "rooted_dirt" if c > 0.52 else "podzol", {"snowy": False} if c <= 0.52 else None)


def crater(k: Kit) -> None:
    """A bowl two blocks deep where the nose went in, a low ragged rim thrown up round it but for the side the body came
    in by; its floor scorched and dusted with ash; the warhead's core bare in the floor beside the nose, the soil round
    it irradiated; the guidance bay's spill (the site's loot) on the floor."""
    cx, cz = CRATER
    floor_y = {}
    for x in range(SIZE[0]):
        for z in range(SIZE[2]):
            r = math.hypot(x + 0.5 - cx, z + 0.5 - cz)
            v = k.n(x, 0, z, 2.0, 41)
            if r < CRATER_R:
                depth = 2 if r < 1.6 else max(1, min(2, round(2.2 * (1 - (r / CRATER_R) ** 2) + (v - 0.5) * 0.6)))
                for y in range(G - depth + 1, G + 3):
                    k.set(x, y, z, AIR)
                k.set(x, G - depth, z, SCORCHED if v > 0.35 else "packed_mud")
                floor_y[(x, z)] = G - depth
                if k.n(x, 5, z, 1.5, 43) > 0.55:
                    k.set(x, G - depth + 1, z, ASH, {"layers": 1 + int(k.n(x, 6, z, 1.2, 45) * 3)})
            elif r < CRATER_R + 1.3 and not _wreck_block(x, z) and v > 0.32:
                if z + 0.5 > cz + 0.8 and abs(x + 0.5 - 13.3) < 2.4:
                    continue                                       # where the body came in: no rim
                if k.name(x, G + 1, z) is None and k.name(x, G, z) != AIR:
                    k.set(x, G + 1, z, "packed_mud" if v > 0.62 else "gravel" if v > 0.5 else "coarse_dirt")
    # The core, bare, the soil round it irradiated; the bay's spilled kit (the site's loot).
    core = (12, G - 2, 13)
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            if k.name(core[0] + dx, core[1], core[2] + dz) not in (None, AIR):
                k.set(core[0] + dx, core[1], core[2] + dz, IRRADIATED)
    k.set(*core, URANIUM)
    k.set(core[0], core[1] + 1, core[2], AIR)
    chest = (15, 12)
    k.chest(chest[0], floor_y.get(chest, G - 1) + 1, chest[1], W, "crashed_missile")


def gantry(k: Kit) -> None:
    """The recovery team's lifting frame over the crater, the site's mark from afar: two lattice legs either side, an
    I-beam across their tops, a chain hoist hanging from it over the nose; they never lifted it."""
    z, xa, xb, top = 15, 10, 17, G + 5
    for x in (xa, xb):
        k.set(x, G, z, "polished_andesite")
        for y in range(G + 1, top):
            k.set(x, y, z, LATTICE)
    for x in range(xa, xb + 1):
        k.set(x, top, z, I_BEAM, {"axis": "x"})
    for y in range(G + 2, top):
        k.set(13, y, z, "iron_chain", {"axis": "y", "waterlogged": False})
    k.set(xb + 1, G + 1, z, "grindstone", {"face": "floor", "facing": E})            # the hand winch at its foot


def scorch(k: Kit) -> None:
    """The fire's mark round the crater: the grass burned to bare earth and scorched ground in patches, ash lying in
    drifts, a few dead bushes; the radiation keeping it bare."""
    cx, cz = CRATER
    for x in range(SIZE[0]):
        for z in range(SIZE[2]):
            if k.name(x, G, z) is not None or _wreck_block(x, z):
                continue
            r = math.hypot(x + 0.5 - cx, z + 0.5 - (cz + 1.5))
            reach = 5.5 + (k.n(x, 0, z, 3.0, 51) - 0.5) * 3.0
            if r > reach:
                # Just outside it, on the land itself, the brush killed and left standing.
                if r < reach + 1.6 and k.name(x, G + 1, z) is None and k.n(x, 4, z, 1.4, 57) > 0.7:
                    k.set(x, G + 1, z, "dead_bush")
                continue
            v = k.n(x, 1, z, 2.0, 53)
            k.set(x, G, z, SCORCHED if v > 0.58 or r < reach * 0.45 else "packed_mud" if v > 0.3 else "gravel")
            if k.name(x, G + 1, z) is None:
                w = k.n(x, 2, z, 1.4, 55)
                if w > 0.72:
                    k.set(x, G + 1, z, ASH, {"layers": 1 + int(w * 2)})


def cordon(k: Kit) -> None:
    """The recovery team's cordon: short posts in a ring round the wreck and the crater, rope sagging between them
    (fallen in places), every third post a full one with a radiation sign facing out."""
    cx, cz, radius = 14.5, 17.0, 10.0
    posts = []
    for i in range(10):
        a = 2 * math.pi * i / 10 + 0.2
        x, z = round(cx + radius * math.sin(a) - 0.5), round(cz + radius * math.cos(a) - 0.5)
        if k.name(x, G, z) == AIR or abs(x - 14) <= 1 and z > cz:
            posts.append(None)                                   # where the furrow runs out: a gap
            continue
        sign_post = len(posts) % 3 == 0
        for y in (G + 1, G + 2):
            if sign_post:
                k.set(x, y, z, "stripped_spruce_log", {"axis": "y"})
            else:
                k.set(x, y, z, "spruce_fence")
        posts.append((x, z, a))
    for i, p in enumerate(posts):
        q = posts[(i + 1) % len(posts)]
        if p is None or q is None:
            continue
        if k.n(p[0], 0, p[1], 3.0, 61) > 0.72:
            continue                                             # the rope down here
        k.wire((p[0], G + 2, p[1]), (q[0], G + 2, q[1]), sag=0.7)
    for i, p in enumerate(posts):
        if p is None or i % 3:
            continue
        x, z, a = p
        face = (E if math.sin(a) > 0 else W) if abs(math.sin(a)) > abs(math.cos(a)) else (S if math.cos(a) > 0 else N)
        fx, fz = {N: (0, -1), S: (0, 1), E: (1, 0), W: (-1, 0)}[face]
        if k.name(x + fx, G + 2, z + fz) is None:
            k.set(x + fx, G + 2, z + fz, gb("radiation_sign"), {"facing": face})


def camp(k: Kit) -> None:
    """Outside the cordon to the south-east: a floodlight behind a sandbag wall turned on the crater, the
    decontamination point (crates, a tub, the suit left on its stand), crates under a tarp."""
    x0, z0 = 21, 29
    for x in range(x0, x0 + 4):
        k.set(x, G + 1, z0, SANDBAGS)
        k.set(x, G + 2, z0, SANDBAG_SLAB if k.n(x, 0, z0, 1.5, 71) > 0.4 else SANDBAGS)
    for z in (z0 + 1, z0 + 2):
        k.set(x0, G + 1, z, SANDBAGS)
    # The floodlight on its pole (the kit's pole stands on layer 0; this site's ground is layer G).
    px, pz = x0 + 2, z0 + 1
    k.set(px, G, pz, "polished_andesite")
    for y in range(G + 1, G + 5):
        k.set(px, y, pz, LATTICE)
    k.set(px, G + 4, pz - 1, FLOODLIGHT, {"facing": N})
    # Decontamination: crates of kit, a tub, the suit.
    k.barrel(x0 + 3, G + 1, z0 + 4, "up")
    k.barrel(x0 + 4, G + 1, z0 + 4, "up")
    k.set(x0 + 4, G + 1, z0 + 5, "cauldron")
    k.marker(x0 + 2, G + 1, z0 + 5, "stand:hazmat:north")
    # Crates under a tarp.
    for x, z in ((x0 - 2, z0 + 5), (x0 - 1, z0 + 5), (x0 - 2, z0 + 6)):
        k.barrel(x, G + 1, z, "up")
        k.set(x, G + 2, z, "green_carpet")
    k.set(x0 - 1, G + 1, z0 + 6, "green_carpet")
    k.set(x0 + 5, G + 1, z0 + 2, "stripped_spruce_log", {"axis": "y"})
    k.set(x0 + 5, G + 2, z0 + 2, "stripped_spruce_log", {"axis": "y"})
    k.set(x0 + 5, G + 2, z0 + 1, gb("keep_out_sign"), {"facing": N})
