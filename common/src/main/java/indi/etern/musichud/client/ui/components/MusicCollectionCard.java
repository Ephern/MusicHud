package indi.etern.musichud.client.ui.components;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.Drawable;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.Button;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.TextView;
import icyllis.modernui.widget.Toast;
import indi.etern.musichud.beans.music.MusicCollection;
import indi.etern.musichud.client.services.MusicService;
import indi.etern.musichud.client.ui.Theme;
import indi.etern.musichud.client.ui.utils.ButtonInsetBackground;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.client.resources.language.I18n;

import java.util.function.Consumer;

@Slf4j
public class MusicCollectionCard extends LinearLayout {
    private final MusicService musicService = MusicService.getInstance();
    private final Button addToWaitingListButton;
    @Getter
    MusicCollection musicCollection;

    public MusicCollectionCard(Context context, MusicCollection musicCollection) {
        super(context);
        this.musicCollection = musicCollection;

        setOrientation(VERTICAL);

        LayoutParams params = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        setLayoutParams(params);

        UrlImageView imageView = new UrlImageView(context);
        LayoutParams imageParams = new LayoutParams(dp(128), dp(128));
        imageParams.setMargins(0, 0, 0, dp(4));
        addView(imageView, imageParams);
        imageView.loadUrl(musicCollection.getImageThumbnailUrl(dp(128)));
        imageView.setCornerRadius(dp(8));

        TextView name = new TextView(context);
        name.setTextSize(Theme.TEXT_SIZE_NORMAL);
        name.setTextColor(Theme.NORMAL_TEXT_COLOR);
        name.setText(musicCollection.getName());
        LayoutParams params1 = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params1.setMargins(dp(4), 0, 0, 0);
        addView(name, params1);

        addToWaitingListButton = new Button(context);
        updateButton();
        addToWaitingListButton.setTextColor(Theme.SECONDARY_TEXT_COLOR);
        addToWaitingListButton.setTextSize(Theme.TEXT_SIZE_SMALL);
        Drawable background1 = ButtonInsetBackground.builder()
                .inset(0)
                .cornerRadius(dp(8))
                .padding(new ButtonInsetBackground.Padding(dp(4), dp(8), dp(4), dp(8)))
                .build().get();
        addToWaitingListButton.setBackground(background1);
        addToWaitingListButton.setOnClickListener((v) -> {
            if (musicService.getIdlePlaySources().contains(musicCollection)) {
                Toast.makeText(context, I18n.get("music_hud.text.removedFromIdlePlaySource") + "\n" + musicCollection.getName(), Toast.LENGTH_SHORT).show();
                musicService.removeFromIdlePlaySource(musicCollection);
            } else {
                Toast.makeText(context, I18n.get("music_hud.text.addedToIdlePlaySource") + "\n" + musicCollection.getName(), Toast.LENGTH_SHORT).show();
                musicService.addToIdlePlaySource(musicCollection);
            }
        });
        addView(addToWaitingListButton, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

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
            addToWaitingListButton.setText(I18n.get("music_hud.button.removeFromIdlePlaySource"));
        } else {
            addToWaitingListButton.setText(I18n.get("music_hud.button.addToIdlePlaySource"));
        }
    }
}
