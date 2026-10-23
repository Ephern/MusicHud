#version 150

layout(std140) uniform MHCapture {
    vec4 u_SourceRect;
    vec4 u_SourceSize;
};

uniform sampler2D Sampler0;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    float srcX = u_SourceRect.x + texCoord.x * u_SourceRect.z;
    float srcY = u_SourceRect.y + texCoord.y * u_SourceRect.w;

    if (srcX < 0.0 || srcY < 0.0 ||
        srcX >= u_SourceSize.x || srcY >= u_SourceSize.y) {
        fragColor = vec4(0.0);
        return;
    }

    ivec2 size = ivec2(u_SourceSize.xy);
    ivec2 texel = ivec2(
        clamp(int(floor(srcX)), 0, size.x - 1),
        clamp(size.y - 1 - int(floor(srcY)), 0, size.y - 1)
    );

    fragColor = vec4(texelFetch(Sampler0, texel, 0).rgb, 1.0);
}
