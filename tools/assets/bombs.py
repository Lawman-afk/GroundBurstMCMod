"""Bombs (DESIGN.md §14, Phase 6 slice 6d): block models lying on their side, nose north (the blockstate turns them to
face where the player looked), each with an armed twin whose lamp blinks (see charges.py). Bigger bombs reach past their
block (models may run from -16 to 32): the collision box stays within it. Round bodies are "plus" prisms along z: a
core box and a box above and below it (never overlapping), stepped down toward the nose and tail.

- HE Bomb: olive general-purpose bomb, yellow nose band, four fins, two lugs.
- Heavy Bomb: the same, twice the size, two yellow bands.
- Thermobaric Bomb: stubby and grey, two orange bands, a blunt nose with a nozzle ring.
- Cluster Bomb: pale grey clamshell casing with a dark split seam along its top and sides, a yellow band.
- Bunker Buster: long and slender, dark grey, a pointed tungsten nose, small fins.
- Napalm Bomb: a silver finless canister with a red band, rounded at both ends (symmetrical: its napalm spreads both
  ways along it).
- MOAB: huge, olive with an orange nose section, lattice fins (cut out).
- Cluster Bomblet (item): a small yellow cylinder with a ribbon tail.
- Napalm Fire: vanilla's fire, drawn on its own block.
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))

from gz import palette as P  # noqa: E402
from gz import paths  # noqa: E402
from gz.model import BlockModel, write_json  # noqa: E402
from gz.palette import fixed  # noqa: E402
from gz.tex import Tex  # noqa: E402
import charges as C  # noqa: E402
import grenades as G  # noqa: E402

shaded = G.shaded
fill_all = G.fill_all
OLIVE = P.OLIVE
STEEL = P.STEEL
GUNMETAL = P.GUNMETAL
YELLOW = P.HAZARD_YELLOW
ORANGE = fixed("bomb_orange", "#3e1a06", "#6a2c0a", "#9a4210", "#c45c1a", "#de7a30", "#ee9c5a", "#f8c898")
CASING = fixed("cluster_casing", "#5a5e62", "#7a7e82", "#9a9ea2", "#b8bcc0", "#d0d4d6", "#e2e4e6", "#f2f4f4")
TUNGSTEN = fixed("tungsten_nose", "#2a2c30", "#44474c", "#61656b", "#80858b", "#a0a5aa", "#c0c4c8", "#e0e2e4")


def hprism(m: BlockModel, name: str, cy: float, r: float, w: float, z0: float, z1: float, cx: float = 8,
           ends: tuple[str, ...] = ("north", "south")) -> list[str]:
    """A rounded column lying along z: a core box (full width) and boxes above and below it (full height). The core
    keeps its top and bottom (its shoulders show either side of the boxes on it), and every box its ends, so no gaps
    show along the body or round the steps."""
    m.box(f"{name}_c", (cx - r, cy - w, z0), (cx + r, cy + w, z1), faces=("east", "west", "up", "down") + ends)
    m.box(f"{name}_u", (cx - w, cy + w, z0), (cx + w, cy + r, z1), faces=("up", "east", "west") + ends)
    m.box(f"{name}_d", (cx - w, cy - r, z0), (cx + w, cy - w, z1), faces=("down", "east", "west") + ends)
    return [f"{name}_c", f"{name}_u", f"{name}_d"]


def bomb(name: str, z0: float, z1: float, r: float, *, body, nose_paint=None, nose: list[tuple[float, float]],
         tail: list[tuple[float, float]], fins: float = 0, fin_len: float = 3, bands: list[tuple[float, object]] = (),
         seam=None, lugs: bool = True, lattice: bool = False, nose_ramp=None, ring: float = 0) -> list[str]:
    """A bomb along z (nose at z0): body radius r; `nose` and `tail` are (length, radius) steps outward from the body;
    fins of radius `fins` at the tail; `bands` are (z, colour) stripes round the body."""
    m = BlockModel(name, tex_size=(128, 128), kind="block", render_type="minecraft:cutout" if lattice else None)
    fin_r = max(fins, r)
    cy = fin_r + 0.3
    w = round(r * 0.66, 2)
    body_z0 = z0 + sum(s[0] for s in nose)
    body_z1 = z1 - sum(s[0] for s in tail)
    parts = {"body": hprism(m, "body", cy, r, w, body_z0, body_z1)}
    z = body_z0
    nose_boxes = []
    for i, (length, rad) in enumerate(nose):                     # stepping down toward the tip
        nose_boxes += hprism(m, f"nose{i}", cy, rad, round(rad * 0.66, 2), z - length, z, ends=("north", "south"))
        z -= length
    z = body_z1
    tail_boxes = []
    for i, (length, rad) in enumerate(tail):
        tail_boxes += hprism(m, f"tail{i}", cy, rad, round(rad * 0.66, 2), z, z + length, ends=("north", "south"))
        z += length
    fin_boxes = []
    if fins:
        tail_r = tail[0][1] if tail else r
        fz0, fz1 = z1 - fin_len, z1
        t = 0.25
        m.box("fin_u", (8 - t, cy + tail_r * 0.66, fz0), (8 + t, cy + fins, fz1))
        m.box("fin_d", (8 - t, cy - fins, fz0), (8 + t, cy - tail_r * 0.66, fz1))
        m.box("fin_e", (8 + tail_r, cy - t, fz0), (8 + fins, cy + t, fz1))
        m.box("fin_w", (8 - fins, cy - t, fz0), (8 - tail_r, cy + t, fz1))
        fin_boxes = ["fin_u", "fin_d", "fin_e", "fin_w"]
    if ring:
        rz = z0 + nose[0][0] * 0.4 if nose else z0
        hprism(m, "ring", cy, ring, round(ring * 0.66, 2), rz - 0.6, rz + 0.6)
    lug_boxes = []
    if lugs:
        for k, lz in enumerate((body_z0 + (body_z1 - body_z0) * 0.3, body_z0 + (body_z1 - body_z0) * 0.7)):
            m.box(f"lug{k}", (7.6, cy + r, lz - 0.4), (8.4, cy + r + 0.6, lz + 0.4), faces=("north", "south", "east", "west", "up"))
            lug_boxes.append(f"lug{k}")
    mid = (body_z0 + body_z1) / 2
    C.lamp(m, (8.6, cy + r, mid - 0.4), (9.4, cy + r + 0.4, mid + 0.4))
    m.layout()

    def paint(m: BlockModel, t: Tex, lamp_colour) -> None:
        def body_fn(t: Tex, rect, d):
            shaded(body)(t, rect, d)
            x, y, wd, h = rect
            if d in ("north", "south"):
                return
            # The bands and seam. Side faces run along z on their u axis; the top and bottom faces on their v axis
            # (the top's north edge is its first row, the bottom's its last).
            for bz, colour in bands:
                frac = (bz - body_z0) / (body_z1 - body_z0)
                if d in ("up", "down"):
                    v = int(round(frac * (h - 1)))
                    t.hline(x, y + (v if d == "up" else h - 1 - v), wd, colour)
                else:
                    u = int(round(frac * (wd - 1)))
                    t.vline(x + (wd - 1 - u if d == "east" else u), y, h, colour)
            if seam is not None:
                if d == "up":
                    t.vline(x + wd // 2, y, h, seam)               # the clamshell's split, along the top
                elif d in ("east", "west"):
                    t.hline(x, y + h // 2, wd, seam)               # and along each side
        fill_all(m, t, parts["body"], body_fn)
        fill_all(m, t, nose_boxes, shaded(nose_ramp or body))
        fill_all(m, t, tail_boxes, shaded(body))
        if ring:
            fill_all(m, t, ["ring_c", "ring_u", "ring_d"], shaded(GUNMETAL))
        if fins:
            def fin(t: Tex, rect, d):
                shaded(body)(t, rect, d)
                x, y, wd, h = rect
                if lattice and wd > 3 and h > 3:                 # a lattice: a grid of holes through the fin
                    for i in range(1, wd - 1, 2):
                        for j in range(1, h - 1, 2):
                            t.px(x + i, y + j, (0, 0, 0, 0))
            fill_all(m, t, fin_boxes, fin)
        fill_all(m, t, lug_boxes, shaded(GUNMETAL))
        C.paint_lamp(m, t, lamp_colour)
    finish(m)
    return C.save(m, paint)


def finish(m: BlockModel) -> None:
    """Display: fitted in the GUI; in the hand and on the ground scaled so the bomb is a bomb-sized thing, not 3 blocks."""
    m.fit_display(gui_rotation=(30, 225, 0), gui_margin=1.0)
    lo, hi = m.bounds()
    c = [(lo[i] + hi[i]) / 2 for i in range(3)]
    size = max(hi[i] - lo[i] for i in range(3))
    k = min(1.0, 14 / size)

    def ctx(scale: float, base, rotation=(0, 0, 0)) -> dict:
        s = scale * k
        return {"rotation": list(rotation), "translation": [round(base[i] - (c[i] - 8) * s, 3) for i in range(3)], "scale": [round(s, 4)] * 3}
    m.display["ground"] = ctx(0.5, (0, 2, 0))
    m.display["fixed"] = ctx(0.8, (0, 0, 0))
    m.display["thirdperson_righthand"] = ctx(0.5, (0, 2.5, 0), (75, 45, 0))
    m.display["thirdperson_lefthand"] = ctx(0.5, (0, 2.5, 0), (75, 45, 0))
    m.display["firstperson_righthand"] = ctx(0.45, (0, 2, 0), (0, 45, 0))
    m.display["firstperson_lefthand"] = ctx(0.45, (0, 2, 0), (0, 225, 0))
    m.display["head"] = ctx(0.8, (0, 6, 0))


def bomblet() -> None:
    m = BlockModel("cluster_bomblet", tex_size=(32, 32), kind="item")
    can = G.prism(m, "can", 8, 8, 2, 1.3, 3, 10)
    m.box("ribbon", (7.6, 10, 7.9), (8.4, 13, 8.1))
    m.layout()
    t = Tex(m.tex_w, m.tex_h)

    def body(t: Tex, rect, d):
        shaded(YELLOW)(t, rect, d)
        x, y, wd, h = rect
        if d in ("north", "south", "east", "west"):
            t.hline(x, y + h - 2, wd, P.HAZARD_BLACK[3])
    fill_all(m, t, can, body)
    m.paint_all(t, "ribbon", shaded(P.WHITE_CERAMIC))
    G.finish(m, t)


def napalm_fire() -> None:
    """Vanilla's fire, on our block (cut out, as vanilla draws fire)."""
    for k in (0, 1):
        write_json(paths.MODELS / "block" / f"napalm_fire_floor{k}.json", {"parent": f"minecraft:block/fire_floor{k}", "render_type": "minecraft:cutout"})
    variants = {f"age={a}": [{"model": f"groundzero:block/napalm_fire_floor{k}"} for k in (0, 1)] for a in range(4)}
    write_json(paths.ASSETS / "blockstates" / "napalm_fire.json", {"variants": variants})


