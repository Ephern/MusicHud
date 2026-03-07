package indi.etern.musichud.client.ui.pages;

import icyllis.modernui.core.Context;
import icyllis.modernui.widget.LinearLayout;
import indi.etern.musichud.beans.music.Artist;
import indi.etern.musichud.client.ui.components.ArtistDetailView;
import indi.etern.musichud.client.ui.components.ArtistListItem;
import indi.etern.musichud.client.ui.components.RouterContainer;
import indi.etern.musichud.client.ui.utils.ButtonInsetBackground;
import lombok.Getter;

import java.util.List;

public class SearchArtistResultView extends LinearLayout {
    @Getter
    private static SearchArtistResultView instance;
    private static List<Artist> result;

    public SearchArtistResultView(Context context) {
        super(context);
        instance = this;
        setOrientation(LinearLayout.VERTICAL);
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
        var artistListItem = new ArtistListItem(context);
        artistListItem.bindData(artist);
        var background = ButtonInsetBackground.builder()
                .cornerRadius(dp(12))
                .inset(dp(1))
                .padding(new ButtonInsetBackground.Padding(dp(4), dp(4), dp(4), dp(4))).build().get();
        artistListItem.setBackground(background);

        artistListItem.setClickable(true);
        artistListItem.setOnClickListener((view) -> {
            RouterContainer routerContainer = RouterContainer.getInstance();
            if (routerContainer != null) {
                routerContainer.pushNavigate(
                        new ArtistDetailView(context, artist)
                );
            }
        });
        addView(artistListItem, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }
}
