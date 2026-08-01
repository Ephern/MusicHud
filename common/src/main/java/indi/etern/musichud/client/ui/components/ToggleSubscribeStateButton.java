package indi.etern.musichud.client.ui.components;

import icyllis.modernui.core.Context;
import icyllis.modernui.mc.MuiModApi;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.state.ISubscribeState;
import indi.etern.musichud.client.ui.utils.image.ImageUtils;
import indi.etern.musichud.interfaces.Unregister;
import net.minecraft.client.resources.language.I18n;

public class ToggleSubscribeStateButton<T> extends ToggleIconButton {
    @SuppressWarnings("FieldCanBeLocal")
    private Unregister unregister = null;
    private ISubscribeState<T> subscribeState;

    public ToggleSubscribeStateButton(Context context) {
        super(context, new Appearance(
                () -> I18n.get(MusicHud.MOD_ID + ".button.modifyCurrentMusicLike.remove"),
                () -> I18n.get(MusicHud.MOD_ID + ".button.modifyCurrentMusicLike.add"),
                () -> ImageUtils.getImageFromResource("/assets/music_hud/textures/gui/icons/heart_filled.png"),
                () -> ImageUtils.getImageFromResource("/assets/music_hud/textures/gui/icons/heart.png")
        ));
    }

    @Override
    public boolean performClick() {
        boolean b = super.performClick();
        if (subscribeState != null) {
            if (isChecked()) {
                subscribeState.subscribe();
            } else {
                subscribeState.unsubscribe();
            }
        }
        return b;
    }

    public void bindMusicList(ISubscribeState<T> subscribeState) {
        if (subscribeState == null) {
            this.subscribeState = null;
            if (unregister != null) {
                unregister.unregister();
            }
        } else {
            subscribeState.isSubscribed().thenApply((contains) -> {
                MuiModApi.postToUiThread(() -> {
                    setChecked(contains);
                    this.subscribeState = subscribeState;
                    unregister = subscribeState.onOthersModify(checked -> {
                        MuiModApi.postToUiThread(() -> {
                            setChecked(checked);
                        });
                    });
                });
                return null;
            });
        }
    }
}
