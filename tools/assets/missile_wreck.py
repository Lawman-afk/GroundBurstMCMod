"""The Missile Wreck (DESIGN.md §22.2): what is left of a two-stage missile that came down in the countryside, for the
Crashed Missile site and for building with. The test ranges' white with the black roll pattern, broken in three where
it struck and ploughed on: the tail with its engine and fins thrown aside onto the land (the block's own place, propped
on two fins), the body lying in its furrow torn at both ends and buckled, the nose section driven into the crater,
its dark re-entry cone buried; a fin, the interstage ring, a curl of skin and a cable bundle strewn between.

Curves are the model's (DESIGN.md §4.4, "curves as models"): the skin is a true sixteen-sided tube, one plank a facet
and a pixel thick, so a torn end shows its wall, its ragged edge (each facet torn off at its own length) and the dark
inside with the tank dome behind; the cone rises ring by ring from a real profile (warheads.nose).

Frame (facing north, as every model of the mod): the block's bottom centre at the origin, the land's surface at y 0;
the furrow runs north along x -30 (half a block clear of the block's own column), deepening to the crater at its north end. Each piece is built standing (its aft end
at its anchor, its axis up) and placed by three bones: rolled about its own axis, laid down (-90 about x points its
nose north, more points it down) and turned (a positive yaw turns its nose west).

    tools/.venv/Scripts/python.exe tools/assets/missile_wreck.py
"""
from __future__ import annotations

import math
import random
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))

import numpy as np  # noqa: E402
from PIL import Image  # noqa: E402

import warheads as W  # noqa: E402
from gb import materials as M  # noqa: E402
from gb import paths  # noqa: E402
from gb.geo import Animation, GeoModel, save_animations  # noqa: E402
from gb.model import write_json  # noqa: E402
from gb.tex import Tex  # noqa: E402

NAME = "missile_wreck"
R = 8.0                     # the body's radius: a block across
WALL = 1.0                  # its skin, seen at a torn end
SIDES = 16
GRADE = 0.0                 # the land's surface


def _rx(a: float) -> np.ndarray:
    c, s = math.cos(math.radians(a)), math.sin(math.radians(a))
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


def _ry(a: float) -> np.ndarray:
    c, s = math.cos(math.radians(a)), math.sin(math.radians(a))
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


def _rz(a: float) -> np.ndarray:
    c, s = math.cos(math.radians(a)), math.sin(math.radians(a))
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


class OnBone:
    """A model as warheads.section and .nose want it (boxes without a bone), putting every box on one bone."""

    def __init__(self, g: GeoModel, bone: str):
        self.g, self.bone = g, bone

    @property
    def open_ok(self):
        return self.g.open_ok

    def box(self, name, frm, to, faces=("north", "south", "east", "west", "up", "down"), rotation=None, **kw):
        return self.g.box(name, frm, to, faces=faces, rotation=rotation, bone=self.bone)


