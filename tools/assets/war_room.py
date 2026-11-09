"""The War Room Display (DESIGN.md §17.17; the user's plan: "every missile, impact and radar contact on a big screen"),
after the big boards of NORAD's Combat Operations Center and SAC's command post: a wall screen seven blocks wide.

- Formed (GeckoLib), 7 wide, 5 high and 1 deep, the controller in the bottom row's middle (front = north):
  * a dark steel cabinet the whole size of the structure, one big screen across its middle three rows: its glass set two
    pixels back in a heavy bezel (the block entity's renderer lays the network's picture over the glass while it is lit);
  * the header over it: the world's clock at the west end (its hands "hand_h", "hand_m" turned by the block entity), the
    WAR ROOM plate, and the alert condition's panel at the east end: five lamps from 5 (normal) to 1 (attack), each lit
    in its own bone ("lamp5" .. "lamp1", the block entity shows the one it is at) with its number under it;
  * the cabinet below: louvres and speaker grilles, a control panel in its middle (a key switch, toggles, the power lamp
    "power", a plate), a kick plate along the floor;
  * the back: the Energy Port's plate where its block is, louvres, the cable gland.
- Controller (block) and Display Panel (part block): a panel's face is a dark screen in a steel bezel.

    tools\\.venv\\Scripts\\python.exe tools\\assets\\war_room.py
"""
from __future__ import annotations

import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))

from gb import machine_kit as K  # noqa: E402
from gb import materials as M  # noqa: E402
from gb import palette as P  # noqa: E402
from gb import panels as PN  # noqa: E402
from gb.geo import Animation, GeoModel, save_animations  # noqa: E402
from gb.tex import Tex  # noqa: E402
import assemblers as A  # noqa: E402
import command_room as CR  # noqa: E402
import multiblock_tools as MT  # noqa: E402
import scud_storm as SS  # noqa: E402
import structures as ST  # noqa: E402
import warheads as W  # noqa: E402

NAME = "war_room_display"
ALL, SIDES = SS.ALL, SS.SIDES
#: The structure's size (model pixels: x across, y up, z from the front), the cabinet's front plane behind the bezel.
X0, X1, Y_TOP, Z_FRONT, Z_BACK = -56.0, 56.0, 80.0, -8.0, 8.0
Z_BODY = -3.0
#: The screen's glass and its bezel (the glass two pixels back from the bezel's face).
GLASS = (-53.0, 19.0, 53.0, 61.0)
Z_GLASS = -6.0
#: The header's clock (its middle and radius), the plate, the alert condition's panel and lamps.
CLOCK = (-44.0, 72.0, 5.6)
PLATE = (-32.0, 68.5, 2.0, 75.5)
COND = (8.0, 65.0, 52.0, 79.0)
LAMP_XS = (14.0, 22.0, 30.0, 38.0, 46.0)
LAMP_Y = (70.0, 75.0)
#: The lamps' colours lit (5 normal to 1 attack, left to right as you face it) and dark.
LAMP_ON = {5: (90, 255, 110), 4: (255, 228, 70), 3: (255, 176, 40), 2: (255, 108, 36), 1: (255, 52, 36)}
LAMP_OFF = {5: (22, 60, 28), 4: (70, 62, 20), 3: (72, 50, 14), 2: (70, 32, 14), 1: (70, 16, 12)}
#: The cabinet below: its control panel (x0, y0, x1, y1) and its power lamp.
CONTROL = (-12.0, 3.0, 12.0, 13.0)
POWER = (8.0, 10.5)
#: The Energy Port's plate on the back, where its block stands (two columns west of the controller).
PORT_X = -32.0
DARK_STEEL = M.ramp("#101214", "#181b1e", "#212529", "#2b3035", "#363c42", "#434a51", "#525a62", "#656e77", "#7c868f")


def lamp_x(condition: int) -> float:
    """Condition {condition}'s lamp, from 5 at the west end of the panel... as the viewer reads it, left to right (east to
    west: the viewer faces south, so their left is east)."""
    return LAMP_XS[::-1][5 - condition]


