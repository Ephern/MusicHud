package indi.etern.musichud.client.ui.hud.metadata;

import icyllis.modernui.view.Gravity;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.ui.hud.renderer.HudRenderContext;
import lombok.Getter;
import net.minecraft.client.resources.language.I18n;

@Getter
public enum HorizontalAlign {
    LEFT(MusicHud.MOD_ID + ".config.layout.horizontalAlign.LEFT", Gravity.LEFT) {
        @Override
        float calcX(float x, HudRenderContext renderContext, Layout hudLayout) {
            return x;
        }
    }, CENTER(MusicHud.MOD_ID + ".config.layout.horizontalAlign.CENTER", Gravity.CENTER) {
        @Override
        float calcX(float x, HudRenderContext renderContext, Layout hudLayout) {
            return (float) renderContext.guiWidth() / 2 + x - hudLayout.width / 2;
        }
    }, RIGHT("music_hud.config.layout.horizontalAlign.RIGHT", Gravity.RIGHT) {
        @Override
        float calcX(float x, HudRenderContext renderContext, Layout hudLayout) {
            return renderContext.guiWidth() - hudLayout.width - x;
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
    abstract float calcX(float x, HudRenderContext renderContext, Layout hudLayout);
}
