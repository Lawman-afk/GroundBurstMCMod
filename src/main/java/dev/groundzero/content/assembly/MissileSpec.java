package dev.groundzero.content.assembly;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.groundzero.content.ordnance.charge.ChargeBlock;
import dev.groundzero.content.ordnance.charge.ChargeKind;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

/**
 * An assembled missile (the {@code groundzero:missile} item component, DESIGN.md §10): the warhead it carries (with
 * its fuze), its body's size class, its motor, its guidance and whether its electronics are hardened against EMP.
 */
public record MissileSpec(ItemStack warhead, SizeClass body, MissileParts.Motor motor, MissileParts.Guidance guidance, boolean hardened) {
    private static final Codec<SizeClass> SIZE = Codec.STRING.xmap(s -> SizeClass.valueOf(s), SizeClass::name);

    public static final Codec<MissileSpec> CODEC = RecordCodecBuilder.create(i -> i.group(
            ItemStack.OPTIONAL_CODEC.fieldOf("warhead").forGetter(MissileSpec::warhead),
            SIZE.fieldOf("body").forGetter(MissileSpec::body),
            Codec.STRING.xmap(MissileParts.Motor::byId, m -> m.id).fieldOf("motor").forGetter(MissileSpec::motor),
            Codec.STRING.xmap(MissileParts.Guidance::byId, g -> g.id).optionalFieldOf("guidance", MissileParts.Guidance.UNGUIDED)
                    .forGetter(MissileSpec::guidance),
            Codec.BOOL.optionalFieldOf("hardened", false).forGetter(MissileSpec::hardened)).apply(i, MissileSpec::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, MissileSpec> STREAM_CODEC = StreamCodec.composite(
            ItemStack.OPTIONAL_STREAM_CODEC, MissileSpec::warhead,
            ByteBufCodecs.VAR_INT.map(n -> SizeClass.values()[Math.clamp(n, 0, SizeClass.values().length - 1)], SizeClass::ordinal), MissileSpec::body,
            ByteBufCodecs.VAR_INT.map(n -> MissileParts.Motor.values()[Math.clamp(n, 0, MissileParts.Motor.values().length - 1)], Enum::ordinal),
            MissileSpec::motor,
            ByteBufCodecs.VAR_INT.map(n -> MissileParts.Guidance.values()[Math.clamp(n, 0, MissileParts.Guidance.values().length - 1)], Enum::ordinal),
            MissileSpec::guidance,
            ByteBufCodecs.BOOL, MissileSpec::hardened, MissileSpec::new);

    /**
     * Whether the basic Launch Silo cannot fire it: a nuclear warhead, or a Strategic or intercontinental airframe (the
     * Launch Complex and the Missile Silo fire any).
     */
    public boolean needsComplex() {
        return nuclear() || this.body == SizeClass.XL || this.body == SizeClass.XXL;
    }

    /** A whole weapon from the Strategic Assembly Hall (an intercontinental missile). */
    public boolean intercontinental() {
        return this.body == SizeClass.XXL;
    }

    /** A cruise missile (a Cruise Engine, slice 9c): only the Cruise Missile Launcher fires it, and it fires nothing else. */
    public boolean cruise() {
        return this.motor.cruise();
    }

    /** What a cruise missile's look adds to its warhead's id: "he_warhead_cruise" (DESIGN.md §17.11). */
    public static final String CRUISE_LOOK = "_cruise";

    /**
     * Which missile it is (its own model and its shape in the client's table): its warhead's id, and for a cruise missile
     * that warhead's cruise missile ("he_warhead_cruise"); empty with no warhead.
     */
    public String look() {
        return look(this.warhead, cruise());
    }

    /** The look of the missile a warhead makes, a cruise missile or not. */
    public static String look(ItemStack warhead, boolean cruise) {
        String id = warheadId(warhead);
        return id.isEmpty() || !cruise ? id : id + CRUISE_LOOK;
    }

    /** Whether it carries a nuclear warhead (only a Launch Complex or a Missile Silo fires it). */
    public boolean nuclear() {
        ChargeKind kind = AssemblyStats.kindOf(this.warhead);
        return kind != null && kind.nuclear();
    }

    /** How far it flies, blocks. */
    public int range() {
        return this.motor.range;
    }

    /** The id of its warhead ("he_warhead"; empty with none): which missile it is (its own model). */
    public String warheadId() {
        return warheadId(this.warhead);
    }

    /** A warhead's id ("he_warhead"; empty for anything else). */
    public static String warheadId(ItemStack warhead) {
        return warhead.getItem() instanceof BlockItem b && b.getBlock() instanceof ChargeBlock c
                ? net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(c).getPath() : "";
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof MissileSpec s && ItemStack.matches(this.warhead, s.warhead) && this.body == s.body && this.motor == s.motor
                && this.guidance == s.guidance && this.hardened == s.hardened;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(ItemStack.hashItemAndComponents(this.warhead), this.body, this.motor, this.guidance, this.hardened);
    }
}
