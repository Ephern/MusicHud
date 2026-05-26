#version 150

in vec2 f_Position;

out vec4 fragColor;

void main() {
    // DEBUG: magenta with position-dependent edge
    // Should be solid magenta rectangle at quad position
    fragColor = vec4(1.0, 0.0, 0.5, 1.0);
}
