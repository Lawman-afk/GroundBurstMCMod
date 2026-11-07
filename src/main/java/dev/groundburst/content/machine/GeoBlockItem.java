package dev.groundburst.content.machine;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Item form of a GeckoLib-rendered block: draws the block's geo model in hand, in the GUI and in item frames. The 1.21.1
 * backport's, for GeckoLib 4 (its item renderer is a block entity-less renderer, drawn for items whose model is
 * {@code builtin/entity}).
 */
public class GeoBlockItem extends BlockItem implements GeoItem {
    /** Bones that only show in a running or loaded machine: items draw the machine idle and empty. */
    private static final List<String> OPTIONAL_BONES = List.of("die_plate", "die_rod", "die_wire", "die_canister",
            "chip", "frost", "core", "sparks", "puff0", "puff1", "puff2", "spray", "led0", "led1", "led2", "led3", "led4", "battery",
            "workpiece", "bar0", "bar1", "bar2", "bar3", "bar4", "bar5", "bar6", "bar7", "lens_spot", "lens_full", "beam");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final ResourceLocation modelId;
    /** Blocks to shift the model east so it is centred (a two-block-wide machine drawn from its main half: 0.5). */
    private final float centreX;
    /**
     * Bones turned {x, y, z} radians in the item (as its block entity turns them in the world): a model built in one
     * pose and set in another at rest (the mortar's tube, built upright, laid at 60 degrees).
     */
    private Map<String, float[]> pose = Map.of();

    public GeoBlockItem(Block block, Properties properties, ResourceLocation modelId) {
        this(block, properties, modelId, 0);
    }

    public GeoBlockItem(Block block, Properties properties, ResourceLocation modelId, float centreX) {
        super(block, properties);
        this.modelId = modelId;
        this.centreX = centreX;
    }

    /** This item's model drawn with these bones turned (see {@link #pose}). */
    public GeoBlockItem posed(Map<String, float[]> pose) {
        this.pose = pose;
        return this;
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        ResourceLocation id = this.modelId;
        float shift = this.centreX;
        Map<String, float[]> turned = this.pose;
        consumer.accept(new GeoRenderProvider() {
            private @Nullable GeoItemRenderer<GeoBlockItem> renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getGeoItemRenderer() {
                if (this.renderer == null) {
                    this.renderer = new GeoItemRenderer<>(new dev.groundburst.client.render.PolyGeoModel<>(id)) {
                        // GeckoLib lifts item models by half a block (it expects them centred on their origin); block
                        // models sit on their origin like a placed block, so only centre them horizontally.
                        @Override
                        public void preRender(PoseStack poseStack, GeoBlockItem animatable, BakedGeoModel model, @Nullable MultiBufferSource bufferSource,
                                @Nullable VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
                            this.itemRenderTranslations = new org.joml.Matrix4f(poseStack.last().pose());
                            scaleModelForRender(this.scaleWidth, this.scaleHeight, poseStack, animatable, model, isReRender, partialTick, packedLight,
                                    packedOverlay);
                            if (!isReRender) {
                                poseStack.translate(0.5f + shift, 0f, 0.5f);
                            }
                        }

                        // Optional contents (e.g. the press dies, bones named "die_*") are not part of the item; some bones are posed.
                        @Override
                        public void renderRecursively(PoseStack poseStack, GeoBlockItem animatable, GeoBone bone, RenderType renderType,
                                MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight,
                                int packedOverlay, int colour) {
                            if (OPTIONAL_BONES.contains(bone.getName())) {
                                return;
                            }
                            float[] r = turned.get(bone.getName());
                            if (r != null) {
                                var bind = bone.getInitialSnapshot();
                                bone.updateRotation(bind.getRotX() + r[0], bind.getRotY() + r[1], bind.getRotZ() + r[2]);
                            }
                            super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick,
                                    packedLight, packedOverlay, colour);
                        }
                    };
                }
                return this.renderer;
            }
        });
    }

    /** Blocks picked up with stored energy (capacitor banks) show it. */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        Integer stored = stack.get(dev.groundburst.registry.GBDataComponents.ENERGY.get());
        if (stored != null && getBlock() instanceof dev.groundburst.content.power.CapacitorBankBlock bank) {
            tooltip.add(dev.groundburst.content.power.BatteryItem.chargeLine(stored, bank.tier().capacity()));
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // Items show the model at rest.
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
