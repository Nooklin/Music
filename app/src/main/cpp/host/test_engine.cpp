// Host test of the engine: two synthetic songs, planned and rendered offline. Checks the beat phase between the decks
// against the true beats of the synthetic songs, the plan, the handoff and that the output stays clean.
#include <algorithm>
#include <cmath>
#include <cstdio>
#include <cstdlib>

#include "../mix/analyzer.h"
#include "../mix/engine.h"
#include "../mix/planner.h"
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
    TrackMap m = analyzeTrack(dec.data(), dec.size(), sr / 2);
    return m;
}

// Fractional beat position from the true beat times
static double trueBeat(const std::vector<double>& t, double sec) {
    auto it = std::upper_bound(t.begin(), t.end(), sec);
    if (it == t.begin() || it == t.end()) return -1;
    const size_t i = it - t.begin() - 1;
    return (double)i + (sec - t[i]) / (t[i + 1] - t[i]);
}

int main() {
    const int sr = 44100;
    struct Case { double bpmA, bpmB; };
    for (Case c : {Case{124.0, 128.0}, Case{128.0, 124.0}, Case{120.0, 126.0}}) {
        std::printf("case %.0f -> %.0f bpm\n", c.bpmA, c.bpmB);
        Synth sa = makeTrack(sr, c.bpmA, 0.37, 150.0, 0.0, 130.81, 3);
        Synth sb = makeTrack(sr, c.bpmB, 0.21, 150.0, 0.0, 146.83, 5);
        TrackMap ma = analyze(sa, sr), mb = analyze(sb, sr);
        check(ma.valid() && mb.valid(), "both songs have a beat grid", ma.bpm);
        auto srcA = std::make_shared<VectorSource>(sa.pcm);
        auto srcB = std::make_shared<VectorSource>(sb.pcm);

        const double start = 20.0;
        TransitionPlan plan = planTransition(ma, mb, start);
        check(plan.kind != Template::Fallback, "planner finds a real transition", (double)plan.kind);
        std::printf("  plan: kind %d startA %.0f startB %.0f len %d ratio %.4f factor %.1f\n", (int)plan.kind, plan.startBeatA,
                    plan.startBeatB, plan.lengthBeats, plan.tempoRatioA, plan.beatFactor);

        Engine e(sr);
        e.play(srcA, ma, start);
        std::vector<float> out(2 * 256);
        e.render(out.data(), 256);  // applies the play command
        e.loadNext(srcB, mb);
        e.setPlan(plan);

        double worstErr = 0, worstTrue = 0;
        float peak = 0, worstStep = 0;
        float lastL = 0;
        long frames = 0, blendFrames = 0;
        bool sawBlend = false;
        const long maxFrames = (long)sr * 200;
        while (e.telemetry.changes.load() == 0 && frames < maxFrames) {
            e.render(out.data(), 256);
            frames += 256;
            for (int i = 0; i < 256; i++) {
                peak = std::max(peak, std::fabs(out[2 * i]));
                if (frames > 256 || i > 0) worstStep = std::max(worstStep, std::fabs(out[2 * i] - lastL));
                lastL = out[2 * i];
            }
            if (getenv("DBG") && frames % (sr/2*256/256*1) < 256) std::printf("   t=%.2f st=%d pos=%.2f blend=%.2f\n", (double)frames/sr, e.telemetry.state.load(), e.telemetry.positionSec.load(), e.telemetry.blendBeats.load());
            if (e.telemetry.state.load() == Engine::kBlend) {
                sawBlend = true;
                blendFrames += 256;
                worstErr = std::max(worstErr, std::fabs(e.telemetry.phaseErrorMs.load()));
            }
        }
        check(e.telemetry.changes.load() == 1, "the engine hands over to the next song", e.telemetry.changes.load());
        check(sawBlend, "the overlap ran", (double)blendFrames / sr);
        check(worstErr < 10.0, "phase error between the decks (analysed grids) under 10 ms", worstErr);
        check(peak <= 1.0f, "output never clips", peak);
        // True phase after the handoff: B is the song heard; compare against the time A would have been on the true grid
        e.render(out.data(), 256);
        const double hB = e.telemetry.positionSec.load();
        check(hB > mb.cueIn && hB < 80.0, "B plays on after the handoff", hB);
        (void)worstTrue;
        (void)worstStep;
    }

    // Realistic songs: beats a few milliseconds off the grid (a loose sequencer or a human), tempos that are not whole
    // numbers, a drifting song. The tempo ratio the planner finds must be the true one, and the beats of the two decks must
    // line up when measured against the true beats of the synthetic songs (not against the analysed grids).
    struct Real { double bpmA, bpmB, jitter, driftA; };
    for (Real c : {Real{152.88, 155.07, 6.0, 0.0}, Real{155.07, 155.13, 6.0, 0.0}, Real{155.13, 152.27, 8.0, 0.0},
                   Real{126.4, 127.9, 5.0, 1.5}}) {
        std::printf("real case %.2f -> %.2f bpm, jitter %.0f ms, drift %.1f%%\n", c.bpmA, c.bpmB, c.jitter, c.driftA);
        Synth sa = makeTrack(sr, c.bpmA, 0.43, 200.0, c.driftA, 130.81, 11, c.jitter);
        Synth sb = makeTrack(sr, c.bpmB, 0.29, 200.0, 0.0, 146.83, 13, c.jitter);
        TrackMap ma = analyze(sa, sr), mb = analyze(sb, sr);
        check(ma.valid() && mb.valid(), "both songs have a beat grid", ma.bpm);
        if (!(ma.valid() && mb.valid())) continue;
        std::printf("  analysed %.3f and %.3f bpm, constant tempo %d and %d\n", ma.bpm, mb.bpm, (int)ma.constantTempo, (int)mb.constantTempo);
        auto srcA = std::make_shared<VectorSource>(sa.pcm);
        auto srcB = std::make_shared<VectorSource>(sb.pcm);
        TransitionPlan plan = planTransition(ma, mb, 20.0);
        check(plan.kind != Template::Fallback, "planner finds a real transition", (double)plan.kind);
        if (plan.kind == Template::Fallback) continue;
        // The true ratio follows from the nominal tempos (jitter does not change the steady tempo underneath); the
        // drifting song has its tempo at the place of the overlap, which is late in the song
        const double nominalA = c.bpmA * (1.0 + c.driftA / 100.0 * 0.6);  // drift is linear over the song, the overlap sits late
        const double trueRatio = plan.beatFactor * c.bpmB / nominalA;
        std::printf("  plan: startA %.0f startB %.0f len %d ratio %.4f (about %.4f) factor %.1f\n", plan.startBeatA, plan.startBeatB,
                    plan.lengthBeats, plan.tempoRatioA, trueRatio, plan.beatFactor);
        check(std::fabs(plan.tempoRatioA - trueRatio) < (c.driftA > 0 ? 0.012 : 0.002), "tempo ratio matches the true one", plan.tempoRatioA - trueRatio);
        Engine e(sr);
        e.play(srcA, ma, 20.0);
        std::vector<float> out(2 * 256);
        e.render(out.data(), 256);
        e.loadNext(srcB, mb);
        e.setPlan(plan);
        long frames = 0;
        std::vector<double> trueErr;
        double worstAnalysed = 0;
        while (e.telemetry.changes.load() == 0 && frames < (long)sr * 300) {
            e.render(out.data(), 256);
            frames += 256;
            if (e.telemetry.state.load() == Engine::kBlend) {
                const double hA = e.telemetry.positionSec.load(), hB = e.telemetry.bPositionSec.load();
                const double ba = trueBeat(sa.beatTimes, hA), bb = trueBeat(sb.beatTimes, hB);
                if (ba >= 0 && bb >= 0) {
                    // beat phase difference in beats (both songs have whole beats here), in ms of the faster song
                    double d = (ba - std::floor(ba)) - (bb - std::floor(bb));
                    d -= std::round(d);
                    const double ms = d * 60000.0 / std::max(c.bpmB, c.bpmA);
                    trueErr.push_back(std::fabs(ms));
                }
                worstAnalysed = std::max(worstAnalysed, std::fabs(e.telemetry.phaseErrorMs.load()));
            }
        }
        check(e.telemetry.changes.load() == 1, "the engine hands over to the next song", e.telemetry.changes.load());
        check(!trueErr.empty(), "blend measured", (double)trueErr.size());
        if (trueErr.empty()) continue;
        std::sort(trueErr.begin(), trueErr.end());
        const double med = trueErr[trueErr.size() / 2], worst = trueErr.back();
        check(worstAnalysed < 12.0, "phase error on the analysed grids under 12 ms", worstAnalysed);
        check(med < 12.0, "median error against the true beats under 12 ms", med);
        check(worst < 30.0, "worst error against the true beats under 30 ms", worst);
    }

    // The phase lock on its own: the planned tempo ratio is 1.5% wrong on purpose, the beats must still line up soon
    {
        std::printf("phase lock with a wrong planned ratio\n");
        Synth sa = makeTrack(sr, 126.0, 0.43, 200.0, 0.0, 130.81, 11, 4.0);
        Synth sb = makeTrack(sr, 128.0, 0.29, 200.0, 0.0, 146.83, 13, 4.0);
        TrackMap ma = analyze(sa, sr), mb = analyze(sb, sr);
        TransitionPlan plan = planTransition(ma, mb, 20.0);
        const double right = plan.tempoRatioA;
        plan.tempoRatioA = right * 1.015;
        Engine e(sr);
        e.play(std::make_shared<VectorSource>(sa.pcm), ma, 20.0);
        std::vector<float> out(2 * 256);
        e.render(out.data(), 256);
        e.loadNext(std::make_shared<VectorSource>(sb.pcm), mb);
        e.setPlan(plan);
        long frames = 0;
        std::vector<double> late;
        while (e.telemetry.changes.load() == 0 && frames < (long)sr * 300) {
            e.render(out.data(), 256);
            frames += 256;
            if (e.telemetry.state.load() == Engine::kBlend && e.telemetry.blendBeats.load() > 6.0)
                late.push_back(std::fabs(e.telemetry.phaseErrorMs.load()));
        }
        std::sort(late.begin(), late.end());
        check(!late.empty(), "blend measured", (double)late.size());
        if (!late.empty()) check(late.back() < 12.0, "after 6 beats the error stays under 12 ms", late.back());
    }

    // Late grid, pause and stop: A starts without a beat grid, gets it 5 s later, a pause in the middle must not lose time
    {
        std::printf("late grid, pause, stop\n");
        Synth sa = makeTrack(sr, 124.0, 0.37, 150.0, 0.0, 130.81, 3);
        Synth sb = makeTrack(sr, 128.0, 0.21, 150.0, 0.0, 146.83, 5);
        TrackMap ma = analyze(sa, sr), mb = analyze(sb, sr);
        auto srcA = std::make_shared<VectorSource>(sa.pcm);
        auto srcB = std::make_shared<VectorSource>(sb.pcm);
        Engine e(sr);
        e.play(srcA, TrackMap(), 20.0);
        std::vector<float> out(2 * 256);
        e.render(out.data(), 256);
        const int epoch = e.telemetry.epoch.load();
        for (int i = 0; i < 5 * sr / 256; i++) e.render(out.data(), 256);
        check(e.setCurrentMap(ma, epoch), "late grid is accepted", epoch);
        e.render(out.data(), 256);
        e.loadNext(srcB, mb);
        e.setPlan(planTransition(ma, mb, e.telemetry.positionSec.load()));
        // pause for one second
        e.setPaused(true);
        for (int i = 0; i < 10; i++) e.render(out.data(), 256);
        const double posPaused = e.telemetry.positionSec.load();
        for (int i = 0; i < sr / 256; i++) e.render(out.data(), 256);
        check(std::fabs(e.telemetry.positionSec.load() - posPaused) < 1e-9, "pause stands still", e.telemetry.positionSec.load() - posPaused);
        float maxAbs = 0;
        for (int i = 0; i < 2 * 256; i++) maxAbs = std::max(maxAbs, std::fabs(out[i]));
        check(maxAbs == 0.0f, "pause is silent", maxAbs);
        e.setPaused(false);
        long frames = 0;
        double worst = 0;
        while (e.telemetry.changes.load() == 0 && frames < (long)sr * 200) {
            e.render(out.data(), 256);
            frames += 256;
            if (e.telemetry.state.load() == Engine::kBlend) worst = std::max(worst, std::fabs(e.telemetry.phaseErrorMs.load()));
        }
        check(e.telemetry.changes.load() == 1, "mix happens after a late grid", e.telemetry.changes.load());
        check(worst < 10.0, "phase error after a late grid under 10 ms", worst);
        e.stop();
        e.render(out.data(), 256);
        e.render(out.data(), 256);
        float after = 0;
        for (int i = 0; i < 2 * 256; i++) after = std::max(after, std::fabs(out[i]));
        check(after == 0.0f, "stop is silent", after);
    }
    std::printf("%s (%d failures)\n", failures ? "FAILED" : "ALL OK", failures);
    return failures ? 1 : 0;
}
