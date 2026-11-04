package dev.groundburst.client.fx;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import dev.groundburst.GroundBurst;
import dev.groundburst.client.manual.ManualHotkey;
import dev.groundburst.content.artillery.ArtilleryPayloads;
import dev.groundburst.content.artillery.ArtilleryShell;
import dev.groundburst.content.artillery.Ballistics;
import dev.groundburst.content.artillery.Calibre;
import dev.groundburst.content.artillery.GBArtillery;
import dev.groundburst.content.artillery.GunBlockEntity;
import dev.groundburst.content.artillery.GunSeat;
import dev.groundburst.content.artillery.ShellType;
import dev.groundburst.registry.GBLang;
import dev.groundburst.registry.GBSounds;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;

/**
 * A gun's crewman's side (DESIGN.md §20.1), World of Tanks' way of aiming artillery: manned, the gun follows where they
 * look; scrolled out (or V), a strategic view, a camera high over the ground looking down along the line of fire, the
 * mouse moving the aim over the ground and the wheel the height. The number keys pick the shell, left click fires. On
 * the ground the aiming circle, dashed (where the gun is laid, sliding toward the aim at the gun's pace; as wide as a
 * shot's spread now: white while it shrinks, green fully aimed, red out of reach or blocked), the aim's cross and, from
 * above, the shell's arc. Far ground the client has not loaded is drawn from the gun's map of it (ArtilleryTerrain).
 * The readout: the rack, the reload, range, time of flight, charge, elevation, spread.
 */
public final class ArtilleryClient {
    public static final KeyMapping VIEW = new KeyMapping(GBLang.add("key." + GroundBurst.MODID + ".artillery_view", "Artillery: strategic view"),
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYBOARD, InputConstants.KEY_V, ManualHotkey.CATEGORY);

    private static final String T_HINT_GUN = GBLang.add(GroundBurst.MODID + ".artillery.hint_gun",
            "[%s] or scroll out: strategic view   [Shift] leave");
    private static final String T_HINT_MAP = GBLang.add(GroundBurst.MODID + ".artillery.hint_map",
            "[Mouse] aim   [Scroll] height   [%s] or scroll in: gun view");
    private static final String T_HINT_FIRE = GBLang.add(GroundBurst.MODID + ".artillery.hint_fire_n", "[1-%s] shell   [Left click] fire");
    private static final String T_RANGE = GBLang.add(GroundBurst.MODID + ".artillery.range", "RANGE %s");
    private static final String T_TIME = GBLang.add(GroundBurst.MODID + ".artillery.time", "TIME %s s");
    private static final String T_CHARGE = GBLang.add(GroundBurst.MODID + ".artillery.charge", "CHARGE %s");
    private static final String T_ELEVATION = GBLang.add(GroundBurst.MODID + ".artillery.elevation", "ELEV %s°");
    private static final String T_SPREAD = GBLang.add(GroundBurst.MODID + ".artillery.spread", "SPREAD x%s");
    private static final String T_AIMED = GBLang.add(GroundBurst.MODID + ".artillery.aimed", "AIMED");
    private static final String T_AIMING = GBLang.add(GroundBurst.MODID + ".artillery.aiming", "AIMING");
    private static final String T_OUT = GBLang.add(GroundBurst.MODID + ".artillery.hud_out", "OUT OF RANGE");
    private static final String T_BLOCKED = GBLang.add(GroundBurst.MODID + ".artillery.blocked", "BLOCKED");
    private static final String T_LOADING = GBLang.add(GroundBurst.MODID + ".artillery.loading", "LOADING");

    static final Identifier RETICLE = GroundBurst.id("textures/fx/reticle.png");
    private static final int WHITE = 0xFFF0F0E8, GREY = 0xFF9A9E96, AMBER = 0xFFE8B030, GREEN = 0xFF7CE070, RED = 0xFFF05040;
    private static final int RING_WHITE = 0xF8F4F4EE, RING_GREEN = 0xF880F078, RING_RED = 0xF8FF5040;
    /** The strategic camera: how far over the ground (blocks; a far gun's goes higher), how steeply it looks down (degrees). */
    private static final float LOW = 24, STEP = 8, PITCH = 75;

