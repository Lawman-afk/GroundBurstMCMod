package dev.groundburst.client.screen;

import static dev.groundburst.client.screen.Phosphor.AMBER;
import static dev.groundburst.client.screen.Phosphor.BRIGHT;
import static dev.groundburst.client.screen.Phosphor.DIM;
import static dev.groundburst.client.screen.Phosphor.DIM_RED;
import static dev.groundburst.client.screen.Phosphor.EDGE;
import static dev.groundburst.client.screen.Phosphor.LIST_BG;
import static dev.groundburst.client.screen.Phosphor.PANEL;
import static dev.groundburst.client.screen.Phosphor.RED;
import static dev.groundburst.client.screen.Phosphor.SELECTED_BG;
import static dev.groundburst.client.screen.Phosphor.TEXT;
import static dev.groundburst.client.screen.Phosphor.WHITE;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.launch.LaunchNetwork;
import dev.groundburst.content.launch.LaunchPayloads;
import dev.groundburst.content.launch.LaunchSiloBlockEntity;
import dev.groundburst.content.launch.LaunchSite;
import dev.groundburst.content.launch.LaunchStationBlock;
import dev.groundburst.content.launch.RadarArrayBlockEntity;
import dev.groundburst.content.launch.StationPayloads;
import dev.groundburst.registry.GBLang;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The Launch Control Station's screen (DESIGN.md §17.12): on the left a green phosphor map of the whole dimension with
 * the network's members, their targets and strikes, its missiles in the air and what its radars track (right-drag to
 * look round, scroll to zoom, left-click to aim the picks); on the right the members (or the log), the picked member,
 * the aim, the pattern for several, ARM, the timing and LAUNCH.
 */
public class LaunchStationScreen extends Screen {
    private static final String P = GroundBurst.MODID + ".station.";
    private static final String T_TITLE = GBLang.add(P + "title", "Launch Control Station");
    private static final String T_NETWORK = GBLang.add(P + "network", "Network %s");
    private static final String T_MEMBERS = GBLang.add(P + "members", "Network (%s)");
    private static final String T_LOG = GBLang.add(P + "log", "Log");
    private static final String T_EMPTY = GBLang.add(P + "empty",
            "Nothing linked yet. Take Launch Codes from this station, then use them on your launchers and Radar Arrays.");
    private static final String T_NO_LOG = GBLang.add(P + "no_log", "Nothing to report");
    private static final String T_SET = GBLang.add(P + "set", "Set");
    private static final String T_ARM = GBLang.add(P + "arm", "ARM");
    private static final String T_DISARM = GBLang.add(P + "disarm", "DISARM");
    private static final String T_LAUNCH = GBLang.add(P + "launch", "LAUNCH (%s)");
    private static final String T_AT_ONCE = GBLang.add(P + "at_once", "Timing: at once");
    private static final String T_TOGETHER = GBLang.add(P + "together", "Timing: on target together");
    private static final String T_SPACING = GBLang.add(P + "spacing", "Spacing");
    private static final String[] T_PATTERN = {
            GBLang.add(P + "pattern.point", "Pattern: point"),
            GBLang.add(P + "pattern.line", "Pattern: line"),
            GBLang.add(P + "pattern.ring", "Pattern: ring"),
            GBLang.add(P + "pattern.grid", "Pattern: grid")};
    private static final String T_UNLINK = GBLang.add(P + "unlink", "Unlink");
    private static final String T_SURE = GBLang.add(P + "sure", "Sure?");
    private static final String T_NEW_CODES = GBLang.add(P + "new_codes", "New codes");
    private static final String T_CENTRE = GBLang.add(P + "centre", "Station");
    private static final String T_CENTRE_PICK = GBLang.add(P + "centre_pick", "Picked");
    private static final String T_CURSOR = GBLang.add(P + "cursor", "X %s  Z %s");
    private static final String T_ZOOM = GBLang.add(P + "zoom", "1:%s");
    private static final String T_NO_POWER = GBLang.add(P + "no_power", "NO POWER");
    private static final String T_CONTACTS = GBLang.add(P + "contacts", "%s in the air");
    private static final String T_AT = GBLang.add(P + "at", "#%s %s at %s, %s");
    private static final String T_PICKED = GBLang.add(P + "picked", "%s picked");
    private static final String T_PATTERN_OF = GBLang.add(P + "pattern_of", "Aimed round the point: %s");
    private static final String T_NOTHING = GBLang.add(P + "nothing", "Nothing loaded");
    private static final String T_RADAR_SEES = GBLang.add(P + "radar_sees", "Tracks %s missiles within %s blocks");
    private static final String T_RANGE = GBLang.add(P + "range", "%s m: lands %s after LAUNCH");
    private static final String T_TOO_FAR = GBLang.add(P + "too_far", "Out of range (max %s m)");
    private static final String T_FARTHEST = GBLang.add(P + "farthest", "Last lands %s after LAUNCH");
    private static final String T_HINT = GBLang.add(P + "hint", "Shift-click to pick several");
    private static final String T_NO_TARGET = GBLang.add(P + "no_target", "No target");
    private static final String T_ARMED = GBLang.add(P + "state.armed", "ARMED");
    private static final String T_ARMING = GBLang.add(P + "state.arming", "Arming");
    private static final String T_SAFING = GBLang.add(P + "state.safing", "Making safe");
    private static final String T_HELD = GBLang.add(P + "state.held", "Launch in %s");
    private static final String T_FLIGHT = GBLang.add(P + "state.flight", "In flight %s");
    private static final String T_LOST = GBLang.add(P + "state.lost", "No contact");
    private static final String T_TRACKING = GBLang.add(P + "state.tracking", "Tracking %s");
    private static final String T_RADAR_OFF = GBLang.add(P + "state.radar_off", "Not tracking");
    private static final String T_NOT_LOADED = GBLang.add(P + "state.not_loaded", "Not loaded");
    private static final String[] T_STATE = {
            GBLang.add(P + "state.not_formed", "Incomplete"),
            GBLang.add(P + "state.emp", "EMP"),
            GBLang.add(P + "state.halted", "Halted"),
            GBLang.add(P + "state.no_missile", "No missile"),
            GBLang.add(P + "state.no_power", "No power"),
            GBLang.add(P + "state.ready", "READY"),
            GBLang.add(P + "state.counting", "T-%s"),
            GBLang.add(P + "state.blocked", "Blocked"),
            GBLang.add(P + "state.charging", "Charging %s")};
    private static final String T_AGO = GBLang.add(P + "ago", "%s ago");
    /** Each log event's text (a switch: a kind added later cannot go without one). */
    private static final java.util.EnumMap<LaunchNetwork.EventKind, String> T_EVENT = new java.util.EnumMap<>(LaunchNetwork.EventKind.class);

