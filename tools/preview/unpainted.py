"""Faces a model's painter missed: a box left out of every paint call keeps a see-through texture, and GeckoLib's cutout
layer draws nothing there (the War Factory's crane stood on an unpainted bogie and seemed to float). Runs a script's
model function with saving turned off and lists every exported face whose texels are mostly clear.

    tools\\.venv\\Scripts\\python.exe tools\\preview\\unpainted.py ammo_factories:war_factory
    tools\\.venv\\Scripts\\python.exe tools\\preview\\unpainted.py scud_storm:main
"""
from __future__ import annotations

import importlib
import sys
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
sys.path.insert(0, str(ROOT / "assets"))

from gz import geo as G  # noqa: E402


def main() -> None:
    target = sys.argv[1]
    module_name, _, fn_name = target.partition(":")
    found: list[tuple] = []

    def fake_save(self, tex, emissive=None):
        found.append((self, tex))
        return None, None
    G.GeoModel.save = fake_save
    G.save_animations = lambda *a, **k: None
    mod = importlib.import_module(module_name)
    if hasattr(mod, "save_animations"):
        mod.save_animations = G.save_animations
    for attr in ("write_json",):                          # the scripts' other outputs stay as they are
        if hasattr(mod, attr):
            setattr(mod, attr, lambda *a, **k: None)
    getattr(mod, fn_name or "main")()
    for g, tex in found:
        hidden = g.hidden_faces() | g.enclosed_faces()
        img = tex.a
        bad = []
        for b in g.boxes:
            for d, f in b.faces.items():
                if (b.name, d) in hidden or f.rect is None:
                    continue
                x, y, w, h = f.rect
                region = img[y:y + h, x:x + w, 3]
                if region.size and (region < 128).mean() > 0.5:
                    bad.append(f"{b.name}/{d}")
        print(f"{g.name}: {len(bad)} unpainted faces")
        for s in bad[:60]:
            print("   ", s)


if __name__ == "__main__":
    main()
