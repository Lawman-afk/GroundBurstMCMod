"""The aftermath on the ground (DESIGN.md §21.7, §21.8 slice 12b): what a nuclear blast leaves of the land, what a strong
radiation field kills, and the ash a fallout plume lays.

Painted to the texture remaster's rules (DESIGN.md §4.3, the user's request after the first look at them): each block a
designed surface, not noise with dots. The material library's tiles (`gz/materials.py`: world-space noise that wraps
every block, a short hue-shifted ramp per material, scattered dithering for rough matter) give the base; over it, in the
same wrapping space, the block's own structure: pieces (a cell network that wraps) each with its tone and its rounded
light, lit on their top-left edges and shaded on their bottom-right ones, dark seams between, and the small things that
tell the material - bubbles and glints in glass, vesicles and rust runs in slag, charcoal and ash in burnt earth, the
alligatored blocks of burnt wood. Everything stays on its ramp (a shade up or down, never off it), save the glints, the
sheen on char, rust and the glow, which are drawn over it as the library's weathering and glyphs are.

- Trinitite: glossy nodules of bottle-green glass, bubbles, a black inclusion or two, a glint on the biggest.
- Glassed Sand: plates of glaze crazed over gritty sand, flaked off here and there to the grit.
- Slag: dark lumps full of vesicles, rust running down from a few.
- Scorched Earth: a burnt crust dried into plates, deep cracks with ash in them, bits of charcoal.
- Scorched Stone: grey stone sooted in patches, fissured where the heat cracked it, a flake spalled clean.
- Irradiated Soil: clods of olive-brown earth, sickly green specks glowing in the gaps (an emissive layer, as the ores').
- Ash and Fallout Ash: powder lying in soft drifts (shaded as relief), charcoal crumbs; fallout greyer, gritty, with a
  few hot specks.
- Charred Log, Wood and Planks: wood burnt to blocks of char with deep cracks and a dull silver sheen on their lit edges;
  the log's end in charred rings with radial checks; the planks four boards, each crazed along its length.
- Dead Grass Block, Dead Leaves, Withered Crops: dry straw-grey grass over dry earth, brown curled leaves with gaps, dead
  stalks with drooping heads.

    tools/.venv/Scripts/python.exe tools/assets/aftermath.py
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import numpy as np  # noqa: E402

from gz import materials as M  # noqa: E402
from gz import paths  # noqa: E402
from gz.model import write_json  # noqa: E402
from gz.tex import Tex  # noqa: E402

DIRS = ("down", "up", "north", "south", "west", "east")
N = 16

# --- ramps: darkest to lightest, lit shades warmer, shadows cooler --------------------------------------------------------------
ASH_RAMP = M.ramp("#4d4b48", "#5f5c58", "#726e69", "#86817b", "#9a948d", "#ada79f", "#bfb9b1", "#d0cac2")
FALLOUT_RAMP = M.ramp("#2e2d29", "#3b3933", "#4a473f", "#59564c", "#6a665a", "#7c7769", "#8e8979", "#a09b8a")
TRINITITE_RAMP = M.ramp("#0b170d", "#112614", "#19381b", "#234c23", "#30612d", "#40773a", "#548e4a", "#6da55e", "#8cbd77")
GLASSED_SAND_RAMP = M.ramp("#6b5e44", "#827351", "#998960", "#ae9d6f", "#c2b081", "#d3c294", "#e1d1a9", "#ecdebf")
SLAG_RAMP = M.ramp("#0f0e0f", "#181617", "#221f20", "#2d2928", "#393432", "#46403c", "#554d47", "#655b53")
SOIL_RAMP = M.ramp("#110d0a", "#1b1510", "#251d16", "#30261c", "#3c3023", "#4a3b2c", "#584736", "#675441")
STONE_RAMP = M.ramp("#242424", "#2f2f2e", "#3b3a39", "#474645", "#545351", "#615f5d", "#6e6c69", "#7c7975")
EARTH_RAMP = M.ramp("#291f16", "#34281c", "#413122", "#4e3b29", "#5b4530", "#685038", "#765c41", "#84694c")
CHAR_RAMP = M.ramp("#0a0908", "#12100e", "#1b1714", "#241f1b", "#2e2722", "#39302a", "#453a33", "#524640")
DRY_GRASS_RAMP = M.ramp("#3a3426", "#4a432f", "#5b5338", "#6d6443", "#80764f", "#93895d", "#a69b6c", "#b8ad7d")
DEAD_LEAF_RAMP = M.ramp("#2a2016", "#3a2c1d", "#4c3a25", "#5f4a2e", "#735a38", "#876b44", "#9a7d52")
STRAW_RAMP = M.ramp("#352e22", "#4a402e", "#5f533b", "#756749", "#8a7b58", "#9f8f68", "#b2a278")
SHEEN = (98, 94, 90, 255)              # the dull silver of char's lit edges
GLOW = ((128, 214, 74, 255), (184, 248, 116, 255))
HOT = (196, 206, 120, 255)


def on(ramp, k: int) -> float:
    """The tone of shade {k} of a ramp (right on it: noise then flickers round one shade, not between two)."""
    return k / (len(ramp) - 1)


def sites(seed: int, count: int) -> list[tuple[int, int]]:
    """{count} spots over a tile, from a seed (the same every run)."""
    rng = np.random.default_rng(seed)
    return [(int(x), int(y)) for x, y in rng.integers(0, N, size=(count, 2))]


def paint(ramp, shade: int, seed: int, extra=None, rough: bool = True, **kw) -> np.ndarray:
    """A material over a tile as shade indices on its ramp (so what is drawn over it stays on the ramp), its value
    raised or lowered by {extra} first (per piece, per texel)."""
    d = dict(mottle=0.05, grain=0.05, dither=0.8 if rough else 0.45, scale=0.5, despeckle=not rough)
    d.update(kw)
    mat = M.Material(ramp=ramp, tone=on(ramp, shade), extra=extra, **d)
    f = M.tile_face()
    return M.quantize(mat.value(f, seed), ramp, f, mat.dither, despeckle=mat.despeckle, scatter=rough)


def image(ramp, idx: np.ndarray) -> np.ndarray:
    return np.clip(M.colours(ramp, np.clip(idx, 0, len(ramp) - 1)), 0, 255).astype(np.uint8)


def colour(ramp, k: int) -> np.ndarray:
    return np.array([*ramp[k].astype(int), 255], dtype=np.uint8)


def cells(seed: int, count: int, stretch=(1.0, 1.0), cheb: bool = False):
    """A cell network that wraps round the tile: each texel's distance to its nearest and second-nearest site, which
    cell it is in, and where it lies from its cell's site (for each piece's rounded light)."""
    rng = np.random.default_rng(seed)
    pts = rng.random((count, 2)) * N
    yy, xx = np.mgrid[0:N, 0:N].astype(np.float32) + 0.5
    best = np.full((N, N), np.inf, dtype=np.float32)
    second = np.full((N, N), np.inf, dtype=np.float32)
    owner = np.zeros((N, N), dtype=np.int64)
    vx = np.zeros((N, N), dtype=np.float32)
    vy = np.zeros((N, N), dtype=np.float32)
    for i, (px, py) in enumerate(pts):
        for ox in (-N, 0, N):
            for oy in (-N, 0, N):
                dx, dy = (xx - px - ox) * stretch[0], (yy - py - oy) * stretch[1]
                d = np.maximum(np.abs(dx), np.abs(dy)) if cheb else np.hypot(dx, dy)
                closer = d < best
                second = np.where(closer, best, np.minimum(second, d))
                best = np.where(closer, d, best)
                owner = np.where(closer, i, owner)
                vx = np.where(closer, dx, vx)
                vy = np.where(closer, dy, vy)
    return best, second, owner, vx, vy


def dome(vx, vy, k: float, soft: float = 1.5) -> np.ndarray:
    """A piece's rounded light: brighter toward its top left, darker toward its bottom right."""
    return -k * (vx + vy) / (np.hypot(vx, vy) + soft)


