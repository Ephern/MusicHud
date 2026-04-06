package indi.etern.musichud.client.ui.components;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.Drawable;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.*;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.Artist;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.client.services.MusicService;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.ToastUtil;
import indi.etern.musichud.client.ui.utils.ButtonInsetBackgroundFactory;
import net.minecraft.client.resources.language.I18n;

import java.util.stream.Collectors;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class ArtistDetailView extends LinearLayout {
    private final LinearLayout musicList;
    private final TextView noMoreResultText;
    private final ProgressBar loadingProgressRing;
    Artist artist;

    public ArtistDetailView(Context context, Artist artist) {
        super(context);

        this.artist = artist;
        setOrientation(VERTICAL);

        LinearLayout topBar = new LinearLayout(context);
        topBar.setOrientation(HORIZONTAL);

        LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        topBar.setLayoutParams(params);

        Button backButton = new Button(context);
        backButton.setText(I18n.get("music_hud.button.back"));
        backButton.setTextColor(Theme.NORMAL_TEXT_COLOR);
        backButton.setOnClickListener(view -> {
            RouterContainer.getInstance().popNavigate();
            backButton.setOnClickListener(null);
        });
        Drawable drawable = ButtonInsetBackgroundFactory.builder()
                .inset(0)
                .cornerRadius(dp(8))
                .padding(new ButtonInsetBackgroundFactory.Padding(dp(16), 0, dp(16), 0))
                .build().newBackgroundDrawable();
        backButton.setBackground(drawable);
        LayoutParams backButtonParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
        backButtonParams.setMargins(0, 0, dp(4), 0);
        topBar.addView(backButton, backButtonParams);

        UrlImageView avatarImageView = new UrlImageView(context);
        avatarImageView.setLoading(true);
        LayoutParams imageParams = new LayoutParams(dp(128), dp(128));
        avatarImageView.setCircular(true);
        topBar.addView(avatarImageView, imageParams);

        LinearLayout texts = new LinearLayout(context);
        texts.setGravity(Gravity.LEFT | Gravity.TOP);
        texts.setOrientation(VERTICAL);
        LayoutParams params1 = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        params1.setMargins(dp(16), 0, 0, 0);
        topBar.addView(texts, params1);

        TextView name = new TextView(context);
        name.setTextSize(Theme.TEXT_SIZE_LARGER);
        name.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
        name.setText(artist.getName());
        LayoutParams nameParams = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nameParams.setMargins(0, 0, 0, dp(4));
        name.setLayoutParams(nameParams);
        texts.addView(name);

        TextView briefInfo = new TextView(context);
        briefInfo.setTextSize(Theme.TEXT_SIZE_NORMAL);
        briefInfo.setTextColor(Theme.SECONDARY_TEXT_COLOR);
        LayoutParams infoParams = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        infoParams.setMargins(0, 0, 0, dp(4));
        briefInfo.setLayoutParams(infoParams);
        texts.addView(briefInfo);

        ScrollView descriptionScrollView = new ScrollView(context);
        LayoutParams scrollParams = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        descriptionScrollView.setLayoutParams(scrollParams);

        TextView description = new TextView(context);
        description.setTextSize(Theme.TEXT_SIZE_NORMAL);
        description.setTextColor(Theme.SECONDARY_TEXT_COLOR);
        LayoutParams descriptionParams = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        description.setLayoutParams(descriptionParams);
        descriptionScrollView.addView(description);

        texts.addView(descriptionScrollView);

        LayoutParams topBarParams = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        topBarParams.setMargins(0, dp(24), 0, 0);
        addView(topBar, topBarParams);

        ProgressBar progressBar = new ProgressBar(context);
        progressBar.setIndeterminate(true);
        LayoutParams progressParams = new LayoutParams(MATCH_PARENT, MATCH_PARENT);
        progressParams.setMargins(0, dp(32), 0, 0);
        addView(progressBar, progressParams);

        var scrollView = new ScrollView(context);
        scrollView.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);
        scrollView.setFillViewport(true);
        LayoutParams tracksParams = new LayoutParams(MATCH_PARENT, MATCH_PARENT);
        tracksParams.setMargins(0, dp(24), 0, 0);
        addView(scrollView, tracksParams);

        LinearLayout musicListWrapper = new LinearLayout(context);
        musicListWrapper.setOrientation(VERTICAL);
        musicListWrapper.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);

        musicList = new LinearLayout(context);
        musicList.setOrientation(VERTICAL);
        musicListWrapper.addView(musicList, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));

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
            noMoreResultText.setText(I18n.get("music_hud.text.searchNoMoreResult"));
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

        scrollView.addView(musicListWrapper, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        scrollView.setOnScrollChangeListener(new OnScrollChangeListener() {
            @Override
            public void onScrollChange(View v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
                checkInfiniteScroll(scrollY, scrollView);
            }
        });

        artist.loadDetail().thenAcceptAsync(artist1 -> {
            if (artist1 != null) {
                ArtistDetailView.this.artist = artist1;
                MuiModApi.postToUiThread(() -> {
                    briefInfo.setText(
                            I18n.get("music_hud.text.artist.album").replace("{}", String.valueOf(artist1.getAlbumCount()))
                                    + "  |  " + I18n.get("music_hud.text.artist.music").replace("{}", String.valueOf(artist1.getMusicCount())));
                    description.setText(artist1.getDescription());
                    avatarImageView.loadUrl(artist1.getAvatarThumbnailUrl(dp(128)));
                    removeView(progressBar);
                    for (MusicDetail musicDetail : artist1.getMusicDetails()) {
                        addItem(context, musicDetail);
                    }
                });
            }
        }, MusicHud.EXECUTOR);
    }

    boolean noMoreResult = false;
    boolean loadingMore = false;
    private void checkInfiniteScroll(int scrollY, ScrollView sv) {
        if (loadingMore || noMoreResult) return;
        if (sv.getChildCount() > 0) {
            View child = sv.getChildAt(0);
            int contentHeight = child.getHeight();
            int viewHeight = sv.getHeight();
            int maxScroll = Math.max(0, contentHeight - viewHeight);
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
                    artist.loadMoreMusic().thenAccept((musicDetails) -> {
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
        var musicLayout = new MusicListItem(context);
        musicLayout.setShowPusherInfo(false);
        musicLayout.bindData(musicDetail);
        var background = ButtonInsetBackgroundFactory.builder()
                .cornerRadius(dp(12))
                .inset(dp(1))
                .padding(new ButtonInsetBackgroundFactory.Padding(dp(4), dp(4), dp(4), dp(4))).build().newBackgroundDrawable();
        musicLayout.setBackground(background);

        musicLayout.setClickable(true);
        String artistsName = musicDetail.getArtists().stream()
                .map(Artist::getName).collect(Collectors.joining(" / "));
        musicLayout.setOnClickListener((view) -> {
            MusicService.getInstance().sendPushMusicToQueue(musicDetail);
            ToastUtil.show(Toast.makeText(context, I18n.get("music_hud.text.pushedMusicToPlaylist") + "\n" + musicDetail.getName() + " - " + artistsName, Toast.LENGTH_SHORT));
        });
        musicList.addView(musicLayout);
    }
}
