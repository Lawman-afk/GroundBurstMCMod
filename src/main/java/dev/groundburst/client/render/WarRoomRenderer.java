package dev.groundburst.client.render;

import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import dev.groundburst.GroundBurst;
import dev.groundburst.client.screen.LaunchStationScreen;
import dev.groundburst.content.command.GBCommand;
import dev.groundburst.content.command.WarRoomDisplayBlockEntity;
import dev.groundburst.content.command.WarRoomPayloads;
import dev.groundburst.content.launch.LaunchNetwork;
import dev.groundburst.content.launch.StationPayloads;
import dev.groundburst.registry.GBLang;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;

/**
 * The War Room Display (DESIGN.md §17.17): its model (the frame, the alert condition's lamps and the clock set by the
 * block entity), and on its screen its network's picture ({@link WarRoomBoard}) with, in lit type over it, the network's
 * code and counts, the alert condition and the soonest impact, the members' numbers, the incoming missiles' times, the
 * blasts' magnitudes, the scale, and the last lines of its log.
 */
public class WarRoomRenderer extends MachineGeoRenderer<WarRoomDisplayBlockEntity> {
    private static final String P = GroundBurst.MODID + ".war_room.";
    public static final String T_NET = GBLang.add(P + "net", "NET %s");
    public static final String T_COUNTS = GBLang.add(P + "counts", "%s LINKED   %s ARMED   %s IN FLIGHT   %s INCOMING");
    public static final String T_IMPACT = GBLang.add(P + "impact", "IMPACT %s");
    public static final String T_NO_NETWORK = GBLang.add(P + "no_network", "NO NETWORK: USE LAUNCH CODES ON THIS DISPLAY");
    private static final String T_SCALE = GBLang.add(P + "scale", "1:%s");
    public static final String[] T_CONDITION = {
            GBLang.add(P + "condition.1", "CONDITION 1: ATTACK"),
            GBLang.add(P + "condition.2", "CONDITION 2: INCOMING"),
            GBLang.add(P + "condition.3", "CONDITION 3: MISSILES IN FLIGHT"),
            GBLang.add(P + "condition.4", "CONDITION 4: ARMED"),
            GBLang.add(P + "condition.5", "CONDITION 5: NORMAL")};
    public static final int[] CONDITION_COLOUR = {0xFFFF3A24, 0xFFFF7A24, 0xFFFFB428, 0xFFFFE040, 0xFF46E04A};

    /** The screen's glass (model pixels): its left and right, its top and bottom, and its face's z (a hair in front). */
    private static final float LEFT = -53f, TOP = 61f, FACE = 6.04f;
    private static final float TEXEL = 1 / 64f;
    private static final int LIT = 15728880;
    private static final int GREEN = 0xFF46E04A, DIM_GREEN = 0xFF2C8A30, AMBER = 0xFFFFB428, RED = 0xFFFF3A24, WHITE = 0xFFE8F0E8;

    /** What it draws this frame: the board, its texture, the time (client ticks) for blinking. */
    private record Frame(WarRoomBoard board, Identifier texture, long ticks) {}

    private static final DataTicket<Frame> FRAME = DataTicket.create("groundburst_war_room_frame", Frame.class);

    public WarRoomRenderer(BlockEntityRendererProvider.Context context) {
        super(context, GBCommand.WAR_ROOM_ENTITY.get(), 5, 4);
    }

