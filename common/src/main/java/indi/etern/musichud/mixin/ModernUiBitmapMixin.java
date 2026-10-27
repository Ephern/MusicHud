package indi.etern.musichud.mixin;

import org.apache.logging.log4j.LogManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@Mixin(targets = "icyllis.modernui.graphics.Bitmap", remap = false)
public abstract class ModernUiBitmapMixin {
    @Unique
    private static volatile Boolean music_hud$legacyArc3d;

    @Unique
    private static boolean music_hud$isLegacyArc3d() {
        Boolean legacy = music_hud$legacyArc3d;
        if (legacy == null) {
            legacy = music_hud$detectLegacyArc3d();
            music_hud$legacyArc3d = legacy;
        }
        return legacy;
    }

    @Unique
    private static boolean music_hud$detectLegacyArc3d() {
        boolean legacy = false;
        try {
            Class<?> pixelUtils = Class.forName("icyllis.arc3d.core.PixelUtils", false,
                    ModernUiBitmapMixin.class.getClassLoader());
            pixelUtils.getDeclaredField("UNSAFE");
            pixelUtils.getDeclaredMethod("store_BGRA_8888", Object.class, long.class, int.class);
            legacy = true;
        } catch (Throwable ignored) {
        }
        LogManager.getLogger("MusicHud/ModernUiBitmapMixin")
                .info("ModernUI bitmap pixel-offset patch legacyArc3d={}", legacy);
        return legacy;
    }

    @Unique
    private static long music_hud$fixInt(long address) {
        return music_hud$isLegacyArc3d() ? address - 48L : address;
    }

    @Unique
    private static long music_hud$fixFloat(long address) {
        return music_hud$isLegacyArc3d() ? address - 240L : address;
    }

    @ModifyArg(method = "getPixels([IIIIIII)V",
            at = @At(value = "INVOKE",
                    target = "Licyllis/arc3d/core/Pixmap;<init>(Licyllis/arc3d/core/ImageInfo;Ljava/lang/Object;JI)V",
                    remap = false),
            index = 2, remap = false)
    private static long music_hud$fixGetInt(long address) {
        return music_hud$fixInt(address);
    }

    @ModifyArg(method = "setPixels([IIIIIII)V",
            at = @At(value = "INVOKE",
                    target = "Licyllis/arc3d/core/Pixmap;<init>(Licyllis/arc3d/core/ImageInfo;Ljava/lang/Object;JI)V",
                    remap = false),
            index = 2, remap = false)
    private static long music_hud$fixSetInt(long address) {
        return music_hud$fixInt(address);
    }

    @ModifyArg(method = "getPixels([FIIIIII)V",
            at = @At(value = "INVOKE",
                    target = "Licyllis/arc3d/core/Pixmap;<init>(Licyllis/arc3d/core/ImageInfo;Ljava/lang/Object;JI)V",
                    remap = false),
            index = 2, remap = false)
    private static long music_hud$fixGetFloat(long address) {
        return music_hud$fixFloat(address);
    }

    @ModifyArg(method = "setPixels([FIIIIII)V",
            at = @At(value = "INVOKE",
                    target = "Licyllis/arc3d/core/Pixmap;<init>(Licyllis/arc3d/core/ImageInfo;Ljava/lang/Object;JI)V",
                    remap = false),
            index = 2, remap = false)
    private static long music_hud$fixSetFloat(long address) {
        return music_hud$fixFloat(address);
    }
}
