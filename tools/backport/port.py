"""Turns the 1.21.1 build's sources (branch 1.21.1: Minecraft 1.21.1 on NeoForge 21.1) into this branch's (Minecraft 1.20.1
on Forge 47): docs/BACKPORT.md.

Reads every file of the 1.21.1 build at the commit in base.txt straight from git (so each run starts from that build's own
text) and writes its port here: the Java sources through IMPORTS, RULES and the functions after them, the resources through
their moves and fixes. The 1.21.1 build's files with no place here (DROP) are left out; files edited by hand here
(manual.txt) are kept as they are. This branch's own files (its stand-ins for 1.21.1's API in dev.groundburst.legacy, this
tool, docs/BACKPORT.md, the build files) are not the 1.21.1 build's and are never touched.

The 1.21.1 build is itself main's (Minecraft 26.3) ported by its own tool, so a change on main reaches this branch in two
steps: the 1.21.1 branch's `port.py --sync`, then this one's.

    tools\\.venv\\Scripts\\python.exe tools\\backport\\port.py              (everything)
    tools\\.venv\\Scripts\\python.exe tools\\backport\\port.py <path> ...   (just those of the 1.21.1 build's files)
    tools\\.venv\\Scripts\\python.exe tools\\backport\\port.py --show <path> (print one file's port)
    tools\\.venv\\Scripts\\python.exe tools\\backport\\port.py --sync <commit of the 1.21.1 branch>
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

# The 1.21.1 build's files with no place here: its own tool and notes, its build files (this branch has its own), its
# support for Sable (Create Aeronautics' ships, which have no 1.20.1 build) with the check built on it, and its test
# world (built on the ships too). Its Create support comes over (Create 6 is on 1.20.1), less the ships' scenes.
DROP = [re.compile(p) for p in (
    r"docs/BACKPORT\.md", r"tools/backport/.*",
    r"src/main/java/dev/groundburst/compat/(sable|range)/.*",
    r"src/main/java/dev/groundburst/compat/ShipCheck\.java",
    r"src/main/templates/META-INF/neoforge\.mods\.toml",
    # 1.21's block hit by an explosion (onExplosionHit): 1.20.1 has none (dev.groundburst.legacy.mixin.ExplosionHitMixin).
    r"src/main/java/dev/groundburst/mixin/BlockStateBaseMixin\.java",
    # The 1.21.1 build's test runs keeping the mouse (NeoForge's early window): this branch has its own, on Forge's
    # (dev.groundburst.legacy.client.DevRuns, legacy.mixin.DevWindowMixin and DevMouseMixin).
    r"src/main/java/dev/groundburst/port/DevRuns\.java",
    r"src/main/java/dev/groundburst/port/mixin/Dev(Window|Mouse)Mixin\.java",
)]
# Mixins of the 1.21.1 build left out of its mixin configs here (their files dropped above).
DROPPED_MIXINS = {"BlockStateBaseMixin", "DevWindowMixin", "DevMouseMixin"}
# This branch's own files (changed or written here by hand): never overwritten.
OWN = {"build.gradle", "gradle.properties", "settings.gradle", "src/main/templates/META-INF/mods.toml",
       "src/main/resources/pack.mcmeta",
       # The clouds' shader (the 1.21.1 build's own): 1.20.1's fog distance takes the model view.
       "src/main/resources/assets/groundburst/shaders/core/rendertype_fx_cloud.vsh",
       "src/main/resources/assets/groundburst/shaders/core/rendertype_fx_cloud.fsh",
       "src/main/resources/assets/groundburst/shaders/core/rendertype_fx_cloud.json"}


def git(*args: str) -> bytes:
    return subprocess.run(["git", "-C", str(ROOT), *args], check=True, capture_output=True).stdout


def source_files() -> list[str]:
    return git("ls-tree", "-r", "--name-only", BASE).decode("utf-8").splitlines()


_batch = None


def read_source(path: str) -> bytes:
    """A file of the 1.21.1 build's at BASE (through one long-running `git cat-file --batch`: thousands of files)."""
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


L = "dev.groundburst.legacy"                               # this branch's stand-ins for 1.21.1's API

# Imports: 1.21.1 class -> 1.20.1 class (or this branch's stand-in); None drops the import.
IMPORTS = {
    # NeoForge's event bus and loading: Forge's own (NeoForge forked from it).
    "net.neoforged.api.distmarker.Dist": "net.minecraftforge.api.distmarker.Dist",
    "net.neoforged.api.distmarker.OnlyIn": "net.minecraftforge.api.distmarker.OnlyIn",
    "net.neoforged.bus.api.Event": "net.minecraftforge.eventbus.api.Event",
    "net.neoforged.bus.api.EventPriority": "net.minecraftforge.eventbus.api.EventPriority",
    "net.neoforged.bus.api.IEventBus": "net.minecraftforge.eventbus.api.IEventBus",
    "net.neoforged.bus.api.SubscribeEvent": "net.minecraftforge.eventbus.api.SubscribeEvent",
    "net.neoforged.bus.api.ICancellableEvent": None,
    "net.neoforged.fml.ModContainer": "net.minecraftforge.fml.ModContainer",
    "net.neoforged.fml.ModList": "net.minecraftforge.fml.ModList",
    "net.neoforged.fml.common.EventBusSubscriber": "net.minecraftforge.fml.common.Mod.EventBusSubscriber",
    "net.neoforged.fml.common.Mod": "net.minecraftforge.fml.common.Mod",
    "net.neoforged.fml.config.ModConfig": "net.minecraftforge.fml.config.ModConfig",
    "net.neoforged.fml.event.IModBusEvent": "net.minecraftforge.fml.event.IModBusEvent",
    "net.neoforged.fml.event.lifecycle.FMLClientSetupEvent": "net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent",
    "net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent": "net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent",
    "net.neoforged.fml.loading.FMLPaths": "net.minecraftforge.fml.loading.FMLPaths",
    "net.neoforged.fml.loading.FMLEnvironment": "net.minecraftforge.fml.loading.FMLEnvironment",
    "net.neoforged.neoforge.common.NeoForge": "net.minecraftforge.common.MinecraftForge",
    "net.neoforged.neoforge.common.ModConfigSpec": "net.minecraftforge.common.ForgeConfigSpec",
    "net.neoforged.neoforge.common.SoundActions": "net.minecraftforge.common.SoundActions",
    "net.neoforged.neoforge.common.Tags": "net.minecraftforge.common.Tags",
    "net.neoforged.neoforge.common.util.BlockSnapshot": "net.minecraftforge.common.util.BlockSnapshot",
    "net.neoforged.neoforge.common.world.PieceBeardifierModifier": "net.minecraftforge.common.world.PieceBeardifierModifier",
    "net.neoforged.neoforge.common.extensions.IMenuTypeExtension": "net.minecraftforge.common.extensions.IForgeMenuType",
    "net.neoforged.neoforge.client.ClientHooks": "net.minecraftforge.client.ForgeHooksClient",
    "net.neoforged.neoforge.client.settings.KeyConflictContext": "net.minecraftforge.client.settings.KeyConflictContext",
    "net.neoforged.neoforge.client.model.ExtraFaceData": "net.minecraftforge.client.model.ForgeFaceData",
    "net.neoforged.neoforge.client.model.IModelBuilder": "net.minecraftforge.client.model.IModelBuilder",
    "net.neoforged.neoforge.client.model.data.ModelData": "net.minecraftforge.client.model.data.ModelData",
    "net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext": "net.minecraftforge.client.model.geometry.IGeometryBakingContext",
    "net.neoforged.neoforge.client.model.geometry.IGeometryLoader": "net.minecraftforge.client.model.geometry.IGeometryLoader",
    "net.neoforged.neoforge.client.model.geometry.SimpleUnbakedGeometry": "net.minecraftforge.client.model.geometry.SimpleUnbakedGeometry",
    "net.neoforged.neoforge.client.model.geometry.UnbakedGeometryHelper": "net.minecraftforge.client.model.geometry.UnbakedGeometryHelper",
    "net.neoforged.neoforge.client.model.pipeline.QuadBakingVertexConsumer": "net.minecraftforge.client.model.pipeline.QuadBakingVertexConsumer",
    "net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions": "net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions",
    "net.neoforged.neoforge.client.extensions.common.IClientItemExtensions": "net.minecraftforge.client.extensions.common.IClientItemExtensions",
    "net.neoforged.neoforge.client.extensions.common.IClientBlockExtensions": "net.minecraftforge.client.extensions.common.IClientBlockExtensions",
    # Client events Forge 1.20.1 has under the same names.
    "net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent": "net.minecraftforge.client.event.ClientPlayerNetworkEvent",
    "net.neoforged.neoforge.client.event.ComputeFovModifierEvent": "net.minecraftforge.client.event.ComputeFovModifierEvent",
    "net.neoforged.neoforge.client.event.EntityRenderersEvent": "net.minecraftforge.client.event.EntityRenderersEvent",
    "net.neoforged.neoforge.client.event.InputEvent": "net.minecraftforge.client.event.InputEvent",
    "net.neoforged.neoforge.client.event.ModelEvent": "net.minecraftforge.client.event.ModelEvent",
    "net.neoforged.neoforge.client.event.RegisterColorHandlersEvent": "net.minecraftforge.client.event.RegisterColorHandlersEvent",
    "net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent": "net.minecraftforge.client.event.RegisterKeyMappingsEvent",
    "net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent": "net.minecraftforge.client.event.RegisterParticleProvidersEvent",
    "net.neoforged.neoforge.client.event.RegisterShadersEvent": "net.minecraftforge.client.event.RegisterShadersEvent",
    "net.neoforged.neoforge.client.event.RenderHandEvent": "net.minecraftforge.client.event.RenderHandEvent",
    "net.neoforged.neoforge.client.event.RenderLevelStageEvent": "net.minecraftforge.client.event.RenderLevelStageEvent",
    "net.neoforged.neoforge.client.event.RenderLivingEvent": "net.minecraftforge.client.event.RenderLivingEvent",
    "net.neoforged.neoforge.client.event.ScreenEvent": "net.minecraftforge.client.event.ScreenEvent",
    "net.neoforged.neoforge.client.event.ViewportEvent": "net.minecraftforge.client.event.ViewportEvent",
    # Game events.
    "net.neoforged.neoforge.event.RegisterCommandsEvent": "net.minecraftforge.event.RegisterCommandsEvent",
    "net.neoforged.neoforge.event.entity.EntityJoinLevelEvent": "net.minecraftforge.event.entity.EntityJoinLevelEvent",
    "net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent": "net.minecraftforge.event.entity.EntityLeaveLevelEvent",
    "net.neoforged.neoforge.event.entity.living.LivingHealEvent": "net.minecraftforge.event.entity.living.LivingHealEvent",
    # NeoForge's damage before the armour: Forge's hurt event, before the armour too.
    "net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent": "net.minecraftforge.event.entity.living.LivingHurtEvent",
    "net.neoforged.neoforge.event.entity.player.PlayerEvent": "net.minecraftforge.event.entity.player.PlayerEvent",
    "net.neoforged.neoforge.event.entity.player.PlayerInteractEvent": "net.minecraftforge.event.entity.player.PlayerInteractEvent",
    "net.neoforged.neoforge.event.level.BlockEvent": "net.minecraftforge.event.level.BlockEvent",
    "net.neoforged.neoforge.event.level.ChunkEvent": "net.minecraftforge.event.level.ChunkEvent",
    "net.neoforged.neoforge.event.level.LevelEvent": "net.minecraftforge.event.level.LevelEvent",
    "net.neoforged.neoforge.event.level.ExplosionEvent": "net.minecraftforge.event.level.ExplosionEvent",
    "net.neoforged.neoforge.event.server.ServerStoppedEvent": "net.minecraftforge.event.server.ServerStoppedEvent",
    "net.neoforged.neoforge.event.server.ServerStartingEvent": "net.minecraftforge.event.server.ServerStartingEvent",
    # NeoForge's tick events (a class each, Pre and Post): Forge's, with a phase (rules below).
    "net.neoforged.neoforge.event.tick.LevelTickEvent": "net.minecraftforge.event.TickEvent",
    "net.neoforged.neoforge.event.tick.PlayerTickEvent": "net.minecraftforge.event.TickEvent",
    "net.neoforged.neoforge.event.tick.ServerTickEvent": "net.minecraftforge.event.TickEvent",
    "net.neoforged.neoforge.client.event.ClientTickEvent": "net.minecraftforge.event.TickEvent",
    "net.neoforged.neoforge.client.event.RenderFrameEvent": "net.minecraftforge.event.TickEvent",
    # Fluids, items, energy.
    "net.neoforged.neoforge.fluids.BaseFlowingFluid": "net.minecraftforge.fluids.ForgeFlowingFluid",
    "net.neoforged.neoforge.fluids.FluidStack": "net.minecraftforge.fluids.FluidStack",
    "net.neoforged.neoforge.fluids.FluidType": "net.minecraftforge.fluids.FluidType",
    "net.neoforged.neoforge.fluids.FluidUtil": "net.minecraftforge.fluids.FluidUtil",
    "net.neoforged.neoforge.fluids.capability.IFluidHandler": "net.minecraftforge.fluids.capability.IFluidHandler",
    "net.neoforged.neoforge.fluids.capability.IFluidHandlerItem": "net.minecraftforge.fluids.capability.IFluidHandlerItem",
    "net.neoforged.neoforge.fluids.capability.templates.FluidTank": "net.minecraftforge.fluids.capability.templates.FluidTank",
    "net.neoforged.neoforge.items.IItemHandler": "net.minecraftforge.items.IItemHandler",
    "net.neoforged.neoforge.items.IItemHandlerModifiable": "net.minecraftforge.items.IItemHandlerModifiable",
    "net.neoforged.neoforge.items.ItemStackHandler": "net.minecraftforge.items.ItemStackHandler",
    "net.neoforged.neoforge.items.ItemHandlerHelper": "net.minecraftforge.items.ItemHandlerHelper",
    "net.neoforged.neoforge.energy.IEnergyStorage": "net.minecraftforge.energy.IEnergyStorage",
    "net.neoforged.neoforge.energy.EnergyStorage": "net.minecraftforge.energy.EnergyStorage",
    # Registries.
    "net.neoforged.neoforge.registries.DataPackRegistryEvent": "net.minecraftforge.registries.DataPackRegistryEvent",
    "net.neoforged.neoforge.registries.NeoForgeRegistries": "net.minecraftforge.registries.ForgeRegistries",
    "net.neoforged.neoforge.registries.RegisterEvent": "net.minecraftforge.registries.RegisterEvent",
    "net.neoforged.neoforge.gametest.GameTestHolder": "net.minecraftforge.gametest.GameTestHolder",
    "net.neoforged.neoforge.gametest.PrefixGameTestTemplate": "net.minecraftforge.gametest.PrefixGameTestTemplate",
    # NeoForge's own systems, as this branch's stand-ins over Forge's (dev.groundburst.legacy).
    "net.neoforged.neoforge.registries.DeferredRegister": f"{L}.DeferredRegister",
    "net.neoforged.neoforge.registries.DeferredHolder": f"{L}.DeferredHolder",
    "net.neoforged.neoforge.registries.DeferredBlock": f"{L}.DeferredBlock",
    "net.neoforged.neoforge.registries.DeferredItem": f"{L}.DeferredItem",
    "net.neoforged.neoforge.registries.datamaps.DataMapType": f"{L}.DataMapType",
    "net.neoforged.neoforge.registries.datamaps.DataMapsUpdatedEvent": f"{L}.DataMapsUpdatedEvent",
    "net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent": f"{L}.RegisterDataMapTypesEvent",
    "net.neoforged.neoforge.attachment.AttachmentType": f"{L}.AttachmentType",
    "net.neoforged.neoforge.capabilities.BlockCapability": f"{L}.BlockCapability",
    "net.neoforged.neoforge.capabilities.BlockCapabilityCache": f"{L}.BlockCapabilityCache",
    "net.neoforged.neoforge.capabilities.ItemCapability": f"{L}.ItemCapability",
    "net.neoforged.neoforge.capabilities.Capabilities": f"{L}.Capabilities",
    "net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent": f"{L}.RegisterCapabilitiesEvent",
    "net.neoforged.neoforge.network.PacketDistributor": f"{L}.net.PacketDistributor",
    "net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent": f"{L}.net.RegisterPayloadHandlersEvent",
    "net.neoforged.neoforge.network.handling.IPayloadContext": f"{L}.net.IPayloadContext",
    "net.neoforged.neoforge.network.handling.IPayloadHandler": f"{L}.net.IPayloadHandler",
    "net.neoforged.neoforge.network.registration.PayloadRegistrar": f"{L}.net.PayloadRegistrar",
    "net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent": f"{L}.RegisterTicketControllersEvent",
    "net.neoforged.neoforge.common.world.chunk.TicketController": f"{L}.TicketController",
    "net.neoforged.neoforge.common.util.TriState": f"{L}.TriState",
    "net.neoforged.neoforge.common.TranslatableEnum": f"{L}.TranslatableEnum",
    "net.neoforged.neoforge.common.crafting.SizedIngredient": f"{L}.SizedIngredient",
    "net.neoforged.neoforge.fluids.crafting.FluidIngredient": f"{L}.FluidIngredient",
    "net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient": f"{L}.SizedFluidIngredient",
    "net.neoforged.neoforge.client.event.RegisterMenuScreensEvent": f"{L}.client.RegisterMenuScreensEvent",
    "net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent": f"{L}.client.RegisterClientExtensionsEvent",
    "net.neoforged.neoforge.client.event.RegisterGuiLayersEvent": f"{L}.client.RegisterGuiLayersEvent",
    "net.neoforged.neoforge.client.event.RenderGuiLayerEvent": f"{L}.client.RenderGuiLayerEvent",
    "net.neoforged.neoforge.client.gui.VanillaGuiLayers": f"{L}.client.VanillaGuiLayers",
    "net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent": f"{L}.client.CalculatePlayerTurnEvent",
    "net.neoforged.neoforge.client.gui.ConfigurationScreen": f"{L}.client.ConfigurationScreen",
    "net.neoforged.neoforge.client.gui.IConfigScreenFactory": f"{L}.client.IConfigScreenFactory",
    # Vanilla 1.21.1 types 1.20.1 lacks: this branch's stand-ins.
    "net.minecraft.world.ItemInteractionResult": f"{L}.ItemInteractionResult",
    "net.minecraft.client.DeltaTracker": f"{L}.DeltaTracker",
    "net.minecraft.network.RegistryFriendlyByteBuf": "net.minecraft.network.FriendlyByteBuf",
    "net.minecraft.network.codec.StreamCodec": f"{L}.net.StreamCodec",
    "net.minecraft.network.codec.ByteBufCodecs": f"{L}.net.ByteBufCodecs",
    "net.minecraft.network.protocol.common.custom.CustomPacketPayload": f"{L}.net.CustomPacketPayload",
    "net.minecraft.core.component.DataComponentType": f"{L}.component.DataComponentType",
    "net.minecraft.core.component.DataComponents": f"{L}.component.DataComponents",
    "net.minecraft.world.item.component.CustomData": f"{L}.component.CustomData",
    "net.minecraft.world.item.component.ItemContainerContents": f"{L}.component.ItemContainerContents",
    "net.minecraft.world.item.crafting.RecipeHolder": f"{L}.recipe.RecipeHolder",
    "net.minecraft.world.item.crafting.RecipeInput": f"{L}.recipe.RecipeInput",
    "net.minecraft.world.item.crafting.SingleRecipeInput": f"{L}.recipe.SingleRecipeInput",
    "net.minecraft.world.item.crafting.CraftingInput": f"{L}.recipe.CraftingInput",
}

