package indi.etern.musichud.client.ui.render.gpu;

import com.mojang.blaze3d.opengl.GlDevice;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.*;
import indi.etern.musichud.interfaces.ClientConfig;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL21;
import org.lwjgl.system.MemoryUtil;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

/** Render-thread-owned target. Never wrap borrowed album textures in this class. */
final class SceneNativeTarget implements AutoCloseable {
    private final GpuTexture texture;
    private final GpuTextureView view;
    private boolean closed;
    private final boolean halfFloat;

    /** Advanced targets use native OpenGL storage; unsupported hardware fails explicitly. */
    SceneNativeTarget(String label, int width, int height, boolean halfFloat) {
        RenderSystem.assertOnRenderThread();
        this.halfFloat = halfFloat;
        int maximum = RenderSystem.getDevice().getMaxTextureSize();
        if (width < 1 || height < 1 || width > maximum || height > maximum) {
            throw new IllegalArgumentException("Invalid scene target size: " + width + "x" + height);
        }
        int usage = GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING;
        boolean forceNative = ClientConfig.getInstance().getForceGL32Path();
        if (halfFloat || forceNative) {
            NativeDevice bridge = NativeDevice.resolve(RenderSystem.getDevice());
            SceneNativeTexture nativeTexture = SceneNativeTexture.create(usage, label, width, height, halfFloat);
            try {
                nativeTexture.setTextureFilter(FilterMode.LINEAR, false);
                nativeTexture.flushModeChanges(0);
                texture = bridge.wrap(nativeTexture);
            } catch (RuntimeException | Error failure) {
                nativeTexture.close();
                throw failure;
            }
        } else {
            texture = RenderSystem.getDevice().createTexture(label, usage,
                    TextureFormat.RGBA8, width, height, 1, 1);
        }
        GpuTextureView created;
        try {
            texture.setAddressMode(AddressMode.CLAMP_TO_EDGE);
            texture.setTextureFilter(FilterMode.LINEAR, false);
            if (texture instanceof SceneNativeTexture nativeTexture) {
                nativeTexture.flushModeChanges(0);
            }
            created = RenderSystem.getDevice().createTextureView(texture);
        } catch (RuntimeException | Error failure) {
            texture.close();
            throw failure;
        }
        view = created;
    }

    public GpuTextureView view() {
        if (closed) throw new IllegalStateException("Scene target is closed");
        return view;
    }

    public void setSampler(AddressMode addressU, AddressMode addressV, FilterMode minFilter, FilterMode magFilter) {
        RenderSystem.assertOnRenderThread();
        if (closed) throw new IllegalStateException("Scene target is closed");
        texture.setAddressMode(addressU, addressV);
        texture.setTextureFilter(minFilter, magFilter, false);
        if (texture instanceof SceneNativeTexture nativeTexture) {
            nativeTexture.flushModeChanges(0);
        }
    }

