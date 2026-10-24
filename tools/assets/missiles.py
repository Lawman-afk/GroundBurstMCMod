"""Missiles (DESIGN.md §10.4): one per warhead, made for it.

The head is the warhead itself: its own block model's elements, merged into the missile's item model at their own size
(one texel a pixel, as the missile's paint), its lugs left off. The body under it has the warhead's radius and the same
16-sided plank sections (W.section), so the facets run on from the head down the body; the warhead's mounting ring is
the joint (a warhead too slim for a missile stands on a tapering adapter). The body is painted in the warhead's own
materials and colours, then the missile's own markings, fins and nozzle.

Built in the world's pixels: the axis at x = z = 8, the tail at y = 0. Written at a quarter of that (EXPORT) as an item
model, which the game draws four times over in the world (in flight, on a launcher, on an assembler), so a texel is a
world pixel there. One model per missile serves everywhere; variants share its texture: with spent stages gone
(_s1, _s2), each spent stage falling (_stage1, _stage2) and each assembly section (_part1...). The table the game reads
(assets/groundzero/missiles.json) gives each missile's class, length, stages, engines, sections and radius by height.

Each missile follows its warhead's character and a real family (the research, DESIGN.md §10.4): the Army's olive-drab
tactical missiles, an ATACMS-like dispenser carrier, Luna-M spin verniers, a Scud's riveted aft skirt and exhaust vanes,
an Iskander's boat-tail, a Pershing II's penetrator on its taper, a Lance for the neutron warhead, Minuteman and R-36M
for the hall's two re-entry vehicles, and the UR-500, designed for a Tsar-class warhead, its first stage a core ringed
by six engine-carrying tanks. The superweapons' ammunition (DESIGN.md §19.5) follows C&C Generals: the GLA's Toxin SCUD
(an R-17 in the game's white and toxin green) and China's Nuclear Missile (a DF-5 in silver and red).

    tools\\.venv\\Scripts\\python.exe tools\\assets\\missiles.py [names...]
"""
from __future__ import annotations

import json
import math
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))

from gz import machine_kit as K  # noqa: E402
from gz import materials as M  # noqa: E402
from gz import palette as P  # noqa: E402
from gz import paths  # noqa: E402
from gz.model import BlockModel, write_json  # noqa: E402
from gz.tex import Tex  # noqa: E402
import grenades as G  # noqa: E402
import warheads as W  # noqa: E402

#: Item JSON = world pixels x 0.25 + offset: the axis (8, 8) stays at 8 and the tail (y 0) goes to -15.5 (item models
#: reach from -16 to 32; the half pixel spare takes the planks sunk under the tail).
SCALE = 0.25
TAIL_ITEM_Y = -15.5
EXPORT = (SCALE, (8 - 8 * SCALE, TAIL_ITEM_Y, 8 - 8 * SCALE))
SIDES = W.SIDES
ALL6 = ("north", "south", "east", "west", "up", "down")
#: The table the game reads.
TABLE = paths.ASSETS / "missiles.json"
#: Every missile's paint is the warhead's own, cleaner (a missile is kept in a store, not dragged about).
CLEAN = 0.08


class Rocket:
    """One missile being built: its model in the world's pixels (axis at x = z = 8, tail at y = 0), its parts by group
    (the painter's groups), its round sections, its stages and assembly sections by height."""

    def __init__(self, name: str, cls: str, tex: int = 256):
        self.name = name                                       # the warhead's id: the missile's look
        self.warhead = name
        self.cls = cls
        self.m = BlockModel(f"missile/{name}", tex_size=(tex, tex), kind="item")
        self.m.texture_folder = "block"
        self.m.export = EXPORT
        self.m.open_ok = set()
        self.groups: dict[str, list[str]] = {}
        self.counts: dict[str, int] = {}
        self.round: dict[str, tuple[bool, bool, bool]] = {}
        self.profile: list[tuple[float, float, float]] = []    # (y0, y1, r) round the axis, for the table
        self.parts: list[float] = []                           # assembly sections' tops, bottom up (the last: the tip)
        self.stages: list[float] = []                          # each stage's top, bottom up (the last: the tip)
        self.engines: list[float] = []                         # each stage's exhaust: the bottom of its bell
        #: Groups that belong to an assembly section other than their height says (an upper stage's bell hangs in the
        #: interstage below it, which goes with the stage under it): group -> section index.
        self.part_of: dict[str, int] = {}
        #: Which stage each assembly section flies with (a missile of one stage: all the first).
        self.part_stage: list[int] = []
        self.head: float = 0.0                                 # the warhead's foot
        self.tip: float = 0.0                                  # the nose's tip
        #: Groups stowed while it is in its box (a cruise missile's wings and scoop): its "_folded" variant leaves them out.
        self.folds: set[str] = set()

    # --- building -------------------------------------------------------------------------------------------------------------
    def next_name(self, group: str) -> str:
        """A new piece's name in its group: the group, a dash and a count (no group's name runs into another's)."""
        self.counts[group] = self.counts.get(group, 0) + 1
        return f"{group}-{self.counts[group]}"

    def add(self, group: str, names, top: bool = True, bottom: bool = False, foot: bool = False) -> list[str]:
        self.groups.setdefault(group, []).extend(names)
        for n in names:
            self.round[n] = (top, bottom, foot)
        return list(names)

    def ring(self, group: str, r: float, y0: float, y1: float, caps=("up", "down"), top=True, bottom=False, foot=False,
             sides=None, stagger=None, cx: float = 8.0, cz: float = 8.0) -> list[str]:
        """A round section of radius r from y0 to y1 (W.section's planks). Without a bottom cap it reaches a pixel down
        into what it stands on; with one, its planks are sunk so no two bottom caps share a plane ("base")."""
        if stagger is None and "down" in caps and W.sides_for(r) == 16:
            stagger = "base"
        n = self.next_name(group)
        names = W.section(self.m, n, round(r, 3), y0, y1, sides or W.sides_for(r), cx=cx, cz=cz, caps=caps, stagger=stagger)
        if abs(cx - 8.0) < 1e-6 and abs(cz - 8.0) < 1e-6:
            self.profile.append((round(y0, 3), round(y1, 3), round(r, 3)))
        return self.add(group, names, top, bottom, foot)

    def tube(self, group: str, r: float, y0: float, y1: float, on_joint: bool = False) -> list[str]:
        """A length of body: its top hidden under what stands on it; its foot on a wider joint (a cap there, unseen) or
        reaching down into a section of its own radius' joint."""
        return self.ring(group, r, y0, y1, caps=("down",) if on_joint else (), top=False, bottom=False)

    def joint(self, group: str, r: float, y: float, h: float = 1.0) -> list[str]:
        """A band standing proud round the body where two sections bolt together (seen top and bottom)."""
        return self.ring(group, r, y, y + h, caps=("up", "down"), top=True, bottom=True)

    def box(self, group: str, frm, to, faces=ALL6, rotation=None, light: int = 0, open_ok: bool = True) -> str:
        n = self.next_name(group)
        self.m.box(n, frm, to, faces=faces, rotation=rotation, light=light)
        self.groups.setdefault(group, []).append(n)
        if open_ok:
            self.m.open_ok.add((n, "*"))
        return n

    def stack(self, group: str, radii, y0: float, h: float = 1.0, cx: float = 8.0, cz: float = 8.0) -> float:
        """Rings one above another, bottom up, each h tall: a taper or a flare. Each ring's ledge shows where the next is
        narrower; each reaches a pixel down into the one under it. Returns the top."""
        y = y0
        for i, r in enumerate(radii):
            self.ring(group, r, y, y + h, caps=("up", "down") if i == 0 else ("up",), top=True, bottom=(i == 0),
                      stagger="full", cx=cx, cz=cz)
            y += h
        return y

    def taper(self, group: str, r0: float, r1: float, y0: float, h: int) -> float:
        """Rings a pixel tall from radius r0 at y0 to r1 at the top (an adapter, a boat-tail), each at least 0.12 apart."""
        radii = [r0 + (r1 - r0) * (i + 0.5) / h for i in range(h)]
        return self.stack(group, radii, y0)

    def closure(self, group: str, R: float, y: float, h: float = 0.6, cx: float = 8.0, cz: float = 8.0, inner: float = 2.4) -> float:
        """An aft closure under a section's bottom at y (radius R): rings stepping in under it, each a little over half
        the one above, so the middle of every bottom shows only the smaller ring under it (W.section's "base" stagger
        leaves planks a quarter turn apart level near the middle; this covers them). It stops where what hangs under it
        (a bell's mouth, {inner} across its middle) covers the rest. Returns its foot."""
        r = R
        while True:
            r = round(0.56 * r + 0.1, 3)
            small = W.sides_for(r) != 16
            self.ring(group, r, y - h, y, caps=("down",), top=False, bottom=True, cx=cx, cz=cz, stagger="full" if small else "base")
            y -= h
            if small or 0.56 * r <= inner:
                return y

    def bell(self, group: str, exit_r: float, throat_r: float, y0: float, h: int, cx: float = 8.0, cz: float = 8.0) -> float:
        """A rocket's bell nozzle, its mouth at y0 narrowing up to its throat: rings a pixel tall."""
        y = y0
        for i in range(h):
            r = exit_r + (throat_r - exit_r) * (i / max(1, h - 1)) ** 1.4
            caps = ("up", "down") if i == 0 else ("up",)
            n = self.next_name(group)
            names = W.section(self.m, n, round(r, 3), y, y + 1, W.sides_for(r), cx=cx, cz=cz, caps=caps, stagger="full")
            self.add(group, names, True, i == 0, False)
            y += 1
        return y

    def fins(self, group: str, R: float, y0: float, root: float, tip: float, span: float, thick: float = 0.8,
             bearings=(0, 90, 180, 270), spans: dict | None = None) -> None:
        """Clipped-delta fins on a section of radius R: the trailing edge square at y0, the root chord {root} along the
        body, the tip chord {tip} at {span} out, the leading edge one straight sweep between (an element turned to it,
        not steps). Bearings: 0 the front (+z), 90 east (+x). {spans} gives a fin its own span (a crude rocket's)."""
        for b in bearings:
            s = (spans or {}).get(b, span)
            drop = root - tip
            theta = math.degrees(math.atan2(drop, s))
            length = math.hypot(s, drop)
            h = drop * math.cos(math.radians(theta)) + 0.35
            t, t2 = thick / 2, thick / 2 - 0.05
            top = y0 + root
            if b == 90:
                self.box(group, (8 + R, y0, 8 - t), (8 + R + s, y0 + tip, 8 + t), faces=("north", "south", "east", "down"))
                if drop > 0.01:
                    self.box(group, (8 + R, top - h, 8 - t2), (8 + R + length, top, 8 + t2), faces=("north", "south", "up"),
                             rotation={"origin": (8 + R, top, 8), "axis": "z", "angle": -theta})
            elif b == 270:
                self.box(group, (8 - R - s, y0, 8 - t), (8 - R, y0 + tip, 8 + t), faces=("north", "south", "west", "down"))
                if drop > 0.01:
                    self.box(group, (8 - R - length, top - h, 8 - t2), (8 - R, top, 8 + t2), faces=("north", "south", "up"),
                             rotation={"origin": (8 - R, top, 8), "axis": "z", "angle": theta})
            elif b == 0:
                self.box(group, (8 - t, y0, 8 + R), (8 + t, y0 + tip, 8 + R + s), faces=("east", "west", "south", "down"))
                if drop > 0.01:
                    self.box(group, (8 - t2, top - h, 8 + R), (8 + t2, top, 8 + R + length), faces=("east", "west", "up"),
                             rotation={"origin": (8, top, 8 + R), "axis": "x", "angle": theta})
            elif b == 180:
                self.box(group, (8 - t, y0, 8 - R - s), (8 + t, y0 + tip, 8 - R), faces=("east", "west", "north", "down"))
                if drop > 0.01:
                    self.box(group, (8 - t2, top - h, 8 - R - length), (8 + t2, top, 8 - R), faces=("east", "west", "up"),
                             rotation={"origin": (8, top, 8 - R), "axis": "x", "angle": -theta})

    def grid_fins(self, group: str, R: float, y0: float, span: float = 5.0, width: float = 5.0, depth: float = 1.5,
                  bearings=(0, 90, 180, 270)) -> None:
        """Lattice fins (a Tochka's, an Iskander's): a frame across the flow standing off the body, crossed by bars, the
        air passing through its cells."""
        bar = 0.5
        for b in bearings:
            a = math.radians(b)
            # Built for the +z side (bearing 0) and turned about the axis to its bearing.
            def piece(frm, to, b=b):
                self.box(group, frm, to, faces=ALL6, rotation=None if b == 0 else {"origin": (8, y0, 8), "axis": "y", "angle": b})
            z0, z1 = 8 + R + 0.6, 8 + R + span
            x0, x1 = 8 - width / 2, 8 + width / 2
            piece((x0, y0, z0), (x0 + bar, y0 + depth, z1))
            piece((x1 - bar, y0, z0), (x1, y0 + depth, z1))
            piece((x0 + bar, y0, z1 - bar), (x1 - bar, y0 + depth, z1))
            piece((x0 + bar, y0, z0), (x1 - bar, y0 + depth, z0 + bar))
            for k in (1, 2):                                   # the lattice: two bars each way, crossing at two heights
                xm = x0 + (x1 - x0) * k / 3
                piece((xm - 0.2, y0 + 0.1, z0 + bar), (xm + 0.2, y0 + depth - 0.1, z1 - bar))
                zm = z0 + (z1 - z0) * k / 3
                piece((x0 + bar, y0 + 0.17, zm - 0.2), (x1 - bar, y0 + depth - 0.17, zm + 0.2))
            piece((8 - 0.4, y0 - 0.5, 8 + R - 0.2), (8 + 0.4, y0 + depth + 0.5, z0))   # its hinge arm off the body
            del a

    def on_side(self, group: str, bearing: float, R: float, y0: float, y1: float, depth: float, width: float) -> str:
        """A box standing {depth} off the body (radius R) at a bearing, from y0 to y1, {width} wide: a raceway, a
        connector, a panel. Turned about the axis to its bearing."""
        return self.box(group, (8 - width / 2, y0, 8 + R - 0.3), (8 + width / 2, y1, 8 + R + depth),
                        faces=("east", "west", "south", "up", "down"),
                        rotation=None if bearing % 360 == 0 else {"origin": (8, y0, 8), "axis": "y", "angle": bearing})

    def verniers(self, group: str, R: float, y: float, bearings=(45, 135, 225, 315), size: float = 1.4) -> None:
        """Small nozzles round the body (a Luna-M's spin verniers): stubs standing off it, their mouths down."""
        for b in bearings:
            self.box(group, (8 - size / 2, y, 8 + R - 0.3), (8 + size / 2, y + size, 8 + R + 1.0), faces=("east", "west", "south", "up", "down"),
                     rotation={"origin": (8, y, 8), "axis": "y", "angle": b})

    def vanes(self, group: str, r: float, y0: float, h: float = 1.6) -> None:
        """Graphite jet vanes in a bell's mouth (a Scud's): a cross of plates steering the exhaust."""
        self.box(group, (8 - r, y0 - 0.6, 8 - 0.25), (8 + r, y0 + h, 8 + 0.25))
        self.box(group, (8 - 0.25, y0 - 0.54, 8 - r), (8 + 0.25, y0 + h - 0.07, 8 + r))

    # --- the warhead ------------------------------------------------------------------------------------------------------------
    def mount(self, y: float) -> float:
        """Stands the warhead on the body at y; returns the nose's tip."""
        self.head = y
        self.tip = y + max(el["to"][1] for el in warhead_json(self.warhead)["elements"])
        return self.tip

    def ring_of_head(self) -> float:
        return W.mount(self.warhead)[0]

    # --- painting -----------------------------------------------------------------------------------------------------------------
    def layout(self) -> None:
        while True:
            try:
                self.m.layout()
                return
            except ValueError:
                self.m.tex_w *= 2
                self.m.tex_h = self.m.tex_w

    def kit(self) -> K.Kit:
        k = K.Kit(self.m, {}, seed=sum(map(ord, self.name)) % 997, ao_reach=2.0, turned=True)
        for n, (top, bottom, foot) in self.round.items():
            W.round_faces(k.S, [n], top, bottom, foot)
        return k


