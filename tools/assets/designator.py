"""The Laser Designator and its family (DESIGN.md §17.13).

- Laser Designator (item model): built as a spyglass is, its optics along +y (the objective up, the eyecup down), the
  handle +z, so carried it points where the arm does; a second model, ``laser_designator_raised``, turns it back along
  the gaze while it is in use (raised to the eye). An olive-drab housing (8 x 9 x 6), its front a big objective (dark coated
  glass in a gunmetal bezel) beside the laser's smaller port, a rubber eyecup behind, a rubber-gripped carry handle on
  its top (+z), the battery pack on its right (+x), the fire and range buttons and the rangefinder's little display on its
  left, a yellow laser-warning label, the front cover's seam. Painted with the material library.
- Laser Guidance Unit (a missile part, beside the GPS and inertial units): a gunmetal electronics box under a white
  seeker collar with a red band (laser guidance), the semi-active seeker's dark glass dome on top. Material library.
- ``textures/fx/laser.png`` (64 x 48): the beam's cross-section along its top third (a bright core in a soft glow, the
  same all along it: a laser does not flicker as lightning does), then a spot (a hot point in a halo, a faint four-point
  flare) and a thin ring in the two cells below; white, tinted red in game, drawn additively.
- ``textures/gui/designator_scope.png`` (512 x 512, smoothed): the eyepiece, black round a round opening (radius 0.44 of
  its side) that darkens toward its rim.
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))

from gb import machine_kit as K  # noqa: E402
from gb import materials as M  # noqa: E402
from gb import palette as P  # noqa: E402
from gb import paths  # noqa: E402
from gb.model import BlockModel, write_json  # noqa: E402
from gb.tex import Tex  # noqa: E402
import grenades as G  # noqa: E402
import warheads as W  # noqa: E402

SIDES = ("north", "south", "east", "west")
GLASS = {"body": (22, 30, 56), "rim": (40, 54, 92), "glint": (150, 170, 205), "soft": (70, 88, 128)}
RED_GLASS = {"body": (40, 9, 8), "glint": (150, 52, 40)}

# The designator's optics: the objective's and the port's middles on its front, and their radii.
OBJ = (9.0, 8.0, 2.2)
PORT = (5.4, 8.0, 1.2)
FRONT = 11.5          # the housing's front (its top, y)
BACK = 2.5            # its back, where the eyecup is


def _sections(m: BlockModel):
    groups: dict[str, list[str]] = {}
    lips: dict[str, tuple[bool, bool, bool]] = {}

    def sec(group, name, r, y0, y1, cx, cz, caps, top=True, bottom=False, foot=False):
        names = W.section(m, name, r, y0, y1, W.sides_for(r), cx=cx, cz=cz, caps=caps)
        groups.setdefault(group, []).extend(names)
        for n in names:
            lips[n] = (top, bottom, foot)
        return names
    return groups, lips, sec


def _lens(f: M.Face, img: np.ndarray, cx: float, cz: float, r: float, glass: dict) -> None:
    """Coated glass on a front-facing cap: dark, lighter toward its rim, a glint up and to the left, a softer one opposite."""
    if f.d not in ("up", "down"):
        return
    d = np.hypot(f.X - cx, f.Z - cz)
    inside = d < r
    img[inside, :3] = glass["body"]
    if "rim" in glass:
        img[inside & (d > r - 0.75), :3] = glass["rim"]
    if "soft" in glass:
        img[inside & ((f.X - cx) > 0.2) & ((f.Z - cz) > 0.2) & (d > r * 0.55), :3] = glass["soft"]
    glint = inside & ((f.X - cx) < 0) & ((f.Z - cz) < 0) & (d > r * 0.6)
    img[glint, :3] = glass["glint"]


def designator() -> BlockModel:
    m = BlockModel("laser_designator", tex_size=(64, 64), kind="item", render_type="minecraft:cutout")
    m.open_ok = set()
    groups, lips, sec = _sections(m)
    m.box("body", (4, BACK, 5), (12, FRONT, 11))
    sec("objective", "objective", OBJ[2], FRONT, FRONT + 1.1, OBJ[0], OBJ[1], ("up",))
    sec("port", "port", PORT[2], FRONT, FRONT + 0.7, PORT[0], PORT[1], ("up",))
    # The eyecup hangs under the housing: its planks reach up into it (unseen), its end shows.
    sec("eyecup", "eyecup", 1.7, 0.6, BACK + 0.5, OBJ[0], OBJ[1], ("down",), top=False, bottom=True)
    # The carry handle on its top, the battery on its right, the buttons on its left.
    m.box("post_a", (7.5, 4.0, 11), (8.5, 5.0, 11.7), faces=("east", "west", "up", "down"))
    m.box("post_b", (7.5, 9.0, 11), (8.5, 10.0, 11.7), faces=("east", "west", "up", "down"))
    m.box("grip", (7.2, 3.6, 11.7), (8.8, 10.4, 12.8))
    m.box("battery", (12, 3.5, 6.0), (13.2, 8.5, 10.0), faces=("north", "south", "east", "up", "down"))
    m.box("latch", (13.2, 5.6, 7.3), (13.5, 6.4, 8.7), faces=("north", "south", "east", "up", "down"))
    m.box("button_fire", (3.6, 3.4, 6.2), (4.0, 4.3, 7.2), faces=("north", "south", "west", "up", "down"))
    m.box("button_range", (3.6, 3.4, 7.8), (4.0, 4.3, 8.8), faces=("north", "south", "west", "up", "down"))
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    k = K.Kit(m, {}, seed=1709, ao_reach=1.5, turned=True)
    for n, (top, bottom, foot) in lips.items():
        W.round_faces(k.S, [n], top, bottom, foot)
    k.paint(t, ["body"], K.OLIVE)
    k.paint(t, groups["objective"] + groups["port"], W.metal(W.GUNMETAL, 0.38))
    k.paint(t, groups["eyecup"], K.RUBBER)
    k.paint(t, ["post_a", "post_b"], W.metal(W.GUNMETAL, 0.42))
    k.paint(t, ["grip"], K.RUBBER)
    k.ribs(t, ["grip"], every=2.0, k=0.78, along="v")
    k.paint(t, ["battery"], K.BLACK)
    k.paint(t, ["latch"], W.metal(W.STEEL, 0.55))
    k.paint(t, ["button_fire"], K.RED)
    k.paint(t, ["button_range"], W.metal(W.BLACK, 0.4))

    k.overlay(t, groups["objective"], lambda f, img: _lens(f, img, OBJ[0], OBJ[1], OBJ[2] - 0.6, GLASS))
    k.overlay(t, groups["port"], lambda f, img: _lens(f, img, PORT[0], PORT[1], PORT[2] - 0.45, RED_GLASS))
    k.overlay(t, groups["eyecup"], lambda f, img: _lens(f, img, OBJ[0], OBJ[1], 0.95, GLASS) if f.d == "down" else None)

    def housing(f: M.Face, img: np.ndarray) -> None:
        if f.d in SIDES:
            M.darken(img, np.abs(f.Y - 10.0) < 0.5, 0.72)              # the front cover's seam, round its sides
        if f.d == "west":                                               # the rangefinder's display over the buttons,
            win = (f.Z > 5.0) & (f.Z < 9.0) & (f.Y > 4.5) & (f.Y < 6.5)   # the laser warning label over that
            img[win, :3] = (14, 12, 8)
            img[win & (f.Y > 5.5) & (f.Z < 8.0), :3] = (214, 140, 52)          # its digits, amber
            lab = (f.Z > 6.0) & (f.Z < 9.0) & (f.Y > 7.5) & (f.Y < 9.5)
            img[lab, :3] = P.HAZARD_YELLOW[4][:3]
            img[lab & (np.abs(f.Z - 7.5) < 0.5) & (f.Y < 8.5), :3] = (20, 18, 12)    # its black sun
        if f.d == "north":                                              # a stencilled plate, plain
            M.darken(img, (f.X > 5.0) & (f.X < 11.0) & (f.Y > 3.5) & (f.Y < 5.5), 0.6)
    k.overlay(t, ["body"], housing)
    lo, hi = m.bounds()
    c = [(lo[i] + hi[i]) / 2 for i in range(3)]

    def ctx(scale: float, base: tuple[float, float, float], rotation=(0, 0, 0)) -> dict:
        return {"rotation": list(rotation), "translation": [round(base[i] - (c[i] - 8) * scale, 3) for i in range(3)],
                "scale": [scale] * 3}
    m.fit_display(gui_rotation=(-70, 0, 120), gui_margin=0.5)        # on its side, the lens toward the left
    m.display["ground"] = ctx(0.5, (0, 1, 0))
    m.display["fixed"] = ctx(0.85, (0, 0, 0), (-90, 0, 180))
    m.display["head"] = ctx(0.8, (0, 6, 0))
    # Carried (the arm hanging): its optics along the arm's forward, the handle up, the battery outward, as built.
    # The game mirrors a right hand's transform for the left, so both take the same.
    for hand in ("right", "left"):
        m.display[f"thirdperson_{hand}hand"] = ctx(0.55, (0, -1.5, 0))
        m.display[f"firstperson_{hand}hand"] = ctx(0.36, (2.0, 3.0, 0), (-90, 0, -12))
    m.save(t)
    # Raised to the eye (in use: the spyglass pose, the arm up 110 degrees and in 15): turned back by as much, so the
    # optics run along the gaze, the handle up and the eyecup at the right eye (worked out from the arm's rotations).
    raised = {"rotation": [-110, 0, -15], "translation": [-2.19, 0.46, -0.79], "scale": [0.55] * 3}
    write_json(paths.MODELS / "item" / "laser_designator_raised.json",
               {"parent": "groundburst:item/laser_designator", "display": {"thirdperson_righthand": raised, "thirdperson_lefthand": raised}})
    write_json(paths.ASSETS / "items" / "laser_designator.json", {"model": {
        "type": "minecraft:condition", "property": "minecraft:using_item",
        "on_true": {"type": "minecraft:model", "model": "groundburst:item/laser_designator_raised"},
        "on_false": {"type": "minecraft:model", "model": "groundburst:item/laser_designator"}}})
    return m


def guidance_unit() -> BlockModel:
    m = BlockModel("laser_guidance_unit", tex_size=(64, 64), kind="item", render_type="minecraft:cutout")
    m.open_ok = set()
    groups, lips, sec = _sections(m)
    m.box("box", (4.5, 1, 4.5), (11.5, 5, 11.5))
    sec("collar", "collar", 2.6, 5.0, 7.0, 8.0, 8.0, ("up",), foot=True)
    rings, top, last = W.nose(m, "dome", 2.0, 7.0, 1.8, "dome", 2, min_r=1.2)
    for ring in rings:
        groups.setdefault("dome", []).extend(ring)
        for n in ring:
            lips[n] = (True, False, False)
    m.layout()
    t = Tex(m.tex_w, m.tex_h)
    k = K.Kit(m, {}, seed=1713, ao_reach=1.5, turned=True)
    for n, (top_lip, bottom, foot) in lips.items():
        W.round_faces(k.S, [n], top_lip, bottom, foot)
    k.paint(t, ["box"], W.metal(W.GUNMETAL, 0.45))
    k.paint(t, groups["collar"], W.paint(W.WHITE, 0.66, 0.1))
    k.paint(t, groups["dome"], M.Material(ramp=W.ramp9(W.VIOLET), tone=0.12, mottle=0.02, grain=0.01, dither=0.3, edge_light=0.25,
                                          edge_dark=0.05, ao=0.2, grime=0.0, dust=0.0, facing=W.ROUND))

    def collar(f: M.Face, img: np.ndarray) -> None:
        if f.d in SIDES:
            img[np.abs(f.Y - 5.5) < 0.5, :3] = P.WARNING_RED[4][:3]     # laser guidance's red band
    k.overlay(t, groups["collar"], collar)

    def dome(f: M.Face, img: np.ndarray) -> None:
        ang = W.angle_of(f)                                          # coated glass: a bright glint on its front left,
        if f.d in SIDES:                                             # a softer one opposite
            M.lighten(img, (ang > -80) & (ang < -20), 1.0, 120)
            M.lighten(img, (ang > 100) & (ang < 160), 1.0, 40)
        if f.d == "up":
            M.lighten(img, (f.X < 8.0) & (f.Z > 8.0), 1.0, 110)
    k.overlay(t, groups["dome"], dome)

    def box(f: M.Face, img: np.ndarray) -> None:
        if f.d == "south":
            img[(np.abs(f.Y - 3.5) < 0.5) & (np.abs(f.X - 5.0) < 0.5), :3] = P.WARNING_RED[5][:3]   # its armed lamp
            M.darken(img, (np.abs(f.Y - 2.5) < 0.5) & (f.X > 7.0) & (f.X < 11.0), 0.6)              # a vent slot
    k.overlay(t, ["box"], box)
    G.finish(m, t)
    return m


def laser_texture() -> None:
    w, h = 64, 48
    img = np.zeros((h, w, 4), np.float32)
    v = ((np.arange(16) + 0.5) / 16 * 2 - 1)[:, None]
    beam = np.clip(np.exp(-(v / 0.15) ** 2) + 0.5 * np.exp(-(v / 0.5) ** 2), 0, 1) * (1 - np.abs(v) ** 6)
    img[0:16, :, :3] = 1
    img[0:16, :, 3] = beam * np.ones((1, w))
    c = (np.arange(32) + 0.5) / 32 * 2 - 1
    x, y = np.meshgrid(c, c)
    r = np.hypot(x, y)
    fade = np.clip(1 - r, 0, 1) ** 1.5
    flare = (np.exp(-(y / 0.06) ** 2) + np.exp(-(x / 0.06) ** 2)) * np.clip(1 - r, 0, 1) ** 2
    spot = np.clip(np.exp(-(r / 0.2) ** 2) + 0.6 * np.exp(-(r / 0.45) ** 2) * fade + 0.3 * flare, 0, 1)
    ring = np.clip(np.exp(-((r - 0.78) / 0.07) ** 2), 0, 1) * (r < 0.98)
    img[16:48, 0:32, :3] = 1
    img[16:48, 0:32, 3] = spot
    img[16:48, 32:64, :3] = 1
    img[16:48, 32:64, 3] = ring
    out = paths.TEXTURES / "fx" / "laser.png"
    Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8), "RGBA").save(out)
    print(out)


def scope_texture() -> None:
    n = 512
    c = (np.arange(n) + 0.5) / n * 2 - 1
    x, y = np.meshgrid(c, c)
    r = np.hypot(x, y)
    R = 0.88                                                      # DesignatorScope.APERTURE: 0.44 of the side
    edge = np.clip((r - R) / 0.012 + 0.5, 0, 1)                  # black beyond the opening, its edge a few pixels soft
    vignette = 0.6 * np.clip((r - 0.45 * R) / (0.55 * R), 0, 1) ** 2
    alpha = np.maximum(edge, vignette * (r < R))
    img = np.zeros((n, n, 4), np.float32)
    warm = np.exp(-((r - R) / 0.02) ** 2)                        # a faint warm lip where the eyepiece's barrel catches light
    img[..., 0] = 0.16 * warm
    img[..., 1] = 0.09 * warm
    img[..., 2] = 0.04 * warm
    img[..., 3] = alpha
    out = paths.TEXTURES / "gui" / "designator_scope.png"
    Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8), "RGBA").save(out)
    out.with_name(out.name + ".mcmeta").write_text(json.dumps({"texture": {"blur": True}}, indent=2) + "\n", encoding="utf-8")
    print(out)


def main() -> None:
    for m in (designator(), guidance_unit()):
        issues = m.zfight()
        print(f"{m.name}: {len(issues)} z-fight issue(s)")
        for issue in issues:
            print("  " + issue)
    G.item_defs(["laser_guidance_unit"])                       # (the designator's own: raised while in use)
    laser_texture()
    scope_texture()
    print("designator written")


if __name__ == "__main__":
    main()
