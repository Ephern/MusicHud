package indi.etern.musichud.client.ui.pages;

import icyllis.modernui.core.Context;
import indi.etern.musichud.beans.music.AlbumInfo;
import indi.etern.musichud.client.ui.components.FlexWrapLayout;
import indi.etern.musichud.client.ui.components.MusicCollectionCard;
import lombok.Getter;

import java.util.List;

public class SearchAlbumResultView extends FlexWrapLayout {
    @Getter
    private static SearchAlbumResultView instance;
    private static List<AlbumInfo> result;

    public SearchAlbumResultView(Context context) {
        super(context);
        instance = this;
        refresh();
    }

    public static void setResult(List<AlbumInfo> result) {
        SearchAlbumResultView.result = result;
        if (instance != null) {
            instance.refresh();
        }
    }

    public void refresh() {
        clearFlexChildren();
        setItemSpacing(0);
        setLineSpacing(0);
        if (result != null) {
            for (AlbumInfo playlist : result) {
                addItem(getContext(), playlist);
            }
        }
    }

    public void append(List<AlbumInfo> albumInfos) {
        result.addAll(albumInfos);
        for (AlbumInfo albumInfo : albumInfos) {
            addItem(getContext(), albumInfo);
        }
    }

    private void addItem(Context context, AlbumInfo albumInfo) {
        MusicCollectionCard child = new MusicCollectionCard(context, albumInfo);
        addView(child);
    }
}