def warhead_json(name: str) -> dict:
    return json.loads((paths.MODELS / "block" / f"{name}.json").read_text(encoding="utf-8"))


#: Warhead elements a missile's head leaves off: a bomb's suspension and hoisting lugs (a missile warhead hangs on nothing).
LEFT_OFF = ("lug",)


def head_elements(rk: Rocket) -> list[dict]:
    """The warhead's elements, stood on the missile at its foot and written as the missile's JSON has them."""
    out = []
    for el in warhead_json(rk.warhead)["elements"]:
        if el["name"].startswith(LEFT_OFF):
            continue
        e = json.loads(json.dumps(el))
        e["from"] = [round(v, 5) for v in rk.m.exported((el["from"][0], el["from"][1] + rk.head, el["from"][2]))]
        e["to"] = [round(v, 5) for v in rk.m.exported((el["to"][0], el["to"][1] + rk.head, el["to"][2]))]
        if "rotation" in e and "origin" in e["rotation"]:
            o = el["rotation"]["origin"]
            e["rotation"]["origin"] = [round(v, 5) for v in rk.m.exported((o[0], o[1] + rk.head, o[2]))]
        for face in e["faces"].values():
            face["texture"] = "#w"
        e["name"] = "head_" + e["name"]
        out.append(e)
    return out


def head_box(rk: Rocket) -> tuple[tuple, tuple]:
    els = head_elements(rk)
    lo = tuple(min(min(e["from"][i], e["to"][i]) for e in els) for i in range(3))
    hi = tuple(max(max(e["from"][i], e["to"][i]) for e in els) for i in range(3))
    return lo, hi


def displays(rk: Rocket) -> None:
    """The item's display: fitted in the GUI (lying diagonally across the slot), and in the hand, on the ground and in a
    frame scaled to fit, the head included."""
    also = head_box(rk)
    rk.m.fit_display(gui_rotation=(30, 45, 30), gui_margin=0.5, also=also)
    lo, hi = rk.m.bounds(also)
    c = [(lo[i] + hi[i]) / 2 for i in range(3)]
    size = max(hi[i] - lo[i] for i in range(3))
    k = min(1.0, 32.0 / size)

    def ctx(scale, base, rotation=(0, 0, 0)):
        s = round(scale * k, 4)
        return {"rotation": list(rotation), "translation": [round(base[i] - (c[i] - 8) * s, 3) for i in range(3)], "scale": [s] * 3}
    rk.m.display["ground"] = ctx(0.5, (0, 0, 0))
    rk.m.display["fixed"] = ctx(0.5, (0, 0, 0))
    rk.m.display["thirdperson_righthand"] = ctx(0.4, (0, 3, 1), (0, 90, 0))
    rk.m.display["thirdperson_lefthand"] = ctx(0.4, (0, 3, 1), (0, 90, 0))
    rk.m.display["firstperson_righthand"] = ctx(0.35, (1, 3, 0), (0, -25, 0))
    rk.m.display["firstperson_lefthand"] = ctx(0.35, (1, 3, 0), (0, 155, 0))


class Livery:
    """How a missile is painted: a material per group, and overlays per group (fn(k, f, img))."""

    def __init__(self, **materials: M.Material):
        self.materials = materials
        self.overlays: dict[str, list] = {}

    def on(self, groups, fn) -> "Livery":
        for g in (groups if isinstance(groups, (list, tuple)) else [groups]):
            self.overlays.setdefault(g, []).append(fn)
        return self


def save(rk: Rocket, livery: Livery) -> dict:
    """Lays out, paints and writes the missile and its variants; returns its line in the table."""
    if not rk.part_stage:
        n = len(rk.stages)
        rk.part_stage = [0] * len(rk.parts) if n == 1 else list(range(n)) + [n - 1]
    displays(rk)
    rk.layout()
    k = rk.kit()
    t = Tex(rk.m.tex_w, rk.m.tex_h)
    for group, names in rk.groups.items():
        mat = livery.materials.get(group) or DEFAULTS.get(group) or livery.materials["body"]
        k.paint(t, names, mat)
        for fn in livery.overlays.get(group, []):
            k.overlay(t, names, lambda f, img, fn=fn: fn(k, f, img))
    rk.m.save(t)
    path = rk.m.model_path()
    data = json.loads(path.read_text(encoding="utf-8"))
    data["textures"]["w"] = f"groundzero:block/{rk.warhead}"
    data["elements"] += head_elements(rk)
    write_json(path, data)
    variants(rk, data)
    for issue in flicker(data):
        print(f"  flicker [{rk.name}]: {issue}")
    return entry(rk)


def part_of(rk: Rocket, el: dict) -> int:
    """Which assembly section an element belongs to: the head always the last; a group placed in one; any other by its
    middle's height."""
    if el["name"].startswith("head_"):
        return len(rk.parts) - 1
    group = el["name"].split("-")[0]
    if group in rk.part_of:
        return rk.part_of[group]
    y = ((el["from"][1] + el["to"][1]) / 2 - EXPORT[1][1]) / SCALE
    for i, top in enumerate(rk.parts):
        if y < top:
            return i
    return len(rk.parts) - 1


def stage_of(rk: Rocket, el: dict) -> int:
    return rk.part_stage[part_of(rk, el)] if rk.part_stage else 0


def variants(rk: Rocket, data: dict) -> None:
    """The variants sharing the missile's texture: its assembly sections (_part1...); for a missile of stages, the
    missile with its spent stages gone (_s1, _s2) and each spent stage (_stage1, _stage2); for a cruise missile, itself
    with its wings and scoop stowed (_folded: in its box and leaving it)."""
    folder = paths.MODELS / "item" / "missile"
    for old in folder.glob(f"{rk.name}_*.json"):
        if _own(rk.name, old.stem):                         # (not the warhead's cruise missile's)
            old.unlink()

    def write(suffix: str, keep) -> None:
        v = {k: val for k, val in data.items() if k != "display"}
        v["elements"] = [e for e in data["elements"] if keep(e)]
        write_json(folder / f"{rk.name}_{suffix}.json", v)
    for i in range(len(rk.parts)):
        write(f"part{i + 1}", lambda e, i=i: part_of(rk, e) == i)
    for k in range(1, len(rk.stages)):
        write(f"s{k}", lambda e, k=k: stage_of(rk, e) >= k)
        write(f"stage{k}", lambda e, k=k: stage_of(rk, e) == k - 1)
    if rk.folds:
        write("folded", lambda e: e["name"].split("-")[0] not in rk.folds)


def entry(rk: Rocket) -> dict:
    """The missile's line in the table: heights in world pixels from its tail; its radius by height (round the axis)."""
    return {"class": rk.cls, "tail": TAIL_ITEM_Y, "length": round(rk.tip, 3), "head": round(rk.head, 3),
            "stages": [round(v, 3) for v in rk.stages], "engines": [round(v, 3) for v in rk.engines],
            "parts": [round(v, 3) for v in rk.parts], "profile": [list(p) for p in sorted(set(rk.profile))]}


# --- the flicker check ----------------------------------------------------------------------------------------------------------

def _world(v) -> list[float]:
    return [(v[i] - EXPORT[1][i]) / SCALE for i in range(3)]


def flicker(data: dict, step: float = 0.1, tol: float = 0.03) -> list[str]:
    """Where the finished missile (head and all, in world pixels) would flicker: looking straight down, the two highest
    up caps within {tol} of each other; looking straight up, the two lowest down caps; and upright faces pointing the
    same way on one plane, overlapping. Elements turned about any axis but the upright one are left out."""
    issues = []
    caps = {"up": [], "down": []}
    sides = []
    normals = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0)}
    for el in data["elements"]:
        rot = el.get("rotation")
        if rot and rot.get("axis", "y") != "y":
            continue
        (x0, y0, z0), (x1, y1, z1) = _world(el["from"]), _world(el["to"])
        a = math.radians(rot["angle"]) if rot else 0.0
        o = _world(rot["origin"]) if rot else (8, 0, 8)
        for d in ("up", "down"):
            if d in el["faces"]:
                caps[d].append((x0, x1, z0, z1, y1 if d == "up" else y0, a, o[0], o[2], el["name"]))
        c, s = math.cos(a), math.sin(a)
        for d in el["faces"]:
            if d not in normals:
                continue
            nx, _, nz = normals[d]
            px = x1 if nx > 0 else x0 if nx < 0 else (x0 + x1) / 2
            pz = z1 if nz > 0 else z0 if nz < 0 else (z0 + z1) / 2
            half = (z1 - z0) / 2 if nx else (x1 - x0) / 2
            wx, wz = c * (px - o[0]) + s * (pz - o[2]) + o[0], -s * (px - o[0]) + c * (pz - o[2]) + o[2]
            n = (c * nx + s * nz, -s * nx + c * nz)
            t = (-n[1], n[0])
            sides.append((el["name"], d, n, n[0] * wx + n[1] * wz, t[0] * wx + t[1] * wz, half, y0, y1))
    xs = np.arange(-24, 40, step) + step / 2
    X, Z = np.meshgrid(xs, xs)
    for d, sign in (("up", 1), ("down", -1)):
        best = np.full(X.shape, -1e9)
        second = np.full(X.shape, -1e9)
        for x0, x1, z0, z1, y, a, ox, oz, _ in caps[d]:
            c, s = math.cos(-a), math.sin(-a)
            dx, dz = X - ox, Z - oz
            lx, lz = c * dx + s * dz + ox, -s * dx + c * dz + oz
            h = np.where((lx >= x0) & (lx <= x1) & (lz >= z0) & (lz <= z1), sign * y, -1e9)
            higher = h > best
            second = np.where(higher, best, np.maximum(second, h))
            best = np.where(higher, h, best)
        fight = (best > -1e8) & (second > -1e8) & (best - second < tol)
        if fight.any():
            ys = sorted({round(sign * float(v), 2) for v in best[fight]})
            issues.append(f"{d} caps {float(fight.sum()) * step * step:.2f} px2 at y {ys[:6]}")
    for i in range(len(sides)):
        n1, d1, nn1, off1, mid1, half1, ya1, yb1 = sides[i]
        for j in range(i + 1, len(sides)):
            n2, d2, nn2, off2, mid2, half2, ya2, yb2 = sides[j]
            if nn1[0] * nn2[0] + nn1[1] * nn2[1] < 0.9999 or abs(off1 - off2) > tol:
                continue
            across = min(mid1 + half1, mid2 + half2) - max(mid1 - half1, mid2 - half2)
            up = min(yb1, yb2) - max(ya1, ya2)
            if across > 0.01 and up > 0.01:
                issues.append(f"faces {n1}/{d1} vs {n2}/{d2}: {across * up:.2f} px2")
    return issues


