package indi.etern.musichud.client.ui;

import indi.etern.musichud.client.ui.screen.MainFragment;
import indi.etern.musichud.client.ui.screen.MusicHudScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

/** Entry points that open a ModernUI screen, or the vanilla fallback when it is unavailable. */
public final class ClientScreens {
    private ClientScreens() {
    }

    public static void openMain() {
        MainFragment fragment = MainFragment.getInstance();
        fragment.setDefaultSelectedIndex(0);
        Minecraft.getInstance().setScreen(MusicHudScreen.createScreen(fragment, null, null, "Music HUD"));
    }

    public static Screen configScreen(@Nullable Screen parent) {
        MainFragment fragment = MainFragment.getInstance();
        fragment.setDefaultSelectedIndex(3);
        return MusicHudScreen.createScreen(fragment, null, parent, "Music HUD");
    }
}
