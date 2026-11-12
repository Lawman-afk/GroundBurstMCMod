#version 330

// Ground Burst far clouds (DESIGN.md 12.2): the vanilla emissive entity fragment shader (texture x vertex colour), but
// instead of fading into the render-distance fog it is only hazed toward the fog colour and fades out at the clouds'
// own range, like vanilla clouds, so a mushroom cloud stands over the horizon from far beyond the loaded terrain.
// Environmental fog (water, lava, blindness, powder snow) still applies in full. (Minecraft 26.1.2's shader language:
// no order-independent transparency, which main's 26.3 version also serves.)

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    // Cut out the faint fringe so the cloud can write depth (vanilla's own clouds, drawn later, then stay behind it). A far
    // glow (FX_SOFT: a nuclear flash over the horizon, DESIGN.md 21.10) adds its light, writes no depth and keeps its fringe.
    #ifndef FX_SOFT
    if (color.a < 0.16) {
        discard;
    }
    #endif
    float cloudsEnd = max(FogCloudsEnd, FogRenderDistanceEnd * 4.0);
    // Fade out at the clouds' range, starting at 60% of it.
    color.a *= 1.0 - linear_fog_value(sphericalVertexDistance, cloudsEnd * 0.6, cloudsEnd);
    // Aerial haze: toward the fog colour, but never all the way, out to four render distances.
    float haze = 0.65 * linear_fog_value(cylindricalVertexDistance, FogRenderDistanceStart, FogRenderDistanceEnd * 4.0);
    color.rgb = mix(color.rgb, FogColor.rgb, haze * FogColor.a);
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd,
            1.0e9, 2.0e9, FogColor);
}
