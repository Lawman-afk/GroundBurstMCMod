r"""Rendered-geometry z-fighting check for GeckoLib models: builds the exact quads GeckoLib draws (bone pivots and
rotations, rotated cubes, animation poses) and reports pairs of faces that lie in the same plane and overlap.
GeckoLib's entity cutout pipeline does not cull back faces in 26.x, so faces pointing opposite ways fight too. A
back-to-back pair lies between the two boxes it belongs to, so it can only be seen when one of them can be seen
through: transparent texels, or a zero-thickness plane. Export closes every other visible opening (GeoModel.holes),
so those are the only pairs reported.

    tools\.venv\Scripts\python.exe tools\preview\zfight3d.py                 (all machines, rest + animation frames)
    tools\.venv\Scripts\python.exe tools\preview\zfight3d.py arc_furnace
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))
from gz import paths  # noqa: E402
import geo_preview as G  # noqa: E402


#: Bone-name prefixes of variant sets the renderer shows one at a time (press dies, reactor rods, singularity disks).
EXCLUSIVE_PREFIXES = ("die_", "rods", "disk")


def _clip(subject: list[np.ndarray], clip: list[np.ndarray]) -> list[np.ndarray]:
    """Sutherland-Hodgman: convex polygon intersection in 2D."""
    out = subject
    n = len(clip)
    for i in range(n):
        a, b = clip[i], clip[(i + 1) % n]
        edge = b - a
        inp, out = out, []
        if not inp:
            break

        def inside(p):
            return edge[0] * (p[1] - a[1]) - edge[1] * (p[0] - a[0]) >= -1e-9

        for j in range(len(inp)):
            p, q = inp[j], inp[(j + 1) % len(inp)]
            if inside(q):
                if not inside(p):
                    out.append(_cross(p, q, a, b))
                out.append(q)
            elif inside(p):
                out.append(_cross(p, q, a, b))
    return out


def _cross(p, q, a, b):
    d1, d2 = q - p, b - a
    den = d1[0] * d2[1] - d1[1] * d2[0]
    if abs(den) < 1e-12:
        return q
    t = ((a[0] - p[0]) * d2[1] - (a[1] - p[1]) * d2[0]) / den
    return p + t * d1


def _area(poly) -> float:
    if len(poly) < 3:
        return 0.0
    s = 0.0
    for i in range(len(poly)):
        x0, y0 = poly[i]
        x1, y1 = poly[(i + 1) % len(poly)]
        s += x0 * y1 - x1 * y0
    return abs(s) / 2


def _ccw(poly):
    s = sum(poly[i][0] * poly[(i + 1) % len(poly)][1] - poly[(i + 1) % len(poly)][0] * poly[i][1] for i in range(len(poly)))
    return poly if s > 0 else poly[::-1]


def _cube(label: str) -> str:
    return label.split("/")[0]


def _open_cubes(quads, labels) -> set[str]:
    """Cubes you can see through: a face with see-through texels, or a plane (no thickness to hide behind)."""
    faces: dict[str, int] = {}
    holey: set[str] = set()
    for q, lab in zip(quads, labels):
        c = _cube(lab)
        faces[c] = faces.get(c, 0) + 1
        u0, v0 = np.floor(q.uv.min(axis=0)).astype(int)
        u1, v1 = np.ceil(q.uv.max(axis=0)).astype(int)
        if u1 > u0 and v1 > v0 and (q.tex[v0:v1, u0:u1, 3] < 128).any():
            holey.add(c)
    planes = {c for c, n in faces.items() if n <= 2}     # one- or two-sided planes (glass, backings) have no volume
    return holey | planes


def check(quads, labels, tol: float = 0.05, min_area: float = 0.01) -> list[str]:
    issues = []
    data = []
    open_cubes = _open_cubes(quads, labels)
    for q, lab in zip(quads, labels):
        n = q.normal / np.linalg.norm(q.normal)
        d = float(np.dot(n, q.pos[0]))
        data.append((n, d, q.pos, lab))
    for i in range(len(data)):
        n1, d1, p1, l1 = data[i]
        for j in range(i + 1, len(data)):
            n2, d2, p2, l2 = data[j]
            # GeckoLib's entity cutout pipeline draws back faces too (withCull(false) in 26.x), so faces that share a
            # plane fight whichever way they point.
            c = float(np.dot(n1, n2))
            if abs(c) < 0.9995:
                continue
            if c < 0:
                if _cube(l1) not in open_cubes and _cube(l2) not in open_cubes:
                    continue
                d2 = -d2
            if abs(d1 - d2) > tol:
                continue
            if any(l1.startswith(v) and l2.startswith(v) and _cube(l1) != _cube(l2) for v in EXCLUSIVE_PREFIXES):
                continue   # variants of one part (press dies, reactor control-rod positions): only one is ever drawn
            # 2D basis on the shared plane.
            u = p1[1] - p1[0]
            u = u / (np.linalg.norm(u) + 1e-12)
            v = np.cross(n1, u)
            a = _ccw([np.array([np.dot(x, u), np.dot(x, v)]) for x in p1])
            b = _ccw([np.array([np.dot(x, u), np.dot(x, v)]) for x in p2])
            area = _area(_clip(a, b))
            if area > min_area:
                issues.append(f"{l1} vs {l2}  (gap {abs(d1 - d2):.3f}px, overlap {area:.2f}px2)")
    return issues


def scan(name: str, frames: int = 8) -> list[str]:
    model_id = f"{paths.MODID}:block/{name}"
    found: dict[str, str] = {}
    poses = [(None, 0.0, "rest")]
    anim_path = paths.ANIMATIONS / "block" / f"{name}.animation.json"
    if anim_path.exists():
        anims = json.loads(anim_path.read_text(encoding="utf-8"))["animations"]
        for an, a in anims.items():
            length = a.get("animation_length", 1.0)
            for k in range(frames):
                poses.append((a, length * k / frames, f"{an}@{length * k / frames:.2f}s"))
    for anim, t, tag in poses:
        labels: list[str] = []
        quads = G.geo_quads(model_id, anim, t, labels)
        for issue in check(quads, labels):
            found.setdefault(issue.split("  (")[0], f"{issue}  [{tag}]")
    return list(found.values())


def main() -> None:
    names = [p.name.removesuffix(".geo.json") for p in sorted((paths.GEO / "block").glob("*.geo.json"))]
    if len(sys.argv) > 1:
        names = [n for n in names if sys.argv[1] in n]
    total = 0
    for n in names:
        issues = scan(n)
        print(f"{n}: {len(issues)}")
        for s in issues:
            print("   ", s)
        total += len(issues)
    print("total", total)


if __name__ == "__main__":
    main()
