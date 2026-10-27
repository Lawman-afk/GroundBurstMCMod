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
# On vanilla's four armour silhouettes, so each reads at a glance as the piece it is (the user found the first icons
# did not look like armour). What vanilla's icons share (studied from the 26.3 iron, gold, chainmail, netherite and
# turtle sets, never copied): the helmet a dome about 10 px wide over the dark of its face; the chestplate a body under
# two shoulder lobes, a V cut at the neck, tapering to a rounded hem; the leggings a waistband over two legs with a gap
# between; the boots two boots side on, toes turned out; light from the top left; an outline lit above and left of the
# shape and dark below and right of it. The hazmat pieces in those shapes: the hood's dome over a wide visor, its filter
# under the chin between the hood's drapes; the coat zipped down the middle under a storm flap, a waist seam, a trefoil
# patch on the chest; the trousers with an elastic waist, reflective tape at the knees and taped cuffs; black rubber
# overboots under the suit's taped cuffs, grey treads. The lead-lined suit has riveted lead plates over them: a crown
# plate, a breastplate (over the zip and the patch), thigh plates and toe caps.
#
# Keys: '.' clear; 'S' the suit's plastic, 'c' a fold or seam in it; 'V' the visor's glass, 'g' and 'v' its glints;
# 'R' black rubber; 'F' a filter's or a tread's grey steel, 'f' the filter's face; 'Z' the zip; 'k' and 'y' the patch,
# black and its yellow trefoil; 'T' reflective tape; 'K' black tape; in the lead plates, 'P' a plate and 'Q' a rivet.
ICONS = {
    "hood": [
        "................",
        "................",
        "................",
        ".....SSSSSS.....",
        "....SSSSSSSS....",
        "...SSVVVVVVSS...",
        "...SVgvVVVVVS...",
        "...SVvVVVVVVS...",
        "...SSVVVVVVSS...",
        "...SSSRFFRSSS...",
        "...SS.RffR.SS...",
        "...SS..FF..SS...",
        "................",
        "................",
        "................",
        "................"],
    "coat": [
        "................",
        "................",
        "................",
        "..SSS......SSS..",
        "..SSSS....SSSS..",
        "..SSSSS..SSSSS..",
        "..SSSSSZcSSSSS..",
        "...SSSSZcSSSS...",
        "....SSSZckkkS...",
        "....SSSZckykS...",
        "....SSSZckkkS...",
        "....cccZcccc....",
        "....SSSZcSSS....",
        ".....SSZcSS.....",
        "................",
        "................"],
    "trousers": [
        "................",
        "................",
        "................",
        "...SSSSSSSSSS...",
        "...SSSSSSSSSS...",
        "...cccccccccc...",
        "...SSSS..SSSS...",
        "...SSS....SSS...",
        "...SSS....SSS...",
        "...TTT....TTT...",
        "...SSS....SSS...",
        "...SSS....SSS...",
        "...KKK....KKK...",
        "................",
        "................",
        "................"],
    "boots": [
        "................",
        "................",
        "................",
        "...SSS....SSS...",
        "...KKK....KKK...",
        "...RRR....RRR...",
        "...RRR....RRR...",
        "...RRR....RRR...",
        "...RRR....RRR...",
        "..RRRR....RRRR..",
        ".RRRRR....RRRRR.",
        ".FFFFF....FFFFF.",
        "................",
        "................",
        "................",
        "................"],
}
#: The lead-lined suit's plates, laid over the icons above (rows not given are clear).
LEAD_PLATES = {
    "hood": [
        "................",
        "................",
        "................",
        ".....PPPPPP.....",
        "....PQPPPPQP...."],
    "coat": [
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        ".....QPPPPQ.....",
        ".....PPPPPP.....",
        ".....PPPPPP.....",
        ".....QPPPPQ....."],
    "trousers": [
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "...PPP....PPP...",
        "...PQP....PQP..."],
    "boots": [
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "..PP........PP..",
        ".PPP........PPP."],
}
#: Which surface each key belongs to: a surface's edges are lit or shaded where it meets another (or the outside).
SURFACE = {"S": "suit", "c": "suit", "Z": "suit", "T": "suit", "K": "suit", "k": "patch", "y": "patch", "P": "lead", "Q": "lead",
           "V": "glass", "g": "glass", "v": "glass", "R": "rubber", "F": "steel", "f": "steel"}
