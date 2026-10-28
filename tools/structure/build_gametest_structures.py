"""Generates the structure templates used by GameTests (data/groundburst/structure/gametest/)."""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from template import Template  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "src" / "main" / "resources" / "data" / "groundburst" / "structure" / "gametest"


def floor(size: int, height: int) -> Template:
    """A smooth-stone floor with explicit air above, so leftovers from earlier tests are cleared."""
    t = Template(size, height, size)
    t.fill(0, 0, 0, size - 1, 0, size - 1, "minecraft:smooth_stone")
    t.fill(0, 1, 0, size - 1, height - 1, size - 1, "minecraft:air")
    return t


def solid(size: int, block: str) -> Template:
    """A cube of one block: rock for ore features to replace."""
    t = Template(size, size, size)
    t.fill(0, 0, 0, size - 1, size - 1, size - 1, block)
    return t


def main() -> None:
    floor(16, 16).save(OUT / "floor_16.nbt")
    solid(12, "minecraft:stone").save(OUT / "stone_12.nbt")
    print(f"Wrote gametest structures to {OUT}")


if __name__ == "__main__":
    main()
