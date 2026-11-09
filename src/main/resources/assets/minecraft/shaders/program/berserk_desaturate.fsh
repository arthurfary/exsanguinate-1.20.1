#version 150

// BERSERK FILTER - drains the colour out of the screen, but blood stays red.
//
// Everything is pulled all the way to grey (Desaturation = 1.0), except
// pixels that are mostly red: those keep their colour so blood, the blade's
// glow and the blood vignette still read as red.
//
// Intensity is 0.0..1.0 and is animated by ColorFilter (1 s fade in,
// 2 s fade out), which also gives the smooth transition the vanilla
// desaturate shader could not do.

uniform sampler2D DiffuseSampler;

in vec2 texCoord;

uniform vec3 Gray;
uniform float Desaturation;
uniform float Intensity;

out vec4 fragColor;

void main() {
    vec4 inTexel = texture(DiffuseSampler, texCoord);
    vec3 color = inTexel.rgb;

    float luma = dot(color, Gray);

    // How red this pixel is: 1.0 = pure red, 0.0 = grey or any other hue.
    // Uses the red in excess of the strongest other channel, relative to the
    // pixel's brightness, so dark blood counts as much as bright blood.
    float redness = clamp((color.r - max(color.g, color.b)) / max(color.r, 0.0001), 0.0, 1.0);

    // Fully grey for everything, none of it for red pixels, all of it scaled
    // by the fade.
    float amount = Desaturation * Intensity * (1.0 - redness);
    vec3 outColor = mix(color, vec3(luma), amount);

    fragColor = vec4(outColor, 1.0);
}
