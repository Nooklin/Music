// Host test of the exit techniques (echo, filter, cut), the shared tempo change and the glide back.
#include <algorithm>
#include <cmath>
#include <cstdio>

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
    return analyzeTrack(dec.data(), dec.size(), sr / 2);
}
static double trueBeat(const std::vector<double>& t, double sec) {
    auto it = std::upper_bound(t.begin(), t.end(), sec);
    if (it == t.begin() || it == t.end()) return -1;
    const size_t i = it - t.begin() - 1;
    return (double)i + (sec - t[i]) / (t[i + 1] - t[i]);
}
static const char* name(Template k) {
    switch (k) {
        case Template::LongBlend: return "long blend";
        case Template::BassSwapShort: return "short blend";
        case Template::EchoOut: return "echo out";
        case Template::FilterOut: return "filter out";
        case Template::Cut: return "cut";
        case Template::ReverbOut: return "reverb out";
        case Template::BrakeOut: return "brake out";
        case Template::SpinOut: return "spin out";
        case Template::RollOut: return "roll out";
        case Template::FadeOut: return "fade out";
        default: return "fallback";
    }
}

int main() {
    const int sr = 44100;
    Synth sa = makeTrack(sr, 124.0, 0.37, 150.0, 0.0, 130.81, 3);
    Synth sb = makeTrack(sr, 100.0, 0.21, 150.0, 0.0, 146.83, 5);  // 24% away: no blend possible, so an exit
    TrackMap ma = analyze(sa, sr), mb = analyze(sb, sr);
    check(ma.valid() && mb.valid(), "both songs have a grid", ma.bpm);

    TransitionPlan auto_ = planTransition(ma, mb, 20.0);
    std::printf("  planner chose: %s (cost %.2f) startA %.0f entry %.2f s flags %d\n", name(auto_.kind), auto_.cost, auto_.startBeatA,
                auto_.entrySecB, auto_.flags);
    check((int)auto_.kind >= 3,
          "tempos too far apart give a clean exit", (double)(int)auto_.kind);
    check(auto_.entrySecB >= mb.cueIn - 0.3, "B comes in at or after its start", auto_.entrySecB);

    struct Case {
        Template kind;
        int flags;
    };
    const Case cases[] = {{Template::Cut, 0},         {Template::EchoOut, 0},  {Template::FilterOut, 0}, {Template::ReverbOut, 0},
                          {Template::BrakeOut, 0},    {Template::SpinOut, 0},  {Template::RollOut, 0}, {Template::FadeOut, 0},
                          {Template::Cut, kFlagRiser | kFlagImpact}};
    for (const Case& cs : cases) {
        const Template kind = cs.kind;
        const bool tail = kind == Template::EchoOut || kind == Template::ReverbOut;
        const bool hit = (cs.flags & kFlagImpact) != 0;
        std::printf("technique: %s flags %d\n", name(kind), cs.flags);
        TransitionPlan plan = auto_;
        plan.kind = kind;
        plan.flags = cs.flags;
        // B is silent for the echo test so that only the tail is heard after the bar line
        Synth quiet = sb;
        if (tail || hit) std::fill(quiet.pcm.begin(), quiet.pcm.end(), 0.0f);
        Engine e(sr);
        e.play(std::make_shared<VectorSource>(sa.pcm), ma, 20.0);
        std::vector<float> out(2 * 256);
        e.render(out.data(), 256);
        e.loadNext(std::make_shared<VectorSource>(quiet.pcm), mb);
        e.setPlan(plan);
        long frames = 0;
        float peak = 0;
        double hfBefore = 0, hfOutro = 0, lfOutro = 0, lfBefore = 0;
        long nBefore = 0, nOutro = 0;
        bool sawOutro = false;
        double handoffAt = -1, tailFirst = 0, tailLate = 0;
        long afterFrames = 0;
        double aSoundAfter = 0;
        while (frames < (long)sr * 200 && afterFrames < (long)sr * 12) {
            const int st = e.telemetry.state.load();
            e.render(out.data(), 256);
            frames += 256;
            const bool after = e.telemetry.changes.load() > 0;
            double hf = 0, lf = 0;
            for (int i = 1; i < 256; i++) {
                const double d = out[2 * i] - out[2 * i - 2];
                hf += d * d;
                lf += out[2 * i] * out[2 * i];
                peak = std::max(peak, std::fabs(out[2 * i]));
            }
            if (!after && st == Engine::kOutro) {
                sawOutro = true;
                hfOutro = nOutro == 0 ? hf : hfOutro;  // first outro block
                hfBefore = hfBefore;
                lfOutro += lf;
                nOutro++;
            }
            if (!after && st == Engine::kPlaying && frames > sr * 20) {
                hfBefore += hf;
                lfBefore += lf;
                nBefore++;
            }
            if (after) {
                if (handoffAt < 0) handoffAt = (double)frames / sr;
                const double t = (double)afterFrames / sr;
                if (tail || hit) {
                    if (t < 1.0) tailFirst += lf;
                    if (t > 9.0) tailLate += lf;
                }
                afterFrames += 256;
            }
        }
        check(sawOutro, "the exit ran (state 6)", sawOutro);
        check(e.telemetry.changes.load() == 1, "B took over", e.telemetry.changes.load());
        check(peak <= 1.0f, "output never clips", peak);
        check(std::isfinite(peak) && std::isfinite(tailFirst), "output has no NaN", peak);
        const double posB = e.telemetry.positionSec.load();
        check(posB > auto_.entrySecB, "B plays on from its entry", posB);
        if (tail) {
            check(tailFirst > 1e-3, "the tail rings after the bar line", tailFirst);
            check(tailLate < 0.01 * tailFirst + 1e-9, "the tail has died out after 9 seconds", tailLate);
        }
        if (hit) check(tailFirst > 1e-3, "the impact is heard on the first beat of B", tailFirst);
        if (!tail && !hit && kind != Template::Cut) check(nOutro > 0 && lfOutro > 1e-3, "A is heard during the exit", lfOutro);
        if (kind == Template::FilterOut && nBefore > 0 && nOutro > 0) {
            // HF share (difference energy over level) falls while the filter closes
            const double shareBefore = hfBefore / std::max(1e-9, lfBefore);
            const double shareOutro = hfOutro / std::max(1e-9, lfOutro / nOutro);
            check(shareOutro < shareBefore, "treble share falls in the filter sweep", shareOutro / shareBefore);
        }
        (void)aSoundAfter;
    }

    // A tempo change shared by both songs, and the glide back to the own tempo
    {
        std::printf("shared tempo change\n");
        Synth a = makeTrack(sr, 120.0, 0.43, 200.0, 0.0, 130.81, 11, 3.0);
        Synth b = makeTrack(sr, 128.0, 0.29, 200.0, 0.0, 130.81, 13, 3.0);
        TrackMap m1 = analyze(a, sr), m2 = analyze(b, sr);
        TransitionPlan plan = planTransition(m1, m2, 20.0);
        std::printf("  planner chose: %s rA %.4f rB %.4f flags %d\n", name(plan.kind), plan.tempoRatioA, plan.tempoRatioB, plan.flags);
        check(plan.kind == Template::LongBlend || plan.kind == Template::BassSwapShort, "7% apart is still blended", (double)(int)plan.kind);
        check((plan.flags & kFlagSplit) != 0, "the change is shared", plan.flags);
        check(std::fabs(plan.tempoRatioA - 1.0) < 0.08 && std::fabs(plan.tempoRatioB - 1.0) < 0.08, "each song moves less than 8%", plan.tempoRatioA);
        Engine e(sr);
        e.play(std::make_shared<VectorSource>(a.pcm), m1, 20.0);
        std::vector<float> out(2 * 256);
        e.render(out.data(), 256);
        e.loadNext(std::make_shared<VectorSource>(b.pcm), m2);
        e.setPlan(plan);
        long frames = 0;
        std::vector<double> errs;
        while (e.telemetry.changes.load() == 0 && frames < (long)sr * 300) {
            e.render(out.data(), 256);
            frames += 256;
            if (e.telemetry.state.load() == Engine::kBlend) {
                const double ba = trueBeat(a.beatTimes, e.telemetry.positionSec.load()), bb = trueBeat(b.beatTimes, e.telemetry.bPositionSec.load());
                if (ba >= 0 && bb >= 0 && e.telemetry.blendBeats.load() > 4.0) {
                    double d = (ba - std::floor(ba)) - (bb - std::floor(bb));
                    d -= std::round(d);
                    errs.push_back(std::fabs(d * 60000.0 / 132.0));
                }
            }
        }
        std::sort(errs.begin(), errs.end());
        check(!errs.empty(), "blend measured", (double)errs.size());
        if (!errs.empty()) check(errs[errs.size() / 2] < 12.0, "median error against the true beats under 12 ms", errs[errs.size() / 2]);
        // After the handoff B starts at rB and glides back to 1: its source position moves 1 s per s in the end
        double p0 = 0;
        for (int i = 0; i < 40 * sr / 256; i++) e.render(out.data(), 256);  // well after the glide
        p0 = e.telemetry.positionSec.load();
        for (int i = 0; i < sr / 256; i++) e.render(out.data(), 256);
        const double rate = (e.telemetry.positionSec.load() - p0) / ((sr / 256) * 256.0 / sr);
        check(std::fabs(rate - 1.0) < 0.002, "B is back at its own tempo after the glide", rate);
    }
    std::printf(failures ? "%d FAILED\n" : "all ok\n", failures);
    return failures ? 1 : 0;
}
