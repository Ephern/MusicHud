package indi.etern.musichud.client.ui.hud.renderer.text;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexConsumer;
import icyllis.modernui.mc.text.BitmapFont;
import icyllis.modernui.mc.text.CharacterStyle;
import icyllis.modernui.mc.text.GLBakedGlyph;
import icyllis.modernui.mc.text.GlyphManager;
import icyllis.modernui.mc.text.ModernTextRenderer;
import icyllis.modernui.mc.text.TextLayout;
import icyllis.modernui.mc.text.TextLayoutEngine;
import icyllis.modernui.mc.text.TextLayoutProcessor;
import icyllis.modernui.mc.text.TextRenderType;
import indi.etern.musichud.client.ui.hud.pipelines.HudRenderPipelines;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.LightTexture;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;

/**
 * Geometry source for the Modern UI text engine, using only its public API. Layout comes from
 * {@link TextLayoutEngine#lookupVanillaLayout}, glyph baking and atlas lookup are deferred to
 * draw time (after the engine finished baking this frame's glyphs, so atlas rebuilds cannot
 * invalidate the UVs we captured), and each glyph quad is re-emitted with per-vertex gradient
 * colors, mirroring {@code TextRunRenderState.buildPass}.
 * <p>
 * Mode selection, device font size and pixel snapping mirror the engine's own GUI text path
 * ({@code MixinGuiTextRenderState.onPrepareText} and {@code TextLayout.prepareTextWithDensity}):
 * uniformly scaled text bakes glyphs at {@code resLevel * scale}. Unlike the engine's own
 * renderer, snapping is performed in layout space (the animated text origin is excluded) so
 * scrolling text keeps rigid glyph spacing, and direct-mask runs are drawn through
 * {@link HudRenderPipelines#GRADIENT_TEXT}, whose fragment shader performs manual bilinear
 * filtering for smooth sub-pixel motion.
 */
public final class ModernTextGeometry {
    private static final Random OBFUSCATED_RANDOM = new Random();

    /**
     * Baked glyph arrays keyed by layout and font size, mirroring {@code TextLayout.getGlyphs}
     * and {@code TextLayout.getGlyphsUniformScale} caches. Layouts are created fresh for every
     * cached text and evicted by the engine's LRU cache, so weak keys follow the same lifetime
     * as the engine's own baked glyph caches. Only accessed on the render thread.
     */
    private static final Map<TextLayout, Map<Integer, GLBakedGlyph[]>> GLYPH_CACHE = new WeakHashMap<>();

    private ModernTextGeometry() {
    }

    public static List<GradientTextRenderState> build(Font font, String text, int x, int y,
                                                      GradientSpec gradient, float alpha,
                                                      @Nullable ScreenRectangle scissor, Matrix3x2f pose) {
        TextLayout layout = TextLayoutEngine.getInstance().lookupVanillaLayout(text);
        float totalAdvance = Math.max(1f, layout.getTotalAdvance());
        if (totalAdvance <= 1f && layout.getGlyphs().length == 0) {
            return List.of();
        }

        int createdResLevel = layout.getCreatedResLevel();
        DrawSetup setup = resolveDraw(pose, x, y, createdResLevel);
        int preferredMode = setup.mode();
        float density;
        if (preferredMode == TextRenderType.MODE_SDF_FILL) {
            density = TextLayoutEngine.adjustPixelDensityForSDF(createdResLevel);
        } else if (preferredMode == TextRenderType.MODE_UNIFORM_SCALE) {
            if (setup.uniformScale() <= 0.001f) {
                // drop if flipped or too small
                return List.of();
            }
            density = createdResLevel * setup.uniformScale();
            preferredMode = TextRenderType.MODE_NORMAL;
        } else {
            density = createdResLevel;
        }
        float invDensity = 1f / density;
        int fontSize = TextLayoutProcessor.computeFontSize(density);
        boolean directMask = preferredMode == TextRenderType.MODE_NORMAL;

        // Bake every glyph now, while this element is merely being submitted: a newly baked
        // glyph can grow or rebuild the font atlas, and that rebuild must never happen while
        // the GUI renderer is already recording this element's vertices, otherwise the UVs
        // written in that pass would no longer match the atlas texture bound for the draw
        // (visible as corrupted, diagonally clipped or entirely missing glyphs).
        bakedGlyphs(layout, fontSize);

        int[] flags = layout.getGlyphFlags();
        List<GradientTextRenderState> states = new ArrayList<>();
        int start = 0;
        RunKey currentKey = null;
        for (int i = 0; i <= flags.length; i++) {
            RunKey key = i == flags.length ? null : keyOf(layout, flags[i], i, preferredMode, directMask);
            if (i > start && (key == null || !key.equals(currentKey))) {
                GradientTextRun run = new ModernTextRun(layout, start, i, fontSize, density, invDensity,
                        directMask, currentKey.pipeline, currentKey.kind, currentKey.bitmapFont,
                        currentKey.isColorEmoji, setup.originX(), setup.originY(), totalAdvance);
                states.add(new GradientTextRenderState(setup.pose(), run, gradient, alpha, scissor));
                start = i;
            }
            currentKey = key;
        }
        return states;
    }

