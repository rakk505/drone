#version 150

// AGC (Auto Gain Control) — contrast stretch pass.
// Maps the smoothed min/max temperature range to full 0–1 output.

uniform sampler2D Sampler0;

uniform float agcMin;  // smoothed minimum temperature in frame
uniform float agcMax;  // smoothed maximum temperature in frame

in vec2 texCoord;
out vec4 fragColor;

void main() {
    float T = texture(Sampler0, texCoord).r;

    // Linear contrast stretch: map [agcMin, agcMax] → [0, 1]
    float range = agcMax - agcMin;
    float stretched = (T - agcMin) / max(range, 0.01);
    stretched = clamp(stretched, 0.0, 1.0);

    fragColor = vec4(stretched, stretched, stretched, 1.0);
}
