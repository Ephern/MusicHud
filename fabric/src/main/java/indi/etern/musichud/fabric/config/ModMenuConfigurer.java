package indi.etern.musichud.fabric.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import indi.etern.musichud.client.ui.screen.MusicHudScreen;

public class ModMenuConfigurer implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return MusicHudScreen::createScreen;
    }
}
