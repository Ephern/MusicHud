package indi.etern.musichud.client.ui.screen;

import dev.architectury.networking.NetworkManager;
import icyllis.modernui.ModernUI;
import icyllis.modernui.R;
import icyllis.modernui.animation.LayoutTransition;
import icyllis.modernui.annotation.Nullable;
import icyllis.modernui.core.Context;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.graphics.drawable.Drawable;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.*;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.*;
import indi.etern.musichud.client.config.ClientConfigDefinition;
import indi.etern.musichud.client.music.NowPlayingInfo;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.components.*;
import indi.etern.musichud.client.ui.pages.AccountBaseView;
import indi.etern.musichud.client.ui.pages.ConfigView;
import indi.etern.musichud.client.ui.pages.HomeView;
import indi.etern.musichud.client.ui.pages.SearchView;
import indi.etern.musichud.client.ui.utils.ButtonInsetBackground;
import indi.etern.musichud.network.pushMessages.c2s.VoteSkipCurrentMusicMessage;
import lombok.NonNull;
import lombok.Setter;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.language.I18n;

import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Queue;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class MainFragment extends Fragment {
    private static volatile MainFragment instance = null;
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

    public MainFragment() {
    }

    public static void refresh() {
        switchMusic(null, null);
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
            if (!ClientConfigDefinition.enable.get()) {
                instance.titleText.setText(I18n.get("music_hud.text.disabled"));
            } else if (MusicHud.getStatus() == MusicHud.ConnectStatus.NOT_CONNECTED) {
                instance.titleText.setText(I18n.get("music_hud.text.notConnected"));
            } else if (MusicHud.getStatus() == MusicHud.ConnectStatus.INCAPABLE) {
                instance.titleText.setText(I18n.get("music_hud.text.incapableWithServer"));
            } else {
                instance.titleText.setText(I18n.get("music_hud.text.idle"));
            }
        }
    }

    public static void switchMusic(MusicDetail musicDetail, Queue<LyricLine> lyricLines) {
        if (instance != null) {
            if (musicDetail == null || musicDetail.equals(MusicDetail.NONE)) {
                instance.albumImage.loadUrl(MusicHud.ICON_BASE64);
                if (!ClientConfigDefinition.enable.get()) {
                    instance.titleText.setText(I18n.get("music_hud.text.disabled"));
                } else if (MusicHud.getStatus() == MusicHud.ConnectStatus.NOT_CONNECTED) {
                    instance.titleText.setText(I18n.get("music_hud.text.notConnected"));
                } else if (MusicHud.getStatus() == MusicHud.ConnectStatus.INCAPABLE) {
                    instance.titleText.setText(I18n.get("music_hud.text.incapableWithServer"));
                } else {
                    instance.titleText.setText(I18n.get("music_hud.text.idle"));
                }
                instance.titleText.setTextColor(Theme.SECONDARY_TEXT_COLOR);
                instance.artists.removeAllViews();
                instance.albumContainer.removeAllViews();
                instance.pusherText.setText("");
                instance.progressBar.setVisibility(View.GONE);
                instance.progressText.setText("");
                instance.skipCurrentButton.setVisibility(View.GONE);
            } else {
                instance.titleText.setTextColor(Theme.NORMAL_TEXT_COLOR);
                instance.albumImage.loadUrl(musicDetail.getAlbum().getThumbnailPicUrl(200));
                instance.titleText.setText(musicDetail.getName());
                PlayerInfo pusherPlayerInfo = NowPlayingInfo.getInstance().getPusherPlayerInfo();
                String name = pusherPlayerInfo != null ? pusherPlayerInfo.getProfile().getName() : null;
                if (name == null || name.isEmpty()) {
                    instance.pusherText.setText("");
                } else {
                    instance.pusherText.setText(I18n.get("music_hud.text.pusherSource") + name);
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
                    Drawable background = ButtonInsetBackground.builder()
                            .inset(0)
                            .cornerRadius(artistButton.dp(2))
                            .padding(new ButtonInsetBackground.Padding(0, 0, 0, 0))
                            .build().get();
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
                Drawable background = ButtonInsetBackground.builder()
                        .inset(0)
                        .cornerRadius(albumButton.dp(2))
                        .padding(new ButtonInsetBackground.Padding(0, 0, 0, 0))
                        .build().get();
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

                instance.skipCurrentButton.setText(I18n.get("music_hud.button.voteForSkip"));
                instance.skipCurrentButton.setTextColor(Theme.NORMAL_TEXT_COLOR);
                instance.skipCurrentButton.setEnabled(true);
                instance.skipCurrentButton.setVisibility(ClientConfigDefinition.enable.get() ? View.VISIBLE : View.GONE);
                instance.progressBar.setVisibility(View.VISIBLE);
                instance.skipCurrentButton.setVisibility(View.VISIBLE);
                startProgressUpdater(musicDetail);
            }
            HomeView homeView = HomeView.getInstance();
            if (homeView != null) {
                homeView.switchMusic(lyricLines);
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

            var baseBackground = new ShapeDrawable();
            baseBackground.setColor(Theme.BASE_BACKGROUND_COLOR);
            base.setBackground(baseBackground);
            var baseParams = new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT);
            base.setLayoutParams(baseParams);
            base.setOrientation(LinearLayout.HORIZONTAL);

            var routerContainer = new RouterContainer(context);
            routerContainer.setTransitionType(RouterContainer.TransitionType.FADE);
            routerContainer.setAnimationDuration(300);

            {
                var sideMenu = new SideMenu(context, routerContainer);
                var homeNav = sideMenu.createNavigationPage(I18n.get("music_hud.text.page.home"), HomeView::new);
                var searchNav = sideMenu.createNavigationPage(I18n.get("music_hud.text.page.search"), SearchView::new);
                var accountNav = sideMenu.createNavigationPage(I18n.get("music_hud.text.page.account"), AccountBaseView::new);
                var settingsNav = sideMenu.createNavigationPage(I18n.get("music_hud.text.page.setting"), ConfigView::new);

                SideMenu.NavigationMeta defaultMeta = List.of(homeNav, searchNav, accountNav, settingsNav).get(defaultSelectedIndex);
                defaultMeta.select();

                int widthDp = base.dp(160);
                var params = new LinearLayout.LayoutParams(widthDp, MATCH_PARENT);
                params.gravity = Gravity.CENTER;
                var side = new LinearLayout(context);
                side.setOrientation(LinearLayout.VERTICAL);
                albumImage = new UrlImageView(context);
                albumImage.loadUrl(MusicHud.ICON_BASE64);
                //noinspection SuspiciousNameCombination
                var imageParams = new FrameLayout.LayoutParams(widthDp, widthDp);
                side.addView(albumImage, imageParams);

                LinearLayout musicInfo = new LinearLayout(context);
                musicInfo.setOrientation(LinearLayout.VERTICAL);

                titleText = new TextView(context);
                titleText.setTextSize(Theme.TEXT_SIZE_LARGE);
                titleText.setTextColor(Theme.NORMAL_TEXT_COLOR);
                if (!ClientConfigDefinition.enable.get()) {
                    instance.titleText.setText(I18n.get("music_hud.text.disabled"));
                } else if (MusicHud.getStatus() == MusicHud.ConnectStatus.NOT_CONNECTED) {
                    instance.titleText.setText(I18n.get("music_hud.text.notConnected"));
                } else if (MusicHud.getStatus() == MusicHud.ConnectStatus.INCAPABLE) {
                    instance.titleText.setText(I18n.get("music_hud.text.incapableWithServer"));
                } else {
                    instance.titleText.setText(I18n.get("music_hud.text.idle"));
                }
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
                musicInfo.addView(pusherText);

                progressBar = new ProgressBar(context, null, R.attr.progressBarStyleHorizontal);
                progressBar.setMin(0);
                progressBar.setMax(100);
                progressBar.setVisibility(View.GONE);
                LinearLayout.LayoutParams params2 = new LinearLayout.LayoutParams(MATCH_PARENT, base.dp(4));
                params2.setMargins(0, side.dp(1), 0, side.dp(-4));
                musicInfo.addView(progressBar, params2);

                progressText = new TextView(context);
                progressText.setTextColor(Theme.SECONDARY_TEXT_COLOR);
                progressText.setTextSize(Theme.TEXT_SIZE_NORMAL);
                LinearLayout.LayoutParams params3 = new LinearLayout.LayoutParams(MATCH_PARENT, base.dp(16));
                params3.setMargins(0, side.dp(4), 0, 0);
                musicInfo.addView(progressText, params3);

                skipCurrentButton = new Button(context);
                skipCurrentButton.setFocusable(true);
                skipCurrentButton.setClickable(true);
                skipCurrentButton.setTextSize(Theme.TEXT_SIZE_NORMAL);
                skipCurrentButton.setTextColor(Theme.NORMAL_TEXT_COLOR);
                skipCurrentButton.setGravity(Gravity.CENTER);
                skipCurrentButton.setText(I18n.get("music_hud.button.voteForSkip"));

                MusicDetail currentlyPlayingMusicDetail = NowPlayingInfo.getInstance().getCurrentlyPlayingMusicDetail();

                skipCurrentButton.setHeight(skipCurrentButton.dp(40));

                var background = ButtonInsetBackground.builder()
                        .padding(new ButtonInsetBackground.Padding(skipCurrentButton.dp(2), skipCurrentButton.dp(1), skipCurrentButton.dp(2), skipCurrentButton.dp(1)))
                        .cornerRadius(skipCurrentButton.dp(4)).build().get();
                skipCurrentButton.setBackground(background);
                skipCurrentButton.setOnClickListener((v) -> {
                    if (NowPlayingInfo.getInstance().getCurrentlyPlayingMusicDetail() != null) {
                        NetworkManager.sendToServer(new VoteSkipCurrentMusicMessage(NowPlayingInfo.getInstance().getCurrentlyPlayingMusicDetail().getId()));
                        MuiModApi.postToUiThread(() -> {
                            skipCurrentButton.setTextColor(Theme.SECONDARY_TEXT_COLOR);
                            skipCurrentButton.setText(I18n.get("music_hud.text.voted"));
                            skipCurrentButton.setEnabled(false);
                        });
                    }
                });
                LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
                buttonParams.setMargins(0, side.dp(2), 0, 0);

                var params1 = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
                params1.setMargins(side.dp(8), side.dp(4), side.dp(8), side.dp(24));

                musicInfo.addView(skipCurrentButton, buttonParams);
                musicInfo.setMinimumHeight(side.dp(128));

                side.addView(musicInfo, params1);
                side.addView(sideMenu, params);

                var sideParams = new LinearLayout.LayoutParams(widthDp, MATCH_PARENT);
                sideParams.setMargins(0, side.dp(24), 0, 0);
                base.addView(side, sideParams);

                LayoutTransition transition1 = new LayoutTransition();
                transition1.enableTransitionType(LayoutTransition.CHANGING);
                musicInfo.setLayoutTransition(transition1);

                LayoutTransition transition2 = new LayoutTransition();
                transition2.enableTransitionType(LayoutTransition.CHANGING);
                side.setLayoutTransition(transition2);

                switchMusic(currentlyPlayingMusicDetail, playingInfo.getLyricLines());
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        instance = null;
    }
}