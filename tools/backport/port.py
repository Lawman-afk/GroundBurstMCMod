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
    r"^\s*GBDataGen\.register\(modBus\);\s*$",
    r"^\s*GBGameTests\.register\(modBus\);\s*$",
    r"^import dev\.groundburst\.(datagen|gametest)\.\w+;\s*$",
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
    sub(r"(\bpublic ItemStack pickupBlock\(\s*(?:@[\w.]+\s+)*)" + QN + r"LivingEntity(\s+\w+)", r"\1net.minecraft.world.entity.player.Player\2"),
    sub(r"(\bpublic boolean emptyContents\(\s*(?:@[\w.]+\s+)*)" + QN + r"LivingEntity(\s+\w+)", r"\1net.minecraft.world.entity.player.Player\2"),
    sub(r"(\bprotected void onExplosionHit\(\s*" + param("BlockState") + C + r")" + param(QN + "ServerLevel") + r"(" + C + r")",
        r"\1net.minecraft.world.level.Level \3\4"),
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
    """26.x's useItemOn answers with an InteractionResult; 1.21.1's with an ItemInteractionResult."""
    method = re.sub(r"\bprotected InteractionResult useItemOn\(", "protected net.minecraft.world.ItemInteractionResult useItemOn(", method, count=1)
    method = re.sub(r"\bInteractionResult\.TRY_WITH_EMPTY_HAND\b", "net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION", method)
    method = re.sub(r"\bInteractionResult\.PASS\b", "net.minecraft.world.ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION", method)
    method = re.sub(r"\bInteractionResult\.(SUCCESS|CONSUME|FAIL)\b", r"net.minecraft.world.ItemInteractionResult.\1", method)
    return method


def release_void(method: str) -> str:
    return re.sub(r"\breturn (?:true|false);", "return;", method)


BODIES = [
    (r"\bpublic net\.minecraft\.world\.InteractionResultHolder<net\.minecraft\.world\.item\.ItemStack> use\([^)]*\)\s*\{", use_holder),
    (r"\bprotected InteractionResult useItemOn\([^)]*\)\s*\{", item_result),
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
    """26.x tells a block entity just before its block is removed (preRemoveSideEffects); 1.21.1 only removes it. The
    topmost of the mod's classes to have it calls it from setRemoved, unless the chunk is being unloaded."""
    if "preRemoveSideEffects" not in text or not re.search(r"\bextends BlockEntity\b", text):
        return text
    text = re.sub(r"@Override\s*\n(\s*public void preRemoveSideEffects\()", r"\1", text)
    text = re.sub(r"\n\s*super\.preRemoveSideEffects\([^;]*\);", "", text)
    hook = ("\n    private boolean unloading_;\n\n    @Override\n    public void onChunkUnloaded() {\n        super.onChunkUnloaded();\n"
            "        this.unloading_ = true;\n    }\n\n    @Override\n    public void setRemoved() {\n"
            "        if (!this.unloading_ && this.level != null && !this.level.isClientSide()) {\n"
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


FUNCTIONS = [block_codecs, pre_remove, saved_data]


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


FILE_FIXES: dict[str, list] = {
    "src/main/java/dev/groundburst/GroundBurstClient.java": [
        after("container.registerConfig(ModConfig.Type.CLIENT, GBClientConfig.SPEC);",
              "\n        dev.groundburst.port.render.RenderBridge.register();   // 26.x's render events, posted from 1.21.1's"),
    ],
    "src/main/java/dev/groundburst/GroundBurst.java": [
        after("modBus.addListener(GBBlockEntities::registerCapabilities);",
              "\n        dev.groundburst.port.transfer.Capabilities.register(modBus);   // the bridge to 1.21.1's handlers"),
    ],
}


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
        sys.stdout.write("(dropped)\n" if out is None else out[1].decode("utf-8"))
        return
    if args and args[0] == "--sync":
        sync(args[1] if len(args) > 1 else "main")
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
