#version 150

layout(std140) uniform MHBlur {
    vec4 u_Source;
    vec4 u_Params;
};

uniform sampler2D Sampler0;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 texelStep = u_Source.xy * u_Params.xy;
    float sigma = max(u_Params.z, 0.25);
    int radius = int(clamp(u_Params.w, 1.0, 64.0));
    vec4 color = vec4(0.0);
    float weightSum = 0.0;
    for (int i = -radius; i <= radius; i++) {
        float offset = float(i) / sigma;
        float weight = exp(-0.5 * offset * offset);
        color += texture(Sampler0, texCoord + texelStep * float(i)) * weight;
        weightSum += weight;
    }
    fragColor = color / max(weightSum, 1e-5);
}
