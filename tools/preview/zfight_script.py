"""Z-fighting check for a GeckoLib model straight from its script, turned parts and all, by part name: every exported
face is turned with its cube and its bones (the rest pose, or an animation's pose at a time) and checked against every
other face lying on its plane (within {tol} px) that overlaps it, leaving out the part of an overlap that something
solid covers (where it cannot be seen). zfight.py (run at every save) skips turned cubes and bones; zfight3d.py checks
the exported geometry as GeckoLib draws it, by bone and cube index. This one runs the script (no files written), names
the boxes and knows what covers what: round sections (planks turned about their middle) and parts turned to a bearing
are where caps meet. (Written for the superweapons' feedback pass, DESIGN.md §19.5.)

GeckoLib draws back faces, so faces pointing opposite ways fight too, unless they are two solids touching face to
face (each on its own side of the plane: hidden inside the model).

Faces a little apart (round sections sink every other plank 0.06 px so their caps do not share a plane) still flicker
from far off: the depth buffer resolves about 1.2e-6 z^2 blocks at z blocks away (0.03 px at 40 blocks, 0.07 px at 60).
--tol 0.1 lists them too.

    tools\\.venv\\Scripts\\python.exe tools\\preview\\zfight_script.py scud_storm
    tools\\.venv\\Scripts\\python.exe tools\\preview\\zfight_script.py nuclear_silo --anim raise --time 9
    tools\\.venv\\Scripts\\python.exe tools\\preview\\zfight_script.py ammo_factories:war_factory --tol 0.1

The script's build() (or the function named after the colon) is run with saving turned off; the model it returns or
would have saved is checked, posed by the script's animations() (or the animations it would have saved).
"""
from __future__ import annotations

import argparse
import importlib
import math
import sys
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
sys.path.insert(0, str(ROOT / "assets"))

from gz import geo as G  # noqa: E402

#: face: (axis, side): the face lies on the box's high (1) or low (0) side of that axis.
LOCAL = {"east": (0, 1), "west": (0, 0), "up": (1, 1), "down": (1, 0), "south": (2, 1), "north": (2, 0)}


def rot(r) -> np.ndarray:
    """GeckoLib's turn (degrees, world signs): about z, then y, then x, each right-handed (a positive z turns +x toward
    +y; a positive y is counterclockwise seen from above)."""
    ax, ay, az = (math.radians(v) for v in r)
    cx, sx, cy, sy, cz, sz = math.cos(ax), math.sin(ax), math.cos(ay), math.sin(ay), math.cos(az), math.sin(az)
    rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return rz @ ry @ rx


def turn(pivot, r, offset=(0.0, 0.0, 0.0)) -> np.ndarray:
    """4x4: turned by r about pivot, then moved by offset (GeckoLib's order for a bone)."""
    m = np.eye(4)
    R = rot(r)
    p = np.asarray(pivot, dtype=float)
    m[:3, :3] = R
    m[:3, 3] = p - R @ p + np.asarray(offset, dtype=float)
    return m


def box_turn(b) -> np.ndarray:
    r = b.rotation
    if not r:
        return np.eye(4)
    o = r.get("origin", (0, 0, 0))
    if "axis" in r:
        v = {"x": (r["angle"], 0, 0), "y": (0, r["angle"], 0), "z": (0, 0, r["angle"])}[r["axis"]]
    else:
        v = (r.get("x", 0), r.get("y", 0), r.get("z", 0))
    return turn(o, v)


def _sample(keys: dict, t: float) -> tuple:
    ts = sorted(keys)
    if t <= ts[0]:
        return tuple(keys[ts[0]])
    if t >= ts[-1]:
        return tuple(keys[ts[-1]])
    for a, b in zip(ts, ts[1:]):
        if a <= t <= b:
            f = (t - a) / (b - a) if b > a else 0.0
            return tuple(x + (y - x) * f for x, y in zip(keys[a], keys[b]))
    return tuple(keys[ts[-1]])


