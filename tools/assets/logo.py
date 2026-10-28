"""The project logo (the CurseForge page's icon): a real photograph of a nuclear test, pixelated. The photo is Castle
Romeo (Bikini Atoll, 27 March 1954), a United States Department of Energy photograph (NNSA Nevada Site Office photo
library, XX-33), in the public domain; it is not kept in the repository:

    https://upload.wikimedia.org/wikipedia/commons/a/a7/Operation_Castle_-_Romeo_001.jpg

It is cropped square round the fireball, averaged down to a coarse grid, cut to a small palette (no dithering, so the
pixels stay clean blocks of colour) and drawn back up with hard pixel edges to 400 x 400.

    tools\\.venv\\Scripts\\python.exe tools\\assets\\logo.py <photo.jpg> [--grid 50] [--colours 24] [--crop x0,y0,size]
        writes docs/branding/logo.png; --review writes a sheet comparing grids and crops instead
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

from PIL import Image, ImageEnhance

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from gz import paths  # noqa: E402

SIZE = 400
OUT = paths.ROOT / "docs" / "branding" / "logo.png"
#: The square kept from the 504 x 576 photo: the fireball, its stem and the glare on the sea under it.
CROP = (102, 82, 320)


def pixelate(photo: Image.Image, grid: int, colours: int, crop: tuple[int, int, int], punch: float = 1.12) -> Image.Image:
    x0, y0, s = crop
    img = photo.convert("RGB").crop((x0, y0, x0 + s, y0 + s))
    img = ImageEnhance.Contrast(img).enhance(punch)
    img = ImageEnhance.Color(img).enhance(1.05)
    small = img.resize((grid, grid), Image.BOX)                     # each pixel the average of its block
    if colours:
        small = small.quantize(colours, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE).convert("RGB")
    return small.resize((SIZE, SIZE), Image.NEAREST)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("photo", type=Path)
    ap.add_argument("--grid", type=int, default=50, help="pixels a side before scaling up (400 must divide by it)")
    ap.add_argument("--colours", type=int, default=24)
    ap.add_argument("--crop", default=",".join(map(str, CROP)))
    ap.add_argument("--review", action="store_true")
    args = ap.parse_args()
    photo = Image.open(args.photo)
    crop = tuple(int(v) for v in args.crop.split(","))
    if args.review:
        variants = [(g, c, cr) for cr in (crop, (72, 52, 380)) for g, c in ((40, 20), (50, 24), (80, 32))]
        cell = 300
        sheet = Image.new("RGB", (3 * cell + 40, 2 * (cell + 110) + 20), (40, 40, 44))
        for i, (g, c, cr) in enumerate(variants):
            logo = pixelate(photo, g, c, cr)
            x, y = 10 + (i % 3) * (cell + 10), 10 + (i // 3) * (cell + 110)
            sheet.paste(logo.resize((cell, cell), Image.NEAREST), (x, y))
            sheet.paste(logo.resize((64, 64), Image.LANCZOS), (x, y + cell + 8))
            sheet.paste(logo.resize((32, 32), Image.LANCZOS), (x + 74, y + cell + 8))
        out = paths.OUT / "previews" / "logo_variants.png"
        out.parent.mkdir(parents=True, exist_ok=True)
        sheet.save(out)
        print(out)
        return
    if SIZE % args.grid:
        raise SystemExit(f"--grid must divide {SIZE}")
    logo = pixelate(photo, args.grid, args.colours, crop)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    logo.save(OUT)
    print(OUT, logo.size)


if __name__ == "__main__":
    main()
