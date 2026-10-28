"""The Construction set (DESIGN.md §22.2): cosmetic blocks for building Cold War sites.

Painted to the texture rules (DESIGN.md §4.3): each texture is the material library's tile (world-space noise that
wraps, a short ramp per material) with the block's own structure drawn over it on its ramp: the ridges of corrugated
sheet, the plates and rivets of a panel, the bars of a grating or a lattice, the seams of a radome's panels, the bags
of a sandbag wall. Rust is a material of its own laid over in patches with runs hanging from them, never a speckle.

- Chain-link Fence: galvanised wire in diamonds; Barbed Wire: a coil with its barbs (both open, drawn as iron bars are).
- Corrugated Steel (galvanised) and Rusted Corrugated Steel, with stairs and slabs: ridges every four texels.
- Steel Panel (grey paint) and Rusted Steel Panel: four riveted plates to a block.
- Steel Grating and its slab: bars every four texels with a frame, open between.
- I-Beam: a real I (web and flanges) set on any axis; Lattice Frame: corner posts, rails and a cross brace, open.
- Radome Panel, stairs and slab: white fibreglass in triangles, the seams between them.
- Floodlight: a dark housing with a glowing lens on a bracket; Sandbags and their slab; four warning signs
  (radiation, high voltage, keep out, toxic: a skull, as the biohazard mark cannot be read at this size).

    tools/.venv/Scripts/python.exe tools/assets/construction.py
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import numpy as np  # noqa: E402
from PIL import Image  # noqa: E402

from gb import materials as M  # noqa: E402
from gb import paths  # noqa: E402
from gb.model import write_json  # noqa: E402

N = 16
TEX = paths.TEXTURES / "block"
RUST_RAMP = M.ramp("#2a1d16", "#3a271c", "#4a3121", "#5a3b27", "#6a462e", "#7a5136", "#8a5d3f", "#996a49", "#a77854")
BURLAP_RAMP = M.ramp("#3e3220", "#524229", "#675433", "#7c663e", "#91794a", "#a58c58", "#b89f68", "#c9b17b")
FLAT_YELLOW = (226, 178, 38)
FLAT_ORANGE = (222, 112, 36)
FLAT_RED = (178, 38, 30)
FLAT_BLACK = (24, 24, 26)
FLAT_WHITE = (232, 230, 222)


# --- helpers ---------------------------------------------------------------------------------------------------------------------
def save(name: str, img: np.ndarray) -> None:
    TEX.mkdir(parents=True, exist_ok=True)
    if img.shape[-1] == 3:
        img = np.concatenate([img, np.full(img.shape[:2] + (1,), 255, np.uint8)], axis=-1)
    Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGBA").save(TEX / f"{name}.png")


def shade(img: np.ndarray, k: np.ndarray) -> None:
    """Multiplies each texel's colour by k (an array the texture's size)."""
    rgb = img[..., :3].astype(np.float32) * k[..., None]
    img[..., :3] = np.clip(rgb, 0, 255).astype(np.uint8)


def rust_over(img: np.ndarray, seed: int, cover: float, runs: int = 3, scale: float = 6.0) -> np.ndarray:
    """Rust laid over a texture in patches covering {cover} of it (wrapping noise, so they run on into the next block),
    the crust darker at a patch's heart, with rust runs hanging a few texels below; returns where it lies."""
    f = M.tile_face()
    n = M.noise(f, scale, seed, 3)
    patch = n > np.quantile(n, 1 - cover)
    heart = n > np.quantile(n, 1 - cover * 0.35)
    rust = M.tile(M.Material(ramp=RUST_RAMP, tone=0.55, mottle=0.08, grain=0.06, dither=0.7, despeckle=False), seed + 7)
    out = img.copy()
    # The steel round the rust is weathered too: duller and a little warmer, more so the nearer the rust.
    fringe = (n > np.quantile(n, 1 - min(1.0, cover * 1.6))) & ~patch
    near = (n > np.quantile(n, 1 - min(1.0, cover * 1.25))) & ~patch
    warm = np.array([118, 84, 62], np.float32)
    rgb = out[..., :3].astype(np.float32)
    rgb *= 0.9
    for mask, k in ((fringe, 0.25), (near, 0.45)):
        rgb[mask] = rgb[mask] * (1 - k) + warm * k
    out[..., :3] = np.clip(rgb, 0, 255).astype(np.uint8)
    out[patch] = rust[patch]
    shade(out, np.where(heart, 0.86, 1.0))
    # Runs: below a patch's lower edge, a fading brown stain down a few texels (it wraps, as the block tiles).
    stain = np.zeros((N, N), np.float32)
    for x in range(N):
        for y in range(N):
            if patch[y, x] and not patch[(y + 1) % N, x] and (x * 5 + y * 3 + seed) % 3:
                for k in range(1, runs + 1 + (x * 7 + y * 3 + seed) % 3):
                    stain[(y + k) % N, x] = max(stain[(y + k) % N, x], 1 - k / (runs + 3))
    tint = np.array(M.RUST_MID, np.float32)
    rgb = out[..., :3].astype(np.float32)
    s = (stain * ~patch)[..., None] * 0.5
    out[..., :3] = np.clip(rgb * (1 - s) + tint * s, 0, 255).astype(np.uint8)
    img[:] = out
    return patch


