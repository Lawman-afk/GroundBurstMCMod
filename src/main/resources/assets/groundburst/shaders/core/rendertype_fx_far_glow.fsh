#version 150

// Ground Burst's far glow on 1.21.1 (main's DESIGN.md 21.10): a nuclear flash's light over the horizon, drawn just past
// the loaded terrain. Vanilla's emissive entity fragment shader ("eyes": texture x vertex colour) without the cloud's
// cutout and without the render-distance fog (it stands where the fog is thickest), only hazed a little toward the
// fog's colour; added as the glow is.

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform vec4 FogColor;

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    color.rgb = mix(color.rgb, FogColor.rgb, 0.3 * FogColor.a);
    fragColor = color;
}
