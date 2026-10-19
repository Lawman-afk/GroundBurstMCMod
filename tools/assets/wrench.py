"""Wrench item sprite (DESIGN.md §25): a steel open-end spanner held diagonally like vanilla tools (handle bottom-left,
head top-right), the jaw opening toward the top-right corner, a red rubber grip on the handle end, dark outline.
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))

from gz import palette as P  # noqa: E402
from gz.tex import Tex  # noqa: E402
import parts as PT  # noqa: E402
import resources as R  # noqa: E402


def _seg_dist(px: float, py: float, ax: float, ay: float, bx: float, by: float) -> tuple[float, float]:
    vx, vy = bx - ax, by - ay
    t = max(0.0, min(1.0, ((px - ax) * vx + (py - ay) * vy) / (vx * vx + vy * vy)))
    return math.hypot(px - (ax + t * vx), py - (ay + t * vy)), t


def wrench() -> Tex:
    s = P.STEEL
    head = (10.8, 4.9)
    shape: dict[tuple[int, int], int] = {}
    for y in range(16):
        for x in range(16):
            cx, cy = x + 0.5, y + 0.5
            d, t = _seg_dist(cx, cy, 3.0, 13.0, 10.0, 6.0)
            in_handle = d <= 1.1
            hx, hy = cx - head[0], cy - head[1]
            in_head = math.hypot(hx, hy) <= 3.9
            # Jaw slot: a gap opening toward the top-right corner.
            along = (hx - hy) / math.sqrt(2)          # toward (+x, -y)
            across = (hx + hy) / math.sqrt(2)
            in_slot = along > -0.8 and abs(across) <= 1.25
            if (in_handle or in_head) and not in_slot:
                # Tone: lit from the upper left across the tool, darker toward the lower right.
                k = 5 if across < -0.8 or (in_handle and (cx - cy) < -9.6) else 4 if across < 0.6 else 3
                if in_handle and t < 0.3:
                    k = -1                             # grip
                shape[(x, y)] = k
    t = Tex(16, 16)
    for (x, y), k in shape.items():
        if k < 0:
            t.px(x, y, P.WARNING_RED[3] if (x + y) % 3 else P.WARNING_RED[4])
        else:
            t.px(x, y, s[k])
    # Outline: dark pixel on every empty neighbour of the shape.
    for (x, y) in list(shape):
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            n = (x + dx, y + dy)
            if n not in shape and 0 <= n[0] < 16 and 0 <= n[1] < 16:
                t.px(*n, s.outline)
    t.px(9, 3, s.shine)                                # glint on the head
    return t


if __name__ == "__main__":
    PT.save("wrench", wrench())
    print(R.item_sheet(PT.ITEMS, "item", "wrench_review.png"))
