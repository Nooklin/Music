#pragma once
// Synthetic dance tracks with known beat positions, for the host tests and the offline renderer.
#include <cmath>
#include <cstdint>
#include <cstdlib>
#include <vector>

#include "../mix/types.h"

namespace nooklin::mix::host {

struct Synth {
    std::vector<float> pcm;          // interleaved stereo
    std::vector<double> beatTimes;   // ground truth, seconds
    int sampleRate = 44100;
    long frames() const { return (long)(pcm.size() / 2); }
};

class Noise {
public:
    explicit Noise(uint32_t seed) : s(seed) {}
    float next() {
        s = s * 1664525u + 1013904223u;
        return (float)((int32_t)s) / 2147483648.0f;
    }
private:
    uint32_t s;
};

/**
 * [bpm] tempo, [offset] seconds before the first beat (a downbeat), [seconds] length, [driftPct] tempo change over the
 * whole song in percent, [keyHz] root note of the pad (changes the key), [seed] for the noise.
 * Structure: 8 bars of drums only, then pad and hats join and every 4th bar has a louder pad (phrase start).
 */
inline Synth makeTrack(int sr, double bpm, double offset, double seconds, double driftPct = 0.0, double keyHz = 130.81,
                       uint32_t seed = 1, double jitterMs = 0.0, int introBeats = 32) {
    Synth s;
    s.sampleRate = sr;
    const long n = (long)(seconds * sr);
    s.pcm.assign((size_t)n * 2, 0.0f);
    Noise noise(seed);
    // Beat times with optional linear tempo drift
    double t = offset;
    long k = 0;
    while (t < seconds - 0.6) {
        // A human or a loose sequencer: every beat lands a little early or late (the tempo underneath stays steady)
        s.beatTimes.push_back(t + (jitterMs > 0.0 ? (double)noise.next() * jitterMs / 1000.0 : 0.0));
        const double frac = std::min(1.0, t / seconds);
        const double b = bpm * (1.0 + driftPct / 100.0 * frac);
        t += 60.0 / b;
        k++;
    }
    auto add = [&](long at, float l, float r) {
        if (at >= 0 && at < n) {
            s.pcm[(size_t)at * 2] += l;
            s.pcm[(size_t)at * 2 + 1] += r;
        }
    };
    const double pi = 3.14159265358979323846;
    for (size_t i = 0; i < s.beatTimes.size(); i++) {
        const double bt = s.beatTimes[i];
        const long at = (long)std::lround(bt * sr);
        const bool down = i % 4 == 0;
        const double amp = down ? 0.95 : 0.6;
        // kick
        double ph = 0.0;
        for (int j = 0; j < (int)(0.28 * sr); j++) {
            const double tt = (double)j / sr;
            const double f = 48.0 + 110.0 * std::exp(-tt / 0.025);
            ph += 2.0 * pi * f / sr;
            const float v = (float)(amp * std::sin(ph) * std::exp(-tt / 0.11));
            add(at + j, v, v);
        }
        const double beatLen = i + 1 < s.beatTimes.size() ? s.beatTimes[i + 1] - bt : 60.0 / bpm;
        const bool body = (long)i >= introBeats;  // after the intro (8 bars: drums only)
        if (body) {
            // off-beat hat
            const long hat = at + (long)std::lround(beatLen * 0.5 * sr);
            float prev = 0.0f;
            for (int j = 0; j < (int)(0.04 * sr); j++) {
                const float w = noise.next();
                const float v = (w - prev) * 0.5f * std::exp(-(float)j / (0.008f * sr)) * 0.35f;
                prev = w;
                add(hat + j, v * 0.9f, v * 1.1f);
            }
            // snare on 2 and 4
            if (i % 4 == 1 || i % 4 == 3) {
                for (int j = 0; j < (int)(0.12 * sr); j++) {
                    const double tt = (double)j / sr;
                    const float v = (float)(0.3 * noise.next() * std::exp(-tt / 0.04) +
                                            0.2 * std::sin(2 * pi * 190.0 * tt) * std::exp(-tt / 0.05));
                    add(at + j, v, v);
                }
            }
            // pad chord (minor), louder on the first bar of a phrase
            const bool phraseStart = (((long)i - introBeats) / 16) % 2 == 1;  // alternate quiet and full sections of 16 beats
            if (i % 4 == 0) {
                const double len = beatLen * 4.0;
                const double pa = phraseStart ? 0.2 : 0.03;
                const double roots[3] = {keyHz * 2.0, keyHz * 2.0 * 1.189207, keyHz * 2.0 * 1.498307};
                for (int j = 0; j < (int)(len * sr); j++) {
                    const double tt = (double)j / sr;
                    const double env = std::min(1.0, tt / 0.05) * std::min(1.0, (len - tt) / 0.05);
                    float v = 0.0f;
                    for (double f : roots) v += (float)(std::sin(2 * pi * f * tt) * 0.33);
                    v *= (float)(pa * env);
                    add(at + j, v, v * 0.8f);
                }
            }
        }
    }
    // Keep inside range
    for (float& v : s.pcm) v = std::max(-0.98f, std::min(0.98f, v));
    return s;
}


/**
 * A song with known structure: [levels] gives per block of 16 beats how loud it is. -1: no drums, a soft pad only (a
 * breakdown); 0: a quiet kick only (intro or outro); 1: kick and hats (a build); 2: everything, loud (a drop or chorus).
 * Returns the song and, in [beatTimes], the true beats.
 */
inline Synth makeStructured(int sr, double bpm, double offset, const std::vector<int>& levels, double keyHz = 130.81,
                            uint32_t seed = 1) {
    Synth s;
    s.sampleRate = sr;
    const double beatLen = 60.0 / bpm;
    const long beats = (long)levels.size() * 16;
    const double seconds = offset + (double)beats * beatLen + 1.0;
    const long n = (long)(seconds * sr);
    s.pcm.assign((size_t)n * 2, 0.0f);
    Noise noise(seed);
    auto add = [&](long at, float l, float r) {
        if (at >= 0 && at < n) {
            s.pcm[(size_t)at * 2] += l;
            s.pcm[(size_t)at * 2 + 1] += r;
        }
    };
    const double pi = 3.14159265358979323846;
    for (long i = 0; i < beats; i++) {
        const double bt = offset + (double)i * beatLen;
        s.beatTimes.push_back(bt);
        const long at = (long)std::lround(bt * sr);
        const int lv = levels[(size_t)(i / 16)];
        if (lv >= 0) {
            const double amp = (i % 4 == 0 ? 1.0 : 0.65) * (lv == 0 ? 0.4 : lv == 1 ? 0.7 : 0.95);
            double ph = 0.0;
            for (int j = 0; j < (int)(0.28 * sr); j++) {
                const double tt = (double)j / sr;
                ph += 2.0 * pi * (48.0 + 110.0 * std::exp(-tt / 0.025)) / sr;
                const float v = (float)(amp * std::sin(ph) * std::exp(-tt / 0.11));
                add(at + j, v, v);
            }
        }
        if (lv >= 1) {
            const long hat = at + (long)std::lround(beatLen * 0.5 * sr);
            float prev = 0.0f;
            for (int j = 0; j < (int)(0.04 * sr); j++) {
                const float w = noise.next();
                const float v = (w - prev) * 0.5f * std::exp(-(float)j / (0.008f * sr)) * 0.35f;
                prev = w;
                add(hat + j, v * 0.9f, v * 1.1f);
            }
        }
        if (lv >= 2 && (i % 4 == 1 || i % 4 == 3)) {
            for (int j = 0; j < (int)(0.12 * sr); j++) {
                const double tt = (double)j / sr;
                const float v = (float)(0.3 * noise.next() * std::exp(-tt / 0.04) + 0.2 * std::sin(2 * pi * 190.0 * tt) * std::exp(-tt / 0.05));
                add(at + j, v, v);
            }
        }
        if (i % 4 == 0 && lv >= -1) {
            const double len = beatLen * 4.0;
            const double pa = lv >= 2 ? 0.2 : (lv == -1 ? 0.06 : 0.03);
            const double roots[3] = {keyHz * 2.0, keyHz * 2.0 * 1.189207, keyHz * 2.0 * 1.498307};
            for (int j = 0; j < (int)(len * sr); j++) {
                const double tt = (double)j / sr;
                const double env = std::min(1.0, tt / 0.05) * std::min(1.0, (len - tt) / 0.05);
                float v = 0.0f;
                for (double f : roots) v += (float)(std::sin(2 * pi * f * tt) * 0.33);
                v *= (float)(pa * env);
                add(at + j, v, v * 0.8f);
            }
        }
    }
    for (float& v : s.pcm) v = std::max(-0.98f, std::min(0.98f, v));
    return s;
}

inline std::vector<float> toMono(const std::vector<float>& stereo) {
    std::vector<float> m(stereo.size() / 2);
    for (size_t i = 0; i < m.size(); i++) m[i] = 0.5f * (stereo[2 * i] + stereo[2 * i + 1]);
    return m;
}

class VectorSource : public PcmSource {
public:
    explicit VectorSource(std::vector<float> d) : data(std::move(d)) {}
    int64_t frames() const override { return (int64_t)(data.size() / 2); }
    void read(int64_t at, float* out, int n) override {
        for (int i = 0; i < n; i++) {
            const int64_t p = at + i;
            if (p >= 0 && p < frames()) {
                out[2 * i] = data[(size_t)p * 2];
                out[2 * i + 1] = data[(size_t)p * 2 + 1];
            } else {
                out[2 * i] = 0.0f;
                out[2 * i + 1] = 0.0f;
            }
        }
    }
private:
    std::vector<float> data;
};

}  // namespace nooklin::mix::host
