// Host test of the tempo evidence: the analysis keeps how well the grid and the strongest candidates fit the onsets.
#include <chrono>
#include <cmath>
#include <cstdio>

#include "../mix/analyzer.h"
#include "synth.h"

using namespace nooklin::mix;
using namespace nooklin::mix::host;

static int failures = 0;
static void check(bool ok, const char* what, double v) {
    std::printf("  %-64s %s (%.3f)\n", what, ok ? "ok  " : "FAIL", v);
    if (!ok) failures++;
}

int main() {
    const int sr = 44100;
    for (double bpm : {85.0, 124.0, 150.0}) {
        Synth s = makeTrack(sr, bpm, 0.3, 150.0, 0.0, 130.81, 7);
        std::vector<float> dec(s.pcm.size() / 4);
        for (size_t i = 0; i < dec.size(); i++) dec[i] = 0.25f * (s.pcm[4 * i] + s.pcm[4 * i + 1] + s.pcm[4 * i + 2] + s.pcm[4 * i + 3]);
        const auto t0 = std::chrono::steady_clock::now();
        TrackMap m = analyzeTrack(dec.data(), dec.size(), sr / 2);
        const double ms = std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now() - t0).count();
        std::printf("track %.0f bpm: grid %.2f, %zu evidence pairs, analysis %.0f ms\n", bpm, m.bpm, m.tempoEvidence.size() / 2, ms);
        check(m.valid(), "grid found", m.bpm);
        check(m.tempoEvidence.size() >= 4 && m.tempoEvidence.size() % 2 == 0, "evidence kept as pairs", (double)m.tempoEvidence.size());
        if (m.tempoEvidence.size() >= 2) {
            check(std::fabs(m.tempoEvidence[0] / m.bpm - 1.0) < 0.01, "first pair is the grid", m.tempoEvidence[0]);
            check(m.tempoEvidence[1] > 0.3, "the grid covers the strong onsets", m.tempoEvidence[1]);
            double rival = -2.0;
            for (size_t i = 2; i + 1 < m.tempoEvidence.size(); i += 2)
                if (std::fabs(m.tempoEvidence[i] / m.bpm - 1.0) > 0.1) rival = std::max(rival, (double)m.tempoEvidence[i + 1]);
            check(m.tempoEvidence[1] >= rival, "no candidate at another tempo fits better", rival);
        }
    }
    std::printf(failures ? "%d FAILED\n" : "all ok\n", failures);
    return failures ? 1 : 0;
}
