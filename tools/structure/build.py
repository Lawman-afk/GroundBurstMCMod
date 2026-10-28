"""Authoring structure templates by script (DESIGN.md §22, Phase 15).

A `Build` wraps a `Template` (the .nbt a structure block saves) with the helpers a site needs: boxes, rooms, rings and
cylinders, doors, stairs, ladders, chests with their loot table, data markers (what a block cannot be: an armour stand),
and wear: cracks, moss, rust stains, cobwebs, rubble, broken glass. Wear is drawn into the template from a seed (each
site designed, not noise), and `SitePiece` weathers it a little more as it is placed, so no two look the same.

Coordinates are the template's own: x east, y up, z south, (0, 0, 0) its corner. Blocks are vanilla names unless they
have a namespace ("groundburst:..."). Neighbour shapes (fences, panes, bars, walls, cables) are worked out by the game
when the template is placed: they need no connection properties here.
"""
from __future__ import annotations

import math
import random
from pathlib import Path
from typing import Callable, Iterable

from template import Template

AIR = "minecraft:air"
FACINGS = ("north", "east", "south", "west")
OPPOSITE = {"north": "south", "south": "north", "east": "west", "west": "east", "up": "down", "down": "up"}
STEP = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}


def mc(name: str) -> str:
    return name if ":" in name else "minecraft:" + name


def gb(name: str) -> str:
    return "groundburst:" + name


