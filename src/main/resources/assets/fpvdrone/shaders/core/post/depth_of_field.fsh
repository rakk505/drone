#version 150

// Depth-of-field pass — simple separable Gaussian with per-pixel CoC.
//
// Called twice: direction=(1,0) horizontal, direction=(0,1) vertical.
// Sampler0 = color input, Sampler1 = depth buffer.

uniform sampler2D Sampler0;  // color
uniform sampler2D Sampler1;  // depth

uniform vec2 direction;
uniform vec2 texSize;
uniform float focusDistance;
uniform float aperture;
uniform float nearPlane;
uniform float farPlane;

in vec2 texCoord;
out vec4 fragColor;

float linearizeDepth(float d) {
    return (nearPlane * farPlane) / (farPlane - d * (farPlane - nearPlane));
}

// CoC in pixels. Dead-zone around focus distance keeps
// the focused object sharp; blur grows with distance from focus plane.
float computeCoC(float depth) {
    float diff = depth - focusDistance;

    // Dead-zone: ±8% of focus distance is perfectly sharp.
    float deadZone = focusDistance * 0.08;
    float absDiff = abs(diff);
    if (absDiff < deadZone) return 0.0;

    // Excess beyond the dead-zone drives blur.
    // Normalize by focusDistance (not pixel depth) so far objects
    // still produce meaningful CoC values.
    float excess = absDiff - deadZone;
    float coc = excess / max(focusDistance, 1.0) * aperture * texSize.x;

    // Front bokeh weaker (real lens asymmetry)
    if (diff < 0.0) coc *= 0.5;

    return min(coc, 10.0);
}

void main() {
    vec2 texel = 1.0 / texSize;
    vec2 step = direction * texel;

    float centerDepth = linearizeDepth(texture(Sampler1, texCoord).r);

    // Ignore geometry closer than ~1.5 blocks — this is the local drone
    // model which is visible in thermal FPV. Without this, the drone's
    // depth writes cause the DoF shader to blur/focus on the drone itself.
    if (centerDepth < 1.5) {
        fragColor = vec4(texture(Sampler0, texCoord).rgb, 1.0);
        return;
    }

    float coc = computeCoC(centerDepth);
    vec3 sharp = texture(Sampler0, texCoord).rgb;

    // If CoC is negligible, pixel is in focus — return sharp
    if (coc < 0.5) {
        fragColor = vec4(sharp, 1.0);
        return;
    }

    // Variable-width 13-tap Gaussian blur
    vec2 blurStep = step * (coc / 6.0);

    float w[7] = float[](
        0.1964, 0.1747, 0.1225, 0.0676, 0.0294, 0.0100, 0.0027
    );

    float total = w[0];
    vec3 acc = sharp * w[0];

    for (int i = 1; i <= 6; i++) {
        vec2 off = blurStep * float(i);
        acc   += texture(Sampler0, texCoord + off).rgb * w[i];
        acc   += texture(Sampler0, texCoord - off).rgb * w[i];
        total += w[i] * 2.0;
    }
    acc /= total;

    // Smooth transition from sharp to blurred
    float blend = smoothstep(0.5, 3.0, coc);
    fragColor = vec4(mix(sharp, acc, blend), 1.0);
}
