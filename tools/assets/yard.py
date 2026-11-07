"""The outdoor defences and the yard (DESIGN.md §22.6), the fourth set of the cosmetic blocks: block models painted with
the material library.

- **Jersey Barrier**: a concrete road barrier a block long, the New Jersey profile (a short upright foot, a shallow
  slope, a steep face to a narrow top) both sides; its slopes are turned plates over a stepped core, each part's end a
  hair inside the last's so a barrier's end shows the profile whole and nothing flickers.
- **Dragon's Teeth**: a concrete tooth, a truncated pyramid. A box cannot taper, so each sloped face is four plates in
  its plane: two turned in that plane as well (26.x's turns about all three axes) so each runs exactly along one corner
  edge, a band across its top and a block across its foot between them; each a hundredth of a pixel behind the one in
  front of it, so the face is whole and flat, its corners true and nothing flickers.
- **Czech Hedgehog** (painted dark grey) and **Rusty Czech Hedgehog**: three steel angle irons crossed at their
  middles, each square to the others, standing on three of their ends (the three turned about all three axes), each
  beam's corner a hair further out than the last's so no two lie in one plane where they cross.
- **Drums** (Oil, Fuel, Rusty): one to four steel drums on end, 16-sided prisms, the rolled chime round each top a ring
  proud of it, the lid sunk inside it, the hoops painted on, two bungs.
- **Ammo Crates**: eight olive ammo boxes in three crossed layers on a wooden pallet; open, the top front box's lid up
  on its hinges (painted with the shut model's texels: share_shut), its cartridges showing.

    tools\\.venv\\Scripts\\python.exe tools\\assets\\yard.py [barrier|teeth|hedgehog|drums|crates]
"""
from __future__ import annotations

import dataclasses
import json
import math
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))

from gb import machine_kit as K  # noqa: E402
from gb import materials as M  # noqa: E402
from gb import paths  # noqa: E402
from gb.model import BlockModel, write_json  # noqa: E402
from gb.tex import Tex  # noqa: E402
import command_room as CR  # noqa: E402
import warheads as W  # noqa: E402

ALL = ("north", "south", "east", "west", "up", "down")
SIDES = ("north", "south", "east", "west")
#: A model built facing north, turned to face each way.
TURN = {"north": {}, "east": {"y": 90}, "south": {"y": 180}, "west": {"y": 270}}


# --- turns ---------------------------------------------------------------------------------------------------------------------
def rx(a: float) -> np.ndarray:
    c, s = math.cos(math.radians(a)), math.sin(math.radians(a))
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


def ry(a: float) -> np.ndarray:
    c, s = math.cos(math.radians(a)), math.sin(math.radians(a))
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


def rz(a: float) -> np.ndarray:
    c, s = math.cos(math.radians(a)), math.sin(math.radians(a))
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def rotation(R: np.ndarray, origin) -> dict | None:
    """An element's turn for the matrix R about {origin}: one axis and an angle where R is a turn about one axis (what
    every version reads), else 26.x's x, y, z (Rz * Ry * Rx, as JOML's rotationZYX)."""
    o = tuple(round(float(v), 5) for v in origin)
    b = -math.degrees(math.asin(max(-1.0, min(1.0, R[2, 0]))))
    a = math.degrees(math.atan2(R[2, 1], R[2, 2]))
    c = math.degrees(math.atan2(R[1, 0], R[0, 0]))
    angles = {"x": a, "y": b, "z": c}
    turned = {k: v for k, v in angles.items() if abs(v) > 1e-4}
    if not turned:
        return None
    if len(turned) == 1:
        (axis, angle), = turned.items()
        return {"origin": o, "axis": axis, "angle": round(angle, 4)}
    return {"origin": o, **{k: round(v, 4) for k, v in angles.items()}}


