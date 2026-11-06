"""Renders a block's states as the game puts them together from its blockstate (variants or multipart, each model turned
by its x and y about the block's middle, x first), for reviewing models that only make sense together: a hatch's frame
and lid, a door's two halves. Each scene is one or more blocks (states joined by ";", each with "@x,y,z" blocks of
offset if not at the origin), drawn from five angles, a row a scene, into tools/out/previews/<block>_states.png.

    tools/.venv/Scripts/python.exe tools/preview/state_view.py bunker_hatch "facing=north,half=top,open=false" ^
        "facing=north,half=top,open=true"
    tools/.venv/Scripts/python.exe tools/preview/state_view.py pressure_door ^
        "facing=east,half=lower,hinge=left,open=false;facing=east,half=upper,hinge=left,open=false@0,1,0"
"""
from __future__ import annotations

import argparse
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))

from gb import paths  # noqa: E402
import render as R  # noqa: E402

VIEWS = [("iso front", 30, 225), ("iso back", 30, 45), ("front", 4, 180), ("from the east", 10, -90), ("top", 85, 180)]


def _matches(when: dict, props: dict) -> bool:
    if "OR" in when:
        return any(_matches(w, props) for w in when["OR"])
    if "AND" in when:
        return all(_matches(w, props) for w in when["AND"])
    return all(str(props.get(k)) in str(v).split("|") for k, v in when.items())


def models_for(block: str, props: dict) -> list[dict]:
    """The models (with their x and y) the blockstate applies for these properties."""
    bs = json.loads((paths.ASSETS / "blockstates" / f"{block}.json").read_text(encoding="utf-8"))
    out = []
    if "variants" in bs:
        for key, v in bs["variants"].items():
            want = dict(kv.split("=") for kv in key.split(",") if kv)
            if all(str(props.get(k)) == val for k, val in want.items()):
                out.append(v[0] if isinstance(v, list) else v)
                break
    for part in bs.get("multipart", []):
        if "when" not in part or _matches(part["when"], props):
            a = part["apply"]
            out.append(a[0] if isinstance(a, list) else a)
    return out


def _facing(n: np.ndarray) -> str:
    """The direction a normal is nearest."""
    return max(R.NORMALS, key=lambda d: float(np.dot(R.NORMALS[d], n)))


def quads_for(block: str, props: dict, offset=(0, 0, 0)) -> list[R.Quad]:
    quads = []
    centre = np.array([8.0, 8.0, 8.0])
    shift = np.array(offset, dtype=float) * 16
    for apply in models_for(block, props):
        model = R.load_model(apply["model"])
        m = R._ry(math.radians(-apply.get("y", 0))) @ R._rx(math.radians(-apply.get("x", 0)))
        for q in R.build_quads(model):
            pos = (m @ (q.pos - centre).T).T + centre + shift
            n = m @ q.normal
            # Shaded by the way the face looks once turned, as the game shades a turned model's faces.
            shade = R.SHADE[_facing(n)] if q.directional else q.shade
            quads.append(R.Quad(pos, q.uv, q.tex, shade, q.emissive, n, q.directional))
    return quads


def scene(block: str, spec: str) -> list[R.Quad]:
    quads = []
    for part in spec.split(";"):
        state, _, at = part.partition("@")
        props = dict(kv.split("=") for kv in state.split(",") if kv)
        offset = tuple(float(v) for v in at.split(",")) if at else (0, 0, 0)
        quads += quads_for(block, props, offset)
    return quads


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("block")
    ap.add_argument("scenes", nargs="+")
    ap.add_argument("--cell", type=int, default=260)
    ap.add_argument("--out", type=Path)
    args = ap.parse_args()
    cell, header, label = args.cell, 18, 16
    light = np.array([0.4, 0.8, 0.45])
    light = light / np.linalg.norm(light)
    rows = [(s, scene(args.block, s)) for s in args.scenes]
    pts = np.concatenate([q.pos for _, quads in rows for q in quads])
    lo, hi = pts.min(axis=0), pts.max(axis=0)
    centre, extent = (lo + hi) / 2, float(np.linalg.norm(hi - lo)) * 1.05
    sheet = Image.new("RGBA", (len(VIEWS) * cell, len(rows) * (cell + header + label)), (28, 30, 34, 255))
    d = ImageDraw.Draw(sheet)
    for r, (spec, quads) in enumerate(rows):
        y = r * (cell + header + label)
        d.text((6, y + 3), spec, fill=(230, 230, 230, 255))
        for c, (name, pitch, yaw) in enumerate(VIEWS):
            tile = R._checker(cell).convert("RGBA")
            tile.alpha_composite(R.render(quads, cell, pitch, yaw, centre, extent, light_dir=light))
            sheet.alpha_composite(tile, (c * cell, y + header))
            d.text((c * cell + 6, y + header + cell + 2), name, fill=(190, 190, 190, 255))
    out = args.out or paths.PREVIEWS / f"{args.block}_states.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    sheet.convert("RGB").save(out)
    print(out)


if __name__ == "__main__":
    main()
