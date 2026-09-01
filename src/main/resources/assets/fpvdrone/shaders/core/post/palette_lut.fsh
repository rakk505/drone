#version 150

// Palette LUT — maps grayscale thermal values to color via 1D texture lookup.
// Sampler0 = grayscale thermal input, Sampler1 = 256x1 palette LUT.
// Req 14.1, 14.2, 14.3, 14.4

uniform sampler2D Sampler0;  // thermal grayscale
uniform sampler2D Sampler1;  // 256x1 palette LUT

in vec2 texCoord;
out vec4 fragColor;

void main() {
    float T = texture(Sampler0, texCoord).r;

    // Look up color from the 1D palette texture.
    // UV.x = temperature (0-1), UV.y = 0.5 (center of 1-pixel-tall texture)
    vec3 color = texture(Sampler1, vec2(T, 0.5)).rgb;

    fragColor = vec4(color, 1.0);
}
