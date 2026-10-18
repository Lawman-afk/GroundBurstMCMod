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

from gz import palette as P  # noqa: E402
from gz import paths  # noqa: E402
from gz.model import write_json  # noqa: E402
from gz.palette import fixed  # noqa: E402
from gz.sprite import faceted_blob, iso_bar, outline, powder_pile  # noqa: E402
from gz.tex import Tex  # noqa: E402
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
    write_json(paths.MODELS / "item" / f"{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"groundzero:item/{name}"}})
    write_json(paths.ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": f"groundzero:item/{name}"}})
    ITEMS.append(name)


def simple_block(name: str, model: dict | None = None) -> None:
    """Blockstate with one variant + item definition pointing at the block model."""
    if model is not None:
        write_json(paths.MODELS / "block" / f"{name}.json", model)
    write_json(paths.ASSETS / "blockstates" / f"{name}.json", {"variants": {"": {"model": f"groundzero:block/{name}"}}})
    write_json(paths.ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": f"groundzero:block/{name}"}})
    BLOCKS.append(name)


def cube_all(name: str, tex_name: str | None = None) -> dict:
    return {"parent": "minecraft:block/cube_all", "textures": {"all": f"groundzero:block/{tex_name or name}"}}


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
    raw_item("raw_uranium", O.PITCHBLENDE, lambda t, p: _spots(t, p, [(5, 7), (10, 5), (9, 11), (4, 11), (11, 8)],
             [O.URANIUM_GLINT, O.AUTUNITE.light, O.URANIUM_GLINT, O.AUTUNITE.base, O.AUTUNITE.light]), 1)
    def lith(t, p):
        for x, y in ((5, 9), (6, 8), (7, 7), (9, 10), (10, 9), (11, 8)):
            if (x, y) in p:
                t.px(x, y, M.SPODUMENE.shine)
    raw_item("raw_lithium", M.SPODUMENE, lith, 2)
    raw_item("raw_tungsten", M.WOLFRAMITE, lambda t, p: _spots(t, p, [(5, 6), (6, 6), (9, 5), (8, 10), (11, 9)],
             [M.WOLFRAMITE.shine, M.WOLFRAMITE.highlight, M.WOLFRAMITE.highlight, M.WOLFRAMITE.highlight, M.WOLFRAMITE.shine]), 3)
    def gal(t, p):
        for x, y in ((5, 8), (9, 6), (8, 11)):
            for dx, dy, tone in ((0, 0, 6), (1, 0, 5), (0, 1, 4), (1, 1, 2)):
                if (x + dx, y + dy) in p:
                    t.px(x + dx, y + dy, M.GALENA[tone])
    raw_item("raw_lead", M.GALENA, gal, 4)
    raw_item("raw_rare_earth", M.MONAZITE, lambda t, p: _spots(t, p, [(5, 7), (9, 6), (7, 10), (11, 9)],
             [M.MONAZITE.shine, M.MONAZITE.highlight, M.MONAZITE.shine, M.MONAZITE.highlight]), 5)


# --- ingots --------------------------------------------------------------------------------------------------------------

def ingot(name: str, ramp, mark) -> None:
    t = Tex(16, 16)
    faces = iso_bar(t, 6.0, 4.0, 9, 4, 3, ramp, taper=0.7)
    pts = set().union(*faces.values())
    # Rim light on the top face's far edges and a hot specular on the near corner.
    for (x, y) in faces["top"]:
        if (x, y - 1) not in faces["top"]:
            t.px(x, y, ramp.highlight)
    outline(t, pts, ramp.outline)
    mark(t, faces)
    save_item(name, t)


def build_ingots() -> None:
    def trefoil_mark(t, f):
        cx = sorted(f["top"])[len(f["top"]) // 2]
        t.px(cx[0], cx[1], P.NUCLEAR_AMBER.base)
        t.px(cx[0] + 1, cx[1], P.NUCLEAR_AMBER.shadow)
    ingot("uranium_ingot", URANIUM_METAL, trefoil_mark)
    ingot("lead_ingot", LEAD_METAL, lambda t, f: None)
    def tung(t, f):
        for (x, y) in f["front"]:
            if (x + y) % 4 == 0:
                t.px(x, y, TUNGSTEN_METAL.light)      # machining lines on the flank
    ingot("tungsten_ingot", TUNGSTEN_METAL, tung)


# --- powders & shard -------------------------------------------------------------------------------------------------------

def build_powders() -> None:
    for name, ramp, seed in (("sulfur_dust", M.SULFUR, 7), ("saltpeter_dust", M.NITER, 8)):
        t = Tex(16, 16)
        pts = powder_pile(t, 8, 13, 8, 6.0, ramp, seed)
        outline(t, pts, ramp.outline)
        save_item(name, t)

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

def uranium_block() -> Tex:
    t = Tex(16, 16)
    t.stripes(0, 0, 16, 16, P.HAZARD_YELLOW.base, P.HAZARD_BLACK.light, width=2)
    t.panel(2, 2, 12, 12, URANIUM_METAL, base=3, style="inset")
    t.panel(3, 3, 10, 10, URANIUM_METAL, base=3, style="raised")
    t.trefoil(8, 8, 3.1, P.HAZARD_BLACK.base, bg=P.HAZARD_YELLOW.base)
    for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):
        t.rivet(x, y, URANIUM_METAL)
    t.outline(0, 0, 16, 16, P.HAZARD_BLACK.base)
    return t


def lead_block() -> Tex:
    """Interlocking lead shielding bricks (chevron-edged, as used for radiation caves)."""
    t = Tex(16, 16, LEAD_METAL.base)
    for row, y in enumerate((0, 4, 8, 12)):
        off = 0 if row % 2 == 0 else 4
        for x in range(-8, 16, 8):
            bx = x + off
            t.panel(bx, y, 8, 4, LEAD_METAL, base=3, style="raised")
            t.px(bx + 7, y + 1, LEAD_METAL.deep)      # interlock notch
            t.px(bx, y + 2, LEAD_METAL.highlight)
    t.scratches(0, 0, 16, 16, (*LEAD_METAL.highlight[:3], 160), seed=3, count=3, length=3)
    return t


def tungsten_block() -> Tex:
    t = Tex(16, 16)
    t.panel(0, 0, 16, 16, TUNGSTEN_METAL, base=3, style="raised")
    # Fly-cut machining arcs.
    for r in (3.5, 5.5, 7.5):
        for a in range(0, 90, 6):
            x = int(2 + r * math.cos(math.radians(a)))
            y = int(2 + r * math.sin(math.radians(a)))
            if 1 <= x < 15 and 1 <= y < 15:
                t.px(x, y, TUNGSTEN_METAL.light)
    t.seam_h(1, 8, 14, TUNGSTEN_METAL)
    t.seam_v(8, 1, 7, TUNGSTEN_METAL)
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        t.rivet(x, y, TUNGSTEN_METAL, size=2) if x < 13 and y < 13 else t.rivet(x, y, TUNGSTEN_METAL)
    return t


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
        model = {"parent": "minecraft:block/cross", "render_type": "minecraft:cutout", "textures": {"cross": f"groundzero:block/{name}"}}
        write_json(paths.MODELS / "block" / f"{name}.json", model)
        variants = {}
        rot = {"up": {}, "down": {"x": 180}, "north": {"x": 90}, "south": {"x": 90, "y": 180},
               "east": {"x": 90, "y": 90}, "west": {"x": 90, "y": 270}}
        for facing, r in rot.items():
            variants[f"facing={facing}"] = {"model": f"groundzero:block/{name}", **r}
        write_json(paths.ASSETS / "blockstates" / f"{name}.json", {"variants": variants})
        # Item uses the flat sprite, like vanilla amethyst buds.
        write_json(paths.MODELS / "item" / f"{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"groundzero:block/{name}"}})
        write_json(paths.ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": f"groundzero:item/{name}"}})
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
