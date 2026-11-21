package indi.etern.musichud.client.ui.components.volume;

import icyllis.modernui.annotation.NonNull;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Image;
import icyllis.modernui.graphics.drawable.InsetDrawable;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.widget.ImageButton;
import icyllis.modernui.widget.ImageView;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.ui.drawable.ScaledImageDrawable;
import indi.etern.musichud.client.utils.image.ImageUtils;
import lombok.Getter;
import lombok.Setter;
import org.apache.logging.log4j.Logger;

/**
 * Mute toggle with a four-step level icon. Level changes come from the config, so the icon is
 * just a projection of it; wheel and hover are reported to the volume controller.
 */
public class VolumeIconButton extends ImageButton {
    private static final int ICON_SIZE_DP = 16;
    private static final int ICON_INSET_DP = 1;
    private static final String ICON_MUTED = "volume_x";
    private static final String ICON_LOW = "volume_0";
    private static final String ICON_MID = "volume_1";
    private static final String ICON_HIGH = "volume_2";
    private static final Logger LOGGER = MusicHud.getLogger(VolumeIconButton.class);

    @Setter
    private Listener listener;
    @Getter
    private int level = 100;
    @Getter
    private boolean muted;
    private boolean tooltipSuppressed;

    public interface Listener {
        void onToggleMute(VolumeIconButton button);

        void onScroll(VolumeIconButton button, MotionEvent event);

        void onHover(VolumeIconButton button, MotionEvent event);
    }

    public VolumeIconButton(Context context) {
        super(context);
        setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        setOnClickListener(v -> {
            if (listener == null) {
                // A visible icon without a controller is a wiring bug, not a swallowed click.
                LOGGER.warn("Volume icon clicked with no listener attached");
                return;
            }
            listener.onToggleMute(this);
        });
        applyState();
    }

    public void setLevel(int level, boolean muted) {
        if (this.level == level && this.muted == muted) {
            return;
        }
        this.level = level;
        this.muted = muted;
        applyState();
    }

    /** The flyout already shows the level, so the tooltip is dropped while it is open. */
    public void setTooltipSuppressed(boolean suppressed) {
        if (tooltipSuppressed == suppressed) {
            return;
        }
        tooltipSuppressed = suppressed;
    }

    private void applyState() {
        Image icon = ImageUtils.getImageFromResource(iconPath(level, muted));
        if (icon != null) {
            int size = dp(ICON_SIZE_DP);
            setImageDrawable(new InsetDrawable(new ScaledImageDrawable(
                    getContext().getResources(), icon, size, size), dp(ICON_INSET_DP)));
        }
    }

    public static String iconPath(int level, boolean muted) {
        String name = muted ? ICON_MUTED
                : level <= 33 ? ICON_LOW
                : level <= 67 ? ICON_MID
                : ICON_HIGH;
        return "/assets/music_hud/textures/gui/icons/" + name + ".png";
    }

    @Override
    public boolean onGenericMotionEvent(@NonNull MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_SCROLL
                && event.getAxisValue(MotionEvent.AXIS_VSCROLL) != 0f) {
            if (listener != null) {
                listener.onScroll(this, event);
            }
            return true;
        }
        return super.onGenericMotionEvent(event);
    }

    @Override
    protected boolean dispatchHoverEvent(@NonNull MotionEvent event) {
        if (listener != null) {
            listener.onHover(this, event);
        }
        return super.dispatchHoverEvent(event);
    }
}
