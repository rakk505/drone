#version 150

// Thermal bloom composite
// Adds the heavily blurred heat signal onto the original framebuffer
// using OpenGL additive blending (dstrgb = one).

uniform sampler2D Sampler0; // Bloom texture
uniform float intensity;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    float bloom = texture(Sampler0, texCoord).r;
    float addedHeat = bloom * intensity;
    
    // Output directly, hardware blending will implicitly do:
    // finalColor = (addedHeat * 1.0) + (dstColor * 1.0)
    fragColor = vec4(addedHeat, addedHeat, addedHeat, 1.0);
}