# Fully qualified names written in the code (and imports not in IMPORTS): moved by prefix, the longest first.
PACKAGES = [
    ("net.neoforged.neoforge.common.NeoForge", "net.minecraftforge.common.MinecraftForge"),
    ("net.neoforged.neoforge.common.ModConfigSpec", "net.minecraftforge.common.ForgeConfigSpec"),
    ("net.neoforged.neoforge.registries.DeferredHolder", f"{L}.DeferredHolder"),
    ("net.neoforged.neoforge.registries.DeferredItem", f"{L}.DeferredItem"),
    ("net.neoforged.neoforge.registries.DeferredBlock", f"{L}.DeferredBlock"),
    ("net.neoforged.neoforge.registries.DeferredRegister", f"{L}.DeferredRegister"),
    ("net.neoforged.neoforge.network.PacketDistributor", f"{L}.net.PacketDistributor"),
    ("net.neoforged.neoforge.network.handling.IPayloadContext", f"{L}.net.IPayloadContext"),
    ("net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent", f"{L}.net.RegisterPayloadHandlersEvent"),
    ("net.neoforged.neoforge.network.registration.PayloadRegistrar", f"{L}.net.PayloadRegistrar"),
    ("net.neoforged.neoforge.network.handling.IPayloadHandler", f"{L}.net.IPayloadHandler"),
    ("net.neoforged.neoforge.capabilities.", f"{L}."),
    ("net.neoforged.neoforge.attachment.AttachmentType", f"{L}.AttachmentType"),
    ("net.neoforged.neoforge.common.world.chunk.TicketController", f"{L}.TicketController"),
    ("net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent", f"{L}.RegisterTicketControllersEvent"),
    ("net.neoforged.neoforge.common.world.chunk.TicketHelper", "net.minecraftforge.common.world.ForgeChunkManager.TicketHelper"),
    ("net.neoforged.neoforge.common.world.chunk.LoadingValidationCallback",
     "net.minecraftforge.common.world.ForgeChunkManager.LoadingValidationCallback"),
    ("net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent", f"{L}.RegisterCapabilitiesEvent"),
    ("net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent", f"{L}.client.RegisterClientExtensionsEvent"),
    ("net.neoforged.neoforge.client.event.RegisterGuiLayersEvent", f"{L}.client.RegisterGuiLayersEvent"),
    ("net.neoforged.neoforge.client.event.RenderGuiLayerEvent", f"{L}.client.RenderGuiLayerEvent"),
    ("net.neoforged.neoforge.client.gui.VanillaGuiLayers", f"{L}.client.VanillaGuiLayers"),
    ("net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent", f"{L}.client.CalculatePlayerTurnEvent"),
    ("net.neoforged.neoforge.registries.datamaps.", f"{L}."),
    ("net.neoforged.neoforge.common.crafting.SizedIngredient", f"{L}.SizedIngredient"),
    ("net.neoforged.neoforge.fluids.crafting.", f"{L}."),
    ("net.neoforged.neoforge.event.tick.RenderFrameEvent", "net.minecraftforge.event.TickEvent.RenderTickEvent"),
    ("net.neoforged.neoforge.client.event.RenderFrameEvent", "net.minecraftforge.event.TickEvent.RenderTickEvent"),
    ("net.neoforged.neoforge.fluids.BaseFlowingFluid", "net.minecraftforge.fluids.ForgeFlowingFluid"),
    ("net.neoforged.neoforge.event.tick.ServerTickEvent", "net.minecraftforge.event.TickEvent.ServerTickEvent"),
    ("net.neoforged.neoforge.event.tick.LevelTickEvent", "net.minecraftforge.event.TickEvent.LevelTickEvent"),
    ("net.neoforged.neoforge.event.tick.PlayerTickEvent", "net.minecraftforge.event.TickEvent.PlayerTickEvent"),
    ("net.neoforged.neoforge.client.event.ClientTickEvent", "net.minecraftforge.event.TickEvent.ClientTickEvent"),
    ("net.neoforged.neoforge.client.event.RegisterMenuScreensEvent", f"{L}.client.RegisterMenuScreensEvent"),
    ("net.neoforged.neoforge.client.event.", "net.minecraftforge.client.event."),
    ("net.neoforged.neoforge.event.", "net.minecraftforge.event."),
    ("net.neoforged.neoforge.fluids.", "net.minecraftforge.fluids."),
    ("net.neoforged.neoforge.items.", "net.minecraftforge.items."),
    ("net.neoforged.neoforge.energy.", "net.minecraftforge.energy."),
    ("net.neoforged.neoforge.client.", "net.minecraftforge.client."),
    ("net.neoforged.neoforge.common.", "net.minecraftforge.common."),
    ("net.neoforged.neoforge.registries.", "net.minecraftforge.registries."),
    ("net.neoforged.fml.", "net.minecraftforge.fml."),
    ("net.neoforged.bus.api.", "net.minecraftforge.eventbus.api."),
    ("net.neoforged.api.distmarker.", "net.minecraftforge.api.distmarker."),
    ("net.minecraft.world.ItemInteractionResult", f"{L}.ItemInteractionResult"),
    ("net.minecraft.client.DeltaTracker", f"{L}.DeltaTracker"),
    ("net.minecraft.network.RegistryFriendlyByteBuf", "net.minecraft.network.FriendlyByteBuf"),
    ("net.minecraft.network.codec.StreamCodec", f"{L}.net.StreamCodec"),
    ("net.minecraft.network.codec.ByteBufCodecs", f"{L}.net.ByteBufCodecs"),
    ("net.minecraft.network.protocol.common.custom.CustomPacketPayload", f"{L}.net.CustomPacketPayload"),
    ("net.minecraft.network.codec.", f"{L}.net."),
    ("net.minecraft.core.component.", f"{L}.component."),
    ("net.minecraft.world.item.component.", f"{L}.component."),
    ("net.minecraft.world.item.crafting.RecipeHolder", f"{L}.recipe.RecipeHolder"),
    ("net.minecraft.world.item.crafting.RecipeInput", f"{L}.recipe.RecipeInput"),
    ("net.minecraft.world.item.crafting.SingleRecipeInput", f"{L}.recipe.SingleRecipeInput"),
    ("net.minecraft.world.item.crafting.CraftingInput", f"{L}.recipe.CraftingInput"),
    # 1.20.5's names for blocks 1.20.1 has under its own.
    ("net.minecraft.world.level.block.TransparentBlock", "net.minecraft.world.level.block.AbstractGlassBlock"),
    ("net.minecraft.world.level.block.WaterloggedTransparentBlock", f"{L}.WaterloggedTransparentBlock"),
    # GeckoLib 4 on 1.20.1 keeps its animation core in its core packages.
    ("software.bernie.geckolib.animatable.GeoAnimatable", "software.bernie.geckolib.core.animatable.GeoAnimatable"),
    ("software.bernie.geckolib.animatable.instance.", "software.bernie.geckolib.core.animatable.instance."),
    ("software.bernie.geckolib.animation.PlayState", "software.bernie.geckolib.core.object.PlayState"),
    ("software.bernie.geckolib.animation.state.", "software.bernie.geckolib.core.state."),
    ("software.bernie.geckolib.animation.", "software.bernie.geckolib.core.animation."),
    ("software.bernie.geckolib.constant.dataticket.DataTicket", "software.bernie.geckolib.core.object.DataTicket"),
    ("software.bernie.geckolib.util.Color", "software.bernie.geckolib.core.object.Color"),
    ("software.bernie.geckolib.util.RenderUtil", "software.bernie.geckolib.util.RenderUtils"),
]
PACKAGES.sort(key=lambda p: -len(p[0]))

# A class's name with or without its package before it (and nothing else of a name).
PK = r"(?<![\w.])(?:[a-z]\w*\.)*"

# Plain textual rules, in order.
RULES = [
    # Forge 1.20.1's ResourceLocation is made with new (1.21's factories).
    sub(r"\b(net\.minecraft\.resources\.)?ResourceLocation\.fromNamespaceAndPath\(", r"new \1ResourceLocation("),
    sub(r"\b(net\.minecraft\.resources\.)?ResourceLocation\.withDefaultNamespace\(", r"new \1ResourceLocation("),
    sub(r"\b(net\.minecraft\.resources\.)?ResourceLocation\.parse\(", r"new \1ResourceLocation("),
    # NeoForge's common tags (c:) by name in the code: Forge 1.20.1's (dev.groundburst.legacy.Tags2; a fluid's water is vanilla's).
    sub(r"(Registries\.FLUID,\s*)new\s+(?:net\.minecraft\.resources\.)?ResourceLocation\(\s*\"c\"\s*,\s*", rf"\1{L}.Tags2.commonFluid("),
    sub(r"\bnew\s+(?:net\.minecraft\.resources\.)?ResourceLocation\(\s*\"c\"\s*,\s*", f"{L}.Tags2.common("),
    # Java 21's Math.clamp: this branch's, with the same overloads (Java 17 has none).
    sub(r"\bMath\.clamp\(", f"{L}.J21.clamp("),
    sub(r"\bNeoForge\.EVENT_BUS\b", "MinecraftForge.EVENT_BUS"),
    sub(r"\bModConfigSpec\b", "ForgeConfigSpec"),
    sub(r"\bBaseFlowingFluid\b", "ForgeFlowingFluid"),
    sub(r"\bNeoForgeRegistries\b", "ForgeRegistries"),
    sub(r"\bIMenuTypeExtension\b", "IForgeMenuType"),
    sub(r"\bClientHooks\b", "ForgeHooksClient"),
    sub(r"\bLivingIncomingDamageEvent\b", "LivingHurtEvent"),
    sub(r"\bExtraFaceData\b", "ForgeFaceData"),
    sub(r"\bRegistryFriendlyByteBuf\b", "FriendlyByteBuf"),
    sub(r"\bRenderUtil\b", "RenderUtils"),
    sub(r"\bTransparentBlock\b", "AbstractGlassBlock"),
    # Stream codecs vanilla 1.21 keeps on its own classes (dev.groundburst.legacy.net.Codecs on 1.20.1).
    # (Each with or without its class's package.)
    sub(PK + r"BlockPos\.STREAM_CODEC\b", f"{L}.net.Codecs.BLOCK_POS"),
    sub(PK + r"ItemStack\.OPTIONAL_STREAM_CODEC\b", f"{L}.net.Codecs.OPTIONAL_ITEM_STACK"),
    sub(PK + r"ItemStack\.STREAM_CODEC\b", f"{L}.net.Codecs.ITEM_STACK"),
    sub(PK + r"FluidStack\.(?:OPTIONAL_)?STREAM_CODEC\b", f"{L}.net.Codecs.FLUID_STACK"),
    sub(PK + r"ResourceLocation\.STREAM_CODEC\b", f"{L}.net.Codecs.RESOURCE_LOCATION"),
    sub(PK + r"UUIDUtil\.STREAM_CODEC\b", f"{L}.net.Codecs.UUID_CODEC"),
    sub(PK + r"ComponentSerialization\.STREAM_CODEC\b", f"{L}.net.Codecs.COMPONENT"),
    sub(PK + r"Ingredient\.CONTENTS_STREAM_CODEC\b", f"{L}.net.Codecs.INGREDIENT"),
    sub(PK + r"Ingredient\.CODEC_NONEMPTY\b", f"{L}.net.Codecs.INGREDIENT_NONEMPTY_CODEC"),
    sub(PK + r"Ingredient\.CODEC\b", f"{L}.net.Codecs.INGREDIENT_CODEC"),
    # JEI 19's recipe types of the game's (JEI 15: an id and a class, dev.groundburst.legacy.jei.JeiTypes).
    sub(PK + r"RecipeType\.createFromVanilla\(", f"{L}.jei.JeiTypes.fromVanilla("),
    # NeoForge's registries 1.20.1 has none of.
    sub(r"\bForgeRegistries\.Keys\.ATTACHMENT_TYPES\b", f"{L}.Attachments.REGISTRY_KEY"),
    sub(r"\bDeferredRegister\.createDataComponents\(\s*Registries\.DATA_COMPONENT_TYPE\s*,\s*", "DeferredRegister.createDataComponents("),
    # NeoForge's tri-state answers to an interaction event: Forge's results.
    sub(r"\bTriState\.TRUE\b", "net.minecraftforge.eventbus.api.Event.Result.ALLOW"),
    sub(r"\bTriState\.FALSE\b", "net.minecraftforge.eventbus.api.Event.Result.DENY"),
    sub(r"\bTriState\.DEFAULT\b", "net.minecraftforge.eventbus.api.Event.Result.DEFAULT"),
]

# Imports with nothing left to name on 1.20.1.
IMPORTS.update({
    # A test player's connection: 1.20.1's is made without the client's settings (rules below).
    "net.minecraft.server.network.CommonListenerCookie": None,
    "net.minecraft.server.level.ClientInformation": None,
    "net.minecraft.util.ProblemReporter": None,
    "net.minecraft.core.dispenser.BlockSource": "net.minecraft.core.BlockSource",
    "net.neoforged.neoforge.common.util.TriState": None,
    "net.neoforged.neoforge.client.gui.ConfigurationScreen": None,
    "net.neoforged.neoforge.client.gui.IConfigScreenFactory": None,
})

# Whole lines removed: NeoForge's that have no counterpart on Forge 1.20.1.
DROP_LINES = [re.compile(p) for p in (
    # NeoForge draws a config screen of its own for any mod's settings; Forge 1.20.1 has none.
    r"^\s*\w+\.registerExtensionPoint\(IConfigScreenFactory\.class, ConfigurationScreen::new\);\s*$",
    # The client half is not a mod of its own on Forge 1.20.1 (one @Mod a mod): the mod starts it.
    r"^@Mod\(value = GroundBurst\.MODID, dist = Dist\.CLIENT\)\s*$",
)]


def skip(text: str, i: int) -> int:
    """If a string, character literal, text block or comment starts at {i}, the index just past it; else {i}."""
    if text.startswith('"""', i):
        end = text.find('"""', i + 3)
        return len(text) if end < 0 else end + 3
    ch = text[i]
    if ch in "\"'":
        j = i + 1
        while j < len(text) and text[j] != ch and text[j] != "\n":
            j += 2 if text[j] == "\\" else 1
        return j + 1
    if text.startswith("//", i):
        end = text.find("\n", i)
        return len(text) if end < 0 else end
    if text.startswith("/*", i):
        end = text.find("*/", i + 2)
        return len(text) if end < 0 else end + 2
    return i


def find_close(text: str, i: int) -> int:
    """The index just past the bracket that closes the one opened just before {i} (past strings and comments)."""
    depth = 1
    while i < len(text) and depth:
        j = skip(text, i)
        if j != i:
            i = j
            continue
        ch = text[i]
        if ch in "([{":
            depth += 1
        elif ch in ")]}":
            depth -= 1
        i += 1
    return i


def receiver_calls(text: str, method: str, make, accept=lambda recv, args: True) -> str:
    """Each call {recv}.{method}({args}) (recv the whole expression before the dot) as make(recv, args), where accept says,
    nested and chained ones too. make returns (prefix, rest): the text scanned again starts after the prefix."""
    pat = re.compile(r"\.\s*" + method + r"\s*\(")
    pos = 0
    while True:
        m = pat.search(text, pos)
        if not m:
            return text
        dot = m.start()
        start = receiver_start(text, dot)
        recv = text[start:dot].strip()
        end = find_close(text, m.end())
        args = text[m.end():end - 1]
        if not recv or recv in ("super", "this") or not accept(recv, args):
            pos = m.end()
            continue
        prefix, rest = make(recv, args)
        text = text[:start] + prefix + rest + text[end:]
        pos = start + len(prefix)


COMPONENT_ARG = re.compile(r"^\s*(?:(?:dev\.groundburst\.registry\.)?GBDataComponents\.\w+(?:\.get\(\))?"
                           r"|(?:dev\.groundburst\.legacy\.component\.)?DataComponents\.[A-Z_]+)\s*$")


def first_arg(args: str) -> str:
    return split_args(args)[0] if args.strip() else ""


def components(text: str) -> str:
    """1.21's component calls on an item stack: dev.groundburst.legacy.component.Comps's (1.20.1 keeps them in its NBT)."""
    for op, fn in (("get", "get"), ("getOrDefault", "getOrDefault"), ("has", "has"), ("set", "set"), ("remove", "remove"),
                   ("update", "update")):
        text = receiver_calls(text, op, lambda recv, args, fn=fn: (f"{L}.component.Comps.{fn}(", f"{recv}, {args})"),
                              lambda recv, args: bool(COMPONENT_ARG.match(first_arg(args))))
    # An item's default components, given with its properties.
    text = receiver_calls(text, "component", lambda recv, args: (f"{L}.component.Comps.withDefault(", f"{recv}, {args})"),
                          lambda recv, args: len(split_args(args)) == 2)
    return text


def capabilities(text: str) -> str:
    """NeoForge's capability lookups on a level, a stack or an entity: dev.groundburst.legacy.Caps's."""
    return receiver_calls(text, "getCapability", lambda recv, args: (f"{L}.Caps.get(", f"{recv}, {args})"),
                          lambda recv, args: bool(args.strip()))   # a capability cache's own getCapability() takes none