def tones(seed: int, count: int, lo: float, hi: float) -> np.ndarray:
    return np.random.default_rng(seed).uniform(lo, hi, count).astype(np.float32)


def edges(label: np.ndarray):
    """The lit (top or left edge of a piece) and shaded (bottom or right edge) texels of a label map (-1: a seam),
    wrapping round the tile."""
    up = np.roll(label, 1, axis=0) != label
    left = np.roll(label, 1, axis=1) != label
    down = np.roll(label, -1, axis=0) != label
    right = np.roll(label, -1, axis=1) != label
    body = label >= 0
    return body & (up | left) & ~(down | right), body & (down | right) & ~(up | left)


def relief(seed: int, scale: float, k: float):
    """Soft drifts: a height field shaded as relief, lit from the top left (rising toward the bottom right faces it)."""
    def extra(f):
        h = M.noise(f, scale, seed, 2)
        return k * ((np.roll(h, -1, axis=1) - np.roll(h, 1, axis=1)) + (np.roll(h, -1, axis=0) - np.roll(h, 1, axis=0)))
    return extra


def at(img: np.ndarray, x: int, y: int, value) -> None:
    """One texel, wrapping round the tile's edges so a detail on its border carries on into the next block."""
    img[y % N, x % N] = value


def save_block(name: str, img: np.ndarray) -> None:
    M.to_tex(img).save(paths.TEXTURES / "block" / f"{name}.png")


