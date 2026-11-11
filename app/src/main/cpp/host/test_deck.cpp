// Host test of the deck: cue exactness, speed, and the handover from passthrough to the stretcher.
#include <algorithm>
#include <cstdio>

#include "../mix/analyzer.h"
#include "../mix/deck.h"
#include "synth.h"

using namespace nooklin::mix;
using namespace nooklin::mix::host;

static int failures = 0;
static void check(bool ok, const char* what, double v) {
    std::printf("  %-60s %s (%.3f)\n", what, ok ? "ok  " : "FAIL", v);
    if (!ok) failures++;
}

// Renders [seconds] of output from a deck cued at [cueSec] at [rate]
static std::vector<float> run(Deck& d, double cueSec, double rate, double seconds, int sr) {
    d.cue(cueSec, rate);
    const long total = (long)(seconds * sr);
    std::vector<float> out((size_t)total * 2);
    for (long i = 0; i < total; i += 100) {
        const int n = (int)std::min<long>(100, total - i);
        d.render(out.data() + 2 * i, n, rate);
    }
    return out;
}

int main() {
    const int sr = 44100;
    Synth s = makeTrack(sr, 124.0, 0.37, 100.0, 0.0, 130.81, 3);
    auto src = std::make_shared<VectorSource>(s.pcm);
    Deck d;
    d.init(sr);
    d.setSource(src.get());

    // 1. Passthrough is bit exact and the position is exact
    {
        auto out = run(d, 20.0, 1.0, 5.0, sr);
        const long at = 20 * sr;
        bool same = true;
        for (long i = 0; i < 5 * sr && same; i++)
            same = out[2 * i] == s.pcm[2 * (at + i)] && out[2 * i + 1] == s.pcm[2 * (at + i) + 1];
        check(same, "speed 1: output equals the source bit for bit", same ? 1 : 0);
        check(d.heardFrames() == at + 5 * sr, "speed 1: position is exact", (double)(d.heardFrames() - at - 5 * sr));
    }
    // 2. A stretched deck starts at the cue point and runs at the wanted speed: analyse its output and compare the beats
    //    with the true beats (true beat j is heard at (t_j - cue) / rate)
    for (double rate : {0.94, 1.06, 1.12}) {
        const double cue = 30.0;
        auto out = run(d, cue, rate, 40.0, sr);
        std::vector<float> mono(out.size() / 2), dec(out.size() / 4);
        for (size_t i = 0; i < mono.size(); i++) mono[i] = 0.5f * (out[2 * i] + out[2 * i + 1]);
        for (size_t i = 0; i < dec.size(); i++) dec[i] = 0.5f * (mono[2 * i] + mono[2 * i + 1]);
        TrackMap m = analyzeTrack(dec.data(), dec.size(), sr / 2);
        std::printf("rate %.2f: found %.3f bpm (expected %.3f)\n", rate, m.bpm, 124.0 * rate);
        check(m.valid() && std::fabs(m.bpm - 124.0 * rate) / (124.0 * rate) < 0.003, "tempo of the output is right", m.bpm);
        if (!m.valid()) continue;
        std::vector<double> err;
        for (double b : m.beats) {
            double best = 1e9;
            for (double tb : s.beatTimes) {
                const double heardAt = (tb - cue) / rate;
                best = std::min(best, std::fabs(heardAt - b));
            }
            if (b > 3.0 && b < 36.0) err.push_back(best);
        }
        std::sort(err.begin(), err.end());
        const double med = err[err.size() / 2] * 1000.0;
        check(med < 12.0, "start and speed: median beat position error under 12 ms", med);
        check(std::fabs((double)d.heardFrames() / sr - (cue + 40.0 * rate)) < 0.002, "position after 40 s is exact", d.heardSec() - (cue + 40.0 * rate));
    }
    // 3. Switching from passthrough to the stretcher in the middle of playback: no click. A pure sine has a known largest
    //    step between samples, so anything above it is a discontinuity.
    {
        std::vector<float> sine(2 * sr * 10);
        for (long i = 0; i < sr * 10; i++) sine[2 * i] = sine[2 * i + 1] = (float)(0.5 * std::sin(2.0 * 3.14159265358979 * 440.0 * i / sr));
        auto ss = std::make_shared<VectorSource>(sine);
        Deck e;
        e.init(sr);
        e.setSource(ss.get());
        e.cue(2.0, 1.0);
        const long total = sr * 2;
        std::vector<float> out(2 * total);
        float rate = 1.0f;
        for (long i = 0; i < total; i += 100) {
            if (i == 44100) rate = 1.05f;
            e.render(out.data() + 2 * i, 100, rate);
        }
        const double limit = 0.5 * 2.0 * 3.14159265358979 * 440.0 / sr;
        float worst = 0;
        long at = 0;
        for (long i = 1; i < total; i++) {
            const float d = std::fabs(out[2 * i] - out[2 * (i - 1)]);
            if (i > 44100 + 3000 || i < 44100 + 200) {
                // outside the pre-roll settle zone only counts the switch itself
            }
            if (d > worst) {
                worst = d;
                at = i;
            }
        }
        std::printf("  largest step %.4f (sine limit %.4f) at sample %ld (switch at 44100)\n", worst, limit, at);
        check(worst < limit * 1.3, "switch to the stretcher does not click", worst / limit);
    }
    std::printf("%s (%d failures)\n", failures ? "FAILED" : "ALL OK", failures);
    return failures ? 1 : 0;
}
