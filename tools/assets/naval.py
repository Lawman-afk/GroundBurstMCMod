"""Sea mines and depth charges (DESIGN.md §14.6): 3D item models, also drawn by the game's entity renderers.

- Sea mine (the body, item/naval/mine_body; the Drifting Sea Mine's item): the moored contact mine of both world wars,
  a black steel sphere 14 px across in bands from a true circle, with nine lead Hertz horns (one on top, four round its
  shoulder at 45 degrees, four round its waist between them), a bolted flange round its middle and the chain's shackle
  under it. Rust weeps from the flange, the paint is worn through at the horns' feet and a band of green-brown sea growth
  clings low down. Its centre is the model's (8, 8, 8), where the renderer puts the mine's centre.
- Sinker (item/naval/mine_sinker): the box-shaped iron trolley a moored mine is laid with (mines were rolled along a
  minelayer's rails on its four small wheels), the mooring chain's drum on top, rust-eaten cast iron. Its bottom is the
  model's y 0; the chain leaves the top of the drum (CHAIN_FROM).
- Moored Sea Mine (item/moored_mine): the mine on its sinker, as stowed on a minelayer's deck. Its item model picks the
  renderer's parts by custom_model_data ("body", "sinker").
- Depth Charge (item/depth_charge): the "ashcan", a navy-grey drum with rolled chimes at its ends and two rolling hoops,
  the hydrostatic pistol's dial in one end and the primer's cap in the other, a yellow band and white stencils.
- The mooring chain (textures/entity/naval/mine_chain.png): rusty links on two crossed planes, as vanilla's chain is
  drawn: the first plane's links face-on where the second's are edge-on (u 0-3 and 3-6 of 16), four links a block.

Pixels: x right, y up, z back. 16 = one block.

    tools\\.venv\\Scripts\\python.exe tools\\assets\\naval.py
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))

from gb import machine_kit as K  # noqa: E402
from gb import materials as M  # noqa: E402
from gb import palette as P  # noqa: E402
from gb import paths  # noqa: E402
from gb.model import BlockModel, write_json  # noqa: E402
from gb.tex import Tex  # noqa: E402
import warheads as W  # noqa: E402

SIDES = ("north", "south", "east", "west")
#: The mine's radius, and the edges of its bands up from the waist (mirrored below it): each band as wide as a true
#: sphere at its middle, so the stack reads as a ball, finer toward the poles where it curves fastest.
R = 7.0
EDGES = (0.5, 1.5, 2.6, 3.7, 4.7, 5.5, 6.1, 6.6, 7.0)
FLANGE = R + 0.45
#: A horn: its shaft's half-width, how far out of the surface it stands and how deep it is sunk; its rounded tip.
HORN, HORN_OUT, HORN_IN, TIP, TIP_OUT = 0.6, 1.9, 0.8, 0.45, 2.5
#: The waist horns' height over the middle (above the flange).
WAIST_Y = 1.8
#: Where the chain hangs from, under the mine's centre (the shackle's pin), in pixels.
SHACKLE = R + 1.7
#: The sinker: its body, its drum along x on top, and where the chain leaves it (the drum's top), from its bottom.
DRUM_Y, DRUM_R = 7.2, 1.6
CHAIN_FROM = DRUM_Y + DRUM_R

# --- materials --------------------------------------------------------------------------------------------------------
MINE_BLACK = M.painted(M.BLACK_RAMP, tone=0.6, wear=0.12, rust=0.1, under=M.STEEL_RAMP[2:8], grime=0.0, streaks=0.0, falloff=0.03,
                       dust=0.0, mottle=0.03, grain=0.02, dither=0.35, edge_light=0.08, edge_dark=0.06, facing=W.ROUND)
FLANGE_BLACK = M.painted(M.BLACK_RAMP, tone=0.42, wear=0.22, rust=0.22, under=M.STEEL_RAMP[2:8], grime=0.0, falloff=0.0, dust=0.0,
                         mottle=0.03, grain=0.02, dither=0.35, facing=W.ROUND)
LEAD = W.metal(P.LEAD, 0.58)
LEAD_TIP = W.metal(P.LEAD, 0.7)
IRON = W.metal(P.GUNMETAL, 0.36, rust=0.25)
GROWTH = M.Material(ramp=M.ramp("#17150c", "#211e11", "#2c2816", "#38331c", "#453f22", "#524b29", "#615831", "#72683b"), tone=0.4,
                    mottle=0.14, grain=0.08, dither=0.8, edge_light=0.04, edge_dark=0.04, ao=0.3, grime=0.0, dust=0.0, despeckle=False,
                    facing=W.ROUND)
BARE = np.array([150, 152, 150], dtype=np.float32)
SINKER_IRON = M.painted(M.ARMOUR_RAMP, tone=0.42, wear=0.4, rust=0.5, under=M.STEEL_RAMP[1:6], grime=0.18, streaks=0.12, dust=0.02)
WHEEL = W.metal(P.GUNMETAL, 0.3, rust=0.35)
DRUM = M.painted(M.ARMOUR_RAMP, tone=0.34, wear=0.3, rust=0.4, under=M.STEEL_RAMP[1:6], grime=0.1, dust=0.0)
NAVY = M.ramp("#15191d", "#1e2429", "#283037", "#333c45", "#3f4a54", "#4c5864", "#5b6874", "#6c7a86", "#808e9a")
CHARGE_GREY = M.painted(NAVY, tone=0.5, wear=0.16, rust=0.06, under=M.STEEL_RAMP[2:8], grime=0.05, streaks=0.0, falloff=0.03, dust=0.0,
                        mottle=0.03, grain=0.02, dither=0.35, facing=W.ROUND)
HOOP_GREY = M.painted(NAVY, tone=0.4, wear=0.3, rust=0.12, under=M.STEEL_RAMP[2:8], grime=0.0, falloff=0.0, dust=0.0, mottle=0.03,
                      grain=0.02, dither=0.35, facing=W.ROUND)
BAND_YELLOW = M.painted(M.YELLOW_PAINT_RAMP, tone=0.6, wear=0.1, under=M.STEEL_RAMP[2:8], grime=0.0, falloff=0.0, dust=0.0, mottle=0.03,
                        grain=0.02, dither=0.35, facing=W.ROUND)
PISTOL = W.metal(P.BRASS, 0.5)
PRIMER = W.metal(P.STEEL, 0.55)
STENCIL = (176, 178, 170, 255)


class Parts:
    """A model's boxes by painter's group, and how each round section's faces end (top lip, bottom lip)."""

    def __init__(self, m: BlockModel):
        self.m = m
        self.groups: dict[str, list[str]] = {}
        self.round: dict[str, tuple[bool, bool]] = {}

    def add(self, group: str, names, top: bool | None = None, bottom: bool | None = None) -> list[str]:
        self.groups.setdefault(group, []).extend(names)
        if top is not None:
            for n in names:
                self.round[n] = (top, bool(bottom))
        return list(names)

    def box(self, group: str, frm, to, faces=("north", "south", "east", "west", "up", "down"), rotation=None) -> str:
        n = f"{group}{len(self.groups.get(group, []))}"
        self.m.box(n, frm, to, faces=faces, rotation=rotation)
        for d in ("north", "south", "east", "west", "up", "down"):
            if d not in faces:
                self.m.open_ok.add((n, d))
        self.groups.setdefault(group, []).append(n)
        return n


