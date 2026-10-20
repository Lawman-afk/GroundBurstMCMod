package dev.groundzero.content.power;

import java.util.List;

import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.util.GeckoLibUtil;

import dev.groundzero.content.machine.AnimatedMachine;
import dev.groundzero.content.machine.MachineBlockEntity;
import dev.groundzero.content.machine.MachineLayouts;
import dev.groundzero.content.machine.MachineMenu;
import dev.groundzero.content.machine.MachineStatus;
import dev.groundzero.content.machine.MachineText;
import dev.groundzero.registry.GZBlockEntities;
import dev.groundzero.registry.GZMenus;
import dev.groundzero.registry.GZSounds;
import dev.groundzero.registry.GZTags;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * RTG, radioisotope thermoelectric generator (DESIGN.md §7): a steady 40 FE/t from a radioisotope fuel slug. The
 * fuel decays whether or not the power is used (it is radioactive decay), so an RTG is best wired to something that
 * always draws. Fuel: a plutonium ingot (10 in-game days) or an enriched uranium ingot (2 days).
 */
public class RtgBlockEntity extends MachineBlockEntity implements AnimatedMachine {
    public static final int RATE = 40;
    public static final int CAPACITY = 20_000;
    public static final int OUTPUT = 160;
    /** Sync slots: fuel time left and total, in seconds. */
    public static final int DATA_DECAY = 0, DATA_DECAY_TOTAL = 1;

    /** A fuel and how many ticks one item lasts. */
    public record Fuel(TagKey<Item> tag, int ticks) {}

    public static final List<Fuel> FUELS = List.of(new Fuel(GZTags.commonItem("ingots/plutonium"), 240_000),
            new Fuel(GZTags.commonItem("ingots/enriched_uranium"), 48_000));

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int decay;
    private int decayTotal;

    public RtgBlockEntity(BlockPos pos, BlockState state) {
        super(GZBlockEntities.RTG.get(), pos, state, CAPACITY, 0, OUTPUT, 1, new int[] {0}, new int[0]);
    }

    public static int fuelTicks(ItemStack stack) {
        for (Fuel fuel : FUELS) {
            if (stack.is(fuel.tag())) {
                return fuel.ticks();
            }
        }
        return 0;
    }

    @Override
    protected boolean isItemValid(int slot, ItemStack stack) {
        return fuelTicks(stack) > 0;
    }

    @Override
    protected boolean tickMachine(ServerLevel level) {
        if (this.decay <= 0) {
            ItemStack fuel = this.inventory.getItem(0);
            int ticks = fuelTicks(fuel);
            if (ticks > 0) {
                this.inventory.removeItem(0, 1);
                this.decay = ticks;
                this.decayTotal = ticks;
            }
        }
        boolean running = this.decay > 0;
        if (running) {
            this.decay--;
            this.energy.generate(RATE);          // anything over capacity is lost: decay does not wait
            this.status = this.energy.stored() >= this.energy.capacity() ? MachineStatus.BUFFER_FULL : MachineStatus.RUNNING;
            setChanged();
        } else {
            this.status = MachineStatus.NO_FUEL;
        }
        PowerUtil.pushToNeighbours(level, this.worldPosition, this.energy, OUTPUT, side -> this.sides.energy(face(side)));
        return running;
    }

    public int decayLeft() {
        return this.decay;
    }

    @Override
    public List<Component> readout(MachineMenu menu) {
        int cap = Math.max(1, menu.capacity());
        int seconds = menu.extra(DATA_DECAY);
        return List.of(Component.translatable(MachineText.OUTPUT, seconds > 0 ? RATE : 0),
                Component.translatable(MachineText.STORED, (int) ((long) menu.energy() * 100 / cap)),
                Component.translatable(MachineText.FUEL_LEFT, (seconds + 59) / 60));
    }

    @Override
    public Component flameTooltip(MachineMenu menu) {
        int seconds = menu.extra(DATA_DECAY);
        return seconds > 0 ? Component.translatable(MachineText.DECAY_LEFT, (seconds + 59) / 60) : Component.translatable(MachineText.DECAY_NONE);
    }

    @Override
    protected int extraDataCount() {
        return 2;
    }

    @Override
    protected int getExtraData(int index) {
        // Sync slots carry shorts, so fuel time goes over in seconds.
        return index == DATA_DECAY ? this.decay / 20 : index == DATA_DECAY_TOTAL ? this.decayTotal / 20 : 0;
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new MachineMenu(GZMenus.RTG.get(), containerId, inventory, this, MachineLayouts.RTG, this.data);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public SoundEvent loopSound() {
        return GZSounds.MACHINE_RTG.get();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("decay", this.decay);
        output.putInt("decay_total", this.decayTotal);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.decay = input.getIntOr("decay", 0);
        this.decayTotal = input.getIntOr("decay_total", 0);
    }
}
