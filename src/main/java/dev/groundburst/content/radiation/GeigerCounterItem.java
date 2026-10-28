package dev.groundburst.content.radiation;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import dev.groundburst.GroundBurst;
import dev.groundburst.content.effect.EmpFields;
import dev.groundburst.content.power.BatteryItem;
import dev.groundburst.registry.GBDataComponents;
import dev.groundburst.registry.GBLang;
import dev.groundburst.registry.GBSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * The Geiger Counter (DESIGN.md §21.3, §21.8). In either hand it clicks at a random rate rising with the dose rate where
 * its holder stands, and the HUD shows its meter (a needle on a log scale and a digital readout, RAD/h); anywhere in
 * the hotbar it still clicks and shows the readout alone. An alarm beeps above a threshold, set by using it while
 * sneaking (off, 1, 10, 100, 1 000 RAD/h). It runs on its own charge, {@link #PER_SECOND} FE a second while it is on
 * (the Battery Charger fills it), and dies in an EMP field until 10 s after its holder leaves.
 */
public class GeigerCounterItem extends Item {
    public static final int CAPACITY = 20_000, TRANSFER = 500, PER_SECOND = 5;
    /** The alarm's thresholds, RAD/h (index 0: off). New counters are set to 100 (sickness within the hour). */
    public static final float[] ALARMS = {0, 1, 10, 100, 1_000};
    public static final int DEFAULT_ALARM = 3;
    /** How long after leaving an EMP field it stays dead, ticks. */
    public static final int EMP_TICKS = 200;

    static final String ALARM = GBLang.add(GroundBurst.MODID + ".tooltip.geiger_alarm", "Alarm: %s");
    static final String ALARM_SET = GBLang.add(GroundBurst.MODID + ".message.geiger_alarm", "Geiger alarm: %s");
    static final String ALARM_OFF = GBLang.add(GroundBurst.MODID + ".geiger.alarm_off", "off");
    static final String ALARM_RATE = GBLang.add(GroundBurst.MODID + ".geiger.alarm_rate", "above %s RAD/h");
    static final String HINT = GBLang.add(GroundBurst.MODID + ".tooltip.geiger_hint",
            "In hand: clicks and its meter. In the hotbar: clicks and the readout. Sneak-use: set the alarm.");
    static final String DEAD = GBLang.add(GroundBurst.MODID + ".tooltip.geiger_dead", "Flat: charge it in a Battery Charger");

    /** How a player carries a working counter: in either hand (the meter), in the hotbar (the readout), or not at all. */
    public enum Carried { NONE, HOTBAR, HELD }

    public GeigerCounterItem(Properties properties) {
        super(properties.stacksTo(1).component(GBDataComponents.ENERGY.get(), CAPACITY)
                .component(GBDataComponents.GEIGER_ALARM.get(), DEFAULT_ALARM));
    }

    public static int stored(ItemStack stack) {
        return stack.getOrDefault(GBDataComponents.ENERGY.get(), 0);
    }

    /** Whether it reads nothing: flat, or knocked out by an EMP. */
    public static boolean dead(ItemStack stack, Level level) {
        return stored(stack) <= 0 || level.getGameTime() < stack.getOrDefault(GBDataComponents.EMP_UNTIL.get(), 0L);
    }

    /** Its alarm threshold, RAD/h (0: off). */
    public static float alarm(ItemStack stack) {
        int i = stack.getOrDefault(GBDataComponents.GEIGER_ALARM.get(), DEFAULT_ALARM);
        return ALARMS[Mth.clamp(i, 0, ALARMS.length - 1)];
    }

    /** The working counter {@code player} carries where it counts (a hand first, then the hotbar), or empty. */
    public static ItemStack counter(Player player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack s = player.getItemInHand(hand);
            if (s.getItem() instanceof GeigerCounterItem && !dead(s, player.level())) {
                return s;
            }
        }
        Inventory inventory = player.getInventory();
        for (int i = 0; i < Inventory.getSelectionSize(); i++) {
            ItemStack s = inventory.getItem(i);
            if (s.getItem() instanceof GeigerCounterItem && !dead(s, player.level())) {
                return s;
            }
        }
        return ItemStack.EMPTY;
    }

    public static Carried carried(Player player) {
        ItemStack s = counter(player);
        if (s.isEmpty()) {
            return Carried.NONE;
        }
        return player.getMainHandItem() == s || player.getOffhandItem() == s ? Carried.HELD : Carried.HOTBAR;
    }

    /** Whether {@code stack} is where it works for {@code player}: a hand or the hotbar. */
    private static boolean on(Player player, ItemStack stack) {
        if (player.getMainHandItem() == stack || player.getOffhandItem() == stack) {
            return true;
        }
        Inventory inventory = player.getInventory();
        for (int i = 0; i < Inventory.getSelectionSize(); i++) {
            if (inventory.getItem(i) == stack) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
        if (!(owner instanceof Player player) || level.getGameTime() % 20 != 0 || !on(player, stack)) {
            return;
        }
        if (EmpFields.inField(level, player.position())) {
            stack.set(GBDataComponents.EMP_UNTIL.get(), level.getGameTime() + EMP_TICKS);
            return;
        }
        int energy = stored(stack);
        if (energy > 0 && !player.isCreative()) {
            stack.set(GBDataComponents.ENERGY.get(), Math.max(0, energy - PER_SECOND));
        }
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!player.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }
        ItemStack stack = player.getItemInHand(hand);
        int next = (stack.getOrDefault(GBDataComponents.GEIGER_ALARM.get(), DEFAULT_ALARM) + 1) % ALARMS.length;
        stack.set(GBDataComponents.GEIGER_ALARM.get(), next);
        if (!level.isClientSide()) {
            player.sendOverlayMessage(Component.translatable(ALARM_SET, alarmText(ALARMS[next])));
        }
        level.playSound(player, player.getX(), player.getY(), player.getZ(), GBSounds.GEIGER_SWITCH.get(), SoundSource.PLAYERS, 0.7f, 1f);
        return InteractionResult.SUCCESS;
    }

    static Component alarmText(float threshold) {
        return threshold <= 0 ? Component.translatable(ALARM_OFF)
                : Component.translatable(ALARM_RATE, java.text.NumberFormat.getIntegerInstance(java.util.Locale.ROOT).format(threshold));
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return stored(stack) < CAPACITY;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13f * stored(stack) / CAPACITY);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return Mth.hsvToRgb(0.08f + 0.22f * stored(stack) / CAPACITY, 0.85f, 1f);   // amber when low, green when full
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(BatteryItem.chargeLine(stored(stack), CAPACITY));
        if (stored(stack) <= 0) {
            builder.accept(Component.translatable(DEAD).withColor(0xFFE0A040));
        }
        builder.accept(Component.translatable(ALARM, alarmText(alarm(stack))).withColor(0xFF9AA0A8));
        builder.accept(Component.translatable(HINT).withColor(0xFF7A8088));
    }
}
