package indi.etern.musichud.client.ui.components.cards;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Image;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.text.SpannableString;
import icyllis.modernui.text.Spanned;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.*;
import indi.etern.musichud.beans.user.Profile;
import indi.etern.musichud.beans.user.ProfileConfigData;
import indi.etern.musichud.client.services.music.MusicService;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.components.IdlePlaySourceWidget;
import indi.etern.musichud.client.ui.components.PusherWidget;
import indi.etern.musichud.client.ui.components.RouterContainer;
import indi.etern.musichud.client.ui.components.UrlImageView;
import indi.etern.musichud.client.ui.components.buttons.ToggleSubscribeButton;
import indi.etern.musichud.client.ui.layouts.FlexWrapLayout;
import indi.etern.musichud.client.ui.pages.routes.MusicCollectionDetailView;
import indi.etern.musichud.client.ui.screen.MainFragment;
import indi.etern.musichud.client.utils.CountFormatter;
import indi.etern.musichud.client.utils.image.ImageUtils;
import indi.etern.musichud.client.utils.ui.InsetBackgroundFactory;
import indi.etern.musichud.interfaces.Unregister;
import indi.etern.musichud.utils.CollectionUpdateNotifier;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class MusicCollectionCard extends LinearLayout {
    private static final String ICON_LIST_MUSIC = "/assets/music_hud/textures/gui/icons/list_music.png";
    private static final String ICON_LIKE_LIST_MUSIC = "/assets/music_hud/textures/gui/icons/heart_filled.png";
    private static final String ICON_RECOMMEND_LIST_MUSIC = "/assets/music_hud/textures/gui/icons/radio.png";
    private static final String ICON_AUDIO_LINES = "/assets/music_hud/textures/gui/icons/audio_lines.png";
    private static final String ICON_DISC_ALBUM = "/assets/music_hud/textures/gui/icons/disc_album.png";
    private static final String ICON_LAYOUT_GRID = "/assets/music_hud/textures/gui/icons/layout_grid.png";
    private static final MusicService musicService = MusicService.getInstance();
    private final ProfileConfigData profileConfigData = ProfileConfigData.getInstance();
    private final AtomicBoolean refreshPending = new AtomicBoolean();
    private final UrlImageView imageView;
    private final TextView nameView;
    private final int albumSize;
    private final boolean isPrivatePlaylistToUser;
    private final LinearLayout content;
    @Getter
    private int imageCornerRadius = dp(8);
    @Getter
    private int backgroundPadding = dp(6);
    @Getter
    MusicCollection musicCollection;
    private Unregister onChangeUnregister;
    private Unregister updateNotifierUnregister;
    private TextView musicTrackCountView;
    private TextView playedCountView;
    private TextView albumTypeView;

    public MusicCollectionCard(Context context, MusicCollection musicCollection, PusherInfo pusherInfo, int albumSize) {
        super(context);
        this.musicCollection = musicCollection;
        this.albumSize = albumSize;

        setOrientation(VERTICAL);
        setLayoutParams(new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));

        imageView = new UrlImageView(context);
        LayoutParams imageParams = new LinearLayout.LayoutParams(albumSize, albumSize, 0);
        imageParams.gravity = Gravity.CENTER;
        imageView.setAspectRatio(1);
        addView(imageView, imageParams);

        addView(new View(context), new LinearLayout.LayoutParams(dp(8), dp(4), 0));

        onChangeUnregister = musicCollection.getMusicDetails().registerOnChange(() -> {
            if (!(musicCollection instanceof Playlist playlist) || playlist.getSpecialType() != PlaylistSpecialType.USER_SPECIFIC) {
                MuiModApi.postToUiThread(() -> {
                    imageView.loadUrl(musicCollection.getImageThumbnailUrl(albumSize));
                });
            }
        });
        registerUpdateNotifier();
        addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                registerUpdateNotifier();
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                if (onChangeUnregister != null) {
                    onChangeUnregister.unregister();
                    onChangeUnregister = null;
                }
                unregisterUpdateNotifier();
            }
        });
        imageView.loadUrl(musicCollection.getImageThumbnailUrl(albumSize));

        content = new LinearLayout(context);
        content.setOrientation(VERTICAL);
        addView(content, new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, 1f));
        {
            FlexWrapLayout row1 = new FlexWrapLayout(context);
            row1.setLineGravity(Gravity.TOP);
            content.addView(row1, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));

            LinearLayout row2 = new LinearLayout(context);
            row2.setOrientation(HORIZONTAL);
            row2.setBaselineAligned(false);
            row2.setGravity(Gravity.TOP);
            content.addView(row2, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));

            LinearLayout texts = new LinearLayout(context);
            texts.setOrientation(HORIZONTAL);
            LayoutParams params2 = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            params2.setMargins(dp(2), dp(2.5f), 0, 0);
            row1.addView(texts, params2);
            if (musicCollection instanceof Playlist playlist) {
                {
                    musicTrackCountView = new TextView(context);
                    musicTrackCountView.setTextSize(Theme.TEXT_SIZE_NORMAL);
                    String iconPath = switch (playlist.getSpecialType()) {
                        case LIKE_LIST -> ICON_LIKE_LIST_MUSIC;
                        case USER_SPECIFIC -> ICON_RECOMMEND_LIST_MUSIC;
                        default -> ICON_LIST_MUSIC;
                    };
                    musicTrackCountView.setText(buildIconText(String.valueOf(playlist.getMusicTrackCount()), iconPath));
                    LayoutParams params1 = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT, 0);
                    params1.setMargins(0, 0, dp(8), 0);
                    texts.addView(musicTrackCountView, params1);
                }
                {
                    playedCountView = new TextView(context);
                    playedCountView.setTextSize(Theme.TEXT_SIZE_NORMAL);
                    playedCountView.setText(buildIconText(CountFormatter.formatCount(playlist.getPlayedCount()), ICON_AUDIO_LINES));
                    texts.addView(playedCountView, new LayoutParams(WRAP_CONTENT, WRAP_CONTENT, 0));
                }
            } else if (musicCollection instanceof Album album) {
                {
                    musicTrackCountView = new TextView(context);
                    musicTrackCountView.setTextSize(Theme.TEXT_SIZE_NORMAL);
                    musicTrackCountView.setText(buildIconText(String.valueOf(album.getMusicTrackCount()), ICON_DISC_ALBUM));
                    LayoutParams params1 = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT, 0);
                    params1.setMargins(0, 0, dp(8), 0);
                    texts.addView(musicTrackCountView, params1);
                }
                String type = album.getType();
                if (!type.isBlank()) {
                    albumTypeView = new TextView(context);
                    albumTypeView.setTextSize(Theme.TEXT_SIZE_NORMAL);
                    albumTypeView.setText(buildIconText(mappedAlbumType(type), ICON_LAYOUT_GRID));
                    texts.addView(albumTypeView, new LayoutParams(WRAP_CONTENT, WRAP_CONTENT, 0));
                }
            }
            row1.addView(new View(context), new LayoutParams(WRAP_CONTENT, MATCH_PARENT, 1));

            LinearLayout buttons = new LinearLayout(context);
            buttons.setOrientation(HORIZONTAL);
            buttons.setGravity(Gravity.CENTER_VERTICAL);
            LayoutParams params3 = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            params3.setMargins(dp(-2), 0, 0, 0);
            row1.addView(buttons, params3);
            int dp2 = dp(2);
            InsetBackgroundFactory backgroundFactory = InsetBackgroundFactory.builder()
                    .backgroundColor(Theme.GHOST_BUTTON_STATES)
                    .inset(0)
                    .cornerRadius(dp(4))
                    .padding(new InsetBackgroundFactory.Padding(dp2, dp2, dp2, dp2))
                    .build();
            {
                ToggleSubscribeButton toggleSubscribeButton = new ToggleSubscribeButton(context);
                backgroundFactory.applyBackgroundTo(toggleSubscribeButton);
                buttons.addView(toggleSubscribeButton, new LayoutParams(dp(22), dp(22), 0));
                if (musicCollection instanceof Playlist playlist) {
                    Profile current = Profile.getCurrent();
                    if (current == null || current.equals(Profile.ANONYMOUS) || playlist.getCreator().getUserId() == current.getUserId()) {
                        toggleSubscribeButton.setVisibility(GONE);
                    } else {
                        var subscribeState = musicService.getPlaylistSubscribeState(playlist);
                        toggleSubscribeButton.bindState(subscribeState);
                    }
                } else if (musicCollection instanceof Album album) {
                    var subscribeState = musicService.getAlbumSubscribeState(album);
                    toggleSubscribeButton.bindState(subscribeState);
                }
            }

            nameView = new TextView(context);
            nameView.setTextSize(Theme.TEXT_SIZE_NORMAL);
            nameView.setTextColor(Theme.NORMAL_TEXT_COLOR);
            nameView.setSingleLine(false);
            nameView.setMaxLines(4);
            isPrivatePlaylistToUser = musicCollection instanceof Playlist playlist && playlist.getPrivacy() == Privacy.PRIVATE
                    && !playlist.getCreator().equals(profileConfigData.getProfile());
            LayoutParams params1 = new LayoutParams(0, WRAP_CONTENT, 1);
            params1.setMargins(dp(2), 0, dp(2), 0);
            row2.addView(nameView, params1);

            LocalPlayer localPlayer = Minecraft.getInstance().player;
            if (pusherInfo == null || pusherInfo.equals(PusherInfo.EMPTY)
                    || (localPlayer != null && pusherInfo.playerUUID().equals(localPlayer.getUUID()))) {
                var idlePlaySourceWidget = new IdlePlaySourceWidget(context, this.musicCollection, dp(22));
                buttons.addView(idlePlaySourceWidget, new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
            } else {
                PusherWidget pusherWidget = new PusherWidget(context);
                pusherWidget.bind(pusherInfo);

                LayoutParams pusherParams = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
                pusherParams.setMargins(dp(4), dp(4), 0, dp(4));
                content.addView(pusherWidget, pusherParams);
            }

            if (!isPrivatePlaylistToUser) {
                setClickable(true);
                setFocusable(true);
                setOnClickListener(v -> {
                    RouterContainer routerContainer = MainFragment.getInstance().getRouterContainer();
                    if (routerContainer != null) {
                        routerContainer.pushNavigate(
                                new MusicCollectionDetailView(context, musicCollection)
                        );
                    }
                });
            }
            applyCorner();
        }
        applyContentMetrics();
        refreshCollectionInfo();
    }

    public void setBackgroundPadding(int padding) {
        this.backgroundPadding = padding;
        applyCorner();
    }

    public void setImageCornerRadius(int cornerRadius) {
        this.imageCornerRadius = cornerRadius;
        applyCorner();
    }

    private void applyCorner() {
        imageView.setCornerRadius(imageCornerRadius);
        if (!isPrivatePlaylistToUser) {
            int inset = dp(1);
            InsetBackgroundFactory.builder().inset(inset)
                    .cornerRadius(imageCornerRadius + backgroundPadding - inset * 2)
                    .padding(new InsetBackgroundFactory.Padding(backgroundPadding, backgroundPadding, backgroundPadding, backgroundPadding))
                    .build().applyBackgroundTo(this);
        } else {
            ShapeDrawable background = new ShapeDrawable();
            background.setPadding(backgroundPadding, backgroundPadding, backgroundPadding, backgroundPadding);
            setBackground(background);
        }
    }

    @Override
    public void setOrientation(int orientation) {
        super.setOrientation(orientation);
        applyContentMetrics();
    }

    /**
     * Vertical pins the card to the image width so rows wrap instead of widening it; horizontal
     * keeps a wrapping card and lets the content fill at least the image height.
     */
    private void applyContentMetrics() {
        if (content == null) {
            return;
        }
        boolean horizontal = getOrientation() == HORIZONTAL;
        content.setMinimumWidth(horizontal ? 0 : albumSize);
        content.setMinimumHeight(horizontal ? albumSize : dp(54));
        setLayoutParams(new LayoutParams(
                horizontal ? WRAP_CONTENT : albumSize + 2 * (backgroundPadding + dp(1)),
                WRAP_CONTENT));
    }

    private SpannableString buildIconText(String text, String iconPath) {
        SpannableString spannableText = new SpannableString("  " + text);
        Image icon = ImageUtils.getImageFromResource(iconPath);
        if (icon != null) {
            spannableText.setSpan(ImageUtils.getIconSpan(icon), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return spannableText;
    }

    private void refreshCollectionInfo() {
        MusicCollection collection = musicCollection;
        if (collection == null) return;
        if (!(musicCollection instanceof Playlist playlist) || playlist.getSpecialType() != PlaylistSpecialType.USER_SPECIFIC) {
            imageView.loadUrl(collection.getImageThumbnailUrl(dp(160)));
        }
        if (collection instanceof Playlist playlist) {
            String iconPath = switch (playlist.getSpecialType()) {
                case LIKE_LIST -> ICON_LIKE_LIST_MUSIC;
                case USER_SPECIFIC -> ICON_RECOMMEND_LIST_MUSIC;
                default -> ICON_LIST_MUSIC;
            };
            musicTrackCountView.setText(buildIconText(String.valueOf(playlist.getMusicTrackCount()), iconPath));
            if (playedCountView != null) {
                playedCountView.setText(buildIconText(CountFormatter.formatCount(playlist.getPlayedCount()), ICON_AUDIO_LINES));
            }
        } else if (collection instanceof Album album) {
            musicTrackCountView.setText(buildIconText(String.valueOf(album.getMusicTrackCount()), ICON_DISC_ALBUM));
            if (albumTypeView != null) {
                albumTypeView.setText(buildIconText(mappedAlbumType(album.getType()), ICON_LAYOUT_GRID));
            }
        }
        boolean isPrivatePlaylistToUser = collection instanceof Playlist playlist
                && playlist.getPrivacy() == Privacy.PRIVATE
                && !playlist.getCreator().equals(profileConfigData.getProfile());
        nameView.setText(isPrivatePlaylistToUser
                ? I18n.get(MusicHud.MOD_ID + ".text.privatePlaylist")
                : collection.getName());
    }

    private void registerUpdateNotifier() {
        if (updateNotifierUnregister != null) {
            updateNotifierUnregister.unregister();
        }
        MusicCollection collection = musicCollection;
        if (collection instanceof Playlist playlist) {
            updateNotifierUnregister = CollectionUpdateNotifier.registerPlaylist(playlist.getId(), this::onCollectionUpdateNotified);
        } else if (collection instanceof Album album) {
            updateNotifierUnregister = CollectionUpdateNotifier.registerAlbum(album.getId(), this::onCollectionUpdateNotified);
        }
    }

    private void unregisterUpdateNotifier() {
        if (updateNotifierUnregister != null) {
            updateNotifierUnregister.unregister();
            updateNotifierUnregister = null;
        }
    }

    private void onCollectionUpdateNotified(boolean operateByRemoteSelf) {
        if (!refreshPending.compareAndSet(false, true)) {
            return;
        }
        MusicCollection collection = musicCollection;
        if (collection == null) {
            refreshPending.set(false);
            return;
        }
        long id = collection.getId();
        CompletableFuture<? extends MusicCollection> future;
        if (collection instanceof Album) {
            future = musicService.loadAlbumDetail(id, false);
        } else {
            future = musicService.loadPlaylistDetail(id, false);
        }
        future.whenComplete((latest, throwable) -> {
            refreshPending.set(false);
            if (throwable != null || !MusicCollections.isUsable(latest)) return;
            MuiModApi.postToUiThread(() -> {
                if (latest != musicCollection) {
                    musicCollection = latest;
                }
                refreshCollectionInfo();
            });
        });
    }

    private String mappedAlbumType(String type) {
        return switch (type) {
            case "专辑" -> I18n.get(MusicHud.MOD_ID + ".text.album.type.album");
            case "EP" -> I18n.get(MusicHud.MOD_ID + ".text.album.type.ep");
            case "Single" -> I18n.get(MusicHud.MOD_ID + ".text.album.type.single");
            case "精选集" -> I18n.get(MusicHud.MOD_ID + ".text.album.type.compilation");
            default -> type;
        };
    }
}