def build() -> tuple[GeoModel, dict]:
    g = GeoModel(NAME, tex_size=(256, 256))
    parts: dict[str, list[str]] = {}
    SS.ROUND.clear()
    g.bone("frame")
    F = "frame"

    def box(group, n, frm, to, bone=F, faces=ALL, rotation=None):
        g.box(n, frm, to, faces=faces, bone=bone, rotation=rotation)
        parts.setdefault(group, []).append(n)

    def ring(group, n, r, a0, a1, bone=F, axis="z", c=(0.0, 0.0), **kw):
        names = W.round_section(SS.OnBone(g, bone), n, r, a0, a1, axis=axis, c=c, **kw)
        SS.ROUND.extend(names)
        parts.setdefault(group, []).extend(names)
        return names

    gx0, gy0, gx1, gy1 = GLASS
    # The body behind the front: its back, sides and top.
    box("body", "body", (X0, 0, Z_BODY), (X1, Y_TOP, Z_BACK), faces=("south", "east", "west", "up"))
    # The cabinet below the screen, the bezel round the glass, the header over it.
    box("cabinet", "cabinet", (X0, 0, Z_FRONT), (X1, 16.0, Z_BODY), faces=("north", "east", "west"))
    box("bezel", "bezel_bottom", (X0, 16.0, Z_FRONT), (X1, gy0, Z_BODY), faces=("north", "east", "west", "up"))
    box("bezel", "bezel_top", (X0, gy1, Z_FRONT), (X1, 64.0, Z_BODY), faces=("north", "east", "west", "down"))
    box("bezel", "bezel_east", (gx1, gy0, Z_FRONT), (X1, gy1, Z_BODY), faces=("north", "east", "west"))
    box("bezel", "bezel_west", (X0, gy0, Z_FRONT), (gx0, gy1, Z_BODY), faces=("north", "east", "west"))
    box("glass", "glass", (gx0, gy0, Z_GLASS), (gx1, gy1, Z_BODY), faces=("north",))
    # The glass's rubber gasket in the bezel's reveal, and the bright trim along the screen's top and foot.
    box("gasket", "gasket_bottom", (gx0, gy0, Z_GLASS - 0.4), (gx1, gy0 + 0.6, Z_GLASS), faces=("north", "up"))
    box("gasket", "gasket_top", (gx0, gy1 - 0.6, Z_GLASS - 0.4), (gx1, gy1, Z_GLASS), faces=("north", "down"))
    box("gasket", "gasket_east", (gx1 - 0.6, gy0 + 0.6, Z_GLASS - 0.4), (gx1, gy1 - 0.6, Z_GLASS), faces=("north", "west"))
    box("gasket", "gasket_west", (gx0, gy0 + 0.6, Z_GLASS - 0.4), (gx0 + 0.6, gy1 - 0.6, Z_GLASS), faces=("north", "east"))
    for which, y in (("low", 15.4), ("high", 64.0)):
        box("trim", f"trim_{which}", (X0 + 1.2, y, Z_FRONT - 0.3), (X1 - 1.2, y + 0.6, Z_FRONT), faces=("north", "up", "down"))
    for which, x0 in (("east", X1 - 1.6), ("west", X0 + 0.6)):
        box("trim", f"post_{which}", (x0, 1.0, Z_FRONT - 0.5), (x0 + 1.0, Y_TOP - 1.0, Z_FRONT), faces=("north", "east", "west", "up", "down"))
    box("header", "header", (X0, 64.0, Z_FRONT), (X1, Y_TOP, Z_BODY), faces=("north", "east", "west", "up"))
    # The header's things: the clock (its bezel, its face, its hands in bones of their own), the plate, the condition panel.
    cx, cy, cr = CLOCK
    ring("clock_rim", "clock_rim", cr + 0.8, Z_FRONT - 0.9, Z_FRONT, c=(cx, cy), lo=0.0)
    ring("clock_face", "clock_face", cr, Z_FRONT - 1.1, Z_FRONT - 0.9, c=(cx, cy), lo=0.0)
    for hand, length, width, z0 in (("hand_h", 3.4, 0.7, Z_FRONT - 1.3), ("hand_m", 4.8, 0.5, Z_FRONT - 1.5)):
        g.bone(hand, pivot=(cx, cy, z0), parent=F)
        box(hand, f"{hand}_arm", (cx - width / 2, cy - 1.0, z0), (cx + width / 2, cy + length, z0 + 0.2), bone=hand,
            faces=("north", "east", "west", "up", "down"))
    ring("clock_hub", "clock_hub", 0.6, Z_FRONT - 1.7, Z_FRONT - 1.1, c=(cx, cy), lo=0.0)
    px0, py0, px1, py1 = PLATE
    box("plate", "plate", (px0, py0, Z_FRONT - 0.4), (px1, py1, Z_FRONT), faces=("north", "east", "west", "up", "down"))
    qx0, qy0, qx1, qy1 = COND
    box("cond_panel", "cond_panel", (qx0, qy0, Z_FRONT - 0.5), (qx1, qy1, Z_FRONT), faces=("north", "east", "west", "up", "down"))
    ly0, ly1 = LAMP_Y
    for c in range(1, 6):
        x = lamp_x(c)
        box(f"lamp_dark_{c}", f"lamp_dark{c}", (x - 2.6, ly0, Z_FRONT - 1.1), (x + 2.6, ly1, Z_FRONT - 0.5),
            faces=("north", "east", "west", "up", "down"))
        g.bone(f"lamp{c}", pivot=(x, ly0, Z_FRONT - 1.1), parent=F)
        box(f"lamp_lit_{c}", f"lamp_lit{c}", (x - 2.75, ly0 - 0.15, Z_FRONT - 1.3), (x + 2.75, ly1 + 0.15, Z_FRONT - 0.5), bone=f"lamp{c}",
            faces=("north", "east", "west", "up", "down"))
    # The cabinet's control panel: a key switch, toggles, the power lamp (dark, and lit in its own bone).
    kx0, ky0, kx1, ky1 = CONTROL
    box("control", "control", (kx0, ky0, Z_FRONT - 0.8), (kx1, ky1, Z_FRONT), faces=("north", "east", "west", "up", "down"))
    ring("key", "key_ring", 1.3, Z_FRONT - 1.2, Z_FRONT - 0.8, c=(-7.0, 8.0), lo=0.0)
    box("key", "key_bit", (-7.3, 7.0, Z_FRONT - 2.0), (-6.7, 9.0, Z_FRONT - 1.2), faces=ALL)
    for j, tx in enumerate((-2.5, 0.0, 2.5)):
        box("toggle_base", f"toggle_base{j}", (tx - 0.7, 7.0, Z_FRONT - 1.0), (tx + 0.7, 9.4, Z_FRONT - 0.8), faces=("north", "east", "west", "up", "down"))
        box("toggle", f"toggle{j}", (tx - 0.2, 8.4, Z_FRONT - 2.4), (tx + 0.2, 8.8, Z_FRONT - 1.0), faces=ALL)
    px, py = POWER
    box("power_dark", "power_dark", (px - 0.9, py - 0.9, Z_FRONT - 1.2), (px + 0.9, py + 0.9, Z_FRONT - 0.8), faces=("north", "east", "west", "up", "down"))
    g.bone("power", pivot=(px, py, Z_FRONT - 1.2), parent=F)
    box("power_lit", "power_lit", (px - 1.05, py - 1.05, Z_FRONT - 1.4), (px + 1.05, py + 1.05, Z_FRONT - 0.8), bone="power",
        faces=("north", "east", "west", "up", "down"))
    box("control_plate", "control_plate", (-4.0, 4.0, Z_FRONT - 1.0), (4.0, 5.6, Z_FRONT - 0.8), faces=("north", "east", "west", "up", "down"))
    # The back: the Energy Port's plate where its block stands, the cable gland.
    box("port", "port", (PORT_X - 7.0, 1.0, Z_BACK), (PORT_X + 7.0, 15.0, Z_BACK + 0.5), faces=("south", "east", "west", "up", "down"))
    box("gland", "gland", (20.0, 2.0, Z_BACK), (26.0, 6.0, Z_BACK + 1.0), faces=("south", "east", "west", "up", "down"))
    return g, parts


