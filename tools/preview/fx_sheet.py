"""Contact sheets of the Spectacle screenshots (``gradlew runFxShots``): one sheet per blast profile, its moments in
order, each labelled with its tick. Output: ``tools/out/previews/fx_<profile>.png``.

    tools/.venv/Scripts/python.exe tools/preview/fx_sheet.py [profile ...]
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from gb import paths  # noqa: E402

SHOTS = paths.ROOT / "run" / "screenshots"
COLS, WIDTH = 4, 480


def sheet(profile: str) -> Path | None:
    shots = [p for p in SHOTS.glob(f"fx_{profile}_*.png") if re.fullmatch(rf"fx_{profile}_\d+\.png", p.name)]
    shots.sort(key=lambda p: int(re.search(r"_(\d+)\.png$", p.name).group(1)))
    if not shots:
        return None
    first = Image.open(shots[0])
    height = round(WIDTH * first.height / first.width)
    rows = (len(shots) + COLS - 1) // COLS
    out = Image.new("RGB", (COLS * WIDTH, rows * height), (20, 20, 20))
    draw = ImageDraw.Draw(out)
    for i, shot in enumerate(shots):
        x, y = (i % COLS) * WIDTH, (i // COLS) * height
        out.paste(Image.open(shot).convert("RGB").resize((WIDTH, height), Image.LANCZOS), (x, y))
        tick = re.search(r"_(\d+)\.png$", shot.name).group(1)
        draw.rectangle((x, y, x + 70, y + 16), fill=(0, 0, 0))
        draw.text((x + 4, y + 2), f"t={tick}", fill=(255, 255, 255))
    path = paths.PREVIEWS / f"fx_{profile}.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    out.save(path)
    return path


def main() -> None:
    profiles = sys.argv[1:] or ["small", "medium", "large", "nuclear", "nuclear_close", "nuclear_night", "meltdown", "meltdown_dusk", "suits", "suits_back", "grenades"]
    for profile in profiles:
        print(sheet(profile))


if __name__ == "__main__":
    main()
