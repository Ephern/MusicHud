package indi.etern.musichud.client.ui.hud.metadata;

import indi.etern.musichud.client.ui.utils.TransitionStatus;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class HudRenderData {
    private static final int TRANSITION_DURATION_MS = 500;
    @Getter
    volatile static TransitionStatus<TransitionNextData> transitionStatus = new TransitionStatus<>(TRANSITION_DURATION_MS);
    private volatile Layout layout;
    private volatile BackgroundColor backgroundColor;
    private volatile BackgroundImage backgroundImage;
    private volatile ProgressBar progressBar;

    public HudRenderData(Layout layout, BackgroundImage backgroundImage) {
        this.layout = layout;
        this.backgroundImage = backgroundImage;
    }
}