# --- markings -----------------------------------------------------------------------------------------------------------------

def band(y0: float, y1: float, mat: M.Material):
    return lambda k, f, img: W.band(k, f, img, y0, y1, mat)


def seam(y: float, factor: float = 0.66):
    """A seam round the body: a texel row darkened at height y."""
    def fn(k, f, img):
        if f.d in SIDES:
            M.darken(img, np.floor(f.Y) == math.floor(y), factor)
    return fn


def rivets(y: float):
    return lambda k, f, img: W.bolts(f, img, y)


def stencil(ys, colour, width: int = 6, pattern: str = "xx.xxx.x"):
    return lambda k, f, img: W.stencil(f, img, ys, colour, width, pattern)


def decal(rows_, colours: dict, top: float):
    return lambda k, f, img: W.decal(f, img, rows_, colours, top)


def soot(k, f, img) -> None:
    """A bell blackened by its exhaust; its mouth (the bottom) darkest in the middle."""
    if f.d == "down":
        d = np.hypot(f.X - 8, f.Z - 8)
        img[..., :3] = np.clip(img[..., :3] * (0.25 + 0.12 * np.clip(d - 0.6, 0, 3)[..., None]), 0, 255)
    elif f.d in SIDES:
        M.darken(img, np.ones(f.shape, dtype=bool), 0.85)


def soot_at(cx: float, cz: float):
    """soot() for a bell off the axis (the clustered first stage's engines)."""
    def fn(k, f, img):
        if f.d == "down":
            d = np.hypot(f.X - cx, f.Z - cz)
            img[..., :3] = np.clip(img[..., :3] * (0.25 + 0.12 * np.clip(d - 0.6, 0, 3)[..., None]), 0, 255)
        elif f.d in SIDES:
            M.darken(img, np.ones(f.shape, dtype=bool), 0.85)
    return fn


def roll(y0: float, y1: float, colour, quadrants=(0, 2)):
    """A roll pattern: two opposite quadrants round the body painted (a test missile's, to see it roll in flight)."""
    def fn(k, f, img):
        if f.d in SIDES:
            q = np.floor(((W.angle_of(f) + 360) % 360) / 90).astype(int)
            on = np.isin(q, quadrants) & (f.Y >= y0) & (f.Y < y1)
            img[on, :3] = colour[:3]
    return fn


def hazard(y0: float, y1: float, a=None, b=None, width: float = 1.0):
    """Diagonal hazard stripes round the body between y0 and y1."""
    a = a or W.YELLOW[4]
    b = b or W.BLACK[1]

    def fn(k, f, img):
        if f.d in SIDES:
            along = (W.angle_of(f) / 360.0) * 2 * math.pi * 4.0
            on = (f.Y >= y0) & (f.Y < y1)
            stripe = np.floor((along + f.Y) / (2 * width)) % 2 == 0
            img[on & stripe, :3] = a[:3]
            img[on & ~stripe, :3] = b[:3]
    return fn


def drips(colour, y_top: float, seed: int = 7, share: float = 0.25):
    """Runs of sludge down the body from y_top (a toxic warhead's leaks)."""
    def fn(k, f, img):
        if f.d in SIDES:
            col = np.floor(W.angle_of(f) / 6).astype(int)
            r = M.white(col.astype(np.float32), np.zeros_like(f.Y), np.zeros_like(f.Z), seed)
            length = 2 + 10 * M.white(col.astype(np.float32), np.ones_like(f.Y), np.zeros_like(f.Z), seed + 1)
            on = (r < share) & (f.Y < y_top) & (f.Y > y_top - length)
            img[on, :3] = colour[:3]
    return fn


def patches(seed: int, share: float, colour, size: float = 3.0):
    """Patch plates and blotches (an improvised rocket's): squares of another colour by position."""
    def fn(k, f, img):
        if f.d in SIDES:
            cell = np.floor(f.Y / size) * 37 + np.floor(W.angle_of(f) / (size * 6))
            r = M.white(cell.astype(np.float32), np.zeros_like(f.Y), np.zeros_like(f.Z), seed)
            img[r < share, :3] = colour[:3]
    return fn


def frost(y0: float, y1: float):
    """Frost gathered on a cold tank: pale specks, thicker low down."""
    def fn(k, f, img):
        if f.d in SIDES:
            r = M.white(f.X, f.Y, f.Z, 917)
            on = (r < 0.02 + 0.2 * np.clip(1 - (f.Y - y0) / max(1, y1 - y0), 0, 1) ** 2) & (f.Y >= y0) & (f.Y < y1)
            img[on, :3] = W.FROST[6][:3]
    return fn


def paint(ramp, tone: float = 0.5, **kw) -> M.Material:
    """The warhead's paint (W.paint): its colours, in a missile's cleaner factory finish (after the first look: the
    warheads' mottle, over a long body, read as noise)."""
    for key, v in dict(wear=CLEAN, mottle=0.012, grain=0.012, dither=0.3).items():
        kw.setdefault(key, v)
    return W.paint(ramp, tone, **kw)


NOZZLE = W.metal(W.GUNMETAL, 0.32)
RING = W.BASE
#: Materials of the parts every missile has, unless its livery says otherwise.
DEFAULTS = {"aft": W.metal(W.GUNMETAL, 0.36)}


# --- Light (S) ----------------------------------------------------------------------------------------------------------------

def he() -> dict:
    """The HE warhead's missile: an olive-drab Army tactical missile, the body the warhead's own width, a guidance
    section under the head, a flared aft skirt with four clipped-delta fins and a single bell; the yellow band of its
    explosive fill repeated on the motor."""
    rk = Rocket("he_warhead", "S")
    R = 4.0
    rk.bell("bell", 2.4, 1.6, 0.0, 3)
    rk.ring("skirt", R + 0.4, 3.0, 12.0, caps=("up", "down"), top=True, bottom=True)
    rk.fins("fin", R + 0.4, 3.0, 9.0, 4.0, 4.5)
    rk.closure("aft", R + 0.4, 3.0)
    rk.tube("body", R, 12.0, 40.0)
    rk.joint("joint", R + 0.3, 40.0)
    rk.tube("guidance", R, 41.0, 46.0, on_joint=True)
    rk.on_side("raceway", 90, R, 13.0, 39.0, 0.6, 1.0)
    rk.on_side("plug", 270, R + 0.4, 5.0, 8.0, 0.5, 1.6)
    rk.mount(46.0)
    rk.parts, rk.stages, rk.engines = [12.0, 46.0, rk.tip], [rk.tip], [0.0]
    lv = Livery(body=paint(W.OLIVE), skirt=paint(W.OLIVE, 0.45), fin=paint(W.OLIVE, 0.42), joint=RING, guidance=paint(W.OLIVE, 0.47),
                bell=NOZZLE, raceway=paint(W.OLIVE, 0.42), plug=W.metal(W.BLACK, 0.4))
    lv.on("body", band(30, 31, W.paint(W.YELLOW, 0.625, 0.3)))
    lv.on("body", stencil((36.0, 35.0, 33.0), W.WHITE[5]))
    lv.on("body", seam(22.0, 0.72))
    lv.on("guidance", rivets(44.0))
    lv.on("joint", rivets(40.0))
    lv.on("skirt", stencil((8.0,), W.WHITE[5], 4, "x.xx"))
    lv.on("bell", soot)
    return save(rk, lv)


def napalm() -> dict:
    """The incendiary's missile: a bright steel rocket like the firebomb tank it carries (a Luna-M's lines: the warhead
    on a short neck, a ring of four spin verniers behind it), long low rectangular fins, the light red bands of an
    incendiary down its length."""
    rk = Rocket("napalm_warhead", "S")
    R = 4.4
    rk.bell("bell", 2.6, 1.8, 0.0, 3)
    rk.ring("skirt", R + 0.5, 3.0, 11.0, caps=("up", "down"), top=True, bottom=True)
    rk.fins("fin", R + 0.5, 3.0, 8.0, 8.0, 3.6, thick=0.7)
    rk.closure("aft", R + 0.5, 3.0)
    rk.tube("body", R, 11.0, 36.0)
    rk.ring("collar", R + 0.4, 36.0, 39.0, caps=("up", "down"), top=True, bottom=True)
    rk.verniers("vernier", R + 0.4, 36.6)
    rk.tube("neck", R, 39.0, 41.0, on_joint=True)
    rk.mount(41.0)
    rk.parts, rk.stages, rk.engines = [11.0, 41.0, rk.tip], [rk.tip], [0.0]
    steel = W.metal(W.STEEL, 0.62, brushed=0.06)
    lv = Livery(body=steel, skirt=W.metal(W.STEEL, 0.56, brushed=0.05), fin=W.metal(W.STEEL, 0.5), collar=RING, neck=steel,
                vernier=NOZZLE, bell=NOZZLE)
    red = W.paint(W.RED, 0.625, 0.3)
    lv.on("body", band(31, 33, red)).on("body", band(15, 16, red))
    lv.on("body", stencil((27.0, 26.0), W.BLACK[2], 5, "xx.xx"))
    lv.on("body", seam(23.0, 0.7))
    lv.on("fin", lambda k, f, img: img.__setitem__((f.Y >= 8.0) & (f.Y < 11.0), W.RED[4]) if f.d in SIDES else None)
    lv.on("collar", rivets(37.5))
    lv.on("vernier", soot).on("bell", soot)
    return save(rk, lv)


def chemical() -> dict:
    """The chemical warhead's missile: pale grey like its warhead, the two dark green bands of a toxic agent repeated in
    pairs down the body, swept trapezoid fins with black tips."""
    rk = Rocket("chemical_warhead", "S")
    R = 4.2
    rk.bell("bell", 2.4, 1.6, 0.0, 3)
    rk.ring("skirt", R + 0.4, 3.0, 11.0, caps=("up", "down"), top=True, bottom=True)
    rk.fins("fin", R + 0.4, 3.0, 9.0, 3.0, 4.6)
    rk.closure("aft", R + 0.4, 3.0)
    rk.tube("body", R, 11.0, 38.0)
    rk.joint("joint", R + 0.3, 38.0)
    rk.tube("guidance", R, 39.0, 44.0, on_joint=True)
    rk.on_side("raceway", 90, R, 12.0, 37.0, 0.6, 1.0)
    rk.mount(44.0)
    rk.parts, rk.stages, rk.engines = [11.0, 44.0, rk.tip], [rk.tip], [0.0]
    grey = W.paint(W.WHITE, 0.625, CLEAN)
    lv = Livery(body=grey, skirt=W.paint(W.WHITE, 0.58, CLEAN), fin=W.paint(W.WHITE, 0.56, CLEAN), joint=RING, guidance=grey,
                bell=NOZZLE, raceway=W.paint(W.WHITE, 0.55, CLEAN))
    green = W.paint(W.CHEM_GREEN, 0.5, 0.15)
    for y in (32, 30, 19, 17):
        lv.on("body", band(y, y + 1, green))
    lv.on("body", stencil((25.0, 24.0), W.BLACK[2], 5, "x.xxx"))
    lv.on("fin", lambda k, f, img: img.__setitem__(np.hypot(f.X - 8, f.Z - 8) > 7.6, W.BLACK[1]) if f.d in SIDES else None)
    lv.on("guidance", rivets(42.0)).on("joint", rivets(38.0)).on("bell", soot)
    return save(rk, lv)


