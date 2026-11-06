"""Turns main's sources (Minecraft 26.3) into this branch's (Minecraft 26.1.2): docs/BACKPORT.md.

Reads every file of main at the commit in base.txt straight from git (so each run starts from main's own text) and writes
its port here: the Java sources through RULES, the resources through their fixes. Main's files that have no place here
(DROP) are left out; files edited by hand here (manual.txt) are kept as they are. This branch's own files (the
compatibility classes in dev.groundburst.port, this tool, docs/BACKPORT.md) are not main's and are never touched.

    tools\\.venv\\Scripts\\python.exe tools\\backport\\port.py              (everything)
    tools\\.venv\\Scripts\\python.exe tools\\backport\\port.py <path> ...   (just those of main's files)
    tools\\.venv\\Scripts\\python.exe tools\\backport\\port.py --show <path> (print one file's port)
    tools\\.venv\\Scripts\\python.exe tools\\backport\\port.py --sync <commit> (bring the branch up to a newer main)

(The venv is main's: tools\\.venv in the main checkout.)
"""
from __future__ import annotations

import gzip
import io
import json
import re
import struct
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
BASE = (HERE / "base.txt").read_text(encoding="utf-8").split()[0]
MANUAL = {line.strip() for line in (HERE / "manual.txt").read_text(encoding="utf-8").splitlines()
          if line.strip() and not line.startswith("#")} if (HERE / "manual.txt").exists() else set()

# 26.1.2's data version: what its structure templates are saved at.
DATA_VERSION = 4790

# Main's files with no place here: its data generators (their output is ported instead, below), the world survey (it
# reads worldgen 26.3 reshaped), the generators' cache. (The blast and play benches and the photographers come over.)
DROP = [re.compile(p) for p in (
    r"src/main/java/dev/groundburst/datagen/.*",
    r"src/main/java/dev/groundburst/client/fx/WorldSurveyClient\.java",
    r"src/main/java/dev/groundburst/world/structure/WorldSurvey\.java",
    r"src/generated/resources/\.cache/.*",
    r"docs/BACKPORT\.md", r"tools/backport/.*",
    # The blast effects handed to a shader pack and drawn after the level (main's DESIGN.md §12.3): built on 26.3's
    # renderer and Iris's API for it. This branch keeps its own FxRenderTypes (drawn after the translucent terrain).
    r"src/main/java/dev/groundburst/client/fx/(IrisFx|DrawnLast)\.java",
)]
# Main's paths that are this branch's own (build files changed here by hand): never overwritten.
OWN = {"build.gradle", "gradle.properties"}


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
    r"^\s*dev\.groundburst\.client\.fx\.WorldSurveyClient\.register\(\);\s*$",
    r"^\s*WorldSurvey\.register\(\);\s*$",
    r"^\s*GBDataGen\.register\(modBus\);\s*$",
    r"^import dev\.groundburst\.datagen\.\w+;\s*$",
)]

# Imports: 26.3 class -> 26.1.2 class (or this branch's stand-in); None drops the import.
IMPORTS = {
    "net.minecraft.world.entity.EntityTypes": "net.minecraft.world.entity.EntityType",
    "net.neoforged.neoforge.registries.NewDatapackRegistryEvent": "net.neoforged.neoforge.registries.DataPackRegistryEvent",
    "net.minecraft.tags.BlockItemTagId": "dev.groundburst.port.BlockItemTagId",
    "net.minecraft.world.entity.EntitySpawnRequest": None,
    "net.minecraft.world.item.component.CookingFuel": None,
    "net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt": None,
}

