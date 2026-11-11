#pragma once
// Key estimation from a pitch class profile (Krumhansl-Schmuckler) and how much two keys clash (Camelot wheel).
#include <algorithm>
#include <cmath>

namespace nooklin::mix {

struct Key {
    int root = -1;       // pitch class of the tonic, 0 = C; -1: not known
    bool minor = false;
    double fit = 0.0;    // correlation with the best key profile
    double margin = 0.0; // how much better it is than the next best key
    bool known() const { return root >= 0; }
    /** 0..1: how sure the estimate is (a flat profile or two close candidates give little). */
    double confidence() const {
        if (!known()) return 0.0;
        return std::min(1.0, std::max(0.0, margin / 0.08)) * std::min(1.0, std::max(0.0, (fit - 0.25) / 0.2));
    }
    int code() const { return known() ? root + (minor ? 12 : 0) : -1; }
};

inline Key estimateKey(const double c[12]) {
    static const double maj[12] = {6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88};
    static const double mnr[12] = {6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17};
    double sum = 0.0;
    for (int i = 0; i < 12; i++) sum += c[i];
    Key k;
    if (sum <= 1e-9) return k;
    const double mc = sum / 12.0;
    double best = -2.0, second = -2.0;
    int bestRoot = -1;
    bool bestMinor = false;
    for (int minor = 0; minor < 2; minor++) {
        const double* prof = minor ? mnr : maj;
        double mp = 0.0;
        for (int i = 0; i < 12; i++) mp += prof[i];
        mp /= 12.0;
        for (int r = 0; r < 12; r++) {
            double sxy = 0, sxx = 0, syy = 0;
            for (int i = 0; i < 12; i++) {
                const double x = c[(i + r) % 12] - mc, y = prof[i] - mp;
                sxy += x * y;
                sxx += x * x;
                syy += y * y;
            }
            const double corr = sxx > 1e-12 && syy > 1e-12 ? sxy / std::sqrt(sxx * syy) : 0.0;
            if (corr > best) {
                second = best;
                best = corr;
                bestRoot = r;
                bestMinor = minor == 1;
            } else if (corr > second) {
                second = corr;
            }
        }
    }
    k.root = bestRoot;
    k.minor = bestMinor;
    k.fit = best;
    k.margin = best - second;
    return k;
}

/**
 * 0 (fits) to 1 (clashes) between two keys on the Camelot wheel: the same key, its relative major or minor and the
 * neighbours on the circle of fifths mix well, a whole step apart is a risk, further is a clash. 0 when a key is unknown.
 */
inline double keyClash(const Key& a, const Key& b) {
    if (!a.known() || !b.known()) return 0.0;
    // 0.0.126: the same tonic in major and minor (C and C minor): the detection often flips the mode of one song (log 0.0.125: 4 songs read as both)
    if (a.root == b.root && a.minor != b.minor) return 0.3;
    auto pos = [](const Key& k) { return (7 * (k.root + (k.minor ? 3 : 0))) % 12; };  // place on the circle of fifths
    int d = std::abs(pos(a) - pos(b));
    d = std::min(d, 12 - d);
    if (a.minor != b.minor) d += 1;
    static const double table[] = {0.0, 0.0, 0.3, 0.7, 1.0};
    return table[std::min(d, 4)];
}

}  // namespace nooklin::mix
