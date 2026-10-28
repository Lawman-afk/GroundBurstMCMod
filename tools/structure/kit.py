"""The building kit for the world's sites, rebuilt (DESIGN.md §22.2): what the research on building in Minecraft asks of
every site, as helpers over `Build`.

- The ground: a template's layer 0 is the land's own top block (the site's ground); what stands on the land stands on
  layer 1. Pads, roads and footings take the place of the land's top block; where layer 0 is left unset the land stays
  as it was. Everything that stands on bare land (a fence, a pole, a mast, a pier) has a footing in layer 0, so it is a
  bottom block and `SitePiece` fills under it down to the land wherever the land dips.
- Noise, not dice: wear and material variety come from value noise (clusters with soft edges), and gradients (dirtier
  low, cleaner high; rusted at the base of a wall) from the height mixed with it. Never an even random mix.
- Shapes that read: lattice towers, radomes smoothed with stairs and slabs, sagging wires, chain-link runs with their
  fallen sections, modules on piers with their cladding banded.
"""
from __future__ import annotations

import math
from typing import Callable, Iterable

from build import AIR, OPPOSITE, STEP, Build, gb, mc

N, E, S, W = "north", "east", "south", "west"
DIR4 = (N, E, S, W)
FACING_OF = {(0, -1): N, (0, 1): S, (1, 0): E, (-1, 0): W}
BUTTON = "polished_blackstone_button"

# The Construction set's blocks.
CHAIN_LINK = gb("chain_link_fence")
BARBED = gb("barbed_wire")
CORRUGATED = gb("corrugated_steel")
CORRUGATED_STAIRS = gb("corrugated_steel_stairs")
CORRUGATED_SLAB = gb("corrugated_steel_slab")
RUSTED = gb("rusted_corrugated_steel")
RUSTED_STAIRS = gb("rusted_corrugated_steel_stairs")
RUSTED_SLAB = gb("rusted_corrugated_steel_slab")
PANEL = gb("steel_panel")
RUSTED_PANEL = gb("rusted_steel_panel")
GRATING = gb("steel_grating")
GRATING_SLAB = gb("steel_grating_slab")
I_BEAM = gb("i_beam")
LATTICE = gb("lattice_frame")
RADOME = gb("radome_panel")
RADOME_STAIRS = gb("radome_panel_stairs")
RADOME_SLAB = gb("radome_panel_slab")
FLOODLIGHT = gb("floodlight")
SANDBAGS = gb("sandbags")
SANDBAG_SLAB = gb("sandbag_slab")


# --- noise -------------------------------------------------------------------------------------------------------------------------
def _hash(ix: int, iy: int, iz: int, seed: int) -> float:
    h = (ix * 374761393 + iy * 668265263 + iz * 2147483647 + seed * 1274126177) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1103515245) & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFFFF) / float(0x1000000)


def _smooth(t: float) -> float:
    return t * t * (3 - 2 * t)


def noise(x: float, y: float, z: float, scale: float, seed: int) -> float:
    """Value noise in [0, 1): smooth clusters about {scale} blocks across."""
    x, y, z = x / scale, y / scale, z / scale
    ix, iy, iz = math.floor(x), math.floor(y), math.floor(z)
    fx, fy, fz = _smooth(x - ix), _smooth(y - iy), _smooth(z - iz)
    acc = 0.0
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                w = (fx if dx else 1 - fx) * (fy if dy else 1 - fy) * (fz if dz else 1 - fz)
                acc += w * _hash(ix + dx, iy + dy, iz + dz, seed)
    return acc


def fbm(x: float, y: float, z: float, scale: float, seed: int, octaves: int = 2) -> float:
    total, amp, norm = 0.0, 1.0, 0.0
    for o in range(octaves):
        total += amp * noise(x, y, z, scale / (2 ** o), seed + o * 101)
        norm += amp
        amp *= 0.5
    return total / norm


