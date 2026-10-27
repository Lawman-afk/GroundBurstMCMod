"""The aftermath on the ground (DESIGN.md §21.7, §21.8 slice 12b): what a nuclear blast leaves of the land, what a strong
radiation field kills, and the ash a fallout plume lays.

Natural matter painted on the material library's tiles (`gz/materials.py`: world-space noise that wraps every block,
scattered dithering, a base tone right on a ramp shade), each on a short hue-shifted ramp of its own, with its details
drawn over it in the same wrapping space:

- Trinitite: bottle-green glass, bubbles with a lit rim, black inclusions, a glint or two.
- Glassed Sand: sand fused to a pale glaze, crazed with fine cracks (a wrapping cell network), each crack's lower lip lit.
- Slag: black-brown rock full of vesicles (dark pits lit on their far rim), a few rusty flecks.
- Scorched Earth: charred soil, bits of charcoal, grey ash specks. Scorched Stone: grey stone blackened in soot patches.
- Irradiated Soil: olive-brown earth with sickly green flecks that glow (a second, emissive layer, as the ores' glints).
- Ash and Fallout Ash: fine powder, pale and warm; fallout greyer, with dark grains and a few hot yellow-green specks.
- Charred Log, Wood and Planks: wood burnt to a crackle of black blocks (alligatoring) with a dull silver sheen on their
  lit edges; the log's end in charred rings and radial cracks; the planks in four boards with char cracks across.

Dead Grass, Dead Leaves and Withered Crops are vanilla's own grass, oak leaves and ripe wheat through a dead tint (the
tint sources are in the client: `FalloutClient`), so no texture of theirs is drawn here: only their models.

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

# --- ramps: darkest to lightest, lit shades warmer, shadows cooler --------------------------------------------------------------
ASH_RAMP = M.ramp("#4d4b48", "#5f5c58", "#726e69", "#86817b", "#9a948d", "#ada79f", "#bfb9b1", "#d0cac2")
FALLOUT_RAMP = M.ramp("#2e2d29", "#3b3933", "#4a473f", "#59564c", "#6a665a", "#7c7769", "#8e8979", "#a09b8a")
TRINITITE_RAMP = M.ramp("#0b170d", "#112614", "#19381b", "#234c23", "#30612d", "#40773a", "#548e4a", "#6da55e", "#8cbd77")
GLASSED_SAND_RAMP = M.ramp("#6b5e44", "#827351", "#998960", "#ae9d6f", "#c2b081", "#d3c294", "#e1d1a9", "#ecdebf")
SLAG_RAMP = M.ramp("#0f0e0f", "#181617", "#221f20", "#2d2928", "#393432", "#46403c", "#554d47", "#655b53")
SOIL_RAMP = M.ramp("#110d0a", "#1b1510", "#251d16", "#30261c", "#3c3023", "#4a3b2c", "#584736", "#675441")
STONE_RAMP = M.ramp("#242424", "#2f2f2e", "#3b3a39", "#474645", "#545351", "#615f5d", "#6e6c69", "#7c7975")
EARTH_RAMP = M.ramp("#291f16", "#34281c", "#413122", "#4e3b29", "#5b4530", "#685038", "#765c41", "#84694c")
CHAR_RAMP = M.ramp("#0a0908", "#12100e", "#1b1714", "#241f1b", "#2e2722", "#39302a", "#453a33")
SHEEN = (104, 99, 94, 255)            # the dull silver of char's lit edges
RUST = ((110, 52, 24, 255), (146, 72, 32, 255))
GLOW = ((128, 214, 74, 255), (184, 248, 116, 255))
HOT = (196, 206, 120, 255)


def shade(ramp, k: int) -> tuple[int, int, int, int]:
    r, g, b = ramp[k]
    return int(r), int(g), int(b), 255


def on(n: int, k: int) -> float:
    """The tone of shade {k} of an {n}-shade ramp (right on it: noise then flickers round one shade, not between two)."""
    return k / (n - 1)


def sites(seed: int, count: int) -> list[tuple[int, int]]:
    """{count} spots over a tile, from a seed (the same every run)."""
    rng = np.random.default_rng(seed)
    return [(int(x), int(y)) for x, y in rng.integers(0, 16, size=(count, 2))]


def put(img: np.ndarray, x: int, y: int, colour) -> None:
    """One texel, wrapping round the tile's edges so a detail on its border carries on into the next block."""
    img[y % 16, x % 16] = colour