def attachments(text: str) -> str:
    """NeoForge's data attachments on an entity or a chunk, and its data maps on a registry's holder."""
    text = receiver_calls(text, "getData", lambda recv, args: ("", f"{args.strip()}.get({recv})"),
                          lambda recv, args: recv.endswith("builtInRegistryHolder()"))
    for op, fn in (("hasData", "has"), ("getData", "get"), ("setData", "set"), ("removeData", "remove"),
                   ("getExistingDataOrNull", "getExistingOrNull"), ("getExistingData", "getExisting")):
        text = receiver_calls(text, op, lambda recv, args, fn=fn: (f"{L}.Attachments.{fn}(", f"{recv}, {args})"))
    return text


def recipe_lookups(text: str) -> str:
    """1.21's recipe manager hands back holders: dev.groundburst.legacy.recipe.Recipes over 1.20.1's."""
    text = receiver_calls(text, "getAllRecipesFor", lambda recv, args: (f"{L}.recipe.Recipes.all(", f"{recv}, {args})"))
    text = receiver_calls(text, "getRecipeFor", lambda recv, args: (f"{L}.recipe.Recipes.find(", f"{recv}, {args})"),
                          lambda recv, args: len(split_args(args)) == 3)
    return text


# Java 21's first and last of a list (Java 17 has them on a deque only: J21 takes both). Pair.getFirst is DFU's own.
def sequenced(text: str) -> str:
    pairs = set(re.findall(r"\bPair<[^;=]*?>\s+(\w+)\s*[=;,)]", text))

    def ok(recv: str, args: str) -> bool:
        return not args.strip() and not re.search(r"(?i)pair|either|\bp\d*$|entry$", recv) and recv not in pairs
    for op, fn in (("getFirst", "first"), ("getLast", "last"), ("removeFirst", "removeFirst"), ("removeLast", "removeLast")):
        text = receiver_calls(text, op, lambda recv, args, fn=fn: (f"{L}.J21.{fn}(", f"{recv})"), ok)
    return text


TICK_EVENTS = {"ServerTickEvent": "ServerTickEvent", "LevelTickEvent": "LevelTickEvent", "PlayerTickEvent": "PlayerTickEvent",
               "ClientTickEvent": "ClientTickEvent"}


def tick_events(text: str) -> str:
    """NeoForge's tick events (Pre and Post, a class each) as Forge's (one, with a phase): a guard at the top of each
    listener for its phase, and the level's and player's getters as Forge's fields."""
    def accessors(body: str, name: str, kind: str) -> str:
        if kind == "LevelTickEvent":
            body = re.sub(rf"\b{name}\.getLevel\(\)", f"{name}.level", body)
        if kind == "PlayerTickEvent":
            body = re.sub(rf"\b{name}\.getEntity\(\)", f"{name}.player", body)
        if kind in ("RenderFrameEvent", "RenderTickEvent"):
            body = re.sub(rf"\b{name}\.getPartialTick\(\)", f"{L}.DeltaTracker.of({name}.renderTickTime)", body)
        return body

    # Methods: void name(XTickEvent.Post event) { ... }
    def method(m: re.Match) -> str:
        return m.group(0)
    pat = re.compile(r"\((?:final\s+)?(?:net\.minecraftforge\.event\.TickEvent\.|TickEvent\.)?(ServerTickEvent|LevelTickEvent|PlayerTickEvent|ClientTickEvent"
                     r"|RenderFrameEvent|RenderTickEvent)\.(Pre|Post)\s+(\w+)\)")
    out, pos = [], 0
    for m in pat.finditer(text):
        if m.start() < pos:
            continue
        kind, phase, name = m.group(1), m.group(2), m.group(3)
        if kind in ("RenderFrameEvent", "RenderTickEvent"):
            kind = "RenderTickEvent"
        guard = f"if ({name}.phase != net.minecraftforge.event.TickEvent.Phase.{'START' if phase == 'Pre' else 'END'}) return;"
        head = f"(net.minecraftforge.event.TickEvent.{kind} {name})"
        after = text[m.end():]
        lam = re.match(r"\s*->\s*", after)
        brace = re.match(r"\s*(?:throws [\w., ]+)?\s*\{", after)
        if lam:
            body_start = m.end() + lam.end()
            if text[body_start] == "{":
                end = find_close(text, body_start + 1)
                body = accessors(text[body_start + 1:end - 1], name, kind)
                out.append(text[pos:m.start()] + head + " -> {" + guard + body + "}")
            else:
                # An expression body: to the end of the call it is the last argument of.
                depth, i = 0, body_start
                while i < len(text):
                    ch = text[i]
                    if ch in "([{":
                        depth += 1
                    elif ch in ")]}":
                        if depth == 0:
                            break
                        depth -= 1
                    elif ch in "\"'":
                        q = ch
                        i += 1
                        while i < len(text) and text[i] != q:
                            i += 2 if text[i] == "\\" else 1
                    elif ch == ";" and depth == 0:
                        break
                    i += 1
                end = i
                body = accessors(text[body_start:end].rstrip(), name, kind)
                out.append(text[pos:m.start()] + head + " -> { " + guard + " " + body + "; }")
            pos = end
        elif brace:
            body_start = m.end() + brace.end()
            end = find_close(text, body_start)
            body = accessors(text[body_start:end - 1], name, kind)
            out.append(text[pos:m.start()] + head + text[m.end():body_start] + " " + guard + body + "}")
            pos = end
        else:
            out.append(text[pos:m.end()])
            pos = m.end()
    out.append(text[pos:])
    text = "".join(out)
    # Bare references to the classes (an addListener's class argument): Forge's.
    text = re.sub(r"(net\.minecraftforge\.event\.TickEvent\.)(?:RenderFrameEvent|RenderTickEvent)\.(?:Pre|Post)\b", r"\1RenderTickEvent", text)
    text = re.sub(r"(net\.minecraftforge\.event\.TickEvent\.\w+)\.(?:Pre|Post)\b", r"\1", text)
    text = re.sub(r"(?<![\w.])(?:RenderFrameEvent)\.(?:Pre|Post)\b", "net.minecraftforge.event.TickEvent.RenderTickEvent", text)
    text = re.sub(r"(?<![\w.])(ServerTickEvent|LevelTickEvent|PlayerTickEvent|ClientTickEvent)\.(?:Pre|Post)\b", r"net.minecraftforge.event.TickEvent.\1", text)
    return text


def param(type_re: str) -> str:
    """A parameter of a type matching {type_re}, its name captured (annotations and final allowed before it)."""
    return r"(?:final\s+)?(?:@[\w.]+\s+)*(?:" + type_re + r")\s+(\w+)"


C = r"\s*,\s*"
QN = r"(?:[\w.]+\.)?"
REGISTRIES = f"{L}.Regs.access()"

# Overridden methods whose signatures changed: the 1.21.1 build's body kept, the signature 1.20.1's, and a line at the
# body's top giving the body what it had.
SIGNATURES = [
    # Saving a block entity: 1.21 hands the registries along (1.20.1's are found: dev.groundburst.legacy.Regs).
    sub(r"(\bprotected void saveAdditional\(\s*" + param(QN + "CompoundTag") + r")" + C + param(QN + r"HolderLookup\.Provider") + r"\s*\)(\s*\{)",
        rf"\1)\4 net.minecraft.core.HolderLookup.Provider \3 = {REGISTRIES};"),
    sub(r"(\b(?:protected|public) )void loadAdditional\(\s*" + param(QN + "CompoundTag") + C + param(QN + r"HolderLookup\.Provider") + r"\s*\)(\s*\{)",
        rf"public void load(net.minecraft.nbt.CompoundTag \2)\4 net.minecraft.core.HolderLookup.Provider \3 = {REGISTRIES};"),
    sub(r"\bsuper\.saveAdditional\((\w+), \w+\)", r"super.saveAdditional(\1)"),
    sub(r"\bsuper\.loadAdditional\((\w+), \w+\)", r"super.load(\1)"),
    sub(r"(\bpublic )([\w.]+) getUpdateTag\(\s*" + param(QN + r"HolderLookup\.Provider") + r"\s*\)(\s*\{)",
        rf"\1\2 getUpdateTag()\4 net.minecraft.core.HolderLookup.Provider \3 = {REGISTRIES};"),
    sub(r"(\bpublic void handleUpdateTag\(\s*" + param(QN + "CompoundTag") + r")" + C + param(QN + r"HolderLookup\.Provider") + r"\s*\)(\s*\{)",
        rf"\1)\4 net.minecraft.core.HolderLookup.Provider \3 = {REGISTRIES};"),
    sub(r"(\bpublic void onDataPacket\(\s*" + param(QN + "Connection") + C + param(QN + r"ClientboundBlockEntityDataPacket") + r")" + C
        + param(QN + r"HolderLookup\.Provider") + r"\s*\)(\s*\{)",
        rf"\1)\5 net.minecraft.core.HolderLookup.Provider \4 = {REGISTRIES};"),
    sub(r"\bsuper\.getUpdateTag\(\w+\)", "super.getUpdateTag()"),
    sub(r"\bsuper\.handleUpdateTag\((\w+), \w+\)", r"super.handleUpdateTag(\1)"),
    sub(r"\.saveWithoutMetadata\(\s*\w+\s*\)", ".saveWithoutMetadata()"),
    sub(r"\.saveWithFullMetadata\(\s*\w+\s*\)", ".saveWithFullMetadata()"),
    sub(r"\.saveWithId\(\s*\w+\s*\)", ".saveWithId()"),
    sub(r"\bBlockEntity\.loadStatic\(([^,()]+(?:\([^()]*\))?), ([^,()]+(?:\([^()]*\))?), ([^,()]+(?:\([^()]*\))?), [^,()]+(?:\([^()]*\))?\)",
        r"BlockEntity.loadStatic(\1, \2, \3)"),
    sub(r"\bItemStack\.parseOptional\(\s*\w+(?:\(\))?\s*,\s*", "ItemStack.of("),
    sub(r"\bItemStack\.parse\(\s*\w+(?:\(\))?\s*,\s*([^()]+(?:\([^()]*\))?)\)", r"java.util.Optional.of(ItemStack.of(\1))"),
    sub(r"\bContainerHelper\.(saveAllItems|loadAllItems)\(([^,()]+), ([^,()]+), [^,()]+\)", r"ContainerHelper.\1(\2, \3)"),
    sub(r"\bComponent\.Serializer\.toJson\(([^,()]+(?:\([^()]*\))?), [^,()]+\)", r"Component.Serializer.toJson(\1)"),
    sub(r"\bComponent\.Serializer\.fromJson\(([^,()]+(?:\([^()]*\))?), [^,()]+\)", r"Component.Serializer.fromJson(\1)"),
    sub(r"\bFluidStack\.parseOptional\(\s*\w+(?:\(\))?\s*,\s*", "FluidStack.loadFluidStackFromNBT("),
]


def stack_saves(text: str) -> str:
    """1.21's item and fluid stack saves take the registries: 1.20.1's write into a tag of their own."""
    text = receiver_calls(text, "saveOptional", lambda recv, args: ("", f"{recv}.save(new net.minecraft.nbt.CompoundTag())"),
                          lambda recv, args: len(split_args(args)) == 1)
    text = receiver_calls(text, "save", lambda recv, args: ("", f"{recv}.save({split_args(args)[1].strip()})"),
                          lambda recv, args: len(split_args(args)) == 2 and re.search(r"(?i)regist|provider|lookup", split_args(args)[0]))
    text = receiver_calls(text, "save", lambda recv, args: ("", f"{recv}.save(new net.minecraft.nbt.CompoundTag())"),
                          lambda recv, args: len(split_args(args)) == 1 and re.search(r"(?i)regist|provider|lookup|level\(\)\.registryAccess", args))
    return text


FUNCTIONS: list = [tick_events, components, capabilities, attachments, recipe_lookups, sequenced, stack_saves]


def method_bodies(text: str, signature: str, fn) -> str:
    """{fn} applied to each method whose declaration matches {signature} (up to its opening brace), body and all."""
    out, pos = [], 0
    for m in re.compile(signature, re.S).finditer(text):
        if m.start() < pos:
            continue
        end = find_close(text, m.end())                   # the declaration's match ends just past its opening brace
        out.append(text[pos:m.start()])
        out.append(fn(text[m.start():end]))
        pos = end
    out.append(text[pos:])
    return "".join(out)


def calls(text: str, start: str, fn) -> str:
    """Each call matching {start} (up to its opening parenthesis), its whole argument list found by matching brackets
    (over lines, past strings), replaced by fn(match, args)."""
    out, pos = [], 0
    for m in re.compile(start).finditer(text):
        if m.start() < pos:
            continue
        i = find_close(text, m.end())
        out.append(text[pos:m.start()])
        out.append(fn(m, text[m.end():i - 1]))
        pos = i
    out.append(text[pos:])
    return "".join(out)


def split_args(args: str) -> list[str]:
    """A call's arguments, split at its own commas."""
    parts, cur, depth, i = [], [], 0, 0
    while i < len(args):
        j = skip(args, i)
        if j != i:
            cur.append(args[i:j])
            i = j
            continue
        ch = args[i]
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


def receiver_start(text: str, dot: int) -> int:
    """Where the expression that ends at {dot} (the '.' before a method call) begins: walking back over names, dots,
    calls, indexing and generic arguments, to the first character that cannot be part of it."""
    i = dot - 1
    while i >= 0:
        ch = text[i]
        if ch in ")]":
            close, open_ = ch, "(" if ch == ")" else "["
            depth = 0
            while i >= 0:
                if text[i] == close:
                    depth += 1
                elif text[i] == open_:
                    depth -= 1
                    if depth == 0:
                        break
                elif text[i] == '"':
                    i -= 1
                    while i >= 0 and text[i] != '"':
                        i -= 1
                i -= 1
            i -= 1
            continue
        if ch.isalnum() or ch in "_$.":
            i -= 1
            continue
        if ch == '"':                                       # a string literal's method: "a".equals(b)
            i -= 1
            while i >= 0 and text[i] != '"':
                i -= 1
            i -= 1
            continue
        if ch in " \t\r\n":
            # Whitespace inside a chain broken over lines ("foo\n    .bar()"): keep going if the next line goes on with a dot.
            j = i
            while j >= 0 and text[j] in " \t\r\n":
                j -= 1
            k = i + 1
            while k < len(text) and text[k] in " \t\r\n":
                k += 1
            if k < len(text) and text[k] == "." and j >= 0 and (text[j].isalnum() or text[j] in "_)]>"):
                i = j
                continue
            break
        break
    start = i + 1
    m = re.search(r"\bnew\s+$", text[max(0, start - 12):start])
    if m:
        start -= len(m.group(0))
    return start


def port_imports(text: str) -> str:
    def repl(m: re.Match) -> str:
        name = m.group(2)
        static = m.group(1) or ""
        if name in IMPORTS:
            new = IMPORTS[name]
            return "" if new is None else f"import {static}{new};"
        for old, new in PACKAGES:
            if name.startswith(old):
                return f"import {static}{new}{name[len(old):]};"
        return m.group(0)
    text = re.sub(r"import (static )?([\w.]+);", repl, text)
    # One import, once (two 1.21.1 classes may both become the same 1.20.1 one).
    seen, out = set(), []
    for line in text.split("\n"):
        key = line.strip()
        if key.startswith("import ") and key in seen:
            continue
        if key.startswith("import "):
            seen.add(key)
        out.append(line)
    return "\n".join(out)


def qualified_names(text: str) -> str:
    """Fully qualified names in the code (not in imports): moved as their packages are."""
    lines = text.split("\n")
    for n, line in enumerate(lines):
        if line.startswith("import ") or line.startswith("package "):
            continue
        for old, new in PACKAGES:
            if old in line:
                line = re.sub(re.escape(old) + (r"\b" if old[-1].isalnum() else ""), new, line)
        lines[n] = line
    return "\n".join(lines)


def port_java(text: str, path: str) -> str:
    text = text.replace("\r\n", "\n")
    for fix in FILE_FIXES_BEFORE.get(path, []):
        text = fix(text)
    text = "\n".join(ln for ln in text.split("\n") if not any(p.match(ln) for p in DROP_LINES))
    text = port_imports(text)
    text = qualified_names(text)
    for pattern, repl in RULES:
        text = pattern.sub(repl, text)
    for pattern, repl in SIGNATURES:
        text = pattern.sub(repl, text)
    for fn in FUNCTIONS:
        text = fn(text)
    # NeoForge's config values answer as suppliers of their primitives; Forge 1.20.1's as plain suppliers.
    getters = r"\.getAs(?:Boolean|Int|Double|Long)\(\)"
    if "/config/" in path:
        text = re.sub(getters, ".get()", text)
    text = re.sub(r"\b(GB\w*Config\.[A-Z_0-9]+)" + getters, r"\1.get()", text)
    for fix in FILE_FIXES.get(path, []):
        text = fix(text)
    return text


# Per-file fixes (a function of the text each), for what only one file does: on the 1.21.1 text (BEFORE) or the port.
def after(anchor: str, insert: str):
    def fix(text: str) -> str:
        assert anchor in text, f"port.py: '{anchor}' is gone from the 1.21.1 build: update FILE_FIXES"
        return text.replace(anchor, anchor + insert, 1)
    return fix


def swap(old: str, new: str):
    def fix(text: str) -> str:
        assert old in text, f"port.py: '{old}' is gone from the 1.21.1 build: update FILE_FIXES"
        return text.replace(old, new)
    return fix


def replace_method(signature: str, new: str):
    """Replaces the method whose declaration begins with {signature} (to its closing brace) with {new}."""
    def fix(text: str) -> str:
        assert signature in text, f"port.py: '{signature}' is gone from the 1.21.1 build: update FILE_FIXES"
        return method_bodies(text, re.escape(signature) + r"[^{]*\{", lambda method: new)
    return fix


def drop_method(signature: str):
    """Removes the method whose declaration begins with {signature}."""
    def fix(text: str) -> str:
        assert signature in text, f"port.py: '{signature}' is gone from the 1.21.1 build: update FILE_FIXES"
        return method_bodies(text, r"\n[ \t]*" + re.escape(signature) + r"[^{]*\{", lambda method: "\n")
    return fix


def cut(start: str, end: str, insert: str = ""):
    """Removes the text from {start} (with it) up to {end} (without it), putting {insert} in its place."""
    def fix(text: str) -> str:
        i = text.find(start)
        assert i >= 0, f"port.py: '{start}' is gone from the 1.21.1 build: update FILE_FIXES"
        j = text.find(end, i + len(start))
        assert j >= 0, f"port.py: '{end}' is gone from the 1.21.1 build (after '{start}'): update FILE_FIXES"
        return text[:i] + insert + text[j:]
    return fix


