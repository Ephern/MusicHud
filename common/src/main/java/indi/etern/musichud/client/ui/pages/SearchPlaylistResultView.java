package indi.etern.musichud.client.ui.pages;

import icyllis.modernui.core.Context;
import indi.etern.musichud.beans.music.Playlist;
import indi.etern.musichud.client.ui.components.FlexWrapLayout;
import indi.etern.musichud.client.ui.components.MusicCollectionCard;
import lombok.Getter;

import java.util.List;

public class SearchPlaylistResultView extends FlexWrapLayout {
    @Getter
    private static SearchPlaylistResultView instance;
    private static List<Playlist> result;

    public SearchPlaylistResultView(Context context) {
        super(context);
        instance = this;
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
        setItemSpacing(0);
        setLineSpacing(0);
        if (result != null) {
            for (Playlist playlist : result) {
                addItem(getContext(), playlist);
            }
        }
    }

    public void append(List<Playlist> playlists) {
        result.addAll(playlists);
        for (Playlist playlist : playlists) {
            addItem(getContext(), playlist);
        }
    }

    private void addItem(Context context, Playlist playlist) {
        MusicCollectionCard child = new MusicCollectionCard(context, playlist);
        addView(child);
    }
}