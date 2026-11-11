// Host test of 0.0.90: in a long blend B gets its melody after one bar (at once for a vocal cut) and its bass at three eighths.
#include <cstdio>
#include "../mix/engine.cpp"  // automate() is internal to the engine

using namespace nooklin::mix;

static int failures = 0;
static void check(bool ok, const char* what, double v) {
    std::printf("  %-70s %s (%.3f)\n", what, ok ? "ok  " : "FAIL", v);
    if (!ok) failures++;
}

int main() {
    std::printf("long blend of 32 beats, no vocal cut\n");
    Automation a5 = automate(Template::LongBlend, 5.5, 32, 0);
    check(a5.eqB[1] > 0.95, "B melody is in after one bar", a5.eqB[1]);
    check(a5.eqA[1] < 0.05, "A melody is out at the same time", a5.eqA[1]);
    Automation a3 = automate(Template::LongBlend, 3.0, 32, 0);
    check(a3.eqB[1] < 0.01 && a3.eqA[1] > 0.99, "before that the melodies have not swapped", a3.eqB[1]);
    Automation b11 = automate(Template::LongBlend, 11.0, 32, 0);
    Automation b14 = automate(Template::LongBlend, 14.0, 32, 0);
    check(b11.eqB[0] < 0.01, "B bass is still out at beat 11", b11.eqB[0]);
    check(b14.eqB[0] > 0.99 && b14.eqA[0] < 0.01, "B bass has swapped by beat 14 (was beat 17)", b14.eqB[0]);

    std::printf("long blend of 32 beats with a vocal cut\n");
    Automation v3 = automate(Template::LongBlend, 3.0, 32, kFlagVocalCut);
    check(v3.eqB[1] > 0.99, "B melody is in as A melody goes out", v3.eqB[1]);
    check(v3.eqA[1] < 0.01, "A melody is out", v3.eqA[1]);
    Automation v1 = automate(Template::LongBlend, 1.0, 32, kFlagVocalCut);
    check(v1.eqA[1] + v1.eqB[1] > 0.9, "no hole in the melody while they swap", v1.eqA[1] + v1.eqB[1]);

    std::printf("blend of 16 beats\n");
    Automation c = automate(Template::LongBlend, 9.5, 16, 0);
    check(c.eqB[0] > 0.99, "B bass has swapped by beat 9.5", c.eqB[0]);
    std::printf("stem mix (overlays carry the curves)\n");
    Automation m = automate(Template::LongBlend, 8.0, 32, kFlagStemMix | kFlagStemLive);
    check(m.eqA[0] == 1.0 && m.eqA[1] == 1.0 && m.eqA[2] == 1.0 && m.eqB[0] == 1.0 && m.eqB[1] == 1.0, "EQ stays flat", m.eqB[1]);
    check(m.gainA > 0.99 && m.gainB > 0.99, "both songs at full volume in the middle", m.gainB);
    Automation m0 = automate(Template::LongBlend, 0.0, 32, kFlagStemMix | kFlagStemLive);
    check(m0.gainB < 0.01, "B starts silent", m0.gainB);
    Automation me = automate(Template::LongBlend, 32.0, 32, kFlagStemMix | kFlagStemLive);
    check(me.gainA < 0.01, "A is gone at the end", me.gainA);
    Automation mx = automate(Template::LongBlend, 8.0, 32, kFlagStemMix);
    check(mx.eqA[1] < 1.0 || mx.eqB[1] < 1.0 || mx.eqB[0] < 1.0, "without overlays it is the plain blend", mx.eqB[0]);
    std::printf(failures ? "%d FAILED\n" : "all ok\n", failures);
    return failures ? 1 : 0;
}
