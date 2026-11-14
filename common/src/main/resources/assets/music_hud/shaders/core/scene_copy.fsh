#version 150

uniform sampler2D Sampler0;

in vec2 texCoord;
out vec4 fragColor;

// Copies a scene target 1:1 in texel space. Every scene target stores row 0 at the top of the
// screen, so a copy must neither flip the rows nor replace the alpha.
void main() {
    fragColor = texture(Sampler0, texCoord);
}