class Wreck:
    def __init__(self) -> None:
        self.g = GeoModel(NAME, tex_size=(512, 512))
        if not hasattr(self.g, "open_ok"):
            self.g.open_ok = set()
        self.g.bone("wreck")
        self.bones: dict[str, tuple[tuple, tuple, str | None]] = {"wreck": ((0, 0, 0), (0, 0, 0), None)}
        self.axis: dict[str, tuple[float, float]] = {}            # each piece's axis (x, z) as built
        self.group: dict[str, list[str]] = {k: [] for k in ("out", "in", "edge", "dome", "fin", "nozzle", "nozzle_in", "cone",
                                                            "kit", "cable", "bulk")}
        self.of: dict[str, str] = {}                                # box -> its piece (for the paint's per-piece work)
        self.torn: dict[str, list[float]] = {}                      # piece -> the heights (as built) of its torn ends
        self.n = 0

    # --- bones -------------------------------------------------------------------------------------------------------------
    def _bone(self, name: str, pivot, rotation, parent: str) -> None:
        self.g.bone(name, pivot=pivot, parent=parent, rotation=rotation)
        self.bones[name] = (tuple(map(float, pivot)), tuple(map(float, rotation)), parent)

    def piece(self, name: str, at, yaw: float, lay: float, roll: float, parent: str = "wreck") -> str:
        """Three bones placing a piece built standing with its aft end at {at}: rolled about its own axis, laid down,
        turned."""
        self._bone(f"{name}_yaw", at, (0, yaw, 0), parent)
        self._bone(f"{name}_lay", at, (lay, 0, 0), f"{name}_yaw")
        self._bone(name, at, (0, roll, 0), f"{name}_lay")
        self.axis[name] = (at[0], at[2])
        return name

    def to_world(self, bone: str, X: np.ndarray, Y: np.ndarray, Z: np.ndarray) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
        """Model points of a bone's boxes where the game draws them (each bone turns about its pivot, Rz Ry Rx)."""
        P = np.stack([X, Y, Z], axis=-1).astype(np.float64)
        name: str | None = bone
        while name:
            pivot, (rx, ry, rz), parent = self.bones[name]
            rot = _rz(rz) @ _ry(ry) @ _rx(rx)
            P = (P - pivot) @ rot.T + pivot
            name = parent
        return P[..., 0], P[..., 1], P[..., 2]

    def normal_world(self, bone: str, n) -> np.ndarray:
        v = np.array(n, dtype=np.float64)
        name: str | None = bone
        while name:
            _, (rx, ry, rz), parent = self.bones[name]
            v = (_rz(rz) @ _ry(ry) @ _rx(rx)) @ v
            name = parent
        return v

    # --- shapes ------------------------------------------------------------------------------------------------------------
    def tube(self, bone: str, r: float, y0: float, y1: float, wall: float = WALL, sides: int = SIDES, torn_top: float = 0.0,
             torn_bottom: float = 0.0, group: str = "skin", only: tuple[int, ...] | None = None, seed: int = 0) -> list[str]:
        """A length of hollow skin round the piece's axis from y0 to y1, one plank a facet, its outside at r and
        {wall} thick; a torn end ragged (each facet torn off up to that many pixels short). {only}: some facets (a curl
        of skin)."""
        ax, az = self.axis[bone]
        s = r * math.tan(math.pi / sides) + 0.03
        rng = random.Random(seed)
        names = []
        for i in range(sides):
            if only is not None and i not in only:
                continue
            a = i * 360.0 / sides
            top = y1 - (rng.random() ** 1.6) * torn_top
            bot = y0 + (rng.random() ** 1.6) * torn_bottom
            if torn_top and rng.random() < 0.18:
                top = y1 - torn_top * (1.3 + rng.random())        # now and then a bite torn deeper
            n = f"t{self.n}_{i}"
            faces = ("south", "north", "up", "down") + (("east", "west") if torn_top or torn_bottom or only else ())
            self.g.box(n, (ax - s, bot, az + r - wall), (ax + s, top, az + r), faces=faces,
                       rotation={"origin": (ax, 0, az), "axis": "y", "angle": a} if a else None, bone=bone)
            names.append(n)
            self.of[n] = bone
            self.group["out" if group == "skin" else group].append(n)
        self.n += 1
        if torn_top:
            self.torn.setdefault(bone, []).append(y1)
        if torn_bottom:
            self.torn.setdefault(bone, []).append(y0)
        return names

    def disc(self, bone: str, r: float, y0: float, y1: float, group: str = "dome") -> list[str]:
        """A solid round plate across the piece (a tank dome, a bulkhead), warheads' planks."""
        ax, az = self.axis[bone]
        names = W.section(OnBone(self.g, bone), f"d{self.n}", r, y0, y1, W.sides_for(r), cx=ax, cz=az, stagger="full")
        self.n += 1
        for n in names:
            self.of[n] = bone
        self.group[group].extend(names)
        return names

    def cone(self, bone: str, r0: float, y0: float, length: float, steps: int, min_r: float = 1.0) -> list[str]:
        """A cone (a re-entry vehicle) on the piece's axis, ring by ring from the real profile as warheads.nose builds
        a nose: each ring at least 0.12 inside the one under it, reaching a pixel down into it; the last runs on to
        the tip."""
        ax, az = self.axis[bone]
        radii, last = [], r0
        for i in range(steps):
            r = min(W.profile("cone", (i + 0.5) / steps, r0, length), last - 0.12)
            if r < min_r and radii:
                break
            radii.append(max(r, min_r))
            last = radii[-1]
        names = []
        for i, r in enumerate(radii):
            a = y0 + length * i / steps
            b = y0 + length * (i + 1) / steps if i + 1 < len(radii) else y0 + length
            above = radii[i + 1] if i + 1 < len(radii) else 0.0
            stagger = "alternate" if W.sides_for(r) == 16 and above >= 0.55 * r else "full"
            names += W.section(OnBone(self.g, bone), f"c{self.n}_{i}", round(r, 3), round(a, 3), round(b, 3), W.sides_for(r),
                               cx=ax, cz=az, caps=("up",), stagger=stagger)
        self.n += 1
        for n in names:
            self.of[n] = bone
        self.group["cone"].extend(names)
        return names

    def block(self, bone: str, frm, to, group: str, rotation=None, faces=("north", "south", "east", "west", "up", "down")) -> str:
        n = f"b{self.n}"
        self.n += 1
        self.g.box(n, frm, to, faces=faces, rotation=rotation, bone=bone)
        self.of[n] = bone
        self.group[group].append(n)
        return n