    private static @Nullable BlockPos gun;
    private static boolean strategic;
    /** Where the crewman wants it laid (sent to the server), and what was sent last. */
    private static Vec3 cursor = Vec3.ZERO;
    private static @Nullable Vec3 sent;
    /** The gun view's aim is held (as it was laid, or as the strategic view left it) until the crewman looks round. */
    private static boolean held;
    private static float heldYaw, heldPitch;
    private static @Nullable Marker eye;
    private static float bearing, height = 48, neutralYaw, neutralPitch;
    private static @Nullable CameraType savedCamera;
    private static long seenFired = Long.MIN_VALUE;
    /** This frame's laying: where the gun is laid and how wide its circle is (drawn, and read by the readout). */
    private static @Nullable Laid laid;
    private static final IntOpenHashSet WHISTLING = new IntOpenHashSet();
    private static final Map<String, ItemStack> ICONS = new HashMap<>();

    private ArtilleryClient() {}

    /** Where the gun is laid this frame: the muzzle, where its shell would come down, the circle there, its colour. */
    private record Laid(Vec3 muzzle, Vec3 u, Ballistics.Fall fall, double range, double radius, float yaw, int colour, String status) {}

    public static void register(IEventBus modBus) {
        modBus.addListener((RegisterKeyMappingsEvent e) -> e.register(VIEW));
        modBus.addListener((RegisterGuiLayersEvent e) -> e.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, GroundBurst.id("artillery"), ArtilleryClient::hud));
        ArtilleryPayloads.stateHook = s -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && mc.level.isLoaded(s.gun()) && mc.level.getBlockEntity(s.gun()) instanceof GunBlockEntity m) {
                m.applyState(s);
            }
        };
        ArtilleryShell.clientHook = ArtilleryClient::shellTick;
        ArtilleryTerrain.register();
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Pre e) -> tick(Minecraft.getInstance()));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> WHISTLING.removeIf(id -> {
            ClientLevel level = Minecraft.getInstance().level;
            return level == null || !(level.getEntity(id) instanceof ArtilleryShell);
        }));
        NeoForge.EVENT_BUS.addListener(ArtilleryClient::angles);
        NeoForge.EVENT_BUS.addListener(ArtilleryClient::submit);
        NeoForge.EVENT_BUS.addListener((InputEvent.MouseScrollingEvent e) -> {
            if (gun != null && Minecraft.getInstance().gui.screen() == null) {
                e.setCanceled(true);
                scroll(Minecraft.getInstance(), e.getScrollDeltaY());
            }
        });
        NeoForge.EVENT_BUS.addListener((InputEvent.InteractionKeyMappingTriggered e) -> {
            if (gun != null) {
                e.setCanceled(true);                       // manning: the hands are on the gun (fire goes by tick)
                e.setSwingHand(false);
            }
        });
        NeoForge.EVENT_BUS.addListener((RenderHandEvent e) -> {
            if (gun != null) {
                e.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener((RenderGuiLayerEvent.Pre e) -> {
            if (gun == null) {
                return;
            }
            Identifier n = e.getName();
            if (n.equals(VanillaGuiLayers.HOTBAR) || n.equals(VanillaGuiLayers.SELECTED_ITEM_NAME) || strategic && n.equals(VanillaGuiLayers.CROSSHAIR)) {
                e.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
            Minecraft mc = Minecraft.getInstance();
            if (strategic) {
                leaveView(mc, false);
            }
            gun = null;
            laid = null;
            WHISTLING.clear();
            ArtilleryTerrain.clear();
        });
    }

    /** Whether the client is manning a gun. */
    public static boolean manning() {
        return gun != null;
    }

    /** The gun being manned and whether its strategic view is up (for the far ground's map). */
    static @Nullable BlockPos gunPos() {
        return gun;
    }

    static boolean strategic() {
        return strategic;
    }

    static Vec3 cursor() {
        return cursor;
    }

    /** Up into the strategic view over {@code at}, {@code over} blocks above it (the photographer's). */
    public static void photoView(Vec3 at, float over) {
        Minecraft mc = Minecraft.getInstance();
        if (gun == null) {
            return;
        }
        cursor = at;
        sent = null;
        held = true;
        if (!strategic) {
            enterView(mc);
        }
        GunBlockEntity m = gunEntity(mc);
        height = Mth.clamp(over, LOW, m == null ? 96 : high(m));
    }

    /** Back down from the strategic view (the photographer's). */
    public static void photoLeave() {
        if (strategic) {
            leaveView(Minecraft.getInstance(), false);
        }
    }

    static @Nullable GunBlockEntity gunEntity(Minecraft mc) {
        return gun != null && mc.level != null && mc.level.getBlockEntity(gun) instanceof GunBlockEntity m ? m : null;
    }

    /** How far the aim may wander from the gun (it lays only to its own reach). */
    private static double farthest(GunBlockEntity m) {
        return m.spec.maxRange() + 40;
    }

    /** How high the strategic camera may go over the ground: higher for a gun that reaches further. */
    private static float high(GunBlockEntity m) {
        return (float) Mth.clamp(m.spec.maxRange() / 8, 96, 200);
    }

    /** The ground's top at (x, z): the client's own, else the far ground's map, else {@code fallback}. */
    static double ground(ClientLevel level, double x, double z, double fallback) {
        if (level.isLoaded(new BlockPos(Mth.floor(x), level.getMinY(), Mth.floor(z)))) {
            return level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
        }
        double h = ArtilleryTerrain.height(x, z);
        return Double.isNaN(h) ? fallback : h;
    }

    // --- each tick ----------------------------------------------------------------------------------------------------
    private static void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        BlockPos now = p != null && mc.level != null && p.getVehicle() instanceof GunSeat seat
                && mc.level.getBlockEntity(seat.gun()) instanceof GunBlockEntity ? seat.gun() : null;
        if (now == null ? gun != null : !now.equals(gun)) {
            if (gun != null) {
                leave(mc);
            }
            if (now != null) {
                join(mc, now);
            }
        }
        GunBlockEntity m = gunEntity(mc);
        if (m == null || p == null) {
            return;
        }
        boolean free = mc.gui.screen() == null;
        for (int i = 0; i < m.spec.shells().size() && i < mc.options.keyHotbarSlots.length; i++) {
            while (mc.options.keyHotbarSlots[i].consumeClick()) {
                ClientPacketDistributor.sendToServer(new ArtilleryPayloads.Select(gun, i));
            }
        }
        for (int i = m.spec.shells().size(); i < mc.options.keyHotbarSlots.length; i++) {
            while (mc.options.keyHotbarSlots[i].consumeClick()) {
                // (no such shell: the hotbar does not change while manning)
            }
        }
        while (VIEW.consumeClick()) {
            if (strategic) {
                leaveView(mc, true);
            } else {
                enterView(mc);
            }
        }
        boolean fire = false;
        while (mc.options.keyAttack.consumeClick()) {
            fire = true;
        }
        while (mc.options.keyUse.consumeClick()) {
            // the hands are on the gun
        }
        if (strategic) {
            while (mc.options.keyTogglePerspective.consumeClick()) {
                // the strategic camera is its own
            }
        }
        if (fire && free) {
            ClientPacketDistributor.sendToServer(new ArtilleryPayloads.Fire(gun));
        }
        if (!strategic && free) {
            if (held && Math.abs(Mth.wrapDegrees(p.getYRot() - heldYaw)) + Math.abs(p.getXRot() - heldPitch) > 0.4f) {
                held = false;
            }
            if (!held) {
                Vec3 hit = lookHit(mc, p, m);
                if (hit != null) {
                    cursor = hit;
                }
            }
        }
        if (sent == null || cursor.distanceToSqr(sent) > 0.0025) {
            ClientPacketDistributor.sendToServer(new ArtilleryPayloads.Aim(gun, cursor));
            sent = cursor;
        }
        if (m.firedAt() != seenFired) {
            if (seenFired != Long.MIN_VALUE && strategic) {
                // From up there the gun is far below: its crewman hears it as if beside it.
                mc.getSoundManager().play(SimpleSoundInstance.forUI(m.spec.fireSound().get(), 1f, 0.55f));
            }
            seenFired = m.firedAt();
        }
        ArtilleryTerrain.tick(mc, m);
    }

    private static void join(Minecraft mc, BlockPos at) {
        gun = at;
        strategic = false;
        sent = null;
        GunBlockEntity m = gunEntity(mc);
        Vec3 on = m == null ? null : m.laid();
        laid = m == null ? null : lay(mc, m, 1);
        cursor = on != null ? on : laid != null ? laid.fall().at() : Vec3.atCenterOf(at);
        seenFired = m == null ? Long.MIN_VALUE : m.firedAt();
        hold(mc.player);
    }

    private static void leave(Minecraft mc) {
        if (strategic) {
            leaveView(mc, false);
        }
        gun = null;
        laid = null;
    }

    private static void hold(@Nullable LocalPlayer p) {
        held = true;
        if (p != null) {
            heldYaw = p.getYRot();
            heldPitch = p.getXRot();
        }
    }

    /** Where the crewman's look meets the ground, out to a little past the gun's reach. */
    private static @Nullable Vec3 lookHit(Minecraft mc, LocalPlayer p, GunBlockEntity m) {
        Vec3 from = p.getEyePosition(), look = p.getViewVector(1);
        BlockHitResult hit = mc.level.clip(new ClipContext(from.add(look.scale(1.5)), from.add(look.scale(farthest(m))), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.ANY, p));
        // A ship's block is looked at where it is seen (another mod's clip reports it in the ship's plot, far off).
        return hit.getType() == HitResult.Type.MISS ? null
                : dev.groundburst.content.machine.Frames.at(mc.level, hit.getBlockPos()).toWorld(hit.getLocation());
    }

    // --- the strategic view ---------------------------------------------------------------------------------------------
    private static void enterView(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || gun == null) {
            return;
        }
        Vec3 from = Vec3.atCenterOf(gun);
        double dx = cursor.x - from.x, dz = cursor.z - from.z;
        bearing = dx * dx + dz * dz < 4 ? p.getYRot() : (float) Math.toDegrees(Math.atan2(-dx, dz));
        GunBlockEntity m = gunEntity(mc);
        height = m == null ? 48 : (float) Mth.clamp(Math.hypot(dx, dz) / 5, 48, high(m));
        held = true;
        eye = new Marker(EntityTypes.MARKER, mc.level);
        place(eye);
        neutralYaw = p.getYRot();
        neutralPitch = Mth.clamp(p.getXRot(), -60, 60);
        savedCamera = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.setCameraEntity(eye);
        strategic = true;
    }

    /** Back to the gun view; {@code look}: turned to look at the aim (its aim held there until they look round). */
    private static void leaveView(Minecraft mc, boolean look) {
        strategic = false;
        LocalPlayer p = mc.player;
        if (p != null) {
            mc.setCameraEntity(p);
            if (look) {
                Vec3 d = cursor.subtract(p.getEyePosition());
                float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z)), pitch = (float) -Math.toDegrees(Math.atan2(d.y, d.horizontalDistance()));
                p.setYRot(yaw);
                p.setXRot(pitch);
                p.yRotO = yaw;
                p.xRotO = pitch;
                hold(p);
            }
        }
        if (savedCamera != null) {
            mc.options.setCameraType(savedCamera);
            savedCamera = null;
        }
        eye = null;
    }

    /** The mouse wheel while manning: out goes up (into the strategic view, then higher), in comes down. */
    private static void scroll(Minecraft mc, double delta) {
        if (delta == 0) {
            return;
        }
        GunBlockEntity m = gunEntity(mc);
        float top = m == null ? 96 : high(m);
        if (!strategic) {
            if (delta < 0) {
                enterView(mc);
            }
        } else if (delta > 0) {
            if (height <= LOW + 0.01f) {
                leaveView(mc, true);
            } else {
                height = Math.max(LOW, height - STEP * Math.max(1, height / 64));
            }
        } else {
            height = Math.min(top, height + STEP * Math.max(1, height / 64));
        }
    }

    /**
     * Each frame the strategic camera is up: the crewman's turn this frame (the game turned their head; it is put
     * back) moves the aim over the ground, and the camera is set over it.
     */
    private static void angles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (!strategic || eye == null || event.getCamera().entity() != eye || mc.player == null || mc.level == null || gun == null) {
            return;
        }
        LocalPlayer p = mc.player;
        float dYaw = Mth.wrapDegrees(p.getYRot() - neutralYaw), dPitch = p.getXRot() - neutralPitch;
        p.setYRot(neutralYaw);
        p.setXRot(neutralPitch);
        p.yRotO = neutralYaw;
        p.xRotO = neutralPitch;
        GunBlockEntity m = gunEntity(mc);
        if (m != null && mc.gui.screen() == null && (dYaw != 0 || dPitch != 0)) {
            double k = height / 72.0;
            double yr = Math.toRadians(bearing);
            double fx = -Math.sin(yr), fz = Math.cos(yr);                // forward (as the camera looks), and its right
            double rx = -Math.cos(yr), rz = -Math.sin(yr);
            double x = cursor.x + (rx * dYaw - fx * dPitch) * k, z = cursor.z + (rz * dYaw - fz * dPitch) * k;
            Vec3 base = Vec3.atCenterOf(gun);
            double ox = x - base.x, oz = z - base.z, d = Math.hypot(ox, oz), far = farthest(m);
            if (d > far) {
                x = base.x + ox / d * far;
                z = base.z + oz / d * far;
            }
            cursor = new Vec3(x, ground(mc.level, x, z, cursor.y), z);
        }
        place(eye);
        event.setYaw(bearing);
        event.setPitch(PITCH);
        event.setRoll(0);
    }

    /** The strategic camera over the aim, looking down at it along the bearing. */
    private static void place(Marker at) {
        double yr = Math.toRadians(bearing);
        double back = height / Math.tan(Math.toRadians(PITCH));
        double x = cursor.x + Math.sin(yr) * back, y = cursor.y + height, z = cursor.z - Math.cos(yr) * back;
        at.setPos(x, y, z);
        at.xo = x;
        at.yo = y;
        at.zo = z;
        at.xOld = x;
        at.yOld = y;
        at.zOld = z;
        at.setYRot(bearing);
        at.setXRot(PITCH);
        at.yRotO = bearing;
        at.xRotO = PITCH;
    }

    // --- the laying, the circle, the arc ---------------------------------------------------------------------------------
    /** Where the gun is laid this frame, and what its circle should look like. */
    private static @Nullable Laid lay(Minecraft mc, GunBlockEntity m, float partial) {
        if (mc.level == null) {
            return null;
        }
        Vec3 on = m.shownLaid(partial);
        float yaw, e;
        int charge;
        if (on != null) {
            GunBlockEntity.Solution s = m.solve(on);
            yaw = s.yaw();
            e = s.elevation();
            charge = s.charge();
        } else {
            yaw = m.shownYaw(partial);
            e = m.shownElevation(partial);
            charge = m.charge();
        }
        Vec3 muzzle = m.muzzle(yaw, e);
        Vec3 u = Ballistics.velocity(m.spec.charges()[charge], yaw, e);
        Ballistics.Fall fall = Ballistics.fall(mc.level, muzzle, u, null, GunBlockEntity.MAX_FLIGHT);
        if (fall == null) {
            if (on == null) {
                return null;
            }
            // Over ground the client has not loaded (a far gun): where it is laid, its flight worked out to there.
            fall = Ballistics.flightTo(muzzle, u, on);
        }
        double range = Math.hypot(fall.at().x - muzzle.x, fall.at().z - muzzle.z);
        GunBlockEntity.Solution want = m.solve(cursor);
        boolean onAim = on != null && Math.hypot(on.x - cursor.x, on.z - cursor.z) < 0.5;
        boolean blocked = onAim && Math.hypot(fall.at().x - cursor.x, fall.at().z - cursor.z) > Math.max(2.5, m.radius(range));
        int colour;
        String status;
        if (!want.reachable()) {
            colour = RING_RED;
            status = T_OUT;
        } else if (blocked) {
            colour = RING_RED;
            status = T_BLOCKED;
        } else if (onAim && m.dispersion() < 1.05f) {
            colour = RING_GREEN;
            status = T_AIMED;
        } else {
            colour = RING_WHITE;
            status = T_AIMING;
        }
        return new Laid(muzzle, u, fall, range, m.radius(range), yaw, colour, status);
    }

    private static void submit(SubmitCustomGeometryEvent event) {
        Minecraft mc = Minecraft.getInstance();
        GunBlockEntity m = gunEntity(mc);
        if (m == null || mc.level == null) {
            laid = null;
            return;
        }
        float partial = mc.isPaused() ? 1 : mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        laid = lay(mc, m, partial);
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        ClientLevel level = mc.level;
        if (strategic) {
            ArtilleryTerrain.submit(event, level, camera);
        }
        if (laid == null) {
            return;
        }
        Laid l = laid;
        FxRenderTypes.submit(event.getSubmitNodeCollector(), event.getPoseStack(), FxRenderTypes.fire(RETICLE), (pose, buffer) -> {
            Vec3 c = l.fall().at();
            double stretch = l.fall().stretch();
            double r = Math.max(0.6, l.radius());
            float w = (float) (0.07 + 0.004 * c.distanceTo(camera));
            ring(buffer, pose, level, c, r, stretch, l.yaw(), camera, w, l.colour());
            cross(buffer, pose, level, c, Math.min(1.2, Math.max(0.5, r * 0.25)), camera, w * 0.9f, l.colour());
            if (Math.hypot(c.x - cursor.x, c.z - cursor.z) > 0.8) {
                cross(buffer, pose, level, cursor, 1.4, camera, w, l.colour() == RING_RED ? RING_RED : RING_WHITE);
            }
            if (strategic) {
                arc(buffer, pose, l, camera);
            }
        });
    }

    /**
     * The circle laid on the ground round {@code c}, as dashes: {@code r} across the line of fire, {@code stretch} times
     * that along it. Each dash is one ribbon (the reticle's texture gives it a dark edge, so it reads on any ground).
     */
    private static void ring(VertexConsumer b, PoseStack.Pose pose, ClientLevel level, Vec3 c, double r, double stretch, float yaw, Vec3 camera,
            float w, int colour) {
        double perimeter = Math.PI * r * (1 + stretch);
        int dashes = Mth.clamp((int) Math.round(perimeter / Math.max(0.9, w * 9)), 16, 96);
        double y = Math.toRadians(yaw), fx = Math.sin(y), fz = Math.cos(y);
        for (int i = 0; i < dashes; i++) {
            double a0 = Math.PI * 2 * i / dashes, a1 = a0 + Math.PI * 2 / dashes * 0.58;
            Vec3 prevIn = null, prevOut = null;
            for (int s = 0; s <= 3; s++) {
                double a = a0 + (a1 - a0) * s / 3;
                double ax = Math.cos(a), al = Math.sin(a) * stretch;
                double ox = fz * ax + fx * al, oz = -fx * ax + fz * al;        // as Ballistics.scatter lays its offsets
                double len = Math.hypot(ox, oz);
                double nx = ox / len, nz = oz / len;
                double x = c.x + ox * r, z = c.z + oz * r;
                double gy = surface(level, x, z, c.y) + 0.12;
                Vec3 in = new Vec3(x - nx * w, gy, z - nz * w), out = new Vec3(x + nx * w, gy, z + nz * w);
                if (prevIn != null) {
                    quad(b, pose, prevIn, prevOut, out, in, camera, colour);
                }
                prevIn = in;
                prevOut = out;
            }
        }
    }

    /** A small cross lying on the ground at {@code c}, arms {@code arm} long. */
    private static void cross(VertexConsumer b, PoseStack.Pose pose, ClientLevel level, Vec3 c, double arm, Vec3 camera, float w, int colour) {
        double gy = surface(level, c.x, c.z, c.y) + 0.14;
        quad(b, pose, new Vec3(c.x - arm, gy, c.z - w), new Vec3(c.x - arm, gy, c.z + w), new Vec3(c.x + arm, gy, c.z + w),
                new Vec3(c.x + arm, gy, c.z - w), camera, colour);
        quad(b, pose, new Vec3(c.x - w, gy + 0.01, c.z - arm), new Vec3(c.x + w, gy + 0.01, c.z - arm), new Vec3(c.x + w, gy + 0.01, c.z + arm),
                new Vec3(c.x - w, gy + 0.01, c.z + arm), camera, colour);
    }

    /** The shell's arc from the muzzle to where it comes down, dashed, facing the camera. */
    private static void arc(VertexConsumer b, PoseStack.Pose pose, Laid l, Vec3 camera) {
        double end = l.fall().ticks();
        double dash = Math.max(2, end / 60);
        for (double t = 0; t < end; t += dash * 2) {
            Vec3 p0 = Ballistics.point(l.muzzle(), l.u(), t), p1 = Ballistics.point(l.muzzle(), l.u(), Math.min(end, t + dash));
            Vec3 along = p1.subtract(p0), view = camera.subtract(p0.add(p1).scale(0.5));
            Vec3 side = along.cross(view);
            if (side.lengthSqr() < 1e-9) {
                continue;
            }
            side = side.normalize().scale(0.0025 * view.length() + 0.04);
            quad(b, pose, p0.subtract(side), p0.add(side), p1.add(side), p1.subtract(side), camera, 0x90F4F4EE);
        }
    }

    /** The top of whatever stands at (x, z), plants and all (the reticle lies over them); the far ground's map beyond. */
    private static double surface(ClientLevel level, double x, double z, double fallback) {
        int bx = Mth.floor(x), bz = Mth.floor(z);
        if (!level.isLoaded(new BlockPos(bx, level.getMinY(), bz))) {
            double h = ArtilleryTerrain.height(x, z);
            return Double.isNaN(h) ? fallback : h;
        }
        return level.getHeight(Heightmap.Types.WORLD_SURFACE, bx, bz);
    }

    static void quad(VertexConsumer b, PoseStack.Pose pose, Vec3 a, Vec3 bb, Vec3 c, Vec3 d, Vec3 camera, int colour) {
        vertex(b, pose, a, camera, 0, 0, colour);
        vertex(b, pose, bb, camera, 0, 1, colour);
        vertex(b, pose, c, camera, 1, 1, colour);
        vertex(b, pose, d, camera, 1, 0, colour);
    }

    static void vertex(VertexConsumer b, PoseStack.Pose pose, Vec3 p, Vec3 camera, float u, float v, int colour) {
        b.addVertex(pose, (float) (p.x - camera.x), (float) (p.y - camera.y), (float) (p.z - camera.z)).setColor(colour).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0, 1, 0);
    }

    // --- the readout ---------------------------------------------------------------------------------------------------
    private static void hud(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        GunBlockEntity m = gunEntity(mc);
        if (m == null) {
            return;
        }
        Font font = mc.font;
        int w = g.guiWidth(), h = g.guiHeight();
        List<ShellType> kinds = m.spec.shells();
        int n = kinds.size();
        int cell = 22, x0 = w / 2 - n * cell / 2, y0 = h - 28;
        g.fill(x0 - 4, y0 - 4, x0 + n * cell + 2, y0 + cell + 4, 0x90101410);
        for (int i = 0; i < n; i++) {
            ShellType t = kinds.get(i);
            int x = x0 + i * cell;
            boolean picked = i == m.selected();
            int count = m.shells(i);
            g.fill(x, y0, x + cell - 2, y0 + cell - 2, picked ? 0xC0405038 : 0x80262A24);
            g.item(icon(m.spec.calibre(), t), x + 2, y0 + 2);
            if (count == 0) {
                g.fill(x, y0, x + cell - 2, y0 + cell - 2, 0xA0000000);
            }
            if (picked) {
                g.outline(x - 1, y0 - 1, cell, cell, AMBER);
            }
            if (count >= 0) {                                   // (a creative crewman's are without end: no count)
                String c = Integer.toString(count);
                g.text(font, c, x + cell - 2 - font.width(c), y0 + cell - 10, count == 0 ? RED : WHITE, true);
            }
            g.text(font, Integer.toString(i + 1), x + 1, y0 + 1, GREY, true);
        }
        float loaded = 1 - m.reload() / (float) m.spec.reload();
        int barW = n * cell + 2;
        g.fill(x0 - 2, y0 + cell + 1, x0 - 2 + barW, y0 + cell + 3, 0xA0000000);
        g.fill(x0 - 2, y0 + cell + 1, x0 - 2 + Math.round(barW * Mth.clamp(loaded, 0, 1)), y0 + cell + 3, loaded >= 1 ? GREEN : AMBER);
        // The shell, then the laying: above and either side of the rack.
        ShellType picked = m.selectedType();
        String name = icon(m.spec.calibre(), picked).getHoverName().getString();
        g.text(font, name, w / 2 - font.width(name) / 2, y0 - 16, 0xFF000000 | picked.colour, true);
        Laid l = laid;
        if (l != null) {
            String status = m.reload() > 0 && l.colour() != RING_RED ? Component.translatable(T_LOADING).getString()
                    : Component.translatable(l.status()).getString();
            int sc = l.colour() == RING_RED ? RED : l.colour() == RING_GREEN ? GREEN : m.reload() > 0 ? AMBER : WHITE;
            String[] left = {status, Component.translatable(T_RANGE, Math.round(l.range())).getString(),
                    Component.translatable(T_TIME, String.format("%.1f", l.fall().ticks() / 20.0)).getString()};
            String[] right = {Component.translatable(T_CHARGE, m.charge() + 1).getString(),
                    Component.translatable(T_ELEVATION, String.format("%.1f", m.elevation())).getString(),
                    Component.translatable(T_SPREAD, String.format("%.1f", m.dispersion())).getString()};
            int lx = x0 - 10, rx = x0 + n * cell + 8;
            for (int i = 0; i < 3; i++) {
                int ty = y0 - 1 + i * 10;
                g.text(font, left[i], lx - font.width(left[i]), ty, i == 0 ? sc : WHITE, true);
                g.text(font, right[i], rx, ty, GREY, true);
            }
        }
        String hint = Component.translatable(strategic ? T_HINT_MAP : T_HINT_GUN, VIEW.getTranslatedKeyMessage().getString()).getString();
        String fire = Component.translatable(T_HINT_FIRE, n).getString();
        g.text(font, hint, w / 2 - font.width(hint) / 2, 6, 0xFFD8DCD0, true);
        g.text(font, fire, w / 2 - font.width(fire) / 2, 17, 0xFFD8DCD0, true);
        if (strategic) {
            // The middle of the screen is the aim: a small mark, the circle is on the ground.
            int cx = w / 2, cy = h / 2;
            g.fill(cx - 3, cy, cx + 4, cy + 1, 0xC0FFFFFF);
            g.fill(cx, cy - 3, cx + 1, cy + 4, 0xC0FFFFFF);
        }
    }

    private static ItemStack icon(Calibre calibre, ShellType t) {
        return ICONS.computeIfAbsent(calibre.item(t), k -> {
            var item = GBArtillery.shell(calibre, t);
            return item == null ? ItemStack.EMPTY : new ItemStack(item.get());
        });
    }

    // --- shells coming down --------------------------------------------------------------------------------------------
    /** A shell's last two seconds: its whistle, following it down (lower for a bigger shell). */
    private static void shellTick(ArtilleryShell shell) {
        if (WHISTLING.contains(shell.getId()) || shell.getDeltaMovement().y >= 0) {
            return;
        }
        Vec3 v = shell.getDeltaMovement();
        double ground = surface((ClientLevel) shell.level(), shell.getX(), shell.getZ(), shell.getY() - 40);
        double drop = Math.max(0, shell.getY() - ground);
        // Ticks until it has fallen the drop, coming down at -v.y and gaining G a tick.
        double t = (v.y + Math.sqrt(v.y * v.y + 2 * Ballistics.G * drop)) / Ballistics.G;
        if (t <= 40) {
            WHISTLING.add(shell.getId());
            Minecraft.getInstance().getSoundManager().play(new Whistle(shell));
        }
    }

    /** A shell's whistle, following it down. */
    private static final class Whistle extends AbstractTickableSoundInstance {
        private final ArtilleryShell shell;

        Whistle(ArtilleryShell shell) {
            super(GBSounds.SHELL_INCOMING.get(), SoundSource.HOSTILE, SoundInstance.createUnseededRandom());
            this.shell = shell;
            this.volume = 2.4f * (shell.calibre() == Calibre.MORTAR ? 1 : 1.6f);
            this.pitch = shell.calibre().whistle * (0.95f + shell.getRandom().nextFloat() * 0.1f);
            this.attenuation = SoundInstance.Attenuation.LINEAR;
            this.x = shell.getX();
            this.y = shell.getY();
            this.z = shell.getZ();
        }

        @Override
        public void tick() {
            if (this.shell.isRemoved()) {
                stop();
                return;
            }
            this.x = this.shell.getX();
            this.y = this.shell.getY();
            this.z = this.shell.getZ();
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }
}
