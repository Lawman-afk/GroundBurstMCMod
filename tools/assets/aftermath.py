"""The aftermath on the ground (DESIGN.md §21.7, §21.8 slice 12b): what a nuclear blast leaves of the land, what a strong
radiation field kills, and the ash a fallout plume lays.

Painted to the texture remaster's rules (DESIGN.md §4.3, the user's request after the first look at them): each block a
designed surface, not noise with dots. The material library's tiles (`gb/materials.py`: world-space noise that wraps
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
- Irradiated Soil: vanilla's own dirt, at the user's request, with the irradiation laid over it: sickly green specks
  glowing in its darkest gaps (an emissive layer, as the ores'), the earth round them stained olive shade for shade.
  The two layers are fitted to vanilla's dirt, read from the decompiled client's assets (`paths.VANILLA_ASSETS`, built
  by Gradle); only our layers ship.
- Ash and Fallout Ash: powder lying in soft drifts (shaded as relief), charcoal crumbs; fallout greyer, gritty, with a
  few hot specks.
- Charred Log, Wood and Planks: wood burnt to blocks of char with deep cracks and a dull silver sheen on their lit edges;
  the log's end in charred rings with radial checks; the planks four boards, each crazed along its length.
- Dead Grass Block: vanilla's grass block through a dead tint (the tint source is in the client, `FalloutClient`; the
  item's tint is the same colour), as first built: the user liked it better than the repaint.
- Dead Leaves, Withered Crops: brown curled leaves with gaps, dead stalks with drooping heads.
- Slice 12e, on vanilla's own blocks the way the user likes them: Frosted Stone (vanilla's stone, hoarfrost feathered
  over it in drifts fitted to its texture), Frosted Grass Block (vanilla's grass through a pale icy tint), Frozen Plant
  (vanilla's short grass through an icy tint), Toxic Soil (vanilla's dirt, stained sickly yellow-green and bruise
  purple with spore specks, fitted to it). Cryo Crystal is ours: a cluster of pale blue ice needles, each lit down one
  side, a cold glint at the tips.

    tools/.venv/Scripts/python.exe tools/assets/aftermath.py
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import numpy as np  # noqa: E402
from PIL import Image  # noqa: E402

from gb import materials as M  # noqa: E402
from gb import paths  # noqa: E402
from gb.model import write_json  # noqa: E402
from gb.tex import Tex  # noqa: E402

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
CHAR_RAMP = M.ramp("#0a0908", "#12100e", "#1b1714", "#241f1b", "#2e2722", "#39302a", "#453a33", "#524640")
DEAD_LEAF_RAMP = M.ramp("#2a2016", "#3a2c1d", "#4c3a25", "#5f4a2e", "#735a38", "#876b44", "#9a7d52")
STRAW_RAMP = M.ramp("#352e22", "#4a402e", "#5f533b", "#756749", "#8a7b58", "#9f8f68", "#b2a278")
SHEEN = (98, 94, 90, 255)              # the dull silver of char's lit edges
GLOW = ((128, 214, 74, 255), (184, 248, 116, 255))
#: The stain round a glowing speck: muted olive shades a dirt shade goes to, by brightness (darkest first; each a little
#: darker than the dirt it stains, so the stain reads as soured earth, not as green bits).
STAIN = ((48, 52, 30), (62, 70, 38), (80, 90, 48), (100, 110, 60), (122, 132, 74))
#: The Dead Grass Block's tint (the client's, FalloutClient.DEAD_GRASS: dry and straw-grey).
DEAD_GRASS = 0xFF9C8F62
#: Frost on grass and a frozen plant (the client's FalloutClient.FROSTED_GRASS and FROZEN_PLANT).
FROSTED_GRASS, FROZEN_PLANT_TINT = 0xFFB4D0D6, 0xFFA8CFE2
#: Hoarfrost, darkest to brightest: blue-grey shade, pale ice, white.
RIME = ((150, 176, 190), (186, 208, 220), (214, 230, 238), (238, 246, 250))
#: Anthrax's stain on soil: sickly yellow-greens and a bruised purple-brown, by brightness (darkest first).
TOXIC = ((56, 54, 30), (80, 80, 40), (106, 104, 52), (132, 128, 68))
BRUISE = ((44, 30, 40), (66, 46, 58))
#: Cryo crystal: deep ice blue to white.
ICE = ((40, 92, 128), (70, 138, 176), (112, 184, 214), (160, 218, 236), (214, 242, 250), (248, 254, 255))
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


def luma(rgb: np.ndarray) -> np.ndarray:
    return rgb[..., 0] * 0.299 + rgb[..., 1] * 0.587 + rgb[..., 2] * 0.114


def irradiated_soil() -> tuple[np.ndarray, np.ndarray]:
    """The two layers laid over vanilla's dirt: the stain (round each speck, the earth gone olive, each texel to the
    olive of its own brightness, so the dirt's relief stays) and the glint (the specks, glowing in the darkest gaps,
    far enough apart round the wrapping tile that no two clump)."""
    path = paths.VANILLA_ASSETS / "textures" / "block" / "dirt.png"
    if not path.exists():
        raise SystemExit(f"{path} is missing: build the mod once (Gradle unpacks vanilla's assets there)")
    dirt = np.array(Image.open(path).convert("RGBA")).astype(np.int32)
    rgb = dirt[..., :3]
    grey = np.abs(rgb[..., 0] - rgb[..., 2]) < 10                  # the pebbles: left as they are
    lum = luma(rgb.astype(np.float32))
    gaps = (~grey) & (lum <= np.sort(lum[~grey])[int(0.12 * np.count_nonzero(~grey))])
    rng = np.random.default_rng(64)
    specks: list[tuple[int, int]] = []
    for k in rng.permutation(np.count_nonzero(gaps)):
        y, x = np.argwhere(gaps)[k]
        if all(min(abs(x - sx), N - abs(x - sx)) ** 2 + min(abs(y - sy), N - abs(y - sy)) ** 2 >= 13 for sx, sy in specks):
            specks.append((int(x), int(y)))
        if len(specks) == 6:
            break
    stain = np.zeros((N, N, 4), dtype=np.uint8)
    glint = np.zeros((N, N, 4), dtype=np.uint8)
    levels = luma(np.array(STAIN, dtype=np.float32))
    for i, (x, y) in enumerate(specks):
        glint[y, x] = GLOW[1] if i % 3 == 0 else GLOW[0]
        for dx, dy in ((1, 0), (0, 1), (-1, 0), (0, -1))[: 2 + i % 2]:
            nx, ny = (x + dx) % N, (y + dy) % N
            if grey[ny, nx] or (nx, ny) in specks:
                continue
            olive = STAIN[int(np.argmin(np.abs(levels - (lum[ny, nx] - 8))))]
            stain[ny, nx] = (*olive, 255)
    return stain, glint


def vanilla(name: str) -> np.ndarray:
    """A vanilla block texture as int RGBA (read from the decompiled client's assets to fit our layers; never shipped)."""
    path = paths.VANILLA_ASSETS / "textures" / "block" / f"{name}.png"
    if not path.exists():
        raise SystemExit(f"{path} is missing: build the mod once (Gradle unpacks vanilla's assets there)")
    return np.array(Image.open(path).convert("RGBA")).astype(np.int32)


def rime_over_stone() -> np.ndarray:
    """Hoarfrost over vanilla's stone: feathery drifts (noise, thresholded) covering about two fifths of the face, thicker
    where the stone is lighter (its raised parts); in each drift the lit top-left texels whitest, its feathered edge
    the blue-grey shade. A layer: clear where the stone shows."""
    stone = vanilla("stone")
    lum = luma(stone[..., :3].astype(np.float32))
    f = M.tile_face()
    field = M.noise(f, 2.2, 151, 2) + 0.12 * (lum - lum.mean()) / (lum.std() + 1e-6) + 0.3 * (M.white(f.X, f.Y, f.Z, 152) - 0.5)
    cover = field > np.quantile(field, 0.7)
    out = np.zeros((N, N, 4), dtype=np.uint8)
    lit, shade = edges(np.where(cover, 0, -1))
    for y in range(N):
        for x in range(N):
            if not cover[y, x]:
                continue
            k = 2 if lum[y, x] > lum.mean() else 1
            if lit[y, x]:
                k = 3
            elif shade[y, x]:
                k = 0
            out[y, x] = (*RIME[k], 255)
    return out


def toxic_stain() -> np.ndarray:
    """Anthrax's stain over vanilla's dirt: blotches of sickly yellow-green (each texel to the green of its own
    brightness, so the dirt's clods still show), a few bruise-purple seeps in its darkest gaps, and pale spore specks.
    A layer: clear where the dirt shows."""
    dirt = vanilla("dirt")
    rgb = dirt[..., :3]
    grey = np.abs(rgb[..., 0] - rgb[..., 2]) < 10
    lum = luma(rgb.astype(np.float32))
    f = M.tile_face()
    field = M.noise(f, 3, 161, 2) + 0.25 * (M.white(f.X, f.Y, f.Z, 162) - 0.5)
    blot = (field > np.quantile(field, 0.66)) & ~grey
    out = np.zeros((N, N, 4), dtype=np.uint8)
    levels = luma(np.array(TOXIC, dtype=np.float32))
    for y in range(N):
        for x in range(N):
            if blot[y, x]:
                out[y, x] = (*TOXIC[int(np.argmin(np.abs(levels - (lum[y, x] - 4))))], 255)
    dark = (~grey) & (lum <= np.sort(lum[~grey])[int(0.15 * np.count_nonzero(~grey))])
    rng = np.random.default_rng(163)
    ys, xs = np.nonzero(dark)
    for i, k in enumerate(rng.choice(len(xs), size=min(9, len(xs)), replace=False)):
        out[ys[k], xs[k]] = (*BRUISE[i % 2], 255)
    for x, y in ((3, 4), (11, 2), (6, 12), (13, 10)):        # spores, few and far apart
        out[y, x] = (196, 214, 120, 255)
    return out


def cryo_crystal() -> np.ndarray:
    """A cluster of ice needles from a common foot (a cross plane): five blades of differing height and lean, each lit
    down its left side and shaded down its right, its tip a cold white glint; clear between them."""
    img = np.zeros((N, N, 4), dtype=np.uint8)
    blades = [(7.5, 14, 1.6, 0.0), (4.5, 10, 1.2, -0.28), (10.5, 11, 1.3, 0.3), (2.5, 6, 0.9, -0.45), (13.0, 7, 0.9, 0.5)]
    for foot, height, width, lean in blades:
        for i in range(int(height)):
            y = N - 1 - i
            t = i / height
            cx = foot + lean * i
            half = max(0.5, width * (1 - t * 0.85))
            for x in range(N):
                d = (x + 0.5 - cx) / half
                if abs(d) > 1:
                    continue
                k = 4 if d < -0.35 else (1 if d > 0.4 else 2 + (i % 3 == 0))
                if i >= height - 2:
                    k = 5 if i == int(height) - 1 else 4
                img[y, x] = (*ICE[k], 255)
    return img


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
    return f"groundburst:block/{name}"


def cube_all(name: str, cutout: bool = False) -> None:
    model = {"parent": "minecraft:block/cube_all", "textures": {"all": block_id(name)}}
    if cutout:
        model["render_type"] = "minecraft:cutout"
    write_json(paths.MODELS / "block" / f"{name}.json", model)
    simple_state(name)
    item_def(name, block_id(name))


def simple_state(name: str, model: str | None = None) -> None:
    write_json(paths.ASSETS / "blockstates" / f"{name}.json", {"variants": {"": {"model": model or block_id(name)}}})


def item_def(name: str, model: str, tint: int | None = None) -> None:
    m = {"type": "minecraft:model", "model": model}
    if tint is not None:
        m["tints"] = [{"type": "minecraft:constant", "value": tint - (1 << 32) if tint >= 1 << 31 else tint}]
    write_json(paths.ASSETS / "items" / f"{name}.json", {"model": m})


def glowing_soil() -> None:
    """Irradiated Soil: vanilla's dirt, the stain over it, and the specks on an emissive layer over that (as the ores'
    glints: each layer drawn after the one under it, in the same place)."""
    elements = []
    for tex, light in (("#dirt", 0), ("#stain", 0), ("#glint", 10)):
        el = {"from": [0, 0, 0], "to": [16, 16, 16], "faces": {d: {"uv": [0, 0, 16, 16], "texture": tex, "cullface": d} for d in DIRS}}
        if light:
            el["light_emission"] = light
        elements.append(el)
    write_json(paths.MODELS / "block" / "irradiated_soil.json", {
        "parent": "minecraft:block/block", "render_type": "minecraft:cutout",
        "textures": {"particle": "minecraft:block/dirt", "dirt": "minecraft:block/dirt", "stain": block_id("irradiated_soil_stain"),
                     "glint": block_id("irradiated_soil_glint")},
        "elements": elements})
    simple_state("irradiated_soil")
    item_def("irradiated_soil", block_id("irradiated_soil"))


def overlaid(name: str, base: str, layers: list[tuple[str, int]], particle: str | None = None) -> None:
    """A block drawn as vanilla's {base} with our layers over it, each drawn after the one under it in the same place
    (as the ores' glints): (texture, light emission)."""
    elements, textures = [], {"particle": particle or base, "base": base}
    for i, (tex, light) in enumerate([("#base", 0)] + [(f"#layer{j}", li) for j, (_, li) in enumerate(layers)]):
        el = {"from": [0, 0, 0], "to": [16, 16, 16], "faces": {d: {"uv": [0, 0, 16, 16], "texture": tex, "cullface": d} for d in DIRS}}
        if light:
            el["light_emission"] = light
        elements.append(el)
    for j, (tex, _) in enumerate(layers):
        textures[f"layer{j}"] = block_id(tex)
    write_json(paths.MODELS / "block" / f"{name}.json", {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
                                                          "textures": textures, "elements": elements})
    simple_state(name)
    item_def(name, block_id(name))


def frozen_ground() -> None:
    """Slice 12e's frozen ground and toxic soil: models, states and items (their textures saved in main)."""
    overlaid("frosted_stone", "minecraft:block/stone", [("frosted_stone_rime", 0)])
    overlaid("toxic_soil", "minecraft:block/dirt", [("toxic_soil_stain", 0)])
    write_json(paths.MODELS / "block" / "frosted_grass_block.json", {"parent": "minecraft:block/grass_block"})
    simple_state("frosted_grass_block")
    item_def("frosted_grass_block", block_id("frosted_grass_block"), FROSTED_GRASS)
    write_json(paths.MODELS / "block" / "frozen_plant.json", {"parent": "minecraft:block/tinted_cross", "render_type": "minecraft:cutout",
                                                               "textures": {"cross": "minecraft:block/short_grass"}})
    simple_state("frozen_plant")
    write_json(paths.MODELS / "item" / "frozen_plant.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "minecraft:block/short_grass"}})
    item_def("frozen_plant", "groundburst:item/frozen_plant", FROZEN_PLANT_TINT)
    write_json(paths.MODELS / "block" / "cryo_crystal.json", {"parent": "minecraft:block/cross", "render_type": "minecraft:cutout",
                                                               "textures": {"cross": block_id("cryo_crystal")}})
    rot = {"down": {"x": 180}, "east": {"x": 90, "y": 90}, "north": {"x": 90}, "south": {"x": 90, "y": 180}, "up": {}, "west": {"x": 90, "y": 270}}
    write_json(paths.ASSETS / "blockstates" / "cryo_crystal.json", {"variants": {
        f"facing={face}": {"model": block_id("cryo_crystal"), **r} for face, r in rot.items()}})
    write_json(paths.MODELS / "item" / "cryo_crystal.json", {"parent": "minecraft:item/generated", "textures": {"layer0": block_id("cryo_crystal")}})
    item_def("cryo_crystal", "groundburst:item/cryo_crystal")


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
    write_json(paths.MODELS / "block" / "dead_grass_block.json", {"parent": "minecraft:block/grass_block"})
    simple_state("dead_grass_block")
    item_def("dead_grass_block", block_id("dead_grass_block"), DEAD_GRASS)
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
    item_def("withered_crops", "groundburst:item/withered_crops")


def main() -> None:
    save_block("trinitite", trinitite())
    save_block("glassed_sand", glassed_sand())
    save_block("slag", slag())
    save_block("scorched_earth", scorched_earth())
    save_block("scorched_stone", scorched_stone())
    stain, glint = irradiated_soil()
    save_block("irradiated_soil_stain", stain)
    save_block("irradiated_soil_glint", glint)
    save_block("ash", ash())
    save_block("fallout_ash", fallout_ash())
    save_block("charred_log", charred_log_side())
    save_block("charred_log_top", charred_log_top())
    save_block("charred_planks", charred_planks())
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
    save_block("frosted_stone_rime", rime_over_stone())
    save_block("toxic_soil_stain", toxic_stain())
    save_block("cryo_crystal", cryo_crystal())
    frozen_ground()
    for i in range(len(FLAKES)):
        flake(i).save(paths.TEXTURES / "particle" / f"fallout_ash_{i}.png")
    write_json(paths.ASSETS / "particles" / "fallout_ash.json", {"textures": [f"groundburst:fallout_ash_{i}" for i in range(len(FLAKES))]})
    print("aftermath assets written")


if __name__ == "__main__":
    main()
