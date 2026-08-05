package indi.etern.musichud.client.ui.components;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Image;
import icyllis.modernui.text.SpannableString;
import icyllis.modernui.text.Spanned;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import indi.etern.musichud.beans.music.Artist;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.utils.image.ImageUtils;
import indi.etern.musichud.client.utils.ui.ButtonInsetBackgroundFactory;

public class ArtistCard extends LinearLayout {//TODO
    public static final int imageSize = 100;
    private UrlImageView albumImage;
    private TextView artistName;
    private TextView productionsCounts;
    private Artist artist;

    public ArtistCard(Context context) {
        super(context);
        initView(context);
    }

    private void initView(Context context) {
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_VERTICAL);
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

        productionsCounts = new TextView(context);
        productionsCounts.setTextSize(Theme.TEXT_SIZE_NORMAL);
        productionsCounts.setTextColor(Theme.NORMAL_TEXT_COLOR);
        productionsCounts.setTextAlignment(TEXT_ALIGNMENT_CENTER);
        texts.addView(productionsCounts);

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
        String albumIcon = "\uD83D\uDDB8";
        String musicIcon = "♫";
        boolean showMusicCount = musicCount > 0;
        String string = albumIcon + " " + artist.getAlbumCount() + (showMusicCount ? ("  " + musicIcon + " " + musicCount) : "");
        SpannableString countsString = new SpannableString(string);

        Image albumIconImage = ImageUtils.getImageFromResource("/assets/music_hud/textures/gui/icons/disc_album.png");
        if (albumIconImage != null) {
            int start = string.indexOf(albumIcon);
            if (start >= 0) {
                countsString.setSpan(ImageUtils.getIconSpan(albumIconImage), start, start + albumIcon.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
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

        productionsCounts.setText(countsString);
        this.artist = artist;
    }
}