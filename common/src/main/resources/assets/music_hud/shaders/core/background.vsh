#version 150

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

// u_Layout: (halfWidth, halfHeight, cornerRadius)
layout(std140) uniform MHPosition {
    mat4 u_Translation;
    vec3 u_Layout;
};

in vec3 Position;

out vec2 f_Position;

void main() {
    f_Position = Position.xy;
    vec4 localPos = u_Translation * vec4(Position, 1.0);
    gl_Position = ProjMat * ModelViewMat * vec4(localPos.xy, Position.z, 1.0);
}