def about_y(R: np.ndarray, frm, to, origin, quarter: int, centre=(8.0, 8.0)):
    """A turned box carried a quarter turn round the block's middle {quarter} times: its matrix, box and origin."""
    T = ry(-90.0 * quarter)                                   # (a quarter turn clockwise seen from above, north to east)
    o = np.array(origin, dtype=float)
    c = np.array([centre[0], 0.0, centre[1]])
    o2 = T @ (o - c) + c
    shift = o2 - o
    return T @ R, tuple(np.array(frm) + shift), tuple(np.array(to) + shift), tuple(o2)


# --- the Jersey Barrier --------------------------------------------------------------------------------------------------------
#: The profile (y up, z across, the front at small z; the back its mirror about z 8): the foot's top, the crease where
#: the shallow slope meets the steep face, the top's edge.
FOOT = (2.0, 1.2)
CREASE = (4.6, 4.8)
TOP = (6.2, 13.6)


def plate_along(p0, p1, thick: float):
    """A plate whose outer face runs up the profile from p0 to p1 (z, y), {thick} inward: (its length, its turn about
    x in degrees, positive tipping its top toward +z)."""
    dz, dy = p1[0] - p0[0], p1[1] - p0[1]
    return math.hypot(dz, dy), math.degrees(math.atan2(dz, dy))


def barrier() -> None:
    m = BlockModel("jersey_barrier", tex_size=(128, 64), kind="block")
    m.open_ok = set()
    parts: dict[str, list[str]] = {}

    def box(group, n, frm, to, faces, rotation=None):
        m.box(n, frm, to, faces=faces, rotation=rotation)
        parts.setdefault(group, []).append(n)

    # Each part's ends a hair inside the last's: the barrier's end shows its profile whole, no two ends in one plane.
    box("foot", "foot", (0.0, 0.0, FOOT[0]), (16.0, FOOT[1], 16.0 - FOOT[0]), ("north", "south", "east", "west"))
    box("core", "core_low", (0.03, FOOT[1], CREASE[0]), (15.97, CREASE[1], 16.0 - CREASE[0]), ("east", "west"))
    box("core", "core_high", (0.06, CREASE[1], TOP[0]), (15.94, TOP[1], 16.0 - TOP[0]), ("east", "west", "up"))
    for side, mirror in (("front", False), ("back", True)):
        for i, (p0, p1, thick, inset) in enumerate(((FOOT, CREASE, 2.0, 0.09), (CREASE, TOP, 2.0, 0.12))):
            length, angle = plate_along(p0, p1, thick)
            z0 = p0[0] if not mirror else 16.0 - p0[0] - thick
            frm, to = (inset, p0[1], z0), (16.0 - inset, p0[1] + length, z0 + thick)
            origin = (8.0, p0[1], p0[0] if not mirror else 16.0 - p0[0])
            box("slope", f"{side}_{'low' if i == 0 else 'high'}", frm, to,
                ("south" if mirror else "north", "east", "west"), rotation={"origin": origin, "axis": "x", "angle": -angle if mirror else angle})
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    k = K.Kit(m, {}, seed=6101, ao_reach=1.4, turned=True)
    k.paint(t, parts["foot"] + parts["core"] + parts["slope"], M.concrete(tone=0.6))
    k.overlay(t, parts["foot"], scupper)
    m.fit_display()
    W.match_walls(m, t, [b.name for b in m.boxes])
    m.save(t)
    CR.blockstate("jersey_barrier")
    CR.block_item("jersey_barrier")


def scupper(f: M.Face, img: np.ndarray) -> None:
    """The drain slot through the foot under the slopes, a dark slot at its middle."""
    if f.d not in ("north", "south"):
        return
    rgb = img[..., :3].astype(np.float32)
    rgb[(np.abs(f.X - 8.0) < 2.5)] *= 0.25
    img[..., :3] = np.clip(rgb, 0, 255).astype(np.uint8)


