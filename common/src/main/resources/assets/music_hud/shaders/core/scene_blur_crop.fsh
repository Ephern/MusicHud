#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform MHCrop {
    vec4 u_Rect;
};

uniform sampler2D Sampler0;

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

void main() {
    vec2 uv = mix(u_Rect.xy, u_Rect.zw, texCoord);
    fragColor = texture(Sampler0, clamp(uv, 0.0, 1.0));
}
