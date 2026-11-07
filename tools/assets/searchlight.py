"""The Searchlight (DESIGN.md §22.6): a big searchlight on a stand, after the anti-aircraft searchlights of the Second
World War and the Cold War's xenon lights: a round foot and a column, a yoke that turns on a turntable over it, and
between the yoke's arms the lamp drum on its trunnions, its front a polished bezel round the lens, a vent stack on top.
Two blocks high.

Built as it stands facing north (its lens to the north), its lower block's bottom middle the origin. Bones:
- "base": the foot and the column (still).
- "yoke": the turntable, the crossbar and the two arms; turned about up to sweep (the block entity sets it each frame).
- "lamp": the lamp drum on the yoke's trunnions; turned about the trunnions to raise the light (set each frame).
- "drum": in the lamp, the drum laid on its side (built standing, as the Tape Computer's reels are); "glow": the lit
  lens in front of the dark one, scaled to nothing while the light is off.
Animations: "idle" and "working" both still (the sweep is the block entity's: Java turns the bones).
The beam's texture (textures/fx/searchlight_beam.png): bright down its middle, fading to nothing at its sides.

    tools\\.venv\\Scripts\\python.exe tools\\assets\\searchlight.py
"""
from __future__ import annotations

import sys
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))

from gb import machine_kit as K  # noqa: E402
from gb import materials as M  # noqa: E402
from gb import paths  # noqa: E402
from gb.geo import Animation, GeoModel, save_animations  # noqa: E402
from gb.model import write_json  # noqa: E402
from gb.tex import Tex  # noqa: E402
import command_room as CR  # noqa: E402
import scud_storm as SS  # noqa: E402
import warheads as W  # noqa: E402

NAME = "searchlight"
ALL, SIDES = SS.ALL, SS.SIDES
#: The yoke's turn's pivot (its turntable's top), the trunnions' height; the drum's radius, its back and front (along its
#: axis, before it is laid on its side: up is forward).
YOKE_Y, TRUNNION_Y = 9.0, 17.0
DRUM_R, DRUM_BACK, DRUM_FRONT = 5.4, 12.0, 21.0
LENS_LIT = (255, 246, 222)


