package indi.etern.musichud.fabric.client;

import fuzs.forgeconfigapiport.fabric.api.v5.ModConfigEvents;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.ui.hud.scene.HudScene;
import indi.etern.musichud.client.ui.render.RenderRuntime;
import indi.etern.musichud.client.ui.render.VanillaSceneGraphics;
import indi.etern.musichud.platform.mod.forgeConfig.config.ClientConfigDefinition;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;

/** Registers the ModernUI-backed HUD. Only loaded when {@code ModernUiSupport} reports availability. */
public final class ClientUiBootstrap {
    private ClientUiBootstrap() {
    }

    public static void start() {
        ModConfigEvents.loading(MusicHud.MOD_ID).register((config) -> {
            if (config.getSpec() == ClientConfigDefinition.configure.getRight()) {
                HudScene scene = HudScene.getInstance();
                HudElementRegistry.addFirst(
                        Identifier.fromNamespaceAndPath(MusicHud.MOD_ID, "main_hud"),
                        (graphics, deltaTracker) -> RenderRuntime.getInstance()
                                .renderFrame(new VanillaSceneGraphics(graphics), scene)
                );
            }
        });
    }
}
