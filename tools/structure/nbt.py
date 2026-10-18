"""Minimal NBT writer for Minecraft structure templates (and anything else NBT).

Python values map to NBT types as follows unless wrapped explicitly:
    bool/int -> Int, float -> Double, str -> String, dict -> Compound, list -> List (element type inferred).
Wrap with Byte/Short/Int/Long/Float/Double/IntArray/LongArray/ByteArray/TypedList to force a type.
"""
from __future__ import annotations

import gzip
import io
import struct
from dataclasses import dataclass
from pathlib import Path
from typing import Any

TAG_END, TAG_BYTE, TAG_SHORT, TAG_INT, TAG_LONG, TAG_FLOAT, TAG_DOUBLE = 0, 1, 2, 3, 4, 5, 6
TAG_BYTE_ARRAY, TAG_STRING, TAG_LIST, TAG_COMPOUND, TAG_INT_ARRAY, TAG_LONG_ARRAY = 7, 8, 9, 10, 11, 12


@dataclass(frozen=True)
class Byte:
    value: int


@dataclass(frozen=True)
class Short:
    value: int


@dataclass(frozen=True)
class Int:
    value: int


@dataclass(frozen=True)
class Long:
    value: int


@dataclass(frozen=True)
class Float:
    value: float


@dataclass(frozen=True)
class Double:
    value: float


@dataclass(frozen=True)
class ByteArray:
    values: bytes


@dataclass(frozen=True)
class IntArray:
    values: list[int]


@dataclass(frozen=True)
class LongArray:
    values: list[int]


@dataclass(frozen=True)
class TypedList:
    """A list whose element tag type is given explicitly (needed for empty lists and numeric lists)."""
    tag: int
    values: list[Any]


def _tag_of(value: Any) -> int:
    if isinstance(value, Byte):
        return TAG_BYTE
    if isinstance(value, Short):
        return TAG_SHORT
    if isinstance(value, (Int, bool)) or isinstance(value, int):
        return TAG_INT
    if isinstance(value, Long):
        return TAG_LONG
    if isinstance(value, Float):
        return TAG_FLOAT
    if isinstance(value, (Double, float)):
        return TAG_DOUBLE
    if isinstance(value, ByteArray):
        return TAG_BYTE_ARRAY
    if isinstance(value, str):
        return TAG_STRING
    if isinstance(value, (list, TypedList)):
        return TAG_LIST
    if isinstance(value, dict):
        return TAG_COMPOUND
    if isinstance(value, IntArray):
        return TAG_INT_ARRAY
    if isinstance(value, LongArray):
        return TAG_LONG_ARRAY
    raise TypeError(f"Cannot encode {type(value).__name__} as NBT")


def _write_string(out: io.BytesIO, s: str) -> None:
    data = s.encode("utf-8")  # Java modified UTF-8 only differs for NUL and supplementary chars
    out.write(struct.pack(">H", len(data)))
    out.write(data)


def _write_payload(out: io.BytesIO, tag: int, value: Any) -> None:
    raw = getattr(value, "value", value)
    if tag == TAG_BYTE:
        out.write(struct.pack(">b", int(raw)))
    elif tag == TAG_SHORT:
        out.write(struct.pack(">h", int(raw)))
    elif tag == TAG_INT:
        out.write(struct.pack(">i", int(raw)))
    elif tag == TAG_LONG:
        out.write(struct.pack(">q", int(raw)))
    elif tag == TAG_FLOAT:
        out.write(struct.pack(">f", float(raw)))
    elif tag == TAG_DOUBLE:
        out.write(struct.pack(">d", float(raw)))
    elif tag == TAG_BYTE_ARRAY:
        out.write(struct.pack(">i", len(value.values)))
        out.write(bytes(value.values))
    elif tag == TAG_STRING:
        _write_string(out, value)
    elif tag == TAG_LIST:
        if isinstance(value, TypedList):
            elem_tag, items = value.tag, value.values
        else:
            items = value
            elem_tag = _tag_of(items[0]) if items else TAG_END
            for item in items:
                if _tag_of(item) != elem_tag:
                    raise TypeError("NBT lists must be homogeneous; wrap values or use TypedList")
        out.write(struct.pack(">bi", elem_tag, len(items)))
        for item in items:
            _write_payload(out, elem_tag, item)
    elif tag == TAG_COMPOUND:
        for key, item in value.items():
            item_tag = _tag_of(item)
            out.write(struct.pack(">b", item_tag))
            _write_string(out, key)
            _write_payload(out, item_tag, item)
        out.write(struct.pack(">b", TAG_END))
    elif tag == TAG_INT_ARRAY:
        out.write(struct.pack(">i", len(value.values)))
        out.write(struct.pack(f">{len(value.values)}i", *value.values))
    elif tag == TAG_LONG_ARRAY:
        out.write(struct.pack(">i", len(value.values)))
        out.write(struct.pack(f">{len(value.values)}q", *value.values))
    else:
        raise ValueError(f"Unknown tag {tag}")


def encode(root: dict) -> bytes:
    """Encodes a root compound (unnamed) to uncompressed NBT bytes."""
    out = io.BytesIO()
    out.write(struct.pack(">b", TAG_COMPOUND))
    _write_string(out, "")
    _write_payload(out, TAG_COMPOUND, root)
    return out.getvalue()


def write_gzipped(path: Path, root: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    # mtime=0 keeps output byte-identical between runs, so regenerated files don't show as changed in git.
    with open(path, "wb") as f:
        with gzip.GzipFile(fileobj=f, mode="wb", mtime=0) as gz:
            gz.write(encode(root))
