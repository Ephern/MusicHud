package indi.etern.musichud.client.ui.components;

import icyllis.modernui.ModernUI;
import icyllis.modernui.R;
import icyllis.modernui.animation.LayoutTransition;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Image;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.text.SpannableString;
import icyllis.modernui.text.Spanned;
import icyllis.modernui.text.style.ImageSpan;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ProgressBar;
import icyllis.modernui.widget.TextView;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.Album;
import indi.etern.musichud.beans.music.Artist;
import indi.etern.musichud.beans.music.MusicCollection;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.beans.music.SourceMeta;
import indi.etern.musichud.beans.music.Traceable;
import indi.etern.musichud.client.audio.NowPlayingInfo;
import indi.etern.musichud.client.services.music.MusicService;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.components.buttons.ModifyPlaylistTrackModalButton;
import indi.etern.musichud.client.ui.components.buttons.ToggleTrackLikeStateButton;
import indi.etern.musichud.client.ui.components.buttons.VoteSkipButton;
import indi.etern.musichud.client.ui.layouts.FlexWrapLayout;
import indi.etern.musichud.client.ui.pages.routes.ArtistDetailView;
import indi.etern.musichud.client.ui.pages.routes.MusicCollectionDetailView;
import indi.etern.musichud.client.utils.PlayerInfoUtil;
import indi.etern.musichud.client.utils.image.ImageUtils;
import indi.etern.musichud.client.utils.ui.InsetBackgroundFactory;
import indi.etern.musichud.server.api.playmode.PlayMode;
import lombok.Getter;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.language.I18n;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

@Getter
public class MusicInfoCard extends LinearLayout {
    private final UrlImageView albumImage;
    private final LinearLayout musicInfo;
    private final TextView titleText;
    private final FlexWrapLayout artists;
    private final LinearLayout albumContainer;
    private final PlayerHeadView pusherHeadView;
    private final TextView pusherText;
    private final Button sourceButton;
    private final ProgressBar progressBar;
    private final TextView playedTimeText;
    private final TextView totalTimeText;
    private final LinearLayout buttonsLayout;
    private final ToggleTrackLikeStateButton likeButton;
    private final ModifyPlaylistTrackModalButton addToPlaylistButton;
    private final VoteSkipButton skipCurrentButton;
    private final LayoutTransition layoutTransition;

    public MusicInfoCard(Context context, int sideWidth) {
        super(context);
        setOrientation(VERTICAL);

        albumImage = new UrlImageView(context);
        albumImage.loadUrl(MusicHud.ICON_BASE64);
        albumImage.setCornerRadius(dp(12));
        //noinspection SuspiciousNameCombination
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(sideWidth, sideWidth);
        imageParams.gravity = Gravity.CENTER;
        addView(albumImage, imageParams);

        musicInfo = new LinearLayout(context);
        musicInfo.setOrientation(LinearLayout.VERTICAL);

        titleText = new TextView(context);
        titleText.setTextSize(Theme.TEXT_SIZE_LARGE);
        titleText.setTextColor(Theme.NORMAL_TEXT_COLOR);
        titleText.setText(I18n.get(MusicHud.MOD_ID + ".text.idle"));
        musicInfo.addView(titleText);

        artists = new FlexWrapLayout(context);
        artists.setMinimumHeight(artists.dp(16));
        artists.setLayoutTransition(null);
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
        LinearLayout.LayoutParams pusherTextParams = new LinearLayout.LayoutParams(WRAP_CONTENT, rowHeight);
        pusherTextParams.gravity = Gravity.LEFT | Gravity.CENTER_HORIZONTAL;
        pusherTextParams.setMargins(pusherText.dp(4), 0, 0, 0);
        pusherRow.addView(pusherText, pusherTextParams);

        LinearLayout.LayoutParams pusherRowParams = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        int dp2 = musicInfo.dp(2);
        pusherRowParams.setMargins(0, dp2, 0, dp2);
        musicInfo.addView(pusherRow, pusherRowParams);

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
                                    MuiModApi.postToUiThread(() -> {
                                        RouterContainer router = RouterContainer.getInstance();
                                        if (router != null) {
                                            router.pushNavigate(new MusicCollectionDetailView(context, musicCollection));
                                        }
                                    }));
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
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(MATCH_PARENT, dp(4));
        progressParams.setMargins(0, dp(1), 0, -dp(4));
        musicInfo.addView(progressBar, progressParams);