    /**
     * Mirrors {@code MixinGuiTextRenderState.onPrepareText}: decides the render mode for the
     * given pose, extracts the pose translation into the text origin (so that per-vertex
     * snapping can align quads to the device pixel grid) and computes the residual uniform
     * scale used to bake glyphs at the device font size.
     */
    private static DrawSetup resolveDraw(Matrix3x2f pose, float x, float y, int createdResLevel) {
        Matrix3x2f ctm = new Matrix3x2f(pose);
        int mode;
        boolean pureTranslation;
        if (!approxZero(ctm.m01) || !approxZero(ctm.m10)
                || !approxEqual(ctm.m00, 1f) || !approxEqual(ctm.m11, 1f)) {
            pureTranslation = false;
            if (ModernTextRenderer.sComputeDeviceFontSize
                    && approxZero(ctm.m01) && approxZero(ctm.m10)
                    && approxEqual(ctm.m00, ctm.m11)) {
                mode = TextRenderType.MODE_UNIFORM_SCALE;
            } else if (ModernTextRenderer.sAllowSDFTextIn2D) {
                mode = TextRenderType.MODE_SDF_FILL;
            } else {
                mode = TextRenderType.MODE_NORMAL;
            }
        } else {
            mode = TextRenderType.MODE_NORMAL;
            pureTranslation = true;
        }
        float uniformScale = 1f;
        if (ModernTextRenderer.sComputeDeviceFontSize
                && (pureTranslation || mode == TextRenderType.MODE_UNIFORM_SCALE)) {
            // extract the translation vector for snapping to pixel grid
            x += ctm.m20 / ctm.m00;
            y += ctm.m21 / ctm.m11;
            // the pose translation is moved into the text origin
            ctm.m20 = 0f;
            ctm.m21 = 0f;
            uniformScale = ctm.m00;
            if (approxEqual(uniformScale, 1f)) {
                mode = TextRenderType.MODE_NORMAL;
            } else {
                float upperLimit = Math.max(1f,
                        (float) TextLayoutEngine.sMinPixelDensityForSDF / createdResLevel);
                if (uniformScale <= upperLimit) {
                    // uniform scale smaller and not too large
                    mode = TextRenderType.MODE_UNIFORM_SCALE;
                } else {
                    mode = ModernTextRenderer.sAllowSDFTextIn2D
                            ? TextRenderType.MODE_SDF_FILL : TextRenderType.MODE_NORMAL;
                }
            }
        }
        return new DrawSetup(mode, ctm, x, y, uniformScale);
    }

    private static boolean approxZero(float value) {
        return Math.abs(value) <= 1e-5f;
    }

    private static boolean approxEqual(float a, float b) {
        return Math.abs(a - b) <= 1e-5f;
    }

    private record DrawSetup(int mode, Matrix3x2f pose, float originX, float originY, float uniformScale) {
    }

    private enum TextureKind {
        FONT,
        EMOJI,
        BITMAP
    }

    private record RunKey(RenderPipeline pipeline, TextureKind kind, @Nullable BitmapFont bitmapFont, boolean isColorEmoji) {
    }