def pose_at(anim: G.Animation | None, t: float) -> dict[str, tuple[tuple, tuple]]:
    """{bone: (turn, move)} of an animation at time t (keys joined in straight lines; the last held after it)."""
    if anim is None:
        return {}
    out = {}
    for bone, chans in anim.bones.items():
        r = _sample(chans["rotation"], t) if "rotation" in chans else (0.0, 0.0, 0.0)
        p = _sample(chans["position"], t) if "position" in chans else (0.0, 0.0, 0.0)
        out[bone] = (r, p)
    return out


def bone_matrices(g: G.GeoModel, pose: dict) -> dict[str, np.ndarray]:
    mats: dict[str, np.ndarray] = {}
    for bone in g.bones:                                   # parents come before their children
        r, p = pose.get(bone.name, ((0.0, 0.0, 0.0), (0.0, 0.0, 0.0)))
        local = turn(bone.pivot, np.add(bone.rotation, r), p)
        mats[bone.name] = mats[bone.parent] @ local if bone.parent else local
    return mats


class Solids:
    """Every box as a solid in model space, to tell whether a point is inside one (the faces it would be seen through
    are drawn: a fight there is buried)."""

    def __init__(self):
        self.inv: list[np.ndarray] = []
        self.lo: list[np.ndarray] = []
        self.hi: list[np.ndarray] = []
        self.amin: list[np.ndarray] = []
        self.amax: list[np.ndarray] = []

    def add(self, C: np.ndarray, lo: np.ndarray, hi: np.ndarray, world: np.ndarray) -> None:
        self.inv.append(np.linalg.inv(C))
        self.lo.append(lo)
        self.hi.append(hi)
        self.amin.append(world.min(axis=0))
        self.amax.append(world.max(axis=0))

    def freeze(self) -> None:
        self.INV, self.LO, self.HI = np.array(self.inv), np.array(self.lo), np.array(self.hi)
        self.AMIN, self.AMAX = np.array(self.amin), np.array(self.amax)

    def covered(self, pts: np.ndarray) -> np.ndarray:
        """For each point (k x 3): whether it lies inside any box."""
        lo, hi = pts.min(axis=0) - 1e-6, pts.max(axis=0) + 1e-6
        near = np.all((self.AMIN <= hi) & (self.AMAX >= lo), axis=1)
        out = np.zeros(len(pts), dtype=bool)
        if not near.any():
            return out
        inv, blo, bhi = self.INV[near], self.LO[near], self.HI[near]
        h = np.c_[pts, np.ones(len(pts))]                              # k x 4
        local = np.einsum("bij,kj->bki", inv, h)[..., :3]              # boxes x k x 3
        inside = np.all((local >= blo[:, None, :] - 1e-6) & (local <= bhi[:, None, :] + 1e-6), axis=2)
        return inside.any(axis=0)


def faces(g: G.GeoModel, pose: dict | None = None, solids: Solids | None = None) -> list[dict]:
    """Every face the export keeps, in model space: its corners, normal, box and the box's middle and corners."""
    hidden = g.hidden_faces() | g.enclosed_faces()
    mats = bone_matrices(g, pose or {})
    out = []
    for bone in g.bones:
        for b in bone.boxes:
            inf = b.__dict__.get("inflate", 0.0) or 0.0
            lo, hi = np.asarray(b.frm, float) - inf, np.asarray(b.to, float) + inf
            C = mats[bone.name] @ box_turn(b)
            corners = np.array([[x, y, z, 1.0] for x in (lo[0], hi[0]) for y in (lo[1], hi[1]) for z in (lo[2], hi[2])])
            world = (C @ corners.T).T[:, :3]
            if solids is not None:
                solids.add(C, lo, hi, world)
            key = tuple(np.round(np.sort(world, axis=0).ravel(), 3))
            middle = world.mean(axis=0)
            for d in b.faces:
                if (b.name, d) in hidden:
                    continue
                ax, side = LOCAL[d]
                o = [k for k in range(3) if k != ax]
                pts = []
                for u, v in ((0, 0), (1, 0), (1, 1), (0, 1)):
                    p = np.empty(3)
                    p[ax] = hi[ax] if side else lo[ax]
                    p[o[0]] = hi[o[0]] if u else lo[o[0]]
                    p[o[1]] = hi[o[1]] if v else lo[o[1]]
                    pts.append([*p, 1.0])
                P = (C @ np.array(pts).T).T[:, :3]
                n = np.zeros(3)
                n[ax] = 1.0 if side else -1.0
                n = C[:3, :3] @ n
                out.append({"box": b.name, "bone": bone.name, "face": d, "pts": P, "n": n, "middle": middle, "key": key})
    return out


