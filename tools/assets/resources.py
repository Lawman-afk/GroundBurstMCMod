"""Phase 3 resource assets: item sprites, storage/raw blocks, voidstone + exotic crystals, and the blockstate /
item-definition JSON for every resource block and item (ores included).
"""
from __future__ import annotations

import math
import random
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))

import numpy as np  # noqa: E402

from gb import materials as MT  # noqa: E402
from gb import palette as P  # noqa: E402
from gb import panels as PN  # noqa: E402
from gb import paths  # noqa: E402
from gb import vanilla as V  # noqa: E402
from gb.model import write_json  # noqa: E402
from gb.palette import fixed  # noqa: E402
from gb.sprite import faceted_blob, iso_bar, outline, powder_pile  # noqa: E402
from gb.tex import Tex  # noqa: E402
import minerals as M  # noqa: E402
import ores as O  # noqa: E402

URANIUM_METAL = fixed("uranium_metal", "#151c18", "#26322b", "#3a4a40", "#526658", "#6d8573", "#93ab95", "#c8e0b8")
LEAD_METAL = fixed("lead_metal", "#1b1f29", "#2e3442", "#454d5e", "#5e6779", "#7a8496", "#9ca5b5", "#c6ccd8")
TUNGSTEN_METAL = fixed("tungsten_metal", "#161c26", "#283244", "#3e4b62", "#586a86", "#7b8fad", "#a8bad2", "#e2ecf8")
VOID = fixed("void", "#07050c", "#100b19", "#1a1228", "#251a38", "#33244c", "#4a3468", "#6a4c90")
EXOTIC = fixed("exotic", "#1e0838", "#3a1268", "#5a1ea0", "#7c34d0", "#a05cf0", "#c896ff", "#f0dcff")

ITEMS: list[str] = []
BLOCKS: list[str] = []


