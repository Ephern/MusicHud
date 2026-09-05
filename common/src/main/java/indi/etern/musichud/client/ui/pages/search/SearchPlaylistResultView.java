package indi.etern.musichud.client.ui.pages.search;

import icyllis.modernui.core.Context;
import indi.etern.musichud.beans.music.Playlist;
import indi.etern.musichud.beans.music.PusherInfo;
import indi.etern.musichud.client.ui.components.MusicCollectionCard;
import indi.etern.musichud.client.ui.components.WaterfallLayout;
import lombok.Getter;

import java.util.List;

public class SearchPlaylistResultView extends WaterfallLayout {
    @Getter
    private static SearchPlaylistResultView instance;
    private static List<Playlist> result;

    public SearchPlaylistResultView(Context context) {
        super(context);
        instance = this;
        setRowMinWidth(dp(174));
        refresh();
    }

    public static void setResult(List<Playlist> result) {
        SearchPlaylistResultView.result = result;
        if (instance != null) {
            instance.refresh();
        }
    }

    public void refresh() {
        removeAllViews();
        if (result != null) {
            for (Playlist playlist : result) {
                addItem(getContext(), playlist);
            }
        }
    }

    public void append(List<Playlist> playlists) {
        if (playlists == null || playlists.isEmpty()) {
            return;
        }
        result.addAll(playlists);
        for (Playlist playlist : playlists) {
            addItem(getContext(), playlist);
        }
    }

    private void addItem(Context context, Playlist playlist) {
        MusicCollectionCard child = new MusicCollectionCard(context, playlist, PusherInfo.EMPTY);
        addView(child);
    }
}