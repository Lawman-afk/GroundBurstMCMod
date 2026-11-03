"""Turns main's sources (Minecraft 26.3) into this branch's (Minecraft 1.21.1): docs/BACKPORT.md.

Reads every file of main at the commit in base.txt straight from git (so each run starts from main's own text) and writes
its port here: the Java sources through RULES, the resources through their moves and fixes. Main's files that have no
place here (DROP) are left out; files edited by hand here (manual.txt) are kept as they are. This branch's own files
(the compatibility classes in dev.groundburst.port, this tool, docs/BACKPORT.md) are not main's and are never touched.

    tools\\.venv\\Scripts\\python.exe tools\\backport\\port.py              (everything)
    tools\\.venv\\Scripts\\python.exe tools\\backport\\port.py <path> ...   (just those of main's files)
    tools\\.venv\\Scripts\\python.exe tools\\backport\\port.py --show <path> (print one file's port)
"""
from __future__ import annotations

import json
import re
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
BASE = (HERE / "base.txt").read_text(encoding="utf-8").split()[0]
MANUAL = {line.strip() for line in (HERE / "manual.txt").read_text(encoding="utf-8").splitlines()
          if line.strip() and not line.startswith("#")} if (HERE / "manual.txt").exists() else set()

# Main's files with no place here: its development tools (photographers, the blast bench, the world survey), its data
# generators (their output is ported instead, below) and its GameTests (to come).
DROP = [re.compile(p) for p in (
    r"src/main/java/dev/groundburst/datagen/.*",
    r"src/main/java/dev/groundburst/gametest/.*",
    r"src/main/java/dev/groundburst/client/fx/(FxPhotographer|SitePhotographer|BlastBenchClient|WorldSurveyClient|DevWorlds)\.java",
    r"src/main/java/dev/groundburst/client/manual/ManualPhotographer\.java",
    r"src/main/java/dev/groundburst/blast/BlastBench\.java",
    r"src/main/java/dev/groundburst/world/structure/WorldSurvey\.java",
    r"src/main/resources/data/groundburst/structure/test/.*",          # the GameTests' structures
    r"docs/BACKPORT\.md", r"tools/backport/.*",
)]
# Main's paths that are this branch's own (build files changed here by hand): never overwritten.
OWN = {"build.gradle", "gradle.properties", "src/main/templates/META-INF/neoforge.mods.toml"}


def git(*args: str) -> bytes:
    return subprocess.run(["git", "-C", str(ROOT), *args], check=True, capture_output=True).stdout


def main_files() -> list[str]:
    return git("ls-tree", "-r", "--name-only", BASE).decode("utf-8").splitlines()


_batch = None


def read_main(path: str) -> bytes:
    """A file of main's at BASE (through one long-running `git cat-file --batch`: thousands of files)."""
    global _batch
    if _batch is None:
        _batch = subprocess.Popen(["git", "-C", str(ROOT), "cat-file", "--batch"], stdin=subprocess.PIPE, stdout=subprocess.PIPE)
    _batch.stdin.write(f"{BASE}:{path}\n".encode("utf-8"))
    _batch.stdin.flush()
    header = _batch.stdout.readline().decode("utf-8").split()
    if len(header) < 3 or header[1] == "missing":
        raise FileNotFoundError(path)
    data = _batch.stdout.read(int(header[2]))
    _batch.stdout.read(1)                                   # the newline after each object
    return data


# --- Java -----------------------------------------------------------------------------------------------------------------
def sub(pattern: str, repl, flags: int = 0):
    return re.compile(pattern, flags), repl


# Whole lines removed: hooks into the dropped tools.
DROP_LINES = [re.compile(p) for p in (
    r"^\s*dev\.groundburst\.client\.fx\.(FxPhotographer|BlastBenchClient|WorldSurveyClient|SitePhotographer)\.register\(\);\s*$",
    r"^\s*ManualPhotographer\.register\(\);\s*$",
    r"^\s*BlastBench\.register\(\);\s*$",
    r"^\s*WorldSurvey\.register\(\);\s*$",
)]
# Whole statements removed (they may run over several lines): hooks into the dropped tools.
DROP_STATEMENTS = [re.compile(p, re.M) for p in (
    r"^[ \t]*dev\.groundburst\.client\.fx\.FxPhotographer\.\w+ = [^;]*;[ \t]*\r?\n",
)]

