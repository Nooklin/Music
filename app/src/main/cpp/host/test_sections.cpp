// Host test of the section analysis: a song with known structure (intro, build, drop, breakdown, drop, outro).
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
static TrackMap analyze(const Synth& s, int sr) {
    std::vector<float> dec(s.pcm.size() / 4);
    for (size_t i = 0; i < dec.size(); i++) dec[i] = 0.25f * (s.pcm[4 * i] + s.pcm[4 * i + 1] + s.pcm[4 * i + 2] + s.pcm[4 * i + 3]);
    return analyzeTrack(dec.data(), dec.size(), sr / 2);
}

int main() {
    const int sr = 44100;
    // blocks of 16 beats: intro 2, build 2, drop 4, breakdown 2, drop 4, outro 2 (= 16 blocks, 256 beats)
    std::vector<int> lv = {0, 0, 1, 1, 2, 2, 2, 2, -1, -1, 2, 2, 2, 2, 0, 0};
    Synth s = makeStructured(sr, 128.0, 0.35, lv);
    TrackMap m = analyze(s, sr);
    check(m.valid(), "grid found", m.bpm);
    std::printf("  sections:");
    for (auto& sec : m.sections) std::printf(" [%ld %s %.2f]", sec.beat, sec.high ? "H" : "L", sec.ratio);
    std::printf("\n");
    check(!m.sections.empty(), "sections found", (double)m.sections.size());
    // True high stretches: beats 64..128 and 160..224 (blocks 4..7 and 10..13), in 128 bpm
    auto typeAt = [&](double b) { int i = m.sectionAt(b); return i < 0 ? -1 : (m.sections[(size_t)i].high ? 1 : 0); };
    check(typeAt(8) == 0, "the intro is low", typeAt(8));
    check(typeAt(100) == 1, "the first drop is high", typeAt(100));
    check(typeAt(145) == 0, "the breakdown is low", typeAt(145));
    check(typeAt(190) == 1, "the second drop is high", typeAt(190));
    check(typeAt(240) == 0, "the outro is low", typeAt(240));
    // The boundary of a drop lies within 4 beats of the true one (beat 64 and beat 160)
    double d1 = 1e9, d2 = 1e9;
    for (auto& sec : m.sections) {
        if (sec.high) {
            d1 = std::min(d1, std::fabs((double)sec.beat - 64.0));
            d2 = std::min(d2, std::fabs((double)sec.beat - 160.0));
        }
    }
    check(d1 <= 4.0, "first drop boundary within 4 beats", d1);
    check(d2 <= 4.0, "second drop boundary within 4 beats", d2);
    // A song without structure gives no wrong certainty: constant level
    std::vector<int> flat(16, 2);
    Synth f = makeStructured(sr, 128.0, 0.35, flat, 130.81, 4);
    TrackMap mf = analyze(f, sr);
    std::printf("  flat song sections: %zu\n", mf.sections.size());
    check(mf.sections.size() <= 2, "a flat song has at most two sections", (double)mf.sections.size());
    std::printf(failures ? "%d FAILED\n" : "all ok\n", failures);
    return failures ? 1 : 0;
}
