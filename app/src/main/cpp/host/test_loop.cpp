// Host test of 0.0.93: beat loop. An A that ends loud (no outro) used to get an exit; with loops allowed it gets a long blend that repeats its last bars.
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <string>

#include "../mix/analyzer.h"
#include "../mix/planner.h"
#include "synth.h"

using namespace nooklin::mix;
using namespace nooklin::mix::host;

static int failures = 0;
static void check(bool ok, const char* what, double v) {
    std::printf("  %-70s %s (%.3f)\n", what, ok ? "ok  " : "FAIL", v);
    if (!ok) failures++;
}
static TrackMap analyze(const Synth& s, int sr) {
    std::vector<float> dec(s.pcm.size() / 4);
    for (size_t i = 0; i < dec.size(); i++) dec[i] = 0.25f * (s.pcm[4 * i] + s.pcm[4 * i + 1] + s.pcm[4 * i + 2] + s.pcm[4 * i + 3]);
    return analyzeTrack(dec.data(), dec.size(), sr / 2);
}

int main() {
    const int sr = 44100;
    std::vector<int> loudEnd = {0, 0, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2};
    std::vector<int> lvB = {0, 0, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 1, 1, 0, 0};
    TrackMap a = analyze(makeStructured(sr, 128.0, 0.35, loudEnd, 130.81, 3), sr);
    TrackMap b = analyze(makeStructured(sr, 126.0, 0.25, lvB, 130.81, 5), sr);
    std::printf("A: beats %zu confidence %.2f end %.1f s, B confidence %.2f\n", a.beats.size(), a.confidence, a.musicEnd(), b.confidence);

    PlannerSettings off;
    TransitionPlan p0 = planTransition(a, b, 10.0, off);
    check(!(p0.flags & kFlagLoop) && p0.loopBeats == 0, "loops off: never a loop", p0.flags);

    PlannerSettings on;
    on.loops = true;
    TransitionPlan p1 = planTransition(a, b, 10.0, on);
    std::printf("  without loops kind %d style %d; with loops kind %d style %d len %d flags %d loopBeat %.0f loopBeats %d\n", (int)p0.kind, p0.style,
                (int)p1.kind, p1.style, p1.lengthBeats, p1.flags, p1.loopBeat, p1.loopBeats);
    if (p0.kind != Template::LongBlend && p0.kind != Template::BassSwapShort) {
        // Only when an exit was chosen can a loop take its place
        if (p1.flags & kFlagLoop) {
            check(p1.kind == Template::LongBlend && p1.lengthBeats == 16, "a loop plan is a long blend of 16 beats", p1.lengthBeats);
            check(p1.loopBeats == 8 && std::fabs(p1.startBeatA + 8.0 - p1.loopBeat) < 1e-9, "the loop point is 8 beats after the start", p1.loopBeat - p1.startBeatA);
            check(a.isDownbeat((long)p1.loopBeat) && a.isDownbeat((long)p1.startBeatA), "loop point and start lie on bar lines", p1.loopBeat);
            check(a.timeAtBeat(p1.startBeatA + 16.0) > a.musicEnd() - 0.01, "the overlap runs to or past the end of A", a.timeAtBeat(p1.startBeatA + 16.0) - a.musicEnd());
        } else {
            std::printf("  (no loop chosen for this pair)\n");
        }
    }
    TransitionPlan p2 = planTransition(a, b, 10.0, on);
    TrackMap shaky = a;
    shaky.confidence = 0.2;
    TransitionPlan p3 = planTransition(shaky, b, 10.0, on);
    check(!(p3.flags & kFlagLoop), "a grid that is not trusted gets no loop", p3.flags);
    PlannerSettings noTempo = on;
    noTempo.maxTempoChange = 0.0;
    TransitionPlan p4 = planTransition(a, b, 10.0, noTempo);
    check(!(p4.flags & kFlagLoop), "a tempo in doubt gets no loop", p4.flags);
    // 0.0.95: the repeated bars must be steady; an A that fades out at its end gets no loop
    std::vector<int> fading = {0, 0, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 1, 0, 0};
    TrackMap af = analyze(makeStructured(sr, 128.0, 0.35, fading, 130.81, 3), sr);
    TransitionPlan p5 = planTransition(af, b, 10.0, on);
    std::printf("  fading A: kind %d flags %d\n", (int)p5.kind, p5.flags);
    check(!(p5.flags & kFlagLoop), "an A that fades out at its end gets no loop", p5.flags);
    // 0.0.112: a loop point inside A (a phrase start in the last part): A fades out, so its last bars are no loop, but an earlier phrase is
    {
        std::vector<int> lvA = {0, 0, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 1, 0};
        TrackMap am = analyze(makeStructured(sr, 128.0, 0.35, lvA, 130.81, 3), sr);
        TrackMap bv = b;
        bv.vocal.assign(bv.beats.size(), 0.0f);
        for (size_t i = 0; i < 40 && i < bv.vocal.size(); i++) bv.vocal[i] = 0.6f;
        PlannerSettings st = on;
        st.stems = false;
        TransitionPlan pm = planTransition(am, bv, 10.0, st);
        const long lastDown = [&] { long j = (long)am.beats.size() - 1; while (j > 0 && (!am.isDownbeat(j) || am.beats[(size_t)j] > am.musicEnd() - 0.5)) j--; return j; }();
        std::printf("  loop inside A: kind %d style %d flags %d startA %.0f loopBeat %.0f (last bar line %ld) cost %.2f\n", (int)pm.kind, pm.style, pm.flags, pm.startBeatA, pm.loopBeat, lastDown, pm.cost);
        if (pm.flags & kFlagLoop) {
            check(am.isPhraseStart((long)pm.loopBeat) || (long)pm.loopBeat == lastDown, "the loop point is a phrase start or the last bar line", pm.loopBeat);
            check(pm.loopBeats == 8 && std::fabs(pm.startBeatA + 8.0 - pm.loopBeat) < 1e-9, "loop point 8 beats after the start", pm.loopBeat - pm.startBeatA);
        } else {
            std::printf("  (no loop chosen here)\n");
        }
    }
    (void)p2;
    // 0.0.105: with stems on, a loop plan never counts on stems (a stem overlay would replace the loop): no stem flags, and little singing on top of singing
    {
        PlannerSettings st = on;
        st.stems = true;
        TransitionPlan ps = planTransition(a, b, 10.0, st);
        if (ps.flags & kFlagLoop) {
            check((ps.flags & kFlagStemMix) == 0, "a loop plan is not a stem mix", ps.flags);
            check(ps.vocalOverlap * ps.lengthBeats <= 4.0 + 1e-9, "and has at most 4 beats of two voices", ps.vocalOverlap * ps.lengthBeats);
        } else {
            std::printf("  (no loop chosen with stems on for this pair)\n");
        }
    }
    // 0.0.129: a clash of the last bars of A with B must be confirmed by the whole songs (A is in the key of B, only its last bars read as another key)
    std::printf("loop key clash against the whole songs\n");
    {
        TrackMap odd = a;
        const size_t blocks = odd.chroma.size() / 12;
        for (size_t k = blocks > 10 ? blocks - 10 : 0; k < blocks; k++)
            for (int pc = 0; pc < 12; pc++) odd.chroma[k * 12 + (size_t)pc] = (pc == 6 || pc == 9 || pc == 1) ? 1.0f : 0.0f;   // F# minor
        const std::string log = explainPlan(odd, b, 10.0, on);
        const size_t at = log.find("beat loop:");
        const size_t kc = at == std::string::npos ? at : log.find("key clash ", at);
        const int rejected = kc == std::string::npos ? -1 : std::atoi(log.c_str() + kc + 10);
        std::printf("  loop candidates turned down for a key clash: %d\n", rejected);
        check(rejected == 0, "the last bars alone do not turn a loop down when the whole songs agree", rejected);
    }
    std::printf(failures ? "%d FAILED\n" : "all ok\n", failures);
    return failures ? 1 : 0;
}