# --- the Dragon's Teeth --------------------------------------------------------------------------------------------------------
#: The tooth: its foot's half-width, its top's, its height (block pixels about the block's middle).
TOOTH_FOOT, TOOTH_TOP, TOOTH_H = 7.0, 3.0, 13.0


def teeth() -> None:
    m = BlockModel("dragons_teeth", tex_size=(128, 128), kind="block")
    m.open_ok = set()
    parts: dict[str, list[str]] = {}
    b, a, H = TOOTH_FOOT, TOOTH_TOP, TOOTH_H
    run = b - a                                        # how far a face leans in over its height
    tilt = math.degrees(math.atan2(run, H))            # each face's lean from upright
    V = math.hypot(H, run)                             # a face's height up its slope
    beta = math.degrees(math.atan2(run, V))            # a corner edge's lean in the face's plane
    edge = math.hypot(run, V)                          # a corner edge's length
    thick = 1.6
    cb = math.cos(math.radians(beta))
    w_side = (2.0 * a) * cb                            # the corner plates as wide as reaches the far top corner, no more
    # Built on the north face (its foot along z = 8 - b, leaning toward +z), then carried round to the others. Each plate
    # stands upright in the face's plane before its turns: x across, y up the slope, z into the tooth from z0.
    z0 = 8.0 - b
    plates = []
    # The band across the face's top and the block across its foot: turned only to lean (in front: none behind them).
    plates.append(("top", (8.0 - a, V - 1.6, z0), (8.0 + a, V, z0 + thick), (8.0, 0.0, z0), rx(tilt)))
    plates.append(("foot", (6.0, 0.0, z0), (10.0, 4.2, z0 + thick), (8.0, 0.0, z0), rx(tilt)))
    # The corner plates: turned in the face's plane (about z through the foot's corner) so their outer edge runs up the
    # corner, then leant with the face: a hundredth of a pixel and two behind the plates in front of them.
    for side, sx, back in (("left", -1, 0.02), ("right", 1, 0.04)):
        x_corner = 8.0 + sx * b
        if sx < 0:
            frm, to = (x_corner, 0.0, z0 + back), (x_corner + w_side, edge, z0 + back + thick)
        else:
            frm, to = (x_corner - w_side, 0.0, z0 + back), (x_corner, edge, z0 + back + thick)
        R = rx(tilt) @ rz(sx * beta)
        plates.append((side, frm, to, (x_corner, 0.0, z0 + back), R))
    for q, face in enumerate(("north", "east", "south", "west")):
        for name, frm, to, origin, R in plates:
            R2, frm2, to2, o2 = about_y(R, frm, to, origin, q)
            n = f"{face}_{name}"
            # (its outer face is its own north face, whichever way it is carried: every other lies inside the tooth)
            m.box(n, frm2, to2, faces=("north",), rotation=rotation(R2, o2))
            parts.setdefault("face", []).append(n)
    m.box("cap", (8.0 - a, H - 1.0, 8.0 - a), (8.0 + a, H, 8.0 + a), faces=("up",))
    parts["cap"] = ["cap"]
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    k = K.Kit(m, {}, seed=6201, ao_reach=1.4, turned=True)
    # The faces are painted as one surface (each plate's borders lie across the face, not at its edges): the material's
    # own edges, grime and streaks off, put back by where each texel stands on the tooth (tooth_weather).
    k.paint(t, parts["face"], M.concrete(tone=0.62, edge_light=0.0, edge_dark=0.0, grime=0.0, streaks=0.0, falloff=0.0, ao=0.0,
                                         dither=0.35, extra=tooth_weather))
    k.paint(t, parts["cap"], M.concrete(tone=0.62, dither=0.35))
    m.fit_display()
    W.match_walls(m, t, [b.name for b in m.boxes])
    m.save(t)
    write_json(paths.ASSETS / "blockstates" / "dragons_teeth.json", {"variants": {"": {"model": "groundburst:block/dragons_teeth"}}})
    CR.block_item("dragons_teeth")