    static {
        for (LaunchNetwork.EventKind k : LaunchNetwork.EventKind.values()) {
            T_EVENT.put(k, GBLang.add(P + "event." + k.name().toLowerCase(java.util.Locale.ROOT), switch (k) {
                case LAUNCHED -> "%s launched";
                case IMPACT -> "%s: impact at %s, %s";
                case LOST -> "%s: missile lost at %s, %s";
                case NO_CONTACT -> "No contact with %s";
                case FAILED -> "%s: order failed (%s)";
                case LINKED -> "%s linked";
                case UNLINKED -> "%s unlinked";
                case NEW_CODES -> "New codes issued";
                case FIRE_CALLED -> "%s: fire called on %s, %s";
                case DESIGNATED -> "%s designated on %s, %s";
            }));
        }
    }
    private static final String T_LASER = GBLang.add(P + "laser", "Laser");
    private static final String T_NO_ANSWER = GBLang.add(P + "event.no_answer", "no answer");
    private static final String T_GONE = GBLang.add(P + "gone", "%s at %s, %s");
    /** Short names for the members' kinds (by their controller's id); others go by their block's own name. */
    private static final Map<String, String> T_KIND = Map.of(
            "launch_silo", GBLang.add(P + "kind.launch_silo", "Launch Silo"),
            "launch_complex", GBLang.add(P + "kind.launch_complex", "Launch Complex"),
            "missile_silo", GBLang.add(P + "kind.missile_silo", "Missile Silo"),
            "cruise_launcher", GBLang.add(P + "kind.cruise_launcher", "Cruise Launcher"),
            "radar_array", GBLang.add(P + "kind.radar_array", "Radar Array"));

    private static final int W = 420, H = 236, MAP = 196;
    private static final int COL = MAP + 18, CW = W - COL - 8, ROW = 10, ROWS = 7;
    private static final int TABS_Y = 16, LIST_Y = 29, DETAIL_Y = 103, BOX_Y = 125, INFO_Y = 144, PATTERN_Y = 156, ARM_Y = 176,
            LAUNCH_Y = 196, HINT_Y = 222;
    private static final int[] ZOOMS = {1, 2, 4, 8, 16, 32, 64, 128};
    private static final int CONFIRM = 60;

    private static StationPayloads.@Nullable Scope latest;

    public static void register() {
        LaunchStationBlock.screenHook = pos -> Minecraft.getInstance().gui.setScreen(new LaunchStationScreen(pos));
        StationPayloads.scopeHook = scope -> latest = scope;
    }

    private final BlockPos pos;
    private @Nullable EditBox xBox, zBox, spacingBox;
    private @Nullable Button setButton, laserButton, armButton, launchButton, patternButton, timingButton, unlinkButton, codesButton;
    /** The picked members (by position, so picks survive the list changing), in the order they were picked. */
    private final Set<BlockPos> picked = new LinkedHashSet<>();
    private int scroll, logScroll;
    private boolean showLog;
    private @Nullable BlockPos boxesFor;
    private int pattern;
    private boolean together = true;
    /** The tick a confirm-twice button was first pressed (Unlink, New codes), or -1. */
    private long unlinkAsked = -1, codesAsked = -1;
    private long ticks;
    private @Nullable TerrainMap map;
    private double viewX, viewZ;
    private int zoomStep = 4;
    private boolean panning;

    public LaunchStationScreen(BlockPos pos) {
        super(Component.translatable(T_TITLE));
        this.pos = pos;
        this.viewX = pos.getX() + 0.5;
        this.viewZ = pos.getZ() + 0.5;
    }

    private int left() {
        return (this.width - W) / 2;
    }

    private int top() {
        return (this.height - H) / 2;
    }

    private int col() {
        return left() + COL;
    }

    private int mapX() {
        return left() + 8;
    }

    private int mapY() {
        return top() + 16;
    }

    private int zoom() {
        return ZOOMS[this.zoomStep];
    }

    private StationPayloads.@Nullable Scope scope() {
        return latest != null && latest.pos().equals(this.pos) ? latest : null;
    }

    private boolean on() {
        StationPayloads.Scope s = scope();
        return s != null && s.formed() && s.powered();
    }

    private long now() {
        StationPayloads.Scope s = scope();
        return s == null ? 0 : s.now();
    }

    private List<StationPayloads.MemberView> members() {
        StationPayloads.Scope s = scope();
        return s == null ? List.of() : s.members();
    }

    private List<StationPayloads.MemberView> pickedMembers() {
        List<StationPayloads.MemberView> out = new ArrayList<>();
        for (BlockPos p : this.picked) {
            for (StationPayloads.MemberView m : members()) {
                if (m.pos().equals(p)) {
                    out.add(m);
                }
            }
        }
        return out;
    }

    private StationPayloads.@Nullable MemberView single() {
        List<StationPayloads.MemberView> ms = pickedMembers();
        return ms.size() == 1 ? ms.getFirst() : null;
    }

    private int number(BlockPos member) {
        List<StationPayloads.MemberView> ms = members();
        for (int i = 0; i < ms.size(); i++) {
            if (ms.get(i).pos().equals(member)) {
                return i + 1;
            }
        }
        return 0;
    }

    // --- map coordinates -------------------------------------------------------------------------------------------------
    private int screenX(double wx) {
        return mapX() + MAP / 2 + (int) Math.floor((wx - this.viewX) / zoom());
    }

    private int screenY(double wz) {
        return mapY() + MAP / 2 + (int) Math.floor((wz - this.viewZ) / zoom());
    }

    private double worldX(double sx) {
        return this.viewX + (sx - mapX() - MAP / 2.0) * zoom();
    }

    private double worldZ(double sy) {
        return this.viewZ + (sy - mapY() - MAP / 2.0) * zoom();
    }

