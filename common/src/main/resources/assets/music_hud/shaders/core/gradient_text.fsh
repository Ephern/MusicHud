#version 150

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    // Manual bilinear filtering: the engine's GUI text sampler is locked to NEAREST, so the
    // four nearest texels are fetched and mixed by hand for smooth sub-pixel motion.
    vec2 texSize = vec2(textureSize(Sampler0, 0));
    vec2 uv = texCoord0 * texSize - 0.5;
    vec2 f = fract(uv);
    vec2 base = (floor(uv) + 0.5) / texSize;
    vec2 texel = 1.0 / texSize;
    vec4 c00 = texture(Sampler0, base);
    vec4 c10 = texture(Sampler0, base + vec2(texel.x, 0.0));
    vec4 c01 = texture(Sampler0, base + vec2(0.0, texel.y));
    vec4 c11 = texture(Sampler0, base + texel);
    vec4 texColor = mix(mix(c00, c10, f.x), mix(c01, c11, f.x), f.y);
    vec4 color = texColor * vertexColor * ColorModulator;
    if (color.a < 0.01) discard;
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance,
            FogEnvironmentalStart, FogEnvironmentalEnd,
            FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
