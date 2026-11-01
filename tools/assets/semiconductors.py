"""The semiconductor chain's items (DESIGN.md §8.1): silicon, polysilicon, the quartz crucible and the ceramic
package. Item sprites plus their model / item-definition JSON.

Silicon is a lump of metallurgical silicon in flat, glassy facets; polysilicon a length broken off a Siemens rod, its
cut end showing the rings it grew in round the thin seed rod; the crucible a cup of milky fused quartz; the ceramic
package a white alumina housing, its gold seal ring round an empty gold cavity and gold leads down both sides.
"""
from __future__ import annotations

import math
import random
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))

from gb import palette as P  # noqa: E402
from gb import vanilla as V  # noqa: E402
from gb.palette import fixed  # noqa: E402
from gb.sprite import outline  # noqa: E402
from gb.tex import Tex  # noqa: E402
import parts as PT  # noqa: E402
import resources as R  # noqa: E402

SILICON = fixed("silicon", "#121620", "#232a38", "#363f52", "#4f5a72", "#717e98", "#a2aec4", "#e2e8f4")
POLYSILICON = fixed("polysilicon", "#1a1e26", "#2e343e", "#464e5a", "#646d7a", "#8a939e", "#b6bec6", "#eef2f6")
FUSED_QUARTZ = fixed("fused_quartz", "#47505a", "#6a747e", "#8e98a2", "#b2bbc3", "#d1d8dd", "#e8edf0", "#ffffff")
GOLD = PT.GOLD
ITEMS: list[str] = []


def save(name: str, t: Tex) -> None:
    R.save_item(name, t)
    ITEMS.append(name)


def _fill(t: Tex, poly: list[tuple[float, float]], color, drawn: set) -> None:
    for y in range(16):
        for x in range(16):
            if V._poly(poly, x + 0.5, y + 0.5):
                t.px(x, y, color)
                drawn.add((x, y))


def silicon() -> None:
    """Metallurgical silicon: a broken lump in flat, glassy facets, lit from the upper left, bright where they meet."""
    t = Tex(16, 16)
    drawn: set[tuple[int, int]] = set()
    s = SILICON
    # Facets back to front: (polygon, tone).
    facets = [
        ([(3, 6), (6, 3), (10, 2.5), (13.5, 5), (14, 9.5), (11, 13), (5.5, 13.5), (2, 10.5)], 3),
        ([(6, 3), (10, 2.5), (11.5, 5.5), (7, 6.5)], 6),            # top
        ([(3, 6), (6, 3), (7, 6.5), (4.5, 9)], 5),                  # upper left
        ([(10, 2.5), (13.5, 5), (12, 8), (11.5, 5.5)], 3),          # upper right
        ([(7, 6.5), (11.5, 5.5), (12, 8), (9.5, 10.5), (4.5, 9)], 4),  # the broad front facet
        ([(2, 10.5), (3, 6), (4.5, 9)], 3),                         # left
        ([(12, 8), (13.5, 5), (14, 9.5), (11, 13), (9.5, 10.5)], 2),  # right, in shade
        ([(4.5, 9), (9.5, 10.5), (11, 13), (5.5, 13.5), (2, 10.5)], 1),  # underside
    ]
    for poly, tone in facets:
        _fill(t, poly, s[tone], drawn)
    # Glassy edges: the ridges between the lit facets catch the light.
    for x, y in ((7, 6), (8, 6), (9, 6), (10, 5), (11, 5), (5, 9), (6, 9), (7, 10), (8, 10)):
        if (x, y) in drawn:
            t.px(x, y, s[5])
    t.px(8, 3, (255, 255, 255, 255))                               # a glint
    outline(t, drawn, s.outline)
    save("silicon", t)


