#version 150

uniform mat4 u_MVP;

layout(std140) uniform MHBasePosition {
    mat4 u_Translation;
    vec3 u_Layout;
};

in vec3 Position;

out vec2 f_Position;

void main() {
    f_Position = Position.xy;
//    vec4 localPos = u_Translation * vec4(Position, 1.0);
//    gl_Position = u_MVP * vec4(localPos.xy, Position.z, 1.0);
    gl_Position = u_MVP * vec4(Position, 1.0);
}