RULES = [
    # --- names ------------------------------------------------------------------------------------------------------------
    sub(r"\bEntityTypes\.", "EntityType."),
    sub(r"\bPushReaction\.IMMOVEABLE\b", "PushReaction.BLOCK"),
    sub(r"\bPushReaction\.POPPED\b", "PushReaction.DESTROY"),
    sub(r"\bTags\.BlockItems\.(\w+)\.block\(\)", r"Tags.Blocks.\1"),
    sub(r"\bTags\.BlockItems\.(\w+)\.item\(\)", r"Tags.Items.\1"),
    sub(r"\bNewDatapackRegistryEvent\b", "DataPackRegistryEvent.NewRegistry"),
    sub(r"\.worldRegistry\(", ".dataPackRegistry("),
    sub(r"\bModConfig\.Type\.SYNCED\b", "ModConfig.Type.SERVER"),
    sub(r"\bModConfig\.Type\.LOCAL\b", "ModConfig.Type.COMMON"),
    # 26.3 calls configured features "features" (the feature types are FEATURE_TYPE); 26.1.2 keeps the old names.
    sub(r"\bRegistries\.FEATURE\b", "Registries.CONFIGURED_FEATURE"),
    # --- the client ---------------------------------------------------------------------------------------------------------
    sub(r"\.gui\.screen\(\)", ".screen"),
    sub(r"\.gui\.setScreen\(", ".setScreen("),
    sub(r"\.gui\.hud\.isHidden\(\)", ".options.hideGui"),
    sub(r"(\b\w+)\.gui\.hud\.toggle\(\)", r"\1.options.hideGui = !\1.options.hideGui"),
    sub(r"\.mainCamera\(\)", ".getMainCamera()"),
    sub(r"(\b\w+)\.gameRenderer\.mainRenderTarget\(\)", r"\1.getMainRenderTarget()"),
    sub(r"\.gameRenderer\.lighting\(\)", ".gameRenderer.getLighting()"),
    sub(r"\.gameRenderer\.gameRenderState\(\)", ".gameRenderer.getGameRenderState()"),
    # The pose stack turns by quaternions in both; 26.3's rotate is 26.1.2's mulPose.
    sub(r"\.rotate\(((?:com\.mojang\.math\.)?Axis\.|new (?:org\.joml\.)?Quaternionf)", r".mulPose(\1"),
    sub(r"\.rotateDegrees\(((?:[^,()]|\([^()]*\))+),\s*((?:[^()]|\([^()]*\))+)\)", r".mulPose(\1.rotationDegrees(\2))"),
    sub(r"\bLightCoordsUtil\.getLightCoords\(", "net.minecraft.client.renderer.LevelRenderer.getLightCoords("),
    sub(r"\bInputConstants\.Type\.KEYBOARD\b", "InputConstants.Type.KEYSYM"),
    sub(r"\bWorldPresets([.:]{1,2})createTestWorldDimensions\b", r"WorldPresets\1createFlatWorldDimensions"),
    # --- the world ----------------------------------------------------------------------------------------------------------
    sub(r"\.damageCooldownTime\b", ".invulnerableTime"),
    sub(r"\.isViewBlocking\(\((\w+), (\w+), (\w+), \w+\) -> ", r".isViewBlocking((\1, \2, \3) -> "),
    sub(r"\.getStructureTemplateManager\(\)", ".getStructureManager()"),
    sub(r"(\w+)\.is\(BlockTags\.ICE_MELTS_WHEN_DESTROYED_ABOVE\)", r"\1.blocksMotion()"),
    sub(r"(\w+)\.biomeResolver\(\)\.getNoiseBiome\(((?:[^()]|\([^()]*\))+)\)", r"\1.biomeSource().getNoiseBiome(\2, \1.randomState().sampler())"),
    sub(r"\bBlocks\.(\w+)\.asList\(\)\.getFirst\(\)", r"Blocks.\1"),          # 26.3's weathering families
    sub(r"\bTags\.EntityType\.", "Tags.EntityTypes."),
    # A block's glow: 26.1.2's predicate is given the level and the position too.
    sub(r"\.emissiveRendering\((\w+) -> ", r".emissiveRendering((\1, level_, pos_) -> "),
    sub(r"\.getVisibilityPercent\((?:[\w.]+\(\)|\w+), (\w+)\)", r".getVisibilityPercent(\1)"),
    # A player breaking a block: 26.1.2's hook is given the level and player, not the server's.
    sub(r"\bpublic void playerDestroy\(ServerLevel (\w+), ServerPlayer (\w+), ",
        r"public void playerDestroy(net.minecraft.world.level.Level \1, net.minecraft.world.entity.player.Player \2, "),
    # --- GameTests ----------------------------------------------------------------------------------------------------------
    sub(r"\bnew TestData<>\((\w+), net\.minecraft\.world\.level\.Level\.OVERWORLD,\s*", r"new TestData<>(\1, "),
    sub(r"(\b\w+)\.makeMockServerPlayer\(", r"dev.groundburst.port.Mock.serverPlayer(\1, "),
]


def method_bodies(text: str, signature: str, fn) -> str:
    """{fn} applied to each method whose declaration matches {signature} (up to its opening brace), body and all."""
    out, pos = [], 0
    for m in re.compile(signature, re.S).finditer(text):
        if m.start() < pos:
            continue
        depth, i = 0, m.end() - 1
        while i < len(text):
            ch = text[i]
            if ch == "{":
                depth += 1
            elif ch == "}":
                depth -= 1
                if depth == 0:
                    break
            elif ch == '"':                               # skip string literals
                i += 1
                while i < len(text) and text[i] != '"':
                    i += 2 if text[i] == "\\" else 1
            i += 1
        out.append(text[pos:m.start()])
        out.append(fn(text[m.start():i + 1]))
        pos = i + 1
    out.append(text[pos:])
    return "".join(out)


def calls(text: str, start: str, fn) -> str:
    """Each call matching {start} (up to its opening parenthesis), its whole argument list found by matching brackets
    (over lines, past strings), replaced by fn(match, args)."""
    out, pos = [], 0
    for m in re.compile(start).finditer(text):
        if m.start() < pos:
            continue
        depth, i = 1, m.end()
        while i < len(text) and depth:
            ch = text[i]
            if ch in "([{":
                depth += 1
            elif ch in ")]}":
                depth -= 1
            elif ch == '"':
                i += 1
                while i < len(text) and text[i] != '"':
                    i += 2 if text[i] == "\\" else 1
            i += 1
        out.append(text[pos:m.start()])
        out.append(fn(m, text[m.end():i - 1]))
        pos = i
    out.append(text[pos:])
    return "".join(out)


def split_args(args: str) -> list[str]:
    """A call's arguments, split at its own commas."""
    parts, cur, depth, i = [], [], 0, 0
    while i < len(args):
        ch = args[i]
        if ch == '"':
            j = i + 1
            while j < len(args) and args[j] != '"':
                j += 2 if args[j] == "\\" else 1
            cur.append(args[i:j + 1])
            i = j + 1
            continue
        if ch in "([{":
            depth += 1
        elif ch in ")]}":
            depth -= 1
        elif ch == "," and depth == 0:
            parts.append("".join(cur))
            cur = []
            i += 1
            continue
        cur.append(ch)
        i += 1
    parts.append("".join(cur))
    return parts


def fill_biomes(m: re.Match, args: str) -> str:
    """26.3's biome resolver is given a quart position; 26.1.2's a climate sampler too (and the fill takes one)."""
    lam = re.match(r"\s*\((\w+), (\w+), (\w+)\) ->", args)
    if not lam or len(split_args(args)) != 1:
        return m.group(0) + args + ")"
    return f".fillBiomesFromNoise(({lam.group(1)}, {lam.group(2)}, {lam.group(3)}, sampler_) ->{args[lam.end():]}, null)"


