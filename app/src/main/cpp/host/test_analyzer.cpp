// Host test of the beat analysis on synthetic tracks with known beats. Exit code 0 when every check passes.
#include <algorithm>
#include <chrono>
#include <cstdio>

#include "../mix/analyzer.h"
#include "synth.h"

using namespace nooklin::mix;
using namespace nooklin::mix::host;

struct Case {
    double bpm, offset, drift;
};

static int failures = 0;
static void check(bool ok, const char* what, double v) {
    std::printf("  %-52s %s (%.4f)\n", what, ok ? "ok  " : "FAIL", v);
    if (!ok) failures++;
}

int main() {
    const int sr = 22050;  // what the Android decoder hands over after its 2:1 decimation
    const Case cases[] = {{124, 0.37, 0}, {128, 1.10, 0}, {90, 0.52, 0}, {140, 0.20, 0}, {120, 0.64, 2.0}, {100, 0.8, 0}};
    for (const Case& c : cases) {
        Synth s = makeTrack(sr, c.bpm, c.offset, 150.0, c.drift, 130.81, 7);
        auto mono = toMono(s.pcm);
        auto t0 = std::chrono::steady_clock::now();
        TrackMap m = analyzeTrack(mono.data(), mono.size(), sr);
        double ms = std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now() - t0).count();
        std::printf("bpm %.0f offset %.2f drift %.1f%%: found bpm %.3f, %zu beats, confidence %.2f, %.0f ms\n", c.bpm,
                    c.offset, c.drift, m.bpm, m.beats.size(), m.confidence, ms);
        check(m.valid(), "valid beat grid", m.valid() ? 1 : 0);
        if (!m.valid()) continue;
        if (c.drift == 0.0) check(std::fabs(m.bpm - c.bpm) / c.bpm < 0.002, "tempo within 0.2%", m.bpm);
        // beat error: each found beat against the nearest true beat
        std::vector<double> err;
        for (double b : m.beats) {
            double best = 1e9;
            for (double tb : s.beatTimes) best = std::min(best, std::fabs(tb - b));
            if (b > s.beatTimes.front() - 0.1 && b < s.beatTimes.back() + 0.1) err.push_back(best);
        }
        std::sort(err.begin(), err.end());
        const double med = err[err.size() / 2] * 1000.0, p95 = err[err.size() * 95 / 100] * 1000.0;
        check(med < 8.0, "median beat error under 8 ms", med);
        check(p95 < 20.0, "95th percentile beat error under 20 ms", p95);
        // Which true beat number an analysed beat time is (true beats before the first one are extrapolated)
        auto trueIndex = [&](double tm) {
            if (tm < s.beatTimes.front()) return (long)std::lround((tm - s.beatTimes.front()) / (s.beatTimes[1] - s.beatTimes[0]));
            double best = 1e9;
            long bi = 0;
            for (size_t i = 0; i < s.beatTimes.size(); i++) {
                const double d = std::fabs(s.beatTimes[i] - tm);
                if (d < best) {
                    best = d;
                    bi = (long)i;
                }
            }
            return bi;
        };
        const long dbIdx = trueIndex(m.beats[m.downbeatPhase]);
        check(((dbIdx % 4) + 4) % 4 == 0, "first beat of the bar is a true downbeat", (double)(((dbIdx % 4) + 4) % 4));
        long pi = -1;
        for (size_t i = 0; i < m.beats.size(); i++)
            if (m.isPhraseStart((long)i) && m.beats[i] > 60.0) {
                pi = (long)i;
                break;
            }
        if (pi >= 0) {
            const long ti = trueIndex(m.beats[pi]);
            check(((ti % 16) + 16) % 16 == 0, "phrase start is a true phrase start (16 beats)", (double)(((ti % 16) + 16) % 16));
        }
        check(ms < 20000.0, "analysis under 20 s for 150 s of audio", ms);
    }
    std::printf("%s (%d failures)\n", failures ? "FAILED" : "ALL OK", failures);
    return failures ? 1 : 0;
}