# --- glass and molten ground ------------------------------------------------------------------------------------------------------
def trinitite() -> np.ndarray:
    d0, d1, owner, vx, vy = cells(11, 9)
    seam = (d1 - d0) < 0.7
    t = tones(12, 9, -0.12, 0.10)
    idx = paint(TRINITITE_RAMP, 4, 11, extra=lambda f: t[owner] + dome(vx, vy, 0.12), rough=False, mottle=0.03, grain=0.015)
    lit, shade = edges(np.where(seam, -1, owner))
    idx[lit] += 1
    idx[shade] -= 1
    idx[seam] = 1
    for x, y in sites(13, 4):                          # bubbles: a dark hollow, its far rim lit
        at(idx, x, y, 0)
        at(idx, x + 1, y + 1, 7)
    for x, y in sites(14, 2):                          # black inclusions
        at(idx, x, y, 0)
        at(idx, x + 1, y, 0)
    img = image(TRINITITE_RAMP, idx)
    sizes = np.bincount(owner[~seam].ravel(), minlength=9)
    for c in np.argsort(sizes)[-4:]:                   # a glint near the top left of each big nodule
        ys, xs = np.nonzero((owner == c) & ~seam)
        k = int(np.argmin(xs + ys + 0.6 * np.abs(xs - ys)))
        at(img, xs[k] + 1, ys[k] + 1, (204, 232, 176, 255))
        at(img, xs[k] + 2, ys[k] + 1, colour(TRINITITE_RAMP, 8))
    return img


def glassed_sand() -> np.ndarray:
    grit = paint(GLASSED_SAND_RAMP, 3, 21, rough=True, mottle=0.05, grain=0.09, dither=0.9)
    d0, d1, owner, vx, vy = cells(22, 6)
    seam = (d1 - d0) < 0.5
    t = tones(23, 6, -0.06, 0.06)
    glaze = paint(GLASSED_SAND_RAMP, 5, 24, extra=lambda f: t[owner] + dome(vx, vy, 0.06, 2.5), rough=False, mottle=0.03, grain=0.012)
    bare = (M.noise(M.tile_face(), 4, 25, 2) > 0.66) & ~seam    # the glaze flaked off to the grit
    idx = np.where(bare, grit, glaze)
    lit, shade = edges(np.where(seam | bare, -1, owner))
    idx[lit] += 1
    idx[shade] -= 1
    idx[seam] = 2
    img = image(GLASSED_SAND_RAMP, idx)
    for x, y in sites(26, 3):                          # the glaze's shine
        at(img, x, y, (248, 240, 216, 255))
    return img


def slag() -> np.ndarray:
    d0, d1, owner, vx, vy = cells(31, 10)
    seam = (d1 - d0) < 0.8
    t = tones(32, 10, -0.1, 0.08)
    idx = paint(SLAG_RAMP, 3, 31, extra=lambda f: t[owner] + dome(vx, vy, 0.14), rough=True, mottle=0.04, grain=0.05, dither=0.6)
    lit, shade = edges(np.where(seam, -1, owner))
    idx[lit] += 1
    idx[shade] -= 1
    idx[seam] = 0
    pits = sites(33, 7)
    for x, y in pits:                                  # vesicles: a hollow, its far rim catching the light
        at(idx, x, y, 0)
        at(idx, x + 1, y, 1)
        at(idx, x + 1, y + 1, 6)
    img = image(SLAG_RAMP, idx)
    for x, y in pits[:3]:                              # rust running down from a few (the library's rust colours)
        at(img, x, y + 1, np.array([*M.RUST_CORE.astype(int), 255], dtype=np.uint8))
        at(img, x, y + 2, np.array([*M.RUST_MID.astype(int), 255], dtype=np.uint8))
        at(img, x + 1, y + 2, np.array([*(M.RUST_MID * 0.85).astype(int), 255], dtype=np.uint8))
    return img


