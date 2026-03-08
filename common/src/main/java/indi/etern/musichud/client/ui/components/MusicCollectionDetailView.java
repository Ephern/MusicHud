package indi.etern.musichud.client.ui.components;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.Drawable;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.*;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.*;
import indi.etern.musichud.client.services.MusicService;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.utils.ButtonInsetBackground;
import net.minecraft.client.resources.language.I18n;

import java.util.stream.Collectors;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;

public class MusicCollectionDetailView extends LinearLayout {
    public MusicCollectionDetailView(Context context, MusicCollection musicCollection) {
        super(context);

        setOrientation(VERTICAL);
        String collectionNameI18n = musicCollection.getNameI18nKey();

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
        Drawable drawable = ButtonInsetBackground.builder()
                .inset(0)
                .cornerRadius(dp(8))
                .padding(new ButtonInsetBackground.Padding(dp(16), 0, dp(16), 0))
                .build().get();
        backButton.setBackground(drawable);
        LayoutParams topBarParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
        topBarParams.setMargins(0, 0, dp(4), 0);
        topBar.addView(backButton, topBarParams);

        UrlImageView imageView = new UrlImageView(context);
        LayoutParams imageParams = new LayoutParams(dp(60), dp(60));
        topBar.addView(imageView, imageParams);
        imageView.loadUrl(musicCollection.getImageThumbnailUrl(dp(128)));
        imageView.setCornerRadius(dp(8));

        LinearLayout texts = new LinearLayout(context);
        texts.setGravity(Gravity.CENTER_VERTICAL);
        texts.setOrientation(VERTICAL);
        LayoutParams params1 = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        params1.setMargins(dp(16), 0, 0, 0);
        topBar.addView(texts, params1);

        TextView type = new TextView(context);
        type.setTextSize(Theme.TEXT_SIZE_LARGE);
        type.setTextColor(Theme.SECONDARY_TEXT_COLOR);
        type.setText(I18n.get(collectionNameI18n));
        LayoutParams params2 = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params2.setMargins(0, 0, 0, dp(4));
        type.setLayoutParams(params2);
        texts.addView(type);

        TextView name = new TextView(context);
        name.setTextSize(Theme.TEXT_SIZE_LARGER);
        name.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
        name.setText(musicCollection.getName());
        texts.addView(name);

        addView(topBar);

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

        LinearLayout tracks = new LinearLayout(context);
        tracks.setOrientation(VERTICAL);
        scrollView.addView(tracks, new LayoutParams(MATCH_PARENT, MATCH_PARENT));

        musicCollection.loadMusicDetails().thenAcceptAsync(playlistDetail -> {
            MuiModApi.postToUiThread(() -> {
                type.setText(I18n.get(collectionNameI18n) + "  " + I18n.get("music_hud.text.totalCount").replace("{}", String.valueOf(playlistDetail.size())));
                removeView(progressBar);
                for (MusicDetail musicDetail : playlistDetail) {
                    addItem(context, musicDetail, tracks);
                }
            });
        }, MusicHud.EXECUTOR);
    }

    private void addItem(Context context, MusicDetail musicDetail, LinearLayout tracks) {
        var musicLayout = new MusicListItem(context);
        musicLayout.setShowPusherInfo(false);
        musicLayout.bindData(musicDetail);
        var background = ButtonInsetBackground.builder()
                .cornerRadius(dp(12))
                .inset(dp(1))
                .padding(new ButtonInsetBackground.Padding(dp(4), dp(4), dp(4), dp(4))).build().get();
        musicLayout.setBackground(background);

        musicLayout.setClickable(true);
        String artistsName = musicDetail.getArtists().stream()
                .map(Artist::getName).collect(Collectors.joining(" / "));
        musicLayout.setOnClickListener((view) -> {
            MusicService.getInstance().sendPushMusicToQueue(musicDetail);
            Toast.makeText(context, I18n.get("music_hud.text.pushedMusicToPlaylist") + "\n" + musicDetail.getName() + " - " + artistsName, Toast.LENGTH_SHORT).show();
        });
        tracks.addView(musicLayout);
    }
}