def call_fixes(text: str) -> str:
    """Calls whose arguments changed, too deep for a pattern."""
    text = calls(text, r"\.isSectionCompiledAndVisible\(", lambda m, a: f".isSectionCompiledAndVisible({split_args(a)[0].strip()})")
    text = calls(text, r"\.placeItemBackInInventory\(",
                 lambda m, a: f".placeItemBackInInventory({split_args(a)[0].strip()})" if "Prediction" in a else m.group(0) + a + ")")
    text = calls(text, r"\.fillBiomesFromNoise\(", fill_biomes)
    # A block's outline: 26.1.2 has no outline submit; drawn as custom line geometry, as its own hit outline is.
    def outline(m: re.Match, a: str) -> str:
        p = [x.strip() for x in split_args(a)]
        if len(p) != 6:
            return m.group(0) + a + ")"
        pose, shape, rtype, colour, width = p[0], p[1], p[2], p[3], p[4]
        return (f".submitCustomGeometry({pose}, {rtype}, (pose_, buffer_) -> {{ com.mojang.blaze3d.vertex.PoseStack stack_ = "
                f"new com.mojang.blaze3d.vertex.PoseStack(); stack_.last().set(pose_); net.minecraft.client.renderer.ShapeRenderer"
                f".renderShape(stack_, buffer_, {shape}, 0, 0, 0, {colour}, {width}); }})")
    text = calls(text, r"\.submitShapeOutline\(", outline)
    return text


# Vanilla 26.1.2 classes whose codec() is abstract: a block extending one directly needs its own.
CODEC_BASES = r"(?:BaseEntityBlock|HorizontalDirectionalBlock|DirectionalBlock|FaceAttachedHorizontalDirectionalBlock|VegetationBlock)"


def block_codecs(text: str) -> str:
    """Blocks give a codec in 26.1.2 (for the block type registry): this one, as it is."""
    if re.search(r"\bcodec\(\)\s*\{", text):
        return text
    def add(m: re.Match) -> str:
        return m.group(0) + (f"\n    @Override\n    protected com.mojang.serialization.MapCodec<? extends {m.group(2)}> codec() {{\n"
                             f"        return com.mojang.serialization.MapCodec.unit(this);\n    }}\n")
    return re.sub(r"(\n(?:public |protected |private )?(?:final )?class (\w+)(?:<[^{]*?>)? extends " + CODEC_BASES + r"\b[^{]*\{)", add, text)


def pip_renderers(text: str) -> str:
    """26.3's picture-in-picture renderers submit into a collector they are handed; 26.1.2's are made with a buffer
    source and submit into the game's feature dispatcher, which they then draw."""
    if "extends PictureInPictureRenderer<" not in text:
        return text
    text = re.sub(r"(\bclass (\w+) extends PictureInPictureRenderer<\w+> \{)",
                  r"\1\n        public \2(net.minecraft.client.renderer.MultiBufferSource.BufferSource bufferSource_) {\n"
                  r"            super(bufferSource_);\n        }\n", text)

    def body(method: str) -> str:
        method = re.sub(r"renderToTexture\((\w+) (\w+), PoseStack (\w+), SubmitNodeCollector (\w+)\)(\s*)\{",
                        r"renderToTexture(\1 \2, PoseStack \3)\5{\n            var features_ = net.minecraft.client.Minecraft.getInstance()"
                        r".gameRenderer.getFeatureRenderDispatcher();\n            SubmitNodeCollector \4 = features_.getSubmitNodeStorage();",
                        method, count=1)
        end = method.rstrip().rfind("}")
        return method[:end] + "    features_.renderAllFeatures();\n        " + method[end:]
    return method_bodies(text, r"\bprotected void renderToTexture\(\w+ \w+, PoseStack \w+, SubmitNodeCollector \w+\)\s*\{", body)


COLOURED = {"DYED_TERRACOTTA": "TERRACOTTA"}


def coloured_blocks(text: str) -> str:
    """26.3 keeps a colour's blocks in families (Blocks.CONCRETE.pick(DyeColor.GRAY)); 26.1.2 has each by name."""
    return re.sub(r"\bBlocks\.([A-Z_]+)\.pick\(DyeColor\.([A-Z_]+)\)",
                  lambda m: f"Blocks.{m.group(2)}_{COLOURED.get(m.group(1), m.group(1))}", text)


def pose_rotations(text: str) -> str:
    """A pose (PoseStack.Pose) turns with rotate in 26.1.2 (the stack itself with mulPose, as the rules make every
    turn): undone for the variables declared as poses."""
    for name in set(re.findall(r"\b(?:PoseStack\.)?Pose (\w+)\b", text)):
        if not re.search(rf"\bPoseStack {name}\b", text):
            text = re.sub(rf"\b{name}\.mulPose\(", rf"{name}.rotate(", text)
            continue
        # The same name is a pose stack elsewhere in the file: only in the methods given it as a pose.
        text = method_bodies(text, rf"\b\w+\([^)]*\b(?:PoseStack\.)?Pose {name}\b[^)]*\)\s*\{{",
                             lambda method, n=name: re.sub(rf"\b{n}\.mulPose\(", rf"{n}.rotate(", method))
    return text


FUNCTIONS = [block_codecs, pip_renderers, call_fixes, coloured_blocks, pose_rotations]


def port_imports(text: str) -> str:
    def repl(m: re.Match) -> str:
        name = m.group(2)
        static = m.group(1) or ""
        if name in IMPORTS:
            new = IMPORTS[name]
            return "" if new is None else f"import {static}{new};"
        return m.group(0)
    text = re.sub(r"import (static )?([\w.]+);", repl, text)
    # One import, once (two 26.3 classes may both become the same 26.1.2 one).
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
    text = port_imports(text)
    for pattern, repl in RULES:
        text = pattern.sub(repl, text)
    for fn in FUNCTIONS:
        text = fn(text)
    for fix in FILE_FIXES.get(path, []):
        text = fix(text)
    return text


