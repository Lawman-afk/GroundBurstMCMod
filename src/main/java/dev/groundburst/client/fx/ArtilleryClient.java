package dev.groundburst.client.fx;

import java.util.EnumMap;
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
import dev.groundburst.content.artillery.GBArtillery;
import dev.groundburst.content.artillery.GunSeat;
import dev.groundburst.content.artillery.MortarBlockEntity;
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
 * mouse moving the aim over the ground and the wheel the height. 1 to 6 pick the shell, left click fires. On the
 * ground the aiming circle (where the tube points, as wide as the spread a shot would have now: white while it
 * shrinks, green fully aimed, red out of reach or blocked), the aim's cross and, from above, the shell's arc. The
 * readout: the rack, the reload, range, charge, elevation, time of flight.
 */
public final class ArtilleryClient {
    public static final KeyMapping VIEW = new KeyMapping(GBLang.add("key." + GroundBurst.MODID + ".artillery_view", "Artillery: strategic view"),
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYBOARD, InputConstants.KEY_V, ManualHotkey.CATEGORY);

    private static final String T_HINT_GUN = GBLang.add(GroundBurst.MODID + ".artillery.hint_gun",
            "[%s] or scroll out: strategic view   [Shift] leave");
    private static final String T_HINT_MAP = GBLang.add(GroundBurst.MODID + ".artillery.hint_map",
            "[Mouse] aim   [Scroll] height   [%s] or scroll in: gun view");
    private static final String T_HINT_FIRE = GBLang.add(GroundBurst.MODID + ".artillery.hint_fire", "[1-6] shell   [Left click] fire");
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

    private static final Identifier RETICLE = GroundBurst.id("textures/fx/reticle.png");
    private static final int WHITE = 0xFFF0F0E8, GREY = 0xFF9A9E96, AMBER = 0xFFE8B030, GREEN = 0xFF7CE070, RED = 0xFFF05040;
    private static final int RING_WHITE = 0xF0F4F4EE, RING_GREEN = 0xF080F078, RING_RED = 0xF0FF5040, SHADOW = 0x90000000;
    /** The strategic camera: how far over the ground (blocks), how steeply it looks down (degrees). */
    private static final float LOW = 24, HIGH = 96, STEP = 8, PITCH = 75;
    /** How far the aim may wander from the gun in the strategic view (it lays only to its own reach). */
    private static final double FARTHEST = MortarBlockEntity.MAX_RANGE + 40;

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
    /** This frame's laying: where the tube points and how wide its circle is (drawn, and read by the readout). */
    private static @Nullable Laid laid;
    private static final IntOpenHashSet WHISTLING = new IntOpenHashSet();
    private static final Map<ShellType, ItemStack> ICONS = new EnumMap<>(ShellType.class);

    private ArtilleryClient() {}

    /** Where the tube points this frame: the muzzle, where its shell would come down, the circle there, its colour. */
    private record Laid(Vec3 muzzle, Vec3 u, Ballistics.Fall fall, double range, double radius, float yaw, int colour, String status) {}