    private boolean inMap(double x, double y) {
        return x >= mapX() && x < mapX() + MAP && y >= mapY() && y < mapY() + MAP;
    }

    private void clampView() {
        this.viewX = Mth.clamp(this.viewX, -30_000_000, 30_000_000);
        this.viewZ = Mth.clamp(this.viewZ, -30_000_000, 30_000_000);
    }

    @Override
    protected void init() {
        ClientPacketDistributor.sendToServer(new LaunchPayloads.Watch(this.pos, true));
        if (this.map == null) {
            this.map = new TerrainMap(MAP, MAP, false, "dynamic/station_map");
        }
        int x = col(), t = top();
        int boxW = (CW - 80) / 2;
        this.xBox = addRenderableWidget(new EditBox(this.font, x, t + BOX_Y, boxW, 16, Component.literal("X")));
        this.zBox = addRenderableWidget(new EditBox(this.font, x + boxW + 4, t + BOX_Y, boxW, 16, Component.literal("Z")));
        this.xBox.setMaxLength(9);
        this.zBox.setMaxLength(9);
        this.boxesFor = null;
        this.setButton = addRenderableWidget(Button.builder(Component.translatable(T_SET), b -> sendTarget())
                .bounds(x + CW - 74, t + BOX_Y, 34, 16).build());
        // To laser: aim the picks at a spot one of the network's Laser Designators is lasing (DESIGN.md §17.13).
        this.laserButton = addRenderableWidget(Button.builder(Component.translatable(T_LASER), b -> {
            StationPayloads.Scope s = scope();
            if (s != null && !s.spots().isEmpty()) {
                StationPayloads.SpotView spot = s.spots().getFirst();
                aimAt(spot.x(), spot.z());
            }
        }).bounds(x + CW - 38, t + BOX_Y, 38, 16).build());
        this.patternButton = addRenderableWidget(Button.builder(Component.translatable(T_PATTERN[this.pattern]), b -> {
            this.pattern = (this.pattern + 1) % T_PATTERN.length;
            b.setMessage(Component.translatable(T_PATTERN[this.pattern]));
        }).bounds(x, t + PATTERN_Y, CW - 70, 16).build());
        this.spacingBox = addRenderableWidget(new EditBox(this.font, x + CW - 30, t + PATTERN_Y, 30, 16, Component.translatable(T_SPACING)));
        this.spacingBox.setMaxLength(3);
        if (this.spacingBox.getValue().isEmpty()) {
            this.spacingBox.setValue("24");
        }
        this.armButton = addRenderableWidget(Button.builder(Component.translatable(T_ARM), b -> toggleArm())
                .bounds(x, t + ARM_Y, 62, 16).build());
        this.timingButton = addRenderableWidget(Button.builder(timingLabel(), b -> {
            this.together = !this.together;
            b.setMessage(timingLabel());
        }).bounds(x + 66, t + ARM_Y, CW - 66, 16).build());
        this.launchButton = addRenderableWidget(Button.builder(Component.translatable(T_LAUNCH, 0),
                b -> ClientPacketDistributor.sendToServer(new StationPayloads.Fire(this.pos, this.together))).bounds(x, t + LAUNCH_Y, CW, 20).build());
        this.unlinkButton = addRenderableWidget(Button.builder(Component.translatable(T_UNLINK), b -> unlink())
                .bounds(x + CW - 40, t + DETAIL_Y - 1, 40, 12).build());
        this.codesButton = addRenderableWidget(Button.builder(Component.translatable(T_NEW_CODES), b -> newCodes())
                .bounds(left() + W - 8 - 30 - 64, t + 2, 60, 12).build());
        addRenderableWidget(Button.builder(Component.translatable(T_CENTRE), b -> {
            this.viewX = this.pos.getX() + 0.5;
            this.viewZ = this.pos.getZ() + 0.5;
        }).bounds(mapX() + MAP - 88, mapY() + MAP + 3, 42, 14).build());
        addRenderableWidget(Button.builder(Component.translatable(T_CENTRE_PICK), b -> centreOnPicks())
                .bounds(mapX() + MAP - 42, mapY() + MAP + 3, 42, 14).build());
    }

    private Component timingLabel() {
        return Component.translatable(this.together ? T_TOGETHER : T_AT_ONCE);
    }

    private void centreOnPicks() {
        List<StationPayloads.MemberView> ms = pickedMembers();
        if (!ms.isEmpty()) {
            this.viewX = ms.stream().mapToDouble(m -> m.pos().getX() + 0.5).average().orElse(this.viewX);
            this.viewZ = ms.stream().mapToDouble(m -> m.pos().getZ() + 0.5).average().orElse(this.viewZ);
        }
    }

    private int spacing() {
        try {
            return this.spacingBox == null ? 24 : Mth.clamp(Integer.parseInt(this.spacingBox.getValue().trim()), 0, 256);
        } catch (NumberFormatException e) {
            return 24;
        }
    }

    private List<BlockPos> pickedLaunchers() {
        return pickedMembers().stream().filter(m -> !m.radar() && !m.lost()).map(StationPayloads.MemberView::pos).toList();
    }

    private void sendTarget() {
        if (this.xBox == null || this.zBox == null) {
            return;
        }
        try {
            aimAt(Integer.parseInt(this.xBox.getValue().trim()), Integer.parseInt(this.zBox.getValue().trim()));
        } catch (NumberFormatException ignored) {
            // not numbers: nothing to set
        }
    }

    private void aimAt(int x, int z) {
        List<BlockPos> ls = pickedLaunchers();
        if (ls.isEmpty() || !on()) {
            return;
        }
        if (this.xBox != null && this.zBox != null) {
            this.xBox.setValue(Integer.toString(x));
            this.zBox.setValue(Integer.toString(z));
        }
        ClientPacketDistributor.sendToServer(new StationPayloads.Aim(this.pos, ls, x, z, this.pattern, spacing()));
    }

    private boolean anyArmed(List<StationPayloads.MemberView> ms) {
        return ms.stream().anyMatch(StationPayloads.MemberView::willBeArmed);
    }

