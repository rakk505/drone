#version 150

uniform sampler2D Sampler0;

uniform float entityTemp;   // per-entity temperature (default 0.92 from JSON)
uniform vec4 ColorModulator;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec4 texColor = texture(Sampler0, texCoord0);

    // Alpha from texture, vertex color (invisibility), and ColorModulator
    float alpha = texColor.a * vertexColor.a * ColorModulator.a;
    if (alpha < 0.1) {
        discard;
    }

    float T = entityTemp > 0.01 ? entityTemp : 0.92;
    fragColor = vec4(T, T, T, 1.0);
}
