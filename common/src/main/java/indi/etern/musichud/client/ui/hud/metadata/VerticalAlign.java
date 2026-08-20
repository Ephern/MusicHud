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
        public float calcY(float y, int guiHeight, Layout hudLayout) {
            return y;
        }
    }, CENTER(MusicHud.MOD_ID + ".config.layout.verticalAlign.CENTER", Gravity.CENTER) {
        @Override
        public float calcY(float y, int guiHeight, Layout hudLayout) {
            return (float) guiHeight / 2 + y - hudLayout.getHeight() / 2;
        }
    }, BOTTOM(MusicHud.MOD_ID + ".config.layout.verticalAlign.BOTTOM", Gravity.BOTTOM) {
        @Override
        public float calcY(float y, int guiHeight, Layout hudLayout) {
            return guiHeight - hudLayout.getHeight() - y;
        }
    };

    private final String displayNameKey;
    private final int gravity;

    VerticalAlign(String displayNameKey, int gravity) {
        this.displayNameKey = displayNameKey;
        this.gravity = gravity;
    }


    public float calcY(float y, HudRenderContext hudRenderContext, Layout hudLayout) {
        return calcY(y, hudRenderContext.guiHeight(), hudLayout);
    }

    public abstract float calcY(float y, int guiHeight, Layout hudLayout);

    @Override
    public String toString() {
        return I18n.get(displayNameKey);
    }
}