def section(p: Parts, group: str, r: float, y0: float, y1: float, cx: float, cz: float, caps, stagger=None) -> list[str]:
    names = W.section(p.m, f"{group}{len(p.groups.get(group, []))}_", round(r, 3), round(y0, 3), round(y1, 3), W.sides_for(r), cx, cz,
                      caps=caps, stagger=stagger)
    return p.add(group, names, "up" in caps, "down" in caps)


def prism(p: Parts, group: str, axis: str, r: float, a0: float, a1: float, c1: float, c2: float) -> list[str]:
    """An octagonal prism of apothem r along x (c1, c2 = its y and z) or z (c1, c2 = its x and y): four planks, two of them
    turned 45 degrees about its axis, as warheads.section lays them about y. Each plank is a little shorter than the last
    at both ends, so no two of their end caps lie in one plane."""
    s = r * math.tan(math.pi / 8)
    names = []
    k = 0
    for angle in (0.0, 45.0):
        for long_first in (True, False):
            h1, h2 = (r, s) if long_first else (s, r)
            a0, a1 = a0 + (0.06 if k else 0.0), a1 - (0.06 if k else 0.0)
            k += 1
            if axis == "x":
                frm, to = (a0, c1 - h1, c2 - h2), (a1, c1 + h1, c2 + h2)
                rot = None if angle == 0 else {"origin": (a0, c1, c2), "axis": "x", "angle": angle}
                ends = ("east", "west")
                sides = ("up", "down") if long_first else ("north", "south")
            else:
                frm, to = (c1 - h1, c2 - h2, a0), (c1 + h1, c2 + h2, a1)
                rot = None if angle == 0 else {"origin": (c1, c2, a0), "axis": "z", "angle": angle}
                ends = ("north", "south")
                sides = ("east", "west") if long_first else ("up", "down")
            names.append(p.box(group, frm, to, faces=ends + sides, rotation=rot))
    return names


