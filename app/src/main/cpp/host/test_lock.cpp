#include <algorithm>
#include <cmath>
#include <cstdio>
#include "../mix/analyzer.h"
#include "../mix/engine.h"
#include "../mix/planner.h"
#include "synth.h"
using namespace nooklin::mix;
using namespace nooklin::mix::host;
static TrackMap analyze(const Synth& s, int sr) {
    std::vector<float> dec(s.pcm.size() / 4);
    for (size_t i = 0; i < dec.size(); i++) dec[i] = 0.25f * (s.pcm[4*i] + s.pcm[4*i+1] + s.pcm[4*i+2] + s.pcm[4*i+3]);
    return analyzeTrack(dec.data(), dec.size(), sr / 2);
}
// 0.0.142: the phase lock closes a wrong tempo ratio of A. Before: 0.75% gave 5 ms, 1.5% gave 10 ms, 3% gave 20 ms.
int main() {
    int failures = 0;
    const int sr = 44100;
    for (double mm : {0.0, 0.005, 0.0075, 0.015, 0.03}) for (double bpmB : {128.0, 132.0}) {
        Synth sa = makeTrack(sr, 126.0, 0.43, 200.0, 0.0, 130.81, 11, 0.0);
        Synth sb = makeTrack(sr, bpmB, 0.29, 200.0, 0.0, 146.83, 13, 0.0);
        TrackMap ma = analyze(sa, sr), mb = analyze(sb, sr);
        if (!ma.valid() || !mb.valid()) continue;
        TransitionPlan plan = planTransition(ma, mb, 20.0);
        if (plan.kind == Template::Fallback) continue;
        plan.tempoRatioA *= 1.0 + mm;   // a wrong tempo ratio: the lock has to correct it
        Engine e(sr);
        e.play(std::make_shared<VectorSource>(sa.pcm), ma, 20.0);
        std::vector<float> out(512);
        e.render(out.data(), 256);
        e.loadNext(std::make_shared<VectorSource>(sb.pcm), mb);
        e.setPlan(plan);
        long frames = 0; double worst = 0, last = 0;
        while (e.telemetry.changes.load() == 0 && frames < (long)sr * 300) {
            e.render(out.data(), 256); frames += 256;
            int st = e.telemetry.state.load();
            if (st == Engine::kBlend) { double v = e.telemetry.phaseErrorMs.load(); worst = std::max(worst, std::fabs(v)); last = v; }
        }
        const double limit = mm <= 0.0151 ? 1.5 : 5.0;
        const bool ok = worst < limit && (mm > 0.0151 || std::fabs(last) < 0.5);
        if (!ok) failures++;
        std::printf("  mismatch %.2f%% bpmB %.0f: worst %.2f ms (under %.1f), end %.2f ms   %s\n", mm*100, bpmB, worst, limit, last, ok ? "ok" : "FAIL");
    }
    std::printf(failures ? "%d FAILURES\n" : "ALL OK (0 failures)\n", failures);
    return failures ? 1 : 0;
}
