package dev.groundzero.content.machine;

import java.util.List;

import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.util.GeckoLibUtil;

import dev.groundzero.content.recipe.ProcessingKind;
import dev.groundzero.registry.GZBlockEntities;
import dev.groundzero.registry.GZMenus;
import dev.groundzero.registry.GZSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Gas Centrifuge (DESIGN.md §8): UF6 from its gas tank into enriched uranium nuggets and depleted uranium dust;
 * 50 FE/t. Two blocks tall. Slots: 0-1 outputs.
 *
 * <p><b>Cascade bonus:</b> centrifuges joined flange to flange form a cascade. The header flanges sit on a
 * centrifuge's left and right sides, so a cascade is a straight line along that axis of centrifuges at the same height
 * whose flanges line up (each faces the same way or the opposite way). Every centrifuge in it runs 15% faster for each
 * other centrifuge in the line, counting at most {@value #MAX_CASCADE} (so up to +105%). A neighbour turned a quarter
 * (flanges pointing elsewhere) breaks the line, so crossing lines never share centrifuges.
 */
public class GasCentrifugeBlockEntity extends ProcessingBlockEntity implements AnimatedMachine {
    public static final int ENERGY_PER_TICK = 50;
    public static final int MAX_CASCADE = 8;
    public static final float CASCADE_BONUS = 0.15f;
    /** Extra sync slot after the progress pair: the cascade size. */
    public static final int DATA_CASCADE = 2;
    private static final int SCAN_INTERVAL = 20;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int cascade = 1;
    private boolean scanned;

    public GasCentrifugeBlockEntity(BlockPos pos, BlockState state) {
        super(GZBlockEntities.GAS_CENTRIFUGE.get(), pos, state, ProcessingKind.CENTRIFUGING, 0, false, 2, 25_000, 250,
                List.of(MachineTank.Spec.input(8_000)));
    }

    /** Centrifuges in this one's cascade (itself included), capped at {@link #MAX_CASCADE}. */
    public int cascade() {
        return this.cascade;
    }

    /** Speed for a cascade of {@code size} centrifuges. */
    public static float speedFor(int size) {
        return 1f + CASCADE_BONUS * (Math.min(size, MAX_CASCADE) - 1);
    }

    /** Re-counts the cascade: the line of flange-aligned centrifuges through this one, along its flange axis. */
    public void updateCascade(Level level) {
        Direction.Axis facing = facing(getBlockState());
        Direction.Axis flanges = facing == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
        int n = 1;
        for (Direction.AxisDirection sign : Direction.AxisDirection.values()) {
            Direction d = Direction.fromAxisAndDirection(flanges, sign);
            BlockPos p = this.worldPosition.relative(d);
            // Lower halves only (the upper half has no block entity), and only with flanges on the same axis.
            while (n < MAX_CASCADE * 2 && level.isLoaded(p) && level.getBlockEntity(p) instanceof GasCentrifugeBlockEntity other
                    && facing(other.getBlockState()) == facing) {
                n++;
                p = p.relative(d);
            }
        }
        this.cascade = Math.min(n, MAX_CASCADE);
        this.scanned = true;
    }

    private static Direction.Axis facing(BlockState state) {
        return state.hasProperty(MachineBlock.FACING) ? state.getValue(MachineBlock.FACING).getAxis() : Direction.Axis.Z;
    }

    @Override
    protected float speedMultiplier() {
        return speedFor(this.cascade);
    }

    @Override
    protected boolean tickMachine(ServerLevel level) {
        if (!this.scanned || (level.getGameTime() + this.worldPosition.hashCode()) % SCAN_INTERVAL == 0) {
            updateCascade(level);
        }
        return super.tickMachine(level);
    }

    @Override
    protected int extraDataCount() {
        return super.extraDataCount() + 1;
    }

    @Override
    protected int getExtraData(int index) {
        return index == DATA_CASCADE ? this.cascade : super.getExtraData(index);
    }

    @Override
    public List<Component> readout(MachineMenu menu) {
        int size = Math.max(1, menu.extra(DATA_CASCADE));
        return List.of(Component.translatable(MachineText.CASCADE, size, MAX_CASCADE),
                Component.translatable(MachineText.CASCADE_SPEED, Math.round((speedFor(size) - 1f) * 100)));
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new MachineMenu(GZMenus.GAS_CENTRIFUGE.get(), containerId, inventory, this, MachineLayouts.GAS_CENTRIFUGE, this.data);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public SoundEvent loopSound() {
        return GZSounds.MACHINE_GAS_CENTRIFUGE.get();
    }
}