# --- the mine ---------------------------------------------------------------------------------------------------------------

def horn_bases(cx: float, cy: float, cz: float) -> list[tuple[float, float, float]]:
    """Where each horn meets the sphere (for the worn paint round its foot)."""
    c45 = R * math.cos(math.radians(45))
    out = [(cx, cy + R, cz), (cx, cy + c45, cz + c45), (cx, cy + c45, cz - c45), (cx + c45, cy + c45, cz), (cx - c45, cy + c45, cz)]
    rb = math.sqrt(R * R - WAIST_Y * WAIST_Y)
    for a in (45, 135, 225, 315):
        out.append((cx + rb * math.cos(math.radians(a)), cy + WAIST_Y, cz + rb * math.sin(math.radians(a))))
    return out


def mine(p: Parts, cx: float, cy: float, cz: float) -> None:
    """The horned sphere with its centre at (cx, cy, cz)."""
    section(p, "flange", FLANGE, cy - 0.5, cy + 0.5, cx, cz, ("up", "down"), "full")
    radii = [math.sqrt(max(0.3, R * R - ((a + b) / 2) ** 2)) for a, b in zip(EDGES, EDGES[1:])]
    for sign, cap in ((1, "up"), (-1, "down")):
        for i, ((a, b), r) in enumerate(zip(zip(EDGES, EDGES[1:]), radii)):
            outer = radii[i + 1] if i + 1 < len(radii) else 0.0
            caps = (cap,) if r > outer else ()
            y0, y1 = (cy + a, cy + b) if sign > 0 else (cy - b, cy - a)
            # (Their tops show as rings: each plank a little lower than the last, so no two caps share a plane.)
            stagger = "base" if caps == ("down",) and W.sides_for(r) == 16 else "full"
            section(p, "body", r, y0, y1, cx, cz, caps, stagger)
    # The horns: built standing on the top (or lying along x at the waist) and turned to their places about the centre.
    def standing(rot):
        p.box("horn", (cx - HORN, cy + R - HORN_IN, cz - HORN), (cx + HORN, cy + R + HORN_OUT, cz + HORN),
              faces=("north", "south", "east", "west", "up"), rotation=rot)
        p.box("tip", (cx - TIP, cy + R + HORN_OUT, cz - TIP), (cx + TIP, cy + R + TIP_OUT, cz + TIP),
              faces=("north", "south", "east", "west", "up"), rotation=rot)
    standing(None)
    for axis in ("x", "z"):
        for angle in (45.0, -45.0):
            standing({"origin": (cx, cy, cz), "axis": axis, "angle": angle})
    y = cy + WAIST_Y
    rb = math.sqrt(R * R - WAIST_Y * WAIST_Y)
    for sign in (1, -1):
        for angle in (45.0, -45.0):
            rot = {"origin": (cx, y, cz), "axis": "y", "angle": angle}
            if sign > 0:
                p.box("horn", (cx + rb - HORN_IN, y - HORN, cz - HORN), (cx + R + HORN_OUT, y + HORN, cz + HORN),
                      faces=("north", "south", "east", "up", "down"), rotation=rot)
                p.box("tip", (cx + R + HORN_OUT, y - TIP, cz - TIP), (cx + R + TIP_OUT, y + TIP, cz + TIP),
                      faces=("north", "south", "east", "up", "down"), rotation=rot)
            else:
                p.box("horn", (cx - R - HORN_OUT, y - HORN, cz - HORN), (cx - rb + HORN_IN, y + HORN, cz + HORN),
                      faces=("north", "south", "west", "up", "down"), rotation=rot)
                p.box("tip", (cx - R - TIP_OUT, y - TIP, cz - TIP), (cx - R - HORN_OUT, y + TIP, cz + TIP),
                      faces=("north", "south", "west", "up", "down"), rotation=rot)
    # The mooring boss under the bottom pole and the chain's shackle on it.
    section(p, "boss", 1.3, cy - R - 0.5, cy - R + 0.6, cx, cz, ("down",))
    bottom = cy - R - 0.5
    for sx in (-1, 1):
        x0, x1 = sorted((cx + sx * 0.3, cx + sx * 0.7))
        p.box("shackle", (x0, bottom - 1.2, cz - 0.3), (x1, bottom, cz + 0.3), faces=("north", "south", "east", "west"))
    p.box("shackle", (cx - 0.8, bottom - 1.6, cz - 0.35), (cx + 0.8, bottom - 1.2, cz + 0.35))