    private void toggleArm() {
        List<StationPayloads.MemberView> ms = pickedMembers().stream().filter(m -> !m.radar()).toList();
        if (!ms.isEmpty()) {
            ClientPacketDistributor.sendToServer(new StationPayloads.Arm(this.pos, ms.stream().map(StationPayloads.MemberView::pos).toList(),
                    !anyArmed(ms)));
        }
    }

    private void unlink() {
        StationPayloads.MemberView m = single();
        if (m == null) {
            return;
        }
        if (this.unlinkAsked >= 0 && this.ticks - this.unlinkAsked <= CONFIRM) {
            ClientPacketDistributor.sendToServer(new StationPayloads.Unlink(this.pos, m.pos()));
            this.picked.remove(m.pos());
            this.unlinkAsked = -1;
        } else {
            this.unlinkAsked = this.ticks;
        }
    }

    private void newCodes() {
        if (this.codesAsked >= 0 && this.ticks - this.codesAsked <= CONFIRM) {
            ClientPacketDistributor.sendToServer(new StationPayloads.NewCodes(this.pos));
            this.codesAsked = -1;
        } else {
            this.codesAsked = this.ticks;
        }
    }

    /** Whether a member can be armed at its aim (ready, the aim in its reach), as the screen knows it. */
    private static boolean canArm(StationPayloads.MemberView m) {
        return !m.radar() && !m.lost() && m.hasTarget() && m.readiness() == LaunchSiloBlockEntity.Readiness.READY.ordinal()
                && distance(m) <= m.range();
    }

    private static double distance(StationPayloads.MemberView m) {
        return Math.hypot(m.targetX() - m.pos().getX(), m.targetZ() - m.pos().getZ());
    }

    private static boolean counting(StationPayloads.MemberView m) {
        return m.readiness() == LaunchSiloBlockEntity.Readiness.COUNTING.ordinal() && m.countdown() >= 0;
    }

    @Override
    public void tick() {
        this.ticks++;
        List<StationPayloads.MemberView> ms = members();
        this.picked.removeIf(p -> ms.stream().noneMatch(m -> m.pos().equals(p)));
        if (this.picked.isEmpty() && !ms.isEmpty() && this.boxesFor == null) {
            this.picked.add(ms.getFirst().pos());
        }
        this.scroll = Mth.clamp(this.scroll, 0, Math.max(0, ms.size() - ROWS));
        StationPayloads.MemberView one = single();
        List<StationPayloads.MemberView> picks = pickedMembers();
        BlockPos first = picks.isEmpty() ? null : picks.getFirst().pos();
        if (this.xBox != null && this.zBox != null && first != null && !first.equals(this.boxesFor)) {
            this.boxesFor = first;
            StationPayloads.MemberView m = picks.getFirst();
            this.xBox.setValue(m.hasTarget() ? Integer.toString(m.targetX()) : "");
            this.zBox.setValue(m.hasTarget() ? Integer.toString(m.targetZ()) : "");
        }
        boolean on = on();
        if (on && this.map != null) {
            this.map.request(this.pos, this.viewX, this.viewZ, zoom());
        }
        int armed = (int) ms.stream().filter(m -> !m.radar() && !m.lost() && m.willBeArmed() && m.launchAt() < 0).count();
        List<StationPayloads.MemberView> launchers = picks.stream().filter(m -> !m.radar()).toList();
        if (this.setButton != null) {
            this.setButton.active = on && launchers.stream().anyMatch(m -> !counting(m) && !m.lost());
            if (this.laserButton != null) {
                StationPayloads.Scope sc = scope();
                this.laserButton.active = this.setButton.active && sc != null && !sc.spots().isEmpty();
            }
        }
        if (this.armButton != null) {
            boolean disarm = anyArmed(launchers);
            this.armButton.active = on && (disarm || launchers.stream().anyMatch(LaunchStationScreen::canArm));
            this.armButton.setMessage(Component.translatable(disarm ? T_DISARM : T_ARM));
        }
        if (this.launchButton != null) {
            this.launchButton.active = on && armed > 0;
            this.launchButton.setMessage(Component.translatable(T_LAUNCH, armed));
        }
        if (this.patternButton != null) {
            this.patternButton.active = launchers.size() > 1;
        }
        if (this.unlinkButton != null) {
            this.unlinkButton.active = on && one != null;
            this.unlinkButton.visible = !this.showLog;
            boolean asking = this.unlinkAsked >= 0 && this.ticks - this.unlinkAsked <= CONFIRM;
            this.unlinkButton.setMessage(Component.translatable(asking ? T_SURE : T_UNLINK));
        }
        if (this.codesButton != null) {
            this.codesButton.active = on;
            boolean asking = this.codesAsked >= 0 && this.ticks - this.codesAsked <= CONFIRM;
            this.codesButton.setMessage(Component.translatable(asking ? T_SURE : T_NEW_CODES));
        }
    }

    // --- input --------------------------------------------------------------------------------------------------------------
    private boolean inList(double x, double y) {
        return x >= col() && x < col() + CW && y >= top() + LIST_Y && y < top() + LIST_Y + ROWS * ROW;
    }

    /** The two tabs over the list: which one is under x/y (0 the members, 1 the log, -1 neither). */
    private int tabAt(double x, double y) {
        if (y < top() + TABS_Y - 1 || y >= top() + TABS_Y + 11) {
            return -1;
        }
        int x0 = col();
        int w0 = this.font.width(membersTab()) + 8;
        if (x >= x0 && x < x0 + w0) {
            return 0;
        }
        int w1 = this.font.width(Component.translatable(T_LOG)) + 8;
        return x >= x0 + w0 + 2 && x < x0 + w0 + 2 + w1 ? 1 : -1;
    }

