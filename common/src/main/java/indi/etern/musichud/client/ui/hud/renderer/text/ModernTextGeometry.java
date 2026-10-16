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
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.LightTexture;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Geometry source for the Modern UI text engine, using only its public API. Layout comes from
 * {@link TextLayoutEngine#lookupVanillaLayout}, glyph baking and atlas lookup are deferred to
 * draw time (after the engine finished baking this frame's glyphs, so atlas rebuilds cannot
 * invalidate the UVs we captured), and each glyph quad is re-emitted with per-vertex gradient
 * colors, mirroring {@code TextRunRenderState.buildPass}.
 */
public final class ModernTextGeometry {
    private static final Random OBFUSCATED_RANDOM = new Random();

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

        int preferredMode = chooseMode(pose);
        int resLevel = preferredMode == TextRenderType.MODE_SDF_FILL
                ? TextLayoutEngine.adjustPixelDensityForSDF(layout.getCreatedResLevel())
                : layout.getCreatedResLevel();
        float invDensity = 1f / resLevel;
        int fontSize = TextLayoutProcessor.computeFontSize(resLevel);
        boolean directMask = preferredMode == TextRenderType.MODE_NORMAL;

        int[] flags = layout.getGlyphFlags();
        List<GradientTextRenderState> states = new ArrayList<>();
        int start = 0;
        RunKey currentKey = null;
        for (int i = 0; i <= flags.length; i++) {
            RunKey key = i == flags.length ? null : keyOf(layout, flags[i], i, preferredMode);
            if (i > start && (key == null || !key.equals(currentKey))) {
                GradientTextRun run = new ModernTextRun(layout, start, i, fontSize, (float) resLevel, invDensity,
                        directMask, currentKey.pipeline, currentKey.kind, currentKey.bitmapFont,
                        currentKey.isColorEmoji, x, y, totalAdvance);
                states.add(new GradientTextRenderState(pose, run, gradient, alpha, scissor));
                start = i;
            }
            currentKey = key;
        }
        return states;
    }

    private static int chooseMode(Matrix3x2f pose) {
        boolean pureTranslation = Math.abs(pose.m01) < 1e-6f && Math.abs(pose.m10) < 1e-6f
                && Math.abs(pose.m00 - 1f) < 1e-6f && Math.abs(pose.m11 - 1f) < 1e-6f;
        if (pureTranslation) {
            return TextRenderType.MODE_NORMAL;
        }
        return ModernTextRenderer.sAllowSDFTextIn2D ? TextRenderType.MODE_SDF_FILL : TextRenderType.MODE_NORMAL;
    }

    private enum TextureKind {
        FONT,
        EMOJI,
        BITMAP
    }

    private record RunKey(RenderPipeline pipeline, TextureKind kind, @Nullable BitmapFont bitmapFont, boolean isColorEmoji) {
    }

    private static RunKey keyOf(TextLayout layout, int bits, int index, int preferredMode) {
        if ((bits & CharacterStyle.ANY_BITMAP_REPLACEMENT) != 0) {
            if (layout.getFont(index) instanceof BitmapFont bitmapFont) {
                return new RunKey(TextRenderType.getPipelineForGui(TextRenderType.MODE_NORMAL, true),
                        TextureKind.BITMAP, bitmapFont, false);
            }
            return new RunKey(TextRenderType.getPipelineForGui(TextRenderType.MODE_NORMAL, false),
                    TextureKind.EMOJI, null, true);
        }
        return new RunKey(TextRenderType.getPipelineForGui(preferredMode, false),
                TextureKind.FONT, null, false);
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
        public void writeVertices(VertexConsumer consumer, Matrix3x2f pose, GradientSpec gradient, float alpha) {
            int[] glyphIds = layout.getGlyphs();
            float[] positions = layout.getPositions();
            int[] flags = layout.getGlyphFlags();
            float baseline = originY + TextLayout.sBaselineOffset;
            for (int i = start; i < end; i++) {
                GLBakedGlyph glyph = bake(glyphIds[i], flags[i], i);
                if (glyph == null || glyph.x == Integer.MIN_VALUE) {
                    continue;
                }
                int bits = flags[i];
                float rx;
                float ry;
                float w;
                float h;
                boolean fakeItalic = false;
                int ascent = 0;
                if ((bits & CharacterStyle.ANY_BITMAP_REPLACEMENT) != 0) {
                    float scaleFactor;
                    if (!isColorEmoji) {
                        ascent = -glyph.y / TextLayoutEngine.BITMAP_SCALE;
                        scaleFactor = 1f / TextLayoutEngine.BITMAP_SCALE;
                    } else {
                        ascent = TextLayout.STANDARD_BASELINE_OFFSET;
                        scaleFactor = TextLayoutProcessor.sBaseFontSize / GlyphManager.EMOJI_BASE;
                    }
                    fakeItalic = (bits & CharacterStyle.ITALIC_MASK) != 0;
                    rx = originX + positions[i << 1] + glyph.x * scaleFactor;
                    ry = baseline + positions[i << 1 | 1] + glyph.y * scaleFactor;
                    w = glyph.width * scaleFactor;
                    h = glyph.height * scaleFactor;
                } else {
                    rx = originX + positions[i << 1] + glyph.x * invDensity;
                    ry = baseline + positions[i << 1 | 1] + glyph.y * invDensity;
                    w = glyph.width * invDensity;
                    h = glyph.height * invDensity;
                }
                if (directMask) {
                    rx = Math.round(rx * density) * invDensity;
                    ry = Math.round(ry * density) * invDensity;
                }
                int colorLeft;
                int colorRight;
                if (isColorEmoji) {
                    int a = Math.clamp((int) (alpha * 255f), 0, 255);
                    colorLeft = (a << 24) | 0xFFFFFF;
                    colorRight = colorLeft;
                } else {
                    colorLeft = gradient.sample((rx - originX) / totalAdvance, alpha);
                    colorRight = gradient.sample((rx + w - originX) / totalAdvance, alpha);
                }
                float upSkew = 0f;
                float downSkew = 0f;
                if (fakeItalic) {
                    upSkew = 0.25f * ascent;
                    downSkew = 0.25f * (ascent - h);
                }
                consumer.addVertexWith2DPose(pose, rx + upSkew, ry)
                        .setColor(colorLeft).setUv(glyph.u1, glyph.v1).setLight(LightTexture.FULL_BRIGHT);
                consumer.addVertexWith2DPose(pose, rx + downSkew, ry + h)
                        .setColor(colorLeft).setUv(glyph.u1, glyph.v2).setLight(LightTexture.FULL_BRIGHT);
                consumer.addVertexWith2DPose(pose, rx + w + downSkew, ry + h)
                        .setColor(colorRight).setUv(glyph.u2, glyph.v2).setLight(LightTexture.FULL_BRIGHT);
                consumer.addVertexWith2DPose(pose, rx + w + upSkew, ry)
                        .setColor(colorRight).setUv(glyph.u2, glyph.v1).setLight(LightTexture.FULL_BRIGHT);
            }
        }

        @Nullable
        private GLBakedGlyph bake(int glyphId, int bits, int index) {
            GlyphManager manager = GlyphManager.getInstance();
            if ((bits & CharacterStyle.OBFUSCATED_MASK) != 0) {
                GlyphManager.FastCharSet chars = manager.lookupFastChars(layout.getFont(index), fontSize, glyphId);
                if (chars == null || chars.glyphs.isEmpty()) {
                    return null;
                }
                return chars.glyphs.get(OBFUSCATED_RANDOM.nextInt(chars.glyphs.size()));
            }
            return manager.lookupGlyph(layout.getFont(index), fontSize, glyphId);
        }
    }
}
