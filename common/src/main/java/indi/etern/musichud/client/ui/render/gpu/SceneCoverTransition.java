package indi.etern.musichud.client.ui.render.gpu;

import indi.etern.musichud.client.utils.image.ImageTextureData;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Owns the active and previous downsampled cover textures and crossfades between them.
 * <p>
 * Image-to-image switches use {@link SceneCoverBlend}; none-to-image and image-to-none
 * switches fade the existing cover through the overlay opacity instead.
 */
public final class SceneCoverTransition {
    private static final float TRANSITION_SECONDS = 0.5f;

    private final SceneCover[] covers = {new SceneCover(), new SceneCover()};
    private final SceneCoverBlend blend = new SceneCoverBlend();

    private SceneGpuContext owner;
    private int activeSlot;
    private ImageTextureData activeImage;
    private SceneGpuTexture activeTexture;
    private SceneGpuTexture previousTexture;
    private SceneGpuTexture fadingTexture;
    private boolean blending;
    private boolean fading;
    private float transitionStart;
    private float fadeFrom;
    private float fadeTo;

    public record Frame(@Nullable SceneGpuTexture cover, float opacityScale, boolean animated) {
    }

    public Frame update(SceneGpuContext gpu, @Nullable ImageTextureData image,
                        int targetLongEdge, float timeSeconds) {
        if (gpu == null) {
            return new Frame(null, 0f, false);
        }
        if (owner != gpu) {
            owner = gpu;
            activeSlot = 0;
            activeImage = null;
            activeTexture = null;
            previousTexture = null;
            fadingTexture = null;
            blending = false;
            fading = false;
        }
        if (!Objects.equals(image, activeImage)) {
            transitionTo(gpu, image, targetLongEdge, timeSeconds);
        }

        float opacityScale = 1f;
        boolean animated = blending || fading;
        SceneGpuTexture cover = activeTexture;
        if (blending) {
            float t = progress(timeSeconds);
            SceneGpuTexture blended = blend.apply(gpu, previousTexture, activeTexture, smooth(t));
            if (blended != null) {
                cover = blended;
            }
            if (t >= 1f) {
                blending = false;
                previousTexture = null;
            }
        } else if (fading) {
            float t = progress(timeSeconds);
            opacityScale = fadeFrom + (fadeTo - fadeFrom) * smooth(t);
            cover = fadingTexture != null ? fadingTexture : activeTexture;
            if (t >= 1f) {
                fading = false;
                fadingTexture = null;
                opacityScale = fadeTo;
                if (fadeTo <= 0f) {
                    cover = null;
                }
            }
        }
        return new Frame(cover, opacityScale, animated);
    }

    private void transitionTo(SceneGpuContext gpu, @Nullable ImageTextureData image,
                              int targetLongEdge, float timeSeconds) {
        if (image == null) {
            activeImage = null;
            if (activeTexture != null) {
                fadingTexture = activeTexture;
                activeTexture = null;
                previousTexture = null;
                blending = false;
                fading = true;
                fadeFrom = 1f;
                fadeTo = 0f;
                transitionStart = timeSeconds;
            }
            return;
        }

        int nextSlot = 1 - activeSlot;
        SceneGpuTexture prepared = covers[nextSlot].prepare(gpu, image, targetLongEdge);
        if (prepared == null) {
            // Keep the current cover; remember the failed image so it is not retried every frame.
            activeImage = image;
            return;
        }
        if (activeTexture != null) {
            previousTexture = activeTexture;
            blending = true;
            fading = false;
            fadingTexture = null;
        } else {
            previousTexture = null;
            blending = false;
            fading = true;
            fadingTexture = null;
            fadeFrom = 0f;
            fadeTo = 1f;
        }
        activeSlot = nextSlot;
        activeTexture = prepared;
        activeImage = image;
        transitionStart = timeSeconds;
    }

    private float progress(float timeSeconds) {
        return Math.clamp((timeSeconds - transitionStart) / TRANSITION_SECONDS, 0f, 1f);
    }

    private static float smooth(float t) {
        return t * t * (3f - 2f * t);
    }
}