def tile(ramp, tone_shade: int, seed: int, **kw) -> np.ndarray:
    d = dict(mottle=0.07, grain=0.06, dither=0.8, scale=0.5, despeckle=False)
    d.update(kw)
    return M.tile(M.Material(ramp=ramp, tone=on(len(ramp), tone_shade), **d), seed=seed)


def save_block(name: str, img: np.ndarray) -> None:
    M.to_tex(img).save(paths.TEXTURES / "block" / f"{name}.png")


# --- glass and molten ground ------------------------------------------------------------------------------------------------------
def trinitite() -> np.ndarray:
    img = tile(TRINITITE_RAMP, 3, 11, mottle=0.1, grain=0.03, dither=0.55, scale=0.6)
    for x, y in sites(12, 6):                          # bubbles: a dark hollow, its rim lit at the bottom right
        put(img, x, y, shade(TRINITITE_RAMP, 0))
        put(img, x + 1, y + 1, shade(TRINITITE_RAMP, 6))
        put(img, x + 1, y, shade(TRINITITE_RAMP, 1))
    for x, y in sites(13, 4):                          # black inclusions
        put(img, x, y, (12, 14, 10, 255))
    for x, y in sites(14, 3):                          # glints on the glaze
        put(img, x, y, shade(TRINITITE_RAMP, 8))
        put(img, x + 1, y, shade(TRINITITE_RAMP, 7))
    return img


def cells(seed: int, count: int, stretch: tuple[float, float] = (1, 1), chebyshev: bool = False) -> tuple[np.ndarray, np.ndarray]:
    """A cell network that wraps round the tile: each texel's nearest and second-nearest site distances."""
    pts = np.array(sites(seed, count), dtype=np.float32) + 0.5
    yy, xx = np.mgrid[0:16, 0:16].astype(np.float32) + 0.5
    d = []
    for ox in (-16, 0, 16):
        for oy in (-16, 0, 16):
            for px, py in pts:
                dx, dy = (xx - px - ox) * stretch[0], (yy - py - oy) * stretch[1]
                d.append(np.maximum(np.abs(dx), np.abs(dy)) if chebyshev else np.hypot(dx, dy))
    d = np.sort(np.stack(d), axis=0)
    return d[0], d[1]


def glassed_sand() -> np.ndarray:
    img = tile(GLASSED_SAND_RAMP, 4, 21, mottle=0.07, grain=0.03, dither=0.45, scale=0.6, despeckle=True)
    # Crazing: a few fine cracks through the glaze, faint, each with a lit lip under it.
    d0, d1 = cells(22, 4)
    crack = (d1 - d0) < 0.45
    M.darken(img, crack, 0.84)
    lip = np.roll(crack, 1, axis=0) & ~crack
    M.lighten(img, lip, 1.05, 3)
    for x, y in sites(23, 3):                          # the glaze's shine
        put(img, x, y, shade(GLASSED_SAND_RAMP, 7))
    for x, y in sites(24, 4):                          # unfused grains
        put(img, x, y, shade(GLASSED_SAND_RAMP, 2))
    return img


def slag() -> np.ndarray:
    img = tile(SLAG_RAMP, 3, 31, mottle=0.08, grain=0.06, dither=0.8)
    for x, y in sites(32, 8):                          # vesicles
        put(img, x, y, shade(SLAG_RAMP, 0))
        put(img, x + 1, y, shade(SLAG_RAMP, 1))
        put(img, x + 1, y + 1, shade(SLAG_RAMP, 6))
    for i, (x, y) in enumerate(sites(33, 3)):          # rust
        put(img, x, y, RUST[i % 2])
    return img


