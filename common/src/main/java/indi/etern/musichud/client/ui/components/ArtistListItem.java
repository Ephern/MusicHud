package indi.etern.musichud.client.ui.components;

import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import indi.etern.musichud.beans.music.Artist;
import indi.etern.musichud.client.ui.Theme;
import net.minecraft.client.resources.language.I18n;

public class ArtistListItem extends LinearLayout {
    public static final int imageSize = 54;
    private UrlImageView albumImage;
    private TextView artistName;
    private TextView musicAndAlbumCounts;

    public ArtistListItem(Context context) {
        super(context);
        initView(context);
    }

    private void initView(Context context) {
        setOrientation(HORIZONTAL);
        LayoutParams layoutParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        setLayoutParams(layoutParams);

        albumImage = new UrlImageView(context);
        albumImage.setCircular(true);
        addView(albumImage, new LayoutParams(dp(imageSize), dp(imageSize)));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(VERTICAL);
        texts.setGravity(Gravity.CENTER_VERTICAL);
        LayoutParams textsParams = new LayoutParams(0, LayoutParams.MATCH_PARENT, 1);
        textsParams.setMargins(dp(12), 0, 0, 0);
        addView(texts, textsParams);

        artistName = new TextView(context);
        artistName.setSingleLine(true);
        artistName.setTextSize(Theme.TEXT_SIZE_LARGE);
        artistName.setTextColor(Theme.NORMAL_TEXT_COLOR);
        texts.addView(artistName);

        musicAndAlbumCounts = new TextView(context);
        musicAndAlbumCounts.setTextSize(Theme.TEXT_SIZE_NORMAL);
        musicAndAlbumCounts.setTextColor(Theme.NORMAL_TEXT_COLOR);
        texts.addView(musicAndAlbumCounts);
    }

    public void bindData(Artist artist) {
        albumImage.loadUrl(artist.getAvatarThumbnailUrl(dp(imageSize)));
        artistName.setText(artist.getName());
        musicAndAlbumCounts.setText(
                I18n.get("music_hud.text.artist.album").replace("{}", String.valueOf(artist.getAlbumCount()))
                        + "  |  " + I18n.get("music_hud.text.artist.music").replace("{}", String.valueOf(artist.getMusicCount())));
    }
}