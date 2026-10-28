"""The project logo (the CurseForge page's icon): one of the mod's own models, drawn from its in-game model and
textures by the preview renderer, tilted across a 400 x 400 square over a plain sky.

Two subjects: the DF-5 that the Nuclear Missile Silo fires (an XL missile, silver with red bands) laid corner to
corner on a dusk sky, the logo; and the Tsar warhead as it sits in the world, leaning, on a day sky.

    tools\\.venv\\Scripts\\python.exe tools\\assets\\logo.py [--subject missile|tsar] [--out path] [--review]
        writes docs/branding/logo.png (the missile unless --subject says otherwise); --review writes a sheet of both
        at full size and at the sizes a listing shows them (tools/out/previews/logo_review.png)
"""
from __future__ import annotations

import argparse
import math
import sys
from dataclasses import dataclass
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "preview"))
from gz import paths  # noqa: E402
import render as R  # noqa: E402

SIZE = 400
OUT = paths.ROOT / "docs" / "branding" / "logo.png"
#: Light from the upper left, towards the viewer (view space: x right, y up, z towards the camera).
LIGHT = np.array([-0.45, 0.75, 0.55])


@dataclass(frozen=True)
class Subject:
    model: str
    pitch: float          # degrees: tilts the view to see a little of the top
    yaw: float            # degrees: turns the model to show its markings
    roll: float           # degrees: leans the picture (negative leans the top to the right)
    margin: float         # the space left round the model, as a share of the side
    sky: str


SUBJECTS = {
    "missile": Subject("groundzero:item/missile/nuclear_missile_warhead", 12, 210, -45, 0.08, "dusk"),
    "tsar": Subject("groundzero:block/tsar_warhead", 22, 215, -32, 0.07, "day"),
}


def posed(s: Subject) -> list[R.Quad]:
    """The model's faces turned to the view: centred on the model's middle, then pitch and yaw as the renderer would
    turn them, then rolled about the line of sight (the renderer has no roll), so it can be drawn with no turn."""
    quads = R.build_quads(R.load_model(s.model))
    pts = np.concatenate([q.pos for q in quads])
    middle = (pts.min(axis=0) + pts.max(axis=0)) / 2
    m = R._rz(math.radians(s.roll)) @ R.view_matrix(s.pitch, s.yaw)
    return [R.Quad((m @ (q.pos - middle).T).T, q.uv, q.tex, q.shade, q.emissive, m @ q.normal, q.directional)
            for q in quads]


def draw(s: Subject, size: int = SIZE) -> Image.Image:
    """The model alone (transparent round it), as large as the margin allows."""
    quads = posed(s)
    pts = np.concatenate([q.pos for q in quads])
    lo, hi = pts.min(axis=0), pts.max(axis=0)
    span = max(hi[0] - lo[0], hi[1] - lo[1]) / (1 - 2 * s.margin)
    centre = np.array([(lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2, 0.0])
    return R.render(quads, size, 0, 0, centre, span, ssaa=4, light_dir=LIGHT / np.linalg.norm(LIGHT))


def sky(kind: str, size: int = SIZE) -> Image.Image:
    """A dusk sky (night blue over a low orange glow) or a day sky (the game's blue, paler at the horizon), with a
    half-step of noise so the gradient shows no bands."""
    top, bottom, power = {"dusk": ((22, 28, 56), (190, 98, 56), 1.4), "day": ((92, 136, 214), (196, 222, 246), 1.0)}[kind]
    t = (np.arange(size) / (size - 1)) ** power
    col = np.array(top, float)[None] * (1 - t[:, None]) + np.array(bottom, float)[None] * t[:, None]
    rgb = np.repeat(col[:, None, :], size, axis=1)
    rgb += np.random.default_rng(7).uniform(-0.5, 0.5, rgb.shape)
    return Image.fromarray(np.clip(np.round(rgb), 0, 255).astype(np.uint8), "RGB")


def logo(s: Subject) -> Image.Image:
    """The model over its sky, with a soft shadow down and to the right to lift it off the sky."""
    model = draw(s)
    out = sky(s.sky).convert("RGBA")
    shadow = model.getchannel("A").filter(ImageFilter.GaussianBlur(SIZE / 40)).point(lambda v: int(v * 0.5))
    layer = Image.new("RGBA", model.size, (0, 0, 0, 0))
    layer.putalpha(shadow)
    out.alpha_composite(layer, (round(SIZE * 0.02), round(SIZE * 0.03)))
    out.alpha_composite(model)
    return out.convert("RGB")


def review(logos: dict[str, Image.Image]) -> Path:
    """Each logo at full size, then rounded at 120 px (a project header) and 56 px (a search list) on a dark page."""
    def rounded(img: Image.Image, px: int, r: int) -> Image.Image:
        im = img.resize((px, px), Image.LANCZOS).convert("RGBA")
        mask = Image.new("L", (px * 4, px * 4), 0)
        ImageDraw.Draw(mask).rounded_rectangle([0, 0, px * 4 - 1, px * 4 - 1], r * 4, fill=255)
        im.putalpha(mask.resize((px, px), Image.LANCZOS))
        return im

    sheet = Image.new("RGBA", (len(logos) * (SIZE + 20) + 20, SIZE + 180), (24, 24, 27, 255))
    d = ImageDraw.Draw(sheet)
    for i, (name, img) in enumerate(logos.items()):
        x = 20 + i * (SIZE + 20)
        sheet.alpha_composite(img.convert("RGBA"), (x, 20))
        sheet.alpha_composite(rounded(img, 120, 10), (x, SIZE + 40))
        sheet.alpha_composite(rounded(img, 56, 6), (x + 140, SIZE + 40))
        d.text((x + 210, SIZE + 46), name, fill=(235, 235, 235, 255))
    out = paths.PREVIEWS / "logo_review.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    sheet.convert("RGB").save(out)
    return out


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--subject", choices=sorted(SUBJECTS), default="missile")
    ap.add_argument("--out", type=Path, default=OUT)
    ap.add_argument("--review", action="store_true")
    args = ap.parse_args()
    if args.review:
        print(review({name: logo(s) for name, s in SUBJECTS.items()}))
        return
    img = logo(SUBJECTS[args.subject])
    args.out.parent.mkdir(parents=True, exist_ok=True)
    img.save(args.out)
    print(args.out, img.size)


if __name__ == "__main__":
    main()
