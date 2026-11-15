package indi.etern.musichud.client.ui.render.interop.vk;

import com.mojang.blaze3d.vulkan.VulkanCommandPool;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanQueue;
import com.mojang.blaze3d.vulkan.VulkanUtils;
import indi.etern.musichud.client.ui.render.gpu.SceneRetirement;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkImageSubresourceRange;

/**
 * Image layout transitions for textures arc3d rendered into.
 * <p>
 * blaze3d never barriers (every game texture lives in {@code VK_IMAGE_LAYOUT_GENERAL} from
 * allocation on), so the first blaze3d pass that samples an arc3d target has to fix the layout
 * itself. The barrier is submitted on the game's queue ahead of that pass, which Vulkan orders by
 * submission.
 */
final class VulkanImageBarrier {
    private VulkanImageBarrier() {
    }

    static void transitionToSampled(VulkanDevice device, long image, int oldLayout) {
        VulkanCommandPool pool = new VulkanCommandPool(device, device.graphicsQueue());
        try {
            VkCommandBuffer commandBuffer = pool.allocateBuffer();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkCommandBufferBeginInfo begin = VkCommandBufferBeginInfo.calloc(stack).sType$Default();
                begin.flags(VK10.VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
                VulkanUtils.crashIfFailure(device, VK10.vkBeginCommandBuffer(commandBuffer, begin),
                        "Failed to begin the scene interop barrier");

                VkImageMemoryBarrier.Buffer barrier =
                        VkImageMemoryBarrier.calloc(1, stack).sType$Default();
                barrier.oldLayout(oldLayout);
                barrier.newLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
                barrier.srcQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED);
                barrier.dstQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED);
                barrier.image(image);
                barrier.srcAccessMask(VK10.VK_ACCESS_MEMORY_WRITE_BIT);
                barrier.dstAccessMask(VK10.VK_ACCESS_MEMORY_READ_BIT);
                VkImageSubresourceRange range = barrier.subresourceRange();
                range.aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
                        .baseMipLevel(0).levelCount(1).baseArrayLayer(0).layerCount(1);
                VK12.vkCmdPipelineBarrier(commandBuffer,
                        VK10.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
                        VK10.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
                        0, null, null, barrier);
                VulkanUtils.crashIfFailure(device, VK10.vkEndCommandBuffer(commandBuffer),
                        "Failed to end the scene interop barrier");
            }
            VulkanQueue.Submission submission = device.graphicsQueue().beginSubmit();
            submission.executeCommands(commandBuffer);
            submission.close();
        } finally {
            // The pool, and its command buffer, may still be executing: retire instead of destroying.
            SceneRetirement.retire(pool::destroy);
        }
    }
}
