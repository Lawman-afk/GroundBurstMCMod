"""The project logo (the CurseForge page's icon): a mushroom cloud rising from a fresh crater against a scorched dusk
sky, as pixel art on a 50 x 50 grid drawn eight times over (400 x 400), so it stays crisp and reads as a small icon.

The cloud is built from round puffs (a rolling cap leaning a little with the wind, a dome over it, a stem with a
condensation skirt, a base surge piling up round the stem's foot), each pixel shaded by the puff nearest the viewer
(light from the upper left, the fireball's glow from below) and stepped down a short hand-picked ramp; the sky is
banded with a dithered row between bands; the ground is speckled scorched earth round a crater lit by the fireball.
No smooth gradients: every colour is one of the palette's.

    tools\\.venv\\Scripts\\python.exe tools\\assets\\logo.py      writes docs/branding/logo.png and a review sheet
"""
from __future__ import annotations

import math
import random
import sys
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from gz import paths  # noqa: E402

N = 50          # grid
SCALE = 8       # 400 x 400
OUT = paths.ROOT / "docs" / "branding" / "logo.png"

# Sky, top to horizon: night blue through plum and rust to the glow on the horizon.
SKY = ["#12151f", "#191c2a", "#232233", "#352838", "#53303a", "#7c3d36", "#a85234", "#cf7238"]
# Ground: scorched earth, darkest to the rim lit by the fireball.
GROUND = ["#120d0a", "#1d1511", "#291e17", "#38291f", "#58392a"]
# Cloud: shadow to highlight (smoke brown to sunlit cream).
CLOUD = ["#24171a", "#3e2724", "#5c3a2e", "#80553b", "#a6744d", "#c99563", "#e3b885", "#f5dcae"]
# The fireball's light.
GLOW = ["#b8461c", "#e8692a", "#ff9c3d", "#ffd27a"]
STAR = "#4b4c63"

HORIZON = 37
STEM_X = 25.0


def rgb(h: str) -> tuple[int, int, int]:
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5))


def puffs(rng: random.Random) -> list[tuple[float, float, float]]:
    """The cloud as (x, y, radius) puffs, drawn in this order (later ones in front where they stand out more)."""
    p = []
    cx, cy = 25.8, 12.6
    # The cap: a rolling torus seen a little from below, leaning a little to the right with the wind.
    for i in range(16):
        a = 2 * math.pi * i / 16
        x, y = cx + 11.6 * math.cos(a), cy + 2.9 * math.sin(a) + 0.5 * math.cos(a)
        p.append((x, y, 3.3 + rng.uniform(-0.35, 0.55)))
    # The dome over it, off-centre with the wind.
    for x, y, r in ((20.0, 9.6, 3.9), (25.6, 8.0, 4.6), (31.0, 9.2, 3.9), (23.0, 10.6, 3.6), (28.6, 10.4, 3.6)):
        p.append((x + rng.uniform(-0.3, 0.3), y + rng.uniform(-0.3, 0.3), r))
    # The underside rolling back in towards the stem.
    for x, y, r in ((15.4, 15.4, 2.5), (36.0, 15.6, 2.5), (19.6, 16.4, 2.3), (31.8, 16.6, 2.3)):
        p.append((x, y, r))
    # The stem, wider at the foot, swaying a little.
    y = 17.5
    while y <= HORIZON - 2.0:
        t = (y - 17.5) / (HORIZON - 19.5)
        x = STEM_X + 0.7 * math.sin(y * 0.5)
        p.append((x, y, 2.0 + 1.0 * t + rng.uniform(-0.12, 0.12)))
        y += 1.3
    # The condensation skirt round the stem: a thin ring, its front half passing in front of the stem.
    for i in range(24):
        a = 2 * math.pi * i / 24
        p.append((STEM_X + 5.6 * math.cos(a), 25.5 + 0.8 * math.sin(a), 1.05 + rng.uniform(-0.05, 0.15)))
    # The base surge piling up round the stem's foot and rolling out along the ground.
    for i in range(17):
        x = 8.0 + i * 2.15
        lift = max(0.0, 1.0 - abs(x - STEM_X) / 17.0)
        p.append((x, HORIZON - 0.4 - 2.2 * lift ** 1.5 + rng.uniform(-0.35, 0.35), 1.6 + 2.2 * lift ** 1.2 + rng.uniform(-0.2, 0.35)))
    return p