def toxic_waste() -> dict:
    """The toxic waste drum's missile, improvised: a body of welded drums with their rolling hoops, crude plate fins of
    four different spans welded on, patch plates, a cut-off pipe for a nozzle, sludge leaking down from the warhead and
    hazard paint daubed on its tail."""
    rk = Rocket("toxic_waste_warhead", "S")
    R = 5.0
    rk.ring("pipe", 2.2, 0.0, 4.0, caps=("up", "down"), top=True, bottom=True)
    rk.ring("plate", R + 0.3, 4.0, 5.0, caps=("up", "down"), top=True, bottom=True)
    rk.closure("aft", R + 0.3, 4.0)
    rk.fins("fin", R, 5.0, 8.0, 8.0, 4.0, thick=0.9, spans={0: 3.6, 90: 4.4, 180: 3.2, 270: 4.0})
    drums = [(5.0, 16.0), (17.0, 28.0), (29.0, 39.0)]
    for i, (a, b) in enumerate(drums):
        rk.tube("body", R, a, b, on_joint=i > 0)
    for y in (16.0, 28.0):
        rk.joint("hoop", R + 0.5, y)
    rk.on_side("patch", 135, R, 20.0, 24.0, 0.3, 2.6)
    rk.on_side("patch", 300, R, 9.0, 12.0, 0.3, 2.2)
    rk.on_side("strap", 90, R, 6.0, 38.0, 0.4, 0.8)
    rk.mount(39.0)
    rk.parts, rk.stages, rk.engines = [16.0, 39.0, rk.tip], [rk.tip], [0.0]
    waste = paint(W.WASTE, 0.5, rust=0.08)
    lv = Livery(body=waste, pipe=W.metal(W.RUST, 0.4, rust=0.2), plate=W.metal(W.RUST, 0.45, rust=0.15), fin=W.metal(W.RUST, 0.48, rust=0.2),
                hoop=W.metal(W.WASTE, 0.56), patch=W.metal(W.RUST, 0.5, rust=0.25), strap=W.metal(W.STEEL, 0.42, rust=0.1))
    toxin = M.Material(ramp=W.ramp9(P.TOXIN), tone=0.62, mottle=0.05, grain=0.03, dither=0.4, edge_light=0, edge_dark=0, ao=0)
    lv.on("body", band(25, 26, toxin))
    lv.on("body", drips(P.TOXIN.shades[4], 39.0, 11))
    lv.on("body", hazard(6.0, 10.0, width=1.0))
    lv.on("body", decal(W.TREFOIL, {"Y": W.YELLOW[4], "K": W.BLACK[1]}, 23.0))
    lv.on("pipe", soot)
    return save(rk, lv)


def dirty() -> dict:
    """The dirty drum's missile, improvised: a rusty tube with a weld seam down it, yellow tape round it in two places,
    angle-iron fins welded on unevenly, a pipe for a raceway, a trefoil sticker and a cut-off pipe nozzle."""
    rk = Rocket("dirty_warhead", "S")
    R = 5.0
    rk.ring("pipe", 2.0, 0.0, 4.0, caps=("up", "down"), top=True, bottom=True)
    rk.ring("plate", R + 0.3, 4.0, 5.0, caps=("up", "down"), top=True, bottom=True)
    rk.closure("aft", R + 0.3, 4.0)
    rk.fins("fin", R, 5.0, 9.0, 5.0, 4.0, thick=0.9, spans={0: 4.4, 90: 3.6, 180: 4.0, 270: 3.0})
    rk.tube("body", R, 5.0, 37.0, on_joint=True)
    rk.joint("collar", R + 0.4, 37.0)
    rk.on_side("pipe_run", 60, R, 7.0, 36.0, 0.8, 0.8)
    rk.on_side("patch", 210, R, 14.0, 18.0, 0.3, 2.4)
    rk.mount(38.0)
    rk.parts, rk.stages, rk.engines = [14.0, 38.0, rk.tip], [rk.tip], [0.0]
    rust = paint(W.RUST, 0.5, rust=0.2)
    lv = Livery(body=rust, pipe=W.metal(W.GUNMETAL, 0.4, rust=0.2), plate=W.metal(W.RUST, 0.42, rust=0.2), fin=W.metal(W.GUNMETAL, 0.42, rust=0.25),
                collar=W.metal(W.RUST, 0.45, rust=0.1), pipe_run=W.metal(W.GUNMETAL, 0.45, rust=0.15), patch=W.metal(W.STEEL, 0.45, rust=0.3))
    tape = W.paint(W.YELLOW, 0.625, 0.45)
    lv.on("body", band(30, 32, tape)).on("body", band(10, 11, tape))
    lv.on("body", decal(W.TREFOIL, {"Y": W.YELLOW[4], "K": W.BLACK[1]}, 25.0))
    lv.on("body", lambda k, f, img: img.__setitem__(W.facet_mid(f) & (np.abs(f.normal[0]) > 0.95), W.RUST[1]) if f.d in SIDES else None)
    lv.on("pipe", soot)
    return save(rk, lv)


# --- Medium (M) ---------------------------------------------------------------------------------------------------------------

def heavy() -> dict:
    """The Heavy HE warhead's missile: a big single-stage olive-drab missile in the Pershing's manner, the warhead's
    breadth all the way down, big trapezoid fins on a riveted aft skirt, a mid-body joint, two yellow bands."""
    rk = Rocket("heavy_warhead", "M")
    R = 6.0
    rk.bell("bell", 3.4, 2.2, 0.0, 4)
    rk.ring("skirt", R + 0.5, 4.0, 16.0, caps=("up", "down"), top=True, bottom=True)
    rk.fins("fin", R + 0.5, 4.0, 13.0, 6.0, 6.5, thick=1.0)
    rk.closure("aft", R + 0.5, 4.0, inner=3.4)
    rk.tube("body", R, 16.0, 36.0)
    rk.joint("joint", R + 0.3, 36.0)
    rk.tube("body", R, 37.0, 54.0, on_joint=True)
    rk.joint("joint", R + 0.3, 54.0)
    rk.tube("guidance", R, 55.0, 61.0, on_joint=True)
    for b in (90, 270):
        rk.on_side("raceway", b, R, 17.0, 53.0, 0.7, 1.2)
    rk.mount(61.0)
    rk.parts, rk.stages, rk.engines = [16.0, 61.0, rk.tip], [rk.tip], [0.0]
    lv = Livery(body=paint(W.OLIVE), skirt=paint(W.OLIVE, 0.44), fin=paint(W.OLIVE, 0.42), joint=RING, guidance=paint(W.OLIVE, 0.47),
                bell=NOZZLE, raceway=paint(W.OLIVE, 0.42))
    yellow = W.paint(W.YELLOW, 0.625, 0.3)
    lv.on("body", band(48, 49, yellow)).on("body", band(46, 47, yellow))
    lv.on("body", stencil((42.0, 41.0, 39.0), W.WHITE[5], 7, "xx.xxx.x"))
    lv.on("body", stencil((26.0,), W.WHITE[5], 5, "x.xx.x"))
    lv.on("skirt", rivets(14.5)).on("skirt", rivets(5.5)).on("joint", rivets(36.0)).on("joint", rivets(54.0))
    lv.on("bell", soot)
    return save(rk, lv)


def cluster() -> dict:
    """The cluster dispenser's missile, an ATACMS: light grey, no big fins (four small control fins on a boat-tail),
    the dispenser's yellow band carried down, black bands where its sections join."""
    rk = Rocket("cluster_warhead", "M")
    R = 4.6
    rk.bell("bell", 2.2, 1.6, 0.0, 2)
    rk.taper("tail", 3.4, R - 0.15, 2.0, 5)
    rk.closure("aft", 3.4, 2.0)
    rk.fins("fin", R - 0.4, 3.0, 5.0, 4.0, 3.4, thick=0.7)
    rk.tube("body", R, 7.0, 34.0)
    rk.joint("joint", R + 0.25, 34.0)
    rk.tube("body", R, 35.0, 60.0, on_joint=True)
    rk.joint("joint", R + 0.25, 60.0)
    rk.tube("guidance", R, 61.0, 64.0, on_joint=True)
    rk.on_side("raceway", 90, R, 8.0, 59.0, 0.5, 1.0)
    rk.mount(64.0)
    rk.parts, rk.stages, rk.engines = [7.0, 64.0, rk.tip], [rk.tip], [0.0]
    grey = paint(W.CASING, 0.5)
    lv = Livery(body=grey, tail=paint(W.CASING, 0.42), fin=paint(W.CASING, 0.4), joint=W.metal(W.BLACK, 0.4), guidance=grey,
                bell=NOZZLE, raceway=paint(W.CASING, 0.44))
    lv.on("body", band(56, 57, W.paint(W.YELLOW, 0.625, 0.3)))
    lv.on("body", band(9, 11, W.paint(W.BLACK, 0.5, CLEAN)))
    lv.on("body", stencil((51.0, 50.0, 48.0), W.BLACK[2], 6, "x.xx.xx"))
    lv.on("body", stencil((24.0,), W.BLACK[2], 4, "xx.x"))
    lv.on("bell", soot)
    return save(rk, lv)


def gas() -> dict:
    """The poison gas dispenser's missile: the gas casing's olive-yellow down the body, yellow and black hazard bands at
    its tail, the agent's dashes in a ring, swept fins."""
    rk = Rocket("gas_warhead", "M")
    R = 4.6
    rk.bell("bell", 2.6, 1.8, 0.0, 3)
    rk.ring("skirt", R + 0.4, 3.0, 13.0, caps=("up", "down"), top=True, bottom=True)
    rk.fins("fin", R + 0.4, 3.0, 10.0, 4.0, 5.0)
    rk.closure("aft", R + 0.4, 3.0)
    rk.tube("body", R, 13.0, 57.0)
    rk.joint("joint", R + 0.3, 57.0)
    rk.tube("guidance", R, 58.0, 63.0, on_joint=True)
    rk.on_side("raceway", 90, R, 14.0, 56.0, 0.6, 1.0)
    rk.mount(63.0)
    rk.parts, rk.stages, rk.engines = [13.0, 63.0, rk.tip], [rk.tip], [0.0]
    body = paint(W.GAS)
    lv = Livery(body=body, skirt=paint(W.GAS, 0.42), fin=paint(W.GAS, 0.4), joint=RING, guidance=body, bell=NOZZLE, raceway=paint(W.GAS, 0.42))
    lv.on("body", band(52, 53, W.paint(W.YELLOW, 0.66, 0.3)))
    lv.on("skirt", hazard(8.0, 12.0))

    def dashes(k, f, img):
        if f.d in SIDES and (round(math.degrees(math.atan2(f.normal[0], f.normal[2])) / 22.5) % 2 == 0):
            img[W.facet_mid(f) & (np.floor(f.Y) == 40)] = W.YELLOW[3]
    lv.on("body", dashes)
    lv.on("body", stencil((46.0, 45.0), W.BLACK[2], 5, "xx.xx"))
    lv.on("joint", rivets(57.0)).on("bell", soot)
    return save(rk, lv)


def smoke() -> dict:
    """The smoke dispenser's missile: plain steel grey with the white bands of a smoke load, a Luna-M's ring of spin
    verniers behind the head and clipped-delta fins."""
    rk = Rocket("smoke_warhead", "M")
    R = 4.6
    rk.bell("bell", 2.6, 1.8, 0.0, 3)
    rk.ring("skirt", R + 0.4, 3.0, 12.0, caps=("up", "down"), top=True, bottom=True)
    rk.fins("fin", R + 0.4, 3.0, 9.0, 3.0, 5.0)
    rk.closure("aft", R + 0.4, 3.0)
    rk.tube("body", R, 12.0, 54.0)
    rk.ring("collar", R + 0.4, 54.0, 57.0, caps=("up", "down"), top=True, bottom=True)
    rk.verniers("vernier", R + 0.4, 54.6)
    rk.tube("neck", R, 57.0, 62.0, on_joint=True)
    rk.mount(62.0)
    rk.parts, rk.stages, rk.engines = [12.0, 62.0, rk.tip], [rk.tip], [0.0]
    grey = paint(W.STEEL, 0.5)
    lv = Livery(body=grey, skirt=paint(W.STEEL, 0.44), fin=paint(W.STEEL, 0.42), collar=RING, neck=grey,
                vernier=NOZZLE, bell=NOZZLE)
    white = W.paint(W.WHITE, 0.75, 0.2)
    for y in (48, 46, 22):
        lv.on("body", band(y, y + 1, white))
    lv.on("body", stencil((40.0, 39.0), W.BLACK[2], 5, "x.xx.x"))
    lv.on("collar", rivets(55.5)).on("vernier", soot).on("bell", soot)
    return save(rk, lv)


