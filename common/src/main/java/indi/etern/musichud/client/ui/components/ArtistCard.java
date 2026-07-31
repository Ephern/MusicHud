package indi.etern.musichud.client.ui.components;

import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.Artist;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.utils.ui.ButtonInsetBackgroundFactory;
import net.minecraft.client.resources.language.I18n;

public class ArtistCard extends LinearLayout {//TODO
    public static final int imageSize = 104;
    private UrlImageView albumImage;
    private TextView artistName;
    private TextView musicCounts;
    private TextView albumCounts;
    private Artist artist;

    public ArtistCard(Context context) {
        super(context);
        initView(context);
    }

    private void initView(Context context) {
        setOrientation(VERTICAL);
        LayoutParams layoutParams = new LayoutParams(dp(120), ViewGroup.LayoutParams.WRAP_CONTENT);
        setLayoutParams(layoutParams);

        albumImage = new UrlImageView(context);
        albumImage.setCircular(true);
        addView(albumImage, new LayoutParams(dp(imageSize), dp(imageSize)));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(VERTICAL);
        texts.setGravity(Gravity.CENTER_HORIZONTAL);
        addView(texts);

        artistName = new TextView(context);
        artistName.setSingleLine(false);
        artistName.setMaxLines(4);
        artistName.setMaxWidth(dp(100));
        artistName.setTextSize(Theme.TEXT_SIZE_LARGE);
        artistName.setTextColor(Theme.NORMAL_TEXT_COLOR);
        artistName.setTextAlignment(TEXT_ALIGNMENT_CENTER);
        texts.addView(artistName);

        albumCounts = new TextView(context);
        albumCounts.setTextSize(Theme.TEXT_SIZE_NORMAL);
        albumCounts.setTextColor(Theme.SECONDARY_TEXT_COLOR);
        albumCounts.setTextAlignment(TEXT_ALIGNMENT_CENTER);
        texts.addView(albumCounts);

        musicCounts = new TextView(context);
        musicCounts.setTextSize(Theme.TEXT_SIZE_NORMAL);
        musicCounts.setTextColor(Theme.SECONDARY_TEXT_COLOR);
        musicCounts.setTextAlignment(TEXT_ALIGNMENT_CENTER);
        texts.addView(musicCounts);

        var background = ButtonInsetBackgroundFactory.builder()
                .cornerRadius(dp(12))
                .inset(dp(1))
                .padding(new ButtonInsetBackgroundFactory.Padding(dp(8), dp(8), dp(8), dp(8))).build().newBackgroundDrawable();
        setBackground(background);

        setClickable(true);
        setOnClickListener((view) -> {
            RouterContainer routerContainer = RouterContainer.getInstance();
            if (routerContainer != null && artist != null) {
                routerContainer.pushNavigate(
                        new ArtistDetailView(context, artist)
                );
            }
        });
    }

    public void bindData(Artist artist) {
        albumImage.loadUrl(artist.getAvatarThumbnailUrl(dp(imageSize)));
        artistName.setText(artist.getName());
        int musicCount = artist.getMusicCount();
        if (musicCount > 0) {
            musicCounts.setText(I18n.get(MusicHud.MOD_ID + ".text.artist.music").replace("{}", String.valueOf(musicCount)));
        } else {
            musicCounts.setText("");
        }
        albumCounts.setText(I18n.get(MusicHud.MOD_ID + ".text.artist.album").replace("{}", String.valueOf(artist.getAlbumCount())));
        this.artist = artist;
    }
}