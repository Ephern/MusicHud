package indi.etern.musichud.client.ui.components;

import icyllis.modernui.animation.LayoutTransition;
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
import indi.etern.musichud.client.utils.PlayerInfoUtil;
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
    private static final String ICON_AUDIO_LINES = "/assets/music_hud/textures/gui/icons/audio_lines.png";
    private static final String ICON_DISC_ALBUM = "/assets/music_hud/textures/gui/icons/disc_album.png";
    private static final String ICON_LAYOUT_GRID = "/assets/music_hud/textures/gui/icons/layout_grid.png";
    private static final MusicService musicService = MusicService.getInstance();
    private final ProfileConfigData profileConfigData = ProfileConfigData.getInstance();
    @Getter
    MusicCollection musicCollection;
    private Unregister onChangeUnregister;
    private Unregister updateNotifierUnregister;
    private final AtomicBoolean refreshPending = new AtomicBoolean();
    private UrlImageView imageView;
    private TextView nameView;
    private TextView musicTrackCountView;
    private TextView playedCountView;
    private TextView albumTypeView;

    public MusicCollectionCard(Context context, MusicCollection musicCollection) {
        super(context);
        this.musicCollection = musicCollection;

        setOrientation(VERTICAL);

        LayoutParams params = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        setLayoutParams(params);

        imageView = new UrlImageView(context);
        int dp160 = dp(160);
        LayoutParams imageParams = new LinearLayout.LayoutParams(dp160, dp160, 1);
        imageParams.setMargins(0, 0, 0, dp(4));
        imageView.setAspectRatio(1);
        addView(imageView, imageParams);

        onChangeUnregister = musicCollection.getMusicDetails().registerOnChange(() -> {
            MuiModApi.postToUiThread(() -> {
                imageView.loadUrl(musicCollection.getImageThumbnailUrl(dp160));
            });
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
        imageView.loadUrl(musicCollection.getImageThumbnailUrl(dp160));
        imageView.setCornerRadius(dp(8));

        FlexWrapLayout row1 = new FlexWrapLayout(context);
        row1.setOrientation(HORIZONTAL);
        row1.setBaselineAligned(false);
        row1.setGravity(Gravity.CENTER_VERTICAL);
        LayoutTransition transition = new LayoutTransition();
        transition.enableTransitionType(LayoutTransition.CHANGING);
        row1.setLayoutTransition(transition);
        addView(row1, new LayoutParams(dp160, WRAP_CONTENT));

        LinearLayout row2 = new LinearLayout(context);
        row2.setOrientation(HORIZONTAL);
        row2.setBaselineAligned(false);
        row2.setGravity(Gravity.TOP);
        addView(row2, new LayoutParams(dp160, WRAP_CONTENT));

        if (musicCollection instanceof Playlist playlist) {
            {
                musicTrackCountView = new TextView(context);
                musicTrackCountView.setTextSize(Theme.TEXT_SIZE_NORMAL);
                musicTrackCountView.setText(buildCountText(String.valueOf(playlist.getMusicTrackCount()), ICON_LIST_MUSIC));
                LayoutParams params1 = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT, 0);
                params1.setMargins(0, 0, dp(8), 0);
                row1.addView(musicTrackCountView, params1);
            }
            {
                playedCountView = new TextView(context);
                playedCountView.setTextSize(Theme.TEXT_SIZE_NORMAL);
                playedCountView.setText(buildCountText(String.valueOf(playlist.getPlayedCount()), ICON_AUDIO_LINES));
                row1.addView(playedCountView, new LayoutParams(WRAP_CONTENT, WRAP_CONTENT, 0));
            }
        } else if (musicCollection instanceof Album album) {
            {
                musicTrackCountView = new TextView(context);
                musicTrackCountView.setTextSize(Theme.TEXT_SIZE_NORMAL);
                musicTrackCountView.setText(buildCountText(String.valueOf(album.getMusicTrackCount()), ICON_DISC_ALBUM));
                LayoutParams params1 = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT, 0);
                params1.setMargins(0, 0, dp(8), 0);
                row1.addView(musicTrackCountView, params1);
            }
            String type = album.getType();
            if (!type.isBlank()) {
                albumTypeView = new TextView(context);
                albumTypeView.setTextSize(Theme.TEXT_SIZE_NORMAL);
                albumTypeView.setText(buildCountText(mappedAlbumType(type), ICON_LAYOUT_GRID));
                row1.addView(albumTypeView, new LayoutParams(WRAP_CONTENT, WRAP_CONTENT, 0));
            }
        }
        row1.addView(new View(context), new LayoutParams(WRAP_CONTENT, MATCH_PARENT, 1));
        InsetBackgroundFactory backgroundFactory = InsetBackgroundFactory.builder()
                .backgroundColor(Theme.GHOST_BUTTON_STATES)
                .inset(0)
                .cornerRadius(dp(4))
                .padding(new InsetBackgroundFactory.Padding(dp(2), dp(2), dp(2), dp(2)))
                .build();
        {
            ToggleSubscribeButton toggleSubscribeButton = new ToggleSubscribeButton(context);
            backgroundFactory.applyBackgroundTo(toggleSubscribeButton);
            row1.addView(toggleSubscribeButton, new LayoutParams(row2.dp(22), row2.dp(22), 0));
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
        nameView.setMaxWidth(dp(120));
        boolean isPrivatePlaylistToUser =
                musicCollection instanceof Playlist playlist && playlist.getPrivacy() == Privacy.PRIVATE
                        && !playlist.getCreator().equals(profileConfigData.getProfile());
        LayoutParams params1 = new LayoutParams(0, WRAP_CONTENT, 1);
        params1.setMargins(dp(2), 0, dp(2), 0);
        row2.addView(nameView, params1);

        PusherInfo pusherInfo = musicCollection.getPusherInfo();
        LocalPlayer localPlayer = Minecraft.getInstance().player;
        if (pusherInfo == null || pusherInfo.equals(PusherInfo.EMPTY)
                || (localPlayer != null && pusherInfo.getPlayerUUID().equals(localPlayer.getUUID()))) {
            nameView.setMinLines(2);
            ToggleIdlePlaySourceButton toggleIdleSourceButton = new ToggleIdlePlaySourceButton(context);
            backgroundFactory.applyBackgroundTo(toggleIdleSourceButton);
            toggleIdleSourceButton.bindState(musicService.getIdlePlaySourceState().local().collection(musicCollection));
            row1.addView(toggleIdleSourceButton, new LayoutParams(row2.dp(22), row2.dp(22), 0));
        } else {
            LinearLayout pusherRow = new LinearLayout(context);
            pusherRow.setOrientation(LinearLayout.HORIZONTAL);
            pusherRow.setGravity(Gravity.CENTER_VERTICAL);

            TextView pusherText = new TextView(context);
            pusherText.setTextColor(Theme.SECONDARY_TEXT_COLOR);
            pusherText.setTextSize(Theme.TEXT_SIZE_NORMAL);
            pusherText.setTextAlignment(TEXT_ALIGNMENT_TEXT_START);
            pusherText.setText(pusherInfo.getPlayerName());

            PlayerHeadView pusherHeadView = new PlayerHeadView(context);
            int rowHeight = pusherText.dp(Theme.TEXT_SIZE_LARGER);
            //noinspection SuspiciousNameCombination
            pusherHeadView.setLayoutParams(new LinearLayout.LayoutParams(rowHeight, rowHeight));
            pusherHeadView.setPlayerSkinSupplier(() -> {
                try {
                    return PlayerInfoUtil.getPlayerSkin(PlayerInfoUtil.getPlayerInfoByUUID(pusherInfo.getPlayerUUID()));
                } catch (Exception ignored) {}
                return null;
            });

            pusherRow.addView(pusherHeadView);
            LinearLayout.LayoutParams params5 = new LinearLayout.LayoutParams(WRAP_CONTENT, rowHeight);
            params5.gravity = Gravity.LEFT | Gravity.CENTER_HORIZONTAL;
            params5.setMargins(pusherText.dp(4), 0, 0, 0);
            pusherRow.addView(pusherText, params5);

            LayoutParams pusherParams = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            pusherParams.setMargins(dp(4), dp(4), 0, dp(4));
            addView(pusherRow, pusherParams);
        }

        if (!isPrivatePlaylistToUser) {
            setClickable(true);
            setFocusable(true);
            setOnClickListener(v -> {
                RouterContainer routerContainer = RouterContainer.getInstance();
                if (routerContainer != null) {
                    routerContainer.pushNavigate(
                            new MusicCollectionDetailView(context, musicCollection)
                    );
                }
            });

            InsetBackgroundFactory.builder().inset(dp(1))
                    .cornerRadius(dp(12))
                    .padding(new InsetBackgroundFactory.Padding(dp(6), dp(6), dp(6), dp(6)))
                    .build().applyBackgroundTo(this);
        } else {
            ShapeDrawable background = new ShapeDrawable();
            background.setPadding(dp(6), dp(6), dp(6), dp(6));
            setBackground(background);
        }

        refreshCollectionInfo();
    }

    private SpannableString buildCountText(String text, String iconPath) {
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
        imageView.loadUrl(collection.getImageThumbnailUrl(dp(160)));
        if (collection instanceof Playlist playlist) {
            musicTrackCountView.setText(buildCountText(String.valueOf(playlist.getMusicTrackCount()), ICON_LIST_MUSIC));
            if (playedCountView != null) {
                playedCountView.setText(buildCountText(String.valueOf(playlist.getPlayedCount()), ICON_AUDIO_LINES));
            }
        } else if (collection instanceof Album album) {
            musicTrackCountView.setText(buildCountText(String.valueOf(album.getMusicTrackCount()), ICON_DISC_ALBUM));
            if (albumTypeView != null) {
                albumTypeView.setText(buildCountText(mappedAlbumType(album.getType()), ICON_LAYOUT_GRID));
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
            if (throwable != null || latest == null) return;
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
            case "专辑" -> I18n.get(MusicHud.MOD_ID +".text.album.type.album");
            case "EP" -> I18n.get(MusicHud.MOD_ID + ".text.album.type.ep");
            case "Single" -> I18n.get(MusicHud.MOD_ID + ".text.album.type.single");
            case "精选集" -> I18n.get(MusicHud.MOD_ID + ".text.album.type.compilation");
            default -> type;
        };
    }
}
