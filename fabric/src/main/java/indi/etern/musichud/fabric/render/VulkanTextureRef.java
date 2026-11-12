package indi.etern.musichud.fabric.render;

import indi.etern.musichud.client.ui.render.pipeline.SceneTextureRef;

/** Raw VulkanMod/GL-compatible texture name used for the main render target or uploaded artwork. */
record VulkanTextureRef(int id) implements SceneTextureRef {
}
