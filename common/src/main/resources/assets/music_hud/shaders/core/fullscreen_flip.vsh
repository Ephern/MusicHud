#version 330
#extension GL_ARB_separate_shader_objects : require

// Mirrors the sampled row so a pass stays orientation-neutral under VulkanMod's
// GL-style negative-height viewport.

layout(location = 0) out vec2 texCoord;

void main() {
    vec2 p = vec2(float((gl_VertexIndex << 1) & 2), float(gl_VertexIndex & 2));
    texCoord = vec2(p.x, 1.0 - p.y);
    gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
}