class Kit(Build):
    """A `Build` that knows the rules above."""

    def __init__(self, sx: int, sy: int, sz: int, seed: int = 0):
        super().__init__(sx, sy, sz, seed)
        self.seed = seed
        self._iron_doors: list[tuple[int, int, int, str]] = []
        #: Underground: what is left unset is rock, not air (so nothing is put there).
        self.buried = False

    # --- doors ----------------------------------------------------------------------------------------------------------------------
    def iron_door(self, x: int, y: int, z: int, facing: str, hinge: str = "left", open_: bool = False) -> None:
        """An iron door, which only a signal opens: `finish` puts a button on the wall beside it on both sides."""
        self.door(x, y, z, facing, "iron_door", hinge, open_)
        self._iron_doors.append((x, y, z, facing))

    def finish(self) -> None:
        """What waits for the walls to be built: a button by each iron door on each side of it, at shoulder height on the
        wall beside the door; where the door opens into a passage one wide, on the passage's wall just in front of it."""
        for x, y, z, facing in self._iron_doors:
            fx, fz = STEP[facing]
            sides = ((fz, fx), (-fz, -fx))                          # along the wall, either way
            for face, (dx, dz) in ((facing, (fx, fz)), (OPPOSITE[facing], (-fx, -fz))):
                placed = False
                for sx, sz in sides:
                    wx, wz = x + sx, z + sz
                    if self.solid(wx, y + 1, wz) and self._free(wx + dx, y + 1, wz + dz):
                        self.set(wx + dx, y + 1, wz + dz, BUTTON, {"face": "wall", "facing": face, "powered": False})
                        placed = True
                        break
                ax, az = x + dx, z + dz
                if placed or not self._free(ax, y + 1, az):
                    continue
                for sx, sz in sides:
                    if self.solid(ax + sx, y + 1, az + sz):
                        self.set(ax, y + 1, az, BUTTON, {"face": "wall", "facing": FACING_OF[(-sx, -sz)], "powered": False})
                        break

    def _free(self, x: int, y: int, z: int) -> bool:
        there = self.name(x, y, z)
        return there == AIR or (there is None and not self.buried)

    def n(self, x: float, y: float, z: float, scale: float, salt: int = 0) -> float:
        return fbm(x, y, z, scale, self.seed + salt)

    # --- the ground (layer 0) ---------------------------------------------------------------------------------------------------
    def ground(self, x: int, z: int, block: str) -> None:
        """Layer 0 at a column: the land's top block taken by {block}."""
        self.set(x, 0, z, block)

    def footing(self, x: int, z: int, block: str | None = None) -> None:
        """A footing under something standing on bare land (a concrete base under a tower's leg), unless the ground
        there is already set. Without a block the land itself is the footing: `SitePiece` fills under whatever stands
        on layer 1 wherever the land dips below layer 0, so a fence needs no strip of dirt drawn under it."""
        if block and self.name(x, 0, z) is None:
            self.ground(x, z, block)

    def patch(self, x0: int, z0: int, x1: int, z1: int, layers: list[tuple[float, str]], scale: float = 4.0, salt: int = 0,
              edge: float = 0.0) -> None:
        """Ground of several materials by noise: {layers} as (threshold, block) from the lowest up, the first whose
        threshold the noise is under. {edge}: the outer blocks of the rectangle thin out raggedly (0 none .. 1 a lot)."""
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if edge:
                    d = min(x - x0, x1 - x, z - z0, z1 - z)
                    if d < 2 and self.n(x, 0, z, 2.0, salt + 7) < edge * (0.7 if d == 0 else 0.35):
                        continue
                v = self.n(x, 0, z, scale, salt)
                for limit, block in layers:
                    if v < limit:
                        if block:
                            self.ground(x, z, block)
                        break

    def road(self, points: list[tuple[int, int]], width: int = 3, salt: int = 31) -> None:
        """A gravel track along a polyline: gravel worn to coarse dirt and taken back by grass at its ragged edges."""
        for (ax, az), (bx, bz) in zip(points, points[1:]):
            steps = max(abs(bx - ax), abs(bz - az), 1)
            for k in range(steps + 1):
                cx, cz = ax + (bx - ax) * k / steps, az + (bz - az) * k / steps
                for dx in range(-width, width + 1):
                    for dz in range(-width, width + 1):
                        x, z = round(cx + dx), round(cz + dz)
                        d = math.hypot(x - cx, z - cz)
                        if d > width / 2 + 0.6:
                            continue
                        v = self.n(x, 0, z, 3.0, salt)
                        edge = d > width / 2 - 0.4
                        if edge and v < 0.42:
                            continue                            # grass has taken the verge back
                        self.ground(x, z, "coarse_dirt" if v > 0.7 or (edge and v < 0.55) else "gravel")

    def pad(self, x0: int, z0: int, x1: int, z1: int, block: str = "light_gray_concrete", cracked: str = "andesite",
            salt: int = 53) -> None:
        """A concrete slab at grade: cracked in clusters, grass coming up through the worst of them."""
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                v = self.n(x, 0, z, 3.5, salt)
                self.ground(x, z, "moss_block" if v > 0.82 else cracked if v > 0.68 else block)

    # --- fences -------------------------------------------------------------------------------------------------------------------
    def fence(self, x0: int, z0: int, x1: int, z1: int, height: int = 2, barbed: bool = True, fallen: float = 0.0,
              salt: int = 71, skip: Callable[[int, int], bool] | None = None) -> None:
        """Chain-link along a straight line (x0 == x1 or z0 == z1), on footings, barbed wire along its top; where the
        noise is high (over 1 - {fallen}) a run of it has come down: the mesh gone, a stub of post left now and then."""
        steps = max(abs(x1 - x0), abs(z1 - z0))
        for k in range(steps + 1):
            x = x0 + (x1 - x0) * k // max(steps, 1)
            z = z0 + (z1 - z0) * k // max(steps, 1)
            if skip and skip(x, z):
                continue
            self.footing(x, z)
            if fallen and self.n(x, 0, z, 5.0, salt) > 1 - fallen:
                if k % 4 == 0:
                    self.set(x, 1, z, CHAIN_LINK)          # a post left standing in the gap
                continue
            for y in range(1, height + 1):
                self.set(x, y, z, CHAIN_LINK)
            if barbed and self.n(x, 9, z, 4.0, salt + 1) > 0.18:
                self.set(x, height + 1, z, BARBED)

    # --- towers, poles, wires --------------------------------------------------------------------------------------------------
    def lattice_tower(self, x0: int, z0: int, w: int, d: int, y0: int, y1: int, block: str = LATTICE, ladder: bool = True) -> None:
        """A braced steel tower w x d from y0 to y1 (inclusive), a ladder up its middle if it is wider than one."""
        for x in range(x0, x0 + w):
            for z in range(z0, z0 + d):
                for y in range(y0, y1 + 1):
                    self.set(x, y, z, block)
                if y0 == 1:
                    self.footing(x, z, "polished_andesite")

    def pole(self, x: int, z: int, h: int, arm: str = "x") -> tuple[int, int, int]:
        """A wooden utility pole h high on a footing, a crossarm across its top with two insulators; returns where the
        wire leaves it (the pole's top)."""
        self.footing(x, z)
        for y in range(1, h + 1):
            self.set(x, y, z, "stripped_spruce_log", {"axis": "y"})
        dx, dz = (1, 0) if arm == "x" else (0, 1)
        for s in (-1, 1):
            self.set(x + dx * s, h, z + dz * s, "spruce_trapdoor", {"facing": (E if s > 0 else W) if arm == "x" else (S if s > 0 else N),
                                                                     "half": "top", "open": False})
            self.set(x + dx * s, h + 1, z + dz * s, "lightning_rod", {"facing": "up"})
        return x, h, z

    def wire(self, a: tuple[int, int, int], b: tuple[int, int, int], sag: float = 1.5) -> None:
        """A wire hung between two points (a run of chain, sagging in the middle); along x or z mostly."""
        (ax, ay, az), (bx, by, bz) = a, b
        steps = max(abs(bx - ax), abs(bz - az))
        axis = "x" if abs(bx - ax) >= abs(bz - az) else "z"
        for k in range(1, steps):
            t = k / steps
            x = round(ax + (bx - ax) * t)
            z = round(az + (bz - az) * t)
            y = round(ay + (by - ay) * t - sag * 4 * t * (1 - t))
            if self.name(x, y, z) is None:
                self.set(x, y, z, "iron_chain", {"axis": axis})

    def floodlight_pole(self, x: int, z: int, h: int, facing: str) -> None:
        """A galvanised pole (lattice one wide) on a footing with a floodlight at its top turned {facing}."""
        self.footing(x, z, "polished_andesite")
        for y in range(1, h + 1):
            self.set(x, y, z, LATTICE)
        fx, fz = STEP[facing]
        self.set(x + fx, h, z + fz, FLOODLIGHT, {"facing": facing})

    # --- radomes -------------------------------------------------------------------------------------------------------------------
    def radome(self, cx: float, cy: float, cz: float, r: float, floor: int, missing: Callable[[int, int, int], bool] | None = None
               ) -> None:
        """A sphere of radome panels {r} round (cx, cy, cz), cut off at y = {floor}: a shell one thick, its top smoothed
        with slabs and its shoulders with stairs; {missing} says where panels have blown out."""
        top = math.ceil(cy + r)
        x0, x1 = math.floor(cx - r - 1), math.ceil(cx + r + 1)
        z0, z1 = math.floor(cz - r - 1), math.ceil(cz + r + 1)
        def inside(x, y, z):
            return (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 + (z + 0.5 - cz) ** 2 <= r * r
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                for y in range(floor, top + 1):
                    if not inside(x, y, z):
                        continue
                    shell = not all(inside(x + dx, y + dy, z + dz) for dx, dy, dz in
                                    ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)) if y + dy >= floor)
                    if not shell:
                        continue
                    if missing and missing(x, y, z):
                        continue
                    self.set(x, y, z, RADOME)
                # The top surface of the column: a slab where the sphere's surface stands half a block over its top
                # block. Its shoulders: a stair, its high side to the middle, where the surface falls away steeply.
                ys = [y for y in range(floor, top + 1) if inside(x, y, z)]
                if not ys:
                    continue
                yt = max(ys)
                h = cy + math.sqrt(max(0.0, r * r - (x + 0.5 - cx) ** 2 - (z + 0.5 - cz) ** 2))
                if missing and missing(x, yt + 1, z):
                    continue
                frac = h - (yt + 1)
                dx, dz = x + 0.5 - cx, z + 0.5 - cz
                steep = math.hypot(dx, dz) > r * 0.55
                if frac >= 0.3 and self.name(x, yt + 1, z) is None:
                    if steep:
                        face = (W if dx > 0 else E) if abs(dx) >= abs(dz) else (N if dz > 0 else S)
                        self.set(x, yt + 1, z, RADOME_STAIRS, {"facing": face, "half": "bottom", "shape": "straight", "waterlogged": False})
                    else:
                        self.set(x, yt + 1, z, RADOME_SLAB, {"type": "bottom", "waterlogged": False})

    # --- walls --------------------------------------------------------------------------------------------------------------------
    def clad(self, x0: int, y0: int, z0: int, x1: int, y1: int, z1: int, salt: int = 91, rust_courses: float = 1.4,
             clean: str = CORRUGATED, rusted: str = RUSTED) -> None:
        """The outer ring of a box clad in corrugated sheet, rusted along its foot: the rust line rises and falls with
        the noise about {rust_courses} courses up, higher where water runs (the corners)."""
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if x not in (x0, x1) and z not in (z0, z1):
                    continue
                corner = (x in (x0, x1)) and (z in (z0, z1))
                line = rust_courses + (self.n(x, 0, z, 3.0, salt) - 0.5) * 2.0 + (0.8 if corner else 0.0)
                for y in range(y0, y1 + 1):
                    self.set(x, y, z, rusted if (y - y0) < line else clean)

    def windows(self, face: str, x0: int, z0: int, x1: int, z1: int, y: int, every: int = 3, height: int = 1, salt: int = 101,
                broken: float = 0.35) -> None:
        """Windows along one wall (face: the way the wall looks out), every few blocks: glass set in, broken out in places
        (clusters, by noise); the sill under each a slab of steel."""
        steps = max(abs(x1 - x0), abs(z1 - z0))
        for k in range(1, steps, every):
            x = x0 + (x1 - x0) * k // max(steps, 1)
            z = z0 + (z1 - z0) * k // max(steps, 1)
            for dy in range(height):
                gone = self.n(x, y + dy, z, 3.0, salt) < broken
                self.set(x, y + dy, z, AIR if gone else "glass_pane")
            fx, fz = STEP[face]
            self.set(x + fx, y - 1, z + fz, "smooth_stone_slab", {"type": "top", "waterlogged": False}, keep=True)

    def debris(self, box: tuple, blocks: list[str], density: float, salt: int = 113, on_ground: bool = True) -> None:
        """Litter in clusters on whatever is under it (the ground, a floor): torn sheeting, panels, bits of rubble."""
        x0, y0, z0, x1, y1, z1 = box
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if self.n(x, 0, z, 2.5, salt) < 1 - density:
                    continue
                for y in range(y0, y1 + 1):
                    if self.name(x, y, z) is None and (self.solid(x, y - 1, z) or (on_ground and y == 1)):
                        blk = blocks[int(self.n(x, 3, z, 1.3, salt + 1) * len(blocks)) % len(blocks)]
                        if "slab" in blk:
                            self.set(x, y, z, blk, {"type": "bottom", "waterlogged": False})
                        elif "trapdoor" in blk:
                            self.set(x, y, z, blk, {"facing": DIR4[(x + z) % 4], "half": "bottom", "open": False})
                        else:
                            self.set(x, y, z, blk)
                        break

    def overgrow(self, box: tuple, density: float = 0.4, salt: int = 127) -> None:
        """Grass and ferns on the land (layer 1 over unset ground) in clumps, thicker by walls and fences."""
        x0, z0, x1, z1 = box
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if self.name(x, 0, z) not in (None,) or self.name(x, 1, z) is not None:
                    continue
                near = any(self.name(x + dx, 1, z + dz) is not None for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))
                v = self.n(x, 0, z, 2.2, salt) + (0.18 if near else 0.0)
                if v > 1 - density:
                    self.set(x, 1, z, "tall_grass" if v > 0.93 else "fern" if v > 0.86 else "short_grass",
                             {"half": "lower"} if v > 0.93 else None)
                    if v > 0.93:
                        self.set(x, 2, z, "tall_grass", {"half": "upper"})

    def vines(self, box: tuple, face: str, density: float = 0.3, salt: int = 131) -> None:
        """Vines hanging down the {face} side of walls in the box (on the air beside them), in clumps, longer low."""
        x0, y0, z0, x1, y1, z1 = box
        fx, fz = STEP[face]
        attach = OPPOSITE[face]
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                for y in range(y1, y0 - 1, -1):
                    if not self.solid(x, y, z) or self.name(x + fx, y, z + fz) is not None:
                        continue
                    if self.n(x, y * 0.5, z, 2.5, salt) > 1 - density * (1.2 - y / max(1, y1)):
                        self.set(x + fx, y, z + fz, "vine", {attach: True, "up": False, "north": attach == N, "south": attach == S,
                                                             "east": attach == E, "west": attach == W})
