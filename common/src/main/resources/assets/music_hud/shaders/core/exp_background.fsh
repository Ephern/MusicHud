#version 150

layout(std140) uniform MHPosition {
    mat4 u_Translation;
    vec4 u_Style;
};

out vec4 fragColor;

void main() {
    fragColor = vec4(0.0, 0.0, 0.0, (128.0 / 255.0) * u_Style.a);
}
