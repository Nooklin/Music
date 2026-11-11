// Host test of the choice of the moment to mix: loudness of the stretches that overlap, keys that clash, and the key
// estimate itself. Synthetic songs with a known structure (a breakdown, a fade-out) and known keys.
#include <algorithm>
#include <cmath>
#include <cstdio>

#include "../mix/analyzer.h"
#include "../mix/harmony.h"
#include "../mix/planner.h"
#include "synth.h"

using namespace nooklin::mix;
using namespace nooklin::mix::host;

static int failures = 0;
static void check(bool ok, const char* what, double v) {
    std::printf("  %-66s %s (%.3f)\n", what, ok ? "ok  " : "FAIL", v);
    if (!ok) failures++;
}

static TrackMap analyze(const std::vector<float>& pcm, int sr) {
    std::vector<float> dec(pcm.size() / 4);
    for (size_t i = 0; i < dec.size(); i++) dec[i] = 0.25f * (pcm[4 * i] + pcm[4 * i + 1] + pcm[4 * i + 2] + pcm[4 * i + 3]);
    return analyzeTrack(dec.data(), dec.size(), sr / 2);
}

// Gain over time: a quiet stretch [q0, q1] seconds (breakdown) and a fade-out over the last [fade] seconds
static std::vector<float> shaped(const Synth& s, double q0, double q1, double fade) {
    std::vector<float> p = s.pcm;
    const double dur = (double)p.size() / 2.0 / s.sampleRate;
    for (size_t i = 0; i < p.size() / 2; i++) {
        const double t = (double)i / s.sampleRate;
        double g = 1.0;
        if (t >= q0 && t <= q1) g = 0.25;
        if (fade > 0.0 && t > dur - fade) g *= std::max(0.25, (dur - t) / fade);
        p[2 * i] *= (float)g;
        p[2 * i + 1] *= (float)g;
    }
    return p;
}

static Key keyOfSong(const TrackMap& m) {
    double c[12] = {};
    m.chromaSum(0, (double)m.beats.size(), c);
    return estimateKey(c);
}

