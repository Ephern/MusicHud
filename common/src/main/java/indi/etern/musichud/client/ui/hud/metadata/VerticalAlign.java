package indi.etern.musichud.client.ui.hud.metadata;

import icyllis.modernui.view.Gravity;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.ui.hud.renderer.HudRenderContext;
import lombok.Getter;
import net.minecraft.client.resources.language.I18n;

@Getter
public enum VerticalAlign {
    TOP(MusicHud.MOD_ID + ".config.layout.verticalAlign.TOP", Gravity.TOP) {
        @Override
        float calcY(float y, HudRenderContext hudRenderContext, Layout hudLayout) {
            return y;
        }
    }, CENTER("music_hud.config.layout.verticalAlign.CENTER", Gravity.CENTER) {
        @Override
        float calcY(float y, HudRenderContext hudRenderContext, Layout hudLayout) {
            return (float) hudRenderContext.guiHeight() / 2 + y - hudLayout.height / 2;
        }
    }, BOTTOM("music_hud.config.layout.verticalAlign.BOTTOM", Gravity.BOTTOM) {
        @Override
        float calcY(float y, HudRenderContext hudRenderContext, Layout hudLayout) {
            return hudRenderContext.guiHeight() - hudLayout.height - y;
        }
    };

    private final String displayNameKey;
    private final int gravity;

    VerticalAlign(String displayNameKey, int gravity) {
        this.displayNameKey = displayNameKey;
        this.gravity = gravity;
    }


    abstract float calcY(float y, HudRenderContext hudRenderContext, Layout hudLayout);

    @Override
    public String toString() {
        return I18n.get(displayNameKey);
    }
}