def tooth_weather(f: M.Face) -> np.ndarray:
    """A tooth face's light and dirt by where it stands: its corners and its top's edge catching the light, grime at its
    foot, the sky's light down it, rain streaks hanging from its top."""
    if f.d in ("up", "down"):
        return np.zeros(f.shape, np.float32)
    nx, _, nz = f.normal
    across = (f.X - 8.0) if abs(nz) > abs(nx) else (f.Z - 8.0)
    y = np.clip(f.Y, 0.0, TOOTH_H)
    half = TOOTH_FOOT - (TOOTH_FOOT - TOOTH_TOP) * y / TOOTH_H
    to_corner = half - np.abs(across)
    v = np.zeros(f.shape, np.float32)
    v += 0.10 * np.clip(1 - to_corner / 0.9, 0, 1)
    v += 0.08 * np.clip(1 - (TOOTH_H - y) / 0.9, 0, 1)
    v -= 0.15 * np.clip(1 - y / 4.0, 0, 1) ** 1.5
    v += 0.05 * (y / TOOTH_H - 0.5)
    streak = M.value_noise(across * 1.0, np.zeros_like(y), np.full_like(y, nx * 3 + nz * 7), (1.6, 1.0, 1.0), 6211)
    v -= 0.07 * np.clip((streak - 0.55) / 0.3, 0, 1) * np.clip(y / TOOTH_H, 0, 1)
    return v.astype(np.float32)


# --- the Czech Hedgehog --------------------------------------------------------------------------------------------------------
#: Each beam's length, its angle iron's flanges (width, thickness): a hedgehog a block and a bit high.
BEAM, FLANGE, FLANGE_T = 28.0, 2.4, 0.6
#: How far each beam's corner stands out along its own flanges from the last's: the three cross at the middle, and
#: no face of one lies in the plane of another's (where they met in one, the middle flickered).
BEAM_STAGGER = 0.12
#: Steel left out in the weather: rust brown, the old grey paint in patches (painted in rust, worn to grey).
HEDGEHOG_RUST = M.ramp("#1f110a", "#2f190e", "#432414", "#583019", "#6e3d1f", "#844b26", "#9a5b30", "#b06f3e")
RUSTED = M.painted(HEDGEHOG_RUST, tone=0.5, wear=0.35, rust=0.3, under=M.ARMOUR_RAMP[2:7], mottle=0.09)
#: The same, new: dark grey paint, a little chipped at its edges to the steel.
PAINTED = M.painted(M.ARMOUR_RAMP, tone=0.5, wear=0.16, under=M.STEEL_RAMP[3:8])
#: The two hedgehogs: block id, paint.
HEDGEHOGS = {"czech_hedgehog": PAINTED, "rusty_czech_hedgehog": RUSTED}


def turn_between(a: np.ndarray, b: np.ndarray) -> np.ndarray:
    """The turn taking direction a onto direction b (Rodrigues)."""
    a, b = a / np.linalg.norm(a), b / np.linalg.norm(b)
    v = np.cross(a, b)
    s, c = np.linalg.norm(v), float(a @ b)
    if s < 1e-9:
        return np.eye(3)
    vx = np.array([[0, -v[2], v[1]], [v[2], 0, -v[0]], [-v[1], v[0], 0]])
    return np.eye(3) + vx + vx @ vx * ((1 - c) / s ** 2)


