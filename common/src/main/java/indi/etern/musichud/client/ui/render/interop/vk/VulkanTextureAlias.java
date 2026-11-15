package indi.etern.musichud.client.ui.render.interop.vk;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
import com.mojang.renderpearl.backend.vulkan.VulkanGpuTexture;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * blaze3d view of a Vulkan image that arc3d owns.
 * <p>
 * {@code VulkanGpuTexture}'s only constructor allocates a new image, so the alias is allocated
 * without running a constructor and its fields are filled in directly. Closing it does nothing: the
 * image stays owned by arc3d.
 */
final class VulkanTextureAlias extends VulkanGpuTexture {
    private static final Object UNSAFE;
    private static final Method ALLOCATE_INSTANCE;

    static {
        Object unsafe = null;
        Method allocate = null;
        try {
            Class<?> type = Class.forName("sun.misc.Unsafe");
            Field field = type.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            unsafe = field.get(null);
            allocate = type.getMethod("allocateInstance", Class.class);
        } catch (Throwable unavailable) {
            unsafe = null;
        }
        UNSAFE = unsafe;
        ALLOCATE_INSTANCE = allocate;
    }

    @SuppressWarnings("unused")
    private VulkanTextureAlias() {
        // Never called: instances are allocated without a constructor.
        super(null, 0, "", GpuFormat.RGBA8_UNORM, 1, 1, 1, 1);
    }

    static boolean isSupported() {
        return UNSAFE != null;
    }

    static @Nullable VulkanGpuTexture create(VulkanDevice device, long image, int width, int height,
                                             GpuFormat format) {
        if (UNSAFE == null) {
            return null;
        }
        try {
            VulkanGpuTexture alias = (VulkanGpuTexture) ALLOCATE_INSTANCE.invoke(UNSAFE,
                    VulkanTextureAlias.class);
            set(alias, GpuTexture.class, "usage", GpuTexture.USAGE_TEXTURE_BINDING);
            set(alias, GpuTexture.class, "label", "MusicHud arc3d texture");
            set(alias, GpuTexture.class, "format", format);
            set(alias, GpuTexture.class, "width", width);
            set(alias, GpuTexture.class, "height", height);
            set(alias, GpuTexture.class, "depthOrLayers", 1);
            set(alias, GpuTexture.class, "mipLevels", 1);
            set(alias, VulkanGpuTexture.class, "device", device);
            set(alias, VulkanGpuTexture.class, "vkImage", image);
            set(alias, VulkanGpuTexture.class, "vmaAllocation", 0L);
            set(alias, VulkanGpuTexture.class, "closed", false);
            set(alias, VulkanGpuTexture.class, "views", 0);
            return alias;
        } catch (Throwable failure) {
            return null;
        }
    }

    private static void set(Object target, Class<?> owner, String name, Object value)
            throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Override
    public boolean isClosed() {
        return false;
    }

    @Override
    public void close() {
    }

    @Override
    public void destroy() {
    }
}