# --- burnt ground -----------------------------------------------------------------------------------------------------------------
def scorched_earth() -> np.ndarray:
    d0, d1, owner, vx, vy = cells(41, 8)
    crack = (d1 - d0) < 1.0
    t = tones(42, 8, -0.1, 0.08)
    idx = paint(SOIL_RAMP, 3, 41, extra=lambda f: t[owner] + dome(vx, vy, 0.08, 2.5), rough=True, mottle=0.05, grain=0.07, dither=0.85)
    lit, shade = edges(np.where(crack, -1, owner))
    idx[lit] += 1
    idx[shade] -= 1
    idx[crack] = 0
    bits = sites(43, 5)
    for x, y in bits:                                  # bits of charcoal
        at(idx, x, y, 0)
        at(idx, x + 1, y, 0)
    img = image(SOIL_RAMP, idx)
    for x, y in bits:                                  # a dull sheen on their tops
        at(img, x, y - 1, SHEEN)
    ash_in = crack & (M.noise(M.tile_face(), 3, 44, 2) > 0.62)    # ash blown into the cracks, dim in their shadow
    img[ash_in] = colour(ASH_RAMP, 1)
    return img


def scorched_stone() -> np.ndarray:
    def soot(f):
        return -0.22 * np.clip((M.noise(f, 3, 52, 2) - 0.42) / 0.3, 0, 1)

    d0, d1, owner, vx, vy = cells(53, 5)
    fissure = ((d1 - d0) < 0.6) & (M.noise(M.tile_face(), 3, 54) > 0.4)
    flake = owner == 2                                  # one flake spalled off clean: fresh, unsooted rock
    idx = paint(STONE_RAMP, 4, 51, extra=lambda f: np.where(flake, 0.1, soot(f)), rough=True, mottle=0.06, grain=0.06, dither=0.8)
    lit, shade = edges(np.where(fissure | ~flake, -1, owner))
    idx[lit] += 1
    idx[shade] -= 1
    idx[fissure] -= 2
    lip = np.roll(fissure, 1, axis=0) & ~fissure       # the lip under a fissure catches the light
    idx[lip] += 1
    return image(STONE_RAMP, idx)


def irradiated_soil() -> tuple[np.ndarray, np.ndarray]:
    d0, d1, owner, vx, vy = cells(61, 14)
    gap = (d1 - d0) < 0.8
    t = tones(62, 14, -0.1, 0.1)
    idx = paint(EARTH_RAMP, 3, 61, extra=lambda f: t[owner] + dome(vx, vy, 0.1), rough=True, mottle=0.05, grain=0.06, dither=0.85)
    lit, shade = edges(np.where(gap, -1, owner))
    idx[lit] += 1
    idx[shade] -= 1
    idx[gap] = 0
    img = image(EARTH_RAMP, idx)
    glint = np.zeros((N, N, 4), dtype=np.uint8)
    ys, xs = np.nonzero(gap)
    rng = np.random.default_rng(63)
    for i, k in enumerate(rng.choice(len(xs), size=7, replace=False)):     # specks glowing in the gaps
        c = GLOW[1] if i % 3 == 0 else GLOW[0]
        img[ys[k], xs[k]] = c
        glint[ys[k], xs[k]] = c
    return img, glint


def ash() -> np.ndarray:
    idx = paint(ASH_RAMP, 5, 71, extra=relief(72, 4, 0.11), rough=True, mottle=0.03, grain=0.04, dither=0.7)
    for x, y in sites(73, 4):                          # charcoal crumbs, each with its little shadow
        at(idx, x, y, 1)
        at(idx, x + 1, y + 1, 3)
    return image(ASH_RAMP, idx)


def fallout_ash() -> np.ndarray:
    idx = paint(FALLOUT_RAMP, 4, 81, extra=relief(82, 4, 0.13), rough=True, mottle=0.05, grain=0.06, dither=0.8)
    for x, y in sites(83, 7):                          # grit
        at(idx, x, y, 1)
    img = image(FALLOUT_RAMP, idx)
    for x, y in ((3, 5), (11, 12)):                    # hot particles, few and far apart
        at(img, x, y, HOT)
    return img


