package indi.etern.musichud.client.ui.components;

import icyllis.modernui.core.Context;
import icyllis.modernui.mc.MuiModApi;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.state.IIdlePlaySourceCollectionState;
import indi.etern.musichud.client.ui.utils.image.ImageUtils;
import indi.etern.musichud.interfaces.Unregister;
import net.minecraft.client.resources.language.I18n;

public class ToggleIdlePlaySourceButton extends ToggleIconButton {
    private IIdlePlaySourceCollectionState collectionState;
    private Unregister unregister = null;

    public ToggleIdlePlaySourceButton(Context context) {
        super(context, new Appearance(
                () -> I18n.get(MusicHud.MOD_ID + ".button.removeFromIdlePlaySource"),
                () -> I18n.get(MusicHud.MOD_ID + ".button.addToIdlePlaySource"),
                () -> ImageUtils.getImageFromResource("/assets/music_hud/textures/gui/icons/star_filled.png"),
                () -> ImageUtils.getImageFromResource("/assets/music_hud/textures/gui/icons/star.png")
        ));
    }

    @Override
    public boolean performClick() {
        boolean b = super.performClick();
        if (collectionState != null) {
            if (isChecked()) {
                collectionState.add();
            } else {
                collectionState.remove();
            }
        }
        return b;
    }

    public void bindMusicList(IIdlePlaySourceCollectionState collectionState) {
        if (collectionState == null) {
            this.collectionState = null;
            if (unregister != null) {
                unregister.unregister();
                unregister = null;
            }
        } else {
            MuiModApi.postToUiThread(() -> {
                setChecked(collectionState.isContained());
                this.collectionState = collectionState;
                unregister = collectionState.onOthersModify(checked ->
                        MuiModApi.postToUiThread(() -> setChecked(checked)));
            });
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (unregister != null) {
            unregister.unregister();
            unregister = null;
        }
    }
}
