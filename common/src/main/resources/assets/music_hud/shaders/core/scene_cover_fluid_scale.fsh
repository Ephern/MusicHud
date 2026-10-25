#version 150

layout(std140) uniform MHFluidScale {
    vec4 u_Params;
};

uniform sampler2D Sampler0;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 uv = (texCoord - 0.5) / u_Params.x + 0.5;
    fragColor = texture(Sampler0, clamp(uv, 0.0, 1.0));
}
