package indi.etern.musichud.client.ui.render.gpu;

import indi.etern.musichud.client.ui.hud.metadata.BackgroundImages;
import indi.etern.musichud.client.utils.image.ImageTextureData;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Owns the active and previous canonical cover textures and crossfades between them.
 * <p>
 * Image-to-image switches use {@link SceneCoverBlend}; none-to-image and image-to-none
 * switches fade the existing cover through the overlay opacity instead.
 * <p>
 * Transition progress is derived from the absolute image availability timestamp so a
 * renderer that was not drawing while the music changed does not restart the fade when
 * it becomes visible again.
 */
public final class SceneCoverTransition {
    private static final float TRANSITION_SECONDS = 0.5f;

    private final SceneCover[] covers = {new SceneCover(), new SceneCover()};
    private final SceneCoverBlend blend = new SceneCoverBlend();

    private SceneGpuContext owner;
    private int activeSlot;
    private ImageTextureData activeSource;
    private ImageTextureData previousSource;
    private ImageTextureData fadingSource;
    private SceneGpuTexture activeTexture;
    private SceneGpuTexture previousTexture;
    private SceneGpuTexture fadingTexture;
    private boolean blending;
    private boolean fading;
    private float transitionStartSeconds;
    private float fadeFrom;
    private float fadeTo;
    private int targetLongEdge;

    public record Frame(@Nullable SceneGpuTexture cover, float opacityScale, boolean animated) {
    }

    public Frame update(SceneGpuContext gpu, @Nullable BackgroundImages images,
                        long changedAtMillis, int targetLongEdge, float timeSeconds) {
        if (gpu == null) {
            return new Frame(null, 0f, false);
        }
        if (owner != gpu) {
            resetState();
            owner = gpu;
        }
        if (this.targetLongEdge != targetLongEdge) {
            this.targetLongEdge = targetLongEdge;
            reResolve(gpu);
        }
        ImageTextureData nextSource = sourceOf(images);
        if (!Objects.equals(nextSource, activeSource)) {
            transitionTo(gpu, images, changedAtMillis, timeSeconds);
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
                previousSource = null;
            }
        } else if (fading) {
            float t = progress(timeSeconds);
            opacityScale = fadeFrom + (fadeTo - fadeFrom) * smooth(t);
            cover = fadingTexture != null ? fadingTexture : activeTexture;
            if (t >= 1f) {
                fading = false;
                fadingTexture = null;
                fadingSource = null;
                opacityScale = fadeTo;
                if (fadeTo <= 0f) {
                    cover = null;
                } else {
                    cover = activeTexture;
                }
            }
        }
        return new Frame(cover, opacityScale, animated);
    }

    private void transitionTo(SceneGpuContext gpu, @Nullable BackgroundImages images,
                              long changedAtMillis, float timeSeconds) {
        ImageTextureData source = sourceOf(images);
        if (source == null) {
            if (activeTexture != null) {
                float startSeconds = changedAtMillis / 1000f;
                float t = progress(startSeconds, timeSeconds);
                if (t >= 1f) {
                    activeTexture = null;
                    activeSource = null;
                    fadingTexture = null;
                    fadingSource = null;
                    previousTexture = null;
                    previousSource = null;
                    blending = false;
                    fading = false;
                    transitionStartSeconds = startSeconds;
                } else {
                    fadingTexture = activeTexture;
                    fadingSource = activeSource;
                    activeTexture = null;
                    activeSource = null;
                    previousTexture = null;
                    previousSource = null;
                    blending = false;
                    fading = true;
                    fadeFrom = 1f;
                    fadeTo = 0f;
                    transitionStartSeconds = startSeconds;
                }
            }
            return;
        }

        int nextSlot = 1 - activeSlot;
        SceneGpuTexture prepared = covers[nextSlot].prepare(gpu, source, targetLongEdge);
        if (prepared == null) {
            // Keep the current cover; remember the failed source so it is not retried every frame.
            activeSource = source;
            return;
        }

        float startSeconds = changedAtMillis / 1000f;
        float t = progress(startSeconds, timeSeconds);
        if (activeTexture != null) {
            if (t < 1f) {
                previousTexture = activeTexture;
                previousSource = activeSource;
                blending = true;
            } else {
                previousTexture = null;
                previousSource = null;
                blending = false;
            }
            fading = false;
            fadingTexture = null;
            fadingSource = null;
        } else {
            previousTexture = null;
            previousSource = null;
            blending = false;
            if (t < 1f) {
                fading = true;
                fadingTexture = null;
                fadingSource = null;
                fadeFrom = 0f;
                fadeTo = 1f;
            } else {
                fading = false;
                fadeFrom = 1f;
                fadeTo = 1f;
            }
        }
        activeSlot = nextSlot;
        activeTexture = prepared;
        activeSource = source;
        transitionStartSeconds = startSeconds;
    }

    private void reResolve(SceneGpuContext gpu) {
        if (activeSource != null) {
            SceneGpuTexture prepared = covers[activeSlot].prepare(gpu, activeSource, targetLongEdge);
            if (prepared != null) {
                activeTexture = prepared;
            }
        }
        if (previousSource != null) {
            SceneGpuTexture prepared = covers[1 - activeSlot].prepare(
                    gpu, previousSource, targetLongEdge);
            if (prepared != null) {
                previousTexture = prepared;
            }
        }
        if (fadingSource != null) {
            SceneGpuTexture prepared = covers[activeSlot].prepare(
                    gpu, fadingSource, targetLongEdge);
            if (prepared != null) {
                fadingTexture = prepared;
            }
        }
    }

    private float progress(float timeSeconds) {
        return progress(transitionStartSeconds, timeSeconds);
    }

    private static float progress(float startSeconds, float timeSeconds) {
        return Math.clamp((timeSeconds - startSeconds) / TRANSITION_SECONDS, 0f, 1f);
    }

    private static float smooth(float t) {
        return t * t * (3f - 2f * t);
    }

    private static @Nullable ImageTextureData sourceOf(@Nullable BackgroundImages images) {
        if (images == null) return null;
        return images.original != null ? images.original : images.current;
    }

    private void resetState() {
        activeSlot = 0;
        activeSource = null;
        previousSource = null;
        fadingSource = null;
        activeTexture = null;
        previousTexture = null;
        fadingTexture = null;
        blending = false;
        fading = false;
        transitionStartSeconds = 0f;
        fadeFrom = 0f;
        fadeTo = 0f;
        targetLongEdge = 0;
    }
}