# --- 1.21.1's game API on 1.20.1 ------------------------------------------------------------------------------------------
_CLASS_INDEX: dict | None = None


def class_index() -> dict:
    """Every class of the 1.21.1 build: its parent's simple name and whether it declares a block's useItemOn or
    useWithoutItem (so a block's use knows what its own parents answer)."""
    global _CLASS_INDEX
    if _CLASS_INDEX is None:
        _CLASS_INDEX = {}
        for path in source_files():
            if not path.endswith(".java") or not path.startswith("src/main/java/"):
                continue
            text = read_source(path).decode("utf-8")
            for m in re.finditer(r"\bclass (\w+)(?:<[^{]*?>)?\s+extends\s+([\w.]+)", text):
                _CLASS_INDEX[m.group(1)] = (m.group(2).split(".")[-1].split("<")[0],
                                            bool(re.search(r"\bItemInteractionResult useItemOn\(", text)),
                                            bool(re.search(r"\bInteractionResult useWithoutItem\(", text)))
    return _CLASS_INDEX


def inherits(cls: str, which: int) -> bool:
    """Whether a parent of the mod's class {cls} declares useItemOn (which 1) or useWithoutItem (which 2)."""
    idx = class_index()
    seen = set()
    parent = idx.get(cls, (None,))[0]
    while parent in idx and parent not in seen:
        seen.add(parent)
        if idx[parent][which]:
            return True
        parent = idx[parent][0]
    return False


USE_SIG_ITEM = re.compile(r"(@Override\s*\n\s*)?((?:public|protected) )(?:net\.minecraft\.world\.)?(?:dev\.groundburst\.legacy\.)?ItemInteractionResult useItemOn\(")
USE_SIG_WITHOUT = re.compile(r"(@Override\s*\n\s*)?((?:public|protected) )(?:net\.minecraft\.world\.)?InteractionResult useWithoutItem\(")


def block_use(text: str) -> str:
    """1.21's two block clicks (useItemOn, then useWithoutItem) as 1.20.1's one use: the two methods kept (no longer
    overriding anything), and a use for each class that has them, asking them in turn as 1.21 does."""
    if "useItemOn(" not in text and "useWithoutItem(" not in text:
        return text
    # A state's clicks (a part passing one to its controller): 1.20.1's use of that state.
    text = receiver_calls(text, "useItemOn", lambda recv, args: (f"{L}.UseOnBlock.itemOn(", f"{recv}, {args})"),
                          lambda recv, args: len(split_args(args)) == 5 and recv != "super")
    text = receiver_calls(text, "useWithoutItem", lambda recv, args: (f"{L}.UseOnBlock.withoutItem(", f"{recv}, {args})"),
                          lambda recv, args: len(split_args(args)) == 3 and recv != "super")
    out, pos = [], 0
    for m in re.finditer(r"\bclass (\w+)(?:<[^{]*?>)?\s+extends\s+([\w.]+)[^{]*\{", text):
        if m.start() < pos:
            continue
        name, parent = m.group(1), m.group(2).split(".")[-1]
        end = find_close(text, m.end())
        body = text[m.end():end - 1]
        has_item = bool(USE_SIG_ITEM.search(body))
        has_without = bool(USE_SIG_WITHOUT.search(body))
        if not (has_item or has_without):
            out.append(text[pos:end])
            pos = end
            continue
        idx = class_index()
        mod_parent_item = inherits(name, 1)
        mod_parent_without = inherits(name, 2)
        body = USE_SIG_ITEM.sub(lambda mm: mm.group(2) + "dev.groundburst.legacy.ItemInteractionResult useItemOn(", body)
        body = USE_SIG_WITHOUT.sub(lambda mm: mm.group(2) + "net.minecraft.world.InteractionResult useWithoutItem(", body)
        if not mod_parent_without:
            body = re.sub(r"\bsuper\.useWithoutItem\(([^,()]+), ([^,()]+), ([^,()]+), ([^,()]+), ([^,()]+)\)",
                          r"super.use(\1, \2, \3, \4, net.minecraft.world.InteractionHand.MAIN_HAND, \5)", body)
        if not mod_parent_item:
            body = re.sub(r"\bsuper\.useItemOn\([^;]*?\)(?=;)", f"{L}.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION", body)
        item = has_item or mod_parent_item
        without = has_without or mod_parent_without
        use = ["\n    @Override\n    public net.minecraft.world.InteractionResult use(net.minecraft.world.level.block.state.BlockState state_, "
               "net.minecraft.world.level.Level level_, net.minecraft.core.BlockPos pos_, net.minecraft.world.entity.player.Player player_, "
               "net.minecraft.world.InteractionHand hand_, net.minecraft.world.phys.BlockHitResult hit_) {\n"]
        if item:
            use.append(f"        {L}.ItemInteractionResult item_ = this.useItemOn(player_.getItemInHand(hand_), state_, level_, pos_, player_, hand_, hit_);\n"
                       f"        if (item_ != {L}.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) {{\n"
                       "            return item_.result();\n        }\n")
        if without:
            use.append("        return hand_ == net.minecraft.world.InteractionHand.MAIN_HAND ? this.useWithoutItem(state_, level_, pos_, player_, hit_)\n"
                       "                : net.minecraft.world.InteractionResult.PASS;\n")
        else:
            use.append("        return super.use(state_, level_, pos_, player_, hand_, hit_);\n")
        use.append("    }\n")
        out.append(text[pos:m.end()] + body + "".join(use) + "}")
        pos = end
    out.append(text[pos:])
    return "".join(out)


def drop_codecs(text: str) -> str:
    """A block's codec (1.20.3's, for the block type registry; the 1.21.1 build's are MapCodec.unit(this)): 1.20.1 has none."""
    return method_bodies(text, r"(?:@Override\s*\n\s*)?(?:public|protected) (?:com\.mojang\.serialization\.)?MapCodec<[^{;]*?> codec\(\)\s*\{",
                         lambda method: "" if re.search(r"MapCodec\.unit\(this\)|simpleCodec\(", method) else method)


def recipes(text: str) -> str:
    """A recipe of the mod's: 1.20.1's recipes report their id (kept by dev.groundburst.legacy.recipe.Recipes as the
    serializer reads them) and are given the registries' access (1.21: a lookup)."""
    text = re.sub(r"\bnet\.neoforged\.neoforge\.network\.codec\.NeoForgeStreamCodecs\.composite\(", "dev.groundburst.port.StreamCodecs.composite(", text)
    text = re.sub(r"\bNeoForgeStreamCodecs\.composite\(", "dev.groundburst.port.StreamCodecs.composite(", text)
    if not re.search(r"\bimplements\s+(?:[\w.]+\.)?Recipe<", text):
        return text
    text = re.sub(r"(\bpublic ItemStack (?:assemble|getResultItem)\([^)]*?)(?:net\.minecraft\.core\.)?HolderLookup\.Provider\b",
                  r"\1net.minecraft.core.RegistryAccess", text)
    if not re.search(r"\bResourceLocation getId\(\)", text):
        m = re.search(r"\bimplements\s+(?:[\w.]+\.)?Recipe<[^{]*\{", text)
        if m:
            text = (text[:m.end()] + "\n    @Override\n    public net.minecraft.resources.ResourceLocation getId() {\n"
                    f"        return {L}.recipe.Recipes.idOf(this);\n    }}\n" + text[m.end():])
    return text


def player_will_destroy(text: str) -> str:
    """1.21's playerWillDestroy answers with the state; 1.20.1's answers nothing."""
    def fix(method: str) -> str:
        method = re.sub(r"\b(?:net\.minecraft\.world\.level\.block\.state\.)?BlockState playerWillDestroy\(", "void playerWillDestroy(", method, count=1)
        method = re.sub(r"\breturn (super\.playerWillDestroy\([^;]*\));", r"\1; return;", method)
        method = re.sub(r"\breturn [^;]+;", "return;", method)
        return method
    return method_bodies(text, r"\b(?:public|protected) (?:net\.minecraft\.world\.level\.block\.state\.)?BlockState playerWillDestroy\([^)]*\)\s*\{", fix)


# The game's own screens a mod's screen may extend.
VANILLA_SCREEN = r"(?:net\.minecraft\.client\.gui\.screens\.(?:inventory\.)?)?(?:Screen|AbstractContainerScreen)"


def top_level(body: str, pattern: str) -> list:
    """The matches of {pattern} in a class's {body} that are its own members' (outside any nested braces)."""
    depth, i, marks = 0, 0, {}
    while i < len(body):
        j = skip(body, i)
        if j != i:
            i = j
            continue
        marks[i] = depth
        if body[i] == "{":
            depth += 1
        elif body[i] == "}":
            depth -= 1
        i += 1
    return [m for m in re.finditer(pattern, body) if marks.get(m.start(), -1) == 0]


def vanilla_screen_body(body: str) -> str:
    """The body of a screen that extends the game's own: its 1.21 background drawn from its render, where 1.21's
    Screen.render draws it (just before the game's render), and its own background's call to the game's as 1.20.1's."""
    four = r"public void renderBackground\(\s*" + param(QN + "GuiGraphics") + C + param("int") + C + param("int") + C + param("float") + r"\s*\)\s*\{"
    own = top_level(body, four)
    if own:
        m = own[0]
        end = find_close(body, m.end())
        inner = calls(body[m.end():end], r"\bsuper\.renderBackground\(",
                      lambda c, a: f"super.renderBackground({split_args(a)[0].strip()})" if len(split_args(a)) == 4 else c.group(0) + a + ")")
        body = body[:m.end()] + inner + body[end:]
    render = top_level(body, r"public void render\(\s*" + param(QN + "GuiGraphics") + C + param("int") + C + param("int") + C + param("float")
                       + r"\s*\)\s*\{")
    if render:
        m = render[0]
        end = find_close(body, m.end())
        method = body[m.end():end]
        call = re.search(r"([ \t]*)super\.render\(", method)
        assert call, "port.py: a screen's render that never calls the game's"
        args = method[call.end():find_close(method, call.end()) - 1]
        background = f"this.renderBackground({args if own else split_args(args)[0].strip()});"
        method = method[:call.start()] + f"{call.group(1)}{background}   // (1.20.1's Screen.render draws no background)\n" + method[call.start():]
        return body[:m.end()] + method + body[end:]
    background = "this.renderBackground(graphics, mouseX, mouseY, partial);" if own else "this.renderBackground(graphics);"
    close = len(body.rstrip())                             # (the body's own end, before the class's closing brace)
    return (body[:close] + "\n\n    // 1.20.1's Screen.render draws no background (1.20.2's and later draw it there).\n    @Override\n"
            "    public void render(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partial) {\n"
            f"        {background}\n        super.render(graphics, mouseX, mouseY, partial);\n    }}" + body[close:])


def screens(text: str) -> str:
    """Screens: 1.20.1's background (the game's renderBackground) takes the graphics alone, and its Screen.render draws
    none (1.20.2 and later draw a screen's background from render). A screen's own 1.21 background (the four-argument
    renderBackground) stays its own method, drawn from render by the screen that extends the game's; its scroll takes
    one amount (1.20.2 split it in two)."""
    for m in reversed(list(re.finditer(r"\bclass\s+\w+(?:<[^{]*?>)?\s+extends\s+" + VANILLA_SCREEN + r"\b(?:<[^{]*?>)?[^{;]*\{", text))):
        end = find_close(text, m.end())
        text = text[:m.end()] + vanilla_screen_body(text[m.end():end - 1]) + text[end - 1:]
    # (1.20.1's Screen has no four-argument renderBackground for it to override.)
    text = re.sub(r"[ \t]*@Override[ \t]*\n([ \t]*public void renderBackground\(\s*" + QN + r"GuiGraphics\s+\w+\s*,\s*int\b)", r"\1", text)
    text = re.sub(r"\b(?:this\.)?renderTransparentBackground\((\w+)\);", r"this.renderBackground(\1);", text)
    text = re.sub(r"(\bpublic boolean mouseScrolled\(\s*" + param("double") + C + param("double") + r")" + C + param("double") + C + param("double")
                  + r"\s*\)(\s*\{)", r"\1, double \5)\6 double \4 = 0;", text)
    text = calls(text, r"\bsuper\.mouseScrolled\(",
                 lambda m, a: "super.mouseScrolled(" + ", ".join(x.strip() for x in (split_args(a)[0], split_args(a)[1], split_args(a)[3])) + ")"
                 if len(split_args(a)) == 4 else m.group(0) + a + ")")
    return text


def synced_data(text: str) -> str:
    """An entity's synced data: 1.20.1 defines it on the entity's own data, not on a builder."""
    m = re.search(r"\bprotected void defineSynchedData\(\s*" + param(QN + r"SynchedEntityData\.Builder") + r"\s*\)", text)
    if not m:
        return text
    name = m.group(1)
    text = text[:m.start()] + "protected void defineSynchedData()" + text[m.end():]
    text = re.sub(rf"\b{name}\.define\(", "this.entityData.define(", text)
    text = re.sub(rf"\bsuper\.defineSynchedData\(\s*{name}\s*\)", "super.defineSynchedData()", text)
    return text


def pathfinding(text: str) -> str:
    """1.20.1 asks whether a block can be walked through in a place."""
    text = re.sub(r"\bprotected boolean isPathfindable\(\s*" + param("BlockState") + C + param(QN + "PathComputationType") + r"\s*\)",
                  r"public boolean isPathfindable(BlockState \1, net.minecraft.world.level.BlockGetter level_, BlockPos pos_, "
                  r"net.minecraft.world.level.pathfinder.PathComputationType \2)", text)
    text = re.sub(r"\bsuper\.isPathfindable\((\w+), (\w+)\)", r"super.isPathfindable(\1, level_, pos_, \2)", text)
    return text


def saved_data_save(text: str) -> str:
    """1.20.1's saved data saves with no registries."""
    if not re.search(r"\bextends SavedData\b", text):
        return text
    return re.sub(r"(\bpublic (?:net\.minecraft\.nbt\.)?CompoundTag save\(\s*" + param(QN + "CompoundTag") + r")" + C
                  + param(QN + r"HolderLookup\.Provider") + r"\s*\)(\s*\{)",
                  rf"\1)\4 net.minecraft.core.HolderLookup.Provider \3 = {REGISTRIES};", text)


def effects(text: str) -> str:
    """Mob effects: 1.20.1's tick says nothing back, and asks a different question of the duration."""
    text = re.sub(r"\bpublic boolean shouldApplyEffectTickThisTick\(", "public boolean isDurationEffectTick(", text)
    def tick(method: str) -> str:
        method = re.sub(r"\bpublic boolean applyEffectTick\(", "public void applyEffectTick(", method, count=1)
        method = re.sub(r"\breturn (super\.applyEffectTick\([^;]*\));", r"\1; return;", method)
        method = re.sub(r"\breturn (?:true|false);", "return;", method)
        return method
    return method_bodies(text, r"\bpublic boolean applyEffectTick\([^)]*\)\s*\{", tick)


def use_duration(text: str) -> str:
    """1.20.1 asks how long an item is used without who uses it."""
    return re.sub(r"(\bpublic int getUseDuration\(\s*" + param(QN + "ItemStack") + r")" + C + param(QN + "LivingEntity") + r"\s*\)(\s*\{)",
                  r"\1)\4 net.minecraft.world.entity.LivingEntity \3 = null;", text)


def save_calls(text: str) -> str:
    """Saves and loads through a block entity's parent: 1.20.1's take the tag alone (load is 1.20.1's loadAdditional)."""
    text = calls(text, r"\bsuper\.saveAdditional\(",
                 lambda m, a: f"super.saveAdditional({split_args(a)[0].strip()})" if len(split_args(a)) == 2 else m.group(0) + a + ")")
    text = calls(text, r"\bsuper\.loadAdditional\(",
                 lambda m, a: f"super.load({split_args(a)[0].strip()})" if len(split_args(a)) == 2 else m.group(0) + a + ")")
    text = calls(text, r"\b(this\.)?loadAdditional\(",
                 lambda m, a: f"{m.group(1) or ''}load({split_args(a)[0].strip()})" if len(split_args(a)) == 2 else m.group(0) + a + ")")
    text = calls(text, r"\b(this\.)?saveAdditional\(",
                 lambda m, a: f"{m.group(1) or ''}saveAdditional({split_args(a)[0].strip()})" if len(split_args(a)) == 2 else m.group(0) + a + ")")
    text = calls(text, r"\bsaveCustomOnly\(", lambda m, a: "saveWithoutMetadata()")
    return text


