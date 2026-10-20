"""Spectacle effect textures (DESIGN.md §12): the puff sheet, the fireball surface and the shock shell.

These are not pixel-art block textures: they are soft, smooth effect sprites drawn on billboards and spheres, tinted by
vertex colour in game (so they are mostly greyscale) and seen at any size. They are made from seeded spectral noise
(tileable by construction where they wrap round a sphere) and radial falloffs.

- ``textures/fx/puffs.png`` (512x640, a 4x5 sheet of 128 px cells, cell n at column n % 4, row n // 4):
  0-7 smoke (billowy, lit from above-left), 8-11 glow (hot cores with wisps, for the fireball body),
  12 spark (a point with a halo), 13-15 dust (grainier, flatter, ragged edges), 16-19 mushroom-cloud cloudlets (soft
  cauliflower billows with no baked light direction: the game lights each one from the sun). One sheet, so smoke and
  cloudlets are drawn in one sorted batch.
- ``textures/fx/fireball.png`` (128x128, tileable): boiling fire; bright veins on a darker body, alpha mostly high.
- ``textures/fx/shock.png`` (64x64, tileable): faint streaky haze for the shock shell.
"""
from __future__ import annotations

import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from gz import paths  # noqa: E402

OUT = paths.TEXTURES / "fx"
CELL = 128


def spectral(size: int, beta: float, seed: int, aniso: tuple[float, float] = (1.0, 1.0)) -> np.ndarray:
    """Tileable noise with a 1/f^beta spectrum, normalised to [0, 1]."""
    rng = np.random.default_rng(seed)
    white = rng.standard_normal((size, size))
    fy = np.fft.fftfreq(size)[:, None] * aniso[1]
    fx = np.fft.fftfreq(size)[None, :] * aniso[0]
    f = np.sqrt(fx * fx + fy * fy)
    f[0, 0] = 1.0
    spectrum = np.fft.fft2(white) / f ** beta
    spectrum[0, 0] = 0
    out = np.real(np.fft.ifft2(spectrum))
    out -= out.min()
    return out / out.max()


def smoothstep(e0: float, e1: float, x: np.ndarray) -> np.ndarray:
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


def grid(size: int) -> tuple[np.ndarray, np.ndarray]:
    """Cell coordinates in [-1, 1]."""
    c = (np.arange(size) + 0.5) / size * 2 - 1
    return np.meshgrid(c, c)


