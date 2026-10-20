"""Field Manual (DESIGN.md §24.2): the book's GUI texture and its item sprite.

textures/gui/manual/book.png (512 x 512), positions mirrored in FieldManualScreen / ManualPages:
  (0, 0)     320 x 200  the open book: olive binding, two cream pages, typed header "FM GZ-1", red RESTRICTED stamp
  (0, 208)   18 x 10    next-page arrow (hover at +18)       (36, 208) previous-page arrow (hover at +18)
  (72, 208)  11 x 11    contents (hover at +12)              (96, 208) back (hover at +12)
  (120, 208) 18 x 18    item slot, printed on the page       (140, 208) 36 x 36 spotlight frame
  (140, 244) 26 x 26    recipe result frame                  (178, 208) 22 x 15 recipe arrow
  (178, 226) 5 x 5      unread dot                           (200, 232) 8 x 16 paperclip
  (200, 208) 24 x 20    bookmark ribbon (hover 224)          (248, 208) add-bookmark ribbon (hover 272)
  (296, 208) 11 x 11    layer down (hover y 220)             (308, 208) layer up (hover y 220)
  (0, 256)   56 x 56    cover emblem (stencilled trefoil)    (0, 320)   126 x 16 search field
"""
from __future__ import annotations

import random
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))

from gz import palette as P  # noqa: E402
from gz import paths  # noqa: E402
from gz.palette import fixed  # noqa: E402
from gz.tex import Tex  # noqa: E402
import resources as R  # noqa: E402

PAPER = fixed("manual_paper", "#8f8566", "#b3a684", "#cfc3a2", "#e2d7b9", "#ece3c9", "#f4ecd8", "#fbf6ea")
INK = (43, 42, 38, 255)
FADED = (107, 102, 90, 255)
STAMP = (168, 50, 42, 255)
OLIVE = P.OLIVE
RIBBON = fixed("ribbon", "#3a0e0c", "#5c1612", "#7e201a", "#9c2c24", "#b83a30", "#d0544a", "#e88a80")
GREY_RIBBON = fixed("ribbon_spare", "#2c2e26", "#3e4136", "#525646", "#676b58", "#7e826c", "#989c84", "#b8bca4")

ARROW_R = ["##........", ".###......", "..####....", "########..", "#########.", "########..", "..####....", ".###......", "##........", ".........."]
HOUSE = [".....#.....", "....###....", "...#####...", "..#######..", ".#########.", "###########", ".##.....##.", ".##.###.##.",
         ".##.###.##.", ".##.###.##.", ".#########."]
BACK = ["....#......", "...##......", "..########.", ".#########.", "..########.", "...##....#.", "....#....#.", ".........#.",
        "....######.", "....#####..", "..........."]
LAYER_UP = ["...........", ".....#.....", "....###....", "...#####...", "..#######..", "....###....", "....###....",
            "....###....", "...........", "...........", "..........."]


def glyph(t: Tex, rows: list[str], x: int, y: int, c) -> None:
    for gy, row in enumerate(rows):
        for gx, ch in enumerate(row):
            if ch == "#":
                t.px(x + gx, y + gy, c)