def polysilicon() -> None:
    """A length broken off a Siemens rod, lying up to the right: a rough grey surface (it grows in nodules), its cut
    end showing the rings it grew in and the dark seed rod at their centre."""
    t = Tex(16, 16)
    s = POLYSILICON
    ax, ay, bx, by, r = 3.6, 12.4, 11.0, 5.0, 3.4
    ux, uy = bx - ax, by - ay
    length = math.hypot(ux, uy)
    ux, uy = ux / length, uy / length
    nx, ny = uy, -ux                                     # across the rod, toward its upper left side
    nx, ny = (-nx, -ny) if nx > 0 else (nx, ny)
    rng = random.Random(41)
    body: set[tuple[int, int]] = set()
    cap: dict[tuple[int, int], float] = {}
    for y in range(16):
        for x in range(16):
            px, py = x + 0.5, y + 0.5
            along = (px - ax) * ux + (py - ay) * uy
            across = (px - ax) * nx + (py - ay) * ny
            # The cut end: an ellipse at B, foreshortened along the rod.
            ex = (px - bx) * ux + (py - by) * uy
            ey = (px - bx) * nx + (py - by) * ny
            e = (ex / (r * 0.5)) ** 2 + (ey / r) ** 2
            if e <= 1:
                cap[(x, y)] = math.sqrt(e)
                continue
            # The broken end at A: a ragged edge rather than a clean cut.
            ragged = 0.7 * math.sin(across * 2.1 + 1.3)
            if ragged - 0.4 <= along <= length and abs(across) <= r:
                body.add((x, y))
                k = across / r                              # +1: upper-left, lit; -1: lower-right, shaded
                tone = 4 if k > 0.45 else 3 if k > -0.1 else 2 if k > -0.6 else 1
                if rng.random() < 0.28:                       # nodules
                    tone = min(6, tone + 1) if rng.random() < 0.5 else max(1, tone - 1)
                t.px(x, y, s[tone])
    for (x, y), d in cap.items():
        tone = 1 if d < 0.3 else 6 if d < 0.62 else 4 if d < 0.82 else 6
        t.px(x, y, s[tone])
    outline(t, body | set(cap), s.outline)
    save("polysilicon", t)


def quartz_crucible() -> None:
    """A cup of milky fused quartz seen from a little above: its lip, the shadowed hollow inside, a lit left flank."""
    t = Tex(16, 16)
    q = FUSED_QUARTZ
    cx, top, bottom, rx_top, rx_bot, ry = 7.5, 5.0, 11.6, 6.3, 4.4, 2.7
    drawn: set[tuple[int, int]] = set()
    for y in range(16):
        for x in range(16):
            px, py = x + 0.5, y + 0.5
            f = min(1.0, max(0.0, (py - top) / (bottom - top)))
            rx = rx_top + (rx_bot - rx_top) * f ** 1.6           # the flank curves in toward the base
            rim = ((px - cx) / rx_top) ** 2 + ((py - top) / ry) ** 2
            in_side = top <= py <= bottom and abs(px - cx) <= rx
            in_base = ((px - cx) / rx_bot) ** 2 + ((py - bottom) / 1.7) ** 2 <= 1
            if not (rim <= 1 or in_side or in_base):
                continue
            drawn.add((x, y))
            hollow = ((px - cx) / (rx_top - 1.3)) ** 2 + ((py - top - 0.15) / (ry - 0.9)) ** 2
            if hollow <= 1:
                # Inside: the far wall catches the light, the floor and near wall are in shadow.
                t.px(x, y, q[4] if py < top - 0.4 else q[3] if py < top + 0.5 else q[2])
            elif rim <= 1:
                t.px(x, y, q[6] if px < cx + 1.5 or py < top else q[5])   # the lip
            else:
                k = (px - cx) / rx
                tone = 5 if k < -0.5 else 4 if k < 0.15 else 3 if k < 0.6 else 2
                if py > bottom - 0.4:
                    tone = max(1, tone - 1)
                t.px(x, y, q[tone])
    for y in range(8, 12):
        if (4, y) in drawn:
            t.px(4, y, q[6])                                   # the glassy streak down its lit side
    outline(t, drawn, q.outline)
    save("quartz_crucible", t)


