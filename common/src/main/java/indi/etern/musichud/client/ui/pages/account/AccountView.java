package indi.etern.musichud.client.ui.pages.account;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Image;
import icyllis.modernui.graphics.drawable.InsetDrawable;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.text.SpannableString;
import icyllis.modernui.text.Spanned;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.*;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.*;
import indi.etern.musichud.beans.user.Profile;
import indi.etern.musichud.beans.user.VipType;
import indi.etern.musichud.client.services.LoginService;
import indi.etern.musichud.client.services.music.MusicService;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.components.RouterContainer;
import indi.etern.musichud.client.ui.components.cards.ArtistCard;
import indi.etern.musichud.client.ui.layouts.FlexWrapLayout;
import indi.etern.musichud.client.ui.components.cards.MusicCollectionCard;
import indi.etern.musichud.client.ui.components.UrlImageView;
import indi.etern.musichud.client.ui.drawable.ScaledImageDrawable;
import indi.etern.musichud.client.utils.image.ImageUtils;
import indi.etern.musichud.client.utils.ui.InsetBackgroundFactory;
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
                MusicCollectionCard card = new MusicCollectionCard(getContext(), playlist, PusherInfo.EMPTY);
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
                MusicCollectionCard card = new MusicCollectionCard(getContext(), album, PusherInfo.EMPTY);
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

    private static void buildCard(Context context, InsetBackgroundFactory backgroundFactory1, LinearLayout line1, String name, String icon, View.OnClickListener onClickListener) {
        Button child = new Button(context);
        child.setTextSize(Theme.TEXT_SIZE_LARGE);
        SpannableString string = new SpannableString("  " + name);
        Image image1 = ImageUtils.getImageFromResource(icon);
        if (image1 != null) {
            string.setSpan(ImageUtils.getIconSpan(image1), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        child.setText(string);
        backgroundFactory1.applyBackgroundTo(child);
        if (onClickListener != null) {
            child.setClickable(true);
            child.setOnClickListener(onClickListener);
        }
        line1.addView(child, new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
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
        LinearLayout topBar = new LinearLayout(context);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.LEFT);
        LayoutParams topPanelLayoutParams = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        topPanelLayoutParams.setMargins(0, dp(32), 0, dp(32));
        addView(topBar, topPanelLayoutParams);

        UrlImageView avatar = new UrlImageView(context);
        avatar.setCircular(true);
        LayoutParams layoutParams = new LayoutParams(dp(80), dp(80));
        avatar.setLayoutParams(layoutParams);
        topBar.addView(avatar);
        avatar.loadUrl(currentProfile.getAvatarUrl());

        LayoutParams infoLp1 = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        infoLp1.setMargins(dp(8), 0, 0, 0);
        LinearLayout topBarContent = new LinearLayout(context);
        topBarContent.setOrientation(VERTICAL);
        topBarContent.setGravity(Gravity.CENTER_VERTICAL);
        topBar.addView(topBarContent, infoLp1);

        int dp6 = dp(6);
        int dp8 = dp(8);

        LinearLayout topBarLine1 = new LinearLayout(context);
        topBarLine1.setGravity(Gravity.CENTER_VERTICAL);
        topBarLine1.setOrientation(HORIZONTAL);
        LayoutParams topBarLine1Params = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        topBarLine1Params.setMargins(dp8, 0, 0, 0);
        topBarContent.addView(topBarLine1, topBarLine1Params);

        {
            LinearLayout userInfo = new LinearLayout(context);
            userInfo.setGravity(Gravity.LEFT);
            userInfo.setOrientation(VERTICAL);
            LayoutParams params = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            params.setMargins(0, 0, dp(16), 0);
            topBarLine1.addView(userInfo, params);

            TextView nickName = new TextView(context);
            nickName.setSingleLine(true);
            nickName.setTextSize(Theme.TEXT_SIZE_LARGER);
            nickName.setTextColor(Theme.EMPHASIZE_TEXT_COLOR);
            nickName.setText(currentProfile.getNickname());
            LayoutParams nameLayoutParams = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            nameLayoutParams.setMargins(0, 0, 0, dp(2));
            userInfo.addView(nickName, nameLayoutParams);

            LinearLayout line2 = new LinearLayout(context);
            line2.setOrientation(HORIZONTAL);
            userInfo.addView(line2, new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));

            VipType vipType1 = currentProfile.getVipType();
            if (vipType1 != VipType.NONE) {
                TextView vipType = new TextView(context);
                vipType.setSingleLine(true);
                vipType.setTextSize(Theme.TEXT_SIZE_NORMAL);
                vipType.setTextColor(Theme.SECONDARY_TEXT_COLOR);
                vipType.setText(I18n.get(MusicHud.MOD_ID + ".text.vip." + vipType1.name()));
                LayoutParams params1 = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
                params1.setMargins(0, 0, dp(8), 0);
                line2.addView(vipType, params1);
            }

            TextView id = new TextView(context);
            id.setSingleLine(true);
            id.setTextSize(Theme.TEXT_SIZE_NORMAL);
            id.setTextColor(Theme.SECONDARY_TEXT_COLOR);
            id.setText(Long.toString(currentProfile.getUserId()));
            line2.addView(id);
        }
//        {
//            LinearLayout buttonsLayout1 = new LinearLayout(context);
//            buttonsLayout1.setOrientation(LinearLayout.HORIZONTAL);
//            buttonsLayout1.setGravity(Gravity.CENTER_VERTICAL);
//            topBarLine1.addView(buttonsLayout1);
//        }

        LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        topBarContent.addView(buttons);

        InsetBackgroundFactory backgroundFactory1 = InsetBackgroundFactory.builder()
                .backgroundColor(Theme.GHOST_BUTTON_STATES)
                .inset(dp(1))
                .cornerRadius(dp(4))
                .padding(new InsetBackgroundFactory.Padding(dp8, dp6, dp8, dp6))
                .build();
        buildCard(context, backgroundFactory1, buttons, I18n.get(MusicHud.MOD_ID + ".button.history"), "/assets/music_hud/textures/gui/icons/rotate_ccw_clock.png",
                v -> {
                    RouterContainer routerContainer = RouterContainer.getInstance();
                    if (routerContainer != null) {
                        routerContainer.pushNavigate(new PlayHistoryView(context));
                    }
                });
        buildCard(context, backgroundFactory1, buttons, I18n.get(MusicHud.MOD_ID + ".button.cloud"), "/assets/music_hud/textures/gui/icons/cloud.png",
                v -> {
                    RouterContainer routerContainer = RouterContainer.getInstance();
                    if (routerContainer != null) {
                        routerContainer.pushNavigate(new CloudDriveView(context));
                    }
                });

        InsetBackgroundFactory backgroundFactory2 = InsetBackgroundFactory.builder()
                .backgroundColor(Theme.GHOST_BUTTON_STATES)
                .inset(dp(1))
                .cornerRadius(dp(4))
                .padding(new InsetBackgroundFactory.Padding(dp6, dp6, dp6, dp6))
                .build();
        {
            ImageButton refreshButton = new ImageButton(context);
            backgroundFactory2.applyBackgroundTo(refreshButton);
            refreshButton.setTooltipText(I18n.get(MusicHud.MOD_ID + ".button.refresh"));
            refreshButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            var resources = getContext().getResources();
            Image image1 = ImageUtils.getImageFromResource("/assets/music_hud/textures/gui/icons/rotate_cw.png");
            refreshButton.setImageDrawable(new InsetDrawable(new ScaledImageDrawable(resources, image1, dp(12), dp(16)), dp(3)));
            refreshButton.setOnClickListener((v) -> {
                refresh(true);
            });
            buttons.addView(refreshButton, new LayoutParams(WRAP_CONTENT, MATCH_PARENT));
        }
        {
            ImageButton logoutButton = new ImageButton(context);
            backgroundFactory2.applyBackgroundTo(logoutButton);
            logoutButton.setTooltipText(I18n.get(MusicHud.MOD_ID + ".button.logout"));
            logoutButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            var resources = getContext().getResources();
            Image image1 = ImageUtils.getImageFromResource("/assets/music_hud/textures/gui/icons/log_out.png");
            logoutButton.setImageDrawable(new InsetDrawable(new ScaledImageDrawable(resources, image1, dp(12), dp(16)), dp(3)));
            logoutButton.setOnClickListener((v) -> {
                IClientLoginService.logoutAndReloginAsAnonymous();
            });
            buttons.addView(logoutButton, new LayoutParams(WRAP_CONTENT, MATCH_PARENT));
        }

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
                if (likeList != Playlist.EMPTY) {
                    elementMap.computeIfAbsent(new ElementKey(Playlist.class, likeList.getId()), key -> {
                        MusicCollectionCard card = new MusicCollectionCard(context, likeList, PusherInfo.EMPTY);
                        card.setTag(likeList.getId());
                        myPlaylistCards.addView(card);
                        return card;
                    });
                }
                ObservableSequencedSet<Playlist> createdPlaylist = categoryPlaylists.getCreatedPlaylist();
                createdPlaylist.forEach(playlist -> elementMap.computeIfAbsent(new ElementKey(Playlist.class, playlist.getId()), key -> {
                    MusicCollectionCard card = new MusicCollectionCard(context, playlist, PusherInfo.EMPTY);
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
            e.printStackTrace();
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