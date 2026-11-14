package indi.etern.musichud.client.ui;

import icyllis.modernui.graphics.Image;
import icyllis.modernui.text.SpannableString;
import icyllis.modernui.text.Spanned;
import icyllis.modernui.text.style.ImageSpan;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.ui.screen.MainFragment;
import indi.etern.musichud.client.utils.image.ImageUtils;
import indi.etern.musichud.interfaces.ClientConfig;
import net.minecraft.client.resources.language.I18n;

/** ModernUI feedback for volume key bindings: cover scale refresh and the volume toast. */
public final class VolumeFeedback {
    private static final ClientConfig clientConfig = ClientConfig.getInstance();

    private VolumeFeedback() {
    }

    public static void onVolumeChanged() {
        MainFragment.refreshCoverScale();
        ToastUtil.show(buildMessage());
    }

    private static CharSequence buildMessage() {
        boolean muted = clientConfig.getMuted();
        int volume = muted ? 0 : clientConfig.getSoundVolume();
        String emoji;
        String resourceName;
        if (muted) {
            emoji = I18n.get(MusicHud.MOD_ID + ".text.volumeTemplate.emoji.level0");
            resourceName = "/assets/music_hud/textures/gui/icons/volume_x.png";
        } else if (volume <= 33) {
            emoji = I18n.get(MusicHud.MOD_ID + ".text.volumeTemplate.emoji.level1");
            resourceName = "/assets/music_hud/textures/gui/icons/volume_0.png";
        } else if (volume <= 67) {
            emoji = I18n.get(MusicHud.MOD_ID + ".text.volumeTemplate.emoji.level2");
            resourceName = "/assets/music_hud/textures/gui/icons/volume_1.png";
        } else {
            emoji = I18n.get(MusicHud.MOD_ID + ".text.volumeTemplate.emoji.level3");
            resourceName = "/assets/music_hud/textures/gui/icons/volume_2.png";
        }
        String template = I18n.get(MusicHud.MOD_ID + ".text.volumeTemplate")
                .replace("{emoji}", emoji)
                .replace("{volume}", String.valueOf(volume));
        SpannableString message = new SpannableString(template);
        Image image = ImageUtils.getImageFromResource(resourceName);
        if (image != null) {
            ImageSpan span = ImageUtils.getIconSpan(image);
            message.setSpan(span, 0, emoji.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return message;
    }
}
