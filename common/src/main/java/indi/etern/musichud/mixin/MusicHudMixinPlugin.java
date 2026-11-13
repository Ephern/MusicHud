package indi.etern.musichud.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class MusicHudMixinPlugin implements IMixinConfigPlugin {

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.contains("ReactiveMusicCompatMixin")) {
            try {
                Class.forName("circuitlord.reactivemusic.ReactiveMusic", false, getClass().getClassLoader());
                return true;
            } catch (ClassNotFoundException e) {
                return false;
            }
        }
        if (mixinClassName.contains("ScreenMixin") || mixinClassName.contains("GuiRendererSceneMixin")
                || mixinClassName.contains("UIManagerMixin")
                || mixinClassName.contains("VulkanSampledCopyMixin")) {
            return modernUiAvailable();
        }
        return true;
    }

    private static boolean modernUiAvailable() {
        if (Boolean.getBoolean("music_hud.debug.forceModernUiMissing")) {
            return false;
        }
        try {
            Class.forName("icyllis.arc3d.engine.ImageProxy", false,
                    MusicHudMixinPlugin.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
