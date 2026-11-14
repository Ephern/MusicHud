#version 150

// Mirrors the sampled row so a pass stays orientation-neutral under VulkanMod's
// GL-style negative-height viewport.

out vec2 texCoord;

void main() {
    vec2 p = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
    texCoord = vec2(p.x, 1.0 - p.y);
    gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
}
