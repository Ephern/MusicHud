package indi.etern.musichud.client.ui.render;

import org.jetbrains.annotations.Nullable;

/**
 * Neutral registration point. The platform initializer installs a factory only when the optional
 * renderer is present; without one the runtime keeps the GL backend.
 */
public final class SceneBackendRegistry {
    private static volatile SceneBackendFactory factory;

    private SceneBackendRegistry() {
    }

    public static void install(SceneBackendFactory backendFactory) {
        factory = backendFactory;
    }

    public static void clear() {
        factory = null;
    }

    public static @Nullable SceneBackendFactory factory() {
        return factory;
    }

    public static boolean isOverridden() {
        return factory != null;
    }
}
