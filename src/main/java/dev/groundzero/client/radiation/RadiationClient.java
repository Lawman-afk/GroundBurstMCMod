package dev.groundzero.client.radiation;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import dev.groundzero.GroundZero;
import dev.groundzero.config.GZClientConfig;
import dev.groundzero.content.nuclear.GZEffects;
import dev.groundzero.content.radiation.DosimeterItem;
import dev.groundzero.content.radiation.GZRadiation;
import dev.groundzero.content.radiation.GeigerCounterItem;
import dev.groundzero.content.radiation.RadiationItems;
import dev.groundzero.content.radiation.RadiationState;
import dev.groundzero.registry.GZSounds;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterRangeSelectItemModelPropertyEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

/**
 * What the player sees and hears of radiation (DESIGN.md §21.3, §21.8): the Geiger Counter's clicks (a Poisson process at
 * a rate rising with the dose rate) and alarm; its meter in the HUD corner (a needle on a log scale from 0.1 to
 * 10 000 RAD/h over a digital readout) while held, the readout alone while in the hotbar; the Dosimeter Badge's dose
 * under it; the edge of sight darkening at Radiation Sickness IV; and the badge's film darkening with the dose.
 */
public final class RadiationClient {
    /** The HUD's sheet: the meter (dial over readout), and the dose strip. */
    static final Identifier HUD = GroundZero.id("textures/gui/geiger_hud.png");
    static final Identifier VIGNETTE = GroundZero.id("textures/gui/radiation_vignette.png");
    static final int SHEET_W = 128, SHEET_H = 128;
    /** The meter: dial ({@link #DIAL_H} high) over the readout window, in the sheet at 0, 0. */
    static final int METER_W = 80, DIAL_H = 48, READOUT_H = 14, METER_H = DIAL_H + READOUT_H;
    /** The readout alone (hotbar), in the sheet at 0, 64; the dose strip at 0, 80. */
    static final int READOUT_V = 64, DOSE_V = 80, DOSE_H = 12;
    /** The needle's pivot in the dial, its length and its swing either side of upright (degrees). */
    static final int PIVOT_X = 40, PIVOT_Y = 45, NEEDLE = 33;
    static final float SWING = 50;
    /** The dial's scale: log10 of its ends, RAD/h. */
    static final float LOG_MIN = -1, LOG_MAX = 4;
    /** Clicks a second with nothing about (background), at most (above which a tube saturates), and the response. */
    static final double BACKGROUND = 0.25, MAX_CLICKS = 60, GAIN = 12, CURVE = 0.75;
    static final int MARGIN = 4;
    static final int LCD = 0xFF9CFF7A, LCD_ALARM = 0xFFFF6A4A, LCD_DIM = 0xFF2C4A26;

    private static final RandomSource RANDOM = RandomSource.create();
    /** The needle's shown reading (log10 RAD/h), easing toward the true one like a damped movement. */
    private static float needle = LOG_MIN;
    private static long lastFrame;
    private static int alarmTicks;

    private RadiationClient() {}