def bevel_mask(img: np.ndarray, mask: np.ndarray) -> None:
    M.bevel(img, mask, light=1.2, dark=0.7, add=5)


def rgba(rgb: np.ndarray, mask: np.ndarray) -> np.ndarray:
    """An open texture: the colour where the mask is, clear elsewhere."""
    out = np.zeros((N, N, 4), np.uint8)
    out[..., :3] = rgb[..., :3]
    out[..., 3] = np.where(mask, 255, 0)
    return out


# --- sheeting and plate --------------------------------------------------------------------------------------------------------------
#: Corrugation: a ridge every four texels, lit on its left flank, its trough in shadow (light from the upper left).
RIDGE = np.array([1.17, 1.05, 0.83, 0.95], np.float32)


def corrugated(rusted: bool) -> np.ndarray:
    img = M.tile(M.galvanized(tone=0.58, mottle=0.04, streaks=0.05), seed=11)
    if rusted:
        rust_over(img, seed=23, cover=0.62)
    col = np.arange(N) % 4
    shade(img, np.tile(RIDGE[col][None, :], (N, 1)))
    return img


def panel(rusted: bool) -> np.ndarray:
    """Two plates to a block, one over the other, lapped at a groove; a row of rivets along each plate's top."""
    f = M.tile_face()
    img = M.tile(M.painted(M.GREY_PAINT_RAMP, tone=0.56, wear=0.0, mottle=0.035, streaks=0.08), seed=31)
    if rusted:
        rust_over(img, seed=37, cover=0.55, runs=4)
    M.seams(f, img, every=8, axis="v")
    col, row = M.grid_xy()
    rivet = (row % 8 == 2) & (col % 4 == 1)
    M.lighten(img, rivet, 1.3, 12)
    below = np.roll(rivet, 1, axis=0) & ~rivet
    M.darken(img, below, 0.72)
    return img


def grating() -> np.ndarray:
    """Bars every four texels both ways, open between; the block's border a heavier frame."""
    img = M.tile(M.galvanized(tone=0.52, mottle=0.04), seed=41)
    col, row = M.grid_xy()
    bar = (col % 4 == 0) | (row % 4 == 0)
    frame = (col == 0) | (col == N - 1) | (row == 0) | (row == N - 1)
    solid = bar | frame
    bevel_mask(img, solid)
    return rgba(img, solid)


def chain_link() -> tuple[np.ndarray, np.ndarray]:
    """Wire in diamonds (along both diagonals every four texels); the edge, a galvanised post."""
    img = M.tile(M.galvanized(tone=0.6, mottle=0.05), seed=51)
    col, row = M.grid_xy()
    wire = ((col + row) % 8 == 0) | ((col - row) % 8 == 0)
    shade(img, np.where((col + row) % 8 == 0, 1.08, 0.9))       # the two directions of wire catch the light apart
    mesh = rgba(img, wire)
    post = M.tile(M.galvanized(tone=0.5), seed=53)
    shade(post, np.tile(np.array([0.8, 1.15, 1.1, 1.0, 0.95, 0.9, 0.85, 0.8] * 2, np.float32)[None, :], (N, 1)))
    return mesh, post


def barbed_wire() -> np.ndarray:
    """Concertina seen from the side: rings of wire overlapping along it (one every eight texels), barbs on them."""
    img = M.tile(M.steel(tone=0.55, brushed=0.0), seed=61)
    wire = np.zeros((N, N), bool)
    for cx in (3.5, 11.5):
        for t in np.linspace(0, 2 * np.pi, 160, endpoint=False):
            x, y = cx + 5.2 * np.cos(t), 8.0 + 5.6 * np.sin(t)
            wire[int(np.clip(round(y - 0.5), 0, N - 1)), int(round(x - 0.5)) % N] = True
    for x0, y0 in ((3, 2), (11, 2), (8, 8), (0, 8), (3, 13), (11, 13)):
        for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            wire[(y0 + dy) % N, (x0 + dx) % N] = True
    col, row = M.grid_xy()
    shade(img, np.where(row < 8, 1.1, 0.86))
    return rgba(img, wire)


