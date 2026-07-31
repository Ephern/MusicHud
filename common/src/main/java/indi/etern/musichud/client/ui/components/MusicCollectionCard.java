package indi.etern.musichud.client.ui.components;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Image;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.text.SpannableString;
import icyllis.modernui.text.Spanned;
import icyllis.modernui.text.style.ImageSpan;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import icyllis.modernui.widget.Toast;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.*;
import indi.etern.musichud.beans.user.ProfileConfigData;
import indi.etern.musichud.client.services.music.MusicService;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.ToastUtil;
import indi.etern.musichud.client.ui.utils.PlayerInfoUtil;
import indi.etern.musichud.client.ui.utils.image.ImageUtils;
import indi.etern.musichud.client.ui.utils.ui.ButtonInsetBackgroundFactory;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;

import java.util.function.Consumer;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class MusicCollectionCard extends LinearLayout {
    private final MusicService musicService = MusicService.getInstance();
    @Getter
    MusicCollection musicCollection;

    public MusicCollectionCard(Context context, MusicCollection musicCollection) {//TODO
        super(context);
        this.musicCollection = musicCollection;

        setOrientation(VERTICAL);

        LayoutParams params = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        setLayoutParams(params);

        UrlImageView imageView = new UrlImageView(context);
        int dp160 = dp(160);
        LayoutParams imageParams = new LinearLayout.LayoutParams(dp160, dp160, 1);
        imageParams.setMargins(0, 0, 0, dp(4));
        imageView.setAspectRatio(1);
        addView(imageView, imageParams);

        imageView.loadUrl(musicCollection.getImageThumbnailUrl(dp160));
        imageView.setCornerRadius(dp(8));

        LinearLayout row1 = new LinearLayout(context);
        row1.setOrientation(HORIZONTAL);
        row1.setBaselineAligned(false);
        row1.setGravity(Gravity.CENTER_VERTICAL);
        addView(row1);

        LinearLayout row2 = new LinearLayout(context);
        row2.setOrientation(HORIZONTAL);
        row2.setBaselineAligned(false);
        row2.setGravity(Gravity.TOP);
        addView(row2);

        if (musicCollection instanceof Playlist playlist) {
            {
                TextView musicTrackCountView = new TextView(context);
                musicTrackCountView.setTextSize(Theme.TEXT_SIZE_NORMAL);
                SpannableString text = new SpannableString("  " + playlist.getMusicTrackCount());
                Image icon = ImageUtils.getImageFromResource("/assets/music_hud/textures/gui/icons/list_music.png");
                if (icon != null) {
                    ImageSpan iconSpan = ImageUtils.getIconSpan(icon);
                    text.setSpan(iconSpan, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                musicTrackCountView.setText(text);
                LayoutParams params1 = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT, 0);
                params1.setMargins(0, 0, dp(8), 0);
                row1.addView(musicTrackCountView, params1);
            }
            {
                TextView playedCountView = new TextView(context);
                playedCountView.setTextSize(Theme.TEXT_SIZE_NORMAL);
                SpannableString text = new SpannableString("  " + playlist.getPlayedCount());
                Image icon = ImageUtils.getImageFromResource("/assets/music_hud/textures/gui/icons/audio_lines.png");
                if (icon != null) {
                    ImageSpan iconSpan = ImageUtils.getIconSpan(icon);
                    text.setSpan(iconSpan, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                playedCountView.setText(text);
                row1.addView(playedCountView, new LayoutParams(WRAP_CONTENT, WRAP_CONTENT, 0));
            }
        } else if (musicCollection instanceof Album album) {
            {
                TextView musicTrackCountView = new TextView(context);
                musicTrackCountView.setTextSize(Theme.TEXT_SIZE_NORMAL);
                SpannableString text = new SpannableString("  " + album.getMusicTrackCount());
                Image icon = ImageUtils.getImageFromResource("/assets/music_hud/textures/gui/icons/disc_album.png");
                if (icon != null) {
                    ImageSpan iconSpan = ImageUtils.getIconSpan(icon);
                    text.setSpan(iconSpan, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                musicTrackCountView.setText(text);
                LayoutParams params1 = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT, 0);
                params1.setMargins(0, 0, dp(8), 0);
                row1.addView(musicTrackCountView, params1);
            }
            String type = album.getType();
            if (!type.isBlank()) {
                TextView albumTypeText = new TextView(context);
                albumTypeText.setTextSize(Theme.TEXT_SIZE_NORMAL);
                SpannableString text = new SpannableString("  " + mappedAlbumType(type));
                Image icon = ImageUtils.getImageFromResource("/assets/music_hud/textures/gui/icons/layout_grid.png");
                if (icon != null) {
                    ImageSpan iconSpan = ImageUtils.getIconSpan(icon);
                    text.setSpan(iconSpan, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                albumTypeText.setText(text);
                row1.addView(albumTypeText, new LayoutParams(WRAP_CONTENT, WRAP_CONTENT, 0));
            }
        }
        row1.addView(new View(context), new LayoutParams(WRAP_CONTENT, MATCH_PARENT, 1));
        ButtonInsetBackgroundFactory backgroundFactory = ButtonInsetBackgroundFactory.builder()
                .inset(0)
                .cornerRadius(dp(4))
                .padding(new ButtonInsetBackgroundFactory.Padding(dp(2), dp(2), dp(2), dp(2)))
                .build();
        {
            Button toggleSubscribeButton = new Button(context);
            updateButton(toggleSubscribeButton);
            toggleSubscribeButton.setTextColor(Theme.SECONDARY_TEXT_COLOR);
            toggleSubscribeButton.setTextSize(Theme.TEXT_SIZE_NORMAL);
            SpannableString text = new SpannableString(" ");
            Image image = ImageUtils.getImageFromResource("/assets/music_hud/textures/gui/icons/heart.png");
            if (image != null) {
                ImageSpan span = ImageUtils.getIconSpan(image);
                text.setSpan(span, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            toggleSubscribeButton.setText(text);
//            toggleSubscribeButton.setTooltipText(I18n.get(MusicHud.MOD_ID + ".button.removeFromIdlePlaySource"));
            toggleSubscribeButton.setBackground(backgroundFactory.newBackgroundDrawable());
            toggleSubscribeButton.setOnClickListener((v) -> {//TODO
//                if (musicService.getLocalIdlePlaySources().stream().anyMatch(collection -> collection.getId() == musicCollection.getId())) {
//                    ToastUtil.show(Toast.makeText(context, I18n.get(MusicHud.MOD_ID + ".text.removedFromIdlePlaySource") + "\n" + musicCollection.getName(), Toast.LENGTH_SHORT));
//                    musicService.removeFromIdlePlaySource(musicCollection);
//                } else {
//                    ToastUtil.show(Toast.makeText(context, I18n.get(MusicHud.MOD_ID + ".text.addedToIdlePlaySource") + "\n" + musicCollection.getName(), Toast.LENGTH_SHORT));
//                    musicService.addToIdlePlaySource(musicCollection);
//                }
            });
            row1.addView(toggleSubscribeButton, new LayoutParams(row2.dp(22), row2.dp(22), 0));
        }

        TextView name = new TextView(context);
        name.setTextSize(Theme.TEXT_SIZE_NORMAL);
        name.setTextColor(Theme.NORMAL_TEXT_COLOR);
        name.setSingleLine(false);
        name.setMaxLines(4);
        name.setMinLines(2);
        name.setMaxWidth(dp(120));
        boolean isPrivatePlaylistToUser =
                musicCollection instanceof Playlist playlist && playlist.getPrivacy() == Privacy.PRIVATE
                        && !playlist.getCreator().equals(ProfileConfigData.getInstance().getProfile());
        if (isPrivatePlaylistToUser) {
            name.setText(I18n.get(MusicHud.MOD_ID + ".text.privatePlaylist"));
        } else {
            name.setText(musicCollection.getName());
        }
        LayoutParams params1 = new LayoutParams(0, WRAP_CONTENT, 1);
        params1.setMargins(dp(2), 0, dp(2), 0);
        row2.addView(name, params1);

        PusherInfo pusherInfo = musicCollection.getPusherInfo();
        LocalPlayer localPlayer = Minecraft.getInstance().player;
        if (pusherInfo == null || pusherInfo.equals(PusherInfo.EMPTY)
                || (localPlayer != null && pusherInfo.getPlayerUUID().equals(localPlayer.getUUID()))) {
            Button toggleIdleSourceButton = new Button(context);
            updateButton(toggleIdleSourceButton);
            toggleIdleSourceButton.setTextColor(Theme.SECONDARY_TEXT_COLOR);
            toggleIdleSourceButton.setTextSize(Theme.TEXT_SIZE_NORMAL);
            toggleIdleSourceButton.setBackground(backgroundFactory.newBackgroundDrawable());
            toggleIdleSourceButton.setOnClickListener((v) -> {
                if (musicService.getLocalIdlePlaySources().stream().anyMatch(collection -> collection.getId() == musicCollection.getId())) {
                    ToastUtil.show(Toast.makeText(context, I18n.get(MusicHud.MOD_ID + ".text.removedFromIdlePlaySource") + "\n" + musicCollection.getName(), Toast.LENGTH_SHORT));
                    musicService.removeFromIdlePlaySource(musicCollection);
                } else {
                    ToastUtil.show(Toast.makeText(context, I18n.get(MusicHud.MOD_ID + ".text.addedToIdlePlaySource") + "\n" + musicCollection.getName(), Toast.LENGTH_SHORT));
                    musicService.addToIdlePlaySource(musicCollection);
                }
            });
            row1.addView(toggleIdleSourceButton, new LayoutParams(row2.dp(22), row2.dp(22), 0));

            Consumer<MusicCollection> listener = collection -> {
                if (collection.equals(musicCollection)) {
                    MuiModApi.postToUiThread(() -> updateButton(toggleIdleSourceButton));
                }
            };

            musicService.getLocalIdlePlaySourceChangeListeners().add(listener);
            addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                    updateButton(toggleIdleSourceButton);
                    musicService.getLocalIdlePlaySourceChangeListeners().add(listener);
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                    musicService.getLocalIdlePlaySourceChangeListeners().remove(listener);
                }
            });
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

            ButtonInsetBackgroundFactory background = ButtonInsetBackgroundFactory.builder().inset(dp(1))
                    .cornerRadius(dp(12))
                    .padding(new ButtonInsetBackgroundFactory.Padding(dp(6), dp(6), dp(6), dp(6)))
                    .build();
            setBackground(background.newBackgroundDrawable());
        } else {
            ShapeDrawable background = new ShapeDrawable();
            background.setPadding(dp(6), dp(6), dp(6), dp(6));
            setBackground(background);
        }
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

    private void updateButton(Button modifyIdleSourceButton) {
        if (musicService.getLocalIdlePlaySources().stream().anyMatch(collection -> collection.getId() == musicCollection.getId())) {
            SpannableString text = new SpannableString("★");
            Image image = ImageUtils.getImageFromResource("/assets/music_hud/textures/gui/icons/star_filled.png");
            if (image != null) {
                ImageSpan span = ImageUtils.getIconSpan(image);
                text.setSpan(span, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            modifyIdleSourceButton.setText(text);
            modifyIdleSourceButton.setTooltipText(I18n.get(MusicHud.MOD_ID + ".button.removeFromIdlePlaySource"));
        } else {
            SpannableString text = new SpannableString("☆");
            Image image = ImageUtils.getImageFromResource("/assets/music_hud/textures/gui/icons/star.png");
            if (image != null) {
                ImageSpan span = ImageUtils.getIconSpan(image);
                text.setSpan(span, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            modifyIdleSourceButton.setText(text);
            modifyIdleSourceButton.setTooltipText(I18n.get(MusicHud.MOD_ID + ".button.addToIdlePlaySource"));
        }
    }
}
