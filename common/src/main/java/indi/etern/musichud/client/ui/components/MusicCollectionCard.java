package indi.etern.musichud.client.ui.components;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.Drawable;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import icyllis.modernui.widget.Toast;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.MusicCollection;
import indi.etern.musichud.client.services.MusicService;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.ToastUtil;
import indi.etern.musichud.client.ui.utils.ButtonInsetBackground;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.client.resources.language.I18n;

import java.util.function.Consumer;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

@Slf4j
public class MusicCollectionCard extends LinearLayout {
    private final MusicService musicService = MusicService.getInstance();
    private final Button addToIdleSourceButton;
    @Getter
    MusicCollection musicCollection;

    public MusicCollectionCard(Context context, MusicCollection musicCollection) {
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
        name.setText(musicCollection.getName());
        LayoutParams params1 = new LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        params1.setMargins(dp(4), 0, 0, 0);
        addView(name, params1);

        addToIdleSourceButton = new Button(context);
        updateButton();
        addToIdleSourceButton.setTextColor(Theme.SECONDARY_TEXT_COLOR);
        addToIdleSourceButton.setTextSize(Theme.TEXT_SIZE_SMALL);
        Drawable background1 = ButtonInsetBackground.builder()
                .inset(0)
                .cornerRadius(dp(8))
                .padding(new ButtonInsetBackground.Padding(dp(4), dp(8), dp(4), dp(8)))
                .build().get();
        addToIdleSourceButton.setBackground(background1);
        addToIdleSourceButton.setOnClickListener((v) -> {
            if (musicService.getIdlePlaySources().contains(musicCollection)) {
                ToastUtil.show(Toast.makeText(context, I18n.get(MusicHud.MOD_ID + ".text.removedFromIdlePlaySource") + "\n" + musicCollection.getName(), Toast.LENGTH_SHORT));
                musicService.removeFromIdlePlaySource(musicCollection);
            } else {
                ToastUtil.show(Toast.makeText(context, I18n.get("music_hud.text.addedToIdlePlaySource") + "\n" + musicCollection.getName(), Toast.LENGTH_SHORT));
                musicService.addToIdlePlaySource(musicCollection);
            }
        });
        addView(addToIdleSourceButton, new LayoutParams(MATCH_PARENT, WRAP_CONTENT));

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

        ButtonInsetBackground background = ButtonInsetBackground.builder().inset(dp(1))
                .cornerRadius(dp(12))
                .padding(new ButtonInsetBackground.Padding(dp(6), dp(6), dp(6), dp(6)))
                .build();
        setBackground(background.get());

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

    private void updateButton() {
        if (musicService.getIdlePlaySources().contains(musicCollection)) {
            addToIdleSourceButton.setText(I18n.get("music_hud.button.removeFromIdlePlaySource"));
        } else {
            addToIdleSourceButton.setText(I18n.get("music_hud.button.addToIdlePlaySource"));
        }
    }
}
