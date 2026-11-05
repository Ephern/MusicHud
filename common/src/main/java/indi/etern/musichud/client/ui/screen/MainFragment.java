package indi.etern.musichud.client.ui.screen;

import icyllis.modernui.animation.*;
import icyllis.modernui.annotation.Nullable;
import icyllis.modernui.core.Choreographer;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.graphics.Image;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.mc.ui.ClampingScrollView;
import icyllis.modernui.text.SpannableString;
import icyllis.modernui.text.Spanned;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.*;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.beans.music.Traceable;
import indi.etern.musichud.client.audio.NowPlayingInfo;
import indi.etern.musichud.client.audio.PlaybackTask;
import indi.etern.musichud.client.audio.StreamAudioPlayer;
import indi.etern.musichud.client.dto.LyricLine;
import indi.etern.musichud.client.services.ConnectionManager;
import indi.etern.musichud.client.services.music.MusicService;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.components.*;
import indi.etern.musichud.client.ui.components.buttons.ModifyPlaylistTrackModalButton;
import indi.etern.musichud.client.ui.components.buttons.ToggleTrackLikeStateButton;
import indi.etern.musichud.client.ui.components.buttons.VoteSkipButton;
import indi.etern.musichud.client.ui.lyric.LineBlurRenderer;
import indi.etern.musichud.client.ui.pages.ConfigView;
import indi.etern.musichud.client.ui.pages.HomeView;
import indi.etern.musichud.client.ui.pages.account.AccountBaseView;
import indi.etern.musichud.client.ui.pages.search.SearchView;
import indi.etern.musichud.client.utils.image.ImageUtils;
import indi.etern.musichud.client.utils.ui.Easing;
import indi.etern.musichud.client.utils.ui.InsetBackgroundFactory;
import indi.etern.musichud.client.utils.ui.PanelStyle;
import indi.etern.musichud.client.utils.ui.SpringInterpolator;
import indi.etern.musichud.connection.ConnectionStateMachine;
import indi.etern.musichud.interfaces.ClientConfig;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class MainFragment extends Fragment {
    private static final ClientConfig clientConfig = ClientConfig.getInstance();
    private static final ConnectionManager connectionManager = ConnectionManager.getInstance();
    private static final AtomicInteger progressUpdaterToken = new AtomicInteger(0);
    private static final int SIDE_PANEL_ANIMATION_DURATION = 300;
    private static final SpringInterpolator SIDE_PANEL_SWITCH_INTERPOLATOR =
            new SpringInterpolator((float) SIDE_PANEL_ANIMATION_DURATION / 1000, 1);
    private static final float CARD_COVER_MIN_SCALE = 0.8f;
    private static final int CARD_ANIM_DURATION_MS = 350;
    private static final SpringInterpolator MUSIC_SWITCH_INTERPOLATOR =
            new SpringInterpolator((float) CARD_ANIM_DURATION_MS / 1000, 1);
    private static final DateTimeFormatter formatterWithHour = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter formatterWithoutHour = DateTimeFormatter.ofPattern("mm:ss");
    private static final NowPlayingInfo nowPlayingInfo = NowPlayingInfo.getInstance();
    private static volatile MainFragment instance = null;

    static {
        ConnectionStateMachine.getConnectStatusListeners().add(status -> {
            if (instance != null && instance.visible) {
                MuiModApi.postToUiThread(() -> instance.refreshServerConnectStatus());
            }
        });
    }

    private final NowPlayingInfo playingInfo = NowPlayingInfo.getInstance();
    long progressUpdaterStartTime = 0;
    long progressTextUpdateCount = 0;
    private boolean visible = false;
    @Setter
    private int defaultSelectedIndex = 0;
    private Button switchServerConnectButton;
    private int sideWidth = -1;
    private StaggeredLyricScrollView lyricsScrollView;
    private LinearLayout sidePanel;
    private boolean sidePanelShown = false;
    private AnimatorSet sidePanelAnimator = null;
    private FrameLayout cardWrapper;
    private MusicInfoCard activeCard;
    private MusicInfoCard stagedCard;
    private LinearLayout buttonsLayout;
    private ToggleTrackLikeStateButton likeButton;
    private ModifyPlaylistTrackModalButton addToPlaylistButton;
    private VoteSkipButton skipCurrentButton;
    private AnimatorSet cardAnimator;
    private AnimatorSet coverScaleAnimator;
    private boolean cardSwitching = false;
    private Traceable<MusicDetail> pendingSwitch;
    private MusicDetail displayedDetail;
    private LinearLayout navigatorWrapper;
    @Getter
    private RouterContainer routerContainer;
    private Button sidebarButton;

    private MainFragment() {
    }

    public static void refresh() {
        HomeView homeView = HomeView.getInstance();
        if (homeView != null) {
            homeView.refresh();
        }
        SearchView searchView = SearchView.getInstance();
        if (searchView != null) {
            searchView.refresh();
        }
        AccountBaseView accountBaseView = AccountBaseView.getInstance();
        if (accountBaseView != null) {
            accountBaseView.refresh();
        }
        if (instance != null && instance.visible && instance.activeCard != null) {
            NowPlayingInfo nowPlayingInfo = NowPlayingInfo.getInstance();
            Traceable<MusicDetail> current = nowPlayingInfo.getCurrentlyPlayingMusic();
            Traceable<MusicDetail> nextToPlay = nowPlayingInfo.getNextToPlayMusic();
            Queue<LyricLine> lines = nowPlayingInfo.getLyricLines();
            instance.applyMusic(current, nextToPlay, lines, true);
        }
    }

    public static void switchMusic(Traceable<MusicDetail> musicDetailTrace, Traceable<MusicDetail> nextToPlayTrace, Queue<LyricLine> lines) {
        if (instance != null && instance.visible) {
            instance.applyMusic(musicDetailTrace, nextToPlayTrace, lines, true);
        }
    }

    private static boolean isSameMusic(MusicDetail a, MusicDetail b) {
        MusicDetail normalizedA = a == null ? MusicDetail.NONE : a;
        MusicDetail normalizedB = b == null ? MusicDetail.NONE : b;
        return normalizedA.equals(normalizedB);
    }

    /**
     * The album cover is full size only when a track is actually playing and not muted.
     */
    private static boolean isCoverExpanded() {
        MusicDetail detail = NowPlayingInfo.getInstance().getCurrentlyPlayingMusicDetail();
        if (detail == null || detail.equals(MusicDetail.NONE) || PlaybackTask.getTargetGain() == 0) {
            return false;
        }
        StreamAudioPlayer.Status status = StreamAudioPlayer.getInstance().getStatus();
        return status == StreamAudioPlayer.Status.PLAYING;
    }

    /**
     * Re-applies the cover scale (e.g. after a mute toggle) on the UI thread.
     */
    public static void refreshCoverScale() {
        MainFragment target = instance;
        if (target == null) {
            return;
        }
        try {
            MuiModApi.postToUiThread(() -> {
                if (target.visible && target.activeCard != null) {
                    target.updateCoverScale(true);
                }
            });
        } catch (IllegalStateException ignored) {
            // UI not ready or shutting down
        }
    }

    public static void updateNextToPlay(Traceable<MusicDetail> nextToPlayTrace) {
        if (instance != null && instance.visible) {
            HomeView homeView = HomeView.getInstance();
            if (homeView != null) {
                homeView.updateNextToPlay(nextToPlayTrace);
            }
        }
    }

    public static void refreshLyricViews() {
        HomeView homeView = HomeView.getInstance();
        if (homeView != null) {
            StaggeredLyricScrollView staggeredLyricScrollView = homeView.getStaggeredLyricScrollView();
            if (staggeredLyricScrollView != null) {
                MuiModApi.postToUiThread(staggeredLyricScrollView::refreshLinesStyle);
            }
        }
        if (instance != null && instance.visible && instance.lyricsScrollView != null) {
            MuiModApi.postToUiThread(instance.lyricsScrollView::refreshLinesStyle);
        }
    }

    public static void refreshLyricsSidebarVisibility() {
        if (instance != null && instance.visible) {
            instance.updateSidePanelVisibility();
        }
    }

    public static void refreshPanelStyle() {
        MainFragment target = instance;
        if (target == null) {
            return;
        }
        try {
            MuiModApi.postToUiThread(() -> {
                if (target.visible && target.sidePanel != null) {
                    target.applyPanelBackground();
                }
            });
        } catch (IllegalStateException ignored) {
            // UI not ready or shutting down
        }
    }

    public static @NonNull MainFragment getInstance() {
        if (instance == null) {
            synchronized (MainFragment.class) {
                if (instance == null) {
                    instance = new MainFragment();
                }
            }
        }
        return instance;
    }

    private static boolean hasMusic() {
        MusicDetail detail = nowPlayingInfo.getCurrentlyPlayingMusicDetail();
        return detail != null && !detail.equals(MusicDetail.NONE);
    }

    private static boolean notAbsoluteMusicLyric() {
        Queue<LyricLine> lines = nowPlayingInfo.getLyricLines();
        return lines != null && !lines.isEmpty()
                && lines.stream().filter(l -> l.getType() == LyricLine.Type.NORMAL).count() > 1;
    }

    private void applyPanelBackground() {
        if (sidePanel == null) {
            return;
        }
        {
            int dp12 = sidePanel.dp(12);
            var padding = new InsetBackgroundFactory.Padding(dp12, 0, dp12, dp12);
            InsetBackgroundFactory factory = clientConfig.getEnableSidebarBackground()
                    ? PanelStyle.factory(sidePanel,
                    clientConfig.getPanelOpacity(), clientConfig.getPanelBrightness(),
                    sidePanel.dp(16), padding)
                    : PanelStyle.transparentFactory(sidePanel.dp(16), padding);
            factory.applyBackgroundTo(sidePanel);
        }
        {
            int dp2 = navigatorWrapper.dp(2);
            var padding = new InsetBackgroundFactory.Padding(dp2, dp2, dp2, dp2);
            InsetBackgroundFactory factory = clientConfig.getEnableSidebarBackground()
                    ? PanelStyle.factory(navigatorWrapper,
                    clientConfig.getPanelOpacity(), clientConfig.getPanelBrightness(),
                    navigatorWrapper.dp(16), padding, InsetBackgroundFactory.BackdropBlur.UI_LAYER)
                    : PanelStyle.transparentFactory(navigatorWrapper.dp(16), padding);
            factory.applyBackgroundTo(navigatorWrapper);
        }
    }

    private void applyMusic(Traceable<MusicDetail> musicDetailTrace, Traceable<MusicDetail> nextToPlayTrace,
                            Queue<LyricLine> lines, boolean animate) {
        MusicDetail musicDetail = musicDetailTrace == null ? MusicDetail.NONE : musicDetailTrace.value();
        if (musicDetail != null && !musicDetail.equals(MusicDetail.NONE)) {
            startProgressUpdater(musicDetail);
        }
        if (!animate || activeCard == null || stagedCard == null) {
            // Initial display (or cards not built yet): bind in place without animating.
            if (activeCard != null) {
                activeCard.bind(musicDetailTrace, sideWidth);
                displayedDetail = musicDetail;
                updateCoverScale(false);
            }
        } else if (cardSwitching) {
            // Keep only the newest request; it plays once the running transition settles.
            pendingSwitch = musicDetailTrace;
        } else if (isSameMusic(displayedDetail, musicDetail)) {
            // Same track re-synced (startAt / reconnect / config refresh): refresh contents in place.
            activeCard.bind(musicDetailTrace, sideWidth);
            displayedDetail = musicDetail;
        } else {
            startCardSwitch(musicDetailTrace);
        }
        HomeView homeView = HomeView.getInstance();
        if (homeView != null) {
            homeView.switchMusic(musicDetailTrace, nextToPlayTrace, lines);
        }
        if (lyricsScrollView != null) {
            if (notAbsoluteMusicLyric()) {
                lyricsScrollView.switchLyrics(musicDetail, lines);
            } else {
                lyricsScrollView.clearLyrics();
            }
        }
        updateActionButtons(displayedDetail);
        updateSidePanelVisibility();
    }

    /**
     * Binds the sidebar action row to the track that is actually displayed.
     */
    private void updateActionButtons(MusicDetail detail) {
        if (buttonsLayout == null) {
            return;
        }
        if (detail == null || detail.equals(MusicDetail.NONE)) {
            buttonsLayout.setVisibility(View.GONE);
            likeButton.bindMusicList(null);
            addToPlaylistButton.bindMusicDetail(null);
            return;
        }
        skipCurrentButton.reset();
        likeButton.bindMusicList(MusicService.getInstance().getMusicTrackState(detail).currentUsersLikeList());
        addToPlaylistButton.bindMusicDetail(detail);
        buttonsLayout.setVisibility(View.VISIBLE);
    }

    /**
     * Starts the card-style right-to-left switch: the active card slides out to the left while the
     * staged card slides in from the right. Both cards move in parallel sharing one spring so the
     * seam never gaps; album covers scale uniformly between {@link #CARD_COVER_MIN_SCALE} and 1.
     */
    private void startCardSwitch(Traceable<MusicDetail> musicDetailTrace) {
        int width = sideWidth;
        cardSwitching = true;
        if (coverScaleAnimator != null) {
            coverScaleAnimator.cancel();
            coverScaleAnimator = null;
        }
        float targetCoverScale = isCoverExpanded() ? 1 : CARD_COVER_MIN_SCALE;

        // Prepare the incoming card off-screen right; visible before bind so image lazy-load triggers.
        stagedCard.setVisibility(View.VISIBLE);
        stagedCard.setTranslationX(width);
        stagedCard.setAlpha(0f);
        UrlImageView activeAlbumImageView = activeCard.getAlbumImage();
        UrlImageView stagedAlbumImageView = stagedCard.getAlbumImage();
        stagedAlbumImageView.setScaleX(CARD_COVER_MIN_SCALE);
        stagedAlbumImageView.setScaleY(CARD_COVER_MIN_SCALE);
        stagedCard.bind(musicDetailTrace, width);

        ObjectAnimator outX = ObjectAnimator.ofFloat(activeCard, View.TRANSLATION_X, 0f, -width);
        ObjectAnimator outA = ObjectAnimator.ofFloat(activeCard, View.ALPHA, 1f, 0f);
        ObjectAnimator outSx = ObjectAnimator.ofFloat(activeAlbumImageView, View.SCALE_X, activeAlbumImageView.getScaleX(), CARD_COVER_MIN_SCALE);
        ObjectAnimator outSy = ObjectAnimator.ofFloat(activeAlbumImageView, View.SCALE_Y, activeAlbumImageView.getScaleY(), CARD_COVER_MIN_SCALE);
        ObjectAnimator inX = ObjectAnimator.ofFloat(stagedCard, View.TRANSLATION_X, width, 0f);
        ObjectAnimator inA = ObjectAnimator.ofFloat(stagedCard, View.ALPHA, 0f, 1f);
        ObjectAnimator inSx = ObjectAnimator.ofFloat(stagedAlbumImageView, View.SCALE_X, CARD_COVER_MIN_SCALE, targetCoverScale);
        ObjectAnimator inSy = ObjectAnimator.ofFloat(stagedAlbumImageView, View.SCALE_Y, CARD_COVER_MIN_SCALE, targetCoverScale);
        List<ObjectAnimator> animators = List.of(outX, outA, outSx, outSy, inX, inA, inSx, inSy);
        for (ObjectAnimator animator : animators) {
            animator.setInterpolator(MUSIC_SWITCH_INTERPOLATOR);
            animator.setDuration(CARD_ANIM_DURATION_MS);
        }

        AnimatorSet set = new AnimatorSet();
        set.playTogether(outX, outA, outSx, outSy, inX, inA, inSx, inSy);
        final MusicDetail newDetail = musicDetailTrace == null ? null : musicDetailTrace.value();
        final AnimatorSet thisAnimator = set;
        cardAnimator = set;
        set.addListener(new AnimatorListener() {
            @Override
            public void onAnimationEnd(@NonNull Animator animation) {
                // Stale callbacks (canceled / replaced) must not settle the newer transition.
                if (cardAnimator != thisAnimator) {
                    return;
                }
                settleCardSwitch(newDetail);
            }
        });
        set.start();
    }

    private void settleCardSwitch(MusicDetail newDetail) {
        cardAnimator = null;

        MusicInfoCard oldCard = activeCard;
        oldCard.setVisibility(View.INVISIBLE);
        oldCard.setTranslationX(0f);
        oldCard.setAlpha(0f);
        oldCard.getAlbumImage().setScaleX(1f);
        oldCard.getAlbumImage().setScaleY(1f);

        activeCard = stagedCard;
        stagedCard = oldCard;
        activeCard.setTranslationX(0f);
        activeCard.setAlpha(1f);
        activeCard.setVisibility(View.VISIBLE);
        displayedDetail = newDetail;
        cardSwitching = false;
        // Recycle the outgoing card immediately so no stale content leaks into its next slide-in.
        stagedCard.clear();
        updateCoverScale(true);

        // Drain the latest-wins slot, if any switch arrived while animating.
        if (pendingSwitch != null) {
            Traceable<MusicDetail> pending = pendingSwitch;
            pendingSwitch = null;
            MusicDetail pendingDetail = pending.value();
            if (isSameMusic(displayedDetail, pendingDetail)) {
                activeCard.bind(pending, sideWidth);
                displayedDetail = pendingDetail;
            } else {
                startCardSwitch(pending);
            }
        }
        updateActionButtons(displayedDetail);
    }

    /**
     * Moves the active cover between {@link #CARD_COVER_MIN_SCALE} and 1 to match playback state.
     */
    private void updateCoverScale(boolean animate) {
        if (activeCard == null) {
            return;
        }
        View cover = activeCard.getAlbumImage();
        float target = isCoverExpanded() ? 1f : CARD_COVER_MIN_SCALE;
        if (!animate) {
            cover.setScaleX(target);
            cover.setScaleY(target);
            return;
        }
        // A card transition owns the cover scale while it runs.
        if (cardSwitching || (cover.getScaleX() == target && cover.getScaleY() == target)) {
            return;
        }
        if (coverScaleAnimator != null) {
            coverScaleAnimator.cancel();
        }
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(cover, View.SCALE_X, target);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(cover, View.SCALE_Y, target);
        scaleX.setInterpolator(MUSIC_SWITCH_INTERPOLATOR);
        scaleY.setInterpolator(MUSIC_SWITCH_INTERPOLATOR);
        scaleX.setDuration(CARD_ANIM_DURATION_MS);
        scaleY.setDuration(CARD_ANIM_DURATION_MS);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(scaleX, scaleY);
        coverScaleAnimator = set;
        set.start();
    }

    private void startProgressUpdater(MusicDetail musicDetail) {
        int token = progressUpdaterToken.incrementAndGet();
        // The volatile field may be nulled by a concurrent switch-to-idle/stop between the UI task
        // being posted and running, so fall back to the track's own duration instead of NPE-ing.
        Duration musicDuration = nowPlayingInfo.getMusicDuration();
        if (musicDuration == null) {
            musicDuration = Duration.ofMillis(musicDetail.getDurationMillis());
        }
        DateTimeFormatter formatter = musicDuration.toHoursPart() >= 1 ?
                formatterWithHour :
                formatterWithoutHour;
        // 兜底退出：startAt 可能因任务被取代/失败永不触发，超时后结束进度循环防泄漏
        long currentTimeMillis = System.currentTimeMillis();
        long deadline = currentTimeMillis + musicDuration.toMillis() + 120_000;
        progressUpdaterStartTime = currentTimeMillis;
        progressTextUpdateCount = -1;
        postFrameCallback(musicDetail, formatter, deadline, token);
    }

    private void postFrameCallback(MusicDetail musicDetail, DateTimeFormatter formatter, long deadline, int token) {
        Choreographer.getInstance().postFrameCallback((choreographer, frameTimeNanos) -> {
            if (progressUpdaterToken.get() != token) return;
            MusicInfoCard card = activeCard;
            if (card != null) {
                long currentTimeMillis = System.currentTimeMillis();
                long count = (currentTimeMillis - progressUpdaterStartTime) / 1000;
                if (count > progressTextUpdateCount) {
                    progressTextUpdateCount = count;
                    Duration playedDuration = nowPlayingInfo.getPlayedDuration();
                    String playedTimeString = formatter.format(LocalTime.MIDNIGHT.plusSeconds(playedDuration.toSeconds()));
                    card.getPlayedTimeText().setText(playedTimeString);
                }
                card.getProgressBar().setProgress((int) (nowPlayingInfo.getProgressRate() * instance.sideWidth));
                if (musicDetail.equals(nowPlayingInfo.getCurrentlyPlayingMusicDetail())
                        && nowPlayingInfo.getProgressRate() < 1
                        && System.currentTimeMillis() < deadline) {
                    postFrameCallback(musicDetail, formatter, deadline, token);
                }
            }
        });
    }

    private void reset() {
        visible = false;
        defaultSelectedIndex = 0;
        sidePanelShown = false;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable DataSet savedInstanceState) {
        try {
            visible = true;
            var context = requireContext();
            var base = new FrameLayout(context);
            int dp24 = base.dp(24);
            int dp12 = base.dp(12);
            base.setLayoutParams(new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));

            // Everything except the floating control bar keeps its original row arrangement.
            var contentRow = new LinearLayout(context);
            contentRow.setOrientation(LinearLayout.HORIZONTAL);
            contentRow.setPadding(0, 0, dp24, 0);
            base.addView(contentRow, new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));

            routerContainer = new RouterContainer(context);

            routerContainer.setAnimationStyle(RouterContainer.AnimationStyle.SCALE_FADE_ROOT);
            routerContainer.setAnimationDuration(300);

            {
                LinearLayout controlBar = new LinearLayout(context);
                var params = new FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
                params.gravity = Gravity.TOP | Gravity.START;
                params.setMargins(dp12, dp12, 0, 0);
                // Added after the content row so its blur can sample what is painted underneath.
                base.addView(controlBar, params);

                {
                    navigatorWrapper = new LinearLayout(context);
                    navigatorWrapper.setOrientation(LinearLayout.HORIZONTAL);
                    controlBar.addView(navigatorWrapper, new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
                    InsetBackgroundFactory factory = InsetBackgroundFactory.builder()
                            .padding(new InsetBackgroundFactory.Padding(controlBar.dp(2), controlBar.dp(1), controlBar.dp(2), controlBar.dp(1)))
                            .cornerRadius(controlBar.dp(14)).inset(controlBar.dp(1)).build();
                    {
                        sidebarButton = new Button(context) {
                            @Override
                            protected boolean dispatchHoverEvent(@NotNull MotionEvent event) {
                                Tooltip.handleHover(this, event);
                                return super.dispatchHoverEvent(event);
                            }
                        };
                        sidebarButton.setTextSize(Theme.TEXT_SIZE_LARGE);
                        sidebarButton.setTextColor(Theme.NORMAL_TEXT_COLOR);
                        sidebarButton.setGravity(Gravity.CENTER);
                        factory.applyBackgroundTo(sidebarButton);
                        sidebarButton.setOnClickListener(b -> {

                        });
                        sidebarButton.setWidth(sidebarButton.dp(40));
                        sidebarButton.setHeight(sidebarButton.dp(40));

                        navigatorWrapper.addView(sidebarButton, new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
                        refreshSidebarStatus();
                    }
                    {
                        View divider = new View(context);
                        ShapeDrawable dividerBg = new ShapeDrawable();
                        dividerBg.setShape(ShapeDrawable.VLINE);
                        dividerBg.setSize(divider.dp(1), divider.dp(1));
                        dividerBg.setColor(Theme.GHOST_BUTTON_BACKGROUND_HOVERED);
                        divider.setBackground(dividerBg);
                        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(divider.dp(1), MATCH_PARENT);
                        dividerParams.setMargins(divider.dp(2), divider.dp(8), divider.dp(2), divider.dp(8));
                        navigatorWrapper.addView(divider, dividerParams);
                    }
                    {
                        NavigatorWidget navigator = new NavigatorWidget(context, routerContainer);
                        if (Minecraft.getInstance().player != null) {//in game
                            var homeNav = navigator.createNavigationPage("Home", "/assets/music_hud/textures/gui/icons/house.png",
                                    I18n.get(MusicHud.MOD_ID + ".text.page.home"), HomeView::new);
                            var searchNav = navigator.createNavigationPage("Search", "/assets/music_hud/textures/gui/icons/search.png",
                                    I18n.get(MusicHud.MOD_ID + ".text.page.search"), SearchView::new);
                            var accountNav = navigator.createNavigationPage("Account", "/assets/music_hud/textures/gui/icons/square_user_round.png",
                                    I18n.get(MusicHud.MOD_ID + ".text.page.account"), AccountBaseView::new);
                            var settingsNav = navigator.createNavigationPage("Settings", "/assets/music_hud/textures/gui/icons/settings.png",
                                    I18n.get(MusicHud.MOD_ID + ".text.page.setting"), ConfigView::new);
                            NavigatorWidget.NavigationMeta defaultMeta = List.of(homeNav, searchNav, accountNav, settingsNav).get(defaultSelectedIndex);
                            defaultMeta.select();
                        } else {
                            var settingsNav = navigator.createNavigationPage("Settings", "/assets/music_hud/textures/gui/icons/settings.png",
                                    I18n.get(MusicHud.MOD_ID + ".text.page.setting"), ConfigView::new);
                            settingsNav.select();
                        }
                        navigatorWrapper.addView(navigator, new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
                    }
                    {
                        View divider = new View(context);
                        ShapeDrawable dividerBg = new ShapeDrawable();
                        dividerBg.setShape(ShapeDrawable.VLINE);
                        dividerBg.setSize(divider.dp(1), divider.dp(1));
                        dividerBg.setColor(Theme.GHOST_BUTTON_BACKGROUND_HOVERED);
                        divider.setBackground(dividerBg);
                        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(divider.dp(1), MATCH_PARENT);
                        dividerParams.setMargins(divider.dp(2), divider.dp(8), divider.dp(2), divider.dp(8));
                        navigatorWrapper.addView(divider, dividerParams);
                    }
                    {
                        switchServerConnectButton = new Button(context) {
                            @Override
                            protected boolean dispatchHoverEvent(MotionEvent event) {
                                Tooltip.handleHover(this, event);
                                return super.dispatchHoverEvent(event);
                            }
                        };
                        switchServerConnectButton.setTextSize(Theme.TEXT_SIZE_LARGE);
                        switchServerConnectButton.setTextColor(Theme.NORMAL_TEXT_COLOR);
                        switchServerConnectButton.setGravity(Gravity.CENTER);
                        factory.applyBackgroundTo(switchServerConnectButton);
                        switchServerConnectButton.setOnClickListener(b -> connectionManager.toggleConnection());
                        switchServerConnectButton.setWidth(switchServerConnectButton.dp(40));
                        switchServerConnectButton.setHeight(switchServerConnectButton.dp(40));

                        navigatorWrapper.addView(switchServerConnectButton, new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
                        refreshServerConnectStatus();
                    }
                }
            }
            {
                var side = new LinearLayout(context);
                side.setOrientation(LinearLayout.VERTICAL);
                sidePanel = side;
                // Freshly created views are visible; the first sync below decides the real state.
                sidePanelShown = true;
                applyPanelBackground();
                LinearLayout.LayoutParams params1 = new LinearLayout.LayoutParams(WRAP_CONTENT, MATCH_PARENT);
                params1.setMargins(dp12, sidePanel.dp(64), dp12, dp12);
                contentRow.addView(side, params1);

                LayoutTransition panelTransition = new LayoutTransition();
                panelTransition.enableTransitionType(LayoutTransition.CHANGING);
                side.setLayoutTransition(panelTransition);

                //noinspection UnstableApiUsage
                var sideScrollView = new ClampingScrollView(context);
                sideScrollView.setVerticalScrollBarEnabled(false);
                sideScrollView.setHorizontalScrollBarEnabled(false);
                side.addView(sideScrollView, new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, 0));

                var sideContent = new LinearLayout(context);
                sideContent.setOrientation(LinearLayout.VERTICAL);

                sideContent.addView(new View(context), new LinearLayout.LayoutParams(MATCH_PARENT, dp12));

                sideWidth = base.dp(231);

                // Merged album cover + info card, double-buffered for right-to-left switching.
                // (ViewGroup clips children by default, so off-screen cards are cut at the wrapper bounds.)
                cardWrapper = new FrameLayout(context);
                activeCard = new MusicInfoCard(context, sideWidth);
                stagedCard = new MusicInfoCard(context, sideWidth);
                cardWrapper.addView(activeCard, new FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
                cardWrapper.addView(stagedCard, new FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
                stagedCard.setVisibility(View.INVISIBLE);
                stagedCard.setTranslationX(sideWidth);
                stagedCard.setAlpha(0f);
                stagedCard.getAlbumImage().setScaleX(CARD_COVER_MIN_SCALE);
                stagedCard.getAlbumImage().setScaleY(CARD_COVER_MIN_SCALE);
                sideContent.addView(cardWrapper, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

                LinearLayout actionsRow = new LinearLayout(context);
                actionsRow.setOrientation(LinearLayout.HORIZONTAL);
                actionsRow.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
                sideContent.addView(actionsRow, actionsParams);

                buttonsLayout = new LinearLayout(context);
                buttonsLayout.setOrientation(LinearLayout.HORIZONTAL);
                buttonsLayout.setGravity(Gravity.START);
                actionsRow.addView(buttonsLayout, new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
                {
                    InsetBackgroundFactory buttonsBackground = InsetBackgroundFactory.builder()
                            .backgroundColor(Theme.GHOST_BUTTON_STATES)
                            .padding(new InsetBackgroundFactory.Padding(base.dp(2), base.dp(1), base.dp(2), base.dp(1)))
                            .cornerRadius(base.dp(4)).build();
                    likeButton = new ToggleTrackLikeStateButton(context);
                    buttonsBackground.applyBackgroundTo(likeButton);
                    int buttonSize = base.dp(36);
                    buttonsLayout.addView(likeButton, new LinearLayout.LayoutParams(buttonSize, buttonSize));
                    addToPlaylistButton = new ModifyPlaylistTrackModalButton(context);
                    buttonsBackground.applyBackgroundTo(addToPlaylistButton);
                    buttonsLayout.addView(addToPlaylistButton, new LinearLayout.LayoutParams(buttonSize, buttonSize));
                    skipCurrentButton = new VoteSkipButton(context);
                    buttonsBackground.applyBackgroundTo(skipCurrentButton);
                    buttonsLayout.addView(skipCurrentButton, new LinearLayout.LayoutParams(buttonSize, buttonSize));
                }
                buttonsLayout.setVisibility(View.GONE);

                // Lyrics live under the card in the same panel; hiding them gives the card their space.
                lyricsScrollView = new StaggeredLyricScrollView(context);
                lyricsScrollView.setDrawFadeEdgesWhenManualScroll(true);
                lyricsScrollView.setLyricFollowOffset(lyricsScrollView.dp(36));
                lyricsScrollView.setMainLyricSize(Theme.SIDEBAR_MAIN_LYRIC_SIZE);
                lyricsScrollView.setSubLyricSize(Theme.SIDEBAR_SUB_LYRIC_SIZE);
                int blurPadding = Math.max(0, LineBlurRenderer.maxPaddingFor(base.dp(StaggeredLyricScrollView.MAX_BLUR_DP)));
                LinearLayout.LayoutParams lyricsParams = new LinearLayout.LayoutParams(MATCH_PARENT, 0, 1);
                lyricsParams.setMargins(-blurPadding, -lyricsScrollView.dp(8), -blurPadding, 0);
                side.addView(lyricsScrollView, lyricsParams);

                StreamAudioPlayer streamAudioPlayer = StreamAudioPlayer.getInstance();
                StreamAudioPlayer.Status status = streamAudioPlayer.getStatus();
                checkAudioPlayerStatus(status);
                Consumer<StreamAudioPlayer.Status> statusListener = newStatus -> MuiModApi.postToUiThread(
                        () -> checkAudioPlayerStatus(newStatus)
                );
                streamAudioPlayer.getStatusChangeListener().add(statusListener);
                base.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                    @Override
                    public void onViewAttachedToWindow(View v) {
                    }

                    @Override
                    public void onViewDetachedFromWindow(View v) {
                        streamAudioPlayer.getStatusChangeListener().remove(statusListener);
                    }
                });

                var sideParams = new LinearLayout.LayoutParams(sideWidth, MATCH_PARENT);
                sideScrollView.addView(sideContent, sideParams);

//                LinearLayout serverConnectPanel = new LinearLayout(context);
//                serverConnectPanel.setOrientation(LinearLayout.VERTICAL);
//                serverConnectPanel.setGravity(Gravity.CENTER_VERTICAL);

//                serverConnectPanel.addView(switchServerConnectButton, new LinearLayout.LayoutParams(MATCH_PARENT, base.dp(36)));
//                refreshServerConnectStatus();
//                LinearLayout.LayoutParams params4 = new LinearLayout.LayoutParams(sideWidth, WRAP_CONTENT);
//                params4.setMargins(0, serverConnectPanel.dp(8), 0, 0);
//                side.addView(serverConnectPanel, params4);

                LayoutTransition transition2 = new LayoutTransition();
                transition2.enableTransitionType(LayoutTransition.CHANGING);
                sideContent.setLayoutTransition(transition2);

//                LayoutTransition transition3 = new LayoutTransition();
//                transition3.disableTransitionType(LayoutTransition.DISAPPEARING);
//                transition3.disableTransitionType(LayoutTransition.APPEARING);
//                transition3.enableTransitionType(LayoutTransition.CHANGING);
//                serverConnectPanel.setLayoutTransition(transition3);

                NowPlayingInfo nowPlayingInfo = NowPlayingInfo.getInstance();
                Traceable<MusicDetail> currentlyPlaying = nowPlayingInfo.getCurrentlyPlayingMusic();
                Traceable<MusicDetail> nextToPlay = nowPlayingInfo.getNextToPlayMusic();

                // Seed both cards without animating: the active one shows current playback,
                // the staged one shows idle so both measure to a stable height.
                stagedCard.bind(Traceable.of(MusicDetail.NONE), sideWidth);
                applyMusic(currentlyPlaying, nextToPlay, playingInfo.getLyricLines(), false);
            }

            routerContainer.setOnPageChangeListener(new RouterContainer.OnPageChangeListener() {
                @Override
                public void onPageChangeStart(@Nullable String fromKey, @NonNull String toKey) {
                    // Only a Home transition changes the side panel's width, so serialize those.
                    boolean sidePanelChanges = ("Home".equals(fromKey) || "Home".equals(toKey)) && hasMusic();
                    routerContainer.setTransitionType(
                            sidePanelChanges ? RouterContainer.TransitionType.SERIAL : RouterContainer.TransitionType.CROSS
                    );
                }

                @Override
                public void onPageChangeEnd(@NonNull String pageKey) {
                    // Late safety net: the panel may have been built after the initial navigation.
                    updateSidePanelVisibility();
                    refreshSidebarStatus();
                }

                @Override
                public void onTransitionStart(@Nullable String fromKey, @NonNull String toKey,
                                              @NonNull RouterContainer.TransitionType type) {
                    if ("Home".equals(toKey)) {
                        HomeView homeView = HomeView.getInstance();
                        if (homeView != null) {
                            StaggeredLyricScrollView scrollView = homeView.getStaggeredLyricScrollView();
                            if (scrollView != null) {
                                // HomeView is created lazily, so lyrics switched while another page was
                                // shown were dropped. Push the current state before reinitializing; the
                                // instance-based check avoids replaying the slide-in for the same content.
                                MusicDetail currentMusic = playingInfo.getCurrentlyPlayingMusicDetail();
                                Queue<LyricLine> currentLyrics = playingInfo.getLyricLines();
                                if (!scrollView.isShowing(currentMusic, currentLyrics)) {
                                    scrollView.switchLyrics(currentMusic == null ? MusicDetail.NONE : currentMusic, currentLyrics);
                                }
                                scrollView.reinitialize();
                            }
                        }
                        // The very first page load has no settled panel to animate out.
                        hideSidePanel(fromKey != null);
                    }
                }

                @Override
                public void onBeforeSwap(@Nullable String fromKey, @NonNull String toKey,
                                         @NonNull RouterContainer.TransitionType type) {
                    if (!"Home".equals(toKey)) {
                        HomeView homeView = HomeView.getInstance();
                        if (homeView != null) {
                            StaggeredLyricScrollView scrollView = homeView.getStaggeredLyricScrollView();
                            if (scrollView != null) {
                                scrollView.suspendLyricFollowingAndHide();
                            }
                        }
                    }
                    syncSidePanelBeforeSwap();
                }
            });

            var params = new LinearLayout.LayoutParams(0, MATCH_PARENT, 1);
            params.setMargins(routerContainer.dp(40), 0, 0, 0);
            contentRow.addView(routerContainer, params);

            var root = new FrameLayout(context);
            root.setLayoutParams(new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
            root.addView(new MinecraftBackgroundView(context),
                    new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
            root.addView(base, new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
            return root;
        } catch (Exception e) {
            visible = false;
            throw e;
        }
    }

    private boolean shouldShowSidePanel() {
        if (!hasMusic()) {
            return false;
        }
        // Use the router of this view: the static instance is only set once attached, and the
        // initial navigation (e.g. to Home) already happened while building the view.
        return routerContainer == null || !"Home".equals(routerContainer.getEffectivePageKey());
    }

    private void updateSidePanelVisibility() {
        if (sidePanel == null) {
            return;
        }
        if (shouldShowSidePanel()) {
            showSidePanel(true);
        } else {
            hideSidePanel(true);
        }
        updateLyricsVisibility();
    }

    /**
     * Frame-aligned variant for the router's swap hook: the incoming page must be laid out at its
     * final size, so a hide is applied immediately instead of sliding out into a reflow.
     */
    private void syncSidePanelBeforeSwap() {
        if (sidePanel == null) {
            return;
        }
        if (shouldShowSidePanel()) {
            showSidePanel(true);
        } else {
            snapSidePanelHidden();
        }
        updateLyricsVisibility();
    }

    private void updateLyricsVisibility() {
        if (lyricsScrollView == null) {
            return;
        }
        boolean show = clientConfig.getEnableLyricsSidebar() && notAbsoluteMusicLyric();
        boolean visible = lyricsScrollView.getVisibility() == View.VISIBLE;
        if (show == visible) {
            return;
        }
        if (show) {
            lyricsScrollView.setVisibility(View.VISIBLE);
            lyricsScrollView.reinitialize();
        } else {
            lyricsScrollView.suspendLyricFollowingAndHide();
            lyricsScrollView.setVisibility(View.GONE);
        }
    }

    private void showSidePanel(boolean animate) {
        if (sidePanel == null || sidePanelShown) {
            return;
        }
        if (!shouldShowSidePanel()) {
            return;
        }
        sidePanelShown = true;
        if (sidePanelAnimator != null) {
            sidePanelAnimator.cancel();
            sidePanelAnimator = null;
        }
        sidePanel.setVisibility(View.VISIBLE);
        if (!animate) {
            sidePanel.setTranslationX(0f);
            sidePanel.setAlpha(1f);
        } else {
            float distance = sidePanelSlideDistance();
            sidePanel.setTranslationX(-distance);
            sidePanel.setAlpha(0f);
            ObjectAnimator slideIn = ObjectAnimator.ofFloat(sidePanel, View.TRANSLATION_X, -distance, 0f);
            slideIn.setDuration(SIDE_PANEL_ANIMATION_DURATION);
            slideIn.setInterpolator(SIDE_PANEL_SWITCH_INTERPOLATOR);
            ObjectAnimator fadeIn = ObjectAnimator.ofFloat(sidePanel, View.ALPHA, 0f, 1f);
            fadeIn.setDuration(SIDE_PANEL_ANIMATION_DURATION);
            fadeIn.setInterpolator(SIDE_PANEL_SWITCH_INTERPOLATOR);
            sidePanelAnimator = new AnimatorSet();
            sidePanelAnimator.playTogether(slideIn, fadeIn);
            sidePanelAnimator.start();
        }
        if (lyricsScrollView != null && lyricsScrollView.getVisibility() == View.VISIBLE) {
            lyricsScrollView.reinitialize();
        }
    }

    /**
     * A panel hidden before its first layout has no measured width; fall back to the content width
     * so the very first show still slides instead of popping in.
     */
    private float sidePanelSlideDistance() {
        int width = sidePanel.getWidth();
        return width > 0 ? width : sideWidth;
    }

    private void hideSidePanel(boolean animate) {
        if (sidePanel == null || !sidePanelShown) {
            return;
        }
        if (!animate || sidePanel.getWidth() <= 0) {
            snapSidePanelHidden();
            return;
        }
        sidePanelShown = false;
        if (sidePanelAnimator != null) {
            sidePanelAnimator.cancel();
            sidePanelAnimator = null;
        }
        if (lyricsScrollView != null) {
            lyricsScrollView.suspendLyricFollowingAndHide();
        }
        float distance = sidePanel.getWidth();
        ObjectAnimator slideOut = ObjectAnimator.ofFloat(sidePanel, View.TRANSLATION_X,
                sidePanel.getTranslationX(), -distance);
        slideOut.setDuration(SIDE_PANEL_ANIMATION_DURATION);
        slideOut.setInterpolator(Easing.EASE_IN_QUINT);
        ObjectAnimator fadeOut = ObjectAnimator.ofFloat(sidePanel, View.ALPHA, sidePanel.getAlpha(), 0f);
        fadeOut.setDuration(SIDE_PANEL_ANIMATION_DURATION);
        fadeOut.setInterpolator(Easing.EASE_IN_OUT_CUBIC);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(slideOut, fadeOut);
        AnimatorSet thisAnimator = set;
        sidePanelAnimator = set;
        set.addListener(new AnimatorListener() {
            @Override
            public void onAnimationEnd(@NonNull Animator animation) {
                // Stale callbacks must not hide a panel that a newer show() already revived.
                if (sidePanelAnimator != thisAnimator) {
                    return;
                }
                sidePanelAnimator = null;
                sidePanel.setVisibility(View.GONE);
                sidePanel.setTranslationX(0f);
                sidePanel.setAlpha(1f);
            }
        });
        set.start();
    }

    /**
     * Hides the panel without animating, finishing any running hide so the row reflows now.
     */
    private void snapSidePanelHidden() {
        if (sidePanel == null) {
            return;
        }
        sidePanelShown = false;
        if (lyricsScrollView != null) {
            lyricsScrollView.suspendLyricFollowingAndHide();
        }
        if (sidePanelAnimator != null) {
            AnimatorSet running = sidePanelAnimator;
            // Neutralise the end callback before cancelling so it cannot re-apply its own state.
            sidePanelAnimator = null;
            running.cancel();
        }
        sidePanel.setTranslationX(0f);
        sidePanel.setAlpha(1f);
        sidePanel.setVisibility(View.GONE);
    }

    private void refreshSidebarStatus() {
        sidebarButton.setVisibility(View.VISIBLE);
        String buttonText = "";
        String icon = "";
        if ("Home".equals(routerContainer.getCurrentPageKey())) {
            icon = "/assets/music_hud/textures/gui/icons/square_library.png";
            buttonText = I18n.get(MusicHud.MOD_ID + ".text.sidebar.openLibrary");
            Tooltip.attach(sidebarButton, buttonText);
        } else {
            icon = "/assets/music_hud/textures/gui/icons/turntable.png";
            buttonText = I18n.get(MusicHud.MOD_ID + ".text.sidebar.openPlayer");
            Tooltip.attach(sidebarButton, buttonText);
        }
        SpannableString string = new SpannableString(" ");
        Image imageFromResource = ImageUtils.getImageFromResource(icon);
        if (imageFromResource != null) {
            string.setSpan(ImageUtils.getIconSpan(imageFromResource), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        sidebarButton.setText(string);
    }

    private void refreshServerConnectStatus() {
        if (Minecraft.getInstance().player != null) {
            switchServerConnectButton.setVisibility(View.VISIBLE);
            boolean singlePlayer = Minecraft.getInstance().getCurrentServer() == null;
            String buttonText = "";
            String icon = "";
            switch (ConnectionStateMachine.getConnectStatus()) {
                case CONNECTED -> {
                    icon = "/assets/music_hud/textures/gui/icons/link.png";
                    if (singlePlayer) {
                        buttonText = I18n.get(MusicHud.MOD_ID + ".text.connected.integrated");
                        switchServerConnectButton.setEnabled(false);
                        Tooltip.attach(switchServerConnectButton, buttonText);
                    } else {
                        buttonText = I18n.get(MusicHud.MOD_ID + ".text.connected");
                        switchServerConnectButton.setEnabled(true);
                        Tooltip.attach(switchServerConnectButton, buttonText + "\n" + I18n.get(MusicHud.MOD_ID + ".button.disconnect"));
                    }
                }
                case NOT_CONNECTED -> {
                    icon = "/assets/music_hud/textures/gui/icons/unlink.png";
                    if (clientConfig.getEnableIsolatedMode()) {
                        buttonText = I18n.get(MusicHud.MOD_ID + ".text.notConnected.isolated");
                    } else {
                        buttonText = I18n.get(MusicHud.MOD_ID + ".text.notConnected");
                    }
                    switchServerConnectButton.setEnabled(true);
                    Tooltip.attach(switchServerConnectButton, buttonText + "\n" + I18n.get(MusicHud.MOD_ID + ".button.connect"));
                }
                case INCOMPATIBLE -> {
                    icon = "/assets/music_hud/textures/gui/icons/unlink.png";
                    String template;
                    if (clientConfig.getEnableIsolatedMode()) {
                        template = I18n.get(buttonText + "\n" + MusicHud.MOD_ID + ".text.incompatibleWithServer.isolated");
                    } else {
                        template = I18n.get(buttonText + "\n" + MusicHud.MOD_ID + ".text.incompatibleWithServer");
                    }
                    buttonText = template.replace("{version}", connectionManager.getServerVersion().toString());
                    switchServerConnectButton.setEnabled(false);
                    Tooltip.attach(switchServerConnectButton, buttonText + "\n" + I18n.get(MusicHud.MOD_ID + ".button.connect"));
                }
            }
            SpannableString string = new SpannableString(" ");
            Image imageFromResource = ImageUtils.getImageFromResource(icon);
            if (imageFromResource != null) {
                string.setSpan(ImageUtils.getIconSpan(imageFromResource), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            switchServerConnectButton.setText(string);
        } else {
            switchServerConnectButton.setVisibility(View.GONE);
        }
    }

    private void checkAudioPlayerStatus(StreamAudioPlayer.Status status) {
        MusicInfoCard card = activeCard;
        if (card != null && card.getProgressBar() != null) {
            card.getProgressBar().setIndeterminate(status == StreamAudioPlayer.Status.BUFFERING || status == StreamAudioPlayer.Status.RETRYING);
        }
        updateCoverScale(true);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Invalidate the running transition first so its end callback cannot settle stale views.
        AnimatorSet running = cardAnimator;
        cardAnimator = null;
        if (running != null) {
            running.cancel();
        }
        AnimatorSet scaling = coverScaleAnimator;
        coverScaleAnimator = null;
        if (scaling != null) {
            scaling.cancel();
        }
        AnimatorSet panelAnimator = sidePanelAnimator;
        sidePanelAnimator = null;
        if (panelAnimator != null) {
            panelAnimator.cancel();
        }
        cardSwitching = false;
        pendingSwitch = null;
        activeCard = null;
        stagedCard = null;
        cardWrapper = null;
        buttonsLayout = null;
        likeButton = null;
        addToPlaylistButton = null;
        skipCurrentButton = null;
        sidePanel = null;
        lyricsScrollView = null;
        routerContainer = null;
        displayedDetail = null;
        reset();
    }
}
