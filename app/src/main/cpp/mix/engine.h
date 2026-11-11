#pragma once
// The mix engine: two decks, one clock (the output frame counter), a sequencer that works in beats.
//
// Threading: [render] runs on the audio thread and never allocates, locks or blocks. Everything else is called from
// one control thread. Songs and plans cross over through atomics (see Slot::state and the command word).
#include <atomic>
#include <memory>

#include "deck.h"
#include "dsp.h"
#include "types.h"

namespace nooklin::mix {

class Engine {
public:
    explicit Engine(int sampleRate = 44100);

    // ---- control thread -------------------------------------------------------------------------------------------
    /** Starts [src] now at [startSec]; whatever played is cut. Returns false when a command is still pending. */
    bool play(std::shared_ptr<PcmSource> src, const TrackMap& map, double startSec);
    /** Makes the next song ready. Returns false while the previous next song has not been released yet. */
    bool loadNext(std::shared_ptr<PcmSource> src, const TrackMap& map);
    /** The plan for the change to the song given to [loadNext]. Returns false when a command is still pending. */
    bool setPlan(const TransitionPlan& plan);
    bool clearPlan();
    /** Drops the song given to [loadNext] (and its plan). Follow with [reclaim] once the audio thread has taken it. */
    bool dropNext();
    /** Stops everything that plays. */
    bool stop();
    void setSpeed(double speed) { speedWanted.store(speed, std::memory_order_relaxed); }
    /** Master gain (replay gain, volume limit), smoothed over a few milliseconds. */
    void setGain(double g) { gainWanted.store(g, std::memory_order_relaxed); }
    /** Pause and resume with a short fade; while paused no command is lost, they are applied at once. */
    void setPaused(bool p) { pausedWanted.store(p, std::memory_order_relaxed); }
    /** True when no command is waiting for the audio thread. */
    bool idleCommands() const { return cmd.load(std::memory_order_acquire) == kNone; }
    bool pausedSilent() const { return pausedWanted.load() && pauseGain.load() <= 0.0f; }
    /**
     * Gives the song that plays now its beat grid (analysis finished after it started). [epoch] is the value of
     * telemetry.epoch the grid belongs to; when the song has changed since, the grid is dropped.
     */
    bool setCurrentMap(const TrackMap& map, int epoch);
    /** Releases the song that has been played out. Call regularly (not from the audio thread). */
    void reclaim();
    /** True when the next-song slot is free for [loadNext]. */
    bool nextSlotFree() const { return slots[1 - curIdx.load(std::memory_order_acquire)].state.load() == kEmpty; }

    // ---- audio thread ---------------------------------------------------------------------------------------------
    /** Fills [frames] interleaved stereo frames. */
    void render(float* out, int frames);

    // ---- telemetry (any thread) -----------------------------------------------------------------------------------
    enum State : int { kIdle = 0, kPlaying = 1, kRamp = 2, kArmed = 3, kBlend = 4, kFade = 5, kOutro = 6 };
    struct Telemetry {
        // Seqlock: the audio thread makes it odd while it writes, readers retry until they see the same even value twice
        std::atomic<unsigned> seq{0};
        void begin() { seq.fetch_add(1, std::memory_order_acq_rel); }
        void end() { seq.fetch_add(1, std::memory_order_acq_rel); }
        std::atomic<int> state{kIdle};
        std::atomic<double> positionSec{0.0};     // position in the current song (the one that leaves last)
        std::atomic<double> bPositionSec{0.0};    // position in the song that comes in (0 while it does not run)
        std::atomic<double> phaseErrorMs{0.0};    // beat phase between the decks while both run
        std::atomic<double> blendBeats{0.0};      // beats into the overlap
        std::atomic<int> changes{0};              // songs handed over so far
        std::atomic<int> ended{0};                // 1 when the last song ran out
        std::atomic<int> epoch{0};                // changes whenever another song becomes the current one
    } telemetry;

private:
    enum SlotState : int { kEmpty = 0, kReady = 1, kActive = 2, kRetired = 3 };
    enum Cmd : int { kNone = 0, kCmdPlan = 1, kCmdClear = 2, kCmdPlay = 3, kCmdDrop = 4, kCmdStop = 5 };

    struct Slot {
        Deck deck;
        std::shared_ptr<PcmSource> src;
        TrackMap map;
        double trim = 1.0;  // level correction so that songs are heard equally loud
        std::atomic<int> state{kEmpty};
    };

    void applyCommand();
    void step(float* out, int n);
    void handoff();
    void silence(float* out, int n) { std::fill(out, out + 2 * n, 0.0f); }

    int sr;
    Slot slots[2];
    ThreeBandEq eq[2];
    std::atomic<int> curIdx{0};
    std::atomic<int> cmd{kNone};
    TransitionPlan cmdPlan;
    double cmdStartSec = 0.0;
    std::atomic<double> speedWanted{1.0};
    std::atomic<double> gainWanted{1.0};
    std::atomic<bool> pausedWanted{false};
    std::atomic<float> pauseGain{1.0f};
    std::atomic<TrackMap*> pendingMap{nullptr};  // handed to the audio thread
    std::atomic<TrackMap*> retiredMap{nullptr};  // handed back, deleted on the control thread
    int pendingEpoch = 0;
    float gainNow = 1.0f;

    // audio thread state
    State st = kIdle;
    TransitionPlan plan;
    bool planActive = false;
    bool bRunning = false;
    // 0.0.107: last mix factors of B, for measuring only (read by host tests)
  public:
    struct Trace { double gainB = 0, trimB = 1, bal = 1, lv = 1, bassB = 0; } trace;
  private:
    BlendLeveler level;      // 0.0.103: level control of the overlap by the measured correlation
    bool voiceLive = false;  // 0.0.110: a plan with singing on both sides whose two songs have their overlay (the singing is taken apart there)
    bool stemLive = false;   // the blend in progress is a stem mix with both overlays in place
    double trim = 0.0;       // speed correction of A that closes the beat gap to B (relative, 0.01 = 1%)
    double trimI = 0.0;      // its slow part (integral), which absorbs a small error in the planned tempo ratio
    int64_t fadeFrames = 0, fadeDone = 0;
    // exit techniques and the tempo glide after a shared tempo change
    Echo echo;
    SweepFilter sweep;
    Reverb reverb;
    RingFx ringFx;
    Riser riser;
    Impact impact;
    bool echoOn = false;
    bool reverbOn = false;
    int64_t reverbLeft = 0;       // frames the reverb keeps ringing after the last song went in
    int64_t echoLeft = 0;         // frames the echo keeps ringing after the last song went in
    int entryLeft = 0, entryTotal = 1;  // short fade-in of a song that comes in on a cut
    double glideFrom = 1.0, glideT = 0.0, glideTotal = 0.0;  // current song plays at speed * glide, which returns to 1
    bool gliding = false;
    float bufA[2 * Deck::kMaxBlock], bufB[2 * Deck::kMaxBlock];
    static constexpr int kPreBeats = 2;
};

}  // namespace nooklin::mix