def build() -> Wreck:
    w = Wreck()
    g = w.g

    # --- the tail: the first stage's aft end, the engine and three fins, thrown onto the land and propped on two fins.
    # Its middle on the block; its torn front turned north-west, back toward the body.
    tail_len = 46.0
    yaw = 40.0
    fwd = np.array([-math.sin(math.radians(yaw)), 0.0, -math.cos(math.radians(yaw))])
    at = tuple(np.array([0.0, 12.6, 0.0]) - fwd * tail_len / 2)
    tail = w.piece("tail", at, yaw=yaw, lay=-90.0, roll=45.0)
    ay = at[1]
    w.tube(tail, R, ay, ay + tail_len, torn_top=5.0, seed=11)
    w.disc(tail, R - WALL - 0.05, ay + 31.0, ay + 32.2)                          # the fuel tank's aft dome, seen inside
    w.disc(tail, R - 0.05, ay - 0.6, ay + 1.0, group="bulk")                     # the engine's thrust plate
    # The bell: hoops narrowing to the throat at the plate.
    for k, r in enumerate((7.5, 7.0, 6.4, 5.8, 5.2)):
        y0 = ay - 11.0 + 2.0 * k
        w.tube(tail, r, y0, y0 + 2.2, wall=0.8, group="nozzle")
    # Three fins (one torn off, lying apart), the one on top bent back by the fall.
    ax, az = w.axis[tail]
    for a in (0.0, 90.0, 180.0):
        if a == 0.0:
            # The fin on top (the other two prop the tail on the ground), bent back at its root by the fall.
            rot = {"origin": (ax, 0, az + R), "axis": "y", "angle": -28.0}
        else:
            rot = {"origin": (ax, 0, az), "axis": "y", "angle": a}
        w.block(tail, (ax - 0.7, ay + 2.0, az + R - 0.5), (ax + 0.7, ay + 19.0, az + R + 10.0), "fin", rotation=rot)

    # --- the body: the rest of the first stage, the interstage and the second stage, torn at both ends, in the furrow,
    # dipping into it toward the crater, a little askew; buckled two-thirds along.
    body = w.piece("body", (-30.0, -8.0, -2.0), yaw=8.0, lay=-94.0, roll=30.0)
    by = -8.0
    w.tube(body, R, by, by + 36.0, torn_bottom=6.0, seed=23)
    w.disc(body, R - WALL - 0.05, by + 9.0, by + 10.2)                           # a tank dome inside its torn aft end
    w.tube(body, R + 0.5, by + 14.0, by + 17.0, group="skin", seed=29)            # the interstage joint, standing proud
    w._bone("body_kink", (-30.0, by + 36.0, -2.0), (-9.0, 0, 4.0), "body")          # buckled
    w.axis["body_kink"] = w.axis["body"]
    w.tube("body_kink", R, by + 35.6, by + 62.0, torn_top=7.0, seed=31)
    w.disc("body_kink", R - WALL - 0.05, by + 52.0, by + 53.2)

    # --- the nose: the guidance bay and the re-entry cone, driven nose first into the crater, its torn base in the air.
    nose = w.piece("nose", (-32.0, -6.0, -76.0), yaw=-12.0, lay=-134.0, roll=70.0)
    ny = -6.0
    w.tube(nose, R, ny, ny + 18.0, torn_bottom=6.0, seed=41)
    w.disc(nose, R - 0.05, ny + 17.4, ny + 18.6, group="bulk")                    # the bay's top ring under the cone
    ax, az = w.axis[nose]
    # The bay's equipment, seen through the torn base: the guidance platform and its boxes on a shelf.
    w.disc(nose, R - WALL - 0.05, ny + 9.0, ny + 10.0, group="kit")
    for k, (dx, dz, h) in enumerate(((-3.0, -2.0, 4.0), (2.0, -1.0, 3.0), (-1.0, 3.0, 5.0))):
        w.block(nose, (ax + dx - 2.0, ny + 10.0, az + dz - 2.0), (ax + dx + 2.0, ny + 10.0 + h, az + dz + 2.0), "kit")
    w.cone(nose, R - 0.2, ny + 18.6, 28.0, 14)

    # --- what was strewn between: a fin on the land, the interstage ring half sunk at the furrow's edge, a curl of
    # skin, the cable bundle trailing from the body's torn end.
    w._bone("debris", (0, 0, 0), (0, 0, 0), "wreck")
    w.axis["debris"] = (0.0, 0.0)
    w.block("debris", (-6.0, 0.0, -30.0), (12.0, 1.4, -20.0), "fin", rotation={"origin": (3.0, 0, -25.0), "axis": "y", "angle": 22.0})
    ring = w.piece("ring", (-8.0, 2.6, -44.0), yaw=74.0, lay=-90.0, roll=0.0)
    w.tube(ring, R + 0.6, 2.6, 7.4, wall=0.9, group="skin", seed=53)
    curl = w.piece("curl", (-56.0, 3.2, 44.0), yaw=118.0, lay=-90.0, roll=200.0)
    w.tube(curl, R + 0.3, 3.2, 17.0, wall=0.6, only=(0, 1, 2, 3, 4), torn_top=3.0, torn_bottom=2.0, seed=59)
    for k, (dx, colour) in enumerate(((0.0, "cable"), (1.2, "cable"), (2.4, "cable"))):
        w.block("debris", (-29.0 + dx, -15.6, -6.0), (-28.2 + dx, -14.8, 10.0 - 3.0 * k), colour,       # on the furrow's floor
                rotation={"origin": (-28.0, -15.6, -6.0), "axis": "y", "angle": -8.0 - 9.0 * k})
    return w


