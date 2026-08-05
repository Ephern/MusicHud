package indi.etern.musichud.client.utils;

import icyllis.modernui.mc.MuiModApi;
import indi.etern.musichud.client.ui.ToastUtil;
import indi.etern.musichud.client.ui.screen.MainFragment;
import indi.etern.musichud.utils.IClientDistUtil;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.server.IntegratedServer;

/**
 * To avoid loading client classes in server environment, which may causing class load exceptions.
 * Before methods calling, using
 * MusicHud.getCurrentEnvironment().getSide() == Environment.Side.CLIENT
 * or other methods to ensure it is in client environment.
 * */
@SuppressWarnings("unused")
public class ClientDistUtil implements IClientDistUtil {
    @Getter
    private static final ClientDistUtil instance = new ClientDistUtil();

    @Override
    public boolean isLocalPlayer(Object player) {
        return player instanceof LocalPlayer;
    }

    @Override
    public String getI18n(String key, Object... objects) {
        return I18n.get(key, objects);
    }

    @Override
    public void showToast(CharSequence message) {
        ToastUtil.show(message);
    }

    @Override
    public void refreshMainGUI() {
        MuiModApi.postToUiThread(MainFragment::refresh);
    }

    @Override
    public boolean inIntegratedServer() {
        return Minecraft.getInstance().isLocalServer();
    }

    @Override
    public boolean inSinglePlayer() {
        IntegratedServer integratedServer = Minecraft.getInstance().getSingleplayerServer();
        return integratedServer != null && !integratedServer.isPublished();
    }
}