def misc_calls(text: str) -> str:
    """Single calls 1.20.1 makes otherwise."""
    text = receiver_calls(text, "createSerializationContext", lambda recv, args: ("net.minecraft.resources.RegistryOps.create(", f"{args}, {recv})"))
    text = receiver_calls(text, "invalidateCapabilities", lambda recv, args: (f"{L}.Caps.invalidate(", f"{recv}, {args})"))
    text = receiver_calls(text, "igniteForSeconds", lambda recv, args: ("", f"{recv}.setSecondsOnFire((int) ({args}))"))
    text = receiver_calls(text, "consume", lambda recv, args: (f"{L}.Items2.consume(", f"{recv}, {args})"),
                          lambda recv, args: len(split_args(args)) == 2 and bool(re.search(r"(?i)stack|held|item", recv)))
    text = receiver_calls(text, "hasInfiniteMaterials", lambda recv, args: ("", f"{recv}.getAbilities().instabuild"),
                          lambda recv, args: not args.strip())
    text = receiver_calls(text, "withColor", lambda recv, args: ("", f"{recv}.withStyle(s_ -> s_.withColor({args}))"),
                          lambda recv, args: bool(re.search(r"Component\.|literal\(|translatable\(|\.copy\(\)|empty\(\)|append\(", recv)))
    text = re.sub(r"\.listOf\(\s*\d+\s*,\s*\d+\s*\)", ".listOf()", text)
    text = re.sub(r"\bmakeMockPlayer\([^()]*\)", "makeMockPlayer()", text)
    text = re.sub(r"\.getOrThrow\(\)", ".getOrThrow(false, error_ -> {})", text)
    text = re.sub(r"\bisSameItemSameComponents\(", "isSameItemSameTags(", text)
    text = re.sub(r"\bItemStack\.OPTIONAL_CODEC\b", f"{L}.net.Codecs.OPTIONAL_ITEM_STACK_CODEC", text)
    text = re.sub(r"\bFluidStack\.OPTIONAL_CODEC\b", f"{L}.net.Codecs.OPTIONAL_FLUID_STACK_CODEC", text)
    text = re.sub(r"\b(?:net\.minecraft\.world\.item\.)?Item\.ABSOLUTE_MAX_STACK_SIZE\b", "64", text)
    text = re.sub(r"\bBlocks\.SHORT_GRASS\b", "Blocks.GRASS", text)
    text = re.sub(r"\bTerrainAdjustment\.ENCAPSULATE\b", "TerrainAdjustment.BEARD_BOX", text)
    text = re.sub(r"\bSoundType\.COPPER_GRATE\b", "SoundType.COPPER", text)
    text = re.sub(r"\bOperation\.ADD_VALUE\b", "Operation.ADDITION", text)
    text = re.sub(r"\bOperation\.ADD_MULTIPLIED_BASE\b", "Operation.MULTIPLY_BASE", text)
    text = re.sub(r"\bOperation\.ADD_MULTIPLIED_TOTAL\b", "Operation.MULTIPLY_TOTAL", text)
    text = re.sub(r"\b(?:net\.minecraftforge\.event\.)?EventHooks\b", "net.minecraftforge.event.ForgeEventFactory", text)
    # An entity's or block entity's registries: 1.20.1's have none of their own (dev.groundburst.legacy.Regs).
    text = re.sub(r"\bthis\.registryAccess\(\)", f"{L}.Regs.access()", text)
    text = re.sub(r"(?<![\w.])registryAccess\(\)", f"{L}.Regs.access()", text)
    # 1.21 built placement modifier and structure types on map codecs; 1.20.1 on plain ones.
    text = re.sub(r"\(\)\s*->\s*\(\)\s*->\s*([\w.]+)\.CODEC\)", r"() -> () -> \1.CODEC.codec())", text)
    # Spawning and solidity tests: 1.20.1 keeps Blocks.never to itself.
    text = re.sub(r"\.isValidSpawn\(\s*Blocks::never\s*\)", ".isValidSpawn((s_, l_, p_, e_) -> false)", text)
    text = re.sub(r"\bBlocks::never\b", "(s_, l_, p_) -> false", text)
    # A bucket's fluid: Forge 1.20.1 hands it out (its field is private).
    if "BucketItem" in text:
        text = re.sub(r"(?<![\w.])([a-z]\w*)\.content\b(?!\s*\()", r"\1.getFluid()", text)
    text = calls(text, r"\bDirection\.getNearest\(",
                 lambda m, a: f"{L}.Dirs.nearest({a})" if len(split_args(a)) == 1 else m.group(0) + a + ")")
    text = re.sub(r"\bCodec\.withAlternative\(", f"{L}.Codecs2.withAlternative(", text)
    # The mod's blocks' and items' holders given to 1.20.1's is(...): the objects themselves.
    text = re.sub(r"\.is\(([\w.]+\.(?:item|block)\(\))\)", r".is(\1.get())", text)
    text = receiver_calls(text, "getComponents", lambda recv, args: (f"{L}.component.DataComponentMap.of(", f"{recv})"),
                          lambda recv, args: not args.strip())
    text = re.sub(r"\.saveWithFullMetadata\([^()]*(?:\([^()]*\))?[^()]*\)", ".saveWithFullMetadata()", text)
    # The frame's partial tick: 1.20.1's Minecraft keeps it itself.
    text = re.sub(r"\.getTimer\(\)\.getGameTimeDeltaPartialTick\([^()]*\)", ".getFrameTime()", text)
    text = re.sub(r"\.getTimer\(\)\.getGameTimeDeltaTicks\(\)", ".getDeltaFrameTime()", text)
    # Sound events 1.21 holds in holders and 1.20.1 does not.
    text = re.sub(r"\bSoundEvents\.(\w+)\.value\(\)", lambda m: m.group(0) if m.group(1) in sound_holders() else f"SoundEvents.{m.group(1)}", text)
    # The mod's mob effects (held by DeferredHolders, given to 1.21's effect methods as holders): the effects themselves.
    for cls, name in effect_holders():
        text = re.sub(rf"\b({cls}\.{name})\.value\(\)", r"\1.get()", text)
        text = re.sub(rf"\b({cls}\.{name})\b(?!\s*[.=(]|\s*,\s*\"|\w)", r"\1.get()", text)
        if re.search(rf"\bclass {cls}\b", text):
            text = re.sub(rf"(?<![\w.])({name})\b(?!\s*[.=(]|\w)", r"\1.get()", text)
    text = re.sub(r"\bMobEffects\.(\w+)\.value\(\)", r"MobEffects.\1", text)
    # Damage of a type: 1.20.1 keeps DamageSources.source to itself (dev.groundburst.legacy.Game.source).
    text = receiver_calls(text, "source", lambda recv, args: (f"{L}.Game.source(", f"{recv[:-len('.damageSources()')]}, {args})"),
                          lambda recv, args: recv.endswith(".damageSources()"))
    text = receiver_calls(text, "source", lambda recv, args: (f"{L}.Game.source(", f"this, {args})"),
                          lambda recv, args: recv in ("damageSources()", "this.damageSources()"))
    # A config list's default element (NeoForge's, for its config screen): Forge 1.20.1 takes none.
    text = calls(text, r"\.defineListAllowEmpty\(", lambda m, a: (".defineListAllowEmpty(" + ", ".join(x.strip() for x in (split_args(a)[0], split_args(a)[1], split_args(a)[3])) + ")")
                 if len(split_args(a)) == 4 else m.group(0) + a + ")")
    text = re.sub(r"(\w+) instanceof (?:net\.minecraft\.world\.entity\.vehicle\.)?VehicleEntity\b(?!\s+\w)", rf"{L}.Game.isVehicle(\1)", text)
    text = re.sub(r"\bItemStack\.hashItemAndComponents\(", f"{L}.Game.hashItemAndComponents(", text)
    text = receiver_calls(text, "getRegisteredName", lambda recv, args: (f"{L}.Game.registeredName(", f"{recv})"), lambda recv, args: not args.strip())
    text = re.sub(r"\.getServerDirectory\(\)\.resolve\(", ".getServerDirectory().toPath().resolve(", text)
    text = receiver_calls(text, "transformNormal", lambda recv, args: (f"{L}.Game.transformNormal(", f"{recv}, {args})"),
                          lambda recv, args: len(split_args(args)) == 4)
    text = receiver_calls(text, "copy", lambda recv, args: (f"{L}.Game.copy(", f"{recv})"),
                          lambda recv, args: not args.strip() and bool(re.search(r"(?i)pose$|last\(\)$", recv)))
    text = re.sub(r"\.isSectionCompiled\(", ".isChunkCompiled(", text)
    text = receiver_calls(text, "hasChannel", lambda recv, args: ("", "true"))      # the mod's one channel: there if the mod is
    text = receiver_calls(text, "requestedViewDistance", lambda recv, args: (f"{L}.Game.viewDistance(", f"{recv})"),
                          lambda recv, args: not args.strip())
    text = re.sub(r"\bnet\.minecraft\.world\.level\.chunk\.status\.ChunkStatus\b", "net.minecraft.world.level.chunk.ChunkStatus", text)
    text = receiver_calls(text, "getRecipes", lambda recv, args: (f"{L}.recipe.Recipes.everything(", f"{recv})"),
                          lambda recv, args: not args.strip() and "RecipeManager" in recv)
    text = receiver_calls(text, "openMenu", lambda recv, args: ("net.minecraftforge.network.NetworkHooks.openScreen(",
                                                                f"(net.minecraft.server.level.ServerPlayer) {recv}, {args})"),
                          lambda recv, args: len(split_args(args)) == 2)
    text = re.sub(r"\bContainers\.dropContentsOnDestroy\(", f"{L}.Game.dropContentsOnDestroy(", text)
    text = re.sub(r"\b([\w.()]+)\.getType\(\)\.is\(EntityTypeTags\.UNDEAD\)", rf"{L}.Game.undead(\1)", text)
    text = re.sub(r"\.getScrollDeltaY\(\)", ".getScrollDelta()", text)
    # NeoForge 1.21's common tags by Forge 1.20.1's names.
    for old, new in (("GLASS_BLOCKS", "GLASS"), ("COBBLESTONES", "COBBLESTONE"), ("SANDSTONE_BLOCKS", "SANDSTONE"), ("GRAVELS", "GRAVEL"),
                     ("STONES", "STONE"), ("OBSIDIANS", "OBSIDIAN"), ("SANDS", "SAND")):
        text = re.sub(rf"\bTags\.(Blocks|Items)\.{old}\b", rf"Tags.\1.{new}", text)
    text = re.sub(r"\bTags\.Blocks\.CONCRETES\b", f"{L}.Tags2.CONCRETES", text)
    text = re.sub(r"\.blockInteractionRange\(\)", ".getBlockReach()", text)
    text = re.sub(r"\bModelResourceLocation\.standalone\(", "(", text)
    text = re.sub(r"\bnet\.minecraft\.client\.resources\.model\.\(", "(", text)
    text = calls(text, r"\bnew (?:net\.minecraft\.world\.level\.block\.)?DropExperienceBlock\(",
                 lambda m, a: f"new net.minecraft.world.level.block.DropExperienceBlock({split_args(a)[1].strip()}, {split_args(a)[0].strip()})"
                 if len(split_args(a)) == 2 else m.group(0) + a + ")")
    text = calls(text, r"\bnew (?:net\.minecraft\.world\.level\.block\.)?AmethystClusterBlock\(",
                 lambda m, a: "new net.minecraft.world.level.block.AmethystClusterBlock(" + ", ".join(
                     [f"(int) ({split_args(a)[0].strip()})", f"(int) ({split_args(a)[1].strip()})", split_args(a)[2].strip()]) + ")"
                 if len(split_args(a)) == 3 else m.group(0) + a + ")")
    text = receiver_calls(text, "center", lambda recv, args: ("", f"{recv}.getPosition()"),
                          lambda recv, args: not args.strip() and bool(re.search(r"(?i)explosion$", recv)))
    text = receiver_calls(text, "getBlockInteraction", lambda recv, args: ("", f"(({L}.mixin.ExplosionAccess) {recv}).groundburst$interaction()"),
                          lambda recv, args: not args.strip())
    text = calls(text, r"\.saveWithoutMetadata\(", lambda m, a: ".saveWithoutMetadata()")
    text = calls(text, r"\.getUpdateTag\(", lambda m, a: ".getUpdateTag()")
    # A block entity handed its update tag (1.21 hands the registries along too; Forge 1.20.1's takes the tag alone).
    text = calls(text, r"\.handleUpdateTag\(", lambda m, a: f".handleUpdateTag({split_args(a)[0].strip()})" if len(split_args(a)) == 2
                 else m.group(0) + a + ")")
    text = receiver_calls(text, "loadWithComponents", lambda recv, args: ("", f"{recv}.load({split_args(args)[0].strip()})"))
    text = receiver_calls(text, "collectComponents", lambda recv, args: (f"{L}.component.DataComponentMap.collect(", f"{recv})"),
                          lambda recv, args: not args.strip())
    text = receiver_calls(text, "applyComponentsFromItemStack", lambda recv, args: (f"{L}.component.DataComponentInput.apply(", f"{recv}, {args})"))
    text = receiver_calls(text, "canBeHurtBy", lambda recv, args: (f"{L}.Game.canBeHurtBy(", f"{recv}, {args})"))
    text = re.sub(r"\.getDefaultMaxStackSize\(\)", ".getMaxStackSize()", text)
    text = re.sub(r"\bAABB\.encapsulatingFullBlocks\(", f"{L}.Game.encapsulatingFullBlocks(", text)
    text = re.sub(r"(?<![\w.])(?:net\.minecraft\.core\.)?BlockPos\.(min|max)\(", rf"{L}.Game.\1(", text)
    text = calls(text, r"\bnew (?:net\.minecraft\.network\.)?(?:Registry)?FriendlyByteBuf\(",
                 lambda m, a: f"new net.minecraft.network.FriendlyByteBuf({split_args(a)[0].strip()})" if len(split_args(a)) == 2 else m.group(0) + a + ")")
    # A test player: 1.20.1's is made without the client's settings.
    text = re.sub(r",\s*(?:net\.minecraft\.server\.level\.)?ClientInformation\.createDefault\(\)", "", text)
    text = calls(text, r"\bnew (?:net\.minecraft\.server\.network\.)?ServerGamePacketListenerImpl\(",
                 lambda m, a: "new net.minecraft.server.network.ServerGamePacketListenerImpl(" + ", ".join(x.strip() for x in split_args(a)[:3]) + ")"
                 if len(split_args(a)) == 4 else m.group(0) + a + ")")
    text = re.sub(r"\bhelper\.getBounds\(\)", f"(({L}.mixin.GameTestHelperAccess) helper).groundburst$bounds()", text)
    # Fluid stacks: Forge 1.20.1's names for NeoForge's calls.
    text = re.sub(r"\bFluidStack\.isSameFluidSameComponents\(([^,()]+), ([^,()]+)\)", r"\1.isFluidStackIdentical(\2)", text)
    text = re.sub(r"\bFluidStack\.isSameFluid\(([^,()]+), ([^,()]+)\)", r"\1.isFluidEqual(\2)", text)
    for name in set(re.findall(r"\bFluidStack (\w+)\b", text)):
        text = re.sub(rf"\b{name}\.getHoverName\(\)", f"{name}.getDisplayName()", text)
        text = re.sub(rf"\b{name}\.getFluidType\(\)", f"{name}.getFluid().getFluidType()", text)
        text = re.sub(rf"\b{name}\.copyWithAmount\(", f"{L}.Game.copyWithAmount({name}, ", text)
    text = re.sub(r"\.isValidSpawn\(\s*Blocks::ocelotOrParrot\s*\)",
                  ".isValidSpawn((s_, l_, p_, e_) -> e_ == net.minecraft.world.entity.EntityType.OCELOT || e_ == net.minecraft.world.entity.EntityType.PARROT)",
                  text)
    # GeckoLib bones set three axes at once (GeckoLib 4.8 on 1.20.1: one by one, and marked: dev.groundburst.legacy.client.Geo).
    for op in ("updateRotation", "updatePosition", "updateScale"):
        text = receiver_calls(text, op, lambda recv, args, op=op: (f"{L}.client.Geo.{op}(", f"{recv}, {args})"),
                              lambda recv, args: len(split_args(args)) == 3)
    # A new world made by a dev tool: 1.20.1's has no screen to come back to.
    text = calls(text, r"\.createFreshLevel\(", lambda m, a: ".createFreshLevel(" + ", ".join(x.strip() for x in split_args(a)[:4]) + ")"
                 if len(split_args(a)) == 5 else m.group(0) + a + ")")
    text = re.sub(r"\.saturationModifier\(", ".saturationMod(", text)
    text = re.sub(r"\.alwaysEdible\(\)", ".alwaysEat()", text)
    text = re.sub(PK + r"FluidStack::getFluidHolder\b", f"{L}.FluidStacks::holder", text)
    # A recipe's result (1.20.1 asks it with the registries' access: the server's or the client's).
    text = calls(text, r"\.getResultItem\(", lambda m, a: f".getResultItem({L}.Regs.access())" if a.strip() else m.group(0) + a + ")")
    # 1.20.5's default gravity of an entity (1.20.1's entities fall as their own code moves them).
    text = re.sub(r"@Override\s*\n(\s*)(protected|public) double getDefaultGravity\(", r"\1\2 double getDefaultGravity(", text)
    # A fluid block picked up in a bucket: 1.20.1 is not told by whom.
    text = re.sub(r"(\bpublic ItemStack pickupBlock\(\s*)" + param(r"(?:@[\w.]+\s+)?" + QN + "Player") + C + r"(" + param(QN + "LevelAccessor") + C
                  + param("BlockPos") + C + param("BlockState") + r"\s*\)\s*\{)",
                  r"\1\3 net.minecraft.world.entity.player.Player \2 = null;", text)
    # A fuel's burning time: NeoForge's stack.getBurnTime(type) is the whole answer; Forge 1.20.1's is only the item's
    # own (-1 for vanilla's fuels, coal among them), ForgeHooks's the whole.
    text = receiver_calls(text, "getBurnTime", lambda recv, args: ("net.minecraftforge.common.ForgeHooks.getBurnTime(", f"{recv}, {args})"),
                          lambda recv, args: len(split_args(args)) == 1)
    return text


_EFFECT_HOLDERS: list | None = None


def effect_holders() -> list:
    """(class, field) of every DeferredHolder of a mob effect in the 1.21.1 build."""
    global _EFFECT_HOLDERS
    if _EFFECT_HOLDERS is None:
        _EFFECT_HOLDERS = []
        for path in source_files():
            if path.endswith(".java") and path.startswith("src/main/java/"):
                text = read_source(path).decode("utf-8")
                cls = path.rsplit("/", 1)[1][:-5]
                for m in re.finditer(r"DeferredHolder<MobEffect,\s*[\w.<>]+>\s+(\w+)\s*=", text):
                    _EFFECT_HOLDERS.append((cls, m.group(1)))
    return _EFFECT_HOLDERS


_SOUND_HOLDERS: set | None = None


def sound_holders() -> set:
    """Vanilla 1.20.1's sound events kept in holders (the rest are plain): read from Forge's sources the build made."""
    global _SOUND_HOLDERS
    if _SOUND_HOLDERS is None:
        import zipfile
        _SOUND_HOLDERS = set()
        jar = ROOT / "build" / "moddev" / "artifacts" / "forge-1.20.1-47.4.28-sources.jar"
        if jar.exists():
            with zipfile.ZipFile(jar) as z:
                text = z.read("net/minecraft/sounds/SoundEvents.java").decode("utf-8")
            _SOUND_HOLDERS = set(re.findall(r"Holder\.Reference<SoundEvent> (\w+) =", text))
    return _SOUND_HOLDERS


SET_TYPE = r"(BlockSetType\.\w+|[\w.]*\.blockSetType\(\)|\w*[Ss]et(?:Type)?(?:\.get\(\))?|[a-z]\w*Type)"


