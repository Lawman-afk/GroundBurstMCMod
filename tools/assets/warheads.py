"""Warheads (DESIGN.md §14, §17.6; Phase 6 slice 6d, bombs until they became missile warheads): block models standing
nose up on their mounting ring, the way they wait to be fitted to a missile. Each has an armed twin whose lamp blinks
(see charges.py). The bigger ones stand taller than their block (models may run up to y 32): the collision box stays
within it. Round bodies are "plus" prisms (grenades.prism): a core box and a box either side of it, stepped in toward
the nose. The front (stencil band, lamp) is the model's south side: the blockstate turns it to face the player.

- HE Warhead: olive, one yellow band (high explosive), a steel fuze tip.
- Heavy HE Warhead: a size up and much taller, two yellow bands.
- Thermobaric Warhead: stubby gunmetal, two orange bands, a blunt cap and a ring of fuel ports.
- Cluster Warhead: pale grey clamshell casing split down its sides, a yellow band and black dashes.
- Bunker Buster Warhead: long and slim, dark, a pointed tungsten penetrator nose.
- Napalm Warhead: silver, a red band (incendiary), a rounded nose.
- Massive Ordnance Warhead: huge, olive with an orange nose.
- Poison Gas Warhead: yellow-green clamshell casing (a cluster of gas canisters), a yellow band.
- Smoke Warhead: grey clamshell casing (a cluster of smoke canisters), a white band.
- Cryo Cluster Warhead: ice-blue clamshell casing (a cluster of cryo canisters), white band and dashes.
- EMP Cluster Warhead: dark clamshell casing (a cluster of EMP canisters), copper band and dashes.
- Chemical Warhead: pale grey with the two green bands of a chemical weapon, a steel nose.
- Toxic Waste Warhead: a dark drum of a body, a glowing toxin-green nose and band, a hazard-dashed ring.
- Tactical Nuclear Warhead (Phase 8): a squat olive cylinder with a blunt gunmetal cap, a yellow band over a black one
  and a radiation trefoil on its front.
- Fission Warhead (Phase 8): a fat yellow-ochre body under a rounded dark nose, a red band and a trefoil.
- Boosted Fission Warhead (8b): a sleeker grey body under a sharper dark nose, two cyan boost-gas bands, a trefoil.
- Neutron Warhead (8b): slim and silver, three cyan rings and a row of neutron-reflector studs.
- Dirty Warhead (8b): a rusty drum with a flat cap, two strips of yellow tape and a trefoil.
- Thermonuclear Warhead (8c, only ever built whole into an intercontinental missile): the biggest nuclear body, dull
  olive drab under a blunt dark nose, two yellow bands, a trefoil.
- Cobalt Warhead (8c): as big, blue-black, its cobalt jacket's plates in cobalt-blue bands with seams between, a trefoil.
- Cluster Bomblet (item): a small yellow cylinder with a ribbon tail.
- Napalm Fire: vanilla's fire, drawn on its own block.

NOSES gives each warhead's nose colours to the missile (launch.py): a missile shows the warhead it carries.
"""
from __future__ import annotations

import sys
from dataclasses import dataclass, field
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
RED = P.WARNING_RED
BLACK = P.HAZARD_BLACK
ORANGE = fixed("bomb_orange", "#3e1a06", "#6a2c0a", "#9a4210", "#c45c1a", "#de7a30", "#ee9c5a", "#f8c898")
CASING = fixed("cluster_casing", "#5a5e62", "#7a7e82", "#9a9ea2", "#b8bcc0", "#d0d4d6", "#e2e4e6", "#f2f4f4")
TUNGSTEN = fixed("tungsten_nose", "#2a2c30", "#44474c", "#61656b", "#80858b", "#a0a5aa", "#c0c4c8", "#e0e2e4")
GAS = fixed("gas_casing", "#3a3f12", "#5a6118", "#7c8520", "#9ca82c", "#b8c44a", "#cfd872", "#e4eaa2")
WASTE = fixed("waste_drum", "#14160e", "#1e2214", "#2a301c", "#363e24", "#46502e", "#58643a", "#6e7a4a")
EMP_CASING = fixed("emp_casing", "#1c2026", "#282e36", "#363e48", "#48525e", "#5e6a78", "#7a8896", "#9eacba")
BOOST_GREY = fixed("boost_grey", "#3c4044", "#555a5f", "#6f757a", "#8c9297", "#a7adb1", "#c0c5c8", "#dadee0")
DRAB = fixed("thermo_drab", "#1a1c10", "#272a18", "#363a22", "#474c2e", "#5a603c", "#6f764c", "#8a9064")
BLUE_BLACK = fixed("blue_black", "#08090e", "#0e1018", "#151926", "#1d2334", "#262e44", "#323c56", "#46526e")
COBALT = fixed("cobalt_blue", "#0a1430", "#102050", "#183070", "#204496", "#2c5cbc", "#4a7ed8", "#7aa6ee")
RUST = fixed("waste_rust", "#2a1206", "#46200c", "#663014", "#86441c", "#a35a28", "#bd7638", "#d69a5c")
OCHRE = fixed("bomb_ochre", "#3a2a08", "#5e440e", "#846216", "#a8801e", "#c49c34", "#d8b85a", "#ecd690")
NUKE_NOSE = fixed("nuke_nose", "#121416", "#1c1f22", "#282c30", "#363b40", "#474d53", "#5c636a", "#788088")
DARK = fixed("penetrator_dark", "#16181a", "#202326", "#2c3034", "#3a3e44", "#4a4f56", "#5e646c", "#767c84")


