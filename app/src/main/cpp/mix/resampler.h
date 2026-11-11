#pragma once
// Streaming stereo sample rate converter: windowed sinc (Kaiser), 32 taps, 256 phases with linear interpolation between
// phases. Used to bring decoded audio to the engine rate. Input and output are interleaved 16-bit stereo.
#include <cmath>
#include <cstdint>
#include <vector>

namespace nooklin::mix {

class Resampler {
public:
    Resampler(int srcRate, int dstRate) : step((double)srcRate / (double)dstRate) {
        const double cutoff = srcRate > dstRate ? (double)dstRate / (double)srcRate * 0.97 : 0.97;
        table.assign((size_t)(kPhases + 1) * kTaps, 0.0f);
        const double beta = 8.0;
        auto bessel0 = [](double x) {
            double sum = 1.0, term = 1.0;
            for (int k = 1; k < 30; k++) {
                term *= (x / (2.0 * k)) * (x / (2.0 * k));
                sum += term;
            }
            return sum;
        };
        const double norm = bessel0(beta);
        for (int p = 0; p <= kPhases; p++) {
            const double frac = (double)p / kPhases;
            double sum = 0.0;
            for (int t = 0; t < kTaps; t++) {
                const double x = (double)(t - kTaps / 2 + 1) - frac;  // distance from the output point, in source frames
                const double sinc = std::fabs(x) < 1e-9 ? 1.0 : std::sin(M_PI * x * cutoff) / (M_PI * x * cutoff);
                const double r = x / (kTaps / 2.0);
                const double w = std::fabs(r) >= 1.0 ? 0.0 : bessel0(beta * std::sqrt(1.0 - r * r)) / norm;
                const double v = sinc * w * cutoff;
                table[(size_t)p * kTaps + t] = (float)v;
                sum += v;
            }
            for (int t = 0; t < kTaps; t++) table[(size_t)p * kTaps + t] = (float)(table[(size_t)p * kTaps + t] / sum);
        }
        hist.assign((size_t)kTaps * 2, 0.0f);  // silence before the start
    }

    bool passthrough() const { return std::fabs(step - 1.0) < 1e-9; }

    /** Converts [frames] input frames; output frames are appended to [out] (interleaved stereo). */
    void process(const int16_t* in, int frames, std::vector<int16_t>& out) {
        for (int i = 0; i < frames; i++) {
            hist.push_back(in[2 * i] * (1.0f / 32768.0f));
            hist.push_back(in[2 * i + 1] * (1.0f / 32768.0f));
        }
        const int64_t have = (int64_t)hist.size() / 2;  // frames in hist; hist[0] is source frame base
        // Output n needs source frames [pos - taps/2 + 1, pos + taps/2] relative to base
        while (true) {
            const int64_t i0 = (int64_t)std::floor(pos);
            if (i0 + kTaps / 2 >= base + have) break;
            const double frac = pos - (double)i0;
            const double fp = frac * kPhases;
            const int p0 = (int)fp;
            const float w1 = (float)(fp - p0);
            const float* t0 = &table[(size_t)p0 * kTaps];
            const float* t1 = &table[(size_t)(p0 + 1) * kTaps];
            float l = 0.0f, r = 0.0f;
            const int64_t first = i0 - kTaps / 2 + 1 - base;
            for (int t = 0; t < kTaps; t++) {
                const float c = t0[t] + (t1[t] - t0[t]) * w1;
                const int64_t idx = first + t;
                if (idx < 0) continue;
                l += c * hist[(size_t)idx * 2];
                r += c * hist[(size_t)idx * 2 + 1];
            }
            out.push_back(clip16(l));
            out.push_back(clip16(r));
            pos += step;
        }
        // Drop what can no longer be needed
        const int64_t keepFrom = (int64_t)std::floor(pos) - kTaps / 2;
        if (keepFrom > base) {
            const int64_t drop = keepFrom - base;
            hist.erase(hist.begin(), hist.begin() + (size_t)drop * 2);
            base = keepFrom;
        }
    }

private:
    static constexpr int kTaps = 32;
    static constexpr int kPhases = 256;
    static int16_t clip16(float v) {
        v *= 32768.0f;
        return (int16_t)(v > 32767.0f ? 32767.0f : (v < -32768.0f ? -32768.0f : std::lrintf(v)));
    }
    double step;
    double pos = 0.0;      // position of the next output frame, in source frames
    int64_t base = -kTaps;  // source frame number of hist[0] (hist starts with silence)
    std::vector<float> table, hist;
};

}  // namespace nooklin::mix
