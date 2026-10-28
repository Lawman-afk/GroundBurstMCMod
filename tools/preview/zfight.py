"""Z-fighting check for generated models: finds pairs of faces that lie on (or within `tol` px of) the same plane
and overlap, which flicker in game. GeckoLib renders without back-face culling, so faces pointing opposite ways
fight too, unless they are two solids touching face to face (hidden inside the model).

    tools\\.venv\\Scripts\\python.exe tools\\preview\\zfight.py            (every geo + block model)
    tools\\.venv\\Scripts\\python.exe tools\\preview\\zfight.py ore_crusher

Only unrotated cubes are checked (rotated parts are rare and checked by eye in the previews).
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from gb import paths  # noqa: E402

AXIS = {"east": (0, 1), "west": (0, -1), "up": (1, 1), "down": (1, -1), "south": (2, 1), "north": (2, -1)}


def _faces(boxes):
    """boxes: list of (name, frm, to, faces) in world px. Yields (name, axis, sign, plane, rect, frm, to)."""
    for name, frm, to, faces in boxes:
        for d in faces:
            axis, sign = AXIS[d]
            plane = to[axis] if sign > 0 else frm[axis]
            others = [i for i in range(3) if i != axis]
            rect = (frm[others[0]], to[others[0]], frm[others[1]], to[others[1]])
            yield name, axis, sign, plane, rect, frm, to


def check(boxes, tol: float = 0.1) -> list[str]:
    faces = list(_faces(boxes))
    issues = []
    for i in range(len(faces)):
        n1, a1, s1, p1, r1, f1, t1 = faces[i]
        for j in range(i + 1, len(faces)):
            n2, a2, s2, p2, r2, f2, t2 = faces[j]
            if a1 != a2 or n1 == n2 or abs(p1 - p2) > tol:
                continue
            if f1 == f2 and t1 == t2:
                continue   # identical boxes: layered overlays or swap-in variants, drawn one at a time or identically
            ox = min(r1[1], r2[1]) - max(r1[0], r2[0])
            oy = min(r1[3], r2[3]) - max(r1[2], r2[2])
            if ox <= 1e-6 or oy <= 1e-6:
                continue
            if s1 != s2 and abs(p1 - p2) < 1e-6:
                # Opposite faces exactly touching: hidden only if the two boxes sit on opposite sides of the plane.
                if (s1 > 0 and f2[a2] >= p1 - 1e-6) or (s1 < 0 and t2[a2] <= p1 + 1e-6):
                    continue
            d = ["x", "y", "z"][a1]
            issues.append(f"{n1}/{'+' if s1 > 0 else '-'}{d} vs {n2}/{'+' if s2 > 0 else '-'}{d} at {d}={p1:g}/{p2:g} overlap {ox:g}x{oy:g}")
    return issues


def geo_boxes(path: Path):
    data = json.loads(path.read_text(encoding="utf-8"))
    out = []
    for bone in data["minecraft:geometry"][0]["bones"]:
        if bone.get("rotation"):
            continue
        for k, c in enumerate(bone.get("cubes", [])):
            if c.get("rotation") and any(c["rotation"]):
                continue
            ox, oy, oz = c["origin"]
            sx, sy, sz = c["size"]
            # Bedrock X is mirrored: origin.x = -(x1).
            frm = (-(ox + sx), oy, oz)
            to = (-ox, oy + sy, oz + sz)
            out.append((f"{bone['name']}#{k}", frm, to, list(c["uv"].keys())))
    return out


def block_boxes(path: Path):
    data = json.loads(path.read_text(encoding="utf-8"))
    out = []
    for k, e in enumerate(data.get("elements", [])):
        if e.get("rotation") and e["rotation"].get("angle", 0):
            continue
        out.append((e.get("name", f"e{k}"), tuple(e["from"]), tuple(e["to"]), list(e.get("faces", {}).keys())))
    return out


def main() -> None:
    only = sys.argv[1] if len(sys.argv) > 1 else None
    total = 0
    targets = [(p, geo_boxes) for p in sorted((paths.ASSETS / "geckolib" / "models").rglob("*.geo.json"))]
    targets += [(p, block_boxes) for p in sorted((paths.MODELS / "block").glob("*.json"))]
    for p, loader in targets:
        if only and only not in p.name:
            continue
        try:
            issues = check(loader(p))
        except (KeyError, TypeError):
            continue
        if issues:
            print(f"{p.name}: {len(issues)}")
            for s in issues:
                print("   ", s)
            total += len(issues)
    print("total", total)


if __name__ == "__main__":
    main()
