package indi.etern.musichud.neoforge;

import indi.etern.musichud.client.ui.ClientScreens;
import indi.etern.musichud.client.ui.ModernUiSupport;
import indi.etern.musichud.client.ui.screen.ModernUiRequiredScreen;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("unused")
public class ConfigScreenFactory implements IConfigScreenFactory {
    @Override
    @NotNull
    public Screen createScreen(@NotNull ModContainer container, @NotNull Screen modListScreen) {
        return ModernUiSupport.available()
                ? ClientScreens.configScreen(modListScreen)
                : new ModernUiRequiredScreen(modListScreen);
    }
}
