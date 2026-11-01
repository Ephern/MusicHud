package indi.etern.musichud.client.ui.hud.presentation;

import com.mojang.blaze3d.systems.RenderSystem;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.ui.hud.metadata.BackgroundImages;
import indi.etern.musichud.client.utils.image.ImageTextureData;
import indi.etern.musichud.client.utils.image.ImageUtils;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * The artwork of one track with explicit ownership. Every texture is derived from one decode and
 * kept out of {@link ImageUtils}' shared cache so the crossfade can keep sampling the previous cover
 * after a track switch.
 */
public final class TrackArtwork {
    private static final Logger LOGGER = MusicHud.getLogger(TrackArtwork.class);

    private final ImageTextureData background;
    private final ImageTextureData thumbnail;
    private final ImageTextureData source;
    private boolean closed;

    private TrackArtwork(ImageTextureData background, ImageTextureData thumbnail, ImageTextureData source) {
        this.background = background;
        this.thumbnail = thumbnail;
        this.source = source;
    }

    /** Decodes once and derives the thumbnail, background and resample sources. Blocks. */
    public static @Nullable TrackArtwork load(String picUrl, int thumbnailSize, int backgroundSize,
                                              int sourceSize) {
        if (picUrl == null || picUrl.isBlank() || thumbnailSize < 1 || backgroundSize < 1
                || sourceSize < 1) {
            return null;
        }
        int[] boxes = Arrays.stream(new int[]{thumbnailSize, backgroundSize, sourceSize})
                .filter(box -> box > 0)
                .distinct()
                .sorted()
                .toArray();
        try {
            ImageTextureData[] textures = ImageUtils.downloadScaledSetOwned(picUrl, boxes).join();
            ImageTextureData thumbnail = pick(textures, boxes, thumbnailSize);
            if (thumbnail == null) {
                return null;
            }
            //noinspection resource
            ImageTextureData background = pick(textures, boxes, backgroundSize);
            //noinspection resource
            ImageTextureData source = pick(textures, boxes, sourceSize);
            return new TrackArtwork(background != null ? background : thumbnail, thumbnail,
                    source != null ? source : thumbnail);
        } catch (RuntimeException failure) {
            LOGGER.debug("While loading the track artwork", failure);
            return null;
        }
    }

    private static @Nullable ImageTextureData pick(ImageTextureData[] textures, int[] boxes, int box) {
        for (int i = 0; i < boxes.length; i++) {
            if (boxes[i] == box) {
                return textures[i];
            }
        }
        return null;
    }

    /** Source for the scene background layers (small). */
    public ImageTextureData background() {
        return background;
    }

    /** Source for the sharp cover shown in the HUD/GUI (display size). */
    public ImageTextureData thumbnail() {
        return thumbnail;
    }

    /** Resample source for the display art. */
    public ImageTextureData source() {
        return source;
    }

    public BackgroundImages images() {
        return new BackgroundImages(thumbnail, 1f, background, source);
    }

    public boolean holds(ImageTextureData data) {
        return data != null && (data == background || data == thumbnail || data == source);
    }

    public boolean isClosed() {
        synchronized (this) {
            return closed;
        }
    }

    /**
     * Releases the textures. The GL resources are destroyed on the render thread, so this may be
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
        if (source != background && source != thumbnail) {
            closeQuietly(source);
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
