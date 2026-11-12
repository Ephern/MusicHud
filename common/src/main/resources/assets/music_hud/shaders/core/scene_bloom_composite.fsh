#version 150

// u_Params: (strength, curve, unused, unused)
// u_Tint:   (r, g, b, a)
layout(std140) uniform MHBloom {
    vec4 u_Params;
    vec4 u_Tint;
};

uniform sampler2D Sampler0;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 bloom = texture(Sampler0, texCoord);
    float a = clamp(bloom.a, 0.0, 1.0);

    // Soft shoulder that preserves full coverage at a = 1 while lifting the tail.
    float curve = max(u_Params.y, 0.001);
    a = (1.0 - exp(-curve * a)) / (1.0 - exp(-curve));
    a = clamp(a * u_Params.x, 0.0, 1.0);

    fragColor = vec4(u_Tint.rgb * a, a * u_Tint.a);
}
