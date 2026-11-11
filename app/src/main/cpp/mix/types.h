#pragma once
// Shared types of the Auto Mix engine. Platform independent: no Android headers here.
#include <algorithm>
#include <cmath>
#include <cstdint>
#include <memory>
#include <vector>

namespace nooklin::mix {

/**
 * What the analysis knows about one song. Times are seconds in the song.
 * [beats] holds every beat of the song (a grid per song that follows small tempo drift), [downbeatPhase] says which
 * beats start a bar (beats[i] starts a bar when i % 4 == downbeatPhase), [phrasePhase] which bar (0..3, counted from
 * the first bar) starts a phrase of 16 beats.
 */
struct TrackMap {
    double bpm = 0.0;         // 0: no reliable beat
    double confidence = 0.0;  // 0..1
    bool constantTempo = false;  // the grid is one straight line (steady tempo); not carried to the JNI maps
    std::vector<double> beats;
    int downbeatPhase = 0;
    int phrasePhase = 0;
    double cueIn = 0.0;   // where the music starts
    double cueOut = 0.0;  // where the music ends (0: not known, use duration)
    double duration = 0.0;

    // What the song sounds like along the grid (empty for a song that was not analysed with them)
    std::vector<float> energy;  // per beat: loudness relative to the median of the music (the median is 1)
    std::vector<float> chroma;  // per block of 4 beats (block k = beats 4k..4k+3): 12 pitch classes, from C; zero where not measured

    /**
     * Sections of the music, found from the way the sound changes (see the analyser): each starts at a beat on a bar line
     * and runs to the next one. [high] is true for a stretch that is louder and busier than the song is on average, false for
     * a quieter one (intro, breakdown, outro). [ratio] is the loudness of the 16 beats after the start over the 16 before it:
     * a high section that starts with a ratio of 1.4 or more begins with a drop. Empty when no sections were found.
     */
    struct Section {
        long beat = 0;
        bool high = false;
        float ratio = 1.0f;
    };
    std::vector<Section> sections;
    double loudness = 0.0;      // level of the music in dB (mean RMS between cueIn and cueOut), 0 when unknown
    // Evidence for the tempo: pairs (bpm, share of the strong onsets the grid at that tempo covers). The first pair is the grid
    // itself, the others the strongest candidates of the tempo search; empty when not known. -1 as coverage: not measurable.
    std::vector<float> tempoEvidence;
    std::vector<float> vocal;   // per beat: share of the sound that is singing, 0 to 1; empty when unknown, -1 for beats not measured
    float vocalAt(double beat) const {
        const long i = (long)std::floor(beat);
        if (i < 0 || i >= (long)vocal.size()) return -1.0f;
        return vocal[(size_t)i];
    }
    /** Mean singing share over the beats [from, to) that were measured; -1 when none were. */
    double vocalMean(double from, double to) const {
        double sum = 0.0;
        long n = 0;
        for (long i = std::max(0L, (long)std::floor(from)); i < std::min((long)vocal.size(), (long)std::ceil(to)); i++)
            if (vocal[(size_t)i] >= 0.0f) { sum += vocal[(size_t)i]; n++; }
        return n > 0 ? sum / (double)n : -1.0;
    }

    // 0.0.103: where the music can be mixed in and out (beats). Measured from the energy curve and the sections; [cueConfidence]
    // (0..1) says how much the two ways agree, a low value means the planner should not trust the section borders.
    double cueConfidence = 0.0;
    int phraseBeats = 16;          // 8, 16 or 32: the length of a phrase in this song
    double introEnd = 0.0;         // beat where the intro ends (the music is full from here)
    double outroStart = 0.0;       // beat where the outro starts (the music thins out from here)
    int usableIntroBeats = 0;      // beats between the first beat of the music and introEnd (0: no usable intro, the song starts at full strength)
    int usableOutroBeats = 0;      // beats between outroStart and the last beat of the music
    double energyStart = -1.0;     // mean relative energy of the first 16 beats of the music (-1 unknown)
    double energyEnd = -1.0;       // same for the last 16 beats
    double swing = 0.5;            // position of the off-beat eighth between two beats: 0.5 straight, about 0.6 to 0.67 swung or shuffled
    bool hasCues = false;          // the cue fields above were measured (false for a map made before 0.0.103)

    bool valid() const { return bpm > 0.0 && beats.size() >= 16; }

    /** Index of the section that beat [b] lies in, -1 when before the first one or when there are none. */
    int sectionAt(double b) const {
        int idx = -1;
        for (size_t i = 0; i < sections.size(); i++) {
            if ((double)sections[i].beat <= b) idx = (int)i;
            else break;
        }
        return idx;
    }
    /** First beat after section [i]: the start of the next one, or the beat at the end of the music. */
    double sectionEndBeat(int i) const {
        if (i + 1 < (int)sections.size()) return (double)sections[(size_t)i + 1].beat;
        return std::min((double)beats.size() - 1.0, beatAtTime(musicEnd()));
    }