def block_set_types(text: str) -> str:
    """1.20.1's doors and trapdoors take their properties first, then their block set type."""
    text = re.sub(r"\bnew (?:net\.minecraft\.world\.level\.block\.)?(DoorBlock|TrapDoorBlock)\(" + SET_TYPE + r", ((?:[^,()]|\([^()]*\))+)\)",
                  r"new net.minecraft.world.level.block.\1(\3, \2)", text)
    if re.search(r"\bextends (?:net\.minecraft\.world\.level\.block\.)?(?:DoorBlock|TrapDoorBlock)\b", text):
        text = re.sub(r"\bsuper\(" + SET_TYPE + r", ((?:[^,()]|\([^()]*\))+)\);", r"super(\2, \1);", text)
    return text


BLOCK_BEHAVIOUR_PUBLIC = ("attack", "canBeReplaced", "canSurvive", "entityInside", "getAnalogOutputSignal", "getBlockSupportShape",
                          "getCollisionShape", "getDestroyProgress", "getDirectSignal", "getDrops", "getFluidState", "getInteractionShape",
                          "getLightBlock", "getMenuProvider", "getOcclusionShape", "getRenderShape", "getSeed", "getShadeBrightness", "getShape",
                          "getSignal", "getVisualShape", "hasAnalogOutputSignal", "isPathfindable", "isSignalSource", "mirror", "neighborChanged",
                          "onPlace", "onProjectileHit", "onRemove", "randomTick", "rotate", "skipRendering", "spawnAfterBreak", "tick",
                          "triggerEvent", "updateIndirectNeighbourShapes", "updateShape", "use", "useShapeForLightOcclusion",
                          "propagatesSkylightDown", "getCloneItemStack", "isRandomlyTicking")


def block_access(text: str) -> str:
    """1.20.3 made a block's behaviour methods protected; 1.20.1's are public (an override may not narrow them)."""
    if not is_block_class(text):
        return text
    return re.sub(r"\bprotected ((?:final )?[\w.<>?\[\], ]+? (?:" + "|".join(BLOCK_BEHAVIOUR_PUBLIC) + r")\()", r"public \1", text)


def render_boxes(text: str) -> str:
    """A renderer's culling box (NeoForge 1.21 asks the renderer): this branch's RenderBox, asked in its place on 1.20.1."""
    m = re.search(r"(?:@Override\s*\n\s*)?public (?:net\.minecraft\.world\.phys\.)?AABB getRenderBoundingBox\(\s*([\w.<>?]+) \w+\s*\)", text)
    if not m:
        return text
    kind = m.group(1)
    text = text[:m.start()] + re.sub(r"^@Override\s*\n\s*", "", m.group(0)) + text[m.end():]

    def implement(c: re.Match) -> str:
        head = c.group(0)
        if " implements " in head:
            return head.replace(" implements ", f" implements {L}.client.RenderBox<{kind}>, ", 1)
        return head[:-1].rstrip() + f" implements {L}.client.RenderBox<{kind}> {{"
    return re.sub(r"\bclass \w+(?:<[^{]*?>)?(?:\s+extends\s+[\w.<>?, ]+?)?(?:\s+implements\s+[\w.<>?, ]+?)?\s*\{", implement, text, count=1)


def block_sources(text: str) -> str:
    """A dispenser's block source: 1.21's record, 1.20.1's interface with getters."""
    for name in set(re.findall(r"\bBlockSource (\w+)\b", text)):
        for old, new in (("state", "getBlockState"), ("level", "getLevel"), ("pos", "getPos"), ("blockEntity", "getEntity")):
            text = re.sub(rf"\b{name}\.{old}\(\)", f"{name}.{new}()", text)
        text = re.sub(rf"\b{name}\.center\(\)", f"net.minecraft.world.phys.Vec3.atCenterOf({name}.getPos())", text)
    # A pose's copy (1.20.1's has none).
    for name in set(re.findall(r"\b(?:PoseStack\.)?Pose (\w+)\b", text)):
        text = re.sub(rf"\b{name}\.copy\(\)", f"{L}.Game.copy({name})", text)
    return text


def implicit_components(text: str) -> str:
    """A block entity's data kept in its item (1.21's implicit components): this branch's ImplicitComponents."""
    if "collectImplicitComponents" not in text and "applyImplicitComponents" not in text:
        return text
    text = re.sub(r"(?:@Override\s*\n\s*)?protected void (collectImplicitComponents|applyImplicitComponents)\(", r"public void \1(", text)
    text = re.sub(r"\n\s*super\.(?:collectImplicitComponents|applyImplicitComponents)\(\w+\);", "", text)
    text = re.sub(r"\b(?:net\.minecraft\.world\.level\.block\.entity\.)?BlockEntity\.DataComponentInput\b", f"{L}.component.DataComponentInput", text)
    def implement(m: re.Match) -> str:
        head = m.group(0)
        if " implements " in head:
            return head.replace(" implements ", f" implements {L}.component.ImplicitComponents, ", 1)
        return head[:-1].rstrip() + f" implements {L}.component.ImplicitComponents {{"
    return re.sub(r"\bpublic (?:abstract )?class \w+(?:<[^{]*?>)?\s+extends\s+[\w.<>?, ]+?\{", implement, text, count=1)


def java17(text: str) -> str:
    """Java 17 refuses a type pattern its expression's type already is (Java 21 allows it): the expression seen as an Object."""
    out, pos = [], 0
    for m in re.finditer(r"\binstanceof\s+(?:final\s+)?[\w.]+(?:<[^>]*>)?\s+[a-z_]\w*\b(?!\s*[.(])", text):
        if m.start() < pos:
            continue
        end = m.start()
        while end > 0 and text[end - 1] in " \t":
            end -= 1
        start = receiver_start(text, end)
        expr = text[start:end]
        if not expr.strip() or expr.strip().startswith("(Object)"):
            continue
        out.append(text[pos:start] + "((Object) " + expr.strip() + ") ")
        pos = m.start()
    out.append(text[pos:])
    return "".join(out)


def attribute_modifiers(text: str) -> str:
    """1.21 names an attribute modifier by an id; 1.20.1 by a UUID (made from the id: dev.groundburst.legacy.Attrs)."""
    text = calls(text, r"\baddAttributeModifier\(", lambda m, a: (
        f"addAttributeModifier({split_args(a)[0].strip()}, {L}.Attrs.uuid({split_args(a)[1].strip()}), "
        + ", ".join(x.strip() for x in split_args(a)[2:]) + ")")
        if len(split_args(a)) == 4 and not split_args(a)[1].strip().startswith('"') else m.group(0) + a + ")")
    text = calls(text, r"\bnew (?:net\.minecraft\.world\.entity\.ai\.attributes\.)?AttributeModifier\(",
                 lambda m, a: f"{L}.Attrs.modifier({a})" if len(split_args(a)) == 3 else m.group(0) + a + ")")
    return text


def patches(text: str) -> str:
    """Component patches (1.21): on 1.20.1 a stack's tag (dev.groundburst.legacy.component.DataComponentPatch)."""
    P = f"{L}.component.DataComponentPatch"
    text = receiver_calls(text, "getComponentsPatch", lambda recv, args: (f"{P}.of(", f"{recv})"), lambda recv, args: not args.strip())
    text = receiver_calls(text, "applyComponents", lambda recv, args: ("", f"{args.strip()}.applyTo({recv})"))
    text = receiver_calls(text, "getFluidHolder", lambda recv, args: (f"{L}.FluidStacks.holder(", f"{recv})"),
                          lambda recv, args: not args.strip())
    # 1.21's three-argument stacks are a holder, a count and a patch.
    text = calls(text, r"\bnew (?:net\.minecraft\.world\.item\.)?ItemStack\(",
                 lambda m, a: f"{P}.stack({a})" if len(split_args(a)) == 3 else m.group(0) + a + ")")
    text = calls(text, r"\bnew (?:net\.minecraftforge\.fluids\.)?FluidStack\(",
                 lambda m, a: f"{P}.fluid({a})" if len(split_args(a)) == 3 else m.group(0) + a + ")")
    return text


def class_name(text: str) -> tuple[str, str] | None:
    m = re.search(r"\bclass (\w+)(?:<[^{]*?>)?\s+extends\s+([\w.]+)", text)
    return (m.group(1), m.group(2).split(".")[-1]) if m else None


def is_block_class(text: str) -> bool:
    """Whether the file's class is a block (by its parents' names), not an item."""
    c = class_name(text)
    if not c:
        return False
    name, parent = c
    idx = class_index()
    seen = set()
    while True:
        if parent.endswith("Block"):
            return True
        if parent.endswith("Item") or parent not in idx or parent in seen:
            return False
        seen.add(parent)
        parent = idx[parent][0]


def tooltips(text: str) -> str:
    """1.21 hands a tooltip a context; 1.20.1 an item the level, a block what it is in (dev.groundburst.legacy.TooltipContext)."""
    T = f"{L}.TooltipContext"
    if "TooltipContext" not in text and "getTooltipLines" not in text:
        return text
    where = "net.minecraft.world.level.BlockGetter" if is_block_class(text) else "net.minecraft.world.level.Level"
    text = re.sub(r"(\bpublic void appendHoverText\(\s*" + param(QN + "ItemStack") + C + r")" + param(QN + r"(?:Item\.)?TooltipContext")
                  + r"(" + C + param(QN + r"List<[^>]*>") + C + param(QN + "TooltipFlag") + r"\s*\)\s*\{)",
                  rf"\1{where} \3_level\4 {T} \3 = {T}.of(\3_level);", text)
    text = re.sub(r"\bsuper\.appendHoverText\((\w+), (\w+), (\w+), (\w+)\)", r"super.appendHoverText(\1, \2_level, \3, \4)", text)
    text = re.sub(r"\b(?:net\.minecraft\.world\.item\.)?Item\.TooltipContext\b", T, text)
    text = re.sub(r"(?<![\w.])TooltipContext\.(of|EMPTY)\b", rf"{T}.\1", text)
    text = receiver_calls(text, "getTooltipLines", lambda recv, args: ("", f"{recv}.getTooltipLines({', '.join(x.strip() for x in split_args(args)[1:])})"),
                          lambda recv, args: len(split_args(args)) == 3)
    return text


def vertex_call(args: str) -> str:
    p = [x.strip() for x in split_args(args)]
    if len(p) == 4:
        return f".vertex({L}.Verts.matrix({p[0]}), {p[1]}, {p[2]}, {p[3]})"
    return f".vertex({', '.join(p)})"


def normal_call(args: str) -> str:
    p = [x.strip() for x in split_args(args)]
    if len(p) == 4:
        return f".normal({L}.Verts.normal({p[0]}), {p[1]}, {p[2]}, {p[3]})"
    return f".normal({', '.join(p)})"


def vertices(text: str) -> str:
    """1.21's vertices (addVertex, then any of setColor, setUv, setOverlay, setLight, setNormal) as 1.20.1's (vertex, color,
    uv, overlayCoords, uv2, normal, in that order, and endVertex). 1.21's one-call vertex: dev.groundburst.legacy.Verts.full."""
    if "addVertex(" not in text:
        return text
    text = receiver_calls(text, "addVertex", lambda recv, args: (f"{L}.Verts.full(", f"{recv}, {args})"),
                          lambda recv, args: len(split_args(args)) == 11)
    out, pos = [], 0
    for m in re.finditer(r"\.\s*addVertex\(", text):
        if m.start() < pos:
            continue
        i, depth = m.start(), 0
        while i < len(text):
            j = skip(text, i)
            if j != i:
                i = j
                continue
            ch = text[i]
            if ch in "([{":
                depth += 1
            elif ch in ")]}":
                if depth == 0:
                    break
                depth -= 1
            elif ch == ";" and depth == 0:
                break
            i += 1
        chain = text[m.start():i]
        chain = calls(chain, r"\.\s*addVertex\(", lambda mm, a: vertex_call(a))
        for old, new in (("setColor", "color"), ("setUv1", "overlayCoords"), ("setUv2", "uv2"), ("setUv", "uv"),
                         ("setOverlay", "overlayCoords"), ("setLight", "uv2")):
            chain = re.sub(rf"\.\s*{old}\(", f".{new}(", chain)
        chain = calls(chain, r"\.\s*setNormal\(", lambda mm, a: normal_call(a))
        out.append(text[pos:m.start()] + chain.rstrip() + ".endVertex()")
        pos = i
    out.append(text[pos:])
    return "".join(out)


FUNCTIONS.extend([drop_codecs, block_use, player_will_destroy, screens, synced_data, pathfinding, saved_data_save, effects, use_duration,
                  save_calls, misc_calls, block_set_types, patches, tooltips, vertices, java17, attribute_modifiers, implicit_components,
                  block_sources, block_access, render_boxes, recipes])


