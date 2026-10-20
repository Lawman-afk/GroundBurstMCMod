"""Multiblock tool items (DESIGN.md §23.1, §25): the Holo-Projector and the creative Structure Spawner.

Holo-Projector: a gunmetal emitter puck projecting a small glowing cyan block with hologram scanlines.
Structure Spawner: a blueprint with the structure's floor plan, the controller cell marked, and a gold seal.
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))

from gz import palette as P  # noqa: E402
from gz.palette import fixed  # noqa: E402
from gz.tex import Tex  # noqa: E402
import parts as PT  # noqa: E402
import resources as R  # noqa: E402

HOLO = fixed("holo", "#0a3a4a", "#0e5a70", "#167f98", "#22a6c0", "#46cce0", "#86e8f4", "#d4fbff")
BLUEPRINT = fixed("blueprint", "#0c1e44", "#14306a", "#1c4290", "#2858b0", "#3c70c8", "#6c98dc", "#c4d8f4")


def _inside(poly: list[tuple[float, float]], x: float, y: float) -> bool:
    n, ok = len(poly), False
    for i in range(n):
        (x0, y0), (x1, y1) = poly[i], poly[(i + 1) % n]
        if (y0 > y) != (y1 > y) and x < x0 + (y - y0) * (x1 - x0) / (y1 - y0):
            ok = not ok
    return ok


def iso_cube(cx: float, top: float, w: float, h: float, s: float) -> dict[tuple[int, int], str]:
    """Pixels of a 2:1 isometric cube, keyed to the face they belong to (top, left, right)."""
    faces = {
        "top": [(cx, top), (cx + w, top + h), (cx, top + 2 * h), (cx - w, top + h)],
        "left": [(cx - w, top + h), (cx, top + 2 * h), (cx, top + 2 * h + s), (cx - w, top + h + s)],
        "right": [(cx, top + 2 * h), (cx + w, top + h), (cx + w, top + h + s), (cx, top + 2 * h + s)],
    }
    cube = {}
    for y in range(16):
        for x in range(16):
            for face, poly in faces.items():
                if _inside(poly, x + 0.5, y + 0.5):
                    cube[(x, y)] = face
                    break
    return cube


def cube_edges(cube: dict[tuple[int, int], str]) -> tuple[set, set]:
    """(silhouette pixels, inner edge pixels where two faces meet)."""
    sil, inner = set(), set()
    for (x, y), face in cube.items():
        if any((x + dx, y + dy) not in cube for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            sil.add((x, y))
        elif any(cube.get((x + dx, y + dy), face) != face for dx, dy in ((1, 0), (0, 1), (-1, 0))) and face == "top":
            inner.add((x, y))
    return sil, inner


def holo_projector() -> None:
    t = Tex(16, 16)
    cube = iso_cube(8.0, 0.0, 5.0, 2.5, 5.5)
    tone = {"top": 5, "left": 3, "right": 2}
    for (x, y), face in cube.items():
        k = tone[face] - (1 if face != "top" and y % 2 == 1 else 0)     # hologram scanlines on the sides
        t.px(x, y, HOLO[k])
    sil, inner = cube_edges(cube)
    for (x, y) in inner:
        t.px(x, y, HOLO[6])                                                # lit edges round the top face
    for (x, y) in sil:
        t.px(x, y, HOLO[6] if cube[(x, y)] == "top" else HOLO[1])
    # Light cone fanning up from the lens to the cube's lower corners.
    for (x, y, a) in ((4, 10, 90), (5, 10, 130), (10, 10, 130), (11, 10, 90), (6, 11, 170), (9, 11, 170)):
        t.px(x, y, (*HOLO[5][:3], a))
    # Emitter puck: lit top with the lens, darker band, dark outline.
    g = P.GUNMETAL
    rows = {
        11: "...oooo..oooo...",
        12: "..o556LLLL655o..",
        13: "..o4444444444o..",
        14: "..o2323232323o..",
        15: "...oooooooooo...",
    }
    legend = {"o": g.outline, "L": HOLO[6]}
    for y, row in rows.items():
        for x, c in enumerate(row):
            if c != ".":
                t.px(x, y, legend[c] if c in legend else g[int(c)])
    PT.save("holo_projector", t)


def reactor_plan(t) -> None:
    """3x3 floor plan: the core cell in the middle (blue), the controller at the front middle."""
    for x in range(4, 13):
        for y in (3, 6, 9, 12):
            if y < 12:
                t.px(x, y, BLUEPRINT[6])
    for y in range(3, 12):
        for x in (4, 7, 10, 12):
            t.px(x, y, BLUEPRINT[6])
    for (x, y) in ((8, 7), (9, 7), (8, 8), (9, 8)):
        t.px(x, y, P.CHERENKOV[5])
    for (x, y) in ((8, 10), (9, 10)):
        t.px(x, y, P.HAZARD_YELLOW[5])


def ring_plan(t) -> None:
    """The accelerator ring: a hollow square, the controller marked at the front."""
    for x in range(4, 13):
        t.px(x, 3, BLUEPRINT[6])
        t.px(x, 11, BLUEPRINT[6])
    for y in range(3, 12):
        t.px(4, y, BLUEPRINT[6])
        t.px(12, y, BLUEPRINT[6])
    for x in range(6, 11):
        t.px(x, 5, BLUEPRINT[5])
        t.px(x, 9, BLUEPRINT[5])
    for y in range(5, 10):
        t.px(6, y, BLUEPRINT[5])
        t.px(10, y, BLUEPRINT[5])
    t.px(7, 11, P.HAZARD_YELLOW[5])
    t.px(8, 11, P.CHERENKOV[5])


def tokamak_plan(t) -> None:
    """The fusion reactor from above: an octagonal ring, pink plasma inside it, the solenoid in the middle."""
    ring = [(6, 3), (7, 3), (8, 3), (9, 3), (10, 3), (5, 4), (11, 4), (4, 5), (12, 5), (4, 6), (12, 6), (4, 7), (12, 7),
            (4, 8), (12, 8), (4, 9), (12, 9), (5, 10), (11, 10), (6, 11), (7, 11), (8, 11), (9, 11), (10, 11)]
    for x, y in ring:
        t.px(x, y, BLUEPRINT[6])
    plasma = [(7, 4), (8, 4), (9, 4), (6, 5), (10, 5), (5, 6), (11, 6), (5, 7), (11, 7), (5, 8), (11, 8), (6, 9), (10, 9),
              (7, 10), (8, 10), (9, 10)]
    for x, y in plasma:
        t.px(x, y, (240, 110, 180, 255))
    t.px(8, 7, P.COPPER[5])
    t.px(8, 3, P.HAZARD_YELLOW[5])                                        # the controller at the front


def cage_plan(t) -> None:
    """The singularity cage from above: corner posts, walls, emitters on each side and the black core in the middle."""
    for x in range(4, 13):
        t.px(x, 3, BLUEPRINT[5])
        t.px(x, 11, BLUEPRINT[5])
    for y in range(3, 12):
        t.px(4, y, BLUEPRINT[5])
        t.px(12, y, BLUEPRINT[5])
    for x, y in ((4, 3), (12, 3), (4, 11), (12, 11)):
        t.px(x, y, BLUEPRINT[6])                                           # posts
    for x, y in ((8, 3), (8, 11), (4, 7), (12, 7)):
        t.px(x, y, P.SINGULARITY[5])                                       # emitters
    for x, y in ((7, 6), (8, 6), (9, 6), (7, 8), (8, 8), (9, 8), (7, 7), (9, 7)):
        t.px(x, y, P.SINGULARITY[3])
    t.px(8, 7, (10, 6, 16, 255))                                           # the core


def structure_spawner(name: str, plan=None) -> None:
    """A blueprint sheet, its bottom-left corner curling, with the structure's floor plan drawn on it."""
    rows = [
        "................",
        "..oooooooooooo..",
        "..o5444444444o..",
        "..o4444444444o..",
        "..o4444444444o..",
        "..o4444444444o..",
        "..o4444444444o..",
        "..o4444444444o..",
        "..o4444444444o..",
        "..o4444444444o..",
        "..o4444444444o..",
        ".oo5544444443o..",
        "o5665444444433o.",
        "o4554333333332o.",
        ".oooooooooooooo.",
        "................",
    ]
    t, _ = PT.template(rows, {"o": BLUEPRINT.outline}, BLUEPRINT)
    if plan is not None:
        plan(t)
        for (x, y, c) in ((12, 12, 5), (13, 12, 4), (12, 13, 3), (13, 13, 2)):
            t.px(x, y, P.HAZARD_YELLOW[c])                                 # creative seal
        PT.save(name, t)
        return
    # Floor plan of the structure: a 2x2 grid of cells, the controller's cell marked.
    for x in range(4, 13):
        for y in (3, 7, 10):
            t.px(x, y, BLUEPRINT[6])
    for y in range(3, 11):
        for x in (4, 8, 12):
            t.px(x, y, BLUEPRINT[6])
    for (x, y) in ((5, 8), (6, 8), (5, 9), (6, 9)):
        t.px(x, y, P.HAZARD_YELLOW[5])
    t.px(10, 5, BLUEPRINT[5])                                              # dimension ticks
    t.px(6, 5, BLUEPRINT[5])
    t.px(12, 12, P.HAZARD_YELLOW[5])                                       # creative seal
    t.px(13, 12, P.HAZARD_YELLOW[4])
    t.px(12, 13, P.HAZARD_YELLOW[3])
    t.px(13, 13, P.HAZARD_YELLOW[2])
    PT.save(name, t)


def build() -> None:
    holo_projector()
    structure_spawner("chemical_plant_spawner")
    structure_spawner("fission_reactor_spawner", reactor_plan)
    structure_spawner("particle_accelerator_spawner", ring_plan)
    structure_spawner("fusion_reactor_spawner", tokamak_plan)
    structure_spawner("singularity_reactor_spawner", cage_plan)


if __name__ == "__main__":
    build()
    print(R.item_sheet(PT.ITEMS, "item", "multiblock_tools_review.png"))