# Imports: 26.x class -> 1.21.1 class (or this branch's stand-in); None drops the import.
IMPORTS = {
    "net.minecraft.resources.Identifier": "net.minecraft.resources.ResourceLocation",
    "net.minecraft.world.entity.EntityTypes": "net.minecraft.world.entity.EntityType",
    "net.minecraft.client.renderer.rendertype.RenderTypes": "net.minecraft.client.renderer.RenderType",
    "net.minecraft.client.renderer.rendertype.RenderType": "net.minecraft.client.renderer.RenderType",
    "net.minecraft.util.LightCoordsUtil": "net.minecraft.client.renderer.LightTexture",
    "net.minecraft.world.level.storage.ValueInput": "dev.groundburst.port.ValueInput",
    "net.minecraft.world.level.storage.ValueOutput": "dev.groundburst.port.ValueOutput",
    "net.minecraft.client.gui.GuiGraphicsExtractor": "dev.groundburst.port.GuiGraphicsExtractor",
    "net.minecraft.util.ARGB": "dev.groundburst.port.ARGB",
    "net.neoforged.neoforge.client.network.ClientPacketDistributor": "net.neoforged.neoforge.network.PacketDistributor",
    "net.minecraft.world.entity.animal.pig.Pig": "net.minecraft.world.entity.animal.Pig",
    "net.minecraft.world.entity.animal.cow.Cow": "net.minecraft.world.entity.animal.Cow",
    "net.minecraft.client.renderer.RenderPipelines": "dev.groundburst.port.RenderPipelines",
    "com.mojang.blaze3d.pipeline.RenderPipeline": "dev.groundburst.port.RenderPipeline",
    "org.joml.Matrix3x2fStack": "dev.groundburst.port.Matrix3x2fStack",
    # GeckoLib 5 -> 4
    "com.geckolib.animatable.manager.AnimatableManager": "software.bernie.geckolib.animation.AnimatableManager",
    "com.geckolib.cache.model.GeoBone": "software.bernie.geckolib.cache.object.GeoBone",
    "com.geckolib.cache.model.BakedGeoModel": "software.bernie.geckolib.cache.object.BakedGeoModel",
}