# --- painting ----------------------------------------------------------------------------------------------------------------
BODY = M.painted(DARK_STEEL, tone=0.5, wear=0.03, grime=0.02, mottle=0.015, grain=0.0, dither=0.0, falloff=0.03)
BEZEL = M.painted(DARK_STEEL, tone=0.42, wear=0.12)
HEADER = M.painted(DARK_STEEL, tone=0.46, wear=0.08)


def cabinet_face(f: M.Face, img: np.ndarray) -> None:
    """The cabinet below the screen: a kick plate along the floor, louvres either side of the control panel, the speaker
    grilles at its ends, panel seams at the blocks' joints."""
    if f.d != "north":
        return
    rgb = img[..., :3].astype(np.float32)
    rgb[f.Y < 1.6] *= 0.5
    for sx in (-48.0, -24.0, 24.0, 48.0):
        louvre = (np.abs(f.X - sx) < 7.0) & (f.Y > 4.0) & (f.Y < 12.0)
        rgb[louvre & (np.floor(f.Y * 2) % 2 == 0)] *= 0.42
    for sx in (-38.0, 38.0):
        grille = (np.abs(f.X - sx) < 3.0) & (np.abs(f.Y - 8.0) < 3.0) & (np.floor(f.X) % 2 == 0) & (np.floor(f.Y) % 2 == 0)
        rgb[grille] *= 0.35
    seams = np.zeros_like(f.X, dtype=bool)
    for jx in (-40.0, -24.0, -8.0, 8.0, 24.0, 40.0):
        seams |= np.abs(f.X - jx) < 0.25
    rgb[seams & (f.Y > 1.6)] *= 0.6
    img[..., :3] = np.clip(rgb, 0, 255).astype(np.uint8)


