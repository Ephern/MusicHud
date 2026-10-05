package indi.etern.musichud.mixin;

import icyllis.arc3d.engine.*;
import icyllis.arc3d.vulkan.VulkanImageDesc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static org.lwjgl.vulkan.VK11.*;

@Pseudo
@Mixin(value = Caps.class, remap = false)
public abstract class VulkanSampledCopyMixin {
    @Shadow
    public abstract int maxTextureSize();

    @Inject(method = "getImageDescForSampledCopy", at = @At("HEAD"),
            cancellable = true, remap = false)
    private void musichud$vulkanSampledCopy(ImageDesc src, int width, int height,
                                            int depthOrArraySize, int imageFlags,
                                            CallbackInfoReturnable<ImageDesc> cir) {
        if (!(src instanceof VulkanImageDesc vkSrc)) return;
        if (width < 1 || height < 1 || depthOrArraySize < 1) return;
        if (width > maxTextureSize() || height > maxTextureSize()) return;

        int mipLevelCount = (imageFlags & ISurface.FLAG_MIPMAPPED) != 0
                ? DataUtils.computeMipLevelCount(width, height, 1) : 1;
        int usage = VK_IMAGE_USAGE_TRANSFER_SRC_BIT
                | VK_IMAGE_USAGE_TRANSFER_DST_BIT
                | VK_IMAGE_USAGE_SAMPLED_BIT;
        if ((imageFlags & ISurface.FLAG_RENDERABLE) != 0) {
            usage |= VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_INPUT_ATTACHMENT_BIT;
        }
        cir.setReturnValue(new VulkanImageDesc(
                0, VK_IMAGE_TYPE_2D, vkSrc.getVkFormat(), VK_IMAGE_TILING_OPTIMAL,
                usage, VK_SHARING_MODE_EXCLUSIVE,
                Engine.ImageType.k2D, src.getViewFormat(),
                width, height, 1, 1, mipLevelCount, 1,
                imageFlags | ISurface.FLAG_SAMPLED_IMAGE));
    }
}
