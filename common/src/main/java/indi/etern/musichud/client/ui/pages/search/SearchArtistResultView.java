package indi.etern.musichud.client.ui.pages.search;

import icyllis.modernui.core.Context;
import indi.etern.musichud.beans.music.Artist;
import indi.etern.musichud.client.ui.components.ArtistCard;
import indi.etern.musichud.client.ui.components.AutoFlowGridLayout;
import lombok.Getter;

import java.util.List;

public class SearchArtistResultView extends AutoFlowGridLayout {
    @Getter
    private static SearchArtistResultView instance;
    private static List<Artist> result;

    public SearchArtistResultView(Context context) {
        super(context);
        setRowMinWidth(dp(120));
        instance = this;
        refresh();
    }

    public static void setResult(List<Artist> result) {
        SearchArtistResultView.result = result;
        if (instance != null) {
            instance.refresh();
        }
    }

    public void refresh() {
        removeAllViews();
        if (result != null) {
            for (Artist artist : result) {
                addItem(getContext(), artist);
            }
        }
    }

    public void append(List<Artist> artists) {
        result.addAll(artists);
        for (Artist artist : artists) {
            addItem(getContext(), artist);
        }
    }

    private void addItem(Context context, Artist artist) {
        var artistListItem = new ArtistCard(context);
        artistListItem.bindData(artist);
        addView(artistListItem);
    }
}
