package indi.etern.musichud.client.ui.hud.renderer;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.SamplerCache;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import indi.etern.musichud.client.ui.hud.pipelines.GpuTextureViewRef;
import indi.etern.musichud.client.ui.hud.pipelines.HudPipeline;
import indi.etern.musichud.client.ui.hud.pipelines.HudRenderState;
import indi.etern.musichud.client.ui.hud.pipelines.HudTextureSetup;
import indi.etern.musichud.client.ui.hud.pipelines.HudUniform;
import indi.etern.musichud.client.ui.hud.pipelines.RenderPipelineHudPipeline;
import indi.etern.musichud.client.ui.hud.pipelines.RenderStateUtil;
import lombok.Getter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.DynamicUniformStorage;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix3x2f;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * Multiple elements may share one {@link HudPipeline} while carrying
 * different per-element uniform content. Every submitted element is kept as its own
 * {@link TextureSetup} identity and gets its own set of UBO slices. Uniform storage is
 * keyed by UBO name alone, so shared uniforms (e.g. {@code MHDynamicStatus}) are uploaded
 * only once per frame and deduplicated across elements by
 * {@link DynamicUniformStorage#writeUniform}, while distinct content for the same name
 * lives in separate blocks of the same buffer. During the GUI pass each draw binds only the
 * slices of the element it belongs to (per-draw uniform upload, equivalent to
 * {@code RenderPass.drawMultipleIndexed}'s {@code uniformUploaderConsumer}).
 * <p>
 * On-demand upload is preserved: if a logical element's uniform data did not change since
 * the last frame ({@link HudUniform#shouldUseBuffer}) and its previously uploaded slice is
 * still backed by a live buffer, the slice is reused and nothing is re-written.
 */
public class HudRenderContextImpl implements HudRenderContext {
    private static final RenderStateUtil UNIFORM_WRITER = new RenderStateUtil();
    @Getter
    private static HudRenderContext current;

    private final Map<String, DynamicUniformStorage<UniformAdapter>> storageMap = new HashMap<>();
    private final Map<CacheKey, LastWritten> lastWrittenUniforms = new HashMap<>();

    private final List<ElementRecord> elements = new ArrayList<>();
    private final Map<TextureSetup, Map<String, GpuBufferSlice>> elementSlices = new IdentityHashMap<>();
    private final Map<String, GpuBufferSlice> defaultSlices = new HashMap<>();

    private HudGraphics graphics;

    public HudRenderContextImpl() {
        current = this;
    }

    @Override
    public void beginFrame(HudGraphics graphics) {
        for (DynamicUniformStorage<?> storage : storageMap.values()) {
            storage.endFrame();
        }
        elements.clear();
        elementSlices.clear();
        defaultSlices.clear();
        this.graphics = graphics;
    }

    @Override
    public void endFrame() {
        prepareUniforms();
    }

    @Override
    public HudGraphics graphics() {
        return graphics;
    }

    @Override
    public void submitHudRenderState(HudRenderState state) {
        TextureSetup textureSetup = toVanillaTextureSetup(state.textureSetup(), state.elementKey());
        HudGuiElementRenderState element = new HudGuiElementRenderState(
                ((RenderPipelineHudPipeline) state.pipeline()).renderPipeline(),
                textureSetup,
                state.pose(),
                state.width(),
                state.height(),
                state.bounds(),
                state.elementKey(),
                state.uniforms()
        );
        UNIFORM_WRITER.submitGuiElementRenderState(((VanillaHudGraphics) graphics).vanilla(), element);
        elementSlices.put(textureSetup, new HashMap<>());
        elements.add(new ElementRecord(state.elementKey(), state.pipeline(), textureSetup, state.uniforms()));
    }

    /**
     * Distinct 1x1 texture view per logical element (elementKey). It is placed in
     * {@code texure2} of the element's {@link TextureSetup} so that elements of different
     * profiles are record-unequal and {@code GuiRenderer} never merges them into one draw,
     * while elements of the same profile stay mergeable. No mod pipeline declares
     * {@code Sampler2}, so binding this view is a no-op.
     */
    private final Map<String, GpuTextureView> discriminatorViews = new HashMap<>();

    private GpuTextureView discriminatorView(String elementKey) {
        if (elementKey == null) {
            elementKey = "";
        }
        return discriminatorViews.computeIfAbsent(elementKey, key -> {
            GpuDevice device = RenderSystem.getDevice();
            GpuTexture texture = device.createTexture(
                    "music_hud_" + key, GpuTexture.USAGE_TEXTURE_BINDING,
                    TextureFormat.RGBA8, 1, 1, 1, 1);
            return device.createTextureView(texture);
        });
    }

    private TextureSetup toVanillaTextureSetup(HudTextureSetup setup, @org.jetbrains.annotations.Nullable String elementKey) {
        GpuTextureView primary = setup.primary() == null ? null : ((GpuTextureViewRef) setup.primary()).view();
        GpuTextureView secondary = setup.secondary() == null ? null : ((GpuTextureViewRef) setup.secondary()).view();
        SamplerCache samplerCache = RenderSystem.getSamplerCache();
        return new TextureSetup(primary, secondary, discriminatorView(elementKey),
                samplerCache.getRepeat(FilterMode.NEAREST), samplerCache.getRepeat(FilterMode.NEAREST), samplerCache.getRepeat(FilterMode.NEAREST));
    }

    public void prepareUniforms() {
        for (ElementRecord element : elements) {
            if (element.uniforms == null) continue;
            Map<String, GpuBufferSlice> slices = elementSlices.get(element.textureSetup);
            for (HudUniform uniform : element.uniforms) {
                CacheKey cacheKey = new CacheKey(element.elementKey, element.pipeline, uniform.getUBOName());
                GpuBufferSlice slice;
                LastWritten last = lastWrittenUniforms.get(cacheKey);
                if (last != null && last.uniform.shouldUseBuffer(uniform)
                        && last.slice != null && !last.slice.buffer().isClosed()) {
                    slice = last.slice;
                } else {
                    DynamicUniformStorage<UniformAdapter> storage = storageMap.computeIfAbsent(
                            uniform.getUBOName(),
                            k -> new DynamicUniformStorage<>(uniform.getUBOName(), uniform.getUBOSize(), 256)
                    );
                    slice = storage.writeUniform(new UniformAdapter(uniform));
                }
                lastWrittenUniforms.put(cacheKey, new LastWritten(uniform, slice));
                if (slices != null) {
                    slices.put(uniform.getUBOName(), slice);
                }
                defaultSlices.put(uniform.getUBOName(), slice);
            }
        }
    }

    /**
     * Binds a default slice for every uniform name uploaded this frame. This guarantees the
     * dev-mode validation of {@code GuiRenderer} always finds a value for every declared
     * uniform; per-draw binding ({@link #bindElementUniforms}) then overrides the values
     * that actually belong to the element being drawn.
     */
    public void bindAllUniforms(RenderPass pass) {
        if (pass == null) return;
        for (Map.Entry<String, GpuBufferSlice> entry : defaultSlices.entrySet()) {
            pass.setUniform(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Per-draw uniform upload, following the same contract as
     * {@code RenderPass.UniformUploader}. The uploader is invoked once per uniform of the
     * element that the given {@link TextureSetup} identity belongs to.
     */
    @FunctionalInterface
    public interface UniformUploader {
        void upload(String uboName, GpuBufferSlice slice);
    }

    public void bindElementUniforms(RenderPass pass, TextureSetup textureSetup, UniformUploader uploader) {
        Map<String, GpuBufferSlice> slices = elementSlices.get(textureSetup);
        if (slices == null) return;
        for (Map.Entry<String, GpuBufferSlice> entry : slices.entrySet()) {
            uploader.upload(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public @NotNull Matrix3x2f currentPose() {
        return new Matrix3x2f(graphics.pose());
    }

    @Override
    public Transforming transform() {
        return Transforming.on(graphics.pose());
    }

    @Override
    public void nextStratum() {
        graphics.nextStratum();
    }

    @Override
    public int guiWidth() {
        return graphics.guiWidth();
    }

    @Override
    public int guiHeight() {
        return graphics.guiHeight();
    }

    @Override
    public void pushScissor(int fromX, int fromY, int toX, int toY) {
        graphics.pushScissor(fromX, fromY, toX, toY);
    }

    @Override
    public void popScissor() {
        graphics.popScissor();
    }

    @Override
    public void drawString(Font font, String text, int x, int y, int color, boolean dropShadow) {
        graphics.drawString(font, text, x, y, color, dropShadow);
    }

    @Override
    public void fill(int fromX, int fromY, int toX, int toY, int color) {
        graphics.fill(fromX, fromY, toX, toY, color);
    }

    private record CacheKey(String elementKey, HudPipeline pipeline, String uboName) {
    }

    private record LastWritten(HudUniform uniform, GpuBufferSlice slice) {
    }

    private record ElementRecord(String elementKey, HudPipeline pipeline, TextureSetup textureSetup, HudUniform[] uniforms) {
    }

    private record UniformAdapter(HudUniform uniform) implements DynamicUniformStorage.DynamicUniform {
        @Override
        public void write(ByteBuffer byteBuffer) {
            uniform.write(byteBuffer);
        }
    }
}