    private static RunKey keyOf(TextLayout layout, int bits, int index, int preferredMode, boolean directMask) {
        if ((bits & CharacterStyle.ANY_BITMAP_REPLACEMENT) != 0) {
            if (layout.getFont(index) instanceof BitmapFont bitmapFont) {
                return new RunKey(TextRenderType.getPipelineForGui(TextRenderType.MODE_NORMAL, true),
                        TextureKind.BITMAP, bitmapFont, false);
            }
            return new RunKey(TextRenderType.getPipelineForGui(TextRenderType.MODE_NORMAL, false),
                    TextureKind.EMOJI, null, true);
        }
        // direct-mask runs use our manual-bilinear pipeline; every other mode keeps the engine's
        return new RunKey(directMask ? HudRenderPipelines.GRADIENT_TEXT
                        : TextRenderType.getPipelineForGui(preferredMode, false),
                TextureKind.FONT, null, false);
    }

    /**
     * Bakes (once per layout and font size) every glyph of the layout, exactly like
     * {@code TextLayout.prepareGlyphs}: obfuscated code points bake to their fast char set.
     */
    private static GLBakedGlyph[] bakedGlyphs(TextLayout layout, int fontSize) {
        return GLYPH_CACHE
                .computeIfAbsent(layout, k -> new HashMap<>())
                .computeIfAbsent(fontSize, size -> {
                    int[] glyphIds = layout.getGlyphs();
                    int[] flags = layout.getGlyphFlags();
                    GlyphManager manager = GlyphManager.getInstance();
                    GLBakedGlyph[] glyphs = new GLBakedGlyph[glyphIds.length];
                    for (int i = 0; i < glyphs.length; i++) {
                        glyphs[i] = (flags[i] & CharacterStyle.OBFUSCATED_MASK) != 0
                                ? manager.lookupFastChars(layout.getFont(i), size, glyphIds[i])
                                : manager.lookupGlyph(layout.getFont(i), size, glyphIds[i]);
                    }
                    return glyphs;
                });
    }

    private static final class ModernTextRun implements GradientTextRun {
        private final TextLayout layout;
        private final int start;
        private final int end;
        private final int fontSize;
        private final float density;
        private final float invDensity;
        private final boolean directMask;
        private final RenderPipeline pipeline;
        private final TextureKind kind;
        @Nullable
        private final BitmapFont bitmapFont;
        private final boolean isColorEmoji;
        private final float originX;
        private final float originY;
        private final float totalAdvance;

        ModernTextRun(TextLayout layout, int start, int end, int fontSize, float density, float invDensity,
                      boolean directMask, RenderPipeline pipeline, TextureKind kind,
                      @Nullable BitmapFont bitmapFont, boolean isColorEmoji,
                      float originX, float originY, float totalAdvance) {
            this.layout = layout;
            this.start = start;
            this.end = end;
            this.fontSize = fontSize;
            this.density = density;
            this.invDensity = invDensity;
            this.directMask = directMask;
            this.pipeline = pipeline;
            this.kind = kind;
            this.bitmapFont = bitmapFont;
            this.isColorEmoji = isColorEmoji;
            this.originX = originX;
            this.originY = originY;
            this.totalAdvance = totalAdvance;
        }

        @Override
        public RenderPipeline pipeline() {
            return pipeline;
        }

        @Override
        public GpuTextureView texture() {
            GlyphManager manager = GlyphManager.getInstance();
            return switch (kind) {
                case FONT -> manager.getFontTexture();
                case EMOJI -> manager.getEmojiTexture();
                case BITMAP -> manager.getCurrentTexture(bitmapFont);
            };
        }