def paint_mine(k: K.Kit, t: Tex, p: Parts, cx: float, cy: float, cz: float, seed: int) -> None:
    g = p.groups
    k.paint(t, g["body"], MINE_BLACK)
    bases = np.array(horn_bases(cx, cy, cz), dtype=np.float32)

    def weather(f, img):
        X, Y, Z = f.X, f.Y, f.Z
        # Bare steel where the horns' collars have rubbed the paint off round their feet.
        d = np.min(np.sqrt((X[..., None] - bases[:, 0]) ** 2 + (Y[..., None] - bases[:, 1]) ** 2 + (Z[..., None] - bases[:, 2]) ** 2), axis=-1)
        n = M.noise(f, 1.4, seed + 5)
        worn = (d < 1.0) & (n > 0.45)
        img[worn, :3] = img[worn, :3] * 0.45 + BARE * 0.55
        # Rust weeping down from the flange in runs, here and there round it.
        az = np.arctan2(X - cx, Z - cz)
        run = np.sin(az * 11 + 1.3) * np.sin(az * 5 + 0.4)
        length = 2.2 + 1.8 * (0.5 + 0.5 * np.sin(az * 7 + 2.1))
        weep = (run > 0.45) & (Y < cy - 0.5) & (Y > cy - 0.5 - length)
        fade = np.clip(1 - (cy - 0.5 - Y) / np.maximum(length, 1e-3), 0, 1)[weep][..., None]
        img[weep, :3] = img[weep, :3] * (1 - 0.6 * fade) + M.RUST_MID * 0.6 * fade
        # Sea growth on its underside: a ragged edge, patchy, thicker toward the bottom.
        rag = M.noise(f, 2.5, seed + 9, 2)
        line = cy - 4.4 + 1.8 * (rag - 0.5)
        thick = M.noise(f, 1.2, seed + 13)
        grow = (Y < line) & (thick > 0.3 + 1.2 * np.clip((Y - (cy - R)) / R, 0, 1))
        M.paste(img, k.on(f, GROWTH), grow)
    k.overlay(t, g["body"], weather)
    k.paint(t, g["flange"], FLANGE_BLACK)
    k.overlay(t, g["flange"], lambda f, img: W.bolts(f, img, cy, (96, 96, 92, 255)))
    k.paint(t, g["horn"], LEAD)
    k.paint(t, g["tip"], LEAD_TIP)
    k.paint(t, g["boss"] + g["shackle"], IRON)


# --- the sinker ---------------------------------------------------------------------------------------------------------------

def sinker(p: Parts, cx: float, y0: float, cz: float) -> None:
    """The trolley with its bottom at y0, centred on (cx, cz)."""
    p.box("sinker", (cx - 5.5, y0 + 1.2, cz - 4.5), (cx + 5.5, y0 + 6.0, cz + 4.5))
    for z0 in (cz - 4.5, cz + 3.7):
        p.box("rail", (cx - 5.5, y0 + 6.0, z0), (cx + 5.5, y0 + 6.5, z0 + 0.8), faces=("north", "south", "east", "west", "up"))
    p.add("drum", prism(p, "drum", "x", DRUM_R, cx - 3.5, cx + 3.5, y0 + DRUM_Y, cz))
    for x in (cx - 3.7, cx + 3.7):
        for z0, z1 in ((cz - 5.4, cz - 4.6), (cz + 4.6, cz + 5.4)):
            p.add("wheel", prism(p, "wheel", "z", 1.4, z0, z1, x, y0 + 1.4))
        p.box("axle", (x - 0.3, y0 + 1.1, cz - 5.7), (x + 0.3, y0 + 1.7, cz + 5.7), faces=("north", "south", "east", "west", "up", "down"))
    for x in (cx - 2.4, cx + 2.4):
        for z0, z1 in ((cz - 4.8, cz - 4.5), (cz + 4.5, cz + 4.8)):
            p.box("rib", (x - 0.3, y0 + 1.2, z0), (x + 0.3, y0 + 6.0, z1), faces=("north", "south", "east", "west", "up"))