    /** Mean relative loudness over the beats [from, to); 1 when there is no measurement. */
    double meanEnergy(double from, double to) const {
        if (energy.empty()) return 1.0;
        const long a = std::max(0L, (long)std::floor(from)), b = std::min((long)energy.size(), (long)std::ceil(to));
        if (b <= a) return 1.0;
        double sum = 0.0;
        for (long i = a; i < b; i++) sum += energy[(size_t)i];
        return sum / (double)(b - a);
    }

    /** Adds the pitch class energy of the beats [from, to) to [out] (12 values); returns how much was found. */
    double chromaSum(double from, double to, double* out) const {
        double total = 0.0;
        const long blocks = (long)(chroma.size() / 12);
        for (long k = std::max(0L, (long)std::floor(from / 4.0)); k < blocks && (double)k * 4.0 < to; k++) {
            const double lo = std::max(from, (double)k * 4.0), hi = std::min(to, (double)k * 4.0 + 4.0);
            const double w = std::max(0.0, hi - lo) / 4.0;
            for (int p = 0; p < 12; p++) {
                const double v = (double)chroma[(size_t)k * 12 + (size_t)p] * w;
                out[p] += v;
                total += v;
            }
        }
        return total;
    }

    /** Fractional beat number at time [t] (extrapolates with the first or last interval). */
    double beatAtTime(double t) const {
        const size_t n = beats.size();
        if (n < 2) return 0.0;
        if (t <= beats[0]) return (t - beats[0]) / (beats[1] - beats[0]);
        if (t >= beats[n - 1]) return (double)(n - 1) + (t - beats[n - 1]) / (beats[n - 1] - beats[n - 2]);
        size_t hi = std::upper_bound(beats.begin(), beats.end(), t) - beats.begin();
        size_t lo = hi - 1;
        return (double)lo + (t - beats[lo]) / (beats[hi] - beats[lo]);
    }

    /** Time of fractional beat number [b]. */
    double timeAtBeat(double b) const {
        const size_t n = beats.size();
        if (n < 2) return 0.0;
        if (b <= 0.0) return beats[0] + b * (beats[1] - beats[0]);
        if (b >= (double)(n - 1)) return beats[n - 1] + (b - (double)(n - 1)) * (beats[n - 1] - beats[n - 2]);
        size_t lo = (size_t)std::floor(b);
        return beats[lo] + (b - (double)lo) * (beats[lo + 1] - beats[lo]);
    }

    bool isDownbeat(long i) const { return ((i - downbeatPhase) % 4 + 4) % 4 == 0; }

    /** True when beat [i] starts a phrase of 16 beats. */
    bool isPhraseStart(long i) const { return ((i - downbeatPhase) % 16 + 16) % 16 == phrasePhase * 4; }

    /** The tempo around beat [i] (beats per minute). */
    double bpmAtBeat(double b) const {
        const double dt = timeAtBeat(b + 0.5) - timeAtBeat(b - 0.5);
        return dt > 0.0 ? 60.0 / dt : bpm;
    }

