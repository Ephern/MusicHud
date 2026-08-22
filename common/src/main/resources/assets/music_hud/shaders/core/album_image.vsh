#version 150

uniform mat4 ProjMat;
uniform mat4 ModelViewMat;

layout(std140) uniform MHPosition {
    mat4 u_Translation;
    vec3 u_Layout; // (halfWidth, halfHeight, cornerRadius)
};

in vec3 Position;
in vec4 Color;

out vec2 f_Position;
out vec4 f_Color;

void main() {
    f_Position = Position.xy;
    f_Color = Color;

    vec4 localPos = u_Translation * vec4(Position, 1.0);
    gl_Position = ProjMat * ModelViewMat * vec4(localPos.xy, Position.z, 1.0);
}
