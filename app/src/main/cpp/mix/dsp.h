#pragma once
// Small DSP blocks of the mix engine. No allocation after construction, safe on the audio thread.
#include <algorithm>
#include <cmath>
#include <vector>

namespace nooklin::mix {

inline double smoothstep(double x, double a, double b);

struct Biquad {
    double b0 = 1, b1 = 0, b2 = 0, a1 = 0, a2 = 0;
    double z1[2] = {0, 0}, z2[2] = {0, 0};

    void lowpass(double sr, double hz, double q) {
        const double w = 2.0 * 3.14159265358979323846 * hz / sr;
        const double alpha = std::sin(w) / (2.0 * q), c = std::cos(w);
        const double a0 = 1.0 + alpha;
        b0 = (1.0 - c) / 2.0 / a0;
        b1 = (1.0 - c) / a0;
        b2 = b0;
        a1 = -2.0 * c / a0;
        a2 = (1.0 - alpha) / a0;
    }
    void reset() { z1[0] = z1[1] = z2[0] = z2[1] = 0.0; }
    inline double process(double x, int ch) {
        const double y = b0 * x + z1[ch];
        z1[ch] = b1 * x - a1 * y + z2[ch];
        z2[ch] = b2 * x - a2 * y;
        return y;
    }
};

/**
 * Three bands like the knobs of a DJ mixer: low (under 180 Hz), mid (180 Hz to 2.5 kHz), high (above). The bands are made
 * by subtraction of fourth-order Butterworth low passes, so with all gains at 1 the output is exactly the input. Each
 * gain moves to its target with a short slew, so a kill or a swap does not click.
 */
class ThreeBandEq {
public:
    void init(double sampleRate, double lowHz = 180.0, double midHz = 2500.0, double slewSeconds = 0.03) {
        const double q1 = 0.5412, q2 = 1.3066;
        lp[0].lowpass(sampleRate, lowHz, q1);
        lp[1].lowpass(sampleRate, lowHz, q2);
        lp[2].lowpass(sampleRate, midHz, q1);
        lp[3].lowpass(sampleRate, midHz, q2);
        coef = 1.0 - std::exp(-1.0 / (slewSeconds * sampleRate));
        reset();
    }
    void reset() {
        for (auto& b : lp) b.reset();
        for (int i = 0; i < 3; i++) gain[i] = target[i] = 1.0;
    }
    void setTarget(double low, double mid, double high) {
        target[0] = low;
        target[1] = mid;
        target[2] = high;
    }
    void snap(double low, double mid, double high) {
        gain[0] = target[0] = low;
        gain[1] = target[1] = mid;
        gain[2] = target[2] = high;
    }
    bool flat() const {
        for (int i = 0; i < 3; i++)
            if (std::fabs(gain[i] - 1.0) > 1e-4 || std::fabs(target[i] - 1.0) > 1e-4) return false;
        return true;
    }
    /** Processes [n] interleaved stereo frames in place. */
    void process(float* buf, int n) {
        if (flat()) return;
        for (int i = 0; i < n; i++) {
            for (int g = 0; g < 3; g++) gain[g] += coef * (target[g] - gain[g]);
            for (int c = 0; c < 2; c++) {
                const double x = buf[2 * i + c];
                const double low = lp[1].process(lp[0].process(x, c), c);
                const double upToMid = lp[3].process(lp[2].process(x, c), c);
                buf[2 * i + c] = (float)(gain[0] * low + gain[1] * (upToMid - low) + gain[2] * (x - upToMid));
            }
        }
    }

private:
    Biquad lp[4];
    double gain[3] = {1, 1, 1}, target[3] = {1, 1, 1};
    double coef = 0.001;
};


/** An echo: a delay line with feedback. Stereo, the same delay on both sides. Returns the repeats only (the wet signal). */
class Echo {
public:
    void init(int sampleRate, double maxSeconds) {
        frames = (size_t)(maxSeconds * sampleRate) + 2;
        buf.assign(frames * 2, 0.0f);
        pos = 0;
    }
    void reset() {
        std::fill(buf.begin(), buf.end(), 0.0f);
        pos = 0;
    }
    /** [delay] in frames (below the length of the line), [feedback] 0..0.9. */
    void set(size_t delay, float feedback) {
        d = std::min(delay, frames - 1);
        fb = std::min(0.9f, std::max(0.0f, feedback));
    }
    inline void process(float inL, float inR, float& wetL, float& wetR) {
        const size_t r = (pos + frames - d) % frames;
        wetL = buf[2 * r];
        wetR = buf[2 * r + 1];
        buf[2 * pos] = inL + wetL * fb;
        buf[2 * pos + 1] = inR + wetR * fb;
        pos = pos + 1 == frames ? 0 : pos + 1;
    }

private:
    std::vector<float> buf;
    size_t frames = 2, pos = 0, d = 1;
    float fb = 0.55f;
};

/** A low pass filter of the fourth order with a cutoff that can move from block to block. */
class SweepFilter {
public:
    void init(double sampleRate) { sr = sampleRate; }
    void reset() {
        a.reset();
        b.reset();
    }
    void setCutoff(double hz) {
        hz = std::min(0.45 * sr, std::max(60.0, hz));
        a.lowpass(sr, hz, 0.7);
        b.lowpass(sr, hz, 1.1);
    }
    void process(float* buf, int n) {
        for (int i = 0; i < n; i++)
            for (int c = 0; c < 2; c++) buf[2 * i + c] = (float)b.process(a.process(buf[2 * i + c], c), c);
    }

private:
    double sr = 44100.0;
    Biquad a, b;
};

/** A reverb of the Freeverb kind: eight comb filters in parallel and four all-pass filters in series per side. Returns the wet signal only. */
class Reverb {
public:
    void init(int sampleRate) {
        static const int combTune[8] = {1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617};
        static const int allTune[4] = {556, 441, 341, 225};
        const double k = (double)sampleRate / 44100.0;
        for (int c = 0; c < 2; c++) {
            const int spread = c * 23;
            for (int i = 0; i < 8; i++) {
                comb[c][i].buf.assign((size_t)std::max(8.0, (combTune[i] + spread) * k), 0.0f);
                comb[c][i].pos = 0;
                comb[c][i].store = 0.0f;
            }
            for (int i = 0; i < 4; i++) {
                ap[c][i].buf.assign((size_t)std::max(8.0, (allTune[i] + spread) * k), 0.0f);
                ap[c][i].pos = 0;
            }
        }
    }
    void reset() {
        for (int c = 0; c < 2; c++) {
            for (auto& x : comb[c]) {
                std::fill(x.buf.begin(), x.buf.end(), 0.0f);
                x.pos = 0;
                x.store = 0.0f;
            }
            for (auto& x : ap[c]) {
                std::fill(x.buf.begin(), x.buf.end(), 0.0f);
                x.pos = 0;
            }
        }
    }
    inline void process(float inL, float inR, float& wetL, float& wetR) {
        const float in = (inL + inR) * 0.015f;
        float o[2];
        for (int c = 0; c < 2; c++) {
            float acc = 0.0f;
            for (auto& x : comb[c]) {
                const float y = x.buf[x.pos];
                x.store = y * (1.0f - kDamp) + x.store * kDamp;
                x.buf[x.pos] = in + x.store * kFeedback;
                x.pos = x.pos + 1 == x.buf.size() ? 0 : x.pos + 1;
                acc += y;
            }
            for (auto& x : ap[c]) {
                const float b = x.buf[x.pos];
                const float y = -acc + b;
                x.buf[x.pos] = acc + b * 0.5f;
                x.pos = x.pos + 1 == x.buf.size() ? 0 : x.pos + 1;
                acc = y;
            }
            o[c] = acc * 3.0f;
        }
        wetL = o[0];
        wetR = o[1];
    }

private:
    static constexpr float kFeedback = 0.90f, kDamp = 0.25f;
    struct Comb {
        std::vector<float> buf;
        size_t pos = 0;
        float store = 0.0f;
    };
    struct AllPass {
        std::vector<float> buf;
        size_t pos = 0;
    };
    Comb comb[2][8];
    AllPass ap[2][4];
};

/** The last seconds of what a song played, to play back at another speed (brake, spin back, beat roll). */
class AudioRing {
public:
    void init(int sampleRate, double seconds) {
        frames = (int64_t)(seconds * sampleRate) + 4;
        buf.assign((size_t)frames * 2, 0.0f);
        written = 0;
    }
    void reset() {
        std::fill(buf.begin(), buf.end(), 0.0f);
        written = 0;
    }
    void write(const float* in, int n) {
        for (int i = 0; i < n; i++) {
            const size_t at = (size_t)((written + i) % frames);
            buf[2 * at] = in[2 * i];
            buf[2 * at + 1] = in[2 * i + 1];
        }
        written += n;
    }
    int64_t count() const { return written; }
    /** The frame at the (fractional) position [pos], counted from the reset; positions that are gone or not yet written are clamped. */
    inline void read(double pos, float& l, float& r) const {
        const double lo = (double)std::max<int64_t>(0, written - frames + 2), hi = (double)std::max<int64_t>(0, written - 1);
        pos = std::min(hi, std::max(lo, pos));
        const int64_t i0 = (int64_t)std::floor(pos);
        const double f = pos - (double)i0;
        const size_t a = (size_t)(i0 % frames), b = (size_t)((i0 + 1 < written ? i0 + 1 : i0) % frames);
        l = (float)(buf[2 * a] * (1.0 - f) + buf[2 * b] * f);
        r = (float)(buf[2 * a + 1] * (1.0 - f) + buf[2 * b + 1] * f);
    }

private:
    std::vector<float> buf;
    int64_t frames = 4, written = 0;
};

/**
 * Three ways to leave a song by playing back what it just did: [kBrake] the record slows down to a stop (the pitch falls),
 * [kSpin] it is stopped by hand and pulled backwards, [kRoll] the first beat of the last two is repeated in shorter and
 * shorter pieces. The effect lasts [totalBeats]; the audio of A is replaced block by block.
 */
class RingFx {
public:
    enum Mode { kBrake = 0, kSpin = 1, kRoll = 2 };
    void init(int sampleRate) {
        sr = sampleRate;
        ring.init(sampleRate, 8.0);
    }
    void begin(Mode m, double beatFrames, double beats) {
        mode = m;
        bf = std::max(1.0, beatFrames);
        total = std::max(1.0, beats);
        ring.reset();
        head = 0.0;
        done = 0;
        rollMarked = false;
    }
    /** Keep listening to A before the effect starts, so that the effect has something to play back. */
    void record(const float* buf, int n) { ring.write(buf, n); }
    /** [buf] holds the audio of A for [n] frames and gets the effect instead. */
    void process(float* buf, int n) {
        ring.write(buf, n);
        const double base = (double)ring.count() - n;  // ring position of the first frame of this block
        for (int i = 0; i < n; i++) {
            const double b = (double)(done + i) / bf;  // beats since the start
            const double x = std::min(1.0, b / total);
            float l = 0.0f, r = 0.0f;
            double g = 1.0;
            if (mode == kBrake) {
                if (done + i == 0) head = base;
                const double speed = std::pow(std::max(0.0, 1.0 - x), 1.5);
                ring.read(head, l, r);
                head += speed;
                g = smoothstep(1.0 - x, 0.0, 0.12);
            } else if (mode == kSpin) {
                if (done + i == 0) head = base;
                const double speed = 1.0 - 3.2 * smoothstep(x, 0.55, 0.95);
                ring.read(head, l, r);
                head += speed;
                g = smoothstep(1.0 - x, 0.0, 0.10);
            } else {
                const double rb = b - (total - 1.0);  // beats into the roll (the last beat)
                if (rb < 0.0) {
                    ring.read(base + i, l, r);
                } else {
                    const double len = rb < 0.5 ? 0.25 : (rb < 0.75 ? 0.125 : 0.0625);
                    const double segStart = rb < 0.5 ? 0.0 : (rb < 0.75 ? 0.5 : 0.75);
                    const double inSeg = (rb - segStart) / len;            // slices into the segment
                    const double within = inSeg - std::floor(inSeg);       // 0..1 in the slice
                    const double sliceFrames = len * bf;
                    if (!rollMarked) {
                        sliceSrc = base + i - bf;
                        rollMarked = true;
                    }
                    ring.read(sliceSrc + within * sliceFrames, l, r);
                    const double fade = std::min(1.0, std::min(within * sliceFrames, (1.0 - within) * sliceFrames) / (0.001 * sr));
                    g = fade * smoothstep(1.0 - x, 0.0, 0.04);
                }
            }
            buf[2 * i] = (float)(l * g);
            buf[2 * i + 1] = (float)(r * g);
        }
        done += n;
    }
private:
    int sr = 44100;
    AudioRing ring;
    Mode mode = kBrake;
    double bf = 22050.0, total = 2.0, head = 0.0, sliceSrc = 0.0;
    int64_t done = 0;
    bool rollMarked = false;
};

/** The noise that rises towards a drop: white noise through a high pass whose cutoff and level climb. */
class Riser {
public:
    void init(int sampleRate) { sr = sampleRate; }
    void reset(uint32_t seed) {
        s = seed ? seed : 1u;
        lp = 0.0;
    }
    /** [x] 0 at the start of the rise, 1 at its end. */
    inline float next(double x) {
        s = s * 1664525u + 1013904223u;
        const double n = (double)((int32_t)s) / 2147483648.0;
        const double cut = 300.0 * std::pow(30.0, std::min(1.0, std::max(0.0, x)));
        const double a = std::exp(-2.0 * 3.14159265358979323846 * cut / sr);
        lp = a * lp + (1.0 - a) * n;
        return (float)((n - lp) * 0.28 * x * x);
    }

private:
    double sr = 44100.0, lp = 0.0;
    uint32_t s = 1u;
};

/** The hit on the first beat of B: a sine that drops in pitch and a short burst of noise. */
class Impact {
public:
    void init(int sampleRate) { sr = sampleRate; }
    void trigger() {
        t = 0.0;
        phase = 0.0;
        on = true;
    }
    void stop() { on = false; }
    bool active() const { return on; }
    inline float next() {
        if (!on) return 0.0f;
        s = s * 1664525u + 1013904223u;
        const double n = (double)((int32_t)s) / 2147483648.0;
        const double f = 42.0 + 60.0 * std::exp(-t / 0.10);
        phase += 2.0 * 3.14159265358979323846 * f / sr;
        const double v = std::sin(phase) * std::exp(-t / 0.32) * 0.7 + n * std::exp(-t / 0.025) * 0.25;
        t += 1.0 / sr;
        if (t > 1.8) on = false;
        return (float)v;
    }

private:
    double sr = 44100.0, t = 0.0, phase = 0.0;
    bool on = false;
    uint32_t s = 7u;
};

inline double smoothstep(double x, double a, double b) {
    if (b <= a) return x >= b ? 1.0 : 0.0;
    double t = (x - a) / (b - a);
    t = std::min(1.0, std::max(0.0, t));
    return t * t * (3.0 - 2.0 * t);
}

/**
 * 0.0.103: level control of the overlap by the measured correlation of the two songs. Equal-power crossfade curves are only right for
 * signals that are uncorrelated; two songs that share a loop, a sample or a bass line add up louder (up to +3 dB at the middle), two that
 * cancel add up quieter (Fink, Holters, Zoelzer 2016). The power of A and of B (after the EQ) and their cross term are followed with a time
 * constant of 0.4 s; the sum is scaled so that its power is the power it would have had with no correlation. The scale is clamped to
 * [0.85, 1.2] and follows slowly, so it cannot pump. No allocation, safe on the audio thread. Measured on the mid (L+R)/2 of each song.
 */
struct BlendLeveler {
    double aa = 0.0, bb = 0.0, ab = 0.0, k = 1.0;
    bool primed = false;
    void reset() { aa = bb = ab = 0.0; k = 1.0; primed = false; }
    /** [a] and [b] are interleaved stereo blocks of [n] frames; returns the scale for the sum gA*a + gB*b. */
    double update(const float* a, const float* b, int n, int sr, double gA, double gB) {
        if (n <= 0) return k;
        double saa = 0.0, sbb = 0.0, sab = 0.0;
        for (int i = 0; i < n; i++) {
            const double x = 0.5 * ((double)a[2 * i] + (double)a[2 * i + 1]);
            const double y = 0.5 * ((double)b[2 * i] + (double)b[2 * i + 1]);
            saa += x * x;
            sbb += y * y;
            sab += x * y;
        }
        saa /= n;
        sbb /= n;
        sab /= n;
        if (!primed) {
            aa = saa; bb = sbb; ab = sab;
            primed = true;
        } else {
            const double alpha = std::exp(-(double)n / (0.4 * (double)std::max(8000, sr)));
            aa = alpha * aa + (1.0 - alpha) * saa;
            bb = alpha * bb + (1.0 - alpha) * sbb;
            ab = alpha * ab + (1.0 - alpha) * sab;
        }
        const double target = gA * gA * aa + gB * gB * bb;
        const double actual = target + 2.0 * gA * gB * ab;
        double want = 1.0;
        if (target > 1e-9 && actual > 1e-9) want = std::sqrt(target / actual);
        want = std::min(1.2, std::max(0.85, want));  // 0.0.107: was [0.7, 1.5], swung 0.80 to 1.49 within a few beats
        k += 0.02 * (want - k);
        return k;
    }
};

/** Soft limiter: leaves everything under 0.9 alone and rounds off what is above it. */
inline float softLimit(float x) {
    const float m = std::fabs(x);
    if (m <= 0.9f) return x;
    const float v = 0.9f + 0.1f * std::tanh((m - 0.9f) / 0.1f);
    return x < 0.0f ? -v : v;
}

}  // namespace nooklin::mix