# --- burnt ground -----------------------------------------------------------------------------------------------------------------
def scorched_earth() -> np.ndarray:
    img = tile(SOIL_RAMP, 3, 41, mottle=0.08, grain=0.07, dither=0.85)
    for x, y in sites(42, 6):                          # charcoal bits
        put(img, x, y, shade(SOIL_RAMP, 0))
        put(img, x + 1, y, shade(SOIL_RAMP, 1))
    for x, y in sites(43, 5):                          # ash
        put(img, x, y, shade(ASH_RAMP, 4))
    return img


def scorched_stone() -> np.ndarray:
    """Grey stone darkened by soot, the soot worked into its value before the ramp (so it dithers in, not in blotches)."""
    def soot(f):
        return -0.2 * np.clip((M.noise(f, 3, 52, 2) - 0.42) / 0.3, 0, 1)

    img = M.tile(M.Material(ramp=STONE_RAMP, tone=on(len(STONE_RAMP), 4), mottle=0.06, grain=0.07, dither=0.85, scale=0.5,
                            despeckle=False, extra=soot), seed=51)
    for x, y in sites(53, 6):
        put(img, x, y, shade(STONE_RAMP, 6))
    return img


def irradiated_soil() -> tuple[np.ndarray, np.ndarray]:
    img = tile(EARTH_RAMP, 3, 61, mottle=0.08, grain=0.07, dither=0.85)
    glint = np.zeros((16, 16, 4), dtype=np.uint8)
    for i, (x, y) in enumerate(sites(62, 7)):
        c = GLOW[1] if i % 3 == 0 else GLOW[0]
        put(img, x, y, c)
        put(glint, x, y, c)
    for x, y in sites(63, 4):
        put(img, x, y, shade(EARTH_RAMP, 0))
    return img, glint


def ash() -> np.ndarray:
    img = tile(ASH_RAMP, 5, 71, mottle=0.05, grain=0.05, dither=0.7)
    for x, y in sites(72, 5):                          # charcoal
        put(img, x, y, shade(ASH_RAMP, 1))
    return img


def fallout_ash() -> np.ndarray:
    img = tile(FALLOUT_RAMP, 4, 81, mottle=0.07, grain=0.06, dither=0.8)
    for x, y in sites(82, 6):
        put(img, x, y, shade(FALLOUT_RAMP, 0))
    for x, y in sites(83, 3):                          # hot particles
        put(img, x, y, HOT)
    return img


# --- burnt wood ---------------------------------------------------------------------------------------------------------------------
def char_blocks(seed: int, width=(3, 5), height=(3, 5)) -> np.ndarray:
    """Alligatored char: blocks in staggered columns, separated by cracks; the label of each texel's block (-1: crack)."""
    rng = np.random.default_rng(seed)
    label = np.full((16, 16), -1, dtype=np.int32)
    x, n = 0, 0
    widths = []
    while x < 16:
        w = int(rng.integers(width[0], width[1] + 1))
        w = min(w, 16 - x) if 16 - x - w < width[0] else w
        widths.append((x, w))
        x += w
    for x0, w in widths:
        y = int(rng.integers(0, 4))
        while y < 16 + 4:
            h = int(rng.integers(height[0], height[1] + 1))
            for yy in range(y, y + h - 1):             # the last row is the crack under it
                for xx in range(x0, x0 + w - 1):       # the last column the crack beside it
                    label[yy % 16, xx % 16] = n
            n += 1
            y += h
    return label


