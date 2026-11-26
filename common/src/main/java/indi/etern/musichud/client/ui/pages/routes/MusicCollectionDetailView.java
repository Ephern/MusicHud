package indi.etern.musichud.client.ui.pages.routes;

import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Image;
import icyllis.modernui.graphics.drawable.InsetDrawable;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.text.SpannableString;
import icyllis.modernui.text.Spanned;
import icyllis.modernui.text.TextUtils;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.*;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.*;
import indi.etern.musichud.beans.user.Profile;
import indi.etern.musichud.client.services.music.MusicService;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.components.IdlePlaySourceWidget;
import indi.etern.musichud.client.ui.components.RouterContainer;
import indi.etern.musichud.client.ui.components.Tooltip;
import indi.etern.musichud.client.ui.components.UrlImageView;
import indi.etern.musichud.client.ui.components.buttons.ToggleSubscribeButton;
import indi.etern.musichud.client.ui.drawable.ScaledImageDrawable;
import indi.etern.musichud.client.ui.hud.metadata.BackgroundData;
import indi.etern.musichud.client.ui.hud.metadata.BackgroundImages;
import indi.etern.musichud.client.ui.hud.presentation.BackgroundOverrideStack;
import indi.etern.musichud.client.ui.hud.presentation.BackgroundState;
import indi.etern.musichud.client.ui.layouts.MusicVirtualizedListLayout;
import indi.etern.musichud.client.ui.screen.MainFragment;
import indi.etern.musichud.client.utils.CountFormatter;
import indi.etern.musichud.client.utils.image.ImageTextureData;
import indi.etern.musichud.client.utils.image.ImageUtils;
import indi.etern.musichud.client.utils.ui.InsetBackgroundFactory;
import indi.etern.musichud.client.utils.ui.PanelStyle;
import indi.etern.musichud.interfaces.Unregister;
import indi.etern.musichud.utils.CollectionUpdateNotifier;
import indi.etern.musichud.utils.collections.ObservableSequencedSet;
import net.minecraft.client.resources.language.I18n;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.SequencedSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class MusicCollectionDetailView extends FrameLayout implements RouterContainer.RouteAware {
    private static final MusicService musicService = MusicService.getInstance();
    private static final int COVER_SIZE = 1024;
    private static final int BAR_MARGIN = 12;
    private static final int BAR_CORNER_RADIUS = 16;
    private static final int PANEL_WIDTH_DP = 280;
    private static final int PANEL_PADDING_DP = 12;
    private static final int PANEL_MARGIN_DP = 12;
    private static final int PANEL_GAP_DP = 12;
    private static final int PANEL_WIDTH = PANEL_WIDTH_DP + PANEL_PADDING_DP * 2;
    private static final int LIST_RIGHT_INSET = PANEL_WIDTH + PANEL_MARGIN_DP + PANEL_GAP_DP;
    private static final String ICON_LIST_MUSIC = "/assets/music_hud/textures/gui/icons/list_music.png";
    private static final String ICON_LIKE_LIST_MUSIC = "/assets/music_hud/textures/gui/icons/heart_filled.png";
    private static final String ICON_RECOMMEND_LIST_MUSIC = "/assets/music_hud/textures/gui/icons/radio.png";
    private static final String ICON_DISC_ALBUM = "/assets/music_hud/textures/gui/icons/disc_album.png";
    private static final String ICON_AUDIO_LINES = "/assets/music_hud/textures/gui/icons/audio_lines.png";
    private final LinearLayout topBar;
    private final ScrollView scrollView;
    private final ProgressBar progressBar;
    private final MusicVirtualizedListLayout virtualList;
    private final UrlImageView imageView;
    private final TextView typeView;
    private final TextView nameView;
    private final TextView musicTrackCountView;
    private final TextView playedCountView;
    private final LinearLayout statsRow;
    private final TextView extraInfoView;
    private final AtomicBoolean syncPending = new AtomicBoolean();
    private final int imageSize = dp(PANEL_WIDTH_DP);
    private MusicCollection musicCollection;
    private Unregister tracksSyncUnregister = null;
    private Unregister updateNotifierUnregister = null;
    private String currentCoverUrl = null;
    private long collectionId = -1;
    private boolean albumCollection = false;
    private boolean routeActive;
    private BackgroundOverrideStack.Layer backgroundThemeLayer;
    private BackgroundData backgroundData;
    private String backgroundImageUrl;
    private int backgroundImageGeneration;
    private ImageTextureData ownedBackgroundImage;

    public MusicCollectionDetailView(Context context, MusicCollection musicCollection) {
        super(context);

        this.musicCollection = musicCollection;

        // The list fills the page and scrolls underneath the floating bar, so it is added first.
        virtualList = new MusicVirtualizedListLayout(context);
        scrollView = new ScrollView(context);
        scrollView.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);
        scrollView.setClipToPadding(false);
        scrollView.addView(virtualList, new ScrollView.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        scrollView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) ->
                updateVirtualWindow());
        scrollView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                updateVirtualWindow());
        scrollView.post(this::updateVirtualWindow);
        LayoutParams params = new LayoutParams(MATCH_PARENT, MATCH_PARENT);
        params.setMargins(dp(2), 0, dp(2), 0);
        addView(scrollView, params);

        progressBar = new ProgressBar(context);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT);
        progressParams.setMargins(0, dp(32), dp(LIST_RIGHT_INSET), 0);
        addView(progressBar, progressParams);

        LinearLayout infoPanel = new LinearLayout(context);
        infoPanel.setOrientation(LinearLayout.VERTICAL);
        infoPanel.setPadding(dp(PANEL_PADDING_DP), dp(PANEL_PADDING_DP), dp(PANEL_PADDING_DP), dp(PANEL_PADDING_DP));
        PanelStyle.applyDynamically(infoPanel, dp(BAR_CORNER_RADIUS),
                new InsetBackgroundFactory.Padding(0, 0, 0, 0),
                InsetBackgroundFactory.BackdropBlur.UI_LAYER);

        imageView = new UrlImageView(context);
        imageView.setCornerRadius(dp(8));
        infoPanel.addView(imageView, new LinearLayout.LayoutParams(MATCH_PARENT, imageSize));
        loadCoverImage();

        nameView = new TextView(context);
        nameView.setTextSize(Theme.TEXT_SIZE_LARGE);
        nameView.setTextColor(Theme.NORMAL_TEXT_COLOR);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        nameParams.setMargins(0, dp(12), 0, 0);
        infoPanel.addView(nameView, nameParams);

        musicTrackCountView = createStatView(context);
        playedCountView = createStatView(context);
        statsRow = new LinearLayout(context);
        statsRow.setOrientation(LinearLayout.HORIZONTAL);
        statsRow.setBaselineAligned(false);
        statsRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams statsRowParams = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        statsRowParams.setMargins(0, dp(6), 0, 0);
        infoPanel.addView(statsRow, statsRowParams);
        LinearLayout.LayoutParams musicTrackCountParams = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        musicTrackCountParams.setMargins(0, 0, dp(12), 0);
        statsRow.addView(musicTrackCountView, musicTrackCountParams);
        statsRow.addView(playedCountView, new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));

        ScrollView extraInfoScroll = new ScrollView(context);
        extraInfoScroll.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        LinearLayout.LayoutParams extraInfoScrollParams = new LinearLayout.LayoutParams(MATCH_PARENT, 0, 1);
        extraInfoScrollParams.setMargins(0, dp(12), 0, 0);
        infoPanel.addView(extraInfoScroll, extraInfoScrollParams);

        extraInfoView = new TextView(context);
        extraInfoView.setTextSize(Theme.TEXT_SIZE_NORMAL);
        extraInfoView.setTextColor(Theme.SECONDARY_TEXT_COLOR);
        extraInfoScroll.addView(extraInfoView, new ScrollView.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(dp(PANEL_WIDTH), MATCH_PARENT);
        panelParams.gravity = Gravity.TOP | Gravity.END;
        panelParams.setMargins(0, dp(64), dp(PANEL_MARGIN_DP), dp(PANEL_MARGIN_DP));
        addView(infoPanel, panelParams);

        topBar = new LinearLayout(context);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setClickable(true);

        InsetBackgroundFactory buttonBackgroundFactory = InsetBackgroundFactory.builder()
                .backgroundColor(Theme.GHOST_BUTTON_STATES)
                .inset(dp(1))
                .cornerRadius(dp(14))
                .build();
        {
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
            buttonBackgroundFactory.applyBackgroundTo(backButton);
            topBar.addView(backButton, new LinearLayout.LayoutParams(dp(40), dp(40)));
        }

        typeView = new TextView(context);
        typeView.setTextSize(Theme.TEXT_SIZE_LARGE);
        typeView.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
        typeView.setSingleLine();
        typeView.setEllipsize(TextUtils.TruncateAt.END);
        typeView.setMaxWidth(dp(320));
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        titleParams.setMargins(dp(4), 0, dp(4), 0);
        topBar.addView(typeView, titleParams);

        {
            ImageButton refreshButton = new ImageButton(context);
            buttonBackgroundFactory.applyBackgroundTo(refreshButton);
            Tooltip.attach(refreshButton, I18n.get(MusicHud.MOD_ID + ".button.refresh"));
            refreshButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            Image refreshIcon = ImageUtils.getImageFromResource("/assets/music_hud/textures/gui/icons/rotate_cw.png");
            if (refreshIcon != null) {
                refreshButton.setImageDrawable(new InsetDrawable(
                        new ScaledImageDrawable(getContext().getResources(), refreshIcon, dp(12), dp(16)), dp(3)));
            }
            refreshButton.setOnClickListener(view -> refreshData(true));
            topBar.addView(refreshButton, new LinearLayout.LayoutParams(dp(40), dp(40)));
        }
        {
            ToggleSubscribeButton toggleSubscribeButton = new ToggleSubscribeButton(context);
            buttonBackgroundFactory.applyBackgroundTo(toggleSubscribeButton);
            topBar.addView(toggleSubscribeButton, new LinearLayout.LayoutParams(dp(40), dp(40)));
            if (musicCollection instanceof Playlist playlist) {
                Profile current = Profile.getCurrent();
                if (current == null || current.equals(Profile.ANONYMOUS) || playlist.getCreator().getUserId() == current.getUserId()) {
                    toggleSubscribeButton.setVisibility(GONE);
                } else {
                    var subscribeState = musicService.getPlaylistSubscribeState(playlist);
                    toggleSubscribeButton.bindState(subscribeState);
                }
            } else if (musicCollection instanceof Album album) {
                var subscribeState = musicService.getAlbumSubscribeState(album);
                toggleSubscribeButton.bindState(subscribeState);
            }
        }
        {
            var idlePlaySourceWidget = new IdlePlaySourceWidget(context, this.musicCollection, dp(40), v -> buttonBackgroundFactory);
            topBar.addView(idlePlaySourceWidget, new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        }

        PanelStyle.applyDynamically(topBar, dp(BAR_CORNER_RADIUS),
                new InsetBackgroundFactory.Padding(dp(2), dp(2), dp(2), dp(2)),
                InsetBackgroundFactory.BackdropBlur.UI_LAYER);
        FrameLayout.LayoutParams topBarParams = new FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        topBarParams.gravity = Gravity.TOP;
        topBarParams.setMargins(0, dp(BAR_MARGIN), 0, dp(BAR_MARGIN));
        // Added last: its backdrop blur samples the content recorded underneath.
        addView(topBar, topBarParams);

        bindInfo(musicCollection);

        if (musicCollection instanceof Playlist playlist) {//TODO intelligent recommendations list for like list
            collectionId = playlist.getId();
            albumCollection = false;
            updateNotifierUnregister = CollectionUpdateNotifier.registerPlaylist(collectionId, this::onCollectionUpdateNotified);
        } else if (musicCollection instanceof Album album) {
            collectionId = album.getId();
            albumCollection = true;
            updateNotifierUnregister = CollectionUpdateNotifier.registerAlbum(collectionId, this::onCollectionUpdateNotified);
        }

        refreshData(false);
    }

    /**
     * The list inset equals the space the bar and the info panel occupy, which is only known after
     * measure for the bar. Applying it before laying children out keeps the first frame correct.
     */
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) topBar.getLayoutParams();
        int topInset = lp.topMargin + topBar.getMeasuredHeight() + lp.bottomMargin;
        int rightInset = dp(LIST_RIGHT_INSET);
        if (scrollView.getPaddingTop() != topInset || scrollView.getPaddingRight() != rightInset) {
            scrollView.setPadding(0, topInset, rightInset, 0);
        }
        super.onLayout(changed, left, top, right, bottom);
    }

    private void updateVirtualWindow() {
        int viewport = scrollView.getHeight();
        if (viewport <= 0) {
            return;
        }
        // The list is laid out below the bar, so shift the window up by that inset to keep the
        // content covered by the bar realized (it is what the bar's blur samples).
        virtualList.updateWindow(scrollView.getScrollY() - scrollView.getPaddingTop(), viewport);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (routeActive) {
            syncBackground();
        }
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE && changedView == this) {
            syncTracksView();
        }
        if (visibility == VISIBLE && routeActive) {
            syncBackground();
        }
    }

    @Override
    public void onRoutePushed() {
        routeActive = true;
        syncBackground();
    }

    @Override
    public void onRoutePopped() {
        routeActive = false;
        popBackground();
    }

    private void onCollectionUpdateNotified(boolean operateByRemoteSelf) {
        CompletableFuture<? extends MusicCollection> future;
        if (albumCollection) {
            future = musicService.loadAlbumDetail(collectionId, true);
        } else {
            future = musicService.loadPlaylistDetail(collectionId, true);
        }
        future.whenComplete((latest, throwable) -> {
            if (throwable != null || !MusicCollections.isUsable(latest)) return;
            MuiModApi.postToUiThread(() -> {
                if (!isAttachedToWindow()) return;
                String newCoverUrl = latest.getImageThumbnailUrl(COVER_SIZE);
                if (!newCoverUrl.isBlank() && !Objects.equals(currentCoverUrl, newCoverUrl)) {
                    currentCoverUrl = newCoverUrl;
                    imageView.loadUrl(newCoverUrl, imageSize, imageSize);
                    syncBackground();
                }
                if (!operateByRemoteSelf) {
                    if (!isAttachedToWindow()) return;
                    if (latest != musicCollection) {
                        musicCollection = latest;
                    }
                    bindInfo(latest);
                    unregisterTracksSync();
                    registerTracksSync(latest);
                    syncTracksView();
                }
            });
        });
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        popBackground();
        unregisterTracksSync();
        if (updateNotifierUnregister != null) {
            updateNotifierUnregister.unregister();
            updateNotifierUnregister = null;
        }
    }

    private void loadCoverImage() {
        String coverUrl = musicCollection.getImageThumbnailUrl(COVER_SIZE);
        if (coverUrl.isBlank()) {
            return;
        }
        currentCoverUrl = coverUrl;
        imageView.loadUrl(coverUrl, imageSize, imageSize);
    }

    private void syncBackground() {
        if (!routeActive) {
            popBackground();
            return;
        }
        String resolvedCoverUrl = currentCoverUrl;
        if (resolvedCoverUrl == null || resolvedCoverUrl.isBlank()) {
            resolvedCoverUrl = musicCollection.getImageThumbnailUrl(COVER_SIZE);
        }
        if (resolvedCoverUrl == null || resolvedCoverUrl.isBlank()) {
            return;
        }
        final String coverUrl = resolvedCoverUrl;
        if (backgroundData != null && Objects.equals(backgroundImageUrl, coverUrl)) {
            pushBackground(backgroundData);
            return;
        }
        if (Objects.equals(backgroundImageUrl, coverUrl)) {
            return;
        }
        backgroundImageUrl = coverUrl;
        backgroundData = null;
        int generation = ++backgroundImageGeneration;
        if (coverUrl.startsWith("data:image")) {
            CompletableFuture.supplyAsync(() -> {
                ImageTextureData image = ImageUtils.loadBase64(coverUrl);
                try {
                    return new LoadedBackground(image, true);
                } catch (RuntimeException | Error failure) {
                    image.close();
                    throw failure;
                }
            }, MusicHud.EXECUTOR).whenComplete((loaded, throwable) ->
                    handleBackgroundData(generation, coverUrl, loaded, throwable));
        } else {
            ImageUtils.downloadScaledAsync(coverUrl, COVER_SIZE, COVER_SIZE)
                    .thenApplyAsync(image -> image == null
                                    ? null
                                    : new LoadedBackground(image, false),
                            MusicHud.EXECUTOR)
                    .whenComplete((loaded, throwable) ->
                            handleBackgroundData(generation, coverUrl, loaded, throwable));
        }
    }

    private void handleBackgroundData(int generation, String coverUrl,
                                      LoadedBackground loaded, Throwable throwable) {
        MuiModApi.postToUiThread(() -> {
            if (generation != backgroundImageGeneration || !Objects.equals(backgroundImageUrl, coverUrl)) {
                if (loaded != null && loaded.owned()) {
                    releaseDelayed(loaded.image());
                }
                return;
            }
            if (throwable != null || loaded == null) {
                if (loaded != null && loaded.owned()) {
                    releaseDelayed(loaded.image());
                }
                backgroundImageUrl = null;
                return;
            }
            BackgroundData data = new BackgroundData(new BackgroundImages(loaded.image(), 1f));
            if (!pushBackground(data)) {
                if (loaded.owned()) {
                    releaseDelayed(loaded.image());
                }
                backgroundImageUrl = null;
                return;
            }
            backgroundData = data;
            if (loaded.owned()) {
                ImageTextureData previous = ownedBackgroundImage;
                ownedBackgroundImage = loaded.image();
                releaseDelayed(previous);
            } else if (ownedBackgroundImage != null) {
                releaseDelayed(ownedBackgroundImage);
                ownedBackgroundImage = null;
            }
        });
    }

    private void releaseDelayed(ImageTextureData image) {
        if (image == null) {
            return;
        }
        MusicHud.EXECUTOR.execute(() -> {
            try {
                Thread.sleep(800);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            image.close();
        });
    }

    private boolean pushBackground(BackgroundData data) {
        if (!routeActive || !isAttachedToWindow() || data == null) {
            return false;
        }
        RouterContainer router = MainFragment.getInstance().getRouterContainer();
        if (router == null || router.getEffectivePage() != this) {
            return false;
        }
        BackgroundOverrideStack stack = BackgroundState.getInstance().getOverrides();
        if (backgroundThemeLayer == null) {
            backgroundThemeLayer = stack.push(data);
            return backgroundThemeLayer != null;
        }
        if (stack.update(backgroundThemeLayer, data)) {
            return true;
        }
        backgroundThemeLayer = stack.push(data);
        return backgroundThemeLayer != null;
    }

    private void popBackground() {
        if (backgroundThemeLayer != null) {
            BackgroundState.getInstance().getOverrides().pop(backgroundThemeLayer);
            backgroundThemeLayer = null;
        }
        if (ownedBackgroundImage != null) {
            releaseDelayed(ownedBackgroundImage);
            ownedBackgroundImage = null;
            backgroundData = null;
            backgroundImageUrl = null;
        }
    }

    private record LoadedBackground(ImageTextureData image, boolean owned) {
    }

    private void bindInfo(MusicCollection collection) {
        String typeLabel = I18n.get(collection.getNameI18nKey());
        if (collection instanceof Album album) {
            String albumType = mappedAlbumType(album.getType());
            typeView.setText(albumType.isBlank() || albumType.equals(typeLabel)
                    ? typeLabel
                    : typeLabel + " - " + albumType);
        } else {
            typeView.setText(typeLabel);
        }
        nameView.setText(collection.getName());

        musicTrackCountView.setVisibility(GONE);
        playedCountView.setVisibility(GONE);
        extraInfoView.setVisibility(GONE);

        if (collection instanceof Playlist playlist) {
            int musicTrackCount = playlist.getMusicTrackCount();
            if (musicTrackCount >= 0) {
                String iconPath = switch (playlist.getSpecialType()) {
                    case LIKE_LIST -> ICON_LIKE_LIST_MUSIC;
                    case USER_SPECIFIC -> ICON_RECOMMEND_LIST_MUSIC;
                    default -> ICON_LIST_MUSIC;
                };
                musicTrackCountView.setText(buildIconText(String.valueOf(musicTrackCount), iconPath));
                musicTrackCountView.setVisibility(VISIBLE);
            }
            long playedCount = playlist.getPlayedCount();
            if (playedCount >= 0) {
                playedCountView.setText(buildIconText(CountFormatter.formatCount(playedCount), ICON_AUDIO_LINES));
                playedCountView.setVisibility(VISIBLE);
            }
            String description = playlist.getDescription();
            if (!description.isBlank()) {
                extraInfoView.setText(description);
                extraInfoView.setVisibility(VISIBLE);
            }
        } else if (collection instanceof Album album) {
            int musicTrackCount = Math.max(album.getMusicTrackCount(), album.getMusicDetails().size());
            if (musicTrackCount > 0) {
                musicTrackCountView.setText(buildIconText(String.valueOf(musicTrackCount), ICON_DISC_ALBUM));
                musicTrackCountView.setVisibility(VISIBLE);
            }
            String company = album.getCompany();
            String artists = album.getArtists().stream()
                    .map(Artist::getName)
                    .reduce((a, b) -> a + " / " + b)
                    .orElse("");
            String alias = album.getAlias().stream()
                    .reduce((a, b) -> a + " | " + b)
                    .orElse("");
            String extraInfo = (company.isBlank() ? "" : (company + "\n")) + artists
                    + (alias.isBlank() ? "" : "\n" + alias);
            if (!extraInfo.isBlank()) {
                extraInfoView.setText(extraInfo);
                extraInfoView.setVisibility(VISIBLE);
            }
        }
        statsRow.setVisibility(musicTrackCountView.getVisibility() == VISIBLE
                || playedCountView.getVisibility() == VISIBLE ? VISIBLE : GONE);
    }

    private TextView createStatView(Context context) {
        TextView view = new TextView(context);
        view.setTextSize(Theme.TEXT_SIZE_NORMAL);
        view.setTextColor(Theme.SECONDARY_TEXT_COLOR);
        return view;
    }

    private SpannableString buildIconText(String text, String iconPath) {
        SpannableString value = new SpannableString("  " + text);
        Image icon = ImageUtils.getImageFromResource(iconPath);
        if (icon != null) {
            value.setSpan(ImageUtils.getIconSpan(icon), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return value;
    }

    private void refreshData(boolean ignoreCache) {
        syncPending.set(false);
        progressBar.setVisibility(View.VISIBLE);
        progressBar.setIndeterminate(true);
        MusicService.getInstance().loadMoreMusicOfCollection(musicCollection, ignoreCache)
                .whenComplete((result, throwable) -> MuiModApi.postToUiThread(() -> {
                    if (throwable != null || result == null || !MusicCollections.isUsable(result.musicCollection())) {
                        progressBar.setVisibility(View.GONE);
                        return;
                    }
                    Collection<MusicDetail> musicDetails = result.musicDetails();
                    MusicCollection musicCollection1 = result.musicCollection();
                    this.musicCollection = musicCollection1;
                    bindInfo(musicCollection1);
                    loadCoverImage();
                    syncBackground();
                    progressBar.setVisibility(View.GONE);
                    virtualList.resetItems(musicDetails instanceof ObservableSequencedSet<MusicDetail> observable
                            ? observable.snapshot()
                            : new ArrayList<>(musicDetails));
                    unregisterTracksSync();
                    registerTracksSync(musicCollection1);
                }));
    }

    private void unregisterTracksSync() {
        if (tracksSyncUnregister != null) {
            tracksSyncUnregister.unregister();
            tracksSyncUnregister = null;
        }
    }

    private void registerTracksSync(MusicCollection collection) {
        SequencedSet<MusicDetail> musicDetails = collection.getMusicDetails();
        if (!(musicDetails instanceof ObservableSequencedSet<MusicDetail> tracks)) {
            return;
        }
        tracksSyncUnregister = tracks.registerOnChange(this::scheduleTracksSync);
    }

    private void scheduleTracksSync() {
        if (syncPending.compareAndSet(false, true)) {
            MuiModApi.postToUiThread(() -> {
                syncPending.set(false);
                syncTracksView();
            });
        }
    }

    private void syncTracksView() {
        MusicCollection collection = musicCollection;
        if (collection == null) return;
        bindInfo(collection);
        SequencedSet<MusicDetail> musicDetails = collection.getMusicDetails();
        if (musicDetails instanceof ObservableSequencedSet<MusicDetail> tracks) {
            syncTracksList(tracks);
        }
        String newCoverUrl = collection.getImageThumbnailUrl(COVER_SIZE);
        if (!newCoverUrl.isBlank() && !Objects.equals(currentCoverUrl, newCoverUrl)) {
            currentCoverUrl = newCoverUrl;
            imageView.loadUrl(newCoverUrl, imageSize, imageSize);
            syncBackground();
        }
    }

    private void syncTracksList(ObservableSequencedSet<MusicDetail> tracks) {
        virtualList.syncItems(tracks.snapshot());
    }

    private String mappedAlbumType(String type) {
        return switch (type) {
            case "专辑" -> I18n.get(MusicHud.MOD_ID + ".text.album.type.album");
            case "EP" -> I18n.get(MusicHud.MOD_ID + ".text.album.type.ep");
            case "Single" -> I18n.get(MusicHud.MOD_ID + ".text.album.type.single");
            case "精选集" -> I18n.get(MusicHud.MOD_ID + ".text.album.type.compilation");
            default -> type;
        };
    }
}