def main() -> None:
    rng = random.Random(1945)
    img = np.zeros((N, N, 3), dtype=np.uint8)

    # Sky: bands, a dithered row where one band meets the next.
    rows = HORIZON + 1
    for y in range(rows):
        band = min(len(SKY) - 1, int(y / (rows - 1) * len(SKY)))
        nxt = min(len(SKY) - 1, band + 1)
        edge = int((y + 1) / (rows - 1) * len(SKY)) > band
        for x in range(N):
            img[y, x] = rgb(SKY[nxt] if edge and (x + y) % 2 == 0 else SKY[band])
    for x, y in ((4, 3), (11, 6), (41, 4), (46, 9), (7, 13), (36, 2), (45, 16), (2, 18)):
        img[y, x] = rgb(STAR)

    # Distant hills on the horizon, then speckled scorched ground with the crater in the middle.
    hill = [HORIZON - 1 - int(1.4 + 1.4 * math.sin(x * 0.33) + 1.1 * math.sin(x * 0.12 + 1.0)) for x in range(N)]
    for x in range(N):
        for y in range(hill[x], HORIZON + 1):
            img[y, x] = rgb(GROUND[1])
    for y in range(HORIZON + 1, N):
        depth = (y - HORIZON) / (N - HORIZON)
        for x in range(N):
            roll = rng.random()
            k = 3 if roll < 0.10 * (1 - depth) else 2 if roll < 0.45 else 1
            img[y, x] = rgb(GROUND[k])
    for y in range(HORIZON + 1, HORIZON + 6):
        half = 10.5 - 1.9 * (y - HORIZON - 1)
        for x in range(N):
            d = abs(x + 0.5 - STEM_X)
            if d <= half:
                img[y, x] = rgb(GROUND[0])
            elif d <= half + 1.3:
                img[y, x] = rgb(GROUND[4] if y <= HORIZON + 2 else GROUND[3])

    # The cloud.
    p = puffs(rng)
    light = np.array([-0.62, -0.62, 0.36])
    light /= np.linalg.norm(light)
    core = np.array([STEM_X + 0.3, 17.2])
    shade = np.full((N, N), -1.0)
    glow = np.zeros((N, N))
    for y in range(N):
        for x in range(N):
            best, normal = -1.0, None
            for px, py, r in p:
                dx, dy = (x + 0.5 - px) / r, (y + 0.5 - py) / r
                d2 = dx * dx + dy * dy
                if d2 < 1:
                    z = math.sqrt(1 - d2)
                    if z * r + 0.02 * py > best:        # the puff standing out most towards the viewer wins
                        best, normal = z * r + 0.02 * py, (dx, dy, z)
            if normal is None:
                continue
            lam = max(0.0, normal[0] * light[0] + normal[1] * light[1] + normal[2] * light[2])
            # Shadowed where the cap overhangs (its underside) and towards the stem's middle.
            occl = 0.0
            if 14.5 < y < 19 and abs(x + 0.5 - STEM_X) < 12:
                occl = 0.18
            shade[y, x] = max(0.0, lam ** 0.85 - occl)
            under = max(0.0, normal[1]) * math.exp(-((x + 0.5 - core[0]) ** 2 / 55 + (y + 0.5 - core[1]) ** 2 / 9))
            foot = math.exp(-((x + 0.5 - STEM_X) ** 2 / 7 + (y + 0.5 - (HORIZON - 1.2)) ** 2 / 3.5))
            glow[y, x] = max(under * 1.25, foot * 1.05)
    cloud = shade >= 0
    for y in range(N):
        for x in range(N):
            if not cloud[y, x]:
                continue
            g = glow[y, x]
            if g > 0.62:
                c = GLOW[min(3, 1 + int((g - 0.62) / 0.16))]
            elif g > 0.40:
                c = GLOW[0]
            else:
                t = min(0.999, shade[y, x] * 1.05 + 0.2 * g)
                c = CLOUD[1 + int(t * (len(CLOUD) - 1))]
            # A dark edge where the cloud meets the sky on its shadowed side (right and below), none on the lit side.
            if any(0 <= y + dy < N and 0 <= x + dx < N and not cloud[y + dy, x + dx] for dx, dy in ((1, 0), (0, 1))):
                if shade[y, x] < 0.5 and g < 0.40:
                    c = CLOUD[0]
            img[y, x] = rgb(c)

    # The fireball's light on the ground round the crater's rim (dithered, fading out).
    for x in range(N):
        for y in range(HORIZON, HORIZON + 4):
            d = abs(x + 0.5 - STEM_X)
            if cloud[y, x] or tuple(img[y, x]) == rgb(GROUND[0]):
                continue
            if d < 9 and (x + y) % 2 == 0:
                img[y, x] = rgb(GLOW[0] if d < 6.5 else GROUND[4])

    big = Image.fromarray(img).resize((N * SCALE, N * SCALE), Image.NEAREST)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    big.save(OUT)
    # Review sheet: full size beside the sizes CurseForge shows it at (128, 64, 32).
    sheet = Image.new("RGB", (N * SCALE + 170, N * SCALE), (40, 40, 44))
    sheet.paste(big, (0, 0))
    sheet.paste(big.resize((128, 128), Image.LANCZOS), (N * SCALE + 20, 20))
    sheet.paste(big.resize((64, 64), Image.LANCZOS), (N * SCALE + 20, 170))
    sheet.paste(big.resize((32, 32), Image.LANCZOS), (N * SCALE + 20, 255))
    review = paths.OUT / "previews" / "logo_review.png"
    review.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(review)
    print(OUT, big.size)
    print(review)


if __name__ == "__main__":
    main()