def lattice() -> np.ndarray:
    """A face of an open braced cube: corner posts two texels wide, a rail along the bottom, a cross brace; open between.
    Stacked, the posts run on and each block's rail sits on the one below's top, so a tower reads as one."""
    img = M.tile(M.galvanized(tone=0.55, mottle=0.04), seed=71)
    col, row = M.grid_xy()
    posts = (col <= 1) | (col >= N - 2)
    rail = row >= N - 2
    diag = (np.abs(col - row) <= 0) | (np.abs(col - (N - 1 - row)) <= 0)
    solid = posts | rail | (diag & (row >= 1) & (row <= N - 2))
    bevel_mask(img, solid)
    shade(img, np.where(posts, 0.92, 1.0))
    return rgba(img, solid)


def radome() -> np.ndarray:
    """White fibreglass in triangles: a seam along the block's top and one down its diagonal, the two triangles a shade
    apart (the dome is faceted), a lit lip beside each seam."""
    img = M.tile(M.white_paint(tone=0.78, mottle=0.02, grime=0.08), seed=81)
    col, row = M.grid_xy()
    upper = col > row
    shade(img, np.where(upper, 1.0, 0.95))
    seam = (row == 0) | (col == row)
    lip = ((row == 1) | (col == row - 1)) & ~seam
    M.darken(img, seam, 0.8)
    M.lighten(img, lip, 1.04, 4)
    return img


