#version 150

uniform mat4 u_MVP;

in vec3 Position;
in vec4 Color;

out vec2 f_Position;
out vec4 f_Color;

void main() {
    f_Position = Position.xy;
    f_Color = Color;
    gl_Position = u_MVP * vec4(Position, 1.0);
}