def charred(label: np.ndarray, seed: int, lit_gain: float = 1.3, sheen_share: float = 0.35, vary: float = 0.0) -> np.ndarray:
    img = tile(CHAR_RAMP, 3, seed, mottle=0.05, grain=0.05, dither=0.6)
    crack = label < 0
    rng = np.random.default_rng(seed + 1)
    if vary:                                           # each block a touch lighter or darker than the next
        tone = 1 + vary * (rng.random(int(label.max()) + 1) * 2 - 1)
        M.lighten(img, ~crack, 1.0)
        rgb = img[..., :3].astype(np.float32)
        rgb[~crack] *= tone[label[~crack]][:, None]
        img[..., :3] = np.clip(rgb, 0, 255).astype(np.uint8)
    img[crack] = shade(CHAR_RAMP, 0)
    # Each block's top and left edge catches the light (a dull silver now and then); its bottom and right are dark.
    up = np.roll(label, 1, axis=0) != label
    left = np.roll(label, 1, axis=1) != label
    down = np.roll(label, -1, axis=0) != label
    lit = ~crack & (up | left)
    M.lighten(img, lit, lit_gain, 6)
    sheen = lit & (rng.random((16, 16)) < sheen_share)
    img[sheen] = SHEEN
    M.darken(img, ~crack & down & ~up, 0.8)
    return img


def charred_log_side() -> np.ndarray:
    """The bark burnt to small blocks of char in staggered columns up the log (alligatoring), deep cracks between."""
    return charred(char_blocks(91, width=(2, 4), height=(2, 5)), 92, lit_gain=1.18, sheen_share=0.18, vary=0.12)


def charred_log_top() -> np.ndarray:
    """The log's end: growth rings charred in two shades, radial checks, a pith, and the char-crackled bark round it."""
    img = tile(CHAR_RAMP, 3, 95, mottle=0.04, grain=0.04, dither=0.5)
    yy, xx = np.mgrid[0:16, 0:16].astype(np.float32) + 0.5
    r = np.maximum(np.abs(xx - 8), np.abs(yy - 8)) * 0.55 + np.hypot(xx - 8, yy - 8) * 0.45   # squarish rings, as vanilla's
    inside = r < 6.7
    ring = (np.floor(r * 0.75) % 2 == 0) & inside
    img[ring] = shade(CHAR_RAMP, 4)
    img[~ring & inside] = shade(CHAR_RAMP, 2)
    grain = M.tile(M.Material(ramp=CHAR_RAMP, tone=on(len(CHAR_RAMP), 3), mottle=0.0, grain=0.08, dither=0.9, despeckle=False), seed=98)
    speck = inside & (np.random.default_rng(99).random((16, 16)) < 0.12)
    img[speck] = grain[speck]
    bark = ~inside
    img[bark] = charred(char_blocks(96, width=(2, 3), height=(2, 3)), 97, lit_gain=1.1, sheen_share=0.08)[bark]
    edge = inside & (r >= 6.0)
    img[edge] = shade(CHAR_RAMP, 1)
    a = np.degrees(np.arctan2(yy - 8, xx - 8))
    for angle in (-120, 15, 100):                      # radial checks where the wood split as it burned
        crack = (np.abs(((a - angle + 180) % 360) - 180) < 8) & (r > 2.0) & inside
        img[crack] = shade(CHAR_RAMP, 0)
    img[r < 1.2] = shade(CHAR_RAMP, 0)                 # the pith
    return img


def charred_planks() -> np.ndarray:
    """Four boards, their grain along them, charred: dark seams, ends staggered, each board's top edge catching the
    light, a check or two across them."""
    img = M.tile(M.Material(ramp=CHAR_RAMP, tone=on(len(CHAR_RAMP), 4), mottle=0.03, grain=0.04, dither=0.55, brushed=0.18,
                            scale=0.5, despeckle=False), seed=101)
    col, row = M.grid_xy()
    board = row // 4
    for b, k in enumerate((1.0, 0.9, 1.08, 0.95)):    # each board its own tone
        M.lighten(img, (board == b) & (row % 4 != 3), k, 0)
    seam = row % 4 == 3
    img[seam] = shade(CHAR_RAMP, 0)
    M.lighten(img, (row % 4 == 0), 1.22, 5)
    ends = ((board == 0) & (col == 11)) | ((board == 1) & (col == 4)) | ((board == 2) & (col == 13)) | ((board == 3) & (col == 7))
    img[ends & ~seam] = shade(CHAR_RAMP, 1)
    rng = np.random.default_rng(102)
    for b in range(4):                                 # a check across a board here and there
        if rng.random() < 0.6:
            c = int(rng.integers(0, 16))
            put(img, c, b * 4 + 1, shade(CHAR_RAMP, 1))
            put(img, c, b * 4 + 2, shade(CHAR_RAMP, 1))
    sheen = (row % 4 == 0) & ~ends & (rng.random((16, 16)) < 0.25)
    img[sheen] = SHEEN
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


