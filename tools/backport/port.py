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
    r"src/main/java/dev/groundburst/client/fx/(FxPhotographer|SitePhotographer|HealPhotographer|BlastBenchClient|WorldSurveyClient|DevWorlds)\.java",
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
    r"^\s*dev\.groundburst\.client\.fx\.(FxPhotographer|BlastBenchClient|WorldSurveyClient|SitePhotographer|HealPhotographer)\.register\(\);\s*$",
    r"^\s*ManualPhotographer\.register\(\);\s*$",
    r"^\s*BlastBench\.register\(\);\s*$",
    r"^\s*WorldSurvey\.register\(\);\s*$",
    r"^\s*GBDataGen\.register\(modBus\);\s*$",
    r"^\s*GBGameTests\.register\(modBus\);\s*$",
    r"^import dev\.groundburst\.(datagen|gametest)\.\w+;\s*$",
    r"^\s*\w+\.registerCategory\(\w+\);\s*$",                         # 1.21.1's key categories are names, not registered
)]
# Whole statements removed (they may run over several lines): hooks into the dropped tools, and 26.x's
# picture-in-picture renderers (1.21.1 draws those views straight into the screen: dev.groundburst.port.render.PictureInPicture).
DROP_STATEMENTS = [re.compile(p, re.M) for p in (
    r"^[ \t]*dev\.groundburst\.client\.fx\.FxPhotographer\.\w+ = [^;]*;[ \t]*\r?\n",
    r"^[ \t]*modBus\.addListener\(\((?:net\.neoforged\.neoforge\.client\.event\.)?RegisterPictureInPictureRenderersEvent \w+\) ->[^;]*;[ \t]*\r?\n",
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
    "net.minecraft.util.Util": "net.minecraft.Util",
    "net.minecraft.world.level.ScheduledTickAccess": "net.minecraft.world.level.LevelAccessor",
    "net.minecraft.world.level.redstone.Orientation": None,
    "net.minecraft.world.entity.InsideBlockEffectApplier": None,
    "net.minecraft.world.item.component.TooltipDisplay": None,
    "net.minecraft.world.level.block.VegetationBlock": "net.minecraft.world.level.block.BushBlock",
    # 26.x's render states and submit nodes: this branch's stand-ins over 1.21.1's direct rendering (dev.groundburst.port.render)
    "net.minecraft.client.renderer.SubmitNodeCollector": "dev.groundburst.port.render.SubmitNodeCollector",
    "net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState": "dev.groundburst.port.render.BlockEntityRenderState",
    "net.minecraft.client.renderer.entity.state.EntityRenderState": "dev.groundburst.port.render.EntityRenderState",
    "net.minecraft.client.renderer.feature.ModelFeatureRenderer": "dev.groundburst.port.render.ModelFeatureRenderer",
    "net.minecraft.client.renderer.item.ItemModelResolver": "dev.groundburst.port.render.ItemModelResolver",
    "net.minecraft.client.renderer.item.ItemStackRenderState": "dev.groundburst.port.render.ItemStackRenderState",
    "net.minecraft.client.renderer.state.level.CameraRenderState": "dev.groundburst.port.render.CameraRenderState",
    "net.minecraft.client.renderer.state.level.SkyRenderState": "dev.groundburst.port.render.SkyRenderState",
    "net.minecraft.client.renderer.state.level.LevelRenderState": "dev.groundburst.port.render.LevelRenderState",
    "net.minecraft.util.context.ContextKey": "dev.groundburst.port.ContextKey",
    "net.minecraft.world.level.saveddata.SavedDataType": "dev.groundburst.port.SavedDataType",
    "net.minecraft.client.input.MouseButtonEvent": "dev.groundburst.port.input.MouseButtonEvent",
    "net.minecraft.client.input.KeyEvent": "dev.groundburst.port.input.KeyEvent",
    "net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent": "dev.groundburst.port.render.ExtractLevelRenderStateEvent",
    "net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent": "dev.groundburst.port.render.SubmitCustomGeometryEvent",
    # Recipes: 26.x's results are templates and its recipes show through displays (stand-ins built from 1.21.1's recipes).
    "net.minecraft.world.item.ItemStackTemplate": "dev.groundburst.port.ItemStackTemplate",
    "net.neoforged.neoforge.fluids.FluidStackTemplate": "dev.groundburst.port.FluidStackTemplate",
    "net.minecraft.util.context.ContextMap": "dev.groundburst.port.ContextMap",
    "net.minecraft.world.item.crafting.display.RecipeDisplay": "dev.groundburst.port.recipe.RecipeDisplay",
    "net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay": "dev.groundburst.port.recipe.ShapedCraftingRecipeDisplay",
    "net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay": "dev.groundburst.port.recipe.ShapelessCraftingRecipeDisplay",
    "net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay": "dev.groundburst.port.recipe.FurnaceRecipeDisplay",
    "net.minecraft.world.item.crafting.display.SlotDisplay": "dev.groundburst.port.recipe.SlotDisplay",
    "net.minecraft.world.item.crafting.display.SlotDisplayContext": "dev.groundburst.port.recipe.SlotDisplayContext",
    "net.minecraft.util.TriState": "net.neoforged.neoforge.common.util.TriState",
    "net.minecraft.world.entity.EntitySpawnReason": "net.minecraft.world.entity.MobSpawnType",
    "net.neoforged.neoforge.registries.NewDatapackRegistryEvent": "net.neoforged.neoforge.registries.DataPackRegistryEvent",
    "net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent": "dev.groundburst.port.net.RegisterClientPayloadHandlersEvent",
    "net.minecraft.world.item.ItemUseAnimation": "net.minecraft.world.item.UseAnim",
    "net.minecraft.tags.BlockItemTagId": "dev.groundburst.port.BlockItemTagId",
    "net.minecraft.core.component.DataComponentGetter": None,
    "net.minecraft.world.attribute.EnvironmentAttributes": None,
    "net.minecraft.world.item.component.CookingFuel": None,
    "net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt": None,
    "com.mojang.blaze3d.platform.cursor.CursorTypes": "dev.groundburst.port.CursorTypes",
    "com.mojang.blaze3d.vertex.QuadInstance": "dev.groundburst.port.render.QuadInstance",
    "net.minecraft.client.color.block.BlockTintSource": "net.minecraft.client.color.block.BlockColor",
    "net.minecraft.client.renderer.block.BlockAndTintGetter": "net.minecraft.world.level.BlockAndTintGetter",
    "net.neoforged.neoforge.client.event.AddClientReloadListenersEvent": None,
    "net.minecraft.world.item.component.Consumable": "dev.groundburst.port.Consumable",
    "net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect": "dev.groundburst.port.ApplyStatusEffectsConsumeEffect",
    "net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty": None,
    "net.minecraft.world.entity.ItemOwner": None,
    "net.neoforged.neoforge.client.event.RegisterRangeSelectItemModelPropertyEvent": None,
    "net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent": None,
    "net.minecraft.client.renderer.block.dispatch.BlockStateModel": "dev.groundburst.port.render.BlockStateModel",
    "net.minecraft.client.renderer.block.dispatch.BlockStateModelPart": "dev.groundburst.port.render.BlockStateModelPart",
    "net.minecraft.client.resources.model.geometry.BakedQuad": "net.minecraft.client.renderer.block.model.BakedQuad",
    # JEI 27 -> 19
    "mezz.jei.api.recipe.types.IRecipeType": "mezz.jei.api.recipe.RecipeType",
    "mezz.jei.api.recipe.types.IRecipeHolderType": "mezz.jei.api.recipe.RecipeType",
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
    # GeckoLib 4's controllers are given their animatable, and their handlers its state.
    sub(r"\bnew AnimationController<(\w*)>\(\"", r'new AnimationController<\1>(this, "'),
    sub(r"\btest\.animatable\(\)", "test.getAnimatable()"),
    sub(r"\bEntityTypes\.", "EntityType."),
    sub(r"\bRenderTypes\.", "RenderType."),
    sub(r"\bLightCoordsUtil\.", "LightTexture."),
    sub(r"\bClientPacketDistributor\.sendToServer\(", "PacketDistributor.sendToServer("),
    sub(r"\bPushReaction\.IMMOVEABLE\b", "PushReaction.BLOCK"),
    sub(r"\bPushReaction\.POPPED\b", "PushReaction.DESTROY"),
    sub(r"\bTags\.BlockItems\.(\w+)\.block\(\)", r"Tags.Blocks.\1"),
    sub(r"\bTags\.BlockItems\.(\w+)\.item\(\)", r"Tags.Items.\1"),
    # NeoForge 26.x's transfer API: this branch's own (dev.groundburst.port.transfer), bridged to 1.21.1's handlers.
    sub(r"\bnet\.neoforged\.neoforge\.transfer\.", "dev.groundburst.port.transfer."),
    sub(r"\bnet\.neoforged\.neoforge\.capabilities\.Capabilities\b", "dev.groundburst.port.transfer.Capabilities"),
    sub(r"\bnet\.minecraft\.world\.level\.storage\.Value(Input|Output)\b", r"dev.groundburst.port.Value\1"),
    # --- methods --------------------------------------------------------------------------------------------------------
    sub(r"\.sendOverlayMessage\(((?:[^()]|\((?:[^()]|\([^()]*\))*\))*)\)", r".displayClientMessage(\1, true)"),
    sub(r"\bChunkPos\.containing\(", "new ChunkPos("),
    sub(r"\bChunkPos\.pack\(", "ChunkPos.asLong("),
    sub(r"\.getMinY\(\)", ".getMinBuildHeight()"),
    # 26.x's top is the highest block (inclusive); 1.21.1's getMaxBuildHeight is the first above it.
    sub(r"((?:[A-Za-z_]\w*(?:\([^()]*\))?\.)*[A-Za-z_]\w*(?:\([^()]*\))?)\.getMaxY\(\)", r"(\1.getMaxBuildHeight() - 1)"),
    sub(r"\.gui\.screen\(\)", ".screen"),
    sub(r"\.gui\.setScreen\(", ".setScreen("),
    sub(r"\.mainCamera\(\)", ".getMainCamera()"),
    sub(r"\.getMainCamera\(\)\.position\(\)", ".getMainCamera().getPosition()"),
    sub(r"\bget([XY])Rot\((\w+)\)", r"getView\1Rot(\2)"),
    # Renderers: 26.x's take a state and submit it; the stand-in bases do both in 1.21.1's render call.
    sub(r"\bextends EntityRenderer<(\w+), ([\w.]+)>", r"extends dev.groundburst.port.render.StateEntityRenderer<\1, \2>"),
    sub(r"\bimplements BlockEntityRenderer<([^<>{]+(?:<[^<>{]*>)?[^<>{]*),\s*([\w.]+)>", r"extends dev.groundburst.port.render.StateBlockEntityRenderer<\1, \2>"),
    sub(r"\bBlockEntityRenderer\.super\.", "super."),
    sub(r"\b[\w.]*(?:\(\))?\.getItemModelResolver\(\)", "dev.groundburst.port.render.ItemModelResolver.INSTANCE"),
    # The pose stack turns by quaternions in both; 26.x's rotate is 1.21.1's mulPose.
    sub(r"\.rotate\(((?:com\.mojang\.math\.)?Axis\.|new (?:org\.joml\.)?Quaternionf)", r".mulPose(\1"),
    sub(r"\.rotateDegrees\(((?:[^,()]|\([^()]*\))+),\s*((?:[^()]|\([^()]*\))+)\)", r".mulPose(\1.rotationDegrees(\2))"),
    sub(r"\.damageCooldownTime\b", ".invulnerableTime"),
    sub(r"\bInteractionResult\.SUCCESS_SERVER\b", "InteractionResult.SUCCESS"),
    sub(r"\.getUnitVec3i\(\)", ".getNormal()"),
    sub(r"\.noCollision\(\)", ".noCollission()"),
    sub(r"\bVegetationBlock\b", "BushBlock"),
    sub(r"\bScheduledTickAccess\b", "LevelAccessor"),
    # Entities are hurt on the server in 26.x (hurtServer, given the level); 1.21.1's hurt runs on either side.
    sub(r"(\b(?:public|protected) boolean )hurtServer\(ServerLevel (\w+), DamageSource (\w+), float (\w+)\)(\s*\{)",
        r"\1hurt(DamageSource \3, float \4)\5 if (!(this.level() instanceof ServerLevel \2)) return false;"),
    sub(r"\bsuper\.hurtServer\(\w+, ", "super.hurt("),
    sub(r"\.hurtServer\((?:[^,()]|\([^()]*\))+, ", ".hurt("),
    # A missile's look: a string in 26.x's custom model data, a number in 1.21.1's (dev.groundburst.port.Looks).
    sub(r"\bnew (?:net\.minecraft\.world\.item\.component\.)?CustomModelData\(\s*(?:java\.util\.)?List\.of\(\),\s*(?:java\.util\.)?List\.of\(\),\s*"
        r"(?:java\.util\.)?List\.of\(((?:[^()]|\((?:[^()]|\([^()]*\))*\))*)\),\s*(?:java\.util\.)?List\.of\(\)\)",
        r"new net.minecraft.world.item.component.CustomModelData(dev.groundburst.port.Looks.id(\1))"),
    # Dust takes its colour as a vector in 1.21.1.
    sub(r"\bnew (?:net\.minecraft\.core\.particles\.)?DustParticleOptions\(((?:[^,()]|\([^()]*\))+), ",
        r"new net.minecraft.core.particles.DustParticleOptions(net.minecraft.world.phys.Vec3.fromRGB24(\1).toVector3f(), "),
    # Block entity types: built with 1.21.1's builder (26.x constructs them from the factory and the blocks).
    sub(r"\bnew (?:net\.minecraft\.world\.level\.block\.entity\.)?BlockEntityType<>\(((?:[^()]|\((?:[^()]|\((?:[^()]|\([^()]*\))*\))*\))*)\)",
        r"net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(\1).build(null)"),
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
    sub(r"\bsuper\.extractRenderState\((\w+), (\w+, \w+, \w+)\)", r"super.render(\1.unwrap(), \2)"),
    sub(r"(\bpublic void )extractBackground\(GuiGraphicsExtractor (\w+), (int \w+, int \w+, float \w+)\)(\s*\{)",
        r"\1renderBackground(net.minecraft.client.gui.GuiGraphics gg_, \3)\4 GuiGraphicsExtractor \2 = new GuiGraphicsExtractor(gg_);"),
    sub(r"\bsuper\.extractBackground\((\w+), (\w+, \w+, \w+)\)", r"super.renderBackground(\1.unwrap(), \2)"),
    sub(r"(\bprotected void )extractLabels\(GuiGraphicsExtractor (\w+), (int \w+, int \w+)\)(\s*\{)",
        r"\1renderLabels(net.minecraft.client.gui.GuiGraphics gg_, \3)\4 GuiGraphicsExtractor \2 = new GuiGraphicsExtractor(gg_);"),
    sub(r"\bsuper\.extractLabels\((\w+), (\w+, \w+)\)", r"super.renderLabels(\1.unwrap(), \2)"),
    # Saved data: 26.x's storage takes the type; the stand-in builds 1.21.1's factory with the level (dev.groundburst.port).
    sub(r"((?:\w|\.|\(\))+)\.getDataStorage\(\)\.computeIfAbsent\((\w+)\)", r"\2.get(\1)"),
    sub(r"((?:\w|\.|\(\))+)\.getDataStorage\(\)\.get\((\w+)\)", r"\2.find(\1)"),
    # Screens' input: 26.x hands an event, 1.21.1 the position and button (or key); the event made from them.
    sub(r"(\bpublic boolean )mouseClicked\(MouseButtonEvent (\w+), boolean (\w+)\)(\s*\{)",
        r"\1mouseClicked(double mouseX_, double mouseY_, int button_)\4 MouseButtonEvent \2 = new MouseButtonEvent(mouseX_, mouseY_, button_); boolean \3 = false;"),
    sub(r"\bsuper\.mouseClicked\((\w+), \w+\)", r"super.mouseClicked(\1.x(), \1.y(), \1.button())"),
    sub(r"(\bpublic boolean )mouseReleased\(MouseButtonEvent (\w+)\)(\s*\{)",
        r"\1mouseReleased(double mouseX_, double mouseY_, int button_)\3 MouseButtonEvent \2 = new MouseButtonEvent(mouseX_, mouseY_, button_);"),
    sub(r"\bsuper\.mouseReleased\((\w+)\)", r"super.mouseReleased(\1.x(), \1.y(), \1.button())"),
    sub(r"(\bpublic boolean )mouseDragged\(MouseButtonEvent (\w+), double (\w+), double (\w+)\)(\s*\{)",
        r"\1mouseDragged(double mouseX_, double mouseY_, int button_, double \3, double \4)\5 MouseButtonEvent \2 = new MouseButtonEvent(mouseX_, mouseY_, button_);"),
    sub(r"\bsuper\.mouseDragged\((\w+), (\w+), (\w+)\)", r"super.mouseDragged(\1.x(), \1.y(), \1.button(), \2, \3)"),
    sub(r"(\bpublic boolean )keyPressed\(KeyEvent (\w+)\)(\s*\{)",
        r"\1keyPressed(int key_, int scancode_, int modifiers_)\3 KeyEvent \2 = new KeyEvent(key_, scancode_, modifiers_);"),
    sub(r"\bsuper\.keyPressed\((\w+)\)", r"super.keyPressed(\1.key(), \1.scancode(), \1.modifiers())"),
    # 1.21.1's container screens draw their panel in renderBg, called from renderBackground (which main's code overrides).
    sub(r"(\bclass \w+(?:<[^{]*?>)? extends AbstractContainerScreen<[^{]*\{)",
        r"\1\n    @Override\n    protected void renderBg(net.minecraft.client.gui.GuiGraphics gg_, float partial_, int mouseX_, int mouseY_) {}\n"),
    # --- renamed or reshaped since 1.21.1 ------------------------------------------------------------------------------------
    sub(r"\bMobEffects\.NAUSEA\b", "MobEffects.CONFUSION"),
    sub(r"\bMobEffects\.MINING_FATIGUE\b", "MobEffects.DIG_SLOWDOWN"),
    sub(r"\bMobEffects\.SLOWNESS\b", "MobEffects.MOVEMENT_SLOWDOWN"),
    sub(r"\bMobEffects\.HASTE\b", "MobEffects.DIG_SPEED"),
    sub(r"\bMobEffects\.SPEED\b", "MobEffects.MOVEMENT_SPEED"),
    sub(r"\bMobEffects\.STRENGTH\b", "MobEffects.DAMAGE_BOOST"),
    sub(r"\bMobEffects\.INSTANT_HEALTH\b", "MobEffects.HEAL"),
    sub(r"\bMobEffects\.INSTANT_DAMAGE\b", "MobEffects.HARM"),
    sub(r"\bMobEffects\.JUMP_BOOST\b", "MobEffects.JUMP"),
    sub(r"\bMobEffects\.RESISTANCE\b", "MobEffects.DAMAGE_RESISTANCE"),
    sub(r"\.getDeltaTracker\(\)", ".getTimer()"),
    sub(r"\.preventsBlockDrops\(\)", ".getAbilities().instabuild"),
    sub(r"\b(?:net\.minecraft\.world\.phys\.shapes\.)?Shapes\.rotateHorizontal\(", "dev.groundburst.port.Shapes2.rotateHorizontal("),
    sub(r"\.recipeAccess\(\)\.recipeMap\(\)\.byType\(", ".getRecipeManager().byType("),
    sub(r"\.recipeAccess\(\)", ".getRecipeManager()"),
    sub(r"\.identifier\(\)", ".location()"),
    sub(r"\bTags\.EntityType\.", "Tags.EntityTypes."),
    sub(r"\.typeHolder\(\)\.is\(", ".getType().is("),
    sub(r"(\b\w+)\.is\(EntityTypeTags\.", r"\1.getType().is(EntityTypeTags."),
    sub(r"\bBlockTags\.GRASS_BLOCKS\b", "BlockTags.DIRT"),          # 1.21.1's dirt tag still holds the grass blocks
    sub(r"\bBlockTags\.MUD\b", "net.minecraft.world.level.block.Blocks.MUD"),
    sub(r"\bBlockTags\.MOSS_BLOCKS\b", "net.minecraft.world.level.block.Blocks.MOSS_BLOCK"),
    sub(r"(\w+)\.is\(BlockTags\.ICE_MELTS_WHEN_DESTROYED_ABOVE\)", r"\1.blocksMotion()"),
    sub(r"\s*\|\|\s*\w+\.is\(Blocks\.BUSH\)", ""),                     # 26.x's bush block
    sub(r"\s*\|\s*Block\.UPDATE_SKIP_(?:BLOCK_ENTITY_SIDEEFFECTS|ON_PLACE)\b", ""),
    sub(r"\.getRespawnData\(\)\.pos\(\)", ".getSharedSpawnPos()"),
    sub(r"\.spawnProtectionRadius\(\)", ".getSpawnProtectionRadius()"),
    sub(r"\.requires\(Commands\.hasPermission\(([^()]+)\)\)", r".requires(source_ -> source_.hasPermission(\1))"),
    sub(r"\bResourceKeyArgument\.getRegistryKey\(", "dev.groundburst.port.Args.getRegistryKey("),
    sub(r"\.needsSync\b", ".hurtMarked"),
    sub(r"((?:\w+\.)*\w+)\.canSpreadFireAround\([^()]*\)", r"\1.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOFIRETICK)"),
    sub(r"\b(?:net\.minecraft\.client\.)?Minecraft\.getInstance\(\)\.has(Shift|Control|Alt)Down\(\)", r"net.minecraft.client.gui.screens.Screen.has\1Down()"),
    sub(r"\b(?:this\.)?minecraft\.has(Shift|Control|Alt)Down\(\)", r"net.minecraft.client.gui.screens.Screen.has\1Down()"),
    sub(r"\bnew DynamicTexture\(\(\) -> \"[^\"]*\", ", "new DynamicTexture("),
    # 26.x's images take ARGB colours, 1.21.1's ABGR.
    sub(r"\.setPixel\(((?:[^,()]|\([^()]*\))+), ((?:[^,()]|\([^()]*\))+), ((?:[^()]|\((?:[^()]|\([^()]*\))*\))+)\)",
        r".setPixelRGBA(\1, \2, dev.groundburst.port.ARGB.toABGR(\3))"),
    sub(r"(\b\w+)\.getPixel\(((?:[^()]|\([^()]*\))*)\)", r"dev.groundburst.port.ARGB.toABGR(\1.getPixelRGBA(\2))"),
    sub(r"\.isViewBlocking\(\((\w+), (\w+), (\w+), \w+\) -> ", r".isViewBlocking((\1, \2, \3) -> "),
    sub(r"\.emissiveRendering\((\w+) -> ", r".emissiveRendering((\1, level_, pos_) -> "),
    sub(r"\.coldEnoughToSnow\(((?:[^,()]|\([^()]*\))+), (?:[^()]|\([^()]*\))+\)", r".coldEnoughToSnow(\1)"),
    sub(r"\.warmEnoughToRain\(((?:[^,()]|\([^()]*\))+), (?:[^()]|\([^()]*\))+\)", r".warmEnoughToRain(\1)"),
    sub(r"\.getPos\(\)\.pack\(\)", ".getPos().toLong()"),
    sub(r"\.markUnsaved\(\)", ".setUnsaved(true)"),
    sub(r"\.isSolidRender\(\)", ".isSolidRender(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO)"),
    sub(r"\.snapTo\(", ".moveTo("),
    sub(r"((?:net\.minecraft\.client\.)?Minecraft\.getInstance\(\)|\b\w+)\.getWindow\(\)\.getGuiScale\(\)", r"((int) \1.getWindow().getGuiScale())"),
    sub(r"\.lookupOrThrow\(([^()]+)\)\.getOptional\(", r".registryOrThrow(\1).getOptional("),
    sub(r"\.lookupOrThrow\(([^()]+)\)\.getValue\(", r".registryOrThrow(\1).get("),
    sub(r"\.getStructureTemplateManager\(\)", ".getStructureManager()"),
    sub(r"\bNewDatapackRegistryEvent\b", "DataPackRegistryEvent.NewRegistry"),
    sub(r"\.worldRegistry\(", ".dataPackRegistry("),
    sub(r"\.create\(([^,()]+), EntitySpawnReason\.\w+\)", r".create(\1)"),
    sub(r"\bEntitySpawnReason\.", "MobSpawnType."),
    sub(r"\bItemUseAnimation\b", "UseAnim"),
    sub(r"\bDeferredRegister\.Entities\b", "dev.groundburst.port.EntityRegister"),
    sub(r"\bDeferredRegister\.createEntities\(", "new dev.groundburst.port.EntityRegister("),
    # A server player's level is a server level in 26.x.
    sub(r"\bServerLevel (\w+) = (\w+)\.level\(\);", r"ServerLevel \1 = (ServerLevel) \2.level();"),
    # HUD layers: 26.x's draw through the extractor (dev.groundburst.port.GuiGraphicsExtractor).
    sub(r"\.register(Above|Below)\(([^,()]+(?:\([^()]*\))?), ([^,()]+(?:\([^()]*\))?), (\w+)::(\w+)\)",
        r".register\1(\2, \3, (gg_, dt_) -> \4.\5(new dev.groundburst.port.GuiGraphicsExtractor(gg_), dt_))"),
    sub(r"\.register(Above|Below)All\(([^,()]+(?:\([^()]*\))?), (\w+)::(\w+)\)",
        r".register\1All(\2, (gg_, dt_) -> \3.\4(new dev.groundburst.port.GuiGraphicsExtractor(gg_), dt_))"),
    # Client-bound payloads: 1.21.1 takes the handler with the codec (the client's, registered later: dev.groundburst.port.net).
    sub(r"\.playToClient\(([\w.]+)\.TYPE, ([\w.]+)\.CODEC\)",
        r".playToClient(\1.TYPE, \2.CODEC, dev.groundburst.port.net.RegisterClientPayloadHandlersEvent.handler(\1.TYPE))"),
    # A bundle's or a container's contents: 26.x's are templates, 1.21.1's stacks.
    sub(r"\bfor \(ItemStackTemplate (\w+) : ((?:[^()]|\([^()]*\))+)\)", r"for (ItemStackTemplate \1 : ItemStackTemplate.of(\2))"),
    # Attachments: 26.x serializes them with a map codec.
    sub(r"(AttachmentType\.builder\([^;]*?)\.serialize\(([\w.]+)\)", r"\1.serialize(\2.codec())"),
    sub(r"\.getRecipeManager\(\)\.byType\(", ".getRecipeManager().getAllRecipesFor("),
    sub(r"\.addCraftingStation\(", ".addRecipeCatalysts("),
    # Key mappings: 1.21.1's categories are translation keys (26.x's KeyMapping.Category of an id: key.category.<ns>.<path>).
    sub(r"\bnew KeyMapping\.Category\(((?:[^()]|\([^()]*\))+)\)", r"dev.groundburst.port.input.KeyEvent.category(\1)"),
    sub(r"\bKeyMapping\.Category\b", "String"),
    sub(r"(\b[A-Z_]+)\.matches\((\w+)\)", r"\1.matches(\2.key(), \2.scancode())"),
    sub(r"(\b\w+)\.getKeyEvent\(\)", r"new dev.groundburst.port.input.KeyEvent(\1.getKeyCode(), \1.getScanCode(), \1.getModifiers())"),
    sub(r"\.getHoveredSlot\(\)", ".getSlotUnderMouse()"),
    # A block state's particle sprite (26.x: its model set's particle material).
    sub(r"\.getModelManager\(\)\.getBlockStateModelSet\(\)\.get\(((?:[^()]|\([^()]*\))+)\)\.particleMaterial\(\)\.sprite\(\)",
        r".getBlockRenderer().getBlockModelShaper().getParticleIcon(\1)"),
    # Block models' parts and quads (dev.groundburst.port.render: BlockStateModel, BlockStateModelPart, QuadInstance).
    sub(r"\b(?:\w+(?:\.\w+\(\))*)\.getModelManager\(\)\.getBlockStateModelSet\(\)\.get\(", "dev.groundburst.port.render.BlockStateModel.of("),
    sub(r"(\b\w+)\.putBakedQuad\(", r"dev.groundburst.port.render.QuadInstance.put(\1, "),
    sub(r"\bRenderType\.itemTranslucent\(", "RenderType.itemEntityTranslucentCull("),
    sub(r"\bSheets\.cutoutBlockItemSheet\(\)", "Sheets.cutoutBlockSheet()"),
    sub(r"\b\w+\.gameRenderer\.gameRenderState\(\)\.windowRenderState\.appropriateLineWidth\b", "2.0F"),
    sub(r"\bLightTexture\.max\(", "dev.groundburst.port.Lights.max("),
    # A crystal cluster: 26.x gives its width, 1.21.1 how far its box is inset from each side.
    sub(r"\bnew AmethystClusterBlock\(((?:[^,()]|\([^()]*\))+), ((?:[^,()]|\([^()]*\))+), ", r"new AmethystClusterBlock(\1, (16 - (\2)) / 2, "),
    # Consumables: 1.21.1's food (dev.groundburst.port.Consumable).
    sub(r"\.component\(DataComponents\.CONSUMABLE, ", ".food("),
    sub(r"\bnet\.minecraft\.client\.renderer\.item\.ItemStackRenderState\b", "dev.groundburst.port.render.ItemStackRenderState"),
    sub(r"\bnet\.minecraft\.util\.Util\b", "net.minecraft.Util"),
    sub(r"\bprotected void applyImplicitComponents\(DataComponentGetter (\w+)\)",
        r"protected void applyImplicitComponents(net.minecraft.world.level.block.entity.BlockEntity.DataComponentInput \1)"),
    sub(r"\bLightTexture\.getLightCoords\(", "net.minecraft.client.renderer.LevelRenderer.getLightColor("),
    sub(r"\bInputConstants\.Type\.KEYBOARD\b", "InputConstants.Type.KEYSYM"),
    sub(r"\.getCamera\(\)\.entity\(\)", ".getCamera().getEntity()"),
    sub(r"\.getCamera\(\)\.position\(\)", ".getCamera().getPosition()"),
    sub(r"\.startRiding\(((?:[^,()]|\([^()]*\))+), (true|false), (?:true|false)\)", r".startRiding(\1, \2)"),
    sub(r"\.isSectionCompiledAndVisible\(((?:[^()]|\([^()]*\))+), [^,()]+\)", r".isSectionCompiled(\1)"),
    sub(r"\bDirection\.getApproximateNearest\(", "Direction.getNearest("),
    sub(r"\bFogType\.ATMOSPHERIC\b", "FogType.NONE"),
    sub(r"\.getSelectedSlot\(\)", ".selected"),
    sub(r"\bcontext\.font\(\)", "context.getFont()"),
    sub(r"\bnet\.minecraft\.client\.renderer\.rendertype\.RenderTypes?\b", "net.minecraft.client.renderer.RenderType"),
    sub(r"\bBuiltInRegistries\.(\w+)\.getValue\(", r"BuiltInRegistries.\1.get("),
    sub(r"(\b\w+)\.get\(DataComponents\.DYE\)", r"dev.groundburst.port.Stacks.dye(\1)"),
    sub(r"\bsuper\.getOcclusionShape\((\w+)\)", r"super.getOcclusionShape(\1, level_, pos_)"),
    sub(r"\bsuper\.propagatesSkylightDown\((\w+)\)", r"super.propagatesSkylightDown(\1, level_, pos_)"),
    sub(r"\bsuper\.getLightDampening\((\w+)\)", r"super.getLightBlock(\1, level_, pos_)"),
    # 26.x's flash takes a colour; 1.21.1's is plain.
    sub(r"\b(?:net\.minecraft\.core\.particles\.)?ColorParticleOption\.create\(ParticleTypes\.FLASH, [^()]*\)", "ParticleTypes.FLASH"),
    sub(r"\bBlock\.column\(", "dev.groundburst.port.Shapes2.column("),
    sub(r"(\b\w+(?:\.\w+\(\))*\.getPersistentData\(\))\.(get(?:String|Int|Long|Float|Double|Boolean)Or)\(", r"dev.groundburst.port.Nbt.\2(\1, "),
    sub(r"\.placeItemBackInInventory\(((?:[^()]|\([^()]*\))+), net\.minecraft\.util\.Prediction\.\w+\)", r".placeItemBackInInventory(\1)"),
    sub(r"\.getCooldowns\(\)\.(addCooldown|isOnCooldown|removeCooldown)\((\w+)\b(?!\.)", r".getCooldowns().\1(\2.getItem()"),
    sub(r"\bModConfig\.Type\.SYNCED\b", "ModConfig.Type.SERVER"),
    sub(r"\bModConfig\.Type\.LOCAL\b", "ModConfig.Type.COMMON"),
    sub(r"\(net\.neoforged\.neoforge\.client\.event\.AddClientReloadListenersEvent (\w+)\) -> \1\.addListener\(\s*(?:[^,()]|\([^()]*\))+,\s*",
        r"(net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent \1) -> \1.registerReloadListener("),
    sub(r"\(AddClientReloadListenersEvent (\w+)\) -> \1\.addListener\(\s*(?:[^,()]|\([^()]*\))+,\s*",
        r"(net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent \1) -> \1.registerReloadListener("),
    sub(r"(\w+)\.biomeResolver\(\)\.getNoiseBiome\(((?:[^()]|\([^()]*\))+)\)", r"\1.biomeSource().getNoiseBiome(\2, \1.randomState().sampler())"),
    sub(r"\bRotation\.LEGACY_CODEC\b", "dev.groundburst.port.LegacyCodecs.ROTATION"),
    sub(r"(\b\w+)\.environmentAttributes\(\)\.getValue\(EnvironmentAttributes\.WATER_EVAPORATES, [^()]*\)", r"\1.dimensionType().ultraWarm()"),
    sub(r"(\b\w+)\.getLightDampening\(\)", r"\1.getLightBlock(level, pos)"),         # (a block's own state, in its place)
    sub(r"(\bthis\.\w+)\.keyPressed\((\w+)\)", r"\1.keyPressed(\2.key(), \2.scancode(), \2.modifiers())"),
    # An ingredient's contents.
    sub(r"((?:this\.)?(?:\w+\.)*(?:ingredient\(\)|catalyst\(\)\.get\(\)))\.items\(\)", r"dev.groundburst.port.Ingredients.items(\1)"),
    sub(r"((?:this\.)?(?:\w+\.)*ingredient\(\))\.fluids\(\)", r"dev.groundburst.port.Ingredients.fluids(\1)"),
    # JEI 19's recipe types (26.x's JEI: IRecipeType, IRecipeHolderType).
    sub(r"\bIRecipeType\.create\(", "new RecipeType<>("),
    sub(r"\bIRecipeType<", "RecipeType<"),
    sub(r"\bIRecipeHolderType<(\w+)>", r"RecipeType<net.minecraft.world.item.crafting.RecipeHolder<\1>>"),
    sub(r"\bIRecipeHolderType\.create\(", "RecipeType.createFromVanilla("),
    sub(r"\bRecipeIngredientRole\.CRAFTING_STATION\b", "RecipeIngredientRole.CATALYST"),
    sub(r"\bnew (?:net\.minecraft\.world\.item\.component\.)?DyedItemColor\(((?:[^(),]|\([^()]*\))+)\)", r"new net.minecraft.world.item.component.DyedItemColor(\1, true)"),
]


def param(type_re: str) -> str:
    """A parameter of a type matching {type_re}, its name captured (annotations and final allowed before it)."""
    return r"(?:final\s+)?(?:@[\w.]+\s+)*(?:" + type_re + r")\s+(\w+)"


C = r"\s*,\s*"
QN = r"(?:[\w.]+\.)?"                                     # an optional package or outer class before a type's name

# Overridden methods whose signatures changed: main's parameter names and body kept, the signature 1.21.1's, and a line or
# two at the body's top giving main's code what it had (a server level, a tick scheduler).
SIGNATURES = [
    # Tooltips: 26.x hands a consumer and the display settings; 1.21.1 a list.
    sub(r"(\bpublic void appendHoverText\(\s*)" + param(QN + "ItemStack") + C + param(QN + "TooltipContext") + C + param(QN + "TooltipDisplay")
        + C + param(QN + r"Consumer<(?:[\w.]+\.)?Component>") + C + param(QN + "TooltipFlag") + r"\s*\)(\s*\{)",
        r"\1net.minecraft.world.item.ItemStack \2, net.minecraft.world.item.Item.TooltipContext \3, java.util.List<net.minecraft.network.chat.Component> \5_list, "
        r"net.minecraft.world.item.TooltipFlag \6)\7 java.util.function.Consumer<net.minecraft.network.chat.Component> \5 = \5_list::add;"),
    sub(r"\bsuper\.appendHoverText\((\w+), (\w+), \w+, (\w+), (\w+)\)", r"super.appendHoverText(\1, \2, \3_list, \4)"),
    # Neighbour changes: 26.x passes a tick scheduler and a random; 1.21.1 the level does both.
    sub(r"(\bprotected BlockState updateShape\(\s*)" + param("BlockState") + C + param(QN + "LevelReader") + C + param(QN + "LevelAccessor") + C
        + param("BlockPos") + C + param("Direction") + C + param("BlockPos") + C + param("BlockState") + C + param(QN + "RandomSource") + r"\s*\)(\s*\{)",
        r"\1BlockState \2, Direction \6, BlockState \8, net.minecraft.world.level.LevelAccessor \3, BlockPos \5, BlockPos \7)\10 "
        r"net.minecraft.world.level.LevelAccessor \4 = \3; net.minecraft.util.RandomSource \9 = \3.getRandom();"),
    sub(r"\bsuper\.updateShape\((\w+), (\w+), \w+, (\w+), (\w+), (\w+), (\w+), \w+\)", r"super.updateShape(\1, \4, \6, \2, \3, \5)"),
    sub(r"(\bprotected void neighborChanged\(\s*" + param("BlockState") + C + param(QN + "Level") + C + param("BlockPos") + C + param(QN + "Block") + C + r")"
        + param(QN + "Orientation") + r"(" + C + r")", r"\1BlockPos fromPos_\7"),
    sub(r"\bsuper\.neighborChanged\((\w+), (\w+), (\w+), (\w+), \w+, (\w+)\)", r"super.neighborChanged(\1, \2, \3, \4, fromPos_, \5)"),
    sub(r"\.updateNeighborsAt\(([^,()]+), ([^,()]+), [^,()]+\)", r".updateNeighborsAt(\1, \2)"),
    # A neighbour update with no orientation (26.x's may be null): 1.21.1 takes where it comes from, which must be given.
    sub(r"(\b\w+)\.neighborChanged\(([^,()]+), ((?:[^,()]|\([^()]*\))+), null\)", r"\1.neighborChanged(\2, \3, \2)"),
    # Entities inside a block: 26.x adds an effect applier and whether it was a precise check.
    sub(r"(\bprotected void entityInside\(\s*" + param("BlockState") + C + param(QN + "Level") + C + param("BlockPos") + C + param(QN + "Entity") + r")"
        + C + param(QN + "InsideBlockEffectApplier") + C + param("boolean") + r"\s*\)", r"\1)"),
    sub(r"\bsuper\.entityInside\((\w+), (\w+), (\w+), (\w+), \w+, \w+\)", r"super.entityInside(\1, \2, \3, \4)"),
    # After a block is removed: 1.21.1 tells the block on removal, with what replaced it.
    sub(r"(\bprotected void )affectNeighborsAfterRemoval\(\s*" + param("BlockState") + C + param(QN + "ServerLevel") + C + param("BlockPos") + C
        + param("boolean") + r"\s*\)(\s*\{)",
        r"\1onRemove(BlockState \2, net.minecraft.world.level.Level \3_l, BlockPos \4, BlockState newState_, boolean \5)\6 "
        r"super.onRemove(\2, \3_l, \4, newState_, \5); if (!(\3_l instanceof net.minecraft.server.level.ServerLevel \3) || \2.is(newState_.getBlock())) return;"),
    sub(r"\bsuper\.affectNeighborsAfterRemoval\([^;]*\);", ""),
    # Light and shape questions about a state: 1.21.1 asks them of a state in a place.
    sub(r"(\bprotected boolean propagatesSkylightDown\(\s*" + param("BlockState") + r")\s*\)",
        r"\1, net.minecraft.world.level.BlockGetter level_, BlockPos pos_)"),
    sub(r"(\bprotected int )getLightDampening\(\s*" + param("BlockState") + r"\s*\)",
        r"\1getLightBlock(BlockState \2, net.minecraft.world.level.BlockGetter level_, BlockPos pos_)"),
    sub(r"(\bprotected VoxelShape getOcclusionShape\(\s*" + param("BlockState") + r")\s*\)",
        r"\1, net.minecraft.world.level.BlockGetter level_, BlockPos pos_)"),
    sub(r"(\bprotected int getAnalogOutputSignal\(\s*" + param("BlockState") + C + param(QN + "Level") + C + param("BlockPos") + r")" + C
        + param(QN + "Direction") + r"\s*\)(\s*\{)", r"\1)\6 Direction \5 = Direction.NORTH;"),
    # Effects tick on the server in 26.x, given its level.
    sub(r"(\bpublic boolean applyEffectTick\(\s*)" + param(QN + "ServerLevel") + C + param(QN + "LivingEntity") + C + param("int") + r"\s*\)(\s*\{)",
        r"\1net.minecraft.world.entity.LivingEntity \3, int \4)\5 if (!(\3.level() instanceof net.minecraft.server.level.ServerLevel \2)) return true;"),
    sub(r"(\bpublic void inventoryTick\(\s*" + param(QN + "ItemStack") + C + r")" + param(QN + "ServerLevel") + C + param(QN + "Entity") + C
        + param(QN + "EquipmentSlot") + r"\s*\)(\s*\{)",
        r"\1net.minecraft.world.level.Level \3_l, net.minecraft.world.entity.Entity \4, int slotId_, boolean selected_)\6 "
        r"if (!(\3_l instanceof net.minecraft.server.level.ServerLevel \3)) return; "
        r"net.minecraft.world.entity.EquipmentSlot \5 = selected_ ? net.minecraft.world.entity.EquipmentSlot.MAINHAND : null;"),
    sub(r"(\bpublic void playerDestroy\(\s*)" + param(QN + "ServerLevel") + C + param(QN + "ServerPlayer") + r"(" + C + r")",
        r"\1net.minecraft.world.level.Level \2, net.minecraft.world.entity.player.Player \3\4"),
    # (A type-use annotation goes before the simple name of a qualified type: none is kept.)
    sub(r"(\bpublic ItemStack pickupBlock\(\s*)(?:@[\w.]+\s+)*" + QN + r"LivingEntity(\s+\w+)", r"\1net.minecraft.world.entity.player.Player\2"),
    sub(r"(\bpublic boolean emptyContents\(\s*)(?:@[\w.]+\s+)*" + QN + r"LivingEntity(\s+\w+)", r"\1net.minecraft.world.entity.player.Player\2"),
    sub(r"(\bprotected void onExplosionHit\(\s*)" + param("BlockState") + C + param(QN + "ServerLevel") + C + param("BlockPos") + C
        + param(QN + "Explosion") + C + param(QN + r"BiConsumer<[^>]*>") + r"\s*\)(\s*\{)",
        r"\1BlockState \2, net.minecraft.world.level.Level \3_l, BlockPos \4, net.minecraft.world.level.Explosion \5, "
        r"java.util.function.BiConsumer<net.minecraft.world.item.ItemStack, BlockPos> \6)\7 "
        r"if (!(\3_l instanceof net.minecraft.server.level.ServerLevel \3)) { super.onExplosionHit(\2, \3_l, \4, \5, \6); return; }"),
    sub(r"(\bprotected boolean hasClickedOutside\(\s*" + param("double") + C + param("double") + C + param("int") + C + param("int") + r")\s*\)",
        r"\1, int button_)"),
    sub(r"\bsuper\.hasClickedOutside\(((?:[^()]|\([^()]*\))*)\)", r"super.hasClickedOutside(\1, button_)"),
    # Using an item: 1.21.1's use answers with the held stack too.
    sub(r"(\bpublic )InteractionResult( use\(\s*" + param(QN + "Level") + C + param(QN + "Player") + C + param(QN + "InteractionHand") + r"\s*\)\s*\{)",
        r"\1net.minecraft.world.InteractionResultHolder<net.minecraft.world.item.ItemStack>\2"),
    sub(r"(\bpublic )boolean( releaseUsing\()", r"\1void\2"),
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


def use_holder(method: str) -> str:
    """26.x's use returns an InteractionResult; 1.21.1's a holder of it and the held stack."""
    names = re.search(r"use\(\s*" + param(QN + "Level") + C + param(QN + "Player") + C + param(QN + "InteractionHand"), method)
    player, hand = names.group(2), names.group(3)
    return re.sub(r"\breturn ([^;]+);", rf"return dev.groundburst.port.Results.holder(\1, {player}, {hand});", method)


def item_result(method: str) -> str:
    """26.x's useItemOn answers with an InteractionResult; 1.21.1's with an ItemInteractionResult. (So do the helpers
    that answer for it: those that may try the empty hand.)"""
    method = re.sub(r"\bInteractionResult (\w+)\(", r"net.minecraft.world.ItemInteractionResult \1(", method, count=1)
    method = re.sub(r"\bInteractionResult\.TRY_WITH_EMPTY_HAND\b", "net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION", method)
    method = re.sub(r"\bInteractionResult\.PASS\b", "net.minecraft.world.ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION", method)
    method = re.sub(r"\bInteractionResult\.(SUCCESS|CONSUME|FAIL)\b", r"net.minecraft.world.ItemInteractionResult.\1", method)
    return method


def release_void(method: str) -> str:
    return re.sub(r"\breturn (?:true|false);", "return;", method)


def try_empty_hand(method: str) -> str:
    return item_result(method) if "TRY_WITH_EMPTY_HAND" in method else method


BODIES = [
    (r"\bpublic net\.minecraft\.world\.InteractionResultHolder<net\.minecraft\.world\.item\.ItemStack> use\([^)]*\)\s*\{", use_holder),
    (r"\bprotected InteractionResult useItemOn\([^)]*\)\s*\{", item_result),
    (r"\b(?:public |protected |private )?(?:static )?InteractionResult \w+\([^)]*\)\s*\{", try_empty_hand),
    (r"\bpublic void releaseUsing\([^)]*\)\s*\{", release_void),
]

# Vanilla 1.21.1 classes whose codec() is abstract: a block extending one directly needs its own.
CODEC_BASES = r"(?:BaseEntityBlock|HorizontalDirectionalBlock|DirectionalBlock|FaceAttachedHorizontalDirectionalBlock|BushBlock)"


def block_codecs(text: str) -> str:
    """Blocks give a codec in 1.21.1 (for the block type registry): this one, as it is."""
    if re.search(r"\bcodec\(\)\s*\{", text):
        return text
    def add(m: re.Match) -> str:
        return m.group(0) + (f"\n    @Override\n    protected com.mojang.serialization.MapCodec<? extends {m.group(2)}> codec() {{\n"
                             f"        return com.mojang.serialization.MapCodec.unit(this);\n    }}\n")
    return re.sub(r"(\n(?:public |protected |private )?(?:final )?class (\w+)(?:<[^{]*?>)? extends " + CODEC_BASES + r"\b[^{]*\{)", add, text)


def pre_remove(text: str) -> str:
    """26.x tells a block entity just before its block is replaced while the block entity is still there
    (preRemoveSideEffects); 1.21.1 only removes it. The topmost of the mod's classes to have it calls it from setRemoved,
    unless the chunk is being unloaded or its block is still there: something took the block entity away first (Create
    does, after saving it, as it carries the block off), and 26.x would not call it then either (no drops twice)."""
    if "preRemoveSideEffects" not in text or not re.search(r"\bextends BlockEntity\b", text):
        return text
    text = re.sub(r"@Override\s*\n(\s*public void preRemoveSideEffects\()", r"\1", text)
    text = re.sub(r"\n\s*super\.preRemoveSideEffects\([^;]*\);", "", text)
    hook = ("\n    private boolean unloading_;\n\n    @Override\n    public void onChunkUnloaded() {\n        super.onChunkUnloaded();\n"
            "        this.unloading_ = true;\n    }\n\n    @Override\n    public void setRemoved() {\n"
            "        if (!this.unloading_ && this.level != null && !this.level.isClientSide()\n"
            "                && !this.level.getBlockState(this.worldPosition).is(this.getBlockState().getBlock())) {\n"
            "            preRemoveSideEffects(this.worldPosition, this.getBlockState());\n        }\n        super.setRemoved();\n    }\n")
    end = text.rstrip().rfind("}")
    return text[:end] + hook + text[end:]


def saved_data(text: str) -> str:
    """1.21.1's saved data saves itself (26.x's is saved by its type's codec): it does so through its TYPE."""
    if not re.search(r"\bextends SavedData\b", text) or re.search(r"\bCompoundTag save\(", text):
        return text
    end = text.rstrip().rfind("}")
    hook = ("\n    @Override\n    public net.minecraft.nbt.CompoundTag save(net.minecraft.nbt.CompoundTag tag_, net.minecraft.core.HolderLookup.Provider registries_) {\n"
            "        return TYPE.save(this, tag_, registries_);\n    }\n")
    return text[:end] + hook + text[end:]


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


def top_level_commas(args: str) -> int:
    depth, n, i = 0, 0, 0
    while i < len(args):
        ch = args[i]
        if ch in "([{":
            depth += 1
        elif ch in ")]}":
            depth -= 1
        elif ch == "," and depth == 0:
            n += 1
        elif ch == '"':
            i += 1
            while i < len(args) and args[i] != '"':
                i += 2 if args[i] == "\\" else 1
        i += 1
    return n


def wide_composites(text: str) -> str:
    """26.x builds stream codecs of up to twelve fields; 1.21.1's stop at six (dev.groundburst.port.StreamCodecs)."""
    def fix(m: re.Match, args: str) -> str:
        fields = top_level_commas(args) // 2
        name = "dev.groundburst.port.StreamCodecs.composite(" if fields > 6 else m.group(0)
        return name + args + ")"
    return calls(text, r"\bStreamCodec\.composite\(", fix)


def overlay_messages(text: str) -> str:
    """26.x's overlay message is 1.21.1's client message shown over the hotbar."""
    return calls(text, r"\.sendOverlayMessage\(", lambda m, args: f".displayClientMessage({args}, true)")


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
    """26.x's biome resolver is given a quart position; 1.21.1's a climate sampler too (and the fill takes one)."""
    lam = re.match(r"\s*\((\w+), (\w+), (\w+)\) ->", args)
    if not lam or len(split_args(args)) != 1:
        return m.group(0) + args + ")"
    return f".fillBiomesFromNoise(({lam.group(1)}, {lam.group(2)}, {lam.group(3)}, sampler_) ->{args[lam.end():]}, null)"


def call_fixes(text: str) -> str:
    """Calls whose arguments changed, too deep for a pattern."""
    text = calls(text, r"\.isSectionCompiledAndVisible\(", lambda m, a: f".isSectionCompiled({split_args(a)[0].strip()})")
    text = calls(text, r"\.placeItemBackInInventory\(",
                 lambda m, a: f".placeItemBackInInventory({split_args(a)[0].strip()})" if "Prediction" in a else m.group(0) + a + ")")
    text = calls(text, r"\.fillBiomesFromNoise\(", fill_biomes)
    # 26.x sends particles to everyone, forced (seen from far) or not; 1.21.1 forces them player by player.
    def particles(m: re.Match, a: str) -> str:
        p = split_args(a)
        if len(p) == 11 and p[1].strip() in ("true", "false") and p[2].strip() in ("true", "false"):
            return f"dev.groundburst.port.Particles.send({m.group(1)}, {p[0].strip()}, {p[1].strip()}, {', '.join(x.strip() for x in p[3:])})"
        return m.group(0) + a + ")"
    text = calls(text, r"(\b\w+)\.sendParticles\(", particles)
    # JEI 19's slots take stacks (26.x's JEI adds an item, stack, ingredient or fluid with one method: dev.groundburst.port.Jei).
    if "mezz.jei" in text:
        text = calls(text, r"(Background\(\)\s*|\bslot)\.add\(",
                     lambda m, a: f"{m.group(1)}.addIngredientsUnsafe(dev.groundburst.port.Jei.of({a}))")
    return text


COLOURED = {"DYED_TERRACOTTA": "TERRACOTTA"}


def coloured_blocks(text: str) -> str:
    """26.x keeps a colour's blocks in families (Blocks.CONCRETE.pick(DyeColor.GRAY)); 1.21.1 has each by name."""
    return re.sub(r"\bBlocks\.([A-Z_]+)\.pick\(DyeColor\.([A-Z_]+)\)",
                  lambda m: f"Blocks.{m.group(2)}_{COLOURED.get(m.group(1), m.group(1))}", text)


NBT_READS = r"get(?:String|Int|Long|Float|Double|Boolean)Or|getCompoundOrEmpty|read|store"


def declared_types(text: str) -> str:
    """Calls on variables of types whose methods changed, found by their declarations: a chunk position's x and z are
    fields in 1.21.1 (and it packs to a long with toLong), a compound tag's reads with defaults and codec reads and writes
    are this branch's (dev.groundburst.port.Nbt), and a pose moves through its matrix."""
    for name in set(re.findall(r"\bChunkPos (\w+)\b", text)):
        text = re.sub(rf"\b{name}\.([xz])\(\)", rf"{name}.\1", text)
        text = re.sub(rf"\b{name}\.pack\(\)", rf"{name}.toLong()", text)
    for name in set(re.findall(r"\bCompoundTag (\w+)\b", text)):
        text = re.sub(rf"\b{name}\.({NBT_READS})\(", rf"dev.groundburst.port.Nbt.\1({name}, ", text)
    for name in set(re.findall(r"\bOptional<CompoundTag> (\w+)\b", text)):
        text = re.sub(rf"\b{name}\.get\(\)\.({NBT_READS})\(", rf"dev.groundburst.port.Nbt.\1({name}.get(), ", text)
    for name in set(re.findall(r"\b(?:PoseStack\.)?Pose (\w+)\b", text)):
        text = re.sub(rf"\b{name}\.translate\(", rf"{name}.pose().translate(", text)
        text = re.sub(rf"\b{name}\.(?:rotate|mulPose)\(", rf"dev.groundburst.port.render.Poses.rotate({name}, ", text)
        text = re.sub(rf"\b{name}\.scale\(", rf"dev.groundburst.port.render.Poses.scale({name}, ", text)
        text = re.sub(rf"\b{name}\.set\(", rf"dev.groundburst.port.render.Poses.set({name}, ", text)
    for name in set(re.findall(r"\bIngredient (\w+)\b", text)):
        text = re.sub(rf"\b{name}\.items\(\)", rf"dev.groundburst.port.Ingredients.items({name})", text)
    return text


def screen_size(text: str) -> str:
    """26.x's container screens are given their panel's size; 1.21.1's set it after."""
    if "extends AbstractContainerScreen" not in text:
        return text
    return re.sub(r"\bsuper\((\w+), (\w+), (\w+), ((?:[^,()]|\([^()]*\))+), ((?:[^,()]|\([^()]*\))+)\);",
                  r"super(\1, \2, \3);\n        this.imageWidth = \4;\n        this.imageHeight = \5;", text)


FUNCTIONS = [block_codecs, pre_remove, saved_data, declared_types, screen_size, wide_composites, overlay_messages, coloured_blocks,
             call_fixes]


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
    for pattern, repl in SIGNATURES:
        text = pattern.sub(repl, text)
    for signature, fn in BODIES:
        text = method_bodies(text, signature, fn)
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


FILE_FIXES: dict[str, list] = {
    "src/main/java/dev/groundburst/GroundBurstClient.java": [
        after("container.registerConfig(ModConfig.Type.CLIENT, GBClientConfig.SPEC);",
              "\n        dev.groundburst.port.render.RenderBridge.register();   // 26.x's render events, posted from 1.21.1's"
              "\n        dev.groundburst.port.net.RegisterClientPayloadHandlersEvent.post(modBus);   // and its payload handlers'"
              "\n        dev.groundburst.port.render.ItemModels.register(modBus);   // what 1.21.1's item models need from code"
              "\n        dev.groundburst.port.render.FreeRotationModels.register(modBus);   // models turned by any angle"
              "\n        dev.groundburst.port.PortCheck.register();   // gradlew runPortCheck"),
    ],
    # Items and blocks: their properties made when they are registered, as 26.x's registers make them (they may name
    # other registered things, an effect, a block).
    "src/main/java/dev/groundburst/registry/GBItems.java": [
        swap("ITEMS.registerItem(name, factory, properties);", "ITEMS.register(name, () -> factory.apply(properties.apply(new Item.Properties())));"),
        swap("ITEMS.registerSimpleBlockItem(block, properties);",
             "ITEMS.register(block.getId().getPath(), () -> new BlockItem(block.get(), properties.apply(new Item.Properties())));"),
        swap("(DeferredItem<BlockItem>) (DeferredItem<?>) ITEMS.registerItem(block.getId().getPath(),\n"
             "                props -> factory.apply(block.get(), props), Item.Properties::useBlockDescriptionPrefix);",
             "(DeferredItem<BlockItem>) (DeferredItem<?>) ITEMS.register(block.getId().getPath(),\n"
             "                () -> factory.apply(block.get(), new Item.Properties()));"),
    ],
    "src/main/java/dev/groundburst/blast/GBBlast.java": [
        swap("BlastManager.of(player.level())", "BlastManager.of((net.minecraft.server.level.ServerLevel) player.level())"),
    ],
    "src/main/java/dev/groundburst/blast/BlastChunks.java": [
        swap('tag.get().getStringOr("Status", "")', 'dev.groundburst.port.Nbt.getStringOr(tag.get(), "Status", "")'),
    ],
    "src/main/java/dev/groundburst/registry/GBBlocks.java": [
        swap("BLOCKS.registerBlock(name, factory, properties);", "BLOCKS.register(name, () -> factory.apply(properties.apply(BlockBehaviour.Properties.of())));"),
    ],
    # Fuel: 1.21.1 asks the stack its burn time (26.x reads the item's cooking-fuel component through a loot context).
    "src/main/java/dev/groundburst/content/power/CombustionGeneratorBlockEntity.java": [
        swap("return stack.has(DataComponents.COOKING_FUEL);",
             "return stack.getBurnTime(net.minecraft.world.item.crafting.RecipeType.SMELTING) > 0;"),
        swap("int duration = ResolvableInt.getFromItem(fuel, DataComponents.COOKING_FUEL, CookingFuel::burnTime, lootContext(level, fuel), 0);",
             "int duration = fuel.getBurnTime(net.minecraft.world.item.crafting.RecipeType.SMELTING);"),
        swap("ItemStackTemplate remainder = fuel.getCraftingRemainder();",
             "ItemStack remainderStack = fuel.getCraftingRemainingItem();\n"
             "        ItemStackTemplate remainder = remainderStack.isEmpty() ? null : ItemStackTemplate.fromNonEmptyStack(remainderStack);"),
        drop_method("private LootContext lootContext(ServerLevel level, ItemStack fuel)"),
    ],
    # A block's tint: 1.21.1's block colours answer it straight (26.x's give a tint source).
    "src/main/java/dev/groundburst/client/fx/Debris.java": [
        swap("BlockTintSource source = Minecraft.getInstance().getBlockColors().getTintSource(state, 0);\n"
             "        return source == null ? 0xFFFFFF : source.colorAsTerrainParticle(state, level, at) & 0xFFFFFF;",
             "int color = Minecraft.getInstance().getBlockColors().getColor(state, level, at, 0);\n"
             "        return color == -1 ? 0xFFFFFF : color & 0xFFFFFF;"),
    ],
    # JEI 19's string subtypes: none is "", not null.
    "src/main/java/dev/groundburst/compat/jei/GBJeiPlugin.java": [
        swap("return spec != null ? spec.look() : null;", "return spec != null ? spec.look() : \"\";"),
    ],
    # Standalone models: 1.21.1 registers them by location and hands back a baked model.
    "src/main/java/dev/groundburst/client/render/FissionSections.java": [
        swap("        private final StandaloneModelKey<BlockStateModelPart> key = new StandaloneModelKey<>(() -> GroundBurst.MODID + \":\" + this.id);\n", ""),
        swap("return Minecraft.getInstance().getModelManager().getStandaloneModel(this.key);",
             "var models = Minecraft.getInstance().getModelManager();\n"
             "            var baked = models.getModel(net.minecraft.client.resources.model.ModelResourceLocation.standalone(model()));\n"
             "            return baked == models.getMissingModel() ? null : new BlockStateModelPart(baked, null, 0);"),
        swap("public static void register(ModelEvent.RegisterStandalone event) {", "public static void register(ModelEvent.RegisterAdditional event) {"),
        swap("event.register(s.key, SimpleUnbakedStandaloneModel.simpleModelWrapper(s.model()));",
             "event.register(net.minecraft.client.resources.model.ModelResourceLocation.standalone(s.model()));"),
        swap("import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;\n", ""),
        swap("import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;\n", ""),
    ],
    # The dosimeter's dose: an item property 1.21.1's model overrides read (dev.groundburst.port.render.ItemModels).
    "src/main/java/dev/groundburst/client/radiation/RadiationClient.java": [
        swap('modBus.addListener((RegisterRangeSelectItemModelPropertyEvent event) -> event.register(GroundBurst.id("dose"), Dose.CODEC));',
             'dev.groundburst.port.render.ItemModels.property(GroundBurst.id("dose"), Dose::get);'),
        replace_method("public record Dose() implements RangeSelectItemModelProperty",
                       "public record Dose() {\n"
                       "        public static float get(ItemStack stack, @Nullable ClientLevel level, net.minecraft.world.entity.@Nullable LivingEntity owner, int seed) {\n"
                       "            Player local = Minecraft.getInstance().player;\n"
                       "            if (local == null || owner != null && owner != local) {\n"
                       "                return 0;\n"
                       "            }\n"
                       "            return state(local).dose();\n"
                       "        }\n"
                       "    }"),
    ],
    # Block tints: 1.21.1's block colour handlers (26.x's tint sources).
    "src/main/java/dev/groundburst/client/fallout/FalloutClient.java": [
        swap("modBus.addListener((RegisterColorHandlersEvent.BlockTintSources event) -> {", "modBus.addListener((RegisterColorHandlersEvent.Block event) -> {"),
        swap("event.register(List.of(dead(DEAD_GRASS, BiomeColors::getAverageGrassColor)), ", "event.register(dead(DEAD_GRASS, BiomeColors::getAverageGrassColor), "),
        swap("event.register(List.of(dead(FROSTED_GRASS, BiomeColors::getAverageGrassColor)), ", "event.register(dead(FROSTED_GRASS, BiomeColors::getAverageGrassColor), "),
        swap("event.register(List.of(net.minecraft.client.color.block.BlockTintSources.constant(FROZEN_PLANT)), ",
             "event.register((state, level, pos, tintIndex) -> FROZEN_PLANT, "),
        replace_method("static BlockTintSource dead(int colour, ToIntBiFunction<BlockAndTintGetter, BlockPos> biome)",
                       "static BlockColor dead(int colour, ToIntBiFunction<BlockAndTintGetter, BlockPos> biome) {\n"
                       "        return (state, level, pos, tintIndex) -> level == null || pos == null ? colour\n"
                       "                : ARGB.srgbLerp(BIOME_SHARE, colour, 0xFF000000 | biome.applyAsInt(level, pos));\n"
                       "    }"),
    ],
    "src/main/java/dev/groundburst/registry/GBCreativeTabs.java": [
        swap("previous = holder.key();", "previous = holder.getKey();"),
    ],
    "src/main/java/dev/groundburst/content/construction/BarbedWireBlock.java": [
        swap("entity.isClientAuthoritative() ? entity.getKnownMovement() : entity.oldPosition().subtract(entity.position())",
             "new Vec3(entity.getX() - entity.xOld, entity.getY() - entity.yOld, entity.getZ() - entity.zOld)"),
    ],
    "src/main/java/dev/groundburst/content/ordnance/GrenadeItem.java": [
        swap("ThrownExplosive thrown = Projectile.spawnProjectileFromRotation((l, owner, item) -> new ThrownExplosive(l, owner, item), server, stack,\n"
             "                    player, 0f, this.type.speed, 1.0f);",
             "ThrownExplosive thrown = new ThrownExplosive(server, player, stack.copyWithCount(1));\n"
             "            thrown.shootFromRotation(player, player.getXRot(), player.getYRot(), 0f, this.type.speed, 1.0f);\n"
             "            server.addFreshEntity(thrown);"),
    ],
    # The play bench (its client half is this branch's own): 1.21.1's game rule for the time of day, and its teleport.
    "src/main/java/dev/groundburst/blast/PlayBench.java": [
        swap('"gamerule advance_time false"', '"gamerule doDaylightCycle false"'),
        swap("java.util.Set.of(), 135, 20, true)", "135, 20)"),
    ],
    "src/main/java/dev/groundburst/GroundBurst.java": [
        after("modBus.addListener(GBBlockEntities::registerCapabilities);",
              "\n        dev.groundburst.port.transfer.Capabilities.register(modBus);   // the bridge to 1.21.1's handlers"),
        after("GBItems.ITEMS.register(modBus);",
              "\n        dev.groundburst.content.gear.GBArmor.register(modBus);   // 1.21.1's armour materials are a registry"),
    ],
}


# --- resources ------------------------------------------------------------------------------------------------------------
def port_resource(path: str, data: bytes) -> tuple[str, bytes] | None:
    """Where main's resource goes here and what it becomes (None: it has no place here)."""
    # GeckoLib 4 reads geo/ and animations/ (GeckoLib 5: geckolib/models/ and geckolib/animations/).
    path = re.sub(r"^(src/main/resources/assets/\w+)/geckolib/models/", r"\1/geo/", path)
    path = re.sub(r"^(src/main/resources/assets/\w+)/geckolib/animations/", r"\1/animations/", path)
    # Armour: 1.21.1 draws a suit from textures/models/armor/<name>_layer_1 (body) and _layer_2 (legs).
    path = re.sub(r"^(src/main/resources/assets/\w+)/textures/entity/equipment/humanoid/(\w+)\.png$", r"\1/textures/models/armor/\2_layer_1.png", path)
    path = re.sub(r"^(src/main/resources/assets/\w+)/textures/entity/equipment/humanoid_leggings/(\w+)\.png$",
                  r"\1/textures/models/armor/\2_layer_2.png", path)
    # 1.21.1 takes only lower-case names in a pack (the sound credits).
    path = re.sub(r"^(src/main/resources/assets/\w+/sounds)/CREDITS\.txt$", r"\1/credits.txt", path)
    # 26.x's configured features: 1.21.1's worldgen/configured_feature.
    path = re.sub(r"^(src/(?:main|generated)/resources/data/\w+/worldgen)/feature/", r"\1/configured_feature/", path)
    if re.match(r"^src/(main|generated)/resources/data/\w+/recipe/.*\.json$", path):
        data = port_recipe(data)
        if data is None:
            return None                              # it needs one of 26.x's items
    if re.match(r"^src/(main|generated)/resources/data/\w+/advancement/.*\.json$", path):
        data = port_advancement(data)
    # 26.x's item model definitions: made into 1.21.1's item models by item_models (below).
    if re.match(r"^src/(main|generated)/resources/assets/\w+/items/.*\.json$", path):
        return None
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
    return path, data


VANILLA_ANGLES = {0, 22.5, -22.5, 45, -45}


# Vanilla parents whose blocks 1.21.1 draws in a see-through layer by name (not by their models): a model of the mod's
# on one of them names the layer itself.
PARENT_LAYERS = {"minecraft:block/grass_block": "minecraft:cutout_mipped", "minecraft:block/leaves": "minecraft:cutout_mipped",
                 "minecraft:block/cross": "minecraft:cutout", "minecraft:block/tinted_cross": "minecraft:cutout"}


def port_model(data: bytes) -> bytes:
    """A block or item model whose parts turn by angles 1.21.1 will not read (it takes multiples of 22.5 up to 45): its
    parts moved where only this branch's loader reads them (dev.groundburst.port.render.FreeRotationModels). One on a
    see-through vanilla parent (grass, leaves, a cross) names its layer (PARENT_LAYERS)."""
    m = json.loads(data)
    if m.get("parent") in PARENT_LAYERS and "render_type" not in m:
        m["render_type"] = PARENT_LAYERS[m["parent"]]
        data = (json.dumps(m, indent=2) + "\n").encode("utf-8")
    # 26.x's bar templates are 1.21.1's iron bars' models (the same parts and texture names).
    if isinstance(m.get("parent"), str) and m["parent"].startswith("minecraft:block/template_bars_"):
        m["parent"] = m["parent"].replace("minecraft:block/template_bars_", "minecraft:block/iron_bars_")
        data = (json.dumps(m, indent=2) + "\n").encode("utf-8")
    elements = m.get("elements")
    if not isinstance(elements, list) or "loader" in m:
        return data
    if all(float(e.get("rotation", {}).get("angle", 0)) in VANILLA_ANGLES for e in elements):
        return data
    m["loader"] = "groundburst:free_rotation"
    m["groundburst:elements"] = m.pop("elements")
    return (json.dumps(m, indent=2) + "\n").encode("utf-8")


def vanilla_ids(java: str, pattern: str) -> set[str]:
    """Ids 1.21.1 registers, read from its sources (build/mcsrc): {pattern} captures each name in {java}."""
    src = ROOT / "build" / "mcsrc" / java
    return {"minecraft:" + n for n in re.findall(pattern, src.read_text(encoding="utf-8"))} if src.exists() else set()


ENTITY_TYPES = None


def port_biome(data: bytes) -> bytes:
    """26.x keeps a biome's sky, fog, music, ambient particles and mob spawns as environment attributes; 1.21.1 as its
    effects and spawners (a mob 1.21.1 lacks is left out)."""
    global ENTITY_TYPES
    if ENTITY_TYPES is None:
        ENTITY_TYPES = vanilla_ids("net/minecraft/world/entity/EntityType.java", r'register\(\s*"([a-z0-9_]+)"')
    b = json.loads(data)
    attrs = b.pop("attributes", {})
    effects = b.get("effects", {})

    def colour(v):
        return int(v.lstrip("#"), 16) if isinstance(v, str) else v

    for key in list(effects):
        effects[key] = colour(effects[key])
    effects.pop("dry_foliage_color", None)                    # 26.x's
    for attr, key in (("minecraft:visual/sky_color", "sky_color"), ("minecraft:visual/fog_color", "fog_color"),
                      ("minecraft:visual/water_fog_color", "water_fog_color")):
        if attr in attrs:
            effects[key] = colour(attrs[attr])
    effects.setdefault("sky_color", 7907327)
    effects.setdefault("fog_color", 12638463)
    effects.setdefault("water_color", 4159204)
    effects.setdefault("water_fog_color", 329011)
    particles = attrs.get("minecraft:visual/ambient_particles")
    if particles:
        effects["particle"] = {"options": particles[0]["particle"], "probability": particles[0]["probability"]}
    if "minecraft:audio/background_music" in attrs and attrs.get("minecraft:audio/music_volume", 1) == 0:
        # No music: 1.21.1 has no silence, so the music is an empty sound that waits long between plays.
        effects["music"] = {"sound": "minecraft:intentionally_empty", "min_delay": 12000, "max_delay": 24000, "replace_current_music": True}
    b["effects"] = effects
    spawns = attrs.get("minecraft:gameplay/natural_mob_spawns", {}).get("argument", {})
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
    if b.get("carvers") == []:
        b["carvers"] = {}
    return (json.dumps(b, indent=2) + "\n").encode("utf-8")


# 26.x's named predicates the mod's loot tables use, as 1.21.1 writes them out.
NAMED_CONDITIONS = {
    "minecraft:tool/can_silk_touch": {"condition": "minecraft:match_tool", "predicate": {
        "predicates": {"minecraft:enchantments": [{"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}},
    "minecraft:tool/can_shear": {"condition": "minecraft:match_tool", "predicate": {"items": "minecraft:shears"}},
}


def loot_condition(c):
    """A 26.x loot condition (a named predicate, or {"type", ...}) as 1.21.1 writes one ({"condition", ...})."""
    if isinstance(c, str):
        if c in NAMED_CONDITIONS:
            return json.loads(json.dumps(NAMED_CONDITIONS[c]))
        return {"condition": "minecraft:reference", "name": c}
    if not isinstance(c, dict) or "condition" in c:
        return c
    c = dict(c)
    kind = c.pop("type")
    out = {"condition": kind}
    if kind == "minecraft:match_block":                      # 1.21.1's block state property: one block
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
    """A 26.x loot function ({"type", ...}) as 1.21.1 writes one ({"function", ...})."""
    if not isinstance(f, dict) or "function" in f:
        return port_loot_object(f)
    f = dict(f)
    out = {"function": f.pop("type")}
    out.update(f)
    return port_loot_object(out)


def port_loot_object(o):
    """26.x's single "condition" and its "modifier" list, wherever they sit (pools, entries, functions), as 1.21.1's
    "conditions" and "functions"."""
    if isinstance(o, list):
        return [port_loot_object(x) for x in o]
    if not isinstance(o, dict):
        return o
    out = {}
    for k, v in o.items():
        # A pool's, an entry's or a function's own condition (a condition's "condition" is its 1.21.1 kind: kept).
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
    """A block state as 1.21.1 writes one ({"Name", "Properties"}), from 26.x's ("name", "name[a=b]" or {"id", ...})."""
    if isinstance(v, str):
        m = re.fullmatch(r"([^\[]+)(?:\[(.*)\])?", v)
        props = dict(p.split("=", 1) for p in m.group(2).split(",")) if m.group(2) else {}
        return {"Name": m.group(1), **({"Properties": props} if props else {})}
    if isinstance(v, dict) and "id" in v and "Name" not in v:
        props = v.get("properties", v.get("Properties", {}))
        return {"Name": v["id"], **({"Properties": props} if props else {})}
    return v


def state_provider(v):
    """A block state provider as 1.21.1 writes one (26.x writes a plain state for the simple provider)."""
    if isinstance(v, dict) and "type" in v:
        return v
    return {"type": "minecraft:simple_state_provider", "state": block_state(v)}


def port_feature(data: bytes) -> bytes:
    """26.x writes a configured feature's settings beside its type; 1.21.1 under "config", with full block states."""
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
    """A tag's entries from outside the mod (vanilla's, other mods' and the common tags) made optional: 26.x has blocks,
    items and tags 1.21.1 lacks, and one missing required entry fails the whole tag."""
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


# --- item models ----------------------------------------------------------------------------------------------------------
VANILLA_JAR = ROOT / "build" / "moddev" / "artifacts" / "neoforge-21.1.265-client-extra-aka-minecraft-resources.jar"
LOOK_RANGE = 16_777_213              # dev.groundburst.port.Looks: below 2^24, so every look's number is exact as a float


def java_hash(s: str) -> int:
    h = 0
    for ch in s.encode("utf-16-be").decode("utf-16-be"):
        for unit in ([ord(ch)] if ord(ch) < 0x10000 else [0xD800 + ((ord(ch) - 0x10000) >> 10), 0xDC00 + ((ord(ch) - 0x10000) & 0x3FF)]):
            h = (31 * h + unit) & 0xFFFFFFFF
    return h - (1 << 32) if h >= 1 << 31 else h


def look_id(look: str) -> int:
    """dev.groundburst.port.Looks.id: a missile look's custom model data number."""
    return java_hash(look) % LOOK_RANGE + 1


def model_json(ref: str) -> dict | None:
    ns, path = ref.split(":", 1) if ":" in ref else ("minecraft", ref)
    try:
        if ns == "minecraft":
            import zipfile
            with zipfile.ZipFile(VANILLA_JAR) as jar:
                return json.loads(jar.read(f"assets/minecraft/models/{path}.json"))
        return json.loads(read_main(f"src/main/resources/assets/{ns}/models/{path}.json"))
    except (KeyError, FileNotFoundError):
        return None


def resolved(ref: str, key: str):
    """A model's {key} (textures, display...), its own or the nearest parent's."""
    for _ in range(16):
        m = model_json(ref)
        if m is None:
            return None
        if key in m:
            return m[key]
        if "parent" not in m:
            return None
        ref = m["parent"]
    return None


def item_models(files: list[str]) -> dict[str, bytes]:
    """1.21.1's item models (models/item/<name>.json), from main's item model definitions (items/<name>.json):
      - a plain model: that model (or the item's own model file, when it names it);
      - GeckoLib's special model: builtin/entity, keeping the base model's textures and display;
      - a select on the display context: NeoForge's separate transforms;
      - a select on custom model data (the missile's looks): overrides on dev.groundburst.port.Looks's numbers;
      - a range dispatch: overrides on the property's thresholds; a condition on using the item: an override on
        groundburst:using_item.
    What the models need from code (constant tints, the properties and their items) goes into ItemModelTable.java."""
    out: dict[str, bytes] = {}
    tints: dict[str, list[int]] = {}
    props: dict[str, list[str]] = {}
    containers: list[str] = []
    present = set(files)
    for path in files:
        m = re.fullmatch(r"src/main/resources/assets/(\w+)/items/(.+)\.json", path)
        if not m:
            continue
        ns, name = m.groups()
        item_id, own = f"{ns}:{name}", f"{ns}:item/{name}"
        dest = f"src/main/resources/assets/{ns}/models/item/{name}.json"
        root = json.loads(read_main(path))["model"]
        kind = root["type"].removeprefix("minecraft:")

        def ref(model: dict) -> str:
            assert model["type"].removeprefix("minecraft:") == "model", f"{path}: a {model['type']} inside a {kind}"
            r = model["model"]
            if r == own:                             # the item's own model file is about to be the item model: keep it apart
                kept = f"src/main/resources/assets/{ns}/models/item/{name}_own.json"
                out[kept] = port_model(read_main(dest))
                return f"{ns}:item/{name}_own"
            return r

        if kind == "model":
            if root.get("tints"):
                tints[item_id] = [(t["value"] | 0xFF000000) if t["type"] == "minecraft:constant" else -1 for t in root["tints"]]
            if root["model"] == own and dest in present:
                continue                             # main's own model is the item's
            model = {"parent": root["model"]}
        elif kind == "neoforge:fluid_container":
            # NeoForge 21.1's bucket model loader takes the same settings.
            model = {"parent": "neoforge:item/bucket", "loader": "neoforge:fluid_container"}
            model.update({k: v for k, v in root.items() if k != "type"})
            containers.append(item_id)                   # its fluid tinted from code in 1.21.1 (ItemModels)
        elif kind == "special":
            model = {"parent": "builtin/entity"}
            for key in ("textures", "display", "gui_light"):
                v = resolved(root["base"], key)
                if v is not None:
                    model[key] = v
        elif kind == "select" and root["property"] == "minecraft:display_context":
            perspectives = {}
            for case in root["cases"]:
                for when in case["when"] if isinstance(case["when"], list) else [case["when"]]:
                    perspectives[when] = {"parent": ref(case["model"])}
            model = {"loader": "neoforge:separate_transforms", "base": {"parent": ref(root["fallback"])}, "perspectives": perspectives}
        elif kind == "select" and root["property"] == "minecraft:custom_model_data":
            looks = [(look_id(w), ref(case["model"]), w) for case in root["cases"]
                     for w in (case["when"] if isinstance(case["when"], list) else [case["when"]])]
            ids = [i for i, _, _ in looks]
            assert len(ids) == len(set(ids)), f"{path}: two looks share a number (dev.groundburst.port.Looks)"
            model = {"parent": ref(root["fallback"]),
                     "overrides": [{"predicate": {"custom_model_data": i}, "model": r} for i, r, _ in sorted(looks)]}
        elif kind == "range_dispatch":
            scale = root.get("scale", 1)
            props.setdefault(root["property"], []).append(item_id)
            entries = sorted((e["threshold"] / scale, ref(e["model"])) for e in root["entries"])
            model = {"parent": ref(root["fallback"]),
                     "overrides": [{"predicate": {root["property"]: t}, "model": r} for t, r in entries]}
        elif kind == "condition" and root["property"] == "minecraft:using_item":
            props.setdefault("groundburst:using_item", []).append(item_id)
            model = {"parent": ref(root["on_false"]),
                     "overrides": [{"predicate": {"groundburst:using_item": 1}, "model": ref(root["on_true"])}]}
        else:
            raise SystemExit(f"port.py: {path}: no 1.21.1 item model for a {kind} on {root.get('property')}")
        out[dest] = (json.dumps(model, indent=2) + "\n").encode("utf-8")
    out["src/main/java/dev/groundburst/port/render/ItemModelTable.java"] = item_model_table(tints, props, containers).encode("utf-8")
    return out


def item_model_table(tints: dict[str, list[int]], props: dict[str, list[str]], containers: list[str]) -> str:
    def hexes(values: list[int]) -> str:
        return ", ".join(f"0x{v & 0xFFFFFFFF:08X}" for v in values)
    tint_rows = ",\n".join(f'            Map.entry("{k}", new int[] {{{hexes(v)}}})' for k, v in sorted(tints.items()))
    prop_rows = ",\n".join(f'            Map.entry("{k}", List.of({", ".join(chr(34) + i + chr(34) for i in v)}))' for k, v in sorted(props.items()))
    container_rows = ",\n".join(f'            "{i}"' for i in sorted(containers))
    return ("package dev.groundburst.port.render;\n\nimport java.util.List;\nimport java.util.Map;\n\n"
            "/**\n * Written by tools/backport/port.py from main's item model definitions (assets/&lt;ns&gt;/items): what this branch's\n"
            " * item models need from code ({@link ItemModels}). Not edited by hand.\n */\n"
            "final class ItemModelTable {\n    private ItemModelTable() {}\n\n"
            "    /** Items tinted a constant colour, ARGB per tint index. */\n"
            f"    static final Map<String, int[]> TINTS = Map.ofEntries(\n{tint_rows});\n\n"
            "    /** The item properties the models' overrides read, and the items that have them. */\n"
            f"    static final Map<String, List<String>> PROPERTIES = Map.ofEntries(\n{prop_rows});\n\n"
            "    /** Fluid containers (buckets): their fluid tinted as the fluid is. */\n"
            f"    static final List<String> FLUID_CONTAINERS = List.of(\n{container_rows});\n}}\n")


def ingredient(v):
    """A 26.x ingredient ("minecraft:stone", "#c:ingots/iron", or a list of them) as 1.21.1 writes it."""
    if isinstance(v, str):
        return {"tag": v[1:]} if v.startswith("#") else {"item": v}
    if isinstance(v, list):
        return [ingredient(x) for x in v]
    return v


def fluid_ingredient(v):
    """A 26.x fluid ingredient ("minecraft:water", "#c:argon", or a list of them) as NeoForge 21.1 writes it."""
    if isinstance(v, str):
        return {"tag": v[1:]} if v.startswith("#") else {"fluid": v}
    if isinstance(v, list):
        return [fluid_ingredient(x) for x in v]
    return v


def sized(entries, convert):
    return [{**e, "ingredient": convert(e["ingredient"])} if isinstance(e, dict) and "ingredient" in e else e for e in entries]


# 26.x's names for things 1.21.1 has under another.
ITEM_ALIASES = {"minecraft:iron_chain": "minecraft:chain"}
VANILLA_ITEMS = None


def vanilla_items() -> set[str]:
    """1.21.1's items (and blocks, a few more than have items), read from its sources: enough to tell 26.x's new ones."""
    global VANILLA_ITEMS
    if VANILLA_ITEMS is None:
        blocks_src = (ROOT / "build" / "mcsrc" / "net/minecraft/world/level/block/Blocks.java").read_text(encoding="utf-8")
        items_src = (ROOT / "build" / "mcsrc" / "net/minecraft/world/item/Items.java").read_text(encoding="utf-8")
        blocks = dict(re.findall(r'public static final Block (\w+) = register\(\s*"([a-z0-9_]+)"', blocks_src))
        VANILLA_ITEMS = {"minecraft:" + b for b in blocks.values()} | {"minecraft:" + i for i in re.findall(r'registerItem\(\s*"([a-z0-9_]+)"', items_src)}
    return VANILLA_ITEMS


def missing_items(value) -> list[str]:
    """The vanilla items named in a recipe that 1.21.1 lacks."""
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
    return out


def port_advancement(data: bytes) -> bytes:
    """A recipe's unlock: 26.x names its recipes, 1.21.1 one recipe."""
    a = json.loads(data)
    for criterion in a.get("criteria", {}).values():
        c = criterion.get("conditions", {})
        if criterion.get("trigger") == "minecraft:recipe_unlocked" and "recipes" in c and "recipe" not in c:
            r = c.pop("recipes")
            c["recipe"] = r[0] if isinstance(r, list) else r
    return (json.dumps(a, indent=2) + "\n").encode("utf-8")


def port_recipe(data: bytes) -> bytes | None:
    for old, new in ITEM_ALIASES.items():
        data = data.replace(f'"{old}"'.encode(), f'"{new}"'.encode())
    r = json.loads(data)
    if "key" in r:
        r["key"] = {k: ingredient(v) for k, v in r["key"].items()}
    if "ingredients" in r:
        r["ingredients"] = [ingredient(v) for v in r["ingredients"]]
    for k in ("ingredient", "base", "addition", "template", "catalyst"):
        if k in r:
            r[k] = ingredient(r[k])
    # The machines' own: counted item inputs and fluid inputs.
    if isinstance(r.get("inputs"), list):
        r["inputs"] = sized(r["inputs"], ingredient)
    if isinstance(r.get("fluids"), list):
        r["fluids"] = sized(r["fluids"], fluid_ingredient)
    if missing_items(r):
        return None
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
    for dest, data in item_models(main_files()).items():   # every item model again: they come from several of main's files
        (ROOT / dest).parent.mkdir(parents=True, exist_ok=True)
        (ROOT / dest).write_bytes(checkout_eol(dest, data))
    (HERE / "base.txt").write_text(f"{new}   main's commit this branch is brought up to (docs/BACKPORT.md)\n", encoding="utf-8")
    print(f"{len(changes)} of main's files changed since {old[:8]}; now at {new[:8]}")
    for c in conflicts:
        print("  conflict to resolve by hand:", c)


def main() -> None:
    args = sys.argv[1:]
    if args and args[0] == "--show":
        out = port_file(args[1])
        sys.stdout.write("(dropped)\n" if out is None else out[1].decode("utf-8"))
        return
    if args and args[0] == "--sync":
        sync(args[1] if len(args) > 1 else "main")
        return
    files = args or main_files()
    written = kept = dropped = 0
    derived = item_models(main_files())
    for dest, data in derived.items():
        target = ROOT / dest
        target.parent.mkdir(parents=True, exist_ok=True)
        data = checkout_eol(dest, data)
        if not target.exists() or target.read_bytes() != data:
            target.write_bytes(data)
            written += 1
    for path in files:
        if path in derived:
            continue
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
            (ROOT / path).unlink()                    # moved here (GeckoLib's folders)
        if not target.exists() or target.read_bytes() != data:
            target.write_bytes(data)
            written += 1
    print(f"{len(files)} of main's files: {written} written, {kept} kept (edited by hand), {dropped} left out")


if __name__ == "__main__":
    main()
