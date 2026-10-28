"""Chemical Plant, formed (GeckoLib model + animation), DESIGN.md §8 / §23.3. A 2x2x2 structure drawn from its
controller, which sits at the front-left (north-west) bottom; the model spans x -8..24, z -8..24, y 0..32 in the
north-facing frame (model origin = controller block bottom centre).

Look: a steel skid with a hazard-striped front edge; two tall round process tanks (stepped pixel circles) with a
vertical window slot on the front. The renderer draws each tank's real fluid behind its window at its real level
(water in the left tank, the product in the right one); bubbles rise through it while running. A stirrer motor on the
left tank turns a paddle shaft you can see through the window; a header pipe with red hand-wheel valves runs across
the front and turns back into both tanks; a ladder climbs the back of the left tank. Pipes and cables need something
solid to meet, and the plant connects only through its ports: a red-hatched housing on the Energy Port's two open
faces (front right), a yellow-hatched one on the Item Port's (back right) and a blue-hatched one on the Fluid Port's
(back left), each an L wrapping its cell's outer corner; the two feed silos stand between the back two. The
controller's corner carries a control cabinet with a green CRT.
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import numpy as np  # noqa: E402

from gb import materials as M  # noqa: E402
from gb import palette as P  # noqa: E402
from gb.geo import Animation, GeoModel, save_animations  # noqa: E402
from gb.palette import fixed  # noqa: E402
from gb.tex import Tex  # noqa: E402

NAME = "chemical_plant"
ACID = fixed("acid", "#1c3208", "#2c4c0c", "#3e6a12", "#56901a", "#74b428", "#9cd44a", "#d0f090")
FUEL = fixed("fuel", "#3a1c04", "#5c2c06", "#844208", "#b05e10", "#d4801c", "#eca440", "#fcd890")
FLUID_BLUE = fixed("plant_fluid_blue", "#0c1a3a", "#12285a", "#1a3a7e", "#2452a6", "#3a6ec8", "#6a98e0", "#a8c8f4")
CLEAR = (0, 0, 0, 0)
TANK_Y0, TANK_Y1 = 3.0, 26.0
TANKS = [(0.0, 8.0, ACID), (16.0, 8.0, FUEL)]
# Stepped round tank: (dz0, dz1, half-width in x) around the tank centre.
BANDS = [(-6.5, -5.0, 3.0), (-5.0, -3.5, 5.0), (-3.5, 3.5, 6.5), (3.5, 5.0, 5.0), (5.0, 6.5, 3.0)]
WINDOW = 2.0                  # half-width of the window slot on the front band (tank centre +- this)
# Where the renderer draws each tank's fluid (model px, north-facing frame): just behind the window slot.
FLUID_BOXES = [(cx - 1.9, TANK_Y0 + 0.5, cz - 6.3, cx + 1.9, TANK_Y1 - 0.5, cz - 5.2) for cx, cz, _ in TANKS]

# Two smaller feed silos behind the process tanks, between the Fluid Port and Item Port housings, hold the plant's two
# chemical inputs; each has a window on its back (south) face and a feed pipe into a process tank.
SILOS = [(5.0, 17.6), (10.9, 17.6)]
SILO_Y1 = 18.0
SILO_BANDS = [(-2.8, -2.1, 1.8), (-2.1, -1.2, 2.4), (-1.2, 1.2, 2.8), (1.2, 2.1, 2.4), (2.1, 2.8, 1.8)]
SILO_FLUID_BOXES = [(cx - 0.85, TANK_Y0 + 0.5, cz + 2.3, cx + 0.85, SILO_Y1 - 0.5, cz + 2.7) for cx, cz in SILOS]


def tone(d: str) -> int:
    return {"up": 4, "down": 2}.get(d, 3)


def build() -> tuple[GeoModel, Tex, Tex]:
    g = GeoModel(NAME, tex_size=(256, 256))
    g.bone("frame")
    g.bone("stirrer", pivot=(0, 15, 8), parent="frame")
    for t in range(2):
        for i in range(4):
            g.bone(f"bubble{t}_{i}", pivot=(TANKS[t][0], 6, TANKS[t][1]), parent="frame")
    F = "frame"
    parts: dict[str, list] = {}

    def add(group, box):
        parts.setdefault(group, []).append(box)
        return box

    add("skid", g.box("skid", (-8, 0, -8), (24, 3, 24), bone=F))

    for ti, (cx, cz, liquid) in enumerate(TANKS):
        for bi, (z0, z1, hw) in enumerate(BANDS):
            front = bi == 0
            add(f"tank{ti}_front" if front else f"tank{ti}", g.box(f"tank{ti}_b{bi}", (cx - hw, TANK_Y0, cz + z0), (cx + hw, TANK_Y1, cz + z1),
                                                                    faces=("north", "south", "east", "west"), bone=F))
        # Dome: two stepped caps.
        add(f"dome{ti}", g.box(f"dome{ti}_a", (cx - 5, TANK_Y1, cz - 5), (cx + 5, TANK_Y1 + 1.5, cz + 5), bone=F))
        add(f"dome{ti}", g.box(f"dome{ti}_b", (cx - 3, TANK_Y1 + 1.5, cz - 3), (cx + 3, TANK_Y1 + 2.5, cz + 3), bone=F))
        # Dark tank interior behind the window; the renderer draws the real fluid in front of it.
        add(f"liquid_back{ti}", g.box(f"liquid_back{ti}", (cx - WINDOW, TANK_Y0, cz - 5.15), (cx + WINDOW, TANK_Y1, cz - 5.1),
                                      faces=("north",), bone=F))
        for i in range(4):
            bx = cx + (-1.2, -0.7, 0.7, 1.2)[i]            # clear of the stirrer shaft
            add("bubble", g.box(f"bub{ti}_{i}", (bx - 0.25, 4.0, cz - 6.25), (bx + 0.25, 4.5, cz - 6.2), faces=("north",),
                                shade=False, bone=f"bubble{ti}_{i}"))
        # Hoop bands and a nameplate on each tank.
        for hi, y in enumerate((8.0, 17.0)):
            add("hoop", g.box(f"hoop{ti}_{hi}", (cx - 6.8, y, cz - 3.2), (cx + 6.8, y + 0.8, cz + 3.2), faces=("east", "west"), bone=F))

    # Feed silos for the two chemical inputs, windows facing back.
    for si, (cx, cz) in enumerate(SILOS):
        for bi, (z0, z1, hw) in enumerate(SILO_BANDS):
            back = bi == len(SILO_BANDS) - 1
            add("silo_window" if back else "silo", g.box(f"silo{si}_b{bi}", (cx - hw, TANK_Y0, cz + z0), (cx + hw, SILO_Y1, cz + z1),
                                                      faces=("north", "south", "east", "west"), bone=F))
        add("silo_dome", g.box(f"silo{si}_dome_a", (cx - 2.0, SILO_Y1, cz - 2.0), (cx + 2.0, SILO_Y1 + 1.0, cz + 2.0), bone=F))
        add("silo_dome", g.box(f"silo{si}_dome_b", (cx - 1.2, SILO_Y1 + 1.0, cz - 1.2), (cx + 1.2, SILO_Y1 + 1.8, cz + 1.2), bone=F))
        add(f"liquid_back_silo{si}", g.box(f"liquid_back_silo{si}", (cx - 1.8, TANK_Y0, cz + 2.22), (cx + 1.8, SILO_Y1, cz + 2.25),
                                           faces=("south",), bone=F))
        hy = 10.0 + 2.5 * si                                   # staggered: the two hoops would meet
        add("hoop", g.box(f"silo{si}_hoop", (cx - 3.0, hy, cz - 1.0), (cx + 3.0, hy + 0.8, cz + 1.0), faces=("east", "west"), bone=F))
    # Feed pipes from the silos' fronts into the process tanks' back shoulders.
    add("pipe", g.box("feed0", (3.8, 5.4, 13.0), (5.0, 6.6, 15.0), faces=("east", "west", "up", "down"), bone=F))
    add("pipe", g.box("feed1", (11.2, 5.4, 13.0), (12.4, 6.6, 15.0), faces=("east", "west", "up", "down"), bone=F))

    # Stirrer motor on the left tank, shaft and paddle (spins) visible through the window.
    add("motor", g.box("motor", (-2.5, TANK_Y1 + 2.5, 5.5), (2.5, TANK_Y1 + 5.5, 10.5), bone=F))
    add("motor_fins", g.box("motor_fins", (-2.8, TANK_Y1 + 3.0, 6.5), (2.8, TANK_Y1 + 3.6, 9.5), faces=("east", "west", "up"), bone=F))
    add("shaft", g.box("shaft", (-0.4, 6.0, 1.8), (0.4, 18.0, 2.6), faces=("north", "south", "east", "west"), bone="stirrer"))
    add("paddle", g.box("paddle", (-1.3, 6.0, 2.0), (1.3, 7.5, 2.4), faces=("north", "south", "east", "west", "up", "down"), bone="stirrer"))

    # Manifold between the tanks: a header pipe at the front, risers into each tank, valves with red hand-wheels.
    add("pipe", g.box("header", (-3.1, 9.0, -4.0), (19.1, 10.5, -2.5), faces=("north", "south", "up", "down"), bone=F))
    # Elbows: the header's ends turn back and enter each tank's shoulder (beside the front window band).
    for i, (x0, x1) in enumerate(((-4.6, -3.1), (19.1, 20.6))):
        add("pipe", g.box(f"elbow{i}", (x0, 9.0, -4.0), (x1, 10.5, 3.0), faces=("north", "east", "west", "up", "down"), bone=F))
        fx0, fx1 = (x0 - 0.3, x1 + 0.1) if i == 0 else (x0 - 0.1, x1 + 0.3)
        add("flange", g.box(f"flange{i}", (fx0, 8.7, 2.2), (fx1, 10.8, 2.9), faces=("north", "east", "west", "up", "down"), bone=F))
    for i, x in enumerate((4.0, 8.0, 12.0)):
        add("pipe", g.box(f"riser{i}", (x - 0.6, 3.0, -3.85), (x + 0.6, 9.0, -2.65), faces=("north", "south", "east", "west"), bone=F))
        add("valve", g.box(f"valve{i}", (x - 0.8, 10.5, -3.9), (x + 0.8, 11.5, -2.6), faces=("north", "south", "east", "west", "up"), bone=F))
        add("wheel", g.box(f"wheel{i}", (x - 1.4, 11.5, -3.9), (x + 1.4, 11.9, -2.6), faces=("north", "south", "east", "west", "up", "down"), bone=F))
    add("pipe", g.box("crossover", (5.0, 20.0, 7.2), (11.0, 21.4, 8.8), faces=("north", "south", "up", "down"), bone=F))

    # Control cabinet round the controller's outer corner, CRT on the front (the controller itself has no sockets).
    add("cabinet", g.box("cabinet_front", (-7.9, 3, -7.9), (2.0, 11.5, -5.2), bone=F))
    add("cabinet", g.box("cabinet_side", (-7.9, 3, -5.2), (-5.2, 11.5, 2.0), bone=F))
    # Connection housing filling the Item Port's open faces (east and south of the far corner).
    add("housing_e", g.box("housing_e", (19.2, 3, 13.2), (23.9, 13, 23.9), bone=F))
    add("housing_s", g.box("housing_s", (14.0, 3, 19.2), (19.2, 13, 23.9), bone=F))
    # Energy Port (beside the controller, x 8..24, z -8..8): a shallow arm across the front, clear of the header pipe,
    # and an arm down the east side, clear of the header's elbow and the product tank.
    add("ehousing_n", g.box("ehousing_n", (13.5, 3, -7.9), (23.9, 13, -6.4), bone=F))
    add("ehousing_e", g.box("ehousing_e", (21.2, 3, -6.4), (23.9, 13, 2.9), bone=F))
    # Fluid Port (behind the controller, x -8..8, z 8..24): the same kind of L-shaped housing round its outer corner,
    # its west arm clear of the left tank's shoulder, its south arm clear of the left silo.
    add("fhousing_w", g.box("fhousing_w", (-7.9, 3, 12.0), (-5.2, 13, 23.9), bone=F))
    add("fhousing_s", g.box("fhousing_s", (-5.2, 3, 19.2), (1.8, 13, 23.9), bone=F))

    # Ladder up the back of the left tank, standing just off its back band.
    for i, x in enumerate((-2.0, 1.4)):
        add("ladder", g.box(f"rail{i}", (x, 3, 14.7), (x + 0.6, 29, 15.7), faces=("north", "south", "east", "west", "up"), bone=F))
    for k in range(8):
        add("ladder", g.box(f"rung{k}", (-1.4, 5.5 + k * 3, 15.1), (1.4, 6.0 + k * 3, 15.5), faces=("north", "south", "up", "down"), bone=F))

    g.layout()
    tex, glow = Tex(g.tex_w, g.tex_h), Tex(g.tex_w, g.tex_h)
    paint(g, tex, parts)
    return g, tex, glow


def paint(g: GeoModel, tex: Tex, parts: dict) -> None:
    """The plant's surfaces, in model space (tools/gb/materials.py; the remaster after slice 9b): stainless tanks and
    silos welded in courses, their window slots left open over the real fluid; a dark steel skid with a tread-plate
    deck and a hazard-striped front; galvanised pipework with red hand-wheels; an olive cabinet; the ports' housings."""
    S = M.Surfacer(g, seed=113)
    gun, steel = P.GUNMETAL, P.STEEL
    names = lambda key: [b.name for b in parts.get(key, [])]                      # noqa: E731
    stainless = M.steel(tone=0.72, brushed=0.06, grime=0.035, streaks=0.03)          # bright, kept clean
    dark = M.painted(M.ARMOUR_RAMP, tone=0.5, wear=0.2, rust=0.06)

    S.paint(tex, names("skid"), dark)
    S.paint(tex, names("skid"), M.stripes(width=2, wear=0.35, under=M.STEEL_RAMP[2:7]), only=("north",))

    def tread(f, img):
        a, b = f.plane_uv()
        bar = ((np.floor(a) % 4) == 0) & ((np.floor(b + 2 * (np.floor(a / 4) % 2)) % 4) == 0)
        M.lighten(img, bar, 1.25, 8)
        M.darken(img, np.roll(bar, 1, axis=0) & ~bar, 0.7)
    S.overlay(tex, names("skid"), tread, only=("up",))

    vessels = sum((names(k) for k in parts if k.startswith("tank") or k in ("silo", "silo_window")), [])
    S.paint(tex, vessels, stainless)

    def courses(f, img):                                                       # welded courses round the vessels
        if f.vertical:
            M.seams(f, img, every=6, axis="v", offset=int(TANK_Y0) + 2)
    S.overlay(tex, vessels, courses)
    S.paint(tex, names("dome0") + names("dome1") + names("silo_dome"), stainless)

    def window(t, r, d):                                                       # the slot over the real fluid
        x, y, w, h = r
        if d == "north":
            cx = x + w // 2
            t.rect(cx - 2, y + 1, 4, h - 2, CLEAR)
            t.vline(cx - 3, y, h, P.BRASS[3])
            t.vline(cx + 2, y, h, P.BRASS[3])
            for yy in range(y + 4, y + h - 2, 4):
                t.px(cx - 3, yy, P.BRASS[5])                                    # level marks
    for b in names("tank0_front") + names("tank1_front"):
        g.paint_all(tex, b, window)

    def silo_window(t, r, d):
        x, y, w, h = r
        if d == "south":
            t.rect(x + 1, y + 1, w - 2, h - 2, CLEAR)
            t.vline(x, y, h, P.BRASS[3])
            t.vline(x + w - 1, y, h, P.BRASS[3])
    for b in names("silo_window"):
        g.paint_all(tex, b, silo_window)
    for key in [k for k in parts if k.startswith("liquid_back")]:
        g.paint_all(tex, names(key)[0], lambda t, r, d: t.rect(*r, P.BLACK_PAINT[2]))   # the vessel's dark inside
    for b in names("bubble"):
        g.paint_all(tex, b, lambda t, r, d: t.rect(*r, (235, 250, 225, 255)))
    S.paint(tex, names("hoop"), M.painted(M.ARMOUR_RAMP, tone=0.55, wear=0.25))

    S.paint(tex, names("motor") + names("motor_fins"), M.painted(M.OLIVE_RAMP, tone=0.45, wear=0.25))
    S.overlay(tex, names("motor_fins"), lambda f, img: M.darken(img, (np.floor(f.plane_uv()[0] * 2) % 2) == 1, 0.6))
    S.paint(tex, names("shaft") + names("paddle"), M.steel(tone=0.66))
    g.paint_all(tex, names("paddle")[0], lambda t, r, d: t.px(r[0], r[1], P.WARNING_RED.base))
    S.paint(tex, names("pipe"), M.galvanized(tone=0.6, rust=0.08))
    S.paint(tex, names("flange") + names("valve"), M.painted(M.ARMOUR_RAMP, tone=0.55, wear=0.25))
    S.paint(tex, names("wheel"), M.painted(M.RED_PAINT_RAMP, tone=0.55, wear=0.3))

    def port(t, cx, cy, r=None):
        """A 4x4 connection socket centred on (cx, cy): gunmetal ring, dark bore, four bolts (only those inside the
        face rectangle r, when given, so a narrow face does not paint its neighbours)."""
        t.rect(cx - 2, cy - 2, 4, 4, gun[4])
        t.rect(cx - 1, cy - 1, 2, 2, P.BLACK_PAINT.deep)
        t.hline(cx - 2, cy + 1, 4, gun[5])
        for px, py in ((cx - 3, cy - 3), (cx + 2, cy - 3), (cx - 3, cy + 2), (cx + 2, cy + 2)):
            if r is None or r[0] <= px < r[0] + r[2] and r[1] <= py < r[1] + r[3]:
                t.px(px, py, steel[5])

    S.paint(tex, names("cabinet"), M.painted(M.OLIVE_RAMP, tone=0.55, wear=0.3))

    def screen(t, r, d):
        x, y, w, h = r
        if d == "north" and w > 8:
            sx, sy, sw, sh = x + 3, y + 2, 4, 4
            t.rect(sx, sy, sw, sh, P.BLACK_PAINT.deep)
            for xx in range(sx, sx + sw):
                t.px(xx, sy + sh // 2 + (xx % 3 == 0) - (xx % 3 == 1), P.INDICATOR_GREEN[4])
            t.px(sx, sy, P.INDICATOR_GREEN[5])
    for b in names("cabinet"):
        g.paint_all(tex, b, screen)

    # The ports' housings: dark steel, the open face striped round its socket (yellow items, blue fluid, red power).
    housings = {"housing_e": ("east", 8, M.YELLOW_PAINT_RAMP), "housing_s": ("south", 2, M.YELLOW_PAINT_RAMP),
                "fhousing_w": ("west", 4, M.ramp(*[f"#{c[0]:02x}{c[1]:02x}{c[2]:02x}" for c in FLUID_BLUE.shades])),
                "fhousing_s": ("south", 5, M.ramp(*[f"#{c[0]:02x}{c[1]:02x}{c[2]:02x}" for c in FLUID_BLUE.shades])),
                "ehousing_n": ("north", 8, M.RED_PAINT_RAMP), "ehousing_e": ("east", 3, M.RED_PAINT_RAMP)}
    for key, (face, u, ramp) in housings.items():
        S.paint(tex, names(key), dark)
        band = M.Striped(ramp=ramp, tone=0.6, dither=0.4, wear=0.3, under=M.STEEL_RAMP[2:7], width=2,
                         other=M.painted(M.BLACK_RAMP, tone=0.5, wear=0.3))
        S.overlay(tex, names(key), lambda f, img, band=band: M.paste(img, S.material_on(f, band),
                  np.minimum(np.minimum(f.border["top"], f.border["bottom"]), np.minimum(f.border["left"], f.border["right"])) < 1),
                  only=(face,))
        g.paint_all(tex, names(key)[0], lambda t, r, d, face=face, u=u: port(t, r[0] + u, r[1] + r[3] - 5, r) if d == face else None)
    S.paint(tex, names("ladder"), M.painted(M.YELLOW_PAINT_RAMP, tone=0.55, wear=0.4))


def animations() -> list[Animation]:
    working = Animation("working", 2.0, loop=True)
    working.rotate("stirrer", {0: (0, 0, 0), 2.0: (0, 720, 0)})
    rise = 12.0
    for t in range(2):
        for i in range(4):
            off = (i * 0.5 + t * 0.25) % 2.0
            keys = {}
            for k in range(5):
                tt = round(k * 0.5, 2)
                phase = ((tt + off) % 2.0) / 2.0
                keys[tt] = (0, phase * rise, 0)
            working.move(f"bubble{t}_{i}", keys)
    return [working]


if __name__ == "__main__":
    model, texture, glowmask = build()
    for p in model.save(texture, glowmask):
        print(p)
    print(save_animations(NAME, animations()))
