package indi.etern.musichud.client.ui.hud.renderer.text;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;
import icyllis.modernui.mc.text.BitmapFont;
import icyllis.modernui.mc.text.CharacterStyle;
import icyllis.modernui.mc.text.GLBakedGlyph;
import icyllis.modernui.mc.text.GlyphManager;
import icyllis.modernui.mc.text.ModernTextRenderer;
import icyllis.modernui.mc.text.TextLayout;
import icyllis.modernui.mc.text.TextLayoutEngine;
import icyllis.modernui.mc.text.TextLayoutProcessor;
import icyllis.modernui.mc.text.TextRenderType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.LinkedHashSet;
import java.util.Random;
import java.util.Set;

import static org.lwjgl.opengl.GL11.GL_LINEAR;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL33C.glBindSampler;
import static org.lwjgl.opengl.GL33C.glGenSamplers;
import static org.lwjgl.opengl.GL33C.glSamplerParameteri;

/**
 * Emits HUD text as our own glyph quads on Minecraft 1.21.1's immediate render system,
 * mirroring the newer branches' {@code ModernTextGeometry} but targeting
 * {@link MultiBufferSource} batches instead of the GuiRenderer element states.
 * <p>
 * The engine's own GUI path ({@code TextLayout.drawText}) aligns every glyph to the screen
 * pixel grid <i>after</i> adding the animated text origin, so during a marquee every glyph
 * snaps on its own schedule and the spacing pulses (the jitter this class fixes). Here the
 * quads are snapped in layout space, the origin is added afterwards, and one texel of quad/UV
 * bleed makes the hardware linear sampling smooth at the edges.
 * <p>
 * Sampling cannot be changed on the atlas texture itself (Modern UI keeps its A8 atlas at
 * NEAREST), so a linear GL sampler object is bound around the batches this class flushes —
 * the same mechanism the engine uses for its SDF render types. That replaces the newer
 * branches' custom fragment shader: at 1:1 device density a base-level bilinear tap is what
 * makes sub-pixel motion smooth instead of stepping texels.
 * <p>
 * Mode selection, bake density and the bitmap/emoji metric math are ported one-to-one from
 * {@code ModernTextRenderer.drawText} and {@code TextLayout.drawText} so the output stays
 * visually identical to the engine apart from the snapping and filtering change. Shadows,
 * underlines, strikethrough and text backgrounds are intentionally unsupported; the HUD never
 * requests them.
 */
public final class LegacyTextGeometry {
    private static final Random OBFUSCATED_RANDOM = new Random();
    private static final float EPSILON = 1e-5f;

    /**
     * GL sampler object used while our text batches are flushed; {@code 0} means "not created
     * yet". Only ever touched on the render thread.
     */
    private static int linearSampler = 0;

    private LegacyTextGeometry() {
    }

    /**
     * Draws {@code text} into the given buffer source with per-vertex gradient colors and
     * flushes the batches it touched itself, while the caller keeps {@code ColorModulator}
     * neutral.
     *
     * @return {@code false} if the Modern UI text engine is unavailable or the layout cannot be
     *         prepared; nothing has been written in that case and the caller can safely fall
     *         back to the engine's own drawing path. When {@code true} is returned the text has
     *         been fully handled, possibly drawn as nothing (empty text, dropped scale).
     */
    public static boolean draw(String text, float x, float y, GradientSpec gradient, float alpha,
                               Matrix4f pose, MultiBufferSource.BufferSource source, int packedLight) {
        TextLayout layout;
        DrawSetup setup;
        GLBakedGlyph[] glyphs;
        try {
            TextLayoutEngine engine = TextLayoutEngine.getInstance();
            layout = engine.lookupVanillaLayout(text);
            if (layout.getGlyphs().length == 0) {
                return true;
            }
            setup = resolveDraw(pose, x, y, engine.getResLevel());
            if (setup == null) {
                // flipped or far too small, the engine draws nothing either
                return true;
            }
            // All glyph lookups (which may bake new glyphs and grow the atlas) happen before
            // the first vertex is written: an atlas rebuild must never invalidate UVs that were
            // already copied into a buffer builder.
            glyphs = bakedGlyphs(layout, TextLayoutProcessor.computeFontSize(setup.density()));
        } catch (Throwable t) {
            // The text engine may be disabled (no Modern UI); nothing has been written yet,
            // so the caller can safely use the delegation fallback.
            return false;
        }
        emit(layout, setup, glyphs, gradient, alpha, source, packedLight);
        return true;
    }

