package indi.etern.musichud.mixin;

import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Read-only access to the raw quad bounds and atlas UVs of a baked vanilla glyph. They are
 * private and not exposed by any public getter, yet they are required to re-emit a glyph quad
 * with per-vertex gradient colors.
 */
@Mixin(BakedGlyph.class)
public interface BakedGlyphAccessor {
    @Accessor("u0")
    float music_hud$u0();

    @Accessor("u1")
    float music_hud$u1();

    @Accessor("v0")
    float music_hud$v0();

    @Accessor("v1")
    float music_hud$v1();

    @Accessor("left")
    float music_hud$left();

    @Accessor("right")
    float music_hud$right();

    @Accessor("up")
    float music_hud$up();

    @Accessor("down")
    float music_hud$down();
}