def paint_sinker(k: K.Kit, t: Tex, p: Parts) -> None:
    g = p.groups
    k.paint(t, g["sinker"] + g["rail"] + g["rib"], SINKER_IRON)
    k.paint(t, g["drum"], DRUM)
    # The chain wound round the drum: a darker turn every other texel along it.
    k.overlay(t, g["drum"], lambda f, img: M.darken(img, (np.floor(f.X) % 2) == 0, 0.7) if f.d not in ("east", "west") else None)
    k.paint(t, g["wheel"] + g["axle"], WHEEL)


# --- the depth charge ------------------------------------------------------------------------------------------------------------

def depth_charge(p: Parts, cx: float, cy: float, cz: float) -> None:
    """The drum standing on end, its middle at (cx, cy, cz): the pistol's end up."""
    r = 4.5
    # (Its caps lie under the chimes, unseen: its planks only alternate, so its texel rows line up for the band.)
    section(p, "drum", r, cy - 5.4, cy + 5.4, cx, cz, ("up", "down"), "alternate")
    for y0, y1 in ((cy - 5.8, cy - 5.4), (cy + 5.4, cy + 5.8)):
        section(p, "chime", 4.75, y0, y1, cx, cz, ("up", "down"), "full")
    for y0 in (cy - 3.4, cy + 2.6):
        section(p, "hoop", 4.95, y0, y0 + 0.8, cx, cz, ("up", "down"), "full")
    section(p, "pistol", 1.8, cy + 5.8, cy + 6.4, cx, cz, ("up",))
    section(p, "knob", 0.6, cy + 6.4, cy + 6.8, cx, cz, ("up",))
    section(p, "primer", 1.2, cy - 6.4, cy - 5.8, cx, cz, ("down",))


def paint_depth_charge(k: K.Kit, t: Tex, p: Parts, cx: float, cy: float, cz: float) -> None:
    g = p.groups
    k.paint(t, g["drum"], CHARGE_GREY)
    k.overlay(t, g["drum"], lambda f, img: W.band(k, f, img, cy, cy + 1, BAND_YELLOW))
    k.overlay(t, g["drum"], lambda f, img: W.stencil(f, img, [cy - 1.6], STENCIL, width=5, pattern="xx.x."))
    k.paint(t, g["chime"] + g["hoop"], HOOP_GREY)
    k.paint(t, g["pistol"], PISTOL)

    def dial(f, img):                                         # the depth dial: marks round its face, a pointer
        if f.d != "up":
            return
        a = (np.degrees(np.arctan2(f.Z - cz, f.X - cx)) + 360) % 360
        rr = np.hypot(f.X - cx, f.Z - cz)
        M.darken(img, (rr > 1.0) & (np.floor(a / 30) % 2 == 0), 0.55)
    k.overlay(t, g["pistol"], dial)
    k.paint(t, g["knob"] + g["primer"], PRIMER)


# --- saving --------------------------------------------------------------------------------------------------------------------

def layout(m: BlockModel) -> None:
    while True:
        try:
            m.layout()
            return
        except ValueError:
            m.tex_w *= 2
            m.tex_h = m.tex_w


def kit(p: Parts, seed: int) -> K.Kit:
    k = K.Kit(p.m, {}, seed=seed, ao_reach=1.6, turned=True)
    for n, (top, bottom) in p.round.items():
        W.round_faces(k.S, [n], top, bottom, False)
    return k


def finish(m: BlockModel, gui=(30, 225, 0), ground=0.5, fixed=0.8, hand=0.5) -> None:
    m.fit_display(gui_rotation=gui, gui_margin=1.0)
    lo, hi = m.bounds()
    c = [(lo[i] + hi[i]) / 2 for i in range(3)]
    size = max(hi[i] - lo[i] for i in range(3))
    k = min(1.0, 14 / size)

    def ctx(scale: float, base, rotation=(0, 0, 0)) -> dict:
        s = scale * k
        return {"rotation": list(rotation), "translation": [round(base[i] - (c[i] - 8) * s, 3) for i in range(3)], "scale": [round(s, 4)] * 3}
    m.display["ground"] = ctx(ground, (0, 2, 0))
    m.display["fixed"] = ctx(fixed, (0, 0, 0))
    m.display["thirdperson_righthand"] = ctx(hand, (0, 2.5, 1.0), (75, 45, 0))
    m.display["thirdperson_lefthand"] = ctx(hand, (0, 2.5, 1.0), (75, 45, 0))
    m.display["firstperson_righthand"] = ctx(hand * 0.9, (1.0, 2.5, 0), (0, 45, 0))
    m.display["firstperson_lefthand"] = ctx(hand * 0.9, (1.0, 2.5, 0), (0, 225, 0))
    m.display["head"] = ctx(0.8, (0, 6, 0))


