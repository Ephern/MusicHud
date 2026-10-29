package indi.etern.musichud.client.ui.render.gpu.blaze3d;

import com.mojang.blaze3d.textures.GpuTextureView;
import indi.etern.musichud.client.ui.render.pipeline.SceneTextureRef;

public record GpuTextureViewRef(GpuTextureView view) implements SceneTextureRef {
}
