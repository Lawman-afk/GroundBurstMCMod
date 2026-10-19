"""Phase 4 slice 5 nuclear and exotic materials (DESIGN.md §6, §26.3): Gas Centrifuge, Fusion Fuel Synthesizer and
Exotic Matter Fabricator products. Item sprites plus their model / item-definition JSON.

Ingots and dusts use the shared vanilla-style silhouettes (gz.vanilla); enriched uranium is a brighter, greener
metal than natural uranium and depleted uranium a dense dark grey, so the three read apart at a glance.
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))

from gz import palette as P  # noqa: E402
from gz import vanilla as V  # noqa: E402
from gz.palette import fixed  # noqa: E402
from gz.sprite import faceted_blob, outline  # noqa: E402
from gz.tex import Tex  # noqa: E402
import parts as PT  # noqa: E402
import resources as R  # noqa: E402

ENRICHED = fixed("enriched_uranium", "#0e1a0e", "#1c3018", "#2c4826", "#3e6234", "#557f44", "#78a35c", "#b4d894")
DEPLETED = fixed("depleted_uranium", "#0f1012", "#1a1c1f", "#282b2f", "#383c41", "#4b5056", "#666c73", "#959ba2")
DEPLETED_DUST = fixed("depleted_uranium_dust", "#121412", "#20231f", "#32362f", "#474c43", "#5f6559", "#7f8678", "#aeb5a4")
LID = fixed("lithium_deuteride", "#2a3038", "#46505c", "#68737f", "#8e98a3", "#b4bcc4", "#d6dbe0", "#f4f6f8")
SHARD = fixed("exotic_violet", "#12041e", "#2a0a46", "#4a1678", "#6e26ac", "#9444d8", "#b878f0", "#e4c8ff")
STEEL = PT.STEEL
ITEMS: list[str] = []


def save(name: str, t: Tex) -> None:
    R.save_item(name, t)
    ITEMS.append(name)


def nugget(name: str, ramp) -> None:
    """A small lump like vanilla's nuggets: three lobes with a flat facet, outlined in the metal's own dark."""
    t = Tex(16, 16)
    pts = faceted_blob(t, [(7.2, 8.6, 2.7), (9.8, 10.0, 2.0), (5.0, 10.4, 1.8)], ramp, facets=1, seed=7)
    outline(t, pts, ramp.outline)
    save(name, t)


def cylinder(t: Tex, cx: float, top: float, rx: float, ry: float, h: int, ramp) -> set[tuple[int, int]]:
    """A short upright cylinder seen from above-front: bright elliptical top, body shaded from lit left to dark right,
    with a darker line where the top meets the body so the two read apart."""
    pts: set[tuple[int, int]] = set()
    for y in range(16):
        for x in range(16):
            u = (x + 0.5 - cx) / rx
            in_top = u * u + ((y + 0.5 - top) / ry) ** 2 <= 1
            in_bot = u * u + ((y + 0.5 - top - h) / ry) ** 2 <= 1
            in_body = abs(u) <= 1 and top <= y + 0.5 <= top + h
            if in_top:
                t.px(x, y, ramp[6] if u < 0 else ramp[5])
                pts.add((x, y))
            elif in_body or in_bot:
                rim = u * u + ((y - 0.5 - top) / ry) ** 2 <= 1          # first row under the top ellipse
                tone = 4 if u < -0.5 else 3 if u < 0.1 else 2 if u < 0.6 else 1
                t.px(x, y, ramp[max(1, tone - 1)] if rim else ramp[tone])
                pts.add((x, y))
    return pts


def lid_pellets() -> None:
    """Two pressed lithium deuteride pellets: one standing behind, one in front."""
    t = Tex(16, 16)
    for cx, top in ((10.0, 3.0), (6.0, 6.5)):
        pts = cylinder(t, cx, top, 3.6, 1.8, 4, LID)
        outline(t, pts, LID.outline)
    save("lithium_deuteride_pellet", t)


def stabilised_exotic_matter() -> None:
    """An exotic crystal with a black core, held in a steel stabiliser ring (back arc behind, front arc over it)."""
    t = Tex(16, 16)
    cx, cy = 7.5, 7.5
    ring = []
    for y in range(16):
        for x in range(16):
            d = ((x + 0.5 - cx) / 6.6) ** 2 + ((y + 0.5 - cy) / 2.0) ** 2
            if 0.55 <= d <= 1.0:
                ring.append((x, y))
    for x, y in ring:
        if y + 0.5 < cy:
            t.px(x, y, STEEL[3])
    crystal = set()
    for y in range(1, 15):
        half = (y - 0.5) * 0.62 if y <= 7 else (14.5 - y) * 0.62
        for x in range(16):
            dx = x + 0.5 - cx
            if abs(dx) <= half + 0.01:
                tone = 6 if abs(dx) < 0.6 and y < 7 else 5 if dx < 0 and y < 8 else 4 if dx < 0 else 3 if y < 8 else 2
                t.px(x, y, SHARD[tone])
                crystal.add((x, y))
    t.px(7, 7, SHARD[0])                                                    # black core
    t.px(8, 7, SHARD[0])
    t.px(7, 8, SHARD[1])
    outline(t, crystal, SHARD.outline)
    for x, y in ring:
        if y + 0.5 >= cy:
            t.px(x, y, STEEL[5] if x < cx else STEEL[4])
    for x, y in ((1, 7), (14, 7)):
        t.px(x, y, P.BRASS[5])                                              # clamp bolts
    save("stabilised_exotic_matter", t)


def containment_cell() -> None:
    """An empty magnetic bottle: steel end caps, a glass tube with two glowing field rings and nothing inside."""
    rows = [
        "................",
        ".....oooooo.....",
        "....o566665o....",
        "....o455554o....",
        "....oooooooo....",
        "....ogGGGGGo....",
        "....oCCCCCCo....",
        "....og....ko....",
        "....og....ko....",
        "....og....ko....",
        "....oCCCCCCo....",
        "....ogGGGGko....",
        "....oooooooo....",
        "....o455554o....",
        ".....o3333o.....",
        "......oooo......",
    ]
    legend = {"o": STEEL.outline, "g": (170, 200, 214, 255), "G": (120, 150, 166, 255), "k": (64, 84, 98, 255),
              "C": P.CHERENKOV[5], ".": None}
    t = Tex(16, 16)
    for y, row in enumerate(rows):
        for x, c in enumerate(row):
            if c == "." and 4 < x < 11 and 4 < y < 12:
                t.px(x, y, (22, 30, 38, 255))                               # the empty, dark inside of the tube
            elif c.isdigit():
                t.px(x, y, STEEL[int(c)])
            elif c != ".":
                t.px(x, y, legend[c])
    save("containment_cell", t)


def singularity_seed() -> None:
    """A matte-black sphere with a violet lensing rim, ringed by a thin accretion band: its far arc passes behind
    the sphere, its near arc sweeps across the lower half in front of it (hand-placed so it reads at 1x)."""
    t = Tex(16, 16)
    cx, cy, r = 7.5, 7.5, 4.8
    far = [(0, 7), (1, 6), (2, 6), (3, 5), (12, 5), (13, 6), (14, 6), (15, 7)]
    near = [(1, 8), (2, 8), (3, 9), (4, 9), (5, 10), (6, 10), (7, 10), (8, 10), (9, 10), (10, 10), (11, 9), (12, 9), (13, 8), (14, 8)]
    for x, y in far:
        t.px(x, y, SHARD[3])
    pts = set()
    for y in range(16):
        for x in range(16):
            d = ((x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2) ** 0.5
            if d <= r:
                t.px(x, y, SHARD[2] if d > r - 0.8 else (6, 4, 10, 255))
                pts.add((x, y))
    outline(t, pts, SHARD[4])
    for x, y in near:
        t.px(x, y, SHARD[6] if 4 <= x <= 11 else SHARD[5])
    save("singularity_seed", t)


def build() -> None:
    nugget("enriched_uranium_nugget", ENRICHED)
    save("enriched_uranium_ingot", V.ingot(ENRICHED))
    save("depleted_uranium_dust", V.dust2(DEPLETED_DUST, 21, sparkle=True))
    save("depleted_uranium_ingot", V.ingot(DEPLETED))
    lid_pellets()
    stabilised_exotic_matter()
    containment_cell()
    singularity_seed()


if __name__ == "__main__":
    build()
    print(R.item_sheet(ITEMS + ["uranium_ingot", "uranium_dust", "exotic_shard"], "item", "nuclear_review.png"))