# --- burnt wood ---------------------------------------------------------------------------------------------------------------------
def char_blocks(seed: int, width=(2, 4), height=(2, 5)) -> np.ndarray:
    """Alligatored char: blocks in staggered columns up the wood, a crack under and beside each (label -1)."""
    rng = np.random.default_rng(seed)
    label = np.full((N, N), -1, dtype=np.int64)
    columns, x = [], 0
    while x < N:
        w = int(rng.integers(width[0], width[1] + 1))
        if N - x - w < width[0]:
            w = N - x
        columns.append((x, w))
        x += w
    n = 0
    for x0, w in columns:
        y = int(rng.integers(0, 4))
        while y < N + 4:
            h = int(rng.integers(height[0], height[1] + 1))
            for yy in range(y, y + h - 1):
                for xx in range(x0, x0 + w - 1):
                    label[yy % N, xx % N] = n
            n += 1
            y += h
    return label


def charred(label: np.ndarray, seed: int, shade: int = 3, sheen: float = 0.22, vary: float = 0.12) -> np.ndarray:
    """Char blocks: each its own tone, lit along its top and left with a dull sheen now and then, shaded along its bottom
    and right, deep black cracks between."""
    t = np.concatenate([tones(seed, int(label.max()) + 1, -vary, vary), [0.0]]).astype(np.float32)
    idx = paint(CHAR_RAMP, shade, seed, extra=lambda f: t[label], rough=True, mottle=0.04, grain=0.04, dither=0.6)
    crack = label < 0
    lit, shaded = edges(label)
    idx[lit] += 1
    idx[shaded] -= 1
    idx[crack] = 0
    img = image(CHAR_RAMP, idx)
    rng = np.random.default_rng(seed + 1)
    shine = lit & (np.roll(label, 1, axis=0) != label) & (rng.random((N, N)) < sheen)
    img[shine] = SHEEN
    return img


def charred_log_side() -> np.ndarray:
    return charred(char_blocks(91, width=(2, 4), height=(2, 5)), 92)


def charred_log_top() -> np.ndarray:
    yy, xx = np.mgrid[0:N, 0:N].astype(np.float32) + 0.5
    r = np.maximum(np.abs(xx - 8), np.abs(yy - 8)) * 0.55 + np.hypot(xx - 8, yy - 8) * 0.45    # squarish rings, as vanilla's
    inside = r < 6.7
    ring = 0.09 * np.sign(np.sin(r * 2.1))
    idx = paint(CHAR_RAMP, 3, 95, extra=lambda f: np.where(inside, ring, 0), rough=True, mottle=0.03, grain=0.05, dither=0.6)
    a = np.degrees(np.arctan2(yy - 8, xx - 8))
    for angle in (-120, 15, 100):                      # radial checks where the wood split as it burned
        crack = (np.abs(((a - angle + 180) % 360) - 180) < 8) & (r > 2.0) & inside
        idx[crack] = 0
        idx[np.roll(crack, 1, axis=1) & ~crack & inside] += 1
    idx[r < 1.2] = 1                                   # the pith
    idx[inside & (r >= 6.0)] -= 1                      # the wood's edge in the bark's shadow
    img = image(CHAR_RAMP, idx)
    bark = charred(char_blocks(96, width=(2, 3), height=(2, 3)), 97, shade=2, sheen=0.1)
    img[~inside] = bark[~inside]
    return img


def charred_planks() -> np.ndarray:
    """Four long boards charred black: dark seams between them, each board whole from its end round to its end (the
    ends staggered), its grain along it, short checks partway across it where the char crazed, a nail at each end."""
    label = np.full((N, N), -1, dtype=np.int64)
    ends = (11, 4, 13, 7)
    for b in range(4):
        for xx in range(N):
            if xx != ends[b]:
                label[4 * b:4 * b + 3, xx] = b
    idx = paint(CHAR_RAMP, 4, 102, extra=lambda f: np.array([0.0, -0.06, 0.05, -0.03], dtype=np.float32)[np.maximum(label, 0)],
                rough=True, mottle=0.03, grain=0.04, dither=0.6, brushed=0.15)
    lit, shaded = edges(label)
    idx[lit] += 1
    idx[shaded] -= 1
    idx[label < 0] = 0
    rng = np.random.default_rng(103)
    for b in range(4):                                 # checks: partway across the board, never right through it
        for _ in range(2):
            x, y = int(rng.integers(0, N)), 4 * b + int(rng.integers(0, 2))
            if x not in (ends[b], ends[b] + 1, (ends[b] - 1) % N):
                at(idx, x, y, 1)
                at(idx, x, y + 1, 1)
    img = image(CHAR_RAMP, idx)
    top = (np.arange(N)[:, None] % 4 == 0) & (label >= 0)
    img[top & (rng.random((N, N)) < 0.3)] = SHEEN       # a dull sheen along each board's lit edge
    for b in range(4):                                 # a nail by each board's end
        at(img, ends[b] + 2, 4 * b + 1, colour(CHAR_RAMP, 0))
        at(img, ends[b] + 1, 4 * b, SHEEN)
    return img


