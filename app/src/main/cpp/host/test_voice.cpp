// Host test of 0.0.110: when the singing is taken apart in the overlays (kFlagVoiceLive), A's mids (its voice) are not cut at the start of the
// overlap, so a sentence can be finished; without the overlays the old cut stays. B is at full gain two beats into a long blend.
#include <cstdio>
#include "../mix/engine.cpp"  // automate() is internal to the engine

using namespace nooklin::mix;

static int failures = 0;
static void check(bool ok, const char* what, double v) {
    std::printf("  %-70s %s (%.3f)\n", what, ok ? "ok  " : "FAIL", v);
    if (!ok) failures++;
}

int main() {
    Automation cut = automate(Template::LongBlend, 3.0, 16, kFlagVocalCut);
    check(cut.eqA[1] < 0.05, "without overlays: A's mids are cut early for the singing", cut.eqA[1]);
    Automation live = automate(Template::LongBlend, 3.0, 16, kFlagVocalCut | kFlagVoiceLive);
    check(live.eqA[1] > 0.95, "with overlays: A's mids (its voice) are still there at beat 3", live.eqA[1]);
    Automation late = automate(Template::LongBlend, 10.0, 16, kFlagVocalCut | kFlagVoiceLive);
    check(late.eqA[1] < 0.05, "and they are gone after the bass swap", late.eqA[1]);
    check(live.eqB[1] < 0.05, "B's mids open at beat 4 as without singing, not at once", live.eqB[1]);
    Automation sh = automate(Template::BassSwapShort, 1.5, 8, kFlagVocalCut | kFlagVoiceLive);
    check(sh.eqA[1] > 0.95, "short swap with overlays: A's voice is kept for the first half", sh.eqA[1]);
    Automation shCut = automate(Template::BassSwapShort, 1.5, 8, kFlagVocalCut);
    check(shCut.eqA[1] < 0.5, "short swap without overlays: cut as before", shCut.eqA[1]);
    Automation g = automate(Template::LongBlend, 3.0, 16, 0);
    check(g.gainB > 0.98, "B is at full gain two beats into a long blend (0.0.108)", g.gainB);
    std::printf(failures ? "FAILED\n" : "all ok\n");
    return failures ? 1 : 0;
}
