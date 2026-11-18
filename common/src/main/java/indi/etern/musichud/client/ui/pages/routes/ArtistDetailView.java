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
import indi.etern.musichud.beans.music.Artist;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.beans.music.Traceable;
import indi.etern.musichud.client.services.music.MusicService;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.ToastUtil;
import indi.etern.musichud.client.ui.components.MusicTrackItem;
import indi.etern.musichud.client.ui.components.RouterContainer;
import indi.etern.musichud.client.ui.components.Tooltip;
import indi.etern.musichud.client.ui.components.UrlImageView;
import indi.etern.musichud.client.ui.components.buttons.ToggleSubscribeButton;
import indi.etern.musichud.client.ui.drawable.ScaledImageDrawable;
import indi.etern.musichud.client.ui.hud.metadata.BackgroundData;
import indi.etern.musichud.client.ui.hud.metadata.BackgroundImages;
import indi.etern.musichud.client.ui.hud.presentation.BackgroundOverrideStack;
import indi.etern.musichud.client.ui.hud.presentation.BackgroundState;
import indi.etern.musichud.client.ui.screen.MainFragment;
import indi.etern.musichud.client.utils.image.ImageTextureData;
import indi.etern.musichud.client.utils.image.ImageUtils;
import indi.etern.musichud.client.utils.ui.InsetBackgroundFactory;
import indi.etern.musichud.client.utils.ui.PanelStyle;
import indi.etern.musichud.interfaces.IClientMusicService;
import net.minecraft.client.resources.language.I18n;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class ArtistDetailView extends FrameLayout implements RouterContainer.RouteAware {
    private static final IClientMusicService musicService = MusicService.getInstance();
    private static final int AVATAR_SIZE = 1024;
    private static final int BAR_MARGIN = 12;
    private static final int BAR_CORNER_RADIUS = 16;
    private static final int PANEL_WIDTH_DP = 280;
    private static final int PANEL_PADDING_DP = 12;
    private static final int PANEL_MARGIN_DP = 12;
    private static final int PANEL_GAP_DP = 12;
    private static final int PANEL_WIDTH = PANEL_WIDTH_DP + PANEL_PADDING_DP * 2;
    private static final int LIST_RIGHT_INSET = PANEL_WIDTH + PANEL_MARGIN_DP + PANEL_GAP_DP;
    private static final String ICON_DISC_ALBUM = "/assets/music_hud/textures/gui/icons/disc_album.png";
    private static final String ICON_LIST_MUSIC = "/assets/music_hud/textures/gui/icons/list_music.png";
    private final LinearLayout topBar;
    private final ScrollView scrollView;
    private final LinearLayout musicList;
    private final TextView noMoreResultText;
    private final ProgressBar loadingProgressRing;
    private final ProgressBar progressBar;
    private final TextView nameView;
    private final TextView albumCountView;
    private final TextView musicCountView;
    private final LinearLayout statsRow;
    private final TextView description;
    private final UrlImageView avatarImageView;
    private final InsetBackgroundFactory itemBackgroundFactory = InsetBackgroundFactory.builder()
            .cornerRadius(dp(7))
            .inset(dp(1))
            .padding(new InsetBackgroundFactory.Padding(dp(4), dp(4), dp(4), dp(4))).build();
    private final int imageSize = dp(PANEL_WIDTH_DP);
    Artist artist;
    boolean noMoreResult = false;
    boolean loadingMore = false;
    private String currentAvatarUrl = null;
    private boolean routeActive;
    private BackgroundOverrideStack.Layer backgroundThemeLayer;
    private BackgroundData backgroundData;
    private String backgroundImageUrl;
    private int backgroundImageGeneration;
    private ImageTextureData ownedBackgroundImage;
    private boolean artistDetailLoaded;

    public ArtistDetailView(Context context, Artist artist) {
        super(context);

        this.artist = artist;

        // The list fills the page and scrolls underneath the floating bar, so it is added first.
        scrollView = new ScrollView(context);
        scrollView.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);
        scrollView.setFillViewport(true);
        scrollView.setClipToPadding(false);
        LayoutParams params = new LayoutParams(MATCH_PARENT, MATCH_PARENT);
        params.setMargins(dp(2), 0, dp(2), 0);
        addView(scrollView, params);

        LinearLayout musicListWrapper = new LinearLayout(context);
        musicListWrapper.setOrientation(LinearLayout.VERTICAL);
        musicListWrapper.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);

        musicList = new LinearLayout(context);
        musicList.setOrientation(LinearLayout.VERTICAL);
        musicListWrapper.addView(musicList, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        {
            loadingProgressRing = new ProgressBar(getContext());
            loadingProgressRing.setIndeterminate(true);
            loadingProgressRing.setVisibility(GONE);
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            layoutParams.setMargins(0, dp(16), 0, dp(16));
            layoutParams.gravity = Gravity.CENTER;
            loadingProgressRing.setLayoutParams(layoutParams);
            musicListWrapper.addView(loadingProgressRing);
        }
        {
            noMoreResultText = new TextView(getContext());
            noMoreResultText.setText(I18n.get(MusicHud.MOD_ID + ".text.searchNoMoreResult"));
            noMoreResultText.setTextColor(Theme.SECONDARY_TEXT_COLOR);
            noMoreResultText.setTextSize(Theme.TEXT_SIZE_NORMAL);
            noMoreResultText.setTextAlignment(TEXT_ALIGNMENT_CENTER);
            noMoreResultText.setVisibility(GONE);
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            layoutParams.setMargins(0, dp(32), 0, dp(32));
            layoutParams.gravity = Gravity.CENTER;
            noMoreResultText.setLayoutParams(layoutParams);
            musicListWrapper.addView(noMoreResultText);
        }

        scrollView.addView(musicListWrapper, new ScrollView.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        progressBar = new ProgressBar(context);
        progressBar.setIndeterminate(true);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT);
        progressParams.setMargins(0, dp(32), dp(LIST_RIGHT_INSET), 0);
        addView(progressBar, progressParams);

        LinearLayout infoPanel = new LinearLayout(context);
        infoPanel.setOrientation(LinearLayout.VERTICAL);
        infoPanel.setPadding(dp(PANEL_PADDING_DP), dp(PANEL_PADDING_DP), dp(PANEL_PADDING_DP), dp(PANEL_PADDING_DP));
        PanelStyle.applyDynamically(infoPanel, dp(BAR_CORNER_RADIUS),
                new InsetBackgroundFactory.Padding(0, 0, 0, 0),
                InsetBackgroundFactory.BackdropBlur.UI_LAYER);

        avatarImageView = new UrlImageView(context);
        avatarImageView.setLoading(true);
        avatarImageView.setCornerRadius(dp(8));
        infoPanel.addView(avatarImageView, new LinearLayout.LayoutParams(MATCH_PARENT, imageSize));
        loadAvatarImage();

        nameView = new TextView(context);
        nameView.setTextSize(Theme.TEXT_SIZE_LARGE);
        nameView.setTextColor(Theme.NORMAL_TEXT_COLOR);
        nameView.setText(artist.getName());
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        nameParams.setMargins(0, dp(12), 0, 0);
        infoPanel.addView(nameView, nameParams);

        albumCountView = createStatView(context);
        albumCountView.setVisibility(GONE);
        musicCountView = createStatView(context);
        musicCountView.setVisibility(GONE);
        statsRow = new LinearLayout(context);
        statsRow.setOrientation(LinearLayout.HORIZONTAL);
        statsRow.setBaselineAligned(false);
        statsRow.setGravity(Gravity.CENTER_VERTICAL);
        statsRow.setVisibility(GONE);
        LinearLayout.LayoutParams statsRowParams = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        statsRowParams.setMargins(0, dp(6), 0, 0);
        infoPanel.addView(statsRow, statsRowParams);
        LinearLayout.LayoutParams albumCountParams = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        albumCountParams.setMargins(0, 0, dp(12), 0);
        statsRow.addView(albumCountView, albumCountParams);
        statsRow.addView(musicCountView, new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));

        ScrollView descriptionScroll = new ScrollView(context);
        descriptionScroll.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        LinearLayout.LayoutParams descriptionScrollParams = new LinearLayout.LayoutParams(MATCH_PARENT, 0, 1);
        descriptionScrollParams.setMargins(0, dp(12), 0, 0);
        infoPanel.addView(descriptionScroll, descriptionScrollParams);

        description = new TextView(context);
        description.setTextSize(Theme.TEXT_SIZE_NORMAL);
        description.setTextColor(Theme.SECONDARY_TEXT_COLOR);
        description.setVisibility(GONE);
        descriptionScroll.addView(description, new ScrollView.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(dp(PANEL_WIDTH), MATCH_PARENT);
        panelParams.gravity = Gravity.TOP | Gravity.END;
        panelParams.setMargins(0, dp(PANEL_MARGIN_DP), dp(PANEL_MARGIN_DP), dp(PANEL_MARGIN_DP));
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

        TextView typeView = new TextView(context);
        typeView.setTextSize(Theme.TEXT_SIZE_LARGE);
        typeView.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
        typeView.setSingleLine();
        typeView.setEllipsize(TextUtils.TruncateAt.END);
        typeView.setMaxWidth(dp(320));
        typeView.setText(I18n.get(MusicHud.MOD_ID + ".text.artist"));
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
            refreshButton.setOnClickListener(view -> loadData(true));
            topBar.addView(refreshButton, new LinearLayout.LayoutParams(dp(40), dp(40)));
        }
        {
            ToggleSubscribeButton toggleSubscribeButton = new ToggleSubscribeButton(context);
            buttonBackgroundFactory.applyBackgroundTo(toggleSubscribeButton);
            topBar.addView(toggleSubscribeButton, new LinearLayout.LayoutParams(dp(40), dp(40)));
            var subscribeState = musicService.getArtistSubscribedState(artist);
            toggleSubscribeButton.bindState(subscribeState);
        }

        PanelStyle.applyDynamically(topBar, dp(BAR_CORNER_RADIUS),
                new InsetBackgroundFactory.Padding(dp(2), dp(2), dp(2), dp(2)),
                InsetBackgroundFactory.BackdropBlur.UI_LAYER);
        FrameLayout.LayoutParams topBarParams = new FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        topBarParams.gravity = Gravity.TOP;
        topBarParams.setMargins(0, dp(BAR_MARGIN), 0, dp(BAR_MARGIN));
        // Added last: its backdrop blur samples the content recorded underneath.
        addView(topBar, topBarParams);

        scrollView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            checkInfiniteScroll(scrollY, scrollView);
        });

        loadData(false);
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

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        popBackground();
    }

    private void loadData(boolean ignoreCache) {
        noMoreResult = false;
        loadingMore = false;
        MuiModApi.postToUiThread(() -> {
            noMoreResultText.setVisibility(GONE);
            loadingProgressRing.setVisibility(GONE);
            progressBar.setVisibility(View.VISIBLE);
        });
        MusicService.getInstance().loadArtist(artist.getId(), ignoreCache).thenAcceptAsync(artist1 -> {
            if (artist1 != null) {
                ArtistDetailView.this.artist = artist1;
                MuiModApi.postToUiThread(() -> {
                    nameView.setText(artist.getName());
                    updateProductionCounts(artist);
                    String descriptionText = artist1.getDescription();
                    description.setText(descriptionText);
                    description.setVisibility(descriptionText.isBlank() ? GONE : VISIBLE);
                    artistDetailLoaded = true;
                    loadAvatarImage();
                    syncBackground();
                    musicList.removeAllViews();
                    for (MusicDetail musicDetail : artist1.getMusicDetails()) {
                        addItem(getContext(), musicDetail);
                    }
                    progressBar.setVisibility(GONE);
                });
            }
        }, MusicHud.EXECUTOR);
    }

    /** Statistics share one row under the name; the row hides when there is nothing to show. */
    private void updateProductionCounts(Artist artist) {
        int albumCount = artist.getAlbumCount();
        albumCountView.setVisibility(albumCount > 0 ? VISIBLE : GONE);
        if (albumCount > 0) {
            albumCountView.setText(buildIconText(String.valueOf(albumCount), ICON_DISC_ALBUM));
        }
        int musicCount = artist.getMusicCount();
        musicCountView.setVisibility(musicCount > 0 ? VISIBLE : GONE);
        if (musicCount > 0) {
            musicCountView.setText(buildIconText(String.valueOf(musicCount), ICON_LIST_MUSIC));
        }
        statsRow.setVisibility(albumCount > 0 || musicCount > 0 ? VISIBLE : GONE);
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

    private void loadAvatarImage() {
        Artist current = artist;
        if (current == null) {
            return;
        }
        if (current.getAvatarUrl().isBlank()) {
            if (artistDetailLoaded) {
                avatarImageView.loadUrl(current.getAvatarThumbnailUrl(imageSize));
                currentAvatarUrl = null;
            }
            return;
        }
        String avatarUrl = current.getAvatarThumbnailUrl(AVATAR_SIZE);
        if (Objects.equals(currentAvatarUrl, avatarUrl)) {
            return;
        }
        currentAvatarUrl = avatarUrl;
        avatarImageView.loadUrl(avatarUrl, imageSize, imageSize);
    }

    private void syncBackground() {
        if (!routeActive) {
            popBackground();
            return;
        }
        Artist current = artist;
        if (current == null || current.getAvatarUrl().isBlank()) {
            return;
        }
        String resolvedAvatarUrl = currentAvatarUrl;
        if (resolvedAvatarUrl == null || resolvedAvatarUrl.isBlank()) {
            resolvedAvatarUrl = current.getAvatarThumbnailUrl(AVATAR_SIZE);
        }
        if (resolvedAvatarUrl == null || resolvedAvatarUrl.isBlank()) {
            return;
        }
        final String avatarUrl = resolvedAvatarUrl;
        if (backgroundData != null && Objects.equals(backgroundImageUrl, avatarUrl)) {
            pushBackground(backgroundData);
            return;
        }
        if (Objects.equals(backgroundImageUrl, avatarUrl)) {
            return;
        }
        backgroundImageUrl = avatarUrl;
        backgroundData = null;
        int generation = ++backgroundImageGeneration;
        if (avatarUrl.startsWith("data:image")) {
            CompletableFuture.supplyAsync(() -> {
                ImageTextureData image = ImageUtils.loadBase64(avatarUrl);
                try {
                    return new LoadedBackground(image, true);
                } catch (RuntimeException | Error failure) {
                    image.close();
                    throw failure;
                }
            }, MusicHud.EXECUTOR).whenComplete((loaded, throwable) ->
                    handleBackgroundData(generation, avatarUrl, loaded, throwable));
        } else {
            ImageUtils.downloadScaledAsync(avatarUrl, AVATAR_SIZE, AVATAR_SIZE)
                    .thenApplyAsync(image -> image == null
                                    ? null
                                    : new LoadedBackground(image, false),
                            MusicHud.EXECUTOR)
                    .whenComplete((loaded, throwable) ->
                            handleBackgroundData(generation, avatarUrl, loaded, throwable));
        }
    }

    private void handleBackgroundData(int generation, String avatarUrl,
                                      LoadedBackground loaded, Throwable throwable) {
        MuiModApi.postToUiThread(() -> {
            if (generation != backgroundImageGeneration || !Objects.equals(backgroundImageUrl, avatarUrl)) {
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

    private void checkInfiniteScroll(int scrollY, ScrollView sv) {
        if (loadingMore || noMoreResult) return;
        if (sv.getChildCount() > 0) {
            View child = sv.getChildAt(0);
            int contentHeight = child.getHeight();
            int viewHeight = sv.getHeight();
            // The scroll range includes the padding, which holds the floating bar's inset.
            int maxScroll = Math.max(0, contentHeight + sv.getPaddingTop() + sv.getPaddingBottom() - viewHeight);
            int threshold = sv.dp(100);

            MuiModApi.postToUiThread(() -> {
                if (loadingProgressRing != null) {
                    loadingProgressRing.setVisibility(View.VISIBLE);
                }
            });
            if (maxScroll - scrollY <= threshold) {
                if (artist.getMusicDetails().size() >= artist.getMusicCount()) {
                    noMoreResult = true;
                    MuiModApi.postToUiThread(() -> {
                        loadingProgressRing.setVisibility(View.GONE);
                        noMoreResultText.setVisibility(View.VISIBLE);
                    });
                } else {
                    loadingMore = true;
                    MusicService.getInstance().loadMoreMusicOfArtist(artist).thenAccept((musicDetails) -> {
                        MuiModApi.postToUiThread(() -> {
                            for (MusicDetail musicDetail : musicDetails) {
                                addItem(getContext(), musicDetail);
                            }
                            loadingProgressRing.setVisibility(View.GONE);
                            loadingMore = false;
                        });
                    });
                }
            }
        }
    }

    private void addItem(Context context, MusicDetail musicDetail) {
        var musicLayout = new MusicTrackItem(context);
        musicLayout.setShowPusherInfo(false);
        musicLayout.bindData(musicDetail);
        itemBackgroundFactory.applyBackgroundTo(musicLayout);

        musicLayout.setClickable(true);
        String artistsName = musicDetail.getArtists().stream()
                .map(Artist::getName).collect(Collectors.joining(" / "));
        musicLayout.setOnClickListener((view) -> {
            MusicService.getInstance().sendPushMusicToQueue(Traceable.of(musicDetail.getId()));
            ToastUtil.show(Toast.makeText(context, I18n.get(MusicHud.MOD_ID + ".text.pushedMusicToPlayQueue") + "\n" + musicDetail.getName() + " - " + artistsName, Toast.LENGTH_SHORT));
        });
        musicList.addView(musicLayout);
    }

    private record LoadedBackground(ImageTextureData image, boolean owned) {
    }
}
