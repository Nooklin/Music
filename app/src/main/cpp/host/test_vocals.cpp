// Host test of the rules added in 0.0.62: singing on both sides, genre classes, loudness and the fade-out.
#include <cmath>
#include <cstdio>
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
static void sing(TrackMap& m, long from, long to, float v) {
    if (m.vocal.empty()) m.vocal.assign(m.beats.size(), 0.0f);
    for (long i = std::max(0L, from); i < std::min((long)m.vocal.size(), to); i++) m.vocal[(size_t)i] = v;
}

int main() {
    const int sr = 44100;
    std::vector<int> lvA = {0, 0, 1, 1, 2, 2, 2, 2, -1, -1, 2, 2, 2, 2, 0, 0};
    std::vector<int> lvB = {0, 0, 1, 1, 2, 2, 2, 2, -1, -1, 2, 2, 2, 2, 0, 0};
    Synth sa = makeStructured(sr, 128.0, 0.35, lvA, 130.81, 3);
    Synth sb = makeStructured(sr, 126.0, 0.25, lvB, 130.81, 5);
    TrackMap a = analyze(sa, sr), b = analyze(sb, sr);
    std::printf("loudness and sections\n");
    check(a.loudness != 0.0 && b.loudness != 0.0, "loudness is measured", a.loudness);
    std::vector<int> quiet(16, 0), loud(16, 2);
    TrackMap mq = analyze(makeStructured(sr, 128.0, 0.35, quiet, 130.81, 2), sr), ml = analyze(makeStructured(sr, 128.0, 0.35, loud, 130.81, 2), sr);
    check(ml.loudness > mq.loudness + 3.0, "a loud song measures louder than a quiet one", ml.loudness - mq.loudness);

    // A fade out: the level steps down over the last four blocks
    std::vector<int> fade = {0, 0, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 1, 1, 0, 0};
    TrackMap mf = analyze(makeStructured(sr, 128.0, 0.35, fade, 130.81, 4), sr);
    std::printf("  fade song sections:");
    for (auto& sec : mf.sections) std::printf(" [%ld %s %.2f]", sec.beat, sec.high ? "H" : "L", sec.ratio);
    std::printf("\n");
    check(!mf.sections.empty() && !mf.sections.back().high, "the song ends in a low section", (double)mf.sections.size());

    std::printf("singing\n");
    const long endA = (long)a.beats.size();
    // Both songs sing all through: any overlap has singing on both sides
    TrackMap a1 = a, b1 = b;
    sing(a1, 0, endA, 0.5f);
    sing(b1, 0, (long)b.beats.size(), 0.5f);
    TransitionPlan p1 = planTransition(a1, b1, 10.0);
    std::printf("  both sing: kind %d style %d len %d vocalA %.2f vocalB %.2f flags %d\n", (int)p1.kind, p1.style, p1.lengthBeats, p1.vocalA, p1.vocalB, p1.flags);
    const bool blend1 = p1.style >= 1 && p1.style <= 3;
    // 0.0.77: a blend with two voices on top of each other costs 4 per share of the overlap, so a clean exit wins; if a blend is
    // still chosen it cuts A's mids
    check(p1.style == 4 || (blend1 && (p1.flags & kFlagVocalCut)), "with singing everywhere an exit, or a blend that cuts A's mids", p1.flags);
    // A sings except in its breakdown: the planner picks the quiet window of A
    TrackMap a3 = a;
    sing(a3, 0, (long)a3.beats.size(), 0.5f);
    sing(a3, 128, 160, 0.0f);
    TrackMap b3 = b;
    sing(b3, 0, (long)b3.beats.size(), 0.5f);
    sing(b3, 0, 40, 0.0f);
    TransitionPlan p3 = planTransition(a3, b3, 10.0);
    std::printf("  A quiet only in its breakdown, B quiet in its intro: kind %d style %d startA %.0f len %d vocalA %.2f vocalB %.2f\n", (int)p3.kind, p3.style, p3.startBeatA, p3.lengthBeats, p3.vocalA, p3.vocalB);
    check(p3.vocalA <= 0.01 || p3.vocalB <= 0.01 || p3.style == 4, "the overlap lies where at most one song sings", std::min(p3.vocalA, p3.vocalB));
    // Control: B sings only after the overlap, A has stopped
    TrackMap a2 = a, b2 = b;
    sing(a2, 0, 100, 0.5f);
    sing(b2, 120, 200, 0.5f);
    TransitionPlan p2 = planTransition(a2, b2, 10.0);
    std::printf("  no singing in the overlap: kind %d style %d len %d vocalA %.2f vocalB %.2f flags %d\n", (int)p2.kind, p2.style, p2.lengthBeats, p2.vocalA, p2.vocalB, p2.flags);
    check(!(p2.flags & kFlagVocalCut), "no singing on both sides gives no mid cut", p2.flags);
    // The cost of overlapping singing makes the planner prefer a window without it
    check(p2.cost <= p1.cost + 1e-9 || !blend1, "singing on top of singing costs more", p1.cost - p2.cost);

    std::printf("stem mix\n");
    PlannerSettings withStems;
    withStems.stems = true;
    TransitionPlan ps = planTransition(a2, b2, 10.0, withStems);
    const bool isLong = ps.kind == Template::LongBlend && ps.lengthBeats >= 16;
    std::printf("  stems on: kind %d len %d flags %d\n", (int)ps.kind, ps.lengthBeats, ps.flags);
    check(!isLong || (ps.flags & kFlagStemMix), "a long blend with stems on is a stem mix", ps.flags);
    check(!(ps.flags & kFlagStemMix) || isLong, "only a long blend is a stem mix", ps.flags);
    check(!(p2.flags & kFlagStemMix) && !(p1.flags & kFlagStemMix), "no stem mix when stems are off", p2.flags);
    // 0.0.99: an overlap that lies less than the lead ahead gets no stems (they would come too late)
    PlannerSettings tooLate = withStems;
    tooLate.stemLeadSeconds = 1e6;
    TransitionPlan pl = planTransition(a2, b2, 10.0, tooLate);
    std::printf("  stems on, no time to split: kind %d len %d flags %d\n", (int)pl.kind, pl.lengthBeats, pl.flags);
    check(!(pl.flags & kFlagStemMix), "no stem mix when the overlap is too close", pl.flags);
    // 0.0.129: the plan that is planned again without stems is named in the log, with the headroom it lacked
    check(ps.flags & kFlagStemMix, "the fixture gives a stem mix when there is time (needed for the next check)", ps.flags);
    const std::string lateLog = explainPlan(a2, b2, 10.0, tooLate);
    check(lateLog.find("planned again without stems") != std::string::npos, "the log names the plan without stems", 0.0);
    std::printf("genre\n");
    // A tempo gap above 10% leaves only exits; for house a cut is not chosen
    Synth sfar = makeStructured(sr, 100.0, 0.25, lvB, 130.81, 6);
    TrackMap far = analyze(sfar, sr);
    PlannerSettings house;
    house.genre = 2;
    TransitionPlan ph = planTransition(a, far, 10.0, house);
    std::printf("  house, far tempo: kind %d style %d\n", (int)ph.kind, ph.style);
    check(ph.kind != Template::Cut, "house does not cut", (double)(int)ph.kind);
    check((int)ph.kind >= 3 && ph.kind != Template::Cut, "and takes an exit", (double)(int)ph.kind);
    PlannerSettings hip;
    hip.genre = 1;
    TransitionPlan pp = planTransition(a, far, 10.0, hip);
    std::printf("  hip-hop, far tempo: kind %d style %d drop %d\n", (int)pp.kind, pp.style, pp.flags & kFlagDrop);
    check((int)pp.kind >= 3, "hip-hop gets an exit or a cut", (double)(int)pp.kind);
    std::printf(failures ? "%d FAILED\n" : "all ok\n", failures);
    return failures ? 1 : 0;
}
