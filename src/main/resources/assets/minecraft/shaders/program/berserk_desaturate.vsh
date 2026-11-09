#version 150

// Fullscreen quad for the berserk filter. Same as vanilla's post vertex
// shaders, except texCoord is clamped to the last half texel: at the very
// edge the fragment shader then samples the screen itself instead of
// whatever is outside it (and it keeps InSize a live uniform, which the
// pass expects to find).

in vec4 Position;

uniform mat4 ProjMat;
uniform vec2 InSize;
uniform vec2 OutSize;

out vec2 texCoord;

void main() {
    vec4 outPos = ProjMat * vec4(Position.xy, 0.0, 1.0);
    gl_Position = vec4(outPos.xy, 0.2, 1.0);

    vec2 halfTexel = 0.5 / InSize;
    texCoord = clamp(Position.xy / OutSize, halfTexel, 1.0 - halfTexel);
}
