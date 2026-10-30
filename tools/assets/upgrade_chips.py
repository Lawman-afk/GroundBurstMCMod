"""Upgrade chips (DESIGN.md §7.3): item sprites plus their model / item-definition JSON.

A chip is a plug-in card: a small board on a gold edge connector, built on one of the four circuit tiers and told apart
the same way the circuits are, by the board's colour (basic green, advanced blue, military olive drab, quantum black)
and by pips along its top (one to four: gold, the quantum's glowing violet). On its face a label plate says its kind:
amber with a double chevron for speed, green with a lightning bolt for efficiency, and for balanced the two halves side
by side, a chevron on amber and a bolt on green.
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))

from gb import palette as P  # noqa: E402
from gb.sprite import outline  # noqa: E402
from gb.tex import Tex  # noqa: E402
import advanced_parts as AP  # noqa: E402
import resources as R  # noqa: E402

TIERS = [("basic", AP.GREEN), ("advanced", AP.BLUE), ("military", AP.DRAB), ("quantum", AP.BLACK)]
KINDS = ["speed", "efficiency", "balanced"]
GOLD = AP.GOLD
SPEED = P.NUCLEAR_AMBER
EFFICIENCY = P.INDICATOR_GREEN
WHITE = P.WHITE_CERAMIC[6]
ITEMS: list[str] = []

# The card (x 3..12, y 2..12), its pips (y 3), its plate (x 4..11, y 4..10), its connector (y 13..14).
CARD = (3, 2, 12, 12)
PLATE = (4, 4, 8, 7)
# Pictograms on the plate, (x, y) from its corner: rows 1..5 are its face (row 0 lit, row 6 shaded).
CHEVRONS = [(1, 1), (2, 2), (3, 3), (2, 4), (1, 5), (4, 1), (5, 2), (6, 3), (5, 4), (4, 5)]
BOLT = [(4, 1), (5, 1), (3, 2), (4, 2), (2, 3), (3, 3), (4, 3), (5, 3), (3, 4), (4, 4), (2, 5), (3, 5)]
HALF_CHEVRON = [(1, 2), (2, 3), (1, 4)]
HALF_BOLT = [(6, 1), (5, 2), (6, 2), (5, 3), (6, 3), (5, 4)]


def plate(t: Tex, kind: str) -> None:
    """The label plate: its colour (amber, green, or half of each) lit along the top, its pictogram in white."""
    x0, y0, w, h = PLATE
    for yy in range(h):
        for xx in range(w):
            ramp = SPEED if kind == "speed" or kind == "balanced" and xx < w // 2 else EFFICIENCY
            tone = ramp[5] if yy == 0 else ramp[2] if yy == h - 1 else ramp[4] if xx < w - 1 else ramp[3]
            t.px(x0 + xx, y0 + yy, tone)
    marks = CHEVRONS if kind == "speed" else BOLT if kind == "efficiency" else HALF_CHEVRON + HALF_BOLT
    for xx, yy in marks:
        t.px(x0 + xx, y0 + yy, WHITE)


def chip(tier: str, ramp, level: int, kind: str) -> None:
    t = Tex(16, 16)
    x0, y0, x1, y1 = CARD
    w, h = x1 - x0 + 1, y1 - y0 + 1
    t.rect(x0, y0, w, h, ramp[4])
    t.hline(x0, y0, w, ramp[5])                                   # lit along the top
    t.vline(x0, y0 + 1, h - 1, ramp[5])
    t.hline(x0, y1, w, ramp[3])                                   # shaded along the bottom and right
    t.vline(x1, y0 + 1, h - 1, ramp[3])
    # The edge connector: gold fingers under the card, a gap between each.
    for x in range(x0 + 1, x1):
        if (x - x0) % 2 == 1:
            t.px(x, y1 + 1, GOLD[5])
            t.px(x, y1 + 2, GOLD[3])
    # Pips along the top: its tier.
    for i in range(level):
        t.px(x0 + 1 + 2 * i, y0 + 1, P.SINGULARITY[6] if tier == "quantum" else GOLD[6])
    plate(t, kind)
    pts = {(x, y) for y in range(16) for x in range(16) if t.get(x, y)[3]}
    outline(t, pts, ramp.outline)
    name = f"{tier}_{kind}_chip"
    R.save_item(name, t)
    ITEMS.append(name)


def build() -> None:
    for kind in KINDS:
        for level, (tier, ramp) in enumerate(TIERS, start=1):
            chip(tier, ramp, level, kind)


if __name__ == "__main__":
    build()
    print(R.item_sheet(ITEMS, "item", "upgrade_chips_review.png"))
