package indi.etern.musichud.client.config;

import com.mojang.blaze3d.platform.InputConstants;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.registry.client.keymappings.KeyMappingRegistry;
import icyllis.modernui.mc.MuiModApi;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.services.MusicService;
import indi.etern.musichud.client.ui.screen.MainFragment;
import indi.etern.musichud.interfaces.ClientRegister;
import indi.etern.musichud.interfaces.RegisterMark;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.lwjgl.glfw.GLFW;

@RegisterMark
public class Keybinds implements ClientRegister {
    public void register() {
        var mainMapping = new KeyMapping(
                MusicHud.MOD_ID + ".open_main",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_M,
                KeyMapping.Category.register(ResourceLocation.fromNamespaceAndPath(MusicHud.MOD_ID,MusicHud.MOD_ID))
        );
        var voteMapping = new KeyMapping(
                MusicHud.MOD_ID + ".vote_skip",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_PERIOD,
                KeyMapping.Category.register(ResourceLocation.fromNamespaceAndPath(MusicHud.MOD_ID,MusicHud.MOD_ID))
        );
        var toggleHudMapping = new KeyMapping(
                MusicHud.MOD_ID + ".toggle_hud",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_COMMA,
                KeyMapping.Category.register(ResourceLocation.fromNamespaceAndPath(MusicHud.MOD_ID,MusicHud.MOD_ID))
        );
        KeyMappingRegistry.register(mainMapping);
        KeyMappingRegistry.register(voteMapping);
        KeyMappingRegistry.register(toggleHudMapping);
        ClientTickEvent.CLIENT_POST.register(instance -> {
            while (mainMapping.consumeClick()) {
                Minecraft.getInstance().setScreen(MuiModApi.get().createScreen(new MainFragment()));
            }
            while (voteMapping.consumeClick()) {
                MusicService.getInstance().keyBindsVoteSkipCurrent();
            }
            while (toggleHudMapping.consumeClick()) {
                ModConfigSpec.ConfigValue<Boolean> enableHud = ClientConfigDefinition.enableHud;
                enableHud.set(!enableHud.get());
            }
        });
    }
}