"""Radiation instruments and medicine (DESIGN.md §21.8, Phase 12 slice 12a).

- Geiger Counter: a civil-defence survey meter. In the inventory a sprite; in hand, on the ground and in a frame a
  model (`geiger_counter_in_hand`): a yellow steel case with a black carrying handle, a meter window standing proud of
  its face (a cream dial, the needle, the red band), a selector knob, and the probe (a chrome tube with its end window)
  clipped along its side on a black cable. Painted with the material library.
- Dosimeter Badge: a film badge on a spring clip; its film shows the dose taken (six sprites, white through yellow,
  orange and red to black, chosen by the client's `groundburst:dose` property).
- Lead Container: a squat lead flask, its lid bolted down, a trefoil placard on its side.
- Rad-Away: an IV bag of orange fluid on its hanger tab, its port at its foot.
- Potassium Iodide Tablets: a blister card of white tablets under a purple band.
- Anti-Rad Injector: an autoinjector pen held like a tool: a green safety cap, a yellow band, an orange tip.
- Effect icons for Rad-Away and Potassium Iodide.
- The HUD sheet (`gui/geiger_hud.png`, read by RadiationClient): the meter (a cream dial with a log scale from 0.1 to
  10 000 RAD/h and its coloured band, over an LCD window), the LCD alone, the dose strip; the sickness vignette; the
  Lead Container's window (a lead-lined well of nine places between two trefoils).
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))

from gb import machine_kit as K  # noqa: E402
from gb import palette as P  # noqa: E402
from gb import paths  # noqa: E402
from gb.model import BlockModel, write_json  # noqa: E402
from gb.palette import fixed  # noqa: E402
from gb.tex import Tex  # noqa: E402
import grenades as G  # noqa: E402
import gui as GUI  # noqa: E402
import resources as R  # noqa: E402
import warheads as W  # noqa: E402

Y, BLK, STEEL, LEAD, RUB = P.HAZARD_YELLOW, P.HAZARD_BLACK, P.STEEL, P.LEAD, P.RUBBER
CREAM = fixed("dial_cream", "#5e5848", "#8a826a", "#b0a888", "#cfc6a4", "#e2dbbc", "#efe9d2", "#f8f4e6")
ORANGE = fixed("rad_away", "#3a1404", "#682608", "#9a3c0c", "#c85816", "#e67826", "#f49a4a", "#fcc48a")
PURPLE = fixed("iodide", "#1e0c2e", "#341648", "#4e2468", "#6a3488", "#8a4ca8", "#ae74c8", "#d2a8e4")
GREEN = fixed("injector_cap", "#0a2410", "#123a1a", "#1c5426", "#286e34", "#3a8a44", "#5aa862", "#8acc8e")
PLASTIC = fixed("badge_plastic", "#1e2226", "#30353b", "#454b52", "#5d646c", "#787f87", "#959ca3", "#b6bcc2")
LCD_GLASS = fixed("lcd_glass", "#040a04", "#08120a", "#0e1c10", "#142616", "#1c321e", "#284428", "#3a5c38")
#: The film's colour by stage of dose: none, I, II, III, IV, lethal.
FILM = [(236, 232, 214), (232, 206, 88), (232, 150, 60), (218, 92, 58), (150, 36, 40), (34, 22, 24)]
OUTLINE = BLK[1]
#: A trefoil five pixels square: two blades up, the hub, one blade down.
TREFOIL5 = [(0, 0), (1, 0), (3, 0), (4, 0), (0, 1), (1, 1), (3, 1), (4, 1), (2, 2), (1, 3), (2, 3), (3, 3), (1, 4), (2, 4), (3, 4)]


def outline(t: Tex, c=OUTLINE) -> None:
    """A dark pixel on every empty side of the drawing (vanilla's item outline)."""
    size = t.w
    filled = {(x, y) for y in range(size) for x in range(size) if t.get(x, y)[3] > 0}
    for (x, y) in list(filled):
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            n = (x + dx, y + dy)
            if 0 <= n[0] < size and 0 <= n[1] < size and n not in filled:
                t.px(n[0], n[1], c)


# --- sprites -------------------------------------------------------------------------------------------------------------------
def geiger_sprite() -> Tex:
    """The inventory icon: the yellow case seen from the front and a little above, its handle, the dial, the probe (a
    pixel in from the right edge, so its outline shows)."""
    t = Tex(16, 16)
    t.rect(4, 2, 6, 1, RUB[3])                                     # the grip
    t.hline(5, 2, 4, RUB[4])
    t.px(4, 3, RUB[2])
    t.px(9, 3, RUB[2])                                             # its posts
    t.rect(1, 4, 11, 1, Y[5])                                      # the case's top, lit
    t.rect(1, 5, 11, 8, Y[3])                                      # its front
    t.vline(1, 5, 8, Y[4])
    t.hline(1, 12, 11, Y[2])
    t.vline(12, 5, 7, Y[1])                                        # its right side, in shade
    t.px(12, 4, Y[3])
    t.rect(2, 6, 7, 5, STEEL[2])                                   # the meter's bezel
    t.rect(3, 7, 5, 3, CREAM[5])                                   # the dial
    t.hline(3, 7, 5, CREAM[6])
    t.px(7, 7, P.WARNING_RED[4])                                   # the red band
    t.px(4, 9, BLK[1])
    t.px(5, 8, BLK[1])                                             # the needle
    t.px(6, 7, BLK[2])
    t.px(10, 10, BLK[2])                                           # the knob
    t.px(10, 9, STEEL[4])
    t.rect(2, 11, 5, 1, Y[2])                                      # a stencilled plate
    t.vline(13, 6, 7, STEEL[4])                                    # the probe
    t.vline(14, 7, 5, STEEL[2])
    t.px(13, 13, BLK[2])                                           # its window end
    t.px(13, 5, RUB[3])                                            # its cable
    outline(t)
    return t


def dosimeter_sprite(stage: int) -> Tex:
    """A film badge: a grey holder on a spring clip, the film showing through its window in its stage's colour, the
    filter strips under it."""
    t = Tex(16, 16)
    t.rect(6, 1, 4, 3, STEEL[3])                                   # the clip
    t.hline(6, 1, 4, STEEL[5])
    t.px(9, 3, STEEL[1])
    t.rect(3, 3, 10, 12, PLASTIC[3])                               # the holder
    t.vline(3, 4, 10, PLASTIC[4])
    t.vline(12, 4, 10, PLASTIC[1])
    t.hline(3, 14, 10, PLASTIC[1])
    t.hline(4, 3, 8, PLASTIC[5])
    t.px(3, 3, (0, 0, 0, 0))
    t.px(12, 3, (0, 0, 0, 0))
    t.px(3, 14, (0, 0, 0, 0))
    t.px(12, 14, (0, 0, 0, 0))                                     # rounded corners
    r, g, b = FILM[stage]
    t.rect(5, 5, 6, 5, (r, g, b, 255))                             # the film
    t.hline(5, 5, 6, (min(255, r + 18), min(255, g + 18), min(255, b + 18), 255))
    t.px(5, 6, (min(255, r + 26), min(255, g + 26), min(255, b + 26), 255))
    t.outline(4, 4, 8, 7, PLASTIC[1])                              # its window
    t.px(5, 11, LEAD[2])
    t.px(6, 11, LEAD[2])                                           # the filters: lead, copper, open
    t.px(8, 11, P.COPPER[3])
    t.px(9, 11, P.COPPER[3])
    t.px(10, 11, PLASTIC[5])
    t.hline(5, 12, 6, PLASTIC[2])
    outline(t)
    return t


def lead_container_sprite() -> Tex:
    """A squat lead flask: shaded round, its lid's rim and bolts, a carrying lug, a trefoil placard; all of it on one
    centre column (x 8: odd widths), so the placard and its trefoil sit in the middle of the flask."""
    t = Tex(16, 16)
    shades = [LEAD[4], LEAD[4], LEAD[3], LEAD[3], LEAD[3], LEAD[3], LEAD[2], LEAD[2], LEAD[2], LEAD[1], LEAD[1]]
    for i, x in enumerate(range(3, 14)):
        t.vline(x, 6, 8, shades[i])                                # the body, lit from the left
    t.hline(3, 13, 11, LEAD[1])
    t.rect(2, 4, 13, 2, LEAD[2])                                   # the lid's rim
    t.hline(2, 4, 13, LEAD[4])
    for x in (4, 8, 12):
        t.px(x, 5, LEAD[5])                                        # its bolts
    t.hline(7, 2, 3, LEAD[3])                                      # the lug: a bar on two posts
    t.px(7, 2, LEAD[4])
    t.px(7, 3, LEAD[2])
    t.px(9, 3, LEAD[1])
    t.rect(5, 7, 7, 7, Y[4])                                       # the placard
    t.outline(5, 7, 7, 7, Y[2])
    for dx, dy in TREFOIL5:
        t.px(6 + dx, 8 + dy, BLK[1])
    outline(t)
    return t


def rad_away_sprite() -> Tex:
    """An IV bag: its hanger tab, clear plastic round orange fluid, a white label, the port at its foot (no line: the
    whole bag and its outline fit inside the sprite)."""
    t = Tex(16, 16)
    clear = (198, 214, 220, 255)
    clear_dark = (132, 150, 158, 255)
    t.rect(6, 1, 4, 2, clear_dark)                                 # the hanger tab
    t.px(7, 2, (0, 0, 0, 0))
    t.px(8, 2, (0, 0, 0, 0))
    t.rect(3, 3, 10, 10, clear)                                    # the bag
    t.vline(12, 4, 9, clear_dark)
    t.rect(4, 5, 8, 7, ORANGE[4])                                  # the fluid
    t.vline(4, 5, 7, ORANGE[5])
    t.vline(11, 5, 7, ORANGE[2])
    t.hline(4, 5, 8, ORANGE[6])
    t.rect(5, 7, 6, 3, (236, 236, 228, 255))                       # the label
    t.hline(5, 8, 6, ORANGE[3])
    t.px(5, 4, (255, 255, 255, 255))                               # a gleam
    t.rect(7, 13, 2, 1, clear_dark)                                # the port
    outline(t)
    return t


def potassium_iodide_sprite() -> Tex:
    """A blister card: a purple band printed KI, two rows of white tablets in their bubbles."""
    t = Tex(16, 16)
    card = (226, 224, 218, 255)
    t.rect(2, 2, 12, 13, card)
    t.vline(13, 2, 13, (178, 176, 170, 255))
    t.hline(2, 14, 12, (178, 176, 170, 255))
    t.rect(2, 2, 12, 7, PURPLE[3])                                 # the band
    t.hline(2, 2, 12, PURPLE[5])
    t.text(5, 3, "KI", (246, 240, 250, 255))
    for row, y in enumerate((9, 12)):
        for x in (3, 7, 11):
            t.rect(x, y, 2, 2, (250, 250, 248, 255))               # a tablet
            t.px(x + 1, y + 1, (196, 196, 204, 255))
    outline(t)
    return t


def injector_sprite() -> Tex:
    """An autoinjector held like a tool (low left to high right): a green cap, a grey body with a yellow band, an orange
    tip at the bottom."""
    t = Tex(16, 16)
    for i in range(10):
        x, y = 3 + i, 12 - i
        if i < 2:
            c, d = (P.WARNING_RED[4], P.WARNING_RED[3]) if i == 0 else (ORANGE[4], ORANGE[3])
        elif i >= 8:
            c, d = GREEN[4], GREEN[2]
        elif i in (5, 6):
            c, d = Y[4], Y[2]
        else:
            c, d = PLASTIC[4], PLASTIC[2]
        t.px(x, y, c)
        t.px(x + 1, y, c)
        t.px(x + 1, y + 1, d)
        t.px(x, y + 1, d)
    t.px(2, 14, STEEL[5])                                          # the needle's shroud, on the pen's axis
    outline(t)
    return t


def effect_icon(kind: str) -> Tex:
    """18x18 effect icons, as vanilla's: Rad-Away's drop in an IV bag, Potassium Iodide's tablet."""
    t = Tex(18, 18)
    if kind == "rad_away":
        t.rect(5, 2, 8, 12, (198, 214, 220, 255))
        t.rect(6, 5, 6, 8, ORANGE[4])
        t.vline(6, 5, 8, ORANGE[5])
        t.hline(6, 5, 6, ORANGE[6])
        t.rect(8, 14, 2, 3, (198, 214, 220, 255))
        t.rect(7, 0, 4, 2, (132, 150, 158, 255))
    else:
        t.disc(9, 9, 7.2, PURPLE[3])
        t.disc(9, 9, 5.5, (246, 244, 240, 255))
        t.hline(5, 9, 9, (190, 186, 196, 255))
        t.text(6, 4, "KI", PURPLE[2])
    for y in range(18):
        for x in range(18):
            if t.get(x, y)[3] > 0:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < 18 and 0 <= ny < 18 and t.get(nx, ny)[3] > 0 and t.get(nx, ny)[:3] != OUTLINE[:3]:
                    t.px(x, y, OUTLINE)
                    break
    return t


# --- the Geiger Counter's model ----------------------------------------------------------------------------------------------
def geiger_model() -> None:
    """In hand: the case (10 x 7 x 6) on its handle, the meter standing proud of its face, the knob, the probe clipped
    along its right side on two brackets, its cable round the back."""
    m = BlockModel("geiger_counter_in_hand", tex_size=(64, 64), kind="item", render_type="minecraft:cutout")
    m.box("case", (3, 1, 5), (13, 8, 11))
    m.box("meter", (4, 2, 4.5), (12, 7, 5), faces=("north", "east", "west", "up", "down"))
    m.box("knob", (11.5, 1.5, 4.3), (12.5, 2.5, 5), faces=("north", "east", "west", "up", "down"))
    m.box("post_l", (4, 8, 7.5), (5, 10, 8.5), faces=("north", "south", "east", "west"))
    m.box("post_r", (11, 8, 7.5), (12, 10, 8.5), faces=("north", "south", "east", "west"))
    m.box("grip", (3.5, 10, 7.25), (12.5, 11, 8.75))
    m.box("clip_a", (13, 2.5, 6), (13.4, 3.5, 7), faces=("north", "south", "up", "down"))
    m.box("clip_b", (13, 2.5, 9), (13.4, 3.5, 10), faces=("north", "south", "up", "down"))
    m.box("probe", (13.4, 2.3, 3.5), (14.9, 3.7, 12))
    m.box("probe_window", (13.6, 2.5, 3.2), (14.7, 3.5, 3.5), faces=("north", "east", "west", "up", "down"))
    # The cable: out of the probe's back end, along behind the case, into its back.
    m.box("cable_a", (13.8, 2.6, 12), (14.6, 3.4, 12.8), faces=("south", "east", "up", "down"))
    m.box("cable_b", (9, 2.6, 12), (13.8, 3.4, 12.8), faces=("north", "south", "up", "down", "west"))
    m.box("cable_c", (9, 2.6, 11), (9.8, 3.4, 12), faces=("east", "west", "up", "down"))
    m.layout()
    k = K.Kit(m, {}, seed=1212, ao_reach=1.5)
    t = Tex(m.tex_w, m.tex_h)
    k.paint(t, ["case"], W.paint(W.YELLOW, 0.62, wear=0.3))
    k.paint(t, ["meter"], W.metal(W.STEEL, 0.55))
    k.paint(t, ["knob", "post_l", "post_r", "cable_a", "cable_b", "cable_c"], W.metal(W.BLACK, 0.4))
    k.paint(t, ["grip"], W.paint(P.RUBBER, 0.45, wear=0.05))
    k.paint(t, ["clip_a", "clip_b"], W.metal(W.STEEL, 0.45))
    k.paint(t, ["probe"], W.metal(W.STEEL, 0.72, brushed=0.06))
    k.paint(t, ["probe_window"], W.metal(W.BLACK, 0.3))

    def dial(f, img):
        """The meter's face (8 x 5), laid out by hand: the steel bezel round a cream dial, its scale of ticks, the red
        band at the right, the needle leaning up and right from its boss."""
        if f.d != "north":
            return
        rows = ["........",
                ".ctctcr.",
                ".ccccnc.",
                ".cccncc.",
                "...b...."]
        colours = {"c": CREAM[5], "t": CREAM[2], "r": P.WARNING_RED[4], "n": BLK[1], "b": BLK[2]}
        for v, row in enumerate(rows[:img.shape[0]]):
            for u, ch in enumerate(row[:img.shape[1]]):
                if ch in colours:
                    img[v, u, :3] = colours[ch][:3]
    k.overlay(t, ["meter"], dial)

    def stencil(f, img):
        """A black stencilled plate under the meter."""
        if f.d != "north":
            return
        h = img.shape[0]
        img[h - 1, 1:6, :3] = (img[h - 1, 1:6, :3] * 0.35).astype(np.uint8)
    k.overlay(t, ["case"], stencil)

    def grille(f, img):
        if f.d == "north":
            img[::2, :, :3] = (img[::2, :, :3] * 0.5).astype(np.uint8)   # the probe's window, slotted
    k.overlay(t, ["probe_window"], grille)
    G.finish(m, t)
    # Held by the handle: in first person a little bigger and turned so the meter faces the player.
    lo, hi = m.bounds()
    c = [(lo[i] + hi[i]) / 2 for i in range(3)]

    def ctx(scale: float, base: tuple[float, float, float], rotation=(0, 0, 0)) -> dict:
        return {"rotation": list(rotation), "translation": [round(base[i] - (c[i] - 8) * scale, 3) for i in range(3)],
                "scale": [scale] * 3}
    m.display["firstperson_righthand"] = ctx(0.4, (1.0, 3.0, 0), (0, -15, 0))
    m.display["firstperson_lefthand"] = ctx(0.4, (1.0, 3.0, 0), (0, 15, 0))
    m.display["thirdperson_righthand"] = ctx(0.5, (0, 1.5, 1.0), (0, 0, 0))
    m.display["thirdperson_lefthand"] = ctx(0.5, (0, 1.5, 1.0), (0, 0, 0))
    m.save(t)


# --- the HUD and the screens --------------------------------------------------------------------------------------------------
METER_W, DIAL_H, READOUT_H = 80, 48, 14
PIVOT = (40.5, 45.5)
SWING = 50.0


def lcd(t: Tex, x: int, y: int, w: int, h: int) -> None:
    """An LCD window: a dark bezel, green-black glass with its unlit segments faintly showing."""
    t.rect(x, y, w, h, BLK[2])
    t.outline(x, y, w, h, BLK[0])
    t.hline(x + 1, y + h - 1, w - 2, BLK[4])
    t.rect(x + 2, y + 2, w - 4, h - 4, LCD_GLASS[3])
    t.hline(x + 2, y + 2, w - 4, LCD_GLASS[5])
    for xx in range(x + 22, x + 50, 6):
        t.rect(xx, y + 4, 4, 1, LCD_GLASS[4])                       # ghost segments: "8888"
        t.rect(xx, y + 7, 4, 1, LCD_GLASS[4])
        t.rect(xx, y + 10, 4, 1, LCD_GLASS[4])


def hud() -> None:
    t = Tex(128, 128)
    # The meter: a yellow instrument face round a cream dial, over the LCD.
    t.rect(0, 0, METER_W, DIAL_H + READOUT_H, Y[3])
    t.outline(0, 0, METER_W, DIAL_H + READOUT_H, BLK[1])
    t.hline(1, 1, METER_W - 2, Y[5])
    t.vline(1, 1, DIAL_H + READOUT_H - 2, Y[4])
    t.vline(METER_W - 2, 1, DIAL_H + READOUT_H - 2, Y[2])
    for x, y in ((3, 3), (METER_W - 5, 3), (3, DIAL_H - 4), (METER_W - 5, DIAL_H - 4)):
        t.rivet(x, y, STEEL, size=2)
    t.rect(6, 4, METER_W - 12, DIAL_H - 6, BLK[1])                  # the dial's bezel
    t.rect(7, 5, METER_W - 14, DIAL_H - 8, CREAM[5])
    t.hline(7, 5, METER_W - 14, CREAM[6])
    t.hline(7, DIAL_H - 4, METER_W - 14, CREAM[3])
    # The band (green, yellow, orange, red by decade) and the scale's ticks on their arc round the pivot.
    bands = [(0.0, 0.2, (96, 168, 72)), (0.2, 0.4, (214, 190, 58)), (0.4, 0.6, (226, 132, 48)), (0.6, 1.0, (196, 52, 40))]
    for yy in range(5, DIAL_H - 3):
        for xx in range(7, METER_W - 7):
            dx, dy = xx + 0.5 - PIVOT[0], PIVOT[1] - (yy + 0.5)
            r = math.hypot(dx, dy)
            ang = math.degrees(math.atan2(dx, dy))
            if abs(ang) > SWING + 0.5:
                continue
            u = (ang + SWING) / (2 * SWING)
            if 24.5 <= r <= 26.7:
                for a, b, c in bands:
                    if a <= u <= b:
                        t.px(xx, yy, (*c, 255))
    for decade in range(6):
        for sub, length in ((1, 4.5), (2, 2.0), (5, 2.5)):
            v = decade + math.log10(sub)
            if v > 5:
                continue
            ang = math.radians(-SWING + 2 * SWING * v / 5)
            r0, r1 = 28.0, 28.0 + length
            for s in np.linspace(r0, r1, 8):
                xx, yy = PIVOT[0] + s * math.sin(ang), PIVOT[1] - s * math.cos(ang)
                t.px(int(xx), int(yy), BLK[1])
    labels = ["0.1", "1", "10", "100", "1K", "10K"]
    for decade, label in enumerate(labels):
        ang = math.radians(-SWING + 2 * SWING * decade / 5)
        xx, yy = PIVOT[0] + 37.5 * math.sin(ang), PIVOT[1] - 37.5 * math.cos(ang)
        left = int(round(xx - Tex.text_width(label) / 2))
        left = max(8, min(METER_W - 8 - Tex.text_width(label), left))
        t.text(left, int(round(yy - 2)), label, BLK[2])
    t.text(int(PIVOT[0] - Tex.text_width("RAD/H") / 2), 33, "RAD/H", BLK[3])
    t.trefoil(15.5, 38.5, 3.2, BLK[2], bg=Y[4])
    lcd(t, 4, DIAL_H, METER_W - 8, READOUT_H - 1)
    # The LCD alone (in the hotbar), and the dose strip with its film well.
    t.rect(0, 64, METER_W, READOUT_H, Y[3])
    t.outline(0, 64, METER_W, READOUT_H, BLK[1])
    lcd(t, 2, 65, METER_W - 4, READOUT_H - 2)
    t.rect(0, 80, METER_W, 12, BLK[3])
    t.outline(0, 80, METER_W, 12, BLK[1])
    t.hline(1, 81, METER_W - 2, BLK[5])
    t.rect(2, 81, 10, 10, BLK[1])                                   # the film's well
    t.save(paths.TEXTURES / "gui" / "geiger_hud.png")

    # The sickness vignette: the edge of sight going dark and green-black, mottled so it swims as it pulses.
    n = 256
    yy, xx = np.mgrid[0:n, 0:n].astype(np.float32)
    d = np.hypot((xx + 0.5) / n * 2 - 1, (yy + 0.5) / n * 2 - 1) / math.sqrt(2)
    rng = np.random.default_rng(12)
    coarse = rng.random((9, 9)).astype(np.float32)
    from scipy import ndimage  # noqa: PLC0415
    mottle = ndimage.zoom(coarse, n / 9, order=3)[:n, :n]
    edge = np.clip((d - 0.32 + 0.08 * (mottle - 0.5)) / 0.52, 0, 1)
    alpha = (edge ** 1.6 * 235).astype(np.uint8)
    img = np.zeros((n, n, 4), np.uint8)
    img[..., 0], img[..., 1], img[..., 2] = 14, 26, 8
    img[..., 3] = alpha
    from PIL import Image  # noqa: PLC0415
    Image.fromarray(img, "RGBA").save(paths.TEXTURES / "gui" / "radiation_vignette.png")

    # The Lead Container: a lead-lined well of nine places between two trefoils, over the inventory.
    t = Tex(256, 256)
    GUI.window(t, H=173, inv_y=91)                                 # 7 taller than a chest's: the well clears the header
    gx, gy = 61, 23
    t.rect(gx - 4, gy - 3, 62, 60, LEAD[1])
    t.outline(gx - 4, gy - 3, 62, 60, LEAD[0])
    t.hline(gx - 3, gy - 2, 60, LEAD[3])
    for x, y in ((gx - 3, gy - 2), (gx + 55, gy - 2), (gx - 3, gy + 54), (gx + 55, gy + 54)):
        t.px(x, y, LEAD[5])
    for row in range(3):
        for col in range(3):
            GUI.slot(t, gx + col * 18, gy + row * 18)
    for cx in (30.5, 146.5):                                        # level with the well's middle
        t.disc(cx, gy + 26.5, 13.0, BLK[1])
        t.disc(cx, gy + 26.5, 12.0, Y[4])
        t.trefoil(cx, gy + 26.5, 10.0, BLK[1])
    for row in range(3):                                            # the player's inventory and hotbar
        for col in range(9):
            GUI.slot(t, 7 + col * 18, 90 + row * 18)
    for col in range(9):
        GUI.slot(t, 7 + col * 18, 148)
    t.save(paths.TEXTURES / "gui" / "lead_container.png")


# --- item definitions -----------------------------------------------------------------------------------------------------------
def item_defs() -> None:
    # The Geiger Counter: the sprite in the inventory, the model everywhere else.
    write_json(paths.ASSETS / "items" / "geiger_counter.json", {"model": {
        "type": "minecraft:select", "property": "minecraft:display_context",
        "cases": [{"when": "gui", "model": {"type": "minecraft:model", "model": "groundburst:item/geiger_counter"}}],
        "fallback": {"type": "minecraft:model", "model": "groundburst:item/geiger_counter_in_hand"}}})
    # The Dosimeter Badge: its film by the dose its holder has taken.
    thresholds = [100, 200, 400, 600, 1000]
    write_json(paths.ASSETS / "items" / "dosimeter_badge.json", {"model": {
        "type": "minecraft:range_dispatch", "property": "groundburst:dose",
        "entries": [{"threshold": v, "model": {"type": "minecraft:model", "model": f"groundburst:item/dosimeter_badge_{i + 1}"}}
                    for i, v in enumerate(thresholds)],
        "fallback": {"type": "minecraft:model", "model": "groundburst:item/dosimeter_badge_0"}}})


def main() -> None:
    sprite = geiger_sprite()
    sprite.save(paths.TEXTURES / "item" / "geiger_counter.png")
    write_json(paths.MODELS / "item" / "geiger_counter.json",
               {"parent": "minecraft:item/generated", "textures": {"layer0": "groundburst:item/geiger_counter"}})
    geiger_model()
    for stage in range(6):
        name = f"dosimeter_badge_{stage}"
        dosimeter_sprite(stage).save(paths.TEXTURES / "item" / f"{name}.png")
        write_json(paths.MODELS / "item" / f"{name}.json",
                   {"parent": "minecraft:item/generated", "textures": {"layer0": f"groundburst:item/{name}"}})
    R.save_item("lead_container", lead_container_sprite())
    R.save_item("rad_away", rad_away_sprite())
    R.save_item("potassium_iodide", potassium_iodide_sprite())
    R.save_item("anti_rad_injector", injector_sprite())
    item_defs()
    for kind in ("rad_away", "potassium_iodide"):
        effect_icon(kind).save(paths.TEXTURES / "mob_effect" / f"{kind}.png")
    hud()
    print("radiation assets written")


if __name__ == "__main__":
    main()
