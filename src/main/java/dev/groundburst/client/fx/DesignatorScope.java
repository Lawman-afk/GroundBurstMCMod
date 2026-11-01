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
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The Laser Designator's sight (DESIGN.md §17.13), while we lase in first person: a 2x zoom (and the aim slowed to
 * match), the hand put away, an eyepiece with a lit reticle, and a rangefinder's readouts: the range to the spot and
 * where it is, what it is on, the mode, the network and the charge; from the server, the network's laser-guided
 * launchers that could be armed on the spot and those armed, the missiles homing, and what a left click did; what a
 * left click will do. Lasing in third person, the readouts in a strip under the crosshair. A left click while lasing
 * (which the game would otherwise drop while an item is in use) goes to the server here.
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
    private static final String T_NET = GBLang.add(P + "net", "NET %s");
    private static final String T_NO_NET = GBLang.add(P + "no_net", "NO NET");
    private static final String T_RANGE = GBLang.add(P + "range", "RNG %s m");
    private static final String T_NO_RETURN = GBLang.add(P + "no_return", "RNG ---");
    private static final String T_WHERE = GBLang.add(P + "where", "X %s  Y %s  Z %s");
    private static final String T_ON = GBLang.add(P + "on", "ON %s");
    private static final String T_HOMING = GBLang.add(P + "homing", "HOMING %s");
    private static final String T_BATTERY = GBLang.add(P + "battery", "BAT %s%%");
    /** The launcher it works with: its number, its kind, its place among the network's laser-guided ones. */
    private static final String T_PICK = GBLang.add(P + "pick", "#%s %s  %s/%s");
    private static final String T_NO_PICK = GBLang.add(P + "no_pick", "NO LASER MISSILE");
    /** That launcher's state (a switch: a state added later cannot go without one). */
    private static final java.util.EnumMap<LaserDesignatorItem.Pick, String> T_STATE = new java.util.EnumMap<>(LaserDesignatorItem.Pick.class);
    /** A left click's result's text (a switch: a result added later cannot go without one). */
    private static final java.util.EnumMap<LaserDesignatorItem.Result, String> T_RESULT = new java.util.EnumMap<>(LaserDesignatorItem.Result.class);

    static {
        for (LaserDesignatorItem.Result r : LaserDesignatorItem.Result.values()) {
            T_RESULT.put(r, GBLang.add(P + "result." + r.name().toLowerCase(java.util.Locale.ROOT), switch (r) {
                case NONE -> "";
                case DESIGNATED -> "#%s ARMED ON IT";
                case OUT_OF_REACH -> "#%s CAN'T REACH IT";
                case NOT_READY -> "#%s NOT READY";
                case FIRED -> "#%s FIRED";
                case NOT_ARMED -> "#%s NOT ARMED";
                case NO_LAUNCHER -> "NO LASER MISSILE";
                case NO_RETURN -> "NO RETURN";
                case NO_NETWORK -> "NO NETWORK";
                case DISARMED -> "#%s DISARMED";
            }));
        }
        for (LaserDesignatorItem.Pick k : LaserDesignatorItem.Pick.values()) {
            T_STATE.put(k, GBLang.add(P + "state." + k.name().toLowerCase(java.util.Locale.ROOT), switch (k) {
                case NONE -> "";
                case READY -> "READY";
                case OUT_OF_REACH -> "OUT OF REACH";
                case NOT_READY -> "NOT READY";
                case ARMED -> "ARMED";
            }));
        }
    }

    /** Ticks a left click's brackets snap in over; ticks between left clicks sent. */
    private static final int SNAP = 8, CLICK_GAP = 4;
    /**
     * Our last left click while lasing: when (game time), whether it was to launch (its launcher was armed) and whether
     * it was to disarm (sneaking).
     */
    private static long clickedAt = Long.MIN_VALUE / 2;
    private static boolean clickedLaunch, clickedDisarm;

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
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Pre event) -> clicks(Minecraft.getInstance()));
        NeoForge.EVENT_BUS.addListener((InputEvent.MouseScrollingEvent event) -> {
            Minecraft mc = Minecraft.getInstance();
            if (LaserFx.scoped(mc)) {
                event.setCanceled(true);                   // zoomed, the wheel picks a launcher, not a hotbar slot
                scroll(mc, event.getScrollDeltaY());
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

    /**
     * Left clicks while lasing: taken before the game would drop them (it ignores attacks while an item is in use) and
     * sent to the server, which arms, launches or (sneaking) disarms the designator's launcher.
     */
    private static void clicks(Minecraft mc) {
        if (mc.player == null || mc.level == null || mc.gui.screen() != null || !LaserFx.lasing(mc.player)) {
            return;
        }
        boolean clicked = false;
        while (mc.options.keyAttack.consumeClick()) {
            clicked = true;
        }
        long now = mc.level.getGameTime();
        if (clicked && now - clickedAt >= CLICK_GAP) {
            boolean disarm = mc.player.isShiftKeyDown();
            ClientPacketDistributor.sendToServer(new DesignatorPayloads.Click(disarm));
            clickedAt = now;
            DesignatorPayloads.Status s = status();
            clickedDisarm = disarm;
            clickedLaunch = !disarm && s != null && s.pick().state() == LaserDesignatorItem.Pick.ARMED.ordinal();
        }
    }

    /** The mouse wheel while zoomed: down the next laser-guided launcher, up the one before (the server keeps the pick). */
    static void scroll(Minecraft mc, double delta) {
        if (delta != 0 && mc.player != null) {
            ClientPacketDistributor.sendToServer(new DesignatorPayloads.Scroll(delta < 0 ? 1 : -1));
        }
    }

    /** Ticks since our last left click while lasing (with the frame's part), and its mode, for the spot's ring. */
    static float sinceClick(Minecraft mc, float partial) {
        return mc.level == null ? Float.MAX_VALUE : (float) (mc.level.getGameTime() - clickedAt) + partial;
    }

    static boolean clickedFire() {
        return clickedLaunch;
    }

    static boolean clickedDisarm() {
        return clickedDisarm;
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
            eyepiece(g, mc.font, r, player.tickCount + partial, sinceClick(mc, partial));
        } else {
            strip(g, mc.font, r);
        }
    }

    /**
     * What the readouts say this frame: the launcher it works with, and by the middle its state, or a left click's
     * result for a while after it.
     */
    private record Readout(Component net, Component range, @Nullable Component where, @Nullable Component on, Component pick, boolean armed,
            @Nullable Component homing, Component battery, Component action, int actionColour) {}

    private static Readout readout(LocalPlayer player, ItemStack stack, Designators.@Nullable Hit hit, Vec3 eye) {
        DesignatorPayloads.Status s = status();
        String code = s != null ? s.network() : LaunchCodesItem.code(stack);
        Component net = code.isEmpty() ? Component.translatable(T_NO_NET) : Component.translatable(T_NET, code);
        Component range = hit == null ? Component.translatable(T_NO_RETURN) : Component.translatable(T_RANGE, Math.round(eye.distanceTo(hit.surface())));
        Component where = hit == null ? null : Component.translatable(T_WHERE, Mth.floor(hit.pos().x), Mth.floor(hit.pos().y), Mth.floor(hit.pos().z));
        Component on = hit == null || hit.entity() == null ? null : Component.translatable(T_ON, hit.entity().getName());
        boolean linked = s != null && !s.network().isEmpty();
        DesignatorPayloads.Pick p = s == null ? DesignatorPayloads.Pick.NONE : s.pick();
        Component pick = !linked || p.number() == 0 ? Component.translatable(T_NO_PICK)
                : Component.translatable(T_PICK, p.number(), dev.groundburst.client.screen.LaunchStationScreen.kind(p.kind()), p.index(), p.total());
        Component homing = linked || s != null && s.homing() > 0 ? Component.translatable(T_HOMING, s.homing()) : null;
        int charge = (int) ((long) LaserDesignatorItem.stored(stack) * 100 / LaserDesignatorItem.CAPACITY);
        LaserDesignatorItem.Result[] results = LaserDesignatorItem.Result.values();
        LaserDesignatorItem.Result result = s == null ? LaserDesignatorItem.Result.NONE : results[Math.clamp(s.result(), 0, results.length - 1)];
        LaserDesignatorItem.Pick[] states = LaserDesignatorItem.Pick.values();
        LaserDesignatorItem.Pick state = states[Math.clamp(p.state(), 0, states.length - 1)];
        Component action = result != LaserDesignatorItem.Result.NONE ? Component.translatable(T_RESULT.get(result), s.count())
                : Component.translatable(T_STATE.get(state));
        boolean armed = state == LaserDesignatorItem.Pick.ARMED;
        int colour = result == LaserDesignatorItem.Result.FIRED ? RED : result != LaserDesignatorItem.Result.NONE ? AMBER
                : armed ? RED : state == LaserDesignatorItem.Pick.READY ? AMBER : DIM;
        return new Readout(net, range, where, on, pick, armed, homing, Component.translatable(T_BATTERY, charge), action, colour);
    }

    /** The sight: the eyepiece over the view, black round it, the reticle and the readouts within. */
    private static void eyepiece(GuiGraphicsExtractor g, Font font, Readout r, float time, float sinceClick) {
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
        if (sinceClick >= 0 && sinceClick < SNAP * 2) {
            float k = Math.min(1, sinceClick / SNAP);
            if (clickedDisarm()) {
                brackets(g, cx, cy, 1 - k, DIM);           // let go: the brackets open out off the middle
            } else {
                brackets(g, cx, cy, k, clickedFire() ? RED : AMBER);
            }
        }
        // Along the top: the launcher it works with, and the network under it (side by side they would not fit).
        int topY = cy - (int) (radius * 0.80);
        centred(g, font, r.pick(), cx, topY, r.armed() ? RED : AMBER);
        centred(g, font, r.net(), cx, topY + 11, DIM);
        // By the middle, clear of the lines: its launcher's state, or what the last left click did.
        g.text(font, r.action(), cx + 12, cy + 8, r.actionColour(), true);
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

    /** The missiles homing and the charge, in one line. */
    private static Component system(Readout r) {
        return r.homing() == null ? r.battery() : Component.empty().append(r.homing()).append("   ").append(r.battery());
    }

    /**
     * A lit duplex reticle: thin lines with a gap at the middle, heavy posts from the sides out to the edge and a short
     * one above (none below, where the readouts are), marks every few degrees; the spot itself is the aim point.
     */
    private static void reticle(GuiGraphicsExtractor g, int cx, int cy, int radius) {
        int gap = 4, inner = (int) (radius * 0.32);
        g.fill(cx - inner, cy, cx - gap, cy + 1, RETICLE);
        g.fill(cx + gap + 1, cy, cx + inner + 1, cy + 1, RETICLE);
        g.fill(cx, cy - inner, cx + 1, cy - gap, RETICLE);
        g.fill(cx, cy + gap + 1, cx + 1, cy + inner + 1, RETICLE);
        g.fill(cx - radius, cy - 1, cx - inner, cy + 2, RETICLE);
        g.fill(cx + inner + 1, cy - 1, cx + radius + 1, cy + 2, RETICLE);
        g.fill(cx - 1, cy - (int) (radius * 0.56), cx + 2, cy - inner, RETICLE);     // (stopping short of the lines above)
        int step = Math.max(6, inner / 4);
        for (int d = step; d < inner - 2; d += step) {
            g.fill(cx - d, cy - 2, cx - d + 1, cy + 3, RETICLE);
            g.fill(cx + d, cy - 2, cx + d + 1, cy + 3, RETICLE);
            g.fill(cx - 2, cy + d, cx + 3, cy + d + 1, RETICLE);
        }
    }

    /** A left click: brackets snapping in on the middle ({@code done} 0 to 1), then held a moment; red for fire. */
    private static void brackets(GuiGraphicsExtractor g, int cx, int cy, float done, int c) {
        int half = 7 + Math.round(24 * (1 - done));
        int arm = Math.max(3, half / 3);
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

    /** Lasing in third person: the mode, the range and the action, and the system, under the crosshair. */
    private static void strip(GuiGraphicsExtractor g, Font font, Readout r) {
        int cx = g.guiWidth() / 2, y = g.guiHeight() / 2 + 14;
        Component first = Component.empty().append(r.pick()).append("   ").append(r.range()).append("   ").append(r.action());
        centred(g, font, first, cx, y, r.armed() ? RED : AMBER);
        centred(g, font, Component.empty().append(r.net()).append("   ").append(system(r)), cx, y + 10, DIM);
    }

    private static void centred(GuiGraphicsExtractor g, Font font, Component text, int cx, int y, int color) {
        g.text(font, text, cx - font.width(text) / 2, y, color, true);
    }
}
