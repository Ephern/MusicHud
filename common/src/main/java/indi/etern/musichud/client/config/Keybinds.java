package indi.etern.musichud.client.config;

import com.mojang.blaze3d.platform.InputConstants;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.interfaces.IKeyRegistryService;
import indi.etern.musichud.client.services.ConnectionManager;
import indi.etern.musichud.client.services.music.MusicService;
import indi.etern.musichud.client.ui.ClientScreens;
import indi.etern.musichud.client.ui.ModernUiSupport;
import indi.etern.musichud.client.ui.VolumeFeedback;
import indi.etern.musichud.client.ui.screen.ModernUiRequiredScreen;
import indi.etern.musichud.interfaces.ClientConfig;
import indi.etern.musichud.interfaces.ClientRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

@RegisterMark
public class Keybinds implements ClientRegister {
    private static final ClientConfig clientConfig = ClientConfig.getInstance();
    private static final ConnectionManager connectionManager = ConnectionManager.getInstance();

    public void register() {
        KeyMapping.Category category = KeyMapping.Category.register(ResourceLocation.fromNamespaceAndPath(MusicHud.MOD_ID, MusicHud.MOD_ID));
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
            if (ModernUiSupport.available()) {
                ClientScreens.openMain();
            } else {
                Minecraft.getInstance().setScreen(new ModernUiRequiredScreen(Minecraft.getInstance().screen));
            }
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
                notifyVolumeChanged();
            });
        });
        service.register(increaseVolume, () -> {
            MusicHud.EXECUTOR.execute(() -> {
                clientConfig.forceSetSoundVolume(Math.clamp(clientConfig.getSoundVolume() + clientConfig.getSoundVolumeInterval(), 0, 100));
                clientConfig.save();
                notifyVolumeChanged();
            });
        });
        service.register(decreaseVolume, () -> {
            MusicHud.EXECUTOR.execute(() -> {
                clientConfig.forceSetSoundVolume(Math.clamp(clientConfig.getSoundVolume() - clientConfig.getSoundVolumeInterval(), 0, 100));
                clientConfig.save();
                notifyVolumeChanged();
            });
        });
    }

    private static void notifyVolumeChanged() {
        if (ModernUiSupport.available()) {
            VolumeFeedback.onVolumeChanged();
        }
    }
}