def ceramic_package() -> None:
    """A white alumina chip housing seen from above, waiting for its chips: the gold seal ring round an empty gold
    cavity, gold leads down both sides, the slab's front edge below."""
    rows = [
        "................",
        "................",
        "................",
        "..oooooooooooo..",
        "LLo6666666665oLL",
        "..o6GGGGGGGG5o..",
        "LLo6GddddddG4oLL",
        "..o5GdcccccG4o..",
        "LLo5GdcccccG4oLL",
        "..o5GdccccbG3o..",
        "LLo5GGGGGGGG3oLL",
        "..o4444444443o..",
        "..o3333333332o..",
        "..oooooooooooo..",
        "................",
        "................",
    ]
    legend = {"o": P.WHITE_CERAMIC.outline, "G": GOLD[5], "c": GOLD[3], "d": GOLD[1], "b": GOLD[4], "L": GOLD[4]}
    t, _ = PT.template(rows, legend, P.WHITE_CERAMIC)
    save("ceramic_package", t)


def silicon_boule() -> None:
    """A single crystal as it comes out of the puller, lying up to the right: the cone of its shoulder and the thin seed
    neck at the top end, its tail tapering at the other; polished grey, a growth ridge running down its length."""
    t = Tex(16, 16)
    s = SILICON
    ax, ay, bx, by = 2.6, 13.4, 13.4, 2.6
    ux, uy = bx - ax, by - ay
    length = math.hypot(ux, uy)
    ux, uy = ux / length, uy / length
    nx, ny = uy, -ux
    nx, ny = (-nx, -ny) if nx > 0 else (nx, ny)

    def radius(f: float) -> float:
        if f < 0.1:
            return 1.6 + (3.6 - 1.6) * f / 0.1               # the tail
        if f < 0.62:
            return 3.6
        if f < 0.86:
            return 3.6 - (3.6 - 0.7) * (f - 0.62) / 0.24     # the shoulder's cone
        return 0.6                                            # the seed neck
    drawn: set[tuple[int, int]] = set()
    for y in range(16):
        for x in range(16):
            px, py = x + 0.5, y + 0.5
            along = (px - ax) * ux + (py - ay) * uy
            across = (px - ax) * nx + (py - ay) * ny
            f = along / length
            if not 0 <= f <= 1:
                continue
            r = radius(f)
            if abs(across) > r:
                continue
            drawn.add((x, y))
            k = across / r
            tone = 6 if 0.35 < k < 0.7 else 5 if k > 0.0 else 4 if k > -0.45 else 3
            if f >= 0.62 or f < 0.1:
                tone = max(2, tone - 1)                          # the cones face away from the light
            if 0.1 <= f < 0.62 and abs(k + 0.1) < 0.12:
                tone = 6                                         # a growth ridge
            t.px(x, y, s[tone])
    outline(t, drawn, s.outline)
    save("silicon_boule", t)


def silicon_wafer() -> None:
    """A polished wafer seen from above at a slant: a mirror-grey disc catching a band of light, its edge below, the
    orientation notch at its foot."""
    t = Tex(16, 16)
    s = SILICON
    cx, cy, rx, ry = 7.5, 7.2, 6.6, 4.4
    drawn: set[tuple[int, int]] = set()
    for y in range(16):
        for x in range(16):
            px, py = x + 0.5, y + 0.5
            top = ((px - cx) / rx) ** 2 + ((py - cy) / ry) ** 2 <= 1
            edge = ((px - cx) / rx) ** 2 + ((py - cy - 1.1) / ry) ** 2 <= 1 and py > cy
            if not (top or edge):
                continue
            drawn.add((x, y))
            if not top:
                t.px(x, y, s[2])                                # the wafer's edge, its thickness
                continue
            # A mirror: dark, with a broad band of reflected light across it from the upper left.
            band = (px - cx) * 0.55 + (py - cy) * 0.9
            tone = 6 if -1.6 < band < -0.5 else 5 if -3.0 < band < 0.6 else 4 if band < 2.6 else 3
            t.px(x, y, s[tone])
    t.px(4, 5, (255, 255, 255, 255))
    outline(t, drawn, s.outline)
    save("silicon_wafer", t)


def build() -> None:
    silicon()
    polysilicon()
    quartz_crucible()
    ceramic_package()
    silicon_boule()
    silicon_wafer()


if __name__ == "__main__":
    build()
    print(R.item_sheet(ITEMS, "item", "semiconductors_review.png"))
