"""Slice 6e textures (DESIGN.md §16.4): the Frozen effect's icon and the ice drawn round a frozen creature.

- Frozen icon (18x18): a snowflake on a dark round, like Radiation Sickness's trefoil.
- Ice shell (32x32, translucent): clear blue ice, frosted white at its edges, with a few cracks and a glint; the block
  drawn round a frozen creature (IceShellRenderer) uses it on every face.
"""
from __future__ import annotations

import random
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from gz import paths  # noqa: E402
from gz.tex import Tex  # noqa: E402

ICE = [(16, 60, 92), (34, 98, 140), (70, 146, 196), (118, 190, 232), (170, 222, 248), (214, 242, 255), (245, 252, 255)]


def frozen_icon() -> Tex:
    """A snowflake centred on the icon: everything symmetric about its middle (between pixels 8 and 9), so the cross
    arms are two pixels wide."""
    t = Tex(18, 18)
    t.disc(9, 9, 8.2, (16, 30, 44, 255))
    t.disc(9, 9, 7.2, (26, 50, 72, 255))
    arm, diag, tip = ICE[5] + (255,), ICE[4] + (255,), ICE[6] + (255,)
    for i in range(5, 13):                     # the diagonals
        t.px(i, i, diag)
        t.px(i, 17 - i, diag)
    for i in range(3, 15):                     # the cross, two pixels wide
        for k in (8, 9):
            t.px(i, k, arm)
            t.px(k, i, arm)
    for a in (4, 13):                          # barbs near the ends of the cross
        for b in (7, 10):
            t.px(a, b, arm)
            t.px(b, a, arm)
    for x in (8, 9):
        for y in (8, 9):
            t.px(x, y, tip)
    return t


def ice_shell() -> Tex:
    rng = random.Random(611)
    t = Tex(32, 32)
    for y in range(32):
        for x in range(32):
            edge = min(x, y, 31 - x, 31 - y)
            base = ICE[3] if (x * 7 + y * 3) % 11 else ICE[4]
            alpha = 120 if edge > 2 else 170 + (2 - edge) * 25
            colour = ICE[5] if edge <= 1 else base
            t.px(x, y, colour + (alpha,))
    for _ in range(5):                         # cracks: short white zig-zags
        x, y = rng.randrange(4, 28), rng.randrange(4, 28)
        for _ in range(rng.randrange(5, 10)):
            t.px(x, y, ICE[6] + (200,))
            x = max(2, min(29, x + rng.choice((-1, 0, 1))))
            y = max(2, min(29, y + rng.choice((-1, 1))))
    for i in range(6):                         # a glint across one corner
        t.px(4 + i, 9 - i, ICE[6] + (230,))
        t.px(5 + i, 9 - i, ICE[6] + (200,))
    return t


def main() -> None:
    icon = paths.TEXTURES / "mob_effect" / "frozen.png"
    frozen_icon().save(icon)
    shell = paths.TEXTURES / "entity" / "ice_shell.png"
    shell.parent.mkdir(parents=True, exist_ok=True)
    ice_shell().save(shell)
    print(icon, shell)


if __name__ == "__main__":
    main()
