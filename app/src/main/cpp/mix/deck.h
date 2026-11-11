#pragma once
// One deck: a source, a position that is always known exactly in source frames, and a time-stretcher that is only used
// when the speed is not 1. At speed 1 the deck passes the audio through bit for bit.
#include <cmath>
#include <vector>

#include "../third_party/signalsmith-stretch.h"
#include "types.h"

namespace nooklin::mix {

class Deck {
public:
    static constexpr int kMaxBlock = 256;  // frames per render call

    void init(int sampleRate) {
        sr = sampleRate;
        stretch.presetDefault(2, (float)sampleRate);
        tmp.assign(2 * kScratch, 0.0f);
        for (auto& v : chIn) v.assign(kScratch, 0.0f);
        for (auto& v : chOut) v.assign(kScratch, 0.0f);
        passTmp.assign(2 * kMaxBlock, 0.0f);
    }

    void setSource(PcmSource* s) {
        src = s;
        stretching = false;
        heard = 0;
        cursor = 0;
    }
    bool loaded() const { return src != nullptr; }

    /** Source time of the audio at the start of the next block, in source frames. */
    int64_t heardFrames() const { return heard; }
    double heardSec() const { return (double)heard / (double)sr; }
    bool atEnd() const { return src && heard >= src->frames(); }
    bool stretchingNow() const { return stretching; }

    /**
     * Puts the deck at [sec] so that the next [render] returns the audio that starts there, at speed [rate]. With a
     * speed other than 1 the stretcher is primed first (its pre-roll is run here and thrown away), so the first sample
     * out is the sample at [sec], not a few thousand frames later.
     */
    void cue(double sec, double rate) {
        const int64_t start = (int64_t)std::llround(sec * sr);
        xfRemain = 0;
        if (!needsStretch(rate)) {
            stretching = false;
            cursor = start;
            heard = start;
            return;
        }
        prime(start, rate);
    }

    /** Renders [n] (at most kMaxBlock) interleaved stereo frames at [rate] into [out]. */
    void render(float* out, int n, double rate) {
        if (!src) {
            std::fill(out, out + 2 * n, 0.0f);
            return;
        }
        if (!stretching && needsStretch(rate)) {
            // Hand over from passthrough to the stretcher at this very frame, with a short crossfade
            passCursor = cursor;
            prime(cursor, rate);
            xfRemain = kCrossfade;
            xfTotal = kCrossfade;
        }
        if (!stretching) {
            src->read(cursor, out, n);
            cursor += n;
            heard = cursor;
            return;
        }
        const double want = inCarry + rate * n;
        const int in = (int)std::floor(want);
        inCarry = want - in;
        src->read(cursor, tmp.data(), in);
        for (int i = 0; i < in; i++) {
            chIn[0][i] = tmp[2 * i];
            chIn[1][i] = tmp[2 * i + 1];
        }
        float* ip[2] = {chIn[0].data(), chIn[1].data()};
        float* op[2] = {chOut[0].data(), chOut[1].data()};
        stretch.process(ip, in, op, n);
        for (int i = 0; i < n; i++) {
            out[2 * i] = chOut[0][i];
            out[2 * i + 1] = chOut[1][i];
        }
        cursor += in;
        heard += in;
        if (xfRemain > 0) {
            src->read(passCursor, passTmp.data(), n);
            passCursor += n;
            for (int i = 0; i < n; i++) {
                const int left = std::max(0, xfRemain - i);
                const float w = (float)left / (float)xfTotal;  // weight of the passthrough, 1 -> 0
                out[2 * i] = out[2 * i] * (1.0f - w) + passTmp[2 * i] * w;
                out[2 * i + 1] = out[2 * i + 1] * (1.0f - w) + passTmp[2 * i + 1] * w;
            }
            xfRemain = std::max(0, xfRemain - n);
        }
    }

private:
    static constexpr int kScratch = 32768;
    static constexpr int kCrossfade = 256;

    static bool needsStretch(double rate) { return std::fabs(rate - 1.0) > 1e-4; }

    void prime(int64_t start, double rate) {
        stretching = true;
        stretch.reset();
        const int il = std::min(kScratch, stretch.inputLatency());
        src->read(start, tmp.data(), il);
        for (int i = 0; i < il; i++) {
            chIn[0][i] = tmp[2 * i];
            chIn[1][i] = tmp[2 * i + 1];
        }
        float* ip[2] = {chIn[0].data(), chIn[1].data()};
        stretch.seek(ip, il, rate);
        cursor = start + il;
        const int ol = std::min(kScratch, stretch.outputLatency());
        const int inl = std::min(kScratch, (int)std::lround(rate * ol));
        src->read(cursor, tmp.data(), inl);
        for (int i = 0; i < inl; i++) {
            chIn[0][i] = tmp[2 * i];
            chIn[1][i] = tmp[2 * i + 1];
        }
        float* ip2[2] = {chIn[0].data(), chIn[1].data()};
        float* op[2] = {chOut[0].data(), chOut[1].data()};
        stretch.process(ip2, inl, op, ol);  // pre-roll, thrown away
        cursor += inl;
        heard = start;
        inCarry = 0.0;
    }

    int sr = 44100;
    PcmSource* src = nullptr;
    signalsmith::stretch::SignalsmithStretch<float> stretch;
    bool stretching = false;
    int64_t cursor = 0;   // next source frame to read
    int64_t heard = 0;    // source frame of the audio at the start of the next block
    double inCarry = 0.0;
    int64_t passCursor = 0;
    int xfRemain = 0, xfTotal = kCrossfade;
    std::vector<float> tmp, passTmp, chIn[2], chOut[2];
};

}  // namespace nooklin::mix