def body_face(f: M.Face, img: np.ndarray) -> None:
    """The back and sides: louvres high and low on the back, its stiffening ribs at the blocks' joints."""
    rgb = img[..., :3].astype(np.float32)
    if f.d == "south":
        for sx in (-44.0, -16.0, 16.0, 44.0):
            for y0 in (6.0, 66.0):
                louvre = (np.abs(f.X - sx) < 8.0) & (f.Y > y0) & (f.Y < y0 + 8.0) & (np.floor(f.Y * 2) % 2 == 0)
                rgb[louvre] *= 0.45
        for jx in (-40.0, -24.0, -8.0, 8.0, 24.0, 40.0):
            rgb[np.abs(f.X - jx) < 0.4] *= 0.72
    if f.d in ("east", "west"):
        rgb[np.abs(f.Y % 16.0 - 8.0) > 7.6] *= 0.75
    img[..., :3] = np.clip(rgb, 0, 255).astype(np.uint8)


def bezel_bolts(f: M.Face, img: np.ndarray) -> None:
    """The bezel's bolts: a pair at each of its corners and along its long sides, every block."""
    if f.d != "north":
        return
    rgb = img[..., :3].astype(np.float32)
    bolts = np.zeros_like(f.X, dtype=bool)
    for bx in np.arange(X0 + 4.0, X1 - 3.0, 16.0):
        for by in (17.5, 62.5):
            bolts |= (np.abs(f.X - bx) < 0.5) & (np.abs(f.Y - by) < 0.5)
    for by in (24.0, 40.0, 56.0):
        for bx in (X0 + 2.5, X1 - 2.5):
            bolts |= (np.abs(f.X - bx) < 0.5) & (np.abs(f.Y - by) < 0.5)
    rgb[bolts] = rgb[bolts] * 0.5 + 60
    img[..., :3] = np.clip(rgb, 0, 255).astype(np.uint8)


