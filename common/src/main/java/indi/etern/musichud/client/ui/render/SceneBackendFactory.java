package indi.etern.musichud.client.ui.render;

/** Creates a platform backend. Registered by the platform module only when the runtime supports it. */
public interface SceneBackendFactory {
    SceneBackend create();
}