BOMBS = ["he_bomb", "heavy_bomb", "thermobaric_bomb", "cluster_bomb", "bunker_buster", "napalm_bomb", "moab"]


def blockstates() -> None:
    for name in BOMBS:
        v = {}
        for facing, rot in C.HORIZONTAL.items():
            for armed in (False, True):
                v[f"armed={str(armed).lower()},facing={facing}"] = {"model": f"groundzero:block/{name}{'_armed' if armed else ''}", **rot}
        write_json(paths.ASSETS / "blockstates" / f"{name}.json", {"variants": v})
        write_json(paths.ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": f"groundzero:block/{name}"}})


def main() -> None:
    names = []
    names += bomb("he_bomb", 1, 15, 2.5, body=OLIVE, nose=[(1.5, 2), (1.5, 1.2)], tail=[(1.5, 1.8), (1.5, 1.2)], fins=3.2, fin_len=3,
                  bands=[(4.2, YELLOW[4])])
    names += bomb("heavy_bomb", -5, 21, 4.5, body=OLIVE, nose=[(2.5, 3.6), (2, 2.4), (1, 1.2)], tail=[(2.5, 3.2), (2.5, 2)], fins=5.4,
                  fin_len=5, bands=[(2.5, YELLOW[4]), (4.0, YELLOW[4])])
    names += bomb("thermobaric_bomb", 1, 15, 4, body=GUNMETAL, nose=[(1.5, 3)], tail=[(2, 3), (1.5, 2)], fins=4.6, fin_len=3,
                  bands=[(5, ORANGE[4]), (10, ORANGE[4])], ring=3.4)
    names += bomb("cluster_bomb", -2, 18, 3.5, body=CASING, nose=[(2, 2.6), (1.5, 1.6)], tail=[(2, 2.6), (2, 1.6)], fins=4.4, fin_len=4,
                  bands=[(5, YELLOW[4])], seam=P.HAZARD_BLACK[2])
    names += bomb("bunker_buster", -8, 24, 2.5, body=GUNMETAL, nose_ramp=TUNGSTEN, nose=[(2, 2), (2, 1.4), (2, 0.8)], tail=[(2, 1.8), (1, 1.2)],
                  fins=3, fin_len=3, bands=[(-1, YELLOW[4])])
    names += bomb("napalm_bomb", -1, 17, 3, body=STEEL, nose=[(1, 2.4), (1, 1.6)], tail=[(1, 2.4), (1, 1.6)], fins=0,
                  bands=[(7, P.WARNING_RED[3])], lugs=True)
    names += bomb("moab", -14, 30, 7, body=OLIVE, nose_ramp=ORANGE, nose=[(4, 6), (3, 4.4), (2, 2.6)], tail=[(3, 5.6), (3, 4)], fins=8.5,
                  fin_len=6, bands=[(0, ORANGE[4])], lattice=True)
    blockstates()
    bomblet()
    G.item_defs(["cluster_bomblet"])
    napalm_fire()
    print("bombs written:", ", ".join(names))


if __name__ == "__main__":
    main()
