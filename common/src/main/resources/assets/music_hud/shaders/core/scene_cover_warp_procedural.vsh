#version 150

// Attribute locations are bound by HudShaderManager (Position=0, Color=1, UV0=2).
// Do not use layout(location=) here: GLSL 1.50 does not allow it without
// GL_ARB_explicit_attrib_location, and it would clash with the explicit bindings.
in vec3 Position;
in vec2 UV0;
out vec2 texCoord;

void main() {
    texCoord = UV0;
    gl_Position = vec4(Position.xy * 2.0 - 1.0, 0.0, 1.0);
}
