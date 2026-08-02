package indi.etern.musichud.utils;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.platform.Environment;

import java.util.function.Supplier;

/**
 * To avoid loading client classes in server environment, which may causing class load exceptions.
 * */
public interface IClientDistUtil {
    static IClientDistUtil getInstance() {
        Environment currentEnvironment = MusicHud.getCurrentEnvironment();
        if (currentEnvironment.getSide() == Environment.Side.CLIENT) {
            Environment.Platform platform = currentEnvironment.getPlatform();
            Supplier<IClientDistUtil> supplier = platform.getClientDistUtilSupplier();
            if (supplier != null) {
                IClientDistUtil iClientDistUtil1 = supplier.get();
                if (iClientDistUtil1 instanceof IClientDistUtil iClientDistUtil) {
                    return iClientDistUtil;
                }
            }
        }
        throw new UnsupportedOperationException();
    }

    boolean isLocalPlayer(Object player);

    String getI18n(String key, Object... objects);

    void showToast(CharSequence message);

    void refreshMainGUI();

    boolean inIntegratedServer();
}