# --- dead plants ---------------------------------------------------------------------------------------------------------------------
def dry_earth(seed: int) -> np.ndarray:
    """Dry earth in clods (shade indices): the dead grass block's sides and bottom."""
    d0, d1, owner, vx, vy = cells(seed, 14)
    gap = (d1 - d0) < 0.7
    t = tones(seed + 1, 14, -0.08, 0.08)
    idx = paint(EARTH_RAMP, 4, seed, extra=lambda f: t[owner] + dome(vx, vy, 0.08), rough=True, mottle=0.05, grain=0.06, dither=0.85)
    lit, shade = edges(np.where(gap, -1, owner))
    idx[lit] += 1
    idx[shade] -= 1
    idx[gap] = 1
    return idx


def dead_grass_top() -> np.ndarray:
    idx = paint(DRY_GRASS_RAMP, 4, 111, rough=True, mottle=0.06, grain=0.05, dither=0.8)
    for x, y in sites(112, 26):                        # blades: a lit tip, a shaded foot
        at(idx, x, y, min(7, int(idx[y % N, x % N]) + 2))
        at(idx, x, y + 1, max(0, int(idx[(y + 1) % N, x % N]) - 1))
    img = image(DRY_GRASS_RAMP, idx)
    earth = image(EARTH_RAMP, dry_earth(113))
    for x, y in sites(114, 7):                         # bare earth between the tufts
        img[y % N, x % N] = earth[y % N, x % N]
    return img


def dead_grass_side() -> np.ndarray:
    idx = dry_earth(121)
    img = image(EARTH_RAMP, idx)
    grass = image(DRY_GRASS_RAMP, paint(DRY_GRASS_RAMP, 4, 122, rough=True, mottle=0.05, grain=0.05))
    rng = np.random.default_rng(123)
    for x in range(N):                                 # the turf's fringe over the earth, ragged, its tip lit
        depth = 2 + int(rng.integers(0, 3))
        img[:depth, x] = grass[:depth, x]
        img[0, x] = colour(DRY_GRASS_RAMP, 6)
        img[depth - 1, x] = colour(DRY_GRASS_RAMP, 2)
        img[depth, x] = colour(EARTH_RAMP, max(0, int(idx[depth, x]) - 2))
    return img


def dead_leaves() -> np.ndarray:
    f = M.tile_face()
    cover = M.noise(f, 5, 131, 2) + 0.25 * (M.white(f.X, f.Y, f.Z, 132) - 0.5) > 0.44
    label = np.where(cover, 0, -1)
    idx = paint(DEAD_LEAF_RAMP, 3, 133, rough=True, mottle=0.08, grain=0.08, dither=0.9)
    lit, shade = edges(label)
    idx[lit] += 1
    idx[shade] -= 1
    for x, y in sites(134, 6):                         # a few leaves catching the light
        if cover[y % N, x % N]:
            at(idx, x, y, 5)
    img = image(DEAD_LEAF_RAMP, idx)
    img[~cover] = 0
    return img


