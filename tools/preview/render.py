"""Software preview renderer for block/item model JSON (and, via geo.py, GeckoLib models).

Renders a model from several angles into a labelled contact sheet so models can be reviewed as images:
    tools\\.venv\\Scripts\\python.exe tools\\preview\\render.py groundzero:block/fission_bomb
    tools\\.venv\\Scripts\\python.exe tools\\preview\\render.py path\\to\\model.json --out sheet.png

Matches vanilla where it matters for judging a model: face/UV orientation, element rotations (single-axis or
XYZ Euler), uv rotation, directional face shading (up 1.0, N/S 0.8, E/W 0.6, down 0.5), cutout alpha, and
light_emission (unshaded). Oversized models are framed automatically.
"""
from __future__ import annotations

import argparse
import json
import math
import sys
from dataclasses import dataclass
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from gz import paths  # noqa: E402

SHADE = {"up": 1.0, "down": 0.5, "north": 0.8, "south": 0.8, "east": 0.6, "west": 0.6}
NORMALS = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}


# --- model loading ------------------------------------------------------------------------------------------------

def _split_id(ident: str) -> tuple[str, str]:
    if ":" in ident:
        ns, p = ident.split(":", 1)
    else:
        ns, p = "minecraft", ident
    return ns, p


def _asset_root(ns: str) -> Path:
    return paths.ASSETS if ns == paths.MODID else paths.VANILLA_ASSETS.parent / ns


def model_file(ident: str) -> Path:
    ns, p = _split_id(ident)
    return _asset_root(ns) / "models" / f"{p}.json"


def texture_file(ident: str) -> Path:
    ns, p = _split_id(ident)
    return _asset_root(ns) / "textures" / f"{p}.png"


def load_model(src: str | Path) -> dict:
    """Loads a model and merges its parent chain (textures, elements, display)."""
    path = Path(src) if isinstance(src, Path) or str(src).endswith(".json") else model_file(str(src))
    data = json.loads(path.read_text(encoding="utf-8"))
    merged = {"textures": {}, "elements": None, "display": {}, "texture_size": None}
    chain = [data]
    while "parent" in chain[-1]:
        parent = chain[-1]["parent"]
        if parent.startswith("builtin/"):
            break
        pf = model_file(parent)
        if not pf.exists():
            raise FileNotFoundError(f"parent model {parent} not found at {pf}")
        chain.append(json.loads(pf.read_text(encoding="utf-8")))
    for m in reversed(chain):
        merged["textures"].update(m.get("textures", {}))
        if m.get("elements") is not None:
            merged["elements"] = m["elements"]
        merged["display"].update(m.get("display", {}))
        if m.get("texture_size"):
            merged["texture_size"] = m["texture_size"]
    if merged["elements"] is None:
        raise ValueError(f"{src}: model has no elements (generated item models are not supported yet)")
    return merged


def resolve_texture(textures: dict, ref: str, depth: int = 0) -> str:
    if depth > 10:
        raise ValueError("texture reference loop")
    if ref.startswith("#"):
        return resolve_texture(textures, textures[ref[1:]], depth + 1)
    return ref


# --- geometry -------------------------------------------------------------------------------------------------------

@dataclass
class Quad:
    pos: np.ndarray        # (4, 3) model units, order TL, TR, BR, BL as seen from outside
    uv: np.ndarray         # (4, 2) texture pixel coords
    tex: np.ndarray        # (H, W, 4) uint8
    shade: float
    emissive: bool
    normal: np.ndarray
    directional: bool = True   # False for shade:false elements (no per-direction lighting in game)


