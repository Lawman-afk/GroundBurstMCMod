#version 330
#extension GL_ARB_separate_shader_objects : require

// Ground Burst far clouds (DESIGN.md 12.2): the vanilla emissive entity fragment shader (texture x vertex colour), but
// instead of fading into the render-distance fog it is only hazed toward the fog colour and fades out at the clouds'
// own range, like vanilla clouds, so a mushroom cloud stands over the horizon from far beyond the loaded terrain.
// Environmental fog (water, lava, blindness, powder snow) still applies in full.

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:oit.glsl>

uniform sampler2D Sampler0;

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec4 vertexColor;
layout(location = 6) in vec2 texCoord0;

#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    // Cut out the faint fringe so the cloud can write depth (vanilla's own clouds, drawn later, then stay behind it).
    #if !defined(OIT_ALPHA_ONLY) && !defined(OIT_ACCUMULATE)
    if (color.a < 0.16) {
        discard;
    }
    #endif
    float cloudsEnd = max(FogCloudsEnd, FogRenderDistanceEnd * 4.0);
    // Fade out at the clouds' range, starting at 60% of it.
    color.a *= 1.0 - linear_fog_value(sphericalVertexDistance, cloudsEnd * 0.6, cloudsEnd);

    #ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
    #else
    #ifdef OIT_ACCUMULATE
    color = sampleColorForAccumulation(color);
    vec4 fogColor = vec4(FogColor.rgb * color.a, FogColor.a);
    #else
    vec4 fogColor = FogColor;
    #endif
    // Aerial haze: toward the fog colour, but never all the way, out to four render distances.
    float haze = 0.65 * linear_fog_value(cylindricalVertexDistance, FogRenderDistanceStart, FogRenderDistanceEnd * 4.0);
    color.rgb = mix(color.rgb, fogColor.rgb, haze * fogColor.a);
    color = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd,
            1.0e9, 2.0e9, fogColor);
    fragColor = color;
    #endif
}