    double musicEnd() const { return cueOut > 0.0 ? cueOut : duration; }
};

/** A source of stereo audio at the engine sample rate. Implemented by the Android decoder and by tests. */
class PcmSource {
public:
    virtual ~PcmSource() = default;
    /** Total length in frames. */
    virtual int64_t frames() const = 0;
    /** Copies [n] frames starting at frame [at] into interleaved [out]; frames outside the audio (or not yet decoded)
     *  are returned as silence. Must not block or allocate (it is called from the audio thread). */
    virtual void read(int64_t at, float* out, int n) = 0;
    /** How many frames from the start are available (decoded). Fully decoded sources return frames(). */
    virtual int64_t available() const { return frames(); }
    /** True when a stretch of the song has been replaced by other audio (stems). */
    virtual bool hasOverlay() const { return false; }
};

enum class Template : int {
    LongBlend = 0,      // 16 or 32 beats, melody swap, bass swap (the tempo of A, or of both songs, is matched)
    BassSwapShort = 1,  // 8 beats, kick swap on a bar line
    Fallback = 2,       // no beat match and no bar line: a short clean equal-power fade, no stretch
    EchoOut = 3,        // A goes into an echo two beats before a bar line and stops there; B comes in on the bar line
    FilterOut = 4,      // A is filtered down over four beats and stops on a bar line; B comes in there
    Cut = 5,            // A stops on a bar line and B comes in there, at full strength (also onto the drop of B)
    ReverbOut = 6,      // A goes into a long reverb two beats before a bar line and stops there; the reverb rings on under B
    BrakeOut = 7,       // the record slows down to a stop over two beats (the pitch falls); B comes in on the bar line
    SpinOut = 8,        // A is stopped and pulled backwards over two beats; B comes in on the bar line
    RollOut = 9,        // the last beat of A is repeated in shorter and shorter pieces; B comes in on the bar line
    FadeOut = 10        // A fades out over four beats; B comes in on the bar line with a short fade-in (20 ms onto a drop, half a beat otherwise); for a big difference in energy
};

/**
 * One planned change from A to B, in beats of each song. The overlap starts at beat [startBeatA] of A (a downbeat on a
 * phrase start) and at beat [startBeatB] of B (a downbeat). A is brought to the tempo of B in the [rampBeats] before
 * the overlap, so B plays at its own tempo.
 */
constexpr int kFlagDrop = 1;
constexpr int kFlagSplit = 2;
constexpr int kFlagVocalCut = 4;  // singing on both sides: A's mids go out at the start of the overlap
constexpr int kFlagRiser = 8;     // exits: a rising noise over the last four beats of A
constexpr int kFlagImpact = 16;   // exits: a low hit on the first beat of B
constexpr int kFlagStemMix = 32;  // long blend: the songs are split in drums, bass, melody and singing, each with its own volume curve (overlay); the engine keeps EQ flat
constexpr int kFlagLoop = 128;     // long blend in which the last bars of A repeat (the loop is an overlay on A that runs past its end)
constexpr int kFlagStemLive = 64; // engine only: kFlagStemMix and both songs really have their overlay
constexpr int kFlagHardSwap = 256; // long blend: no gradual bass swap, A's low end is cut on one beat (kick based genres such as hardstyle)
constexpr int kFlagVoiceLive = 1024; // engine only: singing on both sides and both songs have their overlay (0.0.110): the singing is handled by the overlays, A's mids are not cut for it
constexpr int kFlagLevel = 512;    // the engine regulates the level of the overlap by the measured correlation (0.0.103)

struct TransitionPlan {
    Template kind = Template::Fallback;
    double startBeatA = 0.0;
    double startBeatB = 0.0;
    int lengthBeats = 16;
    int rampBeats = 8;
    double tempoRatioA = 1.0;  // speed of A during the overlap, A is faster when > 1
    double tempoRatioB = 1.0;  // speed of B during the overlap (1 unless the tempo change is shared by both songs); after
                               // the overlap B glides back to its own tempo over [glideBeats] beats
    int glideBeats = 16;
    double beatFactor = 1.0;   // B tempo folded to A (0.5, 1 or 2): one beat of A is 1/beatFactor beats of B
    // Fallback only: where in A (seconds) the fade starts, where in B it starts, and how long it lasts
    double fallbackStartA = 0.0;
    double fallbackStartB = 0.0;
    double fallbackSeconds = 3.0;
    // EchoOut, FilterOut and Cut: startBeatA is the bar line where A stops; B starts there at [entrySecB] seconds
    double entrySecB = 0.0;
    int flags = 0;
    int style = 0;  // how it was planned: 0 no sections, 1 relaxed (A goes down, B begins), 2 rolling (B drops as A goes down),
                    // 3 double drop, 4 exit (echo, filter or cut)  // kFlagDrop: B comes in on a drop; kFlagSplit: the tempo change is shared by both songs
    // How the planner chose (for the log): relative loudness of the two overlapped stretches, how much the keys clash
    // (0..1, already weighted by how sure the keys are), the keys (root + 12 when minor, -1 unknown) and the cost
    double energyA = 0.0, energyB = 0.0, keyClash = 0.0, cost = 0.0;
    double rawCost = 0.0;                 // 0.0.118: the cost before the floor of 1e-6, to tell apart plans that tie at the floor
    double vocalA = -1.0, vocalB = -1.0;  // singing share over the overlapped stretches (-1 unknown)
    double vocalOverlap = -1.0;           // share of the overlapped beats where both songs sing (-1 unknown; costs 4 times this)
    int genre = 0;                        // the genre class the plan was made for
    int keyA = -1, keyB = -1;
    // kFlagLoop: from beat [loopBeat] of A the [loopBeats] beats before it repeat until the overlap is over
    double loopBeat = 0.0;
    int loopBeats = 0;
    // 0.0.103: beat of the overlap (counted from startBeatA) where the bass of A hands over to B; -1: the engine's default (half way, on a bar)
    double bassSwapBeat = -1.0;
};

}  // namespace nooklin::mix
