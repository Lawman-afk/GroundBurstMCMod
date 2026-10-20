package dev.groundzero.content.multiblock;

/**
 * A structure's fluid port: once formed it hands pipes the controller's tanks (filling input tanks, draining product
 * tanks). Items go through the Item Port, power through the Energy Port.
 */
public class FluidPortBlock extends PortBlock {
    public FluidPortBlock(Properties properties) {
        super(properties);
    }
}
