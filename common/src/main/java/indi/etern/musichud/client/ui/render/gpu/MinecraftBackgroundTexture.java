package indi.etern.musichud.client.ui.render.gpu;

import icyllis.arc3d.sketch.Image;
import org.jetbrains.annotations.Nullable;

/**
 * Exposes the baked GUI background to the UI thread. The bake target is reused across frames, so its
 * wrapper stays valid until the background size changes.
 */
public final class MinecraftBackgroundTexture {
    private static final PublishedSceneTexture TEXTURE = new PublishedSceneTexture();

    private MinecraftBackgroundTexture() {
    }

    public static void publish(SceneGpuTexture baked) {
        TEXTURE.publish(baked);
    }

    public static void clear() {
        TEXTURE.publish(null);
    }

    public static void registerConsumer() {
        TEXTURE.registerConsumer();
    }

    public static void unregisterConsumer() {
        TEXTURE.unregisterConsumer();
    }

    public static @Nullable Image image() {
        return TEXTURE.image();
    }
}
