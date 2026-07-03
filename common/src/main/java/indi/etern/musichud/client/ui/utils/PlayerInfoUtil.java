package indi.etern.musichud.client.ui.utils;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.interfaces.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public class PlayerInfoUtil {
    private static final ClientConfig clientConfig = ClientConfig.getInstance();

    public static PlayerInfo getPlayerInfoByUUID(UUID uuid) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientPacketListener connection = minecraft.getConnection();
        if (connection == null) {
            throw new IllegalStateException();
        }
        return connection.getPlayerInfo(uuid);
    }

    public static ResourceLocation getPlayerSkin(PlayerInfo playerInfo) {
        if (playerInfo == null) {
            if (Minecraft.getInstance().getCurrentServer() == null || //single player
                    MusicHud.getConnectStatus() != MusicHud.ConnectStatus.CONNECTED && clientConfig.getEnableIsolatedMode()) {// isolated mode
                LocalPlayer player = Minecraft.getInstance().player;
                if (player != null) {
                    return player.getSkin().texture();
                }
            }
        } else {
            return playerInfo.getSkin().texture();
        }
        return null;
    }
}
