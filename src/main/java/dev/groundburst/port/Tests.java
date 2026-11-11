package dev.groundburst.port;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.gametest.framework.GameTestHelper;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;

/** What main's GameTests ask of 26.x's test helper that 1.21.1's lacks (port.py turns the calls into these). */
public final class Tests {
    private Tests() {}

    /**
     * 26.x's {@code makeMockServerPlayer(GameType)}: a server player in the test's level, in that game mode, and, as
     * there, in no one's player list, on a connection that sends nothing (Forge 1.20.1 hands a server player an item through a
     * wrapper that tells its client; 1.21.1's own mock server player joins the server's list on a
     * connection that cannot take the mod's packets: the first one sent to every player failed).
     */
    public static Player mockServerPlayer(GameTestHelper helper, GameType mode) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "test-mock-player")) {
            @Override
            public boolean isSpectator() {
                return mode == GameType.SPECTATOR;
            }

            @Override
            public boolean isCreative() {
                return mode == GameType.CREATIVE;
            }
        };
        mode.updatePlayerAbilities(player.getAbilities());
        net.minecraft.network.Connection nowhere = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND) {
            @Override
            public void send(net.minecraft.network.protocol.Packet<?> packet) {}

            @Override
            public void send(net.minecraft.network.protocol.Packet<?> packet, net.minecraft.network.PacketSendListener listener) {}
        };
        new net.minecraft.server.network.ServerGamePacketListenerImpl(level.getServer(), nowhere, player);   // (it sets player.connection)
        return player;
    }
}