# Per-file fixes (a function of the text each), for what only one file does.
def after(anchor: str, insert: str):
    def fix(text: str) -> str:
        assert anchor in text, f"port.py: '{anchor}' is gone from main: update FILE_FIXES"
        return text.replace(anchor, anchor + insert, 1)
    return fix


def swap(old: str, new: str):
    def fix(text: str) -> str:
        assert old in text, f"port.py: '{old}' is gone from main: update FILE_FIXES"
        return text.replace(old, new)
    return fix


def resub(pattern: str, repl: str, count: int = 0):
    def fix(text: str) -> str:
        new, n = re.subn(pattern, repl, text, count=count)
        assert n, f"port.py: /{pattern}/ is gone from main: update FILE_FIXES"
        return new
    return fix


def replace_method(signature: str, new: str):
    """Replaces the method whose declaration begins with {signature} (to its closing brace) with {new}."""
    def fix(text: str) -> str:
        assert signature in text, f"port.py: '{signature}' is gone from main: update FILE_FIXES"
        return method_bodies(text, re.escape(signature) + r"[^{]*\{", lambda method: new)
    return fix


def drop_method(signature: str):
    """Removes the method whose declaration begins with {signature} (and its javadoc's blank line)."""
    def fix(text: str) -> str:
        assert signature in text, f"port.py: '{signature}' is gone from main: update FILE_FIXES"
        return method_bodies(text, r"\n[ \t]*" + re.escape(signature) + r"[^{]*\{", lambda method: "\n")
    return fix


SKY = "dev.groundburst.port.SkyColors"

FILE_FIXES: dict[str, list] = {
    # The way under a shader pack (main's DESIGN.md §12.3) has no place here (no IrisFx, no DrawnLast): its option goes.
    "src/main/java/dev/groundburst/config/GBClientConfig.java": [
        resub(r'\n    public static final ModConfigSpec\.EnumValue<PackFx> SHADER_PACK_FX = B\n(?:.*\n)*?            \.defineEnum\("shaderPackFx", PackFx\.OVER\);\n',
              "\n", 1),
    ],
    # Fuel: 26.1.2 has its fuel values (26.3 reads the item's cooking-fuel component through a loot context).
    "src/main/java/dev/groundburst/content/power/CombustionGeneratorBlockEntity.java": [
        swap("return stack.has(DataComponents.COOKING_FUEL);",
             "return this.level != null && stack.getBurnTime(net.minecraft.world.item.crafting.RecipeType.SMELTING, this.level.fuelValues()) > 0;"),
        swap("int duration = ResolvableInt.getFromItem(fuel, DataComponents.COOKING_FUEL, CookingFuel::burnTime, lootContext(level, fuel), 0);",
             "int duration = fuel.getBurnTime(net.minecraft.world.item.crafting.RecipeType.SMELTING, level.fuelValues());"),
        swap("ItemStackTemplate remainder = fuel.getCraftingRemainder();",
             "ItemStackTemplate remainder = fuel.getItem().getCraftingRemainder(fuel);"),
        drop_method("private LootContext lootContext(ServerLevel level, ItemStack fuel)"),
    ],
    # 26.1.2's sky colours are packed ints (26.3's vectors, null when there is none).
    "src/main/java/dev/groundburst/client/fx/FxLight.java": [
        swap("Vector3fc skyColor = sky.skyColor != null ? sky.skyColor : new Vector3f(0.47f, 0.65f, 1f);",
             f"Vector3fc skyColor = {SKY}.vector(sky.skyColor);"),
        swap("Vector4fc glow = sky.sunriseAndSunsetColor;", f"Vector4fc glow = {SKY}.vector4(sky.sunriseAndSunsetColor);"),
    ],
    "src/main/java/dev/groundburst/client/fx/Spectacle.java": [
        resub(r"sky\.skyColor = (\w+)\(sky\.skyColor\.x\(\), sky\.skyColor\.y\(\), sky\.skyColor\.z\(\), ",
              rf"sky.skyColor = {SKY}.tinted(sky.skyColor, (r_, g_, b_, a_) -> \1(r_, g_, b_, a_), "),
        swap("sky.skyColor != null", "true"),
    ],
    # A healed entity: 26.1.2 loads it with the reason alone.
    "src/main/java/dev/groundburst/heal/Snapshots.java": [
        swap("new EntitySpawnRequest(EntitySpawnReason.LOAD, false)", "EntitySpawnReason.LOAD"),
    ],
    # Healing's mixins: 26.1.2's fire sets its block with setBlock(pos, state, 3); its farmland turns back with the
    # static turnToDirt (26.3: setBlockAndUpdate, and turnToBaseBlock).
    "src/main/java/dev/groundburst/mixin/FireBlockMixin.java": [
        swap('"Lnet/minecraft/world/level/Level;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"',
             '"Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"'),
    ],
    "src/main/java/dev/groundburst/mixin/FarmlandBlockMixin.java": [
        swap('@WrapMethod(method = "turnToBaseBlock")\n    private void ', '@WrapMethod(method = "turnToDirt")\n    private static void '),
    ],
    # The GameTests' configured features (26.3's Feature is 26.1.2's ConfiguredFeature).
    "src/main/java/dev/groundburst/gametest/ResourceTests.java": [
        swap("import net.minecraft.world.level.levelgen.feature.Feature;", "import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;"),
        swap("Feature feature = features.getOrThrow(", "ConfiguredFeature<?, ?> feature = features.getOrThrow("),
    ],
    "src/main/java/dev/groundburst/registry/GBCreativeTabs.java": [
        swap("previous = holder.key();", "previous = holder.getKey();"),
    ],
    "src/main/java/dev/groundburst/gametest/GBGameTests.java": [
        swap("new FunctionGameTestInstance(spec.function().key(), data)", "new FunctionGameTestInstance(spec.function().getKey(), data)"),
    ],
}