def hedgehog(name: str, paint: M.Material) -> None:
    """Three angle irons, each square to the others and the three's sum straight up, so the hedgehog stands on three of
    their ends: each beam's own x turned onto its line, its flanges onto the other two's (so all three lie alike), each
    beam's corner BEAM_STAGGER further out along its flanges than the last's."""
    m = BlockModel(name, tex_size=(128, 64), kind="block")
    m.open_ok = set()
    R0 = turn_between(np.array([1.0, 1.0, 1.0]), np.array([0.0, 1.0, 0.0]))
    R_yaw = ry(15.0)
    cycle = np.array([[0, 0, 1], [1, 0, 0], [0, 1, 0]], dtype=float)        # x to y, y to z, z to x
    beams = []
    for i in range(3):
        R = R_yaw @ R0 @ np.linalg.matrix_power(cycle, i)
        o = BEAM_STAGGER * i
        web = (np.array((-BEAM / 2, FLANGE_T + o, o)), np.array((BEAM / 2, FLANGE + o, FLANGE_T + o)))
        leg = (np.array((-BEAM / 2, o, o)), np.array((BEAM / 2, FLANGE_T + o, FLANGE + o)))
        beams.append((R, (("web", web), ("leg", leg))))
    # Stood with its lowest corner a hair above the ground.
    low = min(float((R @ np.array([x, y, z]))[1]) for R, pieces in beams for _, (a, b) in pieces
              for x in (a[0], b[0]) for y in (a[1], b[1]) for z in (a[2], b[2]))
    c = np.array([8.0, 0.02 - low, 8.0])
    names = []
    for i, (R, pieces) in enumerate(beams):
        for part, (a, b) in pieces:
            n = f"beam{i}_{part}"
            m.box(n, tuple(c + a), tuple(c + b), faces=ALL, rotation=rotation(R, c))
            names.append(n)
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    k = K.Kit(m, {}, seed=6301, ao_reach=1.2, turned=True)
    k.paint(t, names, paint)
    m.fit_display()
    W.match_walls(m, t, [b.name for b in m.boxes])
    m.save(t)
    CR.blockstate(name)
    CR.block_item(name)


# --- the drums -----------------------------------------------------------------------------------------------------------------
#: A drum: its radius (to its facets' middles) and height; where each stands (block pixels, facing north) for one to four.
DRUM_R, DRUM_H = 3.85, 12.2
DRUM_SPOTS = {1: [(8.0, 8.0)], 2: [(4.1, 8.0), (11.9, 8.0)], 3: [(4.1, 4.2), (11.9, 4.2), (8.0, 11.8)],
              4: [(4.1, 4.1), (11.9, 4.1), (4.1, 11.9), (11.9, 11.9)]}
RUST_RAMP = M.ramp("#24120a", "#3a1c0e", "#522713", "#6b3419", "#844321", "#9c552c", "#b46a3b", "#c98452")
DRUMS = {
    "oil_drum": M.painted(M.OLIVE_RAMP, tone=0.5, wear=0.3, rust=0.12, under=M.STEEL_RAMP[2:7]),
    "fuel_drum": M.painted(M.RED_PAINT_RAMP, tone=0.5, wear=0.28, rust=0.1, under=M.STEEL_RAMP[2:7]),
    "rusty_drum": M.painted(RUST_RAMP, tone=0.5, wear=0.0, rust=0.5),
}


#: The chime round a drum's top (its inside, its outside proud of the drum, its foot, its top) and the lid sunk inside it.
#: Its outside 0.04 proud (neighbours stand 7.8 apart: chimes 0.1 proud overlapped in their tops' plane and flickered),
#: standing on the drum's sides' top (reaching down over them, its sides would lie a hair off theirs).
CHIME = (DRUM_R - 0.6, DRUM_R + 0.04, DRUM_H - 0.2, DRUM_H + 0.2)
LID_R, LID_TOP = DRUM_R - 0.5, DRUM_H - 0.3


