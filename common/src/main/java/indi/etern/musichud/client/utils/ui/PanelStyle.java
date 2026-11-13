package indi.etern.musichud.client.utils.ui;

import icyllis.modernui.graphics.drawable.Drawable;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.util.ColorStateList;
import icyllis.modernui.view.View;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.interfaces.ClientConfig;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PanelStyle {
    public static final int BACK_DROP_BLUR_RADIUS = 128;
    private static final ClientConfig CONFIG = ClientConfig.getInstance();
    private static final int BORDER_RGB = 0x7F7F7F;
    private static final int STROKE_WIDTH_DP = 1;
    /** Attached views only: entries are added on attach and removed on detach. */
    private static final Map<View, Entry> dynamicStyles = new ConcurrentHashMap<>();
    private static final Logger LOGGER = MusicHud.getLogger(PanelStyle.class);

    private PanelStyle() {
    }

    public static int fillColor(double opacity, double brightness) {
        int gray = (int) Math.round(Math.clamp(brightness, 0.0, 1.0) * 127.0);
        int alpha = (int) Math.round(Math.clamp(opacity, 0.0, 1.0) * 255.0);
        return (alpha << 24) | (gray << 16) | (gray << 8) | gray;
    }

    public static int borderColor(double opacity) {
        int alpha = (int) Math.round(Math.clamp(opacity, 0.0, 1.0) * 0.5 * 255.0);
        return (alpha << 24) | BORDER_RGB;
    }

    public static void updateDynamicStyles() {
        dynamicStyles.forEach((view, entry) -> {
            try {
                entry.updateColors(view);
            } catch (Exception e) {
                LOGGER.warn("Failed to update style for {}", view, e);
            }
        });
    }

    public static void applyDynamically(View view, int cornerRadiusPx,
                                        InsetBackgroundFactory.Padding padding,
                                        InsetBackgroundFactory.BackdropBlur backdropBlur) {
        Entry entry = new Entry(cornerRadiusPx, padding, backdropBlur);
        view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                // The blur drawable is released on detach, so a re-attach needs a fresh background.
                if (entry.attachedBefore) {
                    entry.apply(v);
                }
                entry.attachedBefore = true;
                dynamicStyles.put(v, entry);
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                dynamicStyles.remove(v);
            }
        });
        // Apply before attach so the background padding is part of the first measure.
        entry.apply(view);
        if (view.isAttachedToWindow()) {
            entry.attachedBefore = true;
            dynamicStyles.put(view, entry);
        }
    }

    private static final class Entry {
        private final int cornerRadiusPx;
        private final InsetBackgroundFactory.Padding padding;
        private final InsetBackgroundFactory.BackdropBlur backdropBlur;
        private ShapeDrawable shape;
        private Drawable installedBackground;
        private double opacity = Double.NaN;
        private double brightness = Double.NaN;
        boolean attachedBefore;

        Entry(int cornerRadiusPx, InsetBackgroundFactory.Padding padding,
              InsetBackgroundFactory.BackdropBlur backdropBlur) {
            this.cornerRadiusPx = cornerRadiusPx;
            this.padding = padding;
            this.backdropBlur = backdropBlur;
        }

        void apply(View view) {
            double opacity = CONFIG.getPanelOpacity();
            double brightness = CONFIG.getPanelBrightness();
            shape = factory(view, opacity, brightness, cornerRadiusPx, padding, backdropBlur)
                    .applyBackgroundTo(view);
            installedBackground = view.getBackground();
            this.opacity = opacity;
            this.brightness = brightness;
        }

        void updateColors(View view) {
            double opacity = CONFIG.getPanelOpacity();
            double brightness = CONFIG.getPanelBrightness();
            if (opacity == this.opacity && brightness == this.brightness) {
                return;
            }
            if (shape == null || view.getBackground() != installedBackground) {
                apply(view);
                return;
            }
            shape.setColor(ColorStateList.valueOf(fillColor(opacity, brightness)));
            shape.setStroke(view.dp(STROKE_WIDTH_DP), borderColor(opacity));
            this.opacity = opacity;
            this.brightness = brightness;
        }
    }

    public static InsetBackgroundFactory factory(View view, double opacity, double brightness,
                                                 int cornerRadiusPx,
                                                 InsetBackgroundFactory.Padding padding) {
        return factory(view, opacity, brightness, cornerRadiusPx, padding,
                InsetBackgroundFactory.BackdropBlur.NONE);
    }

    public static InsetBackgroundFactory factory(View view, double opacity, double brightness,
                                                 int cornerRadiusPx,
                                                 InsetBackgroundFactory.Padding padding,
                                                 InsetBackgroundFactory.BackdropBlur backdropBlur) {
        return InsetBackgroundFactory.builder()
                .backgroundColor(ColorStateList.valueOf(fillColor(opacity, brightness)))
                .strokeWidth(view.dp(STROKE_WIDTH_DP))
                .strokeColor(borderColor(opacity))
                .cornerRadius(cornerRadiusPx)
                .padding(padding)
                .backdropBlur(backdropBlur)
                .backdropBlurRadius(backdropBlur == InsetBackgroundFactory.BackdropBlur.NONE
                        ? 0 : view.dp(BACK_DROP_BLUR_RADIUS))
                .build();
    }

    public static InsetBackgroundFactory transparentFactory(int cornerRadiusPx,
                                                            InsetBackgroundFactory.Padding padding) {
        return InsetBackgroundFactory.builder()
                .backgroundColor(ColorStateList.valueOf(0x00000000))
                .cornerRadius(cornerRadiusPx)
                .padding(padding)
                .build();
    }
}
