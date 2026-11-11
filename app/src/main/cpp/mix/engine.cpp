#include "engine.h"

#include <algorithm>
#include <cmath>

namespace nooklin::mix {
namespace {

constexpr double kHalfPi = 1.57079632679489661923;

struct Automation {
    double gainA = 1.0, gainB = 0.0;
    double eqA[3] = {1, 1, 1};
    double eqB[3] = {0, 0, 1};
};

// p is the position in the overlap in beats (negative before it starts), L its length in beats.
Automation automate(Template kind, double p, int L, int flags, double bassSwapBeat = -1.0) {
    Automation a;
    if (p < 0.0) return a;  // B runs silent with only its highs open
    if (kind == Template::LongBlend && (flags & kFlagStemLive)) {
        // Stem mix: the volume of drums, bass, melody and singing of both songs is in the overlays, so the engine only
        // brings B in (a short fade against clicks) and takes A away at the very end
        a.gainB = smoothstep(p, 0.0, 0.5);
        a.eqA[0] = a.eqA[1] = a.eqA[2] = 1.0;
        a.eqB[0] = a.eqB[1] = a.eqB[2] = 1.0;
        a.gainA = std::cos(kHalfPi * smoothstep(p, L - 1.0, (double)L));
    } else if (kind == Template::LongBlend) {
        // Swaps fall on bar lines (a bar is 4 beats): the melody (mids) first, then the bass
        // 0.0.90: B was heard with only its highs for a quarter of the blend (log 0.0.89: "B comes in at volume too late"). The melody now
        // swaps after one bar, or at once when A's mids go out for the singing (then nothing is left of the melody otherwise), and the bass at
        // three eighths instead of half. 0.0.101: back to half (one swap in the middle, on a bar line).
        const bool voiceLive = (flags & kFlagVoiceLive) != 0;  // 0.0.110: the overlays take the singing apart: A's mids stay until the melody swap
        const bool vocalCut = (flags & kFlagVocalCut) != 0 && !voiceLive;
        const double midIn = vocalCut ? 0.0 : 4.0;
        const double midLen = vocalCut ? 2.0 : 1.0;
        // 0.0.103: the planner may put the bass swap on the bar where A's harmony changes (bassSwapBeat), and kick based genres swap hard:
        // one beat of the low end of A is gone and B's is there, over a quarter of a beat (no two kicks smeared together)
        const bool hard = (flags & kFlagHardSwap) != 0;
        double bassSwap = std::max(8.0, std::round(L * 0.5 / 4.0) * 4.0);
        if (bassSwapBeat >= 4.0 && bassSwapBeat <= L - 4.0) bassSwap = bassSwapBeat;
        const double sw = hard ? 0.25 : 1.0;
        a.gainB = smoothstep(p, 0.0, 2.0);  // 0.0.107: full at once (was 0.85 until the bass swap); the balance counts the EQ now
        a.eqB[0] = smoothstep(p, bassSwap, bassSwap + sw);
        a.eqB[1] = smoothstep(p, midIn, midIn + midLen);
        a.eqB[2] = 1.0;
        a.eqA[0] = 1.0 - smoothstep(p, bassSwap, bassSwap + sw);
        // With the singing in the overlays A's mids (the voice among them) stay until the bass swap, so that a sentence can be finished
        const double aMidOut = voiceLive ? std::max(midIn, bassSwap) : midIn;
        a.eqA[1] = 1.0 - smoothstep(p, aMidOut, aMidOut + midLen);
        a.eqA[2] = 1.0 - smoothstep(p, bassSwap + 2.0, L - 2.0);
        a.gainA = std::cos(kHalfPi * smoothstep(p, L - 2.0, (double)L));
        if (vocalCut) a.eqA[1] = 1.0 - smoothstep(p, 0.0, 2.0);
    } else {
        // Short: B comes in with its highs, one kick swap on the bar line in the middle
        const double swap = L * 0.5;
        a.gainB = smoothstep(p, 0.0, 1.0);
        a.eqB[0] = a.eqB[1] = smoothstep(p, swap, swap + 0.5);
        a.eqB[2] = 1.0;
        a.eqA[0] = a.eqA[1] = 1.0 - smoothstep(p, swap, swap + 0.5);
        a.eqA[2] = 1.0 - smoothstep(p, swap + 0.5, L - 1.0);
        a.gainA = std::cos(kHalfPi * smoothstep(p, L - 1.0, (double)L));
        if ((flags & kFlagVocalCut) && !(flags & kFlagVoiceLive)) a.eqA[1] = std::min(a.eqA[1], 1.0 - smoothstep(p, 0.0, 1.0));
    }
    return a;
}

// Beats before the bar line at which the exit effect starts
double kindPre(Template kind, double bpm) {
    if (kind == Template::FilterOut || kind == Template::FadeOut) return 4.0;
    if (kind == Template::EchoOut || kind == Template::ReverbOut || kind == Template::BrakeOut || kind == Template::SpinOut ||
        kind == Template::RollOut)
        return 2.0;
    return std::max(0.02, 0.015 * std::max(40.0, bpm) / 60.0);  // a cut: A is faded out over 15 ms
}

// Beats before the bar line at which the exit starts: the effect itself, or the riser when that is longer
double exitPre(Template kind, double bpm, int flags) {
    const double k = kindPre(kind, bpm);
    return (flags & kFlagRiser) ? std::max(k, 4.0) : k;
}

bool isExit(Template k) {
    return k == Template::EchoOut || k == Template::FilterOut || k == Template::Cut || k == Template::ReverbOut ||
           k == Template::BrakeOut || k == Template::SpinOut || k == Template::RollOut || k == Template::FadeOut;
}

}  // namespace

Engine::Engine(int sampleRate) : sr(sampleRate) {
    for (int i = 0; i < 2; i++) {
        slots[i].deck.init(sampleRate);
        eq[i].init(sampleRate);
    }
    echo.init(sampleRate, 2.0);
    sweep.init(sampleRate);
    reverb.init(sampleRate);
    ringFx.init(sampleRate);
    riser.init(sampleRate);
    impact.init(sampleRate);
}

bool Engine::play(std::shared_ptr<PcmSource> src, const TrackMap& map, double startSec) {
    if (cmd.load(std::memory_order_acquire) != kNone) return false;
    Slot& n = slots[1 - curIdx.load(std::memory_order_acquire)];
    const int s = n.state.load();
    if (s != kEmpty && s != kReady) return false;
    n.src = std::move(src);
    n.map = map;
    n.trim = 1.0;
    n.deck.setSource(n.src.get());
    n.state.store(kReady, std::memory_order_release);
    cmdStartSec = startSec;
    cmd.store(kCmdPlay, std::memory_order_release);
    return true;
}

bool Engine::loadNext(std::shared_ptr<PcmSource> src, const TrackMap& map) {
    Slot& n = slots[1 - curIdx.load(std::memory_order_acquire)];
    if (n.state.load() != kEmpty) return false;
    n.src = std::move(src);
    n.map = map;
    // Bring the next song to the level the current one is heard at (at most 6 dB down and 4 dB up)
    n.trim = 1.0;
    {
        const Slot& c = slots[curIdx.load(std::memory_order_acquire)];
        if (c.map.loudness != 0.0 && map.loudness != 0.0) {
            const double db = std::clamp((c.map.loudness + 20.0 * std::log10(std::max(0.1, c.trim))) - map.loudness, -6.0, 4.0);
            n.trim = std::pow(10.0, db / 20.0);
        }
    }
    n.deck.setSource(n.src.get());
    n.state.store(kReady, std::memory_order_release);
    return true;
}

bool Engine::setPlan(const TransitionPlan& p) {
    if (cmd.load(std::memory_order_acquire) != kNone) return false;
    cmdPlan = p;
    cmd.store(kCmdPlan, std::memory_order_release);
    return true;
}

bool Engine::clearPlan() {
    if (cmd.load(std::memory_order_acquire) != kNone) return false;
    cmd.store(kCmdClear, std::memory_order_release);
    return true;
}

bool Engine::dropNext() {
    if (cmd.load(std::memory_order_acquire) != kNone) return false;
    cmd.store(kCmdDrop, std::memory_order_release);
    return true;
}

bool Engine::stop() {
    if (cmd.load(std::memory_order_acquire) != kNone) return false;
    cmd.store(kCmdStop, std::memory_order_release);
    return true;
}

bool Engine::setCurrentMap(const TrackMap& map, int epoch) {
    reclaim();
    if (pendingMap.load(std::memory_order_acquire) != nullptr) return false;
    pendingEpoch = epoch;
    pendingMap.store(new TrackMap(map), std::memory_order_release);
    return true;
}

void Engine::reclaim() {
    delete retiredMap.exchange(nullptr, std::memory_order_acq_rel);
    for (auto& s : slots) {
        if (s.state.load(std::memory_order_acquire) == kRetired) {
            s.src.reset();
            s.map = TrackMap();
            s.state.store(kEmpty, std::memory_order_release);
        }
    }
}

void Engine::applyCommand() {
    if (TrackMap* m = pendingMap.exchange(nullptr, std::memory_order_acq_rel)) {
        if (m->valid() && pendingEpoch == telemetry.epoch.load(std::memory_order_relaxed) &&
            slots[curIdx.load(std::memory_order_relaxed)].state.load() == kActive) {
            std::swap(slots[curIdx.load(std::memory_order_relaxed)].map, *m);
        }
        // Whatever is left in [m] (the new map or the old one) is deleted by the control thread
        retiredMap.store(m, std::memory_order_release);  // setCurrentMap reclaims before it hands over a new one
    }
    const int c = cmd.load(std::memory_order_acquire);
    if (c == kNone) return;
    if (c == kCmdPlan) {
        plan = cmdPlan;
        planActive = true;
    } else if (c == kCmdClear) {
        planActive = false;
    } else if (c == kCmdDrop) {
        Slot& n = slots[1 - curIdx.load(std::memory_order_relaxed)];
        if (n.state.load() == kReady) n.state.store(kRetired, std::memory_order_release);
        planActive = false;
    } else if (c == kCmdStop) {
        for (auto& sl : slots) {
            const int v = sl.state.load();
            if (v == kActive || v == kReady) sl.state.store(kRetired, std::memory_order_release);
        }
        planActive = false;
        bRunning = false;
        gliding = false;
        echoOn = false;
        reverbOn = false;
        impact.stop();
        st = kIdle;
        telemetry.state.store(kIdle);
    } else if (c == kCmdPlay) {
        const int old = curIdx.load(std::memory_order_relaxed);
        const int nw = 1 - old;
        if (slots[old].state.load() == kActive) slots[old].state.store(kRetired, std::memory_order_release);
        curIdx.store(nw, std::memory_order_release);
        Slot& s = slots[nw];
        const double speed = speedWanted.load(std::memory_order_relaxed);
        s.deck.cue(cmdStartSec, speed);
        eq[nw].snap(1, 1, 1);
        s.state.store(kActive, std::memory_order_release);
        telemetry.begin();
        telemetry.epoch.fetch_add(1);
        telemetry.positionSec.store(s.deck.heardSec(), std::memory_order_relaxed);
        telemetry.ended.store(0);
        telemetry.state.store(kPlaying, std::memory_order_relaxed);
        telemetry.end();
        st = kPlaying;
        planActive = false;
        bRunning = false;
        gliding = false;
        echoOn = false;
        reverbOn = false;
        impact.stop();
        entryLeft = 0;
        trim = 0.0;
        trimI = 0.0;
    }
    cmd.store(kNone, std::memory_order_release);
}

void Engine::handoff() {
    level.reset();
    const int old = curIdx.load(std::memory_order_relaxed);
    slots[old].state.store(kRetired, std::memory_order_release);
    const int nw = 1 - old;
    curIdx.store(nw, std::memory_order_release);
    slots[nw].state.store(kActive, std::memory_order_release);
    eq[nw].snap(1, 1, 1);
    eq[old].snap(1, 1, 1);
    bRunning = false;
    gliding = planActive && plan.tempoRatioB != 1.0 && plan.glideBeats > 0 && slots[nw].map.valid();
    if (gliding) {
        glideFrom = plan.tempoRatioB;
        glideT = 0.0;
        glideTotal = plan.glideBeats * 60.0 / (slots[nw].map.bpm * plan.tempoRatioB);
    }
    planActive = false;
    trim = 0.0;
    trimI = 0.0;
    st = kPlaying;
    telemetry.begin();
    telemetry.positionSec.store(slots[nw].deck.heardSec(), std::memory_order_relaxed);
    telemetry.changes.fetch_add(1);
    telemetry.epoch.fetch_add(1);
    telemetry.end();
}

void Engine::render(float* out, int frames) {
    applyCommand();
    const float fadeStep = 64.0f / (0.02f * (float)sr);  // a pause or resume takes 20 ms
    int done = 0;
    while (done < frames) {
        const int n = std::min(frames - done, 64);
        float* o = out + 2 * done;
        float pg = pauseGain.load(std::memory_order_relaxed);
        const bool wantPause = pausedWanted.load(std::memory_order_relaxed);
        if (wantPause && pg <= 0.0f) {
            silence(o, n);
        } else {
            const float pgStart = pg;
            pg = wantPause ? std::max(0.0f, pg - fadeStep) : std::min(1.0f, pg + fadeStep);
            pauseGain.store(pg, std::memory_order_relaxed);
            step(o, n);
            const float g0 = gainNow * pgStart;
            gainNow += 0.2f * ((float)gainWanted.load(std::memory_order_relaxed) - gainNow);
            const float g1 = gainNow * pg;
            for (int i = 0; i < n; i++) {
                const float g = g0 + (g1 - g0) * ((float)i / (float)n);
                o[2 * i] *= g;
                o[2 * i + 1] *= g;
            }
        }
        done += n;
    }
}

void Engine::step(float* out, int n) {
    const int ci = curIdx.load(std::memory_order_relaxed);
    Slot& A = slots[ci];
    if (A.state.load(std::memory_order_acquire) != kActive) {
        silence(out, n);
        return;
    }
    const int bi = 1 - ci;
    Slot& B = slots[bi];
    const double speed = speedWanted.load(std::memory_order_relaxed);
    Deck& dA = A.deck;
    const double hA = dA.heardSec();
    const double bt = A.map.valid() ? A.map.beatAtTime(hA) : 0.0;
    double rateA = speed;
    if (gliding && st == kPlaying) {
        glideT += (double)n / (double)sr;
        const double u = glideT / std::max(1e-6, glideTotal);
        if (u >= 1.0) gliding = false;
        else rateA = speed * (1.0 + (glideFrom - 1.0) * (1.0 - smoothstep(u, 0.0, 1.0)));
    }
    double gA = 1.0, gB = 0.0;
    bool outro = false;
    bool riserOn = false, ringActive = false;
    double riserX = 0.0, riserDx = 0.0;
    Automation au;
    bool haveAu = false;
    const bool bReady = B.state.load(std::memory_order_acquire) == kReady;

    // A song that ran out: go to the next one at once when there is one
    if (st == kPlaying && dA.atEnd()) {
        if (bReady) {
            B.deck.cue(B.map.cueIn, speed);
            handoff();
            step(out, n);
            return;
        }
        A.state.store(kRetired, std::memory_order_release);
        telemetry.begin();
        telemetry.ended.store(1);
        telemetry.state.store(kIdle);
        telemetry.end();
        silence(out, n);
        return;
    }

    if (st == kPlaying && planActive && bReady) {
        if (plan.kind == Template::Fallback) {
            if (hA >= plan.fallbackStartA) {
                B.deck.cue(plan.fallbackStartB, speed);
                eq[bi].snap(1, 1, 1);
                bRunning = true;
                fadeFrames = (int64_t)(plan.fallbackSeconds * sr);
                fadeDone = 0;
                st = kFade;
            }
        } else if (isExit(plan.kind)) {
            if (A.map.valid() && bt >= plan.startBeatA - exitPre(plan.kind, A.map.bpm, plan.flags)) {
                echo.reset();
                sweep.reset();
                reverb.reset();
                reverbOn = false;
                impact.stop();
                riser.reset(0x9E3779B9u ^ (uint32_t)(plan.startBeatA * 16.0));
                if (plan.kind == Template::BrakeOut || plan.kind == Template::SpinOut || plan.kind == Template::RollOut) {
                    const double beatFrames = 60.0 / std::max(40.0, A.map.bpm) * sr / std::max(0.25, speed);
                    ringFx.begin(plan.kind == Template::BrakeOut ? RingFx::kBrake
                                 : plan.kind == Template::SpinOut ? RingFx::kSpin : RingFx::kRoll,
                                 beatFrames, 2.0);
                }
                if (plan.kind == Template::ReverbOut) {
                    reverbOn = true;
                    reverbLeft = (int64_t)(9.0 * sr);
                }
                if (plan.kind == Template::EchoOut) {
                    echo.set((size_t)(0.75 * 60.0 / std::max(40.0, A.map.bpm) * sr), 0.55f);
                    echoOn = true;
                    echoLeft = (int64_t)(8.0 * sr);
                }
                st = kOutro;
            }
        } else if (A.map.valid() && bt >= plan.startBeatA - plan.rampBeats - kPreBeats) {
            st = kRamp;
        }
    }

    if (st == kRamp) {
        const double rampEnd = plan.startBeatA - kPreBeats;
        const double u = std::min(1.0, std::max(0.0, (bt - (rampEnd - plan.rampBeats)) / plan.rampBeats));
        rateA = speed * (1.0 + (plan.tempoRatioA - 1.0) * smoothstep(u, 0.0, 1.0));
        if (bt >= rampEnd) {
            // Cue B where it has to be when A reaches the start of the overlap: its beat grid follows the beat grid of A
            const double target = plan.startBeatB + (bt - plan.startBeatA) / plan.beatFactor;
            B.deck.cue(B.map.timeAtBeat(target), speed * plan.tempoRatioB);
            eq[bi].snap(0, 0, 1);
            bRunning = true;
            trim = 0.0;
            trimI = 0.0;
            st = kArmed;
            // A stem mix only plays as one when both songs have their overlay now; otherwise it is the plain blend
            stemLive = (plan.flags & kFlagStemMix) && A.src && B.src && A.src->hasOverlay() && B.src->hasOverlay();
            voiceLive = (plan.flags & kFlagVocalCut) && !(plan.flags & kFlagStemMix) && A.src && B.src && A.src->hasOverlay() && B.src->hasOverlay();
        }
    }
    if (st == kArmed || st == kBlend) {
        // The beats of B follow those of A: a small speed trim on A (which is gone after the overlap) closes any gap
        rateA = speed * plan.tempoRatioA;
        const double target = plan.startBeatB + (bt - plan.startBeatA) / plan.beatFactor;
        const double err = B.deck.heardSec() - B.map.timeAtBeat(target);  // positive: B is ahead
        telemetry.phaseErrorMs.store(err * 1000.0, std::memory_order_relaxed);
        // Phase lock: A is sped up when B is ahead of the grid and slowed when B is behind, up to 3% in total. 0.0.142: a PI
        // loop with natural frequency 3 rad/s and damping 1 (P 6 per second, I 9 per second squared). The old values (P 0.83,
        // I 0.69) took about 7 seconds for one swing, so a 16 beat overlap ended in the middle of an overshoot: in the logs a
        // tempo mismatch of 0.75% gave 8 to 9 ms and then swung to -8 ms. The integral part takes over a constant tempo error.
        constexpr double kP = 6.0;
        constexpr double kI = 9.0;
        const double dt = (double)n / (double)sr;
        trimI = std::min(0.015, std::max(-0.015, trimI + kI * err * dt));
        const double want = std::min(0.03, std::max(-0.03, kP * err + trimI));
        trim += 0.05 * (want - trim);
        rateA *= 1.0 + trim;
        const double p = bt - plan.startBeatA;
        if (st == kArmed && p >= 0.0) st = kBlend;
        telemetry.blendBeats.store(p, std::memory_order_relaxed);
        if (p >= plan.lengthBeats || dA.atEnd()) {
            // The overlap is over: B plays on alone, from this very block
            handoff();
            step(out, n);
            return;
        }
        au = automate(plan.kind, p, plan.lengthBeats, plan.flags | (stemLive ? kFlagStemLive : 0) | (voiceLive ? kFlagVoiceLive : 0), plan.bassSwapBeat);
        haveAu = true;
        gA = au.gainA;
        gB = au.gainB;
    }
    if (st == kOutro) {
        const double pre = exitPre(plan.kind, A.map.bpm, plan.flags);
        const double own = kindPre(plan.kind, A.map.bpm);
        const double p = std::max(0.0, bt - (plan.startBeatA - pre));
        const double q = std::max(0.0, p - (pre - own));  // beats into the effect itself
        const double x = std::min(1.0, q / own);
        outro = true;
        riserOn = (plan.flags & kFlagRiser) != 0;
        riserX = std::min(1.0, p / pre);
        riserDx = (double)n / (pre * 60.0 / std::max(40.0, A.map.bpm) * sr / std::max(0.25, speed));
        if (bt >= plan.startBeatA || dA.atEnd()) {
            // The bar line: B comes in on its entry, A is gone
            B.deck.cue(plan.entrySecB, speed);
            eq[bi].snap(1, 1, 1);
            // A song that comes in after a fade has a short fade-in; after any other exit it is 5 ms
            // 0.0.86 (log 0.0.84: the fade-in was often too slow): onto a drop B comes in at once (20 ms, so the drop lands), otherwise
            // within half a beat
            entryTotal = entryLeft = plan.kind == Template::FadeOut
                ? ((plan.flags & kFlagDrop) ? std::max(1, (int)(0.02 * sr))
                                            : std::max(1, (int)(0.5 * 60.0 / std::max(40.0, B.map.bpm) * sr)))
                : std::max(1, (int)(0.005 * sr));
            echoLeft = echoOn ? (int64_t)(8.0 * sr) : 0;
            if (reverbOn) reverbLeft = (int64_t)(9.0 * sr);
            if (plan.flags & kFlagImpact) impact.trigger();
            handoff();
            step(out, n);
            return;
        }
        if (plan.kind == Template::EchoOut || plan.kind == Template::ReverbOut) gA = std::cos(kHalfPi * smoothstep(x, 0.0, 1.0));
        else if (plan.kind == Template::Cut) gA = std::cos(kHalfPi * x);
        else if (plan.kind == Template::FadeOut) gA = std::cos(kHalfPi * smoothstep(x, 0.0, 1.0));
        else if (plan.kind == Template::BrakeOut || plan.kind == Template::SpinOut || plan.kind == Template::RollOut) {
            gA = 1.0;
            // the effect starts when its own beats start; before that A plays on as it was
            ringActive = q > 0.0 || own >= pre;
        } else {
            sweep.setCutoff(18000.0 * std::pow(250.0 / 18000.0, smoothstep(x, 0.0, 1.0)));
            const double lastBeat = std::max(0.0, p - (pre - 1.0));
            gA = std::cos(kHalfPi * smoothstep(lastBeat, 0.0, 1.0));
        }
    }
    if (st == kFade) {
        const double x = std::min(1.0, (double)fadeDone / (double)std::max<int64_t>(1, fadeFrames));
        gA = std::cos(kHalfPi * x);
        gB = std::sin(kHalfPi * x);
        fadeDone += n;
        if (x >= 1.0 || dA.atEnd()) {
            handoff();
            step(out, n);
            return;
        }
    }

    dA.render(bufA, n, rateA);
    if (outro && plan.kind == Template::FilterOut) sweep.process(bufA, n);
    if (outro && (plan.kind == Template::BrakeOut || plan.kind == Template::SpinOut || plan.kind == Template::RollOut)) {
        if (ringActive) ringFx.process(bufA, n);
        else ringFx.record(bufA, n);
    }
    float inEcho[2 * 64];
    const bool echoFeed = outro && (plan.kind == Template::EchoOut || plan.kind == Template::ReverbOut);
    if (echoFeed) {
        for (int i = 0; i < 2 * n; i++) inEcho[i] = 0.7f * bufA[i];
    }
    gA *= A.trim;
    gB *= B.trim;
    if (outro) {
        for (int i = 0; i < 2 * n; i++) out[i] = softLimit((float)(gA * bufA[i]));
    } else {
        if (haveAu) eq[ci].setTarget(au.eqA[0], au.eqA[1], au.eqA[2]);
        eq[ci].process(bufA, n);
        if (bRunning) {
            B.deck.render(bufB, n, speed * plan.tempoRatioB);
            if (haveAu) eq[bi].setTarget(au.eqB[0], au.eqB[1], au.eqB[2]);
            eq[bi].process(bufB, n);
            // Together the two songs must not be louder than one: scale down when the gains add up to more than one in power
            // 0.0.107: the EQ already took parts of each song away (A loses its mids and bass, B has no bass at first), so the power is counted
            // per band (bass 0.5, mids 0.35, highs 0.15 of a song's power); before, the gains alone were counted and B sat 3 dB low for the
            // whole blend and then jumped to full level when A was gone
            auto band = [&](const double* e) { return 0.5 * e[0] * e[0] + 0.35 * e[1] * e[1] + 0.15 * e[2] * e[2]; };
            const double wa = haveAu ? band(au.eqA) : 1.0, wb = haveAu ? band(au.eqB) : 1.0;
            const double pw = gA * gA * wa + gB * gB * wb;
            const double bal = pw > 1.0 ? 1.0 / std::sqrt(pw) : 1.0;
            double lv = 1.0;
            if (plan.flags & kFlagLevel) lv = level.update(bufA, bufB, n, sr, gA, gB);
            else level.reset();
            trace.gainB = gB; trace.trimB = B.trim; trace.bal = bal; trace.lv = lv; trace.bassB = haveAu ? au.eqB[0] : 1.0;
            for (int i = 0; i < 2 * n; i++) out[i] = softLimit((float)(bal * lv * (gA * bufA[i] + gB * bufB[i])));
        } else {
            for (int i = 0; i < 2 * n; i++) out[i] = softLimit(bufA[i]);
        }
    }
    if (entryLeft > 0) {
        for (int i = 0; i < n; i++) {
            const int left = std::max(0, entryLeft - i);
            const float g = 1.0f - (float)left / (float)entryTotal;
            out[2 * i] *= g;
            out[2 * i + 1] *= g;
        }
        entryLeft = std::max(0, entryLeft - n);
    }
    if (echoOn) {
        for (int i = 0; i < n; i++) {
            float wl, wr;
            echo.process(echoFeed ? inEcho[2 * i] : 0.0f, echoFeed ? inEcho[2 * i + 1] : 0.0f, wl, wr);
            out[2 * i] = softLimit(out[2 * i] + wl);
            out[2 * i + 1] = softLimit(out[2 * i + 1] + wr);
        }
        if (!outro) {
            echoLeft -= n;
            if (echoLeft <= 0) echoOn = false;
        }
    }
    if (reverbOn) {
        const bool feed = outro && plan.kind == Template::ReverbOut;
        for (int i = 0; i < n; i++) {
            float wl, wr;
            reverb.process(feed ? inEcho[2 * i] : 0.0f, feed ? inEcho[2 * i + 1] : 0.0f, wl, wr);
            out[2 * i] = softLimit(out[2 * i] + wl);
            out[2 * i + 1] = softLimit(out[2 * i + 1] + wr);
        }
        if (!outro) {
            reverbLeft -= n;
            if (reverbLeft <= 0) reverbOn = false;
        }
    }
    if (outro && riserOn) {
        for (int i = 0; i < n; i++) {
            const float r = riser.next(std::min(1.0, riserX + riserDx * i / n));
            out[2 * i] = softLimit(out[2 * i] + r);
            out[2 * i + 1] = softLimit(out[2 * i + 1] + r);
        }
    }
    if (impact.active()) {
        for (int i = 0; i < n; i++) {
            const float v = impact.next();
            out[2 * i] = softLimit(out[2 * i] + v);
            out[2 * i + 1] = softLimit(out[2 * i + 1] + v);
        }
    }
    telemetry.begin();
    telemetry.state.store(st, std::memory_order_relaxed);
    telemetry.positionSec.store(dA.heardSec(), std::memory_order_relaxed);
    telemetry.bPositionSec.store(bRunning ? B.deck.heardSec() : 0.0, std::memory_order_relaxed);
    telemetry.end();
}

}  // namespace nooklin::mix
