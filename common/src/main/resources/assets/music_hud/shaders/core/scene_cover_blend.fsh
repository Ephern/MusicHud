#version 150

layout(std140) uniform MHCoverBlend {
    // x: crossfade factor (0 = previous, 1 = current)
    vec4 u_Blend;
};

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 previous = texture(Sampler0, texCoord);
    vec4 current = texture(Sampler1, texCoord);
    fragColor = mix(previous, current, u_Blend.x);
}
