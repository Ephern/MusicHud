package indi.etern.musichud.client.ui.components;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.Drawable;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.util.ColorStateList;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ProgressBar;
import icyllis.modernui.widget.TextView;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.Album;
import indi.etern.musichud.beans.music.Artist;
import indi.etern.musichud.beans.music.Playlist;
import indi.etern.musichud.beans.user.Profile;
import indi.etern.musichud.client.services.LoginService;
import indi.etern.musichud.client.services.MusicService;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.utils.ButtonInsetBackground;
import lombok.Getter;
import net.minecraft.client.resources.language.I18n;

import java.util.concurrent.CompletableFuture;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class AccountView extends LinearLayout {
    @Getter
    private static AccountView instance;

    public AccountView(Context context) {
        super(context);
        refresh();
        instance = this;
        addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                instance = null;
            }
        });
    }

    public void refresh() {
        removeAllViews();
        setOrientation(LinearLayout.VERTICAL);
        setLayoutParams(new LayoutParams(MATCH_PARENT, MATCH_PARENT));
        Context context = getContext();

        Profile currentProfile = Profile.getCurrent();
        if (currentProfile == null) {
            setGravity(Gravity.CENTER_HORIZONTAL);

            TextView textView = new TextView(context);
            textView.setTextSize(Theme.TEXT_SIZE_NORMAL);
            textView.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
            textView.setText(I18n.get(MusicHud.MOD_ID + ".error.getAccountInfo"));
            textView.setLayoutParams(new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
            LayoutParams params1 = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            params1.setMargins(0, dp(64), 0, 0);
            textView.setLayoutParams(params1);

            Button retryButton = new Button(context);
            retryButton.setFocusable(true);
            retryButton.setClickable(true);
            retryButton.setTextColor(Theme.PRIMARY_COLOR);
            retryButton.setHeight(dp(36));
            retryButton.setWidth(dp(84));
            retryButton.setTextSize(Theme.TEXT_SIZE_NORMAL);
            retryButton.setText(I18n.get(MusicHud.MOD_ID + ".button.retry"));

            ProgressBar progressRing = new ProgressBar(context);
            progressRing.setIndeterminate(true);
            progressRing.setIndeterminateTintList(ColorStateList.valueOf(Theme.PRIMARY_COLOR));
            progressRing.setVisibility(GONE);
            LayoutParams ringParams = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            ringParams.setMargins(0, dp(32), 0, 0);
            progressRing.setLayoutParams(ringParams);

            var background = ButtonInsetBackground.builder()
                    .padding(new ButtonInsetBackground.Padding(0, 0, 0, 0))
                    .cornerRadius(dp(4)).inset(dp(1)).build().get();
            retryButton.setBackground(background);
            LayoutParams buttonParams = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            buttonParams.setMargins(0, dp(8), 0, 0);
            retryButton.setLayoutParams(buttonParams);
            retryButton.setOnClickListener((view) -> {
                MuiModApi.postToUiThread(() -> {
                    retryButton.setVisibility(GONE);
                    progressRing.setVisibility(VISIBLE);
                });
                LoginService.getInstance().loginToServer();
            });

            addView(textView);
            addView(retryButton);
            addView(progressRing);
        } else {
            setGravity(Gravity.TOP);
            LinearLayout topPanel = new LinearLayout(context);
            topPanel.setOrientation(LinearLayout.HORIZONTAL);
            topPanel.setGravity(Gravity.LEFT);

            UrlImageView avatar = new UrlImageView(context);
            avatar.setCircular(true);
            LayoutParams layoutParams = new LayoutParams(dp(68), dp(68));
            avatar.setLayoutParams(layoutParams);
            topPanel.addView(avatar);
            avatar.loadUrl(currentProfile.getAvatarUrl());

            LayoutParams textsLayoutParams = new LayoutParams(MATCH_PARENT, MATCH_PARENT);
            textsLayoutParams.setMargins(dp(16), 0, 0, 0);
            LinearLayout texts = new LinearLayout(context);
            texts.setOrientation(VERTICAL);
            texts.setGravity(Gravity.CENTER_VERTICAL);
            topPanel.addView(texts, textsLayoutParams);

            LayoutParams nameLayoutParams = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            TextView nickName = new TextView(context);
            nickName.setSingleLine(true);
            nickName.setTextSize(Theme.TEXT_SIZE_LARGER);
            nickName.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
            nickName.setText(currentProfile.getNickname());
            texts.addView(nickName, nameLayoutParams);

            LayoutParams idLayoutParams = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            TextView id = new TextView(context);
            id.setSingleLine(true);
            id.setTextSize(Theme.TEXT_SIZE_NORMAL);
            id.setTextColor(Theme.SECONDARY_TEXT_COLOR);
            id.setText(Long.toString(currentProfile.getUserId()));
            texts.addView(id, idLayoutParams);

            LayoutParams logoutButtonParam = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            Button logoutButton = new Button(context);
            logoutButton.setText(I18n.get(MusicHud.MOD_ID + ".button.logout"));
            logoutButton.setTextColor(Theme.PRIMARY_COLOR);
            logoutButton.setTextSize(Theme.TEXT_SIZE_NORMAL);
            Drawable background = ButtonInsetBackground.builder()
                    .inset(0).cornerRadius(dp(4))
                    .padding(new ButtonInsetBackground.Padding(0, dp(2), 0, dp(2)))
                    .build().get();
            logoutButton.setBackground(background);
            texts.addView(logoutButton, logoutButtonParam);
            logoutButton.setOnClickListener(b -> {
                LoginService.getInstance().logout();
                LoginService.getInstance().loginAsAnonymousToServer();
            });

            LayoutParams topPanelLayoutParams = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            topPanelLayoutParams.setMargins(0, dp(32), 0, dp(32));
            addView(topPanel, topPanelLayoutParams);

            ProgressBar progressBar = new ProgressBar(context);
            progressBar.setIndeterminate(true);
            addView(progressBar, new LayoutParams(MATCH_PARENT, MATCH_PARENT));

            LinearLayout content = new LinearLayout(context);
            content.setOrientation(VERTICAL);
            content.setLayoutParams(new LayoutParams(MATCH_PARENT, WRAP_CONTENT));
            addView(content);

            TextView myPlaylistText = new TextView(context);
            myPlaylistText.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
            myPlaylistText.setTextSize(Theme.TEXT_SIZE_LARGE);
            myPlaylistText.setText(I18n.get(MusicHud.MOD_ID + ".text.myPlaylists"));
            content.addView(myPlaylistText, new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));

            AutoFlowGridLayout playlistCards = new AutoFlowGridLayout(context);
            playlistCards.setRowMinWidth(dp(143));
            LayoutParams playlistsParams = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            playlistsParams.setMargins(0, dp(16), 0, dp(32));
            content.addView(playlistCards, playlistsParams);

            TextView albumText = new TextView(context);
            albumText.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
            albumText.setTextSize(Theme.TEXT_SIZE_LARGE);
            albumText.setText(I18n.get(MusicHud.MOD_ID + ".text.myAlbums"));
            content.addView(albumText, new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));

            AutoFlowGridLayout albumCards = new AutoFlowGridLayout(context);
            albumCards.setRowMinWidth(dp(143));
            LayoutParams albumParams = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            albumParams.setMargins(0, dp(16), 0, dp(32));
            content.addView(albumCards, albumParams);

            TextView artistText = new TextView(context);
            artistText.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
            artistText.setTextSize(Theme.TEXT_SIZE_LARGE);
            artistText.setText(I18n.get(MusicHud.MOD_ID + ".text.myArtists"));
            content.addView(artistText, new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));

            AutoFlowGridLayout artistCards = new AutoFlowGridLayout(context);
            artistCards.setRowMinWidth(dp(143));
            LayoutParams artistParams = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            artistParams.setMargins(0, dp(16), 0, dp(32));
            content.addView(artistCards, artistParams);

            MusicService musicService = MusicService.getInstance();
            CompletableFuture.allOf(
                    musicService.loadUserPlaylists().thenAcceptAsync(playlists -> {
                        MuiModApi.postToUiThread(() -> {
                            for (Playlist playlist : playlists) {
                                playlistCards.addView(new MusicCollectionCard(context, playlist));
                            }
                        });
                    }, MusicHud.EXECUTOR),
                    musicService.loadUserAlbums().thenAcceptAsync(albums -> {
                        MuiModApi.postToUiThread(() -> {
                            for (Album playlist : albums) {
                                albumCards.addView(new MusicCollectionCard(context, playlist));
                            }
                        });
                    }),
                    musicService.loadUserArtists().thenAcceptAsync(artists -> {
                        MuiModApi.postToUiThread(() -> {
                            for (Artist artist : artists) {
                                ArtistCard artistCard = new ArtistCard(context);
                                artistCard.bindData(artist);
                                artistCards.addView(artistCard);
                            }
                        });
                    })
            ).thenAccept((v) -> {
                MuiModApi.postToUiThread(() -> {
                    removeView(progressBar);
                });
            });
        }
    }
}