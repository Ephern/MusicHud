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
import indi.etern.musichud.beans.music.MusicCollection;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.client.services.MusicService;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.ToastUtil;
import indi.etern.musichud.client.ui.utils.ButtonInsetBackground;
import net.minecraft.client.resources.language.I18n;

import java.util.function.Consumer;
import java.util.stream.Collectors;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class MusicCollectionDetailView extends LinearLayout {
    private final MusicCollection musicCollection;
    private final Button addToIdleSourceListButton;
    private final MusicService musicService = MusicService.getInstance();
    private final ProgressBar progressBar;
    private final TextView type;
    private final LinearLayout tracksListView;

    public MusicCollectionDetailView(Context context, MusicCollection musicCollection) {
        super(context);

        this.musicCollection = musicCollection;
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
        LayoutParams backButtonParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
        backButtonParams.setMargins(0, 0, dp(4), 0);
        topBar.addView(backButton, backButtonParams);

        UrlImageView imageView = new UrlImageView(context);
        LayoutParams imageParams = new LayoutParams(dp(72), dp(72));
        topBar.addView(imageView, imageParams);
        imageView.loadUrl(musicCollection.getImageThumbnailUrl(dp(72)));
        imageView.setCornerRadius(dp(8));

        LinearLayout briefInfo = new LinearLayout(context);
        briefInfo.setGravity(Gravity.CENTER_VERTICAL);
        briefInfo.setOrientation(VERTICAL);
        LayoutParams params1 = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        params1.setMargins(dp(16), 0, 0, 0);
        topBar.addView(briefInfo, params1);

        type = new TextView(context);
        type.setTextSize(Theme.TEXT_SIZE_LARGE);
        type.setTextColor(Theme.SECONDARY_TEXT_COLOR);
        type.setText(I18n.get(collectionNameI18n));
        LayoutParams params2 = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params2.setMargins(0, 0, 0, dp(4));
        type.setLayoutParams(params2);
        briefInfo.addView(type);

        TextView name = new TextView(context);
        name.setTextSize(Theme.TEXT_SIZE_LARGER);
        name.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
        name.setText(musicCollection.getName());
        briefInfo.addView(name);

        LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(HORIZONTAL);

        addToIdleSourceListButton = new Button(context);
        updateButton();
        addToIdleSourceListButton.setTextColor(Theme.PRIMARY_COLOR);
        addToIdleSourceListButton.setTextSize(Theme.TEXT_SIZE_NORMAL);
        Drawable background1 = ButtonInsetBackground.builder()
                .inset(0)
                .cornerRadius(dp(8))
                .padding(new ButtonInsetBackground.Padding(0, dp(2), 0, dp(2)))
                .build().get();
        addToIdleSourceListButton.setBackground(background1);
        addToIdleSourceListButton.setOnClickListener((v) -> {
            if (musicService.getIdlePlaySources().contains(musicCollection)) {
                ToastUtil.show(Toast.makeText(context, I18n.get("music_hud.text.removedFromIdlePlaySource") + "\n" + musicCollection.getName(), Toast.LENGTH_SHORT));
                musicService.removeFromIdlePlaySource(musicCollection);
            } else {
                ToastUtil.show(Toast.makeText(context, I18n.get("music_hud.text.addedToIdlePlaySource") + "\n" + musicCollection.getName(), Toast.LENGTH_SHORT));
                musicService.addToIdlePlaySource(musicCollection);
            }
        });
        buttons.addView(addToIdleSourceListButton, new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));

        Button refreshButton = new Button(context);
        refreshButton.setText(I18n.get("music_hud.button.refresh"));
        refreshButton.setTextColor(Theme.PRIMARY_COLOR);
        refreshButton.setTextSize(Theme.TEXT_SIZE_NORMAL);
        refreshButton.setOnClickListener((v) -> refreshData());
        LayoutParams params3 = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        params3.setMargins(dp(16), 0, 0, 0);
        buttons.addView(refreshButton, params3);

        briefInfo.addView(buttons, new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));

        LayoutParams topBarParams = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        topBarParams.setMargins(0, dp(24), 0, 0);
        addView(topBar, topBarParams);

        progressBar = new ProgressBar(context);
        LayoutParams progressParams = new LayoutParams(MATCH_PARENT, MATCH_PARENT);
        progressParams.setMargins(0, dp(32), 0, 0);
        addView(progressBar, progressParams);

        var scrollView = new ScrollView(context);
        scrollView.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);
        scrollView.setFillViewport(true);
        LayoutParams tracksParams = new LayoutParams(MATCH_PARENT, MATCH_PARENT);
        tracksParams.setMargins(0, dp(24), 0, 0);
        addView(scrollView, tracksParams);

        tracksListView = new LinearLayout(context);
        tracksListView.setOrientation(VERTICAL);
        scrollView.addView(tracksListView, new LayoutParams(MATCH_PARENT, MATCH_PARENT));

        refreshData();

        Consumer<MusicCollection> listener = playlist1 -> {
            if (playlist1.equals(musicCollection)) {
                MuiModApi.postToUiThread(this::updateButton);
            }
        };

        addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                musicService.getIdlePlaySourceChangeListeners().add(listener);
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                musicService.getIdlePlaySourceChangeListeners().remove(listener);
            }
        });
    }

    private void refreshData() {
        Context context = getContext();
        String collectionNameI18n = musicCollection.getNameI18nKey();
        tracksListView.removeAllViews();
        progressBar.setVisibility(View.VISIBLE);
        progressBar.setIndeterminate(true);
        musicCollection.loadMusicDetails().thenAcceptAsync(playlistDetail -> {
            MuiModApi.postToUiThread(() -> {
                type.setText(I18n.get(collectionNameI18n) + "  " + I18n.get("music_hud.text.totalCount").replace("{}", String.valueOf(playlistDetail.size())));
                progressBar.setVisibility(View.GONE);
                for (MusicDetail musicDetail : playlistDetail) {
                    addItem(context, musicDetail);
                }
            });
        }, MusicHud.EXECUTOR);
    }

    private void addItem(Context context, MusicDetail musicDetail) {
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
            ToastUtil.show(Toast.makeText(context, I18n.get("music_hud.text.pushedMusicToPlaylist") + "\n" + musicDetail.getName() + " - " + artistsName, Toast.LENGTH_SHORT));
        });
        tracksListView.addView(musicLayout);
    }

    private void updateButton() {
        if (musicService.getIdlePlaySources().contains(musicCollection)) {
            addToIdleSourceListButton.setText(I18n.get("music_hud.button.removeFromIdlePlaySource"));
        } else {
            addToIdleSourceListButton.setText(I18n.get("music_hud.button.addToIdlePlaySource"));
        }
    }
}
