#version 150

// Fragment shader for thermal sky rendering.
// Outputs near-black (0.02) representing radiative cold at 8-14um LWIR.
// No sun or moon — germanium lenses block visible wavelengths.

out vec4 fragColor;

void main() {
    // Radiative cold sky temperature
    float T_sky = 0.02;
    fragColor = vec4(T_sky, T_sky, T_sky, 1.0);
}
