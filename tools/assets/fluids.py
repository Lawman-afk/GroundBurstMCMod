"""Fluid textures, liquid block models/blockstates and bucket item definitions (DESIGN.md §6.4).

Three shared looks, each a pair of animated textures like vanilla water (16x16 still frames, 32x32 flowing frames),
drawn in greys and tinted per fluid by its fluid model, so one set serves every chemical:
  liquid - thin, translucent, slow interference ripples with bright caustic glints (acids, fuels, cryogenics)
  thick  - opaque gel/slurry: lazy rolling blobs and the odd bubble
  gas    - faint wisps (tanks, pipes and the GUI only; gases are never placed in the world)
Patterns are sums of waves with whole-number frequencies, so every frame tiles and the animation loops seamlessly.
Flowing frames stretch the same pattern along the flow and scroll it one whole period per loop.
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from gz import paths  # noqa: E402
from gz.model import write_json  # noqa: E402

FRAMES = 32
OUT = paths.TEXTURES / "block" / "fluid"

# Per look: grey levels (dark -> light) with alpha, frametime, and the wave recipe.
LOOKS = {
    "liquid": {"levels": [(150, 185), (166, 190), (182, 196), (198, 202), (214, 208), (236, 222)], "frametime": 2},
    "thick": {"levels": [(118, 255), (138, 255), (158, 255), (176, 255), (196, 255), (222, 255)], "frametime": 3},
    "gas": {"levels": [(190, 70), (205, 95), (218, 120), (232, 145), (244, 170), (255, 200)], "frametime": 2},
}
# (fx, fy, speed, weight): whole-number spatial frequencies (periods per tile) and time speed (cycles per loop).
WAVES = {
    "liquid": [(1, 2, 1, 1.0), (2, -1, -1, 0.8), (3, 1, 2, 0.45), (-1, 3, 1, 0.5), (4, 3, -2, 0.25)],
    "thick": [(1, 1, 1, 1.0), (-1, 2, 1, 0.7), (2, 1, -1, 0.5), (1, -3, 1, 0.3)],
    "gas": [(1, 1, 1, 1.0), (2, -1, 1, 0.6), (-1, 2, -1, 0.5), (3, 2, 1, 0.3)],
}


# Flowing: fast across the flow, slow along it, so features streak downward; the whole pattern scrolls one tile
# height per loop (whole periods along y keep the scroll seamless).
FLOW_WAVES = {
    "liquid": [(3, 1, 0, 1.0), (5, 1, 1, 0.6), (2, 2, -1, 0.5), (7, 1, 0, 0.25)],
    "thick": [(2, 1, 0, 1.0), (3, 1, 1, 0.6), (1, 2, 0, 0.4)],
    "gas": [(2, 1, 0, 1.0), (3, 1, 1, 0.5), (1, 2, -1, 0.4)],
}


def field(look: str, size: int, t: float, flowing: bool) -> np.ndarray:
    """Wave sum in [0, 1] over a size x size tile at loop phase t (0..1)."""
    y, x = np.mgrid[0:size, 0:size] / size
    if flowing:
        y = y - t
    v = np.zeros((size, size))
    for fx, fy, speed, w in (FLOW_WAVES if flowing else WAVES)[look]:
        v += w * np.sin(2 * np.pi * (fx * x + fy * y + speed * t))
    # A slow second octave breaks up the regularity of the waves.
    v += 0.35 * np.sin(2 * np.pi * (x + 2 * y + t)) * np.sin(2 * np.pi * (2 * x - y - t))
    v -= v.min()
    return v / max(v.max(), 1e-6)


def quantize(look: str, v: np.ndarray, rng: np.random.Generator) -> np.ndarray:
    """Banded greys: soft-edged shapes in a few flat steps, like vanilla water (no dithering)."""
    levels = LOOKS[look]["levels"]
    n = len(levels)
    h, w = v.shape
    idx = np.clip(np.floor(v ** 1.15 * n), 0, n - 1).astype(int)
    out = np.zeros((h, w, 4), np.uint8)
    for i, (g, a) in enumerate(levels):
        out[idx == i] = (g, g, g, a)
    return out


def glints(look: str, img: np.ndarray, v: np.ndarray) -> None:
    """Liquid: caustic glints on the brightest crests. Thick: a pale rim on blob tops. Gas: none."""
    if look == "liquid":
        img[v > 0.93] = (250, 250, 250, 230)
    elif look == "thick":
        img[(v > 0.86) & (v < 0.9)] = (232, 232, 232, 255)


def strip(look: str, size: int, flowing: bool) -> Image.Image:
    rng = np.random.default_rng(7)
    frames = []
    for k in range(FRAMES):
        t = k / FRAMES
        v = field(look, size, t, flowing)
        img = quantize(look, v, rng)
        glints(look, img, v)
        frames.append(img)
    return Image.fromarray(np.concatenate(frames, axis=0), "RGBA")


def write_textures() -> list[Path]:
    OUT.mkdir(parents=True, exist_ok=True)
    written = []
    for look, spec in LOOKS.items():
        for kind, size, flowing in (("still", 16, False), ("flow", 32, True)):
            p = OUT / f"{look}_{kind}.png"
            strip(look, size, flowing).save(p)
            p.with_suffix(".png.mcmeta").write_text(json.dumps({"animation": {"frametime": spec["frametime"]}}, indent=2) + "\n")
            written.append(p)
    return written


# (name, look, is_gas) — mirrors GZFluids.
FLUIDS = [
    ("sulfuric_acid", "liquid", False), ("nitric_acid", "liquid", False), ("synthetic_fuel", "liquid", False),
    ("liquid_rocket_fuel", "liquid", False), ("thermobaric_slurry", "thick", False), ("napalm", "thick", False),
    ("liquid_oxygen", "liquid", False), ("liquid_nitrogen", "liquid", False),
    ("deuterium", "gas", True), ("uranium_hexafluoride", "gas", True), ("tritium", "gas", True),
]


# Where the 26.x vanilla bucket's opening is (rows 2-5): the fluid shows through exactly these pixels, so the bucket
# looks like vanilla's water and lava buckets. Shape only; the bucket itself is vanilla's own texture.
BUCKET_OPENING = [
    "................",
    "................",
    ".....######.....",
    "...##########...",
    "...##########...",
    ".....######.....",
] + ["................"] * 10


def write_bucket_mask() -> Path:
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(BUCKET_OPENING):
        for x, c in enumerate(row):
            if c == "#":
                img.putpixel((x, y), (255, 255, 255, 255))
    p = paths.TEXTURES / "item" / "mask" / "bucket_fluid.png"
    p.parent.mkdir(parents=True, exist_ok=True)
    img.save(p)
    return p


def gas_cloud_models(name: str) -> None:
    """A released gas: a translucent layer hanging from the top of the block, 2 px deeper per part (1-8)."""
    variants = {}
    for v in range(1, 9):
        y0 = 16 - 2 * v
        faces = {}
        for d in ("north", "south", "east", "west"):
            faces[d] = {"uv": [0, 0, 16, 2 * v], "texture": "#gas", "tintindex": 0}
        faces["up"] = {"uv": [0, 0, 16, 16], "texture": "#gas", "tintindex": 0}
        faces["down"] = {"uv": [0, 0, 16, 16], "texture": "#gas", "tintindex": 0}
        write_json(paths.MODELS / "block" / f"{name}_{v}.json", {
            "render_type": "minecraft:translucent",
            "textures": {"gas": "groundzero:block/fluid/gas_still", "particle": "groundzero:block/fluid/gas_still"},
            "elements": [{"from": [0, y0, 0], "to": [16, 16, 16], "shade": False, "faces": faces}],
        })
        variants[f"volume={v}"] = {"model": f"groundzero:block/{name}_{v}"}
    write_json(paths.ASSETS / "blockstates" / f"{name}.json", {"variants": variants})


def write_blocks_and_buckets() -> None:
    write_bucket_mask()
    for name, look, gas in FLUIDS:
        if gas:
            gas_cloud_models(name)
        else:
            # Liquid blocks are drawn by the fluid renderer; the block model only names the particle texture.
            write_json(paths.MODELS / "block" / f"{name}.json", {"textures": {"particle": f"groundzero:block/fluid/{look}_still"}})
            write_json(paths.ASSETS / "blockstates" / f"{name}.json", {"variants": {"": {"model": f"groundzero:block/{name}"}}})
        # Buckets: vanilla's bucket with the fluid's own tinted texture showing through its opening; gas buckets are
        # flipped upside down (NeoForge flips any lighter-than-air fluid's container).
        write_json(paths.ASSETS / "items" / f"{name}_bucket.json", {"model": {
            "type": "neoforge:fluid_container",
            "fluid": f"groundzero:{name}",
            "flip_gas": True,
            "textures": {"base": "minecraft:item/bucket", "fluid": "groundzero:item/mask/bucket_fluid"},
        }})


# Tints for the review sheet (mirrors GZFluids colours).
TINTS = {
    "sulfuric_acid": 0xD8CE4A, "nitric_acid": 0xE8A050, "synthetic_fuel": 0x7A5420, "liquid_rocket_fuel": 0xD04838,
    "thermobaric_slurry": 0x8C949C, "napalm": 0xE2742A, "liquid_oxygen": 0x9CC4FF, "liquid_nitrogen": 0xD8E8F2,
    "deuterium": 0xA4BCFF, "uranium_hexafluoride": 0xC6D06A, "tritium": 0xB8F0D0,
}


def review() -> Path:
    """Each fluid's still frame 0 and flowing frame 0, tinted, over a checker so translucency shows."""
    cell = 96
    sheet = Image.new("RGBA", (len(FLUIDS) * (cell + 8), cell * 2 + 8), (40, 42, 46, 255))
    yy, xx = np.indices((cell, cell))
    checker = (yy // 12 + xx // 12) % 2
    bg = Image.fromarray(np.where(checker[..., None] == 1, [90, 90, 90, 255], [60, 60, 60, 255]).astype(np.uint8), "RGBA")
    for i, (name, look, _) in enumerate(FLUIDS):
        c = TINTS[name]
        tint = np.array([(c >> 16) & 255, (c >> 8) & 255, c & 255]) / 255
        for row, (kind, size) in enumerate((("still", 16), ("flow", 32))):
            frame = np.array(Image.open(OUT / f"{look}_{kind}.png"))[:size].astype(float)
            frame[..., :3] *= tint
            im = Image.fromarray(frame.astype(np.uint8), "RGBA").resize((cell, cell), Image.NEAREST)
            tile = bg.copy()
            tile.alpha_composite(im)
            sheet.paste(tile, (i * (cell + 8), row * (cell + 8)))
    p = paths.ROOT / "tools" / "out" / "previews" / "fluids_review.png"
    sheet.save(p)
    return p


if __name__ == "__main__":
    for p in write_textures():
        print(p)
    write_blocks_and_buckets()
    print(review())
