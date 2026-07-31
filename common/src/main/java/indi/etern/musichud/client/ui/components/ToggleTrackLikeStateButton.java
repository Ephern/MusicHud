package indi.etern.musichud.client.ui.components;

import icyllis.modernui.core.Context;
import icyllis.modernui.mc.MuiModApi;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.state.IMusicTrackState;
import indi.etern.musichud.client.ui.utils.image.ImageUtils;
import indi.etern.musichud.interfaces.Unregister;
import net.minecraft.client.resources.language.I18n;

public class ToggleTrackLikeStateButton extends ToggleIconButton {
    @SuppressWarnings("FieldCanBeLocal")
    private Unregister unregister = null;

    public ToggleTrackLikeStateButton(Context context) {
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
        if (playlistSubState != null) {
            if (isChecked()) {
                playlistSubState.add();
            } else {
                playlistSubState.remove();
            }
        }
        return b;
    }

    public void bindMusicList(IMusicTrackState.IPlaylistSubState playlistSubState) {
        if (playlistSubState == null) {
            this.playlistSubState = null;
            if (unregister != null) {
                unregister.unregister();
            }
        } else {
            playlistSubState.isContained().thenApply((contains) -> {
                MuiModApi.postToUiThread(() -> {
                    setChecked(contains);
                    this.playlistSubState = playlistSubState;
                    unregister = playlistSubState.onExternalModify(checked -> {
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
