package indi.etern.musichud.client.ui.render.interop;

/**
 * What a resource will be used for across the API boundary. Vulkan needs it at creation time (usage
 * flags are immutable), GL ignores it.
 */
public enum InteropUsage {
    /** Sampled by the other API. */
    SAMPLE,
    /** Rendered into by the other API. */
    RENDER_TARGET
}
