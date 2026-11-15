package indi.etern.musichud.client.ui.pages.routes;

import icyllis.modernui.animation.LayoutTransition;
import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Image;
import icyllis.modernui.graphics.drawable.InsetDrawable;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.text.SpannableString;
import icyllis.modernui.text.Spanned;
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
    private static final int BAR_TOP_MARGIN = 12;
    private static final int BAR_CORNER_RADIUS = 16;
    private final LinearLayout topBar;
    private final ScrollView scrollView;
    private final LinearLayout musicList;
    private final TextView noMoreResultText;
    private final ProgressBar loadingProgressRing;
    private final ProgressBar progressBar;
    private final TextView productionCounts;
    private final TextView description;
    private final UrlImageView avatarImageView;
    private final InsetBackgroundFactory itemBackgroundFactory = InsetBackgroundFactory.builder()
            .cornerRadius(dp(7))
            .inset(dp(1))
            .padding(new InsetBackgroundFactory.Padding(dp(4), dp(4), dp(4), dp(4))).build();
    private final int imageSize = dp(128);
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

        topBar = new LinearLayout(context);
        topBar.setClickable(true);
        topBar.setOrientation(LinearLayout.HORIZONTAL);

        ImageButton backButton = new ImageButton(context);
        String tooltipText = I18n.get(MusicHud.MOD_ID + ".button.back");
        Image image = ImageUtils.getImageFromResource("/assets/music_hud/textures/gui/icons/arrow_left.png");
        if (image != null) {
            backButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            backButton.setImageDrawable(new ScaledImageDrawable(getContext().getResources(), image, dp(16), dp(16)));
        }
        Tooltip.attach(backButton, tooltipText);
        backButton.setOnClickListener(view -> {
            MainFragment.getInstance().getRouterContainer().popNavigate();
            backButton.setOnClickListener(null);
        });
        InsetBackgroundFactory backgroundFactory = InsetBackgroundFactory.builder()
                .inset(0)
                .cornerRadius(dp(8))
                .padding(new InsetBackgroundFactory.Padding(dp(16), 0, dp(16), 0))
                .build();
        backgroundFactory.applyBackgroundTo(backButton);
        LinearLayout.LayoutParams backButtonParams = new LinearLayout.LayoutParams(WRAP_CONTENT, MATCH_PARENT);
        backButtonParams.setMargins(0, 0, dp(4), 0);
        topBar.addView(backButton, backButtonParams);

        avatarImageView = new UrlImageView(context);
        avatarImageView.setLoading(true);
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(imageSize, imageSize);
        avatarImageView.setCornerRadius(dp(8));
        topBar.addView(avatarImageView, imageParams);
        loadAvatarImage();

        LinearLayout artistInfoView = new LinearLayout(context);
        artistInfoView.setGravity(Gravity.LEFT | Gravity.TOP);
        artistInfoView.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params1 = new LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT);
        params1.setMargins(dp(16), 0, 0, 0);
        topBar.addView(artistInfoView, params1);

        LinearLayout row1 = new LinearLayout(context);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER_VERTICAL);
        LayoutTransition layoutTransition = new LayoutTransition();
        layoutTransition.enableTransitionType(LayoutTransition.CHANGING);
        layoutTransition.enableTransitionType(LayoutTransition.APPEARING);
        layoutTransition.enableTransitionType(LayoutTransition.DISAPPEARING);
        row1.setLayoutTransition(layoutTransition);
        LinearLayout.LayoutParams row1Params = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        row1Params.setMargins(0, 0, dp(32), dp(8));
        artistInfoView.addView(row1, row1Params);

        TextView name = new TextView(context);
        name.setTextSize(Theme.TEXT_SIZE_LARGER);
        name.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
        name.setText(artist.getName());
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        nameParams.setMargins(0, 0, dp(16), 0);
        row1.addView(name, nameParams);

        productionCounts = new TextView(context);
        productionCounts.setTextSize(Theme.TEXT_SIZE_LARGE);
        productionCounts.setTextColor(Theme.NORMAL_TEXT_COLOR);
        productionCounts.setVisibility(GONE);
        LinearLayout.LayoutParams productionCountsParams = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        productionCountsParams.setMargins(0, 0, dp(16), 0);
        row1.addView(productionCounts, productionCountsParams);

        InsetBackgroundFactory backgroundFactory1 = InsetBackgroundFactory.builder()
                .backgroundColor(Theme.GHOST_BUTTON_STATES)
                .inset(0)
                .cornerRadius(dp(4))
                .padding(new InsetBackgroundFactory.Padding(dp(2), dp(2), dp(2), dp(2)))
                .build();
        int dp28 = dp(28);
        {
            ImageButton refreshButton = new ImageButton(context);
            backgroundFactory1.applyBackgroundTo(refreshButton);
            Tooltip.attach(refreshButton, I18n.get(MusicHud.MOD_ID + ".button.refresh"));
            refreshButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            var resources = getContext().getResources();
            Image image1 = ImageUtils.getImageFromResource("/assets/music_hud/textures/gui/icons/rotate_cw.png");
            refreshButton.setImageDrawable(new InsetDrawable(new ScaledImageDrawable(resources, image1, dp(12), dp(16)), dp(3)));
            refreshButton.setOnClickListener((v) -> {
                loadData(true);
            });
            row1.addView(refreshButton, new LinearLayout.LayoutParams(dp28, dp28));
        }
        {
            ToggleSubscribeButton toggleSubscribeButton = new ToggleSubscribeButton(context);
            backgroundFactory1.applyBackgroundTo(toggleSubscribeButton);
            row1.addView(toggleSubscribeButton, new LinearLayout.LayoutParams(dp28, dp28, 0));
            var subscribeState = musicService.getArtistSubscribedState(artist);
            toggleSubscribeButton.bindState(subscribeState);
        }

        ScrollView descriptionScrollView = new ScrollView(context);
        artistInfoView.addView(descriptionScrollView,
                new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        description = new TextView(context);
        description.setTextSize(Theme.TEXT_SIZE_NORMAL);
        description.setTextColor(Theme.SECONDARY_TEXT_COLOR);
        description.setLayoutParams(new FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        descriptionScrollView.addView(description);

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
        progressParams.setMargins(0, dp(32), 0, 0);
        addView(progressBar, progressParams);

        PanelStyle.applyDynamically(topBar, dp(BAR_CORNER_RADIUS),
                new InsetBackgroundFactory.Padding(dp(10), dp(8), dp(10), dp(8)),
                InsetBackgroundFactory.BackdropBlur.UI_LAYER);
        FrameLayout.LayoutParams topBarParams = new FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        topBarParams.gravity = Gravity.TOP;
        topBarParams.setMargins(0, dp(BAR_TOP_MARGIN), 0, 0);
        // Added last: its backdrop blur samples the content recorded underneath.
        addView(topBar, topBarParams);

        scrollView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            checkInfiniteScroll(scrollY, scrollView);
        });

        loadData(false);
    }

    /**
     * The list inset equals the space the floating bar occupies, which is only known after measure.
     * Applying it before laying children out keeps the first frame below the bar.
     */
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) topBar.getLayoutParams();
        int inset = lp.topMargin + topBar.getMeasuredHeight();
        if (scrollView.getPaddingTop() != inset) {
            scrollView.setPadding(0, inset, 0, 0);
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
                    int musicCount = artist.getMusicCount();
                    String albumIcon = "\uD83D\uDDB8";
                    String musicIcon = "♫";
                    boolean showMusicCount = musicCount > 0;
                    int albumCount = artist.getAlbumCount();
                    boolean showAlbumCount = albumCount > 0;
                    String string = albumIcon + " " + albumCount + (showMusicCount ? ("  " + musicIcon + " " + musicCount) : "");
                    SpannableString countsString = new SpannableString(string);

                    if (showAlbumCount) {
                        Image albumIconImage = ImageUtils.getImageFromResource("/assets/music_hud/textures/gui/icons/disc_album.png");
                        if (albumIconImage != null) {
                            int start = string.indexOf(albumIcon);
                            if (start >= 0) {
                                countsString.setSpan(ImageUtils.getIconSpan(albumIconImage), start, start + albumIcon.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            }
                        }
                    }
                    if (showMusicCount) {
                        Image listIconImage = ImageUtils.getImageFromResource("/assets/music_hud/textures/gui/icons/list_music.png");
                        if (listIconImage != null) {
                            int start = string.indexOf(musicIcon);
                            if (start >= 0) {
                                countsString.setSpan(ImageUtils.getIconSpan(listIconImage), start, start + musicIcon.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            }
                        }
                    }
                    if (showAlbumCount || showMusicCount) {
                        productionCounts.setText(countsString);
                        productionCounts.setVisibility(VISIBLE);
                    } else {
                        productionCounts.setVisibility(GONE);
                    }
                    description.setText(artist1.getDescription());
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