def cryo() -> dict:
    """The cryo dispenser's missile: its canister's ice blue, insulated (ribs every few pixels like a cold tank's
    stiffeners), frost thick toward the tail, white bands, white fins."""
    rk = Rocket("cryo_warhead", "M")
    R = 4.6
    rk.bell("bell", 2.6, 1.8, 0.0, 3)
    rk.ring("skirt", R + 0.4, 3.0, 12.0, caps=("up", "down"), top=True, bottom=True)
    rk.fins("fin", R + 0.4, 3.0, 9.0, 4.0, 4.6)
    rk.closure("aft", R + 0.4, 3.0)
    y = 12.0
    for i, top in enumerate((24.0, 36.0, 48.0, 58.0)):
        rk.tube("body", R, y, top, on_joint=i > 0)
        if top < 58.0:
            rk.joint("rib", R + 0.3, top)
            y = top + 1
    rk.joint("joint", R + 0.3, 58.0)
    rk.tube("guidance", R, 59.0, 63.0, on_joint=True)
    rk.mount(63.0)
    rk.parts, rk.stages, rk.engines = [12.0, 63.0, rk.tip], [rk.tip], [0.0]
    ice = paint(G.CRYO_CAN, 0.55)
    lv = Livery(body=ice, skirt=paint(W.WHITE, 0.7), fin=paint(W.WHITE, 0.68), rib=paint(W.WHITE, 0.72), joint=RING,
                guidance=ice, bell=NOZZLE)
    lv.on("body", frost(12.0, 58.0)).on("skirt", frost(3.0, 12.0))
    lv.on("body", band(52, 53, W.paint(W.WHITE, 0.8, 0.15)))
    lv.on("body", stencil((44.0, 43.0), W.WHITE[6], 5, "xx.x.x"))
    lv.on("joint", rivets(58.0)).on("bell", soot)
    return save(rk, lv)


# --- Heavy (L) ----------------------------------------------------------------------------------------------------------------

def thermobaric() -> dict:
    """The thermobaric warhead's missile, an Iskander: dark gunmetal, smooth, a long boat-tail with four small fins at
    its very end, the warhead's two orange bands repeated near the head."""
    rk = Rocket("thermobaric_warhead", "L")
    R = 5.6
    rk.bell("bell", 2.6, 1.8, 0.0, 2)
    rk.taper("tail", 4.0, R - 0.15, 2.0, 8)
    rk.closure("aft", 4.0, 2.0)
    rk.fins("fin", 4.4, 3.0, 6.0, 5.0, 4.0, thick=0.8)
    rk.tube("body", R, 10.0, 52.0)
    rk.joint("joint", R + 0.25, 52.0)
    rk.tube("body", R, 53.0, 92.0, on_joint=True)
    rk.joint("joint", R + 0.25, 92.0)
    rk.tube("guidance", R, 93.0, 97.0, on_joint=True)
    rk.on_side("raceway", 90, R, 11.0, 91.0, 0.6, 1.2)
    rk.mount(97.0)
    rk.parts, rk.stages, rk.engines = [10.0, 97.0, rk.tip], [rk.tip], [0.0]
    dark = paint(W.GUNMETAL, 0.48)
    lv = Livery(body=dark, tail=paint(W.GUNMETAL, 0.4), fin=paint(W.GUNMETAL, 0.38), joint=W.metal(W.BLACK, 0.4), guidance=dark,
                bell=NOZZLE, raceway=paint(W.GUNMETAL, 0.42))
    orange = W.paint(W.ORANGE, 0.625, 0.3)
    lv.on("body", band(86, 87, orange)).on("body", band(84, 85, orange))
    lv.on("body", stencil((79.0, 78.0, 76.0), W.WHITE[5], 6, "xx.xx.x"))
    lv.on("body", stencil((30.0,), W.WHITE[5], 4, "x.xx"))
    lv.on("bell", soot)
    return save(rk, lv)


def bunker_buster() -> dict:
    """The penetrator's missile, a Pershing II: dark steel, a long taper up to the slim tungsten penetrator, big swept
    fins on the aft skirt, a yellow band."""
    rk = Rocket("bunker_buster_warhead", "L")
    R = 6.0
    rk.bell("bell", 3.6, 2.4, 0.0, 4)
    rk.ring("skirt", R + 0.5, 4.0, 16.0, caps=("up", "down"), top=True, bottom=True)
    rk.fins("fin", R + 0.5, 4.0, 13.0, 5.0, 6.5, thick=1.0)
    rk.closure("aft", R + 0.5, 4.0)
    rk.tube("body", R, 16.0, 44.0)
    rk.joint("joint", R + 0.3, 44.0)
    rk.tube("body", R, 45.0, 70.0, on_joint=True)
    rk.joint("joint", R + 0.3, 70.0)
    top = rk.taper("adapter", R - 0.2, 2.95, 71.0, 10)
    for b in (90, 270):
        rk.on_side("raceway", b, R, 17.0, 69.0, 0.6, 1.0)
    rk.mount(top)
    rk.parts, rk.stages, rk.engines = [16.0, top, rk.tip], [rk.tip], [0.0]
    dark = paint(W.DARK, 0.5)
    lv = Livery(body=dark, skirt=paint(W.DARK, 0.44), fin=paint(W.DARK, 0.42), joint=RING, adapter=W.metal(W.TUNGSTEN, 0.5),
                bell=NOZZLE, raceway=paint(W.DARK, 0.44))
    lv.on("body", band(62, 63, W.paint(W.YELLOW, 0.625, 0.3)))
    lv.on("body", stencil((56.0, 55.0, 53.0), W.WHITE[4], 6, "xx.xxx"))
    lv.on("skirt", rivets(14.5)).on("joint", rivets(44.0)).on("joint", rivets(70.0)).on("bell", soot)
    return save(rk, lv)


def emp() -> dict:
    """The EMP canister's missile: its dark blue-grey, a body wider than the canister tapering up to it, copper bands
    between white ceramic insulator rings down the body like the coil it carries, swept fins."""
    rk = Rocket("emp_warhead", "L")
    R = 6.0
    rk.bell("bell", 3.4, 2.2, 0.0, 4)
    rk.ring("skirt", R + 0.5, 4.0, 15.0, caps=("up", "down"), top=True, bottom=True)
    rk.fins("fin", R + 0.5, 4.0, 11.0, 4.0, 6.0, thick=1.0)
    rk.closure("aft", R + 0.5, 4.0, inner=3.4)
    rk.tube("body", R, 15.0, 40.0)
    rk.joint("insulator", R + 0.3, 40.0)
    rk.joint("coil", R + 0.5, 41.0, 2.0)
    rk.joint("insulator", R + 0.3, 43.0)
    rk.tube("body", R, 44.0, 82.0, on_joint=True)
    rk.joint("joint", R + 0.3, 82.0)
    top = rk.taper("adapter", R - 0.2, 4.45, 83.0, 5)
    rk.on_side("raceway", 90, R, 16.0, 39.0, 0.6, 1.0)
    rk.mount(top)
    rk.parts, rk.stages, rk.engines = [15.0, top, rk.tip], [rk.tip], [0.0]
    body = paint(W.EMP_CASING, 0.5)
    lv = Livery(body=body, skirt=paint(W.EMP_CASING, 0.42), fin=paint(W.EMP_CASING, 0.4), joint=RING, adapter=body,
                insulator=W.paint(W.WHITE, 0.75, 0.1), coil=W.metal(P.COPPER, 0.56), bell=NOZZLE, raceway=paint(W.EMP_CASING, 0.42))
    copper = W.metal(P.COPPER, 0.6)
    for y in (70, 66, 25):
        lv.on("body", band(y, y + 1, copper))
    lv.on("body", stencil((60.0, 59.0), W.WHITE[4], 5, "x.xxx"))
    lv.on("coil", lambda k, f, img: M.darken(img, (np.floor(f.Y * 2) % 2 == 0), 0.75) if f.d in SIDES else None)
    lv.on("joint", rivets(82.0)).on("bell", soot)
    return save(rk, lv)


def massive() -> dict:
    """The Massive Ordnance Warhead's missile, a heavy Scud: olive drab, the warhead's breadth, welded fuel and oxidiser
    tanks joined at a bolted ring, a riveted aft skirt with four big trapezoid fins, graphite vanes in the bell, the
    warhead's orange band carried down."""
    rk = Rocket("massive_warhead", "L")
    R = 7.2
    rk.bell("bell", 4.4, 2.8, 0.0, 5)
    rk.vanes("vane", 3.4, 0.0)
    rk.ring("skirt", R + 0.5, 5.0, 19.0, caps=("up", "down"), top=True, bottom=True)
    rk.fins("fin", R + 0.5, 5.0, 15.0, 7.0, 7.0, thick=1.1)
    rk.closure("aft", R + 0.5, 5.0)
    rk.tube("body", R, 19.0, 52.0)
    rk.joint("joint", R + 0.35, 52.0, 1.5)
    rk.tube("body", R, 53.5, 82.0, on_joint=True)
    rk.joint("joint", R + 0.35, 82.0)
    rk.tube("guidance", R, 83.0, 88.0, on_joint=True)
    for b in (90, 270):
        rk.on_side("raceway", b, R, 20.0, 81.0, 0.7, 1.4)
    rk.mount(88.0)
    rk.parts, rk.stages, rk.engines = [19.0, 88.0, rk.tip], [rk.tip], [0.0]
    lv = Livery(body=paint(W.OLIVE), skirt=paint(W.OLIVE, 0.44), fin=paint(W.OLIVE, 0.42), joint=RING, guidance=paint(W.OLIVE, 0.47),
                bell=NOZZLE, vane=W.metal(W.BLACK, 0.3), raceway=paint(W.OLIVE, 0.42))
    lv.on("body", band(76, 78, W.paint(W.ORANGE, 0.625, 0.3)))
    lv.on("body", seam(36.0)).on("body", seam(68.0))
    lv.on("body", stencil((72.0, 71.0, 69.0), W.WHITE[5], 8, "xx.xxx.x"))
    lv.on("body", stencil((44.0, 43.0), W.WHITE[5], 6, "x.xx.x"))
    lv.on("skirt", rivets(17.5)).on("skirt", rivets(6.5)).on("joint", rivets(52.5)).on("joint", rivets(82.0))
    lv.on("bell", soot)
    return save(rk, lv)


# --- Strategic (XL): two stages -----------------------------------------------------------------------------------------------

def tactical() -> dict:
    """The tactical nuclear warhead's missile, a Pershing 1a: two olive-drab stages of one width, each with its own
    fins, a dark interstage between them, a taper up to the warhead, the warhead's yellow-over-black band repeated."""
    rk = Rocket("tactical_nuclear_warhead", "XL")
    R = 5.4
    rk.bell("bell", 3.2, 2.2, 0.0, 4)
    rk.ring("skirt", R + 0.4, 4.0, 16.0, caps=("up", "down"), top=True, bottom=True)
    rk.fins("fin", R + 0.4, 4.0, 12.0, 6.0, 6.0, thick=1.0)
    rk.closure("aft", R + 0.4, 4.0)
    rk.tube("stage1", R, 16.0, 62.0)
    rk.ring("inter", R + 0.3, 62.0, 68.0, caps=("up", "down"), top=True, bottom=True)
    rk.bell("bell2", 2.8, 2.0, 63.0, 3)
    rk.tube("stage2", R, 68.0, 110.0, on_joint=True)
    rk.fins("fin2", R, 68.0, 8.0, 4.0, 4.0, thick=0.8)
    rk.joint("joint", R + 0.3, 110.0)
    top = rk.taper("adapter", R - 0.2, 3.85, 111.0, 6)
    for b in (90,):
        rk.on_side("raceway", b, R, 17.0, 61.0, 0.6, 1.2)
        rk.on_side("raceway2", b, R, 75.0, 109.0, 0.6, 1.2)
    rk.mount(top)
    rk.parts, rk.stages, rk.engines = [62.0, top, rk.tip], [62.0, rk.tip], [0.0, 63.0]
    olive = paint(W.OLIVE)
    lv = Livery(body=olive, stage1=olive, stage2=olive, skirt=paint(W.OLIVE, 0.44), fin=paint(W.OLIVE, 0.42), fin2=paint(W.OLIVE, 0.42),
                inter=W.metal(W.BLACK, 0.42), joint=RING, adapter=olive, bell=NOZZLE, bell2=NOZZLE, raceway=paint(W.OLIVE, 0.42),
                raceway2=paint(W.OLIVE, 0.42))
    lv.on("stage2", band(103, 104, W.paint(W.YELLOW, 0.625, 0.25))).on("stage2", band(102, 103, W.paint(W.BLACK, 0.5, 0.2)))
    lv.on("stage2", stencil((96.0, 95.0, 93.0), W.WHITE[5], 6, "xx.xxx.x"))
    lv.on("stage1", stencil((50.0, 49.0, 47.0), W.WHITE[5], 6, "x.xx.xx"))
    lv.on("inter", rivets(63.0)).on("inter", rivets(67.0)).on("skirt", rivets(14.5)).on("joint", rivets(110.0))
    lv.on("bell", soot).on("bell2", soot)
    return save(rk, lv)


