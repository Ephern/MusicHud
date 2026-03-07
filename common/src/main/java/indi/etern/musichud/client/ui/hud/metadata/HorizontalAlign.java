package indi.etern.musichud.client.ui.hud.metadata;

import icyllis.modernui.view.Gravity;
import lombok.Getter;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;

@Getter
public enum HorizontalAlign {
    LEFT("music_hud.config.layout.horizontalAlign.LEFT", Gravity.LEFT) {
        @Override
        float calcX(float x, GuiGraphics graphics, Layout hudLayout) {
            return x;
        }
    }, CENTER("music_hud.config.layout.horizontalAlign.CENTER", Gravity.CENTER) {
        @Override
        float calcX(float x, GuiGraphics graphics, Layout hudLayout) {
            return (float) graphics.guiWidth() / 2 + x - hudLayout.width / 2;
        }
    }, RIGHT("music_hud.config.layout.horizontalAlign.RIGHT", Gravity.RIGHT) {
        @Override
        float calcX(float x, GuiGraphics graphics, Layout hudLayout) {
            return graphics.guiWidth() - hudLayout.width - x;
        }
    };

    private final String displayName;
    private final int gravity;

    HorizontalAlign(String displayName, int gravity) {
        this.displayName = displayName;
        this.gravity = gravity;
    }

    @Override
    public String toString() {
        return I18n.get(displayName);
    }
    abstract float calcX(float x, GuiGraphics graphics, Layout hudLayout);
}
