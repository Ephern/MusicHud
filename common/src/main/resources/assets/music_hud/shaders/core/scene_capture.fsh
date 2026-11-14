#version 150

// u_SourceRect: (left, top, width, height) in source pixels, top-left origin
// u_SourceSize: (sourceWidth, sourceHeight, downscale, flipY)
layout(std140) uniform MHCapture {
    vec4 u_SourceRect;
    vec4 u_SourceSize;
};

uniform sampler2D Sampler0;

in vec2 texCoord;
out vec4 fragColor;

vec2 clampUv(vec2 uv) {
    return clamp(uv, vec2(0.0), vec2(1.0));
}

void main() {
    float srcX = u_SourceRect.x + texCoord.x * u_SourceRect.z;
    float srcY = u_SourceRect.y + texCoord.y * u_SourceRect.w;

    if (srcX < 0.0 || srcY < 0.0 ||
        srcX >= u_SourceSize.x || srcY >= u_SourceSize.y) {
        fragColor = vec4(0.0);
        return;
    }

    float downscale = max(u_SourceSize.z, 1.0);
    float flipY = u_SourceSize.w;

    if (downscale < 1.5) {
        // Exact 1:1 copy: the destination pixel maps onto exactly one source texel.
        ivec2 size = ivec2(u_SourceSize.xy);
        int row = int(floor(srcY));
        ivec2 texel = ivec2(
            clamp(int(floor(srcX)), 0, size.x - 1),
            clamp(flipY != 0.0 ? size.y - 1 - row : row, 0, size.y - 1)
        );
        fragColor = vec4(texelFetch(Sampler0, texel, 0).rgb, 1.0);
        return;
    }

    // Downscaled capture: average the covered box with four bilinear taps at its quadrant
    // centers. The result only feeds a large blur, so this is well past the detail that survives.
    vec2 size = u_SourceSize.xy;
    vec2 texelSize = vec2(1.0) / size;
    vec2 center = vec2(srcX, srcY) + 0.5 * downscale;
    vec2 offset = 0.25 * downscale * texelSize;
    // flipY converts the main target's row order into the one the rest of the chain expects.
    float centerV = flipY != 0.0 ? 1.0 - center.y / size.y : center.y / size.y;
    vec2 uv = vec2(center.x / size.x, centerV);
    vec3 color = texture(Sampler0, clampUv(uv + vec2(offset.x, -offset.y))).rgb
               + texture(Sampler0, clampUv(uv - offset)).rgb
               + texture(Sampler0, clampUv(uv + offset)).rgb
               + texture(Sampler0, clampUv(uv + vec2(-offset.x, offset.y))).rgb;
    fragColor = vec4(color * 0.25, 1.0);
}
