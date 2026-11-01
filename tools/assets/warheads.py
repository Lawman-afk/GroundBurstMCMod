"""Warheads (DESIGN.md §14, §16, §17.6; Phase 6 slice 6d, bombs until they became missile warheads; remastered after
the Fission Reactor, DESIGN.md §4.4): block models standing nose up on their mounting ring, the way they wait to be
fitted to a missile. Each has an armed twin whose lamp blinks (see charges.py). The bigger ones stand taller than their
block (models may run up to y 32): the collision box stays within it. The front (stencils, lamp) is the model's south
side: the blockstate turns it to face the player.

How they are built (the remaster's research, §4.4): the shape is the model's and the detail the texture's. Round
sections are true 16-sided prisms (octagons when small), each the union of planks laid every 22.5 degrees, spanning
the width one way and one facet the other (overlapping square boxes would make a star): 26.x model elements turn by any
angle. Noses follow a real profile (a tangent ogive, a dome, a cone) a step at a time, so they read as a smooth
taper, not a ziggurat. Every type has its own silhouette and the details of its kind (an HE warhead's suspension
lugs, a dispenser's straps and clamshell split, a firebomb's filler cap, a re-entry vehicle's cone over its skirt),
painted with the material library (painted steel that wears at its real edges, bands, stencils and markings laid on
in model space so they run on round the facets). Markings follow the US colour codes where one applies: a yellow band
for high explosive, light red for incendiary, dark green for a toxic chemical.

- HE Warhead: olive drab, an ogive nose and nose fuze, a yellow band, two suspension lugs on its back.
- Heavy HE Warhead: a size up and much taller, two yellow bands, lugs and a hoisting lug.
- Thermobaric Warhead: stubby and fat, gunmetal, a blunt dome, a ring of fuel ports, two orange bands.
- Cluster Warhead: a dispenser canister: light grey, strapped, split down its sides, a yellow band, black dashes.
- Bunker Buster Warhead: long and slim, dark steel, a long sharp tungsten penetrator nose.
- Napalm Warhead: a silver firebomb tank, rounded at both ends, a filler cap, a light red band.
- Massive Ordnance Warhead: huge, olive drab, a blunt orange nose, lifting lugs, seams riveted round it.
- Poison Gas Warhead: a yellow-green dispenser canister of gas bomblets, a yellow band.
- Smoke Warhead: a grey dispenser canister of smoke canisters, a white band.
- Cryo Cluster Warhead: an ice-blue dispenser with frost on it, white bands, vent caps.
- EMP Cluster Warhead: a dark dispenser with a copper coil round its waist between ceramic insulators.
- Chemical Warhead: pale grey bulk-filled, two dark green bands, a filler plug and a steel nose.
- Toxic Waste Warhead: a dark drum with rolling hoops, a glowing toxin-green dome and band, hazard dashes.
- Tactical Nuclear Warhead: a squat olive cylinder, a blunt dark cap, a yellow band over a black one, a trefoil, an
  arming panel.
- Fission Warhead: a fat ochre body with a bolted equator ring, a rounded dark nose, a red band, a trefoil.
- Boosted Fission Warhead: sleeker and grey, a sharper dark nose, two cyan bands and its boost-gas reservoir.
- Neutron Warhead: slim and silver, three cyan rings, rows of reflector studs.
- Dirty Warhead: a rusty drum with hoops and a lid, yellow tape and a trefoil sticker.
- Thermonuclear Warhead (built whole into an intercontinental missile): a re-entry vehicle, a dark ablative cone on a
  drab skirt banded yellow, a trefoil.
- Cobalt Warhead: the same cone in blue-black, its cobalt jacket's plates banded cobalt blue.
- Tsar Mega-bomb (built whole): a fat dark-green bomb on four tail fins round its parachute pack, a rounded nose,
  white stencils ("50" for its megatons), a trefoil, four cooling vents glowing orange low on its body.
- Antimatter Warhead (Phase 11, DESIGN.md §16.3): a white ceramic capsule, no fins; three magnetic confinement rings,
  each a violet coil glowing between dark steel collars; a violet trefoil on yellow, a band of hazard chevrons, a dark
  cap round a glowing port on its dome.
- Singularity Warhead (Phase 11, DESIGN.md §16.2): a matte-black sphere held in a gyroscope of three hoops (dark steel
  outermost, bright steel, brass innermost, pinned together where they cross), its violet core glowing through a slit
  round its equator, on a cradle on its mounting ring.
- Cryogenic Warhead (DESIGN.md §16.16; built whole into its Cryo ICBM): a steel dewar rimed at its foot, a sight glass
  glowing ice blue round its waist between dark collars, an ice-blue band, four liquid nitrogen tanks strapped round it
  and piped in, a white domed lid and a relief valve's stack with a brass cap.
- Toxin SCUD Warhead (a superweapon's, DESIGN.md §19.5; built whole into its Toxin SCUD): the C&C Generals SCUD's
  toxin-green nose, a long ogive over a short green section, a black band and white stencils.
- Nuclear Missile Warhead (China's superweapon, built whole into its Nuclear Missile): a DF-5's re-entry vehicle, a long
  silver cone over a short skirt, its tip and a band red, a red star on its front and a trefoil on its back.
- Cluster Bomblet (item): a small yellow can with a ring of tail tabs and a ribbon.
- Napalm Fire: vanilla's fire, drawn on its own block.

A missile carries its warhead's own model on its nose: the game draws it there (WarheadNose), fitted to the missile's
body by launch.py's warhead_mounts, which measures each warhead with `mount` here. NOSES is the bare nose of a missile
with no warhead.

    tools\\.venv\\Scripts\\python.exe tools\\assets\\warheads.py
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
from gb.palette import fixed  # noqa: E402
from gb.tex import Tex  # noqa: E402
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
TSAR = fixed("tsar_green", "#0f160b", "#172111", "#1f2c17", "#29391e", "#344826", "#435b31", "#577340")
CANVAS = fixed("chute_canvas", "#2a2314", "#3d331d", "#534627", "#6a5a33", "#837042", "#9d8858", "#baa67a")
VENT = ((40, 12, 6, 255), (255, 112, 32, 255), (255, 196, 96, 255))   # the vents' slats, glow and hottest glow
RUST = fixed("waste_rust", "#2a1206", "#46200c", "#663014", "#86441c", "#a35a28", "#bd7638", "#d69a5c")
OCHRE = fixed("bomb_ochre", "#3a2a08", "#5e440e", "#846216", "#a8801e", "#c49c34", "#d8b85a", "#ecd690")
NUKE_NOSE = fixed("nuke_nose", "#121416", "#1c1f22", "#282c30", "#363b40", "#474d53", "#5c636a", "#788088")
DARK = fixed("penetrator_dark", "#16181a", "#202326", "#2c3034", "#3a3e44", "#4a4f56", "#5e646c", "#767c84")
ABLATIVE = fixed("ablative", "#110d0a", "#1a1410", "#241c16", "#30261e", "#3e3228", "#4e4034", "#625244")
FROST = fixed("frost", "#5a7884", "#7896a2", "#98b4be", "#b6ced6", "#cfe2e8", "#e4f0f4", "#f6fbfc")
CHEM_GREEN = fixed("chem_green", "#0e1a0e", "#152816", "#1d3820", "#264a2a", "#305c34", "#3c7040", "#4c864e")
SMOKE_GREEN = fixed("smoke_green", "#2a3a1c", "#3c5228", "#506c36", "#668646", "#7ea05a", "#98b874", "#b8d29a")
#: The GLA's toxin green (the SCUD's nose and fins, C&C Generals), and China's red.
SCUD_GREEN = fixed("scud_green", "#0d1f07", "#15330b", "#1f4b11", "#2b6518", "#3a8222", "#4f9f30", "#71bc4c")
#: The antimatter warhead's confinement glow.
#: The singularity's matte black, a hint of blue-violet in its lights.
VOID = fixed("void_black", "#040407", "#08080d", "#0d0d14", "#13131c", "#1a1a26", "#232333", "#2e2e42")
VIOLET = fixed("confinement_violet", "#16082c", "#2a0f52", "#45198a", "#6427c2", "#8a4ae8", "#b182ff", "#dcc8ff")
PLA_RED = fixed("pla_red", "#2e0604", "#4c0a06", "#6e1009", "#92180c", "#b42412", "#d23c22", "#e8664a")
GREEN = P.INDICATOR_GREEN
WHITE = P.WHITE_CERAMIC
CYAN = P.CHERENKOV

#: The trefoil, five pixels square: black blades (K) on a yellow ground.
TREFOIL = ["YYYYY", "YKYKY", "YYKYY", "YYKYY", "YYYYY"]
#: A five-pointed star (China's red star).
STAR = ["..R..", ".RRR.", "RRRRR", ".RRR.", "R...R"]
SIDES = ("north", "south", "east", "west")


# --- palettes as materials ---------------------------------------------------------------------------------------------------

def ramp9(r) -> np.ndarray:
    """A palette ramp in nine shades (fine steps for a curved, painted surface)."""
    base = K.ramp_of(r)
    n = len(base)
    return np.stack([np.interp(np.linspace(0, n - 1, 9), np.arange(n), base[:, c]) for c in range(3)], axis=1).astype(np.float32)


#: A round body's faces alike all round (the game lights its facets), its ledges a touch darker than its sides (the
#: game draws tops brighter), so a stepped nose reads as a taper.
ROUND = {"up": -0.05, "down": -0.18, "north": 0.0, "south": 0.0, "east": 0.0, "west": 0.0}


def paint(r, tone: float = 0.5, wear: float = 0.22, **kw) -> M.Material:
    """Ordnance paint: smooth, worn through at the real edges, a little grime low down; its tone on a shade."""
    d = dict(grime=0.06, streaks=0.0, falloff=0.03, dust=0.0, mottle=0.02, grain=0.02, dither=0.35, facing=ROUND)
    d.update(kw)
    return M.painted(ramp9(r), tone=tone, wear=wear, **d)


def metal(r, tone: float = 0.5, **kw) -> M.Material:
    """Bare metal: fuzes, rings, lugs, the penetrator's nose."""
    d = dict(mottle=0.03, grain=0.02, dither=0.35, edge_light=0.18, edge_dark=0.12, ao=0.4, grime=0.04, dust=0.0, brushed=0.03,
             facing=ROUND)
    d.update(kw)
    return M.Material(ramp=ramp9(r), tone=tone, **d)


# --- round geometry ----------------------------------------------------------------------------------------------------------