def drums(name: str) -> None:
    """One to four steel drums on end, each a 16-sided prism (its bottom closed, no top of its own), the rolled chime
    round its top a ring standing proud of it, the lid sunk inside the chime (its planks' levels hidden under it), the
    rolling hoops painted on, two bungs in its lid."""
    paint = DRUMS[name]
    for count, spots in DRUM_SPOTS.items():
        m = BlockModel(f"{name}_{count}", tex_size=(128, 128), kind="block")
        m.open_ok = set()
        bodies, lids, chimes, bungs = [], [], [], []
        for j, (cx, cz) in enumerate(spots):
            # (Its bottom closed, its planks' levels stepping down into what it stands on.)
            bodies += W.round_section(m, f"drum{j}", DRUM_R, 0.0, DRUM_H - 0.2, c=(cx, cz), sides=16, lo=0.0)
            lids += W.round_section(m, f"lid{j}", LID_R, LID_TOP - 0.6, LID_TOP, c=(cx, cz), sides=16, hi=0.0)
            chimes += W.round_ring(m, f"chime{j}", CHIME[0], CHIME[1], CHIME[2], CHIME[3], c=(cx, cz), n=16)
            a = math.radians(40 + 97 * j)
            for b_, (rr, size) in enumerate(((2.0, 1.0), (2.3, 0.7))):
                bx, bz = cx + rr * math.cos(a + b_ * 2.6), cz + rr * math.sin(a + b_ * 2.6)
                n = f"bung{j}_{b_}"
                m.box(n, (bx - size / 2, LID_TOP - 0.7, bz - size / 2), (bx + size / 2, LID_TOP + 0.3, bz + size / 2),
                      faces=("north", "south", "east", "west", "up"))
                bungs.append(n)
        m.layout()
        t = Tex(m.tex_w, m.tex_h)
        k = K.Kit(m, {}, seed=6400 + count, ao_reach=1.2, turned=True)
        W.round_faces(k.S, bodies + lids)
        k.paint(t, bodies + chimes, paint)
        # The lid flat (no dithering: its planks lie each its own way, and their dither would draw their edges on it).
        k.paint(t, lids, dataclasses.replace(paint, dither=0.0, grain=0.0))
        k.S.paint(t, bodies, dataclasses.replace(paint, dither=0.0, grain=0.0), only=("down",))
        k.paint(t, bungs, K.STEEL)
        k.overlay(t, bodies + lids, lambda f, img: drum_marks(f, img, spots))
        m.fit_display()
        W.match_walls(m, t, [b.name for b in m.boxes])
        m.save(t)
    v = {}
    for facing, rot in TURN.items():
        for count in DRUM_SPOTS:
            v[f"drums={count},facing={facing}"] = {"model": f"groundburst:block/{name}_{count}", **rot}
    write_json(paths.ASSETS / "blockstates" / f"{name}.json", {"variants": v})
    write_json(paths.ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": f"groundburst:block/{name}_1"}})


def drum_marks(f: M.Face, img: np.ndarray, spots) -> None:
    """A drum's two rolling hoops (a dark band with a lit top); its lid a shade darker at its edge, in the chime's shadow."""
    rgb = img[..., :3].astype(np.float32)
    if f.d == "up":
        r = np.min([np.hypot(f.X - cx, f.Z - cz) for cx, cz in spots], axis=0)
        rgb[r > LID_R - 0.7] *= 0.78
    elif f.d in SIDES:
        for y in (DRUM_H * 0.34, DRUM_H * 0.66):
            rgb[np.abs(f.Y - y) < 0.5] *= 0.74
            rgb[np.abs(f.Y - (y + 0.75)) < 0.25] *= 1.16
    img[..., :3] = np.clip(rgb, 0, 255).astype(np.uint8)


# --- the Ammo Crates -----------------------------------------------------------------------------------------------------------
CRATE_PAINT = M.painted(M.OLIVE_RAMP, tone=0.47, wear=0.3, under=M.WOOD_RAMP[2:7], mottle=0.07)
PALLET_WOOD = M.wood(tone=0.6)
#: The crates (x0, z0, x1, z1, y0, y1): three along the pallet, three across them, two along on top; the last of the
#: top two (the front one) has a lid of its own, which stands open while the crates are.
CRATES = [(1.0, 1.0, 15.0, 5.4, 2.3, 5.7), (1.0, 5.8, 15.0, 10.2, 2.3, 5.7), (1.0, 10.6, 15.0, 15.0, 2.3, 5.7),
          (1.0, 1.0, 5.4, 15.0, 5.7, 9.1), (5.8, 1.0, 10.2, 15.0, 5.7, 9.1), (10.6, 1.0, 15.0, 15.0, 5.7, 9.1),
          (1.5, 8.7, 14.5, 13.1, 9.1, 12.5), (1.5, 3.4, 14.5, 7.8, 9.1, 12.5)]
LID = 0.6


def crates(open_: bool) -> None:
    """A wooden pallet with eight olive ammo boxes strapped on it, three layers crossed; open, the front box on top has
    its lid up on its hinges, rows of cartridges inside."""
    name = "ammo_crates_open" if open_ else "ammo_crates"
    m = BlockModel(name, tex_size=(128, 128), kind="block")
    m.open_ok = set()
    parts: dict[str, list[str]] = {}

    def box(group, n, frm, to, faces=ALL, rotation=None):
        m.box(n, frm, to, faces=faces, rotation=rotation)
        parts.setdefault(group, []).append(n)

    for i, x in enumerate((0.0, 6.7, 13.4)):
        box("pallet", f"runner{i}", (x, 0.0, 0.0), (x + 2.6, 0.5, 16.0), faces=("north", "south", "east", "west", "up"))
    for i, z in enumerate((0.4, 7.1, 13.8)):
        box("pallet", f"stringer{i}", (0.0, 0.5, z), (16.0, 1.7, z + 1.8), faces=("north", "south", "east", "west"))
    for i, x in enumerate((0.0, 3.3, 6.6, 9.9, 13.2)):
        box("pallet", f"deck{i}", (x, 1.7, 0.0), (x + 2.8, 2.3, 16.0), faces=("north", "south", "east", "west", "up"))
    for i, (x0, z0, x1, z1, y0, y1) in enumerate(CRATES):
        if i == len(CRATES) - 1:
            box("crate", f"crate{i}", (x0, y0, z0), (x1, y1 - LID, z1), faces=("north", "south", "east", "west", "up"))
            hinge = {"origin": (8.0, y1 - LID, z1), "axis": "x", "angle": 105.0} if open_ else None
            box("lid", "lid", (x0, y1 - LID, z0), (x1, y1, z1), faces=ALL if open_ else ("north", "south", "east", "west", "up"),
                rotation=hinge)
        else:
            box("crate", f"crate{i}", (x0, y0, z0), (x1, y1, z1), faces=("north", "south", "east", "west", "up"))
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    k = K.Kit(m, {}, seed=6501, ao_reach=1.4, turned=True)
    k.paint(t, parts["pallet"], PALLET_WOOD)
    k.paint(t, parts["crate"] + parts["lid"], CRATE_PAINT)
    k.overlay(t, parts["crate"] + parts["lid"], crate_marks)
    m.fit_display()
    W.match_walls(m, t, [b.name for b in m.boxes])
    m.save(t)


def crate_marks(f: M.Face, img: np.ndarray) -> None:
    """An ammo box's marks: its lid's seam, the cleats at its ends, a rope handle on each end, a yellow band and a line of
    stencilling along its sides; the open box's cartridges, brass in rows, inside it."""
    rgb = img[..., :3].astype(np.float32)
    box = f.box
    (x0, y0, z0), (x1, y1, z1) = box.frm, box.to
    long_x = (x1 - x0) > (z1 - z0)
    if box.name == "crate7" and f.d == "up":
        inner = (f.X > x0 + 0.6) & (f.X < x1 - 0.6) & (f.Z > z0 + 0.6) & (f.Z < z1 - 0.6)
        rgb[inner] = (34, 30, 22)
        rows = inner & (np.floor(f.X - x0) % 2 == 0) & (np.floor((f.Z - z0) * 1.0) % 2 == 1)
        rgb[rows] = (196, 152, 70)
        img[..., :3] = np.clip(rgb, 0, 255).astype(np.uint8)
        return
    if f.d in ("up", "down"):
        img[..., :3] = np.clip(rgb, 0, 255).astype(np.uint8)
        return
    side = (f.d in ("north", "south")) == long_x            # a long side (else an end)
    if box.name != "lid":
        top = y1 if box.name != "crate7" else y1 + LID
        rgb[np.abs(f.Y - (top - LID - 0.1)) < 0.25] *= 0.62     # the lid's seam
    if side:
        along = f.X if long_x else f.Z
        lo, hi = (x0, x1) if long_x else (z0, z1)
        rgb[(along < lo + 1.0) | (along > hi - 1.0)] *= 0.8      # the cleats
        mid = (lo + hi) / 2
        if box.name != "lid":
            band = np.abs(f.Y - (y0 + 1.2)) < 0.5
            rgb[band & (np.abs(along - mid) < 3.5)] = (196, 166, 52)
            dash = band & (np.abs(along - mid) < 3.0) & ((np.floor(along * 1.7) % 3) != 0) & (np.abs(f.Y - (y0 + 1.2)) < 0.3)
            rgb[dash] = (40, 38, 30)
    elif box.name != "lid":
        across = f.Z if long_x else f.X
        lo, hi = (z0, z1) if long_x else (x0, x1)
        mid = (lo + hi) / 2
        rope = (np.abs(across - mid) < 1.4) & (np.abs(f.Y - (y0 + 1.6)) < 0.5) & ~((np.abs(across - mid) < 0.6) & (f.Y < y0 + 1.6))
        rgb[rope] = (176, 158, 112)
    img[..., :3] = np.clip(rgb, 0, 255).astype(np.uint8)


def share_shut(name: str, moving: tuple[str, ...]) -> None:
    """The open model's moving parts painted as the shut model's (its texels, its texture): painted where they swing to,
    their noise and bands would fall another way, and they would change as they moved (the Steel Locker's way). A face
    the shut model has none of (a lid's underside, against the box while shut) keeps the open model's own paint."""
    closed = json.loads((paths.MODELS / "block" / f"{name}.json").read_text(encoding="utf-8"))
    faces = {el["name"]: el["faces"] for el in closed["elements"]}
    path = paths.MODELS / "block" / f"{name}_open.json"
    model = json.loads(path.read_text(encoding="utf-8"))
    model["textures"]["shut"] = f"groundburst:block/{name}"
    for el in model["elements"]:
        if el["name"] in moving:
            shut = faces[el["name"]]
            el["faces"] = {d: ({**shut[d], "texture": "#shut"} if d in shut else f) for d, f in el["faces"].items()}
    write_json(path, model)


def crate_files() -> None:
    crates(False)
    crates(True)
    share_shut("ammo_crates", ("lid",))
    v = {}
    for facing, rot in TURN.items():
        for open_ in (False, True):
            v[f"facing={facing},open={'true' if open_ else 'false'}"] = {
                "model": f"groundburst:block/ammo_crates{'_open' if open_ else ''}", **rot}
    write_json(paths.ASSETS / "blockstates" / "ammo_crates.json", {"variants": v})
    CR.block_item("ammo_crates")


def main() -> None:
    which = sys.argv[1:] or ["barrier", "teeth", "hedgehog", "drums", "crates"]
    if "crates" in which:
        crate_files()
    if "hedgehog" in which:
        for name, paint in HEDGEHOGS.items():
            hedgehog(name, paint)
    if "drums" in which:
        for name in DRUMS:
            drums(name)
    if "barrier" in which:
        barrier()
    if "teeth" in which:
        teeth()


if __name__ == "__main__":
    main()