FILE_FIXES_BEFORE: dict[str, list] = {
    # Create Aeronautics' ships (Sable) have no 1.20.1 build: the Create support starts without them, and the Create check
    # leaves out its ships' scenes (the ship's launcher, the ship hit, ShipCheck, the rack aboard an airship).
    "src/main/java/dev/groundburst/compat/GBCompat.java": [
        cut("            if (ModList.get().isLoaded(\"sable\")) {\n", "            if (ModList.get().isLoaded(\"create\")) {\n"),
        swap(" * This build's support for other mods' moving structures (main's DESIGN.md §17.16, docs/BACKPORT.md): Sable's sub-levels\n"
             " * (the ships, planes and cars of Create Aeronautics) and Create's contraptions (trains, bearings, pistons, gantries...),\n"
             " * each only when that mod is installed. Nothing here touches either mod's classes unless it is.\n",
             " * This build's support for other mods' moving structures (main's DESIGN.md §17.16, docs/BACKPORT.md): Create's\n"
             " * contraptions (trains, bearings, pistons, gantries...), only when Create is installed (Create Aeronautics' ships, which\n"
             " * the 1.21.1 build supports through Sable, have no 1.20.1 build). Nothing here touches Create's classes unless it is.\n"),
    ],
    "src/main/java/dev/groundburst/compat/CreateCheck.java": [
        cut(" * §17.16): in a flat creative world with Create, Sable and Create Aeronautics loaded,", " */\n@EventBusSubscriber",
            " * §17.16): in a flat creative world with Create loaded, a Cruise Missile Launcher on a Create mechanical bearing\n"
            " * turned by a creative motor (its deck held together and carried) fires, and the log says where its missile lit and\n"
            " * which way it headed against where its box's tail and its boxes' heading are in the world at that moment (\"Create\n"
            " * check: ... OK\" or \"... WRONG\"). Then a factory, an assembler yard and a blasted platform, Create's shafts healed\n"
            " * ({@link HealCheck}), and last a Depth Charge Rack loaded by a Create funnel ({@link RackCheck}). Pictures in\n"
            " * run/screenshots/create_*.png. (The 1.21.1 build's ships' scenes are left out: Create Aeronautics and Sable have no\n"
            " * 1.20.1 build.)\n"),
        cut("            // --- the Sable ship ---", "            case 410 ->"),
        cut("            // --- a ship hit: a blast beside a Sable ship, then a shell into it ---", "            case RackCheck.END ->"),
        swap("                if (ticks >= ShipCheck.START && ticks < ShipCheck.END) {\n"
             "                    ShipCheck.tick(mc, ticks);                   // ships under the slower weapons, and aimed at\n"
             "                } else if (ticks >= HealCheck.START",
             "                if (ticks >= HealCheck.START"),
        # What only the ships' scenes used: the ship's launcher and where it was set down, the watch on a launcher standing
        # in the world (a ship's: the bearing's is carried, checked after its carrier ticks it), the second ship.
        cut("    /** The ship's launcher on a stone platform; where the ship is set down, turned 30 degrees. */\n",
            "    private static boolean started, stopped;\n"),
        swap("            GroundBurst.LOGGER.info(\"Create check: making the world (Create {}, Sable {})\", ModList.get().isLoaded(\"create\"),\n"
             "                    ModList.get().isLoaded(\"sable\"));\n",
             "            GroundBurst.LOGGER.info(\"Create check: making the world (Create {})\", ModList.get().isLoaded(\"create\"));\n"),
        cut("    /** After each server tick: a launcher standing in the world (a ship's) being watched may have lit a missile. */\n",
            "    /** A carried launcher being watched,"),
        cut("    /** The second ship: a 5x2x5 block of planks made a ship,", "\n    /** The wreck's platform, glued:"),
        cut("    /** The launcher aboard the ship: the one near where the ship was set down. */\n",
            "    /** Fires {@code name} (its countdown cut short), watching for its first missile. */\n"),
        swap("// a Depth Charge Rack loaded by a funnel, and aboard an airship\n", "// a Depth Charge Rack loaded by a funnel\n"),
    ],
    "src/main/java/dev/groundburst/compat/RackCheck.java": [
        cut("            // --- a rack aboard an airship ---", "            default -> {"),
        swap("import dev.groundburst.compat.sable.SableScenes;\n", ""),
        # What only the airship's scene used: the ship, its rack, where it was seen, the helpers its log lines used.
        cut("    /** The airship: levitite under a deck of planks,", "\n    private RackCheck() {}\n"),
        cut("\n\n    private static String round(double v) {\n", "\n}\n"),
        swap(" * The Create check's Depth Charge Rack scenes ({@link CreateCheck}; main's DESIGN.md §14.6): a rack on the ground loaded\n"
             " * by a Create funnel on its Item Port (depth charges dropped on the funnel); then a rack aboard a Create Aeronautics\n"
             " * airship turned 30 degrees, its screen's button rolling a charge off from where the ship has the rack, the way the ship\n"
             " * points it, lying across as it lay; and a redstone block put beside it aboard rolling the last one off.\n",
             " * The Create check's Depth Charge Rack scene ({@link CreateCheck}; main's DESIGN.md §14.6): a rack on the ground loaded\n"
             " * by a Create funnel on its Item Port (depth charges dropped on the funnel). (The 1.21.1 build's rack aboard a Create\n"
             " * Aeronautics airship is left out: Create Aeronautics has no 1.20.1 build.)\n"),
    ],
    # A contraption's own frame: the world's here (no ships carry contraptions on 1.20.1).
    "src/main/java/dev/groundburst/compat/create/ContraptionFrame.java": [
        swap(" * place in the contraption), and the contraption has it wherever it has moved and turned it to now. A contraption aboard\n"
             " * a Sable ship (a bearing turning a launcher on an airship) is itself in the ship's plot: the ship's frame then takes it\n"
             " * on into the world.\n",
             " * place in the contraption), and the contraption has it wherever it has moved and turned it to now.\n"),
        swap("    /** The frame the contraption itself is in: the world's, or a ship's. */\n",
             "    /** The frame the contraption itself is in (the world's: nothing on 1.20.1 carries contraptions about). */\n"),
    ],
    "src/main/java/dev/groundburst/compat/create/ContraptionHulls.java": [
        swap("    /** The frame the contraption itself is in: the world's, or a ship's (a bearing turning on an airship). */\n",
             "    /** The frame the contraption itself is in (the world's: nothing on 1.20.1 carries contraptions about). */\n"),
    ],
}
FILE_FIXES: dict[str, list] = {
    # Forge 1.20.1 makes a mod with no arguments: its bus and container are found; this branch's stand-ins start first,
    # and the client half (no mod of its own here) is started by the mod on the client.
    "src/main/java/dev/groundburst/GroundBurst.java": [
        swap("    public GroundBurst(IEventBus modBus, ModContainer modContainer) {\n",
             "    public GroundBurst() {\n"
             "        IEventBus modBus = net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus();\n"
             "        ModContainer modContainer = net.minecraftforge.fml.ModLoadingContext.get().getActiveContainer();\n"
             "        dev.groundburst.legacy.Lifecycle.install(modBus, modContainer);\n"),
        swap("        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, GBCommonConfig.SPEC);\n",
             "        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, GBCommonConfig.SPEC);\n"
             "        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,\n"
             "                () -> () -> new GroundBurstClient(modBus, modContainer));\n"),
    ],
    # Ice that a silk touch tool keeps (1.21 asks the tool's enchantments for a tag; 1.20.1, as vanilla's ice, for silk touch).
    "src/main/java/dev/groundburst/content/fallout/CryoIceBlock.java": [
        swap("EnchantmentHelper.hasTag(destroyedWith, EnchantmentTags.PREVENTS_ICE_MELTING)",
             "EnchantmentHelper.getItemEnchantmentLevel(net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH, destroyedWith) != 0"),
        swap("import net.minecraft.tags.EnchantmentTags;\n", ""),
    ],
    # 1.20.1's test functions: no manual-only or sky access setting, and their names asked by getter.
    "src/main/java/dev/groundburst/gametest/GameTestRegistration.java": [
        swap("0L, true, false,\n                    1, 1, spec.sky(), spec.body()));",
             "0L, true, 1, 1, helper -> {\n"
             "                encase(helper, spec.sky());\n"
             "                spec.body().accept(helper);\n"
             "            }));"),
        swap("f.testName()", "f.getTestName()"),
        # 1.21's test framework walls each test round with barriers, and roofs those with no sky; 1.20.1's leaves them open
        # (a blast in one reached the next): its walls built here as each test starts.
        after("    private GameTestRegistration() {}\n",
              "\n"
              "    /**\n"
              "     * 1.21's walls round a test as it starts (StructureUtils.encaseStructure): barriers just outside its\n"
              "     * structure, and a roof of them over it unless it has the sky. 1.20.1's tests stand open to their neighbours.\n"
              "     */\n"
              "    private static void encase(net.minecraft.gametest.framework.GameTestHelper helper, boolean sky) {\n"
              "        net.minecraft.world.phys.AABB bounds = ((dev.groundburst.legacy.mixin.GameTestHelperAccess) helper).groundburst$bounds();\n"
              "        // (1.20.1's bounds run from the structure block, a block under the structure, a size long: the size is all.)\n"
              "        int sx = (int) Math.round(bounds.maxX - bounds.minX) + 1;\n"
              "        int sy = (int) Math.round(bounds.maxY - bounds.minY) + 1;\n"
              "        int sz = (int) Math.round(bounds.maxZ - bounds.minZ) + 1;\n"
              "        net.minecraft.core.BlockPos corner = helper.absolutePos(net.minecraft.core.BlockPos.ZERO);\n"
              "        net.minecraft.core.BlockPos lo = corner.offset(-1, 0, -1), hi = corner.offset(sx, sy, sz);\n"
              "        for (net.minecraft.core.BlockPos p : net.minecraft.core.BlockPos.betweenClosed(lo, hi)) {\n"
              "            boolean wall = p.getX() == lo.getX() || p.getX() == hi.getX() || p.getZ() == lo.getZ() || p.getZ() == hi.getZ();\n"
              "            if (wall || !sky && p.getY() == hi.getY()) {\n"
              "                helper.getLevel().setBlockAndUpdate(p, net.minecraft.world.level.block.Blocks.BARRIER.defaultBlockState());\n"
              "            }\n"
              "        }\n"
              "    }\n"),
        # (gradlew runGameTestServer -PonlyTests=<name>,<name>: only those, for a look at a few.)
        swap("            if (PortSkips.skipped(spec.name())) {\n",
             "            String only = System.getProperty(\"groundburst.onlyTests\", \"\");\n"
             "            if (!only.isBlank() && !java.util.Arrays.asList(only.split(\",\")).contains(spec.name())) {\n"
             "                continue;\n"
             "            }\n"
             "            if (PortSkips.skipped(spec.name())) {\n"),
    ],
    # 1.20.1's GameTests have no sky access setting (its test structures stand open to the sky).
    "src/main/java/dev/groundburst/port/SmokeTest.java": [
        swap(", skyAccess = true)", ")"),
    ],
    # A charge caught in a blast: 1.20.1 (Forge) tells the block as the blast destroys it.
    "src/main/java/dev/groundburst/content/ordnance/charge/ChargeBlock.java": [
        replace_method("protected void onExplosionHit(",
                       "public void onBlockExploded(BlockState state, net.minecraft.world.level.Level level, BlockPos pos,\n"
                       "            net.minecraft.world.level.Explosion explosion) {\n"
                       "        if (level instanceof net.minecraft.server.level.ServerLevel server) {\n"
                       "            Charges.caughtInBlast(server, pos, state);\n"
                       "        }\n"
                       "        super.onBlockExploded(state, level, pos, explosion);\n"
                       "    }"),
    ],
    # Item and fluid resources (the transfer API's): their components through the stack's tag, a fluid's name its own.
    "src/main/java/dev/groundburst/port/transfer/item/ItemResource.java": [
        swap("        s.set(type, data);\n", f"        {L}.component.Comps.set(s, type, data);\n"),
        swap("        s.remove(type);\n", f"        {L}.component.Comps.remove(s, type);\n"),
    ],
    "src/main/java/dev/groundburst/port/transfer/fluid/FluidResource.java": [
        swap("return toStack(1).getHoverName();", "return toStack(1).getDisplayName();"),
    ],
    # Forge 1.20.1 names the fire's burning of a block next to it tryCatchFire (its own name, not remapped; the calls in
    # it are vanilla's, remapped).
    "src/main/java/dev/groundburst/mixin/FireBlockMixin.java": [
        swap('@Inject(method = "checkBurnOut", at = {', '@Inject(method = "tryCatchFire", remap = false, at = {'),
        swap('setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"),',
             'setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z", remap = true),'),
        swap('removeBlock(Lnet/minecraft/core/BlockPos;Z)Z")})', 'removeBlock(Lnet/minecraft/core/BlockPos;Z)Z", remap = true)})'),
        swap('@WrapMethod(method = "checkBurnOut")', '@WrapMethod(method = "tryCatchFire", remap = false)'),
    ],
    # The GameTests' mock server player: in no player list, as main's, but on a connection that sends nothing (Forge
    # 1.20.1's player inventory wrapper, which hands a player an item, tells a server player's client the slot).
    "src/main/java/dev/groundburst/port/Tests.java": [
        swap("in no one's player list and on no connection (",
             "in no one's player list, on a connection that sends nothing (Forge 1.20.1 hands a server player an item through a\n"
             "     * wrapper that tells its client; "),
        swap("        mode.updatePlayerAbilities(player.getAbilities());\n",
             "        mode.updatePlayerAbilities(player.getAbilities());\n"
             "        net.minecraft.network.Connection nowhere = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND) {\n"
             "            @Override\n"
             "            public void send(net.minecraft.network.protocol.Packet<?> packet) {}\n"
             "\n"
             "            @Override\n"
             "            public void send(net.minecraft.network.protocol.Packet<?> packet, net.minecraft.network.PacketSendListener listener) {}\n"
             "        };\n"
             "        new net.minecraft.server.network.ServerGamePacketListenerImpl(level.getServer(), nowhere, player);   // (it sets player.connection)\n"),
    ],
    # The effects' glow: a 1.20.1 shader sets its own blending as it is used, over the render type's, and the eyes shader's
    # is not additive: the glow draws on this branch's own copy of it that adds (shaders/core/rendertype_fx_glow). Iris on
    # Forge 1.20.1 is Oculus (Iris's code and API under its own mod id).
    "src/main/java/dev/groundburst/client/fx/FxRenderTypes.java": [
        swap('static final boolean IRIS = net.minecraftforge.fml.ModList.get().isLoaded("iris");',
             'static final boolean IRIS = net.minecraftforge.fml.ModList.get().isLoaded("iris")\n'
             '            || net.minecraftforge.fml.ModList.get().isLoaded("oculus");'),
        after("    private static @Nullable ShaderInstance cloudShader;\n",
              "    /** The glow's shader: the eyes shader, its own file blending it additively (1.20.1's shaders set their blending). */\n"
              "    private static @Nullable ShaderInstance glowShader;\n"),
        after("                        shader -> cloudShader = shader);\n",
              "                event.registerShader(new ShaderInstance(event.getResourceProvider(),\n"
              "                        new ResourceLocation(GroundBurst.MODID, \"rendertype_fx_glow\"), DefaultVertexFormat.NEW_ENTITY),\n"
              "                        shader -> glowShader = shader);\n"),
        after("                        : net.minecraft.client.renderer.GameRenderer.getRendertypeEyesShader());\n",
              "        private static final ShaderStateShard GLOW_SHADER = new ShaderStateShard(\n"
              "                () -> packDraws() ? net.minecraft.client.renderer.GameRenderer.getRendertypeEyesShader() : glowShader);\n"),
        swap(".setShaderState(writeDepth ? CLOUD_SHADER : additive ? RENDERTYPE_EYES_SHADER : FIRE_SHADER)",
             ".setShaderState(writeDepth ? CLOUD_SHADER : additive ? GLOW_SHADER : FIRE_SHADER)"),
    ],
    # An item changed in place: its components are its tag on 1.20.1, set back whole.
    "src/main/java/dev/groundburst/port/transfer/Adapters.java": [
        swap(f"        if (((Object) {L}.component.DataComponentMap.of(stack)) instanceof {L}.component.PatchedDataComponentMap map) {{\n"
             "            map.restorePatch(patch);\n"
             "        }\n",
             "        stack.setTag(patch.tag());\n"),
    ],
    "src/main/java/dev/groundburst/port/transfer/energy/ItemAccessEnergyHandler.java": [
        swap("accessResource.toStack(1).get(this.energyComponent)", f"{L}.component.Comps.get(accessResource.toStack(1), this.energyComponent)"),
    ],
    # The launch station's screen, drawn after its model: GeckoLib 4.8 hands the colour on as four channels.
    "src/main/java/dev/groundburst/client/render/LaunchStationRenderer.java": [
        swap("int packedLight, int packedOverlay, int colour) {\n"
             "        super.postRender(pose, be, model, buffers, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);",
             "int packedLight, int packedOverlay, float red, float green,\n"
             "            float blue, float alpha) {\n"
             "        super.postRender(pose, be, model, buffers, buffer, isReRender, partialTick, packedLight, packedOverlay, red, green, blue, alpha);"),
    ],
    # The stage's partial tick: a float on 1.20.1.
    "src/main/java/dev/groundburst/port/render/RenderBridge.java": [
        swap("event.getPartialTick().getGameTimeDeltaPartialTick(false)", "event.getPartialTick()"),
    ],
    "src/main/java/dev/groundburst/content/fluid/CreativeFluidTankBlock.java": [swap("f.getHoverName()", "f.getDisplayName()")],
    "src/main/java/dev/groundburst/content/fluid/FluidTankBlock.java": [swap("f.getHoverName()", "f.getDisplayName()")],
    "src/main/java/dev/groundburst/client/render/FluidTankRenderer.java": [swap("f.getFluidType().isLighterThanAir()", "f.getFluid().getFluidType().isLighterThanAir()")],
    # The closest forest (a pair of its place and biome; DataFixerUpper's Pair has its own getFirst).
    "src/main/java/dev/groundburst/blast/PlayBench.java": [swap(f"{L}.J21.first(found)", "found.getFirst()")],
    # A sprite of the GUI atlas by name (1.20.2's): its texture file drawn whole on 1.20.1.
    "src/main/java/dev/groundburst/port/GuiGraphicsExtractor.java": [
        swap("        this.g.blitSprite(sprite, x, y, width, height);\n",
             "        this.g.blit(sprite.withPath(p -> \"textures/gui/sprites/\" + p + \".png\"), x, y, 0, 0, width, height, width, height);\n"),
        swap("tinted(color, () -> this.g.blitSprite(sprite, x, y, width, height));",
             "tinted(color, () -> this.g.blit(sprite.withPath(p -> \"textures/gui/sprites/\" + p + \".png\"), x, y, 0, 0, width, height, width, height));"),
    ],
    # The machines' recipe serializers: by their 1.21 codecs, read and written by 1.20.1's serializer calls.
    "src/main/java/dev/groundburst/content/recipe/GBRecipes.java": [
        replace_method("private static RecipeSerializer<ProcessingRecipe> serializer(MapCodec<ProcessingRecipe> codec,",
                       "private static RecipeSerializer<ProcessingRecipe> serializer(MapCodec<ProcessingRecipe> codec,\n"
                       "            StreamCodec<FriendlyByteBuf, ProcessingRecipe> streamCodec) {\n"
                       f"        return {L}.recipe.Recipes.serializer(codec, streamCodec);\n"
                       "    }"),
    ],
    # The Frozen effect asked of an effect instance or event (1.21's holders: is(...); 1.20.1's effects themselves).
    "src/main/java/dev/groundburst/content/effect/EffectSystems.java": [
        swap("e.getEffectInstance().is(FROZEN.get())", "e.getEffectInstance().getEffect() == FROZEN.get()"),
        swap("e.getEffect().is(FROZEN.get())", "e.getEffect() == FROZEN.get()"),
    ],
    # Sounds 1.21 hands out in holders: the registered holder (1.20.1's DeferredHolder is no holder itself).
    "src/main/java/dev/groundburst/content/superweapon/SuperweaponBlockEntity.java": [
        swap("dev.groundburst.registry.GBSounds.SUPERWEAPON_READY\n", "dev.groundburst.registry.GBSounds.SUPERWEAPON_READY.getDelegate()\n"),
        swap("dev.groundburst.registry.GBSounds.SUPERWEAPON_WARNING;", "dev.groundburst.registry.GBSounds.SUPERWEAPON_WARNING.getDelegate();"),
    ],
    "src/main/java/dev/groundburst/content/radiation/RadiationItems.java": [
        swap(".soundAfterConsume(GBSounds.MEDICAL_INJECTOR)", ".soundAfterConsume(GBSounds.MEDICAL_INJECTOR.getDelegate())"),
    ],
    # The bunker's block set (1.20.3 added a wind charge's and an arrow's say and the plates' sensitivity).
    "src/main/java/dev/groundburst/content/construction/BunkerSet.java": [
        swap("true, false, false,\n                    BlockSetType.PressurePlateSensitivity.MOBS, SoundType.NETHERITE_BLOCK,",
             "true, SoundType.NETHERITE_BLOCK,"),
    ],
    "src/main/java/dev/groundburst/content/machine/DecontaminationShowerBlockEntity.java": [
        swap("public static final List<Holder<MobEffect>> WASHED", "public static final List<MobEffect> WASHED"),
        swap("for (Holder<MobEffect> effect : WASHED)", "for (MobEffect effect : WASHED)"),
    ],
    # 1.20.1's ray clip takes an entity for its collision context (none here).
    "src/main/java/dev/groundburst/content/artillery/Ballistics.java": [
        swap("ClipContext.Fluid.ANY, ctx))", "ClipContext.Fluid.ANY, (net.minecraft.world.entity.Entity) null))"),
    ],
    "src/main/java/dev/groundburst/content/launch/Flights.java": [
        swap("ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()))",
             "ClipContext.Fluid.NONE, (net.minecraft.world.entity.Entity) null))"),
    ],
    # A shell's random (1.20.1 keeps an entity's own to itself): its level's.
    "src/main/java/dev/groundburst/client/fx/ArtilleryClient.java": [
        swap("shell.getRandom().nextFloat()", "shell.level().getRandom().nextFloat()"),
    ],
    # A site's loot table: 1.20.1 names loot tables by id (1.20.5 made them a registry).
    "src/main/java/dev/groundburst/world/structure/GBStructures.java": [
        swap("    public static ResourceKey<LootTable> loot(String name) {\n        return ResourceKey.create(Registries.LOOT_TABLE, GroundBurst.id(\"chests/\" + name));",
             "    public static net.minecraft.resources.ResourceLocation loot(String name) {\n        return GroundBurst.id(\"chests/\" + name);"),
    ],
    "src/main/java/dev/groundburst/gametest/StructureTests.java": [
        swap("level.getServer().reloadableRegistries().getLootTable(GBStructures.loot(name))", "level.getServer().getLootData().getLootTable(GBStructures.loot(name))"),
        swap("c.getLootTable() != null\n                && c.getLootTable().equals(",
             f"(({L}.mixin.RandomizableContainerAccess) c).groundburst$lootTable() != null\n"
             f"                && (({L}.mixin.RandomizableContainerAccess) c).groundburst$lootTable().equals("),
    ],
    # The game's own explosions in the heal tests, made as 1.20.1 makes them.
    "src/main/java/dev/groundburst/gametest/HealTests.java": [
        swap("level.explode(primed, Explosion.getDefaultDamageSource(level, primed), null, at.x, at.y, at.z, 4.0F, false, Level.ExplosionInteraction.TNT);",
             "level.explode(primed, at.x, at.y, at.z, 4.0F, Level.ExplosionInteraction.TNT);"),
        swap("new Explosion(level, null, null, null, at.x, at.y, at.z, 3.0F, false, Explosion.BlockInteraction.DESTROY,\n"
             "                net.minecraft.core.particles.ParticleTypes.EXPLOSION, net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER,\n"
             "                net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE) {};",
             "new Explosion(level, null, null, null, at.x, at.y, at.z, 3.0F, false, Explosion.BlockInteraction.DESTROY) {};"),
    ],
    # The crewman stands at the gun, LIFT above it (1.20.2 gave a vehicle its passengers' attachment points).
    "src/main/java/dev/groundburst/content/artillery/GunSeat.java": [
        replace_method("protected Vec3 getPassengerAttachmentPoint(",
                       "protected void positionRider(Entity passenger, Entity.MoveFunction move) {\n"
                       "        if (this.hasPassenger(passenger)) {\n"
                       "            move.accept(passenger, this.getX(), this.getY() + LIFT, this.getZ());\n"
                       "        }\n"
                       "    }"),
    ],
}


