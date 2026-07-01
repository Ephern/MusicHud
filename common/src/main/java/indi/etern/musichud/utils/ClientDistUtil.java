package indi.etern.musichud.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.entity.player.Player;

/**
 * To avoid loading client classes in server environment, which may causing class load exceptions.
 * Before methods calling, using
 * MusicHud.getCurrentEnvironment().getSide() == Environment.Side.CLIENT
 * to ensure it is in client environment.
 * */
public class ClientDistUtil {
    public static boolean inIsolatedMode(Player player) {
        return Minecraft.getInstance().getCurrentServer() == null && player instanceof LocalPlayer;
    }

    public static String getI18n(String key, Object... objects) {
        return I18n.get(key, objects);
    }
}