def _face_corners(d: str, f: tuple, t: tuple) -> list[tuple[float, float, float]]:
    (x0, y0, z0), (x1, y1, z1) = f, t
    return {
        "north": [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
        "south": [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
        "west": [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
        "east": [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
        "up": [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
        "down": [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)],
    }[d]


def _auto_uv(d: str, f: tuple, t: tuple) -> list[float]:
    (x0, y0, z0), (x1, y1, z1) = f, t
    return {
        "north": [16 - x1, 16 - y1, 16 - x0, 16 - y0], "south": [x0, 16 - y1, x1, 16 - y0],
        "west": [z0, 16 - y1, z1, 16 - y0], "east": [16 - z1, 16 - y1, 16 - z0, 16 - y0],
        "up": [x0, z0, x1, z1], "down": [x0, 16 - z1, x1, 16 - z0],
    }[d]


def _rx(a: float) -> np.ndarray:
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


def _ry(a: float) -> np.ndarray:
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


def _rz(a: float) -> np.ndarray:
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def element_rotation(rot: dict) -> tuple[np.ndarray, np.ndarray]:
    origin = np.array(rot.get("origin", [8, 8, 8]), dtype=float)
    if "axis" in rot:
        a = math.radians(rot.get("angle", 0))
        m = {"x": _rx, "y": _ry, "z": _rz}[rot["axis"]](a)
    else:
        # JOML rotationZYX(z, y, x) == Rz * Ry * Rx
        m = _rz(math.radians(rot.get("z", 0))) @ _ry(math.radians(rot.get("y", 0))) @ _rx(math.radians(rot.get("x", 0)))
    if rot.get("rescale"):
        scale = []
        for axis in np.eye(3):
            v = m @ axis
            scale.append(1.0 / max(abs(v)))
        m = m @ np.diag(scale)
    return m, origin


_tex_cache: dict[str, np.ndarray] = {}


def _load_tex(ident: str) -> np.ndarray:
    if ident not in _tex_cache:
        p = texture_file(ident)
        if not p.exists():
            # Magenta/black missing texture, like the game.
            t = np.zeros((16, 16, 4), np.uint8)
            t[:, :] = (255, 0, 255, 255)
            t[8:, :8] = (0, 0, 0, 255)
            t[:8, 8:] = (0, 0, 0, 255)
            _tex_cache[ident] = t
        else:
            img = Image.open(p).convert("RGBA")
            # Animated textures (a strip with an .mcmeta, as the game requires): use the first frame. A tall texture
            # without one is an ordinary texture (large GeckoLib models use 256x512).
            meta = Path(str(p) + ".mcmeta")
            if meta.exists():
                anim = json.loads(meta.read_text(encoding="utf-8")).get("animation", {})
                fh = anim.get("height", anim.get("width", img.width))   # frames are square unless the .mcmeta says
                if img.height > fh and img.height % fh == 0:
                    img = img.crop((0, 0, img.width, fh))
            _tex_cache[ident] = np.array(img, np.uint8)
    return _tex_cache[ident]


def build_quads(model: dict) -> list[Quad]:
    quads: list[Quad] = []
    for el in model["elements"]:
        f, t = tuple(el["from"]), tuple(el["to"])
        rot = element_rotation(el["rotation"]) if el.get("rotation") else None
        shade_on = el.get("shade", True)
        emissive = el.get("light_emission", 0) >= 10
        for d, face in el["faces"].items():
            tex = _load_tex(resolve_texture(model["textures"], face["texture"]))
            th, tw = tex.shape[:2]
            uv = face.get("uv") or _auto_uv(d, f, t)
            u0, v0, u1, v1 = [c / 16.0 for c in uv]
            corners_uv = [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]
            r = (face.get("rotation", 0) // 90) % 4
            corners_uv = corners_uv[r:] + corners_uv[:r]
            pos = np.array(_face_corners(d, f, t), dtype=float)
            normal = np.array(NORMALS[d], dtype=float)
            if rot is not None:
                m, o = rot
                pos = (m @ (pos - o).T).T + o
                normal = m @ normal
            uvp = np.array([(u * tw, v * th) for u, v in corners_uv], dtype=float)
            quads.append(Quad(pos, uvp, tex, SHADE[d] if shade_on and not emissive else 1.0, emissive, normal, shade_on and not emissive))
    return quads


# --- rasteriser ---------------------------------------------------------------------------------------------------------

def view_matrix(pitch: float, yaw: float) -> np.ndarray:
    """Camera looks down -Z of view space; pitch tilts to see the top."""
    return _rx(math.radians(pitch)) @ _ry(math.radians(yaw))


def render(quads: list[Quad], size: int, pitch: float, yaw: float, center: np.ndarray, extent: float,
           bg: tuple[int, int, int, int] = (0, 0, 0, 0), ssaa: int = 2, light_dir: np.ndarray | None = None) -> Image.Image:
    S = size * ssaa
    scale = S / extent
    rot = view_matrix(pitch, yaw)
    color = np.zeros((S, S, 4), np.float32)
    color[:, :] = bg
    depth = np.full((S, S), -1e9, np.float32)
    glow = np.zeros((S, S, 3), np.float32)
    for q in quads:
        vp = (rot @ (q.pos - center).T).T
        n = rot @ q.normal
        if n[2] <= 1e-6:      # back-face cull (faces point away from the camera)
            continue
        sx = vp[:, 0] * scale + S / 2
        sy = -vp[:, 1] * scale + S / 2
        sz = vp[:, 2]
        for tri in ((0, 1, 2), (0, 2, 3)):
            _raster_tri(color, depth, glow, sx[list(tri)], sy[list(tri)], sz[list(tri)], q.uv[list(tri)], q, light_dir, n)
    img = Image.fromarray(np.clip(color, 0, 255).astype(np.uint8), "RGBA")
    if ssaa > 1:
        img = img.resize((size, size), Image.BOX)
    return img


def _raster_tri(color, depth, glow, xs, ys, zs, uvs, q: Quad, light_dir, n) -> None:
    H, W = depth.shape
    x0, x1 = int(max(0, math.floor(xs.min()))), int(min(W - 1, math.ceil(xs.max())))
    y0, y1 = int(max(0, math.floor(ys.min()))), int(min(H - 1, math.ceil(ys.max())))
    if x1 < x0 or y1 < y0:
        return
    px, py = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
    (ax, bx, cx), (ay, by, cy) = xs, ys
    det = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
    if abs(det) < 1e-9:
        return
    w0 = ((by - cy) * (px - cx) + (cx - bx) * (py - cy)) / det
    w1 = ((cy - ay) * (px - cx) + (ax - cx) * (py - cy)) / det
    w2 = 1 - w0 - w1
    eps = -1e-6
    inside = (w0 >= eps) & (w1 >= eps) & (w2 >= eps)
    if not inside.any():
        return
    z = w0 * zs[0] + w1 * zs[1] + w2 * zs[2]
    u = w0 * uvs[0, 0] + w1 * uvs[1, 0] + w2 * uvs[2, 0]
    v = w0 * uvs[0, 1] + w1 * uvs[1, 1] + w2 * uvs[2, 1]
    th, tw = q.tex.shape[:2]
    ui = np.clip(np.floor(u).astype(int), 0, tw - 1)
    vi = np.clip(np.floor(v).astype(int), 0, th - 1)
    texel = q.tex[vi, ui].astype(np.float32)
    region_depth = depth[y0:y1 + 1, x0:x1 + 1]
    # Like the game (LEQUAL), a later coplanar layer draws over an earlier one: overlays such as ore layers
    # sit exactly on the base face.
    mask = inside & (texel[..., 3] > 25) & (z >= region_depth - 1e-3)
    if not mask.any():
        return
    shade = q.shade
    if light_dir is not None and q.directional:
        shade *= 0.75 + 0.25 * max(0.0, float(np.dot(n / (np.linalg.norm(n) + 1e-9), light_dir)))
    rgb = texel[..., :3] * shade
    region = color[y0:y1 + 1, x0:x1 + 1]
    alpha = texel[..., 3:4] / 255.0
    out_rgb = rgb * alpha + region[..., :3] * (1 - alpha)
    region[..., :3] = np.where(mask[..., None], out_rgb, region[..., :3])
    region[..., 3] = np.where(mask, 255, region[..., 3])
    region_depth[mask] = z[mask]
    if q.emissive:
        g = glow[y0:y1 + 1, x0:x1 + 1]
        g[mask] = texel[..., :3][mask]


def model_bounds(quads: list[Quad]) -> tuple[np.ndarray, float]:
    pts = np.concatenate([q.pos for q in quads])
    lo, hi = pts.min(axis=0), pts.max(axis=0)
    center = (lo + hi) / 2
    extent = float(np.linalg.norm(hi - lo)) * 1.08
    return center, max(extent, 1.0)


# --- contact sheet ------------------------------------------------------------------------------------------------------

# Model convention: the FRONT of every model is its north (-Z) face, as in vanilla (furnaces, etc.).
VIEWS = [
    ("iso front (north + east)", 30, 225),
    ("iso back (south + west)", 30, 45),
    ("front (north)", 0, 180),
    ("east side", 0, -90),
    ("back (south)", 0, 0),
    ("west side", 0, 90),
    ("top", 90, 180),
    ("low angle (front, below)", -25, 200),
]


def _checker(size: int, a=(52, 56, 62), b=(60, 64, 70), cell: int = 16) -> Image.Image:
    img = Image.new("RGB", (size, size), a)
    d = ImageDraw.Draw(img)
    for y in range(0, size, cell):
        for x in range(0, size, cell):
            if (x // cell + y // cell) % 2:
                d.rectangle([x, y, x + cell - 1, y + cell - 1], fill=b)
    return img


def gui_icon(model: dict, quads: list[Quad], px: int = 32) -> Image.Image:
    """Approximates the inventory icon at true pixel size using the model's gui display transform."""
    disp = model["display"].get("gui", {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.625] * 3})
    rx, ry, rz = disp.get("rotation", [0, 0, 0])
    s = disp.get("scale", [1, 1, 1])
    tr = disp.get("translation", [0, 0, 0])
    m = _rx(math.radians(rx)) @ _ry(math.radians(ry)) @ _rz(math.radians(rz))
    tq = []
    for q in quads:
        p = (q.pos - 8.0) * np.array(s)
        p = (m @ p.T).T + np.array(tr)
        tq.append(Quad(p, q.uv, q.tex, q.shade, q.emissive, m @ (q.normal * np.sign(s)), q.directional))
    # GUI slot shows a 16x16 unit area; camera looks down -Z.
    return render(tq, px, 0, 0, np.zeros(3), 16.0, ssaa=1)


def contact_sheet(src: str | Path, out: Path | None = None, cell: int = 320, title: str | None = None) -> Path:
    model = load_model(src)
    quads = build_quads(model)
    center, extent = model_bounds(quads)
    light = np.array([0.4, 0.8, 0.45])
    light = light / np.linalg.norm(light)
    cols = 4
    tiles = []
    for label, pitch, yaw in VIEWS:
        tile = _checker(cell).convert("RGBA")
        img = render(quads, cell, pitch, yaw, center, extent, light_dir=light)
        tile.alpha_composite(img)
        tiles.append((label, tile))
    # Inventory icon at true size (x1) and enlarged (x8) to judge readability.
    icon = gui_icon(model, quads, 16)
    big = icon.resize((128, 128), Image.NEAREST)
    icon_tile = _checker(cell, (139, 139, 139), (139, 139, 139)).convert("RGBA")
    slot = Image.new("RGBA", (136, 136), (55, 55, 55, 255))
    slot.alpha_composite(big, (4, 4))
    icon_tile.alpha_composite(slot, ((cell - 136) // 2, (cell - 136) // 2 - 20))
    icon_tile.alpha_composite(icon, ((cell - 16) // 2, cell - 40))
    tiles.append(("inventory icon (x8 / x1)", icon_tile))

    rows = math.ceil(len(tiles) / cols)
    header = 34
    sheet = Image.new("RGBA", (cols * cell, header + rows * (cell + 18)), (28, 30, 34, 255))
    d = ImageDraw.Draw(sheet)
    name = title or str(src)
    lo = np.concatenate([q.pos for q in quads]).min(axis=0)
    hi = np.concatenate([q.pos for q in quads]).max(axis=0)
    dims = hi - lo
    d.text((10, 10), f"{name}    {len(model['elements'])} elements    size {dims[0]:.1f} x {dims[1]:.1f} x {dims[2]:.1f} px "
                     f"({dims[0] / 16:.2f} x {dims[1] / 16:.2f} x {dims[2] / 16:.2f} blocks)", fill=(230, 230, 230, 255))
    for i, (label, tile) in enumerate(tiles):
        x, y = (i % cols) * cell, header + (i // cols) * (cell + 18)
        sheet.alpha_composite(tile, (x, y))
        d.text((x + 6, y + cell + 3), label, fill=(200, 200, 200, 255))
    if out is None:
        safe = str(src).replace(":", "_").replace("/", "_").replace("\\", "_")
        out = paths.PREVIEWS / f"{Path(safe).stem}.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    sheet.convert("RGB").save(out)
    return out


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("model", nargs="+", help="model id (groundzero:block/x) or path to a model JSON")
    ap.add_argument("--out", type=Path)
    ap.add_argument("--cell", type=int, default=320)
    args = ap.parse_args()
    for m in args.model:
        print(contact_sheet(m, args.out, args.cell))


if __name__ == "__main__":
    main()