def build() -> tuple[GeoModel, dict]:
    g = GeoModel(NAME, tex_size=(128, 128))
    parts: dict[str, list[str]] = {}
    SS.ROUND.clear()

    def box(group, n, frm, to, bone, faces=ALL):
        g.box(n, frm, to, faces=faces, bone=bone)
        parts.setdefault(group, []).append(n)

    g.bone("base")
    # (Each round part's caps on as few levels as keep their overlaps apart where they show: the foot's top, round the
    # column, flickered on one plane where it showed past it.) The column reaches a pixel down into the foot: its sides'
    # lowest row of texels then lies all inside the foot (half in, its shade came from inside it: dark specks).
    parts["foot"] = ring(g, "foot", 5.0, 0.0, 1.2, "base", hi=1.8)
    parts["column"] = ring(g, "column", 1.8, 1.2, 8.0, "base", root=1.0)
    parts["collar"] = ring(g, "collar", 2.6, 8.0, YOKE_Y + 0.1, "base", lo=1.8, toward="hi")
    g.bone("yoke", pivot=(0.0, YOKE_Y, 0.0), parent="base")
    parts["turntable"] = ring(g, "turntable", 3.2, YOKE_Y, YOKE_Y + 1.0, "yoke", lo=2.6, hi=0.0)
    box("yoke", "crossbar", (-6.6, YOKE_Y + 1.0, -1.2), (6.6, YOKE_Y + 2.2, 1.2), "yoke")
    for side, x in (("l", -6.6), ("r", 5.6)):
        box("yoke", f"arm_{side}", (x, YOKE_Y + 2.2, -1.0), (x + 1.0, TRUNNION_Y + 1.2, 1.0), "yoke",
            faces=("north", "south", "east", "west", "up"))
        hub_x = x - 0.6 if side == "l" else x + 1.0
        box("hub", f"hub_{side}", (hub_x, TRUNNION_Y - 1.0, -1.0), (hub_x + 0.6, TRUNNION_Y + 1.0, 1.0), "yoke")
    g.bone("lamp", pivot=(0.0, TRUNNION_Y, 0.0), parent="yoke")
    # The drum laid on its side: built standing along y about the trunnions' axis (its front up), turned to face north.
    g.bone("drum", pivot=(0.0, TRUNNION_Y, 0.0), parent="lamp", rotation=(-90.0, 0.0, 0.0))
    cy = TRUNNION_Y
    # The back: its plate, a boss in its middle (the lamp house's cap); the drum closed round the plate.
    parts["back"] = (ring(g, "back", 4.6, cy - 6.4, cy - 4.9, "drum", lo=1.6)
                     + ring(g, "back_boss", 1.6, cy - 7.0, cy - 6.2, "drum", lo=0.0))
    parts["drum"] = ring(g, "drum_body", DRUM_R, cy - 5.0, cy + 3.7, "drum", lo=4.6)
    parts["bezel"] = ring(g, "bezel", DRUM_R + 0.4, cy + 3.6, cy + 4.6, "drum", lo=DRUM_R, hi=4.3)
    # The lens, dark glass a hair proud of the bezel (its own disc: painted on the bezel's planks it came out ragged).
    parts["lens"] = ring(g, "lens", 4.3, cy + 4.5, cy + 4.7, "drum", hi=0.0, flat=True)
    # The vent stack on the drum's top (the drum's +z, as it lies), its handles at its sides.
    box("stack", "stack", (-1.4, cy - 3.0, DRUM_R - 0.4), (1.4, cy + 0.6, DRUM_R + 1.8), "drum",
        faces=("north", "south", "east", "west", "up"))
    box("stack", "stack_cap", (-1.9, cy - 3.4, DRUM_R + 1.8), (1.9, cy + 1.0, DRUM_R + 2.4), "drum")
    for side, x in (("l", -DRUM_R - 0.8), ("r", DRUM_R + 0.2)):
        box("handle", f"handle_{side}", (x, cy - 1.5, -0.4), (x + 0.6, cy + 1.5, 0.4), "drum")
    # The lit lens a hair in front of the bezel's glass, in its own bone (scaled to nothing while the light is off).
    g.bone("glow", pivot=(0.0, cy + 4.6, 0.0), parent="drum")
    parts["lit"] = ring(g, "lit", 4.4, cy + 4.6, cy + 4.95, "glow", hi=0.0, flat=True)
    return g, parts


def ring(g: GeoModel, name: str, r: float, y0: float, y1: float, bone: str, **kw) -> list[str]:
    """A round part about the bone's axis (warheads.round_section), its planks kept for painting round."""
    names = W.round_section(SS.OnBone(g, bone), name, r, y0, y1, c=(0.0, 0.0), **kw)
    SS.ROUND.extend(names)
    return names


OLIVE = M.painted(M.OLIVE_RAMP, tone=0.5, wear=0.22)
BEZEL = M.steel(tone=0.7, brushed=0.06)


def bezel_face(f: M.Face, img: np.ndarray) -> None:
    """The bezel's front: its steel ring round the lens, darker at its inner lip."""
    if f.d != "up":
        return
    r = np.hypot(f.X, f.Z)
    rgb = img[..., :3].astype(np.float32)
    rgb[(r >= 4.3) & (r < 4.9)] *= 0.62
    img[..., :3] = np.clip(rgb, 0, 255).astype(np.uint8)


#: The dark lens: dark glass, even all over.
LENS_DARK = (38, 46, 54)


