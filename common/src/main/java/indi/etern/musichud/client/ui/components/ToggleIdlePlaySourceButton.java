package indi.etern.musichud.client.ui.components;

import icyllis.modernui.core.Context;
import icyllis.modernui.mc.MuiModApi;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.state.IIdlePlaySourceCollectionState;
import indi.etern.musichud.client.utils.image.ImageUtils;
import indi.etern.musichud.interfaces.Unregister;
import net.minecraft.client.resources.language.I18n;

import java.util.concurrent.atomic.AtomicInteger;

public class ToggleIdlePlaySourceButton extends ToggleIconButton {
    private static final long TOGGLE_DEBOUNCE_DELAY_MILLIS = 800;
    private final AtomicInteger toggleVersion = new AtomicInteger(0);
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
        boolean initialChecked = isChecked();
        boolean b = super.performClick();
        if (collectionState != null) {
            final boolean targetState = isChecked();
            final int version = toggleVersion.incrementAndGet();
            MusicHud.EXECUTOR.execute(() -> {
                try {
                    Thread.sleep(TOGGLE_DEBOUNCE_DELAY_MILLIS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (toggleVersion.get() != version) return;
                if (targetState == initialChecked) return;
                if (targetState) {
                    collectionState.add();
                } else {
                    collectionState.remove();
                }
            });
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
