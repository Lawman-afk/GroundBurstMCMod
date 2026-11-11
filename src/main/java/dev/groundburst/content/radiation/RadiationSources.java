package dev.groundburst.content.radiation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

import dev.groundburst.GroundBurst;
import dev.groundburst.network.GBNetwork;
import net.minecraft.network.FriendlyByteBuf;
import dev.groundburst.legacy.net.StreamCodec;
import dev.groundburst.legacy.net.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;
import dev.groundburst.port.SavedDataType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.TickEvent;
import dev.groundburst.legacy.net.PacketDistributor;

/**
 * Every {@link RadiationSource} in a level (DESIGN.md §21.8), as the level's saved data ({@code radiation_sources.dat}).
 * Sources add up where they overlap. Once a second the ones that have ended are dropped; clients are told of the whole
 * list whenever it changes, and a player of it on arriving in the level, to draw the fallout overhead.
 */
public final class RadiationSources extends SavedData {
    static final SavedDataType<RadiationSources> TYPE = new SavedDataType<>(GroundBurst.id("radiation_sources"), RadiationSources::new,
            RadiationSource.CODEC.listOf().xmap(RadiationSources::new, s -> List.copyOf(s.sources)));
    private static final Map<ServerLevel, RadiationSources> BY_LEVEL = new WeakHashMap<>();

    private final List<RadiationSource> sources = new ArrayList<>();
    /** The list changed since clients were last told (they are told within a second). */
    private boolean unsent;

    private RadiationSources() {}

    private RadiationSources(List<RadiationSource> sources) {
        this.sources.addAll(sources);
    }

    public static RadiationSources of(ServerLevel level) {
        return BY_LEVEL.computeIfAbsent(level, l -> TYPE.get(l));
    }

    static void register() {
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.TickEvent.LevelTickEvent event) -> {if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
            if (((Object) event.level) instanceof ServerLevel level && level.getGameTime() % 20 == 0) {
                of(level).tick(level);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((LevelEvent.Unload event) -> {
            if (((Object) event.getLevel()) instanceof ServerLevel level) {
                BY_LEVEL.remove(level);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> tell(event.getEntity()));
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent event) -> tell(event.getEntity()));
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent event) -> tell(event.getEntity()));
    }

    private static void tell(Player player) {
        if (((Object) player) instanceof ServerPlayer p) {
            PacketDistributor.sendToPlayer(p, new Sync(of((ServerLevel) p.level()).sources()));
        }
    }

    public void add(RadiationSource source) {
        this.sources.add(source);
        this.unsent = true;
        setDirty();
    }

    /** For GameTests: removes the sources centred within {@code within} of {@code centre}. */
    public void removeNear(Vec3 centre, double within) {
        if (this.sources.removeIf(s -> s.center().distanceTo(centre) <= within)) {
            this.unsent = true;
            setDirty();
        }
    }

    public List<RadiationSource> sources() {
        return List.copyOf(this.sources);
    }

    /** Healing (DESIGN.md §21.9): the sources centred in the chunk column at (cx, cz) go; how many did. */
    public int clearCentredIn(int cx, int cz) {
        int before = this.sources.size();
        if (this.sources.removeIf(s -> ((int) Math.floor(s.center().x) >> 4) == cx && ((int) Math.floor(s.center().z) >> 4) == cz)) {
            this.unsent = true;
            setDirty();
        }
        return before - this.sources.size();
    }

    /** The dose rate at {@code pos} now from every source, before shielding (RAD/h). */
    public float rateAt(ServerLevel level, Vec3 pos) {
        long now = level.getGameTime();
        float sum = 0;
        for (RadiationSource s : this.sources) {
            sum += s.rateAt(now, pos.distanceTo(s.center()));
        }
        return sum;
    }

    /** Whether {@code pos} lies inside any source (before decay). */
    public boolean inside(Vec3 pos) {
        for (RadiationSource s : this.sources) {
            if (pos.distanceToSqr(s.center()) < (double) s.radius() * s.radius()) {
                return true;
            }
        }
        return false;
    }

    private void tick(ServerLevel level) {
        long now = level.getGameTime();
        if (this.sources.removeIf(s -> s.ended(now))) {
            this.unsent = true;
            setDirty();
        }
        if (this.unsent) {
            this.unsent = false;
            PacketDistributor.sendToPlayersInDimension(level, new Sync(List.copyOf(this.sources)));
        }
    }

    /** Every source in the receiving player's level (empty: none). */
    public record Sync(List<RadiationSource> sources) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = GBNetwork.type("radiation_sources");
        public static final StreamCodec<FriendlyByteBuf, Sync> CODEC = RadiationSource.STREAM_CODEC
                .apply(dev.groundburst.legacy.net.ByteBufCodecs.list()).map(Sync::new, Sync::sources).cast();
        /** Client hook (set by the client). */
        public static Consumer<Sync> hook = s -> {};

        @Override
        public Type<Sync> type() {
            return TYPE;
        }
    }

    @Override
    public net.minecraft.nbt.CompoundTag save(net.minecraft.nbt.CompoundTag tag_) { net.minecraft.core.HolderLookup.Provider registries_ = dev.groundburst.legacy.Regs.access();
        return TYPE.save(this, tag_, registries_);
    }
}
