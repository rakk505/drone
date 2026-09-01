#version 150

// DDE (Digital Detail Enhancement) — Sobel edge detection with white outlines.
// Adds characteristic thermal imager edge sharpening.
// Req 12.1, 12.2

uniform sampler2D Sampler0;

uniform vec2 texSize;       // texture dimensions in pixels
uniform float ddeStrength;  // edge outline strength (0.15–0.30)

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 texel = 1.0 / texSize;

    // Sample 3x3 neighborhood
    float tl = texture(Sampler0, texCoord + vec2(-texel.x,  texel.y)).r;
    float tc = texture(Sampler0, texCoord + vec2(     0.0,  texel.y)).r;
    float tr = texture(Sampler0, texCoord + vec2( texel.x,  texel.y)).r;
    float ml = texture(Sampler0, texCoord + vec2(-texel.x,      0.0)).r;
    float mc = texture(Sampler0, texCoord).r;
    float mr = texture(Sampler0, texCoord + vec2( texel.x,      0.0)).r;
    float bl = texture(Sampler0, texCoord + vec2(-texel.x, -texel.y)).r;
    float bc = texture(Sampler0, texCoord + vec2(     0.0, -texel.y)).r;
    float br = texture(Sampler0, texCoord + vec2( texel.x, -texel.y)).r;

    // Sobel kernels
    float gx = -tl - 2.0*ml - bl + tr + 2.0*mr + br;
    float gy = -tl - 2.0*tc - tr + bl + 2.0*bc + br;
    float edge = sqrt(gx * gx + gy * gy);

    // Add white edge outlines to the original image
    float result = mc + edge * ddeStrength;
    result = clamp(result, 0.0, 1.0);

    fragColor = vec4(result, result, result, 1.0);
}
