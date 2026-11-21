package indi.etern.musichud.client.ui.shadow;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** Declarative Gaussian shadow settings. */
public record BlurShadowSpec(
        @NotNull Shape shape,
        float radiusDp,
        float offsetXDp,
        float offsetYDp,
        int color,
        float alpha,
        @NotNull CacheMode cacheMode
) {
    public BlurShadowSpec {
        Objects.requireNonNull(shape, "shape");
        Objects.requireNonNull(cacheMode, "cacheMode");
        radiusDp = Math.max(0f, radiusDp);
        alpha = Math.clamp(alpha, 0f, 1f);
    }

    public enum CacheMode {
        /** Re-render the shadow source only when the owner invalidates it. */
        STATIC,
        /** Re-render the shadow source every frame (animated content). */
        LIVE
    }

    public sealed interface Shape permits Shape.RoundedRect {
        record RoundedRect(float cornerRadiusDp) implements Shape {
            public RoundedRect {
                cornerRadiusDp = Math.max(0f, cornerRadiusDp);
            }
        }
    }
}
