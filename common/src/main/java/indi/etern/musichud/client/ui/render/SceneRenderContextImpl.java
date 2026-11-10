package indi.etern.musichud.client.ui.render;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import indi.etern.musichud.MusicHud;
import indi.etern.musichud.client.ui.render.gpu.blaze3d.SceneTextureAdapters;
import indi.etern.musichud.client.ui.render.gpu.blaze3d.VanillaScenePipeline;
import indi.etern.musichud.client.ui.render.pipeline.*;
import indi.etern.musichud.client.ui.render.text.GradientSpec;
import indi.etern.musichud.client.ui.render.text.GradientTextRenderer;
import indi.etern.musichud.client.ui.render.text.GradientTextRenderState;
import indi.etern.musichud.client.ui.render.text.TextEdgeFade;
import lombok.Getter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.DynamicUniformStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;

import java.nio.ByteBuffer;
import java.util.*;

public class SceneRenderContextImpl implements SceneRenderContext {
    private static final RenderStateUtil UNIFORM_WRITER = new RenderStateUtil();
    @Getter
    private static SceneRenderContext current;

    private final Map<RenderLayer, Map<CacheKey, DynamicUniformStorage<UniformAdapter>>> storageMap =
            new IdentityHashMap<>();
    private final Map<RenderLayer, Map<CacheKey, LastWritten>> lastWrittenUniforms =
            new IdentityHashMap<>();

    private final List<ElementRecord> elements = new ArrayList<>();
    private final Map<TextureSetup, Map<String, GpuBufferSlice>> elementSlices = new IdentityHashMap<>();
    private final Map<String, GpuBufferSlice> defaultSlices = new HashMap<>();

    private final Deque<ScreenRectangle> scissorStack = new ArrayDeque<>();

    private SceneGraphics graphics;
    private long frameTimeMillis;
    private long frameTimeNanos;

    public SceneRenderContextImpl() {
        current = this;
    }

    @Override
    public void beginFrame(SceneGraphics graphics) {
        elements.clear();
        scissorStack.clear();
        this.graphics = graphics;
        frameTimeMillis = System.currentTimeMillis();
        frameTimeNanos = System.nanoTime();
    }

    @Override
    public void endFrame() {
        RenderStats.endTextFrame();
        long startNanos = System.nanoTime();
        prepareUniforms();
        RenderStats.addUniformNanos(System.nanoTime() - startNanos);
        // The offscreen passes recorded during scene.render() uploaded their uniforms into ring
        // buffers; advance them now so the next frame never overwrites an in-flight slice.
        RenderRuntime.getInstance().endSceneFrame();
    }

    @Override
    public void endGuiFrame() {
        elements.clear();
        elementSlices.clear();
        defaultSlices.clear();
        scissorStack.clear();
        for (Map<CacheKey, DynamicUniformStorage<UniformAdapter>> ownerStorages : storageMap.values()) {
            for (DynamicUniformStorage<?> storage : ownerStorages.values()) {
                storage.endFrame();
            }
        }
        RenderRuntime.getInstance().processPendingGpuRecreate();
    }

    @Override
    public long frameTimeMillis() {
        return frameTimeMillis;
    }

    @Override
    public long frameTimeNanos() {
        return frameTimeNanos;
    }

    @Override
    public SceneGraphics graphics() {
        return graphics;
    }

    @Override
    public void submit(RenderLayer owner, SceneElementRenderState state) {
        Objects.requireNonNull(owner, "owner");
        TextureSetup textureSetup = toVanillaTextureSetup(state.textureSetup(), owner);
        SceneGuiElementRenderState element = new SceneGuiElementRenderState(
                ((VanillaScenePipeline) state.pipeline()).renderPipeline(),
                textureSetup,
                state.pose(),
                state.width(),
                state.height(),
                state.bounds()
        );
        UNIFORM_WRITER.submitGuiElementRenderState(((VanillaSceneGraphics) graphics).vanilla(), element);
        elementSlices.put(textureSetup, new HashMap<>());
        elements.add(new ElementRecord(owner, state.pipeline(), textureSetup, state.uniforms()));
    }

    /** Breaks GUI batching between render layers. */
    private final Map<RenderLayer, GpuTextureView> discriminatorViews =
            new IdentityHashMap<>();
    private int discriminatorSerial;

    private GpuTextureView discriminatorView(RenderLayer owner) {
        return discriminatorViews.computeIfAbsent(owner, layer -> {
            String type = layer.getClass().getSimpleName();
            if (type.isEmpty()) {
                type = layer.getClass().getName();
            }
            GpuDevice device = RenderSystem.getDevice();
            GpuTexture texture = device.createTexture(
                    "music_hud_element_" + type + "_" + discriminatorSerial++,
                    GpuTexture.USAGE_TEXTURE_BINDING,
                    TextureFormat.RGBA8, 1, 1, 1, 1);
            return device.createTextureView(texture);
        });
    }

    private TextureSetup toVanillaTextureSetup(SceneTextureSetup setup, RenderLayer owner) {
        GpuTextureView primary = setup.primary() == null ? null : SceneTextureAdapters.view(setup.primary());
        GpuTextureView secondary = setup.secondary() == null ? null : SceneTextureAdapters.view(setup.secondary());
        return new TextureSetup(primary, secondary, discriminatorView(owner));
    }