def smoke(seed: int, grain: float = 1.0, flat: float = 1.0) -> np.ndarray:
    """A billowy puff: a cluster of soft round billows, each lit on its upper-left side, with a ragged outline."""
    rng = np.random.default_rng(seed)
    x, y = grid(CELL)
    height = np.zeros((CELL, CELL))
    # One big billow in the middle and a ring of smaller ones round it (a cauliflower outline).
    blobs = [(0.0, 0.05, 0.5)]
    for k in range(7 + rng.integers(0, 4)):
        a = rng.uniform(0, 2 * np.pi)
        d = rng.uniform(0.25, 0.5)
        blobs.append((np.cos(a) * d, np.sin(a) * d * flat, rng.uniform(0.22, 0.36)))
    for bx, by, br in blobs:
        r2 = ((x - bx) ** 2 + (y * flat - by) ** 2) / (br * br)
        height = np.maximum(height, np.sqrt(np.clip(1 - r2, 0, 1)) * br)
    fine = spectral(CELL, 1.6, seed + 99)
    height = height + 0.03 * grain * (fine - 0.5)
    # Dense in the middle, thinning toward a frayed edge (smoke, not cotton wool).
    alpha = smoothstep(0.0, 0.3, height + 0.06 * grain * (fine - 0.5)) * (0.62 + 0.38 * fine)
    alpha = np.clip(alpha, 0, 1)
    alpha = np.asarray(Image.fromarray((alpha * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(1.6 * grain * CELL / 64)), float) / 255
    gy, gx = np.gradient(height * CELL / 2)
    nz = 1.0 / np.sqrt(1 + gx * gx + gy * gy)
    light = np.clip((-gx * -0.45 + -gy * -0.6) * nz + 0.65 * nz, 0, 1)    # lit from the upper left
    shade = np.clip(0.55 + 0.45 * light + 0.06 * (fine - 0.5), 0.4, 1.0)
    rgb = np.stack([shade, shade * 0.985, shade * 0.97], -1)
    return np.concatenate([rgb, alpha[..., None]], -1)


def glow(seed: int) -> np.ndarray:
    """A hot core fading out through wisps."""
    x, y = grid(CELL)
    n = spectral(CELL, 1.8, seed)
    r = np.sqrt(x * x + y * y)
    core = np.exp(-(r / 0.38) ** 2)
    body = (1 - smoothstep(0.25, 0.95, r + 0.25 * (n - 0.5)))
    alpha = np.clip(0.85 * body * (0.5 + 0.5 * n) + 0.5 * core, 0, 1)
    bright = np.clip(0.6 + 0.4 * core + 0.25 * (n - 0.5), 0, 1)
    rgb = np.stack([bright, bright, bright], -1)
    return np.concatenate([rgb, alpha[..., None]], -1)


def spark() -> np.ndarray:
    x, y = grid(CELL)
    r = np.sqrt(x * x + y * y)
    alpha = np.clip(np.exp(-(r / 0.16) ** 2) + 0.35 * np.exp(-(r / 0.5) ** 2), 0, 1)
    rgb = np.ones((CELL, CELL, 3))
    return np.concatenate([rgb, alpha[..., None]], -1)


def puffs() -> Image.Image:
    sheet = np.zeros((CELL * 5, CELL * 4, 4))
    cells = [smoke(10 + i) for i in range(8)] + [glow(40 + i) for i in range(4)] + [spark()] \
        + [smoke(70 + i, grain=1.6, flat=1.25) for i in range(3)] + [cloudlet(300 + i) for i in range(4)]
    for n, cell in enumerate(cells):
        cx, cy = n % 4, n // 4
        sheet[cy * CELL:(cy + 1) * CELL, cx * CELL:(cx + 1) * CELL] = cell
    return Image.fromarray((np.clip(sheet, 0, 1) * 255).astype(np.uint8), "RGBA")


def fireball() -> Image.Image:
    size = 128
    body = spectral(size, 2.3, 7)
    swirl = spectral(size, 1.6, 8)
    # Bright boiling cells (where the body noise peaks) threaded with softer bright ridges, on a darker roiling base.
    ridge = 1 - np.abs(spectral(size, 2.0, 9) - 0.5) * 2
    bright = 0.42 + 0.5 * smoothstep(0.35, 0.85, body) + 0.25 * ridge ** 3 + 0.12 * (swirl - 0.5)
    bright = np.clip(0.35 + 0.65 * bright, 0.6, 1)
    alpha = np.clip(0.55 + 0.45 * smoothstep(0.2, 0.7, body) + 0.15 * (swirl - 0.5), 0.35, 1)
    rgb = np.stack([bright, np.clip(bright * 0.95, 0, 1), np.clip(bright * 0.88, 0, 1)], -1)
    return Image.fromarray((np.concatenate([rgb, alpha[..., None]], -1) * 255).astype(np.uint8), "RGBA")


def shock() -> Image.Image:
    size = 64
    haze = spectral(size, 2.4, 22)
    fine = spectral(size, 1.5, 23)
    alpha = np.clip(0.55 + 0.45 * (0.75 * haze + 0.25 * fine), 0, 1)
    rgb = np.ones((size, size, 3))
    return Image.fromarray((np.concatenate([rgb, alpha[..., None]], -1) * 255).astype(np.uint8), "RGBA")


def cloudlet(seed: int) -> np.ndarray:
    """A big soft billow cluster: brighter where it is thick, no light direction, edges frayed by noise."""
    size = CELL
    rng = np.random.default_rng(seed)
    c = (np.arange(size) + 0.5) / size * 2 - 1
    x, y = np.meshgrid(c, c)
    height = np.zeros((size, size))
    blobs = [(0.0, 0.0, 0.48)]
    for _ in range(9 + rng.integers(0, 5)):
        a = rng.uniform(0, 2 * np.pi)
        d = rng.uniform(0.2, 0.52)
        blobs.append((np.cos(a) * d, np.sin(a) * d, rng.uniform(0.2, 0.34)))
    for bx, by, br in blobs:
        r2 = ((x - bx) ** 2 + (y - by) ** 2) / (br * br)
        height = np.maximum(height, np.sqrt(np.clip(1 - r2, 0, 1)) * br)
    fine = spectral(size, 1.7, seed + 7)
    body = spectral(size, 2.4, seed + 8)
    alpha = smoothstep(0.0, 0.32, height + 0.07 * (fine - 0.5)) * (0.7 + 0.3 * body)
    alpha = np.asarray(Image.fromarray((np.clip(alpha, 0, 1) * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(2.4)), float) / 255
    shade = np.clip(0.72 + 0.5 * height + 0.12 * (body - 0.5) + 0.06 * (fine - 0.5), 0.6, 1.0)
    rgb = np.stack([shade, shade, shade], -1)
    return np.concatenate([rgb, alpha[..., None]], -1)


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    for name, image in (("puffs", puffs()), ("fireball", fireball()), ("shock", shock())):
        path = OUT / f"{name}.png"
        image.save(path)
        print(path)


if __name__ == "__main__":
    main()