        @Override
        public void writeVertices(VertexConsumer consumer, float z, Matrix3x2f pose, GradientSpec gradient, float alpha) {
            GLBakedGlyph[] bakedGlyphs = bakedGlyphs(layout, fontSize);
            float[] positions = layout.getPositions();
            int[] flags = layout.getGlyphFlags();
            for (int i = start; i < end; i++) {
                GLBakedGlyph glyph = glyphAt(bakedGlyphs, i, flags[i]);
                if (glyph == null || glyph.x == Integer.MIN_VALUE) {
                    continue;
                }
                int bits = flags[i];
                boolean bitmapReplacement = (bits & CharacterStyle.ANY_BITMAP_REPLACEMENT) != 0;
                float localX;
                float localY;
                float w;
                float h;
                boolean fakeItalic = false;
                int ascent = 0;
                if (bitmapReplacement) {
                    float scaleFactor;
                    if (!isColorEmoji) {
                        ascent = -glyph.y / TextLayoutEngine.BITMAP_SCALE;
                        scaleFactor = 1f / TextLayoutEngine.BITMAP_SCALE;
                    } else {
                        ascent = TextLayout.STANDARD_BASELINE_OFFSET;
                        scaleFactor = TextLayoutProcessor.sBaseFontSize / GlyphManager.EMOJI_BASE;
                    }
                    fakeItalic = (bits & CharacterStyle.ITALIC_MASK) != 0;
                    localX = positions[i << 1] + glyph.x * scaleFactor;
                    localY = TextLayout.sBaselineOffset + positions[i << 1 | 1] + glyph.y * scaleFactor;
                    w = glyph.width * scaleFactor;
                    h = glyph.height * scaleFactor;
                } else {
                    localX = positions[i << 1] + glyph.x * invDensity;
                    localY = TextLayout.sBaselineOffset + positions[i << 1 | 1] + glyph.y * invDensity;
                    w = glyph.width * invDensity;
                    h = glyph.height * invDensity;
                }
                // Snapping happens in layout space: the animated text origin must not take
                // part in it, otherwise every glyph snaps on its own schedule and the glyph
                // spacing pulses while the text scrolls (the marquee jitter this class fixes).
                if (directMask) {
                    localX = Math.round(localX * density) * invDensity;
                    localY = Math.round(localY * density) * invDensity;
                }
                int colorLeft;
                int colorRight;
                if (isColorEmoji) {
                    int a = Math.clamp((int) (alpha * 255f), 0, 255);
                    colorLeft = (a << 24) | 0xFFFFFF;
                    colorRight = colorLeft;
                } else {
                    colorLeft = gradient.sample(localX / totalAdvance, alpha);
                    colorRight = gradient.sample((localX + w) / totalAdvance, alpha);
                }
                float upSkew = 0f;
                float downSkew = 0f;
                if (fakeItalic) {
                    upSkew = 0.25f * ascent;
                    downSkew = 0.25f * (ascent - h);
                }
                float rx = originX + localX;
                float ry = originY + localY;
                float u1 = glyph.u1;
                float v1 = glyph.v1;
                float u2 = glyph.u2;
                float v2 = glyph.v2;
                // One texel of bleed (position + UV) so the manual bilinear taps never clip
                // at the quad edge; the atlas reserves a border around every glyph.
                if (directMask && !bitmapReplacement) {
                    float du = (u2 - u1) / (float) glyph.width;
                    float dv = (v2 - v1) / (float) glyph.height;
                    rx -= invDensity;
                    ry -= invDensity;
                    w += 2f * invDensity;
                    h += 2f * invDensity;
                    u1 -= du;
                    v1 -= dv;
                    u2 += du;
                    v2 += dv;
                }
                consumer.addVertexWith2DPose(pose, rx + upSkew, ry, z)
                        .setColor(colorLeft).setUv(u1, v1).setLight(LightTexture.FULL_BRIGHT);
                consumer.addVertexWith2DPose(pose, rx + downSkew, ry + h, z)
                        .setColor(colorLeft).setUv(u1, v2).setLight(LightTexture.FULL_BRIGHT);
                consumer.addVertexWith2DPose(pose, rx + w + downSkew, ry + h, z)
                        .setColor(colorRight).setUv(u2, v2).setLight(LightTexture.FULL_BRIGHT);
                consumer.addVertexWith2DPose(pose, rx + w + upSkew, ry, z)
                        .setColor(colorRight).setUv(u2, v1).setLight(LightTexture.FULL_BRIGHT);
            }
        }

        @Nullable
        private static GLBakedGlyph glyphAt(GLBakedGlyph[] bakedGlyphs, int index, int bits) {
            GLBakedGlyph glyph = bakedGlyphs[index];
            if (glyph == null) {
                return null;
            }
            if ((bits & CharacterStyle.OBFUSCATED_MASK) != 0) {
                GlyphManager.FastCharSet chars = (GlyphManager.FastCharSet) glyph;
                if (chars.glyphs.isEmpty()) {
                    return null;
                }
                return chars.glyphs.get(OBFUSCATED_RANDOM.nextInt(chars.glyphs.size()));
            }
            return glyph;
        }
    }
}
