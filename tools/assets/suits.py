"""Hazmat suits (DESIGN.md §21.5): worn textures (vanilla's 64x32 humanoid armour layout) and item icons, plus the
equipment asset and item definitions.

Hazmat Suit: bright yellow plastic, taped seams. The hood has a wide dark visor and a filter mask under it with two
round cartridges; the coat a front zip and a trefoil patch on the back; black rubber gloves and boots.
Lead-Lined Hazmat Suit: the same suit in safety orange with riveted grey lead plates over the chest, back, shins,
the hood's crown and the boot caps.

Armour UV layout (one box per part, faces laid out vanilla-style: top/bottom on the first row, then right, front,
left, back): head 8x8x8 at (0, 0); body 8x12x4 at (16, 16); arm 4x12x4 at (40, 16) (both arms share it); leg 4x12x4
at (0, 16) (both legs share it). The "humanoid" layer carries hood, coat (body and arms) and boots (legs); the
"humanoid_leggings" layer carries the trousers (legs and the waist of the body box).
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from gz import palette as P  # noqa: E402
from gz import paths  # noqa: E402
from gz.model import write_json  # noqa: E402
from gz.palette import fixed  # noqa: E402
from gz.sprite import outline  # noqa: E402
from gz.tex import Tex  # noqa: E402

YELLOW = fixed("hazmat_yellow", "#3a2c06", "#6e5410", "#b08a14", "#e2b41e", "#f4cc34", "#fadc68", "#fff0a8")
ORANGE = fixed("hazmat_orange", "#3a1606", "#6e2a0c", "#a84412", "#d8601a", "#ec7a2c", "#f69a58", "#ffc49a")
RUBBER = P.RUBBER
LEAD = P.LEAD
GLASS = fixed("visor_glass", "#0a1418", "#10222a", "#18323c", "#22444f", "#2e5a66", "#5a8a96", "#a8d4dc")
FILTER = P.GUNMETAL


# --- box faces ------------------------------------------------------------------------------------------------------
def faces(u: int, v: int, w: int, h: int, d: int) -> dict[str, tuple[int, int, int, int]]:
    """Face rectangles (x, y, w, h) of a box with texture origin (u, v) and size w x h x d."""
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
            "left": (u + d + w, v + d, d, h), "back": (u + d + w + d, v + d, w, h)}


HEAD = faces(0, 0, 8, 8, 8)
BODY = faces(16, 16, 8, 12, 4)
ARM = faces(40, 16, 4, 12, 4)
LEG = faces(0, 16, 4, 12, 4)


def fill(t: Tex, r: tuple[int, int, int, int], c) -> None:
    t.rect(*r, c)


def seams(t: Tex, r: tuple[int, int, int, int], ramp, every: int = 0) -> None:
    """Shade a plastic panel: a light top edge, a dark bottom edge, and folds every few pixels."""
    x, y, w, h = r
    t.hline(x, y, w, ramp.light)
    t.hline(x, y + h - 1, w, ramp.shadow)
    if every:
        for yy in range(y + every, y + h - 1, every):
            t.hline(x, yy, w, ramp.shadow)


def hood(t: Tex, ramp, lead: bool) -> None:
    for name, r in HEAD.items():
        fill(t, r, ramp.base)
        seams(t, r, ramp)
    x, y, w, h = HEAD["front"]
    # Visor: dark glass across the eyes with a glint, framed.
    t.rect(x + 1, y + 2, 6, 3, GLASS.base)
    t.hline(x + 1, y + 2, 6, GLASS.light)
    t.px(x + 2, y + 2, GLASS.shine)
    t.px(x + 3, y + 3, GLASS.highlight)
    t.outline(x, y + 1, 8, 5, LEAD.shadow if lead else RUBBER.base)
    # Filter mask: a black mouthpiece with two round grey cartridges.
    t.rect(x + 3, y + 6, 2, 2, RUBBER.shadow)
    t.px(x + 1, y + 6, FILTER.light)
    t.px(x + 6, y + 6, FILTER.light)
    t.px(x + 1, y + 7, FILTER.shadow)
    t.px(x + 6, y + 7, FILTER.shadow)
    # Side seams and a drawstring at the back.
    for side in ("left", "right"):
        sx, sy, sw, sh = HEAD[side]
        t.vline(sx + sw // 2, sy, sh, ramp.shadow)
    bx, by, bw, bh = HEAD["back"]
    t.hline(bx, by + bh - 2, bw, ramp.shadow)
    if lead:
        # A lead crown plate on top and a band round the back, riveted.
        tx, ty, tw, th = HEAD["top"]
        t.rect(tx + 1, ty + 1, tw - 2, th - 2, LEAD.base)
        t.outline(tx + 1, ty + 1, tw - 2, th - 2, LEAD.shadow)
        for px_, py_ in ((tx + 2, ty + 2), (tx + 5, ty + 2), (tx + 2, ty + 5), (tx + 5, ty + 5)):
            t.px(px_, py_, LEAD.highlight)
        t.rect(bx + 1, by + 1, bw - 2, 3, LEAD.base)
        t.px(bx + 2, by + 2, LEAD.highlight)
        t.px(bx + 5, by + 2, LEAD.highlight)


def coat(t: Tex, ramp, lead: bool) -> None:
    for r in BODY.values():
        fill(t, r, ramp.base)
        seams(t, r, ramp, every=4)
    x, y, w, h = BODY["front"]
    t.vline(x + 4, y, h, FILTER.light)                     # the zip
    t.vline(x + 3, y, h, ramp.shadow)
    t.px(x + 4, y + 1, FILTER.shine)
    t.hline(x, y + h - 2, w, RUBBER.base)                  # belt
    t.px(x + 4, y + h - 2, FILTER.light)
    bx, by, bw, bh = BODY["back"]
    if lead:
        for px0, py0 in ((x + 1, y + 2), (bx + 1, by + 2)):  # chest and back plates
            t.rect(px0, py0, 6, 6, LEAD.base)
            t.outline(px0, py0, 6, 6, LEAD.shadow)
            t.hline(px0 + 1, py0 + 1, 4, LEAD.light)
            for rx, ry in ((px0 + 1, py0 + 1), (px0 + 4, py0 + 1), (px0 + 1, py0 + 4), (px0 + 4, py0 + 4)):
                t.px(rx, ry, LEAD.highlight)
        t.vline(x + 4, y, 2, FILTER.light)
    else:
        # A black-on-yellow trefoil patch on the back.
        t.rect(bx, by + 1, 8, 8, P.HAZARD_BLACK.base)      # a full-width black patch...
        t.trefoil(bx + 4, by + 5, 3.6, P.HAZARD_YELLOW.highlight)   # ...with a bright trefoil on it
    for r in ARM.values():
        fill(t, r, ramp.base)
        seams(t, r, ramp, every=5)
    for name in ("right", "front", "left", "back"):        # black rubber gloves, a cuff above them
        ax, ay, aw, ah = ARM[name]
        t.rect(ax, ay + ah - 3, aw, 3, RUBBER.base)
        t.hline(ax, ay + ah - 3, aw, RUBBER.light)
        t.hline(ax, ay + ah - 4, aw, ramp.shadow)
    t.rect(*ARM["bottom"], RUBBER.base)
    if lead:
        ax, ay, aw, ah = ARM["front"]
        t.rect(ax, ay + 1, aw, 3, LEAD.base)               # shoulder plates
        t.hline(ax, ay + 1, aw, LEAD.light)


def boots(t: Tex, ramp, lead: bool) -> None:
    """Boots: the lower legs only, black rubber with a sole (the rest of the leg box stays clear)."""
    for name in ("right", "front", "left", "back"):
        lx, ly, lw, lh = LEG[name]
        t.rect(lx, ly + lh - 5, lw, 5, RUBBER.base)
        t.hline(lx, ly + lh - 5, lw, RUBBER.light)
        t.hline(lx, ly + lh - 1, lw, RUBBER.outline)
    t.rect(*LEG["bottom"], RUBBER.outline)
    if lead:
        lx, ly, lw, lh = LEG["front"]
        t.rect(lx, ly + lh - 4, lw, 2, LEAD.base)          # toe caps
        t.hline(lx, ly + lh - 4, lw, LEAD.light)


def trousers(t: Tex, ramp, lead: bool) -> None:
    for name in ("right", "front", "left", "back"):
        lx, ly, lw, lh = LEG[name]
        t.rect(lx, ly, lw, lh - 3, ramp.base)              # the boots cover the last few rows
        t.hline(lx, ly + 5, lw, P.WHITE_CERAMIC.light)     # reflective knee tape
        t.hline(lx, ly + 6, lw, P.WHITE_CERAMIC.base)
        t.hline(lx, ly + lh - 4, lw, ramp.shadow)
    t.rect(*LEG["top"], ramp.base)
    lx, ly, lw, lh = LEG["front"]
    if lead:
        t.rect(lx, ly + 2, lw, 7, LEAD.base)               # shin plates
        t.outline(lx, ly + 2, lw, 7, LEAD.shadow)
        t.px(lx + 1, ly + 3, LEAD.highlight)
        t.px(lx + 2, ly + 7, LEAD.highlight)
    # The waist: the lower rows of the body box.
    for name in ("right", "front", "left", "back"):
        bx, by, bw, bh = BODY[name]
        t.rect(bx, by + bh - 4, bw, 4, ramp.base)
        t.hline(bx, by + bh - 4, bw, ramp.light)
        t.hline(bx, by + bh - 2, bw, RUBBER.base)
    t.rect(*BODY["bottom"], ramp.shadow)


def worn(ramp, lead: bool) -> tuple[Tex, Tex]:
    upper, lower = Tex(64, 32), Tex(64, 32)
    hood(upper, ramp, lead)
    coat(upper, ramp, lead)
    boots(upper, ramp, lead)
    trousers(lower, ramp, lead)
    return upper, lower


# --- item icons -------------------------------------------------------------------------------------------------------
ICONS = {
    "hood": [
        "................", "................", ".....######.....", "....########....", "...##########...", "...#oooooooo#...",
        "...#oooooooo#...", "...##########...", "...###mm#####...", "...#c#mm##c#....", "....########....", ".....######.....",
        "................", "................", "................", "................"],
    "coat": [
        "................", "................", "..###......###..", ".#####.##.#####.", ".##############.", ".##############.",
        ".##.###z####.##.", ".gg.###z####.gg.", ".gg.###z####.gg.", "....###z####....", "....###z####....", "....bbbbbbbb....",
        "....########....", "................", "................", "................"],
    "trousers": [
        "................", "................", "...bbbbbbbbbb...", "...##########...", "...##########...", "...####..####...",
        "...###....###...", "...###....###...", "...ttt....ttt...", "...###....###...", "...###....###...", "...###....###...",
        "...###....###...", "................", "................", "................"],
    "boots": [
        "................", "................", "................", "................", "................", "...rrr....rrr...",
        "...rrr....rrr...", "...rrr....rrr...", "...rrr....rrr...", "...rrrr...rrrr..", "..rrrrr..rrrrr..", "..sssss..sssss..",
        "................", "................", "................", "................"],
}


def icon(kind: str, ramp, lead: bool) -> Tex:
    t = Tex(16, 16)
    pts = set()
    for y, row in enumerate(ICONS[kind]):
        for x, ch in enumerate(row):
            if ch == ".":
                continue
            pts.add((x, y))
            col = {"#": ramp.base, "o": GLASS.base, "m": RUBBER.shadow, "c": FILTER.light, "g": RUBBER.base, "z": FILTER.light,
                   "b": RUBBER.base, "t": P.WHITE_CERAMIC.light, "r": RUBBER.base, "s": RUBBER.outline}[ch]
            t.px(x, y, col)
    # Light from the top left: a highlight on the first suit pixel of each row, shade on the last.
    for y in range(16):
        xs = [x for x in range(16) if (x, y) in pts]
        if xs and ICONS[kind][y][xs[0]] == "#":
            t.px(xs[0], y, ramp.highlight)
        if xs and ICONS[kind][y][xs[-1]] == "#":
            t.px(xs[-1], y, ramp.shadow)
    if kind == "hood":
        t.px(5, 5, GLASS.shine)
        t.px(6, 6, GLASS.highlight)
    if kind == "boots":
        t.px(3, 5, RUBBER.light)
        t.px(10, 5, RUBBER.light)
    if lead:
        plates = {"hood": [(6, 2), (7, 2), (8, 2), (9, 2), (5, 3), (10, 3)], "coat": [(5, 4), (6, 4), (9, 4), (10, 4), (5, 5), (10, 5)],
                  "trousers": [(3, 9), (4, 9), (11, 9), (12, 9), (3, 10), (12, 10)], "boots": [(3, 9), (4, 9), (10, 9), (11, 9)]}
        for p in plates[kind]:
            t.px(*p, LEAD.base)
        t.px(*plates[kind][0], LEAD.highlight)
    outline(t, pts, (ramp.outline if not lead else ORANGE.outline))
    return t


def main() -> None:
    for asset, ramp, lead in (("hazmat", YELLOW, False), ("lead_lined_hazmat", ORANGE, True)):
        upper, lower = worn(ramp, lead)
        upper.save(paths.TEXTURES / "entity" / "equipment" / "humanoid" / f"{asset}.png")
        lower.save(paths.TEXTURES / "entity" / "equipment" / "humanoid_leggings" / f"{asset}.png")
        write_json(paths.ASSETS / "equipment" / f"{asset}.json", {"layers": {
            "humanoid": [{"texture": f"groundzero:{asset}"}],
            "humanoid_leggings": [{"texture": f"groundzero:{asset}"}]}})
        for kind in ICONS:
            name = f"{asset}_{kind}"
            icon(kind, ramp, lead).save(paths.TEXTURES / "item" / f"{name}.png")
            write_json(paths.MODELS / "item" / f"{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"groundzero:item/{name}"}})
            write_json(paths.ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": f"groundzero:item/{name}"}})
        print(asset)


if __name__ == "__main__":
    main()
