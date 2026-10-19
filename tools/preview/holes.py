r"""Open-face check for GeckoLib models: finds boxes with a missing face that you can see into.

A box may leave a face out when something else covers it (a band under a dome, a pipe end against a tank). This
samples points just outside every missing face of every solid box (thicker than a plane) and reports the faces where
part of the opening is not inside another box: a visible hole showing the box's empty inside, like a tank band with
no top beside its dome. Rotated boxes and bones are skipped.

    tools\.venv\Scripts\python.exe tools\preview\holes.py                 (all machines)
    tools\.venv\Scripts\python.exe tools\preview\holes.py chemical_plant
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from gz import paths  # noqa: E402

FACES = ("north", "south", "east", "west", "up", "down")
PLANE = 0.3          # thinner than this along an axis = a deliberate one-sided plane (glass, backing), not a solid
EPS = 0.02


def boxes(model: Path) -> list[tuple[str, np.ndarray, np.ndarray, set[str]]]:
    geo = json.loads(model.read_text(encoding="utf-8"))["minecraft:geometry"][0]
    out = []
    for bone in geo["bones"]:
        if any(bone.get("rotation", [0, 0, 0])):
            continue
        for i, c in enumerate(bone.get("cubes", [])):
            if any(c.get("rotation", [0, 0, 0])):
                continue
            o, s = np.array(c["origin"], float), np.array(c["size"], float)
            inf = c.get("inflate", 0.0)
            out.append((f"{bone['name']}#{i}", o - inf, o + s + inf, set(c.get("uv", {}).keys())))
    return out


def face_points(lo: np.ndarray, hi: np.ndarray, face: str, step: float = 0.25) -> np.ndarray:
    # Bedrock geometry mirrors x (east is -x); only the plane matters here, so map faces onto axes directly.
    axis, side = {"east": (0, 0), "west": (0, 1), "up": (1, 1), "down": (1, 0), "south": (2, 1), "north": (2, 0)}[face]
    others = [a for a in range(3) if a != axis]
    us = np.arange(lo[others[0]] + step / 2, hi[others[0]], step)
    vs = np.arange(lo[others[1]] + step / 2, hi[others[1]], step)
    pts = []
    for u in us:
        for v in vs:
            p = np.zeros(3)
            p[others[0]], p[others[1]] = u, v
            p[axis] = (hi[axis] + EPS) if side else (lo[axis] - EPS)
            pts.append(p)
    return np.array(pts).reshape(-1, 3)


def check(model: Path) -> list[str]:
    bs = boxes(model)
    issues = []
    for name, lo, hi, faces in bs:
        if np.any(hi - lo < PLANE):
            continue
        for f in FACES:
            if f in faces:
                continue
            pts = face_points(lo, hi, f)
            if len(pts) == 0:
                continue
            covered = np.zeros(len(pts), bool)
            for other, olo, ohi, _ in bs:
                if other == name:
                    continue
                covered |= np.all((pts >= olo - 1e-6) & (pts <= ohi + 1e-6), axis=1)
            open_area = (~covered).sum() * 0.0625
            if open_area > 0.2:
                issues.append(f"{name} has no {f} face: {open_area:.2f} px2 open")
    return issues


def main() -> None:
    models = sorted((paths.GEO / "block").glob("*.geo.json"))
    if len(sys.argv) > 1:
        models = [m for m in models if sys.argv[1] in m.name]
    total = 0
    for m in models:
        issues = check(m)
        print(f"{m.name.removesuffix('.geo.json')}: {len(issues)}")
        for i in issues:
            print("   ", i)
        total += len(issues)
    print("total", total)


if __name__ == "__main__":
    main()
