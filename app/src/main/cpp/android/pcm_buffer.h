#pragma once
// A whole decoded song in memory: 16-bit stereo at the engine rate. Filled by the decoder thread (append, finish) and
// only handed to the engine after finish(), after which it is read only.
#include <atomic>
#include <chrono>
#include <memory>
#include <mutex>
#include <vector>

#include "../mix/resampler.h"
#include "../mix/types.h"

namespace nooklin::mix {

class PcmBuffer : public PcmSource {
public:
    static constexpr int kEngineRate = 44100;

    PcmBuffer(int srcRate, int64_t expectedFrames) {
        if (srcRate != kEngineRate) resampler = std::make_unique<Resampler>(srcRate, kEngineRate);
        if (expectedFrames > 0) data.reserve((size_t)expectedFrames * 2 + 8192);
    }

    /** [in] holds [frames] interleaved stereo frames at the source rate. */
    void append(const int16_t* in, int frames) {
        if (finished.load()) return;
        if (resampler) {
            resampler->process(in, frames, data);
        } else {
            data.insert(data.end(), in, in + (size_t)frames * 2);
        }
    }

    void finish() {
        data.shrink_to_fit();
        total = (int64_t)(data.size() / 2);
        finished.store(true, std::memory_order_release);
    }

    bool isFinished() const { return finished.load(std::memory_order_acquire); }
    size_t bytes() const { return data.size() * sizeof(int16_t); }

    /** The decoded length. */
    int64_t baseFrames() const { return finished.load(std::memory_order_acquire) ? total : 0; }

    /** The length the engine sees: the decoded length, or further when an overlay runs past the end (a beat loop). */
    int64_t frames() const override {
        const int64_t base = baseFrames();
        if (base == 0) return 0;
        const Overlay* o = overlay.load(std::memory_order_acquire);
        return o ? std::max<int64_t>(base, o->start + o->frames) : base;
    }

    void read(int64_t at, float* out, int n) override {
        const int64_t len = baseFrames();
        constexpr float k = 1.0f / 32768.0f;
        for (int i = 0; i < n; i++) {
            const int64_t f = at + i;
            if (f < 0 || f >= len) {
                out[2 * i] = out[2 * i + 1] = 0.0f;
            } else {
                out[2 * i] = data[(size_t)f * 2] * k;
                out[2 * i + 1] = data[(size_t)f * 2 + 1] * k;
            }
        }
        // A stretch with other audio over it (the same song with the singing changed, see setOverlay)
        const Overlay* o = overlay.load(std::memory_order_acquire);
        if (o) {
            const int64_t from = std::max<int64_t>(at, o->start);
            const int64_t to = std::min<int64_t>(at + n, o->start + o->frames);
            for (int64_t f = from; f < to; f++) {
                const size_t j = (size_t)(f - o->start) * 2;
                out[2 * (f - at)] = o->data[j] * k;
                out[2 * (f - at) + 1] = o->data[j + 1] * k;
            }
        }
    }

    /**
     * Puts [frames] frames of other audio (interleaved 16-bit stereo, the same song) over the song from frame [start]; the
     * audio thread reads it from the next block on. One overlay at a time; a new one replaces it. The replaced one is kept for a
     * moment (the audio thread may still be reading it) and freed by a later call. Returns false when [start] is outside the song.
     */
    bool setOverlay(int64_t start, const int16_t* in, int frames) {
        // The overlay may run past the end of the song by up to 30 s (the repeated bars of a beat loop)
        if (!finished.load(std::memory_order_acquire) || start < 0 || start >= total || frames <= 0 || start + frames > total + kMaxPastEnd) return false;
        auto* o = new Overlay();
        o->start = start;
        o->frames = frames;
        o->data.assign(in, in + (size_t)frames * 2);
        std::lock_guard<std::mutex> g(overlayLock);
        retire(overlay.exchange(o, std::memory_order_acq_rel));
        return true;
    }

    /** Takes the overlay away: the song sounds as it was decoded. */
    void clearOverlay() {
        std::lock_guard<std::mutex> g(overlayLock);
        retire(overlay.exchange(nullptr, std::memory_order_acq_rel));
    }

    bool hasOverlay() const override { return overlay.load(std::memory_order_acquire) != nullptr; }

    /** Copies frames of the song as it was decoded (without an overlay) into [out] (interleaved 16-bit stereo); the rest is silence. */
    void copyDecoded(int64_t at, int16_t* out, int n) const {
        const int64_t len = baseFrames();
        for (int i = 0; i < n; i++) {
            const int64_t f = at + i;
            const bool inside = f >= 0 && f < len;
            out[2 * i] = inside ? data[(size_t)f * 2] : 0;
            out[2 * i + 1] = inside ? data[(size_t)f * 2 + 1] : 0;
        }
    }

    ~PcmBuffer() {
        delete overlay.load();
        for (auto& r : retired) delete r.first;
    }

    /** Mono audio at half the engine rate for the analysis (the mean of four samples), as float. */
    std::vector<float> decimatedMono() const {
        const size_t m = (size_t)(baseFrames() / 2);
        std::vector<float> d(m);
        constexpr float k = 0.25f / 32768.0f;
        for (size_t i = 0; i < m; i++)
            d[i] = k * (float)((int)data[4 * i] + data[4 * i + 1] + data[4 * i + 2] + data[4 * i + 3]);
        return d;
    }

private:
    static constexpr int64_t kMaxPastEnd = 44100LL * 30;
    struct Overlay {
        int64_t start = 0;
        int frames = 0;
        std::vector<int16_t> data;
    };

    // A replaced overlay stays alive for a few seconds: the audio thread reads it without a lock for the length of one block
    void retire(Overlay* old) {
        using Clock = std::chrono::steady_clock;
        const auto now = Clock::now();
        for (size_t i = 0; i < retired.size();) {
            if (now - retired[i].second > std::chrono::seconds(5)) {
                delete retired[i].first;
                retired.erase(retired.begin() + (long)i);
            } else {
                i++;
            }
        }
        if (old) retired.emplace_back(old, now);
    }

    std::atomic<Overlay*> overlay{nullptr};
    std::mutex overlayLock;
    std::vector<std::pair<Overlay*, std::chrono::steady_clock::time_point>> retired;
    std::vector<int16_t> data;
    std::unique_ptr<Resampler> resampler;
    std::atomic<bool> finished{false};
    int64_t total = 0;
};

}  // namespace nooklin::mix
