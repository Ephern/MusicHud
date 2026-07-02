package indi.etern.musichud.client.ui.components;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.Drawable;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import icyllis.modernui.widget.Toast;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.MusicCollection;
import indi.etern.musichud.beans.music.Playlist;
import indi.etern.musichud.beans.music.Privacy;
import indi.etern.musichud.beans.music.PusherInfo;
import indi.etern.musichud.client.config.ProfileConfigData;
import indi.etern.musichud.client.services.MusicService;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.ToastUtil;
import indi.etern.musichud.client.ui.utils.ButtonInsetBackgroundFactory;
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

    public MusicCollectionCard(Context context, MusicCollection musicCollection) {//FIXME Button state & updating
        super(context);
        this.musicCollection = musicCollection;

        setOrientation(VERTICAL);

        LayoutParams params = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        setLayoutParams(params);

        UrlImageView imageView = new UrlImageView(context);
        LayoutParams imageParams = new LinearLayout.LayoutParams(dp(128), dp(128), 1);
        imageParams.setMargins(0, 0, 0, dp(4));
        imageView.setAspectRatio(1);
        addView(imageView, imageParams);

        imageView.loadUrl(musicCollection.getImageThumbnailUrl(dp(128)));
        imageView.setCornerRadius(dp(8));

        TextView name = new TextView(context);
        name.setTextSize(Theme.TEXT_SIZE_NORMAL);
        name.setTextColor(Theme.NORMAL_TEXT_COLOR);
        boolean isPrivatePlaylistToUser =
                musicCollection instanceof Playlist playlist && playlist.getPrivacy() == Privacy.PRIVATE
                        && !playlist.getCreator().equals(ProfileConfigData.getInstance().getProfile());
        if (isPrivatePlaylistToUser) {
            name.setText(I18n.get(MusicHud.MOD_ID + ".text.privatePlaylist"));
        } else {
            name.setText(musicCollection.getName());
        }
        LayoutParams params1 = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        params1.setMargins(dp(4), 0, 0, 0);
        addView(name, params1);

        PusherInfo pusherInfo = musicCollection.getPusherInfo();
        LocalPlayer localPlayer = Minecraft.getInstance().player;
        if (pusherInfo == null || pusherInfo.equals(PusherInfo.EMPTY)
                || (localPlayer != null && pusherInfo.getPlayerUUID().equals(localPlayer.getUUID()))) {
            Button addToIdleSourceButton = new Button(context);
            updateButton(addToIdleSourceButton);
            addToIdleSourceButton.setTextColor(Theme.SECONDARY_TEXT_COLOR);
            addToIdleSourceButton.setTextSize(Theme.TEXT_SIZE_SMALL);
            Drawable background1 = ButtonInsetBackgroundFactory.builder()
                    .inset(0)
                    .cornerRadius(dp(8))
                    .padding(new ButtonInsetBackgroundFactory.Padding(dp(4), dp(8), dp(4), dp(8)))
                    .build().newBackgroundDrawable();
            addToIdleSourceButton.setBackground(background1);
            addToIdleSourceButton.setOnClickListener((v) -> {
                if (musicService.getLocalIdlePlaySources().stream().anyMatch(collection -> collection.getId() == musicCollection.getId())) {
                    ToastUtil.show(Toast.makeText(context, I18n.get(MusicHud.MOD_ID + ".text.removedFromIdlePlaySource") + "\n" + musicCollection.getName(), Toast.LENGTH_SHORT));
                    musicService.removeFromIdlePlaySource(musicCollection);
                } else {
                    ToastUtil.show(Toast.makeText(context, I18n.get(MusicHud.MOD_ID + ".text.addedToIdlePlaySource") + "\n" + musicCollection.getName(), Toast.LENGTH_SHORT));
                    musicService.addToIdlePlaySource(musicCollection);
                }
            });
            addView(addToIdleSourceButton, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));

            Consumer<MusicCollection> listener = collection -> {
                if (collection.equals(musicCollection)) {
                    MuiModApi.postToUiThread(() -> updateButton(addToIdleSourceButton));
                }
            };

            addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                    musicService.getLocalIdlePlaySourceChangeListeners().add(listener);
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                    musicService.getLocalIdlePlaySourceChangeListeners().remove(listener);
                }
            });
        } else {
            TextView pusherText = new TextView(context);
            pusherText.setTextColor(Theme.SECONDARY_TEXT_COLOR);
            pusherText.setTextSize(Theme.TEXT_SIZE_SMALL);
            pusherText.setTextAlignment(TEXT_ALIGNMENT_TEXT_START);
            pusherText.setText(I18n.get(MusicHud.MOD_ID + ".text.pusherSource") + pusherInfo.getPlayerName());
            LayoutParams pusherParams = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            pusherParams.setMargins(dp(4), 0, 0, dp(8));
            addView(pusherText, pusherParams);
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

    private void updateButton(Button addToIdleSourceButton) {
        if (musicService.getLocalIdlePlaySources().stream().anyMatch(collection -> collection.getId() == musicCollection.getId())) {
            addToIdleSourceButton.setText(I18n.get(MusicHud.MOD_ID + ".button.removeFromIdlePlaySource"));
        } else {
            addToIdleSourceButton.setText(I18n.get(MusicHud.MOD_ID + ".button.addToIdlePlaySource"));
        }
    }
}