        LinearLayout progressTexts = new LinearLayout(context);
        progressTexts.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams progressTextsParams = new LinearLayout.LayoutParams(MATCH_PARENT, dp(16));
        progressTextsParams.setMargins(0, dp(6), 0, 0);
        musicInfo.addView(progressTexts, progressTextsParams);

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

        InsetBackgroundFactory buttonsBackground = InsetBackgroundFactory.builder()
                .backgroundColor(Theme.GHOST_BUTTON_STATES)
                .padding(new InsetBackgroundFactory.Padding(buttonsLayout.dp(2), buttonsLayout.dp(1), buttonsLayout.dp(2), buttonsLayout.dp(1)))
                .cornerRadius(buttonsLayout.dp(4)).build();
        {
            likeButton = new ToggleTrackLikeStateButton(context);
            buttonsBackground.applyBackgroundTo(likeButton);
            buttonsLayout.addView(likeButton, new LinearLayout.LayoutParams(0, MATCH_PARENT, 1));
        }
        {
            addToPlaylistButton = new ModifyPlaylistTrackModalButton(context);
            buttonsBackground.applyBackgroundTo(addToPlaylistButton);
            buttonsLayout.addView(addToPlaylistButton, new LinearLayout.LayoutParams(0, MATCH_PARENT, 1));
        }
        {
            skipCurrentButton = new VoteSkipButton(context);
            buttonsBackground.applyBackgroundTo(skipCurrentButton);
            buttonsLayout.addView(skipCurrentButton, new LinearLayout.LayoutParams(0, MATCH_PARENT, 1));
        }

        LinearLayout.LayoutParams buttonsParams = new LinearLayout.LayoutParams(MATCH_PARENT, dp(40));
        buttonsParams.setMargins(0, dp(2), 0, 0);
        musicInfo.addView(buttonsLayout, buttonsParams);
        musicInfo.setMinimumHeight(dp(132));

        LinearLayout.LayoutParams musicInfoParams = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        musicInfoParams.setMargins(dp(4), dp(4), dp(4), 0);
        addView(musicInfo, musicInfoParams);

