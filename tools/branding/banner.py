"""The mod page's banner: the Tsar Bomba's mushroom cloud over cherry groves, in the 2026-11 style (style.py).

The picture is the blast bench's at tick 120 (`runBlastBench -Pbench=tsar_warhead -PshotSize=1920x1080`, a normally
generated world), the one the first banner (docs/branding/banner.jpg, 2026-11-08) was cut from. Two layouts:
- `scene`, 1800 x 600 as the first banner: the picture across the whole banner, the title, stripe and tagline centred
  over the hills under the cloud, the land darkened under them;
- `wide`, 1600 x 400 as the banner the user brought: the title, stripe and tagline on the left over the picture
  blurred into the dark, the cloud standing clear on the right.

Usage: tools\\.venv\\Scripts\\python.exe tools\\branding\\banner.py [--source <png>] [--out <folder>]
Writes banner_scene.png and banner_wide.png (and a JPEG of each) into the folder (default build/branding).
"""
import argparse
from pathlib import Path

import numpy as np
from PIL import Image, ImageEnhance, ImageFilter

import style as S

SOURCE = S.ROOT / "run" / "screenshots" / "bench_tsar_warhead_120.png"
CLOUD_X = 960          # the cloud's stem in the 1920 x 1080 picture
CLOUD_CAP = (90, 372)  # its cap, top to underside


def scene_banner(src: Image.Image, scale: float = 1.05, gap_title: float = 22, gap_tag: float = 20,
                 bottom: float = 34, stripe_w: float = 300) -> Image.Image:
    """The first banner's framing (the picture's top 640 rows), the type centred over the hills."""
    W, H = 1800, 600
    k = W / 1920
    pic = src.crop((0, 0, 1920, round(H / k))).resize((W, H), Image.LANCZOS)
    pic = ImageEnhance.Color(ImageEnhance.Contrast(pic).enhance(1.06)).enhance(1.12).convert("RGBA")

    cap, stripe_h, tag_cap = 84 * scale, S.STRIPE_H * scale, 31 * scale
    block = cap + gap_title * scale + stripe_h + gap_tag * scale + tag_cap
    top = H - bottom - block
    xs, ys = S.grid(W, H)

    # The land darkens toward the bottom, most under the type, so white and green read over pink and green.
    fall = S.smoothstep(top - 130, H, ys) * 0.78
    scrim = np.exp(-(((xs - W / 2) / 720) ** 2 + ((ys - (top + block / 2)) / 120) ** 2)) * 0.50
    pic = S.darken(pic, S.NIGHT, S.mask(np.maximum(fall, scrim)))
    # The cloud's base and the far hills a touch darker too, toward the edges: the banner's frame.
    edge = S.smoothstep(0.62, 1.15, np.hypot((xs - W / 2) / (W / 2), (ys - H * 0.42) / (H * 0.9))) * 0.55
    pic = S.darken(pic, S.NIGHT, S.mask(edge))

    text = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    x0 = (W - S.title_width(cap)) / 2
    S.draw_title(text, x0, top, cap)
    sw = round(stripe_w * scale)
    stripe = S.hazard_stripe(sw, round(stripe_h), S.STRIPE_PERIOD * scale, S.STRIPE_YELLOW * scale)
    stripe_top = round(top + cap + gap_title * scale)
    text.alpha_composite(stripe, (round((W - sw) / 2), stripe_top))
    tag_top = stripe_top + stripe_h + gap_tag * scale
    S.draw_tagline(text, (W - S.tagline_width(tag_cap)) / 2, tag_top, tag_cap)

    pic.alpha_composite(S.shadow_of(text, 7, 0.55, (0, 3)))
    pic.alpha_composite(text)
    return pic.convert("RGB")


def wide_banner(src: Image.Image, cap: float = 74, left: float = 90, cloud_at: float = 1330,
                fade=(960, 1270), rows=(50, 500)) -> Image.Image:
    """The brought banner's layout: type on the left in the dark, the cloud on the right."""
    W, H = 1600, 400
    scale = cap / 84
    k = H / (rows[1] - rows[0])
    pic = src.crop((0, rows[0], 1920, rows[1])).resize((round(1920 * k), H), Image.LANCZOS).convert("RGBA")
    px = round(cloud_at - CLOUD_X * k)

    # The dark: the same picture, covering the banner, blurred to colour alone and taken down to night.
    cover = src.resize((W, round(1080 * W / 1920)), Image.LANCZOS)
    cy = round((cover.height - H) * 0.45)
    back = cover.crop((0, cy, W, cy + H)).filter(ImageFilter.GaussianBlur(46)).convert("RGBA")
    back = S.tint(back, S.NIGHT, 0.80)

    xs, ys = S.grid(W, H)
    sharp = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    sharp.alpha_composite(pic, (px, 0)) if px >= 0 else sharp.alpha_composite(pic.crop((-px, 0, pic.width, H)))
    # The picture fades in to the right of the type, and a little at its top and bottom into the dark.
    a = S.smoothstep(fade[0], fade[1], xs) * (0.55 + 0.45 * S.smoothstep(0, 60, ys) * S.smoothstep(H, H - 70, ys))
    sharp = S.darken(sharp, S.NIGHT, S.mask(S.smoothstep(fade[1] + 160, fade[0], xs) * 0.55))
    sharp.putalpha(S.mask(a * (np.asarray(sharp.getchannel("A")) / 255.0)))
    back.alpha_composite(sharp)

    stripe_h, tag_cap = S.STRIPE_H * scale, 31 * scale
    block = cap + 53 * scale + stripe_h + 29 * scale + tag_cap
    top = (H - block) / 2
    text = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    S.draw_title(text, left, top, cap)
    sw = round(S.STRIPE_W * scale)
    stripe = S.hazard_stripe(sw, round(stripe_h), S.STRIPE_PERIOD * scale, S.STRIPE_YELLOW * scale)
    stripe_top = round(top + cap + 53 * scale)
    text.alpha_composite(stripe, (round(left), stripe_top))
    S.draw_tagline(text, left, stripe_top + stripe_h + 29 * scale, tag_cap)
    back.alpha_composite(S.shadow_of(text, 6, 0.35, (0, 2)))
    back.alpha_composite(text)
    return back.convert("RGB")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--source", default=str(SOURCE))
    ap.add_argument("--out", default=str(S.ROOT / "build" / "branding"))
    args = ap.parse_args()
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    src = Image.open(args.source).convert("RGB")
    for name, img in (("banner_scene", scene_banner(src)), ("banner_wide", wide_banner(src))):
        img.save(out / f"{name}.png", optimize=True)
        img.save(out / f"{name}.jpg", quality=92, subsampling=0)
        print(name, img.size, out / f"{name}.png")


if __name__ == "__main__":
    main()
