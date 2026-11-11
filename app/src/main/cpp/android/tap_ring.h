// 0.0.110: what the mix engine plays, copied for AirPlay. One producer (the audio callback) and one consumer (a thread of the host),
// no lock and no allocation on the producer side. Float stereo goes in, 16-bit stereo comes out (the format AirPlay wants).
// When the ring is full the newest audio is dropped (the consumer drains every few ms, so it does not fill up in practice).
#pragma once
#include <algorithm>
#include <atomic>
#include <cmath>
#include <cstdint>
#include <vector>

namespace nooklin::mix {

class TapRing {
public:
    static constexpr uint32_t kFrames = 1u << 17;  // about 3 s at 44.1 kHz
    TapRing() : buf_(kFrames * 2) {}

    void enable(bool on, bool muteLocal) {
        muteLocal_.store(on && muteLocal, std::memory_order_relaxed);
        if (on && !on_.load(std::memory_order_relaxed)) flush();
        on_.store(on, std::memory_order_release);
    }
    bool muteLocal() const { return muteLocal_.load(std::memory_order_relaxed); }

    /** Audio thread: copies [frames] stereo float frames; when the local output is muted the block is set to silence afterwards. */
    void push(float* data, int frames) {
        if (!on_.load(std::memory_order_acquire)) return;
        const uint32_t w = w_.load(std::memory_order_relaxed);
        const uint32_t r = r_.load(std::memory_order_acquire);
        const uint32_t room = kFrames - (w - r);
        const uint32_t n = std::min<uint32_t>((uint32_t)frames, room);
        for (uint32_t i = 0; i < n; i++) {
            const uint32_t at = ((w + i) & (kFrames - 1)) * 2;
            buf_[at] = toShort(data[2 * i]);
            buf_[at + 1] = toShort(data[2 * i + 1]);
        }
        w_.store(w + n, std::memory_order_release);
        if (muteLocal_.load(std::memory_order_relaxed)) std::fill(data, data + 2 * frames, 0.0f);
    }

    /** Consumer thread: up to [maxFrames] frames into [out] (interleaved 16-bit); returns how many. */
    int read(int16_t* out, int maxFrames) {
        if (flushReq_.exchange(false, std::memory_order_acq_rel)) r_.store(flushAt_.load(std::memory_order_acquire), std::memory_order_release);
        const uint32_t r = r_.load(std::memory_order_relaxed);
        const uint32_t w = w_.load(std::memory_order_acquire);
        const uint32_t n = std::min<uint32_t>((uint32_t)maxFrames, w - r);
        for (uint32_t i = 0; i < n; i++) {
            const uint32_t at = ((r + i) & (kFrames - 1)) * 2;
            out[2 * i] = buf_[at];
            out[2 * i + 1] = buf_[at + 1];
        }
        r_.store(r + n, std::memory_order_release);
        return (int)n;
    }

    /** Any thread: what is waiting is thrown away (done by the consumer on its next read). */
    void flush() {
        flushAt_.store(w_.load(std::memory_order_acquire), std::memory_order_release);  // only what is in the ring now is thrown away
        flushReq_.store(true, std::memory_order_release);
    }

private:
    static int16_t toShort(float v) {
        const float c = std::max(-1.0f, std::min(1.0f, v));
        return (int16_t)std::lrintf(c * 32767.0f);
    }
    std::vector<int16_t> buf_;
    std::atomic<uint32_t> w_{0}, r_{0}, flushAt_{0};
    std::atomic<bool> on_{false}, muteLocal_{false}, flushReq_{false};
};

}  // namespace nooklin::mix