def fission() -> dict:
    """The fission warhead's missile, an SS-20 in desert sand: two finless solid stages (lattice fins steer it), the
    warhead's ochre and red, a dark interstage."""
    rk = Rocket("fission_warhead", "XL")
    R1, R2 = 7.6, 6.4
    rk.bell("bell", 4.0, 2.8, 0.0, 5)
    rk.ring("skirt", R1 + 0.3, 5.0, 12.0, caps=("up", "down"), top=True, bottom=True)
    rk.closure("aft", R1 + 0.3, 5.0)
    rk.grid_fins("grid", R1 + 0.3, 6.5, span=6.5, width=6.0, depth=2.0)
    rk.tube("stage1", R1, 12.0, 62.0)
    rk.taper("inter", R1 - 0.1, R2 + 0.35, 62.0, 4)
    rk.bell("bell2", 3.2, 2.2, 61.0, 3)
    rk.tube("stage2", R2, 66.0, 114.0)
    rk.joint("joint", R2 + 0.3, 114.0)
    rk.tube("guidance", R2, 115.0, 120.0, on_joint=True)
    rk.on_side("raceway", 90, R1, 13.0, 61.0, 0.7, 1.4)
    rk.on_side("raceway2", 90, R2, 67.0, 113.0, 0.6, 1.2)
    rk.mount(120.0)
    rk.parts, rk.stages, rk.engines = [62.0, 120.0, rk.tip], [62.0, rk.tip], [0.0, 61.0]
    sand = paint(W.OCHRE, 0.5)
    lv = Livery(body=sand, stage1=sand, stage2=sand, skirt=paint(W.OCHRE, 0.48), grid=W.metal(W.GUNMETAL, 0.45), inter=W.metal(W.BLACK, 0.42),
                joint=RING, guidance=sand, bell=NOZZLE, bell2=NOZZLE, raceway=paint(W.OCHRE, 0.48), raceway2=paint(W.OCHRE, 0.48))
    red = W.paint(W.RED, 0.625, 0.25)
    lv.on("stage2", band(106, 107, red)).on("stage1", band(55, 56, red))
    lv.on("stage2", stencil((98.0, 97.0, 95.0), W.BLACK[2], 7, "xx.xxx.x"))
    lv.on("stage1", stencil((44.0, 43.0), W.BLACK[2], 6, "x.xx.xx"))
    lv.on("stage1", seam(37.0, 0.75)).on("stage2", seam(90.0, 0.75))
    lv.on("joint", rivets(114.0)).on("bell", soot).on("bell2", soot)
    return save(rk, lv)


def boosted() -> dict:
    """The boosted fission warhead's missile, a Pershing II in the warhead's grey: a wider first stage with big fins,
    a slimmer second with small control vanes, the warhead's two cyan bands repeated."""
    rk = Rocket("boosted_fission_warhead", "XL")
    R1, R2 = 6.6, 5.6
    rk.bell("bell", 3.6, 2.4, 0.0, 4)
    rk.ring("skirt", R1 + 0.4, 4.0, 16.0, caps=("up", "down"), top=True, bottom=True)
    rk.fins("fin", R1 + 0.4, 4.0, 12.0, 5.0, 6.0, thick=1.0)
    rk.closure("aft", R1 + 0.4, 4.0)
    rk.tube("stage1", R1, 16.0, 62.0)
    rk.taper("inter", R1 - 0.1, R2 + 0.35, 62.0, 4)
    rk.bell("bell2", 3.0, 2.0, 61.0, 3)
    rk.tube("stage2", R2, 66.0, 116.0)
    rk.fins("fin2", R2, 66.0, 6.0, 3.0, 3.4, thick=0.8)
    rk.joint("joint", R2 + 0.3, 116.0)
    rk.tube("guidance", R2, 117.0, 121.0, on_joint=True)
    rk.on_side("raceway", 90, R1, 17.0, 61.0, 0.6, 1.2)
    rk.mount(121.0)
    rk.parts, rk.stages, rk.engines = [62.0, 121.0, rk.tip], [62.0, rk.tip], [0.0, 61.0]
    grey = paint(W.BOOST_GREY, 0.55)
    lv = Livery(body=grey, stage1=grey, stage2=grey, skirt=paint(W.BOOST_GREY, 0.48), fin=paint(W.BOOST_GREY, 0.45),
                fin2=paint(W.BOOST_GREY, 0.45), inter=W.metal(W.BLACK, 0.42), joint=RING, guidance=grey, bell=NOZZLE, bell2=NOZZLE,
                raceway=paint(W.BOOST_GREY, 0.48))
    cyan = W.paint(W.CYAN, 0.625, 0.25)
    lv.on("stage2", band(109, 110, cyan)).on("stage2", band(107, 108, cyan))
    lv.on("stage2", stencil((100.0, 99.0, 97.0), W.BLACK[2], 7, "xx.xxx.x"))
    lv.on("stage1", stencil((48.0, 47.0), W.BLACK[2], 6, "x.xx.xx"))
    lv.on("skirt", rivets(14.5)).on("joint", rivets(116.0)).on("bell", soot).on("bell2", soot)
    return save(rk, lv)


def neutron() -> dict:
    """The neutron warhead's missile, a Lance (the real carrier of the W70-3): slim and silver, a first stage with
    four big clipped-delta fins, a slimmer second stage, the warhead's cyan bands, reflector studs in rows."""
    rk = Rocket("neutron_warhead", "XL")
    R1, R2 = 4.6, 3.6
    rk.bell("bell", 2.8, 2.0, 0.0, 3)
    rk.ring("skirt", R1 + 0.3, 3.0, 18.0, caps=("up", "down"), top=True, bottom=True)
    rk.fins("fin", R1 + 0.3, 3.0, 15.0, 7.0, 6.5, thick=0.8)
    rk.closure("aft", R1 + 0.3, 3.0)
    rk.tube("stage1", R1, 18.0, 66.0)
    rk.taper("inter", R1 - 0.1, R2 + 0.35, 66.0, 3)
    rk.bell("bell2", 2.4, 1.8, 65.0, 3)
    rk.tube("stage2", R2, 69.0, 116.0)
    rk.joint("joint", R2 + 0.3, 116.0)
    rk.tube("guidance", R2, 117.0, 121.0, on_joint=True)
    rk.mount(121.0)
    rk.parts, rk.stages, rk.engines = [66.0, 121.0, rk.tip], [66.0, rk.tip], [0.0, 65.0]
    silver = W.metal(W.STEEL, 0.6, brushed=0.06)
    lv = Livery(body=silver, stage1=silver, stage2=silver, skirt=W.metal(W.STEEL, 0.52), fin=W.metal(W.STEEL, 0.5), inter=W.metal(W.BLACK, 0.42),
                joint=RING, guidance=silver, bell=NOZZLE, bell2=NOZZLE)
    cyan = W.paint(W.CYAN, 0.625, 0.25)
    for y in (110, 104, 98):
        lv.on("stage2", band(y, y + 1, cyan))
    lv.on("stage1", band(56, 57, cyan))

    def studs(k, f, img):
        if f.d in SIDES:
            for y in (80.5, 86.5, 92.5):
                M.lighten(img, (np.floor(f.Y) == math.floor(y)) & W.facet_mid(f), 1.4, 20)
    lv.on("stage2", studs)
    lv.on("stage1", stencil((44.0, 43.0, 41.0), W.BLACK[2], 5, "xx.x.x"))
    lv.on("joint", rivets(116.0)).on("bell", soot).on("bell2", soot)
    return save(rk, lv)


# --- Intercontinental (XXL, the hall): three stages ---------------------------------------------------------------------------

def thermonuclear() -> dict:
    """The thermonuclear re-entry vehicle's missile, a Minuteman: three solid stages narrowing upward in the warhead's
    drab, black interstages, four nozzles under the first stage, a post-boost bus under the re-entry vehicle, white and
    yellow bands."""
    rk = Rocket("thermonuclear_warhead", "XXL", tex=512)
    R1, R2, R3, RB = 11.0, 9.6, 8.4, 7.2
    for i, (dx, dz) in enumerate(((-4, -4), (4, -4), (-4, 4), (4, 4))):
        rk.bell("bell", 2.6, 1.8, 0.0, 3, cx=8 + dx, cz=8 + dz)
    rk.ring("skirt", R1 + 0.4, 3.0, 8.0, caps=("up", "down"), top=True, bottom=True)
    rk.closure("aft", R1 + 0.4, 3.0, inner=0.0)
    rk.tube("stage1", R1, 8.0, 60.0, on_joint=True)
    rk.taper("inter1", R1 - 0.1, R2 + 0.35, 60.0, 4)
    rk.bell("bell2", 4.0, 2.8, 59.0, 4)
    rk.tube("stage2", R2, 64.0, 104.0)
    rk.taper("inter2", R2 - 0.1, R3 + 0.35, 104.0, 3)
    rk.bell("bell3", 3.4, 2.4, 103.0, 3)
    rk.tube("stage3", R3, 107.0, 136.0)
    rk.taper("bus", R3 - 0.1, RB - 0.05, 136.0, 3)
    rk.tube("bus", RB - 0.25, 139.0, 148.0)
    rk.on_side("raceway", 90, R1, 9.0, 59.0, 0.8, 1.6)
    rk.on_side("raceway2", 90, R2, 65.0, 103.0, 0.7, 1.4)
    rk.on_side("raceway3", 90, R3, 108.0, 135.0, 0.6, 1.2)
    rk.mount(148.0)
    rk.parts, rk.stages, rk.engines = [60.0, 104.0, 148.0, rk.tip], [60.0, 104.0, rk.tip], [0.0, 59.0, 103.0]
    drab = paint(W.DRAB, 0.55)
    lv = Livery(body=drab, stage1=drab, stage2=drab, stage3=drab, skirt=W.metal(W.GUNMETAL, 0.42), inter1=W.metal(W.BLACK, 0.42),
                inter2=W.metal(W.BLACK, 0.42), bus=W.metal(W.GUNMETAL, 0.45), bell=NOZZLE, bell2=NOZZLE, bell3=NOZZLE,
                raceway=paint(W.DRAB, 0.48), raceway2=paint(W.DRAB, 0.48), raceway3=paint(W.DRAB, 0.48))
    white = W.paint(W.WHITE, 0.75, 0.2)
    lv.on("stage1", band(50, 52, white)).on("stage2", band(96, 97, W.paint(W.YELLOW, 0.625, 0.25))).on("stage3", band(128, 130, white))
    lv.on("stage1", stencil((40.0, 39.0, 37.0), W.WHITE[5], 9, "xx.xxx.x"))
    lv.on("stage2", stencil((86.0, 85.0), W.WHITE[5], 7, "x.xx.xx"))
    lv.on("stage3", stencil((122.0,), W.WHITE[5], 6, "xx.xx"))
    lv.on("bus", rivets(146.0)).on("skirt", rivets(5.5))
    lv.on("bell", soot).on("bell2", soot).on("bell3", soot)
    return save(rk, lv)


