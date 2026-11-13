package indi.etern.musichud.client.ui.render.gpu;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Advances after a UI frame was submitted. The Vulkan backend uses it to expose textures that
 * were written by VulkanMod no earlier than one frame ago.
 */
public final class SceneFrameClock {
    private static final AtomicLong CLOCK = new AtomicLong();

    private SceneFrameClock() {
    }

    public static void tick() {
        CLOCK.incrementAndGet();
    }

    public static long current() {
        return CLOCK.get();
    }
}
