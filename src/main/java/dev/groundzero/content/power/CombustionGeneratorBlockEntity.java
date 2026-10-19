package dev.groundzero.content.power;

import java.util.Optional;

import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.util.GeckoLibUtil;

import dev.groundzero.content.machine.AnimatedMachine;
import dev.groundzero.content.machine.MachineBlockEntity;
import dev.groundzero.content.machine.MachineLayouts;
import dev.groundzero.content.machine.MachineMenu;
import dev.groundzero.content.machine.MachineStatus;
import dev.groundzero.registry.GZBlockEntities;
import dev.groundzero.registry.GZMenus;
import dev.groundzero.registry.GZSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;
import net.minecraft.world.phys.Vec3;

/**
 * Combustion Generator (DESIGN.md §7): burns any furnace fuel for 60 FE/t and pushes power into adjacent
 * cables and machines. Burning pauses while the buffer is full, so no fuel is wasted.
 */
public class CombustionGeneratorBlockEntity extends MachineBlockEntity implements AnimatedMachine {
    public static final int RATE = 60;
    public static final int CAPACITY = 20_000;
    public static final int OUTPUT = 240;
    public static final int DATA_BURN = 0, DATA_BURN_TOTAL = 1;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int burn;
    private int burnTotal;

    public CombustionGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(GZBlockEntities.COMBUSTION_GENERATOR.get(), pos, state, CAPACITY, 0, OUTPUT, 1, new int[] {0}, new int[0]);
    }

    @Override
    protected boolean isItemValid(int slot, ItemStack stack) {
        return stack.has(DataComponents.COOKING_FUEL);
    }

    @Override
    protected boolean tickMachine(ServerLevel level) {
        boolean running = false;
        if (this.energy.stored() >= this.energy.capacity()) {
            this.status = MachineStatus.BUFFER_FULL;
        } else {
            if (this.burn <= 0) {
                startBurning(level);
            }
            if (this.burn > 0) {
                this.burn--;
                this.energy.generate(RATE);
                this.status = MachineStatus.RUNNING;
                running = true;
            } else {
                this.status = MachineStatus.NO_FUEL;
            }
        }
        PowerUtil.pushToNeighbours(level, this.worldPosition, this.energy, OUTPUT, side -> this.sides.energy(face(side)));
        return running;
    }

    private void startBurning(ServerLevel level) {
        ItemStack fuel = this.inventory.getItem(0);
        if (fuel.isEmpty()) {
            return;
        }
        int duration = ResolvableInt.getFromItem(fuel, DataComponents.COOKING_FUEL, CookingFuel::burnTime, lootContext(level, fuel), 0);
        if (duration <= 0) {
            return;
        }
        this.burn = duration;
        this.burnTotal = duration;
        ItemStackTemplate remainder = fuel.getCraftingRemainder();
        ItemStack left = fuel.copyWithCount(fuel.getCount() - 1);
        if (remainder != null) {
            if (left.isEmpty()) {
                left = remainder.create();
            } else {
                Containers.dropItemStack(level, this.worldPosition.getX(), this.worldPosition.getY() + 1, this.worldPosition.getZ(), remainder.create());
            }
        }
        this.inventory.setItem(0, left);
    }

    private LootContext lootContext(ServerLevel level, ItemStack fuel) {
        return new LootContext.Builder(new LootParams.Builder(level)
                .withParameter(LootContextParams.BLOCK_STATE, getBlockState())
                .withParameter(LootContextParams.BLOCK_ENTITY, this)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(this.worldPosition))
                .withParameter(LootContextParams.CONTAINER, this.inventory)
                .withOptionalParameter(net.neoforged.neoforge.common.loot.NeoForgeLootContextParams.QUERIED_STACK, fuel)
                .create(LootContextParamSets.CONTAINER_PROCESS))
                .create(Optional.empty());
    }

    @Override
    public java.util.List<net.minecraft.network.chat.Component> readout(MachineMenu menu) {
        int cap = Math.max(1, menu.capacity());
        boolean burning = menu.extra(DATA_BURN) > 0;
        return java.util.List.of(
                net.minecraft.network.chat.Component.translatable(dev.groundzero.content.machine.MachineText.OUTPUT, burning ? RATE : 0),
                net.minecraft.network.chat.Component.translatable(dev.groundzero.content.machine.MachineText.STORED, (int) ((long) menu.energy() * 100 / cap)),
                net.minecraft.network.chat.Component.translatable(menu.status().translationKey()));
    }

    @Override
    protected int extraDataCount() {
        return 2;
    }

    @Override
    protected int getExtraData(int index) {
        return index == DATA_BURN ? this.burn : index == DATA_BURN_TOTAL ? this.burnTotal : 0;
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new MachineMenu(GZMenus.COMBUSTION_GENERATOR.get(), containerId, inventory, this, MachineLayouts.COMBUSTION_GENERATOR, this.data);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("burn", this.burn);
        output.putInt("burn_total", this.burnTotal);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.burn = input.getIntOr("burn", 0);
        this.burnTotal = input.getIntOr("burn_total", 0);
    }

    @Override
    public SoundEvent loopSound() {
        return GZSounds.MACHINE_COMBUSTION_GENERATOR.get();
    }

    @Override
    public boolean itemsAreFuel() {
        return true;
    }
}
