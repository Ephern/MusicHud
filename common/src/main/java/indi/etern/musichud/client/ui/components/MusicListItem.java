package indi.etern.musichud.client.ui.components;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.Drawable;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import indi.etern.musichud.beans.music.Artist;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.beans.music.PusherInfo;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.utils.ButtonInsetBackgroundFactory;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

public class MusicListItem extends LinearLayout {
    public static final int imageSize = 54;
    private final DateTimeFormatter timeFormatterWithHour = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("mm:ss");
    private UrlImageView albumImage;
    private TextView musicName;
    private LinearLayout musicArtistAndAlbum;
    private TextView durationText;
    private TextView pusherText;
    @Setter
    @Getter
    private boolean showPusherInfo = true;
    @Getter
    private MusicDetail musicDetail;

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

        musicName = new TextView(context);
        musicName.setSingleLine(true);
        musicName.setTextSize(Theme.TEXT_SIZE_LARGE);
        musicName.setTextColor(Theme.NORMAL_TEXT_COLOR);
        musicTexts.addView(musicName);

        musicArtistAndAlbum = new LinearLayout(context);
        musicArtistAndAlbum.setOrientation(HORIZONTAL);
        musicTexts.addView(musicArtistAndAlbum);

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(HORIZONTAL);
        musicTexts.addView(linearLayout);

        durationText = new TextView(context);
        durationText.setTextSize(Theme.TEXT_SIZE_NORMAL);
        durationText.setSingleLine(true);
        durationText.setTextColor(Theme.SECONDARY_TEXT_COLOR);

        LayoutParams params = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, dp(16), 0);
        linearLayout.addView(durationText, params);

        pusherText = new TextView(getContext());
        pusherText.setTextColor(Theme.SECONDARY_TEXT_COLOR);
        pusherText.setTextSize(Theme.TEXT_SIZE_NORMAL);
        linearLayout.addView(pusherText);
    }

    public void bindData(MusicDetail musicDetail) {
        if (Objects.equals(this.musicDetail,musicDetail)) {
            return;
        }
        this.musicDetail = musicDetail;
        albumImage.loadUrl(musicDetail.getAlbum().getThumbnailPicUrl(dp(imageSize)));

        musicName.setText(musicDetail.getName());

        musicArtistAndAlbum.removeAllViews();
        int index = 0;
        Context context = getContext();
        for (Artist artist : musicDetail.getArtists()) {
            if (index != 0) {
                TextView split = new TextView(context);
                split.setTextColor(Theme.SECONDARY_TEXT_COLOR);
                split.setTextSize(Theme.TEXT_SIZE_SMALL);
                split.setText(" / ");
                musicArtistAndAlbum.addView(split);
            }
            index++;
            Button artistButton = new Button(context);
            Drawable background = ButtonInsetBackgroundFactory.builder()
                    .inset(0)
                    .cornerRadius(dp(2))
                    .padding(new ButtonInsetBackgroundFactory.Padding(0, 0, 0, 0))
                    .build().newBackgroundDrawable();
            artistButton.setBackground(background);
            artistButton.setFocusable(true);
            artistButton.setClickable(true);
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
            musicArtistAndAlbum.addView(artistButton);
        }
        TextView split = new TextView(context);
        split.setTextColor(Theme.SECONDARY_TEXT_COLOR);
        split.setTextSize(Theme.TEXT_SIZE_SMALL);
        split.setText(" - ");
        musicArtistAndAlbum.addView(split);
        Button albumButton = new Button(context);
        Drawable background = ButtonInsetBackgroundFactory.builder()
                .inset(0)
                .cornerRadius(dp(2))
                .padding(new ButtonInsetBackgroundFactory.Padding(0, 0, 0, 0))
                .build().newBackgroundDrawable();
        albumButton.setBackground(background);
        albumButton.setFocusable(true);
        albumButton.setClickable(true);
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
        musicArtistAndAlbum.addView(albumButton);

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
            }
        }
    }
}