        layoutTransition = new LayoutTransition();
        layoutTransition.enableTransitionType(LayoutTransition.CHANGING);
        layoutTransition.setAnimateParentHierarchy(false);
        musicInfo.setLayoutTransition(layoutTransition);
    }

    /**
     * Resets this card to an empty state without playing its own child transitions, so a
     * recycled (off-screen) card never leaks stale content into its next slide-in.
     */
    public void clear() {
        musicInfo.setLayoutTransition(null);
        try {
            albumImage.clear();
            titleText.setText("");
            artists.removeAllViews();
            albumContainer.removeAllViews();
            pusherHeadView.setVisibility(View.GONE);
            pusherText.setText("");
            sourceButton.setVisibility(View.GONE);
            sourceButton.setTag(null);
            progressBar.setIndeterminate(false);
            progressBar.setProgress(0);
            progressBar.setVisibility(View.GONE);
            playedTimeText.setText("");
            totalTimeText.setText("");
            buttonsLayout.setVisibility(View.GONE);
            likeButton.bindMusicList(null);
            addToPlaylistButton.bindMusicDetail(null);
            skipCurrentButton.reset();
        } finally {
            musicInfo.setLayoutTransition(layoutTransition);
        }
    }

    /**
     * Binds the given music to this card. Idle / null / NONE renders the idle placeholder.
     *
     * @param sideWidth current side panel width in px, used for the album thumbnail size
     */
    public void bind(Traceable<MusicDetail> musicDetailTrace, int sideWidth) {
        if (musicDetailTrace == null || musicDetailTrace.value() == null || musicDetailTrace.value().equals(MusicDetail.NONE)) {
            albumImage.loadUrl(MusicHud.ICON_BASE64);
            titleText.setText(I18n.get(MusicHud.MOD_ID + ".text.idle"));
            titleText.setTextColor(Theme.SECONDARY_TEXT_COLOR);
            artists.removeAllViews();
            albumContainer.removeAllViews();
            pusherHeadView.setVisibility(View.GONE);
            pusherText.setText("");
            sourceButton.setVisibility(View.GONE);
            sourceButton.setTag(null);
            progressBar.setVisibility(View.GONE);
            playedTimeText.setText("");
            totalTimeText.setText("");
            buttonsLayout.setVisibility(View.GONE);
            likeButton.bindMusicList(null);
            addToPlaylistButton.bindMusicDetail(null);
        } else {
            MusicDetail musicDetail = musicDetailTrace.value();
            titleText.setTextColor(Theme.NORMAL_TEXT_COLOR);
            Album album = musicDetail.getAlbum();
            albumImage.loadUrl(album.getImageThumbnailUrl(sideWidth));
            titleText.setText(musicDetail.getName());
            PlayerInfo pusherPlayerInfo = NowPlayingInfo.getInstance().getPusherPlayerInfo();
            String name = pusherPlayerInfo != null ? pusherPlayerInfo.getProfile().getName() : null;
            if (name == null || name.isEmpty()) {
                pusherHeadView.setVisibility(View.GONE);
                pusherText.setText("");
            } else {
                pusherHeadView.setVisibility(View.VISIBLE);
                pusherText.setText(name);
            }
            SourceMeta source = musicDetailTrace.source();
            if (source != null) {
                sourceButton.setTag(source);
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

                sourceButton.setVisibility(View.VISIBLE);
                sourceButton.setText(text);
            } else {
                sourceButton.setVisibility(View.GONE);
                sourceButton.setTag(null);
            }
            Context context = ModernUI.getInstance();
            artists.removeAllViews();
            int index = 0;
            InsetBackgroundFactory backgroundFactory = InsetBackgroundFactory.builder()
                    .inset(0)
                    .cornerRadius(buttonsLayout.dp(2))
                    .padding(new InsetBackgroundFactory.Padding(0, 0, 0, 0))
                    .build();
            for (Artist artist : musicDetail.getArtists()) {
                if (index != 0) {
                    TextView split = new TextView(context);
                    split.setTextColor(Theme.SECONDARY_TEXT_COLOR);
                    split.setTextSize(Theme.TEXT_SIZE_SMALL);
                    split.setText(" / ");
                    split.setSingleLine();
                    artists.addView(split);
                }
                index++;
                Button artistButton = new Button(context);
                backgroundFactory.applyBackgroundTo(artistButton);
                artistButton.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
                boolean validArtist = artist.getId() > 0;
                artistButton.setEnabled(validArtist);
                artistButton.setTextColor(validArtist ? Theme.PRIMARY_COLOR : Theme.SECONDARY_TEXT_COLOR);
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
                artists.addView(artistButton);
            }

            albumContainer.removeAllViews();
            Button albumButton = new Button(context);
            backgroundFactory.applyBackgroundTo(albumButton);
            boolean validAlbum = album.getId() > 0;
            albumButton.setEnabled(validAlbum);
            albumButton.setTextColor(validAlbum ? Theme.PRIMARY_COLOR : Theme.SECONDARY_TEXT_COLOR);
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
            albumContainer.addView(albumButton);

            skipCurrentButton.reset();
            progressBar.setVisibility(View.VISIBLE);
            likeButton.bindMusicList(MusicService.getInstance().getMusicTrackState(musicDetail).currentUsersLikeList());
            addToPlaylistButton.bindMusicDetail(musicDetail);
            buttonsLayout.setVisibility(View.VISIBLE);
        }
    }
}
