package indi.etern.musichud.client.ui.utils.image;

import com.mojang.blaze3d.platform.NativeImage;
import icyllis.modernui.graphics.Bitmap;
import indi.etern.musichud.beans.music.MusicDetail;
import indi.etern.musichud.client.audio.NowPlayingInfo;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;

import java.io.Closeable;
import java.lang.ref.Cleaner;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

@Getter
public final class ImageTextureData implements Closeable {
    private final String source;
    private final DynamicTexture texture;
    private static final Cleaner CLEANER = Cleaner.create();
    private final Cleaner.Cleanable cleanable;

    public ImageTextureData(
            String source, DynamicTexture texture
    ) {
        this.source = source;
        this.texture = texture;
        this.cleanable = CLEANER.register(this, () -> {
            try {
                //noinspection ResultOfMethodCallIgnored
                Minecraft.getInstance().submit(texture::close);
            } catch (Exception ignored) {}
        });
    }

    @Override
    public void close() {
        String nowPlayingAlbumUrl = NowPlayingInfo.getInstance().getCurrentlyPlayingMusicDetail().getAlbum().getPicUrl();
        if (nowPlayingAlbumUrl.equals(source) || source.contains(nowPlayingAlbumUrl)) {
            AtomicReference<BiConsumer<MusicDetail, MusicDetail>> atomicListenerReference = new AtomicReference<>();
            atomicListenerReference.set((previous, current) -> {
                NowPlayingInfo.getInstance().getMusicSwitchListener().remove(atomicListenerReference.get());
                //noinspection ResultOfMethodCallIgnored
                Minecraft.getInstance().submit(texture::close);
            });
            NowPlayingInfo.getInstance().getMusicSwitchListener().add(atomicListenerReference.get());
        } else {
            //noinspection ResultOfMethodCallIgnored
            Minecraft.getInstance().submit(texture::close);
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

/*
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
*/

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (ImageTextureData) obj;
        return Objects.equals(this.source, that.source) &&
                Objects.equals(this.texture, that.texture);
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, texture);
    }

    @Override
    public String toString() {
        return "ImageTextureData[" +
                "source=" + source + ", " +
                "texture=" + texture + ']';
    }
}