def cube_all(name: str) -> None:
    write_json(paths.MODELS / "block" / f"{name}.json", {"parent": "minecraft:block/cube_all", "textures": {"all": block_id(name)}})
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
    """Irradiated Soil: the earth, and its flecks again on an emissive layer over it (as the ores' glints)."""
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
        model = name if n == 8 else f"{name}_height{h}"
        if n < 8:
            side = {"uv": [0, 16 - h, 16, 16], "texture": "#texture"}
            write_json(paths.MODELS / "block" / f"{model}.json", {
                "parent": "minecraft:block/thin_block", "textures": {"particle": block_id(name), "texture": block_id(name)},
                "elements": [{"from": [0, 0, 0], "to": [16, h, 16], "faces": {
                    "down": {"uv": [0, 0, 16, 16], "texture": "#texture", "cullface": "down"},
                    "up": {"uv": [0, 0, 16, 16], "texture": "#texture"},
                    **{d: {**side, "cullface": d} for d in ("north", "south", "west", "east")}}}]})
        else:
            write_json(paths.MODELS / "block" / f"{name}_block.json", {"parent": "minecraft:block/cube_all", "textures": {"all": block_id(name)}})
            model = f"{name}_block"
        variants[f"layers={n}"] = {"model": block_id(model)}
    write_json(paths.ASSETS / "blockstates" / f"{name}.json", {"variants": variants})
    item_def(name, block_id(f"{name}_height2"))


def dead_plants() -> None:
    """Vanilla's grass, leaves and wheat, through the dead tints (FalloutClient: the same colours here for the items)."""
    dead_grass, dead_leaves, withered = 0xFF9C8F62, 0xFF7F6744, 0xFF7D7466
    write_json(paths.MODELS / "block" / "dead_grass_block.json", {"parent": "minecraft:block/grass_block"})
    simple_state("dead_grass_block")
    item_def("dead_grass_block", block_id("dead_grass_block"), dead_grass)
    write_json(paths.MODELS / "block" / "dead_leaves.json", {"parent": "minecraft:block/leaves", "render_type": "minecraft:cutout",
                                                              "textures": {"all": "minecraft:block/oak_leaves"}})
    simple_state("dead_leaves")
    item_def("dead_leaves", block_id("dead_leaves"), dead_leaves)
    planes = []
    for axis, at, (a, b) in (("x", 4, ("west", "east")), ("x", 12, ("west", "east")), ("z", 4, ("north", "south")), ("z", 12, ("north", "south"))):
        frm = [at, -1, 0] if axis == "x" else [0, -1, at]
        to = [at, 15, 16] if axis == "x" else [16, 15, at]
        flip = at == 12
        planes.append({"from": frm, "to": to, "shade_direction_override": "up", "faces": {
            a: {"uv": [16, 0, 0, 16] if flip else [0, 0, 16, 16], "texture": "#crop", "tintindex": 0},
            b: {"uv": [0, 0, 16, 16] if flip else [16, 0, 0, 16], "texture": "#crop", "tintindex": 0}}})
    write_json(paths.MODELS / "block" / "withered_crops.json", {
        "ambientocclusion": False, "render_type": "minecraft:cutout",
        "textures": {"particle": "#crop", "crop": "minecraft:block/wheat_stage7"}, "elements": planes})
    simple_state("withered_crops")
    write_json(paths.MODELS / "item" / "withered_crops.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "minecraft:block/wheat_stage7"}})
    item_def("withered_crops", "groundzero:item/withered_crops", withered)


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
