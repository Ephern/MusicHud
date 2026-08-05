package indi.etern.musichud.client.ui.components;

import com.mojang.blaze3d.platform.NativeImage;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Image;
import icyllis.modernui.graphics.drawable.ImageDrawable;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.view.ViewTreeObserver;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.ImageView;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.utils.image.ImageUtils;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.HttpTexture;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;

public class PlayerHeadView extends FrameLayout {
    private static final Logger LOGGER = MusicHud.getLogger(PlayerHeadView.class);
    private static final int HEAD_SIZE = 8;
    private static final int SKIN_FACE_U = 8;
    private static final int SKIN_FACE_V = 8;
    private static final int SKIN_HAT_U = 40;
    private static final int SKIN_HAT_V = 8;
    private static final float FACE_SCALE = 0.87f;

    @Getter
    private Supplier<ResourceLocation> playerSkinSupplier;

    @Setter
    @Getter
    private ResourceLocation skin;

    private final ImageView faceView;
    private final ImageView hatView;
    private ResourceLocation lastRenderedSkin;
    private String pendingDownloadUrl;
    private CompletableFuture<?> pendingDownload;

    private static final VarHandle HTTP_TEXTURE_URL_HANDLE;

    static {
        VarHandle handle = null;
        try {
            for (Field f : HttpTexture.class.getDeclaredFields()) {
                if (f.getType() == String.class && !Modifier.isStatic(f.getModifiers())) {
                    MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(HttpTexture.class, MethodHandles.lookup());
                    handle = lookup.unreflectVarHandle(f);
                    break;
                }
            }
        } catch (Throwable t) {
            LOGGER.error("Failed to init VarHandle for HttpTexture url", t);
        }
        HTTP_TEXTURE_URL_HANDLE = handle;
    }

    private final ViewTreeObserver.OnPreDrawListener preDrawListener = () -> {
        updateHeadImage();
        return true;
    };

    public PlayerHeadView(Context context) {
        super(context);
        faceView = new ImageView(context);
        faceView.setScaleType(ImageView.ScaleType.FIT_XY);
        hatView = new ImageView(context);
        hatView.setScaleType(ImageView.ScaleType.FIT_XY);
        addView(faceView, new LayoutParams(MATCH_PARENT, MATCH_PARENT));
        addView(hatView, new LayoutParams(MATCH_PARENT, MATCH_PARENT));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        int w = right - left;
        int h = bottom - top;
        if (w > 0 && h > 0) {
            faceView.setPivotX(w / 2f);
            faceView.setPivotY(h / 2f);
            faceView.setScaleX(FACE_SCALE);
            faceView.setScaleY(FACE_SCALE);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        lastRenderedSkin = null;
        getViewTreeObserver().addOnPreDrawListener(preDrawListener);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        getViewTreeObserver().removeOnPreDrawListener(preDrawListener);
        cancelPendingDownload();
    }

    public void setPlayerSkinSupplier(@Nullable Supplier<ResourceLocation> playerSkinSupplier) {
        this.playerSkinSupplier = playerSkinSupplier;
        skin = playerSkinSupplier == null ? null : playerSkinSupplier.get();
        updateHeadImage();
    }

    private void cancelPendingDownload() {
        if (pendingDownload != null) {
            pendingDownload.cancel(true);
            pendingDownload = null;
        }
        pendingDownloadUrl = null;
    }

    private void updateHeadImage() {
        if (playerSkinSupplier != null) {
            skin = playerSkinSupplier.get();
        }
        if (skin == null) {
            faceView.setImageDrawable(null);
            hatView.setImageDrawable(null);
            lastRenderedSkin = null;
            cancelPendingDownload();
            return;
        }
        if (skin.equals(lastRenderedSkin)) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;

        NativeImage skinImage = null;
        boolean readFromStream = false;
        try {
            var texture = minecraft.getTextureManager().getTexture(skin);
            if (texture instanceof DynamicTexture dt) {
                skinImage = dt.getPixels();
            } else if (texture instanceof HttpTexture ht) {
                String url = extractTextureUrl(ht);
                if (url != null) {
                    if (!url.equals(pendingDownloadUrl)) {
                        cancelPendingDownload();
                        pendingDownloadUrl = url;
                        pendingDownload = downloadAndRenderSkin(url, skin);
                    }
                    return;
                }
            } else {
                try {
                    var resource = minecraft.getResourceManager()
                            .getResource(skin).orElse(null);
                    if (resource != null) {
                        try (InputStream stream = resource.open()) {
                            skinImage = NativeImage.read(stream);
                            readFromStream = true;
                        }
                    }
                } catch (Exception ignored) {
                }
            }
            if (skinImage == null) return;

            renderFromNativeImage(skinImage, skin);
        } finally {
            if (readFromStream) {
                skinImage.close();
            }
        }
    }

    private void renderFromNativeImage(NativeImage skinImage, ResourceLocation skin) {
        try (NativeImage faceNat = new NativeImage(NativeImage.Format.RGBA, HEAD_SIZE, HEAD_SIZE, false);
             NativeImage hatNat = new NativeImage(NativeImage.Format.RGBA, HEAD_SIZE, HEAD_SIZE, false)) {
            skinImage.copyRect(faceNat, SKIN_FACE_U, SKIN_FACE_V, 0, 0, HEAD_SIZE, HEAD_SIZE, false, false);
            skinImage.copyRect(hatNat, SKIN_HAT_U, SKIN_HAT_V, 0, 0, HEAD_SIZE, HEAD_SIZE, false, false);

            var bitmap = ImageUtils.convertNativeImageToBitmap(faceNat);
            var resources = getContext().getResources();
            Image faceImage = Image.createTextureFromBitmap(bitmap);
            bitmap = ImageUtils.convertNativeImageToBitmap(hatNat);
            Image hatImage = Image.createTextureFromBitmap(bitmap);
            if (faceImage != null && hatImage != null) {
                var faceDrawable = new ImageDrawable(resources, faceImage);
                var hatDrawable = new ImageDrawable(resources, hatImage);
                faceDrawable.setFilter(false);
                hatDrawable.setFilter(false);
                faceView.setImageDrawable(faceDrawable);
                hatView.setImageDrawable(hatDrawable);
                lastRenderedSkin = skin;
            }
        } catch (Exception ignored) {
        }
    }

    private CompletableFuture<Void> downloadAndRenderSkin(String url, ResourceLocation skin) {
        return ImageUtils.downloadAsync(url)
                .thenAccept(imageTextureData -> {
                    if (url.equals(pendingDownloadUrl)) {
                        MuiModApi.postToUiThread(() -> {
                            try {
                                var skinImage = imageTextureData.getTexture().getPixels();
                                if (skinImage != null) {
                                    renderFromNativeImage(skinImage, skin);
                                }
                            } catch (Exception e) {
                                LOGGER.debug("Failed to render skin from download: {}", url, e);
                            }
                        });
                    }
                })
                .exceptionally(e -> {
                    LOGGER.warn("Failed to download skin: {}", url, e);
                    return null;
                });
    }

    @Nullable
    private static String extractTextureUrl(HttpTexture texture) {
        if (HTTP_TEXTURE_URL_HANDLE == null) return null;
        try {
            return (String) HTTP_TEXTURE_URL_HANDLE.get(texture);
        } catch (Exception ignored) {
            return null;
        }
    }
}
