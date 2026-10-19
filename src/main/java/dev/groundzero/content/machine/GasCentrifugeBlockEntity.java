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
 * <p><b>Cascade bonus:</b> centrifuges standing side by side in a straight line (east-west or north-south, same
 * height) form a cascade. Every centrifuge in it runs 15% faster for each other centrifuge in the line, counting at
 * most {@value #MAX_CASCADE} (so up to +105%). The longer of a centrifuge's two lines counts.
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

    /** Re-counts the cascade: the longer straight line of centrifuges through this one, along X or Z. */
    public void updateCascade(Level level) {
        int size = Math.max(line(level, Direction.Axis.X), line(level, Direction.Axis.Z));
        this.cascade = Math.min(size, MAX_CASCADE);
        this.scanned = true;
    }

    private int line(Level level, Direction.Axis axis) {
        int n = 1;
        for (Direction.AxisDirection sign : Direction.AxisDirection.values()) {
            Direction d = Direction.fromAxisAndDirection(axis, sign);
            BlockPos p = this.worldPosition.relative(d);
            // Lower halves only: the upper half of a tall machine has no block entity.
            while (n < MAX_CASCADE * 2 && level.isLoaded(p) && level.getBlockEntity(p) instanceof GasCentrifugeBlockEntity) {
                n++;
                p = p.relative(d);
            }
        }
        return n;
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
