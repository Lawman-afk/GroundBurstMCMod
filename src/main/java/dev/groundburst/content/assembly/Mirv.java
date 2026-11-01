package dev.groundburst.content.assembly;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.launch.LaunchNetwork;
import dev.groundburst.content.ordnance.charge.ChargeKind;
import dev.groundburst.registry.GBDataComponents;
import dev.groundburst.registry.GBLang;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * MIRVs (DESIGN.md §17.14): a missile carrying a bus of re-entry vehicles in its warhead's place, each RV a warhead
 * with its own fuze, or a decoy. What each bus takes and how much of its load each takes, the RVs it holds, and where
 * they come down round the missile's aim (its spread, one of the Launch Control Station's patterns).
 */
public final class Mirv {
    /** How far apart a bus spreads its RVs (blocks), and the step its screen's buttons move it by. */
    public static final int MAX_SPACING = 150, STEP = 4, BIG_STEP = 20;
    /** The look of a re-entry vehicle in flight (its own model: tools/assets/missiles.py), the same for every RV. */
    public static final String RV_LOOK = "re_entry_vehicle";

    /** How a bus spreads its RVs round where the missile is aimed: a pattern of the station's, {@code spacing} apart. */
    public record Spread(LaunchNetwork.Pattern pattern, int spacing) {
        public static final Codec<Spread> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.xmap(Spread::patternOf, LaunchNetwork.Pattern::name).fieldOf("pattern").forGetter(Spread::pattern),
                Codec.INT.fieldOf("spacing").forGetter(Spread::spacing)).apply(i, Spread::of));
        public static final StreamCodec<ByteBuf, Spread> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT.map(n -> LaunchNetwork.Pattern.values()[Math.clamp(n, 0, LaunchNetwork.Pattern.values().length - 1)],
                        Enum::ordinal), Spread::pattern,
                ByteBufCodecs.VAR_INT, Spread::spacing, Spread::of);

        public static Spread of(LaunchNetwork.Pattern pattern, int spacing) {
            return new Spread(pattern, Math.clamp(spacing, 0, MAX_SPACING));
        }

        private static LaunchNetwork.Pattern patternOf(String name) {
            try {
                return LaunchNetwork.Pattern.valueOf(name);
            } catch (IllegalArgumentException e) {
                return LaunchNetwork.Pattern.RING;
            }
        }
    }

    private static final String P = GroundBurst.MODID + ".mirv.";
    public static final String T_LOAD = GBLang.add(P + "load", "Load %s/%s, %s RVs");
    public static final String T_EMPTY = GBLang.add(P + "empty", "Empty: use it to load re-entry vehicles");
    public static final String T_RV = GBLang.add(P + "rv", "%sx %s");
    public static final String T_SPREAD = GBLang.add(P + "spread", "Spread: %s, %s blocks apart");
    public static final String T_SPREAD_POINT = GBLang.add(P + "spread_point", "Spread: all on the aim");
    public static final String[] T_PATTERN = {GBLang.add(P + "pattern.point", "point"), GBLang.add(P + "pattern.line", "line"),
            GBLang.add(P + "pattern.ring", "ring"), GBLang.add(P + "pattern.grid", "grid")};

    /** JEI's page for the buses and the decoy. */
    public static final String INFO = GBLang.add(GroundBurst.MODID + ".jei.info.mirv",
            "A MIRV carries a bus of re-entry vehicles in its warhead's place: the Heavy MIRV Bus on a Heavy body, the Strategic "
                    + "MIRV Bus on a Strategic one, built at their missile assemblers. Use a bus to load it: the Heavy bus takes up to 6 "
                    + "Light or Medium warheads (load 12: Light 2, Medium 4), the Strategic bus up to 8 of the missile-borne nuclear "
                    + "warheads (load 16: Tactical Nuclear and Neutron 2, Fission 4, Boosted Fission 6), and either takes RV Decoys (1). "
                    + "Set its spread there too: point, line, ring or grid, up to 150 blocks apart. Coming down from the top of its arc "
                    + "the bus lets its RVs go one by one, each falling white-hot on its own point round the aim, each warhead by its "
                    + "own fuze. Interceptors must kill every RV, and cannot tell a decoy from a warhead.");

    private Mirv() {}

    /** Whether a stack is a MIRV bus. */
    public static boolean isBus(ItemStack stack) {
        return stack.getItem() instanceof MirvBusItem;
    }

    /** A bus's class (null: not a bus). */
    public static @org.jspecify.annotations.Nullable SizeClass classOf(ItemStack stack) {
        return stack.getItem() instanceof MirvBusItem bus ? bus.size : null;
    }

    /** How many RVs a bus of {@code size} has mounts for. */
    public static int mounts(SizeClass size) {
        return size == SizeClass.XL ? 8 : 6;
    }

    /** How much a bus of {@code size} carries, in all. */
    public static int capacity(SizeClass size) {
        return size == SizeClass.XL ? 16 : 12;
    }

    /**
     * How much of a {@code bus}'s load an RV takes (0: that bus does not take it). A decoy 1 on either. The Heavy bus: a
     * Light conventional warhead 2, a Medium one 4. The Strategic bus, only the missile-borne nuclear warheads: a
     * Tactical Nuclear or a Neutron Warhead 2, a Fission Warhead 4, a Boosted Fission Warhead 6.
     */
    public static int load(SizeClass bus, ItemStack rv) {
        if (rv.isEmpty()) {
            return 0;
        }
        if (rv.getItem() instanceof MirvDecoyItem) {
            return 1;
        }
        ChargeKind kind = AssemblyStats.kindOf(rv);
        SizeClass size = MissileAssembly.warheadClassOf(rv);
        if (kind == null || size == null) {
            return 0;
        }
        if (bus == SizeClass.XL) {
            return switch (kind) {
                case TACTICAL_NUCLEAR_WARHEAD, NEUTRON_WARHEAD -> 2;
                case FISSION_WARHEAD -> 4;
                case BOOSTED_FISSION_WARHEAD -> 6;
                default -> 0;
            };
        }
        if (kind.strategic() || kind.wholeWeapon()) {
            return 0;
        }
        return size == SizeClass.S ? 2 : size == SizeClass.M ? 4 : 0;
    }

    /** What a bus holds, mount by mount (empty mounts empty). */
    public static NonNullList<ItemStack> mountsOf(ItemStack bus) {
        int n = bus.getItem() instanceof MirvBusItem b ? mounts(b.size) : 0;
        NonNullList<ItemStack> out = NonNullList.withSize(n, ItemStack.EMPTY);
        bus.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(out);
        return out;
    }

    /** The RVs a bus holds, in mount order (its empty mounts skipped): the order they leave it in. */
    public static List<ItemStack> rvs(ItemStack bus) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack s : mountsOf(bus)) {
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return out;
    }

    /** How much of its load a bus carries. */
    public static int loadOf(ItemStack bus) {
        SizeClass size = classOf(bus);
        return size == null ? 0 : rvs(bus).stream().mapToInt(s -> load(size, s)).sum();
    }

    /** A bus's spread (a new one's: a ring, 16 blocks apart for a Heavy bus, 40 for a Strategic one). */
    public static Spread spread(ItemStack bus) {
        Spread s = bus.get(GBDataComponents.MIRV_SPREAD.get());
        return s != null ? s : defaultSpread(classOf(bus));
    }

    public static Spread defaultSpread(@org.jspecify.annotations.Nullable SizeClass size) {
        return new Spread(LaunchNetwork.Pattern.RING, size == SizeClass.XL ? 40 : 16);
    }

    /**
     * Where a bus's RVs come down, in the order they leave it: its spread's points round x/z (the station's pattern for
     * as many launchers as it has RVs, all at {@code from}, so a line lies across the way it flies).
     */
    public static List<int[]> points(ItemStack bus, BlockPos from, int x, int z) {
        int n = rvs(bus).size();
        Spread s = spread(bus);
        return LaunchNetwork.points(Collections.nCopies(n, from), x, z, s.pattern(), s.spacing());
    }

    /** What a bus carries, as its tooltip and a MIRV's say: its load, its RVs by kind, its spread. */
    public static List<Component> describe(ItemStack bus) {
        List<Component> out = new ArrayList<>();
        SizeClass size = classOf(bus);
        List<ItemStack> rvs = rvs(bus);
        if (size == null) {
            return out;
        }
        if (rvs.isEmpty()) {
            out.add(Component.translatable(T_EMPTY).withStyle(ChatFormatting.GRAY));
            return out;
        }
        out.add(Component.translatable(T_LOAD, loadOf(bus), capacity(size), rvs.size()).withStyle(ChatFormatting.GOLD));
        Map<String, int[]> counts = new LinkedHashMap<>();
        Map<String, Component> names = new LinkedHashMap<>();
        for (ItemStack rv : rvs) {
            String key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(rv.getItem()).toString();
            counts.computeIfAbsent(key, k -> new int[1])[0]++;
            names.putIfAbsent(key, rv.getHoverName());
        }
        counts.forEach((key, n) -> out.add(Component.literal("  ").append(Component.translatable(T_RV, n[0], names.get(key)))
                .withStyle(ChatFormatting.GRAY)));
        Spread s = spread(bus);
        out.add((s.pattern() == LaunchNetwork.Pattern.POINT || s.spacing() == 0 || rvs.size() < 2 ? Component.translatable(T_SPREAD_POINT)
                : Component.translatable(T_SPREAD, Component.translatable(T_PATTERN[s.pattern().ordinal()]), s.spacing()))
                .withStyle(ChatFormatting.GRAY));
        return out;
    }
}