def _clip(subject: list, clip: list) -> list:
    """Sutherland-Hodgman: the convex polygon subject cut to the convex, counterclockwise polygon clip."""
    out = subject
    for i in range(len(clip)):
        a, b = clip[i], clip[(i + 1) % len(clip)]
        inp, out = out, []
        if not inp:
            break
        side = lambda p: (b[0] - a[0]) * (p[1] - a[1]) - (b[1] - a[1]) * (p[0] - a[0])
        for j in range(len(inp)):
            p, q = inp[j], inp[(j + 1) % len(inp)]
            sp, sq = side(p), side(q)
            if sp >= 0:
                out.append(p)
            if (sp >= 0) != (sq >= 0):
                f = sp / (sp - sq)
                out.append((p[0] + (q[0] - p[0]) * f, p[1] + (q[1] - p[1]) * f))
    return out


def _area(poly: list) -> float:
    return 0.5 * sum(poly[i][0] * poly[(i + 1) % len(poly)][1] - poly[(i + 1) % len(poly)][0] * poly[i][1] for i in range(len(poly)))


def overlap(a: np.ndarray, b: np.ndarray, n: np.ndarray) -> tuple[list, np.ndarray, np.ndarray]:
    """The polygon (in the plane's u, v) two faces on one plane (normal n) share, and the plane's u and v."""
    e = np.eye(3)[int(np.argmin(np.abs(n)))]
    u = np.cross(n, e)
    u /= np.linalg.norm(u)
    v = np.cross(n, u)
    pa = [(float(p @ u), float(p @ v)) for p in a]
    pb = [(float(p @ u), float(p @ v)) for p in b]
    if _area(pa) < 0:
        pa.reverse()
    if _area(pb) < 0:
        pb.reverse()
    return (_clip(pa, pb) if len(pa) > 2 and len(pb) > 2 else []), u, v


def seen(poly: list, u: np.ndarray, v: np.ndarray, n: np.ndarray, at: float, solids: Solids, step: float = 0.2) -> float:
    """How much of a polygon on the plane n.x = at (px2) can be seen from the side n points to: sample points just off
    it that lie inside no box."""
    us, vs = [p[0] for p in poly], [p[1] for p in poly]
    gu = np.arange(min(us) + step / 2, max(us), step)
    gv = np.arange(min(vs) + step / 2, max(vs), step)
    if gu.size == 0 or gv.size == 0:
        return 0.0
    U, V = np.meshgrid(gu, gv, indexing="ij")
    U, V = U.ravel(), V.ravel()
    inside = np.ones(U.size, dtype=bool)
    for i in range(len(poly)):                                   # counterclockwise: inside is left of every edge
        a, b = poly[i], poly[(i + 1) % len(poly)]
        inside &= (b[0] - a[0]) * (V - a[1]) - (b[1] - a[1]) * (U - a[0]) >= 0
    if not inside.any():
        return 0.0
    pts = U[inside, None] * u + V[inside, None] * v + (at + 0.03) * n
    return float((~solids.covered(pts)).sum()) * step * step


