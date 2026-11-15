#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform MHBlur {
    vec4 u_Source;
    vec4 u_Params;
};

uniform sampler2D Sampler0;

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

void main() {
    vec2 uv = clamp(texCoord, u_Source.xy * 0.5, vec2(1.0) - u_Source.xy * 0.5);
    fragColor = texture(Sampler0, uv);
}