def paint(g: GeoModel, parts: dict) -> tuple[Tex, Tex]:
    k = K.Kit(g, {}, seed=6601, ao_reach=1.6, turned=True)
    W.round_faces(k.S, SS.ROUND, top=True, bottom=False, foot=False)
    S = k.S
    t = Tex(g.tex_w, g.tex_h)
    S.paint(t, parts["foot"] + parts["column"] + parts["collar"] + parts["turntable"] + parts["yoke"], OLIVE)
    S.paint(t, parts["hub"] + parts["handle"], K.STEEL)
    S.paint(t, parts["back"] + parts["drum"] + parts["stack"], OLIVE)
    S.overlay(t, parts["stack"], lambda f, img: M.darken(img, (np.floor(f.Y * 1.0) % 2 == 0) & (np.abs(f.X) < 1.0), 0.45),
              only=("north", "south"))
    S.paint(t, parts["bezel"], BEZEL)
    S.overlay(t, parts["bezel"], bezel_face)
    CR.fill_faces(g, t, parts["lens"], LENS_DARK)
    glow = Tex(g.tex_w, g.tex_h)
    CR.fill_faces(g, t, parts["lit"], LENS_LIT)
    CR.fill_faces(g, glow, parts["lit"], LENS_LIT)
    return t, glow


def animations() -> list[Animation]:
    """Still both ways: the block entity turns the yoke and the lamp."""
    return [Animation("idle", 0.05, loop="hold_on_last_frame"), Animation("working", 0.05, loop="hold_on_last_frame")]


def beam_texture() -> None:
    """The beam's light across it: bright down its middle, a soft fall to nothing at its sides (alike all along); and the
    lens's glare, a soft round glow."""
    w, h = 32, 8
    u = (np.arange(w) + 0.5) / w * 2 - 1
    a = np.clip(1 - np.abs(u), 0, 1) ** 1.6
    img = np.zeros((h, w, 4), np.uint8)
    img[..., 0:3] = 255
    img[..., 3] = (a * 255).astype(np.uint8)[None, :]
    Image.fromarray(img, "RGBA").save(paths.TEXTURES / "fx" / "searchlight_beam.png")
    n = 32
    yy, xx = np.mgrid[0:n, 0:n]
    r = np.hypot(xx + 0.5 - n / 2, yy + 0.5 - n / 2) / (n / 2)
    glow = np.clip(1 - r, 0, 1) ** 2.2
    img = np.zeros((n, n, 4), np.uint8)
    img[..., 0:3] = 255
    img[..., 3] = (glow * 255).astype(np.uint8)
    Image.fromarray(img, "RGBA").save(paths.TEXTURES / "fx" / "searchlight_glare.png")


ICON = [
    "................",
    "......kkkk......",
    ".....kooook.....",
    "....kwwwwwok....",
    "....kwwwwwok....",
    "....kwwwwwok....",
    "....kwwwwwok....",
    ".....kooook.....",
    ".....y.kk.y.....",
    ".....y.kk.y.....",
    ".....yyyyyy.....",
    ".......kk.......",
    ".......kk.......",
    ".....kkkkkk.....",
    "....kkkkkkkk....",
    "................",
]


def icon() -> None:
    col = {"k": (44, 50, 34), "o": (88, 100, 58), "w": (250, 240, 210), "y": (74, 86, 48)}
    img = np.zeros((16, 16, 4), np.uint8)
    for y, row in enumerate(ICON):
        for x, c in enumerate(row):
            if c != ".":
                img[y, x] = (*col[c], 255)
    Image.fromarray(img, "RGBA").save(paths.TEXTURES / "item" / f"{NAME}.png")
    write_json(paths.MODELS / "item" / f"{NAME}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"groundburst:item/{NAME}"}})
    write_json(paths.ASSETS / "items" / f"{NAME}.json", {"model": {"type": "minecraft:model", "model": f"groundburst:item/{NAME}"}})


def block_files() -> None:
    """Both halves draw nothing themselves (the lower one's block entity draws the light): a model with its particle."""
    write_json(paths.MODELS / "block" / f"{NAME}.json", {"textures": {"particle": "groundburst:block/steel_panel"}})
    write_json(paths.ASSETS / "blockstates" / f"{NAME}.json", {"multipart": [{"apply": {"model": f"groundburst:block/{NAME}"}}]})


def main() -> None:
    g, parts = build()
    g.layout()
    t, glow = paint(g, parts)
    W.match_walls(g, t, [b.name for b in g.boxes], entity=True)
    g.save(t, glow)
    print(save_animations(NAME, animations()))
    print(NAME, "tex", g.tex_w, g.tex_h)
    beam_texture()
    icon()
    block_files()


if __name__ == "__main__":
    main()
