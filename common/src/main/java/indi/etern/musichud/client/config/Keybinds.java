package indi.etern.musichud.client.config;

import com.mojang.blaze3d.platform.InputConstants;
import icyllis.modernui.graphics.Image;
import icyllis.modernui.text.SpannableString;
import icyllis.modernui.text.Spanned;
import icyllis.modernui.text.style.ImageSpan;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.interfaces.IKeyRegistryService;
import indi.etern.musichud.client.services.ConnectionManager;
import indi.etern.musichud.client.services.music.MusicService;
import indi.etern.musichud.client.ui.ToastUtil;
import indi.etern.musichud.client.ui.screen.MainFragment;
import indi.etern.musichud.client.ui.screen.MusicHudScreen;
import indi.etern.musichud.client.utils.image.ImageUtils;
import indi.etern.musichud.interfaces.ClientConfig;
import indi.etern.musichud.interfaces.ClientRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import lombok.SneakyThrows;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

@RegisterMark
public class Keybinds implements ClientRegister {
    private static final ClientConfig clientConfig = ClientConfig.getInstance();
    private static final ConnectionManager connectionManager = ConnectionManager.getInstance();

    public void register() {
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MusicHud.MOD_ID, MusicHud.MOD_ID));
        var mainMapping = new KeyMapping(
                MusicHud.MOD_ID + ".open_main",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_M,
                category
        );
        var voteMapping = new KeyMapping(
                MusicHud.MOD_ID + ".vote_skip",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_PERIOD,
                category
        );
        var toggleHudMapping = new KeyMapping(
                MusicHud.MOD_ID + ".toggle_hud",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_COMMA,
                category
        );
        var toggleIsolatedMode = new KeyMapping(
                MusicHud.MOD_ID + ".toggle_connection",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                category
        );
        var muteMapping = new KeyMapping(
                MusicHud.MOD_ID + ".mute",
                InputConstants.Type.KEYSYM,
                -1,//No defaults as "InputConstants.UNKNOWN"
                category
        );
        var increaseVolume = new KeyMapping(
                MusicHud.MOD_ID + ".increase_volume",
                InputConstants.Type.KEYSYM,
                -1,//No defaults as "InputConstants.UNKNOWN"
                category
        );
        var decreaseVolume = new KeyMapping(
                MusicHud.MOD_ID + ".decrease_volume",
                InputConstants.Type.KEYSYM,
                -1,//No defaults as "InputConstants.UNKNOWN"
                category
        );
        IKeyRegistryService service = IKeyRegistryService.getInstance();
        service.register(mainMapping, () -> {
            Minecraft.getInstance().gui.setScreen(MusicHudScreen.createScreen(MainFragment.getInstance(), null, null, "Music HUD"));
        });
        service.register(voteMapping, () -> {
            MusicHud.EXECUTOR.execute(() -> {
                MusicService.getInstance().keyBindsVoteSkipCurrent();
            });
        });
        service.register(toggleHudMapping, () -> {
            MusicHud.EXECUTOR.execute(() -> {
                clientConfig.setEnableHud(!clientConfig.getEnableHud());
                clientConfig.save();
            });
        });
        service.register(toggleIsolatedMode, () -> {
            MusicHud.EXECUTOR.execute(connectionManager::keyBindsToggleConnection);
        });
        service.register(muteMapping, () -> {
            MusicHud.EXECUTOR.execute(() -> {
                clientConfig.setMuted(!clientConfig.getMuted());
                clientConfig.save();
                ToastUtil.show(getVolumeToastString());
            });
        });
        service.register(increaseVolume, () -> {
            MusicHud.EXECUTOR.execute(() -> {
                clientConfig.forceSetSoundVolume(Math.clamp(clientConfig.getSoundVolume() + clientConfig.getSoundVolumeInterval(), 0, 100));
                clientConfig.save();
                ToastUtil.show(getVolumeToastString());
            });
        });
        service.register(decreaseVolume, () -> {
            MusicHud.EXECUTOR.execute(() -> {
                clientConfig.forceSetSoundVolume(Math.clamp(clientConfig.getSoundVolume() - clientConfig.getSoundVolumeInterval(), 0, 100));
                clientConfig.save();
                ToastUtil.show(getVolumeToastString());
            });
        });
    }

    @SneakyThrows
    private CharSequence getVolumeToastString() {
        String emoji;
        boolean muted = clientConfig.getMuted();
        int volume = muted ? 0 : clientConfig.getSoundVolume();
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
        String template = I18n.get(MusicHud.MOD_ID + ".text.volumeTemplate").replace("{emoji}", emoji).replace("{volume}", String.valueOf(volume));
        SpannableString message = new SpannableString(template);
        Image image = ImageUtils.getImageFromResource(resourceName);
        if (image != null) {
            ImageSpan span = ImageUtils.getIconSpan(image);
            message.setSpan(span, 0, emoji.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return message;
    }
}