def glass_face(f: M.Face, img: np.ndarray) -> None:
    """The screen dark: deep green-black glass, a faint phosphor grid, a soft sheen across its upper corner."""
    rgb = np.zeros_like(img[..., :3], dtype=np.float32)
    rgb[...] = (8, 16, 11)
    grid = (np.floor(f.X) % 8 == 0) | (np.floor(f.Y) % 8 == 0)
    rgb[grid] = (12, 26, 16)
    sheen = (f.X + 53.0) * 0.4 + (f.Y - 19.0) * 1.0
    rgb[(sheen > 40) & (sheen < 44)] += 10
    img[..., :3] = np.clip(rgb, 0, 255).astype(np.uint8)


def paint(g: GeoModel, parts: dict) -> tuple[Tex, Tex]:
    k = K.Kit(g, {}, seed=2201, ao_reach=2.4, turned=True)
    W.round_faces(k.S, SS.ROUND, top=True, bottom=False, foot=False)
    S = k.S
    t, glow = Tex(g.tex_w, g.tex_h), Tex(g.tex_w, g.tex_h)
    S.paint(t, parts["body"], BODY)
    S.overlay(t, parts["body"], body_face)
    S.paint(t, parts["cabinet"], BODY)
    S.overlay(t, parts["cabinet"], cabinet_face)
    S.paint(t, parts["bezel"], BEZEL)
    S.overlay(t, parts["bezel"], bezel_bolts)
    S.paint(t, parts["trim"], K.BRIGHT)
    CR.fill_faces(g, t, parts["gasket"], (16, 17, 18))
    S.paint(t, parts["header"], HEADER)
    S.paint(t, parts["glass"], M.painted(M.BLACK_RAMP, tone=0.3, wear=0.0))
    S.overlay(t, parts["glass"], glass_face)
    S.paint(t, parts["clock_rim"] + parts["clock_hub"], K.BRIGHT)
    S.paint(t, parts["clock_face"], M.white_paint(tone=0.85, wear=0.0))
    S.paint(t, parts["plate"] + parts["control_plate"], K.WHITE)
    S.paint(t, parts["cond_panel"] + parts["control"], CR.PANEL)
    S.paint(t, parts["key"] + parts["toggle"], K.BRIGHT)
    S.paint(t, parts["toggle_base"], K.BLACK)
    S.paint(t, parts["port"], M.steel(tone=0.4, brushed=0.04, rust=0.05))
    S.paint(t, parts["gland"], K.GUN)
    CR.fill_faces(g, t, parts["hand_h"] + parts["hand_m"], (22, 22, 26))

    def clock_marks(f: M.Face, img: np.ndarray) -> None:
        if f.d != "north":
            return
        cx, cy, cr = CLOCK
        a = np.arctan2(f.Y - cy, f.X - cx)
        r = np.hypot(f.X - cx, f.Y - cy)
        hour = (np.abs((a / (np.pi / 6)) - np.round(a / (np.pi / 6))) < 0.12) & (r > cr - 1.4) & (r < cr - 0.3)
        img[..., :3][hour] = (30, 30, 34)
    S.overlay(t, parts["clock_face"], clock_marks)

    def stencil(tx: Tex, text: str, r, colour, scale_y: int = 1):
        x, y, w, h = r
        tw = Tex.text_width(text)
        tx.text(x + max(0, (w - tw) // 2), y + max(0, (h - 5) // 2), text, colour)

    g.paint_all(t, "plate", lambda tx, r, d: stencil(tx, "WAR ROOM", r, (26, 28, 32, 255)) if d == "north" else None)
    g.paint_all(t, "control_plate", lambda tx, r, d: stencil(tx, "DISPLAY", r, (26, 28, 32, 255)) if d == "north" else None)

    def cond_legend(f: M.Face, img: np.ndarray) -> None:
        # Each lamp's number under it, in white stencil (drawn from the face's own pixels: three by five).
        if f.d != "north":
            return
        rgb = img[..., :3]
        for c in range(1, 6):
            x = lamp_x(c)
            glyph = DIGITS[c]
            for gy, row in enumerate(glyph):
                for gx, ch in enumerate(row):
                    if ch == "#":
                        # (read left to right by a viewer facing the display: their right is the model's -x)
                        sel = (np.floor(f.X) == np.floor(x + 1.5 - gx)) & (np.floor(f.Y) == np.floor(68.0 - gy))
                        rgb[sel] = (214, 214, 204)
    S.overlay(t, parts["cond_panel"], cond_legend)
    for c in range(1, 6):
        CR.fill_faces(g, t, parts[f"lamp_dark_{c}"], LAMP_OFF[c])
        CR.fill_faces(g, t, parts[f"lamp_lit_{c}"], LAMP_ON[c])
        CR.fill_faces(g, glow, parts[f"lamp_lit_{c}"], LAMP_ON[c])
    CR.fill_faces(g, t, parts["power_dark"], (20, 60, 26))
    CR.fill_faces(g, t, parts["power_lit"], (90, 255, 110))
    CR.fill_faces(g, glow, parts["power_lit"], (90, 255, 110))
    border = M.painted(M.RED_PAINT_RAMP, tone=0.58, wear=0.3)

    def port_rim(f, img):
        ring = K.ring(f)
        M.paste(img, k.on(f, border), ring < 2)
        M.darken(img, ring == 2, 0.55)
    k.overlay(t, "port", port_rim, only=("south",))
    return t, glow


#: Three-by-five numerals for the condition lamps (top row first, as they are read).
DIGITS = {
    1: (".#.", "##.", ".#.", ".#.", "###"),
    2: ("##.", "..#", ".#.", "#..", "###"),
    3: ("##.", "..#", ".#.", "..#", "##."),
    4: ("#.#", "#.#", "###", "..#", "..#"),
    5: ("###", "#..", "##.", "..#", "##."),
}


# --- the blocks ---------------------------------------------------------------------------------------------------------------
def panel_tex() -> Tex:
    """A Display Panel's face: dark glass (a faint phosphor grid, a sheen) in a bevelled steel bezel."""
    img = PN.face(M.painted(DARK_STEEL, tone=0.45, wear=0.1), style="inset", seed=31)
    t = M.to_tex(img)
    t.rect(2, 2, 12, 12, (8, 16, 11, 255))
    for i in range(2, 14):
        for j in range(2, 14):
            if i % 4 == 0 or j % 4 == 0:
                t.px(i, j, (12, 26, 16, 255))
    for i in range(3, 7):
        t.px(i + 6, i - 1, (26, 40, 32, 255))
    t.outline(2, 2, 12, 12, (24, 28, 32, 255))
    return t


def placard(t: Tex) -> None:
    """The controller's placard: a little big board, a green map with a red track across it."""
    t.rect(4, 9, 6, 4, (8, 22, 12, 255))
    t.px(5, 11, (70, 220, 90, 255))
    t.px(6, 10, (70, 220, 90, 255))
    t.px(8, 11, (255, 70, 50, 255))
    t.px(7, 10, (255, 176, 40, 255))


def spawner_marks(ox: int, oy: int, w: int, h: int):
    """The spawner sheet's plan: one long row, the screen along it."""
    return [(x, oy + h // 2, P.INDICATOR_GREEN[4]) for x in range(ox + 1, ox + w - 1)]


def main() -> None:
    ST.part_block("display_panel", panel_tex())
    ST.controller(NAME, ST.controller_front(placard), ST.controller_top(), ST.casing())
    model, parts = build()
    model.layout()
    texture, glowmask = paint(model, parts)
    W.match_walls(model, texture, [b.name for b in model.boxes], entity=True)
    for p in model.save(texture, glowmask):
        print(p)
    idle = Animation("idle", 1.0, loop="hold_on_last_frame")
    idle.rotate("hand_h", {0: (0, 0, 0), 1: (0, 0, 0)})
    print(save_animations(NAME, [idle]))
    MT.structure_spawner(f"{NAME}_spawner", A.plan(7, 1, spawner_marks))
    print("war room display written; texture", model.tex_w, model.tex_h)


if __name__ == "__main__":
    main()
