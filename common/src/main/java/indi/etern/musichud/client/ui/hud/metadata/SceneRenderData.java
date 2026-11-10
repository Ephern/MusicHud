package indi.etern.musichud.client.ui.hud.metadata;

import indi.etern.musichud.client.ui.render.*;
import indi.etern.musichud.client.ui.render.pipeline.*;

import indi.etern.musichud.client.utils.ui.Transitionable;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class SceneRenderData {
    private static final int TRANSITION_DURATION_MS = 500;
    volatile SceneStyle hudStyle;
    volatile Transitionable<BackgroundData> transitionableBackground;
    volatile SceneRenderData fallback;
    private long initTimestamp;

    public SceneRenderData(SceneStyle hudStyle, BackgroundImages backgroundImages) {
        this.hudStyle = hudStyle;
        BackgroundData backgroundData = backgroundImages == null ? BackgroundData.NONE : new BackgroundData(backgroundImages);
        transitionableBackground = new Transitionable<>(backgroundData, TRANSITION_DURATION_MS);
        initTimestamp = System.currentTimeMillis();
    }

    public SceneRenderData(SceneStyle hudStyle) {
        this.hudStyle = hudStyle;
        initTimestamp = System.currentTimeMillis();
    }

    public Transitionable<BackgroundData> getTransitionableBackground() {
        if (fallback != null && transitionableBackground == null) {
            return fallback.transitionableBackground;
        } else {
            return transitionableBackground;
        }
    }
}