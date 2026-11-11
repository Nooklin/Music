// Measurement tool and listening test (0.0.103). Two modes:
//
//   render_pair report a.wav [b.wav ...]
//       analyses each file and prints its cue fields: bpm, confidence, sections, usable intro and outro, phrase length, swing, edge energy.
//   render_pair pair a.wav b.wav [family 0..4] [outdir]
//       plans the change from A to B once for each forced length of 8, 16, 32 and 64 beats (and once free), prints the plan and its cost and
//       renders every plan with the real engine to outdir/pair_L8.wav ... pair_free.wav (about 15 s before the overlap until 10 s after it),
//       so the lengths can be compared by ear on the same pair at the same place.
//   render_pair synth outdir
//       writes two synthetic songs as wav files (to try the tool without music).
//
// WAV files: 16-bit PCM or 32-bit float, mono or stereo, 44100 Hz. Build:
//   g++ -O2 -std=c++17 -Ithird_party host/render_pair.cpp mix/analyzer.cpp mix/planner.cpp mix/engine.cpp -o render_pair
#include <cmath>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>

#include "../mix/analyzer.h"
#include "../mix/engine.h"
#include "../mix/planner.h"
#include "synth.h"

using namespace nooklin::mix;
using namespace nooklin::mix::host;

static bool readWav(const char* path, std::vector<float>* stereo, int* rate) {
    FILE* f = std::fopen(path, "rb");
    if (!f) return false;
    std::vector<unsigned char> d;
    unsigned char buf[65536];
    size_t n;
    while ((n = std::fread(buf, 1, sizeof buf, f)) > 0) d.insert(d.end(), buf, buf + n);
    std::fclose(f);
    if (d.size() < 44 || std::memcmp(d.data(), "RIFF", 4) != 0 || std::memcmp(d.data() + 8, "WAVE", 4) != 0) return false;
    auto u16 = [&](size_t i) { return (int)(d[i] | (d[i + 1] << 8)); };
    auto u32 = [&](size_t i) { return (uint32_t)(d[i] | (d[i + 1] << 8) | (d[i + 2] << 16) | ((uint32_t)d[i + 3] << 24)); };
    int fmt = 0, ch = 0, bits = 0;
    size_t at = 12;
    while (at + 8 <= d.size()) {
        const uint32_t sz = u32(at + 4);
        if (std::memcmp(d.data() + at, "fmt ", 4) == 0) {
            fmt = u16(at + 8); ch = u16(at + 10); *rate = (int)u32(at + 12); bits = u16(at + 22);
        } else if (std::memcmp(d.data() + at, "data", 4) == 0) {
            const size_t bytes = std::min<size_t>(sz, d.size() - at - 8);
            const unsigned char* p = d.data() + at + 8;
            if (ch < 1 || ch > 2) return false;
            const size_t frames = bytes / (size_t)(ch * bits / 8);
            stereo->assign(frames * 2, 0.0f);
            for (size_t i = 0; i < frames; i++)
                for (int c = 0; c < 2; c++) {
                    const size_t o = (i * (size_t)ch + (size_t)std::min(c, ch - 1)) * (size_t)(bits / 8);
                    float v = 0.0f;
                    if (fmt == 1 && bits == 16) v = (float)(int16_t)(p[o] | (p[o + 1] << 8)) / 32768.0f;
                    else if (fmt == 3 && bits == 32) { uint32_t x = p[o] | (p[o + 1] << 8) | (p[o + 2] << 16) | ((uint32_t)p[o + 3] << 24); std::memcpy(&v, &x, 4); }
                    else return false;
                    (*stereo)[2 * i + (size_t)c] = v;
                }
            return true;
        }
        at += 8 + sz + (sz & 1);
    }
    return false;
}

static bool writeWav(const char* path, const std::vector<float>& stereo, int rate) {
    FILE* f = std::fopen(path, "wb");
    if (!f) return false;
    const uint32_t bytes = (uint32_t)(stereo.size() * 2);
    auto w32 = [&](uint32_t v) { std::fwrite(&v, 4, 1, f); };
    auto w16 = [&](uint16_t v) { std::fwrite(&v, 2, 1, f); };
    std::fwrite("RIFF", 1, 4, f); w32(36 + bytes); std::fwrite("WAVEfmt ", 1, 8, f);
    w32(16); w16(1); w16(2); w32((uint32_t)rate); w32((uint32_t)rate * 4); w16(4); w16(16);
    std::fwrite("data", 1, 4, f); w32(bytes);
    for (float v : stereo) { const int16_t s = (int16_t)std::lround(std::max(-1.0f, std::min(1.0f, v)) * 32767.0f); std::fwrite(&s, 2, 1, f); }
    std::fclose(f);
    return true;
}

static TrackMap analyze(const std::vector<float>& stereo, int sr) {
    std::vector<float> dec(stereo.size() / 4);
    for (size_t i = 0; i < dec.size(); i++) dec[i] = 0.25f * (stereo[4 * i] + stereo[4 * i + 1] + stereo[4 * i + 2] + stereo[4 * i + 3]);
    return analyzeTrack(dec.data(), dec.size(), sr / 2);
}

