package dev.groundzero.content.multiblock;

/**
 * A structure's energy port: once formed it hands cables the controller's energy (a reactor's output, an
 * accelerator's input). Item and fluid automation go through the Item Port instead.
 */
public class EnergyPortBlock extends PortBlock {
    public EnergyPortBlock(Properties properties) {
        super(properties);
    }
}
