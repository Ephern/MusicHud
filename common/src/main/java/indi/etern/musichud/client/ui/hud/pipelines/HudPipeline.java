package indi.etern.musichud.client.ui.hud.pipelines;

/**
 * Version-neutral handle to a render pipeline (shader pair + render state).
 * <p>
 * The version-specific implementation wraps the actual pipeline object
 * ({@code RenderPipeline} on 1.21.6+, the custom shader program on 1.21.1).
 */
public interface HudPipeline {
    String name();
}
