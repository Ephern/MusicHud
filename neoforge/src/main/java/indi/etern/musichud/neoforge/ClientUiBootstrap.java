package indi.etern.musichud.neoforge;

import indi.etern.musichud.client.ui.hud.scene.HudScene;
import indi.etern.musichud.client.ui.render.RenderRuntime;
import indi.etern.musichud.client.ui.render.VanillaSceneGraphics;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Registers the ModernUI-backed HUD. Only loaded when {@code ModernUiSupport} reports availability. */
public final class ClientUiBootstrap {
    private static HudScene scene;

    private ClientUiBootstrap() {
    }

    public static void ensureStarted() {
        if (scene != null) {
            return;
        }
        scene = HudScene.getInstance();
        NeoForge.EVENT_BUS.addListener(ClientUiBootstrap::onRenderGui);
    }

    private static void onRenderGui(RenderGuiEvent.Pre event) {
        if (scene != null) {
            RenderRuntime.getInstance().renderFrame(new VanillaSceneGraphics(event.getGuiGraphics()), scene);
        }
    }
}
