package indi.etern.musichud.client.ui.render.gpu.blaze3d.gl;

import com.mojang.blaze3d.opengl.GlDevice;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.textures.GpuTexture;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Reflection bridge for NeoForge's validation device wrapper. */
final class NeoForgeValidationBridge {
    private static final String VALIDATION_DEVICE =
            "net.neoforged.neoforge.client.blaze3d.validation.ValidationGpuDevice";

    private NeoForgeValidationBridge() {
    }

    static boolean isGlDevice(GpuDevice device) {
        if (device instanceof GlDevice) {
            return true;
        }
        Object real = realDevice(device);
        return real instanceof GlDevice;
    }

    static GpuTexture wrap(GpuDevice device, GpuTexture texture) {
        Validation validation = Validation.resolve(device);
        return validation == null ? texture : validation.wrap(texture);
    }

    private static Object realDevice(GpuDevice device) {
        try {
            Class<?> type = Class.forName(VALIDATION_DEVICE, false,
                    device.getClass().getClassLoader());
            if (!type.isInstance(device)) {
                return null;
            }
            return type.getMethod("getRealDevice").invoke(device);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    /** Resolved reflection handles. */
    private record Validation(Object validationDevice, Method wrapMethod,
                              Object validator, Method validateUsage) {
        private static Validation resolve(GpuDevice device) {
            if (device instanceof GlDevice) {
                return null;
            }
            try {
                Class<?> type = Class.forName(VALIDATION_DEVICE, false,
                        device.getClass().getClassLoader());
                if (!type.isInstance(device)
                        || !(type.getMethod("getRealDevice").invoke(device) instanceof GlDevice)) {
                    throw new UnsupportedOperationException(
                            "Scene native targets require the OpenGL backend");
                }
                Field validatorField = type.getDeclaredField("validator");
                Method wrap = type.getDeclaredMethod("wrapGpuTexture",
                        GpuTexture.class, validatorField.getType());
                Method validateUsage = validatorField.getType()
                        .getDeclaredMethod("validateTextureUsage", int.class);
                if (!validatorField.trySetAccessible() || !wrap.trySetAccessible()
                        || !validateUsage.trySetAccessible()) {
                    throw new UnsupportedOperationException(
                            "NeoForge validation texture bridge is inaccessible");
                }
                return new Validation(device, wrap, validatorField.get(device), validateUsage);
            } catch (ReflectiveOperationException | LinkageError failure) {
                throw new UnsupportedOperationException(
                        "Unsupported scene GPU device wrapper", failure);
            }
        }

        private GpuTexture wrap(GpuTexture texture) {
            try {
                validateUsage.invoke(validator, texture.usage());
                Object wrapped = wrapMethod.invoke(validationDevice, texture, validator);
                if (!(wrapped instanceof GpuTexture result) || result == texture) {
                    throw new IllegalStateException(
                            "NeoForge did not create a validation texture wrapper");
                }
                return result;
            } catch (InvocationTargetException failure) {
                if (failure.getCause() instanceof RuntimeException runtime) throw runtime;
                if (failure.getCause() instanceof Error error) throw error;
                throw new IllegalStateException(
                        "NeoForge texture validation wrapper failed", failure.getCause());
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException(
                        "Cannot create NeoForge validation texture wrapper", failure);
            }
        }
    }
}