    public static void register(IEventBus modBus) {
        DosimeterItem.local = () -> {
            Player player = Minecraft.getInstance().player;
            return player == null ? null : state(player);
        };
        modBus.addListener((RegisterGuiLayersEvent event) -> {
            event.registerAbove(VanillaGuiLayers.HOTBAR, GroundZero.id("radiation_meter"), RadiationClient::meter);
            event.registerBelow(VanillaGuiLayers.HOTBAR, GroundZero.id("radiation_sickness"), RadiationClient::vignette);
        });
        modBus.addListener((RegisterRangeSelectItemModelPropertyEvent event) -> event.register(GroundZero.id("dose"), Dose.CODEC));
        modBus.addListener((RegisterMenuScreensEvent event) -> event.register(RadiationItems.LEAD_CONTAINER_MENU.get(), LeadContainerScreen::new));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> tick(Minecraft.getInstance()));
    }

    static RadiationState state(Player player) {
        return player.hasData(GZRadiation.STATE) ? player.getData(GZRadiation.STATE) : RadiationState.NONE;
    }

    /** Clicks, and the alarm, once a tick. */
    private static void tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.isPaused()) {
            return;
        }
        ItemStack counter = GeigerCounterItem.counter(player);
        if (counter.isEmpty()) {
            alarmTicks = 0;
            return;
        }
        float rate = state(player).rate();
        // A Poisson process: this tick's clicks drawn from the mean the dose rate gives (a tube saturates past MAX_CLICKS).
        double mean = Math.min(MAX_CLICKS, BACKGROUND + GAIN * Math.pow(rate, CURVE)) / 20;
        int clicks = poisson(mean);
        for (int i = 0; i < Math.min(clicks, 3); i++) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(GZSounds.GEIGER_CLICK.get(), 0.92f + RANDOM.nextFloat() * 0.16f,
                    0.55f + 0.1f * i));
        }
        float alarm = GeigerCounterItem.alarm(counter);
        if (alarm > 0 && rate >= alarm) {
            if (alarmTicks++ % 12 == 0) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(GZSounds.GEIGER_ALARM.get(), 1f, 0.7f));
            }
        } else {
            alarmTicks = 0;
        }
    }

    private static int poisson(double mean) {
        double limit = Math.exp(-mean), p = 1;
        int k = 0;
        do {
            k++;
            p *= RANDOM.nextDouble();
        } while (p > limit && k < 20);
        return k - 1;
    }

    /** The meter (held), or the readout (hotbar), and the dose (with a badge), in the configured corner. */
    private static void meter(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        GeigerCounterItem.Carried carried = GeigerCounterItem.carried(player);
        boolean badge = hasBadge(player);
        if (carried == GeigerCounterItem.Carried.NONE && !badge) {
            return;
        }
        RadiationState state = state(player);
        int h = (carried == GeigerCounterItem.Carried.HELD ? METER_H : carried == GeigerCounterItem.Carried.HOTBAR ? READOUT_H : 0)
                + (badge ? DOSE_H + 1 : 0);
        GZClientConfig.HudCorner corner = GZClientConfig.GEIGER_CORNER.get();
        boolean right = corner == GZClientConfig.HudCorner.TOP_RIGHT || corner == GZClientConfig.HudCorner.BOTTOM_RIGHT;
        boolean bottom = corner == GZClientConfig.HudCorner.BOTTOM_LEFT || corner == GZClientConfig.HudCorner.BOTTOM_RIGHT;
        int x = right ? g.guiWidth() - METER_W - MARGIN : MARGIN;
        int y = bottom ? g.guiHeight() - h - MARGIN : MARGIN;
        Font font = mc.font;
        float alarm = carried == GeigerCounterItem.Carried.NONE ? 0 : GeigerCounterItem.alarm(GeigerCounterItem.counter(player));
        boolean alarming = alarm > 0 && state.rate() >= alarm && (player.tickCount / 6) % 2 == 0;
        if (carried == GeigerCounterItem.Carried.HELD) {
            g.blit(RenderPipelines.GUI_TEXTURED, HUD, x, y, 0, 0, METER_W, METER_H, SHEET_W, SHEET_H);
            drawNeedle(g, x, y, state.rate());
            readout(g, font, x, y + DIAL_H, state.rate(), alarming);
            y += METER_H;
        } else if (carried == GeigerCounterItem.Carried.HOTBAR) {
            g.blit(RenderPipelines.GUI_TEXTURED, HUD, x, y, 0, READOUT_V, METER_W, READOUT_H, SHEET_W, SHEET_H);
            readout(g, font, x, y, state.rate(), alarming);
            y += READOUT_H;
        }
        if (badge) {
            y += 1;
            g.blit(RenderPipelines.GUI_TEXTURED, HUD, x, y, 0, DOSE_V, METER_W, DOSE_H, SHEET_W, SHEET_H);
            int stage = state.stage();
            g.fill(x + 3, y + 2, x + 11, y + 10, DosimeterItem.STAGE_COLOURS[stage]);
            String dose = Math.round(state.dose()) + " rad";
            var pose = g.pose();
            pose.pushMatrix();
            pose.translate(x + 14, y + 3);
            pose.scale(0.75f, 0.75f);
            g.text(font, "DOSE", 0, 0, 0xFF9AA0A8, false);
            g.text(font, dose, (METER_W - 18) * 4 / 3 - font.width(dose), 0, DosimeterItem.STAGE_COLOURS[stage], false);
            pose.popMatrix();
        }
    }

    /** The needle, eased toward the reading, trembling a little at high rates like a real movement on a busy tube. */
    private static void drawNeedle(GuiGraphicsExtractor g, int x, int y, float rate) {
        long now = net.minecraft.util.Util.getMillis();
        float dt = Math.min(0.25f, (now - lastFrame) / 1000f);
        lastFrame = now;
        float target = (float) Mth.clamp(Math.log10(Math.max(rate, 1e-3)), LOG_MIN, LOG_MAX);
        needle += (target - needle) * (1 - (float) Math.exp(-dt / 0.35f));
        float t = (needle - LOG_MIN) / (LOG_MAX - LOG_MIN);
        float shake = rate > 1 ? (RANDOM.nextFloat() - 0.5f) * 0.012f : 0;
        float angle = (float) Math.toRadians(-SWING + 2 * SWING * Mth.clamp(t + shake, 0, 1));
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x + PIVOT_X + 0.5f, y + PIVOT_Y + 0.5f);
        pose.rotate(angle);
        g.fill(0, -NEEDLE, 1, 0, 0xFF1A1A1A);           // the needle, black on the cream dial
        g.fill(0, -NEEDLE, 1, -NEEDLE + 6, 0xFFC8302A);  // its red tip
        pose.popMatrix();
        g.fill(x + PIVOT_X - 1, y + PIVOT_Y - 1, x + PIVOT_X + 2, y + PIVOT_Y + 2, 0xFF2A2A2A);  // the boss
    }

    /** The digital readout: three significant figures and the unit; red and blinking over the alarm's threshold. */
    private static void readout(GuiGraphicsExtractor g, Font font, int x, int y, float rate, boolean alarming) {
        String value = rate < 0.01f ? "0.00" : rate < 10 ? String.format(java.util.Locale.ROOT, "%.2f", rate)
                : rate < 100 ? String.format(java.util.Locale.ROOT, "%.1f", rate)
                : java.text.NumberFormat.getIntegerInstance(java.util.Locale.ROOT).format(Math.round(rate));
        int colour = alarming ? LCD_ALARM : LCD;
        var pose = g.pose();                                   // a 3/4 font, so the reading and its unit fit the glass
        pose.pushMatrix();
        pose.translate(x + METER_W - 27, y + 4);
        pose.scale(0.75f, 0.75f);
        g.text(font, value, -2 - font.width(value), 0, colour, false);
        g.text(font, "RAD/h", 2, 0, 0xFF6A8A5A, false);
        pose.popMatrix();
    }

    private static boolean hasBadge(Player player) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).getItem() instanceof DosimeterItem) {
                return true;
            }
        }
        return false;
    }

    /** At Radiation Sickness IV the edge of sight darkens and swims. */
    private static void vignette(GuiGraphicsExtractor g, DeltaTracker delta) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        MobEffectInstance sick = player.getEffect(GZEffects.RADIATION_SICKNESS);
        if (sick == null || sick.getAmplifier() < 3) {
            return;
        }
        float pulse = 0.55f + 0.25f * Mth.sin((player.tickCount + delta.getGameTimeDeltaPartialTick(false)) * 0.12f);
        g.blit(RenderPipelines.GUI_TEXTURED, VIGNETTE, 0, 0, 0, 0, g.guiWidth(), g.guiHeight(), 256, 256, 256, 256,
                ARGB.color((int) (pulse * 255), 0xFFFFFF));
    }

    /** The {@code groundzero:dose} item model property: the dose its holder has taken (the local player's), rad. */
    public record Dose() implements RangeSelectItemModelProperty {
        public static final MapCodec<Dose> CODEC = MapCodec.unit(new Dose());

        @Override
        public float get(ItemStack stack, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
            Player local = Minecraft.getInstance().player;
            if (local == null) {
                return 0;
            }
            if (owner != null && owner.asLivingEntity() != local) {
                return 0;
            }
            return state(local).dose();
        }

        @Override
        public MapCodec<? extends RangeSelectItemModelProperty> type() {
            return CODEC;
        }
    }
}
