package indi.etern.musichud.client.ui.pages.account;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Image;
import icyllis.modernui.graphics.drawable.InsetDrawable;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.*;
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
import indi.etern.musichud.client.ui.drawable.ScaledImageDrawable;
import indi.etern.musichud.client.utils.image.ImageUtils;
import indi.etern.musichud.client.utils.ui.ButtonInsetBackgroundFactory;
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
    private final Map<ElementKey, View> elementMap = new HashMap<>();
    private FlexWrapLayout myPlaylistCards;
    private FlexWrapLayout mySubscribedPlaylistCards;
    private FlexWrapLayout albumCards;
    private FlexWrapLayout artistCards;
    private LinearLayout myPlaylistsContent;
    private LinearLayout mySubscribedPlaylistsContent;
    private LinearLayout mySubscribedAlbumsContent;
    private LinearLayout mySubscribedArtistsContent;
    private Unregister playlistAddRegister;
    private Unregister playlistRemoveRegister;
    private Unregister albumAddRegister;
    private Unregister albumRemoveRegister;
    private Unregister artistAddRegister;
    private Unregister artistRemoveRegister;
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

        LinearLayout buttonsLayout = new LinearLayout(context);
        buttonsLayout.setOrientation(LinearLayout.HORIZONTAL);
        infoLayout.addView(buttonsLayout);

        ButtonInsetBackgroundFactory backgroundFactory = ButtonInsetBackgroundFactory.builder()
                .backgroundColor(Theme.GHOST_BUTTON_STATES)
                .inset(0)
                .cornerRadius(dp(4))
                .padding(new ButtonInsetBackgroundFactory.Padding(dp(2), dp(2), dp(2), dp(2)))
                .build();
        int dp28 = dp(28);
        {
            ImageButton refreshButton = new ImageButton(context);
            refreshButton.setTooltipText(I18n.get(MusicHud.MOD_ID + ".button.refresh"));
            refreshButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            var resources = getContext().getResources();
            Image image1 = ImageUtils.getImageFromResource("/assets/music_hud/textures/gui/icons/rotate_cw.png");
            refreshButton.setImageDrawable(new InsetDrawable(new ScaledImageDrawable(resources, image1, dp(12), dp(16)), dp(3)));
            refreshButton.setOnClickListener((v) -> {
                refresh(true);
            });
            buttonsLayout.addView(refreshButton, new LayoutParams(dp28, dp28));
        }
        {
            ImageButton logoutButton = new ImageButton(context);
            logoutButton.setTooltipText(I18n.get(MusicHud.MOD_ID + ".button.logout"));
            logoutButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            var resources = getContext().getResources();
            Image image1 = ImageUtils.getImageFromResource("/assets/music_hud/textures/gui/icons/log_out.png");
            logoutButton.setImageDrawable(new InsetDrawable(new ScaledImageDrawable(resources, image1, dp(12), dp(16)), dp(3)));
            logoutButton.setOnClickListener((v) -> {
                IClientLoginService.logoutAndReloginAsAnonymous();
            });
            buttonsLayout.addView(logoutButton, new LayoutParams(dp28, dp28));
        }

//        Button refreshButton = new Button(context);
//        refreshButton.setTextColor(Theme.PRIMARY_COLOR);
//        refreshButton.setTextSize(Theme.TEXT_SIZE_NORMAL);
//        refreshButton.setText(I18n.get(MusicHud.MOD_ID + ".button.refresh"));
//        var background1 = backgroundFactory.newBackgroundDrawable();
//        refreshButton.setBackground(background1);
//        LayoutParams params = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
//        params.setMargins(0, 0, dp(8), 0);
//        refreshButton.setLayoutParams(params);
//        refreshButton.setOnClickListener(b -> {
//            refresh(true);
//        });
//        buttonsLayout.addView(refreshButton);

//        Button logoutButton = new Button(context);
//        logoutButton.setText(I18n.get(MusicHud.MOD_ID + ".button.logout"));
//        logoutButton.setTextColor(Theme.PRIMARY_COLOR);
//        logoutButton.setTextSize(Theme.TEXT_SIZE_NORMAL);
//        var background2 = backgroundFactory.newBackgroundDrawable();
//        logoutButton.setBackground(background2);
//        logoutButton.setOnClickListener(b -> {
//            IClientLoginService.logoutAndReloginAsAnonymous();
//        });
//        buttonsLayout.addView(logoutButton, new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));

        LayoutParams topPanelLayoutParams = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        topPanelLayoutParams.setMargins(0, dp(32), 0, dp(32));
        addView(topPanel, topPanelLayoutParams);

        ProgressBar progressBar = new ProgressBar(context);
        progressBar.setIndeterminate(true);
        addView(progressBar, new LayoutParams(MATCH_PARENT, MATCH_PARENT));

        TextView errorText = new TextView(context);
        errorText.setText(I18n.get(MusicHud.MOD_ID + ".text.accountLoadError"));
        errorText.setGravity(Gravity.CENTER);
        errorText.setTextSize(Theme.TEXT_SIZE_NORMAL);
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
                Playlist likeList = categoryPlaylists.getLikeList();
                elementMap.computeIfAbsent(new ElementKey(Playlist.class, likeList.getId()), key -> {
                    MusicCollectionCard card = new MusicCollectionCard(context, likeList);
                    card.setTag(likeList.getId());
                    myPlaylistCards.addView(card);
                    return card;
                });
                ObservableSequencedSet<Playlist> createdPlaylist = categoryPlaylists.getCreatedPlaylist();
                createdPlaylist.forEach(playlist -> elementMap.computeIfAbsent(new ElementKey(Playlist.class, playlist.getId()), key -> {
                    MusicCollectionCard card = new MusicCollectionCard(context, playlist);
                    card.setTag(playlist.getId());
                    myPlaylistCards.addView(card);
                    return card;
                }));
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

                post(() -> {
                    myPlaylistsContent.setVisibility(createdPlaylist.isEmpty() ? GONE : VISIBLE);
                    mySubscribedPlaylistsContent.setVisibility(subscribedPlaylist.isEmpty() ? GONE : VISIBLE);
                    mySubscribedAlbumsContent.setVisibility(albums.isEmpty() ? GONE : VISIBLE);
                    mySubscribedArtistsContent.setVisibility(artists.isEmpty() ? GONE : VISIBLE);
                });

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

    private record ElementKey(Class<?> clazz, long id) {
    }
}