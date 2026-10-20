"""Fluid tank (DESIGN.md §6.4): textures, connected multipart block model, item model.

Look: a box of recessed clear glass in a steel frame, with riveted steel caps on top and bottom. Tanks join into
boxes up to 3x3x5, and the block state says which neighbours belong to the same tank, so:
  - each glass panel and cap only exists on faces of the whole tank (not between its blocks);
  - each of the 12 edge bars only exists where both faces meeting there are outside faces;
  - each of the 8 corner blocks exists when any of its three edges does.
Bars stop short of the corners (2..14) and corner blocks fill the ends, so no two frame pieces overlap.
Glass panels sit 1 px in from the block face (behind the frame) and have both faces, so the far side shows too.

The Creative Fluid Tank (creative only, never joins) is the same single block in the creative finish used by the
creative capacitor bank: a violet frame, gunmetal caps with a violet hatch, brass rivets, the same glass.
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from gz import palette as P  # noqa: E402
from gz import paths  # noqa: E402
from gz.model import write_json  # noqa: E402
from gz.tex import Tex  # noqa: E402

NAME = "fluid_tank"
S, G = P.STEEL, P.GUNMETAL
DIRS = ("north", "south", "east", "west", "up", "down")
OPP = {"north": "south", "south": "north", "east": "west", "west": "east", "up": "down", "down": "up"}


def textures(name: str = NAME, frame_ramp=S, cap_ramp=G, rivet=None, hatch=None) -> None:
    rivet, hatch = rivet or S, hatch or cap_ramp
    frame = Tex(16, 16)
    frame.rect(0, 0, 16, 16, frame_ramp[3])
    for i in range(0, 16, 4):
        frame.px(i, i, frame_ramp[4])
    frame.rect(14, 14, 2, 2, cap_ramp[3])                # corner block face with a rivet
    frame.px(14, 14, rivet[5])
    frame.save(paths.TEXTURES / "block" / f"{name}_frame.png")

    if name == NAME:
        glass = Tex(16, 16)
        for y in range(16):
            for x in range(16):
                glass.px(x, y, (196, 214, 222, 46))
        for k in range(5):                                    # one faint diagonal glint
            glass.px(4 + k, 9 - k, (235, 245, 250, 70))
        glass.save(paths.TEXTURES / "block" / f"{NAME}_glass.png")

    cap = Tex(16, 16)
    cap.rect(0, 0, 16, 16, cap_ramp[3])
    cap.outline(2, 2, 12, 12, cap_ramp[2])
    cap.hline(2, 2, 12, cap_ramp[4])
    cap.rect(5, 5, 6, 6, hatch[4])                        # inspection hatch
    cap.outline(5, 5, 6, 6, hatch[2])
    cap.px(7, 7, rivet[5])
    for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):
        cap.px(x, y, rivet[5])
    cap.save(paths.TEXTURES / "block" / f"{name}_cap.png")


def face(tex: str, uv=(0, 0, 16, 16), cull: str | None = None) -> dict:
    f = {"uv": list(uv), "texture": f"#{tex}"}
    if cull:
        f["cullface"] = cull
    return f


# Glass panels and caps: a zero-thickness plane 1 px inside the face, with both faces.
PLANES = {
    "north": ([0, 0, 1], [16, 16, 1]), "south": ([0, 0, 15], [16, 16, 15]),
    "west": ([1, 0, 0], [1, 16, 16]), "east": ([15, 0, 0], [15, 16, 16]),
    "up": ([0, 15, 0], [16, 15, 16]), "down": ([0, 1, 0], [16, 1, 16]),
}
# Edges by their two faces; each bar is the 2x2 strip along that edge, from 2 to 14.
EDGES = {
    ("up", "north"): ([2, 14, 0], [14, 16, 2]), ("up", "south"): ([2, 14, 14], [14, 16, 16]),
    ("up", "west"): ([0, 14, 2], [2, 16, 14]), ("up", "east"): ([14, 14, 2], [16, 16, 14]),
    ("down", "north"): ([2, 0, 0], [14, 2, 2]), ("down", "south"): ([2, 0, 14], [14, 2, 16]),
    ("down", "west"): ([0, 0, 2], [2, 2, 14]), ("down", "east"): ([14, 0, 2], [16, 2, 14]),
    ("north", "west"): ([0, 2, 0], [2, 14, 2]), ("north", "east"): ([14, 2, 0], [16, 14, 2]),
    ("south", "west"): ([0, 2, 14], [2, 14, 16]), ("south", "east"): ([14, 2, 14], [16, 14, 16]),
}
CORNERS = {(v, h1, h2): None for v in ("up", "down") for h1 in ("north", "south") for h2 in ("west", "east")}


def corner_box(v: str, ns: str, we: str) -> tuple[list[float], list[float]]:
    y0 = 14 if v == "up" else 0
    z0 = 0 if ns == "north" else 14
    x0 = 0 if we == "west" else 14
    return [x0, y0, z0], [x0 + 2, y0 + 2, z0 + 2]


def models() -> None:
    tex = {"frame": f"groundzero:block/{NAME}_frame", "glass": f"groundzero:block/{NAME}_glass",
           "cap": f"groundzero:block/{NAME}_cap", "particle": f"groundzero:block/{NAME}_frame"}
    multipart = []
    item_elements = []

    def model(name: str, elements: list, render: str) -> str:
        write_json(paths.MODELS / "block" / f"{NAME}_{name}.json", {"textures": tex, "render_type": render, "elements": elements})
        return f"groundzero:block/{NAME}_{name}"

    for d, (frm, to) in PLANES.items():
        kind = "cap" if d in ("up", "down") else "glass"
        el = {"from": frm, "to": to, "faces": {d: face(kind), OPP[d]: face(kind)}}
        if kind == "glass":
            el["shade"] = False
        m = model(f"{kind}_{d}", [el], "minecraft:translucent" if kind == "glass" else "minecraft:cutout")
        multipart.append({"when": {d: "false"}, "apply": {"model": m}})
        item_elements.append(el)

    for (a, b), (frm, to) in EDGES.items():
        # The two faces along the bar's length; skip the two end faces (corner blocks cover the ends).
        axis_ends = {0: ("west", "east"), 1: ("down", "up"), 2: ("north", "south")}
        length_axis = next(i for i in range(3) if to[i] - frm[i] > 2)
        faces = {d: face("frame", (0, 0, 2, 12) if length_axis == 1 else (0, 0, 12, 2)) for d in DIRS if d not in axis_ends[length_axis]}
        el = {"from": frm, "to": to, "faces": faces}
        m = model(f"edge_{a}_{b}", [el], "minecraft:cutout")
        multipart.append({"when": {a: "false", b: "false"}, "apply": {"model": m}})
        item_elements.append(el)

    for v, ns, we in CORNERS:
        frm, to = corner_box(v, ns, we)
        el = {"from": frm, "to": to, "faces": {d: face("frame", (14, 14, 16, 16)) for d in DIRS}}
        m = model(f"corner_{v}_{ns}_{we}", [el], "minecraft:cutout")
        edges = [{v: "false", ns: "false"}, {v: "false", we: "false"}, {ns: "false", we: "false"}]
        multipart.append({"when": {"OR": edges}, "apply": {"model": m}})
        item_elements.append(el)

    write_json(paths.ASSETS / "blockstates" / f"{NAME}.json", {"multipart": multipart})
    write_json(paths.MODELS / "item" / f"{NAME}.json", {"parent": "minecraft:block/block", "textures": tex,
                                                        "render_type": "minecraft:translucent", "elements": item_elements})
    write_json(paths.ASSETS / "items" / f"{NAME}.json", {"model": {"type": "minecraft:model", "model": f"groundzero:item/{NAME}"}})
    creative_models(item_elements)


def creative_models(elements: list) -> None:
    """The creative tank never joins, so its block is the whole single-block element set: the glass in one translucent
    model, frame and caps in one cutout model, both always applied."""
    name = f"creative_{NAME}"
    tex = {"frame": f"groundzero:block/{name}_frame", "glass": f"groundzero:block/{NAME}_glass",
           "cap": f"groundzero:block/{name}_cap", "particle": f"groundzero:block/{name}_frame"}
    glass = [e for e in elements if any(f["texture"] == "#glass" for f in e["faces"].values())]
    solid = [e for e in elements if e not in glass]
    write_json(paths.MODELS / "block" / f"{name}_glass.json", {"textures": tex, "render_type": "minecraft:translucent", "elements": glass})
    write_json(paths.MODELS / "block" / f"{name}_frame.json", {"textures": tex, "render_type": "minecraft:cutout", "elements": solid})
    write_json(paths.ASSETS / "blockstates" / f"{name}.json", {"multipart": [
        {"apply": {"model": f"groundzero:block/{name}_frame"}}, {"apply": {"model": f"groundzero:block/{name}_glass"}}]})
    write_json(paths.MODELS / "item" / f"{name}.json", {"parent": "minecraft:block/block", "textures": tex,
                                                       "render_type": "minecraft:translucent", "elements": elements})
    write_json(paths.ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": f"groundzero:item/{name}"}})


if __name__ == "__main__":
    textures()
    textures(f"creative_{NAME}", P.SINGULARITY, G, P.BRASS, P.SINGULARITY)
    models()
    print("fluid tank written")
