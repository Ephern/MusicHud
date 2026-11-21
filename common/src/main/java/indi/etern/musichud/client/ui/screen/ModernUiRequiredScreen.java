package indi.etern.musichud.client.ui.screen;

import indi.etern.musichud.client.ui.ModernUiSupport;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Vanilla-only screen shown when the installed ModernUI is too old for the client UI. It has no
 * ModernUI references so it also works as the fallback for the ModMenu / NeoForge config entries.
 */
public class ModernUiRequiredScreen extends Screen {
    private static final Component TITLE = Component.translatable("music_hud.modernuiRequired.title");
    private static final Component MESSAGE = Component.translatable("music_hud.modernuiRequired.message");
    private static final Component DOWNLOAD = Component.translatable("music_hud.modernuiRequired.download");

    private final @Nullable Screen parent;

    public ModernUiRequiredScreen(@Nullable Screen parent) {
        super(TITLE);
        this.parent = parent;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(DOWNLOAD, button -> Util.getPlatform().openUri(ModernUiSupport.DOWNLOAD_URL))
                .bounds(width / 2 - 100, height / 2 + 28, 200, 20)
                .build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose())
                .bounds(width / 2 - 100, height / 2 + 54, 200, 20)
                .build());
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        int centerX = width / 2;
        int top = height / 2 - 52;
        graphics.drawCenteredString(font, TITLE, centerX, top, 0xFFFFFFFF);
        graphics.drawCenteredString(font, MESSAGE, centerX, top + 22, 0xFFB0B0B0);
        graphics.drawCenteredString(font, Component.literal(ModernUiSupport.DOWNLOAD_URL),
                centerX, top + 42, 0xFF6EA8FF);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            // A null parent means the screen was opened in-game; setScreen(null) resumes play.
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
