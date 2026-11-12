"""The mod's branding style (2026-11): the colours, type and hazard stripe of the banner and logos.

Measured from the 1600 x 400 banner the user brought (a glowing missile, the title, a stripe and the tagline on a dark,
blurred landscape): the title in Russo One, GROUND white and BURST green; under it a hazard stripe of yellow and
near-black bands at 45 degrees; the tagline in Rajdhani Bold, cream. Both fonts are under the SIL Open Font License
(their licences beside them in fonts/).
"""
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
FONTS = HERE / "fonts"
TITLE_FONT = FONTS / "RussoOne-Regular.ttf"
TAG_FONT = FONTS / "Rajdhani-Bold.ttf"

WHITE = (255, 255, 255)
GREEN = (176, 232, 112)          # BURST
CREAM = (226, 222, 192)          # the tagline
HAZARD_YELLOW = (236, 190, 48)
HAZARD_DARK = (20, 20, 16)
NIGHT = (14, 18, 13)             # the dark the banner's background falls to
TAGLINE = "Assemble it. Launch it. Watch the horizon."

# The measured banner, 400 px high: the title's capitals 84 px, the stripe 240 x 10 px with bands 16 px apart (9 of
# yellow), 53 px under the title's baseline, the tagline's capitals 31 px, 29 px under the stripe.
STRIPE_W, STRIPE_H, STRIPE_PERIOD, STRIPE_YELLOW = 240, 10, 16, 9


def title_font(cap: float) -> ImageFont.FreeTypeFont:
    """Russo One at the size whose capitals are `cap` pixels tall (its H is 0.70 of the size)."""
    return ImageFont.truetype(str(TITLE_FONT), round(cap / 0.70))


def tag_font(cap: float) -> ImageFont.FreeTypeFont:
    """Rajdhani Bold at the size whose capitals are `cap` pixels tall (its A is 0.65 of the size)."""
    return ImageFont.truetype(str(TAG_FONT), round(cap / 0.65))


def title_width(cap: float) -> float:
    f = title_font(cap)
    return f.getlength("GROUND ") + f.getlength("BURST")


def draw_title(layer: Image.Image, x: float, cap_top: float, cap: float, *, words=("GROUND", "BURST"),
               colours=(WHITE, GREEN)) -> tuple[float, float]:
    """Draws the title with its capitals' top at cap_top; returns its right edge and its baseline."""
    f = title_font(cap)
    d = ImageDraw.Draw(layer)
    top_offset = f.getbbox("H")[1]          # the font's capitals start this far under the text's origin
    y = cap_top - top_offset
    for i, (word, colour) in enumerate(zip(words, colours)):
        d.text((x, y), word, font=f, fill=colour + (255,))
        x += f.getlength(word + (" " if i < len(words) - 1 else ""))
    return x, cap_top + cap


def draw_tagline(layer: Image.Image, x: float, cap_top: float, cap: float, text: str = TAGLINE,
                 colour=CREAM) -> float:
    f = tag_font(cap)
    d = ImageDraw.Draw(layer)
    d.text((x, cap_top - f.getbbox("A")[1]), text, font=f, fill=colour + (255,))
    return x + f.getlength(text)


def tagline_width(cap: float, text: str = TAGLINE) -> float:
    return tag_font(cap).getlength(text)


def hazard_stripe(width: int, height: int, period: float, yellow: float, supersample: int = 4) -> Image.Image:
    """A hazard band: yellow where (x + y) mod period < yellow, near-black elsewhere (bands leaning as / does)."""
    s = supersample
    w, h = width * s, height * s
    img = Image.new("RGBA", (w, h), HAZARD_DARK + (255,))
    d = ImageDraw.Draw(img)
    p, yl = period * s, yellow * s
    k = -h
    while k < w + h:
        # the band from x+y = k to x+y = k + yl, as a polygon over the strip
        d.polygon([(k, 0), (k + yl, 0), (k + yl - h, h), (k - h, h)], fill=HAZARD_YELLOW + (255,))
        k += p
    return img.resize((width, height), Image.LANCZOS)


def shadow_of(layer: Image.Image, radius: float, opacity: float, offset=(0, 0)) -> Image.Image:
    """A soft dark shadow of a layer's opaque parts."""
    alpha = layer.getchannel("A").point(lambda a: int(a * opacity))
    sh = Image.new("RGBA", layer.size, (0, 0, 0, 0))
    sh.putalpha(alpha)
    sh = sh.filter(ImageFilter.GaussianBlur(radius))
    out = Image.new("RGBA", layer.size, (0, 0, 0, 0))
    out.alpha_composite(sh, offset)
    return out


def smoothstep(e0, e1, x):
    """0 below e0, 1 above e1, a smooth S between (numbers or numpy arrays)."""
    t = np.clip((np.asarray(x, dtype=np.float64) - e0) / (e1 - e0), 0.0, 1.0)
    return t * t * (3 - 2 * t)


def mask(a) -> Image.Image:
    """A greyscale mask from an array of 0..1."""
    return Image.fromarray(np.uint8(np.clip(a, 0, 1) * 255 + 0.5), "L")


def grid(w: int, h: int):
    """Pixel centres' coordinates: (xs, ys), each h x w."""
    return np.meshgrid(np.arange(w) + 0.5, np.arange(h) + 0.5)


def tint(img: Image.Image, colour, amount: float) -> Image.Image:
    """Mixes a picture toward a flat colour."""
    flat = Image.new(img.mode, img.size, colour + ((255,) if img.mode == "RGBA" else ()))
    return Image.blend(img, flat, amount)


def darken(img: Image.Image, colour, alpha: Image.Image) -> Image.Image:
    """Lays a colour over a picture through a mask (255: all colour)."""
    over = Image.new("RGBA", img.size, colour + (255,))
    over.putalpha(alpha)
    out = img.convert("RGBA")
    out.alpha_composite(over)
    return out
