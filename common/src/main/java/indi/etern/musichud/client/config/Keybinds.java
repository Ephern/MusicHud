package indi.etern.musichud.client.config;

import com.mojang.blaze3d.platform.InputConstants;
import icyllis.modernui.mc.MuiModApi;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.services.LoginService;
import indi.etern.musichud.client.services.MusicService;
import indi.etern.musichud.client.ui.screen.MainFragment;
import indi.etern.musichud.interfaces.ClientConfig;
import indi.etern.musichud.interfaces.ClientRegister;
import indi.etern.musichud.interfaces.IKeyRegistryService;
import indi.etern.musichud.interfaces.RegisterMark;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

@RegisterMark
public class Keybinds implements ClientRegister {
    private static final ClientConfig clientConfig = ClientConfig.getInstance();
    private static final LoginService loginService = LoginService.getInstance();

    public void register() {
        KeyMapping.Category category = KeyMapping.Category.register(MusicHud.location(MusicHud.MOD_ID));
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
        IKeyRegistryService service = IKeyRegistryService.getInstance();
        service.register(mainMapping, () -> {
            Minecraft.getInstance().setScreen(MuiModApi.get().createScreen(new MainFragment()));
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
    }
}