@dataclass
class Spec:
    """A warhead, bottom to top: its mounting ring, its body, its nose steps (top y, radius), its tip."""
    name: str
    r: float                                   # body radius
    ring: tuple[float, float]                  # (height, radius) of the mounting ring at the bottom
    body_top: float
    nose: list[tuple[float, float]]            # (top y, radius) steps above the body
    tip: tuple[float, float]                   # (top y, radius) of the fuze tip
    body: tuple
    nose_ramp: tuple
    tip_ramp: tuple = STEEL
    bands: list[tuple[float, tuple]] = field(default_factory=list)   # (y, colour) round the body
    seam: tuple | None = None                  # a clamshell split down the sides
    dashes: tuple | None = None                # a row of dashes round the body (y, colour)
    ports: bool = False                        # fuel ports round the shoulder
    trefoil: float | None = None               # a radiation trefoil on the front at this height


def ogive(base: float, height: float, r: float, steps: int, sharp: float) -> list[tuple[float, float]]:
    """A nose of `steps` steps from radius r at `base` toward a point `height` above: radius r * (1 - t^sharp)^0.65 at
    each step's middle (sharp 1: a cone; higher: blunter, rounder shoulders)."""
    return [(round(base + height * (i + 1) / steps, 2), max(0.4, round(r * (1 - ((i + 0.5) / steps) ** sharp) ** 0.65, 2)))
            for i in range(steps)]


