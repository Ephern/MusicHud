package indi.etern.musichud.client.ui.utils.image;

import com.mojang.blaze3d.platform.NativeImage;
import icyllis.arc3d.core.Pixmap;
import icyllis.modernui.graphics.Bitmap;
import indi.etern.musichud.MusicHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.concurrent.atomic.AtomicReference;

import static indi.etern.musichud.client.ui.utils.image.ImageUtils.convertBitmapToNativeImage;

public class ImageBlurPostProcessor {

    public static ImageTextureData blur(ImageTextureData originalImageData, int radius) {
        synchronized (originalImageData.getSource()) {
            String cacheKey = originalImageData.getSource() + "_blurred_" + radius;
            ImageTextureData cachedData = ImageUtils.getCachedTexturesData().getIfPresent(cacheKey);
            if (cachedData != null) {
                return cachedData;
            }

            Bitmap bitmap = originalImageData.convertToBitmap();
            assert bitmap != null;
            var result = applyGaussianBlur(bitmap, radius);

            NativeImage nativeImage = convertBitmapToNativeImage(result);
            assert nativeImage != null;
            ResourceLocation imageBlurredLocation = MusicHud.location("image_blurred_" + radius + "_" + bitmap.hashCode());
            AtomicReference<DynamicTexture> texture = new AtomicReference<>();
            Minecraft.getInstance().submit(() -> {
                texture.set(new DynamicTexture(() -> "downloaded_blurred_" + originalImageData.getSource().hashCode(), nativeImage));
            }).join();
            ImageTextureData imageTextureData = new ImageTextureData(
                    originalImageData.getSource(),
                    imageBlurredLocation,
                    texture.get(),
                    false
            );

            ImageUtils.getCachedTexturesData().put(cacheKey, imageTextureData);
            return imageTextureData;
        }
    }

    private static Bitmap applyGaussianBlur(Bitmap source, int radius) {
        int width = source.getWidth();
        int height = source.getHeight();

        // 创建目标 Bitmap
        Bitmap result = Bitmap.createBitmap(width, height, source.getFormat());

        // 获取 Pixmap 进行像素操作
        Pixmap sourcePixmap = source.getPixmap();
        Pixmap resultPixmap = result.getPixmap();

        // 创建临时 Pixmap
        Bitmap tempBitmap = Bitmap.createBitmap(width, height, source.getFormat());
        Pixmap tempPixmap = tempBitmap.getPixmap();

        float sigma = Math.max(radius / 3.0f, 0.5f);
        float[] kernel = createGaussianKernel(radius, sigma);

        // 水平模糊
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float[] color = horizontalConvolve(sourcePixmap, x, y, width, radius, kernel);
                tempPixmap.setColor4f(x, y, color);
            }
        }

        // 垂直模糊
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float[] color = verticalConvolve(tempPixmap, x, y, height, radius, kernel);
                resultPixmap.setColor4f(x, y, color);
            }
        }

        tempBitmap.close(); // 释放临时资源
        return result;
    }

    private static float[] horizontalConvolve(Pixmap pixmap, int x, int y,
                                              int width, int radius, float[] kernel) {
        float[] sum = new float[4]; // RGBA
        float weightSum = 0;

        for (int i = -radius; i <= radius; i++) {
            int nx = x + i;

            // 边界处理：镜像
            if (nx < 0) nx = -nx - 1;
            else if (nx >= width) nx = 2 * width - nx - 1;

            float[] color = new float[4];
            pixmap.getColor4f(nx, y, color);
            float weight = kernel[i + radius];

            for (int c = 0; c < 4; c++) {
                sum[c] += color[c] * weight;
            }
            weightSum += weight;
        }

        // 归一化
        if (weightSum > 0) {
            for (int c = 0; c < 4; c++) {
                sum[c] /= weightSum;
            }
        }

        return sum;
    }

    private static float[] verticalConvolve(Pixmap pixmap, int x, int y,
                                            int height, int radius, float[] kernel) {
        float[] sum = new float[4]; // RGBA
        float weightSum = 0;

        for (int i = -radius; i <= radius; i++) {
            int ny = y + i;

            // 边界处理：镜像
            if (ny < 0) ny = -ny - 1;
            else if (ny >= height) ny = 2 * height - ny - 1;

            float[] color = new float[4];
            pixmap.getColor4f(x, ny, color);
            float weight = kernel[i + radius];

            for (int c = 0; c < 4; c++) {
                sum[c] += color[c] * weight;
            }
            weightSum += weight;
        }

        // 归一化
        if (weightSum > 0) {
            for (int c = 0; c < 4; c++) {
                sum[c] /= weightSum;
            }
        }

        return sum;
    }

    private static float[] createGaussianKernel(int radius, float sigma) {
        int size = 2 * radius + 1;
        float[] kernel = new float[size];
        float sum = 0;

        for (int i = 0; i < size; i++) {
            int x = i - radius;
            kernel[i] = (float) Math.exp(-(x * x) / (2 * sigma * sigma));
            sum += kernel[i];
        }

        // 归一化
        for (int i = 0; i < size; i++) {
            kernel[i] /= sum;
        }

        return kernel;
    }
}