def withered_crops() -> np.ndarray:
    """Dead stalks, a kink or a break in some, the heads hanging over; transparent between (a crop's cross planes)."""
    img = np.zeros((N, N, 4), dtype=np.uint8)
    stalks = [(1, 11, 7, -1), (4, 13, 5, 1), (6, 8, None, 0), (9, 12, 6, 1), (12, 10, 4, -1), (14, 13, 8, 1)]
    for x, height, kink, lean in stalks:
        top = N - height
        for y in range(N - 1, top - 1, -1):
            xx = x + (lean if kink is not None and y < N - kink else 0)
            img[y, xx % N] = colour(STRAW_RAMP, 3 if y % 3 else 2)
            if xx - 1 >= 0 and img[y, xx - 1, 3] == 0 and y > top + 1:
                img[y, xx - 1] = colour(STRAW_RAMP, 1)
        hx = x + (lean if kink is not None else 0)
        if lean:                                       # the head bowed over to one side, hanging
            for dy, dx, k in ((0, lean, 5), (1, lean, 4), (1, 2 * lean, 4), (2, 2 * lean, 3), (3, 2 * lean, 2)):
                if 0 <= hx + dx < N:
                    img[top + dy, hx + dx] = colour(STRAW_RAMP, k)
        else:                                          # broken off: a lit splinter at the top
            img[top, hx] = colour(STRAW_RAMP, 5)
    return img


# --- the ash particle ---------------------------------------------------------------------------------------------------------------
FLAKES = [
    ["..##....", ".####...", ".###....", "..#....."],
    [".#......", "###.....", ".##.....", "........"],
    ["###.....", ".###....", "..#....."],
    [".##.....", "##......"],
]


def flake(i: int) -> Tex:
    t = Tex(8, 8)
    rows = FLAKES[i]
    y0 = (8 - len(rows)) // 2
    for y, line in enumerate(rows):
        for x, ch in enumerate(line):
            if ch == "#":
                lit = y == 0 or rows[y - 1][x] != "#"
                t.px(x + 2, y + y0, (232, 230, 225, 255) if lit else (196, 193, 186, 255))
    return t


# --- models ---------------------------------------------------------------------------------------------------------------------------
def block_id(name: str) -> str:
    return f"groundzero:block/{name}"


def cube_all(name: str, cutout: bool = False) -> None:
    model = {"parent": "minecraft:block/cube_all", "textures": {"all": block_id(name)}}
    if cutout:
        model["render_type"] = "minecraft:cutout"
    write_json(paths.MODELS / "block" / f"{name}.json", model)
    simple_state(name)
    item_def(name, block_id(name))


def simple_state(name: str, model: str | None = None) -> None:
    write_json(paths.ASSETS / "blockstates" / f"{name}.json", {"variants": {"": {"model": model or block_id(name)}}})


