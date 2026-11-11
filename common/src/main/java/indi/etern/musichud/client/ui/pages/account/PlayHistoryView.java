package indi.etern.musichud.client.ui.pages.account;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Image;
import icyllis.modernui.graphics.drawable.InsetDrawable;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.mc.ui.ClampingScrollView;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.*;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.Album;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.beans.music.Playlist;
import indi.etern.musichud.beans.music.PusherInfo;
import indi.etern.musichud.beans.record.PlayRecord;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.components.MusicListFactory;
import indi.etern.musichud.client.ui.components.MusicTrackItem;
import indi.etern.musichud.client.ui.components.RouterContainer;
import indi.etern.musichud.client.ui.components.SegmentedBar;
import indi.etern.musichud.client.ui.components.Tooltip;
import indi.etern.musichud.client.ui.components.cards.MusicCollectionCard;
import indi.etern.musichud.client.ui.drawable.ScaledImageDrawable;
import indi.etern.musichud.client.ui.layouts.WaterfallLayout;
import indi.etern.musichud.client.ui.screen.MainFragment;
import indi.etern.musichud.client.utils.image.ImageUtils;
import indi.etern.musichud.client.utils.ui.InsetBackgroundFactory;
import indi.etern.musichud.client.utils.ui.PanelStyle;
import indi.etern.musichud.network.RequestResponseManager;
import indi.etern.musichud.network.payloads.requestResponseCycle.*;
import lombok.NonNull;
import net.minecraft.client.resources.language.I18n;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

/**
 * Full-screen account play-history page with three tabs (songs / albums / playlists).
 * Opened from {@link AccountView} through {@link RouterContainer#pushNavigate(View)}.
 */
public class PlayHistoryView extends FrameLayout {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private final LinearLayout topBar;
    private ViewPager pager;

    public PlayHistoryView(Context context) {
        super(context);

        topBar = new LinearLayout(context);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setClickable(true);
        int dp2 = dp(2);
        PanelStyle.applyDynamically(topBar, dp(16),
                new InsetBackgroundFactory.Padding(dp2, dp2, dp2, dp2),
                InsetBackgroundFactory.BackdropBlur.UI_LAYER);

        ImageButton backButton = new ImageButton(context);
        Tooltip.attach(backButton, I18n.get(MusicHud.MOD_ID + ".button.back"));
        Image backIcon = ImageUtils.getImageFromResource("/assets/music_hud/textures/gui/icons/arrow_left.png");
        if (backIcon != null) {
            backButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            backButton.setImageDrawable(new ScaledImageDrawable(getContext().getResources(), backIcon, dp(16), dp(16)));
        }
        backButton.setOnClickListener(view -> {
            RouterContainer routerContainer = MainFragment.getInstance().getRouterContainer();
            if (routerContainer != null) {
                routerContainer.popNavigate();
            }
        });
        InsetBackgroundFactory buttonBackgroundFactory = InsetBackgroundFactory.builder()
                .inset(dp(1))
                .cornerRadius(dp(14))
                .build();
        buttonBackgroundFactory.applyBackgroundTo(backButton);
        topBar.addView(backButton, new LinearLayout.LayoutParams(dp(40), dp(40)));

        TextView title = new TextView(context);
        title.setTextSize(Theme.TEXT_SIZE_LARGER);
        title.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
        title.setText(I18n.get(MusicHud.MOD_ID + ".button.history"));
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        titleParams.setMargins(dp(4), 0, dp(4), 0);
        topBar.addView(title, titleParams);

        ImageButton refreshButton = new ImageButton(context);
        buttonBackgroundFactory.applyBackgroundTo(refreshButton);
        Tooltip.attach(refreshButton, I18n.get(MusicHud.MOD_ID + ".button.refresh"));
        refreshButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        Image refreshIcon = ImageUtils.getImageFromResource("/assets/music_hud/textures/gui/icons/rotate_cw.png");
        if (refreshIcon != null) {
            refreshButton.setImageDrawable(new InsetDrawable(
                    new ScaledImageDrawable(getContext().getResources(), refreshIcon, dp(12), dp(16)), dp(3)));
        }
        refreshButton.setOnClickListener(view -> {
            if (pager != null && pager.getAdapter() instanceof HistoryPagerAdapter adapter) {
                HistoryPage page = adapter.getPage(pager.getCurrentItem());
                if (page != null) {
                    page.reload();
                }
            }
        });
        topBar.addView(refreshButton, new LinearLayout.LayoutParams(dp(40), dp(40)));

        {
            View divider = new View(context);
            ShapeDrawable dividerBg = new ShapeDrawable();
            dividerBg.setShape(ShapeDrawable.VLINE);
            dividerBg.setSize(divider.dp(1), divider.dp(1));
            dividerBg.setColor(Theme.GHOST_BUTTON_BACKGROUND_HOVERED);
            divider.setBackground(dividerBg);
            LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(divider.dp(1), MATCH_PARENT);
            dividerParams.setMargins(divider.dp(2), divider.dp(8), divider.dp(2), divider.dp(8));
            topBar.addView(divider, dividerParams);
        }

        SegmentedBar tabBar = new SegmentedBar(context);
        tabBar.addItem(new SegmentedBar.Item("track", null,
                "/assets/music_hud/textures/gui/icons/eighth_note.png",
                I18n.get(MusicHud.MOD_ID + ".text.history.track")));
        tabBar.addItem(new SegmentedBar.Item("album", null,
                "/assets/music_hud/textures/gui/icons/disc_album.png",
                I18n.get(MusicHud.MOD_ID + ".text.history.album")));
        tabBar.addItem(new SegmentedBar.Item("playlist", null,
                "/assets/music_hud/textures/gui/icons/list_music.png",
                I18n.get(MusicHud.MOD_ID + ".text.history.playlist")));
        topBar.addView(tabBar, new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));

