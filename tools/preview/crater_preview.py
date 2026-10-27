"""Crater preview: the explosion engine's ray field (src/.../blast/BlastJob.java, RayField.java) re-run in numpy over
flat test ground, drawn from above and in cross-section, so crater shapes can be tuned without starting the game.

Ground: grass at y = 0, dirt down to y = -3, stone below; the burst sits 1.5 blocks above the grass. Costs, splitting,
the crater / pressure split and the streak noise follow the Java code (keep them in step).

    tools\\.venv\\Scripts\\python.exe tools\\preview\\crater_preview.py --crater 40 --streaks 0.35
"""
from __future__ import annotations

import argparse
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from gz import paths  # noqa: E402

AIR_COST = 0.05
SPLIT = 0.7
RES = {"grass": 0.6, "dirt": 0.5, "stone": 6.0}
N = np.array([[1, 0, 0], [-1, 0, 0], [0, 1, 0], [0, -1, 0], [0, 0, 1], [0, 0, -1]], float)
A = np.array([[0, 1, 0], [0, 1, 0], [0, 0, 1], [0, 0, 1], [1, 0, 0], [1, 0, 0]], float)
B = np.array([[0, 0, 1], [0, 0, 1], [1, 0, 0], [1, 0, 0], [0, 1, 0], [0, 1, 0]], float)
M32 = 0xFFFFFFFF


def cost(res: float) -> float:
    return 0.3 + res ** 0.75


# --- the streak noise (StreakNoise.java): value noise over the direction, three octaves ---------------------------------
def _hash(x, y, z, seed: int):
    """32-bit integer hash of lattice points (wraps like Java ints), to [-1, 1)."""
    x = x.astype(np.int64) & M32
    y = y.astype(np.int64) & M32
    z = z.astype(np.int64) & M32
    h = ((x * 0x27D4EB2D) & M32) ^ ((y * 0x165667B1) & M32) ^ ((z * 0x9E3779B9) & M32) ^ (seed & M32)
    h ^= h >> 15
    h = (h * 0x85EBCA6B) & M32
    h ^= h >> 13
    h = (h * 0xC2B2AE35) & M32
    h ^= h >> 16
    return (h & 0xFFFFFF).astype(float) / 0x7FFFFF - 1.0


def _noise(p, seed: int):
    f = np.floor(p)
    t = p - f
    w = t * t * (3 - 2 * t)
    i = f.astype(np.int64)
    out = 0.0
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                v = _hash(i[:, 0] + dx, i[:, 1] + dy, i[:, 2] + dz, seed)
                wx = w[:, 0] if dx else 1 - w[:, 0]
                wy = w[:, 1] if dy else 1 - w[:, 1]
                wz = w[:, 2] if dz else 1 - w[:, 2]
                out = out + v * wx * wy * wz
    return out


OCTAVES = ((7.0, 0.5), (15.0, 0.3), (31.0, 0.2))


def streak(d, seed: int):
    """Fine noise over the direction (about 45, 95 and 195 features round a circle): neighbouring rays differ, and as
    each ray runs straight out the difference draws a radial line."""
    out = 0.0
    for k, (freq, amp) in enumerate(OCTAVES):
        out = out + amp * _noise(d * freq, seed + 31 * k)
    return out


def factor(d, seed: int, streaks: float):
    """Energy and reach multiplier of a ray by direction: strong directions carve fingers past the mean crater radius
    and spikes into its floor."""
    return np.clip(1 + streaks * 1.7 * streak(d, seed), 0.45, 1.8)


# --- the field ---------------------------------------------------------------------------------------------------------
class Field:
    def __init__(self):
        self.face = np.zeros(0, int)
        self.u = np.zeros(0)
        self.v = np.zeros(0)
        self.s = np.zeros(0)
        self.e = np.zeros(0)
        self.mode = np.zeros(0, int)   # 0 crater, 1 pressure, 2 dead
        self.reach = np.zeros(0)       # how far this ray may destroy

    def dirs(self, face=None, u=None, v=None):
        face = self.face if face is None else face
        u = self.u if u is None else u
        v = self.v if v is None else v
        x = N[face] + u[:, None] * A[face] + v[:, None] * B[face]
        return x / np.linalg.norm(x, axis=1)[:, None]


def eased_streaks(crater: float, streaks: float) -> float:
    """As BlastJob.streaks: a big crater's streaks eased off, from all of them at 40 blocks to 0.7 at 200 and over."""
    big = min(1.0, max(0.0, (crater - 40) / 160))
    return streaks * (1 - 0.3 * big)


