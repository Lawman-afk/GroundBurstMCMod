"""Phase 4 slice 5 parts (DESIGN.md §26.2): Circuit Fabricator products and the battery. Item sprites plus their
model / item-definition JSON.

The four circuit tiers share one board silhouette (a fabricated rectangle with a gold edge connector, unlike the
hand-cut perfboard of the handmade board) and are told apart by board colour and what sits on them:
basic = green board, one chip; advanced = blue board, two chips and gold traces; military = olive-drab conformal-
coated board with a white ceramic package and a gold lid; quantum = black board with a violet-glowing core chip.
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))

from gz import palette as P  # noqa: E402
from gz.palette import fixed  # noqa: E402
from gz.sprite import outline  # noqa: E402
from gz.tex import Tex  # noqa: E402
import parts as PT  # noqa: E402
import resources as R  # noqa: E402

GREEN = fixed("pcb_green", "#06200e", "#0b3418", "#124a22", "#1a622e", "#257c3c", "#3c9a52", "#6cc07e")
BLUE = fixed("pcb_blue", "#060e26", "#0c1a40", "#14285c", "#1c3a7c", "#2a529c", "#4474bc", "#7ea6dc")
DRAB = fixed("pcb_drab", "#16180c", "#262a14", "#383e1e", "#4c5428", "#626c34", "#7e8a48", "#a8b26e")
BLACK = fixed("pcb_black", "#050506", "#0c0c0f", "#141418", "#1d1d23", "#282830", "#383842", "#50505e")
GOLD = PT.GOLD
STEEL = PT.STEEL
ITEMS: list[str] = []

def save(name: str, t) -> None:
    R.save_item(name, t)
    ITEMS.append(name)


def circuit(name: str, ramp, trace, chips: list[tuple[int, int, int, int, str]]) -> None:
    """A 12x10 board (x 2..13, y 3..12): flat board tone lit along the top edge, traces with vias running to a row
    of gold connector pads, then the tier's chips as (x, y, w, h, kind) rectangles."""
    t = Tex(16, 16)
    x0, y0, x1, y1 = 2, 3, 13, 12
    t.rect(x0, y0, x1 - x0 + 1, y1 - y0 + 1, ramp[4])
    t.hline(x0, y0, x1 - x0 + 1, ramp[5])
    t.hline(x0, y1 - 1, x1 - x0 + 1, ramp[3])
    # Traces: a bus along the top, three runs down to the connector, vias as light dots.
    t.hline(x0 + 1, y0 + 1, x1 - x0 - 1, trace)
    for x in (4, 8, 11):
        t.vline(x, y0 + 1, y1 - y0 - 1, trace)
        t.px(x, y0 + 1, ramp[6])
    # Connector pads along the bottom edge.
    for x in range(x0, x1 + 1):
        t.px(x, y1, GOLD[5] if (x - x0) % 2 == 0 else ramp[2])
    for x, y, w, h, kind in chips:
        for yy in range(y, y + h):
            for xx in range(x, x + w):
                top = yy == y
                if kind == "chip":
                    t.px(xx, yy, P.BLACK_PAINT[4] if top else P.BLACK_PAINT[2])
                elif kind == "ceramic":
                    t.px(xx, yy, P.WHITE_CERAMIC[6] if top else P.WHITE_CERAMIC[4])
                elif kind == "lid":
                    t.px(xx, yy, GOLD[6] if top else GOLD[4])
                elif kind == "core":
                    t.px(xx, yy, P.SINGULARITY[6] if (xx, yy) == (x + 1, y + 1) else P.SINGULARITY[5] if top else P.SINGULARITY[4])
        if kind in ("chip", "ceramic"):
            for yy in range(y + 1, y + h, 2):                               # pins down both sides
                t.px(x - 1, yy, STEEL[5])
                t.px(x + w, yy, STEEL[5])
        if kind == "chip":
            t.px(x, y + 1, P.BLACK_PAINT[5])                                # pin-1 dot
    pts = {(x, y) for y in range(16) for x in range(16) if t.get(x, y)[3]}
    outline(t, pts, ramp.outline)
    save(name, t)


def circuits() -> None:
    circuit("basic_circuit", GREEN, PT.COPPER[4], [(6, 6, 4, 4, "chip")])
    circuit("advanced_circuit", BLUE, GOLD[4], [(4, 6, 3, 4, "chip"), (9, 6, 3, 4, "chip")])
    circuit("military_circuit", DRAB, GOLD[3], [(5, 5, 6, 5, "ceramic"), (6, 6, 4, 3, "lid")])
    circuit("quantum_circuit", BLACK, P.SINGULARITY[2], [(5, 5, 6, 5, "chip"), (6, 6, 4, 3, "core")])