def paper(t: Tex, x: int, y: int, w: int, h: int, gutter_right: bool, seed: int) -> None:
    """A cream page: warm paper, a soft shadow toward the gutter, a few fibres."""
    t.rect(x, y, w, h, PAPER[4])
    rng = random.Random(seed)
    for _ in range(w * h // 40):
        t.px(x + rng.randrange(w), y + rng.randrange(h), PAPER[5] if rng.random() < 0.6 else PAPER[3])
    for i in range(8):
        shade = PAPER[3] if i < 3 else PAPER[4]
        gx = x + w - 1 - i if gutter_right else x + i
        if i < 5:
            t.vline(gx, y, h, PAPER[2] if i == 0 else shade)
    t.hline(x, y, w, PAPER[5])
    t.hline(x, y + h - 1, w, PAPER[3])


def book(t: Tex) -> None:
    W, H = 320, 200
    # Binding: olive cloth over board, darker rim, corner protectors.
    t.rect(0, 2, W, H - 4, OLIVE[2])
    t.rect(2, 0, W - 4, H, OLIVE[2])
    t.rect(2, 2, W - 4, H - 4, OLIVE[3])
    t.outline(1, 1, W - 2, H - 2, OLIVE[1])
    rng = random.Random(7)
    for _ in range(900):
        t.px(2 + rng.randrange(W - 4), 2 + rng.randrange(H - 4), OLIVE[4] if rng.random() < 0.5 else OLIVE[2])
    for cx, cy in ((2, 2), (W - 10, 2), (2, H - 10), (W - 10, H - 10)):
        t.rect(cx, cy, 8, 8, P.BRASS[3])
        t.outline(cx, cy, 8, 8, P.BRASS[1])
        t.px(cx + 1, cy + 1, P.BRASS[5])
    # Page block edges under each page (the stack of leaves), then the two pages.
    for i, c in enumerate((PAPER[1], PAPER[3], PAPER[2], PAPER[3])):
        t.rect(8 - i, 6 + i, 152, 186, c)
        t.rect(160 + i, 6 + i, 152, 186, c)
    paper(t, 10, 6, 150, 186, True, 11)
    paper(t, 160, 6, 150, 186, False, 12)
    # Gutter: the fold between the pages.
    t.vline(159, 6, 186, PAPER[1])
    t.vline(160, 6, 186, PAPER[2])
    # Typed header and the RESTRICTED stamp; a ruled line under each header and above the page numbers.
    t.text(44, 10, "FM GZ-1", FADED)                                           # clear of the contents and back icons
    sw = Tex.text_width("RESTRICTED")
    sx = 160 + 150 - 14 - sw - 4
    t.outline(sx - 3, 8, sw + 6, 9, STAMP)
    t.text(sx, 10, "RESTRICTED", STAMP)
    for px in (22, 172):
        t.hline(px, 19, 126, PAPER[2])
        t.hline(px, 176, 126, PAPER[2])


def slot(t: Tex, x: int, y: int, w: int = 18, h: int = 18) -> None:
    """A slot printed on the page: a shallow recess, dark top-left edge, light bottom-right."""
    t.rect(x, y, w, h, PAPER[3])
    t.hline(x, y, w, PAPER[1])
    t.vline(x, y, h, PAPER[1])
    t.hline(x + 1, y + h - 1, w - 1, PAPER[6])
    t.vline(x + w - 1, y + 1, h - 1, PAPER[6])


def widgets(t: Tex) -> None:
    for i, c in enumerate((INK, STAMP)):
        glyph(t, ARROW_R, 4 + i * 18, 208, c)                                      # next
        glyph(t, [row[::-1] for row in ARROW_R], 40 + i * 18, 208, c)               # previous
        glyph(t, HOUSE, 72 + i * 12, 208, c)
        glyph(t, BACK, 96 + i * 12, 208, c)
    slot(t, 120, 208)
    # Spotlight frame: a ring of darker paper round a recess.
    t.rect(140, 208, 36, 36, PAPER[3])
    t.outline(140, 208, 36, 36, PAPER[1])
    t.outline(141, 209, 34, 34, PAPER[5])
    slot(t, 142, 210, 32, 32)
    # Result frame: a slot with a heavier border.
    t.rect(140, 244, 26, 26, PAPER[2])
    t.outline(140, 244, 26, 26, PAPER[1])
    slot(t, 144, 248)
    # Recipe arrow: a solid arrow in faded ink, a shaft and a head.
    t.rect(179, 216, 13, 5, FADED)
    for k in range(8):
        t.vline(192 + k, 211 + k, 15 - 2 * k, FADED)
    # Unread dot.
    for x, y in ((179, 226), (180, 226), (181, 226), (178, 227), (182, 227), (178, 228), (182, 228), (178, 229), (182, 229),
                 (179, 230), (180, 230), (181, 230)):
        t.px(x, y, STAMP)
    t.rect(179, 227, 3, 3, (200, 70, 60, 255))
    # Paperclip.
    clip = ["..####..", ".#....#.", ".#.##.#.", ".#.#..#.", ".#.#..#.", ".#.#..#.", ".#.#..#.", ".#.#..#.", ".#.#..#.",
            ".#.#..#.", ".#....#.", ".#....#.", "..#..#..", "...##...", "........", "........"]
    for gy, row in enumerate(clip):
        for gx, ch in enumerate(row):
            if ch == "#":
                t.px(200 + gx, 232 + gy, P.STEEL[5] if gx < 4 else P.STEEL[3])
    # Bookmark ribbons: cloth tabs sticking out of the book's edge with a notched end.
    for i, ramp in enumerate((RIBBON, RIBBON, GREY_RIBBON, GREY_RIBBON)):
        x0 = 200 + i * 24
        base = 4 if i % 2 else 3
        t.rect(x0, 208, 20, 20, ramp[base])
        t.hline(x0, 208, 20, ramp[base + 1])
        t.hline(x0, 227, 20, ramp[base - 2])
        for k in range(4):                                                         # notched (swallowtail) end
            t.vline(x0 + 20 + k, 208 + k, 20 - 2 * k, ramp[base])
        t.rect(x0 + 21, 216, 3, 4, (0, 0, 0, 0))
        t.vline(x0 + 4, 208, 20, ramp[base - 1])                                   # where it enters the book
        if i >= 2:
            t.rect(x0 + 9, 213, 2, 10, (240, 236, 220, 255))                       # "+"
            t.rect(x0 + 5, 217, 10, 2, (240, 236, 220, 255))
    # Layer buttons: paper buttons with an arrow, normal and hover (ink red).
    for row, c in ((0, INK), (12, STAMP)):
        for i, rows in enumerate((LAYER_UP[::-1], LAYER_UP)):
            x0, y0 = 296 + i * 12, 208 + row
            t.rect(x0, y0, 11, 11, PAPER[3] if row == 0 else PAPER[2])
            t.outline(x0, y0, 11, 11, PAPER[1])
            glyph(t, rows, x0, y0 + (0 if i else 0), c)
    # Cover emblem: a stencilled trefoil in a double ring, stamped in olive ink.
    cx, cy, r = 28.0, 28.0 + 256, 26.0
    t.disc(cx, cy, r, OLIVE[2])
    t.disc(cx, cy, r - 2.0, (0, 0, 0, 0))
    t.disc(cx, cy, r - 3.5, OLIVE[2])
    t.disc(cx, cy, r - 5.0, (0, 0, 0, 0))
    t.trefoil(cx, cy, r - 8.0, OLIVE[2])
    # Search field: an underline with a magnifier at the left.
    t.hline(0, 333, 126, PAPER[1])
    lens = ["..###...", ".#...#..", "#.....#.", "#.....#.", "#.....#.", ".#...#..", "..###.#.", "......##"]
    glyph(t, lens, 2, 322, FADED)


def item_sprite() -> Tex:
    """The Field Manual item: an olive hardcover, spine to the left, cream page edges, a typed label plate and a
    yellow-and-black hazard band across the cover."""
    rows = [
        "................",
        "..1222222222....",
        ".122333333332p..",
        ".123333333332pp.",
        ".123LLLLLL332pp.",
        ".123LkkkkL332pp.",
        ".123LLLLLL332pp.",
        ".123LkkkLL332pp.",
        ".123LLLLLL332pp.",
        ".123333333332pp.",
        ".12yyyyyyyyy2pp.",
        ".12yyyyyyyyy2pp.",
        ".123333333332pp.",
        ".122222222222pp.",
        "..11111111111pP.",
        "...........PPP..",
    ]
    t = Tex(16, 16)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch.isdigit():
                t.px(x, y, OLIVE[int(ch)])
            elif ch == "p":
                t.px(x, y, PAPER[5] if y % 2 else PAPER[4])
            elif ch == "P":
                t.px(x, y, PAPER[2])
            elif ch == "L":
                t.px(x, y, PAPER[5])
            elif ch == "k":
                t.px(x, y, FADED)
            elif ch == "y":
                t.px(x, y, P.HAZARD_YELLOW.base if (x + y) % 4 < 2 else P.HAZARD_BLACK.base)
    t.vline(3, 2, 11, OLIVE[2])                                                    # the spine's hinge
    return t


if __name__ == "__main__":
    tex = Tex(512, 512)
    book(tex)
    widgets(tex)
    out = paths.TEXTURES / "gui" / "manual" / "book.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    print(tex.save(out))
    R.save_item("field_manual", item_sprite())
    print("field_manual item written")
