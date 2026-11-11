// Host test of 0.0.130: the native signal processing around the stem model (mix/stem_dsp.cpp), checked against slow, direct versions of the
// same formulas (a plain DFT), for the identity (all volumes 1 gives the music back), for NaN in the estimates, and for 1 thread against 3.
//   g++ -O2 -std=c++17 -pthread host/test_stem_dsp.cpp mix/stem_dsp.cpp -o t_stemdsp && ./t_stemdsp
#include <algorithm>
#include <chrono>
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <limits>
#include <random>
#include <vector>

#include "../mix/stem_dsp.h"

using namespace nooklin::mix::stemdsp;

static int failures = 0;
static void check(bool ok, const char* what, double v) {
    std::printf("  %-74s %s (%.3g)\n", what, ok ? "ok  " : "FAIL", v);
    if (!ok) failures++;
}

struct Piece {
    int samples, frames, stride;
    std::vector<float> left, right, spec0, spec1, mags;
    std::vector<float> est[kStems];
};

static Piece make(double seconds, unsigned seed) {
    Piece p;
    p.samples = (int)(seconds * 44100);
    p.frames = (p.samples + kPad + kHop - 1) / kHop;
    const int splits = (p.frames + 511) / 512;
    p.stride = splits * 512;
    std::mt19937 rng(seed);
    std::uniform_real_distribution<float> noise(-0.05f, 0.05f), pos(0.01f, 1.0f);
    p.left.resize((size_t)p.samples);
    p.right.resize((size_t)p.samples);
    for (int i = 0; i < p.samples; i++) {
        const double t = i / 44100.0;
        p.left[(size_t)i] = (float)(0.4 * std::sin(2 * M_PI * 220 * t) + 0.2 * std::sin(2 * M_PI * 3100 * t)) + noise(rng);
        p.right[(size_t)i] = (float)(0.3 * std::sin(2 * M_PI * 330 * t) + 0.2 * std::sin(2 * M_PI * 7000 * t)) + noise(rng);
    }
    p.spec0.assign((size_t)p.frames * kBins * 2, 0.0f);
    p.spec1.assign((size_t)p.frames * kBins * 2, 0.0f);
    p.mags.assign((size_t)2 * p.stride * kModelBins, 0.0f);
    for (int s = 0; s < kStems; s++) {
        p.est[s].resize((size_t)2 * p.stride * kModelBins);
        for (float& v : p.est[s]) v = pos(rng);
    }
    return p;
}

static void runStft(Piece& p, int threads) {
    stft(p.left.data(), p.right.data(), p.samples, p.frames, p.stride, p.spec0.data(), p.spec1.data(), p.mags.data(), threads);
}
static void estPtrs(const Piece& p, const float* out[kStems]) {
    for (int s = 0; s < kStems; s++) out[s] = p.est[s].data();
}

// Direct DFT of one frame of both channels (slow, independent of the FFT in the module)
static void dftFrame(const Piece& p, int t, std::vector<double>& lr, std::vector<double>& li, std::vector<double>& rr, std::vector<double>& ri) {
    lr.assign(kBins, 0); li.assign(kBins, 0); rr.assign(kBins, 0); ri.assign(kBins, 0);
    std::vector<double> xl(kFft), xr(kFft);
    for (int i = 0; i < kFft; i++) {
        const long s = (long)t * kHop - kPad + i;
        const double w = 0.5 - 0.5 * std::cos(2 * M_PI * i / kFft);
        xl[(size_t)i] = (s >= 0 && s < p.samples) ? p.left[(size_t)s] * w : 0.0;
        xr[(size_t)i] = (s >= 0 && s < p.samples) ? p.right[(size_t)s] * w : 0.0;
    }
    for (int k = 0; k < kBins; k++) {
        for (int i = 0; i < kFft; i++) {
            const double a = -2 * M_PI * (double)((long)k * i % kFft) / kFft, c = std::cos(a), s = std::sin(a);
            lr[(size_t)k] += xl[(size_t)i] * c; li[(size_t)k] += xl[(size_t)i] * s;
            rr[(size_t)k] += xr[(size_t)i] * c; ri[(size_t)k] += xr[(size_t)i] * s;
        }
    }
}

static double maxDiff(const std::vector<float>& a, const std::vector<float>& b) {
    double d = 0;
    for (size_t i = 0; i < a.size(); i++) d = std::max(d, (double)std::fabs(a[i] - b[i]));
    return d;
}

