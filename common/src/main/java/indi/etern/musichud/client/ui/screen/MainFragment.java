package indi.etern.musichud.client.ui.screen;

import icyllis.modernui.ModernUI;
import icyllis.modernui.R;
import icyllis.modernui.animation.*;
import icyllis.modernui.annotation.Nullable;
import icyllis.modernui.core.Context;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.graphics.Image;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.mc.ui.ClampingScrollView;
import icyllis.modernui.text.SpannableString;
import icyllis.modernui.text.Spanned;
import icyllis.modernui.text.style.ImageSpan;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.*;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.*;
import indi.etern.musichud.client.audio.NowPlayingInfo;
import indi.etern.musichud.client.audio.StreamAudioPlayer;
import indi.etern.musichud.client.services.ConnectionManager;
import indi.etern.musichud.client.services.music.MusicService;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.components.*;
import indi.etern.musichud.client.utils.ui.Easing;
import indi.etern.musichud.client.ui.dto.LyricLine;
import indi.etern.musichud.client.ui.pages.ConfigView;
import indi.etern.musichud.client.ui.pages.HomeView;
import indi.etern.musichud.client.ui.pages.account.AccountBaseView;
import indi.etern.musichud.client.ui.pages.search.SearchView;
import indi.etern.musichud.client.utils.PlayerInfoUtil;
import indi.etern.musichud.client.utils.image.ImageUtils;
import indi.etern.musichud.client.utils.ui.InsetBackgroundFactory;
import indi.etern.musichud.connection.ConnectionStateMachine;
import indi.etern.musichud.interfaces.ClientConfig;
import indi.etern.musichud.server.api.playmode.PlayMode;
import lombok.NonNull;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.language.I18n;