def config_registration(text: str) -> str:
    """NeoForge registers a config with the mod's container; Forge 1.20.1 with the loading context (the same container)."""
    return re.sub(r"\b\w+\.registerConfig\(ModConfig\.Type\.", "net.minecraftforge.fml.ModLoadingContext.get().registerConfig(ModConfig.Type.", text)


FUNCTIONS.append(config_registration)


# --- resources ------------------------------------------------------------------------------------------------------------
# 1.21's singular data folders, 1.20.1's plural ones.
DATA_FOLDERS = {"recipe": "recipes", "loot_table": "loot_tables", "advancement": "advancements", "structure": "structures",
                "tags/block": "tags/blocks", "tags/item": "tags/items", "tags/fluid": "tags/fluids",
                "tags/entity_type": "tags/entity_types", "tags/game_event": "tags/game_events", "tags/function": "tags/functions"}

# NeoForge 1.21's common tags (c:) and Forge 1.20.1's (forge:): the same path, but for these.
TAG_RENAMES = {"glass_blocks": "glass", "glass_blocks/colorless": "glass/colorless", "glass_panes/colorless": "glass_panes/colorless",
               "slime_balls": "slimeballs", "sands": "sand", "gravels": "gravel", "stones": "stone", "cobblestones": "cobblestone",
               "obsidians": "obsidian", "strings": "string", "leathers": "leather", "gunpowders": "gunpowder",
               "sands/colorless": "sand/colorless", "sands/red": "sand/red", "netherracks": "netherrack"}
# c: fluid tags that are vanilla's on 1.20.1.
VANILLA_FLUID_TAGS = {"water": "minecraft:water", "lava": "minecraft:lava"}
# c: tags Forge 1.20.1 has no counterpart of: the mod's own here (src/main/resources/data/groundburst/tags, this branch's;
# the code's are dev.groundburst.legacy.Tags2's).
OWN_COMMON_TAGS = {"concretes": "groundburst:concretes"}


def common_tag(name: str, fluid: bool = False) -> str:
    """A c: tag's name (without the c:) as Forge 1.20.1's."""
    if fluid and name in VANILLA_FLUID_TAGS:
        return VANILLA_FLUID_TAGS[name]
    if name in OWN_COMMON_TAGS:
        return OWN_COMMON_TAGS[name]
    return "forge:" + TAG_RENAMES.get(name, name)


def common_tags_in(text: str) -> str:
    """Every c: tag named in a JSON text (an id in quotes, with or without #)."""
    return re.sub(r'"(#?)c:([a-z0-9_/.-]+)"', lambda m: f'"{m.group(1)}{common_tag(m.group(2))}"', text)


def port_resource(path: str, data: bytes) -> tuple[str, bytes] | None:
    """Where the 1.21.1 build's resource goes here and what it becomes (None: it has no place here)."""
    m = re.match(r"^(src/(?:main|generated)/resources/data/)(\w+)/(.*)$", path)
    if m:
        root, ns, rest = m.groups()
        fluid = rest.startswith("tags/fluid/")
        for old, new in sorted(DATA_FOLDERS.items(), key=lambda kv: -len(kv[0])):
            if rest.startswith(old + "/"):
                rest = new + rest[len(old):]
                break
        # The common tags' own files: c's tag folders are forge's (or vanilla's, the water).
        if ns == "c" and rest.startswith("tags/"):
            folder, _, name = rest[len("tags/"):].partition("/")
            tag = name[:-len(".json")] if name.endswith(".json") else name
            new_ns, new_name = common_tag(tag, fluid).split(":")
            ns, rest = new_ns, f"tags/{folder}/{new_name}.json"
        # NeoForge's biome modifiers: Forge's.
        if rest.startswith("neoforge/biome_modifier/"):
            rest = "forge/biome_modifier/" + rest[len("neoforge/biome_modifier/"):]
        path = f"{root}{ns}/{rest}"
        if path.endswith(".json"):
            data = port_data_json(path, data)
            if data is None:
                return None
        if path.endswith(".nbt") and "/structures/" in path:
            data = port_structure(data)
        return path, data
    if re.match(r"^src/main/resources/[\w.]*mixins\.json$", path):
        return path, port_mixins(data)
    if re.match(r"^src/(main|generated)/resources/assets/\w+/models/.*\.json$", path):
        return path, port_model(data)
    # 1.20.1 refuses a resource whose name has capitals (and logs an error for it): the sounds' credits in lower case.
    if path == "src/main/resources/assets/groundburst/sounds/CREDITS.txt":
        return "src/main/resources/assets/groundburst/sounds/credits.txt", data
    return path, data


def port_mixins(data: bytes) -> bytes:
    """A mixin config: Java 17, with the refmap the build makes (build.gradle's mixin block)."""
    m = json.loads(data)
    for side in ("mixins", "client", "server"):
        if side in m:
            m[side] = [name for name in m[side] if name not in DROPPED_MIXINS]
            if not m[side] and side != "mixins":
                del m[side]                                 # (a side whose mixins were all dropped)
    m["compatibilityLevel"] = "JAVA_17"
    m["refmap"] = "groundburst.refmap.json"
    # MixinExtras started before these mixins are read (dev.groundburst.legacy.mixin.MixinPlugin).
    m["plugin"] = "dev.groundburst.legacy.mixin.MixinPlugin"
    return (json.dumps(m, indent=2) + "\n").encode("utf-8")


def port_data_json(path: str, data: bytes) -> bytes | None:
    text = data.decode("utf-8")
    text = common_tags_in(text)
    text = text.replace('"neoforge:', '"forge:')
    obj = json.loads(text)
    # (By the folder under the namespace: a recipe's advancement is in advancements/recipes/.)
    folder = re.match(r"^.*?/data/[^/]+/([^/]+)/", path)
    folder = folder.group(1) if folder else ""
    if folder == "recipes":
        obj = port_recipe(obj)
        if obj is None:
            return None
    elif folder == "advancements":
        obj = port_advancement(obj)
    elif folder == "loot_tables":
        obj = port_loot(obj)
    elif folder == "tags":
        obj = port_tag(obj, "/tags/fluids/" in path)
    return (json.dumps(obj, indent=2, ensure_ascii=False) + "\n").encode("utf-8")


def port_tag(obj, fluid: bool):
    def entry(v):
        if isinstance(v, str):
            return port_tag_id(v, fluid)
        if isinstance(v, dict) and "id" in v:
            return dict(v, id=port_tag_id(v["id"], fluid))
        return v
    obj["values"] = [entry(v) for v in obj.get("values", [])]
    return obj


def port_tag_id(v: str, fluid: bool) -> str:
    if fluid and v.startswith("#forge:"):
        name = v[len("#forge:"):]
        if name in VANILLA_FLUID_TAGS:
            return "#" + VANILLA_FLUID_TAGS[name]
    return v


def port_item_stack(v):
    """1.21's item stack in JSON ({"id", "count", "components"}) as 1.20.1's ({"item", "count"})."""
    if not isinstance(v, dict):
        return v
    out = {}
    if "id" in v:
        out["item"] = v["id"]
    elif "item" in v:
        out["item"] = v["item"]
    if "count" in v:
        out["count"] = v["count"]
    for k, x in v.items():
        if k not in ("id", "item", "count", "components"):
            out[k] = x
    return out


def port_recipe(obj):
    if "result" in obj and isinstance(obj["result"], dict):
        obj["result"] = port_item_stack(obj["result"])
        # 1.20.1's stonecutting result is an id, its count beside it (and needed).
        if obj.get("type") == "minecraft:stonecutting":
            result = obj["result"]
            obj["result"] = result["item"]
            obj["count"] = result.get("count", 1)
    # A machine recipe's fluids: the c: fluid tags that are vanilla's on 1.20.1 (water, lava).
    for f in obj.get("fluids", []) if isinstance(obj.get("fluids"), list) else []:
        ing = f.get("ingredient") if isinstance(f, dict) else None
        for one in (ing if isinstance(ing, list) else [ing]):
            if isinstance(one, dict) and isinstance(one.get("tag"), str) and one["tag"].startswith("forge:"):
                name = one["tag"][len("forge:"):]
                if name in VANILLA_FLUID_TAGS:
                    one["tag"] = VANILLA_FLUID_TAGS[name]
    return obj


def port_item_predicate(pred):
    """1.21's item predicate as 1.20.1's: its items one id or a tag string (1.20.1: a list of ids, or a tag), and its
    enchantments among its sub-predicates ({"predicates": {"minecraft:enchantments": [...]}}, each naming its
    enchantment "enchantments"; 1.20.1: "enchantments" beside the items, each naming it "enchantment")."""
    if not isinstance(pred, dict):
        return pred
    items = pred.get("items")
    if isinstance(items, str):
        if items.startswith("#"):
            pred.pop("items")
            pred["tag"] = items[1:]
        else:
            pred["items"] = [items]
    subs = pred.pop("predicates", None)
    if isinstance(subs, dict):
        for kind, key in (("minecraft:enchantments", "enchantments"), ("minecraft:stored_enchantments", "stored_enchantments")):
            if kind in subs:
                out = []
                for e in subs.pop(kind):
                    e = dict(e)
                    name = e.pop("enchantments", None)
                    assert name is None or isinstance(name, str) and not name.startswith("#"), \
                        f"port.py: an enchantment predicate 1.20.1 cannot read ({name})"
                    if name is not None:
                        e["enchantment"] = name
                    out.append(e)
                pred[key] = out
        assert not subs, f"port.py: item sub-predicates 1.20.1 has none of: {sorted(subs)}"
    return pred


def port_advancement(obj):
    """1.21's item predicates in the criteria (an inventory's items, ...): 1.20.1's."""
    def walk(o, item_predicate=False):
        if isinstance(o, dict):
            if item_predicate:
                port_item_predicate(o)
            for k, v in o.items():
                # (an inventory criterion's items: a list of predicates; a consumed or used item: one)
                walk(v, k in ("items", "item") and not isinstance(v, str))
        elif isinstance(o, list):
            for v in o:
                walk(v, item_predicate)
    for crit in obj.get("criteria", {}).values():
        walk(crit.get("conditions", {}))
    return obj


def port_loot(obj):
    """1.21's copy_components from a block entity: this branch's own loot function (dev.groundburst.legacy.loot); a
    tool's predicate (shears, silk touch) as 1.20.1's."""
    def walk(o):
        if isinstance(o, dict):
            if o.get("function") == "minecraft:copy_components":
                o["function"] = "groundburst:copy_components"
            if o.get("condition") == "minecraft:match_tool" and "predicate" in o:
                port_item_predicate(o["predicate"])
            for v in o.values():
                walk(v)
        elif isinstance(o, list):
            for v in o:
                walk(v)
    walk(obj)
    return obj


# Vanilla textures 1.20.3 and later renamed (1.20.1's names).
TEXTURE_RENAMES = {"block/short_grass": "block/grass"}


def port_model(data: bytes) -> bytes:
    text = data.decode("utf-8")
    out = text.replace('"neoforge:', '"forge:')
    for new, old in TEXTURE_RENAMES.items():
        out = re.sub(r'"(minecraft:)?' + re.escape(new) + '"', lambda m: f'"{m.group(1) or ""}{old}"', out)
    return data if out == text else out.encode("utf-8")


# --- structure templates (NBT) --------------------------------------------------------------------------------------------
# 1.20.1's data version: what its structure templates are saved at.
DATA_VERSION = 3465


def _nbt_read(buf, tag: int):
    import struct
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


def _nbt_write(out, tag: int, v) -> None:
    import struct
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


def nbt_load(data: bytes):
    import gzip
    import io
    import struct
    buf = io.BytesIO(gzip.decompress(data))
    tag = struct.unpack(">b", buf.read(1))[0]
    n = struct.unpack(">H", buf.read(2))[0]
    name = buf.read(n).decode("utf-8")
    return tag, name, _nbt_read(buf, tag)


def nbt_save(tag: int, name: str, root) -> bytes:
    import gzip
    import io
    import struct
    out = io.BytesIO()
    out.write(struct.pack(">b", tag))
    b = name.encode("utf-8")
    out.write(struct.pack(">H", len(b)))
    out.write(b)
    _nbt_write(out, tag, root)
    return gzip.compress(out.getvalue(), mtime=0)


# The sites' blocks 1.20.1 has not (1.20.3's and 1.21's): what stands in their place, and whether their properties stay.
PALETTE_RENAMES = {
    "minecraft:short_grass": ("minecraft:grass", True),               # 1.20.3 renamed 1.20.1's grass
    # 1.21's copper doors (the guard's and the mast's huts): a door a hand opens, as a copper door is (an iron door needs
    # redstone), dark as the weathered green.
    "minecraft:weathered_copper_door": ("minecraft:spruce_door", True),
    # 1.21's heavy core (the test site's device on its stand, hung from its cables): a dense iron-bound block of 1.20.1's.
    "minecraft:heavy_core": ("minecraft:lodestone", False),
}


def port_structure(data: bytes) -> bytes:
    """A structure template, saved at 1.20.1's data version (so nothing is updated as it loads), its newer blocks as
    1.20.1's (PALETTE_RENAMES)."""
    tag, name, root = nbt_load(data)
    root["DataVersion"] = (3, DATA_VERSION)
    # (A compound is a dict of name to (tag, value); a list is (element tag, [values]).)
    palettes = []
    if "palette" in root:
        palettes.append(root["palette"][1][1])
    if "palettes" in root:
        palettes.extend(p[1] for p in root["palettes"][1][1])
    for entries in palettes:
        for state in entries:
            block = state["Name"][1]
            if block in PALETTE_RENAMES:
                new, keep = PALETTE_RENAMES[block]
                state["Name"] = (8, new)
                if not keep:
                    state.pop("Properties", None)
    return nbt_save(tag, name, root)


# --- the run ----------------------------------------------------------------------------------------------------------------
def checkout_eol(dest: str, data: bytes) -> bytes:
    """Text files as git checks them out here (CRLF, but for the generated data: .gitattributes)."""
    if dest.startswith("src/generated/") or not re.search(r"\.(java|md|txt|py|json|toml|cfg|gradle|properties|ps1|mcmeta|fsh|vsh|glsl)$", dest):
        return data
    return data.replace(b"\r\n", b"\n").replace(b"\n", b"\r\n")


def port_file(path: str) -> tuple[str, bytes] | None:
    """Where the 1.21.1 build's file {path} goes here and what it becomes (None: it has no place here)."""
    if any(p.fullmatch(path) for p in DROP) or path in OWN:
        return None
    data = read_source(path)
    if path.endswith(".java"):
        return path, port_java(data.decode("utf-8"), path).encode("utf-8")
    if path.startswith("src/main/resources/") or path.startswith("src/generated/resources/"):
        return port_resource(path, data)
    return path, data


def sync(new: str) -> None:
    """Brings this branch up to the 1.21.1 branch's commit {new} from the one in base.txt: each file changed since is ported
    again; one edited by hand here (manual.txt) gets the change merged into it (a three-way merge of ported text: the old
    port, the new port, and the file as it is here), conflicts left marked for a person; a file deleted there goes."""
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
            theirs_file = ROOT / "build" / "port-merge" / (dest + ".source")
            base_file.parent.mkdir(parents=True, exist_ok=True)
            base_file.write_bytes(checkout_eol(dest, out_old[1]) if out_old else b"")
            theirs_file.write_bytes(checkout_eol(dest, data_new))
            r = subprocess.run(["git", "merge-file", "-L", "here", "-L", "port of the old 1.21.1 build",
                                "-L", "port of the new 1.21.1 build", str(target), str(base_file), str(theirs_file)],
                               capture_output=True)
            if r.returncode != 0:
                conflicts.append(dest)
            continue
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(checkout_eol(dest, data_new))
    BASE = new
    (HERE / "base.txt").write_text(f"{new}   the 1.21.1 branch's commit this branch is brought up to (docs/BACKPORT.md)\n",
                                   encoding="utf-8")
    print(f"{len(changes)} of the 1.21.1 build's files changed since {old[:8]}; now at {new[:8]}")
    for c in conflicts:
        print("  conflict to resolve by hand:", c)


def main() -> None:
    args = sys.argv[1:]
    if args and args[0] == "--show":
        out = port_file(args[1])
        sys.stdout.write("(dropped)\n" if out is None else out[1].decode("utf-8"))
        return
    if args and args[0] == "--sync":
        sync(args[1] if len(args) > 1 else "1.21.1")
        return
    files = args or source_files()
    written = kept = dropped = 0
    for path in files:
        out = port_file(path)
        if out is None:
            dropped += 1
            continue
        dest, data = out
        if dest in MANUAL and (ROOT / dest).exists():
            kept += 1
            continue
        target = ROOT / dest
        data = checkout_eol(dest, data)
        if dest != path and (ROOT / path).exists():
            (ROOT / path).unlink()                         # the 1.21.1 build's copy in this checkout, moved (a folder renamed)
        if not target.exists() or target.read_bytes() != data:
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
            written += 1
    # The 1.21.1 build's files dropped here but still in this checkout (it began as a copy of that branch): removed.
    if not args:
        for path in source_files():
            if (any(p.fullmatch(path) for p in DROP) and not path.startswith("tools/backport/")
                    and path != "docs/BACKPORT.md" and (ROOT / path).exists()):
                (ROOT / path).unlink()
        # Folders the moves and drops left empty.
        for folder in sorted((p for p in (ROOT / "src").rglob("*") if p.is_dir()), key=lambda p: -len(p.parts)):
            if not any(folder.iterdir()):
                folder.rmdir()
    # The code's common tags are renamed by Tags2's table at run time, the data's by TAG_RENAMES here: the same.
    tags2 = (ROOT / "src/main/java/dev/groundburst/legacy/Tags2.java").read_text(encoding="utf-8")
    for old, new in TAG_RENAMES.items():
        assert f'Map.entry("{old}", "{new}")' in tags2, f"port.py: Tags2's renames are not TAG_RENAMES ({old})"
    assert tags2.count("Map.entry(") == len(TAG_RENAMES), "port.py: Tags2 renames a tag TAG_RENAMES does not"
    print(f"{written} written, {kept} kept (manual.txt), {dropped} dropped")


if __name__ == "__main__":
    main()
