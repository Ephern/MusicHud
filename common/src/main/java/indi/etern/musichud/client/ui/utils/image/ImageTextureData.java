package indi.etern.musichud.client.ui.utils.image;

import com.mojang.blaze3d.platform.NativeImage;
import icyllis.modernui.graphics.Bitmap;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.client.music.NowPlayingInfo;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.Logger;

import java.io.Closeable;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

@Getter
public final class ImageTextureData implements Closeable {
    private static final Logger logger = MusicHud.getLogger(ImageTextureData.class);
    private final String source;
    private final ResourceLocation location;
    private final DynamicTexture texture;
    private volatile boolean registered;

    public ImageTextureData(
            String source, ResourceLocation location, DynamicTexture texture, boolean registered
    ) {
        this.source = source;
        this.location = location;
        this.texture = texture;
        this.registered = registered;
    }

    @Override
    public void close() {
        String nowPlayingAlbumUrl = NowPlayingInfo.getInstance().getCurrentlyPlayingMusicDetail().getAlbum().getPicUrl();
        if (nowPlayingAlbumUrl.equals(source) || source.contains(nowPlayingAlbumUrl)) {
            AtomicReference<BiConsumer<MusicDetail, MusicDetail>> atomicListenerReference = new AtomicReference<>();
            atomicListenerReference.set((previous, current) -> {
                NowPlayingInfo.getInstance().getMusicSwitchListener().remove(atomicListenerReference.get());
                release();
            });
            NowPlayingInfo.getInstance().getMusicSwitchListener().add(atomicListenerReference.get());
        } else {
            release();
        }
    }

    public Bitmap convertToBitmap() {
        NativeImage pixels = texture.getPixels();
        if (pixels == null) {
            return null;
        } else {
            return ImageUtils.convertNativeImageToBitmap(pixels);
        }
    }

    public CompletableFuture<Void> register() {
        if (!registered) {
            synchronized (source) {
                if (!registered) {
                    CompletableFuture<Void> completableFuture = new CompletableFuture<>();
                    Minecraft.getInstance().submit(() -> {
                        Minecraft.getInstance().getTextureManager().register(location, texture);
                        logger.debug("Registered texture {} : {}", location, texture);
                        registered = true;
                    }).thenAcceptAsync((v) -> {
                        completableFuture.complete(null);
                    }, MusicHud.EXECUTOR);
                    return completableFuture;
                } else {
                    logger.debug("Texture already registered (concurrent access detected) {} : {}", location, texture);
                    return CompletableFuture.completedFuture(null);
                }
            }
        } else {
            logger.debug("Texture already registered {} : {}", location, texture);
            return CompletableFuture.completedFuture(null);
        }
    }

    public void release() {
        //noinspection ResultOfMethodCallIgnored
        Minecraft.getInstance().submit(() -> {
            Minecraft.getInstance().getTextureManager().release(location);
            logger.debug("Released texture {} : {}", location, texture);
            registered = true;
        });
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (ImageTextureData) obj;
        return Objects.equals(this.source, that.source) &&
                Objects.equals(this.location, that.location) &&
                Objects.equals(this.texture, that.texture) &&
                this.registered == that.registered;
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, location, texture, registered);
    }

    @Override
    public String toString() {
        return "ImageTextureData[" +
                "source=" + source + ", " +
                "location=" + location + ", " +
                "texture=" + texture + ", " +
                "registered=" + registered + ']';
    }
}