def section(m: BlockModel, name: str, r: float, y0: float, y1: float, sides: int = 16, cx: float = 8.0, cz: float = 8.0,
            caps=("up", "down"), stagger: str | None = None, step: float = 0.06) -> list[str]:
    """A round section: a prism of {sides} sides (16, 8, or 4: a square) and apothem r. It is the union of planks
    laid every 360/sides degrees (and each turned a quarter more), each spanning the width one way and one facet the
    other (squares overlapped would make a star), so each plank shows only its two ends (two facets) and its caps.

    No two caps may lie in one plane where they show (they would flicker). Planks side by side round the section meet
    only near its rim, so every other plank is sunk 0.06 px ("alternate"); planks a quarter turn apart meet only near
    the middle, which the ring above covers. A small section, or one whose top shows, sinks each plank a little more
    than the last ("full"). A section whose bottom shows with nothing under its middle (a missile's tail) sinks the
    quarter-turned planks 0.12 further ("base"), so no two of its bottom caps share a plane anywhere. A plank is sunk
    whole, never stretched, so its texels stay a pixel tall and bands line up
    from facet to facet. A section with no bottom cap stands on something wider: its planks reach a pixel down into
    it (unseen), so no sunk cap under them leaves a slit. {step} is the "alternate" sink: big structures, seen from
    further off, pass more (0.15) so the depth buffer still tells the two levels apart there."""
    if sides == 4:
        root = 0.0 if "down" in caps else 1.0
        m.box(f"{name}_0", (cx - r, y0 - root, cz - r), (cx + r, y1, cz + r), faces=tuple(SIDES) + tuple(caps))
        _open(m, f"{name}_0", [d for d in ("up", "down") if d not in caps])
        return [f"{name}_0"]
    stagger = stagger or ("alternate" if sides == 16 else "full")
    s = r * math.tan(math.pi / sides)
    root = 0.0 if "down" in caps else 1.0
    names = []
    k = 0
    for i, a in enumerate([j * 360.0 / sides for j in range(sides // 4)]):
        for long_x in (True, False):
            hx, hz = (r, s) if long_x else (s, r)
            index = i if long_x else i + sides // 4                  # the plank's place round the section, in facets
            if stagger == "base":                            # (a bottom that shows: the quarter-turned planks lower still)
                sink = 0.06 * (index % 2) + (0.12 if not long_x else 0.0)
            else:
                sink = step * (index % 2) if stagger == "alternate" else 0.06 * k
            ends = ("east", "west") if long_x else ("north", "south")
            rot = None if a == 0 else {"origin": (cx, y0, cz), "axis": "y", "angle": a}
            n = f"{name}_{k}"
            m.box(n, (cx - hx, y0 - sink - root, cz - hz), (cx + hx, y1 - sink, cz + hz), faces=ends + tuple(caps), rotation=rot)
            _open(m, n, [d for d in ("north", "south", "east", "west", "up", "down") if d not in ends and d not in caps])
            names.append(n)
            k += 1
    return names


def _open(m: BlockModel, box: str, faces) -> None:
    """Faces a section leaves out on purpose (inside it, or under what it stands on): not a hole to close."""
    for d in faces:
        m.open_ok.add((box, d))


def sides_for(r: float) -> int:
    """Enough sides to look round, none narrower than a pixel: 16 from r 2.5, 8 from r 1.2, else a square."""
    return 16 if r >= 2.5 else 8 if r >= 1.2 else 4


def profile(shape: str, x: float, R: float, L: float) -> float:
    """A nose's radius at x (0 at its base, 1 at its point) for base radius R and length L: a tangent ogive (the
    classic ordnance nose), a dome (an ellipse: blunt), "blunt" (between them) or a cone (a re-entry vehicle)."""
    if shape == "cone":
        return R * (1 - x)
    if shape == "dome":
        return R * math.sqrt(max(0.0, 1 - x * x))
    if shape == "blunt":
        return 0.5 * (profile("ogive", x, R, L) + profile("dome", x, R, L))
    rho = (R * R + L * L) / (2 * R)
    return math.sqrt(max(0.0, rho * rho - (x * L) ** 2)) + R - rho


def nose(m: BlockModel, name: str, R: float, y0: float, L: float, shape: str, steps: int, min_r: float = 0.7) -> tuple[list[list[str]], float, float]:
    """A nose of up to {steps} rings on a body of radius R from y0, {L} long: each ring the profile's radius at its
    middle and at least 0.12 px inside the ring under it (it reaches a pixel down into that ring, unseen: rings of one
    radius would put their sides in one plane and flicker). Where the profile thins below {min_r} the nose ends and its
    last ring runs on to the top. Returns the rings (bottom up), the nose's top and its last ring's radius."""
    radii = []
    last = R
    for i in range(steps):
        r = min(profile(shape, (i + 0.5) / steps, R, L), last - 0.12)
        if r < min_r and radii:
            break
        radii.append(max(r, min_r))
        last = radii[-1]
    rings = []
    for i, r in enumerate(radii):
        a = y0 + L * i / steps
        b = y0 + L * (i + 1) / steps if i + 1 < len(radii) else y0 + L        # (whole-pixel steps: L / steps = 1 or 2)
        above = radii[i + 1] if i + 1 < len(radii) else 0.0
        stagger = "alternate" if sides_for(r) == 16 and above >= 0.55 * r else "full"
        rings.append(section(m, f"{name}{i}", round(r, 3), round(a, 3), round(b, 3), sides_for(r), caps=("up",), stagger=stagger))
    return rings, y0 + L, radii[-1]


# --- painting round things -----------------------------------------------------------------------------------------------------

def round_faces(S: M.Surfacer, names, top: bool = True, bottom: bool = False, foot: bool = True) -> None:
    """Readies a section's faces for painting: its sides run on round it (no lip at a facet's sides); its top and
    bottom are lit or shaded lips only where it really ends (a ledge); one standing on something gathers grime at its
    foot. A cap's long sides run under the other planks' caps; its ends are the rim."""
    for n in names:
        b = S.m.get(n)
        long_x = (b.to[0] - b.frm[0]) >= (b.to[2] - b.frm[2])
        for d in list(b.faces):
            f = S.face(b, d)
            inf = {k: np.full_like(v, np.inf) for k, v in f.border.items()}
            if d in ("up", "down"):
                for k in (("top", "bottom") if long_x else ("left", "right")):
                    f.edge[k] = inf[k]
            else:
                f.edge["left"], f.edge["right"] = inf["left"], inf["right"]
                if not top:
                    f.edge["top"] = inf["top"]
                if not bottom:
                    f.edge["bottom"] = inf["bottom"]
            f.stop = {k: v.copy() for k, v in f.edge.items()}
            if foot and d in SIDES:
                f.stop["bottom"] = f.border["bottom"].copy()


def angle_of(f: M.Face) -> np.ndarray:
    """Each texel's bearing round the warhead's axis in degrees: 0 the front (south, +z), 90 east (+x)."""
    return np.degrees(np.arctan2(f.X - 8.0, f.Z - 8.0))


def front(f: M.Face, spread: float = 0.5) -> bool:
    """Whether a face looks toward the front (south)."""
    return f.d in SIDES and f.normal[2] > spread


def decal(f: M.Face, img: np.ndarray, rows: list[str], colours: dict, top: float, cx: float = 8.0) -> None:
    """A small picture laid on the front facets (its top row at height {top}), by each texel's place across the front
    (x) and height (y): rows of characters, each a colour or '.' for none."""
    if not front(f):
        return
    h, w = len(rows), len(rows[0])
    col = np.floor(f.X - (cx - w / 2)).astype(int)
    row = np.floor(top - f.Y).astype(int)
    inside = (col >= 0) & (col < w) & (row >= 0) & (row < h)
    for ch, c in colours.items():
        mask = np.zeros(f.shape, dtype=bool)
        for j, line in enumerate(rows):
            for i, cc in enumerate(line):
                if cc == ch:
                    mask |= inside & (row == j) & (col == i)
        img[mask] = c


def stencil(f: M.Face, img: np.ndarray, ys, colour, width: int = 6, pattern: str = "xx.xxx.x") -> None:
    """Stencilled lines of small marks across the front at heights {ys}: a serial, a fill code, handling marks."""
    if not front(f):
        return
    for k, y in enumerate(ys):
        marks = (pattern[k % len(pattern):] + pattern)[:width]
        col = np.floor(f.X - (8 - width / 2)).astype(int)
        on = (np.floor(f.Y) == math.floor(y)) & (col >= 0) & (col < width)
        for i, ch in enumerate(marks):
            if ch == "x":
                img[on & (col == i)] = colour


def bolts(f: M.Face, img: np.ndarray, y: float, colour=None) -> None:
    """A bolt head at the middle of each facet at height y (a ring of them round a section)."""
    if f.d not in SIDES:
        return
    h, w = f.shape
    row = np.floor(f.Y) == math.floor(y)
    mid = np.zeros(f.shape, dtype=bool)
    mid[:, w // 2] = True
    on = row & mid
    M.lighten(img, on, 1.35, 16) if colour is None else img.__setitem__(on, colour)
    M.darken(img, np.roll(on, 1, axis=0) & ~on, 0.75)


class Build:
    """One warhead's model: its parts by group (the painter's groups), and its round sections."""

    def __init__(self, name: str, tex: int = 128):
        self.m = BlockModel(name, tex_size=(tex, tex), kind="block")
        self.m.open_ok = set()
        self.last_r: float | None = None                       # the radius of the last nose ring (a tip goes inside it)
        self.groups: dict[str, list[str]] = {}
        self.round: dict[str, tuple[bool, bool, bool]] = {}       # a round section's plank -> (top lip, bottom lip, grime at foot)

    def add(self, group: str, names, top: bool = True, bottom: bool = False, foot: bool = True) -> list[str]:
        self.groups.setdefault(group, []).extend(names)
        for n in names:
            self.round[n] = (top, bottom, foot)
        return list(names)

    def ring(self, group: str, r: float, y0: float, y1: float, caps=("up", "down"), top=True, bottom=False, foot=True, sides=None,
             stagger=None):
        return self.add(group, section(self.m, f"{group}{len(self.groups.get(group, []))}", r, y0, y1, sides or sides_for(r), caps=caps,
                                       stagger=stagger), top, bottom, foot)

    def nose(self, group: str, R: float, y0: float, L: float, shape: str, steps: int, min_r: float = 0.7) -> float:
        rings, top, self.last_r = nose(self.m, group, R, y0, L, shape, steps, min_r)
        for names in rings:
            self.add(group, names, False, False, False)
        return top

    def box(self, group: str, frm, to, **kw) -> str:
        n = f"{group}{len(self.groups.get(group, []))}"
        self.m.box(n, frm, to, **kw)
        self.groups.setdefault(group, []).append(n)
        return n

    def lamp(self, rb: float, h: float) -> None:
        """The armed lamp on the front of the mounting ring."""
        C.lamp(self.m, (7.4, h * 0.25, 8 + rb), (8.6, h * 0.25 + 0.8, 8 + rb + 0.35), faces=("north", "south", "east", "west", "up", "down"))

    def layout(self) -> None:
        while True:
            try:
                self.m.layout()
                return
            except ValueError:
                self.m.tex_w *= 2
                self.m.tex_h = self.m.tex_w

    def kit(self) -> K.Kit:
        k = K.Kit(self.m, {}, seed=sum(map(ord, self.m.name)) % 997, ao_reach=2.0, turned=True)
        for n, (top, bottom, foot) in self.round.items():
            round_faces(k.S, [n], top, bottom, foot)
        return k


# --- common parts --------------------------------------------------------------------------------------------------------------

def mounting_ring(w: Build, r: float, h: float) -> None:
    """The ring a warhead stands on and bolts to its missile by: gunmetal, a bolt at each facet."""
    w.ring("base", r, 0, h, caps=("up", "down"), top=True, bottom=False, foot=True)
    w.lamp(r, h)


def lugs(w: Build, r: float, ys, side: str = "north") -> None:
    """Suspension lugs on the warhead's back (or a hoisting lug on its side): a loop standing off the body at each
    height, as a bomb's lugs are threaded into its case."""
    for y in ys:
        if side == "north":
            z0, z1 = 8 - r - 1.2, 8 - r + 0.3
            w.box("lug", (7.2, y, z0), (7.7, y + 1.2, z1))
            w.box("lug", (8.3, y, z0), (8.8, y + 1.2, z1))
            w.box("lug", (7.2, y + 1.2, z0), (8.8, y + 1.7, z1))
        else:
            x0, x1 = 8 + r - 0.3, 8 + r + 1.2
            w.box("lug", (x0, y, 7.2), (x1, y + 1.2, 7.7))
            w.box("lug", (x0, y, 8.3), (x1, y + 1.2, 8.8))
            w.box("lug", (x0, y + 1.2, 7.2), (x1, y + 1.7, 8.8))


def straps(w: Build, r: float, ys) -> None:
    """A dispenser's straps: bands standing half a pixel proud, holding its clamshell halves shut."""
    for y in ys:
        w.ring("strap", r + 0.5, y, y + 1, caps=("up", "down"), top=True, bottom=True, foot=False)


def tip(w: Build, r: float, y0: float, h: float, group: str = "tip") -> float:
    """A fuze or cap on the nose: at least 0.12 px inside the ring under it (it reaches down into it), so their sides
    never share a plane. Each tip after the first goes inside the one before."""
    if w.last_r is not None:
        r = min(r, w.last_r - 0.12)
    w.ring(group, round(r, 3), y0, y0 + h, caps=("up",), top=True, foot=False)
    w.last_r = r
    return y0 + h


def finish_model(m: BlockModel) -> None:
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


# --- the painter -----------------------------------------------------------------------------------------------------------------

class Look:
    """How a warhead is painted: a material per group, and overlays (bands, stencils, decals) per group."""

    def __init__(self, **materials: M.Material):
        self.materials = materials
        self.overlays: dict[str, list] = {}

    def on(self, group: str, fn) -> "Look":
        self.overlays.setdefault(group, []).append(fn)
        return self


def band(k: K.Kit, f, img, y0: float, y1: float, material: M.Material) -> None:
    """A painted band round a section over the texel rows from height y0 up to y1 (whole pixels: every facet's rows
    line up, so the band runs straight round)."""
    if f.d in SIDES:
        row = np.floor(f.Y + 0.1)
        M.paste(img, k.on(f, material), (row >= math.floor(y0)) & (row < math.ceil(y1)))


def facet_mid(f) -> np.ndarray:
    """The middle column of a facet (where a seam, a dash or a bolt sits on it)."""
    mid = np.zeros(f.shape, dtype=bool)
    mid[:, f.shape[1] // 2] = True
    return mid


def save(w: Build, look: Look) -> list[str]:
    finish_model(w.m)
    w.layout()

    def painter(m: BlockModel, t: Tex, lamp_colour) -> None:
        k = w.kit()
        for group, names in w.groups.items():
            mat = look.materials.get(group) or look.materials.get("body")
            k.paint(t, names, mat)
            for fn in look.overlays.get(group, []):
                k.overlay(t, names, lambda f, img, fn=fn: fn(k, f, img))
        C.paint_lamp(m, t, lamp_colour)
    return C.save(w.m, painter)


# --- the warheads ----------------------------------------------------------------------------------------------------------------

BASE = metal(GUNMETAL, 0.5)
FUZE = metal(STEEL, 0.62)
BRASS = metal(P.BRASS, 0.56)
LUG = metal(STEEL, 0.5)


def he_family(name: str, r: float, body_top: float, nose_len: float, steps: int, bands, lug_ys, body_ramp=OLIVE, nose_ramp=OLIVE,
              band_ramp=YELLOW, hoist: bool = False, seams=(), nose_shape: str = "ogive", tip_r: float = 1.0) -> list[str]:
    """A general-purpose high-explosive warhead: the low-drag bomb shape (a long body under a tangent-ogive nose with a
    nose fuze), olive drab with a yellow band for its explosive fill, suspension lugs on its back."""
    w = Build(name)
    hb = 2.0
    mounting_ring(w, r + 0.75, hb)
    w.ring("body", r, hb, body_top, caps=("up",), top=False)
    top = w.nose("nose", r, body_top, nose_len, nose_shape, steps)
    top = tip(w, tip_r, top, 1.2, "fuze")
    tip(w, max(0.6, tip_r * 0.6), top, 0.6, "fuzecap")
    lugs(w, r, lug_ys)
    if hoist:                                                  # a hoisting lug at the balance point, on its east side
        lugs(w, r, (round((hb + body_top) / 2 - 0.8, 1),), side="east")
    look = Look(body=paint(body_ramp, 0.5), nose=paint(nose_ramp, 0.5), base=BASE, fuze=BRASS, fuzecap=FUZE, lug=LUG)
    for y0, y1 in bands:
        look.on("body", lambda k, f, img, y0=y0, y1=y1: band(k, f, img, y0, y1, paint(band_ramp, 0.625, 0.3)))
    for y in seams:
        look.on("body", lambda k, f, img, y=y: (M.darken(img, (np.floor(f.Y) == math.floor(y)) & (f.d in SIDES), 0.62),
                                                  bolts(f, img, y + 1.5)))
    look.on("body", lambda k, f, img: stencil(f, img, (hb + 3.5, hb + 2.5), WHITE[5]))
    look.on("base", lambda k, f, img: bolts(f, img, hb * 0.6))
    return save(w, look)


def dispenser(name: str, body_ramp, band_ramp, dash_colour, seam_colour, r: float = 4.6, extra=None, look_extra=None,
              bands=((10, 11),), body_top: float = 11.0) -> list[str]:
    """A dispenser canister (a cluster warhead's: the clamshell that splits open at height to scatter its load): a
    strapped canister split down both sides, an ogive nose with a fuze."""
    w = Build(name)
    hb = 2.0
    mounting_ring(w, r + 0.7, hb)
    w.ring("body", r, hb, body_top, caps=("up",), top=False)
    straps(w, r, (4.0, 8.4))
    top = w.nose("nose", r, body_top, 5.0, "ogive", 5)
    top = tip(w, 0.9, top, 1.0, "fuze")
    if extra:
        extra(w, r, body_top)
    look = Look(body=paint(body_ramp, 0.5), nose=paint(body_ramp, 0.5), base=BASE, fuze=FUZE, strap=metal(STEEL, 0.44), lug=LUG)

    def split(k, f, img):                                      # the clamshell's split, down the middle of each side facet
        if f.d in SIDES and abs(f.normal[0]) > 0.95:
            img[facet_mid(f)] = seam_colour
    look.on("body", split)
    for y0, y1 in bands:
        look.on("body", lambda k, f, img, y0=y0, y1=y1: band(k, f, img, y0, y1, paint(band_ramp, 0.625, 0.3)))

    def dashes(k, f, img):                                     # a mark on every other facet: the load's code
        if f.d in SIDES and abs(f.normal[0]) < 0.95 and (round(math.degrees(math.atan2(f.normal[0], f.normal[2])) / 22.5) % 2 == 0):
            img[facet_mid(f) & (np.floor(f.Y + 0.1) == 6)] = dash_colour
    look.on("body", dashes)
    look.on("body", lambda k, f, img: stencil(f, img, (3.0,), WHITE[5], 4, "x.xx"))
    look.on("base", lambda k, f, img: bolts(f, img, hb * 0.6))
    if look_extra:
        look_extra(look)
    return save(w, look)


def nuclear(name: str, r: float, body_top: float, nose_len: float, steps: int, body_ramp, bands, shape: str = "blunt",
            trefoil_y: float = 6.5, extra=None, look_extra=None, cap_r: float = 1.2, nose_ramp=NUKE_NOSE, hb: float = 2.0) -> list[str]:
    """A nuclear warhead's physics package in its case: a round body under a dark nose, bands, a trefoil on its front."""
    w = Build(name)
    mounting_ring(w, r + 0.7, hb)
    w.ring("body", r, hb, body_top, caps=("up",), top=False)
    top = w.nose("nose", r, body_top, nose_len, shape, steps)
    tip(w, cap_r, top, 0.8, "fuze")
    if extra:
        extra(w, r, body_top)
    look = Look(body=paint(body_ramp, 0.5), nose=paint(nose_ramp, 0.5, 0.15), base=BASE, fuze=FUZE, lug=LUG)
    for y0, y1, ramp in bands:
        look.on("body", lambda k, f, img, y0=y0, y1=y1, ramp=ramp: band(k, f, img, y0, y1, paint(ramp, 0.625, 0.25)))
    look.on("body", lambda k, f, img: decal(f, img, TREFOIL, {"Y": YELLOW[4], "K": BLACK[1]}, trefoil_y + 2.5))
    look.on("base", lambda k, f, img: bolts(f, img, hb * 0.6))
    if look_extra:
        look_extra(look)
    return save(w, look)


def build_all() -> list[str]:
    names = []
    # --- conventional ---
    names += he_family("he_warhead", 4.0, 9.0, 6.0, 6, [(7, 8)], (3.4, 6.2))
    names += he_family("heavy_warhead", 6.0, 15.0, 10.0, 10, [(13, 14), (11, 12)], (5.0, 10.0), hoist=True, tip_r=1.3)
    names += he_family("massive_warhead", 7.2, 19.0, 10.0, 10, [(17, 18)], (6.0, 13.0), nose_ramp=ORANGE, band_ramp=ORANGE,
                       hoist=True, seams=(8.0, 14.0), nose_shape="blunt", tip_r=1.5)
    names += thermobaric()
    names += bunker_buster()
    names += napalm()
    # --- dispensers ---
    names += dispenser("cluster_warhead", CASING, YELLOW, BLACK[3], BLACK[2])
    names += dispenser("gas_warhead", GAS, YELLOW, YELLOW[3], BLACK[2])
    names += dispenser("smoke_warhead", STEEL, WHITE, WHITE[4], BLACK[2])
    names += dispenser("cryo_warhead", G.CRYO_CAN, WHITE, WHITE[5], G.CRYO_CAN[0], extra=cryo_vents,
                       look_extra=lambda look: look.on("body", frost), bands=((10, 11), (2, 3)))
    names += dispenser("emp_warhead", EMP_CASING, P.COPPER, P.COPPER[4], BLACK[1], extra=emp_coil,
                       look_extra=lambda look: look.materials.update(coil=metal(P.COPPER, 0.56), insulator=paint(WHITE, 0.75, 0.1)))
    names += chemical()
    names += toxic_waste()
    # --- nuclear ---
    names += nuclear("tactical_nuclear_warhead", 4.0, 10.0, 4.0, 4, OLIVE, [(9, 10, YELLOW), (8, 9, BLACK)], "dome",
                     trefoil_y=5.0, extra=arming_panel, look_extra=lambda look: look.materials.update(panel=paint(GUNMETAL, 0.4)))
    names += nuclear("fission_warhead", 6.2, 11.0, 7.0, 7, OCHRE, [(9, 10, RED)], "blunt", trefoil_y=4.8, extra=equator,
                     look_extra=lambda look: look.materials.update(equator=metal(GUNMETAL, 0.45)), cap_r=1.4)
    names += nuclear("boosted_fission_warhead", 5.6, 12.0, 8.0, 8, BOOST_GREY, [(10, 11, CYAN), (8, 9, CYAN)], "ogive",
                     trefoil_y=6.0, extra=reservoir, look_extra=lambda look: look.materials.update(tank=metal(STEEL, 0.55)), cap_r=1.0)
    names += nuclear("neutron_warhead", 3.2, 13.0, 6.0, 6, STEEL, [(11, 12, CYAN), (8, 9, CYAN), (5, 6, CYAN)], "ogive",
                     trefoil_y=4.0, look_extra=lambda look: look.on("body", studs), cap_r=0.7, hb=1.5)
    names += dirty()
    names += reentry("thermonuclear_warhead", ABLATIVE, DRAB, YELLOW, None)
    names += reentry("cobalt_warhead", BLUE_BLACK, BLUE_BLACK, COBALT, COBALT)
    names += tsar()
    # --- exotic (Phase 11, DESIGN.md §16) ---
    names += antimatter()
    names += singularity()
    names += storm()
    names += cryogenic()
    # --- the superweapons' (DESIGN.md §19.5) ---
    names += scud_storm()
    names += nuclear_missile()
    return names


# --- the conventional ones with their own shapes -----------------------------------------------------------------------------

def thermobaric() -> list[str]:
    """Stubby and fat, a blunt dome over a ring of fuel ports (the fuel-air charge disperses before it lights), two
    orange bands."""
    w = Build("thermobaric_warhead")
    r, hb = 5.6, 2.0
    mounting_ring(w, r + 0.7, hb)
    w.ring("body", r, hb, 9.0, caps=("up",), top=False)
    top = w.nose("nose", r, 9.0, 5.0, "dome", 5)
    tip(w, 1.6, top - 0.2, 0.9, "fuze")
    for bearing in (45, 135, 225, 315):                       # fuel ports round the shoulder, between the facets' axes
        a = math.radians(bearing)
        cx, cz = 8 + math.sin(a) * (r - 0.2), 8 + math.cos(a) * (r - 0.2)
        w.box("port", (cx - 0.8, 7.2, cz - 0.8), (cx + 0.8, 8.6, cz + 0.8),
              rotation={"origin": (cx, 7.2, cz), "axis": "y", "angle": bearing})
    look = Look(body=paint(GUNMETAL, 0.5), nose=paint(GUNMETAL, 0.5), base=BASE, fuze=FUZE, port=metal(BLACK, 0.4))
    for y0, y1 in ((4, 5), (6, 7)):
        look.on("body", lambda k, f, img, y0=y0, y1=y1: band(k, f, img, y0, y1, paint(ORANGE, 0.625, 0.3)))
    look.on("body", lambda k, f, img: stencil(f, img, (8.0,), WHITE[5], 5, "xx.xx"))
    look.on("base", lambda k, f, img: bolts(f, img, 1.2))
    return save(w, look)


def bunker_buster() -> list[str]:
    """Long and slim: a thick-walled dark steel case under a long, sharp tungsten penetrator nose, a yellow band near
    its tail (the fuze is in the tail)."""
    w = Build("bunker_buster_warhead")
    r, hb = 2.7, 1.5
    mounting_ring(w, r + 0.6, hb)
    w.ring("body", r, hb, 13.0, caps=("up",), top=False)
    top = w.nose("nose", r, 13.0, 10.0, "ogive", 10, min_r=0.5)
    tip(w, 0.5, top, 0.8, "fuze")
    look = Look(body=paint(DARK, 0.5, 0.18), nose=metal(TUNGSTEN, 0.56), base=BASE, fuze=metal(TUNGSTEN, 0.7))
    look.on("body", lambda k, f, img: band(k, f, img, 3, 4, paint(YELLOW, 0.625, 0.3)))
    look.on("body", lambda k, f, img: stencil(f, img, (6.0, 5.0), WHITE[4], 3, "xx.x"))
    look.on("base", lambda k, f, img: bolts(f, img, 0.9))
    return save(w, look)


def napalm() -> list[str]:
    """A firebomb tank (the Mk 77's): unfinned sheet metal rounded at both ends, silver, a filler cap on its shoulder,
    the light red band of an incendiary."""
    w = Build("napalm_warhead")
    r, hb = 4.4, 1.5
    mounting_ring(w, r + 0.6, hb)
    w.ring("body", r, hb, 8.0, caps=("up",), top=False)
    top = w.nose("nose", r, 8.0, 5.0, "dome", 5)
    tip(w, 0.9, top - 0.3, 0.8, "fuze")                       # the igniter
    w.ring("cap", 1.0, 11.0, 12.6, caps=("up",), top=True, foot=False, sides=8)                   # the filler cap
    for n in w.groups["cap"]:                                 # off to one side of the dome
        b = w.m.get(n)
        b.frm = (b.frm[0] + 2.2, b.frm[1], b.frm[2] - 0.9)
        b.to = (b.to[0] + 2.2, b.to[1], b.to[2] - 0.9)
        if b.rotation:
            o = b.rotation["origin"]
            b.rotation["origin"] = (o[0] + 2.2, o[1], o[2] - 0.9)
    look = Look(body=metal(STEEL, 0.62, brushed=0.06), nose=metal(STEEL, 0.62, brushed=0.06), base=BASE, fuze=metal(RED, 0.5),
                cap=metal(GUNMETAL, 0.5))
    look.on("body", lambda k, f, img: band(k, f, img, 5.0, 6.0, paint(RED, 0.625, 0.3)))
    look.on("body", lambda k, f, img: (M.darken(img, (np.floor(f.Y) == 4) & (f.d in SIDES), 0.7)))      # the tank's girth seam
    look.on("body", lambda k, f, img: stencil(f, img, (3.0,), BLACK[2], 5, "xx.xx"))
    look.on("base", lambda k, f, img: bolts(f, img, 0.9))
    return save(w, look)


def chemical() -> list[str]:
    """A bulk-filled chemical warhead: pale grey with the two dark green bands of a toxic chemical agent, a filler
    plug on its shoulder and the burster's well under a steel nose."""
    w = Build("chemical_warhead")
    r, hb = 4.2, 2.0
    mounting_ring(w, r + 0.7, hb)
    w.ring("body", r, hb, 10.0, caps=("up",), top=False)
    top = w.nose("nose", r, 10.0, 5.0, "ogive", 5)
    tip(w, 0.9, top, 1.0, "fuze")
    w.box("plug", (9.6, 9.4, 4.6), (11.2, 10.6, 6.2))         # the filler plug, standing on the shoulder
    look = Look(body=paint(WHITE, 0.625, 0.15), nose=metal(STEEL, 0.56), base=BASE, fuze=FUZE, plug=metal(BLACK, 0.45))
    for y0, y1 in ((7, 8), (5, 6)):
        look.on("body", lambda k, f, img, y0=y0, y1=y1: band(k, f, img, y0, y1, paint(CHEM_GREEN, 0.5, 0.2)))
    look.on("body", lambda k, f, img: stencil(f, img, (3.6,), BLACK[2], 5, "x.xxx"))
    look.on("base", lambda k, f, img: bolts(f, img, 1.2))
    return save(w, look)


def toxic_waste() -> list[str]:
    """A dark drum of toxic sludge with two rolling hoops, a glowing toxin-green dome and band, hazard dashes."""
    w = Build("toxic_waste_warhead")
    r, hb = 5.0, 1.5
    mounting_ring(w, r + 0.6, hb)
    w.ring("body", r, hb, 10.0, caps=("up",), top=False)
    for y in (4.0, 7.0):
        w.ring("hoop", r + 0.5, y, y + 1, caps=("up", "down"), top=True, bottom=True, foot=False)
    top = w.nose("nose", r, 10.0, 4.0, "dome", 4)
    tip(w, 1.4, top - 0.2, 0.8, "fuze")
    look = Look(body=paint(WASTE, 0.5, 0.25, rust=0.05), nose=M.Material(ramp=ramp9(P.TOXIN), tone=0.55, mottle=0.06, grain=0.04,
                dither=0.5, edge_light=0.18, edge_dark=0.1, ao=0.3, grime=0.0, dust=0.0), base=BASE, fuze=FUZE,
                hoop=metal(WASTE, 0.56))
    look.on("body", lambda k, f, img: band(k, f, img, 8, 9, M.Material(ramp=ramp9(P.TOXIN), tone=0.62, mottle=0.05, grain=0.03,
                                                                             dither=0.4, edge_light=0, edge_dark=0, ao=0)))
    look.on("body", lambda k, f, img: img.__setitem__((np.floor(f.Y) == 2) & (np.floor(angle_of(f) / 22.5) % 2 == 0) & (f.d in SIDES),
                                                      YELLOW[4]))
    look.on("body", lambda k, f, img: decal(f, img, TREFOIL, {"Y": YELLOW[4], "K": BLACK[1]}, 7.2))
    look.on("base", lambda k, f, img: bolts(f, img, 0.9))
    return save(w, look)


def antimatter() -> list[str]:
    """The antimatter warhead (DESIGN.md §16.3): antimatter held in a Penning trap in a white ceramic capsule, no fins.
    Three magnetic confinement rings stand round it, each a violet coil glowing between two dark steel collars (the
    coils are lit: their elements emit light); a violet trefoil on a yellow square on its front between the first two
    (the gamma flash), a band of yellow and black hazard chevrons under the third; its dome capped by a dark collar round
    a glowing port."""
    w = Build("antimatter_warhead")
    r, hb, body_top = 4.4, 1.5, 15.4                        # (the body's top under the third ring's collar, not in its plane)
    mounting_ring(w, r + 0.7, hb)
    w.ring("body", r, hb, body_top, caps=("up",), top=False)
    top = w.nose("nose", r, body_top, 4.6, "dome", 5)
    top = tip(w, 1.7, top - 0.4, 0.8, "cap")                # (its planks' tops clear of the dome's last ring)
    tip(w, 1.0, top, 0.45, "port")
    for y in (2.0, 10.0, 15.0):
        # The coil has no caps: they would lie in the collars' planes, and it never shows its ends.
        w.ring("collar", r + 0.65, y, y + 0.5, caps=("up", "down"), top=True, bottom=True, foot=False)
        w.ring("coil", r + 0.45, y + 0.5, y + 1.5, caps=(), top=False, bottom=False, foot=False)
        w.ring("collar", r + 0.65, y + 1.5, y + 2.0, caps=("up", "down"), top=True, bottom=True, foot=False)
    for n in w.groups["coil"] + w.groups["port"]:
        w.m.get(n).light_emission = 15
    lit = dict(mottle=0.04, grain=0.03, dither=0.4, edge_light=0.0, edge_dark=0.0, ao=0.0, grime=0.0, dust=0.0, facing=ROUND)
    look = Look(body=paint(WHITE, 0.62, 0.08), nose=paint(WHITE, 0.62, 0.08), base=BASE, collar=metal(GUNMETAL, 0.34),
                coil=M.Material(ramp=ramp9(VIOLET), tone=0.66, **lit), cap=metal(GUNMETAL, 0.4),
                port=M.Material(ramp=ramp9(VIOLET), tone=0.86, **lit), lug=LUG)

    def windings(k, f, img):                                   # the coil's turns: every other texel round it a shade lighter
        if f.d in SIDES:
            col = np.floor((angle_of(f) + 360) % 360 / 360 * 32).astype(int)
            M.lighten(img, col % 2 == 0, 1.14, 6)

    def chevrons(k, f, img):                                   # hazard chevrons all round, under the third ring
        if f.d not in SIDES:
            return
        col = np.floor((angle_of(f) + 360) % 360 / 360 * 28).astype(int)
        row = np.floor(f.Y + 0.1).astype(int)
        band = (row >= 12) & (row < 15)
        img[band] = YELLOW[4]
        img[band & (((col + row) % 4) < 2)] = BLACK[1]
    look.on("coil", windings)
    look.on("body", chevrons)
    look.on("body", lambda k, f, img: decal(f, img, TREFOIL, {"Y": YELLOW[4], "K": VIOLET[2]}, 9.4))
    look.on("base", lambda k, f, img: bolts(f, img, hb * 0.6))
    return save(w, look)


def storm() -> list[str]:
    """The storm warhead (DESIGN.md §16.14): a Tesla storm generator, squat and caged. A dark steel drum on its mounting
    ring wound with bright copper between two dark collars (the coil's turns showing); four capacitor tubes standing
    round it in steel ferrules, glowing blue (their elements emit light), tied at the top by a steel hoop (its cage);
    yellow and black hazard stripes round the drum's shoulder; a neck rising to a Tesla coil's top load (a flat polished
    ring) and a copper electrode at its middle."""
    w = Build("storm_warhead")
    hb, r = 1.5, 3.2
    mounting_ring(w, 5.3, hb)
    w.ring("body", r, hb, 9.6, caps=("up",), top=True)
    # The coil has no caps: its ends lie inside the collars.
    w.ring("coil", r + 0.45, 3.2, 8.2, caps=(), top=False, bottom=False, foot=False)
    w.ring("collar", r + 0.65, 2.6, 3.2, caps=("up", "down"), top=True, bottom=True, foot=False)
    w.ring("collar", r + 0.65, 8.2, 8.8, caps=("up", "down"), top=True, bottom=True, foot=False)
    for k, a in enumerate((45, 135, 225, 315)):
        cx, cz = 8 + 4.6 * math.sin(math.radians(a)), 8 + 4.6 * math.cos(math.radians(a))
        w.add("ferrule", section(w.m, f"ferrule_lo{k}", 0.8, hb, 2.3, 8, cx=cx, cz=cz, caps=("up",)), True, False, True)
        w.add("tube", section(w.m, f"tube{k}", 0.6, 2.3, 9.1, 8, cx=cx, cz=cz, caps=()), False, False, False)
        w.add("ferrule", section(w.m, f"ferrule_hi{k}", 0.8, 9.1, 9.75, 8, cx=cx, cz=cz, caps=("up", "down")), True, True, False)
    hoop(w, "cage", 4.6, 10.0, "y", thick=0.45, width=0.5)
    w.ring("lid", 2.0, 9.6, 10.0, caps=("up",), top=True, foot=False)      # (covers the drum's planks where they cross)
    w.ring("neck", 1.0, 10.0, 11.6, caps=("up",), top=True, foot=False)
    hoop(w, "torus", 2.6, 12.3, "y", thick=1.5, width=1.3)
    w.ring("electrode", 0.55, 11.6, 13.7, caps=("up",), top=True, foot=False)
    for n in w.groups["tube"]:
        w.m.get(n).light_emission = 15
    lit = dict(mottle=0.04, grain=0.03, dither=0.4, edge_light=0.0, edge_dark=0.0, ao=0.0, grime=0.0, dust=0.0, facing=ROUND)
    look = Look(body=metal(GUNMETAL, 0.4), base=BASE, coil=metal(P.COPPER, 0.58), collar=metal(GUNMETAL, 0.32),
                ferrule=metal(STEEL, 0.5), tube=M.Material(ramp=ramp9(P.TESLA), tone=0.7, **lit), cage=metal(STEEL, 0.52),
                neck=metal(GUNMETAL, 0.45), lid=metal(GUNMETAL, 0.36), torus=metal(STEEL, 0.66), electrode=metal(P.COPPER, 0.62),
                lug=LUG)

    def windings(k, f, img):                                   # the coil's turns: every other texel round it a shade lighter
        if f.d in SIDES:
            row = np.floor(f.Y * 2).astype(int)
            M.lighten(img, row % 2 == 0, 1.12, 6)

    def stripes(k, f, img):                                    # hazard stripes round the drum's shoulder
        if f.d not in SIDES:
            return
        col = np.floor((angle_of(f) + 360) % 360 / 360 * 24).astype(int)
        row = np.floor(f.Y + 0.1).astype(int)
        band = (row >= 8) & (row < 10) & (f.Y > 8.8)
        img[band] = YELLOW[4]
        img[band & (((col + row) % 4) < 2)] = BLACK[1]
    look.on("coil", windings)
    look.on("body", stripes)
    look.on("base", lambda k, f, img: bolts(f, img, hb * 0.6))
    return save(w, look)


def cryogenic() -> list[str]:
    """The cryogenic warhead (DESIGN.md §16.16): a heat sink of exotic matter held near absolute zero in a polished steel
    dewar, rime dusting it at its foot. A sight glass round its waist glows ice blue between two dark collars (the heat
    sink seen through it: lit); a cryogen's ice-blue band under its shoulder. Four liquid nitrogen tanks stand strapped
    round it, ice blue, rimed at their feet, under white domes and their valves,
    each piped in at the dewar's shoulder. A white insulated lid is domed over it, and on top a relief valve's stack with
    a brass cap, where its boil-off vents."""
    w = Build("cryogenic_warhead")
    hb, r = 1.5, 3.5
    mounting_ring(w, 5.8, hb)
    w.ring("body", r, hb, 10.6, caps=("up",), top=True)
    # The glass has no caps: its ends lie inside the collars.
    w.ring("glass", r + 0.22, 5.1, 6.7, caps=(), top=False, bottom=False, foot=False)
    w.ring("collar", r + 0.35, 4.6, 5.1, caps=("up", "down"), top=True, bottom=True, foot=False)
    w.ring("collar", r + 0.35, 6.7, 7.2, caps=("up", "down"), top=True, bottom=True, foot=False)
    for k, a in enumerate((45, 135, 225, 315)):
        cx, cz = 8 + 4.85 * math.sin(math.radians(a)), 8 + 4.85 * math.cos(math.radians(a))
        w.add("tank", section(w.m, f"tank{k}", 0.85, hb, 8.6, 8, cx=cx, cz=cz, caps=("up",)), True, False, True)
        w.add("dome", section(w.m, f"dome{k}", 0.62, 8.6, 9.1, 8, cx=cx, cz=cz, caps=("up",)), True, False, False)
        w.add("tankvalve", section(w.m, f"tankvalve{k}", 0.3, 9.1, 9.6, 8, cx=cx, cz=cz, caps=("up",)), True, False, False)
        for j, y in enumerate((2.6, 7.6)):
            w.add("strap", section(w.m, f"strap{k}_{j}", 0.97, y, y + 0.5, 8, cx=cx, cz=cz, caps=("up", "down")), True, True, False)
        # Its feed line in to the dewar's shoulder, from the dome's side.
        w.box("pipe", (7.82, 8.63, 8 + r - 0.25), (8.18, 8.85, 8 + 4.85), rotation={"origin": (8, 8.74, 8), "axis": "y", "angle": a})
    top = w.nose("lid", r, 10.6, 2.0, "dome", 3)
    top = tip(w, 1.1, top - 0.3, 0.6, "neck")
    # The stack has no cap: its top is inside the valve.
    w.ring("stack", 0.5, top, 14.6, caps=(), top=False, foot=False)
    w.ring("valve", 0.85, 14.6, 15.3, caps=("up", "down"), top=True, bottom=True, foot=False)
    for n in w.groups["glass"]:
        w.m.get(n).light_emission = 15
    lit = dict(mottle=0.04, grain=0.03, dither=0.4, edge_light=0.0, edge_dark=0.0, ao=0.0, grime=0.0, dust=0.0, facing=ROUND)
    look = Look(body=metal(STEEL, 0.7), base=BASE, glass=M.Material(ramp=ramp9(P.CRYO), tone=0.74, **lit),
                collar=metal(GUNMETAL, 0.32), tank=paint(G.CRYO_CAN, 0.56, 0.1), dome=paint(WHITE, 0.7, 0.08),
                tankvalve=metal(STEEL, 0.6), strap=metal(GUNMETAL, 0.4), pipe=metal(STEEL, 0.55), lid=paint(WHITE, 0.68, 0.1),
                neck=metal(STEEL, 0.55), stack=metal(STEEL, 0.6), valve=BRASS, lug=LUG)

    def churn(k, f, img):                                      # the cold churning behind the glass: darker swirls round it
        if f.d in SIDES:
            col = np.floor((angle_of(f) + 360) % 360 / 360 * 24 + f.Y * 1.5).astype(int)
            M.darken(img, col % 4 == 0, 0.78)

    def rime(k, f, img):                                       # a dusting of rime at the feet, thinning upward
        if f.d not in SIDES:
            return
        r = M.white(f.X, f.Y, f.Z, 923)
        on = (r < 0.22 * np.clip(1 - (f.Y - hb) / 3.2, 0, 1) ** 1.5) & (f.Y >= hb)
        img[on, :3] = (img[on, :3] * 0.35 + np.array(FROST[5][:3]) * 0.65).astype(img.dtype)
    look.on("glass", churn)
    look.on("body", lambda k, f, img: band(k, f, img, 9, 10, paint(G.CRYO_CAN, 0.6, 0.06)))     # a cryogen's ice-blue band
    look.on("body", rime).on("tank", rime)
    look.on("base", lambda k, f, img: bolts(f, img, hb * 0.6))
    return save(w, look)


def hoop(w: Build, group: str, R: float, cy: float, axis: str, thick: float = 0.6, width: float = 0.8, n: int = 16) -> list[str]:
    """A thin hoop of radius R round the point (8, cy, 8), in the plane square to {axis}: {n} planks laid tangent to it,
    each turned about the axis to its place (26.x elements turn by any angle), a hair longer than its share so they
    meet. About y it lies flat; about x it stands across the warhead's front and back; about z, from side to side. Every
    other plank is a hair narrower, so where neighbours overlap at a joint their flat faces are not in one plane."""
    chord = 2 * R * math.tan(math.pi / n) + 0.04
    t = thick / 2
    names = []
    for i in range(n):
        a = 360.0 * i / n
        h = width / 2 - (0.04 if i % 2 else 0.0)
        if axis == "y":
            frm, to = (8 + R - t, cy - h, 8 - chord / 2), (8 + R + t, cy + h, 8 + chord / 2)
        elif axis == "x":
            frm, to = (8 - h, cy + R - t, 8 - chord / 2), (8 + h, cy + R + t, 8 + chord / 2)
        else:
            frm, to = (8 - chord / 2, cy + R - t, 8 - h), (8 + chord / 2, cy + R + t, 8 + h)
        names.append(w.box(group, frm, to, rotation={"origin": (8, cy, 8), "axis": axis, "angle": a}))
    return names


def singularity() -> list[str]:
    """The singularity warhead (DESIGN.md §16.2): a singularity seed held in a matte-black sphere, its violet core
    glowing through a slit round its equator (the slit's ring emits light), hung in a gyroscope of three hoops (dark
    steel outermost and standing front to back on its cradle, bright steel across it, brass flat round the sphere's
    waist), pinned together in brass where they cross. Its sphere is rings from a true circle's profile: below its
    waist each wider than the last (their undersides show), above it narrowing (their tops show)."""
    w = Build("singularity_warhead")
    hb, cy, R = 1.5, 9.0, 5.0
    mounting_ring(w, 5.3, hb)
    w.box("cradle", (7.1, hb, 7.1), (8.9, 2.4, 8.9))
    # The sphere in bands, each as wide as a true sphere at its middle; a band shows its underside where it is wider than
    # the one below and its top where it is wider than the one above (else it reaches into its wider neighbour, unseen).
    # The slit (lit) is a narrower band just above the brass hoop, so the hoop does not hide it.
    bands = [(3.9, 4.4), (4.4, 5.0), (5.0, 5.7), (5.7, 6.4), (6.4, 7.1), (7.1, 7.8), (7.8, 8.4), (8.4, 9.6), ("slit", 9.6, 10.4),
             (10.4, 11.1), (11.1, 11.8), (11.8, 12.5), (12.5, 13.2), (13.2, 13.7), (13.7, 14.0)]
    radii = []
    for band in bands:
        if band[0] == "slit":
            radii.append(4.6)
        else:
            y0, y1 = band
            radii.append(round(math.sqrt(max(0.3, R * R - (cy - (y0 + y1) / 2) ** 2)), 3))
    for i, band in enumerate(bands):
        if band[0] == "slit":
            w.ring("slit", radii[i], band[1], band[2], caps=(), top=False, bottom=False, foot=False)
            continue
        y0, y1 = band
        below = radii[i - 1] if i > 0 else 0.0
        above = radii[i + 1] if i + 1 < len(radii) else 0.0
        caps = tuple(c for c, on in (("up", radii[i] > above), ("down", radii[i] > below)) if on)
        stagger = "full" if len(caps) == 2 else "base" if caps == ("down",) and sides_for(radii[i]) == 16 else None
        w.add("body", section(w.m, f"band{i}_", radii[i], y0, y1, sides_for(radii[i]), caps=caps, stagger=stagger),
              top="up" in caps, bottom="down" in caps, foot=False)
    w.last_r = radii[-1]
    tip(w, 0.9, 14.0, 0.45, "pole")
    hoop(w, "outer", 7.2, cy, "x")
    hoop(w, "middle", 6.4, cy, "z")
    hoop(w, "inner", 5.7, cy, "y")
    for n in w.groups["slit"]:
        w.m.get(n).light_emission = 15
    # The pins where the hoops cross: outer to middle above and below, middle to inner at the sides, inner to the
    # sphere at the front and back.
    for sy in (1, -1):
        a, b = sorted((cy + sy * 6.7, cy + sy * 6.9))
        w.box("pin", (7.65, a, 7.65), (8.35, b, 8.35))
    for sx in (1, -1):
        a, b = sorted((8 + sx * 6.0, 8 + sx * 6.1))
        w.box("pin", (a, cy - 0.35, 7.65), (b, cy + 0.35, 8.35))
    for sz in (1, -1):
        a, b = sorted((8 + sz * 4.9, 8 + sz * 5.4))
        w.box("pin", (7.65, cy - 0.35, a), (8.35, cy + 0.35, b))
    lit = dict(mottle=0.04, grain=0.03, dither=0.4, edge_light=0.0, edge_dark=0.0, ao=0.0, grime=0.0, dust=0.0, facing=ROUND)
    look = Look(body=M.Material(ramp=ramp9(VOID), tone=0.42, mottle=0.03, grain=0.02, dither=0.35, edge_light=0.08, edge_dark=0.05,
                                ao=0.3, grime=0.0, dust=0.0, facing=ROUND),
                slit=M.Material(ramp=ramp9(VIOLET), tone=0.7, **lit), base=BASE, cradle=metal(GUNMETAL, 0.42),
                outer=metal(GUNMETAL, 0.4), middle=metal(STEEL, 0.62), inner=BRASS, pin=metal(P.BRASS, 0.62), pole=metal(GUNMETAL, 0.38),
                lug=LUG)

    def swirl(k, f, img):                                      # the core turning in the slit: darker eddies round it
        if f.d in SIDES:
            col = np.floor((angle_of(f) + 360) % 360 / 360 * 30).astype(int)
            M.darken(img, col % 3 == 0, 0.72)
    look.on("slit", swirl)
    look.on("base", lambda k, f, img: bolts(f, img, hb * 0.6))
    return save(w, look)


# --- extras on the dispensers and nuclear warheads ---------------------------------------------------------------------------

def frost(k, f, img) -> None:
    """Frost on the cryo canister: pale specks gathered toward its foot."""
    if f.d not in SIDES:
        return
    r = M.white(f.X, f.Y, f.Z, 917)
    on = r < 0.12 + 0.18 * np.clip(1 - (f.Y - 2) / 8, 0, 1)
    img[on, :3] = FROST[6][:3]


def cryo_vents(w: Build, r: float, top: float) -> None:
    """Two white vent caps on the cryo canister's shoulder: its coolant's relief valves."""
    for dx in (-2.2, 2.2):
        w.ring("vent", 0.8, top - 0.4, top + 0.9, caps=("up",), top=True, foot=False, sides=8)
    vents = w.groups["vent"]
    half = len(vents) // 2
    for i, n in enumerate(vents):
        dx = -2.2 if i < half else 2.2
        b = w.m.get(n)
        b.frm = (b.frm[0] + dx, b.frm[1], b.frm[2] - 2.4)
        b.to = (b.to[0] + dx, b.to[1], b.to[2] - 2.4)
        if b.rotation:
            o = b.rotation["origin"]
            b.rotation["origin"] = (o[0] + dx, o[1], o[2] - 2.4)


def emp_coil(w: Build, r: float, top: float) -> None:
    """The EMP canister's coil: a copper winding round its waist between two white ceramic insulator rings."""
    w.ring("insulator", r + 0.4, 5.0, 6.0, caps=("up", "down"), top=True, bottom=True, foot=False)
    w.ring("coil", r + 0.6, 6.0, 8.0, caps=("up", "down"), top=True, bottom=True, foot=False)
    w.ring("insulator", r + 0.4, 8.0, 9.0, caps=("up", "down"), top=True, bottom=True, foot=False)


def arming_panel(w: Build, r: float, top: float) -> None:
    """The tactical warhead's arming panel on its east side: a small box with a data plate."""
    w.box("panel", (8 + r - 0.4, 3.4, 6.4), (8 + r + 0.8, 6.4, 9.6))


def equator(w: Build, r: float, top: float) -> None:
    """The fission warhead's bolted equator: the ring where its case's halves meet."""
    w.ring("equator", r + 0.5, 6.0, 7.0, caps=("up", "down"), top=True, bottom=True, foot=False)


def reservoir(w: Build, r: float, top: float) -> None:
    """The boosted warhead's reservoir of boost gas (deuterium and tritium) on its side, and the line into the case."""
    w.ring("tank", 1.1, 4.0, 9.0, caps=("up", "down"), top=True, bottom=True, foot=False, sides=8)
    for n in w.groups["tank"]:
        b = w.m.get(n)
        dx = r + 1.0
        b.frm = (b.frm[0] + dx, b.frm[1], b.frm[2])
        b.to = (b.to[0] + dx, b.to[1], b.to[2])
        if b.rotation:
            o = b.rotation["origin"]
            b.rotation["origin"] = (o[0] + dx, o[1], o[2])
    w.box("tank", (8 + r - 0.3, 8.4, 7.6), (8 + r + 0.6, 8.9, 8.4))


def studs(k, f, img) -> None:
    """The neutron warhead's reflector studs: rows of bright heads between its rings."""
    if f.d not in SIDES:
        return
    for y in (6.5, 9.5, 12.0):
        on = (np.floor(f.Y) == math.floor(y))
        h, wd = f.shape
        mid = np.zeros(f.shape, dtype=bool)
        mid[:, wd // 2] = True
        M.lighten(img, on & mid, 1.4, 20)


# --- the drum, the re-entry vehicles and the Tsar ----------------------------------------------------------------------------

def dirty() -> list[str]:
    """An improvised radiological weapon: a rusty drum with two hoops and a bolted lid, yellow tape round it, a trefoil
    sticker on its front."""
    w = Build("dirty_warhead")
    r, hb = 5.0, 1.5
    mounting_ring(w, r + 0.6, hb)
    w.ring("body", r, hb, 11.0, caps=("up",), top=False)
    for y in (4.0, 8.0):
        w.ring("hoop", r + 0.5, y, y + 1, caps=("up", "down"), top=True, bottom=True, foot=False)
    w.ring("lid", r + 0.3, 11.0, 12.0, caps=("up",), top=True, foot=False)
    w.ring("lid", 3.0, 12.0, 12.6, caps=("up",), top=True, foot=False, sides=8)              # the lid's raised middle
    w.ring("bung", 1.0, 12.6, 13.4, caps=("up",), top=True, foot=False, sides=8)
    look = Look(body=paint(RUST, 0.5, 0.3, rust=0.18), base=BASE, hoop=metal(RUST, 0.45, rust=0.1), lid=paint(RUST, 0.45, 0.3, rust=0.12),
                bung=metal(GUNMETAL, 0.5))
    for y0, y1 in ((9, 10), (2, 3)):
        look.on("body", lambda k, f, img, y0=y0, y1=y1: band(k, f, img, y0, y1, paint(YELLOW, 0.625, 0.45)))
    look.on("body", lambda k, f, img: decal(f, img, TREFOIL, {"Y": YELLOW[4], "K": BLACK[1]}, 7.6))
    look.on("lid", lambda k, f, img: bolts(f, img, 11.5))
    look.on("base", lambda k, f, img: bolts(f, img, 0.9))
    return save(w, look)


def reentry(name: str, cone_ramp, skirt_ramp, band_ramp, jacket_ramp) -> list[str]:
    """A re-entry vehicle (an intercontinental missile's warhead: the Mk 21's sharp cone over its skirt): the cone
    sheathed in dark ablative heat shield, a nose tip of graphite, the skirt banded and bolted to the bus. A cobalt
    jacket shows as cobalt-blue bands round the cone where its plates meet."""
    w = Build(name)
    r, hb = 6.4, 2.0
    mounting_ring(w, r + 0.6, hb)
    w.ring("body", r, hb, 8.0, caps=("up",), top=False)
    top = w.nose("nose", r, 8.0, 16.0, "cone", 16, min_r=0.7)
    tip(w, 0.7, top, 0.8, "fuze")
    look = Look(body=paint(skirt_ramp, 0.5), nose=paint(cone_ramp, 0.375, 0.08, mottle=0.06, grain=0.06, dither=0.6), base=BASE,
                fuze=metal(DARK, 0.45))
    look.on("body", lambda k, f, img: band(k, f, img, 2, 3, paint(band_ramp, 0.625, 0.25)))
    look.on("body", lambda k, f, img: bolts(f, img, 7.5))
    look.on("body", lambda k, f, img: decal(f, img, TREFOIL, {"Y": YELLOW[4], "K": BLACK[1]}, 7.6))
    if jacket_ramp is not None:
        for y in (10, 14, 18):
            look.on("nose", lambda k, f, img, y=y: band(k, f, img, y, y + 1, paint(jacket_ramp, 0.5, 0.2)))
    look.on("base", lambda k, f, img: bolts(f, img, 1.2))
    return save(w, look)


def tsar() -> list[str]:
    """The Tsar Mega-bomb as a missile's warhead (after the user's look: tail fins and a parachute pack were odd on a
    missile): the fat body (the front's stencils, a white band, four glowing vents low down), a rounded nose and its
    fuze cap, over a boat-tail that steps in, a pixel at a time, to the mounting ring it is bolted to its missile by."""
    w = Build("tsar_warhead", 256)
    mounting_ring(w, 5.2, 2.0)
    # The boat-tail, ring by ring out to the body (each covered above by the next, so only its foot shows).
    for r, y0, y1 in ((5.6, 2.0, 4.0), (6.2, 4.0, 6.0), (6.6, 6.0, 8.0)):
        w.ring("tail", r, y0, y1, caps=("down",), top=False, foot=False)
    w.ring("body", 7.0, 8.0, 21.0, caps=("up",), top=False, foot=False)
    top = w.nose("nose", 7.0, 21.0, 8.0, "blunt", 8)
    tip(w, 0.9, top, 0.8, "fuze")
    for d, (a, b) in {"n": ((6, 8.8, 0.8), (10, 11.6, 1.05)), "s": ((6, 8.8, 14.95), (10, 11.6, 15.2)),
                      "w": ((0.8, 8.8, 6), (1.05, 11.6, 10)), "e": ((14.95, 8.8, 6), (15.2, 11.6, 10))}.items():
        w.box("vent", a, b, light=15)
    look = Look(body=paint(TSAR, 0.5, 0.18), nose=paint(TSAR, 0.5, 0.18), tail=paint(TSAR, 0.45, 0.2), base=BASE,
                fuze=FUZE, vent=metal(BLACK, 0.3))
    look.on("body", lambda k, f, img: band(k, f, img, 19, 20, paint(WHITE, 0.75, 0.2)))
    look.on("body", lambda k, f, img: decal(f, img, ["WWW.WWW", "W...W.W", "WWW.W.W", "..W.W.W", "WWW.WWW"],
                                            {"W": WHITE[5]}, 17.0))
    look.on("body", lambda k, f, img: stencil(f, img, (13.0, 12.0), WHITE[4], 7, "xx.xxx.x"))

    def back(k, f, img):                                       # the trefoil on the back
        if f.d in SIDES and f.normal[2] < -0.5:
            col = np.floor(f.X - 5.5).astype(int)
            row = np.floor(17.5 - f.Y).astype(int)
            for j, line in enumerate(TREFOIL):
                for i, ch in enumerate(line):
                    img[(row == j) & (col == 4 - i)] = BLACK[1] if ch == "K" else YELLOW[4]
    look.on("body", back)
    look.on("tail", lambda k, f, img: bolts(f, img, 7.4))     # where the body's case bolts to its tail
    look.on("base", lambda k, f, img: bolts(f, img, 1.2))

    def vent(k, f, img):
        img[...] = VENT[0]
        if f.d in SIDES:
            slit = (np.floor(f.Y * 2) % 2 == 0)
            img[slit, :3] = VENT[1][:3]
    look.on("vent", vent)
    return save(w, look)


# --- the superweapons' warheads (DESIGN.md §19.5) --------------------------------------------------------------------------------

def scud_storm() -> list[str]:
    """A toxin SCUD's warhead (the GLA's SCUD Storm, C&C Generals): the Scud's long nose, painted the GLA's toxin green,
    over a short section of the same green, a black band where it bolts on, white stencils."""
    w = Build("scud_storm_warhead")
    r, hb = 4.0, 2.0
    mounting_ring(w, r + 0.6, hb)
    w.ring("body", r, hb, 6.0, caps=("up",), top=False)
    top = w.nose("nose", r, 6.0, 16.0, "ogive", 16)
    tip(w, 0.75, top, 0.9, "fuze")
    green = paint(SCUD_GREEN, 0.6, 0.26)
    look = Look(body=green, nose=green, base=BASE, fuze=metal(DARK, 0.42))
    look.on("body", lambda k, f, img: band(k, f, img, 2, 3, paint(BLACK, 0.5, 0.2)))
    look.on("nose", lambda k, f, img: stencil(f, img, (9.0, 8.0), WHITE[5], 4, "xx.x"))
    look.on("base", lambda k, f, img: bolts(f, img, hb * 0.6))
    return save(w, look)


def nuclear_missile() -> list[str]:
    """China's Nuclear Missile's warhead (C&C Generals; a DF-5's re-entry vehicle): a long silver cone over a short skirt,
    its tip and a band at its foot red, a red star on the front of its skirt, a trefoil on its back."""
    w = Build("nuclear_missile_warhead")
    r, hb = 6.0, 2.0
    mounting_ring(w, r + 0.6, hb)
    w.ring("body", r, hb, 8.0, caps=("up",), top=False)
    top = w.nose("nose", r, 8.0, 18.0, "ogive", 18)
    tip(w, 0.7, top, 0.8, "fuze")
    silver = metal(STEEL, 0.64, brushed=0.06)
    red = paint(PLA_RED, 0.55, 0.15)
    look = Look(body=silver, nose=silver, base=BASE, fuze=red)
    def red_tip(k, f, img):                                    # the tip red, its rings' ledges with it
        y0 = top - 4
        M.paste(img, k.on(f, red), (np.floor(f.Y + 0.1) >= y0) if f.d in SIDES else (f.Y > y0 + 0.01))
    look.on("nose", red_tip)
    look.on("body", lambda k, f, img: band(k, f, img, 2, 3, red))
    look.on("body", lambda k, f, img: decal(f, img, STAR, {"R": PLA_RED[4]}, 7.6))

    def back(k, f, img):                                       # the trefoil on the back
        if f.d in SIDES and f.normal[2] < -0.5:
            col = np.floor(f.X - 5.5).astype(int)
            row = np.floor(13.5 - f.Y).astype(int)
            for j, line in enumerate(TREFOIL):
                for i, ch in enumerate(line):
                    img[(row == j) & (col == 4 - i)] = BLACK[1] if ch == "K" else YELLOW[4]
    look.on("nose", back)
    look.on("base", lambda k, f, img: bolts(f, img, 1.2))
    return save(w, look)


# --- the missile's view of each warhead, the bomblet, the fire, the blockstates ------------------------------------------------

#: The nose of a missile with no warhead: (nose ramp, band colour or None, tip ramp), bare steel. (A missile with a warhead
#: carries the warhead's own model, launch.py's warhead_mounts.)
NOSES = {"none": (STEEL, None, STEEL)}

#: Every warhead, in the order they are built and listed.
WARHEADS = ["he_warhead", "heavy_warhead", "thermobaric_warhead", "cluster_warhead", "bunker_buster_warhead", "napalm_warhead",
            "massive_warhead", "gas_warhead", "smoke_warhead", "cryo_warhead", "emp_warhead", "chemical_warhead",
            "toxic_waste_warhead", "tactical_nuclear_warhead", "fission_warhead", "boosted_fission_warhead", "neutron_warhead",
            "dirty_warhead", "thermonuclear_warhead", "cobalt_warhead", "tsar_warhead", "scud_storm_warhead", "nuclear_missile_warhead",
            "antimatter_warhead", "singularity_warhead", "storm_warhead", "cryogenic_warhead"]


def mount(name: str) -> tuple[float, float, float]:
    """How a warhead stands on a missile (launch.py's warhead_mounts), from its saved model: the radius of the widest round
    section standing on its floor (its mounting ring; the Tsar's parachute pack), as the middle of a facet (its corners
    reach about 2% further); how far it reaches out from its axis anywhere (lugs, fins); and its height."""
    import json
    data = json.loads((paths.MODELS / "block" / f"{name}.json").read_text(encoding="utf-8"))
    base = half = height = 0.0
    for el in data["elements"]:
        (x0, y0, z0), (x1, y1, z1) = el["from"], el["to"]
        height = max(height, y1)
        if abs((x0 + x1) / 2 - 8) < 0.05 and abs((z0 + z1) / 2 - 8) < 0.05 and y0 <= 0.5:
            base = max(base, (x1 - x0) / 2, (z1 - z0) / 2)
        rot = el.get("rotation")
        a = math.radians(rot["angle"]) if rot and rot.get("axis") == "y" else 0.0
        ox, oz = (rot["origin"][0], rot["origin"][2]) if rot else (8.0, 8.0)
        for x in (x0, x1):
            for z in (z0, z1):
                wx = math.cos(a) * (x - ox) + math.sin(a) * (z - oz) + ox
                wz = -math.sin(a) * (x - ox) + math.cos(a) * (z - oz) + oz
                half = max(half, abs(wx - 8), abs(wz - 8))
    return round(base, 3), round(half, 3), round(height, 3)


def bomblet() -> None:
    """A cluster bomblet: a small yellow can (a combined-effects bomblet's) with a ring of tail tabs and its ribbon."""
    m = BlockModel("cluster_bomblet", tex_size=(64, 64), kind="item")
    m.open_ok = set()
    can = section(m, "can", 2.0, 3, 10, 8)
    tabs = []
    for d, (a, b) in {"n": ((7.4, 10, 5.6), (8.6, 11.4, 6.2)), "s": ((7.4, 10, 9.8), (8.6, 11.4, 10.4)),
                      "w": ((5.6, 10, 7.4), (6.2, 11.4, 8.6)), "e": ((9.8, 10, 7.4), (10.4, 11.4, 8.6))}.items():
        m.box(f"tab_{d}", a, b)
        tabs.append(f"tab_{d}")
    m.box("ribbon", (7.6, 10, 7.9), (8.4, 13.5, 8.1))
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    k = K.Kit(m, {}, seed=531, turned=True)
    round_faces(k.S, can, top=True, bottom=True, foot=False)
    k.paint(t, can, paint(YELLOW, 0.625, 0.2))
    k.overlay(t, can, lambda f, img: M.paste(img, k.on(f, paint(BLACK, 0.5, 0.1)), (f.Y >= 4) & (f.Y < 5) & (f.d in SIDES)))
    k.paint(t, tabs, metal(STEEL, 0.5))
    k.paint(t, "ribbon", paint(WHITE, 0.75, 0.0))
    G.finish(m, t)


def napalm_fire() -> None:
    """Vanilla's fire, on our block (cut out, as vanilla draws fire)."""
    for k in (0, 1):
        write_json(paths.MODELS / "block" / f"napalm_fire_floor{k}.json", {"parent": f"minecraft:block/fire_floor{k}", "render_type": "minecraft:cutout"})
    variants = {f"age={a}": [{"model": f"groundburst:block/napalm_fire_floor{k}"} for k in (0, 1)] for a in range(4)}
    write_json(paths.ASSETS / "blockstates" / "napalm_fire.json", {"variants": variants})


def blockstates() -> None:
    for name in WARHEADS:
        v = {}
        for facing, rot in C.HORIZONTAL.items():
            for armed in (False, True):
                v[f"armed={str(armed).lower()},facing={facing}"] = {"model": f"groundburst:block/{name}{'_armed' if armed else ''}", **rot}
        write_json(paths.ASSETS / "blockstates" / f"{name}.json", {"variants": v})
        write_json(paths.ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": f"groundburst:block/{name}"}})


def check_caps(name: str, step: float = 0.05, tol: float = 0.03) -> float:
    """Looking straight down on a model: the area (px2) where the two highest caps of different elements lie within
    {tol} of each other (they would flicker). Turned elements are turned; the check walks a grid of points."""
    import json
    data = json.loads((paths.MODELS / "block" / f"{name}.json").read_text(encoding="utf-8"))
    caps = []
    for el in data["elements"]:
        if "up" not in el["faces"]:
            continue
        (x0, _, z0), (x1, y1, z1) = el["from"], el["to"]
        rot = el.get("rotation")
        a = math.radians(rot["angle"]) if rot and rot.get("axis") == "y" else 0.0
        o = rot["origin"] if rot else (8, 0, 8)
        caps.append((x0, x1, z0, z1, y1, a, o[0], o[2]))
    xs = np.arange(-8, 24, step) + step / 2
    X, Z = np.meshgrid(xs, xs)
    best = np.full(X.shape, -1e9)
    second = np.full(X.shape, -1e9)
    for x0, x1, z0, z1, y1, a, ox, oz in caps:
        # The point in the element's own (unturned) frame: turned back by its angle about its origin.
        c, s = math.cos(-a), math.sin(-a)
        dx, dz = X - ox, Z - oz
        lx, lz = c * dx + s * dz + ox, -s * dx + c * dz + oz
        inside = (lx >= x0) & (lx <= x1) & (lz >= z0) & (lz <= z1)
        h = np.where(inside, y1, -1e9)
        higher = h > best
        second = np.where(higher, best, np.maximum(second, h))
        best = np.where(higher, h, best)
    fight = (best > -1e8) & (second > -1e8) & (best - second < tol)
    return float(fight.sum()) * step * step


def check_faces(name: str, tol: float = 0.03) -> list[str]:
    """Upright faces that would flicker: two pointing the same way (turned elements turned) on one plane (within {tol})
    and overlapping, as rings of one radius one above the other would be."""
    import json
    data = json.loads((paths.MODELS / "block" / f"{name}.json").read_text(encoding="utf-8"))
    normals = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0)}
    faces = []
    for el in data["elements"]:
        rot = el.get("rotation")
        a = math.radians(rot["angle"]) if rot and rot.get("axis") == "y" else 0.0
        c, s = math.cos(a), math.sin(a)
        ox, oz = (rot["origin"][0], rot["origin"][2]) if rot else (8.0, 8.0)
        (x0, y0, z0), (x1, y1, z1) = el["from"], el["to"]
        for d in el["faces"]:
            if d not in normals:
                continue
            nx, _, nz = normals[d]
            # The face's middle and its half-width along it, before turning.
            px, pz = (x1 if nx > 0 else x0 if nx < 0 else (x0 + x1) / 2), (z1 if nz > 0 else z0 if nz < 0 else (z0 + z1) / 2)
            half = (z1 - z0) / 2 if nx else (x1 - x0) / 2
            wx, wz = c * (px - ox) + s * (pz - oz) + ox, -s * (px - ox) + c * (pz - oz) + oz       # (as the game turns: +y, right-handed)
            n = (c * nx + s * nz, -s * nx + c * nz)
            t = (-n[1], n[0])
            faces.append((el["name"], d, n, n[0] * wx + n[1] * wz, t[0] * wx + t[1] * wz, half, y0, y1))
    issues = []
    for i in range(len(faces)):
        n1, d1, nn1, off1, mid1, half1, ya1, yb1 = faces[i]
        for j in range(i + 1, len(faces)):
            n2, d2, nn2, off2, mid2, half2, ya2, yb2 = faces[j]
            if nn1[0] * nn2[0] + nn1[1] * nn2[1] < 0.9999 or abs(off1 - off2) > tol:
                continue
            across = min(mid1 + half1, mid2 + half2) - max(mid1 - half1, mid2 - half2)
            up = min(yb1, yb2) - max(ya1, ya2)
            if across > 0.01 and up > 0.01:
                issues.append(f"{n1}/{d1} vs {n2}/{d2}: {across * up:.2f} px2")
    return issues


def main() -> None:
    names = build_all()
    for n in names:
        area = check_caps(n)
        if area > 0:
            print(f"  cap flicker [{n}]: {area:.2f} px2 seen from above")
        for issue in check_faces(n)[:6]:
            print(f"  face flicker [{n}]: {issue}")
    blockstates()
    bomblet()
    G.item_defs(["cluster_bomblet"])
    napalm_fire()
    print("warheads written:", ", ".join(names))


if __name__ == "__main__":
    main()
