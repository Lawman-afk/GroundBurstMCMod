package dev.groundburst.content.machine;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.base.BoneSnapshots;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.renderer.base.RenderPassInfo;
import software.bernie.geckolib.util.GeckoLibUtil;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;

/** Item form of a GeckoLib-rendered block: draws the block's geo model in hand, in the GUI and in item frames. */
public class GeoBlockItem extends BlockItem implements GeoItem {
    /** Bones that only show in a running or loaded machine: items draw the machine idle and empty. */
    private static final java.util.List<String> OPTIONAL_BONES = java.util.List.of("die_plate", "die_rod", "die_wire", "die_canister",
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
    private java.util.Map<String, float[]> pose = java.util.Map.of();

    public GeoBlockItem(Block block, Properties properties, ResourceLocation modelId) {
        this(block, properties, modelId, 0);
    }

    public GeoBlockItem(Block block, Properties properties, ResourceLocation modelId, float centreX) {
        super(block, properties);
        this.modelId = modelId;
        this.centreX = centreX;
    }

    /** This item's model drawn with these bones turned (see {@link #pose}). */
    public GeoBlockItem posed(java.util.Map<String, float[]> pose) {
        this.pose = pose;
        return this;
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        ResourceLocation id = this.modelId;
        float shift = this.centreX;
        java.util.Map<String, float[]> turned = this.pose;
        consumer.accept(new GeoRenderProvider() {
            private @Nullable GeoItemRenderer<GeoBlockItem> renderer;

            @Override
            public GeoItemRenderer<?> getGeoItemRenderer() {
                if (this.renderer == null) {
                    this.renderer = new GeoItemRenderer<>(new DefaultedBlockGeoModel<>(id)) {
                        // GeckoLib lifts item models by half a block (it expects them centred on their origin); block
                        // models sit on their origin like a placed block, so only centre them horizontally.
                        @Override
                        public void adjustRenderPose(RenderPassInfo<GeoRenderState> renderPassInfo) {
                            renderPassInfo.poseStack().translate(0.5f + shift, 0f, 0.5f);
                        }

                        // Optional contents (e.g. the press dies, bones named "die_*") are not part of the item.
                        @Override
                        @SuppressWarnings({"rawtypes", "unchecked"})
                        public void adjustModelBonesForRender(RenderPassInfo renderPassInfo, BoneSnapshots snapshots) {
                            for (String bone : OPTIONAL_BONES) {
                                snapshots.ifPresent(bone, snapshot -> snapshot.skipRender(true));
                            }
                            turned.forEach((bone, r) -> snapshots.ifPresent(bone, snapshot -> snapshot.setRotation(r[0], r[1], r[2])));
                        }
                    };
                }
                return this.renderer;
            }
        });
    }

    /** Blocks picked up with stored energy (capacitor banks) show it. */
    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(net.minecraft.world.item.ItemStack stack, net.minecraft.world.item.Item.TooltipContext context, java.util.List<net.minecraft.network.chat.Component> builder_list, net.minecraft.world.item.TooltipFlag flag) { java.util.function.Consumer<net.minecraft.network.chat.Component> builder = builder_list::add;
        Integer stored = stack.get(dev.groundburst.registry.GBDataComponents.ENERGY.get());
        if (stored != null && getBlock() instanceof dev.groundburst.content.power.CapacitorBankBlock bank) {
            builder.accept(dev.groundburst.content.power.BatteryItem.chargeLine(stored, bank.tier().capacity()));
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