int main() {
    const int sr = 44100;
    const char* names[] = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};

    std::printf("key estimate\n");
    struct K { double hz; int root; };
    for (K k : {K{130.81, 0}, K{146.83, 2}, K{185.00, 6}, K{174.61, 5}}) {
        Synth s = makeTrack(sr, 126.0, 0.4, 150.0, 0.0, k.hz, 21);
        TrackMap m = analyze(s.pcm, sr);
        Key key = keyOfSong(m);
        std::printf("  expected %s minor, found %s %s, fit %.2f margin %.2f confidence %.2f\n", names[k.root],
                    key.known() ? names[key.root] : "?", key.minor ? "minor" : "major", key.fit, key.margin, key.confidence());
        check(key.known() && key.root == k.root && key.minor, "key found", key.root);
    }

    std::printf("loud into calm (0.0.74)\n");
    {
        // A is loud to the end, B starts calm for 40 s: no blend of the loud end into the calm start
        Synth sa = makeTrack(sr, 126.0, 0.4, 200.0, 0.0, 130.81, 3);
        Synth sb = makeTrack(sr, 126.0, 0.3, 200.0, 0.0, 130.81, 5);
        TrackMap ma = analyze(sa.pcm, sr), mb = analyze(shaped(sb, 0.0, 40.0, 0.0), sr);
        TransitionPlan p = planTransition(ma, mb, 20.0);
        const bool blend = p.kind == Template::LongBlend || p.kind == Template::BassSwapShort;
        std::printf("  kind %d, energy A %.2f B %.2f\n", (int)p.kind, p.energyA, p.energyB);
        check(!(blend && p.energyA >= 0.8 && p.energyB <= 0.6 * p.energyA), "no loud into calm blend", p.energyB);
    }
    std::printf("loudness of the overlap\n");
    {
        // A ends loud, with a breakdown from 25 to 45 s before the end: the mix goes out in the breakdown
        Synth sa = makeTrack(sr, 126.0, 0.4, 200.0, 0.0, 130.81, 3);
        Synth sb = makeTrack(sr, 126.0, 0.3, 200.0, 0.0, 130.81, 5);
        TrackMap ma = analyze(shaped(sa, 200 - 0.6 - 45, 200 - 0.6 - 25, 0.0), sr), mb = analyze(sb.pcm, sr);
        TransitionPlan p = planTransition(ma, mb, 20.0);
        const double end = ma.musicEnd();
        const double t0 = ma.timeAtBeat(p.startBeatA), t1 = ma.timeAtBeat(p.startBeatA + p.lengthBeats);
        std::printf("  overlap in A from %.1f to %.1f s, music ends at %.1f s, energy A %.2f B %.2f\n", t0, t1, end, p.energyA, p.energyB);
        check(p.kind != Template::Fallback, "a real transition", (double)p.kind);
        // 0.0.118: an exit in the breakdown now pays for the final drop of A it throws away (unplayedCost), so a blend that ends 16 s before
        // the end can win instead. Either the mix goes out in the breakdown over a quiet stretch (as before), or at most 20 s of A are left unplayed.
        const bool inBreakdown = t0 >= end - 50.0 && t1 <= end - 18.0 && p.energyA < 0.6;
        const bool little = end - t1 <= 20.0;
        check(inBreakdown || little, "the mix goes out in the breakdown, or A is played to within 20 s of its end", t0);
    }
    {
        // A fades out at the end: the mix goes out there
        Synth sa = makeTrack(sr, 126.0, 0.4, 200.0, 0.0, 130.81, 3);
        Synth sb = makeTrack(sr, 126.0, 0.3, 200.0, 0.0, 130.81, 5);
        TrackMap ma = analyze(shaped(sa, -1, -1, 30.0), sr), mb = analyze(sb.pcm, sr);
        TransitionPlan p = planTransition(ma, mb, 20.0);
        const double end = ma.musicEnd();
        const double t1 = ma.timeAtBeat(p.startBeatA + p.lengthBeats);
        std::printf("  overlap in A ends at %.1f s, music ends at %.1f s, energy A %.2f\n", t1, end, p.energyA);
        check(p.kind != Template::Fallback && t1 > end - 25.0, "with a fade-out the overlap is in the outro", t1);
    }

    std::printf("keys that clash\n");
    {
        Synth sa = makeTrack(sr, 126.0, 0.4, 200.0, 0.0, 130.81, 3);       // C minor
        Synth sf = makeTrack(sr, 126.0, 0.3, 200.0, 0.0, 174.61, 5, 0.0, 0);       // F minor: a neighbour, mixes well; both with the harmony from the first beat
        Synth sx = makeTrack(sr, 126.0, 0.3, 200.0, 0.0, 185.00, 5, 0.0, 0);       // F# minor: far away
        TrackMap ma = analyze(sa.pcm, sr), mf = analyze(sf.pcm, sr), mx = analyze(sx.pcm, sr);
        TransitionPlan pf = planTransition(ma, mf, 20.0), px = planTransition(ma, mx, 20.0);
        std::printf("  F minor: length %d, clash %.2f (keys %d and %d); F# minor: length %d, clash %.2f (keys %d and %d)\n",
                    pf.lengthBeats, pf.keyClash, pf.keyA, pf.keyB, px.lengthBeats, px.keyClash, px.keyA, px.keyB);
        check(pf.keyClash < 0.2, "a neighbouring key does not clash", pf.keyClash);
        const bool exitKind = (int)px.kind >= 3;
        check(exitKind, "a far key gives a clean exit instead of a blend", (double)(int)px.kind);
        check(pf.kind == Template::LongBlend || pf.kind == Template::BassSwapShort, "a neighbouring key is blended", (double)(int)pf.kind);
    }

    std::printf("rejection log (0.0.129)\n");
    {
        // A far key gives an exit. If a blend was priced and lost, the log must name the rule that refused it; if none was priced there is nothing to name
        Synth sa = makeTrack(sr, 126.0, 0.4, 200.0, 0.0, 130.81, 3);       // C minor
        Synth sx = makeTrack(sr, 126.0, 0.3, 200.0, 0.0, 185.00, 5, 0.0, 0);       // F# minor: far away
        TrackMap ma = analyze(sa.pcm, sr), mx = analyze(sx.pcm, sr);
        const std::string log = explainPlan(ma, mx, 20.0);
        const bool priced = log.find("cheapest priced") != std::string::npos;
        const bool named = log.find("turned down by ") != std::string::npos;
        std::printf("  priced blend in the log: %s; refusal named: %s\n", priced ? "yes" : "no", named ? "yes" : "no");
        if (priced) check(named, "a priced blend that lost to an exit is logged with the rule that refused it", 0.0);
        else std::printf("  (no blend was priced for this pair; nothing to name)\n");
    }

    std::printf("%s (%d failures)\n", failures ? "FAILED" : "ALL OK", failures);
    return failures ? 1 : 0;
}
