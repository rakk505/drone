#version 330

uniform sampler2D InSampler;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    float temperature = texture(InSampler, texCoord).a;
    float heat = smoothstep(0.45, 0.92, temperature) * max(temperature - 0.45, 0.0);
    fragColor = vec4(heat, heat, heat, 1.0);
}