    /**
     * Mirrors {@code ModernTextRenderer.drawText}'s GUI mode selection: decides between the
     * direct-mask and SDF atlases, extracts the pose translation into the text origin (so the
     * animated origin can be excluded from snapping) and computes the glyph bake density.
     */
    @Nullable
    private static DrawSetup resolveDraw(Matrix4f pose, float x, float y, int createdResLevel) {
        int mode = chooseMode(pose);
        float uniformScale = 1f;
        if (ModernTextRenderer.sComputeDeviceFontSize
                && ((mode == TextRenderType.MODE_NORMAL && (pose.properties() & Matrix4f.PROPERTY_TRANSLATION) != 0)
                || mode == TextRenderType.MODE_UNIFORM_SCALE)) {
            Matrix4f projection = RenderSystem.getProjectionMatrix();
            if (RenderSystem.getVertexSorting() == VertexSorting.ORTHOGRAPHIC_Z && projection.m23() == 0f) {
                Window window = Minecraft.getInstance().getWindow();
                float projScaleX = (projection.m00() * window.getWidth()) / (2f * createdResLevel);
                // in OpenGL this is negative, in Vulkan this is positive
                float projScaleY = Math.abs((projection.m11() * window.getHeight()) / (2f * createdResLevel));
                if (isApproxEqual(projScaleX, projScaleY)) {
                    // 2D uniform scaling: move the pose translation into the text origin so the
                    // snapping in emit() works on layout-local coordinates, exactly like the
                    // engine does before it snaps (but the engine snaps screen-space afterwards).
                    x += pose.m30() / pose.m00();
                    y += pose.m31() / pose.m11();
                    pose = new Matrix4f(pose);
                    pose.m30(0f);
                    pose.m31(0f);
                    uniformScale = pose.m00() * projScaleX;
                    if (isApproxEqual(uniformScale, 1f)) {
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
                } else {
                    // projection is not uniformly scaled
                    mode = ModernTextRenderer.sAllowSDFTextIn2D
                            ? TextRenderType.MODE_SDF_FILL : TextRenderType.MODE_NORMAL;
                }
            } else {
                // 3D projection
                mode = ModernTextRenderer.sAllowSDFTextIn2D
                        ? TextRenderType.MODE_SDF_FILL : TextRenderType.MODE_NORMAL;
            }
        }
        float density;
        boolean directMask;
        if (mode == TextRenderType.MODE_SDF_FILL) {
            density = TextLayoutEngine.adjustPixelDensityForSDF(createdResLevel);
            directMask = false;
        } else if (mode == TextRenderType.MODE_UNIFORM_SCALE) {
            if (uniformScale <= 0.001f) {
                return null;
            }
            // The engine converts uniform-scale runs to normal ones once the bake density is
            // known; snapping still applies to them, on the scaled grid.
            density = createdResLevel * uniformScale;
            mode = TextRenderType.MODE_NORMAL;
            directMask = true;
        } else {
            density = createdResLevel;
            directMask = true;
        }
        return new DrawSetup(mode, pose, x, y, density, directMask);
    }

    /**
     * Mirrors {@code ModernTextRenderer.chooseMode} for the GUI display mode: pure translation
     * keeps the direct mask, uniformly scaled 2D text is resolved by the device font size block
     * in {@link #resolveDraw}, everything else falls back to SDF when allowed.
     */
    private static int chooseMode(Matrix4f ctm) {
        if (TextLayoutEngine.sCurrentInWorldRendering) {
            return TextRenderType.MODE_SDF_FILL;
        }
        if ((ctm.properties() & Matrix4f.PROPERTY_TRANSLATION) == 0) {
            if (ModernTextRenderer.sComputeDeviceFontSize && ctm.m23() == 0f
                    && isApproxZero(ctm.m01()) && isApproxZero(ctm.m03())
                    && isApproxZero(ctm.m10()) && isApproxZero(ctm.m13())
                    && isApproxEqual(ctm.m33(), 1f)
                    && isApproxEqual(ctm.m00(), ctm.m11())) {
                return TextRenderType.MODE_UNIFORM_SCALE;
            }
            if (ModernTextRenderer.sAllowSDFTextIn2D) {
                return TextRenderType.MODE_SDF_FILL;
            }
        }
        // pure translation, or fallback
        return TextRenderType.MODE_NORMAL;
    }

    private static boolean isApproxZero(float value) {
        return Math.abs(value) <= EPSILON;
    }

    private static boolean isApproxEqual(float a, float b) {
        return Math.abs(a - b) <= EPSILON;
    }

    /**
     * Bakes every glyph of the layout at the given font size, exactly like
     * {@code TextLayout.prepareGlyphs}: obfuscated code points bake to their fast char set.
     */
    private static GLBakedGlyph[] bakedGlyphs(TextLayout layout, int fontSize) {
        int[] glyphIds = layout.getGlyphs();
        int[] flags = layout.getGlyphFlags();
        GlyphManager manager = GlyphManager.getInstance();
        GLBakedGlyph[] glyphs = new GLBakedGlyph[glyphIds.length];
        for (int i = 0; i < glyphs.length; i++) {
            glyphs[i] = (flags[i] & CharacterStyle.OBFUSCATED_MASK) != 0
                    ? manager.lookupFastChars(layout.getFont(i), fontSize, glyphIds[i])
                    : manager.lookupGlyph(layout.getFont(i), fontSize, glyphIds[i]);
        }
        return glyphs;
    }

    private static void emit(TextLayout layout, DrawSetup setup, GLBakedGlyph[] baked,
                             GradientSpec gradient, float alpha,
                             MultiBufferSource.BufferSource source, int packedLight) {
        float[] positions = layout.getPositions();
        int[] flags = layout.getGlyphFlags();
        int mode = setup.mode();
        float density = setup.density();
        float invDensity = 1f / density;
        boolean directMask = setup.directMask();
        float totalAdvance = Math.max(1f, layout.getTotalAdvance());
        Set<RenderType> touched = new LinkedHashSet<>();
        RenderType prevRenderType = null;
        VertexConsumer builder = null;
        for (int i = 0; i < baked.length; i++) {
            GLBakedGlyph glyph = glyphAt(baked, i, flags[i]);
            if (glyph == null || glyph.x == Integer.MIN_VALUE) {
                continue;
            }
            int bits = flags[i];
            boolean bitmapReplacement = (bits & CharacterStyle.ANY_BITMAP_REPLACEMENT) != 0;
            int texture;
            int glyphMode = mode;
            boolean isBitmapFont = false;
            boolean isColorEmoji = false;
            boolean fakeItalic = false;
            int ascent = 0;
            float localX;
            float localY;
            float w;
            float h;
            if (bitmapReplacement) {
                // Bitmap fonts and color emoji draw 1:1 from their own atlases, using the same
                // metric math as the engine's GUI text path.
                float scaleFactor;
                if (layout.getFont(i) instanceof BitmapFont bitmapFont) {
                    texture = GlyphManager.getInstance().getCurrentTexture(bitmapFont);
                    ascent = bitmapFont.getAscent();
                    scaleFactor = 1f / TextLayoutEngine.BITMAP_SCALE;
                    isBitmapFont = true;
                } else {
                    texture = GlyphManager.getInstance().getEmojiTexture();
                    ascent = TextLayout.STANDARD_BASELINE_OFFSET;
                    scaleFactor = TextLayoutProcessor.sBaseFontSize / GlyphManager.EMOJI_BASE;
                    isColorEmoji = true;
                }
                fakeItalic = (bits & CharacterStyle.ITALIC_MASK) != 0;
                localX = positions[i << 1] + glyph.x * scaleFactor;
                localY = TextLayout.sBaselineOffset + positions[i << 1 | 1] + glyph.y * scaleFactor;
                w = glyph.width * scaleFactor;
                h = glyph.height * scaleFactor;
                glyphMode = TextRenderType.MODE_NORMAL;
            } else {
                localX = positions[i << 1] + glyph.x * invDensity;
                localY = TextLayout.sBaselineOffset + positions[i << 1 | 1] + glyph.y * invDensity;
                w = glyph.width * invDensity;
                h = glyph.height * invDensity;
                texture = GlyphManager.getInstance().getFontTexture();
            }
            // Snapping happens in layout space: the animated text origin must not take part in
            // it, otherwise every glyph snaps on its own schedule and the glyph spacing pulses
            // while the text scrolls (the marquee jitter this class fixes).
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
            float rx = setup.originX() + localX;
            float ry = setup.originY() + localY;
            float u1 = glyph.u1;
            float v1 = glyph.v1;
            float u2 = glyph.u2;
            float v2 = glyph.v2;
            // One texel of bleed (position + UV) so the linear sampler never clips at the quad
            // edge; the atlas reserves a border around every glyph.
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
            RenderType renderType = isBitmapFont
                    ? TextRenderType.getOrCreate(texture, Font.DisplayMode.NORMAL, true)
                    : TextRenderType.getOrCreate(texture, glyphMode);
            if (renderType != prevRenderType) {
                prevRenderType = renderType;
                builder = source.getBuffer(renderType);
                touched.add(renderType);
            }
            builder.addVertex(setup.pose(), rx + upSkew, ry, 0f)
                    .setColor(colorLeft).setUv(u1, v1).setLight(packedLight);
            builder.addVertex(setup.pose(), rx + downSkew, ry + h, 0f)
                    .setColor(colorLeft).setUv(u1, v2).setLight(packedLight);
            builder.addVertex(setup.pose(), rx + w + downSkew, ry + h, 0f)
                    .setColor(colorRight).setUv(u2, v2).setLight(packedLight);
            builder.addVertex(setup.pose(), rx + w + upSkew, ry, 0f)
                    .setColor(colorRight).setUv(u2, v1).setLight(packedLight);
        }
        if (!touched.isEmpty()) {
            // The A8 glyph atlas stays NEAREST (that is what keeps pixel-aligned text crisp),
            // so the linear filtering needed for smooth sub-pixel motion comes from a sampler
            // object bound for this flush. It overrides the texture's own parameters exactly
            // like the sampler object the engine binds for its SDF render types.
            bindLinearSampler();
            try {
                for (RenderType renderType : touched) {
                    source.endBatch(renderType);
                }
            } finally {
                unbindLinearSampler();
            }
        }
    }

    @Nullable
    private static GLBakedGlyph glyphAt(GLBakedGlyph[] baked, int index, int bits) {
        GLBakedGlyph glyph = baked[index];
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

    private static void bindLinearSampler() {
        if (linearSampler == 0) {
            linearSampler = glGenSamplers();
            glSamplerParameteri(linearSampler, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glSamplerParameteri(linearSampler, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glSamplerParameteri(linearSampler, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glSamplerParameteri(linearSampler, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        }
        glBindSampler(0, linearSampler);
    }

    private static void unbindLinearSampler() {
        glBindSampler(0, 0);
    }

    private record DrawSetup(int mode, Matrix4f pose, float originX, float originY,
                             float density, boolean directMask) {
    }
}
