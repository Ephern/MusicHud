package indi.etern.musichud.client.ui.pages.search;

import icyllis.modernui.core.Context;
import indi.etern.musichud.beans.music.Album;
import indi.etern.musichud.client.ui.components.MusicCollectionCard;
import indi.etern.musichud.client.ui.components.WaterfallLayout;
import lombok.Getter;

import java.util.List;

public class SearchAlbumResultView extends WaterfallLayout {
    @Getter
    private static SearchAlbumResultView instance;
    private static List<Album> result;

    public SearchAlbumResultView(Context context) {
        super(context);
        setRowMinWidth(dp(174));
        instance = this;
        refresh();
    }

    public static void setResult(List<Album> result) {
        SearchAlbumResultView.result = result;
        if (instance != null) {
            instance.refresh();
        }
    }

    public void refresh() {
        removeAllViews();
        if (result != null) {
            for (Album playlist : result) {
                addItem(getContext(), playlist);
            }
        }
    }

    public void append(List<Album> albums) {
        result.addAll(albums);
        for (Album album : albums) {
            addItem(getContext(), album);
        }
    }

    private void addItem(Context context, Album album) {
        MusicCollectionCard child = new MusicCollectionCard(context, album);
        addView(child);
    }
}