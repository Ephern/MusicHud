package indi.etern.musichud.client.ui.screen;

import icyllis.modernui.ModernUI;
import icyllis.modernui.R;
import icyllis.modernui.animation.LayoutTransition;
import icyllis.modernui.annotation.Nullable;
import icyllis.modernui.core.Context;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.graphics.drawable.Drawable;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.mc.ui.ClampingScrollView;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.*;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.Artist;
import indi.etern.musichud.client.ui.beans.LyricLine;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.client.audio.NowPlayingInfo;
import indi.etern.musichud.client.audio.StreamAudioPlayer;
import indi.etern.musichud.client.services.LoginService;
import indi.etern.musichud.client.services.MusicService;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.components.*;
import indi.etern.musichud.client.ui.pages.ConfigView;
import indi.etern.musichud.client.ui.pages.HomeView;
import indi.etern.musichud.client.ui.pages.account.AccountBaseView;
import indi.etern.musichud.client.ui.pages.search.SearchView;
import indi.etern.musichud.client.ui.utils.ButtonInsetBackgroundFactory;
import indi.etern.musichud.client.ui.utils.PlayerInfoUtil;
import indi.etern.musichud.interfaces.ClientConfig;
import indi.etern.musichud.interfaces.IClientLoginService;
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
import java.util.function.Consumer;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class MainFragment extends Fragment {
    private static final ClientConfig clientConfig = ClientConfig.getInstance();
    private static final IClientLoginService I_CLIENT_LOGIN_SERVICE = LoginService.getInstance();
    private static volatile MainFragment instance = null;

    static {
        MusicHud.getConnectStatusListeners().add(status -> {
            if (instance != null) {
                MuiModApi.postToUiThread(() -> {
                    instance.refreshServerConnectStatus();
                });
            }
        });
    }

    private final NowPlayingInfo playingInfo = NowPlayingInfo.getInstance();
    private UrlImageView albumImage;
    private TextView titleText;
    private FlexWrapLayout artists;
    private LinearLayout albumContainer;
    private TextView pusherText;
    @Setter
    private int defaultSelectedIndex = 0;
    private ProgressBar progressBar;
    private TextView progressText;
    private Button skipCurrentButton;
    private TextView serverConnectStatus;
    private Button switchServerConnectButton;
    private PlayerHeadView pusherHeadView;

    public MainFragment() {
    }

    public static void refresh() {
        switchMusic(null, null, null);
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
        if (instance != null && instance.titleText != null) {
            instance.titleText.setText(I18n.get(MusicHud.MOD_ID + ".text.idle"));
            instance.refreshServerConnectStatus();
        }
    }

    public static void switchMusic(MusicDetail musicDetail, MusicDetail nextToPlay, Queue<LyricLine> lyricLines) {
        if (instance != null) {
            if (musicDetail == null || musicDetail.equals(MusicDetail.NONE)) {
                instance.albumImage.loadUrl(MusicHud.ICON_BASE64);
                instance.titleText.setText(I18n.get(MusicHud.MOD_ID + ".text.idle"));
                instance.titleText.setTextColor(Theme.SECONDARY_TEXT_COLOR);
                instance.artists.removeAllViews();
                instance.albumContainer.removeAllViews();
                instance.pusherHeadView.setVisibility(View.GONE);
                instance.pusherText.setText("");
                instance.progressBar.setVisibility(View.GONE);
                instance.progressText.setText("");
                instance.skipCurrentButton.setVisibility(View.GONE);
            } else {
                instance.titleText.setTextColor(Theme.NORMAL_TEXT_COLOR);
                instance.albumImage.loadUrl(musicDetail.getAlbum().getThumbnailPicUrl(240));
                instance.titleText.setText(musicDetail.getName());
                PlayerInfo pusherPlayerInfo = NowPlayingInfo.getInstance().getPusherPlayerInfo();
                String name = pusherPlayerInfo != null ? pusherPlayerInfo.getProfile().getName() : null;
                if (name == null || name.isEmpty()) {
                    instance.pusherHeadView.setVisibility(View.GONE);
                    instance.pusherText.setText("");
                } else {
                    instance.pusherHeadView.setVisibility(View.VISIBLE);
                    instance.pusherText.setText(name);
                }
                Context context = ModernUI.getInstance();
                instance.artists.removeAllViews();
                int index = 0;
                for (Artist artist : musicDetail.getArtists()) {
                    if (index != 0) {
                        TextView split = new TextView(context);
                        split.setTextColor(Theme.SECONDARY_TEXT_COLOR);
                        split.setTextSize(Theme.TEXT_SIZE_SMALL);
                        split.setText(" / ");
                        instance.artists.addView(split);
                    }
                    index++;
                    Button artistButton = new Button(context);
                    Drawable background = ButtonInsetBackgroundFactory.builder()
                            .inset(0)
                            .cornerRadius(artistButton.dp(2))
                            .padding(new ButtonInsetBackgroundFactory.Padding(0, 0, 0, 0))
                            .build().newBackgroundDrawable();
                    artistButton.setBackground(background);
                    artistButton.setFocusable(true);
                    artistButton.setClickable(true);
                    artistButton.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
                    artistButton.setTextColor(Theme.PRIMARY_COLOR);
                    artistButton.setTextSize(Theme.TEXT_SIZE_NORMAL);
                    artistButton.setText(artist.getName());
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
                Drawable background = ButtonInsetBackgroundFactory.builder()
                        .inset(0)
                        .cornerRadius(albumButton.dp(2))
                        .padding(new ButtonInsetBackgroundFactory.Padding(0, 0, 0, 0))
                        .build().newBackgroundDrawable();
                albumButton.setBackground(background);
                albumButton.setFocusable(true);
                albumButton.setClickable(true);
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

                instance.skipCurrentButton.setText(I18n.get(MusicHud.MOD_ID + ".button.voteForSkip"));
                instance.skipCurrentButton.setTextColor(Theme.NORMAL_TEXT_COLOR);
                instance.skipCurrentButton.setEnabled(true);
                instance.skipCurrentButton.setVisibility(clientConfig.getEnable() ? View.VISIBLE : View.GONE);
                instance.progressBar.setVisibility(View.VISIBLE);
                instance.skipCurrentButton.setVisibility(View.VISIBLE);
                startProgressUpdater(musicDetail);
            }
            HomeView homeView = HomeView.getInstance();
            if (homeView != null) {
                homeView.switchMusic(musicDetail, nextToPlay, lyricLines);
            }
        }
    }

    private static void startProgressUpdater(MusicDetail musicDetail) {
        NowPlayingInfo nowPlayingInfo = NowPlayingInfo.getInstance();
        MusicHud.EXECUTOR.execute(() -> {
            do {
                if (instance == null || instance.progressBar == null) {
                    return;
                }
                Duration playedDuration = nowPlayingInfo.getPlayedDuration();
                Duration musicDuration = nowPlayingInfo.getMusicDuration();
                DateTimeFormatter formatter = musicDuration.toHoursPart() >= 1 ?
                        DateTimeFormatter.ofPattern("HH:mm:ss") :
                        DateTimeFormatter.ofPattern("mm:ss");
                String playtimeText = formatter.format(
                        LocalTime.MIDNIGHT.plusSeconds(playedDuration.toSeconds())
                ) + " / " + formatter.format(
                        LocalTime.MIDNIGHT.plusSeconds(musicDuration.toSeconds())
                );
                MuiModApi.postToUiThread(() -> {
                    if (instance != null && instance.progressBar != null) {
                        instance.progressBar.setProgress((int) (nowPlayingInfo.getProgressRate() * 100));
                        instance.progressText.setText(playtimeText);
                    }
                });
                try {
                    Thread.sleep(Duration.of(50, ChronoUnit.MILLIS));
                } catch (InterruptedException e) {
                    return;
                }
            } while (musicDetail.equals(nowPlayingInfo.getCurrentlyPlayingMusicDetail())
                    && nowPlayingInfo.getProgressRate() < 1);
        });
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable DataSet savedInstanceState) {
        try {
            instance = this;
            var context = requireContext();
            var base = new LinearLayout(context);
            base.setPadding(base.dp(24), 0, base.dp(24), 0);

            var baseParams = new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT);
            base.setLayoutParams(baseParams);
            base.setOrientation(LinearLayout.HORIZONTAL);

            var routerContainer = new RouterContainer(context);
            routerContainer.setTransitionType(RouterContainer.TransitionType.FADE);
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

                var sideMenu = new SideMenu(context, routerContainer);
                if (Minecraft.getInstance().player != null) {//in game
                    var homeNav = sideMenu.createNavigationPage(I18n.get(MusicHud.MOD_ID + ".text.page.home"), HomeView::new);
                    var searchNav = sideMenu.createNavigationPage(I18n.get(MusicHud.MOD_ID + ".text.page.search"), SearchView::new);
                    var accountNav = sideMenu.createNavigationPage(I18n.get(MusicHud.MOD_ID + ".text.page.account"), AccountBaseView::new);
                    var settingsNav = sideMenu.createNavigationPage(I18n.get(MusicHud.MOD_ID + ".text.page.setting"), ConfigView::new);
                    SideMenu.NavigationMeta defaultMeta = List.of(homeNav, searchNav, accountNav, settingsNav).get(defaultSelectedIndex);
                    defaultMeta.select();
                } else {
                    var settingsNav = sideMenu.createNavigationPage(I18n.get(MusicHud.MOD_ID + ".text.page.setting"), ConfigView::new);
                    settingsNav.select();
                }

                int widthDp = base.dp(160);
                var params = new LinearLayout.LayoutParams(widthDp, MATCH_PARENT);
                params.gravity = Gravity.CENTER;
                albumImage = new UrlImageView(context);
                albumImage.loadUrl(MusicHud.ICON_BASE64);
                //noinspection SuspiciousNameCombination
                var imageParams = new FrameLayout.LayoutParams(widthDp, widthDp);
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

                pusherText = new TextView(context);
                pusherText.setTextColor(Theme.SECONDARY_TEXT_COLOR);
                pusherText.setTextSize(Theme.TEXT_SIZE_NORMAL);

                LinearLayout pusherRow = new LinearLayout(context);
                pusherRow.setOrientation(LinearLayout.HORIZONTAL);
                pusherRow.setGravity(Gravity.CENTER_VERTICAL);

                pusherHeadView = new PlayerHeadView(context);
                int rowHeight = pusherText.dp(Theme.TEXT_SIZE_LARGER);
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
                LinearLayout.LayoutParams params5 = new LinearLayout.LayoutParams(WRAP_CONTENT, rowHeight);
                params5.gravity = Gravity.LEFT | Gravity.CENTER_HORIZONTAL;
                params5.setMargins(pusherText.dp(4), 0, 0, 0);
                pusherRow.addView(pusherText, params5);
                LinearLayout.LayoutParams params6 = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
                int dp2 = musicInfo.dp(2);
                params6.setMargins(0, dp2, 0, dp2);
                musicInfo.addView(pusherRow, params6);

                progressBar = new ProgressBar(context, null, R.attr.progressBarStyleHorizontal);
                progressBar.setMin(0);
                progressBar.setMax(100);
                progressBar.setVisibility(View.GONE);
                StreamAudioPlayer streamAudioPlayer = StreamAudioPlayer.getInstance();
                StreamAudioPlayer.Status status = streamAudioPlayer.getStatus();
                checkAudioPlayerStatus(status);
                Consumer<StreamAudioPlayer.Status> statusListener = newStatus -> MuiModApi.postToUiThread(() -> {
                    checkAudioPlayerStatus(newStatus);
                });
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

                progressText = new TextView(context);
                progressText.setTextColor(Theme.SECONDARY_TEXT_COLOR);
                progressText.setTextSize(Theme.TEXT_SIZE_NORMAL);
                LinearLayout.LayoutParams params3 = new LinearLayout.LayoutParams(MATCH_PARENT, base.dp(16));
                params3.setMargins(0, sideContent.dp(4), 0, 0);
                musicInfo.addView(progressText, params3);

                skipCurrentButton = new Button(context);
                skipCurrentButton.setFocusable(true);
                skipCurrentButton.setClickable(true);
                skipCurrentButton.setTextSize(Theme.TEXT_SIZE_NORMAL);
                skipCurrentButton.setTextColor(Theme.NORMAL_TEXT_COLOR);
                skipCurrentButton.setGravity(Gravity.CENTER);
                skipCurrentButton.setText(I18n.get(MusicHud.MOD_ID + ".button.voteForSkip"));

                NowPlayingInfo nowPlayingInfo = NowPlayingInfo.getInstance();
                MusicDetail currentlyPlayingMusicDetail = nowPlayingInfo.getCurrentlyPlayingMusicDetail();
                MusicDetail nextToPlayMusicDetail = nowPlayingInfo.getNextToPlayIdleMusicDetail();

                skipCurrentButton.setHeight(skipCurrentButton.dp(40));

                var background = ButtonInsetBackgroundFactory.builder()
                        .padding(new ButtonInsetBackgroundFactory.Padding(skipCurrentButton.dp(2), skipCurrentButton.dp(1), skipCurrentButton.dp(2), skipCurrentButton.dp(1)))
                        .cornerRadius(skipCurrentButton.dp(4)).build().newBackgroundDrawable();
                skipCurrentButton.setBackground(background);
                skipCurrentButton.setOnClickListener((v) -> {
                    MusicService.getInstance().voteForSkipCurrent();
                    MuiModApi.postToUiThread(() -> {
                        skipCurrentButton.setTextColor(Theme.SECONDARY_TEXT_COLOR);
                        skipCurrentButton.setText(I18n.get(MusicHud.MOD_ID + ".text.voted"));
                        skipCurrentButton.setEnabled(false);
                    });
                });
                LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
                buttonParams.setMargins(0, sideContent.dp(2), 0, 0);

                var params1 = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
                params1.setMargins(sideContent.dp(8), sideContent.dp(4), sideContent.dp(8), sideContent.dp(24));

                musicInfo.addView(skipCurrentButton, buttonParams);
                musicInfo.setMinimumHeight(sideContent.dp(132));

                sideContent.addView(musicInfo, params1);
                sideContent.addView(sideMenu, params);

                var bottomBlank = new FrameLayout(context);
                sideContent.addView(bottomBlank, new FrameLayout.LayoutParams(MATCH_PARENT, base.dp(128)));

                var sideParams = new LinearLayout.LayoutParams(widthDp, MATCH_PARENT);
                sideParams.setMargins(0, sideContent.dp(32), 0, 0);

                sideScrollView.addView(sideContent, sideParams);


                LinearLayout serverConnectPanel = new LinearLayout(context);
                serverConnectPanel.setOrientation(LinearLayout.VERTICAL);

                serverConnectStatus = new TextView(context);
                serverConnectStatus.setTextSize(Theme.TEXT_SIZE_NORMAL);
                serverConnectStatus.setTextColor(Theme.SECONDARY_TEXT_COLOR);
                serverConnectPanel.addView(serverConnectStatus, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

                switchServerConnectButton = new Button(context);
                switchServerConnectButton.setFocusable(true);
                switchServerConnectButton.setClickable(true);
                switchServerConnectButton.setTextSize(Theme.TEXT_SIZE_NORMAL);
                switchServerConnectButton.setTextColor(Theme.NORMAL_TEXT_COLOR);
                switchServerConnectButton.setText(I18n.get(MusicHud.MOD_ID + ".button.logout"));
                switchServerConnectButton.setGravity(Gravity.CENTER);
                Drawable background1 = ButtonInsetBackgroundFactory.builder()
                        .inset(0)
                        .cornerRadius(switchServerConnectButton.dp(2))
                        .padding(new ButtonInsetBackgroundFactory.Padding(0, 0, 0, 0))
                        .build().newBackgroundDrawable();
                switchServerConnectButton.setBackground(background1);
                switchServerConnectButton.setOnClickListener(b -> {
                    MusicHud.EXECUTOR.execute(I_CLIENT_LOGIN_SERVICE::toggleConnection);
                });
                serverConnectPanel.addView(switchServerConnectButton, new LinearLayout.LayoutParams(MATCH_PARENT, base.dp(40)));
                refreshServerConnectStatus();
                LinearLayout.LayoutParams params4 = new LinearLayout.LayoutParams(widthDp, WRAP_CONTENT);
                params4.setMargins(0, serverConnectPanel.dp(8), 0, serverConnectPanel.dp(48));
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

                switchMusic(currentlyPlayingMusicDetail, nextToPlayMusicDetail, playingInfo.getLyricLines());
            }
            var params = new LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT, 0);
            params.setMargins(routerContainer.dp(80), 0, routerContainer.dp(64), 0);
            base.addView(routerContainer, params);

            return base;
        } catch (Exception e) {
            instance = null;
            throw e;
        }
    }

    private void refreshServerConnectStatus() {
        if (Minecraft.getInstance().player != null) {
            boolean singlePlayer = Minecraft.getInstance().getCurrentServer() == null;
            switch (MusicHud.getConnectStatus()) {
                case CONNECTED -> {
                    if (singlePlayer) {
                        serverConnectStatus.setText(I18n.get(MusicHud.MOD_ID + ".text.connected.integrated"));
                        switchServerConnectButton.setVisibility(View.GONE);
                    } else {
                        serverConnectStatus.setText(I18n.get(MusicHud.MOD_ID + ".text.connected"));
                        switchServerConnectButton.setVisibility(View.VISIBLE);
                        switchServerConnectButton.setText(I18n.get(MusicHud.MOD_ID + ".button.disconnect"));
                    }
                }
                case NOT_CONNECTED -> {
                    if (clientConfig.getEnableIsolatedMode()) {
                        serverConnectStatus.setText(I18n.get(MusicHud.MOD_ID + ".text.notConnected.isolated"));
                    } else {
                        serverConnectStatus.setText(I18n.get(MusicHud.MOD_ID + ".text.notConnected"));
                    }
                    switchServerConnectButton.setVisibility(View.VISIBLE);
                    switchServerConnectButton.setText(I18n.get(MusicHud.MOD_ID + ".button.connect"));
                }
                case INCOMPATIBLE -> {
                    if (clientConfig.getEnableIsolatedMode()) {
                        serverConnectStatus.setText(I18n.get(MusicHud.MOD_ID + ".text.incompatible"));
                    } else {
                        serverConnectStatus.setText(I18n.get(MusicHud.MOD_ID + ".text.incompatible.isolated"));
                    }
                    switchServerConnectButton.setVisibility(View.VISIBLE);
                    switchServerConnectButton.setText(I18n.get(MusicHud.MOD_ID + ".button.connect"));
                }
            }
        } else {
            serverConnectStatus.setText("");
            switchServerConnectButton.setVisibility(View.GONE);
        }
    }

    private void checkAudioPlayerStatus(StreamAudioPlayer.Status status) {
        progressBar.setIndeterminate(status == StreamAudioPlayer.Status.BUFFERING || status == StreamAudioPlayer.Status.RETRYING);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        instance = null;
    }
}