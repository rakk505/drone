#version 150

// Vertex shader for thermal block rendering (solid, cutout, translucent variants share this)
// Minecraft 1.20.1 BLOCK vertex format: Position, Color, UV0, UV2 (lightmap), Normal

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat3 IViewRotMat;
uniform vec3 ChunkOffset;
uniform sampler2D Sampler2; // lightmap

out vec2 texCoord0;
out vec4 vertexColor;
out vec2 lightmapCoord;
out vec3 worldNormal;
out vec3 worldPos;
out float blockLight;
out float skyLight;

void main() {
    vec3 pos = Position + ChunkOffset;
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    texCoord0 = UV0;
    vertexColor = Color;

    // Pass world position to fragment shader for heat source distance calculation
    worldPos = pos;

    // Lightmap UV: UV2 is in 0-255 range, convert to 0-1 for texture lookup
    lightmapCoord = clamp(vec2(UV2) / 256.0, vec2(0.0), vec2(1.0));

    // Block light is the X component, sky light is Y component (both 0-1 after division)
    blockLight = lightmapCoord.x;
    skyLight = lightmapCoord.y;

    // Pass normal in world space for solar heating calculation
    worldNormal = Normal;
}