def save_item(name: str, t: Tex) -> None:
    t.save(paths.TEXTURES / "item" / f"{name}.png")
    write_json(paths.MODELS / "item" / f"{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"groundburst:item/{name}"}})
    write_json(paths.ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": f"groundburst:item/{name}"}})
    ITEMS.append(name)


def simple_block(name: str, model: dict | None = None) -> None:
    """Blockstate with one variant + item definition pointing at the block model."""
    if model is not None:
        write_json(paths.MODELS / "block" / f"{name}.json", model)
    write_json(paths.ASSETS / "blockstates" / f"{name}.json", {"variants": {"": {"model": f"groundburst:block/{name}"}}})
    write_json(paths.ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": f"groundburst:block/{name}"}})
    BLOCKS.append(name)


def cube_all(name: str, tex_name: str | None = None) -> dict:
    return {"parent": "minecraft:block/cube_all", "textures": {"all": f"groundburst:block/{tex_name or name}"}}


# --- raw chunks --------------------------------------------------------------------------------------------------------

# Angular broken-rock silhouette shared by all raw ores (tones 1..6, lit from the top-left), like vanilla's raw
# items share one shape; each mineral adds its own surface detail.
CHUNK_TEMPLATE = [
    "................",
    "................",
    "......6554......",
    "....66555444....",
    "...6555554443...",
    "..655555444433..",
    "..55555444433332",
    ".355554443333322",
    ".335544333333222",
    ".333333333332221",
    "..33333333222221",
    "..2333332222211.",
    "...22222222111..",
    "....111111111...",
    "................",
    "................",
]


def raw_item(name: str, ramp, detail, seed: int) -> None:
    t = Tex(16, 16)
    pts = set()
    for y, row in enumerate(CHUNK_TEMPLATE):
        for x, c in enumerate(row):
            if c.isdigit():
                t.px(x, y, ramp[int(c)])
                pts.add((x, y))
    detail(t, pts)
    outline(t, pts, ramp.outline)
    save_item(name, t)


def _spots(t: Tex, pts, coords, colors):
    for (x, y), c in zip(coords, colors):
        if (x, y) in pts:
            t.px(x, y, c)


def build_raw_items() -> None:
    def spots(colors, count):
        def detail(t, rng, lit):
            for p in rng.sample(lit, min(count, len(lit))):
                t.px(*p, rng.choice(colors))
        return detail

    def uranium(t, rng, lit):
        # Pitchblende with yellow autunite crust and a couple of faint green glints.
        for p in rng.sample(lit, min(5, len(lit))):
            t.px(*p, rng.choice([O.AUTUNITE.light, O.AUTUNITE.base, O.URANIUM_GLINT]))
    save_item("raw_uranium", V.raw(O.PITCHBLENDE, "a", uranium))
    save_item("raw_lithium", V.raw(M.SPODUMENE, "b", spots([M.SPODUMENE.shine], 4)))
    save_item("raw_tungsten", V.raw(M.WOLFRAMITE, "a", spots([M.WOLFRAMITE.shine, M.WOLFRAMITE.highlight], 5)))
    save_item("raw_lead", V.raw(M.GALENA, "b", spots([M.GALENA.shine], 4)))
    save_item("raw_rare_earth", V.raw(M.MONAZITE, "a", spots([M.MONAZITE.shine, M.MONAZITE.highlight], 4)))
    save_item("fluorite", fluorite_crystal())


FLUORITE_ROWS = [
    # A purple cube with a small green one grown on in front (fluorite's colour zoning). Purple cube: T top, E the
    # lit front edges of its top, C its deeper-coloured core, W a glint, L/l left face, R/r right face; green cube:
    # t top, e its top's front edges, g left face, h right face.
    "................",
    ".....TT.........",
    "...TWTTT........",
    ".TTTTCCTTTT.....",
    ".EETCCCCTEE.....",
    ".LLEECCEERR.....",
    ".LLLLEERRRR.....",
    ".LLLLLRRRRRtt...",
    ".LLLLLRRRtttttt.",
    ".LLLLLRRReettee.",
    ".lllllrrrggeehh.",
    "...lllrrrggghhh.",
    ".....lr..ggghhh.",
    ".........ggghhh.",
    "...........gh...",
    "................",
]


def fluorite_crystal() -> Tex:
    """Fluorite as it grows: a glassy purple cube with a small green cube grown on in front of it."""
    f, g = M.FLUORITE, M.FLUORITE_GREEN
    legend = {"T": f[5], "E": f[6], "C": f[4], "W": (255, 255, 255, 255), "L": f[4], "l": f[3], "R": f[2], "r": f[1],
              "t": g[5], "e": g[6], "g": g[4], "h": g[2]}
    t = Tex(16, 16)
    pts = set()
    for y, row in enumerate(FLUORITE_ROWS):
        for x, c in enumerate(row):
            if c != ".":
                t.px(x, y, legend[c])
                pts.add((x, y))
    outline(t, pts, f.outline)
    return t


# --- ingots --------------------------------------------------------------------------------------------------------------

def ingot(name: str, ramp, mark) -> None:
    save_item(name, V.ingot(ramp, mark))


def build_ingots() -> None:
    ingot("uranium_ingot", URANIUM_METAL, lambda t, f: None)
    ingot("lead_ingot", LEAD_METAL, lambda t, f: None)

    def tung(t, f):
        for (x, y) in f["front"]:
            if (x + 2 * y) % 5 == 0 and (x, y + 1) in f["front"]:
                t.px(x, y, TUNGSTEN_METAL[4])          # machining lines on the flank
    ingot("tungsten_ingot", TUNGSTEN_METAL, lambda t, f: None)


# --- powders & shard -------------------------------------------------------------------------------------------------------

# Crusher dusts (DESIGN.md §6): one heap silhouette for the family; metal dusts catch a couple of glints so they
# read differently from the mineral powders.
URANIUM_DUST = fixed("uranium_dust", "#121a10", "#24321e", "#3a4c2c", "#55693c", "#71864c", "#97ab66", "#cce0a0")
COBALT_DUST = fixed("cobalt_dust", "#0c1430", "#18285a", "#24408a", "#3260b4", "#4f84d4", "#82acec", "#c8dcff")
IRON_DUST = fixed("iron_dust", "#2a2422", "#463c38", "#665a54", "#877a72", "#a89a90", "#c8bcb2", "#ece4dc")
GOLD_DUST = fixed("gold_dust", "#4a2c06", "#7a4a0a", "#b07412", "#d89a1e", "#f0bc34", "#fad866", "#fff4b8")
COPPER_DUST = fixed("copper_dust", "#3a160a", "#64280e", "#8e3e18", "#b45a26", "#d27a3c", "#eaa062", "#fcd2a4")
TUNGSTEN_DUST = fixed("tungsten_dust", "#121418", "#202329", "#33373f", "#484d56", "#626872", "#868d98", "#bcc3cc")
RARE_EARTH_DUST = fixed("rare_earth_dust", "#2e200e", "#4e3618", "#705024", "#906c34", "#ae8a4a", "#ccaa6c", "#ecd4a4")
CINNABAR_DUST = fixed("cinnabar_dust", "#3a0808", "#640e0c", "#901812", "#b8261a", "#d6402a", "#ec6c50", "#ffb09a")
FLUORITE_DUST = fixed("fluorite_dust", "#2a1a36", "#463056", "#664a7a", "#86689c", "#a68abc", "#c6aed8", "#ead8f4")

DUSTS = [
    # name, ramp, seed, metallic glints
    ("sulfur_dust", M.SULFUR, 7, False),
    ("saltpeter_dust", M.NITER, 8, False),
    ("uranium_dust", URANIUM_DUST, 11, True),
    ("lithium_dust", M.SPODUMENE, 12, False),
    ("tungsten_dust", TUNGSTEN_DUST, 13, True),
    ("lead_dust", M.GALENA, 14, True),
    ("rare_earth_dust", RARE_EARTH_DUST, 15, False),
    ("cobalt_dust", COBALT_DUST, 16, True),
    ("iron_dust", IRON_DUST, 17, True),
    ("gold_dust", GOLD_DUST, 18, True),
    ("copper_dust", COPPER_DUST, 19, True),
    ("cinnabar_dust", CINNABAR_DUST, 20, False),
    ("fluorite_dust", FLUORITE_DUST, 25, False),
]


def build_powders() -> None:
    for name, ramp, seed, metallic in DUSTS:
        save_item(name, V.dust2(ramp, seed, sparkle=metallic))

    # Exotic shard: a faceted crystal growing up-right with a smaller one at its foot (hand-pixelled).
    shard = [
        "................",
        "............6...",
        "...........653..",
        "..........6532..",
        ".........66532..",
        "........665322..",
        ".......665322...",
        "......665322....",
        ".....665322.....",
        "....665322......",
        "...665322.......",
        "..655322........",
        "..44322.........",
        "...322..........",
        "................",
        "................",
    ]
    t = Tex(16, 16)
    pts = set()
    for y, row in enumerate(shard):
        for x, c in enumerate(row):
            if c.isdigit():
                t.px(x, y, EXOTIC[int(c)])
                pts.add((x, y))
    outline(t, pts, VOID.outline)
    save_item("exotic_shard", t)


# --- storage & raw blocks ---------------------------------------------------------------------------------------------------

def _metal(ramp, tone: float = 0.55, brushed: float = 0.05) -> MT.Material:
    return MT.Material(ramp=np.array([s[:3] for s in ramp.shades], dtype=np.float32), tone=tone, mottle=0.035, grain=0.03, dither=0.45,
                       edge_light=0.14, edge_dark=0.1, ao=0.0, grime=0.0, dust=0.0, brushed=brushed)


def uranium_block() -> Tex:
    """A uranium billet in a hazard-striped frame, a trefoil placard on it (the material library, the remaster after the
    Fission Reactor rework)."""
    img = PN.face(_metal(URANIUM_METAL, 0.5), frame=MT.stripes(width=2, wear=0.0), style="raised", seed=31)
    PN.bevel_box(img, 3, 3, 10, 10, True, 0.6)
    t = MT.to_tex(img)
    t.trefoil(8, 8, 3.1, P.HAZARD_BLACK.base, bg=P.HAZARD_YELLOW.base)
    t.outline(0, 0, 16, 16, P.HAZARD_BLACK.base)
    return t


def lead_block() -> Tex:
    """Interlocking lead shielding bricks (chevron-edged, as used for radiation caves): dull lead in courses, each brick
    lit along its top and left, an interlock notch at its end."""
    img = PN.tile(_metal(LEAD_METAL, 0.5, 0.0), 33)
    col, row = PN.grid()
    off = np.where((row // 4) % 2 == 0, 0, 4)
    bx = (col - off) % 8
    by = row % 4
    MT.darken(img, (by == 3) | (bx == 7), 0.58)                 # mortarless joints between the bricks
    MT.lighten(img, ((by == 0) | (bx == 0)) & (by != 3) & (bx != 7), 1.14, 5)
    MT.darken(img, (bx == 6) & (by == 1), 0.5)                   # interlock notch
    return MT.to_tex(img)


def tungsten_block() -> Tex:
    """A machined tungsten plate in four, fly-cut arcs in its top-left quarter, bolted at its corners."""
    img = PN.tile(_metal(TUNGSTEN_METAL, 0.55, 0.04), 35)
    PN.bevel_box(img, 0, 0, 16, 16, True, 1.0)
    col, row = PN.grid()
    for r in (3.5, 5.5, 7.5):
        d = np.sqrt((col + 0.5 - 2) ** 2 + (row + 0.5 - 2) ** 2)
        MT.lighten(img, (np.abs(d - r) < 0.5) & (col >= 1) & (row >= 1) & (col < 15) & (row < 15), 1.12, 6)
    MT.darken(img, ((row == 8) & (col >= 1) & (col < 15)) | ((col == 8) & (row >= 1) & (row < 8)), 0.62)
    MT.lighten(img, ((row == 9) & (col >= 1) & (col < 15)) | ((col == 9) & (row >= 1) & (row < 8)), 1.1, 4)
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        PN.bolt(img, x, y)
    return MT.to_tex(img)


def raw_block(ramp, spot_color, seed: int) -> Tex:
    """Packed raw chunks with dark gaps, like a compressed aggregate."""
    rng = random.Random(seed)
    t = Tex(16, 16, ramp.outline)
    centres = [(3, 3), (9, 2), (14, 4), (5, 8), (11, 8), (2, 13), (8, 13), (14, 12)]
    for (cx, cy) in centres:
        lobes = [(cx + 0.5, cy + 0.5, rng.uniform(2.6, 3.3)), (cx + rng.uniform(-1, 1), cy + rng.uniform(-1, 1), 2.0)]
        for ox in (-16, 0, 16):                         # wrap so the texture tiles
            for oy in (-16, 0, 16):
                shifted = [(a + ox, b + oy, r) for a, b, r in lobes]
                faceted_blob(t, shifted, ramp, facets=1, seed=seed + cx)
    for _ in range(4):
        t.px(rng.randrange(16), rng.randrange(16), spot_color)
    return t


def build_blocks() -> None:
    for name, tex in (("uranium_block", uranium_block()), ("lead_block", lead_block()), ("tungsten_block", tungsten_block()),
                      ("raw_uranium_block", raw_block(O.PITCHBLENDE, O.URANIUM_GLINT, 11)),
                      ("raw_lithium_block", raw_block(M.SPODUMENE, M.SPODUMENE.shine, 12)),
                      ("raw_tungsten_block", raw_block(M.WOLFRAMITE, M.WOLFRAMITE.shine, 13)),
                      ("raw_lead_block", raw_block(M.GALENA, M.GALENA.shine, 14)),
                      ("raw_rare_earth_block", raw_block(M.MONAZITE, M.MONAZITE.shine, 15))):
        tex.save(paths.TEXTURES / "block" / f"{name}.png")
        simple_block(name, cube_all(name))


# --- voidstone & exotic crystals -------------------------------------------------------------------------------------------------

def voidstone(budding: bool) -> Tex:
    rng = random.Random(77 if budding else 76)
    t = Tex(16, 16, VOID.base)
    # Layered dark strata with faint violet veins.
    for y in range(16):
        t.hline(0, y, 16, VOID[2 + (y // 3 + (y % 5 == 0)) % 3])
    for _ in range(26):
        t.px(rng.randrange(16), rng.randrange(16), VOID[rng.choice((1, 4))])
    for x0, y0, length in ((1, 3, 6), (9, 10, 6), (4, 12, 4)):
        for k in range(length):
            t.px(x0 + k, y0 + (k // 2), EXOTIC.shadow)
    if budding:
        for (x, y) in ((3, 5), (11, 3), (7, 9), (12, 12), (2, 13)):
            t.px(x, y, EXOTIC.highlight)
            t.px(x + 1, y, EXOTIC.base)
            t.px(x, y + 1, EXOTIC.shadow)
    return t


def crystal_sprite(stage: int) -> Tex:
    """Cross-model crystal sprites: stage 0 small bud, 1 large bud, 2 full cluster."""
    t = Tex(16, 16)
    spikes = {0: [(8, 15, 5, 2)], 1: [(8, 15, 9, 3), (5, 15, 5, 2), (11, 15, 6, 2)],
              2: [(8, 15, 14, 3), (4, 15, 9, 2), (12, 15, 11, 3), (2, 15, 5, 2), (14, 15, 6, 2)]}[stage]
    for cx, base, h, w in spikes:
        for k in range(h):
            y = base - k
            half = max(0, round(w * (1 - k / h) - 0.3))
            for x in range(cx - half, cx + half + 1):
                if not (0 <= x < 16 and 0 <= y < 16):
                    continue
                tone = 5 if x < cx else 3 if x == cx else 2
                if k >= h - 2:
                    tone = 6
                t.px(x, y, EXOTIC[tone])
            t.px(cx, y, EXOTIC.highlight if k % 3 else EXOTIC.shine)   # bright core line
    return t


def build_exotic() -> None:
    for name, budding in (("voidstone", False), ("budding_voidstone", True)):
        voidstone(budding).save(paths.TEXTURES / "block" / f"{name}.png")
        simple_block(name, cube_all(name))
    for stage, name in enumerate(("small_exotic_bud", "large_exotic_bud", "exotic_crystal_cluster")):
        crystal_sprite(stage).save(paths.TEXTURES / "block" / f"{name}.png")
        model = {"parent": "minecraft:block/cross", "render_type": "minecraft:cutout", "textures": {"cross": f"groundburst:block/{name}"}}
        write_json(paths.MODELS / "block" / f"{name}.json", model)
        variants = {}
        rot = {"up": {}, "down": {"x": 180}, "north": {"x": 90}, "south": {"x": 90, "y": 180},
               "east": {"x": 90, "y": 90}, "west": {"x": 90, "y": 270}}
        for facing, r in rot.items():
            variants[f"facing={facing}"] = {"model": f"groundburst:block/{name}", **r}
        write_json(paths.ASSETS / "blockstates" / f"{name}.json", {"variants": variants})
        # Item uses the flat sprite, like vanilla amethyst buds.
        write_json(paths.MODELS / "item" / f"{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"groundburst:block/{name}"}})
        write_json(paths.ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": f"groundburst:item/{name}"}})
        BLOCKS.append(name)


def build_ore_states() -> None:
    for name in O.ALL_ORES:
        simple_block(name)


# --- review --------------------------------------------------------------------------------------------------------------

def item_sheet(names: list[str], folder: str, out_name: str) -> Path:
    from PIL import Image, ImageDraw
    cell = 160
    cols = 6
    rows = (len(names) + cols - 1) // cols
    sheet = Image.new("RGB", (cols * cell, rows * (cell + 16)), (30, 32, 36))
    d = ImageDraw.Draw(sheet)
    for i, n in enumerate(names):
        t = Tex.load(paths.TEXTURES / folder / f"{n}.png")
        x, y = (i % cols) * cell, (i // cols) * (cell + 16)
        slot = Image.new("RGB", (cell, cell), (139, 139, 139))
        slot.paste(Image.new("RGB", (136, 136), (55, 55, 55)), (12, 4))
        big = t.scaled(8)
        slot.paste(big, (12, 4), big)
        small = t.image()
        slot.paste(small, (cell - 22, cell - 20), small)
        sheet.paste(slot, (x, y))
        d.text((x + 4, y + cell + 2), n, fill=(220, 220, 220))
    out = paths.PREVIEWS / out_name
    sheet.save(out)
    return out


if __name__ == "__main__":
    build_raw_items()
    build_ingots()
    build_powders()
    build_blocks()
    build_exotic()
    build_ore_states()
    print(item_sheet(ITEMS, "item", "items_review.png"))
    print(item_sheet(["uranium_block", "lead_block", "tungsten_block", "raw_uranium_block", "raw_lithium_block",
                      "raw_tungsten_block", "raw_lead_block", "raw_rare_earth_block", "voidstone", "budding_voidstone",
                      "small_exotic_bud", "large_exotic_bud", "exotic_crystal_cluster"], "block", "blocks_review.png"))
