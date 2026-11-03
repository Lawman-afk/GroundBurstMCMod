package dev.groundburst.port.transfer.fluid;

import org.jspecify.annotations.Nullable;

import dev.groundburst.port.transfer.ItemAccessResourceHandler;
import dev.groundburst.port.transfer.access.ItemAccess;
import dev.groundburst.port.transfer.item.ItemResource;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

/** A bucket: empty, or 1,000 mB of the fluid it carries (filled and emptied whole). */
public class BucketResourceHandler extends ItemAccessResourceHandler<FluidResource> {
    public BucketResourceHandler(ItemAccess itemAccess) {
        super(itemAccess, 1);
    }

    @Override
    protected FluidResource getResourceFrom(ItemResource accessResource, int index) {
        return accessResource.getItem() instanceof BucketItem b && b.content != Fluids.EMPTY ? FluidResource.of(b.content) : FluidResource.EMPTY;
    }

    @Override
    protected int getAmountFrom(ItemResource accessResource, int index) {
        return getResourceFrom(accessResource, index).isEmpty() ? 0 : 1000;
    }

    @Override
    protected @Nullable ItemResource update(ItemResource accessResource, int index, FluidResource newResource, int newAmount) {
        if (newAmount == 0) {
            return ItemResource.of(Items.BUCKET);
        }
        if (newAmount == 1000) {
            var bucket = newResource.getFluid().getBucket();
            return bucket == Items.AIR ? null : ItemResource.of(bucket);
        }
        return null;
    }

    @Override
    protected int getCapacity(int index, FluidResource resource) {
        return 1000;
    }
}