static void report(const char* name, const TrackMap& m) {
    std::printf("%s: bpm %.2f confidence %.2f beats %zu duration %.1f s sections", name, m.bpm, m.confidence, m.beats.size(), m.duration);
    for (const auto& sec : m.sections) std::printf(" [%ld %c %.2f]", sec.beat, sec.high ? 'H' : 'L', (double)sec.ratio);
    std::printf("\n   cues: confidence %.2f phrase %d intro %d beats outro %d beats edge energy %.2f to %.2f swing %.2f loudness %.1f dB\n", m.cueConfidence,
                m.phraseBeats, m.usableIntroBeats, m.usableOutroBeats, m.energyStart, m.energyEnd, m.swing, m.loudness);
}

static const char* kindName(Template k) {
    static const char* n[] = {"long blend", "short blend", "fallback", "echo", "filter", "cut", "reverb", "brake", "spin", "roll", "fade"};
    return n[(int)k];
}

int main(int argc, char** argv) {
    const int sr = 44100;
    if (argc >= 3 && !std::strcmp(argv[1], "synth")) {
        std::vector<int> lv = {0, 0, 1, 1, 2, 2, 2, 2, -1, -1, 2, 2, 2, 2, 0, 0};
        Synth a = makeStructured(sr, 126.0, 0.35, lv, 130.81), b = makeStructured(sr, 128.0, 0.2, lv, 146.83);
        const std::string d = argv[2];
        return writeWav((d + "/synth_a.wav").c_str(), a.pcm, sr) && writeWav((d + "/synth_b.wav").c_str(), b.pcm, sr) ? 0 : 1;
    }
    if (argc >= 3 && !std::strcmp(argv[1], "report")) {
        for (int i = 2; i < argc; i++) {
            std::vector<float> pcm;
            int rate = 0;
            if (!readWav(argv[i], &pcm, &rate) || rate != sr) { std::printf("%s: cannot read (need 16-bit or float wav at 44100 Hz)\n", argv[i]); continue; }
            report(argv[i], analyze(pcm, sr));
        }
        return 0;
    }
    if (argc >= 4 && !std::strcmp(argv[1], "pair")) {
        std::vector<float> pa, pb;
        int ra = 0, rb = 0;
        if (!readWav(argv[2], &pa, &ra) || !readWav(argv[3], &pb, &rb) || ra != sr || rb != sr) { std::printf("cannot read the files (16-bit or float wav at 44100 Hz)\n"); return 1; }
        const int family = argc >= 5 ? std::atoi(argv[4]) : 0;
        const std::string outdir = argc >= 6 ? argv[5] : ".";
        const bool trace = argc >= 7;   // 0.0.107: a 6th argument prints B's level per beat
        TrackMap ma = analyze(pa, sr), mb = analyze(pb, sr);
        report("A", ma);
        report("B", mb);
        if (!ma.valid() || !mb.valid()) { std::printf("no beat grid in one of the songs\n"); return 1; }
        for (int L : {8, 16, 32, 64, 0}) {
            PlannerSettings st;
            st.genre = family;
            st.maxTempoChange = 0.10;
            st.lengthBeats = L;
            const TransitionPlan plan = planTransition(ma, mb, 0.0, st);
            std::printf("length %s: kind %s style %d length %d startA %.0f startB %.0f cost %.2f key clash %.2f energy %.2f to %.2f flags %d bass swap %.0f\n",
                        L ? std::to_string(L).c_str() : "free", kindName(plan.kind), plan.style, plan.lengthBeats, plan.startBeatA, plan.startBeatB, plan.cost,
                        plan.keyClash, plan.energyA, plan.energyB, plan.flags, plan.bassSwapBeat);
            if (plan.kind == Template::Fallback) continue;
            Engine e(sr);
            const double from = std::max(0.0, ma.timeAtBeat(plan.startBeatA) - 15.0);
            e.play(std::make_shared<VectorSource>(pa), ma, from);
            std::vector<float> out(2 * 256), all;
            e.render(out.data(), 256);
            e.loadNext(std::make_shared<VectorSource>(pb), mb);
            e.setPlan(plan);
            long after = -1;
            for (long frames = 0; frames < (long)sr * 240; frames += 256) {
                e.render(out.data(), 256);
                all.insert(all.end(), out.begin(), out.end());
                if (trace && e.trace.gainB > 0 && (frames / 256) % (long)(sr * 60.0 / ma.bpm / 256) == 0)
                    std::printf("      t %.1f s  gainB %.2f  bal %.2f  leveler %.2f  bassB %.2f  => B level %.2f (bass x%.2f)\n", (double)frames / sr,
                                e.trace.gainB, e.trace.bal, e.trace.lv, e.trace.bassB, e.trace.gainB * e.trace.bal * e.trace.lv, e.trace.bassB);
                if (e.telemetry.changes.load() > 0 && after < 0) after = frames;
                if (after >= 0 && frames - after > (long)sr * 10) break;
            }
            const std::string path = outdir + "/pair_L" + (L ? std::to_string(L) : std::string("free")) + ".wav";
            std::printf("   wrote %s (%.1f s)\n", path.c_str(), (double)all.size() / 2.0 / sr);
            writeWav(path.c_str(), all, sr);
        }
        return 0;
    }
    std::printf("usage: render_pair report a.wav [b.wav ...] | pair a.wav b.wav [family 0..4] [outdir] | synth outdir\n");
    return 1;
}
