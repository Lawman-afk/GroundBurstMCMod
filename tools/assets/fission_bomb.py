"""Fission Bomb ("A-bomb") block model + texture.

Look (DESIGN.md §15): fat yellow-ochre ovoid with a box tail and a bolted equator seam. Original design: dark nose
cap with four brass contact-fuze nubs, red identification band, gunmetal equator flange with bolts and a lifting
lug, olive-drab box tail with cross struts and trefoil placards, resting on a gunmetal cradle with hazard-striped
saddles.

The bomb lies along Z with its nose to the north (the model's front) and is two blocks long: the model lives in the
front block and extends 16 px into the block behind it (the bomb occupies both, like a bed).
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from gz import palette as P  # noqa: E402
from gz.model import BlockModel, Box  # noqa: E402
from gz.shapes import Disc, band_tone, cylinder_z  # noqa: E402
from gz.tex import Tex  # noqa: E402

NAME = "fission_bomb"
CX, CY = 8.0, 9.0          # bomb axis


def tone_for(d: str) -> int:
    return {"up": 4, "down": 2}.get(d, 3)


def paint_plain(tex: Tex, rect, d: str, ramp: P.Ramp) -> None:
    x, y, w, h = rect
    tex.rect(x, y, w, h, ramp[tone_for(d)])


def build() -> tuple[BlockModel, Tex]:
    m = BlockModel(NAME, tex_size=(128, 128))
    discs: list[tuple[Disc, P.Ramp]] = []
    boxes: dict[str, list[Box]] = {}

    def disc(name, z0, z1, d, ramp):
        dd = cylinder_z(m, name, CX, CY, z0, z1, d)
        discs.append((dd, ramp))
        return dd

    def add(group: str, *bs: Box) -> None:
        boxes.setdefault(group, []).extend(bs)

    ochre, dark, red = P.OCHRE, P.GUNMETAL, P.WARNING_RED
    gun, olive, brass = P.GUNMETAL, P.OLIVE, P.BRASS

    # --- turned body (nose north -> tail south) --------------------------------------------------------------
    disc("nose0", 0.5, 1.5, 6, dark)
    disc("nose1", 1.5, 3, 8, dark)
    disc("shoulder", 3, 5, 12, ochre)
    disc("band", 5, 6, 14, red)
    # The main body stops at the flange instead of running through it, so no faces sit on top of each other.
    main_fore = disc("main", 6, 12, 14, ochre)
    disc("flange", 12, 14, 16, gun)
    main_aft = disc("main_aft", 14, 21, 14, ochre)
    disc("aft0", 21, 23, 12, ochre)
    disc("aft1", 23, 25, 10, ochre)
    disc("aft2", 25, 26, 8, ochre)
    disc("hub", 26, 30, 4, olive)

    # --- contact fuze nubs standing proud of the nose shoulder --------------------------------------------------
    for i, (dx, dy) in enumerate(((-3.5, 3.5), (3.5, 3.5), (-3.5, -3.5), (3.5, -3.5))):
        # Centres ~5 px off-axis: clear of the nose cap (r 4), standing on the shoulder face (r 6).
        add("fuze", m.box(f"fuze{i}", (CX + dx - 1, CY + dy - 1, 1), (CX + dx + 1, CY + dy + 1, 3),
                          faces=("north", "east", "west", "up", "down")))

    # --- box tail: square shroud with X struts ------------------------------------------------------------------
    z0, z1 = 22, 32
    lo, hi, ylo, yhi = 1, 15, 2, 16
    add("shroud",
        m.box("shroud_top", (lo, yhi - 1, z0), (hi, yhi, z1)),
        m.box("shroud_bottom", (lo, ylo, z0), (hi, ylo + 1, z1)),
        m.box("shroud_east", (hi - 1, ylo + 1, z0), (hi, yhi - 1, z1)),
        m.box("shroud_west", (lo, ylo + 1, z0), (lo + 1, yhi - 1, z1)))
    rot = lambda a: {"origin": (CX, CY, 27), "axis": "z", "angle": a}  # noqa: E731
    add("strut",
        m.box("strut_a", (CX - 0.5, ylo + 1.5, z0 + 1), (CX + 0.5, yhi - 1.5, z1 - 1), rotation=rot(45)),
        m.box("strut_b", (CX - 0.5, ylo + 1.5, z0 + 1.15), (CX + 0.5, yhi - 1.5, z1 - 1.15), rotation=rot(-45)))

    # --- lifting lug on the flange ---------------------------------------------------------------------------------
    add("lug",
        m.box("lug_post_n", (7, 17, 12), (9, 19, 12.75)),
        m.box("lug_post_s", (7, 17, 13.25), (9, 19, 14)),
        m.box("lug_bar", (6.75, 18.75, 11.75), (9.25, 19.5, 14.25)))

    # --- cradle ------------------------------------------------------------------------------------------------------
    for i, (za, zb) in enumerate(((7, 10), (17, 20))):
        add("saddle", m.box(f"saddle{i}", (2, 1, za), (14, 3, zb)))
        add("cradle",
            m.box(f"saddle{i}_l", (2, 3, za), (4, 5, zb)),
            m.box(f"saddle{i}_r", (12, 3, za), (14, 5, zb)),
            m.box(f"foot{i}_l", (1, 0, za - 0.5), (5, 1, zb + 0.5)),
            m.box(f"foot{i}_r", (11, 0, za - 0.5), (15, 1, zb + 0.5)))
    add("cradle",
        m.box("rail_l", (2.5, 0.5, 10), (3.5, 1.5, 17), faces=("east", "west", "up")),
        m.box("rail_r", (12.5, 0.5, 10), (13.5, 1.5, 17), faces=("east", "west", "up")))

    m.layout()
    tex = Tex(m.tex_w, m.tex_h)

    # --- painting: turned parts shade as smooth curves ---------------------------------------------------------------
    for dd, ramp in discs:
        for b in dd.boxes:
            m.paint_all(tex, b, lambda t, r, d, b=b, dd=dd, ramp=ramp: t.rect(*r, ramp[band_tone(dd, b, d)]))

    # Flange bolts.
    flange = next(dd for dd, _ in discs if dd.boxes[0].name.startswith("flange"))
    for b in flange.boxes:
        for d in ("east", "west", "up"):
            if d in b.faces:
                x, y, w, h = m.rect(b, d)
                for k in range(x + 1, x + w, 2):
                    tex.px(k, y + (h - 1) // 2, gun.shine)

    # Main body markings on the tall central band.
    fore_band = max(main_fore.boxes, key=lambda bx: bx.size()[1])
    aft_band = max(main_aft.boxes, key=lambda bx: bx.size()[1])
    # West side (image x runs north -> south): inset access hatch forward of the flange.
    x, y, w, h = m.rect(fore_band, "west")
    tex.panel(x + 1, y + 1, 5, h - 2, ochre, base=3, style="inset")
    for px_, py_ in ((x + 1, y + 1), (x + 5, y + 1), (x + 1, y + h - 2), (x + 5, y + h - 2)):
        tex.px(px_, py_, ochre.deep)
    # East side (image x runs south -> north): stencilled serial aft of the flange.
    x, y, w, h = m.rect(aft_band, "east")
    tex.text(x + 1, y, "15", P.BLACK_PAINT.base)
    # Sparse paint wear on the main body's side faces.
    for i, b in enumerate(main_fore.boxes + main_aft.boxes):
        for d in ("east", "west"):
            if d in b.faces:
                rx, ry, rw, rh = m.rect(b, d)
                if rw * rh >= 20:
                    tex.chip_edges(rx, ry, rw, rh, ochre.shadow, seed=7 + i * 13 + (d == "east"), chips=2, edges="tb", max_len=2)

    for b in boxes["fuze"]:
        def fuze(t, r, d):
            x, y, w, h = r
            t.rect(x, y, w, h, brass[{"up": 5, "down": 2}.get(d, 4)])
            if d == "north":
                t.rect(x, y, w, h, brass.shadow)       # recessed striker
                t.px(x, y, brass.shine)
        m.paint_all(tex, b, fuze)

    # Box tail: olive plates, riveted edges, trefoil placards on the side plates.
    for b in boxes["shroud"]:
        def shroud(t, r, d, b=b):
            x, y, w, h = r
            if min(w, h) >= 3:
                t.panel(x, y, w, h, olive, base=tone_for(d), style="raised")
            else:
                t.rect(x, y, w, h, olive[tone_for(d)])
            if w >= 6 and h >= 6:
                t.rivet_row(x + 1, y + 1, w - 2, 3, olive)
                t.rivet_row(x + 1, y + h - 2, w - 2, 3, olive)
            if b.name in ("shroud_east", "shroud_west") and d in ("east", "west"):
                px0, py0 = x + (w - 10) // 2, y + (h - 10) // 2
                t.rect(px0, py0, 10, 10, P.HAZARD_YELLOW.light)
                t.outline(px0, py0, 10, 10, P.HAZARD_YELLOW.shadow)
                t.trefoil(px0 + 5, py0 + 5, 4.3, P.BLACK_PAINT.deep)
        m.paint_all(tex, b, shroud)
    for b in boxes["strut"]:
        m.paint_all(tex, b, lambda t, r, d: paint_plain(t, r, d, olive))
    for b in boxes["lug"]:
        m.paint_all(tex, b, lambda t, r, d: paint_plain(t, r, d, gun))

    # Cradle: gunmetal, hazard stripes on the saddle ends.
    for b in boxes["saddle"]:
        def saddle(t, r, d):
            x, y, w, h = r
            if d in ("north", "south"):
                t.stripes(x, y, w, h, P.HAZARD_YELLOW.base, P.HAZARD_BLACK.base, width=2)
            else:
                paint_plain(t, r, d, gun)
        m.paint_all(tex, b, saddle)
    for b in boxes["cradle"]:
        def cradle(t, r, d):
            x, y, w, h = r
            paint_plain(t, r, d, gun)
            if w >= 4 and h >= 2 and d in ("east", "west", "up"):
                t.rivet(x + 1, y, gun)
        m.paint_all(tex, b, cradle)

    m.particle = m.texture_ref()
    m.fit_display()
    return m, tex


if __name__ == "__main__":
    model, texture = build()
    for p in model.save(texture):
        print(p)
