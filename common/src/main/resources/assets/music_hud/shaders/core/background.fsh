#version 150

layout(std140) uniform MHBasePosition {
    mat4 u_Translation;
    vec3 u_Layout;
};

in vec2 f_Position;

out vec4 fragColor;

void main() {
    // UBO test: green if u_Layout has valid halfWidth, red if zero, blue otherwise
    float hw = u_Layout[0];
    if (hw < -0.5) {
        fragColor = vec4(0.0, 1.0, 0.0, 1.0); // green = UBO data arrived
    } else if (hw > 0.5) {
        fragColor = vec4(1.0, 0.0, 0.0, 1.0); // red = positive but unexpected
    } else {
        fragColor = vec4(0.0, 0.0, 1.0, 1.0); // blue = zero or near-zero
    }
}