WARHEADS = [
    Spec("he_warhead", 4, (1.5, 4.6), 8, ogive(8, 7, 3.8, 5, 1.6), (15.8, 0.5), OLIVE, OLIVE,
         bands=[(6.5, YELLOW[4])]),
    Spec("heavy_warhead", 6, (2, 6.8), 14, ogive(14, 11.5, 5.8, 7, 1.6), (26.4, 0.6), OLIVE, OLIVE,
         bands=[(10, YELLOW[4]), (12, YELLOW[4])]),
    Spec("thermobaric_warhead", 5, (1.5, 5.6), 9, ogive(9, 4.5, 4.8, 4, 2.6), (14.4, 1.4), GUNMETAL, GUNMETAL, GUNMETAL,
         bands=[(4, ORANGE[4]), (7, ORANGE[4])], ports=True),
    Spec("cluster_warhead", 4.5, (1.5, 5.1), 10, ogive(10, 5.5, 4.3, 5, 1.8), (16, 0.5), CASING, CASING,
         bands=[(8.5, YELLOW[4])], seam=BLACK[3], dashes=(4, BLACK[3])),
    Spec("bunker_buster_warhead", 2.5, (1.5, 3.2), 14, ogive(14, 10, 2.4, 6, 1.0), (25.4, 0.4), DARK, TUNGSTEN, TUNGSTEN,
         bands=[(3, YELLOW[4])]),
    Spec("napalm_warhead", 4, (1.5, 4.6), 9, ogive(9, 4, 3.8, 4, 2.4), (13.6, 0.8), STEEL, STEEL, STEEL,
         bands=[(5, RED[4])]),
    Spec("massive_warhead", 7, (2, 7.8), 18, ogive(18, 11.5, 6.8, 8, 1.6), (30.4, 0.7), OLIVE, ORANGE,
         bands=[(16, ORANGE[4])]),
    Spec("gas_warhead", 4.5, (1.5, 5.1), 10, ogive(10, 5.5, 4.3, 5, 1.8), (16, 0.5), GAS, GAS,
         bands=[(8.5, YELLOW[5])], seam=BLACK[3], dashes=(4, YELLOW[3])),
    Spec("smoke_warhead", 4.5, (1.5, 5.1), 10, ogive(10, 5.5, 4.3, 5, 1.8), (16, 0.5), STEEL, STEEL,
         bands=[(8.5, P.WHITE_CERAMIC[5])], seam=BLACK[3], dashes=(4, P.WHITE_CERAMIC[4])),
    Spec("cryo_warhead", 4.5, (1.5, 5.1), 10, ogive(10, 5.5, 4.3, 5, 1.8), (16, 0.5), G.CRYO_CAN, G.CRYO_CAN,
         bands=[(8.5, P.WHITE_CERAMIC[6])], seam=G.CRYO_CAN[0], dashes=(4, P.WHITE_CERAMIC[5])),
    Spec("emp_warhead", 4.5, (1.5, 5.1), 10, ogive(10, 5.5, 4.3, 5, 1.8), (16, 0.5), EMP_CASING, EMP_CASING,
         bands=[(8.5, P.COPPER[5])], seam=BLACK[1], dashes=(4, P.COPPER[4])),
    Spec("chemical_warhead", 4.5, (1.5, 5.1), 10, ogive(10, 5.5, 4.3, 5, 1.8), (16, 0.5), P.WHITE_CERAMIC, P.STEEL, P.STEEL,
         bands=[(5, P.INDICATOR_GREEN[3]), (7.5, P.INDICATOR_GREEN[3])]),
    Spec("toxic_waste_warhead", 5, (1.5, 5.6), 10, ogive(10, 4.5, 4.8, 4, 2.4), (15, 1.2), WASTE, P.TOXIN, WASTE,
         bands=[(7.5, P.TOXIN[4])], dashes=(4, YELLOW[4])),
    Spec("tactical_nuclear_warhead", 4, (1.5, 4.6), 9.5, ogive(9.5, 3.8, 3.8, 4, 2.8), (13.6, 1.2), OLIVE, NUKE_NOSE, STEEL,
         bands=[(8.4, YELLOW[4]), (7.8, BLACK[2])], trefoil=5.0),
    Spec("fission_warhead", 6, (2, 6.8), 12, ogive(12, 7.5, 5.8, 6, 2.4), (20, 1.2), OCHRE, NUKE_NOSE, STEEL,
         bands=[(10.5, RED[4])], trefoil=6.5),
    Spec("boosted_fission_warhead", 5.5, (2, 6.3), 12.5, ogive(12.5, 8.5, 5.3, 6, 1.9), (21.4, 1.0), BOOST_GREY, NUKE_NOSE, STEEL,
         bands=[(11, P.CHERENKOV[4]), (9.8, P.CHERENKOV[3])], trefoil=6.5),
    Spec("neutron_warhead", 3.2, (1.5, 3.8), 13, ogive(13, 6, 3.0, 5, 1.6), (19.6, 0.6), STEEL, STEEL, STEEL,
         bands=[(11, P.CHERENKOV[4]), (8, P.CHERENKOV[4]), (5, P.CHERENKOV[4])], dashes=(3, P.STEEL[6])),
    Spec("dirty_warhead", 5, (1.5, 5.6), 10, ogive(10, 2.5, 4.8, 3, 3.2), (13, 1.5), RUST, RUST, STEEL,
         bands=[(8.6, YELLOW[4]), (3.4, YELLOW[4])], trefoil=6),
    Spec("thermonuclear_warhead", 6.5, (2, 7.3), 14, ogive(14, 7.5, 6.3, 6, 2.8), (22.3, 1.5), DRAB, NUKE_NOSE, STEEL,
         bands=[(12.6, YELLOW[4]), (3.6, YELLOW[4])], trefoil=8),
    Spec("cobalt_warhead", 6.5, (2, 7.3), 14, ogive(14, 7.5, 6.3, 6, 2.8), (22.3, 1.5), BLUE_BLACK, BLUE_BLACK, STEEL,
         bands=[(12.6, COBALT[4]), (3.6, COBALT[4])], dashes=(10.4, COBALT[3]), trefoil=7.2),
]

#: The trefoil, five pixels square: black blades (K) on a yellow ground.
TREFOIL = ["YYYYY", "YKYKY", "YYKYY", "YYKYY", "YYYYY"]

#: A missile's nose for each warhead it can carry: (nose ramp, band colour or None, tip ramp). "none": bare steel.
NOSES = {"none": (STEEL, None, STEEL)}
for _s in WARHEADS:
    NOSES[_s.name] = (_s.nose_ramp, _s.bands[0][1] if _s.bands else None, _s.tip_ramp)


def side_row(rect, y0: float, y1: float, y: float) -> int:
    """The texture row of height y on a side face of a box running y0..y1 (image top = the box's top)."""
    x, top, w, h = rect
    return top + max(0, min(h - 1, int(round((y1 - y) * h / (y1 - y0) - 0.5))))


