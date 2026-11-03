package dev.groundburst.content.artillery;

import java.util.List;
import java.util.function.Supplier;

import net.minecraft.sounds.SoundEvent;

/**
 * One kind of gun (DESIGN.md §20.1): what it fires, how it is laid, how its shots spread, and its figures.
 *
 * @param id            its block's id
 * @param calibre       its bore
 * @param shells        what it fires, in its rack's order (its crew's keys 1 up)
 * @param rackSize      shells of each kind its rack holds
 * @param charges       its propellant charges' muzzle speeds, blocks a tick, lowest first
 * @param minElevation  its elevations, degrees (the high arc is taken where it lies in them, else the low)
 * @param maxElevation
 * @param restElevation as it rests
 * @param minRange      how near and far it reaches, blocks (beyond: out of range)
 * @param maxRange
 * @param traverseLimit how far round from its front it turns, degrees either way (180: all round)
 * @param traverseRate  how fast it is laid round, degrees a tick
 * @param rangeRate     how fast it is laid out or in, blocks of range a tick
 * @param elevateRate   how fast its tube is drawn rising and falling, degrees a tick
 * @param reload        ticks between shots
 * @param accuracy      its aiming circle's radius fully aimed, blocks per 100 of range
 * @param aimTime       ticks for its circle to shrink to a third
 * @param bloom         a shot multiplies its spread by this ...
 * @param maxBloom      ... up to this (as it is when just manned)
 * @param turnBloom     how much laying blooms its spread, per degree a second
 * @param socketY       the tube's pivot: blocks over its floor ...
 * @param socketBack    ... and back from the block's middle toward its rest
 * @param tube          from the pivot to the muzzle, blocks
 * @param seatBack      where its crewman stands: blocks behind its middle ...
 * @param seatLeft      ... and to its left, as it rests
 * @param fireSound     its report
 * @param fireVolume
 * @param smoke         how big its muzzle's smoke is
 */
public record GunSpec(String id, Calibre calibre, List<ShellType> shells, int rackSize, double[] charges, float minElevation,
                      float maxElevation, float restElevation, double minRange, double maxRange, float traverseLimit, float traverseRate,
                      double rangeRate, float elevateRate, int reload, double accuracy, double aimTime, double bloom, double maxBloom,
                      double turnBloom, double socketY, double socketBack, double tube, double seatBack, double seatLeft,
                      Supplier<SoundEvent> fireSound, float fireVolume, float smoke) {
    /** Whether it turns all the way round. */
    public boolean allRound() {
        return this.traverseLimit >= 180;
    }
}