# --- painting -------------------------------------------------------------------------------------------------------------------
TREFOIL = W.TREFOIL


def paint(w: Wreck) -> Tex:
    g = w.g
    g.layout()
    tex = Tex(g.tex_w, g.tex_h)
    S = M.Surfacer(g, seed=401, turned=True)          # faceted: paint each facet where it stands round the axis
    white = W.paint(W.WHITE, tone=0.8, wear=0.35, grime=0.1)
    inside = M.painted(M.GREY_PAINT_RAMP, tone=0.32, wear=0.1, grime=0.2, edge_light=0.05)
    edge = M.steel(tone=0.62, brushed=0.0, edge_light=0.25)
    S.paint(tex, w.group["out"], white, only=("south",))
    S.paint(tex, w.group["out"], inside, only=("north",))
    S.paint(tex, w.group["out"], edge, only=("up", "down", "east", "west"))
    S.paint(tex, w.group["fin"], white)
    S.paint(tex, w.group["dome"], M.steel(tone=0.55, brushed=0.04))
    S.paint(tex, w.group["bulk"], M.steel(tone=0.38))
    S.paint(tex, w.group["nozzle"], M.steel(tone=0.42, brushed=0.06), only=("south", "up", "down", "east", "west"))
    S.paint(tex, w.group["nozzle"], M.soot(tone=0.25), only=("north",))
    # The heat shield: charred near black, its char cracked paler in places.
    S.paint(tex, w.group["cone"], M.soot(tone=0.42, mottle=0.1, grain=0.08))
    S.paint(tex, w.group["kit"], M.painted(M.OLIVE_RAMP, tone=0.45, wear=0.2))
    S.paint(tex, w.group["cable"], M.rubber(tone=0.4))
    black = W.paint(W.BLACK, tone=0.35, wear=0.3)

    def around(f: M.Face) -> tuple[np.ndarray, np.ndarray]:
        """The angle round the piece's axis (0 .. 1, a turn) and the height along it, as built."""
        ax, az = w.axis[w.of[f.box.name]]
        ang = (np.arctan2(f.X - ax, f.Z - az) / (2 * math.pi)) % 1.0
        return ang, f.Y

    # The roll pattern on the tail: black quarters, checkered in two rows; a black band round the interstage; the
    # guidance bay's red band and its trefoil on yellow; stencilled dashes down the body.
    def livery(f: M.Face, img: np.ndarray) -> None:
        piece = w.of[f.box.name]
        ang, y = around(f)
        quarter = np.floor(ang * 4).astype(int) % 2
        mask = np.zeros(f.shape, bool)
        if piece == "tail":
            y0 = w.bones["tail"][0][1]
            row = ((y - y0) > 24).astype(int)
            mask = ((y - y0) > 4) & (quarter == row)
        elif piece == "body":
            mask = (y > -8.0 + 13.5) & (y < -8.0 + 17.5)
            mask |= (y > -8.0 + 24.0) & (y < -8.0 + 27.0)
        elif piece == "body_kink":
            mask = (y > -8.0 + 56.0) & (y < -8.0 + 58.0)
            dash = (np.abs(ang - 0.25) < 0.035) & (((y // 3) % 2) == 0) & (y > -8.0 + 40.0) & (y < -8.0 + 52.0)
            mask |= dash
        elif piece == "nose":
            red = (y > -6.0 + 13.0) & (y < -6.0 + 16.0)
            M.paste(img, S.material_on(f, W.paint(W.RED, tone=0.5, wear=0.3)), red)
            # The trefoil, 10 pixels square, on the bay's side.
            u = (ang - 0.5) * 2 * math.pi * R
            v = (-6.0 + 9.5) - y
            inside_decal = (np.abs(u) < 5.0) & (np.abs(v) < 5.0)
            yellow = S.material_on(f, W.paint(W.YELLOW, tone=0.55, wear=0.3))
            M.paste(img, yellow, inside_decal)
            iu = np.clip(((u + 5.0) / 2.0).astype(int), 0, 4)
            iv = np.clip(((v + 5.0) / 2.0).astype(int), 0, 4)
            pattern = np.array([[c == "K" for c in row] for row in TREFOIL])
            mask = inside_decal & pattern[iv, iu]
        if mask.any():
            M.paste(img, S.material_on(f, black), mask)
    S.overlay(tex, w.group["out"], livery, only=("south",))                    # the outside only
    S.overlay(tex, w.group["fin"], livery)

    # Scorched where it burned (the torn ends and the nose, heaviest at the edge), sooted in patches all over, caked in
    # earth on whatever lies low in the furrow and the crater, the paint scuffed through along the side it slid on.
    def damage(f: M.Face, img: np.ndarray) -> None:
        piece = w.of[f.box.name]
        bone = piece
        Xw, Yw, Zw = w.to_world(bone, f.X, f.Y, f.Z)
        n = w.normal_world(bone, f.normal)
        burn = M.noise(f, 6.0, 431, 2) * 0.55
        for end in w.torn.get(piece, []):
            burn = np.maximum(burn, np.clip(1.0 - np.abs(f.Y - end) / 9.0, 0, 1) * 1.1)
        if piece in ("nose",):
            burn = np.maximum(burn, 0.55 + M.noise(f, 4.0, 433, 2) * 0.4)
            if f.box.name in w.group["cone"]:
                burn = np.maximum(burn, 0.85)                   # the heat shield charred black by the fire
        if f.box.name in w.group["nozzle"]:
            burn = np.maximum(burn, 0.7)
        M.sooted(f, img, np.clip(burn - 0.3, 0, 1), strength=0.75)
        # Earth where it met the earth: splashed up the underside of whatever lies on the ground or ploughed through
        # it, and over the front of the nose that struck first; in clots, not a wash.
        clots = 0.55 + 0.45 * M.noise(f, 2.5, 437, 2)
        low = np.zeros(f.shape, np.float32)
        if n[1] < -0.15:
            low = np.full(f.shape, 0.42 + 0.5 * min(1.0, -n[1]), np.float32) * clots
        if piece in ("body", "body_kink"):
            low = np.maximum(low, np.clip((-4.0 - Yw) / 8.0, 0, 1) * clots)                 # the side down in the furrow
        if piece == "nose":
            tip_y = -6.0 + 18.6 + 28.0
            low = np.maximum(low, np.clip((f.Y - (tip_y - 16.0)) / 12.0, 0, 1) * clots)    # its front, driven in
        mud = low > 0.45
        rgb = img[..., :3].astype(np.float32)
        earth = np.array([92, 70, 48], np.float32)
        k = np.clip((low - 0.45) * 2.0, 0, 0.8)[..., None]
        rgb = np.where(mud[..., None], rgb * (1 - k) + earth * k, rgb)
        img[..., :3] = np.clip(rgb, 0, 255).astype(np.uint8)
    S.overlay(tex, sum((w.group[k] for k in ("out", "fin", "dome", "bulk", "nozzle", "cone", "kit")), []), damage)
    g.save(tex)
    return tex


# --- the item and the block -------------------------------------------------------------------------------------------------------
#: The item's picture, row by row: the tail on the left (its nozzle, fins and black and white quarters, lit above),
#: torn open at the break; the nose beyond the gap dropped a pixel, white with its red band, its charred cone tapering.
ICON = [
    "................",
    "................",
    "................",
    "................",
    ".s..............",
    ".ss.............",
    ".sss............",
    "NWWKKWWD........",
    "NwwKKwwD.DWWRcc.",
    "NKKssKKD.DwwRCCc",
    ".sss.....DssRC..",
    ".ss.............",
    ".s..............",
    "................",
    "................",
    "................",
]


def icon() -> None:
    """The item's picture (ICON)."""
    col = {"W": (230, 230, 222), "w": (196, 198, 194), "s": (150, 152, 150), "K": (34, 34, 38), "k": (58, 58, 62),
           "D": (84, 72, 64), "R": (176, 44, 32), "C": (52, 48, 46), "c": (82, 76, 72), "N": (96, 98, 104)}
    img = np.zeros((16, 16, 4), np.uint8)
    assert all(len(row) == 16 for row in ICON) and len(ICON) == 16
    for y, row in enumerate(ICON):
        for x, c in enumerate(row[:16]):
            if c != ".":
                img[y, x] = (*col[c], 255)
    Image.fromarray(img, "RGBA").save(paths.TEXTURES / "item" / f"{NAME}.png")
    write_json(paths.MODELS / "item" / f"{NAME}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"groundburst:item/{NAME}"}})
    write_json(paths.ASSETS / "items" / f"{NAME}.json", {"model": {"type": "minecraft:model", "model": f"groundburst:item/{NAME}"}})


def block_files() -> None:
    """The block draws nothing itself (its block entity draws the model): a model with only its particle."""
    write_json(paths.MODELS / "block" / f"{NAME}.json", {"textures": {"particle": "groundburst:block/rusted_steel_panel"}})
    write_json(paths.ASSETS / "blockstates" / f"{NAME}.json",
               {"variants": {f"facing={f}": {"model": f"groundburst:block/{NAME}"} for f in ("north", "east", "south", "west")}})


def main() -> None:
    w = build()
    paint(w)
    print(save_animations(NAME, [Animation("idle", 0.05, loop=False)]))
    icon()
    block_files()
    lo, hi = w.g.bounds()
    print("missile wreck written; bounds", [round(v, 1) for v in lo], [round(v, 1) for v in hi])


if __name__ == "__main__":
    main()