class Build:
    def __init__(self, sx: int, sy: int, sz: int, seed: int = 0):
        self.t = Template(sx, sy, sz)
        self.size = (sx, sy, sz)
        self.rng = random.Random(seed)

    # --- basics ------------------------------------------------------------------------------------------------------
    def inside(self, x: int, y: int, z: int) -> bool:
        sx, sy, sz = self.size
        return 0 <= x < sx and 0 <= y < sy and 0 <= z < sz

    def set(self, x: int, y: int, z: int, block: str, props: dict | None = None, nbt: dict | None = None, keep: bool = False) -> None:
        """Sets a block (silently ignored outside the template); keep: only where nothing is set yet."""
        if not self.inside(x, y, z):
            return
        if keep and self.t.get(x, y, z) is not None:
            return
        self.t.set(x, y, z, mc(block), {k: str(v).lower() if isinstance(v, bool) else str(v) for k, v in (props or {}).items()} or None, nbt)

    def name(self, x: int, y: int, z: int) -> str | None:
        got = self.t.get(x, y, z) if self.inside(x, y, z) else None
        return got[0] if got else None

    def solid(self, x: int, y: int, z: int) -> bool:
        """Whether the block there is a whole one to build against (not a pane, plant, fitting or thin post). Matched on
        the end of the name for panes and grass, so a steel panel and a grass block count as whole."""
        n = self.name(x, y, z)
        if n is None or n == AIR:
            return False
        path = n.split(":", 1)[-1]
        if path.endswith("pane") or path.endswith("grass"):
            return False
        return not any(k in path for k in ("water", "torch", "lantern", "carpet", "button", "rail", "iron_chain", "bars",
                                           "fence", "ladder", "cobweb", "_wall", "lever", "flower", "fern", "bush",
                                           "trapdoor", "door", "sign", "candle", "structure_block"))

    def unset(self, x: int, y: int, z: int) -> None:
        if self.inside(x, y, z):
            self.t.remove(x, y, z)

    def fill(self, x0: int, y0: int, z0: int, x1: int, y1: int, z1: int, block: str, props: dict | None = None, keep: bool = False) -> None:
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.set(x, y, z, block, props, keep=keep)

    def clear(self, x0: int, y0: int, z0: int, x1: int, y1: int, z1: int) -> None:
        self.fill(x0, y0, z0, x1, y1, z1, AIR)

    def walls(self, x0: int, y0: int, z0: int, x1: int, y1: int, z1: int, block: str) -> None:
        """The four walls of a box (no floor or roof)."""
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.set(x, y, z0, block)
                self.set(x, y, z1, block)
            for z in range(z0, z1 + 1):
                self.set(x0, y, z, block)
                self.set(x1, y, z, block)

    def room(self, x0: int, y0: int, z0: int, x1: int, y1: int, z1: int, wall: str, floor: str | None = None,
             roof: str | None = None) -> None:
        """A closed box: walls, floor and roof (wall material unless given), air inside."""
        self.walls(x0, y0, z0, x1, y1, z1, wall)
        self.fill(x0, y0, z0, x1, y0, z1, floor or wall)
        self.fill(x0, y1, z0, x1, y1, z1, roof or wall)
        self.clear(x0 + 1, y0 + 1, z0 + 1, x1 - 1, y1 - 1, z1 - 1)

    def disc(self, cx: float, cz: float, r: float, y: int, block: str, inner: float = -1, props: dict | None = None) -> None:
        """A disc (or a ring from {inner} out) of radius {r} about (cx, cz), at height y."""
        for x in range(math.floor(cx - r - 1), math.ceil(cx + r + 1) + 1):
            for z in range(math.floor(cz - r - 1), math.ceil(cz + r + 1) + 1):
                d = math.hypot(x + 0.5 - cx, z + 0.5 - cz)
                if inner < d <= r:
                    self.set(x, y, z, block, props)

    def cylinder(self, cx: float, cz: float, r: float, y0: int, y1: int, block: str, inner: float = -1) -> None:
        for y in range(y0, y1 + 1):
            self.disc(cx, cz, r, y, block, inner)

    # --- furniture ------------------------------------------------------------------------------------------------------
    def chest(self, x: int, y: int, z: int, facing: str, loot: str, block: str = "chest") -> None:
        props = {"facing": facing, "waterlogged": "false"}
        if block in ("chest", "trapped_chest"):
            props["type"] = "single"
        self.set(x, y, z, block, props, nbt={"LootTable": loot if ":" in loot else "groundburst:chests/" + loot})

    def barrel(self, x: int, y: int, z: int, facing: str = "up", loot: str | None = None) -> None:
        nbt = {"LootTable": loot if ":" in loot else "groundburst:chests/" + loot} if loot else None
        self.set(x, y, z, "barrel", {"facing": facing, "open": "false"}, nbt=nbt)

    def marker(self, x: int, y: int, z: int, meta: str) -> None:
        """A data marker: SitePiece turns it into what it names (an armour stand)."""
        self.set(x, y, z, "structure_block", {"mode": "data"}, nbt={"mode": "DATA", "metadata": meta, "name": "", "author": "",
                                                                    "posX": 0, "posY": 1, "posZ": 0, "sizeX": 0, "sizeY": 0, "sizeZ": 0,
                                                                    "rotation": "NONE", "mirror": "NONE", "ignoreEntities": True,
                                                                    "powered": False, "showair": False, "showboundingbox": True,
                                                                    "integrity": 1.0, "seed": 0})

    def door(self, x: int, y: int, z: int, facing: str, block: str = "iron_door", hinge: str = "left", open_: bool = False) -> None:
        for half, dy in (("lower", 0), ("upper", 1)):
            self.set(x, y + dy, z, block, {"facing": facing, "half": half, "hinge": hinge, "open": open_, "powered": "false"})

    def ladder(self, x: int, y0: int, y1: int, z: int, facing: str) -> None:
        for y in range(y0, y1 + 1):
            self.set(x, y, z, "ladder", {"facing": facing, "waterlogged": "false"})

    def stairs(self, x: int, y: int, z: int, block: str, facing: str, half: str = "bottom", wet: bool = False) -> None:
        self.set(x, y, z, block, {"facing": facing, "half": half, "shape": "straight", "waterlogged": wet})

    def slab(self, x: int, y: int, z: int, block: str, kind: str = "bottom", wet: bool = False) -> None:
        self.set(x, y, z, block, {"type": kind, "waterlogged": wet})

    def trapdoor(self, x: int, y: int, z: int, block: str, facing: str, half: str = "bottom", open_: bool = False) -> None:
        self.set(x, y, z, block, {"facing": facing, "half": half, "open": open_, "powered": "false", "waterlogged": "false"})

    def lamp(self, x: int, y: int, z: int) -> None:
        """A ceiling lamp, long dead (a redstone lamp, unlit)."""
        self.set(x, y, z, "redstone_lamp", {"lit": "false"})

    def lantern(self, x: int, y: int, z: int, hanging: bool = True, block: str = "lantern") -> None:
        self.set(x, y, z, block, {"hanging": hanging, "waterlogged": "false"})

    def bed(self, x: int, y: int, z: int, facing: str, colour: str = "gray") -> None:
        """A bed whose foot is at (x, y, z), its head one block {facing}."""
        dx, dz = STEP[facing]
        self.set(x, y, z, f"{colour}_bed", {"facing": facing, "part": "foot", "occupied": "false"})
        self.set(x + dx, y, z + dz, f"{colour}_bed", {"facing": facing, "part": "head", "occupied": "false"})

    def pane_window(self, x0: int, y0: int, z0: int, x1: int, y1: int, z1: int, block: str = "glass_pane") -> None:
        self.fill(x0, y0, z0, x1, y1, z1, block)

    # --- wear -----------------------------------------------------------------------------------------------------------
    def each(self, x0: int, y0: int, z0: int, x1: int, y1: int, z1: int) -> Iterable[tuple[int, int, int]]:
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    yield x, y, z

    def swap(self, box: tuple, frm: str | Iterable[str], to: str | Callable[[], str], chance: float, props: dict | None = None) -> None:
        """Turns a share of the {frm} blocks in a box to {to} (a block, or a picker)."""
        frm = {mc(frm)} if isinstance(frm, str) else {mc(f) for f in frm}
        for x, y, z in self.each(*box):
            if self.name(x, y, z) in frm and self.rng.random() < chance:
                self.set(x, y, z, to() if callable(to) else to, props)

    def stains(self, box: tuple, on: Iterable[str], colours: tuple[str, ...], chance: float, run: int = 3) -> None:
        """Rust and water stains running down walls: from a seed block, a short streak downward in a darker block."""
        on = {mc(b) for b in on}
        for x, y, z in self.each(*box):
            if self.name(x, y, z) in on and self.rng.random() < chance:
                c = self.rng.choice(colours)
                for k in range(self.rng.randint(1, run)):
                    if self.name(x, y - k, z) in on:
                        self.set(x, y - k, z, c)

    def cobwebs(self, box: tuple, chance: float) -> None:
        """Cobwebs in the air of upper corners (an air block with solid above and on two sides)."""
        for x, y, z in self.each(*box):
            if self.name(x, y, z) != AIR or not self.solid(x, y + 1, z):
                continue
            sides = sum(self.solid(x + dx, y, z + dz) for dx, dz in STEP.values())
            if sides >= 2 and self.rng.random() < chance:
                self.set(x, y, z, "cobweb")

    def rubble(self, box: tuple, chance: float, blocks: tuple[str, ...] = ("cobblestone", "gravel", "andesite")) -> None:
        """Fallen debris on floors: a share of air blocks resting on something solid gets a rubble block (or a slab)."""
        for x, y, z in self.each(*box):
            if self.name(x, y, z) == AIR and self.solid(x, y - 1, z) and self.rng.random() < chance:
                b = self.rng.choice(blocks)
                if b.endswith("_slab"):
                    self.slab(x, y, z, b)
                else:
                    self.set(x, y, z, b)

    def moss(self, box: tuple, chance: float, on: Iterable[str]) -> None:
        """Moss carpets and a grass tuft on floors of the listed blocks, open above."""
        on = {mc(b) for b in on}
        for x, y, z in self.each(*box):
            if self.name(x, y, z) in on and self.name(x, y + 1, z) in (AIR, None) and self.rng.random() < chance:
                self.set(x, y + 1, z, self.rng.choice(("moss_carpet", "moss_carpet", "short_grass")))

    def flood(self, box: tuple, level: int) -> None:
        """Water in every air block of a box up to {level} (waterlogging what can be); a flooded ruin."""
        wettable = ("stairs", "slab", "ladder", "chest", "lantern", "iron_bars", "glass_pane", "iron_chain", "trapdoor", "fence", "_wall")
        x0, y0, z0, x1, y1, z1 = box
        for x, y, z in self.each(x0, y0, z0, x1, min(y1, level), z1):
            got = self.t.get(x, y, z)
            if got is None:
                continue
            name, props = got
            if name == AIR or name == mc("cobweb") or name == mc("moss_carpet") or name == mc("short_grass"):
                self.set(x, y, z, "water", {"level": "0"})
            elif any(w in name for w in wettable) and "waterlogged" in props:
                props["waterlogged"] = "true"
                nbt = self.t._nbt.get((x, y, z))
                self.t.set(x, y, z, name, props, nbt)

    # --- output ---------------------------------------------------------------------------------------------------------
    def save(self, path: Path) -> None:
        path.parent.mkdir(parents=True, exist_ok=True)
        self.t.save(path)