#: How far the light runs across a whole icon, in shades, from its top left to its bottom right.
SLOPE = {"suit": 1.0, "lead": 1.0, "glass": 0.8, "rubber": 0.8, "steel": 0.5}


def _grid(rows: list[str]) -> dict[tuple[int, int], str]:
    return {(x, y): ch for y, row in enumerate(rows) for x, ch in enumerate(row) if ch != "."}


def icon(kind: str, ramp, lead: bool) -> Tex:
    g = _grid(ICONS[kind])
    if lead:
        for p, ch in _grid(LEAD_PLATES[kind]).items():
            if p in g:
                g[p] = ch                                  # a plate over the suit (and over the zip and the patch)
    xs, ys = [p[0] for p in g], [p[1] for p in g]
    cx, cy = (min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2
    span = max(max(xs) - min(xs), max(ys) - min(ys)) + 1

    def tone(p, base: int, top: int = 2, left: int = 1, down: int = -1, right: int = -1) -> int:
        """A shade on a ramp: the surface's base, lit along its top and left edges, shaded along its bottom and right
        ones, a little lighter toward the icon's top left."""
        x, y = p
        own = SURFACE[g[p]]
        k = base
        for (dx, dy), step in (((0, -1), top), ((-1, 0), left), ((0, 1), down), ((1, 0), right)):
            if SURFACE.get(g.get((x + dx, y + dy), "."), None) != own:
                k += step
        return k + round(SLOPE[own] * ((cx - x) + (cy - y)) / span)

    t = Tex(16, 16)
    for p, ch in g.items():
        if ch in "Sc":
            t.px(*p, ramp[max(1, min(6, tone(p, 3) - (ch == "c")))])
        elif ch == "P":
            t.px(*p, LEAD[max(1, min(5, tone(p, 3)))])
        elif ch == "Q":
            t.px(*p, LEAD.shine)
        elif ch == "V":
            t.px(*p, GLASS[max(1, min(4, tone(p, 2, top=1, left=0, down=0)))])
        elif ch in "gv":
            t.px(*p, GLASS.shine if ch == "g" else GLASS.highlight)
        elif ch == "R":
            t.px(*p, RUBBER[max(1, min(6, tone(p, 3)))])
        elif ch == "F":
            t.px(*p, FILTER[max(1, min(6, tone(p, 3)))])
        elif ch == "f":
            t.px(*p, FILTER.deep)
        elif ch == "Z":
            t.px(*p, FILTER.highlight if p[1] % 2 == 0 else FILTER.light)     # the zip's teeth
        elif ch == "k":
            t.px(*p, P.HAZARD_BLACK.deep)
        elif ch == "y":
            t.px(*p, P.HAZARD_YELLOW.highlight)
        elif ch == "T":
            t.px(*p, P.WHITE_CERAMIC[max(2, min(6, tone(p, 4, top=1, down=0)))])
        elif ch == "K":
            t.px(*p, RUBBER[max(0, min(4, tone(p, 2, top=1)))])
    # The outline: lit above and left of the shape, dark below and right of it; rubber's own near-black round the boots.
    for y in range(16):
        for x in range(16):
            near = [g[q] for q in ((x + 1, y), (x, y + 1), (x - 1, y), (x, y - 1)) if q in g]
            if (x, y) in g or not near:
                continue
            dark = (x - 1, y) in g or (x, y - 1) in g
            if all(SURFACE[ch] in ("rubber", "steel") for ch in near):
                t.px(x, y, RUBBER.outline)
            else:
                t.px(x, y, ramp.outline if dark else ramp.deep)
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