def cobalt() -> dict:
    """The cobalt re-entry vehicle's missile, an R-36M: a heavy liquid missile in the warhead's blue-black, two broad
    stages of one width and a small third under a conical bus, no fins (it rides out of its silo in a canister), the
    jacket's cobalt-blue bands repeated."""
    rk = Rocket("cobalt_warhead", "XXL", tex=512)
    R1, R3, RB = 11.6, 9.0, 7.2
    for dx, dz in ((-4.2, 0), (4.2, 0), (0, -4.2), (0, 4.2)):
        rk.bell("bell", 2.8, 2.0, 0.0, 3, cx=8 + dx, cz=8 + dz)
    rk.ring("skirt", R1 + 0.4, 3.0, 7.0, caps=("up", "down"), top=True, bottom=True)
    rk.closure("aft", R1 + 0.4, 3.0, inner=0.0)
    rk.tube("stage1", R1, 7.0, 66.0, on_joint=True)
    rk.joint("inter1", R1 + 0.35, 66.0, 2.0)
    rk.bell("bell2", 4.2, 3.0, 64.0, 2)
    rk.tube("stage2", R1, 68.0, 112.0, on_joint=True)
    rk.taper("inter2", R1 - 0.1, R3 + 0.35, 112.0, 4)
    rk.bell("bell3", 3.4, 2.4, 111.0, 3)
    rk.tube("stage3", R3, 116.0, 136.0)
    rk.taper("bus", R3 - 0.1, RB - 0.05, 136.0, 4)
    rk.tube("bus", RB - 0.25, 140.0, 148.0)
    rk.on_side("raceway", 0, R1, 8.0, 65.0, 0.8, 1.6)
    rk.on_side("raceway2", 0, R1, 69.0, 111.0, 0.8, 1.6)
    rk.mount(148.0)
    rk.parts, rk.stages, rk.engines = [66.0, 112.0, 148.0, rk.tip], [66.0, 112.0, rk.tip], [0.0, 64.0, 111.0]
    bb = paint(W.BLUE_BLACK, 0.55)
    lv = Livery(body=bb, stage1=bb, stage2=bb, stage3=bb, skirt=W.metal(W.GUNMETAL, 0.42), inter1=W.metal(W.GUNMETAL, 0.45),
                inter2=W.metal(W.BLACK, 0.42), bus=W.metal(W.GUNMETAL, 0.45), bell=NOZZLE, bell2=NOZZLE, bell3=NOZZLE,
                raceway=paint(W.BLUE_BLACK, 0.48), raceway2=paint(W.BLUE_BLACK, 0.48))
    cobaltb = W.paint(W.COBALT, 0.5, 0.2)
    for y in (58, 54, 104, 100, 130):
        lv.on(["stage1", "stage2", "stage3"], band(y, y + 1, cobaltb))
    lv.on("stage1", stencil((40.0, 39.0, 37.0), W.WHITE[4], 9, "xx.xxx.x"))
    lv.on("stage2", stencil((88.0, 87.0), W.WHITE[4], 8, "x.xx.xx"))
    lv.on("stage1", seam(30.0, 0.75)).on("stage2", seam(90.0, 0.75))
    lv.on("inter1", rivets(67.0)).on("bus", rivets(146.0)).on("skirt", rivets(5.0))
    lv.on("bell", soot).on("bell2", soot).on("bell3", soot)
    return save(rk, lv)


def tsar() -> dict:
    """The Tsar's missile, the UR-500 it was meant for: a first stage of a central oxidiser core ringed by six fuel
    tanks, each with its engine; a broad second stage and a third under the bomb; the Tsar's green, white bands."""
    rk = Rocket("tsar_warhead", "XXL", tex=512)
    Rc, Rt, d = 7.6, 4.0, 11.2
    rk.bell("bell", 2.6, 1.8, 0.0, 3)
    rk.ring("core", Rc, 3.4, 56.0, caps=("up", "down"), top=True, bottom=True)
    rk.closure("aft", Rc, 3.4)
    for i in range(6):
        a = math.radians(30 + 60 * i)
        cx, cz = 8 + d * math.sin(a), 8 + d * math.cos(a)
        rk.bell(f"obell{i}", 2.4, 1.6, 0.0, 3, cx=cx, cz=cz)
        rk.ring(f"tank{i}", Rt, 3.0, 50.0, caps=("up", "down"), top=True, bottom=True, cx=cx, cz=cz)
        rk.ring(f"dome{i}", Rt - 1.2, 50.0, 52.0, caps=("up",), top=True, cx=cx, cz=cz, stagger="full")
    rk.taper("inter1", Rc - 0.1, 8.6, 56.0, 3)
    rk.bell("bell2", 4.2, 3.0, 55.0, 3)
    rk.tube("stage2", 9.0, 59.0, 104.0)
    rk.taper("inter2", 8.9, 6.95, 104.0, 4)
    rk.bell("bell3", 3.4, 2.4, 103.0, 3)
    rk.tube("stage3", 6.6, 108.0, 136.0)
    rk.joint("joint", 6.9, 136.0)
    rk.tube("bus", 5.0, 137.0, 140.0, on_joint=True)
    rk.mount(140.0)
    rk.parts, rk.stages, rk.engines = [56.0, 104.0, 140.0, rk.tip], [56.0, 104.0, rk.tip], [0.0, 55.0, 103.0]
    green = paint(W.TSAR, 0.55)
    tanks = {f"tank{i}": green for i in range(6)}
    domes = {f"dome{i}": paint(W.TSAR, 0.5) for i in range(6)}
    obells = {f"obell{i}": NOZZLE for i in range(6)}
    lv = Livery(body=green, core=green, stage2=green, stage3=green, inter1=W.metal(W.BLACK, 0.42), inter2=W.metal(W.BLACK, 0.42),
                joint=RING, bus=W.metal(W.GUNMETAL, 0.45), bell=NOZZLE, bell2=NOZZLE, bell3=NOZZLE, **tanks, **domes, **obells)
    white = W.paint(W.WHITE, 0.75, 0.2)
    lv.on("core", band(44, 46, white)).on("stage2", band(94, 96, white)).on("stage3", band(128, 129, white))
    for i in range(6):
        lv.on(f"tank{i}", band(42, 44, white))
        a = math.radians(30 + 60 * i)
        lv.on(f"obell{i}", soot_at(8 + d * math.sin(a), 8 + d * math.cos(a)))
    lv.on("stage2", stencil((84.0, 83.0, 81.0), W.WHITE[5], 9, "xx.xxx.x"))
    lv.on("bell", soot).on("bell2", soot).on("bell3", soot)
    return save(rk, lv)


# --- The superweapons' ammunition (DESIGN.md §19.5) ---------------------------------------------------------------------------

#: A star of nine rows (China's red star on a broad body).
STAR9 = ["....R....", "...RRR...", "...RRR...", "RRRRRRRRR", ".RRRRRRR.", "..RRRRR..", "..RRRRR..", ".RRR.RRR.", ".R.....R."]


def scud_storm() -> dict:
    """A Toxin SCUD (the GLA's SCUD Storm fires nine; C&C Generals): an R-17 Scud as the game paints it, a white body
    under the toxin-green warhead, four green trapezoid fins on a riveted aft section, graphite vanes in its bell, black
    rings where its tanks join, the GLA's green band and black stencils; weathered, as kept in the open."""
    rk = Rocket("scud_storm_warhead", "M")
    R = 4.0
    rk.bell("bell", 2.6, 1.8, 0.0, 3)
    rk.vanes("vane", 2.0, 0.0)
    rk.ring("skirt", R + 0.4, 3.0, 13.0, caps=("up", "down"), top=True, bottom=True)
    rk.fins("fin", R + 0.4, 3.0, 10.0, 4.0, 4.6, thick=0.9)
    rk.closure("aft", R + 0.4, 3.0)
    rk.tube("body", R, 13.0, 29.0)
    rk.joint("joint", R + 0.3, 29.0)
    rk.tube("body", R, 30.0, 44.0, on_joint=True)
    rk.joint("joint", R + 0.3, 44.0)
    rk.tube("guidance", R, 45.0, 49.0, on_joint=True)
    rk.on_side("raceway", 90, R, 14.0, 43.0, 0.6, 1.0)
    rk.mount(49.0)
    rk.parts, rk.stages, rk.engines = [13.0, 49.0, rk.tip], [rk.tip], [0.0]
    white = paint(W.WHITE, 0.72, wear=0.16, grime=0.1)
    green = paint(W.SCUD_GREEN, 0.6, wear=0.18)
    lv = Livery(body=white, skirt=paint(W.WHITE, 0.66, wear=0.2, grime=0.14), fin=green, joint=W.metal(W.BLACK, 0.42), guidance=white,
                bell=NOZZLE, vane=W.metal(W.BLACK, 0.3), raceway=paint(W.WHITE, 0.62, wear=0.16))
    lv.on("body", band(40, 42, green))
    lv.on("body", stencil((37.0, 36.0, 34.0), W.BLACK[2], 5, "xx.xxx.x"))
    lv.on("body", stencil((22.0, 21.0), W.BLACK[2], 4, "x.xx"))
    lv.on("body", seam(21.5, 0.8))
    lv.on("body", patches(31, 0.04, W.WHITE[3], size=3.0))
    lv.on("skirt", rivets(11.5)).on("skirt", rivets(4.5)).on("joint", rivets(29.0)).on("joint", rivets(44.0))
    lv.on("guidance", rivets(47.5))
    lv.on("bell", soot)
    return save(rk, lv)


def nuclear_missile() -> dict:
    """China's Nuclear Missile (C&C Generals' superweapon): a DF-5, the two-stage liquid ICBM, in silver: four engines
    under a first stage as broad as the second, a bolted red interstage ring, a short taper up to the re-entry vehicle;
    the warhead's red repeated in bands, a big red star on the first stage, red serials down the second."""
    rk = Rocket("nuclear_missile_warhead", "XL")
    R1 = 7.0
    engines = [(8 + dx, 8 + dz) for dx, dz in ((-3.4, -3.4), (3.4, -3.4), (-3.4, 3.4), (3.4, 3.4))]
    for i, (cx, cz) in enumerate(engines):
        rk.bell(f"ebell{i}", 2.2, 1.5, 0.0, 3, cx=cx, cz=cz)
    rk.ring("skirt", R1 + 0.4, 3.0, 8.0, caps=("up", "down"), top=True, bottom=True)
    rk.closure("aft", R1 + 0.4, 3.0, inner=0.0)
    rk.tube("stage1", R1, 8.0, 68.0, on_joint=True)
    rk.joint("inter", R1 + 0.35, 68.0, 2.0)
    rk.bell("bell2", 3.6, 2.6, 66.0, 2)
    rk.tube("stage2", R1, 70.0, 110.0, on_joint=True)
    top = rk.taper("adapter", R1 - 0.1, 6.2, 110.0, 3)
    rk.on_side("raceway", 90, R1, 9.0, 67.0, 0.8, 1.6)
    rk.on_side("raceway2", 90, R1, 71.0, 109.0, 0.7, 1.4)
    rk.mount(top)
    rk.parts, rk.stages, rk.engines = [68.0, top, rk.tip], [68.0, rk.tip], [0.0, 66.0]
    silver = W.metal(W.STEEL, 0.64, brushed=0.06)
    red = W.paint(W.PLA_RED, 0.55, 0.15)
    lv = Livery(body=silver, stage1=silver, stage2=silver, skirt=W.metal(W.GUNMETAL, 0.45), inter=red, adapter=silver,
                bell2=NOZZLE, raceway=W.metal(W.STEEL, 0.55), raceway2=W.metal(W.STEEL, 0.55),
                **{f"ebell{i}": NOZZLE for i in range(len(engines))})
    lv.on("stage1", band(60, 62, red)).on("stage1", band(12, 13, red)).on("stage2", band(102, 104, red))
    lv.on("stage1", decal(STAR9, {"R": W.PLA_RED[4]}, 52.0))
    lv.on("stage2", stencil((96.0, 95.0, 94.0, 92.0, 91.0, 90.0), W.PLA_RED[3], 5, "xx.x.xx.xxx"))
    lv.on("stage1", stencil((36.0, 35.0, 33.0), W.BLACK[2], 7, "xx.xxx.x"))
    lv.on("stage1", seam(30.0, 0.78)).on("stage2", seam(88.0, 0.78))
    lv.on("inter", rivets(69.0)).on("skirt", rivets(5.5))
    for i, (cx, cz) in enumerate(engines):
        lv.on(f"ebell{i}", soot_at(cx, cz))
    lv.on("bell2", soot)
    return save(rk, lv)


# --- cruise missiles (slice 9c, DESIGN.md §17.11) ------------------------------------------------------------------------------

#: A cruise missile's length from its tail (the booster gone) to its nose's tip, at most (its box holds no more).
CRUISE_TIP = 79.0
#: Its booster: from its bell's mouth to the top of its case (the missile's tail sits on it, the jet's nozzle between).
BOOSTER_TOP = 12.8
CRUISE_TAIL = 13.0


