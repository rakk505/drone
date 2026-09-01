#version 150

// Shared vertex shader for all thermal post-processing passes.
// Simple passthrough: position + UV.

in vec3 Position;
in vec2 UV0;

uniform mat4 ProjMat;

out vec2 texCoord;

void main() {
    gl_Position = ProjMat * vec4(Position, 1.0);
    texCoord = UV0;
}