def warhead(s: Spec) -> list[str]:
    m = BlockModel(s.name, tex_size=(128, 128), kind="block")
    rh, rr = s.ring
    ring = G.prism(m, "ring", 8, 8, rr, round(rr * 0.66, 2), 0, rh)
    body = G.prism(m, "body", 8, 8, s.r, round(s.r * 0.66, 2), rh, s.body_top)
    nose, y = [], s.body_top
    for i, (top, r) in enumerate(s.nose):
        nose += G.prism(m, f"nose{i}", 8, 8, r, round(r * 0.66, 2), y, top)
        y = top
    tip_top, tip_r = s.tip
    m.box("tip", (8 - tip_r, y, 8 - tip_r), (8 + tip_r, tip_top, 8 + tip_r))
    ports = []
    if s.ports:                                       # fuel ports round the shoulder, one each side
        py = s.body_top - 1.5
        for d, (a, b) in {"n": ((7.2, py, 8 - s.r - 0.5), (8.8, py + 1, 8 - s.r)), "s": ((7.2, py, 8 + s.r), (8.8, py + 1, 8 + s.r + 0.5)),
                          "w": ((8 - s.r - 0.5, py, 7.2), (8 - s.r, py + 1, 8.8)), "e": ((8 + s.r, py, 7.2), (8 + s.r + 0.5, py + 1, 8.8))}.items():
            m.box(f"port_{d}", a, b)
            ports.append(f"port_{d}")
    # The armed lamp on the front of the mounting ring.
    C.lamp(m, (7.4, rh * 0.25, 8 + rr), (8.6, rh * 0.25 + 0.8, 8 + rr + 0.3), faces=("north", "south", "east", "west", "up", "down"))
    m.layout()

    def paint(m: BlockModel, t: Tex, lamp_colour) -> None:
        def ring_fn(t: Tex, rect, d):
            shaded(GUNMETAL)(t, rect, d)
            x, yy, w, h = rect
            if d in ("north", "south", "east", "west") and w > 2:
                for bx in range(x + 1, x + w - 1, 3):     # bolt heads round the ring
                    t.px(bx, yy + h // 2, STEEL[5])
        fill_all(m, t, ring, ring_fn)

        def body_fn(t: Tex, rect, d):
            shaded(s.body)(t, rect, d)
            if d not in ("north", "south", "east", "west"):
                return
            x, yy, w, h = rect
            for by, colour in s.bands:
                t.hline(x, side_row(rect, rh, s.body_top, by), w, colour)
            if s.dashes is not None:
                row = side_row(rect, rh, s.body_top, s.dashes[0])
                for bx in range(x, x + w, 3):
                    t.px(bx, row, s.dashes[1])
            if s.seam is not None and d in ("east", "west") and w > 2:
                t.vline(x + w // 2, yy, h, s.seam)       # the clamshell's split, down each side
            if s.trefoil is not None and d == "south" and w >= 5:
                row = side_row(rect, rh, s.body_top, s.trefoil) - 2
                for j, line in enumerate(TREFOIL):
                    for i, ch in enumerate(line):
                        t.px(x + (w - 5) // 2 + i, row + j, BLACK[1] if ch == "K" else YELLOW[4])
        fill_all(m, t, body, body_fn)
        fill_all(m, t, nose, shaded(s.nose_ramp))
        m.paint_all(t, "tip", shaded(s.tip_ramp))
        fill_all(m, t, ports, shaded(BLACK))
        C.paint_lamp(m, t, lamp_colour)
    finish(m)
    return C.save(m, paint)


def finish(m: BlockModel) -> None:
    """Display: fitted in the GUI; in the hand and on the ground scaled so a warhead is warhead-sized, not 2 blocks."""
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


def blockstates() -> None:
    for s in WARHEADS:
        v = {}
        for facing, rot in C.HORIZONTAL.items():
            for armed in (False, True):
                v[f"armed={str(armed).lower()},facing={facing}"] = {"model": f"groundzero:block/{s.name}{'_armed' if armed else ''}", **rot}
        write_json(paths.ASSETS / "blockstates" / f"{s.name}.json", {"variants": v})
        write_json(paths.ASSETS / "items" / f"{s.name}.json", {"model": {"type": "minecraft:model", "model": f"groundzero:block/{s.name}"}})


def main() -> None:
    names = []
    for s in WARHEADS:
        names += warhead(s)
    blockstates()
    bomblet()
    G.item_defs(["cluster_bomblet"])
    napalm_fire()
    print("warheads written:", ", ".join(names))


if __name__ == "__main__":
    main()
