package indi.etern.musichud.fabric.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import indi.etern.musichud.client.ui.screen.MusicHudScreen;
import indi.etern.musichud.client.ui.screen.MainFragment;

public class ModMenuConfigurer implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            var fragment = MainFragment.getInstance();
            fragment.setDefaultSelectedIndex(3);//Setting page
            return MusicHudScreen.createScreen(fragment, null, parent, "Music HUD");
        };
    }
}
