package indi.etern.musichud.client.utils;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.connection.ConnectionStateMachine;
import indi.etern.musichud.interfaces.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class PlayerInfoUtil {
    private static final ClientConfig clientConfig = ClientConfig.getInstance();

    public static PlayerInfo getPlayerInfoByUUID(UUID uuid) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientPacketListener connection = minecraft.getConnection();
        if (connection == null) {
            // Not in a world (main menu / disconnect transition): callers handle a null
            // result with their own fallback instead of crashing render/UI threads
            return null;
        }
        return connection.getPlayerInfo(uuid);
    }

    public static ResourceLocation getPlayerSkin(PlayerInfo playerInfo) {
        if (playerInfo == null) {
            if (Minecraft.getInstance().getCurrentServer() == null || //single player
                    ConnectionStateMachine.getConnectStatus() != MusicHud.ConnectStatus.CONNECTED && clientConfig.getEnableIsolatedMode()) {// isolated mode
                LocalPlayer player = Minecraft.getInstance().player;
                if (player != null) {
                    return player.getSkin().body().texturePath();
                }
            }
        } else {
            return playerInfo.getSkin().body().texturePath();
        }
        return null;
    }

    /**
     * Version-neutral skin resource path ({@code "namespace:path"} string) so the HUD layer
     * does not depend on the {@code ResourceLocation}/{@code Identifier} class name, which
     * changes across Minecraft versions.
     */
    public static @Nullable String getPlayerSkinPath(PlayerInfo playerInfo) {
        ResourceLocation location = getPlayerSkin(playerInfo);
        return location == null ? null : location.toString();
    }

    public static UUID getSelfUUID() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? null : player.getUUID();
    }
}
