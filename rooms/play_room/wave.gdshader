shader_type canvas_item;

uniform float waveAmplitude: hint_range(0.0, 1.0) = 0.25;
uniform bool correctVertex = true;
uniform float waveFrequency: hint_range(0.0, 100.0) = 10.0;
uniform bool verticalWave = true;
uniform bool keepTransparency = true;
uniform bool useTime = true;
uniform float waveSpeed: hint_range(0.0, 100.0) = 1.0;
uniform float progress: hint_range(0.0, 1.0) = 0.0;
uniform float firstHalf: hint_range(0.0, 1.0) = 0.5;
uniform float secondHalf: hint_range(0.0, 1.0) = 0.5;
uniform bool freezeFirstHalf = false;
uniform bool freezeSecondHalf = false;

uniform sampler2D screen_texture : hint_screen_texture;

void vertex() {
    if (correctVertex) {
        if (verticalWave) {
            VERTEX = vec2(VERTEX.x, VERTEX.y * (1.0 + waveAmplitude));
        } else {
            VERTEX = vec2(VERTEX.x * (1.0 + waveAmplitude), VERTEX.y);
        }
    }
}

void fragment() {
    vec2 uv = UV;
    float waveUv = (verticalWave ? uv.x : uv.y);
    float againstWaveUv = (verticalWave ? uv.y : uv.x);
    float freezeFactor = 1.0;
    if (freezeFirstHalf) {
        freezeFactor = waveUv;
    } else if (freezeSecondHalf) {
        freezeFactor = 1.0 - waveUv;
    }

    if (againstWaveUv < firstHalf || againstWaveUv > 1.0 - secondHalf) {
        float wave;
        if (useTime) {
            wave = freezeFactor * sin(waveUv * waveFrequency + TIME * waveSpeed) * (waveAmplitude / 4.0);
        } else {
            wave = freezeFactor * sin(waveUv * waveFrequency + progress * 2.0 * PI) * (waveAmplitude / 4.0);
        }

        // Get the screen texture size.
        vec2 screen_size = vec2(textureSize(screen_texture, 0));
        // In the original shader TEXTURE_PIXEL_SIZE.x / TEXTURE_PIXEL_SIZE.y equals (1/w)/(1/h)= h/w.
        vec2 aspect = vec2(screen_size.y / screen_size.x, 1.0);
        vec2 center = 0.5 * aspect;

        // Adjust the uv coordinates using the wave offset.
        uv -= center;
        if (verticalWave) {
            uv.y += wave;
            uv.y += uv.y * waveAmplitude;
        } else {
            uv.x += wave;
            uv.x += uv.x * waveAmplitude;
        }
        uv += center;
        // Clamp UVs to prevent wrapping.
        uv = clamp(uv, vec2(0.0), vec2(1.0));
        againstWaveUv = (verticalWave ? uv.y : uv.x);

        // Sample the screen texture.
        vec4 texColor = texture(screen_texture, uv);
        if (keepTransparency) {
            if (againstWaveUv <= 0.0 || againstWaveUv >= 1.0) {
                COLOR.a = 0.0;
            } else {
                COLOR = texColor;
            }
        } else {
            COLOR = texColor;
        }
    }
}