def run(crater: float, structural: float, scorch: float, depth: float, lobes: float, streaks: float, seed: int, ejecta: float = 0.0):
    streaks = eased_streaks(crater, streaks)
    f = Field()
    fc, uu, vv = [], [], []
    for face in range(6):
        for i in range(4):
            for j in range(4):
                fc.append(face)
                uu.append(-0.75 + 0.5 * i)
                vv.append(-0.75 + 0.5 * j)
    f.face, f.u, f.v = np.array(fc), np.array(uu), np.array(vv)
    f.s = np.full(len(fc), 0.5)
    d = f.dirs()
    base = crater * cost(RES["stone"])
    f.e = base * (1 - (1 - depth) * np.maximum(0, -d[:, 1])) * factor(d, seed, streaks)
    f.mode = np.zeros(len(fc), int)
    f.reach = crater * factor(d, seed, streaks)
    center = np.array([0.5, 1.5, 0.5])
    destroyed: set[tuple] = set()
    scorched: set[tuple] = set()
    visited: set[tuple] = set()
    outer = max(crater, structural, scorch)

    def block(y):
        return "grass" if y == 0 else "dirt" if -3 <= y < 0 else "stone" if y < -3 else None

    rubble: set[tuple] = set()
    rng = np.random.default_rng(seed)
    R = int(outer) + 2
    cols = [(x, z, math.hypot(x + 0.5 - center[0], z + 0.5 - center[2])) for x in range(-R, R + 1) for z in range(-R, R + 1)]
    by_ring: dict[int, list] = {}
    for x, z, dist in cols:
        by_ring.setdefault(int(math.ceil(dist)), []).append((x, z, dist))
    BUCKETS = 720
    horizon = np.full(BUCKETS, -1e9)

    def surface(x, z):
        y = 0
        while (x, y, z) in destroyed and y > -60:
            y -= 1
        return y

    def ground_ring(r):
        """GroundPass.java: the burst's view of the ground ring at distance r; spokes of scorch and ejecta."""
        for x, z, dist in by_ring.get(r, []):
            top = surface(x, z)
            eye = center[1] + max(1.5, 0.25 * crater)
            ang = math.atan2(top + 1 - eye, max(dist, 0.5))
            az = math.atan2(z + 0.5 - center[2], x + 0.5 - center[0])
            b = int((az + math.pi) / (2 * math.pi) * BUCKETS) % BUCKETS
            visible = ang >= horizon[b] - 0.02
            half = int(min(BUCKETS // 8, math.ceil(0.5 / max(dist, 0.5) / (2 * math.pi) * BUCKETS)))
            for i in range(-half, half + 1):
                j = (b + i) % BUCKETS
                horizon[j] = max(horizon[j], ang)
            if not visible or (x, 0, z) in destroyed or dist < crater * 0.45:
                continue
            h = np.array([[math.cos(az), 0.0, math.sin(az)]])
            spoke = float(streak(h, seed)[0])          # same noise as the crater's rays: spokes line up with its fingers
            if dist <= scorch and dist / scorch < 0.72 + 0.6 * spoke * (1 + streaks) and rng.random() > 0.08:
                if top == 0:
                    scorched.add((x, 0, z))
            if ejecta > 0 and crater * 0.8 <= dist <= crater * 1.9:
                t = max(0.0, (dist - crater) / (0.9 * crater))
                pr = ejecta * max(0.0, spoke + 0.05) * 3.0 * (1 - t) ** 1.5
                if rng.random() < pr:
                    rubble.add((x, top + 1, z))

    for r in range(0, int(outer) + 1):
        # split
        while True:
            alive = f.mode != 2
            cu, cv = f.u, f.v
            dd = 1 + cu * cu + cv * cv
            stretch = np.maximum(np.sqrt(1 + cu * cu), np.sqrt(1 + cv * cv)) / dd
            need = alive & (max(1, r) * f.s * stretch > SPLIT)
            if not need.any():
                break
            idx = np.where(need)[0]
            parent_d = f.dirs(f.face[idx], f.u[idx], f.v[idx])
            pf = factor(parent_d, seed, streaks)
            nf, nu, nv, ns, ne, nm, nr = [], [], [], [], [], [], []
            for du, dv in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
                q = f.s[idx] / 4
                cu2, cv2 = f.u[idx] + du * q, f.v[idx] + dv * q
                cd = f.dirs(f.face[idx], cu2, cv2)
                cf = factor(cd, seed, streaks)
                nf.append(f.face[idx]); nu.append(cu2); nv.append(cv2); ns.append(f.s[idx] / 2)
                ne.append(f.e[idx] * cf / pf); nm.append(f.mode[idx])
                nr.append(crater * cf)
            keep = ~need
            f.face = np.concatenate([f.face[keep]] + nf)
            f.u = np.concatenate([f.u[keep]] + nu)
            f.v = np.concatenate([f.v[keep]] + nv)
            f.s = np.concatenate([f.s[keep]] + ns)
            f.e = np.concatenate([f.e[keep]] + ne)
            f.mode = np.concatenate([f.mode[keep]] + nm)
            f.reach = np.concatenate([f.reach[keep]] + nr)
        d = f.dirs()
        pos = np.floor(center + d * r).astype(int)
        for k in range(len(f.e)):
            if f.mode[k] == 2:
                continue
            p = tuple(pos[k])
            kind = block(p[1])
            if kind is None or p in destroyed:
                f.e[k] -= AIR_COST
                if d[k, 1] >= 0 and p[1] > 0:
                    f.mode[k] = 2
                continue
            if p in visited:
                f.mode[k] = 2
                continue
            visited.add(p)
            if f.mode[k] == 0 and r <= f.reach[k]:
                c = cost(RES[kind])
                if f.e[k] < c:
                    f.mode[k] = 2
                else:
                    f.e[k] -= c
                    destroyed.add(p)
            else:
                f.mode[k] = 2
            if f.mode[k] == 0 and r + 1 > f.reach[k]:
                f.mode[k] = 1
        keep = f.mode != 2
        f.face, f.u, f.v, f.s, f.e, f.mode, f.reach = f.face[keep], f.u[keep], f.v[keep], f.s[keep], f.e[keep], f.mode[keep], f.reach[keep]
        ground_ring(r)
    return destroyed, scorched, outer, rubble

    # (ground_ring is defined before the loop runs; see below)


def draw(destroyed, scorched, outer, out: Path, title: str, rubble=frozenset()):
    R = int(outer) + 4
    size = 2 * R + 1
    scale = max(2, 640 // size)
    top = Image.new("RGB", (size, size), (86, 140, 58))
    px = top.load()
    deepest = {}
    for (x, y, z) in destroyed:
        key = (x, z)
        deepest[key] = min(deepest.get(key, 1), y)
    lowest = min([v for v in deepest.values()] + [-1])
    for (x, y, z) in scorched:
        if -R <= x <= R and -R <= z <= R:
            px[x + R, z + R] = (110, 84, 56)
    for (x, z), y in deepest.items():
        if -R <= x <= R and -R <= z <= R:
            t = (y - lowest) / max(1, -lowest)
            g = int(40 + 150 * t)
            px[x + R, z + R] = (g, g - 6, g - 14)
    for (x, y, z) in rubble:
        if -R <= x <= R and -R <= z <= R:
            px[x + R, z + R] = (150, 150, 150)
    top = top.resize((size * scale, size * scale), Image.NEAREST)
    # Cross-section through z = 0.
    depth_rows = int(max(8, -lowest + 4))
    side = Image.new("RGB", (size, depth_rows + 6), (170, 200, 230))
    sp = side.load()
    for x in range(-R, R + 1):
        for y in range(1, -depth_rows, -1):
            yy = 1 - y
            if y > 0:
                continue
            kind = "grass" if y == 0 else "dirt" if y >= -3 else "stone"
            col = {"grass": (86, 140, 58), "dirt": (120, 86, 54), "stone": (120, 120, 120)}[kind]
            if (x, y, 0) in destroyed:
                col = (170, 200, 230)
            sp[x + R, yy + 4] = col
    side = side.resize((size * scale, (depth_rows + 6) * scale), Image.NEAREST)
    img = Image.new("RGB", (size * scale, size * scale + side.height + 24), (30, 30, 34))
    img.paste(top, (0, 22))
    img.paste(side, (0, 24 + top.height))
    ImageDraw.Draw(img).text((6, 4), title, fill=(230, 230, 230))
    img.save(out)
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--crater", type=float, default=32)
    ap.add_argument("--structural", type=float, default=0)
    ap.add_argument("--scorch", type=float, default=44)
    ap.add_argument("--depth", type=float, default=0.55)
    ap.add_argument("--lobes", type=float, default=0.18)
    ap.add_argument("--streaks", type=float, default=0.0)
    ap.add_argument("--seed", type=int, default=12345)
    ap.add_argument("--ejecta", type=float, default=0.0)
    ap.add_argument("--name", default="crater")
    a = ap.parse_args()
    destroyed, scorched, outer, rubble = run(a.crater, a.structural, a.scorch, a.depth, a.lobes, a.streaks, a.seed, a.ejecta)
    out = paths.ROOT / "tools" / "out" / "previews" / f"{a.name}.png"
    print(draw(destroyed, scorched, outer, out, f"crater {a.crater} scorch {a.scorch} streaks {a.streaks} ejecta {a.ejecta}  ({len(destroyed)} blocks)", rubble))


if __name__ == "__main__":
    main()
