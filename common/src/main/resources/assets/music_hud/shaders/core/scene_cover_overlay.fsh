#version 150

layout(std140) uniform MHOverlay {
    // x: master fluid opacity
    vec4 u_Params;
};

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 scene = texture(Sampler0, texCoord);
    vec4 cover = texture(Sampler1, texCoord);
    float a = cover.a * u_Params.x;
    fragColor = vec4(scene.rgb * (1.0 - a) + cover.rgb * u_Params.x, 1.0);
}