# --- resources ------------------------------------------------------------------------------------------------------------
def port_resource(path: str, data: bytes) -> tuple[str, bytes] | None:
    """Where main's resource goes here and what it becomes (None: it has no place here)."""
    # 26.3's configured features: 26.1.2's worldgen/configured_feature.
    path = re.sub(r"^(src/(?:main|generated)/resources/data/\w+/worldgen)/feature/", r"\1/configured_feature/", path)
    if re.match(r"^src/(main|generated)/resources/data/\w+/recipe/.*\.json$", path):
        data = port_recipe(data)
        if data is None:
            return None                              # it needs one of 26.2's or 26.3's items
    if re.match(r"^src/(main|generated)/resources/data/\w+/advancement/.*\.json$", path):
        if unlocks_dropped(data):
            return None                              # the unlock of a recipe left out
        data = port_advancement(data)
    if re.match(r"^src/(main|generated)/resources/assets/\w+/models/.*\.json$", path):
        data = port_model(data)
    if re.match(r"^src/(main|generated)/resources/data/\w+/loot_table/.*\.json$", path):
        data = port_loot(data)
    if re.match(r"^src/(main|generated)/resources/data/\w+/worldgen/biome/.*\.json$", path):
        data = port_biome(data)
    if re.match(r"^src/(main|generated)/resources/data/\w+/worldgen/configured_feature/.*\.json$", path):
        data = port_feature(data)
    if re.match(r"^src/(main|generated)/resources/data/\w+/tags/.*\.json$", path):
        data = port_tag(data)
    if re.match(r"^src/(main|generated)/resources/data/\w+/structure/.*\.nbt$", path):
        data = port_structure(data)
    return path, data


def port_model(data: bytes) -> bytes:
    """A model's elements shaded as if they faced up (26.3's shade_direction_override "up"): 26.1.2 has no such
    override, so they are left unshaded, which is the light an upward face gets."""
    if b"shade_direction_override" not in data:
        return data
    m = json.loads(data)
    for e in m.get("elements", []):
        if e.pop("shade_direction_override", None) == "up":
            e["shade"] = False
    return (json.dumps(m, indent=2) + "\n").encode("utf-8")


def vanilla_ids(java: str, pattern: str) -> set[str]:
    """Ids 26.1.2 registers, read from its sources (build/mcsrc): {pattern} captures each name in {java}."""
    src = ROOT / "build" / "mcsrc" / java
    return {"minecraft:" + n for n in re.findall(pattern, src.read_text(encoding="utf-8"))} if src.exists() else set()


ENTITY_TYPES = None


def port_biome(data: bytes) -> bytes:
    """26.3 keeps a biome's mob spawns as an environment attribute; 26.1.2 as its spawners and spawn costs (a mob
    26.1.2 lacks is left out). Its other attributes (sky, fog, music, particles) are 26.1.2's too."""
    global ENTITY_TYPES
    if ENTITY_TYPES is None:
        ENTITY_TYPES = vanilla_ids("net/minecraft/world/entity/EntityType.java", r'register\(\s*"([a-z0-9_]+)"')
    b = json.loads(data)
    attrs = b.get("attributes", {})
    spawns = attrs.pop("minecraft:gameplay/natural_mob_spawns", {}).get("argument", {})
    spawners = {}
    for category, entries in spawns.get("spawns_by_category", {}).items():
        out = []
        for e in entries:
            if ENTITY_TYPES and e["type"] not in ENTITY_TYPES:
                continue
            count = e.get("count", 1)
            lo, hi = (count["min_inclusive"], count["max_inclusive"]) if isinstance(count, dict) else (count, count)
            out.append({"type": e["type"], "weight": e["weight"], "minCount": lo, "maxCount": hi})
        spawners[category] = out
    b["spawners"] = spawners
    b["spawn_costs"] = spawns.get("spawn_costs", {})
    return (json.dumps(b, indent=2) + "\n").encode("utf-8")


