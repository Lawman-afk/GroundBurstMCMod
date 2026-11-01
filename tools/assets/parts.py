"""Phase 4 parts: alloys, plates, rods, wire, canisters, magnets, circuit boards, assemblies and press dies
(DESIGN.md §26.2). Item sprites plus their model / item-definition JSON.

Most sprites are hand-pixelled templates: each character maps to a colour through a legend, digits 1..6 meaning
tones of the sprite's primary material ramp. Bars and plates use the shared isometric bar helper so they sit
with the Phase 3 ingots.
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))

from gb import palette as P  # noqa: E402
from gb.palette import fixed  # noqa: E402
from gb.sprite import iso_bar, outline  # noqa: E402
from gb.tex import Tex  # noqa: E402
from gb import vanilla as V  # noqa: E402
import resources as R  # noqa: E402

STEEL = fixed("steel_metal", "#14181e", "#252b33", "#39414b", "#525c68", "#717c89", "#98a3ae", "#d2dae2")
COBALT = fixed("cobalt_metal", "#0e1628", "#1c2a48", "#2c406a", "#40598c", "#5c78ac", "#8aa2cc", "#c8d6f0")
CARBIDE = fixed("carbide", "#0c0c0e", "#18181c", "#25262b", "#34363c", "#4a4c54", "#6e7280", "#b4bccc")
COPPER = fixed("copper_metal", "#3a1608", "#62280e", "#8c3e18", "#b45a26", "#d27a3c", "#eaa062", "#fcd2a4")
GOLD = fixed("gold_metal", "#4a2c06", "#7a4a0a", "#b07412", "#d89a1e", "#f0bc34", "#fad866", "#fff4b8")
PCB = fixed("perfboard", "#2a1606", "#46260c", "#643a14", "#82501e", "#a0682c", "#bc8640", "#dcb070")
MAGNET_RED = fixed("magnet_red", "#2e0606", "#560c0c", "#801414", "#a8201c", "#cc3428", "#e45a48", "#f8988a")
ARGON = fixed("argon", "#1e1440", "#33246a", "#4c3a94", "#6852b8", "#8a74d6", "#ae9cea", "#dcd2fa")
HOT = P.NUCLEAR_AMBER

ITEMS: list[str] = []


def save(name: str, t: Tex) -> None:
    R.save_item(name, t)
    ITEMS.append(name)


def template(rows: list[str], legend: dict[str, tuple], primary=None, edge=None) -> tuple[Tex, set]:
    """Paints a 16x16 character template. Digits 1..6 are tones of `primary`; other characters come from the
    legend (an RGBA colour). '.' is empty. Returns the texture and painted pixels; `edge` adds an outline."""
    t = Tex(16, 16)
    pts = set()
    for y, row in enumerate(rows):
        for x, c in enumerate(row):
            if c == ".":
                continue
            color = primary[int(c)] if c.isdigit() else legend[c]
            t.px(x, y, color)
            pts.add((x, y))
    if edge is not None:
        outline(t, pts, edge)
    return t, pts


# --- bars & plates ------------------------------------------------------------------------------------------------------

def bar_ingot(name: str, ramp, mark=None) -> None:
    save(name, V.ingot(ramp, mark))


def plate(name: str, ramp, dense: bool = False) -> None:
    t = Tex(16, 16)
    faces = iso_bar(t, 7.0, 4.5, 9, 7, 2 if dense else 1, ramp)
    pts = set().union(*faces.values())
    top = faces["top"]
    for (x, y) in top:
        if (x, y - 1) not in top or (x - 1, y) not in top:
            t.px(x, y, ramp.highlight)            # bevelled lit edges
    # A brushed streak and (for dense plate) a riveted doubler.
    ys = sorted({y for _, y in top})
    mid = ys[len(ys) // 2]
    row = sorted(x for x, y in top if y == mid)
    for x in row[2:-2:2]:
        t.px(x, mid, ramp[5])
    if dense:
        for (x, y) in top:
            if (x + 2 * y) % 7 == 0 and (x, y - 1) in top and (x + 1, y) in top:
                t.px(x, y, ramp.shadow)          # rivet heads with their lit side
                t.px(x - 1, y, ramp.shine) if (x - 1, y) in top else None
    outline(t, pts, ramp.outline)
    save(name, t)


# --- rods, wire, filament -----------------------------------------------------------------------------------------------

ROD = [
    "................",
    "............o...",
    "...........o65o.",
    "..........o654o.",
    ".........o654o..",
    "........o654o...",
    ".......o654o....",
    "......o654o.....",
    ".....o654o......",
    "....o654o.......",
    "...o654o........",
    "..o654o.........",
    ".o543o..........",
    ".o32o...........",
    "..oo............",
    "................",
]


def rod(name: str, ramp) -> None:
    t, _ = template(ROD, {"o": ramp.outline}, ramp)
    save(name, t)




def spool(name: str, wire, flange=STEEL) -> None:
    """A reel of wire: steel flanges, the wire wound in alternating light/dark turns between them."""
    rows = [
        "................",
        "................",
        "...ooo..........",
        "..o444oooooooo..",
        ".o4wWwoWwWwWwoo.",
        ".o3wWwoWwWwWwo4o",
        ".o3wWwoWwWwWwo4o",
        ".o3wWwoWwWwWwo3o",
        ".o2wWwoWwWwWwo3o",
        "..o222oooooooo3o",
        "...ooo.......o2o",
        "..............o.",
        "................",
        "................",
        "................",
        "................",
    ]
    legend = {"o": flange.outline, "w": wire[3], "W": wire[5]}
    t, _ = template(rows, legend, flange)
    for x in range(7, 13):
        t.px(x, 4, wire[6] if x % 2 else wire[4])     # light catching the top of each turn
    # Lit wire turns on the near face; a loose tail of wire trailing off the reel.
    for x, y in ((14, 11), (14, 12), (13, 13), (12, 13)):
        t.px(x, y, wire[4])
    save(name, t)


def filament(name: str) -> None:
    """Tungsten filament: a tight coiled zig-zag, glowing faintly at the tip."""
    rows = [
        "................",
        "................",
        "............h...",
        "...........h6h..",
        "..........5.5...",
        ".........4.4....",
        "........5.5.....",
        ".......4.4......",
        "......5.5.......",
        ".....4.4........",
        "....5.5.........",
        "...4.4..........",
        "..3.3...........",
        ".33.............",
        "................",
        "................",
    ]
    t, pts = template(rows, {"h": HOT[5]}, STEEL)
    # Each coil turn gets a dark back-stroke so the zig-zag reads as a helix.
    for (x, y) in list(pts):
        if (x + 1, y + 1) not in pts and 0 < x < 15 and y < 14:
            t.px(x + 1, y + 1, STEEL[2])
    save(name, t)


# --- canisters, magnets, boards ---------------------------------------------------------------------------------------

CANISTER_ROWS = [
    "................",
    "......oooo......",
    ".....o5665o.....",
    ".....o3443o.....",
    "....oo4444oo....",
    "...o55666554o...",
    "...o45566544o...",
    "...o45566543o...",
    "...oYYYYYYYYo...",
    "...oKKKKKKKKo...",
    "...oYYYYYYYYo...",
    "...o45566543o...",
    "...o34455432o...",
    "...o23344321o...",
    "....oooooooo....",
    "................",
]
# Gas canister: a taller pressure bottle with a valve hand-wheel and a gauge.
GAS_ROWS = [
    "......vvvv......",
    ".......vv.......",
    "......oooo......",
    ".....o5665og....",
    "....o566665oG...",
    "...o56666655o...",
    "...o45666654o...",
    "...oYYYYYYYYo...",
    "...oKKKKKKKKo...",
    "...oYYYYYYYYo...",
    "...o45566543o...",
    "...o45566543o...",
    "...o34455432o...",
    "...o23344321o...",
    "....oooooooo....",
    "................",
]


def canister_sprite(name: str, rows: list[str], band, mark, frost: bool = False) -> None:
    """Canister family: steel body, two-tone contents band (Y light / K dark) colour-coding what is inside."""
    legend = {"o": STEEL.outline, "Y": band[4], "K": mark, "v": P.WARNING_RED[4], "g": P.WHITE_CERAMIC[5], "G": P.BRASS[4]}
    t, pts = template(rows, legend, STEEL)
    if frost:
        for (x, y) in ((5, 6), (10, 11), (6, 12), (11, 5), (4, 11)):
            if (x, y) in pts:
                t.px(x, y, P.WHITE_CERAMIC[6])                  # frost specks on a cryogenic canister
    save(name, t)


def canisters() -> None:
    canister_sprite("empty_canister", CANISTER_ROWS, P.HAZARD_YELLOW, P.HAZARD_BLACK[3])
    canister_sprite("empty_gas_canister", GAS_ROWS, P.HAZARD_YELLOW, P.HAZARD_BLACK[3])
    canister_sprite("deuterium_canister", GAS_ROWS, P.CHERENKOV, P.WHITE_CERAMIC[6])
    canister_sprite("liquid_oxygen_canister", CANISTER_ROWS, P.CRYO, P.CHERENKOV[2], frost=True)
    canister_sprite("liquid_nitrogen_canister", CANISTER_ROWS, P.WHITE_CERAMIC, P.CRYO[2], frost=True)


def membrane(name: str, tint) -> None:
    """Separation membrane: a zeolite sieve cartridge in a steel frame; the sieve colour tells O2 from N2."""
    rows = [
        "................",
        "................",
        "..oooooooooooo..",
        "..o5555555555o..",
        "..o5abababab4o..",
        "..o5babababa3o..",
        "..o5abababab3o..",
        "..o4babababa3o..",
        "..o4abababab3o..",
        "..o4babababa2o..",
        "..o3abababab2o..",
        "..o3322222222o..",
        "..oooooooooooo..",
        "................",
        "................",
        "................",
    ]
    t, _ = template(rows, {"o": STEEL.outline, "a": tint[4], "b": tint[3]}, STEEL)
    for x, y in ((4, 4), (5, 5)):
        t.px(x, y, tint[6])                                    # sheen on the sieve
    save(name, t)


def magnet() -> None:
    rows = [
        "................",
        "....oooooo......",
        "...o566554o.....",
        "..o56544443o....",
        ".o5541oo1443o...",
        ".o541o..o143o...",
        ".o441o..o132o...",
        ".o441o..o132o...",
        ".o441o..o132o...",
        ".oSSso..oSssoe..",
        ".oSssso.oSsssoe.",
        ".ooooo..ooooo.e.",
        "............e.e.",
        "...........e....",
        "................",
        "................",
    ]
    t, _ = template(rows, {"o": MAGNET_RED.outline, "S": STEEL[6], "s": STEEL[4], "e": P.EMP[4]}, MAGNET_RED)
    save("neodymium_magnet", t)


def circuit_board() -> None:
    """Handmade circuit board: brown perfboard with copper traces, a salvaged chip and a gold contact."""
    rows = [
        "................",
        "................",
        "...oooooooooo...",
        "..o4545454545o..",
        ".o4cccc5k5c45o..",
        ".o5454c4kkc54o..",
        ".o45KKKKc4c45o..",
        ".o54KKKK5cc54o..",
        ".o45KKKK4c5c5o..",
        ".o545c454c4c4o..",
        ".o4cc54ccc5c5o..",
        ".o3434343434g3o.",
        "..o2323232323go.",
        "...ooooooooooo..",
        "................",
        "................",
    ]
    legend = {"o": PCB.outline, "c": COPPER[5], "K": P.BLACK_PAINT[2], "k": P.BLACK_PAINT[4], "g": GOLD[5]}
    t, _ = template(rows, legend, PCB)
    for x in (5, 6, 7):
        t.px(x, 5, STEEL[5])                     # chip legs
    save("handmade_circuit_board", t)


# --- assemblies ------------------------------------------------------------------------------------------------------------

def machine_frame() -> None:
    """A welded cube of steel angle seen from above-left: lit near edges, darker far edges, yellow gussets."""
    t = Tex(16, 16)
    # Top diamond (A far, C near) on true 2:1 slopes, one pixel in from every edge so the outline fits the sprite.
    A, B, C, D = (8, 1), (14, 4), (8, 7), (2, 4)
    drop = 7
    Ab, Bb, Cb, Db = ((x, y + drop) for x, y in (A, B, C, D))
    far = STEEL[2]
    for p0, p1 in ((A, Ab), (Db, Ab), (Ab, Bb)):           # hidden edges first, dark
        t.line(*p0, *p1, far)
    for p0, p1, c in ((D, A, STEEL[5]), (A, B, STEEL[5]), (D, C, STEEL[6]), (C, B, STEEL[4]),
                      (D, Db, STEEL[4]), (C, Cb, STEEL[5]), (B, Bb, STEEL[3]),
                      (Db, Cb, STEEL[3]), (Cb, Bb, STEEL[2])):
        t.line(*p0, *p1, c)
    pts = {(x, y) for y in range(16) for x in range(16) if t.get(x, y)[3]}
    for y in range(C[1], Cb[1] + 1):
        t.px(C[0] + 1, y, STEEL[3])
        pts.add((C[0] + 1, y))
    # Yellow gusset plates on the six visible corner joints (the far corner is hidden behind the frame).
    for x, y in (D, B, C, Db, Bb, Cb):
        t.px(x, y, P.HAZARD_YELLOW[4])
        pts.add((x, y))
    outline(t, pts, STEEL.outline)
    save("machine_frame", t)


def motor() -> None:
    rows = [
        "................",
        "................",
        "...oooooooooo...",
        "..o5666666665o..",
        ".oo4cCcCcCcC4oo.",
        "so4cCcCcCcCc4o..",
        "so4cCcCcCcCc3oSS",
        "so3cCcCcCcCc3oS4",
        "so3cCcCcCcCc2oS3",
        "so2cCcCcCcCc2o..",
        ".oo2cCcCcCcC2oo.",
        "..o1222222221o..",
        "...oooooooooo...",
        "....o4o..o4o....",
        "....ooo..ooo....",
        "................",
    ]
    legend = {"o": STEEL.outline, "c": COPPER[3], "C": COPPER[5], "s": STEEL[3], "S": STEEL[5]}
    t, _ = template([r[:16] for r in rows], legend, STEEL)
    save("electric_motor", t)


def pressure_vessel() -> None:
    rows = [
        "................",
        ".....oooooo.....",
        "....o566665o....",
        "...o56666654o...",
        "...o5666665Go...",
        "...o456665GgGo..",
        "...o455554oGo...",
        "...o455554o.....",
        "...oYYYYYYo.....",
        "...oKYKYKYo.....",
        "...o455543o.....",
        "...o445543o.....",
        "...o344432o.....",
        "....o3332o......",
        ".....oooo.......",
        "................",
    ]
    legend = {"o": STEEL.outline, "Y": P.HAZARD_YELLOW[4], "K": P.HAZARD_BLACK[3], "G": P.BRASS[5], "g": P.WHITE_CERAMIC[5]}
    t, _ = template(rows, legend, STEEL)
    save("pressure_vessel", t)


def heating_element() -> None:
    rows = [
        "................",
        "................",
        "..oooooooooooo..",
        ".o566666666665o.",
        ".o4oooooooooo3o.",
        ".o4o..........o.",
        ".o4oh.h.h.h.hoo.",
        ".o3o.H.H.H.H.o..",
        ".o3oh.h.h.h.hoo.",
        ".o3o..........o.",
        ".o3oooooooooo2o.",
        ".o233333333322o.",
        "..oooooooooooo..",
        "..ccc......ccc..",
        "................",
        "................",
    ]
    legend = {"o": STEEL.outline, "h": HOT[4], "H": HOT[6], "c": COPPER[5]}
    t, _ = template(rows, legend, STEEL)
    for x in range(4, 14):
        for y in range(5, 10):
            if t.get(x, y)[3] == 0:
                t.px(x, y, P.BLACK_PAINT[1])       # dark cavity behind the coil
    save("heating_element", t)


def servo() -> None:
    rows = [
        "................",
        ".......oo.......",
        "......o55o......",
        "....oo5WW5oo....",
        "...o5W6666W5o...",
        "...oo566665oo...",
        "..oooooooooooo..",
        ".o555555555554o.",
        ".o4KKKKKKKKKK3o.",
        "oo4KkkkkkkkkK3oo",
        "o54KkYYYYYYkK325o",
        "oo4KkkkkkkkkK3oo",
        ".o3KKKKKKKKKK2o.",
        ".o222222222221o.",
        "..oooooooooooo..",
        "................",
    ]
    legend = {"o": STEEL.outline, "K": P.BLACK_PAINT[3], "k": P.BLACK_PAINT[2], "Y": P.HAZARD_YELLOW[4], "W": P.WHITE_CERAMIC[5]}
    t, _ = template([r[:16] for r in rows], legend, STEEL)
    save("servo", t)


# --- press dies -------------------------------------------------------------------------------------------------------------

DIE_STEEL = fixed("die_steel", "#101318", "#1f242b", "#2f363f", "#434c57", "#5e6874", "#86919c", "#c4ccd4")


# Die block geometry (true 2:1 isometric): a square block A x A, H tall, its top face a diamond; the block sits low in
# the sprite (as an item rests in its slot) with a pixel to spare round it for the outline.
# Screen x = OX + u - v, y = OY + (u + v) / 2 - z.
DIE_A, DIE_H = 6.5, 3.6
DIE_OX, DIE_OY = 8.0, 6.8


def _die_region(px: float, py: float) -> tuple[str, float, float]:
    """Which face of the die block a pixel centre lies on ('top', 'left', 'right' or ''), with its face coordinates."""
    a, h = DIE_A, DIE_H
    # Top face at z = h.
    d, s_ = px - DIE_OX, 2 * (py - DIE_OY + h)
    u, v = (s_ + d) / 2, (s_ - d) / 2
    if 0 <= u <= a and 0 <= v <= a:
        return "top", u, v
    # Left front face: v = a.
    u = px - DIE_OX + a
    z = DIE_OY + (u + a) / 2 - py
    if 0 <= u <= a and 0 <= z <= h:
        return "left", u, z
    # Right front face: u = a.
    v = DIE_OX + a - px
    z = DIE_OY + (a + v) / 2 - py
    if 0 <= v <= a and 0 <= z <= h:
        return "right", v, z
    return "", 0, 0


def die(name: str, band, inside, wire=None) -> None:
    """A hardened tool-steel die block: a square slab seen from above at 2:1 with its cavity cut into the centre of the
    top face and a colour band round its flanks so each die is recognisable at a glance. {@code inside(u, v)} says
    whether a point of the top face (0..DIE_A each way) is part of the cavity."""
    t = Tex(16, 16)
    pts = set()
    for y in range(16):
        for x in range(16):
            face, f1, f2 = _die_region(x + 0.5, y + 0.5)
            if not face:
                continue
            pts.add((x, y))
            if face == "top":
                u, v = f1, f2
                colour = DIE_STEEL[5] if min(u, v) < 0.8 else DIE_STEEL[4]       # lit far rim
                if inside(u, v):
                    # In the cavity you see its far walls (lit) and its dark floor.
                    wall = not inside(u - 0.9, v) or not inside(u, v - 0.9)
                    colour = DIE_STEEL[3] if wall else DIE_STEEL[1]
                    if wire is not None and wire(u, v):
                        colour = band[5]                                          # a wire strung across the cavity
                elif inside(u - 0.9, v) or inside(u, v - 0.9):
                    colour = DIE_STEEL[6]                                     # bright lip on the cavity's near edge
            else:
                z = f2
                in_band = 1.0 <= z <= 2.1
                if face == "left":
                    colour = band[4] if in_band else DIE_STEEL[3]
                else:
                    colour = band[3] if in_band else DIE_STEEL[2]
            t.px(x, y, colour)
    outline(t, pts, DIE_STEEL.outline)
    save(name, t)


def dies() -> None:
    c = DIE_A / 2
    # Plate die: a long, narrow slot across the block.
    die("plate_die", P.HAZARD_YELLOW, lambda u, v: 0.9 <= u <= DIE_A - 0.9 and abs(v - c) <= 1.25)
    # Rod die: one round bore in the middle.
    die("rod_die", P.WARNING_RED, lambda u, v: (u - c) ** 2 + (v - c) ** 2 <= 1.7 ** 2)
    # Wire die: three small drawing holes in a row.
    die("wire_die", COPPER, lambda u, v: abs(v - c) <= 1.0 and any(abs(u - hu) <= 0.85 for hu in (c - 2.1, c, c + 2.1)))
    # Canister die: a wide square cavity.
    die("canister_die", P.CHERENKOV, lambda u, v: abs(u - c) <= 1.85 and abs(v - c) <= 1.85)
    # Diamond wire saw: a long slot with a web of diamond-coated wires strung across it.
    die("diamond_wire_saw", P.CRYO, lambda u, v: 0.8 <= u <= DIE_A - 0.8 and abs(v - c) <= 1.9,
        wire=lambda u, v: (u - 0.8) % 1.4 < 0.55)


def build() -> None:
    bar_ingot("steel_ingot", STEEL)
    bar_ingot("cobalt_ingot", COBALT)
    bar_ingot("tungsten_carbide_ingot", CARBIDE)
    plate("steel_plate", STEEL)
    plate("copper_plate", COPPER)
    plate("lead_plate", R.LEAD_METAL)
    plate("tungsten_plate", R.TUNGSTEN_METAL)
    plate("dense_steel_plate", STEEL, dense=True)
    rod("steel_rod", STEEL)
    rod("tungsten_rod", R.TUNGSTEN_METAL)
    spool("copper_wire", COPPER)
    spool("gold_wire", GOLD)
    filament("tungsten_filament")
    canisters()
    membrane("oxygen_membrane", P.CRYO)
    membrane("argon_sieve", ARGON)
    membrane("nitrogen_membrane", P.TOXIN)
    magnet()
    circuit_board()
    machine_frame()
    motor()
    pressure_vessel()
    heating_element()
    servo()
    dies()


if __name__ == "__main__":
    build()
    print(R.item_sheet(ITEMS, "item", "parts_review.png"))