def save(m: BlockModel, p: Parts, painter, seed: int, **display) -> None:
    finish(m, **display)
    layout(m)
    t = Tex(m.tex_w, m.tex_h)
    painter(kit(p, seed), t)
    for path in m.save(t):
        print(path)


def mine_body() -> None:
    m = BlockModel("naval/mine_body", tex_size=(128, 128), kind="item")
    m.open_ok = set()
    p = Parts(m)
    mine(p, 8, 8, 8)
    save(m, p, lambda k, t: paint_mine(k, t, p, 8, 8, 8, 31), 31)


def mine_sinker() -> None:
    m = BlockModel("naval/mine_sinker", tex_size=(64, 64), kind="item")
    m.open_ok = set()
    p = Parts(m)
    sinker(p, 8, 0, 8)
    save(m, p, lambda k, t: paint_sinker(k, t, p), 37)


def moored_mine() -> None:
    m = BlockModel("moored_mine", tex_size=(128, 128), kind="item")
    m.open_ok = set()
    p = Parts(m)
    sinker(p, 8, 0, 8)
    cy = 18.0                                         # its shackle on the drum (whole pixels: the bands' caps stay apart)
    mine(p, 8, cy, 8)

    def painter(k, t):
        paint_sinker(k, t, p)
        paint_mine(k, t, p, 8, cy, 8, 31)
    save(m, p, painter, 41)
    model = lambda name: {"type": "minecraft:model", "model": f"groundburst:item/{name}"}
    write_json(paths.ASSETS / "items" / "moored_mine.json",
               {"model": {"type": "minecraft:select", "property": "minecraft:custom_model_data",
                          "cases": [{"when": "body", "model": model("naval/mine_body")}, {"when": "sinker", "model": model("naval/mine_sinker")}],
                          "fallback": model("moored_mine")}})
    write_json(paths.ASSETS / "items" / "drifting_mine.json", {"model": model("naval/mine_body")})


def charge() -> None:
    m = BlockModel("depth_charge", tex_size=(64, 64), kind="item")
    m.open_ok = set()
    p = Parts(m)
    depth_charge(p, 8, 8, 8)
    save(m, p, lambda k, t: paint_depth_charge(k, t, p, 8, 8, 8), 43, gui=(30, 225, 0))
    write_json(paths.ASSETS / "items" / "depth_charge.json", {"model": {"type": "minecraft:model", "model": "groundburst:item/depth_charge"}})


def chain_texture() -> None:
    """Two columns of links, three texels wide: in each, a link face-on (a ring round a slot) where the other column's is
    edge-on (a bar down its middle), a link every 8 texels, so the crossed planes show a chain of alternating links."""
    t = Tex(16, 16)
    rng = np.random.default_rng(7)
    shade = {"light": (138, 104, 78), "mid": (104, 76, 58), "dark": (66, 48, 38), "deep": (42, 31, 26)}
    rust = (152, 74, 34)

    def put(x, y, tone):
        c = shade[tone]
        if rng.random() < 0.16:
            c = tuple(int(c[i] * 0.5 + rust[i] * 0.5) for i in range(3))
        t.px(x, y % 16, (*c, 255))

    def face_on(x0, y0):
        put(x0 + 1, y0, "light")
        for dy in (1, 2, 3):
            put(x0, y0 + dy, "light" if dy == 1 else "mid")
            put(x0 + 2, y0 + dy, "dark" if dy < 3 else "deep")
        put(x0 + 1, y0 + 4, "dark")

    def edge_on(x0, y0):
        for dy, tone in enumerate(("mid", "light", "light", "mid", "dark")):
            put(x0 + 1, y0 + dy, tone)
    for y in (0, 8):
        face_on(0, y)
        edge_on(0, y + 4)
        edge_on(3, y)
        face_on(3, y + 4)
    out = paths.TEXTURES / "entity" / "naval" / "mine_chain.png"
    print(t.save(out))


def main() -> None:
    mine_body()
    mine_sinker()
    moored_mine()
    charge()
    chain_texture()
    print("naval written")


if __name__ == "__main__":
    main()