# 26.3's named predicates the mod's loot tables use, as 26.1.2 writes them out.
NAMED_CONDITIONS = {
    "minecraft:tool/can_silk_touch": {"condition": "minecraft:match_tool", "predicate": {
        "predicates": {"minecraft:enchantments": [{"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}},
    "minecraft:tool/can_shear": {"condition": "minecraft:match_tool", "predicate": {"items": "minecraft:shears"}},
}


def loot_condition(c):
    """A 26.3 loot condition (a named predicate, or {"type", ...}) as 26.1.2 writes one ({"condition", ...})."""
    if isinstance(c, str):
        if c in NAMED_CONDITIONS:
            return json.loads(json.dumps(NAMED_CONDITIONS[c]))
        return {"condition": "minecraft:reference", "name": c}
    if not isinstance(c, dict) or "condition" in c:
        return c
    c = dict(c)
    kind = c.pop("type")
    out = {"condition": kind}
    if kind == "minecraft:match_block":                      # 26.1.2's block state property: one block
        out["condition"] = "minecraft:block_state_property"
        out["block"] = c.pop("blocks")
        if "state" in c:
            out["properties"] = c.pop("state")
    if kind in ("minecraft:any_of", "minecraft:all_of"):
        out["terms"] = [loot_condition(t) for t in c.pop("terms")]
    if kind == "minecraft:inverted":
        out["term"] = loot_condition(c.pop("term"))
    out.update(c)
    return port_loot_object(out)


def loot_function(f):
    """A 26.3 loot function ({"type", ...}) as 26.1.2 writes one ({"function", ...})."""
    if not isinstance(f, dict) or "function" in f:
        return port_loot_object(f)
    f = dict(f)
    out = {"function": f.pop("type")}
    out.update(f)
    return port_loot_object(out)


def port_loot_object(o):
    """26.3's single "condition" and its "modifier" list, wherever they sit (pools, entries, functions), as 26.1.2's
    "conditions" and "functions"."""
    if isinstance(o, list):
        return [port_loot_object(x) for x in o]
    if not isinstance(o, dict):
        return o
    out = {}
    for k, v in o.items():
        # A pool's, an entry's or a function's own condition (a condition's "condition" is its 26.1.2 kind: kept).
        if k == "condition" and (isinstance(v, dict) or (isinstance(v, str) and ("type" in o or "rolls" in o or "entries" in o or "name" in o or "function" in o))):
            out.setdefault("conditions", []).append(loot_condition(v))
        elif k == "conditions" and isinstance(v, list):
            out.setdefault("conditions", []).extend(loot_condition(x) for x in v)
        elif k == "modifier":
            out.setdefault("functions", []).extend(loot_function(x) for x in (v if isinstance(v, list) else [v]))
        elif k == "functions" and isinstance(v, list):
            out.setdefault("functions", []).extend(loot_function(x) for x in v)
        else:
            out[k] = port_loot_object(v)
    return out


def port_loot(data: bytes) -> bytes:
    t = json.loads(data)
    new = port_loot_object(t)
    if new == t:
        return data
    return (json.dumps(new, indent=2) + "\n").encode("utf-8")


def block_state(v):
    """A block state as 26.1.2 writes one ({"Name", "Properties"}), from 26.3's ("name", "name[a=b]" or {"id", ...})."""
    if isinstance(v, str):
        m = re.fullmatch(r"([^\[]+)(?:\[(.*)\])?", v)
        props = dict(p.split("=", 1) for p in m.group(2).split(",")) if m.group(2) else {}
        return {"Name": m.group(1), **({"Properties": props} if props else {})}
    if isinstance(v, dict) and "id" in v and "Name" not in v:
        props = v.get("properties", v.get("Properties", {}))
        return {"Name": v["id"], **({"Properties": props} if props else {})}
    return v


def state_provider(v):
    """A block state provider as 26.1.2 writes one (26.3 writes a plain state for the simple provider)."""
    if isinstance(v, dict) and "type" in v:
        return v
    return {"type": "minecraft:simple_state_provider", "state": block_state(v)}


def port_feature(data: bytes) -> bytes:
    """26.3 writes a configured feature's settings beside its type; 26.1.2 under "config", with full block states."""
    f = json.loads(data)
    config = {k: v for k, v in f.items() if k != "type"}
    if isinstance(config.get("blocks"), dict):                  # a geode's
        blocks = config["blocks"]
        for key in list(blocks):
            if key.endswith("_provider"):
                blocks[key] = state_provider(blocks[key])
        if "inner_placements" in blocks:
            blocks["inner_placements"] = [block_state(s) for s in blocks["inner_placements"]]
    for target in config.get("targets", []) if isinstance(config.get("targets"), list) else []:   # an ore's
        if "state" in target:
            target["state"] = block_state(target["state"])
    return (json.dumps({"type": f["type"], "config": config}, indent=2) + "\n").encode("utf-8")


def port_tag(data: bytes) -> bytes:
    """A tag's entries from outside the mod (vanilla's, other mods' and the common tags) made optional: 26.3 has blocks,
    items and tags 26.1.2 lacks, and one missing required entry fails the whole tag."""
    t = json.loads(data)
    values, changed = [], False
    for v in t.get("values", []):
        if isinstance(v, str) and not v.lstrip("#").startswith("groundburst:"):
            v = {"id": v, "required": False}
            changed = True
        values.append(v)
    if not changed:
        return data                                  # as main has it
    t["values"] = values
    return (json.dumps(t, indent=2) + "\n").encode("utf-8")


VANILLA_ITEMS = None


def vanilla_items() -> set[str]:
    """26.1.2's items (and blocks, a few more than have items), read from its sources: enough to tell 26.2's and 26.3's
    new ones."""
    global VANILLA_ITEMS
    if VANILLA_ITEMS is None:
        # One item model definition a vanilla item, one blockstate file a vanilla block (the sources build many of
        # their names, the dyed blocks and the stairs among them).
        assets = ROOT / "build" / "mcsrc" / "assets" / "minecraft"
        VANILLA_ITEMS = ({"minecraft:" + f.stem for f in (assets / "items").glob("*.json")}
                         | {"minecraft:" + f.stem for f in (assets / "blockstates").glob("*.json")}
                         | {"minecraft:water", "minecraft:lava", "minecraft:air"})
        assert len(VANILLA_ITEMS) > 1500, "port.py: could not read 26.1.2's items from build/mcsrc/assets"
    return VANILLA_ITEMS


def missing_items(value) -> list[str]:
    """The vanilla items named in a recipe that 26.1.2 lacks."""
    out = []
    if isinstance(value, dict):
        for k, v in value.items():
            if k in ("item", "id") and isinstance(v, str) and v.startswith("minecraft:") and v not in vanilla_items():
                out.append(v)
            else:
                out.extend(missing_items(v))
    elif isinstance(value, list):
        for v in value:
            out.extend(missing_items(v))
    elif isinstance(value, str) and value.startswith("minecraft:") and not value.startswith("minecraft:crafting") \
            and re.fullmatch(r"minecraft:[a-z0-9_]+", value) and value not in vanilla_items() and value not in RECIPE_WORDS:
        out.append(value)
    return out


# Vanilla ids in recipes that are not items (types, categories).
RECIPE_WORDS = {"minecraft:smelting", "minecraft:blasting", "minecraft:smoking", "minecraft:campfire_cooking",
                "minecraft:stonecutting", "minecraft:smithing_transform", "minecraft:smithing_trim"}


def port_advancement(data: bytes) -> bytes:
    """A recipe's unlock: 26.3 names its recipes, 26.1.2 one recipe."""
    a = json.loads(data)
    changed = False
    for criterion in a.get("criteria", {}).values():
        c = criterion.get("conditions", {})
        if criterion.get("trigger") == "minecraft:recipe_unlocked" and "recipes" in c and "recipe" not in c:
            r = c.pop("recipes")
            c["recipe"] = r[0] if isinstance(r, list) else r
            changed = True
    return (json.dumps(a, indent=2) + "\n").encode("utf-8") if changed else data


DROPPED_RECIPES = None


def dropped_recipes() -> set[str]:
    """The ids of main's recipes left out here (they need 26.2's or 26.3's items)."""
    global DROPPED_RECIPES
    if DROPPED_RECIPES is None:
        DROPPED_RECIPES = set()
        for f in main_files():
            m = re.match(r"^src/(?:main|generated)/resources/data/(\w+)/recipe/(.*)\.json$", f)
            if m and port_recipe(read_main(f)) is None:
                DROPPED_RECIPES.add(f"{m.group(1)}:{m.group(2)}")
    return DROPPED_RECIPES


def unlocks_dropped(data: bytes) -> bool:
    """Whether an advancement only unlocks recipes left out here."""
    a = json.loads(data)
    recipes = a.get("rewards", {}).get("recipes", [])
    return bool(recipes) and all(r in dropped_recipes() for r in recipes)


def port_recipe(data: bytes) -> bytes | None:
    r = json.loads(data)
    if missing_items(r):
        return None
    return data


# --- structure templates (NBT) --------------------------------------------------------------------------------------------
def _nbt_read(buf: io.BytesIO, tag: int):
    if tag == 1:
        return struct.unpack(">b", buf.read(1))[0]
    if tag == 2:
        return struct.unpack(">h", buf.read(2))[0]
    if tag == 3:
        return struct.unpack(">i", buf.read(4))[0]
    if tag == 4:
        return struct.unpack(">q", buf.read(8))[0]
    if tag == 5:
        return struct.unpack(">f", buf.read(4))[0]
    if tag == 6:
        return struct.unpack(">d", buf.read(8))[0]
    if tag == 7:
        n = struct.unpack(">i", buf.read(4))[0]
        return buf.read(n)
    if tag == 8:
        n = struct.unpack(">H", buf.read(2))[0]
        return buf.read(n).decode("utf-8")
    if tag == 9:
        elem, n = struct.unpack(">bi", buf.read(5))
        return (elem, [_nbt_read(buf, elem) for _ in range(n)])
    if tag == 10:
        out = {}
        while True:
            t = struct.unpack(">b", buf.read(1))[0]
            if t == 0:
                return out
            n = struct.unpack(">H", buf.read(2))[0]
            name = buf.read(n).decode("utf-8")
            out[name] = (t, _nbt_read(buf, t))
    if tag == 11:
        n = struct.unpack(">i", buf.read(4))[0]
        return list(struct.unpack(f">{n}i", buf.read(4 * n)))
    if tag == 12:
        n = struct.unpack(">i", buf.read(4))[0]
        return list(struct.unpack(f">{n}q", buf.read(8 * n)))
    raise ValueError(f"NBT tag {tag}")


def _nbt_write(out: io.BytesIO, tag: int, v) -> None:
    if tag == 1:
        out.write(struct.pack(">b", v))
    elif tag == 2:
        out.write(struct.pack(">h", v))
    elif tag == 3:
        out.write(struct.pack(">i", v))
    elif tag == 4:
        out.write(struct.pack(">q", v))
    elif tag == 5:
        out.write(struct.pack(">f", v))
    elif tag == 6:
        out.write(struct.pack(">d", v))
    elif tag == 7:
        out.write(struct.pack(">i", len(v)))
        out.write(v)
    elif tag == 8:
        b = v.encode("utf-8")
        out.write(struct.pack(">H", len(b)))
        out.write(b)
    elif tag == 9:
        elem, items = v
        out.write(struct.pack(">bi", elem, len(items)))
        for x in items:
            _nbt_write(out, elem, x)
    elif tag == 10:
        for name, (t, x) in v.items():
            out.write(struct.pack(">b", t))
            b = name.encode("utf-8")
            out.write(struct.pack(">H", len(b)))
            out.write(b)
            _nbt_write(out, t, x)
        out.write(b"\0")
    elif tag == 11:
        out.write(struct.pack(">i", len(v)))
        out.write(struct.pack(f">{len(v)}i", *v))
    elif tag == 12:
        out.write(struct.pack(">i", len(v)))
        out.write(struct.pack(f">{len(v)}q", *v))
    else:
        raise ValueError(f"NBT tag {tag}")


def port_structure(data: bytes) -> bytes:
    """A structure template: 26.3 names a palette's block states "id" and "properties", 26.1.2 "Name" and "Properties"
    (26.1.2 reads a lowercase entry as air); saved at 26.1.2's data version, so nothing is updated as it loads."""
    raw = gzip.decompress(data)
    buf = io.BytesIO(raw)
    tag = struct.unpack(">b", buf.read(1))[0]
    n = struct.unpack(">H", buf.read(2))[0]
    root_name = buf.read(n).decode("utf-8")
    root = _nbt_read(buf, tag)
    palettes = []
    if "palette" in root:
        palettes.append(root["palette"][1][1])
    if "palettes" in root:
        palettes.extend(p for p in root["palettes"][1][1])
    for palette in palettes:
        for i, entry in enumerate(palette):
            new = {}
            for key, val in entry.items():
                new[{"id": "Name", "properties": "Properties"}.get(key, key)] = val
            palette[i] = new
    root["DataVersion"] = (3, DATA_VERSION)
    out = io.BytesIO()
    out.write(struct.pack(">b", tag))
    b = root_name.encode("utf-8")
    out.write(struct.pack(">H", len(b)))
    out.write(b)
    _nbt_write(out, tag, root)
    return gzip.compress(out.getvalue(), mtime=0)


# --- driver ---------------------------------------------------------------------------------------------------------------
def checkout_eol(path: str, data: bytes) -> bytes:
    """Line endings as git checks the file out here (core.autocrlf: CRLF for text, but LF for the generated JSON that
    .gitattributes keeps LF), so a file the port leaves as main has it shows no change."""
    if b"\0" in data[:8000] or path.endswith((".nbt", ".png", ".ogg")):
        return data
    crlf = subprocess.run(["git", "-C", str(ROOT), "config", "core.autocrlf"], capture_output=True, text=True).stdout.strip() == "true" \
        if not hasattr(checkout_eol, "crlf") else checkout_eol.crlf
    checkout_eol.crlf = crlf
    lf = data.replace(b"\r\n", b"\n")
    if not crlf or (path.startswith("src/generated/") and (path.endswith(".json") or "/.cache/" in path)):
        return lf
    return lf.replace(b"\n", b"\r\n")


# Main's other text files changed here (path -> [(old, new)]).
TEXT_FIXES = {
    # JEI for 26.1.2 is 29.x (26.3's is 31.x).
    "src/main/templates/META-INF/neoforge.mods.toml": [('modId="jei"', 'modId="jei"'), ('versionRange="[31,)"', 'versionRange="[29,)"')],
}


def port_file(path: str) -> tuple[str, bytes] | None:
    if any(p.fullmatch(path) for p in DROP) or path in OWN:
        return None
    data = read_main(path)
    if path in TEXT_FIXES:
        text = data.decode("utf-8")
        for old, new in TEXT_FIXES[path]:
            assert old in text, f"port.py: '{old}' is gone from main's {path}: update TEXT_FIXES"
            text = text.replace(old, new)
        data = text.encode("utf-8")
    if path.endswith(".java"):
        return path, port_java(data.decode("utf-8"), path).encode("utf-8")
    if path.startswith("src/main/resources/") or path.startswith("src/generated/resources/"):
        return port_resource(path, data)
    return path, data


def sync(new: str) -> None:
    """Brings this branch up to main's commit {new} from the one in base.txt: each file main changed since is ported again;
    one edited by hand here (manual.txt) gets main's change merged into it (a three-way merge of ported text: the old
    port, the new port, and the file as it is here), conflicts left marked for a person; a file main deleted goes."""
    global BASE
    old = BASE
    new = git("rev-parse", new).decode("utf-8").strip()
    changes = git("diff", "--name-status", "--no-renames", old, new).decode("utf-8").splitlines()
    conflicts = []
    for line in changes:
        status, path = line.split("\t", 1)
        if status.startswith("D"):
            BASE = old
            out = port_file(path)
            if out is not None and (ROOT / out[0]).exists() and out[0] not in MANUAL:
                (ROOT / out[0]).unlink()
            continue
        BASE = new
        out_new = port_file(path)
        if out_new is None:
            continue
        dest, data_new = out_new
        target = ROOT / dest
        if dest in MANUAL and target.exists():
            BASE = old
            try:
                out_old = port_file(path)
            except FileNotFoundError:
                out_old = None
            base_file = ROOT / "build" / "port-merge" / (dest + ".base")
            theirs_file = ROOT / "build" / "port-merge" / (dest + ".main")
            base_file.parent.mkdir(parents=True, exist_ok=True)
            base_file.write_bytes(checkout_eol(dest, out_old[1]) if out_old else b"")
            theirs_file.write_bytes(checkout_eol(dest, data_new))
            r = subprocess.run(["git", "merge-file", "-L", "here", "-L", "port of old main", "-L", "port of new main",
                                str(target), str(base_file), str(theirs_file)], capture_output=True)
            if r.returncode != 0:
                conflicts.append(dest)
            continue
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(checkout_eol(dest, data_new))
    BASE = new
    (HERE / "base.txt").write_text(f"{new}   main's commit this branch is brought up to (docs/BACKPORT.md)\n", encoding="utf-8")
    print(f"{len(changes)} of main's files changed since {old[:8]}; now at {new[:8]}")
    for c in conflicts:
        print("  conflict to resolve by hand:", c)


def main() -> None:
    args = sys.argv[1:]
    if args and args[0] == "--show":
        out = port_file(args[1])
        sys.stdout.buffer.write(b"(dropped)\n" if out is None else out[1])
        return
    if args and args[0] == "--sync":
        sync(args[1] if len(args) > 1 else "main")
        return
    files = args or main_files()
    written = kept = dropped = 0
    for path in files:
        out = port_file(path)
        if out is None:
            if path in OWN:                               # this branch's own (its build files): never touched
                kept += 1
                continue
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
            (ROOT / path).unlink()                    # moved here (configured features)
        if not target.exists() or target.read_bytes() != data:
            target.write_bytes(data)
            written += 1
    print(f"{len(files)} of main's files: {written} written, {kept} kept (edited by hand), {dropped} left out")


if __name__ == "__main__":
    main()