import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
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
    private UrlImageView albumImage;
    private TextView titleText;
    private FlexWrapLayout artists;
    private LinearLayout albumContainer;
    private TextView pusherText;
    @Setter
    private int defaultSelectedIndex = 0;
    private ProgressBar progressBar;
    private VoteSkipButton skipCurrentButton;
    private Button switchServerConnectButton;
    private PlayerHeadView pusherHeadView;
    private LinearLayout buttonsLayout;
    private TextView playedTimeText;
    private TextView totalTimeText;
    private ToggleTrackLikeStateButton likeButton;
    private ModifyPlaylistTrackModalButton addToPlaylistButton;
    private int sideWidth = -1;
    private StaggeredLyricScrollView lyricsScrollView;
    private LinearLayout lyricsSidebar;
    private int lyricsPanelWidth = -1;
    private boolean lyricsPanelShown = false;
    private AnimatorSet lyricsAnimator = null;
    private Button sourceButton;

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
        if (instance != null && instance.visible && instance.titleText != null) {
            // Restore the current playback instead of blanking it to "idle": a failed connect
            // attempt no longer stops the ongoing playback, so a blanket clear would wrongly
            // wipe the GUI while the HUD keeps playing.
            NowPlayingInfo nowPlayingInfo = NowPlayingInfo.getInstance();
            Traceable<MusicDetail> current = nowPlayingInfo.getCurrentlyPlayingMusic();
            Traceable<MusicDetail> nextToPlay = nowPlayingInfo.getNextToPlayIdleMusic();
            Queue<LyricLine> lines = nowPlayingInfo.getLyricLines();
            displayMusicInfo(current);
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
            displayMusicInfo(musicDetailTrace);
            MusicDetail musicDetail = musicDetailTrace == null ? MusicDetail.NONE : musicDetailTrace.value();
            if (musicDetail != null && !musicDetail.equals(MusicDetail.NONE)) {
                startProgressUpdater(musicDetail);
            }
            HomeView homeView = HomeView.getInstance();
            if (homeView != null) {
                homeView.switchMusic(musicDetailTrace, nextToPlayTrace, lines);
            }
            if (instance.lyricsScrollView != null) {
                instance.lyricsScrollView.switchLyrics(musicDetail, lines);
            }
            instance.updateLyricsPanelVisibility();
        }
    }

    private static void displayMusicInfo(Traceable<MusicDetail> musicDetailTrace) {
        if (musicDetailTrace == null || musicDetailTrace.value() == null || musicDetailTrace.value().equals(MusicDetail.NONE)) {
            instance.albumImage.loadUrl(MusicHud.ICON_BASE64);
            instance.titleText.setText(I18n.get(MusicHud.MOD_ID + ".text.idle"));
            instance.titleText.setTextColor(Theme.SECONDARY_TEXT_COLOR);
            instance.artists.removeAllViews();
            instance.albumContainer.removeAllViews();
            instance.pusherHeadView.setVisibility(View.GONE);
            instance.pusherText.setText("");
            instance.sourceButton.setVisibility(View.GONE);
            instance.progressBar.setVisibility(View.GONE);
            instance.playedTimeText.setText("");
            instance.totalTimeText.setText("");
            instance.buttonsLayout.setVisibility(View.GONE);
            instance.likeButton.bindMusicList(null);
            instance.addToPlaylistButton.bindMusicDetail(null);
        } else {
            MusicDetail musicDetail = musicDetailTrace.value();
            instance.titleText.setTextColor(Theme.NORMAL_TEXT_COLOR);
            Album album = musicDetail.getAlbum();
            instance.albumImage.loadUrl(album.getImageThumbnailUrl(instance.sideWidth));
            instance.titleText.setText(musicDetail.getName());
            PlayerInfo pusherPlayerInfo = NowPlayingInfo.getInstance().getPusherPlayerInfo();
            String name = pusherPlayerInfo != null ? pusherPlayerInfo.getProfile().name() : null;
            if (name == null || name.isEmpty()) {
                instance.pusherHeadView.setVisibility(View.GONE);
                instance.pusherText.setText("");
            } else {
                instance.pusherHeadView.setVisibility(View.VISIBLE);
                instance.pusherText.setText(name);
            }
            SourceMeta source = musicDetailTrace.source();
            if (source != null) {
                instance.sourceButton.setTag(source);
                SpannableString text = new SpannableString("    " + source.name());
                {
                    String iconPath;
                    Class<?> type = source.type();
                    if (Album.class.isAssignableFrom(type)) {
                        iconPath = "/assets/music_hud/textures/gui/icons/disc_album.png";
                    } else {
                        iconPath = "/assets/music_hud/textures/gui/icons/list_music.png";
                    }
                    Image icon = ImageUtils.getImageFromResource(iconPath);
                    if (icon != null) {
                        ImageSpan iconSpan = ImageUtils.getIconSpan(icon);
                        text.setSpan(iconSpan, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                }
                {
                    PlayMode playMode = source.playMode();
                    String playModeIconPath = switch (playMode) {
                        case RANDOM -> "/assets/music_hud/textures/gui/icons/shuffle.png";
                        case SEQUENTIAL -> "/assets/music_hud/textures/gui/icons/repeat.png";
                        case INTELLIGENT -> "/assets/music_hud/textures/gui/icons/heart_pulse.png";
                    };
                    Image icon = ImageUtils.getImageFromResource(playModeIconPath);
                    if (icon != null) {
                        ImageSpan iconSpan = ImageUtils.getIconSpan(icon);
                        text.setSpan(iconSpan, 2, 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                }

                instance.sourceButton.setVisibility(View.VISIBLE);
                instance.sourceButton.setText(text);
            } else {
                instance.sourceButton.setVisibility(View.GONE);
            }
            Context context = ModernUI.getInstance();
            instance.artists.removeAllViews();
            int index = 0;
            InsetBackgroundFactory backgroundFactory = InsetBackgroundFactory.builder()
                    .inset(0)
                    .cornerRadius(instance.buttonsLayout.dp(2))
                    .padding(new InsetBackgroundFactory.Padding(0, 0, 0, 0))
                    .build();
            for (Artist artist : musicDetail.getArtists()) {
                if (index != 0) {
                    TextView split = new TextView(context);
                    split.setTextColor(Theme.SECONDARY_TEXT_COLOR);
                    split.setTextSize(Theme.TEXT_SIZE_SMALL);
                    split.setText(" / ");
                    split.setSingleLine();
                    instance.artists.addView(split);
                }
                index++;
                Button artistButton = new Button(context);
                backgroundFactory.applyBackgroundTo(artistButton);
                artistButton.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
                artistButton.setTextColor(Theme.PRIMARY_COLOR);
                artistButton.setTextSize(Theme.TEXT_SIZE_NORMAL);
                artistButton.setText(artist.getName());
                artistButton.setSingleLine();
                artistButton.setOnClickListener(button -> {
                    RouterContainer routerContainer = RouterContainer.getInstance();
                    if (routerContainer != null) {
                        routerContainer.pushNavigate(
                                new ArtistDetailView(context, artist)
                        );
                    }
                });
                instance.artists.addView(artistButton);
            }

            instance.albumContainer.removeAllViews();
            Button albumButton = new Button(context);
            backgroundFactory.applyBackgroundTo(albumButton);
            albumButton.setTextColor(Theme.PRIMARY_COLOR);
            albumButton.setTextSize(Theme.TEXT_SIZE_NORMAL);
            albumButton.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
            albumButton.setText(musicDetail.getAlbum().getName());
            albumButton.setOnClickListener(button -> {
                RouterContainer routerContainer = RouterContainer.getInstance();
                if (routerContainer != null) {
                    routerContainer.pushNavigate(
                            new MusicCollectionDetailView(context, musicDetail.getAlbum())
                    );
                }
            });
            instance.albumContainer.addView(albumButton);

            instance.skipCurrentButton.reset();
            instance.progressBar.setVisibility(View.VISIBLE);
            instance.likeButton.bindMusicList(MusicService.getInstance().getMusicTrackState(musicDetail).currentUsersLikeList());
            instance.addToPlaylistButton.bindMusicDetail(musicDetail);
            instance.buttonsLayout.setVisibility(View.VISIBLE);
        }
    }

    private static void startProgressUpdater(MusicDetail musicDetail) {
        int token = progressUpdaterToken.incrementAndGet();
        NowPlayingInfo nowPlayingInfo = NowPlayingInfo.getInstance();
        Duration musicDuration = nowPlayingInfo.getMusicDuration();
        DateTimeFormatter formatter = musicDuration.toHoursPart() >= 1 ?
                DateTimeFormatter.ofPattern("HH:mm:ss") :
                DateTimeFormatter.ofPattern("mm:ss");
        String totalTimeString = formatter.format(LocalTime.MIDNIGHT.plusSeconds(musicDuration.toSeconds()));
        // 兜底退出：startAt 可能因任务被取代/失败永不触发，超时后结束进度循环防泄漏
        long deadline = System.currentTimeMillis() + musicDuration.toMillis() + 120_000;
        MusicHud.EXECUTOR.execute(() -> {
            do {
                if (instance == null || instance.progressBar == null
                        || progressUpdaterToken.get() != token) {
                    return;
                }
                Duration playedDuration = nowPlayingInfo.getPlayedDuration();
                String playedTimeString = formatter.format(LocalTime.MIDNIGHT.plusSeconds(playedDuration.toSeconds()));
                MuiModApi.postToUiThread(() -> {
                    if (instance != null && instance.visible && instance.progressBar != null) {
                        instance.progressBar.setProgress((int) (nowPlayingInfo.getProgressRate() * instance.sideWidth));
                        instance.playedTimeText.setText(playedTimeString);
                        instance.totalTimeText.setText(totalTimeString);
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
                albumImage = new UrlImageView(context);
                albumImage.loadUrl(MusicHud.ICON_BASE64);
                //noinspection SuspiciousNameCombination
                var imageParams = new FrameLayout.LayoutParams(sideWidth, sideWidth);
                sideContent.addView(albumImage, imageParams);

                LinearLayout musicInfo = new LinearLayout(context);
                musicInfo.setOrientation(LinearLayout.VERTICAL);

                titleText = new TextView(context);
                titleText.setTextSize(Theme.TEXT_SIZE_LARGE);
                titleText.setTextColor(Theme.NORMAL_TEXT_COLOR);
                instance.titleText.setText(I18n.get(MusicHud.MOD_ID + ".text.idle"));
                musicInfo.addView(titleText);

                artists = new FlexWrapLayout(context);
                musicInfo.addView(artists);

                albumContainer = new LinearLayout(context);
                albumContainer.setOrientation(LinearLayout.HORIZONTAL);
                albumContainer.setGravity(Gravity.TOP | Gravity.LEFT);
                musicInfo.addView(albumContainer);

                LinearLayout pusherRow = new LinearLayout(context);
                pusherRow.setOrientation(LinearLayout.HORIZONTAL);
                pusherRow.setGravity(Gravity.CENTER_VERTICAL);

                pusherHeadView = new PlayerHeadView(context);
                int rowHeight = pusherRow.dp(Theme.TEXT_SIZE_LARGER);
                //noinspection SuspiciousNameCombination
                pusherHeadView.setLayoutParams(new LinearLayout.LayoutParams(rowHeight, rowHeight));
                pusherHeadView.setVisibility(View.GONE);
                pusherHeadView.setPlayerSkinSupplier(() -> {
                    try {
                        PlayerInfo pusherPlayerInfo = NowPlayingInfo.getInstance().getPusherPlayerInfo();
                        return PlayerInfoUtil.getPlayerSkin(pusherPlayerInfo);
                    } catch (Exception ignored) {
                    }
                    return null;
                });
                pusherRow.addView(pusherHeadView);

                pusherText = new TextView(context);
                pusherText.setTextColor(Theme.SECONDARY_TEXT_COLOR);
                pusherText.setTextSize(Theme.TEXT_SIZE_NORMAL);
                LinearLayout.LayoutParams params5 = new LinearLayout.LayoutParams(WRAP_CONTENT, rowHeight);
                params5.gravity = Gravity.LEFT | Gravity.CENTER_HORIZONTAL;
                params5.setMargins(pusherText.dp(4), 0, 0, 0);
                pusherRow.addView(pusherText, params5);


                LinearLayout.LayoutParams params6 = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
                int dp2 = musicInfo.dp(2);
                params6.setMargins(0, dp2, 0, dp2);
                musicInfo.addView(pusherRow, params6);


                sourceButton = new Button(context);
                sourceButton.setVisibility(View.GONE);
                sourceButton.setTextSize(Theme.TEXT_SIZE_NORMAL);
                sourceButton.setTextColor(Theme.SECONDARY_TEXT_COLOR);
                sourceButton.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
                sourceButton.setSingleLine();
                sourceButton.setOnClickListener(view -> {
                    Object tag = sourceButton.getTag();
                    if (tag instanceof SourceMeta sourceMeta) {
                        Class<?> type = sourceMeta.type();
                        if (MusicCollection.class.isAssignableFrom(type)) {
                            //noinspection unchecked
                            MusicService.getInstance().loadMusicCollectionDetail(sourceMeta.id(), (Class<? extends MusicCollection>) type)
                                    .thenAccept((musicCollection) ->
                                            MuiModApi.postToUiThread(() -> routerContainer.pushNavigate(
                                                    new MusicCollectionDetailView(context, musicCollection)))
                                    );
                        }
                    }
                });
                InsetBackgroundFactory.builder()
                        .backgroundColor(Theme.GHOST_BUTTON_STATES)
                        .padding(new InsetBackgroundFactory.Padding(0, sourceButton.dp(1), 0, sourceButton.dp(1)))
                        .cornerRadius(sourceButton.dp(4)).build().applyBackgroundTo(sourceButton);
                musicInfo.addView(sourceButton, new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, 0));

                progressBar = new ProgressBar(context, null, R.attr.progressBarStyleHorizontal);
                progressBar.setMin(0);
                progressBar.setMax(sideWidth);
                progressBar.setVisibility(View.GONE);
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
                LinearLayout.LayoutParams params2 = new LinearLayout.LayoutParams(MATCH_PARENT, base.dp(4));
                params2.setMargins(0, sideContent.dp(1), 0, sideContent.dp(-4));
                musicInfo.addView(progressBar, params2);

                LinearLayout progressTexts = new LinearLayout(context);
                progressTexts.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams params3 = new LinearLayout.LayoutParams(MATCH_PARENT, base.dp(16));
                params3.setMargins(0, sideContent.dp(6), 0, 0);
                musicInfo.addView(progressTexts, params3);

                playedTimeText = new TextView(context);
                playedTimeText.setTextColor(Theme.SECONDARY_TEXT_COLOR);
                playedTimeText.setTextSize(Theme.TEXT_SIZE_NORMAL);
                progressTexts.addView(playedTimeText, new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, 0));

                progressTexts.addView(new View(context), new LinearLayout.LayoutParams(WRAP_CONTENT, MATCH_PARENT, 1));

                totalTimeText = new TextView(context);
                totalTimeText.setTextColor(Theme.SECONDARY_TEXT_COLOR);
                totalTimeText.setTextSize(Theme.TEXT_SIZE_NORMAL);
                progressTexts.addView(totalTimeText, new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, 0));

                buttonsLayout = new LinearLayout(context);
                buttonsLayout.setOrientation(LinearLayout.HORIZONTAL);

                InsetBackgroundFactory backgroundFactory = InsetBackgroundFactory.builder()
                        .backgroundColor(Theme.GHOST_BUTTON_STATES)
                        .padding(new InsetBackgroundFactory.Padding(buttonsLayout.dp(2), buttonsLayout.dp(1), buttonsLayout.dp(2), buttonsLayout.dp(1)))
                        .cornerRadius(buttonsLayout.dp(4)).build();
                {
                    likeButton = new ToggleTrackLikeStateButton(context);
                    backgroundFactory.applyBackgroundTo(likeButton);
                    buttonsLayout.addView(likeButton, new LinearLayout.LayoutParams(0, MATCH_PARENT, 1));
                }
                {
                    addToPlaylistButton = new ModifyPlaylistTrackModalButton(context);
                    backgroundFactory.applyBackgroundTo(addToPlaylistButton);
                    buttonsLayout.addView(addToPlaylistButton, new LinearLayout.LayoutParams(0, MATCH_PARENT, 1));
                }
                {
                    skipCurrentButton = new VoteSkipButton(context);
                    backgroundFactory.applyBackgroundTo(skipCurrentButton);
                    buttonsLayout.addView(skipCurrentButton, new LinearLayout.LayoutParams(0, MATCH_PARENT, 1));
                }

                LinearLayout.LayoutParams buttonsParams = new LinearLayout.LayoutParams(MATCH_PARENT, buttonsLayout.dp(40));
                buttonsParams.setMargins(0, sideContent.dp(2), 0, 0);

                var params1 = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
                params1.setMargins(sideContent.dp(8), sideContent.dp(4), sideContent.dp(8), sideContent.dp(24));

                musicInfo.addView(buttonsLayout, buttonsParams);
                musicInfo.setMinimumHeight(sideContent.dp(132));

                sideContent.addView(musicInfo, params1);

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
                backgroundFactory.applyBackgroundTo(switchServerConnectButton);
                switchServerConnectButton.setOnClickListener(b -> connectionManager.toggleConnection());
                serverConnectPanel.addView(switchServerConnectButton, new LinearLayout.LayoutParams(MATCH_PARENT, base.dp(36)));
                refreshServerConnectStatus();
                LinearLayout.LayoutParams params4 = new LinearLayout.LayoutParams(sideWidth, WRAP_CONTENT);
                params4.setMargins(0, serverConnectPanel.dp(8), 0, serverConnectPanel.dp(16));
                side.addView(serverConnectPanel, params4);

                LayoutTransition transition1 = new LayoutTransition();
                transition1.enableTransitionType(LayoutTransition.CHANGING);
                musicInfo.setLayoutTransition(transition1);

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
                Traceable<MusicDetail> nextToPlay = nowPlayingInfo.getNextToPlayIdleMusic();

                switchMusic(currentlyPlaying, nextToPlay, playingInfo.getLyricLines());
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
                    // 过渡刚开始就提前启动回家时右侧歌词栏的隐藏动画，
                    // 让面板在 onBeforeSwap 置 GONE 时已基本离屏，避免页面 addView 同帧重排。
                    if ("Home".equals(toKey)) {
                        hideLyricsPanel();
                    }
                }

                @Override
                public void onBeforeSwap(@Nullable String fromKey, @NonNull String toKey,
                                         @NonNull RouterContainer.TransitionType type) {
                    // 回到 Home 时隐藏侧边栏（HomeView 自带歌词组件），离开 Home 时显示侧边栏。
                    // 该钩子在页面结构变更前一帧由 RouterContainer 触发，与页面 addView 帧对齐，
                    // 避免用固定 300ms 计时导致切换瞬间出现两次 reflow。
                    if ("Home".equals(toKey)) {
                        hideLyricsPanel();
                        if (lyricsSidebar != null) {
                            lyricsSidebar.setVisibility(View.GONE);
                        }
                    } else {
                        showLyricsPanel();
                    }
                }
            });

            var params = new LinearLayout.LayoutParams(0, MATCH_PARENT, 1);
            params.setMargins(routerContainer.dp(80), 0, routerContainer.dp(24), 0);
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
        slideIn.setInterpolator(Easing.EASE_OUT_QUINT);
        ObjectAnimator fadeIn = ObjectAnimator.ofFloat(lyricsSidebar, View.ALPHA, 0f, 1f);
        fadeIn.setDuration(LYRICS_ANIMATION_DURATION);
        fadeIn.setInterpolator(Easing.EASE_IN_OUT_CUBIC);

        lyricsAnimator = new AnimatorSet();
        lyricsAnimator.playTogether(slideIn, fadeIn);
        lyricsAnimator.start();

        if (lyricsScrollView != null) {
            lyricsScrollView.reinitializeAfterShow();
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
            lyricsScrollView.suspendLyricFollowing();
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
                        template = I18n.get(MusicHud.MOD_ID + ".text.incompatibleWithServer");
                    } else {
                        template = I18n.get(MusicHud.MOD_ID + ".text.incompatibleWithServer.isolated");
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
        progressBar.setIndeterminate(status == StreamAudioPlayer.Status.BUFFERING || status == StreamAudioPlayer.Status.RETRYING);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        reset();
    }
}