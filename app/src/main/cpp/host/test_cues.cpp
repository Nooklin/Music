// Host test of 0.0.103: cue fields of the analysis (usable intro and outro, confidence, phrase length, swing, edge energy),
// the family dependent lengths of the planner, the bass swap fields and the correlation based level control.
#include <cmath>
#include <cstdio>

#include "../mix/analyzer.h"
#include "../mix/dsp.h"
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
    // blocks of 16 beats: intro 2, build 2, drop 4, breakdown 2, drop 4, outro 2
    std::vector<int> lv = {0, 0, 1, 1, 2, 2, 2, 2, -1, -1, 2, 2, 2, 2, 0, 0};
    Synth s = makeStructured(sr, 128.0, 0.35, lv);
    TrackMap m = analyze(s, sr);
    std::printf("structured song: cues %d confidence %.2f phrase %d intro %d outro %d energy %.2f..%.2f swing %.2f\n", (int)m.hasCues, m.cueConfidence,
                m.phraseBeats, m.usableIntroBeats, m.usableOutroBeats, m.energyStart, m.energyEnd, m.swing);
    check(m.hasCues, "cue fields are measured", m.hasCues);
    check(m.usableIntroBeats >= 16 && m.usableIntroBeats <= 80, "usable intro is between 16 and 80 beats (true: 32 quiet + 32 build)", m.usableIntroBeats);
    check(m.usableOutroBeats >= 16 && m.usableOutroBeats <= 48, "usable outro is between 16 and 48 beats (true: 32)", m.usableOutroBeats);
    check(m.cueConfidence >= 0.4, "cue confidence of a clearly structured song is at least 0.4", m.cueConfidence);
    check(m.phraseBeats == 16 || m.phraseBeats == 32, "phrase length is 16 or 32", m.phraseBeats);
    check(m.swing > 0.35 && m.swing < 0.65, "a straight synthetic groove is not swung", m.swing);
    check(m.energyStart >= 0.0 && m.energyEnd >= 0.0, "edge energies are measured", m.energyEnd);
    check(m.energyEnd < 0.6, "the quiet outro reads as quiet", m.energyEnd);

    // A flat song starts at full strength: no usable intro to speak of
    std::vector<int> flat(16, 2);
    TrackMap mf = analyze(makeStructured(sr, 128.0, 0.35, flat, 130.81, 4), sr);
    std::printf("flat song: confidence %.2f intro %d outro %d\n", mf.cueConfidence, mf.usableIntroBeats, mf.usableOutroBeats);
    check(mf.usableIntroBeats <= 8, "a flat song has no usable intro", mf.usableIntroBeats);
    check(mf.usableOutroBeats <= 8, "and no usable outro", mf.usableOutroBeats);

    // Planner: the family changes the length. The same pair planned for every family: all plans are legal and the hard family swaps hard.
    TrackMap b = analyze(makeStructured(sr, 128.0, 0.2, lv, 130.81), sr);
    int n8[5] = {};
    for (int g = 0; g < 5; g++) {
        PlannerSettings st;
        st.genre = g;
        st.maxTempoChange = 0.06;
        TransitionPlan p = planTransition(m, b, 10.0, st);
        std::printf("  family %d: kind %d style %d length %d flags %d bass swap %.0f cost %.2f\n", g, (int)p.kind, p.style, p.lengthBeats, p.flags, p.bassSwapBeat, p.cost);
        if (p.kind == Template::BassSwapShort) n8[g]++;
        if (p.kind == Template::LongBlend) check((p.flags & kFlagLevel) != 0, "every blend carries the level flag", g);
        if (g == 4 && p.kind == Template::LongBlend && p.lengthBeats >= 16) {
            check((p.flags & kFlagHardSwap) != 0, "family C blends with a hard swap", p.flags);
            check(p.bassSwapBeat >= 4.0 && std::fmod(p.bassSwapBeat, 4.0) == 0.0, "on a bar line", p.bassSwapBeat);
        }
    }
    check(n8[2] == 0 && n8[3] == 0, "house and bass families do not pick an 8 beat blend for this pair", n8[2] + n8[3]);

    // 0.0.110: a drop of B that lands in the middle or at the end of the overlap is rewarded (rolling blends stay off, so the double drop is the
    // style that can do it). 32 beats forced: the synthetic B has drops at beats 64 and 160.
    {
        PlannerSettings st;
        st.genre = 4;
        st.maxTempoChange = 0.06;
        st.lengthBeats = 32;
        TransitionPlan p = planTransition(m, b, 10.0, st);
        const double lenB = (double)p.lengthBeats / p.beatFactor;
        std::printf("  build up: kind %d style %d length %d factor %.1f startA %.0f startB %.0f cost %.2f\n", (int)p.kind, p.style, p.lengthBeats, p.beatFactor, p.startBeatA, p.startBeatB, p.cost);
        auto lands = [&](double drop) { return std::fabs(p.startBeatB + lenB - drop) <= 1.0 || std::fabs(p.startBeatB + lenB / 2.0 - drop) <= 1.0; };
        check(lands(64.0) || lands(160.0), "B's drop falls in the middle or at the end of the overlap", p.startBeatB + lenB);
    }

    // 0.0.110: a hand-over of the voice inside the overlap (A has finished singing before B starts) is rewarded
    {
        PlannerSettings st;
        st.genre = 2;
        st.maxTempoChange = 0.06;
        st.lengthBeats = 16;
        TrackMap va = m, vb = b, vn1 = m, vn2 = b;
        const size_t na = va.beats.size(), nb = vb.beats.size();
        va.vocal.assign(na, 0.0f); vb.vocal.assign(nb, 0.0f); vn1.vocal.assign(na, 0.0f); vn2.vocal.assign(nb, 0.0f);
        for (size_t i = 224; i < 230 && i < na; i++) va.vocal[i] = 0.6f;   // A sings in the first beats of the overlap, then stops
        for (size_t i = 8; i < 14 && i < nb; i++) vb.vocal[i] = 0.6f;       // B starts singing at beat 8 of the overlap
        TransitionPlan ph = planTransition(va, vb, 10.0, st), pn = planTransition(vn1, vn2, 10.0, st);
        std::printf("  voice hand-over: cost %.2f startA %.0f startB %.0f; no singing: cost %.2f startA %.0f startB %.0f\n", ph.cost, ph.startBeatA, ph.startBeatB, pn.cost, pn.startBeatA, pn.startBeatB);
        check(ph.startBeatA == pn.startBeatA && ph.startBeatB == pn.startBeatB, "the same place is chosen", ph.startBeatA);
        check(ph.cost < pn.cost - 0.1, "the hand-over costs less than no singing at all (a reward)", pn.cost - ph.cost);
    }

    // 0.0.109: a song that stays loud to its very end goes into a drop of B (no exit), the same for every family
    {
        std::vector<int> loudEnd = {0, 0, 1, 1, 2, 2, 2, 2, -1, -1, 2, 2, 2, 2, 2, 2};
        TrackMap la = analyze(makeStructured(sr, 128.0, 0.35, loudEnd), sr);
        for (int g : {0, 4}) {
            PlannerSettings st;
            st.genre = g;
            st.maxTempoChange = 0.06;
            TransitionPlan p = planTransition(la, b, 10.0, st);
            std::printf("  loud end, family %d: kind %d style %d length %d startA %.0f startB %.0f cost %.2f energy %.2f to %.2f\n", g, (int)p.kind, p.style, p.lengthBeats,
                        p.startBeatA, p.startBeatB, p.cost, p.energyA, p.energyB);
            check(p.kind == Template::LongBlend || p.kind == Template::BassSwapShort, "a loud end of A is blended, not an exit", (double)(int)p.kind);
        }
    }

    // 0.0.112: loud end of A into a B without any drop (flat song): the last beats of A overlap the start of B, no exit
    {
        std::vector<int> loudEnd = {0, 0, 1, 1, 2, 2, 2, 2, -1, -1, 2, 2, 2, 2, 2, 2};
        std::vector<int> flat = {0, 0, 1, 1, 1, 1, 1, 1, 0, 0, 1, 1, 1, 1, 1, 1};
        TrackMap la = analyze(makeStructured(sr, 128.0, 0.35, loudEnd), sr);
        TrackMap fb = analyze(makeStructured(sr, 128.0, 0.35, flat), sr);
        PlannerSettings st;
        st.genre = 0;
        st.maxTempoChange = 0.06;
        st.lengthBeats = 16;   // forces the end-of-A overlap (the only 16-beat place that fits a loud end)
        TransitionPlan p = planTransition(la, fb, 10.0, st);
        std::printf("  loud end into flat B: kind %d style %d length %d startA %.0f startB %.0f cost %.2f\n", (int)p.kind, p.style, p.lengthBeats, p.startBeatA, p.startBeatB, p.cost);
        check(p.kind == Template::LongBlend || p.kind == Template::BassSwapShort, "no drop in B: still a blend", (double)(int)p.kind);
    }

    // Level control: two identical signals (correlation 1) are scaled down, two opposite ones are scaled up, uncorrelated ones are left alone
    {
        const int n = 64, blocks = 400;
        std::vector<float> a(2 * n), c(2 * n);
        double kSame = 1.0, kOpp = 1.0, kUnc = 1.0;
        BlendLeveler l1, l2, l3;
        uint32_t r = 12345;
        auto rnd = [&]() { r = r * 1664525u + 1013904223u; return (float)((r >> 8) & 0xffff) / 32768.0f - 1.0f; };
        for (int k = 0; k < blocks; k++) {
            for (int i = 0; i < 2 * n; i++) a[i] = 0.3f * rnd();
            for (int i = 0; i < 2 * n; i++) c[i] = a[i];
            kSame = l1.update(a.data(), c.data(), n, sr, 0.7071, 0.7071);
            for (int i = 0; i < 2 * n; i++) c[i] = -a[i] * 0.99f;
            kOpp = l2.update(a.data(), c.data(), n, sr, 0.7071, 0.7071);
            for (int i = 0; i < 2 * n; i++) c[i] = 0.3f * rnd();
            kUnc = l3.update(a.data(), c.data(), n, sr, 0.7071, 0.7071);
        }
        check(kSame < 0.88, "identical songs: the sum is scaled down (to the 0.85 floor, 0.0.107)", kSame);
        check(kOpp >= 1.19, "opposite songs: the sum is scaled up (to the 1.2 ceiling, 0.0.107)", kOpp);
        check(kUnc > 0.9 && kUnc < 1.1, "uncorrelated songs: left alone", kUnc);
    }

    // Octave (0.0.105): fast music is measured at half tempo by the search; the app then builds the grid again at twice that tempo. The forced
    // grid must come out at the doubled tempo, fit the onsets as well, and keep its cue fields.
    for (double fast : {174.0, 190.0}) {
        Synth sf = makeTrack(sr, fast, 0.3, 120.0, 0.0, 130.81, 7);
        std::vector<float> dec(sf.pcm.size() / 4);
        for (size_t i = 0; i < dec.size(); i++) dec[i] = 0.25f * (sf.pcm[4 * i] + sf.pcm[4 * i + 1] + sf.pcm[4 * i + 2] + sf.pcm[4 * i + 3]);
        TrackMap half = analyzeTrack(dec.data(), dec.size(), sr / 2);
        TrackMap full = analyzeTrack(dec.data(), dec.size(), sr / 2, 2.0 * half.bpm);
        std::printf("true %.0f: search %.2f, doubled grid %.2f (evidence %.2f against %.2f)\n", fast, half.bpm, full.bpm, full.tempoEvidence.size() < 2 ? -1.0 : (double)full.tempoEvidence[1], half.tempoEvidence.size() < 2 ? -1.0 : (double)half.tempoEvidence[1]);
        check(std::fabs(half.bpm * 2.0 / fast - 1.0) < 0.02, "the search itself reads fast music at half tempo (the known limit)", half.bpm);
        check(std::fabs(full.bpm / fast - 1.0) < 0.01, "the grid built at twice that tempo is at the true tempo", full.bpm);
        check(full.tempoEvidence.size() >= 2 && half.tempoEvidence.size() >= 2 && full.tempoEvidence[1] >= half.tempoEvidence[1] - 0.1, "and fits the onsets as well (coverage)", full.tempoEvidence.size() < 2 ? -1.0 : full.tempoEvidence[1]);
        check(full.beats.size() > half.beats.size() * 3 / 2, "with about twice as many beats", (double)full.beats.size());
    }
    std::printf(failures ? "%d FAILED\n" : "all ok\n", failures);
    return failures ? 1 : 0;
}
