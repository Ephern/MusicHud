package indi.etern.musichud.client.config;

import com.mojang.blaze3d.platform.InputConstants;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.services.LoginService;
import indi.etern.musichud.client.services.MusicService;
import indi.etern.musichud.client.ui.ToastUtil;
import indi.etern.musichud.client.ui.screen.MainFragment;
import indi.etern.musichud.client.ui.screen.MusicHudScreen;
import indi.etern.musichud.interfaces.ClientConfig;
import indi.etern.musichud.interfaces.ClientRegister;
import indi.etern.musichud.interfaces.IKeyRegistryService;
import indi.etern.musichud.interfaces.RegisterMark;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import org.lwjgl.glfw.GLFW;

@RegisterMark
public class Keybinds implements ClientRegister {
    private static final ClientConfig clientConfig = ClientConfig.getInstance();
    private static final LoginService loginService = LoginService.getInstance();

    public void register() {
        String category = "key.category.music_hud.music_hud";
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
            Minecraft.getInstance().setScreen(MusicHudScreen.createScreen(new MainFragment(), null, null, "Music HUD"));
        });
        service.register(voteMapping, () -> {
            MusicService.getInstance().keyBindsVoteSkipCurrent();
        });
        service.register(toggleHudMapping, () -> {
            clientConfig.setEnableHud(!clientConfig.getEnableHud());
            clientConfig.save();
        });
        service.register(toggleIsolatedMode, () -> {
            MusicHud.EXECUTOR.execute(loginService::keyBindsToggleConnection);
        });
        service.register(muteMapping, () -> {
            clientConfig.setMuted(!clientConfig.getMuted());
            clientConfig.save();
            ToastUtil.show(getVolumeToastString());
        });
        service.register(increaseVolume, () -> {
            clientConfig.forceSetSoundVolume(Math.clamp(clientConfig.getSoundVolume() + clientConfig.getSoundVolumeInterval(), 0, 100));
            clientConfig.save();
            ToastUtil.show(getVolumeToastString());
        });
        service.register(decreaseVolume, () -> {
            clientConfig.forceSetSoundVolume(Math.clamp(clientConfig.getSoundVolume() - clientConfig.getSoundVolumeInterval(), 0, 100));
            clientConfig.save();
            ToastUtil.show(getVolumeToastString());
        });
    }

    private String getVolumeToastString() {
        String emoji;
        boolean muted = clientConfig.getMuted();
        int volume = muted ? 0 : clientConfig.getSoundVolume();
        if (muted) {
            emoji = I18n.get(MusicHud.MOD_ID + ".text.volumeTemplate.emoji.level0");
        } else if (volume <= 33) {
            emoji = I18n.get(MusicHud.MOD_ID + ".text.volumeTemplate.emoji.level1");
        } else if (volume <= 67) {
            emoji = I18n.get(MusicHud.MOD_ID + ".text.volumeTemplate.emoji.level2");
        } else {
            emoji = I18n.get(MusicHud.MOD_ID + ".text.volumeTemplate.emoji.level3");
        }
        return I18n.get(MusicHud.MOD_ID + ".text.volumeTemplate").replace("{emoji}", emoji).replace("{volume}", String.valueOf(volume));
    }
}