def sandbags() -> np.ndarray:
    """Bags of hessian in a running bond: rows four high, bags eight long, each plump (lit top left, shaded bottom right),
    the end of each bag folded under, dark gaps between."""
    base = M.tile(M.Material(ramp=BURLAP_RAMP, tone=0.6, mottle=0.06, grain=0.08, dither=0.9, despeckle=False), seed=91)
    col, row = M.grid_xy()
    shift = np.where((row // 4) % 2 == 1, 4, 0)
    u = (col + shift) % 8                                      # across a bag
    v = row % 4                                                # down a bag
    k = np.ones((N, N), np.float32)
    k += np.where(v == 0, 0.14, 0.0) + np.where(v == 3, -0.22, 0.0)
    k += np.where(u == 1, 0.08, 0.0) + np.where(u == 6, -0.1, 0.0)
    gap = (u == 7) | ((v == 3) & ((u == 0) | (u == 6)))
    k = np.where(gap, 0.55, k)
    fold = (u == 0) & (v == 1)
    k = np.where(fold, 0.8, k)
    img = base.copy()
    shade(img, k)
    return img


# --- signs ------------------------------------------------------------------------------------------------------------------------------
def sign_plate(field, border=FLAT_BLACK) -> np.ndarray:
    """A 16x16 texture whose 12x12 middle is the sign's face: an enamelled field with a border, a little worn."""
    img = np.zeros((N, N, 4), np.uint8)
    f = M.tile_face()
    base = M.tile(M.painted(M.GREY_PAINT_RAMP, tone=0.45, wear=0.0), seed=101)
    img[...] = base
    face = np.zeros((N, N, 3), np.float32)
    face[...] = field
    grain = M.noise(f, 1.2, 103, 2)
    face *= (0.94 + 0.08 * grain)[..., None]
    col, row = M.grid_xy()
    inside = (col >= 2) & (col <= 13) & (row >= 2) & (row <= 13)
    edge = inside & ((col == 2) | (col == 13) | (row == 2) | (row == 13))
    img[inside, :3] = face[inside].astype(np.uint8)
    if border is not None:
        img[edge, :3] = border
    chips = inside & (M.noise(f, 3.0, 107, 1) > 0.93)
    img[chips, :3] = (np.array(M.RUST_MID) * 0.9).astype(np.uint8)
    return img


def put(img: np.ndarray, pixels: list[str], colour, x0: int = 2, y0: int = 2) -> None:
    """Draws a mark from rows of text ('#' set, anything else not) at a corner of the sign's face."""
    for dy, line in enumerate(pixels):
        for dx, ch in enumerate(line):
            if ch == "#":
                img[y0 + dy, x0 + dx, :3] = colour


RADIATION = ["            ",
             "            ",
             "  ###  ###  ",
             "  ###  ###  ",
             "   ##  ##   ",
             "     ##     ",
             "     ##     ",
             "            ",
             "    ####    ",
             "    ####    ",
             "     ##     ",
             "            "]
BOLT = ["            ",
        "      ##    ",
        "     ##     ",
        "    ##      ",
        "   ######   ",
        "      ##    ",
        "     ##     ",
        "    ##      ",
        "   ##       ",
        "  ##        ",
        "            ",
        "            "]
NO_ENTRY = ["            ",
            "    ####    ",
            "  ########  ",
            "  ########  ",
            " ########## ",
            " #        # ",
            " #        # ",
            " ########## ",
            "  ########  ",
            "  ########  ",
            "    ####    ",
            "            "]
SKULL = ["            ",
         "            ",
         "   ######   ",
         "  ########  ",
         "  #  ##  #  ",
         "  #  ##  #  ",
         "  ########  ",
         "   ##  ##   ",
         "   ######   ",
         "   # ## #   ",
         "            ",
         "            "]


def symbol(inside, img: np.ndarray, colour, size: int = 12, x0: int = 2, y0: int = 2) -> None:
    """Draws a mark given as a test on points of the sign's face (x right, y up, from its middle, in texels), sampled
    four times a texel each way: a texel is set where most of its samples are."""
    k = 4
    g = (np.arange(size * k) + 0.5) / k - size / 2
    xx, yy = np.meshgrid(g, -g)
    hit = inside(xx, yy).reshape(size, k, size, k).mean(axis=(1, 3)) > 0.5
    ys, xs = np.nonzero(hit)
    img[ys + y0, xs + x0, :3] = colour


def trefoil(x, y):
    """The radiation trefoil: a dot, and three blades of 60 degrees (up left, up right, down) round it."""
    r = np.hypot(x, y)
    a = np.degrees(np.arctan2(y, x)) % 360
    blade = np.zeros_like(r, bool)
    for c in (30, 150, 270):
        d = np.abs((a - c + 180) % 360 - 180)
        blade |= (d <= 30) & (r >= 1.9) & (r <= 5.4)
    return blade | (r <= 1.2)


def signs() -> dict[str, np.ndarray]:
    out = {}
    s = sign_plate(FLAT_YELLOW)
    symbol(trefoil, s, FLAT_BLACK)
    out["radiation_sign"] = s
    s = sign_plate(FLAT_YELLOW)
    put(s, BOLT, FLAT_BLACK)
    out["high_voltage_sign"] = s
    s = sign_plate(FLAT_WHITE, border=FLAT_RED)
    put(s, NO_ENTRY, FLAT_RED)
    out["keep_out_sign"] = s
    s = sign_plate(FLAT_ORANGE)
    put(s, SKULL, FLAT_BLACK)
    out["toxic_sign"] = s
    return out


# --- shaped blocks ------------------------------------------------------------------------------------------------------------------
def beam() -> tuple[np.ndarray, np.ndarray]:
    """The girder's paint (grey, rust along its edges) and its end (the I in section, the cut steel bright)."""
    side = M.tile(M.painted(M.GREY_PAINT_RAMP, tone=0.5, wear=0.0, streaks=0.18, mottle=0.05), seed=111)
    rust_over(side, seed=115, cover=0.03, runs=3, scale=3.0)
    end = M.tile(M.steel(tone=0.6, brushed=0.0, mottle=0.02, grain=0.02), seed=113)
    return side, end


def floodlight() -> tuple[np.ndarray, np.ndarray]:
    """The housing (dark steel, cooling fins) and the lens (warm white, a grid of facets), drawn unshaded."""
    housing = M.tile(M.painted(M.ARMOUR_RAMP, tone=0.45, wear=0.2), seed=121)
    col, row = M.grid_xy()
    shade(housing, np.where(row % 3 == 0, 0.75, 1.0))
    lens = np.zeros((N, N, 4), np.uint8)
    lens[..., :3] = (255, 244, 205)
    lens[..., 3] = 255
    facet = (col % 4 == 0) | (row % 4 == 0)
    lens[facet, :3] = (236, 214, 150)
    return housing, lens


# --- models and states ---------------------------------------------------------------------------------------------------------------
def bid(name: str) -> str:
    return f"groundburst:block/{name}"


def model(name: str, data: dict) -> None:
    write_json(paths.MODELS / "block" / f"{name}.json", data)


def state(name: str, data: dict) -> None:
    write_json(paths.ASSETS / "blockstates" / f"{name}.json", data)


def item(name: str, model_id: str) -> None:
    write_json(paths.ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": model_id}})


def cube(name: str, texture: str | None = None, cutout: bool = False) -> None:
    m = {"parent": "minecraft:block/cube_all", "textures": {"all": bid(texture or name)}}
    if cutout:
        m["render_type"] = "minecraft:cutout"
    model(name, m)
    state(name, {"variants": {"": {"model": bid(name)}}})
    item(name, bid(name))


def vanilla_state(name: str) -> dict:
    return json.loads((paths.VANILLA_ASSETS / "blockstates" / f"{name}.json").read_text(encoding="utf-8"))


def renamed(data, old: str, new: str):
    """A vanilla blockstate with its model ids swapped for ours."""
    text = json.dumps(data).replace(f'"minecraft:block/{old}', f'"groundburst:block/{new}')
    return json.loads(text)


def stairs(name: str, texture: str) -> None:
    t = {"bottom": bid(texture), "top": bid(texture), "side": bid(texture)}
    for suffix, parent in (("", "stairs"), ("_inner", "inner_stairs"), ("_outer", "outer_stairs")):
        model(f"{name}{suffix}", {"parent": f"minecraft:block/{parent}", "textures": t})
    state(name, renamed(vanilla_state("stone_brick_stairs"), "stone_brick_stairs", name))
    item(name, bid(name))


def slab(name: str, texture: str, full: str, cutout: bool = False) -> None:
    t = {"bottom": bid(texture), "top": bid(texture), "side": bid(texture)}
    for suffix, parent in (("", "slab"), ("_top", "slab_top")):
        m = {"parent": f"minecraft:block/{parent}", "textures": t}
        if cutout:
            m["render_type"] = "minecraft:cutout"
        model(f"{name}{suffix}", m)
    data = renamed(vanilla_state("stone_brick_slab"), "stone_brick_slab", name)
    data = json.loads(json.dumps(data).replace(f'"groundburst:block/{name}_double"', f'"{bid(full)}"')
                      .replace('"minecraft:block/stone_bricks"', f'"{bid(full)}"'))
    state(name, data)
    item(name, bid(name))


def bars(name: str, bars_tex: str, edge_tex: str) -> None:
    """Joins its neighbours as iron bars do: vanilla's bar templates with our textures, its blockstate with our models."""
    for part in ("cap", "cap_alt", "post", "post_ends", "side", "side_alt"):
        model(f"{name}_{part}", {"parent": f"minecraft:block/template_bars_{part}",
                                 "textures": {"bars": bid(bars_tex), "edge": bid(edge_tex), "particle": bid(bars_tex)},
                                 "render_type": "minecraft:cutout"})
    state(name, renamed(vanilla_state("iron_bars"), "iron_bars", name))
    write_json(paths.MODELS / "item" / f"{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": bid(bars_tex)}})
    item(name, f"groundburst:item/{name}")


def i_beam() -> None:
    """Flanges two texels thick across the x faces of the post, the web between them; along y, turned for x and z."""
    face = {"texture": "#side"}
    def el(frm, to):
        return {"from": frm, "to": to, "faces": {d: dict(face) for d in ("north", "south", "east", "west")}
                | {"up": {"texture": "#end", "cullface": "up"}, "down": {"texture": "#end", "cullface": "down"}}}
    m = {"parent": "minecraft:block/block", "textures": {"side": bid("i_beam"), "end": bid("i_beam_end"), "particle": bid("i_beam")},
         "elements": [el([1, 0, 1], [15, 16, 3]), el([1, 0, 13], [15, 16, 15]), el([7, 0, 3], [9, 16, 13])]}
    model("i_beam", m)
    state("i_beam", {"variants": {"axis=y": {"model": bid("i_beam")},
                                  "axis=z": {"model": bid("i_beam"), "x": 90},
                                  "axis=x": {"model": bid("i_beam"), "x": 90, "y": 90}}})
    item("i_beam", bid("i_beam"))


def floodlight_model() -> None:
    """Facing north: the housing (its lens at z 6, glowing), the bracket back to the mount at z 16."""
    s = {"texture": "#housing"}
    m = {"parent": "minecraft:block/block",
         "textures": {"housing": bid("floodlight"), "lens": bid("floodlight_lens"), "particle": bid("floodlight")},
         "elements": [
             {"from": [3, 3, 6], "to": [13, 13, 13], "faces": {
                 "north": {"texture": "#lens", "uv": [3, 3, 13, 13]},
                 "south": dict(s), "east": dict(s), "west": dict(s), "up": dict(s), "down": dict(s)}},
             {"from": [3.5, 3.5, 5.5], "to": [12.5, 12.5, 6], "light_emission": 15, "shade": False,
              "faces": {"north": {"texture": "#lens", "uv": [3.5, 3.5, 12.5, 12.5]}}},
             {"from": [6, 6, 13], "to": [10, 10, 16], "faces": {d: dict(s) for d in ("east", "west", "up", "down", "south")}},
         ]}
    model("floodlight", m)
    rot = {"north": {}, "east": {"y": 90}, "south": {"y": 180}, "west": {"y": 270}, "up": {"x": 270}, "down": {"x": 90}}
    state("floodlight", {"variants": {f"facing={k}": {"model": bid("floodlight")} | v for k, v in rot.items()}})
    item("floodlight", bid("floodlight"))


def sign_model(name: str) -> None:
    """A thin plate on the wall to the south of a north-facing sign: its face 12x12 out from the wall."""
    edge = {"texture": "#back"}
    m = {"parent": "minecraft:block/block",
         "textures": {"front": bid(name), "back": bid("sign_back"), "particle": bid(name)},
         "elements": [{"from": [2, 2, 15], "to": [14, 14, 16], "faces": {
             "north": {"texture": "#front", "uv": [2, 2, 14, 14]},
             "south": {"texture": "#back", "uv": [2, 2, 14, 14], "cullface": "south"},
             "east": edge | {"uv": [0, 2, 1, 14]}, "west": edge | {"uv": [15, 2, 16, 14]},
             "up": edge | {"uv": [2, 15, 14, 16]}, "down": edge | {"uv": [2, 0, 14, 1]}}}]}
    model(name, m)
    rot = {"north": 0, "east": 90, "south": 180, "west": 270}
    state(name, {"variants": {f"facing={k}": {"model": bid(name)} | ({"y": v} if v else {}) for k, v in rot.items()}})
    item(name, bid(name))


def lattice_model() -> None:
    model("lattice_frame", {"parent": "minecraft:block/cube_all", "textures": {"all": bid("lattice_frame")},
                            "render_type": "minecraft:cutout"})
    state("lattice_frame", {"variants": {"": {"model": bid("lattice_frame")}}})
    item("lattice_frame", bid("lattice_frame"))


def main() -> None:
    save("corrugated_steel", corrugated(False))
    save("rusted_corrugated_steel", corrugated(True))
    save("steel_panel", panel(False))
    save("rusted_steel_panel", panel(True))
    save("steel_grating", grating())
    mesh, post = chain_link()
    save("chain_link_fence", mesh)
    save("chain_link_fence_post", post)
    save("barbed_wire", barbed_wire())
    save("lattice_frame", lattice())
    save("radome_panel", radome())
    save("sandbags", sandbags())
    for name, img in signs().items():
        save(name, img)
    save("sign_back", M.tile(M.painted(M.GREY_PAINT_RAMP, tone=0.42, wear=0.1), seed=131))
    side, end = beam()
    save("i_beam", side)
    save("i_beam_end", end)
    housing, lens = floodlight()
    save("floodlight", housing)
    save("floodlight_lens", lens)

    for name in ("corrugated_steel", "rusted_corrugated_steel", "steel_panel", "rusted_steel_panel", "radome_panel", "sandbags"):
        cube(name)
    cube("steel_grating", cutout=True)
    for base in ("corrugated_steel", "rusted_corrugated_steel", "radome_panel"):
        stairs(f"{base}_stairs", base)
    slab("corrugated_steel_slab", "corrugated_steel", "corrugated_steel")
    slab("rusted_corrugated_steel_slab", "rusted_corrugated_steel", "rusted_corrugated_steel")
    slab("radome_panel_slab", "radome_panel", "radome_panel")
    slab("sandbag_slab", "sandbags", "sandbags")
    slab("steel_grating_slab", "steel_grating", "steel_grating", cutout=True)
    bars("chain_link_fence", "chain_link_fence", "chain_link_fence_post")
    bars("barbed_wire", "barbed_wire", "barbed_wire")
    i_beam()
    lattice_model()
    floodlight_model()
    for name in ("radiation_sign", "high_voltage_sign", "keep_out_sign", "toxic_sign"):
        sign_model(name)
    print("construction set written")


if __name__ == "__main__":
    main()