def cruise(warhead: str, cls: str, R: float, body: M.Material, fin: M.Material, marks=None) -> dict:
    """A Light or Medium warhead's cruise missile (a Tomahawk's lines): the warhead's own head on a body its width, a
    boat-tail with the jet's nozzle and four small fins in an X, a pair of straight wings at mid-body (out: as it flies
    and as an item), the air scoop under its belly (+z: down in level flight) behind them; under its tail the solid
    booster that lights it out of its box and falls away (its first stage). Painted as the warhead's own missile:
    {body} and {fin}, and {marks}(lv, body's foot, warhead's foot) for its bands and stencils."""
    rk = Rocket(warhead + "_cruise", cls)
    rk.warhead = warhead
    head_h = max(el["to"][1] for el in warhead_json(warhead)["elements"])
    mount = max(48.0, CRUISE_TIP - head_h)
    # The booster: bell, case, its aft closure.
    exit_r = min(2.6, R * 0.55)
    rk.bell("bbell", exit_r, exit_r * 0.66, 0.0, 3)
    rk.ring("booster", R, 3.0, BOOSTER_TOP, caps=("up", "down"), top=True, bottom=True)
    rk.closure("baft", R, 3.0, inner=exit_r)
    # The missile's tail: the jet's nozzle on the booster, the boat-tail rising to the body, its fins in an X.
    rk.ring("nozzle", round(R * 0.45, 3), CRUISE_TAIL, 14.2, caps=("up", "down"), top=True, bottom=True, stagger="full")
    tail_top = rk.taper("tail", R * 0.62, R - 0.15, 14.2, 5)
    rk.tube("body", R, tail_top, mount)
    root = R * 0.7
    for b in (45, 135, 225, 315):
        rk.box("tfin", (8 - 0.3, 14.6, 8 + root), (8 + 0.3, 20.0, 8 + root + 5.4), faces=("east", "west", "south", "up", "down"),
               rotation={"origin": (8, 14.6, 8), "axis": "y", "angle": b})
    # Wings out at mid-body, square to its belly; the scoop under it behind them.
    wing = mount - 17.0
    for s in (-1, 1):
        x0, x1 = (8 + R - 0.4, 8 + R + 13.5) if s > 0 else (8 - R - 13.5, 8 - R + 0.4)
        rk.box("wing", (x0, wing, 8 - 0.35), (x1, wing + 5.0, 8 + 0.35),
               faces=("north", "south", "up", "down", "east" if s > 0 else "west"))
        rk.box("wroot", (8 + s * (R - 0.2) - 0.8, wing - 0.4, 8 - 1.0), (8 + s * (R - 0.2) + 0.8, wing + 5.4, 8 + 1.0),
               faces=("north", "south", "up", "down", "east" if s > 0 else "west"))
    rk.box("scoop", (8 - 1.8, wing - 11.0, 8 + R - 0.3), (8 + 1.8, wing - 3.0, 8 + R + 1.6), faces=("east", "west", "south", "up", "down"))
    rk.mount(mount)
    rk.parts, rk.stages, rk.engines = [CRUISE_TAIL, wing - 1.0, rk.tip], [CRUISE_TAIL, rk.tip], [0.0, CRUISE_TAIL]
    rk.part_stage = [0, 1, 1]
    rk.folds = {"wing", "scoop"}                                # (stowed in its box, out as it leaves: "_folded")
    lv = Livery(body=body, tail=body, fin=fin, tfin=fin, wing=fin, wroot=body, scoop=body, booster=W.metal(W.GUNMETAL, 0.36),
                baft=W.metal(W.GUNMETAL, 0.3), bbell=NOZZLE, nozzle=W.metal(W.BLACK, 0.34))
    lv.on("booster", hazard(BOOSTER_TOP - 2.0, BOOSTER_TOP - 0.6, width=0.8))
    lv.on("booster", stencil((6.0,), W.WHITE[5], 4, "x.xx"))
    lv.on("bbell", soot).on("nozzle", soot)
    lv.on("scoop", lambda k, f, img: M.darken(img, np.ones(f.shape, dtype=bool), 0.2) if f.d == "up" else None)   # its mouth
    lv.on("body", seam(tail_top + 1.0, 0.7)).on("body", seam(wing + 7.0, 0.72))
    if marks:
        marks(lv, tail_top, mount)
    return save(rk, lv)


def _he_cruise() -> dict:
    def marks(lv, foot, top):
        lv.on("body", band(top - 9, top - 8, W.paint(W.YELLOW, 0.625, 0.3)))
        lv.on("body", stencil((top - 4.0, top - 5.0), W.WHITE[5], 6, "xx.xxx.x"))
    return cruise("he_warhead", "S", 4.0, paint(W.OLIVE), paint(W.OLIVE, 0.42), marks)


def _napalm_cruise() -> dict:
    def marks(lv, foot, top):
        red = W.paint(W.RED, 0.625, 0.3)
        lv.on("body", band(top - 8, top - 6, red)).on("body", band(foot + 3, foot + 4, red))
        lv.on("body", stencil((top - 12.0, top - 13.0), W.BLACK[2], 5, "xx.xx"))
    return cruise("napalm_warhead", "S", 4.4, W.metal(W.STEEL, 0.62, brushed=0.06), W.metal(W.STEEL, 0.5), marks)


def _chemical_cruise() -> dict:
    def marks(lv, foot, top):
        green = W.paint(W.CHEM_GREEN, 0.5, 0.15)
        for y in (top - 6, top - 8, foot + 6, foot + 4):
            lv.on("body", band(y, y + 1, green))
        lv.on("body", stencil((top - 13.0, top - 14.0), W.BLACK[2], 5, "x.xxx"))
    return cruise("chemical_warhead", "S", 4.2, W.paint(W.WHITE, 0.625, CLEAN), W.paint(W.WHITE, 0.56, CLEAN), marks)


def _toxic_waste_cruise() -> dict:
    def marks(lv, foot, top):
        toxin = M.Material(ramp=W.ramp9(P.TOXIN), tone=0.62, mottle=0.05, grain=0.03, dither=0.4, edge_light=0, edge_dark=0, ao=0)
        lv.on("body", band(top - 7, top - 6, toxin))
        lv.on("body", drips(P.TOXIN.shades[4], top, 11))
        lv.on("body", hazard(foot + 1.0, foot + 4.0, width=1.0))
        lv.on("body", decal(W.TREFOIL, {"Y": W.YELLOW[4], "K": W.BLACK[1]}, top - 11.0))
    return cruise("toxic_waste_warhead", "S", 5.0, paint(W.WASTE, 0.5, rust=0.08), W.metal(W.RUST, 0.48, rust=0.2), marks)


def _dirty_cruise() -> dict:
    def marks(lv, foot, top):
        tape = W.paint(W.YELLOW, 0.625, 0.45)
        lv.on("body", band(top - 6, top - 4, tape)).on("body", band(foot + 2, foot + 3, tape))
        lv.on("body", decal(W.TREFOIL, {"Y": W.YELLOW[4], "K": W.BLACK[1]}, top - 10.0))
    return cruise("dirty_warhead", "S", 5.0, paint(W.RUST, 0.5, rust=0.2), W.metal(W.GUNMETAL, 0.42, rust=0.25), marks)


def _heavy_cruise() -> dict:
    def marks(lv, foot, top):
        yellow = W.paint(W.YELLOW, 0.625, 0.3)
        lv.on("body", band(top - 7, top - 6, yellow)).on("body", band(top - 9, top - 8, yellow))
        lv.on("body", stencil((top - 13.0, top - 14.0, top - 16.0), W.WHITE[5], 7, "xx.xxx.x"))
    return cruise("heavy_warhead", "M", 6.0, paint(W.OLIVE), paint(W.OLIVE, 0.42), marks)


def _cluster_cruise() -> dict:
    def marks(lv, foot, top):
        lv.on("body", band(top - 5, top - 4, W.paint(W.YELLOW, 0.625, 0.3)))
        lv.on("body", band(foot + 2, foot + 4, W.paint(W.BLACK, 0.5, CLEAN)))
        lv.on("body", stencil((top - 10.0, top - 11.0), W.BLACK[2], 6, "x.xx.xx"))
    return cruise("cluster_warhead", "M", 4.6, paint(W.CASING, 0.5), paint(W.CASING, 0.4), marks)


def _gas_cruise() -> dict:
    def marks(lv, foot, top):
        lv.on("body", band(top - 6, top - 5, W.paint(W.YELLOW, 0.66, 0.3)))
        lv.on("body", hazard(foot + 1.0, foot + 4.0))

        def dashes(k, f, img, y=int(top - 14)):
            if f.d in SIDES and (round(math.degrees(math.atan2(f.normal[0], f.normal[2])) / 22.5) % 2 == 0):
                img[W.facet_mid(f) & (np.floor(f.Y) == y)] = W.YELLOW[3]
        lv.on("body", dashes)
    return cruise("gas_warhead", "M", 4.6, paint(W.GAS), paint(W.GAS, 0.4), marks)


def _smoke_cruise() -> dict:
    def marks(lv, foot, top):
        white = W.paint(W.WHITE, 0.75, 0.2)
        for y in (top - 6, top - 8, foot + 3):
            lv.on("body", band(y, y + 1, white))
        lv.on("body", stencil((top - 13.0, top - 14.0), W.BLACK[2], 5, "x.xx.x"))
    return cruise("smoke_warhead", "M", 4.6, paint(W.STEEL, 0.5), paint(W.STEEL, 0.42), marks)


def _cryo_cruise() -> dict:
    def marks(lv, foot, top):
        lv.on("body", frost(foot, top))
        lv.on("body", band(top - 6, top - 5, W.paint(W.WHITE, 0.8, 0.15)))
        lv.on("body", stencil((top - 12.0, top - 13.0), W.WHITE[6], 5, "xx.x.x"))
    return cruise("cryo_warhead", "M", 4.6, paint(G.CRYO_CAN, 0.55), paint(W.WHITE, 0.68), marks)


#: Every missile, by its warhead, in the order the warheads are listed.
BUILDERS = {"he_warhead": he, "napalm_warhead": napalm, "chemical_warhead": chemical, "toxic_waste_warhead": toxic_waste,
            "dirty_warhead": dirty, "heavy_warhead": heavy, "cluster_warhead": cluster, "gas_warhead": gas, "smoke_warhead": smoke,
            "cryo_warhead": cryo, "thermobaric_warhead": thermobaric, "bunker_buster_warhead": bunker_buster, "emp_warhead": emp,
            "massive_warhead": massive, "tactical_nuclear_warhead": tactical, "fission_warhead": fission,
            "boosted_fission_warhead": boosted, "neutron_warhead": neutron, "thermonuclear_warhead": thermonuclear,
            "cobalt_warhead": cobalt, "tsar_warhead": tsar, "scud_storm_warhead": scud_storm, "nuclear_missile_warhead": nuclear_missile,
            # The cruise missiles (slice 9c): each Light and Medium warhead's, its look "<warhead>_cruise".
            "he_warhead_cruise": _he_cruise, "napalm_warhead_cruise": _napalm_cruise, "chemical_warhead_cruise": _chemical_cruise,
            "toxic_waste_warhead_cruise": _toxic_waste_cruise, "dirty_warhead_cruise": _dirty_cruise,
            "heavy_warhead_cruise": _heavy_cruise, "cluster_warhead_cruise": _cluster_cruise, "gas_warhead_cruise": _gas_cruise,
            "smoke_warhead_cruise": _smoke_cruise, "cryo_warhead_cruise": _cryo_cruise}


def _own(name: str, file_stem: str) -> bool:
    """Whether a model file ("he_warhead_s1") is a variant of missile {name}, not of another missile whose name runs on
    from it ("he_warhead_cruise", the warhead's cruise missile)."""
    return file_stem.startswith(name + "_") and not (not name.endswith("_cruise") and file_stem.startswith(name + "_cruise"))


def build(names=None) -> dict:
    table = json.loads(TABLE.read_text(encoding="utf-8")) if TABLE.exists() and names else {}
    for n in names or BUILDERS:
        table[n] = BUILDERS[n]()
    return {n: table[n] for n in BUILDERS if n in table}


def item_definition(table: dict) -> None:
    """The Missile item's model, picked by its custom model data: each warhead's missile (its id) and its variants (_s1,
    _stage1, _part1...); the looks older missiles were saved with ("strategic_<warhead>", "icbm_<warhead>", "flight_...")
    to the same; anything else (a missile with no warhead) the plain missile (tools/assets/launch.py)."""
    folder = paths.MODELS / "item" / "missile"

    def model(name):
        return {"type": "minecraft:model", "model": f"groundzero:item/{name}"}
    cases = []
    for n in table:
        cases.append({"when": [n] + [f"{pre}{n}" for pre in ("strategic_", "icbm_", "flight_", "flight_strategic_", "flight_icbm_")],
                      "model": model(f"missile/{n}")})
        for v in sorted(folder.glob(f"{n}_*.json")):
            look = v.name[:-5]
            if not _own(n, look):
                continue                                       # another missile's (the warhead's cruise missile)
            when = [look]
            if look.startswith(f"{n}_s") and look[len(n) + 2:].isdigit():   # the old flight looks of the stages gone
                k = look[len(n) + 2:]
                when += [f"{pre}{n}_s{k}" for pre in ("strategic_", "icbm_", "flight_strategic_", "flight_icbm_")]
            cases.append({"when": when, "model": model(f"missile/{look}")})
    write_json(paths.ASSETS / "items" / "missile.json", {"model": {
        "type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0, "cases": cases,
        "fallback": model("missile")}})


def main() -> None:
    names = sys.argv[1:] or None
    table = build(names)
    write_json(TABLE, table)
    item_definition(table)
    print(f"{len(table)} missiles in the table")


if __name__ == "__main__":
    main()
