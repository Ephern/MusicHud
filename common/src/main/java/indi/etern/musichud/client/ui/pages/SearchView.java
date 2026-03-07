package indi.etern.musichud.client.ui.pages;

import dev.architectury.networking.NetworkManager;
import icyllis.modernui.R;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.Drawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.api.SearchType;
import indi.etern.musichud.beans.music.AlbumInfo;
import indi.etern.musichud.beans.music.Artist;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.beans.music.Playlist;
import indi.etern.musichud.client.config.ClientConfigDefinition;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.utils.ButtonInsetBackground;
import indi.etern.musichud.network.requestResponseCycle.SearchRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import net.minecraft.client.resources.language.I18n;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class SearchView extends LinearLayout {
    @Getter
    private static SearchView instance = null;
    @Getter
    private final Map<SearchType, SearchMeta> searchMetas = new HashMap<>();
    @Getter
    private final HashSet<Consumer<SearchMeta>> searchRefreshListeners = new HashSet<>();
    private EditText searchTextInput;
    private SearchResultTabPage searchResultTabPage;
    @Getter
    private String searchText;

    public SearchView(Context context) {
        super(context);
        instance = this;
        refresh();
    }

    public void refresh() {
        Context context = getContext();
        removeAllViews();
        setOrientation(VERTICAL);

        boolean enabled = ClientConfigDefinition.enable.get();
        if (MusicHud.getStatus() != MusicHud.ConnectStatus.CONNECTED || !enabled) {
            setGravity(Gravity.CENTER);
            TextView textView = Theme.getNotificationTextView(context, enabled);
            addView(textView);
            return;
        }

        LinearLayout top = new LinearLayout(context);
        top.setOrientation(HORIZONTAL);
        LayoutParams topParams = new LayoutParams(MATCH_PARENT, dp(38));
        topParams.setMargins(0, dp(16), 0, 0);
        addView(top, topParams);

        top.addView(new View(context), new LayoutParams(0, WRAP_CONTENT, 2));
        searchTextInput = new EditText(context, null, R.attr.editTextOutlinedStyle);
        searchTextInput.setTextAlignment(SearchView.TEXT_ALIGNMENT_CENTER);
        searchTextInput.setHint(I18n.get("music_hud.field.hint.searchMusic"));
        searchTextInput.setSingleLine();
        LayoutParams params = new LayoutParams(0, WRAP_CONTENT, 6);
        params.setMargins(dp(52), 0, 0, 0);
        top.addView(searchTextInput, params);

        Button searchButton = new Button(context);
        searchButton.setText(I18n.get("music_hud.button.searchMusic"));
        LayoutParams buttonParams = new LayoutParams(WRAP_CONTENT, MATCH_PARENT);
        Drawable background = ButtonInsetBackground.builder()
                .inset(0).padding(new ButtonInsetBackground.Padding(dp(8), 0, dp(8), 0))
                .cornerRadius(dp(4)).build().get();
        searchButton.setBackground(background);
        buttonParams.setMargins(dp(8), 0, 0, 0);
        top.addView(searchButton, buttonParams);

        top.addView(new View(context), new LayoutParams(0, WRAP_CONTENT, 2));

        searchResultTabPage = new SearchResultTabPage(context);

        LayoutParams resultAreaParams = new LayoutParams(MATCH_PARENT, 0, 1);
        resultAreaParams.setMargins(dp(32), 0, dp(32), 0);
        addView(searchResultTabPage, resultAreaParams);

        searchTextInput.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEY_ENTER && event.getAction() == KeyEvent.ACTION_DOWN) {
                refreshSearch(true);
                return true;
            }
            return false;
        });
        searchButton.setOnClickListener((v) -> refreshSearch(true));

        addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                instance = null;
            }
        });
    }

    public void refreshSearch(boolean force) {
        searchText = searchTextInput.getText().toString();
        if (searchText == null || searchText.isEmpty()) return;
        int currentItem = searchResultTabPage.getPager().getCurrentItem();
        SearchType[] searchTypes = {SearchType.MUSIC, SearchType.PLAYLIST, SearchType.ALBUM, SearchType.ARTIST};
        SearchType searchType = searchTypes[currentItem];
        SearchMeta searchMeta = searchMetas.get(searchType);
        if (force || searchMeta == null || !searchMeta.text.equals(searchText)) {
            if (searchMeta != null && searchMeta.pendingFuture != null) {
                searchMeta.pendingFuture.cancel(true);
            }
            SearchMeta searchMeta1 = new SearchMeta(searchType, searchText);
            searchMeta1.pendingFuture = new CompletableFuture<>();
            searchMetas.put(searchType, searchMeta1);
            searchRefreshListeners.forEach(listener -> listener.accept(searchMeta1));
            NetworkManager.sendToServer(new SearchRequest(searchText, searchType, 0));
        }
    }

    public void loadMoreSearchResult() {
        int currentItem = searchResultTabPage.getPager().getCurrentItem();
        SearchType[] searchTypes = {SearchType.MUSIC, SearchType.PLAYLIST, SearchType.ALBUM, SearchType.ARTIST};
        SearchType searchType = searchTypes[currentItem];
        SearchMeta searchMeta = searchMetas.get(searchType);
        String text = searchTextInput.getText().toString();
        if (searchMeta != null && searchMeta.pendingFuture == null && searchMeta.mayHasMore) {
            int offset = searchMeta.nextOffset;
            CompletableFuture<CompletingType> completableFuture = new CompletableFuture<>();
            searchMeta.pendingFuture = completableFuture;
            searchRefreshListeners.forEach(listener -> listener.accept(searchMeta));
            NetworkManager.sendToServer(new SearchRequest(text, searchType, offset));
        }
    }

    private void refreshSearchMeta(int offset, List<?> result, SearchType searchType) {
        boolean mayHasMore = !result.isEmpty();
        SearchMeta searchMeta = searchMetas.getOrDefault(searchType, new SearchMeta(searchType, searchText));
        searchMeta.nextOffset = offset + result.size();
        searchMeta.mayHasMore = mayHasMore;
        CompletableFuture<CompletingType> pendingFuture = searchMeta.pendingFuture;
        searchMeta.pendingFuture = null;
        searchMetas.put(searchType, searchMeta);
        pendingFuture.complete(mayHasMore ? CompletingType.NORMAL : CompletingType.NO_MORE_RESULT);
    }

    public void setSearchMusicResult(int offset, List<MusicDetail> result) {
        SearchType searchType = SearchType.MUSIC;
        refreshSearchMeta(offset, result, searchType);
        if (offset == 0) {
            SearchMusicResultView.setResult(result);
        } else {
            SearchMusicResultView instance = SearchMusicResultView.getInstance();
            if (instance != null) {
                instance.append(result);
            }
        }
    }

    public void setSearchPlaylistResult(int offset, List<Playlist> result) {
        SearchType searchType = SearchType.PLAYLIST;
        refreshSearchMeta(offset, result, searchType);
        if (offset == 0) {
            SearchPlaylistResultView.setResult(result);
        } else {
            SearchPlaylistResultView instance = SearchPlaylistResultView.getInstance();
            if (instance != null) {
                instance.append(result);
            }
        }
    }

    public void setSearchAlbumResult(int offset, List<AlbumInfo> result) {
        SearchType searchType = SearchType.ALBUM;
        refreshSearchMeta(offset, result, searchType);
        if (offset == 0) {
            SearchAlbumResultView.setResult(result);
        } else {
            SearchAlbumResultView instance = SearchAlbumResultView.getInstance();
            if (instance != null) {
                instance.append(result);
            }
        }
    }

    public void setSearchArtistResult(int offset, List<Artist> result) {
        SearchType searchType = SearchType.ARTIST;
        refreshSearchMeta(offset, result, searchType);
        if (offset == 0) {
            SearchArtistResultView.setResult(result);
        } else {
            SearchArtistResultView instance = SearchArtistResultView.getInstance();
            if (instance != null) {
                instance.append(result);
            }
        }
    }

    public enum CompletingType {
        NORMAL, NO_MORE_RESULT
    }

    @Data
    @EqualsAndHashCode
    @ToString
    @Getter
    public static final class SearchMeta {
        private final SearchType searchType;
        private final String text;
        CompletableFuture<CompletingType> pendingFuture;
        private int nextOffset = 0;
        private boolean mayHasMore = true;

        private SearchMeta(SearchType searchType, String text) {
            this.searchType = searchType;
            this.text = text;
        }
    }
}