package dev.groundburst.content.fluid;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.transfer.ItemAccessResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Canisters behave like buckets for the mod's fluids: an empty canister takes exactly 1000 mB of any fluid that has a
 * canister of its kind (liquid canisters for liquids, gas canisters for gases) and becomes that fluid's canister; a
 * filled canister pours out its 1000 mB and goes back to empty.
 */
public final class CanisterFluidHandler extends ItemAccessResourceHandler<FluidResource> {
    public CanisterFluidHandler(ItemAccess itemAccess) {
        super(itemAccess, 1);
    }

    @Override
    protected FluidResource getResourceFrom(ItemResource accessResource, int index) {
        ChemFluid f = GBFluids.ofCanister(accessResource.getItem());
        return f == null ? FluidResource.EMPTY : FluidResource.of(f.source().get());
    }

    @Override
    protected int getAmountFrom(ItemResource accessResource, int index) {
        return GBFluids.ofCanister(accessResource.getItem()) == null ? 0 : FluidType.BUCKET_VOLUME;
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        ChemFluid f = GBFluids.of(resource.getFluid());
        Item current = this.itemAccess.getResource().getItem();
        return f != null && (f.emptyCanister().get() == current || f.canister().get() == current);
    }

    @Override
    protected @Nullable ItemResource update(ItemResource accessResource, int index, FluidResource newResource, int newAmount) {
        ChemFluid f = GBFluids.of(newResource.getFluid());
        if (f == null) {
            return ItemResource.EMPTY;
        }
        if (newAmount == 0) {
            return ItemResource.of(f.emptyCanister().get());
        }
        return newAmount == FluidType.BUCKET_VOLUME ? ItemResource.of(f.canister().get()) : ItemResource.EMPTY;
    }

    @Override
    protected int getCapacity(int index, FluidResource resource) {
        return FluidType.BUCKET_VOLUME;
    }
}
