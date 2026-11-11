#include "stem_dsp.h"

#include <algorithm>
#include <cmath>
#include <thread>
#include <vector>

namespace nooklin::mix::stemdsp {
namespace {

constexpr float kEps = 1e-10f;
constexpr double kPi = 3.14159265358979323846;

// A complex FFT of fixed size, in place, with the tables shared and read only (every thread has its own re/im)
struct Fft {
    std::vector<double> cosT, sinT;
    std::vector<int> rev;
    std::vector<double> window;
    Fft() : cosT(kFft / 2), sinT(kFft / 2), rev((size_t)kFft), window((size_t)kFft) {
        for (int i = 0; i < kFft / 2; i++) {
            cosT[(size_t)i] = std::cos(2.0 * kPi * i / kFft);
            sinT[(size_t)i] = std::sin(2.0 * kPi * i / kFft);
        }
        int bits = 0;
        while ((1 << bits) < kFft) bits++;
        for (int i = 0; i < kFft; i++) {
            int r = 0, x = i;
            for (int b = 0; b < bits; b++) { r = (r << 1) | (x & 1); x >>= 1; }
            rev[(size_t)i] = r;
        }
        for (int i = 0; i < kFft; i++) window[(size_t)i] = 0.5 - 0.5 * std::cos(2.0 * kPi * i / kFft);
    }
    void transform(double* re, double* im, bool inverse) const {
        for (int i = 0; i < kFft; i++) {
            const int j = rev[(size_t)i];
            if (j > i) { std::swap(re[i], re[j]); std::swap(im[i], im[j]); }
        }
        const double sign = inverse ? 1.0 : -1.0;
        for (int half = 1; half < kFft; half *= 2) {
            const int step = kFft / (half * 2);
            for (int start = 0; start < kFft; start += half * 2) {
                int k = 0;
                for (int j = start; j < start + half; j++) {
                    const double wr = cosT[(size_t)k], wi = sign * sinT[(size_t)k];
                    const int l = j + half;
                    const double xr = re[l] * wr - im[l] * wi;
                    const double xi = re[l] * wi + im[l] * wr;
                    re[l] = re[j] - xr;
                    im[l] = im[j] - xi;
                    re[j] += xr;
                    im[j] += xi;
                    k += step;
                }
            }
        }
        if (inverse) {
            const double scale = 1.0 / kFft;
            for (int i = 0; i < kFft; i++) { re[i] *= scale; im[i] *= scale; }
        }
    }
};

const Fft& fft() {
    static const Fft f;   // built once (thread safe since C++11)
    return f;
}

inline float finite(float x) { return std::isfinite(x) ? x : 0.0f; }

// The ratio masks of the four stems for one bin (the model can answer with NaN on digital silence: that counts as 0)
inline void masks(const float* const est[kStems], size_t at, float out[kStems]) {
    float sq[kStems];
    float total = kEps;
    for (int s = 0; s < kStems; s++) {
        const float e = finite(est[s][at]);
        sq[s] = e * e;
        total += sq[s];
    }
    for (int s = 0; s < kStems; s++) out[s] = (sq[s] + kEps / kStems) / total;
}

// Runs fn(begin, end) over [0, n) split in contiguous ranges, on up to [threads] threads (the calling thread does the first range)
template <class F>
void parallelRanges(int n, int threads, F fn) {
    threads = std::max(1, std::min(threads, n));
    if (threads == 1) { fn(0, n, 0); return; }
    std::vector<std::thread> pool;
    for (int i = 1; i < threads; i++) pool.emplace_back([=, &fn] { fn((int)((long long)n * i / threads), (int)((long long)n * (i + 1) / threads), i); });
    fn(0, (int)((long long)n / threads), 0);
    for (auto& t : pool) t.join();
}

}  // namespace

void stft(const float* left, const float* right, int samples, int frames, int stride, float* spec0, float* spec1, float* magnitudes,
          int threads) {
    const Fft& f = fft();
    parallelRanges(frames, threads, [&](int begin, int end, int) {
        std::vector<double> re((size_t)kFft), im((size_t)kFft);
        for (int t = begin; t < end; t++) {
            const long from = (long)t * kHop - kPad;
            for (int i = 0; i < kFft; i++) {
                const long s = from + i;
                const bool in = s >= 0 && s < samples;
                re[(size_t)i] = in ? (double)left[s] * f.window[(size_t)i] : 0.0;
                im[(size_t)i] = in ? (double)right[s] * f.window[(size_t)i] : 0.0;
            }
            f.transform(re.data(), im.data(), false);
            for (int k = 0; k < kBins; k++) {
                const int k2 = (kFft - k) % kFft;
                const double lr = (re[(size_t)k] + re[(size_t)k2]) / 2.0;
                const double li = (im[(size_t)k] - im[(size_t)k2]) / 2.0;
                const double rr = (im[(size_t)k] + im[(size_t)k2]) / 2.0;
                const double ri = -(re[(size_t)k] - re[(size_t)k2]) / 2.0;
                const size_t at = ((size_t)t * kBins + (size_t)k) * 2;
                spec0[at] = (float)lr;
                spec0[at + 1] = (float)li;
                spec1[at] = (float)rr;
                spec1[at + 1] = (float)ri;
                if (k < kModelBins) {
                    magnitudes[((size_t)0 * stride + (size_t)t) * kModelBins + (size_t)k] = (float)std::sqrt(lr * lr + li * li);
                    magnitudes[((size_t)1 * stride + (size_t)t) * kModelBins + (size_t)k] = (float)std::sqrt(rr * rr + ri * ri);
                }
            }
        }
    });
}

void energy(const float* spec0, const float* spec1, const float* const est[kStems], int frames, int stride, int stem, float* out,
            int threads) {
    const float* spec[2] = {spec0, spec1};
    parallelRanges(frames, threads, [&](int begin, int end, int) {
        float m[kStems];
        for (int t = begin; t < end; t++) {
            double sum = 0.0;
            for (int c = 0; c < 2; c++) {
                for (int k = 0; k < kModelBins; k++) {
                    masks(est, ((size_t)c * stride + (size_t)t) * kModelBins + (size_t)k, m);
                    const size_t at = ((size_t)t * kBins + (size_t)k) * 2;
                    const float re = spec[c][at], im = spec[c][at + 1];
                    sum += m[stem] * m[stem] * (re * re + im * im);
                }
            }
            out[t] = (float)sum;
        }
    });
}

void render(const float* spec0, const float* spec1, const float* const est[kStems], int frames, int stride, int samples,
            const float* gains, float* outL, float* outR, int threads) {
    const Fft& f = fft();
    const float* spec[2] = {spec0, spec1};
    const size_t total = (size_t)(frames - 1) * kHop + kFft;
    threads = std::max(1, std::min(threads, frames));
    // Every thread adds its frames into a buffer of its own (the frames of two ranges overlap at the border); the buffers are summed in order
    struct Part { size_t first = 0; std::vector<double> l, r, w; };
    std::vector<Part> parts((size_t)threads);
    parallelRanges(frames, threads, [&](int begin, int end, int index) {
        if (end <= begin) return;
        Part& p = parts[(size_t)index];
        p.first = (size_t)begin * kHop;
        const size_t len = (size_t)(end - 1 - begin) * kHop + kFft;
        p.l.assign(len, 0.0);
        p.r.assign(len, 0.0);
        p.w.assign(len, 0.0);
        std::vector<double> re((size_t)kFft), im((size_t)kFft);
        std::vector<float> mixed0(kBins), mixed1(kBins);
        float* mixed[2] = {mixed0.data(), mixed1.data()};
        for (int t = begin; t < end; t++) {
            const float* g = gains + (size_t)t * kStems;
            for (int c = 0; c < 2; c++) {
                float tail[kStems] = {0, 0, 0, 0};
                float m[kStems];
                for (int k = 0; k < kModelBins; k++) {
                    masks(est, ((size_t)c * stride + (size_t)t) * kModelBins + (size_t)k, m);
                    float v = 0.0f;
                    for (int s = 0; s < kStems; s++) { v += g[s] * m[s]; tail[s] += m[s]; }
                    mixed[c][k] = v;
                }
                float v = 0.0f;
                for (int s = 0; s < kStems; s++) v += g[s] * tail[s] / kModelBins;
                for (int k = kModelBins; k < kBins; k++) mixed[c][k] = v;
            }
            // Back: both channels in one complex transform (left plus i times right)
            for (int k = 0; k < kBins; k++) {
                const size_t at = ((size_t)t * kBins + (size_t)k) * 2;
                const double lr = spec[0][at] * (double)mixed[0][k];
                const double li = spec[0][at + 1] * (double)mixed[0][k];
                const double rr = spec[1][at] * (double)mixed[1][k];
                const double ri = spec[1][at + 1] * (double)mixed[1][k];
                re[(size_t)k] = lr - ri;
                im[(size_t)k] = li + rr;
                if (k >= 1 && k < kFft / 2) {
                    const int k2 = kFft - k;
                    re[(size_t)k2] = lr + ri;
                    im[(size_t)k2] = -li + rr;
                }
            }
            f.transform(re.data(), im.data(), true);
            const size_t from = (size_t)(t - begin) * kHop;
            for (int i = 0; i < kFft; i++) {
                const double w = f.window[(size_t)i];
                p.l[from + (size_t)i] += re[(size_t)i] * w;
                p.r[from + (size_t)i] += im[(size_t)i] * w;
                p.w[from + (size_t)i] += w * w;
            }
        }
    });
    std::vector<double> l(total, 0.0), r(total, 0.0), w(total, 0.0);
    for (const Part& p : parts) {
        for (size_t i = 0; i < p.l.size(); i++) {
            l[p.first + i] += p.l[i];
            r[p.first + i] += p.r[i];
            w[p.first + i] += p.w[i];
        }
    }
    for (int i = 0; i < samples; i++) {
        const double ww = w[(size_t)kPad + (size_t)i];
        outL[i] = ww > 1e-8 ? (float)(l[(size_t)kPad + (size_t)i] / ww) : 0.0f;
        outR[i] = ww > 1e-8 ? (float)(r[(size_t)kPad + (size_t)i] / ww) : 0.0f;
    }
}

}  // namespace nooklin::mix::stemdsp
