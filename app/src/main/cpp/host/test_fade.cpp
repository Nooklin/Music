// Host test of 0.0.84: the fade out for a big difference in energy, and B coming in on a later strong section.
#include <cmath>
#include <cstdio>

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
static void show(const char* n, const TrackMap& m) {
    std::printf("  %s sections:", n);
    for (auto& sec : m.sections) std::printf(" [%ld %s %.2f]", sec.beat, sec.high ? "H" : "L", sec.ratio);
    std::printf("\n");
}

int main() {
    const int sr = 44100;
    // A is loud to the end; B is 24% away in tempo (no blend), starts quiet and gets loud after a third of the song
    std::vector<int> loudEnd = {0, 0, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2};
    std::vector<int> quietStart = {0, 0, 0, 0, 0, 0, 2, 2, 2, 2, 2, 2, 2, 2, 1, 0};
    TrackMap a = analyze(makeStructured(sr, 124.0, 0.35, loudEnd, 130.81, 3), sr);
    TrackMap b = analyze(makeStructured(sr, 100.0, 0.25, quietStart, 146.83, 5), sr);
    show("A", a);
    show("B", b);
    std::printf("big difference in energy\n");
    TransitionPlan p = planTransition(a, b, 10.0);
    std::printf("  kind %d style %d entry %.1f s startA %.0f energy %.2f -> %.2f flags %d\n", (int)p.kind, p.style, p.entrySecB, p.startBeatA,
                p.energyA, p.energyB, p.flags);
    // A drop of B is reachable now (it is at about 58 s): the exit comes in there, not on the quiet start
    check(p.kind == Template::FadeOut || p.kind == Template::EchoOut || p.kind == Template::FilterOut || (p.flags & kFlagDrop) != 0, "a loud end into a quiet start fades, echoes, filters (0.0.104) or comes in on B's drop", (double)(int)p.kind);
    check(p.kind == Template::EchoOut || p.kind == Template::FilterOut || p.entrySecB > b.cueIn + 20.0, "B comes in on its later strong section, or after an echo or filter exit (0.0.104)", p.entrySecB);
    check(p.kind != Template::BrakeOut && p.kind != Template::SpinOut, "no brake and no spin back out", (double)(int)p.kind);
    if (p.kind == Template::FadeOut)
        check(std::fabs(p.energyA - p.energyB) < 0.6 || p.entrySecB > b.cueIn + 1.0, "B comes in where it is as strong as A, or the gap is small", p.entrySecB);

    std::printf("a fade where the energy differs\n");
    std::vector<int> lateLoudA = {0, 0, 0, 0, 0, 0, 0, 0, 2, 2, 2, 2, 2, 2, 2, 2};
    std::vector<int> lateLoudB = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 2, 2, 2};
    TrackMap fa = analyze(makeStructured(sr, 124.0, 0.35, lateLoudA, 130.81, 3), sr);
    TrackMap fb = analyze(makeStructured(sr, 100.0, 0.25, lateLoudB, 146.83, 5), sr);
    TransitionPlan fd = planTransition(fa, fb, 10.0);
    std::printf("  kind %d style %d entry %.1f s energy %.2f -> %.2f\n", (int)fd.kind, fd.style, fd.entrySecB, fd.energyA, fd.energyB);
    check(fd.kind == Template::FadeOut || fd.kind == Template::EchoOut || fd.kind == Template::FilterOut, "a loud end into a quiet start is a fade, echo or filter exit (0.0.104)", (double)(int)fd.kind);

    std::printf("fade: B has no strong section to come in on\n");
    std::vector<int> flatQuiet = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    TrackMap f = analyze(makeStructured(sr, 100.0, 0.25, flatQuiet, 146.83, 5), sr);
    show("B", f);
    TransitionPlan fp = planTransition(a, f, 10.0);
    std::printf("  kind %d style %d entry %.1f s energy %.2f -> %.2f\n", (int)fp.kind, fp.style, fp.entrySecB, fp.energyA, fp.energyB);
    check(fp.kind != Template::BrakeOut && fp.kind != Template::SpinOut, "no brake and no spin back out", (double)(int)fp.kind);

    std::printf("small difference in energy\n");
    std::vector<int> evenB = {0, 0, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2};
    TrackMap c = analyze(makeStructured(sr, 100.0, 0.25, evenB, 146.83, 5), sr);
    TransitionPlan q = planTransition(a, c, 10.0);
    std::printf("  kind %d style %d energy %.2f -> %.2f\n", (int)q.kind, q.style, q.energyA, q.energyB);
    check(q.kind != Template::BrakeOut && q.kind != Template::SpinOut, "no brake and no spin back out", (double)(int)q.kind);
    check(q.kind != Template::Cut, "no cut between tempos 24% apart", (double)(int)q.kind);
    check(q.kind != Template::FadeOut || std::fabs(q.energyA - q.energyB) >= 0.35, "no fade where the energy is the same", std::fabs(q.energyA - q.energyB));
    std::printf(failures ? "%d FAILED\n" : "all ok\n", failures);
    return failures ? 1 : 0;
}
