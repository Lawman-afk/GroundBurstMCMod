"""The mod's logos (the mod page's square avatar), each the glowing missile icon the user brought (docs/branding/icon.png,
1024 px: the mod's DF-5 diagonal with a green glow, on transparency) on a ground in the 2026-11 style (style.py).

- dusk:     the first logo's sky (navy over burnt orange, tools/assets/logo.py's) behind the glowing missile;
- night:    the banner's dark, lit a little behind the missile;
- cloud:    the Tsar Bomba's mushroom cloud (the banner's picture) darkened behind it;
- hazard:   the banner's hazard stripe as a tape under the missile, on the dark: the user's choice (2026-11-12), the
            mod's logo, written to docs/branding/logo.png too;
- scope:    a green phosphor radar scope (the mod's own screens' look) with the missile across it;
- wordmark: the missile over the title, GROUND white and BURST green;
- badge:    the night logo in a ring, round, for places that crop avatars to a circle.

Usage: tools\\.venv\\Scripts\\python.exe tools\\branding\\logos.py [--icon <png>] [--out <folder>]
Writes logo_<name>.png (400 x 400) and logo_<name>_1024.png into the folder (default build/branding/logos), a sheet of
them at 400, 128 and 48 px (build/branding/logos_sheet.png) and the chosen one as docs/branding/logo.png.
"""
import argparse
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageEnhance, ImageFilter, ImageFont

import style as S

N = 1024                                 # every logo is made at 1024 and scaled down
ICON = S.ROOT / "docs" / "branding" / "icon.png"
CHOSEN = "hazard"                        # the user's pick (2026-11-12): the mod's logo
LOGO = S.ROOT / "docs" / "branding" / "logo.png"
SCENE = S.ROOT / "run" / "screenshots" / "bench_tsar_warhead_120.png"
DUSK_TOP, DUSK_BOTTOM = (22, 28, 56), (188, 98, 56)     # the first logo's sky, top to bottom
PHOSPHOR = (110, 230, 90)


def noise(n: int, amount: float, seed: int) -> np.ndarray:
    """A little fine noise so gradients do not band once saved."""
    return np.random.default_rng(seed).normal(0, amount, (n, n, 1))


def from_array(a: np.ndarray) -> Image.Image:
    return Image.fromarray(np.uint8(np.clip(a, 0, 255) + 0.5), "RGB").convert("RGBA")


def dusk() -> Image.Image:
    t = np.linspace(0, 1, N)[:, None, None]
    a = np.array(DUSK_TOP) * (1 - t) + np.array(DUSK_BOTTOM) * t
    return from_array(np.broadcast_to(a, (N, N, 3)) + noise(N, 0.8, 1))


def night(light: float = 1.0) -> Image.Image:
    xs, ys = S.grid(N, N)
    r = np.hypot((xs - N * 0.5) / N, (ys - N * 0.5) / N)
    lit = np.exp(-(r / 0.36) ** 2)[..., None] * light
    base = np.array(S.NIGHT, float)
    glow = np.array((50, 60, 48), float)
    return from_array(base + (glow - base) * lit + noise(N, 0.9, 2))


def cloud() -> Image.Image:
    src = Image.open(SCENE).convert("RGB")
    # a square with the cloud in its top-left, the corner the missile leaves free
    sq = src.crop((700, 0, 1540, 840)).resize((N, N), Image.LANCZOS)
    sq = ImageEnhance.Color(sq).enhance(0.9)
    img = S.darken(sq, S.NIGHT, S.mask(np.full((N, N), 0.30)))
    xs, ys = S.grid(N, N)
    v = S.smoothstep(0.38, 0.80, np.hypot((xs - N * 0.42) / N, (ys - N * 0.42) / N)) * 0.70
    return S.darken(img, S.NIGHT, S.mask(v))


def hazard() -> Image.Image:
    img = night(0.85)
    w = round(N * 1.6)
    h = round(N * 0.05)
    tape = S.hazard_stripe(w, h, h * 1.6, h * 0.9, supersample=3)
    tape = tape.rotate(45, resample=Image.BICUBIC, expand=True)
    # beside the missile toward the bottom-right corner, as the banner's stripe lies under its title
    cx, cy = N * 0.655, N * 0.655
    layer = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    layer.alpha_composite(tape, (round(cx - tape.width / 2), round(cy - tape.height / 2)))
    img.alpha_composite(S.shadow_of(layer, 8, 0.6, (0, 5)))
    img.alpha_composite(layer)
    return img


