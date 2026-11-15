package indi.etern.musichud.fabric.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import indi.etern.musichud.client.ui.ClientScreens;
import indi.etern.musichud.client.ui.ModernUiSupport;
import indi.etern.musichud.client.ui.screen.ModernUiRequiredScreen;

public class ModMenuConfigurer implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> ModernUiSupport.available()
                ? ClientScreens.configScreen(parent)
                : new ModernUiRequiredScreen(parent);
    }
}