    public void prepareUniforms() {
        for (ElementRecord element : elements) {
            if (element.uniforms == null) continue;
            Map<String, GpuBufferSlice> slices = elementSlices.get(element.textureSetup);
            Map<CacheKey, LastWritten> ownerCache = lastWrittenUniforms.computeIfAbsent(
                    element.owner, key -> new HashMap<>());
            for (SceneUniform uniform : element.uniforms) {
                CacheKey cacheKey = new CacheKey(element.pipeline, uniform.getUBOName());
                GpuBufferSlice slice;
                LastWritten last = ownerCache.get(cacheKey);
                if (last != null && last.uniform.shouldUseBuffer(uniform)
                        && last.slice != null && !last.slice.buffer().isClosed()) {
                    slice = last.slice;
                } else {
                    DynamicUniformStorage<UniformAdapter> storage = storageMap
                            .computeIfAbsent(element.owner, key -> new HashMap<>())
                            .computeIfAbsent(cacheKey,
                                    key -> new DynamicUniformStorage<>(
                                            uniform.getUBOName(), uniform.getUBOSize(), 256));
                    slice = storage.writeUniform(new UniformAdapter(uniform));
                }
                ownerCache.put(cacheKey, new LastWritten(uniform, slice));
                if (slices != null) {
                    slices.put(uniform.getUBOName(), slice);
                }
                defaultSlices.put(uniform.getUBOName(), slice);
            }
        }
    }

    public void bindAllUniforms(RenderPass pass) {
        if (pass == null) return;
        for (Map.Entry<String, GpuBufferSlice> entry : defaultSlices.entrySet()) {
            pass.setUniform(entry.getKey(), entry.getValue());
        }
    }

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
        ScreenRectangle rectangle = new ScreenRectangle(fromX, fromY, toX - fromX, toY - fromY);
        ScreenRectangle parent = scissorStack.peek();
        if (parent == null) {
            scissorStack.push(rectangle);
        } else {
            ScreenRectangle intersection = parent.intersection(rectangle);
            scissorStack.push(intersection == null ? ScreenRectangle.empty() : intersection);
        }
        graphics.pushScissor(fromX, fromY, toX, toY);
    }

    @Override
    public void popScissor() {
        if (!scissorStack.isEmpty()) {
            scissorStack.pop();
        }
        graphics.popScissor();
    }

    @Nullable
    public ScreenRectangle currentScissor() {
        return scissorStack.peek();
    }

    @Override
    public void drawString(Font font, String text, int x, int y, int color, boolean dropShadow) {
        if (dropShadow) {
            long textStartNanos = System.nanoTime();
            try {
                graphics.drawString(font, text, x, y, color, dropShadow);
            } finally {
                RenderStats.addTextNanos(System.nanoTime() - textStartNanos);
            }
            return;
        }
        drawGradientString(font, text, x, y, GradientSpec.flat(color), 1f, TextEdgeFade.NONE);
    }

    @Override
    public void drawGradientString(Font font, String text, int x, int y, GradientSpec gradient, float alpha,
                                   TextEdgeFade fade) {
        if (font == null || text == null || text.isEmpty() || gradient == null || alpha <= 0f) {
            return;
        }
        if (fade == null) {
            fade = TextEdgeFade.NONE;
        }
        // Text is the only per-frame work outside the background chain that can take milliseconds
        // (layout + glyph geometry), so it gets its own bucket in the phase report.
        long textStartNanos = System.nanoTime();
        try {
            List<GradientTextRenderState> states = null;
            try {
                states = GradientTextRenderer.build(font, text, x, y, gradient, alpha, fade,
                        currentScissor(), currentPose());
            } catch (Throwable t) {
                MusicHud.getLogger(SceneRenderContextImpl.class).debug("Gradient text geometry failed", t);
            }
            if (states == null || states.isEmpty()) {
                graphics.drawString(font, text, x, y, gradient.sample(0.5f, alpha), false);
                return;
            }
            for (GradientTextRenderState state : states) {
                UNIFORM_WRITER.submitGlyphRenderState(((VanillaSceneGraphics) graphics).vanilla(), state);
            }
        } finally {
            RenderStats.addTextNanos(System.nanoTime() - textStartNanos);
        }
    }

    @Override
    public void fill(int fromX, int fromY, int toX, int toY, int color) {
        graphics.fill(fromX, fromY, toX, toY, color);
    }

    private record CacheKey(ScenePipeline pipeline, String uboName) {
    }

    private record LastWritten(SceneUniform uniform, GpuBufferSlice slice) {
    }

    private record ElementRecord(RenderLayer owner, ScenePipeline pipeline, TextureSetup textureSetup, SceneUniform[] uniforms) {
    }

    private record UniformAdapter(SceneUniform uniform) implements DynamicUniformStorage.DynamicUniform {
        @Override
        public void write(@NotNull ByteBuffer byteBuffer) {
            uniform.write(byteBuffer);
        }
    }
}
