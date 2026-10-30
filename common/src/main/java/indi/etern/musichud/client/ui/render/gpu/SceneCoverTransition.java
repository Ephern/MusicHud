package indi.etern.musichud.client.ui.render.gpu;

import indi.etern.musichud.client.ui.render.FluidBackgroundSettings;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class SceneCoverTransition {
    private static final float TRANSITION_SECONDS = FluidBackgroundSettings.COVER_TRANSITION_SECONDS;

    private final SceneCover[] covers = {new SceneCover(), new SceneCover()};
    private final SceneCoverBlend blend = new SceneCoverBlend();

    private SceneGpuContext owner;
    private int activeSlot;
    private SceneCoverSource activeSource;
    private SceneCoverSource previousSource;
    private SceneCoverSource fadingSource;
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

    /** Releases every cover/blend target; the state is reset so the next frame re-resolves. */
    public void release() {
        covers[0].release();
        covers[1].release();
        blend.release();
        resetState();
        owner = null;
    }

    /**
     * @return true while any crossfade slot still samples {@code identity}, i.e. while the artwork
     * that owns it may not be released yet.
     */
    public boolean holds(Object identity) {
        return identity != null
                && (samples(activeSource, identity)
                || samples(previousSource, identity)
                || samples(fadingSource, identity));
    }

    private static boolean samples(@Nullable SceneCoverSource source, Object identity) {
        return source != null && Objects.equals(source.identity(), identity);
    }

    public Frame update(SceneGpuContext gpu, @Nullable SceneCoverSource source,
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
        if (!Objects.equals(source, activeSource)) {
            transitionTo(gpu, source, changedAtMillis, timeSeconds);
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

    private void transitionTo(SceneGpuContext gpu, @Nullable SceneCoverSource source,
                              long changedAtMillis, float timeSeconds) {
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
