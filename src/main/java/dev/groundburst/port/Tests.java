package dev.groundburst.port;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;

/** What main's GameTests ask of 26.x's test helper that 1.21.1's lacks (port.py turns the calls into these). */
public final class Tests {
    private Tests() {}

    /**
     * 26.x's {@code makeMockServerPlayer(GameType)}: a server player in the test's level, in that game mode, and, as
     * there, in no one's player list and on no connection (1.21.1's own mock server player joins the server's list on a
     * connection that cannot take the mod's packets: the first one sent to every player failed).
     */
    public static Player mockServerPlayer(GameTestHelper helper, GameType mode) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "test-mock-player"),
                ClientInformation.createDefault()) {
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
        return player;
    }
}
