package dev.groundzero.client.render;

import com.geckolib.animatable.GeoAnimatable;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.renderer.GeoBlockRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import com.google.common.reflect.TypeToken;

import dev.groundzero.content.machine.AnimatedMachine;

import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.AABB;

/** GeckoLib machine renderer whose culling box covers the whole model, including the upper half of tall machines. */
public class MachineGeoRenderer<T extends BlockEntity & GeoAnimatable> extends GeoBlockRenderer<T, BlockEntityRenderState> {
    private final int height;

    public MachineGeoRenderer(BlockEntityRendererProvider.Context context, BlockEntityType<? extends T> type, int height) {
        super(context, type);
        this.height = height;
    }

    private static final DataTicket<Set<String>> HIDDEN_BONES = DataTicket.create("groundzero_hidden_bones", new TypeToken<Set<String>>() {});

    @Override
    public void addRenderData(T animatable, @Nullable Void relatedObject, BlockEntityRenderState renderState, float partialTick) {
        if (animatable instanceof AnimatedMachine machine) {
            ((GeoRenderState) renderState).addGeckolibData(HIDDEN_BONES, machine.hiddenBones());
        }
    }

    // Raw type: GeckoLib injects GeoRenderState into BlockEntityRenderState at runtime, which javac cannot see here.
    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void adjustModelBonesForRender(RenderPassInfo renderPassInfo, BoneSnapshots snapshots) {
        Set<String> hidden = (Set<String>) renderPassInfo.getGeckolibData(HIDDEN_BONES);
        if (hidden != null) {
            for (String bone : hidden) {
                snapshots.ifPresent(bone, snapshot -> snapshot.skipRender(true));
            }
        }
    }

    @Override
    public AABB getRenderBoundingBox(T blockEntity) {
        return new AABB(blockEntity.getBlockPos()).inflate(0.25, 0, 0.25).expandTowards(0, this.height - 1, 0);
    }
}