    public static void register(IEventBus modBus) {
        modBus.addListener((RegisterKeyMappingsEvent e) -> e.register(VIEW));
        modBus.addListener((RegisterGuiLayersEvent e) -> e.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, GroundBurst.id("artillery"), ArtilleryClient::hud));
        ArtilleryPayloads.stateHook = s -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && mc.level.isLoaded(s.gun()) && mc.level.getBlockEntity(s.gun()) instanceof MortarBlockEntity m) {
                m.applyState(s);
            }
        };
        ArtilleryShell.clientHook = ArtilleryClient::shellTick;
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
        });
    }

    /** Whether the client is manning a gun. */
    public static boolean manning() {
        return gun != null;
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
        height = Mth.clamp(over, LOW, HIGH);
    }

    /** Back down from the strategic view (the photographer's). */
    public static void photoLeave() {
        if (strategic) {
            leaveView(Minecraft.getInstance(), false);
        }
    }

    private static @Nullable MortarBlockEntity mortar(Minecraft mc) {
        return gun != null && mc.level != null && mc.level.getBlockEntity(gun) instanceof MortarBlockEntity m ? m : null;
    }

    // --- each tick ----------------------------------------------------------------------------------------------------
    private static void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        BlockPos now = p != null && mc.level != null && p.getVehicle() instanceof GunSeat seat
                && mc.level.getBlockEntity(seat.gun()) instanceof MortarBlockEntity ? seat.gun() : null;
        if (now == null ? gun != null : !now.equals(gun)) {
            if (gun != null) {
                leave(mc);
            }
            if (now != null) {
                join(mc, now);
            }
        }
        MortarBlockEntity m = mortar(mc);
        if (m == null || p == null) {
            return;
        }
        boolean free = mc.gui.screen() == null;
        for (int i = 0; i < ShellType.values().length && i < mc.options.keyHotbarSlots.length; i++) {
            while (mc.options.keyHotbarSlots[i].consumeClick()) {
                ClientPacketDistributor.sendToServer(new ArtilleryPayloads.Select(gun, i));
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
                Vec3 hit = lookHit(mc, p);
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
                mc.getSoundManager().play(SimpleSoundInstance.forUI(GBSounds.MORTAR_FIRE.get(), 1f, 0.55f));
            }
            seenFired = m.firedAt();
        }
    }

    private static void join(Minecraft mc, BlockPos at) {
        gun = at;
        strategic = false;
        sent = null;
        MortarBlockEntity m = mortar(mc);
        laid = m == null ? null : lay(mc, m, 1);
        cursor = laid != null ? laid.fall().at() : Vec3.atCenterOf(at);
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
    private static @Nullable Vec3 lookHit(Minecraft mc, LocalPlayer p) {
        Vec3 from = p.getEyePosition(), look = p.getViewVector(1);
        BlockHitResult hit = mc.level.clip(new ClipContext(from.add(look.scale(1.5)), from.add(look.scale(FARTHEST)), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.ANY, p));
        return hit.getType() == HitResult.Type.MISS ? null : hit.getLocation();
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
        height = 48;
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
        if (!strategic) {
            if (delta < 0) {
                enterView(mc);
            }
        } else if (delta > 0) {
            if (height <= LOW + 0.01f) {
                leaveView(mc, true);
            } else {
                height = Math.max(LOW, height - STEP);
            }
        } else {
            height = Math.min(HIGH, height + STEP);
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
        if (mc.gui.screen() == null && (dYaw != 0 || dPitch != 0)) {
            double k = height / 72.0;
            double yr = Math.toRadians(bearing);
            double fx = -Math.sin(yr), fz = Math.cos(yr);                // forward (as the camera looks), and its right
            double rx = -Math.cos(yr), rz = -Math.sin(yr);
            double x = cursor.x + (rx * dYaw - fx * dPitch) * k, z = cursor.z + (rz * dYaw - fz * dPitch) * k;
            Vec3 base = Vec3.atCenterOf(gun);
            double ox = x - base.x, oz = z - base.z, d = Math.hypot(ox, oz);
            if (d > FARTHEST) {
                x = base.x + ox / d * FARTHEST;
                z = base.z + oz / d * FARTHEST;
            }
            cursor = new Vec3(x, Ballistics.ground(mc.level, x, z, cursor.y), z);
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
    /** How the tube is laid this frame, and what its circle should look like. */
    private static @Nullable Laid lay(Minecraft mc, MortarBlockEntity m, float partial) {
        if (mc.level == null) {
            return null;
        }
        float yaw = m.shownYaw(partial), e = m.shownElevation(partial);
        Vec3 muzzle = m.muzzle(yaw, e);
        Vec3 u = Ballistics.velocity(MortarBlockEntity.CHARGES[m.charge()], yaw, e);
        Ballistics.Fall fall = Ballistics.fall(mc.level, muzzle, u, null, 400);
        if (fall == null) {
            return null;
        }
        double range = Math.hypot(fall.at().x - muzzle.x, fall.at().z - muzzle.z);
        MortarBlockEntity.Solution s = m.solve(cursor);
        boolean on = Math.abs(Mth.wrapDegrees(yaw - s.yaw())) < 0.6f && Math.abs(e - s.elevation()) < 0.6f && s.charge() == m.charge();
        boolean blocked = on && Math.hypot(fall.at().x - cursor.x, fall.at().z - cursor.z) > Math.max(2.5, m.radius(range));
        int colour;
        String status;
        if (!s.reachable()) {
            colour = RING_RED;
            status = T_OUT;
        } else if (blocked) {
            colour = RING_RED;
            status = T_BLOCKED;
        } else if (on && m.dispersion() < 1.05f) {
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
        MortarBlockEntity m = mortar(mc);
        if (m == null || mc.level == null) {
            laid = null;
            return;
        }
        float partial = mc.isPaused() ? 1 : mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        laid = lay(mc, m, partial);
        if (laid == null) {
            return;
        }
        Laid l = laid;
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        ClientLevel level = mc.level;
        FxRenderTypes.submit(event.getSubmitNodeCollector(), event.getPoseStack(), FxRenderTypes.fire(RETICLE), (pose, buffer) -> {
            Vec3 c = l.fall().at();
            double stretch = l.fall().stretch();
            double r = Math.max(0.6, l.radius());
            float w = (float) (0.05 + 0.0035 * c.distanceTo(camera));
            ring(buffer, pose, level, c, r, stretch, l.yaw(), camera, w * 2.2f, SHADOW);
            ring(buffer, pose, level, c, r, stretch, l.yaw(), camera, w, l.colour());
            cross(buffer, pose, level, c, Math.min(1.2, r * 0.25), camera, w * 0.8f, l.colour());
            if (Math.hypot(c.x - cursor.x, c.z - cursor.z) > 0.8) {
                cross(buffer, pose, level, cursor, 1.2, camera, w * 2f, SHADOW);
                cross(buffer, pose, level, cursor, 1.2, camera, w, l.colour() == RING_RED ? RING_RED : RING_WHITE);
            }
            if (strategic) {
                arc(buffer, pose, l, camera);
            }
        });
    }

    /** The circle laid on the ground round {@code c}: {@code r} across the line of fire, {@code stretch} times that along it. */
    private static void ring(VertexConsumer b, PoseStack.Pose pose, ClientLevel level, Vec3 c, double r, double stretch, float yaw, Vec3 camera,
            float w, int colour) {
        int n = Mth.clamp((int) (r * stretch * 6), 32, 96);
        double y = Math.toRadians(yaw), fx = Math.sin(y), fz = Math.cos(y);
        Vec3 prevIn = null, prevOut = null;
        for (int i = 0; i <= n; i++) {
            double a = Math.PI * 2 * i / n;
            double ax = Math.cos(a), al = Math.sin(a) * stretch;
            double ox = fz * ax + fx * al, oz = -fx * ax + fz * al;            // as Ballistics.scatter lays its offsets
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

    /** A small cross lying on the ground at {@code c}, arms {@code arm} long. */
    private static void cross(VertexConsumer b, PoseStack.Pose pose, ClientLevel level, Vec3 c, double arm, Vec3 camera, float w, int colour) {
        double gy = surface(level, c.x, c.z, c.y) + 0.14;
        quad(b, pose, new Vec3(c.x - arm, gy, c.z - w), new Vec3(c.x - arm, gy, c.z + w), new Vec3(c.x + arm, gy, c.z + w),
                new Vec3(c.x + arm, gy, c.z - w), camera, colour);
        quad(b, pose, new Vec3(c.x - w, gy, c.z - arm), new Vec3(c.x + w, gy, c.z - arm), new Vec3(c.x + w, gy, c.z + arm),
                new Vec3(c.x - w, gy, c.z + arm), camera, colour);
    }

    /** The shell's arc from the muzzle to where it comes down, dashed, facing the camera. */
    private static void arc(VertexConsumer b, PoseStack.Pose pose, Laid l, Vec3 camera) {
        double end = l.fall().ticks();
        for (double t = 0; t < end; t += 4) {
            Vec3 p0 = Ballistics.point(l.muzzle(), l.u(), t), p1 = Ballistics.point(l.muzzle(), l.u(), Math.min(end, t + 2));
            Vec3 along = p1.subtract(p0), view = camera.subtract(p0.add(p1).scale(0.5));
            Vec3 side = along.cross(view);
            if (side.lengthSqr() < 1e-9) {
                continue;
            }
            side = side.normalize().scale(0.0025 * view.length() + 0.04);
            quad(b, pose, p0.subtract(side), p0.add(side), p1.add(side), p1.subtract(side), camera, 0x80F4F4EE);
        }
    }

    /** The top of whatever stands at (x, z), plants and all (the reticle lies over them). */
    private static double surface(ClientLevel level, double x, double z, double fallback) {
        int bx = Mth.floor(x), bz = Mth.floor(z);
        if (!level.isLoaded(new BlockPos(bx, level.getMinY(), bz))) {
            return fallback;
        }
        return level.getHeight(Heightmap.Types.WORLD_SURFACE, bx, bz);
    }

    private static void quad(VertexConsumer b, PoseStack.Pose pose, Vec3 a, Vec3 bb, Vec3 c, Vec3 d, Vec3 camera, int colour) {
        vertex(b, pose, a, camera, 0, 0, colour);
        vertex(b, pose, bb, camera, 0, 1, colour);
        vertex(b, pose, c, camera, 1, 1, colour);
        vertex(b, pose, d, camera, 1, 0, colour);
    }

    private static void vertex(VertexConsumer b, PoseStack.Pose pose, Vec3 p, Vec3 camera, float u, float v, int colour) {
        b.addVertex(pose, (float) (p.x - camera.x), (float) (p.y - camera.y), (float) (p.z - camera.z)).setColor(colour).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0, 1, 0);
    }

    // --- the readout ---------------------------------------------------------------------------------------------------
    private static void hud(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        MortarBlockEntity m = mortar(mc);
        if (m == null) {
            return;
        }
        Font font = mc.font;
        int w = g.guiWidth(), h = g.guiHeight();
        ShellType[] all = ShellType.values();
        int cell = 22, x0 = w / 2 - all.length * cell / 2, y0 = h - 28;
        g.fill(x0 - 4, y0 - 4, x0 + all.length * cell + 2, y0 + cell + 4, 0x90101410);
        for (ShellType t : all) {
            int x = x0 + t.ordinal() * cell;
            boolean picked = t == m.selected();
            int count = m.shells(t);
            g.fill(x, y0, x + cell - 2, y0 + cell - 2, picked ? 0xC0405038 : 0x80262A24);
            g.item(icon(t), x + 2, y0 + 2);
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
            g.text(font, Integer.toString(t.ordinal() + 1), x + 1, y0 + 1, GREY, true);
        }
        float loaded = 1 - m.reload() / (float) MortarBlockEntity.RELOAD;
        int barW = all.length * cell + 2;
        g.fill(x0 - 2, y0 + cell + 1, x0 - 2 + barW, y0 + cell + 3, 0xA0000000);
        g.fill(x0 - 2, y0 + cell + 1, x0 - 2 + Math.round(barW * Mth.clamp(loaded, 0, 1)), y0 + cell + 3, loaded >= 1 ? GREEN : AMBER);
        // The shell, then the laying: above the rack.
        ShellType picked = m.selected();
        String name = GBArtillery.MORTAR_SHELLS.get(picked).get().getDefaultInstance().getHoverName().getString();
        int ty = y0 - 16;
        g.text(font, name, w / 2 - font.width(name) / 2, ty, 0xFF000000 | picked.colour, true);
        Laid l = laid;
        if (l != null) {
            // Left of the rack (set against it): how it stands, the range, the flight. Right of it: how it is laid.
            String status = m.reload() > 0 && l.colour() != RING_RED ? Component.translatable(T_LOADING).getString()
                    : Component.translatable(l.status()).getString();
            int sc = l.colour() == RING_RED ? RED : l.colour() == RING_GREEN ? GREEN : m.reload() > 0 ? AMBER : WHITE;
            String[] left = {status, Component.translatable(T_RANGE, Math.round(l.range())).getString(),
                    Component.translatable(T_TIME, String.format("%.1f", l.fall().ticks() / 20.0)).getString()};
            String[] right = {Component.translatable(T_CHARGE, m.charge() + 1).getString(),
                    Component.translatable(T_ELEVATION, String.format("%.1f", m.elevation())).getString(),
                    Component.translatable(T_SPREAD, String.format("%.1f", m.dispersion())).getString()};
            int lx = x0 - 10, rx = x0 + all.length * cell + 8;
            for (int i = 0; i < 3; i++) {
                int ty2 = y0 - 1 + i * 10;
                g.text(font, left[i], lx - font.width(left[i]), ty2, i == 0 ? sc : WHITE, true);
                g.text(font, right[i], rx, ty2, GREY, true);
            }
        }
        String hint = Component.translatable(strategic ? T_HINT_MAP : T_HINT_GUN, VIEW.getTranslatedKeyMessage().getString()).getString();
        String fire = Component.translatable(T_HINT_FIRE).getString();
        g.text(font, hint, w / 2 - font.width(hint) / 2, 6, 0xFFD8DCD0, true);
        g.text(font, fire, w / 2 - font.width(fire) / 2, 17, 0xFFD8DCD0, true);
        if (strategic) {
            // The middle of the screen is the aim: a small mark, the circle is on the ground.
            int cx = w / 2, cy = h / 2;
            g.fill(cx - 3, cy, cx + 4, cy + 1, 0xC0FFFFFF);
            g.fill(cx, cy - 3, cx + 1, cy + 4, 0xC0FFFFFF);
        }
    }

    private static ItemStack icon(ShellType t) {
        return ICONS.computeIfAbsent(t, k -> new ItemStack(GBArtillery.MORTAR_SHELLS.get(k).get()));
    }

    // --- shells coming down --------------------------------------------------------------------------------------------
    /** A shell's last two seconds: its whistle, following it down. */
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
            this.volume = 2.4f;
            this.pitch = 0.95f + shell.getRandom().nextFloat() * 0.1f;
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
