package indi.etern.musichud.client.ui.screen;

import icyllis.modernui.animation.*;
import icyllis.modernui.annotation.Nullable;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.graphics.Image;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.mc.ui.ClampingScrollView;
import icyllis.modernui.text.SpannableString;
import icyllis.modernui.text.Spanned;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.beans.music.Traceable;
import indi.etern.musichud.client.audio.NowPlayingInfo;
import indi.etern.musichud.client.audio.StreamAudioPlayer;
import indi.etern.musichud.client.dto.LyricLine;
import indi.etern.musichud.client.services.ConnectionManager;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.components.*;
import indi.etern.musichud.client.ui.pages.ConfigView;
import indi.etern.musichud.client.ui.pages.HomeView;
import indi.etern.musichud.client.ui.pages.account.AccountBaseView;
import indi.etern.musichud.client.ui.pages.search.SearchView;
import indi.etern.musichud.client.utils.image.ImageUtils;
import indi.etern.musichud.client.utils.ui.Easing;
import indi.etern.musichud.client.utils.ui.InsetBackgroundFactory;
import indi.etern.musichud.client.utils.ui.SpringInterpolator;
import indi.etern.musichud.connection.ConnectionStateMachine;
import indi.etern.musichud.interfaces.ClientConfig;
import lombok.NonNull;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.sounds.SoundSource;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
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
    private static final int LYRICS_ANIMATION_DURATION = 300;
    private static final SpringInterpolator LYRIC_PANEL_SWITCH_INTERPOLATOR =
            new SpringInterpolator((float) LYRICS_ANIMATION_DURATION / 1000, 1);
    private static final float CARD_COVER_MIN_SCALE = 0.8f;
    private static final int CARD_ANIM_DURATION_MS = 350;
    private static final SpringInterpolator MUSIC_SWITCH_INTERPOLATOR =
            new SpringInterpolator((float) CARD_ANIM_DURATION_MS / 1000, 1);
    private static volatile MainFragment instance = null;

    static {
        ConnectionStateMachine.getConnectStatusListeners().add(status -> {
            if (instance != null && instance.visible) {
                MuiModApi.postToUiThread(() -> instance.refreshServerConnectStatus());
            }
        });
    }

    private final NowPlayingInfo playingInfo = NowPlayingInfo.getInstance();
    private boolean visible = false;
    @Setter
    private int defaultSelectedIndex = 0;
    private Button switchServerConnectButton;
    private int sideWidth = -1;
    private StaggeredLyricScrollView lyricsScrollView;
    private LinearLayout lyricsSidebar;
    private int lyricsPanelWidth = -1;
    private boolean lyricsPanelShown = false;
    private AnimatorSet lyricsAnimator = null;

    // Double-buffered music info card (album cover + info merged)
    private FrameLayout cardWrapper;
    private MusicInfoCard activeCard;
    private MusicInfoCard stagedCard;
    private AnimatorSet cardAnimator;
    private AnimatorSet coverScaleAnimator;
    private boolean cardSwitching = false;
    // Latest-wins slot: a switch arriving mid-animation replaces any earlier pending one
    private Traceable<MusicDetail> pendingSwitch;
    // MusicDetail currently settled on the active card
    private MusicDetail displayedDetail;

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
            // Restore the current playback instead of blanking it to "idle": a failed connect
            // attempt no longer stops the ongoing playback, so a blanket clear would wrongly
            // wipe the GUI while the HUD keeps playing.
            NowPlayingInfo nowPlayingInfo = NowPlayingInfo.getInstance();
            Traceable<MusicDetail> current = nowPlayingInfo.getCurrentlyPlayingMusic();
            Traceable<MusicDetail> nextToPlay = nowPlayingInfo.getNextToPlayMusic();
            Queue<LyricLine> lines = nowPlayingInfo.getLyricLines();
            // Don't rebuild contents while a card transition is in flight; it settles on its own.
            if (!instance.cardSwitching) {
                instance.activeCard.bind(current, instance.sideWidth);
                instance.displayedDetail = current == null ? null : current.value();
                instance.updateCoverScale(false);
            }
            if (homeView != null) {
                homeView.switchMusic(current, nextToPlay, lines);
            }
            if (instance.lyricsScrollView != null) {
                instance.lyricsScrollView.switchLyrics(current == null ? MusicDetail.NONE : current.value(), lines);
            }
            instance.updateLyricsPanelVisibility();
            instance.refreshServerConnectStatus();
        }
    }

    public static void switchMusic(Traceable<MusicDetail> musicDetailTrace, Traceable<MusicDetail> nextToPlayTrace, Queue<LyricLine> lines) {
        if (instance != null && instance.visible) {
            instance.applyMusic(musicDetailTrace, nextToPlayTrace, lines, true);
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
            lyricsScrollView.switchLyrics(musicDetail, lines);
        }
        updateLyricsPanelVisibility();
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
    }

    private static boolean isSameMusic(MusicDetail a, MusicDetail b) {
        MusicDetail normalizedA = a == null ? MusicDetail.NONE : a;
        MusicDetail normalizedB = b == null ? MusicDetail.NONE : b;
        return normalizedA.equals(normalizedB);
    }

    /** The album cover is full size only when a track is actually playing and not muted. */
    private static boolean isCoverExpanded() {
        MusicDetail detail = NowPlayingInfo.getInstance().getCurrentlyPlayingMusicDetail();
        float targetGain = clientConfig.getMuted() ? 0 : (float) clientConfig.getSoundVolume() / 100 *
                (clientConfig.getMixWithVanillaSoundVolume() ? Minecraft.getInstance().options.getSoundSourceVolume(SoundSource.MUSIC) : 1);
        if (detail == null || detail.equals(MusicDetail.NONE) || targetGain == 0) {
            return false;
        }
        StreamAudioPlayer.Status status = StreamAudioPlayer.getInstance().getStatus();
        return status == StreamAudioPlayer.Status.PLAYING;
    }

    /** Moves the active cover between {@link #CARD_COVER_MIN_SCALE} and 1 to match playback state. */
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

    /** Re-applies the cover scale (e.g. after a mute toggle) on the UI thread. */
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

    private static void startProgressUpdater(MusicDetail musicDetail) {
        int token = progressUpdaterToken.incrementAndGet();
        NowPlayingInfo nowPlayingInfo = NowPlayingInfo.getInstance();
        Duration musicDuration = nowPlayingInfo.getMusicDuration();
        // 兜底退出：startAt 可能因任务被取代/失败永不触发，超时后结束进度循环防泄漏
        long deadline = System.currentTimeMillis() + musicDuration.toMillis() + 120_000;
        MusicHud.EXECUTOR.execute(() -> {
            do {
                MusicInfoCard card = instance == null ? null : instance.activeCard;
                if (card == null || card.getProgressBar() == null
                        || progressUpdaterToken.get() != token) {
                    return;
                }
                MuiModApi.postToUiThread(() -> {
                    MusicInfoCard current = instance == null ? null : instance.activeCard;
                    if (instance != null && instance.visible && current != null) {
                        current.getProgressBar().setProgress((int) (nowPlayingInfo.getProgressRate() * instance.sideWidth));
                    }
                });
                try {
                    Thread.sleep(Duration.of(50, ChronoUnit.MILLIS));
                } catch (InterruptedException e) {
                    return;
                }
            } while (musicDetail.equals(nowPlayingInfo.getCurrentlyPlayingMusicDetail())
                    && nowPlayingInfo.getProgressRate() < 1
                    && System.currentTimeMillis() < deadline);
        });
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
            instance.updateLyricsPanelVisibility();
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

    private void reset() {
        visible = false;
        defaultSelectedIndex = 0;
        lyricsPanelShown = false;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable DataSet savedInstanceState) {
        try {
            visible = true;
            var context = requireContext();
            var base = new LinearLayout(context);
            base.setPadding(base.dp(24), 0, base.dp(24), 0);

            var baseParams = new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT);
            base.setLayoutParams(baseParams);
            base.setOrientation(LinearLayout.HORIZONTAL);

            var routerContainer = new RouterContainer(context);

            routerContainer.setAnimationStyle(RouterContainer.AnimationStyle.SCALE_FADE_ROOT);
            routerContainer.setAnimationDuration(300);

            {
                var side = new LinearLayout(context);
                side.setOrientation(LinearLayout.VERTICAL);
                base.addView(side, new LinearLayout.LayoutParams(WRAP_CONTENT, MATCH_PARENT));

                //noinspection UnstableApiUsage
                var sideScrollView = new ClampingScrollView(context);
                side.addView(sideScrollView, new LinearLayout.LayoutParams(WRAP_CONTENT, 0, 1));

                var sideContent = new LinearLayout(context);
                sideContent.setOrientation(LinearLayout.VERTICAL);

                sideContent.addView(new View(context), new LinearLayout.LayoutParams(MATCH_PARENT, sideContent.dp(24)));

                sideWidth = base.dp(240);
                var params = new LinearLayout.LayoutParams(sideWidth, MATCH_PARENT);
                params.gravity = Gravity.CENTER;

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
                LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
                cardParams.setMargins(0, 0, 0, sideContent.dp(24));
                sideContent.addView(cardWrapper, cardParams);

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

                var sideMenu = new SideMenu(context, routerContainer);
                if (Minecraft.getInstance().player != null) {//in game
                    var homeNav = sideMenu.createNavigationPage("Home", "/assets/music_hud/textures/gui/icons/house.png",
                            I18n.get(MusicHud.MOD_ID + ".text.page.home"), HomeView::new);
                    var searchNav = sideMenu.createNavigationPage("Search", "/assets/music_hud/textures/gui/icons/search.png",
                            I18n.get(MusicHud.MOD_ID + ".text.page.search"), SearchView::new);
                    var accountNav = sideMenu.createNavigationPage("Account", "/assets/music_hud/textures/gui/icons/square_user_round.png",
                            I18n.get(MusicHud.MOD_ID + ".text.page.account"), AccountBaseView::new);
                    var settingsNav = sideMenu.createNavigationPage("Settings", "/assets/music_hud/textures/gui/icons/settings.png",
                            I18n.get(MusicHud.MOD_ID + ".text.page.setting"), ConfigView::new);
                    SideMenu.NavigationMeta defaultMeta = List.of(homeNav, searchNav, accountNav, settingsNav).get(defaultSelectedIndex);
                    defaultMeta.select();
                } else {
                    var settingsNav = sideMenu.createNavigationPage("Settings", "/assets/music_hud/textures/gui/icons/settings.png",
                            I18n.get(MusicHud.MOD_ID + ".text.page.setting"), ConfigView::new);
                    settingsNav.select();
                }
                sideContent.addView(sideMenu, params);

                var sideParams = new LinearLayout.LayoutParams(sideWidth, MATCH_PARENT);
                sideScrollView.addView(sideContent, sideParams);

                LinearLayout serverConnectPanel = new LinearLayout(context);
                serverConnectPanel.setOrientation(LinearLayout.VERTICAL);
                serverConnectPanel.setGravity(Gravity.CENTER_VERTICAL);

                switchServerConnectButton = new Button(context);
                switchServerConnectButton.setTextSize(Theme.TEXT_SIZE_NORMAL);
                switchServerConnectButton.setTextColor(Theme.NORMAL_TEXT_COLOR);
                switchServerConnectButton.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
                InsetBackgroundFactory backgroundFactory = InsetBackgroundFactory.builder()
                        .backgroundColor(Theme.GHOST_BUTTON_STATES)
                        .padding(new InsetBackgroundFactory.Padding(switchServerConnectButton.dp(2), switchServerConnectButton.dp(1), switchServerConnectButton.dp(2), switchServerConnectButton.dp(1)))
                        .cornerRadius(switchServerConnectButton.dp(4)).build();
                backgroundFactory.applyBackgroundTo(switchServerConnectButton);
                switchServerConnectButton.setOnClickListener(b -> connectionManager.toggleConnection());
                serverConnectPanel.addView(switchServerConnectButton, new LinearLayout.LayoutParams(MATCH_PARENT, base.dp(36)));
                refreshServerConnectStatus();
                LinearLayout.LayoutParams params4 = new LinearLayout.LayoutParams(sideWidth, WRAP_CONTENT);
                params4.setMargins(0, serverConnectPanel.dp(8), 0, serverConnectPanel.dp(16));
                side.addView(serverConnectPanel, params4);

                LayoutTransition transition2 = new LayoutTransition();
                transition2.enableTransitionType(LayoutTransition.CHANGING);
                sideContent.setLayoutTransition(transition2);

                LayoutTransition transition3 = new LayoutTransition();
                transition3.disableTransitionType(LayoutTransition.DISAPPEARING);
                transition3.disableTransitionType(LayoutTransition.APPEARING);
                transition3.enableTransitionType(LayoutTransition.CHANGING);
                serverConnectPanel.setLayoutTransition(transition3);

                NowPlayingInfo nowPlayingInfo = NowPlayingInfo.getInstance();
                Traceable<MusicDetail> currentlyPlaying = nowPlayingInfo.getCurrentlyPlayingMusic();
                Traceable<MusicDetail> nextToPlay = nowPlayingInfo.getNextToPlayMusic();

                // Seed both cards without animating: the active one shows current playback,
                // the staged one shows idle so both measure to a stable height.
                stagedCard.bind(Traceable.of(MusicDetail.NONE), sideWidth);
                applyMusic(currentlyPlaying, nextToPlay, playingInfo.getLyricLines(), false);
            }

            lyricsPanelWidth = base.dp(320);
            lyricsSidebar = new LinearLayout(context);
            lyricsSidebar.setOrientation(LinearLayout.VERTICAL);
            lyricsSidebar.setVisibility(View.GONE);
            lyricsSidebar.setAlpha(0f);
            lyricsSidebar.setTranslationX(lyricsPanelWidth);

            lyricsScrollView = new StaggeredLyricScrollView(context);
            lyricsSidebar.addView(lyricsScrollView, new LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));

            MusicDetail currentMusic = playingInfo.getCurrentlyPlayingMusicDetail();
            Queue<LyricLine> currentLyrics = playingInfo.getLyricLines();
            if (currentMusic != null && currentLyrics != null && !currentMusic.equals(MusicDetail.NONE)) {
                lyricsScrollView.switchLyrics(currentMusic, currentLyrics);
            }

            routerContainer.setOnPageChangeListener(new RouterContainer.OnPageChangeListener() {
                @Override
                public void onPageChangeStart(@Nullable String fromKey, @NonNull String toKey) {
                    boolean useSerial = ("Home".equals(fromKey) || "Home".equals(toKey)) && shouldShowLyricsPanel();
                    routerContainer.setTransitionType(
                            useSerial ? RouterContainer.TransitionType.SERIAL : RouterContainer.TransitionType.CROSS
                    );
                }

                @Override
                public void onPageChangeEnd(@NonNull String pageKey) {
                    // animation is already triggered in onBeforeSwap
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
                        hideLyricsPanel();
                    }
                }

                @Override
                public void onBeforeSwap(@Nullable String fromKey, @NonNull String toKey,
                                         @NonNull RouterContainer.TransitionType type) {
                    if ("Home".equals(toKey)) {
                        hideLyricsPanel();
                        if (lyricsSidebar != null) {
                            lyricsSidebar.setVisibility(View.GONE);
                        }
                    } else {
                        HomeView homeView = HomeView.getInstance();
                        if (homeView != null) {
                            StaggeredLyricScrollView scrollView = homeView.getStaggeredLyricScrollView();
                            if (scrollView != null) {
                                scrollView.suspendLyricFollowingAndHide();
                            }
                        }
                        showLyricsPanel();
                    }
                }
            });

            var params = new LinearLayout.LayoutParams(0, MATCH_PARENT, 1);
            params.setMargins(routerContainer.dp(64), 0, 0, 0);
            base.addView(routerContainer, params);

            LinearLayout.LayoutParams params1 = new LinearLayout.LayoutParams(lyricsPanelWidth, MATCH_PARENT);
            params1.setMargins(base.dp(24), 0, 0, 0);
            base.addView(lyricsSidebar, params1);

            if (defaultSelectedIndex != 0) {
                showLyricsPanel();
            }

            return base;
        } catch (Exception e) {
            visible = false;
            throw e;
        }
    }

    private boolean shouldShowLyricsPanel() {
        if (!clientConfig.getEnableLyricsSidebar()) {
            return false;
        }
        NowPlayingInfo nowPlayingInfo = NowPlayingInfo.getInstance();
        MusicDetail detail = nowPlayingInfo.getCurrentlyPlayingMusicDetail();
        boolean musicInvalid = detail == null || detail.equals(MusicDetail.NONE);
        if (musicInvalid) {
            return false;
        }
        Queue<LyricLine> lines = nowPlayingInfo.getLyricLines();
        return lines != null && !lines.isEmpty()
                && lines.stream().filter(l -> l.getType() == LyricLine.Type.NORMAL).count() > 1;
    }

    private void updateLyricsPanelVisibility() {
        RouterContainer rc = RouterContainer.getInstance();
        if (rc == null) return;
        String currentKey = rc.getCurrentPageKey();
        if ("Home".equals(currentKey) || currentKey == null) return;
        if (shouldShowLyricsPanel()) {
            showLyricsPanel();
        } else {
            hideLyricsPanel();
        }
    }

    private void showLyricsPanel() {
        if (lyricsSidebar == null || lyricsPanelShown) {
            return;
        }
        if (!shouldShowLyricsPanel()) {
            return;
        }
        lyricsPanelShown = true;
        if (lyricsAnimator != null) {
            lyricsAnimator.cancel();
        }

        ViewGroup.LayoutParams lp = lyricsSidebar.getLayoutParams();
        lp.width = lyricsPanelWidth;
        lyricsSidebar.setLayoutParams(lp);
        lyricsSidebar.setVisibility(View.VISIBLE);
        lyricsSidebar.setTranslationX(lyricsPanelWidth);
        lyricsSidebar.setAlpha(0f);

        ObjectAnimator slideIn = ObjectAnimator.ofFloat(lyricsSidebar, View.TRANSLATION_X, lyricsPanelWidth, 0);
        slideIn.setDuration(LYRICS_ANIMATION_DURATION);
        slideIn.setInterpolator(LYRIC_PANEL_SWITCH_INTERPOLATOR);
        ObjectAnimator fadeIn = ObjectAnimator.ofFloat(lyricsSidebar, View.ALPHA, 0f, 1f);
        fadeIn.setDuration(LYRICS_ANIMATION_DURATION);
        fadeIn.setInterpolator(LYRIC_PANEL_SWITCH_INTERPOLATOR);

        lyricsAnimator = new AnimatorSet();
        lyricsAnimator.playTogether(slideIn, fadeIn);
        lyricsAnimator.start();

        if (lyricsScrollView != null) {
            lyricsScrollView.reinitialize();
        }
    }

    private void hideLyricsPanel() {
        if (lyricsSidebar == null || !lyricsPanelShown) {
            return;
        }
        lyricsPanelShown = false;
        if (lyricsAnimator != null) {
            lyricsAnimator.cancel();
        }
        if (lyricsScrollView != null) {
            lyricsScrollView.suspendLyricFollowingAndHide();
        }

        ObjectAnimator slideOut = ObjectAnimator.ofFloat(lyricsSidebar, View.TRANSLATION_X, 0, lyricsPanelWidth);
        slideOut.setDuration(LYRICS_ANIMATION_DURATION);
        slideOut.setInterpolator(Easing.EASE_IN_QUINT);
        ObjectAnimator fadeOut = ObjectAnimator.ofFloat(lyricsSidebar, View.ALPHA, 1f, 0f);
        fadeOut.setDuration(LYRICS_ANIMATION_DURATION);
        fadeOut.setInterpolator(Easing.EASE_IN_OUT_CUBIC);

        lyricsAnimator = new AnimatorSet();
        lyricsAnimator.playTogether(slideOut, fadeOut);
        lyricsAnimator.addListener(new AnimatorListener() {
            @Override
            public void onAnimationEnd(@NonNull Animator animation) {
                ViewGroup.LayoutParams lp = lyricsSidebar.getLayoutParams();
                lp.width = 0;
                lyricsSidebar.setLayoutParams(lp);
            }
        });
        lyricsAnimator.start();
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
                        switchServerConnectButton.setTooltipText(null);
                    } else {
                        buttonText = I18n.get(MusicHud.MOD_ID + ".text.connected");
                        switchServerConnectButton.setEnabled(true);
                        switchServerConnectButton.setTooltipText(I18n.get(MusicHud.MOD_ID + ".button.disconnect"));
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
                    switchServerConnectButton.setTooltipText(I18n.get(MusicHud.MOD_ID + ".button.connect"));
                }
                case INCOMPATIBLE -> {
                    icon = "/assets/music_hud/textures/gui/icons/unlink.png";
                    String template;
                    if (clientConfig.getEnableIsolatedMode()) {
                        template = I18n.get(MusicHud.MOD_ID + ".text.incompatibleWithServer.isolated");
                    } else {
                        template = I18n.get(MusicHud.MOD_ID + ".text.incompatibleWithServer");
                    }
                    buttonText = template.replace("{version}", connectionManager.getServerVersion().toString());
                    switchServerConnectButton.setEnabled(false);
                    switchServerConnectButton.setTooltipText(I18n.get(MusicHud.MOD_ID + ".button.connect"));
                }
            }
            String linkUnicode = "\uD83D\uDD17";
            SpannableString string = new SpannableString("  " + linkUnicode + " " + buttonText);
            Image imageFromResource = ImageUtils.getImageFromResource(icon);
            if (imageFromResource != null) {
                string.setSpan(ImageUtils.getIconSpan(imageFromResource), 2, 2 + linkUnicode.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
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
        cardSwitching = false;
        pendingSwitch = null;
        activeCard = null;
        stagedCard = null;
        cardWrapper = null;
        displayedDetail = null;
        reset();
    }
}