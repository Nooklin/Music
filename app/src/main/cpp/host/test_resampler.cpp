#include <cmath>
#include <cstdio>
#include <vector>

#include "../mix/resampler.h"
using namespace nooklin::mix;

int main() {
    int failures = 0;
    for (int src : {48000, 32000, 22050, 96000}) {
        const double f = 1000.0, amp = 0.5;
        const int n = src * 2;
        std::vector<int16_t> in(2 * n);
        for (int i = 0; i < n; i++) in[2 * i] = in[2 * i + 1] = (int16_t)(amp * 32767 * std::sin(2 * M_PI * f * i / src));
        Resampler r(src, 44100);
        std::vector<int16_t> out;
        for (int i = 0; i < n; i += 1000) r.process(&in[2 * i], std::min(1000, n - i), out);
        const int m = (int)(out.size() / 2);
        // The output must be the same sine at 44100; skip the edges
        double worst = 0;
        for (int i = 2000; i < m - 2000; i++) {
            const double want = amp * 32767 * std::sin(2 * M_PI * f * (i / 44100.0) + 0.0);
            worst = std::max(worst, std::fabs(out[2 * i] - want));
        }
        const double expectLen = 2.0 * 44100;
        const bool ok = worst < 60.0 && std::fabs(m - expectLen) < 40;
        std::printf("%6d -> 44100: %d frames (expected %.0f), worst error %.1f of 32767 %s\n", src, m, expectLen, worst, ok ? "ok" : "FAIL");
        if (!ok) failures++;
    }
    std::printf("%s\n", failures ? "FAILED" : "ALL OK");
    return failures ? 1 : 0;
}