    @Override
    public void addRenderData(WarRoomDisplayBlockEntity be, @Nullable Void relatedObject, BlockEntityRenderState renderState, float partialTick) {
        super.addRenderData(be, relatedObject, renderState, partialTick);
        if (!be.isFormed() || !be.shownPowered() || be.getLevel() == null) {
            return;
        }
        WarRoomBoard board = WarRoomBoard.of(be.getBlockPos());
        if (board == null || board.board() == null || !board.board().powered()) {
            return;
        }
        long ticks = be.getLevel().getGameTime();
        ((GeoRenderState) renderState).addGeckolibData(FRAME, new Frame(board, board.texture(ticks), ticks));
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void postRenderPass(RenderPassInfo renderPassInfo, SubmitNodeCollector renderTasks) {
        super.postRenderPass(renderPassInfo, renderTasks);
        Frame frame = (Frame) renderPassInfo.getGeckolibData(FRAME);
        if (frame == null) {
            return;
        }
        WarRoomPayloads.Board b = frame.board().board();
        if (b == null) {
            return;
        }
        PoseStack pose = renderPassInfo.poseStack();
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        tryRotateByBlockstate(renderPassInfo, pose);
        // Turned to face the model's front (-z), at the glass's top left, one unit a picture texel, y down.
        pose.mulPose(Axis.YP.rotationDegrees(180));
        pose.translate(LEFT / 16f, TOP / 16f, FACE / 16f);
        pose.scale(TEXEL, -TEXEL, TEXEL);
        float w = WarRoomBoard.W, h = WarRoomBoard.H;
        renderTasks.submitCustomGeometry(pose, RenderTypes.text(frame.texture()), (p, buffer) -> {
            buffer.addVertex(p, 0, 0, 0).setColor(-1).setUv(0, 0).setLight(LIT);
            buffer.addVertex(p, 0, h, 0).setColor(-1).setUv(0, 1).setLight(LIT);
            buffer.addVertex(p, w, h, 0).setColor(-1).setUv(1, 1).setLight(LIT);
            buffer.addVertex(p, w, 0, 0).setColor(-1).setUv(1, 0).setLight(LIT);
        });
        text(renderTasks, pose, frame, b);
        pose.popPose();
    }

    private void text(SubmitNodeCollector out, PoseStack pose, Frame frame, WarRoomPayloads.Board b) {
        Font font = Minecraft.getInstance().font;
        WarRoomBoard board = frame.board();
        boolean blink = frame.ticks() % 20 < 12;
        if (b.code().isEmpty()) {
            Component none = Component.translatable(T_NO_NETWORK);
            line(out, font, pose, none, WarRoomBoard.W / 2f - font.width(none) / 2f, WarRoomBoard.H / 2f - 4, AMBER, 1f);
            return;
        }
        // The header: the code and the counts, the alert condition and the soonest impact.
        int linked = 0, armed = 0, inFlight = 0, incoming = 0, soonest = -1;
        for (StationPayloads.MemberView m : b.members()) {
            if (m.launcher()) {
                linked++;
                armed += (m.willBeArmed() || m.standingBy()) && !m.lost() ? 1 : 0;
            }
        }
        for (StationPayloads.ContactView c : b.contacts()) {
            if (c.own()) {
                inFlight++;
            } else {
                incoming++;
                soonest = soonest < 0 ? c.eta() : Math.min(soonest, c.eta());
            }
        }
        line(out, font, pose, Component.translatable(T_NET, b.code()), 4, 3, AMBER, 1.25f);
        line(out, font, pose, Component.translatable(T_COUNTS, linked, armed, inFlight, incoming), 4, 16, GREEN, 0.85f);
        int c = Math.clamp(b.condition(), 1, 5);
        Component cond = Component.translatable(T_CONDITION[c - 1]);
        int condColour = c <= 2 && !blink ? 0xFF7A1A10 : CONDITION_COLOUR[c - 1];
        line(out, font, pose, cond, WarRoomBoard.W - 4 - font.width(cond) * 1.1f, 3, condColour, 1.1f);
        if (soonest >= 0) {
            int s = Math.max(0, (soonest + 19) / 20);
            Component impact = Component.translatable(T_IMPACT, s / 60 + ":" + String.format(Locale.ROOT, "%02d", s % 60));
            line(out, font, pose, impact, WarRoomBoard.W - 4 - font.width(impact) * 1.25f, 15, blink ? RED : 0xFF8A1A10, 1.25f);
        }
        // Labels: each member's number, the incoming missiles' times to impact, the fixed blasts' magnitudes.
        if ((b.layers() & WarRoomDisplayBlockEntity.LABELS) != 0) {
            List<StationPayloads.MemberView> ms = b.members();
            for (int i = 0; i < ms.size(); i++) {
                StationPayloads.MemberView m = ms.get(i);
                float x = board.px(m.at().getX() + 0.5) + 4, y = board.py(m.at().getZ() + 0.5) - 7;
                if (inside(x, y)) {
                    line(out, font, pose, Component.literal(Integer.toString(i + 1)), x, y, DIM_GREEN, 0.6f);
                }
            }
            for (StationPayloads.ContactView ct : b.contacts()) {
                if (ct.own()) {
                    continue;
                }
                int s = Math.max(0, (ct.eta() + 19) / 20);
                float x = board.px(ct.x() + 0.5) + 4, y = board.py(ct.z() + 0.5) + 2;
                if (inside(x, y)) {
                    line(out, font, pose, Component.literal(s / 60 + ":" + String.format(Locale.ROOT, "%02d", s % 60)), x, y, RED, 0.6f);
                }
            }
            if ((b.layers() & WarRoomDisplayBlockEntity.BLASTS) != 0) {
                for (StationPayloads.BlastView bl : b.blasts()) {
                    if (!bl.fixed()) {
                        continue;
                    }
                    float x = board.px(bl.x() + 0.5) + 5, y = board.py(bl.z() + 0.5) - 3;
                    if (inside(x, y)) {
                        line(out, font, pose, Component.literal(String.format(Locale.ROOT, "M%.1f", bl.magnitude())), x, y, AMBER, 0.6f);
                    }
                }
            }
        }
        // The foot: the scale, and the last lines of the log.
        Component scale = Component.translatable(T_SCALE, board.zoom());
        line(out, font, pose, scale, WarRoomBoard.W - 4 - font.width(scale) * 0.7f, WarRoomBoard.H - 9, DIM_GREEN, 0.7f);
        if ((b.layers() & WarRoomDisplayBlockEntity.LOG) != 0) {
            List<LaunchNetwork.Event> log = b.log();
            int lines = Math.min(3, log.size());
            for (int i = 0; i < lines; i++) {
                LaunchNetwork.Event e = log.get(log.size() - lines + i);
                int col = switch (e.kind()) {
                    case LAUNCHED, BLAST, DEAD_HAND_ARMED -> AMBER;
                    case LOST, NO_CONTACT, FAILED, DEAD_HAND_ALERT, DEAD_HAND_FIRED -> RED;
                    default -> WHITE;
                };
                String text = font.plainSubstrByWidth(LaunchStationScreen.describe(e, b.members()).getString(), (int) ((WarRoomBoard.W - 60) / 0.7f));
                line(out, font, pose, Component.literal(text), 4, WarRoomBoard.H - 9 - (lines - 1 - i) * 7, col, 0.7f);
            }
        }
    }

    private static boolean inside(float x, float y) {
        return x >= 2 && y >= 24 && x < WarRoomBoard.W - 30 && y < WarRoomBoard.H - 26;
    }

    private static void line(SubmitNodeCollector out, Font font, PoseStack pose, Component text, float x, float y, int colour, float size) {
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(size, size, 1);
        out.submitText(pose, 0, 0, text.getVisualOrderText(), false, Font.DisplayMode.POLYGON_OFFSET, LIT, colour, 0, 0);
        pose.popPose();
    }
}