def scope() -> Image.Image:
    img = from_array(np.broadcast_to(np.array((8, 16, 10), float), (N, N, 3)) + noise(N, 0.7, 3))
    xs, ys = S.grid(N, N)
    c = N / 2
    r = np.hypot(xs - c, ys - c) / (N * 0.46)
    ang = (np.degrees(np.arctan2(ys - c, xs - c)) + 360) % 360
    disc = (r <= 1).astype(float)
    face = np.array((12, 34, 16), float)
    a = np.asarray(img.convert("RGB"), float)
    a = a * (1 - disc[..., None] * 0.9) + face * disc[..., None] * 0.9
    # the sweep: bright at its leading edge (about the 1:30 position), fading back over 70 degrees
    lead = 315.0
    behind = (lead - ang) % 360
    sweep = np.where(behind < 70, (1 - behind / 70) ** 2, 0) * disc * (0.25 + 0.75 * np.clip(r, 0, 1))
    a = a + np.array(PHOSPHOR, float) * (sweep * 0.38)[..., None]
    img = from_array(a)
    d = ImageDraw.Draw(img)
    line = PHOSPHOR + (96,)
    R = N * 0.46
    for k in (1 / 3, 2 / 3, 1.0):
        d.ellipse((c - R * k, c - R * k, c + R * k, c + R * k), outline=line, width=5 if k == 1.0 else 3)
    d.line((c - R, c, c + R, c), fill=PHOSPHOR + (60,), width=3)
    d.line((c, c - R, c, c + R), fill=PHOSPHOR + (60,), width=3)
    for bx, by in ((0.26, -0.21), (-0.30, 0.12), (0.12, 0.31)):
        x, y = c + bx * N, c + by * N
        d.ellipse((x - 9, y - 9, x + 9, y + 9), fill=PHOSPHOR + (200,))
    return img.filter(ImageFilter.GaussianBlur(0.6))


def wordmark(icon: Image.Image) -> Image.Image:
    img = night(0.9)
    small = icon.resize((round(N * 0.74), round(N * 0.74)), Image.LANCZOS)
    img.alpha_composite(small, (round((N - small.width) / 2), round(N * 0.0)))
    text = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    cap = N * 0.072                      # the title then spans 0.84 of the logo
    top = N * 0.80
    S.draw_title(text, (N - S.title_width(cap)) / 2, top, cap)
    sw, sh = round(N * 0.30), round(cap * 0.14)
    stripe = S.hazard_stripe(sw, sh, sh * 1.6, sh * 0.9)
    text.alpha_composite(stripe, (round((N - sw) / 2), round(top + cap * 1.36)))
    img.alpha_composite(S.shadow_of(text, 6, 0.5, (0, 3)))
    img.alpha_composite(text)
    return img


def badge(icon: Image.Image) -> Image.Image:
    img = night(1.0)
    d = ImageDraw.Draw(img)
    m = N * 0.035
    d.ellipse((m, m, N - m, N - m), outline=S.GREEN + (255,), width=round(N * 0.022))
    k = round(N * 0.94)                  # the missile over the ring, its nose and tail just inside it
    img.alpha_composite(icon.resize((k, k), Image.LANCZOS), (round((N - k) / 2), round((N - k) / 2)))
    round_mask = Image.new("L", (N * 4, N * 4), 0)
    ImageDraw.Draw(round_mask).ellipse((0, 0, N * 4 - 1, N * 4 - 1), fill=255)
    img.putalpha(round_mask.resize((N, N), Image.LANCZOS))
    return img


def place(ground: Image.Image, icon: Image.Image, scale: float = 1.04) -> Image.Image:
    out = ground.copy()
    k = round(N * scale)
    ic = icon.resize((k, k), Image.LANCZOS)
    out.alpha_composite(ic, (round((N - k) / 2), round((N - k) / 2)))
    return out


def sheet(logos: dict, path: Path):
    pad, label_h = 24, 40
    cell_w = 400 + 128 + 48 + pad * 4
    cols = 2
    rows = (len(logos) + cols - 1) // cols
    W = cols * cell_w + pad
    H = rows * (400 + label_h + pad) + pad
    img = Image.new("RGB", (W, H), (43, 43, 46))
    d = ImageDraw.Draw(img)
    f = ImageFont.truetype(str(S.TAG_FONT), 30)
    for i, (name, logo) in enumerate(logos.items()):
        x = pad + (i % cols) * cell_w
        y = pad + (i // cols) * (400 + label_h + pad)
        d.text((x, y), name, font=f, fill=(235, 235, 235))
        y += label_h
        for size, dx, dy in ((400, 0, 0), (128, 400 + pad, 0), (48, 400 + pad * 2 + 128, 0)):
            small = logo.resize((size, size), Image.LANCZOS)
            img.paste(small, (x + dx, y + dy), small)
    img.save(path)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--icon", default=str(ICON))
    ap.add_argument("--out", default=str(S.ROOT / "build" / "branding" / "logos"))
    args = ap.parse_args()
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    icon = Image.open(args.icon).convert("RGBA").resize((N, N), Image.LANCZOS)
    logos = {
        "dusk": place(dusk(), icon),
        "night": place(night(), icon),
        "cloud": place(cloud(), icon),
        "hazard": place(hazard(), icon),
        "scope": place(scope(), icon),
        "wordmark": wordmark(icon),
        "badge": badge(icon),
    }
    for name, logo in logos.items():
        logo.save(out / f"logo_{name}_1024.png", optimize=True)
        logo.resize((400, 400), Image.LANCZOS).save(out / f"logo_{name}.png", optimize=True)
    sheet(logos, out.parent / "logos_sheet.png")
    logos[CHOSEN].resize((400, 400), Image.LANCZOS).convert("RGB").save(LOGO, optimize=True)
    print("wrote", ", ".join(logos), "to", out, "and", CHOSEN, "as", LOGO)


if __name__ == "__main__":
    main()
