#version 150

// u_Params: (1 / sourceWidth, 1 / sourceHeight, spanX, spanY) with the span in source texels
layout(std140) uniform MHArtworkDownsample {
    vec4 u_Params;
};

uniform sampler2D Sampler0;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 texel = u_Params.xy;
    // 2x2 taps at the sub-cell centres: an exact box average for a 2x step.
    vec2 offset = max(u_Params.zw, vec2(1.0)) * 0.25 * texel;
    vec4 sum = vec4(0.0);
    for (int j = -1; j <= 1; j += 2) {
        for (int i = -1; i <= 1; i += 2) {
            vec4 c = texture(Sampler0, texCoord + vec2(float(i), float(j)) * offset);
            sum += vec4(c.rgb * c.a, c.a);
        }
    }
    float alpha = sum.a * 0.25;
    if (alpha <= 1e-5) {
        fragColor = vec4(0.0);
        return;
    }
    fragColor = vec4(sum.rgb * 0.25 / alpha, alpha);
}