def check(g: G.GeoModel, pose: dict | None = None, tol: float = 0.02, min_area: float = 0.05) -> list[tuple]:
    """(gap, area, description) of every pair of faces within {tol} of one plane whose shared part shows more than
    {min_area} px2 (from the side a face looks to: both sides for faces looking opposite ways)."""
    solids = Solids()
    F = faces(g, pose, solids)
    solids.freeze()
    groups: dict[tuple, list] = {}
    for i, f in enumerate(F):
        n = f["n"]
        k = int(np.argmax(np.abs(n)))
        s = 1.0 if n[k] > 0 else -1.0
        nc = n * s
        groups.setdefault(tuple(np.round(nc, 3)), []).append((float(nc @ f["pts"][0]), i, s, nc))
    issues = []
    for lst in groups.values():
        lst.sort(key=lambda e: e[0])
        for a in range(len(lst)):
            da, i, si, nc = lst[a]
            for b in range(a + 1, len(lst)):
                db, j, sj, _ = lst[b]
                if db - da > tol:
                    break
                fi, fj = F[i], F[j]
                if fi["box"] == fj["box"] or fi["key"] == fj["key"]:
                    continue                       # one box, or two identical boxes (overlays drawn as one)
                if si != sj and db - da < 1e-4:
                    # Opposite faces touching: hidden if the two boxes lie on opposite sides of the plane.
                    if (nc @ fi["middle"] - da) * (nc @ fj["middle"] - da) < -1e-9:
                        continue
                poly, u, v = overlap(fi["pts"], fj["pts"], nc)
                if len(poly) < 3 or abs(_area(poly)) <= min_area:
                    continue
                # Seen from the side the faces look to (the outer of the two planes); faces looking opposite ways from
                # both sides (GeckoLib draws back faces).
                area = 0.0
                if si > 0 or sj > 0:
                    area += seen(poly, u, v, nc, db if si > 0 and sj > 0 else max(da, db), solids)
                if si < 0 or sj < 0:
                    area += seen(poly, u, v, -nc, -(da if si < 0 and sj < 0 else min(da, db)), solids)
                if area > min_area:
                    issues.append((db - da, area, f"{fi['box']}/{fi['face']} vs {fj['box']}/{fj['face']} "
                                                  f"[{fi['bone']} | {fj['bone']}] gap {db - da:.3f} share {area:.2f} px2"
                                                  f" at {np.round(fi['pts'].mean(axis=0), 1).tolist()}"))
    issues.sort(key=lambda e: (e[0], -e[1]))
    return issues


def load(target: str) -> tuple[list[G.GeoModel], list[G.Animation]]:
    """Runs a script's build function with saving turned off: the models it returns or would save, and its
    animations (its animations() or what it would save)."""
    module_name, _, fn_name = target.partition(":")
    models: list[G.GeoModel] = []
    anims: list[G.Animation] = []

    def fake_save(self, tex, emissive=None):
        models.append(self)
        return None, None

    def fake_save_animations(name, animations, *a, **kw):
        anims.extend(animations)
        return None
    G.GeoModel.save = fake_save
    G.save_animations = fake_save_animations
    mod = importlib.import_module(module_name)
    if hasattr(mod, "save_animations"):
        mod.save_animations = fake_save_animations
    for attr in dir(mod):                                  # helper modules imported by name (e.g. "SS")
        sub = getattr(mod, attr)
        if getattr(sub, "__name__", "").startswith(("scud_storm", "nuclear_silo", "launch", "structures")) and hasattr(sub, "save_animations"):
            sub.save_animations = fake_save_animations
    result = getattr(mod, fn_name or "build")()
    for r in (result if isinstance(result, tuple) else (result,)):
        if isinstance(r, G.GeoModel) and r not in models:
            models.append(r)
    if not anims and hasattr(mod, "animations"):
        anims = list(mod.animations())
    for m in models:
        if not m._laid_out:
            m.close_holes()
    return models, anims


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("target", help="script[:function] in tools/assets (default function: build)")
    ap.add_argument("--anim", help="an animation to pose the model by")
    ap.add_argument("--time", type=float, default=None, help="seconds into it (default: its end)")
    ap.add_argument("--tol", type=float, default=0.02)
    ap.add_argument("--min-area", type=float, default=0.05)
    args = ap.parse_args()
    models, anims = load(args.target)
    anim = None
    if args.anim:
        anim = next((a for a in anims if a.name == args.anim), None)
        if anim is None:
            sys.exit(f"no animation {args.anim}: {[a.name for a in anims]}")
    t = args.time if args.time is not None else (anim.length if anim else 0.0)
    for g in models:
        issues = check(g, pose_at(anim, t), args.tol, args.min_area)
        print(f"{g.name}{f' ({anim.name} at {t:g} s)' if anim else ''}: {len(issues)}")
        for _, _, s in issues:
            print("   ", s)


if __name__ == "__main__":
    main()
