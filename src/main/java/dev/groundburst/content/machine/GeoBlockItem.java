package dev.groundburst.content.machine;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import com.geckolib.animatable.GeoItem;
import com.geckolib.animatable.client.GeoRenderProvider;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.model.DefaultedBlockGeoModel;
import com.geckolib.renderer.GeoItemRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.util.GeckoLibUtil;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;

/** Item form of a GeckoLib-rendered block: draws the block's geo model in hand, in the GUI and in item frames. */
public class GeoBlockItem extends BlockItem implements GeoItem {
    /** Bones that only show in a running or loaded machine: items draw the machine idle and empty. */
    private static final java.util.List<String> OPTIONAL_BONES = java.util.List.of("die_plate", "die_rod", "die_wire", "die_canister",
            "chip", "frost", "core", "sparks", "puff0", "puff1", "puff2", "spray", "led0", "led1", "led2", "led3", "led4", "battery",
            "workpiece", "bar0", "bar1", "bar2", "bar3", "bar4", "bar5", "bar6", "bar7", "lens_spot", "lens_full", "beam");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Identifier modelId;
    /** Blocks to shift the model east so it is centred (a two-block-wide machine drawn from its main half: 0.5). */
    private final float centreX;

    public GeoBlockItem(Block block, Properties properties, Identifier modelId) {
        this(block, properties, modelId, 0);
    }

    public GeoBlockItem(Block block, Properties properties, Identifier modelId, float centreX) {
        super(block, properties);
        this.modelId = modelId;
        this.centreX = centreX;
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        Identifier id = this.modelId;
        float shift = this.centreX;
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
    public void appendHoverText(net.minecraft.world.item.ItemStack stack, TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display,
            Consumer<net.minecraft.network.chat.Component> builder, net.minecraft.world.item.TooltipFlag flag) {
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