RULES = [
    # --- names ------------------------------------------------------------------------------------------------------------
    sub(r"\bIdentifier\b", "ResourceLocation"),
    sub(r"\bcom\.geckolib\.", "software.bernie.geckolib."),
    sub(r"\bsoftware\.bernie\.geckolib\.cache\.model\.", "software.bernie.geckolib.cache.object."),
    sub(r"\bsoftware\.bernie\.geckolib\.animatable\.manager\.AnimatableManager\b", "software.bernie.geckolib.animation.AnimatableManager"),
    sub(r"\bEntityTypes\.", "EntityType."),
    sub(r"\bRenderTypes\.", "RenderType."),
    sub(r"\bLightCoordsUtil\.", "LightTexture."),
    sub(r"\bClientPacketDistributor\.sendToServer\(", "PacketDistributor.sendToServer("),
    sub(r"\bPushReaction\.IMMOVEABLE\b", "PushReaction.BLOCK"),
    sub(r"\bPushReaction\.POPPED\b", "PushReaction.DESTROY"),
    sub(r"\bTags\.BlockItems\.(\w+)\.block\(\)", r"Tags.Blocks.\1"),
    sub(r"\bTags\.BlockItems\.(\w+)\.item\(\)", r"Tags.Items.\1"),
    # --- methods --------------------------------------------------------------------------------------------------------
    sub(r"\.sendOverlayMessage\(((?:[^()]|\((?:[^()]|\([^()]*\))*\))*)\)", r".displayClientMessage(\1, true)"),
    sub(r"\bChunkPos\.containing\(", "new ChunkPos("),
    sub(r"\bChunkPos\.pack\(", "ChunkPos.asLong("),
    sub(r"\.getMinY\(\)", ".getMinBuildHeight()"),
    # 26.x's top is the highest block (inclusive); 1.21.1's getMaxBuildHeight is the first above it.
    sub(r"((?:[A-Za-z_]\w*(?:\([^()]*\))?\.)*[A-Za-z_]\w*(?:\([^()]*\))?)\.getMaxY\(\)", r"(\1.getMaxBuildHeight() - 1)"),
    sub(r"\.gui\.screen\(\)", ".screen"),
    sub(r"\.gui\.setScreen\(", ".setScreen("),
    # Items and blocks name their own registry key in 26.x (Properties.setId); 1.21.1 has no such step.
    sub(r"\.setId\((?:[^()]|\((?:[^()]|\([^()]*\))*\))*\)", ""),
    # --- saving: main's ValueOutput / ValueInput over 1.21.1's CompoundTag (dev.groundburst.port) -----------------------
    sub(r"(\b(?:public|protected) void saveAdditional\()ValueOutput (\w+)\)(\s*\{)",
        r"\1net.minecraft.nbt.CompoundTag tag_, net.minecraft.core.HolderLookup.Provider registries_)\3 ValueOutput \2 = new ValueOutput(tag_, registries_);"),
    sub(r"\bsuper\.saveAdditional\((\w+)\);", r"super.saveAdditional(\1.tag(), \1.lookup());"),
    sub(r"(\b(?:public|protected) void loadAdditional\()ValueInput (\w+)\)(\s*\{)",
        r"\1net.minecraft.nbt.CompoundTag tag_, net.minecraft.core.HolderLookup.Provider registries_)\3 ValueInput \2 = new ValueInput(tag_, registries_);"),
    sub(r"\bsuper\.loadAdditional\((\w+)\);", r"super.loadAdditional(\1.tag(), \1.lookup());"),
    sub(r"(\b(?:public|protected) void addAdditionalSaveData\()ValueOutput (\w+)\)(\s*\{)",
        r"\1net.minecraft.nbt.CompoundTag tag_)\3 ValueOutput \2 = new ValueOutput(tag_, this.registryAccess());"),
    sub(r"\bsuper\.addAdditionalSaveData\((\w+)\);", r"super.addAdditionalSaveData(\1.tag());"),
    sub(r"(\b(?:public|protected) void readAdditionalSaveData\()ValueInput (\w+)\)(\s*\{)",
        r"\1net.minecraft.nbt.CompoundTag tag_)\3 ValueInput \2 = new ValueInput(tag_, this.registryAccess());"),
    sub(r"\bsuper\.readAdditionalSaveData\((\w+)\);", r"super.readAdditionalSaveData(\1.tag());"),
    # --- screens: 26.x extracts what a screen draws, 1.21.1 renders it (the graphics wrapped: GuiGraphicsExtractor) --------
    sub(r"(\bpublic void )extractRenderState\(GuiGraphicsExtractor (\w+), (int \w+, int \w+, float \w+)\)(\s*\{)",
        r"\1render(net.minecraft.client.gui.GuiGraphics gg_, \3)\4 GuiGraphicsExtractor \2 = new GuiGraphicsExtractor(gg_);"),
    sub(r"\bsuper\.extractRenderState\((\w+), ", r"super.render(\1.unwrap(), "),
    sub(r"(\bpublic void )extractBackground\(GuiGraphicsExtractor (\w+), (int \w+, int \w+, float \w+)\)(\s*\{)",
        r"\1renderBackground(net.minecraft.client.gui.GuiGraphics gg_, \3)\4 GuiGraphicsExtractor \2 = new GuiGraphicsExtractor(gg_);"),
    sub(r"\bsuper\.extractBackground\((\w+), ", r"super.renderBackground(\1.unwrap(), "),
    sub(r"(\bprotected void )extractLabels\(GuiGraphicsExtractor (\w+), (int \w+, int \w+)\)(\s*\{)",
        r"\1renderLabels(net.minecraft.client.gui.GuiGraphics gg_, \3)\4 GuiGraphicsExtractor \2 = new GuiGraphicsExtractor(gg_);"),
    sub(r"\bsuper\.extractLabels\((\w+), ", r"super.renderLabels(\1.unwrap(), "),
    # 1.21.1's container screens draw their panel in renderBg, called from renderBackground (which main's code overrides).
    sub(r"(\bclass \w+(?:<[^{]*?>)? extends AbstractContainerScreen<[^{]*\{)",
        r"\1\n    @Override\n    protected void renderBg(net.minecraft.client.gui.GuiGraphics gg_, float partial_, int mouseX_, int mouseY_) {}\n"),
]


def port_imports(text: str) -> str:
    def repl(m: re.Match) -> str:
        name = m.group(2)
        static = m.group(1) or ""
        if name in IMPORTS:
            new = IMPORTS[name]
            return "" if new is None else f"import {static}{new};"
        return m.group(0)
    text = re.sub(r"import (static )?([\w.]+);", repl, text)
    # One import, once (two 26.x classes may both become the same 1.21.1 one).
    seen, out = set(), []
    for line in text.split("\n"):
        if line.startswith("import ") and line in seen:
            continue
        if line.startswith("import "):
            seen.add(line)
        out.append(line)
    return "\n".join(out)


