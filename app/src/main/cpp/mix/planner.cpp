#include "planner.h"

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cstdio>
#include <string>
#include <vector>

#include "harmony.h"

namespace nooklin::mix {
namespace {

constexpr double kMaxEarlyExitSec = 45.0;   // how long before the end of the music of A the overlap may end (0.0.67: was 120)
constexpr double kMaxEarlyBlendSec = 60.0;  // 0.0.97: a blend on a breakdown or outro may end this long before the music of A ends (the cost of leaving early still counts)
// 0.0.98: switched off after the log of 0.0.97 (the code stays, only the switch changes): a fade onto a drop of B, a relaxed blend of 64 beats
// and the rolling blend
constexpr bool kFadeOntoDrop = false, kRollingBlend = false;
// 0.0.100: the same for a cut onto a drop of B (with impact and riser, and the beat roll that comes from it): it never sounded right
constexpr bool kCutOntoDrop = false;
constexpr double kMaxEntrySec = 60.0;       // how far into B the overlap may start
constexpr double kEarlyCostPerMin = 0.4;    // cost of leaving A early, per minute of the song that is not played
constexpr double kSkipCostPerMin = 0.8;     // cost of skipping the start of B, per minute

// Exit techniques (echo, filter, cut): base costs, lower is preferred; the rules are in exitCost()
constexpr double kEchoBase = 0.30;
constexpr double kFilterBase = 0.35;
constexpr double kCutBase = 0.40;
constexpr double kStretchCost = 3.0;        // cost per unit of log tempo change applied to the songs (0.0.77: was 1.5; every blend with a shared change of 6% or more per song had 3.7 to 15 ms of phase error at the handoff, the others about 0)
constexpr double kDropRise = 1.4;           // energy after a phrase start over the energy before it that makes it a drop
constexpr double kDropMin = 0.8;            // and the energy after it must be at least this
constexpr double kMaxDropSec = 90.0;        // drops this far into B count as entries for the exit techniques (0.0.84: was 24; a drop later in B that suits the end of A is better than a quiet start)
constexpr double kMaxEntryShare = 0.4;      // 0.0.86: B is not entered deeper than this share of its music (log 0.0.84: a double drop came in at 53% of B)
constexpr double kFadeGap = 0.35;           // the end of A and the start of B differ at least this much in energy: a fade out and in
constexpr double kFadeBase = 0.20;
constexpr double kFadePenalty = 0.7;        // 0.0.104: a fade out and in is not mixing; echo or filter on a bar line wins when it is possible (log 0.0.103: 9 of 13 changes were fades)
constexpr double kCutMaxTempoGap = 0.03;    // 0.0.88: a cut only between songs whose tempos are this close (half and double count), otherwise it is heard as a jump
// True when the tempos of A and B, folded by a factor 1, 2 or 1/2, are within kCutMaxTempoGap. A tempo in doubt (no tempo change allowed) or unknown counts as not close.
inline bool cutTempoOk(const TrackMap& a, const TrackMap& b, const PlannerSettings& s) {
    if (s.maxTempoChange <= 0.0 || a.bpm <= 0.0 || b.bpm <= 0.0) return false;
    double best = 1e9;
    for (double f : {0.5, 1.0, 2.0}) best = std::min(best, std::fabs(std::log(a.bpm / b.bpm / f)));
    return best <= std::log(1.0 + kCutMaxTempoGap);
}
constexpr int kEchoPre = 2, kFilterPre = 4; // beats of the exit effect before the bar line

constexpr double kSingingShare = 0.12;   // share of singing in a beat from which it counts as singing (legacy planner)
constexpr double kSingingAtExit = 0.12;  // share of singing at the end of A from which it counts as singing

// Beats of the exit before the bar line, with the riser when the plan has one
double preBeats(Template kind, int flags) {
    const double k = (kind == Template::FilterOut || kind == Template::FadeOut) ? 4.0 : kind == Template::Cut ? 0.0 : 2.0;
    return (flags & kFlagRiser) ? std::max(k, 4.0) : k;
}

// The same pair of songs always gets the same number, so a plan does not change when it is made again
uint32_t pairHash(const TrackMap& a, const TransitionPlan& p) {
    uint32_t h = 2166136261u;
    auto mix = [&h](long v) {
        h ^= (uint32_t)v;
        h *= 16777619u;
        h ^= h >> 15;
    };
    mix((long)(p.startBeatA * 4.0));
    mix((long)(p.entrySecB * 100.0));
    mix((long)(a.bpm * 100.0));
    mix((long)(p.energyA * 1000.0));
    return h;
}

/**
 * Turns the plain exit of the planner (echo, filter or cut) into the exit a DJ would pick for this moment. A cut onto a drop
 * gets an impact and often a riser; a soft exit is picked from echo, filter, reverb, brake, spin back and beat roll with
 * weights that follow the genre, the energy at the end of A and whether A is singing there. The choice is made from a
 * hash of the pair, so the same pair gives the same exit every time. Only effects that fit in the time left are used.
 */
// 0.0.121: false: no reverb out (user: sounds really bad, "exit: reverb out onto a drop"). Set to true to bring it back.
constexpr bool kReverbExit = false;
TransitionPlan refineExit(TransitionPlan p, const TrackMap& a, double nowA, const PlannerSettings& s) {
    if (p.kind != Template::EchoOut && p.kind != Template::FilterOut && p.kind != Template::Cut) return p;
    const long i = (long)p.startBeatA;
    const auto fits = [&](Template k, int flags) { return a.timeAtBeat((double)i - preBeats(k, flags)) >= nowA + s.leadSeconds; };
    const uint32_t h = pairHash(a, p);
    const double eA = p.energyA;
    const bool singing = a.vocalMean((double)i - 2.0, (double)i) > kSingingAtExit;
    const bool drop = (p.flags & kFlagDrop) != 0;

    if (p.kind == Template::Cut) {
        if (drop && !singing && eA >= 0.5 && h % 4 == 0 && fits(Template::RollOut, kFlagRiser | kFlagImpact)) {
            p.kind = Template::RollOut;
            p.flags |= kFlagRiser | kFlagImpact;
        } else if (drop) {
            p.flags |= kFlagImpact;
            if (eA >= 0.5 && h % 2 == 0 && fits(Template::Cut, kFlagRiser)) p.flags |= kFlagRiser;
        }
        return p;
    }

    struct Option {
        Template kind;
        double w;
    };
    const int g = s.genre == 4 ? 3 : s.genre;  // family C exits like family B
    Option opts[6] = {
        {Template::EchoOut, g == 2 ? 1.5 : (g == 3 ? 1.0 : 2.0)},
        {Template::FilterOut, g == 2 ? 3.0 : (g == 3 ? 2.0 : 1.5)},
        {Template::ReverbOut, g == 2 ? 1.0 : (g == 3 ? 0.5 : (eA < 0.55 ? 3.0 : 1.5))},
        {Template::BrakeOut, 0.0},  // 0.0.84: no brake and no spin back out (they sounded odd); a big energy gap gets a fade
        {Template::SpinOut, 0.0},
        {Template::RollOut, g == 2 ? 1.5 : (g == 3 ? 2.0 : 1.0)},
    };
    for (Option& o : opts) {
        if (o.kind == p.kind) o.w *= 1.5;  // the planner picked this one for its own reasons
        if (singing) {
            if (o.kind == Template::SpinOut || o.kind == Template::RollOut) o.w = 0.0;  // not in the middle of a word
            if (o.kind == Template::BrakeOut) o.w *= 0.3;
            if (o.kind == Template::ReverbOut) o.w *= 1.5;
        }
        if (o.kind == Template::ReverbOut && !kReverbExit) o.w = 0.0;  // 0.0.121: switched off for now (its 9 s tail rang over the drop of B)
        if (o.kind == Template::RollOut && eA < 0.4) o.w = 0.0;  // a roll needs something to roll
        if (o.kind == Template::BrakeOut && eA < 0.3) o.w = 0.0;
        if (!fits(o.kind, p.flags)) o.w = 0.0;
    }
    double total = 0.0;
    for (const Option& o : opts) total += o.w;
    if (total <= 0.0) return p;
    double r = (double)(h % 10007u) / 10007.0 * total;
    for (const Option& o : opts) {
        if (o.w <= 0.0) continue;
        if (r < o.w) {
            p.kind = o.kind;
            break;
        }
        r -= o.w;
    }
    if (drop && (p.kind == Template::RollOut || p.kind == Template::BrakeOut || p.kind == Template::SpinOut)) {
        p.flags |= kFlagImpact;
        if (p.kind == Template::RollOut && fits(p.kind, p.flags | kFlagRiser)) p.flags |= kFlagRiser;
    }
    return p;
}

TransitionPlan fallbackPlan(const TrackMap& a, const TrackMap& b, double nowA, double seconds) {
    TransitionPlan p;
    p.kind = Template::Fallback;
    p.fallbackSeconds = seconds;
    const double end = a.musicEnd() > 0.0 ? a.musicEnd() : a.duration;
    p.fallbackStartA = std::max(nowA + 1.0, end - seconds);
    p.fallbackStartB = b.cueIn;
    return p;
}

// Rules of 0.0.74 for what a DJ never does, the same in every planner
// 1. loud into calm: a loud end of A is not mixed into a calm start of B (an exit is chosen; the blend stays as a last resort)
constexpr double kBusyEnergy = 0.7, kCalmShare = 0.45;  // 0.0.76: was 0.8 (a 0.74 into 0.12 swap slipped through); 0.0.99: 0.6 to 0.45 (0.91 into 0.51 was turned down by 0.04)
inline bool busyIntoCalm(double eA, double eB) { return eA >= kBusyEnergy && eB <= kCalmShare * eA; }
// 6. a blend that costs this much more than the best exit gives way to the exit; above the ceiling an exit is looked for at all
//    (log 0.0.76: a relaxed blend of cost 3.9 with 12 beats of two voices won because blends always won)
constexpr double kBlendExitMargin = 0.5, kBlendCostCeiling = 1.5;
// 3. two voices on top of each other: a blend in which both songs sing together for more than this many beats gives way to an exit
//    (log 0.0.75: 64 beats with 62% = 40 beats on top of each other cost 3.28 and still won against every exit). A short overlap
//    stays possible: A's mids go out for it (flag kFlagVocalCut)
constexpr double kMaxSingBeats = 16.0;
// 0.0.86: without stems two voices on top of each other cost 12 per share of the overlap (was 4) and are allowed for at most 4 beats
// (was 16): the least singing together wins. With stems the singing of one song is taken out of the overlap, so it is cheap.
constexpr double kMaxSingBeatsNoStems = 4.0, kSingWeightNoStems = 12.0, kSingWeightStems = 1.5;
// 0.0.92: with stems a long blend (16 beats or more) becomes a stem mix: drums, bass, melody and singing of both songs get their own
// volume curve. The stretch is split offline (about 25 to 55 s of work per change), so it may not be longer than the model takes
// at once (40 s with a margin of 2 s on both sides).
constexpr double kStemMixMaxSec = 34.0;
inline int withStemMix(const TrackMap& a, const TrackMap& b, const TransitionPlan& p, const PlannerSettings& s) {
    if (!s.stems || p.kind != Template::LongBlend || p.lengthBeats < 16) return p.flags;
    const double sa = a.timeAtBeat(p.startBeatA + p.lengthBeats) - a.timeAtBeat(p.startBeatA);
    const double sb = b.timeAtBeat(p.startBeatB + p.lengthBeats / p.beatFactor) - b.timeAtBeat(p.startBeatB);
    if (sa <= 0.0 || sb <= 0.0 || sa > kStemMixMaxSec || sb > kStemMixMaxSec) return p.flags;
    return p.flags | kFlagStemMix;
}
inline bool poorBlend(const TransitionPlan& p, bool stems = false) {
    if (busyIntoCalm(p.energyA, p.energyB)) return true;
    if (stems) return false;
    return p.vocalOverlap > 0.0 && p.vocalOverlap * (double)p.lengthBeats > kMaxSingBeatsNoStems;
}
// 0.0.129: which rule turned this blend down, for the log (only called for a blend that lost; names the first rule that applies, in the order of the gates)
inline std::string rejectedBy(const TransitionPlan& p, bool stems, double maxBlendClash, bool exitFound, double blendLimit) {
    char b[96];
    if (p.keyClash > maxBlendClash) { std::snprintf(b, sizeof b, "keyGate (limit %.2f)", maxBlendClash); return b; }
    if (busyIntoCalm(p.energyA, p.energyB)) { std::snprintf(b, sizeof b, "loudToCalm (B at most %.0f%% of a loud A)", kCalmShare * 100.0); return b; }
    if (!stems && p.vocalOverlap > 0.0 && p.vocalOverlap * (double)p.lengthBeats > kMaxSingBeatsNoStems) { std::snprintf(b, sizeof b, "singingOnSinging (over %.0f beats)", kMaxSingBeatsNoStems); return b; }
    if (exitFound) { std::snprintf(b, sizeof b, "exitCheaper (blend may cost at most %.2f)", blendLimit); return b; }
    return "-";
}
// 2. a jump in energy always costs: down more than up (calm into loud is a build up)
inline double energyJumpCost(double eA, double eB) { return 0.8 * std::max(0.0, eA - eB) + 0.3 * std::max(0.0, eB - eA); }
// 5. a long blend over a calm start of B lets two songs run together while B does nothing: at most 16 beats
inline bool longOverCalm(int L, double eA, double eB, const PlannerSettings& s) { return s.lengthBeats == 0 && L > 16 && eB < 0.4 && eA >= 0.5; }
// 4. clashing keys weigh double (they were 0.8 per 16 beats, 1.6 now)
constexpr double kKeyWeight = 1.6;

// ---- 0.0.103: families, lengths, feel, bass swap ---------------------------------------------------------------------------
// The genre only picks a family (s.genre): 1 D (hip-hop, pop, dancehall, rock: short and vocal), 2 A (house, techno, trance, disco, afro
// house, amapiano: long), 3 B (drum and bass, UKG, dubstep: 16 to 32 beats), 4 C (hardstyle, hard techno, psytrance: kick swap), 0 unknown.
// The numbers below come from DJ guides and forum consensus (SCOPE.md 0.0.103), not from listening tests; they are priors that the
// measured properties of the two songs (usable intro and outro, key, tempo, singing, feel) can outweigh.
constexpr double kShortLastResort = 0.8;   // 0.0.124 (guess): an overlap of 8 beats costs this much more, so that it only wins when no 16 beat blend is reasonable (log 0.0.123: 6 of 11 short swaps had a 16 beat candidate 0.1 to 0.5 dearer)
// 0.0.124 (guess): the singing of B should begin inside the overlap, not at its very end (log 0.0.123: B came in after 90% of the overlap in half of the stem mixes)
constexpr double kVoiceLateFrom = 0.5, kVoiceLateCost = 0.5, kVoiceGate = 0.15;   // 0.0.125: was 0.6 / 0.3 / 0.3 on B's first singing
constexpr double kMinOverlapSec = 5.0, kShortOverlapCost = 1.0;   // 0.0.117 (guesses): overlaps shorter than 5 s cost up to 1.0 more
// 0.0.118 (guesses): what is thrown away of A when the change leaves it at [endBeat]: singing in the part not played, and a drop of A that is
// never heard. Only when more than 4 s of A are left unplayed. Used by blends and by exits (log 0.0.117: A cut before a drop or in the middle of a verse).
constexpr double kUnplayedSingCost = 0.8, kUnplayedDropCost = 0.6, kUnplayedDropRatio = 1.2;
// 0.0.123: the loud part of A that is not played costs per minute whatever the length of the song (log 0.0.122: 27 s and 48.7 s of a loud A were skipped at 0.2 to 0.4)
constexpr double kUnplayedLoudPerMin = 0.5, kUnplayedLoudEnergy = 0.7;
// 0.0.123: the most one song may be stretched in a blend (log 0.0.122: 12 to 13% on both songs for 128 to 197 bpm); a larger step is not made as a blend
constexpr double kMaxSongStretch = 0.08;
inline double unplayedCost(const TrackMap& a, double endBeat, bool withDrop = true) {
    const double ts = a.musicEnd() - a.timeAtBeat(endBeat);
    if (ts < 4.0) return 0.0;
    double c = 0.0;
    if (!a.energy.empty() && a.meanEnergy(endBeat, a.beatAtTime(a.musicEnd())) >= kUnplayedLoudEnergy) c += kUnplayedLoudPerMin * ts / 60.0;
    const double v = a.vocalMean(endBeat, a.beatAtTime(a.musicEnd()));
    if (v > 0.0) c += kUnplayedSingCost * std::min(1.0, v / 0.3) * std::min(1.0, ts / 20.0);
    for (const auto& sec : a.sections)
        if (withDrop && (double)sec.beat >= endBeat && sec.high && sec.ratio >= (float)kUnplayedDropRatio) { c += kUnplayedDropCost * std::min(1.0, ts / 40.0); break; }
    return c;
}
inline double lengthCost(int family, int L) {
    switch (family) {
        case 1: return L <= 8 ? 0.0 : L == 16 ? 0.05 : L == 32 ? 0.25 : 0.6;
        case 2: return L <= 8 ? 0.6 : L == 16 ? 0.15 : L == 32 ? 0.0 : 0.05;
        case 3: return L <= 8 ? 0.5 : L == 16 ? 0.0 : L == 32 ? 0.05 : 0.5;
        case 4: return L <= 8 ? 0.15 : L == 16 ? 0.0 : L == 32 ? 0.1 : 0.6;
        default: return L <= 8 ? 0.5 : L == 16 ? 0.1 : L == 32 ? 0.0 : 0.1;
    }
}
// A phrase of 8 beats does not carry a blend of 32; a blend longer than the phrase of either song crosses a phrase border
inline double phraseCost(const TrackMap& a, const TrackMap& b, int L) {
    if (!a.hasCues || !b.hasCues) return 0.0;
    double c = 0.0;
    if (L > std::min(a.phraseBeats, b.phraseBeats) && L > 16) c += 0.1;
    if (std::min(a.phraseBeats, b.phraseBeats) < 16 && L >= 32) c += 0.1;
    return c;
}
// Two grooves with a different feel (straight against shuffled or 2-step) clash on the off-beats; 0.06 of a beat is tolerated
inline double feelCost(const TrackMap& a, const TrackMap& b) {
    if (!a.hasCues || !b.hasCues) return 0.0;
    return std::min(0.6, std::max(0.0, 3.0 * (std::fabs(a.swing - b.swing) - 0.06)));
}
// A blend that is longer than the shorter of A's usable outro and B's usable intro (Mixxx AutoDJ uses that minimum as the crossfade time)
// costs 0.3 per doubling, but only when the cue fields of both songs can be trusted
inline double cueBoundCost(const TrackMap& a, const TrackMap& b, int L, int style) {
    if (style != 1 || !a.hasCues || !b.hasCues || std::min(a.cueConfidence, b.cueConfidence) < 0.5) return 0.0;
    const double lim = std::max(8.0, (double)std::min(a.usableOutroBeats, b.usableIntroBeats));
    return (double)L > lim ? 0.3 * std::log2((double)L / lim) : 0.0;
}
// Half-time (B read at half or double A's beat) is fine for a long blend and awkward for a short one
inline double halfTimeCost(double beatFactor, int L) {
    if (std::fabs(beatFactor - 1.0) < 1e-6) return 0.0;
    return L < 16 ? 0.4 : 0.05;
}
// Where the bass of A hands over to B, in beats from the start of the overlap. Family C: one hard swap on a bar line half way (the kick
// tail is the bass in those genres, two kicks together smear). Others: on the bar where the harmony of A changes most inside the middle
// half of the overlap, when A's pitch classes were measured there (chroma exists at the start and the end of a song only); -1: default.
inline double bassSwapFor(const TrackMap& a, double startA, int L, int family, int* flags) {
    if (L < 16) return -1.0;
    if (family == 4) {
        *flags |= kFlagHardSwap;
        return std::floor(L * 0.5 / 4.0) * 4.0;
    }
    double bestD = 0.25, bestAt = -1.0;
    for (int r = 4 * (int)std::ceil(L * 0.25 / 4.0); r <= L * 0.75 + 1e-9; r += 4) {
        double c0[12] = {}, c1[12] = {};
        const double t0 = a.chromaSum(startA + r - 4.0, startA + r, c0), t1 = a.chromaSum(startA + r, startA + r + 4.0, c1);
        if (t0 <= 1e-9 || t1 <= 1e-9) continue;
        double dot = 0.0, n0 = 0.0, n1 = 0.0;
        for (int k = 0; k < 12; k++) { dot += c0[k] * c1[k]; n0 += c0[k] * c0[k]; n1 += c1[k] * c1[k]; }
        if (n0 <= 1e-12 || n1 <= 1e-12) continue;
        const double d = 1.0 - dot / std::sqrt(n0 * n1);
        if (d > bestD) { bestD = d; bestAt = (double)r; }
    }
    return bestAt;
}

struct Entry {
    long beat = -1;      // phrase start in B, -1 when B has no grid
    double sec = 0.0;    // where in B it comes in
    double energy = 1.0; // loudness of the first 8 beats
    bool drop = false;
};

}  // namespace

// Why blends were turned down, per style (1 relaxed, 2 rolling, 3 double drop): for the log
struct Why {
    int tried[7] = {}, noTempo[7] = {}, noRoom[7] = {}, tooSoon[7] = {}, tooEarly[7] = {}, tooDeep[7] = {}, costlier[7] = {}, ok[7] = {};
    // 0.0.101: the cost parts of the cheapest blend of each style that got as far as being priced (also when it lost), for the log
    double bestCost[7] = {};
    char parts[7][260] = {};
    char turnedDown[320] = {};  // the best blend that lost against an exit, for the log
    // 0.0.103: why the beat loop was not made (counts per reason, over all entries of B that were looked at)
    // 0.0.115 (analysis log, remove later): the cheapest priced cost per style at 8, 16 and 32 beats (0: none), the exit cost, the loop that was found
    double byLen[7][3] = {};
    double exitCost = -1.0, loopCost = -1.0, loopPointBeat = -1.0;
    int loopCands = 0;
    int shortA[4] = {}, shortIntro[4] = {};   // 0.0.118: relaxed candidates of 8/16/32/64 beats dropped for a section of A / an intro of B that is too short
    // 0.0.129: a plan that needs stems came too close for the split and was planned again without them (headroom in s against the lead required)
    bool stemsReplanned = false;
    double stemHeadroomSec = -1.0, stemLeadRequiredSec = -1.0;
    int loopCalls = 0, loopOff = 0, loopConf = 0, loopRoom = 0, loopSoon = 0, loopTempo = 0, loopSteady = 0, loopClash = 0, loopPoor = 0, loopCostlier = 0, loopOk = 0;
};
Why* g_why = nullptr;

// The planner of 0.0.57, used for songs without sections: every phrase start is a candidate
static TransitionPlan planLegacy(const TrackMap& a, const TrackMap& b, double nowA, const PlannerSettings& s) {
    const TransitionPlan fb = fallbackPlan(a, b, nowA, 3.0);
    if (!a.valid()) return fb;

    const double endA = a.musicEnd();
    const double logLimit = std::log(1.0 + s.maxTempoChange);
    const int lengths[] = {s.lengthBeats > 0 ? s.lengthBeats : 0, 32, 16, 8};

    // ---- 1. a beat matched blend ---------------------------------------------------------------------------------
    bool found = false, legacyBlendOk = false;
    TransitionPlan best;
    double bestCost = 1e18;
    if (b.valid()) {
        // Where B can come in: the phrase starts of its first minute of music
        std::vector<long> entries;
        for (long i = 0; i < (long)b.beats.size() && entries.size() < 6; i++) {
            if (b.beats[i] < b.cueIn - 0.2 || !b.isPhraseStart(i)) continue;
            if (b.beats[i] > b.cueIn + kMaxEntrySec && !entries.empty()) break;
            entries.push_back(i);
        }
        for (int li = 0; li < 4 && !entries.empty(); li++) {
            const int L = lengths[li];
            if (L == 0) continue;
            const bool shortKind = L <= 8;
            if (s.lengthBeats == 0 && L == 32 && a.bpm < 150.0 && s.genre != 2 && s.genre != 3 && s.genre != 0) continue;  // 32 beats for fast songs and for the long families
            // Every place in A from which the overlap fits in A, from the end backwards (a bar start for the short template)
            for (long i = (long)a.beats.size() - 1; i >= 0; i--) {
                if (!(shortKind ? a.isDownbeat(i) : a.isPhraseStart(i))) continue;
                if (i + L >= (long)a.beats.size()) continue;
                const double endOfOverlap = a.beats[i + L];
                if (endOfOverlap > endA - 0.5) continue;
                if (endA - endOfOverlap > kMaxEarlyExitSec) break;
                // The ramp ends two beats before the overlap (the engine cues B there), so it starts rampBeats + 2 before it
                if (a.timeAtBeat((double)i - s.rampBeats - 2.0) < nowA + s.leadSeconds) break;  // earlier places are earlier still
                for (long startB : entries) {
                    // Tempo: B folded to the tempo of A, then the speed A needs. The ratio is measured over a whole stretch
                    // of beats (at least 16, so the overlap and a little more) in both songs: the time A takes divided by
                    // the time B takes for the same music. One beat interval is too noisy for this, a window of beats is not.
                    double bestF = 1.0, bestR = 1e9;
                    for (double f : {0.5, 1.0, 2.0}) {
                        double span = std::max<double>(L, 16.0);
                        const double roomA = (double)a.beats.size() - 1.0 - (double)i;
                        const double roomB = ((double)b.beats.size() - 1.0 - (double)startB) * f;
                        span = std::min(span, std::min(roomA, roomB));
                        if (span < (double)L) continue;
                        const double ta = a.timeAtBeat((double)i + span) - a.timeAtBeat((double)i);
                        const double tb = b.timeAtBeat((double)startB + span / f) - b.timeAtBeat((double)startB);
                        if (ta <= 0.0 || tb <= 0.0) continue;
                        const double r = ta / tb;
                        if (std::fabs(std::log(r)) < std::fabs(std::log(bestR))) {
                            bestR = r;
                            bestF = f;
                        }
                    }
                    if (bestR > 1e8 || std::fabs(std::log(bestR)) > logLimit) continue;
                    // B must have room for the overlap, measured in its own beats
                    const double lenB = (double)L / bestF;
                    if (startB + lenB + 1 >= (double)b.beats.size()) continue;
                    // A alone takes a small change; a larger one is shared, A faster and B slower or the other way round
                    double rA = bestR, rB = 1.0;
                    int flags = 0;
                    if (std::fabs(bestR - 1.0) > s.soloTempoChange) {
                        rA = std::sqrt(bestR);
                        rB = 1.0 / rA;
                        flags = kFlagSplit;
                    }
                    if (std::max(std::fabs(std::log(rA)), std::fabs(std::log(rB))) > std::log(1.0 + kMaxSongStretch)) continue;   // 0.0.123

                    // Cost of this choice: loud stretches on top of each other, clashing keys, stretching, leaving A
                    // early, skipping the start of B, and a small preference for the longer templates
                    const double eA = a.meanEnergy((double)i, (double)(i + L));
                    const double eB = b.meanEnergy((double)startB, (double)startB + lenB);
                    double ca[12] = {}, cb[12] = {};
                    a.chromaSum((double)i - 16.0, (double)(i + L), ca);
                    b.chromaSum((double)startB, (double)startB + lenB + 16.0, cb);
                    const Key ka = estimateKey(ca), kb = estimateKey(cb);
                    const double clash = keyClash(ka, kb) * std::min(ka.confidence(), kb.confidence());
                    if (longOverCalm(L, eA, eB, s)) continue;
                    // Singing on top of singing (0.0.86): the beats where both songs sing cost; A's mids go out for it
                    double both = 0.0, bothKnown = -1.0;
                    if (!a.vocal.empty() && !b.vocal.empty()) {
                        for (int k = 0; k < L; k++)
                            if (a.vocalAt((double)(i + k)) > (float)kSingingShare && b.vocalAt((double)startB + (double)k / bestF) > (float)kSingingShare) both += 1.0;
                        both /= (double)L;
                        bothKnown = both;
                        if (both > 0.0) flags |= kFlagVocalCut;
                    }
                    // 0.0.122: no loudness term (it was 0.5 * (eA + eB) and made every legacy blend cost 1.0 to 1.5 more than the planner on sections would price it)
                    const double cEnergy = energyJumpCost(eA, eB) + (s.stems ? kSingWeightStems : kSingWeightNoStems) * both;
                    const double cKey = kKeyWeight * clash * ((double)L / 16.0);
                    const double cStretch = kStretchCost * (std::fabs(std::log(rA)) + std::fabs(std::log(rB)));
                    const double cEarly = kEarlyCostPerMin * (endA - endOfOverlap) / 60.0 + unplayedCost(a, (double)(i + L));
                    const double cSkip = kSkipCostPerMin * std::max(0.0, b.beats[startB] - b.cueIn) / 60.0;
                    const double cost = cEnergy + cKey + cStretch + cEarly + cSkip + lengthCost(s.genre, L) + (L <= 8 && s.lengthBeats == 0 ? kShortLastResort : 0.0) + phraseCost(a, b, L) + feelCost(a, b) + halfTimeCost(bestF, L);
                    if (cost >= bestCost) continue;
                    bestCost = cost;
                    found = true;
                    best = TransitionPlan();
                    best.kind = shortKind ? Template::BassSwapShort : Template::LongBlend;
                    best.startBeatA = (double)i;
                    best.startBeatB = (double)startB;
                    best.lengthBeats = L;
                    best.rampBeats = s.rampBeats;
                    best.tempoRatioA = rA;
                    best.tempoRatioB = rB;
                    best.beatFactor = bestF;
                    best.flags = flags;
                    best.genre = s.genre;
                    best.bassSwapBeat = shortKind ? -1.0 : bassSwapFor(a, (double)i, L, s.genre, &best.flags);
                    best.flags = withStemMix(a, b, best, s);
                    best.energyA = eA;
                    best.energyB = eB;
                    best.vocalA = a.vocalMean((double)i, (double)(i + L));
                    best.vocalB = b.vocalMean((double)startB, (double)startB + lenB);
                    best.vocalOverlap = bothKnown;
                    best.keyClash = clash;
                    best.cost = cost;
                    best.keyA = ka.code();
                    best.keyB = kb.code();
                }
            }
        }
        legacyBlendOk = found && best.keyClash <= s.maxBlendClash && !poorBlend(best, s.stems);
        if (legacyBlendOk && best.cost <= kBlendCostCeiling) return best;   // 0.0.118: above the ceiling an exit is looked for too
    }

    // ---- 2. a clean exit on a bar line: echo, filter sweep or cut --------------------------------------------------
    std::vector<Entry> entries;
    if (b.valid()) {
        for (long j = 0; j < (long)b.beats.size() - 40; j++) {
            if (b.beats[j] < b.cueIn - 0.2 || !b.isPhraseStart(j)) continue;
            const double since = b.beats[j] - b.cueIn;
            const double after = b.meanEnergy((double)j, (double)j + 16.0), before = b.meanEnergy((double)j - 16.0, (double)j);
            const bool drop = since > 8.0 && before > 0.05 && after >= kDropRise * before && after >= kDropMin;
            if (since > kMaxDropSec || since > kMaxEntryShare * (b.musicEnd() - b.cueIn)) break;
            if (since > kMaxEntrySec && !drop) continue;
            Entry e;
            e.beat = j;
            e.sec = b.beats[j];
            e.energy = b.meanEnergy((double)j, (double)j + 8.0);
            e.drop = drop;
            entries.push_back(e);
        }
    }
    if (entries.empty()) {
        Entry e;  // B without a grid, or without a phrase start: it comes in where its music starts
        e.sec = b.cueIn;
        entries.push_back(e);
    }
    bool exitFound = false;
    TransitionPlan exitPlan;
    double exitCost = 1e18;
    for (long i = (long)a.beats.size() - 1; i >= 0; i--) {
        if (!a.isPhraseStart(i)) continue;
        const double tCut = a.beats[i];
        if (tCut > endA - 0.5) continue;
        if (endA - tCut > kMaxEarlyExitSec) break;
        const double eA = a.meanEnergy((double)i - 8.0, (double)i);
        for (int k = 0; k < 4; k++) {
            const Template kind = k == 0 ? Template::EchoOut : k == 1 ? Template::FilterOut : k == 2 ? Template::Cut : Template::FadeOut;
            const int pre = k == 0 ? kEchoPre : (k == 1 || k == 3) ? kFilterPre : 0;
            if (a.timeAtBeat((double)i - pre) < nowA + s.leadSeconds) continue;
            // A fade is for a big difference in energy between the end of A and the start of B
            if (k == 3 && std::fabs(eA - entries[0].energy) < kFadeGap) continue;
            if (k == 2 && !cutTempoOk(a, b, s)) continue;
            for (const Entry& e : entries) {
                const double jump = std::fabs(eA - e.energy);
                if (k == 3 && (jump < kFadeGap || (e.drop && !kFadeOntoDrop))) continue;
                if (k == 2 && e.drop && !kCutOntoDrop) continue;  // 0.0.89: a fade only for a clear difference at the entry itself
                // A big difference in energy is never a cut, and no echo or filter when a fade is possible
                if (jump >= kFadeGap && k == 2) continue;  // 0.0.104: echo and filter stay possible for a big gap (they cost less than the fade, see below)
                double base = k == 0 ? kEchoBase : k == 1 ? kFilterBase : k == 2 ? kCutBase : kFadeBase;
                // Rules: filtering a quiet end is hardly heard, an echo suits it; a loud end is filtered or cut. Two loud
                // songs of the same strength are cut; a cut onto a drop lands hard and is what a DJ does there
                if (k == 1) base += eA < 0.5 ? 0.15 : (eA > 0.8 ? -0.10 : 0.0);
                if (k == 2 && eA > 0.8 && e.energy > 0.8 && jump < 0.25) base -= 0.25;
                if (k == 2 && e.drop) base -= 0.15;
                const double cSkip = (e.drop ? 0.2 : kSkipCostPerMin) * std::max(0.0, e.sec - b.cueIn) / 60.0;
                // A fade comes in where B is as strong as the end of A: the gap counts double for it
                const double cost = base + (k == 3 ? 0.5 : 0.25) * jump + kEarlyCostPerMin * (endA - tCut) / 60.0 + unplayedCost(a, (double)i) + cSkip + (k == 3 ? kFadePenalty : 0.0);
                if (cost >= exitCost) continue;
                exitCost = cost;
                exitFound = true;
                exitPlan = TransitionPlan();
                exitPlan.kind = kind;
                exitPlan.startBeatA = (double)i;
                exitPlan.startBeatB = (double)e.beat;
                exitPlan.entrySecB = e.sec;
                exitPlan.flags = e.drop ? kFlagDrop : 0;
                exitPlan.energyA = eA;
                exitPlan.energyB = e.energy;
                exitPlan.cost = cost;
            }
        }
    }
    if (exitFound) {
        const bool blendWins = legacyBlendOk && best.cost <= exitCost;
        if (!blendWins && found && g_why) {
            std::snprintf(g_why->turnedDown, sizeof g_why->turnedDown,
                          "legacy blend turned down by %s: %d beats, energy %.2f to %.2f, cost %.2f, key clash %.2f, both sing %.2f; exit cost %.2f",
                          rejectedBy(best, s.stems, s.maxBlendClash, true, exitCost).c_str(),
                          best.lengthBeats, best.energyA, best.energyB, best.cost, best.keyClash, best.vocalOverlap, exitCost);
        }
        if (blendWins) return best;   // 0.0.122: above the ceiling the blend gives way only to a cheaper exit (no margin; the 0.0.118 margin of 0.5 made nearly every legacy plan an exit)
        return refineExit(exitPlan, a, nowA, s);
    }
    return found ? best : fb;
}

namespace {

// ---- planning on sections ------------------------------------------------------------------------------------------------
constexpr double kTypeBaseRelaxed = 0.0, kTypeBaseRolling = 0.15, kTypeBaseDouble = 0.25;
constexpr double kSkipCostSections = 0.5;   // per minute of B that is skipped to get to a drop
constexpr double kLongMaxClash = 0.05, kLongMaxVocal = 0.1, kLongMaxTempo = 0.03;  // the conditions for a blend of 64 beats
constexpr double kTypeBaseBreakdown = 0.25;  // 0.0.101: own design, untested, so it starts as costly as a double drop
constexpr double kDoubleDropMaxTempo = 0.05;  // two loud stretches on top of each other need almost the same tempo
constexpr double kEmergency = 0.5;           // echo and filter exits are a rescue, not a habit: a blend or a cut is preferred
constexpr double kSinging = 0.12;            // share of singing in a beat from which it counts as singing
constexpr double kMinDropRatio = 1.2;        // a high section that starts this much louder counts as a drop

// The speed A needs to play B's music at B's tempo, measured over a stretch of beats (one beat interval is too noisy)
bool tempoOf(const TrackMap& a, const TrackMap& b, double startA, double startB, int L, double* ratio, double* factor) {
    double bestF = 1.0, bestR = 1e9;
    for (double f : {0.5, 1.0, 2.0}) {
        double span = std::max<double>(L, 16.0);
        const double roomA = (double)a.beats.size() - 1.0 - startA;
        const double roomB = ((double)b.beats.size() - 1.0 - startB) * f;
        span = std::min(span, std::min(roomA, roomB));
        if (span < (double)L) continue;
        const double ta = a.timeAtBeat(startA + span) - a.timeAtBeat(startA);
        const double tb = b.timeAtBeat(startB + span / f) - b.timeAtBeat(startB);
        if (ta <= 0.0 || tb <= 0.0) continue;
        const double r = ta / tb;
        if (std::fabs(std::log(r)) < std::fabs(std::log(bestR))) {
            bestR = r;
            bestF = f;
        }
    }
    if (bestR > 1e8) return false;
    *ratio = bestR;
    *factor = bestF;
    return true;
}

// 0.0.97: the key of the whole of A and of B. The key of a stretch of 8 to 32 beats is unreliable (a drum-only outro and a bare intro gave
// "clash 1.00" for C major and A minor, which are relative keys), so a clash must be confirmed by the whole songs.
// ---- 0.0.110: moments that make a change feel meant (bonus, not only penalties) -------------------------------------------------
// Until now every term of the cost was a penalty, so the planner picked the change with the least wrong in it and a build up to a drop or a
// hand-over of the voice never won against a neutral overlap. These two terms give a reward (a negative cost) for a moment a DJ plays on purpose.
constexpr double kDropLandBonus = 0.35;   // a drop of B lands where the overlap ends (A gives way, B arrives): the build up of B is the overlap
constexpr double kDropMidBonus = 0.2;     // the drops of both songs fall together in the middle of the overlap
constexpr double kHandoffBonus = 0.2;     // A's singing is over before B's singing begins, inside the overlap: the voice is handed over
constexpr double kMomentMaxClash = 0.3;   // no bonus when the keys clash more than this

// How well a drop of B falls where it should: 0 none, up to 1 for a clear drop. [at] is the beat of B where it should fall.
inline double dropFit(const TrackMap& b, double at) {
    double best = 0.0;
    for (size_t j = 1; j < b.sections.size(); j++) {
        const auto& sec = b.sections[j];
        if (!sec.high || sec.ratio < (float)kMinDropRatio) continue;
        if (std::fabs((double)sec.beat - at) > 1.0) continue;
        best = std::max(best, std::min(1.0, ((double)sec.ratio - 1.0) / 1.0));
    }
    return best;
}

// 1 when A has finished singing at least one beat before B starts, inside the overlap, 0 otherwise
inline double voiceHandoff(const TrackMap& a, const TrackMap& b, double startA, double startB, int L, double f) {
    if (a.vocal.empty() || b.vocal.empty()) return 0.0;
    int lastA = -1, firstB = -1;
    for (int i = 0; i < L; i++) {
        if (a.vocalAt(startA + i) > (float)kSinging) lastA = i;
        if (firstB < 0 && b.vocalAt(startB + (double)i / f) > (float)kSinging) firstB = i;
    }
    return lastA >= 0 && firstB >= 0 && firstB - lastA >= 2 ? 1.0 : 0.0;
}

struct WholeKeys { Key a, b; bool ok = false; };
WholeKeys g_whole;

struct BlendTry {
    int style;           // 1 relaxed, 2 rolling, 3 double drop
    double startA;       // beat in A where the overlap starts
    double startB;       // beat in B at that moment
    int L;
    double baseCost;
    double maxTempo;     // largest tempo change allowed for this style
};

bool evalBlend(const TrackMap& a, const TrackMap& b, const BlendTry& t, double nowA, const PlannerSettings& s, TransitionPlan* out) {
    const int sy = t.style >= 1 && t.style <= 6 ? t.style : 0;
    if (g_why) g_why->tried[sy]++;
    if (t.startA < 1.0 || t.startB < 0.0) { if (g_why) g_why->noRoom[sy]++; return false; }
    if (t.startA + t.L + 1 >= (double)a.beats.size()) { if (g_why) g_why->noRoom[sy]++; return false; }
    if (b.timeAtBeat(t.startB) - b.cueIn > kMaxEntryShare * (b.musicEnd() - b.cueIn)) { if (g_why) g_why->tooDeep[sy]++; return false; }
    double ratio = 1.0, f = 1.0;
    if (!tempoOf(a, b, t.startA, t.startB, t.L, &ratio, &f)) { if (g_why) g_why->noRoom[sy]++; return false; }
    if (std::fabs(std::log(ratio)) > std::log(1.0 + t.maxTempo)) { if (g_why) g_why->noTempo[sy]++; return false; }
    const double lenB = (double)t.L / f;
    if (t.startB + lenB + 1 >= (double)b.beats.size()) { if (g_why) g_why->noRoom[sy]++; return false; }
    // The ramp ends two beats before the overlap and must still lie ahead of us
    if (a.timeAtBeat(t.startA - s.rampBeats - 2.0) < nowA + s.leadSeconds) { if (g_why) g_why->tooSoon[sy]++; return false; }
    // A must not be left much before its music ends (0.0.70: a blend on a breakdown in the middle of a song left it 76 to 103 s early)
    if (a.musicEnd() - a.timeAtBeat(t.startA + t.L) > kMaxEarlyBlendSec) { if (g_why) g_why->tooEarly[sy]++; return false; }
    double rA = ratio, rB = 1.0;
    int flags = 0;
    if (std::fabs(ratio - 1.0) > s.soloTempoChange) {
        rA = std::sqrt(ratio);
        rB = 1.0 / rA;
        flags = kFlagSplit;
    }
    if (std::max(std::fabs(std::log(rA)), std::fabs(std::log(rB))) > std::log(1.0 + kMaxSongStretch)) { if (g_why) g_why->noTempo[sy]++; return false; }
    double ca[12] = {}, cb[12] = {};
    a.chromaSum(t.startA - 16.0, t.startA + t.L, ca);
    b.chromaSum(t.startB, t.startB + lenB + 16.0, cb);
    const Key ka = estimateKey(ca), kb = estimateKey(cb);
    double clash = keyClash(ka, kb) * std::min(ka.confidence(), kb.confidence());
    if (g_whole.ok) clash = std::min(clash, keyClash(g_whole.a, g_whole.b) * std::min(g_whole.a.confidence(), g_whole.b.confidence()));
    const double endA = a.musicEnd();
    const double eA = a.meanEnergy(t.startA, t.startA + t.L), eB = b.meanEnergy(t.startB, t.startB + lenB);
    if (longOverCalm(t.L, eA, eB, s)) { if (g_why) g_why->costlier[sy]++; return false; }
    const double cKey = kKeyWeight * clash * ((double)t.L / 32.0);
    const double cStretch = kStretchCost * (std::fabs(std::log(rA)) + std::fabs(std::log(rB)));
    const double cEarly = kEarlyCostPerMin * std::max(0.0, endA - a.timeAtBeat(t.startA + t.L)) / 60.0 + unplayedCost(a, t.startA + t.L, dropFit(b, t.startB + (double)t.L / f) < 0.3);   // 0.0.123: A's drop that is never heard counts unless B lands a drop of its own where the overlap ends
    const double cSkip = kSkipCostSections * std::max(0.0, b.timeAtBeat(t.startB) - b.cueIn) / 60.0;
    // Singing on top of singing: the beats of the overlap where both songs sing; a short overlap is a poor use of a long outro
    double both = 0.0, bothKnown = -1.0, vA = a.vocalMean(t.startA, t.startA + t.L), vB = b.vocalMean(t.startB, t.startB + lenB);
    if (!a.vocal.empty() && !b.vocal.empty()) {
        for (int i = 0; i < t.L; i++)
            if (a.vocalAt(t.startA + i) > (float)kSinging && b.vocalAt(t.startB + (double)i / f) > (float)kSinging) both += 1.0;
        both /= (double)t.L;
        bothKnown = both;
        if (both > 0.0) flags |= kFlagVocalCut;
    }
    // 0.0.102: 64 beats only for two songs that suit each other fully: same key class, no singing in either, nearly the same tempo.
    // Source: mixgraph.io melodic techno guide: 32 to 64 bars, "when both tracks share the same key and similar arpeggio patterns, extend the
    // blend as long as possible", pads blend more gracefully than leads or vocal hooks. 64 beats is 16 bars, shorter than that source.
    if (t.L >= 64 && (clash > kLongMaxClash || a.vocal.empty() || b.vocal.empty() || vA > kLongMaxVocal || vB > kLongMaxVocal ||
                      std::fabs(ratio - 1.0) > kLongMaxTempo || !g_whole.ok)) { if (g_why) g_why->costlier[sy]++; return false; }
    double cGenre = 0.0;
    if (s.genre == 2 && t.style != 3) cGenre -= 0.1;       // long blends are what house and techno are made of
    if (s.genre == 3 && t.style == 3) cGenre -= 0.15;      // modern bass music lives on the drops
    if (s.genre == 4 && t.style == 3) cGenre -= 0.15;      // hardstyle and hard techno live on drops too
    // 0.0.103: the length by family, and what the measured cues say (see lengthCost and the others above)
    // 0.0.117: an overlap is judged in seconds as well as in beats: 8 beats last 3 s at 155 bpm (log 0.0.115: 2.8 to 3.6 s, hardly more than a cut).
    // The cost is part of "len" in the log; the values are guesses.
    const double ovSec = a.timeAtBeat(t.startA + t.L) - a.timeAtBeat(t.startA);
    const double cShort = kShortOverlapCost * std::max(0.0, kMinOverlapSec - ovSec) / kMinOverlapSec;
    const double cLen = lengthCost(s.genre, t.L) + (t.L <= 8 && s.lengthBeats == 0 ? kShortLastResort : 0.0) + phraseCost(a, b, t.L) + cueBoundCost(a, b, t.L, t.style) + halfTimeCost(f, t.L) + cShort;
    const double cFeel = feelCost(a, b);
    // 0.0.110: rewards (see above). Only for a clean overlap: no singing on top of singing and keys that fit.
    double cMoment = 0.0, cHand = 0.0;
    if (clash <= kMomentMaxClash && both <= 0.0) {
        cMoment = -std::max(kDropLandBonus * dropFit(b, t.startB + lenB), kDropMidBonus * dropFit(b, t.startB + lenB / 2.0));
        if (t.L >= 8 && bothKnown >= 0.0) cHand = -kHandoffBonus * voiceHandoff(a, b, t.startA, t.startB, t.L, f);
    }
    // 0.0.125: when the vocals of B really come in. The vocal gate (VocalGate) keeps B's singing out until A's singing is gone (at about an eighth
    // of the overlap) and then waits for a phrase start of B (singing after a pause); without one B comes in at the very end (log 0.0.124: median
    // 97% of the overlap, 4 s without any singing). B's singing that runs through the whole overlap therefore costs as much as singing at the end.
    double cVT = 0.0;
    if (!b.vocal.empty() && t.L >= 8) {
        auto sing = [&](int i) { return b.vocalAt(t.startB + (double)i / f) > (float)kSinging; };
        const int gate = (int)std::ceil(kVoiceGate * t.L);
        int onset = -1;
        for (int i = std::max(1, gate); i < t.L && onset < 0; i++)
            if (sing(i) && !sing(i - 1)) onset = i;
        bool anySing = onset >= 0;
        for (int i = 0; i < 2 * t.L && !anySing; i++) anySing = sing(i);
        if (onset >= 0) cVT = kVoiceLateCost * std::min(1.0, std::max(0.0, ((double)onset / t.L - kVoiceLateFrom) / (1.0 - kVoiceLateFrom)));
        else if (anySing) cVT = kVoiceLateCost;   // singing through the overlap without a phrase start, or only after it: in at the end
    }
    // Cue fields that disagree with each other (low confidence): the section borders are not trusted, so section blends cost more
    const double cDoubt = (a.hasCues && a.cueConfidence < 0.35) || (b.hasCues && b.cueConfidence < 0.35) ? 0.3 : 0.0;
    const double cost = t.baseCost + cKey + cStretch + cEarly + cSkip + (s.stems ? kSingWeightStems : kSingWeightNoStems) * both + cLen + cGenre + energyJumpCost(eA, eB) + cFeel + cDoubt + cMoment + cHand + cVT;
    if (g_why) {
        double& bl = g_why->byLen[sy][t.L <= 8 ? 0 : t.L <= 16 ? 1 : 2];
        if (bl == 0.0 || cost < bl) bl = std::max(1e-6, cost);
    }
    if (g_why && (g_why->bestCost[sy] == 0.0 || cost < g_why->bestCost[sy])) {
        g_why->bestCost[sy] = std::max(1e-6, cost);
        std::snprintf(g_why->parts[sy], sizeof g_why->parts[sy], "beat %.0f, %d beats, cost %.2f = base %.2f + key %.2f + stretch %.2f + early %.2f + skip %.2f + sing %.2f + len %.2f + genre %.2f + energy %.2f (%.2f to %.2f) + feel %.2f + doubt %.2f + moment %.2f + voice %.2f",
                      t.startA, t.L, cost, t.baseCost, cKey, cStretch, cEarly, cSkip, (s.stems ? kSingWeightStems : kSingWeightNoStems) * both, cLen, cGenre, energyJumpCost(eA, eB), eA, eB, cFeel, cDoubt, cMoment, cHand + cVT);
    }
    if (out->cost > 0.0 && cost >= out->rawCost) { if (g_why) g_why->costlier[sy]++; return false; }
    if (g_why) g_why->ok[sy]++;
    TransitionPlan p;
    p.kind = t.L <= 8 ? Template::BassSwapShort : Template::LongBlend;
    p.startBeatA = t.startA;
    p.startBeatB = t.startB;
    p.lengthBeats = t.L;
    p.rampBeats = s.rampBeats;
    p.tempoRatioA = rA;
    p.tempoRatioB = rB;
    p.beatFactor = f;
    p.flags = flags;
    p.genre = s.genre;
    p.bassSwapBeat = t.L <= 8 ? -1.0 : bassSwapFor(a, t.startA, t.L, s.genre, &p.flags);
    p.flags = withStemMix(a, b, p, s);
    p.style = t.style;
    p.energyA = eA;
    p.energyB = eB;
    p.keyClash = clash;
    // 0.0.126: the timing of B's singing only decides between blends (rawCost); against an exit and the ceiling the blend counts without it
    p.cost = std::max(1e-6, cost - cVT);
    p.rawCost = cost;
    p.keyA = ka.code();
    p.keyB = kb.code();
    p.vocalA = vA;
    p.vocalB = vB;
    p.vocalOverlap = bothKnown;
    p.genre = s.genre;
    p.flags = withStemMix(a, b, p, s);
    *out = p;
    return true;
}

}  // namespace

static TransitionPlan planNoLoop(const TrackMap& a, const TrackMap& b, double nowA, const PlannerSettings& s) {
    // Songs with hardly any structure (fewer than three sections: an intro and the rest) use the phrase-based planner of 0.0.57
    if (!a.valid() || a.sections.size() < 3 || (b.valid() && b.sections.size() < 3)) return planLegacy(a, b, nowA, s);
    const TransitionPlan fb = fallbackPlan(a, b, nowA, 3.0);
    const double endA = a.musicEnd();
    const int nA = (int)a.sections.size();

    // ---- 1. a blend between sections, three kinds -----------------------------------------------------------------------
    TransitionPlan best;
    best.cost = 0.0;  // 0: nothing found yet
    bool found = false;
    g_whole = WholeKeys();
    if (b.valid() && !a.chroma.empty() && !b.chroma.empty()) {
        double ca[12] = {}, cb[12] = {};
        a.chromaSum(0.0, (double)a.beats.size(), ca);
        b.chromaSum(0.0, (double)b.beats.size(), cb);
        g_whole.a = estimateKey(ca);
        g_whole.b = estimateKey(cb);
        g_whole.ok = true;
    }
    if (b.valid()) {
        const int nB = (int)b.sections.size();
        const double entryB = (double)b.sections[0].beat;
        const double introLen = nB > 1 && !b.sections[0].high ? (double)b.sections[1].beat - entryB : 16.0;
        const int maxLens[3] = {64, 32, 32};
        for (int i = 1; i < nA; i++) {
            const bool aHigh = a.sections[(size_t)i].high;
            const double bA = (double)a.sections[(size_t)i].beat;
            const double lenA = a.sectionEndBeat(i) - bA;
            const double lenBefore = bA - (double)a.sections[(size_t)i - 1].beat;
            if (!aHigh) {
                // relaxed: A goes down (outro or breakdown), B starts at its beginning and its intro overlaps
                for (int L : {64, 32, 16, 8}) {
                    if (s.lengthBeats > 0 && L != s.lengthBeats) continue;
                    // 0.0.118: 16 beats may run on past a short last section of A (into its outro) and past a short intro of B; the cost decides
                    const int li = L == 8 ? 0 : L == 16 ? 1 : L == 32 ? 2 : 3;
                    const bool lastA = i == nA - 1;
                    if ((double)L > lenA && !(L == 16 && lastA)) { if (g_why) g_why->shortA[li]++; continue; }
                    if ((double)L > introLen && L != 16) { if (g_why) g_why->shortIntro[li]++; continue; }
                    BlendTry t{1, bA, entryB, L, kTypeBaseRelaxed, s.maxTempoChange};
                    if (evalBlend(a, b, t, nowA, s, &best)) found = true;
                }
                // 0.0.86: B may also come in at the start of a later calm section (a breakdown after its first stretch) while A goes
                // down, when that suits the energy of A better than the beginning of B (the skip cost and the depth limit still count)
                for (int j = 1; j < nB; j++) {
                    if (b.sections[(size_t)j].high) continue;
                    const double bj = (double)b.sections[(size_t)j].beat;
                    const double lenBj = b.sectionEndBeat(j) - bj;
                    for (int L : {32, 16, 8}) {
                        if (s.lengthBeats > 0 && L != s.lengthBeats) continue;
                        if ((double)L > lenA || (double)L > lenBj) continue;
                        BlendTry t{1, bA, bj, L, kTypeBaseRelaxed, s.maxTempoChange};
                        if (evalBlend(a, b, t, nowA, s, &best)) found = true;
                    }
                }
                // rolling: A leaves its high stretch as B drops; the overlap ends on that boundary
                for (int j = 1; kRollingBlend && j < nB; j++) {
                    if (!b.sections[(size_t)j].high || b.sections[(size_t)j].ratio < kMinDropRatio) continue;
                    const double bB = (double)b.sections[(size_t)j].beat;
                    const double lenBeforeB = bB - (double)b.sections[(size_t)j - 1].beat;
                    for (int L : {32, 16, 8}) {
                        if (s.lengthBeats > 0 && L != s.lengthBeats) continue;
                        if ((double)L > lenBefore || (double)L > lenBeforeB) continue;
                        for (double f : {1.0, 0.5, 2.0}) {
                            BlendTry t{2, bA - L, bB - (double)L / f, L, kTypeBaseRolling, s.maxTempoChange};
                            if (evalBlend(a, b, t, nowA, s, &best)) found = true;
                        }
                    }
                }
            } else {
                // double drop: A drops (its section starts louder) at the same moment as B
                if (a.sections[(size_t)i].ratio < kMinDropRatio) continue;
                for (int j = 1; j < nB; j++) {
                    if (!b.sections[(size_t)j].high || b.sections[(size_t)j].ratio < kMinDropRatio) continue;
                    const double bB = (double)b.sections[(size_t)j].beat;
                    const double lenBeforeB = bB - (double)b.sections[(size_t)j - 1].beat;
                    // breakdown swap (0.0.101): A is calm before this drop of its own, B drops where the overlap ends and A stops there
                    if (!a.sections[(size_t)i - 1].high && s.prevStyle != 6) {
                        for (int L : {32, 16}) {
                            if (s.lengthBeats > 0 && L != s.lengthBeats) continue;
                            if ((double)L > lenBefore || (double)L > lenBeforeB) continue;
                            BlendTry t{6, bA - L, bB - (double)L, L, kTypeBaseBreakdown, std::min(kDoubleDropMaxTempo, s.maxTempoChange)};
                            if (evalBlend(a, b, t, nowA, s, &best)) found = true;
                        }
                    }
                    if (s.prevStyle == 2 || s.prevStyle == 3) continue;  // no double drop right after a rolling or double drop
                    for (int L : {32, 16}) {
                        if (s.lengthBeats > 0 && L != s.lengthBeats) continue;
                        if ((double)L / 2 > lenBefore || (double)L / 2 > lenBeforeB || (double)L / 2 > lenA) continue;
                        for (double f : {1.0, 0.5, 2.0}) {
                            BlendTry t{3, bA - L / 2.0, bB - (double)L / 2.0 / f, L, kTypeBaseDouble, std::min(kDoubleDropMaxTempo, s.maxTempoChange)};
                            if (evalBlend(a, b, t, nowA, s, &best)) found = true;
                        }
                    }
                }
            }
        }
        // 0.0.109: A stays loud to its very end (log 0.0.108: 4 of 5 exits came from songs with 4 to 8 beats of outro, 1.26 into 0.27, 1.29 into
        // 0.15). No section boundary lies near such an end, so none of the blends above could be made. The last beats of A now overlap the start
        // of a drop of B (loud on loud, the same type as a double drop): A stops at the end of the overlap and B goes on in its drop.
        if (nA >= 2 && nB >= 2 && a.sections[(size_t)nA - 1].high && s.prevStyle != 2 && s.prevStyle != 3) {
            long last = (long)a.beats.size() - 1;
            while (last > 0 && a.beats[(size_t)last] > endA - 0.3) last--;
            for (int j = 1; j < nB; j++) {
                if (!b.sections[(size_t)j].high || b.sections[(size_t)j].ratio < kMinDropRatio) continue;
                const double bB = (double)b.sections[(size_t)j].beat;
                const double lenDropB = b.sectionEndBeat(j) - bB;
                for (int L : {16, 8}) {
                    if (s.lengthBeats > 0 && L != s.lengthBeats) continue;
                    if ((double)L > lenDropB) continue;
                    BlendTry t{3, (double)(last - L), bB, L, kTypeBaseDouble, std::min(kDoubleDropMaxTempo, s.maxTempoChange)};
                    if (evalBlend(a, b, t, nowA, s, &best)) found = true;
                }
            }
        }
        // 0.0.112: the same loud end of A, but B has no drop to land on (log 0.0.111: B's high stretch has ratio 0.98 or lies too deep).
        // The last beats of A then overlap the start of B (its intro) as a relaxed blend; the cost, the clash limit and the ceiling decide.
        if (nA >= 2 && nB >= 1 && a.sections[(size_t)nA - 1].high) {
            long last = (long)a.beats.size() - 1;
            while (last > 0 && a.beats[(size_t)last] > endA - 0.3) last--;
            // 0.0.123: B may also come in at the start of one of its loud sections (log 0.0.122: 6 of 14 exits were a loud end of A that the
            // rule "loud into calm" refused to mix into a calm intro of B; the skip cost and the depth limit still count)
            for (int j = 0; j < nB; j++) {
                if (j > 0 && !b.sections[(size_t)j].high) continue;
                const double b0 = (double)b.sections[(size_t)j].beat;
                const double lenB0 = b.sectionEndBeat(j) - b0;
                for (int L : {16, 8}) {
                    if (s.lengthBeats > 0 && L != s.lengthBeats) continue;
                    if ((double)L > lenB0 || (double)(last - L) < 0.0) continue;
                    BlendTry t{1, (double)(last - L), b0, L, kTypeBaseRelaxed, s.maxTempoChange};
                    if (evalBlend(a, b, t, nowA, s, &best)) found = true;
                }
            }
        }
        (void)maxLens;
        if (found && best.keyClash <= s.maxBlendClash && !(s.genre == 1 && best.cost > 0.35) && !poorBlend(best, s.stems) && best.cost <= kBlendCostCeiling) return best;
    }

    // 0.0.86: the phrase-based blend of 0.0.57 (step 1b) is no longer tried here when no blend fits the structure: it knows nothing
    // about singing and wins at late plans (a seek); an exit or a fade comes instead. Songs without sections still use it (above).

    // ---- 2. a clean exit on a bar line: echo, filter sweep or cut --------------------------------------------------------
    // A leaves where it goes down (the start of an outro or breakdown) or at a phrase start; B comes in at its beginning, and
    // only for a cut from a loud A onto a drop of B does B come in on the drop.
    struct Entry2 { double beat; double sec; double energy; bool drop; };
    std::vector<Entry2> entries;
    {
        Entry2 e{b.valid() && !b.sections.empty() ? (double)b.sections[0].beat : -1.0, b.cueIn, 1.0, false};
        if (e.beat >= 0.0) {
            e.sec = b.timeAtBeat(e.beat);
            e.energy = b.meanEnergy(e.beat, e.beat + 8.0);
        }
        entries.push_back(e);
        if (b.valid())
            for (size_t j = 1; j < b.sections.size(); j++) {
                const auto& sec = b.sections[j];
                const double t = b.timeAtBeat((double)sec.beat);
                if (!sec.high || sec.ratio < kDropRise || t - b.cueIn > kMaxDropSec || t - b.cueIn > kMaxEntryShare * (b.musicEnd() - b.cueIn)) continue;
                entries.push_back({(double)sec.beat, t, b.meanEnergy((double)sec.beat, (double)sec.beat + 8.0), true});
            }
    }
    bool exitFound = false;
    TransitionPlan exitPlan;
    double exitCost = 1e18;
    for (long i = (long)a.beats.size() - 1; i >= 0; i--) {
        const int si = a.sectionAt((double)i - 1.0);  // the section A leaves
        const bool boundary = si + 1 < nA && a.sections[(size_t)si + 1].beat == i;
        if (!a.isPhraseStart(i) && !boundary) continue;
        const double tCut = a.beats[(size_t)i];
        if (tCut > endA - 0.5) continue;
        if (endA - tCut > kMaxEarlyExitSec) break;
        const bool aHigh = si >= 0 && a.sections[(size_t)si].high;
        const bool goesDown = boundary && !a.sections[(size_t)si + 1].high;
        const double eA = a.meanEnergy((double)i - 8.0, (double)i);
        for (int k = 0; k < 4; k++) {
            const Template kind = k == 0 ? Template::EchoOut : k == 1 ? Template::FilterOut : k == 2 ? Template::Cut : Template::FadeOut;
            const int pre = k == 0 ? kEchoPre : (k == 1 || k == 3) ? kFilterPre : 0;
            if (a.timeAtBeat((double)i - pre) < nowA + s.leadSeconds) continue;
            // A fade is for a big difference in energy between the end of A and the start of B (entries[0])
            if (k == 3 && std::fabs(eA - entries[0].energy) < kFadeGap) continue;
            if (k == 2 && !cutTempoOk(a, b, s)) continue;
            for (const Entry2& e : entries) {
                // A cut is for a loud A onto a drop of B; echo and filter come in at the beginning or on a drop of B (0.0.89); a fade comes in at the
                // beginning or on any drop of B, whichever is as strong as the end of A
                const bool cutOk = (aHigh && e.drop) || ((s.genre == 1 || s.genre == 4) && !e.drop && e.energy >= 0.9);
                if (k == 2) {
                    if (!cutOk && b.valid() && !b.sections.empty()) continue;
                }
                // 0.0.89: a fade only where B comes in clearly softer or louder than the end of A (not on a drop of the same strength)
                if (k == 3 && (std::fabs(eA - e.energy) < kFadeGap || (e.drop && !kFadeOntoDrop))) continue;
                if (k == 2 && e.drop && !kCutOntoDrop) continue;
                // A big difference in energy is never a cut, and no echo or filter when a fade is possible
                if (std::fabs(eA - e.energy) >= kFadeGap && k == 2) continue;  // 0.0.104: echo and filter stay possible for a big gap
                double base = k == 0 ? kEchoBase : k == 1 ? kFilterBase : k == 2 ? kCutBase : kFadeBase;
                if (k == 1) base += eA < 0.5 ? 0.15 : (eA > 0.8 ? -0.10 : 0.0);
                if (k == 2) base -= 0.2;
                if (s.prevExit == (int)kind) base += 0.5;  // 0.0.101: not the same exit twice in a row
                if (k < 2) base += kEmergency;
                if (k < 2 && aHigh) base += 0.3;   // leaving in the middle of a loud stretch
                if (k == 2 && s.genre == 2) base += 1.5;  // a cut is not how house and techno change songs
                if (a.vocalMean((double)i - 2.0, (double)i) > kSinging) base += k == 2 ? 0.5 : 0.25;  // not in the middle of a word
                if (goesDown) base -= 0.2;          // the natural place: A is going down here anyway
                const double cSkip = (e.drop ? 0.2 : kSkipCostPerMin) * std::max(0.0, e.sec - b.cueIn) / 60.0;
                const double cost = base + (k == 3 ? 0.5 : 0.25) * std::fabs(eA - e.energy) + kEarlyCostPerMin * (endA - tCut) / 60.0 + unplayedCost(a, (double)i) + cSkip + (k == 3 ? kFadePenalty : 0.0);
                if (cost >= exitCost) continue;
                exitCost = cost;
                exitFound = true;
                exitPlan = TransitionPlan();
                exitPlan.kind = kind;
                exitPlan.startBeatA = (double)i;
                exitPlan.startBeatB = e.beat;
                exitPlan.entrySecB = e.sec;
                exitPlan.flags = e.drop ? kFlagDrop : 0;
                exitPlan.style = 4;
                exitPlan.vocalA = a.vocalMean((double)i - 8.0, (double)i);
                exitPlan.vocalB = b.vocalMean(e.beat, e.beat + 8.0);
                exitPlan.genre = s.genre;
                exitPlan.energyA = eA;
                exitPlan.energyB = e.energy;
                exitPlan.cost = cost;
            }
        }
    }
    const bool blendOk = found && best.keyClash <= s.maxBlendClash && !poorBlend(best, s.stems) && !(exitFound && best.cost > exitPlan.cost + kBlendExitMargin);
    if (exitFound && (!blendOk || (s.genre == 1 && exitPlan.cost < best.cost))) {
        if (found && g_why) {
            const std::string why = blendOk ? std::string("exitCheaper (genre rule: no margin)")
                                            : rejectedBy(best, s.stems, s.maxBlendClash, true, exitPlan.cost + kBlendExitMargin);
            std::snprintf(g_why->turnedDown, sizeof g_why->turnedDown,
                          "blend turned down by %s: style %d, %d beats, energy %.2f to %.2f, cost %.2f, key clash %.2f, both sing %.2f; exit cost %.2f",
                          why.c_str(), best.style, best.lengthBeats, best.energyA, best.energyB, best.cost, best.keyClash, best.vocalOverlap, exitPlan.cost);
        }
        return refineExit(exitPlan, a, nowA, s);
    }
    return found ? best : fb;
}


namespace {
std::string describeMap(const char* name, const TrackMap& m) {
    char buf[256];
    std::snprintf(buf, sizeof buf, "%s: bpm %.2f beats %zu cueIn %.1f s end %.1f s of %.1f s, chroma blocks %zu, sections", name, m.bpm,
                  m.beats.size(), m.cueIn, m.musicEnd(), m.duration, m.chroma.size() / 12);
    std::string r = buf;
    for (const auto& sec : m.sections) {
        std::snprintf(buf, sizeof buf, " [%ld %c %.2f]", sec.beat, sec.high ? 'H' : 'L', (double)sec.ratio);
        r += buf;
    }
    if (m.sections.empty()) r += " none";
    if (m.hasCues) {
        std::snprintf(buf, sizeof buf, ", cues: confidence %.2f phrase %d intro %d beats outro %d beats edge energy %.2f to %.2f swing %.2f", m.cueConfidence,
                      m.phraseBeats, m.usableIntroBeats, m.usableOutroBeats, m.energyStart, m.energyEnd, m.swing);
        r += buf;
    } else {
        r += ", no cues (older map)";
    }
    if (!m.chroma.empty()) {
        double c[12] = {};
        m.chromaSum(0.0, (double)m.beats.size(), c);
        const Key k = estimateKey(c);
        std::snprintf(buf, sizeof buf, ", key code %d confidence %.2f", k.code(), k.confidence());
        r += buf;
    }
    return r;
}
}  // namespace

// 0.0.93 beat loop. A blend of 16 beats whose first half is the last two bars of A and whose second half repeats them: for an A that
// has no outro to blend over (the planner would leave it with an exit). Only with a grid that can be trusted in both songs and
// tempos that can be matched; the loop point is the last bar line of A. The audio of the loop is made by the app (overlay on A).
constexpr int kLoopBeats = 8, kLoopOverlap = 16;
constexpr double kLoopMinConfidence = 0.5;
constexpr double kLoopSteady = 0.8;  // the energy of the repeated bars against the 16 beats before them: at least this share (a guess)
// 0.0.112 (all values are guesses): other loop points than the last bar line: at most 6 phrase starts, at least 16 beats before the last
// bar line, in the last 40% of the music; each minute of A that is skipped costs 0.4; a loop that lets B sing straight away wins over a
// blend that is up to 0.25 cheaper.
constexpr int kLoopMaxMid = 6, kLoopMinGap = 16;
constexpr double kLoopSkipPerMin = 0.4, kLoopVoiceBonus = 0.25;
// 0.0.114 (guesses): log 0.0.113 had loops that skipped 70 s of A at a cost of 2.15 and 2.30 against exits of 1.08. A loop replaces an exit only
// when it costs at most 0.3 more, and a loop point inside A skips at most 20 s of A.
constexpr double kLoopMaxSkipSec = 20.0, kLoopOverExit = 0.3;
static bool planLoop(const TrackMap& a, const TrackMap& b, double nowA, const PlannerSettings& s, TransitionPlan* out) {
    if (g_why) g_why->loopCalls++;
    if (!s.loops || s.maxTempoChange <= 0.0 || !a.valid() || !b.valid()) { if (g_why) g_why->loopOff++; return false; }
    if (a.confidence < kLoopMinConfidence || b.confidence < kLoopMinConfidence) { if (g_why) g_why->loopConf++; return false; }
    // 0.0.129: the key of a short stretch is unreliable (see g_whole), so a clash of the stretches must be confirmed by the whole songs, as in the
    // section planner. Own copy: g_whole is not filled for songs that take the legacy planner (it would still hold the pair before).
    WholeKeys whole;
    if (!a.chroma.empty() && !b.chroma.empty()) {
        double wa[12] = {}, wb[12] = {};
        a.chromaSum(0.0, (double)a.beats.size(), wa);
        b.chromaSum(0.0, (double)b.beats.size(), wb);
        whole.a = estimateKey(wa);
        whole.b = estimateKey(wb);
        whole.ok = true;
    }
    const double endA = a.musicEnd();
    long j0 = (long)a.beats.size() - 1;
    while (j0 >= 0 && (!a.isDownbeat(j0) || a.beats[(size_t)j0] > endA - 0.5)) j0--;
    // 0.0.112: the loop point is not only the last bar line: a phrase start in the last part of A can make the end as well (the overlay
    // replaces everything of A after the loop point, so the rest of A is skipped). The last bar line stays the first candidate.
    std::vector<long> js;
    if (j0 >= 0) js.push_back(j0);
    for (long q = j0 - kLoopMinGap; q > 0 && js.size() < 1 + (size_t)kLoopMaxMid; q--) {
        if (!a.isPhraseStart(q) || !a.isDownbeat(q)) continue;
        if (endA - a.beats[(size_t)q] > kLoopMaxSkipSec) break;   // 0.0.114: at most 20 s of A are skipped
        if (a.vocal.empty()) break;                                // 0.0.114: no loop inside A when its singing is not known
        js.push_back(q);
    }
    if (g_why) g_why->loopCands = (int)js.size();
    const int L = kLoopOverlap;
    const double logLimit = std::log(1.0 + s.maxTempoChange);
    bool found = false;
    double bestCost = 1e18;
    for (const long j : js) {
    const long i = j - kLoopBeats;
    if (i < 16) { if (g_why) g_why->loopRoom++; continue; }
    if (a.timeAtBeat((double)i - s.rampBeats - 2.0) < nowA + s.leadSeconds) { if (g_why) g_why->loopSoon++; continue; }
    double ca[12] = {};
    a.chromaSum((double)i - 16.0, (double)j, ca);
    const Key ka = estimateKey(ca);
    const double skippedMin = std::max(0.0, endA - a.timeAtBeat((double)j)) / 60.0;
    int entriesSeen = 0;
    for (long startB = 0; startB < (long)b.beats.size() && entriesSeen < 6; startB++) {
        if (b.beats[(size_t)startB] < b.cueIn - 0.2 || !b.isPhraseStart(startB)) continue;
        if (b.beats[(size_t)startB] > b.cueIn + kMaxEntrySec && entriesSeen > 0) break;
        entriesSeen++;
        double bestF = 1.0, bestR = 1e9;
        for (double f : {0.5, 1.0, 2.0}) {
            const double span = 16.0;
            const double ta = a.timeAtBeat((double)i + span) - a.timeAtBeat((double)i);
            const double tb = b.timeAtBeat((double)startB + span / f) - b.timeAtBeat((double)startB);
            if (ta <= 0.0 || tb <= 0.0) continue;
            const double r = ta / tb;
            if (std::fabs(std::log(r)) < std::fabs(std::log(bestR))) { bestR = r; bestF = f; }
        }
        if (bestR > 1e8 || std::fabs(std::log(bestR)) > logLimit) { if (g_why) g_why->loopTempo++; continue; }
        const double lenB = (double)L / bestF;
        if ((double)startB + lenB + 1.0 >= (double)b.beats.size()) { if (g_why) g_why->loopRoom++; continue; }
        double rA = bestR, rB = 1.0;
        int flags = kFlagLoop;
        if (std::fabs(bestR - 1.0) > s.soloTempoChange) { rA = std::sqrt(bestR); rB = 1.0 / rA; flags |= kFlagSplit; }
        if (std::max(std::fabs(std::log(rA)), std::fabs(std::log(rB))) > std::log(1.0 + kMaxSongStretch)) { if (g_why) g_why->loopTempo++; continue; }   // 0.0.123
        const double eA = a.meanEnergy((double)i, (double)j), eB = b.meanEnergy((double)startB, (double)startB + lenB);
        if (longOverCalm(L, eA, eB, s)) continue;
        // The bars that repeat must be steady: a song that is fading out or breaking down at its end sounds flat when its end repeats
        if (eA < kLoopSteady * a.meanEnergy((double)i - 16.0, (double)i)) { if (g_why) g_why->loopSteady++; continue; }
        double cb[12] = {};
        b.chromaSum((double)startB, (double)startB + lenB + 16.0, cb);
        const Key kb = estimateKey(cb);
        double clash = keyClash(ka, kb) * std::min(ka.confidence(), kb.confidence());
        if (whole.ok) clash = std::min(clash, keyClash(whole.a, whole.b) * std::min(whole.a.confidence(), whole.b.confidence()));   // 0.0.129
        if (clash > s.maxBlendClash) { if (g_why) g_why->loopClash++; continue; }
        // The loop repeats the first half of the overlap in A, so beat k of the overlap sounds beat (k mod 8) of A
        double both = 0.0, bothKnown = -1.0;
        if (!a.vocal.empty() && !b.vocal.empty()) {
            for (int k = 0; k < L; k++)
                if (a.vocalAt((double)(i + k % kLoopBeats)) > (float)kSingingShare && b.vocalAt((double)startB + (double)k / bestF) > (float)kSingingShare) both += 1.0;
            both /= (double)L;
            bothKnown = both;
            if (both > 0.0) flags |= kFlagVocalCut;
        }
        TransitionPlan p;
        p.kind = Template::LongBlend;
        p.startBeatA = (double)i;
        p.startBeatB = (double)startB;
        p.lengthBeats = L;
        p.rampBeats = s.rampBeats;
        p.tempoRatioA = rA;
        p.tempoRatioB = rB;
        p.beatFactor = bestF;
        p.flags = flags;
        p.style = 5;
        p.energyA = eA;
        p.energyB = eB;
        p.keyClash = clash;
        p.keyA = ka.code();
        p.keyB = kb.code();
        p.vocalA = a.vocalMean((double)i, (double)j);
        p.vocalB = b.vocalMean((double)startB, (double)startB + lenB);
        p.vocalOverlap = bothKnown;
        p.genre = s.genre;
        p.loopBeat = (double)j;
        p.loopBeats = kLoopBeats;
        // 0.0.105: a loop never uses stems (the loop is the overlay on A; a stem overlay replaces it, log 0.0.104: the blend was cut off at the end of A)
        if (poorBlend(p, false)) { if (g_why) g_why->loopPoor++; continue; }
        const double cost = 0.5 * (eA + eB) + energyJumpCost(eA, eB) + kSingWeightNoStems * both +
                            kKeyWeight * clash + kStretchCost * (std::fabs(std::log(rA)) + std::fabs(std::log(rB))) +
                            kSkipCostPerMin * std::max(0.0, b.beats[(size_t)startB] - b.cueIn) / 60.0 + kLoopSkipPerMin * skippedMin;
        if (cost >= bestCost) { if (g_why) g_why->loopCostlier++; continue; }
        bestCost = cost;
        if (g_why) { g_why->loopCost = cost; g_why->loopPointBeat = (double)j; }
        p.cost = std::max(1e-6, cost);
        p.bassSwapBeat = -1.0;
        *out = p;
        found = true;
        if (g_why) g_why->loopOk++;
    }
    }
    return found;
}

static TransitionPlan planOnce(const TrackMap& a, const TrackMap& b, double nowA, const PlannerSettings& s) {
    TransitionPlan p = planNoLoop(a, b, nowA, s);
    // 0.0.103: a blend that was built on section borders of a song whose cues disagree (low cueConfidence) gives way to a beat loop, which needs
    // no borders, when the loop costs at most 0.5 more
    if ((p.kind == Template::LongBlend || p.kind == Template::BassSwapShort) && p.style != 5 && a.hasCues && a.cueConfidence < 0.35) {
        TransitionPlan lp;
        if (planLoop(a, b, nowA, s, &lp) && lp.cost <= p.cost + 0.5) return lp;
    }
    // A fallback fade or an echo, filter or other effect exit is replaced by a beat loop blend when one fits. A cut (onto a drop) and a fade out
    // (big difference in energy) stay: a long blend is the wrong move there.
    if ((int)p.kind >= (int)Template::Fallback && p.kind != Template::Cut && p.kind != Template::FadeOut) {
        if (g_why) g_why->exitCost = p.cost;
        TransitionPlan lp;
        if (planLoop(a, b, nowA, s, &lp) && lp.cost <= p.cost + kLoopOverExit) return lp;
    }
    // 0.0.112: the loop makes an end of its own: it also competes with a blend when B starts singing in the overlap
    if (p.style != 5 && (p.kind == Template::LongBlend || p.kind == Template::BassSwapShort)) {
        TransitionPlan lp;
        if (planLoop(a, b, nowA, s, &lp) && lp.vocalB >= kSingingShare && lp.cost - kLoopVoiceBonus < p.cost) return lp;
    }
    return p;
}

// 0.0.99: a plan that needs stems (a stem mix, or a long blend with some singing on top of singing) is only kept when its overlap is far enough
// ahead to split the songs in time; otherwise it is planned again without stems (log 0.0.98: a stem mix 12 s ahead came too late and B started on its own)
static TransitionPlan planTransitionInner(const TrackMap& a, const TrackMap& b, double nowA, const PlannerSettings& s);
TransitionPlan planTransition(const TrackMap& a, const TrackMap& b, double nowA, const PlannerSettings& s) {
    TransitionPlan p = planTransitionInner(a, b, nowA, s);
    // 0.0.103: every overlap is level controlled by the measured correlation (the engine does the measuring)
    if (p.kind == Template::LongBlend || p.kind == Template::BassSwapShort) p.flags |= kFlagLevel;
    return p;
}

static TransitionPlan planTransitionInner(const TrackMap& a, const TrackMap& b, double nowA, const PlannerSettings& s) {
    const TransitionPlan p = planOnce(a, b, nowA, s);
    if (!s.stems || s.stemLeadSeconds <= 0.0 || !a.valid()) return p;
    const bool needs = (p.flags & kFlagStemMix) || ((p.kind == Template::LongBlend || p.kind == Template::BassSwapShort) && p.vocalOverlap > 0.05);
    if (!needs) return p;
    const double headroom = a.timeAtBeat(p.startBeatA) - nowA;
    if (headroom >= s.stemLeadSeconds) return p;
    PlannerSettings s2 = s;
    s2.stems = false;
    if (g_why) *g_why = Why();
    const TransitionPlan q = planOnce(a, b, nowA, s2);
    if (g_why) { g_why->stemsReplanned = true; g_why->stemHeadroomSec = headroom; g_why->stemLeadRequiredSec = s.stemLeadSeconds; }   // 0.0.129: after the reset above
    return q;
}


// 0.0.115: everything needed to judge afterwards whether a change was the right one (remove again when the analysis is done).
constexpr bool kAnalysisLog = true;
static std::string analysisLines(const TrackMap& a, const TrackMap& b, const TransitionPlan& p, const Why& w) {
    std::string r;
    char buf[400];
    static const char* names[7] = {"legacy", "relaxed", "rolling", "double", "-", "-", "breakdown swap"};
    r += "\nby length (cheapest priced cost at 8/16/32 beats, - none):";
    for (int i = 0; i < 7; i++) {
        if (i == 4 || i == 5) continue;
        if (w.byLen[i][0] == 0.0 && w.byLen[i][1] == 0.0 && w.byLen[i][2] == 0.0) continue;
        r += std::string(" ") + names[i] + " ";
        for (int k = 0; k < 3; k++) {
            if (w.byLen[i][k] == 0.0) std::snprintf(buf, sizeof buf, "%s-", k ? "/" : "");
            else std::snprintf(buf, sizeof buf, "%s%.2f", k ? "/" : "", w.byLen[i][k]);
            r += buf;
        }
        r += ";";
    }
    std::snprintf(buf, sizeof buf, "\nrelaxed candidates dropped for length (8/16/32/64 beats): section of A too short %d/%d/%d/%d, intro of B too short %d/%d/%d/%d",
                  w.shortA[0], w.shortA[1], w.shortA[2], w.shortA[3], w.shortIntro[0], w.shortIntro[1], w.shortIntro[2], w.shortIntro[3]);
    r += buf;
    std::snprintf(buf, sizeof buf, "\nexit cost %.2f; loop cost %.2f at beat %.0f of %d candidate points; chosen cost %.2f", w.exitCost, w.loopCost, w.loopPointBeat, w.loopCands, p.cost);
    r += buf;
    // 0.0.125: the keys behind a clash (also for exits, whose plan does not carry them): the stretch of the overlap and the whole songs
    if (a.valid() && b.valid() && !a.chroma.empty() && !b.chroma.empty()) {
        const double sa = p.startBeatA, sb = p.startBeatB;
        const int L = p.lengthBeats > 0 ? p.lengthBeats : 16;
        double ca[12] = {}, cb[12] = {}, wa[12] = {}, wb[12] = {};
        a.chromaSum(sa - 16.0, sa + L, ca);
        b.chromaSum(sb, sb + L + 16.0, cb);
        a.chromaSum(0.0, (double)a.beats.size(), wa);
        b.chromaSum(0.0, (double)b.beats.size(), wb);
        const Key ka = estimateKey(ca), kb = estimateKey(cb), kwa = estimateKey(wa), kwb = estimateKey(wb);
        std::snprintf(buf, sizeof buf, "\nkeys (code, confidence): overlap A %d %.2f, B %d %.2f, clash %.2f; whole songs A %d %.2f, B %d %.2f, clash %.2f",
                      ka.code(), ka.confidence(), kb.code(), kb.confidence(), keyClash(ka, kb) * std::min(ka.confidence(), kb.confidence()),
                      kwa.code(), kwa.confidence(), kwb.code(), kwb.confidence(), keyClash(kwa, kwb) * std::min(kwa.confidence(), kwb.confidence()));
        r += buf;
    }
    if (!a.valid() || !b.valid() || p.lengthBeats <= 0) return r;
    const double f = p.beatFactor > 0.0 ? p.beatFactor : 1.0;
    const double endBeatA = p.startBeatA + p.lengthBeats, endBeatB = p.startBeatB + p.lengthBeats / f;
    const double tA0 = a.timeAtBeat(p.startBeatA), tA1 = a.timeAtBeat(endBeatA), tB0 = b.timeAtBeat(p.startBeatB), tB1 = b.timeAtBeat(endBeatB);
    const double bLen = std::max(1.0, b.musicEnd() - b.cueIn);
    std::snprintf(buf, sizeof buf,
                  "\ntiming: A from %.1f s to %.1f s (%.1f s of %.1f s music, %.1f s of A never played); B from %.1f s (%.0f%% into B) for %.1f s; local bpm A %.1f B %.1f (song bpm %.1f and %.1f); ratios A %.3f B %.3f, beat factor %.1f",
                  tA0, tA1, tA1 - tA0, a.musicEnd(), std::max(0.0, a.musicEnd() - tA1), tB0, 100.0 * (tB0 - b.cueIn) / bLen, tB1 - tB0,
                  a.bpmAtBeat(p.startBeatA), b.bpmAtBeat(p.startBeatB), a.bpm, b.bpm, p.tempoRatioA, p.tempoRatioB, f);
    r += buf;
    {
        // 0.0.124: when the singing of A ends and when the singing of B begins inside the overlap (beats from its start, - none)
        int lastA = -1, firstB = -1;
        for (int i = 0; i < p.lengthBeats; i++) {
            if (!a.vocal.empty() && a.vocalAt(p.startBeatA + i) > (float)kSinging) lastA = i;
            if (!b.vocal.empty() && firstB < 0 && b.vocalAt(p.startBeatB + (double)i / f) > (float)kSinging) firstB = i;
        }
        char sa[24], sb[24];
        if (lastA >= 0) std::snprintf(sa, sizeof sa, "beat %d", lastA); else std::snprintf(sa, sizeof sa, "-");
        if (firstB >= 0) std::snprintf(sb, sizeof sb, "beat %d (%.0f%%)", firstB, 100.0 * firstB / p.lengthBeats); else std::snprintf(sb, sizeof sb, "-");
        std::snprintf(buf, sizeof buf, "\nsinging in the overlap of %d beats: A last sings at %s, B first sings at %s", p.lengthBeats, sa, sb);
        r += buf;
    }
    if (p.flags & kFlagLoop) {
        std::snprintf(buf, sizeof buf, "\nloop: beats %.0f to %.0f of A repeat (energy %.2f), A at %.1f s", p.loopBeat - p.loopBeats, p.loopBeat, a.meanEnergy(p.loopBeat - p.loopBeats, p.loopBeat), a.timeAtBeat(p.loopBeat));
        r += buf;
    }
    // Curves in steps of 4 beats: A from 16 beats before the overlap to 16 after its end, B from its start to 16 beats after the overlap
    auto curve = [&](const TrackMap& m, double from, double to, bool vocal) {
        std::string c;
        for (double x = from; x < to - 0.01; x += 4.0) {
            const double v = vocal ? (double)m.vocalMean(x, x + 4.0) : m.meanEnergy(x, x + 4.0);
            std::snprintf(buf, sizeof buf, "%s%.2f", c.empty() ? "" : " ", v);
            c += buf;
        }
        return c;
    };
    r += "\nenergy A (4-beat steps, 16 before to 16 after the overlap): " + curve(a, p.startBeatA - 16.0, endBeatA + 16.0, false);
    r += "\nenergy B (from its start of the overlap to 16 beats after it): " + curve(b, p.startBeatB, endBeatB + 16.0, false);
    if (!a.vocal.empty()) r += "\nsinging A: " + curve(a, p.startBeatA - 16.0, endBeatA + 16.0, true);
    else r += "\nsinging A: not known";
    if (!b.vocal.empty()) r += "\nsinging B: " + curve(b, p.startBeatB, endBeatB + 16.0, true);
    else r += "\nsinging B: not known";
    return r;
}

std::string explainPlan(const TrackMap& a, const TrackMap& b, double nowA, const PlannerSettings& s) {
    Why w;
    g_why = &w;
    const TransitionPlan p = planTransition(a, b, nowA, s);
    g_why = nullptr;
    std::string r = describeMap("A", a) + "\n" + describeMap("B", b) + "\n";
    static const char* names[7] = {"legacy", "relaxed", "rolling", "double", "-", "-", "breakdown swap"};
    char buf[256];
    for (int i = 0; i < 7; i++) {
        if (i == 4 || i == 5) continue;
        std::snprintf(buf, sizeof buf, "%s: tried %d, no room %d, tempo %d, too soon %d, too early %d, too deep %d, costlier %d, accepted %d\n", names[i], w.tried[i],
                      w.noRoom[i], w.noTempo[i], w.tooSoon[i], w.tooEarly[i], w.tooDeep[i], w.costlier[i], w.ok[i]);
        r += buf;
        if (w.parts[i][0]) r += std::string("  cheapest priced: ") + w.parts[i] + "\n";
    }
    std::snprintf(buf, sizeof buf, "beat loop: tried %d, off %d, grid not sure %d, no room %d, too soon %d, tempo %d, end not steady %d, key clash %d, singing %d, costlier %d, accepted %d\n",
                  w.loopCalls, w.loopOff, w.loopConf, w.loopRoom, w.loopSoon, w.loopTempo, w.loopSteady, w.loopClash, w.loopPoor, w.loopCostlier, w.loopOk);
    r += buf;
    std::snprintf(buf, sizeof buf, "chosen kind %d style %d length %d bass swap %.0f family %d", (int)p.kind, p.style, p.lengthBeats, p.bassSwapBeat, p.genre);
    r += buf;
    if (w.turnedDown[0]) r += std::string("\n") + w.turnedDown;
    if (w.stemsReplanned) {   // 0.0.129
        std::snprintf(buf, sizeof buf, "\nstems: planned again without stems, headroom %.1f s is less than the %.0f s required", w.stemHeadroomSec, w.stemLeadRequiredSec);
        r += buf;
    }
    if (kAnalysisLog) r += analysisLines(a, b, p, w);
    return r;
}

}  // namespace nooklin::mix
