#version 330

uniform sampler2D InSampler;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 stepUv = vec2(0.0, 1.0 / float(textureSize(InSampler, 0).y));
    float value = texture(InSampler, texCoord).r * 0.19648255;
    const float weights[6] = float[](0.17488405, 0.12300011, 0.06831997,
                                     0.02995320, 0.01036696, 0.00283305);
    for (int index = 0; index < 6; ++index) {
        vec2 offset = stepUv * float(index + 1);
        value += (texture(InSampler, texCoord + offset).r
                + texture(InSampler, texCoord - offset).r) * weights[index];
    }
    fragColor = vec4(value, value, value, 1.0);
}
