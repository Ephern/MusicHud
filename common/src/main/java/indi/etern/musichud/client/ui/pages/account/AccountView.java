package indi.etern.musichud.client.ui.pages.account;

import icyllis.modernui.animation.LayoutTransition;
import icyllis.modernui.core.Context;
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
import indi.etern.musichud.beans.music.UserCategoryPlaylists;
import indi.etern.musichud.beans.user.Profile;
import indi.etern.musichud.client.services.LoginService;
import indi.etern.musichud.client.services.music.MusicService;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.components.ArtistCard;
import indi.etern.musichud.client.ui.components.FlexWrapLayout;
import indi.etern.musichud.client.ui.components.MusicCollectionCard;
import indi.etern.musichud.client.ui.components.UrlImageView;
import indi.etern.musichud.client.ui.utils.ui.ButtonInsetBackgroundFactory;
import indi.etern.musichud.interfaces.IClientLoginService;
import indi.etern.musichud.interfaces.Unregister;
import indi.etern.musichud.utils.collections.ObservableSequencedSet;
import lombok.Getter;
import net.minecraft.client.resources.language.I18n;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class AccountView extends LinearLayout {
    @Getter
    private static AccountView instance;
    private final IClientLoginService IClientLoginService = LoginService.getInstance();
    private FlexWrapLayout myPlaylistCards;
    private FlexWrapLayout mySubscribedPlaylistCards;
    private FlexWrapLayout albumCards;
    private FlexWrapLayout artistCards;
    private LinearLayout myPlaylistsContent;
    private LinearLayout mySubscribedPlaylistsContent;
    private LinearLayout mySubscribedAlbumsContent;
    private LinearLayout mySubscribedArtistsContent;
    private final Map<ElementKey, View> elementMap = new HashMap<>();
    private final Consumer<Playlist> playlistCardCreator = playlist -> {
        MuiModApi.postToUiThread(() -> {
            if (!isAttachedToWindow()) {
                return;
            }
            long id = playlist.getId();
            elementMap.computeIfAbsent(new ElementKey(Playlist.class, id), (key) -> {
                MusicCollectionCard card = new MusicCollectionCard(getContext(), playlist);
                card.setTag(id);
                mySubscribedPlaylistCards.addView(card);
                return card;
            });
        });
    };
    private final Consumer<Album> albumCardCreator = album -> {
        MuiModApi.postToUiThread(() -> {
            if (!isAttachedToWindow()) {
                return;
            }
            long id = album.getId();
            elementMap.computeIfAbsent(new ElementKey(Album.class, id), (key) -> {
                MusicCollectionCard card = new MusicCollectionCard(getContext(), album);
                card.setTag(id);
                albumCards.addView(card);
                return card;
            });
        });
    };
    private final Consumer<Artist> artistCardCreator = artist -> {
        MuiModApi.postToUiThread(() -> {
            if (!isAttachedToWindow()) {
                return;
            }
            long id = artist.getId();
            elementMap.computeIfAbsent(new ElementKey(Artist.class, id), (key) -> {
                ArtistCard artistCard = new ArtistCard(getContext());
                artistCard.setTag(artist.getId());
                artistCard.bindData(artist);
                artistCards.addView(artistCard);
                return artistCard;
            });
        });
    };
    private Unregister playlistAddRegister;
    private Unregister playlistRemoveRegister;
    private Unregister albumAddRegister;
    private Unregister albumRemoveRegister;
    private Unregister artistAddRegister;
    private Unregister artistRemoveRegister;

    private record ElementKey(Class<?> clazz, long id) {}

    public AccountView(Context context) {
        super(context);
//        refresh(false);
        instance = this;
        addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                instance = AccountView.this;
                refresh(false);
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                unregisterCollectionListeners();
                instance = null;
            }
        });
    }

    private void unregisterCollectionListeners() {
        if (playlistAddRegister != null) {
            playlistAddRegister.unregister();
            playlistAddRegister = null;
        }
        if (playlistRemoveRegister != null) {
            playlistRemoveRegister.unregister();
            playlistRemoveRegister = null;
        }
        if (albumAddRegister != null) {
            albumAddRegister.unregister();
            albumAddRegister = null;
        }
        if (albumRemoveRegister != null) {
            albumRemoveRegister.unregister();
            albumRemoveRegister = null;
        }
        if (artistAddRegister != null) {
            artistAddRegister.unregister();
            artistAddRegister = null;
        }
        if (artistRemoveRegister != null) {
            artistRemoveRegister.unregister();
            artistRemoveRegister = null;
        }
    }

    public void refresh(boolean ignoreCache) {
        removeAllViews();
        elementMap.clear();
        setOrientation(LinearLayout.VERTICAL);
        setLayoutParams(new LayoutParams(MATCH_PARENT, MATCH_PARENT));
        Context context = getContext();

        Profile currentProfile = Profile.getCurrent();
        if (currentProfile == null || currentProfile == Profile.ANONYMOUS) {
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
            retryButton.setTextColor(Theme.PRIMARY_COLOR);
            retryButton.setHeight(dp(36));
            retryButton.setWidth(dp(84));
            retryButton.setTextSize(Theme.TEXT_SIZE_NORMAL);
            retryButton.setText(I18n.get(MusicHud.MOD_ID + ".button.retry"));

            LinearLayout buttonsLayout = new LinearLayout(context);
            buttonsLayout.setOrientation(LinearLayout.HORIZONTAL);
            LayoutParams buttonsParams = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            buttonsParams.setMargins(0, dp(8), 0, 0);
            buttonsLayout.setLayoutParams(buttonsParams);
            LayoutTransition transition = new LayoutTransition();
            transition.enableTransitionType(LayoutTransition.CHANGING);
            buttonsLayout.setLayoutTransition(transition);

            ButtonInsetBackgroundFactory backgroundFactory = ButtonInsetBackgroundFactory.builder()
                    .padding(new ButtonInsetBackgroundFactory.Padding(0, 0, 0, 0))
                    .cornerRadius(dp(4)).inset(dp(1)).build();

            Button logoutButton = new Button(context);
            logoutButton.setTextColor(Theme.PRIMARY_COLOR);
            logoutButton.setHeight(dp(36));
            logoutButton.setWidth(dp(84));
            logoutButton.setTextSize(Theme.TEXT_SIZE_NORMAL);
            logoutButton.setText(I18n.get(MusicHud.MOD_ID + ".button.logout"));
            var background2 = backgroundFactory.newBackgroundDrawable();
            logoutButton.setBackground(background2);
            logoutButton.setLayoutParams(new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
            logoutButton.setOnClickListener(b -> {
                IClientLoginService.logout();
            });

            ProgressBar progressRing = new ProgressBar(context);
            progressRing.setIndeterminate(true);
            progressRing.setIndeterminateTintList(ColorStateList.valueOf(Theme.PRIMARY_COLOR));
            progressRing.setVisibility(GONE);
            LayoutParams ringParams = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            ringParams.setMargins(0, dp(32), 0, 0);
            progressRing.setLayoutParams(ringParams);

            var background3 = backgroundFactory.newBackgroundDrawable();
            retryButton.setBackground(background3);
            retryButton.setLayoutParams(new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
            retryButton.setOnClickListener((view) -> {
                MuiModApi.postToUiThread(() -> {
                    retryButton.setVisibility(GONE);
                    progressRing.setVisibility(VISIBLE);
                });
                IClientLoginService.loginToServer(null);
            });


            addView(textView);
            addView(buttonsLayout);
            buttonsLayout.addView(retryButton);
            buttonsLayout.addView(logoutButton);
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

            LayoutParams infoLp1 = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            infoLp1.setMargins(dp(16), 0, 0, 0);
            LinearLayout infoLayout = new LinearLayout(context);
            infoLayout.setOrientation(VERTICAL);
            infoLayout.setGravity(Gravity.CENTER_VERTICAL);
            topPanel.addView(infoLayout, infoLp1);

            LayoutParams nameLayoutParams = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            TextView nickName = new TextView(context);
            nickName.setSingleLine(true);
            nickName.setTextSize(Theme.TEXT_SIZE_LARGER);
            nickName.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
            nickName.setText(currentProfile.getNickname());
            infoLayout.addView(nickName, nameLayoutParams);

            LayoutParams idLayoutParams = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            TextView id = new TextView(context);
            id.setSingleLine(true);
            id.setTextSize(Theme.TEXT_SIZE_NORMAL);
            id.setTextColor(Theme.SECONDARY_TEXT_COLOR);
            id.setText(Long.toString(currentProfile.getUserId()));
            infoLayout.addView(id, idLayoutParams);

            ButtonInsetBackgroundFactory backgroundFactory = ButtonInsetBackgroundFactory.builder()
                    .inset(0).cornerRadius(dp(4))
                    .padding(new ButtonInsetBackgroundFactory.Padding(0, dp(2), 0, dp(2)))
                    .build();

            LinearLayout buttonsLayout = new LinearLayout(context);
            buttonsLayout.setOrientation(LinearLayout.HORIZONTAL);
            infoLayout.addView(buttonsLayout);

            Button refreshButton = new Button(context);
            refreshButton.setTextColor(Theme.PRIMARY_COLOR);
            refreshButton.setTextSize(Theme.TEXT_SIZE_NORMAL);
            refreshButton.setText(I18n.get(MusicHud.MOD_ID + ".button.refresh"));
            var background1 = backgroundFactory.newBackgroundDrawable();
            refreshButton.setBackground(background1);
            LayoutParams params = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            params.setMargins(0, 0, dp(8), 0);
            refreshButton.setLayoutParams(params);
            refreshButton.setOnClickListener(b -> {
                refresh(true);
            });
            buttonsLayout.addView(refreshButton);

            Button logoutButton = new Button(context);
            logoutButton.setText(I18n.get(MusicHud.MOD_ID + ".button.logout"));
            logoutButton.setTextColor(Theme.PRIMARY_COLOR);
            logoutButton.setTextSize(Theme.TEXT_SIZE_NORMAL);
            var background2 = backgroundFactory.newBackgroundDrawable();
            logoutButton.setBackground(background2);
            logoutButton.setOnClickListener(b -> {
                IClientLoginService.logout();
            });
            buttonsLayout.addView(logoutButton, new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));

            LayoutParams topPanelLayoutParams = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            topPanelLayoutParams.setMargins(0, dp(32), 0, dp(32));
            addView(topPanel, topPanelLayoutParams);

            ProgressBar progressBar = new ProgressBar(context);
            progressBar.setIndeterminate(true);
            addView(progressBar, new LayoutParams(MATCH_PARENT, MATCH_PARENT));

            TextView errorText = new TextView(context);
            errorText.setText(I18n.get(MusicHud.MOD_ID + ".text.accountLoadError"));
            errorText.setGravity(Gravity.CENTER);
            errorText.setTextAlignment(TEXT_ALIGNMENT_CENTER);
            errorText.setVisibility(GONE);
            addView(errorText, new LayoutParams(MATCH_PARENT, MATCH_PARENT));

            LinearLayout content = new LinearLayout(context);
            content.setOrientation(VERTICAL);
            content.setLayoutParams(new LayoutParams(MATCH_PARENT, WRAP_CONTENT));
            addView(content);

            {
                myPlaylistsContent = new LinearLayout(context);
                myPlaylistsContent.setOrientation(VERTICAL);
                myPlaylistsContent.setVisibility(GONE);
                LayoutParams myPlaylistsContentParams = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
                myPlaylistsContentParams.setMargins(0, 0, 0, dp(32));
                content.addView(myPlaylistsContent, myPlaylistsContentParams);

                TextView myPlaylistsText = new TextView(context);
                myPlaylistsText.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
                myPlaylistsText.setTextSize(Theme.TEXT_SIZE_LARGE);
                myPlaylistsText.setText(I18n.get(MusicHud.MOD_ID + ".text.myPlaylists"));
                LayoutParams titleParam = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
                titleParam.setMargins(0, 0, 0, dp(16));
                myPlaylistsContent.addView(myPlaylistsText, titleParam);

                myPlaylistCards = new FlexWrapLayout(context);
                myPlaylistsContent.addView(myPlaylistCards, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));
            }
            {
                mySubscribedPlaylistsContent = new LinearLayout(context);
                mySubscribedPlaylistsContent.setOrientation(VERTICAL);
                mySubscribedPlaylistsContent.setVisibility(GONE);
                LayoutParams mySubscribedPlaylistsContentParams = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
                mySubscribedPlaylistsContentParams.setMargins(0, 0, 0, dp(32));
                content.addView(mySubscribedPlaylistsContent, mySubscribedPlaylistsContentParams);

                TextView subscribedPlaylistsText = new TextView(context);
                subscribedPlaylistsText.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
                subscribedPlaylistsText.setTextSize(Theme.TEXT_SIZE_LARGE);
                subscribedPlaylistsText.setText(I18n.get(MusicHud.MOD_ID + ".text.mySubscribedPlaylists"));
                LayoutParams titleParam = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
                titleParam.setMargins(0, 0, 0, dp(16));
                mySubscribedPlaylistsContent.addView(subscribedPlaylistsText, titleParam);

                mySubscribedPlaylistCards = new FlexWrapLayout(context);
                mySubscribedPlaylistsContent.addView(mySubscribedPlaylistCards, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));
            }
            {
                mySubscribedAlbumsContent = new LinearLayout(context);
                mySubscribedAlbumsContent.setOrientation(VERTICAL);
                mySubscribedAlbumsContent.setVisibility(GONE);
                LayoutParams mySubscribedAlbumsContentParams = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
                mySubscribedAlbumsContentParams.setMargins(0, 0, 0, dp(32));
                content.addView(mySubscribedAlbumsContent, mySubscribedAlbumsContentParams);

                TextView subscribedAlbumsText = new TextView(context);
                subscribedAlbumsText.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
                subscribedAlbumsText.setTextSize(Theme.TEXT_SIZE_LARGE);
                subscribedAlbumsText.setText(I18n.get(MusicHud.MOD_ID + ".text.myAlbums"));
                LayoutParams titleParam = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
                titleParam.setMargins(0, 0, 0, dp(16));
                mySubscribedAlbumsContent.addView(subscribedAlbumsText, titleParam);

                albumCards = new FlexWrapLayout(context);
                mySubscribedAlbumsContent.addView(albumCards, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));
            }
            {
                mySubscribedArtistsContent = new LinearLayout(context);
                mySubscribedArtistsContent.setOrientation(VERTICAL);
                mySubscribedArtistsContent.setVisibility(GONE);
                LayoutParams mySubscribedArtistsContentParams = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
                mySubscribedArtistsContentParams.setMargins(0, 0, 0, dp(32));
                content.addView(mySubscribedArtistsContent, mySubscribedArtistsContentParams);

                TextView artistText = new TextView(context);
                artistText.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
                artistText.setTextSize(Theme.TEXT_SIZE_LARGE);
                artistText.setText(I18n.get(MusicHud.MOD_ID + ".text.myArtists"));
                LayoutParams titleParam = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
                titleParam.setMargins(0, 0, 0, dp(16));
                mySubscribedArtistsContent.addView(artistText, titleParam);

                artistCards = new FlexWrapLayout(context);
                mySubscribedArtistsContent.addView(artistCards, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));
            }

            MusicService musicService = MusicService.getInstance();
            musicService.loadUserCollections(ignoreCache).thenAccept(userCollections -> {
                MuiModApi.postToUiThread(() -> {
                    if (!isAttachedToWindow()) {
                        return;
                    }
                    unregisterCollectionListeners();
                    UserCategoryPlaylists categoryPlaylists = userCollections.getUserCategoryPlaylists();
                    myPlaylistCards.addView(new MusicCollectionCard(context, categoryPlaylists.getLikeList()));
                    ObservableSequencedSet<Playlist> createdPlaylist = categoryPlaylists.getCreatedPlaylist();
                    createdPlaylist.forEach(playlist -> myPlaylistCards.addView(new MusicCollectionCard(context, playlist)));
                    ObservableSequencedSet<Playlist> subscribedPlaylist = categoryPlaylists.getSubscribedPlaylist();
                    subscribedPlaylist.forEach(playlistCardCreator);
                    playlistAddRegister = subscribedPlaylist.registerOnAdd(playlistCardCreator);
                    playlistRemoveRegister = subscribedPlaylist.registerOnRemove(playlist -> {
                        MuiModApi.postToUiThread(() -> {
                            if (!isAttachedToWindow()) {
                                return;
                            }
                            View toRemove = elementMap.remove(new ElementKey(Playlist.class, playlist.getId()));
                            if (toRemove != null) {
                                mySubscribedPlaylistCards.removeView(toRemove);
                            }
                        });
                    });
                    myPlaylistsContent.setVisibility(createdPlaylist.isEmpty() ? GONE : VISIBLE);
                    mySubscribedPlaylistsContent.setVisibility(subscribedPlaylist.isEmpty() ? GONE : VISIBLE);

                    ObservableSequencedSet<Album> albums = userCollections.getSubscribedAlbums();
                    albums.forEach(albumCardCreator);
                    albumAddRegister = albums.registerOnAdd(albumCardCreator);
                    albumRemoveRegister = albums.registerOnRemove(album -> {
                        MuiModApi.postToUiThread(() -> {
                            if (!isAttachedToWindow()) {
                                return;
                            }
                            View toRemove = elementMap.remove(new ElementKey(Album.class, album.getId()));
                            if (toRemove != null) {
                                albumCards.removeView(toRemove);
                            }
                        });
                    });
                    mySubscribedAlbumsContent.setVisibility(albums.isEmpty() ? GONE : VISIBLE);

                    ObservableSequencedSet<Artist> artists = userCollections.getSubscribedArtists();
                    artists.forEach(artistCardCreator);
                    artistAddRegister = artists.registerOnAdd(artistCardCreator);
                    artistRemoveRegister = artists.registerOnRemove(artist -> {
                        MuiModApi.postToUiThread(() -> {
                            if (!isAttachedToWindow()) {
                                return;
                            }
                            View toRemove = elementMap.remove(new ElementKey(Artist.class, artist.getId()));
                            if (toRemove != null) {
                                artistCards.removeView(toRemove);
                            }
                        });
                    });
                    mySubscribedArtistsContent.setVisibility(artists.isEmpty() ? GONE : VISIBLE);

                    progressBar.setVisibility(View.GONE);
                });
            }).exceptionally((e) -> {
                MuiModApi.postToUiThread(() -> {
                    if (isAttachedToWindow()) {
                        progressBar.setVisibility(View.GONE);
                        errorText.setVisibility(View.VISIBLE);
                    }
                });
                return null;
            });
        }
    }
}