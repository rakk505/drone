#version 330

uniform sampler2D InSampler;
uniform sampler2D BloomSampler;

layout(std140) uniform BloomConfig {
    vec4 PaletteIntensity; // palette, intensity, reserved, reserved
};

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 base = texture(InSampler, texCoord);
    float bloom = texture(BloomSampler, texCoord).r * PaletteIntensity.y;
    vec3 result;
    if (PaletteIntensity.x > 0.5 && PaletteIntensity.x < 1.5) {
        result = base.rgb - vec3(bloom);
    } else if (PaletteIntensity.x > 1.5) {
        result = base.rgb + vec3(bloom, bloom * 0.42, bloom * 0.12);
    } else {
        result = base.rgb + vec3(bloom);
    }
    fragColor = vec4(clamp(result, 0.0, 1.0), base.a);
}
