#version 150

// NETD Noise — dynamic per-frame sensor readout noise.
// Amplitude: 0.015 + 0.01 * (1.0 - T_pixel) — stronger on colder areas.
// Req 13.1, 13.3

uniform sampler2D Sampler0;

uniform int frameCount;
uniform vec2 texSize;

in vec2 texCoord;
out vec4 fragColor;

// Hash function for pseudo-random noise from screen position + frame
float hash(vec2 p, float seed) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33 + seed);
    return fract((p3.x + p3.y) * p3.z);
}

void main() {
    float T = texture(Sampler0, texCoord).r;

    // Per-pixel noise that changes every frame
    vec2 pixelPos = texCoord * texSize;
    float seed = float(frameCount) * 0.7123;
    float noise = hash(pixelPos, seed) * 2.0 - 1.0; // range [-1, 1]

    // NETD amplitude: extremely subtle — real sensors have NETD ~50mK / 100K range = 0.05%.
    // AGC contrast stretch amplifies noise significantly, so keep base very low.
    // Previous values (0.003 + 0.002) were ~10x too high after AGC amplification.
    float amplitude = 0.0005 + 0.0003 * (1.0 - T);
    float result = clamp(T + noise * amplitude, 0.0, 1.0);

    fragColor = vec4(result, result, result, 1.0);
}
