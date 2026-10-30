package indi.etern.musichud.client.ui.hud.presentation;

import com.mojang.blaze3d.systems.RenderSystem;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.ui.hud.metadata.BackgroundImages;
import indi.etern.musichud.client.utils.image.ImageTextureData;
import indi.etern.musichud.client.utils.image.ImageUtils;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

/**
 * The artwork of one track, with explicit ownership.
 * <p>
 * Both textures are derived from a single decode and are deliberately kept out of
 * {@link ImageUtils}' shared texture cache: {@link TrackArtworkStore} decides when they die. That is
 * what makes the crossfades safe — they keep sampling the previous cover for a while, which a
 * cache-eviction policy cannot express.
 */
public final class TrackArtwork {
    private static final Logger LOGGER = MusicHud.getLogger(TrackArtwork.class);

    private final ImageTextureData background;
    private final ImageTextureData thumbnail;
    private boolean closed;

    private TrackArtwork(ImageTextureData background, ImageTextureData thumbnail) {
        this.background = background;
        this.thumbnail = thumbnail;
    }

    /**
     * Decodes the artwork once and derives the display thumbnail and the (much smaller) background
     * source. Blocks; call it from a worker thread.
     */
    public static @Nullable TrackArtwork load(String picUrl, int thumbnailSize, int backgroundSize) {
        if (picUrl == null || picUrl.isBlank() || thumbnailSize < 1 || backgroundSize < 1) {
            return null;
        }
        boolean shared = thumbnailSize == backgroundSize;
        int[] boxes = shared
                ? new int[]{backgroundSize}
                : new int[]{thumbnailSize, backgroundSize};
        try {
            ImageTextureData[] textures = ImageUtils.downloadScaledSetOwned(picUrl, boxes).join();
            if (textures.length == 0 || textures[0] == null) {
                return null;
            }
            return shared
                    ? new TrackArtwork(textures[0], textures[0])
                    : new TrackArtwork(textures[1], textures[0]);
        } catch (RuntimeException failure) {
            LOGGER.debug("While loading the track artwork", failure);
            return null;
        }
    }

    /** Source for the scene background layers (small). */
    public ImageTextureData background() {
        return background;
    }

    /** Source for the sharp cover shown in the HUD/GUI (display size). */
    public ImageTextureData thumbnail() {
        return thumbnail;
    }

    public BackgroundImages images() {
        return new BackgroundImages(thumbnail, 1f, background);
    }

    public boolean holds(ImageTextureData data) {
        return data != null && (data == background || data == thumbnail);
    }

    public boolean isClosed() {
        synchronized (this) {
            return closed;
        }
    }

    /**
     * Releases both textures. The GL resources are destroyed on the render thread, so this may be
     * called from any thread and is idempotent.
     */
    public void close() {
        synchronized (this) {
            if (closed) return;
            closed = true;
        }
        if (RenderSystem.isOnRenderThread()) {
            closeTextures();
        } else {
            try {
                var minecraft = Minecraft.getInstance();
                //noinspection ConstantValue
                if (minecraft != null) {
                    minecraft.submit(this::closeTextures);
                }
            } catch (RuntimeException failure) {
                LOGGER.debug("While scheduling the artwork release", failure);
            }
        }
    }

    private void closeTextures() {
        closeQuietly(background);
        if (thumbnail != background) {
            closeQuietly(thumbnail);
        }
    }

    private static void closeQuietly(ImageTextureData data) {
        if (data == null) return;
        try {
            data.closeDirect();
        } catch (RuntimeException failure) {
            LOGGER.debug("While releasing an artwork texture", failure);
        }
    }
}
