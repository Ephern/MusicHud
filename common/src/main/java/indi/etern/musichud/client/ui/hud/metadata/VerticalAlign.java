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
        public float calcY(float y, int guiHeight, HudStyle hudHudStyle) {
            return y;
        }
    }, CENTER(MusicHud.MOD_ID + ".config.layout.verticalAlign.CENTER", Gravity.CENTER) {
        @Override
        public float calcY(float y, int guiHeight, HudStyle hudHudStyle) {
            return (float) guiHeight / 2 + y - hudHudStyle.getHeight() / 2;
        }
    }, BOTTOM(MusicHud.MOD_ID + ".config.layout.verticalAlign.BOTTOM", Gravity.BOTTOM) {
        @Override
        public float calcY(float y, int guiHeight, HudStyle hudHudStyle) {
            return guiHeight - hudHudStyle.getHeight() - y;
        }
    };

    private final String displayNameKey;
    private final int gravity;

    VerticalAlign(String displayNameKey, int gravity) {
        this.displayNameKey = displayNameKey;
        this.gravity = gravity;
    }


    public float calcY(float y, HudRenderContext hudRenderContext, HudStyle hudHudStyle) {
        return calcY(y, hudRenderContext.guiHeight(), hudHudStyle);
    }

    public abstract float calcY(float y, int guiHeight, HudStyle hudHudStyle);

    @Override
    public String toString() {
        return I18n.get(displayNameKey);
    }
}