int main() {
    std::printf("spectrum against a direct DFT\n");
    {
        Piece p = make(0.6, 1);
        runStft(p, 3);
        double worst = 0, scale = 0;
        for (int t : {0, 3, p.frames / 2, p.frames - 1}) {
            std::vector<double> lr, li, rr, ri;
            dftFrame(p, t, lr, li, rr, ri);
            for (int k = 0; k < kBins; k += 7) {
                const size_t at = ((size_t)t * kBins + (size_t)k) * 2;
                worst = std::max({worst, std::fabs(p.spec0[at] - lr[(size_t)k]), std::fabs(p.spec0[at + 1] - li[(size_t)k]),
                                  std::fabs(p.spec1[at] - rr[(size_t)k]), std::fabs(p.spec1[at + 1] - ri[(size_t)k])});
                scale = std::max(scale, std::fabs(lr[(size_t)k]));
            }
            if (t < p.frames && kModelBins > 5) {
                const double m = std::sqrt(lr[5] * lr[5] + li[5] * li[5]);
                worst = std::max(worst, (double)std::fabs(p.mags[((size_t)0 * p.stride + (size_t)t) * kModelBins + 5] - m));
            }
        }
        check(worst < 1e-3 * std::max(1.0, scale), "spectrum and magnitudes match the direct DFT", worst);
    }

    std::printf("identity: all volumes 1 gives the music back\n");
    {
        Piece p = make(1.5, 2);
        runStft(p, 3);
        const float* est[kStems];
        estPtrs(p, est);
        std::vector<float> gains((size_t)p.frames * kStems, 1.0f), l((size_t)p.samples), r((size_t)p.samples);
        render(p.spec0.data(), p.spec1.data(), est, p.frames, p.stride, p.samples, gains.data(), l.data(), r.data(), 3);
        const double dl = maxDiff(l, p.left), dr = maxDiff(r, p.right);
        check(dl < 1e-4 && dr < 1e-4, "left and right come back within 1e-4", std::max(dl, dr));
    }

    std::printf("volumes against a direct inverse DFT\n");
    {
        Piece p = make(0.5, 3);
        runStft(p, 2);
        const float* est[kStems];
        estPtrs(p, est);
        std::vector<float> gains((size_t)p.frames * kStems);
        std::mt19937 rng(9);
        std::uniform_real_distribution<float> g(0.0f, 1.0f);
        for (float& v : gains) v = g(rng);
        std::vector<float> l((size_t)p.samples), r((size_t)p.samples);
        render(p.spec0.data(), p.spec1.data(), est, p.frames, p.stride, p.samples, gains.data(), l.data(), r.data(), 3);
        // Reference: the same formulas in double, with a direct inverse DFT
        const size_t total = (size_t)(p.frames - 1) * kHop + kFft;
        std::vector<double> ol(total, 0.0), orr(total, 0.0), ow(total, 0.0);
        std::vector<double> cr(kFft), ci(kFft), mixed[2];
        for (int t = 0; t < p.frames; t++) {
            mixed[0].assign(kBins, 0); mixed[1].assign(kBins, 0);
            for (int c = 0; c < 2; c++) {
                double tail[kStems] = {0, 0, 0, 0};
                for (int k = 0; k < kModelBins; k++) {
                    const size_t at = ((size_t)c * p.stride + (size_t)t) * kModelBins + (size_t)k;
                    double e2[kStems], total2 = 1e-10;
                    for (int s = 0; s < kStems; s++) { e2[s] = (double)p.est[s][at] * p.est[s][at]; total2 += e2[s]; }
                    for (int s = 0; s < kStems; s++) {
                        const double m = (e2[s] + 1e-10 / kStems) / total2;
                        mixed[c][(size_t)k] += gains[(size_t)t * kStems + (size_t)s] * m;
                        tail[s] += m;
                    }
                }
                double v = 0;
                for (int s = 0; s < kStems; s++) v += gains[(size_t)t * kStems + (size_t)s] * tail[s] / kModelBins;
                for (int k = kModelBins; k < kBins; k++) mixed[c][(size_t)k] = v;
            }
            // Spectrum of the combined signal L + iR for all 4096 bins, then a direct inverse DFT
            std::vector<double> xr(kFft, 0.0), xi(kFft, 0.0);
            for (int k = 0; k < kBins; k++) {
                const size_t at = ((size_t)t * kBins + (size_t)k) * 2;
                const double lr = p.spec0[at] * mixed[0][(size_t)k], li = p.spec0[at + 1] * mixed[0][(size_t)k];
                const double rr = p.spec1[at] * mixed[1][(size_t)k], ri = p.spec1[at + 1] * mixed[1][(size_t)k];
                xr[(size_t)k] = lr - ri; xi[(size_t)k] = li + rr;
                if (k >= 1 && k < kFft / 2) { xr[(size_t)(kFft - k)] = lr + ri; xi[(size_t)(kFft - k)] = -li + rr; }
            }
            for (int i = 0; i < kFft; i++) {
                double sr = 0, si = 0;
                for (int k = 0; k < kFft; k++) {
                    const double a = 2 * M_PI * (double)((long)k * i % kFft) / kFft, c = std::cos(a), s = std::sin(a);
                    sr += xr[(size_t)k] * c - xi[(size_t)k] * s;
                    si += xr[(size_t)k] * s + xi[(size_t)k] * c;
                }
                const double w = 0.5 - 0.5 * std::cos(2 * M_PI * i / kFft);
                ol[(size_t)t * kHop + (size_t)i] += sr / kFft * w;
                orr[(size_t)t * kHop + (size_t)i] += si / kFft * w;
                ow[(size_t)t * kHop + (size_t)i] += w * w;
            }
        }
        double worst = 0;
        for (int i = 0; i < p.samples; i++) {
            const double w = ow[(size_t)kPad + (size_t)i];
            const double el = w > 1e-8 ? ol[(size_t)kPad + (size_t)i] / w : 0.0, er = w > 1e-8 ? orr[(size_t)kPad + (size_t)i] / w : 0.0;
            worst = std::max({worst, std::fabs(l[(size_t)i] - el), std::fabs(r[(size_t)i] - er)});
        }
        check(worst < 2e-4, "the render matches the reference for random volumes", worst);
    }

    std::printf("energy\n");
    {
        Piece p = make(0.8, 4);
        runStft(p, 3);
        const float* est[kStems];
        estPtrs(p, est);
        std::vector<float> e1((size_t)p.frames), e3((size_t)p.frames);
        energy(p.spec0.data(), p.spec1.data(), est, p.frames, p.stride, 0, e1.data(), 1);
        energy(p.spec0.data(), p.spec1.data(), est, p.frames, p.stride, 0, e3.data(), 3);
        check(maxDiff(e1, e3) == 0.0, "1 thread and 3 threads give the same energy", maxDiff(e1, e3));
        double worst = 0;
        for (int t : {0, 2, p.frames / 2, p.frames - 1}) {
            double sum = 0;
            for (int c = 0; c < 2; c++) {
                const std::vector<float>& sp = c == 0 ? p.spec0 : p.spec1;
                for (int k = 0; k < kModelBins; k++) {
                    const size_t at = ((size_t)c * p.stride + (size_t)t) * kModelBins + (size_t)k;
                    double total2 = 1e-10, e0 = 0;
                    for (int s = 0; s < kStems; s++) { const double e2 = (double)p.est[s][at] * p.est[s][at]; total2 += e2; if (s == 0) e0 = e2; }
                    const double m = (e0 + 1e-10 / kStems) / total2;
                    const size_t sa = ((size_t)t * kBins + (size_t)k) * 2;
                    sum += m * m * ((double)sp[sa] * sp[sa] + (double)sp[sa + 1] * sp[sa + 1]);
                }
            }
            worst = std::max(worst, std::fabs(e1[(size_t)t] - sum) / std::max(1e-9, sum));
        }
        check(worst < 1e-3, "the energy matches the reference (relative)", worst);
    }

    std::printf("NaN in the estimates and threads\n");
    {
        Piece p = make(0.8, 5);
        runStft(p, 3);
        p.est[0][1000] = std::numeric_limits<float>::quiet_NaN();
        p.est[2][2000] = std::numeric_limits<float>::infinity();
        const float* est[kStems];
        estPtrs(p, est);
        std::vector<float> gains((size_t)p.frames * kStems, 1.0f), l1((size_t)p.samples), r1((size_t)p.samples), l3((size_t)p.samples), r3((size_t)p.samples);
        render(p.spec0.data(), p.spec1.data(), est, p.frames, p.stride, p.samples, gains.data(), l1.data(), r1.data(), 1);
        render(p.spec0.data(), p.spec1.data(), est, p.frames, p.stride, p.samples, gains.data(), l3.data(), r3.data(), 3);
        bool finiteOut = true;
        for (float v : l3) if (!std::isfinite(v)) finiteOut = false;
        check(finiteOut, "a NaN or infinite estimate gives no NaN in the music", 0.0);
        check(maxDiff(l1, l3) < 1e-6 && maxDiff(r1, r3) < 1e-6, "1 thread and 3 threads give the same music", std::max(maxDiff(l1, l3), maxDiff(r1, r3)));
        check(maxDiff(l3, p.left) < 1e-4, "and it is still the original with all volumes 1", maxDiff(l3, p.left));
    }

    std::printf("speed (this computer, not a phone): 20 s of music\n");
    {
        Piece p = make(20.0, 6);
        const float* est[kStems];
        estPtrs(p, est);
        std::vector<float> gains((size_t)p.frames * kStems, 0.5f), l((size_t)p.samples), r((size_t)p.samples), e((size_t)p.frames);
        for (int threads : {1, 3}) {
            auto t0 = std::chrono::steady_clock::now();
            runStft(p, threads);
            auto t1 = std::chrono::steady_clock::now();
            energy(p.spec0.data(), p.spec1.data(), est, p.frames, p.stride, 0, e.data(), threads);
            auto t2 = std::chrono::steady_clock::now();
            render(p.spec0.data(), p.spec1.data(), est, p.frames, p.stride, p.samples, gains.data(), l.data(), r.data(), threads);
            auto t3 = std::chrono::steady_clock::now();
            auto ms = [](auto a, auto b) { return std::chrono::duration<double, std::milli>(b - a).count(); };
            std::printf("  %d thread(s): stft %.0f ms, energy %.0f ms, render %.0f ms\n", threads, ms(t0, t1), ms(t1, t2), ms(t2, t3));
        }
    }

    std::printf(failures ? "%d FAILED\n" : "all ok\n", failures);
    return failures ? 1 : 0;
}
