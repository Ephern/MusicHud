package indi.etern.musichud.client.ui.components;

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
import icyllis.modernui.widget.TextView;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.*;
import indi.etern.musichud.client.services.music.MusicService;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.components.buttons.ModifyPlaylistTrackModalButton;
import indi.etern.musichud.client.ui.components.buttons.ToggleTrackLikeStateButton;
import indi.etern.musichud.client.ui.layouts.FlexWrapLayout;
import indi.etern.musichud.client.ui.pages.routes.ArtistDetailView;
import indi.etern.musichud.client.ui.pages.routes.MusicCollectionDetailView;
import indi.etern.musichud.client.utils.PlayerInfoUtil;
import indi.etern.musichud.client.utils.image.ImageUtils;
import indi.etern.musichud.client.utils.ui.InsetBackgroundFactory;
import indi.etern.musichud.server.api.playmode.PlayMode;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.resources.language.I18n;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class MusicTrackItem extends LinearLayout {
    public static final int imageSize = 56;
    private static final MusicService musicService = MusicService.getInstance();
    private static final DateTimeFormatter PLAY_RECORD_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
    private final DateTimeFormatter timeFormatterWithHour = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("mm:ss");
    @Getter
    private UrlImageView albumImageView;
    private TextView musicName;
    private TextView feeLabel;
    private FlexWrapLayout row2;
    private TextView durationText;
    private TextView pusherText;
    @Setter
    @Getter
    private boolean showPusherInfo = true;
    @Getter
    private MusicDetail musicDetail;
    @Getter
    private Traceable<MusicDetail> musicTrace;
    private PlayerHeadView pusherHeadView;
    private ToggleTrackLikeStateButton likeButton;
    @Getter
    private LinearLayout buttonsLayout;
    private ModifyPlaylistTrackModalButton addToPlaylistButton;
    private Button sourceButton;
    private TextView playRecordTimeText;
    private LinearLayout pusherInfo;
    @Getter
    private LinearLayout infoRow;

    public MusicTrackItem(Context context) {
        super(context);
        initView(context);
    }

    private void initView(Context context) {
        setOrientation(HORIZONTAL);
        LayoutParams musicLayoutParams = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        setLayoutParams(musicLayoutParams);
        setGravity(Gravity.CENTER_VERTICAL);

        albumImageView = new UrlImageView(context);
        albumImageView.setCornerRadius(dp(4));
        albumImageView.setAspectRatio(1);
        addView(albumImageView, new LayoutParams(dp(imageSize), dp(imageSize)));

        LinearLayout musicTexts = new LinearLayout(context);
        musicTexts.setOrientation(VERTICAL);
        musicTexts.setGravity(Gravity.CENTER_VERTICAL);
        LayoutParams textsParams = new LayoutParams(0, WRAP_CONTENT, 1);
        textsParams.setMargins(dp(12), 0, 0, 0);
        addView(musicTexts, textsParams);

        LinearLayout row1 = new LinearLayout(context);
        row1.setOrientation(HORIZONTAL);
        row1.setGravity(Gravity.CENTER_VERTICAL);
        musicTexts.addView(row1);

        musicName = new TextView(context);
        musicName.setSingleLine(true);
        musicName.setTextSize(Theme.TEXT_SIZE_LARGE);
        musicName.setTextColor(Theme.NORMAL_TEXT_COLOR);
        row1.addView(musicName, new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));

        row2 = new FlexWrapLayout(context);
        row2.setAnimationsEnabled(false);
        musicTexts.addView(row2, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        infoRow = new LinearLayout(context);
        infoRow.setOrientation(HORIZONTAL);
        infoRow.setGravity(Gravity.CENTER_VERTICAL);
        musicTexts.addView(infoRow);

        durationText = new TextView(context);
        durationText.setTextSize(Theme.TEXT_SIZE_NORMAL);
        durationText.setSingleLine(true);
        durationText.setTextColor(Theme.SECONDARY_TEXT_COLOR);

        LayoutParams params = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        params.setMargins(0, 0, dp(12), 0);
        infoRow.addView(durationText, params);

        feeLabel = new TextView(context);
        feeLabel.setSingleLine(true);
        feeLabel.setTextSize(Theme.TEXT_SIZE_NORMAL);
        feeLabel.setTextColor(Theme.SECONDARY_TEXT_COLOR);
        feeLabel.setVisibility(GONE);
        LayoutParams params1 = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        params1.setMargins(0, 0, dp(12), 0);
        infoRow.addView(feeLabel, params1);

        pusherInfo = new LinearLayout(context);
        pusherInfo.setOrientation(LinearLayout.HORIZONTAL);
        pusherInfo.setGravity(Gravity.CENTER_VERTICAL);
        pusherInfo.setVisibility(GONE);
        LayoutParams params2 = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        params2.setMargins(0, 0, dp(12), 0);
        infoRow.addView(pusherInfo, params2);

        pusherText = new TextView(context);
        pusherText.setTextColor(Theme.SECONDARY_TEXT_COLOR);
        pusherText.setTextSize(Theme.TEXT_SIZE_NORMAL);
        pusherText.setTextAlignment(TEXT_ALIGNMENT_TEXT_START);

        pusherHeadView = new PlayerHeadView(context);
        int rowHeight = pusherText.dp(Theme.TEXT_SIZE_LARGER);
        //noinspection SuspiciousNameCombination
        pusherHeadView.setLayoutParams(new LinearLayout.LayoutParams(rowHeight, rowHeight));
        pusherHeadView.setVisibility(View.GONE);

        pusherInfo.addView(pusherHeadView);
        LinearLayout.LayoutParams params5 = new LinearLayout.LayoutParams(WRAP_CONTENT, rowHeight);
        params5.gravity = Gravity.LEFT | Gravity.CENTER_HORIZONTAL;
        params5.setMargins(pusherText.dp(4), 0, 0, 0);
        pusherInfo.addView(pusherText, params5);

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
                                    MuiModApi.postToUiThread(() -> RouterContainer.getInstance().pushNavigate(
                                            new MusicCollectionDetailView(getContext(), musicCollection)))
                            );
                }
            }
        });
        InsetBackgroundFactory.builder()
                .backgroundColor(Theme.GHOST_BUTTON_STATES)
                .padding(new InsetBackgroundFactory.Padding(0, sourceButton.dp(1), 0, sourceButton.dp(1)))
                .cornerRadius(sourceButton.dp(4)).build().applyBackgroundTo(sourceButton);
        infoRow.addView(sourceButton, new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));

        playRecordTimeText = new TextView(context);
        playRecordTimeText.setSingleLine(true);
        playRecordTimeText.setTextSize(Theme.TEXT_SIZE_NORMAL);
        playRecordTimeText.setTextColor(Theme.SECONDARY_TEXT_COLOR);
        playRecordTimeText.setVisibility(GONE);
        LayoutParams recordTimeParams = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        infoRow.addView(playRecordTimeText, recordTimeParams);

        buttonsLayout = new LinearLayout(context);
        buttonsLayout.setOrientation(HORIZONTAL);
        buttonsLayout.setGravity(Gravity.CENTER_VERTICAL);
        LayoutParams buttonsLayoutParams = new LayoutParams(WRAP_CONTENT, MATCH_PARENT, 0);
        buttonsLayoutParams.setMargins(0, 0, dp(10), 0);
        addView(buttonsLayout, buttonsLayoutParams);

        InsetBackgroundFactory backgroundFactory = InsetBackgroundFactory.builder()
                .inset(dp(2))
                .backgroundColor(Theme.GHOST_BUTTON_STATES)
                .cornerRadius(dp(4)).build();
        addToPlaylistButton = new ModifyPlaylistTrackModalButton(context);
        backgroundFactory.applyBackgroundTo(addToPlaylistButton);
        buttonsLayout.addView(addToPlaylistButton, new LinearLayout.LayoutParams(dp(40), dp(40), 0));

        likeButton = new ToggleTrackLikeStateButton(context);
        backgroundFactory.applyBackgroundTo(likeButton);
        buttonsLayout.addView(likeButton, new LinearLayout.LayoutParams(dp(40), dp(40), 0));
    }

    public void setRowAnimationsEnabled(boolean enabled) {
        row2.setAnimationsEnabled(enabled);
    }

    public void clearData() {
        albumImageView.clear();
        musicName.setText("");
        feeLabel.setText("");
        feeLabel.setVisibility(GONE);
        durationText.setText("");
        row2.removeAllViews();
        pusherText.setText("");
        pusherHeadView.setVisibility(View.GONE);
        pusherHeadView.setPlayerSkinSupplier(null);
        playRecordTimeText.setText("");
        playRecordTimeText.setVisibility(GONE);
        addToPlaylistButton.bindMusicDetail(null);
        likeButton.bindMusicList(null);
        setTag(null);
        musicDetail = null;
        musicTrace = null;
    }

    /**
     * Shows the play-record time at the end of the last row; pass {@code null} or
     * {@link Instant#MIN} to hide it.
     */
    public void setPlayRecordTime(Instant playTime) {
        if (playRecordTimeText == null) {
            return;
        }
        if (playTime == null || playTime.equals(Instant.MIN)) {
            playRecordTimeText.setText("");
            playRecordTimeText.setVisibility(GONE);
        } else {
            playRecordTimeText.setText(PLAY_RECORD_FORMATTER.format(playTime));
            playRecordTimeText.setVisibility(VISIBLE);
        }
    }

    public void bindData(MusicDetail musicDetail) {
        bindData(Traceable.of(musicDetail));
    }

    public void bindData(Traceable<MusicDetail> musicTrace) {
        MusicDetail musicDetail = musicTrace == null ? null : musicTrace.value();
        if (Objects.equals(this.musicDetail, musicDetail) && Objects.equals(this.musicTrace, musicTrace)) {
            return;
        }
        setTag(musicDetail == null ? null : musicDetail.getId());
        this.musicDetail = musicDetail;
        this.musicTrace = musicTrace;
        Album album = musicDetail.getAlbum();
        albumImageView.loadUrl(album.getImageThumbnailUrl(dp(imageSize)));

        musicName.setText(musicDetail.getName());
        Fee fee1 = musicDetail.getFee();
        if (fee1 == Fee.VIP || fee1 == Fee.SEPARATELY_PURCHASE) {
            feeLabel.setText(I18n.get(MusicHud.MOD_ID + ".text.fee." + fee1.name()));
            feeLabel.setVisibility(VISIBLE);
        } else {
            feeLabel.setVisibility(GONE);
        }

        row2.removeAllViews();
        int index = 0;
        Context context = getContext();
        InsetBackgroundFactory backgroundFactory = InsetBackgroundFactory.builder()
                .inset(0)
                .cornerRadius(dp(2))
                .padding(new InsetBackgroundFactory.Padding(0, 0, 0, 0))
                .build();
        List<Artist> artists = musicDetail.getArtists();
        for (final Artist artist : artists) {
            if (index != 0) {
                TextView split = new TextView(context);
                split.setTextColor(Theme.SECONDARY_TEXT_COLOR);
                split.setTextSize(Theme.TEXT_SIZE_NORMAL);
                split.setText(" / ");
                split.setSingleLine();
                row2.addView(split);
            }
            index++;
            Button artistButton = new Button(context);
            backgroundFactory.applyBackgroundTo(artistButton);
            boolean validArtist = artist.getId() > 0;
            artistButton.setEnabled(validArtist);
            artistButton.setTextColor(validArtist ? Theme.PRIMARY_COLOR : Theme.SECONDARY_TEXT_COLOR);
            artistButton.setTextSize(Theme.TEXT_SIZE_NORMAL);
            artistButton.setTextAlignment(TEXT_ALIGNMENT_TEXT_START);
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
            row2.addView(artistButton);
        }
        String albumName = album.getName();
        albumName = albumName == null ? "" : albumName;
        if (!artists.isEmpty() && !artists.getFirst().getName().isBlank() && !albumName.isBlank()) {
            TextView split = new TextView(context);
            split.setTextColor(Theme.SECONDARY_TEXT_COLOR);
            split.setTextSize(Theme.TEXT_SIZE_NORMAL);
            split.setText(" - ");
            split.setSingleLine();
            row2.addView(split);
        }
        if (!Album.NONE.equals(album)) {
            Button albumButton = new Button(context);
            backgroundFactory.applyBackgroundTo(albumButton);
            boolean validAlbum = album.getId() > 0;
            albumButton.setEnabled(validAlbum);
            albumButton.setTextColor(validAlbum ? Theme.PRIMARY_COLOR : Theme.SECONDARY_TEXT_COLOR);
            albumButton.setTextSize(Theme.TEXT_SIZE_NORMAL);
            albumButton.setText(albumName);
            albumButton.setSingleLine();
            albumButton.setTextAlignment(TEXT_ALIGNMENT_TEXT_START);
            albumButton.setOnClickListener(button -> {
                RouterContainer routerContainer = RouterContainer.getInstance();
                if (routerContainer != null) {
                    routerContainer.pushNavigate(
                            new MusicCollectionDetailView(context, album)
                    );
                }
            });
            row2.addView(albumButton);
        }

        Duration duration = Duration.of(musicDetail.getDurationMillis(), ChronoUnit.MILLIS);
        DateTimeFormatter formatter = duration.toHoursPart() >= 1 ?
                timeFormatterWithHour :
                timeFormatter;
        durationText.setText(formatter.format(
                LocalTime.MIDNIGHT.plusSeconds(duration.toSeconds())
        ));

        if (showPusherInfo) {
            PusherInfo pusherInfo = musicDetail.getPusherInfo();
            if (!pusherInfo.getPlayerName().isEmpty()) {
                ClientPacketListener connection = Minecraft.getInstance().getConnection();
                if (connection == null) throw new IllegalStateException();
                pusherText.setText(pusherInfo.getPlayerName());
                pusherHeadView.setVisibility(VISIBLE);
                pusherHeadView.setPlayerSkinSupplier(() -> {
                    try {
                        return PlayerInfoUtil.getPlayerSkin(PlayerInfoUtil.getPlayerInfoByUUID(pusherInfo.getPlayerUUID()));
                    } catch (Exception ignored) {
                    }
                    return null;
                });
            }
            this.pusherInfo.setVisibility(VISIBLE);

            SourceMeta source = musicTrace.source();
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


                sourceButton.setText(text);
                sourceButton.setVisibility(VISIBLE);
            } else {
                sourceButton.setVisibility(GONE);
            }
        } else {
            pusherHeadView.setVisibility(GONE);
            pusherHeadView.setPlayerSkinSupplier(null);
            this.pusherInfo.setVisibility(GONE);
            sourceButton.setVisibility(GONE);
        }

        addToPlaylistButton.bindMusicDetail(musicDetail);
        likeButton.bindMusicList(musicService.getMusicTrackState(musicDetail).currentUsersLikeList());
    }
}