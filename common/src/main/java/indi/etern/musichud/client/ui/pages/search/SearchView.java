package indi.etern.musichud.client.ui.pages.search;

import icyllis.modernui.R;
import icyllis.modernui.animation.LayoutTransition;
import icyllis.modernui.core.Context;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.text.SpannableString;
import icyllis.modernui.text.Spanned;
import icyllis.modernui.text.style.ImageSpan;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.api.SearchType;
import indi.etern.musichud.beans.music.Album;
import indi.etern.musichud.beans.music.Artist;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.beans.music.Playlist;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.components.SegmentedBar;
import indi.etern.musichud.client.ui.components.Tooltip;
import indi.etern.musichud.client.utils.image.ImageUtils;
import indi.etern.musichud.client.utils.ui.InsetBackgroundFactory;
import indi.etern.musichud.client.utils.ui.PanelStyle;
import indi.etern.musichud.connection.ConnectionStateMachine;
import indi.etern.musichud.interfaces.ClientConfig;
import indi.etern.musichud.network.RequestResponseManager;
import indi.etern.musichud.network.payloads.requestResponseCycle.SearchRequest;
import indi.etern.musichud.network.payloads.requestResponseCycle.SearchResultResponse;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import net.minecraft.client.resources.language.I18n;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class SearchView extends FrameLayout {
    private static final int BAR_TOP_MARGIN = 12;
    @Getter
    private static SearchView instance = null;
    @Getter
    private final Map<SearchType, SearchMeta> searchMetas = new HashMap<>();
    @Getter
    private final HashSet<Consumer<SearchMeta>> searchRefreshListeners = new HashSet<>();
    private static final ClientConfig clientConfig = ClientConfig.getInstance();
    private LinearLayout topBar;
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
        topBar = null;
        searchResultTabPage = null;

        boolean enabled = clientConfig.getEnable();
        if (ConnectionStateMachine.getConnectStatus() != ConnectionStateMachine.ConnectStatus.CONNECTED && !ClientConfig.getInstance().getEnableIsolatedMode() || !enabled) {
            TextView textView = Theme.getNotificationTextView(context, enabled);
            addView(textView, new LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER));
            return;
        }

        searchResultTabPage = new SearchResultTabPage(context);
        addView(searchResultTabPage, new LayoutParams(MATCH_PARENT, MATCH_PARENT));

        topBar = new LinearLayout(context);
        topBar.setGravity(Gravity.CENTER);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setClickable(true);

        LinearLayout searchWidget = new LinearLayout(context);
        searchWidget.setOrientation(LinearLayout.HORIZONTAL);
        topBar.addView(searchWidget, new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));

        SegmentedBar tabBar = new SegmentedBar(context);
        int dp2 = dp(2);
        {
            tabBar.addItem(new SegmentedBar.Item("music", null,
                    "/assets/music_hud/textures/gui/icons/eighth_note.png",
                    I18n.get(MusicHud.MOD_ID + ".text.page.search.music")));
            tabBar.addItem(new SegmentedBar.Item("playlist", null,
                    "/assets/music_hud/textures/gui/icons/list_music.png",
                    I18n.get(MusicHud.MOD_ID + ".text.page.search.playlist")));
            tabBar.addItem(new SegmentedBar.Item("album", null,
                    "/assets/music_hud/textures/gui/icons/disc_album.png",
                    I18n.get(MusicHud.MOD_ID + ".text.page.search.album")));
            tabBar.addItem(new SegmentedBar.Item("artist", null,
                    "/assets/music_hud/textures/gui/icons/user.png",
                    I18n.get(MusicHud.MOD_ID + ".text.page.search.artist")));
            PanelStyle.applyDynamically(tabBar, dp(16),
                    new InsetBackgroundFactory.Padding(dp2, dp2, dp2, dp2),
                    InsetBackgroundFactory.BackdropBlur.UI_LAYER);
            LinearLayout.LayoutParams params2 = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            params2.setMargins(0, 0, dp(8), 0);
            searchWidget.addView(tabBar, params2);
        }
        {
            LinearLayout searchBar = new LinearLayout(context);
            PanelStyle.applyDynamically(searchBar, dp(16),
                    new InsetBackgroundFactory.Padding(dp2, dp2, dp2, dp2),
                    InsetBackgroundFactory.BackdropBlur.UI_LAYER);
            searchWidget.addView(searchBar, new LinearLayout.LayoutParams(WRAP_CONTENT, MATCH_PARENT));
            LayoutTransition transition = new LayoutTransition();
            transition.enableTransitionType(LayoutTransition.CHANGING);
            searchWidget.setLayoutTransition(transition);
            {
                searchTextInput = new EditText(context, null, R.attr.editTextStyle);
                searchTextInput.setTextAlignment(SearchView.TEXT_ALIGNMENT_TEXT_START);
                searchTextInput.setHint(I18n.get(MusicHud.MOD_ID + ".field.hint.searchMusic"));
                searchTextInput.setSingleLine();
                searchTextInput.setTextSize(Theme.TEXT_SIZE_LARGE);
                searchTextInput.setMinWidth(dp(240));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(WRAP_CONTENT, MATCH_PARENT);
                searchBar.addView(searchTextInput, params);

                Button searchButton = new Button(context);
                searchButton.setTextSize(Theme.TEXT_SIZE_LARGE);
                ImageSpan iconSpan = ImageUtils.getIconSpan(ImageUtils.getImageFromResource("/assets/music_hud/textures/gui/icons/search.png"));
                SpannableString text = new SpannableString(" ");
                text.setSpan(iconSpan, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                searchButton.setText(text);

                Tooltip.attach(searchButton, I18n.get(MusicHud.MOD_ID + ".button.searchMusic"));
                InsetBackgroundFactory.builder()
                        .inset(dp(1))
                        .cornerRadius(dp(14))
                        .build()
                        .applyBackgroundTo(searchButton);
                searchBar.addView(searchButton, new LinearLayout.LayoutParams(dp(40), dp(40)));
                searchButton.setOnClickListener((v) -> refreshSearch(true));
            }
        }

        searchResultTabPage.bindBar(tabBar);

        FrameLayout.LayoutParams topBarParams = new FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        topBarParams.gravity = Gravity.TOP;
        topBarParams.setMargins(0, dp(BAR_TOP_MARGIN), 0, 0);
        // Added last: its backdrop blur samples the content recorded underneath.
        addView(topBar, topBarParams);

        searchTextInput.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEY_ENTER && event.getAction() == KeyEvent.ACTION_DOWN) {
                refreshSearch(true);
                return true;
            }
            return false;
        });

        addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                instance = null;
                searchMetas.clear();
            }
        });
    }

    /**
     * The results start below the floating bar, whose height is only known after measure.
     */
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (topBar != null && searchResultTabPage != null) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) topBar.getLayoutParams();
            searchResultTabPage.setContentTopInset(lp.topMargin + topBar.getMeasuredHeight());
        }
        super.onLayout(changed, left, top, right, bottom);
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
            sendSearchRequest(searchText, searchType, 0);
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
            searchMeta.pendingFuture = new CompletableFuture<>();
            searchRefreshListeners.forEach(listener -> listener.accept(searchMeta));
            sendSearchRequest(text, searchType, offset);
        }
    }

    private void sendSearchRequest(String text, SearchType searchType, int offset) {
        RequestResponseManager.send(
                        new SearchRequest(text, searchType, offset),
                        SearchResultResponse.class,
                        Duration.ofSeconds(10))
                .thenAccept(response -> MuiModApi.postToUiThread(() -> handleSearchResult(response)))
                .exceptionally(e -> {
                    MusicHud.getLogger(SearchView.class).warn("Failed to search {}: {}", searchType, text, e);
                    return null;
                });
    }

    @SuppressWarnings("unchecked")
    private void handleSearchResult(SearchResultResponse response) {
        switch (response.getSearchType()) {
            case MUSIC -> setSearchMusicResult(response.getOffset(), (List<MusicDetail>) response.getResult());
            case PLAYLIST -> setSearchPlaylistResult(response.getOffset(), (List<Playlist>) response.getResult());
            case ALBUM -> setSearchAlbumResult(response.getOffset(), (List<Album>) response.getResult());
            case ARTIST -> setSearchArtistResult(response.getOffset(), (List<Artist>) response.getResult());
            default -> {
            }
        }
    }

    private void refreshSearchMeta(int offset, List<?> result, SearchType searchType) {
        boolean mayHasMore = result.size() == 50;// see also: IMusicApiService.searchXXX(...);
        SearchMeta searchMeta = searchMetas.getOrDefault(searchType, new SearchMeta(searchType, searchText));
        searchMeta.nextOffset = offset + result.size();
        searchMeta.mayHasMore = mayHasMore;
        CompletableFuture<CompletingType> pendingFuture = searchMeta.pendingFuture;
        searchMeta.pendingFuture = null;
        searchMetas.put(searchType, searchMeta);
        if (pendingFuture != null) {
            pendingFuture.complete(mayHasMore ? CompletingType.NORMAL : CompletingType.NO_MORE_RESULT);
        }
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

    public void setSearchAlbumResult(int offset, List<Album> result) {
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