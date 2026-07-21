package indi.etern.musichud.client.utils;

import icyllis.modernui.mc.MuiModApi;
import indi.etern.musichud.beans.music.Album;
import indi.etern.musichud.beans.music.Artist;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.beans.music.Playlist;
import indi.etern.musichud.client.ui.ToastUtil;
import indi.etern.musichud.client.ui.pages.search.SearchView;
import indi.etern.musichud.client.ui.screen.MainFragment;
import indi.etern.musichud.utils.IClientDistUtil;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;

import java.util.List;

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
        return Minecraft.getInstance().getCurrentServer() != null;
    }

    @Override
    public void setSearchViewAlbumsResult(int offset, List<Album> result) {
        MuiModApi.postToUiThread(() -> {
            SearchView.getInstance().setSearchAlbumResult(offset, result);
        });
    }

    @Override
    public void setSearchViewArtistsResult(int offset, List<Artist> result) {
        MuiModApi.postToUiThread(() -> {
            SearchView.getInstance().setSearchArtistResult(offset, result);
        });
    }

    @Override
    public void setSearchViewMusicsResult(int offset, List<MusicDetail> result) {
        MuiModApi.postToUiThread(() -> {
            SearchView.getInstance().setSearchMusicResult(offset, result);
        });
    }

    @Override
    public void setSearchViewPlaylistsResult(int offset, List<Playlist> result) {
        MuiModApi.postToUiThread(() -> {
            SearchView.getInstance().setSearchPlaylistResult(offset, result);
        });
    }
}