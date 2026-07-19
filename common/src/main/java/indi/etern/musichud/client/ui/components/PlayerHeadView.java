package indi.etern.musichud.client.ui.components;

import com.mojang.blaze3d.platform.NativeImage;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Image;
import icyllis.modernui.graphics.drawable.ImageDrawable;
import icyllis.modernui.view.ViewTreeObserver;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.ImageView;
import indi.etern.musichud.client.ui.utils.image.ImageUtils;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.util.function.Supplier;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;

public class PlayerHeadView extends FrameLayout {
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
        getViewTreeObserver().addOnPreDrawListener(preDrawListener);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        getViewTreeObserver().removeOnPreDrawListener(preDrawListener);
    }

    public void setPlayerSkinSupplier(@Nullable Supplier<ResourceLocation> playerSkinSupplier) {
        this.playerSkinSupplier = playerSkinSupplier;
        skin = playerSkinSupplier == null ? null : playerSkinSupplier.get();
        updateHeadImage();
    }

    private void updateHeadImage() {
        if (playerSkinSupplier != null) {
            skin = playerSkinSupplier.get();
        }
        if (skin == null) {
            faceView.setImageDrawable(null);
            hatView.setImageDrawable(null);
            lastRenderedSkin = null;
            return;
        }
        if (skin.equals(lastRenderedSkin)) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;

        NativeImage skinImage = loadSkinImage(skin);
        if (skinImage == null) return;

        try (NativeImage faceNat = new NativeImage(NativeImage.Format.RGBA, HEAD_SIZE, HEAD_SIZE, false);
             NativeImage hatNat = new NativeImage(NativeImage.Format.RGBA, HEAD_SIZE, HEAD_SIZE, false)) {
            // copyRect(dst, srcX, srcY, dstX, dstY, width, height, flipX, flipY)
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
        } catch (Exception ignored) {}
    }

    @Nullable
    private static NativeImage loadSkinImage(ResourceLocation skinId) {
        var texture = Minecraft.getInstance().getTextureManager().getTexture(skinId);
        if (texture instanceof DynamicTexture dt) {
            return dt.getPixels();
        }
        try {
            var resource = Minecraft.getInstance().getResourceManager()
                    .getResource(skinId).orElse(null);
            if (resource != null) {
                try (InputStream stream = resource.open()) {
                    return NativeImage.read(stream);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
}
