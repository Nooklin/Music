// Host test of 0.0.95: a grid can be built at a given tempo.
#include <cmath>
#include <cstdio>

#include "../mix/analyzer.h"
#include "synth.h"

using namespace nooklin::mix;
using namespace nooklin::mix::host;

static int failures = 0;
static void check(bool ok, const char* what, double v) {
    std::printf("  %-70s %s (%.3f)\n", what, ok ? "ok  " : "FAIL", v);
    if (!ok) failures++;
}
static std::vector<float> decimate(const Synth& s) {
    std::vector<float> dec(s.pcm.size() / 4);
    for (size_t i = 0; i < dec.size(); i++) dec[i] = 0.25f * (s.pcm[4 * i] + s.pcm[4 * i + 1] + s.pcm[4 * i + 2] + s.pcm[4 * i + 3]);
    return dec;
}

int main() {
    const int sr = 44100;
    std::vector<int> lv = {0, 0, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2};
    Synth s = makeStructured(sr, 120.0, 0.35, lv, 130.81, 3);
    const std::vector<float> dec = decimate(s);
    TrackMap m = analyzeTrack(dec.data(), dec.size(), sr / 2);
    TrackMap m2 = analyzeTrack(dec.data(), dec.size(), sr / 2, 0.0);
    std::printf("normal grid %.2f bpm, %zu beats\n", m.bpm, m.beats.size());
    check(std::fabs(m.bpm - m2.bpm) < 1e-9, "forcedBpm 0 changes nothing", m.bpm - m2.bpm);
    TrackMap f = analyzeTrack(dec.data(), dec.size(), sr / 2, 90.0);
    std::printf("forced 90: %.2f bpm, %zu beats\n", f.bpm, f.beats.size());
    check(f.bpm > 0.0 && std::fabs(f.bpm / 90.0 - 1.0) < 0.08, "a grid at 90 bpm is built when asked for", f.bpm);
    check(f.beats.size() < m.beats.size(), "and it has fewer beats than the grid at 120", (double)f.beats.size());
    std::printf(failures ? "%d FAILED\n" : "all ok\n", failures);
    return failures ? 1 : 0;
}