def port_java(text: str, path: str) -> str:
    lines = [ln for ln in text.split("\n") if not any(p.match(ln) for p in DROP_LINES)]
    text = "\n".join(lines)
    for p in DROP_STATEMENTS:
        text = p.sub("", text)
    text = port_imports(text)
    for pattern, repl in RULES:
        text = pattern.sub(repl, text)
    for fix in FILE_FIXES.get(path, []):
        text = fix(text)
    return text


# Per-file fixes (a function of the text each), for what only one file does.
FILE_FIXES: dict[str, list] = {}


# --- resources ------------------------------------------------------------------------------------------------------------
def port_resource(path: str, data: bytes) -> tuple[str, bytes] | None:
    """Where main's resource goes here and what it becomes (None: it has no place here)."""
    # GeckoLib 4 reads geo/ and animations/ (GeckoLib 5: geckolib/models/ and geckolib/animations/).
    path = re.sub(r"^(src/main/resources/assets/\w+)/geckolib/models/", r"\1/geo/", path)
    path = re.sub(r"^(src/main/resources/assets/\w+)/geckolib/animations/", r"\1/animations/", path)
    if re.match(r"^src/(main|generated)/resources/data/\w+/recipe/.*\.json$", path):
        data = port_recipe(data)
    return path, data


def ingredient(v):
    """A 26.x ingredient ("minecraft:stone", "#c:ingots/iron", or a list of them) as 1.21.1 writes it."""
    if isinstance(v, str):
        return {"tag": v[1:]} if v.startswith("#") else {"item": v}
    if isinstance(v, list):
        return [ingredient(x) for x in v]
    return v


def port_recipe(data: bytes) -> bytes:
    r = json.loads(data)
    if "key" in r:
        r["key"] = {k: ingredient(v) for k, v in r["key"].items()}
    if "ingredients" in r:
        r["ingredients"] = [ingredient(v) for v in r["ingredients"]]
    for k in ("ingredient", "base", "addition", "template"):
        if k in r:
            r[k] = ingredient(r[k])
    return (json.dumps(r, indent=2) + "\n").encode("utf-8")


# --- driver ---------------------------------------------------------------------------------------------------------------
def checkout_eol(path: str, data: bytes) -> bytes:
    """Line endings as git checks the file out here (core.autocrlf: CRLF for text, but LF for the generated JSON that
    .gitattributes keeps LF), so a file the port leaves as main has it shows no change."""
    if b"\0" in data[:8000]:
        return data
    crlf = subprocess.run(["git", "-C", str(ROOT), "config", "core.autocrlf"], capture_output=True, text=True).stdout.strip() == "true" \
        if not hasattr(checkout_eol, "crlf") else checkout_eol.crlf
    checkout_eol.crlf = crlf
    lf = data.replace(b"\r\n", b"\n")
    if not crlf or (path.startswith("src/generated/") and (path.endswith(".json") or "/.cache/" in path)):
        return lf
    return lf.replace(b"\n", b"\r\n")


def port_file(path: str) -> tuple[str, bytes] | None:
    if any(p.fullmatch(path) for p in DROP) or path in OWN:
        return None
    data = read_main(path)
    if path.endswith(".java"):
        return path, port_java(data.decode("utf-8"), path).encode("utf-8")
    if path.startswith("src/main/resources/") or path.startswith("src/generated/resources/"):
        return port_resource(path, data)
    return path, data


def main() -> None:
    args = sys.argv[1:]
    if args and args[0] == "--show":
        out = port_file(args[1])
        sys.stdout.write("(dropped)\n" if out is None else out[1].decode("utf-8"))
        return
    files = args or main_files()
    written = kept = dropped = 0
    for path in files:
        out = port_file(path)
        if out is None:
            stale = ROOT / path
            if stale.exists() and (path.startswith("src/") or path.startswith("tools/")) and not path.startswith("tools/backport"):
                stale.unlink()
            dropped += 1
            continue
        dest, data = out
        if dest in MANUAL and (ROOT / dest).exists():
            kept += 1
            continue
        target = ROOT / dest
        target.parent.mkdir(parents=True, exist_ok=True)
        data = checkout_eol(dest, data)
        if dest != path and (ROOT / path).exists():
            (ROOT / path).unlink()                    # moved here (GeckoLib's folders)
        if not target.exists() or target.read_bytes() != data:
            target.write_bytes(data)
            written += 1
    print(f"{len(files)} of main's files: {written} written, {kept} kept (edited by hand), {dropped} left out")


if __name__ == "__main__":
    main()
