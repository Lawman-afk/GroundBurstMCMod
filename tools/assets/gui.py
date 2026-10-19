"""Machine GUI textures + the matching Java layout constants (src/.../content/machine/MachineLayouts.java).

One source of truth: LAYOUTS below drives both the drawn texture and the slot/widget positions the menu and
screen use. Military/industrial style: riveted gunmetal panels, recessed slots, amber phosphor gauges on dark
glass, hazard trim. Texture: 256x256, window at (0,0), fill sprites to the right of it (x >= 176).
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from gz import palette as P  # noqa: E402
from gz import paths  # noqa: E402
from gz.palette import fixed, mix  # noqa: E402
from gz.tex import Tex  # noqa: E402

W, H = 176, 166
PANEL = P.GUNMETAL
GLASS = fixed("crt_glass", "#05080a", "#0a1013", "#10181c", "#162126", "#1e2c33", "#2a3c44", "#3c525c")
AMBER = fixed("phosphor_amber", "#3a1c02", "#6a3404", "#a05408", "#d8800e", "#ffa826", "#ffc864", "#ffe6b0")

# Sprite positions (shared by every machine texture).
ENERGY_FILL = (180, 0)        # 12 x 52
PROGRESS_FILL = (196, 0)      # 24 x 17
FLAME_FILL = (196, 20)        # 14 x 14
REDSTONE_ICONS = (180, 56)    # 3 icons, 16 x 16 each

LAYOUTS = {
    "ore_crusher": {
        "title": (28, 6),
        "slots": [("in", 56, 35), ("out_big", 116, 35), ("out", 146, 35)],
        "energy": (8, 20, 14, 46),
        "progress": (79, 34, 24, 17),
        "flame": None,
        "status": (160, 5, 8, 8),
        "redstone": (151, 58, 18, 18),
        "readout": None,
        "inventory": (8, 84),
    },
    "arc_furnace": {
        "title": (28, 6),
        "slots": [("in", 56, 25), ("in", 56, 47), ("out_big", 116, 35), ("out", 146, 35)],
        "energy": (8, 20, 14, 46),
        "progress": (79, 34, 24, 17),
        "flame": None,
        "status": (160, 5, 8, 8),
        "redstone": (151, 58, 18, 18),
        "readout": None,
        "inventory": (8, 84),
    },
    "industrial_press": {
        "title": (28, 6),
        "slots": [("in", 56, 25), ("die", 56, 47), ("out_big", 116, 35)],
        "energy": (8, 20, 14, 46),
        "progress": (79, 34, 24, 17),
        "flame": None,
        "status": (160, 5, 8, 8),
        "redstone": (151, 58, 18, 18),
        "readout": None,
        "inventory": (8, 84),
    },
    "electrolytic_separator": {
        "title": (28, 6),
        "slots": [("canister", 52, 25), ("bucket", 52, 53), ("out_big", 116, 35)],
        "energy": (8, 20, 14, 46),
        "tank": (30, 20, 14, 46),
        "progress": (79, 34, 24, 17),
        "flame": None,
        "status": (160, 5, 8, 8),
        "redstone": (151, 58, 18, 18),
        "readout": None,
        "inventory": (8, 84),
    },
    "cryogenic_condenser": {
        "title": (28, 6),
        "slots": [("canister", 56, 25), ("membrane", 56, 47), ("out_big", 116, 35)],
        "energy": (8, 20, 14, 46),
        "progress": (79, 34, 24, 17),
        "flame": None,
        "status": (160, 5, 8, 8),
        "redstone": (151, 58, 18, 18),
        "readout": None,
        "inventory": (8, 84),
    },
    "combustion_generator": {
        "title": (28, 6),
        "slots": [("fuel", 56, 53)],
        "energy": (8, 20, 14, 46),
        "progress": None,
        "flame": (57, 34, 14, 14),
        "status": (160, 5, 8, 8),
        "redstone": (151, 58, 18, 18),
        "readout": (88, 20, 78, 34),
        "inventory": (8, 84),
    },
}


# --- widgets ---------------------------------------------------------------------------------------------------------

def window(t: Tex) -> None:
    """Riveted gunmetal panel with a header strip and an inventory section separated by a seam."""
    t.rect(0, 0, W, H, PANEL.base)
    t.outline(0, 0, W, H, PANEL.outline)
    t.hline(1, 1, W - 2, PANEL.highlight)
    t.vline(1, 1, H - 2, PANEL.light)
    t.hline(1, H - 2, W - 2, PANEL.deep)
    t.vline(W - 2, 1, H - 2, PANEL.deep)
    # Header strip
    t.rect(3, 3, W - 6, 13, PANEL.shadow)
    t.hline(3, 16, W - 6, PANEL.light)
    t.stripes(W - 30, 4, 26, 11, P.HAZARD_YELLOW.shadow, P.HAZARD_BLACK.base, width=3)
    t.outline(W - 31, 3, 28, 13, PANEL.outline)
    # Inventory section
    t.seam_h(3, 76, W - 6, PANEL)
    t.rect(3, 78, W - 6, H - 81, PANEL[3])
    t.hline(3, 78, W - 6, PANEL.shadow)
    # Corner rivets and a few along the edges
    for x, y in ((4, 18), (W - 6, 18), (4, H - 6), (W - 6, H - 6), (4, 72), (W - 6, 72)):
        t.rivet(x, y, PANEL, size=2)
    # Subtle panel wear
    t.scratches(30, 20, 110, 50, (*PANEL.light[:3], 90), seed=5, count=4, length=4)


def slot(t: Tex, x: int, y: int) -> None:
    """Recessed 18x18 slot (the item sits at x+1, y+1)."""
    t.rect(x, y, 18, 18, PANEL.deep)
    t.hline(x, y, 18, PANEL.outline)
    t.vline(x, y, 18, PANEL.outline)
    t.hline(x + 1, y + 17, 17, PANEL.light)
    t.vline(x + 17, y + 1, 17, PANEL.light)
    t.rect(x + 1, y + 1, 16, 16, PANEL[1])


def big_slot(t: Tex, x: int, y: int) -> None:
    """26x26 framed output slot around an 18x18 slot."""
    t.panel(x - 4, y - 4, 26, 26, PANEL, base=2, style="inset")
    t.outline(x - 4, y - 4, 26, 26, PANEL.outline)
    slot(t, x, y)


DIE_GHOST = ["................", "................", "................", "................", "......####......",
             "....##....##....", "...#..####..#...", "...#.#....#.#...", "...##.####.##...", "...#.##..##.#...",
             "...#...##...#...", "....##....##....", "......####......", "................", "................",
             "................"]


def die_slot(t: Tex, x: int, y: int) -> None:
    """Catalyst slot with a ghosted die outline so players know what goes there."""
    slot(t, x, y)
    for yy, row in enumerate(DIE_GHOST):
        for xx, c in enumerate(row):
            if c == "#":
                t.px(x + 1 + xx, y + 1 + yy, PANEL[2])


BUCKET_GHOST = ["................", "................", "................", "...##########...", "...#........#...",
                "...##########...", "....#......#....", "....#......#....", "....#......#....", ".....#....#.....",
                ".....#....#.....", ".....######.....", "................", "................", "................",
                "................"]
CANISTER_GHOST = ["................", "......####......", ".....#....#.....", ".....######.....", "....#......#....",
                  "....#......#....", "....#......#....", "....########....", "....#......#....", "....########....",
                  "....#......#....", "....#......#....", "....#......#....", ".....######.....", "................",
                  "................"]
MEMBRANE_GHOST = ["................", "................", "...##########...", "...#.#.#.#.#.#..", "...##########...",
                  "...#.#.#.#.#.#..", "...##########...", "...#.#.#.#.#.#..", "...##########...", "...#.#.#.#.#.#..",
                  "...##########...", "................", "................", "................", "................",
                  "................"]


def ghost_slot(rows: list[str]):
    def draw(t: Tex, x: int, y: int) -> None:
        slot(t, x, y)
        for yy, row in enumerate(rows):
            for xx, c in enumerate(row):
                if c == "#":
                    t.px(x + 1 + xx, y + 1 + yy, PANEL[2])
    return draw


def tank_frame(t: Tex, x: int, y: int, w: int, h: int) -> None:
    """Fluid gauge: dark glass with bright graduation ticks on the right so the level reads against the fluid."""
    t.rect(x - 1, y - 1, w + 2, h + 2, PANEL.outline)
    t.rect(x, y, w, h, GLASS.base)
    for yy in range(y + h - 1, y, -5):
        t.hline(x + w - 3, yy, 3, PANEL.highlight)
    t.hline(x - 1, y + h + 1, w + 2, PANEL.light)


def gauge_frame(t: Tex, x: int, y: int, w: int, h: int) -> None:
    t.rect(x - 1, y - 1, w + 2, h + 2, PANEL.outline)
    t.rect(x, y, w, h, GLASS.base)
    for yy in range(y + h - 1, y, -6):          # graduation ticks
        t.px(x, yy, GLASS.highlight)
        t.px(x + w - 1, yy, GLASS.highlight)
    t.hline(x, y, w, GLASS.shine)               # glass glint
    t.hline(x - 1, y + h + 1, w + 2, PANEL.light)


def energy_fill(t: Tex) -> None:
    """Segmented amber phosphor bars (2 px bars, 1 px gaps), hotter toward the top."""
    x0, y0 = ENERGY_FILL
    for yy in range(52):
        if yy % 3 == 2:
            continue
        k = 1 - yy / 51
        c = AMBER[3 + round(k * 2)]
        t.hline(x0, y0 + yy, 12, c)
        t.px(x0, y0 + yy, AMBER.shadow)
        t.px(x0 + 11, y0 + yy, AMBER.shadow)


def arrow_mask(w: int, h: int) -> list[tuple[int, int]]:
    """Chunky right-pointing arrow: shaft then head."""
    pts = []
    mid = h // 2
    for x in range(w):
        if x < w - 9:
            for y in range(mid - 3, mid + 4):
                pts.append((x, y))
        else:
            half = (w - x) - 1
            for y in range(mid - half, mid + half + 1):
                pts.append((x, y))
    return pts


def progress_track(t: Tex, x: int, y: int, w: int, h: int) -> None:
    for (ax, ay) in arrow_mask(w, h):
        t.px(x + ax, y + ay, PANEL.deep)
    for (ax, ay) in arrow_mask(w, h):
        if (ax, ay - 1) not in arrow_mask(w, h):
            t.px(x + ax, y + ay, PANEL.outline)


def progress_fill(t: Tex) -> None:
    x0, y0 = PROGRESS_FILL
    for (ax, ay) in arrow_mask(24, 17):
        stripe = ((ax + ay) // 3) % 2 == 0
        t.px(x0 + ax, y0 + ay, P.HAZARD_YELLOW.light if stripe else P.HAZARD_YELLOW.base)
    for (ax, ay) in arrow_mask(24, 17):
        if (ax, ay - 1) not in arrow_mask(24, 17):
            t.px(x0 + ax, y0 + ay, P.HAZARD_YELLOW.shine)


FLAME = ["......#.......", ".....##.......", ".....###......", "....####......", "....#####.....", "...######..#..",
         "...#######.##.", "..##########..", "..###########.", ".############.", ".#############", ".#############",
         "..###########.", "...#########.."]


def flame_track(t: Tex, x: int, y: int) -> None:
    for yy, row in enumerate(FLAME):
        for xx, c in enumerate(row):
            if c == "#":
                t.px(x + xx, y + yy, PANEL.deep)


def flame_fill(t: Tex) -> None:
    x0, y0 = FLAME_FILL
    for yy, row in enumerate(FLAME):
        for xx, c in enumerate(row):
            if c == "#":
                core = 4 <= xx <= 9 and yy >= 7
                col = P.NUCLEAR_AMBER.shine if core and yy >= 9 else P.NUCLEAR_AMBER.light if core else P.WARNING_RED.light if yy < 6 else P.NUCLEAR_AMBER.base
                t.px(x0 + xx, y0 + yy, col)


def lamp_bezel(t: Tex, x: int, y: int, w: int, h: int) -> None:
    t.rect(x - 1, y - 1, w + 2, h + 2, PANEL.outline)
    t.rect(x, y, w, h, GLASS.base)


def button(t: Tex, x: int, y: int, w: int, h: int) -> None:
    t.panel(x, y, w, h, PANEL, base=4, style="raised")
    t.outline(x, y, w, h, PANEL.outline)


def redstone_icons(t: Tex) -> None:
    """Ignore (grey dust), active-high (lit torch), active-low (dark torch)."""
    x0, y0 = REDSTONE_ICONS
    dust = [(5, 9), (6, 8), (7, 9), (8, 8), (9, 9), (10, 8), (6, 10), (8, 10), (10, 10), (7, 7), (9, 7)]
    for x, y in dust:
        t.px(x0 + x, y0 + y, P.CONCRETE.light)
    t.line(x0 + 3, y0 + 12, x0 + 12, y0 + 3, P.WARNING_RED.base)    # "ignored" slash
    for i, lit in ((1, True), (2, False)):
        bx = x0 + i * 16
        t.rect(bx + 7, y0 + 6, 2, 8, P.DESERT_TAN.shadow)          # stick
        head = P.WARNING_RED.light if lit else P.PRIMER_RED.deep
        t.rect(bx + 6, y0 + 3, 4, 3, head)
        if lit:
            t.px(bx + 7, y0 + 2, P.WARNING_RED.shine)
            t.px(bx + 5, y0 + 3, (*P.WARNING_RED.highlight[:3], 160), blend=True)
            t.px(bx + 10, y0 + 3, (*P.WARNING_RED.highlight[:3], 160), blend=True)


def readout_panel(t: Tex, x: int, y: int, w: int, h: int) -> None:
    """CRT screen: bezel, dark glass, faint scanlines; text is drawn live by the screen."""
    t.panel(x - 3, y - 3, w + 6, h + 6, PANEL, base=2, style="inset")
    t.rect(x - 1, y - 1, w + 2, h + 2, PANEL.outline)
    t.rect(x, y, w, h, GLASS.base)
    for yy in range(y, y + h, 2):
        t.hline(x, yy, w, GLASS[2])
    t.hline(x, y, w, GLASS.highlight)


def build(name: str, spec: dict) -> Path:
    t = Tex(256, 256)
    window(t)
    for kind, x, y in spec["slots"]:
        {"out_big": big_slot, "die": die_slot, "bucket": ghost_slot(BUCKET_GHOST), "canister": ghost_slot(CANISTER_GHOST),
         "membrane": ghost_slot(MEMBRANE_GHOST)}.get(kind, slot)(t, x - 1, y - 1)
    ex, ey, ew, eh = spec["energy"]
    gauge_frame(t, ex, ey, ew, eh)
    t.text(ex + 3, ey + eh + 3, "FE", PANEL.highlight)
    if spec.get("tank"):
        tx, ty, tw, th = spec["tank"]
        tank_frame(t, tx, ty, tw, th)
        t.text(tx + 1, ty + th + 3, "H2O", PANEL.highlight)
    if spec["progress"]:
        progress_track(t, *spec["progress"])
    if spec["flame"]:
        fx, fy, _, _ = spec["flame"]
        flame_track(t, fx, fy)
    lamp_bezel(t, *spec["status"])
    button(t, *spec["redstone"])
    if spec["readout"]:
        readout_panel(t, *spec["readout"])
    ix, iy = spec["inventory"]
    for row in range(3):
        for col in range(9):
            slot(t, ix - 1 + col * 18, iy - 1 + row * 18)
    for col in range(9):
        slot(t, ix - 1 + col * 18, iy - 1 + 58)
    energy_fill(t)
    progress_fill(t)
    flame_fill(t)
    redstone_icons(t)
    return t.save(paths.TEXTURES / "gui" / "machine" / f"{name}.png")


def java_rect(r) -> str:
    return "null" if r is None else f"new MachineLayout.Rect({r[0]}, {r[1]}, {r[2]}, {r[3]})"


def write_java() -> Path:
    lines = [
        "package dev.groundzero.content.machine;",
        "",
        "import java.util.List;",
        "",
        "import dev.groundzero.GroundZero;",
        "",
        "/** GENERATED by tools/assets/gui.py together with the GUI textures. Do not edit by hand. */",
        "public final class MachineLayouts {",
    ]
    for name, s in LAYOUTS.items():
        slots = ", ".join(f"new MachineLayout.Pos({x}, {y})" for _, x, y in s["slots"])
        p = s["progress"]
        f = s["flame"]
        lines.append(f"    public static final MachineLayout {name.upper()} = new MachineLayout(")
        lines.append(f"            GroundZero.id(\"textures/gui/machine/{name}.png\"), {W}, {H}, List.of({slots}), {s['inventory'][0]}, {s['inventory'][1]},")
        lines.append(f"            {java_rect(s['energy'])}, {ENERGY_FILL[0]}, {ENERGY_FILL[1]},")
        lines.append(f"            {java_rect(p)}, {PROGRESS_FILL[0]}, {PROGRESS_FILL[1]},")
        lines.append(f"            {java_rect(f)}, {FLAME_FILL[0]}, {FLAME_FILL[1]},")
        lines.append(f"            {java_rect(s['status'])}, {java_rect(s['redstone'])}, {REDSTONE_ICONS[0]}, {REDSTONE_ICONS[1]},")
        lines.append(f"            {java_rect(s['readout'])}, {s['title'][0]}, {s['title'][1]}, {java_rect(s.get('tank'))});")
        lines.append("")
    lines.append("    private MachineLayouts() {}")
    lines.append("}")
    out = paths.ROOT / "src" / "main" / "java" / "dev" / "groundzero" / "content" / "machine" / "MachineLayouts.java"
    out.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return out


# --- side-configuration tab (shared by every machine) -----------------------------------------------------------------
# Layout constants mirrored in MachineScreen.SideTab.
TAB = (0, 0, 24, 26)            # collapsed tab
PANEL_RECT = (0, 32, 112, 112)  # expanded panel: channel buttons stacked on the left, face grid on the right
FACE_FRAME = (120, 0)           # 18x18
FACE_ICON = (140, 0)            # 16x16 plain face
FACE_FRONT = (160, 0)           # 16x16 front face
CHANNEL_ON = (120, 24)          # 40x12
CHANNEL_OFF = (120, 40)         # 40x12
CHECK_OFF = (120, 56)           # 10x10
CHECK_ON = (132, 56)            # 10x10
ICON_ITEMS = (164, 24)          # 8x8
ICON_ENERGY = (174, 24)         # 8x8
ICON_WRENCH = (184, 24)         # 12x12
ICON_FLUID = (164, 36)          # 8x8

# Open-end spanner: forked jaw top-right, handle running down-left to a rounded end. 'h' = lit edge, 's' = shade.
# Open-end spanner: a forked jaw (two prongs round a notch) top-right, a 3-px handle running down-left.
# 'h' = lit metal, 's' = shade.
WRENCH = [".......hh...", "......hhh.h.", "......hh..hh", "......hhh.hs", ".....hhhhhs.", "....hhhss...",
          "...hhhs.....", "..hhhs......", ".hhhs.......", "hhhs........", "hhs.........", ".s.........."]
BOLT = ["...##...", "..##....", ".##.....", "######..", "...##...", "..##....", ".##.....", "........"]
DROP = ["...#....", "...#....", "..###...", ".#####..", ".##.##..", ".#.###..", "..###...", "........"]
CRATE = ["........", ".######.", ".#....#.", ".######.", ".#.##.#.", ".#.##.#.", ".######.", "........"]


def tab_panel(t: Tex, x: int, y: int, w: int, h: int) -> None:
    """A tab panel that grows out of the GUI's right edge: flat left side, bevelled right side and corners."""
    t.rect(x, y, w, h, PANEL.base)
    t.hline(x, y, w - 2, PANEL.outline)
    t.hline(x, y + h - 1, w - 2, PANEL.outline)
    t.vline(x + w - 1, y + 2, h - 4, PANEL.outline)
    t.px(x + w - 2, y + 1, PANEL.outline)
    t.px(x + w - 2, y + h - 2, PANEL.outline)
    t.px(x + w - 1, y, (0, 0, 0, 0))
    t.px(x + w - 1, y + 1, (0, 0, 0, 0))
    t.px(x + w - 1, y + h - 1, (0, 0, 0, 0))
    t.px(x + w - 1, y + h - 2, (0, 0, 0, 0))
    t.px(x + w - 2, y, (0, 0, 0, 0))
    t.px(x + w - 2, y + h - 1, (0, 0, 0, 0))
    t.hline(x, y + 1, w - 3, PANEL.highlight)
    t.hline(x, y + h - 2, w - 3, PANEL.deep)
    t.vline(x + w - 2, y + 2, h - 4, PANEL.deep)
    t.rivet(x + w - 5, y + 3, PANEL)
    t.rivet(x + w - 5, y + h - 5, PANEL)


