package indi.etern.musichud.client.ui.render.interop.vk;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.backend.common.BaseGpuTexture;
import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
import com.mojang.renderpearl.backend.vulkan.VulkanGpuTexture;
import indi.etern.musichud.MusicHud;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * blaze3d view of a Vulkan image that arc3d owns.
 * <p>
 * {@code VulkanGpuTexture}'s only constructor allocates a new image, so the alias is allocated
 * without running a constructor and its fields are filled in directly. Closing it does nothing: the
 * image stays owned by arc3d.
 */
final class VulkanTextureAlias extends VulkanGpuTexture {
    private static final Logger LOGGER = MusicHud.getLogger(VulkanTextureAlias.class);
    private static final AtomicBoolean CREATE_FAILURE_LOGGED = new AtomicBoolean();
    private static final Object UNSAFE;
    private static final Method ALLOCATE_INSTANCE;
    private static final boolean SUPPORTED;

    // GpuTexture is an interface since 26.3; its state lives in BaseGpuTexture.
    private static final String[] BASE_FIELDS = {
            "format", "width", "height", "depthOrLayers", "mipLevels", "usage", "label"};
    private static final String[] TEXTURE_FIELDS = {
            "device", "vkImage", "vmaAllocation", "closed", "views"};

    static {
        Object unsafe;
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
        SUPPORTED = unsafe != null
                && hasFields(BaseGpuTexture.class, BASE_FIELDS)
                && hasFields(VulkanGpuTexture.class, TEXTURE_FIELDS);
        if (unsafe != null && !SUPPORTED) {
            LOGGER.warn("Scene interop: game texture fields changed; arc3d image import is disabled");
        }
    }

    @SuppressWarnings("unused")
    private VulkanTextureAlias() {
        // Never called: instances are allocated without a constructor.
        //noinspection DataFlowIssue
        super(null, 0, "", GpuFormat.RGBA8_UNORM, 1, 1, 1, 1);
    }

    static boolean isSupported() {
        return SUPPORTED;
    }

    static @Nullable VulkanGpuTexture create(VulkanDevice device, long image, int width, int height,
                                             GpuFormat format) {
        if (!SUPPORTED) {
            return null;
        }
        try {
            VulkanGpuTexture alias = (VulkanGpuTexture) ALLOCATE_INSTANCE.invoke(UNSAFE,
                    VulkanTextureAlias.class);
            set(alias, BaseGpuTexture.class, "usage", GpuTexture.USAGE_TEXTURE_BINDING);
            set(alias, BaseGpuTexture.class, "label", "MusicHud arc3d texture");
            set(alias, BaseGpuTexture.class, "format", format);
            set(alias, BaseGpuTexture.class, "width", width);
            set(alias, BaseGpuTexture.class, "height", height);
            set(alias, BaseGpuTexture.class, "depthOrLayers", 1);
            set(alias, BaseGpuTexture.class, "mipLevels", 1);
            set(alias, VulkanGpuTexture.class, "device", device);
            set(alias, VulkanGpuTexture.class, "vkImage", image);
            set(alias, VulkanGpuTexture.class, "vmaAllocation", 0L);
            set(alias, VulkanGpuTexture.class, "closed", false);
            set(alias, VulkanGpuTexture.class, "views", 0);
            return alias;
        } catch (Throwable failure) {
            if (CREATE_FAILURE_LOGGED.compareAndSet(false, true)) {
                LOGGER.warn("Scene interop: cannot alias an arc3d image as a game texture", failure);
            }
            return null;
        }
    }

    private static boolean hasFields(Class<?> owner, String[] names) {
        try {
            for (String name : names) {
                owner.getDeclaredField(name).setAccessible(true);
            }
            return true;
        } catch (Throwable missing) {
            return false;
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
