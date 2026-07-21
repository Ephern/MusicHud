package indi.etern.musichud.utils;

import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.Album;
import indi.etern.musichud.beans.music.Artist;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.beans.music.Playlist;
import indi.etern.musichud.platform.Environment;

import java.util.List;
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

    void setSearchViewAlbumsResult(int offset, List<Album> result);

    void setSearchViewArtistsResult(int offset, List<Artist> result);

    void setSearchViewMusicsResult(int offset, List<MusicDetail> result);

    void setSearchViewPlaylistsResult(int offset, List<Playlist> result);
}