def item_def(name: str, model: str) -> None:
    write_json(paths.ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": model}})


def glowing_soil() -> None:
    """Irradiated Soil: the earth, and its specks again on an emissive layer over it (as the ores' glints)."""
    elements = []
    for tex, light in (("#all", 0), ("#glint", 10)):
        el = {"from": [0, 0, 0], "to": [16, 16, 16], "faces": {d: {"uv": [0, 0, 16, 16], "texture": tex, "cullface": d} for d in DIRS}}
        if light:
            el["light_emission"] = light
        elements.append(el)
    write_json(paths.MODELS / "block" / "irradiated_soil.json", {
        "parent": "minecraft:block/block", "render_type": "minecraft:cutout",
        "textures": {"particle": block_id("irradiated_soil"), "all": block_id("irradiated_soil"), "glint": block_id("irradiated_soil_glint")},
        "elements": elements})
    simple_state("irradiated_soil")
    item_def("irradiated_soil", block_id("irradiated_soil"))


def pillar(name: str, side: str, end: str) -> None:
    write_json(paths.MODELS / "block" / f"{name}.json", {"parent": "minecraft:block/cube_column",
                                                         "textures": {"end": block_id(end), "side": block_id(side)}})
    write_json(paths.MODELS / "block" / f"{name}_horizontal.json", {"parent": "minecraft:block/cube_column_horizontal",
                                                                    "textures": {"end": block_id(end), "side": block_id(side)}})
    write_json(paths.ASSETS / "blockstates" / f"{name}.json", {"variants": {
        "axis=x": {"model": block_id(f"{name}_horizontal"), "x": 90, "y": 90},
        "axis=y": {"model": block_id(name)},
        "axis=z": {"model": block_id(f"{name}_horizontal"), "x": 90}}})
    item_def(name, block_id(name))


def layers(name: str) -> None:
    """Up to eight layers, as snow: two pixels each, the eighth a whole block."""
    variants = {}
    for n in range(1, 9):
        h = 2 * n
        if n < 8:
            model = f"{name}_height{h}"
            side = {"uv": [0, 16 - h, 16, 16], "texture": "#texture"}
            write_json(paths.MODELS / "block" / f"{model}.json", {
                "parent": "minecraft:block/thin_block", "textures": {"particle": block_id(name), "texture": block_id(name)},
                "elements": [{"from": [0, 0, 0], "to": [16, h, 16], "faces": {
                    "down": {"uv": [0, 0, 16, 16], "texture": "#texture", "cullface": "down"},
                    "up": {"uv": [0, 0, 16, 16], "texture": "#texture"},
                    **{d: {**side, "cullface": d} for d in ("north", "south", "west", "east")}}}]})
        else:
            model = f"{name}_block"
            write_json(paths.MODELS / "block" / f"{model}.json", {"parent": "minecraft:block/cube_all", "textures": {"all": block_id(name)}})
        variants[f"layers={n}"] = {"model": block_id(model)}
    write_json(paths.ASSETS / "blockstates" / f"{name}.json", {"variants": variants})
    item_def(name, block_id(f"{name}_height2"))


def dead_plants() -> None:
    write_json(paths.MODELS / "block" / "dead_grass_block.json", {"parent": "minecraft:block/cube_bottom_top", "textures": {
        "top": block_id("dead_grass_block_top"), "side": block_id("dead_grass_block_side"), "bottom": block_id("dry_earth")}})
    simple_state("dead_grass_block")
    item_def("dead_grass_block", block_id("dead_grass_block"))
    cube_all("dead_leaves", cutout=True)
    planes = []
    for axis, pos, (a, b) in (("x", 4, ("west", "east")), ("x", 12, ("west", "east")), ("z", 4, ("north", "south")), ("z", 12, ("north", "south"))):
        frm = [pos, -1, 0] if axis == "x" else [0, -1, pos]
        to = [pos, 15, 16] if axis == "x" else [16, 15, pos]
        flip = pos == 12
        planes.append({"from": frm, "to": to, "shade_direction_override": "up", "faces": {
            a: {"uv": [16, 0, 0, 16] if flip else [0, 0, 16, 16], "texture": "#crop"},
            b: {"uv": [0, 0, 16, 16] if flip else [16, 0, 0, 16], "texture": "#crop"}}})
    write_json(paths.MODELS / "block" / "withered_crops.json", {
        "ambientocclusion": False, "render_type": "minecraft:cutout",
        "textures": {"particle": "#crop", "crop": block_id("withered_crops")}, "elements": planes})
    simple_state("withered_crops")
    write_json(paths.MODELS / "item" / "withered_crops.json", {"parent": "minecraft:item/generated", "textures": {"layer0": block_id("withered_crops")}})
    item_def("withered_crops", "groundzero:item/withered_crops")


def main() -> None:
    save_block("trinitite", trinitite())
    save_block("glassed_sand", glassed_sand())
    save_block("slag", slag())
    save_block("scorched_earth", scorched_earth())
    save_block("scorched_stone", scorched_stone())
    soil, glint = irradiated_soil()
    save_block("irradiated_soil", soil)
    save_block("irradiated_soil_glint", glint)
    save_block("ash", ash())
    save_block("fallout_ash", fallout_ash())
    save_block("charred_log", charred_log_side())
    save_block("charred_log_top", charred_log_top())
    save_block("charred_planks", charred_planks())
    save_block("dead_grass_block_top", dead_grass_top())
    save_block("dead_grass_block_side", dead_grass_side())
    save_block("dry_earth", image(EARTH_RAMP, dry_earth(141)))
    save_block("dead_leaves", dead_leaves())
    save_block("withered_crops", withered_crops())
    for name in ("trinitite", "glassed_sand", "slag", "scorched_earth", "scorched_stone", "charred_planks"):
        cube_all(name)
    glowing_soil()
    pillar("charred_log", "charred_log", "charred_log_top")
    pillar("charred_wood", "charred_log", "charred_log")
    layers("ash")
    layers("fallout_ash")
    dead_plants()
    for i in range(len(FLAKES)):
        flake(i).save(paths.TEXTURES / "particle" / f"fallout_ash_{i}.png")
    write_json(paths.ASSETS / "particles" / "fallout_ash.json", {"textures": [f"groundzero:fallout_ash_{i}" for i in range(len(FLAKES))]})
    print("aftermath assets written")


if __name__ == "__main__":
    main()
