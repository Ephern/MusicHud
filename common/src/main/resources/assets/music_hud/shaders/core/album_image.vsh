#version 150

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

layout(std140) uniform HudAlbumParams {
    mat4 u_Translation;
    vec4 u_RectParam;  // (halfWidth, halfHeight, radius, unused)
    vec3 u_TransitionParam;  // (fadeProgress, nextImageAspect, imageAspect)
    mat4 u_BgColors;
//    float u_Progress; removed after 1.21.11
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