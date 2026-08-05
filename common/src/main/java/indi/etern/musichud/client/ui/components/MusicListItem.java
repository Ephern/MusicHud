package indi.etern.musichud.client.ui.components;

import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.Artist;
import indi.etern.musichud.beans.music.Fee;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.beans.music.PusherInfo;
import indi.etern.musichud.client.services.music.MusicService;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.utils.PlayerInfoUtil;
import indi.etern.musichud.client.utils.ui.ButtonInsetBackgroundFactory;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.resources.language.I18n;

import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class MusicListItem extends LinearLayout {
    public static final int imageSize = 56;
    private static final MusicService musicService = MusicService.getInstance();
    private final DateTimeFormatter timeFormatterWithHour = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("mm:ss");
    private UrlImageView albumImage;
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
    private PlayerHeadView pusherHeadView;
    private ToggleTrackLikeStateButton likeButton;
    @Getter
    private LinearLayout buttonsLayout;
    private ModifyPlaylistTrackModalButton addToPlaylistButton;

    public MusicListItem(Context context) {
        super(context);
        initView(context);
    }

    private void initView(Context context) {
        setOrientation(HORIZONTAL);
        LayoutParams musicLayoutParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        setLayoutParams(musicLayoutParams);
        setGravity(Gravity.CENTER_VERTICAL);

        albumImage = new UrlImageView(context);
        albumImage.setCornerRadius(dp(8));
        albumImage.setAspectRatio(1);
        addView(albumImage, new LayoutParams(dp(imageSize), dp(imageSize)));

        LinearLayout musicTexts = new LinearLayout(context);
        musicTexts.setOrientation(VERTICAL);
        musicTexts.setGravity(Gravity.CENTER_VERTICAL);
        LayoutParams textsParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1);
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
        musicTexts.addView(row2);

        LinearLayout row3 = new LinearLayout(context);
        row3.setOrientation(HORIZONTAL);
        row3.setGravity(Gravity.CENTER_VERTICAL);
        musicTexts.addView(row3);

        durationText = new TextView(context);
        durationText.setTextSize(Theme.TEXT_SIZE_NORMAL);
        durationText.setSingleLine(true);
        durationText.setTextColor(Theme.SECONDARY_TEXT_COLOR);

        LayoutParams params = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        params.setMargins(0, 0, dp(12), 0);
        row3.addView(durationText, params);

        feeLabel = new TextView(context);
        feeLabel.setSingleLine(true);
        feeLabel.setTextSize(Theme.TEXT_SIZE_NORMAL);
        feeLabel.setTextColor(Theme.SECONDARY_TEXT_COLOR);
        feeLabel.setVisibility(GONE);
        LayoutParams params1 = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        params1.setMargins(0, 0, dp(12), 0);
        row3.addView(feeLabel, params1);

        LinearLayout pusherInfo = new LinearLayout(context);
        pusherInfo.setOrientation(LinearLayout.HORIZONTAL);
        pusherInfo.setGravity(Gravity.CENTER_VERTICAL);

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

        row3.addView(pusherInfo, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        buttonsLayout = new LinearLayout(context);
        buttonsLayout.setOrientation(HORIZONTAL);
        buttonsLayout.setGravity(Gravity.CENTER_VERTICAL);
        LayoutParams buttonsLayoutParams = new LayoutParams(WRAP_CONTENT, MATCH_PARENT, 0);
        buttonsLayoutParams.setMargins(0, 0, dp(10), 0);
        addView(buttonsLayout, buttonsLayoutParams);

        ButtonInsetBackgroundFactory backgroundFactory = ButtonInsetBackgroundFactory.builder()
                .inset(dp(2))
                .backgroundColor(Theme.GHOST_BUTTON_STATES)
                .cornerRadius(dp(4)).build();
        addToPlaylistButton = new ModifyPlaylistTrackModalButton(context);
        addToPlaylistButton.setBackground(backgroundFactory.newBackgroundDrawable());
        buttonsLayout.addView(addToPlaylistButton, new LinearLayout.LayoutParams(dp(40), dp(40), 0));

        likeButton = new ToggleTrackLikeStateButton(context);
        likeButton.setBackground(backgroundFactory.newBackgroundDrawable());
        buttonsLayout.addView(likeButton, new LinearLayout.LayoutParams(dp(40), dp(40), 0));
    }

    public void bindData(MusicDetail musicDetail) {
        if (Objects.equals(this.musicDetail, musicDetail)) {
            return;
        }
        setTag(musicDetail.getId());
        this.musicDetail = musicDetail;
        albumImage.loadUrl(musicDetail.getAlbum().getThumbnailPicUrl(dp(imageSize)));

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
        ButtonInsetBackgroundFactory backgroundFactory = ButtonInsetBackgroundFactory.builder()
                .inset(0)
                .cornerRadius(dp(2))
                .padding(new ButtonInsetBackgroundFactory.Padding(0, 0, 0, 0))
                .build();
        for (Artist artist : musicDetail.getArtists()) {
            if (index != 0) {
                TextView split = new TextView(context);
                split.setTextColor(Theme.SECONDARY_TEXT_COLOR);
                split.setTextSize(Theme.TEXT_SIZE_SMALL);
                split.setText(" / ");
                row2.addView(split);
            }
            index++;
            Button artistButton = new Button(context);
            artistButton.setBackground(backgroundFactory.newBackgroundDrawable());
            artistButton.setTextColor(Theme.PRIMARY_COLOR);
            artistButton.setTextSize(Theme.TEXT_SIZE_NORMAL);
            artistButton.setTextAlignment(TEXT_ALIGNMENT_TEXT_START);
            artistButton.setText(artist.getName());
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
        TextView split = new TextView(context);
        split.setTextColor(Theme.SECONDARY_TEXT_COLOR);
        split.setTextSize(Theme.TEXT_SIZE_SMALL);
        split.setText(" - ");
        row2.addView(split);
        Button albumButton = new Button(context);
        albumButton.setBackground(backgroundFactory.newBackgroundDrawable());
        albumButton.setTextColor(Theme.PRIMARY_COLOR);
        albumButton.setTextSize(Theme.TEXT_SIZE_NORMAL);
        albumButton.setText(musicDetail.getAlbum().getName());
        albumButton.setTextAlignment(TEXT_ALIGNMENT_TEXT_START);
        albumButton.setOnClickListener(button -> {
            RouterContainer routerContainer = RouterContainer.getInstance();
            if (routerContainer != null) {
                routerContainer.pushNavigate(
                        new MusicCollectionDetailView(context, musicDetail.getAlbum())
                );
            }
        });
        row2.addView(albumButton);

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
        } else {
            pusherHeadView.setVisibility(View.GONE);
            pusherHeadView.setPlayerSkinSupplier(null);
        }

        addToPlaylistButton.bindMusicDetail(musicDetail);
        likeButton.bindMusicList(musicService.getMusicTrackState(musicDetail).currentUsersLikeList());
    }
}