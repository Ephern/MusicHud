package indi.etern.musichud.client.ui.components;

import icyllis.modernui.animation.LayoutTransition;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Image;
import icyllis.modernui.graphics.drawable.InsetDrawable;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.text.SpannableString;
import icyllis.modernui.text.Spanned;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.*;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.Artist;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.beans.music.Traceable;
import indi.etern.musichud.client.services.music.MusicService;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.ToastUtil;
import indi.etern.musichud.client.ui.drawable.ScaledImageDrawable;
import indi.etern.musichud.client.utils.image.ImageUtils;
import indi.etern.musichud.client.utils.ui.InsetBackgroundFactory;
import indi.etern.musichud.interfaces.IClientMusicService;
import net.minecraft.client.resources.language.I18n;

import java.util.stream.Collectors;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class ArtistDetailView extends LinearLayout {
    private final LinearLayout musicList;
    private final TextView noMoreResultText;
    private final ProgressBar loadingProgressRing;
    private final ProgressBar progressBar;
    private final TextView productionCounts;
    private final TextView description;
    private final UrlImageView avatarImageView;
    private static final IClientMusicService musicService = MusicService.getInstance();
    private final InsetBackgroundFactory itemBackgroundFactory = InsetBackgroundFactory.builder()
            .cornerRadius(dp(12))
            .inset(dp(1))
            .padding(new InsetBackgroundFactory.Padding(dp(4), dp(4), dp(4), dp(4))).build();
    Artist artist;

    public ArtistDetailView(Context context, Artist artist) {
        super(context);

        this.artist = artist;
        setOrientation(VERTICAL);

        LinearLayout topBar = new LinearLayout(context);
        topBar.setOrientation(HORIZONTAL);

        LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        topBar.setLayoutParams(params);

        ImageButton backButton = new ImageButton(context);
        String tooltipText = I18n.get(MusicHud.MOD_ID + ".button.back");
        Image image = ImageUtils.getImageFromResource("/assets/music_hud/textures/gui/icons/arrow_left.png");
        if (image != null) {
            backButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            backButton.setImageDrawable(new ScaledImageDrawable(getContext().getResources(), image, dp(16), dp(16)));
        }
        backButton.setTooltipText(tooltipText);
        backButton.setOnClickListener(view -> {
            RouterContainer.getInstance().popNavigate();
            backButton.setOnClickListener(null);
        });
        InsetBackgroundFactory backgroundFactory = InsetBackgroundFactory.builder()
                .inset(0)
                .cornerRadius(dp(8))
                .padding(new InsetBackgroundFactory.Padding(dp(16), 0, dp(16), 0))
                .build();
        backgroundFactory.applyBackgroundTo(backButton);
        LayoutParams backButtonParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
        backButtonParams.setMargins(0, 0, dp(4), 0);
        topBar.addView(backButton, backButtonParams);

        avatarImageView = new UrlImageView(context);
        avatarImageView.setLoading(true);
        LayoutParams imageParams = new LayoutParams(dp(128), dp(128));
        avatarImageView.setCircular(true);
        topBar.addView(avatarImageView, imageParams);

        LinearLayout artistInfoView = new LinearLayout(context);
        artistInfoView.setGravity(Gravity.LEFT | Gravity.TOP);
        artistInfoView.setOrientation(VERTICAL);
        LayoutParams params1 = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        params1.setMargins(dp(16), 0, 0, 0);
        topBar.addView(artistInfoView, params1);

        LinearLayout row1 = new LinearLayout(context);
        row1.setOrientation(HORIZONTAL);
        row1.setGravity(Gravity.CENTER_VERTICAL);
        LayoutTransition layoutTransition = new LayoutTransition();
        layoutTransition.enableTransitionType(LayoutTransition.CHANGING);
        layoutTransition.enableTransitionType(LayoutTransition.APPEARING);
        layoutTransition.enableTransitionType(LayoutTransition.DISAPPEARING);
        row1.setLayoutTransition(layoutTransition);
        LayoutParams row1Params = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        row1Params.setMargins(0, 0, 0, dp(8));
        artistInfoView.addView(row1, row1Params);

        TextView name = new TextView(context);
        name.setTextSize(Theme.TEXT_SIZE_LARGER);
        name.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
        name.setText(artist.getName());
        LayoutParams nameParams = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nameParams.setMargins(0, 0, dp(16), 0);
        row1.addView(name, nameParams);

        productionCounts = new TextView(context);
        productionCounts.setTextSize(Theme.TEXT_SIZE_LARGE);
        productionCounts.setTextColor(Theme.NORMAL_TEXT_COLOR);
        productionCounts.setVisibility(GONE);
        LayoutParams productionCountsParams = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
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
            refreshButton.setTooltipText(I18n.get(MusicHud.MOD_ID + ".button.refresh"));
            refreshButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            var resources = getContext().getResources();
            Image image1 = ImageUtils.getImageFromResource("/assets/music_hud/textures/gui/icons/rotate_cw.png");
            refreshButton.setImageDrawable(new InsetDrawable(new ScaledImageDrawable(resources, image1, dp(12), dp(16)), dp(3)));
            refreshButton.setOnClickListener((v) -> {
                loadData(true);
            });
            row1.addView(refreshButton, new LayoutParams(dp28, dp28));
        }
        {
            ToggleSubscribeButton toggleSubscribeButton = new ToggleSubscribeButton(context);
            backgroundFactory1.applyBackgroundTo(toggleSubscribeButton);
            row1.addView(toggleSubscribeButton, new LayoutParams(dp28, dp28, 0));
            var subscribeState = musicService.getArtistSubscribedState(artist);
            toggleSubscribeButton.bindState(subscribeState);
        }

        ScrollView descriptionScrollView = new ScrollView(context);
        LayoutParams scrollParams = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        descriptionScrollView.setLayoutParams(scrollParams);

        description = new TextView(context);
        description.setTextSize(Theme.TEXT_SIZE_NORMAL);
        description.setTextColor(Theme.SECONDARY_TEXT_COLOR);
        LayoutParams descriptionParams = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        description.setLayoutParams(descriptionParams);
        descriptionScrollView.addView(description);

        artistInfoView.addView(descriptionScrollView);

        LayoutParams topBarParams = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        topBarParams.setMargins(0, dp(24), 0, 0);
        addView(topBar, topBarParams);

        progressBar = new ProgressBar(context);
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

        scrollView.addView(musicListWrapper, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        scrollView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            checkInfiniteScroll(scrollY, scrollView);
        });

        loadData(false);
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
                    avatarImageView.loadUrl(artist1.getAvatarThumbnailUrl(dp(128)));
                    musicList.removeAllViews();
                    for (MusicDetail musicDetail : artist1.getMusicDetails()) {
                        addItem(getContext(), musicDetail);
                    }
                    progressBar.setVisibility(GONE);
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
            ToastUtil.show(Toast.makeText(context, I18n.get(MusicHud.MOD_ID + ".text.pushedMusicToPlaylist") + "\n" + musicDetail.getName() + " - " + artistsName, Toast.LENGTH_SHORT));
        });
        musicList.addView(musicLayout);
    }
}
