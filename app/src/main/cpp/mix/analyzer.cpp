#include "analyzer.h"

#include <algorithm>
#include <cmath>
#include <numeric>

namespace nooklin::mix {
namespace {

constexpr double kPi = 3.14159265358979323846;

// In-place radix-2 FFT
void fft(std::vector<double>& re, std::vector<double>& im) {
    const size_t n = re.size();
    for (size_t i = 1, j = 0; i < n; i++) {
        size_t bit = n >> 1;
        for (; j & bit; bit >>= 1) j ^= bit;
        j ^= bit;
        if (i < j) {
            std::swap(re[i], re[j]);
            std::swap(im[i], im[j]);
        }
    }
    for (size_t len = 2; len <= n; len <<= 1) {
        const double ang = -2.0 * kPi / (double)len;
        const double wr = std::cos(ang), wi = std::sin(ang);
        for (size_t i = 0; i < n; i += len) {
            double cr = 1.0, ci = 0.0;
            for (size_t k = 0; k < len / 2; k++) {
                const size_t a = i + k, b = i + k + len / 2;
                const double tr = re[b] * cr - im[b] * ci;
                const double ti = re[b] * ci + im[b] * cr;
                re[b] = re[a] - tr;
                im[b] = im[a] - ti;
                re[a] += tr;
                im[a] += ti;
                const double nr = cr * wr - ci * wi;
                ci = cr * wi + ci * wr;
                cr = nr;
            }
        }
    }
}

double mean(const std::vector<double>& v, size_t from, size_t to) {
    from = std::min(from, v.size());
    to = std::min(to, v.size());
    if (to <= from) return 0.0;
    double s = 0.0;
    for (size_t i = from; i < to; i++) s += v[i];
    return s / (double)(to - from);
}

double median(std::vector<double> v) {
    if (v.empty()) return 0.0;
    std::nth_element(v.begin(), v.begin() + v.size() / 2, v.end());
    return v[v.size() / 2];
}

struct Features {
    double hopSec = 0.01;
    double centreSec = 0.0;  // time of the middle of a frame, from its start
    std::vector<double> low, mid, high;  // per band, normalised onset strength per frame
    std::vector<double> onset;           // combined, detrended, unit deviation
    std::vector<double> rms;
};

Features extract(const float* x, size_t n, int sr) {
    Features f;
    const size_t N = 2048;
    const size_t hop = (size_t)std::lround(sr * 0.01);
    f.hopSec = (double)hop / (double)sr;
    f.centreSec = (double)(N / 2) / (double)sr;
    if (n < N * 8) return f;
    const size_t frames = (n - N) / hop + 1;
    std::vector<double> win(N);
    for (size_t i = 0; i < N; i++) win[i] = 0.5 - 0.5 * std::cos(2.0 * kPi * (double)i / (double)N);
    auto bin = [&](double hz) { return (size_t)std::max(1.0, std::floor(hz * (double)N / (double)sr)); };
    const size_t b0 = bin(30), b1 = bin(150), b2 = bin(2500), b3 = std::min(N / 2 - 1, bin(12000));
    std::vector<double> re(N), im(N), prev(N / 2, 0.0);
    f.low.assign(frames, 0.0);
    f.mid.assign(frames, 0.0);
    f.high.assign(frames, 0.0);
    f.rms.assign(frames, 0.0);
    for (size_t t = 0; t < frames; t++) {
        const size_t at = t * hop;
        double e = 0.0;
        for (size_t i = 0; i < N; i++) {
            const double v = x[at + i];
            re[i] = v * win[i];
            im[i] = 0.0;
        }
        for (size_t i = N / 2 - hop / 2; i < N / 2 + hop / 2 && i < N; i++) e += (double)x[at + i] * x[at + i];
        f.rms[t] = std::sqrt(e / (double)hop);
        fft(re, im);
        double s[3] = {0, 0, 0};
        for (size_t k = 1; k <= b3; k++) {
            const double m = std::sqrt(re[k] * re[k] + im[k] * im[k]) / ((double)N / 4.0);
            const double L = std::log1p(100.0 * m);
            const double fl = std::max(0.0, L - prev[k]);
            prev[k] = L;
            if (k <= b1) {
                if (k >= b0) s[0] += fl;
            } else if (k <= b2) {
                s[1] += fl;
            } else {
                s[2] += fl;
            }
        }
        f.low[t] = s[0] / (double)std::max<size_t>(1, b1 - b0 + 1);
        f.mid[t] = s[1] / (double)std::max<size_t>(1, b2 - b1);
        f.high[t] = s[2] / (double)std::max<size_t>(1, b3 - b2);
    }
    auto norm = [&](std::vector<double>& v) {
        const double m = mean(v, 0, v.size()) + 1e-12;
        for (double& d : v) d /= m;
    };
    norm(f.low);
    norm(f.mid);
    norm(f.high);
    f.onset.assign(frames, 0.0);
    for (size_t t = 0; t < frames; t++) f.onset[t] = 1.0 * f.low[t] + 0.6 * f.mid[t] + 0.4 * f.high[t];
    // Remove the slow trend, keep what rises above it
    std::vector<double> o2(frames);
    const long w = 25;
    for (long t = 0; t < (long)frames; t++) {
        const size_t a = (size_t)std::max(0L, t - w), b = (size_t)std::min((long)frames, t + w + 1);
        o2[t] = std::max(0.0, f.onset[t] - mean(f.onset, a, b));
    }
    double var = 0.0;
    for (double d : o2) var += d * d;
    const double sd = std::sqrt(var / (double)frames) + 1e-12;
    for (double& d : o2) d /= sd;
    f.onset = std::move(o2);
    return f;
}

// Tempo: autocorrelation with a comb over the multiples of the lag and a prior around 125 BPM. The prior is wide (1.2
// octaves; 0.0.70 and before: 0.8): with the narrow one a song of 87 BPM lost against its 4/3 (116 BPM) when both scored
// the same, which happens with a dotted-eighth feel (log 0.0.69: the grid said 116, 129, 147 and 150 where the older
// analysis said 87, 94, 110 and 100).
constexpr double kTempoPriorOctaves = 1.2;
double estimateTempo(const Features& f) {
    const size_t T = f.onset.size();
    const double hs = f.hopSec;
    const size_t maxLag = (size_t)std::ceil(4.0 * 60.0 / 70.0 / hs) + 2;
    if (T < maxLag * 2) return 0.0;
    std::vector<double> acf(maxLag + 1, 0.0);
    for (size_t l = 0; l <= maxLag; l++) {
        double s = 0.0;
        for (size_t t = 0; t + l < T; t++) s += f.onset[t] * f.onset[t + l];
        acf[l] = s / (double)(T - l);
    }
    if (acf[0] <= 1e-12) return 0.0;
    for (double& a : acf) a /= acf[0];
    auto at = [&](double lag) {
        if (lag < 0 || lag >= (double)maxLag) return 0.0;
        const size_t i = (size_t)lag;
        const double fr = lag - (double)i;
        return acf[i] * (1.0 - fr) + acf[std::min(maxLag, i + 1)] * fr;
    };
    double best = -1.0, bestBpm = 0.0, sum = 0.0;
    int cnt = 0;
    for (double bpm = 70.0; bpm <= 180.0; bpm += 0.25) {
        const double lag = 60.0 / bpm / hs;
        const double comb = at(lag) + 0.5 * at(2.0 * lag) + 0.25 * at(4.0 * lag);
        const double prior = std::exp(-0.5 * std::pow(std::log2(bpm / 125.0) / kTempoPriorOctaves, 2.0));
        const double sc = comb * prior;
        sum += sc;
        cnt++;
        if (sc > best) {
            best = sc;
            bestBpm = bpm;
        }
    }
    if (best <= 0.0 || best < 1.5 * sum / (double)cnt) return 0.0;
    return bestBpm;
}

// Dynamic-programming beat tracking: returns beat frames (ascending)
std::vector<double> trackBeats(const Features& f, double bpm) {
    const size_t T = f.onset.size();
    const double P = 60.0 / bpm / f.hopSec;
    const long lo = (long)std::lround(0.7 * P), hi = (long)std::lround(1.4 * P);
    const double lambda = 300.0;
    std::vector<double> C(T, 0.0);
    std::vector<long> back(T, -1);
    std::vector<double> pen(hi - lo + 1);
    for (long tau = lo; tau <= hi; tau++) pen[tau - lo] = lambda * std::pow(std::log((double)tau / P), 2.0);
    for (size_t t = 0; t < T; t++) {
        double bestPrev = 0.0;
        long arg = -1;
        for (long tau = lo; tau <= hi; tau++) {
            const long p = (long)t - tau;
            if (p < 0) break;
            const double v = C[p] - pen[tau - lo];
            if (arg < 0 || v > bestPrev) {
                bestPrev = v;
                arg = p;
            }
        }
        if (arg >= 0 && bestPrev > 0.0) {
            C[t] = f.onset[t] + bestPrev;
            back[t] = arg;
        } else {
            C[t] = f.onset[t];
        }
    }
    size_t end = T - 1;
    double bestC = -1e300;
    for (size_t t = T - (size_t)std::min<long>((long)T, hi); t < T; t++) {
        if (C[t] > bestC) {
            bestC = C[t];
            end = t;
        }
    }
    std::vector<double> frames;
    for (long t = (long)end; t >= 0; t = back[t]) {
        frames.push_back((double)t);
        if (back[t] < 0) break;
    }
    std::reverse(frames.begin(), frames.end());
    // Sub-frame position by a parabola on the onset curve
    for (double& fr : frames) {
        const long i = (long)fr;
        if (i > 0 && i + 1 < (long)T) {
            const double a = f.onset[i - 1], b = f.onset[i], c = f.onset[i + 1];
            const double d = a - 2.0 * b + c;
            if (d < -1e-9) fr += std::clamp(0.5 * (a - c) / d, -0.5, 0.5);
        }
    }
    return frames;
}

// Which pulse level (family) is right, checked with the beat tracker itself. The tempo guess above can sit at 3/4 or 3/2
// of the real pulse (a dotted feel; Blank Space came out at 128 where it is 96), and the tracker then lays a clean grid on
// the wrong level. So the strongest autocorrelation candidates are each tracked, and a grid is scored by the share of the
// strong onsets that lie within kCoverTolSec of a beat or a half beat, corrected for the share a random grid would hit.
// The best level wins when it beats the best candidate outside its family (0.5, 1, 2 times) by kFamilyMargin; otherwise the
// guess above stays. The octave inside the winning family is the one the guess above scores highest.
constexpr int kTempoCandidates = 6;
constexpr double kCoverTolSec = 0.03;
constexpr double kFamilyMargin = 1.3;
constexpr double kFamilyRatioTol = 0.04;

double gridCoverage(const Features& f, double bpm) {
    const std::vector<double> bf = trackBeats(f, bpm);
    if (bf.size() < 16) return -1.0;
    const size_t T = f.onset.size();
    std::vector<double> gp;
    for (size_t i = 0; i + 1 < bf.size(); i++) {
        gp.push_back(bf[i] * f.hopSec);
        gp.push_back(0.5 * (bf[i] + bf[i + 1]) * f.hopSec);
    }
    double mu = 0.0, sd = 0.0;
    for (double v : f.onset) mu += v;
    mu /= (double)T;
    for (double v : f.onset) sd += (v - mu) * (v - mu);
    sd = std::sqrt(sd / (double)T);
    double total = 0.0, hit = 0.0;
    size_t j = 0;
    for (size_t t = 1; t + 1 < T; t++) {
        if (!(f.onset[t] > f.onset[t - 1] && f.onset[t] >= f.onset[t + 1] && f.onset[t] > mu + sd)) continue;
        const double ts = (double)t * f.hopSec;
        while (j + 1 < gp.size() && gp[j + 1] < ts) j++;
        double d = std::fabs(gp[j] - ts);
        if (j + 1 < gp.size()) d = std::min(d, std::fabs(gp[j + 1] - ts));
        total += f.onset[t];
        if (d < kCoverTolSec) hit += f.onset[t];
    }
    if (total <= 0.0) return -1.0;
    const double chance = std::min(0.95, 2.0 * kCoverTolSec / (60.0 / bpm / 2.0));
    return (hit / total - chance) / (1.0 - chance);
}

bool sameFamily(double a, double b) {
    const double r = a / b;
    for (double k : {0.5, 1.0, 2.0})
        if (std::fabs(r / k - 1.0) < kFamilyRatioTol) return true;
    return false;
}

double chooseTempo(const Features& f, std::vector<float>* evidence = nullptr) {
    const double guess = estimateTempo(f);
    if (guess <= 0.0) return 0.0;
    const size_t T = f.onset.size();
    const double hs = f.hopSec;
    const size_t maxLag = (size_t)std::ceil(4.0 * 60.0 / 60.0 / hs) + 2;
    if (T < maxLag * 2) return guess;
    std::vector<double> acf(maxLag + 1, 0.0);
    for (size_t l = 0; l <= maxLag; l++) {
        double s = 0.0;
        for (size_t t = 0; t + l < T; t++) s += f.onset[t] * f.onset[t + l];
        acf[l] = s / (double)(T - l);
    }
    if (acf[0] <= 1e-12) return guess;
    for (double& a : acf) a /= acf[0];
    auto at = [&](double lag) {
        if (lag < 0 || lag >= (double)maxLag) return 0.0;
        const size_t i = (size_t)lag;
        const double fr = lag - (double)i;
        return acf[i] * (1.0 - fr) + acf[std::min(maxLag, i + 1)] * fr;
    };
    auto comb = [&](double bpm) {
        const double lag = 60.0 / bpm / hs;
        return at(lag) + 0.5 * at(2.0 * lag) + 0.25 * at(4.0 * lag);
    };
    struct Cand { double bpm, score, cover; };
    std::vector<Cand> peaks;
    std::vector<double> sc;
    for (double bpm = 60.0; bpm <= 200.0; bpm += 0.25) sc.push_back(comb(bpm));
    for (size_t i = 1; i + 1 < sc.size(); i++)
        if (sc[i] > sc[i - 1] && sc[i] >= sc[i + 1]) peaks.push_back({60.0 + 0.25 * (double)i, sc[i], 0.0});
    std::sort(peaks.begin(), peaks.end(), [](const Cand& a, const Cand& b) { return a.score > b.score; });
    std::vector<Cand> cands;
    for (const Cand& c : peaks) {
        bool dup = false;
        for (const Cand& e : cands) if (std::fabs(c.bpm / e.bpm - 1.0) < 0.03) dup = true;
        if (!dup) cands.push_back(c);
        if ((int)cands.size() >= kTempoCandidates) break;
    }
    for (Cand& c : cands) c.cover = gridCoverage(f, c.bpm);
    if (evidence)
        for (const Cand& c : cands) {
            evidence->push_back((float)c.bpm);
            evidence->push_back((float)c.cover);
        }
    std::sort(cands.begin(), cands.end(), [](const Cand& a, const Cand& b) { return a.cover > b.cover; });
    if (cands.size() < 2 || cands[0].cover <= 0.0) return guess;
    double other = 0.0;
    for (size_t i = 1; i < cands.size(); i++)
        if (!sameFamily(cands[i].bpm, cands[0].bpm)) {
            other = cands[i].cover;
            break;
        }
    if (other > 0.0 && cands[0].cover < kFamilyMargin * other) return guess;
    if (sameFamily(guess, cands[0].bpm)) return guess;
    // Another family: the octave inside it that the guess above would score highest (70 to 180 BPM)
    double best = 0.0, bestBpm = cands[0].bpm;
    for (double k : {0.5, 1.0, 2.0}) {
        const double bpm = cands[0].bpm * k;
        if (bpm < 70.0 || bpm > 180.0) continue;
        const double sc2 = comb(bpm) * std::exp(-0.5 * std::pow(std::log2(bpm / 125.0) / kTempoPriorOctaves, 2.0));
        if (sc2 > best) {
            best = sc2;
            bestBpm = bpm;
        }
    }
    return bestBpm;
}

double rmsAt(const Features& f, double sec, double len) {
    const size_t a = (size_t)std::max(0.0, (sec - f.centreSec) / f.hopSec);
    const size_t b = (size_t)std::max(0.0, (sec + len - f.centreSec) / f.hopSec);
    return mean(f.rms, a, std::max(a + 1, b));
}

// 0.0.102: loudness of every beat relative to the 95th percentile of the music (the loud part of the song), not to its median.
// With the median a song with a long calm stretch read 1.0 in that stretch and 3 on its drop, so the same number meant different things in
// different songs. Mixed In Key's energy patent (US 8,865,993) also scales each track by its own 95th percentile loudness "to avoid
// penalizing musical compositions which have been mastered at a quieter volume level". The factor 1.4 keeps the numbers near the old
// ones for an ordinary song, so the thresholds of the planner stay usable; 1.4 is my estimate, not measured on a large set of songs.
constexpr double kEnergyTypical = 1.4;
void computeEnergy(TrackMap& map, const Features& f) {
    const size_t n = map.beats.size();
    map.energy.assign(n, 1.0f);
    std::vector<double> e(n, 0.0);
    for (size_t i = 0; i < n; i++) {
        const double len = i + 1 < n ? map.beats[i + 1] - map.beats[i] : map.beats[n - 1] - map.beats[n - 2];
        e[i] = rmsAt(f, map.beats[i], std::max(0.05, len));
    }
    std::vector<double> inside;
    const double end = map.cueOut > 0.0 ? map.cueOut : map.duration;
    for (size_t i = 0; i < n; i++)
        if (map.beats[i] >= map.cueIn && map.beats[i] < end) inside.push_back(e[i]);
    if (inside.empty()) return;
    std::sort(inside.begin(), inside.end());
    const double ref = inside[(size_t)((double)(inside.size() - 1) * 0.95)] / kEnergyTypical;
    if (ref <= 1e-9) return;
    for (size_t i = 0; i < n; i++) map.energy[i] = (float)std::min(3.0, e[i] / ref);
}

// Pitch class energy per block of 4 beats, only where the planner looks: the start and the end of the music. Tonal peaks of
// a long window are counted (not the broadband noise of drums), each at its nearest semitone.
void computeChroma(TrackMap& map, const float* x, size_t n, int sr) {
    const size_t N = 8192, hop = 4096;
    if (n < N * 2 || map.beats.size() < 16) return;
    const size_t blocks = (map.beats.size() + 3) / 4;
    map.chroma.assign(blocks * 12, 0.0f);
    const double end = map.cueOut > 0.0 ? map.cueOut : map.duration;
    const double w0 = map.cueIn + 100.0, w1 = end - 140.0;
    std::vector<double> win(N), re(N), im(N), mag(N / 2), pre(N / 2 + 1);
    for (size_t i = 0; i < N; i++) win[i] = 0.5 - 0.5 * std::cos(2.0 * kPi * (double)i / (double)N);
    const size_t kLo = (size_t)std::ceil(100.0 * (double)N / sr), kHi = std::min(N / 2 - 2, (size_t)std::floor(2000.0 * (double)N / sr));
    std::vector<double> acc(blocks * 12, 0.0);
    for (size_t at = 0; at + N <= n; at += hop) {
        const double tc = ((double)at + (double)N / 2.0) / (double)sr;
        if (tc < map.cueIn || tc > end) continue;
        if (tc > w0 && tc < w1) continue;
        for (size_t i = 0; i < N; i++) {
            re[i] = (double)x[at + i] * win[i];
            im[i] = 0.0;
        }
        fft(re, im);
        pre[0] = 0.0;
        for (size_t k = 0; k < N / 2; k++) {
            mag[k] = std::sqrt(re[k] * re[k] + im[k] * im[k]);
            pre[k + 1] = pre[k] + mag[k];
        }
        const double b = map.beatAtTime(tc);
        if (b < 0.0) continue;
        const size_t blk = (size_t)(b / 4.0);
        if (blk >= blocks) continue;
        for (size_t k = std::max<size_t>(kLo, 25); k <= kHi; k++) {
            if (!(mag[k] > mag[k - 1] && mag[k] >= mag[k + 1])) continue;
            const size_t lo = k - 24, hi = std::min(N / 2, k + 25);
            const double local = (pre[hi] - pre[lo]) / (double)(hi - lo);
            if (mag[k] < 3.0 * local) continue;  // not a tonal peak
            const double a = mag[k - 1], c = mag[k + 1], bb = mag[k];
            const double d = a - 2.0 * bb + c;
            const double delta = d < -1e-12 ? std::clamp(0.5 * (a - c) / d, -0.5, 0.5) : 0.0;
            const double hz = ((double)k + delta) * (double)sr / (double)N;
            const long pitch = std::lround(12.0 * std::log2(hz / 440.0) + 69.0);
            const int pc = (int)(((pitch % 12) + 12) % 12);
            acc[blk * 12 + (size_t)pc] += std::sqrt(mag[k]);
        }
    }
    for (size_t i = 0; i < acc.size(); i++) map.chroma[i] = (float)acc[i];
}


// Sections: bars are described by loudness, onsets in the three bands and total onset density; a checkerboard kernel over the
// bar-to-bar similarity finds where the sound changes (a new section: the novelty peaks), boundaries are put on bar lines
// (a bar line), and each stretch is called high (louder and busier than the average) or low. Neighbours of the same kind merge.
void computeSections(TrackMap& map, const Features& f) {
    map.sections.clear();
    const long n = (long)map.beats.size();
    if (n < 96) return;
    const long d0 = map.downbeatPhase;
    const long nb = (n - d0) / 4;
    if (nb < 24) return;
    const double end = map.cueOut > 0.0 ? map.cueOut : map.duration;
    auto frameMean = [&](const std::vector<double>& v, double t0, double t1) {
        const long a = std::max(0L, (long)std::floor((t0 - f.centreSec) / f.hopSec));
        const long b = std::min((long)v.size(), (long)std::ceil((t1 - f.centreSec) / f.hopSec));
        if (b <= a) return 0.0;
        double s = 0.0;
        for (long i = a; i < b; i++) s += v[(size_t)i];
        return s / (double)(b - a);
    };
    const int F = 5;
    std::vector<double> v((size_t)nb * F, 0.0);
    std::vector<char> inside((size_t)nb, 0);
    for (long k = 0; k < nb; k++) {
        const double t0 = map.beats[(size_t)(d0 + 4 * k)];
        const double t1 = d0 + 4 * k + 4 < n ? map.beats[(size_t)(d0 + 4 * k + 4)] : t0 + 4.0 * 60.0 / map.bpm;
        v[(size_t)k * F + 0] = rmsAt(f, t0, t1 - t0);
        v[(size_t)k * F + 1] = frameMean(f.low, t0, t1);
        v[(size_t)k * F + 2] = frameMean(f.mid, t0, t1);
        v[(size_t)k * F + 3] = frameMean(f.high, t0, t1);
        v[(size_t)k * F + 4] = v[(size_t)k * F + 1] + v[(size_t)k * F + 2] + v[(size_t)k * F + 3];
        inside[(size_t)k] = t0 >= map.cueIn - 0.5 && t1 <= end + 0.5;
    }
    // Per feature: relative to its mean over the music, then centred
    double fmean[F];
    long cnt = 0;
    for (long k = 0; k < nb; k++) cnt += inside[(size_t)k];
    if (cnt < 16) return;
    for (int j = 0; j < F; j++) {
        double s = 0.0;
        for (long k = 0; k < nb; k++)
            if (inside[(size_t)k]) s += v[(size_t)k * F + j];
        fmean[j] = s / (double)cnt + 1e-12;
    }
    std::vector<double> rel((size_t)nb * F), cen((size_t)nb * F);
    for (long k = 0; k < nb; k++)
        for (int j = 0; j < F; j++) rel[(size_t)k * F + j] = std::min(3.0, v[(size_t)k * F + j] / fmean[j]);
    for (int j = 0; j < F; j++) {
        double s = 0.0;
        for (long k = 0; k < nb; k++)
            if (inside[(size_t)k]) s += rel[(size_t)k * F + j];
        s /= (double)cnt;
        for (long k = 0; k < nb; k++) cen[(size_t)k * F + j] = rel[(size_t)k * F + j] - s;
    }
    // Similarity of every pair of bars: the dot product of the centred vectors over the mean squared size. A song that hardly
    // changes has tiny vectors, and the floor keeps its noise from looking like structure.
    std::vector<float> S((size_t)nb * (size_t)nb, 0.0f);
    double avgsq = 0.0;
    for (long k = 0; k < nb; k++)
        if (inside[(size_t)k])
            for (int j = 0; j < F; j++) avgsq += cen[(size_t)k * F + j] * cen[(size_t)k * F + j];
    avgsq /= (double)cnt;
    const double scale = avgsq + 0.15;
    for (long a = 0; a < nb; a++)
        for (long b = a; b < nb; b++) {
            double d = 0.0;
            for (int j = 0; j < F; j++) d += cen[(size_t)a * F + j] * cen[(size_t)b * F + j];
            const float c = (float)std::clamp(d / scale, -1.5, 1.5);
            S[(size_t)a * (size_t)nb + (size_t)b] = c;
            S[(size_t)b * (size_t)nb + (size_t)a] = c;
        }
    // Novelty at the start of bar k: a Gaussian checkerboard of 8 bars on each side
    const long N = std::min(8L, nb / 4);
    std::vector<double> g((size_t)(2 * N));
    for (long i = 0; i < 2 * N; i++) {
        const double x = ((double)i - ((double)N - 0.5)) / ((double)N / 2.0);
        g[(size_t)i] = std::exp(-0.5 * x * x);
    }
    double wsum = 0.0;
    for (long i = 0; i < 2 * N; i++)
        for (long j = 0; j < 2 * N; j++) wsum += g[(size_t)i] * g[(size_t)j];
    std::vector<double> nov((size_t)nb, 0.0);
    for (long k = 0; k < nb; k++) {
        double s = 0.0;
        for (long i = 0; i < 2 * N; i++) {
            const long a = std::clamp(k - N + i, 0L, nb - 1);
            for (long j = 0; j < 2 * N; j++) {
                const long b = std::clamp(k - N + j, 0L, nb - 1);
                const double sign = ((i < N) == (j < N)) ? 1.0 : -1.0;
                s += sign * g[(size_t)i] * g[(size_t)j] * (double)S[(size_t)a * (size_t)nb + (size_t)b];
            }
        }
        nov[(size_t)k] = std::max(0.0, s);
    }
    for (double& d : nov) d /= wsum;  // 1 would be a perfect boundary
    // Boundaries: peaks of the novelty (a bar line), at least 4 bars apart
    std::vector<long> bounds;
    const long firstBar = [&] {
        for (long k = 0; k < nb; k++)
            if (inside[(size_t)k]) return k;
        return 0L;
    }();
    bounds.push_back(firstBar);
    for (long k = 4; k < nb - 4; k++) {
        if (nov[(size_t)k] < 0.2) continue;
        bool peak = true;
        for (long d = -2; d <= 2; d++)
            if (d != 0 && k + d >= 0 && k + d < nb && nov[(size_t)(k + d)] > nov[(size_t)k]) peak = false;
        if (!peak) continue;
        const long snapped = k;
        if (snapped <= bounds.back() + 3 || snapped >= nb - 3) continue;
        const double te = map.beats[(size_t)(d0 + 4 * snapped)];
        if (te >= end - 4.0) continue;
        bounds.push_back(snapped);
    }
    // High or low per stretch
    struct Seg { long bar; bool high; };
    std::vector<Seg> segs;
    for (size_t s = 0; s < bounds.size(); s++) {
        const long a = bounds[s];
        long b = s + 1 < bounds.size() ? bounds[s + 1] : nb;
        while (b > a + 1 && !inside[(size_t)(b - 1)]) b--;
        double r = 0.0, o = 0.0;
        for (long k = a; k < b; k++) {
            r += rel[(size_t)k * F + 0];
            o += rel[(size_t)k * F + 4];
        }
        r /= (double)std::max(1L, b - a);
        o /= (double)std::max(1L, b - a);
        segs.push_back({a, r >= 1.0 && o >= 1.0});
    }
    // Neighbours of the same kind merge
    std::vector<Seg> merged;
    for (const Seg& sg : segs)
        if (merged.empty() || merged.back().high != sg.high) merged.push_back(sg);
    // A fade-out or a slow wind-down has no sharp boundary: when the last bars stay clearly below the usual level for at
    // least 8 bars, they count as a low stretch of their own (the outro the planner wants to blend into)
    if (!merged.empty() && merged.back().high) {
        std::vector<double> lv;
        for (long k = 0; k < nb; k++)
            if (inside[(size_t)k]) lv.push_back(rel[(size_t)k * F + 0]);
        const double usual = median(lv);
        long lastBar = nb - 1;
        while (lastBar > 0 && !inside[(size_t)lastBar]) lastBar--;
        long startBar = lastBar + 1;
        while (startBar - 1 > merged.back().bar && rel[(size_t)(startBar - 1) * F + 0] < 0.65 * usual) startBar--;
        if (lastBar + 1 - startBar >= 8 && startBar >= merged.back().bar + 4) merged.push_back({startBar, false});
    }
    for (const Seg& sg : merged) {
        TrackMap::Section sec;
        sec.beat = d0 + 4 * sg.bar;
        sec.high = sg.high;
        const double a = map.meanEnergy((double)sec.beat, (double)sec.beat + 16.0);
        const double b = map.meanEnergy((double)sec.beat - 16.0, (double)sec.beat);
        sec.ratio = (float)(a / std::max(0.05, b));
        map.sections.push_back(sec);
    }
}

// 0.0.103: where the music can be mixed in and out, how long a phrase is and how swung the rhythm is. The edges are found twice, from the
// energy curve and from the sections; where the two disagree the confidence is low and the planner falls back on a loop or an exit.
void computeCues(TrackMap& map, const Features& f) {
    const long nb = (long)map.beats.size();
    const double end = map.cueOut > 0.0 ? map.cueOut : map.duration;
    long first = 0;
    while (first < nb && map.beats[(size_t)first] < map.cueIn) first++;
    long last = nb - 1;
    while (last > first && map.beats[(size_t)last] >= end) last--;
    if (last - first < 32) return;
    map.energyStart = map.meanEnergy((double)first, (double)first + 16.0);
    map.energyEnd = map.meanEnergy((double)last - 16.0, (double)last);

    // Energy based edges: the music is "full" at 0.8 of its loud level, counted in whole bars
    const double full = 0.8;
    long ie = first;
    while (ie + 4 <= last && map.meanEnergy((double)ie, (double)ie + 4.0) < full) ie += 4;
    long os = last;
    while (os - 4 >= first && map.meanEnergy((double)os - 4.0, (double)os) < full) os -= 4;
    // Section based edges: the first high section and the end of the last high section
    long sIn = -1, sOut = -1;
    for (size_t i = 0; i < map.sections.size(); i++) {
        if (!map.sections[i].high) continue;
        if (sIn < 0) sIn = map.sections[i].beat;
        sOut = (long)map.sectionEndBeat((int)i);
    }
    double conf = 0.4 * std::min(1.0, std::max(0.0, map.confidence));
    long introEnd = ie, outroStart = os;
    if (sIn >= 0) {
        const long d = std::labs(sIn - ie);
        conf += 0.3 * (d <= 4 ? 1.0 : d <= 8 ? 0.7 : d <= 16 ? 0.3 : 0.0);
        // A quiet start that the sections see and the energy does not: take the later of the two (the safer, a shorter overlap)
        introEnd = std::max(ie, std::min(sIn, ie + 32));
        if (d > 16) introEnd = ie;
        const long d2 = std::labs(sOut - os);
        conf += 0.3 * (d2 <= 4 ? 1.0 : d2 <= 8 ? 0.7 : d2 <= 16 ? 0.3 : 0.0);
        outroStart = std::min(os, std::max(sOut, os - 32));
        if (d2 > 16) outroStart = os;
    }
    if (map.sections.size() < 3) conf *= 0.7;
    map.cueConfidence = std::min(1.0, std::max(0.0, conf));
    map.introEnd = (double)introEnd;
    map.outroStart = (double)outroStart;
    map.usableIntroBeats = (int)std::max(0L, introEnd - first);
    map.usableOutroBeats = (int)std::max(0L, last - outroStart);

    // Phrase length from the section starts: the longest of 32, 16 or 8 that most of them are a multiple of, counted from the first one
    map.phraseBeats = 16;
    if (map.sections.size() >= 4) {
        int c8 = 0, c16 = 0, c32 = 0, total = 0;
        const long base = map.sections[0].beat;
        for (size_t i = 1; i < map.sections.size(); i++) {
            const long d = map.sections[i].beat - base;
            total++;
            if (d % 8 == 0) c8++;
            if (d % 16 == 0) c16++;
            if (d % 32 == 0) c32++;
        }
        if (total >= 3) {
            if (c32 >= 0.75 * total) map.phraseBeats = 32;
            else if (c16 >= 0.75 * total) map.phraseBeats = 16;
            else if (c8 >= 0.75 * total) map.phraseBeats = 8;
        }
    }

    // Swing: where the strongest onset between two beats lies (0.5 straight). A shuffled or 2-step groove puts it later.
    std::vector<double> pos;
    for (long i = first; i + 1 < last && i < first + 400; i++) {
        const double t0 = map.beats[(size_t)i], t1 = map.beats[(size_t)i + 1];
        const long a = (long)((t0 + 0.25 * (t1 - t0) - f.centreSec) / f.hopSec), b = (long)((t0 + 0.85 * (t1 - t0) - f.centreSec) / f.hopSec);
        double best = 0.0;
        long bestAt = -1;
        for (long j = a; j <= b; j++)
            if (j >= 0 && j < (long)f.onset.size() && f.onset[(size_t)j] > best) { best = f.onset[(size_t)j]; bestAt = j; }
        if (bestAt >= 0 && best > 1.0) pos.push_back(((double)bestAt * f.hopSec + f.centreSec - t0) / (t1 - t0));
    }
    if (pos.size() >= 32) map.swing = median(pos);
    map.hasCues = true;
}

}  // namespace

TrackMap analyzeTrack(const float* x, size_t n, int sr, double forcedBpm) {
    TrackMap map;
    map.duration = (double)n / (double)sr;
    Features f = extract(x, n, sr);
    if (f.onset.empty()) return map;
    const size_t T = f.onset.size();

    // Where the music starts and ends
    {
        std::vector<double> sm(T);
        for (size_t t = 0; t < T; t++) sm[t] = mean(f.rms, t > 15 ? t - 15 : 0, t + 15);
        const double lim = 0.1 * median(sm);
        long first = -1, last = -1;
        for (size_t t = 0; t < T; t++) {
            if (sm[t] > lim) {
                if (first < 0) first = (long)t;
                last = (long)t;
            }
        }
        if (first >= 0) {
            map.cueIn = (double)first * f.hopSec;
            map.cueOut = (double)(last + 1) * f.hopSec;
        }
    }

    std::vector<float> evidence;
    double rough = chooseTempo(f, &evidence);
    if (forcedBpm > 0.0) rough = forcedBpm;
    if (rough <= 0.0) return map;
    {
        // The grid itself first: its coverage is the one of the candidate at the same tempo, or measured now
        double own = 2.0;
        for (size_t i = 0; i + 1 < evidence.size(); i += 2)
            if (std::fabs(evidence[i] / rough - 1.0) < 0.01) own = evidence[i + 1];
        if (own > 1.5) own = gridCoverage(f, rough);
        map.tempoEvidence.push_back((float)rough);
        map.tempoEvidence.push_back((float)own);
        map.tempoEvidence.insert(map.tempoEvidence.end(), evidence.begin(), evidence.end());
    }
    std::vector<double> bf = trackBeats(f, rough);
    if (bf.size() < 16) return map;

    // Beat numbers from the intervals (the tracker may skip a beat), then a local line fit per beat so that the grid
    // follows slow drift (a live drummer, a tempo ramp) without the jitter of single onsets
    std::vector<double> t(bf.size());
    for (size_t i = 0; i < bf.size(); i++) t[i] = bf[i] * f.hopSec + f.centreSec;
    const size_t m = t.size();
    std::vector<double> iv(m - 1);
    for (size_t i = 1; i < m; i++) iv[i - 1] = t[i] - t[i - 1];
    std::vector<long> k(m, 0);
    for (size_t i = 1; i < m; i++) {
        std::vector<double> w;
        for (size_t j = (i > 9 ? i - 9 : 1); j <= std::min(m - 1, i + 8); j++) w.push_back(iv[j - 1]);
        const double per = median(w);
        k[i] = k[i - 1] + std::max(1L, (long)std::lround(iv[i - 1] / per));
    }
    std::vector<double> sm(m, 0.0);
    std::vector<char> use(m, 1);
    for (int pass = 0; pass < 2; pass++) {
        for (size_t i = 0; i < m; i++) {
            double sk = 0, st = 0, skk = 0, skt = 0;
            int c = 0;
            for (size_t j = (i > 8 ? i - 8 : 0); j <= std::min(m - 1, i + 8); j++) {
                if (!use[j]) continue;
                const double kk = (double)(k[j] - k[i]);
                sk += kk;
                st += t[j];
                skk += kk * kk;
                skt += kk * t[j];
                c++;
            }
            const double den = c * skk - sk * sk;
            sm[i] = (c >= 4 && std::fabs(den) > 1e-9) ? (st - (c * skt - sk * st) / den * sk) / c : t[i];
        }
        for (size_t i = 0; i < m; i++) use[i] = std::fabs(t[i] - sm[i]) < 0.035;
    }
    // Constant tempo (most dance music): one straight line through all beats is exact and has no local noise, which a
    // planner needs to match two songs. It is used when nearly every beat lies within 25 ms of the line; otherwise the
    // local fit above stays (a live drummer, a tempo ramp).
    {
        std::vector<char> in(m, 1);
        double t0 = 0.0, per = 0.0;
        bool fitted = false;
        const double lims[4] = {0.05, 0.03, 0.025, 0.025};
        for (int pass = 0; pass < 4; pass++) {
            double sk = 0, st = 0, skk = 0, skt = 0;
            long c = 0;
            for (size_t i = 0; i < m; i++) {
                if (!in[i]) continue;
                const double kk = (double)k[i];
                sk += kk;
                st += t[i];
                skk += kk * kk;
                skt += kk * t[i];
                c++;
            }
            const double den = (double)c * skk - sk * sk;
            if (c < 16 || std::fabs(den) < 1e-9) {
                fitted = false;
                break;
            }
            per = ((double)c * skt - sk * st) / den;
            t0 = (st - per * sk) / (double)c;
            fitted = true;
            for (size_t i = 0; i < m; i++) in[i] = std::fabs(t[i] - (t0 + per * (double)k[i])) < lims[pass];
        }
        if (fitted && per > 0.0) {
            size_t near = 0;
            for (size_t i = 0; i < m; i++)
                if (std::fabs(t[i] - (t0 + per * (double)k[i])) < 0.025) near++;
            if ((double)near >= 0.85 * (double)m) {
                for (size_t i = 0; i < m; i++) sm[i] = t0 + per * (double)k[i];
                map.constantTempo = true;
            }
        }
    }
    size_t good = 0;
    for (size_t i = 0; i < m; i++)
        if (std::fabs(t[i] - sm[i]) < 0.025) good++;
    map.confidence = (double)good / (double)m;
    if (map.confidence < 0.6) return map;

    // The flux frames lag the true attack; measure the lag on the waveform (median over all beats) and take it away
    double shift = 0.0;
    {
        const int w8 = std::max(1, (int)(0.008 * sr)), lagS = std::max(1, (int)(0.004 * sr));
        std::vector<double> offs;
        for (size_t i = 0; i < m; i += 2) {
            const long c0 = (long)std::lround(sm[i] * sr);
            const long from = c0 - (long)(0.09 * sr), to = c0 + (long)(0.09 * sr);
            if (from < w8 + lagS || to + lagS + w8 >= (long)n) continue;
            double best = -1.0;
            long bestAt = c0;
            for (long j = from; j < to; j += 2) {
                double a1 = 0, a0 = 0;
                for (int q = 0; q < w8; q += 2) {
                    a1 += std::fabs(x[j + lagS + q]);
                    a0 += std::fabs(x[j - lagS + q]);
                }
                if (a1 - a0 > best) {
                    best = a1 - a0;
                    bestAt = j;
                }
            }
            offs.push_back((double)(bestAt - c0) / sr);
        }
        if (offs.size() >= 8) shift = median(offs);
    }
    for (double& v : sm) v += shift;

    // Grid over the whole song: interpolate the smoothed times between known beat numbers, extend with the edge intervals
    const long kLast = k[m - 1];
    auto gridTime = [&](long kk) {
        if (kk <= 0) return sm[0] + (double)kk * (sm[1] - sm[0]) / (double)(k[1] - k[0]);
        if (kk >= kLast) return sm[m - 1] + (double)(kk - kLast) * (sm[m - 1] - sm[m - 2]) / (double)(k[m - 1] - k[m - 2]);
        const size_t hi = std::lower_bound(k.begin(), k.end(), kk) - k.begin();
        if (k[hi] == kk) return sm[hi];
        const double fr = (double)(kk - k[hi - 1]) / (double)(k[hi] - k[hi - 1]);
        return sm[hi - 1] + fr * (sm[hi] - sm[hi - 1]);
    };
    long kmin = 0;
    while (gridTime(kmin - 1) >= 0.0) kmin--;
    while (gridTime(kmin) < 0.0) kmin++;
    for (long kk = kmin; gridTime(kk) <= map.duration - 0.05; kk++) map.beats.push_back(gridTime(kk));
    const long count = (long)map.beats.size();
    if (count < 16) {
        map.beats.clear();
        return map;
    }
    map.bpm = 60.0 / ((map.beats.back() - map.beats.front()) / (double)(count - 1));

    // First beat of the bar: kick and body onsets on the beat, plus a rise in loudness at the bar line
    // Flux frames lag the attack, so look for the strongest onset in a window after the beat
    auto peak = [&](const std::vector<double>& v, double sec) {
        const long a = (long)std::floor((sec - f.centreSec - 0.03) / f.hopSec), b = (long)std::ceil((sec - f.centreSec + 0.10) / f.hopSec);
        double m = 0.0;
        for (long j = a; j <= b; j++)
            if (j >= 0 && j < (long)v.size()) m = std::max(m, v[j]);
        return m;
    };
    const double meanRms = mean(f.rms, 0, f.rms.size()) + 1e-9;
    double score[4] = {0, 0, 0, 0};
    for (long i = 0; i < count; i++) {
        const double ts = map.beats[i];
        double z = peak(f.low, ts);  // the kick; the mid band holds the backbeat (snare, clap) on beats 2 and 4
        const double pb = map.beats[i] - map.beats[std::max(0L, i - 4)];
        const double nb = map.beats[std::min(count - 1, i + 4)] - map.beats[i];
        const double rise = rmsAt(f, ts, nb) - rmsAt(f, std::max(0.0, ts - pb), pb);
        z += 1.5 * std::max(0.0, rise) / meanRms;
        score[i % 4] += z;
    }
    int bestPhase = 0;
    for (int p = 1; p < 4; p++)
        if (score[p] > score[bestPhase]) bestPhase = p;
    map.downbeatPhase = bestPhase;

    // Phrase phase: at which bar (0..3) a section change most often falls
    double nov[4] = {0, 0, 0, 0};
    long bar = 0;
    for (long i = bestPhase; i < count; i += 4, bar++) {
        const long pre = std::max(0L, i - 16), post = std::min(count - 1, i + 16);
        const double before = rmsAt(f, map.beats[pre], std::max(0.1, map.beats[i] - map.beats[pre]));
        const double after = rmsAt(f, map.beats[i], std::max(0.1, map.beats[post] - map.beats[i]));
        if (i - pre == 16 && post - i == 16) nov[bar % 4] += std::fabs(after - before) / meanRms;
    }
    int ph = 0;
    for (int p = 1; p < 4; p++)
        if (nov[p] > nov[ph]) ph = p;
    map.phrasePhase = ph;
    computeEnergy(map, f);
    computeChroma(map, x, n, sr);
    computeSections(map, f);
    {
        // Level of the music, to bring every song to the same loudness
        const double end = map.cueOut > 0.0 ? map.cueOut : map.duration;
        const size_t a0 = (size_t)std::max(0.0, (map.cueIn - f.centreSec) / f.hopSec);
        const size_t b0 = (size_t)std::max(0.0, (end - f.centreSec) / f.hopSec);
        if (b0 > a0 + 10) {
            double s = 0.0;
            for (size_t i = a0; i < std::min(b0, f.rms.size()); i++) s += (double)f.rms[i] * (double)f.rms[i];
            s /= (double)(std::min(b0, f.rms.size()) - a0);
            map.loudness = s > 1e-12 ? 10.0 * std::log10(s) : 0.0;
        }
    }
    computeCues(map, f);
    return map;
}

}  // namespace nooklin::mix
