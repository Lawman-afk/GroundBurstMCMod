"""Builds Minecraft structure templates (the .nbt format written by structure blocks)."""
from __future__ import annotations

from pathlib import Path

from nbt import Int, TAG_COMPOUND, TypedList, write_gzipped

# Data version of Minecraft 26.3 (version.json "world_version").
DATA_VERSION = 5023


def _state_key(name: str, props: dict[str, str] | None) -> tuple:
    return (name, tuple(sorted((props or {}).items())))


class Template:
    """A sparse block template. Unset positions inside the size are left untouched when placed,
    so fill air explicitly where the template must clear space."""

    def __init__(self, size_x: int, size_y: int, size_z: int):
        self.size = (size_x, size_y, size_z)
        self._palette: list[tuple] = []
        self._palette_index: dict[tuple, int] = {}
        self._blocks: dict[tuple[int, int, int], int] = {}
        self._nbt: dict[tuple[int, int, int], dict] = {}

    def set(self, x: int, y: int, z: int, name: str, props: dict[str, str] | None = None, nbt: dict | None = None) -> None:
        sx, sy, sz = self.size
        if not (0 <= x < sx and 0 <= y < sy and 0 <= z < sz):
            raise ValueError(f"({x},{y},{z}) outside template of size {self.size}")
        key = _state_key(name, props)
        index = self._palette_index.get(key)
        if index is None:
            index = len(self._palette)
            self._palette.append(key)
            self._palette_index[key] = index
        self._blocks[(x, y, z)] = index
        if nbt is not None:
            self._nbt[(x, y, z)] = nbt
        else:
            self._nbt.pop((x, y, z), None)

    def get(self, x: int, y: int, z: int) -> tuple[str, dict[str, str]] | None:
        """The block set at a position (name, properties), or None."""
        i = self._blocks.get((x, y, z))
        if i is None:
            return None
        name, props = self._palette[i]
        return name, dict(props)

    def remove(self, x: int, y: int, z: int) -> None:
        """Unsets a position (left untouched when placed)."""
        self._blocks.pop((x, y, z), None)
        self._nbt.pop((x, y, z), None)

    def blocks(self):
        """Every set position with its block: ((x, y, z), name, props)."""
        for pos, i in self._blocks.items():
            name, props = self._palette[i]
            yield pos, name, dict(props)

    def fill(self, x0: int, y0: int, z0: int, x1: int, y1: int, z1: int, name: str, props: dict[str, str] | None = None) -> None:
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.set(x, y, z, name, props)

    def to_nbt(self) -> dict:
        palette = []
        for name, props in self._palette:
            # 26.3 block-state NBT uses lowercase "id"/"properties" (older versions used "Name"/"Properties").
            entry = {"id": name}
            if props:
                entry["properties"] = dict(props)
            palette.append(entry)
        blocks = []
        for (x, y, z), state in sorted(self._blocks.items(), key=lambda kv: (kv[0][1], kv[0][2], kv[0][0])):
            entry = {"pos": [Int(x), Int(y), Int(z)], "state": Int(state)}
            if (x, y, z) in self._nbt:
                entry["nbt"] = self._nbt[(x, y, z)]
            blocks.append(entry)
        return {
            "DataVersion": Int(DATA_VERSION),
            "size": [Int(v) for v in self.size],
            "palette": TypedList(TAG_COMPOUND, palette),
            "blocks": TypedList(TAG_COMPOUND, blocks),
            "entities": TypedList(TAG_COMPOUND, []),
        }

    def save(self, path: Path) -> None:
        write_gzipped(path, self.to_nbt())