    /** Read an owned RGBA8 cover once for its statistics, preserving texture and pixel-pack state. */
    public byte[] readRgba8() {
        RenderSystem.assertOnRenderThread();
        if (closed) throw new IllegalStateException("Scene target is closed");
        if (halfFloat) throw new IllegalStateException("RGBA8 readback cannot read a half-float target");
        GpuTexture raw = view.texture();
        if (!(raw instanceof GlTexture nativeTexture)) {
            throw new UnsupportedOperationException("Scene texture readback requires an OpenGL texture view");
        }
        int byteCount = Math.multiplyExact(Math.multiplyExact(width(), height()), 4);
        int[] parameters = {GL11.GL_PACK_ALIGNMENT, GL11.GL_PACK_ROW_LENGTH, GL12.GL_PACK_IMAGE_HEIGHT,
                GL11.GL_PACK_SKIP_PIXELS, GL11.GL_PACK_SKIP_ROWS, GL12.GL_PACK_SKIP_IMAGES};
        int[] previous = new int[parameters.length];
        for (int i = 0; i < parameters.length; i++) previous[i] = GL11.glGetInteger(parameters[i]);
        int packBuffer = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        int previousBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        ByteBuffer pixels = MemoryUtil.memAlloc(byteCount);
        try {
            GL21.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
            for (int parameter : parameters) GL11.glPixelStorei(parameter,
                    parameter == GL11.GL_PACK_ALIGNMENT ? 1 : 0);
            GlStateManager._bindTexture(nativeTexture.glId());
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            byte[] result = new byte[byteCount];
            pixels.get(result);
            return result;
        } finally {
            GlStateManager._bindTexture(previousBinding);
            for (int i = 0; i < parameters.length; i++) GL11.glPixelStorei(parameters[i], previous[i]);
            GL21.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, packBuffer);
            MemoryUtil.memFree(pixels);
        }
    }

    /** Keep the owning device's validation texture wrapper on clear operations. */
    public void clear(int color) {
        RenderSystem.assertOnRenderThread();
        if (closed) throw new IllegalStateException("Scene target is closed");
        RenderSystem.getDevice().createCommandEncoder().clearColorTexture(texture, color);
    }

    public boolean halfFloat() { return halfFloat; }

    public int width() { return texture.getWidth(0); }
    public int height() { return texture.getHeight(0); }

    /** Optional NeoForge bridge; uses its own factory/validator and keeps validation enabled. */
    private record NativeDevice(Object validationDevice, Method wrapMethod, Object validator, Method validateUsage) {
        private static final String VALIDATION_DEVICE =
                "net.neoforged.neoforge.client.blaze3d.validation.ValidationGpuDevice";

        static NativeDevice resolve(GpuDevice device) {
            if (device instanceof GlDevice) return new NativeDevice(null, null, null, null);
            try {
                Class<?> type = Class.forName(VALIDATION_DEVICE, false, device.getClass().getClassLoader());
                if (!type.isInstance(device)
                        || !(type.getMethod("getRealDevice").invoke(device) instanceof GlDevice)) {
                    throw new UnsupportedOperationException("Scene native targets require the OpenGL backend");
                }
                Field validatorField = type.getDeclaredField("validator");
                Method wrap = type.getDeclaredMethod("wrapGpuTexture", GpuTexture.class, validatorField.getType());
                Method validateUsage = validatorField.getType().getDeclaredMethod("validateTextureUsage", int.class);
                if (!validatorField.trySetAccessible() || !wrap.trySetAccessible() || !validateUsage.trySetAccessible()) {
                    throw new UnsupportedOperationException("NeoForge validation texture bridge is inaccessible");
                }
                return new NativeDevice(device, wrap, validatorField.get(device), validateUsage);
            } catch (ReflectiveOperationException failure) {
                throw new UnsupportedOperationException("Unsupported scene GPU device wrapper", failure);
            }
        }

        GpuTexture wrap(SceneNativeTexture texture) {
            if (validationDevice == null) return texture;
            try {
                validateUsage.invoke(validator, texture.usage());
                Object wrapped = wrapMethod.invoke(validationDevice, texture, validator);
                if (!(wrapped instanceof GpuTexture result) || result == texture) {
                    throw new IllegalStateException("NeoForge did not create a validation texture wrapper");
                }
                return result;
            } catch (InvocationTargetException failure) {
                if (failure.getCause() instanceof RuntimeException runtime) throw runtime;
                if (failure.getCause() instanceof Error error) throw error;
                throw new IllegalStateException("NeoForge texture validation wrapper failed", failure.getCause());
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Cannot create NeoForge validation texture wrapper", failure);
            }
        }
    }

    /** Call after the final GUI consumer, including when resizing or reloading resources. */
    @Override public void close() {
        RenderSystem.assertOnRenderThread();
        if (!closed) {
            closed = true;
            try { view.close(); } finally { texture.close(); }
        }
    }
}