def sensor_array() -> None:
    """A steel plate carrying a 2x2 grid of glossy sensor lenses and a gold connector tab."""
    rows = [
        "................",
        "................",
        "..oooooooooooo..",
        ".o666666666666o.",
        ".o5LLL5555LLL5o.",
        ".o5LlB5555LlB5o.",
        ".o5LBB5555LBB5o.",
        ".o455555555554o.",
        ".o4LLL4444LLL4o.",
        ".o4LlB4444LlB4o.",
        ".o4LBB4444LBB4o.",
        ".o333333333333o.",
        "..oooooggoooooo.",
        "......oggo......",
        "......oooo......",
        "................",
    ]
    legend = {"o": STEEL.outline, "L": P.BLACK_PAINT[2], "B": P.CHERENKOV[3], "l": P.CHERENKOV[6], "g": GOLD[5]}
    t, _ = PT.template([r[:16] for r in rows], legend, STEEL)
    save("sensor_array", t)


def superconducting_coil() -> None:
    """A solenoid seen side-on: gold windings on a dark former between frosted cryo-blue end flanges."""
    rows = [
        "................",
        "................",
        "...ff......ff...",
        "..fFFo....oFFf..",
        "..FFfoooooofFF..",
        "..FFfGGGGGGfFF..",
        "..FFfggggggfFF..",
        "..FFfGGGGGGfFF..",
        "..FFfggggggfFF..",
        "..FFfGGGGGGfFF..",
        "..FFfggggggfFF..",
        "..FFfoooooofFF..",
        "..fFFo....oFFf..",
        "...ff......ff...",
        "......ww..ww....",
        "................",
    ]
    legend = {"f": P.CRYO[3], "F": P.CRYO[5], "o": P.BLACK_PAINT[2], "G": GOLD[5], "g": GOLD[3], "w": PT.COPPER[4]}
    t, pts = PT.template([r[:16] for r in rows], legend, STEEL)
    outline(t, pts, P.CRYO.outline)
    save("superconducting_coil", t)


def battery() -> None:
    """A dry cell: copper terminal cap, olive label with a lightning bolt, steel base."""
    rows = [
        "................",
        "......oooo......",
        "......oCCo......",
        "....oooooooo....",
        "...o56666665o...",
        "...o45555554o...",
        "...oOOOOyyOOo...",
        "...oOOOyyOOOo...",
        "...oOOyyyyOOo...",
        "...oOOOOyyOOo...",
        "...oOOOyyOOOo...",
        "...oOOOyOOOOo...",
        "...o34444443o...",
        "...o23333332o...",
        "....oooooooo....",
        "................",
    ]
    legend = {"o": STEEL.outline, "C": PT.COPPER[5], "O": P.OLIVE[4], "y": P.HAZARD_YELLOW[5]}
    t, _ = PT.template(rows, legend, STEEL)
    for y in range(6, 12):
        t.px(4, y, P.OLIVE[5])                                              # label highlight
        t.px(11, y, P.OLIVE[2])
    save("battery", t)


def capacitor() -> None:
    """An electrolytic capacitor can: blue sleeve with a white polarity stripe, scored steel top, two copper legs."""
    rows = [
        "................",
        "................",
        ".....oooooo.....",
        "....o566o65o....",
        "....o5oooo5o....",
        "....o665o56o....",
        "....oBBBBWBo....",
        "....oBbBBWBo....",
        "....oBbBBWBo....",
        "....oBbBBWBo....",
        "....oBbBBWBo....",
        "....obbbbWbo....",
        ".....oooooo.....",
        "......c..c......",
        "......c..c......",
        "................",
    ]
    legend = {"o": STEEL.outline, "B": P.CHERENKOV[3], "b": P.CHERENKOV[2], "W": P.WHITE_CERAMIC[5], "c": PT.COPPER[5]}
    t, _ = PT.template(rows, legend, STEEL)
    save("capacitor", t)


def build() -> None:
    circuits()
    sensor_array()
    superconducting_coil()
    battery()
    capacitor()


if __name__ == "__main__":
    build()
    print(R.item_sheet(ITEMS, "item", "advanced_parts_review.png"))