        pager = new ViewPager(context);
        pager.setAdapter(new HistoryPagerAdapter());
        pager.setFocusableInTouchMode(true);
        pager.setKeyboardNavigationCluster(true);
        tabBar.setOnItemSelectedListener((key, index) -> pager.setCurrentItem(index, true));
        pager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                tabBar.setSelectedIndex(position, true);
            }
        });
        addView(pager, new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));

        FrameLayout.LayoutParams topBarParams = new FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        topBarParams.gravity = Gravity.TOP;
        topBarParams.setMargins(0, dp(12), 0, 0);
        // Added last so its backdrop blur samples the pager painted underneath.
        addView(topBar, topBarParams);
    }

    /**
     * The page inset equals the space the floating bar occupies, which is only known after measure.
     * It goes to each page's own scroll view so their content scrolls underneath the bar.
     */
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) topBar.getLayoutParams();
        int inset = lp.topMargin + topBar.getMeasuredHeight();
        if (pager != null && pager.getAdapter() instanceof HistoryPagerAdapter adapter) {
            adapter.setContentInsetTop(inset);
        }
        super.onLayout(changed, left, top, right, bottom);
    }

    private static class HistoryPagerAdapter extends PagerAdapter {
        private final Map<Integer, HistoryPage> pages = new HashMap<>();
        private int contentInsetTop;

        void setContentInsetTop(int inset) {
            if (contentInsetTop == inset) {
                return;
            }
            contentInsetTop = inset;
            pages.values().forEach(page -> page.setContentInsetTop(inset));
        }

        @Override
        public int getCount() {
            return 3;
        }

        @NonNull
        @Override
        public Object instantiateItem(@NonNull ViewGroup container, int position) {
            HistoryPage page = new HistoryPage(container.getContext(), position);
            page.setContentInsetTop(contentInsetTop);
            pages.put(position, page);
            container.addView(page, new ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT));
            return page;
        }

        @Override
        public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
            pages.remove(position);
            if (object instanceof View view) {
                container.removeView(view);
            }
        }

        HistoryPage getPage(int position) {
            return pages.get(position);
        }

        @Override
        public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
            return view == object;
        }
    }

    /**
     * One tab page. Loads its record list lazily on first attach and renders it into a
     * scrolling column.
     */
    private static class HistoryPage extends FrameLayout {
        private final int position;
        private final ScrollView scrollView;
        private final LinearLayout content;
        private final ProgressBar progressBar;
        private final TextView emptyText;
        private boolean loaded = false;

        HistoryPage(Context context, int position) {
            super(context);
            this.position = position;

            //noinspection UnstableApiUsage
            scrollView = new ClampingScrollView(context);
            scrollView.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
            scrollView.setClipToPadding(false);
            addView(scrollView, new LayoutParams(MATCH_PARENT, MATCH_PARENT));

            content = new LinearLayout(context);
            content.setOrientation(LinearLayout.VERTICAL);
            scrollView.addView(content, new ScrollView.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

            progressBar = new ProgressBar(context);
            progressBar.setIndeterminate(true);
            LayoutParams progressParams = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            progressParams.gravity = Gravity.CENTER;
            addView(progressBar, progressParams);

            emptyText = new TextView(context);
            emptyText.setText(I18n.get(MusicHud.MOD_ID + ".text.history.empty"));
            emptyText.setTextColor(Theme.SECONDARY_TEXT_COLOR);
            emptyText.setTextSize(Theme.TEXT_SIZE_NORMAL);
            emptyText.setTextAlignment(TEXT_ALIGNMENT_CENTER);
            emptyText.setVisibility(GONE);
            LayoutParams emptyParams = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            emptyParams.gravity = Gravity.CENTER;
            addView(emptyText, emptyParams);

            addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                    if (!loaded) {
                        loaded = true;
                        load();
                    }
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                }
            });
        }

        void setContentInsetTop(int inset) {
            if (scrollView.getPaddingTop() != inset) {
                scrollView.setPadding(0, inset, 0, 0);
            }
        }

        void reload() {
            loaded = true;
            content.removeAllViews();
            emptyText.setVisibility(GONE);
            progressBar.setVisibility(VISIBLE);
            load();
        }

        private void load() {
            switch (position) {
                case 0 -> RequestResponseManager.send(
                                GetUserRecentTrackRecordRequest.REQUEST,
                                GetUserRecentTrackRecordResponse.class,
                                REQUEST_TIMEOUT)
                        .thenAccept(response -> MuiModApi.postToUiThread(
                                () -> renderTrackRecords(response.getResult().extraData())))
                        .exceptionally(this::onLoadFailed);
                case 1 -> RequestResponseManager.send(
                                GetUserRecentAlbumRecordRequest.REQUEST,
                                GetUserRecentAlbumRecordResponse.class,
                                REQUEST_TIMEOUT)
                        .thenAccept(response -> MuiModApi.postToUiThread(
                                () -> renderAlbumRecords(response.getResult().extraData())))
                        .exceptionally(this::onLoadFailed);
                case 2 -> RequestResponseManager.send(
                                GetUserRecentPlaylistRecordRequest.REQUEST,
                                GetUserRecentPlaylistRecordResponse.class,
                                REQUEST_TIMEOUT)
                        .thenAccept(response -> MuiModApi.postToUiThread(
                                () -> renderPlaylistRecords(response.getResult().extraData())))
                        .exceptionally(this::onLoadFailed);
                default -> {
                }
            }
        }

        private Void onLoadFailed(Throwable throwable) {
            MusicHud.getLogger(PlayHistoryView.class).warn("Failed to load play records", throwable);
            MuiModApi.postToUiThread(() -> {
                progressBar.setVisibility(GONE);
                emptyText.setVisibility(VISIBLE);
            });
            return null;
        }

        private void renderTrackRecords(List<PlayRecord<MusicDetail>> records) {
            if (!isAttachedToWindow()) {
                return;
            }
            progressBar.setVisibility(GONE);
            int count = 0;
            for (PlayRecord<MusicDetail> record : records) {
                Object data = record.getData();
                if (!(data instanceof MusicDetail musicDetail)) {
                    continue;
                }
                MusicTrackItem item = MusicListFactory.createItem(this);
                item.bindData(musicDetail);
                item.setPlayRecordTime(record.getPlayTime());
                content.addView(item, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
                count++;
            }
            emptyText.setVisibility(count == 0 ? VISIBLE : GONE);
        }

        private void renderAlbumRecords(List<PlayRecord<Album>> records) {
            if (!isAttachedToWindow()) {
                return;
            }
            progressBar.setVisibility(GONE);
            WaterfallLayout waterfall = new WaterfallLayout(getContext());
            waterfall.setRowMinWidth(dp(174));
            int count = 0;
            for (PlayRecord<Album> record : records) {
                Object data = record.getData();
                if (!(data instanceof Album album)) {
                    continue;
                }
                waterfall.addView(new MusicCollectionCard(getContext(), album, PusherInfo.EMPTY, dp(160)));
                count++;
            }
            if (count > 0) {
                content.addView(waterfall, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
            }
            emptyText.setVisibility(count == 0 ? VISIBLE : GONE);
        }

        private void renderPlaylistRecords(List<PlayRecord<Playlist>> records) {
            if (!isAttachedToWindow()) {
                return;
            }
            progressBar.setVisibility(GONE);
            WaterfallLayout waterfall = new WaterfallLayout(getContext());
            waterfall.setRowMinWidth(dp(174));
            int count = 0;
            for (PlayRecord<Playlist> record : records) {
                Object data = record.getData();
                if (!(data instanceof Playlist playlist)) {
                    continue;
                }
                waterfall.addView(new MusicCollectionCard(getContext(), playlist, PusherInfo.EMPTY, dp(160)));
                count++;
            }
            if (count > 0) {
                content.addView(waterfall, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
            }
            emptyText.setVisibility(count == 0 ? VISIBLE : GONE);
        }
    }
}