    private Component membersTab() {
        return Component.translatable(T_MEMBERS, members().size());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int tab = tabAt(event.x(), event.y());
        if (tab >= 0) {
            this.showLog = tab == 1;
            return true;
        }
        if (inList(event.x(), event.y())) {
            if (this.showLog) {
                return true;
            }
            int row = this.scroll + (int) ((event.y() - top() - LIST_Y) / ROW);
            List<StationPayloads.MemberView> ms = members();
            if (row < ms.size()) {
                BlockPos p = ms.get(row).pos();
                if (Minecraft.getInstance().hasShiftDown()) {
                    if (!this.picked.remove(p)) {
                        this.picked.add(p);
                    }
                } else {
                    this.picked.clear();
                    this.picked.add(p);
                    if (doubleClick) {
                        centreOnPicks();
                    }
                }
                this.boxesFor = null;
                this.unlinkAsked = -1;
            }
            return true;
        }
        if (inMap(event.x(), event.y()) && on()) {
            if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
                aimAt((int) Math.floor(worldX(event.x())), (int) Math.floor(worldZ(event.y())));
                return true;
            }
            if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
                this.panning = true;
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (this.panning) {
            this.viewX -= dx * zoom();
            this.viewZ -= dy * zoom();
            clampView();
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.panning && event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            this.panning = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (inList(x, y)) {
            if (this.showLog) {
                StationPayloads.Scope s = scope();
                int n = s == null ? 0 : s.log().size();
                this.logScroll = Mth.clamp(this.logScroll - (int) Math.signum(scrollY), 0, Math.max(0, n - ROWS));
            } else {
                this.scroll = Mth.clamp(this.scroll - (int) Math.signum(scrollY), 0, Math.max(0, members().size() - ROWS));
            }
            return true;
        }
        if (inMap(x, y) && scrollY != 0) {
            double wx = worldX(x), wz = worldZ(y);
            this.zoomStep = Mth.clamp(this.zoomStep - (int) Math.signum(scrollY), 0, ZOOMS.length - 1);
            this.viewX = wx - (x - mapX() - MAP / 2.0) * zoom();
            this.viewZ = wz - (y - mapY() - MAP / 2.0) * zoom();
            clampView();
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        ClientPacketDistributor.sendToServer(new LaunchPayloads.Watch(this.pos, false));
        super.onClose();
    }

    @Override
    public void removed() {
        if (this.map != null) {
            this.map.close();
            this.map = null;
        }
        super.removed();
    }

    // --- words ----------------------------------------------------------------------------------------------------------------
    /** A member's kind, short. */
    /** A launcher kind's name (the network's record of it is its block's id). */
    public static Component kind(String id) {
        Identifier key = Identifier.tryParse(id);
        if (key != null && T_KIND.containsKey(key.getPath())) {
            return Component.translatable(T_KIND.get(key.getPath()));
        }
        return key == null ? Component.literal(id) : BuiltInRegistries.BLOCK.getValue(key).getName();
    }

    private static String clock(int ticks) {
        int s = Math.max(0, (ticks + 19) / 20);
        return s / 60 + ":" + String.format(Locale.ROOT, "%02d", s % 60);
    }

    /** What a member is doing, as its row says it, and the colour. */
    private Component state(StationPayloads.MemberView m, List<StationPayloads.ContactView> contacts) {
        if (m.lost()) {
            return Component.translatable(T_LOST);
        }
        if (m.radar()) {
            if (!m.contact()) {
                return Component.translatable(T_NOT_LOADED);
            }
            if (!m.working()) {
                return Component.translatable(T_RADAR_OFF);
            }
            return Component.translatable(T_TRACKING, tracked(m, contacts));
        }
        if (m.launchAt() >= 0) {
            return Component.translatable(T_HELD, clock((int) Math.max(0, m.launchAt() - now())));
        }
        int r = Mth.clamp(m.readiness(), 0, T_STATE.length - 1);
        if (r == LaunchSiloBlockEntity.Readiness.COUNTING.ordinal()) {
            return m.countdown() > 0 ? Component.translatable(T_STATE[r], (m.countdown() + 19) / 20)
                    : m.inFlight() >= 0 ? Component.translatable(T_FLIGHT, clock(m.inFlight())) : Component.translatable(T_STATE[r], 0);
        }
        if (m.inFlight() >= 0) {
            return Component.translatable(T_FLIGHT, clock(m.inFlight()));
        }
        if (m.pendingArm() >= 0 && (m.pendingArm() == 1) != m.armed()) {
            return Component.translatable(m.pendingArm() == 1 ? T_ARMING : T_SAFING);
        }
        if (m.armed()) {
            return Component.translatable(T_ARMED);
        }
        if (r == LaunchSiloBlockEntity.Readiness.CHARGING.ordinal()) {
            return Component.translatable(T_STATE[r], clock(m.countdown()));
        }
        return Component.translatable(T_STATE[r]);
    }

    private int stateColor(StationPayloads.MemberView m) {
        if (m.lost()) {
            return RED;
        }
        if (m.radar()) {
            return m.contact() && m.working() ? BRIGHT : DIM;
        }
        int r = m.readiness();
        boolean busy = m.launchAt() >= 0 || counting(m) || m.inFlight() >= 0;
        boolean down = r == LaunchSiloBlockEntity.Readiness.EMP.ordinal() || r == LaunchSiloBlockEntity.Readiness.HALTED.ordinal()
                || r == LaunchSiloBlockEntity.Readiness.BLOCKED.ordinal();
        return busy || down ? AMBER : m.willBeArmed() ? RED : r == LaunchSiloBlockEntity.Readiness.READY.ordinal() ? BRIGHT : DIM;
    }

    private static int tracked(StationPayloads.MemberView radar, List<StationPayloads.ContactView> contacts) {
        int n = 0;
        for (StationPayloads.ContactView c : contacts) {
            if (Math.abs(c.x() - radar.pos().getX()) <= RadarArrayBlockEntity.RANGE && Math.abs(c.z() - radar.pos().getZ()) <= RadarArrayBlockEntity.RANGE) {
                n++;
            }
        }
        return n;
    }

    /** A member named in the log: "#2 Missile Silo" while it is on the network, otherwise its kind and where it was. */
    private Component named(BlockPos member, String memberKind) {
        int n = number(member);
        return n > 0 ? Component.literal("#" + n + " ").append(kind(memberKind))
                : Component.translatable(T_GONE, kind(memberKind), member.getX(), member.getZ());
    }

    private Component event(LaunchNetwork.Event e) {
        Component who = named(e.member(), e.memberKind());
        String key = T_EVENT.get(e.kind());
        return switch (e.kind()) {
            case IMPACT, LOST -> Component.translatable(key, who, e.x(), e.z());
            case FAILED -> Component.translatable(key, who, e.detail() < 0 ? Component.translatable(T_NO_ANSWER)
                    : Component.translatable(T_STATE[Mth.clamp(e.detail(), 0, T_STATE.length - 1)], 0));
            case NEW_CODES -> Component.translatable(key);
            case FIRE_CALLED, DESIGNATED -> Component.translatable(key, who, e.x(), e.z());
            default -> Component.translatable(key, who);
        };
    }

    // --- drawing ------------------------------------------------------------------------------------------------------------
    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractBackground(g, mouseX, mouseY, partial);
        g.fill(left(), top(), left() + W, top() + H, PANEL);
        g.outline(left(), top(), W, H, EDGE);
        drawMap(g, mouseX, mouseY);
        drawTabs(g);
        if (this.showLog) {
            drawLog(g);
        } else {
            drawList(g);
        }
    }

    private void drawMap(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int x0 = mapX(), y0 = mapY(), x1 = x0 + MAP, y1 = y0 + MAP;
        g.outline(x0 - 1, y0 - 1, MAP + 2, MAP + 2, EDGE);
        StationPayloads.Scope s = scope();
        if (!on() || this.map == null || s == null) {
            g.fill(x0, y0, x1, y1, 0xFF040C06);
            Component off = Component.translatable(T_NO_POWER);
            g.text(this.font, off, x0 + MAP / 2 - this.font.width(off) / 2, y0 + MAP / 2 - 4, RED);
            return;
        }
        this.map.draw(g, x0, y0, this.pos, this.viewX, this.viewZ, zoom());
        g.enableScissor(x0, y0, x1, y1);
        // The radars' reach (dim squares), under everything.
        for (StationPayloads.MemberView m : s.members()) {
            if (m.radar() && !m.lost() && m.contact() && m.working()) {
                int ax0 = screenX(m.pos().getX() - RadarArrayBlockEntity.RANGE), ax1 = screenX(m.pos().getX() + RadarArrayBlockEntity.RANGE);
                int az0 = screenY(m.pos().getZ() - RadarArrayBlockEntity.RANGE), az1 = screenY(m.pos().getZ() + RadarArrayBlockEntity.RANGE);
                Phosphor.line(g, ax0, az0, ax1, az0, DIM, 4);
                Phosphor.line(g, ax0, az1, ax1, az1, DIM, 4);
                Phosphor.line(g, ax0, az0, ax0, az1, DIM, 4);
                Phosphor.line(g, ax1, az0, ax1, az1, DIM, 4);
            }
        }
        // Each armed (or launching) member's strike round its target, the picked over the rest.
        for (int pass = 0; pass < 2; pass++) {
            for (StationPayloads.MemberView m : s.members()) {
                boolean picked = this.picked.contains(m.pos());
                if ((pass == 1) == picked && !m.radar() && m.hasTarget() && m.strike().reach() > 0
                        && (m.willBeArmed() || counting(m) || m.launchAt() >= 0)) {
                    drawStrike(g, m, picked, x0, y0, x1, y1);
                }
            }
        }
        // The pattern the picks would take round the cursor.
        List<StationPayloads.MemberView> launchers = pickedMembers().stream().filter(m -> !m.radar() && !m.lost()).toList();
        if (inMap(mouseX, mouseY) && !this.panning && launchers.size() > 1) {
            int cx = (int) Math.floor(worldX(mouseX)), cz = (int) Math.floor(worldZ(mouseY));
            for (int[] p : LaunchNetwork.points(launchers.stream().map(StationPayloads.MemberView::pos).toList(), cx, cz,
                    LaunchNetwork.Pattern.values()[this.pattern], spacing())) {
                Phosphor.cross(g, screenX(p[0] + 0.5), screenY(p[1] + 0.5), 2, 0x99FF4A3A);
            }
        }
        // The station, each member and its target (the picked bright; armed ones joined to theirs by a red line).
        int cx = screenX(this.pos.getX() + 0.5), cz = screenY(this.pos.getZ() + 0.5);
        g.fill(cx - 2, cz - 2, cx + 3, cz + 3, WHITE);
        g.fill(cx - 1, cz - 1, cx + 2, cz + 2, 0xFF061A0C);
        boolean blink = this.ticks % 20 < 12;
        for (StationPayloads.MemberView m : s.members()) {
            boolean picked = this.picked.contains(m.pos());
            int sx = screenX(m.pos().getX() + 0.5), sz = screenY(m.pos().getZ() + 0.5);
            if (!m.radar() && m.hasTarget()) {
                int tx = screenX(m.targetX() + 0.5), tz = screenY(m.targetZ() + 0.5);
                boolean armed = m.willBeArmed() || m.launchAt() >= 0;
                if (armed || picked) {
                    Phosphor.line(g, sx, sz, tx, tz, armed ? 0xCCFF4A3A : 0x88FF4A3A, 2);
                }
                if (!m.pendingAim() || blink) {
                    Phosphor.cross(g, tx, tz, 3, picked ? RED : DIM_RED);
                }
            }
            int color = m.lost() ? (blink ? RED : DIM_RED) : picked ? WHITE : stateColor(m);
            if (m.radar()) {
                Phosphor.ring(g, sx, sz, 3, color);
            } else {
                g.fill(sx - 2, sz - 2, sx + 3, sz + 3, color);
            }
            label(g, Integer.toString(number(m.pos())), sx + 4, sz - 6, picked ? WHITE : DIM);
        }
        // The network's laser spots: pulsing red diamonds, who lases each beside it.
        boolean pulse = this.ticks % 10 < 6;
        for (StationPayloads.SpotView spot : s.spots()) {
            int px = screenX(spot.x() + 0.5), pz = screenY(spot.z() + 0.5);
            int r = pulse ? 4 : 3;
            Phosphor.line(g, px - r, pz, px, pz - r, RED, 0);
            Phosphor.line(g, px, pz - r, px + r, pz, RED, 0);
            Phosphor.line(g, px + r, pz, px, pz + r, RED, 0);
            Phosphor.line(g, px, pz + r, px - r, pz, RED, 0);
            g.fill(px, pz, px + 1, pz + 1, WHITE);
            if (!spot.who().isEmpty()) {
                label(g, spot.who(), px + 6, pz - 3, RED);
            }
        }
        // Missiles in the air: the network's amber, others its radars track red.
        for (StationPayloads.ContactView c : s.contacts()) {
            int mx = screenX(c.x()), mz = screenY(c.z());
            int col = c.own() ? AMBER : RED;
            Phosphor.line(g, mx, mz, screenX(c.targetX()), screenY(c.targetZ()), c.own() ? 0x88FFB040 : 0x88FF4A3A, 2);
            g.fill(mx - 1, mz - 1, mx + 2, mz + 2, col);
            Phosphor.line(g, mx, mz, mx + (int) (Math.sin(c.heading()) * 5), mz + (int) (Math.cos(c.heading()) * 5), col, 0);
        }
        if (inMap(mouseX, mouseY) && !this.panning) {
            g.fill(x0, mouseY, x1, mouseY + 1, 0x3340FF70);
            g.fill(mouseX, y0, mouseX + 1, y1, 0x3340FF70);
        }
        Phosphor.scanlines(g, x0, y0, x1, y1);
        g.disableScissor();
    }

    /** Small type (as the list's), drawn on whole screen pixels. */
    private void label(GuiGraphicsExtractor g, String text, int x, int y, int color) {
        int gs = Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
        float scale = Math.max(1, gs * 2 / 3) / (float) gs;
        g.pose().pushMatrix();
        g.pose().translate(x, Math.round(y * gs) / (float) gs);
        g.pose().scale(scale, scale);
        g.text(this.font, text, 0, 0, color, false);
        g.pose().popMatrix();
    }

    private void drawStrike(GuiGraphicsExtractor g, StationPayloads.MemberView m, boolean picked, int x0, int y0, int x1, int y1) {
        LaunchSite.Strike k = m.strike();
        double scale = zoom();
        int tx = screenX(m.targetX() + 0.5), tz = screenY(m.targetZ() + 0.5);
        int lethal = (int) Math.round(k.lethal() / scale), reach = (int) Math.round(k.reach() / scale);
        if (lethal > 0) {
            Phosphor.disc(g, tx, tz, lethal, picked ? 0x40FF4A3A : 0x30701C16, x0, y0, x1, y1);
        }
        Phosphor.ring(g, tx, tz, Math.max(1, reach), picked ? RED : 0xCC7A2418);
        if (k.along() / scale >= 1.5) {
            double dx = m.targetX() - m.pos().getX(), dz = m.targetZ() - m.pos().getZ(), d = Math.sqrt(dx * dx + dz * dz);
            Phosphor.ellipse(g, tx, tz, (k.along() + k.reach()) / scale, (k.across() + k.reach()) / scale, d > 1e-6 ? dx / d : 1,
                    d > 1e-6 ? dz / d : 0, picked ? 0xCCFF4A3A : 0x997A2418, 3);
        }
    }

    private void drawTabs(GuiGraphicsExtractor g) {
        int x = col(), y = top() + TABS_Y;
        Component a = membersTab(), b = Component.translatable(T_LOG);
        int wa = this.font.width(a) + 8, wb = this.font.width(b) + 8;
        g.fill(x, y - 1, x + wa, y + 11, this.showLog ? 0xFF0A200F : SELECTED_BG);
        g.fill(x + wa + 2, y - 1, x + wa + 2 + wb, y + 11, this.showLog ? SELECTED_BG : 0xFF0A200F);
        g.text(this.font, a, x + 4, y + 1, this.showLog ? DIM : TEXT, false);
        g.text(this.font, b, x + wa + 6, y + 1, this.showLog ? TEXT : DIM, false);
    }

    private void drawList(GuiGraphicsExtractor g) {
        int x = col(), y = top() + LIST_Y, h = ROWS * ROW;
        g.fill(x, y, x + CW, y + h, LIST_BG);
        g.outline(x - 1, y - 1, CW + 2, h + 2, EDGE);
        StationPayloads.Scope s = scope();
        List<StationPayloads.MemberView> ms = members();
        if (ms.isEmpty()) {
            var lines = this.font.split(Component.translatable(T_EMPTY), CW - 6);
            for (int i = 0; i < lines.size() && i < ROWS; i++) {
                g.text(this.font, lines.get(i), x + 3, y + 2 + i * 10, DIM, false);
            }
            return;
        }
        boolean bar = ms.size() > ROWS;
        int textW = CW - 6 - (bar ? 4 : 0);
        int gs = Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
        float scale = Math.max(1, gs * 2 / 3) / (float) gs;
        int room = (int) (textW / scale);
        List<StationPayloads.ContactView> contacts = s == null ? List.of() : s.contacts();
        for (int i = 0; i < ROWS && this.scroll + i < ms.size(); i++) {
            StationPayloads.MemberView m = ms.get(this.scroll + i);
            int ry = y + i * ROW;
            if (this.picked.contains(m.pos())) {
                g.fill(x, ry, x + CW - (bar ? 4 : 0), ry + ROW, SELECTED_BG);
            }
            String right = m.radar() ? "" : m.hasTarget() ? (int) distance(m) + "m" : Component.translatable(T_NO_TARGET).getString();
            int rightW = this.font.width(right);
            String label = (this.scroll + i + 1) + ". " + kind(m.kind()).getString() + "  " + state(m, contacts).getString();
            int color = stateColor(m);
            if (!m.contact() && !m.lost() && color != RED) {
                color = blend(color);                                 // asleep (not loaded): a little dimmer
            }
            g.pose().pushMatrix();
            g.pose().translate(x + 3, Math.round((ry + (ROW - 8 * scale) / 2f) * gs) / (float) gs);
            g.pose().scale(scale, scale);
            g.text(this.font, right, room - rightW, 0, m.hasTarget() && !m.radar() && m.range() > 0 && distance(m) > m.range() ? RED : DIM, false);
            g.text(this.font, this.font.plainSubstrByWidth(label, room - rightW - 6), 0, 0, color, false);
            g.pose().popMatrix();
        }
        if (bar) {
            int track = h - 2, thumb = Math.max(6, track * ROWS / ms.size());
            int ty = y + 1 + (track - thumb) * this.scroll / Math.max(1, ms.size() - ROWS);
            g.fill(x + CW - 3, y + 1, x + CW - 1, y + h - 1, 0xFF0E2A16);
            g.fill(x + CW - 3, ty, x + CW - 1, ty + thumb, DIM);
        }
    }

    /** A colour two thirds as bright. */
    private static int blend(int argb) {
        int r = (argb >> 16 & 0xFF) * 2 / 3, gr = (argb >> 8 & 0xFF) * 2 / 3, b = (argb & 0xFF) * 2 / 3;
        return argb & 0xFF000000 | r << 16 | gr << 8 | b;
    }

    private void drawLog(GuiGraphicsExtractor g) {
        int x = col(), y = top() + LIST_Y, h = ROWS * ROW;
        g.fill(x, y, x + CW, y + h, LIST_BG);
        g.outline(x - 1, y - 1, CW + 2, h + 2, EDGE);
        StationPayloads.Scope s = scope();
        List<LaunchNetwork.Event> log = s == null ? List.of() : new ArrayList<>(s.log());
        if (log.isEmpty()) {
            g.text(this.font, Component.translatable(T_NO_LOG), x + 3, y + 2, DIM, false);
            return;
        }
        java.util.Collections.reverse(log);                            // newest first
        this.logScroll = Mth.clamp(this.logScroll, 0, Math.max(0, log.size() - ROWS));
        int gs = Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
        float scale = Math.max(1, gs * 2 / 3) / (float) gs;
        int room = (int) ((CW - 6) / scale);
        for (int i = 0; i < ROWS && this.logScroll + i < log.size(); i++) {
            LaunchNetwork.Event e = log.get(this.logScroll + i);
            int ry = y + i * ROW;
            String ago = Component.translatable(T_AGO, clock((int) Math.max(0, now() - e.time()))).getString();
            int agoW = this.font.width(ago);
            int color = switch (e.kind()) {
                case LAUNCHED -> AMBER;
                case IMPACT -> BRIGHT;
                case LOST, NO_CONTACT, FAILED -> RED;
                default -> TEXT;
            };
            g.pose().pushMatrix();
            g.pose().translate(x + 3, Math.round((ry + (ROW - 8 * scale) / 2f) * gs) / (float) gs);
            g.pose().scale(scale, scale);
            g.text(this.font, ago, room - agoW, 0, DIM, false);
            g.text(this.font, this.font.plainSubstrByWidth(event(e).getString(), room - agoW - 6), 0, 0, color, false);
            g.pose().popMatrix();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        int x = col(), t = top();
        StationPayloads.Scope s = scope();
        g.text(this.font, this.title, left() + 8, t + 4, TEXT);
        if (s != null && !s.code().isEmpty()) {
            Component code = Component.translatable(T_NETWORK, s.code());
            g.text(this.font, code, left() + 8 + this.font.width(this.title) + 10, t + 4, AMBER);
        }
        if (s != null) {
            Component energy = Component.literal(s.energy() + "%");
            g.text(this.font, energy, left() + W - 8 - this.font.width(energy), t + 4, s.powered() ? BRIGHT : RED);
            if (!s.contacts().isEmpty() && on()) {                // over the map's top right corner
                Component c = Component.translatable(T_CONTACTS, s.contacts().size());
                g.text(this.font, c, mapX() + MAP - this.font.width(c) - 3, mapY() + 3, AMBER, true);
            }
        }
        if (on()) {
            boolean over = inMap(mouseX, mouseY);
            int cx = (int) Math.floor(over ? worldX(mouseX) : this.viewX), cz = (int) Math.floor(over ? worldZ(mouseY) : this.viewZ);
            g.text(this.font, Component.translatable(T_CURSOR, cx, cz), mapX(), mapY() + MAP + 6, over ? TEXT : DIM, false);
            Component zoom = Component.translatable(T_ZOOM, zoom());
            g.text(this.font, zoom, mapX() + MAP - 92 - this.font.width(zoom), mapY() + MAP + 6, DIM, false);
        }
        // The picked member (or how many are picked) and its aim.
        List<StationPayloads.MemberView> picks = pickedMembers();
        StationPayloads.MemberView one = single();
        List<StationPayloads.ContactView> contacts = s == null ? List.of() : s.contacts();
        int textW = CW - (one != null && !this.showLog ? 44 : 0);
        if (one != null) {
            Component head = Component.translatable(T_AT, number(one.pos()), kind(one.kind()), one.pos().getX(), one.pos().getZ());
            g.text(this.font, this.font.plainSubstrByWidth(head.getString(), textW), x, t + DETAIL_Y + 1, TEXT, false);
            Component what = one.radar() ? Component.translatable(T_RADAR_SEES, tracked(one, contacts), RadarArrayBlockEntity.RANGE)
                    : one.shown().isEmpty() ? Component.translatable(T_NOTHING)
                    : one.shown().getCount() > 1 ? one.shown().getHoverName().copy().append(" x" + one.shown().getCount()) : one.shown().getHoverName();
            g.text(this.font, this.font.plainSubstrByWidth(what.getString(), CW), x, t + DETAIL_Y + 11, DIM, false);
            if (!one.radar() && one.hasTarget()) {
                double d = distance(one);
                Component line = one.range() > 0 && d > one.range() ? Component.translatable(T_TOO_FAR, one.range())
                        : Component.translatable(T_RANGE, (int) d, clock(one.timing().toImpact(one.targetX(), one.targetZ())));
                g.text(this.font, this.font.plainSubstrByWidth(line.getString(), CW), x, t + INFO_Y, one.range() > 0 && d > one.range() ? RED : TEXT, false);
            }
        } else if (!picks.isEmpty()) {
            g.text(this.font, Component.translatable(T_PICKED, picks.size()), x, t + DETAIL_Y + 1, TEXT, false);
            Component pattern = Component.translatable(T_PATTERN_OF, Component.translatable(T_PATTERN[this.pattern]).getString()
                    .replaceFirst("^[^:]*:\\s*", ""));
            g.text(this.font, this.font.plainSubstrByWidth(pattern.getString(), CW), x, t + DETAIL_Y + 11, DIM, false);
            int last = -1;
            for (StationPayloads.MemberView m : picks) {
                if (!m.radar() && m.hasTarget()) {
                    last = Math.max(last, m.timing().toImpact(m.targetX(), m.targetZ()));
                }
            }
            if (last >= 0) {
                g.text(this.font, Component.translatable(T_FARTHEST, clock(last)), x, t + INFO_Y, TEXT, false);
            }
        }
        g.text(this.font, this.font.plainSubstrByWidth(Component.translatable(T_HINT).getString(), CW), x, t + HINT_Y, DIM, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