def glyph(t: Tex, rows: list[str], x: int, y: int, c, shade=None) -> None:
    for yy, row in enumerate(rows):
        for xx, ch in enumerate(row):
            if ch in "#h":
                t.px(x + xx, y + yy, c)
            elif ch == "s":
                t.px(x + xx, y + yy, shade if shade is not None else c)


def side_config_texture() -> Path:
    t = Tex(256, 256)
    tab_panel(t, *TAB)
    glyph(t, WRENCH, TAB[0] + 5, TAB[1] + 7, P.STEEL.highlight, P.STEEL.shadow)
    tab_panel(t, *PANEL_RECT)
    px_, py_ = PANEL_RECT[0], PANEL_RECT[1]
    glyph(t, WRENCH, px_ + 5, py_ + 4, P.STEEL.highlight, P.STEEL.shadow)
    t.hline(px_ + 3, py_ + 17, PANEL_RECT[2] - 8, PANEL.shadow)
    t.hline(px_ + 3, py_ + 18, PANEL_RECT[2] - 8, PANEL.light)
    # Face frame + face icons.
    slot(t, *FACE_FRAME)
    fx, fy = FACE_ICON
    t.panel(fx, fy, 16, 16, P.OLIVE, base=3, style="raised")
    t.outline(fx + 2, fy + 2, 12, 12, P.OLIVE.shadow)
    for rx, ry in ((fx + 1, fy + 1), (fx + 14, fy + 1), (fx + 1, fy + 14), (fx + 14, fy + 14)):
        t.rivet(rx, ry, P.OLIVE)
    gx, gy = FACE_FRONT
    t.panel(gx, gy, 16, 16, P.OLIVE, base=3, style="raised")
    t.rect(gx + 3, gy + 3, 7, 3, P.BLACK_PAINT.base)           # nameplate
    t.hline(gx + 4, gy + 4, 5, P.HAZARD_YELLOW.base)
    t.rect(gx + 11, gy + 3, 2, 2, P.INDICATOR_GREEN.base)      # status lamp
    t.stripes(gx + 1, gy + 12, 14, 3, P.HAZARD_YELLOW.base, P.HAZARD_BLACK.base, width=2)
    # Channel buttons (selected / not), checkbox, icons.
    cx, cy = CHANNEL_ON
    t.panel(cx, cy, 40, 12, PANEL, base=4, style="inset")
    t.outline(cx, cy, 40, 12, P.HAZARD_YELLOW.shadow)
    cx, cy = CHANNEL_OFF
    t.panel(cx, cy, 40, 12, PANEL, base=3, style="raised")
    t.outline(cx, cy, 40, 12, PANEL.outline)
    for (bx, by), on in ((CHECK_OFF, False), (CHECK_ON, True)):
        t.rect(bx, by, 10, 10, PANEL.deep)
        t.outline(bx, by, 10, 10, PANEL.outline)
        if on:
            for i, (xx, yy) in enumerate(((2, 5), (3, 6), (4, 7), (5, 6), (6, 5), (7, 4), (7, 3))):
                t.px(bx + xx, by + yy, P.INDICATOR_GREEN.light)
                t.px(bx + xx, by + yy - 1, P.INDICATOR_GREEN.base)
    glyph(t, CRATE, *ICON_ITEMS, P.DESERT_TAN.light)
    glyph(t, BOLT, *ICON_ENERGY, P.HAZARD_YELLOW.light)
    glyph(t, DROP, *ICON_FLUID, P.CHERENKOV.light)
    glyph(t, WRENCH, *ICON_WRENCH, P.STEEL.highlight, P.STEEL.shadow)
    return t.save(paths.TEXTURES / "gui" / "machine" / "side_config.png")


if __name__ == "__main__":
    print(side_config_texture())
    for n, spec in LAYOUTS.items():
        print(build(n, spec))
    print(write_java())
