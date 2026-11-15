#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D Sampler0;

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

// Copies a scene target 1:1 in texel space. Every scene target stores row 0 at the top of the
// screen, so a copy must neither flip the rows nor replace the alpha.
void main() {
    fragColor = texture(Sampler0, texCoord);
}
