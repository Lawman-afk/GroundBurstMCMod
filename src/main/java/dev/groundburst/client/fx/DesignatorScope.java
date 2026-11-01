package dev.groundburst.client.fx;

import org.jspecify.annotations.Nullable;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.launch.DesignatorPayloads;
import dev.groundburst.content.launch.Designators;
import dev.groundburst.content.launch.LaserDesignatorItem;
import dev.groundburst.content.launch.LaunchCodesItem;
import dev.groundburst.registry.GBLang;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The Laser Designator's sight (DESIGN.md §17.13), while we lase in first person: a 2x zoom (and the aim slowed to
 * match), the hand put away, an eyepiece with a lit reticle, and a rangefinder's readouts: the range to the spot and
 * where it is, what it is on, the mode, the network and the charge; and from the server, the call-for-fire lock, the
 * armed launchers of the network that reach the spot and the missiles homing on it. Lasing in third person, the
 * readouts in a strip over the hotbar.
 */
public final class DesignatorScope {
    static final Identifier EYEPIECE = GroundBurst.id("textures/gui/designator_scope.png");
    private static final int EYEPIECE_SIZE = 512;
    /** The eyepiece's opening, as a share of its texture's side (the texture's own). */
    private static final float APERTURE = 0.44f;
    private static final float ZOOM = 0.5f;
    /** Ticks the server's status is believed (it comes every {@link LaserDesignatorItem#STATUS}). */
    private static final int STALE = 20;
    private static final int AMBER = 0xFFFFB547, DIM = 0xFFB07A34, RED = 0xFFFF4A36, RETICLE = 0xD8FFA63A;

    private static final String P = GroundBurst.MODID + ".scope.";
    private static final String[] T_MODE = {GBLang.add(P + "designate", "DESIGNATE"), GBLang.add(P + "fire", "CALL FOR FIRE")};
    private static final String T_NET = GBLang.add(P + "net", "NET %s");
    private static final String T_NO_NET = GBLang.add(P + "no_net", "NO NET");
    private static final String T_RANGE = GBLang.add(P + "range", "RNG %s m");
    private static final String T_NO_RETURN = GBLang.add(P + "no_return", "RNG ---");
    private static final String T_WHERE = GBLang.add(P + "where", "X %s  Y %s  Z %s");
    private static final String T_ON = GBLang.add(P + "on", "ON %s");
    private static final String T_ARMED = GBLang.add(P + "armed", "ARMED %s");
    private static final String T_HOMING = GBLang.add(P + "homing", "HOMING %s");
    private static final String T_BATTERY = GBLang.add(P + "battery", "BAT %s%%");
    private static final String T_LOCKING = GBLang.add(P + "locking", "LOCKING %s%%");
    private static final String T_FIRED = GBLang.add(P + "fired", "FIRE CALLED");

    private static DesignatorPayloads.@Nullable Status status;
    private static int statusAge = Integer.MAX_VALUE;

    private DesignatorScope() {}

