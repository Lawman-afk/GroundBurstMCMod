package dev.groundzero.content.machine;

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
    private static final java.util.List<String> OPTIONAL_BONES = java.util.List.of("die_plate", "die_rod", "die_wire", "die_canister");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Identifier modelId;

    public GeoBlockItem(Block block, Properties properties, Identifier modelId) {
        super(block, properties);
        this.modelId = modelId;
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        Identifier id = this.modelId;
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
                            renderPassInfo.poseStack().translate(0.5f, 0f, 0.5f);
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

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // Items show the model at rest.
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
