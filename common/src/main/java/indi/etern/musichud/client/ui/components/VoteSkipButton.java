package indi.etern.musichud.client.ui.components;

import icyllis.modernui.core.Context;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.services.music.MusicService;
import indi.etern.musichud.client.ui.utils.image.ImageUtils;
import indi.etern.musichud.client.utils.ClientDistUtil;
import net.minecraft.client.resources.language.I18n;

public class VoteSkipButton extends ToggleIconButton {

    public VoteSkipButton(Context context) {
        super(context, new Appearance(
                () -> ClientDistUtil.getInstance().inSinglePlayer() ? null : I18n.get(MusicHud.MOD_ID + ".text.voted"),
                () -> ClientDistUtil.getInstance().inSinglePlayer()
                        ? I18n.get(MusicHud.MOD_ID + ".button.skip")
                        : I18n.get(MusicHud.MOD_ID + ".button.voteForSkip"),
                () -> ImageUtils.getImageFromResource("/assets/music_hud/textures/gui/icons/skip_forward_filled.png"),
                () -> ImageUtils.getImageFromResource("/assets/music_hud/textures/gui/icons/skip_forward.png")
        ));

        setOnClickListener((v) -> {
            if (isChecked()) {
                MusicService.getInstance().voteForSkipCurrent();
                setEnabled(false);
            }
        });
    }

    public void reset() {
        setChecked(false);
        setEnabled(true);
    }
}
