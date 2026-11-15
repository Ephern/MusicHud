package indi.etern.musichud.client.ui.render;

import icyllis.modernui.view.Gravity;
import indi.etern.musichud.MusicHud;
import lombok.Getter;
import net.minecraft.client.resources.language.I18n;

@Getter
public enum VerticalAlign {
    TOP(MusicHud.MOD_ID + ".config.layout.verticalAlign.TOP", Gravity.TOP) {
        @Override
        public float calcY(float y, int guiHeight, SceneStyle hudHudStyle) {
            return y;
        }
    }, CENTER(MusicHud.MOD_ID + ".config.layout.verticalAlign.CENTER", Gravity.CENTER) {
        @Override
        public float calcY(float y, int guiHeight, SceneStyle hudHudStyle) {
            return (float) guiHeight / 2 + y - hudHudStyle.getHeight() / 2;
        }
    }, BOTTOM(MusicHud.MOD_ID + ".config.layout.verticalAlign.BOTTOM", Gravity.BOTTOM) {
        @Override
        public float calcY(float y, int guiHeight, SceneStyle hudHudStyle) {
            return guiHeight - hudHudStyle.getHeight() - y;
        }
    };

    private final String displayNameKey;
    private final int gravity;

    VerticalAlign(String displayNameKey, int gravity) {
        this.displayNameKey = displayNameKey;
        this.gravity = gravity;
    }


    public float calcY(float y, SceneRenderContext hudRenderContext, SceneStyle hudHudStyle) {
        return calcY(y, hudRenderContext.guiHeight(), hudHudStyle);
    }

    public abstract float calcY(float y, int guiHeight, SceneStyle hudHudStyle);

    @Override
    public String toString() {
        return I18n.get(displayNameKey);
    }
}
