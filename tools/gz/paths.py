"""Well-known project paths."""
from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MODID = "groundzero"

RESOURCES = ROOT / "src" / "main" / "resources"
ASSETS = RESOURCES / "assets" / MODID
DATA = RESOURCES / "data" / MODID

TEXTURES = ASSETS / "textures"
MODELS = ASSETS / "models"
GEO = ASSETS / "geckolib" / "models"          # GeckoLib 5 resource roots
ANIMATIONS = ASSETS / "geckolib" / "animations"
SOUNDS = ASSETS / "sounds"

# Vanilla assets extracted from the decompiled client sources (build/mcsrc). Used only by the
# preview renderer to draw vanilla parents/textures (e.g. stone behind an ore overlay); never shipped.
VANILLA_ASSETS = ROOT / "build" / "mcsrc" / "assets" / "minecraft"

# Scratch output (previews, contact sheets). Git-ignored.
OUT = ROOT / "tools" / "out"
PREVIEWS = OUT / "previews"