    public static void register(IEventBus modBus) {
        DesignatorPayloads.statusHook = s -> {
            status = s;
            statusAge = 0;
        };
        modBus.addListener((RegisterGuiLayersEvent event) ->
                event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, GroundBurst.id("designator_scope"), DesignatorScope::draw));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
            if (statusAge < Integer.MAX_VALUE) {
                statusAge++;
            }
        });
        NeoForge.EVENT_BUS.addListener((ComputeFovModifierEvent event) -> {
            Minecraft mc = Minecraft.getInstance();
            if (event.getPlayer() == mc.player && LaserFx.scoped(mc)) {
                event.setNewFovModifier(event.getNewFovModifier() * ZOOM);
            }
        });
        NeoForge.EVENT_BUS.addListener((CalculatePlayerTurnEvent event) -> {
            if (LaserFx.scoped(Minecraft.getInstance())) {
                event.setMouseSensitivity(slowed(event.getMouseSensitivity()));
            }
        });
        NeoForge.EVENT_BUS.addListener((RenderHandEvent event) -> {
            if (LaserFx.scoped(Minecraft.getInstance())) {
                event.setCanceled(true);                   // held to the eye: no hand in the sight
            }
        });
        NeoForge.EVENT_BUS.addListener((RenderGuiLayerEvent.Pre event) -> {
            if ((event.getName().equals(VanillaGuiLayers.CROSSHAIR) || event.getName().equals(VanillaGuiLayers.SELECTED_ITEM_NAME))
                    && LaserFx.scoped(Minecraft.getInstance())) {
                event.setCanceled(true);                   // the reticle is the crosshair; the readouts are where the item's name goes
            }
        });
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> {
            status = null;
            statusAge = Integer.MAX_VALUE;
        });
    }

    /** The server's last word on our designator, while it is fresh. */
    static DesignatorPayloads.@Nullable Status status() {
        return statusAge <= STALE ? status : null;
    }

    /**
     * The sensitivity that turns the view half as fast at half the field of view (the turn goes as the cube of
     * {@code 0.6 s + 0.2}), so the aim feels the same zoomed.
     */
    static double slowed(double sensitivity) {
        return (Math.cbrt(ZOOM) * (sensitivity * 0.6 + 0.2) - 0.2) / 0.6;
    }

    private static void draw(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.getCameraEntity() != player || !LaserFx.lasing(player)) {
            return;
        }
        float partial = delta.getGameTimeDeltaPartialTick(false);
        Vec3 eye = player.getEyePosition(partial);
        Designators.Hit hit = LaserFx.ourHit(mc, partial);                 // the beam's own trace, as far as we see
        Readout r = readout(player, player.getUseItem(), hit, eye);
        if (mc.options.getCameraType().isFirstPerson()) {
            eyepiece(g, mc.font, r, player.tickCount + partial);
        } else {
            strip(g, mc.font, r);
        }
    }

    /** What the readouts say this frame. */
    private record Readout(Component mode, boolean fire, Component net, Component range, @Nullable Component where, @Nullable Component on,
            @Nullable Component armed, @Nullable Component homing, Component battery, float lock, boolean fired) {}

    private static Readout readout(LocalPlayer player, ItemStack stack, Designators.@Nullable Hit hit, Vec3 eye) {
        DesignatorPayloads.Status s = status();
        LaserDesignatorItem.Mode mode = LaserDesignatorItem.mode(stack);
        boolean fire = mode == LaserDesignatorItem.Mode.FIRE;
        String code = s != null ? s.network() : LaunchCodesItem.code(stack);
        Component net = code.isEmpty() ? Component.translatable(T_NO_NET) : Component.translatable(T_NET, code);
        Component range = hit == null ? Component.translatable(T_NO_RETURN) : Component.translatable(T_RANGE, Math.round(eye.distanceTo(hit.surface())));
        Component where = hit == null ? null : Component.translatable(T_WHERE, Mth.floor(hit.pos().x), Mth.floor(hit.pos().y), Mth.floor(hit.pos().z));
        Component on = hit == null || hit.entity() == null ? null : Component.translatable(T_ON, hit.entity().getName());
        boolean linked = s != null && !s.network().isEmpty();
        Component armed = linked ? Component.translatable(T_ARMED, s.armed()) : null;
        Component homing = s != null && (linked || s.homing() > 0) ? Component.translatable(T_HOMING, s.homing()) : null;
        int charge = (int) ((long) LaserDesignatorItem.stored(stack) * 100 / LaserDesignatorItem.CAPACITY);
        float lock = s != null && fire ? s.lock() : 0;
        return new Readout(Component.translatable(T_MODE[mode.ordinal()]), fire, net, range, where, on, armed, homing,
                Component.translatable(T_BATTERY, charge), lock, s != null && fire && s.fired());
    }

    /** The sight: the eyepiece over the view, black round it, the reticle and the readouts within. */
    private static void eyepiece(GuiGraphicsExtractor g, Font font, Readout r, float time) {
        int w = g.guiWidth(), h = g.guiHeight();
        int size = Math.min(w, h);
        int left = (w - size) / 2, top = (h - size) / 2;
        g.blit(RenderPipelines.GUI_TEXTURED, EYEPIECE, left, top, 0, 0, size, size, EYEPIECE_SIZE, EYEPIECE_SIZE, EYEPIECE_SIZE, EYEPIECE_SIZE);
        g.fill(RenderPipelines.GUI, 0, 0, w, top, 0xFF000000);
        g.fill(RenderPipelines.GUI, 0, top + size, w, h, 0xFF000000);
        g.fill(RenderPipelines.GUI, 0, top, left, top + size, 0xFF000000);
        g.fill(RenderPipelines.GUI, left + size, top, w, top + size, 0xFF000000);
        int cx = w / 2, cy = h / 2;
        int radius = (int) (size * APERTURE);
        reticle(g, cx, cy, radius);
        boolean blink = (int) (time / 4) % 2 == 0;
        if (r.fire() && (r.lock() > 0 || r.fired())) {
            lockBox(g, cx, cy, r.fired() ? 1 : r.lock(), r.fired() && !blink);
        }
        // Along the top: the mode and the network.
        int topY = cy - (int) (radius * 0.80);
        int span = (int) (radius * 0.56);
        g.text(font, r.mode(), cx - span, topY, r.fire() ? RED : AMBER, true);
        g.text(font, r.net(), cx + span - font.width(r.net()), topY, DIM, true);
        // By the middle, clear of the lines and the brackets: the lock, in call-for-fire mode.
        if (r.fired()) {
            if (blink) {
                g.text(font, Component.translatable(T_FIRED), cx + 12, cy + 8, RED, true);
            }
        } else if (r.lock() > 0) {
            g.text(font, Component.translatable(T_LOCKING, Math.round(r.lock() * 100)), cx + 12, cy + 8, AMBER, true);
        }
        // Below: the rangefinder, and the system.
        int y = cy + (int) (radius * 0.40);
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(cx, y);
        pose.scale(1.5f, 1.5f);
        g.text(font, r.range(), -font.width(r.range()) / 2, 0, AMBER, true);
        pose.popMatrix();
        y += 15;
        if (r.where() != null) {
            centred(g, font, r.where(), cx, y, AMBER);
        }
        y += 10;
        if (r.on() != null) {
            centred(g, font, r.on(), cx, y, AMBER);
        }
        y += 11;
        Component system = system(r);
        centred(g, font, system, cx, y, DIM);
    }

    /** The armed launchers, the missiles homing and the charge, in one line. */
    private static Component system(Readout r) {
        Component line = r.battery();
        if (r.homing() != null) {
            line = Component.empty().append(r.homing()).append("   ").append(line);
        }
        if (r.armed() != null) {
            line = Component.empty().append(r.armed()).append("   ").append(line);
        }
        return line;
    }

    /**
     * A lit duplex reticle: thin lines with a gap at the middle, heavy posts from the sides and the top out to the edge
     * (none below, where the readouts are), marks every few degrees; the spot itself is the aim point.
     */
    private static void reticle(GuiGraphicsExtractor g, int cx, int cy, int radius) {
        int gap = 4, inner = (int) (radius * 0.32);
        g.fill(cx - inner, cy, cx - gap, cy + 1, RETICLE);
        g.fill(cx + gap + 1, cy, cx + inner + 1, cy + 1, RETICLE);
        g.fill(cx, cy - inner, cx + 1, cy - gap, RETICLE);
        g.fill(cx, cy + gap + 1, cx + 1, cy + inner + 1, RETICLE);
        g.fill(cx - radius, cy - 1, cx - inner, cy + 2, RETICLE);
        g.fill(cx + inner + 1, cy - 1, cx + radius + 1, cy + 2, RETICLE);
        g.fill(cx - 1, cy - radius, cx + 2, cy - inner, RETICLE);
        int step = Math.max(6, inner / 4);
        for (int d = step; d < inner - 2; d += step) {
            g.fill(cx - d, cy - 2, cx - d + 1, cy + 3, RETICLE);
            g.fill(cx + d, cy - 2, cx + d + 1, cy + 3, RETICLE);
            g.fill(cx - 2, cy + d, cx + 3, cy + d + 1, RETICLE);
        }
    }

    /** Call for fire: brackets closing on the middle as the lock builds, red once locked. */
    private static void lockBox(GuiGraphicsExtractor g, int cx, int cy, float lock, boolean dim) {
        int half = 7 + Math.round(24 * (1 - lock));
        int arm = Math.max(3, half / 3);
        int c = lock >= 1 ? (dim ? 0x80FF4A36 : RED) : AMBER;
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                int x = cx + sx * half, y = cy + sy * half;
                int x0 = Math.min(x, x - sx * arm), x1 = Math.max(x, x - sx * arm) + 1;
                int y0 = Math.min(y, y - sy * arm), y1 = Math.max(y, y - sy * arm) + 1;
                g.fill(x0, y, x1, y + 1, c);
                g.fill(x, y0, x + 1, y1, c);
            }
        }
    }

    /** Lasing in third person: the range, the lock and the system under the crosshair. */
    private static void strip(GuiGraphicsExtractor g, Font font, Readout r) {
        int cx = g.guiWidth() / 2, y = g.guiHeight() / 2 + 14;
        Component first = Component.empty().append(r.mode()).append("   ").append(r.range());
        if (r.fired()) {
            first = Component.empty().append(first).append("   ").append(Component.translatable(T_FIRED));
        } else if (r.lock() > 0) {
            first = Component.empty().append(first).append("   ").append(Component.translatable(T_LOCKING, Math.round(r.lock() * 100)));
        }
        centred(g, font, first, cx, y, r.fire() ? RED : AMBER);
        centred(g, font, system(r), cx, y + 10, DIM);
    }

    private static void centred(GuiGraphicsExtractor g, Font font, Component text, int cx, int y, int color) {
        g.text(font, text, cx - font.width(text) / 2, y, color, true);
    }
}
