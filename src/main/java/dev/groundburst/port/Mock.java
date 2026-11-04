package dev.groundburst.port;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/** The GameTests' stand-in player (26.3's {@code GameTestHelper.makeMockServerPlayer}, which 26.1.2 lacks). */
public final class Mock {
    private Mock() {}

    /** A server player in the test's level, not in the player list, playing in {@code gameType}. */
    public static ServerPlayer serverPlayer(GameTestHelper helper, GameType gameType) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "test-mock-player"),
                ClientInformation.createDefault()) {
            @Override
            public GameType gameMode() {
                return gameType;
            }

            @Override
            public boolean isClientAuthoritative() {
                return false;
            }
        };
        gameType.updatePlayerAbilities(player.getAbilities());
        return player;
    }
}
