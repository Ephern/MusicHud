package indi.etern.musichud.client.ui.hud.renderer;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import icyllis.modernui.mc.GradientRectangleRenderState;
import icyllis.modernui.mc.MuiModApi;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.ui.hud.metadata.BackgroundImage;
import indi.etern.musichud.client.ui.hud.metadata.HudRenderData;
import indi.etern.musichud.client.ui.hud.metadata.HudUniformWriter;
import indi.etern.musichud.client.ui.hud.metadata.Layout;
import indi.etern.musichud.client.ui.hud.piplines.HudRenderPipelines;
import indi.etern.musichud.client.ui.utils.image.ImageTextureData;
import indi.etern.musichud.client.ui.utils.image.ImageUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;

public class AlbumImageRenderer {
    private static volatile AlbumImageRenderer instance;
    private Identifier defaultImageLocation;
    private GpuBufferSlice gpuBufferSlice;
    private final HudUniformWriter uniformWriter = new HudUniformWriter();
    private HudRenderData currentData;

    public static AlbumImageRenderer getInstance() {
        if (instance == null) {
            synchronized (AlbumImageRenderer.class) {
                if (instance == null)
                    instance = new AlbumImageRenderer();
            }
        }
        return instance;
    }

    public void configure(HudRenderData data) {
        this.currentData = data;
    }

    public void render(GuiGraphics gr) {
        if (currentData == null) {
            return;
        }

        gpuBufferSlice = uniformWriter.write(currentData, gr);

        Layout layout = currentData.getLayout();
        BackgroundImage bgImage = currentData.getBackgroundImage();

        var transitionStatus = HudRenderData.getTransitionStatus();
        var nextData = transitionStatus.getNextData();
        Identifier nextUnblurredLocation = nextData == null ? null : nextData.nextUnblurred();
        DynamicTexture currentTexture = getDynamicTexture(bgImage.currentUnblurredLocation);
        DynamicTexture nextTexture = getDynamicTexture(nextUnblurredLocation);
        DynamicTexture transitionTexture = transitionStatus.isTransitioning() ?
                nextTexture : currentTexture;

        TextureSetup textureSetup;
        if (currentTexture != null) {
            textureSetup = transitionTexture != null ?
                    TextureSetup.doubleTexture(
                            currentTexture.getTextureView(), currentTexture.getSampler(),
                            transitionTexture.getTextureView(), transitionTexture.getSampler()
                    ) : TextureSetup.singleTexture(currentTexture.getTextureView(), currentTexture.getSampler());
        } else {
            textureSetup = TextureSetup.noTexture();
        }

        float halfWidth = layout.width / 2f;
        float halfHeight = layout.height / 2f;

        ScreenRectangle scissor = MuiModApi.get().peekScissorStack(gr);
        MuiModApi.get().submitGuiElementRenderState(gr,
                new GradientRectangleRenderState(
                        HudRenderPipelines.ROUNDED_ALBUM,
                        textureSetup,
                        new Matrix3x2f(gr.pose()),
                        -halfWidth, -halfHeight, halfWidth, halfHeight,
                        0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF,
                        scissor
                ));
    }

    private DynamicTexture getDynamicTexture(Identifier imageLocation) {
        if (imageLocation == null) {
            if (defaultImageLocation == null) {
                String greyImageBase64 = MusicHud.ICON_BASE64;
                ImageTextureData imageTextureData = ImageUtils.loadBase64(greyImageBase64);
                imageTextureData.register().join();
                defaultImageLocation = imageTextureData.getLocation();
            }
            return getDynamicTexture(defaultImageLocation);
        }

        AbstractTexture texture = Minecraft.getInstance()
                .getTextureManager()
                .getTexture(imageLocation);
        if (texture instanceof DynamicTexture dynamicTexture) {
            return dynamicTexture;
        }
        return null;
    }

    public void updateRenderPass(RenderPass renderPass) {
        if (gpuBufferSlice != null) {
            renderPass.setUniform("HudAlbumParams", gpuBufferSlice);
        }
    }
}
